package ru.dikoveriya.lab

import android.content.ClipData
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val fieldNames080=linkedMapOf(
    "qr" to "QR-код","plant" to "Название растения",
    "code" to "Код контейнера","passage" to "Пассаж",
    "medium" to "Среда и версия","date" to "Дата создания",
    "mother" to "Маточник","note" to "Заметка",
    "text" to "Произвольный текст"
)
private fun label080(v:Float)=String.format(Locale.US,"%.1f",v)

/**
 * Self-contained editor. All coordinates are model millimetres. Dragging and
 * numeric nudges update the same immutable layout used by the PNG renderer.
 * Templates and per-container overrides are stored in SQLite and backed up.
 */
@Composable
fun Lab080LabelEditor(
    culture:CultureRecord,db:LabDatabase,revision:Int,
    medium:String,onBack:()->Unit,onChanged:()->Unit,
    onError:(String)->Unit,modifier:Modifier=Modifier
) {
    val context=LocalContext.current
    val mother=remember(culture.motherId){db.mothers().firstOrNull{it.id==culture.motherId}?.code.orEmpty()}
    val values=remember(culture.id,culture.passage,medium){LabelValues(
        culture.code,culture.plantName,culture.passage,medium,
        SimpleDateFormat("dd.MM.yy",Locale("ru")).format(Date(culture.createdAt)),
        mother,culture.notes.take(150)
    )}
    val initial=remember(culture.id){runCatching{db.labelForCulture080(culture.id)}
        .getOrNull()?:LabelDesign.initial()}
    var serialized by rememberSaveable(culture.id){mutableStateOf(initial.encode())}
    var lastSaved by remember(culture.id){mutableStateOf(initial.encode())}
    var confirmLeave by rememberSaveable(culture.id){mutableStateOf(false)}
    val layout=remember(serialized){LabelDesign.decode(serialized)}
    val latestLayout by rememberUpdatedState(layout)
    var selected by rememberSaveable(culture.id){mutableStateOf("plant")}
    var zoom by rememberSaveable(culture.id){mutableFloatStateOf(1f)}
    var grid by rememberSaveable(culture.id){mutableStateOf(true)}
    var fontPickerOpen by rememberSaveable(culture.id){mutableStateOf(false)}
    var snap by rememberSaveable(culture.id){mutableStateOf(true)}
    var templateName by rememberSaveable(culture.id){mutableStateOf("")}
    var confirmDelete by rememberSaveable(culture.id){mutableStateOf<Long?>(null)}
    var savedNotice by remember{mutableStateOf("")}
    var exportLayout by remember{mutableStateOf<LabelDesign?>(null)}
    val undo=remember{mutableStateListOf<String>()}
    val redo=remember{mutableStateListOf<String>()}
    val templates=remember(revision){db.labelTemplates080()}
    val chosen=layout.layers.firstOrNull{it.id==selected}
    val bmp=remember(serialized,values){LabLabelDesigner.bitmap(values,layout).asImageBitmap()}
    val warnings=remember(serialized){LabLabelDesigner.warnings(layout)}
    var previewWidthPx by remember{mutableFloatStateOf(1f)}
    val handleWidthPx=with(LocalDensity.current){48.dp.toPx()}

    fun checkpoint(){
        undo.add(serialized)
        if(undo.size>60)undo.removeAt(0)
        redo.clear()
    }
    fun commit(next:LabelDesign,history:Boolean=true){
        try{
            next.verify()
            if(next.encode()!=serialized){
                if(history)checkpoint()
                serialized=next.encode()
                savedNotice=""
            }
        }catch(e:Exception){onError(e.message?:"Некорректный макет")}
    }
    fun edit(layer:LabelLayer)=commit(layout.replace(layer))
    fun save(){
        try{
            db.saveLabel080(culture.id,layout)
            lastSaved=serialized
            savedNotice="Макет контейнера сохранён"
            onChanged()
        }catch(e:Exception){onError(e.message?:"Не удалось сохранить макет")}
    }

    val export=rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png")){uri->
        val captured=exportLayout
        if(uri!=null && captured!=null){
            try{
                context.contentResolver.openOutputStream(uri)?.use{
                    it.write(LabLabelDesigner.png(values,captured))
                }?:error("Не удалось открыть файл")
                savedNotice="PNG 40×12 мм сохранён"
            }catch(e:Exception){onError(e.message?:"Не удалось экспортировать PNG")}
        }
        exportLayout=null
    }

    val setSelected:(String)->Unit={selected=it}
    fun requestBack(){
        if(serialized!=lastSaved)confirmLeave=true else onBack()
    }
    BackHandler(enabled=serialized!=lastSaved){
        confirmLeave=true
    }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Lab07ScreenHeader("${culture.code} / МАРКИРОВКА",
            "Редактор этикетки",
            "Холст 40 × 12 мм. Перетащи элемент пальцем, настрой размеры и сохрани шаблон.",::requestBack)
        Row(verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(8.dp)){
            Text("Только просмотр",style=MaterialTheme.typography.bodySmall)
            Slider(value=zoom,onValueChange={zoom=it},valueRange=1f..2.5f,
                modifier=Modifier.weight(1f))
            Text("${(zoom*100).roundToInt()}%",style=MaterialTheme.typography.bodySmall)
        }
        Text("Размер объекта меняется за золотые углы наклейки или кнопками + / − ниже.",
            style=MaterialTheme.typography.bodySmall)
        Row(verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.SpaceBetween,
            modifier=Modifier.fillMaxWidth()){
            Row(verticalAlignment=Alignment.CenterVertically){
                Checkbox(checked=grid,onCheckedChange={grid=it})
                Text("Сетка")
            }
            Row(verticalAlignment=Alignment.CenterVertically){
                Checkbox(checked=snap,onCheckedChange={snap=it})
                Text("Шаг 0,5 мм")
            }
        }
        // The preview is vector-coordinate driven. Its bitmap and export share
        // exactly the same layout, never separate preview positioning logic.
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())){
            Box(
                modifier=Modifier.width((320f*zoom).dp)
                    .aspectRatio(40f/12f)
                    .clip(RoundedCornerShape(5.dp))
                    .border(1.dp,MaterialTheme.colorScheme.outline,
                        RoundedCornerShape(5.dp))
                    .onSizeChanged{previewWidthPx=it.width.toFloat().coerceAtLeast(1f)}
                    .pointerInput(serialized,previewWidthPx){
                        detectTapGestures{offset->
                            val mx=offset.x/previewWidthPx*40f
                            val my=offset.y/previewWidthPx*40f
                            layout.layers.asReversed().firstOrNull{layer->
                                mx in layer.x..(layer.x+layer.width) &&
                                my in layer.y..(layer.y+layer.height)
                            }?.let{setSelected(it.id)}
                        }
                    }
                    .pointerInput(previewWidthPx,snap){
                        var origin:LabelLayer?=null
                        var cumulative=Offset.Zero
                        detectDragGestures(
                            onDragStart={point->
                                val mx=point.x/previewWidthPx*40f
                                val my=point.y/previewWidthPx*40f
                                origin=latestLayout.layers.asReversed().firstOrNull{layer->
                                    !layer.locked &&
                                    mx in layer.x..(layer.x+layer.width) &&
                                    my in layer.y..(layer.y+layer.height)
                                }
                                cumulative=Offset.Zero
                                origin?.let{selected=it.id;checkpoint()}
                            },
                            onDrag={change,delta->
                                change.consume()
                                val start=origin
                                if(start!=null){
                                    cumulative+=delta
                                    val nx=start.x+cumulative.x/previewWidthPx*40f
                                    val ny=start.y+cumulative.y/previewWidthPx*40f
                                    fun nearest(v:Float)=if(snap)
                                        (v*2f).roundToInt()/2f else v
                                    val result=start.copy(
                                        x=nearest(nx).coerceIn(0f,40f-start.width),
                                        y=nearest(ny).coerceIn(0f,12f-start.height))
                                    commit(latestLayout.replace(result),history=false)
                                }
                            },
                            onDragEnd={origin=null},
                            onDragCancel={origin=null}
                        )
                    }
            ){
                Image(bmp,"Предпросмотр наклейки 40 на 12 миллиметров",
                    modifier=Modifier.fillMaxSize())
                androidx.compose.foundation.Canvas(Modifier.fillMaxSize()){
                    val mm=size.width/40f
                    if(grid){
                        for(i in 1..39){
                            drawLine(Color(0x557FA89B),Offset(i*mm,0f),
                                Offset(i*mm,size.height),strokeWidth=.5f)
                        }
                        for(j in 1..11){
                            drawLine(Color(0x557FA89B),Offset(0f,j*mm),
                                Offset(size.width,j*mm),strokeWidth=.5f)
                        }
                    }
                    chosen?.let{layer->
                        drawRect(Color(0xFFB98635),
                            topLeft=Offset(layer.x*mm,layer.y*mm),
                            size=androidx.compose.ui.geometry.Size(
                                layer.width*mm,layer.height*mm),
                            style=Stroke(width=2.dp.toPx()))
                    }
                }
                chosen?.let{layer->
                    // Four visible resizing corners. Hit zones are 48 dp while
                    // the gold markers themselves are 16 dp.
                    listOf(
                        Triple(0,layer.x,layer.y),
                        Triple(1,layer.x+layer.width,layer.y),
                        Triple(2,layer.x,layer.y+layer.height),
                        Triple(3,layer.x+layer.width,layer.y+layer.height)
                    ).forEach{(corner,mx,my)->
                        Box(Modifier
                            .offset {
                                IntOffset(
                                    (mx*previewWidthPx/40f-handleWidthPx/2f).roundToInt(),
                                    (my*previewWidthPx/40f-handleWidthPx/2f).roundToInt()
                                )
                            }
                            .size(48.dp)
                            .pointerInput(layer.id,corner,previewWidthPx,layer.locked) {
                                var baseline=layer
                                var deltaMm=Offset.Zero
                                detectDragGestures(
                                    onDragStart={
                                        baseline=latestLayout.layers.firstOrNull{it.id==layer.id}?:layer
                                        deltaMm=Offset.Zero
                                        if(!baseline.locked)checkpoint()
                                    },
                                    onDrag={change,amount->
                                        change.consume()
                                        if(!baseline.locked){
                                            deltaMm+=amount
                                            val newLayer=baseline.resizedFromCorner(
                                                corner,deltaMm.x/previewWidthPx*40f,
                                                deltaMm.y/previewWidthPx*40f)
                                            commit(latestLayout.replace(newLayer),history=false)
                                        }
                                    }
                                )
                            },
                            contentAlignment=Alignment.Center) {
                            Box(Modifier.size(16.dp)
                                .border(2.dp,if(layer.locked)Color.Gray
                                    else Color(0xFFB98635),RoundedCornerShape(3.dp))
                                .background(MaterialTheme.colorScheme.surface))
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp),
            modifier=Modifier.fillMaxWidth()){
            OutlinedButton(onClick={
                if(undo.isNotEmpty()){
                    redo.add(serialized);serialized=undo.removeAt(undo.lastIndex)
                }
            },enabled=undo.isNotEmpty(),modifier=Modifier.weight(1f)
                .heightIn(min=48.dp)){
                Icon(Icons.Outlined.Undo,null)
                Spacer(Modifier.width(5.dp))
                Text("Отменить")
            }
            OutlinedButton(onClick={
                if(redo.isNotEmpty()){
                    undo.add(serialized);serialized=redo.removeAt(redo.lastIndex)
                }
            },enabled=redo.isNotEmpty(),modifier=Modifier.weight(1f)
                .heightIn(min=48.dp)){
                Icon(Icons.Outlined.Redo,null)
                Spacer(Modifier.width(5.dp))
                Text("Вернуть")
            }
        }
        if(warnings.isNotEmpty()){
            Text("Внимание: "+warnings.take(3).joinToString("; ")+
                ". Перед печатью проверь читаемость.",
                color=MaterialTheme.colorScheme.error,
                style=MaterialTheme.typography.bodySmall)
        }
        SectionTitle("Элементы этикетки")
        // A scrollable one-line selector is explicit; no hidden long-press actions.
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement=Arrangement.spacedBy(6.dp)){
            layout.layers.forEach{layer->
                FilterChip(selected=layer.id==selected,
                    onClick={selected=layer.id},
                    label={Text(fieldNames080[layer.kind]?:"Текст",
                        maxLines=1)},
                    modifier=Modifier.heightIn(min=48.dp))
            }
        }
        chosen?.let{layer->
            Card(colors=CardDefaults.cardColors(
                containerColor=MaterialTheme.colorScheme.surface)){
                Column(Modifier.padding(14.dp),
                    verticalArrangement=Arrangement.spacedBy(9.dp)){
                    Text(fieldNames080[layer.kind]?:"Поле",
                        style=MaterialTheme.typography.titleMedium)
                    if(layer.kind!="qr" && layer.kind!="code"){
                        Row(verticalAlignment=Alignment.CenterVertically){
                            Checkbox(checked=layer.visible,
                                onCheckedChange={edit(layer.copy(visible=it))})
                            Text("Показывать")
                        }
                    }
                    if(layer.kind=="text"){
                        Field("Свой текст",layer.text,{edit(layer.copy(text=it.take(150)))},
                            single=false)
                    }
                    Text("РАЗМЕР ВЫБРАННОГО ОБЪЕКТА",
                        style=MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement=Arrangement.spacedBy(9.dp)){
                        OutlinedButton(enabled=!layer.locked,
                            onClick={edit(layer.scaled(.9f))},
                            modifier=Modifier.weight(1f).heightIn(min=48.dp)){
                            Text("− 10%")
                        }
                        OutlinedButton(enabled=!layer.locked,
                            onClick={edit(layer.scaled(1.1f))},
                            modifier=Modifier.weight(1f).heightIn(min=48.dp)){
                            Text("+ 10%")
                        }
                    }
                    Row(verticalAlignment=Alignment.CenterVertically){
                        Checkbox(checked=layer.locked,
                            onCheckedChange={edit(layer.copy(locked=it))})
                        Text("Закрепить от случайного перемещения")
                    }
                    Text("Положение, мм",style=MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){
                        OutlinedButton(onClick={edit(layer.moved(-.5f,0f))},
                            modifier=Modifier.weight(1f).heightIn(min=48.dp)){
                            Icon(Icons.Outlined.KeyboardArrowLeft,"Влево")
                        }
                        OutlinedButton(onClick={edit(layer.moved(.5f,0f))},
                            modifier=Modifier.weight(1f).heightIn(min=48.dp)){
                            Icon(Icons.Outlined.KeyboardArrowRight,"Вправо")
                        }
                        OutlinedButton(onClick={edit(layer.moved(0f,-.5f))},
                            modifier=Modifier.weight(1f).heightIn(min=48.dp)){
                            Icon(Icons.Outlined.KeyboardArrowUp,"Вверх")
                        }
                        OutlinedButton(onClick={edit(layer.moved(0f,.5f))},
                            modifier=Modifier.weight(1f).heightIn(min=48.dp)){
                            Icon(Icons.Outlined.KeyboardArrowDown,"Вниз")
                        }
                    }
                    Text("X ${label080(layer.x)} · Y ${label080(layer.y)} · размер ${label080(layer.width)} × ${label080(layer.height)} мм",
                        style=MaterialTheme.typography.bodySmall)
                    Text("Ширина, мм: ${label080(layer.width)}",
                        style=MaterialTheme.typography.bodySmall)
                    Slider(value=layer.width,onValueChange={w->
                        val width=w.coerceIn(if(layer.kind=="qr")8.8f else 2f,
                            40f-layer.x)
                        edit(layer.copy(width=width))
                    },valueRange=(if(layer.kind=="qr")8.8f else 2f)..40f)
                    Text("Высота, мм: ${label080(layer.height)}",
                        style=MaterialTheme.typography.bodySmall)
                    Slider(value=layer.height,onValueChange={h->
                        val height=h.coerceIn(if(layer.kind=="qr")8.8f else .8f,
                            12f-layer.y)
                        edit(layer.copy(height=height))
                    },valueRange=(if(layer.kind=="qr")8.8f else .8f)..12f)
                    if(layer.kind!="qr"){
                        Text("Высота букв: ${label080(layer.fontMm)} мм",
                            style=MaterialTheme.typography.bodySmall)
                        Slider(value=layer.fontMm,
                            onValueChange={edit(layer.copy(fontMm=it))},
                            valueRange=.65f..4f)
                        OutlinedButton(onClick={fontPickerOpen=true},
                            modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){
                            Icon(Icons.Outlined.FontDownload,null)
                            Spacer(Modifier.width(8.dp))
                            Text("Шрифт: "+LabelFontCatalog.option(layer.family).title)
                            Spacer(Modifier.weight(1f))
                            Icon(Icons.Outlined.ChevronRight,null)
                        }
                        PickOne("Выравнивание",listOf("Слева","По центру","Справа"),
                            layer.align,{edit(layer.copy(align=it))})
                        Text("Форматирование · B / I / U можно сочетать",
                            style=MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                            FilterChip(selected=layer.bold,
                                onClick={edit(layer.copy(bold=!layer.bold))},
                                label={Text("B · Жирный")},modifier=Modifier.heightIn(min=48.dp))
                            FilterChip(selected=layer.italic,
                                onClick={edit(layer.copy(italic=!layer.italic))},
                                label={Text("I · Курсив")},modifier=Modifier.heightIn(min=48.dp))
                        }
                        FilterChip(selected=layer.underline,
                            onClick={edit(layer.copy(underline=!layer.underline))},
                            label={Text("U · Подчёркнутый")},modifier=Modifier.heightIn(min=48.dp))
                        Text("Межбуквенный интервал: "+label080(layer.letterSpacingMm)+" мм",
                            style=MaterialTheme.typography.bodySmall)
                        Slider(value=layer.letterSpacingMm,
                            onValueChange={edit(layer.copy(letterSpacingMm=it))},
                            valueRange=-.2f..0.8f)
                    }
                    PickOne("Поворот",listOf("0","90","180","270"),
                        layer.rotation.toString(),
                        {edit(layer.copy(rotation=it.toInt()))})
                    if(layer.kind !in setOf("qr","code"))
                        OutlinedButton(onClick={
                            commit(layout.remove(layer.id))
                            selected="plant"
                        },modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){
                            Icon(Icons.Outlined.DeleteOutline,null)
                            Spacer(Modifier.width(7.dp));Text("Удалить элемент")
                        }
                }
            }
        }
        val newKinds=listOf("date","mother","note","text","medium","passage","plant")
            .filter{kind->kind=="text"||layout.layers.none{it.kind==kind}}
        if(newKinds.isNotEmpty()){
            var toAdd by rememberSaveable{mutableStateOf(newKinds.first())}
            PickOne("Добавить элемент",newKinds.map{fieldNames080[it]?:it},
                fieldNames080[toAdd]?:toAdd,{name->
                    toAdd=newKinds.firstOrNull{fieldNames080[it]==name}?:newKinds.first()
                })
            OutlinedButton(onClick={
                val new=layout.add(toAdd)
                commit(new);selected=new.layers.last().id
            },modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){
                Icon(Icons.Outlined.Add,null);Spacer(Modifier.width(7.dp))
                Text("Добавить выбранное поле")
            }
        }
        HorizontalDivider()
        SectionTitle("Макет и печать")
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            listOf(203,300).forEach{v->
                FilterChip(selected=layout.dpi==v,onClick={
                    commit(layout.copy(dpi=v))
                },label={Text("$v dpi")})
            }
        }
        val (w,h)=LabLabel40.dimensions(layout.dpi)
        Text("PNG ${w} × ${h} пикселей. Физический размер 40 × 12 мм. В приложении NIIMBOT выбери этот же размер бумаги.",
            style=MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick={
            commit(LabelDesign.initial().copy(dpi=layout.dpi))
            selected="plant"
        },modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){
            Text("Сбросить к исходному макету")
        }
        Field("Название нового шаблона",templateName,{templateName=it.take(80)})
        OutlinedButton(enabled=templateName.trim().length>=2,
            onClick={
                try{db.saveTemplate080(templateName,layout)
                    templateName="";savedNotice="Шаблон сохранён";onChanged()
                }catch(e:Exception){onError(e.message?:"Не удалось сохранить шаблон")}
            },modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){
            Icon(Icons.Outlined.BookmarkAdd,null)
            Spacer(Modifier.width(7.dp));Text("Сохранить как шаблон")
        }
        templates.forEach{template->
            Row(verticalAlignment=Alignment.CenterVertically){
                TextButton(onClick={
                    commit(template.design)
                    selected="plant"
                },modifier=Modifier.weight(1f)){
                    Icon(Icons.Outlined.BookmarkBorder,null)
                    Spacer(Modifier.width(7.dp));Text(template.name,maxLines=1)
                }
                IconButton(onClick={confirmDelete=template.id}){
                    Icon(Icons.Outlined.DeleteOutline,
                        contentDescription="Удалить шаблон ${template.name}")
                }
            }
        }
        Button(onClick={save()},modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){
            Icon(Icons.Outlined.Save,null);Spacer(Modifier.width(8.dp))
            Text("Сохранить макет контейнера")
        }
        Button(onClick={
            try{
                layout.verify()
                exportLayout=layout
                export.launch("dikoveriya-${culture.code}-40x12.png")
            }catch(e:Exception){onError(e.message?:"Ошибка макета")}
        },modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){
            Icon(Icons.Outlined.SaveAlt,null);Spacer(Modifier.width(8.dp))
            Text("Сохранить PNG 40×12")
        }
        OutlinedButton(onClick={
            try{
                val folder=File(context.cacheDir,"labels").apply{mkdirs()}
                val file=File(folder,"designer-${culture.code}-40x12.png")
                file.writeBytes(LabLabelDesigner.png(values,layout))
                val uri=FileProvider.getUriForFile(context,
                    "${context.packageName}.share",file)
                val intent=Intent(Intent.ACTION_SEND).apply{
                    type="image/png"
                    putExtra(Intent.EXTRA_STREAM,uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    clipData=ClipData.newUri(context.contentResolver,
                        "Этикетка 40×12",uri)
                }
                context.startActivity(Intent.createChooser(intent,
                    "Передать в NIIMBOT / другое приложение"))
            }catch(e:Exception){onError(e.message?:"Ошибка передачи")}
        },modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){
            Icon(Icons.Outlined.Share,null);Spacer(Modifier.width(8.dp))
            Text("Передать на печать")
        }
        if(savedNotice.isNotBlank())Text(savedNotice,
            color=Lab07Palette.Forest,
            style=MaterialTheme.typography.bodyMedium)
        HorizontalDivider()
        Lab09PrintPanel(values=values,layout=layout,onError=onError)
        Text("Официальное приложение NIIMBOT может отсутствовать в меню Android Share. Встроенная печать выше подключается к принтеру напрямую по Bluetooth. Экспорт PNG остаётся запасным вариантом.",
            style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if(fontPickerOpen && chosen!=null && chosen.kind!="qr"){
        Lab09FontPicker(selected=chosen.family,
            onSelect={id->edit(chosen.copy(family=id))},
            onDismiss={fontPickerOpen=false})
    }
    if(confirmLeave)AlertDialog(
        onDismissRequest={confirmLeave=false},
        title={Text("Закрыть без сохранения?")},
        text={Text("Изменения макета контейнера ещё не сохранены. Сначала сохрани их, если они нужны для следующих этикеток.")},
        confirmButton={Button(onClick={confirmLeave=false;onBack()}){
            Text("Выйти без сохранения")
        }},
        dismissButton={TextButton(onClick={confirmLeave=false}){
            Text("Продолжить редактирование")
        }})
    confirmDelete?.let{id->
        AlertDialog(onDismissRequest={confirmDelete=null},
            title={Text("Удалить шаблон?")},
            text={Text("Сохранённые индивидуальные макеты контейнеров не изменятся.")},
            confirmButton={Button(onClick={
                try{db.archiveTemplate080(id);onChanged();confirmDelete=null}
                catch(e:Exception){onError(e.message?:"Не удалось удалить шаблон")}
            }){Text("Удалить шаблон")}},
            dismissButton={TextButton(onClick={confirmDelete=null}){Text("Отмена")}})
    }
}
