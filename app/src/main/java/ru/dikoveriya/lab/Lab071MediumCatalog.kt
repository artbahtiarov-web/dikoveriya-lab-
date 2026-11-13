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
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp

/** A single discoverable media centre. Recipe editing, optional preparations and
 * ingredient catalogue are still distinct, but reached from ONE screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Lab071MediumCatalog(
    plants:List<PlantRecord>,recipes:List<RecipeRecord>,batches:List<BatchRecord>,
    ingredients:List<IngredientEntry>,revision:Int,
    onCreate:()->Unit,onRecipe:(Long)->Unit,onNewBatch:()->Unit,
    onBatch:(Long)->Unit,onAddIngredient:()->Unit,onIngredient:(Long)->Unit,
    onImport:()->Unit,onExport:()->Unit,
    modifier:Modifier=Modifier
) {
    var page by rememberSaveable{mutableStateOf("Рецептуры")}
    var query by rememberSaveable{mutableStateOf("")}
    var purpose by rememberSaveable{mutableStateOf("Любое назначение")}
    var plantFilter by rememberSaveable{mutableStateOf("Все")}
    var showExtras by rememberSaveable{mutableStateOf(false)}
    var filtersOpen by rememberSaveable{mutableStateOf(false)}
    val shown=recipes.filter{recipe->
        (query.isBlank() || recipe.name.contains(query,true)) &&
        (purpose=="Любое назначение" || recipe.purpose==purpose) &&
        (plantFilter=="Все" || recipe.plantId==null ||
            plants.firstOrNull{it.id==recipe.plantId}?.name==plantFilter)
    }
    LazyColumn(modifier=modifier,state=rememberLazyListState(),
        contentPadding=PaddingValues(16.dp),
        verticalArrangement=Arrangement.spacedBy(11.dp)){
        item(key="header"){
            SectionTitle("Питательные среды")
            Text("Выбери: состав среды или запись того, что действительно приготовлено.",
                style=MaterialTheme.typography.bodySmall)
            Lab072Chips(listOf("Рецептуры","Приготовления"),page,{page=it})
        }
        if(page=="Рецептуры"){
            item(key="create"){
                Button(onClick=onCreate,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){
                    Icon(Icons.Outlined.Add,null)
                    Spacer(Modifier.width(8.dp))
                    Text("Создать рецептуру")
                }
            }
            item(key="search"){Field("Найти рецепт или версию",query,{query=it})}
            item(key="filter-purpose"){
                OutlinedButton(onClick={filtersOpen=true},
                    modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){
                    Icon(Icons.Outlined.Tune,null)
                    Spacer(Modifier.width(7.dp))
                    Text("Фильтры: назначение и растение")
                }
            }
            item(key="count"){SectionTitle("Рецептуры · ${shown.size}")}
            if(shown.isEmpty())item(key="empty"){
                Text("Рецептуры не найдены. Измени поиск или добавь новый рецепт.")
            }
            items(shown,key={"recipe-"+it.id}){recipe->
                OutlinedCard(onClick={onRecipe(recipe.id)},
                    modifier=Modifier.fillMaxWidth().heightIn(min=91.dp),
                    shape=RoundedCornerShape(17.dp),
                    border=BorderStroke(1.dp,Lab07Palette.Brass.copy(alpha=.3f)),
                    colors=CardDefaults.outlinedCardColors(
                        containerColor=MaterialTheme.colorScheme.surface)){
                    Row(Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment=Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.spacedBy(12.dp)){
                        Surface(shape=RoundedCornerShape(14.dp),
                            color=Lab07Palette.Champagne){
                            Icon(Icons.Outlined.Science,null,tint=Lab07Palette.Forest,
                                modifier=Modifier.padding(12.dp).size(29.dp))
                        }
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)){
                            Text(recipe.name+" · v"+recipe.version,
                                style=MaterialTheme.typography.titleMedium)
                            Text(recipe.purpose+" · "+recipe.status,
                                style=MaterialTheme.typography.bodySmall,
                                color=MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(plants.firstOrNull{it.id==recipe.plantId}?.name?:"Для всех растений",
                                style=MaterialTheme.typography.bodySmall)
                        }
                        Icon(Icons.Outlined.ChevronRight,contentDescription=null,
                            tint=Lab07Palette.InkBrass)
                    }
                }
            }
            item(key="extras"){
                OutlinedButton(onClick={showExtras=!showExtras},
                    modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){
                    Icon(Icons.Outlined.Settings,null)
                    Spacer(Modifier.width(8.dp))
                    Text(if(showExtras)"Скрыть справочник" else "Компоненты рецептур и CSV")
                    Spacer(Modifier.weight(1f))
                    Icon(if(showExtras)Icons.Outlined.ExpandLess
                        else Icons.Outlined.ExpandMore,null)
                }
            }
            if(showExtras){
                item(key="csv"){
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        OutlinedButton(onClick=onImport,modifier=Modifier.weight(1f)
                            .heightIn(min=48.dp)){Text("Импорт CSV")}
                        OutlinedButton(onClick=onExport,modifier=Modifier.weight(1f)
                            .heightIn(min=48.dp)){Text("Экспорт CSV")}
                    }
                }
                item(key="components"){
                    SectionTitle("Компоненты · ${ingredients.size}")
                    OutlinedButton(onClick=onAddIngredient,
                        modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){
                        Text("Добавить компонент")
                    }
                }
                items(ingredients,key={"ingredient-${it.id}"}){ingredient->
                    Lab07QuickAction(ingredient.name,
                        {Icon(Icons.Outlined.Biotech,null)},
                        onClick={onIngredient(ingredient.id)})
                    Text("${ingredient.category} · ${ingredient.unit} · нажми, чтобы изменить или архивировать",
                        style=MaterialTheme.typography.bodySmall)
                }
            }
        }else{
            item(key="new-batch"){
                Button(onClick=onNewBatch,
                    modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){
                    Icon(Icons.Outlined.Science,null)
                    Spacer(Modifier.width(8.dp))
                    Text("Записать приготовление")
                }
            }
            item(key="help"){
                Text("В этом разделе только реальные приготовления. Расчётные массы не считаются фактически отмеренными. Отдельная партия для назначения рецептуры контейнеру не обязательна.",
                    style=MaterialTheme.typography.bodySmall)
            }
            item(key="batch-title"){
                SectionTitle("Приготовления · ${batches.size}")
            }
            if(batches.isEmpty())item(key="batch-empty"){
                Text("Пока ничего не зарегистрировано.")
            }
            items(batches,key={"batch-${it.id}"}){batch->
                Lab07QuickAction(batch.code,
                    {Icon(Icons.Outlined.Inventory2,null)},
                    onClick={onBatch(batch.id)})
                Text("${batch.recipeName} · v${batch.version} · ${batch.volumeMl} мл · ${batch.status}",
                    style=MaterialTheme.typography.bodySmall)
            }
        }
    }
    if(filtersOpen){
        var draftPurpose by remember(filtersOpen){mutableStateOf(purpose)}
        var draftPlant by remember(filtersOpen){mutableStateOf(plantFilter)}
        ModalBottomSheet(onDismissRequest={filtersOpen=false},
            containerColor=MaterialTheme.colorScheme.surface){
            Column(Modifier.padding(horizontal=20.dp,vertical=12.dp),
                verticalArrangement=Arrangement.spacedBy(14.dp)){
                Text("Фильтры рецептур",style=MaterialTheme.typography.headlineSmall)
                PickOne("Назначение",listOf("Любое назначение")+LabTerms.purposes,
                    draftPurpose,{draftPurpose=it})
                PickOne("Растение",listOf("Все")+plants.map{it.name},
                    draftPlant,{draftPlant=it})
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    OutlinedButton(onClick={draftPurpose="Любое назначение";draftPlant="Все"},
                        modifier=Modifier.weight(1f)){Text("Сбросить")}
                    Button(onClick={purpose=draftPurpose;plantFilter=draftPlant;filtersOpen=false},
                        modifier=Modifier.weight(1f)){Text("Применить")}
                }
                Spacer(Modifier.height(14.dp))
            }
        }
    }

}

/** Replaces the old three-dialog chain "choose recipe -> details -> advanced -> batch".
 * Values are NEVER invented or silently assumed equal to the calculated amount.
 */
@Composable
fun Lab071PrepareBatchDialog(db:LabDatabase,onClose:()->Unit,onSaved:()->Unit) {
    val recipes=remember{db.recipes().filter{it.status=="Утверждена"}}
    var selected by rememberSaveable{mutableStateOf<Long?>(null)}
    var volume by rememberSaveable{mutableStateOf("500")}
    var ph by rememberSaveable{mutableStateOf("")}
    var sterilization by rememberSaveable{mutableStateOf("")}
    var note by rememberSaveable{mutableStateOf("")}
    val values=remember{mutableStateMapOf<String,String>()}
    var errorMessage by remember{mutableStateOf<String?>(null)}
    val recipe=recipes.firstOrNull{it.id==selected}
    val calc=remember(selected,volume){
        if(selected==null || volume.toIntOrNull()==null)null
        else runCatching{
            RecipeCalculator.calculate(db.recipeParts(requireNotNull(selected)),
                requireNotNull(volume.toIntOrNull()))
        }.getOrNull()
    }
    AlertDialog(onDismissRequest=onClose,
        title={Text("Приготовление среды")},
        text={
            Column(Modifier.fillMaxWidth().heightIn(max=570.dp)
                .verticalScroll(rememberScrollState()),
                verticalArrangement=Arrangement.spacedBy(10.dp)) {
                PickOne("Утверждённая рецептура",
                    listOf("Выбери")+recipes.map{"${it.name} · v${it.version}"},
                    recipe?.let{"${it.name} · v${it.version}"}?:"Выбери",
                    {label->
                        selected=recipes.firstOrNull{"${it.name} · v${it.version}"==label}?.id
                        values.clear()
                    })
                Field("Объём, мл",volume,{volume=it.filter(Char::isDigit)})
                Text("Сначала расчёт, затем реальные измеренные количества. Пустые значения не заполняются автоматически.",
                    style=MaterialTheme.typography.bodySmall)
                if(calc!=null)calc.forEach{result->
                    Text("${result.title}: расчёт ${RecipeCalculator.formatAmount(result.amount)} ${result.unit}",
                        style=MaterialTheme.typography.bodySmall)
                    Field("Фактически: ${result.title} (${result.unit})",
                        values[result.title].orEmpty(),{values[result.title]=it},numeric=true)
                }
                Field("Измеренный pH (необязательно)",ph,{ph=it})
                Field("Режим стерилизации",sterilization,{sterilization=it})
                Field("Заметки",note,{note=it},single=false)
                if(errorMessage!=null)Text(requireNotNull(errorMessage),
                    color=MaterialTheme.colorScheme.error,
                    style=MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton={
            Button(enabled=selected!=null && !calc.isNullOrEmpty(),
                onClick={
                    try{
                        val actual=requireNotNull(calc).associate { part ->
                            val input=values[part.title]?.replace(',','.')?.toDoubleOrNull()
                                ?: error("Укажи фактическое количество: ${part.title}")
                            require(input>=0 && input.isFinite()){"Некорректное количество: ${part.title}"}
                            part.title to input
                        }
                        db.prepareBatch(requireNotNull(selected),volume.toInt(),ph,
                            sterilization,note,actual)
                        onSaved()
                    }catch(e:Exception){errorMessage=e.message?:"Не удалось записать партию"}
                }){Text("Сохранить партию")}
        },
        dismissButton={TextButton(onClick=onClose){Text("Отмена")}}
    )
}
