package ru.dikoveriya.lab

import android.database.sqlite.SQLiteDatabase

/**
 * Dikoveriya LAB 1.1.0 — additive unified objects and separate project economics.
 *
 * Money is stored in integral kopecks, not Double.
 * Purchases/cash payments must never be booked again as material consumption.
 * Old cultures, audit rows, permanent codes and QR identifiers are not re-keyed.
 */
internal object LabCoreV12 {
    val backupTables = listOf(
        "custom_categories", "plant_items", "item_events", "item_links",
        "item_photos", "custom_field_definitions", "custom_field_values",
        "operation_templates", "purchases", "cash_ledger", "direct_costs",
        "stock_usage", "equipment_assets", "asset_events", "light_profiles",
        "location_layouts", "item_storage_history", "recurring_task_rules", "task_links",
        "sale_details", "item_sales"
    )

    fun create(db:SQLiteDatabase){
        db.execSQL("""CREATE TABLE IF NOT EXISTS custom_categories(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            domain TEXT NOT NULL,
            name TEXT NOT NULL,
            archived INTEGER NOT NULL DEFAULT 0,
            UNIQUE(domain,name)
        )""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS plant_items(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            code TEXT NOT NULL UNIQUE,
            plant_id INTEGER NOT NULL REFERENCES plants(id),
            kind TEXT NOT NULL CHECK(kind IN ('Горшечное','Черенок','Маточник','Другое')),
            stage TEXT NOT NULL DEFAULT 'В работе',
            status TEXT NOT NULL DEFAULT 'Активен',
            acquired_at INTEGER NOT NULL,
            created_at INTEGER NOT NULL,
            supplier TEXT NOT NULL DEFAULT '',
            purchase_cost_minor INTEGER NOT NULL DEFAULT 0 CHECK(purchase_cost_minor>=0),
            parent_item_id INTEGER REFERENCES plant_items(id),
            parent_culture_id INTEGER REFERENCES cultures(id),
            location_id INTEGER REFERENCES storage_locations(id),
            location_text TEXT NOT NULL DEFAULT '',
            pot_volume_ml INTEGER,
            substrate TEXT NOT NULL DEFAULT '',
            rooting_method TEXT NOT NULL DEFAULT '',
            stimulant TEXT NOT NULL DEFAULT '',
            notes TEXT NOT NULL DEFAULT '',
            favorite INTEGER NOT NULL DEFAULT 0,
            cover_filename TEXT,
            updated_at INTEGER NOT NULL
        )""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS item_events(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            item_id INTEGER NOT NULL REFERENCES plant_items(id),
            kind TEXT NOT NULL,
            event_at INTEGER NOT NULL,
            recorded_at INTEGER NOT NULL,
            note TEXT NOT NULL DEFAULT '',
            details_json TEXT NOT NULL DEFAULT '{}',
            reversal_of INTEGER REFERENCES item_events(id),
            cancelled INTEGER NOT NULL DEFAULT 0
        )""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS item_links(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            child_item_id INTEGER NOT NULL REFERENCES plant_items(id),
            parent_item_id INTEGER REFERENCES plant_items(id),
            parent_culture_id INTEGER REFERENCES cultures(id),
            kind TEXT NOT NULL,
            linked_at INTEGER NOT NULL,
            CHECK((parent_item_id IS NULL)!=(parent_culture_id IS NULL))
        )""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS item_photos(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            entity_kind TEXT NOT NULL,
            entity_id INTEGER NOT NULL,
            filename TEXT NOT NULL UNIQUE,
            caption TEXT NOT NULL DEFAULT '',
            added_at INTEGER NOT NULL
        )""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS custom_field_definitions(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            entity_kind TEXT NOT NULL,
            label TEXT NOT NULL,
            value_kind TEXT NOT NULL CHECK(value_kind IN ('text','number','date','boolean','choice','unit','photo','url')),
            choices_json TEXT NOT NULL DEFAULT '[]',
            required INTEGER NOT NULL DEFAULT 0,
            archived INTEGER NOT NULL DEFAULT 0,
            UNIQUE(entity_kind,label)
        )""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS custom_field_values(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            field_id INTEGER NOT NULL REFERENCES custom_field_definitions(id),
            entity_kind TEXT NOT NULL,
            entity_id INTEGER NOT NULL,
            value_json TEXT NOT NULL,
            changed_at INTEGER NOT NULL,
            UNIQUE(field_id,entity_kind,entity_id)
        )""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS operation_templates(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL UNIQUE,
            applicable_kind TEXT NOT NULL DEFAULT 'Все',
            fields_json TEXT NOT NULL DEFAULT '[]',
            archived INTEGER NOT NULL DEFAULT 0
        )""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS purchases(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            title TEXT NOT NULL,
            category TEXT NOT NULL,
            supplier TEXT NOT NULL DEFAULT '',
            supplier_url TEXT NOT NULL DEFAULT '',
            purchased_at INTEGER NOT NULL,
            material_id INTEGER REFERENCES materials(id),
            asset_id INTEGER,
            quantity REAL NOT NULL DEFAULT 0,
            unit TEXT NOT NULL DEFAULT '',
            unit_cost_minor INTEGER NOT NULL DEFAULT 0,
            amount_minor INTEGER NOT NULL CHECK(amount_minor>=0),
            pay_method TEXT NOT NULL DEFAULT '',
            receipt_filename TEXT,
            note TEXT NOT NULL DEFAULT ''
        )""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS cash_ledger(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            direction TEXT NOT NULL CHECK(direction IN ('Расход','Доход')),
            category TEXT NOT NULL,
            amount_minor INTEGER NOT NULL CHECK(amount_minor>=0),
            occurred_at INTEGER NOT NULL,
            source_type TEXT NOT NULL,
            source_id INTEGER,
            note TEXT NOT NULL DEFAULT '',
            UNIQUE(source_type,source_id,direction)
        )""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS direct_costs(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            entity_kind TEXT NOT NULL,
            entity_id INTEGER NOT NULL,
            amount_minor INTEGER NOT NULL CHECK(amount_minor>=0),
            category TEXT NOT NULL,
            occurred_at INTEGER NOT NULL,
            source_type TEXT NOT NULL,
            source_id INTEGER,
            note TEXT NOT NULL DEFAULT ''
        )""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS stock_usage(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            material_id INTEGER NOT NULL REFERENCES materials(id),
            entity_kind TEXT NOT NULL,
            entity_id INTEGER NOT NULL,
            quantity REAL NOT NULL CHECK(quantity>0),
            unit_cost_minor INTEGER NOT NULL CHECK(unit_cost_minor>=0),
            total_minor INTEGER NOT NULL CHECK(total_minor>=0),
            used_at INTEGER NOT NULL,
            note TEXT NOT NULL DEFAULT ''
        )""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS equipment_assets(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            category TEXT NOT NULL DEFAULT 'Оборудование',
            supplier TEXT NOT NULL DEFAULT '',
            purchased_at INTEGER NOT NULL,
            purchase_price_minor INTEGER NOT NULL CHECK(purchase_price_minor>=0),
            location_id INTEGER REFERENCES storage_locations(id),
            location_text TEXT NOT NULL DEFAULT '',
            nominal_watts REAL,
            measured_watts REAL,
            useful_months INTEGER,
            status TEXT NOT NULL DEFAULT 'Используется',
            notes TEXT NOT NULL DEFAULT '',
            photo_filename TEXT
        )""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS asset_events(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            asset_id INTEGER NOT NULL REFERENCES equipment_assets(id),
            kind TEXT NOT NULL,
            note TEXT NOT NULL DEFAULT '',
            occurred_at INTEGER NOT NULL
        )""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS light_profiles(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            asset_id INTEGER REFERENCES equipment_assets(id),
            location_id INTEGER REFERENCES storage_locations(id),
            nominal_watts REAL NOT NULL DEFAULT 0,
            measured_watts REAL NOT NULL DEFAULT 0,
            hours_per_day REAL NOT NULL DEFAULT 0,
            tariff_minor_per_kwh INTEGER NOT NULL DEFAULT 0,
            from_date INTEGER NOT NULL,
            until_date INTEGER,
            measured_ppfd REAL,
            notes TEXT NOT NULL DEFAULT ''
        )""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS location_layouts(
            location_id INTEGER PRIMARY KEY REFERENCES storage_locations(id),
            shelf_count INTEGER NOT NULL DEFAULT 0 CHECK(shelf_count>=0),
            positions_per_shelf INTEGER NOT NULL DEFAULT 0 CHECK(positions_per_shelf>=0),
            notes TEXT NOT NULL DEFAULT ''
        )""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS item_storage_history(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            item_id INTEGER NOT NULL REFERENCES plant_items(id),
            previous_location_id INTEGER REFERENCES storage_locations(id),
            new_location_id INTEGER REFERENCES storage_locations(id),
            location_text TEXT NOT NULL DEFAULT '',
            occurred_at INTEGER NOT NULL,
            note TEXT NOT NULL DEFAULT ''
        )""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS recurring_task_rules(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            title TEXT NOT NULL,
            kind TEXT NOT NULL DEFAULT 'Уход',
            frequency_days INTEGER NOT NULL CHECK(frequency_days>0),
            season_start_month INTEGER NOT NULL DEFAULT 1,
            season_end_month INTEGER NOT NULL DEFAULT 12,
            entity_kind TEXT,
            entity_id INTEGER,
            priority TEXT NOT NULL DEFAULT 'Обычный',
            next_due_at INTEGER NOT NULL,
            last_generated_due INTEGER,
            active INTEGER NOT NULL DEFAULT 1
        )""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS task_links(
            task_id INTEGER PRIMARY KEY REFERENCES tasks(id),
            entity_kind TEXT NOT NULL,
            entity_id INTEGER NOT NULL
        )""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS sale_details(
            sale_id INTEGER PRIMARY KEY REFERENCES sales(id),
            channel TEXT NOT NULL DEFAULT '',
            buyer TEXT NOT NULL DEFAULT '',
            cogs_minor INTEGER,
            selling_cost_minor INTEGER NOT NULL DEFAULT 0,
            note TEXT NOT NULL DEFAULT ''
        )""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS item_sales(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            item_id INTEGER NOT NULL REFERENCES plant_items(id),
            sold_at INTEGER NOT NULL,
            amount_minor INTEGER NOT NULL CHECK(amount_minor>=0),
            cost_minor INTEGER,
            channel TEXT NOT NULL DEFAULT '',
            buyer TEXT NOT NULL DEFAULT '',
            note TEXT NOT NULL DEFAULT ''
        )""")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_items_plant_kind ON plant_items(plant_id,kind,status)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_items_location ON plant_items(location_id,stage)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_item_events_date ON item_events(item_id,event_at DESC)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_cash_date ON cash_ledger(occurred_at,direction)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_purchases_date ON purchases(purchased_at)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_stock_usage_item ON stock_usage(entity_kind,entity_id,used_at)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_costs_item ON direct_costs(entity_kind,entity_id,occurred_at)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_recurrence_due ON recurring_task_rules(active,next_due_at)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_item_sales_date ON item_sales(sold_at)")

        // Legacy confirmed sales have real cash receipts, but no evidenced historical COGS.
        db.execSQL("""INSERT OR IGNORE INTO cash_ledger
            (direction,category,amount_minor,occurred_at,source_type,source_id,note)
            SELECT 'Доход','Продажа',CAST(ROUND(price*100) AS INTEGER),
            sold_at,'legacy_sale',id,'Продажа до версии 1.1.0'
            FROM sales WHERE price>=0""")
    }

    fun validate(db:SQLiteDatabase){
        db.rawQuery("PRAGMA foreign_key_check",null).use{c->
            require(!c.moveToFirst()){"Нарушены связи данных при обновлении до 1.1.0"}
        }
        db.rawQuery("""SELECT COUNT(*) FROM plant_items
            WHERE (parent_item_id IS NOT NULL AND parent_culture_id IS NOT NULL)
            OR purchase_cost_minor<0""",null).use{c->
            require(c.moveToFirst() && c.getInt(0)==0){"Некорректное происхождение растения"}
        }
        db.rawQuery("""SELECT COUNT(*) FROM cash_ledger WHERE amount_minor<0""",null).use{c->
            require(c.moveToFirst() && c.getInt(0)==0){"Некорректный денежный журнал"}
        }
    }
}
