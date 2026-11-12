package ru.dikoveriya.lab

import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DikoveriyaApp() }
    }
}

@Composable
fun DikoveriyaApp() {
    val context=androidx.compose.ui.platform.LocalContext.current
    val db=remember{LabDatabase(context.applicationContext)}
    var refresh by remember { mutableIntStateOf(0) }
    var tab by rememberSaveable { mutableStateOf("Главная") }
    var tabHistory by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var routeStack by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val stateHolder=rememberSaveableStateHolder()
    val currentRoute=Lab07Navigation.current(routeStack)
    val activeNavigationTab=when(currentRoute?.substringBefore(':')) {
        "collection","variety"->"Коллекция"
        "container","growth","medium","operations","history","work","groups","places"->"Коллекция"
        "tasks"->"Главная"
        else->if(tab=="Аналитика")"Ещё" else tab
    }
    fun openRoute(next:String) {
        // The Collection tab itself is already a level: do not insert it twice.
        routeStack=Lab07Navigation.enter(routeStack,next)
    }
    fun backRoute() { routeStack=Lab07Navigation.back(routeStack) }
    var subsection by rememberSaveable { mutableStateOf("") }
    var dialog by rememberSaveable { mutableStateOf("") }
    var cultureId by remember { mutableStateOf<Long?>(null) }
    var photoCultureId by remember { mutableStateOf<Long?>(null) }
    var saleCultureId by remember { mutableStateOf<Long?>(null) }
    var labelCultureId by remember { mutableStateOf<Long?>(null) }
    var legacyAssessmentId by remember { mutableStateOf<Long?>(null) }
    var labelForExport by remember { mutableStateOf<LabLabelSpec?>(null) }
    var preselectedPlantId by rememberSaveable { mutableStateOf<Long?>(null) }
    var actionCultureId by remember { mutableStateOf<Long?>(null) }
    var actionPreferredMode by rememberSaveable { mutableStateOf("") }
    var recipeId by remember { mutableStateOf<Long?>(null) }
    var advancedRecipeId by remember { mutableStateOf<Long?>(null) }
    var preselectedRecipeId by remember { mutableStateOf<Long?>(null) }
    var batchId by remember { mutableStateOf<Long?>(null) }
    var materialId by remember { mutableStateOf<Long?>(null) }
    var experimentId by remember { mutableStateOf<Long?>(null) }
    var pendingPhotoId by remember { mutableStateOf<Long?>(null) }
    var pendingPhotoNote by remember { mutableStateOf("") }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var lastExitRequest by remember { mutableLongStateOf(0L) }
    fun act(block: ()->Unit): Boolean = try { block(); refresh++; true } catch(e:Exception) { error=e.message?:"Не удалось сохранить данные"; false }

    val plants=remember(refresh){db.plants()}
    val mothers=remember(refresh){db.mothers()}
    val cultures=remember(refresh,tab,currentRoute) {
        if(tab=="Главная" && currentRoute==null)db.cultures(limit=5)
        else db.cultures()
    }
    val recipes=remember(refresh){db.recipes()}
    val ingredients=remember(refresh){db.ingredients()}
    val batches=remember(refresh){db.batches()}
    val tasks=remember(refresh,tab,currentRoute) {
        if(tab=="Главная" && currentRoute==null) emptyList<TaskRecord>()
        else db.tasks()
    }
    val dashboardCounts=remember(refresh){db.dashboard072()}
    val materials=remember(refresh){db.materials()}
    val equipment=remember(refresh){db.equipment()}
    val incidents=remember(refresh){db.incidents()}
    val experiments=remember(refresh){db.experiments()}
    val sales=remember(refresh){db.sales()}
    val metrics=remember(refresh,tab){if(tab=="Аналитика")db.recipeMetrics() else emptyList()}
    val cultivarResults=remember(refresh,tab){if(tab=="Аналитика")db.plantResults() else emptyList()}
    val mediumResults=remember(refresh,tab){if(tab=="Аналитика")db.mediumResults() else emptyList()}
    val running=cultures.filter{it.status in listOf(LabTerms.IN_CULTURE,LabTerms.CONTROL,LabTerms.QUARANTINE)}
    val attention=running.count{it.status!=LabTerms.IN_CULTURE}
    val dueTasks=dashboardCounts.tasksDue

    val saveBackup=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")){uri->
        if(uri!=null) act { context.contentResolver.openOutputStream(uri)?.use{LabBackup.save(context,db,it)} ?: throw IllegalStateException("Нет доступа к файлу") }
    }
    val chooseBackup=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null) restoreUri=uri}
    val exportRecipes=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")){uri->
        if(uri!=null) act { context.contentResolver.openOutputStream(uri)?.use{it.write(db.exportRecipeCsv().toByteArray(Charsets.UTF_8))}?:throw IllegalStateException("Не удалось сохранить CSV") }
    }
    val importRecipes=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->
        if(uri!=null) act {
            val data=context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use{it.readText()}?:throw IllegalStateException("Не удалось прочитать CSV")
            db.importRecipeCsv(data)
        }
    }
    val selectPhoto=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){uri->
        val id=pendingPhotoId
        if(uri!=null && id!=null) act{db.addPhoto(context,id,uri,pendingPhotoNote)}
        pendingPhotoId=null
        pendingPhotoNote=""
    }
    val saveLabel=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")){uri->
        val spec=labelForExport
        if(uri!=null && spec!=null) {
            act { context.contentResolver.openOutputStream(uri)?.use{it.write(LabLabel40.png(spec))}
                ?:throw IllegalStateException("Нет доступа к файлу") }
        }
        labelForExport=null
    }
    val scanner=rememberLauncherForActivityResult(ScanContract()){result->
        if(result.contents!=null) {
            val code=LabQr.getCode(result.contents)
            val id=db.findCulture072(code)
            if(id!=null) { tab="Коллекция";routeStack=Lab07Navigation.enter(emptyList(),Lab07Navigation.container(id)) } else error="Код не найден в этой базе. Проверь этикетку и резервную копию."
        }
    }
    val scan= { scanner.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setPrompt("Наведите камеру на QR-код контейнера").setBeepEnabled(false).setOrientationLocked(false)) }
    val openSection: (String)->Unit = {section->
        when(section){
            "Задачи"->openRoute(Lab07Navigation.TASKS)
            "Группы контейнеров"->openRoute(Lab07Navigation.GROUPS)
            "Места хранения"->openRoute(Lab07Navigation.PLACES)
            else->{
                val target=when(section){"Питательные среды"->"Среды";"Аналитика"->"Аналитика";else->"Ещё"}
                if(tab!=target)tabHistory=tabHistory+tab
                tab=target;routeStack=emptyList();subsection=if(target=="Ещё")section else ""
            }
        }
    }
    // A single back policy is shared by toolbar and Android gestures.
    fun goBack() {
        when {
            routeStack.isNotEmpty() -> backRoute()
            subsection.isNotEmpty() -> subsection=""
            tabHistory.isNotEmpty() -> {tab=tabHistory.last();tabHistory=tabHistory.dropLast(1);subsection=""}
            tab!="Главная" -> {tab="Главная";subsection=""}
            else -> {
                val now=SystemClock.elapsedRealtime()
                if(now-lastExitRequest<2200L)(context as? android.app.Activity)?.finish()
                else {lastExitRequest=now;Toast.makeText(context,"Повтори жест «Назад» для выхода",Toast.LENGTH_SHORT).show()}
            }
        }
    }
    BackHandler(enabled=dialog.isEmpty() && cultureId==null && photoCultureId==null && saleCultureId==null && labelCultureId==null && legacyAssessmentId==null && actionCultureId==null &&
        recipeId==null && advancedRecipeId==null && batchId==null && materialId==null && experimentId==null && restoreUri==null && error==null) {
        goBack()
    }

    DikoveriyaTheme {
        Scaffold(containerColor=MaterialTheme.colorScheme.background,bottomBar={
            NavigationBar(containerColor=Lab07Palette.DeepForest,tonalElevation=0.dp) {
                listOf("Главная","Коллекция","Среды","Ещё").forEachIndexed{index, name->
                    val icon=when(index){0->Icons.Outlined.Home;1->Icons.Outlined.LocalFlorist;2->Icons.Outlined.Science;else->Icons.Outlined.Menu}
                    NavigationBarItem(
                        selected=activeNavigationTab==name,
                        onClick={
                            if(name=="Главная")tabHistory=emptyList() else if(tab!=name)tabHistory=tabHistory+tab
                            tab=name;subsection="";routeStack=emptyList()
                        },
                        icon={Icon(icon,contentDescription=null)},
                        label={Text(when(name){"Главная"->"Обзор";"Коллекция"->"Коллекция";else->name},maxLines=1)},
                        colors=NavigationBarItemDefaults.colors(
                            selectedIconColor=Lab07Palette.Brass,
                            selectedTextColor=Lab07Palette.Champagne,
                            indicatorColor=Lab07Palette.Forest,
                            unselectedIconColor=Color(0xFFCCD4C8),
                            unselectedTextColor=Color(0xFFCCD4C8))
                    )
                }
            }
        }){ padding ->
            if(currentRoute!=null || tab=="Коллекция") {
                val route=currentRoute ?: Lab07Navigation.COLLECTION
                stateHolder.SaveableStateProvider(route) {
                    Lab07RouteScreen(
                        route=route,db=db,revision=refresh,
                        plants=plants,cultures=cultures,recipes=recipes,
                        onBack={goBack()},
                        onNavigate={openRoute(it)},
                        onNewContainer={plantId->preselectedPlantId=plantId;dialog="newCulture"},
                        onNewPlant={dialog="addPlant"},
                        onEdit={cultureId=it},
                        onLabel={labelCultureId=it},
                        onPhotos={photoCultureId=it},
                        onSale={saleCultureId=it},
                        onLegacyAssessment={legacyAssessmentId=it},
                        onAction={id,mode->actionPreferredMode=mode;actionCultureId=id},
                        onChanged={refresh++},
                        onError={error=it},
                        modifier=Modifier.fillMaxSize().padding(padding)
                    )
                }
            } else if(tab=="Среды") {
                stateHolder.SaveableStateProvider("medium-catalog") {
                    Lab071MediumCatalog(
                        plants=plants,recipes=recipes,batches=batches,
                        ingredients=ingredients,revision=refresh,
                        onCreate={dialog="newRecipe"},
                        onRecipe={recipeId=it},
                        onNewBatch={dialog="prepareBatch"},
                        onBatch={batchId=it},
                        onAddIngredient={dialog="newIngredient"},
                        onIngredient={dialog="ingredientInfo:$it"},
                        onImport={importRecipes.launch(arrayOf("text/csv","text/plain","application/octet-stream"))},
                        onExport={exportRecipes.launch("dikoveriya-recipes.csv")},
                        modifier=Modifier.fillMaxSize().padding(padding)
                    )
                }
            } else {
            stateHolder.SaveableStateProvider("legacy:$tab:$subsection") {
            LazyColumn(state=rememberLazyListState(),modifier=Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(horizontal=16.dp,vertical=16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                item {
                    Row(verticalAlignment=Alignment.CenterVertically){
                        Icon(painterResource(R.drawable.ic_dikoveriya_logo),contentDescription=null,
                            tint=Color.Unspecified,modifier=Modifier.size(40.dp))
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text("ДИКОВЕРИЯ LAB",color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.labelLarge)
                            Text(if(subsection.isNotEmpty())subsection else when(tab){"Главная"->"Моя лаборатория";else->tab},style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
                        }
                        IconButton(onClick=scan){Icon(Icons.Outlined.QrCodeScanner,contentDescription="Сканировать контейнер")}
                    }
                }
                when(tab){
                    "Главная" -> {
                        item(key="botanical-home") {
                            Lab07Dashboard(
                                plants,cultures,tasks,
                                onCollection={openRoute(Lab07Navigation.COLLECTION)},
                                onContainer={openRoute(Lab07Navigation.container(it))},
                                onNewContainer={preselectedPlantId=null;dialog="newCulture"},
                                onNewPlant={dialog="addPlant"},
                                onMediums={openSection("Питательные среды")},
                                onScan=scan,
                                counts=dashboardCounts,
                                onMetric={key->if(key=="tasks")openRoute(Lab07Navigation.TASKS)
                                    else openRoute(Lab07Navigation.work(key))}
                            )
                        }
                    }
                    "Аналитика" -> {
                        item { Text("Всего контейнеров: ${cultures.size} · растений: ${plants.size}") }
                        item { Text("Активных: ${running.size} · на контроле: $attention · завершённых: ${cultures.size-running.size}") }
                        item { Text("Продаж: ${sales.size}; выручка по записям: ${money(sales.sumOf{it.price})}") }
                        item { SectionTitle("Результаты по сортам") }
                        items(cultivarResults,key={"results-${it.plantId}"}) { r ->
                            Lab07ReadOnlyCard(r.name,"Контейнеров: ${r.containers} · эксплантов: ${r.lastExplants} · побегов: ${r.lastShoots} · растений: ${r.lastPlants} · выручка: ${money(r.revenue)}")
                        }
                        item { SectionTitle("Рост по рецептурам") }
                        item { Text("Последнее наблюдение каждого контейнера на данной рецептуре; разные контейнеры могут иметь разные исходные условия.",style=MaterialTheme.typography.bodySmall) }
                        items(mediumResults,key={"medium-stats-${it.recipeId}"}) { r ->
                            Lab07ReadOnlyCard(r.name,"Контейнеров: ${r.cultures} · наблюдений для сравнения: ${r.observations} · побегов: ${r.latestShoots} · растений: ${r.latestPlants}")
                        }
                        item { SectionTitle("Архивные наблюдения по партиям") }
                        item { Text("Только по зарегистрированным измерениям. Без повторностей сравнение предварительное.",color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall) }
                        if(metrics.isEmpty()) item { Text("Нет измерений из прежнего журнала партий. Новый журнал находится в карточке контейнера → Рост и результаты.") }
                        items(metrics,key={"metric-${it.recipe}"}){m->
                            Lab07ReadOnlyCard(m.recipe,"Осмотров: ${m.count} · исходных объектов: ${m.input} · архивная категория «здоровых»: ${m.healthy} · заражённых: ${m.infected}. Без коэффициента размножения: тип материала не подтверждён.")
                        }
                        item { SectionTitle("По сортам") }
                        items(plants,key={"stat-${it.id}"}){p->
                            val subset=cultures.filter{it.plantId==p.id}
                            Lab07ReadOnlyCard(p.name,"Контейнеров: ${subset.size}. Остатки в разных единицах нельзя складывать; смотри каждый контейнер.")
                        }
                        item { Text("Показатели по рецептам не являются доказательством причинно-следственной связи: нужен сопоставимый опыт.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    "Ещё" -> {
                        if(subsection.isEmpty()){
                            items(listOf("Аналитика","Маточники","Задачи","Группы контейнеров","Места хранения","Склад","Оборудование","Инциденты","Эксперименты","Продажи","Резервные копии","О программе")){label->
                                TextEntryRow(label){openSection(label)}
                                HorizontalDivider()
                            }
                        } else {
                            item{TextButton(onClick={subsection=""}){Text("‹ Ко всем разделам")}}
                            when(subsection){
                                "Маточники" -> {
                                    item{Button(onClick={dialog="addMother"}){Text("Добавить маточное растение")}}
                                    if(mothers.isEmpty())item{Text("Маточников пока нет.")}
                                    items(mothers,key={"mother-${it.id}"}){m->
                                        SmallCard(m.plantName,"${m.code} · ${m.origin} · ${m.status}"){dialog="motherInfo:${m.id}"}
                                    }
                                }
                                "Склад" -> {
                                    item { Button(onClick={dialog="newMaterial"}){Text("+ Материал")}}
                                    items(materials,key={"material-${it.id}"}){m->SmallCard(m.name,"Остаток: ${RecipeCalculator.formatAmount(m.quantity)} ${m.unit}; минимум ${RecipeCalculator.formatAmount(m.minQuantity)}") {materialId=m.id}}
                                }
                                "Оборудование" -> {
                                    item{Button(onClick={dialog="newEquipment"}){Text("+ Оборудование")}}
                                    items(equipment,key={"equipment-${it.id}"}){e->SmallCard(e.name,"${e.location} · ${e.status}"){dialog="equipmentInfo:${e.id}"}}
                                }
                                "Инциденты" -> {
                                    item{Button(onClick={dialog="newIncident"}){Text("+ Зарегистрировать происшествие")}}
                                    items(incidents,key={"incident-${it.id}"}){i->SmallCard(i.title,"${formatDate(i.time)} · ${i.status} · ${i.note}"){dialog="incidentInfo:${i.id}"}}
                                }
                                "Эксперименты" -> {
                                    item{Button(onClick={dialog="newExperiment"}){Text("+ Эксперимент")}}
                                    items(experiments,key={"experiment-${it.id}"}){e->SmallCard(e.title,"${e.status} · ${formatDate(e.startedAt)} · ${e.hypothesis}"){experimentId=e.id}}
                                }
                                "Продажи" -> {
                                    item{Text("Продажа оформляется из карточки растения на адаптации, доращивании или готового к продаже.")}
                                    items(sales,key={"sale-${it.id}"}){s->Lab07ReadOnlyCard(s.cultureCode,"${s.quantity} шт. · ${money(s.price)} · ${formatDate(s.time)}")}
                                }
                                "Резервные копии" -> {
                                    item{Text("Резервная копия ZIP содержит всю локальную базу и оригиналы фотографий. Храни её отдельно от телефона.")}
                                    item{Button(onClick={saveBackup.launch("dikoveriya-backup-${System.currentTimeMillis()}.zip")}){Text("Сохранить копию")}}
                                    item{OutlinedButton(onClick={chooseBackup.launch(arrayOf("application/zip","application/octet-stream"))}){Text("Восстановить из файла")}}
                                    item{Text("Восстановление полностью заменяет текущие записи. Перед ним рекомендуется сохранить текущую копию.",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
                                }
                                else -> {
                                    item{Text("Версия 0.7.2 · локальная база SQLite v9. Приложение работает без интернета.")}
                                    item{Text("Google Sheets/Drive, прямое подключение NIIMBOT и ИИ-аналитик пока не подключены. Для этикеток есть сохранение QR-кода PNG.")}
                                    item{Text("Названия пассажей скрыты из обычных экранов. В технических данных и аналитике они сохраняются.")}
                                }
                            }
                        }
                    }
                }
            }
            }
            }
            when(dialog){
                "addPlant" -> AddPlantDialog(onClose={dialog=""},onSave={name,notes->if(act{db.addPlant(name,notes)})dialog=""})
                "addMother" -> AddMotherDialog(plants,onClose={dialog=""},onSave={p,origin,note->if(act{db.addMother(p,origin,note)})dialog=""})
                "newCulture" -> QuickCultureDialog(db,preselectedRecipeId,preselectedPlantId,onClose={dialog="";preselectedRecipeId=null;preselectedPlantId=null},
                    onSaved={id->dialog="";preselectedRecipeId=null;preselectedPlantId=null;refresh++;openRoute(Lab07Navigation.container(id))},onChanged={refresh++})
                "newIngredient" -> AddIngredientDialog(onClose={dialog=""},onSave={name,category,unit,notes->
                    if(act{db.addIngredient(name,category,unit,notes)})dialog=""
                })
                "newRecipe" -> QuickMediumDialog(db,onClose={dialog=""},onSaved={id->dialog="";refresh++;recipeId=id},
                    onUseExisting={id->dialog="";recipeId=id})
                "batchChooser" -> ChooseBatchRecipeDialog(recipes,onClose={dialog=""},onPick={recipeId=it;dialog=""})
                "prepareBatch" -> Lab071PrepareBatchDialog(db,onClose={dialog=""},onSaved={dialog="";refresh++})
                "newTask" -> AddTaskDialog(cultures,onClose={dialog=""},onSave={title,due,c->if(act{db.createTask(title,due,c)})dialog=""})
                "newMaterial" -> AddMaterialDialog(onClose={dialog=""},onSave={n,u,q,m,c->if(act{db.addMaterial(n,u,q,m,c)})dialog=""})
                "newEquipment" -> AddEquipmentDialog(onClose={dialog=""},onSave={n,l->if(act{db.addEquipment(n,l)})dialog=""})
                "newIncident" -> AddIncidentDialog(cultures,onClose={dialog=""},onSave={t,n,c->if(act{db.addIncident(t,n,c)})dialog=""})
                "newExperiment" -> AddExperimentDialog(onClose={dialog=""},onSave={t,h->if(act{db.addExperiment(t,h)})dialog=""})
            }
            if(dialog.startsWith("ingredientInfo:")){
                val ing=ingredients.firstOrNull{it.id.toString()==dialog.substringAfter(':')}
                if(ing!=null) DialogForm(ing.name,close={dialog=""}){
                    Text("Категория: ${ing.category} · ${ing.unit}")
                    Text(ing.notes.ifBlank{"Дополнительные сведения не указаны"})
                }
            }
            if(dialog.startsWith("plantInfo:")){
                val p=plants.firstOrNull{it.id.toString()==dialog.substringAfter(':')}
                if(p!=null) DialogForm(p.name,close={dialog=""}){Text(p.description.ifEmpty{"Дополнительных сведений пока нет"})}
            }
            if(dialog.startsWith("motherInfo:")){
                val m=mothers.firstOrNull{it.id.toString()==dialog.substringAfter(':')}
                if(m!=null) DialogForm(m.plantName,close={dialog=""}){Text("Маточник ${m.code}\nПроисхождение: ${m.origin}\nСостояние: ${m.status}")}
            }
            if(dialog.startsWith("equipmentInfo:")){
                val e=equipment.firstOrNull{it.id.toString()==dialog.substringAfter(':')}
                if(e!=null) DialogForm(e.name,close={dialog=""}){Text("Место: ${e.location}\nСостояние: ${e.status}")}
            }
            if(dialog.startsWith("incidentInfo:")){
                val i=incidents.firstOrNull{it.id.toString()==dialog.substringAfter(':')}
                if(i!=null && i.status=="Аннулирован") DialogForm(i.title,close={dialog=""}){Text("Происшествие аннулировано. История сохранена. "+i.note)}
                else if(i!=null) IncidentReviewDialog(i,db.incidentCultures(i.id),db.incidentFollowups(i.id),onClose={dialog=""},onSave={status,note->if(act{db.updateIncident(i.id,status,note)})dialog=""})
            }
            cultureId?.let{id->cultures.firstOrNull{it.id==id}?.let{culture->
                val medium=recipes.firstOrNull{it.id==culture.recipeId}
                Lab071PassportDialog(culture,medium?.let{"${it.name} v${it.version}"},
                    onClose={cultureId=null},
                    onSave={status,location,reason->
                        if(act{db.updateCulture(id,culture.quantity,status,location,culture.batchId,reason,
                            culture.recipeId,true)})cultureId=null
                    })
            }}
            saleCultureId?.let{id->cultures.firstOrNull{it.id==id}?.let{culture->
                Lab071SaleDialog(culture,onClose={saleCultureId=null},
                    onSave={qty,total,note->
                        if(act{db.sale(id,qty,total,note)})saleCultureId=null
                    })
            }}
            photoCultureId?.let{id->cultures.firstOrNull{it.id==id}?.let{culture->
                Lab071PhotosDialog(culture,db.photos(id),context.filesDir,
                    onClose={photoCultureId=null},
                    onAdd={note->pendingPhotoId=id;pendingPhotoNote=note;selectPhoto.launch("image/*")})
            }}
            labelCultureId?.let{id->cultures.firstOrNull{it.id==id}?.let{culture->
                val medium=recipes.firstOrNull{it.id==culture.recipeId}
                Lab071LabelDialog(culture,medium?.let{"${it.name} v${it.version}"},
                    onClose={labelCultureId=null},
                    onExport={spec->
                        labelForExport=spec
                        saveLabel.launch("dikoveriya-${spec.code}-40x12.png")
                    },
                    onShare={spec->
                        try{LabLabel40.share(context,spec)}
                        catch(e:Exception){error=e.message?:"Не удалось передать этикетку"}
                    })
            }}
            legacyAssessmentId?.let{id->cultures.firstOrNull{it.id==id}?.let{culture->
                Lab071LegacyAssessmentDialog(culture,onClose={legacyAssessmentId=null},
                    onSave={note,start,healthy,infected,days->
                        if(act{db.addObservation(id,note,start,healthy,infected,days)})legacyAssessmentId=null
                    })
            }}
            actionCultureId?.let{id->cultures.firstOrNull{it.id==id}?.let{c->
                stateHolder.SaveableStateProvider("action:$id:$actionPreferredMode") {
                LabActionDialog(c,cultures,batches,recipes,db.operationsForCulture(id),onClose={actionCultureId=null},initialMode=actionPreferredMode,
                    onSplit={parts,lost,note,date->if(act{db.splitCulture(id,parts,lost,note,date)})actionCultureId=null},
                    onMerge={parts,out,b,stage,note,confirm,date,recipe->if(act{db.mergeCultures(parts,out,b,stage,note,confirm,date,recipe)})actionCultureId=null},
                    onLoss={lost,reason,note,date->if(act{db.recordLoss(id,lost,reason,note,date)})actionCultureId=null},
                    onCorrection={quantity,reason,date->if(act{db.correctQuantity(id,quantity,reason,date)})actionCultureId=null},
                    onClarify={kind,quantity,reason,date->if(act{db.clarifyQuantityUnit(id,kind,quantity,reason,date)})actionCultureId=null},
                    onVoid={reason,date->if(act{db.voidCulture(id,reason,date)})actionCultureId=null},
                    onIncident={title,notes,affected,response,date->if(act{db.reportIncident(title,notes,affected,response,date)})actionCultureId=null},
                    onCancel={op,reason->if(act{db.cancelOperation(op,reason)})actionCultureId=null}
                )
                }
            }}
            recipeId?.let{id->recipes.firstOrNull{it.id==id}?.let{r->
                EasyRecipeDialog(db,r,plants.firstOrNull{it.id==r.plantId}?.name,onClose={recipeId=null},
                    onUse={target->recipeId=null;preselectedRecipeId=target;dialog="newCulture"},
                    onAdvanced={advancedRecipeId=id;recipeId=null})
            }}
            advancedRecipeId?.let{id->recipes.firstOrNull{it.id==id}?.let{r->
                RecipeDialog(r,db.recipeParts(id),ingredients,onClose={advancedRecipeId=null},
                    onAdd={name,amount,unit,sugar,agar,stock->act{db.addRecipePart(id,name,amount,unit,sugar,agar,stock)}},
                    onRemove={part->act{db.removeRecipePart(part)}},
                    onActivate={act{db.activateRecipe(id)}},
                    onRevise={if(act{db.reviseRecipe(id)})advancedRecipeId=null},
                    onPrepare={volume,ph,sterilization,note,actual->if(act{db.prepareBatch(id,volume,ph,sterilization,note,actual)})advancedRecipeId=null}
                )
            }}
            batchId?.let{id->batches.firstOrNull{it.id==id}?.let{b->
                BatchDialog(b,db.batchSnapshot(id),onClose={batchId=null},onReject={reason->if(act{db.rejectBatch(id,reason)})batchId=null})
            }}
            materialId?.let{id->materials.firstOrNull{it.id==id}?.let{m->
                MaterialDialog(m,onClose={materialId=null},onMove={delta,note->if(act{db.moveMaterial(id,delta,note)})materialId=null})
            }}
            experimentId?.let{id->experiments.firstOrNull{it.id==id}?.let{e->
                ExperimentDialog(e,cultures,onClose={experimentId=null},onAdd={c,g->if(act{db.enrollExperiment(id,c,g)})experimentId=null})
            }}
            restoreUri?.let{uri->AlertDialog(onDismissRequest={restoreUri=null},title={Text("Заменить все данные?")},text={Text("Это удалит текущие записи и заменит их содержимым копии. Отменить после восстановления нельзя. Сделай текущую копию перед продолжением.")},confirmButton={TextButton(onClick={
                if(act{context.contentResolver.openInputStream(uri)?.use{LabBackup.restore(context,db,it)}?:throw IllegalStateException("Файл недоступен")}) { restoreUri=null }
            }){Text("Да, восстановить",color=MaterialTheme.colorScheme.error)}},dismissButton={TextButton(onClick={restoreUri=null}){Text("Отмена")}})}
            error?.let{message->AlertDialog(onDismissRequest={error=null},title={Text("Не удалось выполнить действие")},text={Text(message)},confirmButton={TextButton(onClick={error=null}){Text("Понятно")}})}
        }
    }
}

