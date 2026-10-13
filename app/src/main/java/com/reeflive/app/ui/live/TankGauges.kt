package com.reeflive.app.ui.live

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.reeflive.app.render.TankProjection
import com.reeflive.app.ui.components.Glyph
import com.reeflive.app.ui.components.ReefGlyph
import com.reeflive.app.ui.components.ReefGlyphs
import com.reeflive.app.ui.theme.ReefTheme
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * In-tank gauges (UI_STYLE §10.7): small, tasteful read-outs stuck to the tank and its gear, drawn through the camera
 * (TankProjection) so they sit on the glass / equipment and move with the view. A stick-on thermometer on the front
 * glass, the heater's badge (glows while it heats, shows its set point), a salinity float, the lamp level and the pump
 * flow (its blades spin with the flow). In the sandbox a tap on the heater, salinity, lamp or pump badge opens a big
 * friendly dial; in the real tank they only read.
 */

/** The gauges; [paramId] is the SandboxParams row a dial changes (the heater has its own set point). */
enum class TankGauge(val label: String, val paramId: String?, val glyph: ReefGlyph, val lo: Double, val hi: Double, val step: Double, val decimals: Int, val unit: String) {
    HEATER("Heater", null, ReefGlyphs.flame, 20.0, 32.0, 0.5, 1, "°C"),
    SALINITY("Salt", "sal", ReefGlyphs.salt, 1.015, 1.035, 0.001, 3, ""),
    LIGHT("Lamp", "light", ReefGlyphs.sun, 0.0, 150.0, 5.0, 0, "%"),
    FLOW("Pumps", "flow", ReefGlyphs.powerhead, 0.0, 200.0, 5.0, 0, "%"),
    ;

    fun format(v: Double): String = String.format(Locale.US, "%.${decimals}f", v) + unit
}

/** Live values behind the gauges (tank cm anchors are fixed in [TankGaugeLayer]). */
class GaugeReadings(
    val tempC: Double,
    val sg: Double,
    val lightPct: Double,
    val flowPct: Double,
    val heaterSetC: Double,
    val heaterWorking: Boolean,
)

/** Heater heating right now (the sim heats below its set point; no core read-out, so inferred from the water). */
fun GaugeReadings.heating(): Boolean = heaterWorking && tempC < heaterSetC - 0.05

/**
 * Lays the gauges over the tank view (same bounds as the GL view). [onDial] is null in the real tank (read-only).
 */
@Composable
fun TankGaugeLayer(
    visible: Boolean,
    projection: TankProjection,
    r: GaugeReadings,
    onDial: ((TankGauge) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    // a frame tick read only while placing, so the gauges follow the camera without recomposing
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(visible) { while (visible) withFrameNanos { frame = it } }
    val reduce = ReefTheme.reduceMotion
    AnimatedVisibility(visible, modifier.fillMaxSize(), enter = fadeIn(liveSpec(reduce)), exit = fadeOut(liveSpec(reduce))) {
        Box(Modifier.fillMaxSize()) {
            Anchored(projection, { frame }, 114f, 36f, 33.8f) { Thermometer(r.tempC) }
            Anchored(projection, { frame }, 7f, 30f, 33.8f) {
                Badge(TankGauge.HEATER, if (r.heaterWorking) "Set ${TankGauge.HEATER.format(r.heaterSetC)}" else "Off", glow = if (r.heating()) Candy.orange else null, warn = !r.heaterWorking, onClick = onDial)
            }
            Anchored(projection, { frame }, 18f, 50f, 33.8f) {
                Badge(TankGauge.SALINITY, String.format(Locale.US, "%.3f", r.sg), warn = r.sg < 1.021 || r.sg > 1.028, onClick = onDial)
            }
            Anchored(projection, { frame }, 61f, 63f, 30f) {
                Badge(TankGauge.LIGHT, TankGauge.LIGHT.format(r.lightPct), glow = if (r.lightPct > 1.0) Candy.yellow else null, onClick = onDial)
            }
            Anchored(projection, { frame }, 112f, 50f, 33.8f) {
                Badge(TankGauge.FLOW, TankGauge.FLOW.format(r.flowPct), spin = (r.flowPct / 100.0).toFloat(), warn = r.flowPct < 15.0, onClick = onDial)
            }
        }
    }
}

/** Places [content] centred on the projected tank point, or hides it while the point is behind the camera. */
@Composable
private fun Anchored(projection: TankProjection, frame: () -> Long, x: Float, y: Float, z: Float, content: @Composable () -> Unit) {
    val pt = remember { FloatArray(3) }
    Box(
        Modifier.layout { m, c ->
            val p = m.measure(c.copy(minWidth = 0, minHeight = 0))
            val w = if (c.hasBoundedWidth) c.maxWidth else p.width
            val h = if (c.hasBoundedHeight) c.maxHeight else p.height
            layout(w, h) {
                frame()
                if (projection.project(x, y, z, pt)) {
                    val px = (pt[0] - p.width / 2f).roundToInt()
                    val py = (pt[1] - p.height / 2f).roundToInt()
                    if (px > -p.width && py > -p.height && px < w && py < h) p.place(px, py)
                }
            }
        },
    ) { content() }
}

/** A stick-on glass thermometer: the red column follows the water temperature (18–34 °C), the reading under it. */
@Composable
private fun Thermometer(tempC: Double) {
    val s = LiveTheme.style
    val frac = ((tempC - 18.0) / 16.0).coerceIn(0.0, 1.0).toFloat()
    val tone = when {
        tempC < 23.0 -> Candy.cyan
        tempC > 30.0 -> Candy.red
        else -> Candy.green
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.semantics { contentDescription = "Water temperature " + String.format(Locale.US, "%.1f", tempC) + " degrees" },
    ) {
        Canvas(Modifier.size(width = 18.dp, height = 62.dp)) {
            val ow = 2.dp.toPx()
            val bulbR = size.width / 2f - ow
            val tubeW = size.width * 0.46f
            val tubeTop = ow
            val tubeBottom = size.height - bulbR * 1.6f
            val cx = size.width / 2f
            // glass
            drawRoundRect(Color.White.copy(alpha = 0.82f), Offset(cx - tubeW / 2, tubeTop), Size(tubeW, tubeBottom - tubeTop + bulbR), CornerRadius(tubeW / 2))
            drawCircle(Color.White.copy(alpha = 0.82f), bulbR, Offset(cx, size.height - bulbR - ow))
            // column
            val colTop = tubeBottom - (tubeBottom - tubeTop - tubeW * 0.3f) * frac
            drawRoundRect(Candy.red, Offset(cx - tubeW * 0.25f, colTop), Size(tubeW * 0.5f, tubeBottom - colTop + bulbR), CornerRadius(tubeW * 0.25f))
            drawCircle(Candy.red, bulbR * 0.68f, Offset(cx, size.height - bulbR - ow))
            // ticks every 2 °C
            for (k in 0..8) {
                val ty = tubeBottom - (tubeBottom - tubeTop - tubeW * 0.3f) * (k / 8f)
                drawLine(s.outline.copy(alpha = 0.6f), Offset(cx + tubeW / 2, ty), Offset(cx + tubeW / 2 + (if (k % 2 == 0) 4.dp.toPx() else 2.dp.toPx()), ty), 1.dp.toPx())
            }
            drawRoundRect(s.outline, Offset(cx - tubeW / 2, tubeTop), Size(tubeW, tubeBottom - tubeTop + bulbR), CornerRadius(tubeW / 2), style = Stroke(ow))
            drawCircle(s.outline, bulbR, Offset(cx, size.height - bulbR - ow), style = Stroke(ow))
        }
        Text(
            String.format(Locale.US, "%.1f°", tempC),
            style = s.buttonText.copy(fontSize = 11.sp, lineHeight = 13.sp),
            color = s.onCandy,
            modifier = Modifier.padding(top = 2.dp).clip(RoundedCornerShape(50)).background(tone).border(1.5.dp, s.outline, RoundedCornerShape(50)).padding(horizontal = 6.dp, vertical = 1.dp),
        )
    }
}

/** A small sea-glass badge: the gauge glyph (glowing / spinning) and its reading; tappable when [onClick] is set. */
@Composable
private fun Badge(g: TankGauge, text: String, glow: Color? = null, spin: Float = 0f, warn: Boolean = false, onClick: ((TankGauge) -> Unit)?) {
    val s = LiveTheme.style
    val reduce = ReefTheme.reduceMotion
    val pulse = if (glow != null && !reduce) {
        rememberInfiniteTransition(label = "gaugeGlow").animateFloat(0.45f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "glow").value
    } else 1f
    val angle = if (spin > 0.01f && !reduce) {
        val ms = (900f / spin.coerceIn(0.1f, 2f)).toInt()
        rememberInfiniteTransition(label = "gaugeSpin").animateFloat(0f, 360f, infiniteRepeatable(tween(ms, easing = LinearEasing)), label = "spin").value
    } else 0f
    val tone = if (warn) Candy.red else Candy.white
    var m = Modifier
        .heightIn(min = 30.dp)
        .clip(RoundedCornerShape(50))
        .background(s.panel)
        .border(2.dp, s.outline, RoundedCornerShape(50))
    if (onClick != null) m = m.clickable(onClickLabel = "Change ${g.label.lowercase(Locale.US)}") { onClick(g) }
    Row(m.padding(start = 3.dp, end = 8.dp, top = 3.dp, bottom = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier.size(24.dp).clip(CircleShape).background(glow?.copy(alpha = pulse) ?: tone).border(1.5.dp, s.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Glyph(g.glyph, size = 16.dp, tint = s.onCandy, modifier = Modifier.rotate(angle))
        }
        Text(text, style = s.buttonText.copy(fontSize = 11.sp, lineHeight = 13.sp), color = s.label)
    }
}

/**
 * The big friendly dial for one gauge (sandbox only): drag round the ring or use − / +, then "Set". [value] is the
 * starting value in the gauge's units.
 */
@Composable
fun GaugeDial(g: TankGauge, value: Double, onSet: (Double) -> Unit, onDismiss: () -> Unit) {
    val s = LiveTheme.style
    var v by remember(g) { mutableDoubleStateOf(snap(g, value)) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.width(300.dp).padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LivePanel(Modifier.fillMaxWidth()) {
                Text(g.label, style = s.titleText, color = s.label, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                    DialRing(g, v) { v = it }
                    Text(g.format(v), style = s.titleText.copy(fontSize = 28.sp, lineHeight = 32.sp), color = s.label)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    LivePill("−", { v = snap(g, v - g.step) }, tone = Candy.cyan)
                    LivePill("+", { v = snap(g, v + g.step) }, tone = Candy.orange)
                }
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    LivePill("Cancel", onDismiss, tone = Candy.white)
                    LivePill("Set", { onSet(v) }, tone = Candy.green)
                }
            }
        }
    }
}

private fun snap(g: TankGauge, v: Double): Double {
    val c = v.coerceIn(g.lo, g.hi)
    return (Math.round((c - g.lo) / g.step) * g.step + g.lo).coerceIn(g.lo, g.hi)
}

/** A 270° ring: cold-to-hot gradient of candy dots, the knob at the value; drag or tap anywhere on it. */
@Composable
private fun DialRing(g: TankGauge, v: Double, onChange: (Double) -> Unit) {
    val s = LiveTheme.style
    val start = 135f
    val sweep = 270f
    fun valueAt(o: Offset, w: Float, h: Float): Double {
        val a = Math.toDegrees(atan2((o.y - h / 2).toDouble(), (o.x - w / 2).toDouble())).toFloat()
        var rel = (a - start + 360f) % 360f
        if (rel > sweep) rel = if (rel - sweep < (360f - sweep) / 2) sweep else 0f
        return snap(g, g.lo + (g.hi - g.lo) * rel / sweep)
    }
    Canvas(
        Modifier
            .size(200.dp)
            .pointerInput(g) {
                detectTapGestures { o -> onChange(valueAt(o, size.width.toFloat(), size.height.toFloat())) }
            }
            .pointerInput(g) {
                detectDragGestures { change, _ -> change.consume(); onChange(valueAt(change.position, size.width.toFloat(), size.height.toFloat())) }
            },
    ) {
        val stroke = 18.dp.toPx()
        val r = size.minDimension / 2 - stroke
        val tl = Offset(size.width / 2 - r, size.height / 2 - r)
        drawArc(s.outline, start, sweep, false, tl, Size(r * 2, r * 2), style = Stroke(stroke + 5.dp.toPx(), cap = StrokeCap.Round))
        drawArc(Color.White.copy(alpha = 0.85f), start, sweep, false, tl, Size(r * 2, r * 2), style = Stroke(stroke, cap = StrokeCap.Round))
        val frac = ((v - g.lo) / (g.hi - g.lo)).toFloat().coerceIn(0f, 1f)
        val tone = when (g) {
            TankGauge.HEATER -> if (frac > 0.6f) Candy.red else if (frac < 0.25f) Candy.cyan else Candy.orange
            TankGauge.SALINITY -> Candy.cyan
            TankGauge.LIGHT -> Candy.yellow
            TankGauge.FLOW -> Candy.green
        }
        if (frac > 0f) drawArc(tone, start, sweep * frac, false, tl, Size(r * 2, r * 2), style = Stroke(stroke * 0.7f, cap = StrokeCap.Round))
        val a = Math.toRadians((start + sweep * frac).toDouble())
        val k = Offset(size.width / 2 + r * cos(a).toFloat(), size.height / 2 + r * sin(a).toFloat())
        drawCircle(tone, stroke * 0.75f, k)
        drawCircle(s.outline, stroke * 0.75f, k, style = Stroke(3.dp.toPx()))
    }
}
