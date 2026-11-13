package ru.dikoveriya.lab

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Real offline settings. Do not display fake cloud sync, printer pairing,
 * or backup status here.
 */
@Composable
internal fun Lab10SettingsScreen(
    darkTheme:Boolean,onDarkTheme:(Boolean)->Unit,
    onBack:()->Unit,
    modifier:Modifier=Modifier
) {
    val context=androidx.compose.ui.platform.LocalContext.current
    val prefs=remember{context.getSharedPreferences("dikoveriya_lab_preferences",0)}
    var laboratoryName by remember { mutableStateOf(prefs.getString("laboratory_name","Диковерия LAB") ?: "Диковерия LAB") }
    var editingName by remember { mutableStateOf(false) }
    var draftName by remember(editingName){mutableStateOf(laboratoryName)}
    var error by remember { mutableStateOf("") }
    Column(modifier.verticalScroll(rememberScrollState())
        .padding(horizontal=16.dp,vertical=13.dp),
        verticalArrangement=Arrangement.spacedBy(13.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){
            IconButton(onClick=onBack){
                Icon(Icons.Outlined.ArrowBack,contentDescription="Назад")
            }
            Text("Настройки",style=MaterialTheme.typography.headlineSmall)
        }
        Lab07BrandHero("Лаборатория",
            "Личные настройки приложения и оформления.",
            "ДИКОВЕРИЯ LAB · НАСТРОЙКИ")
        Text("Профиль",style=MaterialTheme.typography.titleLarge)
        OutlinedCard(onClick={editingName=true},
            modifier=Modifier.fillMaxWidth(),
            shape=RoundedCornerShape(17.dp)){
            Row(Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment=Alignment.CenterVertically){
                Icon(Icons.Outlined.Badge,contentDescription=null)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)){
                    Text("Название лаборатории",
                        style=MaterialTheme.typography.titleMedium)
                    Text(laboratoryName,style=MaterialTheme.typography.bodyMedium,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Outlined.Edit,contentDescription="Изменить название")
            }
        }
        Text("Внешний вид",style=MaterialTheme.typography.titleLarge)
        Card(shape=RoundedCornerShape(17.dp),
            colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)){
            Row(Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment=Alignment.CenterVertically){
                Icon(Icons.Outlined.DarkMode,contentDescription=null)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)){
                    Text("Тёмный ботанический стиль",
                        style=MaterialTheme.typography.titleMedium)
                    Text("Лесной зелёный, латунь и слоновая кость",
                        style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked=darkTheme,onCheckedChange=onDarkTheme)
            }
        }
        Text("Данные и безопасность",style=MaterialTheme.typography.titleLarge)
        Card(shape=RoundedCornerShape(17.dp)){
            Column(Modifier.fillMaxWidth().padding(15.dp),
                verticalArrangement=Arrangement.spacedBy(8.dp)){
                Row(verticalAlignment=Alignment.CenterVertically){
                    Icon(Icons.Outlined.Lock,null)
                    Spacer(Modifier.width(9.dp))
                    Text("Локальная база SQLite",style=MaterialTheme.typography.titleMedium)
                }
                Text("Данные хранятся на этом устройстве. Для переноса и защиты используй раздел «Резервные копии».",
                    style=MaterialTheme.typography.bodyMedium)
                Text("Google Drive и облачная синхронизация не подключены.",
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text("Принтеры",style=MaterialTheme.typography.titleLarge)
        Card(shape=RoundedCornerShape(17.dp)){
            Column(Modifier.fillMaxWidth().padding(15.dp),
                verticalArrangement=Arrangement.spacedBy(7.dp)){
                Row(verticalAlignment=Alignment.CenterVertically){
                    Icon(Icons.Outlined.Print,null)
                    Spacer(Modifier.width(9.dp))
                    Text("NIIMBOT BLE",style=MaterialTheme.typography.titleMedium)
                }
                Text("Печать выполняется в редакторе этикеток контейнера. Совместимость конкретной модели должна быть проверена физически.",
                    style=MaterialTheme.typography.bodySmall)
            }
        }
    }
    if(editingName)AlertDialog(onDismissRequest={editingName=false},
        title={Text("Название лаборатории")},
        text={Column{
            OutlinedTextField(value=draftName,onValueChange={draftName=it.take(80)},
                label={Text("Название")},singleLine=true)
            if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
        }},
        confirmButton={TextButton(onClick={
            if(draftName.trim().length in 2..80){
                laboratoryName=draftName.trim()
                prefs.edit().putString("laboratory_name",laboratoryName).apply()
                error="";editingName=false
            }else error="Введите от 2 до 80 символов"
        }){Text("Сохранить")}},
        dismissButton={TextButton(onClick={editingName=false;error=""}){Text("Отмена")}})
}
