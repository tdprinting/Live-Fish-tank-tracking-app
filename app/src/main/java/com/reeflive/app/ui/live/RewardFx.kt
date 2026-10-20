package com.reeflive.app.ui.live

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reeflive.app.ui.theme.ReefTheme
import com.reeflive.core.custom.economy.Gain
import com.reeflive.core.custom.economy.Levels
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/*
 * The money cheers (docs/ECONOMY.md §7, web care.js): sand dollars rise from the tank in bubbles and swim into the
 * money chip, a "+15" floats up, the chip gulps and counts up; a level-up is a splash of bubbles, fish and a big star.
 */

/** Where the money chip is (window px) and the bursts in flight; shared by the chip and [RewardFxLayer]. */
@Stable
class RewardFxState {
    var chip by mutableStateOf(Offset.Unspecified)
    /** Bumped when a burst lands: the chip squashes on it. */
    var landed by mutableLongStateOf(0L)
    internal val bursts = mutableStateListOf<Burst>()
    var levelUp by mutableStateOf<Gain?>(null)

    fun cheer(g: Gain) {
        if (g.levelUp > 0) levelUp = g
        bursts += Burst(g.amount, Random.nextLong(), System.nanoTime())
    }

    internal class Burst(val amount: Int, val seed: Long, val startNs: Long)
}

/** The chip records where it is so the coins know where to swim. */
fun Modifier.rewardTarget(fx: RewardFxState): Modifier = onGloballyPositioned { c ->
    val p = c.positionInWindow()
    fx.chip = Offset(p.x + c.size.height / 2f, p.y + c.size.height / 2f)
}

private const val BURST_MS = 1500L

/** Full-screen layer over the tank: coin bursts and the level-up splash. Draws nothing while idle. */
@Composable
fun RewardFxLayer(fx: RewardFxState, modifier: Modifier = Modifier) {
    val reduce = ReefTheme.reduceMotion
    var origin by remember { mutableStateOf(Offset.Zero) }
    var now by remember { mutableLongStateOf(System.nanoTime()) }
    // a frame clock only while something flies
    LaunchedEffect(fx.bursts.size) {
        while (fx.bursts.isNotEmpty()) {
            withFrameNanos { }
            now = System.nanoTime()
            val done = fx.bursts.filter { (now - it.startNs) / 1_000_000 > BURST_MS + 200 }
            if (done.isNotEmpty()) { fx.bursts.removeAll(done); fx.landed = now }
        }
    }
    Box(modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInWindow() }) {
        if (fx.bursts.isNotEmpty()) {
            Canvas(Modifier.fillMaxSize()) {
                val target = if (fx.chip.isSpecified) fx.chip - origin else Offset(size.width * 0.2f, 60f)
                for (b in fx.bursts) drawBurst(b, now, target, reduce)
            }
        }
        LevelUpSplash(fx, reduce)
    }
}

private fun DrawScope.drawBurst(b: RewardFxState.Burst, now: Long, target: Offset, reduce: Boolean) {
    val t = ((now - b.startNs) / 1e6f / BURST_MS).coerceIn(0f, 1.2f)
    val rng = Random(b.seed)
    val n = min(8, 3 + b.amount / 10)
    val start = Offset(size.width / 2f, size.height * 0.62f)
    val coin = 13.dp.toPx()
    for (i in 0 until n) {
        // each coin leaves a little later, bobs up out of the water, then swims along a curve into the chip
        val lag = i * 0.06f
        val k = ((t - lag) / 0.8f).coerceIn(0f, 1f)
        if (k <= 0f || k >= 1f) continue
        val e = FastOutSlowInEasing.transform(k)
        val from = start + Offset((rng.nextFloat() - 0.5f) * size.width * 0.5f, (rng.nextFloat() - 0.5f) * 80f)
        val ctrl = Offset((from.x + target.x) / 2 + (rng.nextFloat() - 0.5f) * 200f, min(from.y, target.y) + 40f)
        val p = quad(from, ctrl, target, e)
        val wob = if (reduce) 0f else sin((k * 6f + i) * PI.toFloat()) * 6.dp.toPx() * (1 - e)
        val c = Offset(p.x + wob, p.y)
        val s = if (k < 0.15f) k / 0.15f else 1f - 0.35f * e
        // the bubble around the coin, then the coin turning as it swims
        drawCircle(Color.White.copy(alpha = 0.22f * (1 - e)), coin * 1.45f * s, c)
        drawCircle(Color.White.copy(alpha = 0.55f * (1 - e)), coin * 1.45f * s, c, style = Stroke(1.5.dp.toPx()))
        drawCircle(Color.White.copy(alpha = 0.7f * (1 - e)), coin * 0.3f * s, c + Offset(-coin * 0.7f * s, -coin * 0.75f * s))
        translate(c.x - coin, c.y - coin) {
            rotate(if (reduce) 0f else (k * 540f + i * 40f), Offset(coin, coin)) {
                scale(s * (if (reduce) 1f else 0.6f + 0.4f * kotlin.math.abs(cos(k * 9f + i))), 1f * s, Offset(coin, coin)) {
                    drawSandDollarAt(coin)
                }
            }
        }
        // trailing bubbles
        if (!reduce) for (j in 1..3) {
            val q = quad(from, ctrl, target, FastOutSlowInEasing.transform((k - j * 0.04f).coerceIn(0f, 1f)))
            drawCircle(Color.White.copy(alpha = 0.35f * (1 - e)), (4 - j) * 1.6.dp.toPx(), q + Offset(sin(j * 2f + k * 20f) * 6f, 6f * j))
        }
    }
    // "+15" rising from the middle and fading
    val lt = (t / 1.1f).coerceIn(0f, 1f)
    if (lt < 1f) {
        val paint = plusPaint
        paint.textSize = 30.dp.toPx() * (0.8f + 0.2f * min(1f, lt * 5))
        paint.color = android.graphics.Color.argb(((1 - lt) * 255).toInt(), 0xFF, 0xE1, 0x4D)
        paint.setShadowLayer(6f, 0f, 3f, android.graphics.Color.argb(((1 - lt) * 200).toInt(), 0x1B, 0x13, 0x40))
        drawContext.canvas.nativeCanvas.drawText("+${b.amount}", start.x, start.y - 30.dp.toPx() - lt * 90.dp.toPx(), paint)
    }
}

private val plusPaint = android.graphics.Paint().apply {
    isAntiAlias = true; textAlign = android.graphics.Paint.Align.CENTER; typeface = android.graphics.Typeface.DEFAULT_BOLD
}

private fun quad(a: Offset, c: Offset, b: Offset, t: Float): Offset {
    val u = 1 - t
    return Offset(u * u * a.x + 2 * u * t * c.x + t * t * b.x, u * u * a.y + 2 * u * t * c.y + t * t * b.y)
}

/** [drawSandDollar] in a box of 2 × [r] at the current origin. */
private fun DrawScope.drawSandDollarAt(r: Float) {
    val c = Offset(r, r)
    drawCircle(Color(0xFFF3E3C3), r * 0.92f, c)
    drawCircle(Candy.navy, r * 0.92f, c, style = Stroke(r * 0.14f))
    for (i in 0 until 5) {
        val a = (i * 72.0 - 90.0) * PI / 180
        drawLine(Color(0xFFB8925A), c, Offset(c.x + (cos(a) * r * 0.55).toFloat(), c.y + (sin(a) * r * 0.55).toFloat()), strokeWidth = r * 0.16f)
    }
}

/** Level up: the screen dims to deep blue, bubbles and little fish rush up, a big star pops with the new level. */
@Composable
private fun LevelUpSplash(fx: RewardFxState, reduce: Boolean) {
    val g = fx.levelUp
    LaunchedEffect(g) { if (g != null) { delay(3600); if (fx.levelUp === g) fx.levelUp = null } }
    AnimatedVisibility(
        g != null,
        enter = fadeIn(tween(250)) + scaleIn(if (reduce) tween(0) else spring(dampingRatio = 0.45f, stiffness = 260f), initialScale = 0.6f),
        exit = fadeOut(tween(300)) + scaleOut(tween(300), targetScale = 1.1f),
    ) {
        val shown = remember { g } ?: return@AnimatedVisibility
        val rise = remember { Animatable(0f) }
        LaunchedEffect(Unit) { rise.animateTo(1f, tween(if (reduce) 0 else 3400, easing = LinearEasing)) }
        Box(
            Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, null) { fx.levelUp = null },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(Brush.radialGradient(listOf(Color(0xCC0E3B5A), Color(0xEE030912)), center, size.maxDimension * 0.7f))
                val rng = Random(7)
                // bubbles rushing up
                repeat(36) {
                    val x = rng.nextFloat() * size.width
                    val sp = 0.6f + rng.nextFloat() * 0.8f
                    val y = size.height * (1.1f - ((rise.value * sp + rng.nextFloat()) % 1.2f))
                    val r = (3 + rng.nextFloat() * 9).dp.toPx()
                    drawCircle(Color.White.copy(alpha = 0.25f), r, Offset(x + sin(rise.value * 12f + it) * 10f, y))
                    drawCircle(Color.White.copy(alpha = 0.6f), r, Offset(x + sin(rise.value * 12f + it) * 10f, y), style = Stroke(1.2.dp.toPx()))
                }
                // a little school swimming across
                val fishColors = listOf(Candy.orange, Candy.cyan, Candy.yellow, Candy.pink)
                repeat(6) { i ->
                    val y = size.height * (0.25f + 0.1f * (i % 3)) + (if (i >= 3) size.height * 0.4f else 0f)
                    val x = -60f + (size.width + 120f) * ((rise.value * 1.3f + i * 0.13f) % 1f)
                    drawFish(Offset(x, y + sin(rise.value * 10f + i) * 8f), (14 + 4 * (i % 2)).dp.toPx(), fishColors[i % fishColors.size])
                }
                // rays of light from the star
                val rays = 12
                for (i in 0 until rays) {
                    val a = (i * 360f / rays + rise.value * 40f) * PI.toFloat() / 180f
                    val p = Path().apply {
                        moveTo(center.x, center.y - 40.dp.toPx())
                        lineTo(center.x + cos(a - 0.08f) * size.maxDimension, center.y - 40.dp.toPx() + sin(a - 0.08f) * size.maxDimension)
                        lineTo(center.x + cos(a + 0.08f) * size.maxDimension, center.y - 40.dp.toPx() + sin(a + 0.08f) * size.maxDimension)
                        close()
                    }
                    drawPath(p, Candy.yellow.copy(alpha = 0.07f))
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(24.dp)) {
                Canvas(Modifier.size(130.dp)) { drawStar(Candy.yellow) }
                Text("Level ${shown.levelUp}!", color = Candy.white, fontSize = 40.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                val opens = Levels.opens(shown.levelUp)
                if (opens.isNotEmpty()) Text(opens, color = Candy.yellow, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                Text("Tap to keep going", color = Candy.white.copy(alpha = 0.7f), fontSize = 14.sp)
            }
        }
    }
}

private fun DrawScope.drawStar(fill: Color) {
    val c = center
    val R = size.minDimension / 2 * 0.95f
    val p = Path()
    for (i in 0 until 10) {
        val r = if (i % 2 == 0) R else R * 0.48f
        val a = (-90 + i * 36) * PI / 180
        val x = c.x + (cos(a) * r).toFloat()
        val y = c.y + (sin(a) * r).toFloat()
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close()
    drawPath(p, fill)
    drawPath(p, Candy.navy, style = Stroke(R * 0.07f))
    drawCircle(Color.White.copy(alpha = 0.55f), R * 0.12f, c + Offset(-R * 0.22f, -R * 0.25f))
}

/** A tiny cartoon fish facing right at [c], body length [len]. */
private fun DrawScope.drawFish(c: Offset, len: Float, color: Color) {
    val body = Path().apply {
        moveTo(c.x + len * 0.5f, c.y)
        quadraticTo(c.x, c.y - len * 0.42f, c.x - len * 0.35f, c.y)
        quadraticTo(c.x, c.y + len * 0.42f, c.x + len * 0.5f, c.y)
        close()
        moveTo(c.x - len * 0.3f, c.y)
        lineTo(c.x - len * 0.6f, c.y - len * 0.25f)
        lineTo(c.x - len * 0.6f, c.y + len * 0.25f)
        close()
    }
    drawPath(body, color)
    drawPath(body, Candy.navy, style = Stroke(len * 0.07f))
    drawCircle(Candy.navy, len * 0.06f, Offset(c.x + len * 0.25f, c.y - len * 0.06f))
}
