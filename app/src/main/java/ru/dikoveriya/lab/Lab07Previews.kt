package ru.dikoveriya.lab

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/** Design-time previews to review 360/390/430dp and increased font scale in Android Studio.
 * They are NOT a substitute for instrumentation/accessibility checks on physical hardware.
 */
private val referenceCulture=CultureRecord(
    id=1,code="LAB-000123",plantId=1,
    plantName="Alocasia Black Velvet Aurea variegata",
    quantity=18,status=LabTerms.IN_CULTURE,passage=3,
    stage=LabTerms.IN_VITRO,location="Стеллаж A · ярус 2",
    batchId=null,motherId=null,createdAt=1730000000000L,
    notes="",originState="Не установлено",quantityKind="Побеги",recipeId=null
)

@Preview(name="Обзор · 360 dp · крупный шрифт",widthDp=360,heightDp=820,fontScale=1.5f,showBackground=true)
@Composable
private fun PreviewDashboard360() {
    DikoveriyaTheme {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Lab07Dashboard(
                plants=listOf(PlantRecord(1,"Alocasia Black Velvet Aurea variegata")),
                cultures=listOf(referenceCulture),tasks=emptyList(),
                onCollection={},onContainer={},onNewContainer={},
                onNewPlant={},onMediums={},onScan={}
            )
        }
    }
}

@Preview(name="Обзор · 390 dp · обычный шрифт",widthDp=390,heightDp=850,fontScale=1f,showBackground=true)
@Composable
private fun PreviewDashboard390() {
    DikoveriyaTheme {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Lab07Dashboard(
                plants=listOf(PlantRecord(1,"Alocasia Black Velvet Aurea variegata")),
                cultures=listOf(referenceCulture),tasks=emptyList(),
                onCollection={},onContainer={},onNewContainer={},
                onNewPlant={},onMediums={},onScan={}
            )
        }
    }
}

@Preview(name="Контейнер · 430 dp · крупный шрифт",widthDp=430,heightDp=850,fontScale=1.6f,showBackground=true)
@Composable
private fun PreviewContainerTiles430() {
    DikoveriyaTheme {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Lab07ScreenHeader("КОЛЛЕКЦИЯ / СОРТ / КОНТЕЙНЕР",
                referenceCulture.code,referenceCulture.plantName,onBack={})
            Lab07BrandHero(referenceCulture.plantName,"P3 · В пробирке")
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Lab07ActionTile("Рост","Замеры и динамика",Lab07Palette.Forest,{},
                    Modifier.weight(1f),{})
                Lab07ActionTile("Среда","Рецепт и версии",Lab07Palette.Teal,{},
                    Modifier.weight(1f),{})
            }
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Lab07ActionTile("Операции","Пересадки, потери",Lab07Palette.Moss,{},
                    Modifier.weight(1f),{})
                Lab07ActionTile("История","Хронология и аудит",Lab07Palette.Forest,{},
                    Modifier.weight(1f),{})
            }
        }
    }
}
