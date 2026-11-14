package ru.dikoveriya.lab

import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.util.UUID

internal data class Lab11MaterialMeta(
    val photo:String?,val manufacturer:String,val supplier:String,
    val url:String,val notes:String,val purchaseAt:Long?
)

internal fun LabDatabase.materialMeta11(id:Long):Lab11MaterialMeta? =
    readableDatabase.rawQuery("""SELECT photo_filename,manufacturer,supplier,
        supplier_url,product_notes,purchased_at FROM materials WHERE id=?""",
        arrayOf(id.toString())).use{c->
        if(!c.moveToFirst())null else Lab11MaterialMeta(
            if(c.isNull(0))null else c.getString(0),
            c.getString(1),c.getString(2),c.getString(3),c.getString(4),
            if(c.isNull(5))null else c.getLong(5))
    }

internal fun LabDatabase.updateMaterialMeta11(id:Long,manufacturer:String,
    supplier:String,url:String,notes:String){
    require(manufacturer.length<=160 && supplier.length<=160)
    require(url.length<=1000 && notes.length<=2000)
    require(url.isBlank() || url.startsWith("https://") || url.startsWith("http://")){
        "Ссылка должна начинаться с https://"
    }
    val n=writableDatabase.update("materials",ContentValues().apply{
        put("manufacturer",manufacturer.trim());put("supplier",supplier.trim())
        put("supplier_url",url.trim());put("product_notes",notes.trim())
    },"id=?",arrayOf(id.toString()))
    require(n==1){"Материал не найден"}
}

/** Private original media directory is included in ZIP after DB read snapshot. */
internal fun LabDatabase.attachMedia11(context:Context,entity:String,id:Long,uri:Uri):String{
    val (table,column)=when(entity){
        "material"->"materials" to "photo_filename"
        "asset"->"equipment_assets" to "photo_filename"
        "purchase"->"purchases" to "receipt_filename"
        else->error("Неверный тип вложения")
    }
    val ext=when(context.contentResolver.getType(uri)){
        "image/jpeg"->"jpg";"image/png"->"png";"image/webp"->"webp"
        "image/heic","image/heif"->"heic"
        else->error("Изображение должно быть JPEG, PNG, WebP или HEIC")
    }
    val folder=File(context.filesDir,"culture_photos").apply{mkdirs()}
    val filename="media_"+UUID.randomUUID()+"."+ext
    val file=File(folder,filename)
    try{
        context.contentResolver.openInputStream(uri)?.use{src->
            file.outputStream().use{dst->
                var size=0L
                val buf=ByteArray(8192)
                while(true){
                    val n=src.read(buf)
                    if(n<0)break
                    size+=n
                    require(size<=20_000_000){"Изображение превышает 20 МБ"}
                    dst.write(buf,0,n)
                }
                require(size>0){"Пустой файл"}
            }
        }?:error("Не удалось открыть изображение")
        val options=BitmapFactory.Options().apply{inJustDecodeBounds=true}
        BitmapFactory.decodeFile(file.absolutePath,options)
        require(options.outWidth in 1..12000 && options.outHeight in 1..12000){
            "Файл не является поддерживаемой фотографией"
        }
        val db=writableDatabase
        db.beginTransaction()
        try{
            val current=db.rawQuery("SELECT "+column+" FROM "+table+" WHERE id=?",
                arrayOf(id.toString())).use{c->
                require(c.moveToFirst()){"Объект не найден"}
                if(c.isNull(0))null else c.getString(0)
            }
            require(db.update(table,ContentValues().apply{
                put(column,filename)
            },"id=?",arrayOf(id.toString()))==1)
            if(entity=="material")
                db.update("materials",ContentValues().apply{
                    put("purchased_at",System.currentTimeMillis())
                },"id=? AND purchased_at IS NULL",arrayOf(id.toString()))
            db.setTransactionSuccessful()
            // Old filename deliberately remains until next explicit cleanup; data isn't lost.
            if(current!=null && current==filename)error("Неверное имя вложения")
        }finally{db.endTransaction()}
        return filename
    }catch(e:Exception){file.delete();throw e}
}
