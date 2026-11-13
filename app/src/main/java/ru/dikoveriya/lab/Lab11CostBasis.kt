package ru.dikoveriya.lab

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Recorded material cost and inherited production cost are separate from cash.
 * No mutation of original expense or previously sold cost snapshots.
 */
internal data class Lab11CostPart(
    val childCultureId:Long?,val usedUnits:Int,
    val kind:String="Разделение"
)

internal fun SQLiteDatabase.cultureCostBalance11(cultureId:Long):Long{
    val args=arrayOf(cultureId.toString())
    fun amount(sql:String):Long=rawQuery(sql,args).use{c->
        c.moveToFirst();c.getLong(0)
    }
    val direct=amount("""SELECT COALESCE(SUM(amount_minor),0) FROM direct_costs
        WHERE entity_kind='culture' AND entity_id=? AND reversed=0""")
    val incoming=amount("""SELECT COALESCE(SUM(amount_minor),0)
        FROM culture_cost_transfers WHERE child_culture_id=? AND reversed=0""")
    val outgoing=amount("""SELECT COALESCE(SUM(amount_minor),0)
        FROM culture_cost_transfers WHERE parent_culture_id=? AND reversed=0""")
    val remaining=direct+incoming-outgoing
    require(remaining>=0){
        "Нарушена стоимость незавершённого выращивания: контейнер #"+cultureId
    }
    return remaining
}

internal fun LabDatabase.cultureCostBasis11(id:Long):Long =
    readableDatabase.cultureCostBalance11(id)

/**
 * Allocate as exact integer kopecks using cumulative proportional rounding.
 * Consumed units are defined biologically by verified source operation.
 */
internal fun SQLiteDatabase.allocateCultureCost11(
    parentId:Long,parentQuantity:Int,
    parts:List<Lab11CostPart>,
    operationId:String?=null,saleId:Long?=null,
    at:Long=System.currentTimeMillis()
):Long{
    require(parentQuantity>0)
    require(parts.isNotEmpty() && parts.all{
        it.usedUnits>0 && it.kind in setOf("Разделение","Объединение","Потеря","Продажа")
    })
    val total=parts.sumOf{it.usedUnits.toLong()}
    require(total<=parentQuantity){"Нельзя распределить больше, чем имеется"}
    val balance=cultureCostBalance11(parentId)
    if(balance==0L)return 0
    var consumed=0L
    var previously=0L
    val source=BigDecimal.valueOf(balance)
    val denominator=BigDecimal.valueOf(parentQuantity.toLong())
    parts.forEach{part->
        consumed+=part.usedUnits
        val cumulative=source.multiply(BigDecimal.valueOf(consumed))
            .divide(denominator,0,RoundingMode.HALF_UP).longValueExact()
        val allocated=cumulative-previously
        require(allocated>=0)
        previously=cumulative
        if(allocated>0)insertOrThrow("culture_cost_transfers",null,ContentValues().apply{
            put("parent_culture_id",parentId)
            if(part.childCultureId!=null)put("child_culture_id",part.childCultureId)
            if(operationId!=null)put("operation_id",operationId)
            if(saleId!=null)put("sale_id",saleId)
            put("kind",part.kind);put("amount_minor",allocated)
            put("quantity_used",part.usedUnits);put("recorded_at",at)
        })
    }
    return previously
}

/** Profit cannot be declared known if no prior acquisition/expense is documented. */
internal fun SQLiteDatabase.hasCultureCostEvidence11(id:Long):Boolean =
    rawQuery("""SELECT
        (SELECT COUNT(*) FROM direct_costs WHERE entity_kind='culture' AND entity_id=? AND reversed=0)
        + (SELECT COUNT(*) FROM culture_cost_transfers WHERE child_culture_id=? AND reversed=0)""",
        arrayOf(id.toString(),id.toString())
    ).use{c->c.moveToFirst();c.getLong(0)>0}

internal data class Lab11LineEconomy(
    val plantId:Long,val plantName:String,val initialInvestment:Long,
    val directProductionCosts:Long,val aliveContainers:Int,
    val soldPlants:Int,val salesAmount:Long
)

/** A directional approximation, never silently updates past invoices/COGS. */
internal fun LabDatabase.lineEconomy11(plantId:Long):Lab11LineEconomy{
    val db=readableDatabase
    val name=db.rawQuery("SELECT name FROM plants WHERE id=?",
        arrayOf(plantId.toString())).use{c->
        require(c.moveToFirst()){"Сорт не найден"};c.getString(0)
    }
    val init=db.rawQuery("""SELECT COALESCE(SUM(purchase_cost_minor),0)
        FROM plant_items WHERE plant_id=? AND parent_item_id IS NULL
          AND parent_culture_id IS NULL""",arrayOf(plantId.toString()))
        .use{c->c.moveToFirst();c.getLong(0)}
    val direct=db.rawQuery("""SELECT COALESCE(SUM(d.amount_minor),0)
        FROM direct_costs d JOIN cultures c ON c.id=d.entity_id
        WHERE d.entity_kind='culture' AND c.plant_id=? AND d.reversed=0""",
        arrayOf(plantId.toString())).use{c->c.moveToFirst();c.getLong(0)}
    val active=db.rawQuery("""SELECT COUNT(*) FROM cultures
        WHERE plant_id=? AND status IN ('В культуре','На контроле','Карантин')""",
        arrayOf(plantId.toString())).use{c->c.moveToFirst();c.getInt(0)}
    val sales=db.rawQuery("""SELECT COALESCE(SUM(s.quantity),0),
        COALESCE(SUM(s.price),0) FROM sales s JOIN cultures c ON c.id=s.culture_id
        WHERE c.plant_id=?""",arrayOf(plantId.toString())).use{c->
        c.moveToFirst();c.getInt(0) to decimalMinor11(c.getDouble(1))
    }
    return Lab11LineEconomy(plantId,name,init,direct,active,sales.first,sales.second)
}
