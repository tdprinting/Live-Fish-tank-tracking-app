package com.reeflive.app.render

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Pure multi-touch gesture recogniser for the tank camera (ARCHITECTURE §8.2 gestures). [TouchController] feeds it
 * the raw pointer stream (ids, positions in px, event time in ms) and it reports camera motion and taps to [Sink]:
 *
 * - one finger: orbit once it has moved past [touchSlop] (the whole move since touch-down is applied, so nothing is
 *   lost to the slop); a release while moving flings. Holding still does not lock the drag out: after a long press
 *   fires, moving the same finger still orbits.
 * - two or more fingers: pan (centroid), pinch (mean span ratio, about the centroid) and twist-to-rotate (after a
 *   small dead zone so a pinch does not also yaw). No minimum finger span. Every change in the number of fingers
 *   re-bases the deltas, so lifting one of two fingers carries straight on as a one-finger orbit, with no jump.
 * - single tap (confirmed after [DOUBLE_TAP_MS]); double tap (ZOOM); double-tap-and-drag: a one-finger zoom
 *   (drag down = in); two-finger tap (GLASS); long press (LONG_PRESS).
 *
 * No Android types, no allocation per event.
 */
class GestureTracker(private val sink: Sink, var touchSlop: Float = 16f, var doubleTapSlop: Float = 100f) {

    interface Sink {
        fun touching(down: Boolean)
        fun orbit(dx: Float, dy: Float)
        fun pan(dx: Float, dy: Float)
        fun pinch(factor: Float, focusX: Float, focusY: Float)
        fun rotate(radians: Float)
        /** Release velocity of a one-finger drag, px/s. */
        fun fling(vx: Float, vy: Float)
        fun tap(kind: CameraInput.TapKind, x: Float, y: Float)
    }

    // ---- pointers currently down
    private val ids = IntArray(MAX)
    private val xs = FloatArray(MAX)
    private val ys = FloatArray(MAX)
    var count = 0; private set

    // ---- current gesture (first finger down → last finger up)
    private var downT = 0L
    private var downX = 0f; private var downY = 0f
    private var maxPointers = 0
    private var dragging = false
    private var quickZoom = false
    private var secondTap = false
    private var longPressArmed = false
    private var longPressFired = false
    private var multiMoved = 0f
    private var multiT = 0L
    private var multiX = 0f; private var multiY = 0f
    private var rotating = false
    private var twist = 0f

    // ---- baselines for incremental deltas
    private var lastX = 0f; private var lastY = 0f
    private var lastCx = 0f; private var lastCy = 0f; private var lastSpan = 0f; private var lastAngle = 0f

    // ---- single tap waiting for a possible second tap
    private var pendingTap = false
    private var pendingT = 0L
    private var pendingX = 0f; private var pendingY = 0f

    // ---- one-finger velocity samples (ring buffer)
    private val st = LongArray(VEL_N)
    private val sx = FloatArray(VEL_N)
    private val sy = FloatArray(VEL_N)
    private var sHead = 0
    private var sCount = 0

    /** A finger went down. [first] = ACTION_DOWN: any pointers left over from a lost UP/CANCEL are dropped. */
    fun down(id: Int, x: Float, y: Float, t: Long, first: Boolean) {
        if (first && count > 0) count = 0
        if (count == 0) startGesture(x, y, t)
        if (indexOf(id) < 0 && count < MAX) { ids[count] = id; xs[count] = x; ys[count] = y; count++ }
        if (count > maxPointers) maxPointers = count
        if (count >= 2) {
            longPressArmed = false
            quickZoom = false
            secondTap = false
            if (count == 2) { multiT = t; multiMoved = 0f; rotating = false; twist = 0f; centroid(); multiX = cx; multiY = cy }
        }
        rebase()
        if (count == 1) sample(x, y, t)
    }

    /** Positions of the pointers in an ACTION_MOVE (n pointers). Unknown ids (a lost POINTER_DOWN) are adopted. */
    fun move(n: Int, pid: IntArray, px: FloatArray, py: FloatArray, t: Long) {
        var adopted = false
        for (k in 0 until n) {
            val i = indexOf(pid[k])
            if (i >= 0) { xs[i] = px[k]; ys[i] = py[k] }
            else if (count < MAX) { ids[count] = pid[k]; xs[count] = px[k]; ys[count] = py[k]; count++; adopted = true }
        }
        if (count == 0) return
        if (adopted) {
            if (count > maxPointers) maxPointers = count
            if (count >= 2) { longPressArmed = false; quickZoom = false; secondTap = false }
            rebase()
            return
        }
        if (count == 1) moveOne(t) else moveMany()
    }

    private fun moveOne(t: Long) {
        val x = xs[0]; val y = ys[0]
        if (!dragging) {
            val dx = x - downX; val dy = y - downY
            if (dx * dx + dy * dy <= touchSlop * touchSlop) return
            dragging = true
            longPressArmed = false
            if (secondTap) quickZoom = true
        }
        val dx = x - lastX; val dy = y - lastY
        lastX = x; lastY = y
        if (quickZoom) {
            sink.pinch(exp(dy * QUICK_ZOOM_PER_PX), downX, downY)
        } else {
            if (dx != 0f || dy != 0f) sink.orbit(dx, dy)
            sample(x, y, t)
        }
    }

    private fun moveMany() {
        centroid()
        val span = span(cx, cy)
        val angle = atan2(ys[1] - ys[0], xs[1] - xs[0])
        val dcx = cx - lastCx; val dcy = cy - lastCy
        multiMoved += abs(dcx) + abs(dcy) + abs(span - lastSpan)
        if (dcx != 0f || dcy != 0f) sink.pan(dcx, dcy)
        if (lastSpan > MIN_SPAN && span > MIN_SPAN && span != lastSpan) sink.pinch(span / lastSpan, cx, cy)
        val da = Camera.wrapAngle(angle - lastAngle)
        if (rotating) {
            if (da != 0f) sink.rotate(da)
        } else {
            twist += da
            if (abs(twist) > ROTATE_START) rotating = true
        }
        lastCx = cx; lastCy = cy; lastSpan = span; lastAngle = angle
    }

    /** A finger lifted (ACTION_POINTER_UP or ACTION_UP). */
    fun up(id: Int, x: Float, y: Float, t: Long) {
        val i = indexOf(id)
        if (i < 0) return
        xs[i] = x; ys[i] = y
        val wasCount = count
        if (wasCount == 1) sample(x, y, t)
        for (k in i until count - 1) { ids[k] = ids[k + 1]; xs[k] = xs[k + 1]; ys[k] = ys[k + 1] }
        count--
        if (count == 0) { finish(t); return }
        if (wasCount >= 2 && count == 1) {
            // carry straight on as a one-finger orbit
            dragging = true
            sCount = 0
        }
        rebase()
    }

    fun cancel() {
        val was = count > 0
        count = 0
        pendingTap = false
        longPressArmed = false
        dragging = false
        quickZoom = false
        if (was) sink.touching(false)
    }

    /**
     * Fires a due long press or a confirmed single tap. Returns the next time (ms) [timer] should run, or −1 when
     * nothing is pending.
     */
    fun timer(t: Long): Long {
        var next = -1L
        if (longPressArmed && count == 1 && !dragging) {
            val due = downT + LONG_PRESS_MS
            if (t >= due) {
                longPressArmed = false
                longPressFired = true
                sink.tap(CameraInput.TapKind.LONG_PRESS, downX, downY)
            } else next = due
        }
        if (pendingTap) {
            val due = pendingT + DOUBLE_TAP_MS
            if (t >= due) {
                pendingTap = false
                sink.tap(CameraInput.TapKind.PICK, pendingX, pendingY)
            } else next = if (next < 0) due else minOf(next, due)
        }
        return next
    }

    // ------------------------------------------------------------------ internals

    private fun startGesture(x: Float, y: Float, t: Long) {
        secondTap = pendingTap && t - pendingT <= DOUBLE_TAP_MS && dist2(x, y, pendingX, pendingY) <= doubleTapSlop * doubleTapSlop
        if (pendingTap && !secondTap) {
            // a new touch elsewhere confirms the earlier tap right away
            sink.tap(CameraInput.TapKind.PICK, pendingX, pendingY)
        }
        pendingTap = false
        sink.touching(true)
        downT = t; downX = x; downY = y
        maxPointers = 0
        dragging = false
        quickZoom = false
        longPressArmed = !secondTap
        longPressFired = false
        multiMoved = 0f
        rotating = false; twist = 0f
        sCount = 0
    }

    private fun finish(t: Long) {
        sink.touching(false)
        longPressArmed = false
        val held = t - downT
        when {
            maxPointers >= 2 && multiMoved <= touchSlop && t - multiT <= TWO_FINGER_TAP_MS ->
                sink.tap(CameraInput.TapKind.GLASS, multiX, multiY)
            maxPointers == 1 && !dragging && !longPressFired && held < LONG_PRESS_MS -> {
                if (secondTap) sink.tap(CameraInput.TapKind.ZOOM, downX, downY)
                else { pendingTap = true; pendingT = t; pendingX = downX; pendingY = downY }
            }
            dragging && !quickZoom -> {
                velocity(t)
                if (vxOut * vxOut + vyOut * vyOut >= MIN_FLING * MIN_FLING) sink.fling(vxOut, vyOut)
            }
        }
        dragging = false
        quickZoom = false
        secondTap = false
    }

    private fun rebase() {
        if (count == 1) { lastX = xs[0]; lastY = ys[0] }
        else if (count >= 2) {
            centroid()
            lastCx = cx; lastCy = cy
            lastSpan = span(cx, cy)
            lastAngle = atan2(ys[1] - ys[0], xs[1] - xs[0])
        }
    }

    private var cx = 0f
    private var cy = 0f

    private fun centroid() {
        var ax = 0f; var ay = 0f
        for (k in 0 until count) { ax += xs[k]; ay += ys[k] }
        cx = ax / count; cy = ay / count
    }

    private fun span(cx: Float, cy: Float): Float {
        var s = 0f
        for (k in 0 until count) { val dx = xs[k] - cx; val dy = ys[k] - cy; s += sqrt(dx * dx + dy * dy) }
        return s / count
    }

    private fun indexOf(id: Int): Int {
        for (k in 0 until count) if (ids[k] == id) return k
        return -1
    }

    private fun sample(x: Float, y: Float, t: Long) {
        st[sHead] = t; sx[sHead] = x; sy[sHead] = y
        sHead = (sHead + 1) % VEL_N
        if (sCount < VEL_N) sCount++
    }

    private var vxOut = 0f
    private var vyOut = 0f

    /** Release velocity (px/s) from the samples of the last [VEL_WINDOW_MS]; zero when the finger rested. */
    private fun velocity(tUp: Long) {
        vxOut = 0f; vyOut = 0f
        if (sCount < 2) return
        val newest = (sHead - 1 + VEL_N) % VEL_N
        if (tUp - st[newest] > REST_MS) return
        var oldest = newest
        for (k in 1 until sCount) {
            val i = (newest - k + VEL_N) % VEL_N
            if (st[newest] - st[i] > VEL_WINDOW_MS) break
            oldest = i
        }
        val dt = (st[newest] - st[oldest]).toFloat()
        if (dt < 8f) return
        vxOut = (sx[newest] - sx[oldest]) / dt * 1000f
        vyOut = (sy[newest] - sy[oldest]) / dt * 1000f
    }

    private fun dist2(ax: Float, ay: Float, bx: Float, by: Float): Float { val dx = ax - bx; val dy = ay - by; return dx * dx + dy * dy }

    companion object {
        const val MAX = 10
        const val LONG_PRESS_MS = 450L
        const val DOUBLE_TAP_MS = 260L
        const val TWO_FINGER_TAP_MS = 300L
        /** Twist (rad) before two fingers start rotating, so a plain pinch never yaws the camera. */
        const val ROTATE_START = 0.14f
        /** Below this mean finger distance (px) the pinch ratio is too noisy to use. */
        const val MIN_SPAN = 4f
        /** Double-tap-and-drag zoom: factor e^(dy/220) per px (drag down zooms in). */
        const val QUICK_ZOOM_PER_PX = 1f / 220f
        const val MIN_FLING = 180f
        const val VEL_N = 8
        const val VEL_WINDOW_MS = 100L
        const val REST_MS = 70L
    }
}
