package ru.dikoveriya.lab

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private val ingredientCategories = listOf("Базовая среда", "Углеводы", "Загуститель", "Регулятор роста", "Минералы", "Витамины", "Добавка", "Другое")
private val ingredientUnits = listOf("г/л", "мг/л", "мл/л", "мг/л из раствора")

@Composable
fun AddIngredientDialog(onClose: () -> Unit, onSave: (String, String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Добавка") }
    var unit by remember { mutableStateOf("мг/л") }
    var notes by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Новый компонент") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Название") }, singleLine = true)
                PickOne("Категория", ingredientCategories, category, { category = it })
                PickOne("Единица для рецептуры", ingredientUnits, unit, { unit = it })
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Описание, источник, особенности") }, minLines = 2)
                Text("Дозировка не назначается автоматически. Для каждой рецептуры она задаётся по проверенному протоколу.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, category, unit, notes) }, enabled = name.trim().length >= 2) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Отмена") } }
    )
}
