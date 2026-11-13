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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Lab10OperationEntry(
    val id:String,val kind:String,val date:Long,val status:String,
    val note:String,val affected:Int,val firstCultureId:Long?
)
data class Lab10OperationTarget(val id:Long,val code:String,val plant:String)

fun LabDatabase.operationTargets10(opId:String):List<Lab10OperationTarget> =
    readableDatabase.rawQuery("""
        SELECT DISTINCT c.id,c.code,p.name
        FROM operation_changes oc
        JOIN cultures c ON c.id=oc.culture_id
        JOIN plants p ON p.id=c.plant_id
        WHERE oc.operation_id=? ORDER BY c.code LIMIT 500
    """.trimIndent(),arrayOf(opId)).use{c->buildList{
        while(c.moveToNext())add(Lab10OperationTarget(c.getLong(0),
            c.getString(1),c.getString(2)))
    }}

fun LabDatabase.operationsCount10(search:String,kind:String,days:Int?):Int {
    val clause=operationWhere10(search,kind,days)
    return readableDatabase.rawQuery(
        "SELECT COUNT(*) FROM lab_operations o "+clause.first,clause.second
    ).use{c->if(c.moveToFirst())c.getInt(0) else 0}
}

fun LabDatabase.operationsPage10(search:String,kind:String,days:Int?,page:Int):List<Lab10OperationEntry>{
    val clause=operationWhere10(search,kind,days)
    val args=clause.second+arrayOf((page*40).coerceAtMost(800).toString())
    val sql="""
        SELECT o.id,o.kind,o.occurred_at,o.status,o.note,
               (SELECT COUNT(DISTINCT ch.culture_id)
                   FROM operation_changes ch WHERE ch.operation_id=o.id),
               (SELECT MIN(ch.culture_id)
                   FROM operation_changes ch WHERE ch.operation_id=o.id)
        FROM lab_operations o
    """.trimIndent()+clause.first+" ORDER BY o.occurred_at DESC,o.id DESC LIMIT ?"
    return readableDatabase.rawQuery(sql,args).use{c->buildList{
        while(c.moveToNext()){
            add(Lab10OperationEntry(c.getString(0),c.getString(1),c.getLong(2),
                c.getString(3),c.getString(4),c.getInt(5),
                if(c.isNull(6))null else c.getLong(6)))
        }
    }}
}

private fun operationWhere10(search:String,kind:String,days:Int?):Pair<String,Array<String>> {
    val parts=mutableListOf<String>()
    val args=mutableListOf<String>()
    if(search.isNotBlank()){
        val q="%"+search.trim().take(120)+"%"
        parts+="(o.kind LIKE ? OR o.note LIKE ? OR EXISTS ("+
            "SELECT 1 FROM operation_changes ch JOIN cultures c ON c.id=ch.culture_id "+
            "JOIN plants p ON p.id=c.plant_id "+
            "WHERE ch.operation_id=o.id AND (c.code LIKE ? OR p.name LIKE ?)))"
        repeat(4){args+=q}
    }
    if(kind!="Все"){
        parts+="o.kind=?"
        args+=kind
    }
    if(days!=null){
        parts+="o.occurred_at>=?"
        args+=(System.currentTimeMillis()-days*86400000L).toString()
    }
    return (if(parts.isEmpty())"" else "WHERE "+parts.joinToString(" AND ")) to args.toTypedArray()
}

private val operationKinds10=listOf(
    "Все","Разделение и пересадка","Объединение культур","Потери",
    "Исправление учёта","Авария","Отмена операции","Ошибочное создание",
    "Уточнение единицы учёта"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun Lab10OperationsScreen(
    db:LabDatabase,revision:Int,onBack:()->Unit,onOpenCulture:(Long)->Unit,
    modifier:Modifier=Modifier
){
    var search by rememberSaveable{mutableStateOf("")}
    var kind by rememberSaveable{mutableStateOf("Все")}
    var period by rememberSaveable{mutableStateOf("30 дней")}
    var filterOpen by remember{mutableStateOf(false)}
    var detail by remember{mutableStateOf<Lab10OperationEntry?>(null)}
    var page by rememberSaveable{mutableIntStateOf(1)}
    var data by remember{mutableStateOf(emptyList<Lab10OperationEntry>())}
    var total by remember{mutableIntStateOf(0)}
    var loading by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf("")}
    val days=when(period){"7 дней"->7;"30 дней"->30;"90 дней"->90;else->null}
    LaunchedEffect(search,kind,period,page,revision){
        loading=true
        try{
            val result=withContext(Dispatchers.IO){
                db.operationsCount10(search,kind,days) to
                    db.operationsPage10(search,kind,days,page)
            }
            total=result.first;data=result.second;error=""
        }catch(e:Exception){error=e.message?:"Ошибка загрузки операций"}
        finally{loading=false}
    }
    Column(modifier){
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=10.dp),
            verticalAlignment=Alignment.CenterVertically){
            IconButton(onClick=onBack){Icon(Icons.Outlined.ArrowBack,"Назад")}
            Text("Операции",style=MaterialTheme.typography.headlineSmall,
                modifier=Modifier.weight(1f))
            Icon(Icons.Outlined.History,null,tint=MaterialTheme.colorScheme.tertiary)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),
            horizontalArrangement=Arrangement.spacedBy(8.dp)){
            OutlinedTextField(search,{search=it;page=1},
                modifier=Modifier.weight(1f),singleLine=true,
                placeholder={Text("Контейнер, сорт или действие")},
                leadingIcon={Icon(Icons.Outlined.Search,null)})
            IconButton(onClick={filterOpen=true},
                modifier=Modifier.size(52.dp)
                    .background(MaterialTheme.colorScheme.surface,RoundedCornerShape(14.dp))){
                Icon(Icons.Outlined.Tune,"Фильтры операций")
            }
        }
        Text("Найдено: "+total+" · "+period+" · "+kind,
            modifier=Modifier.padding(horizontal=16.dp,vertical=9.dp),
            style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error,
            modifier=Modifier.padding(16.dp))
        if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(data.isEmpty()&&!loading){
            Column(Modifier.fillMaxWidth().padding(30.dp),
                horizontalAlignment=Alignment.CenterHorizontally){
                Icon(Icons.Outlined.History,null,modifier=Modifier.size(46.dp))
                Text("По выбранным условиям операций нет")
            }
        }else LazyColumn(modifier=Modifier.fillMaxSize(),
            contentPadding=PaddingValues(16.dp),
            verticalArrangement=Arrangement.spacedBy(9.dp)){
            items(data,key={it.id}){op->
                OutlinedCard(onClick={detail=op},
                    modifier=Modifier.fillMaxWidth().heightIn(min=93.dp),
                    shape=RoundedCornerShape(17.dp)){
                    Row(Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment=Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.spacedBy(11.dp)){
                        Icon(when {
                            op.kind.contains("Потери",true)->Icons.Outlined.RemoveCircleOutline
                            op.kind.contains("Пересад",true)->Icons.Outlined.SwapHoriz
                            op.kind.contains("Авар",true)->Icons.Outlined.WarningAmber
                            else->Icons.Outlined.History
                        },null,tint=MaterialTheme.colorScheme.primary,
                            modifier=Modifier.size(28.dp))
                        Column(Modifier.weight(1f),
                            verticalArrangement=Arrangement.spacedBy(4.dp)){
                            Text(op.kind,style=MaterialTheme.typography.titleMedium)
                            Text(formatDate(op.date)+" · "+op.status,
                                style=MaterialTheme.typography.bodySmall)
                            Text(op.note,maxLines=2,overflow=TextOverflow.Ellipsis,
                                style=MaterialTheme.typography.bodySmall)
                            Text("Контейнеров в операции: "+op.affected,
                                style=MaterialTheme.typography.labelSmall)
                        }
                        Icon(Icons.Outlined.ChevronRight,null)
                    }
                }
            }
            if(data.size<total)item{
                OutlinedButton(onClick={page++},modifier=Modifier.fillMaxWidth()){
                    Text("Показать ещё · "+(total-data.size))
                }
            }
        }
    }
    if(filterOpen){
        var draftKind by remember(filterOpen){mutableStateOf(kind)}
        var draftPeriod by remember(filterOpen){mutableStateOf(period)}
        ModalBottomSheet(onDismissRequest={filterOpen=false}){
            Column(Modifier.verticalScroll(rememberScrollState())
                .padding(horizontal=20.dp,vertical=12.dp),
                verticalArrangement=Arrangement.spacedBy(13.dp)){
                Text("Фильтры операций",style=MaterialTheme.typography.headlineSmall)
                PickOne("Вид операции",operationKinds10,draftKind,{draftKind=it})
                PickOne("Период",listOf("7 дней","30 дней","90 дней","Всё время"),
                    draftPeriod,{draftPeriod=it})
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    OutlinedButton(onClick={draftKind="Все";draftPeriod="30 дней"},
                        modifier=Modifier.weight(1f)){Text("Сбросить")}
                    Button(onClick={kind=draftKind;period=draftPeriod;page=1;filterOpen=false},
                        modifier=Modifier.weight(1f)){Text("Применить")}
                }
                Spacer(Modifier.height(14.dp))
            }
        }
    }
    detail?.let{op->
        val linked=remember(op.id,revision){db.operationTargets10(op.id)}
        ModalBottomSheet(onDismissRequest={detail=null}){
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal=20.dp,vertical=10.dp),
                verticalArrangement=Arrangement.spacedBy(12.dp)){
                Text(op.kind,style=MaterialTheme.typography.headlineSmall)
                Text(formatDate(op.date)+" · "+op.status)
                Text(op.note)
                Text("Затронутые контейнеры",style=MaterialTheme.typography.titleMedium)
                if(linked.isEmpty())Text("Связанных контейнеров в журнале нет")
                linked.forEach{target->
                    OutlinedCard(onClick={detail=null;onOpenCulture(target.id)},
                        modifier=Modifier.fillMaxWidth()){
                        Row(Modifier.padding(12.dp),
                            verticalAlignment=Alignment.CenterVertically){
                            Icon(Icons.Outlined.Science,null)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)){
                                Text(target.code,style=MaterialTheme.typography.titleMedium)
                                Text(target.plant,style=MaterialTheme.typography.bodySmall)
                            }
                            Icon(Icons.Outlined.ChevronRight,null)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}
