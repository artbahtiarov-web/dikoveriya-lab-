package ru.dikoveriya.lab

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Portable backup: full database JSON + original image bytes in one ZIP. */
object LabBackup {
    fun save(context: Context, db: LabDatabase, output: OutputStream) {
        val folder=File(context.filesDir,"culture_photos")
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("database.json"))
            val photoNames=db.exportJsonTo(zip)
            zip.closeEntry()
            for(name in photoNames){
                require(Regex("[a-zA-Z0-9_-]+\\.(jpg|png|webp|heic)").matches(name)) { "Неверное имя фото в базе" }
                val file=File(folder,name)
                require(file.isFile) { "Нет файла фотографии: $name. Копия отменена." }
                zip.putNextEntry(ZipEntry("photos/$name"))
                file.inputStream().use{it.copyTo(zip)}
                zip.closeEntry()
            }
        }
    }
    fun restore(context: Context, db: LabDatabase, input: InputStream) {
        val photosDir=File(context.filesDir,"culture_photos")
        val stage=File(context.filesDir,"culture_photos_stage_${UUID.randomUUID()}")
        val old=File(context.filesDir,"culture_photos_old_${UUID.randomUUID()}")
        check(stage.mkdirs()) { "Невозможно подготовить каталог восстановления" }
        var json:String?=null
        var size=0L
        val found=mutableSetOf<String>()
        var swapped=false
        var hadOld=false
        try {
            ZipInputStream(input).use { zip->
                var entry=zip.nextEntry
                while(entry!=null) {
                    val path=entry.name
                    require(!entry.isDirectory) { "Неожиданный каталог в архиве" }
                    if(path=="database.json") {
                        require(json==null) { "Дублирующий файл базы" }
                        val buf=java.io.ByteArrayOutputStream()
                        val bytes=ByteArray(8192)
                        while(true){val n=zip.read(bytes);if(n<0)break;size+=n;require(size<=500_000_000){"Архив слишком большой"};buf.write(bytes,0,n);require(buf.size()<=50_000_000){"База слишком большая"}}
                        json=buf.toString("UTF-8")
                    } else {
                        require(path.startsWith("photos/")){"Неизвестный файл в архиве"}
                        val name=path.removePrefix("photos/")
                        require(Regex("[a-zA-Z0-9_-]+\\.(jpg|png|webp|heic)").matches(name) && found.add(name)) { "Неверное или повторное имя фото" }
                        File(stage,name).outputStream().use{dest->
                            val bytes=ByteArray(8192)
                            var imageBytes=0L
                            while(true){val n=zip.read(bytes);if(n<0)break;imageBytes+=n;size+=n;require(size<=500_000_000 && imageBytes<=20_000_000){"Слишком большой фотоархив"};dest.write(bytes,0,n)}
                        }
                    }
                    zip.closeEntry()
                    entry=zip.nextEntry
                }
            }
            val data=json?:error("В архиве отсутствует база данных")
            val root=JSONObject(data)
            require(root.getString("format")=="DIKOVERIYA_LAB" && root.getInt("schema") in 4..11){"Неподдерживаемая версия копии"}
            val photoRows=root.getJSONObject("tables").getJSONArray("culture_photos")
            val expected=buildSet{
                for(i in 0 until photoRows.length())add(photoRows.getJSONObject(i).getString("filename"))
                if(root.getInt("schema")>=11){
                    val covers=root.getJSONObject("tables").getJSONArray("plant_visuals")
                    for(i in 0 until covers.length()){
                        val filename=covers.getJSONObject(i).optString("cover_filename","")
                        if(filename.isNotBlank())add(filename)
                    }
                }
            }
            require(expected==found){"В архиве нет части фотографий или есть лишние фотографии"}
            // Swap only after the entire ZIP has been validated and extracted.
            hadOld=photosDir.exists()
            if(hadOld)check(photosDir.renameTo(old)){"Не удалось сохранить предыдущие фотографии"}
            check(stage.renameTo(photosDir)){"Не удалось заменить каталог фотографий"}
            swapped=true
            // Database restore is transactional. On failure the images are rolled back.
            try { db.importJson(data) }
            catch(e:Exception){
                photosDir.deleteRecursively()
                if(hadOld) check(old.renameTo(photosDir)) { "Ошибка отката фотографий: проверь резервную копию" }
                swapped=false
                throw e
            }
            if(hadOld)old.deleteRecursively()
        } finally {
            if(!swapped) {
                stage.deleteRecursively()
                if(hadOld && old.exists() && !photosDir.exists())old.renameTo(photosDir)
            }
        }
    }
}
