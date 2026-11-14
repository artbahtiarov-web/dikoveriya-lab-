package ru.dikoveriya.lab

import android.database.sqlite.SQLiteDatabase

/**
 * Additive task planning and vegetative propagation schema.
 * All v12 IDs, event rows, photos and previously scheduled tasks remain unchanged.
 */
internal object LabCoreV13 {
    val backupTables=listOf("task_details13","offshoot_groups13","offshoot_members13")

    fun create(db:SQLiteDatabase){
        db.execSQL("""CREATE TABLE IF NOT EXISTS task_details13(
            task_id INTEGER PRIMARY KEY NOT NULL REFERENCES tasks(id),
            object_kind TEXT NOT NULL DEFAULT '',
            object_id INTEGER,
            object_text TEXT NOT NULL DEFAULT '',
            pest TEXT NOT NULL DEFAULT '',
            treatment TEXT NOT NULL DEFAULT '',
            delivery_text TEXT NOT NULL DEFAULT '',
            reminder_at INTEGER,
            ringtone_uri TEXT,
            created_at INTEGER NOT NULL,
            updated_at INTEGER NOT NULL,
            CHECK(reminder_at IS NULL OR reminder_at>0)
        )""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS offshoot_groups13(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            parent_item_id INTEGER NOT NULL REFERENCES plant_items(id),
            event_id INTEGER NOT NULL UNIQUE REFERENCES item_events(id),
            occurred_at INTEGER NOT NULL,
            location_text TEXT NOT NULL DEFAULT '',
            substrate TEXT NOT NULL DEFAULT '',
            note TEXT NOT NULL DEFAULT ''
        )""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS offshoot_members13(
            group_id INTEGER NOT NULL REFERENCES offshoot_groups13(id),
            child_item_id INTEGER NOT NULL UNIQUE REFERENCES plant_items(id),
            propagule_kind TEXT NOT NULL CHECK(propagule_kind IN ('Детка','Клубень')),
            PRIMARY KEY(group_id,child_item_id)
        )""")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_task_details_reminder13 ON task_details13(reminder_at)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_offshoot_parent13 ON offshoot_groups13(parent_item_id,occurred_at)")
    }
    fun validate(db:SQLiteDatabase){
        db.rawQuery("PRAGMA foreign_key_check",null).use{
            require(!it.moveToFirst()){"Нарушены связи задач или размножения"}
        }
    }
}
