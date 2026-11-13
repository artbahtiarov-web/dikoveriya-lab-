package ru.dikoveriya.lab

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Runs on Android emulators with 360 and 430 dp and enlarged fonts. */
@RunWith(AndroidJUnit4::class)
class Lab080EditorInstrumentedTest {
    @get:Rule val rule=createComposeRule()

    @Test fun realEditorHasNudgeUndoTemplatesAndPersistsContainerLayout(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val db=LabDatabase(context)
        try{
            val plant=db.plants().first()
            val id=db.addCultureQuick(plant.id,null,null,5,null,null,"Shelf 1","UI test")
            val culture=requireNotNull(db.cultureById(id))
            rule.setContent{
                DikoveriyaTheme{
                    Lab080LabelEditor(culture,db,0,"MS v2",
                        onBack={},onChanged={},onError={error(it)})
                }
            }
            rule.onNodeWithText("Редактор этикетки").assertExists()
            rule.onNodeWithText("Отменить").assertExists().assertIsNotEnabled()
            rule.onNodeWithText("Элементы этикетки").assertExists()
            rule.onNodeWithContentDescription(
                "Предпросмотр наклейки 40 на 12 миллиметров")
                .performTouchInput{
                    val start=Offset(size.width*.5f,size.height*.2f)
                    val end=Offset(size.width*.35f,size.height*.2f)
                    swipe(start=start,end=end,durationMillis=500)
                }
            rule.onNodeWithContentDescription("Вправо").performScrollTo().performClick()
            rule.onNodeWithText("Отменить").assertIsEnabled().performClick()
            rule.onNodeWithText("Вернуть").assertIsEnabled().performClick()
            rule.onNodeWithText("Сохранить макет контейнера").performScrollTo().performClick()
            rule.runOnIdle{
                val stored=requireNotNull(db.labelForCulture080(id))
                assertEquals(203,stored.dpi)
                assertEquals(1,stored.layers.count{it.kind=="qr"&&it.visible})
                assertEquals(1,stored.layers.count{it.kind=="code"&&it.visible})
                assertTrue("Finger drag must change plant X in actual millimetres",
                    stored.layers.single{it.kind=="plant"}.x<11.7f)
            }
            rule.onNodeWithText("Сохранить PNG 40×12").assertExists()
            rule.onNodeWithText("Передать на печать").assertExists()
        }finally{db.close()}
    }
}
