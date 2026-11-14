package ru.dikoveriya.lab

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun Lab072TasksScreen(
    db:LabDatabase,revision:Int,plants:List<PlantRecord>,
    recipes:List<RecipeRecord>,onBack:()->Unit,
    onNavigate:(String)->Unit,onChanged:()->Unit,
    onError:(String)->Unit,modifier:Modifier=Modifier
){
    val localContext=LocalContext.current
    var notificationsEnabled by remember{mutableStateOf(
        Lab11Reminders.permitted(localContext))}
    val grantReminders=rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()){allowed->
            notificationsEnabled=allowed
            if(allowed)runCatching{Lab11Reminders.sync(localContext,db)}
                .onFailure{onError(it.message?:"Не удалось включить напоминания")}
        }
    var period by rememberSaveable{mutableStateOf("Открытые")}
    var priority by rememberSaveable{mutableStateOf("")}
    var kind by rememberSaveable{mutableStateOf("")}
    var plantId by rememberSaveable{mutableStateOf<Long?>(null)}
    var recipeId by rememberSaveable{mutableStateOf<Long?>(null)}
    var search by rememberSaveable{mutableStateOf("")}
    var order by rememberSaveable{mutableStateOf("Ближайшие")}
    var fromText by rememberSaveable{mutableStateOf("")}
    var toText by rememberSaveable{mutableStateOf("")}
    var advanced by rememberSaveable{mutableStateOf(false)}
    var pages by rememberSaveable{mutableIntStateOf(1)}
    var editTaskId by rememberSaveable{mutableStateOf<Long?>(null)}
    var cancelTaskId by rememberSaveable{mutableStateOf<Long?>(null)}
    var cancelReason by rememberSaveable{mutableStateOf("")}
    var viewDialog by rememberSaveable{mutableStateOf(false)}
    var viewName by rememberSaveable{mutableStateOf("")}
    val filter=Lab072TaskFilter(period=period,priority=priority,kind=kind,
        plantId=plantId,recipeId=recipeId,dueFrom=calendarDay072(fromText),
        dueTo=endOfDay072(toText),text=search,order=order)
    var rows by remember{mutableStateOf(emptyList<Lab072Task>())}
    var total by remember{mutableIntStateOf(0)}
    var loading by remember{mutableStateOf(true)}
    val saved=remember(revision){db.savedViews072("tasks")}
    LaunchedEffect(filter,pages,revision){
        loading=true
        try{
            val pair=withContext(Dispatchers.IO){
                val count=db.taskCount072(filter)
                val max=(pages*40).coerceAtMost(count)
                count to buildList{
                    for(offset in 0 until max step 40)
                        addAll(db.tasksPage072(filter,offset,minOf(40,max-offset)))
                }
            }
            total=pair.first;rows=pair.second
        }catch(e:Exception){onError(e.message?:"Не удалось открыть задачи")}
        finally{loading=false}
    }
    fun update(block:()->Unit){
        try{block();onChanged()}
        catch(e:Exception){onError(e.message?:"Не удалось изменить задачу")}
    }
    LazyColumn(modifier=modifier,state=rememberLazyListState(),
        contentPadding=PaddingValues(16.dp),
        verticalArrangement=Arrangement.spacedBy(10.dp)){
        item(key="header"){
            Lab07ScreenHeader("ЛАБОРАТОРИЯ / РАБОЧИЙ ПЛАН",
                "Задачи","Что требуется сделать и когда. Приоритет указан человеком, не вычисляется автоматически.",onBack)
        }
        item(key="calendar"){
            Lab10TaskCalendar(db,revision,onDay={date->
                // A selected date is the only time constraint, never mixed with
                // stale relative-period filters or hidden previous selections.
                period="Все";fromText=date;toText=date;pages=1
                priority="";kind="";plantId=null;recipeId=null;search=""
            })
        }
        if(Build.VERSION.SDK_INT>=33 && !notificationsEnabled)
            item(key="permissions"){
                OutlinedButton(
                    onClick={grantReminders.launch(Manifest.permission.POST_NOTIFICATIONS)},
                    modifier=Modifier.fillMaxWidth()){
                    Icon(Icons.Outlined.NotificationsActive,contentDescription=null)
                    Spacer(Modifier.width(8.dp))
                    Text("Включить напоминания о задачах")
                }
            }
        item(key="search"){
            OutlinedTextField(
                value=search,
                onValueChange={search=it;pages=1},
                modifier=Modifier.fillMaxWidth(),
                singleLine=true,
                label={Text("Найти задачу, сорт или номер")},
                leadingIcon={Icon(Icons.Outlined.Search,contentDescription=null)},
                trailingIcon={
                    IconButton(onClick={advanced=true}){
                        Icon(Icons.Outlined.Tune,contentDescription="Фильтры задач")
                    }
                }
            )
        }
        item(key="count"){SectionTitle("Задачи · "+total)}
        if(loading)item(key="loading"){CircularProgressIndicator()}
        if(!loading && rows.isEmpty())item(key="empty"){
            Text("Задач по выбранным условиям нет.",
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(rows,key={"task-"+it.id}){t->
            val overdue=!t.done && t.dueAt<System.currentTimeMillis()
            val urgent=t.priority=="Срочный"
            val color=when{
                urgent||overdue->MaterialTheme.colorScheme.error
                t.priority=="Высокий"->Lab07Palette.InkBrass
                else->Lab07Palette.Forest
            }
            OutlinedCard(modifier=Modifier.fillMaxWidth(),
                border=BorderStroke(1.dp,if(overdue)color else MaterialTheme.colorScheme.outlineVariant)){
                Column(Modifier.padding(13.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){
                    Row(verticalAlignment=Alignment.CenterVertically){
                        if(period!="Отменённые")Checkbox(checked=t.done,onCheckedChange={checked->
                            update{db.updateTask072(t.id,done=checked,note=
                                if(checked)"Отмечена выполненной" else "Возобновлена")}
                        })
                        Column(Modifier.weight(1f)){
                            Text(t.title,style=MaterialTheme.typography.titleMedium)
                            Text(formatDate(t.dueAt),
                                color=if(overdue)MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                style=MaterialTheme.typography.bodySmall)
                        }
                    }
                    Text((if(overdue)"Просрочена · " else "")+
                        t.priority+" · "+t.kind,
                        color=color,style=MaterialTheme.typography.bodySmall)
                    if(t.cultureCode!=null)Text(
                        t.cultureCode+" · "+t.plant.orEmpty(),
                        style=MaterialTheme.typography.bodySmall)
                    if(!t.recipe.isNullOrBlank())Text("Среда: "+t.recipe,
                        style=MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){
                        t.cultureId?.let{id->
                            OutlinedButton(onClick={onNavigate(
                                if(t.kind=="Осмотр")Lab07Navigation.growth(id)
                                else Lab07Navigation.container(id))},
                                modifier=Modifier.weight(1f).heightIn(min=48.dp)){
                                Text(if(t.kind=="Осмотр")"Провести осмотр"
                                     else "К контейнеру")
                            }
                        }
                        if(period!="Отменённые")OutlinedButton(onClick={editTaskId=t.id},
                            modifier=Modifier.weight(1f).heightIn(min=48.dp)){
                            Text("Изменить")
                        }
                    }
                    if(period=="Отменённые"){
                        Text("Отменена — запись и история сохранены.",
                            style=MaterialTheme.typography.bodySmall)
                    }else{
                        TextButton(onClick={cancelTaskId=t.id}){
                            Icon(Icons.Outlined.Cancel,null)
                            Spacer(Modifier.width(7.dp))
                            Text("Отменить ошибочную задачу")
                        }
                    }
                }
            }
        }
        if(rows.size<total)item(key="more"){
            OutlinedButton(onClick={pages++},
                modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){
                Text("Ещё 40 · осталось "+(total-rows.size))
            }
        }
    }
    if(advanced){
        Lab10TaskFilterSheet(
            initial=Lab10TaskFilters(priority,kind,plantId,recipeId,fromText,toText,order,period),
            plants=plants,recipes=recipes,savedNames=saved.map{it.name},
            onSelectSaved={name->
                saved.firstOrNull{it.name==name}?.let{view->
                    val f=filter.withSaved(view.config)
                    period=f.period;priority=f.priority;kind=f.kind
                    plantId=f.plantId;recipeId=f.recipeId
                    search=f.text;order=f.order
                    val opts=org.json.JSONObject(view.config)
                    fromText=opts.optString("fromText","")
                    toText=opts.optString("toText","")
                    pages=1
                }
                advanced=false
            },
            onSave={advanced=false;viewDialog=true},
            onApply={v->
                priority=v.priority;kind=v.kind;plantId=v.plantId
                recipeId=v.recipeId;fromText=v.fromText;toText=v.toText
                order=v.order;period=v.period;advanced=false;pages=1
            },
            onDismiss={advanced=false}
        )
    }
    cancelTaskId?.let{id->
        AlertDialog(onDismissRequest={cancelTaskId=null},
            title={Text("Отменить задачу?")},
            text={Column(verticalArrangement=Arrangement.spacedBy(9.dp)){
                Text("Задача исчезнет из текущего рабочего списка, но останется в истории «Отменённые».")
                Field("Причина отмены",cancelReason,{cancelReason=it},single=false)
            }},
            confirmButton={Button(enabled=cancelReason.trim().length>=2,
                onClick={
                    update{db.archiveTask080(id,cancelReason)
                        cancelTaskId=null;cancelReason=""
                    }
                }){Text("Отменить задачу")}},
            dismissButton={TextButton(onClick={cancelTaskId=null}){Text("Назад")}})
    }
    if(viewDialog)AlertDialog(onDismissRequest={viewDialog=false},
        title={Text("Сохранить фильтр задач")},
        text={Field("Название набора",viewName,{viewName=it})},
        confirmButton={Button(enabled=viewName.isNotBlank(),onClick={
            update{
                val stored=org.json.JSONObject(filter.json())
                    .put("fromText",fromText).put("toText",toText).toString()
                db.saveView072("tasks",viewName,stored)
                viewDialog=false;viewName=""
            }
        }){Text("Сохранить")}},
        dismissButton={TextButton(onClick={viewDialog=false}){Text("Отмена")}})
    editTaskId?.let{id->
        rows.firstOrNull{it.id==id}?.let{task->
            Lab072TaskEdit(task,onClose={editTaskId=null},
                onSave={date,p,done,note->
                    update{db.updateTask072(id,done=done,dueAt=date,priority=p,note=note);
                        editTaskId=null}
                })
        }
    }
}

@Composable
internal fun Lab072TaskForm(db:LabDatabase,recipes:List<RecipeRecord>,
    onClose:()->Unit,onSaved:()->Unit,onError:(String)->Unit){
    var title by rememberSaveable{mutableStateOf("")}
    var deadline by rememberSaveable{mutableStateOf("")}
    var priority by rememberSaveable{mutableStateOf("Обычный")}
    var kind by rememberSaveable{mutableStateOf("Осмотр")}
    var cultureCode by rememberSaveable{mutableStateOf("")}
    var recipeId by rememberSaveable{mutableStateOf<Long?>(null)}
    var note by rememberSaveable{mutableStateOf("")}
    val date=if(validDate11(deadline)!=null)endOfDay072(deadline) else null
    AlertDialog(onDismissRequest=onClose,title={Text("Новая задача")},
        text={Column(Modifier.heightIn(max=490.dp).verticalScroll(rememberScrollState()),
            verticalArrangement=Arrangement.spacedBy(9.dp)){
            Field("Что нужно сделать",title,{title=it},single=false)
            Lab11DateField("Срок",deadline,{deadline=it})
            if(date==null)Text("Выбери дату выполнения в календаре",
                color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
            PickOne("Приоритет",listOf("Низкий","Обычный","Высокий","Срочный"),
                priority,{priority=it})
            PickOne("Вид работы",listOf("Осмотр","Пересадка","Уход",
                "Подготовка среды","Другое"),kind,{kind=it})
            Field("Контейнер, например LAB-000123 (необязательно)",cultureCode,
                {cultureCode=it})
            PickOne("Среда (необязательно)",
                listOf("Нет")+recipes.map{it.name+" · v"+it.version},
                recipes.firstOrNull{it.id==recipeId}?.let{it.name+" · v"+it.version}?:"Нет",
                {choice->recipeId=recipes.firstOrNull{
                    it.name+" · v"+it.version==choice}?.id})
            Field("Примечание (необязательно)",note,{note=it},single=false)
            Text("Приоритет и срок назначаются пользователем. Без реальной даты задача не создаётся.",
                style=MaterialTheme.typography.bodySmall)
        }},
        confirmButton={Button(enabled=title.isNotBlank() && date!=null,onClick={
            try{
                val c=if(cultureCode.isBlank())null else
                    db.findCulture072(cultureCode.trim())
                        ?: error("Такого контейнера нет: "+cultureCode.trim())
                db.createTask072(title,requireNotNull(date),priority,kind,c,recipeId,null,note)
                onSaved()
            }catch(e:Exception){onError(e.message?:"Не удалось сохранить задачу")}
        }){Text("Создать")}},
        dismissButton={TextButton(onClick=onClose){Text("Отмена")}})
}
@Composable
private fun Lab072TaskEdit(task:Lab072Task,onClose:()->Unit,
    onSave:(Long?,String?,Boolean?,String)->Unit){
    var due by rememberSaveable(task.id){mutableStateOf("")}
    var priority by rememberSaveable(task.id){mutableStateOf(task.priority)}
    var done by rememberSaveable(task.id){mutableStateOf(task.done)}
    var note by rememberSaveable(task.id){mutableStateOf("")}
    val date=if(due.isBlank())null else validDate11(due)?.let{endOfDay072(due)}
    AlertDialog(onDismissRequest=onClose,title={Text("Изменить задачу")},
        text={Column(Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement=Arrangement.spacedBy(9.dp)){
            Text(task.title,style=MaterialTheme.typography.titleMedium)
            Text("Текущий срок: "+formatDate(task.dueAt))
            Lab11DateField("Новый срок",due,{due=it},allowClear=true)
            PickOne("Приоритет",
                listOf("Не менять","Низкий","Обычный","Высокий","Срочный"),
                priority,{priority=it})
            Row(verticalAlignment=Alignment.CenterVertically){
                Checkbox(checked=done,onCheckedChange={done=it})
                Text("Выполнено")
            }
            Field("Причина изменения",note,{note=it},single=false)
        }},
        confirmButton={Button(enabled=due.isBlank()||date!=null,onClick={
            onSave(date,priority.takeUnless{it=="Не менять"||it=="Не задан"},
                done,note)
        }){Text("Сохранить")}},
        dismissButton={TextButton(onClick=onClose){Text("Отмена")}})
}
