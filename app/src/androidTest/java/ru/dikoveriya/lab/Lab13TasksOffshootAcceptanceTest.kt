package ru.dikoveriya.lab

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class Lab13TasksOffshootAcceptanceTest {
    @Test fun dateTaskLinksSpecificPlantWithTimeAndSelectedSound(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val db=LabDatabase(context)
        try{
            val plant="QA target "+UUID.randomUUID()
            db.addPlant(plant)
            val plantId=db.plants().first{it.name==plant}.id
            val item=db.addPlantItem11(plantId,"Горшечное")
            val today=LocalDate.now().plusDays(2)
            val due=taskMoment13(today,23,59)
            val at=taskMoment13(today,17,45)
            val id=db.createTask13("Подкормить "+plant,due,"Высокий",
                "Подкормка",Lab13TaskSelection("Горшечное растение",item,
                    db.item11(item)!!.code),reminderAt=at,
                ringtoneUri="content://settings/system/notification_sound",
                treatment="Удобрение по подтверждённой дозировке")
            val details=requireNotNull(db.taskDetails13(id))
            assertEquals(item,details.objectId)
            assertEquals("Горшечное растение",details.objectKind)
            assertEquals(at,details.reminderAt)
            assertEquals("content://settings/system/notification_sound",
                details.ringtoneUri)
            assertTrue(db.readableDatabase.rawQuery("""SELECT 1 FROM task_links
                WHERE task_id=? AND entity_kind='item' AND entity_id=?""",
                arrayOf(id.toString(),item.toString())).use{it.moveToFirst()})
            val f=Lab072TaskFilter(period="Все",dueFrom=taskMoment13(today,0,0),
                dueTo=taskMoment13(today,23,59))
            assertTrue(db.tasksPage072(f,0,100).any{it.id==id})
            db.editTaskReminder13(id,null,null)
            assertNull(db.taskDetails13(id)?.reminderAt)
        }finally{db.close()}
    }

    @Test fun mismatchedOrMissingObjectNeverCreatesAnOrphanTask(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val db=LabDatabase(context)
        try{
            val date=LocalDate.now().plusDays(1)
            val before=db.readableDatabase.rawQuery(
                "SELECT COUNT(*) FROM tasks",null).use{it.moveToFirst();it.getInt(0)}
            var rejected=false
            try{db.createTask13("Проверить растение",
                taskMoment13(date,23,59),"Обычный","Осмотр",
                Lab13TaskSelection("Горшечное растение",99999999))}
            catch(_:Exception){rejected=true}
            assertTrue(rejected)
            val after=db.readableDatabase.rawQuery(
                "SELECT COUNT(*) FROM tasks",null).use{it.moveToFirst();it.getInt(0)}
            assertEquals(before,after)
        }finally{db.close()}
    }

    @Test fun longilobaThreePupsAndNineCormsHaveDistinctQrAndOneParent(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val db=LabDatabase(context)
        try{
            val name="QA Longiloba "+UUID.randomUUID()
            db.addPlant(name)
            val plantId=db.plants().first{it.name==name}.id
            val parent=db.addPlantItem11(plantId,"Маточник",
                substrate="Старый субстрат")
            val parentCode=db.item11(parent)!!.code
            val result=db.separateOffshoots13(parent,3,9,System.currentTimeMillis(),
                "Укоренение","Сфагнум","Теплица / лоток A",
                "Отделение при пересадке")
            assertEquals(3,result.offspringIds.size)
            assertEquals(9,result.tuberIds.size)
            val children=result.offspringIds+result.tuberIds
            assertEquals(12,children.distinct().size)
            assertEquals(12,children.map{db.item11(it)!!.code}.distinct().size)
            children.forEach{id->
                val child=requireNotNull(db.item11(id))
                assertTrue(child.code.startsWith("PLT-"))
                assertEquals(parent,child.parentItemId)
                assertEquals(plantId,child.plantId)
                assertEquals("Теплица / лоток A",child.location)
                assertEquals("Сфагнум",child.substrate)
            }
            result.tuberIds.forEach{
                assertEquals("Клубень",db.item11(it)!!.customKind)
                assertEquals("Укоренение",db.item11(it)!!.stage)
            }
            assertEquals(parentCode,db.item11(parent)!!.code)
            assertEquals("Активен",db.item11(parent)!!.status)
            val members=db.readableDatabase.rawQuery(
                "SELECT COUNT(*) FROM offshoot_members13 WHERE group_id=?",
                arrayOf(result.groupId.toString())).use{it.moveToFirst();it.getInt(0)}
            assertEquals(12,members)
            assertTrue(db.itemEvents11(parent).any{it.kind=="Отделение деток и клубней"})
            val before=db.readableDatabase.rawQuery(
                "SELECT COUNT(*) FROM plant_items WHERE parent_item_id=?",
                arrayOf(parent.toString())).use{it.moveToFirst();it.getInt(0)}
            assertEquals(12,before)
            var rejected=false
            try{db.separateOffshoots13(parent,101,0,System.currentTimeMillis())}
            catch(_:Exception){rejected=true}
            assertTrue(rejected)
            val after=db.readableDatabase.rawQuery(
                "SELECT COUNT(*) FROM plant_items WHERE parent_item_id=?",
                arrayOf(parent.toString())).use{it.moveToFirst();it.getInt(0)}
            assertEquals(before,after)
        }finally{db.close()}
    }

    @Test fun upgradingV12AddsTablesWithoutReplacingExistingTasks(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val helper=LabDatabase(context)
        val memory=SQLiteDatabase.create(null)
        try{
            helper.onCreate(memory)
            memory.execSQL("""INSERT INTO tasks(title,due_at,done,note)
                VALUES('QA старый осмотр',2000000000000,0,'из 1.1.0')""")
            memory.setForeignKeyConstraintsEnabled(false)
            LabCoreV13.backupTables.asReversed().forEach{
                memory.execSQL("DROP TABLE IF EXISTS "+it)
            }
            memory.setForeignKeyConstraintsEnabled(true)
            helper.onUpgrade(memory,12,13)
            assertTrue(memory.rawQuery(
                "SELECT 1 FROM tasks WHERE title='QA старый осмотр'",null)
                .use{it.moveToFirst()})
            LabCoreV13.validate(memory)
            LabCoreV13.backupTables.forEach{table->
                memory.rawQuery("SELECT COUNT(*) FROM "+table,null).close()
            }
        }finally{memory.close();helper.close()}
    }
}
