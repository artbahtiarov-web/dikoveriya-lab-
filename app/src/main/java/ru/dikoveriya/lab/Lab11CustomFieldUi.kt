package ru.dikoveriya.lab

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

/** User-controlled schema fields are rendered consistently on every object type. */
@Composable
internal fun Lab11CustomFieldsSection(
    db:LabDatabase,entityKind:String,entityId:Long,revision:Int,
    onChanged:()->Unit,onError:(String)->Unit
){
    val definitions=remember(entityKind,revision){db.fields11(entityKind)}
    val values=remember(entityKind,entityId,revision){
        db.fieldValues11(entityKind,entityId)
    }
    var selected by remember{mutableStateOf<Lab11CustomField?>(null)}
    val context=androidx.compose.ui.platform.LocalContext.current
    val scope=rememberCoroutineScope()
    var pendingPhoto by remember{mutableStateOf<Long?>(null)}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){uri->
        val fieldId=pendingPhoto
        pendingPhoto=null
        if(uri!=null && fieldId!=null)scope.launch{
            try{
                withContext(Dispatchers.IO){
                    db.attachFieldPhoto11(context,fieldId,entityKind,entityId,uri)
                }
                selected=null
                onChanged()
            }catch(e:Exception){onError(e.message?:"Не удалось сохранить пользовательское фото")}
        }
    }
    if(definitions.isNotEmpty()){
        Column(verticalArrangement=Arrangement.spacedBy(9.dp)){
            Text("Дополнительные сведения",style=MaterialTheme.typography.titleMedium)
            definitions.forEach{field->
                OutlinedCard(onClick={
                    if(field.type=="photo"){
                        pendingPhoto=field.id
                        picker.launch("image/*")
                    }else selected=field
                },modifier=Modifier.fillMaxWidth()){
                    Row(Modifier.padding(12.dp),
                        verticalAlignment=Alignment.CenterVertically){
                        Column(Modifier.weight(1f)){
                            Text(field.label,style=MaterialTheme.typography.titleSmall)
                            val v=values[field.id]
                            Text(if(v.isNullOrBlank())"Не заполнено" else
                                if(field.type=="photo")"Фото загружено" else v,
                                style=MaterialTheme.typography.bodySmall,
                                color=MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines=2)
                        }
                        Icon(if(field.type=="photo")Icons.Outlined.AddPhotoAlternate
                            else Icons.Outlined.Edit,"Изменить поле")
                    }
                }
            }
        }
    }
    selected?.let{field->
        var value by remember(field.id,revision){
            mutableStateOf(values[field.id]?:"")
        }
        AlertDialog(onDismissRequest={selected=null},
            title={Text(field.label)},
            text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
                when(field.type){
                    "date"->Lab11DateField("Дата",value,{value=it},allowClear=!field.required)
                    "boolean"->Row(verticalAlignment=Alignment.CenterVertically){
                        Checkbox(checked=value=="true",onCheckedChange={
                            value=if(it)"true" else "false"
                        })
                        Text("Да")
                    }
                    "choice"->{
                        val choices=JSONArray(field.choices)
                        val options=(0 until choices.length()).map{choices.getString(it)}
                        PickOne("Выбери значение",
                            listOf("Не указано")+options,
                            value.ifBlank{"Не указано"},
                            {value=if(it=="Не указано")"" else it})
                    }
                    else->Field(field.label,value,{value=it},
                        single=field.type !in setOf("text"))
                }
                if(field.type=="number" || field.type=="unit")
                    Text("Укажи число; единицы фиксируются названием поля.",
                        style=MaterialTheme.typography.bodySmall)
            }},
            confirmButton={Button(enabled=!field.required || value.isNotBlank(),
                onClick={
                    try{
                        db.saveFieldValue11(field.id,entityKind,entityId,value)
                        selected=null;onChanged()
                    }catch(e:Exception){onError(e.message?:"Не удалось сохранить поле")}
                }){Text("Сохранить")}},
            dismissButton={TextButton(onClick={selected=null}){Text("Отмена")}})
    }
}
