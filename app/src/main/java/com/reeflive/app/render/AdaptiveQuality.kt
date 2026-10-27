package com.reeflive.app.render

import kotlin.math.min

/**
 * Auto picture quality for the Android live tank (the web page has its own dynamic resolution in web/Main.kt, which this
 * follows). Fed every frame's wall interval on the GL thread ([onFrame]); over each [WINDOW_FRAMES]-frame window a mean
 * interval well over the frame cap's period means frames are being missed, and the picture steps one [RUNGS] rung
 * cheaper; [GOOD_WINDOWS] windows in a row at the cap step it one rung back up.
 *
 * The ladder sheds render resolution first (the display scales the smaller buffer up; fill rate is what limits phones),
 * then effects through [relief] ([TankRenderer.relief]: fish out of the shadow map, fewer contact shadows and snow, no
 * light shafts, cheaper rock / sand / caustic shading, no shadow map at all), then a last resolution step. Nothing on
 * the ladder reloads a model or recompiles a shader, so a step costs no hitch.
 *
 * Guards (as on the web): each frame counts at most 0.1 s, so a single hitch cannot step it down; a step up that is slow
 * again straight away goes back down and that rung is left alone for a while (doubling each time, [BOUNCE_S]); and if
 * two steps down did not make frames at least 7 % faster, pixels were not the limit (the CPU or a 30 fps power cap is),
 * so the picture goes back to where it was and holds for [LOCK_S].
 *
 * Pure Kotlin (shared with the web build), no allocation.
 */
class AdaptiveQuality {
    class Rung(val scale: Float, val relief: Int)

    /** Current rung (0 = the preset as picked). */
    var rung = 0; private set
    /** Multiplier on the preset's own render scale. */
    val scale: Float get() = RUNGS[rung].scale
    /** Effects shed without a reload, 0 … [MAX_RELIEF]. */
    val relief: Int get() = RUNGS[rung].relief
    /** Smoothed mean frame interval of the last window (s), for the debug overlay. */
    var lastMean = 0f; private set

    private var grace = START_GRACE
    private var winT = 0f
    private var winN = 0
    private var good = 0
    private var probing = false
    private var probeWindows = 0
    /** While [ceilingLeft] runs, no step up past (above) rung [ceiling]. */
    private var ceiling = 0
    private var ceilingLeft = 0f
    private var bounce = BOUNCE_S
    // the two-step "did fewer pixels help?" check
    private var tryBase = -1f
    private var tryFrom = 0
    private var trySteps = 0
    private var lockLeft = 0f

    /** Back to the top rung (a preset picked by hand, Auto switched on again, a new view). */
    fun reset(start: Int = 0) {
        rung = start.coerceIn(0, RUNGS.size - 1)
        grace = START_GRACE; winT = 0f; winN = 0; good = 0
        probing = false; ceilingLeft = 0f; bounce = BOUNCE_S
        tryBase = -1f; lockLeft = 0f
    }

    /** Hold judgement for [seconds] (a loading burst, a surface change): those frames say nothing about the device. */
    fun hold(seconds: Float) { grace = maxOf(grace, seconds); winT = 0f; winN = 0 }

    /**
     * One frame drawn [dt] seconds after the previous one, with frames capped at [targetFps]. True when the rung
     * changed (read [scale] / [relief]).
     */
    fun onFrame(dt: Float, targetFps: Int): Boolean {
        if (dt <= 0f || targetFps <= 0) return false
        if (ceilingLeft > 0f) ceilingLeft -= dt
        if (lockLeft > 0f) lockLeft -= dt
        if (grace > 0f) { grace -= dt; return false }
        winT += min(dt, 0.1f); winN++
        if (winN < WINDOW_FRAMES) return false
        val mean = winT / winN
        lastMean = mean
        winT = 0f; winN = 0
        val target = 1f / targetFps
        if (mean > target * SLOW) {
            good = 0
            if (probing) {
                // the step up did not hold: back down, and leave the richer rung alone for a while
                probing = false
                ceiling = rung + 1; ceilingLeft = bounce; bounce = min(bounce * 2f, BOUNCE_MAX_S)
                return step(rung + 1)
            }
            if (lockLeft > 0f || rung >= RUNGS.size - 1) return false
            if (tryBase < 0f) { tryBase = mean; tryFrom = rung; trySteps = 0 }
            else if (trySteps >= 2) {
                if (mean > tryBase * 0.93f) {
                    // two cheaper rungs and no faster: not the pixels; put the picture back and stop trading it away
                    val back = tryFrom
                    tryBase = -1f; lockLeft = LOCK_S
                    return step(back)
                }
                tryBase = mean; tryFrom = rung; trySteps = 0
            }
            trySteps++
            return step(rung + 1)
        }
        tryBase = -1f
        if (probing && ++probeWindows >= PROBE_WINDOWS) probing = false
        if (mean < target * FAST) {
            if (++good >= GOOD_WINDOWS && rung > 0 && (ceilingLeft <= 0f || rung - 1 >= ceiling)) {
                good = 0; probing = true; probeWindows = 0
                return step(rung - 1)
            }
        } else good = 0
        return false
    }

    private fun step(to: Int): Boolean {
        val t = to.coerceIn(0, RUNGS.size - 1)
        if (t == rung) return false
        rung = t
        grace = SWITCH_GRACE; winT = 0f; winN = 0
        return true
    }

    companion object {
        /** Highest [relief]. */
        const val MAX_RELIEF = 3
        val RUNGS = arrayOf(
            Rung(1f, 0), Rung(0.88f, 0), Rung(0.77f, 0),
            Rung(0.77f, 1), Rung(0.67f, 1), Rung(0.67f, 2), Rung(0.67f, 3),
            Rung(0.56f, 3),
        )
        const val WINDOW_FRAMES = 30
        /** Mean interval over the cap's period that counts as missing frames (60 → under ~51 fps; 30 → under ~25). */
        const val SLOW = 1.18f
        /** Mean interval under which the frame cap is being met. */
        const val FAST = 1.06f
        const val GOOD_WINDOWS = 6
        /** Windows after a step up in which a slow window counts as that step failing. */
        const val PROBE_WINDOWS = 4
        const val START_GRACE = 4f
        const val SWITCH_GRACE = 1.5f
        const val BOUNCE_S = 30f
        const val BOUNCE_MAX_S = 240f
        const val LOCK_S = 90f
    }
}
