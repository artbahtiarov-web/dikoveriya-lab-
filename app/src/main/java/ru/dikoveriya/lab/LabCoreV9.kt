package ru.dikoveriya.lab

import android.database.sqlite.SQLiteDatabase

/**
 * v9 is strictly additive: historical containers/observations are never reclassified.
 * No implicit group membership, fabricated storage coordinates or urgency defaults.
 * Migration executes in SQLiteOpenHelper's onUpgrade transaction.
 */
internal object LabCoreV9 {
    val backupTables = listOf(
        "production_groups","culture_groups","group_history",
        "storage_locations","culture_storage","storage_movements",
        "control_cases","control_case_history","task_metadata","task_history","saved_views"
    )

    fun create(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS production_groups (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            code TEXT NOT NULL UNIQUE,
            title TEXT NOT NULL,
            plant_id INTEGER NOT NULL REFERENCES plants(id),
            mother_id INTEGER REFERENCES mother_plants(id),
            note TEXT NOT NULL DEFAULT '',
            created_at INTEGER NOT NULL
        )""".trimIndent())
        db.execSQL("""CREATE TABLE IF NOT EXISTS culture_groups (
            culture_id INTEGER PRIMARY KEY REFERENCES cultures(id),
            group_id INTEGER NOT NULL REFERENCES production_groups(id),
            linked_at INTEGER NOT NULL
        )""".trimIndent())
        db.execSQL("""CREATE TABLE IF NOT EXISTS group_history (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            culture_id INTEGER NOT NULL REFERENCES cultures(id),
            previous_group_id INTEGER REFERENCES production_groups(id),
            new_group_id INTEGER REFERENCES production_groups(id),
            recorded_at INTEGER NOT NULL,
            reason TEXT NOT NULL
        )""".trimIndent())
        db.execSQL("""CREATE TABLE IF NOT EXISTS storage_locations (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            parent_id INTEGER REFERENCES storage_locations(id),
            level TEXT NOT NULL CHECK(level IN ('Помещение','Стеллаж','Полка','Позиция')),
            label TEXT NOT NULL CHECK(length(trim(label)) BETWEEN 1 AND 80),
            full_path TEXT NOT NULL UNIQUE,
            created_at INTEGER NOT NULL
        )""".trimIndent())
        db.execSQL("""CREATE TABLE IF NOT EXISTS culture_storage (
            culture_id INTEGER PRIMARY KEY REFERENCES cultures(id),
            location_id INTEGER NOT NULL REFERENCES storage_locations(id),
            assigned_at INTEGER NOT NULL
        )""".trimIndent())
        db.execSQL("""CREATE TABLE IF NOT EXISTS storage_movements (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            culture_id INTEGER NOT NULL REFERENCES cultures(id),
            from_location_id INTEGER REFERENCES storage_locations(id),
            to_location_id INTEGER NOT NULL REFERENCES storage_locations(id),
            previous_text TEXT NOT NULL,
            changed_at INTEGER NOT NULL,
            reason TEXT NOT NULL,
            operation_id TEXT NOT NULL REFERENCES lab_operations(id)
        )""".trimIndent())
        db.execSQL("""CREATE TABLE IF NOT EXISTS control_cases (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            culture_id INTEGER NOT NULL REFERENCES cultures(id),
            reason TEXT NOT NULL CHECK(length(trim(reason)) > 0),
            severity TEXT NOT NULL CHECK(severity IN ('Обычный','Важный','Критический')),
            due_at INTEGER,
            required_action TEXT NOT NULL DEFAULT '',
            opened_at INTEGER NOT NULL,
            closed_at INTEGER,
            resolution TEXT NOT NULL DEFAULT ''
        )""".trimIndent())
        db.execSQL("""CREATE TABLE IF NOT EXISTS control_case_history (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            case_id INTEGER NOT NULL REFERENCES control_cases(id),
            recorded_at INTEGER NOT NULL,
            action TEXT NOT NULL,
            note TEXT NOT NULL
        )""".trimIndent())
        db.execSQL("""CREATE TABLE IF NOT EXISTS task_metadata (
            task_id INTEGER PRIMARY KEY REFERENCES tasks(id),
            priority TEXT NOT NULL DEFAULT 'Обычный'
                CHECK(priority IN ('Низкий','Обычный','Высокий','Срочный')),
            kind TEXT NOT NULL DEFAULT 'Другое',
            group_id INTEGER REFERENCES production_groups(id),
            recipe_id INTEGER REFERENCES recipes(id),
            updated_at INTEGER NOT NULL
        )""".trimIndent())
        db.execSQL("""CREATE TABLE IF NOT EXISTS task_history (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            task_id INTEGER NOT NULL REFERENCES tasks(id),
            recorded_at INTEGER NOT NULL,
            action TEXT NOT NULL,
            previous_due_at INTEGER,
            next_due_at INTEGER,
            previous_done INTEGER,
            next_done INTEGER,
            previous_priority TEXT,
            next_priority TEXT,
            note TEXT NOT NULL DEFAULT ''
        )""".trimIndent())

        db.execSQL("""CREATE TABLE IF NOT EXISTS saved_views (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            screen TEXT NOT NULL,
            name TEXT NOT NULL,
            config_json TEXT NOT NULL,
            created_at INTEGER NOT NULL,
            UNIQUE(screen,name)
        )""".trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_production_groups_plant ON production_groups(plant_id,id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_culture_groups_group ON culture_groups(group_id,culture_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_group_history_culture ON group_history(culture_id,recorded_at)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_storage_parent ON storage_locations(parent_id,level,label)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_culture_storage_location ON culture_storage(location_id,culture_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_storage_movements_culture ON storage_movements(culture_id,changed_at)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_cases_open ON control_cases(closed_at,severity,due_at,culture_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_cases_culture ON control_cases(culture_id,closed_at)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_task_meta_priority ON task_metadata(priority,kind,recipe_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_cultures_stage_status ON cultures(stage,status,created_at DESC,id DESC)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_cultures_status_stage ON cultures(status,stage,created_at DESC,id DESC)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_cultures_updated ON cultures(updated_at DESC,id DESC)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_cultures_recipe_stage ON cultures(medium_recipe_id,stage,id DESC)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_tasks_culture_due ON tasks(culture_id,done,due_at)")
    }

    fun validate(db: SQLiteDatabase) {
        LabCoreV8.validate(db)
        // FK alone cannot ensure that a production group has a homogeneous origin.
        // Reject malformed v9 backups rather than silently rewriting lineage.
        db.rawQuery("""SELECT cg.culture_id FROM culture_groups cg
            JOIN cultures c ON c.id=cg.culture_id
            JOIN production_groups g ON g.id=cg.group_id
            WHERE c.plant_id!=g.plant_id
               OR IFNULL(c.mother_id,-1)!=IFNULL(g.mother_id,-1)
            LIMIT 1""",null).use { c->
            require(!c.moveToFirst()) {
                "В группе смешаны разные сорта или маточники"
            }
        }
        db.rawQuery("""SELECT child.id FROM storage_locations child
            LEFT JOIN storage_locations parent ON parent.id=child.parent_id
            WHERE (child.level='Помещение' AND child.parent_id IS NOT NULL)
               OR (child.level='Стеллаж' AND (parent.id IS NULL OR parent.level!='Помещение'))
               OR (child.level='Полка' AND (parent.id IS NULL OR parent.level!='Стеллаж'))
               OR (child.level='Позиция' AND (parent.id IS NULL OR parent.level!='Полка'))
            LIMIT 1""",null).use{c->
            require(!c.moveToFirst()) { "Нарушена иерархия мест хранения" }
        }
    }
}
