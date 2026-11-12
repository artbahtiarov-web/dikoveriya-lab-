"""Regression guard: split/merge must persist the actual recipe on daughter cultures.

The Android Gradle PR job compiles the Kotlin implementation; this test protects
against accidentally dropping its transaction-local lineage calls.
"""
import pathlib
import re
import unittest

SOURCE = (pathlib.Path(__file__).resolve().parents[1] /
          "app/src/main/java/ru/dikoveriya/lab/LabDatabase.kt").read_text(encoding="utf-8")


class DaughterRecipeLineageTests(unittest.TestCase):
    def test_split_records_recipe_after_child_creation(self):
        block = SOURCE.split("fun splitCulture(", 1)[1].split("fun mergeCultures(", 1)[0]
        self.assertRegex(block, r"val child=createCultureInDb\([^\n]+")
        self.assertIn("assignDaughterRecipe(this,child,source.recipeId,part.batchId,occurredAt,part.recipeId)", block)

    def test_merge_does_not_guess_between_different_recipes(self):
        block = SOURCE.split("fun mergeCultures(", 1)[1].split("fun ", 1)[0]
        self.assertIn("sources.map{it.recipeId}.distinct().singleOrNull()", block)
        self.assertIn("assignDaughterRecipe(this,child,sharedRecipeId,batchId,occurredAt,recipeId)", block)

    def test_batch_overrides_inheritance_and_history_is_atomic(self):
        block = SOURCE.split("private fun assignDaughterRecipe(", 1)[1].split("fun splitCulture(", 1)[0]
        self.assertIn("val batchRecipeId = selectedBatchId?.let", block)
        self.assertIn("?.recipeId", block)
        self.assertIn("?: inheritedRecipeId", block)
        self.assertIn('db.update("cultures",cv("medium_recipe_id" to recipeId)', block)
        self.assertIn('db.insertOrThrow("culture_recipe_history"', block)
        self.assertIn('"applied_at" to occurredAt', block)


if __name__ == "__main__":
    unittest.main()
