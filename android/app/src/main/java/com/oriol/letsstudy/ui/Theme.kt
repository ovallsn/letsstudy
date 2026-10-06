package com.oriol.letsstudy.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

private val ForestColor = Color(0xFF285747)
private val DeepForestColor = Color(0xFF193A32)
private val InkColor = Color(0xFF1D302A)
private val PageCanvas = Color(0xFFF7F5EE)
private val PaperSurface = Color(0xFFFFFEFA)
private val SoftSage = Color(0xFFE5EEE4)
private val SageColor = Color(0xFF789B82)
private val ClayColor = Color(0xFFB75538)
private val ClayWashColor = Color(0xFFF9E9DF)
private val MutedText = Color(0xFF66746C)
private val Hairline = Color(0xFFDDE2D8)
private val Sand = Color(0xFFF2EDDF)
private val SunColor = Color(0xFFF5D99A)
private val BaseTypography = Typography()

private val LetsStudyTypography = BaseTypography.copy(
    displayLarge = BaseTypography.displayLarge.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
    displayMedium = BaseTypography.displayMedium.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
    displaySmall = BaseTypography.displaySmall.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
    headlineLarge = BaseTypography.headlineLarge.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
    headlineMedium = BaseTypography.headlineMedium.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
    headlineSmall = BaseTypography.headlineSmall.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
    titleLarge = BaseTypography.titleLarge.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
)

@Composable
fun LetsStudyTheme(content: @Composable () -> Unit) {
    val colors = lightColorScheme(
        primary = ForestColor,
        onPrimary = PaperSurface,
        primaryContainer = SoftSage,
        onPrimaryContainer = DeepForestColor,
        secondary = ClayColor,
        onSecondary = Color.White,
        secondaryContainer = ClayWashColor,
        background = PageCanvas,
        onBackground = InkColor,
        surface = PaperSurface,
        onSurface = InkColor,
        surfaceVariant = Sand,
        onSurfaceVariant = MutedText,
        outline = Hairline,
        error = Color(0xFF9D4E40),
        onError = Color.White,
    )
    MaterialTheme(colorScheme = colors, typography = LetsStudyTypography, content = content)
}

object LetsStudyColors {
    val Primary = ForestColor
    val DeepPrimary = DeepForestColor
    val Mint = SoftSage
    val Sage = SageColor
    val Clay = ClayColor
    val ClayWash = ClayWashColor
    val Muted = MutedText
    val Ink = InkColor
    val Border = Hairline
    val Canvas = PageCanvas
    val Card = PaperSurface
    val SoftBlue = SoftSage
    val Warm = Sand
    val Coral = Clay
    val Sun = SunColor
}
