package com.reeflive.core.sim

import com.reeflive.core.sim.MicroGroup.CALANOID
import com.reeflive.core.sim.MicroGroup.CILIATE
import com.reeflive.core.sim.MicroGroup.CYCLOPOID
import com.reeflive.core.sim.MicroGroup.FLATWORM
import com.reeflive.core.sim.MicroGroup.GAMMARID
import com.reeflive.core.sim.MicroGroup.HARPACTICOID
import com.reeflive.core.sim.MicroGroup.MYSID
import com.reeflive.core.sim.MicroGroup.ROTIFER
import com.reeflive.core.sim.timelapse.TimelapseFork
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

/** Research 10 §6.3 acceptance checks for the microfauna model ([MicrofaunaModel]) and the microscope counts ([MicroView]). */
class MicrofaunaModelTest {
    /** The microfauna model alone on a water state under the default light schedule, 15-min steps. */
    private class Bench(val w: WaterState = WaterState(tempC = 26.0), startHour: Double = 0.0) {
        // The §6 check numbers (a 2,000 pod/day predator, 2 L of chaeto) were calibrated on the earlier 48 x 13 in
        // footprint; the measured 48 x 18 in tank has 38 % more floor/glass and so ~38 % more pods for the same
        // predator to eat. Keep the bench on the calibration footprint until the checks are re-derived.
        val m = MicrofaunaModel(TankConfig(widthCm = 33.0))
        private val lights = LightSchedule()
        var t = startHour * 3600
        var hold: (WaterState) -> Unit = {}
        val day: Double get() = t / 86400
        private val hourOfDay: Double get() = (t / 3600) % 24

        fun run(days: Double, dtH: Double = 0.25, each: () -> Unit = {}) {
            val n = Math.round(days * 24 / dtH).toInt()
            repeat(n) {
                hold(w)
                m.step(w, dtH, lights.intensity(t), 1.0)
                t += dtH * 3600
                each()
            }
        }

        /** Run on to the next [hour] of the day (13:00 midday, 20:45 = lights-out + 1 h). */
        fun runTo(hour: Double) {
            var d = hour - hourOfDay
            if (d <= 1e-9) d += 24
            run(d / 24)
        }

        fun state() = m.snapshot()
    }

    /** Tee's stock list as pod predators (DefaultStock), and his corals as polyps. */
    private fun tee(b: Bench) {
        val l = ArrayList<MicroPredator>()
        fun add(p: PodPredator, n: Int) = repeat(n) { l += MicroPredator(p, 1.0) }
        add(PodPredator.CLOWN, 2); add(PodPredator.TANG, 1); add(PodPredator.CARDINAL, 1); add(PodPredator.CLEANER_WRASSE, 1)
        add(PodPredator.SAND_GOBY, 1); add(PodPredator.HAWKFISH, 1); add(PodPredator.BLENNY, 2); add(PodPredator.DOTTYBACK, 1)
        add(PodPredator.BANDED_SHRIMP, 1); add(PodPredator.CLEANER_SHRIMP, 1); add(PodPredator.HERMIT, 3); add(PodPredator.BRITTLE_STAR, 1)
        add(PodPredator.CRAB, 1)
        b.m.predators = l
        b.m.smallPolyps = 1250.0; b.m.zoaPolyps = 50.0; b.m.mushrooms = 5.0; b.m.displayMacroL = 0.5
    }

    /** A mature tank: Tee's stock, 4 g DW m-2 of film, biofilm and detritus, 60 days to settle. */
    private fun mature(): Bench = Bench().also { tee(it); it.m.foodOverride = 4.0; it.run(60.0) }

    /** A new tank on dry rock seeded with live rock (2 % of K), 3 g DW m-2 diatom film, 26 °C, no predators. */
    private fun liveRockStart(film: Double = 3.0): Bench = Bench().also {
        it.m.foodOverride = film
        it.m.dryRock()
        it.m.apply(MicroAction.LIVE_ROCK)
    }

    private fun f(v: Double) = "%.3g".format(v)
    private fun harpFracK(b: Bench) = b.m.total(HARPACTICOID) / b.m.capacity(HARPACTICOID)

    // ------------------------------------------------------------------ check 1 (and 11)
    /**
     * Heavy feeder crash: a mature tank (~60-75 % of harpacticoid K) gets a mandarin-class predator (2,000 pods a day).
     * Exposed glass density at night falls below 0.2 per cm² within 10-14 days, total harpacticoids below 20 % of K by
     * day 21 but never below 3 % (rock pores); after the predator leaves they are back at 60 % of K in 21-35 days.
     * Check 11: the 10x glass view at night shows 5-10 harpacticoids before and 0-2 after 14 days of the predator.
     */
    @Test fun c01_heavyFeederCrashAndViewCounts() {
        val b = mature()
        val start = harpFracK(b)
        b.runTo(20.75)
        val before = MicroView.expected(b.state(), HARPACTICOID, MicroScene.GLASS, MicroZoom.X10)
        println("#1 mature at ${f(start)} of K; 10x glass at night ${f(before)} per field")
        assertTrue("mature harpacticoids at ${f(start)} of K", start in 0.55..0.85)
        assertTrue("10x glass view before the predator: $before", before in 5.0..10.0)

        b.m.predators = b.m.predators + MicroPredator(PodPredator.MANDARIN, 1.0)
        val d0 = b.day
        var glassBelow = -1.0
        var minFrac = 1.0
        var at21 = 1.0
        var after14 = 0.0
        while (b.day - d0 < 30) {
            b.runTo(20.75)
            val dd = b.day - d0
            val glass = b.state().visible(HARPACTICOID, MicroScene.GLASS)
            if (glassBelow < 0 && glass < 0.2) glassBelow = dd
            if (dd in 13.5..14.5) after14 = MicroView.expected(b.state(), HARPACTICOID, MicroScene.GLASS, MicroZoom.X10)
            if (dd in 20.5..21.5) at21 = harpFracK(b)
            minFrac = min(minFrac, harpFracK(b))
        }
        println("#1 glass < 0.2/cm² on day ${f(glassBelow)}; day-21 ${f(at21)} of K; min ${f(minFrac)}; 10x glass after 14 d ${f(after14)}")
        assertTrue("glass fell below 0.2 per cm² on day $glassBelow", glassBelow in 9.5..14.5)
        assertTrue("day-21 harpacticoids ${f(at21)} of K", at21 < 0.2)
        assertTrue("harpacticoids fell to ${f(minFrac)} of K", minFrac >= 0.03)
        assertTrue("10x glass view after 14 days: $after14", after14 <= 2.0)

        b.m.predators = b.m.predators.filter { it.type != PodPredator.MANDARIN }
        val r0 = b.day
        var back = -1.0
        val trace = ArrayList<String>()
        b.run(50.0) {
            if (back < 0 && harpFracK(b) >= 0.6) back = b.day - r0
            if (Math.round((b.day - r0) * 96) % (10 * 96) == 0L) trace += f(harpFracK(b))
        }
        println("#1 recovery to 60 % of K in ${f(back)} days (every 10 d: $trace)")
        assertTrue("recovered to 60 % of K in $back days", back in 21.0..35.0)
    }

    // ------------------------------------------------------------------ check 2
    /**
     * Tigger pod boom: 2,000 Parvocalanus (9 per L) plus phyto to 2 × 10^5 cells/mL at lights-out. Phyto below 10^4
     * within 48 h; calanoids peak at 1.5-2× the dose between day 4 and 7 and fall below 1 per L by day 21. Redosing
     * phyto to 5 × 10^4 every 2 days holds them at a few per litre.
     */
    @Test fun c02_tiggerPodBoomWithPhyto() {
        fun dosed(): Bench = Bench(startHour = 19.75).also {
            tee(it); it.m.foodOverride = 3.0
            it.m.apply(MicroAction.DOSE_TIGGER); it.m.apply(MicroAction.DOSE_PHYTO)
        }
        val b = dosed()
        val v = b.w.volumeL
        val dose = b.m.adultsOf(CALANOID) / v
        var phytoBelow = -1.0
        var peak = 0.0; var peakDay = 0.0
        b.run(21.0) {
            if (phytoBelow < 0 && b.m.phytoPerMl < 1e4) phytoBelow = b.day - 19.75 / 24
            val c = b.m.adultsOf(CALANOID) / v
            if (c > peak) { peak = c; peakDay = b.day - 19.75 / 24 }
        }
        val end = b.m.adultsOf(CALANOID) / v
        println("#2 dose ${f(dose)}/L; phyto < 1e4 after ${f(phytoBelow)} d; peak ${f(peak)}/L (${f(peak / dose)}x) on day ${f(peakDay)}; day 21 ${f(end)}/L")
        assertTrue("phyto cleared after $phytoBelow days", phytoBelow in 0.0..2.0)
        assertTrue("peak ${peak / dose}x the dose", peak / dose in 1.5..2.0)
        assertTrue("peak on day $peakDay", peakDay in 4.0..7.0)
        assertTrue("day-21 calanoids $end per L", end < 1.0)

        val r = dosed()
        var sum = 0.0; var n = 0
        var lo = Double.MAX_VALUE; var hi = 0.0
        var nextDose = 2.0
        r.run(60.0) {
            val d = r.day - 19.75 / 24
            if (d >= nextDose) { nextDose += 2.0; if (r.m.phytoPerMl < 5e4) r.m.setPhyto(5e4) }
            if (d > 30) { val c = r.m.adultsOf(CALANOID) / v; sum += c; n++; lo = min(lo, c); hi = max(hi, c) }
        }
        println("#2 redosed: days 30-60 mean ${f(sum / n)}/L (${f(lo)}-${f(hi)})")
        assertTrue("redosed calanoids mean ${sum / n} per L", sum / n in 2.0..6.0)
    }

    // ------------------------------------------------------------------ check 3
    /**
     * Night emergence: crustaceans visible on an open rock face at lights-out + 1 h are 3-5× the midday count, column
     * copepods 2-4×. The cardinal eats only in the dark and takes at least 40 % of the pods eaten.
     */
    @Test fun c03_nightEmergence() {
        val b = mature()
        val crust = MicroGroup.entries.filter { it.isCrustacean }
        val cope = MicroGroup.entries.filter { it.isCopepod }
        b.runTo(13.0)
        val dayRock = crust.sumOf { b.state().visible(it, MicroScene.ROCK) }
        val dayCol = cope.sumOf { b.state().visible(it, MicroScene.WATER) }
        b.runTo(20.75)
        val nightRock = crust.sumOf { b.state().visible(it, MicroScene.ROCK) }
        val nightCol = cope.sumOf { b.state().visible(it, MicroScene.WATER) }
        val eaten = DoubleArray(PodPredator.entries.size)
        var cardinalByDay = 0.0
        b.run(3.0) {
            for (i in eaten.indices) eaten[i] += b.m.eatenByPredator[i]
            if (!b.m.night) cardinalByDay += b.m.eatenByPredator[PodPredator.CARDINAL.ordinal]
        }
        val share = eaten[PodPredator.CARDINAL.ordinal] / eaten.sum()
        println("#3 rock ${f(nightRock / dayRock)}x, column ${f(nightCol / dayCol)}x, cardinal share ${f(share)}")
        assertTrue("rock emergence ${nightRock / dayRock}x", nightRock / dayRock in 3.0..5.0)
        assertTrue("column emergence ${nightCol / dayCol}x", nightCol / dayCol in 2.0..4.0)
        assertEquals("cardinal fed by day", 0.0, cardinalByDay, 0.0)
        assertTrue("cardinal share $share", share >= 0.4)
    }

    // ------------------------------------------------------------------ check 4
    /** Live-rock colonisation: harpacticoids reach 50 % of K in 28-42 days on a 3 g m-2 film; with no film they stay under 10 %. */
    @Test fun c04_harpacticoidColonisation() {
        val b = liveRockStart()
        var half = -1.0
        b.run(60.0) { if (half < 0 && harpFracK(b) >= 0.5) half = b.day }
        val starved = liveRockStart(film = 0.0)
        var peak = 0.0
        starved.run(60.0) { peak = max(peak, starved.m.total(HARPACTICOID) / starved.m.refCapacity(HARPACTICOID)) }
        println("#4 50 % of K on day ${f(half)}; no film peak ${f(peak)} of K")
        assertTrue("half K on day $half", half in 28.0..42.0)
        assertTrue("no-film peak $peak of K", peak <= 0.1)
    }

    // ------------------------------------------------------------------ check 5
    /** Amphipods are slow: 90-150 days to 50 % of K; a 2 L chaeto refugium raises the 180-day count by at least 50 %. */
    @Test fun c05_amphipodSlowness() {
        val b = liveRockStart()
        var half = -1.0
        b.run(180.0) { if (half < 0 && b.m.total(GAMMARID) >= 0.5 * b.m.capacity(GAMMARID)) half = b.day }
        val plain = b.m.total(GAMMARID)
        val c = liveRockStart()
        c.m.refugiumL = 2.0
        c.run(180.0)
        val chaeto = c.m.total(GAMMARID)
        println("#5 gammarids 50 % of K on day ${f(half)}; day 180 ${f(plain)} vs ${f(chaeto)} with 2 L chaeto")
        assertTrue("half K on day $half", half in 90.0..150.0)
        assertTrue("chaeto raised amphipods ${chaeto / plain}x", chaeto >= 1.5 * plain)
    }

    // ------------------------------------------------------------------ check 6
    /**
     * Copper wipe-out: Cu 0.15 ppm for 5 days takes every crustacean, worm and snail group below 1 % by day 3; ciliates
     * and phyto carry on; nothing comes back in 60 days without re-seeding; a re-seed on day 30 grows at half speed.
     */
    @Test fun c06_copperWipeOut() {
        val b = mature()
        val killable = MicroGroup.entries.filter { it.isCrustacean || it == MicroGroup.BRISTLEWORM || it == MicroGroup.MICROSNAIL || it == MicroGroup.SPAGHETTI_WORM }
            .filter { b.m.present[it.ordinal] }
        val pre = killable.associateWith { b.m.total(it) }
        val preCil = b.m.total(CILIATE)
        val t0 = b.day
        b.hold = { it.copper = if (b.day - t0 < 5) 0.15 else 0.0 }
        b.run(3.0)
        val worst = killable.maxOf { b.m.total(it) / pre.getValue(it) }
        println("#6 day-3 survivors: worst ${f(worst)}; ciliates ${f(b.m.total(CILIATE) / preCil)}")
        assertTrue("survivors after 3 days: " + killable.joinToString { "$it ${f(b.m.total(it) / pre.getValue(it))}" }, worst < 0.01)
        assertTrue("ciliates hit by copper", b.m.total(CILIATE) > 0.5 * preCil)
        b.run(57.0)
        assertTrue("came back without a re-seed: " + killable.filter { b.m.total(it) > 0 }, killable.all { b.m.total(it) == 0.0 })

        // re-seed on day 30 of a treated tank vs an untreated one; 30 days of growth with residual copper
        fun reseeded(copper: Boolean): Double {
            val x = Bench().also { it.m.foodOverride = 3.0; it.m.dryRock() }
            val s0 = x.day
            if (copper) x.hold = { it.copper = if (x.day - s0 < 5) 0.15 else 0.0 }
            x.run(30.0)
            x.m.apply(MicroAction.LIVE_ROCK)
            x.run(30.0)
            return x.m.total(HARPACTICOID)
        }
        val treated = reseeded(true)
        val clean = reseeded(false)
        println("#6 re-seed growth: treated ${f(treated)} vs clean ${f(clean)} (${f(treated / clean)})")
        assertTrue("residual copper did not slow the re-seed: ${treated / clean}", treated / clean in 0.05..0.6)
    }

    // ------------------------------------------------------------------ check 7
    /**
     * Heat ordering: 27 → 35 °C over 6 h, held 24 h. Mysids and calanoids lose > 70 %, amphipods 30-60 %, harpacticoids
     * and cyclopoids < 30 %, rotifers and ciliates < 10 % (against an unheated twin). Harpacticoid egg-sac carriers drop
     * to near zero during the hold and recover within 5 days.
     */
    @Test fun c07_heatToleranceOrdering() {
        fun bench() = Bench(WaterState(tempC = 27.0)).also {
            it.m.foodOverride = 3.0; it.run(10.0)
            it.m.setTotal(MYSID, 100.0); it.m.setTotal(CALANOID, 1000.0); it.m.setTotal(ROTIFER, 1e6)
        }
        val heat = bench()
        val ctrl = bench()
        val t0 = heat.day
        heat.hold = { it.tempC = 27.0 + 8.0 * ((heat.day - t0) * 24 / 6).coerceIn(0.0, 1.0) }
        val breedPre = heat.m.breeding[HARPACTICOID.ordinal]
        heat.run(30.0 / 24); ctrl.run(30.0 / 24)
        val breedHold = heat.m.breeding[HARPACTICOID.ordinal]
        fun loss(g: MicroGroup) = 1 - heat.m.total(g) / ctrl.m.total(g)
        val groups = listOf(MYSID, CALANOID, GAMMARID, HARPACTICOID, CYCLOPOID, ROTIFER, CILIATE)
        println("#7 losses " + groups.joinToString { "$it ${f(loss(it))}" } + "; breeding ${f(breedPre)} -> ${f(breedHold)}")
        assertTrue(loss(MYSID) > 0.7 && loss(CALANOID) > 0.7)
        assertTrue("amphipods ${loss(GAMMARID)}", loss(GAMMARID) in 0.3..0.6)
        assertTrue(loss(HARPACTICOID) < 0.3 && loss(CYCLOPOID) < 0.3)
        assertTrue(loss(ROTIFER) < 0.1 && loss(CILIATE) < 0.1)
        assertTrue("egg sacs during the hold $breedHold", breedHold < 0.1)
        heat.hold = { it.tempC = 27.0 }
        var back = -1.0
        val r0 = heat.day
        heat.run(7.0) { if (back < 0 && heat.m.breeding[HARPACTICOID.ordinal] > 0.8 * breedPre) back = heat.day - r0 }
        println("#7 egg sacs back in ${f(back)} days")
        assertTrue("egg sacs back in $back days", back in 0.0..5.0)
    }

    // ------------------------------------------------------------------ check 8
    /** Salinity: a 20 % change with 29 ppt water barely moves the pods; 24 h at 20 ppt kills 30-50 % of harpacticoids and most mysids. */
    @Test fun c08_salinityShock() {
        fun bench() = Bench(WaterState(tempC = 26.0, sg = 1.026)).also { it.m.foodOverride = 3.0; it.run(20.0); it.m.setTotal(MYSID, 100.0) }
        val wc = bench(); val ctrl = bench()
        wc.w.sg = 0.8 * 1.026 + 0.2 * 1.021
        wc.m.waterChange(0.2)
        wc.run(7.0); ctrl.run(7.0)
        val d = wc.m.total(HARPACTICOID) / ctrl.m.total(HARPACTICOID) - 1
        val low = bench(); val ctrl2 = bench()
        val t0 = low.day
        low.hold = { it.sg = if (low.day - t0 < 1) 1.015 else 1.026 }
        low.run(1.0); ctrl2.run(1.0)
        val harp = 1 - low.m.total(HARPACTICOID) / ctrl2.m.total(HARPACTICOID)
        val mys = 1 - low.m.total(MYSID) / ctrl2.m.total(MYSID)
        println("#8 water change ${f(d)}; 20 ppt for 24 h: harpacticoids −${f(harp)}, mysids −${f(mys)}")
        assertTrue("water change moved pods by $d", kotlin.math.abs(d) < 0.05)
        assertTrue("harpacticoid kill $harp", harp in 0.3..0.5)
        assertTrue("mysid kill $mys", mys > 0.7)
    }

    // ------------------------------------------------------------------ check 9
    /**
     * Corals: 500 GSP polyps with resident calanoids 0.5 per L and cyclopoids 1 per L at night catch 5-40 a day (under
     * 5 % of pod mortality). A rotifer dose of 20 per mL (research 10 §5.2's dosed density; 20 per L of copepods cannot
     * reach 500 a day with the §3.4 rates) lifts capture above 500 a day and gives the corals a feeding bonus.
     */
    @Test fun c09_coralCapture() {
        val b = Bench().also { it.m.foodOverride = 3.0; it.run(10.0) }
        b.m.smallPolyps = 500.0
        b.runTo(23.0)
        val v = b.w.volumeL
        b.m.setTotal(CALANOID, 0.5 * v)
        b.m.setTotal(CYCLOPOID, 1.0 * v / 0.6)
        b.run(0.25 / 24)
        val resident = b.m.coralCapturePerDay
        val copepodDeaths = MicroGroup.entries.filter { it.isCopepod }.sumOf { b.m.eatenPerDay[it.ordinal] + b.m.total(it) * MicrofaunaModel.P[it.ordinal].mBase }
        assertFalse(b.state().coralFed)
        b.m.add(ROTIFER, MicroHabitat.COLUMN, 20.0 * 1000 * v)
        var minDose = Double.MAX_VALUE
        b.run(1.0) { minDose = min(minDose, b.m.coralCapturePerDay) }
        println("#9 resident capture ${f(resident)}/day (${f(resident / copepodDeaths)} of copepod losses); dosed min ${f(minDose)}/day")
        assertTrue("resident capture $resident", resident in 5.0..40.0)
        assertTrue("capture share ${resident / copepodDeaths}", resident < 0.05 * copepodDeaths)
        assertTrue("dosed capture $minDose", minDose > 500)
        assertTrue(b.state().coralFed)
    }

    // ------------------------------------------------------------------ check 10
    /**
     * Flatworms: five on a frag in a lit, low-flow 1,500 cm² zone at 26 °C reach 1 per cm² in 35-45 days and saturate
     * (≥ 90 % of 20 per cm²) by day 60-75; a full kill dumps ~3 g wet into the detritus and toxin for 48 h.
     */
    @Test fun c10_flatwormInfestation() {
        val b = Bench().also { it.m.foodOverride = 3.0; it.m.flatwormAreaOverride = 1500.0 }
        b.m.apply(MicroAction.FLATWORM_FRAG)
        var one = -1.0; var sat = -1.0
        b.run(80.0) {
            val d = b.m.density(FLATWORM, MicroHabitat.ROCK)
            if (one < 0 && d >= 1.0) one = b.day
            if (sat < 0 && d >= 18.0) sat = b.day
        }
        b.m.deadWetG = 0.0
        val n = b.m.total(FLATWORM)
        b.m.apply(MicroAction.TREAT_FLATWORMS)
        println("#10 1/cm² on day ${f(one)}, saturated day ${f(sat)}; kill of ${f(n)} dumps ${f(b.m.deadWetG)} g, toxin ${b.m.toxinHours} h")
        assertTrue("1 per cm² on day $one", one in 35.0..45.0)
        assertTrue("saturated on day $sat", sat in 60.0..75.0)
        assertTrue("dead mass ${b.m.deadWetG} g", b.m.deadWetG in 2.0..4.0)
        assertEquals(48.0, b.m.toxinHours, 1e-9)
        assertEquals(0.0, b.m.total(FLATWORM), 0.0)
    }

    // ------------------------------------------------------------------ check 11 (nauplii rule) and the sampler
    /** The 40x view shows nauplii only when the juvenile pool is above 20 % of the adults; sprites are capped at 300. */
    @Test fun c11_viewNaupliiRuleAndCap() {
        val b = mature()
        b.runTo(20.75)
        val s = b.state()
        val ratio = s.juvenileRatio[HARPACTICOID.ordinal]
        val nap = MicroView.nauplii(s, HARPACTICOID, MicroScene.ROCK, MicroZoom.X40)
        println("#11 harpacticoid juvenile ratio ${f(ratio)}, 40x rock nauplii ${f(nap)}")
        if (ratio > MicroView.NAUPLII_MIN_RATIO) assertTrue(nap > 0) else assertEquals(0.0, nap, 0.0)
        val low = MicrofaunaState(s.total, DoubleArray(s.total.size) { 0.1 }, s.breeding, s.exposed, s.trendPerDay, s.present, s.visible,
            s.phytoPerMl, s.night, s.torchPer100Cm2, s.podsBornPerDay, s.podsEatenPerDay, s.coralCapturePerDay, s.coralFed, s.toxin, s.refugiumL)
        assertEquals(0.0, MicroView.nauplii(low, HARPACTICOID, MicroScene.ROCK, MicroZoom.X40), 0.0)
        // 1x rock at night: hundreds expected, capped at 300 sprites
        val field = MicroView.sample(s, MicroScene.ROCK, MicroZoom.X1, 7L)
        val sprites = field.sumOf { it.sprites + it.nauplii }
        println("#11 1x rock: ${field.joinToString { "${it.group} ${it.sprites}/${f(it.expected)}" }}")
        assertTrue(sprites <= MicroView.SPRITE_CAP)
        assertTrue(field.sumOf { it.expected } > MicroView.SPRITE_CAP)
        // same seed, same field
        assertEquals(field.map { it.sprites }, MicroView.sample(s, MicroScene.ROCK, MicroZoom.X1, 7L).map { it.sprites })
        // rotifers and ciliates only resolve under magnification; phyto haze follows a dose
        assertFalse(MicroView.resolvable(CILIATE, MicroZoom.X1))
        assertTrue(MicroView.resolvable(ROTIFER, MicroZoom.X10))
        b.m.apply(MicroAction.DOSE_PHYTO)
        b.run(0.25 / 24)
        assertTrue(MicroView.phytoHaze(b.state(), MicroZoom.X10) > 0.3f)
    }

    // ------------------------------------------------------------------ integration
    /** The full simulation carries the model: snapshot state, sandbox actions, refugium param and the time-lapse fork. */
    @Test fun simulationIntegration() {
        val sim = TestTank.sim(DefaultStock.all, seed = 5L)
        sim.advanceAbstract(2 * 86400.0)
        val s = sim.latest.microfauna
        assertTrue("no pods in the snapshot", s.total(HARPACTICOID) > 1000)
        assertTrue(s.present[CILIATE.ordinal])
        val preds = sim.water.micro.predators.map { it.type }.toSet()
        assertTrue(PodPredator.CARDINAL in preds && PodPredator.CLEANER_WRASSE in preds)
        assertTrue(sim.water.micro.smallPolyps > 500)
        sim.enqueue(Command.Micro(MicroAction.DOSE_TIGGER))
        sim.enqueue(Command.SetParam("refugium", 1.0))
        sim.enqueue(Command.SetParam("chaeto", 2.0))
        sim.advanceAbstract(3600.0)
        assertTrue(sim.latest.microfauna.total(CALANOID) > 1500)
        assertEquals(2.0, sim.water.micro.refugiumL, 0.05)
        val fork = TimelapseFork.of(sim)
        assertEquals(sim.water.micro.total(HARPACTICOID), fork.water.micro.total(HARPACTICOID), 1e-6)
        assertEquals(2.0, fork.water.micro.refugiumL, 0.05)
        fork.advanceAbstract(86400.0)
        assertTrue(fork.latest.microfauna.total(CALANOID) > 100)
    }
}
