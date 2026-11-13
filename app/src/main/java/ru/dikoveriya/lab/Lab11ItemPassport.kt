package ru.dikoveriya.lab

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
private fun PlantQR11(code:String){
    val bmp=remember(code){LabQr.bitmap(code,240)}
    Image(bitmap=bmp.asImageBitmap(),contentDescription="QR-код "+code,
        modifier=Modifier.size(116.dp))
}
@Composable
private fun InfoRow11(label:String,value:String){
    Row(Modifier.fillMaxWidth().padding(vertical=5.dp),
        verticalAlignment=Alignment.CenterVertically){
        Text(label,modifier=Modifier.weight(1f),
            style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value,style=MaterialTheme.typography.bodyMedium,
            modifier=Modifier.weight(1.5f))
    }
}

@Composable
internal fun Lab11ItemPassport(
    id:Long,db:LabDatabase,revision:Int,onBack:()->Unit,
    onOpenItem:(Long)->Unit,onOpenCulture:(Long)->Unit,
    onChanged:()->Unit,onError:(String)->Unit,modifier:Modifier=Modifier
){
    val context=androidx.compose.ui.platform.LocalContext.current
    val scope=rememberCoroutineScope()
    val item=remember(id,revision){db.item11(id)}
    var tab by rememberSaveable(id){mutableStateOf("Обзор")}
    var showAction by remember{mutableStateOf(false)}
    var showCutting by remember{mutableStateOf(false)}
    var showSale by remember{mutableStateOf(false)}
    var showMeasure by remember{mutableStateOf(false)}
    var showRepeat by remember{mutableStateOf(false)}
    var menu by remember{mutableStateOf(false)}
    var cancelEvent by remember{mutableStateOf<Long?>(null)}
    var cancelReason by remember{mutableStateOf("")}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){uri->
        if(uri!=null && item!=null)scope.launch{
            try{
                withContext(Dispatchers.IO){db.addItemPhoto11(context,item.id,uri,"")}
                onChanged()
            }catch(e:Exception){onError(e.message?:"Не удалось сохранить фото")}
        }
    }
    val qrSaver=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")){uri->
        if(uri!=null && item!=null)try{
            context.contentResolver.openOutputStream(uri)?.use{it.write(LabQr.png(item.code))}
                ?:error("Файл не создан")
        }catch(e:Exception){onError(e.message?:"Не удалось сохранить этикетку")}
    }
    if(item==null){
        Column(modifier.padding(18.dp)){
            Text("Объект не найден")
            TextButton(onClick=onBack){Text("Назад")}
        }
        return
    }
    val history=remember(id,revision){db.itemEvents11(id)}
    val measurements=remember(id,revision){db.itemGrowth11(id)}
    val photos=remember(id,revision){db.itemPhotos11(id)}
    val lineage=remember(id,revision){db.itemLineage11(id)}
    val direct=remember(id,revision){db.directCost11("item",id)}
    val closed=item.status in setOf("Продан","Утилизирован")
    LazyColumn(modifier=modifier,contentPadding=PaddingValues(16.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{
            Row(verticalAlignment=Alignment.CenterVertically){
                IconButton(onClick=onBack){Icon(Icons.Outlined.ArrowBack,"Назад")}
                Column(Modifier.weight(1f)){
                    Text("ПАСПОРТ РАСТЕНИЯ",
                        style=MaterialTheme.typography.labelSmall,
                        color=Lab07Palette.Brass)
                    Text(item.code,style=MaterialTheme.typography.titleLarge)
                }
                IconButton(onClick={
                    try{db.setFavorite11(id,!item.favorite);onChanged()}
                    catch(e:Exception){onError(e.message?:"Ошибка")}
                }){
                    Icon(if(item.favorite)Icons.Outlined.Star else Icons.Outlined.StarBorder,
                        "Избранное",tint=Lab07Palette.Brass)
                }
                Box{
                    IconButton(onClick={menu=true}){
                        Icon(Icons.Outlined.MoreVert,"Дополнительные действия")
                    }
                    DropdownMenu(expanded=menu,onDismissRequest={menu=false}){
                        DropdownMenuItem(text={Text("Добавить фотографию")},
                            onClick={menu=false;picker.launch("image/*")})
                        DropdownMenuItem(text={Text("Сохранить QR (PNG)")},
                            onClick={menu=false;qrSaver.launch(item.code+".png")})
                        DropdownMenuItem(text={Text("Создать черенок")},
                            onClick={menu=false;showCutting=true})
                        DropdownMenuItem(text={Text("Запланировать уход")},
                            onClick={menu=false;showRepeat=true})
                        if(!closed)DropdownMenuItem(text={Text("Оформить продажу")},
                            onClick={menu=false;showSale=true})
                    }
                }
            }
        }
        item{
            Card(colors=CardDefaults.cardColors(containerColor=Lab07Palette.DeepForest),
                shape=RoundedCornerShape(20.dp),
                border=BorderStroke(1.dp,Lab07Palette.Brass.copy(alpha=.6f))){
                Row(Modifier.fillMaxWidth().padding(13.dp),
                    verticalAlignment=Alignment.CenterVertically){
                    Lab11ItemCover(item.cover,Modifier.size(100.dp),item.kind)
                    Column(Modifier.weight(1f).padding(start=14.dp),
                        verticalArrangement=Arrangement.spacedBy(7.dp)){
                        Text(item.plantName,color=Lab07Palette.Champagne,
                            style=MaterialTheme.typography.titleLarge)
                        Text(item.kind+" · "+item.stage,color=Lab07Palette.Champagne)
                        Text(item.status,color=Lab07Palette.Brass,
                            style=MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        item{
            Button(onClick={showAction=true},enabled=!closed,
                modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){
                Icon(Icons.Outlined.AddCircleOutline,null)
                Spacer(Modifier.width(8.dp))
                Text("Действие")
            }
        }
        item{
            TabRow(selectedTabIndex=listOf("Обзор","Рост","История").indexOf(tab),
                containerColor=MaterialTheme.colorScheme.surface){
                listOf("Обзор","Рост","История").forEach{option->
                    Tab(selected=tab==option,onClick={tab=option},
                        text={Text(option)})
                }
            }
        }
        when(tab){
            "Обзор"->{
                item{
                    Card(colors=CardDefaults.cardColors(
                        containerColor=MaterialTheme.colorScheme.surface)){
                        Column(Modifier.padding(16.dp)){
                            InfoRow11("Вид / сорт",item.plantName)
                            InfoRow11("Тип",item.kind)
                            InfoRow11("Этап",item.stage)
                            InfoRow11("Дата появления",formatDate(item.acquiredAt))
                            InfoRow11("Место",item.location.ifBlank{"Не указано"})
                            InfoRow11("Субстрат",item.substrate.ifBlank{"Не указан"})
                            if(item.rootingMethod.isNotBlank())
                                InfoRow11("Укоренение",item.rootingMethod)
                            if(item.stimulant.isNotBlank())
                                InfoRow11("Стимулятор",item.stimulant)
                            if(item.potMl!=null)InfoRow11("Объём горшка",item.potMl.toString()+" мл")
                            InfoRow11("Покупка",kopecks11(item.purchaseMinor))
                            InfoRow11("Материалы и операции",kopecks11(direct))
                            InfoRow11("Прямые затраты",kopecks11(item.purchaseMinor+direct))
                            if(item.notes.isNotBlank())Text(item.notes)
                        }
                    }
                }
                item{
                    OutlinedCard(modifier=Modifier.fillMaxWidth()){
                        Column(Modifier.padding(14.dp),
                            verticalArrangement=Arrangement.spacedBy(10.dp)){
                            Text("Происхождение",style=MaterialTheme.typography.titleMedium)
                            item.parentItemId?.let{source->
                                TextButton(onClick={onOpenItem(source)}){
                                    Text("Родительское растение #"+source)
                                    Icon(Icons.Outlined.ChevronRight,null)
                                }
                            }
                            item.parentCultureId?.let{source->
                                TextButton(onClick={onOpenCulture(source)}){
                                    Text("Исходная культура #"+source)
                                    Icon(Icons.Outlined.ChevronRight,null)
                                }
                            }
                            lineage.forEach{Text(it,style=MaterialTheme.typography.bodySmall)}
                            if(item.parentItemId==null && item.parentCultureId==null)
                                Text("Создано как самостоятельный объект")
                        }
                    }
                }
                item{
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.SpaceBetween){
                        Column{
                            Text("Этикетка",style=MaterialTheme.typography.titleMedium)
                            Text(item.code,style=MaterialTheme.typography.bodySmall)
                            TextButton(onClick={qrSaver.launch(item.code+".png")}){
                                Text("Сохранить PNG")
                            }
                        }
                        PlantQR11(item.code)
                    }
                }
                item{Text("Фотографии · "+photos.size,style=MaterialTheme.typography.titleMedium)}
                items(photos.take(12),key={it.first}){(file,caption)->
                    Row(verticalAlignment=Alignment.CenterVertically){
                        Lab11ItemCover(file,Modifier.size(86.dp))
                        Column(Modifier.padding(start=12.dp)){
                            Text(caption.ifBlank{"Фотография растения"})
                        }
                    }
                }
                item{
                    OutlinedButton(onClick={picker.launch("image/*")},
                        modifier=Modifier.fillMaxWidth()){
                        Icon(Icons.Outlined.AddPhotoAlternate,null)
                        Text("Добавить фотографию")
                    }
                }
            }
            "Рост"->{
                item{
                    Row(verticalAlignment=Alignment.CenterVertically){
                        Text("Измерения",style=MaterialTheme.typography.titleLarge,
                            modifier=Modifier.weight(1f))
                        IconButton(onClick={showMeasure=true}){
                            Icon(Icons.Outlined.AddChart,"Записать замер")
                        }
                    }
                }
                item{Lab11GrowthChart(measurements)}
                if(measurements.isEmpty())item{Text("Замеров пока нет.")}
                items(measurements.asReversed(),key={it.id}){sample->
                    OutlinedCard(modifier=Modifier.fillMaxWidth()){
                        Column(Modifier.padding(12.dp),
                            verticalArrangement=Arrangement.spacedBy(5.dp)){
                            Text(formatDate(sample.at),style=MaterialTheme.typography.titleSmall)
                            Text(listOfNotNull(sample.heightMm?.let{"Высота: "+it+" мм"},
                                sample.leaves?.let{"Листьев: "+it},
                                sample.roots?.let{"Корней: "+it}).joinToString(" · "))
                            if(sample.note.isNotBlank())Text(sample.note,
                                style=MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            else->{
                item{Text("История · "+history.size,
                    style=MaterialTheme.typography.titleLarge)}
                if(history.isEmpty())item{Text("Событий пока нет")}
                items(history,key={it.id}){event->
                    var expanded by rememberSaveable(event.id){mutableStateOf(false)}
                    OutlinedCard(onClick={expanded=!expanded},
                        modifier=Modifier.fillMaxWidth()){
                        Column(Modifier.padding(14.dp),
                            verticalArrangement=Arrangement.spacedBy(6.dp)){
                            Text(event.kind+(if(event.cancelled)" · отменено" else ""),
                                style=MaterialTheme.typography.titleMedium)
                            Text(formatDate(event.time),style=MaterialTheme.typography.bodySmall,
                                color=MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(event.note.ifBlank{"Без примечания"})
                            if(expanded){
                                if(event.details!="{}")Text(event.details,
                                    style=MaterialTheme.typography.bodySmall)
                                if(!event.cancelled && event.kind !in setOf("Создание","Продажа","Отмена")){
                                    TextButton(onClick={cancelEvent=event.id}){
                                        Text("Отменить ошибочную операцию")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if(showAction)Lab11ActionDialog(item,db,onClose={showAction=false},
        onSaved={showAction=false;onChanged()},onError=onError)
    if(showCutting)Lab11NewCuttingDialog(item,db,onClose={showCutting=false},
        onSaved={id2->showCutting=false;onChanged();onOpenItem(id2)},onError=onError)
    if(showSale)Lab11SaleDialog(item,db,onClose={showSale=false},
        onSaved={showSale=false;onChanged()},onError=onError)
    if(showMeasure)Lab11MeasureDialog(item,db,onClose={showMeasure=false},
        onSaved={showMeasure=false;onChanged()},onError=onError)
    if(showRepeat)Lab11RecurrenceDialog(item,db,onClose={showRepeat=false},
        onSaved={showRepeat=false;onChanged()},onError=onError)
    cancelEvent?.let{eventId->
        AlertDialog(onDismissRequest={cancelEvent=null},
            title={Text("Отменить последнее действие?")},
            text={Field("Причина отмены",cancelReason,{cancelReason=it},single=false)},
            confirmButton={Button(enabled=cancelReason.trim().length>=2,onClick={
                try{db.cancelItemEvent11(eventId,cancelReason)
                    cancelEvent=null;cancelReason="";onChanged()
                }catch(e:Exception){onError(e.message?:"Не удалось отменить")}
            }){Text("Подтвердить")}},
            dismissButton={TextButton(onClick={cancelEvent=null}){Text("Назад")}})
    }
}

@Composable
private fun Lab11GrowthChart(samples:List<Lab11GrowthSample>){
    val points=samples.filter{it.heightMm!=null}
    Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),
        shape=RoundedCornerShape(18.dp)){
        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
            Text("Рост по высоте, мм",style=MaterialTheme.typography.titleMedium)
            if(points.size<2)Text("Для графика необходимо два измерения высоты.",
                style=MaterialTheme.typography.bodySmall)
            else{
                val min=points.minOf{requireNotNull(it.heightMm)}
                val max=points.maxOf{requireNotNull(it.heightMm)}
                Text("От "+min+" до "+max+" мм · "+points.size+" измерений",
                    style=MaterialTheme.typography.bodySmall)
                val green=Lab07Palette.Moss
                Canvas(Modifier.fillMaxWidth().height(144.dp)){
                    val low=if(max==min)min-1 else min
                    val diff=(max-low).coerceAtLeast(1.0)
                    val minX=points.first().at
                    val maxX=points.last().at
                    val timespan=(maxX-minX).coerceAtLeast(1L)
                    val offset=12.dp.toPx()
                    val chartW=size.width-offset*2
                    val chartH=size.height-offset*2
                    val positions=points.map{p->
                        val x=offset+chartW*((p.at-minX).toFloat()/timespan)
                        val y=offset+chartH*(1f-((requireNotNull(p.heightMm)-low)/diff).toFloat())
                        androidx.compose.ui.geometry.Offset(x,y)
                    }
                    positions.zipWithNext{a,b->
                        drawLine(green,a,b,strokeWidth=3.dp.toPx())
                    }
                    positions.forEach{drawCircle(green,4.dp.toPx(),it)}
                }
            }
        }
    }
}

@Composable
private fun Lab11MeasureDialog(item:Lab11PlantItem,db:LabDatabase,
    onClose:()->Unit,onSaved:()->Unit,onError:(String)->Unit){
    var date by rememberSaveable{mutableStateOf(dateText11())}
    var height by rememberSaveable{mutableStateOf("")}
    var leaves by rememberSaveable{mutableStateOf("")}
    var roots by rememberSaveable{mutableStateOf("")}
    var note by rememberSaveable{mutableStateOf("")}
    AlertDialog(onDismissRequest=onClose,title={Text("Замер · "+item.code)},
        text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
            Lab11DateField("Дата",date,{date=it})
            Field("Высота в мм (необязательно)",height,{height=it})
            Field("Количество листьев",leaves,{leaves=it})
            Field("Количество корней",roots,{roots=it})
            Field("Примечание",note,{note=it},single=false)
        }},
        confirmButton={Button(enabled=atDate11(date)!=null &&
            listOf(height,leaves,roots).any{it.isNotBlank()},onClick={
            try{
                db.measureItem11(item.id,requireNotNull(atDate11(date)),
                    height.takeIf{it.isNotBlank()}?.replace(',','.')?.toDoubleOrNull(),
                    leaves.toIntOrNull(),roots.toIntOrNull(),note)
                onSaved()
            }catch(e:Exception){onError(e.message?:"Проверь измерение")}
        }){Text("Записать")}},
        dismissButton={TextButton(onClick=onClose){Text("Отмена")}})
}

@Composable
private fun Lab11RecurrenceDialog(item:Lab11PlantItem,db:LabDatabase,
    onClose:()->Unit,onSaved:()->Unit,onError:(String)->Unit){
    var title by rememberSaveable{mutableStateOf("Полить "+item.plantName)}
    var days by rememberSaveable{mutableStateOf("7")}
    var date by rememberSaveable{mutableStateOf(dateText11())}
    var start by rememberSaveable{mutableStateOf("1")}
    var end by rememberSaveable{mutableStateOf("12")}
    AlertDialog(onDismissRequest=onClose,title={Text("Повторяющаяся задача")},
        text={Column(verticalArrangement=Arrangement.spacedBy(11.dp)){
            Field("Название работы",title,{title=it})
            Field("Повторять каждые N дней",days,{days=it})
            Lab11DateField("Первый срок",date,{date=it})
            Field("Начало сезона (месяц 1–12)",start,{start=it})
            Field("Конец сезона (месяц 1–12)",end,{end=it})
            Text("Задачи создаются локально при открытии приложения, не чаще заданного интервала.",
                style=MaterialTheme.typography.bodySmall)
        }},
        confirmButton={Button(onClick={
            try{
                db.addRecurringRule11(title,"Уход",days.toIntOrNull()?:0,
                    requireNotNull(atDate11(date)),"Обычный",
                    start.toIntOrNull()?:0,end.toIntOrNull()?:0,
                    "item",item.id)
                db.generateRecurringTasks11()
                onSaved()
            }catch(e:Exception){onError(e.message?:"Не удалось создать повторение")}
        }){Text("Сохранить")}},
        dismissButton={TextButton(onClick=onClose){Text("Отмена")}})
}
