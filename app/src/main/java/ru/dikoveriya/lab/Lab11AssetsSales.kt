package ru.dikoveriya.lab

import android.content.ContentValues
import java.time.YearMonth
import kotlin.math.roundToLong

internal data class Lab11Asset(val id:Long,val name:String,val category:String,
    val supplier:String,val purchasedAt:Long,val cost:Long,
    val status:String,val location:String,val nominalWatts:Double?,
    val measuredWatts:Double?)
internal data class Lab11LightProfile(val id:Long,val assetId:Long?,
    val locationId:Long?,val measuredWatts:Double,val hoursPerDay:Double,
    val tariffMinor:Int,val from:Long,val to:Long?)
internal data class Lab11Sale(val id:Long,val itemCode:String,
    val itemKind:String,val date:Long,val quantity:Int,val revenue:Long,
    val cogs:Long?,val channel:String,val buyer:String,val note:String,
    val cultureId:Long?,val plantItemId:Long?)

/** Capital purchase: cash expense now, asset retained; never charged again on disposal. */
internal fun LabDatabase.addAsset11(name:String,category:String,supplier:String,
    cost:Long,at:Long,location:String="",nominalWatts:Double?=null,
    measuredWatts:Double?=null,note:String=""):Long{
    require(name.trim().length in 2..160 && cost>=0 && at>0)
    require(nominalWatts==null || nominalWatts.isFinite() && nominalWatts>=0)
    require(measuredWatts==null || measuredWatts.isFinite() && measuredWatts>=0)
    val db=writableDatabase
    db.beginTransaction()
    try{
        val id=db.insertOrThrow("equipment_assets",null,ContentValues().apply{
            put("name",name.trim());put("category",category.take(100))
            put("supplier",supplier.take(160));put("purchase_price_minor",cost)
            put("purchased_at",at);put("location_text",location.take(200))
            if(nominalWatts!=null)put("nominal_watts",nominalWatts)
            if(measuredWatts!=null)put("measured_watts",measuredWatts)
            put("notes",note.take(2000))
        })
        val purchase=db.insertOrThrow("purchases",null,ContentValues().apply{
            put("title",name.trim());put("category","Оборудование")
            put("supplier",supplier.take(160));put("purchased_at",at)
            put("asset_id",id);put("amount_minor",cost);put("unit_cost_minor",cost)
            put("quantity",1.0);put("unit","шт.")
        })
        db.insertOrThrow("cash_ledger",null,ContentValues().apply{
            put("direction","Расход");put("category","Оборудование")
            put("amount_minor",cost);put("occurred_at",at)
            put("source_type","purchase");put("source_id",purchase);put("note",name)
        })
        db.insertOrThrow("asset_events",null,ContentValues().apply{
            put("asset_id",id);put("kind","Приобретено")
            put("occurred_at",at);put("note",note.take(2000))
        })
        db.setTransactionSuccessful()
        return id
    }finally{db.endTransaction()}
}
/**
 * Closing equipment never repeats its acquisition payment. If it was sold,
 * actual proceeds are recorded as a separate receipt linked to the audit event.
 */
internal fun LabDatabase.retireAsset11(id:Long,status:String,reason:String,
    saleProceedsMinor:Long=0L){
    require(status in setOf("Сломано","Утеряно","Утилизировано","Продано","Заменено"))
    require(reason.trim().length in 2..500)
    require(saleProceedsMinor>=0 && (status=="Продано" || saleProceedsMinor==0L)){
        "Выручка допустима только при продаже оборудования"
    }
    val db=writableDatabase
    db.beginTransaction()
    try{
        require(db.update("equipment_assets",ContentValues().apply{
            put("status",status)
        },"id=? AND status='Используется'",arrayOf(id.toString()))==1){
            "Оборудование уже закрыто или не найдено"
        }
        val at=System.currentTimeMillis()
        val eventId=db.insertOrThrow("asset_events",null,ContentValues().apply{
            put("asset_id",id);put("kind",status)
            put("note",reason.trim());put("occurred_at",at)
        })
        if(status=="Продано"){
            db.insertOrThrow("cash_ledger",null,ContentValues().apply{
                put("direction","Доход");put("category","Продажа оборудования")
                put("amount_minor",saleProceedsMinor);put("occurred_at",at)
                put("source_type","asset_sale");put("source_id",eventId)
                put("note",reason.trim())
            })
        }
        db.setTransactionSuccessful()
    }finally{db.endTransaction()}
}
internal fun LabDatabase.assets11():List<Lab11Asset> =
    readableDatabase.rawQuery("""SELECT id,name,category,supplier,purchased_at,
        purchase_price_minor,status,location_text,nominal_watts,measured_watts
        FROM equipment_assets ORDER BY purchased_at DESC,id DESC LIMIT 1000""",null)
        .use{c->buildList{
            while(c.moveToNext())add(Lab11Asset(c.getLong(0),c.getString(1),
                c.getString(2),c.getString(3),c.getLong(4),c.getLong(5),
                c.getString(6),c.getString(7),
                if(c.isNull(8))null else c.getDouble(8),
                if(c.isNull(9))null else c.getDouble(9)))
        }}
internal fun LabDatabase.assetEvents11(assetId:Long):List<Pair<String,String>> =
    readableDatabase.rawQuery("SELECT kind,note FROM asset_events WHERE asset_id=? ORDER BY occurred_at DESC",
        arrayOf(assetId.toString())).use{c->buildList{
        while(c.moveToNext())add(c.getString(0) to c.getString(1))
    }}

internal fun LabDatabase.addLight11(assetId:Long?,locationId:Long?,
    measuredW:Double,hours:Double,tariffMinor:Int,
    from:Long=System.currentTimeMillis(),ppfd:Double?=null):Long{
    require(measuredW.isFinite() && measuredW in 0.0..50000.0)
    require(hours.isFinite() && hours in 0.0..24.0)
    require(tariffMinor>=0 && from>0 && (ppfd==null || ppfd>=0 && ppfd.isFinite()))
    require(assetId!=null || locationId!=null){"Выбери лампу или место"}
    // A revised tariff or lighting schedule closes the previous period.
    // Otherwise two active profiles would double-count electricity.
    val db=writableDatabase
    db.beginTransaction()
    try{
        val where=if(assetId!=null)"asset_id=?" else "asset_id IS NULL AND location_id=?"
        val key=(assetId?:requireNotNull(locationId)).toString()
        val current=db.rawQuery("""SELECT id,from_date FROM light_profiles
            WHERE """+where+""" AND until_date IS NULL
            ORDER BY from_date DESC,id DESC LIMIT 1""",
            arrayOf(key)).use{c->
            if(c.moveToFirst())c.getLong(0) to c.getLong(1) else null
        }
        if(current!=null){
            require(from>current.second){
                "Новый тариф или режим должен начинаться позже предыдущего"
            }
            require(db.update("light_profiles",ContentValues().apply{
                put("until_date",from)
            },"id=?",arrayOf(current.first.toString()))==1)
        }
        val id=db.insertOrThrow("light_profiles",null,ContentValues().apply{
            if(assetId!=null)put("asset_id",assetId)
            if(locationId!=null)put("location_id",locationId)
            put("measured_watts",measuredW);put("nominal_watts",measuredW)
            put("hours_per_day",hours);put("tariff_minor_per_kwh",tariffMinor)
            put("from_date",from);if(ppfd!=null)put("measured_ppfd",ppfd)
        })
        db.setTransactionSuccessful()
        return id
    }finally{db.endTransaction()}
}
internal fun LabDatabase.lightProfiles11():List<Lab11LightProfile> =
    readableDatabase.rawQuery("""SELECT id,asset_id,location_id,measured_watts,
        hours_per_day,tariff_minor_per_kwh,from_date,until_date
        FROM light_profiles ORDER BY from_date DESC,id DESC""",null).use{c->buildList{
        while(c.moveToNext())add(Lab11LightProfile(c.getLong(0),
            if(c.isNull(1))null else c.getLong(1),
            if(c.isNull(2))null else c.getLong(2),c.getDouble(3),c.getDouble(4),
            c.getInt(5),c.getLong(6),if(c.isNull(7))null else c.getLong(7)))
    }}
internal fun Lab11LightProfile.estimatedMonth11(month:YearMonth):Long{
    val first=month.atDay(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    val end=month.plusMonths(1).atDay(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    val start=maxOf(first,from)
    val stop=minOf(end,to?:end)
    val days=((stop-start).coerceAtLeast(0L)/86400000.0)
    val kopecks=measuredWatts/1000.0*hoursPerDay*days*tariffMinor
    return kopecks.roundToLong().coerceAtLeast(0L)
}

/** Sale price and verified item COGS freeze once; future clones do not rewrite it. */
internal fun LabDatabase.sellItem11(id:Long,amountMinor:Long,
    channel:String,buyer:String,note:String,at:Long=System.currentTimeMillis()):Long{
    require(amountMinor>=0 && at>0)
    val db=writableDatabase
    db.beginTransaction()
    try{
        val old=db.rawQuery("SELECT status,purchase_cost_minor FROM plant_items WHERE id=?",
            arrayOf(id.toString())).use{c->require(c.moveToFirst()){"Растение не найдено"}
            c.getString(0) to c.getLong(1)}
        require(old.first=="Активен"){"Растение неактивно"}
        val direct=db.rawQuery("""SELECT COALESCE(SUM(amount_minor),0) FROM direct_costs
            WHERE entity_kind='item' AND entity_id=? AND reversed=0""",
            arrayOf(id.toString()))
            .use{c->c.moveToFirst();c.getLong(0)}
        val sale=db.insertOrThrow("item_sales",null,ContentValues().apply{
            put("item_id",id);put("sold_at",at);put("amount_minor",amountMinor)
            put("cost_minor",old.second+direct)
            put("channel",channel.take(100));put("buyer",buyer.take(140))
            put("note",note.take(2000))
        })
        db.update("plant_items",ContentValues().apply{
            put("status","Продан");put("updated_at",System.currentTimeMillis())
        },"id=?",arrayOf(id.toString()))
        db.insertOrThrow("cash_ledger",null,ContentValues().apply{
            put("direction","Доход");put("category","Продажа")
            put("amount_minor",amountMinor);put("occurred_at",at)
            put("source_type","item_sale");put("source_id",sale)
            put("note",note.take(2000))
        })
        db.insertOrThrow("item_events",null,ContentValues().apply{
            put("item_id",id);put("kind","Продажа")
            put("event_at",at);put("recorded_at",System.currentTimeMillis())
            put("note",note.take(2000))
        })
        db.setTransactionSuccessful()
        return sale
    }finally{db.endTransaction()}
}
internal fun LabDatabase.sales11(limit:Int=500):List<Lab11Sale>{
    val lim=limit.coerceIn(1,2000)
    val result=mutableListOf<Lab11Sale>()
    readableDatabase.rawQuery("""SELECT s.id,c.code,s.sold_at,s.quantity,s.price,
        d.cogs_minor,COALESCE(d.channel,''),COALESCE(d.buyer,''),s.note,s.culture_id
        FROM sales s JOIN cultures c ON c.id=s.culture_id
        LEFT JOIN sale_details d ON d.sale_id=s.id
        ORDER BY s.sold_at DESC LIMIT """+lim,null).use{c->
        while(c.moveToNext())result+=Lab11Sale(c.getLong(0),c.getString(1),
            "in vitro",c.getLong(2),c.getInt(3),decimalMinor11(c.getDouble(4)),
            if(c.isNull(5))null else c.getLong(5),c.getString(6),c.getString(7),
            c.getString(8),c.getLong(9),null)
    }
    readableDatabase.rawQuery("""SELECT s.id,p.code,p.kind,s.sold_at,s.amount_minor,
        s.cost_minor,s.channel,s.buyer,s.note,s.item_id
        FROM item_sales s JOIN plant_items p ON p.id=s.item_id
        ORDER BY s.sold_at DESC LIMIT """+lim,null).use{c->
        while(c.moveToNext())result+=Lab11Sale(c.getLong(0),c.getString(1),
            c.getString(2),c.getLong(3),1,c.getLong(4),
            if(c.isNull(5))null else c.getLong(5),c.getString(6),c.getString(7),
            c.getString(8),null,c.getLong(9))
    }
    return result.sortedWith(compareByDescending<Lab11Sale>{it.date}
        .thenByDescending{it.id}).take(lim)
}

/** Date and cost of an equipment service are audited without rebooking its original purchase. */
internal fun LabDatabase.recordAssetService11(assetId:Long,kind:String,
    at:Long,reason:String,expenseMinor:Long=0L):Long{
    require(kind in setOf("Проверка","Обслуживание","Ремонт","Замена расходника"))
    require(at>0 && at<=System.currentTimeMillis()+86400000L)
    require(reason.trim().length in 2..1000 && expenseMinor>=0)
    val db=writableDatabase
    db.beginTransaction()
    try{
        db.rawQuery("SELECT status FROM equipment_assets WHERE id=?",
            arrayOf(assetId.toString())).use{c->
            require(c.moveToFirst()){"Оборудование не найдено"}
            require(c.getString(0)=="Используется"){"Оборудование выведено из эксплуатации"}
        }
        val id=db.insertOrThrow("asset_events",null,ContentValues().apply{
            put("asset_id",assetId);put("kind",kind)
            put("occurred_at",at);put("note",reason.trim())
        })
        if(expenseMinor>0)db.insertOrThrow("cash_ledger",null,ContentValues().apply{
            put("direction","Расход");put("category",kind+" оборудования")
            put("amount_minor",expenseMinor);put("occurred_at",at)
            put("source_type","asset_service");put("source_id",id)
            put("note",reason.trim())
        })
        db.setTransactionSuccessful()
        return id
    }finally{db.endTransaction()}
}

internal fun LabDatabase.lightLocations11():List<Pair<Long,String>> =
    readableDatabase.rawQuery("""SELECT id,full_path FROM storage_locations
        WHERE level IN ('Полка','Стеллаж') ORDER BY full_path LIMIT 2000""",null)
        .use{c->buildList{
            while(c.moveToNext())add(c.getLong(0) to c.getString(1))
        }}
