package com.reeflive.app.ui.live

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import com.reeflive.app.ui.theme.ReefMotion

/**
 * Finite animation spec for the Live Tank chrome (panels sliding, tabs crossfading, sheets expanding), on the theme's
 * motion scale (UI_STYLE §7). Reduce motion snaps, except crossfades which keep a short 100 ms fade ([crossfade]).
 */
internal fun <T> liveSpec(reduce: Boolean, ms: Int = ReefMotion.NORMAL, crossfade: Boolean = false): FiniteAnimationSpec<T> = when {
    reduce && crossfade -> tween(100)
    reduce -> snap()
    else -> tween(ms, easing = FastOutSlowInEasing)
}
