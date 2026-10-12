package com.reeflive.app.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.reeflive.app.appGraph
import com.reeflive.app.data.repo.Clock
import com.reeflive.app.data.repo.SettingsRepository
import com.reeflive.app.data.repo.TaskRepository
import com.reeflive.core.care.TaskSchedule
import com.reeflive.core.model.CareTask
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/** Daily parameter check at the user's reminder hour (default 08:00). */
class AlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            applicationContext.appGraph.alertEvaluator.runNow()
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.retry()
        }
    }
}

/** Fires at a task's due time; posts the reminder and re-checks daily while it stays overdue. */
class TaskReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_TASK_ID) ?: return Result.success()
        val g = applicationContext.appGraph
        val task = g.tasks.taskOnce(id) ?: return Result.success()
        if (!task.active) return Result.success()
        val last = g.tasks.lastCompletedAt(id)
        val now = g.clock.now()
        val due = TaskSchedule.due(task, last, now, ZoneId.systemDefault())
        if (due.dueAt > now + 60_000L) {
            // Woke early (edit, snooze or clock change): just schedule the real time.
            g.reminders.schedule(task, last)
            return Result.success()
        }
        if (g.settings.current().notifications) {
            val text = if (due.daysUntil < 0) TaskSchedule.dueLabel(due) else "Due now · ${TaskSchedule.describeInterval(task)}"
            g.notifier.notifyTask(task, text)
        }
        // Nag once a day until it is done.
        g.reminders.scheduleAt(task.id, now + TimeUnit.DAYS.toMillis(1))
        return Result.success()
    }

    companion object {
        const val KEY_TASK_ID = "taskId"
    }
}

/** Schedules task reminders and the daily alert check with WorkManager (ARCHITECTURE §7). */
class ReminderScheduler(
    private val context: Context,
    private val tasks: TaskRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
) {
    private val wm: WorkManager get() = WorkManager.getInstance(context)

    fun schedule(task: CareTask, lastDoneAt: Long?) {
        if (!task.active) {
            cancel(task.id)
            return
        }
        scheduleAt(task.id, TaskSchedule.nextDue(task, lastDoneAt, ZoneId.systemDefault()))
    }

    fun scheduleAt(taskId: String, atMs: Long) {
        val delay = (atMs - clock.now()).coerceAtLeast(0L)
        val req = OneTimeWorkRequestBuilder<TaskReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(TaskReminderWorker.KEY_TASK_ID to taskId))
            .addTag(TAG_TASKS)
            .addTag(uniqueName(taskId))
            .build()
        wm.enqueueUniqueWork(uniqueName(taskId), ExistingWorkPolicy.REPLACE, req)
    }

    fun cancel(taskId: String) {
        wm.cancelUniqueWork(uniqueName(taskId))
    }

    suspend fun rescheduleAll() {
        wm.cancelAllWorkByTag(TAG_TASKS)
        tasks.activeWithLast().forEach { schedule(it.task, it.lastDoneAt) }
    }

    /** Daily alert evaluation; [replace] re-anchors it after the reminder hour changes. */
    suspend fun scheduleDailyAlerts(replace: Boolean = false) {
        val hour = settings.current().reminderHour
        val zone = ZoneId.systemDefault()
        val nowZ = ZonedDateTime.now(zone)
        var next = LocalDate.now(zone).atTime(LocalTime.of(hour, 0)).atZone(zone)
        if (!next.isAfter(nowZ)) next = next.plusDays(1)
        val delay = next.toInstant().toEpochMilli() - System.currentTimeMillis()
        val req = PeriodicWorkRequestBuilder<AlertWorker>(24, TimeUnit.HOURS, 2, TimeUnit.HOURS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        wm.enqueueUniquePeriodicWork(
            DAILY_ALERTS,
            if (replace) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.KEEP,
            req,
        )
    }

    companion object {
        const val TAG_TASKS = "task-reminder"
        const val DAILY_ALERTS = "alerts-daily"
        fun uniqueName(taskId: String) = "task:$taskId"
    }
}

/** "Done" action on a task notification: completes the task without opening the app. */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DONE) return
        val id = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        val g = context.appGraph
        val pending = goAsync()
        g.appScope.launch {
            try {
                g.tasks.complete(id)
                g.notifier.cancelTask(id)
                g.tasks.taskOnce(id)?.let { g.reminders.schedule(it, g.tasks.lastCompletedAt(id)) }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_DONE = "com.reeflive.action.TASK_DONE"
        const val EXTRA_TASK_ID = "taskId"
    }
}

/** Re-creates reminders after a reboot or app update (WorkManager survives reboots, this re-anchors times). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val a = intent.action
        if (a != Intent.ACTION_BOOT_COMPLETED && a != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val g = context.appGraph
        val pending = goAsync()
        g.appScope.launch {
            try {
                g.reminders.rescheduleAll()
                g.reminders.scheduleDailyAlerts()
            } finally {
                pending.finish()
            }
        }
    }
}
