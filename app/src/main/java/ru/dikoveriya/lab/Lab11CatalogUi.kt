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
                item{Text("Неиспользуемую запись можно удалить. Если запись уже встречается в истории, она будет архивирована.",
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)}
                if(categoryRows.isEmpty())item{Text("Категории не созданы")}
                items(categoryRows,key={it}){name->
                    Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),
                        modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp)){
                        Row(Modifier.padding(13.dp),verticalAlignment=Alignment.CenterVertically){
                            Text(name,modifier=Modifier.weight(1f))
                            var menu by remember(name){mutableStateOf(false)}
                            Box{
                                IconButton(onClick={menu=true}){
                                    Icon(Icons.Outlined.MoreVert,"Действия с категорией")
                                }
                                DropdownMenu(expanded=menu,onDismissRequest={menu=false}){
                                    DropdownMenuItem(text={Text("Удалить неиспользуемую")},
                                        onClick={
                                            menu=false
                                            try{db.deleteOrArchiveCategory11(domain,name);onChanged()}
                                            catch(e:Exception){onError(e.message?:"Не удалось удалить")}
                                        })
                                    DropdownMenuItem(text={Text("В архив")},
                                        onClick={
                                            menu=false
                                            try{db.archiveCategory11(domain,name);onChanged()}
                                            catch(e:Exception){onError(e.message?:"Не удалось архивировать")}
                                        })
                                }
                            }
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
                            var menu by remember(field.id){mutableStateOf(false)}
                            Box{
                                IconButton(onClick={menu=true}){
                                    Icon(Icons.Outlined.MoreVert,"Действия с полем")
                                }
                                DropdownMenu(expanded=menu,onDismissRequest={menu=false}){
                                    DropdownMenuItem(text={Text("Удалить неиспользуемое")},
                                        onClick={
                                            menu=false
                                            try{db.deleteOrArchiveField11(field.id);onChanged()}
                                            catch(e:Exception){onError(e.message?:"Не удалось удалить")}
                                        })
                                    DropdownMenuItem(text={Text("В архив")},
                                        onClick={
                                            menu=false;db.archiveField11(field.id);onChanged()
                                        })
                                }
                            }
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
                            var menu by remember(template.id){mutableStateOf(false)}
                            Box{
                                IconButton(onClick={menu=true}){
                                    Icon(Icons.Outlined.MoreVert,"Действия с шаблоном")
                                }
                                DropdownMenu(expanded=menu,onDismissRequest={menu=false}){
                                    DropdownMenuItem(text={Text("Удалить неиспользуемый")},
                                        onClick={
                                            menu=false
                                            try{db.deleteOrArchiveTemplate11(template.id);onChanged()}
                                            catch(e:Exception){onError(e.message?:"Не удалось удалить")}
                                        })
                                    DropdownMenuItem(text={Text("В архив")},
                                        onClick={menu=false;db.archiveTemplate11(template.id);onChanged()})
                                }
                            }
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
    var templateFields by remember{mutableStateOf(emptyList<Lab11TemplateField>())}
    var newField by rememberSaveable{mutableStateOf("")}
    var newFieldKind by rememberSaveable{mutableStateOf("text")}
    var newUnit by rememberSaveable{mutableStateOf("")}
    var newChoices by rememberSaveable{mutableStateOf("")}
    var newRequired by rememberSaveable{mutableStateOf(false)}
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
                    Field("Название поля",newField,{newField=it})
                    PickOne("Тип поля",listOf("text","number","date","boolean",
                        "choice","unit","photo","url"),newFieldKind,{newFieldKind=it})
                    if(newFieldKind=="unit")
                        Field("Единица измерения (например, мл)",newUnit,{newUnit=it})
                    if(newFieldKind=="choice")
                        Field("Варианты через запятую",newChoices,{newChoices=it})
                    Row(verticalAlignment=Alignment.CenterVertically){
                        Checkbox(newRequired,{newRequired=it})
                        Text("Обязательное поле")
                    }
                    OutlinedButton(onClick={
                        try{
                            val f=Lab11TemplateField(newField.trim(),newFieldKind,
                                newRequired,newUnit.trim(),
                                newChoices.split(',').map{it.trim()}.filter{it.isNotEmpty()})
                            templateFieldsJson11(templateFields+f)
                            templateFields=templateFields+f
                            newField="";newUnit="";newChoices="";newRequired=false
                        }catch(e:Exception){onError(e.message?:"Проверь поле")}
                    },modifier=Modifier.fillMaxWidth()){
                        Text("Добавить поле в шаблон")
                    }
                    templateFields.forEach{f->
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                            Text(f.name+" · "+f.kind+
                                (if(f.unit.isNotBlank())" · "+f.unit else ""),
                                modifier=Modifier.weight(1f),
                                style=MaterialTheme.typography.bodySmall)
                            TextButton(onClick={templateFields=templateFields-f}){
                                Text("Убрать")
                            }
                        }
                    }
                    Text("Поля шаблона не могут менять количество биоматериала или склад без встроенной проверенной операции.",
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
                            templateFieldsJson11(templateFields))
                    }
                    onSaved()
                }catch(e:Exception){onError(e.message?:"Не удалось сохранить справочник")}
            }){Text("Сохранить")}
        },
        dismissButton={TextButton(onClick=onClose){Text("Отмена")}}
    )
}
