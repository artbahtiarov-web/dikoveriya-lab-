package ru.dikoveriya.lab

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Run twice using 360dp/150% and 430dp/160% emulator configurations.
 * Tests visible semantics, working tap targets and a scrollable dashboard.
 */
@RunWith(AndroidJUnit4::class)
class Lab07LayoutInstrumentedTest {
    @get:Rule val rule=createComposeRule()

    @Test fun dashboardShowsRealLabelsAndQuickActions() {
        val example=CultureRecord(
            id=1,code="LAB-000001",plantId=1,plantName="Alocasia Black Velvet Aurea",
            quantity=10,status=LabTerms.IN_CULTURE,passage=0,stage=LabTerms.IN_VITRO,
            location="",batchId=null,motherId=null,createdAt=1000L,
            notes="",quantityKind="Побеги"
        )
        rule.setContent {
            DikoveriyaTheme {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                    Lab07Dashboard(
                        listOf(PlantRecord(1,"Alocasia Black Velvet Aurea")),
                        listOf(example),emptyList(),
                        onCollection={},onContainer={},onNewContainer={},
                        onNewPlant={},onMediums={},onScan={}
                    )
                }
            }
        }
        rule.onNodeWithText("Моя лаборатория").assertExists()
        rule.onNodeWithText("In vitro").assertExists()
        rule.onNodeWithText("Нужен контроль").assertExists()
        rule.onNodeWithText("Новый контейнер").assertExists().assertHasClickAction()
        rule.onNodeWithText("Сканировать",substring=true).assertExists()
    }

    @Test fun allFourTilesAreClickableAndStayInsideScreen() {
        var chosen by mutableStateOf("")
        rule.setContent {
            DikoveriyaTheme {
                Column(Modifier.fillMaxSize().padding(12.dp)) {
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Lab07ActionTile("Рост","Динамика",Lab07Palette.Forest,
                            { Icon(Icons.Outlined.TrendingUp,null) },Modifier.weight(1f),
                            {chosen="Рост"})
                        Lab07ActionTile("Среда","Версии",Lab07Palette.Teal,
                            { Icon(Icons.Outlined.TrendingUp,null) },Modifier.weight(1f),
                            {chosen="Среда"})
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Lab07ActionTile("Операции","Учёт",Lab07Palette.Moss,
                            { Icon(Icons.Outlined.TrendingUp,null) },Modifier.weight(1f),
                            {chosen="Операции"})
                        Lab07ActionTile("История","Аудит",Lab07Palette.Forest,
                            { Icon(Icons.Outlined.TrendingUp,null) },Modifier.weight(1f),
                            {chosen="История"})
                    }
                }
            }
        }
        for(title in listOf("Рост","Среда","Операции","История")) {
            val tile=rule.onNodeWithText(title).assertExists().assertHasClickAction()
            val screen=rule.onRoot().fetchSemanticsNode().boundsInRoot
            val area=tile.fetchSemanticsNode().boundsInRoot
            assertTrue("$title left clipped",area.left>=screen.left-2f)
            assertTrue("$title right clipped",area.right<=screen.right+2f)
            tile.performClick()
            rule.runOnIdle { assertEquals(title,chosen) }
        }
    }

    @Test fun labelDialogHasReadable40x12PreviewAndTwoWorkingActions() {
        val c=CultureRecord(id=13,code="LAB-000013",plantId=1,
            plantName="Alocasia Black Velvet Aurea variegata",
            quantity=10,status=LabTerms.IN_CULTURE,passage=2,stage=LabTerms.IN_VITRO,
            location="Rack 1",batchId=null,motherId=null,createdAt=1000L,
            notes="",quantityKind="Побеги")
        var export:LabLabelSpec?=null
        var shared:LabLabelSpec?=null
        rule.setContent {
            DikoveriyaTheme {
                Lab071LabelDialog(c,"MS v2",onClose={},
                    onExport={export=it},onShare={shared=it})
            }
        }
        rule.onNodeWithText("Этикетка · 40 × 12 мм").assertExists()
        rule.onNodeWithText("Сохранить PNG 40×12").assertExists()
            .assertHasClickAction().performClick()
        rule.runOnIdle {
            assertEquals("LAB-000013",export?.code)
            assertEquals(203,export?.dpi)
        }
        rule.onNodeWithText("Передать в NIIMBOT / другое приложение")
            .assertExists().assertHasClickAction().performClick()
        rule.runOnIdle {assertEquals("LAB-000013",shared?.code)}
    }

    @Test fun passportEditingHasNoUndiscoverableActionSelector() {
        val c=CultureRecord(id=14,code="LAB-000014",plantId=1,
            plantName="Monstera Burle Marx Flame",
            quantity=12,status=LabTerms.IN_CULTURE,passage=1,stage=LabTerms.ADAPTATION,
            location="Shelf 2",batchId=null,motherId=null,createdAt=1000L,
            notes="",quantityKind="Растения")
        var chosen:String?=null
        rule.setContent {
            DikoveriyaTheme {
                Lab071PassportDialog(c,"MS · v2",onClose={},
                    onSave={status,_,_->chosen=status})
            }
        }
        rule.onNodeWithText("Паспорт контейнера").assertExists()
        rule.onNodeWithText("На контроле").assertExists().performClick()
        rule.onNodeWithText("Сохранить изменения").assertExists().assertIsEnabled().performClick()
        rule.runOnIdle {assertEquals(LabTerms.CONTROL,chosen)}
        rule.onNodeWithText("Действие").assertDoesNotExist()
    }
}
