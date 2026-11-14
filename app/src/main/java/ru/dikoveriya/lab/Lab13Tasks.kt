package ru.dikoveriya.lab

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

internal data class Lab13TaskDetails(
    val objectKind:String,val objectId:Long?,val objectText:String,
    val pest:String,val treatment:String,val delivery:String,
    val reminderAt:Long?,val ringtoneUri:String?
)
internal data class Lab13TaskSelection(
    val kind:String="",val id:Long?=null,val text:String=""
)

internal val taskTypes13=listOf(
    "Осмотр","Полив","Подкормка","Пересадка","Посадка",
    "Обработка от вредителей","Черенкование","Укоренение",
    "Подготовка к продаже","Отправка заказа","Закупка",
    "Обслуживание оборудования","Подготовка среды","Другое"
)
internal val taskObjectTypes13=listOf(
    "Не привязана","Контейнер in vitro","Горшечное растение",
    "Маточник","Черенок","Оборудование",
    "Покупка / закупка","Заказ / отправка","Другое"
)
private val virtualObjectTypes13=setOf("Покупка / закупка","Заказ / отправка","Другое")
internal fun taskMoment13(day:LocalDate,hour:Int,minute:Int):Long{
    require(hour in 0..23 && minute in 0..59)
    return day.atTime(LocalTime.of(hour,minute)).atZone(ZoneId.systemDefault())
        .toInstant().toEpochMilli()
}
internal fun LabDatabase.taskDetails13(id:Long):Lab13TaskDetails?=
    readableDatabase.rawQuery("""SELECT object_kind,object_id,object_text,
        pest,treatment,delivery_text,reminder_at,ringtone_uri
        FROM task_details13 WHERE task_id=?""",arrayOf(id.toString())).use{c->
        if(!c.moveToFirst())null else Lab13TaskDetails(c.getString(0),
            if(c.isNull(1))null else c.getLong(1),
            c.getString(2),c.getString(3),c.getString(4),c.getString(5),
            if(c.isNull(6))null else c.getLong(6),
            if(c.isNull(7))null else c.getString(7))
    }

internal fun LabDatabase.createTask13(
    title:String,dueAt:Long,priority:String,kind:String,selection:Lab13TaskSelection,
    note:String="",recipeId:Long?=null,reminderAt:Long?=null,
    ringtoneUri:String?=null,pest:String="",treatment:String="",delivery:String=""
):Long{
    require(title.trim().length in 2..200){"Напиши название задачи"}
    require(priority in setOf("Низкий","Обычный","Высокий","Срочный"))
    require(kind in taskTypes13)
    require(dueAt>0)
    require(selection.kind.isBlank() || selection.kind in taskObjectTypes13)
    require(selection.text.length<=500 && note.length<=2000 &&
        pest.length<=200 && treatment.length<=300 && delivery.length<=500)
    require(ringtoneUri==null || ringtoneUri.length<=1000)
    if(reminderAt!=null){
        require(reminderAt>0)
        val dueDay=Instant.ofEpochMilli(dueAt).atZone(ZoneId.systemDefault()).toLocalDate()
        val remindDay=Instant.ofEpochMilli(reminderAt).atZone(ZoneId.systemDefault()).toLocalDate()
        require(dueDay==remindDay){"Время напоминания должно относиться к дате задачи"}
    }
    val type=selection.kind
    if(type in virtualObjectTypes13){
        require(selection.id==null && selection.text.isNotBlank()){
            "Опиши покупку, заказ или другой объект"
        }
    }else if(type.isBlank() || type=="Не привязана"){
        require(selection.id==null)
    }else{
        require(selection.id!=null){"Выбери объект из списка или отсканируй его QR"}
    }
    val db=writableDatabase
    db.beginTransaction()
    try{
        val refTable=when(type){
            "Контейнер in vitro"->"cultures"
            "Горшечное растение","Маточник","Черенок"->"plant_items"
            "Оборудование"->"equipment_assets"
            else->null
        }
        val id=selection.id
        if(refTable!=null && id!=null){
            val row=db.rawQuery("SELECT 1 FROM $refTable WHERE id=?",
                arrayOf(id.toString())).use{it.moveToFirst()}
            require(row){"Связанный объект не найден"}
            if(refTable=="plant_items" && type!="Горшечное растение"){
                val actual=db.rawQuery("SELECT kind FROM plant_items WHERE id=?",
                    arrayOf(id.toString())).use{c->c.moveToFirst();c.getString(0)}
                require(actual==type){"Тип выбранного растения не совпадает"}
            }
        }
        val now=System.currentTimeMillis()
        val taskId=db.insertOrThrow("tasks",null,ContentValues().apply{
            if(type=="Контейнер in vitro")put("culture_id",id)
            put("title",title.trim());put("due_at",dueAt);put("done",0)
            put("note",note.trim())
        })
        db.insertOrThrow("task_metadata",null,ContentValues().apply{
            put("task_id",taskId);put("kind",kind)
            put("priority",priority);put("updated_at",now)
            if(recipeId!=null)put("recipe_id",recipeId)
        })
        if(id!=null && refTable!=null){
            db.insertOrThrow("task_links",null,ContentValues().apply{
                put("task_id",taskId)
                put("entity_kind",when(refTable) {
                    "cultures"->"culture"
                    "equipment_assets"->"asset"
                    else->"item"
                })
                put("entity_id",id)
            })
        }
        db.insertOrThrow("task_details13",null,ContentValues().apply{
            put("task_id",taskId);put("object_kind",type)
            if(id!=null)put("object_id",id)
            put("object_text",selection.text.trim())
            put("pest",pest.trim());put("treatment",treatment.trim())
            put("delivery_text",delivery.trim())
            if(reminderAt!=null)put("reminder_at",reminderAt)
            if(ringtoneUri!=null)put("ringtone_uri",ringtoneUri)
            put("created_at",now);put("updated_at",now)
        })
        db.insertOrThrow("task_history",null,ContentValues().apply{
            put("task_id",taskId);put("recorded_at",now)
            put("action","Создана");put("next_due_at",dueAt)
            put("next_done",0);put("next_priority",priority);put("note",note.trim())
        })
        db.setTransactionSuccessful()
        return taskId
    }finally{db.endTransaction()}
}

/** Set/edit a reminder independently of task completion and audit the modification. */
internal fun LabDatabase.editTaskReminder13(
    taskId:Long,at:Long?,ringtoneUri:String?
){
    require(at==null || at>0)
    require(ringtoneUri==null || ringtoneUri.length<=1000)
    val db=writableDatabase
    db.beginTransaction()
    try{
        val due=db.rawQuery("SELECT due_at FROM tasks WHERE id=?",
            arrayOf(taskId.toString())).use{c->
            require(c.moveToFirst()){"Задача не найдена"};c.getLong(0)
        }
        if(at!=null){
            val zone=ZoneId.systemDefault()
            require(Instant.ofEpochMilli(due).atZone(zone).toLocalDate() ==
                Instant.ofEpochMilli(at).atZone(zone).toLocalDate()){
                "Время должно приходиться на дату задачи"
            }
        }
        val now=System.currentTimeMillis()
        val old=taskDetails13(taskId)
        if(old==null){
            db.insertOrThrow("task_details13",null,ContentValues().apply{
                put("task_id",taskId);put("object_kind","")
                if(at!=null)put("reminder_at",at)
                if(ringtoneUri!=null)put("ringtone_uri",ringtoneUri)
                put("created_at",now);put("updated_at",now)
            })
        }else{
            db.update("task_details13",ContentValues().apply{
                if(at==null)putNull("reminder_at") else put("reminder_at",at)
                if(ringtoneUri==null)putNull("ringtone_uri")else put("ringtone_uri",ringtoneUri)
                put("updated_at",now)
            },"task_id=?",arrayOf(taskId.toString()))
        }
        db.insertOrThrow("task_history",null,ContentValues().apply{
            put("task_id",taskId);put("action","Изменено напоминание")
            put("recorded_at",now)
            put("note",if(at==null)"Выключено" else
                "Время: "+Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalTime())
        })
        db.setTransactionSuccessful()
    }finally{db.endTransaction()}
}
