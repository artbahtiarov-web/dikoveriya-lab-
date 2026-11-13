package ru.dikoveriya.lab

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private data class Lab10WelcomePage(
    val eyebrow:String,val title:String,val subtitle:String,
    val items:List<Pair<String,ImageVector>>
)
private val welcomePages10=listOf(
    Lab10WelcomePage(
        "01 / КОЛЛЕКЦИЯ","У каждой культуры — своё место",
        "Визуальная коллекция, собственные фотографии сортов и паспорта контейнеров.",
        listOf("Обложки растений" to Icons.Outlined.PhotoLibrary,
            "QR-паспорта" to Icons.Outlined.QrCodeScanner)
    ),
    Lab10WelcomePage(
        "02 / ПРОЦЕСС","От пробирки до готового растения",
        "Записывай пересадки, потери, наблюдения, рецептуры и адаптацию — с историей каждого действия.",
        listOf("Рост и наблюдения" to Icons.Outlined.TrendingUp,
            "Среды и операции" to Icons.Outlined.Science)
    ),
    Lab10WelcomePage(
        "03 / КОНТРОЛЬ","Ничего лишнего. Всё под рукой.",
        "Задачи, склад, аналитика и резервные копии доступны в понятной структуре.",
        listOf("Календарь и склад" to Icons.Outlined.EventNote,
            "Резервные копии" to Icons.Outlined.Save)
    )
)

/** Optional and one-time only. No fake metrics or mandatory account creation. */
@Composable
internal fun Lab10OnboardingScreen(onFinish:()->Unit){
    var page by remember{mutableIntStateOf(0)}
    val current=welcomePages10[page]
    Column(Modifier.fillMaxSize().background(
        Brush.verticalGradient(listOf(Lab07Palette.DeepForest,
            Lab07Palette.Forest,Lab07Palette.DeepForest)))
        .padding(horizontal=23.dp,vertical=22.dp),
        horizontalAlignment=Alignment.CenterHorizontally,
        verticalArrangement=Arrangement.SpaceBetween){
        Row(Modifier.fillMaxWidth(),
            verticalAlignment=Alignment.CenterVertically){
            Text("ДИКОВЕРИЯ LAB",color=Lab07Palette.Brass,
                style=MaterialTheme.typography.titleMedium,
                modifier=Modifier.weight(1f))
            TextButton(onClick=onFinish){
                Text("Пропустить",color=Lab07Palette.Champagne)
            }
        }
        Column(horizontalAlignment=Alignment.CenterHorizontally,
            verticalArrangement=Arrangement.spacedBy(18.dp)){
            Surface(shape=RoundedCornerShape(30.dp),
                color=Lab07Palette.Forest,
                border=BorderStroke(1.dp,Lab07Palette.Brass.copy(alpha=.7f))){
                Icon(painterResource(R.drawable.ic_dikoveriya_logo),
                    contentDescription="Диковерия — арка открытий",
                    tint=Color.Unspecified,
                    modifier=Modifier.size(130.dp).padding(15.dp))
            }
            Text(current.eyebrow,color=Lab07Palette.Brass,
                style=MaterialTheme.typography.labelLarge)
            Text(current.title,color=Lab07Palette.Champagne,
                style=MaterialTheme.typography.headlineMedium,
                textAlign=TextAlign.Center)
            Text(current.subtitle,color=Lab07Palette.Champagne.copy(alpha=.9f),
                style=MaterialTheme.typography.bodyLarge,
                textAlign=TextAlign.Center)
            current.items.forEach{(label,icon)->
                Surface(color=Lab07Palette.Champagne,
                    shape=RoundedCornerShape(17.dp)){
                    Row(Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment=Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.spacedBy(12.dp)){
                        Icon(icon,null,tint=Lab07Palette.Forest)
                        Text(label,style=MaterialTheme.typography.bodyMedium,
                            color=Lab07Palette.Ink)
                    }
                }
            }
        }
        Column(verticalArrangement=Arrangement.spacedBy(15.dp)){
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center){
                welcomePages10.indices.forEach{index->
                    Box(Modifier.padding(horizontal=4.dp)
                        .size(if(page==index)12.dp else 7.dp)
                        .background(if(page==index)Lab07Palette.Brass
                            else Lab07Palette.Champagne.copy(alpha=.4f),CircleShape))
                }
            }
            Button(onClick={
                if(page==welcomePages10.lastIndex)onFinish() else page++
            },modifier=Modifier.fillMaxWidth().heightIn(min=54.dp),
                colors=ButtonDefaults.buttonColors(containerColor=Lab07Palette.Brass,
                    contentColor=Lab07Palette.DeepForest)){
                Text(if(page==welcomePages10.lastIndex)"Начать работу" else "Далее")
                Spacer(Modifier.width(10.dp))
                Icon(Icons.Outlined.ArrowForward,null)
            }
        }
    }
}
