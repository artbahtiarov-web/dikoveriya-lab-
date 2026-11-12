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
}
