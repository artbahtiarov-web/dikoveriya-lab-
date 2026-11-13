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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun Lab11SalesScreen(db:LabDatabase,revision:Int,
    onBack:()->Unit,onItem:(Long)->Unit,onCulture:(Long)->Unit,
    modifier:Modifier=Modifier
){
    var search by rememberSaveable{mutableStateOf("")}
    var kind by rememberSaveable{mutableStateOf("Все")}
    var from by rememberSaveable{mutableStateOf("")}
    var to by rememberSaveable{mutableStateOf("")}
    var order by rememberSaveable{mutableStateOf("Сначала новые")}
    var page by rememberSaveable{mutableIntStateOf(1)}
    var filterOpen by remember{mutableStateOf(false)}
    var rows by remember{mutableStateOf(emptyList<Lab11Sale>())}
    var total by remember{mutableIntStateOf(0)}
    var loading by remember{mutableStateOf(true)}
    var detail by remember{mutableStateOf<Lab11Sale?>(null)}
    val start=validDate11(from)?.atStartOfDay(ZoneId.systemDefault())
        ?.toInstant()?.toEpochMilli()?:0L
    val end=validDate11(to)?.plusDays(1)?.atStartOfDay(ZoneId.systemDefault())
        ?.toInstant()?.toEpochMilli()?.minus(1L)?:Long.MAX_VALUE
    val valid=from.isBlank() || validDate11(from)!=null
    val validEnd=to.isBlank() || validDate11(to)!=null
    val periodValid=valid && validEnd && start<=end
    val query=remember(search,kind,order,start,end,periodValid){
        if(periodValid)Lab11SaleFilter(search,kind,start,end,order) else null
    }
    LaunchedEffect(revision,query,page){
        if(query==null){
            rows=emptyList();total=0;loading=false
        }else{
            loading=true
            try{
                val result=withContext(Dispatchers.IO){
                    val cnt=db.salesCount11(query)
                    val max=minOf(cnt,page*40)
                    val sales=buildList{
                        for(offset in 0 until max step 40)
                            addAll(db.salesPage11(query,minOf(40,max-offset),offset))
                    }
                    cnt to sales
                }
                total=result.first;rows=result.second
            }finally{loading=false}
        }
    }
    LazyColumn(modifier=modifier,
        contentPadding=PaddingValues(16.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{
            Lab07ScreenHeader("ДИКОВЕРИЯ / ФИНАНСЫ","Продажи",
                "Результат каждой продажи связан с историей выращивания.",onBack)
        }
        item{
            OutlinedTextField(value=search,onValueChange={search=it;page=1},
                modifier=Modifier.fillMaxWidth(),singleLine=true,
                label={Text("Найти продажу, сорт, номер, покупателя")},
                leadingIcon={Icon(Icons.Outlined.Search,contentDescription=null)},
                trailingIcon={
                    IconButton(onClick={filterOpen=true}){
                        Icon(Icons.Outlined.Tune,contentDescription="Фильтры продаж")
                    }
                })
        }
        if(kind!="Все" || from.isNotBlank() || to.isNotBlank() ||
            order!="Сначала новые")item{
            Text("Применён фильтр: "+kind+
                (if(from.isBlank()&&to.isBlank())"" else
                    " · "+from.ifBlank{"начало"}+"—"+to.ifBlank{"сегодня"})+
                " · "+order,
                style=MaterialTheme.typography.labelSmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item{Text("Продажи · "+total,style=MaterialTheme.typography.titleLarge)}
        if(!periodValid)item{
            Text("Проверь даты фильтра: период указан некорректно",
                color=MaterialTheme.colorScheme.error)
        }
        if(loading)item{LinearProgressIndicator(Modifier.fillMaxWidth())}
        if(!loading && rows.isEmpty() && periodValid)item{
            Text("Продаж по этим условиям нет.",
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(rows,key={
            (if(it.plantItemId!=null)"plant:" else "culture:")+it.id
        }){row->
            OutlinedCard(onClick={detail=row},
                modifier=Modifier.fillMaxWidth(),
                shape=RoundedCornerShape(18.dp),
                border=BorderStroke(1.dp,Lab07Palette.Brass.copy(alpha=.28f))){
                Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.spacedBy(12.dp)){
                    Surface(shape=RoundedCornerShape(12.dp),
                        color=Lab07Palette.Champagne){
                        Icon(Icons.Outlined.LocalOffer,null,
                            tint=Lab07Palette.Forest,
                            modifier=Modifier.padding(11.dp).size(28.dp))
                    }
                    Column(Modifier.weight(1f),
                        verticalArrangement=Arrangement.spacedBy(4.dp)){
                        Text(row.itemCode,style=MaterialTheme.typography.titleMedium)
                        Text(row.itemKind+" · "+formatDate(row.date),
                            style=MaterialTheme.typography.bodySmall)
                        Text(row.quantity.toString()+" шт. · "+row.channel,
                            style=MaterialTheme.typography.bodySmall)
                    }
                    Text(kopecks11(row.revenue),style=MaterialTheme.typography.titleSmall)
                    Icon(Icons.Outlined.ChevronRight,null)
                }
            }
        }
        if(rows.size<total)item{
            OutlinedButton(onClick={page++},
                modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){
                Text("Показать ещё · "+(total-rows.size))
            }
        }
    }
    if(filterOpen){
        var draftKind by remember(filterOpen){mutableStateOf(kind)}
        var draftFrom by remember(filterOpen){mutableStateOf(from)}
        var draftTo by remember(filterOpen){mutableStateOf(to)}
        var draftOrder by remember(filterOpen){mutableStateOf(order)}
        var invalid by remember{mutableStateOf(false)}
        ModalBottomSheet(onDismissRequest={filterOpen=false}){
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(20.dp),verticalArrangement=Arrangement.spacedBy(11.dp)){
                Text("Фильтры продаж",style=MaterialTheme.typography.headlineSmall)
                PickOne("Тип растения",
                    listOf("Все","in vitro","Горшечное","Черенок","Маточник","Другое"),
                    draftKind,{draftKind=it;invalid=false})
                Lab11DateField("Начало периода",draftFrom,{draftFrom=it;invalid=false},
                    allowClear=true)
                Lab11DateField("Конец периода",draftTo,{draftTo=it;invalid=false},
                    allowClear=true)
                PickOne("Сортировка",
                    listOf("Сначала новые","Сначала старые","Выручка ↓","Выручка ↑"),
                    draftOrder,{draftOrder=it})
                if(invalid)Text("Неверный период. Проверь даты.",
                    color=MaterialTheme.colorScheme.error)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    OutlinedButton(onClick={
                        draftKind="Все";draftFrom="";draftTo=""
                        draftOrder="Сначала новые";invalid=false
                    },modifier=Modifier.weight(1f)){Text("Сбросить")}
                    Button(onClick={
                        val a=validDate11(draftFrom)?.toEpochDay()
                        val b=validDate11(draftTo)?.toEpochDay()
                        invalid=(draftFrom.isNotBlank()&&a==null)||
                            (draftTo.isNotBlank()&&b==null)||
                            (a!=null&&b!=null&&a>b)
                        if(!invalid){
                            kind=draftKind;from=draftFrom;to=draftTo
                            order=draftOrder;page=1;filterOpen=false
                        }
                    },modifier=Modifier.weight(1f)){Text("Применить")}
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
    detail?.let{row->
        val item=remember(row.plantItemId){row.plantItemId?.let{db.item11(it)}}
        val culture=remember(row.cultureId){row.cultureId?.let{db.cultureById(it)}}
        AlertDialog(onDismissRequest={detail=null},
            title={Text("Продажа · "+row.itemCode)},
            text={Column(Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement=Arrangement.spacedBy(9.dp)){
                Text("Дата: "+formatDate(row.date))
                Text("Количество: "+row.quantity)
                Text("Выручка: "+kopecks11(row.revenue))
                Text("Прямая себестоимость: "+
                    (row.cogs?.let{kopecks11(it)}?:"Не определена"),
                    style=MaterialTheme.typography.bodyMedium)
                if(row.cogs!=null)
                    Text("Результат: "+kopecks11(row.revenue-row.cogs),
                        style=MaterialTheme.typography.titleMedium)
                else Text("Прибыль не рассчитывается без подтверждённой себестоимости.",
                    color=MaterialTheme.colorScheme.error,
                    style=MaterialTheme.typography.bodySmall)
                if(row.channel.isNotBlank())Text("Канал: "+row.channel)
                if(row.buyer.isNotBlank())Text("Покупатель: "+row.buyer)
                if(row.note.isNotBlank())Text("Примечание: "+row.note)
                if(item!=null){
                    Text("Растение: "+item.plantName+" · "+item.kind)
                    Text("Появилось: "+formatDate(item.acquiredAt))
                    Text("Место: "+item.location.ifBlank{"Не указано"})
                }else if(culture!=null){
                    Text("Сорт: "+culture.plantName)
                    Text("В культуре с: "+formatDate(culture.createdAt))
                    Text("Пассаж: "+culture.passage)
                }
            }},
            confirmButton={
                TextButton(onClick={
                    detail=null
                    if(row.plantItemId!=null)onItem(row.plantItemId)
                    else if(row.cultureId!=null)onCulture(row.cultureId)
                }){Text("Открыть паспорт и историю")}
            },
            dismissButton={TextButton(onClick={detail=null}){Text("Закрыть")}})
    }
}
