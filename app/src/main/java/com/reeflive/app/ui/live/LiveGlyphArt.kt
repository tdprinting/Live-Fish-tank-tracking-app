package com.reeflive.app.ui.live

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.reeflive.app.ui.theme.ReefMotion
import com.reeflive.app.ui.theme.ReefTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * Animated glyphs for the live tank and the sandbox (UI_STYLE §5.1): the thermometer level and the bubbles follow
 * their slider, the sun rises and sets for the lights, the eye blinks shut for "hide controls", the speaker's waves
 * fade for mute, and the switch thumb morphs. Every animated value is read inside the Canvas draw lambda, so a
 * running animation redraws only that icon and never recomposes the panel or the GL view behind it. Reduce motion
 * snaps every transition and freezes the looping bubbles.
 */

private const val INK = 1.75f
private fun stroke(w: Float = INK) = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round)

/** Draw [block] in the 24-unit glyph box this scope's size is scaled to (letterboxed when not square). */
private fun DrawScope.inGlyphBox(block: DrawScope.() -> Unit) {
    val s = size.minDimension / 24f
    val dx = (size.width - 24f * s) / 2f
    val dy = (size.height - 24f * s) / 2f
    withTransform({ translate(dx, dy); scale(s, s, Offset.Zero) }, block)
}

private fun path(d: String): Path = PathParser().parsePathString(d).toPath()
private val tubePath by lazy { path("M12 3.5a2.25 2.25 0 0 1 2.25 2.25v8.1a4.25 4.25 0 1 1-4.5 0v-8.1A2.25 2.25 0 0 1 12 3.5Z") }
private val nozzlePath by lazy { path("M3.5 14.5h4.5a2 2 0 0 0 2-2v-1a2 2 0 0 0-2-2H3.5Z") }
private val eyePath by lazy { path("M2.5 12c2.5-4.5 5.7-6.5 9.5-6.5s7 2 9.5 6.5c-2.5 4.5-5.7 6.5-9.5 6.5S5 16.5 2.5 12Z") }
private val speakerPath by lazy { path("M4 9.5h3.5l5-4v13l-5-4H4Z") }
private val moonPath by lazy { path("M15.5 3.5a9 9 0 1 0 5 15.5A8 8 0 0 1 15.5 3.5Z") }
private val lidPath by lazy { path("M4.5 11.5c2 2.8 4.5 4 7.5 4s5.5-1.2 7.5-4") }

/** 0..1 that loops every [periodMs]; frozen at [frozen] under Reduce motion. */
@Composable
private fun loop(periodMs: Int, frozen: Float = 0.35f, label: String): State<Float> {
    if (ReefTheme.reduceMotion) return remember { mutableStateOf(frozen) }
    val t = rememberInfiniteTransition(label = label)
    return t.animateFloat(0f, 1f, infiniteRepeatable(tween(periodMs, easing = LinearEasing), RepeatMode.Restart), label = label)
}

// ---------------------------------------------------------------- thermometer

/**
 * Thermometer whose mercury follows [level] (0 = bottom of the slider's range, 1 = top), tinted cool → warm → hot.
 */
@Composable
fun ThermometerIcon(level: Float, modifier: Modifier = Modifier, size: Dp = 24.dp, tint: Color = ReefTheme.colors.text) {
    val c = ReefTheme.colors
    val reduce = ReefTheme.reduceMotion
    val lv by animateFloatAsState(level.coerceIn(0f, 1f), ReefMotion.spring(reduce), label = "mercury")
    val cold = c.primaryBright; val warm = c.coralSoft; val hot = c.danger
    Canvas(modifier.size(size)) {
        val l = lv
        val mercury = if (l < 0.5f) lerp(cold, warm, l * 2f) else lerp(warm, hot, (l - 0.5f) * 2f)
        inGlyphBox {
            // column from the bulb neck (y 14.2) up to y 5.7 at full
            val top = 14.2f - 8.5f * l
            drawRoundRect(mercury, Offset(11.1f, top), Size(1.8f, 14.2f - top + 0.4f), CornerRadius(0.9f))
            drawCircle(mercury, 2.1f, Offset(12f, 16.4f))
            drawPath(tubePath, tint, style = stroke())
            drawLine(tint, Offset(16.8f, 7f), Offset(18.8f, 7f), 1.5f, StrokeCap.Round)
            drawLine(tint, Offset(16.8f, 10f), Offset(18f, 10f), 1.5f, StrokeCap.Round)
            drawLine(tint, Offset(16.8f, 13f), Offset(18.8f, 13f), 1.5f, StrokeCap.Round)
        }
    }
}

// ---------------------------------------------------------------- bubbles / flow

/**
 * A pump nozzle blowing bubbles: more and bigger bubbles as [level] rises (0 = still water, 1 = full flow). The
 * bubbles drift right and up on a 2.6 s loop; Reduce motion shows a still frame.
 */
@Composable
fun BubblesIcon(level: Float, modifier: Modifier = Modifier, size: Dp = 24.dp, tint: Color = ReefTheme.colors.text, accent: Color = ReefTheme.colors.coralSoft) {
    val reduce = ReefTheme.reduceMotion
    val lv by animateFloatAsState(level.coerceIn(0f, 1f), ReefMotion.spring(reduce), label = "flowLevel")
    val t by loop(2600, label = "bubbles")
    Canvas(modifier.size(size)) {
        val l = lv
        val time = t
        inGlyphBox {
            drawPath(nozzlePath, tint, style = stroke())
            drawLine(tint, Offset(3.5f, 9.5f), Offset(3.5f, 14.5f), 1.5f, StrokeCap.Round)
            val n = (1 + (l * 5f)).toInt().coerceIn(1, 6)
            if (l < 0.03f) {
                // still water: one lazy bubble by the nozzle
                drawCircle(tint.copy(alpha = 0.5f), 1.1f, Offset(12.5f, 12f), style = stroke(1.3f))
            } else {
                for (i in 0 until n) {
                    val ph = (time + i / n.toFloat() + i * 0.13f) % 1f
                    val x = 11f + ph * 10f
                    val y = 12f - sin(ph * PI).toFloat() * (2.5f + 2f * l) - (i % 3) * 1.4f + 1.2f
                    val r = (0.7f + 0.9f * l) * (1f - 0.35f * ph) * (0.8f + 0.2f * (i % 2))
                    val a = (1f - ph * ph).coerceIn(0f, 1f)
                    drawCircle(tint.copy(alpha = 0.9f * a), r, Offset(x, y), style = stroke(1.3f))
                    if (r > 1.1f) drawCircle(accent.copy(alpha = 0.8f * a), r * 0.28f, Offset(x - r * 0.35f, y - r * 0.35f))
                }
            }
        }
    }
}

// ---------------------------------------------------------------- sun and moon

/**
 * Daylight over the tank: at [level] 0 a moon hangs over dark water; as the level rises the moon sets to the right
 * and the sun climbs from the left horizon to noon at 1. The horizon is the water line, with two ripples below it.
 */
@Composable
fun SunMoonIcon(level: Float, modifier: Modifier = Modifier, size: Dp = 24.dp, tint: Color = ReefTheme.colors.text, accent: Color = ReefTheme.colors.coralSoft) {
    val reduce = ReefTheme.reduceMotion
    val lv by animateFloatAsState(level.coerceIn(0f, 1f), ReefMotion.tween(reduce, ReefMotion.SLOW), label = "daylight")
    val c = ReefTheme.colors
    val night = c.primary
    Canvas(modifier.size(size)) {
        val l = lv
        inGlyphBox {
            // sky: everything above the horizon (y 15.5)
            clipRect(0f, 0f, 24f, 15.6f) {
                // moon: top at night, sets to the right as the day comes
                val ma = (PI / 2 * (1 - l)).toFloat()
                val mx = 12f + 7f * cos(ma); val my = 15.5f - 7f * sin(ma)
                val moonAlpha = (1f - l * 1.4f).coerceIn(0f, 1f)
                if (moonAlpha > 0.01f) {
                    withTransform({ translate(mx - 12f, my - 11f); scale(0.42f, 0.42f, Offset(12f, 11f)) }) {
                        drawPath(moonPath, night, alpha = moonAlpha, style = stroke(INK / 0.42f))
                    }
                }
                // sun: rises from the left horizon to noon
                val sa = (PI * (1 - 0.5 * l)).toFloat()
                val sx = 12f + 7.5f * cos(sa); val sy = 15.5f - 7.5f * sin(sa)
                val sunAlpha = (l * 2.5f).coerceIn(0f, 1f)
                if (sunAlpha > 0.01f) {
                    val r = 2.6f
                    drawCircle(accent, r, Offset(sx, sy), alpha = sunAlpha)
                    drawCircle(tint, r, Offset(sx, sy), alpha = sunAlpha, style = stroke(1.5f))
                    val rayLen = 1.2f + 1.3f * l
                    for (k in 0 until 8) {
                        val a = (k * PI / 4).toFloat()
                        val x0 = sx + cos(a) * (r + 1.1f); val y0 = sy + sin(a) * (r + 1.1f)
                        drawLine(tint, Offset(x0, y0), Offset(x0 + cos(a) * rayLen, y0 + sin(a) * rayLen), 1.4f, StrokeCap.Round, alpha = sunAlpha)
                    }
                }
            }
            // the water line and two ripples
            drawLine(tint, Offset(3f, 15.5f), Offset(21f, 15.5f), 1.6f, StrokeCap.Round)
            val ripple = lerp(night, accent, l * 0.6f)
            drawLine(ripple, Offset(6f, 18.5f), Offset(11f, 18.5f), 1.5f, StrokeCap.Round)
            drawLine(ripple, Offset(13.5f, 18.5f), Offset(18f, 18.5f), 1.5f, StrokeCap.Round)
            drawLine(ripple, Offset(9f, 21f), Offset(15f, 21f), 1.5f, StrokeCap.Round, alpha = 0.7f)
        }
    }
}

// ---------------------------------------------------------------- eye, speaker

/** Eye that blinks shut: [open] 1 = watching, 0 = closed (lashes down). */
@Composable
fun EyeIcon(open: Boolean, modifier: Modifier = Modifier, size: Dp = 24.dp, tint: Color = ReefTheme.colors.text, accent: Color = ReefTheme.colors.coralSoft) {
    val reduce = ReefTheme.reduceMotion
    val o by animateFloatAsState(if (open) 1f else 0f, ReefMotion.tween(reduce, ReefMotion.FAST), label = "eyelid")
    Canvas(modifier.size(size)) {
        val p = o
        inGlyphBox {
            if (p > 0.08f) {
                withTransform({ scale(1f, p, Offset(12f, 12f)) }) {
                    drawPath(eyePath, tint, style = stroke())
                    drawCircle(tint, 2.7f * p, Offset(12f, 12f))
                    drawCircle(accent, 0.9f * p, Offset(10.9f, 10.9f))
                }
            } else {
                // closed lid with three lashes
                drawPath(lidPath, tint, style = stroke())
                drawLine(tint, Offset(6.5f, 14.3f), Offset(4.9f, 16.5f), INK, StrokeCap.Round)
                drawLine(tint, Offset(12f, 15.5f), Offset(12f, 18.1f), INK, StrokeCap.Round)
                drawLine(tint, Offset(17.5f, 14.3f), Offset(19.1f, 16.5f), INK, StrokeCap.Round)
            }
        }
    }
}

/** Speaker whose sound waves fade and shrink into a cross when muted. */
@Composable
fun SpeakerIcon(on: Boolean, modifier: Modifier = Modifier, size: Dp = 24.dp, tint: Color = ReefTheme.colors.text, accent: Color = ReefTheme.colors.coralSoft) {
    val reduce = ReefTheme.reduceMotion
    val o by animateFloatAsState(if (on) 1f else 0f, ReefMotion.tween(reduce, ReefMotion.FAST), label = "mute")
    Canvas(modifier.size(size)) {
        val p = o
        inGlyphBox {
            drawPath(speakerPath, tint, style = stroke())
            if (p > 0.02f) {
                val s = 0.6f + 0.4f * p
                withTransform({ scale(s, s, Offset(13f, 12f)) }) {
                    drawArc(accent, -45f, 90f, false, Offset(12f, 8.5f), Size(7f, 7f), alpha = p, style = stroke())
                    drawArc(accent, -45f, 90f, false, Offset(11f, 5f), Size(14f, 14f), alpha = p * p, style = stroke())
                }
            }
            if (p < 0.98f) {
                val q = 1f - p
                drawLine(accent, Offset(16f, 10f), Offset(16f + 4f * q, 10f + 4f * q), INK, StrokeCap.Round, alpha = q)
                drawLine(accent, Offset(20f, 10f), Offset(20f - 4f * q, 10f + 4f * q), INK, StrokeCap.Round, alpha = q)
            }
        }
    }
}

// ---------------------------------------------------------------- switch

/**
 * Animated on/off switch: the track floods with lagoon water and the thumb slides across while its face morphs
 * from a flat dash (off) to a little wave (on); three bubbles rise through the track as it turns on. The row around
 * it handles the tap and the semantics.
 */
@Composable
fun ReefSwitch(on: Boolean, modifier: Modifier = Modifier) {
    val c = ReefTheme.colors
    val reduce = ReefTheme.reduceMotion
    val p by animateFloatAsState(if (on) 1f else 0f, ReefMotion.spring(reduce), label = "switch")
    val track by animateColorAsState(if (on) c.primary else c.glassHi, ReefMotion.tween(reduce, ReefMotion.FAST), label = "switchTrack")
    val thumb by animateColorAsState(if (on) Color.White else c.textSecondary, ReefMotion.tween(reduce, ReefMotion.FAST), label = "switchThumb")
    val face by animateColorAsState(if (on) c.primary else c.bg, ReefMotion.tween(reduce, ReefMotion.FAST), label = "switchFace")
    val fizz = remember { Animatable(1f) }
    var last by remember { mutableStateOf(on) }
    LaunchedEffect(on) {
        if (on && !last && !reduce) { fizz.snapTo(0f); fizz.animateTo(1f, ReefMotion.tween(false, 600)) }
        last = on
    }
    val border = c.glassBorder
    val bright = c.primaryBright
    Canvas(modifier.size(width = 52.dp, height = 32.dp).semantics { stateDescription = if (on) "On" else "Off" }) {
        val h = size.height; val w = size.width
        val r = h / 2
        drawRoundRect(track, Offset.Zero, size, CornerRadius(r))
        drawRoundRect(border, Offset(0.5f, 0.5f), Size(w - 1f, h - 1f), CornerRadius(r), style = Stroke(1.dp.toPx()))
        // bubbles fizzing up as it turns on
        val f = fizz.value
        if (f < 1f) {
            for (i in 0 until 3) {
                val ph = ((f * 1.3f) - i * 0.15f).coerceIn(0f, 1f)
                if (ph > 0f && ph < 1f) {
                    val x = w * (0.3f + 0.2f * i)
                    val y = h * (0.85f - 0.7f * ph)
                    drawCircle(bright.copy(alpha = (1f - ph) * 0.9f), 2.dp.toPx() * (0.6f + 0.4f * ph), Offset(x, y), style = Stroke(1.dp.toPx()))
                }
            }
        }
        // thumb
        val tr = r - 4.dp.toPx()
        val cx = r + (w - 2 * r) * p
        val centre = Offset(cx, r)
        drawCircle(thumb, tr, centre)
        // face: a dash that bends into a wave
        val wave = Path().apply {
            val a = tr * 0.55f
            val amp = tr * 0.3f * p
            moveTo(cx - a, r + amp * 0.2f)
            cubicTo(cx - a * 0.5f, r - amp, cx - a * 0.2f, r - amp, cx, r + amp * 0.2f)
            cubicTo(cx + a * 0.2f, r + amp * 1.4f, cx + a * 0.5f, r + amp * 1.4f, cx + a, r + amp * 0.2f)
        }
        drawPath(wave, face, style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round))
    }
}

/** Switch size, for layouts that reserve room for it. */
val ReefSwitchWidth: Dp = 52.dp

/** Accessible wrapper so a switch inside a clickable row still reads as a switch. */
fun Modifier.switchSemantics(on: Boolean, label: String): Modifier = semantics {
    role = Role.Switch
    contentDescription = label
    stateDescription = if (on) "On" else "Off"
}
