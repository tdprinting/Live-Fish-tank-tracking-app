package com.reeflive.app.tv

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/** Phone / tablet screensaver mode (Live tank menu → Screensaver): the cinematic tank full screen; a tap exits. */
class ScreensaverActivity : Activity() {
    private lateinit var watch: TankWatch

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        watch = TankWatch(this, hint = "Tap to exit", phone = !isTv(this))
        // a clear layer over the tank takes every touch, so the camera is never grabbed: any tap leaves
        watch.root.addView(View(this).apply { setOnClickListener { finish() } }, FrameLayout.LayoutParams(-1, -1))
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
}
