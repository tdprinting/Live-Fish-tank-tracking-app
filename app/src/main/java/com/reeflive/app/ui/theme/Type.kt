package com.reeflive.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

private fun style(family: androidx.compose.ui.text.font.FontFamily, size: Int, line: Int, weight: FontWeight, tracking: Double) =
    TextStyle(fontFamily = family, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight, letterSpacing = (tracking / size).em)

/** Material 3 scale mapped to Outfit/Inter (UI_STYLE §3). */
val ReefTypography: Typography = Typography(
    displayLarge = style(ReefFonts.outfit, 44, 52, FontWeight.SemiBold, -0.5),
    displayMedium = style(ReefFonts.outfit, 36, 42, FontWeight.SemiBold, -0.5),
    displaySmall = style(ReefFonts.outfit, 30, 36, FontWeight.SemiBold, -0.25),
    headlineLarge = style(ReefFonts.outfit, 30, 36, FontWeight.SemiBold, -0.25),
    headlineMedium = style(ReefFonts.outfit, 26, 32, FontWeight.SemiBold, -0.25),
    headlineSmall = style(ReefFonts.outfit, 20, 26, FontWeight.Medium, 0.0),
    titleLarge = style(ReefFonts.outfit, 22, 28, FontWeight.Medium, 0.0),
    titleMedium = style(ReefFonts.inter, 16, 22, FontWeight.SemiBold, 0.0),
    titleSmall = style(ReefFonts.inter, 14, 20, FontWeight.SemiBold, 0.0),
    bodyLarge = style(ReefFonts.inter, 16, 24, FontWeight.Normal, 0.0),
    bodyMedium = style(ReefFonts.inter, 14, 20, FontWeight.Normal, 0.0),
    bodySmall = style(ReefFonts.inter, 12, 16, FontWeight.Normal, 0.0),
    labelLarge = style(ReefFonts.inter, 14, 20, FontWeight.SemiBold, 0.1),
    labelMedium = style(ReefFonts.inter, 12, 16, FontWeight.Medium, 0.3),
    labelSmall = style(ReefFonts.inter, 11, 14, FontWeight.Medium, 0.4),
)

/** Extra numeric styles with tabular figures. */
@Immutable
data class ReefNumberStyles(
    val tileValue: TextStyle,
    val hero: TextStyle,
    val field: TextStyle,
    val body: TextStyle,
    val label: TextStyle,
)

val ReefNumbers = ReefNumberStyles(
    tileValue = style(ReefFonts.outfit, 28, 32, FontWeight.SemiBold, -0.25).copy(fontFeatureSettings = "tnum"),
    hero = style(ReefFonts.outfit, 36, 42, FontWeight.SemiBold, -0.5).copy(fontFeatureSettings = "tnum"),
    field = style(ReefFonts.inter, 22, 28, FontWeight.Medium, 0.0).copy(fontFeatureSettings = "tnum"),
    body = style(ReefFonts.inter, 14, 20, FontWeight.Medium, 0.0).copy(fontFeatureSettings = "tnum"),
    label = style(ReefFonts.inter, 12, 16, FontWeight.Medium, 0.3).copy(fontFeatureSettings = "tnum"),
)

val LocalReefNumbers = staticCompositionLocalOf { ReefNumbers }
