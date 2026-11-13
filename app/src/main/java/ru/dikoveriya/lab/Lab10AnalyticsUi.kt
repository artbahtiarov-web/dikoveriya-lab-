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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

private data class Lab10AnalyticsData(
    val total:Int,
    val active:Int,
    val control:Int,
    val operations:Int,
    val revenue:Double,
    val stages:List<Pair<String,Int>>
)

private fun LabDatabase.analytics10(since:Long?):Lab10AnalyticsData{
    val db=readableDatabase
    fun count(sql:String,args:Array<String> = emptyArray())=
        db.rawQuery(sql,args).use{c->if(c.moveToFirst())c.getInt(0)else 0}
    val total=count("SELECT COUNT(*) FROM cultures")
    val active=count("SELECT COUNT(*) FROM cultures WHERE status IN ('В культуре','На контроле','Карантин')")
    val control=count("SELECT COUNT(*) FROM cultures WHERE status IN ('На контроле','Карантин')")
    val query=if(since==null)"SELECT COUNT(*) FROM lab_operations WHERE status='Проведена'"
        else "SELECT COUNT(*) FROM lab_operations WHERE status='Проведена' AND occurred_at>=?"
    val operations=count(query,if(since==null)emptyArray() else arrayOf(since.toString()))
    val revenue=db.rawQuery("SELECT COALESCE(SUM(price),0) FROM sales",null)
        .use{c->if(c.moveToFirst())c.getDouble(0)else 0.0}
    val stages=db.rawQuery("""
        SELECT stage,COUNT(*) FROM cultures
        WHERE status IN ('В культуре','На контроле','Карантин')
        GROUP BY stage ORDER BY COUNT(*) DESC
    """.trimIndent(),null).use{c->buildList{
        while(c.moveToNext())add(c.getString(0) to c.getInt(1))
    }}
    return Lab10AnalyticsData(total,active,control,operations,revenue,stages)
}

/** All cards use real SQL counts; no fabricated success/efficiency percentages. */
@Composable
internal fun Lab10AnalyticsScreen(db:LabDatabase,revision:Int,
    onMetric:(String)->Unit={},onBreakdown:(String,Long)->Unit={_,_->},
    modifier:Modifier=Modifier
){
    var period by rememberSaveable{mutableStateOf("30 дней")}
    val since=when(period){
        "30 дней"->System.currentTimeMillis()-30L*86400000L
        "90 дней"->System.currentTimeMillis()-90L*86400000L
        else->null
    }
    val state by produceState<Lab10AnalyticsData?>(
        initialValue=null,key1=revision,key2=period){
        value=withContext(Dispatchers.IO){db.analytics10(since)}
    }
    LazyColumn(modifier,contentPadding=PaddingValues(16.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{
            Lab07BrandHero("Аналитика",
                "Реальные показатели из учёта лаборатории.",
                "ДИКОВЕРИЯ LAB · РЕЗУЛЬТАТЫ")
        }
        item{
            Row(verticalAlignment=Alignment.CenterVertically){
                Text("Обзор",style=MaterialTheme.typography.titleLarge,
                    modifier=Modifier.weight(1f))
                Icon(Icons.Outlined.BarChart,null,tint=MaterialTheme.colorScheme.tertiary)
            }
        }
        if(state==null)item{CircularProgressIndicator()}
        state?.let{data->
            item{
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
                    Lab10StatCard("Всего контейнеров",data.total.toString(),
                        Icons.Outlined.Inventory2,Modifier.weight(1f)){onMetric("all")}
                    Lab10StatCard("Активных",data.active.toString(),
                        Icons.Outlined.Spa,Modifier.weight(1f)){onMetric("active")}
                }
            }
            item{
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
                    Lab10StatCard("На контроле",data.control.toString(),
                        Icons.Outlined.ReportProblem,Modifier.weight(1f)){onMetric("attention")}
                    Lab10StatCard("Выручка по записям",
                        String.format(Locale("ru"),"%.0f ₽",data.revenue),
                        Icons.Outlined.Payments,Modifier.weight(1f)){onMetric("sales")}
                }
            }
            item{
                Text("Операции",style=MaterialTheme.typography.titleLarge)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement=Arrangement.spacedBy(6.dp)){
                    listOf("30 дней","90 дней","Всё время").forEach{option->
                        FilterChip(selected=period==option,onClick={period=option},
                            label={Text(option)})
                    }
                }
            }
            item{
                Card(onClick={onMetric("operations")},colors=CardDefaults.cardColors(
                    containerColor=MaterialTheme.colorScheme.surface),
                    shape=RoundedCornerShape(18.dp)){
                    Column(Modifier.padding(17.dp)){
                        Text("Проведённых операций",
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(data.operations.toString(),
                            style=MaterialTheme.typography.headlineMedium)
                        Text("Только зарегистрированные проведённые операции. Отменённые исключены.",
                            style=MaterialTheme.typography.bodySmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item{Text("Активные контейнеры по этапам",
                style=MaterialTheme.typography.titleLarge)}
            if(data.stages.isEmpty())item{Text("Нет активных контейнеров")}
            else items(data.stages,key={it.first}){(stage,count)->
                Card(onClick={onMetric("stage:"+stage)},colors=CardDefaults.cardColors(
                    containerColor=MaterialTheme.colorScheme.surface),
                    shape=RoundedCornerShape(15.dp)){
                    Column(Modifier.fillMaxWidth().padding(13.dp),
                        verticalArrangement=Arrangement.spacedBy(7.dp)){
                        Row{
                            Text(stage,modifier=Modifier.weight(1f),
                                style=MaterialTheme.typography.titleMedium)
                            Text(count.toString(),style=MaterialTheme.typography.titleMedium)
                        }
                        Box(Modifier.fillMaxWidth().height(8.dp)
                            .background(Lab07Palette.Sand,RoundedCornerShape(4.dp))){
                            Box(Modifier.fillMaxWidth(
                                if(data.active==0)0f else count.toFloat()/data.active)
                                .height(8.dp)
                                .background(Lab07Palette.Moss,RoundedCornerShape(4.dp)))
                        }
                    }
                }
            }
            item{
                Text("Показатели роста и эффективности рассчитываются только по сопоставимым измерениям. Без корректной экспериментальной выборки процент эффективности не отображается.",
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item(key="detailed-analysis"){Lab10AnalyticsDetails(db,revision,onBreakdown)}
    }
}

@Composable
private fun Lab10StatCard(label:String,value:String,
    icon:androidx.compose.ui.graphics.vector.ImageVector,modifier:Modifier=Modifier,
    onClick:()->Unit){
    Card(onClick=onClick,modifier=modifier,colors=heightInColor(),shape=RoundedCornerShape(18.dp),
        border=BorderStroke(1.dp,Lab07Palette.Brass.copy(alpha=.28f))){
        Column(Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement=Arrangement.spacedBy(7.dp)){
            Icon(icon,contentDescription=null,tint=Lab07Palette.Forest,
                modifier=Modifier.size(26.dp))
            Text(value,style=MaterialTheme.typography.headlineSmall,color=Lab07Palette.Ink)
            Text(label,style=MaterialTheme.typography.bodySmall,
                color=Lab07Palette.Muted)
        }
    }
}
@Composable
private fun heightInColor()=CardDefaults.cardColors(
    containerColor=Lab07Palette.Champagne)
