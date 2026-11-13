package ru.dikoveriya.lab

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.json.JSONArray

@Composable
internal fun Lab11CustomCatalogScreen(db:LabDatabase,revision:Int,
    onBack:()->Unit,onChanged:()->Unit,onError:(String)->Unit,
    modifier:Modifier=Modifier){
    var tab by rememberSaveable{mutableStateOf("Категории")}
    var domain by rememberSaveable{mutableStateOf("Материалы")}
    var entity by rememberSaveable{mutableStateOf("item")}
    var openAdd by remember{mutableStateOf(false)}
    val categoryRows=remember(revision,domain){db.categories11(domain)}
    val fieldRows=remember(revision,entity){db.fields11(entity)}
    val templates=remember(revision){db.templates11()}
    LazyColumn(modifier=modifier,contentPadding=PaddingValues(16.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Lab07ScreenHeader("ДИКОВЕРИЯ / СПРАВОЧНИКИ",
            "Мои справочники",
            "Собственные категории, поля и действия без изменения основного кода.",onBack)}
        item{
            Row(Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement=Arrangement.spacedBy(8.dp)){
                listOf("Категории","Поля","Операции").forEach{option->
                    FilterChip(selected=tab==option,onClick={tab=option},
                        label={Text(option)})
                }
            }
        }
        item{
            Button(onClick={openAdd=true},modifier=Modifier.fillMaxWidth()){
                Icon(Icons.Outlined.Add,null)
                Spacer(Modifier.width(8.dp))
                Text("Добавить "+when(tab){
                    "Категории"->"категорию"
                    "Поля"->"поле"
                    else->"операцию"
                })
            }
        }
        when(tab){
            "Категории"->{
                item{
                    PickOne("Раздел",listOf("Материалы","Субстраты","Препараты",
                        "Удобрения","Операции","Растения","Оборудование","Места","Метки"),
                        domain,{domain=it})
                }
                if(categoryRows.isEmpty())item{Text("Категории не созданы")}
                items(categoryRows,key={it}){name->
                    Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),
                        modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp)){
                        Row(Modifier.padding(13.dp),verticalAlignment=Alignment.CenterVertically){
                            Text(name,modifier=Modifier.weight(1f))
                            TextButton(onClick={
                                try{db.archiveCategory11(domain,name);onChanged()}
                                catch(e:Exception){onError(e.message?:"Не удалось архивировать")}
                            }){Text("Архив")}
                        }
                    }
                }
            }
            "Поля"->{
                item{
                    PickOne("Объект",listOf("item","culture","material","asset","purchase"),
                        entity,{entity=it})
                }
                if(fieldRows.isEmpty())item{Text("Пользовательских полей нет")}
                items(fieldRows,key={it.id}){field->
                    OutlinedCard(modifier=Modifier.fillMaxWidth()){
                        Row(Modifier.padding(13.dp),verticalAlignment=Alignment.CenterVertically){
                            Column(Modifier.weight(1f)){
                                Text(field.label,style=MaterialTheme.typography.titleMedium)
                                Text(field.type+(if(field.required)" · обязательно" else ""),
                                    style=MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick={
                                db.archiveField11(field.id);onChanged()
                            }){Text("Архив")}
                        }
                    }
                }
            }
            else->{
                if(templates.isEmpty())item{Text("Своих шаблонов операций нет")}
                items(templates,key={it.id}){template->
                    OutlinedCard(modifier=Modifier.fillMaxWidth()){
                        Row(Modifier.padding(13.dp),verticalAlignment=Alignment.CenterVertically){
                            Column(Modifier.weight(1f)){
                                Text(template.title,style=MaterialTheme.typography.titleMedium)
                                Text(template.applicable+" · "+JSONArray(template.fields).length()+
                                    " доп. полей",style=MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick={
                                db.archiveTemplate11(template.id);onChanged()
                            }){Text("Архив")}
                        }
                    }
                }
            }
        }
    }
    if(openAdd)Lab11CatalogAddDialog(db,tab,domain,entity,
        onClose={openAdd=false},
        onSaved={openAdd=false;onChanged()},onError=onError)
}

@Composable
private fun Lab11CatalogAddDialog(db:LabDatabase,tab:String,defaultDomain:String,
    defaultEntity:String,onClose:()->Unit,onSaved:()->Unit,onError:(String)->Unit){
    var name by rememberSaveable{mutableStateOf("")}
    var domain by rememberSaveable{mutableStateOf(defaultDomain)}
    var entity by rememberSaveable{mutableStateOf(defaultEntity)}
    var type by rememberSaveable{mutableStateOf("text")}
    var choices by rememberSaveable{mutableStateOf("")}
    var required by rememberSaveable{mutableStateOf(false)}
    var applies by rememberSaveable{mutableStateOf("Все")}
    AlertDialog(onDismissRequest=onClose,
        title={Text("Новый справочник · "+tab)},
        text={Column(Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement=Arrangement.spacedBy(10.dp)){
            Field("Название",name,{name=it})
            when(tab){
                "Категории"->PickOne("Раздел",
                    listOf("Материалы","Субстраты","Препараты",
                        "Удобрения","Операции","Растения","Оборудование","Места","Метки"),
                    domain,{domain=it})
                "Поля"->{
                    PickOne("Тип объекта",
                        listOf("item","culture","material","asset","purchase"),
                        entity,{entity=it})
                    PickOne("Тип значения",listOf("text","number","date",
                        "boolean","choice","unit","photo","url"),
                        type,{type=it})
                    if(type=="choice")Field("Варианты через запятую",choices,{choices=it})
                    Row(verticalAlignment=Alignment.CenterVertically){
                        Checkbox(required,{required=it})
                        Text("Обязательное")
                    }
                }
                else->{
                    PickOne("Применяется для",listOf("Все","in vitro","Горшечное",
                        "Черенок","Маточник","Другое"),applies,{applies=it})
                    Field("Дополнительные поля через запятую",choices,{choices=it})
                    Text("Поля добавятся в форму наблюдения. Шаблон не может списывать биоматериал без встроенной проверенной операции.",
                        style=MaterialTheme.typography.bodySmall)
                }
            }
        }},
        confirmButton={
            Button(enabled=name.trim().length>=2,onClick={
                try{
                    when(tab){
                        "Категории"->db.addCategory11(domain,name)
                        "Поля"->db.defineField11(entity,name,type,required,
                            choices.split(',').map{it.trim()}.filter{it.isNotEmpty()})
                        else->db.addTemplate11(name,applies,
                            JSONArray(choices.split(',').map{it.trim()}
                                .filter{it.isNotEmpty()}).toString())
                    }
                    onSaved()
                }catch(e:Exception){onError(e.message?:"Не удалось сохранить справочник")}
            }){Text("Сохранить")}
        },
        dismissButton={TextButton(onClick=onClose){Text("Отмена")}}
    )
}
