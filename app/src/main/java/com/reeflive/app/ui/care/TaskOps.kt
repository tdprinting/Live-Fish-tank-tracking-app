package com.reeflive.app.ui.care

import com.reeflive.app.di.AppGraph
import com.reeflive.core.model.CareTask
import com.reeflive.core.model.TaskCompletion
import java.time.LocalDate
import java.time.ZoneId

/** Task side effects shared by Dashboard, Care and Task detail: keep reminders and notifications in step. */
object TaskOps {
    fun isWaterChange(title: String): Boolean = title.contains("water change", ignoreCase = true)

    suspend fun complete(g: AppGraph, taskId: String, note: String? = null, amount: String? = null): TaskCompletion? {
        val c = g.tasks.complete(taskId, note, amount)
        reschedule(g, taskId)
        g.notifier.cancelTask(taskId)
        return c
    }

    suspend fun undo(g: AppGraph, c: TaskCompletion) {
        g.tasks.undoCompletion(c)
        reschedule(g, c.taskId)
    }

    /** Pushes the task to tomorrow at its reminder time. */
    suspend fun snoozeToTomorrow(g: AppGraph, taskId: String, zone: ZoneId = ZoneId.systemDefault()) {
        val t = g.tasks.taskOnce(taskId) ?: return
        val until = LocalDate.now(zone).plusDays(1).atTime(t.reminderHour, t.reminderMinute).atZone(zone).toInstant().toEpochMilli()
        g.tasks.snooze(taskId, until)
        g.reminders.scheduleAt(taskId, until)
        g.notifier.cancelTask(taskId)
    }

    suspend fun save(g: AppGraph, t: CareTask) {
        g.tasks.save(t)
        if (t.active) reschedule(g, t.id) else g.reminders.cancel(t.id)
    }

    suspend fun delete(g: AppGraph, taskId: String) {
        g.reminders.cancel(taskId)
        g.notifier.cancelTask(taskId)
        g.tasks.delete(taskId)
    }

    suspend fun reschedule(g: AppGraph, taskId: String) {
        g.tasks.taskOnce(taskId)?.let { g.reminders.schedule(it, g.tasks.lastCompletedAt(taskId)) }
    }
}
