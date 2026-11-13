package ru.dikoveriya.lab

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val iconOptions10=listOf(
    "leaf" to "Лист","sprout" to "Росток","flower" to "Цветок",
    "flask" to "Пробирка","tree" to "Дерево","star" to "Звезда",
    "heart" to "Избранное","seedling" to "Саженец"
)
private val colorOptions10=listOf(
    "forest" to "Лесной","moss" to "Моховой","gold" to "Золотой",
    "teal" to "Бирюзовый","rose" to "Терракота"
)
private fun plantColor10(key:String):Color=when(key){
    "moss"->Lab07Palette.Moss
    "gold"->Lab07Palette.InkBrass
    "teal"->Lab07Palette.Teal
    "rose"->Color(0xFF9B5748)
    else->Lab07Palette.Forest
}

@Composable
internal fun PlantIcon10(iconKey:String,modifier:Modifier=Modifier,tint:Color=Lab07Palette.Brass){
    val icon=when(iconKey){
        "sprout"->Icons.Outlined.Spa
        "flower"->Icons.Outlined.LocalFlorist
        "flask"->Icons.Outlined.Science
        "tree"->Icons.Outlined.Park
        "star"->Icons.Outlined.StarBorder
        "heart"->Icons.Outlined.FavoriteBorder
        "seedling"->Icons.Outlined.Grass
        else->Icons.Outlined.Eco
    }
    Icon(icon,contentDescription=null,tint=tint,modifier=modifier)
}

@Composable
private fun PlantCover10(filename:String?,icon:String,color:String,modifier:Modifier=Modifier){
    val context=androidx.compose.ui.platform.LocalContext.current
    val bmp by produceState<android.graphics.Bitmap?>(initialValue=null,key1=filename){
        value=withContext(Dispatchers.IO){
            if(filename.isNullOrBlank())null else {
                val name=filename.takeIf{Regex("[a-zA-Z0-9_-]+\\.(jpg|png|webp|heic)").matches(it)}
                val file=name?.let{File(context.filesDir,"culture_photos/"+it)}
                if(file?.isFile!=true)null else try{
                    val opt=BitmapFactory.Options().apply{inJustDecodeBounds=true}
                    BitmapFactory.decodeFile(file.absolutePath,opt)
                    val maxSide=maxOf(opt.outWidth,opt.outHeight)
                    val sample=(maxSide/600).coerceAtLeast(1)
                    BitmapFactory.decodeFile(file.absolutePath,
                        BitmapFactory.Options().apply{inSampleSize=sample})
                }catch(_:Exception){null}
            }
        }
    }
    Box(modifier.background(plantColor10(color)),contentAlignment=Alignment.Center){
        if(bmp!=null){
            Image(bitmap=bmp!!.asImageBitmap(),contentDescription="Фотография растения",
                modifier=Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent,Color(0x990B2E20)))))
        }else PlantIcon10(icon,modifier=Modifier.size(60.dp),tint=Lab07Palette.Brass)
        Box(Modifier.align(Alignment.TopStart).padding(9.dp)
            .background(Lab07Palette.DeepForest.copy(alpha=.82f),RoundedCornerShape(10.dp))
            .padding(6.dp)){
            Icon(Icons.Outlined.Folder,contentDescription="Папка сорта",
                tint=Lab07Palette.Champagne,modifier=Modifier.size(17.dp))
        }
    }
}

@Composable
internal fun Lab10PlantFolder(
    plant:PlantRecord,count:Int,visual:Lab10PlantVisual,
    onOpen:()->Unit,onStyle:()->Unit,compact:Boolean=false
) {
    Card(onClick=onOpen,modifier=Modifier.fillMaxWidth(),
        shape=RoundedCornerShape(19.dp),
        colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),
        border=BorderStroke(1.dp,Lab07Palette.Brass.copy(alpha=.38f))){
        if(compact){
            Row(verticalAlignment=Alignment.CenterVertically,
                modifier=Modifier.heightIn(min=88.dp)){
                PlantCover10(visual.coverFilename,visual.iconKey,visual.colorKey,
                    Modifier.size(width=84.dp,height=84.dp).clip(RoundedCornerShape(13.dp)))
                Column(Modifier.weight(1f).padding(start=12.dp),verticalArrangement=Arrangement.spacedBy(3.dp)){
                    Text(plant.name,style=MaterialTheme.typography.titleMedium,maxLines=2,
                        overflow=TextOverflow.Ellipsis)
                    Text("Контейнеров: "+count,color=MaterialTheme.colorScheme.onSurfaceVariant,
                        style=MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick=onStyle,modifier=Modifier.size(48.dp)){
                    Icon(Icons.Outlined.MoreVert,contentDescription="Оформить карточку сорта")
                }
            }
        }else{
            Box(Modifier.fillMaxWidth().height(135.dp)){
                PlantCover10(visual.coverFilename,visual.iconKey,visual.colorKey,
                    Modifier.fillMaxSize())
                IconButton(onClick=onStyle,
                    modifier=Modifier.align(Alignment.TopEnd).padding(4.dp)
                        .background(Lab07Palette.DeepForest.copy(alpha=.78f),RoundedCornerShape(13.dp))){
                    Icon(Icons.Outlined.MoreVert,contentDescription="Оформить карточку сорта",
                        tint=Color.White)
                }
            }
            Column(Modifier.padding(horizontal=11.dp,vertical=10.dp),
                verticalArrangement=Arrangement.spacedBy(4.dp)){
                Text(plant.name,style=MaterialTheme.typography.titleMedium,
                    maxLines=2,minLines=2,overflow=TextOverflow.Ellipsis)
                Row(verticalAlignment=Alignment.CenterVertically){
                    PlantIcon10(visual.iconKey,modifier=Modifier.size(16.dp),
                        tint=plantColor10(visual.colorKey))
                    Spacer(Modifier.width(5.dp))
                    Text("Контейнеров: "+count,style=MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/** Search and card/list mode stay in the collection; sorting lives in a separate sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun Lab10CollectionScreen(
    plants:List<PlantRecord>,db:LabDatabase,revision:Int,
    onBack:()->Unit,onNavigate:(String)->Unit,onNewPlant:()->Unit,
    onChanged:()->Unit,onError:(String)->Unit,modifier:Modifier=Modifier
){
    val context=androidx.compose.ui.platform.LocalContext.current
    val scope=rememberCoroutineScope()
    var search by rememberSaveable{mutableStateOf("")}
    var order by rememberSaveable{mutableStateOf("Название")}
    var compact by rememberSaveable{mutableStateOf(false)}
    var filterSheet by remember{mutableStateOf(false)}
    var styleId by remember{mutableStateOf<Long?>(null)}
    var localReload by remember{mutableIntStateOf(0)}
    val visuals=remember(revision,localReload){db.plantVisuals10()}
    val counts=remember(revision){db.plantCounts072()}
    val visible=remember(plants,counts,search,order){
        val list=plants.filter{it.name.contains(search,true) || it.description.contains(search,true)}
        if(order=="Количество")list.sortedWith(compareByDescending<PlantRecord>{counts[it.id]?:0}.thenBy{it.name})
        else list.sortedBy{it.name.lowercase()}
    }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){uri->
        val id=styleId
        if(uri!=null && id!=null){
            scope.launch{
                try{
                    withContext(Dispatchers.IO){db.setPlantCover10(context,id,uri)}
                    localReload++;onChanged()
                }catch(e:Exception){onError(e.message?:"Не удалось сохранить обложку")}
            }
        }
    }
    Column(modifier){
        Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=12.dp),
            verticalAlignment=Alignment.CenterVertically){
            IconButton(onClick=onBack){Icon(Icons.Outlined.ArrowBack,"Назад")}
            Column(Modifier.weight(1f)){
                Text("ДИКОВЕРИЯ / ЛАБОРАТОРИЯ",style=MaterialTheme.typography.labelSmall,
                    color=MaterialTheme.colorScheme.tertiary)
                Text("Коллекция",style=MaterialTheme.typography.headlineSmall)
            }
            IconButton(onClick={compact=!compact}){
                Icon(if(compact)Icons.Outlined.GridView else Icons.Outlined.ViewList,
                    if(compact)"Показать плитки" else "Показать список")
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),
            verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(7.dp)){
            OutlinedTextField(value=search,onValueChange={search=it},
                modifier=Modifier.weight(1f),singleLine=true,
                leadingIcon={Icon(Icons.Outlined.Search,null)},
                placeholder={Text("Найти растение")},
                shape=RoundedCornerShape(16.dp))
            IconButton(onClick={filterSheet=true},
                modifier=Modifier.size(52.dp).background(
                    MaterialTheme.colorScheme.surface,RoundedCornerShape(16.dp))){
                Icon(Icons.Outlined.Tune,contentDescription="Фильтры и сортировка",
                    tint=MaterialTheme.colorScheme.primary)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=12.dp),
            verticalAlignment=Alignment.CenterVertically){
            Text("Сортов: "+visible.size,modifier=Modifier.weight(1f),
                style=MaterialTheme.typography.titleMedium)
            TextButton(onClick=onNewPlant){
                Icon(Icons.Outlined.Add,null,modifier=Modifier.size(17.dp))
                Text("Добавить")
            }
        }
        if(visible.isEmpty()){
            Column(Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment=Alignment.CenterHorizontally){
                Icon(Icons.Outlined.Spa,null,tint=Lab07Palette.Moss,
                    modifier=Modifier.size(55.dp))
                Text("Растений не найдено",style=MaterialTheme.typography.titleMedium)
                Text("Проверь поиск или добавь сорт",
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }else{
            LazyVerticalGrid(
                columns=if(compact)GridCells.Fixed(1) else GridCells.Fixed(2),
                contentPadding=PaddingValues(start=16.dp,end=16.dp,bottom=24.dp),
                horizontalArrangement=Arrangement.spacedBy(10.dp),
                verticalArrangement=Arrangement.spacedBy(10.dp),
                modifier=Modifier.fillMaxSize()
            ){
                items(visible,key={it.id}){plant->
                    Lab10PlantFolder(plant,counts[plant.id]?:0,
                        visuals[plant.id]?:Lab10PlantVisual(plant.id),
                        onOpen={onNavigate(Lab07Navigation.variety(plant.id))},
                        onStyle={styleId=plant.id},compact=compact)
                }
            }
        }
    }
    if(filterSheet){
        var draft by remember(filterSheet){mutableStateOf(order)}
        ModalBottomSheet(onDismissRequest={filterSheet=false}){
            Column(Modifier.padding(horizontal=20.dp,vertical=12.dp),
                verticalArrangement=Arrangement.spacedBy(14.dp)){
                Text("Фильтры и порядок",style=MaterialTheme.typography.headlineSmall)
                Text("Сортировка")
                listOf("Название","Количество").forEach{option->
                    Row(Modifier.fillMaxWidth().clickable{draft=option}.heightIn(min=48.dp),
                        verticalAlignment=Alignment.CenterVertically){
                        RadioButton(selected=draft==option,onClick={draft=option})
                        Text(if(option=="Название")"По названию (А–Я)" else "Сначала больше контейнеров")
                    }
                }
                Button(onClick={order=draft;filterSheet=false},
                    modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){
                    Text("Применить")
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
    styleId?.let{id->
        val plant=plants.firstOrNull{it.id==id}
        if(plant!=null){
            val saved=visuals[id]?:Lab10PlantVisual(id)
            var icon by remember(id){mutableStateOf(saved.iconKey)}
            var color by remember(id){mutableStateOf(saved.colorKey)}
            ModalBottomSheet(onDismissRequest={styleId=null}){
                Column(Modifier.padding(horizontal=20.dp,vertical=12.dp),
                    verticalArrangement=Arrangement.spacedBy(12.dp)){
                    Text("Оформление сорта",style=MaterialTheme.typography.headlineSmall)
                    Text(plant.name,maxLines=2)
                    Text("Иконка",style=MaterialTheme.typography.titleMedium)
                    iconOptions10.chunked(4).forEach{options->
                        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                            options.forEach{(key,label)->
                                FilterChip(selected=icon==key,onClick={icon=key},
                                    label={PlantIcon10(key,modifier=Modifier.size(23.dp))},
                                    modifier=Modifier.weight(1f))
                            }
                        }
                    }
                    Text("Цвет",style=MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){
                        colorOptions10.forEach{(key,_)->
                            FilterChip(selected=color==key,onClick={color=key},
                                label={Box(Modifier.size(14.dp).background(plantColor10(key),
                                    RoundedCornerShape(7.dp)))})
                        }
                    }
                    Button(onClick={
                        try{
                            db.stylePlant10(id,icon,color)
                            localReload++;onChanged();styleId=null
                        }catch(e:Exception){onError(e.message?:"Не удалось применить оформление")}
                    },modifier=Modifier.fillMaxWidth()){
                        Text("Сохранить иконку и цвет")
                    }
                    OutlinedButton(onClick={picker.launch("image/*")},
                        modifier=Modifier.fillMaxWidth()){
                        Icon(Icons.Outlined.AddPhotoAlternate,null)
                        Text("Выбрать обложку из галереи")
                    }
                    if(saved.coverFilename!=null){
                        TextButton(onClick={
                            try{
                                db.removePlantCover10(context,id)
                                localReload++;onChanged();styleId=null
                            }catch(e:Exception){onError(e.message?:"Не удалось убрать обложку")}
                        },modifier=Modifier.fillMaxWidth()){Text("Убрать обложку")}
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}


@Composable
internal fun Lab10VarietyHeader(
    plant:PlantRecord,visual:Lab10PlantVisual,count:Int,
    onBack:()->Unit,onNew:(()->Unit)?
){
    Column(verticalArrangement=Arrangement.spacedBy(9.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){
            IconButton(onClick=onBack){Icon(Icons.Outlined.ArrowBack,"Назад")}
            Text("ПАСПОРТ СОРТА",color=MaterialTheme.colorScheme.tertiary,
                style=MaterialTheme.typography.labelLarge)
        }
        Card(shape=RoundedCornerShape(21.dp),
            colors=CardDefaults.cardColors(containerColor=Lab07Palette.DeepForest),
            border=BorderStroke(1.dp,Lab07Palette.Brass.copy(alpha=.6f))){
            Row(verticalAlignment=Alignment.CenterVertically,
                modifier=Modifier.fillMaxWidth().padding(12.dp)){
                PlantCover10(visual.coverFilename,visual.iconKey,visual.colorKey,
                    Modifier.size(width=100.dp,height=116.dp).clip(RoundedCornerShape(16.dp)))
                Column(Modifier.weight(1f).padding(start=12.dp),
                    verticalArrangement=Arrangement.spacedBy(7.dp)){
                    Text(plant.name,color=Lab07Palette.Champagne,
                        style=MaterialTheme.typography.titleLarge,maxLines=3)
                    Row(verticalAlignment=Alignment.CenterVertically){
                        PlantIcon10(visual.iconKey,modifier=Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Контейнеров: "+count,color=Lab07Palette.Champagne,
                            style=MaterialTheme.typography.bodyMedium)
                    }
                    if(plant.description.isNotBlank())
                        Text(plant.description,color=Lab07Palette.Champagne.copy(alpha=.85f),
                            maxLines=2,overflow=TextOverflow.Ellipsis,
                            style=MaterialTheme.typography.bodySmall)
                }
            }
        }
        if(onNew!=null){
            Button(onClick=onNew,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){
                Icon(Icons.Outlined.AddCircleOutline,null)
                Spacer(Modifier.width(6.dp))
                Text("Новый контейнер этого сорта")
            }
        }
    }
}
