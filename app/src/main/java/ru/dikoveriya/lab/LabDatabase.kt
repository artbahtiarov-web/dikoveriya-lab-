package ru.dikoveriya.lab

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import java.io.File
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID
import java.security.MessageDigest

/** Local source of truth. Every multi-record operation is one SQLite transaction. */
class LabDatabase(context: Context) : SQLiteOpenHelper(context, "dikoveriya_lab.db", null, 10) {
    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE plants (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE, description TEXT NOT NULL DEFAULT '')")
        db.execSQL("CREATE TABLE mother_plants (id INTEGER PRIMARY KEY AUTOINCREMENT, code TEXT NOT NULL UNIQUE, plant_id INTEGER NOT NULL REFERENCES plants(id), origin TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'Активен', notes TEXT NOT NULL DEFAULT '', created_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE cultures (id INTEGER PRIMARY KEY AUTOINCREMENT, code TEXT NOT NULL UNIQUE, plant_id INTEGER NOT NULL REFERENCES plants(id), quantity INTEGER NOT NULL CHECK(quantity >= 0), status TEXT NOT NULL, passage INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL, stage TEXT NOT NULL DEFAULT 'В пробирке', location TEXT NOT NULL DEFAULT '', medium_batch_id INTEGER REFERENCES batches(id), mother_id INTEGER REFERENCES mother_plants(id), notes TEXT NOT NULL DEFAULT '', updated_at INTEGER NOT NULL DEFAULT 0, origin_state TEXT NOT NULL DEFAULT 'Не установлено', quantity_kind TEXT NOT NULL DEFAULT 'Не уточнено', medium_recipe_id INTEGER REFERENCES recipes(id))")
        db.execSQL("CREATE TABLE events (id INTEGER PRIMARY KEY AUTOINCREMENT, culture_id INTEGER NOT NULL REFERENCES cultures(id), kind TEXT NOT NULL, note TEXT NOT NULL, created_at INTEGER NOT NULL, before_qty INTEGER, after_qty INTEGER, medium_batch_id INTEGER, event_group TEXT, occurred_at INTEGER)")
        createExtra(db)
        LabCoreV8.create(db)
        LabCoreV9.create(db)
        LabCoreV10.create(db)
        listOf("Alocasia Black Velvet Aurea", "Alocasia Dragon Scale Mint", "Philodendron Burle Marx Flame").forEach { n ->
            db.insertOrThrow("plants", null, cv("name" to n))
        }
    }
    private fun createExtra(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS culture_measurements (id INTEGER PRIMARY KEY AUTOINCREMENT, culture_id INTEGER NOT NULL REFERENCES cultures(id), recipe_id INTEGER REFERENCES recipes(id), observed_at INTEGER NOT NULL, explants INTEGER CHECK(explants>=0), shoots INTEGER CHECK(shoots>=0), plants INTEGER CHECK(plants>=0), contaminated INTEGER CHECK(contaminated>=0), notes TEXT NOT NULL DEFAULT '', record_kind TEXT NOT NULL DEFAULT 'observation')")
        db.execSQL("CREATE TABLE IF NOT EXISTS culture_recipe_history (id INTEGER PRIMARY KEY AUTOINCREMENT, culture_id INTEGER NOT NULL REFERENCES cultures(id), recipe_id INTEGER NOT NULL REFERENCES recipes(id), applied_at INTEGER NOT NULL, note TEXT NOT NULL DEFAULT '')")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_measure_culture ON culture_measurements(culture_id,observed_at)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_measure_recipe ON culture_measurements(recipe_id,observed_at)")
        db.execSQL("CREATE TABLE IF NOT EXISTS ingredient_catalog (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE, category TEXT NOT NULL, unit TEXT NOT NULL, notes TEXT NOT NULL DEFAULT '', archived INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("INSERT OR IGNORE INTO ingredient_catalog(name,category,unit,notes) VALUES (?,?,?,?)", arrayOf("Babio MS (готовая смесь)","Базовая среда","г/л","Проверь этикетку конкретной партии: комплексная смесь может уже содержать сахарозу и агар."))
        db.execSQL("INSERT OR IGNORE INTO ingredient_catalog(name,category,unit,notes) VALUES (?,?,?,?)", arrayOf("Сахароза","Углеводы","г/л","Если уже входит в готовую смесь, не добавляй повторно."))
        db.execSQL("INSERT OR IGNORE INTO ingredient_catalog(name,category,unit,notes) VALUES (?,?,?,?)", arrayOf("Агар","Загуститель","г/л","Если уже входит в готовую смесь, не добавляй повторно."))
        db.execSQL("INSERT OR IGNORE INTO ingredient_catalog(name,category,unit,notes) VALUES (?,?,?,?)", arrayOf("6-БАП (6-бензиламинопурин)","Регулятор роста","мг/л","Цитокинин; дозировку выбирай по протоколу."))
        db.execSQL("INSERT OR IGNORE INTO ingredient_catalog(name,category,unit,notes) VALUES (?,?,?,?)", arrayOf("NAA (НУК)","Регулятор роста","мг/л","Ауксин; дозировку выбирай по протоколу."))
        db.execSQL("INSERT OR IGNORE INTO ingredient_catalog(name,category,unit,notes) VALUES (?,?,?,?)", arrayOf("IBA (ИМК)","Регулятор роста","мг/л","Ауксин; дозировку выбирай по протоколу."))
        db.execSQL("INSERT OR IGNORE INTO ingredient_catalog(name,category,unit,notes) VALUES (?,?,?,?)", arrayOf("Активированный уголь","Добавка","г/л","Может связывать регуляторы роста и другие вещества."))
        db.execSQL("INSERT OR IGNORE INTO ingredient_catalog(name,category,unit,notes) VALUES (?,?,?,?)", arrayOf("Мио-инозитол","Витамины","мг/л",""))
        db.execSQL("INSERT OR IGNORE INTO ingredient_catalog(name,category,unit,notes) VALUES (?,?,?,?)", arrayOf("Тиамин (B1)","Витамины","мг/л",""))
        db.execSQL("INSERT OR IGNORE INTO ingredient_catalog(name,category,unit,notes) VALUES (?,?,?,?)", arrayOf("Железо-ЭДТА","Минералы","мг/л","Учитывай соли железа уже входящие в базовую среду."))
        db.execSQL("INSERT OR IGNORE INTO ingredient_catalog(name,category,unit,notes) VALUES (?,?,?,?)", arrayOf("Кокосовая вода","Добавка","мл/л","Состав натурального материала изменчив."))
        db.execSQL("CREATE TABLE IF NOT EXISTS recipe_imports (sha256 TEXT PRIMARY KEY, imported_at INTEGER NOT NULL, description TEXT NOT NULL DEFAULT '')")
        db.execSQL("CREATE TABLE IF NOT EXISTS culture_photos (id INTEGER PRIMARY KEY AUTOINCREMENT, culture_id INTEGER NOT NULL REFERENCES cultures(id), filename TEXT NOT NULL UNIQUE, caption TEXT NOT NULL DEFAULT '', created_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS lineage (id INTEGER PRIMARY KEY AUTOINCREMENT, parent_culture_id INTEGER NOT NULL REFERENCES cultures(id), child_culture_id INTEGER NOT NULL UNIQUE REFERENCES cultures(id), used_qty INTEGER NOT NULL, resulting_qty INTEGER NOT NULL, event_group TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS recipes (id INTEGER PRIMARY KEY AUTOINCREMENT, family TEXT NOT NULL, version INTEGER NOT NULL, name TEXT NOT NULL, purpose TEXT NOT NULL, plant_id INTEGER REFERENCES plants(id), status TEXT NOT NULL DEFAULT 'Черновик', notes TEXT NOT NULL DEFAULT '', created_at INTEGER NOT NULL, UNIQUE(family,version))")
        db.execSQL("CREATE TABLE IF NOT EXISTS recipe_parts (id INTEGER PRIMARY KEY AUTOINCREMENT, recipe_id INTEGER NOT NULL REFERENCES recipes(id), name TEXT NOT NULL, per_l REAL NOT NULL CHECK(per_l > 0), unit TEXT NOT NULL, has_sugar INTEGER NOT NULL DEFAULT 0, has_agar INTEGER NOT NULL DEFAULT 0, stock_mg_ml REAL NOT NULL DEFAULT 0, sugar_g_l REAL, agar_g_l REAL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS batches (id INTEGER PRIMARY KEY AUTOINCREMENT, code TEXT NOT NULL UNIQUE, recipe_id INTEGER NOT NULL REFERENCES recipes(id), volume_ml INTEGER NOT NULL CHECK(volume_ml > 0), prepared_at INTEGER NOT NULL, state TEXT NOT NULL DEFAULT 'Готова', actual_json TEXT NOT NULL, ph TEXT NOT NULL DEFAULT '', sterilization TEXT NOT NULL DEFAULT '', notes TEXT NOT NULL DEFAULT '')")
        db.execSQL("CREATE TABLE IF NOT EXISTS assessments (id INTEGER PRIMARY KEY AUTOINCREMENT, culture_id INTEGER NOT NULL REFERENCES cultures(id), batch_id INTEGER REFERENCES batches(id), starting_qty INTEGER NOT NULL, healthy_qty INTEGER NOT NULL, contaminated_qty INTEGER NOT NULL DEFAULT 0, observation_days INTEGER NOT NULL, note TEXT NOT NULL DEFAULT '', observed_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS tasks (id INTEGER PRIMARY KEY AUTOINCREMENT, culture_id INTEGER REFERENCES cultures(id), title TEXT NOT NULL, due_at INTEGER NOT NULL, done INTEGER NOT NULL DEFAULT 0, note TEXT NOT NULL DEFAULT '')")
        db.execSQL("CREATE TABLE IF NOT EXISTS materials (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE, unit TEXT NOT NULL, quantity REAL NOT NULL CHECK(quantity >= 0), min_quantity REAL NOT NULL DEFAULT 0, cost_per_unit REAL NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE IF NOT EXISTS material_moves (id INTEGER PRIMARY KEY AUTOINCREMENT, material_id INTEGER NOT NULL REFERENCES materials(id), delta REAL NOT NULL, note TEXT NOT NULL, created_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS equipment (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, location TEXT NOT NULL DEFAULT '', state TEXT NOT NULL DEFAULT 'Исправно', next_check INTEGER, notes TEXT NOT NULL DEFAULT '')")
        db.execSQL("CREATE TABLE IF NOT EXISTS incidents (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, note TEXT NOT NULL, created_at INTEGER NOT NULL, state TEXT NOT NULL DEFAULT 'Открыт', culture_id INTEGER REFERENCES cultures(id), operation_id TEXT)")
        db.execSQL("CREATE TABLE IF NOT EXISTS experiments (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, hypothesis TEXT NOT NULL, started_at INTEGER NOT NULL, state TEXT NOT NULL DEFAULT 'Идёт')")
        db.execSQL("CREATE TABLE IF NOT EXISTS experiment_cultures (experiment_id INTEGER NOT NULL REFERENCES experiments(id), culture_id INTEGER NOT NULL REFERENCES cultures(id), group_name TEXT NOT NULL, PRIMARY KEY(experiment_id,culture_id))")
        db.execSQL("CREATE TABLE IF NOT EXISTS sales (id INTEGER PRIMARY KEY AUTOINCREMENT, culture_id INTEGER NOT NULL REFERENCES cultures(id), quantity INTEGER NOT NULL CHECK(quantity > 0), price REAL NOT NULL CHECK(price >= 0), sold_at INTEGER NOT NULL, note TEXT NOT NULL DEFAULT '')")
        db.execSQL("CREATE TABLE IF NOT EXISTS lab_operations (id TEXT PRIMARY KEY, kind TEXT NOT NULL, occurred_at INTEGER NOT NULL, recorded_at INTEGER NOT NULL, note TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'Проведена', reversal_of TEXT REFERENCES lab_operations(id))")
        db.execSQL("CREATE TABLE IF NOT EXISTS operation_changes (operation_id TEXT NOT NULL REFERENCES lab_operations(id), culture_id INTEGER NOT NULL REFERENCES cultures(id), before_qty INTEGER, after_qty INTEGER NOT NULL CHECK(after_qty >= 0), before_status TEXT, after_status TEXT NOT NULL, created INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(operation_id,culture_id))")
        db.execSQL("CREATE TABLE IF NOT EXISTS culture_links (parent_culture_id INTEGER NOT NULL REFERENCES cultures(id), child_culture_id INTEGER NOT NULL REFERENCES cultures(id), used_qty INTEGER NOT NULL CHECK(used_qty > 0), result_qty INTEGER NOT NULL CHECK(result_qty > 0), event_group TEXT NOT NULL, cancelled INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(parent_culture_id,child_culture_id))")
        db.execSQL("CREATE TABLE IF NOT EXISTS incident_links (incident_id INTEGER NOT NULL REFERENCES incidents(id), culture_id INTEGER NOT NULL REFERENCES cultures(id), action TEXT NOT NULL DEFAULT 'Осмотр', PRIMARY KEY(incident_id,culture_id))")
        db.execSQL("CREATE TABLE IF NOT EXISTS incident_followups (id INTEGER PRIMARY KEY AUTOINCREMENT, incident_id INTEGER NOT NULL REFERENCES incidents(id), status TEXT NOT NULL, note TEXT NOT NULL, occurred_at INTEGER NOT NULL, recorded_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS operation_losses (id INTEGER PRIMARY KEY AUTOINCREMENT, operation_id TEXT NOT NULL REFERENCES lab_operations(id), culture_id INTEGER NOT NULL REFERENCES cultures(id), quantity INTEGER NOT NULL CHECK(quantity > 0), reason TEXT NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_links_child ON culture_links(child_culture_id,cancelled)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_op_changes_culture ON operation_changes(culture_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_culture_plant ON cultures(plant_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_culture_batch ON cultures(medium_batch_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_events_culture ON events(culture_id,created_at)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_assessment_batch ON assessments(batch_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_tasks_due ON tasks(done,due_at)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 6) createExtra(db)
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE plants ADD COLUMN description TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE cultures ADD COLUMN stage TEXT NOT NULL DEFAULT 'В пробирке'")
            db.execSQL("ALTER TABLE cultures ADD COLUMN location TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE cultures ADD COLUMN medium_batch_id INTEGER REFERENCES batches(id)")
            db.execSQL("ALTER TABLE cultures ADD COLUMN mother_id INTEGER")
            db.execSQL("ALTER TABLE cultures ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE cultures ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE events ADD COLUMN before_qty INTEGER")
            db.execSQL("ALTER TABLE events ADD COLUMN after_qty INTEGER")
            db.execSQL("ALTER TABLE events ADD COLUMN medium_batch_id INTEGER")
            db.execSQL("ALTER TABLE events ADD COLUMN event_group TEXT")
            db.execSQL("CREATE TABLE IF NOT EXISTS mother_plants (id INTEGER PRIMARY KEY AUTOINCREMENT, code TEXT NOT NULL UNIQUE, plant_id INTEGER NOT NULL REFERENCES plants(id), origin TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'Активен', notes TEXT NOT NULL DEFAULT '', created_at INTEGER NOT NULL)")
            createExtra(db)
            // The earlier prototype contained mojibake strings; recover known statuses only.
            db.execSQL("UPDATE cultures SET status=? WHERE status=?", arrayOf(LabTerms.IN_CULTURE, "Р’ РєСѓР»СЊС‚СѓСЂРµ"))
            db.execSQL("UPDATE cultures SET status=? WHERE status=?", arrayOf(LabTerms.CONTROL, "РќР° РєРѕРЅС‚СЂРѕР»Рµ"))
            db.execSQL("UPDATE cultures SET status=? WHERE status=?", arrayOf(LabTerms.QUARANTINE, "РљР°СЂР°РЅС‚РёРЅ"))
            db.execSQL("UPDATE cultures SET status=? WHERE status=?", arrayOf(LabTerms.DISPOSED, "РЈС‚РёР»РёР·РёСЂРѕРІР°РЅ"))
        }
        if (oldVersion < 4) createExtra(db)
        if (oldVersion < 5) {
            db.execSQL("ALTER TABLE cultures ADD COLUMN origin_state TEXT NOT NULL DEFAULT 'Не установлено'")
            db.execSQL("ALTER TABLE cultures ADD COLUMN quantity_kind TEXT NOT NULL DEFAULT 'Не уточнено'")
            db.execSQL("ALTER TABLE events ADD COLUMN occurred_at INTEGER")
            val incidentHasOperation = db.rawQuery("PRAGMA table_info(incidents)",null).use { cursor ->
                var found=false
                while(cursor.moveToNext()) if(cursor.getString(1)=="operation_id")found=true
                found
            }
            if(!incidentHasOperation)db.execSQL("ALTER TABLE incidents ADD COLUMN operation_id TEXT")
            db.execSQL("UPDATE cultures SET origin_state=CASE WHEN mother_id IS NULL THEN 'Не установлено' ELSE 'По маточнику' END")
            createExtra(db)
            db.execSQL("INSERT OR IGNORE INTO culture_links(parent_culture_id,child_culture_id,used_qty,result_qty,event_group) SELECT parent_culture_id,child_culture_id,used_qty,resulting_qty,event_group FROM lineage")
        }
        if (oldVersion < 7) {
            db.execSQL("ALTER TABLE cultures ADD COLUMN medium_recipe_id INTEGER REFERENCES recipes(id)")
            db.execSQL("ALTER TABLE recipe_parts ADD COLUMN sugar_g_l REAL")
            db.execSQL("ALTER TABLE recipe_parts ADD COLUMN agar_g_l REAL")
            createExtra(db)
            db.execSQL("UPDATE cultures SET medium_recipe_id=(SELECT recipe_id FROM batches WHERE batches.id=cultures.medium_batch_id) WHERE medium_batch_id IS NOT NULL AND medium_recipe_id IS NULL")
            db.execSQL("INSERT INTO culture_recipe_history(culture_id,recipe_id,applied_at,note) SELECT id,medium_recipe_id,created_at,'Перенесено из существующего учёта партий' FROM cultures WHERE medium_recipe_id IS NOT NULL")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_culture_direct_recipe ON cultures(medium_recipe_id)")
        }
        if (oldVersion < 8) LabCoreV8.migrateFrom7(db)
        if (oldVersion < 9) {
            LabCoreV9.create(db)
            LabCoreV9.validate(db)
        }
        if (oldVersion < 10) {
            LabCoreV10.create(db)
            LabCoreV10.validate(db)
        }
    }
    private fun cv(vararg pairs: Pair<String, Any?>) = ContentValues().apply {
        pairs.forEach { (k, v) -> when(v) {
            null -> putNull(k)
            is String -> put(k, v)
            is Int -> put(k, v)
            is Long -> put(k, v)
            is Double -> put(k, v)
            is Boolean -> put(k, if(v) 1 else 0)
            else -> put(k,v.toString())
        } }
    }
    private fun checkText(text: String, max: Int = 500): String {
        val trimmed = text.trim()
        require(trimmed.isNotEmpty() && trimmed.length <= max) { "Заполни поле (не более $max символов)" }
        return trimmed
    }
    private fun SQLiteDatabase.tx(action: SQLiteDatabase.() -> Unit) {
        beginTransaction()
        try { action(); setTransactionSuccessful() } finally { endTransaction() }
    }
    private fun event(db: SQLiteDatabase, id: Long, type: String, note: String, before: Int? = null, after: Int? = null, batch: Long? = null, group: String? = null, occurredAt: Long = System.currentTimeMillis()) {
        db.insertOrThrow("events", null, cv("culture_id" to id, "kind" to type, "note" to note, "created_at" to System.currentTimeMillis(), "before_qty" to before, "after_qty" to after, "medium_batch_id" to batch, "event_group" to group, "occurred_at" to occurredAt))
    }
    private fun Cursor.optLong(index: Int): Long? = if (isNull(index)) null else getLong(index)
    fun plants(): List<PlantRecord> = readableDatabase.rawQuery("SELECT id,name,description FROM plants WHERE NOT EXISTS (SELECT 1 FROM entity_archives a WHERE a.entity_type='plant' AND a.entity_id=plants.id) ORDER BY name COLLATE NOCASE", null).use { c ->
        buildList { while(c.moveToNext()) add(PlantRecord(c.getLong(0),c.getString(1),c.getString(2))) }
    }
    fun addPlant(name: String, notes: String = "") {
        val clean=checkText(name,120)
        val exists=readableDatabase.rawQuery("SELECT 1 FROM plants WHERE lower(trim(name))=lower(?) LIMIT 1",arrayOf(clean)).use { it.moveToFirst() }
        require(!exists) { "Это растение уже есть в каталоге. Выбери его из списка" }
        writableDatabase.insertOrThrow("plants", null, cv("name" to clean, "description" to notes.take(1000)))
    }
    fun mothers(): List<MotherRecord> = readableDatabase.rawQuery("SELECT m.id,m.code,m.plant_id,p.name,m.origin,m.status FROM mother_plants m JOIN plants p ON p.id=m.plant_id ORDER BY m.id DESC",null).use { c ->
        buildList { while(c.moveToNext()) add(MotherRecord(c.getLong(0),c.getString(1),c.getLong(2),c.getString(3),c.getString(4),c.getString(5))) }
    }
    fun addMother(plantId: Long, origin: String, notes: String) {
        val db=writableDatabase
        db.tx {
            val id=insertOrThrow("mother_plants",null,cv("code" to "TEMP-${UUID.randomUUID()}","plant_id" to plantId,"origin" to checkText(origin),"notes" to notes,"created_at" to System.currentTimeMillis()))
            update("mother_plants",cv("code" to "MAT-%05d".format(Locale.US,id)),"id=?",arrayOf(id.toString()))
        }
    }
    fun cultures(limit:Int=Int.MAX_VALUE): List<CultureRecord> = readableDatabase.rawQuery("SELECT c.id,c.code,c.plant_id,p.name,c.quantity,c.status,c.passage,c.stage,c.location,c.medium_batch_id,c.mother_id,c.created_at,c.notes,c.origin_state,c.quantity_kind,c.medium_recipe_id FROM cultures c JOIN plants p ON p.id=c.plant_id ORDER BY c.id DESC LIMIT ?",arrayOf(limit.coerceIn(1,Int.MAX_VALUE).toString())).use { c ->
        buildList { while(c.moveToNext()) add(CultureRecord(c.getLong(0),c.getString(1),c.getLong(2),c.getString(3),c.getInt(4),c.getString(5),c.getInt(6),c.getString(7),c.getString(8),c.optLong(9),c.optLong(10),c.getLong(11),c.getString(12),c.getString(13),c.getString(14),c.optLong(15))) }
    }
    /** Indexed lookup for operations; never load the full 10k-container collection for one transfer. */
    fun cultureById(id:Long):CultureRecord?=readableDatabase.rawQuery(
        "SELECT c.id,c.code,c.plant_id,p.name,c.quantity,c.status,c.passage,c.stage,c.location,c.medium_batch_id,c.mother_id,c.created_at,c.notes,c.origin_state,c.quantity_kind,c.medium_recipe_id FROM cultures c JOIN plants p ON p.id=c.plant_id WHERE c.id=? LIMIT 1",
        arrayOf(id.toString())).use{c->
        if(c.moveToFirst())CultureRecord(c.getLong(0),c.getString(1),c.getLong(2),
            c.getString(3),c.getInt(4),c.getString(5),c.getInt(6),c.getString(7),
            c.getString(8),c.optLong(9),c.optLong(10),c.getLong(11),c.getString(12),
            c.getString(13),c.getString(14),c.optLong(15))else null
    }
    fun photos(cultureId: Long): List<CulturePhoto> = readableDatabase.rawQuery("SELECT id,culture_id,filename,caption,created_at FROM culture_photos WHERE culture_id=? AND NOT EXISTS (SELECT 1 FROM entity_archives a WHERE a.entity_type='photo' AND a.entity_id=culture_photos.id) ORDER BY id DESC",arrayOf(cultureId.toString())).use { c ->
        buildList { while(c.moveToNext()) add(CulturePhoto(c.getLong(0),c.getLong(1),c.getString(2),c.getString(3),c.getLong(4))) }
    }
    fun addPhoto(context: Context, cultureId: Long, uri: Uri, caption: String) {
        require(cultureById(cultureId)!=null) { "Контейнер не найден" }
        val folder=File(context.filesDir,"culture_photos").apply { mkdirs() }
        val mime=context.contentResolver.getType(uri)
        val ext=when(mime) {"image/png"->"png";"image/webp"->"webp";"image/jpeg"->"jpg";"image/heic","image/heif"->"heic";else->throw IllegalArgumentException("Поддерживаются JPEG, PNG, WebP и HEIC") }
        val name="${UUID.randomUUID()}.$ext"
        val tmp=File(folder,"$name.part")
        val final=File(folder,name)
        try {
            val input=context.contentResolver.openInputStream(uri) ?: error("Фотография недоступна")
            input.use { source -> tmp.outputStream().use { dest ->
                val bytes=ByteArray(8192)
                var size=0L
                while(true){val n=source.read(bytes);if(n<0)break;size+=n;require(size<=20_000_000){"Фотография слишком большая (максимум 20 МБ)"};dest.write(bytes,0,n)}
                require(size>0){"Пустой файл фотографии"}
            }}
            check(tmp.renameTo(final)) { "Не удалось сохранить фотографию" }
            writableDatabase.tx {
                insertOrThrow("culture_photos",null,cv("culture_id" to cultureId,"filename" to name,"caption" to caption.take(500),"created_at" to System.currentTimeMillis()))
                event(this,cultureId,"Фотография",caption.ifBlank{"Добавлено фото"})
            }
        } catch(e: Exception) { tmp.delete();final.delete();throw e }
    }
    /** A single timeline replaces the split old events / assessments / growth presentation. */
    fun events(cultureId: Long): List<LabEvent> = unifiedJournal(cultureId).map { entry ->
        LabEvent(if(entry.source=="event")entry.sourceId else -entry.sourceId,
            cultureId,entry.kind,entry.summary(),entry.occurredAt,entry.beforeQty,
            entry.afterQty,entry.recordedAt)
    }
    fun ancestors(cultureId: Long): List<Pair<String,String>> = readableDatabase.rawQuery("WITH RECURSIVE chain(depth,parent_id,used_qty,result_qty) AS (SELECT 1,parent_culture_id,used_qty,result_qty FROM culture_links WHERE child_culture_id=? AND cancelled=0 UNION ALL SELECT chain.depth+1,l.parent_culture_id,l.used_qty,l.result_qty FROM culture_links l JOIN chain ON l.child_culture_id=chain.parent_id WHERE chain.depth < 100 AND l.cancelled=0) SELECT p.code, chain.used_qty || ' → ' || chain.result_qty FROM chain JOIN cultures p ON p.id=chain.parent_id ORDER BY chain.depth,p.code", arrayOf(cultureId.toString())).use { c ->
        buildList { while(c.moveToNext()) add(c.getString(0) to c.getString(1)) }
    }
    fun descendants(cultureId: Long): List<String> = readableDatabase.rawQuery("SELECT child.code FROM culture_links links JOIN cultures child ON child.id=links.child_culture_id WHERE links.parent_culture_id=? AND links.cancelled=0 ORDER BY child.id DESC",arrayOf(cultureId.toString())).use { c ->
        buildList { while(c.moveToNext()) add(c.getString(0)) }
    }
    private fun verifyBatch(id: Long?) {
        if (id == null) return
        val b = batches().firstOrNull { it.id == id } ?: error("Партия среды не найдена")
        require(b.status == "Готова") { "Партия среды забракована — её нельзя выбрать" }
    }
    private fun verifyMother(plantId: Long, motherId: Long?) {
        if (motherId == null) return
        val m = mothers().firstOrNull { it.id == motherId } ?: error("Маточник не найден")
        require(m.plantId == plantId) { "Маточник принадлежит другому растению" }
    }
    private fun createCultureInDb(db: SQLiteDatabase, plantId: Long, quantity: Int, status: String, passage: Int, stage: String, location: String, batchId: Long?, motherId: Long?, notes: String, group: String? = null, occurredAt: Long = System.currentTimeMillis(), quantityKind: String = "Не уточнено"): Long {
        require(quantity > 0 && quantity <= 1000000) { "Количество должно быть от 1 до 1 000 000" }
        require(quantityKind in LabTerms.quantityKinds) { "Выбери тип учёта количества" }
        verifyBatch(batchId)
        verifyMother(plantId,motherId)
        require(passage >= 0) { "Некорректный номер пассажа" }
        val id=db.insertOrThrow("cultures",null,cv("code" to "TMP-${UUID.randomUUID()}","plant_id" to plantId,"quantity" to quantity,"status" to status,"passage" to passage,"created_at" to occurredAt,"stage" to stage,"location" to location,"medium_batch_id" to batchId,"mother_id" to motherId,"notes" to notes,"updated_at" to System.currentTimeMillis(),"origin_state" to if(motherId!=null)"По маточнику" else "Не установлено","quantity_kind" to quantityKind))
        db.update("cultures",cv("code" to "LAB-%06d".format(Locale.US,id)),"id=?",arrayOf(id.toString()))
        db.insertOrThrow("culture_audit",null,cv(
            "culture_id" to id,"recorded_at" to System.currentTimeMillis(),"source" to "Создание",
            "operation_id" to group,"after_qty" to quantity,"after_kind" to quantityKind,
            "after_status" to status,"after_batch_id" to batchId,"after_stage" to stage,
            "after_location" to location))
        event(db,id,"Создание","Добавлено объектов: $quantity. $notes",null,quantity,batchId,group,occurredAt)
        return id
    }
    fun addCulture(plantId: Long, quantity: Int, motherId: Long?, batchId: Long?, location: String, notes: String, quantityKind: String = "Не уточнено") {
        writableDatabase.tx { createCultureInDb(this,plantId,quantity,LabTerms.IN_CULTURE,0,LabTerms.IN_VITRO,location,batchId,motherId,notes,quantityKind=quantityKind) }
    }
    /** Simple intake: one container, optional mother and recipe; all three measurements are independent. */
    fun addCultureQuick(plantId:Long,motherId:Long?,recipeId:Long?,explants:Int?,shoots:Int?,plantsCount:Int?,location:String,notes:String,
        passage:Int=0,stage:String=LabTerms.IN_VITRO,quantityKind:String?=null,locationId:Long?=null):Long {
        require(passage in 0..999){"Пассаж должен быть от P0 до P999"}
        require(stage in LabTerms.stages){"Выбери этап выращивания"}
        if(quantityKind!=null)require(quantityKind in LabTerms.quantityKinds.drop(1)){
            "Выбери единицу материала: экспланты, побеги или растения"
        }
        val counts=listOf(explants,shoots,plantsCount)
        require(counts.all{it==null || it in 0..1000000}) { "Проверь числа объектов" }
        require(counts.any{it!=null && it>0}) { "Укажи хотя бы один положительный показатель" }
        // Several measurements may coexist but cannot imply a common transferable unit.
        // The provisional quantity is locked until the operator confirms its meaning.
        val positives=listOfNotNull(explants?.takeIf{it>0},shoots?.takeIf{it>0},plantsCount?.takeIf{it>0})
        val inferredKind=if(positives.size==1) when {
            (plantsCount?:0)>0->"Растения"
            (shoots?:0)>0->"Побеги"
            else->"Экспланты"
        } else "Не уточнено"
        val kind=quantityKind?:inferredKind
        val operational=when(kind){
            "Экспланты"->explants
            "Побеги"->shoots
            "Растения"->plantsCount
            else->null
        }
        require(kind=="Не уточнено" || operational!=null && operational>0) {
            "Для выбранной единицы учёта укажи положительное количество"
        }
        val quantity=operational?:positives.maxOrNull()!!
        var resultId=0L
        writableDatabase.tx {
            if(recipeId!=null) {
                val r=recipes().firstOrNull{it.id==recipeId}?:error("Рецептура не найдена")
                require(r.status=="Утверждена") { "Выбирай только готовые рецептуры" }
                require(r.plantId==null || r.plantId==plantId) { "Эта рецептура предназначена для другого растения" }
            }
            val resolvedLocation=locationId?.let{key->
                rawQuery("SELECT full_path FROM storage_locations WHERE id=?",
                    arrayOf(key.toString())).use{c->
                    require(c.moveToFirst()){"Указанное место больше не существует"}
                    c.getString(0)
                }
            } ?: location
            val id=createCultureInDb(this,plantId,quantity,LabTerms.IN_CULTURE,passage,stage,
                resolvedLocation,null,motherId,notes,quantityKind=kind)
            if(locationId!=null){
                insertOrThrow("culture_storage",null,cv(
                    "culture_id" to id,"location_id" to locationId,
                    "assigned_at" to System.currentTimeMillis()))
            }
            update("cultures",cv("medium_recipe_id" to recipeId),"id=?",arrayOf(id.toString()))
            if(recipeId!=null) {
                insertOrThrow("culture_recipe_history",null,cv("culture_id" to id,"recipe_id" to recipeId,"applied_at" to System.currentTimeMillis(),"note" to "Выбрана при создании"))
                event(this,id,"Назначение среды","Рецептура ${recipes().first{it.id==recipeId}.name} · версия ${recipes().first{it.id==recipeId}.version}",quantity,quantity,null)
            }
            insertOrThrow("culture_measurements",null,cv("culture_id" to id,"recipe_id" to recipeId,"observed_at" to System.currentTimeMillis(),"explants" to explants,"shoots" to shoots,"plants" to plantsCount,"notes" to "Показатели при создании","record_kind" to "baseline"))
            resultId=id
        }
        return resultId
    }
    fun updateCulture(id: Long, quantity: Int, status: String, location: String, batchId: Long?, note: String, recipeId: Long? = null, recipeChosen: Boolean = false) {
        require(status in LabTerms.statuses || status == LabTerms.TRANSFERRED) { "Некорректный статус" }
        val reason=checkText(note,2000)
        val db=writableDatabase
        db.tx {
            val c=cultures().firstOrNull { it.id==id } ?: error("Контейнер не найден")
            require(c.status !in listOf(LabTerms.DISPOSED,LabTerms.SOLD,LabTerms.TRANSFERRED,LabTerms.ANNULLED)) { "Закрытый контейнер нельзя менять" }
            require(c.quantity>0) { "Закрытый контейнер нельзя редактировать" }
            require(quantity==c.quantity) { "Количество изменяется через «Потери» или «Исправить ошибку» — так сохранится правильная история" }
            require(status in listOf(LabTerms.IN_CULTURE,LabTerms.CONTROL,LabTerms.QUARANTINE)) { "Для списания выбери «Потери»" }
            // Changing a preparation batch also changes the effective recipe. Clearing a batch
            // does not imply removing the directly assigned recipe.
            val changedBatch = batchId != c.batchId
            val selectedRecipeId = if (changedBatch && batchId != null) {
                verifyBatch(batchId)
                batches().firstOrNull { it.id == batchId }?.recipeId
                    ?: error("Партия среды не найдена")
            } else null
            val effectiveRecipeId = if (recipeChosen) recipeId else (selectedRecipeId ?: c.recipeId)
            if (recipeChosen && effectiveRecipeId != null) {
                val selected=recipes().firstOrNull { it.id==effectiveRecipeId } ?: error("Рецептура не найдена")
                require(selected.status=="Утверждена" && (selected.plantId==null || selected.plantId==c.plantId)) {
                    "Выбери утверждённую рецептуру для этого растения"
                }
            }
            if (recipeChosen && batchId != null) {
                val batchRecipe=batches().firstOrNull{it.id==batchId}?.recipeId
                require(batchRecipe==effectiveRecipeId) { "Партия не соответствует версии рецептуры" }
            }
            update("cultures",cv("quantity" to quantity,"status" to status,"location" to location,"medium_batch_id" to batchId,
                "medium_recipe_id" to effectiveRecipeId,
                "updated_at" to System.currentTimeMillis()),"id=?",arrayOf(id.toString()))
            // A manual free-text change supersedes any previously assigned structured
            // location. Otherwise the new registry would still display the old shelf.
            // Historical storage_movements remain immutable.
            if(c.location != location) {
                delete("culture_storage","culture_id=?",arrayOf(id.toString()))
            }
            if (effectiveRecipeId != null && effectiveRecipeId != c.recipeId) {
                insertOrThrow("culture_recipe_history",null,cv("culture_id" to id,"recipe_id" to effectiveRecipeId,
                    "applied_at" to System.currentTimeMillis(),"note" to "Рецептура изменена в карточке контейнера"))
            }
            event(this,id,if(status==LabTerms.DISPOSED)"Утилизация" else "Изменение","${c.status} → $status; ${c.quantity} → $quantity; место: ${c.location} → $location; партия: ${c.batchId ?: "нет"} → ${batchId ?: "нет"}; рецептура: ${c.recipeId ?: "нет"} → ${effectiveRecipeId ?: "нет"}; причина: $reason",c.quantity,quantity,batchId)
        }
    }
    fun addObservation(cultureId: Long, note: String, starting: Int?, healthy: Int?, contaminated: Int?, days: Int?) {
        val db=writableDatabase
        db.tx {
            val c=cultures().firstOrNull { it.id==cultureId } ?: error("Контейнер не найден")
            require(c.status in listOf(LabTerms.IN_CULTURE,LabTerms.CONTROL,LabTerms.QUARANTINE)) { "Нельзя проводить осмотр закрытого контейнера" }
            event(this,cultureId,"Осмотр",checkText(note,2000),c.quantity,c.quantity,c.batchId)
            if(starting!=null && healthy!=null && contaminated!=null && days!=null) {
                require(starting>0 && healthy>=0 && contaminated>=0 && days>0 && days<=2000) { "Проверь числовые данные наблюдения" }
                insertOrThrow("assessments",null,cv("culture_id" to cultureId,"batch_id" to c.batchId,"starting_qty" to starting,"healthy_qty" to healthy,"contaminated_qty" to contaminated,"observation_days" to days,"note" to note,"observed_at" to System.currentTimeMillis()))
            }
        }
    }
    /** Compatibility entrypoint: all new transfers use the same audited operation engine. */
    fun transfer(sourceId: Long, usedQty: Int, outputQty: Int, batchId: Long?, stage: String, notes: String) {
        val kind=cultures().firstOrNull{it.id==sourceId}?.quantityKind?:"Не уточнено"
        splitCulture(sourceId,listOf(TransferDestination(usedQty,outputQty,stage,batchId,null,kind)),0,notes)
    }
    fun sale(cultureId: Long, quantity: Int, total: Double, notes: String) {
        require(quantity>0 && total>=0 && total.isFinite()) { "Некорректные данные продажи" }
        writableDatabase.tx {
            val c=cultures().firstOrNull{it.id==cultureId}?:error("Контейнер не найден")
            require(c.stage != LabTerms.IN_VITRO && c.status == LabTerms.IN_CULTURE && c.quantityKind=="Растения") {
                "Продавать можно только учтённые растения вне стерильной культуры"
            }
            require(quantity<=c.quantity) { "Недостаточно растений" }
            val rest=c.quantity-quantity
            insertOrThrow("sales",null,cv("culture_id" to cultureId,"quantity" to quantity,"price" to total,"sold_at" to System.currentTimeMillis(),"note" to notes))
            update("cultures",cv("quantity" to rest,"status" to if(rest==0)LabTerms.SOLD else c.status),"id=?",arrayOf(cultureId.toString()))
            event(this,cultureId,"Продажа","Реализовано: $quantity; сумма: $total руб.; $notes",c.quantity,rest,c.batchId)
        }
    }
    fun recipes(): List<RecipeRecord> = readableDatabase.rawQuery("SELECT id,family,version,name,purpose,plant_id,status,notes FROM recipes ORDER BY name COLLATE NOCASE,version DESC",null).use { c ->
        buildList { while(c.moveToNext()) add(RecipeRecord(c.getLong(0),c.getString(1),c.getInt(2),c.getString(3),c.getString(4),c.optLong(5),c.getString(6),c.getString(7))) }
    }
    fun recipeParts(recipeId: Long): List<RecipePart> = readableDatabase.rawQuery("SELECT id,recipe_id,name,per_l,unit,has_sugar,has_agar,stock_mg_ml FROM recipe_parts WHERE recipe_id=? ORDER BY id",arrayOf(recipeId.toString())).use { c ->
        buildList { while(c.moveToNext()) add(RecipePart(c.getLong(0),c.getLong(1),c.getString(2),c.getDouble(3),c.getString(4),c.getInt(5)==1,c.getInt(6)==1,c.getDouble(7))) }
    }
    fun createRecipe(name: String, purpose: String, plantId: Long?, notes: String) {
        require(purpose in LabTerms.purposes) { "Выбери назначение" }
        writableDatabase.insertOrThrow("recipes",null,cv("family" to UUID.randomUUID().toString(),"version" to 1,"name" to checkText(name,120),"purpose" to purpose,"plant_id" to plantId,"notes" to notes,"created_at" to System.currentTimeMillis()))
    }
    fun addRecipePart(recipeId: Long, name: String, amount: Double, unit: String, sugar: Boolean, agar: Boolean, stock: Double) {
        require(amount.isFinite() && amount>0) { "Дозировка должна быть положительной" }
        require(unit in listOf("г/л","мг/л","мл/л","мг/л из раствора")) { "Неизвестная единица" }
        require(stock>=0 && stock.isFinite()) { "Некорректная концентрация маточного раствора" }
        if(unit=="мг/л из раствора") require(stock>0) { "Укажи концентрацию маточного раствора в мг/мл" }
        writableDatabase.tx {
            val r=recipes().firstOrNull { it.id==recipeId }?:error("Рецептура не найдена")
            require(r.status=="Черновик") { "Активную рецептуру изменять нельзя: создай новую версию" }
            val existing=recipeParts(recipeId)
            require(existing.none{it.name.equals(name.trim(),true)}) { "Компонент уже добавлен" }
            require(!sugar || existing.none{it.includesSugar}) { "Сахар уже входит в состав другого компонента" }
            require(!agar || existing.none{it.includesAgar}) { "Агар уже входит в состав другого компонента" }
            insertOrThrow("recipe_parts",null,cv("recipe_id" to recipeId,"name" to checkText(name,120),"per_l" to amount,"unit" to unit,"has_sugar" to sugar,"has_agar" to agar,"stock_mg_ml" to stock))
        }
    }
    fun activateRecipe(recipeId: Long) {
        writableDatabase.tx {
            val r=recipes().firstOrNull{it.id==recipeId}?:error("Рецептура не найдена")
            require(r.status=="Черновик") { "Рецептура уже утверждена" }
            RecipeCalculator.calculate(recipeParts(recipeId),1000)
            update("recipes",cv("status" to "Утверждена"),"id=?",arrayOf(recipeId.toString()))
        }
    }
    fun reviseRecipe(recipeId: Long) {
        writableDatabase.tx {
            val r=recipes().firstOrNull{it.id==recipeId}?:error("Рецептура не найдена")
            require(r.status=="Утверждена") { "Для новой версии сначала утверди текущую" }
            val newVersion=(recipes().filter{it.family==r.family}.maxOfOrNull{it.version}?:0)+1
            val newId=insertOrThrow("recipes",null,cv("family" to r.family,"version" to newVersion,"name" to r.name,"purpose" to r.purpose,"plant_id" to r.plantId,"status" to "Черновик","notes" to r.notes,"created_at" to System.currentTimeMillis()))
            val sugarAgar=rawQuery("SELECT id,sugar_g_l,agar_g_l FROM recipe_parts WHERE recipe_id=?",arrayOf(recipeId.toString())).use { cursor ->
                buildMap<Long,Pair<Double?,Double?>> {
                    while(cursor.moveToNext()) put(cursor.getLong(0),
                        (if(cursor.isNull(1))null else cursor.getDouble(1)) to
                        (if(cursor.isNull(2))null else cursor.getDouble(2)))
                }
            }
            recipeParts(recipeId).forEach { p ->
                insertOrThrow("recipe_parts",null,cv("recipe_id" to newId,"name" to p.name,"per_l" to p.perLiter,"unit" to p.unit,"has_sugar" to p.includesSugar,"has_agar" to p.includesAgar,"stock_mg_ml" to p.stockMgPerMl,"sugar_g_l" to sugarAgar[p.id]?.first,"agar_g_l" to sugarAgar[p.id]?.second))
            }
        }
    }
    fun removeRecipePart(partId: Long) {
        writableDatabase.tx {
            val row=rawQuery("SELECT recipe_id FROM recipe_parts WHERE id=?",arrayOf(partId.toString())).use {c -> if(c.moveToFirst()) c.getLong(0) else error("Компонент не найден")}
            require(recipes().first{it.id==row}.status=="Черновик") { "Утверждённую рецептуру изменять нельзя" }
            delete("recipe_parts","id=?",arrayOf(partId.toString()))
        }
    }
    fun exportRecipeCsv(): String {
        val output=StringBuilder("\uFEFF").append(RecipeCsv.HEADER).append('\n')
        val plantNames=plants().associate{it.id to it.name}
        // Only latest version per family: subsequent edits become new drafts after import.
        val latest=recipes().groupBy{it.family}.values.map{list->list.maxBy{it.version}}
        latest.forEach {r->
            recipeParts(r.id).forEach{p->
                output.append(RecipeCsv.formatRow(listOf(
                    r.family,r.name,r.purpose,r.plantId?.let{plantNames[it]}?:"",p.name,
                    RecipeCalculator.formatAmount(p.perLiter),p.unit,if(p.includesSugar)"1" else "0",
                    if(p.includesAgar)"1" else "0",RecipeCalculator.formatAmount(p.stockMgPerMl),r.version.toString(),r.status
                ))).append('\n')
            }
        }
        return output.toString()
    }
    /** Validated CSV inserts new draft versions; never overwrites published recipe versions. */
    fun importRecipeCsv(csv:String) {
        val lines=RecipeCsv.parse(csv)
        val sha=MessageDigest.getInstance("SHA-256").digest(csv.toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it)}
        val groups=lines.groupBy{listOf(it.family,it.name,it.purpose,it.plant)}
        require(groups.size<=100){"Максимум 100 рецептур за одну загрузку"}
        val db=writableDatabase
        db.tx {
            val already=rawQuery("SELECT 1 FROM recipe_imports WHERE sha256=?",arrayOf(sha)).use{it.moveToFirst()}
            require(!already){"Этот файл уже был импортирован. Измени данные перед следующей загрузкой."}
            groups.forEach{(_,set)->
                require(set.size<=200){"Слишком много компонентов в одной рецептуре"}
                val first=set.first()
                val plantId=if(first.plant.isEmpty())null else plants().firstOrNull{it.name==first.plant}?.id ?: error("Растение ${first.plant} отсутствует в каталоге")
                val sameName=recipes().filter{it.name==first.name && it.purpose==first.purpose && it.plantId==plantId}
                val family=first.family.ifBlank{sameName.maxByOrNull{it.version}?.family?:UUID.randomUUID().toString()}
                val previous=recipes().filter{it.family==family}
                require(previous.isEmpty() || previous.all{it.name==first.name && it.purpose==first.purpose && it.plantId==plantId}) {"Конфликт семейства рецептур: ${first.name}"}
                require(set.map{it.ingredient.lowercase()}.distinct().size==set.size){"Повторение компонента в рецепте ${first.name}"}
                val sugar=set.count{it.sugar};val agar=set.count{it.agar}
                require(sugar<=1 && agar<=1){"Несколько источников сахара или агара: ${first.name}"}
                RecipeCalculator.calculate(set.map{RecipePart(0,0,it.ingredient,it.amount,it.unit,it.sugar,it.agar,it.stock)},1000)
                val newVersion=(previous.maxOfOrNull{it.version}?:0)+1
                val id=insertOrThrow("recipes",null,cv("family" to family,"version" to newVersion,"name" to first.name,"purpose" to first.purpose,"plant_id" to plantId,"status" to "Черновик","notes" to "Импорт CSV; SHA256 ${sha.take(12)}","created_at" to System.currentTimeMillis()))
                set.forEach {p->insertOrThrow("recipe_parts",null,cv("recipe_id" to id,"name" to p.ingredient,"per_l" to p.amount,"unit" to p.unit,"has_sugar" to p.sugar,"has_agar" to p.agar,"stock_mg_ml" to p.stock))}
            }
            insertOrThrow("recipe_imports",null,cv("sha256" to sha,"imported_at" to System.currentTimeMillis(),"description" to "${groups.size} рецептур"))
        }
    }
    fun batches(): List<BatchRecord> = readableDatabase.rawQuery("SELECT b.id,b.code,b.recipe_id,r.name,r.version,b.volume_ml,b.prepared_at,b.state,b.notes FROM batches b JOIN recipes r ON r.id=b.recipe_id ORDER BY b.id DESC",null).use { c ->
        buildList { while(c.moveToNext()) add(BatchRecord(c.getLong(0),c.getString(1),c.getLong(2),c.getString(3),c.getInt(4),c.getInt(5),c.getLong(6),c.getString(7),c.getString(8))) }
    }
    fun prepareBatch(recipeId: Long, volumeMl: Int, ph: String, sterilization: String, notes: String, actual: Map<String,Double>) {
        val db=writableDatabase
        db.tx {
            val recipe=recipes().firstOrNull{it.id==recipeId}?:error("Рецептура не найдена")
            require(recipe.status=="Утверждена") { "Сначала утверди рецептуру" }
            val components=recipeParts(recipeId)
            val calc=RecipeCalculator.calculate(components,volumeMl)
            require(actual.keys == components.map { it.name }.toSet()) { "Нужны фактические количества всех компонентов" }
            require(actual.values.all { it.isFinite() && it >= 0 }) { "Проверь фактические количества" }
            val snapshot=JSONObject().apply {
                put("recipe_id",recipe.id);put("family",recipe.family);put("version",recipe.version)
                put("recipe_name",recipe.name);put("purpose",recipe.purpose)
                put("components",JSONArray().apply { components.zip(calc).forEach{ (p,c) -> put(JSONObject().apply {
                    put("name",p.name);put("per_l",p.perLiter);put("unit",p.unit);put("has_sugar",p.includesSugar);put("has_agar",p.includesAgar)
                    put("stock_mg_ml",p.stockMgPerMl);put("calculated_amount",c.amount);put("actual_amount",actual[p.name]);put("calculated_unit",c.unit)
                }) } })
            }.toString()
            val id=insertOrThrow("batches",null,cv("code" to "TMP-${UUID.randomUUID()}","recipe_id" to recipeId,"volume_ml" to volumeMl,"prepared_at" to System.currentTimeMillis(),"actual_json" to snapshot,"ph" to ph,"sterilization" to sterilization,"notes" to notes))
            update("batches",cv("code" to "MED-%06d".format(Locale.US,id)),"id=?",arrayOf(id.toString()))
        }
    }
    fun batchSnapshot(id: Long): String = readableDatabase.rawQuery("SELECT actual_json FROM batches WHERE id=?",arrayOf(id.toString())).use { c ->
        require(c.moveToFirst()) { "Партия не найдена" }
        c.getString(0)
    }
    fun rejectBatch(batchId: Long, reason: String) {
        writableDatabase.tx {
            val b=batches().firstOrNull{it.id==batchId}?:error("Партия не найдена")
            require(b.status=="Готова") { "Партия уже заблокирована" }
            update("batches",cv("state" to "Брак","notes" to "${b.note}\nБРАК: ${checkText(reason)}"),"id=?",arrayOf(batchId.toString()))
            rawQuery("SELECT id FROM cultures WHERE medium_batch_id=? AND status IN (?,?,?)",arrayOf(batchId.toString(),LabTerms.IN_CULTURE,LabTerms.CONTROL,LabTerms.QUARANTINE)).use { c ->
                while(c.moveToNext()) {
                    val id=c.getLong(0)
                    update("cultures",cv("status" to LabTerms.CONTROL),"id=?",arrayOf(id.toString()))
                    event(this,id,"Проверка среды","Партия ${b.code} забракована: $reason",batch=batchId)
                }
            }
        }
    }
    fun tasks(): List<TaskRecord> = readableDatabase.rawQuery("SELECT id,culture_id,title,due_at,done FROM tasks ORDER BY done,due_at",null).use { c ->
        buildList { while(c.moveToNext()) add(TaskRecord(c.getLong(0),c.optLong(1),c.getString(2),c.getLong(3),c.getInt(4)==1)) }
    }
    fun createTask(title: String, dueAt: Long, cultureId: Long?) {
        writableDatabase.insertOrThrow("tasks",null,cv("culture_id" to cultureId,"title" to checkText(title,200),"due_at" to dueAt,"done" to 0))
    }
    fun setTaskDone(id: Long, done: Boolean) {
        writableDatabase.update("tasks",cv("done" to done),"id=?",arrayOf(id.toString()))
    }
    fun materials(): List<MaterialRecord> = readableDatabase.rawQuery("SELECT id,name,unit,quantity,min_quantity,cost_per_unit FROM materials ORDER BY name COLLATE NOCASE",null).use { c ->
        buildList { while(c.moveToNext()) add(MaterialRecord(c.getLong(0),c.getString(1),c.getString(2),c.getDouble(3),c.getDouble(4),c.getDouble(5))) }
    }
    fun addMaterial(name: String, unit: String, quantity: Double, min: Double, cost: Double) {
        require(listOf(quantity,min,cost).all{it.isFinite() && it>=0}) { "Проверь значения" }
        writableDatabase.insertOrThrow("materials",null,cv("name" to checkText(name,120),"unit" to checkText(unit,20),"quantity" to quantity,"min_quantity" to min,"cost_per_unit" to cost))
    }
    fun moveMaterial(materialId: Long, delta: Double, note: String) {
        require(delta.isFinite() && delta!=0.0) { "Укажи изменение количества" }
        writableDatabase.tx {
            val m=materials().firstOrNull{it.id==materialId}?:error("Материал не найден")
            require(m.quantity + delta >= -0.000001) { "Недостаточно материала" }
            update("materials",cv("quantity" to (m.quantity+delta).coerceAtLeast(0.0)),"id=?",arrayOf(materialId.toString()))
            insertOrThrow("material_moves",null,cv("material_id" to materialId,"delta" to delta,"note" to checkText(note),"created_at" to System.currentTimeMillis()))
        }
    }
    fun equipment(): List<EquipmentRecord> = readableDatabase.rawQuery("SELECT id,name,location,state,next_check FROM equipment ORDER BY id DESC",null).use { c ->
        buildList{while(c.moveToNext()) add(EquipmentRecord(c.getLong(0),c.getString(1),c.getString(2),c.getString(3),c.optLong(4)))}
    }
    fun addEquipment(name: String, location: String) {
        writableDatabase.insertOrThrow("equipment",null,cv("name" to checkText(name,120),"location" to location))
    }
    fun incidents(): List<IncidentRecord> = readableDatabase.rawQuery("SELECT id,title,note,created_at,state FROM incidents ORDER BY id DESC",null).use{c->
        buildList{while(c.moveToNext()) add(IncidentRecord(c.getLong(0),c.getString(1),c.getString(2),c.getLong(3),c.getString(4)))}
    }
    fun addIncident(title: String, note: String, cultureId: Long?) {
        reportIncident(title,note,listOfNotNull(cultureId),"Наблюдение")
    }
    fun experiments(): List<ExperimentRecord> = readableDatabase.rawQuery("SELECT id,title,hypothesis,started_at,state FROM experiments ORDER BY id DESC",null).use{c->
        buildList{while(c.moveToNext()) add(ExperimentRecord(c.getLong(0),c.getString(1),c.getString(2),c.getLong(3),c.getString(4)))}
    }
    fun addExperiment(title: String, hypothesis: String) {
        writableDatabase.insertOrThrow("experiments",null,cv("title" to checkText(title,200),"hypothesis" to checkText(hypothesis,2000),"started_at" to System.currentTimeMillis()))
    }
    fun enrollExperiment(experimentId: Long, cultureId: Long, group: String) {
        writableDatabase.insertOrThrow("experiment_cultures",null,cv("experiment_id" to experimentId,"culture_id" to cultureId,"group_name" to checkText(group,100)))
    }
    fun sales(): List<SaleRecord> = readableDatabase.rawQuery("SELECT s.id,c.code,s.quantity,s.price,s.sold_at FROM sales s JOIN cultures c ON c.id=s.culture_id ORDER BY s.id DESC",null).use{c->
        buildList{while(c.moveToNext()) add(SaleRecord(c.getLong(0),c.getString(1),c.getInt(2),c.getDouble(3),c.getLong(4)))}
    }
    data class MediumMetric(val recipe: String, val count: Int, val input: Int, val healthy: Int, val infected: Int, val averageDays: Double)
    fun recipeMetrics(): List<MediumMetric> = readableDatabase.rawQuery("SELECT r.name || ' · v' || r.version, COUNT(a.id), COALESCE(SUM(a.starting_qty),0), COALESCE(SUM(a.healthy_qty),0), COALESCE(SUM(a.contaminated_qty),0), COALESCE(AVG(a.observation_days),0) FROM assessments a JOIN batches b ON b.id=a.batch_id JOIN recipes r ON r.id=b.recipe_id GROUP BY r.id ORDER BY r.name,r.version",null).use{c->
        buildList{while(c.moveToNext()) add(MediumMetric(c.getString(0),c.getInt(1),c.getInt(2),c.getInt(3),c.getInt(4),c.getDouble(5)))}
    }
    fun countByStatus(): Map<String,Int> = cultures().groupingBy{it.status}.eachCount()
    /** Each operation is atomic, independently identifiable, and traceable to its sources. */
    private fun newOperation(db: SQLiteDatabase, kind: String, note: String, occurredAt: Long): String {
        val now=System.currentTimeMillis()
        require(occurredAt in 946684800000L..(now+300000L)) { "Проверь дату операции: нельзя записывать события из будущего" }
        val id=UUID.randomUUID().toString()
        db.insertOrThrow("lab_operations",null,cv("id" to id,"kind" to kind,"occurred_at" to occurredAt,"recorded_at" to now,"note" to note.trim().take(2000).ifEmpty{"Плановая операция"},"status" to "Проведена"))
        return id
    }
    private fun changeSnapshot(db: SQLiteDatabase, group: String, cultureId: Long, before: CultureRecord?, afterQty: Int, afterStatus: String) {
        db.insertOrThrow("operation_changes",null,cv("operation_id" to group,"culture_id" to cultureId,"before_qty" to before?.quantity,"after_qty" to afterQty,"before_status" to before?.status,"after_status" to afterStatus,"created" to if(before==null)1 else 0))
        // The creation row is already linked; a following recipe update may have its own
        // trigger row, which also belongs to this operation. Never relabel migration snapshots.
        db.execSQL("""UPDATE culture_audit SET operation_id=?
            WHERE id=(SELECT MAX(id) FROM culture_audit
                WHERE culture_id=? AND operation_id IS NULL AND source='Изменение')""",
            arrayOf(group,cultureId))
    }
    private fun changeCount(db: SQLiteDatabase, source: CultureRecord, quantity: Int, newStatus: String, kind: String, note: String, group: String, whenAt: Long) {
        require(quantity>=0 && quantity<=1000000) { "Недопустимое количество" }
        db.update("cultures",cv("quantity" to quantity,"status" to newStatus,"updated_at" to System.currentTimeMillis()),"id=?",arrayOf(source.id.toString()))
        changeSnapshot(db,group,source.id,source,quantity,newStatus)
        event(db,source.id,kind,note,source.quantity,quantity,source.batchId,group,whenAt)
    }
    fun confirmedLosses(cultureId: Long): Int = readableDatabase.rawQuery("SELECT COALESCE(SUM(l.quantity),0) FROM operation_losses l JOIN lab_operations o ON o.id=l.operation_id WHERE l.culture_id=? AND o.status='Проведена'",arrayOf(cultureId.toString())).use{it.moveToFirst();it.getInt(0)}
    fun operationsForCulture(cultureId: Long): List<OperationRecord> = readableDatabase.rawQuery(
        "SELECT o.id,o.kind,o.occurred_at,o.recorded_at,o.status,o.note FROM lab_operations o JOIN operation_changes ch ON ch.operation_id=o.id WHERE ch.culture_id=? ORDER BY o.recorded_at DESC,o.rowid DESC",
        arrayOf(cultureId.toString())
    ).use { c -> buildList { while(c.moveToNext()) add(OperationRecord(c.getString(0),c.getString(1),c.getLong(2),c.getLong(3),c.getString(4),c.getString(5))) } }
    /** Record the actual recipe on a daughter, without changing any parent's historical medium. */
    private fun assignDaughterRecipe(db: SQLiteDatabase, childId: Long,
            inheritedRecipeId: Long?, selectedBatchId: Long?, occurredAt: Long,
            explicitRecipeId: Long? = null) {
        val batchRecipeId = selectedBatchId?.let { batchId ->
            batches().firstOrNull{it.id==batchId}?.recipeId ?: error("Партия среды не найдена")
        }
        require(explicitRecipeId==null || batchRecipeId==null || explicitRecipeId==batchRecipeId) {
            "Выбранная партия приготовлена по другой версии рецептуры"
        }
        val recipeId = explicitRecipeId ?: batchRecipeId ?: inheritedRecipeId ?: return
        val recipe=recipes().firstOrNull{it.id==recipeId}?:error("Рецептура дочернего контейнера не найдена")
        val plantId=db.rawQuery("SELECT plant_id FROM cultures WHERE id=?",
            arrayOf(childId.toString())).use { c ->
            require(c.moveToFirst()){"Дочерний контейнер не найден"};c.getLong(0)
        }
        require(recipe.plantId==null || recipe.plantId==plantId) {
            "Рецептура принадлежит другому виду или сорту"
        }
        if(explicitRecipeId!=null) require(recipe.status=="Утверждена") {
            "Для новой среды выбери утверждённую версию"
        }
        db.update("cultures",cv("medium_recipe_id" to recipeId),"id=?",arrayOf(childId.toString()))
        db.insertOrThrow("culture_recipe_history",null,cv(
            "culture_id" to childId,"recipe_id" to recipeId,"applied_at" to occurredAt,
            "note" to when {
                explicitRecipeId!=null -> "Рецептура выбрана при операции"
                selectedBatchId!=null -> "Рецептура из партии приготовления"
                else -> "Унаследована при операции"
            }))
    }
    /** One source, many daughter containers. Input material, resulting material and losses are kept separate. */
    fun splitCulture(sourceId: Long, destinations: List<TransferDestination>, lost: Int, note: String, occurredAt: Long = System.currentTimeMillis()) {
        require(destinations.isNotEmpty() && destinations.size<=25) { "Укажи от 1 до 25 новых контейнеров" }
        require(lost>=0) { "Потери не могут быть отрицательными" }
        require(destinations.all{it.used in 1..1000000 && it.produced in 1..1000000 && it.stage in LabTerms.stages}) { "Проверь количества и этап для каждого нового контейнера" }
        val taken=destinations.sumOf { it.used.toLong() } + lost.toLong()
        writableDatabase.tx {
            val source=cultureById(sourceId)?:error("Исходный контейнер не найден")
            require(source.status in listOf(LabTerms.IN_CULTURE,LabTerms.CONTROL,LabTerms.QUARANTINE)) { "Нельзя пересаживать закрытый контейнер" }
            require(source.quantityKind != "Не уточнено") { "Тип исходных объектов не указан. Пересадка по общему количеству небезопасна: уточни тип учёта контейнера" }
            require(occurredAt>=source.createdAt) { "Дата пересадки раньше создания исходного контейнера" }
            val rest=LabOperationRules.remainingAfterSplit(source.quantity,destinations.map{it.used},destinations.map{it.produced},lost)
            destinations.forEach{verifyBatch(it.batchId)}
            val group=newOperation(this,"Разделение и пересадка",note,occurredAt)
            if(lost>0)insertOrThrow("operation_losses",null,cv("operation_id" to group,"culture_id" to sourceId,"quantity" to lost,"reason" to "В ходе пересадки"))
            changeCount(this,source,rest,if(rest==0)LabTerms.TRANSFERRED else source.status,"Пересадка", "Изъято ${taken-lost}; потери $lost; осталось $rest. $note",group,occurredAt)
            val createdChildren=mutableListOf<Long>()
            destinations.forEach{part->
                val nextPassage=source.passage+if(part.stage==LabTerms.IN_VITRO)1 else 0
                val child=createCultureInDb(this,source.plantId,part.produced,LabTerms.IN_CULTURE,nextPassage,part.stage,part.location?:source.location,part.batchId,source.motherId,note,group,occurredAt,part.quantityKind)
                createdChildren.add(child)
                assignDaughterRecipe(this,child,source.recipeId,part.batchId,occurredAt,part.recipeId)
                update("cultures",cv("origin_state" to source.originState),"id=?",arrayOf(child.toString()))
                insertOrThrow("culture_links",null,cv("parent_culture_id" to sourceId,"child_culture_id" to child,"used_qty" to part.used,"result_qty" to part.produced,"event_group" to group))
                changeSnapshot(this,group,child,null,part.produced,LabTerms.IN_CULTURE)
                event(this,child,"Происхождение","Из ${source.code}; исходных объектов ${part.used}, получено ${part.produced}. $note",null,part.produced,part.batchId,group,occurredAt)
            }
            // All daughters in this operation have a verified common parent.
            // Group by the actual split event, not by guessed genetic provenance.
            if(createdChildren.size>1) {
                val productionId=insertOrThrow("production_groups",null,cv(
                    "code" to "TEMP-"+UUID.randomUUID(),
                    "title" to "Пересадка из "+source.code,
                    "plant_id" to source.plantId,"mother_id" to source.motherId,
                    "note" to "Операция "+group,"created_at" to occurredAt))
                update("production_groups",
                    cv("code" to "ГР-%06d".format(Locale.US,productionId)),
                    "id=?",arrayOf(productionId.toString()))
                createdChildren.forEach{child->
                    insertOrThrow("culture_groups",null,cv("culture_id" to child,
                        "group_id" to productionId,"linked_at" to occurredAt))
                    insertOrThrow("group_history",null,cv("culture_id" to child,
                        "new_group_id" to productionId,"recorded_at" to occurredAt,
                        "reason" to "Общее происхождение: пересадка "+group))
                }
            }
        }
    }
    /** Combining sources is never silently presented as a verified clonal line. */
    fun mergeCultures(inputs: List<MergeInput>, produced: Int, batchId: Long?, stage: String, note: String, confirmMixedOrigins: Boolean, occurredAt: Long = System.currentTimeMillis(), recipeId: Long? = null) {
        require(inputs.size in 2..25 && inputs.map{it.cultureId}.distinct().size==inputs.size) { "Для объединения выбери 2–25 разных исходных контейнеров" }
        require(inputs.all{it.used>0} && produced in 1..1000000 && stage in LabTerms.stages) { "Проверь количество и этап" }
        writableDatabase.tx {
            val all=inputs.mapNotNull{cultureById(it.cultureId)}.associateBy{it.id}
            val sources=inputs.map{row->
                val c=all[row.cultureId] ?: error("Контейнер ${row.cultureId} не найден")
                require(c.status in listOf(LabTerms.IN_CULTURE,LabTerms.CONTROL,LabTerms.QUARANTINE) && row.used<=c.quantity) { "Источник ${c.code} закрыт или материала недостаточно" }
                c
            }
            require(sources.map{it.plantId}.distinct().size==1) { "Нельзя объединять разные виды или сорта растений в одну линию" }
            require(sources.map{it.quantityKind}.distinct().size==1) { "Для объединения источников приведи учёт к одной категории: экспланты, побеги или растения" }
            require(sources.all{it.quantityKind != "Не уточнено"}) { "Нельзя объединять контейнеры с неизвестным типом объектов. Сначала уточни учёт каждого источника" }
            require(occurredAt>=sources.maxOf{it.createdAt}) { "Дата объединения раньше даты создания одного из источников" }
            val remains=LabOperationRules.remainingAfterMerge(sources.map{it.quantity},inputs.map{it.used},produced)
            verifyBatch(batchId)
            val certain=sources.map{it.motherId}.distinct().size==1 && sources.first().motherId!=null && sources.all{it.originState=="По маточнику"}
            require(certain || confirmMixedOrigins) { "Происхождение неоднородно или неизвестно. Подтверди смешение с сохранением предупреждения" }
            val origin=if(certain)"По маточнику" else "Смешанное / неподтверждённое"
            val group=newOperation(this,"Объединение культур",note,occurredAt)
            val parent=sources.first()
            val child=createCultureInDb(this,parent.plantId,produced,LabTerms.IN_CULTURE,sources.maxOf{it.passage}+if(stage==LabTerms.IN_VITRO)1 else 0,stage,parent.location,batchId,if(certain)parent.motherId else null,note,group,occurredAt,parent.quantityKind)
            // Different source recipes are not interchangeable: leave the result unassigned unless a batch identifies it.
            val sharedRecipeId=sources.map{it.recipeId}.distinct().singleOrNull()
            assignDaughterRecipe(this,child,sharedRecipeId,batchId,occurredAt,recipeId)
            update("cultures",cv("origin_state" to origin),"id=?",arrayOf(child.toString()))
            changeSnapshot(this,group,child,null,produced,LabTerms.IN_CULTURE)
            sources.forEachIndexed{index,c->
                val consumed=inputs[index].used
                val rest=remains[index]
                changeCount(this,c,rest,if(rest==0)LabTerms.TRANSFERRED else c.status,"Объединение","В ${parent.plantName}: использовано $consumed для нового контейнера; ${if(!certain)"происхождение смешанное. " else ""}$note",group,occurredAt)
                insertOrThrow("culture_links",null,cv("parent_culture_id" to c.id,"child_culture_id" to child,"used_qty" to consumed,"result_qty" to produced,"event_group" to group))
            }
            event(this,child,"Происхождение","Объединены источники: ${sources.joinToString { it.code }}. Маркировка: $origin. $note",null,produced,batchId,group,occurredAt)
        }
    }
    /**
     * Explicitly resolve a historical ambiguous operational unit. A measured count is user-provided,
     * not inferred from explants/shoots/plants. Both the old value and the confirmed value are audited.
     */
    fun clarifyQuantityUnit(cultureId: Long, kind: String, verifiedCount: Int, reason: String,
                            occurredAt: Long = System.currentTimeMillis()) {
        require(kind in LabTerms.quantityKinds && kind != "Не уточнено") { "Выбери экспланты, побеги или растения" }
        require(verifiedCount in 1..1000000) { "Подтверди фактическое количество выбранных объектов" }
        val explanation=checkText(reason,2000)
        writableDatabase.tx {
            val c=cultures().firstOrNull{it.id==cultureId}?:error("Контейнер не найден")
            require(c.status in listOf(LabTerms.IN_CULTURE,LabTerms.CONTROL,LabTerms.QUARANTINE)) { "Контейнер закрыт" }
            require(c.quantityKind=="Не уточнено") { "Единица учёта уже подтверждена; для исправлений используй отдельную операцию" }
            require(occurredAt>=c.createdAt) { "Дата уточнения раньше создания контейнера" }
            val group=newOperation(this,"Уточнение единицы учёта",explanation,occurredAt)
            update("cultures",cv("quantity_kind" to kind,"quantity" to verifiedCount,
                "updated_at" to System.currentTimeMillis()),"id=?",arrayOf(cultureId.toString()))
            changeSnapshot(this,group,c.id,c,verifiedCount,c.status)
            event(this,c.id,"Уточнение единицы","Было: ${c.quantity} (${c.quantityKind}); подтверждено: $verifiedCount ($kind). $explanation",
                c.quantity,verifiedCount,c.batchId,group,occurredAt)
        }
    }

    fun recordLoss(cultureId: Long, count: Int, reason: String, notes: String, occurredAt: Long = System.currentTimeMillis()) {
        require(count>0) { "Укажи количество потерянных объектов" }
        val cause=checkText(reason,120)
        val explanation=checkText(notes,2000)
        writableDatabase.tx {
            val c=cultures().firstOrNull{it.id==cultureId}?:error("Контейнер не найден")
            require(c.status in listOf(LabTerms.IN_CULTURE,LabTerms.CONTROL,LabTerms.QUARANTINE)) { "Контейнер уже закрыт" }
            require(c.quantityKind != "Не уточнено") { "Сначала уточни тип учёта контейнера: экспланты, побеги или растения. Количество нельзя менять без известной единицы" }
            require(occurredAt>=c.createdAt) { "Дата потерь раньше создания контейнера" }
            val rest=LabOperationRules.remainingAfterLoss(c.quantity,count)
            val group=newOperation(this,"Потери", "$cause. $explanation",occurredAt)
            insertOrThrow("operation_losses",null,cv("operation_id" to group,"culture_id" to cultureId,"quantity" to count,"reason" to cause))
            changeCount(this,c,rest,if(rest==0)LabTerms.DISPOSED else c.status,"Потери","Потеряно: $count; причина: $cause. $explanation",group,occurredAt)
        }
    }
    /** A correction is not a biological gain/loss; it is always explicitly distinguished in analytics. */
    fun correctQuantity(cultureId: Long, quantity: Int, reason: String, occurredAt: Long = System.currentTimeMillis()) {
        checkText(reason,2000)
        require(quantity in 1..1000000) { "Для исправления укажи положительное количество. Для потерь или ошибочного создания есть отдельное действие" }
        writableDatabase.tx {
            val c=cultures().firstOrNull{it.id==cultureId}?:error("Контейнер не найден")
            require(c.status in listOf(LabTerms.IN_CULTURE,LabTerms.CONTROL,LabTerms.QUARANTINE)) { "Закрытый контейнер нельзя изменить напрямую; проверь возможность отмены операции" }
            require(c.quantityKind != "Не уточнено") { "Сначала уточни тип учёта контейнера: экспланты, побеги или растения. Количество нельзя менять без известной единицы" }
            require(quantity!=c.quantity) { "Количество не изменилось" }
            require(occurredAt>=c.createdAt) { "Дата исправления раньше создания контейнера" }
            val group=newOperation(this,"Исправление учёта",reason,occurredAt)
            changeCount(this,c,quantity,c.status,"Исправление учёта","Было записано ${c.quantity}; правильное количество $quantity. Причина: $reason",group,occurredAt)
        }
    }
    /** Void an accidentally created standalone container. If it has descendants, cancel its latest operation instead. */
    fun voidCulture(cultureId: Long, reason: String, occurredAt: Long = System.currentTimeMillis()) {
        checkText(reason,2000)
        writableDatabase.tx {
            val c=cultures().firstOrNull{it.id==cultureId}?:error("Контейнер не найден")
            require(c.status in listOf(LabTerms.IN_CULTURE,LabTerms.CONTROL,LabTerms.QUARANTINE)) { "Контейнер уже закрыт" }
            val parents=rawQuery("SELECT COUNT(*) FROM culture_links WHERE child_culture_id=? AND cancelled=0",arrayOf(cultureId.toString())).use{it.moveToFirst();it.getInt(0)}
            val kids=rawQuery("SELECT COUNT(*) FROM culture_links WHERE parent_culture_id=? AND cancelled=0",arrayOf(cultureId.toString())).use{it.moveToFirst();it.getInt(0)}
            val related=listOf("sales","assessments","culture_photos","experiment_cultures","tasks","incident_links")
                .sumOf{table->rawQuery("SELECT COUNT(*) FROM $table WHERE culture_id=?",arrayOf(cultureId.toString())).use{it.moveToFirst();it.getInt(0)}}
            val audited=rawQuery("SELECT COUNT(*) FROM operation_changes oc JOIN lab_operations op ON op.id=oc.operation_id WHERE oc.culture_id=? AND op.status='Проведена'",arrayOf(cultureId.toString())).use{it.moveToFirst();it.getInt(0)}
            require(parents==0 && kids==0 && related==0 && audited==0) { "У контейнера есть связанные операции или данные. Не аннулируй запись: сохрани историю и зарегистрируй исправление" }
            require(occurredAt>=c.createdAt) { "Дата аннулирования раньше даты создания контейнера" }
            val group=newOperation(this,"Ошибочное создание",reason,occurredAt)
            changeCount(this,c,0,LabTerms.ANNULLED,"Аннулирование","Ошибочно созданный контейнер. $reason",group,occurredAt)
        }
    }
    /** A lab incident is one event affecting any number of existing containers. No plants are silently lost. */
    fun reportIncident(title: String, description: String, affectedIds: List<Long>, response: String, occurredAt: Long = System.currentTimeMillis()) {
        require(affectedIds.distinct().size==affectedIds.size && affectedIds.size<=1000) { "Нельзя повторять контейнеры или выбрать больше 1000" }
        require(response in listOf("Наблюдение","На контроле","Карантин")) { "Неизвестная мера реагирования" }
        writableDatabase.tx {
            val group=newOperation(this,"Авария", "$title. $description",occurredAt)
            val incident=insertOrThrow("incidents",null,cv("title" to checkText(title,120),"note" to checkText(description,2000),"created_at" to occurredAt,"state" to "Открыт","culture_id" to affectedIds.singleOrNull(),"operation_id" to group))
            val all=cultures().associateBy{it.id}
            affectedIds.forEach{id->
                val c=all[id] ?: error("Контейнер $id не найден")
                require(occurredAt>=c.createdAt) { "Дата происшествия раньше создания контейнера ${c.code}" }
                val newStatus=when {
                    c.status !in listOf(LabTerms.IN_CULTURE,LabTerms.CONTROL,LabTerms.QUARANTINE) -> c.status
                    response=="Карантин" -> LabTerms.QUARANTINE
                    response=="На контроле" -> if(c.status==LabTerms.QUARANTINE)c.status else LabTerms.CONTROL
                    else -> c.status
                }
                insertOrThrow("incident_links",null,cv("incident_id" to incident,"culture_id" to id,"action" to response))
                if(newStatus!=c.status){
                    changeCount(this,c,c.quantity,newStatus,"Авария","${title.trim()}. Реакция: $response. $description",group,occurredAt)
                }else{
                    changeSnapshot(this,group,c.id,c,c.quantity,c.status)
                    event(this,c.id,"Авария","${title.trim()}. Реакция: $response. $description",c.quantity,c.quantity,c.batchId,group,occurredAt)
                }
            }
        }
    }
    fun incidentCultures(incidentId: Long): List<String> = readableDatabase.rawQuery(
        "SELECT c.code || ' · ' || p.name || ' (' || l.action || ')' FROM incident_links l JOIN cultures c ON c.id=l.culture_id JOIN plants p ON p.id=c.plant_id WHERE l.incident_id=? ORDER BY c.code",
        arrayOf(incidentId.toString())
    ).use{cursor->buildList{while(cursor.moveToNext())add(cursor.getString(0))}}
    fun incidentFollowups(incidentId: Long): List<String> = readableDatabase.rawQuery(
        "SELECT occurred_at,status,note FROM incident_followups WHERE incident_id=? ORDER BY id DESC",arrayOf(incidentId.toString())
    ).use{cursor->buildList{while(cursor.moveToNext())add("${java.text.SimpleDateFormat("dd.MM.yyyy HH:mm",Locale("ru")).format(java.util.Date(cursor.getLong(0)))} · ${cursor.getString(1)}: ${cursor.getString(2)}")}}
    fun updateIncident(incidentId: Long, newStatus: String, note: String, occurredAt: Long = System.currentTimeMillis()) {
        require(newStatus in listOf("Открыт","На контроле","Завершён","Аннулирован")) { "Недопустимое состояние происшествия" }
        val details=checkText(note,2000)
        writableDatabase.tx {
            val found=rawQuery("SELECT state FROM incidents WHERE id=?",arrayOf(incidentId.toString())).use{c->require(c.moveToFirst()){"Происшествие не найдено"};c.getString(0)}
            require(found!="Аннулирован") { "Аннулированное происшествие нельзя изменять" }
            newOperation(this,"Разбор происшествия","Происшествие $incidentId: $details",occurredAt)
            update("incidents",cv("state" to newStatus),"id=?",arrayOf(incidentId.toString()))
            insertOrThrow("incident_followups",null,cv("incident_id" to incidentId,"status" to newStatus,"note" to details,"occurred_at" to occurredAt,"recorded_at" to System.currentTimeMillis()))
        }
    }

    /** Conservative reversal: every touched culture must have no later event. Nothing is deleted. */
    fun cancelOperation(originalId: String, reason: String) {
        checkText(reason,2000)
        writableDatabase.tx {
            val original=rawQuery("SELECT kind,status FROM lab_operations WHERE id=?",arrayOf(originalId)).use{c->require(c.moveToFirst()){"Операция не найдена"};c.getString(0) to c.getString(1)}
            require(original.second=="Проведена") { "Операция уже отменена" }
            if(original.first=="Авария"){
                val followups=rawQuery("SELECT COUNT(*) FROM incident_followups f JOIN incidents i ON i.id=f.incident_id WHERE i.operation_id=?",arrayOf(originalId)).use{it.moveToFirst();it.getInt(0)}
                require(followups==0) { "По происшествию уже зарегистрированы дальнейшие решения; автоматическая отмена заблокирована" }
            }
            require(original.first in setOf("Разделение и пересадка","Объединение культур","Потери","Исправление учёта","Ошибочное создание","Авария")) { "Автоматическая отмена этого вида операции недоступна" }
            data class Change(val cultureId: Long,val beforeQty: Int?,val afterQty:Int,val beforeStatus:String?,val afterStatus:String,val created:Boolean)
            val changes=rawQuery("SELECT culture_id,before_qty,after_qty,before_status,after_status,created FROM operation_changes WHERE operation_id=?",arrayOf(originalId)).use{c->buildList{
                while(c.moveToNext())add(Change(c.getLong(0),if(c.isNull(1))null else c.getInt(1),c.getInt(2),if(c.isNull(3))null else c.getString(3),c.getString(4),c.getInt(5)==1))
            }}
            require(changes.isNotEmpty() || original.first=="Авария") { "Нет данных для безопасной отмены" }
            val current=cultures().associateBy{it.id}
            changes.forEach{entry->
                val c=current[entry.cultureId]?:error("Контейнер не найден")
                require(c.quantity==entry.afterQty && c.status==entry.afterStatus) { "${c.code} изменился после операции. Автоматическая отмена запрещена" }
                val max=rawQuery("SELECT MAX(id) FROM events WHERE culture_id=? AND event_group=?",arrayOf(c.id.toString(),originalId)).use{it.moveToFirst();if(it.isNull(0))0L else it.getLong(0)}
                require(max>0) { "Для ${c.code} отсутствует запись исходной операции" }
                val later=rawQuery("SELECT COUNT(*) FROM events WHERE culture_id=? AND id>?",arrayOf(c.id.toString(),max.toString())).use{it.moveToFirst();it.getInt(0)}
                require(later==0) { "После операции с ${c.code} были другие действия. Сначала проверь их последствия" }
            }
            val group=newOperation(this,"Отмена операции","Отмена $originalId. $reason",System.currentTimeMillis())
            update("lab_operations",cv("status" to "Отменена"),"id=?",arrayOf(originalId))
            update("lab_operations",cv("reversal_of" to originalId),"id=?",arrayOf(group))
            changes.forEach{ch->
                val c=current.getValue(ch.cultureId)
                val restoreQty=if(ch.created)0 else requireNotNull(ch.beforeQty)
                val restoreStatus=if(ch.created)LabTerms.ANNULLED else requireNotNull(ch.beforeStatus)
                update("cultures",cv("quantity" to restoreQty,"status" to restoreStatus,"updated_at" to System.currentTimeMillis()),"id=?",arrayOf(ch.cultureId.toString()))
                changeSnapshot(this,group,c.id,c,restoreQty,restoreStatus)
                event(this,ch.cultureId,"Отмена операции","Отменена ${original.first}: $reason. Старые записи сохранены",c.quantity,restoreQty,c.batchId,group)
            }
            update("culture_links",cv("cancelled" to 1),"event_group=?",arrayOf(originalId))
            if(original.first=="Авария") update("incidents",cv("state" to "Аннулирован"),"operation_id=?",arrayOf(originalId))
        }
    }

    companion object {
        /** Tables are restored in parent-first order; all foreign key constraints stay enabled. */
        val BACKUP_ORDER_V4=listOf("plants","mother_plants","recipes","recipe_parts","recipe_imports","batches","cultures","culture_photos","events","lineage","assessments","tasks","materials","material_moves","equipment","incidents","experiments","experiment_cultures","sales")
        val BACKUP_ORDER_V5=BACKUP_ORDER_V4+listOf("lab_operations","operation_changes","culture_links","incident_links","incident_followups","operation_losses")
        val BACKUP_ORDER_V6=BACKUP_ORDER_V5+listOf("ingredient_catalog")
        val BACKUP_ORDER_V7=BACKUP_ORDER_V6+listOf("culture_measurements","culture_recipe_history")
        val BACKUP_ORDER_V8=BACKUP_ORDER_V7+listOf("culture_audit")
        val BACKUP_ORDER_V9=BACKUP_ORDER_V8+LabCoreV9.backupTables
        val BACKUP_ORDER=BACKUP_ORDER_V9+LabCoreV10.backupTables
    }
    fun exportJson(): String {
        val db=readableDatabase
        val root=JSONObject().put("format","DIKOVERIYA_LAB").put("schema",10).put("exported_at",System.currentTimeMillis())
        val tables=JSONObject()
        // A consistent read snapshot prevents a backup that mixes partially completed operations.
        db.beginTransactionNonExclusive()
        try {
        BACKUP_ORDER.forEach{table->
            val arr=JSONArray()
            // Self-referencing storage locations must restore parent rows before children.
            val exportSql=if(table=="storage_locations")
                "SELECT * FROM storage_locations ORDER BY id" else "SELECT * FROM $table"
            db.rawQuery(exportSql,null).use{c->
                while(c.moveToNext()) {
                    val row=JSONObject()
                    for(i in 0 until c.columnCount) {
                        val key=c.getColumnName(i)
                        when(c.getType(i)) {
                            Cursor.FIELD_TYPE_NULL -> row.put(key,JSONObject.NULL)
                            Cursor.FIELD_TYPE_INTEGER -> row.put(key,c.getLong(i))
                            Cursor.FIELD_TYPE_FLOAT -> row.put(key,c.getDouble(i))
                            else -> row.put(key,c.getString(i))
                        }
                    }
                    arr.put(row)
                }
            }
            tables.put(table,arr)
        }
        val result=root.put("tables",tables).toString(2)
        db.setTransactionSuccessful()
        return result
        } finally { db.endTransaction() }
    }
    /** Restore is all-or-nothing; not a merge. Caller must obtain explicit user confirmation. */
    fun importJson(payload: String) {
        require(payload.toByteArray(Charsets.UTF_8).size<=50_000_000) { "Файл резервной копии слишком большой" }
        val root=JSONObject(payload)
        require(root.optString("format")=="DIKOVERIYA_LAB" && root.optInt("schema") in 4..10) { "Неизвестная версия резервной копии" }
        val fromVersion=root.getInt("schema")
        val tables=root.getJSONObject("tables")
        val importOrder=when(fromVersion){4->BACKUP_ORDER_V4;5->BACKUP_ORDER_V5;6->BACKUP_ORDER_V6;7->BACKUP_ORDER_V7;8->BACKUP_ORDER_V8;9->BACKUP_ORDER_V9;else->BACKUP_ORDER}
        importOrder.forEach{require(tables.has(it) && tables.get(it) is JSONArray){"В копии отсутствует таблица $it"}}
        writableDatabase.tx {
            BACKUP_ORDER.reversed().forEach{delete(it,null,null)}
            importOrder.forEach{table->
                val arr=tables.getJSONArray(table)
                require(arr.length()<=100000) { "Слишком много записей: $table" }
                for(i in 0 until arr.length()) {
                    val obj=arr.getJSONObject(i)
                    val values=ContentValues()
                    obj.keys().forEach{key->
                        when(val v=obj.get(key)) {
                            JSONObject.NULL -> values.putNull(key)
                            is Int -> values.put(key,v)
                            is Long -> values.put(key,v)
                            is Double -> values.put(key,v)
                            is String -> values.put(key,v)
                            is Boolean -> values.put(key,if(v)1 else 0)
                            else -> error("Неверное значение в резервной копии")
                        }
                    }
                    insertOrThrow(table,null,values)
                }
            }
            if(fromVersion<6) {
                // Imported older backups have no ingredient catalog. Restore safe names without doses.
                createExtra(this)
            }
            if(fromVersion<7) {
                execSQL("UPDATE cultures SET medium_recipe_id=(SELECT recipe_id FROM batches WHERE batches.id=cultures.medium_batch_id) WHERE medium_batch_id IS NOT NULL AND medium_recipe_id IS NULL")
                execSQL("INSERT INTO culture_recipe_history(culture_id,recipe_id,applied_at,note) SELECT id,medium_recipe_id,created_at,'Перенесено из резервной копии' FROM cultures WHERE medium_recipe_id IS NOT NULL")
            }
            if(fromVersion<8) {
                delete("culture_audit",null,null)
                // Older backups know only the final state. Do not fabricate a change history.
                LabCoreV8.seedCurrentState(this,"Восстановлено из копии v$fromVersion")
                execSQL("UPDATE culture_measurements SET record_kind='baseline' WHERE notes='Показатели при создании'")
            }
            if(fromVersion==4) {
                execSQL("UPDATE cultures SET origin_state=CASE WHEN mother_id IS NULL THEN 'Не установлено' ELSE 'По маточнику' END")
                execSQL("INSERT OR IGNORE INTO culture_links(parent_culture_id,child_culture_id,used_qty,result_qty,event_group) SELECT parent_culture_id,child_culture_id,used_qty,resulting_qty,event_group FROM lineage")
            }
            LabCoreV10.validate(this)
        }
    }
}
