package com.reeflive.app.render

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.opengl.GLSurfaceView
import android.os.PowerManager
import android.util.AttributeSet
import android.util.Log
import android.view.Choreographer
import android.view.MotionEvent
import com.reeflive.core.sim.SimSnapshot
import kotlinx.coroutines.flow.StateFlow
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLDisplay
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The Live Tank GL view (ARCHITECTURE §8.2): GLES 3.0 context, RGBA8 + depth 24, no stencil, window MSAA
 * per quality (Medium 2x; High renders through [PostFx]'s 4x FBO instead), [TouchController] attached. Construct with
 * the quality the window should be created for; switching between presets with different window MSAA needs a new view
 * (the Compose wrapper keys it).
 *
 * Frame budget (phones run hot and slow at full resolution and refresh):
 * - Frames are paced by [FramePacer] on the Choreographer (render mode WHEN_DIRTY): at most [RenderQuality.targetFps]
 *   (60, Low 30; 30 on any preset in battery saver), even on 90 / 120 Hz screens, and none while the view is paused,
 *   hidden ([shown]) or off the window.
 * - The GL buffer is [renderScale] of the view ([RenderQuality.renderScale], capped at [RenderQuality.maxRenderPixels])
 *   and the display hardware scales it up for free (`SurfaceHolder.setFixedSize`). Touches are scaled to buffer pixels.
 * - [autoQuality] (on by default): [AdaptiveQuality] steps the render scale down, then sheds effects
 *   ([TankRenderer.relief]), while frames miss the cap, and back up when there is headroom.
 */
@SuppressLint("ViewConstructor")
class TankGlView(
    context: Context,
    quality: RenderQuality = RenderQuality.MEDIUM,
    attrs: AttributeSet? = null,
    /**
     * False: the host sizes the buffer and picks presets itself (TankWatch, the TV / screensaver view): no auto
     * quality, no render scale, a 60 fps cap on every preset (30 in battery saver).
     */
    private val managed: Boolean = true,
) : GLSurfaceView(context, attrs) {
    val renderer: TankRenderer
    private val touch: TouchController

    init {
        Gl.debug = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        setEGLContextClientVersion(3)
        setEGLConfigChooser(ConfigChooser(if (quality.bloom) 0 else quality.msaa))
        preserveEGLContextOnPause = true
        renderer = TankRenderer(context, quality)
        setRenderer(renderer)
        // frames are requested by the pacer (capped rate, none while hidden) instead of on every vsync
        renderMode = RENDERMODE_WHEN_DIRTY
        touch = TouchController(context, this, renderer.input)
    }

    // ------------------------------------------------------------------ frame budget

    private var preset = quality
    private val auto = AdaptiveQuality()
    /** Multiplier auto quality puts on the preset's render scale (main thread). */
    private var autoScale = 1f
    private val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private var batterySaver = power?.isPowerSaveMode == true

    init {
        renderer.autoQuality = if (managed) auto else null
        renderer.onAutoScale = { k -> autoScale = k; applyScale() }
        renderer.targetFps = capFps()
    }

    /** Auto picture quality ([AdaptiveQuality]); off keeps the preset exactly as picked. Main thread. */
    var autoQuality = true
        set(v) {
            if (v == field) return
            field = v
            if (!managed) return
            restartAuto()
            renderer.autoQuality = if (v) auto else null
        }

    /** False while something covers the whole tank (the microscope): no frames are drawn. Main thread. */
    var shown = true
        set(v) { if (v != field) { field = v; updatePacer() } }

    /** GL buffer size / view size now in use (main thread). */
    var renderScale = 1f; private set
    private var bufW = 0; private var bufH = 0

    /** Frame cap in use: the preset's, or 30 in battery saver. */
    private fun capFps(): Int {
        val fps = if (managed) preset.targetFps else 60
        return if (batterySaver) minOf(30, fps) else fps
    }

    private fun restartAuto() {
        autoScale = 1f
        // the controller lives on the GL thread
        queueEvent { auto.reset(); renderer.relief = 0 }
        renderer.relief = 0
        applyScale()
    }

    /**
     * Sizes the GL buffer: the preset's scale (at most its pixel budget) times auto quality's multiplier. Only a real
     * change reconfigures the surface.
     */
    private fun applyScale() {
        val w = width; val h = height
        if (!managed || w <= 0 || h <= 0) return
        val cap = sqrt(preset.maxRenderPixels.toFloat() / (w.toFloat() * h.toFloat()))
        val s = (minOf(preset.renderScale, cap) * (if (autoQuality) autoScale else 1f)).coerceIn(MIN_SCALE, 1f)
        val full = s >= 0.995f
        val bw = if (full) w else (w * s).roundToInt().coerceAtLeast(1)
        val bh = if (full) h else (h * s).roundToInt().coerceAtLeast(1)
        if (bw != bufW || bh != bufH) {
            bufW = bw; bufH = bh
            if (full) holder.setSizeFromLayout() else holder.setFixedSize(bw, bh)
        }
        val k = if (full) 1f else bw.toFloat() / w
        renderScale = k
        touch.scale = k
        renderer.renderScale = k
        renderer.viewWidth = if (full) 0 else w
        renderer.viewHeight = if (full) 0 else h
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // resizing the surface from inside layout is not allowed: next message
        post { applyScale() }
    }

    private var resumedNow = true
    private var windowShown = true

    override fun onResume() {
        super.onResume()
        resumedNow = true
        updatePacer()
    }

    override fun onPause() {
        resumedNow = false
        updatePacer()
        super.onPause()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updatePacer()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        windowShown = visibility == android.view.View.VISIBLE
        updatePacer()
    }

    private fun updatePacer() {
        if (resumedNow && windowShown && shown && isAttachedToWindow) pacer.start() else pacer.stop()
    }

    private val pacer = FramePacer()

    /**
     * Requests a frame on the Choreographer's vsync whenever a frame period of the cap has gone by (a running remainder,
     * so 60 on a 90 Hz screen comes out at 60 on average). Checks battery saver about once a second.
     */
    private inner class FramePacer : Choreographer.FrameCallback {
        private var running = false
        private var lastNs = 0L
        private var acc = 0L
        private var checks = 0

        fun start() {
            if (running) return
            running = true
            lastNs = 0L; acc = 0L
            Choreographer.getInstance().postFrameCallback(this)
        }

        fun stop() {
            if (!running) return
            running = false
            Choreographer.getInstance().removeFrameCallback(this)
        }

        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            Choreographer.getInstance().postFrameCallback(this)
            if (++checks >= 60) {
                checks = 0
                val saver = power?.isPowerSaveMode == true
                if (saver != batterySaver) batterySaver = saver
            }
            val fps = capFps()
            renderer.targetFps = fps
            val period = 1_000_000_000L / fps
            if (lastNs == 0L) { lastNs = frameTimeNanos; requestRender(); return }
            acc += frameTimeNanos - lastNs
            lastNs = frameTimeNanos
            if (acc >= period - PACE_SLACK_NS) {
                acc -= period
                // after a stall (app switch, a long frame) start over instead of rendering a burst
                if (acc > period) acc = 0L
                requestRender()
            }
        }
    }

    /** Polled on the GL thread once per frame; must be thread-safe (e.g. `AtomicReference::get`). */
    fun setSnapshotProvider(provider: (() -> SimSnapshot?)?) { renderer.snapshotProvider = provider }

    /** Smoothly follow an organism close up (null returns to the orbit camera). */
    fun follow(id: String?) = renderer.follow(id)

    /** The follow camera's on-screen buttons (zoom, swing round): pass the screen's own handle so its buttons reach the camera. */
    var followCam: FollowCamControl
        get() = renderer.followCam
        set(v) { renderer.followCam = v }

    fun setQuality(q: RenderQuality) {
        renderer.setQuality(q)
        if (q == preset) return
        preset = q
        renderer.targetFps = capFps()
        if (managed) restartAuto()
    }
    fun resetCamera() = renderer.resetCamera()
    fun lookAtNursery(on: Boolean) = renderer.lookAtNursery(on)
    /** Bumped by the UI for each "Look at the nursery" press (0 = none yet). */
    var nurseryRequest = 0
        set(v) { if (v != field) { field = v; if (v > 0) lookAtNursery(true) } }

    /** Clean-glass mode: drags slide the Flipper over the front glass (strokes go to [TankViewListener.onScrape]). */
    fun setCleaning(on: Boolean) = renderer.setCleaning(on)
    /** Submarine mode: first-person ride (true) or chase camera. */
    var subFirstPerson: Boolean
        get() = renderer.subFirstPerson
        set(v) { renderer.subFirstPerson = v }

    var listener: TankViewListener?
        get() = renderer.listener
        set(v) { renderer.listener = v }

    /** Draw the circling-fish test harness while no snapshot is available. */
    var demoWhenEmpty: Boolean
        get() = renderer.demoWhenEmpty
        set(v) { renderer.demoWhenEmpty = v }

    /** Camera projection published every frame for overlays drawn over this view. */
    var projection: TankProjection?
        get() = renderer.projection
        set(v) { renderer.projection = v }

    /** Watch-mode lamp look, drawn only (the sim's light schedule is untouched). */
    var viewLight: ViewLight
        get() = renderer.viewLight
        set(v) { renderer.viewLight = v }

    /** Mailbox for in-tank action feedback ([TankBursts.emit] from any thread). */
    var bursts: TankBursts?
        get() = renderer.bursts
        set(v) { renderer.bursts = v }
    /** A fish store visit: the shop around the display tank, walks between its tanks, taps on them and the counter. */
    var storeRoom: StoreRoomControl?
        get() = renderer.storeRoom
        set(v) { renderer.storeRoom = v }
    /** The stand and room round the tank at home (My tank's bought ones, or Tee's). */
    var room: RoomControl
        get() = renderer.room
        set(v) { renderer.room = v }
    /** The supply shelf beside the tank (Supplies mode): open / close, row focus, locked props and taps. */
    var shelf: SupplyShelfControl?
        get() = renderer.shelf
        set(v) { renderer.shelf = v }

    /** "Fix my tank" upkeep shows, played one after another by the renderer. */
    var care: CareShowControl?
        get() = renderer.care
        set(v) { renderer.care = v }

    /** Tap-to-place handle (food from the shelf goes in where the player taps). */
    var aim: TankAimControl?
        get() = renderer.aim
        set(v) { renderer.aim = v }

    val stats: StateFlow<RenderStats> get() = renderer.stats

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean = touch.onTouchEvent(event)

    override fun onDetachedFromWindow() {
        pacer.stop()
        super.onDetachedFromWindow()
        touch.release()
        renderer.shutdown()
    }

    companion object {
        /** Smallest render scale auto quality goes to (beyond this text-sized detail turns to mush). */
        const val MIN_SCALE = 0.4f
        /** A vsync may come this early and still count as a whole frame period (ns). */
        const val PACE_SLACK_NS = 3_000_000L
    }

    /** RGBA8888 / depth 24 / no stencil, preferring [samples]x MSAA and falling back without it. */
    private class ConfigChooser(private val samples: Int) : EGLConfigChooser {
        override fun chooseConfig(egl: EGL10, display: EGLDisplay): EGLConfig {
            val es3 = 0x40 // EGL_OPENGL_ES3_BIT_KHR
            fun attribs(s: Int, depth: Int) = intArrayOf(
                EGL10.EGL_RED_SIZE, 8, EGL10.EGL_GREEN_SIZE, 8, EGL10.EGL_BLUE_SIZE, 8, EGL10.EGL_ALPHA_SIZE, 8,
                EGL10.EGL_DEPTH_SIZE, depth, EGL10.EGL_STENCIL_SIZE, 0,
                EGL10.EGL_RENDERABLE_TYPE, es3,
                EGL10.EGL_SAMPLE_BUFFERS, if (s > 0) 1 else 0, EGL10.EGL_SAMPLES, s,
                EGL10.EGL_NONE,
            )
            for ((s, d) in listOf(samples to 24, 0 to 24, 0 to 16)) {
                val num = IntArray(1)
                val configs = arrayOfNulls<EGLConfig>(1)
                if (egl.eglChooseConfig(display, attribs(s, d), configs, 1, num) && num[0] > 0 && configs[0] != null) return configs[0]!!
            }
            Log.e(Gl.TAG, "no matching EGL config")
            throw IllegalStateException("No GLES 3.0 EGL config")
        }
    }
}
