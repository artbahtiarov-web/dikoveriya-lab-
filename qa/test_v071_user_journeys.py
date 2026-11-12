"""Release 0.7.1 UX regression contract: no hidden passport action mode,
single catalogue/one-click operations, physical label, never drop lab data.
Structural checks complement actual Android connected instrumentation tests.
"""
import pathlib
import unittest
ROOT=pathlib.Path(__file__).resolve().parents[1]
SRC=ROOT/"app/src/main/java/ru/dikoveriya/lab"
def read(f):return (SRC/f).read_text(encoding="utf-8")
MAIN=read("MainActivity.kt")
SCREENS=read("Lab07Screens.kt")
OPS=read("LabOperationDialogs.kt")
MEDIUM=read("Lab071MediumCatalog.kt")
DIALOG=read("Lab071Dialogs.kt")
LABEL=read("LabLabel40.kt")
OLD=read("LabForms.kt")
DB=read("LabDatabase.kt")
NAV=read("Lab07Navigation.kt")
MANIFEST=(ROOT/"app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
PATHS=(ROOT/"app/src/main/res/xml/sharing_paths.xml").read_text(encoding="utf-8")
GRADLE=(ROOT/"app/build.gradle.kts").read_text(encoding="utf-8")

class UnifiedJourneys071(unittest.TestCase):
    def test_version_keeps_db_and_certificate(self):
        self.assertIn('versionName = "0.7.2-rc1"',GRADLE)
        self.assertIn('versionCode = 70200 +',GRADLE)
        self.assertIn('SQLiteOpenHelper(context, "dikoveriya_lab.db", null, 9)',DB)
        self.assertIn('applicationId = "ru.dikoveriya.lab"',GRADLE)
        self.assertIn('file("signing/dikoveriya.p12")',GRADLE)

    def test_four_root_tabs_not_five_parallel_workflows(self):
        self.assertIn('listOf("Главная","Коллекция","Среды","Ещё")',MAIN)
        self.assertNotIn('listOf("Главная","Коллекция","Работа","Аналитика","Ещё")',MAIN)
        self.assertIn('"Аналитика","Маточники","Задачи"',MAIN)
        self.assertIn('Lab071MediumCatalog(',MAIN)
        self.assertIn('var view by rememberSaveable',SCREENS)
        self.assertIn('listOf("Сорта","Контейнеры")',SCREENS)

    def test_passport_and_operations_have_no_double_dropdown(self):
        self.assertNotIn('PickOne("Действие"',OLD)
        self.assertNotIn('fun CultureDialog(',OLD)
        self.assertIn('Lab071PassportDialog(',MAIN)
        self.assertIn('onAction(culture.id,"Пересадка")',SCREENS)
        self.assertIn('onAction(culture.id,"Потери")',SCREENS)
        self.assertIn('if(initialMode.isBlank())',OPS)
        self.assertIn('Text("Операция: $mode"',OPS)

    def test_each_capability_stays_reachable(self):
        for action in ('Lab071PassportDialog','Lab071SaleDialog','Lab071PhotosDialog',
                       'Lab071LabelDialog','Lab071PrepareBatchDialog',
                       'Lab071MediumCatalog','LabActionDialog','QuickCultureDialog',
                       'QuickMediumDialog','RecipeDialog','IncidentReviewDialog',
                       'MaterialDialog','ExperimentDialog','BatchDialog'):
            self.assertIn(action+"(",MAIN)
        for section in ('Задачи','Склад','Оборудование','Инциденты',
                        'Эксперименты','Продажи','Резервные копии'):
            self.assertIn('"'+section+'"',MAIN)
        self.assertIn('saveBackup',MAIN)
        self.assertIn('chooseBackup',MAIN)

    def test_recipies_and_batches_preserve_real_amounts(self):
        self.assertIn('db.prepareBatch(',MEDIUM)
        self.assertIn('val actual=requireNotNull(calc)',MEDIUM)
        self.assertIn('Укажи фактическое количество:',MEDIUM)
        self.assertNotIn('part.title to part.amount',MEDIUM)

    def test_label_is_real_40_by_12mm_and_share_is_cache_scoped(self):
        self.assertIn('const val WIDTH_MM = 40',LABEL)
        self.assertIn('const val HEIGHT_MM = 12',LABEL)
        self.assertIn('val supportedDpi = listOf(203,300)',LABEL)
        self.assertIn('EncodeHintType.ERROR_CORRECTION',LABEL)
        self.assertIn('fun png(spec:LabLabelSpec):ByteArray',LABEL)
        self.assertIn('pHYs',LABEL)
        self.assertIn('FileProvider.getUriForFile',LABEL)
        self.assertIn('putExtra(Intent.EXTRA_STREAM,uri)',LABEL)
        self.assertIn('android:exported="false"',MANIFEST)
        self.assertIn('path="labels/"',PATHS)
        self.assertNotIn('path="."',PATHS)
        self.assertNotIn('LabQr.png(code)',MAIN)
        self.assertIn('Lab071LabelDialog',MAIN)

    def test_explicit_tap_targets_and_back(self):
        self.assertIn('heightIn(min=52.dp)',read("UiBasics.kt"))
        self.assertIn('Icons.Outlined.ExpandMore',read("UiBasics.kt"))
        self.assertIn('routeStack.isNotEmpty() -> backRoute()',MAIN)
        self.assertEqual(MAIN.count('BackHandler('),1)
        self.assertIn('onBack={goBack()}',MAIN)

if __name__=="__main__":
    unittest.main()
