package com.reeflive.app.render

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import com.reeflive.core.sim.SimSnapshot
import kotlinx.coroutines.flow.StateFlow
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLDisplay

/**
 * The Live Tank GL view (ARCHITECTURE §8.2): GLES 3.0 context, RGBA8 + depth 24, no stencil, window MSAA
 * per quality (Medium 2x; High renders through [PostFx]'s 4x FBO instead), continuous rendering while
 * resumed, [TouchController] attached. Construct with the quality the window should be created for;
 * switching between presets with different window MSAA needs a new view (the Compose wrapper keys it).
 */
@SuppressLint("ViewConstructor")
class TankGlView(context: Context, quality: RenderQuality = RenderQuality.MEDIUM, attrs: AttributeSet? = null) : GLSurfaceView(context, attrs) {
    val renderer: TankRenderer
    private val touch: TouchController

    init {
        Gl.debug = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        setEGLContextClientVersion(3)
        setEGLConfigChooser(ConfigChooser(if (quality.bloom) 0 else quality.msaa))
        preserveEGLContextOnPause = true
        renderer = TankRenderer(context, quality)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
        touch = TouchController(context, renderer.input)
    }

    /** Polled on the GL thread once per frame; must be thread-safe (e.g. `AtomicReference::get`). */
    fun setSnapshotProvider(provider: (() -> SimSnapshot?)?) { renderer.snapshotProvider = provider }

    /** Smoothly follow an organism close up (null returns to the orbit camera). */
    fun follow(id: String?) = renderer.follow(id)

    fun setQuality(q: RenderQuality) = renderer.setQuality(q)
    fun resetCamera() = renderer.resetCamera()

    /** Clean-glass mode: drags slide the Flipper over the front glass (strokes go to [TankViewListener.onScrape]). */
    fun setCleaning(on: Boolean) = renderer.setCleaning(on)

    var listener: TankViewListener?
        get() = renderer.listener
        set(v) { renderer.listener = v }

    /** Draw the circling-fish test harness while no snapshot is available. */
    var demoWhenEmpty: Boolean
        get() = renderer.demoWhenEmpty
        set(v) { renderer.demoWhenEmpty = v }

    val stats: StateFlow<RenderStats> get() = renderer.stats

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean = touch.onTouchEvent(event)

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        renderer.shutdown()
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
