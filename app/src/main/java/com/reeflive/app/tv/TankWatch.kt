package com.reeflive.app.tv

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.reeflive.app.R
import com.reeflive.app.appGraph
import com.reeflive.app.render.RenderQuality
import com.reeflive.app.render.TankGlView
import com.reeflive.app.render.TankViewListener
import com.reeflive.app.render.ViewLight
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Full-screen, hands-off Live Tank for a TV (activity) or a screensaver (dream). Plain views, no Compose, so a
 * DreamService can host it too. The camera sways slowly across the tank and, in tour mode, glides in to follow
 * one animal at a time. Remote: ◀ ▶ turn, ▲ ▼ zoom, OK tour on/off, ☰ / play lamp look, Back exits.
 */
class TankWatch(private val context: Context, private val interactive: Boolean) {
    private val graph = context.appGraph
    private val holder = graph.simulationHolder
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val main = Handler(Looper.getMainLooper())

    private val gl = TankGlView(context, RenderQuality.HIGH)
    private val label = TextView(context)
    private val notice = TextView(context)
    val root: View = FrameLayout(context).apply {
        setBackgroundColor(Color.BLACK)
        addView(gl, FrameLayout.LayoutParams(-1, -1))
        addView(label, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(36) })
        addView(notice, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply { topMargin = dp(28); rightMargin = dp(36) })
        keepScreenOn = true
    }

    private var tour = true
    private var lightIdx = 0
    private var following: String? = null
    private var phaseStart = 0L
    private var manualUntil = 0L
    private var swayT = 0.0
    private var lastSwayYaw = 0.0
    private var lastTick = 0L
    private var quality = RenderQuality.HIGH
    private var lowFpsSince = 0L

    init {
        label.apply {
            setTextColor(Color.rgb(225, 240, 248))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = ResourcesCompat.getFont(context, R.font.inter_medium)
            setPadding(dp(20), dp(10), dp(20), dp(10))
            background = GradientDrawable().apply { cornerRadius = dp(24).toFloat(); setColor(Color.argb(150, 4, 16, 32)) }
            alpha = 0f
        }
        notice.apply {
            setTextColor(Color.rgb(225, 240, 248))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = ResourcesCompat.getFont(context, R.font.inter_medium)
            setPadding(dp(16), dp(8), dp(16), dp(8))
            background = GradientDrawable().apply { cornerRadius = dp(20).toFloat(); setColor(Color.argb(170, 4, 16, 32)) }
            visibility = View.GONE
        }
        gl.setSnapshotProvider { holder.snapshots.value }
        gl.demoWhenEmpty = false
        gl.listener = object : TankViewListener {
            override fun onFollowLost(id: String) { main.post { wide() } }
        }
        // a fresh TV install has no tank yet: seed Tee's tank so the reality sim has its livestock
        scope.launch(Dispatchers.IO) { runCatching { graph.seedInstaller.ensureInstalled() } }
    }

    fun resume() {
        holder.onForeground()
        gl.onResume()
        phaseStart = SystemClock.uptimeMillis()
        lastTick = phaseStart
        main.post(tick)
        if (interactive) show("◀ ▶ turn   ▲ ▼ zoom   OK fish tour ${if (tour) "on" else "off"}   ☰ lights", 7000)
    }

    fun pause() {
        main.removeCallbacks(tick)
        gl.onPause()
        holder.onBackground()
    }

    fun destroy() { pause(); scope.cancel() }

    /** A small pill in the top corner that stays up (e.g. "Update ready"); null hides it. */
    fun setNotice(text: String?) {
        notice.text = text
        notice.visibility = if (text == null) View.GONE else View.VISIBLE
    }

    /** D-pad handling; returns false for keys left to the system (Back, Home). */
    fun onKey(code: Int): Boolean {
        val now = SystemClock.uptimeMillis()
        when (code) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> { manualUntil = now + MANUAL_HOLD_MS; turn(-0.22f) }
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { manualUntil = now + MANUAL_HOLD_MS; turn(0.22f) }
            KeyEvent.KEYCODE_DPAD_UP -> { manualUntil = now + MANUAL_HOLD_MS; gl.renderer.input.zoom(0.8f) }
            KeyEvent.KEYCODE_DPAD_DOWN -> { manualUntil = now + MANUAL_HOLD_MS; gl.renderer.input.zoom(1.25f) }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                tour = !tour
                if (tour) { nextAnimal(); show("Fish tour on", 2500) } else { wide(); gl.resetCamera(); show("Whole tank", 2500) }
            }
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                lightIdx = (lightIdx + 1) % ViewLight.entries.size
                gl.viewLight = ViewLight.entries[lightIdx]
                show("Lights: ${ViewLight.entries[lightIdx].label}", 2500)
            }
            else -> return false
        }
        return true
    }

    private fun turn(radians: Float) {
        val h = gl.height.coerceAtLeast(1)
        gl.renderer.input.orbit(-radians * h / PI.toFloat(), 0f)
    }

    private fun wide() {
        following = null
        gl.follow(null)
        phaseStart = SystemClock.uptimeMillis()
    }

    private fun nextAnimal() {
        val ids = holder.snapshots.value.organisms.map { it.id }.filter { it != following }
        if (ids.isEmpty()) { wide(); return }
        following = ids.random()
        gl.follow(following)
        phaseStart = SystemClock.uptimeMillis()
    }

    private val tick = object : Runnable {
        override fun run() {
            val now = SystemClock.uptimeMillis()
            val dt = (now - lastTick).coerceIn(0, 200) / 1000.0
            lastTick = now
            val inPhase = now - phaseStart
            if (now > manualUntil) {
                if (following == null) {
                    // slow side-to-side sway across the front glass
                    swayT += dt
                    val yaw = SWAY_YAW * sin(2 * PI * swayT / SWAY_PERIOD_S)
                    turn((yaw - lastSwayYaw).toFloat())
                    lastSwayYaw = yaw
                    if (tour && inPhase > WIDE_MS) nextAnimal()
                } else {
                    turn((0.05 * dt).toFloat())   // drift around the followed animal
                    if (inPhase > FOLLOW_MS) wide()
                }
            }
            adaptQuality(now)
            main.postDelayed(this, 33)
        }
    }

    /** Drops a preset when the TV's GPU can't hold a smooth frame rate. */
    private fun adaptQuality(now: Long) {
        val fps = gl.stats.value.fps
        if (fps <= 0f || quality == RenderQuality.LOW) return
        if (fps < 24f) {
            if (lowFpsSince == 0L) lowFpsSince = now
            if (now - lowFpsSince > 8000) {
                quality = if (quality == RenderQuality.HIGH) RenderQuality.MEDIUM else RenderQuality.LOW
                gl.setQuality(quality)
                lowFpsSince = 0L
            }
        } else lowFpsSince = 0L
    }

    private val hideLabel = Runnable { label.animate().alpha(0f).setDuration(600).start() }

    private fun show(text: String, ms: Long) {
        label.text = text
        label.animate().alpha(1f).setDuration(250).start()
        main.removeCallbacks(hideLabel)
        main.postDelayed(hideLabel, ms)
    }

    private fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()

    private companion object {
        const val SWAY_YAW = 0.35
        const val SWAY_PERIOD_S = 120.0
        const val WIDE_MS = 90_000L
        const val FOLLOW_MS = 35_000L
        const val MANUAL_HOLD_MS = 30_000L
    }
}
