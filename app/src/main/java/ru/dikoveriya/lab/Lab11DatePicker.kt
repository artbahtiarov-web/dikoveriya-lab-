package ru.dikoveriya.lab

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

private val DATE_FORMAT_11=DateTimeFormatter.ofPattern("dd.MM.uuuu")
    .withResolverStyle(ResolverStyle.STRICT)

internal fun validDate11(text:String):LocalDate?=runCatching{
    LocalDate.parse(text,DATE_FORMAT_11)
}.getOrNull()

/** No free-text date entry: users always select a real Gregorian calendar day. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun Lab11DateField(
    title:String,
    value:String,
    onValue:(String)->Unit,
    allowClear:Boolean=false,
    modifier:Modifier=Modifier
){
    var opened by remember{mutableStateOf(false)}
    Row(modifier=modifier.fillMaxWidth()){
        OutlinedButton(onClick={opened=true},
            modifier=Modifier.weight(1f).heightIn(min=52.dp)){
            Icon(Icons.Outlined.CalendarMonth,contentDescription=null)
            Spacer(Modifier.width(9.dp))
            Text(title+": "+value.ifBlank{"Выбрать дату"},
                maxLines=2)
        }
        if(allowClear && value.isNotBlank()){
            IconButton(onClick={onValue("")}){
                Icon(Icons.Outlined.Close,contentDescription="Очистить дату")
            }
        }
    }
    if(opened){
        val start=validDate11(value)?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
        val state=rememberDatePickerState(initialSelectedDateMillis=start)
        DatePickerDialog(
            onDismissRequest={opened=false},
            confirmButton={
                TextButton(onClick={
                    state.selectedDateMillis?.let{ms->
                        val day=Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        onValue(day.format(DATE_FORMAT_11))
                        opened=false
                    }
                },enabled=state.selectedDateMillis!=null){Text("Выбрать")}
            },
            dismissButton={TextButton(onClick={opened=false}){Text("Отмена")}}
        ){
            DatePicker(state=state,showModeToggle=false)
        }
    }
}
