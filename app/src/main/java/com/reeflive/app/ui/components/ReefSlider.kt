package com.reeflive.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.reeflive.app.ui.theme.ReefLiveTheme
import com.reeflive.app.ui.theme.ReefShapes
import com.reeflive.app.ui.theme.ReefTheme
import com.reeflive.app.ui.theme.statusText
import com.reeflive.core.status.Status
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * Safe / caution / danger zones for a [ReefSlider] track. [safeFrom]..[safeTo] is safe; below [dangerBelow] or above
 * [dangerAbove] is danger; anything else is caution. A null danger bound means "caution all the way to that end".
 */
@Immutable
data class SliderZones(val dangerBelow: Double?, val safeFrom: Double, val safeTo: Double, val dangerAbove: Double?) {
    fun status(v: Double): Status = when {
        dangerBelow != null && v < dangerBelow -> Status.DANGER
        dangerAbove != null && v > dangerAbove -> Status.DANGER
        v >= safeFrom && v <= safeTo -> Status.GOOD
        else -> Status.CAUTION
    }
}

/**
 * What a [ReefSlider] edits. [curve] > 1 stretches the low end of the track (position = fraction^(1/curve)) so wide
 * "extreme" ranges such as nitrate 0–500 still give fine control around the normal values.
 */
@Immutable
data class ReefSliderSpec(
    val label: String,
    val unit: String,
    val min: Double,
    val max: Double,
    val step: Double,
    val decimals: Int,
    val zones: SliderZones? = null,
    val curve: Double = 1.0,
) {
    fun number(v: Double): String = String.format(Locale.US, "%.${decimals}f", v)
    fun format(v: Double): String = number(v) + if (unit.isEmpty()) "" else " $unit"

    /** Clamps to the range and snaps to [step]. */
    fun snap(v: Double): Double {
        val c = v.coerceIn(min, max)
        if (step <= 0.0) return c
        val snapped = min + ((c - min) / step).roundToLong() * step
        val k = 10.0.pow(decimals)
        return ((snapped * k).roundToLong() / k).coerceIn(min, max)
    }

    /** Track position 0..1 for [v]. */
    fun fraction(v: Double): Float {
        val f = ((v - min) / (max - min).coerceAtLeast(1e-12)).coerceIn(0.0, 1.0)
        return (if (curve == 1.0) f else f.pow(1.0 / curve)).toFloat()
    }

    /** Value at track position [t] 0..1. */
    fun valueAt(t: Float): Double {
        val f = t.toDouble().coerceIn(0.0, 1.0)
        return min + (if (curve == 1.0) f else f.pow(curve)) * (max - min)
    }
}

private val TrackHeight = 8.dp
private val ThumbRadius = 11.dp
private val TouchHeight = 48.dp

/**
 * Reef slider (UI_STYLE §6, sandbox): label, live value + unit with status icon, safe/caution/danger zones drawn on the
 * track, a marker at [referenceValue] (the real tank), −/+ steppers with press-and-hold repeat, tap the value to type an
 * exact number, a reset button back to the reference, and a haptic tick whenever the value crosses a zone boundary.
 *
 * [value] is the live (simulated) value; while the user drags or steps, the slider shows the local value and then
 * hands back to [value] so the simulation's own drift stays visible.
 */
@Composable
fun ReefSlider(
    spec: ReefSliderSpec,
    value: Double,
    onValueChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
    referenceValue: Double? = null,
    referenceLabel: String = "Your tank",
    onReset: (() -> Unit)? = null,
    enabled: Boolean = true,
    caption: String? = null,
    disabledReason: String? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    val c = ReefTheme.colors
    val haptics = rememberHaptics()
    val latestValue by rememberUpdatedState(value)
    val latestOnChange by rememberUpdatedState(onValueChange)
    var local by remember { mutableStateOf<Double?>(null) }
    var dragging by remember { mutableStateOf(false) }
    var touchedAt by remember { mutableLongStateOf(0L) }
    var editing by remember { mutableStateOf(false) }
    // after the finger lifts, keep the local value briefly so the thumb does not jump back before the sim applies it
    LaunchedEffect(touchedAt, dragging) {
        if (!dragging && local != null) { delay(700); local = null }
    }

    val shown = (local ?: value).coerceIn(spec.min, spec.max)
    val status = spec.zones?.status(shown)

    fun emit(raw: Double) {
        val before = local ?: latestValue
        val v = spec.snap(raw)
        val zones = spec.zones
        if (zones != null) {
            val a = zones.status(before)
            val b = zones.status(v)
            if (a != b) { if (b == Status.DANGER) haptics.confirm() else haptics.tick() }
        }
        local = v
        touchedAt = System.nanoTime()
        latestOnChange(v)
    }
    val reset: (() -> Unit)? = onReset ?: referenceValue?.let { r -> { emit(r) } }
    val showReset = enabled && referenceValue != null && reset != null && abs(shown - referenceValue) > spec.step / 2

    Column(modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.5f)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) { leading(); Spacer(Modifier.width(8.dp)) }
            Column(Modifier.weight(1f)) {
                Text(spec.label, style = MaterialTheme.typography.titleMedium, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val sub = when {
                    !enabled && disabledReason != null -> disabledReason
                    referenceValue != null -> "$referenceLabel ${spec.format(referenceValue)}" + (caption?.let { " · $it" } ?: "")
                    else -> caption
                }
                if (sub != null) Text(sub, style = MaterialTheme.typography.labelSmall, color = c.textTertiary, maxLines = 2)
            }
            Spacer(Modifier.width(8.dp))
            ValuePill(spec, shown, status, enabled) { editing = true }
            if (showReset) {
                SmallRoundButton(Icons.Rounded.RestartAlt, "Reset ${spec.label} to ${referenceLabel.lowercase()} value", enabled = true, repeat = false) { reset?.invoke() }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SmallRoundButton(Icons.Rounded.Remove, "Decrease ${spec.label}", enabled = enabled && shown > spec.min) {
                emit((local ?: latestValue) - spec.step)
            }
            SliderTrack(
                spec = spec,
                value = shown,
                reference = referenceValue,
                enabled = enabled,
                modifier = Modifier
                    .weight(1f)
                    .semantics {
                        contentDescription = spec.label
                        stateDescription = buildString {
                            append(spec.format(shown))
                            if (status != null) append(", ").append(statusLabel(status))
                            if (referenceValue != null) append(", ").append(referenceLabel).append(' ').append(spec.format(referenceValue))
                        }
                        progressBarRangeInfo = ProgressBarRangeInfo(shown.toFloat(), spec.min.toFloat()..spec.max.toFloat())
                        if (enabled) {
                            setProgress { t -> emit(t.toDouble()); true }
                            customActions = listOfNotNull(
                                CustomAccessibilityAction("Type exact value") { editing = true; true },
                                if (referenceValue != null) CustomAccessibilityAction("Reset to ${referenceLabel.lowercase()} value") { emit(referenceValue); true } else null,
                            )
                        } else disabled()
                    },
                onDragState = { dragging = it },
                onValue = { emit(it) },
            )
            SmallRoundButton(Icons.Rounded.Add, "Increase ${spec.label}", enabled = enabled && shown < spec.max) {
                emit((local ?: latestValue) + spec.step)
            }
        }
    }

    if (editing) {
        ExactValueDialog(spec, shown, onDismiss = { editing = false }) { v -> editing = false; emit(v) }
    }
}

@Composable
private fun ValuePill(spec: ReefSliderSpec, value: Double, status: Status?, enabled: Boolean, onEdit: () -> Unit) {
    val c = ReefTheme.colors
    Row(
        Modifier
            .heightIn(min = 40.dp)
            .clip(ReefShapes.pill)
            .background(c.glass)
            .border(1.dp, c.glassBorder, ReefShapes.pill)
            .clickable(enabled = enabled, onClickLabel = "Type exact value", role = Role.Button, onClick = onEdit)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (status != null) StatusIcon(status, size = 14.dp)
        Text(
            spec.format(value),
            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
            color = if (status != null) c.statusText(status) else c.text,
            maxLines = 1,
        )
        if (enabled) Icon(Icons.Rounded.Edit, contentDescription = null, tint = c.textTertiary, modifier = Modifier.size(14.dp))
    }
}

/** 40 dp round glass button; with [repeat], press-and-hold keeps stepping. */
@Composable
private fun SmallRoundButton(icon: ImageVector, description: String, enabled: Boolean, repeat: Boolean = true, onStep: () -> Unit) {
    val c = ReefTheme.colors
    val scope = rememberCoroutineScope()
    val step by rememberUpdatedState(onStep)
    Box(
        Modifier
            .padding(start = 4.dp)
            .size(40.dp)
            .clip(CircleShape)
            .background(c.glass)
            .border(1.dp, c.glassBorder, CircleShape)
            .alpha(if (enabled) 1f else 0.4f)
            .semantics {
                role = Role.Button
                contentDescription = description
                if (enabled) onClick { step(); true } else disabled()
            }
            .pointerInput(enabled, repeat) {
                if (!enabled) return@pointerInput
                detectTapGestures(onPress = {
                    step()
                    val job = if (repeat) scope.launch {
                        delay(420)
                        while (isActive) { step(); delay(80) }
                    } else null
                    tryAwaitRelease()
                    job?.cancel()
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = c.text, modifier = Modifier.size(20.dp))
    }
}

/**
 * Custom-drawn track: the zones, the reference marker and the thumb share one inset so they always line up
 * (the old Material slider drew its band strip with a different inset than its thumb).
 */
@Composable
private fun SliderTrack(
    spec: ReefSliderSpec,
    value: Double,
    reference: Double?,
    enabled: Boolean,
    modifier: Modifier,
    onDragState: (Boolean) -> Unit,
    onValue: (Double) -> Unit,
) {
    val c = ReefTheme.colors
    var pressed by remember { mutableStateOf(false) }
    val latestSpec by rememberUpdatedState(spec)
    val latestOnValue by rememberUpdatedState(onValue)
    val latestOnDrag by rememberUpdatedState(onDragState)
    Box(
        modifier
            .padding(horizontal = 4.dp)
            .height(TouchHeight)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                val pad = ThumbRadius.toPx()
                detectTapGestures { o -> latestOnValue(latestSpec.valueAt((o.x - pad) / (size.width - 2 * pad))) }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                val pad = ThumbRadius.toPx()
                fun at(x: Float) = latestSpec.valueAt((x - pad) / (size.width - 2 * pad))
                detectHorizontalDragGestures(
                    onDragStart = { o -> pressed = true; latestOnDrag(true); latestOnValue(at(o.x)) },
                    onDragEnd = { pressed = false; latestOnDrag(false) },
                    onDragCancel = { pressed = false; latestOnDrag(false) },
                    onHorizontalDrag = { change, _ -> change.consume(); latestOnValue(at(change.position.x)) },
                )
            },
    ) {
        val good = c.good.copy(alpha = 0.6f)
        val warn = c.caution.copy(alpha = 0.5f)
        val bad = c.danger.copy(alpha = 0.6f)
        val plain = c.glassHi
        val thumb = c.primaryBright
        val ring = c.bg
        val refColor = c.text
        val delta = c.coral
        Canvas(Modifier.fillMaxSize()) {
            val pad = ThumbRadius.toPx()
            val w = size.width - 2 * pad
            val cy = size.height / 2
            val th = TrackHeight.toPx()
            fun x(v: Double) = pad + spec.fraction(v) * w
            val top = cy - th / 2
            val z = spec.zones
            if (z == null) {
                drawRoundRect(plain, Offset(pad, top), Size(w, th), CornerRadius(th / 2))
            } else {
                fun seg(from: Double, to: Double, color: Color) {
                    val a = x(from.coerceIn(spec.min, spec.max)); val b = x(to.coerceIn(spec.min, spec.max))
                    if (b > a) drawRect(color, Offset(a, top), Size(b - a, th))
                }
                val lo = z.dangerBelow ?: spec.min
                val hi = z.dangerAbove ?: spec.max
                seg(spec.min, lo, bad); seg(lo, z.safeFrom, warn); seg(z.safeFrom, z.safeTo, good)
                seg(z.safeTo, hi, warn); seg(hi, spec.max, bad)
                // round the ends
                drawCircle(if ((z.dangerBelow ?: Double.NEGATIVE_INFINITY) > spec.min) bad else if (z.safeFrom > spec.min) warn else good, th / 2, Offset(pad, cy))
                drawCircle(if ((z.dangerAbove ?: Double.POSITIVE_INFINITY) < spec.max) bad else if (z.safeTo < spec.max) warn else good, th / 2, Offset(pad + w, cy))
            }
            val tx = x(value)
            if (reference != null) {
                val rx = x(reference.coerceIn(spec.min, spec.max))
                // how far the sandbox is pushed from the real tank
                if (abs(rx - tx) > 1f) drawLine(delta, Offset(minOf(rx, tx), top - 6.dp.toPx()), Offset(maxOf(rx, tx), top - 6.dp.toPx()), strokeWidth = 3.dp.toPx())
                drawLine(refColor, Offset(rx, cy - 13.dp.toPx()), Offset(rx, cy + 13.dp.toPx()), strokeWidth = 2.dp.toPx())
                drawCircle(refColor, 3.dp.toPx(), Offset(rx, cy - 13.dp.toPx()))
            }
            val r = if (pressed) ThumbRadius.toPx() + 3.dp.toPx() else ThumbRadius.toPx()
            drawCircle(ring, r + 2.dp.toPx(), Offset(tx, cy))
            drawCircle(thumb, r, Offset(tx, cy))
        }
    }
}

@Composable
private fun ExactValueDialog(spec: ReefSliderSpec, current: Double, onDismiss: () -> Unit, onSet: (Double) -> Unit) {
    val c = ReefTheme.colors
    var text by remember { mutableStateOf(spec.number(current)) }
    val parsed = parseDecimal(text)
    val valid = parsed != null && parsed >= spec.min && parsed <= spec.max
    val hint = "${spec.number(spec.min)} – ${spec.format(spec.max)}"
    val unitSuffix: (@Composable () -> Unit)? =
        if (spec.unit.isEmpty()) null else { { Text(spec.unit, style = MaterialTheme.typography.bodyLarge, color = c.textSecondary) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.sheet,
        shape = ReefShapes.dialog,
        title = { Text(spec.label, style = MaterialTheme.typography.headlineSmall, color = c.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ReefTextField(
                    value = text,
                    onValueChange = { text = it },
                    numeric = true,
                    label = "Exact value",
                    suffix = unitSuffix,
                    error = !valid,
                    onIme = { if (valid) onSet(parsed!!) },
                )
                val st = if (valid) spec.zones?.status(parsed!!) else null
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (st != null) { StatusIcon(st, size = 14.dp); Spacer(Modifier.width(6.dp)) }
                    Text(
                        if (valid) (st?.let { statusLabel(it) + " · " } ?: "") + "Range $hint" else "Enter a number from $hint",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (valid) c.textSecondary else c.dangerText,
                    )
                }
            }
        },
        confirmButton = { ReefButton("Set", { if (valid) onSet(parsed!!) }, enabled = valid, compact = true) },
        dismissButton = { ReefButton("Cancel", onDismiss, kind = ButtonKind.OUTLINED, compact = true) },
    )
}

// ---------------------------------------------------------------- previews

@Preview(name = "ReefSlider · Abyss", widthDp = 360, heightDp = 300, showBackground = true, fontScale = 1.3f)
@Composable
private fun ReefSliderPreview() = ReefLiveTheme(darkTheme = true, reduceMotion = true) {
    Column(Modifier.background(ReefTheme.colors.sheet).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ReefSlider(
            ReefSliderSpec("Temperature", "°C", 15.0, 40.0, 0.1, 1, SliderZones(18.0, 23.0, 30.0, 33.0)),
            value = 31.2, onValueChange = {}, referenceValue = 26.1,
        )
        ReefSlider(
            ReefSliderSpec("Nitrate", "ppm", 0.0, 500.0, 0.5, 1, SliderZones(null, 1.0, 20.0, 80.0), curve = 2.0),
            value = 12.0, onValueChange = {}, referenceValue = 12.0,
        )
    }
}
