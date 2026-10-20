package com.reeflive.core.sim

import com.reeflive.core.geom.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** GSP mat on the drawn rock ([GspMatBuilder]) and local polyp retraction ([GspPolyps]). */
class GspMatTest {
    /** UV sphere as a STRIDE-12 mesh (duplicated seam vertices, like an unwelded GLB); [sky] per vertex from its y. */
    private fun sphere(c: Vec3, r: Double, sky: (Double) -> Double = { 1.0 }, rings: Int = 40, segs: Int = 64): Pair<FloatArray, IntArray> {
        val v = ArrayList<Float>(); val idx = ArrayList<Int>()
        for (i in 0..rings) for (j in 0..segs) {
            val th = PI * i / rings; val ph = 2 * PI * j / segs
            val nx = sin(th) * cos(ph); val ny = cos(th); val nz = sin(th) * sin(ph)
            v += listOf((c.x + r * nx).toFloat(), (c.y + r * ny).toFloat(), (c.z + r * nz).toFloat(), nx.toFloat(), ny.toFloat(), nz.toFloat(),
                0f, 0f, 0f, 0f, sky(ny).toFloat(), 1f)
        }
        for (i in 0 until rings) for (j in 0 until segs) {
            val a = i * (segs + 1) + j; val b = a + segs + 1
            idx += listOf(a, b, a + 1, a + 1, b, b + 1)
        }
        return v.toFloatArray() to idx.toIntArray()
    }

    private val open: (Double, Double, Double) -> Double = { _, _, _ -> 10.0 }

    private fun sphereClearance(c: Vec3, r: Double): (Double, Double, Double) -> Double = { x, y, z -> Vec3(x, y, z).distanceTo(c) - r }

    @Test
    fun matCopiesTheRockAndMeasuresAlongItsSurface() {
        val c = Vec3(40.0, 20.0, 20.0); val r = 8.0
        val (v, i) = sphere(c, r)
        val res = GspMatBuilder.build(v, i, null, c + Vec3(0.0, r, 0.0), Vec3(0.0, 1.0, 0.0), 7, 0.8, 1400, sphereClearance(c, r))
        assertNotNull(res); res!!
        // every mat vertex is a rock vertex: on the sphere exactly
        for (k in 0 until res.vertices.size / GspMatBuilder.STRIDE) {
            val o = k * GspMatBuilder.STRIDE
            val p = Vec3(res.vertices[o].toDouble(), res.vertices[o + 1].toDouble(), res.vertices[o + 2].toDouble())
            assertEquals(r, p.distanceTo(c), 1e-3)
            val d = res.vertices[o + 6].toDouble()
            if (d < GspMatBuilder.UNREACHED) {
                // along the surface: great-circle distance from the top (polyline a little longer)
                val gc = r * kotlin.math.acos(((p.y - c.y) / r).coerceIn(-1.0, 1.0))
                assertTrue("vertex at great-circle $gc measured $d", d >= gc - 0.05 && d <= gc * 1.12 + 0.3)
            }
        }
        println("GSP mat on an 8 cm sphere: ${res.vertices.size / 12} vertices, ${res.instanceCount} polyps, reach ${res.reach}")
        assertTrue("reaches round to the bottom: ${res.reach}", res.reach > PI * r * 0.95)
        // polyps on the surface, pointing out of it, sorted by distance, spaced out
        assertTrue(res.instanceCount > 300)
        for (k in 0 until res.instanceCount) {
            val o = k * GspMatBuilder.INSTANCE_FLOATS
            val p = Vec3(res.instances[o].toDouble(), res.instances[o + 1].toDouble(), res.instances[o + 2].toDouble())
            assertEquals(r, p.distanceTo(c), 0.15)
            val q = floatArrayOf(res.instances[o + 4], res.instances[o + 5], res.instances[o + 6], res.instances[o + 7])
            val axis = rotateY(q)
            assertTrue("polyp axis points out of the rock", axis dot (p - c).normalized() > 0.6)
            if (k > 0) assertTrue(res.instanceDist[k] >= res.instanceDist[k - 1])
            assertEquals(res.instanceDist[k].toDouble(), kotlin.math.floor(res.instances[o + 9].toDouble()) / 16.0, 0.07)
        }
        assertTrue(res.countWithin(5f) in 1 until res.countWithin(10f))
    }

    @Test
    fun matSpreadsSlowerIntoShade() {
        val c = Vec3(40.0, 20.0, 20.0); val r = 8.0
        val bottom = c - Vec3(0.0, r, 0.0)
        fun reachBottom(sky: (Double) -> Double): Double {
            val (v, i) = sphere(c, r, sky)
            val res = GspMatBuilder.build(v, i, null, c + Vec3(0.0, r, 0.0), Vec3(0.0, 1.0, 0.0), 7, 0.8, 1400, open)!!
            var best = Double.MAX_VALUE; var d = 0.0
            for (k in 0 until res.vertices.size / 12) {
                val p = Vec3(res.vertices[k * 12].toDouble(), res.vertices[k * 12 + 1].toDouble(), res.vertices[k * 12 + 2].toDouble())
                if (p.distanceTo(bottom) < best) { best = p.distanceTo(bottom); d = res.vertices[k * 12 + 6].toDouble() }
            }
            return d
        }
        val lit = reachBottom { 1.0 }
        val shadedUnder = reachBottom { ny -> if (ny < 0) 0.0 else 1.0 }
        println("GSP distance to the underside: lit $lit, shaded $shadedUnder")
        assertTrue("shade slows the mat: $lit vs $shadedUnder", shadedUnder > lit + 8.0)
    }

    @Test
    fun matDoesNotJumpToAnotherRockOrPlaceOffRock() {
        val a = Vec3(40.0, 20.0, 20.0); val b = Vec3(57.0, 20.0, 20.0)   // 1 cm of water between them
        val (va, ia) = sphere(a, 8.0); val (vb, ib) = sphere(b, 8.0)
        val v = va + vb; val i = ia + ib.map { it + va.size / 12 }.toIntArray()
        val res = GspMatBuilder.build(v, i, null, a + Vec3(0.0, 8.0, 0.0), Vec3(0.0, 1.0, 0.0), 3, 0.8, 1400, open)!!
        for (k in 0 until res.vertices.size / 12) {
            val x = res.vertices[k * 12].toDouble()
            val d = res.vertices[k * 12 + 6]
            if (x > 48.5) assertEquals("the other rock is not reached", GspMatBuilder.UNREACHED, d, 0f)
        }
        // a placement in open water has nothing to grow on
        assertNull(GspMatBuilder.build(v, i, null, Vec3(40.0, 35.0, 20.0), Vec3(0.0, 1.0, 0.0), 3, 0.8, 1400, open))
    }

    @Test
    fun polypsFitUnderAnOverhang() {
        val c = Vec3(40.0, 20.0, 20.0); val r = 8.0
        val (v, i) = sphere(c, r)
        // a ledge 1 cm above the top of the dome: polyps there are shortened or left out, none reach into it
        val ledgeY = c.y + r + 1.0
        val clear: (Double, Double, Double) -> Double = { x, y, z -> minOf(Vec3(x, y, z).distanceTo(c) - r, ledgeY - y) }
        val res = GspMatBuilder.build(v, i, null, c + Vec3(r, 0.0, 0.0), Vec3(1.0, 0.0, 0.0), 5, 0.8, 1400, clear)!!
        for (k in 0 until res.instanceCount) {
            val o = k * GspMatBuilder.INSTANCE_FLOATS
            val y = res.instances[o + 1].toDouble(); val s = res.instances[o + 3].toDouble()
            val axis = rotateY(floatArrayOf(res.instances[o + 4], res.instances[o + 5], res.instances[o + 6], res.instances[o + 7]))
            val tip = y + axis.y * GspMatBuilder.POLYP_CM * s
            assertTrue("polyp $k tip $tip stays under the ledge at $ledgeY", tip <= ledgeY + 0.25)
        }
    }

    @Test
    fun aTouchClosesTheMatAroundItAndItReopens() {
        val sp = TestTank.catalog["briareum_sp"]
        val big = Coral("g", sp, "GSP", "", Vec3(40.0, 20.0, 20.0), Vec3(0.0, 1.0, 0.0), 0.0).also { it.radiusCm = 20.0 }
        val p = Vec3(43.0, 20.0, 20.0)
        assertTrue(GspPolyps.poke(big, p, 100.0))
        assertTrue("touched polyps pull in", GspPolyps.closedAt(big, p, 100.5) > 0.9)
        assertTrue("the ring spreads a few cm", GspPolyps.closedAt(big, p + Vec3(2.5, 0.0, 0.0), 101.5) > 0.5)
        assertTrue("far polyps stay out", GspPolyps.closedAt(big, p + Vec3(10.0, 0.0, 0.0), 102.0) == 0.0)
        assertTrue("reopened a minute later", GspPolyps.closedAt(big, p, 160.0) < 0.05)
        assertTrue("a big mat stays mostly open as a whole: ${big.disturb}", big.disturb < 0.1)
        // touching again close by holds the same spot shut instead of piling up pokes
        GspPolyps.poke(big, p + Vec3(1.0, 0.0, 0.0), 102.0)
        assertEquals(1, GspPolyps.pokesFor(big, 102.0)!!.size / GspPolyps.STRIDE)
        assertTrue(GspPolyps.closedAt(big, p, 140.0) > GspPolyps.closedAt(big, p, 160.0))
        // a small frag shuts as a whole
        val frag = Coral("f", sp, "GSP frag", "", Vec3(40.0, 20.0, 20.0), Vec3(0.0, 1.0, 0.0), 0.0).also { it.radiusCm = 3.0 }
        GspPolyps.poke(frag, frag.pos, 10.0)
        assertTrue("a frag closes whole: ${frag.disturb}", frag.disturb > 0.5)
        // a touch off the mat does nothing
        assertTrue(!GspPolyps.poke(big, Vec3(80.0, 20.0, 20.0), 200.0))
    }

    @Test
    fun aNewColonySettlesBeforeOpening() {
        val sp = TestTank.catalog["briareum_sp"]
        val c = Coral("g", sp, "GSP", "", Vec3(40.0, 20.0, 20.0), Vec3(0.0, 1.0, 0.0), 0.0)
        assertEquals(GspPolyps.SETTLE_OPEN, GspPolyps.settleFactor(c, 0.0), 1e-9)
        assertEquals(1.0, GspPolyps.settleFactor(c, GspPolyps.SETTLE_MIN * 60 + 1), 1e-9)
        c.established = true; c.placedAtSec = Double.NaN
        assertEquals("a colony loaded from a save is settled", 1.0, GspPolyps.settleFactor(c, 0.0), 1e-9)
    }

    private fun rotateY(q: FloatArray): Vec3 {
        // q * (0,1,0) * q^-1
        val x = q[0].toDouble(); val y = q[1].toDouble(); val z = q[2].toDouble(); val w = q[3].toDouble()
        val v = Vec3(2 * (x * y - w * z), 1 - 2 * (x * x + z * z), 2 * (y * z + w * x))
        return v * (1.0 / sqrt(v dot v))
    }
}
