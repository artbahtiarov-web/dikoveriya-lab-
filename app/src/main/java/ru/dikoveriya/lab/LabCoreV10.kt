package ru.dikoveriya.lab

import android.database.sqlite.SQLiteDatabase

/**
 * v10 is additive. Existing biology, transfer lineage, medium provenance,
 * tasks, photos, and historical audits are never overwritten on migration.
 * Both the label layouts and safe archival actions participate in JSON/ZIP backup.
 */
internal object LabCoreV10 {
    val backupTables = listOf("case_tasks","label_templates","label_overrides",
        "entity_archives","entity_changes")

    fun create(db:SQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS case_tasks(
            case_id INTEGER NOT NULL REFERENCES control_cases(id),
            task_id INTEGER NOT NULL REFERENCES tasks(id),
            PRIMARY KEY(case_id,task_id),
            UNIQUE(task_id)
        )""".trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_case_tasks_task ON case_tasks(task_id)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS label_templates(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL COLLATE NOCASE UNIQUE,
            layout_json TEXT NOT NULL,
            created_at INTEGER NOT NULL,
            updated_at INTEGER NOT NULL
        )""".trimIndent())
        db.execSQL("""CREATE TABLE IF NOT EXISTS label_overrides(
            culture_id INTEGER PRIMARY KEY REFERENCES cultures(id),
            layout_json TEXT NOT NULL,
            modified_at INTEGER NOT NULL
        )""".trimIndent())
        db.execSQL("""CREATE TABLE IF NOT EXISTS entity_archives(
            entity_type TEXT NOT NULL,
            entity_id INTEGER NOT NULL,
            reason TEXT NOT NULL,
            archived_at INTEGER NOT NULL,
            PRIMARY KEY(entity_type,entity_id)
        )""".trimIndent())
        db.execSQL("""CREATE TABLE IF NOT EXISTS entity_changes(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            entity_type TEXT NOT NULL,
            entity_id INTEGER NOT NULL,
            before_json TEXT NOT NULL,
            after_json TEXT NOT NULL,
            reason TEXT NOT NULL,
            changed_at INTEGER NOT NULL
        )""".trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_entity_changes ON entity_changes(entity_type,entity_id,changed_at DESC)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_entity_archives_type ON entity_archives(entity_type,archived_at)")
    }

    fun validate(db:SQLiteDatabase) {
        LabCoreV9.validate(db)
        db.rawQuery("""SELECT name FROM label_templates
            WHERE length(trim(name))=0 OR length(layout_json)>30000 LIMIT 1""",null).use{
            require(!it.moveToFirst()){"Повреждён шаблон этикетки"}
        }
        db.rawQuery("""SELECT culture_id FROM label_overrides
            WHERE length(layout_json)>30000 LIMIT 1""",null).use{
            require(!it.moveToFirst()){"Повреждён индивидуальный макет"}
        }
    }
}
