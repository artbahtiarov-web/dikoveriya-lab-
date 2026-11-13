package ru.dikoveriya.lab

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

data class Lab10MaterialMove(val id:Long,val delta:Double,val note:String,val date:Long)

fun LabDatabase.materialHistory10(id:Long):List<Lab10MaterialMove> =
    readableDatabase.rawQuery(
        "SELECT id,delta,note,created_at FROM material_moves WHERE material_id=? ORDER BY id DESC LIMIT 100",
        arrayOf(id.toString())
    ).use{c->buildList{
        while(c.moveToNext())add(Lab10MaterialMove(c.getLong(0),c.getDouble(1),
            c.getString(2),c.getLong(3)))
    }}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun Lab10ResourcesScreen(
    materials:List<MaterialRecord>,db:LabDatabase,revision:Int,
    onBack:()->Unit,onNew:()->Unit,onMove:(Long)->Unit,
    modifier:Modifier=Modifier
){
    var search by rememberSaveable{mutableStateOf("")}
    var appliedCategory by rememberSaveable{mutableStateOf("Все")}
    var onlyCritical by rememberSaveable{mutableStateOf(false)}
    var filterOpen by remember{mutableStateOf(false)}
    var detailId by remember{mutableStateOf<Long?>(null)}
    val visible=remember(materials,search,appliedCategory,onlyCritical){
        materials.filter{m->
            m.name.contains(search,true) &&
            (appliedCategory=="Все" || m.category==appliedCategory) &&
            (!onlyCritical || m.quantity==0.0 || (m.minQuantity>0.0 && m.quantity<=m.minQuantity))
        }.sortedWith(compareByDescending<MaterialRecord>{
            it.quantity==0.0 || (it.minQuantity>0.0 && it.quantity<=it.minQuantity)
        }.thenBy{it.name})
    }
    val attention=materials.count{it.quantity==0.0 ||
        (it.minQuantity>0.0 && it.quantity<=it.minQuantity)}
    Column(modifier){
        Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=12.dp),
            verticalAlignment=Alignment.CenterVertically){
            IconButton(onClick=onBack){Icon(Icons.Outlined.ArrowBack,"Назад")}
            Text("Ресурсы и склад",style=MaterialTheme.typography.headlineSmall,
                modifier=Modifier.weight(1f))
            IconButton(onClick=onNew){Icon(Icons.Outlined.Add,"Добавить материал")}
        }
        Row(Modifier.padding(horizontal=16.dp),
            horizontalArrangement=Arrangement.spacedBy(10.dp)){
            Lab10StockSummary("Позиций",materials.size.toString(),Modifier.weight(1f))
            Lab10StockSummary("Нужен запас",attention.toString(),Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.padding(horizontal=16.dp),
            verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(8.dp)){
            OutlinedTextField(search,{search=it},modifier=Modifier.weight(1f),
                singleLine=true,placeholder={Text("Найти материал")},
                leadingIcon={Icon(Icons.Outlined.Search,null)})
            IconButton(onClick={filterOpen=true},
                modifier=Modifier.size(52.dp).background(
                    MaterialTheme.colorScheme.surface,RoundedCornerShape(14.dp))){
                Icon(Icons.Outlined.Tune,"Фильтры склада")
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("Показано: "+visible.size+" · остатки из реального учёта",
            style=MaterialTheme.typography.bodySmall,
            modifier=Modifier.padding(horizontal=16.dp),
            color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(visible.isEmpty()){
            Column(Modifier.fillMaxWidth().padding(25.dp),
                horizontalAlignment=Alignment.CenterHorizontally){
                Icon(Icons.Outlined.Inventory2,null,modifier=Modifier.size(44.dp))
                Text("Материалов по выбранным условиям нет")
                OutlinedButton(onClick=onNew){Text("Добавить материал")}
            }
        }else LazyColumn(modifier=Modifier.fillMaxSize(),
            contentPadding=PaddingValues(16.dp),
            verticalArrangement=Arrangement.spacedBy(9.dp)){
            items(visible,key={it.id}){material->
                val low=material.quantity==0.0 ||
                    (material.minQuantity>0.0 && material.quantity<=material.minQuantity)
                OutlinedCard(onClick={detailId=material.id},
                    modifier=Modifier.fillMaxWidth().heightIn(min=96.dp),
                    shape=RoundedCornerShape(17.dp),
                    border=BorderStroke(1.dp,if(low)MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.outlineVariant),
                    colors=CardDefaults.outlinedCardColors(
                        containerColor=MaterialTheme.colorScheme.surface)){
                    Row(Modifier.fillMaxWidth().padding(13.dp),
                        horizontalArrangement=Arrangement.spacedBy(12.dp),
                        verticalAlignment=Alignment.CenterVertically){
                        Surface(shape=RoundedCornerShape(12.dp),
                            color=MaterialTheme.colorScheme.primaryContainer){
                            Icon(when(material.category){
                                "Реактивы"->Icons.Outlined.Science
                                "Инвентарь"->Icons.Outlined.Build
                                else->Icons.Outlined.Inventory2
                            },null,tint=MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier=Modifier.padding(11.dp).size(24.dp))
                        }
                        Column(Modifier.weight(1f),
                            verticalArrangement=Arrangement.spacedBy(4.dp)){
                            Text(material.name,style=MaterialTheme.typography.titleMedium,
                                maxLines=2,overflow=TextOverflow.Ellipsis)
                            Text(material.category+" · минимум: "+
                                RecipeCalculator.formatAmount(material.minQuantity)+" "+material.unit,
                                style=MaterialTheme.typography.bodySmall)
                            if(low)Text("Пополнить запас",color=MaterialTheme.colorScheme.error,
                                style=MaterialTheme.typography.labelLarge)
                        }
                        Column(horizontalAlignment=Alignment.End){
                            Text(RecipeCalculator.formatAmount(material.quantity),
                                style=MaterialTheme.typography.titleLarge)
                            Text(material.unit,style=MaterialTheme.typography.bodySmall)
                            Icon(Icons.Outlined.ChevronRight,null)
                        }
                    }
                }
            }
        }
    }
    if(filterOpen){
        var draftCategory by remember(filterOpen){mutableStateOf(appliedCategory)}
        var draftLow by remember(filterOpen){mutableStateOf(onlyCritical)}
        ModalBottomSheet(onDismissRequest={filterOpen=false}){
            Column(Modifier.padding(20.dp),
                verticalArrangement=Arrangement.spacedBy(12.dp)){
                Text("Фильтры склада",style=MaterialTheme.typography.headlineSmall)
                listOf("Все","Реактивы","Расходники","Инвентарь","Другое").forEach{value->
                    Row(Modifier.fillMaxWidth().heightIn(min=44.dp)
                        .clickable{draftCategory=value},verticalAlignment=Alignment.CenterVertically){
                        RadioButton(selected=draftCategory==value,onClick={draftCategory=value})
                        Spacer(Modifier.width(6.dp))
                        Text(value)
                    }
                }
                Row(verticalAlignment=Alignment.CenterVertically){
                    Checkbox(checked=draftLow,onCheckedChange={draftLow=it})
                    Text("Только позиции, требующие пополнения")
                }
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    OutlinedButton(onClick={draftCategory="Все";draftLow=false},
                        modifier=Modifier.weight(1f)){Text("Сбросить")}
                    Button(onClick={
                        appliedCategory=draftCategory;onlyCritical=draftLow
                        filterOpen=false
                    },modifier=Modifier.weight(1f)){Text("Применить")}
                }
            }
        }
    }
    detailId?.let{id->
        val material=materials.firstOrNull{it.id==id}
        if(material!=null){
            val moves=remember(id,revision){db.materialHistory10(id)}
            ModalBottomSheet(onDismissRequest={detailId=null}){
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal=20.dp,vertical=10.dp),
                    verticalArrangement=Arrangement.spacedBy(11.dp)){
                    Text(material.name,style=MaterialTheme.typography.headlineSmall)
                    Text(material.category+" · "+RecipeCalculator.formatAmount(material.quantity)+
                        " "+material.unit)
                    Text("Минимальный запас: "+
                        RecipeCalculator.formatAmount(material.minQuantity)+" "+material.unit)
                    Button(onClick={detailId=null;onMove(id)},modifier=Modifier.fillMaxWidth()){
                        Icon(Icons.Outlined.SwapHoriz,null)
                        Spacer(Modifier.width(8.dp));Text("Приход или расход")
                    }
                    Text("Движение материала",style=MaterialTheme.typography.titleMedium)
                    if(moves.isEmpty())Text("Движений пока нет. Отображается начальный остаток.")
                    moves.forEach{move->
                        Card(colors=CardDefaults.cardColors(
                            containerColor=MaterialTheme.colorScheme.surface)){
                            Column(Modifier.fillMaxWidth().padding(10.dp)){
                                Text((if(move.delta>0)"Приход +" else "Расход ")+
                                    RecipeCalculator.formatAmount(move.delta)+" "+material.unit)
                                Text(formatDate(move.date)+" · "+move.note,
                                    style=MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }
            }
        }
    }
}

@Composable
private fun Lab10StockSummary(title:String,value:String,modifier:Modifier=Modifier){
    Card(modifier=modifier,shape=RoundedCornerShape(16.dp),
        colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)){
        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){
            Text(value,style=MaterialTheme.typography.headlineSmall)
            Text(title,style=MaterialTheme.typography.bodySmall)
        }
    }
}
