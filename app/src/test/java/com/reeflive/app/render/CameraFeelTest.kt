package com.reeflive.app.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

class SpringMathTest {
    @Test fun springIsFrameRateIndependentAndDoesNotOvershoot() {
        val a = Spring(10f).also { it.snap(0f) }
        val b = Spring(10f).also { it.snap(0f) }
        repeat(30) { a.step(1f, 1f / 30f) }
        repeat(120) { b.step(1f, 1f / 120f) }
        assertEquals(a.value, b.value, 1e-4f)
        val c = Spring(10f).also { it.snap(0f) }
        repeat(600) { assertTrue("no overshoot", c.step(1f, 1f / 60f) <= 1f + 1e-5f) }
        assertEquals(1f, c.value, 1e-4f)
    }

    @Test fun softLimitResistsThenEasesBack() {
        var v = 1f
        repeat(200) { v = SoftLimit.drag(v, 0.05f, -1f, 1f, 0.3f) }
        assertTrue("past the limit: $v", v > 1f)
        assertTrue("bounded: $v", v <= 1f + 0.3f * 1.5f + 1e-5f)
        // free movement back toward the range
        assertEquals(v - 0.1f, SoftLimit.drag(v, -0.1f, -1f, 1f, 0.3f), 1e-6f)
        // a single big step across the limit is only resisted for the part outside
        val w = SoftLimit.drag(0.9f, 0.2f, -1f, 1f, 0.3f)
        assertTrue(w > 1f && w < 1.1f)
        var r = v
        repeat(120) { r = SoftLimit.relax(r, -1f, 1f, 7f, 1f / 60f) }
        assertEquals(1f, r, 1e-3f)
    }

    @Test fun inertiaTravelIsFrameRateIndependent() {
        val a = Inertia(3f).also { it.kick(2f) }
        val b = Inertia(3f).also { it.kick(2f) }
        var da = 0f; var db = 0f
        repeat(60) { da += a.advance(1f / 30f) }
        repeat(240) { db += b.advance(1f / 120f) }
        assertEquals(da, db, 1e-3f)
        assertEquals(2f / 3f, da, 0.01f) // ∫ v e^(−ft) = v / f
    }
}

/** Records what the tracker reports. */
private class Rec : GestureTracker.Sink {
    var orbitX = 0f; var orbitY = 0f; var orbits = 0; var firstOrbit = Float.NaN
    var panX = 0f; var zoom = 1f; var rot = 0f; var flingX = 0f; var flings = 0
    val taps = ArrayList<CameraInput.TapKind>()
    var touching = false
    override fun touching(down: Boolean) { touching = down }
    override fun orbit(dx: Float, dy: Float) { if (orbits == 0) firstOrbit = sqrt(dx * dx + dy * dy); orbitX += dx; orbitY += dy; orbits++ }
    override fun pan(dx: Float, dy: Float) { panX += dx }
    override fun pinch(factor: Float, focusX: Float, focusY: Float) { zoom *= factor }
    override fun rotate(radians: Float) { rot += radians }
    override fun fling(vx: Float, vy: Float) { flingX = vx; flings++ }
    override fun tap(kind: CameraInput.TapKind, x: Float, y: Float) { taps += kind }
}

class GestureTrackerTest {
    private val ids = IntArray(10); private val xs = FloatArray(10); private val ys = FloatArray(10)

    private fun move(g: GestureTracker, t: Long, vararg p: Triple<Int, Float, Float>) {
        p.forEachIndexed { k, (id, x, y) -> ids[k] = id; xs[k] = x; ys[k] = y }
        g.move(p.size, ids, xs, ys, t)
    }

    @Test fun holdingStillDoesNotLockOutTheDrag() {
        val r = Rec(); val g = GestureTracker(r, touchSlop = 10f)
        g.down(0, 100f, 100f, 0L, first = true)
        g.timer(600L) // long press fires
        assertEquals(listOf(CameraInput.TapKind.LONG_PRESS), r.taps)
        for (i in 1..10) move(g, 600L + i * 16, Triple(0, 100f + i * 8f, 100f))
        assertEquals("the drag still orbits after a long press", 80f, r.orbitX, 1e-3f)
        g.up(0, 180f, 100f, 800L)
        assertEquals(1, r.taps.size)
    }

    @Test fun dragAppliesTheWholeMoveIncludingTheSlop() {
        val r = Rec(); val g = GestureTracker(r, touchSlop = 10f)
        g.down(0, 0f, 0f, 0L, first = true)
        move(g, 16L, Triple(0, 6f, 0f))
        assertEquals(0, r.orbits)
        move(g, 32L, Triple(0, 14f, 0f))
        assertEquals(14f, r.orbitX, 1e-4f)
    }

    @Test fun liftingOneOfTwoFingersCarriesOnOrbitingWithoutAJump() {
        val r = Rec(); val g = GestureTracker(r, touchSlop = 10f)
        g.down(0, 100f, 100f, 0L, first = true)
        g.down(1, 300f, 100f, 10L, first = false)
        move(g, 26L, Triple(0, 90f, 100f), Triple(1, 310f, 100f)) // spread: pinch
        assertTrue(r.zoom > 1f)
        assertEquals(0, r.orbits)
        g.up(1, 310f, 100f, 40L)
        move(g, 56L, Triple(0, 95f, 100f))
        assertEquals("orbit resumes at once, with only the new 5 px", 5f, r.firstOrbit, 1e-4f)
    }

    @Test fun pinchWorksWithFingersCloseTogether() {
        val r = Rec(); val g = GestureTracker(r, touchSlop = 10f)
        g.down(0, 100f, 100f, 0L, first = true)
        g.down(1, 120f, 100f, 5L, first = false)
        move(g, 21L, Triple(0, 95f, 100f), Triple(1, 125f, 100f))
        assertEquals(1.5f, r.zoom, 1e-4f)
    }

    @Test fun twistRotatesOnlyPastTheDeadZone() {
        val r = Rec(); val g = GestureTracker(r, touchSlop = 10f)
        g.down(0, 100f, 100f, 0L, first = true)
        g.down(1, 300f, 100f, 5L, first = false)
        // small twist (≈ 3°): no rotation
        move(g, 21L, Triple(0, 100f, 105f), Triple(1, 300f, 95f))
        assertEquals(0f, r.rot, 0f)
        // keep twisting: rotation starts
        for (i in 2..8) move(g, 21L + i * 16, Triple(0, 100f, 100f + 5f * i), Triple(1, 300f, 100f - 5f * i))
        assertTrue(abs(r.rot) > 0.05f)
    }

    @Test fun tapDoubleTapAndTwoFingerTap() {
        val r = Rec(); val g = GestureTracker(r, touchSlop = 10f, doubleTapSlop = 50f)
        g.down(0, 50f, 50f, 0L, first = true); g.up(0, 50f, 50f, 80L)
        assertTrue(r.taps.isEmpty())
        g.timer(80L + GestureTracker.DOUBLE_TAP_MS)
        assertEquals(listOf(CameraInput.TapKind.PICK), r.taps)
        r.taps.clear()
        g.down(0, 50f, 50f, 1000L, first = true); g.up(0, 50f, 50f, 1060L)
        g.down(0, 52f, 51f, 1150L, first = true); g.up(0, 52f, 51f, 1200L)
        g.timer(2000L)
        assertEquals(listOf(CameraInput.TapKind.ZOOM), r.taps)
        r.taps.clear()
        g.down(0, 50f, 50f, 3000L, first = true); g.down(1, 150f, 50f, 3010L, first = false)
        g.up(1, 150f, 50f, 3100L); g.up(0, 50f, 50f, 3110L)
        g.timer(4000L)
        assertEquals(listOf(CameraInput.TapKind.GLASS), r.taps)
        assertFalse(r.touching)
    }

    @Test fun quickReleaseFlingsAndARestedFingerDoesNot() {
        val r = Rec(); val g = GestureTracker(r, touchSlop = 10f)
        g.down(0, 0f, 0f, 0L, first = true)
        for (i in 1..6) move(g, i * 10L, Triple(0, i * 20f, 0f))
        g.up(0, 120f, 0f, 62L)
        assertEquals(1, r.flings)
        assertEquals(2000f, r.flingX, 200f)
        val r2 = Rec(); val g2 = GestureTracker(r2, touchSlop = 10f)
        g2.down(0, 0f, 0f, 0L, first = true)
        for (i in 1..6) move(g2, i * 10L, Triple(0, i * 20f, 0f))
        g2.up(0, 120f, 0f, 300L)
        assertEquals(0, r2.flings)
    }

    @Test fun aNewDownDropsStalePointersFromALostUp() {
        val r = Rec(); val g = GestureTracker(r, touchSlop = 10f)
        g.down(0, 0f, 0f, 0L, first = true)
        g.down(1, 50f, 0f, 5L, first = false)
        // UP / CANCEL never arrived; a fresh gesture starts
        g.down(0, 200f, 200f, 1000L, first = true)
        assertEquals(1, g.count)
        move(g, 1020L, Triple(0, 240f, 200f))
        assertEquals(40f, r.orbitX, 1e-4f)
    }
}

class CameraFeelTest {
    private fun settle(cam: Camera, seconds: Float, fps: Int = 60) {
        repeat((seconds * fps).toInt()) { cam.update(1f / fps, 1080, 1920, null, { null }) { } }
    }

    @Test fun orbitPastTheSideEasesBackInsideTheSoftLimit() {
        val cam = Camera()
        settle(cam, 0.1f)
        cam.touching = true
        repeat(60) { cam.orbit(-80f, 0f, 1920f); settle(cam, 1f / 60f) }
        assertTrue("pushed past the limit: ${cam.yaw}", cam.yaw > Camera.MAX_YAW)
        assertTrue("but not far: ${cam.yaw}", cam.yaw < Camera.MAX_YAW + Camera.YAW_GIVE * 1.6f)
        cam.touching = false
        settle(cam, 2f)
        assertEquals(Camera.MAX_YAW, cam.yaw, 0.01f)
    }

    @Test fun dragFeelIsTheSameAtDifferentFrameRates() {
        fun run(fps: Int): FloatArray {
            val cam = Camera()
            settle(cam, 0.1f, fps)
            cam.orbit(-300f, 120f, 1920f)
            cam.zoom(1.5f)
            settle(cam, 0.2f, fps) // 6 frames at 30 fps, 24 at 120
            return cam.eye.copyOf()
        }
        val a = run(30); val b = run(120)
        for (i in 0 until 3) assertEquals(a[i], b[i], 0.05f)
    }

    @Test fun flingCoastsAndStops() {
        val cam = Camera()
        settle(cam, 0.1f)
        cam.fling(-2000f, 0f, 1920f)
        settle(cam, 0.3f)
        val mid = cam.yaw
        assertTrue("turning: $mid", mid > 0.2f)
        settle(cam, 4f)
        val end = cam.yaw
        settle(cam, 1f)
        assertEquals("stopped", end, cam.yaw, 1e-3f)
        assertTrue(cam.yaw <= Camera.MAX_YAW + 1e-3f)
        cam.catchMotion()
    }

    @Test fun doubleTapGlidesInAndBackOut() {
        val cam = Camera()
        settle(cam, 0.1f)
        val far = cam.effectiveDistance
        cam.focusOn(30f, 12f, 20f)
        settle(cam, 2f)
        assertTrue("closer: ${cam.effectiveDistance} vs $far", cam.effectiveDistance < far * 0.6f)
        val dx = cam.eye[0] - 30f; val dy = cam.eye[1] - 12f; val dz = cam.eye[2] - 20f
        assertEquals(cam.effectiveDistance, sqrt(dx * dx + dy * dy + dz * dz), 1f)
        cam.focusOn(30f, 12f, 20f)
        cam.focusOn(30f, 12f, 20f)
        settle(cam, 3f)
        assertEquals(far, cam.effectiveDistance, 2f)
    }

    @Test fun pinchKeepsThePointUnderTheFingers() {
        val cam = Camera()
        settle(cam, 0.1f)
        val d = FloatArray(3)
        cam.ray(270f, 600f, 1080, 1920, d)
        val dist = cam.effectiveDistance
        val p = FloatArray(3) { cam.eye[it] + d[it] * dist }
        cam.zoomAt(1.6f, 270f, 600f, 1080, 1920)
        settle(cam, 2f)
        // project p: it should still be near pixel (270, 600)
        val uv = FloatArray(3)
        assertTrue(cam.projectSphere(p[0], p[1], p[2], 1f, uv))
        assertEquals(270f / 1080f, uv[0], 0.05f)
        assertEquals(1f - 600f / 1920f, uv[1], 0.05f)
    }

    @Test fun followLeadsInTheDirectionOfTravel() {
        val cam = Camera()
        val rig = straightRig("f", 40f, 30f, 16f, 8f)
        cam.follow("f")
        var x = 40f
        repeat(180) {
            x += 10f / 60f // 10 cm/s along +X
            for (i in 0 until 16) rig.spine[i * 4] = x - i * 8f / 15f
            cam.update(1f / 60f, 1080, 1920, null, { rig }) { }
        }
        val dir = FloatArray(3)
        cam.ray(540f, 960f, 1080, 1920, dir)
        // the view centre (aim) sits ahead of the mid-body: the frame leads the fish
        val mid = rig.spine[24]
        val t = (16f - cam.eye[2]) / dir[2]
        val aimX = cam.eye[0] + dir[0] * t
        assertTrue("aim $aimX ahead of mid $mid", aimX > mid + 0.5f)
    }

    /** A rock slab (z 22..26, below y 40) between the viewer and a wagging fish: the eye never gets near it. */
    @Test fun followCameraNeverEntersRock() {
        val slab = object : com.reeflive.core.geom.Sdf {
            override fun dist(p: com.reeflive.core.geom.Vec3) = distAt(p.x, p.y, p.z)
            override fun distAt(x: Double, y: Double, z: Double) = maxOf(22.0 - z, z - 26.0, y - 40.0)
            override fun cover(p: com.reeflive.core.geom.Vec3) = 0.0
        }
        val cam = Camera()
        repeat(12) { cam.update(1f / 60f, 1080, 1920, slab, { null }) { } }
        val rig = straightRig("f", 30f, 30f, 16f, 8f)
        cam.follow("f")
        var x = 30f
        var worst = Float.MAX_VALUE
        repeat(600) { f ->
            x += 12f / 60f * (if ((f / 150) % 2 == 0) 1f else -1f)
            val wag = kotlin.math.sin(f * 0.6f) * 0.8f
            for (i in 0 until 16) { rig.spine[i * 4] = x - i * 8f / 15f; rig.spine[i * 4 + 2] = 16f + wag * (i / 15f - 0.3f) }
            cam.touching = f % 90 == 0
            if (cam.touching) cam.orbit(300f, -80f, 1920f)
            if (f == 300) cam.nudgeZoom(-2f)
            cam.update(1f / 60f, 1080, 1920, slab, { rig }) { }
            worst = minOf(worst, slab.distAt(cam.eye[0].toDouble(), cam.eye[1].toDouble(), cam.eye[2].toDouble()).toFloat())
        }
        val corner = com.reeflive.core.geom.EyeCollider.nearCornerDistance(cam.near, cam.fovY, 1080f / 1920f)
        assertTrue("eye kept off the rock ($worst cm) by more than the near-plane corner ($corner cm)", worst > corner + 0.5f)
    }

    @Test fun zoomButtonsMoveTheFollowCamera() {
        val cam = Camera()
        val rig = straightRig("f", 60f, 30f, 16f, 8f)
        cam.follow("f")
        // eye distance from the fish's body centre once the camera has settled
        fun dist(): Float {
            repeat(120) { cam.update(1f / 60f, 1080, 1920, null, { rig }) { } }
            val dx = cam.eye[0] - rig.spine[20]; val dy = cam.eye[1] - 30f; val dz = cam.eye[2] - 16f
            return sqrt(dx * dx + dy * dy + dz * dz)
        }
        val d0 = dist()
        cam.nudgeZoom(1f)
        val d1 = dist()
        assertTrue("closer: $d1 < $d0", d1 < d0 - 1f)
        cam.nudgeZoom(-2f)
        assertTrue("further: ${dist()} > $d1", dist() > d1 + 1f)
    }
}
