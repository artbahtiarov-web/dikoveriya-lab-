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
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Day counts use the actual persisted task due dates and the phone timezone. */
fun LabDatabase.taskMonth10(month:YearMonth):Map<Int,Int>{
    val key=month.toString()
    return readableDatabase.rawQuery("""
        SELECT CAST(strftime('%d',due_at/1000,'unixepoch','localtime') AS INTEGER),
               COUNT(*)
        FROM tasks
        WHERE done=0
          AND strftime('%Y-%m',due_at/1000,'unixepoch','localtime')=?
        GROUP BY 1
    """.trimIndent(),arrayOf(key)).use{c->buildMap{
        while(c.moveToNext())put(c.getInt(0),c.getInt(1))
    }}
}

@Composable
internal fun Lab10TaskCalendar(
    db:LabDatabase,revision:Int,
    onDay:(String)->Unit,
    modifier:Modifier=Modifier
){
    var monthText by rememberSaveable{mutableStateOf(YearMonth.now().toString())}
    var selected by rememberSaveable{mutableStateOf("")}
    val month=remember(monthText){YearMonth.parse(monthText)}
    val counts=remember(revision,monthText){db.taskMonth10(month)}
    val start=month.atDay(1)
    val offset=start.dayOfWeek.value-1
    val cells=((offset+month.lengthOfMonth()+6)/7)*7
    val ru=Locale("ru")
    Card(modifier=modifier.fillMaxWidth(),
        colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),
        shape=RoundedCornerShape(20.dp),
        border=BorderStroke(1.dp,Lab07Palette.Brass.copy(alpha=.33f))){
        Column(Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement=Arrangement.spacedBy(9.dp)){
            Row(verticalAlignment=Alignment.CenterVertically){
                Text("Календарь задач",style=MaterialTheme.typography.titleMedium,
                    modifier=Modifier.weight(1f))
                IconButton(onClick={monthText=month.minusMonths(1).toString()}){
                    Icon(Icons.Outlined.ChevronLeft,"Предыдущий месяц")
                }
                IconButton(onClick={monthText=month.plusMonths(1).toString()}){
                    Icon(Icons.Outlined.ChevronRight,"Следующий месяц")
                }
            }
            Text(month.month.getDisplayName(TextStyle.FULL_STANDALONE,ru)
                .replaceFirstChar{it.titlecase(ru)}+" "+month.year,
                style=MaterialTheme.typography.labelLarge,color=Lab07Palette.Forest)
            Row{
                listOf("Пн","Вт","Ср","Чт","Пт","Сб","Вс").forEach{d->
                    Text(d,modifier=Modifier.weight(1f),style=MaterialTheme.typography.labelSmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            repeat(cells/7){week->
                Row(horizontalArrangement=Arrangement.spacedBy(3.dp)){
                    repeat(7){weekday->
                        val n=week*7+weekday-offset+1
                        if(n !in 1..month.lengthOfMonth()){
                            Spacer(Modifier.weight(1f).height(47.dp))
                        }else{
                            val date=month.atDay(n)
                            val isToday=date==LocalDate.now()
                            val selectedDate=date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
                            val isSelected=selected==selectedDate
                            Surface(
                                modifier=Modifier.weight(1f).height(47.dp)
                                    .clickable{
                                        selected=selectedDate
                                        onDay(selectedDate)
                                    },
                                shape=RoundedCornerShape(10.dp),
                                color=when{
                                    isSelected->Lab07Palette.Forest
                                    isToday->Lab07Palette.Champagne
                                    else->Color.Transparent
                                }
                            ){
                                Column(horizontalAlignment=Alignment.CenterHorizontally,
                                    verticalArrangement=Arrangement.Center){
                                    Text(n.toString(),style=MaterialTheme.typography.labelLarge,
                                        color=if(isSelected)Color.White
                                            else MaterialTheme.colorScheme.onSurface)
                                    if((counts[n]?:0)>0)
                                        Box(Modifier.size(5.dp).background(
                                            if(isSelected)Lab07Palette.Brass else Lab07Palette.Moss,
                                            RoundedCornerShape(3.dp)))
                                }
                            }
                        }
                    }
                }
            }
            Text("Точка под датой — незавершённые задачи. Нажми на день, чтобы увидеть список.",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
