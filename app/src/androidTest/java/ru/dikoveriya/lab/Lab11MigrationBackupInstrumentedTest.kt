package ru.dikoveriya.lab

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** 1.1 migration and portable photo ZIP safety, on isolated emulator data. */
@RunWith(AndroidJUnit4::class)
class Lab11MigrationBackupInstrumentedTest{
    @Test fun migrationElevenToTwelveKeepsCultureIdAndAddsOnlyNewSchema(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val helper=LabDatabase(context)
        val memory=SQLiteDatabase.create(null)
        try{
            helper.onCreate(memory)
            val legacyId=910010L
            memory.execSQL("""INSERT OR IGNORE INTO plants(id,name,description)
                VALUES(910010,'QA legacy plant','Не изменять')""")
            memory.execSQL("""INSERT INTO cultures(
                id,code,plant_id,quantity,status,passage,created_at,location,
                quantity_kind,origin_state,stage
            ) VALUES(?,'LAB-910010',?,7,'В культуре',2,100000,'Подоконник',
                'Растения','Не установлено','В пробирке')""",
                arrayOf(legacyId,legacyId))
            memory.setForeignKeyConstraintsEnabled(false)
            LabCoreV12.backupTables.asReversed().forEach{table->
                memory.execSQL("DROP TABLE IF EXISTS "+table)
            }
            memory.setForeignKeyConstraintsEnabled(true)
            helper.onUpgrade(memory,11,12)
            val old=memory.rawQuery(
                "SELECT code,quantity,location FROM cultures WHERE id=?",
                arrayOf(legacyId.toString())).use{c->
                assertTrue(c.moveToFirst());Triple(c.getString(0),c.getInt(1),c.getString(2))
            }
            assertEquals("LAB-910010",old.first)
            assertEquals(7,old.second)
            assertEquals("Подоконник",old.third)
            LabCoreV12.backupTables.forEach{table->
                memory.rawQuery("SELECT COUNT(*) FROM "+table,null).close()
            }
            LabCoreV12.validate(memory)
        }finally{memory.close();helper.close()}
    }

    @Test fun fullBackupRestoresNewItemWithPhotoAndRejectedTruncatedZipIsSafe(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val db=LabDatabase(context)
        val imageDir=File(context.filesDir,"culture_photos").apply{mkdirs()}
        try{
            val title="QA backup "+UUID.randomUUID()
            db.addPlant(title)
            val plantId=db.plants().first{it.name==title}.id
            val itemId=db.addPlantItem11(plantId,"Горшечное",
                substrate="Агробалт QA",locationText="Тестовый подоконник")
            val before=db.item11(itemId)!!
            val filename="v11_"+UUID.randomUUID()+".png"
            val png=ByteArrayOutputStream()
            val bmp=Bitmap.createBitmap(24,24,Bitmap.Config.ARGB_8888)
            try{assertTrue(bmp.compress(Bitmap.CompressFormat.PNG,100,png))}
            finally{bmp.recycle()}
            File(imageDir,filename).writeBytes(png.toByteArray())
            db.writableDatabase.insertOrThrow("item_photos",null,ContentValues().apply{
                put("entity_kind","item");put("entity_id",itemId)
                put("filename",filename);put("caption","До пересадки")
                put("added_at",System.currentTimeMillis())
            })
            db.writableDatabase.update("plant_items",ContentValues().apply{
                put("cover_filename",filename)
            },"id=?",arrayOf(itemId.toString()))

            val full=ByteArrayOutputStream()
            LabBackup.save(context,db,full)
            val bad=ByteArrayOutputStream()
            // Construct a well-formed ZIP with exactly this referenced photo missing.
            ZipInputStream(ByteArrayInputStream(full.toByteArray())).use{zin->
                ZipOutputStream(bad).use{zout->
                    var entry=zin.nextEntry
                    while(entry!=null){
                        val name=entry.name
                        if(name!="photos/"+filename){
                            zout.putNextEntry(ZipEntry(name))
                            zin.copyTo(zout)
                            zout.closeEntry()
                        }
                        zin.closeEntry()
                        entry=zin.nextEntry
                    }
                }
            }
            var rejected=false
            try{LabBackup.restore(context,db,ByteArrayInputStream(bad.toByteArray()))}
            catch(_:Exception){rejected=true}
            assertTrue("Damaged ZIP must be rejected",rejected)
            assertEquals(before.code,db.item11(itemId)!!.code)
            assertTrue(File(imageDir,filename).isFile)

            db.performItemAction11(itemId,"Пересадка",System.currentTimeMillis(),
                newSubstrate="Испорченная временная смесь")
            assertEquals("Испорченная временная смесь",db.item11(itemId)!!.substrate)
            LabBackup.restore(context,db,ByteArrayInputStream(full.toByteArray()))
            assertEquals(before.code,db.item11(itemId)!!.code)
            assertEquals("Агробалт QA",db.item11(itemId)!!.substrate)
            assertTrue(File(imageDir,filename).isFile)
            assertEquals(filename,db.itemPhotos11(itemId).first().first)
            LabCoreV12.validate(db.readableDatabase)
        }finally{db.close()}
    }
}
