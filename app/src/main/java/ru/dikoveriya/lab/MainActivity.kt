package ru.dikoveriya.lab

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class Plant(val id: Long, val name: String)
private data class Culture(val id: Long, val code: String, val plantId: Long, val plant: String, val count: Int, val status: String, val passage: Int)
private data class Event(val id: Long, val cultureId: Long, val kind: String, val note: String, val createdAt: Long)

private class LabDatabase(context: Context) : SQLiteOpenHelper(context, "dikoveriya_lab.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("PRAGMA foreign_keys=ON")
        db.execSQL("CREATE TABLE plants (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE)")
        db.execSQL("CREATE TABLE cultures (id INTEGER PRIMARY KEY AUTOINCREMENT, code TEXT NOT NULL UNIQUE, plant_id INTEGER NOT NULL REFERENCES plants(id), quantity INTEGER NOT NULL CHECK(quantity >= 0), status TEXT NOT NULL, passage INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE events (id INTEGER PRIMARY KEY AUTOINCREMENT, culture_id INTEGER NOT NULL REFERENCES cultures(id), kind TEXT NOT NULL, note TEXT NOT NULL, created_at INTEGER NOT NULL)")
        listOf("Alocasia Black Velvet Aurea", "Alocasia Dragon Scale Mint", "Philodendron Burle Marx Flame").forEach { name ->
            db.insertOrThrow("plants", null, ContentValues().apply { put("name", name) })
        }
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Future schema upgrades must use non-destructive migrations.
    }
    override fun onConfigure(db: SQLiteDatabase) { super.onConfigure(db); db.setForeignKeyConstraintsEnabled(true) }
    fun plants(): List<Plant> = readableDatabase.rawQuery("SELECT id,name FROM plants ORDER BY name COLLATE NOCASE", null).use { c ->
        buildList { while (c.moveToNext()) add(Plant(c.getLong(0), c.getString(1))) }
    }
    fun cultures(): List<Culture> = readableDatabase.rawQuery("SELECT c.id,c.code,c.plant_id,p.name,c.quantity,c.status,c.passage FROM cultures c JOIN plants p ON p.id=c.plant_id ORDER BY c.id DESC", null).use { c ->
        buildList { while (c.moveToNext()) add(Culture(c.getLong(0),c.getString(1),c.getLong(2),c.getString(3),c.getInt(4),c.getString(5),c.getInt(6))) }
    }
    fun events(id: Long): List<Event> = readableDatabase.rawQuery("SELECT id,culture_id,kind,note,created_at FROM events WHERE culture_id=? ORDER BY id DESC", arrayOf(id.toString())).use { c ->
        buildList { while (c.moveToNext()) add(Event(c.getLong(0),c.getLong(1),c.getString(2),c.getString(3),c.getLong(4))) }
    }
    fun addPlant(name: String) {
        val clean = name.trim()
        require(clean.isNotEmpty() && clean.length <= 120) { "РЈРєР°Р¶Рё РЅР°Р·РІР°РЅРёРµ СЂР°СЃС‚РµРЅРёСЏ (РґРѕ 120 СЃРёРјРІРѕР»РѕРІ)" }
        writableDatabase.insertOrThrow("plants", null, ContentValues().apply { put("name", clean) })
    }
    fun addCulture(plantId: Long, quantity: Int) {
        require(quantity > 0) { "РљРѕР»РёС‡РµСЃС‚РІРѕ РґРѕР»Р¶РЅРѕ Р±С‹С‚СЊ Р±РѕР»СЊС€Рµ РЅСѓР»СЏ" }
        val db = writableDatabase
        db.beginTransaction()
        try {
            val id = db.insertOrThrow("cultures", null, ContentValues().apply {
                put("code", "TMP-${System.nanoTime()}"); put("plant_id", plantId); put("quantity", quantity)
                put("status", "Р’ РєСѓР»СЊС‚СѓСЂРµ"); put("passage", 0); put("created_at", System.currentTimeMillis())
            })
            val code = "LAB-%06d".format(Locale.US, id)
            db.update("cultures", ContentValues().apply { put("code", code) }, "id=?", arrayOf(id.toString()))
            record(db, id, "РЎРѕР·РґР°РЅРёРµ", "РЎРѕР·РґР°РЅ РєРѕРЅС‚РµР№РЅРµСЂ. РљРѕР»РёС‡РµСЃС‚РІРѕ: $quantity")
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun updateCulture(id: Long, quantity: Int, status: String, reason: String) {
        require(quantity >= 0) { "РљРѕР»РёС‡РµСЃС‚РІРѕ РЅРµ РјРѕР¶РµС‚ Р±С‹С‚СЊ РѕС‚СЂРёС†Р°С‚РµР»СЊРЅС‹Рј" }
        require(reason.trim().isNotEmpty()) { "РЈРєР°Р¶Рё РїСЂРёС‡РёРЅСѓ РёР·РјРµРЅРµРЅРёСЏ" }
        val allowed = setOf("Р’ РєСѓР»СЊС‚СѓСЂРµ", "РќР° РєРѕРЅС‚СЂРѕР»Рµ", "РљР°СЂР°РЅС‚РёРЅ", "РЈС‚РёР»РёР·РёСЂРѕРІР°РЅ")
        require(status in allowed) { "РќРµРёР·РІРµСЃС‚РЅС‹Р№ СЃС‚Р°С‚СѓСЃ" }
        val db = writableDatabase
        db.beginTransaction()
        try {
            val old = db.rawQuery("SELECT quantity,status FROM cultures WHERE id=?", arrayOf(id.toString())).use { c ->
                require(c.moveToFirst()) { "РљРѕРЅС‚РµР№РЅРµСЂ РЅРµ РЅР°Р№РґРµРЅ" }
                c.getInt(0) to c.getString(1)
            }
            require(old.second != "РЈС‚РёР»РёР·РёСЂРѕРІР°РЅ") { "РЈС‚РёР»РёР·РёСЂРѕРІР°РЅРЅС‹Р№ РєРѕРЅС‚РµР№РЅРµСЂ Р·Р°РєСЂС‹С‚ РґР»СЏ РёР·РјРµРЅРµРЅРёР№" }
            require(status != "РЈС‚РёР»РёР·РёСЂРѕРІР°РЅ" || quantity == 0) { "Р”Р»СЏ СѓС‚РёР»РёР·Р°С†РёРё РєРѕР»РёС‡РµСЃС‚РІРѕ РґРѕР»Р¶РЅРѕ Р±С‹С‚СЊ СЂР°РІРЅРѕ РЅСѓР»СЋ" }
            require(status == "РЈС‚РёР»РёР·РёСЂРѕРІР°РЅ" || quantity > 0) { "Р”Р»СЏ РїСѓСЃС‚РѕРіРѕ РєРѕРЅС‚РµР№РЅРµСЂР° РІС‹Р±РµСЂРё СѓС‚РёР»РёР·Р°С†РёСЋ" }
            db.update("cultures", ContentValues().apply { put("quantity", quantity); put("status", status) }, "id=?", arrayOf(id.toString()))
            record(db, id, "РР·РјРµРЅРµРЅРёРµ", "РљРѕР»РёС‡РµСЃС‚РІРѕ: ${old.first} в†’ $quantity; СЃС‚Р°С‚СѓСЃ: ${old.second} в†’ $status; РїСЂРёС‡РёРЅР°: ${reason.trim()}")
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun addObservation(id: Long, note: String) {
        require(note.trim().isNotEmpty()) { "РќР°РїРёС€Рё СЂРµР·СѓР»СЊС‚Р°С‚ РѕСЃРјРѕС‚СЂР°" }
        val db = writableDatabase
        db.beginTransaction()
        try {
            record(db, id, "РћСЃРјРѕС‚СЂ", note.trim())
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    private fun record(db: SQLiteDatabase, id: Long, kind: String, note: String) {
        db.insertOrThrow("events", null, ContentValues().apply {
            put("culture_id", id); put("kind", kind); put("note", note); put("created_at", System.currentTimeMillis())
        })
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = LabDatabase(applicationContext)
        setContent { LabScreen(db) }
    }
}

@Composable
private fun LabScreen(db: LabDatabase) {
    var refresh by remember { mutableIntStateOf(0) }
    val plants = remember(refresh) { db.plants() }
    val cultures = remember(refresh) { db.cultures() }
    var tab by remember { mutableStateOf("Р“Р»Р°РІРЅР°СЏ") }
    var addPlant by remember { mutableStateOf(false) }
    var addCulture by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Long?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    fun runAction(action: () -> Unit) {
        try { action(); refresh++; error = null } catch (e: Exception) { error = e.message ?: "РћС€РёР±РєР° СЃРѕС…СЂР°РЅРµРЅРёСЏ" }
    }
    val active = cultures.count { it.status == "Р’ РєСѓР»СЊС‚СѓСЂРµ" }
    val attention = cultures.count { it.status == "РљР°СЂР°РЅС‚РёРЅ" || it.status == "РќР° РєРѕРЅС‚СЂРѕР»Рµ" }
    MaterialTheme(colorScheme = lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF255B47))) {
        Scaffold(bottomBar = {
            NavigationBar {
                listOf("Р“Р»Р°РІРЅР°СЏ", "Р Р°СЃС‚РµРЅРёСЏ", "Р Р°Р±РѕС‚Р°", "РђРЅР°Р»РёС‚РёРєР°", "Р•С‰С‘").forEach { name ->
                    NavigationBarItem(selected = tab == name, onClick = { tab = name }, icon = { Text(when(name) { "Р“Р»Р°РІРЅР°СЏ" -> "вЊ‚"; "Р Р°СЃС‚РµРЅРёСЏ" -> "вњї"; "Р Р°Р±РѕС‚Р°" -> "вњ“"; "РђРЅР°Р»РёС‚РёРєР°" -> "в–Ґ"; else -> "в°" }) }, label = { Text(name) })
                }
            }
        }) { insets ->
            LazyColumn(Modifier.fillMaxSize().padding(insets).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 20.dp)) {
                item { Text("Р”РРљРћР’Р•Р РРЇ LAB", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge) }
                item { Text(tab, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
                when(tab) {
                    "Р“Р»Р°РІРЅР°СЏ" -> {
                        item { ElevatedCard { Column(Modifier.fillMaxWidth().padding(16.dp)) { Text("РљРѕРЅС‚РµР№РЅРµСЂРѕРІ РІ РєСѓР»СЊС‚СѓСЂРµ"); Text("$active", style = MaterialTheme.typography.displayMedium); Text("РўСЂРµР±СѓСЋС‚ РІРЅРёРјР°РЅРёСЏ: $attention") } } }
                        item { Button(onClick = { addCulture = true }, modifier = Modifier.fillMaxWidth()) { Text("+ РќРѕРІС‹Р№ РєРѕРЅС‚РµР№РЅРµСЂ") } }
                        item { OutlinedButton(onClick = { addPlant = true }, modifier = Modifier.fillMaxWidth()) { Text("+ Р”РѕР±Р°РІРёС‚СЊ СЂР°СЃС‚РµРЅРёРµ РІ РєР°С‚Р°Р»РѕРі") } }
                        item { Text("РџРѕСЃР»РµРґРЅРёРµ РєРѕРЅС‚РµР№РЅРµСЂС‹", style = MaterialTheme.typography.titleLarge) }
                        items(cultures.take(10), key = { it.id }) { c -> CultureCard(c) { selected = c.id } }
                    }
                    "Р Р°СЃС‚РµРЅРёСЏ" -> {
                        item { Button(onClick = { addPlant = true }) { Text("+ Р”РѕР±Р°РІРёС‚СЊ СЂР°СЃС‚РµРЅРёРµ") } }
                        items(plants, key = { it.id }) { p -> ElevatedCard { Text(p.name, Modifier.fillMaxWidth().padding(16.dp)) } }
                    }
                    "Р Р°Р±РѕС‚Р°" -> {
                        item { Button(onClick = { addCulture = true }) { Text("+ РќРѕРІС‹Р№ РєРѕРЅС‚РµР№РЅРµСЂ") } }
                        if (cultures.isEmpty()) item { Text("РљРѕРЅС‚РµР№РЅРµСЂРѕРІ РїРѕРєР° РЅРµС‚") }
                        items(cultures, key = { it.id }) { c -> CultureCard(c) { selected = c.id } }
                    }
                    "РђРЅР°Р»РёС‚РёРєР°" -> {
                        item { Text("Р’СЃРµРіРѕ РєРѕРЅС‚РµР№РЅРµСЂРѕРІ: ${cultures.size}") }
                        item { Text("Р’ РєСѓР»СЊС‚СѓСЂРµ: $active") }
                        item { Text("РќР° РєРѕРЅС‚СЂРѕР»Рµ РёР»Рё РєР°СЂР°РЅС‚РёРЅРµ: $attention") }
                        item { Text("РЈС‚РёР»РёР·РёСЂРѕРІР°РЅРѕ: ${cultures.count { it.status == "РЈС‚РёР»РёР·РёСЂРѕРІР°РЅ" }}") }
                        item { Text("Р Р°СЃС€РёСЂРµРЅРЅР°СЏ Р°РЅР°Р»РёС‚РёРєР° СЂРµС†РµРїС‚СѓСЂ Рё СЂР°Р·РјРЅРѕР¶РµРЅРёСЏ Р±СѓРґРµС‚ РїРѕРґРєР»СЋС‡РµРЅР° РїРѕСЃР»Рµ РІРЅРµРґСЂРµРЅРёСЏ СѓС‡С‘С‚Р° РїР°СЂС‚РёР№ РїРёС‚Р°С‚РµР»СЊРЅС‹С… СЃСЂРµРґ.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    else -> {
                        item { Text("Р’РµСЂСЃРёСЏ 0.2.0 В· РґР°РЅРЅС‹Рµ СЃРѕС…СЂР°РЅСЏСЋС‚СЃСЏ РІ Р»РѕРєР°Р»СЊРЅРѕР№ Р±Р°Р·Рµ С‚РµР»РµС„РѕРЅР°") }
                        item { Text("РџРѕРєР° РЅРµ СЂРµР°Р»РёР·РѕРІР°РЅС‹: РѕР±Р»Р°С‡РЅР°СЏ СЃРёРЅС…СЂРѕРЅРёР·Р°С†РёСЏ, СЂРµР·РµСЂРІРЅС‹Рµ РєРѕРїРёРё, QR Рё СЂРµС†РµРїС‚СѓСЂС‹. РќРµ СѓРґР°Р»СЏР№ РїСЂРёР»РѕР¶РµРЅРёРµ Р±РµР· СЌРєСЃРїРѕСЂС‚Р° РґР°РЅРЅС‹С… РІ Р±СѓРґСѓС‰РµР№ РІРµСЂСЃРёРё.") }
                    }
                }
            }
            if (addPlant) {
                var name by remember { mutableStateOf("") }
                AlertDialog(onDismissRequest = { addPlant = false }, title = { Text("РќРѕРІРѕРµ СЂР°СЃС‚РµРЅРёРµ") }, text = {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("РќР°Р·РІР°РЅРёРµ") }, singleLine = true)
                }, confirmButton = { TextButton(onClick = { runAction { db.addPlant(name) }; if (error == null) addPlant = false }) { Text("РЎРѕС…СЂР°РЅРёС‚СЊ") } }, dismissButton = { TextButton(onClick = { addPlant = false }) { Text("РћС‚РјРµРЅР°") } })
            }
            if (addCulture) {
                var plantId by remember { mutableStateOf(plants.firstOrNull()?.id) }
                var quantity by remember { mutableStateOf("1") }
                AlertDialog(onDismissRequest = { addCulture = false }, title = { Text("РќРѕРІС‹Р№ РєРѕРЅС‚РµР№РЅРµСЂ") }, text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Р Р°СЃС‚РµРЅРёРµ")
                        plants.forEach { p -> Row { RadioButton(selected = plantId == p.id, onClick = { plantId = p.id }); Text(p.name, Modifier.padding(top = 12.dp)) } }
                        OutlinedTextField(value = quantity, onValueChange = { quantity = it.filter(Char::isDigit) }, label = { Text("РљРѕР»РёС‡РµСЃС‚РІРѕ СЂР°СЃС‚РµРЅРёР№") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                    }
                }, confirmButton = { TextButton(onClick = { runAction { db.addCulture(requireNotNull(plantId) { "Р’С‹Р±РµСЂРё СЂР°СЃС‚РµРЅРёРµ" }, quantity.toIntOrNull() ?: 0) }; if (error == null) addCulture = false }) { Text("РЎРѕР·РґР°С‚СЊ") } }, dismissButton = { TextButton(onClick = { addCulture = false }) { Text("РћС‚РјРµРЅР°") } })
            }
            val current = cultures.find { it.id == selected }
            if (current != null) CultureDetails(current, db.events(current.id), onDismiss = { selected = null }, onSave = { count, status, reason ->
                runAction { db.updateCulture(current.id, count, status, reason) }
                if (error == null) selected = null
            }, onObserve = { note ->
                runAction { db.addObservation(current.id, note) }
                if (error == null) selected = null
            })
            if (error != null) AlertDialog(onDismissRequest = { error = null }, title = { Text("РќРµ СѓРґР°Р»РѕСЃСЊ СЃРѕС…СЂР°РЅРёС‚СЊ") }, text = { Text(error.orEmpty()) }, confirmButton = { TextButton(onClick = { error = null }) { Text("РџРѕРЅСЏС‚РЅРѕ") } })
        }
    }
}

@Composable
private fun CultureCard(c: Culture, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(c.plant, fontWeight = FontWeight.SemiBold)
            Text("${c.code} В· ${c.status}")
            Text("Р Р°СЃС‚РµРЅРёР№: ${c.count}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CultureDetails(c: Culture, events: List<Event>, onDismiss: () -> Unit, onSave: (Int, String, String) -> Unit, onObserve: (String) -> Unit) {
    var count by remember(c.id) { mutableStateOf(c.count.toString()) }
    var status by remember(c.id) { mutableStateOf(c.status) }
    var reason by remember(c.id) { mutableStateOf("") }
    var mode by remember(c.id) { mutableStateOf("РСЃС‚РѕСЂРёСЏ") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("${c.plant}\n${c.code}") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("РЎРѕСЃС‚РѕСЏРЅРёРµ: ${c.status} В· Р Р°СЃС‚РµРЅРёР№: ${c.count}")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("РСЃС‚РѕСЂРёСЏ", "РћСЃРјРѕС‚СЂ", "РР·РјРµРЅРёС‚СЊ").forEach { item ->
                    FilterChip(selected = mode == item, onClick = { mode = item }, label = { Text(item) })
                }
            }
            when(mode) {
                "РСЃС‚РѕСЂРёСЏ" -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    events.take(8).forEach { e ->
                        Text("${SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(e.createdAt))} В· ${e.kind}\n${e.note}", style = MaterialTheme.typography.bodySmall)
                        HorizontalDivider()
                    }
                }
                "РћСЃРјРѕС‚СЂ" -> OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("Р§С‚Рѕ Р·Р°РјРµС‚РёР»?") }, minLines = 2)
                else -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (c.status == "РЈС‚РёР»РёР·РёСЂРѕРІР°РЅ") Text("РљРѕРЅС‚РµР№РЅРµСЂ Р·Р°РєСЂС‹С‚. РСЃС‚РѕСЂРёСЏ РґРѕСЃС‚СѓРїРЅР° РґР»СЏ РїСЂРѕСЃРјРѕС‚СЂР°.")
                    else {
                        OutlinedTextField(value = count, onValueChange = { count = it.filter(Char::isDigit) }, label = { Text("РљРѕР»РёС‡РµСЃС‚РІРѕ") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        listOf("Р’ РєСѓР»СЊС‚СѓСЂРµ", "РќР° РєРѕРЅС‚СЂРѕР»Рµ", "РљР°СЂР°РЅС‚РёРЅ", "РЈС‚РёР»РёР·РёСЂРѕРІР°РЅ").forEach { option ->
                            Row { RadioButton(selected = status == option, onClick = { status = option; if (option == "РЈС‚РёР»РёР·РёСЂРѕРІР°РЅ") count = "0" }); Text(option, Modifier.padding(top = 12.dp)) }
                        }
                        OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("РџСЂРёС‡РёРЅР° РёР·РјРµРЅРµРЅРёСЏ") })
                    }
                }
            }
        }
    }, confirmButton = {
        when(mode) {
            "РћСЃРјРѕС‚СЂ" -> TextButton(onClick = { onObserve(reason) }) { Text("РЎРѕС…СЂР°РЅРёС‚СЊ РѕСЃРјРѕС‚СЂ") }
            "РР·РјРµРЅРёС‚СЊ" -> if (c.status != "РЈС‚РёР»РёР·РёСЂРѕРІР°РЅ") TextButton(onClick = { onSave(count.toIntOrNull() ?: -1, status, reason) }) { Text("РЎРѕС…СЂР°РЅРёС‚СЊ") }
            else -> {}
        }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("Р—Р°РєСЂС‹С‚СЊ") } })
}
