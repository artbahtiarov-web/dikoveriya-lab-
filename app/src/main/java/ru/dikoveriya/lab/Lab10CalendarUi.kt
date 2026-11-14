package ru.dikoveriya.lab

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

internal data class Lab11DayTasks(val planned:Int=0,val done:Int=0,val overdue:Int=0,val soon:Int=0)
internal fun LabDatabase.taskMonth11(month:YearMonth,now:Long=System.currentTimeMillis()):Map<Int,Lab11DayTasks>{
    val rows=mutableMapOf<Int,Lab11DayTasks>()
    readableDatabase.rawQuery("""
        SELECT CAST(strftime('%d',t.due_at/1000,'unixepoch','localtime') AS INTEGER), t.done,t.due_at
        FROM tasks t WHERE strftime('%Y-%m',t.due_at/1000,'unixepoch','localtime')=?
          AND NOT EXISTS(SELECT 1 FROM entity_archives a
              WHERE a.entity_type='task' AND a.entity_id=t.id)
    """.trimIndent(),arrayOf(month.toString())).use{c->
        while(c.moveToNext()){
            val day=c.getInt(0)
            val old=rows[day]?:Lab11DayTasks()
            val due=c.getLong(2)
            rows[day]=if(c.getInt(1)==1)old.copy(done=old.done+1)
            else old.copy(planned=old.planned+1,
                overdue=old.overdue+if(due<now)1 else 0,
                soon=old.soon+if(due in now..(now+7*86400000L))1 else 0)
        }
    }
    return rows
}
fun LabDatabase.taskMonth10(month:YearMonth):Map<Int,Int> =
    taskMonth11(month).mapValues{it.value.planned}

@Composable
private fun CalendarDot11(color:Color) {
    Box(Modifier.size(5.dp).background(color,RoundedCornerShape(3.dp)))
}

/** Full calendar; ivory selected-date color has contrasting DeepForest ink. */
@Composable
internal fun Lab10TaskCalendar(db:LabDatabase,revision:Int,onDay:(String)->Unit,
    modifier:Modifier=Modifier
){
    var monthText by rememberSaveable{mutableStateOf(YearMonth.now().toString())}
    var selected by rememberSaveable{mutableStateOf("")}
    val month=remember(monthText){YearMonth.parse(monthText)}
    val counts=remember(revision,monthText){db.taskMonth11(month)}
    val offset=month.atDay(1).dayOfWeek.value-1
    val cellCount=((offset+month.lengthOfMonth()+6)/7)*7
    val ru=Locale("ru")
    val danger=Color(0xFFCF554F)
    val planned=Color(0xFF87918B)
    val soon=Color(0xFFD7A23B)
    val done=Color(0xFF51A577)
    Card(modifier=modifier.fillMaxWidth(),
        colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),
        shape=RoundedCornerShape(20.dp),
        border=BorderStroke(1.dp,Lab07Palette.Brass.copy(alpha=.33f))){
        Column(Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement=Arrangement.spacedBy(10.dp)){
            Row(verticalAlignment=Alignment.CenterVertically){
                Text("Календарь задач",style=MaterialTheme.typography.titleMedium,
                    modifier=Modifier.weight(1f))
                IconButton(onClick={monthText=month.minusMonths(1).toString()}){
                    Icon(Icons.Outlined.ChevronLeft,"Предыдущий месяц")
                }
                IconButton(onClick={
                    monthText=YearMonth.now().toString()
                    selected=""
                    onDay("")
                }){Icon(Icons.Outlined.Today,"Сегодня")}
                IconButton(onClick={monthText=month.plusMonths(1).toString()}){
                    Icon(Icons.Outlined.ChevronRight,"Следующий месяц")
                }
            }
            Text(month.month.getDisplayName(TextStyle.FULL_STANDALONE,ru)
                .replaceFirstChar{it.titlecase(ru)}+" "+month.year,
                style=MaterialTheme.typography.titleMedium)
            Row{
                listOf("Пн","Вт","Ср","Чт","Пт","Сб","Вс").forEach{d->
                    Text(d,modifier=Modifier.weight(1f),
                        style=MaterialTheme.typography.labelSmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            repeat(cellCount/7){week->
                Row(horizontalArrangement=Arrangement.spacedBy(3.dp)){
                    repeat(7){weekday->
                        val day=week*7+weekday-offset+1
                        if(day !in 1..month.lengthOfMonth())
                            Spacer(Modifier.weight(1f).height(52.dp))
                        else{
                            val date=month.atDay(day)
                            val label=date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
                            val isSelected=selected==label
                            val isToday=date==LocalDate.now()
                            val stats=counts[day]?:Lab11DayTasks()
                            Surface(
                                modifier=Modifier.weight(1f).heightIn(min=52.dp)
                                    .semantics{
                                        contentDescription=label+", незавершённых "+stats.planned+
                                            ", выполненных "+stats.done+
                                            ", просроченных "+stats.overdue
                                    }
                                    .clickable {
                                        // Repeated taps on the same date also create a new task.
                                        // "Сегодня" above is the explicit way to clear selection.
                                        selected=label
                                        onDay(label)
                                    },
                                shape=RoundedCornerShape(10.dp),
                                color=when{
                                    isSelected->Lab07Palette.Forest
                                    isToday->Lab07Palette.Champagne
                                    else->Color.Transparent
                                }
                            ){
                                Column(Modifier.padding(vertical=3.dp),
                                    verticalArrangement=Arrangement.Center,
                                    horizontalAlignment=Alignment.CenterHorizontally){
                                    Text(day.toString(),style=MaterialTheme.typography.labelLarge,
                                        color=when{
                                            isSelected->Color.White
                                            isToday->Lab07Palette.DeepForest
                                            else->MaterialTheme.colorScheme.onSurface
                                        })
                                    Row(Modifier.height(7.dp),
                                        horizontalArrangement=Arrangement.spacedBy(2.dp)){
                                        if(stats.overdue>0)CalendarDot11(danger)
                                        if(stats.soon>0)CalendarDot11(soon)
                                        if(stats.planned-stats.overdue-stats.soon>0)CalendarDot11(planned)
                                        if(stats.done>0)CalendarDot11(done)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Text("Нажмите на дату, чтобы добавить задачу. В списке ниже останутся задачи этого дня.",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement=Arrangement.spacedBy(12.dp)){
                listOf(planned to "План",soon to "Скоро",danger to "Просрочено",
                    done to "Выполнено").forEach{(color,name)->
                    Row(verticalAlignment=Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.spacedBy(4.dp)){
                        CalendarDot11(color)
                        Text(name,style=MaterialTheme.typography.labelSmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
