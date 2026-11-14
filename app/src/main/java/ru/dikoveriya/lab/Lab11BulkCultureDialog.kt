package ru.dikoveriya.lab

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** No partially created containers: database method commits N IDs and group together. */
@Composable
internal fun Lab11BulkCultureDialog(
    db:LabDatabase,plants:List<PlantRecord>,recipes:List<RecipeRecord>,
    onClose:()->Unit,onSaved:(Long?)->Unit,onError:(String)->Unit
){
    var mode by rememberSaveable{mutableStateOf("Новая партия")}
    var plantId by rememberSaveable{mutableStateOf<Long?>(null)}
    var sourceCode by rememberSaveable{mutableStateOf("")}
    var count by rememberSaveable{mutableStateOf("5")}
    var each by rememberSaveable{mutableStateOf("1")}
    var different by rememberSaveable{mutableStateOf(false)}
    var distribution by rememberSaveable{mutableStateOf("")}
    var sourceUsed by rememberSaveable{mutableStateOf("1")}
    var lost by rememberSaveable{mutableStateOf("0")}
    var kind by rememberSaveable{mutableStateOf("Побеги")}
    var passage by rememberSaveable{mutableStateOf("0")}
    var stage by rememberSaveable{mutableStateOf(LabTerms.IN_VITRO)}
    var recipeId by rememberSaveable{mutableStateOf<Long?>(null)}
    var motherId by rememberSaveable{mutableStateOf<Long?>(null)}
    var locationId by rememberSaveable{mutableStateOf<Long?>(null)}
    var date by rememberSaveable{mutableStateOf(dateText11())}
    var note by rememberSaveable{mutableStateOf("")}
    val n=count.toIntOrNull()
    val quantities=remember(count,each,different,distribution){
        if(n==null || n !in 1..200)null
        else if(!different){
            val q=each.toIntOrNull()
            if(q==null||q !in 1..1000000)null else List(n){q}
        }else{
            val parts=distribution.replace(';',',').split(',').map{it.trim().toIntOrNull()}
            if(parts.size!=n || parts.any{it==null || it !in 1..1000000})null
            else parts.map{requireNotNull(it)}
        }
    }
    val locations=remember{db.locations072()}
    val mothers=remember{db.mothers()}
    val activeRecipes=recipes.filter{it.status=="Утверждена" &&
        (plantId==null||it.plantId==null||it.plantId==plantId)}
    val availableMothers=mothers.filter{it.plantId==plantId}
    AlertDialog(onDismissRequest=onClose,title={Text("Создать контейнеры")},
        text={Column(Modifier.heightIn(max=550.dp).verticalScroll(rememberScrollState()),
            verticalArrangement=Arrangement.spacedBy(10.dp)){
            PickOne("Источник",listOf("Новая партия","Из исходного контейнера"),
                mode,{mode=it})
            if(mode=="Из исходного контейнера"){
                Field("Код родителя LAB-000001",sourceCode,{sourceCode=it})
                Field("Использовано единиц на дочерний контейнер",sourceUsed,{sourceUsed=it})
                Field("Потери при разделении",lost,{lost=it})
                Text("Система проверит остаток источника и запишет родственные связи.",
                    style=MaterialTheme.typography.bodySmall)
            }else{
                PickOne("Сорт",listOf("Выбрать")+plants.map{it.name},
                    plants.firstOrNull{it.id==plantId}?.name?:"Выбрать",
                    {v->plantId=plants.firstOrNull{it.name==v}?.id})
                PickOne("Маточник (необязательно)",
                    listOf("Не указан")+availableMothers.map{it.code},
                    availableMothers.firstOrNull{it.id==motherId}?.code?:"Не указан",
                    {v->motherId=availableMothers.firstOrNull{it.code==v}?.id})
            }
            Field("Число контейнеров (1–200)",count,{count=it})
            Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
                Checkbox(different,{different=it})
                Text("Разное количество по контейнерам")
            }
            if(different)
                Field("Количество через запятую, по одному на контейнер",
                    distribution,{distribution=it},single=false)
            else Field("Количество в каждом контейнере",each,{each=it})
            PickOne("Единица учёта",LabTerms.quantityKinds.drop(1),
                kind,{kind=it})
            PickOne("Этап",LabTerms.stages,stage,{stage=it})
            if(mode=="Новая партия")
                Field("Пассаж (0–999)",passage,{passage=it})
            PickOne("Среда (необязательно)",
                listOf("Не указана")+activeRecipes.map{it.name+" · v"+it.version},
                activeRecipes.firstOrNull{it.id==recipeId}?.let{it.name+" · v"+it.version}
                    ?:"Не указана",
                {label->recipeId=activeRecipes.firstOrNull{
                    it.name+" · v"+it.version==label}?.id})
            PickOne("Место размещения (необязательно)",
                listOf("Не назначено")+locations.map{it.path},
                locations.firstOrNull{it.id==locationId}?.path?:"Не назначено",
                {label->locationId=locations.firstOrNull{it.path==label}?.id})
            Lab11DateField("Дата создания",date,{date=it})
            Field("Примечание",note,{note=it},single=false)
            if(quantities!=null){
                Text("К созданию: "+quantities.size+" контейнеров, всего "+
                    quantities.sumOf{it.toLong()}+" "+kind.lowercase(),
                    style=MaterialTheme.typography.titleSmall)
            }else Text("Проверь количество контейнеров и распределение.",
                color=MaterialTheme.colorScheme.error)
        }},
        confirmButton={Button(enabled=quantities!=null &&
            atDate11(date)!=null &&
            (mode=="Из исходного контейнера" || plantId!=null),
            onClick={
                try{
                    val q=requireNotNull(quantities)
                    val dateAt=requireNotNull(atDate11(date))
                    if(mode=="Из исходного контейнера"){
                        require(q.size<=25){"Одна пересадка допускает до 25 дочерних контейнеров"}
                        val source=db.findCulture072(sourceCode.trim())
                            ?:error("Исходный контейнер не найден")
                        val used=sourceUsed.toIntOrNull()?:error("Укажи использование источника")
                        db.splitCulturesBulk11(source,List(q.size){used},q,
                            stage,kind,recipeId,
                            locations.firstOrNull{it.id==locationId}?.path,
                            lost.toIntOrNull()?:error("Потери должны быть числом"),
                            note,dateAt)
                        onSaved(source)
                    }else{
                        val ids=db.createCulturesBulk11(requireNotNull(plantId),q,kind,
                            passage.toIntOrNull()?:error("Проверь пассаж"),stage,
                            recipeId,motherId,locationId,"",dateAt,note)
                        onSaved(ids.firstOrNull())
                    }
                }catch(e:Exception){onError(e.message?:"Не удалось создать партию")}
            }){Text("Создать партию")}},
        dismissButton={TextButton(onClick=onClose){Text("Отмена")}})
}
