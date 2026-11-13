package ru.dikoveriya.lab

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlin.math.roundToInt

/** Preflight checks the exact QR pixels to be shipped to the printhead. */
object NiimPreflight {
    fun verifyQr(image:Bitmap,design:LabelDesign,code:String) {
        val qr=design.layers.single{it.kind=="qr" && it.visible}
        val scale=image.width/40f
        val x=(qr.x*scale).roundToInt().coerceIn(0,image.width-1)
        val y=(qr.y*scale).roundToInt().coerceIn(0,image.height-1)
        val size=(minOf(qr.width,qr.height)*scale).roundToInt()
            .coerceAtMost(image.width-x).coerceAtMost(image.height-y)
        require(size>20){"QR слишком мал для печати"}
        val pixels=IntArray(size*size)
        image.getPixels(pixels,0,size,x,y,size,size)
        val decoded=QRCodeReader().decode(BinaryBitmap(
            HybridBinarizer(RGBLuminanceSource(size,size,pixels)))).text
        require(decoded==code){"QR не соответствует контейнеру; печать заблокирована"}
    }
}

@Composable
fun Lab09PrintPanel(values:LabelValues,layout:LabelDesign,
    onError:(String)->Unit
){
    val context=LocalContext.current
    val ble=remember{NiimbotBle(context.applicationContext)}
    val scope=rememberCoroutineScope()
    val found=remember{mutableStateListOf<NiimDiscovered>()}
    var selected by remember{mutableStateOf<NiimDiscovered?>(null)}
    var model by remember{mutableStateOf<NiimModel?>(null)}
    var status by remember{mutableStateOf("Принтер не подключён")}
    var busy by remember{mutableStateOf(false)}
    var density by rememberSaveable{mutableFloatStateOf(3f)}
    var copies by rememberSaveable{mutableFloatStateOf(1f)}
    var flip by rememberSaveable{mutableStateOf(false)}
    var confirmed by remember{mutableIntStateOf(0)}
    var sent by remember{mutableIntStateOf(0)}
    val required=remember{
        if(Build.VERSION.SDK_INT>=31)arrayOf(Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    fun beginScan(){
        try{
            found.clear()
            selected=null
            model=null
            ble.scan(onDevice={device->
                if(found.none{it.address==device.address})found+=device
            },onError={status=it})
            status="Поиск NIIMBOT… Выбери принтер из списка."
        }catch(e:Exception){status=e.message?:"Ошибка Bluetooth";onError(status)}
    }
    val permissionLauncher=rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()){results->
        if(required.all{results[it]==true})beginScan()
        else status="Разреши Bluetooth-поиск и подключение в настройках Android"
    }
    fun checkAndScan(){
        val missing=required.filter{
            ContextCompat.checkSelfPermission(context,it)!=PackageManager.PERMISSION_GRANTED
        }
        if(missing.isEmpty())beginScan() else permissionLauncher.launch(required)
    }
    DisposableEffect(ble){onDispose{ble.close()}}
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
        SectionTitle("Прямая печать NIIMBOT")
        Text("Это прямое Bluetooth-подключение, а не меню «Поделиться». Для D11/D110 используется 203 dpi; D11_H — 300 dpi. Поддержка определяется по ответу принтера.",
            style=MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick={checkAndScan},enabled=!busy,
            modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){
            Icon(Icons.Outlined.BluetoothSearching,null)
            Spacer(Modifier.width(8.dp))
            Text("Найти принтер NIIMBOT")
        }
        if(found.isNotEmpty() && model==null){
            Text("Найденные устройства",style=MaterialTheme.typography.titleSmall)
            found.take(15).forEach{device->
                OutlinedButton(enabled=!busy,onClick={
                    selected=device
                    busy=true
                    status="Подключение к "+device.name+"…"
                    scope.launch{
                        try{
                            ble.connect(device.address)
                            val discovered=ble.identify()
                            model=discovered
                            status="Подключён "+discovered.name+" · "+
                                discovered.dpi+" dpi · головка "+
                                discovered.width+" точек"
                        }catch(e:Exception){
                            ble.close()
                            model=null
                            status="Не удалось подключить: "+(e.message?:"неизвестная ошибка")
                        }finally{busy=false}
                    }
                },modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){
                    Icon(Icons.Outlined.Bluetooth,null)
                    Spacer(Modifier.width(8.dp))
                    Text(device.name+" · "+device.address)
                }
            }
        }
        Text(status,style=MaterialTheme.typography.bodyMedium,
            color=if(model!=null)Lab07Palette.Forest
                else MaterialTheme.colorScheme.onSurfaceVariant)
        model?.let{printer->
            if(!printer.printVerifiedExternally)
                Text("Модель распознана, но печать на этой ревизии не проверена нашей лабораторией. Для начала печатай одну пробную этикетку.",
                    color=MaterialTheme.colorScheme.error,
                    style=MaterialTheme.typography.bodySmall)
            Text("Плотность термопечати: "+density.roundToInt(),
                style=MaterialTheme.typography.bodyMedium)
            Slider(value=density,onValueChange={density=it},
                valueRange=1f..5f,steps=3,enabled=!busy)
            Text("Экземпляров: "+copies.roundToInt(),
                style=MaterialTheme.typography.bodyMedium)
            Slider(value=copies,onValueChange={copies=it},
                valueRange=1f..10f,steps=8,enabled=!busy)
            Row(verticalAlignment=Alignment.CenterVertically){
                Checkbox(checked=flip,onCheckedChange={flip=it},enabled=!busy)
                Text("Повернуть направление подачи на 180°",
                    style=MaterialTheme.typography.bodySmall)
            }
            Button(onClick={
                busy=true;sent=0;confirmed=0
                scope.launch{
                    try{
                        val design=layout.copy(dpi=printer.dpi)
                        val image=withContext(Dispatchers.Default){
                            LabLabelDesigner.bitmap(values,design).also{
                                NiimPreflight.verifyQr(it,design,values.code)
                            }
                        }
                        val job=(1..copies.roundToInt()).map{n->
                            values.code+" · экземпляр "+n to image
                        }
                        NiimbotPrintSession(ble,printer).print(job,
                            density=density.roundToInt(),flipFeed=flip){progress->
                            sent=progress.sent
                            confirmed=progress.confirmed
                            status=progress.message
                        }
                    }catch(e:Exception){
                        status="Печать не подтверждена: "+(e.message?:"неизвестная ошибка")+
                            ". Не повторяй задание, пока не проверишь бумагу."
                        onError(status)
                    }finally{busy=false}
                }
            },enabled=!busy,modifier=Modifier.fillMaxWidth().heightIn(min=54.dp)){
                Icon(Icons.Outlined.Print,null);Spacer(Modifier.width(8.dp))
                Text("Печать напрямую · "+copies.roundToInt()+" шт.")
            }
            Text("Отправлено: "+sent+" · подтверждено принтером: "+confirmed,
                style=MaterialTheme.typography.bodySmall)
            OutlinedButton(enabled=!busy,onClick={
                ble.close();model=null;selected=null;status="Принтер отключён"
            },modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){
                Text("Отключить принтер")
            }
        }
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        Text("Если печать не началась, проверь размер загруженной ленты, Bluetooth и конкретную ревизию устройства. Повтор после разрыва соединения только вручную.",
            style=MaterialTheme.typography.bodySmall)
    }
}
