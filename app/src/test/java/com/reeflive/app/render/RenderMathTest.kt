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
    @Test fun fixturesHangOverTheTank() {
        // AI Prime lens centres (equipment.glb AiPrimeL/R_Lens `lightPos`): pucks ~29 cm over the 41 cm rim of the
        // 48 x 16 x 18 in tank, on goosenecks from behind (lens a little behind the centre line)
        assertEquals(2, LightingState.LIGHT_POS.size)
        assertEquals(32f, LightingState.LIGHT_POS[0][0], 1e-4f)
        assertEquals(88f, LightingState.LIGHT_POS[1][0], 1e-4f)
        for (p in LightingState.LIGHT_POS) {
            assertEquals(69.6f, p[1], 1e-4f)
            assertTrue(p[1] - (Camera.TANK[1] + 0.4f) in 25f..35f)
            assertEquals(20f, p[2], 1e-4f)
            assertTrue(p[0] in 0f..Camera.TANK[0] && p[2] in 0f..Camera.TANK[2])
        }
    }

    @Test fun fullWhiteAndBlues() {
        val s = LightingState.from(floatArrayOf(1f, 1f, 1f, 0f, 0f, 0f, 0f, 0f))
        // weights 4/16 × 3 channels = 0.75
        assertEquals(0.75f, s.intensity, 1e-5f)
        // key = Σ rgb × 0.25
        assertEquals((0.85f + 0.12f + 0.14f) * 0.25f, s.keyColor[0], 1e-5f)
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

    @Test fun blueLightLookIsActinicAndLit() {
        // the Blue light look: blue share ~1 (the grade keeps it blue, corals fluoresce), bright enough to not be night,
        // and its key light is blue; Day stays below the grade's actinic threshold (0.86) so daylight is still balanced
        val s = LightingState.from(ViewLight.BLUE.channels!!)
        assertTrue(s.blueFraction > 0.99f)
        assertFalse(s.night)
        assertTrue(s.intensity in 0.4f..0.7f)
        assertTrue(s.keyColor[2] > 2f * s.keyColor[1] && s.keyColor[1] > s.keyColor[0])
        assertTrue(LightingState.from(ViewLight.DAY.channels!!).blueFraction < 0.86f)
    }

    @Test fun fluorescenceOnlyForCoralTissueWithoutOwnGlow() {
        assertNotNull(Fluorescence.of("zoa_polyp_orange"))
        assertNotNull(Fluorescence.of("clove_polyp"))
        assertNull(Fluorescence.of("gsp_mat_polyp")) // GSP's glow is in its GLB
        assertNull(Fluorescence.of("rock"))
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

    @Test fun aNearMissWithinAFingertipStillPicks() {
        val fish = straightRig("fish", 60f, 30f, 20f, 8f)      // capsule radius 1.76 cm
        val snail = straightRig("snail", 30f, 30f, 20f, 2f)    // floored at 1.2 cm
        val d = floatArrayOf(0f, 0f, -1f)
        // 3 cm off the fish's spine, 130 cm away: a 1.24 cm miss, ~0.0095 as a view angle
        val o = floatArrayOf(57f, 33f, 150f)
        assertNull(Picker.pick(o, d, listOf(fish, snail)))
        assertEquals("fish", Picker.pick(o, d, listOf(fish, snail), slack = 0.02f))
        assertNull("beyond the fingertip", Picker.pick(o, d, listOf(fish, snail), slack = 0.005f))
        // a direct hit always wins over a closer near miss
        assertEquals("snail", Picker.pick(floatArrayOf(29.5f, 30f, 150f), d, listOf(fish, snail), slack = 0.5f))
        // a 24 px fingertip on a 1000 px view at fov 50°: tan(25°) · 2 · 0.024
        assertEquals(0.02238f, Picker.fingerSlack(24f, 1000, 50f), 1e-4f)
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

    @Test fun hairpinSpineIsUnbentToTheCap() {
        // head at x 60 facing +X; the trail runs back 4 stations, then folds straight back past the head (a U-turn)
        val spine = FloatArray(64)
        val frames = FloatArray(64)
        val seg = 8f / 15f
        for (i in 0 until 16) {
            val x = if (i <= 4) 60f - i * seg else 60f - 4 * seg + (i - 4) * seg
            val z = if (i <= 4) 16f else 16.3f
            spine[i * 4] = x; spine[i * 4 + 1] = 30f; spine[i * 4 + 2] = z
        }
        // frames as the sim builds them: model +X along the station tangent (a pure yaw here), up = +Y
        for (i in 0 until 16) {
            val a = if (i == 0) 0 else i - 1; val b = if (i == 15) 15 else i + 1
            val th = kotlin.math.atan2(-(spine[a * 4 + 2] - spine[b * 4 + 2]), spine[a * 4] - spine[b * 4])
            frames[i * 4 + 1] = kotlin.math.sin(th / 2f); frames[i * 4 + 3] = kotlin.math.cos(th / 2f)
        }
        val oldLen = FloatArray(15) { k -> dist(spine, k, k + 1) }
        val moved = FishRig.limitBend(spine, frames, FishRig.MAX_BEND_PER_SEGMENT, FishRig.MAX_BEND_TOTAL, FloatArray(64))
        assertTrue(moved)
        assertEquals(60f, spine[0], 1e-6f); assertEquals(16f, spine[2], 1e-6f)
        var total = 0f
        for (k in 1 until 15) {
            assertEquals("segment $k keeps its length", oldLen[k], dist(spine, k, k + 1), 1e-3f)
            val ax = spine[k * 4] - spine[(k - 1) * 4]; val az = spine[k * 4 + 2] - spine[(k - 1) * 4 + 2]; val ay = spine[k * 4 + 1] - spine[(k - 1) * 4 + 1]
            val bx = spine[(k + 1) * 4] - spine[k * 4]; val bz = spine[(k + 1) * 4 + 2] - spine[k * 4 + 2]; val by = spine[(k + 1) * 4 + 1] - spine[k * 4 + 1]
            val c = (ax * bx + ay * by + az * bz) / (sqrt(ax * ax + ay * ay + az * az) * sqrt(bx * bx + by * by + bz * bz))
            val ang = kotlin.math.acos(c.coerceIn(-1f, 1f))
            assertTrue("segment $k bend $ang", ang <= FishRig.MAX_BEND_PER_SEGMENT + 1e-3f)
            total += ang
        }
        assertTrue("total bend $total", total <= FishRig.MAX_BEND_TOTAL + 1e-2f)
        // the tail station no longer lies on top of the head: the fold is opened into a curve
        assertTrue("tail clear of the head", dist(spine, 0, 15) > 8f * 0.25f)
        // frames follow the new tangents and stay unit
        for (k in 1 until 15) {
            val q = floatArrayOf(frames[k * 4], frames[k * 4 + 1], frames[k * 4 + 2], frames[k * 4 + 3])
            val fwd = rot(q, floatArrayOf(1f, 0f, 0f))
            val dx = spine[(k - 1) * 4] - spine[(k + 1) * 4]; val dy = spine[(k - 1) * 4 + 1] - spine[(k + 1) * 4 + 1]; val dz = spine[(k - 1) * 4 + 2] - spine[(k + 1) * 4 + 2]
            val l = sqrt(dx * dx + dy * dy + dz * dz)
            assertEquals("frame $k along the spine", 1f, (fwd[0] * dx + fwd[1] * dy + fwd[2] * dz) / l, 2e-2f)
            val up = rot(q, floatArrayOf(0f, 1f, 0f))
            assertTrue("roll kept: frame $k up ${up[1]}", up[1] > 0.95f)
        }
    }

    @Test fun gentleCurveIsUntouched() {
        val p = DemoSnapshots.circlePose("a", "x", 10f, 3f, 61f, 30f, 16f, 20f, 0.4f)
        val rig = FishRig("a").also { it.set(null, p, 1f) }
        for (k in 0 until 16) assertEquals(p.spine[k * 3], rig.spine[k * 4], 1e-4f)
    }

    private fun dist(s: FloatArray, a: Int, b: Int): Float {
        val dx = s[a * 4] - s[b * 4]; val dy = s[a * 4 + 1] - s[b * 4 + 1]; val dz = s[a * 4 + 2] - s[b * 4 + 2]
        return sqrt(dx * dx + dy * dy + dz * dz)
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
            assertTrue(y in (Camera.SAND_Y - 0.1f)..(Camera.WATER_Y + 0.1f))
        }
        assertTrue(abs(f.out[(f.foodRange.first) * ParticleField.FLOATS] - 10f) < 1e-4f)
    }
}
