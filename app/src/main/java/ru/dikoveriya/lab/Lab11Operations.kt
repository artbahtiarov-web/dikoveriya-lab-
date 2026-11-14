package ru.dikoveriya.lab

import android.content.ContentValues
import org.json.JSONObject
import org.json.JSONArray

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
    customFields:Map<String,String> = emptyMap(),
    extraMaterials:List<Pair<Long,Double>> = emptyList(),
    newPotMl:Int?=null,photoFilename:String?=null
):Long{
    require(kind in standardOps11 || templates11().any{it.title==kind}){
        "Неизвестная операция. Добавь её через шаблон."
    }
    require(at>0 && at<=System.currentTimeMillis()+86400000L)
    require(note.length<=2000)
    require(newKind==null || newKind in setOf("Горшечное","Черенок","Маточник","Другое"))
    require(materialQty.isFinite() && materialQty>=0)
    require((materialId==null && materialQty==0.0) ||
        (materialId!=null && materialQty>0)){
        "Количество материала без выбранной складской позиции недопустимо"
    }
    require(newPotMl==null || kind in setOf("Посадка","Пересадка") &&
        newPotMl in 1..500000){"Объём нового горшка должен быть положительным"}
    val chosenMaterials=buildList{
        if(materialId!=null)add(materialId to materialQty)
        addAll(extraMaterials)
    }
    require(chosenMaterials.size<=12 && chosenMaterials.all{
        it.first>0 && it.second.isFinite() && it.second>0
    } && chosenMaterials.map{it.first}.distinct().size==chosenMaterials.size){
        "Проверь количество и уникальность позиций материалов"
    }
    val db=writableDatabase
    db.beginTransaction()
    try{
        val before=db.rawQuery("""SELECT status,stage,location_text,substrate,
            stimulant,kind,location_id,pot_volume_ml
            FROM plant_items WHERE id=?""",arrayOf(itemId.toString())).use{c->
            require(c.moveToFirst()){"Растение не найдено"}
            listOf(c.getString(0),c.getString(1),c.getString(2),
                c.getString(3),c.getString(4),c.getString(5),c.getString(6),
                c.getString(7))
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
            .put("before_location_id",before[6]?.toLongOrNull()?:JSONObject.NULL)
            .put("before_pot_ml",before[7]?.toIntOrNull()?:JSONObject.NULL)
        if(newPotMl!=null)details.put("new_pot_ml",newPotMl)
        if(photoFilename!=null){
            require(Regex("[a-zA-Z0-9_-]+\\.(jpg|png|webp|heic)").matches(photoFilename)){
                "Некорректное имя фотографии"
            }
            db.rawQuery("""SELECT 1 FROM item_photos
                WHERE entity_kind='item' AND entity_id=? AND filename=?""",
                arrayOf(itemId.toString(),photoFilename)).use{c->
                require(c.moveToFirst()){"Фотография операции не найдена"}
            }
            details.put("photo_filename",photoFilename)
        }
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
        val usageIds=JSONArray()
        var totalMaterialsMinor=0L
        chosenMaterials.forEach{(id,qty)->
            val material=db.rawQuery("""SELECT quantity,cost_per_unit FROM materials WHERE id=?""",
                arrayOf(id.toString())).use{c->
                require(c.moveToFirst()){"Материал не найден"}
                c.getDouble(0) to c.getDouble(1)
            }
            require(qty<=material.first+1e-8){"На складе недостаточно материала"}
            val unitCost=decimalMinor11(material.second)
            val amount=decimalMinor11(qty*material.second)
            require(amount>=0)
            db.update("materials",ContentValues().apply{
                put("quantity",(material.first-qty).coerceAtLeast(0.0))
            },"id=?",arrayOf(id.toString()))
            db.insertOrThrow("material_moves",null,ContentValues().apply{
                put("material_id",id);put("delta",-qty)
                put("note",kind+" · "+note);put("created_at",at)
            })
            val usageId=db.insertOrThrow("stock_usage",null,ContentValues().apply{
                put("material_id",id);put("entity_kind","item")
                put("entity_id",itemId);put("quantity",qty)
                put("unit_cost_minor",unitCost);put("total_minor",amount)
                put("used_at",at);put("note",kind)
            })
            db.insertOrThrow("direct_costs",null,ContentValues().apply{
                put("entity_kind","item");put("entity_id",itemId)
                put("amount_minor",amount);put("category","Материалы")
                put("occurred_at",at);put("source_type","stock_usage")
                put("source_id",usageId);put("note",kind)
            })
            usageIds.put(usageId)
            totalMaterialsMinor+=amount
        }
        if(usageIds.length()>0){
            details.put("usage_ids",usageIds)
            details.put("direct_cost_minor",totalMaterialsMinor)
            if(usageIds.length()==1)details.put("usage_id",usageIds.getLong(0))
            db.update("item_events",ContentValues().apply{
                put("details_json",details.toString())
            },"id=?",arrayOf(eventId.toString()))
        }
        val cv=ContentValues().apply{
            if(newPotMl!=null)put("pot_volume_ml",newPotMl)
            if(newStage!=null)put("stage",newStage.take(100))
            if(newKind!=null)put("kind",newKind)
            if(newLocation!=null){
                put("location_text",newLocation.take(200))
                // Free-text move cannot keep pointing to an unrelated physical shelf.
                putNull("location_id")
            }
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
        require(row[2] !in setOf("Создание","Продажа","Отмена")){
            "Для этой записи требуется специальная корректировка"
        }
        val newer=db.rawQuery("""SELECT COUNT(*) FROM item_events
            WHERE item_id=? AND id>? AND cancelled=0 AND kind<>'Отмена'""",
            arrayOf(id.toString(),eventId.toString())).use{it.moveToFirst();it.getInt(0)}
        require(newer==0){"Можно отменить только последнее событие: последующие зависят от него"}
        val d=JSONObject(row[4])
        require(d.has("before_status") || d.has("measurement_id")){
            "Для этой старой записи нет снимка состояния — автоматическая отмена небезопасна"
        }
        if(d.has("measurement_id")){
            db.update("item_measurements",ContentValues().apply{
                put("cancelled",1)
            },"id=?",arrayOf(d.getLong("measurement_id").toString()))
        }
        val historicalUsages=when{
            d.has("usage_ids")->{
                val arr=d.getJSONArray("usage_ids")
                (0 until arr.length()).map{arr.getLong(it)}
            }
            d.has("usage_id")->listOf(d.getLong("usage_id"))
            else->emptyList()
        }
        historicalUsages.forEach{usageId->
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
            if(d.has("before_location_id")){
                if(d.isNull("before_location_id"))putNull("location_id")
                else put("location_id",d.getLong("before_location_id"))
            }
            if(d.has("before_substrate"))put("substrate",d.getString("before_substrate"))
            if(d.has("before_stimulant"))put("stimulant",d.getString("before_stimulant"))
            if(d.has("before_pot_ml")){
                if(d.isNull("before_pot_ml"))putNull("pot_volume_ml")
                else put("pot_volume_ml",d.getInt("before_pot_ml"))
            }
            put("updated_at",System.currentTimeMillis())
        }
        db.update("plant_items",previous,"id=?",arrayOf(id.toString()))
        if(d.has("storage_history_id")){
            val oldId=if(d.isNull("before_location_id"))null
                else d.getLong("before_location_id")
            val nowId=db.rawQuery("""SELECT new_location_id FROM item_storage_history
                WHERE id=? AND item_id=?""",
                arrayOf(d.getLong("storage_history_id").toString(),id.toString()))
                .use{c->
                    require(c.moveToFirst()){"Запись перемещения не найдена"}
                    if(c.isNull(0))null else c.getLong(0)
                }
            db.insertOrThrow("item_storage_history",null,ContentValues().apply{
                put("item_id",id)
                if(nowId!=null)put("previous_location_id",nowId)
                if(oldId!=null)put("new_location_id",oldId)
                put("location_text",d.optString("before_location",""))
                put("occurred_at",System.currentTimeMillis())
                put("note","Отмена перемещения #"+eventId+": "+reason)
            })
        }
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
