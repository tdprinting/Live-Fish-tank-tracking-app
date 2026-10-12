package com.reeflive.core.geom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class FlowFieldTest {
    /** SIMULATION §8.9: speed 30 cm along the PH-A axis is 8–20 cm/s. */
    @Test
    fun jetDecay() {
        val f = FlowField(FlowField.defaultJets(), EmptySdf)
        val ph = FlowField.defaultJets().first { it.name == "PH-A" }
        val p = ph.pos + ph.axis * 30.0
        val v = f.velocity(p).length()
        assertTrue("speed $v", v in 8.0..20.0)
    }

    @Test
    fun powerheadsOffLeavesOnlyWeakFlow() {
        val f = FlowField(FlowField.defaultJets(), EmptySdf)
        f.powerheadsOn = false
        val ph = FlowField.defaultJets().first { it.name == "PH-A" }
        assertTrue(f.velocity(ph.pos + ph.axis * 10.0).length() < 2.0)
    }
}

class SplineTest {
    /** SIMULATION §8.10: spine spacing BL/15 ± 10 % during a 180° turn. */
    @Test
    fun continuity() {
        val L = 15.0
        val s = BodySpline(L)
        val r = 1.2 * L
        val centre = Vec3(50.0, 30.0, 15.0)
        s.reset(centre + Vec3(r, 0.0, -30.0), Vec3.Z)
        val pos = Array(BodySpline.POINTS) { Vec3.ZERO }
        val frames = Array(BodySpline.POINTS) { Quat.IDENTITY }
        val dt = 0.02
        val speed = 1.5 * L
        // straight lead-in, then a 180° turn about the centre
        var p = centre + Vec3(r, 0.0, -30.0)
        while (p.z < 0.0 + centre.z) { p += Vec3.Z * (speed * dt); s.update(p, Vec3.Z, dt, 0.15) }
        var a = 0.0
        while (a < PI) {
            a += speed * dt / r
            p = centre + Vec3(r * cos(a), 0.0, r * sin(a))
            val h = Vec3(-sin(a), 0.0, cos(a))
            s.update(p, h, dt, 0.15)
            s.sample(Vec3.Y, pos, frames)
            for (i in 1 until BodySpline.POINTS) {
                val d = pos[i].distanceTo(pos[i - 1])
                assertEquals("segment $i at angle $a", L / 15, d, L / 15 * 0.1)
            }
        }
    }

    @Test
    fun framesAreOrthonormal() {
        val q = Quat.fromForwardUp(Vec3(1.0, 0.2, 0.3), Vec3.Y)
        val f = q.rotate(Vec3.X); val u = q.rotate(Vec3.Y)
        assertEquals(1.0, f.length(), 1e-9)
        assertEquals(0.0, f dot u, 1e-9)
        assertTrue((f - Vec3(1.0, 0.2, 0.3).normalized()).length() < 1e-9)
    }
}

class SdfTest {
    @Test
    fun halfFloatRoundTrip() {
        for (v in listOf(0f, 1f, -2.5f, 13.75f, 0.0625f, -40f)) assertEquals(v, Half.toFloat(Half.fromFloat(v)), 1e-3f)
    }

    @Test
    fun analyticSphereDistance() {
        val s = AnalyticSdf(listOf(Ellipsoid(Vec3(0.0, 0.0, 0.0), Vec3(5.0, 5.0, 5.0))))
        assertEquals(5.0, s.dist(Vec3(10.0, 0.0, 0.0)), 0.05)
        assertTrue(s.dist(Vec3.ZERO) < 0)
        assertEquals(1.0, s.gradient(Vec3(8.0, 0.0, 0.0)).x, 0.01)
    }
}
