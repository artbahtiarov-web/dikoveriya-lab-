"""Migration rehearsal using actual CREATE TABLE strings from Kotlin sources.

This is an in-memory v7-shaped SQLite fixture, NOT an Android on-device
instrumentation test or a claim that a user's actual database was read.
"""
import pathlib
import re
import sqlite3
import unittest

ROOT=pathlib.Path(__file__).resolve().parents[1]
DB=(ROOT/"app/src/main/java/ru/dikoveriya/lab/LabDatabase.kt").read_text(encoding="utf8")
V8=(ROOT/"app/src/main/java/ru/dikoveriya/lab/LabCoreV8.kt").read_text(encoding="utf8")

CREATE_SQL=re.findall(r'db\.execSQL\("(CREATE TABLE [^"]+)"\)',DB)
assert len(CREATE_SQL)>20, "Unexpected Kotlin CREATE TABLE schema"

def old_v7():
    connection=sqlite3.connect(":memory:")
    connection.execute("PRAGMA foreign_keys=ON")
    for command in CREATE_SQL:
        # Only v8 adds the record_kind discriminator; all original v7 columns stay present.
        if "CREATE TABLE IF NOT EXISTS culture_measurements (" in command:
            command=command.replace(", record_kind TEXT NOT NULL DEFAULT 'observation'","")
        connection.execute(command)
    connection.executescript("""
        INSERT INTO plants(id,name) VALUES(1,'Alocasia Black Velvet Aurea');
        INSERT INTO recipes(id,family,version,name,purpose,status,created_at)
            VALUES(1,'ms-A',1,'MS test','Размножение','Утверждена',100);
        INSERT INTO batches(id,code,recipe_id,volume_ml,prepared_at,state,actual_json)
            VALUES(1,'B-001',1,250,100,'Готова','{}');
        INSERT INTO cultures(id,code,plant_id,quantity,quantity_kind,status,
                    passage,created_at,stage,location,medium_batch_id,medium_recipe_id)
            VALUES(1,'LAB-000001',1,10,'Не уточнено','В культуре',0,100,
                   'В пробирке','A1',1,1);
        INSERT INTO events(id,culture_id,kind,note,created_at,occurred_at)
            VALUES(1,1,'Осмотр','Живы',200,200);
        INSERT INTO assessments(id,culture_id,batch_id,starting_qty,healthy_qty,
                    contaminated_qty,observation_days,note,observed_at)
            VALUES(1,1,1,10,7,0,14,'Живы',200);
        INSERT INTO culture_measurements(id,culture_id,recipe_id,observed_at,
                    explants,shoots,plants,notes)
            VALUES(1,1,1,100,10,NULL,0,'Показатели при создании'),
                  (2,1,1,400,NULL,0,NULL,'Побеги не обнаружены');
        INSERT INTO culture_recipe_history(culture_id,recipe_id,applied_at,note)
            VALUES(1,1,100,'При создании');
    """)
    return connection

def upgrade_v8(db):
    # SQL statement texts are cross-checked against Kotlin migration helpers.
    assert 'ALTER TABLE culture_measurements ADD COLUMN record_kind' in V8
    assert "SET record_kind='baseline' WHERE notes='Показатели при создании'" in V8
    assert "LabCoreV8.migrateFrom7(db)" in DB
    db.execute("ALTER TABLE culture_measurements ADD COLUMN record_kind TEXT NOT NULL DEFAULT 'observation'")
    db.execute("UPDATE culture_measurements SET record_kind='baseline' WHERE notes='Показатели при создании'")
    ddl=re.search(r'db\.execSQL\("""(CREATE TABLE IF NOT EXISTS culture_audit.*?)"""\.trimIndent\(\)\)',V8,re.S)
    trigger=re.search(r'db\.execSQL\("""(CREATE TRIGGER IF NOT EXISTS trg_culture_audit_update.*?)"""\.trimIndent\(\)\)',V8,re.S)
    assert ddl and trigger
    db.execute(ddl.group(1))
    db.executescript(trigger.group(1)+";")
    db.execute("""INSERT INTO culture_audit(culture_id,recorded_at,source,after_qty,
        after_kind,after_status,after_batch_id,after_recipe_id,after_stage,after_location)
        SELECT id,1000,'Состояние до миграции',quantity,quantity_kind,status,
          medium_batch_id,medium_recipe_id,stage,location FROM cultures""")

class UpgradeRestoreRehearsal(unittest.TestCase):
    def test_historical_rows_survive_v7_to_v8_and_audit_starts_with_known_state(self):
        db=old_v7()
        before={table:db.execute("SELECT COUNT(*) FROM "+table).fetchone()[0]
                for table in ("cultures","events","assessments","culture_measurements",
                              "recipes","batches","culture_recipe_history")}
        upgrade_v8(db)
        after={table:db.execute("SELECT COUNT(*) FROM "+table).fetchone()[0] for table in before}
        self.assertEqual(before,after)
        self.assertEqual(db.execute("PRAGMA foreign_key_check").fetchall(),[])
        self.assertEqual(db.execute("PRAGMA integrity_check").fetchone()[0],"ok")
        self.assertEqual(db.execute("SELECT record_kind FROM culture_measurements ORDER BY id").fetchall(),
                         [("baseline",),("observation",)])
        self.assertEqual(db.execute("SELECT shoots,plants FROM culture_measurements ORDER BY id").fetchall(),
                         [(None,0),(0,None)])
        source,before_quantity,after_quantity=db.execute(
            "SELECT source,before_qty,after_qty FROM culture_audit").fetchone()
        self.assertEqual((source,before_quantity,after_quantity),
                         ("Состояние до миграции",None,10))

    def test_failed_mutation_and_recovery_roll_back_without_erasing_legacy_data(self):
        db=old_v7()
        upgrade_v8(db)
        db.commit()
        db.execute("BEGIN")
        db.execute("UPDATE cultures SET quantity=7,quantity_kind='Побеги' WHERE id=1")
        self.assertEqual(db.execute("SELECT COUNT(*) FROM culture_audit").fetchone()[0],2)
        try:
            db.execute("UPDATE cultures SET medium_recipe_id=99999 WHERE id=1")
        except sqlite3.IntegrityError:
            pass
        else:
            self.fail("Foreign-key violation was not rejected")
        db.rollback()
        self.assertEqual(db.execute("SELECT quantity,quantity_kind FROM cultures").fetchone(),
                         (10,"Не уточнено"))
        self.assertEqual(db.execute("SELECT COUNT(*) FROM culture_audit").fetchone()[0],1)
        self.assertEqual(db.execute("SELECT COUNT(*) FROM assessments").fetchone()[0],1)
        self.assertEqual(db.execute("SELECT COUNT(*) FROM culture_measurements").fetchone()[0],2)

    def test_backup_json_version_table_contract(self):
        self.assertIn('BACKUP_ORDER_V7=BACKUP_ORDER_V6+listOf("culture_measurements","culture_recipe_history")',DB)
        self.assertIn('BACKUP_ORDER_V8=BACKUP_ORDER_V7+listOf("culture_audit")',DB)
        self.assertIn('7->BACKUP_ORDER_V7',DB)
        self.assertIn('root.optInt("schema") in 4..13',DB)
        self.assertIn('delete("culture_audit",null,null)',DB)
        self.assertIn('LabCoreV10.validate(this)',DB)

if __name__=="__main__":
    unittest.main()
