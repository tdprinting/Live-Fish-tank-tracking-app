package com.reeflive.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.reeflive.app.ui.theme.ReefTheme
import kotlin.math.max

/**
 * A water splash on tap (UI_STYLE §7): from the press point a soft disc swells to the control's far edge and
 * fades, followed by a thin ring like a ripple on the surface. Replaces the Material ripple on the live-tank
 * controls; 450 ms, nothing at all under Reduce motion. Read inside the draw phase only, so it never recomposes
 * the control it decorates.
 */
@Composable
fun Modifier.tapSplash(interaction: InteractionSource, color: Color, enabled: Boolean = true): Modifier {
    val reduce = ReefTheme.reduceMotion
    if (!enabled || reduce) return this
    val progress = remember { Animatable(1f) }
    var origin by remember { mutableStateOf<Offset?>(null) }
    LaunchedEffect(interaction) {
        interaction.interactions.collect { i ->
            if (i is PressInteraction.Press) {
                origin = i.pressPosition
                progress.snapTo(0f)
                progress.animateTo(1f, tween(450, easing = FastOutSlowInEasing))
            }
        }
    }
    return drawWithContent {
        drawContent()
        val p = progress.value
        val o = origin
        if (p < 1f && o != null) {
            val far = max(max(o.x, size.width - o.x), max(o.y, size.height - o.y)) * 1.1f
            // the disc
            drawCircle(color.copy(alpha = color.alpha * 0.28f * (1f - p)), radius = far * p, center = o)
            // the ring, a beat behind the disc
            val rp = (p - 0.15f).coerceAtLeast(0f) / 0.85f
            if (rp > 0f) {
                drawCircle(
                    color.copy(alpha = color.alpha * 0.5f * (1f - rp)),
                    radius = far * rp * 0.9f, center = o,
                    style = Stroke(width = (2.dp.toPx() * (1f - rp * 0.6f)).coerceAtLeast(0.5f)),
                )
            }
        }
    }
}
