package com.reeflive.app.render

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration

/**
 * Gestures (ARCHITECTURE §8.2): the raw MotionEvent stream goes to the pure [GestureTracker] (one-finger orbit +
 * fling, two-finger pan / pinch / twist, tap = pick, double tap = zoom to a point or follow a fish, double-tap-drag =
 * one-finger zoom, two-finger tap = tap the glass, long press = drop food). In clean-glass mode
 * ([CameraInput.cleaning]) the first finger slides the Flipper instead and the other gestures are off.
 *
 * On every ACTION_DOWN the view asks its parent not to intercept: inside Compose (AndroidView) that makes the
 * interop filter hand the view the stream in the Initial pass, so no ancestor `pointerInput` that consumes can turn
 * the gesture into an ACTION_CANCEL half way. Runs on the UI thread and only accumulates into [CameraInput]; the GL
 * thread consumes it once per frame.
 */
class TouchController(context: Context, private val view: View, private val input: CameraInput) {
    private val cfg = ViewConfiguration.get(context)
    private val tracker = GestureTracker(input, cfg.scaledTouchSlop.toFloat(), cfg.scaledDoubleTapSlop.toFloat())
    private val handler = Handler(Looper.getMainLooper())
    private val pid = IntArray(GestureTracker.MAX)
    private val px = FloatArray(GestureTracker.MAX)
    private val py = FloatArray(GestureTracker.MAX)
    private val tick = Runnable { schedule(tracker.timer(SystemClock.uptimeMillis())) }

    fun onTouchEvent(ev: MotionEvent): Boolean {
        if (input.cleaning) {
            if (tracker.count > 0) tracker.cancel()
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> { view.parent?.requestDisallowInterceptTouchEvent(true); input.scrapeTouch(ev.getX(0), ev.getY(0), true) }
                MotionEvent.ACTION_MOVE -> input.scrapeTouch(ev.getX(0), ev.getY(0), true)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> input.scrapeTouch(ev.getX(0), ev.getY(0), false)
            }
            return true
        }
        val t = ev.eventTime
        tracker.instantTaps = input.instantTaps
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                view.parent?.requestDisallowInterceptTouchEvent(true)
                tracker.down(ev.getPointerId(0), ev.getX(0), ev.getY(0), t, first = true)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                val i = ev.actionIndex
                tracker.down(ev.getPointerId(i), ev.getX(i), ev.getY(i), t, first = false)
            }
            MotionEvent.ACTION_MOVE -> {
                val n = minOf(ev.pointerCount, GestureTracker.MAX)
                for (k in 0 until n) { pid[k] = ev.getPointerId(k); px[k] = ev.getX(k); py[k] = ev.getY(k) }
                tracker.move(n, pid, px, py, t)
            }
            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP -> {
                val i = ev.actionIndex
                tracker.up(ev.getPointerId(i), ev.getX(i), ev.getY(i), t)
            }
            MotionEvent.ACTION_CANCEL -> tracker.cancel()
        }
        schedule(tracker.timer(SystemClock.uptimeMillis()))
        return true
    }

    /** Stops the long-press / single-tap timer (view detached). */
    fun release() {
        handler.removeCallbacks(tick)
        tracker.cancel()
    }

    private fun schedule(next: Long) {
        handler.removeCallbacks(tick)
        if (next >= 0) handler.postAtTime(tick, next)
    }
}

/**
 * Thread-safe accumulator between the UI thread (gestures, as the [GestureTracker.Sink]) and the GL thread
 * (camera): motion is summed between frames and applied once in [drain].
 */
class CameraInput : GestureTracker.Sink {
    /** PICK single tap, GLASS two-finger tap, LONG_PRESS hold, ZOOM double tap. */
    enum class TapKind { PICK, GLASS, LONG_PRESS, ZOOM }
    class Tap(val kind: TapKind, val x: Float, val y: Float)

    private var orbitX = 0f; private var orbitY = 0f
    private var panX = 0f; private var panY = 0f
    private var zoom = 1f
    private var focusX = -1f; private var focusY = -1f
    private var rotation = 0f
    private var flingX = 0f; private var flingY = 0f; private var hasFling = false
    private var caught = false
    private var down = false
    private val taps = ArrayList<Tap>(8)
    /** GL-thread copy handed out by [drain] (reused, so draining allocates nothing). */
    private val drained = ArrayList<Tap>(8)

    /** Clean-glass mode: set from the UI thread, read by [TouchController] and the GL thread. */
    @Volatile var cleaning = false
    /** Supplies mode: taps pick a prop at once (no double-tap wait). Set by the GL thread, read by [TouchController]. */
    @Volatile var instantTaps = false
    private var scrapeX = 0f; private var scrapeY = 0f; private var scrapeDown = false

    /** Clean-glass mode: where the finger is and whether it is on the screen. */
    @Synchronized fun scrapeTouch(x: Float, y: Float, down: Boolean) { scrapeX = x; scrapeY = y; scrapeDown = down }

    /** GL thread: the finger position (px) while it is down, else null. */
    @Synchronized fun scrapePointer(out: FloatArray): Boolean {
        if (!scrapeDown) return false
        out[0] = scrapeX; out[1] = scrapeY
        return true
    }

    @Synchronized override fun touching(down: Boolean) { this.down = down; if (down) caught = true }
    @Synchronized override fun orbit(dx: Float, dy: Float) { orbitX += dx; orbitY += dy }
    @Synchronized override fun pan(dx: Float, dy: Float) { panX += dx; panY += dy }
    @Synchronized override fun pinch(factor: Float, focusX: Float, focusY: Float) {
        if (factor > 0f && !factor.isNaN() && !factor.isInfinite()) { zoom *= factor; this.focusX = focusX; this.focusY = focusY }
    }
    @Synchronized override fun rotate(radians: Float) { rotation += radians }
    @Synchronized override fun fling(vx: Float, vy: Float) { flingX = vx; flingY = vy; hasFling = true }
    @Synchronized override fun tap(kind: TapKind, x: Float, y: Float) { if (taps.size < 8) taps += Tap(kind, x, y) }

    /** Older call sites: pinch about the view centre. */
    fun zoom(f: Float) = pinch(f, -1f, -1f)

    /** GL thread: applies accumulated motion to [cam] and returns pending taps (a reused list; read it right away). */
    @Synchronized fun drain(cam: Camera, widthPx: Int, heightPx: Int): List<Tap> {
        if (caught) { cam.catchMotion(); caught = false }
        cam.touching = down
        val h = heightPx.toFloat()
        if (orbitX != 0f || orbitY != 0f) cam.orbit(orbitX, orbitY, h)
        if (panX != 0f || panY != 0f) cam.pan(panX, panY, h)
        if (zoom != 1f) {
            val fx = if (focusX < 0f) widthPx * 0.5f else focusX
            val fy = if (focusY < 0f) heightPx * 0.5f else focusY
            cam.zoomAt(zoom, fx, fy, widthPx, heightPx)
        }
        if (rotation != 0f) cam.rotate(rotation)
        if (hasFling) cam.fling(flingX, flingY, h)
        orbitX = 0f; orbitY = 0f; panX = 0f; panY = 0f; zoom = 1f; rotation = 0f; hasFling = false
        drained.clear()
        if (taps.isNotEmpty()) { drained.addAll(taps); taps.clear() }
        return drained
    }
}
