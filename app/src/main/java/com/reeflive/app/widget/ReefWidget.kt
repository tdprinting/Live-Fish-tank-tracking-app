package com.reeflive.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.reeflive.app.MainActivity
import com.reeflive.app.R
import com.reeflive.app.appGraph
import com.reeflive.app.data.repo.AppSettings
import com.reeflive.app.notif.Notifier
import com.reeflive.app.ui.nav.Routes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.ZoneId

/**
 * Home-screen widget (plain RemoteViews, no extra libraries): health score, key parameters and the next care task.
 * Refreshed by the system (hourly), after the daily check, and whenever ReefLive goes to the background.
 */
class ReefWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                update(context.applicationContext, appWidgetManager, appWidgetIds)
            } catch (e: Exception) {
                // Keep the previous widget content.
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        suspend fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, ReefWidget::class.java))
            if (ids.isNotEmpty()) update(context, manager, ids)
        }

        private suspend fun update(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val g = context.appGraph
            val settings = runCatching { g.settings.settings.first() }.getOrDefault(AppSettings())
            val snap = WidgetData.build(
                g.measurements.latestEvaluations().first(), g.tasks.tasks.first(), settings, g.clock.now(), ZoneId.systemDefault(),
            )
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(Notifier.EXTRA_ROUTE, Routes.INSIGHTS)
            }
            val open = PendingIntent.getActivity(context, 4242, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            for (id in ids) {
                val v = RemoteViews(context.packageName, R.layout.widget_reef)
                v.setTextViewText(R.id.widget_score, snap.score)
                v.setTextViewText(R.id.widget_grade, snap.grade)
                v.setTextViewText(R.id.widget_params, snap.params)
                v.setTextViewText(R.id.widget_task, snap.nextTask)
                v.setOnClickPendingIntent(R.id.widget_root, open)
                manager.updateAppWidget(id, v)
            }
        }
    }
}
