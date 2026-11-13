package ru.dikoveriya.lab

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import kotlin.math.max

/** Genuine brand colours from the user's Arch of Discovery board. */
@Composable
fun Lab07BrandHero(title:String, subtitle:String, overline:String="ДИКОВЕРИЯ  /  LAB") {
    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(
                Lab07Palette.DeepForest, Lab07Palette.Forest, Color(0xFF315340))))
            .border(1.dp,Lab07Palette.Brass.copy(alpha=0.45f),RoundedCornerShape(24.dp))
    ) {
        Canvas(Modifier.matchParentSize()) {
            val sw=size.width
            val sh=size.height
            drawArc(Lab07Palette.Brass.copy(alpha=.21f),180f,180f,false,
                topLeft=Offset(sw*.62f,sh*.05f),
                size=Size(sw*.31f,sh*1.25f),style=Stroke(width=2.dp.toPx()))
            drawLine(Lab07Palette.Brass.copy(alpha=.25f),
                Offset(sw*.56f,sh*.93f),Offset(sw*.95f,sh*.93f),1.dp.toPx())
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=22.dp),
            horizontalArrangement=Arrangement.spacedBy(14.dp),
            verticalAlignment=Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(9.dp)) {
                Text(overline,color=Lab07Palette.Brass,
                    style=MaterialTheme.typography.labelLarge)
                Text(title,color=Lab07Palette.Champagne,
                    style=MaterialTheme.typography.headlineMedium)
                Text(subtitle,color=Color(0xFFE4DFD0),
                    style=MaterialTheme.typography.bodyMedium)
            }
            Icon(painterResource(R.drawable.ic_dikoveriya_logo),
                contentDescription="Арка открытий — символ Диковерии",
                tint=Color.Unspecified,modifier=Modifier.size(76.dp)
                    .clip(RoundedCornerShape(12.dp)))
        }
    }
}

@Composable
fun Lab07Metric(title:String,value:String,qualifier:String,accent:Color,
                modifier:Modifier=Modifier,onClick:()->Unit) {
    Card(onClick=onClick,modifier=modifier.heightIn(min=112.dp),
        shape=RoundedCornerShape(18.dp),
        colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),
        border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.fillMaxWidth().padding(15.dp),
            verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(7.dp).clip(RoundedCornerShape(5.dp)).background(accent))
                Text(title,color=MaterialTheme.colorScheme.onSurfaceVariant,
                    style=MaterialTheme.typography.bodySmall,maxLines=2)
            Icon(Icons.Outlined.ChevronRight,contentDescription="Открыть: $title",
                tint=Lab07Palette.InkBrass,modifier=Modifier.size(16.dp))
            }
            Text(value,style=MaterialTheme.typography.headlineMedium,
                color=MaterialTheme.colorScheme.onSurface)
            Text(qualifier,style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=2)
        }
    }
}

@Composable
fun Lab07QuickAction(text:String,icon:@Composable ()->Unit,primary:Boolean=false,
                     modifier:Modifier=Modifier,onClick:()->Unit) {
    val bg=if(primary) Lab07Palette.Forest else MaterialTheme.colorScheme.surface
    val fg=if(primary) Lab07Palette.Champagne else Lab07Palette.Forest
    Card(onClick=onClick,modifier=modifier.heightIn(min=58.dp),
        shape=RoundedCornerShape(14.dp),
        colors=CardDefaults.cardColors(containerColor=bg),
        border=BorderStroke(1.dp,if(primary) Lab07Palette.Forest else MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=13.dp),
            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)) {
            CompositionLocalProvider(LocalContentColor provides fg) { icon() }
            Text(text,style=MaterialTheme.typography.labelLarge,
                color=fg,modifier=Modifier.weight(1f),maxLines=3)
        }
    }
}

@Composable
fun Lab07Dashboard(
    plants:List<PlantRecord>,cultures:List<CultureRecord>,tasks:List<TaskRecord>,
    onCollection:()->Unit,onContainer:(Long)->Unit,
    onNewContainer:()->Unit,onNewPlant:()->Unit,onMediums:()->Unit,onScan:()->Unit,
    counts:Lab072Counts?=null,onMetric:(String)->Unit={}
) {
    val active=cultures.filter{it.status in setOf(LabTerms.IN_CULTURE,LabTerms.CONTROL,LabTerms.QUARANTINE)}
    val inVitro=counts?.tubes ?: active.count{it.stage==LabTerms.IN_VITRO}
    val controls=counts?.attention ?: active.count{it.status==LabTerms.CONTROL || it.status==LabTerms.QUARANTINE}
    val growth=counts?.growing ?: active.count{it.stage==LabTerms.ADAPTATION || it.stage==LabTerms.GROWING}
    val due=counts?.tasksDue ?: tasks.count{!it.done && it.dueAt<=System.currentTimeMillis()}
    Column(verticalArrangement=Arrangement.spacedBy(13.dp)) {
        SectionTitle("Состояние лаборатории")
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Lab07Metric("В пробирках",inVitro.toString(),"контейнеров",
                Lab07Palette.Forest,Modifier.weight(1f)){onMetric("vitro")}
            Lab07Metric("Требуют внимания",controls.toString(),"контейнеров",
                Lab07Palette.Danger,Modifier.weight(1f)){onMetric("attention")}
        }
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Lab07Metric("Растут",growth.toString(),"адаптация / доращивание",
                Lab07Palette.Moss,Modifier.weight(1f)){onMetric("growth")}
            Lab07Metric("Задачи",due.toString(),"к выполнению",
                Lab07Palette.InkBrass,Modifier.weight(1f)){onMetric("tasks")}
        }
        SectionTitle("Быстрые действия")
        Lab07QuickAction("Новый контейнер",
            {Icon(Icons.Outlined.AddCircleOutline,null)},true,
            onClick=onNewContainer)
        Lab07QuickAction("Сканировать контейнер",
            {Icon(Icons.Outlined.QrCodeScanner,null)},onClick=onScan)
        Text("Растения — во вкладке «Растения», рецептуры — во вкладке «Среды».",
            style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun Lab07ScreenHeader(kicker:String,title:String,subtitle:String="",onBack:()->Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(11.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(7.dp)) {
            IconButton(onClick=onBack,modifier=Modifier.sizeIn(minWidth=48.dp,minHeight=48.dp)) {
                Icon(Icons.Outlined.ArrowBack,contentDescription="Назад на один уровень",
                    tint=MaterialTheme.colorScheme.primary)
            }
            Text(kicker.uppercase(),color=Lab07Palette.InkBrass,
                style=MaterialTheme.typography.labelLarge,maxLines=2,
                modifier=Modifier.weight(1f))
        }
        Text(title,style=MaterialTheme.typography.headlineMedium)
        if(subtitle.isNotBlank()) Text(subtitle,
            color=MaterialTheme.colorScheme.onSurfaceVariant,
            style=MaterialTheme.typography.bodyMedium)
        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
fun Lab07CultureRow(culture:CultureRecord,onClick:()->Unit) {
    OutlinedCard(onClick=onClick,modifier=Modifier.fillMaxWidth().heightIn(min=84.dp),
        shape=RoundedCornerShape(17.dp),
        colors=CardDefaults.outlinedCardColors(containerColor=MaterialTheme.colorScheme.surface),
        border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.fillMaxWidth().padding(15.dp),
            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                .background(Lab07Palette.Forest),contentAlignment=Alignment.Center) {
                Icon(Icons.Outlined.Spa,null,tint=Lab07Palette.Brass)
            }
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(culture.code,style=MaterialTheme.typography.titleMedium,color=Lab07Palette.Forest)
                Text(culture.plantName,style=MaterialTheme.typography.bodyMedium,
                    maxLines=2,overflow=TextOverflow.Ellipsis)
                Text("${culture.status} · ${culture.quantity} (${culture.quantityKind})",
                    color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Outlined.ChevronRight,"Открыть контейнер",tint=Lab07Palette.InkBrass)
        }
    }
}

@Composable
fun Lab07ActionTile(
    title:String,subtitle:String,color:Color,icon:@Composable ()->Unit,
    modifier:Modifier=Modifier,onClick:()->Unit
) {
    Card(onClick=onClick,modifier=modifier.heightIn(min=127.dp),
        shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=color),
        border=BorderStroke(1.dp,Lab07Palette.Brass.copy(alpha=0.36f))) {
        Column(Modifier.fillMaxWidth().padding(15.dp),
            verticalArrangement=Arrangement.spacedBy(8.dp)) {
            CompositionLocalProvider(LocalContentColor provides Lab07Palette.Brass) { icon() }
            Text(title,style=MaterialTheme.typography.titleMedium,color=Lab07Palette.Champagne)
            Text(subtitle,style=MaterialTheme.typography.bodySmall,
                color=Color(0xFFE7E3D9),maxLines=3)
        }
    }
}

/** Direct plot of observed values only: missing samples and measured zero remain distinct. */
@Composable
fun Lab07ObservationPlot(samples:List<GrowthSample>,field:String) {
    val points=samples.sortedWith(compareBy<GrowthSample>{it.date}.thenBy{it.id}).mapNotNull {sample->
        val value=when(field) {
            "Побеги"->sample.shoots
            "Растения"->sample.plants
            "Экспланты"->sample.explants
            else->sample.contaminated
        }
        value?.let{sample.date to it}
    }
    if(points.size<2) {
        Text("Для графика нужны минимум два измерения одного показателя. Нулевое измерение учитывается, пустое — нет.",
            style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val description="$field: "+points.joinToString { "${formatDate(it.first)} — ${it.second}" }
    val minY=points.minOf{it.second}.toFloat()
    val maxY=points.maxOf{it.second}.toFloat()
    val range=max(1f,maxY-minY)
    val minT=points.first().first
    val maxT=points.last().first
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.fillMaxWidth().height(166.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(Color(0xFFF0ECDD))
            .semantics { contentDescription=description }) {
            val left=18.dp.toPx();val top=16.dp.toPx()
            val width=size.width-left-18.dp.toPx()
            val height=size.height-top-18.dp.toPx()
            val timeline=(maxT-minT).coerceAtLeast(1L).toFloat()
            val coords=points.mapIndexed{index,p->
                val x=if(maxT==minT) left+width*index/(points.size-1)
                      else left+width*((p.first-minT).toFloat()/timeline)
                val y=top+height*(1f-(p.second-minY)/range)
                Offset(x,y)
            }
            for(i in 0..3) {
                val y=top+height*i/3
                drawLine(Lab07Palette.Forest.copy(alpha=.12f),Offset(left,y),Offset(left+width,y),1.dp.toPx())
            }
            coords.zipWithNext().forEach{(a,b)->
                drawLine(Lab07Palette.Forest,a,b,2.5.dp.toPx())
            }
            coords.forEach{drawCircle(Lab07Palette.InkBrass,4.dp.toPx(),it)}
        }
        Text("${formatDate(minT)}: ${points.first().second}  ·  ${formatDate(maxT)}: ${points.last().second}",
            style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Наблюдение не равно производству. Для оценки эффективности нужны сравнимые исходные и конечные единицы.",
            style=MaterialTheme.typography.bodySmall)
    }
}

/** Static information panel: unlike a button this must not suggest a tap action. */
@Composable
fun Lab07ReadOnlyCard(title:String,caption:String) {
    OutlinedCard(modifier=Modifier.fillMaxWidth(),
        shape=RoundedCornerShape(16.dp),
        border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),
        colors=CardDefaults.outlinedCardColors(containerColor=MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
            Text(title,style=MaterialTheme.typography.titleMedium,color=Lab07Palette.Forest)
            Text(caption,style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
