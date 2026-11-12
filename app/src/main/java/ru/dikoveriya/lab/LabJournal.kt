package ru.dikoveriya.lab

/**
 * A single read model across all surviving historical journals.
 *
 * Events, quantitative measurements and legacy assessments retain their original
 * tables and identities. We never convert legacy "healthy" into plants/shoots,
 * or treat an event / intake as a repeated growth observation.
 */
data class JournalEntry(
    val source: String, val sourceId: Long, val kind: String, val note: String,
    val occurredAt: Long, val recordedAt: Long,
    val beforeQty: Int?, val afterQty: Int?,
    val explants: Int?, val shoots: Int?, val plants: Int?, val contaminated: Int?,
    val legacyStarting: Int?, val legacyHealthy: Int?, val legacyDays: Int?,
    val recipeId: Long?
) {
    fun summary(): String {
        val measures=when(source) {
            "measurement" -> listOfNotNull(
                explants?.let{"Экспланты: $it"},shoots?.let{"Побеги: $it"},
                plants?.let{"Растения: $it"},contaminated?.let{"Заражено: $it"}
            )
            "legacy" -> listOfNotNull(
                legacyStarting?.let{"Исходно (архив): $it"},
                legacyHealthy?.let{"Здоровых (архив, тип не указан): $it"},
                contaminated?.let{"Заражено (архив): $it"},
                legacyDays?.let{"Период: $it дней"}
            )
            else -> emptyList()
        }
        // An event is an action, not a new measurement. The unit is kept in
        // the culture/operation records and must never be inferred here.
        return (measures+note.takeIf{it.isNotBlank()}.let{if(it==null)emptyList() else listOf(it)})
            .joinToString(" · ").ifBlank{"Без дополнительных данных"}
    }
}

/** Chronological projection, excluding only a provably paired legacy event/assessment duplicate. */
fun LabDatabase.unifiedJournal(cultureId: Long): List<JournalEntry> =
    readableDatabase.rawQuery("""
        SELECT source,source_id,kind,note,occurred_at,recorded_at,before_qty,after_qty,
               explants,shoots,plants,contaminated,legacy_start,legacy_healthy,legacy_days,recipe_id
        FROM (
            SELECT 'event' source,e.id source_id,e.kind,e.note,
                   COALESCE(e.occurred_at,e.created_at) occurred_at,e.created_at recorded_at,
                   e.before_qty,e.after_qty,
                   NULL explants,NULL shoots,NULL plants,NULL contaminated,
                   NULL legacy_start,NULL legacy_healthy,NULL legacy_days,NULL recipe_id
            FROM events e WHERE e.culture_id=?
              AND NOT (e.kind='Осмотр' AND EXISTS (
                  SELECT 1 FROM assessments a WHERE a.culture_id=e.culture_id AND a.note=e.note
                    AND ABS(a.observed_at-e.created_at)<=2000
              ))
            UNION ALL
            SELECT 'measurement',m.id,
                   CASE WHEN m.record_kind='baseline' THEN 'Исходное измерение'
                        ELSE 'Наблюдение роста' END,
                   m.notes,m.observed_at,m.observed_at,NULL,NULL,
                   m.explants,m.shoots,m.plants,m.contaminated,
                   NULL,NULL,NULL,m.recipe_id
            FROM culture_measurements m WHERE m.culture_id=?
            UNION ALL
            SELECT 'legacy',a.id,'Осмотр (архив)',a.note,
                   a.observed_at,a.observed_at,NULL,NULL,
                   NULL,NULL,NULL,a.contaminated_qty,
                   a.starting_qty,a.healthy_qty,a.observation_days,b.recipe_id
            FROM assessments a LEFT JOIN batches b ON b.id=a.batch_id
            WHERE a.culture_id=?
        ) ORDER BY occurred_at DESC,recorded_at DESC,source_id DESC
    """.trimIndent(),arrayOf(cultureId.toString(),cultureId.toString(),cultureId.toString()))
        .use { c -> buildList {
            fun nullableInt(i:Int)=if(c.isNull(i))null else c.getInt(i)
            fun nullableLong(i:Int)=if(c.isNull(i))null else c.getLong(i)
            while(c.moveToNext()) add(JournalEntry(
                c.getString(0),c.getLong(1),c.getString(2),c.getString(3),
                c.getLong(4),c.getLong(5),nullableInt(6),nullableInt(7),
                nullableInt(8),nullableInt(9),nullableInt(10),nullableInt(11),
                nullableInt(12),nullableInt(13),nullableInt(14),nullableLong(15)
            ))
        } }
