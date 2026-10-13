package com.reeflive.app.ui.live

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.reeflive.app.ui.components.GlassIconButton
import com.reeflive.app.ui.components.ReefChip
import com.reeflive.app.ui.theme.GlassCard
import com.reeflive.app.ui.theme.ReefShapes
import com.reeflive.app.ui.theme.ReefTheme
import com.reeflive.core.sim.BacteriaAction
import com.reeflive.core.sim.BacteriaGuildState
import com.reeflive.core.sim.BacteriaState
import com.reeflive.core.sim.Evidence
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/**
 * The bacteria section beside the microscope (research 18): a "Bacteria" chip over the microscope's header opens a
 * panel with each guild's level, trend and what it is doing to the tank. [onAction] (sandbox only) adds the one-tap
 * actions: bottled nitrifiers, carbon dosing, antibiotic, UV, stirring the sand, probiotic.
 */
@Composable
fun BacteriaOverlay(state: BacteriaState, modifier: Modifier = Modifier, onAction: ((BacteriaAction) -> Unit)? = null) {
    var open by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = open) { open = false }
    Box(modifier) {
        if (open) {
            BacteriaPanel(state, onClose = { open = false }, modifier = Modifier.fillMaxSize(), onAction = onAction)
        } else {
            // sits left of the microscope's close button
            ReefChip(
                text = if (state.cloudy || state.h2sMgL > 0.005 || state.pathogenPressure > 0.3) "Bacteria !" else "Bacteria",
                selected = false,
                onClick = { open = true },
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 72.dp),
                accent = ReefTheme.colors.coral,
            )
        }
    }
}

/** Full panel: guild rows, the tank-level readouts and (sandbox) the actions. */
@Composable
fun BacteriaPanel(state: BacteriaState, onClose: () -> Unit, modifier: Modifier = Modifier, onAction: ((BacteriaAction) -> Unit)? = null) {
    val c = ReefTheme.colors
    Column(
        modifier
            .background(c.bg)
            // a hit target, so taps on the panel never reach the microscope underneath
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Bacteria", style = MaterialTheme.typography.titleMedium, color = c.text)
                Text(
                    "${sci(state.freeCellsPerMl)} cells/mL in the water · pathogens ${sci(state.vibrioPerMl)}/mL",
                    style = MaterialTheme.typography.labelMedium, color = c.textSecondary,
                )
            }
            GlassIconButton(Icons.Rounded.Close, "Close bacteria", onClose)
        }
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Alerts(state)
            for (g in state.guilds) GuildRow(g)
            Text(
                "Most numbers are design estimates from research 18 (not measured in a tank); each row says how well it is backed.",
                style = MaterialTheme.typography.labelSmall, color = c.textTertiary,
            )
        }
        onAction?.let { act ->
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BacteriaAction.entries.forEach { a -> ReefChip(text = a.label, selected = isOn(a, state), onClick = { act(a) }, accent = c.coral) }
            }
        }
    }
}

private fun isOn(a: BacteriaAction, s: BacteriaState): Boolean = when (a) {
    BacteriaAction.CARBON_DOSE_START -> s.carbonDoseMgCPerLDay > 0
    BacteriaAction.UV_ON -> s.uvOn
    BacteriaAction.UV_OFF -> !s.uvOn
    BacteriaAction.ANTIBIOTIC -> s.antibioticMgL > 0.05
    else -> false
}

@Composable
private fun Alerts(s: BacteriaState) {
    val c = ReefTheme.colors
    val info = MaterialTheme.typography.labelMedium
    if (s.h2sMgL > 0.005) Text("Hydrogen sulfide ${String.format(Locale.US, "%.3f", s.h2sMgL)} mg/L: toxic, aerate and change water", style = info, color = c.dangerText)
    if (s.cloudy) Text("Bacterial bloom: the water is milky and oxygen is being used up", style = info, color = c.cautionText)
    if (s.carbonDoseMgCPerLDay > 0) Text(String.format(Locale.US, "Carbon dosing %.1f mg C/L a day", s.carbonDoseMgCPerLDay), style = info, color = c.textSecondary)
    if (s.antibioticMgL > 0.05) Text(String.format(Locale.US, "Antibiotic %.1f mg/L in the water", s.antibioticMgL), style = info, color = c.cautionText)
    if (s.uvOn) Text("UV sterilizer on: free-floating bacteria are being killed", style = info, color = c.textSecondary)
    Text(
        String.format(Locale.US, "Sand bed %.1f cm, %.1f cm of it without oxygen", s.sandDepthCm, s.anoxicDepthCm),
        style = info, color = c.textSecondary,
    )
}

@Composable
private fun GuildRow(g: BacteriaGuildState) {
    val c = ReefTheme.colors
    val tone = when (g.guild.good) {
        true -> c.goodText
        false -> c.cautionText
        null -> c.primary
    }
    val trend = g.trendPerDay * 100.0
    GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(8.dp).clip(ReefShapes.pill).background(tone))
            Text(g.guild.label, style = MaterialTheme.typography.labelLarge, color = c.text, modifier = Modifier.weight(1f))
            Text(
                if (abs(trend) < 0.5) "steady" else String.format(Locale.US, "%+.0f%%/d", trend),
                style = MaterialTheme.typography.labelMedium,
                color = trendColor(trend, g.guild.good),
            )
        }
        Box(Modifier.fillMaxWidth().padding(vertical = 6.dp).height(6.dp).clip(ReefShapes.pill).background(c.glassBorder)) {
            Box(Modifier.fillMaxWidth(g.level.toFloat().coerceIn(0f, 1f)).fillMaxHeight().clip(ReefShapes.pill).background(tone))
        }
        Text(g.amount, style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
        Text(g.effect, style = MaterialTheme.typography.bodySmall, color = c.text)
        Text(
            "${evidenceLabel(g.guild.evidence)} · ${g.guild.source}",
            style = MaterialTheme.typography.labelSmall, color = c.textTertiary,
        )
    }
}

@Composable
private fun trendColor(trend: Double, good: Boolean?): Color {
    val c = ReefTheme.colors
    if (abs(trend) < 0.5 || good == null) return c.textTertiary
    return if ((trend > 0) == good) c.goodText else c.cautionText
}

private fun evidenceLabel(e: Evidence): String = when (e) {
    Evidence.SOURCED -> "Sourced"
    Evidence.ANALOGY -> "By analogy"
    Evidence.DESIGN_ESTIMATE -> "Design estimate"
}

private fun sci(x: Double): String {
    if (x <= 0) return "0"
    val e = floor(log10(x)).toInt()
    val m = x / 10.0.pow(e)
    return String.format(Locale.US, "%.1f×10^%d", m, e)
}
