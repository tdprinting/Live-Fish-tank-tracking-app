package com.reeflive.app.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The living ocean behind every screen (UI_STYLE §2):
 * 1. the theme gradient, 2. two drifting radial light shafts on Lissajous paths (23 s / 31 s),
 * 3. a caustic net (two tiled copies scrolling in different directions, screen-blended),
 * 4. twelve rising bubbles that wobble and respawn.
 * Time only drives the draw phase, so the layer never recomposes. [animate] = false
 * (reduce motion, screen not resumed) freezes it at a pleasant frame.
 */
@Composable
fun CausticBackground(
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val colors = ReefTheme.colors
    val tile = rememberCausticTile()
    var time by remember { mutableFloatStateOf(7.5f) }
    LaunchedEffect(animate) {
        if (!animate) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) time += ((now - last) / 1_000_000_000f).coerceAtMost(0.1f)
                last = now
            }
        }
    }
    val bubbles = remember { Bubble.seed(12) }
    Box(modifier.fillMaxSize().background(colors.bgBrush)) {
        Canvas(Modifier.fillMaxSize()) {
            val t = time
            drawShafts(t, colors)
            if (tile != null) drawCaustics(tile, t, colors)
            drawBubbles(bubbles, t, colors)
        }
        content()
    }
}

private fun DrawScope.drawShafts(t: Float, colors: ReefColors) {
    val w = size.width
    val h = size.height
    val r = w * 0.6f
    val c1 = Offset(
        w * (0.25f + 0.15f * sin(2f * PI.toFloat() * t / 23f)),
        h * (0.18f + 0.15f * sin(2f * PI.toFloat() * t / 23f * 2f + 0.7f)),
    )
    val c2 = Offset(
        w * (0.78f + 0.15f * cos(2f * PI.toFloat() * t / 31f)),
        h * (0.55f + 0.15f * sin(2f * PI.toFloat() * t / 31f * 1.5f)),
    )
    for ((c, k) in listOf(c1 to 1f, c2 to 0.8f)) {
        drawCircle(
            brush = Brush.radialGradient(
                listOf(colors.shaftColor.copy(alpha = colors.shaftColor.alpha * k), Color.Transparent),
                center = c, radius = r,
            ),
            radius = r, center = c,
        )
    }
}

private fun DrawScope.drawCaustics(tile: ImageBitmap, t: Float, colors: ReefColors) {
    val density = density
    // (6, 4) dp/s at scale 1.0 and (−4, 7) dp/s at scale 1.6
    drawTiled(tile, scale = 1.0f * density / 2.2f, dx = 6f * density * t, dy = 4f * density * t, alpha = colors.causticAlpha)
    drawTiled(tile, scale = 1.6f * density / 2.2f, dx = -4f * density * t, dy = 7f * density * t, alpha = colors.causticAlpha * 0.8f)
}

private fun DrawScope.drawTiled(tile: ImageBitmap, scale: Float, dx: Float, dy: Float, alpha: Float) {
    val tw = tile.width * scale
    val th = tile.height * scale
    if (tw < 4f || th < 4f) return
    val ox = dx - floor(dx / tw) * tw - tw
    val oy = dy - floor(dy / th) * th - th
    val size = IntSize(tw.roundToInt() + 1, th.roundToInt() + 1)
    var y = oy
    while (y < this.size.height) {
        var x = ox
        while (x < this.size.width) {
            drawImage(
                image = tile,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(tile.width, tile.height),
                dstOffset = IntOffset(x.roundToInt(), y.roundToInt()),
                dstSize = size,
                alpha = alpha,
                blendMode = BlendMode.Screen,
            )
            x += tw
        }
        y += th
    }
}

private class Bubble(val x: Float, val radiusDp: Float, val speedDp: Float, val alpha: Float, val phase: Float, val wobbleHz: Float) {
    companion object {
        fun seed(n: Int): List<Bubble> {
            val rnd = kotlin.random.Random(42)
            return List(n) {
                Bubble(
                    x = rnd.nextFloat(),
                    radiusDp = 2f + rnd.nextFloat() * 4f,
                    speedDp = 20f + rnd.nextFloat() * 20f,
                    alpha = 0.10f + rnd.nextFloat() * 0.15f,
                    phase = rnd.nextFloat(),
                    wobbleHz = 0.3f + rnd.nextFloat() * 0.4f,
                )
            }
        }
    }
}

private fun DrawScope.drawBubbles(bubbles: List<Bubble>, t: Float, colors: ReefColors) {
    val h = size.height
    val w = size.width
    for (b in bubbles) {
        val travel = h + 40f * density
        val y = h + 20f * density - ((b.phase * travel + b.speedDp * density * t) % travel)
        val x = b.x * w + 4f * density * sin(2f * PI.toFloat() * b.wobbleHz * t + b.phase * 6f)
        val r = b.radiusDp * density
        val c = Offset(x, y)
        drawCircle(colors.bubbleColor.copy(alpha = b.alpha * 0.35f), r, c)
        drawCircle(colors.bubbleColor.copy(alpha = b.alpha), r, c, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f * density))
        drawCircle(colors.bubbleColor.copy(alpha = b.alpha * 1.6f), r * 0.28f, Offset(x - r * 0.35f, y - r * 0.35f))
    }
}
