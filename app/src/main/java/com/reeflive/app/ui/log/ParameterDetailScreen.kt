package com.reeflive.app.ui.log

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.reeflive.app.ui.charts.ChartBands
import com.reeflive.app.ui.charts.ChartEvent
import com.reeflive.app.ui.charts.ChartPoint
import com.reeflive.app.ui.charts.OverlaySeries
import com.reeflive.app.ui.charts.TimeSeriesChart
import com.reeflive.app.ui.components.AnimatedNumber
import com.reeflive.app.ui.components.ButtonKind
import com.reeflive.app.ui.components.FactRow
import com.reeflive.app.ui.components.GlassIconButton
import com.reeflive.app.ui.components.ReefButton
import com.reeflive.app.ui.components.ReefChip
import com.reeflive.app.ui.components.ReefTextField
import com.reeflive.app.ui.components.ScreenHeader
import com.reeflive.app.ui.components.SectionHeader
import com.reeflive.app.ui.components.SegmentedPills
import com.reeflive.app.ui.components.StaleChip
import com.reeflive.app.ui.components.StatusPill
import com.reeflive.app.ui.components.TrendArrow
import com.reeflive.app.ui.components.pagePadding
import com.reeflive.app.ui.theme.GlassCard
import com.reeflive.app.ui.theme.ReefTheme
import com.reeflive.app.ui.theme.statusText
import com.reeflive.core.status.Status
import com.reeflive.core.status.Trend

val RANGE_LABELS = listOf("7D", "30D", "90D", "1Y", "All")

data class StatsUi(val min: String, val max: String, val mean: String, val consumption: String?, val inRange: String, val count: Int)

/** Targets editor fields as typed, in display units. */
data class TargetsForm(
    val idealLo: String = "",
    val idealHi: String = "",
    val accLo: String = "",
    val accHi: String = "",
    val dangerLo: String = "",
    val dangerHi: String = "",
)

data class HistoryRowUi(val sessionId: String, val time: String, val value: String, val status: Status, val kit: String?)

data class DetailUi(
    val loading: Boolean = true,
    val id: String = "",
    val name: String = "",
    val unit: String = "",
    val canCycleUnit: Boolean = false,
    val value: String? = null,
    val status: Status = Status.UNKNOWN,
    val trend: Trend = Trend.UNKNOWN,
    val delta: String? = null,
    val tests: Int = 0,
    val staleDays: Int? = null,
    val insight: String = "",
    val range: Int = 1,
    val windowStart: Long = 0L,
    val windowEnd: Long = 0L,
    val points: List<ChartPoint> = emptyList(),
    val bands: ChartBands? = null,
    val precision: Int = 1,
    val events: List<ChartEvent> = emptyList(),
    val overlay: OverlaySeries? = null,
    val overlayOptions: List<Pair<String, String>> = emptyList(),
    val overlayId: String? = null,
    val stats: StatsUi? = null,
    val targets: TargetsForm = TargetsForm(),
    val targetsEdited: Boolean = false,
    val targetErrors: List<String> = emptyList(),
    val history: List<HistoryRowUi> = emptyList(),
)

data class DetailActions(
    val onBack: () -> Unit = {},
    val onRange: (Int) -> Unit = {},
    val onCycleUnit: () -> Unit = {},
    val onOverlay: (String?) -> Unit = {},
    val onTargets: (TargetsForm) -> Unit = {},
    val onSaveTargets: () -> Unit = {},
    val onResetTargets: () -> Unit = {},
    val onOpenSession: (String) -> Unit = {},
    val onLog: () -> Unit = {},
)

/** Parameter detail (DESIGN §5.5). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ParameterDetailContent(state: DetailUi, actions: DetailActions, modifier: Modifier = Modifier) {
    val c = ReefTheme.colors
    var editing by remember { mutableStateOf(false) }
    LazyColumn(modifier.fillMaxSize(), contentPadding = pagePadding(top = 0.dp)) {
        item(key = "header") {
            ScreenHeader(state.name, onBack = actions.onBack, modifier = Modifier.padding(horizontal = 0.dp), actions = {
                GlassIconButton(Icons.Rounded.Add, "Log ${state.name}", actions.onLog, tint = c.coral)
            })
        }
        item(key = "value") {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Bottom) {
                AnimatedNumber(state.value ?: "—", ReefTheme.numbers.hero.copy(fontSize = ReefTheme.numbers.hero.fontSize * 1.2f), c.text, Modifier.alignByBaseline())
                Spacer(Modifier.width(6.dp))
                Text(state.unit, style = MaterialTheme.typography.titleMedium, color = c.textSecondary, modifier = Modifier.alignByBaseline())
                if (state.canCycleUnit) {
                    Spacer(Modifier.width(4.dp))
                    ReefChip("Unit", false, actions.onCycleUnit, leading = { Icon(Icons.Rounded.SwapVert, null, tint = c.textSecondary) })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill(state.status)
                if (state.staleDays != null) StaleChip(state.staleDays)
                TrendArrow(state.trend, c.statusText(state.status), size = 18.dp)
                if (state.delta != null) Text(state.delta, style = ReefTheme.numbers.label, color = c.textSecondary)
                Spacer(Modifier.weight(1f))
                Text("${state.tests} tests", style = MaterialTheme.typography.labelMedium, color = c.textTertiary)
            }
            if (state.insight.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(state.insight, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            }
            Spacer(Modifier.height(16.dp))
        }
        item(key = "chart") {
            GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 4.dp, end = 4.dp, top = 12.dp, bottom = 8.dp)) {
                SegmentedPills(RANGE_LABELS, state.range, actions.onRange, Modifier.fillMaxWidth().padding(horizontal = 8.dp))
                Spacer(Modifier.height(10.dp))
                TimeSeriesChart(
                    points = state.points,
                    bands = state.bands,
                    windowStart = state.windowStart,
                    windowEnd = state.windowEnd,
                    precision = state.precision,
                    unit = state.unit,
                    events = state.events,
                    overlay = state.overlay,
                    emptyText = "Log ${state.name.lowercase()} to see the trend",
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Long-press to read values · pinch to zoom · double-tap to reset",
                    style = MaterialTheme.typography.labelSmall, color = c.textTertiary, modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        }
        if (state.overlayOptions.isNotEmpty()) {
            item(key = "overlay") {
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Compare with", style = MaterialTheme.typography.labelMedium, color = c.textSecondary, modifier = Modifier.align(Alignment.CenterVertically))
                    state.overlayOptions.forEach { (id, label) ->
                        ReefChip(label, selected = state.overlayId == id, onClick = { actions.onOverlay(if (state.overlayId == id) null else id) }, accent = c.anemone)
                    }
                }
            }
        }
        state.stats?.let { s ->
            item(key = "stats") {
                SectionHeader("Stats")
                GlassCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth()) {
                        Stat("Min", s.min, Modifier.weight(1f))
                        Stat("Max", s.max, Modifier.weight(1f))
                        Stat("Mean", s.mean, Modifier.weight(1f))
                        Stat("In range", s.inRange, Modifier.weight(1f))
                    }
                    if (s.consumption != null) {
                        Spacer(Modifier.height(8.dp))
                        FactRow("Consumption", s.consumption)
                    }
                }
            }
        }
        item(key = "targets") {
            SectionHeader("Targets", action = {
                if (!editing) ReefButton("Edit", { editing = true }, kind = ButtonKind.TONAL, compact = true)
            })
            GlassCard(Modifier.fillMaxWidth()) {
                val t = state.targets
                if (!editing) {
                    FactRow("Ideal", "${t.idealLo} – ${t.idealHi} ${state.unit}".trim(), valueColor = c.goodText)
                    FactRow("Acceptable", "${t.accLo} – ${t.accHi} ${state.unit}".trim(), valueColor = c.cautionText)
                    FactRow("Danger", listOfNotNull(t.dangerLo.ifBlank { null }?.let { "< $it" }, t.dangerHi.ifBlank { null }?.let { "> $it" }).joinToString("  ·  ").ifEmpty { "—" }, valueColor = c.dangerText)
                } else {
                    RangeFields("Ideal", t.idealLo, t.idealHi, { actions.onTargets(t.copy(idealLo = it)) }, { actions.onTargets(t.copy(idealHi = it)) }, state.unit)
                    RangeFields("Acceptable", t.accLo, t.accHi, { actions.onTargets(t.copy(accLo = it)) }, { actions.onTargets(t.copy(accHi = it)) }, state.unit)
                    RangeFields("Danger below / above", t.dangerLo, t.dangerHi, { actions.onTargets(t.copy(dangerLo = it)) }, { actions.onTargets(t.copy(dangerHi = it)) }, state.unit)
                    state.targetErrors.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = c.dangerText) }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReefButton("Save", {
                            actions.onSaveTargets()
                            if (state.targetErrors.isEmpty()) editing = false
                        }, enabled = state.targetErrors.isEmpty(), compact = true)
                        ReefButton("Defaults", { actions.onResetTargets(); editing = false }, kind = ButtonKind.OUTLINED, compact = true)
                        ReefButton("Cancel", { editing = false }, kind = ButtonKind.OUTLINED, compact = true)
                    }
                }
            }
        }
        if (state.history.isNotEmpty()) {
            item(key = "historyTitle") { SectionHeader("History") }
            items(state.history, key = { it.sessionId + it.time }) { h ->
                GlassCard(Modifier.fillMaxWidth().padding(bottom = 8.dp), onClick = { actions.onOpenSession(h.sessionId) }, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(h.time, style = MaterialTheme.typography.bodyMedium, color = c.text)
                            if (h.kit != null) Text(h.kit, style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
                        }
                        Text(h.value, style = ReefTheme.numbers.body, color = c.statusText(h.status))
                        Spacer(Modifier.width(8.dp))
                        StatusPill(h.status)
                    }
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    val c = ReefTheme.colors
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
        Text(value, style = ReefTheme.numbers.body, color = c.text)
    }
}

@Composable
private fun RangeFields(label: String, lo: String, hi: String, onLo: (String) -> Unit, onHi: (String) -> Unit, unit: String) {
    val c = ReefTheme.colors
    Text(label, style = MaterialTheme.typography.labelMedium, color = c.textSecondary, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ReefTextField(lo, onLo, Modifier.weight(1f), placeholder = "low", numeric = true, suffix = { Text(unit, style = MaterialTheme.typography.labelMedium, color = c.textTertiary) })
        ReefTextField(hi, onHi, Modifier.weight(1f), placeholder = "high", numeric = true, suffix = { Text(unit, style = MaterialTheme.typography.labelMedium, color = c.textTertiary) })
    }
}
