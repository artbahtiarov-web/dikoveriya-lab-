package ru.dikoveriya.lab

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

internal data class Lab10TaskFilters(
    val priority:String,val kind:String,val plantId:Long?,val recipeId:Long?,
    val fromText:String,val toText:String,val order:String,val period:String="Все"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun Lab10TaskFilterSheet(
    initial:Lab10TaskFilters,
    plants:List<PlantRecord>,recipes:List<RecipeRecord>,
    savedNames:List<String>,onSelectSaved:(String)->Unit,
    onSave:()->Unit,onApply:(Lab10TaskFilters)->Unit,onDismiss:()->Unit
){
    var priority by remember{mutableStateOf(initial.priority)}
    var kind by remember{mutableStateOf(initial.kind)}
    var plantId by remember{mutableStateOf(initial.plantId)}
    var recipeId by remember{mutableStateOf(initial.recipeId)}
    var from by remember{mutableStateOf(initial.fromText)}
    var to by remember{mutableStateOf(initial.toText)}
    var order by remember{mutableStateOf(initial.order)}
    var period by remember{mutableStateOf(initial.period)}
    var invalid by remember{mutableStateOf(false)}
    ModalBottomSheet(onDismissRequest=onDismiss,
        containerColor=MaterialTheme.colorScheme.surface){
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal=20.dp,vertical=12.dp),
            verticalArrangement=Arrangement.spacedBy(10.dp)){
            Text("Фильтры задач",style=MaterialTheme.typography.headlineSmall)
            Text("Изменения применятся после подтверждения",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
            PickOne("Состояние и срок",listOf("Все","Открытые","Сегодня","Ближайшие 7 дней","Просрочено","Выполнено","Отменённые"),
                period,{period=it})
            PickOne("Приоритет",listOf("Все","Не задан","Низкий","Обычный","Высокий","Срочный"),
                priority.ifBlank{"Все"},{priority=if(it=="Все")"" else it})
            PickOne("Тип работы",listOf("Все","Осмотр","Пересадка","Уход","Подготовка среды","Другое"),
                kind.ifBlank{"Все"},{kind=if(it=="Все")"" else it})
            PickOne("Растение",listOf("Все")+plants.map{it.name},
                plants.firstOrNull{it.id==plantId}?.name?:"Все",
                {plantId=plants.firstOrNull{p->p.name==it}?.id})
            PickOne("Среда",listOf("Все")+recipes.map{it.name+" · v"+it.version},
                recipes.firstOrNull{it.id==recipeId}?.let{it.name+" · v"+it.version}?:"Все",
                {v->recipeId=recipes.firstOrNull{it.name+" · v"+it.version==v}?.id})
            Field("Срок с (дд.мм.гггг)",from,{from=it;invalid=false})
            Field("Срок по (дд.мм.гггг)",to,{to=it;invalid=false})
            PickOne("Порядок",listOf("Ближайшие","Важные","Поздние"),order,{order=it})
            if(invalid)Text("Неверный диапазон дат. Проверь формат и порядок.",
                color=MaterialTheme.colorScheme.error)
            if(savedNames.isNotEmpty())PickOne("Сохранённые фильтры",
                listOf("Выбрать")+savedNames,"Выбрать",
                {v->if(v!="Выбрать")onSelectSaved(v)})
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                OutlinedButton(onClick={
                    priority="";kind="";plantId=null;recipeId=null
                    from="";to="";order="Ближайшие";period="Все";invalid=false
                },modifier=Modifier.weight(1f)){Text("Сбросить")}
                OutlinedButton(onClick=onSave,modifier=Modifier.weight(1f)){
                    Text("Сохранить набор")
                }
            }
            Button(onClick={
                val f=calendarDay072(from)
                val t=calendarDay072(to)
                invalid=(from.isNotBlank()&&f==null)||(to.isNotBlank()&&t==null)||
                    (f!=null&&t!=null&&f>t)
                if(!invalid)onApply(Lab10TaskFilters(
                    priority,kind,plantId,recipeId,from,to,order,period))
            },modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){
                Text("Применить")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
