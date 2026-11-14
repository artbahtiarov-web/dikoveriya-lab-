package ru.dikoveriya.lab

import android.app.TimePickerDialog
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal fun reminderMoment13(date:String,hourText:String):Long?{
    val d=validDate11(date)?:return null
    val pair=hourText.split(":")
    if(pair.size!=2)return null
    val h=pair[0].toIntOrNull()?:return null
    val m=pair[1].toIntOrNull()?:return null
    return if(h in 0..23 && m in 0..59)taskMoment13(d,h,m) else null
}
internal fun reminderTimeText13(at:Long):String=
    Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("HH:mm"))

/** System picker returns a content URI, never a copied private ringtone file. */
@Composable
internal fun Lab13ReminderControls(
    day:String,enabled:Boolean,onEnabled:(Boolean)->Unit,
    at:String,onAt:(String)->Unit,
    sound:String,onSound:(String)->Unit
){
    val context=LocalContext.current
    val picker=rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()){result->
        if(result.resultCode==android.app.Activity.RESULT_OK){
            @Suppress("DEPRECATION")
            val selected=result.data?.getParcelableExtra<Uri>(
                RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            onSound(selected?.toString()?:"silent")
        }
    }
    Row(verticalAlignment=Alignment.CenterVertically){
        Switch(checked=enabled,onCheckedChange=onEnabled)
        Spacer(Modifier.width(10.dp))
        Text("Напоминание",style=MaterialTheme.typography.titleMedium)
    }
    if(enabled){
        OutlinedButton(onClick={
            val parts=at.split(":")
            val h=parts.getOrNull(0)?.toIntOrNull()?:9
            val m=parts.getOrNull(1)?.toIntOrNull()?:0
            TimePickerDialog(context,{_,hh,mm->
                onAt("%02d:%02d".format(hh,mm))
            },h,m,true).show()
        },modifier=Modifier.fillMaxWidth()){
            Text("Время: "+at+"  ·  изменить")
        }
        val label=remember(sound){
            when(sound){
                ""->"Звук системы по умолчанию"
                "silent"->"Без звука"
                else->runCatching{
                    RingtoneManager.getRingtone(context,Uri.parse(sound))
                        ?.getTitle(context)?: "Выбранный звук"
                }.getOrDefault("Выбранный звук")
            }
        }
        OutlinedButton(onClick={
            val current=when(sound){
                ""->RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                "silent"->null
                else->Uri.parse(sound)
            }
            picker.launch(Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply{
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE,
                    RingtoneManager.TYPE_NOTIFICATION)
                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE,
                    "Звук напоминания Диковерия LAB")
                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,current)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT,true)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT,true)
            })
        },modifier=Modifier.fillMaxWidth()){
            Text("Мелодия: "+label)
        }
        if(reminderMoment13(day,at)==null){
            Text("Выбери корректное время напоминания",
                color=MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
internal fun Lab13ReminderEditDialog(
    db:LabDatabase,taskId:Long,
    onDismiss:()->Unit,onSaved:()->Unit,onError:(String)->Unit
){
    val detail=remember(taskId){db.taskDetails13(taskId)}
    val due=remember(taskId){db.readableDatabase.rawQuery(
        "SELECT due_at FROM tasks WHERE id=?",arrayOf(taskId.toString())
    ).use{c->require(c.moveToFirst());c.getLong(0)}}
    val day=Instant.ofEpochMilli(due).atZone(ZoneId.systemDefault()).toLocalDate()
        .format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
    var enabled by remember(taskId){mutableStateOf(detail?.reminderAt!=null ||
        detail==null)}
    var time by remember(taskId){mutableStateOf(detail?.reminderAt?.let(::reminderTimeText13)?:"09:00")}
    var sound by remember(taskId){mutableStateOf(detail?.ringtoneUri.orEmpty())}
    AlertDialog(onDismissRequest=onDismiss,title={Text("Напоминание о задаче")},
        text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
            Text("Дата: "+day)
            Lab13ReminderControls(day,enabled,{enabled=it},time,{time=it},
                sound,{sound=it})
            Text("Android может немного задерживать уведомления из-за "+
                "энергосбережения. Выбранная мелодия применяется только к "+
                "этому типу звукового канала.",
                style=MaterialTheme.typography.bodySmall)
        }},
        confirmButton={Button(enabled=!enabled || reminderMoment13(day,time)!=null,
            onClick={
                try{
                    db.editTaskReminder13(taskId,
                        if(enabled)requireNotNull(reminderMoment13(day,time)) else null,
                        sound.takeIf{it.isNotBlank()})
                    onSaved()
                }catch(e:Exception){onError(e.message?:"Не удалось изменить напоминание")}
            }){Text("Сохранить")},
        },
        dismissButton={TextButton(onClick=onDismiss){Text("Отмена")}}
    )
}
