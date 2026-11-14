"""Executable SQLite regression checks for release 0.6.1.

Exercise the actual CREATE TABLE/TRIGGER strings in LabCoreV8.kt using SQLite,
then verify atomic trigger rollback, nullable observations and migration state.
Kotlin compilation and Android Gradle unit tests run separately in PR CI.
"""
from pathlib import Path
import re
import sqlite3
import unittest

ROOT=Path(__file__).resolve().parents[1]
MIG=(ROOT/"app/src/main/java/ru/dikoveriya/lab/LabCoreV8.kt").read_text(encoding="utf-8")
DB=(ROOT/"app/src/main/java/ru/dikoveriya/lab/LabDatabase.kt").read_text(encoding="utf-8")
FLOW=(ROOT/"app/src/main/java/ru/dikoveriya/lab/LabFlowStore.kt").read_text(encoding="utf-8")
JOURNAL=(ROOT/"app/src/main/java/ru/dikoveriya/lab/LabJournal.kt").read_text(encoding="utf-8")

def kotlin_sql(start):
    match=re.search(r'db\.execSQL\("""('+start+r'.*?)"""\.trimIndent\(\)\)',MIG,re.S)
    if match is None:
        raise AssertionError("SQL missing: "+start)
    return match.group(1)

AUDIT_TABLE=kotlin_sql(r'CREATE TABLE IF NOT EXISTS culture_audit')
AUDIT_TRIGGER=kotlin_sql(r'CREATE TRIGGER IF NOT EXISTS trg_culture_audit_update')

def database():
    db=sqlite3.connect(":memory:")
    db.execute("PRAGMA foreign_keys=ON")
    db.executescript("""
        CREATE TABLE recipes(id INTEGER PRIMARY KEY);
        CREATE TABLE batches(id INTEGER PRIMARY KEY,recipe_id INTEGER REFERENCES recipes(id));
        CREATE TABLE lab_operations(id TEXT PRIMARY KEY);
        CREATE TABLE cultures(
            id INTEGER PRIMARY KEY,quantity INTEGER NOT NULL CHECK(quantity>=0),
            quantity_kind TEXT NOT NULL,status TEXT NOT NULL,stage TEXT NOT NULL,
            location TEXT NOT NULL,medium_batch_id INTEGER REFERENCES batches(id),
            medium_recipe_id INTEGER REFERENCES recipes(id)
        );
        CREATE TABLE culture_measurements(
            id INTEGER PRIMARY KEY,culture_id INTEGER REFERENCES cultures(id),
            recipe_id INTEGER REFERENCES recipes(id),observed_at INTEGER NOT NULL,
            explants INTEGER,shoots INTEGER,plants INTEGER,contaminated INTEGER,notes TEXT NOT NULL
        );
        INSERT INTO recipes VALUES (1),(2);
        INSERT INTO batches VALUES(9,1);
        INSERT INTO cultures VALUES(5,12,'Не уточнено','В культуре','В пробирке','A1',9,1);
        INSERT INTO culture_measurements VALUES
           (1,5,1,100,12,NULL,0,NULL,'Показатели при создании'),
           (2,5,1,200,NULL,0,NULL,NULL,'Побегов нет'),
           (3,5,1,300,NULL,NULL,NULL,NULL,'Внешний осмотр');
    """)
    db.execute("ALTER TABLE culture_measurements ADD COLUMN record_kind TEXT NOT NULL DEFAULT 'observation'")
    db.execute("UPDATE culture_measurements SET record_kind='baseline' WHERE notes='Показатели при создании'")
    db.execute(AUDIT_TABLE)
    db.executescript(AUDIT_TRIGGER+";")
    return db

class SQLiteV8Tests(unittest.TestCase):
    def test_baseline_observation_and_null_are_distinct(self):
        db=database()
        got=db.execute("SELECT record_kind,explants,shoots,plants FROM culture_measurements ORDER BY id").fetchall()
        self.assertEqual(got,[
            ("baseline",12,None,0),
            ("observation",None,0,None),
            ("observation",None,None,None),
        ])

    def test_audit_records_complete_before_after_state(self):
        db=database()
        db.execute("UPDATE cultures SET quantity=9,quantity_kind='Побеги',medium_recipe_id=2,medium_batch_id=NULL WHERE id=5")
        rows=db.execute("""SELECT before_qty,after_qty,before_kind,after_kind,
            before_recipe_id,after_recipe_id,before_batch_id,after_batch_id
            FROM culture_audit""").fetchall()
        self.assertEqual(rows,[(12,9,'Не уточнено','Побеги',1,2,9,None)])
        self.assertEqual(db.execute("PRAGMA foreign_key_check").fetchall(),[])

    def test_trigger_audit_rollback_is_atomic(self):
        db=database()
        db.commit()
        db.execute("BEGIN")
        db.execute("UPDATE cultures SET quantity=8,status='На контроле' WHERE id=5")
        self.assertEqual(db.execute("SELECT COUNT(*) FROM culture_audit").fetchone()[0],1)
        db.rollback()
        self.assertEqual(db.execute("SELECT quantity,status FROM cultures").fetchone(),(12,'В культуре'))
        self.assertEqual(db.execute("SELECT COUNT(*) FROM culture_audit").fetchone()[0],0)

    def test_failed_foreign_key_update_has_no_audit(self):
        db=database()
        with self.assertRaises(sqlite3.IntegrityError):
            db.execute("UPDATE cultures SET medium_recipe_id=99999 WHERE id=5")
        self.assertEqual(db.execute("SELECT COUNT(*) FROM culture_audit").fetchone()[0],0)

    def test_backup_schema_and_predecessors(self):
        self.assertIn('SQLiteOpenHelper(context, "dikoveriya_lab.db", null, 13)',DB)
        self.assertIn('BACKUP_ORDER_V7=BACKUP_ORDER_V6+',DB)
        self.assertIn('BACKUP_ORDER_V8=BACKUP_ORDER_V7+listOf("culture_audit")',DB)
        self.assertIn('root.optInt("schema") in 4..13',DB)
        self.assertIn('if(fromVersion<8)',DB)
        self.assertIn("LabCoreV10.validate(this)",DB)
        self.assertIn("db.beginTransactionNonExclusive()",DB)

    def test_all_mutations_and_medium_change_share_transactional_audit(self):
        for name in ("splitCulture","mergeCultures","recordLoss","correctQuantity","clarifyQuantityUnit"):
            with self.subTest(name=name):
                block=DB.split("fun "+name+"(",1)[1].split("\n    fun ",1)[0]
                self.assertIn("writableDatabase.tx {",block)
        self.assertIn('insertOrThrow("lab_operations"',FLOW)
        self.assertIn('"kind" to "Смена среды"',FLOW)
        self.assertIn('"medium_batch_id" to null',FLOW)
        self.assertIn('"event_group" to op',FLOW)
        self.assertIn("assignDaughterRecipe(this,child,source.recipeId,part.batchId,occurredAt,part.recipeId)",DB)

    def test_journal_protects_all_sources_without_conflating_measurements(self):
        self.assertIn("FROM events e",JOURNAL)
        self.assertIn("FROM culture_measurements m",JOURNAL)
        self.assertIn("FROM assessments a",JOURNAL)
        self.assertIn("m.record_kind='baseline'",JOURNAL)
        self.assertIn("a.healthy_qty",JOURNAL)
        self.assertIn("ABS(a.observed_at-e.created_at)<=2000",JOURNAL)

if __name__=="__main__":
    unittest.main()
