package ru.dikoveriya.lab

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import java.util.UUID

/** Fresh human-readable workflow. A recipe is not a batch: these are independent concepts. */
data class MediumPartInput(
    val name: String, val amountForVolume: Double, val unit: String,
    val includedSugarGram: Double? = null, val includedAgarGram: Double? = null,
    val stockMgPerMl: Double = 0.0
)

data class GrowthSample(
    val id: Long, val cultureId: Long, val recipeId: Long?, val date: Long,
    val explants: Int?, val shoots: Int?, val plants: Int?, val contaminated: Int?, val notes: String
)

data class LinkedMedium(val id: Long, val name: String, val purpose: String, val version: Int)

data class CultivarResult(val plantId: Long, val name: String, val containers: Int, val revenue: Double, val lastExplants: Int, val lastShoots: Int, val lastPlants: Int)

data class MediumResult(val recipeId: Long, val name: String, val cultures: Int, val observations: Int, val latestShoots: Int, val latestPlants: Int)

private fun values(vararg pairs: Pair<String, Any?>): ContentValues = ContentValues().apply {
    for ((k, v) in pairs) when (v) {
        null -> putNull(k)
        is String -> put(k, v)
        is Int -> put(k, v)
        is Long -> put(k, v)
        is Double -> put(k, v)
        else -> error("Неизвестный тип данных: $k")
    }
}

private inline fun <T> SQLiteDatabase.atomicResult(block: SQLiteDatabase.() -> T): T {
    beginTransaction()
    try {
        val answer = block()
        setTransactionSuccessful()
        return answer
    } finally { endTransaction() }
}

/** Creates a complete immutable medium in ONE transaction; volumes are inputs, not baked into recipe identity. */
fun LabDatabase.saveReadyMedium(
    name: String, purpose: String, plantId: Long?, notes: String, volumeMl: Int,
    parts: List<MediumPartInput>
): Long {
    val title = name.trim()
    require(title.length in 2..120) { "Укажи название среды (2–120 символов)" }
    require(purpose in LabTerms.purposes) { "Выбери назначение среды" }
    require(volumeMl in 1..100000) { "Укажи объём от 1 до 100 000 мл" }
    require(parts.isNotEmpty() && parts.size <= 60) { "Добавь хотя бы один компонент (максимум 60)" }
    require(parts.map { it.name.trim().lowercase() }.distinct().size == parts.size) { "Один компонент указан несколько раз" }
    require(parts.all { it.name.trim().length in 2..120 }) { "Проверь названия компонентов" }
    require(parts.all { it.unit in listOf("г/л", "мг/л", "мл/л", "мг/л из раствора") }) { "Некорректная единица компонента" }
    require(parts.all { it.amountForVolume > 0 && it.amountForVolume.isFinite() }) { "Количество каждого компонента должно быть положительным" }
    require(parts.all { it.stockMgPerMl >= 0 && it.stockMgPerMl.isFinite() && (it.unit != "мг/л из раствора" || it.stockMgPerMl > 0) }) { "Проверь концентрацию исходного раствора" }
    require(parts.all { (it.includedSugarGram == null || it.includedSugarGram > 0 && it.includedSugarGram.isFinite()) && (it.includedAgarGram == null || it.includedAgarGram > 0 && it.includedAgarGram.isFinite()) }) { "Укажи количество уже содержащихся сахара и агара, граммы" }
    require(parts.count { it.includedSugarGram != null } <= 1 && parts.count { it.includedAgarGram != null } <= 1) { "Уточни двойное включение сахара или агара" }
    require(!(parts.any { it.includedSugarGram != null } && parts.any { it.name.trim().equals("Сахароза", true) })) { "Сахар уже включён в смесь: проверь, не добавляешь ли его дважды" }
    require(!(parts.any { it.includedAgarGram != null } && parts.any { it.name.trim().equals("Агар", true) })) { "Агар уже включён в смесь: проверь, не добавляешь ли его дважды" }

    val normalized = parts.map { part ->
        RecipePart(0, 0, part.name.trim(), part.amountForVolume * 1000.0 / volumeMl, part.unit,
            part.includedSugarGram != null, part.includedAgarGram != null, part.stockMgPerMl)
    }
    RecipeCalculator.calculate(normalized, volumeMl)
    val db = writableDatabase
    return db.atomicResult {
        if (plantId != null) require(rawQuery("SELECT 1 FROM plants WHERE id=?", arrayOf(plantId.toString())).use { it.moveToFirst() }) { "Растение удалено" }
        val duplicate = rawQuery("SELECT id FROM recipes WHERE lower(trim(name))=lower(?) AND purpose=? AND COALESCE(plant_id,-1)=? LIMIT 1",
            arrayOf(title, purpose, (plantId ?: -1L).toString())).use { it.moveToFirst() }
        require(!duplicate) { "Такая среда уже существует. Выбери её из списка или создай отдельный вариант с другим названием" }
        val id = insertOrThrow("recipes", null, values("family" to UUID.randomUUID().toString(), "version" to 1,
            "name" to title, "purpose" to purpose, "plant_id" to plantId, "status" to "Утверждена",
            "notes" to notes.trim().take(2000), "created_at" to System.currentTimeMillis()))
        for (i in parts.indices) {
            val part = parts[i]
            val normalizedPerLiter = normalized[i].perLiter
            insertOrThrow("recipe_parts", null, values("recipe_id" to id,"name" to part.name.trim(),
                "per_l" to normalizedPerLiter, "unit" to part.unit,
                "has_sugar" to if(part.includedSugarGram != null) 1 else 0,
                "has_agar" to if(part.includedAgarGram != null) 1 else 0,
                "sugar_g_l" to part.includedSugarGram?.times(1000.0 / volumeMl),
                "agar_g_l" to part.includedAgarGram?.times(1000.0 / volumeMl),
                "stock_mg_ml" to part.stockMgPerMl))
        }
        id
    }
}

fun LabDatabase.mediumSugarAgar(recipeId: Long): Map<String, Pair<Double?,Double?>> = readableDatabase.rawQuery(
    "SELECT name,sugar_g_l,agar_g_l FROM recipe_parts WHERE recipe_id=?", arrayOf(recipeId.toString())
).use { c -> buildMap { while(c.moveToNext()) put(c.getString(0), (if(c.isNull(1))null else c.getDouble(1)) to (if(c.isNull(2))null else c.getDouble(2))) } }

fun LabDatabase.currentMedium(cultureId: Long): LinkedMedium? = readableDatabase.rawQuery(
    "SELECT r.id,r.name,r.purpose,r.version FROM cultures c JOIN recipes r ON c.medium_recipe_id=r.id WHERE c.id=?", arrayOf(cultureId.toString())
).use { c -> if(c.moveToFirst()) LinkedMedium(c.getLong(0),c.getString(1),c.getString(2),c.getInt(3)) else null }

fun LabDatabase.growthSamples(cultureId: Long): List<GrowthSample> = readableDatabase.rawQuery(
    "SELECT id,culture_id,recipe_id,observed_at,explants,shoots,plants,contaminated,notes FROM culture_measurements WHERE culture_id=? ORDER BY observed_at DESC,id DESC",
    arrayOf(cultureId.toString())
).use { c -> buildList { while(c.moveToNext()) add(GrowthSample(c.getLong(0),c.getLong(1),if(c.isNull(2))null else c.getLong(2),c.getLong(3),
    if(c.isNull(4))null else c.getInt(4),if(c.isNull(5))null else c.getInt(5),if(c.isNull(6))null else c.getInt(6),
    if(c.isNull(7))null else c.getInt(7),c.getString(8))) } }

fun LabDatabase.addGrowthSample(cultureId: Long, explants: Int?, shoots: Int?, plants: Int?, contaminated: Int?, notes: String) {
    require(listOf(explants, shoots, plants, contaminated).any { it != null }) { "Укажи хотя бы один показатель" }
    require(listOf(explants, shoots, plants, contaminated).all { it == null || it in 0..1000000 }) { "Некорректное количество" }
    val db = writableDatabase
    db.atomicResult {
        val recipeId = rawQuery("SELECT medium_recipe_id,status FROM cultures WHERE id=?",arrayOf(cultureId.toString())).use { c ->
            require(c.moveToFirst()) { "Контейнер не найден" }
            require(c.getString(1) !in listOf(LabTerms.DISPOSED,LabTerms.SOLD,LabTerms.ANNULLED,LabTerms.TRANSFERRED)) { "Контейнер закрыт, новый осмотр не допускается" }
            if(c.isNull(0)) null else c.getLong(0)
        }
        insertOrThrow("culture_measurements",null,values("culture_id" to cultureId,"recipe_id" to recipeId,
            "observed_at" to System.currentTimeMillis(),"explants" to explants,"shoots" to shoots,"plants" to plants,
            "contaminated" to contaminated,"notes" to notes.take(2000)))
    }
}

fun LabDatabase.assignMedium(cultureId: Long, newRecipeId: Long, reason: String) {
    val db = writableDatabase
    db.atomicResult {
        require(rawQuery("SELECT 1 FROM recipes WHERE id=? AND status='Утверждена'", arrayOf(newRecipeId.toString())).use{it.moveToFirst()}) { "Выбирай только готовые рецептуры" }
        val current = rawQuery("SELECT medium_recipe_id,status FROM cultures WHERE id=?",arrayOf(cultureId.toString())).use { c ->
            require(c.moveToFirst()) { "Контейнер не найден" }
            require(c.getString(1) !in listOf(LabTerms.DISPOSED,LabTerms.SOLD,LabTerms.ANNULLED,LabTerms.TRANSFERRED)) { "Нельзя сменить среду у закрытого контейнера" }
            if(c.isNull(0)) null else c.getLong(0)
        }
        require(current != newRecipeId) { "Эта среда уже выбрана" }
        update("cultures",values("medium_recipe_id" to newRecipeId,"updated_at" to System.currentTimeMillis()),"id=?",arrayOf(cultureId.toString()))
        insertOrThrow("culture_recipe_history",null,values("culture_id" to cultureId,"recipe_id" to newRecipeId,
            "applied_at" to System.currentTimeMillis(),"note" to reason.trim().ifEmpty { "Смена рецептуры" }.take(1000)))
    }
}

fun LabDatabase.plantResults(): List<CultivarResult> {
    val db = readableDatabase
    return db.rawQuery("""
        SELECT p.id,p.name,COUNT(DISTINCT c.id),COALESCE((SELECT SUM(s.price) FROM sales s
          JOIN cultures sc ON sc.id=s.culture_id WHERE sc.plant_id=p.id),0)
        FROM plants p LEFT JOIN cultures c ON c.plant_id=p.id GROUP BY p.id ORDER BY p.name
    """.trimIndent(),null).use { cursor ->
        buildList {
            while(cursor.moveToNext()) {
                val plantId=cursor.getLong(0)
                val current=db.rawQuery("""
                    SELECT
                      COALESCE((SELECT m.explants FROM culture_measurements m WHERE m.culture_id=c.id AND m.explants IS NOT NULL ORDER BY m.observed_at DESC,m.id DESC LIMIT 1),0),
                      COALESCE((SELECT m.shoots FROM culture_measurements m WHERE m.culture_id=c.id AND m.shoots IS NOT NULL ORDER BY m.observed_at DESC,m.id DESC LIMIT 1),0),
                      COALESCE((SELECT m.plants FROM culture_measurements m WHERE m.culture_id=c.id AND m.plants IS NOT NULL ORDER BY m.observed_at DESC,m.id DESC LIMIT 1),0)
                    FROM cultures c WHERE c.plant_id=?
                """.trimIndent(),arrayOf(plantId.toString())).use { measures ->
                    val values=IntArray(3)
                    while(measures.moveToNext())for(i in 0..2)values[i]+=measures.getInt(i)
                    values
                }
                add(CultivarResult(plantId,cursor.getString(1),cursor.getInt(2),cursor.getDouble(3),current[0],current[1],current[2]))
            }
        }
    }
}

fun LabDatabase.mediumResults(): List<MediumResult> {
    val db=readableDatabase
    return db.rawQuery("""
        SELECT r.id,r.name || ' · v' || r.version,COUNT(DISTINCT cm.culture_id),COUNT(cm.id),
               COALESCE(SUM(CASE WHEN cm.shoots IS NOT NULL AND cm.id=(
                   SELECT x.id FROM culture_measurements x WHERE x.culture_id=cm.culture_id AND x.recipe_id=cm.recipe_id
                     AND x.shoots IS NOT NULL ORDER BY x.observed_at DESC,x.id DESC LIMIT 1
               ) THEN cm.shoots ELSE 0 END),0),
               COALESCE(SUM(CASE WHEN cm.plants IS NOT NULL AND cm.id=(
                   SELECT x.id FROM culture_measurements x WHERE x.culture_id=cm.culture_id AND x.recipe_id=cm.recipe_id
                     AND x.plants IS NOT NULL ORDER BY x.observed_at DESC,x.id DESC LIMIT 1
               ) THEN cm.plants ELSE 0 END),0)
        FROM recipes r JOIN culture_measurements cm ON cm.recipe_id=r.id
        GROUP BY r.id ORDER BY r.name,r.version
    """.trimIndent(),null).use { c -> buildList { while(c.moveToNext()) add(MediumResult(c.getLong(0),c.getString(1),c.getInt(2),c.getInt(3),c.getInt(4),c.getInt(5))) } }
}
