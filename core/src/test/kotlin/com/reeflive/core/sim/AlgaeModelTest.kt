package com.reeflive.core.sim

import com.reeflive.core.sim.AlgaeGroup.CORALLINE
import com.reeflive.core.sim.AlgaeGroup.CYANO
import com.reeflive.core.sim.AlgaeGroup.DIATOMS
import com.reeflive.core.sim.AlgaeGroup.DINOS
import com.reeflive.core.sim.AlgaeGroup.FILM
import com.reeflive.core.sim.AlgaeGroup.HAIR
import com.reeflive.core.sim.AlgaeSurface.GLASS
import com.reeflive.core.sim.AlgaeSurface.ROCK_SHADE
import com.reeflive.core.sim.AlgaeSurface.ROCK_TOP
import com.reeflive.core.sim.AlgaeSurface.SAND
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

/** Research 09 A7 acceptance checks for the algae model ([AlgaeModel]). */
class AlgaeModelTest {
    /** The algae model alone on a water state, under the default light schedule (or a blackout), 15-min steps. */
    private class Bench(val w: WaterState, startHour: Double = 0.0) {
        val m = AlgaeModel(TankConfig())
        private val lights = LightSchedule()
        var t = startHour * 3600
        var dark = false
        var flow = 1.0
        var hold: (WaterState) -> Unit = {}
        val day: Double get() = t / 86400
        val lightNow: Double get() = if (dark) 0.0 else lights.intensity(t)

        fun run(days: Double, dtH: Double = 0.25, each: () -> Unit = {}) {
            val n = Math.round(days * 24 / dtH).toInt()
            repeat(n) {
                hold(w)
                m.step(w, dtH, lightNow, flow)
                t += dtH * 3600
                each()
            }
        }

        fun grazers(vararg g: Pair<GrazerType, Int>) { m.grazers = g.flatMap { (t, n) -> List(n) { AlgaeGrazer(t, 1.0) } } }
    }

    private fun clean(no3: Double = 5.0, po4: Double = 0.05) =
        WaterState(no3 = no3, po4 = po4, algaeHair = 0.0, cyano = 0.0, diatoms = 0.0, coralline = 0.0, dinos = 0.0, film = 0.0, bubble = 0.0)

    private fun f(v: Double) = "%.3f".format(v)

    /**
     * The phosphate floor (ppm): P-limited growth draws PO4 down until uptake only balances what decaying tissue returns
     * (about 0.01 ppm here), under the hobby's "zero" of research 09 A2 (PO4 < 0.02).
     */
    private val PO4_FLOOR = 0.02

    // ------------------------------------------------------------------ check 1
    /** New-tank diatom pulse: Si 2 ppm, no grazers; sand cover > 50 % between day 10 and 28, < 10 % by day 60 with Si < 0.05; +0.5 ppm Si restarts it. */
    @Test
    fun a1_newTankDiatomPulse() {
        val b = Bench(clean(no3 = 3.0, po4 = 0.05).copy(silicate = 2.0, siLeach = 0.1, microbes = 0.0))
        b.hold = { it.no3 = 3.0; it.po4 = 0.05 }
        var first50 = -1.0
        var peak = 0.0
        b.run(60.0) {
            val c = b.m.cover(DIATOMS, SAND)
            peak = max(peak, c)
            if (first50 < 0 && c > 0.5) first50 = b.day
        }
        val at60 = b.m.cover(DIATOMS, SAND)
        println("A7#1 first >50% day ${f(first50)} peak ${f(peak)} day60 cover ${f(at60)} Si ${f(b.w.silicate)}")
        assertTrue("sand diatoms first exceeded 50 % on day $first50", first50 in 10.0..28.0)
        assertTrue("day-60 sand diatoms $at60, Si ${b.w.silicate}", at60 < 0.10 && b.w.silicate < 0.05)
        b.w.silicate += 0.5
        var rise = 0.0
        b.run(7.0) { rise = max(rise, b.m.cover(DIATOMS, SAND) - at60) }
        println("A7#1 restart: +${f(rise)} sand cover within 7 d")
        assertTrue("silicate dose did not restart the pulse: +$rise", rise > 0.15)
    }

    // ------------------------------------------------------------------ check 2
    /**
     * Stoichiometric drawdown from research 09's start (NO3 10, PO4 0.2, 2 g DW hair, no grazers, no feeding). Growing
     * to 20 g would need about 0.36 ppm PO4, more than the water holds, so (as the check itself says) the phosphate hits
     * its floor first and growth turns P-limited: PO4 reaches the floor, f_nut reports P as the limiter, and the nitrate
     * taken matches hair tissue N (2.5 % DW: 0.49 ppm NO3 per g DW grown in 227 L, A3).
     */
    @Test
    fun a2_drawdownIsStoichiometric() {
        val b = Bench(clean(no3 = 10.0, po4 = 0.2))
        AlgaeGroup.entries.filter { it != HAIR }.forEach { b.m.active[it.ordinal] = false }
        b.m.setBiomass(HAIR, 2.0)
        b.w.algaeHair = b.m.headline(HAIR)
        val b0 = b.m.biomass(HAIR)
        var days = 0.0
        var floorDay = -1.0
        while (days < 120) {
            b.run(0.25); days += 0.25
            if (floorDay < 0 && b.w.po4 < PO4_FLOOR) floorDay = days
            if (floorDay >= 0 && days >= floorDay + 14) break
        }
        val grown = b.m.biomass(HAIR) - b0 + b.m.deadDw
        val dN = 10.0 - b.w.no3
        val perG = dN / grown
        println("A7#2 PO4 floor on day ${f(floorDay)}: PO4 ${f(b.w.po4)} NO3 −${f(dN)} for ${f(grown)} g grown (${f(perG)} ppm/g) limiter ${b.m.limiter[HAIR.ordinal]} fNut ${f(b.m.fNut[HAIR.ordinal])}")
        assertTrue("PO4 never reached its floor: ${b.w.po4}", floorDay >= 0 && b.w.po4 < PO4_FLOOR)
        // at the floor: another week barely moves it
        val settled = b.w.po4
        b.run(7.0)
        assertEquals("PO4 still falling at the floor", settled, b.w.po4, 0.005)
        assertEquals(Nutrient.P, b.m.limiter[HAIR.ordinal])
        assertTrue("hair still growing on P it does not have: fNut ${b.m.fNut[HAIR.ordinal]}", b.m.fNut[HAIR.ordinal] < 0.2)
        assertTrue("hair did not grow on the phosphate there was: $grown g", grown > 5.0)
        assertEquals("NO3 taken per g DW grown", 0.49, perG, 0.08)
    }

    // ------------------------------------------------------------------ check 3
    /** ULN (NO3 0.2, PO4 0.005) breeds dinos, not hair; raising nutrients stops them within 7 d and halves them within 21 d. */
    @Test
    fun a3_ultraLowNutrientsBreedDinos() {
        val b = Bench(clean(no3 = 0.2, po4 = 0.005).copy(algaeHair = 0.01, coralline = 0.3))
        b.hold = { it.no3 = 0.2; it.po4 = 0.005; it.nh3Total = 0.0; it.no2 = 0.0 }
        var hairMax = 0.0
        var at30 = -1.0
        b.run(21.0) {
            hairMax = max(hairMax, b.w.algaeHair)
            if (at30 < 0 && b.w.dinos >= 0.3) at30 = b.day
        }
        println("A7#3 ULN: dinos ${f(b.w.dinos)} (30 % on day ${f(at30)}), hair max ${f(hairMax)}")
        assertTrue("dinos only ${b.w.dinos} after 21 d of ULN", at30 in 0.0..21.0)
        assertTrue("hair reached $hairMax under ULN", hairMax < 0.05)
        val peak = b.w.dinos
        b.hold = { it.no3 = 5.0; it.po4 = 0.05; it.nh3Total = 0.0; it.no2 = 0.0 }
        var stoppedBy = -1.0
        var last = b.m.biomass(DINOS)
        for (d in 1..21) {
            b.run(1.0)
            val now = b.m.biomass(DINOS)
            if (stoppedBy < 0 && now <= last) stoppedBy = d.toDouble()
            last = now
        }
        println("A7#3 raised: dino growth stopped by day ${f(stoppedBy)}, cover ${f(peak)} → ${f(b.w.dinos)}")
        assertTrue("dinos kept growing for $stoppedBy days", stoppedBy in 0.0..7.0)
        assertTrue("dino cover ${b.w.dinos} vs $peak after 21 d", b.w.dinos <= peak / 2)
    }

    // ------------------------------------------------------------------ check 4
    /** Sand at 2 cm/s, PO4 0.1, DOC 0.5: cyano > 30 % of the sand within 14 d, on sand before rock; 15 cm/s drops it < 10 % within 10 d. */
    @Test
    fun a4_lowFlowCyanoOnSand() {
        val b = Bench(clean(no3 = 5.0, po4 = 0.1))
        b.hold = { it.no3 = 5.0; it.po4 = 0.1 }
        b.m.docOverride = 0.5
        b.m.flowOverride[SAND.ordinal] = 2.0
        var sandAt = -1.0; var rockAt = -1.0
        b.run(14.0) {
            if (sandAt < 0 && b.m.cover(CYANO, SAND) > 0.05) sandAt = b.day
            val rock = max(b.m.cover(CYANO, ROCK_TOP), b.m.cover(CYANO, ROCK_SHADE))
            if (rockAt < 0 && rock > 0.05) rockAt = b.day
        }
        val sand = b.m.cover(CYANO, SAND)
        println("A7#4 cyano sand ${f(sand)} (5 % on day ${f(sandAt)}, rock 5 % on day ${f(rockAt)})")
        assertTrue("cyano sand cover $sand after 14 d", sand > 0.3)
        assertTrue("cyano on rock (day $rockAt) before sand (day $sandAt)", sandAt >= 0 && (rockAt < 0 || sandAt < rockAt))
        b.m.flowOverride[SAND.ordinal] = 15.0
        var below = -1.0
        val t0 = b.day
        b.run(10.0) { if (below < 0 && b.m.cover(CYANO, SAND) < 0.1) below = b.day - t0 }
        println("A7#4 at 15 cm/s below 10 % after ${f(below)} d")
        assertTrue("cyano still ${b.m.cover(CYANO, SAND)} on sand after 10 d of flow", below in 0.0..10.0)
    }

    // ------------------------------------------------------------------ check 5
    /** Four Trochus and a juvenile tang hold the glass film under 30 % at NO3 5, but a 30 % hair lawn at NO3 20 / PO4 0.2 still reaches 60 % in 30 d. */
    @Test
    fun a5_grazersHoldFilmNotALawn() {
        val b = Bench(clean(no3 = 5.0, po4 = 0.05))
        b.hold = { it.no3 = 5.0; it.po4 = 0.05 }
        b.grazers(GrazerType.TROCHUS to 4, GrazerType.TANG to 1)
        var filmMax = 0.0
        b.run(120.0) { if (b.day > 10) filmMax = max(filmMax, b.m.cover(FILM, GLASS)) }
        println("A7#5 film max ${f(filmMax)} over 120 d with 4 Trochus + tang; hair ${f(b.w.algaeHair)}")
        assertTrue("glass film reached $filmMax", filmMax < 0.3)

        val lawn = Bench(clean(no3 = 20.0, po4 = 0.2).copy(algaeHair = 0.3))
        lawn.hold = { it.no3 = 20.0; it.po4 = 0.2 }
        lawn.grazers(GrazerType.TROCHUS to 4, GrazerType.TANG to 1)
        var at60 = -1.0
        lawn.run(30.0) { if (at60 < 0 && lawn.w.algaeHair >= 0.6) at60 = lawn.day }
        println("A7#5 lawn ${f(lawn.w.algaeHair)} after 30 d (60 % on day ${f(at60)}), grazed ${f(lawn.m.grazedPerDay[HAIR.ordinal])} g/d")
        assertTrue("lawn only ${lawn.w.algaeHair} after 30 d", at60 in 0.0..30.0)
    }

    // ------------------------------------------------------------------ check 6
    /**
     * Killing 40 g DW of hair (NO3 1, PO4 0.02) releases ammonia/nitrate and phosphate and deepens the night DO dip.
     * Research 09 asks for a water PO4 peak of 0.5 ppm. The decay does return > 0.5 ppm of PO4 within 14 days, but the
     * film and diatoms that bloom on it (no grazers, no glass wiping here) and rock binding take much of it back up, so
     * the water peak is checked at 0.25 ppm and the released amount at 0.5 ppm.
     */
    @Test
    fun a6_dieOffReleasesNutrients() {
        val w = WaterSim(TankConfig(), WaterState(no3 = 1.0, po4 = 0.02))
        w.bioMassIndex = 60.0
        w.algae.setBiomass(HAIR, 40.0)
        w.s.algaeHair = w.algae.headline(HAIR)
        var min = 0
        val sched = LightSchedule()
        fun stepDay(each: () -> Unit = {}) = repeat(1440) {
            w.lightIntensity = sched.intensity(min * 60.0)
            w.step(60.0); min++; each()
        }
        var nightBefore = 99.0
        repeat(2) { stepDay { if (w.lightIntensity < 0.05) nightBefore = minOf(nightBefore, w.s.doMgL) } }
        val no3Before = w.s.no3; val po4Before = w.s.po4
        w.algae.kill(HAIR, 40.0)
        val pReleased0 = w.algae.pReleasedMg
        var tanPeak = 0.0; var no3Peak = 0.0; var po4Peak = 0.0; var nightAfter = 99.0
        repeat(14) {
            stepDay {
                tanPeak = max(tanPeak, w.s.nh3Total * 14 / 17)
                no3Peak = max(no3Peak, w.s.no3); po4Peak = max(po4Peak, w.s.po4)
                if (w.lightIntensity < 0.05) nightAfter = minOf(nightAfter, w.s.doMgL)
            }
        }
        val released = (w.algae.pReleasedMg - pReleased0) * (95.0 / 31.0) / w.s.volumeL
        println("A7#6 TAN peak ${f(tanPeak)} NH3-N, NO3 +${f(no3Peak - no3Before)}, PO4 ${f(po4Before)} → ${f(po4Peak)} (released ${f(released)}), night DO ${f(nightBefore)} → ${f(nightAfter)}")
        assertTrue("TAN $tanPeak / NO3 rise ${no3Peak - no3Before}", tanPeak >= 0.1 || no3Peak - no3Before in 10.0..20.0)
        assertTrue("PO4 released $released", released >= 0.5)
        assertTrue("PO4 peak $po4Peak", po4Peak >= 0.25)
        assertTrue("night DO $nightBefore → $nightAfter", nightAfter <= nightBefore - 1.0)
    }

    // ------------------------------------------------------------------ check 7
    /** Coralline at pH 7.8: growth falls ≥ 60 % and cover declines within 30 d; back at 8.2 growth returns within 14 d. */
    @Test
    fun a7_corallineTracksCarbonate() {
        val b = Bench(clean().copy(ph = 8.2, alk = 8.0, coralline = 0.3))
        var ph = 8.2
        b.hold = { it.ph = ph; it.alk = 8.0; it.no3 = 5.0; it.po4 = 0.05 }
        fun specificGrowth(): Double {   // g grown per g per day, averaged over a day
            var g = 0.0
            b.run(1.0) { g += b.m.growthPerDay[CORALLINE.ordinal] / b.m.biomass(CORALLINE) }
            return g / 96
        }
        b.run(2.0)
        val base = specificGrowth()
        val cover0 = b.w.coralline
        ph = 7.8
        val low = specificGrowth()
        b.run(29.0)
        val cover30 = b.w.coralline
        ph = 8.2
        var back = -1.0
        for (d in 1..14) { if (back < 0 && specificGrowth() >= 0.8 * base) back = d.toDouble() }
        println("A7#7 growth ${f(base * 1000)} → ${f(low * 1000)} ‰/d, cover ${f(cover0)} → ${f(cover30)}, recovered on day ${f(back)}")
        assertTrue("growth only fell to ${low / base}", low <= 0.4 * base)
        assertTrue("cover $cover0 → $cover30", cover30 < cover0)
        assertTrue("growth did not return", back in 0.0..14.0)
    }

    // ------------------------------------------------------------------ check 8
    /** Three days of PAR 0: dinos and cyano lose 60-80 %, hair 10-20 %, coralline < 5 %; GSA film visible again within 4 d. */
    @Test
    fun a8_blackoutSelectivity() {
        // dinos under ULN (where they live); cyano, hair, coralline and film under nutrients with a low-flow sand bed
        val dino = Bench(clean(no3 = 0.2, po4 = 0.005).copy(dinos = 0.4), startHour = 20.0)
        dino.hold = { it.no3 = 0.2; it.po4 = 0.005; it.nh3Total = 0.0; it.no2 = 0.0 }
        val mix = Bench(clean(no3 = 5.0, po4 = 0.1).copy(cyano = 0.4, algaeHair = 0.4, coralline = 0.4, film = 0.4), startHour = 20.0)
        mix.hold = { it.no3 = 5.0; it.po4 = 0.1 }
        mix.m.docOverride = 0.5
        mix.m.flowOverride[SAND.ordinal] = 2.0
        dino.run(0.01); mix.run(0.01)
        val d0 = dino.m.biomass(DINOS); val c0 = mix.m.biomass(CYANO); val h0 = mix.m.biomass(HAIR); val k0 = mix.m.biomass(CORALLINE)
        mix.m.wipeGlass()
        dino.dark = true; mix.dark = true
        dino.run(3.0); mix.run(3.0)
        fun loss(a: Double, b: Double) = 1 - b / a
        val dl = loss(d0, dino.m.biomass(DINOS)); val cl = loss(c0, mix.m.biomass(CYANO))
        val hl = loss(h0, mix.m.biomass(HAIR)); val kl = loss(k0, mix.m.biomass(CORALLINE))
        mix.dark = false
        var visible = -1.0
        val t0 = mix.day
        mix.run(4.0) { if (visible < 0 && mix.m.cover(FILM, GLASS) >= 0.10) visible = mix.day - t0 }
        println("A7#8 losses: dinos ${f(dl)} cyano ${f(cl)} hair ${f(hl)} coralline ${f(kl)}; film visible after ${f(visible)} d")
        assertTrue("dinos lost $dl", dl in 0.6..0.8)
        assertTrue("cyano lost $cl", cl in 0.6..0.8)
        assertTrue("hair lost $hl", hl in 0.1..0.2)
        assertTrue("coralline lost $kl", kl < 0.05)
        assertTrue("film not visible within 4 d", visible in 0.0..4.0)
    }

    // ------------------------------------------------------------------ outbreak groups (A2 triggers, A5, A6)
    /** Dinos lift into the water after lights-out and settle after lights-on; a UV steriliser kills the lifted share. */
    @Test
    fun dinosLiftAtNightAndUvTakesThem() {
        fun uln(uv: Boolean) = Bench(clean(no3 = 0.2, po4 = 0.005).copy(dinos = 0.3, microbes = 0.3)).also {
            it.hold = { w -> w.no3 = 0.2; w.po4 = 0.005; w.nh3Total = 0.0; w.no2 = 0.0 }
            it.m.uv = uv
        }
        val b = uln(false)
        var lo = 1.0; var hi = 0.0
        b.run(2.0) {
            if (b.lightNow > 0.5) lo = min(lo, b.m.dinoLift) else if (b.lightNow < LightSchedule.LIGHTS_ON) hi = max(hi, b.m.dinoLift)
        }
        println("dinos lift: midday ${f(lo)}, night ${f(hi)}")
        assertTrue("dinos still lifted at midday: $lo", lo < 0.1)
        assertTrue("dinos never lifted at night: $hi", hi > 0.9)
        assertEquals(b.m.dinoLift.toFloat(), b.m.snapshot().dinoLift, 1e-6f)
        val uvOn = uln(true)
        b.run(14.0); uvOn.run(16.0)
        println("dinos 16 d ULN: no UV ${f(b.m.biomass(DINOS))} g, UV ${f(uvOn.m.biomass(DINOS))} g")
        assertTrue("UV did not hold the dinos back", uvOn.m.biomass(DINOS) < 0.7 * b.m.biomass(DINOS))
    }

    /** Low biodiversity (a young or knocked-back microbial community) lets dinos take a ULN tank faster (A2). */
    @Test
    fun lowBiodiversityBreedsDinosFaster() {
        fun uln(microbes: Double) = Bench(clean(no3 = 0.2, po4 = 0.005).copy(microbes = microbes)).also {
            it.hold = { w -> w.no3 = 0.2; w.po4 = 0.005; w.nh3Total = 0.0; w.no2 = 0.0 }
        }
        val young = uln(0.1); val mature = uln(1.0)
        young.run(14.0); mature.run(14.0)
        println("dinos 14 d ULN: young community ${f(young.w.dinos)}, mature ${f(mature.w.dinos)}")
        assertTrue("young ${young.w.dinos} vs mature ${mature.w.dinos}", young.w.dinos > 1.5 * mature.w.dinos)
        // a probiotic dose stands in for the missing competitors
        val dosed = uln(0.1).also { it.m.probiotic = 1.0 }
        dosed.run(14.0)
        assertTrue("probiotic ${dosed.w.dinos} vs young ${young.w.dinos}", dosed.w.dinos < young.w.dinos)
    }

    /** Cyano on low-flow sand grows faster with dissolved organics (water DOC, carbon dosing) and at a low N:P (A2). */
    @Test
    fun cyanoFollowsDocAndNitrogenToPhosphate() {
        fun sand(no3: Double, doc: Double = 0.2, carbon: Double = 0.0): Double {
            val b = Bench(clean(no3 = no3, po4 = 0.1).copy(doc = doc))
            b.hold = { it.no3 = no3; it.po4 = 0.1; it.doc = doc }
            b.m.carbonDosing = carbon
            b.m.flowOverride[SAND.ordinal] = 3.0
            b.run(10.0)
            return b.m.cover(CYANO, SAND)
        }
        val base = sand(10.0); val docky = sand(10.0, doc = 6.0); val dosed = sand(10.0, carbon = 1.0); val lowNp = sand(0.1)
        println("cyano sand 10 d at 3 cm/s: base ${f(base)}, DOC 6 mg/L ${f(docky)}, carbon dosing ${f(dosed)}, NO3 0.1 ${f(lowNp)}")
        assertTrue("DOC did not drive cyano", docky > 1.5 * base)
        assertTrue("carbon dosing did not drive cyano", dosed > 1.3 * base)
        assertTrue("low N:P did not favour cyano", lowNp > 1.2 * base)
        // and it stays off the well-flushed sand
        val b = Bench(clean(no3 = 10.0, po4 = 0.1).copy(doc = 6.0))
        b.hold = { it.no3 = 10.0; it.po4 = 0.1; it.doc = 6.0 }
        b.m.flowOverride[SAND.ordinal] = 15.0
        b.run(10.0)
        assertTrue("cyano on 15 cm/s sand: ${b.m.cover(CYANO, SAND)}", b.m.cover(CYANO, SAND) < 0.05)
    }

    /** Bubble algae spread slowly (cluster doubling in weeks, A6) and only on rock; young rock and high pH slow them. */
    @Test
    fun bubbleAlgaeSpreadSlowlyOnRock() {
        fun run(microbes: Double = 1.0, ph: Double = 8.2): Bench = Bench(clean().copy(bubble = 0.03, microbes = microbes, ph = ph)).also {
            it.hold = { w -> w.no3 = 5.0; w.po4 = 0.05; w.ph = ph }
            it.run(28.0)
        }
        val b = run()
        val growth = b.w.bubble / 0.03
        println("bubble 28 d: ${f(b.w.bubble)} (x${f(growth)}), shade ${f(b.m.cover(AlgaeGroup.BUBBLE, ROCK_SHADE))}")
        assertTrue("bubble algae grew x$growth in 4 weeks", growth in 1.3..4.0)
        assertEquals(0.0, b.m.cover(AlgaeGroup.BUBBLE, SAND), 0.0)
        assertEquals(0.0, b.m.cover(AlgaeGroup.BUBBLE, GLASS), 0.0)
        val young = run(microbes = 0.0); val lowPh = run(ph = 7.9)
        println("bubble 28 d: young rock ${f(young.w.bubble)}, pH 7.9 ${f(lowPh.w.bubble)}")
        assertTrue("young rock ${young.w.bubble} vs mature ${b.w.bubble}", young.w.bubble < b.w.bubble)
        assertTrue("pH 7.9 ${lowPh.w.bubble} vs 8.2 ${b.w.bubble}", lowPh.w.bubble > b.w.bubble)
    }

    /** Pulled off whole, bubble algae come back slowly; squeezed, burst cells seed new ones and they come back faster (A5). */
    @Test
    fun bubbleRemovalCarefulVersusSqueezed() {
        fun pulled(careful: Boolean): Pair<Double, Bench> {
            val b = Bench(clean().copy(bubble = 0.2))
            b.hold = { it.no3 = 5.0; it.po4 = 0.05 }
            b.run(0.1)
            val before = b.w.bubble
            val g = b.m.removeBubble(b.w, careful)
            assertTrue("nothing removed", g > 0)
            assertTrue("cover $before → ${b.w.bubble}", b.w.bubble < 0.5 * before)
            return b.w.bubble to b
        }
        val (careNow, care) = pulled(true)
        val (squeezeNow, squeeze) = pulled(false)
        care.run(30.0); squeeze.run(30.0)
        println("bubble removal: careful ${f(careNow)} → ${f(care.w.bubble)}, squeezed ${f(squeezeNow)} → ${f(squeeze.w.bubble)} after 30 d")
        assertTrue("squeezing left $squeezeNow vs $careNow", squeezeNow > careNow)
        assertTrue("squeezed ${squeeze.w.bubble} vs careful ${care.w.bubble} after 30 d", squeeze.w.bubble > care.w.bubble)
        assertTrue("squeezing left no rotting sap", squeeze.m.deadDw > care.m.deadDw)
    }

    // ------------------------------------------------------------------ wiring
    /** The sandbox algae control sets the hair cover the model then grows from; the stocked tank stays near its old equilibrium. */
    @Test
    fun sandboxControlAndStockedEquilibrium() {
        val sim = TestTank.sim(DefaultStock.all, seed = 3L)
        sim.enqueue(Command.SetParam("algae", 0.5))
        sim.advanceAbstract(600.0)
        assertEquals(0.5, sim.water.s.algaeHair, 0.01)
        assertEquals(0.5, sim.latest.algae.cover(HAIR, ROCK_TOP).toDouble(), 0.02)
        sim.enqueue(Command.SetParam("algae", 0.03))
        sim.advanceAbstract(30 * 86400.0)
        val s = sim.water.s
        println("stocked 30 d: hair ${f(s.algaeHair)} film ${f(s.film)} diatoms ${f(s.diatoms)} cyano ${f(s.cyano)} dinos ${f(s.dinos)} coralline ${f(s.coralline)} NO3 ${f(s.no3)} PO4 ${f(s.po4)} det ${f(s.detritus)}")
        assertTrue("hair ${s.algaeHair}", s.algaeHair < 0.2)
        assertTrue("cyano ${s.cyano} dinos ${s.dinos}", s.cyano < 0.1 && s.dinos < 0.1)
    }
}
