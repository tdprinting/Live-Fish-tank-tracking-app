package com.reeflive.app.render

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector

/**
 * Gestures (ARCHITECTURE §8.2): 1-finger orbit, 2-finger pan + pinch zoom, tap (pick), double tap
 * (tap the glass), long press (drop food). In clean-glass mode ([CameraInput.cleaning]) the first finger slides the
 * Flipper instead and the other gestures are off. Runs on the UI thread and only accumulates into
 * [CameraInput]; the GL thread consumes it once per frame.
 */
class TouchController(context: Context, private val input: CameraInput) {
    private var lastFocusX = 0f
    private var lastFocusY = 0f
    private var multi = false

    private val scale = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(d: ScaleGestureDetector): Boolean {
            lastFocusX = d.focusX; lastFocusY = d.focusY
            return true
        }

        override fun onScale(d: ScaleGestureDetector): Boolean {
            input.zoom(d.scaleFactor)
            input.pan(d.focusX - lastFocusX, d.focusY - lastFocusY)
            lastFocusX = d.focusX; lastFocusY = d.focusY
            return true
        }
    })

    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            if (multi || e2.pointerCount > 1) return false
            input.orbit(-dx, -dy)
            return true
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean { input.tap(CameraInput.TapKind.PICK, e.x, e.y); return true }
        override fun onDoubleTap(e: MotionEvent): Boolean { input.tap(CameraInput.TapKind.GLASS, e.x, e.y); return true }
        override fun onLongPress(e: MotionEvent) { if (!multi) input.tap(CameraInput.TapKind.LONG_PRESS, e.x, e.y) }
    })

    fun onTouchEvent(ev: MotionEvent): Boolean {
        if (input.cleaning) {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> input.scrapeTouch(ev.getX(0), ev.getY(0), true)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> input.scrapeTouch(ev.getX(0), ev.getY(0), false)
            }
            return true
        }
        when (ev.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> multi = true
            MotionEvent.ACTION_DOWN -> multi = false
        }
        scale.onTouchEvent(ev)
        gestures.onTouchEvent(ev)
        return true
    }
}

/** Thread-safe accumulator between the UI thread (gestures) and the GL thread (camera). */
class CameraInput {
    enum class TapKind { PICK, GLASS, LONG_PRESS }
    class Tap(val kind: TapKind, val x: Float, val y: Float)

    private var orbitX = 0f; private var orbitY = 0f
    private var panX = 0f; private var panY = 0f
    private var zoom = 1f
    private val taps = ArrayList<Tap>()

    /** Clean-glass mode: set from the UI thread, read by [TouchController] and the GL thread. */
    @Volatile var cleaning = false
    private var scrapeX = 0f; private var scrapeY = 0f; private var scrapeDown = false

    /** Clean-glass mode: where the finger is and whether it is on the screen. */
    @Synchronized fun scrapeTouch(x: Float, y: Float, down: Boolean) { scrapeX = x; scrapeY = y; scrapeDown = down }

    /** GL thread: the finger position (px) while it is down, else null. */
    @Synchronized fun scrapePointer(out: FloatArray): Boolean {
        if (!scrapeDown) return false
        out[0] = scrapeX; out[1] = scrapeY
        return true
    }

    @Synchronized fun orbit(dx: Float, dy: Float) { orbitX += dx; orbitY += dy }
    @Synchronized fun pan(dx: Float, dy: Float) { panX += dx; panY += dy }
    @Synchronized fun zoom(f: Float) { if (f > 0f) zoom *= f }
    @Synchronized fun tap(kind: TapKind, x: Float, y: Float) { if (taps.size < 8) taps += Tap(kind, x, y) }

    /** GL thread: applies accumulated motion to [cam] and returns pending taps. */
    @Synchronized fun drain(cam: Camera, viewHeightPx: Float): List<Tap> {
        if (orbitX != 0f || orbitY != 0f) cam.orbit(orbitX, orbitY, viewHeightPx)
        if (panX != 0f || panY != 0f) cam.pan(panX, panY, viewHeightPx)
        if (zoom != 1f) cam.zoom(zoom)
        orbitX = 0f; orbitY = 0f; panX = 0f; panY = 0f; zoom = 1f
        if (taps.isEmpty()) return emptyList()
        return ArrayList(taps).also { taps.clear() }
    }
}
