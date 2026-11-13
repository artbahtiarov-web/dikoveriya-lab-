package ru.dikoveriya.lab

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

private val priorities072=listOf("Низкий","Обычный","Высокий","Срочный")
private val levels072=listOf("Обычный","Важный","Критический")

/** Users select a civil calendar day. No automatic biological schedule is inferred. */
internal fun calendarDay072(raw:String):Long? {
    if(raw.isBlank())return null
    return try {
        SimpleDateFormat("dd.MM.yyyy",Locale("ru")).apply{isLenient=false}
            .parse(raw.trim())?.time
    } catch(_:Exception){null}
}
internal fun endOfDay072(raw:String):Long?=calendarDay072(raw)?.plus(86400000L-1)

@Composable
fun Lab072RegistryScreen(
    mode:String,initialGroupId:Long?,initialPlantId:Long?=null,db:LabDatabase,revision:Int,
    plants:List<PlantRecord>,recipes:List<RecipeRecord>,
    onBack:()->Unit,onNavigate:(String)->Unit,
    onChanged:()->Unit,onError:(String)->Unit,
    onExportLabels:(List<Long>)->Unit = {},
    onPrintLabels:(List<Long>)->Unit = {},
    onNewContainer:(()->Unit)?=null,
    modifier:Modifier=Modifier
) {
    val title=when(mode) {
        "vitro"->"В пробирках"
        "attention"->"Требуют внимания"
        "growth"->"Растут"
        else->"Все контейнеры"
    }
    val subtitle=when(mode) {
        "vitro"->"Все живые контейнеры в стерильной культуре"
        "attention"->"Проблемы, карантин и запланированные проверки"
        "growth"->"Адаптация, доращивание и фактические измерения"
        else->"Поиск по номеру, растению, линии, среде или месту"
    }
    var query by rememberSaveable(mode){mutableStateOf("")}
    var plantId by rememberSaveable(mode){mutableStateOf(initialPlantId)}
    var recipeId by rememberSaveable(mode){mutableStateOf<Long?>(null)}
    var locationId by rememberSaveable(mode){mutableStateOf<Long?>(null)}
    var groupId by rememberSaveable(mode){mutableStateOf(initialGroupId)}
    var stage by rememberSaveable(mode){mutableStateOf("")}
    var status by rememberSaveable(mode){mutableStateOf("")}
    var period by rememberSaveable(mode){mutableStateOf("Всё время")}
    var order by rememberSaveable(mode){mutableStateOf("Новые")}
    var advanced by rememberSaveable(mode){mutableStateOf(false)}
    var pages by rememberSaveable(mode){mutableIntStateOf(1)}
    var selected by rememberSaveable(mode){mutableStateOf(emptyList<Long>())}
    var selecting by rememberSaveable(mode){mutableStateOf(false)}
    var action by rememberSaveable(mode){mutableStateOf("")}
    var viewName by rememberSaveable(mode){mutableStateOf("")}
    var viewDialog by rememberSaveable(mode){mutableStateOf(false)}
    var shownId by rememberSaveable(mode){mutableStateOf<Long?>(null)}
    var resolveId by rememberSaveable(mode){mutableStateOf<Long?>(null)}
    var resolution by rememberSaveable(mode){mutableStateOf("")}
    var restoreStatus by rememberSaveable(mode){mutableStateOf(false)}
    val groups=remember(revision){db.groups072()}
    val locations=remember(revision){db.locations072()}
    val views=remember(revision){db.savedViews072(mode)}
    val base=System.currentTimeMillis()
    val from=when(period){"7 дней"->base-7*86400000L;"30 дней"->base-30*86400000L;else->null}
    val filter=Lab072Query(mode=mode,text=query,plantId=plantId,recipeId=recipeId,
        groupId=groupId,locationId=locationId,stage=stage,status=status,
        fromDate=from,order=order)
    var result by remember {mutableStateOf(emptyList<Lab072Culture>())}
    var total by remember {mutableIntStateOf(0)}
    var loading by remember {mutableStateOf(true)}
    LaunchedEffect(filter,pages,revision) {
        loading=true
        try{
            val data=withContext(Dispatchers.IO) {
                val count=db.countCultures072(filter)
                val limit=(pages*40).coerceAtMost(count)
                val records=buildList {
                    for(offset in 0 until limit step 40)
                        addAll(db.culturesPage072(filter,offset,minOf(40,limit-offset)))
                }
                count to records
            }
            total=data.first;result=data.second
        }catch(e:Exception){onError(e.message?:"Ошибка поиска")}
        finally{loading=false}
    }
    fun reset(){pages=1;selected=emptyList()}
    fun changed(block:()->Unit){
        try{block();action="";selected=emptyList();selecting=false;onChanged()}
        catch(e:Exception){onError(e.message?:"Не удалось сохранить")}
    }
    val listState=rememberLazyListState()
    LazyColumn(modifier=modifier,state=listState,
        contentPadding=PaddingValues(16.dp),
        verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item(key="top") {
            Lab07ScreenHeader("ЛАБОРАТОРИЯ / РАБОЧИЙ СПИСОК",
                initialPlantId?.let {id->
                    plants.firstOrNull{it.id==id}?.name
                } ?: title,subtitle,onBack)
        }
        if(onNewContainer!=null)item(key="new-container"){
            Button(onClick=onNewContainer,
                modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){
                Icon(Icons.Outlined.AddCircleOutline,null)
                Spacer(Modifier.width(7.dp))
                Text("Создать контейнер этого сорта")
            }
        }
        item(key="search") {
            Field("Найти контейнер, сорт, группу или место",query,
                {query=it;reset()})
        }
        item(key="sort") {
            Lab072Chips(listOf("Всё время","7 дней","30 дней"),period,
                {period=it;reset()})
            Spacer(Modifier.height(6.dp))
            PickOne("Порядок",listOf("Новые","Старые","Название","Место",
                "Последний осмотр","По побегам","По растениям"),order,
                {order=it;reset()})
        }
        item(key="filters") {
            OutlinedButton(onClick={advanced=!advanced},
                modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){
                Icon(Icons.Outlined.Tune,null)
                Spacer(Modifier.width(8.dp))
                Text(if(advanced)"Скрыть фильтры" else "Фильтры: сорт, среда, этап, группа, место")
                Spacer(Modifier.weight(1f))
                Icon(if(advanced)Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,null)
            }
        }
        if(advanced)item(key="advanced"){
            Column(verticalArrangement=Arrangement.spacedBy(9.dp)) {
                PickOne("Сорт",listOf("Все")+plants.map{it.name},
                    plants.firstOrNull{it.id==plantId}?.name?:"Все",{value->
                        plantId=plants.firstOrNull{it.name==value}?.id;reset()
                    })
                PickOne("Питательная среда",listOf("Все")+recipes.map{it.name+" · v"+it.version},
                    recipes.firstOrNull{it.id==recipeId}?.let{it.name+" · v"+it.version}?:"Все",
                    {value->recipeId=recipes.firstOrNull{it.name+" · v"+it.version==value}?.id;reset()})
                PickOne("Этап",listOf("Все")+LabTerms.stages,
                    stage.ifBlank{"Все"},{stage=if(it=="Все")"" else it;reset()})
                PickOne("Состояние",listOf("Все")+LabTerms.statuses,
                    status.ifBlank{"Все"},{status=if(it=="Все")"" else it;reset()})
                PickOne("Производственная группа",listOf("Все")+groups.map{it.code+" · "+it.title},
                    groups.firstOrNull{it.id==groupId}?.let{it.code+" · "+it.title}?:"Все",
                    {value->groupId=groups.firstOrNull{it.code+" · "+it.title==value}?.id;reset()})
                PickOne("Место хранения",listOf("Все")+locations.map{it.path},
                    locations.firstOrNull{it.id==locationId}?.path?:"Все",
                    {value->locationId=locations.firstOrNull{it.path==value}?.id;reset()})
                Text("По побегам и по растениям — только последние фактические измерения. Пустые поля не считаются нулём.",
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                    OutlinedButton(onClick={viewDialog=true},modifier=Modifier.weight(1f)){
                        Text("Сохранить фильтр")
                    }
                    OutlinedButton(onClick={onNavigate(Lab07Navigation.GROUPS)},modifier=Modifier.weight(1f)){
                        Text("Группы")
                    }
                }
                if(views.isNotEmpty()){
                    PickOne("Мои фильтры",listOf("Выбрать")+views.map{it.name},"Выбрать",
                        {label->
                            views.firstOrNull{it.name==label}?.let{view->
                                val saved=filter.withSaved(view.config)
                                query=saved.text;plantId=saved.plantId;recipeId=saved.recipeId
                                groupId=saved.groupId;locationId=saved.locationId
                                stage=saved.stage;status=saved.status;order=saved.order
                                period=org.json.JSONObject(view.config).optString("period","Всё время")
                reset()
                            }
                        })
                }
            }
        }
        item(key="count"){
            Row(verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.SpaceBetween,modifier=Modifier.fillMaxWidth()){
                Text("Найдено: $total",style=MaterialTheme.typography.titleMedium)
                TextButton(onClick={selecting=!selecting;selected=emptyList()}){
                    Text(if(selecting)"Отмена выбора" else "Выбрать несколько")
                }
            }
        }
        if(selecting)item(key="bulk"){
            Column(verticalArrangement=Arrangement.spacedBy(7.dp)) {
                Text("Выбрано: "+selected.size+" (не более 200 за операцию)",
                    style=MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){
                    OutlinedButton(onClick={action="group"},enabled=selected.isNotEmpty(),
                        modifier=Modifier.weight(1f)){Text("В группу")}
                    OutlinedButton(onClick={action="move"},enabled=selected.isNotEmpty(),
                        modifier=Modifier.weight(1f)){Text("Переместить")}
                }
                Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){
                    OutlinedButton(onClick={action="control"},enabled=selected.isNotEmpty(),
                        modifier=Modifier.weight(1f)){Text("На контроль")}
                    OutlinedButton(onClick={action="task"},enabled=selected.isNotEmpty(),
                        modifier=Modifier.weight(1f)){Text("Поставить задачу")}
                }
                Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){
                    OutlinedButton(onClick={action="medium"},enabled=selected.isNotEmpty(),
                        modifier=Modifier.weight(1f)){Text("Сменить среду")}
                    OutlinedButton(onClick={action="note"},enabled=selected.isNotEmpty(),
                        modifier=Modifier.weight(1f)){Text("Общая заметка")}
                }
                OutlinedButton(onClick={onExportLabels(selected)},enabled=selected.isNotEmpty(),
                    modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){
                    Icon(Icons.Outlined.QrCode,null)
                    Spacer(Modifier.width(8.dp))
                    Text("Этикетки 40×12 мм · ZIP")
                }
            }
        }
        if(loading)item(key="loading"){CircularProgressIndicator()}
        if(!loading && result.isEmpty())item(key="empty"){
            Lab072EmptyMode(mode,
                onNavigate=onNavigate,
                onCreateTask={action="newTask"})
        }
        items(result,key={"culture-"+it.id}){row->
            Lab072CultureCard(row,mode,selecting,selected.contains(row.id),
                onClick={
                    if(selecting){
                        selected=if(selected.contains(row.id))selected-row.id
                            else if(selected.size<200)selected+row.id else selected
                    } else onNavigate(Lab07Navigation.container(row.id))
                },
                onAttention={shownId=row.id})
        }
        if(result.size<total)item(key="more"){
            OutlinedButton(onClick={pages++},
                modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){
                Text("Показать ещё 40 · осталось "+(total-result.size))
            }
        }
        item(key="tip"){
            Text("Группы и места вводятся вручную. Записи без подтверждённого происхождения не объединяются автоматически.",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if(viewDialog){
        AlertDialog(onDismissRequest={viewDialog=false},title={Text("Сохранить набор фильтров")},
            text={Field("Название, например «Алоказии на проверке»",viewName,{viewName=it})},
            confirmButton={Button(onClick={
                changed{
                    val stored=org.json.JSONObject(filter.json()).put("period",period).toString()
                    db.saveView072(mode,viewName,stored)
                    viewDialog=false;viewName=""
                }
            },enabled=viewName.isNotBlank()){Text("Сохранить")}},
            dismissButton={TextButton(onClick={viewDialog=false}){Text("Отмена")}})
    }
    if(action.isNotEmpty()){
        Lab072BulkDialog(action,selected,db,locations,recipes,
            onClose={action=""},onSaved={changed{}},onError=onError,
            onPlaces={action="";onNavigate(Lab07Navigation.PLACES)})
    }
    shownId?.let{id->
        val cases=remember(id,revision){db.cases072(id)}
        AlertDialog(onDismissRequest={shownId=null},title={Text("Причины контроля")},
            text={Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement=Arrangement.spacedBy(9.dp)){
                if(cases.isEmpty())Text("Контейнер отмечен для контроля, но причина ранее не была указана. Уточни её в новой записи.")
                cases.forEach{item->
                    Text(item.reason,style=MaterialTheme.typography.titleMedium)
                    Text("Важность: "+item.severity,style=MaterialTheme.typography.bodySmall)
                    Text("Действие: "+item.action,style=MaterialTheme.typography.bodySmall)
                    item.dueAt?.let{Text("Срок: "+formatDate(it),style=MaterialTheme.typography.bodySmall)}
                    if(item.closedAt==null)
                        OutlinedButton(onClick={resolveId=item.id;shownId=null}){Text("Записать результат")}
                    else Text("Закрыто: "+item.resolution,style=MaterialTheme.typography.bodySmall)
                    HorizontalDivider()
                }
            }},
            confirmButton={TextButton(onClick={shownId=null;onNavigate(Lab07Navigation.growth(id))}){
                Text("Провести осмотр")
            }},
            dismissButton={TextButton(onClick={shownId=null}){Text("Закрыть")}})
    }
    resolveId?.let{id->
        AlertDialog(onDismissRequest={resolveId=null},
            title={Text("Результат проверки")},
            text={Column{
                Field("Что установлено и какие действия выполнены",resolution,{resolution=it},single=false)
                Row(verticalAlignment=Alignment.CenterVertically){
                    Checkbox(checked=restoreStatus,onCheckedChange={restoreStatus=it})
                    Text("Вернуть к обычной работе, если других проблем нет",
                        style=MaterialTheme.typography.bodySmall)
                }
                Text("Связанная задача автоматически завершится. Карантин снимается только отдельным решением. Старые задачи без связи не затрагиваются.",
                    style=MaterialTheme.typography.bodySmall)
            }},
            confirmButton={Button(enabled=resolution.isNotBlank(),onClick={
                changed{db.resolveCase072(id,resolution,restoreStatus);resolveId=null;
                    resolution="";restoreStatus=false}
            }){Text("Сохранить результат")}},
            dismissButton={TextButton(onClick={resolveId=null}){Text("Отмена")}})
    }
}

@Composable
internal fun Lab072Chips(values:List<String>,selected:String,onSelect:(String)->Unit){
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement=Arrangement.spacedBy(7.dp)){
        values.forEach{value->
            FilterChip(selected=selected==value,onClick={onSelect(value)},
                modifier=Modifier.heightIn(min=48.dp),
                label={Text(value,maxLines=1)},
                colors=FilterChipDefaults.filterChipColors(
                    selectedContainerColor=Lab07Palette.Forest,
                    selectedLabelColor=Lab07Palette.Champagne))
        }
    }
}
@Composable
private fun Lab072CultureCard(
    c:Lab072Culture,mode:String,selecting:Boolean,selected:Boolean,
    onClick:()->Unit,onAttention:()->Unit
){
    val attention=c.status in setOf(LabTerms.CONTROL,LabTerms.QUARANTINE) ||
        c.attentionReason!=null
    val overdue=c.checkDue!=null && c.checkDue<System.currentTimeMillis()
    val levelColor=when{
        overdue||c.severity=="Критический"->MaterialTheme.colorScheme.error
        c.severity=="Важный"->Lab07Palette.InkBrass
        attention->Lab07Palette.Moss
        else->Lab07Palette.Forest
    }
    OutlinedCard(onClick=onClick,modifier=Modifier.fillMaxWidth().heightIn(min=103.dp),
        colors=CardDefaults.outlinedCardColors(containerColor=MaterialTheme.colorScheme.surface),
        border=BorderStroke(1.dp,if(attention)levelColor else MaterialTheme.colorScheme.outlineVariant)){
        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
                if(selecting)Checkbox(checked=selected,onCheckedChange={onClick()})
                Column(Modifier.weight(1f)){
                    Text(c.plant,style=MaterialTheme.typography.titleMedium,maxLines=2)
                    Text(c.code+" · "+c.quantity+" "+c.kind,
                        style=MaterialTheme.typography.bodySmall)
                }
                Icon(Icons.Outlined.ChevronRight,contentDescription="Открыть "+c.code)
            }
            Text(c.status+" · "+c.stage+" · "+c.location.ifBlank{"Место не задано"},
                style=MaterialTheme.typography.bodySmall)
            if(c.groupCode.isNotBlank())Text("Группа: "+c.groupCode+" · "+c.groupTitle,
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(c.recipe.isNotBlank())Text("Среда: "+c.recipe,
                style=MaterialTheme.typography.bodySmall)
            if(mode=="growth"){
                Text("Последний замер: побегов "+(c.lastShoots?.toString()?:"не измеряли")+
                    ", растений "+(c.lastPlants?.toString()?:"не измеряли"),
                    style=MaterialTheme.typography.bodySmall)
            }
            if(mode=="attention" && attention){
                Row(verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.spacedBy(7.dp)){
                    Icon(Icons.Outlined.ReportProblem,null,tint=levelColor,
                        modifier=Modifier.size(17.dp))
                    Text(if(overdue)"Просрочен контроль" else c.severity?:"Причина не уточнена",
                        color=levelColor,style=MaterialTheme.typography.bodySmall)
                }
                Text(c.attentionReason?:"Причина не указана — зафиксируй её при осмотре",
                    style=MaterialTheme.typography.bodySmall)
                if(!selecting)TextButton(onClick=onAttention,
                    modifier=Modifier.heightIn(min=48.dp)){Text("Причина и результат проверки")}
            }
        }
    }
}
@Composable
private fun Lab072EmptyMode(mode:String,onNavigate:(String)->Unit,onCreateTask:()->Unit){
    Column(verticalArrangement=Arrangement.spacedBy(11.dp)){
        Text(when(mode){
            "vitro"->"В стерильной культуре пока нет активных контейнеров по выбранным фильтрам."
            "attention"->"По этим фильтрам нет контейнеров, требующих внимания."
            "growth"->"На адаптации и доращивании контейнеров не найдено."
            else->"Контейнеры не найдены. Измени фильтр."
        },style=MaterialTheme.typography.bodyMedium)
        if(mode=="growth"){
            Text("Когда растения перейдут на адаптацию, здесь появятся их группы и фактические измерения. До перевода можно посмотреть контейнеры в пробирках.",
                style=MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick={onNavigate(Lab07Navigation.work("vitro"))}){
                Text("Посмотреть растения в пробирках")
            }
        }
        if(mode=="attention"){
            Text("Исторический статус «На контроле» не содержит обязательной причины. Новая запись контроля сохраняет её отдельно.",
                style=MaterialTheme.typography.bodySmall)
        }
    }
}
