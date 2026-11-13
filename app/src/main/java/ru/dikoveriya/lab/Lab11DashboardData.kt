package ru.dikoveriya.lab

internal data class Lab11RecentEvent(
    val eventId:Long,val objectId:Long,val objectType:String,
    val code:String,val kind:String,val date:Long,val note:String
)

/** One chronological source for dashboard links; no duplicate dashboard-only events. */
internal fun LabDatabase.recentEvents11(limit:Int=5):List<Lab11RecentEvent>{
    val max=limit.coerceIn(1,30)
    return readableDatabase.rawQuery("""
        SELECT e.id,c.id,'culture',c.code,e.kind,
            COALESCE(e.occurred_at,e.created_at),e.note
        FROM events e JOIN cultures c ON c.id=e.culture_id
        UNION ALL
        SELECT e.id,i.id,'item',i.code,e.kind,e.event_at,e.note
        FROM item_events e JOIN plant_items i ON i.id=e.item_id
        ORDER BY 6 DESC LIMIT """+max,null).use{c->buildList{
        while(c.moveToNext())add(Lab11RecentEvent(
            c.getLong(0),c.getLong(1),c.getString(2),
            c.getString(3),c.getString(4),c.getLong(5),c.getString(6)))
    }}
}
internal fun LabDatabase.activeItemCount11():Int =
    readableDatabase.rawQuery(
        "SELECT COUNT(*) FROM plant_items WHERE status='Активен'",null)
        .use{it.moveToFirst();it.getInt(0)}
