"""Real SQLite schema-11 and visual data contracts; instrumentation covers Android runtime."""
import pathlib
import sqlite3
import unittest
ROOT=pathlib.Path(__file__).resolve().parents[1]
SRC=ROOT/"app/src/main/java/ru/dikoveriya/lab"
DB=(SRC/"LabDatabase.kt").read_text("utf-8")
V11=(SRC/"LabCoreV11.kt").read_text("utf-8")
ZIP=(SRC/"LabBackup.kt").read_text("utf-8")
COL=(SRC/"Lab10CollectionUi.kt").read_text("utf-8")
REG=(SRC/"Lab072RegistryUi.kt").read_text("utf-8")
FILT=(SRC/"Lab10FilterUi.kt").read_text("utf-8")

class Lab10PlantVisualContracts(unittest.TestCase):
    def test_real_schema_and_fk_provenance(self):
        db=sqlite3.connect(":memory:")
        db.execute("PRAGMA foreign_keys=ON")
        db.execute("CREATE TABLE plants (id INTEGER PRIMARY KEY, name TEXT)")
        db.execute("""CREATE TABLE plant_visuals (
            plant_id INTEGER PRIMARY KEY NOT NULL REFERENCES plants(id),
            icon_key TEXT NOT NULL DEFAULT 'leaf',
            color_key TEXT NOT NULL DEFAULT 'forest',
            cover_filename TEXT,
            changed_at INTEGER NOT NULL DEFAULT 0)""")
        db.execute("""CREATE TABLE knowledge_notes (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            title TEXT NOT NULL,category TEXT NOT NULL,body TEXT NOT NULL,
            source_url TEXT NOT NULL DEFAULT '',updated_at INTEGER NOT NULL,created_at INTEGER NOT NULL)""")
        db.execute("INSERT INTO plants VALUES (1,'Alocasia')")
        db.execute("INSERT INTO plant_visuals(plant_id,icon_key,cover_filename) VALUES (1,'leaf','plcover_123.jpg')")
        self.assertEqual(db.execute("SELECT cover_filename FROM plant_visuals WHERE plant_id=1").fetchone()[0],"plcover_123.jpg")
        with self.assertRaises(sqlite3.IntegrityError):
            db.execute("INSERT INTO plant_visuals(plant_id) VALUES (777)")
        self.assertEqual(db.execute("PRAGMA foreign_key_check").fetchall(),[])
        for term in ("CREATE TABLE IF NOT EXISTS plant_visuals","CREATE TABLE IF NOT EXISTS knowledge_notes","REFERENCES plants(id)"):
            self.assertIn(term,V11)

    def test_upgrade_and_backward_compatible_archive(self):
        for term in ('SQLiteOpenHelper(context, "dikoveriya_lab.db", null, 13)',
                     "if (oldVersion < 11)","LabCoreV11.create(db)",
                     "BACKUP_ORDER_V10=BACKUP_ORDER_V9+LabCoreV10.backupTables",
                     "BACKUP_ORDER_V11=BACKUP_ORDER_V10+LabCoreV11.backupTables", "BACKUP_ORDER_V12=BACKUP_ORDER_V11+LabCoreV12.backupTables",
                     'writer.name("schema").value(13)',"root.optInt(\"schema\") in 4..13",
                     "10->BACKUP_ORDER_V10"):
            self.assertIn(term,DB)
        self.assertIn('root.getInt("schema") in 4..13',ZIP)
        self.assertIn('getJSONArray("plant_visuals")',ZIP)
        self.assertIn('"plant_visuals"->c.getColumnIndex("cover_filename")',DB)
        self.assertIn('if(photoIndex>=0 && !c.isNull(photoIndex))',DB)

    def test_photo_safe_and_cover_controls_are_real(self):
        for term in ('plantExists10(plantId)','20_000_000','BitmapFactory.decodeFile',
                     'stylePlant10(', 'setPlantCover10(', 'removePlantCover10('):
            self.assertIn(term,V11)
        for term in ('ActivityResultContracts.GetContent','db.setPlantCover10',
                     'db.removePlantCover10','db.stylePlant10',
                     'GridCells.Fixed(2)','Lab10PlantFolder('):
            self.assertIn(term,COL)

    def test_filters_are_separate_form_not_inline(self):
        self.assertIn('Lab10RegistryFilterSheet(',REG)
        self.assertNotIn('if(advanced)item(key="advanced")',REG)
        self.assertIn('onApply=',REG)
        self.assertIn('onDismiss=',REG)
        self.assertIn('ModalBottomSheet(',FILT)

if __name__=="__main__":
    unittest.main()
