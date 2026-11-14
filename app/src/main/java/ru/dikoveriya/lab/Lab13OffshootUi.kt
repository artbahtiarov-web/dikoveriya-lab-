package ru.dikoveriya.lab

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Physical propagation from a live plant: pups and corms share one source and batch. */
@Composable
internal fun Lab13OffshootDialog(
    parent:Lab11PlantItem,db:LabDatabase,
    onClose:()->Unit,onSaved:(Lab13OffshootResult)->Unit,onError:(String)->Unit
){
    var date by rememberSaveable{mutableStateOf(dateText11())}
    var pups by rememberSaveable{mutableStateOf("3")}
    var corms by rememberSaveable{mutableStateOf("9")}
    var pupStage by rememberSaveable{mutableStateOf("Укоренение")}
    var substrate by rememberSaveable{mutableStateOf("Сфагнум")}
    var location by rememberSaveable{mutableStateOf("Теплица / контейнер для укоренения")}
    var notes by rememberSaveable{mutableStateOf("")}
    val a=pups.toIntOrNull()
    val b=corms.toIntOrNull()
    val valid=a!=null && b!=null && a in 0..100 && b in 0..100 &&
        (a+b) in 1..100 && atDate11(date)!=null
    AlertDialog(onDismissRequest=onClose,
        title={Text("Отделение деток и клубней")},
        text={
            Column(Modifier.heightIn(max=510.dp).verticalScroll(rememberScrollState()),
                verticalArrangement=Arrangement.spacedBy(10.dp)){
                Text("Источник: "+parent.plantName+" · "+parent.code,
                    style=MaterialTheme.typography.titleSmall)
                Lab11DateField("Дата отделения",date,{date=it})
                Field("Количество деток",pups,{pups=it})
                Field("Количество клубней",corms,{corms=it})
                PickOne("Этап деток",listOf("Укоренение","Укоренено",
                    "Адаптация","Доращивание","В работе"),
                    pupStage,{pupStage=it})
                Field("Среда / субстрат",substrate,{substrate=it})
                Field("Место: теплица, лоток, контейнер",location,{location=it})
                Field("Примечание",notes,{notes=it},single=false)
                Text("На каждую детку и каждый клубень создаётся отдельный "+
                    "экземпляр с QR и происхождением. Материнское растение не списывается.",
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(valid){
                    Text("Будет создано "+(a!!+b!!)+" объектов: "+
                        a+" деток, "+b+" клубней.",
                        style=MaterialTheme.typography.titleSmall)
                }else Text("Укажи от 1 до 100 деток и клубней суммарно и выбери дату.",
                    color=MaterialTheme.colorScheme.error,
                    style=MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton={Button(enabled=valid,onClick={
            try{
                val result=db.separateOffshoots13(parent.id,requireNotNull(a),
                    requireNotNull(b),requireNotNull(atDate11(date)),
                    pupStage,substrate,location,notes)
                onSaved(result)
            }catch(e:Exception){onError(e.message?:"Не удалось записать размножение")}
        }){Text("Создать дочерние растения")}},
        dismissButton={TextButton(onClick=onClose){Text("Отмена")}}
    )
}
