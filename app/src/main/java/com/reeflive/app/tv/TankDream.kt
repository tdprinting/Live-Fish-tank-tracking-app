package com.reeflive.app.tv

import android.service.dreams.DreamService

/**
 * The live tank as the system screensaver (Daydream). Any remote press wakes the TV back up. On Fire TV it is
 * picked with adb (`settings put secure screensaver_components com.reeflive/com.reeflive.app.tv.TankDream`).
 */
class TankDream : DreamService() {
    private var watch: TankWatch? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        isScreenBright = true
        val w = TankWatch(this, interactive = false)
        watch = w
        setContentView(w.root)
    }

    override fun onDreamingStarted() { super.onDreamingStarted(); watch?.resume() }
    override fun onDreamingStopped() { watch?.pause(); super.onDreamingStopped() }
    override fun onDetachedFromWindow() { watch?.destroy(); watch = null; super.onDetachedFromWindow() }
}
