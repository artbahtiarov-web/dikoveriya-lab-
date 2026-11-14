package ru.dikoveriya.lab

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import java.util.Locale

internal data class Lab11RackSlot(
    val id:Long,val name:String,val fullPath:String,val cultureCount:Int,
    val itemCount:Int
)
internal data class Lab11RackShelf(val id:Long,val name:String,val slots:List<Lab11RackSlot>,
    val directCultureCount:Int,val directItemCount:Int)
internal data class Lab11Rack(val id:Long,val name:String,val path:String,
    val room:String,val shelves:List<Lab11RackShelf>)
internal data class Lab11SlotOccupant(val id:Long,val code:String,val plant:String,
    val type:String,val status:String)

internal fun LabDatabase.groupIdByCode11(code:String):Long? =
    readableDatabase.rawQuery("SELECT id FROM production_groups WHERE code=?",
        arrayOf(code)).use{c->if(c.moveToFirst())c.getLong(0) else null}
internal fun LabDatabase.locationExists11(id:Long):Boolean =
    readableDatabase.rawQuery("SELECT 1 FROM storage_locations WHERE id=?",
        arrayOf(id.toString())).use{it.moveToFirst()}

private data class LocationPath11(val id:Long,val parent:Long?,
    val level:String,val name:String,val path:String)

internal fun LabDatabase.racks11():List<Lab11Rack>{
    val places=readableDatabase.rawQuery("""SELECT id,parent_id,level,label,full_path
        FROM storage_locations ORDER BY id""",null).use{c->buildList{
        while(c.moveToNext())add(LocationPath11(c.getLong(0),
            if(c.isNull(1))null else c.getLong(1),c.getString(2),
            c.getString(3),c.getString(4)))
    }}
    val cultures=mutableMapOf<Long,Int>()
    readableDatabase.rawQuery("""SELECT location_id,COUNT(*) FROM culture_storage
        GROUP BY location_id""",null).use{c->while(c.moveToNext()){
        cultures[c.getLong(0)]=c.getInt(1)
    }}
    val items=mutableMapOf<Long,Int>()
    readableDatabase.rawQuery("""SELECT location_id,COUNT(*) FROM plant_items
        WHERE location_id IS NOT NULL GROUP BY location_id""",null).use{c->
        while(c.moveToNext())items[c.getLong(0)]=c.getInt(1)
    }
    val shelves=places.filter{it.level=="Полка"}
    val slots=places.filter{it.level=="Позиция"}
    val racks=places.filter{it.level=="Стеллаж"}
    val rooms=places.filter{it.level=="Помещение"}.associateBy{it.id}
    return racks.map{rack->
        Lab11Rack(rack.id,rack.name,rack.path,rooms[rack.parent]?.name?:"Помещение",
            shelves.filter{it.parent==rack.id}.map{sh->
                Lab11RackShelf(sh.id,sh.name,
                    slots.filter{it.parent==sh.id}.map{slot->
                        Lab11RackSlot(slot.id,slot.name,slot.path,
                            cultures[slot.id]?:0,items[slot.id]?:0)
                    },
                    cultures[sh.id]?:0,items[sh.id]?:0)
            })
    }
}

/** Atomic visual shelf builder: room->rack->N shelves->M named positions. */
internal fun LabDatabase.createRack11(
    roomName:String,rackName:String,shelves:Int,positions:Int
):Long{
    require(roomName.trim().length in 2..80 && rackName.trim().length in 2..80)
    require(shelves in 1..20 && positions in 1..50)
    val db=writableDatabase
    db.beginTransaction()
    try{
        val roomLabel=roomName.trim()
        val room=db.rawQuery("""SELECT id FROM storage_locations
            WHERE parent_id IS NULL AND level='Помещение' AND label=?""",
            arrayOf(roomLabel)).use{c->if(c.moveToFirst())c.getLong(0) else null}
            ?:db.insertOrThrow("storage_locations",null,ContentValues().apply{
                put("level","Помещение");put("label",roomLabel)
                put("full_path",roomLabel);put("created_at",System.currentTimeMillis())
            })
        fun place(parent:Long,level:String,name:String,parentPath:String):Long =
            db.insertOrThrow("storage_locations",null,ContentValues().apply{
                put("parent_id",parent);put("level",level);put("label",name)
                put("full_path",parentPath+" / "+name)
                put("created_at",System.currentTimeMillis())
            })
        val name=rackName.trim()
        val rackPath=roomLabel+" / "+name
        val rack=place(room,"Стеллаж",name,roomLabel)
        repeat(shelves){shelfIndex->
            val shelfName="Полка "+(shelfIndex+1)
            val shelf=place(rack,"Полка",shelfName,rackPath)
            repeat(positions){pos->
                place(shelf,"Позиция","Место "+(pos+1),
                    rackPath+" / "+shelfName)
            }
        }
        db.insertOrThrow("location_layouts",null,ContentValues().apply{
            put("location_id",rack);put("shelf_count",shelves)
            put("positions_per_shelf",positions)
        })
        db.setTransactionSuccessful()
        return rack
    }finally{db.endTransaction()}
}

internal fun LabDatabase.slotOccupants11(locationId:Long):List<Lab11SlotOccupant>{
    val cultures=readableDatabase.rawQuery("""SELECT c.id,c.code,p.name,c.status
        FROM culture_storage l JOIN cultures c ON c.id=l.culture_id
        JOIN plants p ON p.id=c.plant_id WHERE l.location_id=? ORDER BY c.id""",
        arrayOf(locationId.toString())).use{c->buildList{
        while(c.moveToNext())add(Lab11SlotOccupant(c.getLong(0),c.getString(1),
            c.getString(2),"culture",c.getString(3)))
    }}
    val items=readableDatabase.rawQuery("""SELECT i.id,i.code,p.name,i.status
        FROM plant_items i JOIN plants p ON p.id=i.plant_id
        WHERE i.location_id=? ORDER BY i.id""",
        arrayOf(locationId.toString())).use{c->buildList{
        while(c.moveToNext())add(Lab11SlotOccupant(c.getLong(0),c.getString(1),
            c.getString(2),"item",c.getString(3)))
    }}
    return cultures+items
}
internal fun LabDatabase.moveItem11(itemId:Long,locationId:Long,
    at:Long=System.currentTimeMillis(),reason:String){
    require(reason.trim().length in 2..500)
    val db=writableDatabase
    db.beginTransaction()
    try{
        val loc=db.rawQuery("SELECT full_path FROM storage_locations WHERE id=?",
            arrayOf(locationId.toString())).use{c->
            require(c.moveToFirst()){"Место не найдено"};c.getString(0)
        }
        val old=db.rawQuery("""SELECT location_id,location_text,status FROM plant_items
            WHERE id=?""",arrayOf(itemId.toString())).use{c->
            require(c.moveToFirst()){"Растение не найдено"}
            Triple(if(c.isNull(0))null else c.getLong(0),c.getString(1),c.getString(2))
        }
        require(old.third=="Активен"){"Нельзя перемещать проданное или списанное растение"}
        require(old.first!=locationId){"Растение уже находится в выбранном месте"}
        val historyId=db.insertOrThrow("item_storage_history",null,ContentValues().apply{
            put("item_id",itemId)
            if(old.first!=null)put("previous_location_id",old.first)
            put("new_location_id",locationId);put("location_text",loc)
            put("occurred_at",at);put("note",reason.trim())
        })
        db.update("plant_items",ContentValues().apply{
            put("location_id",locationId);put("location_text",loc)
            put("updated_at",System.currentTimeMillis())
        },"id=?",arrayOf(itemId.toString()))
        val audit=org.json.JSONObject()
            .put("before_status",old.third).put("before_location",old.second)
            .put("before_location_id",old.first?:org.json.JSONObject.NULL)
            .put("new_location",loc).put("new_location_id",locationId)
            .put("storage_history_id",historyId)
        db.insertOrThrow("item_events",null,ContentValues().apply{
            put("item_id",itemId);put("kind","Перемещение")
            put("event_at",at);put("recorded_at",System.currentTimeMillis())
            put("note",reason+": "+loc);put("details_json",audit.toString())
        })
        db.setTransactionSuccessful()
    }finally{db.endTransaction()}
}


internal fun LabDatabase.findLocationForCode11(code:String):Long?{
    val normalized=code.trim().uppercase()
    if(normalized.startsWith("LAB-"))
        return readableDatabase.rawQuery("""SELECT cs.location_id FROM culture_storage cs
            JOIN cultures c ON c.id=cs.culture_id WHERE c.code=?""",
            arrayOf(normalized)).use{c->if(c.moveToFirst())c.getLong(0) else null}
    if(normalized.startsWith("PLT-"))
        return readableDatabase.rawQuery(
            "SELECT location_id FROM plant_items WHERE code=?",
            arrayOf(normalized)).use{c->
            if(c.moveToFirst()&&!c.isNull(0))c.getLong(0) else null
        }
    return null
}


/**
 * Atomic mixed placement. Production-group QR expands into its registered
 * member containers; repeated scanned codes never cause duplicate events.
 */
internal fun LabDatabase.moveSelected11(
    codes:List<String>,targetId:Long,reason:String
):Pair<Int,Int>{
    require(codes.isNotEmpty() && codes.size<=200){
        "Можно переместить от 1 до 200 объектов одной операцией"
    }
    require(reason.trim().length in 2..500){"Укажи причину размещения"}
    val db=writableDatabase
    db.beginTransaction()
    try{
        db.rawQuery("SELECT 1 FROM storage_locations WHERE id=?",
            arrayOf(targetId.toString())).use{
            require(it.moveToFirst()){"Место размещения не найдено"}
        }
        val plants=linkedSetOf<Long>()
        val cultures=linkedSetOf<Long>()
        codes.forEach{raw->
            val code=raw.trim().uppercase()
            when{
                code.startsWith("LAB-")->{
                    cultures+=findCulture072(code)?:error("Не найден контейнер "+code)
                }
                code.startsWith("PLT-")->{
                    plants+=itemIdByCode11(code)?:error("Не найдено растение "+code)
                }
                code.startsWith("ГР-")->{
                    val group=groupIdByCode11(code)?:error("Не найдена партия "+code)
                    val members=db.rawQuery(
                        "SELECT culture_id FROM culture_groups WHERE group_id=?",
                        arrayOf(group.toString())).use{c->buildList{
                        while(c.moveToNext())add(c.getLong(0))
                    }}
                    require(members.isNotEmpty()){"В партии нет контейнеров"}
                    cultures.addAll(members)
                }
                else->error("Неподдерживаемый номер "+code)
            }
        }
        require(cultures.size+plants.size<=200){"За один раз можно переместить не более 200 объектов"}
        if(cultures.isNotEmpty())
            moveContainers072(cultures.toList(),targetId,reason.trim())
        val at=System.currentTimeMillis()
        plants.forEach{moveItem11(it,targetId,at,reason.trim())}
        db.setTransactionSuccessful()
        return cultures.size to plants.size
    }finally{db.endTransaction()}
}
