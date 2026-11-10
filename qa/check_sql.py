from pathlib import Path
import sqlite3, re

source=Path(__file__).resolve().parent.parent/'app/src/main/java/ru/dikoveriya/lab/LabDatabase.kt'
s=source.read_text(encoding='utf-8')
statements=re.findall(r'db\.execSQL\("([^"]+)"\)',s)
creates=[x for x in statements if x.startswith('CREATE')]
assert len(creates)>=15,len(creates)

def all_create(db):
    # FK references to later tables are permissible in SQLite
    for q in creates: db.execute(q)

c=sqlite3.connect(':memory:')
c.execute('PRAGMA foreign_keys=ON')
all_create(c)
c.execute("INSERT INTO plants(name) VALUES ('Alocasia')")
c.execute("INSERT INTO mother_plants(code,plant_id,origin,created_at) VALUES ('MAT-1',1,'nursery',12345)")
c.execute("INSERT INTO recipes(family,version,name,purpose,created_at,status) VALUES ('x',1,'MS','Размножение',1,'Утверждена')")
c.execute("INSERT INTO recipe_parts(recipe_id,name,per_l,unit) VALUES (1,'Agar',7,'г/л')")
c.execute("INSERT INTO batches(code,recipe_id,volume_ml,prepared_at,actual_json) VALUES ('MED-1',1,500,10,'{}')")
c.execute("INSERT INTO cultures(code,plant_id,quantity,status,passage,created_at,medium_batch_id,mother_id) VALUES ('LAB-1',1,10,'В культуре',0,1,1,1)")
c.execute("INSERT INTO cultures(code,plant_id,quantity,status,passage,created_at,medium_batch_id,mother_id) VALUES ('LAB-2',1,12,'В культуре',1,2,1,1)")
c.execute("INSERT INTO lineage(parent_culture_id,child_culture_id,used_qty,resulting_qty,event_group) VALUES (1,2,5,12,'op1')")
c.execute("INSERT INTO events(culture_id,kind,note,created_at) VALUES (2,'Пересадка','OK',1)")
c.execute("INSERT INTO assessments(culture_id,batch_id,starting_qty,healthy_qty,observation_days,observed_at) VALUES (2,1,5,12,30,123)")
assert c.execute('PRAGMA foreign_key_check').fetchall()==[]
assert c.execute('SELECT count(*) FROM lineage').fetchone()==(1,)
try:
    c.execute("INSERT INTO cultures(code,plant_id,quantity,status,passage,created_at) VALUES ('bad',999,1,'В культуре',0,1)")
    raise AssertionError('FK not enforced')
except sqlite3.IntegrityError: pass
print('PASS: create schema, relations, constraints, FK checks')

# Upgrade from published prototype v0.2 schema 1 to new schema 2
old=sqlite3.connect(':memory:')
old.executescript('''
CREATE TABLE plants (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE);
CREATE TABLE cultures (id INTEGER PRIMARY KEY AUTOINCREMENT, code TEXT NOT NULL UNIQUE, plant_id INTEGER NOT NULL REFERENCES plants(id), quantity INTEGER NOT NULL CHECK(quantity >= 0), status TEXT NOT NULL, passage INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL);
CREATE TABLE events (id INTEGER PRIMARY KEY AUTOINCREMENT, culture_id INTEGER NOT NULL REFERENCES cultures(id), kind TEXT NOT NULL, note TEXT NOT NULL, created_at INTEGER NOT NULL);
INSERT INTO plants(name) VALUES ('old');
INSERT INTO cultures(code,plant_id,quantity,status,passage,created_at) VALUES ('LAB-000001',1,3,'В культуре',0,123);
INSERT INTO events(culture_id,kind,note,created_at) VALUES (1,'Создание','old event',123);
''')
for q in statements:
    if q.startswith('ALTER TABLE'): old.execute(q)
for q in creates:
    if 'IF NOT EXISTS' in q: old.execute(q)
# mother_plants CREATE IF NOT EXISTS is not in createExtra and is executed in onUpgrade separately
assert old.execute('SELECT quantity,stage FROM cultures WHERE code="LAB-000001"').fetchone()==(3,'В пробирке')
assert old.execute('SELECT note FROM events WHERE culture_id=1').fetchone()==('old event',)
assert old.execute("SELECT name FROM sqlite_master WHERE name='culture_photos'").fetchone() is not None
assert old.execute("SELECT name FROM sqlite_master WHERE name='recipe_imports'").fetchone() is not None
print('PASS: preserves prototype data on migration 1 -> 4')
