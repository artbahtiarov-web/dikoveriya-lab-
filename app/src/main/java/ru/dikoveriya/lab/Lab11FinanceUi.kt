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
import androidx.compose.ui.unit.dp
import java.time.YearMonth
import java.time.ZoneId

@Composable
private fun Lab11FinanceTile(title:String,value:String,subtitle:String,
    onClick:()->Unit,modifier:Modifier=Modifier){
    Card(onClick=onClick,modifier=modifier,
        shape=RoundedCornerShape(17.dp),
        colors=CardDefaults.cardColors(containerColor=Lab07Palette.Champagne),
        border=BorderStroke(1.dp,Lab07Palette.Brass.copy(alpha=.3f))){
        Column(Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement=Arrangement.spacedBy(7.dp)){
            Text(title,style=MaterialTheme.typography.titleSmall,color=Lab07Palette.Ink)
            Text(value,style=MaterialTheme.typography.titleLarge,color=Lab07Palette.Ink)
            Text(subtitle,style=MaterialTheme.typography.bodySmall,color=Lab07Palette.Muted)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun Lab11FinanceScreen(
    db:LabDatabase,revision:Int,
    onBack:()->Unit,onSales:()->Unit,onMaterial:()->Unit,onAsset:()->Unit,
    onOpenItem:(Long)->Unit,onOpenCulture:(Long)->Unit,
    onVariety:(Long)->Unit,
    onChanged:()->Unit,onError:(String)->Unit,
    initialView:String="Обзор",initialAction:String="",modifier:Modifier=Modifier
){
    var month by rememberSaveable{mutableStateOf(YearMonth.now().toString())}
    var scope by rememberSaveable{mutableStateOf("Всё время")}
    var view by rememberSaveable(initialView){mutableStateOf(initialView)}
    var cashCategory by rememberSaveable{mutableStateOf("")}
    var purchase by remember{mutableStateOf(initialAction=="Покупка")}
    var expense by remember{mutableStateOf(initialAction=="Расход")}
    var selectedCash by remember{mutableStateOf<Lab11CashRow?>(null)}
    val current=remember(month){YearMonth.parse(month)}
    val from=if(scope=="Месяц")current.atDay(1)
        .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() else 0L
    val to=if(scope=="Месяц")current.plusMonths(1).atDay(1)
        .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()-1 else Long.MAX_VALUE
    val stats=remember(revision,from,to){db.financeSummary11(from,to)}
    val businessResult=remember(revision,from,to){db.performance11(from,to)}
    var costPages by rememberSaveable{mutableIntStateOf(1)}
    LaunchedEffect(from,to){costPages=1}
    val costCount=remember(revision,from,to,view){
        if(view=="Прямые затраты")db.directCostsCount11(from,to)else 0
    }
    val costs=remember(revision,from,to,view,costPages){
        if(view!="Прямые затраты")emptyList()
        else buildList{
            val upto=minOf(costCount,costPages*40)
            for(offset in 0 until upto step 40)
                addAll(db.directCostsPage11(from,to,minOf(40,upto-offset),offset))
        }
    }
    var cashPages by rememberSaveable{mutableIntStateOf(1)}
    var purchasePages by rememberSaveable{mutableIntStateOf(1)}
    LaunchedEffect(from,to,cashCategory){cashPages=1}
    val cashCount=remember(revision,from,to,cashCategory){
        db.cashCount11(from,to,cashCategory)
    }
    val cash=remember(revision,from,to,cashCategory,cashPages){
        val upto=minOf(cashCount,cashPages*40)
        buildList{
            for(offset in 0 until upto step 40)
                addAll(db.cashPage11(from,to,cashCategory,minOf(40,upto-offset),offset))
        }
    }
    val purchaseCount=remember(revision){db.purchasesCount11()}
    val purchases=remember(revision,purchasePages){
        val upto=minOf(purchaseCount,purchasePages*40)
        buildList{
            for(offset in 0 until upto step 40)
                addAll(db.purchasesPage11(minOf(40,upto-offset),offset))
        }
    }
    LazyColumn(modifier=modifier,contentPadding=PaddingValues(16.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{
            Lab07ScreenHeader("ДИКОВЕРИЯ / ЭКОНОМИКА","Финансы",
                "Денежный поток отдельно от себестоимости, имущества и материалов.",onBack)
        }
        item{
            Row(verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.spacedBy(8.dp)){
                FilterChip(selected=scope=="Всё время",onClick={scope="Всё время"},
                    label={Text("Всё время")})
                FilterChip(selected=scope=="Месяц",onClick={scope="Месяц"},
                    label={Text("По месяцам")})
                if(scope=="Месяц"){
                    IconButton(onClick={month=current.minusMonths(1).toString()}){
                        Icon(Icons.Outlined.ChevronLeft,"Предыдущий месяц")
                    }
                    Text(current.monthValue.toString()+"."+current.year,
                        style=MaterialTheme.typography.bodyMedium)
                    IconButton(onClick={month=current.plusMonths(1).toString()}){
                        Icon(Icons.Outlined.ChevronRight,"Следующий месяц")
                    }
                }
            }
        }
        item{
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                Button(onClick={purchase=true},modifier=Modifier.weight(1f)){
                    Icon(Icons.Outlined.AddShoppingCart,null)
                    Spacer(Modifier.width(4.dp));Text("Покупка")
                }
                OutlinedButton(onClick={expense=true},modifier=Modifier.weight(1f)){
                    Icon(Icons.Outlined.ReceiptLong,null)
                    Spacer(Modifier.width(4.dp));Text("Расход")
                }
            }
        }
        item{
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                listOf("Обзор","Операции","Закупки","Отчёты").forEach{label->
                    FilterChip(selected=view==label,onClick={view=label},
                        label={Text(label)})
                }
            }
        }
        if(view=="Обзор"){
            item{
                Row(horizontalArrangement=Arrangement.spacedBy(9.dp)){
                    Lab11FinanceTile("Получено",kopecks11(stats.income),
                        "Поступления",onClick={view="Операции"},modifier=Modifier.weight(1f))
                    Lab11FinanceTile("Оплачено",kopecks11(stats.paid),
                        "Денежные выплаты",onClick={view="Операции"},modifier=Modifier.weight(1f))
                }
            }
            item{
                val net=stats.income-stats.paid
                Lab11FinanceTile("Денежный результат",kopecks11(net),
                    "Деньги поступили минус реальные выплаты",
                    onClick={view="Операции"})
            }
            item{
                Row(horizontalArrangement=Arrangement.spacedBy(9.dp)){
                    Lab11FinanceTile("Материалы на складе",kopecks11(stats.inventory),
                        "Текущая оценка остатков",onClick=onMaterial,modifier=Modifier.weight(1f))
                    Lab11FinanceTile("Оборудование",kopecks11(stats.assets),
                        "Сумма покупок, не рыночная оценка",onClick=onAsset,
                        modifier=Modifier.weight(1f))
                }
            }
            item{
                Lab11FinanceTile("Прямые затраты",kopecks11(stats.direct),
                    "Использованные материалы: не новая выплата",
                    onClick={view="Прямые затраты"})
            }
            item{
                Lab11FinanceTile("Продажи и результат",kopecks11(businessResult.salesRevenue),
                    "Открыть карточки продаж и их себестоимость",
                    onClick=onSales)
            }
            if(stats.unknownCogs>0)item{
                Text("Для "+stats.unknownCogs+
                    " исторических продаж отсутствует достоверная себестоимость. Прибыль по ним не выдумывается.",
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.error)
            }
            item{
                Text("Покупка оборудования и материалов уменьшает денежный остаток один раз. Использование материала уменьшает запас и увеличивает прямые затраты, но не создаёт вторую выплату.",
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if(view=="Прямые затраты"){
            item{
                Row(verticalAlignment=Alignment.CenterVertically){
                    Text("Прямые затраты · "+costCount,
                        modifier=Modifier.weight(1f),
                        style=MaterialTheme.typography.titleLarge)
                    TextButton(onClick={view="Обзор"}){Text("К обзору")}
                }
                Text("Это стоимость фактически использованных материалов и записанных прямых расходов, а не новые платежи.",
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if(costs.isEmpty())item{Text("Прямых затрат в этом периоде нет")}
            items(costs,key={it.id}){record->
                OutlinedCard(onClick={
                    if(record.kind=="item")onOpenItem(record.entityId)
                    else if(record.kind=="culture")onOpenCulture(record.entityId)
                },modifier=Modifier.fillMaxWidth()){
                    Row(Modifier.padding(13.dp),
                        verticalAlignment=Alignment.CenterVertically){
                        Column(Modifier.weight(1f)){
                            Text(record.category,
                                style=MaterialTheme.typography.titleMedium)
                            Text((if(record.kind=="item")"Растение" else "Контейнер")+
                                " #"+record.entityId+" · "+formatDate(record.at),
                                style=MaterialTheme.typography.bodySmall)
                            Text(record.note.ifBlank{record.sourceType},
                                style=MaterialTheme.typography.labelSmall)
                        }
                        Text(kopecks11(record.amountMinor),
                            style=MaterialTheme.typography.titleSmall)
                        Icon(Icons.Outlined.ChevronRight,
                            contentDescription="Открыть объект затрат")
                    }
                }
            }
            if(costs.size<costCount)item{
                OutlinedButton(onClick={costPages++},
                    modifier=Modifier.fillMaxWidth()){
                    Text("Показать ещё · "+(costCount-costs.size))
                }
            }
        }
        if(view=="Отчёты")item{
            Lab11FinancialReportsUi(db,revision,
                onCashCategory={cashCategory=it;view="Операции"},
                onVariety=onVariety)
        }
        if(view=="Операции"){
            if(cashCategory.isNotBlank())item{
                OutlinedButton(onClick={cashCategory=""}){
                    Text("Категория: "+cashCategory+" · Сбросить")
                }
            }
            item{Text("Движение денег · "+cashCount,
                style=MaterialTheme.typography.titleLarge)}
            if(cash.isEmpty())item{Text("За выбранный период движений нет")}
            items(cash,key={it.id}){row->
                OutlinedCard(onClick={selectedCash=row},
                    modifier=Modifier.fillMaxWidth(),
                    shape=RoundedCornerShape(15.dp)){
                    Row(Modifier.padding(13.dp),verticalAlignment=Alignment.CenterVertically){
                        Icon(if(row.direction=="Доход")Icons.Outlined.TrendingUp
                            else Icons.Outlined.TrendingDown,
                            contentDescription=null,
                            tint=if(row.direction=="Доход")Lab07Palette.Moss
                                else MaterialTheme.colorScheme.error)
                        Column(Modifier.weight(1f).padding(start=10.dp)){
                            Text(row.category,style=MaterialTheme.typography.titleSmall)
                            Text(formatDate(row.date)+" · "+row.note,
                                style=MaterialTheme.typography.bodySmall,
                                maxLines=2)
                        }
                        Text((if(row.direction=="Доход")"+" else "−")+
                            kopecks11(row.amount),style=MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if(cash.size<cashCount)item{
                OutlinedButton(onClick={cashPages++},modifier=Modifier.fillMaxWidth()){
                    Text("Показать ещё · "+(cashCount-cash.size))
                }
            }
        }
        if(view=="Закупки"){
            item{Text("Закупки · "+purchaseCount,
                style=MaterialTheme.typography.titleLarge)}
            if(purchases.isEmpty())item{Text("Закупок пока нет")}
            items(purchases,key={it.id}){row->
                OutlinedCard(modifier=Modifier.fillMaxWidth(),onClick={
                    selectedCash=db.cashBySource11("purchase",row.id)
                }){
                    Column(Modifier.padding(14.dp),
                        verticalArrangement=Arrangement.spacedBy(5.dp)){
                        Text(row.name,style=MaterialTheme.typography.titleMedium)
                        Text(row.category+" · "+formatDate(row.date))
                        Text(kopecks11(row.amount)+" · "+row.supplier,
                            style=MaterialTheme.typography.bodySmall)
                        if(row.qty>0)Text("Количество: "+row.qty+" "+row.unit)
                    }
                }
            }
            if(purchases.size<purchaseCount)item{
                OutlinedButton(onClick={purchasePages++},modifier=Modifier.fillMaxWidth()){
                    Text("Показать ещё · "+(purchaseCount-purchases.size))
                }
            }
        }
    }
    if(purchase)Lab11PurchaseDialog(db,onClose={purchase=false},
        onSaved={purchase=false;onChanged()},onError=onError)
    if(expense)Lab11ExpenseDialog(db,onClose={expense=false},
        onSaved={expense=false;onChanged()},onError=onError)
    selectedCash?.let{row->
        AlertDialog(onDismissRequest={selectedCash=null},
            title={Text(row.category)},
            text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
                Text(formatDate(row.date))
                Text(row.direction+" · "+kopecks11(row.amount))
                Text(row.note)
                Text("Основание: "+row.source+" · "+
                    (row.sourceId?.toString()?:"Без связанного документа"),
                    style=MaterialTheme.typography.bodySmall)
            }},
            confirmButton={TextButton(onClick={selectedCash=null}){Text("Закрыть")}},
            dismissButton={
                if(row.source=="legacy_sale" || row.source=="item_sale")
                    TextButton(onClick={selectedCash=null;onSales()}){
                        Text("Открыть продажи")
                    }
            })
    }
}
