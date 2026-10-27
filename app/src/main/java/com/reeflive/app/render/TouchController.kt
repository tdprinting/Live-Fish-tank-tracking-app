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
 * one-finger zoom, two-finger tap = tap the glass, long press = a fingertip held on the glass (slide it, flick it) or
 * food dropped on rock / sand). In clean-glass mode
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

    init {
        // a fingertip's radius: three touch slops (24 dp on Android, half of the 48 dp minimum touch target)
        input.fingerPx = cfg.scaledTouchSlop * 3f
    }

    /**
     * Render buffer pixels per view pixel ([TankGlView.renderScale]): touches are handed on in buffer pixels, the
     * pixels the camera and the picker work in, and the slops and fingertip shrink with them. 1 on the web.
     */
    var scale = 1f
        set(v) {
            if (v == field || v <= 0f) return
            field = v
            tracker.touchSlop = cfg.scaledTouchSlop * v
            tracker.doubleTapSlop = cfg.scaledDoubleTapSlop * v
            input.fingerPx = cfg.scaledTouchSlop * 3f * v
        }
    private fun x(ev: MotionEvent, i: Int) = ev.getX(i) * scale
    private fun y(ev: MotionEvent, i: Int) = ev.getY(i) * scale

    fun onTouchEvent(ev: MotionEvent): Boolean {
        if (input.cleaning) {
            if (tracker.count > 0) tracker.cancel()
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> { view.parent?.requestDisallowInterceptTouchEvent(true); input.scrapeTouch(x(ev, 0), y(ev, 0), true) }
                MotionEvent.ACTION_MOVE -> input.scrapeTouch(x(ev, 0), y(ev, 0), true)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> input.scrapeTouch(x(ev, 0), y(ev, 0), false)
            }
            return true
        }
        val t = ev.eventTime
        tracker.instantTaps = input.instantTaps
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                view.parent?.requestDisallowInterceptTouchEvent(true)
                input.press(x(ev, 0), y(ev, 0))
                tracker.down(ev.getPointerId(0), x(ev, 0), y(ev, 0), t, first = true)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                val i = ev.actionIndex
                tracker.down(ev.getPointerId(i), x(ev, i), y(ev, i), t, first = false)
            }
            MotionEvent.ACTION_MOVE -> {
                val n = minOf(ev.pointerCount, GestureTracker.MAX)
                for (k in 0 until n) { pid[k] = ev.getPointerId(k); px[k] = x(ev, k); py[k] = y(ev, k) }
                tracker.move(n, pid, px, py, t)
            }
            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP -> {
                val i = ev.actionIndex
                tracker.up(ev.getPointerId(i), x(ev, i), y(ev, i), t)
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
    /** PICK single tap, GLASS two-finger tap, LONG_PRESS hold (then [drainHeld]), ZOOM double tap. */
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
    /** A fingertip's radius (px) for forgiving picks ([Picker.fingerSlack]); set by the host from its touch slop. */
    @Volatile var fingerPx = 24f
    private var scrapeX = 0f; private var scrapeY = 0f; private var scrapeDown = false

    /** Clean-glass mode: where the finger is and whether it is on the screen. */
    @Synchronized fun scrapeTouch(x: Float, y: Float, down: Boolean) { scrapeX = x; scrapeY = y; scrapeDown = down }

    /** GL thread: the finger position (px) while it is down, else null. */
    @Synchronized fun scrapePointer(out: FloatArray): Boolean {
        if (!scrapeDown) return false
        out[0] = scrapeX; out[1] = scrapeY
        return true
    }

    // ---- a knob in the scene under the finger (MusicCorner: the radio's volume knob): its drag turns it, not the view
    /** The knob on screen (px; radius 0 = none to grab). Set by the GL thread, read on the first touch. */
    @Volatile var knobX = 0f
    @Volatile var knobY = 0f
    @Volatile var knobR = 0f
    private var grabbing = false
    /** This gesture started on the knob: its fling is dropped too. */
    private var grabGesture = false
    private var grabDx = 0f; private var grabDy = 0f
    private var grabMoved = false
    private var grabEnded = false

    /** The first finger went down at ([x], [y]) px: on the knob, the drag that follows turns it. */
    @Synchronized fun press(x: Float, y: Float) {
        val r = knobR
        val dx = x - knobX; val dy = y - knobY
        grabbing = r > 0f && dx * dx + dy * dy <= (r + fingerPx * 0.5f) * (r + fingerPx * 0.5f)
        grabGesture = grabbing
    }

    /** GL thread: the knob drag since the last frame into [out] (dx, dy px); bit 1 = moved, bit 2 = the finger lifted. */
    @Synchronized fun drainGrab(out: FloatArray): Int {
        if (!grabMoved && !grabEnded) return 0
        out[0] = grabDx; out[1] = grabDy
        val r = (if (grabMoved) 1 else 0) or (if (grabEnded) 2 else 0)
        grabDx = 0f; grabDy = 0f; grabMoved = false; grabEnded = false
        return r
    }

    @Synchronized override fun touching(down: Boolean) {
        this.down = down
        if (down) caught = true
        else if (grabbing) { grabbing = false; grabEnded = true }
    }
    @Synchronized override fun orbit(dx: Float, dy: Float) {
        if (grabbing) { grabDx += dx; grabDy += dy; grabMoved = true; return }
        orbitX += dx; orbitY += dy
    }
    @Synchronized override fun pan(dx: Float, dy: Float) { panX += dx; panY += dy }
    @Synchronized override fun pinch(factor: Float, focusX: Float, focusY: Float) {
        if (factor > 0f && !factor.isNaN() && !factor.isInfinite()) { zoom *= factor; this.focusX = focusX; this.focusY = focusY }
    }
    @Synchronized override fun rotate(radians: Float) { rotation += radians }
    @Synchronized override fun fling(vx: Float, vy: Float) { if (!grabGesture) { flingX = vx; flingY = vy; hasFling = true } }
    @Synchronized override fun tap(kind: TapKind, x: Float, y: Float) { if (taps.size < 8) taps += Tap(kind, x, y) }

    // ---- the held finger (after a long press): the GL thread decides whether it is a fingertip or an orbit
    private var heldDx = 0f; private var heldDy = 0f
    private var heldX = 0f; private var heldY = 0f
    private var heldMoved = false
    private var heldLifted = false
    private var heldVx = 0f; private var heldVy = 0f

    @Synchronized override fun heldMove(dx: Float, dy: Float, x: Float, y: Float) {
        // pressed and held on the knob, then turned: still the knob
        if (grabbing) { grabDx += dx; grabDy += dy; grabMoved = true; return }
        heldDx += dx; heldDy += dy; heldX = x; heldY = y; heldMoved = true
    }
    @Synchronized override fun heldUp(x: Float, y: Float, vx: Float, vy: Float) {
        if (grabGesture) return
        heldX = x; heldY = y; heldVx = vx; heldVy = vy; heldLifted = true
    }

    /**
     * GL thread, after [drain] (so a long press in the same frame is handled first): what the held finger did since
     * the last frame. [out] gets dx, dy, x, y, vx, vy; returns bit 1 = moved, bit 2 = lifted (0 = nothing).
     */
    @Synchronized fun drainHeld(out: FloatArray): Int {
        if (!heldMoved && !heldLifted) return 0
        out[0] = heldDx; out[1] = heldDy; out[2] = heldX; out[3] = heldY; out[4] = heldVx; out[5] = heldVy
        val r = (if (heldMoved) 1 else 0) or (if (heldLifted) 2 else 0)
        heldDx = 0f; heldDy = 0f; heldMoved = false; heldLifted = false; heldVx = 0f; heldVy = 0f
        return r
    }

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
