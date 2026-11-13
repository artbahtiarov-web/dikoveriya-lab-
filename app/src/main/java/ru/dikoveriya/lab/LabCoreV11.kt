package ru.dikoveriya.lab

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.graphics.BitmapFactory
import java.io.File
import java.util.UUID

/** Additive schema: user-selected covers, icon accents and offline knowledge notes. */
object LabCoreV11 {
    val backupTables=listOf("plant_visuals","knowledge_notes")
    fun create(db:SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS plant_visuals (plant_id INTEGER PRIMARY KEY NOT NULL REFERENCES plants(id), icon_key TEXT NOT NULL DEFAULT 'leaf', color_key TEXT NOT NULL DEFAULT 'forest', cover_filename TEXT, changed_at INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE IF NOT EXISTS knowledge_notes (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, category TEXT NOT NULL, body TEXT NOT NULL, source_url TEXT NOT NULL DEFAULT '', updated_at INTEGER NOT NULL, created_at INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_knowledge_category ON knowledge_notes(category,updated_at)")
    }
    fun validate(db:SQLiteDatabase) {
        require(!db.rawQuery("PRAGMA foreign_key_check",null).use{it.moveToFirst()}){"Нарушена целостность ссылок"}
        require(!db.rawQuery("SELECT 1 FROM plant_visuals WHERE cover_filename LIKE '%/%' OR cover_filename LIKE '%..%' LIMIT 1",null).use{it.moveToFirst()}){"Небезопасное имя фотографии"}
    }
}

data class Lab10PlantVisual(val plantId:Long,val iconKey:String="leaf",
    val colorKey:String="forest",val coverFilename:String?=null)

fun LabDatabase.plantVisuals10():Map<Long,Lab10PlantVisual> =
    readableDatabase.rawQuery("SELECT plant_id,icon_key,color_key,cover_filename FROM plant_visuals",null).use{c->
        buildMap { while(c.moveToNext()){
            val row=Lab10PlantVisual(c.getLong(0),c.getString(1),c.getString(2),
                if(c.isNull(3))null else c.getString(3))
            put(row.plantId,row)
        } }
    }

private val icons10=setOf("leaf","sprout","flower","flask","tree","star","heart","seedling")
private val colors10=setOf("forest","moss","gold","teal","rose")

private fun LabDatabase.plantExists10(id:Long)=readableDatabase.rawQuery(
    "SELECT 1 FROM plants WHERE id=?",arrayOf(id.toString())).use{it.moveToFirst()}

fun LabDatabase.stylePlant10(plantId:Long,iconKey:String,colorKey:String){
    require(iconKey in icons10 && colorKey in colors10){"Неверное оформление"}
    require(plantExists10(plantId)){"Сорт не найден"}
    val db=writableDatabase
    db.beginTransaction()
    try{
        db.execSQL("INSERT OR IGNORE INTO plant_visuals(plant_id) VALUES (?)",arrayOf(plantId))
        val values=ContentValues().apply{
            put("icon_key",iconKey);put("color_key",colorKey)
            put("changed_at",System.currentTimeMillis())
        }
        require(db.update("plant_visuals",values,"plant_id=?",arrayOf(plantId.toString()))==1)
        db.setTransactionSuccessful()
    }finally{db.endTransaction()}
}

private fun LabDatabase.coverName10(id:Long):String?=readableDatabase.rawQuery(
    "SELECT cover_filename FROM plant_visuals WHERE plant_id=?",arrayOf(id.toString())
).use{c->if(c.moveToFirst()&&!c.isNull(0))c.getString(0) else null}

/** Copy fully before metadata mutation; old image deleted only after successful commit. */
fun LabDatabase.setPlantCover10(context:Context,plantId:Long,uri:Uri){
    require(plantExists10(plantId)){"Сорт не найден"}
    val ext=when(context.contentResolver.getType(uri)){
        "image/jpeg"->"jpg";"image/png"->"png";"image/webp"->"webp"
        "image/heic","image/heif"->"heic"
        else->throw IllegalArgumentException("Поддерживаются JPEG, PNG, WebP, HEIC")
    }
    val folder=File(context.filesDir,"culture_photos").apply{mkdirs()}
    val filename="plcover_"+UUID.randomUUID()+"."+ext
    val tmp=File(folder,filename+".part")
    val final=File(folder,filename)
    try{
        val input=context.contentResolver.openInputStream(uri)?:error("Файл недоступен")
        input.use{source->tmp.outputStream().use{dest->
            val buffer=ByteArray(8192)
            var size=0L
            while(true){
                val n=source.read(buffer);if(n<0)break
                size+=n;require(size<=20_000_000){"Файл слишком большой (20 МБ)"}
                dest.write(buffer,0,n)
            }
            require(size>0){"Пустое изображение"}
        }}
        val info=BitmapFactory.Options().apply{inJustDecodeBounds=true}
        BitmapFactory.decodeFile(tmp.absolutePath,info)
        require(info.outWidth in 1..12000 && info.outHeight in 1..12000){
            "Файл не распознаётся как изображение"
        }
        check(tmp.renameTo(final)){"Ошибка сохранения"}
        val previous=coverName10(plantId)
        val db=writableDatabase
        db.beginTransaction()
        try{
            db.execSQL("INSERT OR IGNORE INTO plant_visuals(plant_id) VALUES (?)",arrayOf(plantId))
            val values=ContentValues().apply{
                put("cover_filename",filename);put("changed_at",System.currentTimeMillis())
            }
            require(db.update("plant_visuals",values,"plant_id=?",arrayOf(plantId.toString()))==1)
            db.setTransactionSuccessful()
        }finally{db.endTransaction()}
        if(previous!=null && previous.startsWith("plcover_"))File(folder,previous).delete()
    }catch(e:Exception){tmp.delete();final.delete();throw e}
}

fun LabDatabase.removePlantCover10(context:Context,plantId:Long){
    require(plantExists10(plantId)){"Сорт не найден"}
    val previous=coverName10(plantId)?:return
    val db=writableDatabase
    db.beginTransaction()
    try{
        db.execSQL("UPDATE plant_visuals SET cover_filename=NULL,changed_at=? WHERE plant_id=?",
            arrayOf(System.currentTimeMillis(),plantId))
        db.setTransactionSuccessful()
    }finally{db.endTransaction()}
    if(previous.startsWith("plcover_"))File(context.filesDir,"culture_photos/"+previous).delete()
}

data class Lab10KnowledgeNote(val id:Long,val title:String,val category:String,
    val body:String,val sourceUrl:String,val updatedAt:Long)

fun LabDatabase.knowledgeNotes10(search:String=""):List<Lab10KnowledgeNote>{
    val q=search.trim().take(80)
    val sql=if(q.isEmpty())"SELECT id,title,category,body,source_url,updated_at FROM knowledge_notes ORDER BY updated_at DESC LIMIT 200"
        else "SELECT id,title,category,body,source_url,updated_at FROM knowledge_notes WHERE title LIKE ? OR body LIKE ? OR category LIKE ? ORDER BY updated_at DESC LIMIT 200"
    val args=if(q.isEmpty())null else arrayOf("%"+q+"%","%"+q+"%","%"+q+"%")
    return readableDatabase.rawQuery(sql,args).use{c->buildList{
        while(c.moveToNext())add(Lab10KnowledgeNote(c.getLong(0),c.getString(1),
            c.getString(2),c.getString(3),c.getString(4),c.getLong(5)))
    }}
}

fun LabDatabase.addKnowledge10(title:String,category:String,body:String,source:String){
    require(title.trim().length in 2..150){"Название: 2–150 символов"}
    require(category in setOf("Протокол","Среда","Растение","Проблема","Заметка")){"Неизвестная категория"}
    require(body.trim().length in 2..20000){"Укажи содержание"}
    require(source.length<=1000){"Адрес источника слишком длинный"}
    val now=System.currentTimeMillis()
    val values=ContentValues().apply{
        put("title",title.trim());put("category",category);put("body",body.trim())
        put("source_url",source.trim());put("updated_at",now);put("created_at",now)
    }
    writableDatabase.insertOrThrow("knowledge_notes",null,values)
}

fun LabDatabase.deleteKnowledge10(id:Long){
    require(writableDatabase.delete("knowledge_notes","id=?",arrayOf(id.toString()))==1){"Запись не найдена"}
}
