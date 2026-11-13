package ru.dikoveriya.lab

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun Lab11StorageScreen(
    db:LabDatabase,revision:Int,
    onBack:()->Unit,onItem:(Long)->Unit,onCulture:(Long)->Unit,
    onChanged:()->Unit,onError:(String)->Unit,
    initialFocusId:Long?=null,modifier:Modifier=Modifier
){
    val context=androidx.compose.ui.platform.LocalContext.current
    var pendingLabel by remember{mutableStateOf<Long?>(null)}
    val saveQr=rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png")){uri->
        val id=pendingLabel
        pendingLabel=null
        if(uri!=null && id!=null)runCatching{
            context.contentResolver.openOutputStream(uri)?.use{
                it.write(LabQr.png("LOC-%06d".format(java.util.Locale.US,id)))
            }?:error("Файл не создан")
        }.onFailure{onError(it.message?:"Ошибка сохранения QR места")}
    }
    val racks=remember(revision){db.racks11()}
    val focusedRack=remember(initialFocusId,revision){
        racks.firstOrNull{rack->rack.shelves.any{sh->
            sh.id==initialFocusId || sh.slots.any{it.id==initialFocusId}
        }}?.id
    }
    var roomRack by rememberSaveable(initialFocusId){mutableStateOf(focusedRack)}
    var searched by rememberSaveable{mutableStateOf("")}
    var highlighted by rememberSaveable(initialFocusId){mutableStateOf(initialFocusId)}
    var selected by remember(initialFocusId){mutableStateOf(initialFocusId)}
    var newRack by remember{mutableStateOf(false)}
    var legacy by remember{mutableStateOf(false)}
    val showing=racks.firstOrNull{it.id==roomRack}?:racks.firstOrNull()
    if(legacy){
        Lab072PlacesScreen(db,revision,onBack={legacy=false},
            onChanged=onChanged,onError=onError,modifier=modifier)
        return
    }
    LazyColumn(modifier=modifier,contentPadding=PaddingValues(16.dp),
        verticalArrangement=Arrangement.spacedBy(13.dp)){
        item{
            Lab07ScreenHeader("ДИКОВЕРИЯ / РАЗМЕЩЕНИЕ","Места и стеллажи",
                "Выбери полку или позицию, чтобы увидеть реальные контейнеры и растения.",onBack)
        }
        item{
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                Button(onClick={newRack=true},modifier=Modifier.weight(1f)){
                    Icon(Icons.Outlined.Add,null)
                    Spacer(Modifier.width(5.dp))
                    Text("Стеллаж")
                }
                OutlinedButton(onClick={legacy=true},modifier=Modifier.weight(1f)){
                    Text("Все адреса")
                }
            }
        }
        item{
            OutlinedTextField(searched,{searched=it},
                modifier=Modifier.fillMaxWidth(),singleLine=true,
                leadingIcon={Icon(Icons.Outlined.Search,null)},
                label={Text("Найти LAB- или PLT-код")},
                trailingIcon={
                    IconButton(onClick={
                        val loc=db.findLocationForCode11(searched.trim())
                        if(loc==null)onError("Объект без назначенного места либо код не найден")
                        else{
                            highlighted=loc
                            val rack=racks.firstOrNull{r->
                                r.shelves.any{sh->sh.id==loc ||
                                    sh.slots.any{slot->slot.id==loc}}
                            }
                            if(rack!=null)roomRack=rack.id
                            else onError("Место пока не входит в визуальную схему стеллажа")
                        }
                    }){Icon(Icons.Outlined.Search,"Найти на схеме")}
                })
        }
        if(racks.isEmpty())item{
            Column(Modifier.fillMaxWidth().padding(22.dp),
                verticalArrangement=Arrangement.spacedBy(10.dp),
                horizontalAlignment=Alignment.CenterHorizontally){
                Icon(Icons.Outlined.Warehouse,null,modifier=Modifier.size(48.dp),
                    tint=Lab07Palette.Brass)
                Text("Стеллажей ещё нет")
                Button(onClick={newRack=true}){Text("Создать первый стеллаж")}
            }
        }
        if(racks.isNotEmpty()){
            item{
                Text("Стеллажи · "+racks.size,
                    style=MaterialTheme.typography.titleMedium)
                Row(Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    racks.forEach{rack->
                        FilterChip(selected=showing?.id==rack.id,
                            onClick={roomRack=rack.id;highlighted=null},
                            label={Text(rack.name)})
                    }
                }
            }
            showing?.let{rack->
                item{
                    Card(colors=CardDefaults.cardColors(
                        containerColor=Lab07Palette.DeepForest),
                        shape=RoundedCornerShape(22.dp),
                        border=BorderStroke(1.dp,Lab07Palette.Brass.copy(alpha=.6f))){
                        Column(Modifier.fillMaxWidth().padding(14.dp),
                            verticalArrangement=Arrangement.spacedBy(9.dp)){
                            Row(verticalAlignment=Alignment.CenterVertically){
                                Icon(Icons.Outlined.Warehouse,null,
                                    tint=Lab07Palette.Brass)
                                Spacer(Modifier.width(9.dp))
                                Column{
                                    Text(rack.name,color=Lab07Palette.Champagne,
                                        style=MaterialTheme.typography.titleLarge)
                                    Text(rack.room,color=Lab07Palette.Champagne,
                                        style=MaterialTheme.typography.bodySmall)
                                }
                            }
                            val used=rack.shelves.sumOf{sh->
                                sh.directCultureCount+sh.directItemCount+
                                    sh.slots.sumOf{it.cultureCount+it.itemCount}
                            }
                            Text("Размещённых объектов: "+used,
                                color=Lab07Palette.Champagne,
                                style=MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if(rack.shelves.isEmpty())item{
                    Text("Полки не созданы. Нажми «Все адреса» или создай новый стеллаж с полками.")
                }
                items(rack.shelves,key={it.id}){shelf->
                    Card(shape=RoundedCornerShape(19.dp),
                        colors=CardDefaults.cardColors(
                            containerColor=MaterialTheme.colorScheme.surface),
                        border=BorderStroke(1.dp,Lab07Palette.Brass.copy(alpha=.25f))){
                        Column(Modifier.fillMaxWidth().padding(12.dp),
                            verticalArrangement=Arrangement.spacedBy(10.dp)){
                            Row(verticalAlignment=Alignment.CenterVertically){
                                Text(shelf.name,style=MaterialTheme.typography.titleMedium,
                                    modifier=Modifier.weight(1f))
                                val used=shelf.slots.count{
                                    it.cultureCount+it.itemCount>0
                                }
                                Text("Занято "+used+"/"+shelf.slots.size,
                                    style=MaterialTheme.typography.labelSmall)
                            }
                            if(shelf.directCultureCount+shelf.directItemCount>0)
                                Text("На полке без позиции: "+
                                    (shelf.directCultureCount+shelf.directItemCount),
                                    style=MaterialTheme.typography.bodySmall)
                            shelf.slots.chunked(4).forEach{line->
                                Row(horizontalArrangement=Arrangement.spacedBy(5.dp)){
                                    line.forEach{slot->
                                        val occupancy=slot.cultureCount+slot.itemCount
                                        val hot=slot.id==highlighted
                                        val bg=when{
                                            hot->Lab07Palette.Brass
                                            occupancy>0->Lab07Palette.Moss.copy(alpha=.35f)
                                            else->MaterialTheme.colorScheme.surfaceVariant
                                        }
                                        Surface(onClick={selected=slot.id},
                                            modifier=Modifier.weight(1f).heightIn(min=65.dp),
                                            shape=RoundedCornerShape(12.dp),
                                            color=bg){
                                            Column(Modifier.padding(4.dp),
                                                horizontalAlignment=Alignment.CenterHorizontally,
                                                verticalArrangement=Arrangement.Center){
                                                Icon(if(occupancy>0)Icons.Outlined.Inventory2
                                                    else Icons.Outlined.AddBox,null,
                                                    modifier=Modifier.size(19.dp),
                                                    tint=if(hot)Lab07Palette.DeepForest
                                                        else MaterialTheme.colorScheme.onSurface)
                                                Text(slot.name.removePrefix("Место "),
                                                    style=MaterialTheme.typography.labelSmall,
                                                    color=if(hot)Lab07Palette.DeepForest
                                                        else MaterialTheme.colorScheme.onSurface)
                                                if(occupancy>0)Text(occupancy.toString()+" шт.",
                                                    style=MaterialTheme.typography.labelSmall,
                                                    color=if(hot)Lab07Palette.DeepForest
                                                        else MaterialTheme.colorScheme.onSurface)
                                            }
                                        }
                                    }
                                    repeat(4-line.size){Spacer(Modifier.weight(1f))}
                                }
                            }
                            if(shelf.slots.isEmpty()){
                                OutlinedButton(onClick={selected=shelf.id},
                                    modifier=Modifier.fillMaxWidth()){
                                    Text("Просмотреть содержимое полки")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if(newRack)Lab11RackForm(db,onClose={newRack=false},
        onSaved={id->roomRack=id;newRack=false;onChanged()},onError=onError)
    selected?.let{locId->
        val occupants=remember(locId,revision){db.slotOccupants11(locId)}
        var assign by remember(locId){mutableStateOf(false)}
        var code by remember(locId){mutableStateOf("")}
        var reason by remember(locId){mutableStateOf("Размещение")}
        ModalBottomSheet(onDismissRequest={selected=null}){
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                Text("Содержимое позиции",style=MaterialTheme.typography.headlineSmall)
                Row(verticalAlignment=Alignment.CenterVertically){
                    Text("QR: LOC-%06d".format(java.util.Locale.US,locId),
                        style=MaterialTheme.typography.bodySmall,
                        modifier=Modifier.weight(1f))
                    TextButton(onClick={
                        pendingLabel=locId
                        saveQr.launch("LOC-%06d.png".format(java.util.Locale.US,locId))
                    }){Text("Сохранить этикетку")}
                }
                if(occupants.isEmpty())Text("Место свободно")
                occupants.forEach{row->
                    OutlinedCard(onClick={
                        selected=null
                        if(row.type=="culture")onCulture(row.id)else onItem(row.id)
                    },modifier=Modifier.fillMaxWidth()){
                        Row(Modifier.padding(12.dp),
                            verticalAlignment=Alignment.CenterVertically){
                            Column(Modifier.weight(1f)){
                                Text(row.code,style=MaterialTheme.typography.titleMedium)
                                Text(row.plant+" · "+row.status,
                                    style=MaterialTheme.typography.bodySmall)
                            }
                            Icon(Icons.Outlined.ChevronRight,null)
                        }
                    }
                }
                if(assign){
                    Field("Номера LAB-, PLT- или ГР- через запятую или с новой строки",
                        code,{code=it},single=false)
                    Field("Причина размещения",reason,{reason=it})
                    Text("Можно переместить один экземпляр, несколько контейнеров либо все контейнеры партии за одну операцию.",
                        style=MaterialTheme.typography.bodySmall)
                    Button(onClick={
                        try{
                            val codes=code.trim().split(Regex("[,;\\s]+"))
                                .map{it.trim()}.filter{it.isNotBlank()}
                            val count=db.moveSelected11(codes,locId,reason)
                            assign=false;selected=null;onChanged()
                        }catch(e:Exception){onError(e.message?:"Не удалось разместить")}
                    },modifier=Modifier.fillMaxWidth()){
                        Text("Разместить выбранные объекты")
                    }
                }else Button(onClick={assign=true},modifier=Modifier.fillMaxWidth()){
                    Text("Разместить объект")
                }
                Spacer(Modifier.height(15.dp))
            }
        }
    }
}

@Composable
private fun Lab11RackForm(db:LabDatabase,onClose:()->Unit,
    onSaved:(Long)->Unit,onError:(String)->Unit){
    var room by rememberSaveable{mutableStateOf("Лаборатория")}
    var name by rememberSaveable{mutableStateOf("")}
    var shelves by rememberSaveable{mutableStateOf("4")}
    var slots by rememberSaveable{mutableStateOf("6")}
    AlertDialog(onDismissRequest=onClose,title={Text("Конструктор стеллажа")},
        text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
            Field("Помещение",room,{room=it})
            Field("Название стеллажа",name,{name=it})
            Field("Количество полок (1–20)",shelves,{shelves=it})
            Field("Позиций на полке (1–50)",slots,{slots=it})
            Text("Все адреса создаются сразу. После этого каждому месту можно назначать объекты.",
                style=MaterialTheme.typography.bodySmall)
        }},
        confirmButton={Button(enabled=name.trim().length>=2,onClick={
            try{
                val id=db.createRack11(room,name,shelves.toIntOrNull()?:0,
                    slots.toIntOrNull()?:0)
                onSaved(id)
            }catch(e:Exception){onError(e.message?:"Не удалось создать стеллаж")}
        }){Text("Создать")}},
        dismissButton={TextButton(onClick=onClose){Text("Отмена")}})
}
