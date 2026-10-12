package com.reeflive.app.ui.extras

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reeflive.app.appGraph
import com.reeflive.app.data.repo.ParamEval
import com.reeflive.app.data.repo.TaskWithLast
import com.reeflive.app.ui.common.TimeText
import com.reeflive.app.ui.components.EmptyState
import com.reeflive.app.ui.components.SectionHeader
import com.reeflive.app.ui.theme.GlassCard
import com.reeflive.app.ui.theme.ReefTheme
import com.reeflive.core.care.TaskSchedule
import com.reeflive.core.status.HealthScore
import com.reeflive.core.status.InsightEngine
import com.reeflive.core.status.ReefHealth
import com.reeflive.core.util.Fmt
import kotlinx.coroutines.flow.combine
import java.time.ZoneId

data class StabilityRow(val name: String, val score: Int, val detail: String)

data class InsightsUi(
    val loading: Boolean = true,
    val health: HealthScore? = null,
    val stability: List<StabilityRow> = emptyList(),
    val bestWeek: String? = null,
    val alkSwing: String? = null,
    val alkSwingWarn: Boolean = false,
    val ratio: String? = null,
    val ratioHistory: List<String> = emptyList(),
    val mgCa: String? = null,
)

/** Health score, 30-day stability, best week and nutrient ratios (pure; used by previews). */
object InsightsMapper {
    private const val DAY = 86_400_000L

    fun build(evals: List<ParamEval>, tasks: List<TaskWithLast>, now: Long, zone: ZoneId): InsightsUi {
        val from = now - 30 * DAY
        val stab = evals.mapNotNull { e -> ReefHealth.stability(e.def, e.history, from, now)?.let { e to it } }
        val lastTest = evals.mapNotNull { it.latest?.epochMs }.maxOrNull()
        val overdue = tasks.count { it.task.active && TaskSchedule.nextDue(it.task, it.lastDoneAt, zone) < now }
        val health = ReefHealth.score(evals.map { it.eval.status }, stab.map { it.second }, lastTest?.let { (now - it).toDouble() / DAY }, overdue)
        val rows = stab.sortedBy { it.second.score }.map { (e, s) ->
            val swing = s.maxSwingPerDay?.let { " · biggest jump ${Fmt.num(it, e.def.precision)} ${e.def.baseUnit.symbol}/day" }.orEmpty()
            StabilityRow(e.def.name, s.score, "${s.samples} tests$swing")
        }
        val best = ReefHealth.mostStableWeek(evals.map { it.def }, evals.associate { it.id to it.history }, now)
            ?.let { "${TimeText.shortDate(it.startMs)} – ${TimeText.shortDate(it.endMs)} scored ${it.score} / 100" }
        val alk = evals.firstOrNull { it.id == "alk" }
        val alkSwing = alk?.let { ReefHealth.maxSwingPerDay(it.history, now - 7 * DAY, now) }
        val alkLimit = alk?.def?.maxChangePerDay ?: 1.0
        val no3 = evals.firstOrNull { it.id == "no3" }?.history.orEmpty()
        val po4 = evals.firstOrNull { it.id == "po4" }?.history.orEmpty()
        val ratios = ReefHealth.ratioSeries(no3, po4)
        val latest = { id: String -> evals.firstOrNull { it.id == id }?.latest?.value }
        return InsightsUi(
            loading = false,
            health = health,
            stability = rows,
            bestWeek = best,
            alkSwing = alkSwing?.let { "Biggest alkalinity change this week: ${Fmt.num(it, 2)} dKH/day (keep under ${Fmt.num(alkLimit, 1)})." },
            alkSwingWarn = alkSwing != null && alkSwing > alkLimit,
            ratio = InsightEngine.ratioInsight(ratios.lastOrNull()?.second),
            ratioHistory = ratios.takeLast(6).reversed().map { (t, r) -> "${TimeText.shortDate(t)}  ${r.toInt()}:1" },
            mgCa = InsightEngine.mgCaRatio(latest("mg"), latest("ca"))?.let { "Mg:Ca is ${Fmt.num(it, 2)} (target 3.0–3.4)." },
        )
    }
}

@Composable
fun InsightsContent(state: InsightsUi, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val c = ReefTheme.colors
    ExtrasPage("Insights", "Tank health and stability", onBack, modifier) {
        val h = state.health
        if (h == null) {
            if (!state.loading) EmptyState("No data yet", "Log a few tests to see your tank health score.")
            return@ExtrasPage
        }
        GlassCard(Modifier.fillMaxWidth(), tint = c.primary) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScoreRing(h.score, h.grade)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    h.parts.forEach { p ->
                        Column {
                            Row {
                                Text(p.label, style = MaterialTheme.typography.labelLarge, color = c.text, modifier = Modifier.weight(1f))
                                Text("${p.points}/${p.max}", style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
                            }
                            GlassBar(p.points.toFloat() / p.max, height = 5.dp)
                            Text(p.detail, style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
                        }
                    }
                }
            }
        }
        if (state.alkSwing != null || state.ratio != null || state.mgCa != null || state.bestWeek != null) {
            SectionHeader("What stands out")
            GlassCard(Modifier.fillMaxWidth()) {
                state.bestWeek?.let { InfoRow("Most stable week", it) }
                state.alkSwing?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = if (state.alkSwingWarn) c.cautionText else c.textSecondary) }
                state.ratio?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                    if (state.ratioHistory.isNotEmpty()) {
                        Text(state.ratioHistory.joinToString("   "), style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
                    }
                }
                state.mgCa?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                }
            }
        }
        SectionHeader("Stability · last 30 days")
        if (state.stability.isEmpty()) {
            Text("Needs at least 3 tests of a parameter in the last 30 days.", style = MaterialTheme.typography.bodySmall, color = c.textTertiary)
        }
        if (state.stability.isNotEmpty()) GlassCard(Modifier.fillMaxWidth()) {
            state.stability.forEach { r ->
                InfoRow(r.name, r.detail) { StatusPill("${r.score}", scoreColor(r.score)) }
                GlassBar(r.score / 100f, color = scoreColor(r.score), height = 4.dp)
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
fun InsightsRoute(onBack: () -> Unit) {
    val g = LocalContext.current.appGraph
    val flow = remember {
        combine(g.measurements.latestEvaluations(), g.tasks.tasks) { e, t -> InsightsMapper.build(e, t, g.clock.now(), ZoneId.systemDefault()) }
    }
    val state by flow.collectAsStateWithLifecycle(InsightsUi())
    InsightsContent(state, onBack)
}
