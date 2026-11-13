#!/usr/bin/env python3
"""v0.6 post-patch structural and SQLite migration smoke tests, no network needed.
Compatible filename for the existing signed GitHub Actions build workflow.
"""
from pathlib import Path
import re
import sqlite3

ROOT=Path(__file__).resolve().parents[1]
SRC=ROOT/'app/src/main/java/ru/dikoveriya/lab'
def read(path):
    p=ROOT/path
    assert p.is_file(), f'Missing file: {path}'
    return p.read_text(encoding='utf-8')

db=read('app/src/main/java/ru/dikoveriya/lab/LabDatabase.kt')
backup=read('app/src/main/java/ru/dikoveriya/lab/LabBackup.kt')
main=read('app/src/main/java/ru/dikoveriya/lab/MainActivity.kt')
ui=read('app/src/main/java/ru/dikoveriya/lab/LabWorkflowUi.kt')
store=read('app/src/main/java/ru/dikoveriya/lab/LabFlowStore.kt')
theme=read('app/src/main/java/ru/dikoveriya/lab/LabTheme.kt')
gradle=read('app/build.gradle.kts')
models=read('app/src/main/java/ru/dikoveriya/lab/LabModels.kt')

checks={
 'db v11': 'SQLiteOpenHelper(context, "dikoveriya_lab.db", null, 11)' in db,
 'v11 backup': 'put("schema",11)' in db and 'root.getInt("schema") in 4..11' in backup,
 'migration': 'ALTER TABLE cultures ADD COLUMN medium_recipe_id' in db and 'ALTER TABLE recipe_parts ADD COLUMN sugar_g_l' in db,
 'snapshot/history': 'CREATE TABLE IF NOT EXISTS culture_recipe_history' in db,
 'measurements': 'CREATE TABLE IF NOT EXISTS culture_measurements' in db,
 'direct medium': 'medium_recipe_id' in db and 'createCultureInDb(this,plantId,quantity' in db,
 'one step recipe': 'saveReadyMedium(' in store and 'status" to "Утверждена"' in store,
 'recipe duplicate prevention': 'Такая среда уже существует' in store,
 'sugar / agar mass': 'sugar_g_l' in store and 'agar_g_l' in store and 'Сколько сахара внутри' in ui,
 'new workflow': 'QuickCultureDialog(db,preselectedRecipeId' in main and 'QuickMediumDialog(db' in main,
 'growth': 'Lab07GrowthScreen(' in read('app/src/main/java/ru/dikoveriya/lab/Lab07Screens.kt') and 'addGrowthSample(' in store,
 'plant results': 'plantResults()' in main and 'mediumResults()' in main,
 'approved design': 'DikoveriyaTheme' in main and 'Lab07Palette' in theme,
 'single version': 'versionCode = 90000 +' in gradle,
 'copy of old linked cultures': 'Перенесено из существующего учёта партий' in db,
 'history in model': 'val recipeId: Long? = null' in models,
}
for name,ok in checks.items():
    if not ok: raise AssertionError('Failed validation: '+name)

# New DB: execute every constant CREATE TABLE from onCreate and createExtra.
conn=sqlite3.connect(':memory:')
conn.execute('PRAGMA foreign_keys=ON')
statements=re.findall(r'db\.execSQL\("(CREATE TABLE[^"\\]+)"\)',db)
assert len(statements)>=17, ('Not enough DDL statements', len(statements))
for sql in statements:
    conn.execute(sql)
for table in ('plants','mother_plants','cultures','recipes','recipe_parts','culture_measurements','culture_recipe_history','batches','sales'):
    conn.execute(f'SELECT * FROM {table} LIMIT 0')
assert {'medium_recipe_id'} <= {row[1] for row in conn.execute('PRAGMA table_info(cultures)')}
assert {'sugar_g_l','agar_g_l'} <= {row[1] for row in conn.execute('PRAGMA table_info(recipe_parts)')}

# Check that direct recipe linking cannot reference non-existent recipes.
conn.execute("INSERT INTO plants(id,name) VALUES (1,'Plant A')")
conn.execute("INSERT INTO recipes(id,family,version,name,purpose,created_at,status) VALUES (1,'source',1,'MS 01','Размножение',1,'Утверждена')")
conn.execute("INSERT INTO cultures(id,code,plant_id,quantity,status,created_at,medium_recipe_id) VALUES(1,'C-1',1,3,'В культуре',1,1)")
conn.execute("INSERT INTO culture_measurements(culture_id,recipe_id,observed_at,explants,shoots,plants,notes) VALUES(1,1,1,3,0,NULL,'Ввод')")
conn.execute("INSERT INTO culture_measurements(culture_id,recipe_id,observed_at,explants,shoots,plants,notes) VALUES(1,1,2,3,7,2,'Динамика')")
conn.execute("INSERT INTO culture_recipe_history(culture_id,recipe_id,applied_at,note) VALUES(1,1,1,'При создании')")
assert conn.execute('SELECT COUNT(*) FROM culture_measurements').fetchone()[0] == 2
assert conn.execute('PRAGMA foreign_key_check').fetchall() == []
try:
    conn.execute("INSERT INTO culture_measurements(culture_id,observed_at,shoots,notes) VALUES(1,3,-1,'bad')")
    raise AssertionError('Invalid negative counts accepted')
except sqlite3.IntegrityError:
    pass
try:
    conn.execute("UPDATE cultures SET medium_recipe_id=9999 WHERE id=1")
    raise AssertionError('Invalid recipe ref accepted')
except sqlite3.IntegrityError:
    pass

# Old v6: simulate change of medium fields and migration of batch-linked containers.
old=sqlite3.connect(':memory:')
old.execute('PRAGMA foreign_keys=ON')
old.executescript('''
CREATE TABLE recipes(id INTEGER PRIMARY KEY,name TEXT NOT NULL);
CREATE TABLE recipe_parts(id INTEGER PRIMARY KEY,recipe_id INTEGER,name TEXT,per_l REAL,unit TEXT);
CREATE TABLE batches(id INTEGER PRIMARY KEY,recipe_id INTEGER NOT NULL REFERENCES recipes(id));
CREATE TABLE cultures(id INTEGER PRIMARY KEY,medium_batch_id INTEGER REFERENCES batches(id),created_at INTEGER);
INSERT INTO recipes VALUES(1,'MS 02');
INSERT INTO batches VALUES(1,1);
INSERT INTO cultures VALUES(1,1,1000);
''')
old.execute('ALTER TABLE cultures ADD COLUMN medium_recipe_id INTEGER REFERENCES recipes(id)')
old.execute('ALTER TABLE recipe_parts ADD COLUMN sugar_g_l REAL')
old.execute('ALTER TABLE recipe_parts ADD COLUMN agar_g_l REAL')
old.execute('CREATE TABLE culture_recipe_history(id INTEGER PRIMARY KEY,culture_id INTEGER,recipe_id INTEGER,applied_at INTEGER,note TEXT)')
old.execute('UPDATE cultures SET medium_recipe_id=(SELECT recipe_id FROM batches WHERE batches.id=cultures.medium_batch_id) WHERE medium_batch_id IS NOT NULL AND medium_recipe_id IS NULL')
old.execute("INSERT INTO culture_recipe_history(culture_id,recipe_id,applied_at,note) SELECT id,medium_recipe_id,created_at,'Перенесено' FROM cultures WHERE medium_recipe_id IS NOT NULL")
assert old.execute('SELECT medium_recipe_id FROM cultures').fetchone()[0]==1
assert old.execute('SELECT COUNT(*) FROM culture_recipe_history').fetchone()[0]==1
assert old.execute('PRAGMA foreign_key_check').fetchall()==[]

# A later measurement that omits shoots must not erase the last measured shoot count.
conn.execute("INSERT INTO culture_measurements(culture_id,recipe_id,observed_at,explants,notes) VALUES(1,1,3,4,'Только экспланты')")
conn.execute("INSERT INTO sales(culture_id,quantity,price,sold_at) VALUES(1,1,2500,4)")
plant_query=re.search(r'fun LabDatabase\.plantResults\(\).*?return db\.rawQuery\("""(.*?)"""\.trimIndent\(\),null\)',store,re.S)
assert plant_query, 'Missing plant revenue query'
revenue=conn.execute(plant_query.group(1)).fetchall()
assert len(revenue)==1 and revenue[0][3]==2500.0, revenue
latest_query=re.search(r'val current=db\.rawQuery\("""(.*?)"""\.trimIndent\(\),arrayOf\(plantId\.toString\(\)\)\)',store,re.S)
assert latest_query, 'Missing carry-forward metrics query'
latest=conn.execute(latest_query.group(1),('1',)).fetchone()
assert latest==(4,7,2), ('Incorrect carry-forward of independent observations',latest)
medium_query=re.search(r'fun LabDatabase\.mediumResults\(\).*?return db\.rawQuery\("""(.*?)"""\.trimIndent\(\),null\)',store,re.S)
assert medium_query, 'Missing recipe summary query'
summary=conn.execute(medium_query.group(1)).fetchone()
assert summary is not None and summary[2:]==(1,3,7,2), summary

print('PASS: v0.6 structural checks, SQLite DDL, FK checks, migration v6->v7, observational history.')
