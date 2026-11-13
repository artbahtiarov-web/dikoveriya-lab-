package ru.dikoveriya.lab

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal fun Lab11ItemCover(
    filename:String?,modifier:Modifier=Modifier,kind:String="Горшечное"
){
    val context=androidx.compose.ui.platform.LocalContext.current
    val bmp by produceState<Bitmap?>(null,filename){
        value=withContext(Dispatchers.IO){
            if(filename.isNullOrBlank() ||
                !Regex("[a-zA-Z0-9_-]+\\.(jpg|png|webp|heic)").matches(filename))null
            else{
                val file=File(context.filesDir,"culture_photos/"+filename)
                if(!file.isFile)null else runCatching{
                    val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
                    BitmapFactory.decodeFile(file.absolutePath,bounds)
                    BitmapFactory.decodeFile(file.absolutePath,
                        BitmapFactory.Options().apply{
                            inSampleSize=(maxOf(bounds.outWidth,bounds.outHeight)/520).coerceAtLeast(1)
                        })
                }.getOrNull()
            }
        }
    }
    Box(modifier.background(Lab07Palette.Forest),
        contentAlignment=Alignment.Center){
        if(bmp!=null)Image(bmp!!.asImageBitmap(),
            contentDescription="Фото растения",
            modifier=Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
        else Icon(if(kind=="Черенок")Icons.Outlined.Grass
                  else Icons.Outlined.Spa,contentDescription=null,
            tint=Lab07Palette.Brass,modifier=Modifier.size(42.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun Lab11ItemsScreen(
    db:LabDatabase,revision:Int,onBack:()->Unit,onNew:()->Unit,
    onOpen:(Long)->Unit,modifier:Modifier=Modifier
){
    var query by rememberSaveable{mutableStateOf("")}
    var kind by rememberSaveable{mutableStateOf("")}
    var favorites by rememberSaveable{mutableStateOf(false)}
    var filterOpen by remember{mutableStateOf(false)}
    var page by rememberSaveable{mutableIntStateOf(1)}
    var rows by remember{mutableStateOf(emptyList<Lab11PlantItem>())}
    var total by remember{mutableIntStateOf(0)}
    var loading by remember{mutableStateOf(true)}
    LaunchedEffect(revision,query,kind,favorites,page){
        loading=true
        try{
            val pair=withContext(Dispatchers.IO){
                val count=db.itemsCount11(query,kind,favorites)
                val upto=minOf(count,page*40)
                val items=buildList{
                    for(offset in 0 until upto step 40)
                        addAll(db.plantItems11(query,minOf(40,upto-offset),offset,
                            kind,favorites))
                }
                count to items
            }
            total=pair.first
            rows=pair.second
        }finally{loading=false}
    }
    LazyColumn(modifier=modifier,
        contentPadding=PaddingValues(horizontal=16.dp,vertical=12.dp),
        verticalArrangement=Arrangement.spacedBy(10.dp)){
        item{
            Lab07ScreenHeader("КОЛЛЕКЦИЯ / ЭКЗЕМПЛЯРЫ","Мои растения",
                "Горшечные растения, маточники и черенки с отдельной историей и QR.",onBack)
        }
        item{
            Row(verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.spacedBy(8.dp)){
                OutlinedTextField(query,{query=it;page=1},
                    modifier=Modifier.weight(1f),
                    singleLine=true,label={Text("Название, номер, место")},
                    leadingIcon={Icon(Icons.Outlined.Search,contentDescription=null)},
                    trailingIcon={
                        IconButton(onClick={filterOpen=true}){
                            Icon(Icons.Outlined.Tune,"Фильтр экземпляров")
                        }
                    })
                IconButton(onClick=onNew,
                    modifier=Modifier.size(52.dp).background(
                        MaterialTheme.colorScheme.surface,RoundedCornerShape(15.dp))){
                    Icon(Icons.Outlined.Add,"Добавить растение")
                }
            }
        }
        item{
            Text("Экземпляры · "+total,
                style=MaterialTheme.typography.titleMedium)
        }
        if(loading)item{LinearProgressIndicator(Modifier.fillMaxWidth())}
        if(!loading && rows.isEmpty())item{
            Column(Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment=Alignment.CenterHorizontally,
                verticalArrangement=Arrangement.spacedBy(10.dp)){
                Icon(Icons.Outlined.Spa,null,tint=Lab07Palette.Brass,
                    modifier=Modifier.size(48.dp))
                Text("Растений по выбранным условиям нет")
                Button(onClick=onNew){Text("Добавить экземпляр")}
            }
        }
        items(rows,key={it.id}){item->
            OutlinedCard(onClick={onOpen(item.id)},modifier=Modifier.fillMaxWidth(),
                shape=RoundedCornerShape(18.dp),
                colors=CardDefaults.outlinedCardColors(
                    containerColor=MaterialTheme.colorScheme.surface),
                border=BorderStroke(1.dp,Lab07Palette.Brass.copy(alpha=.25f))){
                Row(Modifier.fillMaxWidth().heightIn(min=92.dp),
                    verticalAlignment=Alignment.CenterVertically){
                    Lab11ItemCover(item.cover,
                        modifier=Modifier.size(width=92.dp,height=95.dp),
                        kind=item.kind)
                    Column(Modifier.weight(1f).padding(start=12.dp),
                        verticalArrangement=Arrangement.spacedBy(4.dp)){
                        Text(item.plantName,style=MaterialTheme.typography.titleMedium,
                            maxLines=2,overflow=TextOverflow.Ellipsis)
                        Text(item.code+" · "+item.kind,
                            style=MaterialTheme.typography.bodySmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(item.stage+" · "+item.status,
                            style=MaterialTheme.typography.bodySmall)
                        if(item.location.isNotBlank())
                            Text(item.location,maxLines=1,overflow=TextOverflow.Ellipsis,
                                style=MaterialTheme.typography.labelSmall)
                    }
                    if(item.favorite)Icon(Icons.Outlined.Star,
                        contentDescription="Избранное",tint=Lab07Palette.Brass)
                    Icon(Icons.Outlined.ChevronRight,contentDescription=null,
                        modifier=Modifier.padding(end=8.dp))
                }
            }
        }
        if(rows.size<total)item{
            OutlinedButton(onClick={page++},modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){
                Text("Показать ещё · "+(total-rows.size))
            }
        }
    }
    if(filterOpen){
        var draftKind by remember(filterOpen){mutableStateOf(kind)}
        var draftFav by remember(filterOpen){mutableStateOf(favorites)}
        ModalBottomSheet(onDismissRequest={filterOpen=false}){
            Column(Modifier.padding(20.dp),
                verticalArrangement=Arrangement.spacedBy(12.dp)){
                Text("Фильтры экземпляров",style=MaterialTheme.typography.headlineSmall)
                listOf("Все","Горшечное","Черенок","Маточник","Другое").forEach{option->
                    Row(Modifier.fillMaxWidth().clickable{
                        draftKind=if(option=="Все")"" else option
                    }.heightIn(min=44.dp),verticalAlignment=Alignment.CenterVertically){
                        RadioButton(draftKind==(if(option=="Все")"" else option),
                            onClick={draftKind=if(option=="Все")"" else option})
                        Text(option)
                    }
                }
                Row(verticalAlignment=Alignment.CenterVertically){
                    Checkbox(draftFav,{draftFav=it})
                    Text("Только избранное")
                }
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    OutlinedButton(onClick={draftKind="";draftFav=false},
                        modifier=Modifier.weight(1f)){Text("Сбросить")}
                    Button(onClick={
                        kind=draftKind;favorites=draftFav;page=1;filterOpen=false
                    },modifier=Modifier.weight(1f)){Text("Применить")}
                }
            }
        }
    }
}
