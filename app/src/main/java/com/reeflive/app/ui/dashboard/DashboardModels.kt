package com.reeflive.app.ui.dashboard

import com.reeflive.app.data.repo.AppSettings
import com.reeflive.app.data.repo.ParamEval
import com.reeflive.app.data.repo.TaskWithLast
import com.reeflive.app.ui.common.EventUi
import com.reeflive.app.ui.common.HeroUi
import com.reeflive.app.ui.common.ParamView
import com.reeflive.app.ui.common.TaskUi
import com.reeflive.app.ui.common.TileUi
import com.reeflive.app.ui.common.TimeText
import com.reeflive.app.ui.common.UiMappers
import com.reeflive.core.care.DueState
import com.reeflive.core.care.TaskSchedule
import com.reeflive.core.model.Alert
import com.reeflive.core.model.AlertKind
import com.reeflive.core.model.EventType
import com.reeflive.core.model.LightCurve
import com.reeflive.core.model.LightSchedule
import com.reeflive.core.model.Livestock
import com.reeflive.core.model.ReefEvent
import com.reeflive.core.model.Tank
import com.reeflive.core.params.ParameterTable
import com.reeflive.core.species.SpeciesDb
import com.reeflive.core.species.SpeciesKind
import com.reeflive.core.status.InsightEngine
import com.reeflive.core.status.Status
import com.reeflive.core.status.StatusEngine
import com.reeflive.core.util.Fmt
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

data class AlertUi(val id: String, val paramId: String, val title: String, val message: String, val status: Status)

data class PeekUi(val caption: String, val lightArgb: Long)

data class DashboardUi(
    val loading: Boolean = true,
    val tankName: String = "",
    val date: String = "",
    val lastTest: String = "",
    val summaryStatus: Status = Status.UNKNOWN,
    val summary: String = "",
    val alerts: List<AlertUi> = emptyList(),
    val hero: HeroUi? = null,
    val tiles: List<TileUi> = emptyList(),
    val due: List<TaskUi> = emptyList(),
    val peek: PeekUi? = null,
    val events: List<EventUi> = emptyList(),
)

/** Pure mapping from repository data to [DashboardUi] (DESIGN §5.2). */
object DashboardMapper {
    private val dateFmt = DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.US)
    private val clockFmt = DateTimeFormatter.ofPattern("HH:mm", Locale.US)

    fun build(
        now: Long,
        zone: ZoneId,
        tank: Tank?,
        evals: List<ParamEval>,
        settings: AppSettings,
        tasks: List<TaskWithLast>,
        events: List<ReefEvent>,
        alerts: List<Alert>,
        alive: List<Livestock>,
        light: LightSchedule?,
        species: SpeciesDb?,
    ): DashboardUi {
        val views = evals.associate { it.id to ParamView(it.def, settings.displayUnitFor(it.id, it.def.baseUnit)) }
        val tiles = orderTiles(evals, settings.tileOrder).map { pe -> UiMappers.tile(views.getValue(pe.id), pe.eval, pe.history, now) }

        val logged = evals.filter { it.eval.status != Status.UNKNOWN }
        val dangers = logged.count { it.eval.status == Status.DANGER }
        val cautions = logged.count { it.eval.status == Status.CAUTION }
        val (summaryStatus, summary) = when {
            logged.isEmpty() -> Status.UNKNOWN to "No tests yet"
            dangers > 0 -> Status.DANGER to "$dangers danger" + if (dangers > 1) "s" else ""
            cautions > 0 -> Status.CAUTION to "$cautions caution" + if (cautions > 1) "s" else ""
            else -> Status.GOOD to "All clear"
        }
        val lastMs = logged.mapNotNull { it.latest?.epochMs }.maxOrNull()

        val hero = pickHero(evals)?.let { pe ->
            val view = views.getValue(pe.id)
            val from = now - 30 * UiMappers.DAY
            val tile = UiMappers.tile(view, pe.eval, pe.history, now, sparkDays = 30)
            val delta = pe.eval.delta?.let { d ->
                val prev = pe.eval.previous!!
                "Δ ${view.formatDelta(d, prev.value)} since ${TimeText.dayLabel(prev.epochMs, now).lowercase().replaceFirstChar { it.uppercase() }}"
            }
            val resets = events.filter { it.type == EventType.WATER_CHANGE }.map { it.at }
            HeroUi(
                tile = tile,
                delta = delta,
                insight = InsightEngine.insight(pe.def, pe.history, now, tank?.systemGallons ?: 60.0, resets),
                points = UiMappers.chartPoints(view, pe.history.filter { it.epochMs >= from - 3 * UiMappers.DAY }),
                bands = UiMappers.bands(view),
                events = UiMappers.events(events.filter { it.at >= from }),
                precision = view.precision,
                windowStart = from,
                windowEnd = now,
            )
        }

        val due = tasks.filter { it.task.active }
            .map { TaskSchedule.due(it.task, it.lastDoneAt, now, zone) }
            .filter { it.state == DueState.OVERDUE || it.state == DueState.TODAY }
            .sortedBy { it.dueAt }
            .take(3)
            .map { UiMappers.task(it, now) }

        val minute = Instant.ofEpochMilli(now).atZone(zone).let { it.hour * 60.0 + it.minute }
        val peek = light?.let { l ->
            val ch = l.channelsAt(minute)
            val fish = alive.filter { species?.get(it.speciesId)?.kind == SpeciesKind.FISH }.sumOf { it.quantity }
            val pct = Fmt.pct(LightCurve.intensity(ch).coerceIn(0.0, 1.0))
            PeekUi(
                "$fish fish · lights $pct · ${clockFmt.format(Instant.ofEpochMilli(now).atZone(zone))}",
                LightCurve.swatchArgb(ch),
            )
        }

        val alertUis = alerts.distinctBy { it.paramId to it.kind }.take(6).map { a ->
            AlertUi(
                a.id, a.paramId, ParameterTable.shortName(a.paramId),
                a.message.removePrefix("Danger: ").removePrefix("Watch: "),
                when (a.kind) {
                    AlertKind.DANGER, AlertKind.FAST_CHANGE -> Status.DANGER
                    AlertKind.CAUTION, AlertKind.STALE -> Status.CAUTION
                },
            )
        }

        return DashboardUi(
            loading = false,
            tankName = tank?.name ?: "My reef",
            date = dateFmt.format(Instant.ofEpochMilli(now).atZone(zone)),
            lastTest = lastMs?.let { "Last test ${TimeText.ago(it, now)}" } ?: "No tests logged yet",
            summaryStatus = summaryStatus,
            summary = summary,
            alerts = alertUis,
            hero = hero,
            tiles = tiles,
            due = due,
            peek = peek,
            events = events.sortedByDescending { it.at }.take(5).map { UiMappers.event(it, now) },
        )
    }

    /** Favorites (in user order when set) then any other parameter that has ever been logged. */
    fun orderTiles(evals: List<ParamEval>, userOrder: List<String>): List<ParamEval> {
        val shown = evals.filter { it.def.favorite || it.history.isNotEmpty() }
        if (userOrder.isEmpty()) return shown.sortedWith(compareBy({ !it.def.favorite }, { it.def.order }))
        val rank = userOrder.withIndex().associate { it.value to it.index }
        return shown.sortedWith(compareBy({ rank[it.id] ?: Int.MAX_VALUE }, { !it.def.favorite }, { it.def.order }))
    }

    /** Danger first (worst z-score), else caution with the worst z-score, else alkalinity. */
    fun pickHero(evals: List<ParamEval>): ParamEval? {
        val logged = evals.filter { it.latest != null }
        fun worst(s: Status) = logged.filter { it.eval.status == s }.maxByOrNull { abs(StatusEngine.zScore(it.def, it.latest!!.value)) }
        return worst(Status.DANGER) ?: worst(Status.CAUTION) ?: evals.firstOrNull { it.id == "alk" } ?: evals.firstOrNull()
    }
}
