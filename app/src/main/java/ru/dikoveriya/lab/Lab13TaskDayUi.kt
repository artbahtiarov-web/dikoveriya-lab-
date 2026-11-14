package ru.dikoveriya.lab

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Calendar -> selected date -> its own task list -> +.
 * This view deliberately ignores registry filter state; no hidden constraints.
 */
@Composable
internal fun Lab13TaskDayPage(
    dateText:String,db:LabDatabase,revision:Int,recipes:List<RecipeRecord>,
    onBack:()->Unit,onNavigate:(String)->Unit,
    onChanged:()->Unit,onError:(String)->Unit,modifier:Modifier=Modifier
){
    val context=LocalContext.current
    val day=remember(dateText){validDate11(dateText)}
    var add by remember{mutableStateOf(false)}
    var editReminder by remember{mutableStateOf<Long?>(null)}
    var editTask by remember{mutableStateOf<Long?>(null)}
    var page by remember{mutableIntStateOf(1)}
    if(day==null){
        Text("Не удалось открыть выбранную дату")
        return
    }
    val filter=remember(dateText){Lab072TaskFilter(
        period="Все",dueFrom=calendarDay072(dateText),
        dueTo=endOfDay072(dateText),order="Ближайшие"
    )}
    val count=remember(revision,dateText){db.taskCount072(filter)}
    val tasks=remember(revision,dateText,page){
        val n=(page*40).coerceAtMost(count)
        buildList{
            for(offset in 0 until n step 40)
                addAll(db.tasksPage072(filter,offset,minOf(40,n-offset)))
        }
    }
    Box(modifier.fillMaxSize()){
        LazyColumn(modifier=Modifier.fillMaxSize(),
            contentPadding=PaddingValues(start=16.dp,end=16.dp,bottom=95.dp,top=16.dp),
            verticalArrangement=Arrangement.spacedBy(12.dp)){
            item{
                Row(verticalAlignment=Alignment.CenterVertically){
                    IconButton(onClick=onBack){
                        Icon(Icons.Outlined.ArrowBack,"К календарю")
                    }
                    Column(Modifier.weight(1f)){
                        Text(day.dayOfWeek.getDisplayName(TextStyle.FULL,Locale("ru"))
                            .replaceFirstChar{it.titlecase(Locale("ru"))},
                            style=MaterialTheme.typography.titleMedium)
                        Text(dateText,style=MaterialTheme.typography.headlineSmall)
                    }
                }
                HorizontalDivider()
            }
            item{Text("Задачи на этот день · "+count,
                style=MaterialTheme.typography.titleLarge)}
            if(tasks.isEmpty())item{
                OutlinedCard(modifier=Modifier.fillMaxWidth()){
                    Column(Modifier.padding(20.dp),
                        verticalArrangement=Arrangement.spacedBy(8.dp)){
                        Text("На этот день задач нет",
                            style=MaterialTheme.typography.titleMedium)
                        Text("Нажми «+», чтобы назначить задачу именно на "+dateText+".",
                            style=MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            items(tasks,key={it.id}){t->
                val details=remember(t.id,revision){db.taskDetails13(t.id)}
                OutlinedCard(modifier=Modifier.fillMaxWidth()){
                    Column(Modifier.padding(12.dp),
                        verticalArrangement=Arrangement.spacedBy(8.dp)){
                        Row(verticalAlignment=Alignment.CenterVertically){
                            Checkbox(checked=t.done,onCheckedChange={checked->
                                try{
                                    db.updateTask072(t.id,done=checked,
                                        note=if(checked)"Выполнена" else "Возобновлена")
                                    Lab11Reminders.cancel(context,t.id)
                                    Lab11Reminders.sync(context,db)
                                    onChanged()
                                }catch(e:Exception){
                                    onError(e.message?:"Не удалось отметить задачу")
                                }
                            })
                            Column(Modifier.weight(1f)){
                                Text(t.title,style=MaterialTheme.typography.titleMedium)
                                Text(if(t.done)"Выполнено" else t.priority+" · "+t.kind,
                                    style=MaterialTheme.typography.bodySmall)
                            }
                        }
                        if(details!=null){
                            val objectName=details.objectText.ifBlank{
                                when(details.objectKind){
                                    "Контейнер in vitro"->t.cultureCode.orEmpty()
                                    else->details.objectId?.let{
                                        if(details.objectKind=="Оборудование")
                                            db.assets11().firstOrNull{x->x.id==it}?.name
                                        else db.item11(it)?.let{x->x.plantName+" · "+x.code}
                                    }.orEmpty()
                                }
                            }
                            if(details.objectKind.isNotBlank() && details.objectKind!="Не привязана")
                                Text("Объект: "+details.objectKind+
                                    if(objectName.isNotBlank())" · "+objectName else "",
                                    style=MaterialTheme.typography.bodySmall)
                            if(details.pest.isNotBlank())
                                Text("Вредитель: "+details.pest)
                            if(details.treatment.isNotBlank())
                                Text("Обработка: "+details.treatment)
                            if(details.delivery.isNotBlank())
                                Text("Продажа/отправка: "+details.delivery)
                            Text("Напоминание: "+(details.reminderAt?.let{
                                java.time.Instant.ofEpochMilli(it)
                                    .atZone(java.time.ZoneId.systemDefault())
                                    .format(DateTimeFormatter.ofPattern("HH:mm"))
                            }?:"выключено"),style=MaterialTheme.typography.bodySmall,
                                color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                            TextButton(onClick={editReminder=t.id}){
                                Text("Напоминание")
                            }
                            TextButton(onClick={editTask=t.id}){
                                Text("Изменить")
                            }
                            val linkedKind=details?.objectKind
                            val linkedId=details?.objectId
                            if(linkedId!=null && linkedKind!=null &&
                                linkedKind !in setOf("Не привязана","Покупка / закупка",
                                    "Заказ / отправка","Другое")){
                                TextButton(onClick={
                                    when(linkedKind){
                                        "Контейнер in vitro"->onNavigate(Lab07Navigation.container(linkedId))
                                        "Оборудование"->onNavigate(Lab07Navigation.asset(linkedId))
                                        else->onNavigate(Lab07Navigation.item(linkedId))
                                    }
                                }){
                                    Text("К объекту")
                                    Icon(Icons.Outlined.ChevronRight,null)
                                }
                            }
                        }
                    }
                }
            }
            if(tasks.size<count)item{
                OutlinedButton(onClick={page++},modifier=Modifier.fillMaxWidth()){
                    Text("Ещё задачи · "+(count-tasks.size))
                }
            }
        }
        FloatingActionButton(onClick={add=true},
            modifier=Modifier.align(Alignment.BottomEnd).padding(18.dp)){
            Icon(Icons.Outlined.Add,"Создать задачу на "+dateText)
        }
    }
    if(add)Lab072TaskForm(db,recipes,
        onClose={add=false},
        onSaved={add=false;onChanged();Lab11Reminders.sync(context,db)},
        onError=onError,initialDate=dateText)
    editReminder?.let{id->
        Lab13ReminderEditDialog(db,id,
            onDismiss={editReminder=null},
            onSaved={
                editReminder=null;onChanged()
                Lab11Reminders.cancel(context,id)
                Lab11Reminders.sync(context,db)
            },onError=onError)
    }
    editTask?.let{id->
        val task=tasks.firstOrNull{it.id==id}
        if(task!=null){
            Lab13DayTaskEditDialog(task,db,
                onClose={editTask=null},
                onSaved={editTask=null;onChanged();Lab11Reminders.sync(context,db)},
                onError=onError)
        }
    }
}

@Composable
private fun Lab13DayTaskEditDialog(task:Lab072Task,db:LabDatabase,
    onClose:()->Unit,onSaved:()->Unit,onError:(String)->Unit){
    var title by remember(task.id){mutableStateOf(task.title)}
    var due by remember(task.id){mutableStateOf("")}
    var done by remember(task.id){mutableStateOf(task.done)}
    var reason by remember(task.id){mutableStateOf("")}
    AlertDialog(onDismissRequest=onClose,
        title={Text("Изменить задачу")},
        text={
            Column(verticalArrangement=Arrangement.spacedBy(9.dp)){
                Text("Срок: "+formatDate(task.dueAt))
                Lab11DateField("Новый срок",due,{due=it},allowClear=true)
                Row(verticalAlignment=Alignment.CenterVertically){
                    Checkbox(checked=done,onCheckedChange={done=it})
                    Text("Выполнено")
                }
                Field("Причина изменения",reason,{reason=it})
            }
        },
        confirmButton={Button(enabled=due.isBlank() || validDate11(due)!=null,
            onClick={
                try{
                    db.updateTask072(task.id,done=done,
                        dueAt=due.takeIf{it.isNotBlank()}?.let{endOfDay072(it)},
                        note=reason)
                    onSaved()
                }catch(e:Exception){onError(e.message?:"Ошибка изменения задачи")}
            }){Text("Сохранить")}},
        dismissButton={TextButton(onClick=onClose){Text("Отмена")}}
    )
}
