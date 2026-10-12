package com.reeflive.app.ui.live

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.reeflive.app.ui.components.ButtonKind
import com.reeflive.app.ui.components.ReefButton
import com.reeflive.app.ui.components.ReefChip
import com.reeflive.app.ui.components.ReefSheet
import com.reeflive.app.ui.components.StatusIcon
import com.reeflive.app.ui.theme.GlassCard
import com.reeflive.app.ui.theme.ReefLiveTheme
import com.reeflive.app.ui.theme.ReefShapes
import com.reeflive.app.ui.theme.ReefTheme
import com.reeflive.app.ui.theme.statusText
import com.reeflive.core.status.Status
import java.util.Locale

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
/** Sandbox bottom sheet: wraps [SandboxPanelContent] in a [ReefSheet]. */
@Composable
fun SandboxPanel(
    state: SandboxState,
    actions: SandboxActions,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ReefSheet(onDismiss = onDismiss, modifier = modifier) {
        SandboxPanelContent(state, actions)
    }
}

/**
 * Layer 1: big sliders + feed / tap; scenarios and reset. Layer 2 ("Advanced", collapsible): chemistry,
 * algae seed, each pump, light spectrum.
 */
@Composable
fun SandboxPanelContent(state: SandboxState, actions: SandboxActions, modifier: Modifier = Modifier) {
    val c = ReefTheme.colors
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Sandbox", style = MaterialTheme.typography.headlineSmall, color = c.text)

        ScenarioRow(actions.onScenario)

        SandboxSpecs.basic.forEach { spec ->
            BandSlider(spec, state.valueOf(spec)) { actions.onSetParam(spec.id, it) }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ReefButton("Feed", actions.onFeed, Modifier.weight(1f), kind = ButtonKind.CORAL, icon = Icons.Rounded.Restaurant)
            ReefButton("Tap glass", actions.onTapGlass, Modifier.weight(1f), kind = ButtonKind.TONAL, icon = Icons.Rounded.TouchApp)
        }

        AdvancedHeader(state.advancedExpanded) { actions.onToggleAdvanced(!state.advancedExpanded) }
        if (state.advancedExpanded) {
            SandboxSpecs.advanced.forEach { spec ->
                BandSlider(spec, state.valueOf(spec)) { actions.onSetParam(spec.id, it) }
            }
            if (state.pumps.isNotEmpty()) {
                SubHeader("Pumps")
                state.pumps.forEach { p ->
                    PlainSlider(p.label, p.strength.toDouble(), 0.0, 2.0, "x", 1) { actions.onSetPump(p.id, it.toFloat()) }
                }
            }
            if (state.spectrum.isNotEmpty()) {
                SubHeader("Light spectrum")
                state.spectrum.forEach { ch ->
                    PlainSlider(ch.label, ch.value * 100.0, 0.0, 100.0, "%", 0) { actions.onSetSpectrum(ch.id, (it / 100.0).toFloat()) }
                }
            }
        }

        ReefButton("Reset to my tank", actions.onReset, Modifier.fillMaxWidth(), kind = ButtonKind.OUTLINED, icon = Icons.Rounded.RestartAlt)
    }
}

@Composable
private fun SubHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = ReefTheme.colors.textSecondary)
}

@Composable
private fun ScenarioRow(onScenario: (SandboxScenario) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SubHeader("Scenarios")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SandboxScenario.values().forEach { s ->
                ReefChip(text = s.label, selected = false, onClick = { onScenario(s) })
            }
        }
    }
}

@Composable
private fun AdvancedHeader(expanded: Boolean, onToggle: () -> Unit) {
    val c = ReefTheme.colors
    GlassCard(Modifier.fillMaxWidth(), shape = ReefShapes.tile, onClick = onToggle) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Advanced", style = MaterialTheme.typography.titleMedium, color = c.text)
                Text(
                    "Alk, Ca, Mg, pH, ammonia, O2, algae, pumps, spectrum",
                    style = MaterialTheme.typography.labelSmall, color = c.textSecondary,
                )
            }
            Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = if (expanded) "Collapse" else "Expand", tint = c.textSecondary)
        }
    }
}

private fun fmt(v: Double, decimals: Int, unit: String): String =
    String.format(Locale.US, "%.${decimals}f", v) + if (unit.isEmpty()) "" else " $unit"

/** Slider with a safe / stress / lethal band strip behind the track and a status icon next to the value. */
@Composable
fun BandSlider(spec: SandboxSliderSpec, value: Double, modifier: Modifier = Modifier, onChange: (Double) -> Unit) {
    val c = ReefTheme.colors
    val status = spec.bands?.status(value)
    Column(modifier.fillMaxWidth().semantics { contentDescription = "${spec.label} ${fmt(value, spec.decimals, spec.unit)}" }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(spec.label, style = MaterialTheme.typography.titleMedium, color = c.text, modifier = Modifier.weight(1f))
            if (status != null) {
                StatusIcon(status, size = 14.dp)
                Spacer(Modifier.padding(start = 4.dp))
            }
            Text(
                fmt(value, spec.decimals, spec.unit),
                style = MaterialTheme.typography.titleMedium,
                color = if (status != null) c.statusText(status) else c.text,
            )
        }
        Box(Modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.Center) {
            if (spec.bands != null) BandStrip(spec, Modifier.fillMaxWidth().padding(horizontal = 10.dp))
            Slider(
                value = value.toFloat(),
                onValueChange = { onChange(it.toDouble()) },
                valueRange = spec.min.toFloat()..spec.max.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = c.primaryBright,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = if (spec.bands == null) c.glassHi else Color.Transparent,
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent,
                ),
            )
        }
    }
}

/** Slider without bands (pumps, spectrum). */
@Composable
private fun PlainSlider(label: String, value: Double, min: Double, max: Double, unit: String, decimals: Int, onChange: (Double) -> Unit) {
    BandSlider(SandboxSliderSpec(label, label, unit, min, max, decimals, null), value, onChange = onChange)
}

@Composable
private fun BandStrip(spec: SandboxSliderSpec, modifier: Modifier) {
    val c = ReefTheme.colors
    val b = spec.bands ?: return
    val good = c.good.copy(alpha = 0.55f)
    val warn = c.caution.copy(alpha = 0.45f)
    val bad = c.danger.copy(alpha = 0.55f)
    Canvas(modifier.height(8.dp).clip(ReefShapes.pill)) {
        val span = (spec.max - spec.min).coerceAtLeast(1e-9)
        fun x(v: Double) = (((v - spec.min) / span).coerceIn(0.0, 1.0) * size.width).toFloat()
        val lethalLo = b.lethalLo ?: spec.min
        val lethalHi = b.lethalHi ?: spec.max
        fun seg(from: Double, to: Double, color: Color) {
            val x0 = x(from)
            val x1 = x(to)
            if (x1 > x0) drawRect(color, Offset(x0, 0f), Size(x1 - x0, size.height))
        }
        seg(spec.min, lethalLo, bad)
        seg(lethalLo, b.safeLo, warn)
        seg(b.safeLo, b.safeHi, good)
        seg(b.safeHi, lethalHi, warn)
        seg(lethalHi, spec.max, bad)
    }
}

// ---------------------------------------------------------------- previews

private val previewSandbox = SandboxState(
    values = mapOf(
        "temp" to 29.5, "sal" to 1.025, "no3" to 18.0, "po4" to 0.04,
        "light" to 60.0, "photoperiod" to 10.0, "flow" to 1.0,
        "alk" to 8.5, "ca" to 420.0, "mg" to 1300.0, "ph" to 8.2, "nh3" to 0.0, "do" to 6.5, "algae" to 5.0,
    ),
    pumps = listOf(PumpUi("p1", "Left powerhead", 1f), PumpUi("p2", "Return pump", 0.8f)),
    spectrum = listOf(SpectrumChannelUi("blue", "Royal blue", 0.7f), SpectrumChannelUi("white", "White", 0.4f), SpectrumChannelUi("violet", "Violet", 0.2f)),
    advancedExpanded = true,
)

@Preview(name = "Sandbox · Abyss", widthDp = 393, heightDp = 1400, showBackground = true)
@Composable
private fun SandboxDarkPreview() = ReefLiveTheme(darkTheme = true, reduceMotion = true) {
    Box(Modifier.background(ReefTheme.colors.sheet)) { SandboxPanelContent(previewSandbox, SandboxActions()) }
}

@Preview(name = "Sandbox · Lagoon", widthDp = 393, heightDp = 900, showBackground = true)
@Composable
private fun SandboxLightPreview() = ReefLiveTheme(darkTheme = false, reduceMotion = true) {
    Box(Modifier.background(ReefTheme.colors.sheet)) { SandboxPanelContent(previewSandbox.copy(advancedExpanded = false), SandboxActions()) }
}
