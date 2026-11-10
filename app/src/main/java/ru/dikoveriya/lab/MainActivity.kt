package ru.dikoveriya.lab

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    var tab by remember { mutableStateOf("Главная") }
    var subsection by remember { mutableStateOf("") }
    var dialog by remember { mutableStateOf("") }
    var cultureId by remember { mutableStateOf<Long?>(null) }
    var recipeId by remember { mutableStateOf<Long?>(null) }
    var batchId by remember { mutableStateOf<Long?>(null) }
    var materialId by remember { mutableStateOf<Long?>(null) }
    var experimentId by remember { mutableStateOf<Long?>(null) }
    var qrCode by remember { mutableStateOf<String?>(null) }
    var pendingPhotoId by remember { mutableStateOf<Long?>(null) }
    var pendingPhotoNote by remember { mutableStateOf("") }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("Все") }
    fun act(block: ()->Unit): Boolean = try { block(); refresh++; true } catch(e:Exception) { error=e.message?:"Не удалось сохранить данные"; false }

    val plants=remember(refresh){db.plants()}
    val mothers=remember(refresh){db.mothers()}
    val cultures=remember(refresh){db.cultures()}
    val recipes=remember(refresh){db.recipes()}
    val batches=remember(refresh){db.batches()}
    val tasks=remember(refresh){db.tasks()}
    val materials=remember(refresh){db.materials()}
    val equipment=remember(refresh){db.equipment()}
    val incidents=remember(refresh){db.incidents()}
    val experiments=remember(refresh){db.experiments()}
    val sales=remember(refresh){db.sales()}
    val metrics=remember(refresh){db.recipeMetrics()}
    val running=cultures.filter{it.status in listOf(LabTerms.IN_CULTURE,LabTerms.CONTROL,LabTerms.QUARANTINE)}
    val attention=running.count{it.status!=LabTerms.IN_CULTURE}
    val dueTasks=tasks.count{!it.done && it.dueAt<=System.currentTimeMillis()}

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
    val saveQr=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")){uri->
        val code=qrCode
        if(uri!=null && code!=null) act { context.contentResolver.openOutputStream(uri)?.use{it.write(LabQr.png(code))}?:throw IllegalStateException("Нет доступа к файлу") }
        qrCode=null
    }
    val scanner=rememberLauncherForActivityResult(ScanContract()){result->
        if(result.contents!=null) {
            val code=LabQr.getCode(result.contents)
            val c=cultures.firstOrNull{it.code==code}
            if(c!=null) { tab="Работа";cultureId=c.id } else error="Код не найден в этой базе. Проверь этикетку и резервную копию."
        }
    }
    val scan= { scanner.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setPrompt("Наведите камеру на QR-код контейнера").setBeepEnabled(false).setOrientationLocked(false)) }
    val openSection: (String)->Unit = {tab="Ещё";subsection=it}
    val openCulture: (Long)->Unit = {cultureId=it}

    MaterialTheme(colorScheme=lightColorScheme(primary=Color(0xFF255B47))) {
        Scaffold(bottomBar={
            NavigationBar {
                listOf("Главная","Растения","Работа","Аналитика","Ещё").forEachIndexed{index, name->
                    val icon=when(index){0->Icons.Outlined.Home;1->Icons.Outlined.LocalFlorist;2->Icons.Outlined.Science;3->Icons.Outlined.BarChart;else->Icons.Outlined.Menu}
                    NavigationBarItem(selected=tab==name,onClick={tab=name;subsection=""},icon={Icon(icon,contentDescription=null)},label={Text(name,maxLines=1)})
                }
            }
        }){ padding ->
            LazyColumn(modifier=Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(horizontal=16.dp,vertical=16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                item {
                    Row(verticalAlignment=Alignment.CenterVertically){
                        Column(Modifier.weight(1f)) {
                            Text("ДИКОВЕРИЯ LAB",color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.labelLarge)
                            Text(if(subsection.isNotEmpty())subsection else when(tab){"Главная"->"Моя лаборатория";else->tab},style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
                        }
                        IconButton(onClick=scan){Icon(Icons.Outlined.QrCodeScanner,contentDescription="Сканировать контейнер")}
                    }
                }
                when(tab){
                    "Главная" -> {
                        item {
                            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                                InfoCard("В культуре",running.count{it.stage==LabTerms.IN_VITRO}.toString(),Modifier.weight(1f))
                                InfoCard("На контроле",attention.toString(),Modifier.weight(1f))
                            }
                        }
                        item {
                            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                                InfoCard("На адаптации",running.count{it.stage==LabTerms.ADAPTATION}.toString(),Modifier.weight(1f))
                                InfoCard("Задач сейчас",dueTasks.toString(),Modifier.weight(1f))
                            }
                        }
                        item { SectionTitle("Быстрые действия") }
                        item {
                            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                                Button(onClick={dialog="newCulture"},modifier=Modifier.weight(1f)){Text("Новый контейнер")}
                                OutlinedButton(onClick={openSection("Питательные среды")},modifier=Modifier.weight(1f)){Text("Приготовить среду")}
                            }
                        }
                        item {
                            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                                OutlinedButton(onClick={dialog="addPlant"},modifier=Modifier.weight(1f)){Text("Новое растение")}
                                OutlinedButton(onClick=scan,modifier=Modifier.weight(1f)){Text("Сканировать")}
                            }
                        }
                        item { SectionTitle("Последние контейнеры") }
                        if(cultures.isEmpty()) item{Text("Пока нет контейнеров. Добавь первый.")}
                        items(cultures.take(8),key={"home-${it.id}"}){c->CultureCard(c){openCulture(c.id)}}
                        if(dueTasks>0){item{TextEntryRow("Есть задачи, требующие внимания: $dueTasks"){openSection("Задачи")}}}
                    }
                    "Растения" -> {
                        item { Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                            Button(onClick={dialog="addPlant"}){Text("+ Растение")}
                            OutlinedButton(onClick={dialog="addMother"}){Text("+ Маточник")}
                        } }
                        item { SectionTitle("Каталог · ${plants.size}") }
                        items(plants,key={"plant-${it.id}"}){p->SmallCard(p.name,p.description.ifEmpty{"В каталоге"}){dialog="plantInfo:${p.id}"}}
                        item { SectionTitle("Маточные растения · ${mothers.size}") }
                        items(mothers,key={"mother-${it.id}"}){m->SmallCard(m.plantName,"${m.code} · ${m.origin} · ${m.status}"){dialog="motherInfo:${m.id}"}}
                    }
                    "Работа" -> {
                        item { Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                            Button(onClick={dialog="newCulture"}){Text("+ Контейнер")}
                            OutlinedButton(onClick={openSection("Задачи")}){Text("Задачи")}
                        } }
                        item { Field("Поиск по названию, номеру, месту",search,{search=it}) }
                        item { PickOne("Показать",listOf("Все","Активные","На контроле","Архив"),statusFilter,{statusFilter=it}) }
                        val filtered=cultures.filter{c->
                            (search.isBlank()||listOf(c.code,c.plantName,c.location).any{it.contains(search,true)}) && when(statusFilter){
                                "Активные"->c.status==LabTerms.IN_CULTURE
                                "На контроле"->c.status==LabTerms.CONTROL||c.status==LabTerms.QUARANTINE
                                "Архив"->c.status in listOf(LabTerms.DISPOSED,LabTerms.TRANSFERRED,LabTerms.SOLD)
                                else->true
                            }
                        }
                        item { Text("Найдено: ${filtered.size}",color=MaterialTheme.colorScheme.onSurfaceVariant) }
                        items(filtered,key={"work-${it.id}"}){c->CultureCard(c){openCulture(c.id)}}
                    }
                    "Аналитика" -> {
                        item { Text("Всего контейнеров: ${cultures.size} · растений: ${plants.size}") }
                        item { Text("Активных: ${running.size} · на контроле: $attention · завершённых: ${cultures.size-running.size}") }
                        item { Text("Продаж: ${sales.size}; выручка по записям: ${money(sales.sumOf{it.price})}") }
                        item { SectionTitle("Эффективность питательных сред") }
                        item { Text("Только по зарегистрированным измерениям. Без повторностей сравнение предварительное.",color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall) }
                        if(metrics.isEmpty()) item { Text("Пока нет измерений, связанных с партиями сред. Открой контейнер → Осмотр → Записать показатели.") }
                        items(metrics,key={"metric-${it.recipe}"}){m->
                            val ratio=if(m.input>0)m.healthy.toDouble()/m.input else null
                            SmallCard(m.recipe,"Наблюдений: ${m.count} · выход: ${ratio?.let{RecipeCalculator.formatAmount(it)}?:"—"}× · отмечено заражений: ${m.infected} · средний срок: ${RecipeCalculator.formatAmount(m.averageDays)} дн.") {}
                        }
                        item { SectionTitle("По сортам") }
                        items(plants,key={"stat-${it.id}"}){p->
                            val subset=cultures.filter{it.plantId==p.id}
                            SmallCard(p.name,"Контейнеров: ${subset.size}; живых объектов в активных: ${subset.filter{it.status==LabTerms.IN_CULTURE}.sumOf{it.quantity}}"){}
                        }
                        item { Text("Показатели по рецептам не являются доказательством причинно-следственной связи: нужен сопоставимый опыт.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    "Ещё" -> {
                        if(subsection.isEmpty()){
                            items(listOf("Питательные среды","Задачи","Склад","Оборудование","Инциденты","Эксперименты","Продажи","Резервные копии","О программе")){label->
                                TextEntryRow(label){openSection(label)}
                                HorizontalDivider()
                            }
                        } else {
                            item{TextButton(onClick={subsection=""}){Text("‹ Ко всем разделам")}}
                            when(subsection){
                                "Питательные среды" -> {
                                    item { Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                                        Button(onClick={dialog="newRecipe"}){Text("+ Рецептура")}
                                        OutlinedButton(onClick={dialog="batchChooser"}){Text("Приготовить")}
                                    } }
                                    item { Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                                        OutlinedButton(onClick={importRecipes.launch(arrayOf("text/csv","text/plain","application/octet-stream"))}){Text("Импорт CSV")}
                                        OutlinedButton(onClick={exportRecipes.launch("dikoveriya-recipes.csv")}){Text("Экспорт CSV")}
                                    } }
                                    item { Text("CSV можно редактировать в Excel или Google Sheets. После импорта создаётся новая версия-черновик; старые приготовления не изменяются. Автоматическое подключение Google пока не настроено.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
                                    item { SectionTitle("Рецептуры") }
                                    items(recipes,key={"recipe-${it.id}"}){r->SmallCard(r.name,"${r.purpose} · версия ${r.version} · ${r.status}"){recipeId=r.id}}
                                    item { SectionTitle("Партии приготовлений") }
                                    items(batches,key={"batch-${it.id}"}){b->SmallCard(b.code,"${b.recipeName} · ${b.volumeMl} мл · ${b.status} · ${formatDate(b.preparedAt)}"){batchId=b.id}}
                                }
                                "Задачи" -> {
                                    item{Button(onClick={dialog="newTask"}){Text("+ Задача")}}
                                    if(tasks.isEmpty())item{Text("Нет задач")}
                                    items(tasks,key={"task-${it.id}"}){t->
                                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                                            Checkbox(checked=t.done,onCheckedChange={act{db.setTaskDone(t.id,it)}})
                                            Column{Text(t.title);Text(formatDate(t.dueAt),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                                        }
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
                                    items(sales,key={"sale-${it.id}"}){s->SmallCard(s.cultureCode,"${s.quantity} шт. · ${money(s.price)} · ${formatDate(s.time)}") {}}
                                }
                                "Резервные копии" -> {
                                    item{Text("Резервная копия ZIP содержит всю локальную базу и оригиналы фотографий. Храни её отдельно от телефона.")}
                                    item{Button(onClick={saveBackup.launch("dikoveriya-backup-${System.currentTimeMillis()}.zip")}){Text("Сохранить копию")}}
                                    item{OutlinedButton(onClick={chooseBackup.launch(arrayOf("application/zip","application/octet-stream"))}){Text("Восстановить из файла")}}
                                    item{Text("Восстановление полностью заменяет текущие записи. Перед ним рекомендуется сохранить текущую копию.",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
                                }
                                else -> {
                                    item{Text("Версия 0.3.0-dev · локальная база SQLite. Приложение работает без интернета.")}
                                    item{Text("Google Sheets/Drive, прямое подключение NIIMBOT и ИИ-аналитик пока не подключены. Для этикеток есть сохранение QR-кода PNG.")}
                                    item{Text("Названия пассажей скрыты из обычных экранов. В технических данных и аналитике они сохраняются.")}
                                }
                            }
                        }
                    }
                }
            }
            when(dialog){
                "addPlant" -> AddPlantDialog(onClose={dialog=""},onSave={name,notes->if(act{db.addPlant(name,notes)})dialog=""})
                "addMother" -> AddMotherDialog(plants,onClose={dialog=""},onSave={p,origin,note->if(act{db.addMother(p,origin,note)})dialog=""})
                "newCulture" -> AddCultureDialog(plants,mothers,batches,onClose={dialog=""},onSave={p,q,m,b,place,note->if(act{db.addCulture(p,q,m,b,place,note)})dialog=""})
                "newRecipe" -> AddRecipeDialog(plants,onClose={dialog=""},onSave={name,purpose,p,note->if(act{db.createRecipe(name,purpose,p,note)})dialog=""})
                "batchChooser" -> ChooseBatchRecipeDialog(recipes,onClose={dialog=""},onPick={recipeId=it;dialog=""})
                "newTask" -> AddTaskDialog(cultures,onClose={dialog=""},onSave={title,due,c->if(act{db.createTask(title,due,c)})dialog=""})
                "newMaterial" -> AddMaterialDialog(onClose={dialog=""},onSave={n,u,q,m,c->if(act{db.addMaterial(n,u,q,m,c)})dialog=""})
                "newEquipment" -> AddEquipmentDialog(onClose={dialog=""},onSave={n,l->if(act{db.addEquipment(n,l)})dialog=""})
                "newIncident" -> AddIncidentDialog(cultures,onClose={dialog=""},onSave={t,n,c->if(act{db.addIncident(t,n,c)})dialog=""})
                "newExperiment" -> AddExperimentDialog(onClose={dialog=""},onSave={t,h->if(act{db.addExperiment(t,h)})dialog=""})
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
                if(i!=null) DialogForm(i.title,close={dialog=""}){Text(i.note)}
            }
            cultureId?.let{id->cultures.firstOrNull{it.id==id}?.let{c->
                CultureDialog(c,db.events(id),db.photos(id),context.filesDir,db.ancestors(id),batches,onClose={cultureId=null},
                    onChange={q,st,loc,b,n->if(act{db.updateCulture(id,q,st,loc,b,n)})cultureId=null},
                    onObserve={n,start,healthy,infected,days->if(act{db.addObservation(id,n,start,healthy,infected,days)})cultureId=null},
                    onTransfer={used,out,b,stage,n->if(act{db.transfer(id,used,out,b,stage,n)})cultureId=null},
                    onSale={q,total,n->if(act{db.sale(id,q,total,n)})cultureId=null},
                    onQr={qrCode=c.code;saveQr.launch("${c.code}.png")},
                    onPhoto={note->pendingPhotoId=c.id;pendingPhotoNote=note;selectPhoto.launch("image/*")}
                )
            }}
            recipeId?.let{id->recipes.firstOrNull{it.id==id}?.let{r->
                RecipeDialog(r,db.recipeParts(id),onClose={recipeId=null},
                    onAdd={name,amount,unit,sugar,agar,stock->act{db.addRecipePart(id,name,amount,unit,sugar,agar,stock)}},
                    onRemove={part->act{db.removeRecipePart(part)}},
                    onActivate={act{db.activateRecipe(id)}},
                    onRevise={if(act{db.reviseRecipe(id)})recipeId=null},
                    onPrepare={volume,ph,sterilization,note,actual->if(act{db.prepareBatch(id,volume,ph,sterilization,note,actual)})recipeId=null}
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

@Composable
private fun InfoCard(label:String,value:String,modifier:Modifier=Modifier){
    ElevatedCard(modifier=modifier){Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
        Text(value,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
        Text(label,style=MaterialTheme.typography.bodySmall)
    }}
}
@Composable
private fun CultureCard(c:CultureRecord,onClick:()->Unit){
    SmallCard(c.plantName,"${c.code} · ${c.status} · ${c.stage} · ${c.quantity} шт.${if(c.location.isNotBlank())" · ${c.location}" else ""}",onClick)
}
