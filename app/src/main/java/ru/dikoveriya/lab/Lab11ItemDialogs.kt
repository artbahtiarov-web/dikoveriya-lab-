package ru.dikoveriya.lab

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal fun dateText11():String=LocalDate.now().format(
    DateTimeFormatter.ofPattern("dd.MM.uuuu"))
internal fun atDate11(value:String):Long?=validDate11(value)
    ?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()

@Composable
internal fun Lab11NewItemDialog(
    db:LabDatabase,plants:List<PlantRecord>,
    onClose:()->Unit,onSaved:(Long)->Unit,onError:(String)->Unit,
    forcedPlantId:Long?=null
){
    var plantId by rememberSaveable{mutableStateOf(forcedPlantId)}
    var kind by rememberSaveable{mutableStateOf("Горшечное")}
    var date by rememberSaveable{mutableStateOf(dateText11())}
    var supplier by rememberSaveable{mutableStateOf("")}
    var cost by rememberSaveable{mutableStateOf("0")}
    var place by rememberSaveable{mutableStateOf("")}
    var substrate by rememberSaveable{mutableStateOf("")}
    var rooting by rememberSaveable{mutableStateOf("Вода")}
    var stimulant by rememberSaveable{mutableStateOf("")}
    var pot by rememberSaveable{mutableStateOf("")}
    var parentCode by rememberSaveable{mutableStateOf("")}
    var note by rememberSaveable{mutableStateOf("")}
    AlertDialog(
        onDismissRequest=onClose,
        title={Text("Добавить растение")},
        text={
            Column(Modifier.heightIn(max=530.dp).verticalScroll(rememberScrollState()),
                verticalArrangement=Arrangement.spacedBy(10.dp)){
                if(plants.isEmpty())
                    Text("Сначала добавь сорт в коллекцию",color=MaterialTheme.colorScheme.error)
                PickOne("Вид / сорт",listOf("Выбрать сорт")+plants.map{it.name},
                    plants.firstOrNull{it.id==plantId}?.name?:"Выбрать сорт",
                    {selected->plantId=plants.firstOrNull{it.name==selected}?.id})
                PickOne("Тип",listOf("Горшечное","Черенок","Маточник","Другое"),
                    kind,{kind=it})
                Lab11DateField("Дата появления",date,{date=it})
                Field("Продавец (необязательно)",supplier,{supplier=it})
                Field("Стоимость приобретения, ₽",cost,{cost=it})
                Field("Место: например, подоконник кухни",place,{place=it})
                if(kind=="Черенок"){
                    Field("Метод укоренения",rooting,{rooting=it})
                    Field("Стимулятор (необязательно)",stimulant,{stimulant=it})
                }else{
                    Field("Субстрат / состав",substrate,{substrate=it},single=false)
                    Field("Объём горшка, мл",pot,{pot=it})
                }
                Field("Родитель (PLT-000001 или LAB-000001, необязательно)",
                    parentCode,{parentCode=it})
                Field("Примечание",note,{note=it},single=false)
                Text("Указанная стоимость — выплата за покупку. При создании черенка без покупки оставь 0 ₽.",
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton={
            Button(enabled=plantId!=null && atDate11(date)!=null &&
                runCatching{rubles11(cost)}.isSuccess,
                onClick={
                    try{
                        val parent=parentCode.trim().uppercase()
                        val parentItem=if(parent.startsWith("PLT-"))
                            db.itemIdByCode11(parent) else null
                        val parentCulture=if(parent.startsWith("LAB-"))
                            db.findCulture072(parent) else null
                        if(parent.isNotEmpty() && parentItem==null && parentCulture==null)
                            error("Не найден указанный исходный объект")
                        val id=db.addPlantItem11(
                            plantId=requireNotNull(plantId),
                            kind=kind,
                            acquiredAt=requireNotNull(atDate11(date)),
                            supplier=supplier,costMinor=rubles11(cost),
                            parentItemId=parentItem,parentCultureId=parentCulture,
                            locationText=place,substrate=substrate,
                            rooting=if(kind=="Черенок")rooting else "",
                            stimulant=stimulant,potMl=pot.toIntOrNull(),note=note)
                        onSaved(id)
                    }catch(e:Exception){onError(e.message?:"Не удалось добавить растение")}
                }){Text("Добавить")}
        },
        dismissButton={TextButton(onClick=onClose){Text("Отмена")}}
    )
}

@Composable
internal fun Lab11NewCuttingDialog(
    parent:Lab11PlantItem,db:LabDatabase,
    onClose:()->Unit,onSaved:(Long)->Unit,onError:(String)->Unit
){
    var date by rememberSaveable{mutableStateOf(dateText11())}
    var rooting by rememberSaveable{mutableStateOf("Вода")}
    var stimulant by rememberSaveable{mutableStateOf("")}
    var location by rememberSaveable{mutableStateOf(parent.location)}
    var note by rememberSaveable{mutableStateOf("")}
    AlertDialog(onDismissRequest=onClose,title={Text("Создать черенок")},
        text={Column(Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement=Arrangement.spacedBy(11.dp)){
            Text("Родитель: "+parent.code+" · "+parent.plantName)
            Lab11DateField("Дата среза",date,{date=it})
            PickOne("Укоренение",listOf("Вода","Сфагнум","Субстрат",
                "Перлит","Другой способ"),rooting,{rooting=it})
            Field("Стимулятор (если применялся)",stimulant,{stimulant=it})
            Field("Место",location,{location=it})
            Field("Примечание",note,{note=it},single=false)
        }},
        confirmButton={Button(enabled=atDate11(date)!=null,
            onClick={
                try{
                    val id=db.takeCutting11(parent.id,requireNotNull(atDate11(date)),
                        rooting,stimulant,location,note)
                    onSaved(id)
                }catch(e:Exception){onError(e.message?:"Не удалось создать черенок")}
            }){Text("Создать")}},
        dismissButton={TextButton(onClick=onClose){Text("Отмена")}})
}

@Composable
internal fun Lab11ActionDialog(
    item:Lab11PlantItem,db:LabDatabase,
    onClose:()->Unit,onSaved:()->Unit,onError:(String)->Unit
){
    val allowed=if(item.kind=="Черенок")listOf("Укоренение","Пересадка",
        "Осмотр","Полив","Подкормка","Обработка","Измерение","Потеря",
        "Утилизация","Другое")
        else listOf("Посадка","Пересадка","Полив","Подкормка",
            "Обработка","Обрезка","Прищипка","Осмотр","Измерение",
            "Перемещение","Потеря","Утилизация","Другое")
    val templates=remember{db.templates11()}
    val custom=templates.map{it.title}
    var customAnswers by remember{mutableStateOf(emptyMap<String,String>())}
    var kind by rememberSaveable{mutableStateOf(allowed.first())}
    val fields=remember(kind){
        templates.firstOrNull{it.title==kind}
            ?.let{parseTemplateFields11(it.fields)}?:emptyList()
    }
    val context=LocalContext.current
    var pendingTemplatePhoto by remember{mutableStateOf<String?>(null)}
    var pendingPhotos by remember{mutableStateOf(emptyMap<String,android.net.Uri>())}
    val photoPicker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){uri->
        val target=pendingTemplatePhoto
        pendingTemplatePhoto=null
        // Selecting an image never writes it to the live collection.
        // Save commits it together with the operation; Cancel leaves no changes.
        if(uri!=null && target!=null)
            pendingPhotos=pendingPhotos+(target to uri)
    }
    var date by rememberSaveable{mutableStateOf(dateText11())}
    var stage by rememberSaveable{mutableStateOf("Не менять")}
    var newLocation by rememberSaveable{mutableStateOf("")}
    var substrate by rememberSaveable{mutableStateOf("")}
    var newPotMl by rememberSaveable{mutableStateOf("")}
    var stimulant by rememberSaveable{mutableStateOf("")}
    var note by rememberSaveable{mutableStateOf("")}
    var materialId by rememberSaveable{mutableStateOf<Long?>(null)}
    var qty by rememberSaveable{mutableStateOf("")}
    var extraUses by remember{mutableStateOf(emptyList<Pair<Long?,String>>())}
    val materials=remember{db.materials()}
    val usesMaterial=kind in setOf("Посадка","Пересадка","Подкормка",
        "Обработка","Полив") || kind in custom
    val changesPlace=kind in setOf("Пересадка","Перемещение","Посадка")
    val changesSubstrate=kind in setOf("Посадка","Пересадка")
    val isClosed=item.status in setOf("Утилизирован","Продан")
    AlertDialog(onDismissRequest=onClose,
        title={Text("Действие · "+item.code)},
        text={Column(Modifier.heightIn(max=530.dp).verticalScroll(rememberScrollState()),
            verticalArrangement=Arrangement.spacedBy(11.dp)){
            Text(item.plantName+" · "+item.kind,
                style=MaterialTheme.typography.titleSmall)
            PickOne("Операция",allowed+custom.filter{it !in allowed},
                kind,{kind=it})
            Lab11DateField("Дата операции",date,{date=it})
            if(kind=="Укоренение" || kind=="Пересадка"){
                PickOne("Новый этап",
                    listOf("Не менять","Укоренение","Укоренено","В горшке",
                        "В работе","Адаптация","Доращивание","Готово к продаже"),
                    stage,{stage=it})
            }
            if(changesPlace)
                Field("Новое местоположение (необязательно)",
                    newLocation,{newLocation=it})
            if(changesSubstrate){
                Field("Новый субстрат / состав (необязательно)",
                    substrate,{substrate=it},single=false)
                Field("Новый горшок, мл (необязательно)",
                    newPotMl,{newPotMl=it})
            }
            if(kind=="Укоренение" || kind=="Обработка")
                Field("Стимулятор / препарат",stimulant,{stimulant=it})
            if(usesMaterial && materials.isNotEmpty()){
                PickOne("Материал со склада (необязательно)",
                    listOf("Не использовать")+materials.map{it.name},
                    materials.firstOrNull{it.id==materialId}?.name?:"Не использовать",
                    {value->materialId=materials.firstOrNull{it.name==value}?.id})
                if(materialId!=null){
                    val m=materials.firstOrNull{it.id==materialId}
                    Text("Доступно: "+(m?.quantity?:0.0)+" "+(m?.unit?:""),
                        style=MaterialTheme.typography.bodySmall)
                    Field("Сколько использовано (в единице склада)",qty,{qty=it})
                }
            }
            if(usesMaterial && materials.isNotEmpty()){
                extraUses.forEachIndexed{index,(selectedId,selectedQty)->
                    val current=materials.firstOrNull{it.id==selectedId}
                    PickOne("Дополнительный материал "+(index+1),
                        listOf("Не указан")+materials.map{it.name},
                        current?.name?:"Не указан",
                        {label->
                            val next=extraUses.toMutableList()
                            next[index]=materials.firstOrNull{it.name==label}?.id to selectedQty
                            extraUses=next
                        })
                    if(current!=null){
                        Text("Доступно: "+current.quantity+" "+current.unit,
                            style=MaterialTheme.typography.bodySmall)
                        Field("Расход, "+current.unit,selectedQty,
                            {value->
                                val next=extraUses.toMutableList()
                                next[index]=selectedId to value
                                extraUses=next
                            })
                    }
                    TextButton(onClick={
                        extraUses=extraUses.filterIndexed{i,_->i!=index}
                    }){Text("Убрать строку")}
                }
                if(extraUses.size<11)
                    OutlinedButton(onClick={
                        extraUses=extraUses+(null to "")
                    },modifier=Modifier.fillMaxWidth()){
                        Text("Добавить ещё материал")
                    }
            }
            OutlinedButton(onClick={
                pendingTemplatePhoto="__action_photo"
                photoPicker.launch("image/*")
            },modifier=Modifier.fillMaxWidth()){
                Text(if(pendingPhotos.containsKey("__action_photo"))
                    "Фото действия выбрано" else "Добавить фото действия")
            }
            fields.forEach{field->
                val label=field.name+(if(field.unit.isNotBlank())" ("+field.unit+")" else "")+
                    (if(field.required)" *" else "")
                val value=customAnswers[field.name].orEmpty()
                when(field.kind){
                    "date"->Lab11DateField(label,value,
                        {v->customAnswers=customAnswers+(field.name to v)})
                    "boolean"->Row(verticalAlignment=Alignment.CenterVertically){
                        Checkbox(value=="true",onCheckedChange={checked->
                            customAnswers=customAnswers+(field.name to checked.toString())
                        })
                        Text(label)
                    }
                    "choice"->PickOne(label,field.choices,
                        value.ifEmpty{field.choices.firstOrNull().orEmpty()},
                        {v->customAnswers=customAnswers+(field.name to v)})
                    "photo"->OutlinedButton(onClick={
                        pendingTemplatePhoto=field.name
                        photoPicker.launch("image/*")
                    },modifier=Modifier.fillMaxWidth()){
                        Text(if(pendingPhotos.containsKey(field.name))
                            "Фото выбрано · "+label else "Добавить фото · "+label)
                    }
                    else->Field(label,value,
                        {v->customAnswers=customAnswers+(field.name to v)},
                        single=field.kind!="text")
                }
            }
            Field("Что сделано / результат",note,{note=it},single=false)
            if(isClosed)Text("Этот экземпляр закрыт. Для восстановления потребуется корректирующая операция.",
                color=MaterialTheme.colorScheme.error)
        }},
        confirmButton={
            Button(enabled=!isClosed && atDate11(date)!=null,
                onClick={
                    try{
                        val amount=if(materialId!=null)
                            qty.replace(',','.').toDoubleOrNull()
                                ?:error("Укажи количество материала")
                        else 0.0
                        val otherMaterials=extraUses.mapNotNull{(id,text)->
                            if(id==null)null
                            else id to (text.replace(',','.').toDoubleOrNull()
                                ?:error("Проверь количество дополнительного материала"))
                        }
                        val potVolume=if(newPotMl.isBlank())null
                            else newPotMl.toIntOrNull()?:error("Неверный объём горшка")
                        val newKind=if(item.kind=="Черенок" &&
                            kind=="Пересадка" && stage=="В горшке")"Горшечное" else null
                        db.commitItemActionPhotos11(
                            context,item.id,pendingPhotos){photoNames->
                            db.performItemAction11(item.id,kind,
                                at=requireNotNull(atDate11(date)),
                                note=note,newStage=stage.takeUnless{it=="Не менять"},
                                newKind=newKind,
                                newLocation=newLocation.takeIf{it.isNotBlank()},
                                newSubstrate=substrate.takeIf{it.isNotBlank()},
                                stimulant=stimulant.takeIf{it.isNotBlank()},
                                materialId=materialId,materialQty=amount,
                                extraMaterials=otherMaterials,newPotMl=potVolume,
                                photoFilename=photoNames["__action_photo"],
                                customFields=(customAnswers+photoNames)
                                    .filterKeys{key->fields.any{it.name==key}}
                            )
                        }
                        onSaved()
                    }catch(e:Exception){onError(e.message?:"Не удалось записать действие")}
                }){Text("Записать")}
        },
        dismissButton={TextButton(onClick=onClose){Text("Отмена")}}
    )
}

@Composable
internal fun Lab11SaleDialog(item:Lab11PlantItem,db:LabDatabase,
    onClose:()->Unit,onSaved:()->Unit,onError:(String)->Unit
){
    var amount by rememberSaveable{mutableStateOf("")}
    var channel by rememberSaveable{mutableStateOf("Личная продажа")}
    var buyer by rememberSaveable{mutableStateOf("")}
    var note by rememberSaveable{mutableStateOf("")}
    AlertDialog(onDismissRequest=onClose,title={Text("Продажа растения")},
        text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
            Text(item.code+" · "+item.plantName)
            Field("Цена продажи, ₽",amount,{amount=it})
            Field("Канал (например, Авито)",channel,{channel=it})
            Field("Покупатель (необязательно)",buyer,{buyer=it})
            Field("Примечание",note,{note=it},single=false)
            Text("Прямая себестоимость фиксируется на дату продажи. Общие расходы проекта не распределяются.",
                style=MaterialTheme.typography.bodySmall)
        }},
        confirmButton={Button(enabled=runCatching{rubles11(amount)}.isSuccess,
            onClick={
                try{db.sellItem11(item.id,rubles11(amount),channel,buyer,note)
                    onSaved()
                }catch(e:Exception){onError(e.message?:"Не удалось сохранить продажу")}
            }){Text("Продать")}},
        dismissButton={TextButton(onClick=onClose){Text("Отмена")}})
}
