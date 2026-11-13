package ru.dikoveriya.lab

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Direct operation picker from the bottom + menu.
 * Indexed SQL search and fixed-size pages instead of full in-memory catalog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun Lab10CulturePicker(
    db:LabDatabase,revision:Int,onDismiss:()->Unit,onSelect:(Long)->Unit,
    onScan:()->Unit
) {
    var query by remember{mutableStateOf("")}
    var page by remember{mutableIntStateOf(1)}
    var rows by remember{mutableStateOf(emptyList<Lab072Culture>())}
    var count by remember{mutableIntStateOf(0)}
    var loading by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf<String?>(null)}
    LaunchedEffect(query,page,revision) {
        loading=true
        try {
            val results=withContext(Dispatchers.IO) {
                val f=Lab072Query(mode="all",text=query)
                val total=db.countCultures072(f)
                val take=(page*40).coerceAtMost(total)
                val chunk=buildList{
                    for(offset in 0 until take step 40) {
                        addAll(db.culturesPage072(f,offset,minOf(40,take-offset)))
                    }
                }
                total to chunk
            }
            count=results.first
            rows=results.second
            error=null
        }catch(e:Exception){
            error=e.message?:"Ошибка поиска"
        }finally{loading=false}
    }
    ModalBottomSheet(onDismissRequest=onDismiss,
        containerColor=MaterialTheme.colorScheme.surface){
        Column(Modifier.fillMaxWidth().padding(start=18.dp,end=18.dp,bottom=12.dp),
            verticalArrangement=Arrangement.spacedBy(12.dp)){
            Text("Выбери контейнер",style=MaterialTheme.typography.headlineSmall)
            Text("Операция будет применена только к выбранному контейнеру.",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                query,{query=it;page=1},singleLine=true,
                modifier=Modifier.fillMaxWidth(),
                label={Text("Сорт или код LAB")},
                leadingIcon={Icon(Icons.Outlined.Search,null)}
            )
            OutlinedButton(onClick=onScan,
                modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){
                Icon(Icons.Outlined.QrCodeScanner,null)
                Spacer(Modifier.width(8.dp))
                Text("Найти по QR-коду")
            }
            Text("Найдено: "+count,style=MaterialTheme.typography.labelLarge)
            if(error!=null)Text(error.orEmpty(),color=MaterialTheme.colorScheme.error)
            if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
            if(!loading && rows.isEmpty())Text("Контейнеры не найдены")
            LazyColumn(Modifier.fillMaxWidth().heightIn(max=410.dp),
                verticalArrangement=Arrangement.spacedBy(7.dp)){
                items(rows,key={it.id}){culture->
                    OutlinedCard(onClick={onSelect(culture.id)},
                        modifier=Modifier.fillMaxWidth().heightIn(min=68.dp)){
                        Row(Modifier.padding(10.dp),verticalAlignment=Alignment.CenterVertically){
                            Icon(Icons.Outlined.Science,null,tint=Lab07Palette.Forest)
                            Spacer(Modifier.width(9.dp))
                            Column(Modifier.weight(1f)){
                                Text(culture.code,style=MaterialTheme.typography.labelLarge)
                                Text(culture.plant,style=MaterialTheme.typography.bodyMedium)
                                Text(culture.stage+" · "+culture.status,
                                    style=MaterialTheme.typography.bodySmall)
                            }
                            Icon(Icons.Outlined.ChevronRight,null)
                        }
                    }
                }
                if(rows.size<count)item{
                    OutlinedButton(onClick={page++},
                        modifier=Modifier.fillMaxWidth()){
                        Text("Показать ещё · "+(count-rows.size))
                    }
                }
            }
        }
    }
}
