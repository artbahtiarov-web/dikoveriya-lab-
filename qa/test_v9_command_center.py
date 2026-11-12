"""Executable SQLite v9 migration and scale smoke checks (not Android device tests).

Uses the actual CREATE TABLE and INDEX statements from LabCoreV9.kt.
Historical unknown units and legacy free-text locations must remain untouched.
"""
import pathlib
import re
import sqlite3
import time
import unittest

ROOT=pathlib.Path(__file__).resolve().parents[1]
SRC=ROOT/"app/src/main/java/ru/dikoveriya/lab"
V9=(SRC/"LabCoreV9.kt").read_text("utf-8")
DB=(SRC/"LabDatabase.kt").read_text("utf-8")
REG=(SRC/"Lab072Registry.kt").read_text("utf-8")
ACT=(SRC/"Lab072Actions.kt").read_text("utf-8")
NAV=(SRC/"Lab07Navigation.kt").read_text("utf-8")
UI=(SRC/"Lab072RegistryUi.kt").read_text("utf-8")
WIDGETS=(SRC/"Lab07Widgets.kt").read_text("utf-8")
TASKS=(SRC/"Lab072TasksUi.kt").read_text("utf-8")
BACKUP=(SRC/"LabBackup.kt").read_text("utf-8")

DDL=re.findall(r'db\.execSQL\("""(CREATE TABLE IF NOT EXISTS .*?)"""\.trimIndent\(\)\)',V9,re.S)
INDICES=re.findall(r'db\.execSQL\("(CREATE INDEX IF NOT EXISTS [^"]+)"\)',V9)

def fixture():
    conn=sqlite3.connect(":memory:")
    conn.execute("PRAGMA foreign_keys=ON")
    conn.executescript("""
        CREATE TABLE plants(id INTEGER PRIMARY KEY,name TEXT);
        CREATE TABLE mother_plants(id INTEGER PRIMARY KEY,plant_id INTEGER REFERENCES plants(id));
        CREATE TABLE recipes(id INTEGER PRIMARY KEY,name TEXT,version INTEGER);
        CREATE TABLE lab_operations(id TEXT PRIMARY KEY,kind TEXT);
        CREATE TABLE cultures(
            id INTEGER PRIMARY KEY, code TEXT UNIQUE,plant_id INTEGER REFERENCES plants(id),
            mother_id INTEGER REFERENCES mother_plants(id),quantity INTEGER,
            quantity_kind TEXT,status TEXT,stage TEXT,created_at INTEGER,
            updated_at INTEGER,location TEXT,medium_recipe_id INTEGER REFERENCES recipes(id));
        CREATE TABLE tasks(id INTEGER PRIMARY KEY,culture_id INTEGER REFERENCES cultures(id),
            title TEXT,due_at INTEGER,done INTEGER);
        CREATE TABLE culture_measurements(id INTEGER PRIMARY KEY,culture_id INTEGER,
            record_kind TEXT,observed_at INTEGER,shoots INTEGER,plants INTEGER);
        CREATE TABLE culture_audit(id INTEGER PRIMARY KEY,culture_id INTEGER,
            recorded_at INTEGER,source TEXT,operation_id TEXT);
        CREATE TABLE events(id INTEGER PRIMARY KEY,culture_id INTEGER,kind TEXT,note TEXT,
            created_at INTEGER,occurred_at INTEGER,event_group TEXT);
        INSERT INTO plants VALUES(1,'Alocasia');
        INSERT INTO cultures VALUES
          (1,'LAB-000001',1,NULL,7,'Не уточнено','В культуре','В пробирке',
           100,100,'Старый свободный текст',NULL);
        INSERT INTO tasks VALUES(1,1,'Проверить',200,0);
        INSERT INTO culture_measurements VALUES
          (1,1,'baseline',100,0,NULL),(2,1,'observation',200,NULL,0);
    """)
    return conn

def upgrade(conn):
    for sql in DDL:
        conn.execute(sql)
    for index in INDICES:
        conn.execute(index)

class LabV9Migration(unittest.TestCase):
    def test_real_sql_has_expected_new_tables_and_indices(self):
        self.assertGreaterEqual(len(DDL),11)
        self.assertGreaterEqual(len(INDICES),11)
        db=fixture()
        upgrade(db)
        tables={row[0] for row in db.execute("SELECT name FROM sqlite_master WHERE type='table'")}
        for name in ("production_groups","culture_groups","group_history",
                     "storage_locations","culture_storage","storage_movements",
                     "control_cases","control_case_history","task_metadata",
                     "task_history","saved_views"):
            self.assertIn(name,tables)

    def test_v8_rows_unchanged_and_unknowns_not_inferred(self):
        db=fixture()
        old=db.execute("SELECT * FROM cultures WHERE id=1").fetchone()
        old_measure=db.execute("SELECT * FROM culture_measurements ORDER BY id").fetchall()
        upgrade(db)
        self.assertEqual(db.execute("SELECT * FROM cultures WHERE id=1").fetchone(),old)
        self.assertEqual(db.execute("SELECT * FROM culture_measurements ORDER BY id").fetchall(),old_measure)
        self.assertEqual(db.execute("SELECT * FROM culture_groups").fetchall(),[])
        self.assertEqual(db.execute("SELECT * FROM culture_storage").fetchall(),[])
        self.assertEqual(db.execute("SELECT * FROM control_cases").fetchall(),[])
        self.assertEqual(db.execute("PRAGMA foreign_key_check").fetchall(),[])
        self.assertEqual(db.execute("PRAGMA integrity_check").fetchone()[0],"ok")

    def test_group_and_location_foreign_key_rollback(self):
        db=fixture()
        upgrade(db)
        db.commit()
        db.execute("BEGIN")
        db.execute("INSERT INTO production_groups(code,title,plant_id,note,created_at) VALUES('ГР-000001','Линия',1,'',100)")
        db.execute("INSERT INTO culture_groups VALUES(1,1,100)")
        db.execute("INSERT INTO storage_locations(parent_id,level,label,full_path,created_at) VALUES(NULL,'Помещение','Комната','Комната',100)")
        with self.assertRaises(sqlite3.IntegrityError):
            db.execute("INSERT INTO culture_storage VALUES(1,999,100)")
        db.rollback()
        for table in ("production_groups","culture_groups","storage_locations","culture_storage"):
            self.assertEqual(db.execute("SELECT COUNT(*) FROM "+table).fetchone()[0],0)
        self.assertEqual(db.execute("SELECT location,quantity_kind FROM cultures WHERE id=1").fetchone(),
                         ("Старый свободный текст","Не уточнено"))

    def test_v9_backup_is_parent_first_and_previous_json_supported(self):
        self.assertIn('null, 9)',DB)
        self.assertIn('put("schema",9)',DB)
        self.assertIn('BACKUP_ORDER_V8=BACKUP_ORDER_V7+listOf("culture_audit")',DB)
        self.assertIn('BACKUP_ORDER=BACKUP_ORDER_V8+LabCoreV9.backupTables',DB)
        self.assertIn('8->BACKUP_ORDER_V8',DB)
        self.assertIn('root.optInt("schema") in 4..9',DB)
        self.assertIn('root.getInt("schema") in 4..9',BACKUP)
        self.assertIn('LabCoreV9.create(db)',DB)
        self.assertIn('if (oldVersion < 9) LabCoreV9.create(db)',DB)
        self.assertIn('LabCoreV9.validate(this)',DB)

    def test_10k_paged_sql_and_indexes(self):
        db=fixture()
        upgrade(db)
        db.executemany("""INSERT INTO cultures VALUES(?,?,?,?,?,?,?,?,?,?,?,?)""",
            [(i,"LAB-%06d"%i,1,None,12,"Побеги","В культуре",
              "В пробирке" if i%3 else "Адаптация",i*10,i*10,"Стеллаж А",None)
             for i in range(2,10002)])
        db.commit()
        started=time.perf_counter()
        count=db.execute("""SELECT COUNT(*) FROM cultures
            WHERE stage='В пробирке' AND status IN ('В культуре','На контроле','Карантин')""").fetchone()[0]
        page=db.execute("""SELECT id,code FROM cultures
            WHERE stage='В пробирке' AND status IN ('В культуре','На контроле','Карантин')
            ORDER BY created_at DESC,id DESC LIMIT 40 OFFSET 0""").fetchall()
        elapsed=time.perf_counter()-started
        self.assertGreater(count,6000)
        self.assertEqual(len(page),40)
        self.assertEqual(len({x[0] for x in page}),40)
        self.assertLess(elapsed,4.0,"SQL query exceeds a generous CI sanity ceiling")
        plan=" ".join(str(x) for x in db.execute("""EXPLAIN QUERY PLAN SELECT id
            FROM cultures WHERE stage='В пробирке' AND status='В культуре'
            ORDER BY created_at DESC,id DESC LIMIT 40""").fetchall())
        self.assertIn("INDEX",plan.upper())

    def test_no_unbounded_container_scan_in_new_registry(self):
        self.assertIn("LIMIT $limit OFFSET $offset",REG)
        self.assertIn("limit in 1..100",REG)
        self.assertIn("countCultures072",REG)
        self.assertIn("Dispatchers.IO",UI)
        self.assertIn("latestObserved",REG)
        self.assertIn("AND m.shoots IS NOT NULL",REG)
        self.assertIn("AND m.plants IS NOT NULL",REG)
        self.assertIn("last_shoots DESC",REG)
        self.assertIn("last_plants DESC",REG)
        self.assertIn("findCulture072(code)",(SRC/"MainActivity.kt").read_text("utf-8"))

    def test_four_action_cards_have_clear_names_and_real_routes(self):
        for title in ("В пробирках","Требуют внимания","Растут","Задачи"):
            self.assertIn('"'+title+'"',WIDGETS)
        self.assertIn("Card(onClick=onClick",WIDGETS)
        self.assertIn('onMetric("vitro")',WIDGETS)
        self.assertIn('onMetric("attention")',WIDGETS)
        self.assertIn('onMetric("growth")',WIDGETS)
        self.assertIn('onMetric("tasks")',WIDGETS)
        self.assertIn("Lab07Navigation.work(key)",(SRC/"MainActivity.kt").read_text("utf-8"))
        self.assertIn('const val TASKS="tasks"',NAV)

    def test_mass_actions_are_transactional_and_factual(self):
        self.assertIn("fun LabDatabase.createTasksBulk072(",ACT)
        self.assertIn("fun LabDatabase.flagControl072(",ACT)
        self.assertIn("fun LabDatabase.moveContainers072(",ACT)
        self.assertIn("fun LabDatabase.createGroup072(",ACT)
        self.assertIn("checkedIds072(ids)",ACT)
        self.assertIn("operation_changes",ACT)
        self.assertIn("group_history",ACT)
        self.assertIn("storage_movements",ACT)
        self.assertIn("control_case_history",ACT)
        self.assertIn("task_history",ACT)
        self.assertIn("val beforeAudit=auditTop072(id)",ACT)
        self.assertIn("id>?",ACT)
        self.assertIn("fun LabDatabase.resolveCase072(",ACT)

    def test_task_filters_avoid_global_scans(self):
        self.assertIn("fun LabDatabase.taskCount072(",ACT)
        self.assertIn("SELECT COUNT(*) FROM tasks t",ACT)
        self.assertIn('"Сегодня"',ACT)
        self.assertIn("priority",TASKS)
        self.assertIn("recipeId",TASKS)
        self.assertIn("plantId",TASKS)
        self.assertIn("saveView072",TASKS)

if __name__=="__main__":
    unittest.main()
