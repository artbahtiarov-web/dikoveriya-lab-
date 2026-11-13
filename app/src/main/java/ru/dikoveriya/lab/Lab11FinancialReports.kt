package ru.dikoveriya.lab

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

internal data class Lab11MonthlyMoney(
    val month:String,val received:Long,val paid:Long
)
internal data class Lab11FinancialCategory(
    val name:String,val amount:Long,val transactions:Int
)
internal data class Lab11FinancialPerformance(
    val salesRevenue:Long,val verifiedCostOfSales:Long,
    val generalExpenses:Long,val missingCostSales:Int,
    val financialResult:Long?
)
internal data class Lab11VarietyEconomics(
    val plantId:Long,val name:String,
    val revenue:Long,val soldUnits:Int,val costOfSales:Long?,
    val salesWithoutCost:Int
)
internal fun LabDatabase.monthlyCash11(months:Int=12):List<Lab11MonthlyMoney>{
    require(months in 1..120)
    val first=YearMonth.now().minusMonths(months.toLong()-1)
        .atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val data=mutableMapOf<String,Pair<Long,Long>>()
    readableDatabase.rawQuery("""SELECT strftime('%Y-%m',occurred_at/1000,'unixepoch','localtime'),
        COALESCE(SUM(CASE WHEN direction='Доход' THEN amount_minor ELSE 0 END),0),
        COALESCE(SUM(CASE WHEN direction='Расход' THEN amount_minor ELSE 0 END),0)
        FROM cash_ledger WHERE occurred_at>=?
        GROUP BY strftime('%Y-%m',occurred_at/1000,'unixepoch','localtime')
        ORDER BY 1""",arrayOf(first.toString())).use{c->
        while(c.moveToNext())data[c.getString(0)]=(c.getLong(1) to c.getLong(2))
    }
    return (0 until months).map{
        val period=firstYearMonth11(months-1-it)
        val value=data[period.toString()]
        Lab11MonthlyMoney(period.toString(),value?.first?:0L,value?.second?:0L)
    }
}
private fun firstYearMonth11(monthsBack:Int):YearMonth=
    YearMonth.now().minusMonths(monthsBack.toLong())

internal fun LabDatabase.expenseCategories11(
    from:Long=0,to:Long=Long.MAX_VALUE,
    income:Boolean=false
):List<Lab11FinancialCategory>{
    val direction=if(income)"Доход" else "Расход"
    return readableDatabase.rawQuery("""SELECT category,
        COALESCE(SUM(amount_minor),0),COUNT(*)
        FROM cash_ledger WHERE direction=? AND occurred_at BETWEEN ? AND ?
        GROUP BY category ORDER BY SUM(amount_minor) DESC,category""",
        arrayOf(direction,from.toString(),to.toString())).use{c->buildList{
        while(c.moveToNext())add(Lab11FinancialCategory(
            c.getString(0),c.getLong(1),c.getInt(2)))
    }}
}

/** Business result: only known sales COGS and actual general expense payments.
 * Capital purchases and warehouse acquisitions are excluded from profit.
 * Unknown cost snapshots prohibit an invented profit number.
 */
internal fun LabDatabase.performance11(from:Long=0,to:Long=Long.MAX_VALUE):Lab11FinancialPerformance{
    val db=readableDatabase
    val args=arrayOf(from.toString(),to.toString())
    fun sum(sql:String):Long=db.rawQuery(sql,args).use{c->
        c.moveToFirst();c.getLong(0)
    }
    val revenue=sum("""SELECT COALESCE(SUM(amount_minor),0)
        FROM cash_ledger WHERE direction='Доход' AND category='Продажа'
          AND occurred_at BETWEEN ? AND ?""")
    val general=sum("""SELECT COALESCE(SUM(amount_minor),0)
        FROM cash_ledger WHERE direction='Расход'
          AND source_type='general_expense' AND occurred_at BETWEEN ? AND ?""")
    val existing=sum("""SELECT COALESCE(SUM(cost_minor),0) FROM item_sales
        WHERE sold_at BETWEEN ? AND ?""")
    val vitro=sum("""SELECT COALESCE(SUM(d.cogs_minor),0)
        FROM sales s JOIN sale_details d ON d.sale_id=s.id
        WHERE s.sold_at BETWEEN ? AND ?""")
    val missing=sum("""SELECT COUNT(*) FROM sales s
        LEFT JOIN sale_details d ON d.sale_id=s.id
        WHERE d.cogs_minor IS NULL AND s.sold_at BETWEEN ? AND ?""").toInt()
    val costs=existing+vitro
    return Lab11FinancialPerformance(revenue,costs,general,missing,
        if(missing>0)null else revenue-costs-general)
}

internal fun LabDatabase.varietyEconomics11(
    from:Long=0,to:Long=Long.MAX_VALUE
):List<Lab11VarietyEconomics>{
    val args=arrayOf(from.toString(),to.toString())
    val aggregates=linkedMapOf<Long,Lab11VarietyEconomics>()
    readableDatabase.rawQuery("""SELECT p.id,p.name,
        COALESCE(SUM(s.amount_minor),0),COUNT(s.id),
        COALESCE(SUM(s.cost_minor),0),
        SUM(CASE WHEN s.cost_minor IS NULL THEN 1 ELSE 0 END)
        FROM item_sales s JOIN plant_items i ON i.id=s.item_id
        JOIN plants p ON p.id=i.plant_id WHERE s.sold_at BETWEEN ? AND ?
        GROUP BY p.id,p.name""",args).use{c->
        while(c.moveToNext()){
            val id=c.getLong(0);val missing=c.getInt(5)
            aggregates[id]=Lab11VarietyEconomics(id,c.getString(1),c.getLong(2),
                c.getInt(3),if(missing>0)null else c.getLong(4),missing)
        }
    }
    readableDatabase.rawQuery("""SELECT p.id,p.name,
        COALESCE(SUM(s.price),0),COALESCE(SUM(s.quantity),0),
        COALESCE(SUM(d.cogs_minor),0),
        SUM(CASE WHEN d.cogs_minor IS NULL THEN 1 ELSE 0 END)
        FROM sales s JOIN cultures c ON c.id=s.culture_id
        JOIN plants p ON p.id=c.plant_id
        LEFT JOIN sale_details d ON d.sale_id=s.id
        WHERE s.sold_at BETWEEN ? AND ? GROUP BY p.id,p.name""",args)
        .use{c->while(c.moveToNext()){
            val id=c.getLong(0)
            val name=c.getString(1)
            val revenue=decimalMinor11(c.getDouble(2))
            val sold=c.getInt(3);val unknown=c.getInt(5)
            val previous=aggregates[id]
            val missing=unknown+(previous?.salesWithoutCost?:0)
            val known=c.getLong(4)+(previous?.costOfSales?:0)
            aggregates[id]=Lab11VarietyEconomics(id,name,revenue+(previous?.revenue?:0L),
                sold+(previous?.soldUnits?:0),if(missing>0)null else known,missing)
        }}
    return aggregates.values.sortedByDescending{it.revenue}
}
