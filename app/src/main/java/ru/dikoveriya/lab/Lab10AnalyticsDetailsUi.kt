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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class Lab10ReportLine(
    val name:String,val headline:String,val explanation:String
)

internal fun LabDatabase.analyticsBreakdown10(section:String):List<Lab10ReportLine>{
    val db=readableDatabase
    val sql=when(section){
        "Рост" -> """
            SELECT p.name,COUNT(DISTINCT c.id),
              SUM(CASE WHEN EXISTS(
                SELECT 1 FROM culture_measurements m
                WHERE m.culture_id=c.id AND m.shoots IS NOT NULL
              ) THEN 1 ELSE 0 END),
              SUM((SELECT m.shoots FROM culture_measurements m
                  WHERE m.culture_id=c.id AND m.shoots IS NOT NULL
                  ORDER BY m.observed_at DESC,m.id DESC LIMIT 1))
            FROM plants p JOIN cultures c ON c.plant_id=p.id
            WHERE c.status IN ('В культуре','На контроле','Карантин')
            GROUP BY p.id ORDER BY 3 DESC,p.name LIMIT 40
        """.trimIndent()
        "Среды" -> """
            SELECT r.name || ' · v' || r.version,
                COUNT(DISTINCT m.culture_id),
                COUNT(CASE WHEN m.record_kind='observation' THEN m.id END),
                COALESCE(SUM(CASE WHEN m.shoots IS NOT NULL AND m.id=(
                  SELECT x.id FROM culture_measurements x
                  WHERE x.culture_id=m.culture_id AND x.recipe_id=m.recipe_id
                    AND x.shoots IS NOT NULL
                  ORDER BY x.observed_at DESC,x.id DESC LIMIT 1
                ) THEN m.shoots ELSE 0 END),0)
            FROM recipes r LEFT JOIN culture_measurements m ON m.recipe_id=r.id
            GROUP BY r.id ORDER BY 3 DESC,r.name LIMIT 40
        """.trimIndent()
        "Потери" -> """
            SELECT p.name,COUNT(DISTINCT l.operation_id),
                   COALESCE(SUM(l.quantity),0),COUNT(DISTINCT l.culture_id)
            FROM operation_losses l
            JOIN lab_operations o ON o.id=l.operation_id AND o.status='Проведена'
            JOIN cultures c ON c.id=l.culture_id
            JOIN plants p ON p.id=c.plant_id
            GROUP BY p.id ORDER BY 3 DESC,p.name LIMIT 40
        """.trimIndent()
        "Адаптация" -> """
            SELECT p.name,COUNT(*),
              SUM(CASE WHEN c.status='В культуре' THEN 1 ELSE 0 END),
              SUM(CASE WHEN c.status IN ('На контроле','Карантин') THEN 1 ELSE 0 END)
            FROM plants p JOIN cultures c ON c.plant_id=p.id
            WHERE c.stage='Адаптация'
            GROUP BY p.id ORDER BY 2 DESC,p.name LIMIT 40
        """.trimIndent()
        else->return emptyList()
    }
    return db.rawQuery(sql,null).use{c->buildList {
        while(c.moveToNext()){
            val name=c.getString(0)
            val a=c.getInt(1)
            val b=if(c.isNull(2))0 else c.getInt(2)
            val d=if(c.isNull(3))0 else c.getInt(3)
            when(section){
                "Рост"->add(Lab10ReportLine(name,
                    if(b==0)"Недостаточно измерений" else "Побегов по последним замерам: "+d,
                    "Активных контейнеров: "+a+" · с замерами побегов: "+b+
                        ". Замеры других единиц не складываются с побегами."))
                "Среды"->add(Lab10ReportLine(name,
                    if(b==0)"Нет наблюдений" else "Побегов в последних замерах: "+d,
                    "Контейнеров с наблюдениями: "+a+" · записей: "+b+
                        ". Эффективность без сопоставимых групп не рассчитывается."))
                "Потери"->add(Lab10ReportLine(name,
                    "Подтверждённых потерь: "+b,
                    "Операций: "+a+" · затронутых контейнеров: "+d+
                        ". Суммируются только зарегистрированные потери; отменённые исключены."))
                "Адаптация"->add(Lab10ReportLine(name,
                    "На этапе адаптации: "+a,
                    "В работе: "+b+" · на контроле/карантине: "+d+
                        ". Выживаемость без исходной группы не рассчитывается."))
            }
        }
    }}
}

@Composable
internal fun Lab10AnalyticsDetails(db:LabDatabase,revision:Int) {
    var section by rememberSaveable{mutableStateOf("Рост")}
    val values by produceState<List<Lab10ReportLine>?>(initialValue=null,
        key1=section,key2=revision){
        value=withContext(Dispatchers.IO){db.analyticsBreakdown10(section)}
    }
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
        Text("Подробные показатели",
            style=MaterialTheme.typography.titleLarge)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement=Arrangement.spacedBy(7.dp)){
            listOf("Рост","Среды","Потери","Адаптация").forEach{option->
                FilterChip(selected=section==option,onClick={section=option},
                    label={Text(option)})
            }
        }
        Text(when(section){
            "Рост"->"Последние фактические наблюдения по сортам."
            "Среды"->"Наблюдения по рецептурам без выдуманных коэффициентов."
            "Потери"->"Операции с подтверждёнными потерями."
            else->"Контейнеры на этапе адаптации по зарегистрированным статусам."
        },style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
        when {
            values==null->LinearProgressIndicator(Modifier.fillMaxWidth())
            values!!.isEmpty()->Text("Недостаточно данных для этого раздела")
            else->values!!.forEach{report->
                OutlinedCard(modifier=Modifier.fillMaxWidth(),
                    shape=RoundedCornerShape(17.dp)){
                    Column(Modifier.padding(14.dp),
                        verticalArrangement=Arrangement.spacedBy(6.dp)){
                        Text(report.name,style=MaterialTheme.typography.titleMedium)
                        Text(report.headline,style=MaterialTheme.typography.titleMedium,
                            color=MaterialTheme.colorScheme.primary)
                        Text(report.explanation,style=MaterialTheme.typography.bodySmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
