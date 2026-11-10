package ru.dikoveriya.lab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class Container(val id: String, val plant: String, val passage: Int, val status: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LabScreen() }
    }
}

@Composable
private fun LabScreen() {
    var counter by remember { mutableIntStateOf(1) }
    val containers = remember { mutableStateListOf<Container>() }
    var plant by remember { mutableStateOf("Alocasia Black Velvet Aurea") }
    var showForm by remember { mutableStateOf(false) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    val active = containers.count { it.status == "В культуре" }
    MaterialTheme(colorScheme = lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF255B47))) {
        Scaffold(floatingActionButton = {
            FloatingActionButton(onClick = { showForm = true }) { Text("+", style = MaterialTheme.typography.headlineMedium) }
        }) { padding ->
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item {
                    Text("ДИКОВЕРИЯ", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text("Моя лаборатория", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Card(shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.fillMaxWidth().padding(18.dp)) {
                            Text("Контейнеров в культуре", style = MaterialTheme.typography.bodyMedium)
                            Text("$active", style = MaterialTheme.typography.displaySmall)
                            Text("Тестовая версия 0.1 · данные только в памяти", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                item { Text("Контейнеры", style = MaterialTheme.typography.titleLarge) }
                if (containers.isEmpty()) item { Text("Пока нет контейнеров. Нажми +, чтобы добавить первый.") }
                items(containers, key = { it.id }) { c ->
                    ElevatedCard(onClick = { selectedId = c.id }, shape = RoundedCornerShape(14.dp)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(c.plant, fontWeight = FontWeight.SemiBold)
                            Text("${c.id} · P${c.passage} · ${c.status}")
                        }
                    }
                }
            }
            if (showForm) AlertDialog(
                onDismissRequest = { showForm = false },
                title = { Text("Новый контейнер") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Растение")
                        listOf("Alocasia Black Velvet Aurea", "Alocasia Dragon Scale Mint", "Philodendron Burle Marx Flame").forEach { name ->
                            Row { RadioButton(selected = plant == name, onClick = { plant = name }); Text(name, modifier = Modifier.padding(top = 12.dp)) }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = {
                    containers.add(Container("LAB-%04d".format(counter++), plant, 0, "В культуре"))
                    showForm = false
                }) { Text("Добавить") } },
                dismissButton = { TextButton(onClick = { showForm = false }) { Text("Отмена") } }
            )
            val selected = containers.find { it.id == selectedId }
            if (selected != null) AlertDialog(
                onDismissRequest = { selectedId = null },
                title = { Text(selected.id) },
                text = { Text("${selected.plant}\nПассаж P${selected.passage}\nСтатус: ${selected.status}") },
                confirmButton = { TextButton(onClick = {
                    val i = containers.indexOfFirst { it.id == selected.id }
                    if (i >= 0) containers[i] = selected.copy(status = "Утилизирован")
                    selectedId = null
                }) { Text("Утилизировать") } },
                dismissButton = { TextButton(onClick = { selectedId = null }) { Text("Закрыть") } }
            )
        }
    }
}
