package ru.dikoveriya.lab

import android.graphics.BitmapFactory
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
class Lab071LabelInstrumentedTest {
    private val sample=LabLabelSpec(
        code="LAB-000123",
        plant="Alocasia Black Velvet Aurea Variegata",
        passage=3,
        medium="MS+BAP · v2",
        showMedium=true,
        dpi=203
    )

    @Test fun printedLabelIsPhysically40By12mmAt203And300Dpi() {
        for(dpi in listOf(203,300)) {
            val (w,h)=LabLabel40.dimensions(dpi)
            assertTrue("width tolerance at $dpi", kotlin.math.abs(w*25.4/dpi-40.0)<0.1)
            assertTrue("height tolerance at $dpi", kotlin.math.abs(h*25.4/dpi-12.0)<0.1)
            val png=LabLabel40.png(sample.copy(dpi=dpi))
            val decoded=BitmapFactory.decodeByteArray(png,0,png.size)
            assertEquals(w,decoded.width)
            assertEquals(h,decoded.height)
            // pHYs is the first chunk after IHDR.
            assertEquals("pHYs",String(png,37,4,Charsets.US_ASCII))
            assertEquals("LAB-000123",LabQr.getCode("LAB-000123"))
        }
    }

    @Test fun printedQrIsReadableAt203DpiAndKeepsCanonicalCode() {
        val bitmap=LabLabel40.bitmap(sample)
        val scale=bitmap.width/40f
        val qrSide=(10.2f*scale).roundToInt()
        val x=(.55f*scale).roundToInt()
        val y=(bitmap.height-qrSide)/2
        val pixels=IntArray(qrSide*qrSide)
        bitmap.getPixels(pixels,0,qrSide,x,y,qrSide,qrSide)
        val source=RGBLuminanceSource(qrSide,qrSide,pixels)
        val result=QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source)))
        assertEquals(sample.code,result.text)
    }

    @Test fun labelContainsBlackInkAndWhitePaperAcrossCanvas() {
        val bitmap=LabLabel40.bitmap(sample)
        val (width,height)=LabLabel40.dimensions(203)
        assertEquals(width,bitmap.width)
        assertEquals(height,bitmap.height)
        var black=0
        var white=0
        for(y in 0 until height)for(x in 0 until width){
            val px=bitmap.getPixel(x,y)
            if(px==android.graphics.Color.BLACK)black++
            if(px==android.graphics.Color.WHITE)white++
        }
        assertTrue("black label content",black>500)
        assertTrue("white background/QR quiet zone",white>500)
    }
}
