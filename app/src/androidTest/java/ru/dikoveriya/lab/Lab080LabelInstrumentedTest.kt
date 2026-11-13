package ru.dikoveriya.lab

import android.graphics.BitmapFactory
import android.graphics.Color
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class Lab080LabelInstrumentedTest {
    private val values=LabelValues("LAB-000123",
        "Alocasia Black Velvet Aurea variegata",3,"MS v2","13.11.26","M-01","")
    @Test fun customTemplateIsPixelPerfectAndQrDecodesAtBothDpis(){
        for(dpi in listOf(203,300)){
            val template=LabelDesign.initial().copy(dpi=dpi)
            val png=LabLabelDesigner.png(values,template)
            assertEquals("pHYs",String(png,37,4,Charsets.US_ASCII))
            val b=BitmapFactory.decodeByteArray(png,0,png.size)
            val (w,h)=LabLabel40.dimensions(dpi)
            assertEquals(w,b.width);assertEquals(h,b.height)
            assertTrue(abs(w*25.4/dpi-40.0)<.1)
            assertTrue(abs(h*25.4/dpi-12.0)<.1)
            val qr=template.layers.single{it.kind=="qr"}
            val scale=w/40f
            val x=(qr.x*scale).toInt()
            val y=(qr.y*scale).toInt()
            val side=(qr.width*scale).toInt().coerceAtMost((qr.height*scale).toInt())
            val pixels=IntArray(side*side)
            b.getPixels(pixels,0,side,x,y,side,side)
            val decoded=QRCodeReader().decode(
                BinaryBitmap(HybridBinarizer(RGBLuminanceSource(side,side,pixels))))
            assertEquals(values.code,decoded.text)
        }
    }
    @Test fun undoStateSerializesAndRejectsMissingQrAndCode(){
        val original=LabelDesign.initial()
        val moved=original.replace(original.layers[1].moved(1f,0f))
        assertEquals(moved,LabelDesign.decode(moved.encode()))
        assertEquals(original,LabelDesign.decode(original.encode()))
        assertNotEquals(original,moved)
        assertThrows(IllegalArgumentException::class.java){
            original.copy(layers=original.layers.filterNot{it.kind=="qr"}).encode()
        }
        assertThrows(IllegalArgumentException::class.java){
            original.copy(layers=original.layers.filterNot{it.kind=="code"}).encode()
        }
    }
    @Test fun v9ToV10UpgradePreservesCultureAndOldEvidence(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val helper=LabDatabase(context)
        val sql=SQLiteDatabase.create(null)
        try{
            helper.onCreate(sql)
            sql.execSQL("""INSERT INTO cultures(id,code,plant_id,quantity,status,passage,
                created_at,stage,location,quantity_kind,origin_state)
                VALUES(9010,'LAB-009010',1,5,'В культуре',7,123456,'В пробирке',
                'Прежнее место','Не уточнено','Не установлено')""")
            sql.execSQL("""INSERT INTO culture_measurements(
                culture_id,observed_at,explants,shoots,plants,notes,record_kind)
                VALUES(9010,123457,NULL,0,NULL,'Замер','observation')""")
            sql.setForeignKeyConstraintsEnabled(false)
            LabCoreV10.backupTables.asReversed().forEach{sql.execSQL("DROP TABLE IF EXISTS $it")}
            sql.setForeignKeyConstraintsEnabled(true)
            helper.onUpgrade(sql,9,10)
            sql.rawQuery("SELECT quantity,passage,quantity_kind,location FROM cultures WHERE id=9010",null).use{c->
                assertTrue(c.moveToFirst())
                assertEquals(5,c.getInt(0));assertEquals(7,c.getInt(1))
                assertEquals("Не уточнено",c.getString(2))
                assertEquals("Прежнее место",c.getString(3))
            }
            sql.rawQuery("SELECT explants,shoots,plants FROM culture_measurements WHERE culture_id=9010",null).use{c->
                assertTrue(c.moveToFirst())
                assertTrue(c.isNull(0));assertEquals(0,c.getInt(1));assertTrue(c.isNull(2))
            }
            LabCoreV10.backupTables.forEach{table->
                sql.rawQuery("SELECT COUNT(*) FROM $table",null).use{c->
                    assertTrue(c.moveToFirst());assertEquals(0,c.getInt(0))
                }
            }
            LabCoreV10.validate(sql)
        }finally{sql.close();helper.close()}
    }
}
