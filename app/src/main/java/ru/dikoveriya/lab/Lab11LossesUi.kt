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

internal data class Lab11LossRow(val id:Long,val cultureId:Long,val code:String,
    val operationId:String,val time:Long,val count:Int,val reason:String)

internal fun LabDatabase.lossesByPlant11(plantId:Long):List<Lab11LossRow> =
    readableDatabase.rawQuery("""SELECT l.id,l.culture_id,c.code,
        l.operation_id,o.occurred_at,l.quantity,l.reason
        FROM operation_losses l
        JOIN lab_operations o ON o.id=l.operation_id AND o.status='Проведена'
        JOIN cultures c ON c.id=l.culture_id WHERE c.plant_id=?
        ORDER BY o.occurred_at DESC,l.id DESC LIMIT 500""",
        arrayOf(plantId.toString())).use{c->buildList{
        while(c.moveToNext())add(Lab11LossRow(c.getLong(0),c.getLong(1),
            c.getString(2),c.getString(3),c.getLong(4),c.getInt(5),c.getString(6)))
    }}

@Composable
internal fun Lab11LossesScreen(plantId:Long,db:LabDatabase,revision:Int,
    onBack:()->Unit,onCulture:(Long)->Unit,onHistory:(Long)->Unit,
    modifier:Modifier=Modifier){
    val plant=remember(plantId,revision){db.plants().firstOrNull{it.id==plantId}}
    val rows=remember(plantId,revision){db.lossesByPlant11(plantId)}
    LazyColumn(modifier,contentPadding=PaddingValues(16.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{
            Lab07ScreenHeader("АНАЛИТИКА / ПОДТВЕРЖДЁННЫЕ ПОТЕРИ",
                plant?.name?:"Потери", "Открой любую запись, чтобы перейти к исходному контейнеру и его истории.",onBack)
        }
        item{Text("Подтверждённых записей: "+rows.size,
            style=MaterialTheme.typography.titleMedium)}
        if(rows.isEmpty())item{Text("Нет проведённых операций потерь для этого сорта.")}
        items(rows,key={it.id}){row->
            OutlinedCard(onClick={onCulture(row.cultureId)},
                modifier=Modifier.fillMaxWidth(),
                shape=RoundedCornerShape(17.dp),
                border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)){
                Column(Modifier.padding(14.dp),
                    verticalArrangement=Arrangement.spacedBy(8.dp)){
                    Row(verticalAlignment=Alignment.CenterVertically){
                        Icon(Icons.Outlined.WarningAmber,contentDescription=null,
                            tint=MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(9.dp))
                        Text(row.code,style=MaterialTheme.typography.titleMedium,
                            modifier=Modifier.weight(1f))
                        Icon(Icons.Outlined.ChevronRight,contentDescription="Открыть контейнер")
                    }
                    Text("Потерь: "+row.count+" · "+formatDate(row.time))
                    Text(row.reason,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick={onHistory(row.cultureId)}){
                        Text("История и аудит")
                        Icon(Icons.Outlined.ArrowForward,contentDescription=null)
                    }
                }
            }
        }
    }
}
