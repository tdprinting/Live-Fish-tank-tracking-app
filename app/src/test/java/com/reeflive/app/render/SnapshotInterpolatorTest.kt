package com.reeflive.app.render

import com.reeflive.core.sim.Organism
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The drawn pose must be a smooth function of wall time however unevenly the 20 Hz snapshots land against the
 * 60 Hz frames (FishGlitchTest guards the sim side; this guards the hand-over to the renderer).
 */
class SnapshotInterpolatorTest {
    private val tickNs = 50_000_000L
    private val frameNs = 16_666_667L

    /** Head travel per frame of the demo tang over [frames] frames, snapshots arriving at [arrival] (ns) for tick k. */
    private fun frameSteps(frames: Int, arrival: (Int) -> Long): List<Float> {
        val interp = SnapshotInterpolator()
        var k = 0
        var lastX = Float.NaN; var lastZ = Float.NaN
        val steps = ArrayList<Float>()
        for (f in 0 until frames) {
            val now = f * frameNs
            // the provider hands over the newest snapshot published by now
            while (arrival(k + 1) <= now) k++
            interp.push(DemoSnapshots.at(k * 0.05), now)
            val rig = interp.update(now).first { it.id == "demo_tang" }
            if (!lastX.isNaN() && f > 30) steps += sqrt((rig.headX() - lastX).let { it * it } + (rig.headZ() - lastZ).let { it * it })
            lastX = rig.headX(); lastZ = rig.headZ()
        }
        return steps
    }

    @Test fun jitteredSnapshotsDrawSmoothly() {
        // the sim thread publishes on time ±20 ms (GC, a busy core): deterministic pseudo-jitter
        val steps = frameSteps(600) { k -> k * tickNs + ((sin(k * 12.9898) * 43758.5453).let { it - kotlin.math.floor(it) } * 40_000_000 - 20_000_000).toLong() }
        val mean = steps.average().toFloat()
        val worst = steps.max()
        // the demo tang swims ~4.5 cm/s: ~0.075 cm a frame. Restarting the blend at each arrival drew frame steps of
        // 0 (late snapshot: a freeze) and > 2.5× the mean (early: a jump forward).
        assertTrue("worst frame step $worst vs mean $mean", worst < 1.8f * mean)
        assertTrue("never freezes for a frame: min ${steps.min()} vs mean $mean", steps.min() > 0.2f * mean)
    }

    @Test fun steadySnapshotsDrawEvenly() {
        val steps = frameSteps(400) { k -> k * tickNs + 3_000_000L }
        val mean = steps.average().toFloat()
        assertTrue("worst ${steps.max()} vs mean $mean", steps.max() < 1.35f * mean)
    }

    @Test fun resumeAfterAPauseEasesInsteadOfJumping() {
        val interp = SnapshotInterpolator()
        var now = 0L
        for (k in 0..20) { now = k * tickNs; interp.push(DemoSnapshots.at(k * 0.05), now); interp.update(now) }
        // paused: the same snapshot is offered for 2 s of frames
        val held = DemoSnapshots.at(20 * 0.05)
        var x = 0f
        while (now < 21 * tickNs + 2_000_000_000L) { now += frameNs; interp.push(held, now); x = interp.update(now).first { it.id == "demo_tang" }.headX() }
        // resumed at a later sim time: the first frame still shows the held pose, then it moves over one interval
        now += frameNs
        interp.push(DemoSnapshots.at(21 * 0.05), now)
        val x1 = interp.update(now).first { it.id == "demo_tang" }.headX()
        assertEquals("no jump on resume", x, x1, 0.02f)
    }

    @Test fun appendagePhaseInterpolatesAcrossItsWrap() {
        val w = Organism.APPENDAGE_WRAP.toFloat()
        assertEquals(FishRig.APPENDAGE_WRAP, w, 0f)
        // just before and just after the wrap: half way is on the wrap, not ~1000 rad back
        val mid = FishRig.lerpPhase(w - 0.1f, 0.1f, 0.5f, w)
        assertTrue("mid $mid", abs(mid - w) < 1e-3f || abs(mid) < 1e-3f)
        assertEquals(sin(0.0), sin(Organism.APPENDAGE_WRAP), 1e-9)
    }
}
