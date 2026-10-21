package com.reeflive.core.geom

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertTrue
import org.junit.Test

/** A fish entering a turn curls its whole body into one C, not only the front half (BodySpline.bendSpread). */
class BodyTurnTest {
    private fun joints(pos: Array<Vec3>): DoubleArray {
        val a = DoubleArray(BodySpline.POINTS - 2)
        for (j in 1 until BodySpline.POINTS - 1) {
            val u = (pos[j] - pos[j - 1]).normalized(); val v = (pos[j + 1] - pos[j]).normalized()
            a[j - 1] = acos((u dot v).coerceIn(-1.0, 1.0))
        }
        return a
    }

    /** Swims straight along +X, then 0.35 BL of travel round a 0.6 BL radius; returns the joint turns. */
    private fun turn(spread: Double): Pair<DoubleArray, Array<Vec3>> {
        val L = 8.0
        val s = BodySpline(L).apply { bendSpread = spread }
        var p = Vec3(0.0, 10.0, 0.0)
        s.reset(p, Vec3.X)
        val dt = 0.05; val v = 6.0
        repeat(60) { p += Vec3.X * (v * dt); s.update(p, Vec3.X, dt, 0.15) }
        val r = 0.6 * L
        val c = p + Vec3.Z * r
        var th = 0.0
        while (th * r < 0.35 * L) {
            th += v * dt / r
            p = Vec3(c.x + r * sin(th), p.y, c.z - r * cos(th))
            s.update(p, Vec3(cos(th), 0.0, sin(th)), dt, 0.15)
        }
        val pos = Array(BodySpline.POINTS) { Vec3.ZERO }
        s.sampleStations(pos)
        return joints(pos) to pos
    }

    @Test fun wholeBodyBendsIntoTheTurn() {
        val (raw, rawPos) = turn(0.0)
        val (spread, pos) = turn(0.85)
        fun rearShare(a: DoubleArray) = a.copyOfRange(a.size / 2, a.size).sum() / a.sum()
        // the raw trail keeps the back half straight; the spread body bends behind the middle too
        assertTrue("raw rear share ${rearShare(raw)}", rearShare(raw) < 0.1)
        assertTrue("spread rear share ${rearShare(spread)}", rearShare(spread) > 0.3)
        // no sharp kink: the largest joint turn is well under the raw trail's
        assertTrue("max joint ${spread.max()} vs raw ${raw.max()}", spread.max() < 0.6 * raw.max())
        // same head and (once eased in) the same total turn and segment lengths
        assertTrue(pos[0].distanceTo(rawPos[0]) < 1e-9)
        val n = BodySpline.POINTS - 1
        val tailRaw = (rawPos[n] - rawPos[n - 1]).normalized(); val tail = (pos[n] - pos[n - 1]).normalized()
        assertTrue("tail direction kept ${tail dot tailRaw}", (tail dot tailRaw) > 0.99)
        // segment lengths kept
        for (i in 0 until n) assertTrue("segment $i", abs(pos[i].distanceTo(pos[i + 1]) - rawPos[i].distanceTo(rawPos[i + 1])) < 0.08 * 8.0 / n)
    }
}
