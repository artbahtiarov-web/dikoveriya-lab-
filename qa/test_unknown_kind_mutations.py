"""Regression guard: no quantity-changing action may silently use an unknown unit."""
import pathlib
import re
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
DB = (ROOT / "app/src/main/java/ru/dikoveriya/lab/LabDatabase.kt").read_text(encoding="utf-8")
UI = (ROOT / "app/src/main/java/ru/dikoveriya/lab/LabOperationDialogs.kt").read_text(encoding="utf-8")


def function_body(name):
    start = re.search(r"    fun " + re.escape(name) + r"\(", DB)
    assert start, name
    end = re.search(r"\n    (?:fun |private fun |/\*\*)", DB[start.end():])
    assert end, name
    return DB[start.start():start.end() + end.start()]


class UnknownQuantityKindTests(unittest.TestCase):
    def test_mutations_guard_unknown_unit_before_operation(self):
        for name in ("recordLoss", "correctQuantity", "splitCulture", "mergeCultures"):
            with self.subTest(name=name):
                body = function_body(name)
                self.assertIn('Не уточнено', body)
                self.assertLess(body.index('Не уточнено'), body.index('newOperation('))

    def test_ui_disables_all_four_mutations(self):
        self.assertIn('mode !in listOf("Пересадка","Объединить","Потери","Исправить ошибку")', UI)
        self.assertIn('culture.quantityKind!="Не уточнено"', UI)
        self.assertIn('операция заблокирована', UI)


if __name__ == "__main__":
    unittest.main()
