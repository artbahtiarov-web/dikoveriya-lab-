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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.YearMonth

@Composable
internal fun Lab11SalesScreen(db:LabDatabase,revision:Int,
    onBack:()->Unit,onItem:(Long)->Unit,onCulture:(Long)->Unit,
    modifier:Modifier=Modifier
){
    val rows=remember(revision){db.sales11()}
    var detail by remember{mutableStateOf<Lab11Sale?>(null)}
    LazyColumn(modifier=modifier,contentPadding=PaddingValues(16.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{
            Lab07ScreenHeader("ДИКОВЕРИЯ / ФИНАНСЫ","Продажи",
                "Каждая продажа ведёт к истории конкретного растения или культуры.",onBack)
        }
        item{
            Text("Продажи · "+rows.size,style=MaterialTheme.typography.titleLarge)
        }
        if(rows.isEmpty())item{Text("Продаж ещё нет")}
        items(rows,key={it.itemKind+"-"+it.id}){row->
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
                    Text(kopecks11(row.revenue),
                        style=MaterialTheme.typography.titleSmall)
                    Icon(Icons.Outlined.ChevronRight,null)
                }
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
                if(row.cogs!=null)Text("Результат: "+kopecks11(row.revenue-row.cogs),
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
