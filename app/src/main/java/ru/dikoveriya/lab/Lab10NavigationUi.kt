package ru.dikoveriya.lab

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
            label={Text("Добавить",maxLines=1)},
            alwaysShowLabel=true,
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
        label={Text(name,maxLines=1)},
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
    onOpenTasks:()->Unit
) {
    ModalBottomSheet(
        onDismissRequest=onDismiss,
        containerColor=MaterialTheme.colorScheme.surface,
        shape=RoundedCornerShape(topStart=24.dp,topEnd=24.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom=22.dp)) {
            Text("Быстрые действия",style=MaterialTheme.typography.headlineSmall,
                color=Lab07Palette.Forest,modifier=Modifier.padding(horizontal=20.dp,vertical=8.dp))
            Text("Выберите, что нужно сделать",style=MaterialTheme.typography.bodyMedium,
                color=MaterialTheme.colorScheme.onSurfaceVariant,
                modifier=Modifier.padding(start=20.dp,end=20.dp,bottom=12.dp))
            Lab10QuickRow("Добавить сорт","Новая запись в коллекции",Icons.Outlined.LocalFlorist,onNewPlant)
            Lab10QuickRow("Новый контейнер","Создать культуру из существующего сорта",Icons.Outlined.AddBox,onNewCulture)
            Lab10QuickRow("Сканировать QR","Открыть контейнер по этикетке",Icons.Outlined.QrCodeScanner,onScan)
            Lab10QuickRow("Операция с контейнером","Сначала выберите контейнер",Icons.Outlined.SwapHoriz,onChooseOperation)
            Lab10QuickRow("Задачи","Открыть список и создать задачу",Icons.Outlined.EventNote,onOpenTasks)
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
