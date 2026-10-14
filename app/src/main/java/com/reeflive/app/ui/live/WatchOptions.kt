package com.reeflive.app.ui.live

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.reeflive.app.ui.theme.ReefLiveTheme
import com.reeflive.app.ui.theme.ReefTheme
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Time-lapse slider and the Watch-mode extras (owner request): one slider from real time to the fastest speed the
 * sim supports, snapping at its stops (evenly spaced, so the scale is logarithmic), with the speed and the tank clock
 * read out under it; a badge with a sun / moon running over its arc while time runs fast; and a torch for night
 * viewing. The Watch card itself lives in LiveMenu (LiveCategory.VIEW) and only uses existing systems: the
 * renderer's view light, the follow camera, the food tray, the sound toggle and "hide buttons".
 */

/** Speeds the time slider stops at (SimulationHolder caps the sandbox at 1000x). */
val TimeStops: List<Double> = listOf(1.0, 10.0, 60.0, 240.0, 600.0, 1000.0)

/** "Day 3 · 14:05" for a sim time in seconds (day 1 starts at sim time 0). */
fun tankClock(simTimeSec: Double): String {
    val day = floor(simTimeSec / 86400.0).toInt() + 1
    val sec = ((simTimeSec % 86400.0) + 86400.0) % 86400.0
    val h = (sec / 3600).toInt(); val m = ((sec % 3600) / 60).toInt()
    return String.format(Locale.US, "Day %d · %02d:%02d", day, h, m)
}

/** "60×", "1×". */
fun speedLabel(x: Double): String = if (x < 10) String.format(Locale.US, "%.0f×", x) else "${x.roundToInt()}×"

/**
 * The time-lapse slider: [speed] now, snapping to [stops]; [onSpeed] fires when the thumb settles on a stop. Shows the
 * speed in words and numbers and the live tank clock.
 */
@Composable
fun TimeSlider(
    speed: Double,
    simTimeSec: Double,
    onSpeed: (Double) -> Unit,
    modifier: Modifier = Modifier,
    stops: List<Double> = TimeStops,
    tone: Color = Candy.cyan,
) {
    val s = LiveTheme.style
    val nearest = stops.indices.minByOrNull { abs(kotlin.math.ln(stops[it]) - kotlin.math.ln(speed.coerceAtLeast(1.0))) } ?: 0
    var pos by remember(nearest) { mutableFloatStateOf(nearest.toFloat()) }
    val shown = stops[pos.roundToInt().coerceIn(0, stops.size - 1)]
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(speedLabel(shown), style = s.titleText, color = s.label)
            Text("  " + speedWords(shown), style = MaterialTheme.typography.bodySmall, color = s.labelDim, modifier = Modifier.weight(1f))
            Text(tankClock(simTimeSec), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = s.label)
        }
        Slider(
            value = pos,
            onValueChange = { pos = it },
            onValueChangeFinished = {
                val i = pos.roundToInt().coerceIn(0, stops.size - 1)
                pos = i.toFloat()
                if (stops[i] != speed) onSpeed(stops[i])
            },
            valueRange = 0f..(stops.size - 1).toFloat(),
            steps = (stops.size - 2).coerceAtLeast(0),
            colors = SliderDefaults.colors(thumbColor = tone, activeTrackColor = tone, inactiveTrackColor = s.label.copy(alpha = 0.18f)),
            modifier = Modifier.fillMaxWidth().semantics {
                contentDescription = "Time speed"
                stateDescription = "${speedLabel(shown)}, ${speedWords(shown)}"
            },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            stops.forEach { x -> Text(speedLabel(x), style = MaterialTheme.typography.labelSmall, color = if (x == shown) s.label else s.labelDim) }
        }
    }
}

private fun speedWords(x: Double): String = when {
    x <= 1.0 -> "real time"
    x <= 10.0 -> "a minute in 6 s"
    x <= 60.0 -> "an hour a minute"
    x <= 240.0 -> "a day in 6 min"
    x <= 600.0 -> "a day in 2½ min"
    else -> "a day in 1½ min"
}

/**
 * Shown over the tank while time runs fast: the sun (day) or moon (night) moving over its arc with the tank clock, a
 * ticking ring, the speed and the time. Hidden at real time.
 */
@Composable
fun FastForwardBadge(speed: Double, simTimeSec: Double, modifier: Modifier = Modifier) {
    if (speed <= 1.0) return
    val s = LiveTheme.style
    val reduce = ReefTheme.reduceMotion
    val spin = rememberInfiniteTransition(label = "ffSpin")
    val turn by spin.animateFloat(0f, 1f, infiniteRepeatable(tween(if (reduce) 1 else (2400 / kotlin.math.log10(speed + 1.0)).toInt().coerceAtLeast(300), easing = LinearEasing), RepeatMode.Restart), label = "turn")
    Row(
        modifier.livePanel(s, s.pillShape).padding(horizontal = 12.dp, vertical = 6.dp).semantics { contentDescription = "Time running at ${speedLabel(speed)}, ${tankClock(simTimeSec)}" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Canvas(Modifier.size(width = 44.dp, height = 26.dp)) {
            val w = size.width; val h = size.height
            val sec = ((simTimeSec % 86400.0) + 86400.0) % 86400.0
            val day = sec in 6 * 3600.0..18 * 3600.0
            // 06:00 → left horizon, 12:00 → top, 18:00 → right (and the moon the same way through the night)
            val f = (if (day) (sec - 6 * 3600) / (12 * 3600) else ((sec + 6 * 3600) % 86400) / (12 * 3600)).toFloat().coerceIn(0f, 1f)
            val a = PI.toFloat() * (1f - f)
            val r = w * 0.4f
            val c = Offset(w / 2, h * 0.92f)
            drawArc(s.labelDim, 180f, 180f, false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(1.5f * density))
            drawLine(s.labelDim, Offset(0f, c.y), Offset(w, c.y), 1.5f * density)
            val body = Offset(c.x + r * cos(a), c.y - r * sin(a))
            if (day) drawCircle(Candy.yellow, 5f * density, body) else {
                drawCircle(Color(0xFFE9F3FF), 5f * density, body)
                drawCircle(Candy.navy, 4f * density, body + Offset(2.5f * density, -1.5f * density))
            }
            // ticking ring: faster with the speed
            drawArc(Candy.cyan, turn * 360f, 70f, false, Offset(body.x - 8f * density, body.y - 8f * density), Size(16f * density, 16f * density), style = Stroke(1.5f * density))
        }
        Text(speedLabel(speed), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = s.label)
        Text(tankClock(simTimeSec), style = MaterialTheme.typography.labelMedium, color = s.labelDim)
    }
}

/** Where the torch points, in view pixels (null = the middle of the view). */
class TorchState {
    var x by mutableFloatStateOf(Float.NaN)
    var y by mutableFloatStateOf(Float.NaN)
}

/** Watches touches on the tank (never consumes them) so the torch follows the finger. */
fun Modifier.trackTorch(torch: TorchState, on: Boolean): Modifier = if (!on) this else pointerInput(torch) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        torch.x = down.position.x; torch.y = down.position.y
        while (true) {
            val e = awaitPointerEvent(PointerEventPass.Initial)
            val p = e.changes.firstOrNull() ?: break
            torch.x = p.position.x; torch.y = p.position.y
            if (!p.pressed) break
        }
    }
}

/** A warm torch beam lighting the tank where it points (drawn over the GL view; no touch handling). */
@Composable
fun TorchOverlay(torch: TorchState, on: Boolean, modifier: Modifier = Modifier) {
    if (!on) return
    Canvas(modifier.fillMaxSize()) {
        val c = if (torch.x.isNaN()) Offset(size.width / 2, size.height / 2) else Offset(torch.x, torch.y)
        val r = size.minDimension * 0.28f
        // the rest of the tank a little darker, the beam bright and warm
        drawRect(Color(0xFF02060C).copy(alpha = 0.25f))
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF4D8).copy(alpha = 0.55f), Color(0xFFFFE6B0).copy(alpha = 0.18f), Color.Transparent), c, r), r, c, blendMode = BlendMode.Screen)
        drawCircle(Color(0xFFFFF4D8).copy(alpha = 0.35f), r * 0.98f, c, style = Stroke(1.5f * density))
    }
}

// ---------------------------------------------------------------- previews

@Preview(name = "Time slider · Abyss", widthDp = 360, showBackground = true)
@Composable
private fun TimeSliderPreview() = ReefLiveTheme(darkTheme = true, reduceMotion = true) {
    Column(Modifier.background(ReefTheme.colors.bg).padding(12.dp).livePanel(LiveTheme.style).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TimeSlider(speed = 60.0, simTimeSec = 86400.0 + 14 * 3600 + 5 * 60, onSpeed = {})
        FastForwardBadge(speed = 240.0, simTimeSec = 2 * 86400.0 + 9 * 3600)
        FastForwardBadge(speed = 1000.0, simTimeSec = 2 * 86400.0 + 23 * 3600)
    }
}

@Preview(name = "Torch", widthDp = 360, heightDp = 240, showBackground = true, backgroundColor = 0xFF061018)
@Composable
private fun TorchPreview() = ReefLiveTheme(darkTheme = true, reduceMotion = true) {
    TorchOverlay(remember { TorchState() }, on = true, modifier = Modifier.width(360.dp))
}
