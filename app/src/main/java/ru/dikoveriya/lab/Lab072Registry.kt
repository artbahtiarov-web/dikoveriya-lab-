package ru.dikoveriya.lab

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase

/** All large registries use indexed SQL filtering and 40-row pages, not in-memory lists. */
data class Lab072Counts(val tubes:Int,val attention:Int,val growing:Int,val tasksDue:Int)
data class Lab072Query(
    val mode:String="vitro",val text:String="",val plantId:Long?=null,
    val recipeId:Long?=null,val groupId:Long?=null,val locationId:Long?=null,
    val stage:String="",val status:String="",val fromDate:Long?=null,
    val toDate:Long?=null,val lastObservedFrom:Long?=null,val order:String="Новые"
)
data class Lab072Culture(
    val id:Long,val code:String,val plantId:Long,val plant:String,
    val quantity:Int,val kind:String,val stage:String,val status:String,
    val location:String,val created:Long,val groupCode:String,val groupTitle:String,
    val recipe:String,val latestObserved:Long?,
    val attentionReason:String?,val severity:String?,val checkDue:Long?,
    val lastShoots:Int?,val lastPlants:Int?
)
data class Lab072Group(val id:Long,val code:String,val title:String,
    val plant:String,val plantId:Long,val memberCount:Int,val created:Long)
data class Lab072Location(val id:Long,val parentId:Long?,val level:String,
    val name:String,val path:String)
data class Lab072Task(val id:Long,val title:String,val cultureId:Long?,
    val dueAt:Long,val done:Boolean,val priority:String,val kind:String,
    val plant:String?,val cultureCode:String?,val recipe:String?,val groupId:Long?)
data class Lab072Case(val id:Long,val cultureId:Long,val reason:String,
    val severity:String,val dueAt:Long?,val action:String,val created:Long,
    val closedAt:Long?,val resolution:String)

internal fun lab072CV(vararg fields:Pair<String,Any?>):ContentValues=ContentValues().apply{
    fields.forEach{(key,value)->when(value){
        null->putNull(key)
        is String->put(key,value)
        is Int->put(key,value)
        is Long->put(key,value)
        is Double->put(key,value)
        else->error("Недопустимый тип поля: $key")
    }}
}
internal fun Cursor.lab072Long(i:Int):Long?=if(isNull(i))null else getLong(i)
internal fun Cursor.lab072Int(i:Int):Int?=if(isNull(i))null else getInt(i)
internal fun Cursor.lab072String(i:Int):String?=if(isNull(i))null else getString(i)
internal fun lab072Text(value:String,max:Int=240)=value.trim().also{
    require(it.isNotEmpty() && it.length<=max){"Заполни текст (не более $max символов)"}
}
internal fun <T> LabDatabase.lab072Tx(block:SQLiteDatabase.()->T):T{
    val db=writableDatabase
    db.beginTransaction()
    try{val result=db.block();db.setTransactionSuccessful();return result}
    finally{db.endTransaction()}
}

fun LabDatabase.dashboard072(now:Long=System.currentTimeMillis()):Lab072Counts{
    val db=readableDatabase
    fun count(sql:String,args:Array<String> = emptyArray())=db.rawQuery(sql,args).use{
        if(it.moveToFirst())it.getInt(0)else 0
    }
    val active="'В культуре','На контроле','Карантин'"
    return Lab072Counts(
        count("SELECT COUNT(*) FROM cultures WHERE stage='В пробирке' AND status IN ($active)"),
        count("""SELECT COUNT(*) FROM cultures c WHERE c.status IN ($active)
            AND (c.status IN ('На контроле','Карантин') OR EXISTS(
              SELECT 1 FROM control_cases k WHERE k.culture_id=c.id AND k.closed_at IS NULL))"""),
        count("SELECT COUNT(*) FROM cultures WHERE stage IN ('Адаптация','Доращивание') AND status IN ($active)"),
        count("SELECT COUNT(*) FROM tasks WHERE done=0 AND due_at<=?",arrayOf(now.toString()))
    )
}

private const val JOINS072=""" FROM cultures c JOIN plants p ON p.id=c.plant_id
    LEFT JOIN culture_groups cg ON cg.culture_id=c.id
    LEFT JOIN production_groups g ON g.id=cg.group_id
    LEFT JOIN culture_storage cs ON cs.culture_id=c.id
    LEFT JOIN storage_locations l ON l.id=cs.location_id """

private fun filter072(f:Lab072Query):Pair<String,Array<String>>{
    val where=mutableListOf<String>()
    val args=mutableListOf<String>()
    val active="('В культуре','На контроле','Карантин')"
    when(f.mode){
        "vitro"->where+="c.stage='В пробирке' AND c.status IN $active"
        "attention"->where+="""c.status IN $active AND (
            c.status IN ('На контроле','Карантин') OR EXISTS(
            SELECT 1 FROM control_cases k WHERE k.culture_id=c.id AND k.closed_at IS NULL))"""
        "growth"->where+="c.stage IN ('Адаптация','Доращивание') AND c.status IN $active"
        "all"->Unit
        else->error("Недопустимый раздел")
    }
    if(f.text.isNotBlank()){
        where+="(c.code LIKE ? OR p.name LIKE ? OR c.location LIKE ? OR g.title LIKE ? OR g.code LIKE ? OR l.full_path LIKE ?)"
        repeat(6){args+="%"+f.text.trim().take(100)+"%"}
    }
    if(f.plantId!=null){where+="c.plant_id=?";args+=f.plantId.toString()}
    if(f.recipeId!=null){where+="c.medium_recipe_id=?";args+=f.recipeId.toString()}
    if(f.groupId!=null){where+="cg.group_id=?";args+=f.groupId.toString()}
    if(f.locationId!=null){where+="cs.location_id=?";args+=f.locationId.toString()}
    if(f.stage.isNotBlank()){where+="c.stage=?";args+=f.stage}
    if(f.status.isNotBlank()){where+="c.status=?";args+=f.status}
    if(f.fromDate!=null){where+="c.created_at>=?";args+=f.fromDate.toString()}
    if(f.toDate!=null){where+="c.created_at<=?";args+=f.toDate.toString()}
    if(f.lastObservedFrom!=null){
        where+="EXISTS (SELECT 1 FROM culture_measurements m WHERE m.culture_id=c.id AND m.record_kind='observation' AND m.observed_at>=?)"
        args+=f.lastObservedFrom.toString()
    }
    return (if(where.isEmpty())"" else " WHERE "+where.joinToString(" AND ")) to args.toTypedArray()
}
fun LabDatabase.countCultures072(f:Lab072Query):Int{
    val (where,args)=filter072(f)
    return readableDatabase.rawQuery("SELECT COUNT(*)"+JOINS072+where,args).use{
        if(it.moveToFirst())it.getInt(0)else 0
    }
}
fun LabDatabase.culturesPage072(f:Lab072Query,offset:Int,limit:Int=40):List<Lab072Culture>{
    require(offset>=0 && limit in 1..100)
    val (where,args)=filter072(f)
    val order=when(f.order){
        "Старые"->"c.created_at ASC,c.id ASC"
        "Название"->"p.name COLLATE NOCASE ASC,c.id DESC"
        "Место"->"COALESCE(l.full_path,c.location) COLLATE NOCASE ASC,c.id DESC"
        "По побегам"->"last_shoots DESC,c.id DESC"
        "По растениям"->"last_plants DESC,c.id DESC"
        "Последний осмотр"->"last_observed DESC,c.id DESC"
        else->"c.created_at DESC,c.id DESC"
    }
    val sql="""SELECT c.id,c.code,c.plant_id,p.name,c.quantity,c.quantity_kind,c.stage,c.status,
         COALESCE(l.full_path,c.location),c.created_at,
         COALESCE(g.code,''),COALESCE(g.title,''),
         COALESCE(r.name || ' · v' || r.version,''),
         (SELECT MAX(m.observed_at) FROM culture_measurements m
            WHERE m.culture_id=c.id AND m.record_kind='observation') AS last_observed,
         (SELECT k.reason FROM control_cases k WHERE k.culture_id=c.id AND k.closed_at IS NULL
            ORDER BY k.opened_at DESC,k.id DESC LIMIT 1),
         (SELECT k.severity FROM control_cases k WHERE k.culture_id=c.id AND k.closed_at IS NULL
            ORDER BY k.opened_at DESC,k.id DESC LIMIT 1),
         (SELECT k.due_at FROM control_cases k WHERE k.culture_id=c.id AND k.closed_at IS NULL
            ORDER BY k.opened_at DESC,k.id DESC LIMIT 1),
         (SELECT m.shoots FROM culture_measurements m WHERE m.culture_id=c.id
            AND m.record_kind='observation' AND m.shoots IS NOT NULL
            ORDER BY m.observed_at DESC,m.id DESC LIMIT 1) AS last_shoots,
         (SELECT m.plants FROM culture_measurements m WHERE m.culture_id=c.id
            AND m.record_kind='observation' AND m.plants IS NOT NULL
            ORDER BY m.observed_at DESC,m.id DESC LIMIT 1) AS last_plants
         $JOINS072 LEFT JOIN recipes r ON r.id=c.medium_recipe_id
         $where ORDER BY $order LIMIT $limit OFFSET $offset"""
    return readableDatabase.rawQuery(sql,args).use{c->buildList{
        while(c.moveToNext())add(Lab072Culture(
            c.getLong(0),c.getString(1),c.getLong(2),c.getString(3),
            c.getInt(4),c.getString(5),c.getString(6),c.getString(7),
            c.getString(8),c.getLong(9),c.getString(10),c.getString(11),
            c.getString(12),c.lab072Long(13),c.lab072String(14),
            c.lab072String(15),c.lab072Long(16),c.lab072Int(17),c.lab072Int(18)
        ))
    }}
}
fun LabDatabase.findCulture072(code:String):Long?=readableDatabase.rawQuery(
    "SELECT id FROM cultures WHERE code=? LIMIT 1",arrayOf(code)).use{
    if(it.moveToFirst())it.getLong(0)else null
}
fun LabDatabase.groups072():List<Lab072Group>=readableDatabase.rawQuery(
    """SELECT g.id,g.code,g.title,p.name,g.plant_id,COUNT(cg.culture_id),g.created_at
    FROM production_groups g JOIN plants p ON p.id=g.plant_id
    LEFT JOIN culture_groups cg ON cg.group_id=g.id
    GROUP BY g.id ORDER BY g.id DESC""",null).use{c->buildList{
    while(c.moveToNext())add(Lab072Group(c.getLong(0),c.getString(1),
        c.getString(2),c.getString(3),c.getLong(4),c.getInt(5),c.getLong(6)))
}}
fun LabDatabase.locations072():List<Lab072Location>=readableDatabase.rawQuery(
    "SELECT id,parent_id,level,label,full_path FROM storage_locations ORDER BY full_path COLLATE NOCASE",
    null).use{c->buildList{
    while(c.moveToNext())add(Lab072Location(c.getLong(0),c.lab072Long(1),
        c.getString(2),c.getString(3),c.getString(4)))
}}

/** One grouped SQL count replaces the quadratic per-plant full-list scan. */
fun LabDatabase.plantCounts072():Map<Long,Int> =
    readableDatabase.rawQuery(
        "SELECT plant_id,COUNT(*) FROM cultures GROUP BY plant_id",null
    ).use{c->buildMap{while(c.moveToNext())put(c.getLong(0),c.getInt(1))}}

fun LabDatabase.cultureCount072():Int =
    readableDatabase.rawQuery("SELECT COUNT(*) FROM cultures",null).use{
        if(it.moveToFirst())it.getInt(0)else 0
    }
