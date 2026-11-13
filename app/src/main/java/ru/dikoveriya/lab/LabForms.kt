package ru.dikoveriya.lab

import android.graphics.BitmapFactory
import java.io.File
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun AddPlantDialog(onClose:()->Unit,onSave:(String,String)->Unit){
    var name by remember{mutableStateOf("")};var notes by remember{mutableStateOf("")}
    DialogForm("Новое растение",onClose,onSave={onSave(name,notes)}){
        Field("Название вида / сорта",name,{name=it})
        Field("Примечание (необязательно)",notes,{notes=it},single=false)
        HelpIcon("Каталог","Здесь создаётся название растения, а не новый контейнер. Для разных независимых маточников используется отдельный учёт.")
    }
}
@Composable
fun AddMotherDialog(plants:List<PlantRecord>,onClose:()->Unit,onSave:(Long,String,String)->Unit){
    var selected by remember{mutableStateOf(plants.firstOrNull()?.id)}
    var origin by remember{mutableStateOf("")};var note by remember{mutableStateOf("")}
    DialogForm("Новый маточник",onClose,onSave={if(selected!=null)onSave(selected!!,origin,note)}){
        PickOne("Растение",plants.map{it.name},plants.firstOrNull{it.id==selected}?.name.orEmpty(),{s->selected=plants.firstOrNull{it.name==s}?.id})
        Field("Происхождение (поставщик, место)",origin,{origin=it})
        Field("Дополнительные сведения",note,{note=it},single=false)
        Text("Происхождение каждого маточника сохраняется отдельно.",style=MaterialTheme.typography.bodySmall)
    }
}
@Composable
fun AddCultureDialog(plants:List<PlantRecord>,mothers:List<MotherRecord>,batches:List<BatchRecord>,onClose:()->Unit,onSave:(Long,Int,Long?,Long?,String,String,String)->Unit){
    var plantId by remember{mutableStateOf(plants.firstOrNull()?.id)}
    var qty by remember{mutableStateOf("1")}
    var motherId by remember{mutableStateOf<Long?>(null)}
    var batchId by remember{mutableStateOf<Long?>(null)}
    var place by remember{mutableStateOf("")};var note by remember{mutableStateOf("")}
    var quantityKind by remember{mutableStateOf("Не уточнено")}
    val availableMothers=mothers.filter{it.plantId==plantId}
    val availableBatches=batches.filter{it.status=="Готова"}
    DialogForm("Новый контейнер",onClose,saveText="Создать",onSave={if(plantId!=null) onSave(plantId!!,qty.toIntOrNull()?:0,motherId,batchId,place,note,quantityKind)}){
        PickOne("Растение",plants.map{it.name},plants.firstOrNull{it.id==plantId}?.name.orEmpty(),{s->plantId=plants.firstOrNull{it.name==s}?.id;motherId=null})
        Field("Количество объектов",qty,{qty=it.filter{c->c.isDigit()}},numeric=true)
        PickOne("Что считаем",LabTerms.quantityKinds,quantityKind,{quantityKind=it},"Экспланты, побеги и растения — разные единицы наблюдения. Это поможет правильно оценивать результативность размножения.")
        PickOne("Маточник",listOf("Не указан")+availableMothers.map{"${it.code} · ${it.origin}"},availableMothers.firstOrNull{it.id==motherId}?.let{"${it.code} · ${it.origin}"}?:"Не указан",{s->motherId=availableMothers.firstOrNull{"${it.code} · ${it.origin}"==s}?.id},"Маточник — исходное растение. Если происхождение неизвестно, можно оставить поле пустым; система не будет его придумывать.")
        PickOne("Партия среды",listOf("Не указана")+availableBatches.map{"${it.code} · ${it.recipeName}"},availableBatches.firstOrNull{it.id==batchId}?.let{"${it.code} · ${it.recipeName}"}?:"Не указана",{s->batchId=availableBatches.firstOrNull{"${it.code} · ${it.recipeName}"==s}?.id})
        Field("Место (стеллаж, полка)",place,{place=it})
        Field("Примечание",note,{note=it},single=false)
        Text("Технический номер и этап ввода программа создаст сама.",style=MaterialTheme.typography.bodySmall)
    }
}
@Composable
fun AddRecipeDialog(plants:List<PlantRecord>,onClose:()->Unit,onSave:(String,String,Long?,String)->Unit){
    var name by remember{mutableStateOf("")};var purpose by remember{mutableStateOf("Размножение")}
    var plantId by remember{mutableStateOf<Long?>(null)};var notes by remember{mutableStateOf("")}
    DialogForm("Новая рецептура",onClose,onSave={onSave(name,purpose,plantId,notes)}){
        Field("Понятное название",name,{name=it})
        PickOne("Для чего",LabTerms.purposes,purpose,{purpose=it})
        PickOne("Растение",listOf("Любое")+plants.map{it.name},plants.firstOrNull{it.id==plantId}?.name?:"Любое",{s->plantId=plants.firstOrNull{it.name==s}?.id})
        Field("Примечание, источник протокола",notes,{notes=it},single=false)
        Text("Рецептура создаётся как черновик. Добавь компоненты, проверь состав и только потом утверди.",style=MaterialTheme.typography.bodySmall)
    }
}
@Composable
fun ChooseBatchRecipeDialog(recipes:List<RecipeRecord>,onClose:()->Unit,onPick:(Long)->Unit){
    DialogForm("Выбери рецептуру",onClose){
        val approved=recipes.filter{it.status=="Утверждена"}
        if(approved.isEmpty())Text("Сначала создай и утверди рецептуру")
        approved.forEach{r->SmallCard("${r.name} · версия ${r.version}",r.purpose){onPick(r.id)}}
    }
}
@Composable
fun RecipeDialog(
    r:RecipeRecord,parts:List<RecipePart>,catalog:List<IngredientEntry>,onClose:()->Unit,
    onAdd:(String,Double,String,Boolean,Boolean,Double)->Unit,onRemove:(Long)->Unit,
    onActivate:()->Unit,onRevise:()->Unit,
    onPrepare:(Int,String,String,String,Map<String,Double>)->Unit
){
    var mode by remember(r.id){mutableStateOf("Состав")}
    var partName by remember(r.id){mutableStateOf("")}
    var partAmount by remember(r.id){mutableStateOf("")}
    var partUnit by remember(r.id){mutableStateOf("г/л")}
    var stockStrength by remember(r.id){mutableStateOf("")}
    var sugar by remember(r.id){mutableStateOf(false)}
    var agar by remember(r.id){mutableStateOf(false)}
    var volume by remember(r.id){mutableStateOf("500")}
    var ph by remember(r.id){mutableStateOf("")}
    var sterilization by remember(r.id){mutableStateOf("")}
    var notes by remember(r.id){mutableStateOf("")}
    val actual=remember(r.id){mutableStateMapOf<String,String>()}
    val result=remember(parts,volume){runCatching{RecipeCalculator.calculate(parts,volume.toIntOrNull()?:0)}}
    AlertDialog(onDismissRequest=onClose,title={Column{
        Text(r.name)
        Text("${r.purpose} · версия ${r.version} · ${r.status}",style=MaterialTheme.typography.bodySmall)
    }},text={Column(Modifier.fillMaxWidth().heightIn(max=550.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){
        PickOne("Раздел",listOf("Состав","Расчёт"),mode,{mode=it})
        when(mode){
            "Состав" -> {
                Text("Компоненты",fontWeight=FontWeight.SemiBold)
                if(parts.isEmpty()) Text("Компоненты не добавлены")
                parts.forEach{p->
                    Row(verticalAlignment=Alignment.CenterVertically){
                        Column(Modifier.weight(1f)){
                            Text(p.name,fontWeight=FontWeight.Medium)
                            Text("${RecipeCalculator.formatAmount(p.perLiter)} ${p.unit}${if(p.stockMgPerMl>0)" · запас ${p.stockMgPerMl} мг/мл" else ""}",style=MaterialTheme.typography.bodySmall)
                            if(p.includesSugar || p.includesAgar) Text("Уже содержит: ${listOfNotNull(if(p.includesSugar)"сахар" else null,if(p.includesAgar)"агар" else null).joinToString()}",style=MaterialTheme.typography.bodySmall)
                        }
                        if(r.status=="Черновик") TextButton(onClick={onRemove(p.id)}){Text("Убрать")}
                    }
                    HorizontalDivider()
                }
                if(r.status=="Черновик"){
                    if(catalog.isNotEmpty()) PickOne("Из справочника",listOf("Ввести вручную")+catalog.map{it.name},
                        catalog.firstOrNull{it.name==partName}?.name?:"Ввести вручную",{ name ->
                            if(name!="Ввести вручную") {
                                val choice=catalog.first{it.name==name}
                                partName=choice.name
                                partUnit=choice.unit
                            } else partName=""
                        },"Справочник подставляет название и единицу. Дозировку для каждой рецептуры задаёшь отдельно по проверенному источнику.")
                    Field("Компонент",partName,{partName=it})
                    Field("Количество на 1 л",partAmount,{partAmount=it},numeric=true)
                    PickOne("Единица",listOf("г/л","мг/л","мл/л","мг/л из раствора"),partUnit,{partUnit=it},"Если выбрано «мг/л из раствора», укажи концентрацию запаса в мг/мл: приложение переведёт дозу в мл.")
                    if(partUnit=="мг/л из раствора")Field("Концентрация запаса, мг/мл",stockStrength,{stockStrength=it},numeric=true)
                    Row(verticalAlignment=Alignment.CenterVertically){Checkbox(checked=sugar,onCheckedChange={sugar=it});Text("Уже содержит сахар")}
                    Row(verticalAlignment=Alignment.CenterVertically){Checkbox(checked=agar,onCheckedChange={agar=it});Text("Уже содержит агар")}
                    Button(onClick={onAdd(partName,partAmount.replace(',','.').toDoubleOrNull()?:0.0,partUnit,sugar,agar,stockStrength.replace(',','.').toDoubleOrNull()?:0.0)}){Text("Добавить компонент")}
                    Text("Не вводи данные по памяти. Дозировки должны соответствовать проверенной этикетке или протоколу.",style=MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick=onActivate,enabled=parts.isNotEmpty()){Text("Утвердить рецептуру")}
                } else {
                    Text("Состав утверждён и защищён от изменений. Для корректировки создай новую версию.",style=MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick=onRevise){Text("Создать новую версию")}
                }
            }
            "Расчёт" -> {
                Field("Необходимый объём, мл",volume,{volume=it.filter(Char::isDigit)},numeric=true)
                when{
                    result.isSuccess -> result.getOrThrow().forEach{c->
                        Text("${c.title}: ${RecipeCalculator.formatAmount(c.amount)} ${c.unit}",fontWeight=FontWeight.Medium)
                        if(c.warning.isNotBlank())Text(c.warning,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
                    }
                    else -> Text(result.exceptionOrNull()?.message?:"Проверь рецепт и объём",color=MaterialTheme.colorScheme.error)
                }
                HelpIcon("Расчёт среды","Все ингредиенты рассчитываются из утверждённого количества на 1 л. Предупреждение о точности весов не заменяет проверку рецепта. Научный состав должен быть подтверждён до использования.")
                if(mode=="Приготовить"){
                    Text("Подтверди фактически отмеренные количества",fontWeight=FontWeight.Medium)
                    if(result.isSuccess) result.getOrThrow().forEach { item ->
                        Field("${item.title} (${item.unit})",actual[item.title].orEmpty(),{actual[item.title]=it},numeric=true)
                    }
                    Field("Фактический pH (необязательно)",ph,{ph=it})
                    Field("Стерилизация: температура, давление, время",sterilization,{sterilization=it})
                    Field("Фактические отклонения, партия компонентов",notes,{notes=it},single=false)
                    Text("Утверждённый расчёт будет сохранён снимком. Если фактический состав отличается, подробно укажи это в отклонениях.",style=MaterialTheme.typography.bodySmall)
                }
            }
        }
    }},confirmButton={
        if(mode=="Приготовить" && r.status=="Утверждена" && result.isSuccess)
            TextButton(onClick={
                val items=result.getOrThrow()
                val actualMap=items.associate{it.title to ((actual[it.title]?:error("Фактическая масса ${it.title} не введена")).replace(',','.').toDoubleOrNull() ?: Double.NaN)}
                onPrepare(volume.toIntOrNull()?:0,ph,sterilization,notes,actualMap)
            }){Text("Сохранить партию")}
    },dismissButton={TextButton(onClick=onClose){Text("Закрыть")}})
}
@Composable
fun BatchDialog(b:BatchRecord,snapshot:String,onClose:()->Unit,onReject:(String)->Unit){
    var reason by remember(b.id){mutableStateOf("")}
    DialogForm("Партия ${b.code}",onClose,saveText="Забраковать",onSave=if(b.status=="Готова"){ {onReject(reason)} } else null){
        Text("${b.recipeName} · версия ${b.version}\n${b.volumeMl} мл · ${formatDate(b.preparedAt)}\nСтатус: ${b.status}")
        Text("Примечание: ${b.note}")
        Text("Сохранённый состав",fontWeight=FontWeight.SemiBold)
        val obj=runCatching { org.json.JSONObject(snapshot).getJSONArray("components") }.getOrNull()
        if(obj!=null) for(i in 0 until obj.length()) {
            val part=obj.getJSONObject(i)
            Text("${part.getString("name")}: план ${RecipeCalculator.formatAmount(part.getDouble("calculated_amount"))}, факт ${RecipeCalculator.formatAmount(part.getDouble("actual_amount"))} ${part.getString("calculated_unit")}",style=MaterialTheme.typography.bodySmall)
        }
        if(b.status=="Готова"){
            Field("Причина брака",reason,{reason=it},single=false)
            Text("При списании партии связанные активные контейнеры автоматически попадут на контроль.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
        }
    }
}
@Composable
fun AddTaskDialog(cultures:List<CultureRecord>,onClose:()->Unit,onSave:(String,Long,Long?)->Unit){
    var title by remember{mutableStateOf("")};var after by remember{mutableStateOf("1")}
    var cultureId by remember{mutableStateOf<Long?>(null)}
    DialogForm("Новая задача",onClose,onSave={onSave(title,System.currentTimeMillis()+(after.toLongOrNull()?:0)*86_400_000L,cultureId)}){
        Field("Что нужно сделать?",title,{title=it})
        Field("Через сколько дней",after,{after=it.filter(Char::isDigit)},numeric=true)
        PickOne("Контейнер",listOf("Вся лаборатория")+cultures.map{it.code},cultures.firstOrNull{it.id==cultureId}?.code?:"Вся лаборатория",{s->cultureId=cultures.firstOrNull{it.code==s}?.id})
    }
}
@Composable
fun AddMaterialDialog(onClose:()->Unit,onSave:(String,String,Double,Double,Double,String)->Unit,
    customCategories:List<String> = emptyList()){
    var name by remember{mutableStateOf("")};var unit by remember{mutableStateOf("г")}
    var category by remember{mutableStateOf("Расходники")}
    var qty by remember{mutableStateOf("")};var min by remember{mutableStateOf("0")};var cost by remember{mutableStateOf("0")}
    DialogForm("Новый материал",onClose,onSave={onSave(name,unit,qty.replace(',','.').toDoubleOrNull()?:-1.0,min.replace(',','.').toDoubleOrNull()?:-1.0,cost.replace(',','.').toDoubleOrNull()?:-1.0,category)}){
        Field("Название",name,{name=it})
        PickOne("Категория",(listOf("Реактивы","Расходники","Инвентарь","Другое")+
            customCategories).distinct(),category,{category=it})
        PickOne("Единица",listOf("г","мл","шт.","л","кг"),unit,{unit=it})
        Field("Начальный остаток",qty,{qty=it},numeric=true)
        Field("Минимальный запас",min,{min=it},numeric=true)
        Field("Цена за единицу, ₽",cost,{cost=it},numeric=true)
    }
}
@Composable
fun MaterialDialog(m:MaterialRecord,onClose:()->Unit,onMove:(Double,String)->Unit){
    var qty by remember(m.id){mutableStateOf("")};var op by remember(m.id){mutableStateOf("Приход")};var note by remember(m.id){mutableStateOf("")}
    DialogForm(m.name,onClose,onSave={val delta=(qty.replace(',','.').toDoubleOrNull()?:0.0)*if(op=="Расход")-1 else 1;onMove(delta,note)}){
        Text("Текущий остаток: ${RecipeCalculator.formatAmount(m.quantity)} ${m.unit}")
        PickOne("Движение",listOf("Приход","Расход"),op,{op=it})
        Field("Количество, ${m.unit}",qty,{qty=it},numeric=true)
        Field("Причина",note,{note=it})
    }
}
@Composable
fun AddEquipmentDialog(onClose:()->Unit,onSave:(String,String)->Unit){
    var name by remember{mutableStateOf("")};var location by remember{mutableStateOf("")}
    DialogForm("Оборудование",onClose,onSave={onSave(name,location)}){
        Field("Название",name,{name=it})
        Field("Расположение",location,{location=it})
    }
}
@Composable
fun AddIncidentDialog(cultures:List<CultureRecord>,onClose:()->Unit,onSave:(String,String,Long?)->Unit){
    var title by remember{mutableStateOf("Отключение электричества")};var note by remember{mutableStateOf("")};var cultureId by remember{mutableStateOf<Long?>(null)}
    DialogForm("Новый инцидент",onClose,onSave={onSave(title,note,cultureId)}){
        PickOne("Что произошло?",listOf("Отключение электричества","Сбой температуры","Проблема стерилизации","Повреждение контейнера","Подозрение на заражение","Другое"),title,{title=it})
        PickOne("Затронутый контейнер",listOf("Не выбран")+cultures.map{it.code},cultures.firstOrNull{it.id==cultureId}?.code?:"Не выбран",{s->cultureId=cultures.firstOrNull{it.code==s}?.id})
        Field("Подробности и факты",note,{note=it},single=false)
        Text("Неизвестную причину можно указать как неизвестную. Не записывай предположения как установленный факт.",style=MaterialTheme.typography.bodySmall)
    }
}
@Composable
fun AddExperimentDialog(onClose:()->Unit,onSave:(String,String)->Unit){
    var title by remember{mutableStateOf("")};var hypothesis by remember{mutableStateOf("")}
    DialogForm("Новый эксперимент",onClose,onSave={onSave(title,hypothesis)}){
        Field("Название опыта",title,{title=it})
        Field("Гипотеза / что проверяем",hypothesis,{hypothesis=it},single=false)
    }
}
@Composable
fun ExperimentDialog(experiment:ExperimentRecord,cultures:List<CultureRecord>,onClose:()->Unit,onAdd:(Long,String)->Unit){
    var cultureId by remember(experiment.id){mutableStateOf(cultures.firstOrNull()?.id)}
    var group by remember(experiment.id){mutableStateOf("Контроль")}
    DialogForm(experiment.title,onClose,saveText="Добавить контейнер",onSave={if(cultureId!=null)onAdd(cultureId!!,group)}){
        Text(experiment.hypothesis)
        PickOne("Контейнер",cultures.map{it.code},cultures.firstOrNull{it.id==cultureId}?.code.orEmpty(),{s->cultureId=cultures.firstOrNull{it.code==s}?.id})
        Field("Группа опыта (контроль, опыт 1…)",group,{group=it})
        Text("Обязательно сравнивай сопоставимые исходные группы и сроки наблюдения.",style=MaterialTheme.typography.bodySmall)
    }
}
