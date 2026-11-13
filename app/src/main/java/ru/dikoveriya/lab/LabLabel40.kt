package ru.dikoveriya.lab

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.CRC32
import kotlin.math.roundToInt

/** The physical sheet is 40 mm wide and 12 mm high (landscape).
 * Exported PNG includes pHYs DPI metadata, but the NIIMBOT app must also be set
 * to the matching 40x12 mm media type. Direct Bluetooth printing is not implemented.
 */
data class LabLabelSpec(
    val code: String,
    val plant: String,
    val passage: Int,
    val medium: String = "",
    val showMedium: Boolean = true,
    val dpi: Int = 203
)

object LabLabel40 {
    const val WIDTH_MM = 40
    const val HEIGHT_MM = 12
    val supportedDpi = listOf(203,300)

    fun dimensions(dpi: Int): Pair<Int,Int> {
        require(dpi in supportedDpi) { "Разрешение: 203 или 300 dpi" }
        return ((WIDTH_MM*dpi/25.4).roundToInt()) to ((HEIGHT_MM*dpi/25.4).roundToInt())
    }

    fun bitmap(spec: LabLabelSpec): Bitmap {
        require(LabQr.getCode(spec.code)==spec.code) { "Некорректный ID контейнера" }
        val (width,height)=dimensions(spec.dpi)
        val scale=width/WIDTH_MM.toFloat()
        fun mm(value:Float):Float=value*scale
        val paper=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(paper)
        canvas.drawColor(Color.WHITE)
        val black=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=Color.BLACK}
        val qrSide=mm(10.2f).roundToInt()
        val qrBits=MultiFormatWriter().encode(
            // Bare code is shorter than "DIKOVERIYA:LAB-xxxxxx", improving
            // the physical QR module size on a label only 12 mm high.
            spec.code,BarcodeFormat.QR_CODE,qrSide,qrSide,
            mapOf(EncodeHintType.MARGIN to 4,EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M)
        )
        val xQr=mm(.55f).roundToInt()
        val yQr=(height-qrSide)/2
        val qrPaint=Paint().apply{color=Color.BLACK;style=Paint.Style.FILL}
        for(y in 0 until qrSide)for(x in 0 until qrSide) {
            if(qrBits[x,y]) canvas.drawRect(
                (xQr+x).toFloat(),(yQr+y).toFloat(),
                (xQr+x+1).toFloat(),(yQr+y+1).toFloat(),qrPaint)
        }

        val textLeft=mm(11.55f)
        val textRight=width-mm(.7f)
        val maxWidth=textRight-textLeft
        val titlePaint=Paint(black).apply{
            typeface=android.graphics.Typeface.create("sans-serif",android.graphics.Typeface.BOLD)
            textSize=mm(2.0f)
            isSubpixelText=false
        }
        val codePaint=Paint(black).apply{
            typeface=android.graphics.Typeface.create("sans-serif",android.graphics.Typeface.BOLD)
            textSize=mm(1.75f)
            isSubpixelText=false
        }
        val infoPaint=Paint(black).apply{
            typeface=android.graphics.Typeface.create("sans-serif",android.graphics.Typeface.NORMAL)
            textSize=mm(1.37f)
            isSubpixelText=false
        }
        canvas.save()
        canvas.clipRect(textLeft,0f,textRight,height.toFloat())
        val titleLines=wrapPlant(spec.plant.trim(),maxWidth,titlePaint)
        titleLines.forEachIndexed {i,line->
            canvas.drawText(line,textLeft,mm(2.75f+i*2.35f),titlePaint)
        }
        canvas.drawText("${spec.code}  ·  P${spec.passage}",
            textLeft,mm(8.06f),codePaint)
        val footer=if(spec.showMedium && spec.medium.isNotBlank()) spec.medium else "ДИКОВЕРИЯ LAB"
        canvas.drawText(elide(footer,maxWidth,infoPaint),
            textLeft,mm(10.35f),infoPaint)
        canvas.restore()
        return paper
    }

    internal fun elide(value:String,maxWidth:Float,paint:Paint):String {
        if(paint.measureText(value)<=maxWidth)return value
        var end=value.length
        while(end>0 && paint.measureText(value.take(end)+"…")>maxWidth)end--
        return value.take(end).trimEnd()+"…"
    }

    private fun wrapPlant(name:String,maxWidth:Float,paint:Paint):List<String> {
        if(name.isBlank())return listOf("Растение не указано")
        if(paint.measureText(name)<=maxWidth)return listOf(name)
        val words=name.split(Regex("\\s+"))
        var first=""
        var cursor=0
        while(cursor<words.size) {
            val proposed=(first+" "+words[cursor]).trim()
            if(first.isNotBlank() && paint.measureText(proposed)>maxWidth)break
            first=proposed
            cursor++
        }
        val left=if(cursor>=words.size)"" else words.drop(cursor).joinToString(" ")
        return if(left.isBlank()) listOf(elide(first,maxWidth,paint))
            else listOf(elide(first,maxWidth,paint),elide(left,maxWidth,paint))
    }

    fun png(spec:LabLabelSpec):ByteArray = png(bitmap(spec),spec.dpi)

    /** Share the same pHYs path for the editable and the legacy label renderer. */
    fun png(image:Bitmap,dpi:Int):ByteArray {
        require(dpi in supportedDpi)
        val expected=dimensions(dpi)
        require(image.width==expected.first && image.height==expected.second) {
            "Размер макета не соответствует бумаге 40×12 мм"
        }
        val encoded=ByteArrayOutputStream().use{out->
            check(image.compress(Bitmap.CompressFormat.PNG,100,out))
            out.toByteArray()
        }
        // PNG physical resolution pHYs: pixels/metre, unit=metre.
        val ppm=(dpi/0.0254).roundToInt()
        val data=ByteArrayOutputStream().apply{
            writeInt(ppm);writeInt(ppm);write(1)
        }.toByteArray()
        val type=byteArrayOf('p'.code.toByte(),'H'.code.toByte(),'Y'.code.toByte(),'s'.code.toByte())
        val crc=CRC32().apply{update(type);update(data)}
        val chunk=ByteArrayOutputStream().apply{
            writeInt(data.size);write(type);write(data);writeInt(crc.value.toInt())
        }.toByteArray()
        // A PNG always starts with an 8-byte signature and a 25-byte IHDR chunk.
        require(encoded.size>33 && String(encoded.copyOfRange(12,16),Charsets.US_ASCII)=="IHDR") {
            "Неверный формат PNG"
        }
        return ByteArrayOutputStream().apply{
            write(encoded,0,33);write(chunk);write(encoded,33,encoded.size-33)
        }.toByteArray()
    }

    private fun ByteArrayOutputStream.writeInt(value:Int) {
        write((value ushr 24) and 255)
        write((value ushr 16) and 255)
        write((value ushr 8) and 255)
        write(value and 255)
    }

    fun share(context:Context,spec:LabLabelSpec) {
        val dir=File(context.cacheDir,"labels").apply{mkdirs()}
        val file=File(dir,"label-${spec.code}-40x12.png")
        file.writeBytes(png(spec))
        val uri=FileProvider.getUriForFile(context,"${context.packageName}.share",file)
        val intent=Intent(Intent.ACTION_SEND).apply{
            type="image/png"
            putExtra(Intent.EXTRA_STREAM,uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData=android.content.ClipData.newUri(context.contentResolver,"Диковерия · 40×12 мм",uri)
        }
        context.startActivity(Intent.createChooser(intent,"Отправить этикетку 40×12 мм"))
    }
}
