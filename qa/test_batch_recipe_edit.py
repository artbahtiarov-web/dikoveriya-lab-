"""Regression guard for the legacy container edit path: batch and direct recipe must agree."""
from pathlib import Path
import re
import unittest

SOURCE = Path(__file__).resolve().parents[1] / "app/src/main/java/ru/dikoveriya/lab/LabDatabase.kt"

class BatchRecipeEditTests(unittest.TestCase):
    def test_edit_updates_recipe_in_same_transaction(self):
        source = SOURCE.read_text(encoding="utf-8")
        method = source.split("fun updateCulture(", 1)[1].split("fun addObservation(", 1)[0]
        self.assertIn("db.tx {", method)
        self.assertIn("val changedBatch = batchId != c.batchId", method)
        self.assertIn("verifyBatch(batchId)", method)
        self.assertIn("batches().firstOrNull { it.id == batchId }?.recipeId", method)
        self.assertIn('"medium_recipe_id" to effectiveRecipeId', method)
        self.assertIn("val effectiveRecipeId = if (recipeChosen) recipeId else (selectedRecipeId ?: c.recipeId)",method)
        self.assertIn('insertOrThrow("culture_recipe_history"', method)
        self.assertIn("selectedRecipeId != c.recipeId", method)

    def test_no_batch_keeps_existing_direct_recipe(self):
        source = SOURCE.read_text(encoding="utf-8")
        method = source.split("fun updateCulture(", 1)[1].split("fun addObservation(", 1)[0]
        self.assertIn("if (changedBatch && batchId != null)", method)
        self.assertIn("else null", method)
        self.assertIn("(selectedRecipeId ?: c.recipeId)", method)

if __name__ == "__main__":
    unittest.main()
