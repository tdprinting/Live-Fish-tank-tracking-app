package com.reeflive.app.tv

import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.reeflive.app.appGraph
import com.reeflive.app.update.UpdateState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * TV launcher entry (Fire TV / Android TV): the live tank full screen, driven by the remote. Keeps itself up to
 * date: checks GitHub Releases (the updater's 6 h spacing), downloads a new APK in the background, then a corner
 * pill asks for one OK press to hand it to the system installer (Android never installs without that press).
 */
class TvTankActivity : Activity() {
    private lateinit var watch: TankWatch
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var updateJob: Job? = null
    private val updates get() = appGraph.updates

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        watch = TankWatch(this, interactive = true)
        setContentView(watch.root)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        scope.launch {
            updates.state.collect { s ->
                when (s) {
                    is UpdateState.Available -> updates.download()   // nobody taps "Download" on a TV
                    is UpdateState.Downloading -> watch.setNotice("Downloading update ${(s.read * 100 / s.total.coerceAtLeast(1))}%")
                    is UpdateState.Ready -> watch.setNotice("Update ready · press OK to install")
                    else -> watch.setNotice(null)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        watch.resume()
        // the TV can sit on this screen for days: keep asking (UpdateManager spaces real checks 6 h apart)
        updateJob = scope.launch { while (true) { updates.checkOnLaunch(); delay(CHECK_EVERY_MS) } }
    }

    override fun onPause() { updateJob?.cancel(); watch.pause(); super.onPause() }
    override fun onDestroy() { scope.cancel(); watch.destroy(); super.onDestroy() }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val ok = keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
        val ready = updates.state.value as? UpdateState.Ready
        if (ok && ready != null) { install(ready); return true }
        return watch.onKey(keyCode) || super.onKeyDown(keyCode, event)
    }

    private fun install(ready: UpdateState.Ready) {
        if (!updates.canInstall(this)) {
            // first update only: Android wants "Install unknown apps" allowed for ReefLive
            val opened = runCatching { startActivity(updates.unknownSourcesIntent(this)) }.isSuccess
            watch.setNotice(if (opened) "Allow ReefLive, then press OK again" else "Allow ReefLive under Developer options → Install unknown apps")
            return
        }
        runCatching { startActivity(updates.installIntent(this, ready.file)) }
            .onFailure { watch.setNotice("Couldn't open the installer") }
    }

    private companion object {
        const val CHECK_EVERY_MS = 60 * 60 * 1000L
    }
}
