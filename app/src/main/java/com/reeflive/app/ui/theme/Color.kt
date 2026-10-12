package com.reeflive.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/*
 * Design tokens from docs/UI_STYLE.md §1. This is the only place raw hex values may appear;
 * screens read colors through [ReefTheme.colors].
 */

/** "Abyss" (dark, default) palette, UI_STYLE §1.1. */
object Abyss {
    val abyss0 = Color(0xFF030912)
    val abyss1 = Color(0xFF061327)
    val abyss2 = Color(0xFF0A1F3D)
    val reef = Color(0xFF0E3B5A)
    val lagoon = Color(0xFF1FA6C9)
    val lagoonBright = Color(0xFF5CE1E6)
    val foam = Color(0xFFE8F6FA)
    val mist = Color(0xFFA7C4D4)
    val kelp = Color(0xFF6E8FA3)
    val glass = Color.White.copy(alpha = 0.08f)
    val glassBorder = Color.White.copy(alpha = 0.16f)
    val glassHi = Color.White.copy(alpha = 0.14f)
    val coral = Color(0xFFFF7A59)
    val coralSoft = Color(0xFFFFB08F)
    val anemone = Color(0xFFC77DFF)
    val sand = Color(0xFFE6DFD0)
}

/** Status tokens, UI_STYLE §1.2 (base hue + text-on-dark variant). */
object StatusColors {
    val good = Color(0xFF35D0A6)
    val goodText = Color(0xFF7CF2D0)
    val caution = Color(0xFFFFB547)
    val cautionText = Color(0xFFFFD089)
    val danger = Color(0xFFFF5C7A)
    val dangerText = Color(0xFFFF93A7)
    val unknown = Color(0xFF6E8FA3)
    val unknownText = Color(0xFFA7C4D4)

    val goodTextLight = Color(0xFF0E8C6C)
    val cautionTextLight = Color(0xFFB26A00)
    val dangerTextLight = Color(0xFFC8203F)
}

/** "Lagoon" (light) palette, UI_STYLE §1.3. */
object Lagoon {
    val bg0 = Color(0xFFF2FAFD)
    val bg1 = Color(0xFFE3F3F9)
    val bg2 = Color(0xFFCFE9F3)
    val reef = Color(0xFFB9DFEC)
    val lagoon = Color(0xFF0B7FA3)
    val lagoonBright = Color(0xFF0A9CC4)
    val foam = Color(0xFF0A1B2B)
    val mist = Color(0xFF3E5A6E)
    val kelp = Color(0xFF6E8699)
    val glass = Color.White.copy(alpha = 0.55f)
    val glassBorder = Color(0xFF0A1B2B).copy(alpha = 0.10f)
    val coral = Color(0xFFE85D3A)
    val anemone = Color(0xFF8E44D8)
}

/** Fixed ink colors used on gradient buttons (UI_STYLE §6.2, §6.3). */
object Ink {
    val onPrimary = Color(0xFF04111C)
    val onCoral = Color(0xFF1A0C08)
    val shadow = Color(0xFF000000)
    val shadowLight = Color(0xFF0A1B2B)
    val nightSky = Color(0xFF02060C)
}

/** Gradients, UI_STYLE §1.4. */
object ReefGradients {
    /** Page background (dark): top dark → bottom lighter, "looking up from the deep". */
    val bgAbyss = Brush.verticalGradient(
        0.00f to Abyss.abyss1,
        0.55f to Abyss.abyss2,
        1.00f to Abyss.reef,
    )
    val bgLagoon = Brush.verticalGradient(listOf(Lagoon.bg0, Lagoon.bg2))
    val primaryButton = Brush.horizontalGradient(listOf(Abyss.lagoon, Abyss.lagoonBright))
    val primaryButtonLight = Brush.horizontalGradient(listOf(Lagoon.lagoon, Lagoon.lagoonBright))
    val coralButton = Brush.horizontalGradient(listOf(Abyss.coral, Abyss.coralSoft))
    val coralButtonLight = Brush.horizontalGradient(listOf(Lagoon.coral, Abyss.coral))
    val heroTile = Brush.linearGradient(listOf(Abyss.reef.copy(alpha = 0.55f), Abyss.lagoon.copy(alpha = 0.35f)), start = Offset.Zero, end = Offset.Infinite)
    val heroTileLight = Brush.linearGradient(listOf(Lagoon.reef.copy(alpha = 0.7f), Lagoon.lagoonBright.copy(alpha = 0.25f)), start = Offset.Zero, end = Offset.Infinite)
    val lightArc = listOf(Color(0xFF2B3A6E), Color(0xFF6A3DFF), Color(0xFF2E5BFF), Color(0xFFDDEEFF), Color(0xFF3AA0FF), Color(0xFF2B3A6E))
}

/** Semantic color set for one theme. Read via [ReefTheme.colors]. */
@Immutable
data class ReefColors(
    val isDark: Boolean,
    val bgDeep: Color,
    val bg: Color,
    val bgMid: Color,
    val reef: Color,
    val primary: Color,
    val primaryBright: Color,
    val text: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val glass: Color,
    val glassBorder: Color,
    val glassHi: Color,
    val glassHighlight: Color,
    val sheet: Color,
    val field: Color,
    val coral: Color,
    val coralSoft: Color,
    val anemone: Color,
    val sand: Color,
    val good: Color,
    val goodText: Color,
    val caution: Color,
    val cautionText: Color,
    val danger: Color,
    val dangerText: Color,
    val unknown: Color,
    val unknownText: Color,
    /** Fill alpha for status pills/tints (22 % dark, 18 % light). */
    val statusFillAlpha: Float,
    val shadow: Color,
    val bgBrush: Brush,
    val primaryBrush: Brush,
    val coralBrush: Brush,
    val heroBrush: Brush,
    val causticAlpha: Float,
    val shaftColor: Color,
    val bubbleColor: Color,
)

val AbyssColors = ReefColors(
    isDark = true,
    bgDeep = Abyss.abyss0, bg = Abyss.abyss1, bgMid = Abyss.abyss2, reef = Abyss.reef,
    primary = Abyss.lagoon, primaryBright = Abyss.lagoonBright,
    text = Abyss.foam, textSecondary = Abyss.mist, textTertiary = Abyss.kelp,
    glass = Abyss.glass, glassBorder = Abyss.glassBorder, glassHi = Abyss.glassHi,
    glassHighlight = Color.White.copy(alpha = 0.10f),
    sheet = Color(0xFF0B1A33).copy(alpha = 0.94f),
    field = Color.White.copy(alpha = 0.06f),
    coral = Abyss.coral, coralSoft = Abyss.coralSoft, anemone = Abyss.anemone, sand = Abyss.sand,
    good = StatusColors.good, goodText = StatusColors.goodText,
    caution = StatusColors.caution, cautionText = StatusColors.cautionText,
    danger = StatusColors.danger, dangerText = StatusColors.dangerText,
    unknown = StatusColors.unknown, unknownText = StatusColors.unknownText,
    statusFillAlpha = 0.22f,
    shadow = Ink.shadow.copy(alpha = 0.25f),
    bgBrush = ReefGradients.bgAbyss,
    primaryBrush = ReefGradients.primaryButton,
    coralBrush = ReefGradients.coralButton,
    heroBrush = ReefGradients.heroTile,
    causticAlpha = 0.07f,
    shaftColor = Abyss.lagoon.copy(alpha = 0.10f),
    bubbleColor = Abyss.foam,
)

val LagoonColors = ReefColors(
    isDark = false,
    bgDeep = Lagoon.bg0, bg = Lagoon.bg1, bgMid = Lagoon.bg2, reef = Lagoon.reef,
    primary = Lagoon.lagoon, primaryBright = Lagoon.lagoonBright,
    text = Lagoon.foam, textSecondary = Lagoon.mist, textTertiary = Lagoon.kelp,
    glass = Lagoon.glass, glassBorder = Lagoon.glassBorder, glassHi = Color.White.copy(alpha = 0.75f),
    glassHighlight = Color.White.copy(alpha = 0.9f),
    sheet = Lagoon.bg0.copy(alpha = 0.96f),
    field = Color.White.copy(alpha = 0.7f),
    coral = Lagoon.coral, coralSoft = Abyss.coral, anemone = Lagoon.anemone, sand = Color(0xFF8A7A5C),
    good = StatusColors.good, goodText = StatusColors.goodTextLight,
    caution = StatusColors.caution, cautionText = StatusColors.cautionTextLight,
    danger = StatusColors.danger, dangerText = StatusColors.dangerTextLight,
    unknown = StatusColors.unknown, unknownText = Lagoon.mist,
    statusFillAlpha = 0.18f,
    shadow = Ink.shadowLight.copy(alpha = 0.12f),
    bgBrush = ReefGradients.bgLagoon,
    primaryBrush = ReefGradients.primaryButtonLight,
    coralBrush = ReefGradients.coralButtonLight,
    heroBrush = ReefGradients.heroTileLight,
    causticAlpha = 0.35f,
    shaftColor = Color.White.copy(alpha = 0.45f),
    bubbleColor = Color.White,
)

val LocalReefColors = staticCompositionLocalOf { AbyssColors }

/** Channel swatch colors for the light-schedule chart (UI_STYLE §9.3), in LightChannel order. */
fun channelColor(argb: Long): Color = Color(argb.toInt())

/** Parses "#RRGGBB" / "#AARRGGBB" from species data (not for UI tokens). */
fun parseHexColor(hex: String?): Color? {
    val h = hex?.trim()?.removePrefix("#") ?: return null
    val v = h.toLongOrNull(16) ?: return null
    return when (h.length) {
        6 -> Color((0xFF000000L or v).toInt())
        8 -> Color(v.toInt())
        else -> null
    }
}
