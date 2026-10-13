package dev.eartrainer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Brass = Color(0xFFC9843D)
val BrassDeep = Color(0xFF8A5A24)
val Ink = Color(0xFF14120F)
val InkRaised = Color(0xFF241F1A)
val Paper = Color(0xFFF3EEE6)
val PaperCard = Color(0xFFFFFBF6)
val Good = Color(0xFF2F6F5E)
val Miss = Color(0xFFA24B3A)
val Mist = Color(0xFF8A8176)

private val Light = lightColorScheme(
    primary = BrassDeep,
    onPrimary = Paper,
    secondary = Good,
    onSecondary = Paper,
    background = Paper,
    onBackground = Ink,
    surface = PaperCard,
    onSurface = Ink,
    surfaceVariant = Color(0xFFE7E0D4),
    onSurfaceVariant = Color(0xFF51483E),
    error = Miss,
    onError = Paper,
)

private val Dark = darkColorScheme(
    primary = Brass,
    onPrimary = Ink,
    secondary = Color(0xFF8FBF9F),
    onSecondary = Ink,
    background = Ink,
    onBackground = Paper,
    surface = InkRaised,
    onSurface = Paper,
    surfaceVariant = Color(0xFF322C26),
    onSurfaceVariant = Color(0xFFD2C6B6),
    error = Color(0xFFE08B73),
    onError = Ink,
)

private val Type = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 40.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 34.sp,
    ),
    titleLarge = TextStyle(fontFamily = FontFamily.Serif, fontSize = 22.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = 0.2.sp),
    labelMedium = TextStyle(fontSize = 12.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun EarTrainerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = Type,
        content = content,
    )
}
