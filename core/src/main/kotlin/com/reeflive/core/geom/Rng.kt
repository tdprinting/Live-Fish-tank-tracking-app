package com.reeflive.core.geom

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * xorshift128+ (SIMULATION §4.9). All simulation randomness goes through one of these so a seed
 * reproduces trajectories exactly. Seeds are spread with splitmix64 so small seeds behave well.
 */
class Rng(seed: Long) {
    private var s0: Long
    private var s1: Long

    init {
        var z = seed
        s0 = splitmix(z).also { z += GOLDEN }
        s1 = splitmix(z)
        if (s0 == 0L && s1 == 0L) s1 = 1L
    }

    fun nextLong(): Long {
        var x = s0
        val y = s1
        s0 = y
        x = x xor (x shl 23)
        s1 = x xor y xor (x ushr 17) xor (y ushr 26)
        return s1 + y
    }

    /** Uniform in [0, 1). */
    fun nextDouble(): Double = (nextLong() ushr 11) * (1.0 / (1L shl 53))

    fun range(lo: Double, hi: Double) = lo + (hi - lo) * nextDouble()
    fun nextInt(n: Int): Int = ((nextLong() ushr 33) % n).toInt()
    fun chance(p: Double) = nextDouble() < p
    fun signed() = nextDouble() * 2 - 1

    fun gaussian(): Double {
        val u1 = nextDouble().coerceAtLeast(1e-12)
        val u2 = nextDouble()
        return sqrt(-2 * ln(u1)) * cos(2 * PI * u2)
    }

    fun unitVector(): Vec3 {
        while (true) {
            val v = Vec3(signed(), signed(), signed())
            val l = v.lengthSq()
            if (l in 1e-6..1.0) return v / sqrt(l)
        }
    }

    /** Child generator with an independent stream (for per-organism temperament etc.). */
    fun fork(salt: Long): Rng = Rng(nextLong() xor salt)

    private companion object {
        const val GOLDEN = -0x61c8864680b583ebL
        fun splitmix(seed: Long): Long {
            var z = seed + GOLDEN
            z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
            z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
            return z xor (z ushr 31)
        }
    }
}
