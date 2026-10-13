package com.reeflive.app.render

import kotlin.math.abs
import kotlin.math.exp

/**
 * Critically damped spring on one scalar (ARCHITECTURE §8.2 camera feel). [step] uses the exact solution of
 * x'' = −2ωx' − ω²x, so it is stable and frame-rate independent for any dt and never overshoots from rest.
 * It settles to ~1 % in ≈ 6.6 / [omega] seconds. Pure Kotlin, no allocation.
 */
class Spring(var omega: Float) {
    var value = 0f
    var velocity = 0f

    fun snap(v: Float) { value = v; velocity = 0f }

    fun step(target: Float, dt: Float): Float {
        if (dt <= 0f) return value
        val w = omega
        val x0 = value - target
        val e = exp(-w * dt)
        val c = velocity + w * x0
        val x1 = (x0 + c * dt) * e
        velocity = (c - w * (x0 + c * dt)) * e
        value = target + x1
        return value
    }
}

/** Three [Spring]s sharing one stiffness: a smoothed point (eye / aim of the follow camera). */
class Spring3(omega: Float) {
    private val s = arrayOf(Spring(omega), Spring(omega), Spring(omega))
    var omega: Float
        get() = s[0].omega
        set(v) { s[0].omega = v; s[1].omega = v; s[2].omega = v }

    operator fun get(i: Int): Float = s[i].value

    fun snap(p: FloatArray) { for (i in 0 until 3) s[i].snap(p[i]) }

    fun step(target: FloatArray, dt: Float, out: FloatArray) { for (i in 0 until 3) out[i] = s[i].step(target[i], dt) }

    /** Copies the current value into [out]. */
    fun valueInto(out: FloatArray) { for (i in 0 until 3) out[i] = s[i].value }
}

/**
 * Soft limits: instead of a hard clamp a value can be pushed a little past its range against a growing
 * resistance (rubber band), and eases back inside once the finger lets go. Pure functions.
 */
object SoftLimit {
    /**
     * Applies a user [delta] to [value] with range [lo]..[hi]. Movement back toward the range is free; movement
     * further out is scaled by 1 / (1 + overshoot / [give])², so the value can never get more than about 1.5 ×
     * [give] outside however far the finger travels.
     */
    fun drag(value: Float, delta: Float, lo: Float, hi: Float, give: Float): Float {
        if (delta == 0f) return value
        val outward = (value >= hi && delta > 0f) || (value <= lo && delta < 0f)
        if (!outward) {
            // crossing the limit in one big step: apply the in-range part fully, resist the rest
            val next = value + delta
            return when {
                next > hi && value <= hi -> drag(hi, next - hi, lo, hi, give)
                next < lo && value >= lo -> drag(lo, next - lo, lo, hi, give)
                else -> next
            }
        }
        val over = if (value > hi) value - hi else lo - value
        val k = 1f / ((1f + over / give) * (1f + over / give))
        val next = value + delta * k
        return next.coerceIn(lo - give * 1.5f, hi + give * 1.5f)
    }

    /** Eases a value that is outside lo..hi back toward the range: exponential, frame-rate independent. */
    fun relax(value: Float, lo: Float, hi: Float, rate: Float, dt: Float): Float {
        val c = value.coerceIn(lo, hi)
        if (c == value) return value
        val next = c + (value - c) * exp(-rate * dt)
        return if (abs(next - c) < 1e-4f) c else next
    }

    /** How far outside lo..hi the value is (0 inside). */
    fun overshoot(value: Float, lo: Float, hi: Float): Float = when {
        value > hi -> value - hi
        value < lo -> lo - value
        else -> 0f
    }
}

/**
 * Fling momentum: a velocity that decays exponentially ([friction] 1/s), frame-rate independent. [advance]
 * returns the displacement over dt (the exact integral of the decaying velocity).
 */
class Inertia(var friction: Float) {
    var velocity = 0f
        private set

    fun kick(v: Float) { velocity = v }
    fun stop() { velocity = 0f }
    val active: Boolean get() = abs(velocity) > 1e-3f

    fun advance(dt: Float, extraFriction: Float = 0f): Float {
        if (!active) { velocity = 0f; return 0f }
        if (dt <= 0f) return 0f
        val f = friction + extraFriction
        val e = exp(-f * dt)
        val d = velocity * (1f - e) / f
        velocity *= e
        return d
    }
}
