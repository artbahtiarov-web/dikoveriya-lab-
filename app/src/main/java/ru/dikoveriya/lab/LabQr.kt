package ru.dikoveriya.lab

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import java.io.ByteArrayOutputStream

object LabQr {
    fun payload(code: String): String = "DIKOVERIYA:$code"
    fun getCode(text: String): String? {
        val value=text.trim().removePrefix("DIKOVERIYA:")
        return if(Regex("LAB-[0-9]{6}").matches(value)) value else null
    }
    fun bitmap(code: String, size: Int = 600): Bitmap {
        val bits=MultiFormatWriter().encode(payload(code),BarcodeFormat.QR_CODE,size,size,mapOf(EncodeHintType.MARGIN to 2))
        return Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888).apply {
            for(y in 0 until size) for(x in 0 until size) setPixel(x,y,if(bits[x,y]) Color.BLACK else Color.WHITE)
        }
    }
    fun png(code: String): ByteArray = ByteArrayOutputStream().use { stream ->
        check(bitmap(code).compress(Bitmap.CompressFormat.PNG,100,stream))
        stream.toByteArray()
    }
}
