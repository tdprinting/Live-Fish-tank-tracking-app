package com.reeflive.app.ui.live

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.reeflive.app.ui.theme.ReefTheme
import com.reeflive.core.sim.NetFlush
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The goodbye after the net lifts a dead animal out (core FishNet): a little bathroom card pops up, the net tips the
 * body into the toilet bowl with a plop, the lever goes down and the water swirls it away, then "Bye bye, <name>!".
 * Light and kid-friendly. Tap anywhere to skip; [onSplash] / [onFlush] are for the sound and the buzz.
 */
@Composable
fun ToiletFlush(flush: NetFlush, onDone: () -> Unit, onSplash: () -> Unit = {}, onFlush: () -> Unit = {}) {
    val reduce = ReefTheme.reduceMotion
    val total = if (reduce) REDUCED_MS else TOTAL_MS
    val clock = remember(flush.seq) { Animatable(0f) }
    val done by rememberUpdatedState(onDone)
    val splash by rememberUpdatedState(onSplash)
    val flushed by rememberUpdatedState(onFlush)
    LaunchedEffect(flush.seq) {
        var splashed = false; var pulled = false
        clock.animateTo(total.toFloat(), tween(total, easing = LinearEasing)) {
            val ms = value * (TOTAL_MS.toFloat() / total)
            if (!splashed && ms >= DROP_END) { splashed = true; splash() }
            if (!pulled && ms >= LEVER_START) { pulled = true; flushed() }
        }
        done()
    }
    // the cartoon always runs on the full timeline; reduced motion just plays it faster
    val ms = clock.value * (TOTAL_MS.toFloat() / total)
    val panelIn = ease((ms / 350f).coerceIn(0f, 1f))
    val panelOut = ((ms - FADE_START) / (TOTAL_MS - FADE_START)).coerceIn(0f, 1f)
    val shown = panelIn * (1f - panelOut)
    val body = Color(0xFF000000 or flush.body.toLong())
    val accent = Color(0xFF000000 or flush.accent.toLong())
    Box(
        Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f * shown))
            .clickable(remember { MutableInteractionSource() }, indication = null) { done() }
            .semantics { contentDescription = "${flush.name} is flushed away. Tap to close." },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.graphicsLayer {
                val pop = 0.6f + 0.4f * panelIn + 0.06f * sin(PI.toFloat() * panelIn)
                scaleX = pop * (1f - 0.15f * panelOut); scaleY = scaleX
                alpha = shown
            },
        ) {
            Canvas(Modifier.size(260.dp)) { drawScene(ms, flush.isFish, body, accent) }
            Spacer(Modifier.height(10.dp))
            val cap = ((ms - CAPTION_START) / 400f).coerceIn(0f, 1f)
            Text(
                "Bye bye, ${flush.name}!",
                style = MaterialTheme.typography.headlineSmall, color = Candy.white, textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer { alpha = cap; translationY = (1f - ease(cap)) * 24f },
            )
            Text(
                "Off to the big blue sea 💙",
                style = MaterialTheme.typography.bodyMedium, color = Candy.white.copy(alpha = 0.8f), textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer { alpha = ((ms - CAPTION_START - 250f) / 400f).coerceIn(0f, 1f) },
            )
        }
    }
}

private const val TOTAL_MS = 4600
private const val REDUCED_MS = 2200
private const val NET_IN_END = 800f
private const val TIP_END = 1050f
private const val DROP_END = 1350f
private const val LEVER_START = 1550f
private const val SWIRL_START = 1700f
private const val SWIRL_END = 3300f
private const val CAPTION_START = 3100f
private const val FADE_START = 4100f

private val TILE = Color(0xFFCFE6F2)
private val TILE_LINE = Color(0xFFB5D3E4)
private val PORCELAIN = Color(0xFFFBFDFF)
private val SHADE = Color(0xFFDCE6EE)
private val OUTLINE = Color(0xFF8BA4B8)
private val WATER = Color(0xFF4FC3F7)
private val WATER_DEEP = Color(0xFF1E88C8)
private val NET_GREEN = Color(0xFF2E9E62)

private fun ease(x: Float) = x * x * (3 - 2 * x)

/** The bathroom card in a unit square scaled to the canvas. */
private fun DrawScope.drawScene(ms: Float, isFish: Boolean, body: Color, accent: Color) {
    val w = size.width
    fun p(x: Float, y: Float) = Offset(x * w, y * w)
    // tiles
    drawRoundRect(TILE, cornerRadius = CornerRadius(w * 0.08f))
    for (k in 1..4) {
        drawLine(TILE_LINE, p(0f, k * 0.2f), p(1f, k * 0.2f), w * 0.006f)
        drawLine(TILE_LINE, p(k * 0.2f, 0f), p(k * 0.2f, 0.62f), w * 0.006f)
    }
    drawRect(Color(0xFFA9C9DB), p(0f, 0.62f), Size(w, w * 0.38f))   // floor
    // cistern and its lever
    drawRoundRect(SHADE, p(0.27f, 0.09f), Size(w * 0.46f, w * 0.27f), CornerRadius(w * 0.05f))
    drawRoundRect(PORCELAIN, p(0.28f, 0.08f), Size(w * 0.44f, w * 0.26f), CornerRadius(w * 0.05f))
    drawRoundRect(OUTLINE, p(0.28f, 0.08f), Size(w * 0.44f, w * 0.26f), CornerRadius(w * 0.05f), style = Stroke(w * 0.008f))
    val press = when {
        ms < LEVER_START -> 0f
        ms < LEVER_START + 150 -> (ms - LEVER_START) / 150f
        ms < LEVER_START + 650 -> 1f
        else -> (1f - (ms - LEVER_START - 650) / 250f).coerceAtLeast(0f)
    }
    rotate(-28f * ease(press), pivot = p(0.34f, 0.15f)) {
        drawLine(Color(0xFFB8C4CE), p(0.34f, 0.15f), p(0.22f, 0.15f), w * 0.028f, cap = StrokeCap.Round)
        drawCircle(Color(0xFFB8C4CE), w * 0.022f, p(0.34f, 0.15f))
    }
    // pedestal, bowl rim, seat
    val base = Path().apply {
        moveTo(0.38f * w, 0.64f * w); lineTo(0.62f * w, 0.64f * w); lineTo(0.6f * w, 0.9f * w); lineTo(0.4f * w, 0.9f * w); close()
    }
    drawPath(base, PORCELAIN); drawPath(base, OUTLINE, style = Stroke(w * 0.008f))
    drawOval(SHADE, p(0.15f, 0.43f), Size(w * 0.7f, w * 0.38f))
    drawOval(PORCELAIN, p(0.16f, 0.41f), Size(w * 0.68f, w * 0.36f))
    drawOval(OUTLINE, p(0.16f, 0.41f), Size(w * 0.68f, w * 0.36f), style = Stroke(w * 0.008f))
    // the water, swirling once the lever goes down
    val bowl = Path().apply { addOval(androidx.compose.ui.geometry.Rect(p(0.26f, 0.47f), Size(w * 0.48f, w * 0.24f))) }
    val swirlT = ((ms - SWIRL_START) / (SWIRL_END - SWIRL_START)).coerceIn(0f, 1f)
    val swirling = ms >= LEVER_START
    clipPath(bowl) {
        drawRect(WATER, p(0.26f, 0.47f), Size(w * 0.48f, w * 0.24f))
        drawOval(WATER_DEEP.copy(alpha = if (swirling) 0.25f + 0.55f * ease(swirlT) else 0.2f), p(0.42f, 0.55f), Size(w * 0.16f, w * 0.08f))
        if (swirling) {
            val spin = (ms - LEVER_START) / 1000f * 360f * (1f + swirlT * 1.5f)
            val fade = (1f - ((ms - SWIRL_END) / 500f).coerceIn(0f, 1f))
            withTransform({ scale(1f, 0.5f, p(0.5f, 0.59f)); rotate(spin, p(0.5f, 0.59f)) }) {
                for (k in 0 until 3) {
                    val path = Path()
                    for (i in 0..24) {
                        val a = k * 2.09f + i * 0.17f
                        val rr = (0.2f - i * 0.0075f) * w
                        val pt = Offset(0.5f * w + rr * cos(a), 0.59f * w + rr * sin(a))
                        if (i == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
                    }
                    drawPath(path, Color.White.copy(alpha = 0.55f * fade), style = Stroke(w * 0.012f, cap = StrokeCap.Round))
                }
            }
        }
        // the plop's rings
        val ring = ((ms - DROP_END) / 500f)
        if (ring in 0f..1f) {
            val rr = 0.04f + 0.14f * ring
            drawOval(Color.White.copy(alpha = 0.8f * (1 - ring)), p(0.5f - rr, 0.59f - rr * 0.5f), Size(2 * rr * w, rr * w), style = Stroke(w * 0.01f))
        }
    }
    drawOval(OUTLINE, p(0.26f, 0.47f), Size(w * 0.48f, w * 0.24f), style = Stroke(w * 0.006f))
    // the body: in the net, falling, floating, then round and round and gone
    val fish: Triple<Offset, Float, Float>? = when {
        ms < TIP_END -> null   // drawn with the net
        ms < DROP_END -> {
            val f = (ms - TIP_END) / (DROP_END - TIP_END)
            Triple(p(0.56f - 0.06f * f, 0.33f + 0.26f * f * f), 1f, 40f + 140f * f)
        }
        ms < SWIRL_START -> Triple(p(0.5f, 0.59f + 0.008f * sin((ms - DROP_END) / 90f)), 1f, 180f)
        ms < SWIRL_END -> {
            val s = ease(swirlT)
            val a = swirlT * swirlT * 7f * PI.toFloat()
            val rr = 0.15f * (1f - s)
            Triple(p(0.5f + rr * cos(a), 0.59f + rr * 0.5f * sin(a)), (1f - s * 0.95f), 180f + a * 57.3f)
        }
        else -> null
    }
    fish?.let { (c, sc, rot) -> drawBody(c, w * 0.11f * sc, rot, isFish, body, accent) }
    // the net comes in from the top right, tips, and goes back out
    if (ms < LEVER_START) {
        val inT = ease((ms / NET_IN_END).coerceIn(0f, 1f))
        val outT = ease(((ms - DROP_END) / (LEVER_START - DROP_END)).coerceIn(0f, 1f))
        val tip = ease(((ms - NET_IN_END) / (TIP_END - NET_IN_END)).coerceIn(0f, 1f))
        val c = Offset((1.15f - 0.55f * inT + 0.6f * outT) * w, (-0.15f + 0.45f * inT - 0.5f * outT) * w)
        rotate(-65f * tip, pivot = c) {
            // handle off to the top right
            drawLine(Color(0xFF1F6E45), c + Offset(w * 0.12f, -w * 0.02f), c + Offset(w * 0.5f, -w * 0.3f), w * 0.025f, cap = StrokeCap.Round)
            if (ms < TIP_END) drawBody(c + Offset(0f, w * 0.05f), w * 0.11f, 0f, isFish, body, accent)
            // mesh bag hanging under the hoop
            val bag = Path().apply {
                moveTo(c.x - w * 0.12f, c.y); quadraticBezierTo(c.x, c.y + w * 0.2f, c.x + w * 0.12f, c.y); close()
            }
            drawPath(bag, Color.White.copy(alpha = 0.28f))
            for (i in -3..3) drawLine(Color.White.copy(alpha = 0.6f), Offset(c.x + i * w * 0.034f, c.y), Offset(c.x + i * w * 0.012f, c.y + w * 0.1f * (1f - (i * i) / 12f)), w * 0.004f)
            drawOval(NET_GREEN, Offset(c.x - w * 0.12f, c.y - w * 0.03f), Size(w * 0.24f, w * 0.06f), style = Stroke(w * 0.014f))
        }
    }
    // a few bubbles after it is gone
    if (ms > SWIRL_END - 200 && ms < SWIRL_END + 900) {
        val b = (ms - SWIRL_END + 200) / 1100f
        for (k in 0 until 4) {
            val bt = (b * 1.4f - k * 0.15f).coerceIn(0f, 1f)
            if (bt <= 0f || bt >= 1f) continue
            drawCircle(Color.White.copy(alpha = 0.8f * (1 - bt)), w * (0.012f + 0.006f * k), p(0.5f + 0.03f * sin(k * 2.1f + bt * 6f), 0.58f - 0.2f * bt), style = Stroke(w * 0.005f))
        }
    }
}

/** A cartoon fish (or a little crab-ish blob for an invert) of half-length [r], rotated [deg], with X eyes. */
private fun DrawScope.drawBody(c: Offset, r: Float, deg: Float, isFish: Boolean, body: Color, accent: Color) {
    rotate(deg, pivot = c) {
        if (isFish) {
            val tail = Path().apply {
                moveTo(c.x - r * 0.8f, c.y); lineTo(c.x - r * 1.35f, c.y - r * 0.5f); lineTo(c.x - r * 1.25f, c.y); lineTo(c.x - r * 1.35f, c.y + r * 0.5f); close()
            }
            drawPath(tail, accent)
            drawOval(body, Offset(c.x - r, c.y - r * 0.55f), Size(2 * r, 1.1f * r))
            // a stripe and a fin in the accent colour
            drawLine(accent.copy(alpha = 0.9f), Offset(c.x + r * 0.15f, c.y - r * 0.5f), Offset(c.x + r * 0.15f, c.y + r * 0.5f), r * 0.16f)
            val fin = Path().apply { moveTo(c.x - r * 0.3f, c.y - r * 0.5f); lineTo(c.x + r * 0.05f, c.y - r * 0.85f); lineTo(c.x + r * 0.3f, c.y - r * 0.48f); close() }
            drawPath(fin, accent)
            val e = Offset(c.x + r * 0.6f, c.y - r * 0.12f)
            val k = r * 0.13f
            drawLine(Color(0xFF1B1340), e + Offset(-k, -k), e + Offset(k, k), r * 0.08f, cap = StrokeCap.Round)
            drawLine(Color(0xFF1B1340), e + Offset(-k, k), e + Offset(k, -k), r * 0.08f, cap = StrokeCap.Round)
        } else {
            for (i in -2..2) drawLine(body, Offset(c.x + i * r * 0.3f, c.y), Offset(c.x + i * r * 0.45f, c.y + r * 0.75f), r * 0.1f, cap = StrokeCap.Round)
            drawOval(body, Offset(c.x - r * 0.8f, c.y - r * 0.5f), Size(1.6f * r, 1f * r))
            drawOval(accent.copy(alpha = 0.6f), Offset(c.x - r * 0.5f, c.y - r * 0.35f), Size(1f * r, 0.4f * r))
            for (s in listOf(-1f, 1f)) {
                val e = Offset(c.x + s * r * 0.3f, c.y - r * 0.6f)
                val k = r * 0.1f
                drawLine(Color(0xFF1B1340), e + Offset(-k, -k), e + Offset(k, k), r * 0.07f, cap = StrokeCap.Round)
                drawLine(Color(0xFF1B1340), e + Offset(-k, k), e + Offset(k, -k), r * 0.07f, cap = StrokeCap.Round)
            }
        }
    }
}
