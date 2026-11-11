#!/usr/bin/env python3
"""Static structural checks for v0.5 (not an Android device test)."""
from pathlib import Path
import sqlite3
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
src = root / 'app/src/main/java/ru/dikoveriya/lab'
db = (src/'LabDatabase.kt').read_text(encoding='utf-8')
main = (src/'MainActivity.kt').read_text(encoding='utf-8')
backup = (src/'LabBackup.kt').read_text(encoding='utf-8')
forms = (src/'LabForms.kt').read_text(encoding='utf-8')
assert 'SQLiteOpenHelper(context, "dikoveriya_lab.db", null, 6)' in db
assert 'val BACKUP_ORDER=BACKUP_ORDER_V5+listOf("ingredient_catalog")' in db
assert 'root.optInt("schema") in 4..6' in db
assert 'root.getInt("schema") in 4..6' in backup
assert 'BackHandler(enabled=' in main
assert 'tabHistory.removeAt(tabHistory.lastIndex)' in main
assert 'AddIngredientDialog(' in main
assert 'RecipeDialog(r,db.recipeParts(id),ingredients,' in main
assert 'catalog:List<IngredientEntry>' in forms
assert 'versionName = "0.5.0-dev"' in (root/'app/build.gradle.kts').read_text()
for filename in ('app/src/main/AndroidManifest.xml','app/src/main/res/drawable/ic_dikoveriya_logo.xml',
                 'app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml', 'app/src/main/res/values/launcher_colors.xml'):
    ET.parse(root/filename)
conn = sqlite3.connect(':memory:')
conn.execute("CREATE TABLE ingredient_catalog(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL UNIQUE,category TEXT NOT NULL,unit TEXT NOT NULL,notes TEXT NOT NULL DEFAULT '',archived INTEGER NOT NULL DEFAULT 0)")
conn.execute("INSERT INTO ingredient_catalog(name,category,unit,notes) VALUES (?,?,?,?)", ('Агар','Загуститель','г/л',''))
conn.execute("INSERT OR IGNORE INTO ingredient_catalog(name,category,unit,notes) VALUES (?,?,?,?)", ('Агар','Загуститель','г/л',''))
assert conn.execute('SELECT COUNT(*) FROM ingredient_catalog').fetchone()[0] == 1
conn.execute('UPDATE ingredient_catalog SET archived=1 WHERE name=?', ('Агар',))
assert conn.execute('SELECT COUNT(*) FROM ingredient_catalog WHERE archived=0').fetchone()[0] == 0
print('PASS: v0.5 schema, backup compatibility declarations, navigation, form wiring, icon XML')
