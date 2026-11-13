"""Static structural checks for v0.7.0 UI contracts. Actual layout/font checks require Android tests."""
import pathlib
import re
import unittest

ROOT=pathlib.Path(__file__).resolve().parents[1]
SRC=ROOT/"app/src/main/java/ru/dikoveriya/lab"
MAIN=(SRC/"MainActivity.kt").read_text(encoding="utf-8")
NAV=(SRC/"Lab07Navigation.kt").read_text(encoding="utf-8")
SCREEN=(SRC/"Lab07Screens.kt").read_text(encoding="utf-8")
WIDGETS=(SRC/"Lab07Widgets.kt").read_text(encoding="utf-8")
THEME=(SRC/"LabTheme.kt").read_text(encoding="utf-8")
OPS=(SRC/"LabOperationDialogs.kt").read_text(encoding="utf-8")
DB=(SRC/"LabDatabase.kt").read_text(encoding="utf-8")
BUILD=(ROOT/"app/build.gradle.kts").read_text(encoding="utf-8")

class BotanicalNavigation07(unittest.TestCase):
    def test_version_is_07_and_db_is_still_08(self):
        self.assertIn('versionName = "0.8.0"',BUILD)
        self.assertIn("versionCode = 80000 +",BUILD)
        self.assertIn('SQLiteOpenHelper(context, "dikoveriya_lab.db", null, 10)',DB)
        self.assertIn('namespace = "ru.dikoveriya.lab"',BUILD)

    def test_enter_and_back_have_no_clear_all(self):
        self.assertIn("fun back(stack: List<String>)",NAV)
        self.assertIn("stack.dropLast(1)",NAV)
        self.assertIn("Lab07Navigation.enter(routeStack,next)",MAIN)
        self.assertIn("routeStack.isNotEmpty() -> backRoute()",MAIN)
        self.assertIn("onBack={goBack()}",MAIN)
        self.assertEqual(MAIN.count("BackHandler("),1)

    def test_collection_variety_container_four_tiles(self):
        self.assertIn('route.substringBefore(\':\')',SCREEN)
        for route in ("collection","variety","container","growth","medium","operations","history"):
            self.assertIn('"'+route+'"',SCREEN)
        for tile in ("Рост","Среда","Операции","История"):
            self.assertIn('Lab07ActionTile("'+tile+'"',SCREEN)
        self.assertIn("Lab07Navigation.variety(plant.id)",SCREEN)
        self.assertIn("Lab07Navigation.container(row.id)",(SRC/"Lab072RegistryUi.kt").read_text(encoding="utf-8"))

    def test_filters_scroll_position_and_drafts_persist(self):
        self.assertIn("rememberSaveableStateHolder()",MAIN)
        self.assertIn("stateHolder.SaveableStateProvider(route)",MAIN)
        self.assertIn('SaveableStateProvider("legacy:$tab:$subsection")',MAIN)
        self.assertIn("rememberLazyListState()",SCREEN)
        self.assertIn("rememberSaveable",SCREEN)
        registry=(SRC/"Lab072RegistryUi.kt").read_text(encoding="utf-8")
        for label in ("7 дней","30 дней","По побегам","По растениям"):
            self.assertIn('"'+label+'"',registry)
        self.assertIn("LabTerms.statuses",registry)
        for label in ("30 дней","90 дней"):
            self.assertIn('"'+label+'"',SCREEN)
        self.assertIn("rememberSaveable(culture.id,mode)",OPS)

    def test_style_from_reference_not_green_mono(self):
        for token in ("DeepForest","Brass","Champagne","Paper","Moss","InkBrass"):
            self.assertIn("val "+token+" =",THEME)
        self.assertIn("FontFamily.Serif",THEME)
        self.assertIn("Lab07BrandHero",WIDGETS)
        self.assertIn("Lab07QuickAction",WIDGETS)
        self.assertIn('min=48.dp',SCREEN.replace('minWidth=48.dp','min=48.dp'))

    def test_layout_has_responsive_actions_and_real_data_graph(self):
        self.assertIn("BoxWithConstraints",WIDGETS)
        self.assertIn("maxWidth<380.dp",WIDGETS)
        self.assertIn("Lab07ObservationPlot(samples,plotted)",SCREEN)
        self.assertIn("value?.let{sample.date to it}",WIDGETS)
        self.assertIn("Для графика нужны минимум два измерения",WIDGETS)
        self.assertNotIn("эффективность 95%",SCREEN.lower())

if __name__=="__main__":
    unittest.main()
