package ru.dikoveriya.lab

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Applied filters are immutable until the explicit Apply action. */
internal data class Lab10RegistryFilterValues(
    val plantId:Long?,val recipeId:Long?,val groupId:Long?,val locationId:Long?,
    val stage:String,val status:String,val period:String,val order:String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun Lab10RegistryFilterSheet(
    initial:Lab10RegistryFilterValues,
    plants:List<PlantRecord>,recipes:List<RecipeRecord>,
    groups:List<Pair<Long,String>>,locations:List<Pair<Long,String>>,
    savedNames:List<String>,
    onSelectSaved:(String)->Unit,
    onSavePreset:()->Unit,
    onApply:(Lab10RegistryFilterValues)->Unit,
    onDismiss:()->Unit
) {
    var plantId by remember{mutableStateOf(initial.plantId)}
    var recipeId by remember{mutableStateOf(initial.recipeId)}
    var groupId by remember{mutableStateOf(initial.groupId)}
    var locationId by remember{mutableStateOf(initial.locationId)}
    var stage by remember{mutableStateOf(initial.stage)}
    var status by remember{mutableStateOf(initial.status)}
    var period by remember{mutableStateOf(initial.period)}
    var order by remember{mutableStateOf(initial.order)}
    ModalBottomSheet(onDismissRequest=onDismiss,
        containerColor=MaterialTheme.colorScheme.surface){
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal=20.dp,vertical=12.dp),
            verticalArrangement=Arrangement.spacedBy(11.dp)){
            Text("Фильтры",style=MaterialTheme.typography.headlineSmall)
            Text("Выбранные значения применятся только после нажатия кнопки.",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
            PickOne("Сорт",listOf("Все")+plants.map{it.name},
                plants.firstOrNull{it.id==plantId}?.name?:"Все",{value->
                    plantId=plants.firstOrNull{it.name==value}?.id
                })
            PickOne("Среда",listOf("Все")+recipes.map{it.name+" · v"+it.version},
                recipes.firstOrNull{it.id==recipeId}?.let{it.name+" · v"+it.version}?:"Все",
                {value->recipeId=recipes.firstOrNull{it.name+" · v"+it.version==value}?.id})
            PickOne("Этап",listOf("Все")+LabTerms.stages,
                stage.ifBlank{"Все"},{stage=if(it=="Все")"" else it})
            PickOne("Состояние",listOf("Все")+LabTerms.statuses,
                status.ifBlank{"Все"},{status=if(it=="Все")"" else it})
            PickOne("Группа",listOf("Все")+groups.map{it.second},
                groups.firstOrNull{it.first==groupId}?.second?:"Все",
                {label->groupId=groups.firstOrNull{it.second==label}?.first})
            PickOne("Место",listOf("Все")+locations.map{it.second},
                locations.firstOrNull{it.first==locationId}?.second?:"Все",
                {label->locationId=locations.firstOrNull{it.second==label}?.first})
            PickOne("Период",listOf("Всё время","7 дней","30 дней"),
                period,{period=it})
            PickOne("Сортировка",listOf("Новые","Старые","Название","Место",
                "Последний осмотр","По побегам","По растениям"),
                order,{order=it})
            Text("Для сортировки по побегам и растениям используются только зарегистрированные измерения. NULL не считается нулём.",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(savedNames.isNotEmpty()){
                PickOne("Мои наборы фильтров",listOf("Выбрать")+savedNames,"Выбрать",
                    {name->if(name!="Выбрать")onSelectSaved(name)})
            }
            Row(horizontalArrangement=Arrangement.spacedBy(9.dp)){
                OutlinedButton(onClick={
                    plantId=null;recipeId=null;groupId=null;locationId=null
                    stage="";status="";period="Всё время";order="Новые"
                },modifier=Modifier.weight(1f)){Text("Сбросить")}
                OutlinedButton(onClick=onSavePreset,modifier=Modifier.weight(1f)){
                    Text("Сохранить набор")
                }
            }
            Button(onClick={
                onApply(Lab10RegistryFilterValues(
                    plantId,recipeId,groupId,locationId,stage,status,period,order))
            },modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){
                Text("Применить")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
