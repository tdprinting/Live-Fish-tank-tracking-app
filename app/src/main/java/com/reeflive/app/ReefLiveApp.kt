package com.reeflive.app

import android.app.Application
import android.content.Context
import com.reeflive.app.di.AppGraph
import kotlinx.coroutines.launch

class ReefLiveApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        graph.notifier.createChannels()
        graph.appScope.launch {
            runCatching { graph.reminders.scheduleDailyAlerts() }
        }
    }
}

val Context.appGraph: AppGraph get() = (applicationContext as ReefLiveApp).graph
