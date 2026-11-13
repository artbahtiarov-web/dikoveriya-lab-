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
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp

@Composable
fun Lab072BulkDialog(
    action:String,ids:List<Long>,db:LabDatabase,
    locations:List<Lab072Location>,recipes:List<RecipeRecord>,
    onClose:()->Unit,onSaved:()->Unit,onError:(String)->Unit,
    onPlaces:()->Unit=onClose,
    initialReason:String=""
){
    var title by rememberSaveable(action){mutableStateOf("")}
    var reason by rememberSaveable(action){mutableStateOf(initialReason)}
    var severity by rememberSaveable(action){mutableStateOf("Обычный")}
    var priority by rememberSaveable(action){mutableStateOf("Обычный")}
    var kind by rememberSaveable(action){mutableStateOf("Осмотр")}
    var targetId by rememberSaveable(action){mutableStateOf<Long?>(null)}
    var recipeId by rememberSaveable(action){mutableStateOf<Long?>(null)}
    var due by rememberSaveable(action){mutableStateOf("")}
    val dueValid=due.isBlank() || endOfDay072(due)!=null
    val ready=when(action){
        "group"->title.isNotBlank() && reason.isNotBlank()
        "move"->targetId!=null && reason.isNotBlank()
        "control"->reason.isNotBlank() && title.isNotBlank() && dueValid
        "task"->title.isNotBlank() && endOfDay072(due)!=null
        "medium"->recipeId!=null && reason.isNotBlank()
        "note"->reason.isNotBlank()
        else->false
    }
    val heading=when(action){
        "group"->"Создать производственную группу"
        "move"->"Указать место хранения"
        "control"->"Назначить проверку"
        "medium"->"Сменить среду у выбранных"
        "note"->"Общая заметка для контейнеров"
        else->"Поставить задачи"
    }
    AlertDialog(onDismissRequest=onClose,title={Text(heading)},
        text={Column(Modifier.fillMaxWidth().heightIn(max=505.dp)
            .verticalScroll(rememberScrollState()),
            verticalArrangement=Arrangement.spacedBy(10.dp)){
            Text("Выбрано контейнеров: "+ids.size,
                style=MaterialTheme.typography.titleMedium)
            Text("Операция охватит каждый выбранный контейнер. Если запись одного контейнера невозможна, не изменится ни один.",
                style=MaterialTheme.typography.bodySmall)
            when(action){
                "group"->{
                    Field("Название группы",title,{title=it})
                    Field("Почему объединяются (источник, операция)",reason,{reason=it},single=false)
                    Text("Разрешена только группа одного сорта и одного маточника. Контейнеры с неизвестным происхождением не получают выдуманную генеалогию.",
                        style=MaterialTheme.typography.bodySmall)
                }
                "move"->{
                    PickOne("Точный адрес",listOf("Выбрать")+locations.map{it.path},
                        locations.firstOrNull{it.id==targetId}?.path?:"Выбрать",
                        {picked->targetId=locations.firstOrNull{it.path==picked}?.id})
                    OutlinedButton(onClick=onPlaces){
                        Icon(Icons.Outlined.AddLocation,null)
                        Spacer(Modifier.width(7.dp))
                        Text("Создать адрес хранения")
                    }
                    Field("Причина перемещения",reason,{reason=it},single=false)
                    Text("Прежнее свободное поле места остаётся в истории; новый адрес записывается каждому контейнеру.",
                        style=MaterialTheme.typography.bodySmall)
                }
                "control"->{
                    Field("Причина: что обнаружено",reason,{reason=it},single=false)
                    Field("Что нужно сделать",title,{title=it})
                    PickOne("Важность проблемы",listOf("Обычный","Важный","Критический"),
                        severity,{severity=it})
                    Field("Дата проверки, дд.мм.гггг (необязательно)",due,{due=it})
                    if(due.isNotBlank()){
                        PickOne("Приоритет задачи",listOf("Низкий","Обычный","Высокий","Срочный"),
                            priority,{priority=it})
                    }
                    Text("Критичность проблемы и просроченность — разные признаки. Без даты не создаётся задача со случайным сроком.",
                        style=MaterialTheme.typography.bodySmall)
                }
                "medium"->{
                    Text("Разрешена только утверждённая рецептура для выбранных растений. Старые партии будут отсоединены, а каждая смена попадёт в журнал.",
                        style=MaterialTheme.typography.bodySmall)
                    val approved=recipes.filter{it.status=="Утверждена"}
                    PickOne("Новая рецептура",listOf("Выбрать")+approved.map{it.name+" · v"+it.version},
                        approved.firstOrNull{it.id==recipeId}?.let{it.name+" · v"+it.version}?:"Выбрать",
                        {label->recipeId=approved.firstOrNull{it.name+" · v"+it.version==label}?.id})
                    Field("Почему изменена среда",reason,{reason=it},single=false)
                }
                "note"->{
                    Text("Одна текстовая заметка будет привязана к каждому контейнеру. Это не заменяет отдельные количественные измерения.",
                        style=MaterialTheme.typography.bodySmall)
                    Field("Что наблюдалось в группе",reason,{reason=it},single=false)
                }
                "task"->{
                    Field("Что нужно сделать",title,{title=it},single=false)
                    Field("Срок, дд.мм.гггг",due,{due=it})
                    PickOne("Приоритет",listOf("Низкий","Обычный","Высокий","Срочный"),
                        priority,{priority=it})
                    PickOne("Тип работы",listOf("Осмотр","Пересадка","Уход","Подготовка среды","Другое"),
                        kind,{kind=it})
                    PickOne("Связанная рецептура (необязательно)",
                        listOf("Нет")+recipes.map{it.name+" · v"+it.version},
                        recipes.firstOrNull{it.id==recipeId}?.let{it.name+" · v"+it.version}?:"Нет",
                        {label->recipeId=recipes.firstOrNull{it.name+" · v"+it.version==label}?.id})
                }
            }
            if(!dueValid)Text("Некорректная дата, пример: 20.11.2026",
                color=MaterialTheme.colorScheme.error)
        }},
        confirmButton={Button(enabled=ready,onClick={
            try{
                when(action){
                    "group"->db.createGroup072(ids,title,reason)
                    "move"->db.moveContainers072(ids,requireNotNull(targetId),reason)
                    "control"->db.flagControl072(ids,reason,severity,
                        endOfDay072(due),title,priority)
                    "task"->db.createTasksBulk072(ids,title,requireNotNull(endOfDay072(due)),
                        priority,kind,recipeId)
                    "medium"->db.changeMediumBulk072(ids,requireNotNull(recipeId),reason)
                    "note"->db.recordGroupNote072(ids,reason)
                }
                onSaved()
            }catch(e:Exception){onError(e.message?:"Ошибка записи")}
        }){Text("Применить ко всем")}},
        dismissButton={TextButton(onClick=onClose){Text("Отмена")}})
}

@Composable
fun Lab072GroupsScreen(db:LabDatabase,revision:Int,
    onBack:()->Unit,onNavigate:(String)->Unit,
    modifier:Modifier=Modifier
){
    var query by rememberSaveable{mutableStateOf("")}
    val groups=remember(revision){db.groups072()}
    val visible=groups.filter{it.title.contains(query,true) ||
        it.code.contains(query,true) || it.plant.contains(query,true)}
    LazyColumn(modifier=modifier,state=rememberLazyListState(),
        contentPadding=PaddingValues(16.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)){
        item(key="header"){
            Lab07ScreenHeader("ЛАБОРАТОРИЯ / ГРУППЫ","Группы контейнеров",
                "Группа объединяет записи для работы, но у каждого контейнера остаётся собственный номер и история.",onBack)
        }
        item(key="search"){Field("Найти группу, сорт или код",query,{query=it})}
        item(key="new"){
            Text("Новая группа создаётся из выделенных контейнеров в рабочем списке.",
                style=MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick={onNavigate(Lab07Navigation.work("all"))}){
                Text("Выбрать контейнеры")
            }
        }
        item(key="count"){SectionTitle("Группы · "+visible.size)}
        if(visible.isEmpty())item(key="empty"){Text("Подходящих групп пока нет.")}
        items(visible,key={"group-"+it.id}){g->
            Lab07QuickAction(g.code+" · "+g.title,
                {Icon(Icons.Outlined.Layers,null)},onClick={
                    onNavigate(Lab07Navigation.groupWork(g.id))
                })
            Text(g.plant+" · "+g.memberCount+" контейнеров",
                style=MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun Lab072PlacesScreen(db:LabDatabase,revision:Int,
    onBack:()->Unit,onChanged:()->Unit,onError:(String)->Unit,
    modifier:Modifier=Modifier
){
    val locations=remember(revision){db.locations072()}
    var level by rememberSaveable{mutableStateOf("Помещение")}
    var parent by rememberSaveable{mutableStateOf<Long?>(null)}
    var label by rememberSaveable{mutableStateOf("")}
    val steps=listOf("Помещение","Стеллаж","Полка","Позиция")
    val position=steps.indexOf(level)
    val parents=if(position>0)locations.filter{it.level==steps[position-1]}
        else emptyList()
    LazyColumn(modifier=modifier,state=rememberLazyListState(),
        contentPadding=PaddingValues(16.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)){
        item(key="header"){
            Lab07ScreenHeader("ЛАБОРАТОРИЯ / РАСПОЛОЖЕНИЕ","Где стоят контейнеры",
                "Помещение → стеллаж → полка → позиция. У старых контейнеров прежний текст места сохранён.",onBack)
        }
        item(key="form"){
            Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)){
                Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                    Text("Добавить место",style=MaterialTheme.typography.titleMedium)
                    PickOne("Уровень",steps,level,{level=it;parent=null})
                    if(position>0)PickOne("Внутри",listOf("Выбрать")+parents.map{it.path},
                        parents.firstOrNull{it.id==parent}?.path?:"Выбрать",
                        {value->parent=parents.firstOrNull{it.path==value}?.id})
                    Field("Название, например «Стеллаж 2»",label,{label=it})
                    Button(enabled=label.isNotBlank() && (position==0||parent!=null),
                        onClick={
                            try{
                                db.createLocation072(parent,level,label)
                                label="";onChanged()
                            }catch(e:Exception){onError(e.message?:"Ошибка адреса")}
                        },modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){
                        Text("Сохранить место")
                    }
                }
            }
        }
        item(key="count"){SectionTitle("Места · "+locations.size)}
        if(locations.isEmpty())item(key="empty"){Text("Адреса не заполнены. Начни с помещения.")}
        items(locations,key={"location-"+it.id}){l->
            Lab07ReadOnlyCard(l.level+" · "+l.name,l.path)
        }
    }
}
