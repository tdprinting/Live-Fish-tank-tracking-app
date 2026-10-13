package com.reeflive.app.render

import kotlin.math.exp

/**
 * Follow-mode lens state (GL thread): eases the effect in and out and tracks where the followed fish is on screen.
 * Pure Kotlin. [amount] 0..1 drives PostFx's depth-of-field blur, vignette and chromatic aberration.
 */
class LensState {
    var amount = 0f; private set
    /** Focus centre (u, v) and radius (v units) in texture space; kept at the last good value when the fish is off screen. */
    val focus = floatArrayOf(0.5f, 0.5f, 0.2f)
    private val tmp = FloatArray(3)

    /**
     * [on]: following with the lens allowed. [rig]: the followed organism (null keeps the last focus). Fades at
     * [FADE_RATE] (1/s), frame-rate independent.
     */
    fun update(dt: Float, on: Boolean, rig: FishRig?, camera: Camera) {
        val goal = if (on && rig != null) 1f else 0f
        val a = 1f - exp(-FADE_RATE * dt.coerceIn(0f, 0.1f))
        amount += (goal - amount) * a
        if (amount < 1e-3f && goal == 0f) amount = 0f
        if (rig != null && camera.projectSphere(rig.spine[24], rig.spine[25], rig.spine[26], rig.scale * FOCUS_RADIUS_BL, tmp)) {
            // ease the focus too, so a jerky dart does not snap the sharp zone
            val k = 1f - exp(-FOCUS_RATE * dt.coerceIn(0f, 0.1f))
            for (i in 0 until 3) focus[i] += (tmp[i].coerceIn(-0.5f, 1.5f) - focus[i]) * k
            focus[2] = focus[2].coerceIn(0.04f, 0.6f)
        }
    }

    companion object {
        const val FADE_RATE = 2.6f
        const val FOCUS_RATE = 12f
        /** Sharp zone radius as a fraction of body length (half a body plus room for the fins and tail). */
        const val FOCUS_RADIUS_BL = 0.75f
    }
}
