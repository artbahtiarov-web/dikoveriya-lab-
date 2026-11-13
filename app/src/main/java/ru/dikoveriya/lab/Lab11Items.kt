package ru.dikoveriya.lab

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.graphics.BitmapFactory
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.UUID

/** One physical plant, cutting or mother specimen. In-vitro cultures keep their original IDs. */
internal data class Lab11PlantItem(
    val id:Long,val code:String,val plantId:Long,val plantName:String,
    val kind:String,val stage:String,val status:String,
    val acquiredAt:Long,val supplier:String,val purchaseMinor:Long,
    val parentItemId:Long?,val parentCultureId:Long?,
    val locationId:Long?,val location:String,
    val substrate:String,val rootingMethod:String,val stimulant:String,
    val potMl:Int?,val notes:String,val favorite:Boolean,val cover:String?,
    val customKind:String=""
)

internal data class Lab11ItemEvent(
    val id:Long,val kind:String,val time:Long,val note:String,
    val details:String,val cancelled:Boolean
)

private val itemKinds11=setOf("Горшечное","Черенок","Маточник","Другое")
private fun text11(value:String,max:Int=2000):String{
    require(value.length<=max){"Поле слишком длинное"}
    return value.trim()
}
private fun SQLiteDatabase.isExisting11(table:String,id:Long):Boolean=
    rawQuery("SELECT 1 FROM "+table+" WHERE id=?",arrayOf(id.toString()))
        .use{it.moveToFirst()}
private fun SQLiteDatabase.itemPlant11(id:Long):Long=
    rawQuery("SELECT plant_id FROM plant_items WHERE id=?",arrayOf(id.toString()))
        .use{c->require(c.moveToFirst()){"Растение не найдено"};c.getLong(0)}
private fun SQLiteDatabase.culturePlant11(id:Long):Long=
    rawQuery("SELECT plant_id FROM cultures WHERE id=?",arrayOf(id.toString()))
        .use{c->require(c.moveToFirst()){"Исходный контейнер не найден"};c.getLong(0)}
private fun SQLiteDatabase.newItemRow11(
    plantId:Long,kind:String,acquiredAt:Long,supplier:String,costMinor:Long,
    parentItemId:Long?,parentCultureId:Long?,locationId:Long?,
    locationText:String,substrate:String,rooting:String,stimulant:String,
    potMl:Int?,note:String,customKind:String=""
):Long{
    require(isExisting11("plants",plantId)){"Сорт не найден"}
    require(kind in itemKinds11){"Неизвестный тип растения"}
    require(acquiredAt>0 && acquiredAt<=System.currentTimeMillis()+86400000L){"Неверная дата"}
    require(costMinor>=0){"Стоимость не может быть отрицательной"}
    require(parentItemId==null || parentCultureId==null){"Выбери одного исходного родителя"}
    if(parentItemId!=null)require(itemPlant11(parentItemId)==plantId){"Родитель другого сорта"}
    if(parentCultureId!=null)require(culturePlant11(parentCultureId)==plantId){"Контейнер другого сорта"}
    if(locationId!=null)require(isExisting11("storage_locations",locationId)){"Место не найдено"}
    require(potMl==null || potMl in 1..500000){"Укажи объём горшка в мл"}
    val cv=ContentValues().apply {
        put("code","PENDING-"+UUID.randomUUID());put("plant_id",plantId)
        put("kind",kind);put("custom_kind",customKind.trim().take(100))
        put("stage",if(kind=="Черенок")"Укоренение" else "В работе")
        put("status","Активен");put("acquired_at",acquiredAt)
        put("created_at",System.currentTimeMillis());put("updated_at",System.currentTimeMillis())
        put("supplier",text11(supplier,200));put("purchase_cost_minor",costMinor)
        if(parentItemId!=null)put("parent_item_id",parentItemId)
        if(parentCultureId!=null)put("parent_culture_id",parentCultureId)
        if(locationId!=null)put("location_id",locationId)
        put("location_text",text11(locationText,200))
        put("substrate",text11(substrate,500));put("rooting_method",text11(rooting,300))
        put("stimulant",text11(stimulant,300));if(potMl!=null)put("pot_volume_ml",potMl)
        put("notes",text11(note))
    }
    val id=insertOrThrow("plant_items",null,cv)
    update("plant_items",ContentValues().apply{
        put("code","PLT-%06d".format(Locale.US,id))
    },"id=?",arrayOf(id.toString()))
    if(parentItemId!=null || parentCultureId!=null)
        insertOrThrow("item_links",null,ContentValues().apply{
            put("child_item_id",id)
            if(parentItemId!=null)put("parent_item_id",parentItemId)
            if(parentCultureId!=null)put("parent_culture_id",parentCultureId)
            put("kind",if(kind=="Черенок")"Черенкование" else "Происхождение")
            put("linked_at",acquiredAt)
        })
    insertOrThrow("item_events",null,ContentValues().apply{
        put("item_id",id);put("kind","Создание")
        put("event_at",acquiredAt);put("recorded_at",System.currentTimeMillis())
        put("note",text11(note))
    })
    if(costMinor>0){
        // This payment is dated at acquisition; it is NOT charged again upon use.
        insertOrThrow("cash_ledger",null,ContentValues().apply{
            put("direction","Расход");put("category","Покупка растения")
            put("amount_minor",costMinor);put("occurred_at",acquiredAt)
            put("source_type","item_acquisition");put("source_id",id)
            put("note","Приобретение "+kind)
        })
    }
    return id
}

internal fun LabDatabase.addPlantItem11(
    plantId:Long,kind:String,acquiredAt:Long=System.currentTimeMillis(),
    supplier:String="",costMinor:Long=0,
    parentItemId:Long?=null,parentCultureId:Long?=null,
    locationId:Long?=null,locationText:String="",substrate:String="",
    rooting:String="",stimulant:String="",potMl:Int?=null,note:String=""
):Long{
    val db=writableDatabase
    db.beginTransaction()
    try{
        val id=db.newItemRow11(plantId,kind,acquiredAt,supplier,costMinor,
            parentItemId,parentCultureId,locationId,locationText,
            substrate,rooting,stimulant,potMl,note)
        db.setTransactionSuccessful()
        return id
    }finally{db.endTransaction()}
}

/** One atomic cutting and parental audit event; no silent reduction of a living mother plant. */
internal fun LabDatabase.takeCutting11(parentItemId:Long,date:Long,
    rooting:String,stimulant:String,location:String,note:String):Long{
    val db=writableDatabase
    db.beginTransaction()
    try{
        val plantId=db.itemPlant11(parentItemId)
        val id=db.newItemRow11(plantId,"Черенок",date,"",0,parentItemId,
            null,null,location,"",rooting,stimulant,null,note)
        db.insertOrThrow("item_events",null,ContentValues().apply{
            put("item_id",parentItemId);put("kind","Черенкование")
            put("event_at",date);put("recorded_at",System.currentTimeMillis())
            put("note","Создан дочерний черенок PLT-%06d. ".format(Locale.US,id)+note)
        })
        db.setTransactionSuccessful()
        return id
    }finally{db.endTransaction()}
}

private const val ITEM_SELECT11="""SELECT i.id,i.code,i.plant_id,p.name,i.kind,i.stage,
    i.status,i.acquired_at,i.supplier,i.purchase_cost_minor,
    i.parent_item_id,i.parent_culture_id,i.location_id,
    i.location_text,i.substrate,i.rooting_method,i.stimulant,i.pot_volume_ml,
    i.notes,i.favorite,i.cover_filename FROM plant_items i JOIN plants p ON p.id=i.plant_id"""
private fun Cursor.plantItem11():Lab11PlantItem =
    Lab11PlantItem(getLong(0),getString(1),getLong(2),getString(3),
        getString(4),getString(5),getString(6),getLong(7),getString(8),getLong(9),
        if(isNull(10))null else getLong(10),if(isNull(11))null else getLong(11),
        if(isNull(12))null else getLong(12),getString(13),getString(14),
        getString(15),getString(16),if(isNull(17))null else getInt(17),
        getString(18),getInt(19)==1,if(isNull(20))null else getString(20))

private fun itemWhere11(query:String,kind:String,favorites:Boolean,
    stage:String,status:String):Pair<String,Array<String>>{
    val conditions=mutableListOf<String>()
    val args=mutableListOf<String>()
    if(query.isNotBlank()){
        conditions+="(i.code LIKE ? OR p.name LIKE ? OR i.location_text LIKE ? OR EXISTS (SELECT 1 FROM cultures c WHERE c.id=i.parent_culture_id AND c.code LIKE ?))"
        repeat(4){args+="%"+query.trim().take(100)+"%"}
    }
    if(kind.isNotBlank()){conditions+="i.kind=?";args+=kind}
    if(favorites)conditions+="i.favorite=1"
    if(stage.isNotBlank()){conditions+="i.stage=?";args+=stage}
    if(status.isNotBlank()){conditions+="i.status=?";args+=status}
    return (if(conditions.isEmpty())"" else " WHERE "+conditions.joinToString(" AND ")) to args.toTypedArray()
}

/** SQL paging, not loading 20k plant records and bitmap covers into memory. */
internal fun LabDatabase.plantItems11(query:String="",limit:Int=40,offset:Int=0,
    kind:String="",favorites:Boolean=false,
    stage:String="",status:String=""):List<Lab11PlantItem>{
    require(limit in 1..100 && offset>=0)
    val (where,args)=itemWhere11(query,kind,favorites,stage,status)
    return readableDatabase.rawQuery(
        ITEM_SELECT11+where+" ORDER BY i.favorite DESC,i.updated_at DESC,i.id DESC LIMIT "+limit+" OFFSET "+offset,
        args).use{c->buildList{while(c.moveToNext())add(c.plantItem11())}}
}
internal fun LabDatabase.itemsCount11(query:String="",kind:String="",
    favorites:Boolean=false,stage:String="",status:String=""):Int{
    val (where,args)=itemWhere11(query,kind,favorites,stage,status)
    return readableDatabase.rawQuery(
        "SELECT COUNT(*) FROM plant_items i JOIN plants p ON p.id=i.plant_id"+where,args
    ).use{c->c.moveToFirst();c.getInt(0)}
}
internal fun LabDatabase.item11(id:Long):Lab11PlantItem? =
    readableDatabase.rawQuery(ITEM_SELECT11+" WHERE i.id=?",
        arrayOf(id.toString())).use{c->if(c.moveToFirst())c.plantItem11() else null}

internal fun LabDatabase.itemIdByCode11(code:String):Long? =
    readableDatabase.rawQuery("SELECT id FROM plant_items WHERE code=? LIMIT 1",
        arrayOf(code.trim())).use{c->if(c.moveToFirst())c.getLong(0) else null}

internal fun LabDatabase.itemEvents11(id:Long):List<Lab11ItemEvent> =
    readableDatabase.rawQuery("""SELECT id,kind,event_at,note,details_json,cancelled
        FROM item_events WHERE item_id=? ORDER BY event_at DESC,id DESC LIMIT 1000""",
        arrayOf(id.toString())).use{c->buildList{
            while(c.moveToNext())add(Lab11ItemEvent(c.getLong(0),c.getString(1),
                c.getLong(2),c.getString(3),c.getString(4),c.getInt(5)==1))
        }}

/** Ordinary care is an audited event, not a fictitious quantity operation. */
internal fun LabDatabase.recordItemEvent11(
    id:Long,kind:String,note:String,at:Long=System.currentTimeMillis(),
    newStage:String?=null,newLocation:String?=null,
    substrate:String?=null,stimulant:String?=null
){
    require(kind.trim().length in 2..80){"Назови действие"}
    require(at>0 && at<=System.currentTimeMillis()+86400000L){"Некорректная дата"}
    val db=writableDatabase
    db.beginTransaction()
    try{
        val row=db.rawQuery("SELECT status FROM plant_items WHERE id=?",
            arrayOf(id.toString())).use{c->
            require(c.moveToFirst()){"Объект не найден"};c.getString(0)
        }
        require(row!="Утилизирован" && row!="Продан" || kind=="Исправление"){
            "Нельзя менять закрытое растение без исправления"
        }
        val details=JSONObject()
        if(newStage!=null)details.put("stage",newStage)
        if(newLocation!=null)details.put("location",newLocation)
        if(substrate!=null)details.put("substrate",substrate)
        if(stimulant!=null)details.put("stimulant",stimulant)
        db.insertOrThrow("item_events",null,ContentValues().apply{
            put("item_id",id);put("kind",text11(kind,80))
            put("event_at",at);put("recorded_at",System.currentTimeMillis())
            put("note",text11(note));put("details_json",details.toString())
        })
        val changes=ContentValues().apply{
            if(newStage!=null)put("stage",text11(newStage,100))
            if(newLocation!=null)put("location_text",text11(newLocation,200))
            if(substrate!=null)put("substrate",text11(substrate,500))
            if(stimulant!=null)put("stimulant",text11(stimulant,300))
            if(kind=="Утилизация")put("status","Утилизирован")
            put("updated_at",System.currentTimeMillis())
        }
        require(db.update("plant_items",changes,"id=?",arrayOf(id.toString()))==1)
        db.setTransactionSuccessful()
    }finally{db.endTransaction()}
}

internal fun LabDatabase.setFavorite11(id:Long,favorite:Boolean){
    require(writableDatabase.update("plant_items",ContentValues().apply{
        put("favorite",if(favorite)1 else 0)
    },"id=?",arrayOf(id.toString()))==1){"Объект не найден"}
}

internal fun LabDatabase.itemLineage11(id:Long):List<String>{
    val db=readableDatabase
    return db.rawQuery("""WITH RECURSIVE ancestors(id,depth) AS(
        SELECT parent_item_id,1 FROM plant_items WHERE id=? AND parent_item_id IS NOT NULL
        UNION ALL
        SELECT p.parent_item_id,a.depth+1 FROM plant_items p JOIN ancestors a ON p.id=a.id
        WHERE p.parent_item_id IS NOT NULL AND a.depth<50
    ) SELECT p.code || ' · ' || p.kind FROM ancestors a JOIN plant_items p ON p.id=a.id
        ORDER BY a.depth""",arrayOf(id.toString())).use{c->buildList{
            while(c.moveToNext())add(c.getString(0))
        }}
}

/** Store photo in the existing portable media directory to retain ZIP compatibility. */
internal fun LabDatabase.addItemPhoto11(context:Context,itemId:Long,uri:Uri,caption:String){
    require(item11(itemId)!=null){"Объект не найден"}
    val ext=when(context.contentResolver.getType(uri)){
        "image/jpeg"->"jpg";"image/png"->"png";"image/webp"->"webp"
        "image/heic","image/heif"->"heic"
        else->error("Поддерживаются JPEG, PNG, WebP, HEIC")
    }
    val folder=File(context.filesDir,"culture_photos").apply{mkdirs()}
    val name="obj_"+UUID.randomUUID()+"."+ext
    val file=File(folder,name)
    try{
        context.contentResolver.openInputStream(uri)?.use{input->
            file.outputStream().use{output->
                val buffer=ByteArray(8192);var count=0L
                while(true){
                    val n=input.read(buffer);if(n<0)break
                    count+=n;require(count<=20_000_000){"Изображение больше 20 МБ"}
                    output.write(buffer,0,n)
                }
                require(count>0){"Файл пуст"}
            }
        }?:error("Не удалось открыть изображение")
        val info=BitmapFactory.Options().apply{inJustDecodeBounds=true}
        BitmapFactory.decodeFile(file.absolutePath,info)
        require(info.outWidth in 1..12000 && info.outHeight in 1..12000){
            "Неподдерживаемое изображение"
        }
        val db=writableDatabase
        db.beginTransaction()
        try{
            db.insertOrThrow("item_photos",null,ContentValues().apply{
                put("entity_kind","item");put("entity_id",itemId)
                put("filename",name);put("caption",text11(caption,300))
                put("added_at",System.currentTimeMillis())
            })
            db.update("plant_items",ContentValues().apply{
                put("cover_filename",name)
            },"id=?",arrayOf(itemId.toString()))
            db.setTransactionSuccessful()
        }finally{db.endTransaction()}
    }catch(e:Exception){file.delete();throw e}
}

internal fun LabDatabase.itemPhotos11(id:Long):List<Pair<String,String>> =
    readableDatabase.rawQuery("SELECT filename,caption FROM item_photos WHERE entity_kind='item' AND entity_id=? ORDER BY added_at DESC",
        arrayOf(id.toString())).use{c->buildList{
            while(c.moveToNext())add(c.getString(0) to c.getString(1))
        }}


internal fun LabDatabase.itemFilterValues11(column:String):List<String>{
    val safe=when(column){"stage"->"stage";"status"->"status";else->error("Неверный фильтр")}
    return readableDatabase.rawQuery(
        "SELECT DISTINCT "+safe+" FROM plant_items WHERE "+safe+"<>'' ORDER BY "+safe,
        null).use{c->buildList{while(c.moveToNext())add(c.getString(0))}}
}
