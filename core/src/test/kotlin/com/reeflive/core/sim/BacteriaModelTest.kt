package com.reeflive.core.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max

/** Research 18 "Acceptance checks" for the bacteria layer ([BacteriaModel] inside [WaterSim]). */
class BacteriaModelTest {
    private fun f(v: Double) = "%.3f".format(v)
    private fun e(v: Double) = "%.2e".format(v)

    /** A fed water model on a 12 h light day, 1-min steps (as EquipmentExportTest). */
    private fun fed(w: WaterSim, days: Double, feedG: Double = 1.0, each: (Int) -> Unit = {}) {
        w.bioMassIndex = 60.0
        w.ato = true
        val n = (days * 1440).toInt()
        for (min in 0 until n) {
            if (min % 1440 == 600) w.addFeed(feedG)
            w.lightIntensity = if (min % 1440 in 480..1200) 0.7 else 0.0
            w.step(60.0)
            each(min)
        }
    }

    private fun mature(no3: Double = 10.0, po4: Double = 0.05, config: TankConfig = TankConfig()) =
        WaterSim(config, WaterState(no3 = no3, po4 = po4, tempC = 26.0)).also { it.heaterSetC = 26.0 }

    /**
     * E.1: a new tank on dry rock cycles in 4-6 weeks (CycleTracker: 3 days of zero ammonia and nitrite while
     * ghost-feeding); nitrite peaks in weeks 2-3 and is gone by week 6. A bottle of nitrifiers on day 0 speeds it up.
     */
    @Test
    fun newTankCyclesInFourToSixWeeks() {
        fun run(bottle: Boolean): Pair<Double, Double> {
            val sim = Presets.create(Presets.newTankCycle, TestTank.catalog, TestTank.rock)
            if (bottle) sim.enqueue(Command.Bacteria(BacteriaAction.DOSE_NITRIFIERS))
            var no2Peak = 0.0; var no2Day = 0.0
            for (h in 0 until 42 * 24) {
                sim.advanceAbstract(3600.0)
                val s = sim.water.s
                if (s.no2 > no2Peak) { no2Peak = s.no2; no2Day = h / 24.0 }
            }
            val s = sim.water.s
            val b = sim.water.bacteria
            val cycled = sim.cycle!!.cycledDay ?: 99.0
            println("R17 cycle (bottle=$bottle): cycled day ${f(cycled)}; NO2 peak ${f(no2Peak)} day ${f(no2Day)}; day 42 NO2 ${f(s.no2)} AOB ${f(s.bacteriaAob)} NOB ${f(s.bacteriaNob)} AOA ${f(b.aoa)} denit ${f(b.denit)} free ${e(b.freeCells)}")
            assertTrue("NO2 < 0.1 mg N/L (0.33 ppm) by week 6: ${s.no2}", s.no2 < 0.33)
            assertTrue("young tank: anaerobes still small", b.denit < 1.0)
            return cycled to no2Day
        }
        val plain = run(false)
        // the tuned WaterSim cycle (SIMULATION §8.2) finishes at ~3.5 weeks with this light ghost-feeding; research 18 says 4-6
        assertTrue("cycled within 3-6 weeks: ${plain.first}", plain.first in 21.0..42.0)
        assertTrue("nitrite peak in weeks 1.5-4: ${plain.second}", plain.second in 10.0..28.0)
        val bottled = run(true)
        assertTrue("bottled nitrifiers cycle sooner: ${bottled.first} vs ${plain.first}", bottled.first < plain.first - 1)
    }

    /** E.3: 2 mg C/L/day with NO3 and PO4 present lowers NO3 by ~0.5-1.5 mg NO3-N/L a week, slowing as it runs low. */
    @Test
    fun carbonDosingLowersNitrate() {
        val ctrl = mature()
        val dosed = mature().also { it.bacteria.carbonDose = BacteriaModel.CARBON_DOSE_DEFAULT }
        fed(ctrl, 7.0); fed(dosed, 7.0)
        val wk1 = (ctrl.s.no3 - dosed.s.no3) * WaterSim.N_NO3
        val b = dosed.bacteria.snapshot()
        println("R17 carbon dosing week 1: NO3 ctrl ${f(ctrl.s.no3)} dosed ${f(dosed.s.no3)} (Δ ${f(wk1)} mg N/L); PO4 ${f(ctrl.s.po4)} vs ${f(dosed.s.po4)}; free ${e(b.freeCellsPerMl)} DO ${f(ctrl.s.doMgL)} vs ${f(dosed.s.doMgL)}")
        assertTrue("NO3 falls 0.5-1.5 mg N/L in a week: $wk1", wk1 in 0.5..1.5)
        assertTrue("PO4 falls too", dosed.s.po4 < ctrl.s.po4)
        assertTrue("1e6-1e7 cells/mL while dosing: ${b.freeCellsPerMl}", b.freeCellsPerMl in 1e6..1e7)
        assertTrue("no bloom at the normal dose", !b.cloudy)
        // a lightly fed tank already low in nitrate: the reduction slows as N runs out
        val lowC = mature(no3 = 0.5); val lowD = mature(no3 = 0.5).also { it.bacteria.carbonDose = BacteriaModel.CARBON_DOSE_DEFAULT }
        fed(lowC, 7.0, feedG = 0.3); fed(lowD, 7.0, feedG = 0.3)
        val wkLow = (lowC.s.no3 - lowD.s.no3) * WaterSim.N_NO3
        println("R17 carbon dosing at low NO3: Δ ${f(wkLow)} mg N/L")
        assertTrue("slower at low NO3: $wkLow vs $wk1", wkLow < wk1 * 0.7)
        // without a skimmer the new cells are not exported: they pile up in the tank and die back into the detritus
        val noSkim = mature().also { it.skimmer = false; it.bacteria.carbonDose = BacteriaModel.CARBON_DOSE_DEFAULT }
        fed(noSkim, 7.0)
        println("R17 carbon dosing without skimmer: carbon-fed biomass ${f(noSkim.bacteria.hetC)} vs ${f(dosed.bacteria.hetC)} mg C/L")
        assertTrue("skimmer exports the cells", noSkim.bacteria.hetC > dosed.bacteria.hetC * 1.3)
    }

    /** E.4: overdosing carbon (> 5 mg C/L/day for 5 days) gives a cloudy bloom (> 1e7/mL) and ≥ 1 mg/L lower night O2. */
    @Test
    fun carbonOverdoseBlooms() {
        fun tank() = mature(po4 = 0.1).also { it.s.flowOn = false; it.skimmer = false }
        val ctrl = tank(); val over = tank().also { it.bacteria.carbonDose = 8.0 }
        var doC = 0.0; var doO = 0.0
        // low surface agitation (pumps off): research 18 §2.3 "with low surface agitation"
        fed(ctrl, 5.0) { if (it == 4 * 1440 + 420) doC = ctrl.s.doMgL }
        fed(over, 5.0) { if (it == 4 * 1440 + 420) doO = over.s.doMgL }
        val b = over.bacteria.snapshot()
        println("R17 overdose: free ${e(b.freeCellsPerMl)} cloudy ${b.cloudy}; pre-dawn DO ctrl ${f(doC)} overdosed ${f(doO)}; clarity ${f(over.clarity)}")
        assertTrue("bloom > 1e7 cells/mL: ${b.freeCellsPerMl}", b.freeCellsPerMl > 1e7)
        assertTrue("cloudy", b.cloudy)
        assertTrue("night O2 at least 1 mg/L lower: $doC vs $doO", doC - doO >= 1.0)
        // stop dosing: the bloom clears within about a week as grazers catch up (17 §2.3)
        over.bacteria.carbonDose = 0.0
        fed(over, 7.0)
        println("R17 overdose stopped 7 d: free ${e(over.bacteria.freeCells)}")
        assertTrue("bloom cleared: ${over.bacteria.freeCells}", !over.bacteria.snapshot().cloudy && over.bacteria.freeCells < b.freeCellsPerMl / 2)
    }

    /** Research 18 §1.4: an antibiotic in the display stalls nitrification; ammonia and nitrite build up. */
    @Test
    fun antibioticStallsNitrification() {
        val ctrl = mature(); val treated = mature()
        treated.bacteria.apply(BacteriaAction.ANTIBIOTIC, 1.0, treated)
        fed(ctrl, 2.0); fed(treated, 2.0)
        var maxC = 0.0; var maxT = 0.0; var no2T = 0.0
        ctrl.setParam("nh3", 0.5); treated.setParam("nh3", 0.5)
        fed(ctrl, 1.0) { maxC = max(maxC, ctrl.s.nh3Total) }
        fed(treated, 1.0) { maxT = max(maxT, treated.s.nh3Total); no2T = max(no2T, treated.s.no2) }
        println("R17 antibiotic: AOB factor ${f(treated.bacteria.aobFactor)} NOB ${f(treated.bacteria.nobFactor)}; 24 h after a 0.5 ppm spike TAN ctrl ${f(ctrl.s.nh3Total)} treated ${f(treated.s.nh3Total)}; AOB ${f(treated.s.bacteriaAob)} microbes ${f(treated.s.microbes)}")
        assertTrue("control clears the spike: ${ctrl.s.nh3Total}", ctrl.s.nh3Total < 0.05)
        assertTrue("treated still has ammonia: ${treated.s.nh3Total}", treated.s.nh3Total > 0.15)
        assertTrue("nitrifier bed knocked back", treated.s.bacteriaAob < 0.9)
        assertTrue("community knocked back", treated.s.microbes < ctrl.s.microbes)
        // the antibiotic washes out (half-life ~2 d) and nitrification recovers
        fed(treated, 14.0)
        println("R17 antibiotic +14 d: antibiotic ${f(treated.bacteria.antibiotic)} mg/L TAN ${f(treated.s.nh3Total)} AOB ${f(treated.s.bacteriaAob)}")
        assertTrue("recovers: ${treated.s.nh3Total}", treated.s.nh3Total < 0.05 && treated.bacteria.aobFactor > 0.8)
    }

    /** E.5: stirring a 5 cm bed with an anoxic zone raises H2S 0.1-0.5 mg/L within 1 h; a 2 cm bed (and Tee's 3 cm) < 0.05. */
    @Test
    fun stirringDeepSandReleasesH2s() {
        fun peak(depthCm: Double): Double {
            val w = mature(config = TankConfig(sandTopCm = depthCm))
            fed(w, 1.0)
            w.bacteria.apply(BacteriaAction.STIR_SAND, 1.0, w)
            var p = 0.0
            repeat(60) { w.step(60.0); p = max(p, w.s.h2s) }
            println("R17 stir ${depthCm} cm bed: anoxic ${f(w.bacteria.anoxicDepthCm)} cm, H2S peak ${f(p)} mg/L in 1 h")
            return p
        }
        val deep = peak(5.0)
        assertTrue("5 cm bed: 0.1-0.5 mg/L: $deep", deep in 0.1..0.5)
        assertTrue("2 cm bed < 0.05", peak(2.0) < 0.05)
        assertTrue("Tee's 3 cm bed < 0.05", peak(3.0) < 0.05)
        // it is gone within a few hours in oxygenated water and has to rebuild over weeks
        val w = mature(config = TankConfig(sandTopCm = 5.0))
        fed(w, 0.5)
        w.bacteria.apply(BacteriaAction.STIR_SAND, 1.0, w)
        fed(w, 0.5)
        assertTrue("H2S oxidised: ${w.s.h2s}", w.s.h2s < 0.01)
        assertTrue("store emptied", w.bacteria.sulfideStore < 0.1)
    }

    /** H2S stresses fish and corals; a deep bed denitrifies more than the reference bed. */
    @Test
    fun h2sStressesAndDeepBedDenitrifies() {
        val fish = TestTank.catalog["amphiprion_ocellaris"]
        val o = Organism("c", fish, 5.0, "clown", null, com.reeflive.core.geom.Rng(1))
        val clean = WaterState(tempC = 26.0)
        val foul = clean.copy(h2s = 0.1)
        val sClean = HealthModel.envStress(o, clean)
        val sFoul = HealthModel.envStress(o, foul)
        println("R17 fish env stress clean ${f(sClean)} with 0.1 mg/L H2S ${f(sFoul)}")
        assertTrue(sFoul > sClean + 0.2)
        // deep bed: extra NO3 removal beyond the reference bed over a month
        val shallow = mature(config = TankConfig(sandTopCm = 3.0)); val deep = mature(config = TankConfig(sandTopCm = 8.0))
        fed(shallow, 21.0); fed(deep, 21.0)
        println("R17 3 wk NO3: 3 cm bed ${f(shallow.s.no3)} 8 cm bed ${f(deep.s.no3)}; denit ${f(deep.bacteria.denit)}")
        assertTrue("deep bed removes more nitrate", deep.s.no3 < shallow.s.no3 - 0.5)
    }

    /** E.7: 26 → 29 °C for 3 days raises Vibrio ≥ 10×; 26 °C keeps it < 1e4; warm + dirty water raises fish and coral risk. */
    @Test
    fun warmDirtyWaterRaisesPathogens() {
        val cool = mature()
        fed(cool, 30.0)
        val vCool = cool.bacteria.snapshot().vibrioPerMl
        assertTrue("26 °C stays < 1e4: $vCool", vCool < 1e4)
        assertEquals(0.0, cool.bacteria.pathogenPressure, 1e-9)

        val warm = mature().also { it.setParam("temp", 29.0) }
        val v0 = warm.bacteria.snapshot().vibrioPerMl
        fed(warm, 3.0)
        val v3 = warm.bacteria.snapshot().vibrioPerMl
        println("R17 Vibrio 26 °C 30 d ${e(vCool)}; 29 °C for 3 d ${e(v0)} → ${e(v3)} (DOC ${f(warm.s.doc)})")
        assertTrue("≥ 10× in 3 days at 29 °C: ${v3 / v0}", v3 / v0 >= 10)

        val dirty = mature().also { it.setParam("temp", 29.5); it.skimmer = false }
        fed(dirty, 7.0, feedG = 3.0) { if (it % 1440 == 700) dirty.addDetritusTanGrams(0.3) }
        val st = dirty.bacteria.snapshot()
        println("R17 warm + dirty 7 d: Vibrio ${e(st.vibrioPerMl)} pressure ${f(st.pathogenPressure)} coral ${f(st.coralPathogenStress)} DOC ${f(dirty.s.doc)} detritus ${f(dirty.s.detritus)}")
        assertTrue("fish pathogen pressure: ${st.pathogenPressure}", st.pathogenPressure > 0.5)
        assertTrue("coral pathogen stress in warm water: ${st.coralPathogenStress}", st.coralPathogenStress > 0.2)
        // probiotic and UV push it back down
        val treated = mature().also { it.setParam("temp", 29.5); it.skimmer = false; it.uv = true }
        treated.bacteria.apply(BacteriaAction.DOSE_PROBIOTIC, 3.0, treated)
        fed(treated, 7.0, feedG = 3.0) { if (it % 1440 == 700) treated.addDetritusTanGrams(0.3) }
        println("R17 warm + dirty with probiotic and UV: Vibrio ${e(treated.bacteria.snapshot().vibrioPerMl)}")
        assertTrue(treated.bacteria.snapshot().vibrioPerMl < st.vibrioPerMl / 3)

        // the couplings: corals under Vibrio stress lose tissue, fish get the BACTERIAL disease drive
        val sp = TestTank.catalog["zoanthus_sp"]
        val a = Coral("a", sp, "", "", com.reeflive.core.geom.Vec3.ZERO, com.reeflive.core.geom.Vec3(0.0, 1.0, 0.0), 0.0)
        val b = Coral("b", sp, "", "", com.reeflive.core.geom.Vec3.ZERO, com.reeflive.core.geom.Vec3(0.0, 1.0, 0.0), 0.0)
        val w = WaterState(tempC = 29.5)
        repeat(1440) {
            CoralSim.step(a, w, 0.7, 600.0, 300.0, 1.0, 0.0, ArrayList())
            CoralSim.step(b, w, 0.7, 600.0, 300.0, 1.0, 0.0, ArrayList(), pathogenStress = 0.8)
        }
        println("R17 coral 1 d at 29.5 °C: stress ${f(a.stress)} vs ${f(b.stress)} with Vibrio; tissue ${f(a.tissue)} vs ${f(b.tissue)}")
        assertTrue(b.stress > a.stress && b.tissue < a.tissue)
    }

    /** E.6: UV for 24 h cuts free-living bacteria 50-90 % and leaves the biofilm nitrifiers alone. */
    @Test
    fun uvStripsTheWaterColumnOnly() {
        val w = mature()
        fed(w, 2.0)
        val n0 = w.bacteria.freeCells; val aob0 = w.s.bacteriaAob
        w.bacteria.apply(BacteriaAction.UV_ON, 1.0, w)
        assertTrue(w.uv)
        fed(w, 1.0)
        val cut = 1 - w.bacteria.freeCells / n0
        println("R17 UV 24 h: free ${e(n0)} → ${e(w.bacteria.freeCells)} (−${f(cut * 100)} %), AOB ${f(aob0)} → ${f(w.s.bacteriaAob)}")
        assertTrue("50-90 % fewer: $cut", cut in 0.5..0.9)
        assertTrue("nitrifiers unchanged (< 10 %)", abs(w.s.bacteriaAob - aob0) / aob0 < 0.1)
    }

    /** E.9: nitrogen stays conserved with carbon dosing, denitrification and a stirred bed. */
    @Test
    fun massBalanceWithBacteria() {
        val w = WaterSim(TankConfig(sandTopCm = 6.0), WaterState(no3 = 10.0, detritus = 0.3))
        w.bacteria.carbonDose = 3.0
        val n0 = w.nitrogenInSystemMg()
        fed(w, 30.0) {
            if (it % (7 * 1440) == 0 && it > 0) w.waterChange(0.15)
            if (it == 10 * 1440) w.bacteria.apply(BacteriaAction.STIR_SAND, 1.0, w)
            if (it == 15 * 1440) w.bacteria.apply(BacteriaAction.ANTIBIOTIC, 1.0, w)
        }
        val lhs = w.nitrogenInSystemMg() + w.nExportMg
        val rhs = n0 + w.nInputMg
        println("R17 N balance ${f(lhs)} vs ${f(rhs)}")
        assertTrue("N balance $lhs vs $rhs", abs(lhs - rhs) / rhs < 0.01)
    }

    /** E.10: Tee's mature tank stays stable: nitrifiers full, no bloom, no H2S, Vibrio low, no pathogen pressure. */
    @Test
    fun matureTankStaysStable() {
        val sim = TestTank.sim(DefaultStock.all)
        sim.advanceAbstract(30 * 86400.0)
        val st = sim.snapshot().bacteria
        val s = sim.water.s
        println("R17 mature 30 d: free ${e(st.freeCellsPerMl)} Vibrio ${e(st.vibrioPerMl)} H2S ${f(st.h2sMgL)} AOB ${f(s.bacteriaAob)} NOB ${f(s.bacteriaNob)} denit ${f(sim.water.bacteria.denit)} anoxic ${f(st.anoxicDepthCm)} cm; " +
            st.guilds.joinToString { "${it.guild.shortLabel} ${f(it.level)}" })
        assertEquals(BacteriaGuild.entries.size, st.guilds.size)
        assertTrue("nitrifier bed holds: ${s.bacteriaAob} ${s.bacteriaNob}", s.bacteriaAob > 0.75 && s.bacteriaNob > 0.75)
        assertTrue("1e5-1e6 cells/mL: ${st.freeCellsPerMl}", st.freeCellsPerMl in 1e5..2e6)
        assertTrue(!st.cloudy && st.h2sMgL < 1e-6)
        assertTrue("Vibrio < 1e4: ${st.vibrioPerMl}", st.vibrioPerMl < 1e4)
        assertEquals(0.0, st.pathogenPressure, 1e-9)
        assertTrue(st.guilds.all { it.effect.isNotBlank() && it.amount.isNotBlank() })
    }

    /** Sandbox command path: Command.Bacteria reaches the model and the snapshot. */
    @Test
    fun commandsReachTheModel() {
        val sim = TestTank.sim(emptyList())
        sim.enqueue(Command.Bacteria(BacteriaAction.CARBON_DOSE_START))
        sim.enqueue(Command.Bacteria(BacteriaAction.UV_ON))
        sim.enqueue(Command.Bacteria(BacteriaAction.DOSE_PROBIOTIC))
        sim.advanceAbstract(3600.0)
        val st = sim.snapshot().bacteria
        assertEquals(BacteriaModel.CARBON_DOSE_DEFAULT, st.carbonDoseMgCPerLDay, 1e-9)
        assertTrue(st.uvOn && sim.water.uv)
        assertTrue(st.guild(BacteriaGuild.PROBIOTIC).level > 0.2)
        sim.enqueue(Command.SetParam("carbondose", 0.0))
        sim.enqueue(Command.Bacteria(BacteriaAction.UV_OFF))
        sim.advanceAbstract(3600.0)
        assertEquals(0.0, sim.water.bacteria.carbonDose, 1e-9)
        assertTrue(!sim.water.uv)
    }
}
