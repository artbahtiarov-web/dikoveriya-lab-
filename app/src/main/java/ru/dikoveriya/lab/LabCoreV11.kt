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
    val backupTables=listOf("plant_visuals","knowledge_notes","knowledge_revisions")
    fun create(db:SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS plant_visuals (plant_id INTEGER PRIMARY KEY NOT NULL REFERENCES plants(id), icon_key TEXT NOT NULL DEFAULT 'leaf', color_key TEXT NOT NULL DEFAULT 'forest', cover_filename TEXT, cover_zoom REAL NOT NULL DEFAULT 1.0, cover_pan_x REAL NOT NULL DEFAULT 0, cover_pan_y REAL NOT NULL DEFAULT 0, changed_at INTEGER NOT NULL DEFAULT 0)")
        val visualFields=db.rawQuery("PRAGMA table_info(plant_visuals)",null).use{c->
            buildSet{while(c.moveToNext())add(c.getString(1))}
        }
        if("cover_zoom" !in visualFields)db.execSQL(
            "ALTER TABLE plant_visuals ADD COLUMN cover_zoom REAL NOT NULL DEFAULT 1.0")
        if("cover_pan_x" !in visualFields)db.execSQL(
            "ALTER TABLE plant_visuals ADD COLUMN cover_pan_x REAL NOT NULL DEFAULT 0")
        if("cover_pan_y" !in visualFields)db.execSQL(
            "ALTER TABLE plant_visuals ADD COLUMN cover_pan_y REAL NOT NULL DEFAULT 0")
        db.execSQL("CREATE TABLE IF NOT EXISTS knowledge_notes (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, category TEXT NOT NULL, body TEXT NOT NULL, source_url TEXT NOT NULL DEFAULT '', plant_id INTEGER REFERENCES plants(id), tags TEXT NOT NULL DEFAULT '', protocol_version INTEGER NOT NULL DEFAULT 1, archived INTEGER NOT NULL DEFAULT 0, updated_at INTEGER NOT NULL, created_at INTEGER NOT NULL)")
        val fields=db.rawQuery("PRAGMA table_info(knowledge_notes)",null).use{c->
            buildSet{while(c.moveToNext())add(c.getString(1))}
        }
        if("plant_id" !in fields)db.execSQL("ALTER TABLE knowledge_notes ADD COLUMN plant_id INTEGER REFERENCES plants(id)")
        if("tags" !in fields)db.execSQL("ALTER TABLE knowledge_notes ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
        if("protocol_version" !in fields)db.execSQL("ALTER TABLE knowledge_notes ADD COLUMN protocol_version INTEGER NOT NULL DEFAULT 1")
        if("archived" !in fields)db.execSQL("ALTER TABLE knowledge_notes ADD COLUMN archived INTEGER NOT NULL DEFAULT 0")
        db.execSQL("CREATE TABLE IF NOT EXISTS knowledge_revisions (id INTEGER PRIMARY KEY AUTOINCREMENT, note_id INTEGER NOT NULL REFERENCES knowledge_notes(id), old_title TEXT NOT NULL, old_body TEXT NOT NULL, old_source_url TEXT NOT NULL, old_tags TEXT NOT NULL, revised_at INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_knowledge_revisions ON knowledge_revisions(note_id,revised_at)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_knowledge_category ON knowledge_notes(category,updated_at)")
        val categoryExists=db.rawQuery("PRAGMA table_info(materials)",null).use{c->
            var found=false
            while(c.moveToNext())if(c.getString(1)=="category")found=true
            found
        }
        if(!categoryExists)db.execSQL(
            "ALTER TABLE materials ADD COLUMN category TEXT NOT NULL DEFAULT 'Расходники'")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_material_category ON materials(category)")

    }
    fun validate(db:SQLiteDatabase) {
        require(!db.rawQuery("PRAGMA foreign_key_check",null).use{it.moveToFirst()}){"Нарушена целостность ссылок"}
        require(!db.rawQuery("SELECT 1 FROM plant_visuals WHERE cover_filename LIKE '%/%' OR cover_filename LIKE '%..%' LIMIT 1",null).use{it.moveToFirst()}){"Небезопасное имя фотографии"}
    }
}

data class Lab10PlantVisual(val plantId:Long,val iconKey:String="leaf",
    val colorKey:String="forest",val coverFilename:String?=null,
    val coverZoom:Float=1f,val panX:Float=0f,val panY:Float=0f)

fun LabDatabase.plantVisuals10():Map<Long,Lab10PlantVisual> =
    readableDatabase.rawQuery("SELECT plant_id,icon_key,color_key,cover_filename,cover_zoom,cover_pan_x,cover_pan_y FROM plant_visuals",null).use{c->
        buildMap { while(c.moveToNext()){
            val row=Lab10PlantVisual(c.getLong(0),c.getString(1),c.getString(2),
                if(c.isNull(3))null else c.getString(3),
                c.getFloat(4),c.getFloat(5),c.getFloat(6))
            put(row.plantId,row)
        } }
    }

private val icons10=setOf("leaf","sprout","flower","flask","tree","star","heart","seedling")
private val colors10=setOf("forest","moss","gold","teal","rose")

private fun LabDatabase.plantExists10(id:Long)=readableDatabase.rawQuery(
    "SELECT 1 FROM plants WHERE id=?",arrayOf(id.toString())).use{it.moveToFirst()}

fun LabDatabase.stylePlant10(plantId:Long,iconKey:String,colorKey:String,
    zoom:Float=1f,panX:Float=0f,panY:Float=0f){
    require(zoom in 1f..3f && panX in -0.6f..0.6f && panY in -0.6f..0.6f){
        "Проверь масштаб и расположение изображения"
    }
    require(iconKey in icons10 && colorKey in colors10){"Неверное оформление"}
    require(plantExists10(plantId)){"Сорт не найден"}
    val db=writableDatabase
    db.beginTransaction()
    try{
        db.execSQL("INSERT OR IGNORE INTO plant_visuals(plant_id) VALUES (?)",arrayOf(plantId))
        val values=ContentValues().apply{
            put("icon_key",iconKey);put("color_key",colorKey)
            put("cover_zoom",zoom);put("cover_pan_x",panX);put("cover_pan_y",panY)
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
                put("cover_filename",filename)
                put("cover_zoom",1f);put("cover_pan_x",0f);put("cover_pan_y",0f)
                put("changed_at",System.currentTimeMillis())
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
        db.execSQL("UPDATE plant_visuals SET cover_filename=NULL,cover_zoom=1.0,cover_pan_x=0,cover_pan_y=0,changed_at=? WHERE plant_id=?",
            arrayOf(System.currentTimeMillis(),plantId))
        db.setTransactionSuccessful()
    }finally{db.endTransaction()}
    if(previous.startsWith("plcover_"))File(context.filesDir,"culture_photos/"+previous).delete()
}

data class Lab10KnowledgeNote(
    val id:Long,val title:String,val category:String,val body:String,
    val sourceUrl:String,val updatedAt:Long,val plantId:Long?=null,
    val tags:String="",val version:Int=1
)

fun LabDatabase.knowledgeNotes10(search:String=""):List<Lab10KnowledgeNote>{
    val q=search.trim().take(80)
    val selection=if(q.isEmpty())"" else
        " AND (n.title LIKE ? OR n.body LIKE ? OR n.category LIKE ? OR n.tags LIKE ? OR p.name LIKE ?)"
    val sql="""SELECT n.id,n.title,n.category,n.body,n.source_url,n.updated_at,
        n.plant_id,n.tags,n.protocol_version
        FROM knowledge_notes n LEFT JOIN plants p ON p.id=n.plant_id
        WHERE n.archived=0"""+selection+" ORDER BY n.updated_at DESC LIMIT 300"
    val args=if(q.isEmpty())null else Array(5){"%"+q+"%"}
    return readableDatabase.rawQuery(sql,args).use{c->buildList{
        while(c.moveToNext())add(Lab10KnowledgeNote(
            c.getLong(0),c.getString(1),c.getString(2),
            c.getString(3),c.getString(4),c.getLong(5),
            if(c.isNull(6))null else c.getLong(6),c.getString(7),c.getInt(8)))
    }}
}

private fun knowledgeFields10(title:String,category:String,body:String,
                              source:String,tags:String):ContentValues{
    require(title.trim().length in 2..150){"Название: 2–150 символов"}
    require(category in setOf("Протокол","Среда","Растение","Проблема","Заметка")){"Неизвестная категория"}
    require(body.trim().length in 2..20000){"Укажи содержание"}
    require(source.length<=1000){"Адрес источника слишком длинный"}
    require(tags.length<=400){"Метки слишком длинные"}
    return ContentValues().apply{
        put("title",title.trim());put("category",category)
        put("body",body.trim());put("source_url",source.trim())
        put("tags",tags.trim());put("updated_at",System.currentTimeMillis())
    }
}

fun LabDatabase.addKnowledge10(
    title:String,category:String,body:String,source:String,
    plantId:Long?=null,tags:String=""
) {
    val values=knowledgeFields10(title,category,body,source,tags)
    val now=System.currentTimeMillis()
    if(plantId!=null)require(readableDatabase.rawQuery(
        "SELECT 1 FROM plants WHERE id=?",arrayOf(plantId.toString())).use{it.moveToFirst()}) {
        "Сорт не найден"
    }
    values.put("plant_id",plantId)
    values.put("protocol_version",1)
    values.put("created_at",now)
    writableDatabase.insertOrThrow("knowledge_notes",null,values)
}

fun LabDatabase.updateKnowledge10(
    id:Long,title:String,category:String,body:String,source:String,
    plantId:Long?,tags:String
) {
    val values=knowledgeFields10(title,category,body,source,tags)
    val db=writableDatabase
    db.beginTransaction()
    try{
        db.rawQuery("SELECT title,body,source_url,tags,protocol_version FROM knowledge_notes WHERE id=? AND archived=0",
            arrayOf(id.toString())).use{c->
            require(c.moveToFirst()){"Заметка не найдена"}
            val old=ContentValues().apply{
                put("note_id",id);put("old_title",c.getString(0))
                put("old_body",c.getString(1));put("old_source_url",c.getString(2))
                put("old_tags",c.getString(3));put("revised_at",System.currentTimeMillis())
            }
            db.insertOrThrow("knowledge_revisions",null,old)
            values.put("protocol_version",c.getInt(4)+1)
        }
        values.put("plant_id",plantId)
        require(db.update("knowledge_notes",values,"id=? AND archived=0",
            arrayOf(id.toString()))==1){"Не удалось обновить"}
        db.setTransactionSuccessful()
    }finally{db.endTransaction()}
}

/** Archive without losing the revision trail or orphaning related records. */
fun LabDatabase.deleteKnowledge10(id:Long){
    val values=ContentValues().apply{
        put("archived",1);put("updated_at",System.currentTimeMillis())
    }
    require(writableDatabase.update("knowledge_notes",values,"id=? AND archived=0",
        arrayOf(id.toString()))==1){"Запись уже архивирована"}
}
