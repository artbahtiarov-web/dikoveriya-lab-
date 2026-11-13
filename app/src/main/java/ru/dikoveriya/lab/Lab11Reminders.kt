package ru.dikoveriya.lab

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * 1.1 local notifications — no remote service, hourly task or background
 * development automation. The user's own due dates are the only triggers.
 * Android may delay inexact battery-safe alarms. UI lists remain the source
 * of truth for overdue tasks, even when notifications are disabled.
 */
internal object Lab11Reminders {
    private const val CHANNEL = "lab_tasks_11"
    private const val ACTION = "ru.dikoveriya.lab.TASK_DUE_11"

    fun permitted(context:Context):Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED

    private fun request(context:Context,id:Long,title:String):PendingIntent {
        val intent=Intent(context,Lab11ReminderReceiver::class.java).apply{
            action=ACTION
            data=android.net.Uri.parse("dikoveriya://task/"+id)
            putExtra("id",id)
            putExtra("title",title.take(180))
        }
        return PendingIntent.getBroadcast(context,id.hashCode(),intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun dueReminderAt(dueAt:Long,zone:ZoneId=ZoneId.systemDefault()):Long{
        val date=Instant.ofEpochMilli(dueAt).atZone(zone).toLocalDate()
        return date.atTime(LocalTime.of(9,0)).atZone(zone).toInstant().toEpochMilli()
    }

    fun sync(context:Context,db:LabDatabase,now:Long=System.currentTimeMillis()):Int{
        if(!permitted(context))return 0
        val alarm=context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val rows=db.readableDatabase.rawQuery("""
            SELECT t.id,t.title,t.due_at
            FROM tasks t WHERE t.done=0
            AND t.due_at BETWEEN ? AND ?
            AND NOT EXISTS(SELECT 1 FROM entity_archives a
                WHERE a.entity_type='task' AND a.entity_id=t.id)
            ORDER BY t.due_at,t.id LIMIT 100
        """.trimIndent(),arrayOf(now.toString(),(now+180L*86400000L).toString()))
        var scheduled=0
        rows.use{c->
            while(c.moveToNext()){
                val id=c.getLong(0)
                val due=c.getLong(2)
                val time=dueReminderAt(due)
                if(time<=now)continue
                alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,time,
                    request(context,id,c.getString(1)))
                scheduled++
            }
        }
        return scheduled
    }
    fun cancel(context:Context,id:Long){
        val alarm=context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.cancel(request(context,id,""))
    }
    fun notify(context:Context,taskId:Long){
        if(!permitted(context))return
        val db=LabDatabase(context)
        val title=try{
            db.readableDatabase.rawQuery("""
                SELECT title,due_at FROM tasks WHERE id=? AND done=0
                AND NOT EXISTS(SELECT 1 FROM entity_archives a
                    WHERE a.entity_type='task' AND a.entity_id=tasks.id)
            """.trimIndent(),arrayOf(taskId.toString())).use{c->
                if(!c.moveToFirst())return
                c.getString(0)
            }
        }finally{db.close()}
        val manager=context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(CHANNEL,
            "Задачи и уход",NotificationManager.IMPORTANCE_DEFAULT))
        val launch=PendingIntent.getActivity(context,taskId.hashCode(),
            Intent(context,MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra("open_tasks",true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.notify(taskId.hashCode(),NotificationCompat.Builder(context,CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Диковерия LAB · запланированная задача")
            .setContentText(title.take(150))
            .setStyle(NotificationCompat.BigTextStyle().bigText(title))
            .setAutoCancel(true).setContentIntent(launch)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT).build())
    }
}

class Lab11ReminderReceiver:BroadcastReceiver(){
    override fun onReceive(context:Context,intent:Intent){
        if(intent.action!="ru.dikoveriya.lab.TASK_DUE_11")return
        val id=intent.getLongExtra("id",0L)
        if(id>0)Lab11Reminders.notify(context,id)
    }
}

class Lab11RescheduleReceiver:BroadcastReceiver(){
    override fun onReceive(context:Context,intent:Intent){
        if(intent.action!=Intent.ACTION_BOOT_COMPLETED &&
            intent.action!=Intent.ACTION_MY_PACKAGE_REPLACED)return
        // Receivers have short execution limits: reschedule only indexed,
        // bounded upcoming tasks; never run network operations.
        val db=LabDatabase(context)
        try{Lab11Reminders.sync(context,db)}
        finally{db.close()}
    }
}
