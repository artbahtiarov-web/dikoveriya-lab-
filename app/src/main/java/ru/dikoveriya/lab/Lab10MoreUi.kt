package ru.dikoveriya.lab

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

private data class Lab10MoreAction(val title:String,val hint:String,val icon:ImageVector)
private val lab10MoreSections=listOf(
    "Работа" to listOf(
        Lab10MoreAction("Операции","Журнал действий и происхождения",Icons.Outlined.History),
        Lab10MoreAction("Задачи","Календарь, напоминания и планирование",Icons.Outlined.EventNote),
        Lab10MoreAction("Инциденты","Проблемы и корректирующие действия",Icons.Outlined.ReportProblem),
        Lab10MoreAction("Эксперименты","Сравнение результатов",Icons.Outlined.Science)
    ),
    "Ресурсы" to listOf(
        Lab10MoreAction("Склад","Запасы, покупки и история материалов",Icons.Outlined.Inventory2),
        Lab10MoreAction("Места хранения","Интерактивные стеллажи и позиции",Icons.Outlined.Warehouse),
        Lab10MoreAction("Оборудование","Многоразовое имущество и освещение",Icons.Outlined.Build)
    ),
    "Финансы" to listOf(
        Lab10MoreAction("Финансы","Деньги, стоимость и экономический результат",Icons.Outlined.AccountBalanceWallet),
        Lab10MoreAction("Продажи","Детальные продажи и история растений",Icons.Outlined.LocalOffer),
        Lab10MoreAction("Закупки","Покупки и цены поставщиков",Icons.Outlined.ShoppingCart)
    ),
    "Справочники" to listOf(
        Lab10MoreAction("Мои растения","Горшечные растения, черенки и маточники",Icons.Outlined.Spa),
        Lab10MoreAction("Питательные среды","Рецептуры, партии, компоненты",Icons.Outlined.Science),
        Lab10MoreAction("Группы контейнеров","Производственные партии",Icons.Outlined.Folder),
        Lab10MoreAction("Маточники","Источники культур in vitro",Icons.Outlined.Yard),
        Lab10MoreAction("Пользовательские справочники","Категории, поля и операции",Icons.Outlined.Tune),
        Lab10MoreAction("База знаний","Методики, протоколы и исследования",Icons.Outlined.MenuBook)
    ),
    "Система" to listOf(
        Lab10MoreAction("Настройки","Внешний вид и параметры",Icons.Outlined.Settings),
        Lab10MoreAction("Резервные копии","Сохранение, импорт и восстановление",Icons.Outlined.CloudDownload),
        Lab10MoreAction("О программе","Версия, справка и ограничения",Icons.Outlined.Info)
    )
)

@Composable
internal fun Lab10MoreMenu(onSection:(String)->Unit){
    Column(verticalArrangement=Arrangement.spacedBy(13.dp)){
        Lab07BrandHero("Ещё","Всё дополнительное — в понятных разделах.",
            "ДИКОВЕРИЯ LAB · ИНСТРУМЕНТЫ")
        lab10MoreSections.forEach{(heading,options)->
            Text(heading,style=MaterialTheme.typography.titleLarge,
                modifier=Modifier.padding(top=8.dp))
            options.forEach{item->
                OutlinedCard(onClick={onSection(item.title)},
                    modifier=Modifier.fillMaxWidth().heightIn(min=72.dp),
                    shape=RoundedCornerShape(17.dp),
                    colors=CardDefaults.outlinedCardColors(
                        containerColor=MaterialTheme.colorScheme.surface),
                    border=BorderStroke(1.dp,Lab07Palette.Brass.copy(alpha=.28f))){
                    Row(Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment=Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.spacedBy(12.dp)){
                        Surface(shape=RoundedCornerShape(12.dp),
                            color=Lab07Palette.Champagne){
                            Icon(item.icon,contentDescription=null,tint=Lab07Palette.Forest,
                                modifier=Modifier.padding(10.dp).size(23.dp))
                        }
                        Column(Modifier.weight(1f)){
                            Text(item.title,style=MaterialTheme.typography.titleMedium)
                            Text(item.hint,style=MaterialTheme.typography.bodySmall,
                                color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Outlined.ChevronRight,contentDescription=null,
                            tint=MaterialTheme.colorScheme.tertiary)
                    }
                }
            }
        }
    }
}
