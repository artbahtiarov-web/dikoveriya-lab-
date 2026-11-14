package ru.dikoveriya.lab

/**
 * One SQL-backed sales registry for plants and original in-vitro cultures.
 * No N+1 list of 500 sales; historical sale ID and object ID are preserved.
 */
internal data class Lab11SaleFilter(
    val search:String="",
    val kind:String="Все",
    val dateFrom:Long=0L,
    val dateTo:Long=Long.MAX_VALUE,
    val order:String="Сначала новые"
)
private const val SALES_UNION11="""
    SELECT 'culture' AS origin,s.id,c.code,'in vitro' AS kind,p.name AS plant,
        s.sold_at AS at,s.quantity AS qty,CAST(ROUND(s.price*100) AS INTEGER) AS amount,
        d.cogs_minor AS cogs,COALESCE(d.channel,'') AS channel,
        COALESCE(d.buyer,'') AS buyer,s.note,
        s.culture_id AS culture_id,NULL AS item_id
    FROM sales s
    JOIN cultures c ON c.id=s.culture_id JOIN plants p ON p.id=c.plant_id
    LEFT JOIN sale_details d ON d.sale_id=s.id
    UNION ALL
    SELECT 'item' AS origin,s.id,i.code,i.kind AS kind,p.name AS plant,
        s.sold_at AS at,1 AS qty,s.amount_minor AS amount,
        s.cost_minor AS cogs,s.channel,s.buyer,s.note,
        NULL AS culture_id,s.item_id AS item_id
    FROM item_sales s
    JOIN plant_items i ON i.id=s.item_id JOIN plants p ON p.id=i.plant_id
"""
private fun saleWhere11(f:Lab11SaleFilter):Pair<String,Array<String>>{
    require(f.dateFrom>=0L && f.dateTo>=f.dateFrom)
    val clauses=mutableListOf("at BETWEEN ? AND ?")
    val args=mutableListOf(f.dateFrom.toString(),f.dateTo.toString())
    if(f.kind!="Все"){
        require(f.kind in setOf("in vitro","Горшечное","Черенок","Маточник","Другое"))
        clauses+="kind=?";args+=f.kind
    }
    val query=f.search.trim().take(100)
    if(query.isNotBlank()){
        clauses+="(code LIKE ? OR plant LIKE ? OR channel LIKE ? OR buyer LIKE ?)"
        repeat(4){args+="%"+query+"%"}
    }
    return " WHERE "+clauses.joinToString(" AND ") to args.toTypedArray()
}
internal fun LabDatabase.salesCount11(filter:Lab11SaleFilter):Int{
    val (where,args)=saleWhere11(filter)
    return readableDatabase.rawQuery(
        "SELECT COUNT(*) FROM ("+SALES_UNION11+") t"+where,args
    ).use{c->c.moveToFirst();c.getInt(0)}
}
internal fun LabDatabase.salesPage11(filter:Lab11SaleFilter,limit:Int=40,offset:Int=0):List<Lab11Sale>{
    require(limit in 1..100 && offset>=0)
    val (where,args)=saleWhere11(filter)
    val order=when(filter.order){
        "Сначала новые"->"at DESC,id DESC"
        "Сначала старые"->"at ASC,id ASC"
        "Выручка ↓"->"amount DESC,at DESC"
        "Выручка ↑"->"amount ASC,at DESC"
        else->error("Неизвестный порядок")
    }
    return readableDatabase.rawQuery(
        "SELECT origin,id,code,kind,at,qty,amount,cogs,channel,buyer,note,culture_id,item_id "+
            "FROM ("+SALES_UNION11+") t"+where+
            " ORDER BY "+order+" LIMIT "+limit+" OFFSET "+offset,args
    ).use{c->buildList{
        while(c.moveToNext()){
            add(Lab11Sale(
                id=c.getLong(1),itemCode=c.getString(2),itemKind=c.getString(3),
                date=c.getLong(4),quantity=c.getInt(5),revenue=c.getLong(6),
                cogs=if(c.isNull(7))null else c.getLong(7),
                channel=c.getString(8),buyer=c.getString(9),note=c.getString(10),
                cultureId=if(c.isNull(11))null else c.getLong(11),
                plantItemId=if(c.isNull(12))null else c.getLong(12)))
        }
    }}
}
