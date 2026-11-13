package ru.dikoveriya.lab

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import com.google.zxing.qrcode.QRCodeReader

@RunWith(AndroidJUnit4::class)
class Lab09EditorAndPrinterInstrumentedTest {
    @Test fun oldV1LabelLoadsAsV2WithoutLosingCoordinatesOrQr(){
        val old=LabelDesign.initial().encode()
            .replace("\"version\":2","\"version\":1")
        val restored=LabelDesign.decode(old)
        val qr=restored.layers.single{it.kind=="qr"}
        assertEquals(10.3f,qr.width,0.001f)
        assertEquals(.7f,qr.x,0.001f)
        assertFalse(restored.layers.any{it.italic || it.underline || it.locked})
        val updated=restored.replace(restored.layers.single{it.kind=="plant"}.copy(
            italic=true,underline=true,bold=true,
            letterSpacingMm=.2f,family="Sans Condensed Bold"))
        assertEquals(updated,LabelDesign.decode(updated.encode()))
    }
    @Test fun scalingIsRealMillimetresAndQrStaysSquare(){
        val original=LabelDesign.initial()
        val qr=original.layers.single{it.kind=="qr"}
        val smaller=qr.scaled(.9f)
        assertTrue(smaller.width<qr.width)
        assertEquals(smaller.width,smaller.height,0.0001f)
        assertTrue(smaller.width>=8.8f)
        val moved=qr.resizedFromCorner(3,-2f,-2f)
        assertEquals(moved.width,moved.height,0.0001f)
        assertTrue(moved.x>=0 && moved.y>=0)
        assertTrue(moved.x+moved.width<=40.001f)
        val plant=original.layers.single{it.kind=="plant"}
        val bigger=plant.scaled(1.1f)
        assertTrue(bigger.width>plant.width||bigger.fontMm>plant.fontMm)
        assertTrue(bigger.x+bigger.width<=40.001f)
        assertEquals(plant,plant.copy(locked=true).scaled(1.2f).copy(locked=false))
    }
    @Test fun twentyOrMoreOptionsHaveCyrillicAndPrintStyles(){
        assertTrue(LabelFontCatalog.options.size>=20)
        assertEquals(LabelFontCatalog.options.size,
            LabelFontCatalog.options.map{it.id}.distinct().size)
        assertTrue(LabelFontCatalog.options.all{LabelFontCatalog.cyrillicCovered(it.id)})
        val styles=listOf(
            Triple(false,false,false),Triple(true,false,false),
            Triple(false,true,false),Triple(true,true,false),
            Triple(true,true,true),Triple(false,false,true)
        )
        for((bold,italic,underline) in styles) {
            val base=LabelDesign.initial()
            val plant=base.layers.single{it.kind=="plant"}
            val new=base.replace(plant.copy(bold=bold,italic=italic,
                underline=underline,family="Sans Rounded"))
            val values=LabelValues("LAB-000123","Алоказия Жёлтая",2,"MS v2","","","")
            val image=LabLabelDesigner.bitmap(values,new)
            assertEquals(LabLabel40.dimensions(203).first,image.width)
            assertNotNull(image)
            assertEquals(new,LabelDesign.decode(new.encode()))
        }
    }
    @Test fun frameParserHandlesSplitsConcatenationAndCorruption(){
        val one=NiimCodec.packet(0x21,byteArrayOf(3))
        assertEquals(0x55.toByte(),one[0])
        assertEquals(0xaa.toByte(),one[one.lastIndex])
        val two=NiimCodec.packet(0x23,byteArrayOf(1))
        val reader=NiimCodec.Decoder()
        assertTrue(reader.feed(one.copyOfRange(0,3)).isEmpty())
        val result=reader.feed(one.copyOfRange(3,one.size)+two)
        assertEquals(listOf(0x21,0x23),result.map{it.opcode})
        val corrupted=one.copyOf().also{it[5]=0}
        assertTrue(NiimCodec.Decoder().feed(corrupted).isEmpty())
    }
    @Test fun printheadRowsAreRotatedWithoutCropping(){
        val profile=NiimModel.forId(512)!!
        val (w,h)=LabLabel40.dimensions(203)
        val bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        bitmap.setPixel(1,h-1,Color.BLACK)
        val rows=NiimRaster.rows(bitmap,profile)
        assertEquals(w,rows.sumOf{it.run})
        val at1=rows.firstOrNull{1>=it.index&&1<it.index+it.run}
        assertNotNull(at1)
        assertEquals(1,at1!!.black)
        val frames=NiimRaster.rowFrames(bitmap,profile)
        assertTrue(frames.isNotEmpty())
        assertTrue(frames.all{it[0]==0x55.toByte()})
        val decoded=NiimCodec.Decoder()
        assertEquals(frames.size,
            frames.sumOf{decoded.feed(it).size})
    }
    @Test fun printProfilesRejectUnknownHardware(){
        assertNull(NiimModel.forId(99999))
        assertEquals(203,NiimModel.forId(512)!!.dpi)
        assertEquals(96,NiimModel.forId(512)!!.width)
        assertEquals(300,NiimModel.forId(528)!!.dpi)
    }
}
