package ru.dikoveriya.lab

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.roundToInt

/** Interoperable NIIMBOT frame/row layer.
 *
 * Sources: NIIMBOT Community Wiki print-tasks and
 * iscarelli/niimbot-web-bluetooth (MIT), 2026 documentation.
 * The driver does not claim physical validation on the owner's hardware.
 */
data class NiimPacket(val opcode:Int,val payload:ByteArray)

object NiimCodec {
    fun packet(cmd:Int,payload:ByteArray=byteArrayOf()):ByteArray{
        require(cmd in 0..255 && payload.size<=255)
        var crc=cmd xor payload.size
        payload.forEach{crc=crc xor (it.toInt() and 0xff)}
        return byteArrayOf(0x55,0x55,cmd.toByte(),payload.size.toByte())+
            payload+byteArrayOf(crc.toByte(),0xaa.toByte(),0xaa.toByte())
    }
    fun ushort(value:Int)=byteArrayOf((value ushr 8).toByte(),value.toByte())
    fun uint16(first:Byte,second:Byte)=
        ((first.toInt() and 255) shl 8) or (second.toInt() and 255)

    /** Notifications may split or concatenate frames. Corrupted prefix is discarded. */
    class Decoder {
        private val buffer=ArrayList<Byte>()
        fun feed(data:ByteArray):List<NiimPacket>{
            buffer.addAll(data.toList())
            val frames=mutableListOf<NiimPacket>()
            while(buffer.size>=7){
                val start=buffer.indexOfFirst{it==0x55.toByte()}
                if(start<0){buffer.clear();break}
                repeat(start){buffer.removeAt(0)}
                if(buffer.size<7)break
                if(buffer[1]!=0x55.toByte()){buffer.removeAt(0);continue}
                val length=buffer[3].toInt() and 255
                val packetSize=length+7
                if(buffer.size<packetSize)break
                val bytes=buffer.take(packetSize).toByteArray()
                var crc=(bytes[2].toInt() and 255) xor length
                for(i in 4 until 4+length)crc=crc xor (bytes[i].toInt() and 255)
                val valid=bytes[4+length]==crc.toByte() &&
                    bytes[5+length]==0xaa.toByte() &&
                    bytes[6+length]==0xaa.toByte()
                if(!valid){buffer.removeAt(0);continue}
                frames+=NiimPacket(bytes[2].toInt() and 255,
                    bytes.copyOfRange(4,4+length))
                repeat(packetSize){buffer.removeAt(0)}
            }
            if(buffer.size>4096)buffer.clear()
            return frames
        }
    }
}

data class NiimModel(
    val id:Int,val name:String,val dpi:Int,val width:Int,
    val task:String,val printVerifiedExternally:Boolean
) {
    companion object {
        // External community hardware reports; physical tests in Dikoveriya
        // not yet performed. Unknown models are never silently treated as D11.
        private val known=listOf(
            NiimModel(512,"D11",203,96,"b1",false),
            NiimModel(514,"D11S",203,96,"b1",false),
            NiimModel(528,"D11_H",300,144,"v4",true),
            NiimModel(531,"D11_PRO",300,142,"v4",false),
            NiimModel(2304,"D110",203,96,"b1",true),
            NiimModel(2305,"D110",203,96,"b1",false),
            NiimModel(2320,"D110_M",203,96,"v4",false)
        )
        fun forId(id:Int)=known.firstOrNull{it.id==id}
    }
}

/**
 * Thermal head is 12 mm wide, fed along the 40 mm length.
 * The exported landscape design must therefore be rotated into a 96x320
 * (203dpi) or headWidth x 472 (300dpi) printer raster.
 */
object NiimRaster {
    data class Row(val index:Int,val run:Int,val black:Int,val pixels:ByteArray)
    fun rows(bitmap:Bitmap,model:NiimModel,flipFeed:Boolean=false):List<Row>{
        val (imageWidth,imageHeight)=LabLabel40.dimensions(model.dpi)
        require(bitmap.width==imageWidth && bitmap.height==imageHeight){
            "Разрешение макета не соответствует принтеру: "+model.dpi+" dpi"
        }
        require(model.width>=imageHeight-2 && model.width<=imageHeight+4){
            "Ширина термоголовки несовместима с этикеткой 12 мм"
        }
        val out=mutableListOf<Row>()
        val stride=(model.width+7)/8
        var index=0
        while(index<imageWidth){
            fun make(at:Int):ByteArray {
                val bits=ByteArray(stride)
                for(col in 0 until model.width){
                    val v=col-(model.width-imageHeight)/2
                    if(v !in 0 until imageHeight)continue
                    val feed=if(flipFeed)imageWidth-1-at else at
                    val ink=bitmap.getPixel(feed,imageHeight-1-v)
                    val lum=(((ink shr 16) and 255)*299 +
                        ((ink shr 8) and 255)*587 +(ink and 255)*114)/1000
                    if(Color.alpha(ink)>80 && lum<140)
                        bits[col/8]=(bits[col/8].toInt() or
                            (0x80 ushr (col%8))).toByte()
                }
                return bits
            }
            val row=make(index)
            var run=1
            while(index+run<imageWidth && run<200 &&
                row.contentEquals(make(index+run)))run++
            val black=row.sumOf{Integer.bitCount(it.toInt() and 255)}
            out+=Row(index,run,black,row)
            index+=run
        }
        return out
    }
    fun rowFrames(bitmap:Bitmap,model:NiimModel,flipFeed:Boolean=false):List<ByteArray>{
        return rows(bitmap,model,flipFeed).map{r->
            if(r.black==0)NiimCodec.packet(0x84,NiimCodec.ushort(r.index)+
                byteArrayOf(r.run.toByte()))
            else NiimCodec.packet(0x85,NiimCodec.ushort(r.index)+
                byteArrayOf(0, (r.black and 255).toByte(),
                    (r.black ushr 8).toByte(),r.run.toByte())+r.pixels)
        }
    }
}
