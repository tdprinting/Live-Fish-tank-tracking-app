package com.reeflive.core.sim

import com.reeflive.core.ai.DiamondGobyBrain
import com.reeflive.core.ai.LeopardWrasseBrain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Sand as grains: thrown grains, slopes that pour, Marina's dive and Sandy's mouthfuls (SIMULATION §16). */
class SandGrainTest {
    private fun bed() = SandBed(121.9, 45.7, 3.0, 1L)

    /** Steepest neighbour step over open sand around (x, z) within [r] cm, as tan. */
    private fun steepest(s: SandBed, x: Double, z: Double, r: Double): Double {
        var m = 0.0
        var px = x - r
        while (px <= x + r) {
            var pz = z - r
            while (pz <= z + r) {
                m = maxOf(m, abs(s.surfaceY(px + s.cellX, pz) - s.surfaceY(px, pz)) / s.cellX)
                pz += s.cellZ
            }
            px += s.cellX
        }
        return m
    }

    @Test fun thrownGrainsCarryTheSandAndBuildAHeapWhereTheyLand() {
        val s = bed()
        val v0 = s.sandVolumeCm3()
        val got = s.takeExact(40.0, 30.0, 2.0, 1.5)
        val n = s.launchGrains(40.0, s.surfaceY(40.0, 30.0) + 1.0, 30.0, got, 12.0, 6.0, 0.0)
        assertTrue("grains in the water: $n", n >= 30)
        assertEquals("sand rides with the grains", v0, s.sandVolumeCm3(), 1e-6)
        repeat(80) { s.step(0.05, null) }
        assertEquals("all down", 0, s.grainsLive)
        assertEquals("and conserved", v0, s.sandVolumeCm3(), 1e-4)
        // they flew downstream of the pit: the heap is ahead of it, not on it
        assertTrue("a heap down-range (${s.cellHeight(45.0, 30.0)})", s.heightAt(44.0, 30.0) + s.heightAt(46.0, 30.0) > 0.05)
        assertTrue("a pit where they came from", s.heightAt(40.0, 30.0) < 0.0)
    }

    @Test fun aSteepHeapPoursDownToTheAngleOfReposeAsItHappens() {
        val s = bed()
        s.deposit(60.0, 30.0, 12.0, 1.2)
        val v0 = s.sandVolumeCm3()
        assertTrue("built too steep", steepest(s, 60.0, 30.0, 4.0) > 1.5 * SandBed.REPOSE_TAN)
        var rollingSeen = 0
        repeat(60) { s.step(0.05, null); rollingSeen = maxOf(rollingSeen, s.rolling()) }
        assertTrue("grains tumbled down the faces ($rollingSeen at once, ${s.rolled} in all)", rollingSeen >= 5 && s.rolled >= 20)
        repeat(80) { s.step(0.05, null) }
        assertEquals(0, s.grainsLive)
        assertTrue("back near repose within seconds: ${steepest(s, 60.0, 30.0, 4.0)}", steepest(s, 60.0, 30.0, 4.0) < 1.25 * SandBed.REPOSE_TAN)
        assertEquals("sand conserved", v0, s.sandVolumeCm3(), 1e-3)
        assertTrue(s.avalancheCm3 > 1.0)
        assertTrue("the bed settles and stops being watched", !s.busy)
    }

    @Test fun burialWithGrainsStillLeavesARealCover() {
        val s = bed()
        val v0 = s.sandVolumeCm3()
        val b = s.startBurial("w", 60.0, 8.0, 1.0, 0.0, 10.0, 3.6, 1.6, 2.2)!!
        for (i in 1..40) {
            val k = i / 40.0
            s.buryStep(b, k / 0.85, (k - 0.55) / 0.45, 0.35, if (i % 2 == 0) 1.0 else -1.0)
            s.step(0.05, null)
            assertEquals("conserved mid-dive (step $i)", v0, s.sandVolumeCm3(), 1e-3)
        }
        assertTrue("grains flew: ${s.grainsLaunched}", s.grainsLaunched > 150)
        s.buryStep(b, 1.0, 1.0)
        repeat(100) { s.step(0.05, null) }
        assertEquals(v0, s.sandVolumeCm3(), 1e-3)
        val top = b.bodyY + 0.5 * b.thickCm
        val cover = s.surfaceY(b.cx, b.cz) - top
        assertTrue("cover over the body after the grains settled: $cover cm", cover > 0.5 * b.coverCm)
    }

    @Test fun marinaFlingsSandAndDisappearsUnderIt() {
        val sim = TestTank.sim(DefaultStock.corals, seed = 5L, startHour = 19.0)
        sim.species.ensure("macropharyngodon_bipartitus")
        val o = sim.addFromSeed(LivestockSeed("macropharyngodon_bipartitus", "Marina", 8.5, id = "lw1"))
        val br = sim.agentOf(o)!!.brain as LeopardWrasseBrain
        var peak = 0
        var t = 0.0
        while (!br.buried && t < 2 * 3600) { sim.advance(0.5, behaviorDt = 0.05); t += 0.5; peak = maxOf(peak, sim.sand.grainsLive) }
        assertTrue("buried", br.buried)
        assertTrue("a spray of grains while it dug in: $peak at once", peak >= 60)
        sim.advance(10.0, behaviorDt = 0.05)
        val depth = sim.sand.surfaceY(o.pos.x, o.pos.z) - o.pos.y
        assertTrue("head $depth cm under the sand", depth in 2.0..5.0)
    }

    @Test fun sandyScoopsSiftsAndBlowsTheGrainsOutOfItsGills() {
        val sim = TestTank.sim(emptyList(), seed = 3L, startHour = 11.0)
        sim.species.ensure("valenciennea_puellaris")
        val g = sim.addFromSeed(LivestockSeed("valenciennea_puellaris", "Sandy", 12.0, id = "dg1"))
        val b = sim.agentOf(g)!!.brain as DiamondGobyBrain
        val v0 = sim.sand.sandVolumeCm3()
        var held = 0.0
        var peak = 0
        sim.advance(300.0, behaviorDt = 0.05) { held = maxOf(held, sim.sand.heldCm3); peak = maxOf(peak, sim.sand.grainsLive) }
        assertTrue("mouthfuls ${b.mouthfuls}", b.mouthfuls >= 8)
        assertTrue("it held sand in its mouth while it sifted ($held cm³)", held > 0.5 * DiamondGobyBrain.MOUTHFUL_CM3)
        assertTrue("grains poured out of the gills ($peak at once)", peak >= 15)
        assertTrue("grains launched ${sim.sand.grainsLaunched}", sim.sand.grainsLaunched >= 20L * b.puffs)
        sim.advance(5.0)
        assertEquals("sand conserved through scoop, sift and spit", v0, sim.sand.sandVolumeCm3(), 0.02 * abs(v0) + 0.3)
    }

    @Test fun aCloudSortsItselfCoarseGrainsLandFirst() {
        val s = bed()
        s.puff(60.0, s.surfaceY(60.0, 20.0) + 0.5, 20.0, 400, 8.0, finesShare = 0.4)
        val sand0 = s.meanDiameterMm(Grain.SAND)
        val fine0 = s.meanDiameterMm(Grain.FINE)
        assertTrue("sand is a size mix around 0.5 mm ($sand0)", sand0 in 0.45..0.65)
        assertTrue("fine sand is finer ($fine0)", fine0 in 0.12..0.22)
        repeat(10) { s.step(0.05, null) }
        val sand1 = s.meanDiameterMm(Grain.SAND)
        assertTrue("the coarse grains settle out first ($sand0 -> $sand1)", sand1 < sand0 - 0.03)
        repeat(30) { s.step(0.05, null) }
        assertTrue("silt still hangs after the sand is down", s.suspended(Grain.SILT) > 0)
    }

    @Test fun anEngineerGobySpitIsBlownOutAsGrains() {
        val s = bed()
        val v0 = s.sandVolumeCm3()
        val got = s.takeMouthful(50.0, 25.0, 0.8)
        val n0 = s.grainsLaunched
        s.spit(56.0, s.surfaceY(56.0, 25.0) + 0.8, 25.0, got, 1.4, plume = 6, hx = 2.0, hz = 0.0)
        assertTrue("most of the mouthful goes up as grains (${s.grainsLaunched - n0})", s.grainsLaunched - n0 >= 60)
        repeat(100) { s.step(0.05, null) }
        assertEquals("all down", 0, s.grainsLive)
        assertEquals("and conserved", v0, s.sandVolumeCm3(), 1e-3)
        assertTrue("thrown forward of the mouth", s.heightAt(58.0, 25.0) > s.heightAt(54.0, 25.0))
    }
}
