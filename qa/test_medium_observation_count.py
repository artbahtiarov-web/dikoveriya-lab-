"""Regression test for the medium observation count in LabFlowStore.kt.

Runs the actual SELECT extracted from Kotlin against SQLite in memory.
This catches accidental inclusion of intake measurements or note-only entries.
"""
import pathlib
import re
import sqlite3
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
SOURCE = (ROOT / "app/src/main/java/ru/dikoveriya/lab/LabFlowStore.kt").read_text(encoding="utf-8")
QUERY = re.search(r"fun LabDatabase\.mediumResults\(\).*?return db\.rawQuery\(\s*\"\"\"(.*?)\"\"\"\.trimIndent\(\)", SOURCE, re.S)
if not QUERY:
    raise RuntimeError("Cannot locate mediumResults SQL")


class MediumObservationCountTest(unittest.TestCase):
    def test_intake_and_notes_not_counted_but_numeric_zero_is(self):
        db = sqlite3.connect(":memory:")
        db.executescript("""
          CREATE TABLE recipes(id INTEGER PRIMARY KEY,name TEXT,version INTEGER);
          CREATE TABLE culture_measurements(
            id INTEGER PRIMARY KEY,culture_id INTEGER,recipe_id INTEGER,
            observed_at INTEGER,explants INTEGER,shoots INTEGER,plants INTEGER,
            contaminated INTEGER,notes TEXT NOT NULL);
          INSERT INTO recipes VALUES (1,'MS',1);
          INSERT INTO culture_measurements VALUES
            (1,10,1,100,4,2,1,NULL,'Показатели при создании'),
            (2,10,1,200,NULL,NULL,NULL,NULL,'Внешний вид без изменений'),
            (3,10,1,300,NULL,0,NULL,NULL,'Побеги не обнаружены'),
            (4,10,1,400,NULL,NULL,3,NULL,'Рост растений'),
            (5,11,1,110,5,NULL,NULL,NULL,'Показатели при создании');
        """)
        rows = db.execute(QUERY.group(1)).fetchall()
        self.assertEqual(len(rows), 1)
        medium_id, _, containers, observations, shoots, plants = rows[0]
        self.assertEqual((medium_id, containers, observations, shoots, plants), (1, 2, 2, 0, 3))

    def test_note_only_and_intake_alone_are_not_followups(self):
        db = sqlite3.connect(":memory:")
        db.executescript("""
          CREATE TABLE recipes(id INTEGER PRIMARY KEY,name TEXT,version INTEGER);
          CREATE TABLE culture_measurements(
            id INTEGER PRIMARY KEY,culture_id INTEGER,recipe_id INTEGER,
            observed_at INTEGER,explants INTEGER,shoots INTEGER,plants INTEGER,
            contaminated INTEGER,notes TEXT NOT NULL);
          INSERT INTO recipes VALUES (1,'MS',1);
          INSERT INTO culture_measurements VALUES
            (1,10,1,100,4,2,1,NULL,'Показатели при создании'),
            (2,10,1,200,NULL,NULL,NULL,NULL,'Только заметка');
        """)
        row = db.execute(QUERY.group(1)).fetchone()
        self.assertEqual(row[3], 0)
        self.assertEqual(row[2], 1)


if __name__ == "__main__":
    unittest.main()
