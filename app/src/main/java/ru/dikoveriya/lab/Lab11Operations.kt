package ru.dikoveriya.lab

import android.content.ContentValues
import org.json.JSONObject

private val standardOps11=setOf("Посадка","Введение в культуру",
    "Субкультивирование","Черенкование","Укоренение","Пересадка",
    "Смена среды","Полив","Подкормка","Обработка","Обрезка",
    "Прищипка","Осмотр","Измерение","Перемещение","Потеря",
    "Утилизация","Исправление","Другое")

/**
 * Single atomic action for plant care and verified stock consumption.
 * Changes are committed together; the historical before-state is stored for
 * audit/correction without overwriting source lineage.
 */
internal fun LabDatabase.performItemAction11(
    itemId:Long,kind:String,at:Long,
    note:String="",newStage:String?=null,newKind:String?=null,newLocation:String?=null,
    newSubstrate:String?=null,stimulant:String?=null,
    materialId:Long?=null,materialQty:Double=0.0,
    customFields:Map<String,String> = emptyMap()
):Long{
    require(kind in standardOps11 || templates11().any{it.title==kind}){
        "Неизвестная операция. Добавь её через шаблон."
    }
    require(at>0 && at<=System.currentTimeMillis()+86400000L)
    require(note.length<=2000)
    require(newKind==null || newKind in setOf("Горшечное","Черенок","Маточник","Другое"))
    require(materialId==null || materialQty.isFinite() && materialQty>0)
    val db=writableDatabase
    db.beginTransaction()
    try{
        val before=db.rawQuery("""SELECT status,stage,location_text,substrate,stimulant,kind
            FROM plant_items WHERE id=?""",arrayOf(itemId.toString())).use{c->
            require(c.moveToFirst()){"Растение не найдено"}
            listOf(c.getString(0),c.getString(1),c.getString(2),
                c.getString(3),c.getString(4),c.getString(5))
        }
        val selectedTemplate=templates11().firstOrNull{it.title==kind}
        if(selectedTemplate!=null){
            require(selectedTemplate.applicable=="Все" ||
                selectedTemplate.applicable==before[5]){
                "Шаблон не предназначен для этого типа растения"
            }
            validateTemplateAnswers11(parseTemplateFields11(selectedTemplate.fields),customFields)
        }else require(customFields.isEmpty()){"Нельзя добавлять посторонние поля"}
        require(before[0] !in setOf("Утилизирован","Продан") || kind=="Исправление"){
            "Закрытый экземпляр нельзя изменять обычной операцией"
        }
        val details=JSONObject().put("before_status",before[0])
            .put("before_stage",before[1])
            .put("before_location",before[2])
            .put("before_substrate",before[3])
            .put("before_stimulant",before[4]).put("before_kind",before[5])
        if(newStage!=null)details.put("new_stage",newStage)
        if(newKind!=null)details.put("new_kind",newKind)
        if(newLocation!=null)details.put("new_location",newLocation)
        if(newSubstrate!=null)details.put("new_substrate",newSubstrate)
        if(stimulant!=null)details.put("new_stimulant",stimulant)
        if(customFields.isNotEmpty()){
            require(customFields.size<=30 && customFields.all{
                it.key.length in 1..100 && it.value.length<=2000
            }){"Некорректные дополнительные поля"}
            details.put("user_fields",JSONObject(customFields))
        }
        val eventId=db.insertOrThrow("item_events",null,ContentValues().apply{
            put("item_id",itemId);put("kind",kind)
            put("event_at",at);put("recorded_at",System.currentTimeMillis())
            put("note",note);put("details_json",details.toString())
        })
        if(materialId!=null){
            val material=db.rawQuery("""SELECT quantity,cost_per_unit FROM materials WHERE id=?""",
                arrayOf(materialId.toString())).use{c->
                require(c.moveToFirst()){"Материал не найден"}
                c.getDouble(0) to c.getDouble(1)
            }
            require(materialQty<=material.first+1e-8){"На складе недостаточно материала"}
            val unitCost=decimalMinor11(material.second)
            val total=decimalMinor11(materialQty*material.second)
            require(total>=0)
            db.update("materials",ContentValues().apply{
                put("quantity",(material.first-materialQty).coerceAtLeast(0.0))
            },"id=?",arrayOf(materialId.toString()))
            db.insertOrThrow("material_moves",null,ContentValues().apply{
                put("material_id",materialId);put("delta",-materialQty)
                put("note",kind+" · "+note);put("created_at",at)
            })
            val usageId=db.insertOrThrow("stock_usage",null,ContentValues().apply{
                put("material_id",materialId);put("entity_kind","item")
                put("entity_id",itemId);put("quantity",materialQty)
                put("unit_cost_minor",unitCost);put("total_minor",total)
                put("used_at",at);put("note",kind)
            })
            db.insertOrThrow("direct_costs",null,ContentValues().apply{
                put("entity_kind","item");put("entity_id",itemId)
                put("amount_minor",total);put("category","Материалы")
                put("occurred_at",at);put("source_type","stock_usage")
                put("source_id",usageId);put("note",kind)
            })
            details.put("usage_id",usageId).put("material_id",materialId)
                .put("material_qty",materialQty).put("direct_cost_minor",total)
            db.update("item_events",ContentValues().apply{
                put("details_json",details.toString())
            },"id=?",arrayOf(eventId.toString()))
        }
        val cv=ContentValues().apply{
            if(newStage!=null)put("stage",newStage.take(100))
            if(newKind!=null)put("kind",newKind)
            if(newLocation!=null)put("location_text",newLocation.take(200))
            if(newSubstrate!=null)put("substrate",newSubstrate.take(500))
            if(stimulant!=null)put("stimulant",stimulant.take(300))
            if(kind=="Утилизация")put("status","Утилизирован")
            put("updated_at",System.currentTimeMillis())
        }
        db.update("plant_items",cv,"id=?",arrayOf(itemId.toString()))
        db.setTransactionSuccessful()
        return eventId
    }finally{db.endTransaction()}
}

/** Compensating record restores material/last state; historical rows remain. */
internal fun LabDatabase.cancelItemEvent11(eventId:Long,reason:String){
    require(reason.trim().length in 2..500)
    val db=writableDatabase
    db.beginTransaction()
    try{
        val row=db.rawQuery("""SELECT id,item_id,kind,event_at,details_json,cancelled
            FROM item_events WHERE id=?""",arrayOf(eventId.toString())).use{c->
            require(c.moveToFirst()){"Событие не найдено"}
            listOf(c.getString(0),c.getString(1),c.getString(2),
                c.getString(3),c.getString(4),c.getString(5))
        }
        val id=row[1].toLong()
        require(row[5]=="0"){"Событие уже отменено"}
        require(row[2]!="Создание" && row[2]!="Продажа"){"Используй специальную корректировку"}
        val newer=db.rawQuery("""SELECT COUNT(*) FROM item_events
            WHERE item_id=? AND id>? AND cancelled=0""",
            arrayOf(id.toString(),eventId.toString())).use{it.moveToFirst();it.getInt(0)}
        require(newer==0){"Можно отменить только последнее событие: последующие зависят от него"}
        val d=JSONObject(row[4])
        if(d.has("measurement_id")){
            db.update("item_measurements",ContentValues().apply{
                put("cancelled",1)
            },"id=?",arrayOf(d.getLong("measurement_id").toString()))
        }
        if(d.has("usage_id")){
            val usageId=d.getLong("usage_id")
            val stock=db.rawQuery("""SELECT material_id,quantity,reversed
                FROM stock_usage WHERE id=?""",arrayOf(usageId.toString())).use{c->
                require(c.moveToFirst()){"Использование материала не найдено"}
                Triple(c.getLong(0),c.getDouble(1),c.getInt(2))
            }
            require(stock.third==0)
            db.execSQL("UPDATE materials SET quantity=quantity+? WHERE id=?",
                arrayOf(stock.second,stock.first))
            db.update("stock_usage",ContentValues().apply{
                put("reversed",1)
            },"id=?",arrayOf(usageId.toString()))
            db.update("direct_costs",ContentValues().apply{
                put("reversed",1)
            },"source_type='stock_usage' AND source_id=?",
                arrayOf(usageId.toString()))
            db.insertOrThrow("material_moves",null,ContentValues().apply{
                put("material_id",stock.first);put("delta",stock.second)
                put("created_at",System.currentTimeMillis())
                put("note","Возврат по отмене операции #"+eventId+": "+reason)
            })
        }
        val previous=ContentValues().apply{
            if(d.has("before_status"))put("status",d.getString("before_status"))
            if(d.has("before_kind"))put("kind",d.getString("before_kind"))
            if(d.has("before_stage"))put("stage",d.getString("before_stage"))
            if(d.has("before_location"))put("location_text",d.getString("before_location"))
            if(d.has("before_substrate"))put("substrate",d.getString("before_substrate"))
            if(d.has("before_stimulant"))put("stimulant",d.getString("before_stimulant"))
            put("updated_at",System.currentTimeMillis())
        }
        db.update("plant_items",previous,"id=?",arrayOf(id.toString()))
        db.update("item_events",ContentValues().apply{
            put("cancelled",1)
        },"id=?",arrayOf(eventId.toString()))
        db.insertOrThrow("item_events",null,ContentValues().apply{
            put("item_id",id);put("kind","Отмена")
            put("event_at",System.currentTimeMillis())
            put("recorded_at",System.currentTimeMillis())
            put("note",reason.trim());put("reversal_of",eventId)
        })
        db.setTransactionSuccessful()
    }finally{db.endTransaction()}
}
