package ru.dikoveriya.lab

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.File

/** User-invoked updater. Never downloads or installs in the background. */
@Composable
internal fun Lab11UpdateDialog(onClose:()->Unit){
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val currentCode=remember{Lab11Updater.installedCode(context)}
    val currentName=remember{Lab11Updater.installedVersion(context)}
    var retry by remember{mutableIntStateOf(0)}
    var phase by remember{mutableStateOf("checking")}
    var release by remember{mutableStateOf<Lab11PublishedUpdate?>(null)}
    var file by remember{mutableStateOf<File?>(null)}
    var progress by remember{mutableIntStateOf(0)}
    var error by remember{mutableStateOf("")}
    var permissionHint by remember{mutableStateOf(false)}

    LaunchedEffect(retry){
        phase="checking";error="";file=null;permissionHint=false
        try{
            val result=Lab11Updater.latest()
            release=result
            phase=if(result.code>currentCode)"available" else "current"
        }catch(c:CancellationException){throw c}
        catch(e:Exception){
            phase="error"
            error=e.message?:"Не удалось проверить GitHub Releases"
        }
    }
    AlertDialog(
        onDismissRequest={if(phase!="downloading")onClose()},
        title={Text("Обновление Диковерия LAB")},
        text={
            Column(
                Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement=Arrangement.spacedBy(12.dp)
            ){
                Text("Установлено: "+currentName+" · сборка "+currentCode,
                    style=MaterialTheme.typography.bodyMedium)
                when(phase){
                    "checking"->{
                        LinearProgressIndicator(modifier=Modifier.fillMaxWidth())
                        Text("Проверяем опубликованные версии GitHub...")
                    }
                    "current"->{
                        Text("Установлена актуальная опубликованная сборка.",
                            color=MaterialTheme.colorScheme.primary)
                        release?.let{Text("GitHub: "+it.tag+" · сборка "+it.code)}
                    }
                    "available","downloading","ready"->{
                        release?.let{r->
                            Text("Доступна: "+r.tag+" · сборка "+r.code,
                                style=MaterialTheme.typography.titleMedium)
                            Text("Размер APK: "+(r.size/1024/1024)+" МБ")
                            if(r.notes.isNotBlank())Text(r.notes.take(400),
                                style=MaterialTheme.typography.bodySmall)
                        }
                        if(phase=="downloading"){
                            LinearProgressIndicator(progress={progress/100f},
                                modifier=Modifier.fillMaxWidth())
                            Text("Скачано: "+progress+"%")
                        }
                        if(phase=="ready"){
                            Text("APK скачан. Проверены SHA-256, название пакета, "+
                                "номер сборки и сертификат подписи.",
                                color=MaterialTheme.colorScheme.primary)
                        }
                        if(permissionHint){
                            Text("Android открыл настройки. Разрешите установку из "+
                                "этого источника, вернитесь сюда и снова нажмите «Установить».",
                                color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    "error"->Text(error,color=MaterialTheme.colorScheme.error)
                }
                Text("Установка выполняется штатным установщиком Android только "+
                    "после вашего подтверждения. Данные лаборатории не удаляются.",
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton={
            when(phase){
                "available"->Button(onClick={
                    val selected=release?:return@Button
                    phase="downloading";progress=0;error=""
                    scope.launch{
                        try{
                            file=Lab11Updater.download(context,selected){progress=it}
                            phase="ready"
                        }catch(c:CancellationException){throw c}
                        catch(e:Exception){
                            phase="error";error=e.message?:"Не удалось скачать обновление"
                        }
                    }
                }){Text("Скачать APK")}
                "ready"->Button(onClick={
                    try{
                        permissionHint=!Lab11Updater.requestInstall(
                            context,requireNotNull(file))
                    }catch(e:Exception){
                        phase="error";error=e.message?:"Не удалось открыть установщик"
                    }
                }){Text("Установить")}
                "error","current"->TextButton(onClick={retry++}){
                    Text("Проверить снова")
                }
                else->Unit
            }
        },
        dismissButton={
            TextButton(onClick=onClose,enabled=phase!="downloading"){
                Text("Закрыть")
            }
        }
    )
}
