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
    // The two 64-bit state words as 32-bit halves (h = high, l = low): Long is emulated in the web build, and the
    // sim draws thousands of numbers per tick. The arithmetic below is xorshift128+ exactly, word for word.
    private var s0h: Int; private var s0l: Int
    private var s1h: Int; private var s1l: Int
    // the last output's halves
    private var rh = 0; private var rl = 0

    init {
        var z = seed
        val a = splitmix(z).also { z += GOLDEN }
        var b = splitmix(z)
        if (a == 0L && b == 0L) b = 1L
        s0h = (a ushr 32).toInt(); s0l = a.toInt()
        s1h = (b ushr 32).toInt(); s1l = b.toInt()
    }

    private fun step() {
        var xh = s0h; var xl = s0l
        val yh = s1h; val yl = s1l
        s0h = yh; s0l = yl
        // x ^= x << 23
        xh = xh xor ((xh shl 23) or (xl ushr 9)); xl = xl xor (xl shl 23)
        // s1 = x ^ y ^ (x >>> 17) ^ (y >>> 26)
        s1h = xh xor yh xor (xh ushr 17) xor (yh ushr 26)
        s1l = xl xor yl xor ((xl ushr 17) or (xh shl 15)) xor ((yl ushr 26) or (yh shl 6))
        // result = s1 + y
        rl = s1l + yl
        val carry = if ((rl xor Int.MIN_VALUE) < (s1l xor Int.MIN_VALUE)) 1 else 0
        rh = s1h + yh + carry
    }

    fun nextLong(): Long { step(); return (rh.toLong() shl 32) or (rl.toLong() and 0xFFFFFFFFL) }

    /** Uniform in [0, 1). */
    fun nextDouble(): Double {
        step()
        // (result >>> 11) / 2^53: the top 53 bits, exact in a Double
        return ((rh.toDouble() + (if (rh < 0) 4294967296.0 else 0.0)) * 2097152.0 + (rl ushr 11).toDouble()) * (1.0 / 9007199254740992.0)
    }

    fun range(lo: Double, hi: Double) = lo + (hi - lo) * nextDouble()
    fun nextInt(n: Int): Int { step(); return (rh ushr 1) % n }
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
