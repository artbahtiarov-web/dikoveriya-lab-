package ru.dikoveriya.lab

import android.content.ContentValues

/** Names and classification only. No scientific doses are silently assumed. */
data class IngredientEntry(val id: Long, val name: String, val category: String, val unit: String, val notes: String)

fun LabDatabase.ingredients(): List<IngredientEntry> = readableDatabase.rawQuery(
    "SELECT id,name,category,unit,notes FROM ingredient_catalog WHERE archived=0 ORDER BY category COLLATE NOCASE,name COLLATE NOCASE",
    null
).use { cursor ->
    buildList {
        while (cursor.moveToNext()) add(
            IngredientEntry(cursor.getLong(0), cursor.getString(1), cursor.getString(2), cursor.getString(3), cursor.getString(4))
        )
    }
}

fun LabDatabase.addIngredient(name: String, category: String, unit: String, notes: String) {
    val title = name.trim()
    require(title.length in 2..140) { "Укажи название компонента (от 2 до 140 символов)" }
    require(category in listOf("Базовая среда", "Углеводы", "Загуститель", "Регулятор роста", "Минералы", "Витамины", "Добавка", "Другое")) { "Выбери категорию" }
    require(unit in listOf("г/л", "мг/л", "мл/л", "мг/л из раствора")) { "Выбери единицу измерения" }
    val values = ContentValues().apply {
        put("name", title)
        put("category", category)
        put("unit", unit)
        put("notes", notes.trim().take(1000))
    }
    writableDatabase.insertOrThrow("ingredient_catalog", null, values)
}

fun LabDatabase.archiveIngredient(id: Long) {
    val values = ContentValues().apply { put("archived", 1) }
    require(writableDatabase.update("ingredient_catalog", values, "id=?", arrayOf(id.toString())) == 1) { "Компонент не найден" }
}
