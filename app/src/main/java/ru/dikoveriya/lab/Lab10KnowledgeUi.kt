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

/**
 * Offline knowledge vault. Source metadata is user supplied;
 * this screen never labels an unverified publication as verified.
 */
@Composable
internal fun Lab10KnowledgeScreen(
    db:LabDatabase,revision:Int,onError:(String)->Unit,onChanged:()->Unit,
    modifier:Modifier=Modifier
) {
    var search by rememberSaveable{mutableStateOf("")}
    var editing by remember{mutableStateOf(false)}
    var editNote by remember{mutableStateOf<Lab10KnowledgeNote?>(null)}
    var opened by remember{mutableStateOf<Lab10KnowledgeNote?>(null)}
    var deletePending by remember{mutableStateOf<Lab10KnowledgeNote?>(null)}
    val notes=remember(revision,search){db.knowledgeNotes10(search)}
    val plants=remember(revision){db.plants()}
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
            modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),
            placeholder={Text("Протокол, растение или метка")},
            leadingIcon={Icon(Icons.Outlined.Search,null)})
        Text("Мои протоколы и записи · "+notes.size,
            modifier=Modifier.padding(vertical=11.dp),
            style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(notes.isEmpty()){
            Column(Modifier.fillMaxWidth().padding(26.dp),
                horizontalAlignment=Alignment.CenterHorizontally){
                Icon(Icons.Outlined.MenuBook,null,tint=MaterialTheme.colorScheme.primary,
                    modifier=Modifier.size(54.dp))
                Text("Локальных записей пока нет",
                    style=MaterialTheme.typography.titleMedium)
                Text("Добавляй свои методики, версии и источники.",
                    style=MaterialTheme.typography.bodyMedium,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                Button(onClick={editing=true}){Text("Создать протокол")}
            }
        }else LazyColumn(verticalArrangement=Arrangement.spacedBy(9.dp)){
            items(notes,key={it.id}){note->
                OutlinedCard(onClick={opened=note},modifier=Modifier.fillMaxWidth(),
                    shape=RoundedCornerShape(18.dp),
                    border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),
                    colors=CardDefaults.outlinedCardColors(
                        containerColor=MaterialTheme.colorScheme.surface)){
                    Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){
                        Surface(color=MaterialTheme.colorScheme.primaryContainer,
                            shape=RoundedCornerShape(12.dp)){
                            Icon(Icons.Outlined.MenuBook,null,
                                tint=MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier=Modifier.padding(10.dp).size(28.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f),
                            verticalArrangement=Arrangement.spacedBy(4.dp)){
                            Text(note.title,style=MaterialTheme.typography.titleMedium,
                                maxLines=2,overflow=TextOverflow.Ellipsis)
                            Text(note.category+" · версия "+note.version,
                                style=MaterialTheme.typography.bodySmall,
                                color=MaterialTheme.colorScheme.onSurfaceVariant)
                            if(note.tags.isNotBlank())
                                Text(note.tags,style=MaterialTheme.typography.bodySmall,
                                    maxLines=1,overflow=TextOverflow.Ellipsis)
                        }
                        Icon(Icons.Outlined.ChevronRight,null)
                    }
                }
            }
            item{Spacer(Modifier.height(12.dp))}
        }
    }
    if(editing || editNote!=null) {
        val current=editNote
        Lab10KnowledgeEditor(
            initial=current,plants=plants,
            onClose={editing=false;editNote=null},
            onSave={title,category,body,source,plantId,tags->
                try{
                    if(current==null)
                        db.addKnowledge10(title,category,body,source,plantId,tags)
                    else
                        db.updateKnowledge10(current.id,title,category,body,source,plantId,tags)
                    editing=false;editNote=null
                    onChanged()
                }catch(e:Exception){onError(e.message?:"Не удалось сохранить протокол")}
            }
        )
    }
    opened?.let{note->
        AlertDialog(onDismissRequest={opened=null},
            title={Text(note.title)},
            text={Column(Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement=Arrangement.spacedBy(8.dp)){
                Text(note.category+" · версия "+note.version,
                    style=MaterialTheme.typography.labelLarge,
                    color=MaterialTheme.colorScheme.tertiary)
                note.plantId?.let{id->
                    Text("Растение: "+(plants.firstOrNull{it.id==id}?.name ?: "Не найдено"))
                }
                Text(note.body)
                if(note.tags.isNotBlank())Text("Метки: "+note.tags)
                if(note.sourceUrl.isNotBlank())Text("Источник: "+note.sourceUrl,
                    style=MaterialTheme.typography.bodySmall)
                Text("Добавлено пользователем. Источник не проверялся автоматически.",
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
            }},
            confirmButton={
                Row{
                    TextButton(onClick={editNote=note;opened=null}){
                        Icon(Icons.Outlined.Edit,null)
                        Text("Изменить")
                    }
                    TextButton(onClick={opened=null}){Text("Закрыть")}
                }
            },
            dismissButton={TextButton(onClick={deletePending=note;opened=null}){
                Text("Архивировать",color=MaterialTheme.colorScheme.error)
            }})
    }
    deletePending?.let{note->
        AlertDialog(onDismissRequest={deletePending=null},
            title={Text("Архивировать протокол?")},
            text={Text("Запись скроется из списка, а её версии останутся в базе.")},
            confirmButton={TextButton(onClick={
                try{
                    db.deleteKnowledge10(note.id)
                    deletePending=null;onChanged()
                }catch(e:Exception){onError(e.message?:"Не удалось архивировать")}
            }){Text("Архивировать")}},
            dismissButton={TextButton(onClick={deletePending=null}){Text("Отмена")}})
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Lab10KnowledgeEditor(
    initial:Lab10KnowledgeNote?,plants:List<PlantRecord>,
    onClose:()->Unit,
    onSave:(String,String,String,String,Long?,String)->Unit
){
    var title by remember(initial?.id){mutableStateOf(initial?.title.orEmpty())}
    var category by remember(initial?.id){mutableStateOf(initial?.category?:"Протокол")}
    var body by remember(initial?.id){mutableStateOf(initial?.body.orEmpty())}
    var source by remember(initial?.id){mutableStateOf(initial?.sourceUrl.orEmpty())}
    var tags by remember(initial?.id){mutableStateOf(initial?.tags.orEmpty())}
    var plantId by remember(initial?.id){mutableStateOf(initial?.plantId)}
    ModalBottomSheet(onDismissRequest=onClose,
        containerColor=MaterialTheme.colorScheme.surface){
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal=20.dp,vertical=12.dp),
            verticalArrangement=Arrangement.spacedBy(11.dp)){
            Text(if(initial==null)"Новый протокол" else "Редактирование · v"+initial.version,
                style=MaterialTheme.typography.headlineSmall)
            OutlinedTextField(title,{title=it.take(150)},
                label={Text("Название")},modifier=Modifier.fillMaxWidth())
            PickOne("Категория",
                listOf("Протокол","Среда","Растение","Проблема","Заметка"),
                category,{category=it})
            PickOne("Растение / сорт",listOf("Без привязки")+plants.map{it.name},
                plants.firstOrNull{it.id==plantId}?.name?:"Без привязки",
                {label->plantId=plants.firstOrNull{it.name==label}?.id})
            OutlinedTextField(body,{body=it.take(20000)},minLines=6,maxLines=12,
                label={Text("Методика, наблюдение или вывод")},
                modifier=Modifier.fillMaxWidth())
            OutlinedTextField(source,{source=it.take(1000)},
                label={Text("DOI или ссылка на источник")},
                modifier=Modifier.fillMaxWidth())
            OutlinedTextField(tags,{tags=it.take(400)},
                label={Text("Метки через запятую")},
                modifier=Modifier.fillMaxWidth())
            Text("Изменение сохраняет предыдущую редакцию в базе. Данные доступны офлайн.",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick={onSave(title,category,body,source,plantId,tags)},
                modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){
                Text("Сохранить протокол")
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
