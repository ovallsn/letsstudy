package com.oriol.letsstudy.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.ExperimentalTextApi
import com.oriol.letsstudy.R

private val Violet = Color(0xFF5D56DF)
private val DeepViolet = Color(0xFF4943C9)
private val InkColor = Color(0xFF20263A)
private val PageCanvas = Color(0xFFF7F8FB)
private val PaperSurface = Color(0xFFFFFFFF)
private val SoftLavender = Color(0xFFF0EFFF)
private val LavenderColor = Color(0xFF8B83E8)
private val CoralColor = Color(0xFFE38A4B)
private val CoralWashColor = Color(0xFFFFF1E7)
private val MutedText = Color(0xFF626B80)
private val Hairline = Color(0xFFE9EBF1)
private val Sand = Color(0xFFF0F1F7)
private val SunColor = Color(0xFFFFEBD2)
@OptIn(ExperimentalTextApi::class)
private fun studyFont(resource: Int) = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold).map { weight ->
        Font(resource, weight = weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))
    },
)
private val BodyFont = studyFont(R.font.dm_sans)
private val HeadingFont = studyFont(R.font.manrope)
private val BaseTypography = Typography().let { base -> base.copy(
    bodyLarge = base.bodyLarge.copy(fontFamily = BodyFont), bodyMedium = base.bodyMedium.copy(fontFamily = BodyFont), bodySmall = base.bodySmall.copy(fontFamily = BodyFont),
    labelLarge = base.labelLarge.copy(fontFamily = BodyFont), labelMedium = base.labelMedium.copy(fontFamily = BodyFont), labelSmall = base.labelSmall.copy(fontFamily = BodyFont),
    titleMedium = base.titleMedium.copy(fontFamily = HeadingFont), titleSmall = base.titleSmall.copy(fontFamily = HeadingFont),
) }

private val LetsStudyTypography = BaseTypography.copy(
    displayLarge = BaseTypography.displayLarge.copy(fontFamily = HeadingFont, fontWeight = FontWeight.Bold),
    displayMedium = BaseTypography.displayMedium.copy(fontFamily = HeadingFont, fontWeight = FontWeight.Bold),
    displaySmall = BaseTypography.displaySmall.copy(fontFamily = HeadingFont, fontWeight = FontWeight.Bold),
    headlineLarge = BaseTypography.headlineLarge.copy(fontFamily = HeadingFont, fontWeight = FontWeight.Bold),
    headlineMedium = BaseTypography.headlineMedium.copy(fontFamily = HeadingFont, fontWeight = FontWeight.Bold),
    headlineSmall = BaseTypography.headlineSmall.copy(fontFamily = HeadingFont, fontWeight = FontWeight.Bold),
    titleLarge = BaseTypography.titleLarge.copy(fontFamily = HeadingFont, fontWeight = FontWeight.SemiBold),
)

@Composable
fun LetsStudyTheme(content: @Composable () -> Unit) {
    val colors = lightColorScheme(
        primary = Violet,
        onPrimary = PaperSurface,
        primaryContainer = SoftLavender,
        onPrimaryContainer = DeepViolet,
        secondary = CoralColor,
        onSecondary = Color.White,
        secondaryContainer = CoralWashColor,
        background = PageCanvas,
        onBackground = InkColor,
        surface = PaperSurface,
        onSurface = InkColor,
        surfaceVariant = Sand,
        onSurfaceVariant = MutedText,
        outline = Hairline,
        error = Color(0xFFB84A5A),
        onError = Color.White,
    )
    MaterialTheme(colorScheme = colors, typography = LetsStudyTypography, content = content)
}

object LetsStudyColors {
    val Primary = Violet
    val DeepPrimary = DeepViolet
    val Mint = SoftLavender
    val Sage = LavenderColor
    val Clay = CoralColor
    val ClayWash = CoralWashColor
    val Muted = MutedText
    val Ink = InkColor
    val Border = Hairline
    val Canvas = PageCanvas
    val Card = PaperSurface
    val SoftBlue = SoftLavender
    val Warm = Sand
    val Coral = CoralColor
    val Sun = SunColor
}
