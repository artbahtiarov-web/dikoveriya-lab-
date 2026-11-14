"""Structural integration tests; emulator tests cover real SQLite and Compose behavior."""
import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]/"app/src/main/java/ru/dikoveriya/lab"
def source(name):return (ROOT/name).read_text("utf8")
class TasksAndPropagation13(unittest.TestCase):
    def test_new_dates_open_day_then_create(self):
        tasks=source("Lab072TasksUi.kt")
        day=source("Lab13TaskDayUi.kt")
        self.assertIn('selectedDay=date.takeIf{it.isNotBlank()}',tasks)
        self.assertIn('Lab13TaskDayPage(date,db,revision,recipes',tasks)
        self.assertIn('FloatingActionButton(onClick={add=true}',day)
        self.assertIn('initialDate=dateText',day)
        self.assertIn('period="Все"',day)
    def test_task_sources_and_reminders(self):
        self.assertIn('task_details13',source("LabCoreV13.kt"))
        self.assertIn('db.createTask13(',source("Lab072TasksUi.kt"))
        reminders=source("Lab11Reminders.kt")
        self.assertIn('d.reminder_at',reminders)
        self.assertIn('NotificationChannel(channelId',reminders)
        self.assertIn('setSound(Uri.parse(sound)',reminders)
        self.assertIn('RingtoneManager.ACTION_RINGTONE_PICKER',
                      source("Lab13ReminderUi.kt"))
    def test_offshoots_are_real_descendants(self):
        items=source("Lab11Items.kt")
        self.assertIn('internal fun LabDatabase.separateOffshoots13(',items)
        self.assertIn('val groupId=db.insertOrThrow("offshoot_groups13"',items)
        self.assertIn('db.newItemRow11(',items)
        self.assertIn('val tuberIds=mutableListOf<Long>()',items)
        self.assertIn('Lab13OffshootDialog(item,db',source("Lab11ItemPassport.kt"))
    def test_migration_and_backup(self):
        db=source("LabDatabase.kt")
        self.assertIn('null, 13)',db)
        self.assertIn('if (oldVersion < 13)',db)
        self.assertIn('LabCoreV13.create(db)',db)
        self.assertIn('BACKUP_ORDER=BACKUP_ORDER_V12+LabCoreV13.backupTables',db)
        self.assertIn('root.optInt("schema") in 4..13',db)
if __name__=="__main__":unittest.main()
