package com.reeflive.core.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ca / alk / Mg coupling, potassium and strontium (coverage gaps W-1, W-2; research 01 §1.1, §2.1, 05 §10, §13). */
class ReefChemistryTest {
    /** A bare water model: no corals, no evaporation drift (ATO on), lights off. */
    private fun bare(w: WaterState, demand: Double = 0.0) = WaterSim(TankConfig(), w).also {
        it.ato = true
        it.coralDemandDkhPerDay = demand
    }

    private fun run(ws: WaterSim, days: Double) = repeat((days * 1440).toInt()) { ws.step(60.0) }

    /** 05 §10: Mg below 1200 lets normal water precipitate; at 1300 the same water is stable. Ca follows alk 7.14:1. */
    @Test
    fun lowMagnesiumDrivesPrecipitation() {
        val ok = bare(WaterState(mg = 1300.0))
        val low = bare(WaterState(mg = 1000.0))
        val a0 = low.s.alk; val c0 = low.s.ca; val m0 = low.s.mg
        run(ok, 3.0); run(low, 3.0)
        val dAlk = a0 - low.s.alk
        val dCa = c0 - low.s.ca
        println("W-1 Mg 1000: alk -${"%.2f".format(dAlk)} Ca -${"%.1f".format(dCa)} Mg -${"%.1f".format(m0 - low.s.mg)}; Mg 1300: alk ${"%.2f".format(ok.s.alk)}")
        assertTrue("stable at Mg 1300: alk ${ok.s.alk}", ok.s.alk > 8.45)
        assertTrue("low Mg should precipitate: -$dAlk dKH", dAlk > 0.5)
        assertEquals("Ca falls with alk at the CaCO3 ratio", WaterSim.CA_PER_DKH, dCa / dAlk, 0.05)
        assertTrue("Mg co-precipitates a little", low.s.mg < m0)
        // the product falls until it meets the low-Mg threshold, then precipitation stops
        val before = low.s.alk
        run(low, 20.0)
        assertTrue("precipitation should self-limit: ${low.s.alk}", low.s.alk > 3.0 && before - low.s.alk < 3.0)
    }

    /** 05 §9-10: alk above ~12 or Ca above ~480 is a precipitation risk even with Mg in range; a dose overshoot drifts back. */
    @Test
    fun highAlkPrecipitatesEvenWithGoodMg() {
        assertEquals(0.0, WaterSim.precipitationDkhPerHour(WaterState()), 1e-12)
        val hi = bare(WaterState(alk = 13.0, ca = 470.0, mg = 1300.0))
        run(hi, 2.0)
        assertTrue("alk 13 should fall: ${hi.s.alk}", hi.s.alk < 12.5)
        assertTrue("Ca follows: ${hi.s.ca}", hi.s.ca < 466.0)
    }

    /** 01 §2.1: coral uptake takes Ca and alk together (7.14 ppm per dKH) and Sr with the skeleton; it slows at low Ca. */
    @Test
    fun coralUptakeFollowsStoichiometry() {
        val w = bare(WaterState(), demand = 1.0)
        val a0 = w.s.alk; val c0 = w.s.ca; val sr0 = w.s.sr
        run(w, 1.0)
        val dAlk = a0 - w.s.alk
        assertEquals(1.0, dAlk, 0.05)
        assertEquals(WaterSim.CA_PER_DKH, (c0 - w.s.ca) / dAlk, 0.05)
        assertTrue("Sr is taken up with the skeleton: ${w.s.sr}", w.s.sr < sr0 - 0.1)

        val lowCa = bare(WaterState(ca = 280.0), demand = 1.0)
        val la0 = lowCa.s.alk
        run(lowCa, 1.0)
        assertTrue("calcification slows at Ca 280: ${la0 - lowCa.s.alk} vs $dAlk", la0 - lowCa.s.alk < 0.7 * dAlk)
    }

    /** Weekly 10 % water changes hold Sr near the salt-mix level against the slow skeletal uptake (01 §2.6: consumption is slow). */
    @Test
    fun strontiumConsumptionIsSlow() {
        val sim = TestTank.sim(DefaultStock.corals)
        sim.advanceAbstract(28 * 86400.0)
        val sr = sim.water.s.sr
        println("W-2 Sr after 4 weeks with corals: ${"%.2f".format(sr)}")
        assertTrue("Sr $sr", sr in 7.0..8.0)
    }

    /** 01 §1.1: K outside 340-460 ppm stresses inverts and corals; fish have no K entry. Sr < 6 adds mild coral stress. */
    @Test
    fun potassiumAndStrontiumEffects() {
        val sim = TestTank.sim(DefaultStock.all)
        val shrimp = sim.organisms.first { it.brainKind == BrainKind.CLEANER_SHRIMP }
        val fish = sim.organisms.first { it.brainKind == BrainKind.CLOWN }
        val lowK = WaterState(k = 300.0)
        HealthModel.envStress(shrimp, lowK)
        HealthModel.envStress(fish, lowK)
        assertTrue("shrimp should feel low K: ${shrimp.envStress}", (shrimp.envStress["k"] ?: 0.0) > 0.3)
        assertTrue("fish have no K tolerance: ${fish.envStress}", "k" !in fish.envStress)
        assertEquals("potassium", HealthModel.label("k"))

        fun coralStress(w: WaterState): Double {
            val c = sim.corals.first { it.kind == CoralKind.ZOA }
            c.acclSg = Double.NaN
            CoralSim.step(c, w, 0.6, 300.0, 300.0, 1.0, 0.0, ArrayList())
            return c.stress
        }
        val base = coralStress(WaterState())
        val k = coralStress(WaterState(k = 300.0))
        val sr = coralStress(WaterState(sr = 4.0))
        println("W-2 coral stress base ${"%.3f".format(base)} lowK ${"%.3f".format(k)} lowSr ${"%.3f".format(sr)}")
        assertTrue("low K coral stress $k vs $base", k > base + 0.15)
        assertTrue("low Sr is mild: $sr vs $base", sr > base + 0.03 && sr < base + 0.25)
    }
}
