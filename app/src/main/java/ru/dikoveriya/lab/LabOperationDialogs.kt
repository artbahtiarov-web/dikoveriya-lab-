package ru.dikoveriya.lab

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import java.text.SimpleDateFormat
import java.util.Locale

/** Scenario-first UI: technical passage numbers and database IDs stay out of sight. */
@Composable
fun LabActionDialog(
    culture: CultureRecord,
    allCultures: List<CultureRecord>,
    batches: List<BatchRecord>,
    recipes: List<RecipeRecord>,
    operations: List<OperationRecord>,
    onClose: () -> Unit,
    onSplit: (List<TransferDestination>,Int,String,Long) -> Unit,
    onMerge: (List<MergeInput>,Int,Long?,String,String,Boolean,Long,Long?) -> Unit,
    onLoss: (Int,String,String,Long) -> Unit,
    onCorrection: (Int,String,Long) -> Unit,
    onClarify: (String,Int,String,Long) -> Unit,
    onVoid: (String,Long) -> Unit,
    onIncident: (String,String,List<Long>,String,Long) -> Unit,
    onCancel: (String,String) -> Unit
) {
    val isOpen=culture.status in listOf(LabTerms.IN_CULTURE,LabTerms.CONTROL,LabTerms.QUARANTINE)
    val choices=if(isOpen) (if(culture.quantityKind=="Не уточнено")listOf("Уточнить единицу учёта") else emptyList())+
        listOf("Пересадка","Объединить","Потери","Происшествие","Исправить ошибку","Отменить действие","Ошибочная запись")
        else listOf("Отменить действие","Происшествие")
    var mode by remember(culture.id){ mutableStateOf(choices.first()) }
    var reason by remember(culture.id,mode){ mutableStateOf("") }
    var happenedText by remember(culture.id,mode){ mutableStateOf(formatDate(System.currentTimeMillis())) }
    var acknowledged by remember(culture.id,mode){ mutableStateOf(false) }
    var sourceUsed by remember(culture.id,mode){ mutableStateOf(culture.quantity.toString()) }
    var resultQty by remember(culture.id,mode){ mutableStateOf("1") }
    var losses by remember(culture.id,mode){ mutableStateOf("0") }
    var nextStage by remember(culture.id,mode){ mutableStateOf(culture.stage) }
    var batchId by remember(culture.id,mode){ mutableStateOf<Long?>(null) }
    var mergeRecipeId by remember(culture.id,mode){ mutableStateOf<Long?>(null) }
    var confirmedKind by remember(culture.id,mode){ mutableStateOf("Экспланты") }
    var place by remember(culture.id,mode){ mutableStateOf(culture.location) }
    var lossReason by remember(culture.id,mode){ mutableStateOf("Заражение") }
    var newQuantity by remember(culture.id,mode){ mutableStateOf(culture.quantity.toString()) }
    var incidentTitle by remember(culture.id,mode){ mutableStateOf("") }
    var incidentAction by remember(culture.id,mode){ mutableStateOf("На контроле") }
    var extraSearch by remember(culture.id,mode){ mutableStateOf("") }
    val additional=remember(culture.id,mode){ mutableStateMapOf<Long,String>() }
    val daughtersUsed=remember(culture.id,mode){ mutableStateListOf(culture.quantity.toString()) }
    val daughtersProduced=remember(culture.id,mode){ mutableStateListOf(culture.quantity.toString()) }
    val daughtersStage=remember(culture.id,mode){ mutableStateListOf(culture.stage) }
    val daughtersBatch=remember(culture.id,mode){ mutableStateListOf<Long?>(null) }
    val daughtersRecipe=remember(culture.id,mode){ mutableStateListOf<Long?>(null) }
    val daughtersPlace=remember(culture.id,mode){ mutableStateListOf(culture.location) }
    val daughtersKind=remember(culture.id,mode){ mutableStateListOf(culture.quantityKind) }
    val extraIncident=remember(culture.id,mode){ mutableStateListOf<Long>() }
    val validOps=operations.filter{it.status=="Проведена" && it.kind in setOf("Разделение и пересадка","Объединение культур","Потери","Исправление учёта","Ошибочное создание","Авария")}
    var selectedOperation by remember(culture.id,mode){ mutableStateOf<String?>(validOps.firstOrNull()?.id) }
    fun timestamp():Long {
        val parser=SimpleDateFormat("dd.MM.yyyy HH:mm",Locale("ru")).apply{isLenient=false}
        return requireNotNull(parser.parse(happenedText.trim())) { "Укажи дату как 10.11.2026 16:30" }.time
    }
    val availableBatches=batches.filter{it.status=="Готова"}
    val availableRecipes=recipes.filter{it.status=="Утверждена" && (it.plantId==null || it.plantId==culture.plantId)}
    val validDate=runCatching{timestamp()}.getOrNull()
    val requiresNote=mode in listOf("Потери","Происшествие","Исправить ошибку","Отменить действие","Ошибочная запись","Уточнить единицу учёта")
    val needsAck=mode in listOf("Пересадка","Объединить","Потери","Происшествие","Отменить действие","Ошибочная запись","Уточнить единицу учёта")
    AlertDialog(onDismissRequest=onClose,title={Column{Text("Что произошло?");Text(culture.plantName+" · "+culture.code,style=MaterialTheme.typography.bodySmall)}},
        text={Column(Modifier.fillMaxWidth().heightIn(max=570.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){
            PickOne("Действие",choices,mode,{mode=it})
            if(culture.quantityKind=="Не уточнено" && mode in listOf("Пересадка","Объединить","Потери","Исправить ошибку")) Text("Для изменения количества сначала необходимо подтвердить единицу учёта контейнера. Сейчас она неизвестна; операция заблокирована, чтобы не списать другой тип биоматериала.",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
            when(mode){
                "Уточнить единицу учёта" -> {
                    Text("Подтверди, в каких объектах ведётся операционный остаток. Старое число нельзя без проверки считать растениями, побегами или эксплантами.",
                        style=MaterialTheme.typography.bodySmall)
                    PickOne("Тип объектов",LabTerms.quantityKinds.drop(1),confirmedKind,{confirmedKind=it})
                    Field("Проверенное количество",newQuantity,{newQuantity=it.filter(Char::isDigit)},numeric=true)
                    Text("До сохранения: ${culture.quantity} (${culture.quantityKind}). После сохранения оба значения останутся в аудите.",
                        style=MaterialTheme.typography.bodySmall)
                }
                "Пересадка" -> {
                    Text("Перенос материала в один или несколько новых контейнеров",fontWeight=FontWeight.Medium)
                    HelpIcon("Количество при пересадке","Из исходного контейнера вынимается указанное количество объектов. После деления может получиться больше побегов или растений. Оба числа сохраняются отдельно. Потери в процессе тоже учитываются.")
                    Text("Из исходного контейнера учитываются: ${culture.quantityKind}. Если тип не уточнён, операцию нельзя сохранить до уточнения учёта.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    daughtersUsed.indices.forEach{i->
                        HorizontalDivider()
                        Text("Новый контейнер ${i+1}",fontWeight=FontWeight.SemiBold)
                        Field("Взято из исходного",daughtersUsed[i],{daughtersUsed[i]=it.filter(Char::isDigit)},numeric=true)
                        Field("Получено пригодных",daughtersProduced[i],{daughtersProduced[i]=it.filter(Char::isDigit)},numeric=true)
                        PickOne("Следующий этап",LabTerms.stages,daughtersStage[i],{daughtersStage[i]=it})
                        RecipeChoice(availableRecipes,daughtersRecipe[i],{daughtersRecipe[i]=it})
                        BatchChoice(availableBatches,daughtersBatch[i],{daughtersBatch[i]=it})
                        PickOne("Что считаем",LabTerms.quantityKinds,daughtersKind[i],{daughtersKind[i]=it})
                        Field("Место",daughtersPlace[i],{daughtersPlace[i]=it})
                        if(daughtersUsed.size>1) TextButton(onClick={daughtersUsed.removeAt(i);daughtersProduced.removeAt(i);daughtersStage.removeAt(i);daughtersBatch.removeAt(i);daughtersRecipe.removeAt(i);daughtersPlace.removeAt(i);daughtersKind.removeAt(i)}){Text("Убрать этот контейнер")}
                    }
                    if(daughtersUsed.size<25)OutlinedButton(onClick={daughtersUsed.add("1");daughtersProduced.add("1");daughtersStage.add(culture.stage);daughtersBatch.add(null);daughtersRecipe.add(null);daughtersPlace.add(culture.location);daughtersKind.add(culture.quantityKind)}){Text("+ Ещё контейнер")}
                    Field("Потери во время операции",losses,{losses=it.filter(Char::isDigit)},numeric=true)
                    val spent=daughtersUsed.sumOf{it.toIntOrNull()?.toLong()?:0L}+(losses.toLongOrNull()?:0L)
                    Text("Из ${culture.quantity} исходных объектов будет израсходовано $spent; останется ${culture.quantity-spent}.",color=if(spent>culture.quantity)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall)
                }
                "Объединить" -> {
                    Text("Несколько источников в один новый контейнер",fontWeight=FontWeight.Medium)
                    HelpIcon("Объединение","Вся история каждого источника сохранится. Если линии или маточники различаются, приложение пометит происхождение как смешанное и не будет считать новую культуру подтверждённым клоном.")
                    Field("Из текущего контейнера",sourceUsed,{sourceUsed=it.filter(Char::isDigit)},numeric=true)
                    Field("Поиск второго источника",extraSearch,{extraSearch=it})
                    val options=allCultures.filter{it.id!=culture.id && it.plantId==culture.plantId && culture.quantityKind!="Не уточнено" && it.quantityKind==culture.quantityKind && it.status in listOf(LabTerms.IN_CULTURE,LabTerms.CONTROL,LabTerms.QUARANTINE) && (extraSearch.isBlank()||it.code.contains(extraSearch,true)||it.plantName.contains(extraSearch,true))}.take(40)
                    if(options.isEmpty())Text("Нет доступных контейнеров того же растения с таким же типом учёта (${culture.quantityKind}).")
                    options.forEach{candidate->
                        Row {
                            Checkbox(checked=additional.containsKey(candidate.id),onCheckedChange={selected->if(selected)additional[candidate.id]=candidate.quantity.toString() else additional.remove(candidate.id)})
                            Text(candidate.code+" · "+candidate.quantity+" шт.",modifier=Modifier.padding(top=13.dp))
                        }
                        if(additional.containsKey(candidate.id))Field("Берём из ${candidate.code}",additional[candidate.id].orEmpty(),{additional[candidate.id]=it.filter(Char::isDigit)},numeric=true)
                    }
                    Field("Сколько получилось после объединения",resultQty,{resultQty=it.filter(Char::isDigit)},numeric=true)
                    PickOne("Следующий этап",LabTerms.stages,nextStage,{nextStage=it})
                    RecipeChoice(availableRecipes,mergeRecipeId,{mergeRecipeId=it})
                    BatchChoice(availableBatches,batchId,{batchId=it})
                    Row{Checkbox(checked=acknowledged,onCheckedChange={acknowledged=it});Text("Я понимаю риск смешения происхождения",modifier=Modifier.padding(top=13.dp),style=MaterialTheme.typography.bodySmall)}
                }
                "Потери" -> {
                    PickOne("Причина",listOf("Заражение","Некроз / гибель","Механическое повреждение","Не установлена","Другое"),lossReason,{lossReason=it})
                    Field("Сколько потеряно (есть ${culture.quantity})",losses,{losses=it.filter(Char::isDigit)},numeric=true)
                    Text("Оставшиеся объекты сохранятся. Если списано всё, контейнер закроется без удаления истории.",style=MaterialTheme.typography.bodySmall)
                }
                "Происшествие" -> {
                    Field("Что случилось?",incidentTitle,{incidentTitle=it})
                    PickOne("Что сделать с затронутыми",listOf("Наблюдение","На контроле","Карантин"),incidentAction,{incidentAction=it})
                    Text("Текущий контейнер включён в происшествие. Можно добавить другие.",style=MaterialTheme.typography.bodySmall)
                    Field("Поиск контейнеров",extraSearch,{extraSearch=it})
                    allCultures.filter{it.id!=culture.id&&(extraSearch.isBlank()||it.code.contains(extraSearch,true)||it.plantName.contains(extraSearch,true))}.take(30).forEach{candidate->
                        Row{Checkbox(checked=extraIncident.contains(candidate.id),onCheckedChange={if(it)extraIncident.add(candidate.id) else extraIncident.remove(candidate.id)});Text(candidate.code+" · "+candidate.plantName,modifier=Modifier.padding(top=13.dp),style=MaterialTheme.typography.bodySmall)}
                    }
                    Text("Происшествие не списывает растения автоматически — потери регистрируются отдельным действием.",style=MaterialTheme.typography.bodySmall)
                }
                "Исправить ошибку" -> {
                    Field("Правильное количество",newQuantity,{newQuantity=it.filter(Char::isDigit)},numeric=true)
                    HelpIcon("Исправление учёта","Используй только для ошибочно введённого числа. Если объекты реально погибли, выбери «Потери». Исправления хранятся отдельно от коэффициента размножения.")
                }
                "Отменить действие" -> {
                    if(validOps.isEmpty())Text("Нет операций, доступных для безопасной отмены. Старые операции предыдущей версии можно исправлять отдельной записью.")
                    validOps.forEach{op->
                        Row {
                            RadioButton(selected=selectedOperation==op.id,onClick={selectedOperation=op.id})
                            Text("${op.kind} · ${formatDate(op.occurredAt)}",modifier=Modifier.padding(top=12.dp),style=MaterialTheme.typography.bodySmall)
                        }
                    }
                    Text("Отмена сработает, только если ни один затронутый контейнер не изменялся после этого действия. Все записи останутся в истории.",style=MaterialTheme.typography.bodySmall)
                }
                "Ошибочная запись" -> {
                    Text("Контейнер был создан ошибочно",fontWeight=FontWeight.Medium)
                    Text("Его можно аннулировать, если у него нет дочерних культур и продаж. Данные останутся в архиве.",style=MaterialTheme.typography.bodySmall)
                }
            }
            if(mode!="Отменить действие") { Field("Когда это произошло",happenedText,{happenedText=it})
                if(validDate==null)Text("Укажи дату и время как 10.11.2026 16:30",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
            }
            Field(if(requiresNote)"Причина / пояснение (обязательно)" else "Описание / примечание",reason,{reason=it},single=false)
            if(needsAck && mode!="Объединить")Row {
                Checkbox(checked=acknowledged,onCheckedChange={acknowledged=it})
                Text("Подтверждаю, что указанные данные верны",style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(top=12.dp))
            }
        }},confirmButton={TextButton(enabled=((mode !in listOf("Пересадка","Объединить","Потери","Исправить ошибку")) || culture.quantityKind!="Не уточнено")&&(!needsAck||acknowledged)&&(!requiresNote||reason.isNotBlank())&&(mode!="Происшествие"||incidentTitle.isNotBlank())&&(mode=="Отменить действие"||validDate!=null)&&(mode!="Отменить действие"||selectedOperation!=null),onClick={
            when(mode){
                "Пересадка" -> onSplit(daughtersUsed.indices.map{i->TransferDestination(daughtersUsed[i].toIntOrNull()?:0,daughtersProduced[i].toIntOrNull()?:0,daughtersStage[i],daughtersBatch[i],daughtersPlace[i],daughtersKind[i],daughtersRecipe[i])},losses.toIntOrNull()?:0,reason,requireNotNull(validDate))
                "Объединить" -> onMerge(listOf(MergeInput(culture.id,sourceUsed.toIntOrNull()?:0))+additional.map{MergeInput(it.key,it.value.toIntOrNull()?:0)},resultQty.toIntOrNull()?:0,batchId,nextStage,reason,acknowledged,requireNotNull(validDate),mergeRecipeId)
                "Потери" -> onLoss(losses.toIntOrNull()?:0,lossReason,reason,requireNotNull(validDate))
                "Исправить ошибку" -> onCorrection(newQuantity.toIntOrNull()?:0,reason,requireNotNull(validDate))
                "Уточнить единицу учёта" -> onClarify(confirmedKind,newQuantity.toIntOrNull()?:0,reason,requireNotNull(validDate))
                "Ошибочная запись" -> onVoid(reason,requireNotNull(validDate))
                "Происшествие" -> onIncident(incidentTitle,reason,listOf(culture.id)+extraIncident,incidentAction,requireNotNull(validDate))
                "Отменить действие" -> selectedOperation?.let{onCancel(it,reason)}
            }
        }){Text(if(mode=="Отменить действие")"Отменить операцию" else "Сохранить")}},dismissButton={TextButton(onClick=onClose){Text("Закрыть")}})
}

@Composable
private fun RecipeChoice(approved:List<RecipeRecord>,value:Long?,set:(Long?)->Unit) {
    val options=listOf("Наследовать рецептуру")+approved.map{"${it.name} · v${it.version}"}
    PickOne("Версия рецептуры",options,
        approved.firstOrNull{it.id==value}?.let{"${it.name} · v${it.version}"}?:"Наследовать рецептуру",
        {label->set(approved.firstOrNull{"${it.name} · v${it.version}"==label}?.id)},
        "Выбор рецептуры не требует партии приготовления. Если партия указана, она должна соответствовать выбранной версии.")
}

@Composable
private fun BatchChoice(available: List<BatchRecord>, value: Long?, set: (Long?)->Unit) {
    PickOne("Партия питательной среды",listOf("Не указана")+available.map{it.code+" · "+it.recipeName},
        available.firstOrNull{it.id==value}?.let{it.code+" · "+it.recipeName}?:"Не указана",
        {s->set(available.firstOrNull{s==it.code+" · "+it.recipeName}?.id)})
}

@Composable
fun IncidentReviewDialog(incident:IncidentRecord, affected:List<String>, history:List<String>, onClose:()->Unit, onSave:(String,String)->Unit) {
    var status by remember(incident.id){ mutableStateOf(incident.status) }
    var note by remember(incident.id){ mutableStateOf("") }
    DialogForm("Происшествие: ${incident.title}",onClose,saveText="Сохранить решение",onSave={onSave(status,note)}) {
        Text("Зарегистрировано: ${formatDate(incident.time)}")
        Text(incident.note)
        Text("Затронутые контейнеры",fontWeight=FontWeight.SemiBold)
        if(affected.isEmpty())Text("Контейнеры не указаны") else affected.forEach{Text(it,style=MaterialTheme.typography.bodySmall)}
        Text("История решений",fontWeight=FontWeight.SemiBold)
        if(history.isEmpty())Text("Решений ещё нет") else history.forEach{Text(it,style=MaterialTheme.typography.bodySmall)}
        PickOne("Состояние",listOf("Открыт","На контроле","Завершён","Аннулирован"),status,{status=it},"Закрытие или аннулирование происшествия не меняет состояние растений автоматически. Снятие с карантина оформляется отдельно.")
        Field("Что сделано / результат проверки",note,{note=it},single=false)
    }
}
