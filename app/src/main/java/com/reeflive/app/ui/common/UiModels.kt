package com.reeflive.app.ui.common

import com.reeflive.app.ui.charts.ChartBands
import com.reeflive.app.ui.charts.ChartEvent
import com.reeflive.app.ui.charts.ChartPoint
import com.reeflive.core.care.DueState
import com.reeflive.core.care.TaskDue
import com.reeflive.core.care.TaskSchedule
import com.reeflive.core.model.EventType
import com.reeflive.core.model.ReefEvent
import com.reeflive.core.params.Range
import com.reeflive.core.status.AlertRules
import com.reeflive.core.status.Evaluation
import com.reeflive.core.status.Reason
import com.reeflive.core.status.Reading
import com.reeflive.core.status.StatusEngine
import com.reeflive.core.status.Status
import com.reeflive.core.status.Trend
import kotlin.math.roundToInt

/** Everything a parameter tile or list row shows, already formatted for display. */
data class TileUi(
    val id: String,
    val name: String,
    val shortName: String,
    val value: String?,
    val unit: String,
    val status: Status,
    val trend: Trend,
    val spark: List<ChartPoint>,
    val sparkStart: Long,
    val sparkEnd: Long,
    val ideal: Range,
    val acceptable: Range,
    val ago: String?,
    val staleDays: Int?,
    val tests: Int,
    val verdict: String,
) {
    val logged: Boolean get() = value != null
}

/** The dashboard hero: a tile plus delta, insight sentence and a 30-day mini chart. */
data class HeroUi(
    val tile: TileUi,
    val delta: String?,
    val insight: String,
    val points: List<ChartPoint>,
    val bands: ChartBands,
    val events: List<ChartEvent>,
    val precision: Int,
    val windowStart: Long,
    val windowEnd: Long,
)

data class TaskUi(
    val id: String,
    val title: String,
    val subtitle: String,
    val dueLabel: String,
    val state: DueState,
    val dueAt: Long,
) {
    val overdue: Boolean get() = state == DueState.OVERDUE
}

data class EventUi(val id: String, val type: EventType, val title: String, val time: String)

object UiMappers {
    const val DAY = 86_400_000L

    /** Builds a tile from an evaluation; [view] supplies the display unit. Sparkline covers the last [sparkDays]. */
    fun tile(view: ParamView, eval: Evaluation, history: List<Reading>, now: Long, sparkDays: Int = 14): TileUi {
        val def = view.def
        val from = now - sparkDays * DAY
        val spark = history.filter { it.epochMs >= from }.map {
            ChartPoint(it.epochMs, view.toDisplay(it.value), StatusEngine.valueStatus(def, it.value), it.kit)
        }
        val latest = eval.latest
        val stale = eval.reasons.filterIsInstance<Reason.Stale>().firstOrNull()
        return TileUi(
            id = def.id,
            name = def.name,
            shortName = def.shortName,
            value = latest?.let { view.format(it.value) },
            unit = view.symbol,
            status = eval.status,
            trend = eval.trend,
            spark = spark,
            sparkStart = from,
            sparkEnd = now,
            ideal = view.range(def.ideal),
            acceptable = view.range(def.acceptable),
            ago = latest?.let { TimeText.ago(it.epochMs, now) },
            staleDays = stale?.days?.roundToInt(),
            tests = history.size,
            verdict = AlertRules.shortVerdict(def, eval),
        )
    }

    fun chartPoints(view: ParamView, history: List<Reading>): List<ChartPoint> =
        history.map { ChartPoint(it.epochMs, view.toDisplay(it.value), StatusEngine.valueStatus(view.def, it.value), it.kit) }

    fun bands(view: ParamView): ChartBands {
        val d = view.def
        return ChartBands(view.range(d.ideal), view.range(d.acceptable), d.dangerLow?.let(view::toDisplay), d.dangerHigh?.let(view::toDisplay))
            .let { b ->
                // °F→°C and friends keep order; a decreasing conversion would swap low/high thresholds.
                val lo = b.dangerLow
                val hi = b.dangerHigh
                if (lo != null && hi != null && lo > hi) b.copy(dangerLow = hi, dangerHigh = lo) else b
            }
    }

    fun events(list: List<ReefEvent>): List<ChartEvent> = list.map { ChartEvent(it.at, it.type, it.title) }

    fun task(d: TaskDue, now: Long): TaskUi {
        val interval = TaskSchedule.describeInterval(d.task)
        val last = d.lastDoneAt?.let { "last ${TimeText.ago(it, now)}" } ?: "never done"
        return TaskUi(d.task.id, d.task.title, "$interval · $last", TaskSchedule.dueLabel(d), d.state, d.dueAt)
    }

    fun event(e: ReefEvent, now: Long): EventUi = EventUi(e.id, e.type, e.title, TimeText.ago(e.at, now))
}
