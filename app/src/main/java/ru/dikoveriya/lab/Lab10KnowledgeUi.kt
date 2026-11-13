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
import androidx.compose.ui.unit.dp

/** Actual offline user protocol library, not a fake list of external articles. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun Lab10KnowledgeScreen(
    db:LabDatabase,revision:Int,onError:(String)->Unit,onChanged:()->Unit,
    modifier:Modifier=Modifier
){
    var search by rememberSaveable{mutableStateOf("")}
    var editing by remember{mutableStateOf(false)}
    var opened by remember{mutableStateOf<Lab10KnowledgeNote?>(null)}
    var deletePending by remember{mutableStateOf<Lab10KnowledgeNote?>(null)}
    val rows=remember(revision,search){db.knowledgeNotes10(search)}
    Column(modifier.padding(horizontal=16.dp)){
        Row(verticalAlignment=Alignment.CenterVertically,
            modifier=Modifier.fillMaxWidth().padding(vertical=10.dp)){
            Column(Modifier.weight(1f)){
                Text("СПРАВОЧНИК / ЛАБОРАТОРИЯ",style=MaterialTheme.typography.labelSmall,
                    color=MaterialTheme.colorScheme.tertiary)
                Text("База знаний",style=MaterialTheme.typography.headlineSmall)
            }
            IconButton(onClick={editing=true}){
                Icon(Icons.Outlined.Add,contentDescription="Добавить протокол")
            }
        }
        OutlinedTextField(search,{search=it},singleLine=true,
            modifier=Modifier.fillMaxWidth(),
            placeholder={Text("Поиск по протоколам и заметкам")},
            leadingIcon={Icon(Icons.Outlined.Search,null)})
        Spacer(Modifier.height(12.dp))
        if(rows.isEmpty()){
            Column(Modifier.fillMaxWidth().padding(26.dp),
                horizontalAlignment=Alignment.CenterHorizontally){
                Icon(Icons.Outlined.MenuBook,null,tint=Lab07Palette.Moss,
                    modifier=Modifier.size(54.dp))
                Text("Своих протоколов пока нет",style=MaterialTheme.typography.titleMedium)
                Text("Сохраняй проверенные методики и источники локально",
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Button(onClick={editing=true}){Text("Добавить запись")}
            }
        }else LazyColumn(verticalArrangement=Arrangement.spacedBy(9.dp)){
            items(rows,key={it.id}){entry->
                OutlinedCard(onClick={opened=entry},modifier=Modifier.fillMaxWidth(),
                    shape=RoundedCornerShape(18.dp),
                    colors=CardDefaults.outlinedCardColors(
                        containerColor=MaterialTheme.colorScheme.surface)){
                    Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){
                        Icon(Icons.Outlined.MenuBook,null,tint=MaterialTheme.colorScheme.primary,
                            modifier=Modifier.size(28.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)){
                            Text(entry.title,style=MaterialTheme.typography.titleMedium)
                            Text(entry.category+" · собственный материал",
                                style=MaterialTheme.typography.bodySmall,
                                color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Outlined.ChevronRight,null)
                    }
                }
            }
        }
    }
    if(editing){
        var title by remember{mutableStateOf("")}
        var category by remember{mutableStateOf("Протокол")}
        var body by remember{mutableStateOf("")}
        var source by remember{mutableStateOf("")}
        ModalBottomSheet(onDismissRequest={editing=false}){
            Column(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=12.dp)
                .verticalScroll(rememberScrollState()),
                verticalArrangement=Arrangement.spacedBy(10.dp)){
                Text("Новая запись",style=MaterialTheme.typography.headlineSmall)
                OutlinedTextField(title,{title=it},label={Text("Название")},
                    modifier=Modifier.fillMaxWidth())
                Text("Тип записи")
                listOf("Протокол","Среда","Растение","Проблема","Заметка")
                    .chunked(3).forEach{types->
                        Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){
                            types.forEach{type->
                                FilterChip(selected=category==type,onClick={category=type},
                                    label={Text(type)})
                            }
                        }
                    }
                OutlinedTextField(body,{body=it},minLines=5,maxLines=10,
                    label={Text("Текст / методика")},
                    modifier=Modifier.fillMaxWidth())
                OutlinedTextField(source,{source=it},singleLine=true,
                    label={Text("Источник / DOI / ссылка (необязательно)")},
                    modifier=Modifier.fillMaxWidth())
                Text("Источник не проверяется автоматически. Не выдавай собственную заметку за научную публикацию.",
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick={
                    try{
                        db.addKnowledge10(title,category,body,source)
                        editing=false;onChanged()
                    }catch(e:Exception){onError(e.message?:"Не удалось сохранить запись")}
                },modifier=Modifier.fillMaxWidth()){Text("Сохранить")}
                Spacer(Modifier.height(18.dp))
            }
        }
    }
    opened?.let{note->
        AlertDialog(onDismissRequest={opened=null},
            title={Text(note.title)},
            text={Column(Modifier.verticalScroll(rememberScrollState())){
                Text(note.category,style=MaterialTheme.typography.labelLarge,
                    color=MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.height(8.dp))
                Text(note.body)
                if(note.sourceUrl.isNotBlank()){
                    Spacer(Modifier.height(12.dp))
                    Text("Источник: "+note.sourceUrl,
                        style=MaterialTheme.typography.bodySmall)
                }
            }},
            confirmButton={TextButton(onClick={opened=null}){Text("Закрыть")}},
            dismissButton={TextButton(onClick={deletePending=note;opened=null}){
                Text("Удалить",color=MaterialTheme.colorScheme.error)
            }})
    }
    deletePending?.let{note->
        AlertDialog(onDismissRequest={deletePending=null},
            title={Text("Удалить свою заметку?")},
            text={Text(note.title)},
            confirmButton={TextButton(onClick={
                try{
                    db.deleteKnowledge10(note.id);deletePending=null;onChanged()
                }catch(e:Exception){onError(e.message?:"Не удалось удалить")}
            }){Text("Удалить",color=MaterialTheme.colorScheme.error)}},
            dismissButton={TextButton(onClick={deletePending=null}){Text("Отмена")}})
    }
}
