package com.reeflive.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reeflive.app.data.repo.AppSettings
import com.reeflive.app.data.repo.ThemeMode
import com.reeflive.app.notif.Notifier
import com.reeflive.app.ui.components.LocalHapticsEnabled
import com.reeflive.app.ui.nav.ReefRoot
import com.reeflive.app.ui.theme.ReefLiveTheme
import com.reeflive.app.ui.theme.ReefTheme
import com.reeflive.app.widget.ReefWidget
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    /** Route requested by a notification tap ("log/alk", "care"); consumed by the nav host. */
    private val pendingRoute = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) pendingRoute.value = intent?.getStringExtra(Notifier.EXTRA_ROUTE)
        val graph = appGraph
        setContent {
            val settings by graph.settings.settings.collectAsStateWithLifecycle(AppSettings())
            val dark = when (settings.theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
            }
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            ReefLiveTheme(darkTheme = dark, reduceMotion = settings.reduceMotion) {
                CompositionLocalProvider(LocalHapticsEnabled provides settings.haptics) {
                    if (!settings.loaded) {
                        // DataStore answers in a few ms; show the bare gradient meanwhile.
                        Box(Modifier.fillMaxSize().background(ReefTheme.colors.bgBrush))
                    } else {
                        ReefRoot(
                            settings = settings,
                            pendingRoute = pendingRoute.value,
                            onRouteConsumed = { pendingRoute.value = null },
                        )
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Keep the home-screen widget in step with what was just logged.
        val ctx = applicationContext
        appGraph.appScope.launch { runCatching { ReefWidget.refreshAll(ctx) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(Notifier.EXTRA_ROUTE)?.let { pendingRoute.value = it }
    }
}
