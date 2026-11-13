package ru.dikoveriya.lab

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Lab10VisualMigrationInstrumentedTest {
    @Test fun migrateTenToElevenPreservesContainerAndCreatesEmptyVisuals(){
        val ctx=InstrumentationRegistry.getInstrumentation().targetContext
        val helper=LabDatabase(ctx)
        val sql=SQLiteDatabase.create(null)
        try{
            helper.onCreate(sql)
            sql.execSQL("""INSERT INTO cultures(id,code,plant_id,quantity,status,passage,
                created_at,stage,location,quantity_kind,origin_state)
                VALUES(99901,'LAB-099901',1,3,'В культуре',4,123456,'В пробирке',
                'Стеллаж A','Побеги','По маточнику')""")
            sql.setForeignKeyConstraintsEnabled(false)
            LabCoreV11.backupTables.asReversed().forEach{sql.execSQL("DROP TABLE IF EXISTS "+it)}
            sql.setForeignKeyConstraintsEnabled(true)
            helper.onUpgrade(sql,10,11)
            sql.rawQuery("SELECT quantity,passage,location,quantity_kind FROM cultures WHERE id=99901",null).use{c->
                assertTrue(c.moveToFirst())
                assertEquals(3,c.getInt(0))
                assertEquals(4,c.getInt(1))
                assertEquals("Стеллаж A",c.getString(2))
                assertEquals("Побеги",c.getString(3))
            }
            for(table in LabCoreV11.backupTables){
                sql.rawQuery("SELECT COUNT(*) FROM "+table,null).use{c->
                    assertTrue(c.moveToFirst());assertEquals(0,c.getInt(0))
                }
            }
            LabCoreV11.validate(sql)
        }finally{sql.close();helper.close()}
    }
}
