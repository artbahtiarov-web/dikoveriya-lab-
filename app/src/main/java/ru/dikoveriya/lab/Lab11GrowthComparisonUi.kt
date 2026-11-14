package ru.dikoveriya.lab

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.max

/**
 * Compares measured observations only. Missing data remains missing;
 * never treats absent height/leaf/root counts as zero.
 */
@Composable
internal fun Lab11GrowthComparisonDialog(
    db:LabDatabase,onDismiss:()->Unit,onOpenItem:(Long)->Unit
){
    var leftCode by rememberSaveable{mutableStateOf("")}
    var rightCode by rememberSaveable{mutableStateOf("")}
    var metric by rememberSaveable{mutableStateOf("Высота, мм")}
    fun sampleValue(sample:Lab11GrowthSample):Double?=when(metric){
        "Высота, мм"->sample.heightMm
        "Листья"->sample.leaves?.toDouble()
        "Корни"->sample.roots?.toDouble()
        else->null
    }
    val leftId=remember(leftCode){db.itemIdByCode11(leftCode.trim().uppercase())}
    val rightId=remember(rightCode){db.itemIdByCode11(rightCode.trim().uppercase())}
    val left=remember(leftId){leftId?.let{db.itemGrowth11(it)}?:emptyList()}
    val right=remember(rightId){rightId?.let{db.itemGrowth11(it)}?:emptyList()}
    val seriesLeft=left.mapNotNull{sampleValue(it)?.let{v->it.at to v}}
    val seriesRight=right.mapNotNull{sampleValue(it)?.let{v->it.at to v}}
    AlertDialog(onDismissRequest=onDismiss,
        title={Text("Сравнение растений")},
        text={
            Column(Modifier.heightIn(max=530.dp).verticalScroll(rememberScrollState()),
                verticalArrangement=Arrangement.spacedBy(12.dp)){
                Text("Укажи номера двух экземпляров. Сравнение опирается только на реально записанные измерения.",
                    style=MaterialTheme.typography.bodySmall)
                OutlinedTextField(leftCode,{leftCode=it},singleLine=true,
                    label={Text("Растение A · PLT-000001")},
                    modifier=Modifier.fillMaxWidth())
                OutlinedTextField(rightCode,{rightCode=it},singleLine=true,
                    label={Text("Растение B · PLT-000002")},
                    modifier=Modifier.fillMaxWidth())
                PickOne("Показатель",listOf("Высота, мм","Листья","Корни"),
                    metric,{metric=it})
                if(leftCode.isNotBlank() && leftId==null)
                    Text("Растение A не найдено",color=MaterialTheme.colorScheme.error)
                if(rightCode.isNotBlank() && rightId==null)
                    Text("Растение B не найдено",color=MaterialTheme.colorScheme.error)
                if(leftId!=null && rightId!=null){
                    Text("A: "+seriesLeft.size+" наблюдений · B: "+
                        seriesRight.size+" наблюдений",
                        style=MaterialTheme.typography.bodyMedium)
                    if(seriesLeft.size<2 || seriesRight.size<2){
                        Text("Для сопоставления динамики каждому растению требуется как минимум два замера выбранного показателя.",
                            style=MaterialTheme.typography.bodySmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }else{
                        val all=seriesLeft+seriesRight
                        val minY=all.minOf{it.second}
                        val maxY=all.maxOf{it.second}
                        val minX=all.minOf{it.first}
                        val maxX=all.maxOf{it.first}
                        val ySpan=(maxY-minY).coerceAtLeast(1.0)
                        val xSpan=(maxX-minX).coerceAtLeast(1L)
                        val moss=Lab07Palette.Moss
                        val brass=Lab07Palette.Brass
                        Canvas(Modifier.fillMaxWidth().height(155.dp)){
                            val pad=10.dp.toPx()
                            fun position(p:Pair<Long,Double>):Offset{
                                val x=pad+(size.width-2*pad)*
                                    ((p.first-minX).toFloat()/xSpan)
                                val y=pad+(size.height-2*pad)*
                                    (1f-((p.second-minY)/ySpan).toFloat())
                                return Offset(x,y)
                            }
                            for((points,color) in listOf(
                                seriesLeft to moss,seriesRight to brass)){
                                points.zipWithNext{a,b->
                                    drawLine(color,position(a),position(b),2.dp.toPx())
                                }
                                points.forEach{drawCircle(color,3.5.dp.toPx(),position(it))}
                            }
                        }
                        Text("● A · зелёный     ● B · золотой",
                            style=MaterialTheme.typography.bodySmall)
                        for((name,points) in listOf("A" to seriesLeft,"B" to seriesRight)){
                            val first=points.first()
                            val last=points.last()
                            val days=(last.first-first.first)/86400000.0
                            Text(name+": "+first.second+" → "+last.second+
                                " · изменение "+(last.second-first.second)+
                                (if(days>0.0)
                                    " · среднее "+("%.2f".format(java.util.Locale.US,
                                        (last.second-first.second)/days))+"/сут"
                                else " · даты совпадают"),
                                style=MaterialTheme.typography.bodySmall)
                        }
                        Text("Средняя динамика рассчитана между первым и последним наблюдениями; разные даты, свет и условия ухода могут искажать сравнение.",
                            style=MaterialTheme.typography.bodySmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton={
            TextButton(onClick={onDismiss}){Text("Закрыть")}
        },
        dismissButton={
            if(leftId!=null)TextButton(onClick={onOpenItem(leftId)}){
                Text("Паспорт A")
            }
        }
    )
}
