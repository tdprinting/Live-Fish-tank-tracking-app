package com.reeflive.app.render

import com.reeflive.core.geom.Vec3
import com.reeflive.core.sim.RockData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

/** A straight fish of length [bl] facing +X with its head at (x, y, z). */
fun straightRig(id: String, x: Float, y: Float, z: Float, bl: Float): FishRig {
    val spine = FloatArray(48) { i -> when (i % 3) { 0 -> x - (i / 3) * bl / 15f; 1 -> y; else -> z } }
    val frames = FloatArray(64) { if (it % 4 == 3) 1f else 0f }
    val p = com.reeflive.core.sim.OrganismPose(id, "test", spine, frames, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, true, bl)
    return FishRig(id).also { it.set(null, p, 1f) }
}

class LightingStateTest {
    @Test fun fullWhiteAndBlues() {
        val s = LightingState.from(floatArrayOf(1f, 1f, 1f, 0f, 0f, 0f, 0f, 0f))
        // weights 4/16 × 3 channels = 0.75
        assertEquals(0.75f, s.intensity, 1e-5f)
        // key = Σ rgb × 0.25
        assertEquals((0.85f + 0.23f + 0.18f) * 0.25f, s.keyColor[0], 1e-5f)
        assertEquals((1f + 1f + 1f) * 0.25f, s.keyColor[2], 1e-5f)
        assertEquals(s.keyColor[1] * 0.18f, s.ambient[1], 1e-6f)
        assertEquals(2f / 3f, s.blueFraction, 1e-3f)
        assertFalse(s.night)
    }

    @Test fun intensityClampsAndBlueFraction() {
        val s = LightingState.from(FloatArray(8) { 1f })
        assertEquals(1f, s.intensity, 1e-6f)
        val actinic = LightingState.from(floatArrayOf(0f, 1f, 1f, 0f, 0f, 1f, 0f, 0f))
        assertTrue(actinic.blueFraction > 0.99f)
        assertTrue(actinic.keyColor[2] > actinic.keyColor[0])
    }

    @Test fun nightUsesMoonOnly() {
        val s = LightingState.from(floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0.3f))
        assertTrue(s.night)
        assertTrue(s.intensity < LightingState.NIGHT_THRESHOLD)
        assertTrue(s.keyColor[2] > s.keyColor[0]) // moonlight is blue
        // fog never drops below the floor colour
        assertTrue(s.fogColor.all { it > 0f })
    }

    @Test fun lightColorNormalized() {
        val s = LightingState.from(floatArrayOf(0.5f, 0.5f, 0.5f, 0f, 0f, 0f, 0f, 0f))
        val c = s.lightColor()
        assertEquals(s.intensity, maxOf(c[0], c[1], c[2]), 1e-5f)
    }
}

class PickerTest {
    @Test fun hitsNearestFish() {
        val far = straightRig("far", 60f, 30f, 5f, 8f)
        val near = straightRig("near", 60f, 30f, 20f, 8f)
        val d = floatArrayOf(0f, 0f, -1f)
        assertEquals("near", Picker.pick(floatArrayOf(57f, 30f, 150f), d, listOf(far, near)))
        assertNull(Picker.pick(floatArrayOf(10f, 30f, 150f), d, listOf(far, near)))
    }

    @Test fun capsuleMath() {
        val o = floatArrayOf(0f, 0f, 10f); val d = floatArrayOf(0f, 0f, -1f)
        val t = Picker.rayCapsule(o, d, -5f, 0f, 0f, 5f, 0f, 0f, 1f)
        assertNotNull(t)
        assertEquals(9f, t!!, 0.05f)
        assertNull(Picker.rayCapsule(o, d, -5f, 3f, 0f, 5f, 3f, 0f, 1f))
    }
}

class FishRigTest {
    @Test fun demoFramesPointAlongTheSpine() {
        val p = DemoSnapshots.circlePose("a", "x", 10f, 3f, 61f, 30f, 16f, 20f, 0.4f)
        for (k in 0 until 15) {
            val q = floatArrayOf(p.frames[k * 4], p.frames[k * 4 + 1], p.frames[k * 4 + 2], p.frames[k * 4 + 3])
            val fwd = rot(q, floatArrayOf(1f, 0f, 0f))
            val dx = p.spine[k * 3] - p.spine[(k + 1) * 3]; val dy = p.spine[k * 3 + 1] - p.spine[(k + 1) * 3 + 1]; val dz = p.spine[k * 3 + 2] - p.spine[(k + 1) * 3 + 2]
            val l = sqrt(dx * dx + dy * dy + dz * dz)
            assertEquals(1f, (fwd[0] * dx + fwd[1] * dy + fwd[2] * dz) / l, 1e-3f)
            val up = rot(q, floatArrayOf(0f, 1f, 0f))
            assertTrue("up stays up", up[1] > 0.9f)
        }
        // spine spans one body length
        var len = 0f
        for (k in 0 until 15) {
            val dx = p.spine[k * 3] - p.spine[(k + 1) * 3]; val dz = p.spine[k * 3 + 2] - p.spine[(k + 1) * 3 + 2]; val dy = p.spine[k * 3 + 1] - p.spine[(k + 1) * 3 + 1]
            len += sqrt(dx * dx + dy * dy + dz * dz)
        }
        assertEquals(10f, len, 1.5f)
    }

    @Test fun interpolatesBetweenSnapshots() {
        val i = SnapshotInterpolator()
        val a = DemoSnapshots.at(0.0); val b = DemoSnapshots.at(0.05)
        i.push(a, 0L); i.push(b, 50_000_000L)
        val rigs = i.update(75_000_000L) // half way
        assertEquals(a.organisms.size, rigs.size)
        val r = rigs.first()
        val pa = a.organisms.first(); val pb = b.organisms.first()
        assertEquals((pa.spine[0] + pb.spine[0]) / 2f, r.spine[0], 1e-3f)
        val qlen = sqrt(r.frames[0] * r.frames[0] + r.frames[1] * r.frames[1] + r.frames[2] * r.frames[2] + r.frames[3] * r.frames[3])
        assertEquals(1f, qlen, 1e-4f)
    }

    @Test fun phaseWraps() {
        assertEquals(999.95f, FishRig.lerpPhase(999.9f, 0f, 0.5f), 1e-3f)
    }

    private fun rot(q: FloatArray, v: FloatArray): FloatArray {
        val (x, y, z, w) = q
        // v + 2 q × (q × v + w v)
        val cx = y * v[2] - z * v[1] + w * v[0]; val cy = z * v[0] - x * v[2] + w * v[1]; val cz = x * v[1] - y * v[0] + w * v[2]
        return floatArrayOf(v[0] + 2 * (y * cz - z * cy), v[1] + 2 * (z * cx - x * cz), v[2] + 2 * (x * cy - y * cx))
    }
}

class CameraTest {
    @Test fun followStaysOutOfRockAndNearTheFish() {
        val rock = RockData.defaultTank()
        val cam = Camera()
        // a fish hugging the rock: put it just outside a rock surface point
        val anchor = rock.anchors["PERCH_2"]?.pos ?: Vec3(49.0, 29.0, 21.0)
        val rig = straightRig("f", anchor.x.toFloat(), anchor.y.toFloat() + 3f, anchor.z.toFloat(), 8f)
        cam.follow("f")
        repeat(240) { cam.update(1f / 60f, 1080, 1920, rock.sdf, { rig }) { } }
        val eye = Vec3(cam.eye[0].toDouble(), cam.eye[1].toDouble(), cam.eye[2].toDouble())
        assertTrue("eye outside rock: ${rock.sdf.dist(eye)}", rock.sdf.dist(eye) > 0.5)
        val d = eye.distanceTo(Vec3(rig.spine[24].toDouble(), rig.spine[25].toDouble(), rig.spine[26].toDouble()))
        assertTrue("close up: $d", d in 2.0..60.0)
    }

    @Test fun followLostReturnsToOrbit() {
        val cam = Camera()
        var lost: String? = null
        cam.follow("gone")
        cam.update(0.016f, 100, 100, null, { null }) { lost = it }
        assertEquals("gone", lost)
        assertNull(cam.followId)
    }

    @Test fun yawSpringTakesTheShortArcWithoutOvershoot() {
        val s = YawSpring(Camera.FOLLOW_YAW_OMEGA)
        s.snap(3.0f)
        // target just across the ±π seam: the short way is +0.28 rad, never back through 0
        var prevDist = abs(Camera.wrapAngle(s.value - (-3.0f)))
        repeat(240) {
            val v = s.step(-3.0f, 1f / 60f)
            assertTrue("stays near the seam: $v", abs(v) > 2.9f)
            val d = abs(Camera.wrapAngle(v - (-3.0f)))
            assertTrue("monotone approach (no overshoot)", d <= prevDist + 1e-5f)
            prevDist = d
        }
        assertEquals(0f, Camera.wrapAngle(s.value - (-3.0f)), 1e-3f)
        assertEquals(0f, Camera.wrapAngle(7f - (7f - 2f * Math.PI.toFloat())), 1e-5f)
    }

    @Test fun followCameraDoesNotJumpWhenTheFishTurnsTowardTheGlass() {
        val cam = Camera()
        val rig = straightRig("f", 60f, 30f, 16f, 8f)
        // heading θ in the XZ plane: 0 = +X, π/2 = +Z (toward the viewer), π = −X
        fun aimAt(theta: Float) {
            val dx = kotlin.math.cos(theta); val dz = kotlin.math.sin(theta)
            for (i in 0 until 16) {
                val t = 4f - i * 8f / 15f
                rig.spine[i * 4] = 60f + dx * t; rig.spine[i * 4 + 1] = 30f; rig.spine[i * 4 + 2] = 16f + dz * t
            }
        }
        aimAt(0f)
        cam.follow("f")
        repeat(240) { cam.update(1f / 60f, 1080, 1920, null, { rig }) { } }
        assertTrue("starts on the viewer's side", cam.eye[2] > 16f)
        var maxStep = 0f
        var prev = cam.eye.copyOf()
        // a quick U-turn through facing the front glass (0.5 s), then hold
        for (f in 0 until 300) {
            aimAt((Math.PI * minOf(f / 30.0, 1.0)).toFloat())
            cam.update(1f / 60f, 1080, 1920, null, { rig }) { }
            val dx = cam.eye[0] - prev[0]; val dy = cam.eye[1] - prev[1]; val dz = cam.eye[2] - prev[2]
            maxStep = maxOf(maxStep, sqrt(dx * dx + dy * dy + dz * dz))
            prev = cam.eye.copyOf()
        }
        assertTrue("ends on the viewer's side: ${cam.eye[2]}", cam.eye[2] > 16f)
        assertTrue("smooth: max step $maxStep cm/frame", maxStep < 1.5f)
    }

    @Test fun rayThroughCentreLooksAtTarget() {
        val cam = Camera()
        cam.update(0.016f, 1000, 1000, null, { null }) { }
        val d = FloatArray(3)
        cam.ray(500f, 500f, 1000, 1000, d)
        val tx = Camera.DEFAULT_TARGET[0] - cam.eye[0]; val ty = Camera.DEFAULT_TARGET[1] - cam.eye[1]; val tz = Camera.DEFAULT_TARGET[2] - cam.eye[2]
        val l = sqrt(tx * tx + ty * ty + tz * tz)
        assertEquals(1f, (d[0] * tx + d[1] * ty + d[2] * tz) / l, 1e-3f)
    }
}

class ParticleFieldTest {
    @Test fun particlesStayInWaterAndFoodIsPacked() {
        val f = ParticleField(120)
        val snap = DemoSnapshots.at(1.0).copy(food = floatArrayOf(10f, 50f, 10f, 20f, 40f, 12f), foodKinds = byteArrayOf(0, 3))
        repeat(300) { f.step(1f / 30f, it / 30f, snap) }
        assertEquals(2, f.foodRange.second)
        assertTrue(f.bubbleRange.second > 0)
        val (start, n) = f.ambientRange
        for (i in start until start + n) {
            val y = f.out[i * ParticleField.FLOATS + 1]
            assertTrue(y in 2.9f..56.1f)
        }
        assertTrue(abs(f.out[(f.foodRange.first) * ParticleField.FLOATS] - 10f) < 1e-4f)
    }
}
