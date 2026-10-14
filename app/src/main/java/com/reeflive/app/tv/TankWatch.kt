package com.reeflive.app.tv

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
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
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Full-screen, hands-off Live Tank for a TV (activity) or a screensaver (dream). Plain views, no Compose, so a
 * DreamService can host it too. The camera sways slowly across the tank and, in tour mode, glides in to follow
 * one animal at a time.
 *
 * TV GPUs (Fire TV sticks and TV-integrated Fire OS) are far weaker than a phone and often hand out a 4K surface,
 * so the tank renders into a small fixed-size buffer that the display hardware scales up for free, at a TV preset
 * that steps down ([STEPS]) until the frame rate holds.
 *
 * Remote (interactive): ◀ ▶ turn, ▲ ▼ zoom, OK opens a one-row menu with a clearly lit selection; Back closes it.
 */
class TankWatch(private val context: Context, private val interactive: Boolean) {
    private val graph = context.appGraph
    private val holder = graph.simulationHolder
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val main = Handler(Looper.getMainLooper())

    // window MSAA off: the TV presets never use it, and it is the costliest thing on these GPUs
    private val gl = TankGlView(context, RenderQuality.LOW)
    private val label = TextView(context)
    private val notice = TextView(context)
    private val menu = LinearLayout(context)
    val root: FrameLayout = FrameLayout(context).apply {
        setBackgroundColor(Color.BLACK)
        addView(gl, FrameLayout.LayoutParams(-1, -1))
        addView(label, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(40) })
        addView(notice, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply { topMargin = dp(28); rightMargin = dp(36) })
        addView(menu, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(40) })
        keepScreenOn = true
    }

    /** Called from the menu's "Install update" item (TV activity only). */
    var onInstallUpdate: (() -> Unit)? = null
    private var updateReady = false

    private var tour = true
    private var lightIdx = 0
    private var following: String? = null
    private var phaseStart = 0L
    private var manualUntil = 0L
    private var swayT = 0.0
    private var lastSwayYaw = 0.0
    private var lastTick = 0L
    private var step = 0
    private var slowSince = 0L
    private var stepAt = 0L

    init {
        label.apply {
            setTextColor(TEXT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            typeface = ResourcesCompat.getFont(context, R.font.inter_medium)
            setPadding(dp(22), dp(10), dp(22), dp(10))
            background = pill(Color.argb(160, 4, 16, 32))
            alpha = 0f
        }
        notice.apply {
            setTextColor(TEXT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = ResourcesCompat.getFont(context, R.font.inter_medium)
            setPadding(dp(16), dp(8), dp(16), dp(8))
            background = pill(Color.argb(170, 4, 16, 32))
            visibility = View.GONE
        }
        menu.apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = GradientDrawable().apply { cornerRadius = dp(28).toFloat(); setColor(Color.argb(200, 4, 16, 32)) }
            visibility = View.GONE
        }
        gl.setSnapshotProvider { holder.snapshots.value }
        gl.demoWhenEmpty = false
        gl.listener = object : TankViewListener {
            override fun onFollowLost(id: String) { main.post { wide() } }
        }
        // small render buffer, scaled up by the display (see class note); re-applied when the window size changes
        root.addOnLayoutChangeListener { _, l, t, r, b, ol, ot, or_, ob ->
            if (r - l != or_ - ol || b - t != ob - ot) applyStep()
        }
        // a fresh TV install has no tank yet: seed Tee's tank so the reality sim has its livestock
        scope.launch(Dispatchers.IO) { runCatching { graph.seedInstaller.ensureInstalled() } }
    }

    fun resume() {
        holder.onForeground()
        gl.onResume()
        phaseStart = SystemClock.uptimeMillis()
        lastTick = phaseStart
        stepAt = phaseStart
        main.post(tick)
        if (interactive) show("Press OK for options", 5000)
    }

    fun pause() {
        main.removeCallbacks(tick)
        gl.onPause()
        holder.onBackground()
    }

    fun destroy() { pause(); scope.cancel() }

    /** A small pill in the top corner that stays up (e.g. "Update ready"); null hides it. */
    fun setNotice(text: String?, updateReady: Boolean = false) {
        this.updateReady = updateReady
        notice.text = text
        notice.visibility = if (text == null) View.GONE else View.VISIBLE
    }

    val menuOpen: Boolean get() = menu.visibility == View.VISIBLE

    /** D-pad handling while the menu is closed; returns false for keys left to the system (Back, Home). */
    fun onKey(code: Int): Boolean {
        if (menuOpen) {
            if (code == KeyEvent.KEYCODE_BACK) { closeMenu(); return true }
            main.removeCallbacks(autoClose); main.postDelayed(autoClose, MENU_IDLE_MS)
            return false   // the focused button moves / clicks
        }
        val now = SystemClock.uptimeMillis()
        when (code) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> { manualUntil = now + MANUAL_HOLD_MS; turn(-0.22f) }
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { manualUntil = now + MANUAL_HOLD_MS; turn(0.22f) }
            KeyEvent.KEYCODE_DPAD_UP -> { manualUntil = now + MANUAL_HOLD_MS; gl.renderer.input.zoom(0.8f) }
            KeyEvent.KEYCODE_DPAD_DOWN -> { manualUntil = now + MANUAL_HOLD_MS; gl.renderer.input.zoom(1.25f) }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> openMenu()
            else -> return false
        }
        return true
    }

    // ------------------------------------------------------------------ menu

    private fun openMenu() {
        menu.removeAllViews()
        if (updateReady) item("Install update") { closeMenu(); onInstallUpdate?.invoke() }
        item(if (tour) "Whole tank" else "Fish tour") {
            tour = !tour
            if (tour) nextAnimal() else { wide(); gl.resetCamera() }
            closeMenu()
        }
        item("Next fish") { tour = true; nextAnimal(); closeMenu() }
        item("Lights: ${ViewLight.entries[lightIdx].label}") {
            lightIdx = (lightIdx + 1) % ViewLight.entries.size
            gl.viewLight = ViewLight.entries[lightIdx]
            (it as TextView).text = "Lights: ${ViewLight.entries[lightIdx].label}"
        }
        item("Close") { closeMenu() }
        label.animate().cancel(); label.alpha = 0f
        menu.visibility = View.VISIBLE
        menu.getChildAt(0).requestFocus()
        main.removeCallbacks(autoClose); main.postDelayed(autoClose, MENU_IDLE_MS)
    }

    private fun closeMenu() {
        main.removeCallbacks(autoClose)
        menu.visibility = View.GONE
        root.requestFocus()
    }

    private val autoClose = Runnable { closeMenu() }

    private fun item(text: String, onClick: (View) -> Unit) {
        val tv = TextView(context).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            typeface = ResourcesCompat.getFont(context, R.font.inter_semibold)
            setPadding(dp(28), dp(14), dp(28), dp(14))
            isFocusable = true
            isClickable = true
            // focused = solid bright lagoon with dark text and a white ring, so the selection reads from the sofa
            background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused), GradientDrawable().apply {
                    cornerRadius = dp(22).toFloat(); setColor(Color.rgb(96, 226, 236)); setStroke(dp(3), Color.WHITE)
                })
                addState(intArrayOf(), pill(Color.argb(60, 255, 255, 255)))
            }
            setOnFocusChangeListener { v, has ->
                (v as TextView).setTextColor(if (has) Color.rgb(4, 22, 36) else TEXT)
                v.animate().scaleX(if (has) 1.08f else 1f).scaleY(if (has) 1.08f else 1f).setDuration(120).start()
            }
            setTextColor(TEXT)
            setOnClickListener(onClick)
        }
        menu.addView(tv, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8); marginEnd = dp(8) })
    }

    // ---------------------------------------------------------------- camera

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
            if (now > manualUntil && !menuOpen) {
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
            adapt(now)
            main.postDelayed(this, 33)
        }
    }

    // --------------------------------------------------------------- quality

    private class Step(val quality: RenderQuality, val lines: Int)

    private fun applyStep() {
        val s = STEPS[step]
        gl.setQuality(s.quality)
        val w = root.width; val h = root.height
        if (w > 0 && h > 0) {
            val lines = minOf(s.lines, h)
            gl.holder.setFixedSize((lines * w.toFloat() / h).roundToInt(), lines)
        }
    }

    /** Steps down a preset whenever the frame rate stays under [MIN_FPS] for a few seconds. */
    private fun adapt(now: Long) {
        if (step == STEPS.lastIndex || now - stepAt < SETTLE_MS) return
        val fps = gl.stats.value.fps
        if (fps <= 0f) return
        if (fps < MIN_FPS) {
            if (slowSince == 0L) slowSince = now
            if (now - slowSince > SLOW_MS) { step++; applyStep(); slowSince = 0L; stepAt = now }
        } else slowSince = 0L
    }

    // ----------------------------------------------------------------- misc

    private val hideLabel = Runnable { label.animate().alpha(0f).setDuration(600).start() }

    private fun show(text: String, ms: Long) {
        label.text = text
        label.animate().alpha(1f).setDuration(250).start()
        main.removeCallbacks(hideLabel)
        main.postDelayed(hideLabel, ms)
    }

    private fun pill(color: Int) = GradientDrawable().apply { cornerRadius = dp(24).toFloat(); setColor(color) }

    private fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()

    private companion object {
        val TEXT = Color.rgb(225, 240, 248)
        /** TV presets, best first: render lines (scaled up to the screen) and renderer preset. */
        val STEPS = listOf(
            Step(RenderQuality.MEDIUM, 720),
            Step(RenderQuality.LOW, 720),
            Step(RenderQuality.LOW, 540),
            Step(RenderQuality.LOW, 432),
        )
        const val MIN_FPS = 26f
        const val SLOW_MS = 3000L
        /** Let a new preset warm up (texture uploads, shader compiles) before judging it. */
        const val SETTLE_MS = 5000L
        const val SWAY_YAW = 0.35
        const val SWAY_PERIOD_S = 120.0
        const val WIDE_MS = 90_000L
        const val FOLLOW_MS = 35_000L
        const val MANUAL_HOLD_MS = 30_000L
        const val MENU_IDLE_MS = 10_000L
    }
}
