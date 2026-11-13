package ru.dikoveriya.lab

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import java.time.YearMonth
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun Lab11AssetsScreen(db:LabDatabase,revision:Int,
    onBack:()->Unit,onChanged:()->Unit,onError:(String)->Unit,
    initialAssetId:Long?=null,modifier:Modifier=Modifier
){
    val context=androidx.compose.ui.platform.LocalContext.current
    var pendingLabel by remember{mutableStateOf<Long?>(null)}
    val saveQr=rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png")){uri->
        val id=pendingLabel
        pendingLabel=null
        if(uri!=null && id!=null)runCatching{
            context.contentResolver.openOutputStream(uri)?.use{
                it.write(LabQr.png("AST-%06d".format(Locale.US,id)))
            }?:error("Файл не создан")
        }.onFailure{onError(it.message?:"Ошибка сохранения QR оборудования")}
    }
    val assets=remember(revision){db.assets11()}
    val lights=remember(revision){db.lightProfiles11()}
    val thisMonth=YearMonth.now()
    var add by remember{mutableStateOf(false)}
    var detail by remember(initialAssetId){mutableStateOf(initialAssetId)}
    var retire by remember{mutableStateOf<Long?>(null)}
    var configureLight by remember{mutableStateOf<Long?>(null)}
    var serviceId by remember{mutableStateOf<Long?>(null)}
    var serviceKind by remember{mutableStateOf("Обслуживание")}
    var serviceDate by remember{mutableStateOf(dateText11())}
    var serviceNote by remember{mutableStateOf("")}
    var serviceExpense by remember{mutableStateOf("0")}
    var showLightingUnitCost by rememberSaveable{mutableStateOf(false)}
    var unitsForLighting by rememberSaveable{mutableStateOf("")}
    var reason by remember{mutableStateOf("")}
    var state by remember{mutableStateOf("Сломано")}
    LazyColumn(modifier=modifier,contentPadding=PaddingValues(16.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{
            Lab07ScreenHeader("ДИКОВЕРИЯ / РЕСУРСЫ","Оборудование",
                "Многоразовое имущество и освещение учитываются отдельно от себестоимости растений.",onBack)
        }
        item{
            Button(onClick={add=true},modifier=Modifier.fillMaxWidth()){
                Icon(Icons.Outlined.Add,null)
                Spacer(Modifier.width(8.dp))
                Text("Приобрести оборудование")
            }
        }
        item{
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
                Card(Modifier.weight(1f),colors=CardDefaults.cardColors(
                    containerColor=Lab07Palette.Champagne)){
                    Column(Modifier.padding(13.dp)){
                        Text("Единиц имущества",color=Lab07Palette.Ink)
                        Text(assets.size.toString(),
                            style=MaterialTheme.typography.headlineMedium,
                            color=Lab07Palette.Ink)
                    }
                }
                Card(Modifier.weight(1f),colors=CardDefaults.cardColors(
                    containerColor=Lab07Palette.Champagne)){
                    Column(Modifier.padding(13.dp)){
                        Text("Покупок",color=Lab07Palette.Ink)
                        Text(kopecks11(assets.sumOf{it.cost}),
                            style=MaterialTheme.typography.titleMedium,
                            color=Lab07Palette.Ink)
                    }
                }
            }
        }
        item{
            Text("Оценка освещения за "+thisMonth.monthValue+"."+thisMonth.year,
                style=MaterialTheme.typography.titleMedium)
            Text(kopecks11(lights.sumOf{it.estimatedMonth11(thisMonth)}),
                style=MaterialTheme.typography.headlineSmall)
            Text("Расчётная стоимость по заявленной фактической мощности и тарифу. Это не оплаченный счёт и не себестоимость растений.",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick={showLightingUnitCost=!showLightingUnitCost}){
                Text(if(showLightingUnitCost)"Скрыть условную оценку"
                    else "Условная стоимость света на растение")
            }
            if(showLightingUnitCost){
                OutlinedTextField(unitsForLighting,{unitsForLighting=it},
                    label={Text("Количество единиц продукции на освещённых полках")},
                    modifier=Modifier.fillMaxWidth(),singleLine=true)
                val qty=unitsForLighting.toLongOrNull()
                if(qty!=null && qty>0){
                    val monthCost=lights.sumOf{it.estimatedMonth11(thisMonth)}
                    val value=java.math.BigDecimal.valueOf(monthCost,2)
                        .divide(java.math.BigDecimal.valueOf(qty),4,
                            java.math.RoundingMode.HALF_UP)
                    Text("Условно: "+value.toPlainString()+
                        " ₽/ед. за месяц. В прямую себестоимость не добавляется.",
                        style=MaterialTheme.typography.bodySmall)
                }else if(unitsForLighting.isNotBlank())
                    Text("Укажи положительное количество",
                        color=MaterialTheme.colorScheme.error)
            }
        }
        if(assets.isEmpty())item{Text("Оборудование ещё не зарегистрировано")}
        items(assets,key={it.id}){asset->
            OutlinedCard(onClick={detail=asset.id},
                modifier=Modifier.fillMaxWidth(),
                shape=RoundedCornerShape(17.dp),
                border=BorderStroke(1.dp,Lab07Palette.Brass.copy(alpha=.3f))){
                Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){
                    Icon(Icons.Outlined.Build,null,tint=MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f).padding(start=12.dp),
                        verticalArrangement=Arrangement.spacedBy(4.dp)){
                        Text(asset.name,style=MaterialTheme.typography.titleMedium)
                        Text(asset.category+" · "+asset.status,
                            style=MaterialTheme.typography.bodySmall)
                        Text(asset.location.ifBlank{"Без места"}+" · "+
                            kopecks11(asset.cost),
                            style=MaterialTheme.typography.bodySmall)
                    }
                    Icon(Icons.Outlined.ChevronRight,null)
                }
            }
        }
    }
    if(add)Lab11AssetDialog(db,onClose={add=false},
        onSaved={add=false;onChanged()},onError=onError)
    detail?.let{id->
        val asset=assets.firstOrNull{it.id==id}
        if(asset!=null){
            val history=remember(id,revision){db.assetEvents11(id)}
            ModalBottomSheet(onDismissRequest={detail=null}){
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(20.dp),verticalArrangement=Arrangement.spacedBy(11.dp)){
                    Text(asset.name,style=MaterialTheme.typography.headlineSmall)
                    Row(verticalAlignment=Alignment.CenterVertically){
                        Text("Инвентарный QR: AST-%06d".format(Locale.US,asset.id),
                            modifier=Modifier.weight(1f))
                        TextButton(onClick={
                            pendingLabel=asset.id
                            saveQr.launch("AST-%06d.png".format(Locale.US,asset.id))
                        }){Text("Этикетка")}
                    }
                    Text(asset.category+" · "+asset.status)
                    Text("Стоимость приобретения: "+kopecks11(asset.cost))
                    Text("Поставщик: "+asset.supplier)
                    Text("Дата: "+formatDate(asset.purchasedAt))
                    asset.measuredWatts?.let{Text("Измеренная мощность: "+it+" Вт")}
                    if(asset.status=="Используется"){
                        Button(onClick={detail=null;configureLight=id},
                            modifier=Modifier.fillMaxWidth()){
                            Icon(Icons.Outlined.Lightbulb,null)
                            Text("Настроить освещение")
                        }
                        OutlinedButton(onClick={detail=null;serviceId=id},
                            modifier=Modifier.fillMaxWidth()){
                            Text("Записать ремонт или обслуживание")
                        }
                        OutlinedButton(onClick={detail=null;retire=id},
                            modifier=Modifier.fillMaxWidth()){
                            Text("Вывести из эксплуатации")
                        }
                    }
                    Text("История",style=MaterialTheme.typography.titleMedium)
                    history.forEach{(action,note)->
                        Text(action+" · "+note,style=MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
    configureLight?.let{id->
        Lab11LightDialog(db,id,onClose={configureLight=null},
            onSaved={configureLight=null;onChanged()},onError=onError)
    }
    serviceId?.let{id->
        AlertDialog(onDismissRequest={serviceId=null},
            title={Text("История обслуживания")},
            text={Column(Modifier.heightIn(max=450.dp).verticalScroll(rememberScrollState()),
                verticalArrangement=Arrangement.spacedBy(10.dp)){
                PickOne("Действие",listOf("Проверка","Обслуживание","Ремонт",
                    "Замена расходника"),serviceKind,{serviceKind=it})
                Lab11DateField("Дата",serviceDate,{serviceDate=it})
                Field("Что выполнено и почему",serviceNote,{serviceNote=it},
                    single=false)
                Field("Оплачено, ₽ (если были расходы)",serviceExpense,
                    {serviceExpense=it})
                Text("Если сумма больше нуля, появится отдельная выплата проекта. Исходная стоимость оборудования не списывается повторно.",
                    style=MaterialTheme.typography.bodySmall)
            }},
            confirmButton={
                Button(enabled=atDate11(serviceDate)!=null &&
                    serviceNote.trim().length>=2 &&
                    runCatching{rubles11(serviceExpense)}.isSuccess,
                    onClick={
                        try{
                            db.recordAssetService11(id,serviceKind,
                                requireNotNull(atDate11(serviceDate)),serviceNote,
                                rubles11(serviceExpense))
                            serviceId=null;serviceNote="";serviceExpense="0";onChanged()
                        }catch(e:Exception){onError(e.message?:"Не удалось зарегистрировать обслуживание")}
                    }){Text("Сохранить")}
            },
            dismissButton={TextButton(onClick={serviceId=null}){Text("Отмена")}}
        )
    }
    retire?.let{id->
        AlertDialog(onDismissRequest={retire=null},
            title={Text("Вывод из эксплуатации")},
            text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
                PickOne("Состояние",listOf("Сломано","Утеряно","Утилизировано",
                    "Продано","Заменено"),state,{state=it})
                Field("Причина",reason,{reason=it},single=false)
                Text("Это изменение статуса имущества, не новая денежная выплата.",
                    style=MaterialTheme.typography.bodySmall)
            }},
            confirmButton={Button(enabled=reason.trim().length>=2,onClick={
                try{db.retireAsset11(id,state,reason)
                    retire=null;reason="";onChanged()
                }catch(e:Exception){onError(e.message?:"Не удалось вывести оборудование")}
            }){Text("Подтвердить")}},
            dismissButton={TextButton(onClick={retire=null}){Text("Отмена")}})
    }
}

@Composable
internal fun Lab11AssetDialog(db:LabDatabase,onClose:()->Unit,
    onSaved:()->Unit,onError:(String)->Unit){
    var name by rememberSaveable{mutableStateOf("")}
    var category by rememberSaveable{mutableStateOf("Лампы")}
    var date by rememberSaveable{mutableStateOf(dateText11())}
    var price by rememberSaveable{mutableStateOf("")}
    var supplier by rememberSaveable{mutableStateOf("")}
    var place by rememberSaveable{mutableStateOf("")}
    var nominal by rememberSaveable{mutableStateOf("")}
    var measured by rememberSaveable{mutableStateOf("")}
    var note by rememberSaveable{mutableStateOf("")}
    AlertDialog(onDismissRequest=onClose,title={Text("Новое оборудование")},
        text={Column(Modifier.heightIn(max=530.dp).verticalScroll(rememberScrollState()),
            verticalArrangement=Arrangement.spacedBy(10.dp)){
            Field("Название",name,{name=it})
            PickOne("Категория",listOf("Лампы","Стеллажи","Автоклавы",
                "Многоразовые контейнеры","Инструменты","Прочее"),category,{category=it})
            Lab11DateField("Дата приобретения",date,{date=it})
            Field("Стоимость, ₽",price,{price=it})
            Field("Поставщик",supplier,{supplier=it})
            Field("Местоположение",place,{place=it})
            if(category=="Лампы"){
                Field("Номинальная мощность, Вт",nominal,{nominal=it})
                Field("Измеренная мощность, Вт",measured,{measured=it})
            }
            Field("Примечание",note,{note=it},single=false)
        }},
        confirmButton={Button(enabled=name.trim().length>=2 &&
            atDate11(date)!=null && runCatching{rubles11(price)}.isSuccess,
            onClick={
                try{
                    db.addAsset11(name,category,supplier,rubles11(price),
                        requireNotNull(atDate11(date)),place,
                        nominal.replace(',','.').toDoubleOrNull(),
                        measured.replace(',','.').toDoubleOrNull(),note)
                    onSaved()
                }catch(e:Exception){onError(e.message?:"Не удалось добавить оборудование")}
            }){Text("Сохранить")}},
        dismissButton={TextButton(onClick=onClose){Text("Отмена")}})
}

@Composable
internal fun Lab11LightDialog(db:LabDatabase,assetId:Long,
    onClose:()->Unit,onSaved:()->Unit,onError:(String)->Unit){
    var watts by rememberSaveable{mutableStateOf("")}
    var hours by rememberSaveable{mutableStateOf("16")}
    var tariff by rememberSaveable{mutableStateOf("")}
    var ppfd by rememberSaveable{mutableStateOf("")}
    var date by rememberSaveable{mutableStateOf(dateText11())}
    val places=remember{db.lightLocations11()}
    var selectedPlace by rememberSaveable{mutableStateOf<Long?>(null)}
    AlertDialog(onDismissRequest=onClose,
        title={Text("Расчёт освещения")},
        text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
            Text("Укажи потребление из измерения либо паспорта оборудования.",
                style=MaterialTheme.typography.bodySmall)
            Field("Фактическая мощность, Вт",watts,{watts=it})
            Field("Часов работы в сутки",hours,{hours=it})
            Field("Тариф, ₽/кВт·ч",tariff,{tariff=it})
            Lab11DateField("Действует с",date,{date=it})
            if(places.isNotEmpty())PickOne("Стеллаж или полка",
                listOf("Не привязывать")+places.map{it.second},
                places.firstOrNull{it.first==selectedPlace}?.second?:"Не привязывать",
                {chosen->selectedPlace=places.firstOrNull{it.second==chosen}?.first})
            Field("PPFD, мкмоль/м²/с (необязательно)",ppfd,{ppfd=it})
            Text("Расчётный свет — общие расходы проекта, без распределения по растениям.",
                style=MaterialTheme.typography.bodySmall)
        }},
        confirmButton={Button(onClick={
            try{
                val tariffMinor=rubles11(tariff)
                require(tariffMinor<=Int.MAX_VALUE){"Тариф вне диапазона"}
                db.addLight11(assetId,selectedPlace,
                    watts.replace(',','.').toDoubleOrNull()?:-1.0,
                    hours.replace(',','.').toDoubleOrNull()?:-1.0,
                    tariffMinor.toInt(),
                    requireNotNull(atDate11(date)),
                    ppfd.replace(',','.').toDoubleOrNull())
                onSaved()
            }catch(e:Exception){onError(e.message?:"Проверь параметры лампы")}
        }){Text("Сохранить")}},
        dismissButton={TextButton(onClick=onClose){Text("Отмена")}})
}
