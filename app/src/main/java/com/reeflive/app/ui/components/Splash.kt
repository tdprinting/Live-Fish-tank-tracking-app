package com.reeflive.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.reeflive.app.ui.theme.ReefTheme
import kotlinx.coroutines.launch
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

/**
 * Squash-and-bounce on press (UI_STYLE §7, live tank): the control squashes (wider, shorter) while the finger is down
 * and springs back with a little overshoot when it lifts, so even a quick tap visibly "boings". Pair it with
 * [tapSplash] and a haptic tick in the click handler. Scale is applied in a graphics layer from animation state read
 * in the layer block, so pressing never recomposes the control. Nothing under Reduce motion.
 */
@Composable
fun Modifier.pressSquash(interaction: InteractionSource, enabled: Boolean = true, strength: Float = 1f): Modifier {
    val reduce = ReefTheme.reduceMotion
    if (!enabled || reduce) return this
    val sx = remember { Animatable(1f) }
    val sy = remember { Animatable(1f) }
    LaunchedEffect(interaction) {
        interaction.interactions.collect { i ->
            when (i) {
                is PressInteraction.Press -> {
                    launch { sx.animateTo(1f + 0.07f * strength, tween(90, easing = FastOutSlowInEasing)) }
                    launch { sy.animateTo(1f - 0.12f * strength, tween(90, easing = FastOutSlowInEasing)) }
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    // a kick upward so short taps still bounce: stretch tall, then settle
                    launch { sx.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 520f), initialVelocity = -1.6f * strength) }
                    launch { sy.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 520f), initialVelocity = 2.6f * strength) }
                }
            }
        }
    }
    return graphicsLayer {
        scaleX = sx.value
        scaleY = sy.value
        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
    }
}

/**
 * A burst of bubbles rising from the press point (live tank, UI_STYLE §10.1): six bubbles of mixed sizes float up
 * about 56 dp with a wobble, swell a little and pop, over 700 ms. Drawn after the content and NOT clipped, so the
 * bubbles rise out of the button over the tank. Draw phase only; nothing under Reduce motion.
 */
@Composable
fun Modifier.bubbleBurst(interaction: InteractionSource, color: Color, rim: Color, enabled: Boolean = true): Modifier {
    val reduce = ReefTheme.reduceMotion
    if (!enabled || reduce) return this
    val progress = remember { Animatable(1f) }
    var origin by remember { mutableStateOf<Offset?>(null) }
    var seed by remember { mutableStateOf(0) }
    LaunchedEffect(interaction) {
        interaction.interactions.collect { i ->
            if (i is PressInteraction.Press) {
                origin = i.pressPosition
                seed++
                progress.snapTo(0f)
                progress.animateTo(1f, tween(700, easing = androidx.compose.animation.core.LinearOutSlowInEasing))
            }
        }
    }
    return drawWithContent {
        drawContent()
        val p = progress.value
        val o = origin ?: return@drawWithContent
        if (p >= 1f) return@drawWithContent
        val rise = 56.dp.toPx()
        for (k in 0 until 6) {
            // a fixed fan per press, varied by the press count so repeated taps don't look stamped
            val h = ((seed * 31 + k * 17) % 13) / 13f
            val delay = k * 0.06f
            val q = ((p - delay) / (1f - delay)).coerceIn(0f, 1f)
            if (q <= 0f) continue
            val spread = (k - 2.5f) * 7.dp.toPx() + (h - 0.5f) * 6.dp.toPx()
            val wobble = kotlin.math.sin((q * 9f + k).toDouble()).toFloat() * 3.dp.toPx()
            val x = o.x + spread * q + wobble
            val y = o.y - rise * (0.3f + 0.7f * h) * q
            val r = (2.5f + 3.5f * h).dp.toPx() * (0.6f + 0.6f * q)
            val a = if (q < 0.8f) 1f else (1f - q) / 0.2f
            drawCircle(color.copy(alpha = color.alpha * 0.35f * a), r, Offset(x, y))
            drawCircle(rim.copy(alpha = rim.alpha * 0.9f * a), r, Offset(x, y), style = Stroke(1.4.dp.toPx()))
            drawCircle(Color.White.copy(alpha = 0.8f * a), r * 0.28f, Offset(x - r * 0.35f, y - r * 0.35f))
        }
    }
}
