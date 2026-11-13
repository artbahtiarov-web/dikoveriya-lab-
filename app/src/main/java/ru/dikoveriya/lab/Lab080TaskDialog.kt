package ru.dikoveriya.lab

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Explicit task for one container: no fabricated date or default deadline. */
@Composable
fun Lab080TaskDialog(culture:CultureRecord,onClose:()->Unit,
    onSave:(String,Long,String,String)->Unit) {
    var title by rememberSaveable(culture.id){mutableStateOf("")}
    var date by rememberSaveable(culture.id){mutableStateOf("")}
    var priority by rememberSaveable(culture.id){mutableStateOf("Обычный")}
    var kind by rememberSaveable(culture.id){mutableStateOf("Осмотр")}
    val due=endOfDay072(date)
    AlertDialog(onDismissRequest=onClose,
        title={Text("Задача для ${culture.code}")},
        text={Column(Modifier.fillMaxWidth().heightIn(max=460.dp)
            .verticalScroll(rememberScrollState()),
            verticalArrangement=Arrangement.spacedBy(10.dp)){
            Text(culture.plantName,style=MaterialTheme.typography.bodyMedium)
            Field("Что нужно сделать",title,{title=it},single=false)
            Field("Когда выполнить, дд.мм.гггг",date,{date=it})
            if(date.isNotBlank() && due==null)
                Text("Укажи дату, например 18.11.2026",
                    color=MaterialTheme.colorScheme.error)
            PickOne("Важность",listOf("Низкий","Обычный","Высокий","Срочный"),
                priority,{priority=it})
            PickOne("Вид работы",listOf("Осмотр","Пересадка","Уход",
                "Подготовка среды","Другое"),kind,{kind=it})
            Text("Задача будет связана с этим контейнером и появится в рабочем списке. Срок не назначается автоматически.",
                style=MaterialTheme.typography.bodySmall)
        }},
        confirmButton={Button(enabled=title.trim().isNotEmpty() && due!=null,
            onClick={onSave(title,requireNotNull(due),priority,kind)}){
            Text("Назначить задачу")
        }},
        dismissButton={TextButton(onClick=onClose){Text("Отмена")}})
}
