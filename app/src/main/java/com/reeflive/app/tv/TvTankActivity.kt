package com.reeflive.app.tv

import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/** TV launcher entry (Fire TV / Android TV): the live tank full screen, driven by the remote. */
class TvTankActivity : Activity() {
    private lateinit var watch: TankWatch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        watch = TankWatch(this, interactive = true)
        setContentView(watch.root)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onResume() { super.onResume(); watch.resume() }
    override fun onPause() { watch.pause(); super.onPause() }
    override fun onDestroy() { watch.destroy(); super.onDestroy() }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean =
        watch.onKey(keyCode) || super.onKeyDown(keyCode, event)
}
