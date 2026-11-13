package ru.dikoveriya.lab

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build

/**
 * Offline Android font variants. These are named system typeface/weight
 * combinations (not downloadable fonts); physical preview and export both
 * use this single resolver. No network dependency or unlicensed binary assets.
 *
 * The exact family face may depend on Android vendor. We don't claim twenty
 * independent embedded font families: these are 27 readable typographic options.
 */
data class LabelFontOption(val id:String,val title:String,val family:String,
    val weight:Int,val category:String)

object LabelFontCatalog {
    val options=listOf(
        LabelFontOption("gf:montserrat","Montserrat","montserrat",400,"Обычные"),
        LabelFontOption("gf:roboto","Roboto","roboto",400,"Обычные"),
        LabelFontOption("gf:opensans","Open Sans","opensans",400,"Обычные"),
        LabelFontOption("gf:inter","Inter","inter",400,"Обычные"),
        LabelFontOption("gf:rubik","Rubik","rubik",400,"Обычные"),
        LabelFontOption("gf:manrope","Manrope","manrope",400,"Обычные"),
        LabelFontOption("gf:raleway","Raleway","raleway",400,"Обычные"),
        LabelFontOption("gf:playfairdisplay","Playfair Display","playfairdisplay",400,"С засечками"),
        LabelFontOption("gf:lora","Lora","lora",400,"С засечками"),
        LabelFontOption("gf:nunito","Nunito","nunito",400,"Обычные"),
        LabelFontOption("gf:oswald","Oswald","oswald",400,"Узкие"),
        LabelFontOption("gf:ptsans","PT Sans","ptsans",400,"Обычные"),
        LabelFontOption("gf:ptserif","PT Serif","ptserif",400,"С засечками"),
        LabelFontOption("gf:ibmplexsans","IBM Plex Sans","ibmplexsans",400,"Обычные"),
        LabelFontOption("gf:ibmplexserif","IBM Plex Serif","ibmplexserif",400,"С засечками"),
        LabelFontOption("gf:ibmplexmono","IBM Plex Mono","ibmplexmono",400,"Узкие"),
        LabelFontOption("gf:firacode","Fira Code","firacode",400,"Узкие"),
        LabelFontOption("gf:jost","Jost","jost",400,"Обычные"),
        LabelFontOption("gf:merriweather","Merriweather","merriweather",400,"С засечками"),
        LabelFontOption("gf:yesevaone","Yeseva One","yesevaone",400,"Декоративные"),
        LabelFontOption("gf:comfortaa","Comfortaa","comfortaa",400,"Декоративные"),
        LabelFontOption("gf:cormorantgaramond","Cormorant Garamond","cormorantgaramond",400,"С засечками"),
        LabelFontOption("gf:philosopher","Philosopher","philosopher",400,"Декоративные"),
        LabelFontOption("gf:forum","Forum","forum",400,"С засечками"),
        LabelFontOption("gf:prata","Prata","prata",400,"С засечками"),
        LabelFontOption("gf:tenorsans","Tenor Sans","tenorsans",400,"Декоративные"),
        LabelFontOption("gf:spectral","Spectral","spectral",400,"С засечками"),
        LabelFontOption("gf:alegreya","Alegreya","alegreya",400,"С засечками"),
        LabelFontOption("Sans","Простой · обычный","sans-serif",400,"Обычные"),
        LabelFontOption("Sans Light","Простой · лёгкий","sans-serif",300,"Обычные"),
        LabelFontOption("Sans Thin","Простой · тонкий","sans-serif",100,"Обычные"),
        LabelFontOption("Sans Medium","Простой · средний","sans-serif",500,"Обычные"),
        LabelFontOption("Sans SemiBold","Простой · плотный","sans-serif",600,"Обычные"),
        LabelFontOption("Sans Bold","Простой · жирный","sans-serif",700,"Обычные"),
        LabelFontOption("Sans Black","Простой · очень жирный","sans-serif",900,"Обычные"),
        LabelFontOption("Sans Condensed","Узкий · обычный","sans-serif-condensed",400,"Узкие"),
        LabelFontOption("Sans Condensed Light","Узкий · лёгкий","sans-serif-condensed",300,"Узкие"),
        LabelFontOption("Sans Condensed Medium","Узкий · средний","sans-serif-condensed",500,"Узкие"),
        LabelFontOption("Sans Condensed Bold","Узкий · жирный","sans-serif-condensed",700,"Узкие"),
        LabelFontOption("Sans Condensed Black","Узкий · очень жирный","sans-serif-condensed",900,"Узкие"),
        LabelFontOption("Sans Rounded","Округлый","sans-serif-rounded",400,"Декоративные"),
        LabelFontOption("Sans Rounded Bold","Округлый · жирный","sans-serif-rounded",700,"Декоративные"),
        LabelFontOption("Sans Smallcaps","Малые прописные","sans-serif-smallcaps",400,"Декоративные"),
        LabelFontOption("Casual","Неформальный","casual",400,"Декоративные"),
        LabelFontOption("Cursive","Рукописный","cursive",400,"Декоративные"),
        LabelFontOption("Serif","С засечками · обычный","serif",400,"С засечками"),
        LabelFontOption("Serif Light","С засечками · лёгкий","serif",300,"С засечками"),
        LabelFontOption("Serif Medium","С засечками · средний","serif",500,"С засечками"),
        LabelFontOption("Serif Bold","С засечками · жирный","serif",700,"С засечками"),
        LabelFontOption("Serif Black","С засечками · очень жирный","serif",900,"С засечками"),
        LabelFontOption("Serif Display","Витринный с засечками","serif",600,"Декоративные"),
        LabelFontOption("Mono","Моноширинный","monospace",400,"Узкие"),
        LabelFontOption("Mono Light","Моноширинный · лёгкий","monospace",300,"Узкие"),
        LabelFontOption("Mono Bold","Моноширинный · жирный","monospace",700,"Узкие"),
        LabelFontOption("Sans Heavy Compact","Плотный компактный","sans-serif-condensed",800,"Узкие")
    )
    // 28 truly separate OFL font families are packaged at build time.
    // System variants remain for backwards compatibility with 0.8.0 labels.
    @Volatile private var assetManager:AssetManager?=null
    private val assetCache=java.util.concurrent.ConcurrentHashMap<String,Typeface>()
    fun initialize(context:Context){assetManager=context.applicationContext.assets}
    val embeddedFamilies=options.filter{it.id.startsWith("gf:")}
    val categories=listOf("Все","Обычные","Узкие","С засечками","Декоративные")
    fun option(id:String):LabelFontOption=options.firstOrNull{it.id==id}
        ?:when(id) {
            "Serif"->options.first{it.id=="Serif"}
            "Mono"->options.first{it.id=="Mono"}
            else->options.first()
        }
    fun typeface(id:String,bold:Boolean,italic:Boolean):Typeface{
        val spec=option(id)
        val base=if(id.startsWith("gf:")){
            assetCache[id]?:synchronized(assetCache){
                assetCache[id]?:run {
                    val resources=assetManager
                        ?:error("Шрифты приложения ещё не инициализированы")
                    Typeface.createFromAsset(resources,"fonts/"+spec.family+".ttf")
                        .also{assetCache[id]=it}
                }
            }
        } else Typeface.create(spec.family,Typeface.NORMAL)
        val weight=(spec.weight+if(bold)200 else 0).coerceAtMost(900)
        return if(Build.VERSION.SDK_INT>=28)Typeface.create(base,weight,italic)
        else Typeface.create(base,when {
            bold&&italic->Typeface.BOLD_ITALIC
            bold->Typeface.BOLD
            italic->Typeface.ITALIC
            else->Typeface.NORMAL
        })
    }
    fun cyrillicCovered(id:String):Boolean{
        val paint=Paint().apply{typeface=typeface(id,false,false);textSize=32f}
        return listOf("Ж","Ё","Щ","Ы","я").all{paint.hasGlyph(it)}
    }
}
