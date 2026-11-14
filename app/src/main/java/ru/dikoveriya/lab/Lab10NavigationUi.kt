package ru.dikoveriya.lab

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp

/**
 * Approved 1.0 navigation: Home / Collection / Quick action / Analytics / More.
 * The plus is deliberately an action, not another root route.
 */
@Composable
internal fun Lab10BottomNav(
    selectedTab:String,
    onSelect:(String)->Unit,
    onCreate:()->Unit
) {
    NavigationBar(containerColor=Lab07Palette.DeepForest,tonalElevation=0.dp) {
        listOf(
            "Главная" to Icons.Outlined.Home,
            "Коллекция" to Icons.Outlined.LocalFlorist
        ).forEach { (name,icon) ->
            Lab10Tab(name,icon,selectedTab==name,{onSelect(name)})
        }
        NavigationBarItem(
            selected=false,
            onClick=onCreate,
            icon={
                Box(
                    modifier=Modifier.size(48.dp).background(Lab07Palette.Brass,CircleShape),
                    contentAlignment=Alignment.Center
                ){
                    Icon(Icons.Outlined.Add,contentDescription="Открыть быстрые действия",
                        tint=Lab07Palette.DeepForest,modifier=Modifier.size(27.dp))
                }
            },
            // Central + is a primary action, not a fifth content destination.
            label=null,
            alwaysShowLabel=false,
            colors=NavigationBarItemDefaults.colors(
                unselectedIconColor=Lab07Palette.Brass,
                unselectedTextColor=Lab07Palette.Champagne
            )
        )
        listOf(
            "Аналитика" to Icons.Outlined.BarChart,
            "Ещё" to Icons.Outlined.Menu
        ).forEach { (name,icon) ->
            Lab10Tab(name,icon,selectedTab==name,{onSelect(name)})
        }
    }
}

@Composable
private fun RowScope.Lab10Tab(
    name:String,
    icon:ImageVector,
    selected:Boolean,
    onClick:()->Unit
) {
    NavigationBarItem(
        selected=selected,
        onClick=onClick,
        icon={Icon(icon,contentDescription=name)},
        label={
            // Keep every Russian tab name on one line even at enlarged system scale.
            Text(name,maxLines=1,softWrap=false,
                style=MaterialTheme.typography.labelSmall.copy(fontSize=8.sp,
                    lineHeight=10.sp),
                overflow=TextOverflow.Clip)
        },
        colors=NavigationBarItemDefaults.colors(
            selectedIconColor=Lab07Palette.Brass,
            selectedTextColor=Lab07Palette.Champagne,
            indicatorColor=Lab07Palette.Forest,
            unselectedIconColor=Color(0xFFCCD4C8),
            unselectedTextColor=Color(0xFFCCD4C8)
        )
    )
}

/** Every row opens a working legacy flow or a real route. No decorative actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun Lab10QuickActions(
    onDismiss:()->Unit,
    onNewPlant:()->Unit,
    onNewCulture:()->Unit,
    onScan:()->Unit,
    onChooseOperation:()->Unit,
    onOpenTasks:()->Unit,
    onNewItem:()->Unit={},
    onBulkCulture:()->Unit={},
    onCutting:()->Unit={},
    onFinance:()->Unit={}
) {
    ModalBottomSheet(
        onDismissRequest=onDismiss,
        containerColor=MaterialTheme.colorScheme.surface,
        shape=RoundedCornerShape(topStart=24.dp,topEnd=24.dp)
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(androidx.compose.foundation.rememberScrollState())
            .padding(bottom=22.dp)) {
            Text("Быстрые действия",style=MaterialTheme.typography.headlineSmall,
                color=Lab07Palette.Forest,modifier=Modifier.padding(horizontal=20.dp,vertical=8.dp))
            Text("Выберите, что нужно сделать",style=MaterialTheme.typography.bodyMedium,
                color=MaterialTheme.colorScheme.onSurfaceVariant,
                modifier=Modifier.padding(start=20.dp,end=20.dp,bottom=12.dp))
            Lab10QuickRow("Растение","Горшечное, маточник или черенок",Icons.Outlined.Spa,onNewItem)
            Lab10QuickRow("Контейнеры","Один контейнер или производственная партия",Icons.Outlined.AddBox,onBulkCulture)
            Lab10QuickRow("Черенок","Выбрать родительское растение",Icons.Outlined.Grass,onCutting)
            Lab10QuickRow("Операция","Действие с существующим объектом",Icons.Outlined.SwapHoriz,onChooseOperation)
            Lab10QuickRow("Задача","Календарь и планирование",Icons.Outlined.EventNote,onOpenTasks)
            Lab10QuickRow("Покупка / расход","Движение денег и материалы",Icons.Outlined.ReceiptLong,onFinance)
            Lab10QuickRow("Сканировать QR","Быстро открыть объект",Icons.Outlined.QrCodeScanner,onScan)
        }
    }
}

@Composable
private fun Lab10QuickRow(
    title:String,
    subtitle:String,
    icon:ImageVector,
    onClick:()->Unit
) {
    ListItem(
        modifier=Modifier.fillMaxWidth().clickable(onClick=onClick),
        headlineContent={Text(title)},
        supportingContent={Text(subtitle)},
        leadingContent={Icon(icon,contentDescription=null,tint=Lab07Palette.Forest)},
        trailingContent={Icon(Icons.Outlined.ChevronRight,contentDescription=null)},
        colors=ListItemDefaults.colors(containerColor=Color.Transparent)
    )
}
