package ru.dikoveriya.lab

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Isolated in-memory rehearsal of the actual LabDatabase.onUpgrade(8,9).
 *
 * Constructs the real app schema, removes all v9 additions to model a v8
 * installation, then upgrades without touching any on-device user database.
 */
@RunWith(AndroidJUnit4::class)
class Lab072MigrationInstrumentedTest {
    @Test fun oldCultureAndNullableMeasurementsSurviveV8toV9() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val helper=LabDatabase(context)
        val sql=SQLiteDatabase.create(null)
        try {
            helper.onCreate(sql)
            sql.execSQL("""INSERT INTO cultures(id,code,plant_id,quantity,status,passage,
                created_at,location,quantity_kind,origin_state,stage)
                VALUES(9001,'LAB-009001',1,7,'В культуре',2,123456,
                'Старое текстовое место','Не уточнено','Не установлено','В пробирке')""")
            sql.execSQL("""INSERT INTO culture_measurements(
                culture_id,observed_at,explants,shoots,plants,notes,record_kind)
                VALUES(9001,234567,NULL,0,NULL,'Побегов нет','observation')""")
            sql.setForeignKeyConstraintsEnabled(false)
            LabCoreV9.backupTables.asReversed().forEach{table->
                sql.execSQL("DROP TABLE IF EXISTS $table")
            }
            sql.setForeignKeyConstraintsEnabled(true)

            helper.onUpgrade(sql,8,9)
            val old=sql.rawQuery("""SELECT quantity,quantity_kind,location,origin_state
                FROM cultures WHERE id=9001""",null).use{c->
                assertTrue(c.moveToFirst())
                listOf(c.getString(0),c.getString(1),c.getString(2),c.getString(3))
            }
            assertEquals(listOf("7","Не уточнено","Старое текстовое место","Не установлено"),old)
            sql.rawQuery("SELECT explants,shoots,plants FROM culture_measurements WHERE culture_id=9001",
                null).use{c->
                assertTrue(c.moveToFirst())
                assertTrue(c.isNull(0))
                assertEquals(0,c.getInt(1))
                assertTrue(c.isNull(2))
            }
            for(table in LabCoreV9.backupTables){
                sql.rawQuery("SELECT COUNT(*) FROM $table",null).use{
                    assertTrue(it.moveToFirst())
                    assertEquals("v9 must not invent $table",0,it.getInt(0))
                }
            }
            LabCoreV9.validate(sql)
        } finally {
            sql.close()
            helper.close()
        }
    }
}
