package com.reeflive.app.notif

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.reeflive.app.MainActivity
import com.reeflive.app.R
import com.reeflive.app.work.NotificationActionReceiver
import com.reeflive.core.model.Alert
import com.reeflive.core.model.AlertKind
import com.reeflive.core.model.CareTask
import java.time.LocalTime

/** Notification channels and posting (DESIGN §7). */
class Notifier(private val context: Context) {

    fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CH_DANGER, "Reef alerts: danger", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "A parameter is in the danger zone or changing too fast"
                    enableVibration(true)
                },
                NotificationChannel(CH_CAUTION, "Reef alerts: caution", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "A parameter left its acceptable range"
                },
                NotificationChannel(CH_TASKS, "Maintenance reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Care tasks that are due"
                },
                NotificationChannel(CH_STALE, "Testing reminders", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Parameters you have not tested in a while"
                },
            ),
        )
    }

    fun canNotify(): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private fun quietNow(): Boolean {
        val h = LocalTime.now().hour
        return h >= QUIET_FROM || h < QUIET_TO
    }

    private fun openIntent(route: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_ROUTE, route)
        }
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    @SuppressLint("MissingPermission")
    fun notifyAlert(alert: Alert) {
        if (!canNotify()) return
        val danger = alert.kind == AlertKind.DANGER || alert.kind == AlertKind.FAST_CHANGE
        val channel = when (alert.kind) {
            AlertKind.DANGER, AlertKind.FAST_CHANGE -> CH_DANGER
            AlertKind.CAUTION -> CH_CAUTION
            AlertKind.STALE -> CH_STALE
        }
        val title = when (alert.kind) {
            AlertKind.DANGER, AlertKind.FAST_CHANGE -> "Reef danger"
            AlertKind.CAUTION -> "Reef caution"
            AlertKind.STALE -> "Time to test"
        }
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_reef)
            .setContentTitle(title)
            .setContentText(alert.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(alert.message))
            .setPriority(if (danger) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setColor(0xFFFF7A59.toInt())
            .setAutoCancel(true)
            .setSilent(!danger && quietNow())
            .setContentIntent(openIntent("log/${alert.paramId}", alert.paramId.hashCode()))
            .build()
        NotificationManagerCompat.from(context).notify("alert:${alert.paramId}:${alert.kind}", ID_ALERT, n)
    }

    @SuppressLint("MissingPermission")
    fun notifyTask(task: CareTask, text: String) {
        if (!canNotify()) return
        val done = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_DONE
            putExtra(NotificationActionReceiver.EXTRA_TASK_ID, task.id)
        }
        val donePi = PendingIntent.getBroadcast(
            context, task.id.hashCode(), done, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CH_TASKS)
            .setSmallIcon(R.drawable.ic_stat_reef)
            .setContentTitle(task.title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setColor(0xFF5CE1E6.toInt())
            .setAutoCancel(true)
            .setSilent(quietNow())
            .setContentIntent(openIntent("care", task.id.hashCode() + 1))
            .addAction(0, "Done", donePi)
            .build()
        NotificationManagerCompat.from(context).notify(taskTag(task.id), ID_TASK, n)
    }

    /** Supplies and treatment-dose reminders (same channel as maintenance tasks). */
    @SuppressLint("MissingPermission")
    fun notifyReminder(tag: String, title: String, text: String, route: String) {
        if (!canNotify()) return
        val n = NotificationCompat.Builder(context, CH_TASKS)
            .setSmallIcon(R.drawable.ic_stat_reef)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setColor(0xFF5CE1E6.toInt())
            .setAutoCancel(true)
            .setSilent(quietNow())
            .setContentIntent(openIntent(route, tag.hashCode()))
            .build()
        NotificationManagerCompat.from(context).notify("extras:$tag", ID_REMINDER, n)
    }

    fun cancelTask(taskId: String) = NotificationManagerCompat.from(context).cancel(taskTag(taskId), ID_TASK)

    companion object {
        const val CH_DANGER = "reef_alerts_danger"
        const val CH_CAUTION = "reef_alerts_caution"
        const val CH_TASKS = "reef_tasks"
        const val CH_STALE = "reef_staleness"
        const val EXTRA_ROUTE = "com.reeflive.ROUTE"
        private const val ID_ALERT = 1
        private const val ID_TASK = 2
        private const val ID_REMINDER = 3
        private const val QUIET_FROM = 22
        private const val QUIET_TO = 7

        fun taskTag(taskId: String) = "task:$taskId"
    }
}
