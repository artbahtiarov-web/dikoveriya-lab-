package ru.dikoveriya.lab

import org.json.JSONObject

data class Lab072SavedView(val id:Long,val name:String,val config:String)

fun LabDatabase.savedViews072(screen:String):List<Lab072SavedView> =
    readableDatabase.rawQuery(
        "SELECT id,name,config_json FROM saved_views WHERE screen=? ORDER BY name COLLATE NOCASE",
        arrayOf(screen)).use{cursor->buildList{
        while(cursor.moveToNext())add(Lab072SavedView(
            cursor.getLong(0),cursor.getString(1),cursor.getString(2)))
    }}
fun LabDatabase.saveView072(screen:String,name:String,config:String){
    require(screen in listOf("vitro","attention","growth","all","tasks")){"Неизвестный реестр"}
    val title=lab072Text(name,50)
    require(config.length<=8000){"Фильтр слишком большой"}
    JSONObject(config) // validate JSON before writing
    lab072Tx{
        insertWithOnConflict("saved_views",null,lab072CV(
            "screen" to screen,"name" to title,"config_json" to config,
            "created_at" to System.currentTimeMillis()),android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
    }
}
fun Lab072Query.json():String=JSONObject().apply{
    put("text",text);put("plantId",plantId);put("recipeId",recipeId)
    put("groupId",groupId);put("locationId",locationId)
    put("stage",stage);put("status",status)
    put("fromDate",fromDate);put("toDate",toDate)
    put("lastObservedFrom",lastObservedFrom);put("order",order)
}.toString()
fun Lab072Query.withSaved(json:String):Lab072Query{
    val v=JSONObject(json)
    fun long(key:String):Long?=if(v.isNull(key))null else v.optLong(key).takeIf{it>0}
    return copy(text=v.optString("text","").take(100),
        plantId=long("plantId"),recipeId=long("recipeId"),
        groupId=long("groupId"),locationId=long("locationId"),
        stage=v.optString("stage",""),status=v.optString("status",""),
        fromDate=long("fromDate"),toDate=long("toDate"),
        lastObservedFrom=long("lastObservedFrom"),
        order=v.optString("order","Новые"))
}
fun Lab072TaskFilter.json():String=JSONObject().apply{
    put("period",period);put("priority",priority);put("kind",kind)
    put("plantId",plantId);put("recipeId",recipeId)
    put("dueFrom",dueFrom);put("dueTo",dueTo)
    put("text",text);put("order",order)
}.toString()
fun Lab072TaskFilter.withSaved(json:String):Lab072TaskFilter{
    val v=JSONObject(json)
    fun long(key:String):Long?=if(v.isNull(key))null else v.optLong(key).takeIf{it>0}
    return copy(period=v.optString("period","Все"),priority=v.optString("priority",""),
        kind=v.optString("kind",""),plantId=long("plantId"),recipeId=long("recipeId"),
        dueFrom=long("dueFrom"),dueTo=long("dueTo"),
        text=v.optString("text","").take(100),order=v.optString("order","Ближайшие"))
}
