package ru.dikoveriya.lab

import android.content.ContentValues
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.LocalDate

internal data class Lab11CustomField(val id:Long,val label:String,val type:String,
    val choices:String,val required:Boolean)
internal data class Lab11OperationTemplate(val id:Long,val title:String,
    val applicable:String,val fields:String)
internal data class Lab11RecurringRule(val id:Long,val title:String,val frequency:Int,
    val next:Long,val priority:String,val entityKind:String?,val entityId:Long?,
    val summerStart:Int,val summerEnd:Int)

internal fun LabDatabase.categories11(domain:String):List<String> =
    readableDatabase.rawQuery("""SELECT name FROM custom_categories
        WHERE domain=? AND archived=0 ORDER BY name COLLATE NOCASE""",
        arrayOf(domain)).use{c->buildList{while(c.moveToNext())add(c.getString(0))}}

internal fun LabDatabase.addCategory11(domain:String,name:String){
    require(domain in setOf("Материалы","Субстраты","Препараты","Удобрения",
        "Операции","Растения","Оборудование","Места","Метки"))
    require(name.trim().length in 2..100)
    writableDatabase.insertOrThrow("custom_categories",null,ContentValues().apply{
        put("domain",domain);put("name",name.trim())
    })
}
internal fun LabDatabase.archiveCategory11(domain:String,name:String){
    require(writableDatabase.update("custom_categories",ContentValues().apply{
        put("archived",1)
    },"domain=? AND name=?",arrayOf(domain,name))>0)
}

internal fun LabDatabase.defineField11(entity:String,label:String,
    kind:String,required:Boolean,choices:List<String> = emptyList()):Long{
    require(entity in setOf("item","culture","material","asset","purchase"))
    require(label.trim().length in 2..100)
    require(kind in setOf("text","number","date","boolean","choice","unit","photo","url"))
    require(choices.size<=100 && choices.all{it.length in 1..100})
    if(kind=="choice")require(choices.isNotEmpty()){"Укажи варианты значений"}
    return writableDatabase.insertOrThrow("custom_field_definitions",null,
        ContentValues().apply{
            put("entity_kind",entity);put("label",label.trim())
            put("value_kind",kind);put("choices_json",JSONArray(choices).toString())
            put("required",if(required)1 else 0)
        })
}
internal fun LabDatabase.fields11(entity:String):List<Lab11CustomField> =
    readableDatabase.rawQuery("""SELECT id,label,value_kind,choices_json,required
        FROM custom_field_definitions WHERE entity_kind=? AND archived=0 ORDER BY id""",
        arrayOf(entity)).use{c->buildList{
        while(c.moveToNext())add(Lab11CustomField(c.getLong(0),c.getString(1),
            c.getString(2),c.getString(3),c.getInt(4)==1))
    }}
internal fun LabDatabase.saveFieldValue11(
    fieldId:Long,entityKind:String,entityId:Long,value:String
){
    val f=fields11(entityKind).firstOrNull{it.id==fieldId}?:error("Поле не найдено")
    require(value.length<=4000)
    require(f.type!="photo"){"Для фото используй загрузку изображения"}
    require(!f.required || value.isNotBlank()){"Заполни поле "+f.label}
    if(value.isNotBlank()){
        when(f.type){
            "number","unit"->require(value.replace(',','.').toDoubleOrNull()?.isFinite()==true){
                "Укажи число"
            }
            "date"->require(validDate11(value)!=null){"Неверная дата"}
            "boolean"->require(value in setOf("true","false")){"Укажи да или нет"}
            "choice"->{
                val arr=JSONArray(f.choices)
                require((0 until arr.length()).any{arr.getString(it)==value}){"Нет такого значения"}
            }
            "url"->require(value.startsWith("https://") || value.startsWith("http://")){
                "Адрес должен начинаться с https://"
            }
        }
    }
    writableDatabase.insertWithOnConflict("custom_field_values",null,
        ContentValues().apply{
            put("field_id",fieldId);put("entity_kind",entityKind)
            put("entity_id",entityId);put("value_json",JSONObject().put("value",value).toString())
            put("changed_at",System.currentTimeMillis())
        },android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
}
internal fun LabDatabase.fieldValues11(entity:String,id:Long):Map<Long,String> =
    readableDatabase.rawQuery("""SELECT field_id,value_json FROM custom_field_values
        WHERE entity_kind=? AND entity_id=?""",
        arrayOf(entity,id.toString())).use{c->buildMap{
        while(c.moveToNext())put(c.getLong(0),JSONObject(c.getString(1)).optString("value"))
    }}
internal fun LabDatabase.archiveField11(id:Long){
    writableDatabase.update("custom_field_definitions",
        ContentValues().apply{put("archived",1)},"id=?",arrayOf(id.toString()))
}
internal fun LabDatabase.addTemplate11(name:String,applicable:String,fieldsJson:String):Long{
    require(name.trim().length in 2..100)
    require(applicable in setOf("Все","in vitro","Горшечное","Черенок","Маточник","Другое"))
    require(JSONArray(fieldsJson).length()<=30){"Слишком много полей"}
    return writableDatabase.insertOrThrow("operation_templates",null,ContentValues().apply{
        put("name",name.trim());put("applicable_kind",applicable)
        put("fields_json",fieldsJson)
    })
}
internal fun LabDatabase.templates11():List<Lab11OperationTemplate> =
    readableDatabase.rawQuery("""SELECT id,name,applicable_kind,fields_json
        FROM operation_templates WHERE archived=0 ORDER BY name""",null).use{c->buildList{
        while(c.moveToNext())add(Lab11OperationTemplate(c.getLong(0),c.getString(1),
            c.getString(2),c.getString(3)))
    }}
internal fun LabDatabase.archiveTemplate11(id:Long){
    writableDatabase.update("operation_templates",ContentValues().apply{
        put("archived",1)
    },"id=?",arrayOf(id.toString()))
}

internal fun LabDatabase.createTaskFor11(
    title:String,dueAt:Long,kind:String,priority:String,
    entityKind:String?,entityId:Long?,note:String=""
):Long{
    require(title.trim().length in 2..200 && dueAt>0)
    require(priority in setOf("Низкий","Обычный","Высокий","Срочный"))
    require(entityKind==null || entityKind in setOf("item","asset","purchase"))
    val db=writableDatabase
    db.beginTransaction()
    try{
        val id=db.insertOrThrow("tasks",null,ContentValues().apply{
            put("title",title.trim());put("due_at",dueAt)
            put("done",0);put("note",note.take(2000))
        })
        db.insertOrThrow("task_metadata",null,ContentValues().apply{
            put("task_id",id);put("kind",kind.take(60))
            put("priority",priority);put("updated_at",System.currentTimeMillis())
        })
        if(entityKind!=null && entityId!=null)
            db.insertOrThrow("task_links",null,ContentValues().apply{
                put("task_id",id);put("entity_kind",entityKind);put("entity_id",entityId)
            })
        db.insertOrThrow("task_history",null,ContentValues().apply{
            put("task_id",id);put("recorded_at",System.currentTimeMillis())
            put("action","Создана");put("next_due_at",dueAt)
            put("next_done",0);put("next_priority",priority)
            put("note",note.take(2000))
        })
        db.setTransactionSuccessful()
        return id
    }finally{db.endTransaction()}
}

internal fun LabDatabase.addRecurringRule11(
    title:String,kind:String,days:Int,nextAt:Long,priority:String,
    fromMonth:Int=1,toMonth:Int=12,entityKind:String?=null,entityId:Long?=null
):Long{
    require(title.trim().length in 2..200 && days in 1..3660 && nextAt>0)
    require(fromMonth in 1..12 && toMonth in 1..12)
    require(priority in setOf("Низкий","Обычный","Высокий","Срочный"))
    return writableDatabase.insertOrThrow("recurring_task_rules",null,ContentValues().apply{
        put("title",title.trim());put("kind",kind.take(60))
        put("frequency_days",days);put("season_start_month",fromMonth)
        put("season_end_month",toMonth);put("next_due_at",nextAt)
        put("priority",priority)
        if(entityKind!=null)put("entity_kind",entityKind)
        if(entityId!=null)put("entity_id",entityId)
    })
}
internal fun LabDatabase.recurringRules11():List<Lab11RecurringRule> =
    readableDatabase.rawQuery("""SELECT id,title,frequency_days,next_due_at,priority,
        entity_kind,entity_id,season_start_month,season_end_month
        FROM recurring_task_rules WHERE active=1 ORDER BY next_due_at""",null)
        .use{c->buildList{
            while(c.moveToNext())add(Lab11RecurringRule(c.getLong(0),c.getString(1),
                c.getInt(2),c.getLong(3),c.getString(4),
                if(c.isNull(5))null else c.getString(5),
                if(c.isNull(6))null else c.getLong(6),c.getInt(7),c.getInt(8)))
        }}
internal fun LabDatabase.disableRecurrence11(id:Long){
    writableDatabase.update("recurring_task_rules",ContentValues().apply{
        put("active",0)
    },"id=?",arrayOf(id.toString()))
}

/** Generates real tasks at most seven days ahead. No cloud/hourly automation. */
internal fun LabDatabase.generateRecurringTasks11(now:Long=System.currentTimeMillis()):Int{
    val db=writableDatabase
    db.beginTransaction()
    try{
        var count=0
        val rules=recurringRules11()
        rules.forEach{rule->
            var due=rule.next
            var processed=0
            while(due<=now+7L*86400000L && processed<50){
                val month=Instant.ofEpochMilli(due).atZone(ZoneId.systemDefault()).monthValue
                val inSeason=if(rule.summerStart<=rule.summerEnd)
                    month in rule.summerStart..rule.summerEnd
                else month>=rule.summerStart || month<=rule.summerEnd
                if(inSeason){
                    val id=db.insertOrThrow("tasks",null,ContentValues().apply{
                        put("title",rule.title);put("due_at",due);put("done",0)
                        put("note","Создана по пользовательскому расписанию #"+rule.id)
                    })
                    db.insertOrThrow("task_metadata",null,ContentValues().apply{
                        put("task_id",id);put("kind","Уход");put("priority",rule.priority)
                        put("updated_at",now)
                    })
                    if(rule.entityKind!=null && rule.entityId!=null)
                        db.insertOrThrow("task_links",null,ContentValues().apply{
                            put("task_id",id);put("entity_kind",rule.entityKind)
                            put("entity_id",rule.entityId)
                        })
                    db.insertOrThrow("task_history",null,ContentValues().apply{
                        put("task_id",id);put("recorded_at",now)
                        put("action","По расписанию");put("next_due_at",due)
                        put("next_done",0);put("next_priority",rule.priority)
                    })
                    count++
                }
                due+=rule.frequency*86400000L
                processed++
            }
            if(processed>0)
                db.update("recurring_task_rules",ContentValues().apply{
                    put("next_due_at",due)
                    put("last_generated_due",due-rule.frequency*86400000L)
                },"id=?",arrayOf(rule.id.toString()))
        }
        db.setTransactionSuccessful()
        return count
    }finally{db.endTransaction()}
}
