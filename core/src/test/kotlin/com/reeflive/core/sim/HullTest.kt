package com.reeflive.core.sim

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.sqrt
import kotlin.random.Random

/** [Hull]'s allocation-free sort answers exactly as the boxed `sortedWith(compareBy(x, z))` version it replaced. */
class HullTest {
    /** The previous implementation, verbatim apart from its scratch arrays. */
    private fun reference(xs: DoubleArray, zs: DoubleArray, n: Int, px: Double, pz: Double, margin: Double): Boolean {
        if (n < 3) return false
        val idx = IntArray(1024); val hx = DoubleArray(1024); val hz = DoubleArray(1024)
        val ids = (0 until n).sortedWith(compareBy<Int>({ xs[it] }, { zs[it] }))
        var k = 0
        fun cross(o: Int, a: Int, b: Int) = (xs[a] - xs[o]) * (zs[b] - zs[o]) - (zs[a] - zs[o]) * (xs[b] - xs[o])
        for (i in ids) { while (k >= 2 && cross(idx[k - 2], idx[k - 1], i) <= 0) k--; idx[k++] = i }
        val lower = k + 1
        for (t in ids.indices.reversed().drop(1)) { val i = ids[t]; while (k >= lower && cross(idx[k - 2], idx[k - 1], i) <= 0) k--; idx[k++] = i }
        val m = k - 1
        if (m < 3) return false
        for (t in 0 until m) { hx[t] = xs[idx[t]]; hz[t] = zs[idx[t]] }
        for (t in 0 until m) {
            val ax = hx[t]; val az = hz[t]; val bx = hx[(t + 1) % m]; val bz = hz[(t + 1) % m]
            val ex = bx - ax; val ez = bz - az
            val l = sqrt(ex * ex + ez * ez)
            if (l < 1e-9) continue
            val side = (ex * (pz - az) - ez * (px - ax)) / l
            if (side < margin) return false
        }
        return true
    }

    @Test
    fun sameAnswersAsTheBoxedSort() {
        val rnd = Random(7)
        val hull = Hull()
        var inside = 0
        repeat(20_000) { trial ->
            val n = rnd.nextInt(0, 640)
            // coarse grids give many equal x (and equal points) so ties and collinear runs are exercised; ±0.0 too
            val grid = if (trial % 3 == 0) 1.0 else if (trial % 3 == 1) 0.5 else 0.0
            fun v(span: Double) = if (grid > 0) (rnd.nextInt(-8, 9) * grid).let { if (it == 0.0 && rnd.nextBoolean()) -0.0 else it } else rnd.nextDouble(-span, span)
            val xs = DoubleArray(640) { v(12.0) }
            val zs = DoubleArray(640) { v(9.0) }
            val px = rnd.nextDouble(-6.0, 6.0); val pz = rnd.nextDouble(-5.0, 5.0)
            val margin = if (rnd.nextBoolean()) 0.4 else 0.0
            val want = reference(xs, zs, n, px, pz, margin)
            if (want) inside++
            assertEquals("trial $trial (n=$n)", want, hull.containsWithMargin(xs, zs, n, px, pz, margin))
        }
        println("[hull] $inside of 20000 inside")
    }
}
