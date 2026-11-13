package ru.dikoveriya.lab

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun Lab080IngredientDialog(
    ingredient:IngredientEntry,onClose:()->Unit,
    onSave:(String,String,String,String,String)->Unit,
    onArchive:(String)->Unit
){
    var name by rememberSaveable(ingredient.id){mutableStateOf(ingredient.name)}
    var category by rememberSaveable(ingredient.id){mutableStateOf(ingredient.category)}
    var unit by rememberSaveable(ingredient.id){mutableStateOf(ingredient.unit)}
    var notes by rememberSaveable(ingredient.id){mutableStateOf(ingredient.notes)}
    var reason by rememberSaveable(ingredient.id){mutableStateOf("")}
    var archive by rememberSaveable(ingredient.id){mutableStateOf(false)}
    if(archive){
        AlertDialog(onDismissRequest={archive=false},
            title={Text("Убрать компонент из каталога?")},
            text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
                Text("Исторические рецептуры не изменятся. Компонент перестанет предлагаться при создании новых.")
                Field("Почему архивируется",reason,{reason=it},single=false)
            }},
            confirmButton={Button(enabled=reason.trim().length>=2,
                onClick={onArchive(reason)}){Text("Архивировать")}},
            dismissButton={TextButton(onClick={archive=false}){Text("Отмена")}})
    }else AlertDialog(onDismissRequest=onClose,
        title={Text("Компонент рецептуры")},
        text={Column(Modifier.heightIn(max=470.dp).verticalScroll(rememberScrollState()),
            verticalArrangement=Arrangement.spacedBy(8.dp)){
            Field("Название",name,{name=it})
            PickOne("Категория",listOf("Базовая среда","Углеводы","Загуститель",
                "Регулятор роста","Минералы","Витамины","Добавка","Другое"),
                category,{category=it})
            PickOne("Единица",listOf("г/л","мг/л","мл/л","мг/л из раствора"),
                unit,{unit=it})
            Field("Описание",notes,{notes=it},single=false)
            Field("Причина изменения",reason,{reason=it},single=false)
            OutlinedButton(onClick={archive=true},
                modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){
                Icon(Icons.Outlined.Inventory2,null)
                Spacer(Modifier.width(7.dp));Text("Архивировать компонент")
            }
            Text("Сохранённые версии рецептур и история приготовления не изменяются.",
                style=MaterialTheme.typography.bodySmall)
        }},
        confirmButton={Button(enabled=name.trim().length>=2 &&
            reason.trim().length>=2,onClick={onSave(name,category,unit,notes,reason)}){
            Text("Сохранить изменения")
        }},
        dismissButton={TextButton(onClick=onClose){Text("Закрыть")}})
}

@Composable
fun Lab080PlantDialog(plant:PlantRecord,onClose:()->Unit,
    onSave:(String,String,String)->Unit,onArchive:(String)->Unit){
    var name by rememberSaveable(plant.id){mutableStateOf(plant.name)}
    var notes by rememberSaveable(plant.id){mutableStateOf(plant.description)}
    var reason by rememberSaveable(plant.id){mutableStateOf("")}
    var confirm by rememberSaveable(plant.id){mutableStateOf(false)}
    if(confirm)AlertDialog(onDismissRequest={confirm=false},
        title={Text("Архивировать неиспользуемый сорт?")},
        text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text("Архивирование разрешено только без контейнеров, маточников, рецептур и производственных групп. Историю приложение не удаляет.")
            Field("Причина архивирования",reason,{reason=it},single=false)
        }},
        confirmButton={Button(enabled=reason.trim().length>=2,
            onClick={onArchive(reason)}){Text("Архивировать")}},
        dismissButton={TextButton(onClick={confirm=false}){Text("Отмена")}})
    else AlertDialog(onDismissRequest=onClose,title={Text("Изменить сорт")},
        text={Column(Modifier.heightIn(max=470.dp).verticalScroll(rememberScrollState()),
            verticalArrangement=Arrangement.spacedBy(9.dp)){
            Field("Название растения",name,{name=it})
            Field("Описание",notes,{notes=it},single=false)
            Field("Причина исправления",reason,{reason=it},single=false)
            OutlinedButton(onClick={confirm=true},
                modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){
                Icon(Icons.Outlined.Archive,null)
                Spacer(Modifier.width(8.dp));Text("Архивировать сорт")
            }
        }},
        confirmButton={Button(enabled=name.trim().length>=2 &&
            reason.trim().length>=2,onClick={onSave(name,notes,reason)}){
            Text("Сохранить")
        }},
        dismissButton={TextButton(onClick=onClose){Text("Отмена")}})
}
