package com.reeflive.core.geom

import com.reeflive.core.sim.TestTank
import org.junit.Assert.assertTrue
import org.junit.Test

/** SIMULATION §9.7: flow solver incompressibility, stability, scalar transport; surface waves. */
class FluidTest {
    private fun tank(q: FluidQuality = FluidQuality.LOW) =
        FluidSim(quality = q, solidAt = { p -> TestTank.rock.sdf.dist(p) < 0.0 })

    @Test
    fun projectionIsDivergenceFree() {
        val f = tank()
        repeat(150) { f.step(0.1) }
        val rel = f.relativeDivergence()
        assertTrue("relative divergence $rel", rel < 0.02)
        assertTrue("no flow developed: ${f.maxSpeed()}", f.maxSpeed() > 5.0)
    }

    @Test
    fun stableForLongRuns() {
        val f = tank()
        var peak = 0.0
        repeat(1500) { i ->
            f.step(0.1, heaterWatts = 200.0, heaterPos = Vec3(120.0, 30.0, 8.0))
            if (i % 100 == 99) peak = maxOf(peak, f.maxSpeed())
        }
        val s = f.maxSpeed()
        assertTrue("speed blew up: $s (peak $peak)", s.isFinite() && peak < 160.0)
        // jets reach the far side: the global circulation is a few cm/s mid-tank
        val mid = f.velocity(Vec3(60.0, 30.0, 25.0)).length()
        assertTrue("mid-tank speed $mid", mid in 0.3..60.0)
    }

    @Test
    fun heaterPlumeRisesAndMixes() {
        val f = tank()
        f.powerheadsOn = false; f.hobOn = false
        repeat(300) { f.step(0.1, heaterWatts = 200.0, heaterPos = Vec3(120.0, 20.0, 8.0)) }
        val above = f.tempAnomaly(Vec3(119.0, 40.0, 8.0))
        val farAway = f.tempAnomaly(Vec3(10.0, 20.0, 25.0))
        assertTrue("plume above heater $above vs far $farAway", above > farAway + 0.01)
        assertTrue("heater cell rises: v=${f.velocity(Vec3(119.0, 30.0, 8.0)).y}", f.velocity(Vec3(119.0, 30.0, 8.0)).y > 0.0)
    }

    @Test
    fun doseSpreadsThenMixes() {
        val f = tank()
        repeat(50) { f.step(0.1) }
        val at = Vec3(71.0, 50.0, 6.0)
        f.injectDose(at, 1.0, 227.0)
        repeat(5) { f.step(0.1) }
        val nearEarly = f.doseExcess(at)
        val farEarly = f.doseExcess(Vec3(10.0, 10.0, 25.0))
        repeat(3000) { f.step(0.1) }
        val nearLate = f.doseExcess(at)
        assertTrue("dose plume near $nearEarly far $farEarly", nearEarly > 1.0 && nearEarly > farEarly + 1.0)
        assertTrue("plume did not mix: $nearLate", kotlin.math.abs(nearLate) < 0.2 * nearEarly)
    }

    /** The Live Tank path: full stock with the solver driving fish, food and corals at 20 Hz wall ticks. */
    @Test
    fun simulationRunsWithFluid() {
        val sim = TestTank.sim(com.reeflive.core.sim.DefaultStock.all)
        sim.enableFluid(FluidQuality.LOW)
        sim.enqueue(com.reeflive.core.sim.Command.Feed(com.reeflive.core.sim.FoodKind.FLAKE, 0.3))
        sim.enqueue(com.reeflive.core.sim.Command.Dose("alk", 0.5))
        repeat(400) { sim.stepWall(0.05) }
        val snap = sim.latest
        assertTrue("NaN pose", snap.organisms.all { o -> o.spine.all { it.isFinite() } })
        assertTrue("no flow grid", snap.flowGrid != null && snap.surfaceHeights.isNotEmpty())
        val c = sim.corals.first()
        assertTrue("coral flow ${c.flowSpeed}", c.flowSpeed.isFinite())
    }

    @Test
    fun surfaceWavesStayBounded() {
        val s = WaterSurface()
        s.impulse(60.0, 16.0, 0.5, 3.0)
        repeat(2000) { s.step(0.05) }
        val e = s.energy()
        val maxH = s.heights.maxOf { kotlin.math.abs(it) }
        assertTrue("surface energy $e max $maxH", e.isFinite() && maxH < 1.0)
        assertTrue("HOB keeps the surface moving: $maxH", maxH > 0.001)
    }
}
