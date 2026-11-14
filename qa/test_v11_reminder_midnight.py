"""Regression guard: date-only task deadlines are eligible for 09:00 reminders.

Android's AlarmManager behavior is exercised in instrumented tests; this check
protects the SQL lower bound from silently reverting to the current instant.
"""
from pathlib import Path
import unittest

SOURCE=(Path(__file__).resolve().parents[1]/
        "app/src/main/java/ru/dikoveriya/lab/Lab11Reminders.kt").read_text("utf-8")

class ReminderDateBounds(unittest.TestCase):
    def test_today_midnight_included(self):
        self.assertIn("val todayStart=Instant.ofEpochMilli(now)",SOURCE)
        self.assertIn(".toLocalDate().atStartOfDay(ZoneId.systemDefault())",SOURCE)
        self.assertIn("arrayOf(todayStart.toString()",SOURCE)
        self.assertIn("if(time<=now)continue",SOURCE)

    def test_notification_only_for_confirmed_tasks(self):
        self.assertIn("WHERE t.done=0",SOURCE)
        self.assertIn("entity_archives",SOURCE)
        self.assertIn("setAndAllowWhileIdle",SOURCE)

if __name__=="__main__":
    unittest.main()
