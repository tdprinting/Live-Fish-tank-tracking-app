package com.reeflive.app.work

import com.reeflive.app.data.repo.AlertRepository
import com.reeflive.app.data.repo.Clock
import com.reeflive.app.data.repo.MeasurementRepository
import com.reeflive.app.data.repo.SettingsRepository
import com.reeflive.app.data.repo.TargetsRepository
import com.reeflive.app.notif.Notifier
import com.reeflive.core.care.TaskSchedule
import com.reeflive.core.model.Alert
import com.reeflive.core.model.AlertKind
import com.reeflive.core.status.AlertRules
import com.reeflive.core.status.StatusEngine
import kotlinx.coroutines.flow.first
import java.time.ZoneId

/**
 * Wraps the core [StatusEngine] (ARCHITECTURE §7): evaluates every visible parameter, stores alerts
 * deduped by (parameter, kind, day) and posts a notification only for rows that are new.
 * Runs after every save and daily from [AlertWorker].
 */
class AlertEvaluator(
    private val measurements: MeasurementRepository,
    private val targets: TargetsRepository,
    private val alerts: AlertRepository,
    private val settings: SettingsRepository,
    private val notifier: Notifier,
    private val clock: Clock,
) {
    suspend fun runNow(notify: Boolean = true): List<Alert> {
        val now = clock.now()
        val defs = targets.effectiveOnce()
        val byParam = measurements.readings().first()
        val drafts = defs.flatMap { def ->
            val h = byParam[def.id].orEmpty()
            if (h.isEmpty()) return@flatMap emptyList()
            AlertRules.draftsFor(def, StatusEngine.evaluate(def, h, now))
                // Staleness reminders only for the parameters on the dashboard.
                .filter { it.kind != AlertKind.STALE || def.favorite }
        }
        val fresh = alerts.insert(drafts, TaskSchedule.dayKey(now, ZoneId.systemDefault()))
        if (notify && fresh.isNotEmpty() && settings.current().notifications) {
            // One notification per parameter: the most severe new alert.
            fresh.groupBy { it.paramId }.values
                .map { list -> list.minBy { severity(it.kind) } }
                .forEach { notifier.notifyAlert(it) }
        }
        return fresh
    }

    private fun severity(k: AlertKind) = when (k) {
        AlertKind.FAST_CHANGE -> 0
        AlertKind.DANGER -> 1
        AlertKind.CAUTION -> 2
        AlertKind.STALE -> 3
    }
}
