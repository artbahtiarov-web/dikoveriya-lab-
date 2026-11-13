package ru.dikoveriya.lab

import android.content.ContentValues
import java.math.BigDecimal
import java.math.RoundingMode

internal fun rubles11(text:String):Long{
    val x=runCatching{BigDecimal(text.trim().replace(" ","").replace(',','.'))}
        .getOrNull()?:error("Укажи сумму цифрами")
    require(x>=BigDecimal.ZERO && x<BigDecimal("1000000000000")){"Сумма вне диапазона"}
    return x.multiply(BigDecimal(100)).setScale(0,RoundingMode.HALF_UP).longValueExact()
}
internal fun kopecks11(minor:Long):String=
    BigDecimal.valueOf(minor,2).setScale(2).toPlainString()+" ₽"
internal fun decimalMinor11(value:Double):Long=
    BigDecimal.valueOf(value).multiply(BigDecimal(100))
        .setScale(0,RoundingMode.HALF_UP).longValueExact()

internal data class Lab11CashRow(val id:Long,val direction:String,
    val category:String,val amount:Long,val date:Long,
    val source:String,val sourceId:Long?,val note:String)
internal data class Lab11Purchase(val id:Long,val name:String,
    val category:String,val supplier:String,val date:Long,
    val qty:Double,val unit:String,val amount:Long,
    val materialId:Long?,val assetId:Long?,val note:String)
internal data class Lab11FinanceSummary(val income:Long,val paid:Long,
    val direct:Long,val inventory:Long,val assets:Long,val salesCogs:Long,
    val unknownCogs:Int)

internal fun LabDatabase.purchase11(
    title:String,category:String,amountMinor:Long,at:Long,
    supplier:String="",url:String="",payment:String="",note:String="",
    materialId:Long?=null,quantity:Double=0.0,unit:String=""
):Long{
    require(title.trim().length in 2..160){"Укажи наименование"}
    require(category.trim().isNotEmpty() && category.length<=100)
    require(amountMinor>=0 && at>0)
    require(quantity.isFinite() && quantity>=0)
    require(materialId==null || quantity>0){"Укажи количество для склада"}
    val db=writableDatabase
    db.beginTransaction()
    try{
        if(materialId!=null){
            val old=db.rawQuery("SELECT quantity,cost_per_unit,unit FROM materials WHERE id=?",
                arrayOf(materialId.toString())).use{c->
                require(c.moveToFirst()){"Материал не найден"}
                Triple(c.getDouble(0),c.getDouble(1),c.getString(2))
            }
            require(old.third==unit || unit.isBlank()){"Единица закупки отличается от склада"}
            val next=old.first+quantity
            val weighted=(old.first*old.second+amountMinor.toDouble()/100.0)/next
            require(next.isFinite() && weighted.isFinite())
            db.update("materials",ContentValues().apply{
                put("quantity",next);put("cost_per_unit",weighted)
            },"id=?",arrayOf(materialId.toString()))
            db.insertOrThrow("material_moves",null,ContentValues().apply{
                put("material_id",materialId);put("delta",quantity)
                put("note","Закупка: "+title.trim());put("created_at",at)
            })
        }
        val id=db.insertOrThrow("purchases",null,ContentValues().apply{
            put("title",title.trim());put("category",category.trim())
            put("supplier",supplier.take(180));put("supplier_url",url.take(1000))
            put("purchased_at",at);if(materialId!=null)put("material_id",materialId)
            put("quantity",quantity);put("unit",unit.take(30))
            put("amount_minor",amountMinor)
            put("unit_cost_minor",if(quantity>0)
                BigDecimal.valueOf(amountMinor).divide(BigDecimal.valueOf(quantity),0,RoundingMode.HALF_UP).longValueExact()
                else amountMinor)
            put("pay_method",payment.take(70));put("note",note.take(2000))
        })
        db.insertOrThrow("cash_ledger",null,ContentValues().apply{
            put("direction","Расход");put("category",category)
            put("amount_minor",amountMinor);put("occurred_at",at)
            put("source_type","purchase");put("source_id",id)
            put("note",title.trim())
        })
        db.setTransactionSuccessful()
        return id
    }finally{db.endTransaction()}
}

internal fun LabDatabase.projectExpense11(
    category:String,amountMinor:Long,at:Long,note:String
):Long{
    require(category.isNotBlank() && category.length<=100)
    require(amountMinor>=0 && at>0 && note.length<=2000)
    return writableDatabase.insertOrThrow("cash_ledger",null,ContentValues().apply{
        put("direction","Расход");put("category",category)
        put("amount_minor",amountMinor);put("occurred_at",at)
        put("source_type","general_expense");put("note",note)
    })
}

/** Material consumption adds direct cost but must NOT create a new money outflow. */
internal fun LabDatabase.consumeMaterial11(
    materialId:Long,qty:Double,entityKind:String,entityId:Long,
    usedAt:Long=System.currentTimeMillis(),note:String=""
):Long{
    require(qty.isFinite() && qty>0 && qty<100000000.0)
    require(entityKind in setOf("item","culture") && usedAt>0)
    val db=writableDatabase
    db.beginTransaction()
    try{
        val table=if(entityKind=="item")"plant_items" else "cultures"
        db.rawQuery("SELECT 1 FROM "+table+" WHERE id=?",
            arrayOf(entityId.toString())).use{require(it.moveToFirst()){"Объект не найден"}}
        val old=db.rawQuery("SELECT quantity,cost_per_unit FROM materials WHERE id=?",
            arrayOf(materialId.toString())).use{c->
                require(c.moveToFirst()){"Материал не найден"}
                c.getDouble(0) to c.getDouble(1)
            }
        require(qty<=old.first+1e-8){"На складе недостаточно материала"}
        val total=decimalMinor11(qty*old.second)
        val unit=decimalMinor11(old.second)
        require(total>=0 && unit>=0)
        db.update("materials",ContentValues().apply{
            put("quantity",(old.first-qty).coerceAtLeast(0.0))
        },"id=?",arrayOf(materialId.toString()))
        db.insertOrThrow("material_moves",null,ContentValues().apply{
            put("material_id",materialId);put("delta",-qty)
            put("note","Расход "+entityKind+" #"+entityId+": "+note)
            put("created_at",usedAt)
        })
        val usage=db.insertOrThrow("stock_usage",null,ContentValues().apply{
            put("material_id",materialId);put("entity_kind",entityKind)
            put("entity_id",entityId);put("quantity",qty)
            put("unit_cost_minor",unit);put("total_minor",total)
            put("used_at",usedAt);put("note",note.take(2000))
        })
        db.insertOrThrow("direct_costs",null,ContentValues().apply{
            put("entity_kind",entityKind);put("entity_id",entityId)
            put("amount_minor",total);put("category","Материалы")
            put("occurred_at",usedAt);put("source_type","stock_usage")
            put("source_id",usage);put("note",note.take(2000))
        })
        db.setTransactionSuccessful()
        return usage
    }finally{db.endTransaction()}
}

internal fun LabDatabase.directCost11(kind:String,id:Long):Long=
    readableDatabase.rawQuery("""SELECT COALESCE(SUM(amount_minor),0)
        FROM direct_costs WHERE entity_kind=? AND entity_id=?""",
        arrayOf(kind,id.toString())).use{it.moveToFirst();it.getLong(0)}

internal fun LabDatabase.cashRows11(limit:Int=300):List<Lab11CashRow>{
    val lim=limit.coerceIn(1,2000)
    return readableDatabase.rawQuery("""SELECT id,direction,category,amount_minor,
        occurred_at,source_type,source_id,note FROM cash_ledger
        ORDER BY occurred_at DESC,id DESC LIMIT """+lim,null).use{c->buildList{
        while(c.moveToNext())add(Lab11CashRow(c.getLong(0),c.getString(1),
            c.getString(2),c.getLong(3),c.getLong(4),c.getString(5),
            if(c.isNull(6))null else c.getLong(6),c.getString(7)))
    }}
}
internal fun LabDatabase.purchases11(limit:Int=300):List<Lab11Purchase>{
    val lim=limit.coerceIn(1,2000)
    return readableDatabase.rawQuery("""SELECT id,title,category,supplier,purchased_at,
        quantity,unit,amount_minor,material_id,asset_id,note
        FROM purchases ORDER BY purchased_at DESC,id DESC LIMIT """+lim,null).use{c->buildList{
        while(c.moveToNext())add(Lab11Purchase(c.getLong(0),c.getString(1),
            c.getString(2),c.getString(3),c.getLong(4),c.getDouble(5),
            c.getString(6),c.getLong(7),
            if(c.isNull(8))null else c.getLong(8),
            if(c.isNull(9))null else c.getLong(9),c.getString(10)))
    }}
}

internal fun LabDatabase.financeSummary11(from:Long=0L,to:Long=Long.MAX_VALUE):Lab11FinanceSummary{
    val db=readableDatabase
    fun sum(sql:String,args:Array<String> = emptyArray()):Long=
        db.rawQuery(sql,args).use{it.moveToFirst();it.getLong(0)}
    val dates=arrayOf(from.toString(),to.toString())
    val income=sum("SELECT COALESCE(SUM(amount_minor),0) FROM cash_ledger WHERE direction='Доход' AND occurred_at BETWEEN ? AND ?",dates)
    val paid=sum("SELECT COALESCE(SUM(amount_minor),0) FROM cash_ledger WHERE direction='Расход' AND occurred_at BETWEEN ? AND ?",dates)
    val direct=sum("SELECT COALESCE(SUM(amount_minor),0) FROM direct_costs WHERE occurred_at BETWEEN ? AND ?",dates)
    val inventory=decimalMinor11(db.rawQuery(
        "SELECT COALESCE(SUM(quantity*cost_per_unit),0) FROM materials",null
    ).use{it.moveToFirst();it.getDouble(0)})
    val assets=sum("SELECT COALESCE(SUM(purchase_price_minor),0) FROM equipment_assets")
    val cogs=sum("SELECT COALESCE(SUM(cost_minor),0) FROM item_sales WHERE sold_at BETWEEN ? AND ?",dates)+
        sum("SELECT COALESCE(SUM(d.cogs_minor),0) FROM sale_details d JOIN sales s ON s.id=d.sale_id WHERE s.sold_at BETWEEN ? AND ?",dates)
    val unknown=sum("""SELECT COUNT(*) FROM sales s LEFT JOIN sale_details d ON d.sale_id=s.id
        WHERE d.cogs_minor IS NULL AND s.sold_at BETWEEN ? AND ?""",dates).toInt()
    return Lab11FinanceSummary(income,paid,direct,inventory,assets,cogs,unknown)
}
