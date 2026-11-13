package ru.dikoveriya.lab

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import java.util.UUID

data class NiimDiscovered(val address:String,val name:String,val rssi:Int)

/** Android native BLE transport. No official NIIMBOT app or ACTION_SEND required. */
@SuppressLint("MissingPermission") // UI must obtain runtime BLE permissions before entry
class NiimbotBle(private val context:Context) {
    companion object {
        val SERVICE=UUID.fromString("e7810a71-73ae-499d-8c15-faa9aef0c3f2")
        val CHAR=UUID.fromString("bef8d6c9-9c21-4c9e-b632-bd58c1009f9f")
        val CCCD=UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
    private val adapter:BluetoothAdapter?=
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private var scanCallback:ScanCallback?=null
    private var gatt:BluetoothGatt?=null
    private var characteristic:BluetoothGattCharacteristic?=null
    private var ready:CompletableDeferred<Unit>?=null
    private var responses=Channel<NiimPacket>(Channel.UNLIMITED)
    private var decoder=NiimCodec.Decoder()
    private var mtu=23
    val connected:Boolean get()=gatt!=null&&characteristic!=null
    fun ensureBluetooth(){
        check(adapter!=null&&adapter.isEnabled){"Включи Bluetooth на телефоне"}
    }

    fun scan(onDevice:(NiimDiscovered)->Unit,onError:(String)->Unit){
        stopScan()
        ensureBluetooth()
        val scanner=adapter?.bluetoothLeScanner?:error("BLE-сканер недоступен")
        val seen=mutableMapOf<String,String>()
        val callback=object:ScanCallback(){
            override fun onScanResult(callbackType:Int,result:ScanResult){
                try{
                    val device=result.device
                    val name=result.scanRecord?.deviceName ?: device.name ?: "Bluetooth-принтер"
                    if(name.startsWith("D1",true)||name.startsWith("B1",true)||
                        name.startsWith("B2",true)||name.startsWith("N1",true)||
                        name.startsWith("M2",true)){
                        if(seen[device.address]!=name){
                            seen[device.address]=name
                            onDevice(NiimDiscovered(device.address,name,result.rssi))
                        }
                    }
                }catch(e:Exception){onError(e.message?:"Ошибка сканирования")}
            }
            override fun onScanFailed(errorCode:Int){
                onError("Не удалось начать поиск Bluetooth: "+errorCode)
            }
        }
        scanCallback=callback
        scanner.startScan(null,ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),callback)
    }

    fun stopScan(){
        scanCallback?.let{
            try{adapter?.bluetoothLeScanner?.stopScan(it)}catch(_:Exception){}
        }
        scanCallback=null
    }

    suspend fun connect(address:String){
        stopScan();close()
        ensureBluetooth()
        val current=CompletableDeferred<Unit>()
        ready=current
        responses=Channel(Channel.UNLIMITED)
        decoder=NiimCodec.Decoder()
        val callbacks=object:BluetoothGattCallback(){
            override fun onConnectionStateChange(g:BluetoothGatt,status:Int,newState:Int){
                if(status!=BluetoothGatt.GATT_SUCCESS ||
                    newState==BluetoothProfile.STATE_DISCONNECTED){
                    current.completeExceptionally(IllegalStateException(
                        "Принтер отключён или отклонил соединение (код $status)"))
                    characteristic=null
                    return
                }
                if(newState==BluetoothProfile.STATE_CONNECTED && !g.discoverServices())
                    current.completeExceptionally(IllegalStateException("Принтер не предоставил сервисы"))
            }
            override fun onServicesDiscovered(g:BluetoothGatt,status:Int){
                if(status!=BluetoothGatt.GATT_SUCCESS){
                    current.completeExceptionally(IllegalStateException("Ошибка BLE-сервисов: $status"))
                    return
                }
                val ch=g.getService(SERVICE)?.getCharacteristic(CHAR)
                if(ch==null){
                    current.completeExceptionally(IllegalStateException(
                        "Устройство не имеет известного NIIMBOT BLE-сервиса"))
                    return
                }
                characteristic=ch
                if(!g.setCharacteristicNotification(ch,true)){
                    current.completeExceptionally(IllegalStateException("Не включены ответы принтера"))
                    return
                }
                val descriptor=ch.getDescriptor(CCCD)
                if(descriptor==null){
                    current.completeExceptionally(IllegalStateException("Не найден BLE CCCD"))
                    return
                }
                if(Build.VERSION.SDK_INT>=33){
                    val code=g.writeDescriptor(descriptor,
                        BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                    if(code!=BluetoothStatusCodes.SUCCESS)
                        current.completeExceptionally(IllegalStateException("BLE CCCD: $code"))
                }else{
                    @Suppress("DEPRECATION")
                    descriptor.value=BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    @Suppress("DEPRECATION")
                    if(!g.writeDescriptor(descriptor))
                        current.completeExceptionally(IllegalStateException("Не удалось включить уведомления"))
                }
            }
            override fun onDescriptorWrite(g:BluetoothGatt,d:BluetoothGattDescriptor,status:Int){
                if(status==BluetoothGatt.GATT_SUCCESS)current.complete(Unit)
                else current.completeExceptionally(IllegalStateException("Нет уведомлений принтера: $status"))
            }
            @Deprecated("Required on Android API 26–32")
            override fun onCharacteristicChanged(g:BluetoothGatt,ch:BluetoothGattCharacteristic){
                @Suppress("DEPRECATION")
                receive(ch.value)
            }
            override fun onCharacteristicChanged(g:BluetoothGatt,
                ch:BluetoothGattCharacteristic,value:ByteArray){
                receive(value)
            }
            override fun onMtuChanged(g:BluetoothGatt,newMtu:Int,status:Int){
                if(status==BluetoothGatt.GATT_SUCCESS)mtu=newMtu
            }
        }
        val device=adapter!!.getRemoteDevice(address)
        gatt=device.connectGatt(context,false,callbacks,BluetoothDevice.TRANSPORT_LE)
        try{
            withTimeout(15000){current.await()}
            gatt?.requestMtu(247)
            delay(300)
        }catch(e:Exception){close();throw e}
    }
    private fun receive(value:ByteArray?){
        if(value==null)return
        val parsed=decoder.feed(value)
        parsed.forEach{responses.trySend(it)}
    }
    suspend fun write(bytes:ByteArray){
        val connection=gatt?:error("Принтер не подключён")
        val tx=characteristic?:error("Нет BLE-канала записи")
        val chunkSize=(mtu-3).coerceIn(20,244)
        var offset=0
        while(offset<bytes.size){
            val end=minOf(offset+chunkSize,bytes.size)
            val chunk=bytes.copyOfRange(offset,end)
            if(Build.VERSION.SDK_INT>=33){
                val code=connection.writeCharacteristic(tx,chunk,
                    BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
                check(code==BluetoothStatusCodes.SUCCESS){"BLE-передача отклонена: $code"}
            }else{
                @Suppress("DEPRECATION")
                tx.writeType=BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                @Suppress("DEPRECATION")
                tx.value=chunk
                @Suppress("DEPRECATION")
                check(connection.writeCharacteristic(tx)){"BLE-передача не началась"}
            }
            offset=end
            // Unacknowledged GATT writes must be paced; unpaced streams lose rows.
            delay(10)
        }
    }
    suspend fun exchange(cmd:Int,payload:ByteArray=byteArrayOf(),
        reply:Int,timeout:Long=5500):NiimPacket{
        write(NiimCodec.packet(cmd,payload))
        return withTimeout(timeout){
            while(true){
                val frame=responses.receive()
                if(frame.opcode==reply)return@withTimeout frame
            }
            @Suppress("UNREACHABLE_CODE") error("Unreachable")
        }
    }
    suspend fun identify():NiimModel {
        // Startup command may be needed to arm BLE for some protocol variants.
        try{write(byteArrayOf(3)+NiimCodec.packet(0xc1,byteArrayOf(1)))}
        catch(_:Exception){}
        try{exchange(0xa5,byteArrayOf(1),0xb5,1600)}catch(_:Exception){}
        val info=exchange(0x40,byteArrayOf(8),0x48,4500)
        val data=info.payload
        require(data.isNotEmpty()){"Модель принтера не сообщена"}
        val modelId=if(data.size==1)(data[0].toInt() and 255) shl 8
            else NiimCodec.uint16(data[0],data[1])
        return NiimModel.forId(modelId)?:error(
            "Модель $modelId не проверена для прямой печати. Экспортируй PNG.")
    }
    fun close(){
        stopScan()
        try{gatt?.disconnect()}catch(_:Exception){}
        try{gatt?.close()}catch(_:Exception){}
        gatt=null;characteristic=null;mtu=23
        ready=null
    }
}
