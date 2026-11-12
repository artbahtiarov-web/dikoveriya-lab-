"""Regression guards: never debit a legacy quantity of unknown biological units.

These source-level tests complement Gradle's Kotlin compile/unit tests. Android
instrumentation tests are still required for full transactional behavior.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
DB = (ROOT / "app/src/main/java/ru/dikoveriya/lab/LabDatabase.kt").read_text(encoding="utf-8")
UI = (ROOT / "app/src/main/java/ru/dikoveriya/lab/LabOperationDialogs.kt").read_text(encoding="utf-8")


class TypedOperationSourceTests(unittest.TestCase):
    def test_split_rejects_ambiguous_legacy_quantity_before_mutation(self):
        block = DB.split("fun splitCulture(", 1)[1].split("fun mergeCultures(", 1)[0]
        guard = 'require(source.quantityKind != "Не уточнено")'
        self.assertIn(guard, block)
        self.assertLess(block.index(guard), block.index("val group=newOperation("))

    def test_merge_requires_known_identical_units_before_mutation(self):
        block = DB.split("fun mergeCultures(", 1)[1].split("fun recordLoss(", 1)[0]
        self.assertIn('sources.map{it.quantityKind}.distinct().size==1', block)
        guard = 'require(sources.all{it.quantityKind != "Не уточнено"})'
        self.assertIn(guard, block)
        self.assertLess(block.index(guard), block.index("val group=newOperation("))

    def test_merge_picker_filters_by_unit_and_both_actions_disable_unknown(self):
        self.assertIn('it.quantityKind==culture.quantityKind', UI)
        self.assertIn('culture.quantityKind!="Не уточнено"', UI)
        self.assertIn('mode!="Пересадка" && mode!="Объединить"', UI)

    def test_no_schema_or_existing_measurements_are_rewritten(self):
        split = DB.split("fun splitCulture(", 1)[1].split("fun mergeCultures(", 1)[0]
        self.assertNotIn("UPDATE culture_measurements", split)
        self.assertNotIn("DELETE FROM culture_measurements", split)


if __name__ == "__main__":
    unittest.main()
