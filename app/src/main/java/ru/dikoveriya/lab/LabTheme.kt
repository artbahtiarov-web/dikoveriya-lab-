package ru.dikoveriya.lab

import androidx.compose.material3.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Approved museum-botanical look: warm paper, dark botanical ink, quiet brass accents. */
private val DikoveriyaColors = lightColorScheme(
    primary=Color(0xFF31584C), onPrimary=Color(0xFFFFFFFF), primaryContainer=Color(0xFFE3EAE2),
    onPrimaryContainer=Color(0xFF25453B), secondary=Color(0xFF6D756C), onSecondary=Color(0xFFFFFFFF),
    secondaryContainer=Color(0xFFE8E9E1), onSecondaryContainer=Color(0xFF464F45),
    tertiary=Color(0xFF987B51), background=Color(0xFFF7F6F2), onBackground=Color(0xFF26352E),
    surface=Color(0xFFFFFFFF), onSurface=Color(0xFF26352E), surfaceVariant=Color(0xFFEEEFE9),
    onSurfaceVariant=Color(0xFF667068), outline=Color(0xFFBFC7BD), outlineVariant=Color(0xFFDEE3DA),
    error=Color(0xFFAA4B45), errorContainer=Color(0xFFF8E5E2)
)

@Composable
fun DikoveriyaTheme(content: @Composable ()->Unit) {
    MaterialTheme(
        colorScheme=DikoveriyaColors,
        typography=Typography(
            headlineMedium=TextStyle(fontSize=25.sp,fontWeight=FontWeight.SemiBold,letterSpacing=(-0.4f).sp,lineHeight=31.sp),
            titleLarge=TextStyle(fontSize=19.sp,fontWeight=FontWeight.SemiBold,lineHeight=25.sp),
            titleMedium=TextStyle(fontSize=16.sp,fontWeight=FontWeight.Medium,lineHeight=23.sp),
            bodyMedium=TextStyle(fontSize=14.sp,lineHeight=21.sp),
            bodySmall=TextStyle(fontSize=12.sp,lineHeight=17.sp),
            labelLarge=TextStyle(fontSize=12.sp,fontWeight=FontWeight.SemiBold,letterSpacing=0.6f.sp)
        ),
        shapes=Shapes(small=RoundedCornerShape(12.dp),medium=RoundedCornerShape(16.dp),large=RoundedCornerShape(21.dp)),
        content=content
    )
}
