package com.reeflive.app.ui.live

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.reeflive.app.ui.charts.smoothPath
import com.reeflive.app.ui.components.ButtonKind
import com.reeflive.app.ui.components.ReefButton
import com.reeflive.app.ui.components.ReefChip
import com.reeflive.app.ui.components.ReefSheet
import com.reeflive.app.ui.components.StatusPill
import com.reeflive.app.ui.theme.GlassCard
import com.reeflive.app.ui.theme.ReefLiveTheme
import com.reeflive.app.ui.theme.ReefShapes
import com.reeflive.app.ui.theme.ReefTheme
import com.reeflive.core.sim.Mood
import com.reeflive.core.sim.WaterState
import com.reeflive.core.sim.timelapse.OrganismReport
import com.reeflive.core.sim.timelapse.OrganismSample
import com.reeflive.core.sim.timelapse.SpeciesReport
import com.reeflive.core.sim.timelapse.TimelapseChange
import com.reeflive.core.sim.timelapse.TimelapseProgress
import com.reeflive.core.sim.timelapse.TimelapseRecording
import com.reeflive.core.sim.timelapse.TimelapseReport
import com.reeflive.core.sim.timelapse.TimelapseSample
import com.reeflive.core.sim.timelapse.TimelapseSpec
import com.reeflive.core.sim.timelapse.TimelapseState
import com.reeflive.core.sim.timelapse.Verdict
import com.reeflive.core.status.Status
import java.util.Locale
import kotlin.math.roundToInt

// ---------------------------------------------------------------- setup model

/** A parameter you can time-lapse: slider range comes from the sandbox spec, widened where "super high" is the point. */
internal data class LapseParam(val id: String, val spec: SandboxSliderSpec)

internal object LapseParams {
    val all: List<LapseParam> = listOf(
        LapseParam("sg", SandboxSpecs.salinity.copy(id = "sg", min = 1.010, max = 1.045)),
        LapseParam("temp", SandboxSpecs.temp.copy(min = 18.0, max = 36.0)),
        LapseParam("no3", SandboxSpecs.nitrate.copy(max = 200.0)),
        LapseParam("po4", SandboxSpecs.phosphate.copy(max = 3.0)),
        LapseParam("alk", SandboxSpecs.alk.copy(min = 2.0, max = 20.0)),
        LapseParam("ca", SandboxSpecs.calcium),
        LapseParam("mg", SandboxSpecs.magnesium),
        LapseParam("ph", SandboxSpecs.ph),
        LapseParam("nh3", SandboxSpecs.ammonia.copy(max = 2.0)),
        LapseParam("do", SandboxSpecs.oxygen),
    )
    fun byId(id: String) = all.first { it.id == id }
}

internal data class LapseDuration(val label: String, val hours: Double)

internal val lapseDurations = listOf(
    LapseDuration("6 h", 6.0), LapseDuration("1 d", 24.0), LapseDuration("3 d", 72.0),
    LapseDuration("1 wk", 168.0), LapseDuration("2 wk", 336.0), LapseDuration("4 wk", 672.0),
)

internal val lapseRamps = listOf(LapseDuration("6 h", 6.0), LapseDuration("1 d", 24.0), LapseDuration("3 d", 72.0), LapseDuration("1 wk", 168.0))

internal data class LapsePreset(val label: String, val param: String, val target: Double, val ramp: Boolean, val rampHours: Double, val durationHours: Double)

internal val lapsePresets = listOf(
    LapsePreset("Salinity 1.035", "sg", 1.035, false, 0.0, 72.0),
    LapsePreset("Salinity creep to 1.032", "sg", 1.032, true, 168.0, 336.0),
    LapsePreset("Heat wave 31°C", "temp", 31.0, true, 24.0, 168.0),
    LapsePreset("Nitrate 80", "no3", 80.0, false, 0.0, 336.0),
    LapsePreset("Alk crash to 5", "alk", 5.0, false, 0.0, 168.0),
    LapsePreset("Ammonia 0.5", "nh3", 0.5, false, 0.0, 72.0),
)

internal fun lapseSpec(param: String, target: Double, ramp: Boolean, rampHours: Double, durationHours: Double, label: String = ""): TimelapseSpec {
    val name = label.ifBlank { "${LapseParams.byId(param).spec.label} → ${fmtLapse(target, LapseParams.byId(param).spec.decimals)}" }
    val change = if (ramp) TimelapseChange.Ramp(param, target, rampHours.coerceAtMost(durationHours)) else TimelapseChange.Step(param, target)
    return TimelapseSpec(listOf(change), durationHours, label = name)
}

private fun fmtLapse(v: Double, decimals: Int) = String.format(Locale.US, "%.${decimals}f", v)

/** "6 h", "2 d 4 h", "3 wk". */
internal fun hoursText(h: Double): String {
    val total = h.roundToInt()
    return when {
        total < 48 -> "$total h"
        total % 168 == 0 -> "${total / 168} wk"
        else -> "${total / 24} d" + if (total % 24 != 0) " ${total % 24} h" else ""
    }
}

// ---------------------------------------------------------------- sheet

/** Time-lapse bottom sheet: setup, progress, then results (chart + scrubber + water strip + report). */
@Composable
fun TimelapseSheet(
    state: TimelapseState,
    water: WaterState,
    onStart: (TimelapseSpec) -> Unit,
    onCancel: () -> Unit,
    onNew: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ReefSheet(onDismiss = onDismiss, modifier = modifier) {
        TimelapseContent(state, water, onStart, onCancel, onNew)
    }
}

@Composable
fun TimelapseContent(
    state: TimelapseState,
    water: WaterState,
    onStart: (TimelapseSpec) -> Unit,
    onCancel: () -> Unit,
    onNew: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Time-lapse", style = MaterialTheme.typography.headlineSmall, color = ReefTheme.colors.text)
        when (state) {
            is TimelapseState.Idle -> LapseSetup(water, onStart)
            is TimelapseState.Failed -> {
                Text(state.message, style = MaterialTheme.typography.bodyMedium, color = ReefTheme.colors.dangerText)
                ReefButton("Try again", onNew, Modifier.fillMaxWidth(), kind = ButtonKind.TONAL, icon = Icons.Rounded.Replay)
            }
            is TimelapseState.Running -> LapseProgress(state.spec, state.progress, onCancel)
            is TimelapseState.Done -> LapseResults(state, onNew)
        }
    }
}

@Composable
private fun SubHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = ReefTheme.colors.textSecondary)
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

// ---------------------------------------------------------------- setup

@Composable
private fun LapseSetup(water: WaterState, onStart: (TimelapseSpec) -> Unit) {
    val c = ReefTheme.colors
    var param by remember { mutableStateOf("sg") }
    var target by remember { mutableStateOf(1.035) }
    var ramp by remember { mutableStateOf(false) }
    var rampHours by remember { mutableStateOf(24.0) }
    var duration by remember { mutableStateOf(72.0) }
    var presetLabel by remember { mutableStateOf("Salinity 1.035") }
    val p = LapseParams.byId(param)

    SubHeader("Presets")
    ChipRow {
        lapsePresets.forEach { pr ->
            ReefChip(pr.label, selected = presetLabel == pr.label, onClick = {
                param = pr.param; target = pr.target; ramp = pr.ramp; if (pr.ramp) rampHours = pr.rampHours
                duration = pr.durationHours; presetLabel = pr.label
            })
        }
    }

    SubHeader("What changes")
    ChipRow {
        LapseParams.all.forEach { lp ->
            ReefChip(lp.spec.label, selected = param == lp.id, onClick = {
                param = lp.id; presetLabel = ""
                target = water.get(lp.id)?.coerceIn(lp.spec.min, lp.spec.max) ?: lp.spec.min
            })
        }
    }
    val now = water.get(param)
    Text(
        "Now " + (now?.let { fmtLapse(it, p.spec.decimals) } ?: "?") + (if (p.spec.unit.isEmpty()) "" else " ${p.spec.unit}"),
        style = MaterialTheme.typography.labelMedium, color = c.textSecondary,
    )
    BandSlider(p.spec.copy(label = "Target"), target.coerceIn(p.spec.min, p.spec.max)) { target = it; presetLabel = "" }

    SubHeader("How")
    ChipRow {
        ReefChip("Step (instant)", selected = !ramp, onClick = { ramp = false; presetLabel = "" })
        ReefChip("Ramp", selected = ramp, onClick = { ramp = true; presetLabel = "" })
    }
    if (ramp) {
        ChipRow {
            lapseRamps.forEach { r -> ReefChip("Over ${r.label}", selected = rampHours == r.hours, onClick = { rampHours = r.hours; presetLabel = "" }) }
        }
    }

    SubHeader("Fast-forward")
    ChipRow {
        lapseDurations.forEach { d -> ReefChip(d.label, selected = duration == d.hours, onClick = { duration = d.hours; presetLabel = "" }) }
    }

    val spec = lapseSpec(param, target, ramp, rampHours, duration, presetLabel.ifBlank { "" })
    Text(
        (if (ramp) "Ramp ${p.spec.label.lowercase()} to " else "Set ${p.spec.label.lowercase()} to ") +
            fmtLapse(target, p.spec.decimals) + (if (p.spec.unit.isEmpty()) "" else " ${p.spec.unit}") +
            (if (ramp) " over ${hoursText(rampHours.coerceAtMost(duration))}" else "") + ", then watch ${hoursText(duration)} go by.",
        style = MaterialTheme.typography.bodyMedium, color = c.textSecondary,
    )
    ReefButton("Run time-lapse", { onStart(spec) }, Modifier.fillMaxWidth(), kind = ButtonKind.CORAL, icon = Icons.Rounded.Schedule)
}

// ---------------------------------------------------------------- progress

@Composable
private fun LapseProgress(spec: TimelapseSpec, p: TimelapseProgress, onCancel: () -> Unit) {
    val c = ReefTheme.colors
    Text(spec.label.ifBlank { "Time-lapse" }, style = MaterialTheme.typography.titleMedium, color = c.text)
    Box(
        Modifier.fillMaxWidth().height(10.dp).clip(ReefShapes.pill).background(c.glassHi)
            .semantics { contentDescription = "Progress ${(p.fraction * 100).roundToInt()} percent" },
    ) {
        Box(Modifier.fillMaxWidth(p.fraction.coerceIn(0.01f, 1f)).height(10.dp).clip(ReefShapes.pill).background(c.primaryBright))
    }
    Text(
        "${hoursText(p.simHour)} of ${hoursText(p.totalHours)} · ${(p.fraction * 100).roundToInt()}%",
        style = MaterialTheme.typography.bodyMedium, color = c.textSecondary,
    )
    Text("Your live tank keeps running while this plays out on a copy.", style = MaterialTheme.typography.labelMedium, color = c.textTertiary)
    ReefButton("Stop", onCancel, Modifier.fillMaxWidth(), kind = ButtonKind.OUTLINED, icon = Icons.Rounded.Close)
}

// ---------------------------------------------------------------- results

internal fun Verdict.status(): Status = when (this) {
    Verdict.COPED -> Status.GOOD
    Verdict.STRESSED, Verdict.SICK -> Status.CAUTION
    Verdict.DIED -> Status.DANGER
}

@Composable
private fun LapseResults(done: TimelapseState.Done, onNew: () -> Unit) {
    val c = ReefTheme.colors
    val rec = done.recording
    val report = done.report
    val lastHour = rec.samples.lastOrNull()?.hour ?: 0.0
    var hour by remember(rec) { mutableStateOf(0.0) }
    var selected by remember(rec) { mutableStateOf(report.species.firstOrNull()?.speciesId) }

    Text(
        done.spec.label.ifBlank { "Time-lapse" } + " · " + hoursText(lastHour) + if (done.cancelled) " (stopped early)" else "",
        style = MaterialTheme.typography.titleMedium, color = c.text,
    )
    Text(report.headline().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)

    ChipRow {
        report.species.forEach { s ->
            ReefChip(s.common, selected = selected == s.speciesId, onClick = { selected = s.speciesId })
        }
    }
    val sp = report.species.firstOrNull { it.speciesId == selected }
    if (sp != null) {
        SpeciesChart(rec, sp, hour, Modifier.fillMaxWidth().height(150.dp))
        Slider(
            value = hour.toFloat(), onValueChange = { hour = it.toDouble() },
            valueRange = 0f..lastHour.toFloat().coerceAtLeast(0.01f),
            colors = SliderDefaults.colors(thumbColor = c.primaryBright, activeTrackColor = c.primary, inactiveTrackColor = c.glassHi),
            modifier = Modifier.semantics { contentDescription = "Time scrubber, ${hoursText(hour)}" },
        )
        val sample = rec.sampleAt(hour)
        if (sample != null) {
            Text("At ${hoursText(sample.hour)}", style = MaterialTheme.typography.titleMedium, color = c.text)
            WaterStrip(sample)
            sample.organisms.filter { it.speciesId == sp.speciesId }.forEach { OrganismLine(it) }
        }
    }

    SubHeader("Report")
    report.species.forEach { s -> SpeciesReportCard(s, selected = s.speciesId == selected, onClick = { selected = s.speciesId }) }
    ReefButton("New time-lapse", onNew, Modifier.fillMaxWidth(), kind = ButtonKind.TONAL, icon = Icons.Rounded.Replay)
}

/** Worst health (line) and highest stress (line) across the individuals of [sp], with a cursor at [hour]. */
@Composable
private fun SpeciesChart(rec: TimelapseRecording, sp: SpeciesReport, hour: Double, modifier: Modifier = Modifier) {
    val c = ReefTheme.colors
    val ids = sp.individuals.map { it.id }.toSet()
    val health = rec.samples.map { s -> s.hour to (s.organisms.filter { it.id in ids }.minOfOrNull { it.health } ?: 0f) }
    val stress = rec.samples.map { s -> s.hour to (s.organisms.filter { it.id in ids }.maxOfOrNull { it.stress } ?: 0f) }
    val deaths = sp.individuals.mapNotNull { it.diedAtHour }
    val end = (rec.samples.lastOrNull()?.hour ?: 1.0).coerceAtLeast(0.01)
    Column(modifier.semantics { contentDescription = "${sp.common} health and stress over time" }) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            LegendDot(c.primaryBright, "Health")
            LegendDot(c.coral, "Stress")
            if (deaths.isNotEmpty()) LegendDot(c.danger, "Death")
        }
        Canvas(Modifier.fillMaxWidth().weight(1f).padding(top = 6.dp)) {
            val w = size.width
            val h = size.height
            drawRect(c.glassHi.copy(alpha = 0.5f), Offset.Zero, Size(w, h))
            val stressLevel = 0.35f
            drawLine(c.caution.copy(alpha = 0.5f), Offset(0f, h * (1 - stressLevel)), Offset(w, h * (1 - stressLevel)), strokeWidth = 1.dp.toPx())
            fun pts(s: List<Pair<Double, Float>>) = s.map { (t, v) -> Offset((t / end * w).toFloat(), h - v.coerceIn(0f, 1f) * (h - 4.dp.toPx()) - 2.dp.toPx()) }
            drawPath(smoothPath(pts(stress)), c.coral, style = Stroke(2.dp.toPx()))
            drawPath(smoothPath(pts(health)), c.primaryBright, style = Stroke(2.dp.toPx()))
            deaths.forEach { d -> drawCircle(c.danger, 4.dp.toPx(), Offset((d / end * w).toFloat(), h - 6.dp.toPx())) }
            val cx = (hour / end * w).toFloat()
            drawLine(c.text.copy(alpha = 0.8f), Offset(cx, 0f), Offset(cx, h), strokeWidth = 1.5.dp.toPx())
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(8.dp).background(color, ReefShapes.pill))
        Text(label, style = MaterialTheme.typography.labelSmall, color = ReefTheme.colors.textSecondary)
    }
}

@Composable
private fun WaterStrip(sample: TimelapseSample) {
    val readouts = waterReadouts(sample.water, sample.lightIntensity * 100.0, 1.0)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        readouts.forEach { r ->
            GlassCard(shape = ReefShapes.overlay, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp)) {
                Column {
                    Text(r.label, style = MaterialTheme.typography.labelSmall, color = ReefTheme.colors.textSecondary)
                    Text(r.valueText, style = MaterialTheme.typography.titleMedium, color = statusColor(r.status))
                }
            }
        }
    }
}

@Composable
private fun statusColor(s: Status): Color = with(ReefTheme.colors) {
    when (s) { Status.GOOD -> goodText; Status.CAUTION -> cautionText; Status.DANGER -> dangerText; Status.UNKNOWN -> text }
}

@Composable
private fun OrganismLine(o: OrganismSample) {
    val c = ReefTheme.colors
    val state = when {
        !o.alive -> "Dead"
        o.mood == Mood.SICK && o.symptoms.isNotEmpty() -> o.symptoms.take(2).joinToString(", ")
        o.behavior.isNotBlank() -> o.behavior
        else -> o.mood.label
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(o.nickname, style = MaterialTheme.typography.bodyMedium, color = c.text, modifier = Modifier.weight(1f))
        Text(
            state + " · health ${(o.health * 100).roundToInt()}% · stress ${(o.stress * 100).roundToInt()}%",
            style = MaterialTheme.typography.labelMedium, color = c.textSecondary,
        )
    }
}

@Composable
private fun SpeciesReportCard(s: SpeciesReport, selected: Boolean, onClick: () -> Unit) {
    val c = ReefTheme.colors
    GlassCard(Modifier.fillMaxWidth(), shape = ReefShapes.tile, tint = if (selected) c.primary else null, onClick = onClick) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.common, style = MaterialTheme.typography.titleMedium, color = c.text, modifier = Modifier.weight(1f))
                StatusPill(s.verdict.status(), label = s.verdict.label)
            }
            val onset = s.firstOnsetHour
            if (s.verdict == Verdict.COPED || onset == null) {
                Text("No trouble over the whole run.", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            } else {
                val cause = s.cause
                Text(
                    "First trouble at ${hoursText(onset)}" + (cause?.let { " · likely ${it.label}" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium, color = c.text,
                )
                val died = s.individuals.filter { it.verdict == Verdict.DIED }
                if (died.isNotEmpty()) {
                    val first = died.mapNotNull { it.diedAtHour }.minOrNull()
                    Text(
                        "${died.size} of ${s.individuals.size} died" + (first?.let { ", first at ${hoursText(it)}" } ?: ""),
                        style = MaterialTheme.typography.bodyMedium, color = c.dangerText,
                    )
                }
                s.individuals.filter { it.verdict != Verdict.DIED && it.verdict != Verdict.COPED }.firstOrNull()?.let { survivor ->
                    Text(recoveryText(survivor), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                }
            }
        }
    }
}

private fun recoveryText(o: OrganismReport): String =
    if (o.recovered) "${o.nickname} recovered by ${hoursText(o.recoveredAtHour ?: 0.0)}"
    else "${o.nickname} had not recovered by the end (health ${(o.finalHealth * 100).roundToInt()}%)"

// ---------------------------------------------------------------- previews

private fun previewSample(h: Double, sg: Double, health: Float, stress: Float, alive: Boolean = true) = TimelapseSample(
    h, 12 * 3600.0 + h * 3600, WaterState(sg = sg), 0.6f,
    listOf(
        OrganismSample("clown_f", "amphiprion_ocellaris", "Nemo", "Ocellaris clownfish", health, stress, 0.3f, alive,
            if (!alive) Mood.DEAD else if (stress > 0.5f) Mood.STRESSED else Mood.CALM, if (alive) "Hovering" else "Dead", "", emptyList(), null, 0f,
            mapOf("sg" to stress.toDouble()), emptyMap(), null),
    ),
)

private val previewSpec = lapseSpec("sg", 1.035, false, 0.0, 72.0, "Salinity 1.035")

private val previewRecording = TimelapseRecording(
    previewSpec,
    (0..24).map { i ->
        val h = i * 3.0
        val stress = (i / 12f).coerceAtMost(1f)
        previewSample(h, if (i == 0) 1.025 else 1.035, (1f - stress * 0.9f).coerceAtLeast(0.05f), stress, alive = i < 20)
    },
    emptyList(), true,
)

private val previewDone = TimelapseState.Done(previewSpec, previewRecording, TimelapseReport.build(previewRecording), false)

@Preview(name = "Time-lapse setup · Abyss", widthDp = 393, heightDp = 1100, showBackground = true)
@Composable
private fun LapseSetupDarkPreview() = ReefLiveTheme(darkTheme = true, reduceMotion = true) {
    Box(Modifier.background(ReefTheme.colors.sheet)) { TimelapseContent(TimelapseState.Idle, WaterState(), {}, {}, {}) }
}

@Preview(name = "Time-lapse setup · Lagoon", widthDp = 393, heightDp = 1100, showBackground = true)
@Composable
private fun LapseSetupLightPreview() = ReefLiveTheme(darkTheme = false, reduceMotion = true) {
    Box(Modifier.background(ReefTheme.colors.sheet)) { TimelapseContent(TimelapseState.Idle, WaterState(), {}, {}, {}) }
}

@Preview(name = "Time-lapse running · Abyss", widthDp = 393, heightDp = 360, showBackground = true)
@Composable
private fun LapseRunningDarkPreview() = ReefLiveTheme(darkTheme = true, reduceMotion = true) {
    Box(Modifier.background(ReefTheme.colors.sheet)) {
        TimelapseContent(TimelapseState.Running(previewSpec, TimelapseProgress(0.42f, 30.0, 72.0, 10)), WaterState(), {}, {}, {})
    }
}

@Preview(name = "Time-lapse results · Abyss", widthDp = 393, heightDp = 1300, showBackground = true)
@Composable
private fun LapseResultsDarkPreview() = ReefLiveTheme(darkTheme = true, reduceMotion = true) {
    Box(Modifier.background(ReefTheme.colors.sheet)) { TimelapseContent(previewDone, WaterState(), {}, {}, {}) }
}

@Preview(name = "Time-lapse results · Lagoon", widthDp = 393, heightDp = 1300, showBackground = true)
@Composable
private fun LapseResultsLightPreview() = ReefLiveTheme(darkTheme = false, reduceMotion = true) {
    Box(Modifier.background(ReefTheme.colors.sheet)) { TimelapseContent(previewDone, WaterState(), {}, {}, {}) }
}
