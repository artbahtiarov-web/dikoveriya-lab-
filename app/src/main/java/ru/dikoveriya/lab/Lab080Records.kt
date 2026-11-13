package ru.dikoveriya.lab

import android.database.sqlite.SQLiteDatabase
import org.json.JSONObject

/** Safe archive/edit actions: never erase historical plant, media or photo evidence. */
private fun <T> LabDatabase.transaction080(op:SQLiteDatabase.()->T):T{
    val db=writableDatabase
    db.beginTransaction()
    try{val result=db.op();db.setTransactionSuccessful();return result}
    finally{db.endTransaction()}
}
private fun SQLiteDatabase.audit080(type:String,id:Long,before:String,
    after:String,reason:String){
    insertOrThrow("entity_changes",null,lab072CV(
        "entity_type" to type,"entity_id" to id,
        "before_json" to before,"after_json" to after,
        "reason" to reason,"changed_at" to System.currentTimeMillis()))
}
private fun SQLiteDatabase.archive080(type:String,id:Long,reason:String){
    insertOrThrow("entity_archives",null,lab072CV(
        "entity_type" to type,"entity_id" to id,
        "reason" to reason,"archived_at" to System.currentTimeMillis()))
}
private fun String.required080(max:Int)=trim().also{
    require(it.length in 2..max){"Поле должно содержать от 2 до $max символов"}
}
fun LabDatabase.changeIngredient080(id:Long,name:String,category:String,
    unit:String,notes:String,reason:String)=transaction080{
    val clean=name.required080(140)
    val why=reason.required080(500)
    val validCategories=setOf("Базовая среда","Углеводы","Загуститель",
        "Регулятор роста","Минералы","Витамины","Добавка","Другое")
    val validUnits=setOf("г/л","мг/л","мл/л","мг/л из раствора")
    require(category in validCategories && unit in validUnits){"Выбери правильные категорию и единицу"}
    val old=rawQuery("""SELECT name,category,unit,notes FROM ingredient_catalog
        WHERE id=? AND archived=0""",arrayOf(id.toString())).use{c->
        require(c.moveToFirst()){"Компонент отсутствует или архивирован"}
        JSONObject().put("name",c.getString(0)).put("category",c.getString(1))
            .put("unit",c.getString(2)).put("notes",c.getString(3)).toString()
    }
    val next=JSONObject().put("name",clean).put("category",category)
        .put("unit",unit).put("notes",notes.take(1000)).toString()
    require(old!=next){"Нет изменений"}
    require(update("ingredient_catalog",lab072CV(
        "name" to clean,"category" to category,"unit" to unit,
        "notes" to notes.take(1000)),"id=?",
        arrayOf(id.toString()))==1){"Компонент не найден"}
    audit080("ingredient",id,old,next,why)
}
fun LabDatabase.archiveIngredient080(id:Long,reason:String)=transaction080{
    val why=reason.required080(500)
    val row=rawQuery("SELECT name,archived FROM ingredient_catalog WHERE id=?",
        arrayOf(id.toString())).use{c->
        require(c.moveToFirst()){"Компонент не найден"}
        c.getString(0) to c.getInt(1)
    }
    require(row.second==0){"Компонент уже архивирован"}
    // Recipe parts are historical snapshots stored by name, not foreign keys.
    // Archiving the component never modifies them.
    require(update("ingredient_catalog",lab072CV("archived" to 1),
        "id=?",arrayOf(id.toString()))==1)
    audit080("ingredient",id,JSONObject().put("name",row.first)
        .put("archived",false).toString(),
        JSONObject().put("name",row.first).put("archived",true).toString(),why)
}
fun LabDatabase.changePlant080(id:Long,name:String,notes:String,
    reason:String)=transaction080{
    val clean=name.required080(120)
    val why=reason.required080(500)
    val previous=rawQuery("SELECT name,description FROM plants WHERE id=?",
        arrayOf(id.toString())).use{c->
        require(c.moveToFirst()){"Сорт не найден"}
        c.getString(0) to c.getString(1)
    }
    require(previous.first!=clean || previous.second!=notes.take(1000)){
        "Изменений нет"
    }
    require(update("plants",lab072CV("name" to clean,"description" to notes.take(1000)),
        "id=?",arrayOf(id.toString()))==1)
    audit080("plant",id,JSONObject().put("name",previous.first)
        .put("description",previous.second).toString(),
        JSONObject().put("name",clean).put("description",notes.take(1000)).toString(),why)
}
fun LabDatabase.archiveUnusedPlant080(id:Long,reason:String)=transaction080{
    val why=reason.required080(500)
    val name=rawQuery("SELECT name FROM plants WHERE id=?",arrayOf(id.toString())).use{c->
        require(c.moveToFirst()){"Сорт не найден"};c.getString(0)
    }
    for(table in listOf("cultures","mother_plants","recipes","production_groups")){
        val count=rawQuery("SELECT COUNT(*) FROM $table WHERE plant_id=?",
            arrayOf(id.toString())).use{c->c.moveToFirst();c.getInt(0)}
        require(count==0){"Сорт связан с историей в $table. Архивация недоступна"}
    }
    archive080("plant",id,why)
    audit080("plant",id,JSONObject().put("name",name).put("archived",false).toString(),
        JSONObject().put("name",name).put("archived",true).toString(),why)
}
fun LabDatabase.changePhotoCaption080(id:Long,caption:String,
    reason:String)=transaction080{
    val why=reason.required080(500)
    val old=rawQuery("SELECT caption,culture_id FROM culture_photos WHERE id=?",
        arrayOf(id.toString())).use{c->
        require(c.moveToFirst()){"Фотография не найдена"}
        c.getString(0) to c.getLong(1)
    }
    val next=caption.trim().take(500)
    require(next!=old.first){"Подпись не изменена"}
    require(update("culture_photos",lab072CV("caption" to next),
        "id=?",arrayOf(id.toString()))==1)
    audit080("photo",id,JSONObject().put("caption",old.first).toString(),
        JSONObject().put("caption",next).toString(),why)
    insertOrThrow("events",null,lab072CV("culture_id" to old.second,
        "kind" to "Исправление подписи фото","note" to why,
        "created_at" to System.currentTimeMillis(),
        "occurred_at" to System.currentTimeMillis()))
}
fun LabDatabase.archivePhoto080(id:Long,reason:String)=transaction080{
    val why=reason.required080(500)
    val info=rawQuery("SELECT filename,culture_id FROM culture_photos WHERE id=?",
        arrayOf(id.toString())).use{c->
        require(c.moveToFirst()){"Фотография не найдена"}
        c.getString(0) to c.getLong(1)
    }
    archive080("photo",id,why)
    audit080("photo",id,JSONObject().put("filename",info.first)
        .put("archived",false).toString(),
        JSONObject().put("filename",info.first).put("archived",true).toString(),why)
    insertOrThrow("events",null,lab072CV("culture_id" to info.second,
        "kind" to "Фото скрыто","note" to why,
        "created_at" to System.currentTimeMillis(),
        "occurred_at" to System.currentTimeMillis()))
}
fun LabDatabase.archiveTask080(id:Long,reason:String)=transaction080{
    val why=reason.required080(500)
    val old=rawQuery("SELECT title,done FROM tasks WHERE id=?",
        arrayOf(id.toString())).use{c->
        require(c.moveToFirst()){"Задача не найдена"}
        c.getString(0) to c.getInt(1)
    }
    // Never delete the task: preserve its due date, link and history.
    archive080("task",id,why)
    audit080("task",id,JSONObject().put("title",old.first)
        .put("done",old.second).put("archived",false).toString(),
        JSONObject().put("title",old.first).put("done",old.second)
            .put("archived",true).toString(),why)
    insertOrThrow("task_history",null,lab072CV(
        "task_id" to id,"recorded_at" to System.currentTimeMillis(),
        "action" to "Отменена","note" to why))
}
