package ru.dikoveriya.lab

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Full-screen hierarchy (Collection -> Variety -> Container -> 4 sections).
 * SaveableStateProvider in MainActivity retains each destination's scroll, filters and drafts.
 */
@Composable
fun Lab07RouteScreen(
    route:String, db:LabDatabase, revision:Int,
    plants:List<PlantRecord>,cultures:List<CultureRecord>,recipes:List<RecipeRecord>,
    onBack:()->Unit,onNavigate:(String)->Unit,
    onNewContainer:(Long?)->Unit,onNewPlant:()->Unit,
    onEdit:(Long)->Unit,onAction:(Long,String)->Unit,
    onLabel:(Long)->Unit,onPhotos:(Long)->Unit,onSale:(Long)->Unit,
    onLegacyAssessment:(Long)->Unit,
    onChanged:()->Unit,onError:(String)->Unit,
    modifier:Modifier=Modifier
) {
    val id=route.substringAfter(':',"").toLongOrNull()
    val culture=cultures.firstOrNull{it.id==id}
    when(route.substringBefore(':')) {
        "work" -> {
            val groupId=if(route.startsWith("work:group:"))
                route.substringAfterLast(':').toLongOrNull() else null
            Lab072RegistryScreen(
                mode=if(groupId!=null)"all" else route.substringAfter(':'),
                initialGroupId=groupId,
                db=db,revision=revision,plants=plants,recipes=recipes,
                onBack=onBack,onNavigate=onNavigate,
                onChanged=onChanged,onError=onError,modifier=modifier)
        }
        "tasks" -> Lab072TasksScreen(db,revision,plants,recipes,
            onBack,onNavigate,onChanged,onError,modifier)
        "groups" -> Lab072GroupsScreen(db,revision,onBack,onNavigate,modifier)
        "places" -> Lab072PlacesScreen(db,revision,onBack,onChanged,onError,modifier)
        "collection" -> Lab07CollectionScreen(plants,cultures,onBack,onNavigate,onNewPlant,modifier)
        "variety" -> {
            val plant=plants.firstOrNull{it.id==id}
            if(plant!=null) Lab07VarietyScreen(plant,cultures,onBack,onNavigate,onNewContainer,modifier)
            else Lab07MissingScreen(onBack,modifier)
        }
        "container" -> if(culture!=null) Lab07ContainerScreen(culture,db,revision,onBack,onNavigate,onEdit,onAction,onLabel,onPhotos,onSale,modifier)
                       else Lab07MissingScreen(onBack,modifier)
        "growth" -> if(culture!=null) Lab07GrowthScreen(culture,db,revision,onBack,onChanged,onError,onLegacyAssessment,modifier)
                    else Lab07MissingScreen(onBack,modifier)
        "medium" -> if(culture!=null) Lab07MediumScreen(culture,db,revision,recipes,onBack,onChanged,onError,modifier)
                    else Lab07MissingScreen(onBack,modifier)
        "operations" -> if(culture!=null) Lab07OperationsScreen(culture,db,revision,onBack,onNavigate,onAction,modifier)
                        else Lab07MissingScreen(onBack,modifier)
        "history" -> if(culture!=null) Lab07HistoryScreen(culture,db,revision,onBack,modifier)
                     else Lab07MissingScreen(onBack,modifier)
        else -> Lab07MissingScreen(onBack,modifier)
    }
}

@Composable
private fun Lab07MissingScreen(onBack:()->Unit,modifier:Modifier=Modifier) {
    Column(modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(15.dp)) {
        Lab07ScreenHeader("ДИКОВЕРИЯ / НАВИГАЦИЯ","Запись недоступна",
            "Возможно, контейнер удалён или запись не существует.",onBack)
    }
}

@Composable
private fun Lab07Filters(values:List<String>,selected:String,onSelect:(String)->Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement=Arrangement.spacedBy(7.dp)) {
        values.forEach{value->
            FilterChip(
                selected=value==selected,
                onClick={onSelect(value)},
                modifier=Modifier.heightIn(min=48.dp),
                label={Text(value,maxLines=1)},
                colors=FilterChipDefaults.filterChipColors(
                    selectedContainerColor=Lab07Palette.Forest,
                    selectedLabelColor=Lab07Palette.Champagne)
            )
        }
    }
}

@Composable
private fun Lab07CollectionScreen(
    plants:List<PlantRecord>,cultures:List<CultureRecord>,
    onBack:()->Unit,onNavigate:(String)->Unit,onNewPlant:()->Unit,modifier:Modifier
) {
    var query by rememberSaveable { mutableStateOf("") }
    var order by rememberSaveable { mutableStateOf("По названию") }
    var view by rememberSaveable { mutableStateOf("Сорта") }
    var status by rememberSaveable { mutableStateOf("Все") }
    val counted=plants.map{plant->plant to cultures.count{it.plantId==plant.id}}
    val ordered=when(order) {
        "Больше контейнеров" -> counted.sortedWith(compareByDescending<Pair<PlantRecord,Int>>{it.second}.thenBy{it.first.name})
        else -> counted.sortedBy{it.first.name.lowercase()}
    }
    val filtered=ordered.filter{it.first.name.contains(query,true) || it.first.description.contains(query,true)}
    val visibleCultures=cultures.filter{culture->
        (query.isBlank() || listOf(culture.code,culture.plantName,culture.location)
            .any{it.contains(query,true)}) &&
        (status=="Все" || when(status) {
            "Активные" -> culture.status in setOf(LabTerms.IN_CULTURE,LabTerms.CONTROL,LabTerms.QUARANTINE)
            "Контроль" -> culture.status in setOf(LabTerms.CONTROL,LabTerms.QUARANTINE)
            "Архив" -> culture.status in setOf(LabTerms.DISPOSED,LabTerms.TRANSFERRED,LabTerms.SOLD,LabTerms.ANNULLED)
            else -> true
        })
    }.sortedByDescending{it.createdAt}
    LazyColumn(modifier=modifier,state=rememberLazyListState(),contentPadding=PaddingValues(16.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item(key="header") {
            Lab07ScreenHeader("ДИКОВЕРИЯ  /  КОЛЛЕКЦИЯ","Коллекция редкостей",
                "Выбери сорт, чтобы увидеть все его контейнеры и маточные линии.",onBack)
        }
        item(key="chooser") {
            Lab07Filters(listOf("Сорта","Контейнеры"),view,{view=it})
        }
        item(key="hero") { Lab07BrandHero("Живой каталог",
            "${plants.size} сортов · ${cultures.size} контейнеров","АТЛАС ИССЛЕДОВАТЕЛЯ") }
        item(key="search") { Field(if(view=="Сорта")"Поиск сорта" else "Поиск номера, растения, места",query,{query=it}) }
        if(view=="Сорта") item(key="order") {
            Lab07Filters(listOf("По названию","Больше контейнеров"),order,{order=it})
        } else item(key="status-filter") {
            Lab07Filters(listOf("Все","Активные","Контроль","Архив"),status,{status=it})
        }
        if(view=="Сорта") item(key="new-plant") {
            OutlinedButton(onClick=onNewPlant,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                Icon(Icons.Outlined.Add,null);Spacer(Modifier.width(7.dp));Text("Добавить сорт")
            }
        }
        if(view=="Сорта" && filtered.isEmpty()) item(key="empty") {
            Text(if(plants.isEmpty())"В коллекции пока нет сортов." else "По запросу ничего не найдено.",
                style=MaterialTheme.typography.bodyMedium)
        }
        if(view=="Сорта") items(filtered,key={"plant-${it.first.id}"}){entry->
            val plant=entry.first
            OutlinedCard(onClick={onNavigate(Lab07Navigation.variety(plant.id))},
                modifier=Modifier.fillMaxWidth().heightIn(min=88.dp),
                colors=CardDefaults.outlinedCardColors(containerColor=MaterialTheme.colorScheme.surface),
                shape=RoundedCornerShape(17.dp),
                border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
                Row(Modifier.fillMaxWidth().padding(15.dp),verticalAlignment=Alignment.CenterVertically) {
                    Icon(Icons.Outlined.LocalFlorist,null,tint=Lab07Palette.Forest,
                        modifier=Modifier.size(30.dp))
                    Spacer(Modifier.width(13.dp))
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                        Text(plant.name,style=MaterialTheme.typography.titleMedium,
                            maxLines=3,overflow=TextOverflow.Ellipsis)
                        Text("${entry.second} контейнеров  ·  Открыть сорт",
                            style=MaterialTheme.typography.bodySmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Outlined.ChevronRight,"Открыть сорт",tint=Lab07Palette.InkBrass)
                }
            }
        }
        if(view=="Контейнеры" && visibleCultures.isEmpty()) item(key="empty-container") {
            Text("Контейнеры не найдены. Измени фильтр или создай новый контейнер.")
        }
        if(view=="Контейнеры") items(visibleCultures,key={"all-${it.id}"}){culture->
            Lab07CultureRow(culture){onNavigate(Lab07Navigation.container(culture.id))}
        }
    }
}

@Composable
private fun Lab07VarietyScreen(
    plant:PlantRecord,cultures:List<CultureRecord>,
    onBack:()->Unit,onNavigate:(String)->Unit,onNewContainer:(Long?)->Unit,modifier:Modifier
) {
    var query by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable { mutableStateOf("Все") }
    val related=cultures.filter{it.plantId==plant.id}
    val shown=related.filter { culture ->
        (culture.code.contains(query,true)||culture.location.contains(query,true)) &&
        (status=="Все" || when(status) {
            "Активные" -> culture.status in setOf(LabTerms.IN_CULTURE,LabTerms.CONTROL,LabTerms.QUARANTINE)
            "На контроле" -> culture.status in setOf(LabTerms.CONTROL,LabTerms.QUARANTINE)
            "Архив" -> culture.status in setOf(LabTerms.DISPOSED,LabTerms.SOLD,LabTerms.TRANSFERRED,LabTerms.ANNULLED)
            else -> true
        })
    }.sortedByDescending{it.createdAt}
    LazyColumn(modifier=modifier,state=rememberLazyListState(),contentPadding=PaddingValues(16.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item(key="header") {
            Lab07ScreenHeader("КОЛЛЕКЦИЯ  /  СОРТ",plant.name,
                plant.description.ifBlank{"Карточка сорта · учёт биологического материала"},onBack)
        }
        item(key="hero") {
            Lab07BrandHero(plant.name,"${related.size} контейнеров · ${related.count{it.status==LabTerms.IN_CULTURE}} в культуре",
                "БОТАНИЧЕСКИЙ АТЛАС")
        }
        item(key="new") {
            Button(onClick={onNewContainer(plant.id)},modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) {
                Icon(Icons.Outlined.AddCircleOutline,null);Spacer(Modifier.width(8.dp));Text("Создать контейнер")
            }
        }
        item(key="search") { Field("Поиск номера или места",query,{query=it}) }
        item(key="filters") {
            Lab07Filters(listOf("Все","Активные","На контроле","Архив"),status,{status=it})
        }
        item(key="count") {
            Text("Контейнеры  ·  ${shown.size}",style=MaterialTheme.typography.titleMedium)
        }
        if(shown.isEmpty()) item(key="empty") {
            Text("Подходящих контейнеров нет.",style=MaterialTheme.typography.bodyMedium)
        }
        items(shown,key={"culture-${it.id}"}){culture->
            Lab07CultureRow(culture){onNavigate(Lab07Navigation.container(culture.id))}
        }
    }
}

@Composable
private fun Lab07ContainerScreen(
    culture:CultureRecord,db:LabDatabase,revision:Int,
    onBack:()->Unit,onNavigate:(String)->Unit,onEdit:(Long)->Unit,
    onAction:(Long,String)->Unit,onLabel:(Long)->Unit,onPhotos:(Long)->Unit,
    onSale:(Long)->Unit,modifier:Modifier
) {
    val last=remember(culture.id,revision){db.unifiedJournal(culture.id).firstOrNull()}
    val activeMedium=remember(culture.id,revision){db.currentMedium(culture.id)}
    val closed=culture.status in setOf(LabTerms.DISPOSED,LabTerms.TRANSFERRED,LabTerms.SOLD,LabTerms.ANNULLED)
    LazyColumn(modifier=modifier,state=rememberLazyListState(),
        contentPadding=PaddingValues(horizontal=16.dp,vertical=16.dp),
        verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item(key="header") {
            Lab07ScreenHeader("КОЛЛЕКЦИЯ / КОНТЕЙНЕР",culture.code,culture.plantName,onBack)
        }
        item(key="hero") {
            Lab07BrandHero(culture.plantName,
                "${culture.code} · ${culture.stage} · ${culture.status}",
                "РАБОЧЕЕ МЕСТО · ДИКОВЕРИЯ LAB")
        }
        item(key="summary") {
            Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),
                border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.fillMaxWidth().padding(15.dp),
                    verticalArrangement=Arrangement.spacedBy(7.dp)) {
                    Text("Остаток: ${culture.quantity} · ${culture.quantityKind}",
                        style=MaterialTheme.typography.titleMedium)
                    Text("Среда: ${activeMedium?.let{"${it.name} · v${it.version}"} ?: "не указана"}")
                    Text("Место: ${culture.location.ifBlank{"не указано"}}")
                    if(culture.quantityKind=="Не уточнено")
                        Text("Сначала уточни единицу учёта: нельзя списывать неподтверждённые объекты.",
                            style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick={onEdit(culture.id)},
                        modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                        Icon(Icons.Outlined.Edit,null)
                        Spacer(Modifier.width(8.dp))
                        Text("Изменить паспорт: статус и место")
                    }
                }
            }
        }
        item(key="primary-title"){SectionTitle("Сделать сейчас")}
        if(!closed) {
            item(key="observe-now") {
                Lab07QuickAction("Записать наблюдение",
                    {Icon(Icons.Outlined.AddChart,null)},true,
                    onClick={onNavigate(Lab07Navigation.growth(culture.id))})
            }
            item(key="two-actions") {
                Row(horizontalArrangement=Arrangement.spacedBy(9.dp)) {
                    Lab07QuickAction("Пересадить",
                        {Icon(Icons.Outlined.SwapHoriz,null)},modifier=Modifier.weight(1f),
                        onClick={onAction(culture.id,"Пересадка")})
                    Lab07QuickAction("Списать потери",
                        {Icon(Icons.Outlined.RemoveCircleOutline,null)},modifier=Modifier.weight(1f),
                        onClick={onAction(culture.id,"Потери")})
                }
            }
        }
        item(key="labels") {
            Row(horizontalArrangement=Arrangement.spacedBy(9.dp)) {
                Lab07QuickAction("Этикетка 40×12",
                    {Icon(Icons.Outlined.QrCode,null)},modifier=Modifier.weight(1f),
                    onClick={onLabel(culture.id)})
                Lab07QuickAction("Фотографии",
                    {Icon(Icons.Outlined.PhotoLibrary,null)},modifier=Modifier.weight(1f),
                    onClick={onPhotos(culture.id)})
            }
        }
        item(key="details-title"){SectionTitle("Подробнее")}
        item(key="tiles-1") {
            Row(horizontalArrangement=Arrangement.spacedBy(9.dp)) {
                Lab07ActionTile("Рост","График и замеры",
                    Lab07Palette.Forest,{Icon(Icons.Outlined.TrendingUp,null)},
                    Modifier.weight(1f)){onNavigate(Lab07Navigation.growth(culture.id))}
                Lab07ActionTile("Среда","Рецепт и смена",
                    Color(0xFF294E4A),{Icon(Icons.Outlined.Science,null)},
                    Modifier.weight(1f)){onNavigate(Lab07Navigation.medium(culture.id))}
            }
        }
        item(key="tiles-2") {
            Row(horizontalArrangement=Arrangement.spacedBy(9.dp)) {
                Lab07ActionTile("Операции","Все действия",
                    Color(0xFF3E5134),{Icon(Icons.Outlined.SwapHoriz,null)},
                    Modifier.weight(1f)){onNavigate(Lab07Navigation.operations(culture.id))}
                Lab07ActionTile("История","События и аудит",
                    Color(0xFF304238),{Icon(Icons.Outlined.History,null)},
                    Modifier.weight(1f)){onNavigate(Lab07Navigation.history(culture.id))}
            }
        }
        if(!closed && culture.stage!=LabTerms.IN_VITRO && culture.quantityKind=="Растения") {
            item(key="sale") {
                OutlinedButton(onClick={onSale(culture.id)},
                    modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) {
                    Icon(Icons.Outlined.Sell,null)
                    Spacer(Modifier.width(8.dp))
                    Text("Оформить продажу растений")
                }
            }
        }
        item(key="last") {
            SectionTitle("Последняя запись")
            Text(last?.let{"${formatDate(it.occurredAt)} · ${it.kind} · ${it.summary()}"}
                ?: "Записей пока нет",style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Lab07GrowthScreen(
    culture:CultureRecord,db:LabDatabase,revision:Int,onBack:()->Unit,
    onChanged:()->Unit,onError:(String)->Unit,onLegacyAssessment:(Long)->Unit,
    modifier:Modifier
) {
    var explants by rememberSaveable { mutableStateOf("") }
    var shoots by rememberSaveable { mutableStateOf("") }
    var plants by rememberSaveable { mutableStateOf("") }
    var infected by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var plotted by rememberSaveable { mutableStateOf("Побеги") }
    var saved by rememberSaveable { mutableStateOf("") }
    val samples=remember(culture.id,revision){db.growthSamples(culture.id)}
    val closed=culture.status in setOf(LabTerms.DISPOSED,LabTerms.SOLD,LabTerms.ANNULLED,LabTerms.TRANSFERRED)
    fun parseCount(text:String):Int? = if(text.isBlank())null else text.toIntOrNull() ?: error("Некорректное число")
    LazyColumn(modifier=modifier,state=rememberLazyListState(),contentPadding=PaddingValues(16.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item(key="header") {
            Lab07ScreenHeader("${culture.code}  /  РОСТ","Рост и наблюдения",
                "Независимые измерения; пустое поле не означает ноль.",onBack)
        }
        item(key="plot-title") { SectionTitle("Динамика измерений") }
        item(key="plot-filter") {
            Lab07Filters(listOf("Побеги","Растения","Экспланты","Заражено"),plotted,{plotted=it})
        }
        item(key="plot") { Lab07ObservationPlot(samples,plotted) }
        if(!closed) {
            item(key="observation-title") { SectionTitle("Новое наблюдение") }
            item(key="explants") { Field("Эксплантов",explants,{explants=it.filter(Char::isDigit)},numeric=true) }
            item(key="shoots") { Field("Побегов",shoots,{shoots=it.filter(Char::isDigit)},numeric=true) }
            item(key="plants") { Field("Растений",plants,{plants=it.filter(Char::isDigit)},numeric=true) }
            item(key="infected") { Field("Заражено",infected,{infected=it.filter(Char::isDigit)},numeric=true) }
            item(key="note") { Field("Описание осмотра / условия",note,{note=it},single=false) }
            item(key="save") {
                Button(onClick={
                    try {
                        db.addGrowthSample(culture.id,
                            parseCount(explants),parseCount(shoots),
                            parseCount(plants),parseCount(infected),note)
                        explants="";shoots="";plants="";infected="";note=""
                        saved="Сохранено · ${formatDate(System.currentTimeMillis())}"
                        onChanged()
                    } catch(e:Exception) { onError(e.message ?: "Не удалось сохранить наблюдение") }
                },modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) {
                    Icon(Icons.Outlined.Save,null);Spacer(Modifier.width(8.dp));Text("Сохранить наблюдение")
                }
                if(saved.isNotBlank()) Text(saved,color=Lab07Palette.Forest,
                    style=MaterialTheme.typography.labelLarge)
            }
        } else item(key="closed") {
            Text("Контейнер закрыт. Добавление наблюдений недоступно, история сохранена.",
                style=MaterialTheme.typography.bodyMedium)
        }
        if(!closed)item(key="legacy-assessment") {
            OutlinedButton(onClick={onLegacyAssessment(culture.id)},
                modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) {
                Icon(Icons.Outlined.FactCheck,null)
                Spacer(Modifier.width(8.dp))
                Text("Расширенный осмотр · старый формат")
            }
            Text("Для совместимости с прежним журналом. Не используется для расчётов по известным единицам биоматериала.",
                style=MaterialTheme.typography.bodySmall)
        }
        item(key="timeline-title") { SectionTitle("История измерений · ${samples.size}") }
        if(samples.isEmpty()) item(key="empty") { Text("Пока нет измерений.") }
        items(samples,key={"sample-${it.id}"}){sample->
            val details=listOfNotNull(sample.explants?.let{"Эксплантов: $it"},
                sample.shoots?.let{"Побегов: $it"},sample.plants?.let{"Растений: $it"},
                sample.contaminated?.let{"Заражено: $it"},sample.notes.takeIf{it.isNotBlank()})
                .joinToString(" · ")
            OutlinedCard(Modifier.fillMaxWidth(),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.padding(15.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    Text((if(sample.kind=="baseline")"Исходное измерение" else "Наблюдение") + " · " + formatDate(sample.date),style=MaterialTheme.typography.titleMedium)
                    Text(details.ifBlank{"Замер не заполнен"},style=MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

data class Lab07MediumUse(val id:Long,val name:String,val version:Int,val date:Long,val reason:String)

fun LabDatabase.lab07MediumHistory(cultureId:Long):List<Lab07MediumUse> =
    readableDatabase.rawQuery("""
        SELECT h.id,r.name,r.version,h.applied_at,h.note
        FROM culture_recipe_history h JOIN recipes r ON r.id=h.recipe_id
        WHERE h.culture_id=? ORDER BY h.applied_at DESC,h.id DESC
    """.trimIndent(),arrayOf(cultureId.toString())).use{cursor->
        buildList{while(cursor.moveToNext())add(Lab07MediumUse(
            cursor.getLong(0),cursor.getString(1),cursor.getInt(2),
            cursor.getLong(3),cursor.getString(4)))}
    }

@Composable
private fun Lab07MediumScreen(
    culture:CultureRecord,db:LabDatabase,revision:Int,recipes:List<RecipeRecord>,
    onBack:()->Unit,onChanged:()->Unit,onError:(String)->Unit,modifier:Modifier
) {
    val active=remember(culture.id,revision){db.currentMedium(culture.id)}
    val history=remember(culture.id,revision){db.lab07MediumHistory(culture.id)}
    val suitable=recipes.filter{it.status=="Утверждена" && (it.plantId==null || it.plantId==culture.plantId)}
    var selected by rememberSaveable { mutableStateOf<Long?>(null) }
    var reason by rememberSaveable { mutableStateOf("") }
    var saved by rememberSaveable { mutableStateOf("") }
    val closed=culture.status in setOf(LabTerms.DISPOSED,LabTerms.SOLD,LabTerms.ANNULLED,LabTerms.TRANSFERRED)
    LazyColumn(modifier=modifier,state=rememberLazyListState(),contentPadding=PaddingValues(16.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item(key="header") {
            Lab07ScreenHeader("${culture.code}  /  СРЕДА","Питательная среда",
                "Текущий рецепт и история всех подтверждённых назначений.",onBack)
        }
        item(key="active") {
            Lab07BrandHero(active?.name ?: "Среда не указана",
                active?.let{"Версия ${it.version} · ${it.purpose}"}?:"Рецептура пока не назначена",
                "РЕЦЕПТУРА / ПРОИСХОЖДЕНИЕ")
        }
        item(key="rules") {
            Text("Рецептура имеет собственную версию. Партия приготовления необязательна. Изменение среды не изменяет количество биоматериала.",
                style=MaterialTheme.typography.bodyMedium)
        }
        if(!closed) {
            item(key="title") { SectionTitle("Сменить рецептуру") }
            item(key="choice") {
                PickOne("Утверждённая версия",listOf("Выбрать")+suitable.map{"${it.name} · v${it.version}"},
                    suitable.firstOrNull{it.id==selected}?.let{"${it.name} · v${it.version}"}?:"Выбрать",
                    {label->selected=suitable.firstOrNull{"${it.name} · v${it.version}"==label}?.id})
            }
            item(key="reason") { Field("Причина смены",reason,{reason=it},single=false) }
            item(key="save") {
                Button(
                    enabled=selected!=null && selected!=active?.id && reason.isNotBlank(),
                    onClick={
                        try {
                            db.assignMedium(culture.id,requireNotNull(selected),reason)
                            saved="Смена среды записана · ${formatDate(System.currentTimeMillis())}"
                            reason="";selected=null;onChanged()
                        } catch(e:Exception) {onError(e.message?:"Не удалось назначить среду")}
                    },modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) {
                    Text("Записать смену среды")
                }
                if(saved.isNotBlank()) Text(saved,color=Lab07Palette.Forest,
                    style=MaterialTheme.typography.labelLarge)
            }
        }
        item(key="history-title"){SectionTitle("История рецептур · ${history.size}")}
        if(history.isEmpty()) item(key="empty"){Text("Назначения среды пока не зарегистрированы.")}
        items(history,key={"medium-${it.id}"}){entry->
            Lab07ReadOnlyCard("${entry.name} · v${entry.version}",
                "${formatDate(entry.date)} · ${entry.reason}")
        }
    }
}

@Composable
private fun Lab07OperationsScreen(
    culture:CultureRecord,db:LabDatabase,revision:Int,
    onBack:()->Unit,onNavigate:(String)->Unit,onAction:(Long,String)->Unit,modifier:Modifier
) {
    val operations=remember(culture.id,revision){db.operationsForCulture(culture.id)}
    val closed=culture.status in setOf(LabTerms.DISPOSED,LabTerms.SOLD,LabTerms.ANNULLED,LabTerms.TRANSFERRED)
    val options=if(closed)listOf("Отменить действие","Происшествие") else
        (if(culture.quantityKind=="Не уточнено")listOf("Уточнить единицу учёта") else emptyList())+
        listOf("Пересадка","Объединить","Потери","Исправить ошибку","Происшествие","Отменить действие","Ошибочная запись")
    LazyColumn(modifier=modifier,state=rememberLazyListState(),
        contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item(key="header"){
            Lab07ScreenHeader("${culture.code}  /  ОПЕРАЦИИ","Операции",
                "Записи транзакционны: остаток, связь, причина и аудит сохраняются вместе.",onBack)
        }
        item(key="identity") {
            Lab07BrandHero(culture.code,
                "Остаток: ${culture.quantity} (${culture.quantityKind}) · ${culture.status}",
                "ЛАБОРАТОРНЫЕ ДЕЙСТВИЯ")
        }
        item(key="title") {SectionTitle("Что произошло?")}
        items(options,key={"action-$it"}){name->
            Lab07QuickAction(name,{Icon(when(name){
                "Пересадка"->Icons.Outlined.SwapHoriz
                "Потери"->Icons.Outlined.RemoveCircleOutline
                "Происшествие"->Icons.Outlined.ReportProblem
                "Исправить ошибку"->Icons.Outlined.Edit
                "Уточнить единицу учёта"->Icons.Outlined.CheckCircleOutline
                else->Icons.Outlined.FactCheck
            },null)},primary=name=="Пересадка",
                onClick={onAction(culture.id,name)})
        }
        item(key="medium") {
            OutlinedButton(onClick={onNavigate(Lab07Navigation.medium(culture.id))},
                modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){
                Icon(Icons.Outlined.Science,null);Spacer(Modifier.width(8.dp))
                Text("Смена среды — в карточке рецептуры")
            }
        }
        item(key="list-title") {SectionTitle("Проведённые операции · ${operations.size}")}
        if(operations.isEmpty()) item(key="empty"){Text("Операций пока нет.")}
        items(operations,key={"operation-${it.id}"}){op->
            Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),
                border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    Text(op.kind,style=MaterialTheme.typography.titleMedium)
                    Text("${formatDate(op.occurredAt)} · ${op.status}",
                        style=MaterialTheme.typography.bodySmall)
                    Text(op.note,style=MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

private fun Lab07HistoryFilter(entry:JournalEntry,filter:String):Boolean {
    val kind=entry.kind.lowercase()
    return when(filter) {
        "Рост" -> entry.source!="event" || "осмотр" in kind
        "Среды" -> "сред" in kind || "рецепт" in kind
        "Пересадки" -> "пересад" in kind || "объедин" in kind || "происхожд" in kind
        "Потери" -> "потер" in kind || "утилиз" in kind
        "Инциденты" -> "авари" in kind || "происшеств" in kind
        "Исправления" -> "исправ" in kind || "отмен" in kind || "уточнен" in kind
        "Продажи" -> "продаж" in kind
        else -> true
    }
}

@Composable
private fun Lab07HistoryScreen(
    culture:CultureRecord,db:LabDatabase,revision:Int,onBack:()->Unit,modifier:Modifier
) {
    var category by rememberSaveable { mutableStateOf("Все") }
    var period by rememberSaveable { mutableStateOf("Всё время") }
    var query by rememberSaveable { mutableStateOf("") }
    val journal=remember(culture.id,revision){db.unifiedJournal(culture.id)}
    val audit=remember(culture.id,revision){db.auditTrail(culture.id)}
    val now=System.currentTimeMillis()
    val periodDays=when(period){"7 дней"->7;"30 дней"->30;"90 дней"->90;else->null}
    val visible=journal.filter {entry->
        Lab07HistoryFilter(entry,category) &&
        (periodDays==null || entry.occurredAt>=now-periodDays*86400000L) &&
        (query.isBlank() || (entry.kind+" "+entry.note+" "+entry.summary()).contains(query,true))
    }
    LazyColumn(modifier=modifier,state=rememberLazyListState(),
        contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item(key="header"){
            Lab07ScreenHeader("${culture.code}  /  ИСТОРИЯ","История и аудит",
                "Единая хронология наблюдений, лабораторных действий и изменений.",onBack)
        }
        item(key="search") {Field("Поиск события",query,{query=it})}
        item(key="types") {
            Lab07Filters(listOf("Все","Рост","Среды","Пересадки","Потери","Инциденты","Исправления","Продажи"),
                category,{category=it})
        }
        item(key="period") {
            Lab07Filters(listOf("Всё время","7 дней","30 дней","90 дней"),period,{period=it})
        }
        item(key="count") {
            SectionTitle("Записи · ${visible.size}")
            Text("Исторические осмотры не преобразуются автоматически в показатели роста. Для операций показаны исходные и новые значения.",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if(visible.isEmpty()) item(key="empty") {
            Text("Записей по выбранным фильтрам нет.",style=MaterialTheme.typography.bodyMedium)
        }
        items(visible,key={"${it.source}-${it.sourceId}"}){entry->
            var expanded by rememberSaveable { mutableStateOf(false) }
            val changes=audit.filter{it.operationId!=null && it.operationId==entry.operationId}
            OutlinedCard(
                onClick={expanded=!expanded},
                modifier=Modifier.fillMaxWidth().heightIn(min=96.dp),
                colors=CardDefaults.outlinedCardColors(containerColor=MaterialTheme.colorScheme.surface),
                border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(Modifier.padding(15.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Text(entry.kind,style=MaterialTheme.typography.titleMedium,
                            modifier=Modifier.weight(1f))
                        Icon(if(expanded)Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            "Детали",tint=Lab07Palette.InkBrass)
                    }
                    Text(formatDate(entry.occurredAt),style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(entry.summary(),style=MaterialTheme.typography.bodyMedium)
                    if(expanded) {
                        Text("Источник: ${when(entry.source) {
                            "legacy"->"Архивный осмотр"
                            "measurement"->"Журнал измерений"
                            else->"Операция / событие"
                        }}",style=MaterialTheme.typography.bodySmall)
                        if(entry.recordedAt!=entry.occurredAt) {
                            Text("Внесено: ${formatDate(entry.recordedAt)}",
                                style=MaterialTheme.typography.bodySmall)
                        }
                        changes.forEach{record->
                            HorizontalDivider()
                            Text("Изменения до → после",fontWeight=FontWeight.SemiBold)
                            record.changes().forEach{line->
                                Text(line,style=MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
        if(category=="Все" && period=="Всё время" && query.isBlank()) {
            val unlinked=audit.filter{it.operationId==null}
            if(unlinked.isNotEmpty())item(key="audit-archival") {
                SectionTitle("Другие записи аудита")
                Text("${unlinked.size} записей о создании, миграции и отдельных изменениях. Неизвестные прошлые состояния не восстановлены догадкой.",
                    style=MaterialTheme.typography.bodySmall)
            }
            items(unlinked,key={"audit-${it.id}"}){row->
                Lab07ReadOnlyCard("${formatDate(row.recordedAt)} · ${row.source}",
                    row.changes().joinToString(" · "))
            }
        }
    }
}
