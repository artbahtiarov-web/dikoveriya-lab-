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
import androidx.compose.ui.unit.dp

/** A single discoverable media centre. Recipe editing, optional preparations and
 * ingredient catalogue are still distinct, but reached from ONE screen.
 */
@Composable
fun Lab071MediumCatalog(
    plants:List<PlantRecord>,recipes:List<RecipeRecord>,batches:List<BatchRecord>,
    ingredients:List<IngredientEntry>,revision:Int,
    onCreate:()->Unit,onRecipe:(Long)->Unit,onNewBatch:()->Unit,
    onBatch:(Long)->Unit,onAddIngredient:()->Unit,onIngredient:(Long)->Unit,
    onImport:()->Unit,onExport:()->Unit,
    modifier:Modifier=Modifier
) {
    var query by rememberSaveable{mutableStateOf("")}
    var purpose by rememberSaveable{mutableStateOf("Любое назначение")}
    var plantFilter by rememberSaveable{mutableStateOf("Все")}
    var showExtras by rememberSaveable{mutableStateOf(false)}
    val shown=recipes.filter { recipe->
        (query.isBlank() || recipe.name.contains(query,true)) &&
        (purpose=="Любое назначение" || recipe.purpose==purpose) &&
        (plantFilter=="Все" || recipe.plantId==null ||
            plants.firstOrNull{it.id==recipe.plantId}?.name==plantFilter)
    }
    LazyColumn(modifier=modifier,state=rememberLazyListState(),
        contentPadding=PaddingValues(16.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item(key="header") {
            Lab07BrandHero("Питательные среды",
                "Рецептуры и версии · приготовления по желанию",
                "ЛАБОРАТОРИЯ / РЕЦЕПТУРЫ")
        }
        item(key="actions") {
            Button(onClick=onCreate,
                modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) {
                Icon(Icons.Outlined.Add,null)
                Spacer(Modifier.width(8.dp))
                Text("Создать рецептуру")
            }
        }
        item(key="quick-batch") {
            OutlinedButton(onClick=onNewBatch,
                modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                Icon(Icons.Outlined.Science,null)
                Spacer(Modifier.width(8.dp))
                Text("Записать приготовление партии")
            }
        }
        item(key="find") {Field("Поиск рецептуры",query,{query=it})}
        item(key="filter-purpose") {
            PickOne("Для какого этапа",
                listOf("Любое назначение")+LabTerms.purposes,purpose,{purpose=it})
        }
        item(key="filter-plant") {
            PickOne("Для какого растения",
                listOf("Все")+plants.map{it.name},plantFilter,{plantFilter=it})
        }
        item(key="recipe-count"){SectionTitle("Рецептуры · ${shown.size}")}
        if(shown.isEmpty())item(key="empty"){Text("Рецептуры не найдены. Измени фильтры или добавь новую.")}
        items(shown,key={"recipe-${it.id}"}){recipe->
            Lab07QuickAction("${recipe.name} · v${recipe.version}",
                {Icon(Icons.Outlined.MenuBook,null)},
                primary=false,onClick={onRecipe(recipe.id)})
            Text("${recipe.purpose} · ${recipe.status} · ${plants.firstOrNull{it.id==recipe.plantId}?.name ?: "Для всех растений"}",
                style=MaterialTheme.typography.bodySmall)
        }
        item(key="batch-title"){SectionTitle("Приготовленные партии · ${batches.size}")}
        if(batches.isEmpty())item(key="batch-empty"){Text("Пока нет зарегистрированных приготовлений. Для назначения среды в контейнер партия не обязательна.")}
        items(batches,key={"batch-${it.id}"}){batch->
            Lab07QuickAction(batch.code,
                {Icon(Icons.Outlined.Inventory2,null)},
                onClick={onBatch(batch.id)})
            Text("${batch.recipeName} v${batch.version} · ${batch.volumeMl} мл · ${batch.status}",
                style=MaterialTheme.typography.bodySmall)
        }
        item(key="extras") {
            OutlinedButton(onClick={showExtras=!showExtras},
                modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) {
                Icon(Icons.Outlined.Settings,null)
                Spacer(Modifier.width(8.dp))
                Text(if(showExtras)"Скрыть инструменты" else "Каталог компонентов, CSV и другие инструменты")
                Spacer(Modifier.weight(1f))
                Icon(if(showExtras)Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,null)
            }
        }
        if(showExtras) {
            item(key="csv") {
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick=onImport,modifier=Modifier.weight(1f).heightIn(min=48.dp)) {
                        Text("Импорт CSV")
                    }
                    OutlinedButton(onClick=onExport,modifier=Modifier.weight(1f).heightIn(min=48.dp)) {
                        Text("Экспорт CSV")
                    }
                }
            }
            item(key="components") {
                SectionTitle("Компоненты · ${ingredients.size}")
                OutlinedButton(onClick=onAddIngredient){Text("Добавить компонент")}
            }
            items(ingredients,key={"ingredient-${it.id}"}){ingredient->
                Lab07QuickAction(ingredient.name,{Icon(Icons.Outlined.Biotech,null)},
                    onClick={onIngredient(ingredient.id)})
                Text("${ingredient.category} · ${ingredient.unit}",
                    style=MaterialTheme.typography.bodySmall)
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
    var error by remember{mutableStateOf<String?>(null)}
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
                if(error!=null)Text(requireNotNull(error),
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
                    }catch(e:Exception){error=e.message?:"Не удалось записать партию"}
                }){Text("Сохранить партию")}
        },
        dismissButton={TextButton(onClick=onClose){Text("Отмена")}}
    )
}
