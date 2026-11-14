package ru.dikoveriya.lab

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * A photo chosen inside a care form is only staged in memory until Save.
 * Photos, event, stock movements and direct costs share one outer SQLite
 * transaction. If an action fails, its new photo files are also removed.
 */
internal fun LabDatabase.commitItemActionPhotos11(
    context:Context,
    itemId:Long,
    pending:Map<String,Uri>,
    action:(Map<String,String>)->Long
):Long{
    require(pending.size<=12)
    require(pending.keys.all{it.length in 2..100})
    if(pending.isEmpty())return action(emptyMap())
    val db=writableDatabase
    val created=mutableListOf<String>()
    var committed=false
    db.beginTransaction()
    try{
        val resolved=linkedMapOf<String,String>()
        pending.forEach{(label,uri)->
            val filename=addItemPhoto11(context,itemId,uri,"Операция: "+label)
            created+=filename
            resolved[label]=filename
        }
        val event=action(resolved)
        db.setTransactionSuccessful()
        committed=true
        return event
    }finally{
        try{
            db.endTransaction()
        }finally{
            if(!committed){
                val directory=File(context.filesDir,"culture_photos")
                created.forEach{file->File(directory,file).delete()}
            }
        }
    }
}
