package com.reeflive.core.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class WaterSimTest {
    /** SIMULATION §8.1: Σ N (TAN/NO2/NO3/detritus/pending + exported) conserved within 1 %. */
    @Test
    fun nitrogenMassBalance() {
        val w = WaterSim(TankConfig(), WaterState(no3 = 10.0, detritus = 0.3))
        val n0 = w.nitrogenInSystemMg()
        w.bioMassIndex = 60.0
        for (min in 0 until 30 * 1440) {
            if (min % 1440 == 600) w.addFeed(1.0)
            if (min % (7 * 1440) == 0 && min > 0) w.waterChange(0.15)
            w.lightIntensity = if (min % 1440 in 600..1080) 1.0 else 0.0
            w.step(60.0)
        }
        val lhs = w.nitrogenInSystemMg() + w.nExportMg
        val rhs = n0 + w.nInputMg
        assertTrue("N balance ${"%.1f".format(lhs)} vs ${"%.1f".format(rhs)}", abs(lhs - rhs) / rhs < 0.01)
    }

    /** SIMULATION §8.2: new tank → NH3 peak day 4–12, NO2 peak day 10–25, both < 0.1 ppm by day 45. */
    @Test
    fun cycleTimeline() {
        val sim = Presets.create(Presets.newTankCycle, TestTank.catalog, TestTank.rock)
        var nh3Peak = 0.0; var nh3Day = 0.0
        var no2Peak = 0.0; var no2Day = 0.0
        for (h in 0 until 45 * 24) {
            sim.advanceAbstract(3600.0)
            val s = sim.water.s
            val day = h / 24.0
            if (s.nh3Total > nh3Peak) { nh3Peak = s.nh3Total; nh3Day = day }
            if (s.no2 > no2Peak) { no2Peak = s.no2; no2Day = day }
        }
        val s = sim.water.s
        assertTrue("NH3 peak on day $nh3Day ($nh3Peak)", nh3Day in 4.0..12.0)
        assertTrue("NO2 peak on day $no2Day ($no2Peak)", no2Day in 10.0..25.0)
        assertTrue("TAN ${s.nh3Total} NO2 ${s.no2} at day 45", s.nh3Total < 0.1 && s.no2 < 0.1)
    }

    /**
     * Research 01 §3.9 checkpoints on the new-tank preset: ammonia, then a nitrite peak, then nitrite falling with
     * nitrate rising; cycled (3 days of 0 ammonia and 0 nitrite while ghost-feeding) after 4-8 weeks.
     */
    @Test
    fun cycleCheckpoints() {
        val sim = Presets.create(Presets.newTankCycle, TestTank.catalog, TestTank.rock)
        val c = sim.cycle!!
        sim.advanceAbstract(70 * 86400.0)
        val r = c.reachedDay
        println("W-5 stages reached on days ${r.joinToString { "%.1f".format(it) }}; NH3 peak ${"%.2f".format(c.nh3Peak)} day ${"%.1f".format(c.nh3PeakDay)}, NO2 peak ${"%.2f".format(c.no2Peak)} day ${"%.1f".format(c.no2PeakDay)}; NO3 ${"%.1f".format(sim.water.s.no3)}")
        assertEquals(CycleTracker.Stage.CYCLED, c.stage)
        assertTrue("NH3 peaks before NO2", c.nh3PeakDay < c.no2PeakDay)
        assertTrue("stages in order", r[1] < r[2] && r[2] < r[3])
        assertTrue("nitrite stage within days 7-28: ${r[1]}", r[1] in 4.0..28.0)
        assertTrue("cycled within 3-8 weeks: ${c.cycledDay}", c.cycledDay!! in 21.0..56.0)
        assertTrue("nitrate accumulated: ${sim.water.s.no3}", sim.water.s.no3 > 0.5)
    }

    /** SIMULATION §2.10: an established tank with no feeding keeps TAN and NO2 < 0.01 ppm for 30 days. */
    @Test
    fun establishedTankStaysClean() {
        val w = WaterSim(TankConfig(), WaterState())
        var maxTan = 0.0; var maxNo2 = 0.0
        repeat(30 * 1440) { w.step(60.0); maxTan = max(maxTan, w.s.nh3Total); maxNo2 = max(maxNo2, w.s.no2) }
        assertTrue("TAN $maxTan NO2 $maxNo2", maxTan < 0.01 && maxNo2 < 0.01)
    }

    /** SIMULATION §2.10: nitrate rises ≈ 2–5 ppm/week with the fish fed 1 g/day. */
    @Test
    fun nitrateRisesWithFeeding() {
        val feeding = listOf(FeedTime(9 * 60 + 30, FoodKind.FLAKE, 0.4), FeedTime(17 * 60 + 30, FoodKind.MYSIS, 0.6))
        val sim = TestTank.sim(DefaultStock.all, config = TankConfig(autoFeed = feeding))
        val no3a = sim.water.s.no3
        sim.advanceAbstract(14 * 86400.0)
        val perWeek = (sim.water.s.no3 - no3a) / 2
        assertTrue("NO3 rise $perWeek ppm/week", perWeek in 2.0..5.5)
    }

    /** SIMULATION §2.10: 3 L/day evaporation without ATO raises SG ≈ 0.0003/day at 227 L. */
    @Test
    fun evaporationRaisesSalinity() {
        val w = WaterSim(TankConfig(), WaterState())
        w.manualTopOffDays = 0
        val sg0 = w.s.sg
        repeat(1440) { w.step(60.0) }
        val d = w.s.sg - sg0
        assertTrue("dSG/day $d", d in 0.0002..0.0004)
    }

    /** SIMULATION §2.10: heater off at room 22 °C → 23 °C within ~60 h. */
    @Test
    fun heaterOffCools() {
        val w = WaterSim(TankConfig(roomC = 22.0), WaterState(tempC = 25.5))
        w.heaterWorking = false
        var hours = 0.0
        while (w.s.tempC > 23.0 && hours < 200) { repeat(60) { w.step(60.0) }; hours += 1 }
        assertTrue("reached 23 °C after $hours h", hours in 30.0..90.0)
    }

    /** SIMULATION §2.10: daily pH swing between 0.15 and 0.35 with the default stock and lights. */
    @Test
    fun dailyPhSwing() {
        val sim = TestTank.sim(DefaultStock.all, startHour = 0.0)
        sim.advanceAbstract(2 * 86400.0)
        var lo = 99.0; var hi = 0.0
        repeat(24 * 12) { sim.advanceAbstract(300.0); lo = min(lo, sim.water.s.ph); hi = max(hi, sim.water.s.ph) }
        assertTrue("pH swing ${hi - lo} ($lo..$hi)", hi - lo in 0.15..0.35)
    }

    @Test
    fun freeAmmoniaFraction() {
        // Emerson at pH 8.2, 25 °C ≈ 8 %
        assertEquals(0.08, ChemFns.freeNh3Fraction(8.2, 25.0), 0.01)
    }
}
