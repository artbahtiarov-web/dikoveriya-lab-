package ru.dikoveriya.lab

import android.graphics.Bitmap
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/**
 * Full DB/ZIP roundtrip with user photo covers, plant styling, categorized stock
 * and audited user protocols. Runs only in the isolated Android emulator.
 */
@RunWith(AndroidJUnit4::class)
class Lab10EndToEndInstrumentedTest {
    @Test fun plantCoverAndKnowledgeAndResourcesSurvivePortableBackup(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val db=LabDatabase(context)
        try {
            val plant=db.plants().firstOrNull() ?: run {
                db.addPlant("QA-"+UUID.randomUUID())
                db.plants().first()
            }
            val photoName="plcover_"+UUID.randomUUID()+".png"
            val photoFolder=File(context.filesDir,"culture_photos").apply{mkdirs()}
            val bytes=ByteArrayOutputStream()
            val bitmap=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888)
            try{ assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,bytes)) }
            finally{bitmap.recycle()}
            File(photoFolder,photoName).writeBytes(bytes.toByteArray())
            db.writableDatabase.execSQL(
                """INSERT OR REPLACE INTO plant_visuals(
                    plant_id,icon_key,color_key,cover_filename,cover_zoom,cover_pan_x,cover_pan_y,changed_at
                  ) VALUES (?,?,?,?,?,?,?,?)""",
                arrayOf(plant.id,"sprout","moss",photoName,1.7,0.12,-0.1,System.currentTimeMillis())
            )
            db.stylePlant10(plant.id,"sprout","moss",1.7f,0.12f,-0.1f)
            val matName="QA inventory "+UUID.randomUUID()
            db.addMaterial(matName,"г",10.0,2.0,1.0,"Реактивы")
            val material=db.materials().first{it.name==matName}
            db.moveMaterial(material.id,-2.0,"Тестовый расход")
            assertEquals("Реактивы",db.materials().first{it.id==material.id}.category)
            assertTrue(db.materialHistory10(material.id).isNotEmpty())
            val noteTitle="QA protocol "+UUID.randomUUID()
            db.addKnowledge10(noteTitle,"Протокол","Проверенная процедура, введена пользователем.",
                "https://example.org/source",plant.id,"alocasia, QA")
            val note=db.knowledgeNotes10(noteTitle).first()
            db.updateKnowledge10(note.id,noteTitle,"Протокол",
                "Вторая редакция протокола.",note.sourceUrl,plant.id,"alocasia, QA")
            assertEquals(2,db.knowledgeNotes10(noteTitle).first().version)
            val archive=ByteArrayOutputStream()
            LabBackup.save(context,db,archive)
            assertTrue(archive.size()>1000)
            db.stylePlant10(plant.id,"star","gold")
            db.deleteKnowledge10(note.id)
            LabBackup.restore(context,db,ByteArrayInputStream(archive.toByteArray()))
            val restored=db.plantVisuals10().getValue(plant.id)
            assertEquals(photoName,restored.coverFilename)
            assertEquals("sprout",restored.iconKey)
            assertEquals(1.7f,restored.coverZoom,.001f)
            assertEquals(.12f,restored.panX,.001f)
            assertTrue(File(photoFolder,photoName).isFile)
            assertEquals(2,db.knowledgeNotes10(noteTitle).first().version)
            assertTrue(db.materialHistory10(material.id).any{it.delta==-2.0})
            LabCoreV11.validate(db.readableDatabase)
        }finally{db.close()}
    }

    @Test fun oldTenToElevenAddsOnlyNewFields(){
        val ctx=InstrumentationRegistry.getInstrumentation().targetContext
        val helper=LabDatabase(ctx)
        val sql=SQLiteDatabase.create(null)
        try {
            helper.onCreate(sql)
            sql.setForeignKeyConstraintsEnabled(false)
            LabCoreV11.backupTables.asReversed().forEach{
                sql.execSQL("DROP TABLE IF EXISTS "+it)
            }
            sql.execSQL("DROP INDEX IF EXISTS idx_material_category")
            sql.execSQL("CREATE TABLE materials_old AS SELECT id,name,unit,quantity,min_quantity,cost_per_unit FROM materials")
            sql.execSQL("DROP TABLE materials")
            sql.execSQL("""CREATE TABLE materials (
                id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL UNIQUE,
                unit TEXT NOT NULL,quantity REAL NOT NULL,min_quantity REAL NOT NULL DEFAULT 0,
                cost_per_unit REAL NOT NULL DEFAULT 0)""")
            sql.execSQL("INSERT INTO materials SELECT * FROM materials_old")
            sql.execSQL("DROP TABLE materials_old")
            sql.setForeignKeyConstraintsEnabled(true)
            helper.onUpgrade(sql,10,11)
            val category=sql.rawQuery("PRAGMA table_info(materials)",null).use{c->
                var has=false
                while(c.moveToNext())if(c.getString(1)=="category")has=true
                has
            }
            assertTrue(category)
            LabCoreV11.validate(sql)
        }finally{sql.close();helper.close()}
    }
}
