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
            // One-time: give existing installs the animals' new names (only rows still on the old default names).
            val prefs = getSharedPreferences("reeflive_seed", Context.MODE_PRIVATE)
            if (!prefs.getBoolean(NAMES_2026_10, false)) {
                runCatching { graph.seedInstaller.renameSeededAnimals() }
                    .onSuccess { prefs.edit().putBoolean(NAMES_2026_10, true).apply() }
            }
        }
    }
}

private const val NAMES_2026_10 = "animal_names_2026_10"

val Context.appGraph: AppGraph get() = (applicationContext as ReefLiveApp).graph
