package ru.dikoveriya.lab

import android.content.ContentValues

internal data class Lab11GrowthSample(val id:Long,val at:Long,
    val heightMm:Double?,val leaves:Int?,val roots:Int?,val note:String)

internal fun LabDatabase.measureItem11(itemId:Long,at:Long,
    heightMm:Double?,leaves:Int?,roots:Int?,note:String):Long{
    require(at>0 && at<=System.currentTimeMillis()+86400000L)
    require(heightMm==null || heightMm.isFinite() && heightMm in 0.0..1000000.0)
    require(leaves==null || leaves in 0..1000000)
    require(roots==null || roots in 0..1000000)
    require(heightMm!=null || leaves!=null || roots!=null){"Укажи хотя бы одно измерение"}
    val db=writableDatabase
    db.beginTransaction()
    try{
        db.rawQuery("SELECT 1 FROM plant_items WHERE id=?",
            arrayOf(itemId.toString())).use{require(it.moveToFirst()){"Растение не найдено"}}
        val id=db.insertOrThrow("item_measurements",null,ContentValues().apply{
            put("item_id",itemId);put("observed_at",at)
            if(heightMm!=null)put("height_mm",heightMm)
            if(leaves!=null)put("leaves",leaves)
            if(roots!=null)put("roots",roots)
            put("note",note.take(2000))
        })
        db.insertOrThrow("item_events",null,ContentValues().apply{
            put("item_id",itemId);put("kind","Измерение")
            put("event_at",at);put("recorded_at",System.currentTimeMillis())
            put("note",note)
            put("details_json",org.json.JSONObject()
                .put("measurement_id",id).toString())
        })
        db.setTransactionSuccessful()
        return id
    }finally{db.endTransaction()}
}
internal fun LabDatabase.itemGrowth11(id:Long):List<Lab11GrowthSample> =
    readableDatabase.rawQuery("""SELECT id,observed_at,height_mm,leaves,roots,note
        FROM item_measurements WHERE item_id=? AND cancelled=0 ORDER BY observed_at,id LIMIT 1000""",
        arrayOf(id.toString())).use{c->buildList{
            while(c.moveToNext())add(Lab11GrowthSample(c.getLong(0),c.getLong(1),
                if(c.isNull(2))null else c.getDouble(2),
                if(c.isNull(3))null else c.getInt(3),
                if(c.isNull(4))null else c.getInt(4),c.getString(5)))
        }}
