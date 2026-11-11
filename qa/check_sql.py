"""SQLite schema and upgrade scenarios. This tests SQL itself, not Android runtime."""
from pathlib import Path
import sqlite3
import re

base=Path(__file__).resolve().parent.parent
src=(base/'app/src/main/java/ru/dikoveriya/lab/LabDatabase.kt').read_text(encoding='utf-8')
creates=re.findall(r'db\.execSQL\("([^"\\]+)"\)',src)
creates=[q for q in creates if q.startswith(('CREATE TABLE','CREATE INDEX'))]
assert len(creates)>22

def make_db():
    c=sqlite3.connect(':memory:')
    c.execute('PRAGMA foreign_keys=ON')
    return c

def create_v5():
    c=make_db()
    for stmt in creates:c.execute(stmt)
    return c

def seed(c):
    c.execute("INSERT INTO plants(name) VALUES ('Alocasia')")
    c.execute("INSERT INTO mother_plants(code,plant_id,origin,created_at) VALUES ('MAT-1',1,'supplier',12345)")
    c.execute("INSERT INTO recipes(family,version,name,purpose,created_at,status) VALUES ('f',1,'MS','Размножение',1,'Утверждена')")
    c.execute("INSERT INTO recipe_parts(recipe_id,name,per_l,unit) VALUES (1,'Agar',7,'г/л')")
    c.execute("INSERT INTO batches(code,recipe_id,volume_ml,prepared_at,actual_json) VALUES ('MED-1',1,500,100,'{}')")
    c.execute("INSERT INTO cultures(code,plant_id,quantity,status,passage,created_at,medium_batch_id,mother_id) VALUES ('LAB-1',1,10,'В культуре',0,123,1,1)")
    c.execute("INSERT INTO cultures(code,plant_id,quantity,status,passage,created_at,medium_batch_id,mother_id) VALUES ('LAB-2',1,8,'В культуре',1,124,1,1)")
    c.execute("INSERT INTO lineage(parent_culture_id,child_culture_id,used_qty,resulting_qty,event_group) VALUES (1,2,5,8,'legacy-op')")
    c.execute("INSERT INTO events(culture_id,kind,note,created_at) VALUES (2,'Пересадка','ok',125)")
    return c

# Fresh v5: FKs, optional source count type, parent links and event chronology
c=seed(create_v5())
assert c.execute("SELECT quantity_kind,origin_state FROM cultures WHERE id=1").fetchone()==('Не уточнено','Не установлено')
assert c.execute('PRAGMA foreign_key_check').fetchall()==[]
c.execute("INSERT INTO lab_operations(id,kind,occurred_at,recorded_at,note) VALUES ('split','Разделение и пересадка',1,2,'ok')")
c.execute("INSERT INTO culture_links(parent_culture_id,child_culture_id,used_qty,result_qty,event_group) VALUES(1,2,5,8,'split')")
c.execute("INSERT INTO operation_changes(operation_id,culture_id,before_qty,after_qty,before_status,after_status) VALUES('split',1,10,5,'В культуре','В культуре')")
assert c.execute('SELECT used_qty,result_qty FROM culture_links').fetchall()==[(5,8)]
try:
    c.execute("INSERT INTO culture_links(parent_culture_id,child_culture_id,used_qty,result_qty,event_group) VALUES(999,2,1,1,'bad')")
    raise AssertionError('foreign keys not enforced')
except sqlite3.IntegrityError:pass
try:
    c.execute("INSERT INTO operation_changes(operation_id,culture_id,after_qty,after_status) VALUES('split',2,-1,'bad')")
    raise AssertionError('negative quantity permitted')
except sqlite3.IntegrityError:pass
assert c.execute('PRAGMA foreign_key_check').fetchall()==[]
print('PASS: v5 new database, parent links, operations, constraints')

# Preserve existing v4 historical records when going to v5 (no deleting).
old=make_db()
old.executescript((base/'qa/schema-v4.sql').read_text(encoding='utf-8'))
seed(old)
old.execute("INSERT INTO assessments(culture_id,batch_id,starting_qty,healthy_qty,observation_days,observed_at) VALUES(2,1,5,8,30,130)")
old.execute("ALTER TABLE cultures ADD COLUMN origin_state TEXT NOT NULL DEFAULT 'Не установлено'")
old.execute("ALTER TABLE cultures ADD COLUMN quantity_kind TEXT NOT NULL DEFAULT 'Не уточнено'")
old.execute("ALTER TABLE events ADD COLUMN occurred_at INTEGER")
old.execute("ALTER TABLE incidents ADD COLUMN operation_id TEXT")
old.execute("UPDATE cultures SET origin_state=CASE WHEN mother_id IS NULL THEN 'Не установлено' ELSE 'По маточнику' END")
for stmt in creates: old.execute(stmt) if 'IF NOT EXISTS' in stmt else None
old.execute("INSERT OR IGNORE INTO culture_links(parent_culture_id,child_culture_id,used_qty,result_qty,event_group) SELECT parent_culture_id,child_culture_id,used_qty,resulting_qty,event_group FROM lineage")
assert old.execute('SELECT quantity,quantity_kind,origin_state FROM cultures WHERE id=1').fetchone()==(10,'Не уточнено','По маточнику')
assert old.execute('SELECT parent_culture_id,child_culture_id FROM culture_links').fetchall()==[(1,2)]
assert old.execute('SELECT COUNT(*) FROM assessments').fetchone()==(1,)
assert old.execute('PRAGMA foreign_key_check').fetchall()==[]
print('PASS: migration v4 -> v5 preserves cultures, lineage and scientific observations')

# v1 prototype migrations: add legacy columns, initialize extra tables, then add v5 fields.
proto=make_db()
proto.executescript('''
CREATE TABLE plants (id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL UNIQUE);
CREATE TABLE cultures (id INTEGER PRIMARY KEY AUTOINCREMENT, code TEXT NOT NULL UNIQUE, plant_id INTEGER NOT NULL REFERENCES plants(id),quantity INTEGER NOT NULL CHECK(quantity>=0),status TEXT NOT NULL,passage INTEGER NOT NULL DEFAULT 0,created_at INTEGER NOT NULL);
CREATE TABLE events (id INTEGER PRIMARY KEY AUTOINCREMENT,culture_id INTEGER NOT NULL REFERENCES cultures(id),kind TEXT NOT NULL,note TEXT NOT NULL,created_at INTEGER NOT NULL);
INSERT INTO plants(name) VALUES('Old');
INSERT INTO cultures(code,plant_id,quantity,status,passage,created_at) VALUES('LAB-OLDA',1,4,'В культуре',0,1);
INSERT INTO events(culture_id,kind,note,created_at) VALUES (1,'Создание','old note',1);
''')
for sql in [
 "ALTER TABLE plants ADD COLUMN description TEXT NOT NULL DEFAULT ''",
 "ALTER TABLE cultures ADD COLUMN stage TEXT NOT NULL DEFAULT 'В пробирке'",
 "ALTER TABLE cultures ADD COLUMN location TEXT NOT NULL DEFAULT ''",
 "ALTER TABLE cultures ADD COLUMN medium_batch_id INTEGER REFERENCES batches(id)",
 "ALTER TABLE cultures ADD COLUMN mother_id INTEGER",
 "ALTER TABLE cultures ADD COLUMN notes TEXT NOT NULL DEFAULT ''",
 "ALTER TABLE cultures ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0",
 "ALTER TABLE events ADD COLUMN before_qty INTEGER",
 "ALTER TABLE events ADD COLUMN after_qty INTEGER",
 "ALTER TABLE events ADD COLUMN medium_batch_id INTEGER",
 "ALTER TABLE events ADD COLUMN event_group TEXT",
]: proto.execute(sql)
for stmt in creates:
    if 'IF NOT EXISTS' in stmt:proto.execute(stmt)
proto.execute("ALTER TABLE cultures ADD COLUMN origin_state TEXT NOT NULL DEFAULT 'Не установлено'")
proto.execute("ALTER TABLE cultures ADD COLUMN quantity_kind TEXT NOT NULL DEFAULT 'Не уточнено'")
proto.execute("ALTER TABLE events ADD COLUMN occurred_at INTEGER")
# In migrations from version 1, createExtra created a v5 incidents table with operation_id.
assert 'operation_id' in [col[1] for col in proto.execute('PRAGMA table_info(incidents)')]
assert proto.execute('SELECT quantity,stage,quantity_kind FROM cultures').fetchone()==(4,'В пробирке','Не уточнено')
assert proto.execute('SELECT note FROM events').fetchone()==('old note',)
assert proto.execute('PRAGMA foreign_key_check').fetchall()==[]
print('PASS: migration v1 -> v5 preserves prototype data')

# The same SQL queries used for ancestry after a split/merge.
operation_db=seed(create_v5())
operation_db.execute("INSERT INTO cultures(code,plant_id,quantity,status,passage,created_at) VALUES ('LAB-3',1,9,'В культуре',1,125)")
operation_db.execute("INSERT INTO lab_operations(id,kind,occurred_at,recorded_at,note) VALUES ('group-1','Объединение культур',130,131,'merge')")
operation_db.execute("INSERT INTO culture_links(parent_culture_id,child_culture_id,used_qty,result_qty,event_group) VALUES (1,3,4,9,'group-1')")
operation_db.execute("INSERT INTO culture_links(parent_culture_id,child_culture_id,used_qty,result_qty,event_group) VALUES (2,3,5,9,'group-1')")
ancestry="""WITH RECURSIVE chain(depth,parent_id,used_qty,result_qty) AS
    (SELECT 1,parent_culture_id,used_qty,result_qty FROM culture_links WHERE child_culture_id=? AND cancelled=0
    UNION ALL SELECT chain.depth+1,l.parent_culture_id,l.used_qty,l.result_qty FROM culture_links l JOIN chain ON l.child_culture_id=chain.parent_id WHERE chain.depth<100 AND l.cancelled=0)
    SELECT p.code, chain.used_qty || ' → ' || chain.result_qty FROM chain JOIN cultures p ON p.id=chain.parent_id ORDER BY chain.depth,p.code"""
parents=operation_db.execute(ancestry,(3,)).fetchall()
assert parents==[('LAB-1','4 → 9'),('LAB-2','5 → 9')],parents
operation_db.execute("UPDATE culture_links SET cancelled=1 WHERE event_group='group-1'")
assert operation_db.execute(ancestry,(3,)).fetchall()==[]
assert operation_db.execute('PRAGMA foreign_key_check').fetchall()==[]
print('PASS: merged genealogy retains two parents and cancellation hides revoked links')
