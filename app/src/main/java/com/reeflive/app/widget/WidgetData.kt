package com.reeflive.app.widget

import com.reeflive.app.data.repo.AppSettings
import com.reeflive.app.data.repo.ParamEval
import com.reeflive.app.data.repo.TaskWithLast
import com.reeflive.app.ui.common.ParamView
import com.reeflive.app.ui.common.TimeText
import com.reeflive.app.ui.extras.InsightsMapper
import com.reeflive.core.care.TaskSchedule
import java.time.ZoneId

/** What the home-screen widget shows: health score, key parameters and the next task. */
data class WidgetSnapshot(val score: String, val grade: String, val params: String, val nextTask: String)

object WidgetData {
    private val KEY = listOf("alk", "ca", "temp", "sal", "no3", "po4")

    fun build(evals: List<ParamEval>, tasks: List<TaskWithLast>, settings: AppSettings, now: Long, zone: ZoneId): WidgetSnapshot {
        val health = InsightsMapper.build(evals, tasks, now, zone).health
        val params = KEY.mapNotNull { id ->
            val e = evals.firstOrNull { it.id == id } ?: return@mapNotNull null
            val v = e.latest?.value ?: return@mapNotNull null
            "${e.def.shortName} ${ParamView(e.def, settings.displayUnitFor(id, e.def.baseUnit)).formatWithUnit(v)}"
        }.joinToString("  ·  ").ifEmpty { "No tests logged yet" }
        val next = tasks.filter { it.task.active }
            .map { it to TaskSchedule.nextDue(it.task, it.lastDoneAt, zone) }
            .minByOrNull { it.second }
            ?.let { (t, due) -> "Next: ${t.task.title} · " + if (due < now) "overdue" else TimeText.dayLabel(due, now).lowercase() }
            ?: "No care tasks"
        return WidgetSnapshot(health?.score?.toString() ?: "–", health?.grade ?: "Reef health", params, next)
    }
}
