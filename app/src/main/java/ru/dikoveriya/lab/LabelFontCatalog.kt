package ru.dikoveriya.lab

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
    val categories=listOf("Все","Обычные","Узкие","С засечками","Декоративные")
    fun option(id:String):LabelFontOption=options.firstOrNull{it.id==id}
        ?:when(id) {
            "Serif"->options.first{it.id=="Serif"}
            "Mono"->options.first{it.id=="Mono"}
            else->options.first()
        }
    fun typeface(id:String,bold:Boolean,italic:Boolean):Typeface{
        val spec=option(id)
        val base=Typeface.create(spec.family,Typeface.NORMAL)
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
