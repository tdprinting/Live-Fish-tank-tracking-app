package com.reeflive.core.sim

import org.junit.Assert.assertTrue
import org.junit.Test

/** Sandbox parameter scenarios calibrated against research 05 (SIMULATION §9.6). */
class ParameterEffectsTest {
    private fun stocked(seed: Long = 3L) = TestTank.sim(DefaultStock.all, seed = seed).also { sim ->
        sim.organisms.filter { it.brainKind == BrainKind.WRASSE }.forEach { it.acceptsPrepared = true }
        sim.advanceAbstract(2 * 86400.0)   // settle acclimation
    }

    private fun Simulation.fish() = organisms.filter { it.isFish }
    private fun Simulation.inverts() = organisms.filter { !it.isFish }

    /** 31 °C for 2 days: fish breathe fast and get stressed but survive; corals retract; all recover within days. */
    @Test
    fun heatWave31For2Days() {
        val sim = stocked()
        val coral = sim.corals.first { it.kind == CoralKind.KENYA }
        val extBefore = coral.extension
        sim.enqueue(Command.SetParam("temp", 31.0))
        var gills = 0
        var maxStress = 0.0
        for (h in 0 until 48) {
            sim.advanceAbstract(3600.0)
            gills = maxOf(gills, sim.fish().count { Symptom.RAPID_GILLS in it.symptoms })
            maxStress = maxOf(maxStress, sim.fish().maxOf { it.stress })
        }
        assertTrue("fish deaths at 31 °C: ${sim.fish().filter { !it.alive }}", sim.fish().all { it.alive })
        assertTrue("no fish showed rapid gills", gills >= 3)
        assertTrue("fish not stressed: $maxStress", maxStress > 0.4)
        assertTrue("coral stress ${coral.stress}", coral.stress > 0.3)
        sim.enqueue(Command.SetParam("temp", 25.5))
        sim.advanceAbstract(4 * 86400.0)
        val stressed = sim.fish().filter { it.alive && it.stress > 0.35 && it.disease == null }
        assertTrue("fish still stressed after recovery: $stressed", stressed.isEmpty())
        assertTrue("coral did not reopen: ${coral.extension} vs $extBefore", coral.extension > 0.0 || coral.stress < 0.2)
    }

    /** SG 1.025 → 1.018 within 1 h, held 2 days: fish stressed but alive, shrimp (more sensitive) die. */
    @Test
    fun salinityDropTo1018() {
        val sim = stocked()
        for (i in 1..12) {
            sim.enqueue(Command.SetParam("sg", 1.025 - 0.007 * i / 12))
            sim.advanceAbstract(300.0)
        }
        var fishStress = 0.0
        for (h in 0 until 6) { sim.advanceAbstract(3600.0); fishStress = maxOf(fishStress, sim.fish().maxOf { it.stress }) }
        sim.advanceAbstract(2 * 86400.0)
        assertTrue("fish stress after the drop $fishStress", fishStress > 0.4)
        assertTrue("fish died: ${sim.fish().filter { !it.alive }.map { it.deathCause }}", sim.fish().all { it.alive })
        val shrimp = sim.inverts().filter { it.brainKind == BrainKind.BANDED_SHRIMP || it.brainKind == BrainKind.CLEANER_SHRIMP }
        assertTrue("shrimp survived an osmotic shock: ${shrimp.map { it.health }}", shrimp.any { !it.alive })
    }

    /** Ammonia: 0.25 NH3-N for a day stresses fish (gills) without deaths; 1.0 NH3-N kills most within 3 days. */
    @Test
    fun ammoniaSpike() {
        val mild = stocked(4L)
        // pH 8.2 / 25.5 °C → ~7 % free; TAN 4 ppm ≈ 0.29 ppm NH3 ≈ 0.24 NH3-N
        mild.enqueue(Command.SetParam("nh3", 4.0))
        var gills = 0
        for (h in 0 until 24) { mild.advanceAbstract(3600.0); gills = maxOf(gills, mild.fish().count { Symptom.RAPID_GILLS in it.symptoms }) }
        mild.enqueue(Command.WaterChange(0.5))
        mild.advanceAbstract(2 * 86400.0)
        assertTrue("no gill signs at 0.25 NH3-N", gills >= 3)
        assertTrue("deaths at 0.25 NH3-N: ${mild.fish().filter { !it.alive }}", mild.fish().all { it.alive })

        val severe = stocked(5L)
        severe.enqueue(Command.SetParam("nh3", 17.0))   // ≈ 1.2 ppm NH3 ≈ 1.0 NH3-N
        severe.advanceAbstract(3 * 86400.0)
        val deadFish = severe.fish().count { !it.alive }
        val deadInv = severe.inverts().count { !it.alive }
        assertTrue("1.0 NH3-N killed only $deadFish fish", deadFish >= severe.fish().size / 2)
        assertTrue("1.0 NH3-N killed only $deadInv inverts", deadInv >= severe.inverts().size / 2)
    }

    /** Tank-crash cascade: a fish die-off left in the tank releases ammonia and drags oxygen down. */
    @Test
    fun dieOffCascade() {
        val sim = stocked(6L)
        val doBefore = sim.water.s.doMgL
        for (f in sim.fish()) HealthModel.kill(f, "test", sim.simTimeSec, ArrayList())
        var peakTan = 0.0
        var minDo = 99.0
        for (h in 0 until 72) {
            sim.advanceAbstract(3600.0)
            peakTan = maxOf(peakTan, sim.water.s.nh3Total)
            minDo = minOf(minDo, sim.water.s.doMgL)
        }
        assertTrue("TAN peak $peakTan after die-off", peakTan > 0.3)
        assertTrue("DO did not dip: $minDo vs $doBefore", minDo < doBefore - 0.2)
    }

    /** Pumps off for a night: oxygen falls and cyano starts; strong flow stresses the weak-swimming cardinal. */
    @Test
    fun flowLossAndStrongFlow() {
        val sim = stocked(7L)
        val doBefore = sim.water.s.doMgL
        sim.enqueue(Command.SetParam("flow", 0.0))
        sim.advanceAbstract(12 * 3600.0)
        assertTrue("DO ${sim.water.s.doMgL} vs $doBefore with no flow", sim.water.s.doMgL < doBefore - 0.3)
        sim.enqueue(Command.SetParam("flow", 2.0))
        sim.advanceAbstract(3600.0)
        val cardinal = sim.organisms.first { it.brainKind == BrainKind.CARDINAL }
        assertTrue("cardinal flow stress ${cardinal.envStress}", (cardinal.envStress["flow"] ?: 0.0) > 0.2)
    }

    /** Lights on 16 h at high intensity with phosphate 0.2 grows more hair algae than the normal schedule. */
    @Test
    fun longPhotoperiodGrowsAlgae() {
        fun run(forced: Boolean): Double {
            val sim = TestTank.sim(emptyList(), seed = 8L)
            sim.enqueue(Command.SetParam("po4", 0.2))
            if (forced) sim.enqueue(Command.Lights(on = true, overrideIntensity = 1.0))
            for (d in 0 until 14) { sim.advanceAbstract(86400.0); sim.enqueue(Command.SetParam("po4", 0.2)) }
            return sim.water.s.algaeHair + sim.water.s.cyano
        }
        val normal = run(false)
        val long = run(true)
        assertTrue("long photoperiod $long vs normal $normal", long > normal * 1.3)
    }
}
