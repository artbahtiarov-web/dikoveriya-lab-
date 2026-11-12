package ru.dikoveriya.lab

import android.database.sqlite.SQLiteDatabase
import java.util.UUID

/** Every bulk update is one SQLite transaction and retains a per-container event. */
private fun checkedIds072(ids:List<Long>){
    require(ids.isNotEmpty() && ids.size<=200 && ids.distinct().size==ids.size){
        "Выбери от 1 до 200 разных контейнеров"
    }
}
private fun SQLiteDatabase.operation072(kind:String,note:String,now:Long):String{
    val id=UUID.randomUUID().toString()
    insertOrThrow("lab_operations",null,lab072CV("id" to id,"kind" to kind,
        "note" to note,"occurred_at" to now,"recorded_at" to now,
        "status" to "Проведена"))
    return id
}
private fun SQLiteDatabase.event072(cultureId:Long,kind:String,note:String,
    operation:String,now:Long){
    insertOrThrow("events",null,lab072CV("culture_id" to cultureId,"kind" to kind,
        "note" to note,"created_at" to now,"occurred_at" to now,
        "event_group" to operation))
}
private data class Row072(val id:Long,val qty:Int,val status:String,
    val location:String,val linkedLocationId:Long?)
private fun SQLiteDatabase.row072(id:Long):Row072=rawQuery(
    """SELECT c.id,c.quantity,c.status,c.location,cs.location_id
    FROM cultures c LEFT JOIN culture_storage cs ON cs.culture_id=c.id WHERE c.id=?""",
    arrayOf(id.toString())).use{c->
    require(c.moveToFirst()){"Контейнер не найден"}
    Row072(c.getLong(0),c.getInt(1),c.getString(2),c.getString(3),c.lab072Long(4))
}
private fun SQLiteDatabase.auditTop072(cultureId:Long):Long=rawQuery(
    "SELECT COALESCE(MAX(id),0) FROM culture_audit WHERE culture_id=?",
    arrayOf(cultureId.toString())).use{c->c.moveToFirst();c.getLong(0)}
private fun SQLiteDatabase.change072(op:String,row:Row072,afterStatus:String,
    minAuditId:Long){
    insertOrThrow("operation_changes",null,lab072CV(
        "operation_id" to op,"culture_id" to row.id,
        "before_qty" to row.qty,"after_qty" to row.qty,
        "before_status" to row.status,"after_status" to afterStatus))
    execSQL("""UPDATE culture_audit SET operation_id=?
        WHERE culture_id=? AND id>? AND operation_id IS NULL
        AND source='Изменение'""",arrayOf(op,row.id,minAuditId))
}

fun LabDatabase.createGroup072(ids:List<Long>,title:String,reason:String):Long=lab072Tx{
    checkedIds072(ids)
    val name=lab072Text(title,100)
    val note=lab072Text(reason)
    val placeholders=ids.joinToString(","){"?"}
    val found=rawQuery("SELECT id,plant_id,mother_id FROM cultures WHERE id IN ($placeholders)",
        ids.map{it.toString()}.toTypedArray()).use{c->buildList{
        while(c.moveToNext())add(Triple(c.getLong(0),c.getLong(1),c.lab072Long(2)))
    }}
    require(found.size==ids.size){"Некоторые контейнеры не найдены"}
    require(found.map{it.second}.distinct().size==1){"Можно объединять только один сорт"}
    require(found.map{it.third}.distinct().size==1){
        "Контейнеры имеют разные или неизвестные маточники. Уточни происхождение отдельно"
    }
    require(rawQuery("SELECT COUNT(*) FROM culture_groups WHERE culture_id IN ($placeholders)",
        ids.map{it.toString()}.toTypedArray()).use{it.moveToFirst();it.getInt(0)}==0){
        "Некоторые контейнеры уже состоят в группе"
    }
    val now=System.currentTimeMillis()
    val groupId=insertOrThrow("production_groups",null,lab072CV(
        "code" to "TEMP-"+UUID.randomUUID().toString(),
        "title" to name,"plant_id" to found.first().second,
        "mother_id" to found.first().third,"note" to note,"created_at" to now))
    update("production_groups",lab072CV("code" to "ГР-%06d".format(groupId)),
        "id=?",arrayOf(groupId.toString()))
    ids.forEach{id->
        insertOrThrow("culture_groups",null,lab072CV(
            "culture_id" to id,"group_id" to groupId,"linked_at" to now))
        insertOrThrow("group_history",null,lab072CV(
            "culture_id" to id,"previous_group_id" to null,
            "new_group_id" to groupId,"recorded_at" to now,"reason" to note))
        insertOrThrow("events",null,lab072CV("culture_id" to id,
            "kind" to "Группа контейнеров","note" to "В группе $name: $note",
            "created_at" to now,"occurred_at" to now))
    }
    groupId
}

fun LabDatabase.createLocation072(parentId:Long?,level:String,label:String):Long=lab072Tx{
    val levels=listOf("Помещение","Стеллаж","Полка","Позиция")
    val index=levels.indexOf(level)
    require(index>=0){"Неизвестный уровень хранения"}
    val name=lab072Text(label,80)
    val parent=parentId?.let{id->
        rawQuery("SELECT level,full_path FROM storage_locations WHERE id=?",
            arrayOf(id.toString())).use{c->
            require(c.moveToFirst()){"Родительский адрес не найден"}
            c.getString(0) to c.getString(1)
        }
    }
    require((index==0 && parent==null) || (index>0 && parent!=null &&
        levels.indexOf(parent.first)==index-1)){
        "Адрес: Помещение → Стеллаж → Полка → Позиция"
    }
    val path=(parent?.second?.plus(" / ") ?: "")+name
    require(rawQuery("SELECT COUNT(*) FROM storage_locations WHERE full_path=?",
        arrayOf(path)).use{it.moveToFirst();it.getInt(0)}==0){"Такой адрес уже существует"}
    insertOrThrow("storage_locations",null,lab072CV(
        "parent_id" to parentId,"level" to level,"label" to name,
        "full_path" to path,"created_at" to System.currentTimeMillis()))
}

fun LabDatabase.moveContainers072(ids:List<Long>,targetId:Long,reason:String):Unit=lab072Tx{
    checkedIds072(ids)
    val note=lab072Text(reason)
    val path=rawQuery("SELECT full_path FROM storage_locations WHERE id=?",
        arrayOf(targetId.toString())).use{c->
        require(c.moveToFirst()){"Адрес не найден"};c.getString(0)
    }
    val now=System.currentTimeMillis()
    val op=operation072("Перемещение","Место: $path · $note",now)
    ids.forEach{id->
        val old=row072(id)
        val beforeAudit=auditTop072(id)
        update("cultures",lab072CV("location" to path,"updated_at" to now),
            "id=?",arrayOf(id.toString()))
        insertWithOnConflict("culture_storage",null,lab072CV(
            "culture_id" to id,"location_id" to targetId,"assigned_at" to now),
            SQLiteDatabase.CONFLICT_REPLACE)
        insertOrThrow("storage_movements",null,lab072CV("culture_id" to id,
            "from_location_id" to old.linkedLocationId,
            "to_location_id" to targetId,"previous_text" to old.location,
            "changed_at" to now,"reason" to note,"operation_id" to op))
        change072(op,old,old.status,beforeAudit)
        event072(id,"Перемещение",old.location+" → "+path+" · "+note,op,now)
    }
}
private val active072=setOf(LabTerms.IN_CULTURE,LabTerms.CONTROL,LabTerms.QUARANTINE)
private val severity072=setOf("Обычный","Важный","Критический")
private val priority072=setOf("Низкий","Обычный","Высокий","Срочный")

fun LabDatabase.flagControl072(ids:List<Long>,reason:String,severity:String,
    dueAt:Long?,action:String,taskPriority:String):Unit=lab072Tx{
    checkedIds072(ids)
    val note=lab072Text(reason)
    val required=lab072Text(action)
    require(severity in severity072){"Выбери степень важности"}
    require(taskPriority in priority072){"Выбери приоритет задачи"}
    if(dueAt!=null)require(dueAt>0)
    val now=System.currentTimeMillis()
    val op=operation072("Назначен контроль",note,now)
    ids.forEach{id->
        val old=row072(id)
        require(old.status in active072){"Закрытому контейнеру нельзя назначать контроль"}
        val current=if(old.status==LabTerms.IN_CULTURE)LabTerms.CONTROL else old.status
        val beforeAudit=auditTop072(id)
        update("cultures",lab072CV("status" to current,"updated_at" to now),
            "id=?",arrayOf(id.toString()))
        val caseId=insertOrThrow("control_cases",null,lab072CV(
            "culture_id" to id,"reason" to note,"severity" to severity,
            "due_at" to dueAt,"required_action" to required,"opened_at" to now))
        insertOrThrow("control_case_history",null,lab072CV(
            "case_id" to caseId,"recorded_at" to now,
            "action" to "Создан","note" to note))
        change072(op,old,current,beforeAudit)
        event072(id,"Контроль",note+" · действие: "+required,op,now)
        if(dueAt!=null){
            val taskId=insertOrThrow("tasks",null,lab072CV(
                "culture_id" to id,"title" to required,"due_at" to dueAt,"done" to 0))
            insertOrThrow("task_metadata",null,lab072CV(
                "task_id" to taskId,"priority" to taskPriority,
                "kind" to "Осмотр","updated_at" to now))
            insertOrThrow("task_history",null,lab072CV(
                "task_id" to taskId,"recorded_at" to now,"action" to "Создана",
                "next_due_at" to dueAt,"next_done" to 0,
                "next_priority" to taskPriority,"note" to "Контроль контейнера"))
        }
    }
}

fun LabDatabase.cases072(cultureId:Long):List<Lab072Case> =
    readableDatabase.rawQuery("""SELECT id,culture_id,reason,severity,due_at,
        required_action,opened_at,closed_at,resolution FROM control_cases
        WHERE culture_id=? ORDER BY closed_at IS NOT NULL,opened_at DESC,id DESC""",
        arrayOf(cultureId.toString())).use{c->buildList{
        while(c.moveToNext())add(Lab072Case(c.getLong(0),c.getLong(1),
            c.getString(2),c.getString(3),c.lab072Long(4),c.getString(5),
            c.getLong(6),c.lab072Long(7),c.getString(8)))
    }}

fun LabDatabase.resolveCase072(caseId:Long,note:String)=lab072Tx{
    val text=lab072Text(note)
    val row=rawQuery("SELECT culture_id,closed_at FROM control_cases WHERE id=?",
        arrayOf(caseId.toString())).use{c->
        require(c.moveToFirst()){"Причина контроля не найдена"}
        c.getLong(0) to c.lab072Long(1)
    }
    require(row.second==null){"Контроль уже закрыт"}
    val now=System.currentTimeMillis()
    update("control_cases",lab072CV("closed_at" to now,"resolution" to text),
        "id=?",arrayOf(caseId.toString()))
    insertOrThrow("control_case_history",null,lab072CV(
        "case_id" to caseId,"recorded_at" to now,"action" to "Закрыт","note" to text))
    val op=operation072("Результат контроля",text,now)
    event072(row.first,"Результат контроля",text,op,now)
    // Do not silently revert quarantine/control status; that is a separate passport action.
}

data class Lab072TaskFilter(
    val period:String="Все",val priority:String="",val kind:String="",
    val plantId:Long?=null,val recipeId:Long?=null,
    val dueFrom:Long?=null,val dueTo:Long?=null,
    val text:String="",val order:String="Ближайшие"
)
fun LabDatabase.tasksPage072(f:Lab072TaskFilter,offset:Int,limit:Int=40,
    now:Long=System.currentTimeMillis()):List<Lab072Task>{
    require(offset>=0 && limit in 1..100)
    val w=mutableListOf<String>()
    val args=mutableListOf<String>()
    when(f.period){
        "Просрочено"->{w+="t.done=0 AND t.due_at<?";args+=now.toString()}
        "Ближайшие 7 дней"->{w+="t.done=0 AND t.due_at>=? AND t.due_at<=?"
            args+=now.toString();args+=(now+7*86400000L).toString()}
        "Выполнено"->w+="t.done=1"
        "Открытые"->w+="t.done=0"
        else->Unit
    }
    if(f.priority.isNotBlank()){w+="COALESCE(m.priority,'Не задан')=?";args+=f.priority}
    if(f.kind.isNotBlank()){w+="COALESCE(m.kind,'Другое')=?";args+=f.kind}
    if(f.plantId!=null){w+="c.plant_id=?";args+=f.plantId.toString()}
    if(f.recipeId!=null){w+="COALESCE(m.recipe_id,c.medium_recipe_id)=?";args+=f.recipeId.toString()}
    if(f.dueFrom!=null){w+="t.due_at>=?";args+=f.dueFrom.toString()}
    if(f.dueTo!=null){w+="t.due_at<=?";args+=f.dueTo.toString()}
    if(f.text.isNotBlank()){
        w+="(t.title LIKE ? OR c.code LIKE ? OR p.name LIKE ?)"
        repeat(3){args+="%"+f.text.trim().take(100)+"%"}
    }
    val where=if(w.isEmpty())"" else " WHERE "+w.joinToString(" AND ")
    val sort=when(f.order){
        "Важные"->"""CASE COALESCE(m.priority,'Не задан')
            WHEN 'Срочный' THEN 0 WHEN 'Высокий' THEN 1 WHEN 'Обычный' THEN 2
            WHEN 'Низкий' THEN 3 ELSE 4 END,t.due_at,t.id"""
        "Поздние"->"t.due_at DESC,t.id DESC"
        else->"t.done,t.due_at,t.id"
    }
    return readableDatabase.rawQuery("""SELECT t.id,t.title,t.culture_id,t.due_at,t.done,
        COALESCE(m.priority,'Не задан'),COALESCE(m.kind,'Другое'),
        p.name,c.code,COALESCE(r.name || ' · v' || r.version,''),m.group_id
        FROM tasks t LEFT JOIN task_metadata m ON m.task_id=t.id
        LEFT JOIN cultures c ON c.id=t.culture_id
        LEFT JOIN plants p ON p.id=c.plant_id
        LEFT JOIN recipes r ON r.id=COALESCE(m.recipe_id,c.medium_recipe_id)
        $where ORDER BY $sort LIMIT $limit OFFSET $offset""",
        args.toTypedArray()).use{c->buildList{
        while(c.moveToNext())add(Lab072Task(c.getLong(0),c.getString(1),
            c.lab072Long(2),c.getLong(3),c.getInt(4)==1,
            c.getString(5),c.getString(6),c.lab072String(7),c.lab072String(8),
            c.lab072String(9),c.lab072Long(10)))
    }}
}
fun LabDatabase.taskCount072(f:Lab072TaskFilter,now:Long=System.currentTimeMillis()):Int{
    // For exact counts, keep a dedicated SQL COUNT rather than loading every task.
    val active=when(f.period){"Просрочено"->"done=0 AND due_at<$now";else->""}
    // Count uses the exact same query contract: batches are bounded and never cached.
    var offset=0
    var count=0
    while(true){
        val page=tasksPage072(f,offset,100,now)
        count+=page.size
        if(page.size<100)break
        offset+=100
    }
    return count
}

fun LabDatabase.createTask072(title:String,dueAt:Long,priority:String,kind:String,
    cultureId:Long?,recipeId:Long?,groupId:Long?)=lab072Tx{
    require(priority in priority072){"Укажи важность"}
    require(dueAt>0){"Укажи дату выполнения"}
    val now=System.currentTimeMillis()
    val id=insertOrThrow("tasks",null,lab072CV(
        "title" to lab072Text(title,200),"due_at" to dueAt,
        "culture_id" to cultureId,"done" to 0))
    insertOrThrow("task_metadata",null,lab072CV(
        "task_id" to id,"priority" to priority,"kind" to lab072Text(kind,60),
        "recipe_id" to recipeId,"group_id" to groupId,"updated_at" to now))
    insertOrThrow("task_history",null,lab072CV(
        "task_id" to id,"recorded_at" to now,"action" to "Создана",
        "next_due_at" to dueAt,"next_done" to 0,"next_priority" to priority))
}

fun LabDatabase.updateTask072(taskId:Long,done:Boolean?=null,
    dueAt:Long?=null,priority:String?=null,note:String=""):Unit=lab072Tx{
    require(done!=null || dueAt!=null || priority!=null){"Нет изменений"}
    if(priority!=null)require(priority in priority072)
    val before=rawQuery("""SELECT t.done,t.due_at,COALESCE(m.priority,'Не задан')
        FROM tasks t LEFT JOIN task_metadata m ON m.task_id=t.id WHERE t.id=?""",
        arrayOf(taskId.toString())).use{c->
        require(c.moveToFirst()){"Задача не найдена"}
        Triple(c.getInt(0)==1,c.getLong(1),c.getString(2))
    }
    val targetDue=dueAt?:before.second
    require(targetDue>0)
    val targetDone=done?:before.first
    val targetPriority=priority?:before.third
    val now=System.currentTimeMillis()
    update("tasks",lab072CV("done" to if(targetDone)1 else 0,
        "due_at" to targetDue),"id=?",arrayOf(taskId.toString()))
    if(priority!=null){
        insertWithOnConflict("task_metadata",null,lab072CV(
            "task_id" to taskId,"priority" to priority,
            "updated_at" to now),SQLiteDatabase.CONFLICT_IGNORE)
        update("task_metadata",lab072CV("priority" to priority,"updated_at" to now),
            "task_id=?",arrayOf(taskId.toString()))
    }
    insertOrThrow("task_history",null,lab072CV("task_id" to taskId,
        "recorded_at" to now,"action" to "Изменена",
        "previous_due_at" to before.second,"next_due_at" to targetDue,
        "previous_done" to if(before.first)1 else 0,
        "next_done" to if(targetDone)1 else 0,
        "previous_priority" to before.third,"next_priority" to targetPriority,
        "note" to note))
}

/** One task per selected container, all-or-nothing; never one committed task out of 200. */
fun LabDatabase.createTasksBulk072(ids:List<Long>,title:String,dueAt:Long,
    priority:String,kind:String,recipeId:Long?):Unit=lab072Tx{
    checkedIds072(ids)
    require(priority in priority072){"Выбери важность"}
    require(dueAt>0){"Укажи срок"}
    val caption=lab072Text(title,200)
    val operation=lab072Text(kind,60)
    val now=System.currentTimeMillis()
    ids.forEach{id->
        row072(id) // validate existence before writing each row; transaction rollback on failure
        val taskId=insertOrThrow("tasks",null,lab072CV(
            "title" to caption,"due_at" to dueAt,"culture_id" to id,"done" to 0))
        insertOrThrow("task_metadata",null,lab072CV(
            "task_id" to taskId,"priority" to priority,
            "kind" to operation,"recipe_id" to recipeId,"updated_at" to now))
        insertOrThrow("task_history",null,lab072CV(
            "task_id" to taskId,"recorded_at" to now,"action" to "Создана",
            "next_due_at" to dueAt,"next_done" to 0,
            "next_priority" to priority,"note" to "Массовое назначение"))
    }
}
