package ru.dikoveriya.lab

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Dikovériya / "Arch of Discovery" 2026.
 * Inspired by the approved brand-board: deep forest, champagne paper, brass and moss.
 * Dark gold is used on light surfaces for readable contrast; luminous brass stays on dark green.
 * Android system serif/sans are used instead of bundling unlicensed brand font files.
 */
object Lab07Palette {
    val DeepForest = Color(0xFF102820)
    val Forest = Color(0xFF1B3A2E)
    val Fern = Color(0xFF315943)
    val Moss = Color(0xFF526947)
    val Brass = Color(0xFFE0B777)
    val InkBrass = Color(0xFF7B592C)
    val Champagne = Color(0xFFFAE9DB)
    val Paper = Color(0xFFF9F4EB)
    val Sand = Color(0xFFEBD9BF)
    val Ink = Color(0xFF183127)
    val Muted = Color(0xFF4D6054)
    val Stroke = Color(0xFFCCC4B7)
    val Teal = Color(0xFF245D60)
    val Danger = Color(0xFFA33434)
}

private val BotanicalLight = lightColorScheme(
    primary=Lab07Palette.Forest,
    onPrimary=Color.White,
    primaryContainer=Color(0xFFDAE6DA),
    onPrimaryContainer=Lab07Palette.DeepForest,
    secondary=Lab07Palette.Moss,
    onSecondary=Color.White,
    secondaryContainer=Color(0xFFE2E8D9),
    onSecondaryContainer=Lab07Palette.Forest,
    tertiary=Lab07Palette.InkBrass,
    onTertiary=Color.White,
    tertiaryContainer=Lab07Palette.Champagne,
    onTertiaryContainer=Lab07Palette.Forest,
    background=Lab07Palette.Paper,
    onBackground=Lab07Palette.Ink,
    surface=Color(0xFFFFFCF7),
    onSurface=Lab07Palette.Ink,
    surfaceVariant=Color(0xFFEDE5D8),
    onSurfaceVariant=Lab07Palette.Muted,
    outline=Color(0xFF8B958A),
    outlineVariant=Lab07Palette.Stroke,
    error=Lab07Palette.Danger,
    onError=Color.White,
    errorContainer=Color(0xFFF9DFDA),
    onErrorContainer=Color(0xFF672323)
)

@Composable
fun DikoveriyaTheme(content: @Composable ()->Unit) {
    MaterialTheme(
        colorScheme=BotanicalLight,
        typography=Typography(
            headlineMedium=TextStyle(fontFamily=FontFamily.Serif,fontSize=29.sp,
                fontWeight=FontWeight.Normal,lineHeight=36.sp),
            headlineSmall=TextStyle(fontFamily=FontFamily.Serif,fontSize=25.sp,
                fontWeight=FontWeight.Normal,lineHeight=32.sp),
            titleLarge=TextStyle(fontFamily=FontFamily.Serif,fontSize=23.sp,
                fontWeight=FontWeight.Medium,lineHeight=29.sp),
            titleMedium=TextStyle(fontSize=17.sp,fontWeight=FontWeight.SemiBold,lineHeight=24.sp),
            bodyLarge=TextStyle(fontSize=16.sp,lineHeight=24.sp),
            bodyMedium=TextStyle(fontSize=14.sp,lineHeight=22.sp),
            bodySmall=TextStyle(fontSize=12.sp,lineHeight=18.sp),
            labelLarge=TextStyle(fontSize=13.sp,fontWeight=FontWeight.SemiBold,letterSpacing=0.3.sp)
        ),
        shapes=Shapes(
            small=RoundedCornerShape(10.dp),
            medium=RoundedCornerShape(16.dp),
            large=RoundedCornerShape(24.dp)
        ),
        content=content
    )
}
