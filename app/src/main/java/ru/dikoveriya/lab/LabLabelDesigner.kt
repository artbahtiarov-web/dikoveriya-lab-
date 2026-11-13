package ru.dikoveriya.lab

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.database.sqlite.SQLiteDatabase
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.min
import kotlin.math.roundToInt

/** Coordinates and font sizes are PHYSICAL millimetres, not Android dp.
 * A design is stable across screen sizes, previews and 203/300 dpi output.
 */
data class LabelLayer(
    val id:String,
    val kind:String,
    val x:Float,val y:Float,val width:Float,val height:Float,
    val fontMm:Float=1.65f,
    val family:String="Sans",
    val bold:Boolean=false,
    val italic:Boolean=false,
    val underline:Boolean=false,
    val letterSpacingMm:Float=0f,
    val locked:Boolean=false,
    val align:String="Слева",
    val rotation:Int=0,
    val text:String="",
    val visible:Boolean=true
) {
    fun moved(dx:Float,dy:Float):LabelLayer=if(locked)this else copy(
        x=(x+dx).coerceIn(0f,40f-width),
        y=(y+dy).coerceIn(0f,12f-height)
    )
    /** Scale the physical object; QR remains square, text size scales with its box. */
    /**
     * corner: 0=top-left, 1=top-right, 2=bottom-left, 3=bottom-right.
     * The opposite corner stays fixed. QR never loses its 1:1 aspect ratio.
     */
    fun resizedFromCorner(corner:Int,dx:Float,dy:Float):LabelLayer{
        if(locked||corner !in 0..3||!dx.isFinite()||!dy.isFinite())return this
        val leftCorner=corner==0||corner==2
        val topCorner=corner==0||corner==1
        val minW=if(kind=="qr")8.8f else 2f
        val minH=if(kind=="qr")8.8f else .8f
        val originalRight=x+width
        val originalBottom=y+height
        val left=if(leftCorner)(x+dx).coerceIn(0f,originalRight-minW)else x
        val right=if(leftCorner)originalRight else (originalRight+dx).coerceIn(x+minW,40f)
        val top=if(topCorner)(y+dy).coerceIn(0f,originalBottom-minH)else y
        val bottom=if(topCorner)originalBottom else (originalBottom+dy).coerceIn(y+minH,12f)
        var w=right-left
        var h=bottom-top
        if(kind=="qr"){val side=minOf(w,h);w=side;h=side}
        val nextX=if(leftCorner)right-w else left
        val nextY=if(topCorner)bottom-h else top
        return copy(x=nextX,y=nextY,width=w,height=h)
    }

    fun scaled(factor:Float):LabelLayer {
        if(locked||!factor.isFinite()||factor<=0f)return this
        val minWidth=if(kind=="qr")8.8f else 2f
        val minHeight=if(kind=="qr")8.8f else .8f
        val maxFactor=minOf((40f-x)/width,(12f-y)/height)
        val minFactor=maxOf(minWidth/width,minHeight/height,
            if(kind=="qr")0f else .65f/fontMm)
        if(maxFactor<minFactor)return this
        val scale=factor.coerceIn(minFactor,maxFactor)
        val proposedWidth=width*scale
        val proposedHeight=height*scale
        val side=if(kind=="qr")minOf(proposedWidth,proposedHeight)else 0f
        return copy(width=if(kind=="qr")side else proposedWidth,
            height=if(kind=="qr")side else proposedHeight,
            fontMm=if(kind=="qr")fontMm else (fontMm*scale).coerceIn(.65f,4f))
    }
}

data class LabelDesign(val layers:List<LabelLayer>,val dpi:Int=203) {
    companion object {
        val types=setOf("qr","plant","code","passage","medium","date","mother","note","text")
        fun initial()=LabelDesign(listOf(
            LabelLayer("qr","qr",.7f,.7f,10.3f,10.3f),
            LabelLayer("plant","plant",11.7f,.55f,27.5f,4.75f,2.05f,"Sans",true),
            LabelLayer("code","code",11.7f,6.35f,19.2f,2.05f,1.7f,"Mono",true),
            LabelLayer("passage","passage",31.0f,6.35f,8.2f,2.05f,1.7f),
            LabelLayer("medium","medium",11.7f,9.0f,27.5f,2.0f,1.35f)
        ))
        fun decode(raw:String):LabelDesign {
            require(raw.length<=30000){"Слишком большой макет"}
            val json=JSONObject(raw)
            require(json.optInt("version") in 1..2){"Неподдерживаемая версия редактора"}
            val dpi=json.optInt("dpi",203)
            require(dpi in LabLabel40.supportedDpi)
            val arr=json.getJSONArray("layers")
            require(arr.length() in 2..25){"От 2 до 25 элементов"}
            val layers=(0 until arr.length()).map{i->
                val o=arr.getJSONObject(i)
                LabelLayer(
                    id=o.getString("id"),kind=o.getString("kind"),
                    x=o.getDouble("x").toFloat(),y=o.getDouble("y").toFloat(),
                    width=o.getDouble("w").toFloat(),height=o.getDouble("h").toFloat(),
                    fontMm=o.optDouble("fontMm",1.65).toFloat(),
                    family=o.optString("family","Sans"),bold=o.optBoolean("bold",false),
                    italic=o.optBoolean("italic",false),
                    underline=o.optBoolean("underline",false),
                    letterSpacingMm=o.optDouble("letterSpacingMm",0.0).toFloat(),
                    locked=o.optBoolean("locked",false),
                    align=o.optString("align","Слева"),rotation=o.optInt("rotation",0),
                    text=o.optString("text",""),visible=o.optBoolean("visible",true)
                )
            }
            return LabelDesign(layers,dpi).also{it.verify()}
        }
    }
    fun verify() {
        require(dpi in LabLabel40.supportedDpi)
        require(layers.size in 2..25)
        require(layers.map{it.id}.distinct().size==layers.size){"Повторяется элемент"}
        require(layers.count{it.kind=="qr"&&it.visible}==1){"Нужен ровно один видимый QR"}
        require(layers.any{it.kind=="code"&&it.visible}){"Код контейнера должен быть видимым"}
        layers.forEach{l->
            require(l.kind in types){"Неизвестное поле: ${l.kind}"}
            require(l.id.length in 1..50 && l.text.length<=150)
            require(listOf(l.x,l.y,l.width,l.height,l.fontMm,l.letterSpacingMm).all{it.isFinite()})
            require(l.x>=0f && l.y>=0f && l.width>=.5f && l.height>=.5f)
            require(l.x+l.width<=40.001f && l.y+l.height<=12.001f){"Элемент выходит за край бумаги"}
            require(l.fontMm in .65f..4.0f)
            require(LabelFontCatalog.options.any{it.id==l.family}){"Выбранный шрифт недоступен"}
            require(l.letterSpacingMm in -.2f..0.8f)
            require(l.align in listOf("Слева","По центру","Справа"))
            require(l.rotation in listOf(0,90,180,270))
            if(l.kind=="qr")require(min(l.width,l.height)>=8.8f){"QR меньше 8,8 мм — затруднено сканирование"}
        }
    }
    fun encode():String {
        verify()
        return JSONObject().apply{
            put("version",2);put("dpi",dpi)
            put("layers",JSONArray().apply{layers.forEach{l->
                put(JSONObject().apply{
                    put("id",l.id);put("kind",l.kind);put("x",l.x);put("y",l.y)
                    put("w",l.width);put("h",l.height);put("fontMm",l.fontMm)
                    put("family",l.family);put("bold",l.bold);put("italic",l.italic)
                    put("underline",l.underline);put("letterSpacingMm",l.letterSpacingMm)
                    put("locked",l.locked);put("align",l.align)
                    put("rotation",l.rotation);put("text",l.text);put("visible",l.visible)
                })
            }})
        }.toString()
    }
    fun add(kind:String):LabelDesign {
        require(kind in types && kind!="qr" && kind!="code")
        require(layers.size<25){"Максимум 25 элементов"}
        return copy(layers=layers+LabelLayer(UUID.randomUUID().toString().take(12),
            kind,11.7f,8.7f,25f,2.0f,1.45f))
    }
    fun replace(layer:LabelLayer)=copy(layers=layers.map{if(it.id==layer.id)layer else it})
    fun remove(id:String)=copy(layers=layers.filterNot{
        it.id==id && it.kind !in setOf("qr","code")
    })
}

data class LabelValues(
    val code:String,val plant:String,val passage:Int,
    val medium:String,val date:String,val mother:String,val note:String
) {
    fun value(layer:LabelLayer):String=when(layer.kind){
        "plant"->plant
        "code"->code
        "passage"->"P$passage"
        "medium"->medium
        "date"->date
        "mother"->mother
        "note"->note
        "text"->layer.text
        else->""
    }
}

data class LabelTemplateInfo(val id:Long,val name:String,val design:LabelDesign)

object LabLabelDesigner {
    fun bitmap(values:LabelValues,design:LabelDesign):Bitmap {
        design.verify()
        require(LabQr.getCode(values.code)==values.code){"Некорректный код контейнера"}
        val (w,h)=LabLabel40.dimensions(design.dpi)
        val bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
        val c=Canvas(bitmap)
        c.drawColor(Color.WHITE)
        val mm=w/40f
        val black=Paint(Paint.ANTI_ALIAS_FLAG).apply{
            color=Color.BLACK;isSubpixelText=false
        }
        design.layers.filter{it.visible}.forEach{layer->
            val x=layer.x*mm;val y=layer.y*mm
            val boxW=layer.width*mm;val boxH=layer.height*mm
            c.save()
            c.clipRect(x,y,x+boxW,y+boxH)
            if(layer.rotation!=0)c.rotate(layer.rotation.toFloat(),x+boxW/2,y+boxH/2)
            if(layer.kind=="qr"){
                val side=min(boxW,boxH).roundToInt()
                val code=MultiFormatWriter().encode(values.code,BarcodeFormat.QR_CODE,
                    side,side,mapOf(EncodeHintType.MARGIN to 4,
                        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M))
                val left=(x+(boxW-side)/2).roundToInt()
                val top=(y+(boxH-side)/2).roundToInt()
                val qrPaint=Paint().apply{color=Color.BLACK;style=Paint.Style.FILL}
                for(yy in 0 until side)for(xx in 0 until side)if(code[xx,yy]){
                    c.drawRect((left+xx).toFloat(),(top+yy).toFloat(),
                        (left+xx+1).toFloat(),(top+yy+1).toFloat(),qrPaint)
                }
            }else {
                val raw=values.value(layer).trim()
                if(raw.isNotEmpty()){
                    val paint=Paint(black).apply{
                        typeface=LabelFontCatalog.typeface(layer.family,layer.bold,layer.italic)
                        textSize=layer.fontMm*mm
                        isUnderlineText=layer.underline
                        textAlign=Paint.Align.LEFT
                    }
                    val gap=layer.letterSpacingMm*mm
                    val horizontal=when(layer.align){
                        "По центру"->x+boxW/2
                        "Справа"->x+boxW-.1f*mm
                        else->x+.1f*mm
                    }
                    val availableLines=(layer.height/(layer.fontMm*1.22f)).toInt().coerceAtLeast(1).coerceAtMost(3)
                    val strings=wrap(raw,boxW-.3f*mm,paint,availableLines,gap)
                    strings.forEachIndexed{i,line->
                        val len=measure(line,paint,gap)
                        val position=when(layer.align){
                            "По центру"->horizontal-len/2
                            "Справа"->horizontal-len
                            else->horizontal
                        }
                        drawSpaced(c,line,position,y+(i+1)*layer.fontMm*mm*1.12f,paint,gap)
                    }
                }
            }
            c.restore()
        }
        return bitmap
    }

    private fun measure(text:String,paint:Paint,gap:Float):Float =
        paint.measureText(text)+(text.codePointCount(0,text.length)-1).coerceAtLeast(0)*gap

    private fun drawSpaced(canvas:Canvas,text:String,x:Float,y:Float,paint:Paint,gap:Float){
        if(gap==0f){canvas.drawText(text,x,y,paint);return}
        var cursor=x
        var i=0
        while(i<text.length){
            val cp=Character.codePointAt(text,i)
            val str=String(Character.toChars(cp))
            canvas.drawText(str,cursor,y,paint)
            cursor+=paint.measureText(str)+gap
            i+=Character.charCount(cp)
        }
    }

    private fun wrap(raw:String,width:Float,paint:Paint,lines:Int,gap:Float):List<String>{
        fun ellipsis(value:String):String{
            if(measure(value,paint,gap)<=width)return value
            var text=value
            while(text.isNotEmpty()&&measure(text+"…",paint,gap)>width)
                text=text.dropLast(1)
            return text.trimEnd()+"…"
        }
        if(lines==1)return listOf(ellipsis(raw))
        val result=mutableListOf<String>()
        val words=raw.split(Regex("\\s+")).toMutableList()
        while(words.isNotEmpty()&&result.size<lines){
            var next=words.removeAt(0)
            while(words.isNotEmpty()&&measure(next+" "+words.first(),paint,gap)<=width)
                next+=" "+words.removeAt(0)
            if(result.size==lines-1 && words.isNotEmpty())
                next+=" "+words.joinToString(" ")
            result+=ellipsis(next)
        }
        return result
    }

    fun png(values:LabelValues,design:LabelDesign):ByteArray =
        LabLabel40.png(bitmap(values,design),design.dpi)

    fun warnings(design:LabelDesign):List<String>{
        val result=mutableListOf<String>()
        val visible=design.layers.filter{it.visible}
        visible.forEachIndexed{i,a->
            if(a.kind!="qr" && a.fontMm<1.1f)
                result+="Очень мелкий текст: ${a.kind}"
            visible.drop(i+1).forEach{b->
                val overlap=a.x<b.x+b.width && b.x<a.x+a.width &&
                    a.y<b.y+b.height && b.y<a.y+a.height
                if(overlap)result+="Перекрываются элементы: ${a.kind}, ${b.kind}"
            }
        }
        return result.distinct()
    }
}

fun LabDatabase.labelTemplates080():List<LabelTemplateInfo> =
    readableDatabase.rawQuery("SELECT id,name,layout_json FROM label_templates ORDER BY name COLLATE NOCASE",null)
        .use{cursor->buildList{
            while(cursor.moveToNext()){
                val design=runCatching{LabelDesign.decode(cursor.getString(2))}.getOrNull()
                if(design!=null)add(LabelTemplateInfo(cursor.getLong(0),cursor.getString(1),design))
            }
        }}

fun LabDatabase.labelForCulture080(cultureId:Long):LabelDesign? =
    readableDatabase.rawQuery("SELECT layout_json FROM label_overrides WHERE culture_id=?",
        arrayOf(cultureId.toString())).use{c->
        if(c.moveToFirst())LabelDesign.decode(c.getString(0)) else null
    }

fun LabDatabase.saveLabel080(cultureId:Long,design:LabelDesign){
    val json=design.encode()
    val db=writableDatabase
    db.beginTransaction()
    try{
        require(db.rawQuery("SELECT 1 FROM cultures WHERE id=?",
            arrayOf(cultureId.toString())).use{it.moveToFirst()})
        val v=android.content.ContentValues().apply{
            put("culture_id",cultureId);put("layout_json",json)
            put("modified_at",System.currentTimeMillis())
        }
        db.insertWithOnConflict("label_overrides",null,v,SQLiteDatabase.CONFLICT_REPLACE)
        db.setTransactionSuccessful()
    } finally {db.endTransaction()}
}

fun LabDatabase.saveTemplate080(name:String,design:LabelDesign){
    val safe=name.trim()
    require(safe.length in 2..80){"Название шаблона — от 2 до 80 символов"}
    val v=android.content.ContentValues().apply{
        put("name",safe);put("layout_json",design.encode())
        put("created_at",System.currentTimeMillis())
        put("updated_at",System.currentTimeMillis())
    }
    writableDatabase.insertOrThrow("label_templates",null,v)
}

fun LabDatabase.archiveTemplate080(id:Long){
    val db=writableDatabase
    db.beginTransaction()
    try{
        val before=db.rawQuery("SELECT name,layout_json FROM label_templates WHERE id=?",
            arrayOf(id.toString())).use{c->
            require(c.moveToFirst()){"Шаблон не найден"}
            c.getString(0) to c.getString(1)
        }
        val values=android.content.ContentValues().apply{
            put("entity_type","label_template");put("entity_id",id)
            put("before_json",before.second);put("after_json","{}")
            put("reason","Удалён шаблон без изменения существующих этикеток")
            put("changed_at",System.currentTimeMillis())
        }
        db.insertOrThrow("entity_changes",null,values)
        db.delete("label_templates","id=?",arrayOf(id.toString()))
        db.setTransactionSuccessful()
    }finally{db.endTransaction()}
}
