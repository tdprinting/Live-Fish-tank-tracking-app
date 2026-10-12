package com.reeflive.app.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

/** Corner radii, UI_STYLE §4. */
object ReefShapes {
    val card = RoundedCornerShape(20.dp)
    val tile = RoundedCornerShape(18.dp)
    val pill = RoundedCornerShape(999.dp)
    val sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val dialog = RoundedCornerShape(24.dp)
    val field = RoundedCornerShape(14.dp)
    val overlay = RoundedCornerShape(14.dp)
}

val ReefMaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Motion tokens, UI_STYLE §7. All specs collapse to snaps when reduce-motion is on. */
object ReefMotion {
    const val FAST = 150
    const val NORMAL = 220
    const val SLOW = 350

    fun <T> spring(reduce: Boolean): AnimationSpec<T> =
        if (reduce) snap() else spring(dampingRatio = 0.8f, stiffness = 380f)

    fun <T> bouncy(reduce: Boolean): AnimationSpec<T> =
        if (reduce) snap() else spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)

    fun <T> tween(reduce: Boolean, ms: Int = NORMAL): AnimationSpec<T> =
        if (reduce) snap() else tween(ms, easing = FastOutSlowInEasing)
}

/** True when the user enabled Settings → Reduce motion. */
val LocalReduceMotion = staticCompositionLocalOf { false }
