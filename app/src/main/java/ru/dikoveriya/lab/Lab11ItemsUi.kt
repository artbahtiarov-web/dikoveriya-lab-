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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

@Composable
internal fun Lab11ItemCover(
    filename:String?,modifier:Modifier=Modifier,kind:String="Горшечное",
    zoom:Float=1f,panX:Float=0f,panY:Float=0f
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
    Box(modifier.clipToBounds().background(Lab07Palette.Forest),
        contentAlignment=Alignment.Center){
        if(bmp!=null)Image(bmp!!.asImageBitmap(),
            contentDescription="Фото растения",
            modifier=Modifier.fillMaxSize().graphicsLayer {
                scaleX=zoom;scaleY=zoom
                translationX=panX*size.width
                translationY=panY*size.height
            },contentScale=ContentScale.Crop)
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
    var gridMode by rememberSaveable{mutableStateOf(false)}
    var stage by rememberSaveable{mutableStateOf("")}
    var status by rememberSaveable{mutableStateOf("")}
    var filterOpen by remember{mutableStateOf(false)}
    var page by rememberSaveable{mutableIntStateOf(1)}
    var rows by remember{mutableStateOf(emptyList<Lab11PlantItem>())}
    var total by remember{mutableIntStateOf(0)}
    var loading by remember{mutableStateOf(true)}
    val stageOptions=remember(revision){db.itemFilterValues11("stage")}
    val statusOptions=remember(revision){db.itemFilterValues11("status")}
    LaunchedEffect(revision,query,kind,favorites,stage,status,page){
        loading=true
        try{
            val pair=withContext(Dispatchers.IO){
                val count=db.itemsCount11(query,kind,favorites,stage,status)
                val upto=minOf(count,page*40)
                val items=buildList{
                    for(offset in 0 until upto step 40)
                        addAll(db.plantItems11(query,minOf(40,upto-offset),offset,
                            kind,favorites,stage,status))
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
            Row(verticalAlignment=Alignment.CenterVertically){
                Text("Экземпляры · "+total,
                    style=MaterialTheme.typography.titleMedium,
                    modifier=Modifier.weight(1f))
                IconButton(onClick={gridMode=!gridMode}){
                    Icon(if(gridMode)Icons.Outlined.ViewList else Icons.Outlined.GridView,
                        contentDescription=if(gridMode)"Показать списком" else "Показать плиткой")
                }
            }
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
        if(gridMode){
            items(rows.chunked(2),key={it.first().id}){pair->
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(9.dp)){
                    pair.forEach{plant->
                        Lab11ItemTile(plant,true,onClick={onOpen(plant.id)},
                            modifier=Modifier.weight(1f))
                    }
                    if(pair.size<2)Spacer(Modifier.weight(1f))
                }
            }
        }else{
            items(rows,key={it.id}){plant->
                Lab11ItemTile(plant,false,onClick={onOpen(plant.id)},
                    modifier=Modifier.fillMaxWidth())
            }
        }
        if(rows.size<total)item{
            OutlinedButton(onClick={page++},modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){
                Text("Показать ещё · "+(total-rows.size))
            }
        }
    }
    if(filterOpen){
        val saved=remember(revision){db.savedViews072("items")}
        var presetName by remember(filterOpen){mutableStateOf("")}
        var draftSearch by remember(filterOpen){mutableStateOf(query)}
        var draftKind by remember(filterOpen){mutableStateOf(kind)}
        var draftFav by remember(filterOpen){mutableStateOf(favorites)}
        var draftStage by remember(filterOpen){mutableStateOf(stage)}
        var draftStatus by remember(filterOpen){mutableStateOf(status)}
        ModalBottomSheet(onDismissRequest={filterOpen=false}){
            Column(Modifier.padding(20.dp),
                verticalArrangement=Arrangement.spacedBy(12.dp)){
                Text("Фильтры экземпляров",style=MaterialTheme.typography.headlineSmall)
                if(saved.isNotEmpty())PickOne("Сохранённый набор",
                    listOf("Выбрать")+saved.map{it.name},"Выбрать",
                    {name->
                        val v=saved.firstOrNull{it.name==name}
                        if(v!=null)try{
                            val o=JSONObject(v.config)
                            draftSearch=o.optString("search","")
                            draftKind=o.optString("kind","")
                            draftFav=o.optBoolean("favorite",false)
                            draftStage=o.optString("stage","")
                            draftStatus=o.optString("status","")
                        }catch(e:Exception){onError("Некорректный сохранённый фильтр")}
                    })
                OutlinedTextField(draftSearch,{draftSearch=it},
                    modifier=Modifier.fillMaxWidth(),singleLine=true,
                    label={Text("Текст поиска")})
                listOf("Все","Горшечное","Черенок","Маточник","Другое").forEach{option->
                    Row(Modifier.fillMaxWidth().clickable{
                        draftKind=if(option=="Все")"" else option
                    }.heightIn(min=44.dp),verticalAlignment=Alignment.CenterVertically){
                        RadioButton(draftKind==(if(option=="Все")"" else option),
                            onClick={draftKind=if(option=="Все")"" else option})
                        Text(option)
                    }
                }
                PickOne("Этап",listOf("Все")+stageOptions,
                    draftStage.ifBlank{"Все"},{draftStage=if(it=="Все")"" else it})
                PickOne("Статус",listOf("Все")+statusOptions,
                    draftStatus.ifBlank{"Все"},{draftStatus=if(it=="Все")"" else it})
                Row(verticalAlignment=Alignment.CenterVertically){
                    Checkbox(draftFav,{draftFav=it})
                    Text("Только избранное")
                }
                OutlinedTextField(presetName,{presetName=it},
                    label={Text("Название нового набора фильтров")},
                    modifier=Modifier.fillMaxWidth(),singleLine=true)
                OutlinedButton(enabled=presetName.trim().length in 2..50,
                    onClick={
                        try{
                            val o=JSONObject().put("search",draftSearch)
                                .put("kind",draftKind).put("favorite",draftFav)
                                .put("stage",draftStage).put("status",draftStatus)
                            db.saveView072("items",presetName.trim(),o.toString())
                            onChanged()
                        }catch(e:Exception){onError(e.message?:"Не удалось сохранить набор")}
                    },modifier=Modifier.fillMaxWidth()){
                    Text("Сохранить набор")
                }
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    OutlinedButton(onClick={draftSearch="";draftKind="";draftFav=false;draftStage="";draftStatus=""},
                        modifier=Modifier.weight(1f)){Text("Сбросить")}
                    Button(onClick={
                        query=draftSearch;kind=draftKind;favorites=draftFav;stage=draftStage;status=draftStatus
                        page=1;filterOpen=false
                    },modifier=Modifier.weight(1f)){Text("Применить")}
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Lab11ItemTile(
    item:Lab11PlantItem,gridMode:Boolean,onClick:()->Unit,
    modifier:Modifier=Modifier
){
    OutlinedCard(onClick=onClick,modifier=modifier,
        shape=RoundedCornerShape(18.dp),
        colors=CardDefaults.outlinedCardColors(
            containerColor=MaterialTheme.colorScheme.surface),
        border=BorderStroke(1.dp,Lab07Palette.Brass.copy(alpha=.25f))){
        if(gridMode){
            Column(Modifier.fillMaxWidth(),
                verticalArrangement=Arrangement.spacedBy(6.dp)){
                Lab11ItemCover(item.cover,
                    modifier=Modifier.fillMaxWidth().height(128.dp),kind=item.kind,
                    zoom=item.coverZoom,panX=item.coverPanX,panY=item.coverPanY)
                Column(Modifier.fillMaxWidth().padding(10.dp),
                    verticalArrangement=Arrangement.spacedBy(5.dp)){
                    Text(item.plantName,style=MaterialTheme.typography.titleSmall,
                        maxLines=2,overflow=TextOverflow.Ellipsis)
                    Text(item.code,style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(item.kind+" · "+item.stage,
                        style=MaterialTheme.typography.labelSmall,
                        maxLines=2,overflow=TextOverflow.Ellipsis)
                    if(item.favorite)
                        Icon(Icons.Outlined.Star,contentDescription="Избранное",
                            tint=Lab07Palette.Brass,modifier=Modifier.size(15.dp))
                }
            }
        }else{
            Row(Modifier.fillMaxWidth().heightIn(min=92.dp),
                verticalAlignment=Alignment.CenterVertically){
                Lab11ItemCover(item.cover,
                    modifier=Modifier.size(width=92.dp,height=95.dp),
                    kind=item.kind,zoom=item.coverZoom,
                    panX=item.coverPanX,panY=item.coverPanY)
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
                        Text(item.location,maxLines=1,
                            overflow=TextOverflow.Ellipsis,
                            style=MaterialTheme.typography.labelSmall)
                }
                if(item.favorite)Icon(Icons.Outlined.Star,
                    contentDescription="Избранное",tint=Lab07Palette.Brass)
                Icon(Icons.Outlined.ChevronRight,contentDescription=null,
                    modifier=Modifier.padding(end=8.dp))
            }
        }
    }
}
