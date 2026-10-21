package com.reeflive.app.render

import com.reeflive.core.sim.SimSnapshot

/**
 * Screensaver mode over a live tank view the keeper was using: [enter] remembers their view and fades into
 * [CinemaDirector]'s scenes; [exit] fades through black, puts the view back exactly (camera and any follow) and
 * fades in. The host blocks touches while [active] (a tap only leaves) and draws [fade] / [caption].
 * Pure Kotlin, shared by the Android Live tank screen and the web build.
 */
class CinemaSession {
    private var renderer: TankRenderer? = null
    private var heightPx: () -> Float = { 1f }
    private var director: CinemaDirector? = null

    /** The view to drive (set by the view host when its renderer exists). */
    fun attach(renderer: TankRenderer?, heightPx: () -> Float) {
        if (renderer !== this.renderer) director = null
        this.renderer = renderer
        this.heightPx = heightPx
    }

    /** True from [enter] until the fade back after [exit] has finished. */
    val active: Boolean get() = director != null
    /** True once [exit] was asked (the fade back is running). */
    var leaving = false; private set

    val fade: Float get() = director?.fade ?: 0f
    val caption: String? get() = director?.caption
    val captionAlpha: Float get() = director?.captionAlpha ?: 0f

    fun enter(): Boolean {
        val r = renderer ?: return false
        if (director != null) return true
        r.saveView()
        leaving = false
        director = CinemaDirector(r.input, object : CinemaDirector.Control {
            override fun follow(id: String?) = r.follow(id)
            override fun resetCamera() = r.resetCamera()
            override fun viewHeightPx(): Float = heightPx()
            override fun restore() = r.restoreView()
        }).also { it.startFromView() }
        return true
    }

    fun exit() {
        val d = director ?: return
        leaving = true
        d.exit()
    }

    /** The followed animal left the tank. */
    fun followLost() { director?.followLost() }

    /** Once per UI frame. */
    fun update(dt: Float, snap: SimSnapshot?) {
        val d = director ?: return
        d.update(dt, snap)
        if (d.done) { director = null; leaving = false }
    }
}
