package ru.dikoveriya.lab

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

internal data class Lab13ObjectOption(val id:Long,val title:String,val subtitle:String)

private fun LabDatabase.objectCandidates13(type:String,query:String):List<Lab13ObjectOption>{
    val q=query.trim().take(100)
    return when(type){
        "Контейнер in vitro"->readableDatabase.rawQuery("""
            SELECT c.id,c.code,p.name FROM cultures c JOIN plants p ON p.id=c.plant_id
            WHERE (?='' OR c.code LIKE ? OR p.name LIKE ?)
            ORDER BY c.id DESC LIMIT 30
        """.trimIndent(),arrayOf(q,"%$q%","%$q%")).use{c->buildList{
            while(c.moveToNext())add(Lab13ObjectOption(c.getLong(0),
                c.getString(1),c.getString(2)))
        }}
        "Горшечное растение","Маточник","Черенок","Другое растение / клубень"->{
            val kind=when(type){
                "Маточник"->"Маточник"
                "Черенок"->"Черенок"
                "Другое растение / клубень"->"Другое"
                else->"Горшечное"
            }
            plantItems11(q,limit=30,kind=kind).map{
                Lab13ObjectOption(it.id,it.code,it.plantName+" · "+it.stage)
            }
        }
        "Оборудование"->readableDatabase.rawQuery("""
            SELECT id,name,category FROM equipment_assets
            WHERE (?='' OR name LIKE ? OR category LIKE ?)
            ORDER BY id DESC LIMIT 30
        """.trimIndent(),arrayOf(q,"%$q%","%$q%")).use{c->buildList{
            while(c.moveToNext())add(Lab13ObjectOption(c.getLong(0),
                c.getString(1),c.getString(2)))
        }}
        else->emptyList()
    }
}

/** Context-based target picker; QR resolves existing records only. */
@Composable
internal fun Lab13TaskObjectPicker(
    db:LabDatabase,kind:String,initial:Lab13TaskSelection,
    onClose:()->Unit,onChosen:(Lab13TaskSelection)->Unit,
    onError:(String)->Unit
){
    val suggested=when(kind){
        "Закупка"->"Покупка / закупка"
        "Отправка заказа"->"Заказ / отправка"
        "Обслуживание оборудования"->"Оборудование"
        else->initial.kind.ifBlank{"Горшечное растение"}
    }
    var type by remember{mutableStateOf(
        if(initial.kind.isNotBlank())initial.kind else suggested)}
    var selected by remember{mutableStateOf(initial.id)}
    var query by remember{mutableStateOf("")}
    var description by remember{mutableStateOf(initial.text)}
    val physical=type in setOf("Контейнер in vitro","Горшечное растение",
        "Маточник","Черенок","Другое растение / клубень","Оборудование")
    val candidates=remember(type,query){db.objectCandidates13(type,query)}
    val scanner=rememberLauncherForActivityResult(ScanContract()){result->
        val code=result.contents?.trim().orEmpty()
        if(code.isNotBlank()){
            try{
                val matched=when(type){
                    "Контейнер in vitro"->db.findCulture072(code)
                    "Горшечное растение","Маточник","Черенок",
                    "Другое растение / клубень"->db.itemIdByCode11(code)
                        ?.takeIf{id->
                            val it=db.item11(id)?:return@takeIf false
                            it.kind==when(type){
                                "Маточник"->"Маточник"
                                "Черенок"->"Черенок"
                                "Другое растение / клубень"->"Другое"
                                else->"Горшечное"
                            }
                        }
                    "Оборудование"->code.removePrefix("EQ-").toLongOrNull()
                        ?.takeIf{id->db.assets11().any{it.id==id}}
                    else->null
                }
                if(matched==null)error("Сканированный QR не принадлежит выбранному типу объекта")
                selected=matched
                query=code
            }catch(e:Exception){onError(e.message?:"Объект по QR не найден")}
        }
    }
    AlertDialog(onDismissRequest=onClose,
        title={Text("Объект задачи · "+kind)},
        text={
            Column(Modifier.heightIn(max=540.dp),
                verticalArrangement=Arrangement.spacedBy(10.dp)){
                Text("Привязка необязательна. Выбери тип объекта, "+
                    "затем конкретное растение, контейнер или описание работы.",
                    style=MaterialTheme.typography.bodySmall)
                PickOne("Тип объекта",taskObjectTypes13,
                    type,{type=it;selected=null;query="";description=""})
                if(physical){
                    Row{
                        OutlinedTextField(value=query,
                            onValueChange={query=it;selected=null},
                            singleLine=true,label={Text("Название или номер")},
                            modifier=Modifier.weight(1f))
                        IconButton(onClick={
                            scanner.launch(ScanOptions()
                                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                .setPrompt("QR объекта · "+type)
                                .setBeepEnabled(false)
                                .setOrientationLocked(false))
                        }){Icon(Icons.Outlined.QrCodeScanner,"Сканировать QR")}
                    }
                    LazyColumn(modifier=Modifier.heightIn(max=250.dp),
                        verticalArrangement=Arrangement.spacedBy(6.dp)){
                        if(candidates.isEmpty())item{
                            Text("Ничего не найдено",style=MaterialTheme.typography.bodySmall)
                        }
                        items(candidates,key={it.id}){option->
                            OutlinedCard(onClick={
                                selected=option.id
                            },modifier=Modifier.fillMaxWidth(),
                                colors=CardDefaults.outlinedCardColors(
                                    containerColor=if(selected==option.id)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surface)){
                                Column(Modifier.padding(10.dp)){
                                    Text(option.title,style=MaterialTheme.typography.titleSmall)
                                    Text(option.subtitle,style=MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    if(selected!=null)Text("Выбран объект #"+selected,
                        style=MaterialTheme.typography.labelMedium)
                }else if(type in setOf("Покупка / закупка","Заказ / отправка","Другое")){
                    Field(when(type){
                        "Покупка / закупка"->"Что нужно купить / заказать"
                        "Заказ / отправка"->"Что подготовить, кому, куда"
                        else->"Описание объекта или работы"
                    },description,{description=it},single=false)
                }
            }
        },
        confirmButton={Button(
            enabled=when{
                type=="Не привязана"->true
                physical->selected!=null
                else->description.isNotBlank()
            },
            onClick={
                onChosen(when{
                    type=="Не привязана"->Lab13TaskSelection()
                    physical->Lab13TaskSelection(type,selected,
                        candidates.firstOrNull{it.id==selected}?.let{
                            it.title+" · "+it.subtitle
                        }?:query)
                    else->Lab13TaskSelection(type,null,description.trim())
                })
            }){Text("Выбрать")}},
        dismissButton={TextButton(onClick=onClose){Text("Отмена")}}
    )
}
