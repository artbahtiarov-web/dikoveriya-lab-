package ru.dikoveriya.lab

import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** A custom photo is attached to a typed field and backed up with the same ZIP. */
internal fun LabDatabase.attachFieldPhoto11(
    context:Context,fieldId:Long,entityKind:String,entityId:Long,uri:Uri
):String{
    val definition=readableDatabase.rawQuery("""SELECT value_kind,entity_kind
        FROM custom_field_definitions WHERE id=? AND archived=0""",
        arrayOf(fieldId.toString())).use{c->
        require(c.moveToFirst()){"Пользовательское поле не найдено"}
        c.getString(0) to c.getString(1)
    }
    require(definition.first=="photo" && definition.second==entityKind){
        "Это поле не предназначено для фотографии"
    }
    val table=when(entityKind){
        "item"->"plant_items";"culture"->"cultures"
        "material"->"materials";"asset"->"equipment_assets"
        "purchase"->"purchases";else->error("Неизвестный объект")
    }
    readableDatabase.rawQuery("SELECT 1 FROM "+table+" WHERE id=?",
        arrayOf(entityId.toString())).use{require(it.moveToFirst()){"Объект не найден"}}
    val ext=when(context.contentResolver.getType(uri)){
        "image/jpeg"->"jpg";"image/png"->"png";"image/webp"->"webp"
        "image/heic","image/heif"->"heic"
        else->error("Поддерживается только изображение")
    }
    val folder=File(context.filesDir,"culture_photos").apply{mkdirs()}
    val name="field_"+UUID.randomUUID()+"."+ext
    val file=File(folder,name)
    try{
        context.contentResolver.openInputStream(uri)?.use{src->
            file.outputStream().use{dst->
                var bytes=0L;val buff=ByteArray(8192)
                while(true){
                    val n=src.read(buff);if(n<0)break
                    bytes+=n;require(bytes<=20_000_000){"Изображение больше 20 МБ"}
                    dst.write(buff,0,n)
                }
                require(bytes>0){"Изображение пустое"}
            }
        }?:error("Нет доступа к изображению")
        val opts=BitmapFactory.Options().apply{inJustDecodeBounds=true}
        BitmapFactory.decodeFile(file.absolutePath,opts)
        require(opts.outWidth in 1..12000 && opts.outHeight in 1..12000){
            "Неверное изображение"
        }
        val db=writableDatabase
        db.beginTransaction()
        try{
            db.insertWithOnConflict("custom_field_values",null,ContentValues().apply{
                put("field_id",fieldId);put("entity_kind",entityKind)
                put("entity_id",entityId)
                put("value_json",JSONObject().put("value",name).toString())
                put("photo_filename",name)
                put("changed_at",System.currentTimeMillis())
            },android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
            db.setTransactionSuccessful()
        }finally{db.endTransaction()}
        return name
    }catch(e:Exception){file.delete();throw e}
}
