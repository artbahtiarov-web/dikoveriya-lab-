package ru.dikoveriya.lab

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.io.File

/** One clear purpose per dialog. No nested "Действие" mode selector. */
@Composable
fun Lab071PassportDialog(
    culture:CultureRecord,recipe:String?,onClose:()->Unit,
    onSave:(String,String,String)->Unit
) {
    var status by rememberSaveable(culture.id){mutableStateOf(culture.status)}
    var location by rememberSaveable(culture.id){mutableStateOf(culture.location)}
    var reason by rememberSaveable(culture.id){mutableStateOf("")}
    val editable=culture.status !in setOf(LabTerms.DISPOSED,LabTerms.TRANSFERRED,LabTerms.SOLD,LabTerms.ANNULLED)
    AlertDialog(
        onDismissRequest=onClose,
        title={Text("Паспорт контейнера")},
        text={
            Column(Modifier.fillMaxWidth().heightIn(max=510.dp)
                .verticalScroll(rememberScrollState()),
                verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text(culture.plantName,style=MaterialTheme.typography.titleLarge)
                Text("${culture.code} · ${culture.stage} · пассаж ${culture.passage}")
                Text("Остаток: ${culture.quantity} · ${culture.quantityKind}",
                    fontWeight=FontWeight.SemiBold)
                Text("Среда: ${recipe ?: "не указана"}",
                    style=MaterialTheme.typography.bodyMedium)
                Text("Происхождение: ${culture.originState}",
                    style=MaterialTheme.typography.bodySmall)
                HorizontalDivider()
                if(editable) {
                    Text("Состояние",style=MaterialTheme.typography.titleMedium)
                    // Visible chips, not an undiscoverable dropdown.
                    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        listOf(LabTerms.IN_CULTURE,LabTerms.CONTROL,LabTerms.QUARANTINE).forEach{item->
                            FilterChip(selected=status==item,onClick={status=item},
                                label={Text(item)},modifier=Modifier.heightIn(min=48.dp))
                        }
                    }
                    Field("Стеллаж / место",location,{location=it})
                    Field("Причина изменения (необязательно)",reason,{reason=it},single=false)
                    Text("Количество, потери и смена среды оформляются отдельно, с записью в историю.",
                        style=MaterialTheme.typography.bodySmall)
                } else Text("Контейнер закрыт. Его паспорт доступен только для просмотра.")
            }
        },
        confirmButton={
            if(editable)Button(
                enabled=status!=culture.status || location!=culture.location,
                onClick={onSave(status,location,reason.ifBlank{"Изменение паспорта"})}
            ){Text("Сохранить изменения")}
        },
        dismissButton={TextButton(onClick=onClose){Text("Закрыть")}}
    )
}

@Composable
fun Lab071SaleDialog(culture:CultureRecord,onClose:()->Unit,onSave:(Int,Double,String)->Unit) {
    var qty by rememberSaveable(culture.id){mutableStateOf("1")}
    var total by rememberSaveable(culture.id){mutableStateOf("")}
    var note by rememberSaveable(culture.id){mutableStateOf("")}
    val eligible=culture.status==LabTerms.IN_CULTURE &&
        culture.stage!=LabTerms.IN_VITRO && culture.quantityKind=="Растения"
    val number=qty.toIntOrNull()
    val amount=total.replace(',','.').toDoubleOrNull()
    AlertDialog(onDismissRequest=onClose,title={Text("Продажа растений")},
        text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("${culture.plantName} · ${culture.code}")
            Text("Доступно: ${culture.quantity} (${culture.quantityKind})")
            if(!eligible) Text("Продажа доступна только для учтённых растений вне стерильной культуры со статусом «В культуре».",
                color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
            else {
                Field("Количество растений",qty,{qty=it.filter(Char::isDigit)},numeric=true)
                Field("Общая выручка, ₽",total,{total=it},numeric=true)
                Field("Примечание (необязательно)",note,{note=it},single=false)
                Text("Это выручка, не прибыль. Продажа уменьшит остаток на $number.",
                    style=MaterialTheme.typography.bodySmall)
            }
        }},
        confirmButton={if(eligible)Button(
            enabled=number!=null && number in 1..culture.quantity &&
                amount!=null && amount.isFinite() && amount>=0,
            onClick={onSave(requireNotNull(number),requireNotNull(amount),note)}
        ){Text("Подтвердить продажу")}},
        dismissButton={TextButton(onClick=onClose){Text("Отмена")}}
    )
}

@Composable
fun Lab071PhotosDialog(culture:CultureRecord,photos:List<CulturePhoto>,filesDir:File,
    onClose:()->Unit,onAdd:(String)->Unit) {
    var note by rememberSaveable(culture.id){mutableStateOf("")}
    AlertDialog(onDismissRequest=onClose,title={Text("Фото контейнера")},
        text={Column(Modifier.fillMaxWidth().heightIn(max=510.dp)
            .verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(9.dp)) {
            Text("${culture.plantName} · ${culture.code}")
            Field("Подпись нового фото",note,{note=it})
            Button(onClick={onAdd(note)},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                Icon(Icons.Outlined.AddAPhoto,null)
                Spacer(Modifier.width(8.dp))
                Text("Выбрать фото")
            }
            Text("Фото в истории · ${photos.size}",style=MaterialTheme.typography.titleMedium)
            if(photos.isEmpty())Text("Фотографий пока нет.")
            photos.forEach{photo->
                val file=File(File(filesDir,"culture_photos"),photo.filename)
                val preview=remember(photo.filename){
                    runCatching {
                        val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
                        BitmapFactory.decodeFile(file.absolutePath,bounds)
                        var sample=1
                        while(bounds.outWidth/sample>650||bounds.outHeight/sample>650)sample*=2
                        BitmapFactory.decodeFile(file.absolutePath,
                            BitmapFactory.Options().apply{inSampleSize=sample})?.asImageBitmap()
                    }.getOrNull()
                }
                if(preview!=null)Image(preview,"Фото: ${photo.caption}",
                    Modifier.fillMaxWidth().heightIn(max=180.dp))
                Text("${formatDate(photo.addedAt)} · ${photo.caption}",
                    style=MaterialTheme.typography.bodySmall)
            }
        }},
        confirmButton={},
        dismissButton={TextButton(onClick=onClose){Text("Назад")}})
}

@Composable
fun Lab071LabelDialog(
    culture:CultureRecord,recipe:String?,onClose:()->Unit,
    onExport:(LabLabelSpec)->Unit,onShare:(LabLabelSpec)->Unit
) {
    var title by rememberSaveable(culture.id){mutableStateOf(culture.plantName)}
    var includeMedium by rememberSaveable(culture.id){mutableStateOf(true)}
    var dpi by rememberSaveable(culture.id){mutableIntStateOf(203)}
    val spec=LabLabelSpec(culture.code,title,culture.passage,recipe.orEmpty(),includeMedium,dpi)
    val image=remember(spec){LabLabel40.bitmap(spec).asImageBitmap()}
    val (width,height)=LabLabel40.dimensions(dpi)
    AlertDialog(onDismissRequest=onClose,title={Text("Этикетка · 40 × 12 мм")},
        text={Column(Modifier.fillMaxWidth().heightIn(max=550.dp)
            .verticalScroll(rememberScrollState()),
            verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("Реальный формат — горизонтальная наклейка 40 × 12 мм.",
                style=MaterialTheme.typography.bodySmall)
            Image(bitmap=image,contentDescription="Этикетка: QR, ${culture.code}, $title",
                modifier=Modifier.fillMaxWidth().aspectRatio(40f/12f))
            Text("На макете: QR слева, название, номер ${culture.code}, пассаж и ${if(includeMedium)"среда" else "название лаборатории"}. Полное название доступно после сканирования.",
                style=MaterialTheme.typography.bodySmall)
            Field("Подпись на этикетке",title,{title=it.take(140)})
            Row(verticalAlignment=Alignment.CenterVertically) {
                Checkbox(checked=includeMedium,onCheckedChange={includeMedium=it})
                Text("Показывать среду / версию")
            }
            Text("Плотность печати",style=MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf(203,300).forEach{v->
                    FilterChip(selected=dpi==v,onClick={dpi=v},label={Text("$v dpi")})
                }
            }
            Text("Файл: ${width} × ${height} px, с метаданными DPI. В NIIMBOT необходимо отдельно выбрать бумагу 40 × 12 мм. Bluetooth-печать приложением не выполняется.",
                style=MaterialTheme.typography.bodySmall)
        }},
        confirmButton={
            Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                Button(onClick={onExport(spec)},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                    Icon(Icons.Outlined.SaveAlt,null);Spacer(Modifier.width(7.dp))
                    Text("Сохранить PNG 40×12")
                }
                OutlinedButton(onClick={onShare(spec)},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                    Icon(Icons.Outlined.Share,null);Spacer(Modifier.width(7.dp))
                    Text("Передать в NIIMBOT / другое приложение")
                }
            }
        },
        dismissButton={TextButton(onClick=onClose){Text("Закрыть")}})
}
