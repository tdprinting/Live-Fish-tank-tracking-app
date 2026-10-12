package com.reeflive.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** "Abyss" (dark, default) color tokens from docs/UI_STYLE.md §1.1. Never use raw hex in screens. */
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

/** Status tokens from docs/UI_STYLE.md §1.2 (base hue + text-on-dark variant). */
object StatusColors {
    val good = Color(0xFF35D0A6)
    val goodText = Color(0xFF7CF2D0)
    val caution = Color(0xFFFFB547)
    val cautionText = Color(0xFFFFD089)
    val danger = Color(0xFFFF5C7A)
    val dangerText = Color(0xFFFF93A7)
    val unknown = Color(0xFF6E8FA3)
    val unknownText = Color(0xFFA7C4D4)
}

/** "Lagoon" (light) tokens from docs/UI_STYLE.md §1.3. */
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

/** Gradients from docs/UI_STYLE.md §1.4. */
object ReefGradients {
    /** Page background (dark): top dark -> bottom lighter, "looking up from the deep". */
    val bgAbyss = Brush.verticalGradient(
        0.00f to Abyss.abyss1,
        0.55f to Abyss.abyss2,
        1.00f to Abyss.reef,
    )
    val bgLagoon = Brush.verticalGradient(listOf(Lagoon.bg0, Lagoon.bg2))
    val primaryButton = Brush.horizontalGradient(listOf(Abyss.lagoon, Abyss.lagoonBright))
    val coralButton = Brush.horizontalGradient(listOf(Abyss.coral, Abyss.coralSoft))
}
