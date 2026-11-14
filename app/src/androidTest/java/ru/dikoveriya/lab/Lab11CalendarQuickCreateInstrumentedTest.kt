package ru.dikoveriya.lab

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

/** Calendar first opens a dedicated date page; its + starts creation. */
@RunWith(AndroidJUnit4::class)
class Lab11CalendarQuickCreateInstrumentedTest {
    @get:Rule val rule=createComposeRule()

    @Test fun tappingDayOpensNewTaskWithPrefilledDateAndCanBeRepeated(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val db=LabDatabase(context)
        val today=LocalDate.now()
            .format(DateTimeFormatter.ofPattern("dd.MM.uuuu"))
        var failure by mutableStateOf("")
        var revision by mutableIntStateOf(0)
        try{
            rule.setContent{
                DikoveriyaTheme {
                    Lab072TasksScreen(
                        db=db,revision=revision,plants=db.plants(),recipes=db.recipes(),
                        onBack={},onNavigate={},onChanged={revision++},
                        onError={failure=it})
                }
            }
            fun openToday(){
                rule.onNodeWithContentDescription(today+", незавершённых",
                    substring=true).performScrollTo().performClick()
                rule.onNodeWithText("Задачи на этот день",substring=true).assertExists()
                rule.onNodeWithContentDescription("Создать задачу на "+today)
                    .performClick()
                rule.onNodeWithText("Новая задача").assertExists()
                rule.onNodeWithText("Дата задачи: "+today).assertExists()
            }
            openToday()
            rule.onNodeWithText("Отмена").performClick()
            rule.onNodeWithContentDescription("К календарю").performClick()
            // A second tap also opens the day; only + starts creation.
            openToday()
            rule.onNodeWithText("Отмена").performClick()
            rule.runOnIdle{assertEquals("",failure)}
        }finally{
            rule.runOnIdle { }
            db.close()
        }
    }
}
