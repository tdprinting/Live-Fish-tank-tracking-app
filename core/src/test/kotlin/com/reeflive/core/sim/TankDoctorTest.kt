package com.reeflive.core.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Fix my tank": the tank check's plan (TankDoctor) and the skimmer cup it reads (research 01 §1.1, §3.4, §3.5). */
class TankDoctorTest {
    private val ok = CareState(skimmerCup = 0.2f, sockOn = true, sockAgeH = 10f, carbonOn = false)
    private fun kinds(l: List<CareFix>) = l.map { it.kind }

    @Test
    fun healthyTankNeedsNothing() {
        assertEquals(emptyList<CareFix>(), TankDoctor.diagnose(WaterState(), ok))
    }

    @Test
    fun upkeepIsDueFromTheEquipment() {
        val l = TankDoctor.diagnose(WaterState(), ok.copy(skimmerCup = 0.8f, sockAgeH = 60f))
        assertEquals(listOf(TankDoctor.Kind.EMPTY_SKIMMER, TankDoctor.Kind.SWAP_SOCK), kinds(l))
        assertTrue(Command.Maintain(MaintenanceTask.EMPTY_SKIMMER) in l[0].commands)
        assertTrue(Command.Maintain(MaintenanceTask.CHANGE_SOCK) in l[1].commands)
    }

    @Test
    fun highNitrateGetsACappedWaterChangeThatCountsBeforeTheDoses() {
        // 40 ppm nitrate: a 25 % change (not 75 %), and alk 7.6 is lifted by the Instant Ocean (10.5 dKH) before dosing
        val l = TankDoctor.diagnose(WaterState(no3 = 40.0, alk = 7.6), ok)
        val wc = l.first { it.kind == TankDoctor.Kind.WATER_CHANGE }
        assertEquals(0.25, wc.amount, 1e-9)
        assertTrue("over 25 ppm is urgent", wc.urgent)
        assertFalse("7.6 × 0.75 + 10.5 × 0.25 = 8.3 dKH needs no dose", TankDoctor.Kind.DOSE_ALK in kinds(l))
    }

    @Test
    fun dosesStayInsideADaysSafeSwingMagnesiumFirst() {
        val l = TankDoctor.diagnose(WaterState(alk = 6.5, ca = 360.0, mg = 1150.0), ok)
        assertEquals(listOf(TankDoctor.Kind.DOSE_MG, TankDoctor.Kind.DOSE_CA, TankDoctor.Kind.DOSE_ALK), kinds(l).filter { it.name.startsWith("DOSE") })
        for (f in l) {
            val c = f.commands.single() as? Command.Dose ?: continue
            assertTrue("${c.paramId} ${c.delta}", c.delta <= DoseCheck.DAILY_MAX.getValue(c.paramId) + 1e-9)
        }
    }

    @Test
    fun evaporationIsToppedOffBeforeSalinityIsJudged() {
        // 3 % evaporated: SG 1.0265 falls back to ~1.0257 with the top-off, so no salt swap is planned on top
        val l = TankDoctor.diagnose(WaterState(sg = 1.0265), ok.copy(waterFull = 0.97f))
        assertEquals(listOf(TankDoctor.Kind.TOP_OFF), kinds(l))
        // a full tank that is too salty swaps a little salt water for RO, about 0.001 SG worth
        val salty = TankDoctor.diagnose(WaterState(sg = 1.0285), ok).single()
        assertEquals(TankDoctor.Kind.LOWER_SALT, salty.kind)
        val after = SupplyPreview.afterWaterChange(WaterState(sg = 1.0285), salty.amount, TankDoctor.RO_WATER)
        assertEquals(1.0275, after.sg, 2e-4)
    }

    @Test
    fun runningEveryFixBringsAMessyTankBack() {
        val w = WaterSim(TankConfig(), WaterState(no3 = 30.0, po4 = 0.2, alk = 7.4, mg = 1220.0))
        w.filterSock = true
        w.skimmerCup = 0.9
        w.ato = true
        w.step(60.0)
        val plan = TankDoctor.diagnose(w.s, w.careState())
        assertTrue(kinds(plan).toString(), kinds(plan).containsAll(listOf(TankDoctor.Kind.EMPTY_SKIMMER, TankDoctor.Kind.WATER_CHANGE)))
        for (f in plan) for (c in f.commands) when (c) {
            is Command.Maintain -> w.maintain(c.task)
            is Command.WaterChange -> w.waterChange(c.fraction, c.mix)
            is Command.Dose -> w.dose(c.paramId, c.delta)
            else -> Unit
        }
        repeat(120) { w.step(60.0) }   // the doses drip in over two hours
        assertEquals(0.0, w.skimmerCup, 0.01)
        assertTrue("no3 ${w.s.no3}", w.s.no3 < 25.0)
        assertTrue("alk ${w.s.alk}", w.s.alk > 7.9)
    }

    @Test
    fun skimmerCupFillsInAboutAWeekAndAFullCupStopsSkimming() {
        val w = WaterSim(TankConfig(), WaterState())
        w.ato = true; w.skimmerEmptyDays = 0.0
        w.emptySkimmerCup()
        // a bare WaterSim has no fish making DOC: hold the stocked tank's usual 1.5 mg/L (WaterState default)
        repeat(7 * 24) { w.s.doc = 1.5; repeat(60) { w.step(60.0) } }
        assertTrue("cup after a week ${w.skimmerCup}", w.skimmerCup in 0.35..0.95)
        w.skimmerCup = 1.0
        assertEquals(0.0, w.skimEfficiency, 1e-9)
        w.maintain(MaintenanceTask.EMPTY_SKIMMER)
        assertEquals(1.0, w.skimEfficiency, 1e-9)
    }
}
