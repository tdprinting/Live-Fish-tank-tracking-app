package com.reeflive.core.sim

import com.reeflive.core.geom.FluidQuality
import com.reeflive.core.geom.FluidSim
import com.reeflive.core.geom.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sandbox's real-unit controls at their extremes (research 09 Part B; 05 §14-16): light as absolute PAR 0-1500,
 * flow as tank turnover 0-100×/h, and skipping the routine water changes.
 */
class SandboxExtremesTest {
    private fun f(v: Double) = "%.3f".format(v)

    private fun stocked(seed: Long = 3L) = TestTank.sim(DefaultStock.all, seed = seed).also { sim ->
        sim.organisms.filter { it.brainKind == BrainKind.WRASSE }.forEach { it.acceptsPrepared = true }
        sim.advanceAbstract(86400.0)
    }

    private fun coral(speciesId: String, id: String = speciesId) =
        Coral(id, TestTank.catalog[speciesId], id, "", Vec3(40.0, 20.0, 20.0), Vec3.Y, 0.0)

    private fun goodWater() = WaterState(tempC = 25.5, no3 = 5.0, po4 = 0.04, i = 0.06)

    /** [days] of a 10-h day at rock-top [par] (0 = a blackout), 10-min steps; [each] runs after every day. */
    private fun live(c: Coral, w: WaterState, days: Int, par: Double, startDay: Int = 0, each: (Int) -> Unit = {}) {
        val light = par / LightSchedule.PAR_PER_INTENSITY
        for (d in startDay until startDay + days) {
            for (m in 0 until 24 * 6) {
                val minute = m * 10.0
                val on = par > 0 && minute in 600.0..1200.0
                CoralSim.step(c, w, if (on) light else 0.0, if (on) minute - 600 else null, if (on) 1200 - minute else null, 10.0, (d * 1440 + minute) * 60, ArrayList())
            }
            each(d + 1)
        }
    }

    // ================================================================== light as absolute PAR

    /** The schedule keeps its shape and is scaled to the chosen peak; the default peak is ~300 PAR; moonlight is not scaled. */
    @Test
    fun parScalesTheScheduleToTheChosenPeak() {
        val l = LightSchedule()
        assertEquals(300.0, l.programmedPeakPar, 10.0)
        val noon = 14 * 3600.0; val ramp = 9 * 3600.0 + 15 * 60; val night = 2 * 3600.0
        // untouched: the biology sees exactly the drawn lamps
        for (t in listOf(noon, ramp, night)) assertEquals(l.intensity(t), l.bioIntensity(t), 1e-12)
        val base = doubleArrayOf(l.intensity(noon), l.intensity(ramp))
        l.peakPar = 1500.0
        assertEquals(1500.0, l.bioIntensity(noon) * LightSchedule.PAR_PER_INTENSITY, 1.0)
        // same shape: the morning ramp is the same share of the peak
        assertEquals(base[1] / base[0], l.bioIntensity(ramp) / l.bioIntensity(noon), 1e-9)
        assertTrue("night stays dark: ${l.bioIntensity(night)}", l.bioIntensity(night) < LightSchedule.LIGHTS_ON)
        assertTrue("lamps saturate when drawn", l.intensity(noon) <= 1.0)
        l.peakPar = 0.0
        assertTrue("blackout: ${l.bioIntensity(noon)}", l.bioIntensity(noon) < LightSchedule.LIGHTS_ON)
        l.peakPar = 9999.0
        assertEquals(LightSchedule.MAX_PAR, l.peakPar!!, 1e-9)
    }

    /** The sim reads and reports the absolute PAR: the slider value, the live rock-top PAR and the snapshot. */
    @Test
    fun simulationUsesTheAbsolutePar() {
        val sim = TestTank.sim(emptyList(), startHour = 13.0)
        assertEquals(300.0, sim.paramValue("par")!!, 10.0)
        sim.enqueue(Command.SetParam("par", 1200.0))
        sim.advanceAbstract(600.0)
        assertEquals(1200.0, sim.paramValue("par")!!, 1e-9)
        assertEquals(1200f, sim.latest.peakPar, 1e-3f)
        val live = sim.water.s.lightPar
        assertTrue("live rock-top PAR $live", live in 900.0..1200.0)
        // the old relative dial still works and hands back to the schedule
        sim.enqueue(Command.SetParam("light", 1.0))
        sim.advanceAbstract(600.0)
        assertEquals(sim.lights.programmedPeakPar, sim.paramValue("par")!!, 1e-9)
    }

    /**
     * 1500 PAR bleaches an unacclimated soft coral within ~1-3 days and melts tissue within ~2 weeks; 800 PAR is slower;
     * this tank's normal ~300 PAR leaves it healthy (09 Part B light row; 05 §14).
     */
    @Test
    fun highParBleachesAndBurnsSoftCorals() {
        val normal = coral("sarcophyton_sp", "normal").also { live(it, goodWater(), 14, 300.0) }
        val strong = coral("sarcophyton_sp", "strong")
        var strongDay2 = 0.0
        live(strong, goodWater(), 7, 800.0) { d -> if (d == 2) strongDay2 = strong.bleach }
        val blast = coral("sarcophyton_sp", "blast")
        var paleDay = -1
        var blastDay2 = 0.0
        live(blast, goodWater(), 14, 1500.0) { d ->
            if (paleDay < 0 && blast.bleach > 0.5) paleDay = d
            if (d == 2) blastDay2 = blast.bleach
        }
        println("PAR 300: bleach ${f(normal.bleach)} tissue ${f(normal.tissue)}; 800 for 7 d: bleach ${f(strong.bleach)}; " +
            "1500: pale on day $paleDay, tissue ${f(blast.tissue)} after 14 d, need ${blast.need}")
        assertTrue("healthy at 300 PAR: ${normal.bleach}", normal.bleach < 0.05 && normal.tissue > 0.97)
        assertTrue("1500 PAR pales it within 3 days (day $paleDay)", paleDay in 1..3)
        assertTrue("1500 PAR burns tissue within 2 weeks: ${blast.tissue}", blast.tissue < 0.5)
        assertTrue("800 PAR is slower than 1500: $strongDay2 vs $blastDay2", strongDay2 < blastDay2)
        assertTrue("800 PAR pales it within a week: ${strong.bleach}", strong.bleach > 0.3)
        assertEquals(CoralNeed.LIGHT_HIGH.ordinal, blast.need)
    }

    /** PAR 0 for weeks: a soft coral tolerates 3-5 days, shrinks by ~10 days, dies after 3-4 weeks (09 Part B). */
    @Test
    fun blackoutStarvesSoftCoralsOverWeeks() {
        val c = coral("sarcophyton_sp")
        live(c, goodWater(), 2, 300.0)
        val ext0 = c.extension
        var day4 = Pair(0.0, 0.0); var day12 = 0.0
        live(c, goodWater(), 28, 0.0, startDay = 2) { d ->
            if (d == 6) day4 = Pair(c.bleach, c.tissue)
            if (d == 14) day12 = c.extension
        }
        println("blackout: day 4 bleach ${f(day4.first)} tissue ${f(day4.second)}; day 12 extension ${f(day12)} (was ${f(ext0)}); day 28 tissue ${f(c.tissue)} need ${c.need}")
        assertTrue("tolerates the first days: $day4", day4.first < 0.15 && day4.second > 0.95)
        assertTrue("shrinks by day 12: $day12 vs $ext0", day12 < 0.5 * ext0)
        assertTrue("dead or nearly after 4 weeks: ${c.tissue}", c.tissue < 0.3)
        assertEquals(CoralNeed.LIGHT_LOW.ordinal, c.need)
    }

    /**
     * Algae run on the absolute PAR: hair needs light and saturates (5× the light from 300 to 1500 PAR gives far less than
     * 5× the growth); cyano on the rock tops loses ground above 600 PAR (09 Part B).
     */
    @Test
    fun algaeFollowTheAbsolutePar() {
        fun bench(par: Double, days: Int, setup: (AlgaeModel) -> Unit = {}, each: (AlgaeModel) -> Unit = {}): AlgaeModel {
            val w = WaterState(no3 = 10.0, po4 = 0.1, algaeHair = 0.2, cyano = 0.2, diatoms = 0.0, dinos = 0.0, film = 0.0, bubble = 0.0, coralline = 0.0)
            val m = AlgaeModel(TankConfig())
            setup(m)
            val l = LightSchedule().also { it.peakPar = par }
            var t = 0.0
            repeat(days * 96) {
                w.no3 = 10.0; w.po4 = 0.1
                m.step(w, 0.25, l.bioIntensity(t), 1.0)
                if (it >= 96) each(m)
                t += 900.0
            }
            return m
        }
        fun hair(par: Double): Double {
            var sum = 0.0
            bench(par, 4, each = { m -> sum += m.growthPerDay[AlgaeGroup.HAIR.ordinal] / m.biomass(AlgaeGroup.HAIR) })
            return sum / (3 * 96)
        }
        val h40 = hair(40.0); val h300 = hair(300.0); val h1500 = hair(1500.0)
        val lowFlow = { m: AlgaeModel -> m.docOverride = 0.5; for (s in AlgaeSurface.entries) m.flowOverride[s.ordinal] = 2.0 }
        val c500 = bench(500.0, 8, lowFlow).cover(AlgaeGroup.CYANO, AlgaeSurface.ROCK_TOP)
        val c1500 = bench(1500.0, 8, lowFlow).cover(AlgaeGroup.CYANO, AlgaeSurface.ROCK_TOP)
        println("hair specific growth: 40 PAR ${f(h40)}, 300 ${f(h300)}, 1500 ${f(h1500)}; rock-top cyano cover after 8 d: 500 PAR ${f(c500)}, 1500 ${f(c1500)}")
        assertTrue("hair starves in dim light: $h40 vs $h300", h40 < 0.5 * h300)
        assertTrue("hair saturates: $h1500 vs $h300", h1500 < 2.0 * h300)
        assertTrue("cyano photoinhibited at 1500: $c1500 vs $c500", c1500 < 0.8 * c500)
    }

    // ================================================================== flow as turnover

    /** Turnover maps onto the pumps that are on: ~27×/h normal, 100× reachable (no 2× cap), one pump off works harder. */
    @Test
    fun turnoverMapsOntoThePumps() {
        val sim = TestTank.sim(emptyList())
        assertEquals(27.5, sim.turnoverPerHour, 1.0)
        assertEquals(sim.turnoverPerHour, sim.paramValue("turnover")!!, 1e-9)
        sim.enqueue(Command.SetParam("turnover", 100.0))
        sim.advanceAbstract(60.0)
        assertEquals(100.0, sim.turnoverPerHour, 1e-6)
        assertTrue("strength past the old 2× cap: ${sim.flowStrength}", sim.flowStrength > 3.5)
        assertEquals(100f, sim.latest.turnover, 1e-3f)
        val both = sim.flowStrength
        sim.enqueue(Command.SetParam("pump.PH-A", 0.0))
        sim.enqueue(Command.SetParam("turnover", 100.0))
        sim.advanceAbstract(60.0)
        assertEquals(100.0, sim.turnoverPerHour, 1e-6)
        assertTrue("the remaining pumps run harder: ${sim.flowStrength} vs $both", sim.flowStrength > both)
        sim.enqueue(Command.SetParam("turnover", 0.0))
        sim.advanceAbstract(60.0)
        assertEquals(0.0, sim.turnoverPerHour, 1e-9)
        assertTrue("pumps off at 0", !sim.water.s.flowOn)
    }

    /**
     * 100×/h: soft-coral polyps are blasted shut and abrade, sand blows about and clouds the water, fish struggle, hide
     * and stop eating; turnover 0: oxygen falls and cyano takes off (09 Part B flow row; 05 §15-16).
     */
    @Test
    fun extremeTurnoverEffects() {
        val calm = stocked()
        val blast = stocked()
        blast.enqueue(Command.SetParam("turnover", 100.0))
        val sand0 = blast.sand.movedCm3
        calm.advanceAbstract(12 * 3600.0); blast.advanceAbstract(12 * 3600.0)
        fun softExt(s: Simulation) = s.corals.filter { it.kind == CoralKind.KENYA || it.kind == CoralKind.LEATHER || it.kind == CoralKind.ZOA }.map { it.extension }.average()
        val fishB = blast.organisms.filter { it.alive && it.isFish && it.brainKind != BrainKind.EEL }
        val hiding = fishB.count { Symptom.HIDING in it.symptoms }
        val appetite = fishB.map { HealthModel.appetite(it) }.average()
        val calmAppetite = calm.organisms.filter { it.alive && it.isFish && it.brainKind != BrainKind.EEL }.map { HealthModel.appetite(it) }.average()
        println("100×: soft-coral extension ${f(softExt(blast))} vs ${f(softExt(calm))}; fish hiding $hiding/${fishB.size}, appetite ${f(appetite)} vs ${f(calmAppetite)}; " +
            "sand moved ${f(blast.sand.movedCm3 - sand0)} cm³, haze ${f(blast.water.sandHaze)}, clarity ${f(blast.water.clarity)} vs ${f(calm.water.clarity)}")
        assertTrue("polyps blasted shut", softExt(blast) < 0.4 * softExt(calm))
        assertTrue("fish hide: $hiding of ${fishB.size}", hiding >= fishB.size * 0.8)
        assertTrue("fish stop feeding: $appetite vs $calmAppetite", appetite < 0.6 * calmAppetite)
        assertTrue("sand storm moves sand", blast.sand.movedCm3 - sand0 > 50.0)
        assertTrue("water clouds: ${blast.water.clarity}", blast.water.sandHaze > 0.5 && blast.water.clarity < calm.water.clarity - 0.2)
        // a week of it abrades the soft corals
        blast.advanceAbstract(7 * 86400.0); calm.advanceAbstract(7 * 86400.0)
        val tissueB = blast.corals.filter { it.kind == CoralKind.KENYA }.map { it.tissue }.average()
        val tissueC = calm.corals.filter { it.kind == CoralKind.KENYA }.map { it.tissue }.average()
        assertTrue("soft corals abrade in a blast: $tissueB vs $tissueC", tissueB < tissueC - 0.2)
        // the flow returns to normal: the haze settles
        blast.enqueue(Command.SetParam("turnover", 27.0))
        blast.advanceAbstract(6 * 3600.0)
        assertTrue("haze settles: ${blast.water.sandHaze}", blast.water.sandHaze < 0.05)

        // no flow: oxygen falls and cyano takes off
        val still = stocked()
        val control = stocked()
        val do0 = still.water.s.doMgL
        still.enqueue(Command.SetParam("turnover", 0.0))
        still.advanceAbstract(12 * 3600.0); control.advanceAbstract(12 * 3600.0)
        assertTrue("DO ${still.water.s.doMgL} vs ${control.water.s.doMgL} (was $do0)", still.water.s.doMgL < control.water.s.doMgL - 0.5)
        val cy = { s: Simulation -> s.water.algae.growthPerDay[AlgaeGroup.CYANO.ordinal] / s.water.algae.biomass(AlgaeGroup.CYANO) }
        // (to midday, so both are photosynthesising when the growth is read)
        still.advanceAbstract(2.5 * 86400.0); control.advanceAbstract(2.5 * 86400.0)
        println("0×: DO ${f(still.water.s.doMgL)} vs ${f(control.water.s.doMgL)}; cyano ${f(still.water.s.cyano)} vs ${f(control.water.s.cyano)}, growth ${f(cy(still))} vs ${f(cy(control))}")
        assertTrue("cyano grows faster with no flow", cy(still) > 1.5 * cy(control))
        assertTrue("cyano spreads: ${still.water.s.cyano} vs ${control.water.s.cyano}", still.water.s.cyano > control.water.s.cyano)
    }

    /** Nothing goes NaN or runs away at the extremes: turnover 0 ↔ 100, PAR 0 ↔ 1500, with behaviour and the flow solver on. */
    @Test
    fun extremesStayFiniteAndStable() {
        val sim = stocked(5L)
        fun check(tag: String) {
            val w = sim.water.s
            for (k in listOf("temp", "sal", "ph", "do", "alk", "ca", "mg", "nh3", "no2", "no3", "po4", "algae")) {
                val v = w.get(k) ?: continue
                assertTrue("$tag: $k = $v", v.isFinite())
            }
            assertTrue("$tag DO ${w.doMgL}", w.doMgL in 0.0..20.0)
            assertTrue("$tag clarity ${sim.water.clarity}", sim.water.clarity.isFinite() && sim.water.clarity > 0)
            for (o in sim.organisms) assertTrue("$tag ${o.id} pos ${o.pos} health ${o.health}", o.pos.x.isFinite() && o.pos.y.isFinite() && o.pos.z.isFinite() && o.health.isFinite() && o.stress.isFinite())
            for (c in sim.corals) assertTrue("$tag ${c.id}", c.extension.isFinite() && c.tissue.isFinite() && c.bleach.isFinite() && c.flowSpeed.isFinite())
            for (x in 5..115 step 10) for (z in 5..45 step 10) assertTrue("$tag sand", sim.sand.surfaceY(x.toDouble(), z.toDouble()).isFinite())
        }
        for ((turn, par) in listOf(100.0 to 1500.0, 0.0 to 0.0, 100.0 to 0.0, 0.0 to 1500.0)) {
            sim.enqueue(Command.SetParam("turnover", turn)); sim.enqueue(Command.SetParam("par", par))
            sim.advanceAbstract(2 * 86400.0)
            check("abstract $turn×/$par")
        }
        // the live path at full blast: fish swim in it, the analytic jets and then the solver drive everything
        sim.enqueue(Command.SetParam("turnover", 100.0)); sim.enqueue(Command.SetParam("par", 1500.0))
        repeat(600) { sim.stepWall(0.05) }
        check("live 100×")
        sim.enableFluid(FluidQuality.LOW)
        repeat(400) { sim.stepWall(0.05) }
        check("fluid 100×")
        val snap = sim.latest
        assertTrue("NaN pose", snap.organisms.all { o -> o.spine.all { it.isFinite() } })
        assertTrue("surface", snap.surfaceHeights.all { it.isFinite() && kotlin.math.abs(it) < 10f })
        val shelter = sim.organisms.count { it.alive && it.isFish && it.brain.current is com.reeflive.core.ai.FlowShelter }
        println("live 100×: $shelter fish hiding from the current")
        assertTrue("fish shelter from the current in the live view", shelter >= 1)
        sim.enqueue(Command.SetParam("turnover", 0.0))
        repeat(200) { sim.stepWall(0.05) }
        check("fluid 0×")
    }

    /** The flow solver at the highest pump strength stays finite and bounded. */
    @Test
    fun fluidSolverStableAtMaxStrength() {
        val f = FluidSim(quality = FluidQuality.LOW)
        f.strength = Simulation.MAX_FLOW_STRENGTH
        repeat(600) { f.step(0.1) }
        var vmax = 0.0
        for (x in 5..115 step 10) for (y in 8..40 step 8) for (z in 5..45 step 10) {
            val v = f.velocity(Vec3(x.toDouble(), y.toDouble(), z.toDouble()))
            assertTrue("NaN at $x,$y,$z", v.x.isFinite() && v.y.isFinite() && v.z.isFinite())
            vmax = maxOf(vmax, v.length())
        }
        println("max solver speed at strength ${Simulation.MAX_FLOW_STRENGTH}: ${f(vmax)} cm/s")
        assertTrue("bounded: $vmax", vmax < 400.0)
    }

    // ================================================================== skipping water changes

    /** With the routine water changes skipped, nitrate and phosphate rise week on week, above the tank that keeps them. */
    @Test
    fun skippingWaterChangesLetsNutrientsBuildUp() {
        val kept = stocked(11L)
        val skipped = stocked(11L)
        skipped.enqueue(Command.SetParam("skip_water_changes", 1.0))
        skipped.advanceAbstract(60.0); kept.advanceAbstract(60.0)
        assertEquals(1.0, skipped.paramValue("skip_water_changes")!!, 1e-9)
        assertTrue(skipped.latest.skipWaterChanges)
        val no3 = ArrayList<Double>(); val po4 = ArrayList<Double>()
        no3 += skipped.water.s.no3; po4 += skipped.water.s.po4
        repeat(6) {
            skipped.advanceAbstract(7 * 86400.0); kept.advanceAbstract(7 * 86400.0)
            no3 += skipped.water.s.no3; po4 += skipped.water.s.po4
        }
        println("skipped NO3 ${no3.map { f(it) }} vs kept ${f(kept.water.s.no3)}; PO4 ${po4.map { f(it) }} vs kept ${f(kept.water.s.po4)}")
        assertTrue("NO3 rises over the weeks: $no3", no3.last() > no3.first() + 2.0 && no3[6] > no3[3] && no3[3] > no3[0])
        assertTrue("PO4 rises over the weeks: $po4", po4.last() > po4.first() && po4[6] > po4[3])
        assertTrue("above the kept tank: NO3 ${no3.last()} vs ${kept.water.s.no3}", no3.last() > kept.water.s.no3 * 1.2)
        assertTrue("above the kept tank: PO4 ${po4.last()} vs ${kept.water.s.po4}", po4.last() > kept.water.s.po4)
        // back on: the weekly change comes back
        skipped.enqueue(Command.SetParam("skip_water_changes", 0.0))
        skipped.advanceAbstract(60.0)
        assertTrue(!skipped.skipWaterChanges)
    }
}
