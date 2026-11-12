package ru.dikoveriya.lab

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class DraftPart(
    val name: String, val unit: String, val amount: String = "",
    val sugar: Boolean = false, val sugarAmount: String = "",
    val agar: Boolean = false, val agarAmount: String = "", val stock: String = ""
)
private fun String.asLabNumber(): Double? = trim().replace(',','.').toDoubleOrNull()
private fun String.asCount(): Int? = if(isBlank())null else trim().toIntOrNull()
private val partUnits=listOf("г/л","мг/л","мл/л","мг/л из раствора")

@Composable
fun MediumEditor(
    db: LabDatabase, defaultPlantId: Long?, initialTemplateId: Long? = null,
    onComplete: (Long)->Unit, onUseExisting: (Long)->Unit, onCancel: ()->Unit
) {
    var revision by remember { mutableIntStateOf(0) }
    val plants=remember(revision) { db.plants() }
    val templates=remember(revision) { db.recipes().filter { it.status=="Утверждена" } }
    val catalog=remember(revision) { db.ingredients() }
    var title by remember { mutableStateOf("") }
    var plantId by remember { mutableStateOf(defaultPlantId) }
    var purpose by remember { mutableStateOf("Размножение") }
    var volume by remember { mutableStateOf("500") }
    var notes by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(if(initialTemplateId!=null) "По рецептуре" else "Свой состав") }
    var templateId by remember { mutableStateOf(initialTemplateId) }
    var componentName by remember { mutableStateOf("") }
    val rows=remember { mutableStateListOf<DraftPart>() }
    var addCatalogItem by remember { mutableStateOf(false) }
    var newItemName by remember { mutableStateOf("") }
    var newItemCategory by remember { mutableStateOf("Добавка") }
    var newItemUnit by remember { mutableStateOf("мг/л") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val selectedTemplate=templates.firstOrNull { it.id==templateId }
    val vol=volume.toIntOrNull()
    val currentParts=selectedTemplate?.let { db.recipeParts(it.id) }.orEmpty()
    val templateCalculation=if(mode=="По рецептуре" && vol!=null && currentParts.isNotEmpty())
        runCatching { RecipeCalculator.calculate(currentParts,vol) }.getOrNull() else null

    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("Название, объём и состав — в одной форме",style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
        Field("Название среды",title,{title=it})
        PickOne("Для какого этапа",LabTerms.purposes,purpose,{purpose=it})
        PickOne("Растение",listOf("Для любых растений")+plants.map{it.name},
            plants.firstOrNull{it.id==plantId}?.name?:"Для любых растений",
            {x->plantId=plants.firstOrNull{it.name==x}?.id})
        Field("Объём для расчёта, мл",volume,{volume=it.filter(Char::isDigit)},numeric=true)
        Text("Рецептура хранится в пересчёте на 1 литр; объём нужен только для расчёта текущей порции.",
            style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        PickOne("Способ создания",listOf("Свой состав","По рецептуре"),mode,{mode=it})
        if(mode=="По рецептуре") {
            PickOne("Рецептура-основа",listOf("Выбери")+templates.map{"${it.name} · v${it.version}"},
                selectedTemplate?.let{"${it.name} · v${it.version}"}?:"Выбери",
                {choice->templateId=templates.firstOrNull{"${it.name} · v${it.version}"==choice}?.id})
            if(selectedTemplate!=null) {
                Text("${selectedTemplate.purpose} · ${plants.firstOrNull{it.id==selectedTemplate.plantId}?.name?:"Для любых растений"}",
                    style=MaterialTheme.typography.bodySmall)
                templateCalculation?.forEach { Text("${it.title}: ${RecipeCalculator.formatAmount(it.amount)} ${it.unit}",style=MaterialTheme.typography.bodyMedium) }
                OutlinedButton(onClick={onUseExisting(selectedTemplate.id)},modifier=Modifier.fillMaxWidth()) { Text("Выбрать существующую без копии") }
            } else if(templates.isEmpty()) Text("Рецептур пока нет. Выбери «Свой состав»." ,style=MaterialTheme.typography.bodySmall)
        } else {
            Text("Компоненты",fontWeight=FontWeight.SemiBold)
            if(rows.isEmpty()) Text("Выбери компоненты и укажи количество именно на выбранный объём.",style=MaterialTheme.typography.bodySmall)
            PickOne("Добавить из каталога",listOf("Выбери компонент")+catalog.map{it.name},
                componentName.ifBlank { "Выбери компонент" },{componentName=if(it=="Выбери компонент")"" else it})
            OutlinedButton(enabled=componentName.isNotBlank() && rows.none{it.name==componentName},onClick={
                catalog.firstOrNull { it.name==componentName }?.let{rows.add(DraftPart(it.name,it.unit))};componentName=""
            }) { Text("+ Добавить компонент") }
            TextButton(onClick={addCatalogItem=!addCatalogItem}){Text("Нет компонента? Создать новый здесь")}
            if(addCatalogItem) {
                Field("Название нового компонента",newItemName,{newItemName=it})
                PickOne("Категория",listOf("Базовая среда","Углеводы","Загуститель","Регулятор роста","Минералы","Витамины","Добавка","Другое"),newItemCategory,{newItemCategory=it})
                PickOne("Единица",partUnits,newItemUnit,{newItemUnit=it})
                Button(onClick={
                    try { db.addIngredient(newItemName,newItemCategory,newItemUnit,""); revision++;componentName=newItemName.trim();newItemName="";addCatalogItem=false;errorMessage=null }
                    catch(e:Exception){errorMessage=e.message}
                }){Text("Сохранить компонент")}
            }
            rows.forEachIndexed { index,item ->
                OutlinedCard(modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp)) {
                    Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Text(item.name,modifier=Modifier.weight(1f),fontWeight=FontWeight.SemiBold)
                            TextButton(onClick={rows.removeAt(index)}){Text("Убрать")}
                        }
                        Field("Количество на $volume мл (${if(item.unit=="мг/л из раствора") "мг вещества" else item.unit.removeSuffix("/л")})",item.amount,{rows[index]=item.copy(amount=it)},numeric=true)
                        PickOne("Единица расчёта",partUnits,item.unit,{rows[index]=item.copy(unit=it)})
                        if(item.unit=="мг/л из раствора") Field("Концентрация исходного раствора, мг/мл",item.stock,{rows[index]=item.copy(stock=it)},numeric=true)
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Checkbox(item.sugar,{rows[index]=item.copy(sugar=it,sugarAmount=if(it)item.sugarAmount else "")})
                            Text("Уже содержит сахар")
                        }
                        if(item.sugar) Field("Сколько сахара внутри, г на $volume мл",item.sugarAmount,{rows[index]=item.copy(sugarAmount=it)},numeric=true)
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Checkbox(item.agar,{rows[index]=item.copy(agar=it,agarAmount=if(it)item.agarAmount else "")})
                            Text("Уже содержит агар")
                        }
                        if(item.agar) Field("Сколько агара внутри, г на $volume мл",item.agarAmount,{rows[index]=item.copy(agarAmount=it)},numeric=true)
                        val typed=item.amount.asLabNumber()
                        val size=volume.toIntOrNull()
                        if(typed!=null && size!=null && size>0) Text("В рецептуре на 1 л: ${RecipeCalculator.formatAmount(typed*1000.0/size)} ${item.unit}",
                            style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Field("Источник, особенности (необязательно)",notes,{notes=it},single=false)
        if(errorMessage!=null) Text(errorMessage!!,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
        Button(onClick={
            try {
                val volumeMl=volume.toIntOrNull()?:0
                val ingredients=if(mode=="По рецептуре") {
                    val r=selectedTemplate?:error("Выбери рецептуру")
                    val details=db.mediumSugarAgar(r.id)
                    val sourceParts=db.recipeParts(r.id)
                    require(sourceParts.none { (it.includesSugar && details[it.name]?.first==null) || (it.includesAgar && details[it.name]?.second==null) }) {
                        "В исходной среде отмечены сахар или агар без их количества. Используй исходную среду без копии или создай свой состав с указанными граммами."
                    }
                    sourceParts.map { p ->
                        val sugarAgar=details[p.name]
                        MediumPartInput(p.name,p.perLiter*volumeMl/1000.0,p.unit,
                            sugarAgar?.first?.times(volumeMl/1000.0),sugarAgar?.second?.times(volumeMl/1000.0),p.stockMgPerMl)
                    }
                } else rows.map { item ->
                    MediumPartInput(item.name,item.amount.asLabNumber()?:0.0,item.unit,
                        if(item.sugar) item.sugarAmount.asLabNumber()?:0.0 else null,
                        if(item.agar) item.agarAmount.asLabNumber()?:0.0 else null,
                        item.stock.asLabNumber()?:0.0)
                }
                val id=db.saveReadyMedium(title,purpose,plantId,notes,volumeMl,ingredients)
                errorMessage=null; onComplete(id)
            } catch(e:Exception){errorMessage=e.message?:"Проверь состав и дозировки"}
        },modifier=Modifier.fillMaxWidth()) { Text("Сохранить готовую среду") }
        Text("Проверка формулы и концентраций обязательна. Приложение не назначает гормоны автоматически.",
            style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick=onCancel){Text("Отмена")}
    }
}

@Composable
fun QuickMediumDialog(db:LabDatabase,onClose:()->Unit,onSaved:(Long)->Unit,onUseExisting:(Long)->Unit) {
    AlertDialog(onDismissRequest=onClose,title={Text("Питательная среда")},text={
        Column(Modifier.fillMaxWidth().heightIn(max=570.dp).verticalScroll(rememberScrollState())) {
            MediumEditor(db,null,onComplete=onSaved,onUseExisting=onUseExisting,onCancel=onClose)
        }
    },confirmButton={},dismissButton={})
}

@Composable
fun QuickCultureDialog(db:LabDatabase,initialRecipeId:Long?,onClose:()->Unit,onSaved:(Long)->Unit,onChanged:()->Unit) {
    var revision by remember { mutableIntStateOf(0) }
    val plants=remember(revision){db.plants()}
    val mothers=remember(revision){db.mothers()}
    val recipes=remember(revision){db.recipes().filter{it.status=="Утверждена"}}
    var mode by remember { mutableStateOf("container") }
    var plantId by remember { mutableStateOf(plants.firstOrNull()?.id) }
    var motherId by remember { mutableStateOf<Long?>(null) }
    var recipeId by remember { mutableStateOf(initialRecipeId) }
    var explants by remember { mutableStateOf("") }
    var shoots by remember { mutableStateOf("") }
    var plantsCount by remember { mutableStateOf("") }
    var place by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var newPlantName by remember { mutableStateOf("") }
    var newMotherOrigin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val availableMothers=mothers.filter{it.plantId==plantId}
    val selectedRecipe=recipes.firstOrNull{it.id==recipeId}
    AlertDialog(onDismissRequest=onClose,title={Text(when(mode){"plant"->"Новое растение";"mother"->"Новый маточник";"medium"->"Новая среда";else->"Новый контейнер"})},text={
        Column(Modifier.fillMaxWidth().heightIn(max=580.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(9.dp)) {
            when(mode) {
                "plant" -> {
                    Field("Вид и сорт",newPlantName,{newPlantName=it})
                    Button(onClick={try {
                        db.addPlant(newPlantName)
                        revision++;plantId=db.plants().first{it.name==newPlantName.trim()}.id;motherId=null;newPlantName="";mode="container";onChanged()
                    }catch(e:Exception){errorMessage=e.message}}){Text("Сохранить и выбрать")}
                    TextButton(onClick={mode="container"}){Text("Назад")}
                }
                "mother" -> {
                    Text("Растение: ${plants.firstOrNull{it.id==plantId}?.name?:"не выбрано"}")
                    Field("Происхождение маточника",newMotherOrigin,{newMotherOrigin=it})
                    Button(onClick={try {
                        db.addMother(plantId?:error("Сначала выбери растение"),newMotherOrigin,"")
                        revision++;motherId=db.mothers().first{it.plantId==plantId && it.origin==newMotherOrigin.trim()}.id
                        newMotherOrigin="";mode="container";onChanged()
                    }catch(e:Exception){errorMessage=e.message}}){Text("Сохранить и выбрать")}
                    TextButton(onClick={mode="container"}){Text("Назад")}
                }
                "medium" -> MediumEditor(db,plantId,onComplete={id->revision++;recipeId=id;mode="container";onChanged()},
                    onUseExisting={id->recipeId=id;mode="container"},onCancel={mode="container"})
                else -> {
                    Text("Создай контейнер за один проход. Отсутствующие элементы добавляются здесь же.",style=MaterialTheme.typography.bodySmall)
                    PickOne("Растение",plants.map{it.name},plants.firstOrNull{it.id==plantId}?.name?:"Выбери",{x->plantId=plants.firstOrNull{it.name==x}?.id;motherId=null})
                    TextButton(onClick={mode="plant"}){Text("+ Новое растение")}
                    PickOne("Маточник",listOf("Без маточника")+availableMothers.map{"${it.code} · ${it.origin}"},
                        availableMothers.firstOrNull{it.id==motherId}?.let{"${it.code} · ${it.origin}"}?:"Без маточника",
                        {x->motherId=availableMothers.firstOrNull{"${it.code} · ${it.origin}"==x}?.id})
                    TextButton(onClick={mode="mother"},enabled=plantId!=null){Text("+ Новый маточник")}
                    PickOne("Питательная среда",listOf("Не указана")+recipes.map{"${it.name} · v${it.version}"},
                        selectedRecipe?.let{"${it.name} · v${it.version}"}?:"Не указана",
                        {x->recipeId=recipes.firstOrNull{"${it.name} · v${it.version}"==x}?.id})
                    TextButton(onClick={mode="medium"}){Text("+ Создать новую среду здесь")}
                    Text("Партия приготовления не обязательна. Сохраняется конкретная версия рецептуры.",
                        style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider()
                    Text("Показатели на момент создания",fontWeight=FontWeight.SemiBold)
                    Text("Можно указать один, два или все три; они не складываются друг с другом.",style=MaterialTheme.typography.bodySmall)
                    Field("Эксплантов, шт.",explants,{explants=it.filter(Char::isDigit)},numeric=true)
                    Field("Побегов, шт.",shoots,{shoots=it.filter(Char::isDigit)},numeric=true)
                    Field("Растений, шт.",plantsCount,{plantsCount=it.filter(Char::isDigit)},numeric=true)
                    Field("Стеллаж / полка",place,{place=it})
                    Field("Примечание",note,{note=it},single=false)
                }
            }
            if(errorMessage!=null)Text(errorMessage!!,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
        }
    },confirmButton={
        if(mode=="container") Button(onClick={try {
            val id=db.addCultureQuick(plantId?:error("Выбери растение"),motherId,recipeId,
                explants.asCount(),shoots.asCount(),plantsCount.asCount(),place,note)
            onSaved(id)
        }catch(e:Exception){errorMessage=e.message?:"Проверь данные"}}){Text("Создать контейнер")}
    },dismissButton={TextButton(onClick=onClose){Text("Закрыть")}})
}

@Composable
fun EasyRecipeDialog(db:LabDatabase,r:RecipeRecord,plantName:String?,onClose:()->Unit,onUse:(Long)->Unit,onAdvanced:()->Unit) {
    var volume by remember(r.id){mutableStateOf("500")}
    val parts=db.recipeParts(r.id)
    val details=db.mediumSugarAgar(r.id)
    val calculated=runCatching{RecipeCalculator.calculate(parts,volume.toIntOrNull()?:0)}
    AlertDialog(onDismissRequest=onClose,title={Text(r.name)},text={
        Column(Modifier.fillMaxWidth().heightIn(max=530.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("${plantName?:"Для любых растений"} · ${r.purpose} · версия ${r.version}",style=MaterialTheme.typography.bodySmall)
            Text("Состав на литр",fontWeight=FontWeight.SemiBold)
            parts.forEach { p ->
                val inc=details[p.name]
                Text("${p.name}: ${RecipeCalculator.formatAmount(p.perLiter)} ${p.unit}")
                if(p.includesSugar) Text("Сахар уже внутри: ${inc?.first?.let{RecipeCalculator.formatAmount(it)+" г/л"}?:"количество не указано"}",style=MaterialTheme.typography.bodySmall)
                if(p.includesAgar) Text("Агар уже внутри: ${inc?.second?.let{RecipeCalculator.formatAmount(it)+" г/л"}?:"количество не указано"}",style=MaterialTheme.typography.bodySmall)
                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
            }
            Field("Рассчитать на объём, мл",volume,{volume=it.filter(Char::isDigit)},numeric=true)
            if(calculated.isSuccess) calculated.getOrThrow().forEach { calc ->
                Text("${calc.title}: ${RecipeCalculator.formatAmount(calc.amount)} ${calc.unit}")
                if(calc.warning.isNotBlank())Text(calc.warning,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.tertiary)
            } else Text(calculated.exceptionOrNull()?.message?:"Проверь объём",color=MaterialTheme.colorScheme.error)
            Text("Это расчёт компонентов, а не запись о приготовлении партии.",style=MaterialTheme.typography.bodySmall)
            Button(onClick={onUse(r.id)},modifier=Modifier.fillMaxWidth()){Text("Использовать в новом контейнере")}
            OutlinedButton(onClick=onAdvanced,modifier=Modifier.fillMaxWidth()){Text("Дополнительные операции")}
        }
    },confirmButton={TextButton(onClick=onClose){Text("Готово")}})
}

@Composable
fun GrowthDialog(db:LabDatabase,c:CultureRecord,onClose:()->Unit,onChanged:()->Unit) {
    var revision by remember { mutableIntStateOf(0) }
    val journal=remember(revision){db.unifiedJournal(c.id)}
    val medium=remember(revision){db.currentMedium(c.id)}
    val recipes=remember(revision){db.recipes().filter{it.status=="Утверждена"}}
    var explants by remember{mutableStateOf("")};var shoots by remember{mutableStateOf("")}
    var plants by remember{mutableStateOf("")};var contaminated by remember{mutableStateOf("")}
    var note by remember{mutableStateOf("")}
    var changeRecipe by remember{mutableStateOf(false)}
    var newRecipe by remember{mutableStateOf<Long?>(null)}
    var mediumReason by remember{mutableStateOf("")}
    var errorMessage by remember{mutableStateOf<String?>(null)}
    var savedMessage by remember{mutableStateOf<String?>(null)}
    AlertDialog(onDismissRequest=onClose,title={Text("Рост и результаты · ${c.code}")},text={
        Column(Modifier.fillMaxWidth().heightIn(max=560.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(9.dp)){
            Text(c.plantName,fontWeight=FontWeight.SemiBold)
            Text("Текущая среда: ${medium?.let{"${it.name} · v${it.version}"}?:"не указана"}",style=MaterialTheme.typography.bodySmall)
            TextButton(onClick={changeRecipe=!changeRecipe}){Text("Сменить среду без создания партии")}
            if(changeRecipe){
                PickOne("Новая среда",listOf("Выбери")+recipes.map{"${it.name} · v${it.version}"},
                    recipes.firstOrNull{it.id==newRecipe}?.let{"${it.name} · v${it.version}"}?:"Выбери",
                    {x->newRecipe=recipes.firstOrNull{"${it.name} · v${it.version}"==x}?.id})
                Field("Причина смены",mediumReason,{mediumReason=it})
                OutlinedButton(onClick={try {
                    db.assignMedium(c.id,newRecipe?:error("Выбери среду"),mediumReason)
                    revision++;onChanged();changeRecipe=false;errorMessage=null
                }catch(e:Exception){errorMessage=e.message}}){Text("Записать смену среды")}
            }
            HorizontalDivider()
            if(savedMessage!=null) Text(savedMessage!!,color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.SemiBold)
            Text("Новый осмотр",fontWeight=FontWeight.SemiBold)
            Text("Заполняй только измеренные показатели. Пустое поле означает отсутствие измерения, а не ноль. Осмотр не меняет складской остаток: для пересадок и списаний используй «Что произошло?»." ,style=MaterialTheme.typography.bodySmall)
            Field("Эксплантов, шт.",explants,{explants=it.filter(Char::isDigit)},numeric=true)
            Field("Побегов, шт.",shoots,{shoots=it.filter(Char::isDigit)},numeric=true)
            Field("Растений, шт.",plants,{plants=it.filter(Char::isDigit)},numeric=true)
            Field("Заражено, шт.",contaminated,{contaminated=it.filter(Char::isDigit)},numeric=true)
            Field("Примечание, условия",note,{note=it},single=false)
            Button(onClick={try {
                db.addGrowthSample(c.id,explants.asCount(),shoots.asCount(),plants.asCount(),contaminated.asCount(),note)
                explants="";shoots="";plants="";contaminated="";note="";revision++;onChanged();errorMessage=null
                savedMessage="Сохранено. Запись добавлена в историю наблюдений."
            }catch(e:Exception){savedMessage=null;errorMessage=e.message}}){Text("Сохранить наблюдение")}
            if(errorMessage!=null)Text(errorMessage!!,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            Text("Единый журнал: наблюдения, осмотры, исходные значения и действия",
                fontWeight=FontWeight.SemiBold)
            if(journal.isEmpty()) Text("Пока нет записей")
            journal.take(50).forEach{entry->
                SmallCard(formatDate(entry.occurredAt)+" · "+entry.kind,entry.summary()){}
            }
        }
    },confirmButton={TextButton(onClick=onClose){Text("Закрыть")}})
}
