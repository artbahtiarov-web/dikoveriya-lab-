package ru.dikoveriya.lab

import android.app.Application

/** Fonts are shipped inside APK; no user internet connection is needed. */
class LabApp:Application(){
    override fun onCreate(){
        super.onCreate()
        LabelFontCatalog.initialize(this)
    }
}
