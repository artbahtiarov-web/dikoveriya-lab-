package ru.dikoveriya.lab

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

@Composable
internal fun Lab11FinancialReportsUi(
    db:LabDatabase,revision:Int,onCashCategory:(String)->Unit,
    onVariety:(Long)->Unit
){
    var period by rememberSaveable{mutableStateOf("Месяц")}
    var selectedMonth by rememberSaveable{mutableStateOf(YearMonth.now().toString())}
    var startText by rememberSaveable{mutableStateOf(dateText11())}
    var endText by rememberSaveable{mutableStateOf(dateText11())}
    var categoryFilter by rememberSaveable{mutableStateOf("")}
    val month=remember(selectedMonth){YearMonth.parse(selectedMonth)}
    val currentYear=month.year
    val monthStart=month.atDay(1)
    val dates=remember(period,selectedMonth,startText,endText){
        val begin=when(period){
            "Месяц"->monthStart
            "Квартал"->YearMonth.of(currentYear,((month.monthValue-1)/3)*3+1).atDay(1)
            "Год"->LocalDate.of(currentYear,1,1)
            "Период"->validDate11(startText)
            else->null
        }
        val after=when(period){
            "Месяц"->month.plusMonths(1).atDay(1)
            "Квартал"->begin?.plusMonths(3)
            "Год"->LocalDate.of(currentYear+1,1,1)
            "Период"->validDate11(endText)?.plusDays(1)
            else->null
        }
        if(begin==null || after==null || after<=begin) {
            if(period=="Всё время")0L to Long.MAX_VALUE else null
        }else{
            begin.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() to
                (after.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()-1)
        }
    }
    val general=remember(revision,dates){dates?.let{db.performance11(it.first,it.second)}}
    val byCategory=remember(revision,dates){dates?.let{
        db.expenseCategories11(it.first,it.second)
    }?:emptyList()}
    val varieties=remember(revision,dates){dates?.let{
        db.varietyEconomics11(it.first,it.second)
    }?:emptyList()}
    val months=remember(revision){db.monthlyCash11(12)}
    Column(verticalArrangement=Arrangement.spacedBy(14.dp)){
        Text("Экономика проекта",style=MaterialTheme.typography.titleLarge)
        Text("Отчёт по фактическим операциям. Оборудование и складские закупки относятся к денежному потоку, но не списываются на каждое растение.",
            style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement=Arrangement.spacedBy(6.dp)){
            listOf("Месяц","Квартал","Год","Период","Всё время").forEach{value->
                FilterChip(selected=period==value,onClick={period=value},
                    label={Text(value)})
            }
        }
        if(period in setOf("Месяц","Квартал","Год")){
            Row(verticalAlignment=Alignment.CenterVertically){
                OutlinedButton(onClick={selectedMonth=month.minusMonths(
                    if(period=="Год")12 else if(period=="Квартал")3 else 1
                ).toString()}){Text("‹")}
                Text("  "+month.monthValue+"."+month.year+"  ",
                    style=MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick={selectedMonth=month.plusMonths(
                    if(period=="Год")12 else if(period=="Квартал")3 else 1
                ).toString()}){Text("›")}
            }
        }
        if(period=="Период"){
            Lab11DateField("Начало",startText,{startText=it})
            Lab11DateField("Конец",endText,{endText=it})
        }
        if(dates==null){
            Text("Проверь даты: конец периода должен быть позже начала.",
                color=MaterialTheme.colorScheme.error)
        }else if(general!=null){
            val performance=general
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                Card(Modifier.weight(1f),
                    colors=CardDefaults.cardColors(containerColor=Lab07Palette.Champagne)){
                    Column(Modifier.padding(10.dp)){
                        Text("Выручка продаж",color=Lab07Palette.Ink,
                            style=MaterialTheme.typography.labelMedium)
                        Text(kopecks11(performance.salesRevenue),
                            style=MaterialTheme.typography.titleLarge,color=Lab07Palette.Ink)
                    }
                }
                Card(Modifier.weight(1f),
                    colors=CardDefaults.cardColors(containerColor=Lab07Palette.Champagne)){
                    Column(Modifier.padding(10.dp)){
                        Text("Себестоимость продаж",color=Lab07Palette.Ink,
                            style=MaterialTheme.typography.labelMedium)
                        Text(kopecks11(performance.verifiedCostOfSales),
                            style=MaterialTheme.typography.titleLarge,color=Lab07Palette.Ink)
                    }
                }
            }
            Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)){
                Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){
                    Text("Общие расходы периода",style=MaterialTheme.typography.titleMedium)
                    Text(kopecks11(performance.generalExpenses),
                        style=MaterialTheme.typography.titleLarge)
                    HorizontalDivider()
                    Text("Результат после общих расходов",
                        style=MaterialTheme.typography.titleMedium)
                    Text(performance.financialResult?.let{kopecks11(it)}
                        ?:"Не определяется",style=MaterialTheme.typography.headlineSmall)
                    if(performance.missingCostSales>0)
                        Text("У "+performance.missingCostSales+
                            " исторических продаж не подтверждена себестоимость. Прибыль не рассчитывается.",
                            color=MaterialTheme.colorScheme.error,
                            style=MaterialTheme.typography.bodySmall)
                }
            }
        }
        Text("Денежный поток за 12 месяцев",
            style=MaterialTheme.typography.titleMedium)
        if(months.any{it.paid>0 || it.received>0}){
            val incomeColor=Lab07Palette.Moss
            val paidColor=Lab07Palette.Brass
            val maxAmount=(months.maxOfOrNull{maxOf(it.received,it.paid)}?:1L)
                .coerceAtLeast(1L).toFloat()
            Canvas(Modifier.fillMaxWidth().height(160.dp)){
                val w=size.width/months.size
                val plotHeight=size.height-8.dp.toPx()
                months.forEachIndexed{i,row->
                    val incomeH=plotHeight*(row.received.toFloat()/maxAmount)
                    val paidH=plotHeight*(row.paid.toFloat()/maxAmount)
                    drawRect(incomeColor,topLeft=Offset(i*w+w*.1f,plotHeight-incomeH),
                        size=androidx.compose.ui.geometry.Size(w*.35f,incomeH))
                    drawRect(paidColor,topLeft=Offset(i*w+w*.55f,plotHeight-paidH),
                        size=androidx.compose.ui.geometry.Size(w*.35f,paidH))
                }
            }
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
                Text("● Поступления",color=Lab07Palette.Moss,
                    style=MaterialTheme.typography.bodySmall)
                Text("● Выплаты",color=Lab07Palette.Brass,
                    style=MaterialTheme.typography.bodySmall)
            }
            Text("Слева направо: "+months.first().month+" — "+months.last().month,
                style=MaterialTheme.typography.labelSmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }else{
            Text("Денежных операций за последние 12 месяцев нет.")
        }
        Text("Расходы по категориям",style=MaterialTheme.typography.titleMedium)
        if(byCategory.isEmpty())Text("Расходов за период нет")
        byCategory.forEach{row->
            OutlinedCard(onClick={onCashCategory(row.name)},
                modifier=Modifier.fillMaxWidth()){
                Row(Modifier.padding(13.dp),verticalAlignment=Alignment.CenterVertically){
                    Column(Modifier.weight(1f)){
                        Text(row.name,style=MaterialTheme.typography.titleSmall)
                        Text(row.transactions.toString()+" операций",
                            style=MaterialTheme.typography.labelSmall)
                    }
                    Text(kopecks11(row.amount))
                    Icon(Icons.Outlined.ChevronRight,
                        contentDescription="Открыть операции категории")
                }
            }
        }
        Text("Продажи по сортам",style=MaterialTheme.typography.titleMedium)
        if(varieties.isEmpty())Text("За выбранный период продаж нет")
        varieties.forEach{v->
            OutlinedCard(onClick={onVariety(v.plantId)},modifier=Modifier.fillMaxWidth()){
                Column(Modifier.padding(14.dp),
                    verticalArrangement=Arrangement.spacedBy(6.dp)){
                    Row(verticalAlignment=Alignment.CenterVertically){
                        Text(v.name,style=MaterialTheme.typography.titleSmall,
                            modifier=Modifier.weight(1f))
                        Icon(Icons.Outlined.ChevronRight,
                            contentDescription="Открыть сорт")
                    }
                    Text("Реализовано: "+v.soldUnits+" раст. · "+kopecks11(v.revenue))
                    Text("Себестоимость: "+
                        (v.costOfSales?.let{kopecks11(it)}?:"Не подтверждена"),
                        style=MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
