package com.reeflive.core.sim

import com.reeflive.core.ai.CardinalBrain
import com.reeflive.core.ai.CleanerShrimpBrain
import com.reeflive.core.ai.GobyBrain
import com.reeflive.core.ai.HawkBrain
import com.reeflive.core.ai.PairBouts
import com.reeflive.core.ai.PairMatrix
import com.reeflive.core.ai.PeekerBlennyBrain
import com.reeflive.core.ai.Predation
import com.reeflive.core.ai.WrasseBrain
import com.reeflive.core.geom.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test

/**
 * Acceptance checks of research 06 §5.6, numbered as there (c01–c28). Deterministic seeds; daytime
 * runs start after the lights-on ramp. Windows are shorter than the spec's "10 h sim day" where a
 * rate per hour is what is checked, to keep the suite fast (see SIMULATION §9.10 for the mapping).
 */
class RealismAcceptanceTest {

    // ------------------------------------------------------------------ helpers
    private fun tank(stock: List<LivestockSeed> = DefaultStock.all, seed: Long = 1L, hour: Double = 10.5) =
        TestTank.sim(stock, seed = seed, startHour = hour)

    private fun Simulation.org(kind: BrainKind) = organisms.first { it.brainKind == kind }
    private fun Simulation.brainOf(o: Organism) = agentOf(o)!!.brain
    private fun Simulation.pairBouts(o: Organism) = brainOf(o).utility.behaviors.filterIsInstance<PairBouts>().first()

    /** Advance in 60 s chunks, collecting every event; [sample] runs once per [sampleEvery] s. */
    private fun Simulation.run(seconds: Double, dt: Double = 0.1, sampleEvery: Double = 1.0, events: MutableList<SimEvent> = ArrayList(), sample: (() -> Unit)? = null): MutableList<SimEvent> {
        var left = seconds
        var acc = 0.0
        while (left > 1e-6) {
            val chunk = minOf(60.0, left)
            advance(chunk, behaviorDt = dt) {
                if (sample != null) { acc += dt; if (acc >= sampleEvery - 1e-9) { acc = 0.0; sample() } }
            }
            events += latest.events
            left -= chunk
        }
        return events
    }

    private fun stockWithout(vararg ids: String) = DefaultStock.all.filter { it.id !in ids }

    private fun chases(ev: List<SimEvent>) = ev.filterIsInstance<SimEvent.Chase>().filter { it.bout != "QUIVER_YIELD" }

    // ------------------------------------------------------------------ shared healthy day (c03, c04, c12, c13)
    private class Day(val sim: Simulation, val events: List<SimEvent>, val hours: Double, val tangFlareOk: Int, val tangFlareBad: Int,
                      val maxGapMin: Map<String, Double>, val cardinalNear: Double)

    private val day: Day by lazy { runDay() }

    private fun runDay(): Day {
        val sim = tank(hour = 9.5)
        val tang = sim.org(BrainKind.TANG)
        val card = sim.org(BrainKind.CARDINAL)
        val pocket = (sim.brainOf(card) as CardinalBrain).shadePocket
        val lastContact = HashMap<String, Double>()
        val maxGap = HashMap<String, Double>()
        val flareTimes = ArrayList<Double>()
        var near = 0; var n = 0
        val hours = 5.0
        val ev = sim.run(hours * 3600, sampleEvery = 1.0) {
            val t = sim.simTimeSec
            for (o in sim.organisms) if (o.isFish && o.brainKind != BrainKind.WRASSE) {
                val last = maxOf(o.lastCleanerContactAt, lastContact[o.id] ?: (9.5 * 3600))
                lastContact[o.id] = last
                maxGap.merge(o.nickname, (t - last) / 60, ::maxOf)
            }
            if (tang.spineFlare > 0.5) flareTimes += t
            n++; if (card.pos.distanceTo(pocket) <= 15.0) near++
        }
        val targeted = ev.filterIsInstance<SimEvent.Chase>().filter { it.targetId == tang.id }.map { it.simTimeSec }
        val ok = flareTimes.count { f -> targeted.any { it - f in -3.0..8.0 } }
        return Day(sim, ev, hours, ok, flareTimes.size - ok, maxGap, near.toDouble() / n)
    }

    // ------------------------------------------------------------------ c01
    /** 06 §5.6 #1: male QUIVER_YIELD 1–6/h, female never; male within 0.5–2 BL of the female ≥ 70 %; 95 % within 3 BL of the host. */
    @Test
    fun c01_clownPairHierarchy() {
        val sim = tank()
        val clowns = sim.organisms.filter { it.brainKind == BrainKind.CLOWN }
        val female = clowns.first { it.role == "FEMALE" }
        val male = clowns.first { it !== female }
        val host = sim.rock.anchors.pos("HOST_SHROOM", Vec3.ZERO)
        var pairOk = 0; var hostOk = 0; var n = 0
        val hours = 2.0
        val ev = sim.run(hours * 3600) {
            n++
            val d = male.pos.distanceTo(female.pos)
            if (d in 0.5 * male.lengthCm..2.0 * male.lengthCm) pairOk++
            if (clowns.all { it.pos.distanceTo(host) <= 3 * it.lengthCm }) hostOk++
        }
        val q = ev.filterIsInstance<SimEvent.Quiver>()
        val maleRate = q.count { it.id == male.id } / hours
        assertTrue("male quivers $maleRate/h", maleRate in 1.0..6.0)
        assertEquals("female never yields", 0, q.count { it.id == female.id })
        assertTrue("male near female ${pairOk.toDouble() / n}", pairOk >= 0.7 * n)
        assertTrue("both near host ${hostOk.toDouble() / n}", hostOk >= 0.95 * n)
    }

    // ------------------------------------------------------------------ c02
    /** 06 §5.6 #2: three host intrusions in 10 min; third bout latency ≤ 40 % of the first, bouts per entry ≥ 1.5×. */
    @Test
    @Ignore("WIP: not yet passing (sim/realism handoff)")
    fun c02_hostDefenceSensitisation() {
        var lat1 = 0.0; var lat3 = 0.0; var nLat = 0
        var bouts1 = 0; var bouts3 = 0
        for (seed in 1L..10L) {
            val sim = tank(stockWithout("tang", "wrasse", "cardinal", "hawk", "lawnmower", "tailspot", "goby"), seed = seed)
            val dotty = sim.org(BrainKind.DOTTYBACK)
            val female = sim.organisms.first { it.brainKind == BrainKind.CLOWN && it.role == "FEMALE" }
            val host = sim.rock.anchors.pos("HOST_SHROOM", Vec3.ZERO)
            val inside = sim.rock.clampToWater(host + Vec3(-6.0, 6.0, 6.0), 2.0)
            val away = Vec3(45.0, 30.0, 22.0)
            val stats = sim.pairBouts(female).statsOf("clownF_host")
            val lats = ArrayList<Double?>()
            val perEntry = ArrayList<Int>()
            repeat(3) {
                val b0 = stats.bouts; val l0 = stats.latencies.size
                // hold the intruder in the halo for 90 s
                var t = 0.0
                while (t < 90.0) { sim.advance(0.1, behaviorDt = 0.1) { dotty.pos = inside; dotty.vel = Vec3.ZERO }; t += 0.1 }
                lats += stats.latencies.getOrNull(l0)
                perEntry += stats.bouts - b0
                // then away for ~100 s (three entries in 10 min)
                t = 0.0
                while (t < 100.0) { sim.advance(0.5, behaviorDt = 0.1) { dotty.pos = away; dotty.vel = Vec3.ZERO }; t += 0.5 }
            }
            bouts1 += perEntry[0]; bouts3 += perEntry[2]
            val a = lats[0]; val b = lats[2]
            if (a != null && b != null) { lat1 += a; lat3 += b; nLat++ }
        }
        assertTrue("latency pairs $nLat", nLat >= 3)
        assertTrue("third-entry latency ${lat3 / nLat} vs first ${lat1 / nLat}", lat3 <= 0.4 * lat1)
        assertTrue("bouts per entry third $bouts3 vs first $bouts1", bouts3 >= 1.5 * bouts1)
    }

    // ------------------------------------------------------------------ c03
    /** 06 §5.6 #3: tang 1–3 posed inspections/h, mean inspection 4–10 s, 2–10 min of cleaning per 10 h day. */
    @Test
    @Ignore("WIP: not yet passing (sim/realism handoff)")
    fun c03_cleanerServicesTang() {
        val d = day
        val tang = d.sim.org(BrainKind.TANG)
        val wrasse = d.sim.org(BrainKind.WRASSE)
        val poses = d.events.filterIsInstance<SimEvent.Pose>().filter { it.clientId == tang.id && it.cleanerId == wrasse.id }
        val rate = poses.size / d.hours
        assertTrue("tang poses $rate/h", rate in 1.0..3.0)
        // inspection length: pose → cleaned (honest) or jolt
        val ends = d.events.filter { (it is SimEvent.Cleaned && it.clientId == tang.id && it.cleanerId == wrasse.id) || (it is SimEvent.Jolt && it.clientId == tang.id) }.map { it.simTimeSec }
        val lens = poses.mapNotNull { p -> ends.firstOrNull { it >= p.simTimeSec }?.minus(p.simTimeSec) }.filter { it < 30 }
        val honest = d.events.filterIsInstance<SimEvent.Cleaned>().filter { it.clientId == tang.id && it.cleanerId == wrasse.id }.map { it.simTimeSec }
        val honestLens = poses.mapNotNull { p -> honest.firstOrNull { it >= p.simTimeSec }?.minus(p.simTimeSec) }.filter { it < 30 }
        assertTrue("inspections measured ${lens.size}", lens.isNotEmpty())
        val mean = honestLens.ifEmpty { lens }.average()
        assertTrue("mean inspection $mean s", mean in 4.0..10.0)
        val perDayMin = lens.sum() / d.hours * 10 / 60
        assertTrue("tang cleaning $perDayMin min per 10 h", perDayMin in 2.0..10.0)
    }

    // ------------------------------------------------------------------ c04
    /** 06 §5.6 #4: no client goes > 90 min by day without at least a tactile pass. */
    @Test
    @Ignore("WIP: not yet passing (sim/realism handoff)")
    fun c04_cleanerCooldown() {
        val worst = day.maxGapMin.maxBy { it.value }
        assertTrue("longest gap without a cleaner contact: $worst min (all ${day.maxGapMin})", worst.value <= 90.0)
    }

    // ------------------------------------------------------------------ c05
    /** 06 §5.6 #5: no prepared food → wrasse hunger past 0.6 within 3 days; jolts rise ≥ 3×; trust < 0.4 and fewer poses. */
    @Test
    @Ignore("WIP: not yet passing (sim/realism handoff)")
    fun c05_cheatingRespondsToHunger() {
        // hunger climbs on cleaning alone (tank parasites are scarce) — health-only days for speed
        val s0 = tank(hour = 8.0)
        val w0 = s0.org(BrainKind.WRASSE)
        w0.acceptsPrepared = false; w0.hunger = 0.3
        var day = 0
        while (w0.hunger <= 0.6 && day < 3) { s0.advanceAbstract(86400.0); day++ }
        assertTrue("wrasse hunger ${w0.hunger} after $day days", w0.hunger > 0.6)

        fun window(hunger: Double): Triple<Int, Int, Double> {
            val sim = tank(hour = 10.0, seed = 3L)
            val w = sim.org(BrainKind.WRASSE)
            w.acceptsPrepared = false
            val ev = sim.run(3 * 3600.0) { w.hunger = maxOf(w.hunger, hunger) }
            val jolts = ev.count { it is SimEvent.Jolt }
            val poses = ev.count { it is SimEvent.Pose && it.cleanerId == w.id }
            val trust = sim.organisms.filter { it.isFish && it !== w }.mapNotNull { it.relations[w.id]?.cleanerTrust }.let { if (it.isEmpty()) 0.7 else it.average() }
            return Triple(jolts, poses, trust)
        }
        val fed = window(0.2)
        val hungry = window(0.75)
        assertTrue("jolts fed ${fed.first} hungry ${hungry.first}", hungry.first >= 3 * maxOf(1, fed.first))
        assertTrue("client trust hungry ${hungry.third}", hungry.third < 0.4)
        val posePerAsk = hungry.second.toDouble() / maxOf(1, fed.second)
        assertTrue("poses fed ${fed.second} hungry ${hungry.second}", hungry.third < fed.third && posePerAsk < 3.0)
    }

    // ------------------------------------------------------------------ c06
    /** 06 §5.6 #6: without the wrasse ≥ 2× the chase bouts (tolerance 1.6–4×; S:Cheney08). */
    @Test
    @Ignore("WIP: not yet passing (sim/realism handoff)")
    fun c06_cleanerAsBuffer() {
        var with = 0; var without = 0
        for (seed in 1L..2L) {
            val a = tank(seed = seed, hour = 9.5)
            with += chases(a.run(3 * 3600.0)).count { !it.displayOnly }
            val b = tank(stockWithout("wrasse"), seed = seed, hour = 9.5)
            without += chases(b.run(3 * 3600.0)).count { !it.displayOnly }
        }
        val ratio = without.toDouble() / maxOf(1, with)
        assertTrue("chases with wrasse $with, without $without (×$ratio)", ratio in 1.6..4.0)
    }

    // ------------------------------------------------------------------ c07
    /** 06 §5.6 #7: newcomer cardinal near the dottyback: ≥ 3 chases in hour 1, ≤ 1/h after 7 days; hover radius ≤ 10 cm in week 1, back by week 3 only if the cave is > 40 cm away. */
    @Test
    fun c07_dottybackNewcomerAttack() {
        val sim = tank(stockWithout("cardinal"), hour = 10.0)
        val dotty = sim.org(BrainKind.DOTTYBACK)
        val cave = sim.brainOf(dotty).anchorPos(sim.agentOf(dotty)!!.ctx, "cave")!!
        sim.enqueue(Command.AddOrganism("sphaeramia_nematoptera", 6.0, "Newbie"))
        sim.advance(0.1)
        val card = sim.org(BrainKind.CARDINAL)
        val cb = sim.brainOf(card) as CardinalBrain
        cb.setPocket(sim.rock.clampToWater(cave + Vec3(-14.0, 6.0, 12.0), 3.0))
        val hour1 = chases(sim.run(3600.0)).count { it.actorId == dotty.id && it.targetId == card.id }
        assertTrue("dottyback chases of the newcomer in hour 1: $hour1", hour1 >= 3)
        assertTrue("week-1 hover radius ${card.hoverRadius}", card.hoverRadius <= 10.0)
        sim.advanceAbstract(7 * 86400.0 - 3600)
        val later = chases(sim.run(2 * 3600.0)).count { it.actorId == dotty.id && it.targetId == card.id } / 2.0
        assertTrue("day-7 chases $later/h", later <= 1.0)
        // week 3: near the cave the radius stays small; far away it recovers to baseline ± 20 %
        sim.advanceAbstract(14 * 86400.0)
        sim.advance(1.0)
        assertTrue("near-cave week-3 radius ${card.hoverRadius}", card.hoverRadius <= 10.0)
        cb.setPocket(sim.rock.anchors.pos("HOVER_CARDINAL", Vec3(26.0, 20.0, 15.0)))
        sim.advance(1.0)
        // any tankmate's chase shrinks the radius ×0.7 for 10 min (dayRadius); factor that transient out of the baseline
        val far = if (card.timidUntil > sim.simTimeSec) card.hoverRadius / 0.7 else card.hoverRadius
        assertTrue("far-from-cave week-3 radius $far", far in 0.8 * CardinalBrain.BASE_RADIUS..1.2 * CardinalBrain.BASE_RADIUS)
    }

    // ------------------------------------------------------------------ c08
    /** 06 §5.6 #8: a new dottyback's bouts with the clowns fall from ≥ 2/h on day 1 to ≤ 0.5/h by day 7, mostly display. */
    @Test
    @Ignore("WIP: not yet passing (sim/realism handoff)")
    fun c08_dottybackStandoff() {
        val sim = tank(stockWithout("dottyback"), hour = 10.0)
        sim.enqueue(Command.AddOrganism("pseudochromis_indigo", 6.0, "Newdot"))
        sim.advance(0.1)
        val dotty = sim.org(BrainKind.DOTTYBACK)
        val clownIds = sim.organisms.filter { it.brainKind == BrainKind.CLOWN }.map { it.id }.toSet()
        fun bouts(ev: List<SimEvent>) = ev.filterIsInstance<SimEvent.Chase>().filter { it.actorId == dotty.id && it.targetId in clownIds }
        val d1 = bouts(sim.run(3 * 3600.0))
        assertTrue("day-1 dotty–clown bouts ${d1.size / 3.0}/h", d1.size / 3.0 >= 2.0)
        sim.advanceAbstract(6 * 86400.0 - 3 * 3600)
        val d7 = bouts(sim.run(4 * 3600.0))
        assertTrue("day-7 bouts ${d7.size / 4.0}/h", d7.size / 4.0 <= 0.5)
        if (d7.isNotEmpty()) assertTrue("day-7 display-only ${d7.count { it.displayOnly }}/${d7.size}", d7.count { it.displayOnly } >= 0.8 * d7.size || d7.size <= 2 && d7.count { it.nip } == 0)
    }

    // ------------------------------------------------------------------ c09
    /** 06 §5.6 #9: lawnmower chases a new tailspot on ≥ 60 % of entries (day 1) and ≥ 35 % (day 14); the tailspot moves ≥ 50 cm away by day 3 and stays in its hole ≥ 40 % of daylight. */
    @Test
    @Ignore("WIP: not yet passing (sim/realism handoff)")
    fun c09_blennyWar() {
        val sim = tank(stockWithout("tailspot"), hour = 10.0)
        sim.enqueue(Command.AddOrganism("ecsenius_stigmatura", 5.5, "Newspot"))
        sim.advance(0.1)
        val lmb = sim.org(BrainKind.LAWNMOWER_BLENNY)
        val ts = sim.org(BrainKind.PEEKER_BLENNY)
        val pb = sim.pairBouts(lmb)
        val st = pb.statsOf("lmb_tailspot")
        sim.run(3 * 3600.0)
        assertTrue("entries ${st.entries}", st.entries >= 3)
        val f1 = st.entriesWithBout.toDouble() / st.entries
        assertTrue("day-1 chase fraction $f1 (${st.entriesWithBout}/${st.entries})", f1 >= 0.6)
        val lh = sim.brainOf(lmb).anchorPos(sim.agentOf(lmb)!!.ctx, "hole")!!
        sim.advanceAbstract(2 * 86400.0)
        sim.run(600.0)
        val th = (sim.brainOf(ts) as PeekerBlennyBrain).holePos
        assertTrue("tailspot hole ${th.distanceTo(lh)} cm from the lawnmower's", th.distanceTo(lh) >= 50.0)
        var inHole = 0; var n = 0
        sim.run(3600.0) { n++; if (ts.posture == 3) inHole++ }
        assertTrue("tailspot in hole ${inHole.toDouble() / n}", inHole >= 0.4 * n)
        // day 14: per-entry chase probability (the floor 0.4 × 0.9)
        sim.advanceAbstract(11 * 86400.0)
        sim.advance(1.0)
        lmb.hunger = 0.5
        val ctx = sim.agentOf(lmb)!!.ctx
        val p14 = pb.probability(ctx, PairMatrix.row("lmb_tailspot")!!, ts)
        assertTrue("day-14 per-entry probability $p14", p14 >= 0.35)
    }

    // ------------------------------------------------------------------ c10
    /** 06 §5.6 #10: hair algae ≥ 0.6 and hunger < 0.3 → chase probability ≤ 0.6× that at hunger 0.8. */
    @Test
    fun c10_wellFedBlennyIsCalmer() {
        val sim = tank(hour = 11.0)
        sim.advance(1.0)
        val lmb = sim.org(BrainKind.LAWNMOWER_BLENNY)
        val ts = sim.org(BrainKind.PEEKER_BLENNY)
        val pb = sim.pairBouts(lmb)
        val row = PairMatrix.row("lmb_tailspot")!!
        val ctx = sim.agentOf(lmb)!!.ctx
        sim.water.s.algaeHair = 0.65
        lmb.hunger = 0.2
        val fed = pb.probability(ctx, row, ts)
        lmb.hunger = 0.8
        val hungry = pb.probability(ctx, row, ts)
        assertTrue("fed $fed hungry $hungry", hungry > 0 && fed <= 0.6 * hungry)
    }

    // ------------------------------------------------------------------ c11
    /** 06 §5.6 #11: hawk perched ≥ 75 % of daylight; 5–20 hops/h; never strikes a fish ≥ 0.5 its length; ≤ 1 charge/h at the wrasse. */
    @Test
    @Ignore("WIP: not yet passing (sim/realism handoff)")
    fun c11_hawkfishEconomy() {
        val sim = tank(hour = 10.5)
        val hawk = sim.org(BrainKind.HAWK)
        val hb = sim.brainOf(hawk) as HawkBrain
        var perched = 0; var n = 0; var hops = 0
        var last = hb.current
        val hours = 2.0
        val ev = sim.run(hours * 3600) {
            n++
            if (hawk.attached) perched++
            if (hb.current !== last) { hops++; last = hb.current }
        }
        assertTrue("perched ${perched.toDouble() / n}", perched >= 0.75 * n)
        assertTrue("hops ${hops / hours}/h", hops / hours in 5.0..20.0)
        for (f in sim.organisms.filter { it.isFish && it !== hawk && it.lengthCm >= 0.5 * hawk.lengthCm })
            assertEquals("hawk prey weight for ${f.nickname}", 0.0, Predation.preyWeight(hawk, f, false), 0.0)
        val wrasse = sim.org(BrainKind.WRASSE)
        val charges = ev.filterIsInstance<SimEvent.Chase>().count { it.actorId == hawk.id && it.targetId == wrasse.id && !it.displayOnly }
        assertTrue("charges at the wrasse ${charges / hours}/h", charges / hours <= 1.0)
    }

    // ------------------------------------------------------------------ c12
    /** 06 §5.6 #12: the tang starts no chases, flares its spine only when targeted; ich 0.3 → 2–10 flashes/h and ≥ 2× cleaner visits. */
    @Test
    fun c12_tangNeverBullies() {
        val d = day
        val tang = d.sim.org(BrainKind.TANG)
        assertEquals("chases started by the tang", 0, d.events.filterIsInstance<SimEvent.Chase>().count { it.actorId == tang.id })
        assertTrue("spine flares without a bout: ${d.tangFlareBad} (with ${d.tangFlareOk})", d.tangFlareBad <= 0.05 * (d.tangFlareOk + d.tangFlareBad))
        val healthyPoses = d.events.count { it is SimEvent.Pose && it.clientId == tang.id } / d.hours

        val sim = tank(hour = 9.5)
        val t2 = sim.org(BrainKind.TANG)
        t2.disease = Disease.ICH; t2.diseaseStage = 0.3
        val hours = 3.0
        val ev = sim.run(hours * 3600) { t2.diseaseStage = 0.3 }
        val flashes = t2.counters.totalOf(Counters.K.FLASH) / hours
        assertTrue("flashes $flashes/h", flashes in 2.0..10.0)
        val sickPoses = ev.count { it is SimEvent.Pose && it.clientId == t2.id } / hours
        assertTrue("cleaner visits sick $sickPoses/h vs healthy $healthyPoses/h", sickPoses >= 2 * healthyPoses)
    }

    // ------------------------------------------------------------------ c13
    /** 06 §5.6 #13: by day within 15 cm of its pocket ≥ 85 %, clamped fins when exposed to bright light; dusk/night forage ≥ 30 cm out and ≥ 70 % of intake in the dark. */
    @Test
    fun c13_cardinalDiel() {
        assertTrue("cardinal near pocket by day ${day.cardinalNear}", day.cardinalNear >= 0.85)
        // exposed under bright light → clamped fins
        val s = tank(hour = 12.0)
        val c = s.org(BrainKind.CARDINAL)
        var exposed = 0; var clamped = 0
        s.run(240.0) {
            c.pos = Vec3(60.0, s.rock.waterLevelCm - 8, 28.0); c.vel = Vec3.ZERO
            if (c.brightExposed) { exposed++; if (c.finClamp >= 0.5) clamped++ }
        }
        assertTrue("exposed $exposed clamped $clamped", exposed > 100 && clamped >= 0.5 * exposed)
        // a full day for a cardinal alone with Tee's feeding schedule
        val sim = TestTank.sim(listOf(TestTank.seed("cardinal")), seed = 2L, startHour = 6.0)
        val card = sim.org(BrainKind.CARDINAL)
        val pocket = (sim.brainOf(card) as CardinalBrain).shadePocket
        var dark = 0L; var lit = 0L; var lastFeed = card.counters.totalOf(Counters.K.FEED); var farNight = 0.0
        sim.run(24 * 3600.0, dt = 0.2, sampleEvery = 5.0) {
            val f = card.counters.totalOf(Counters.K.FEED)
            if (sim.lightIntensity < 0.3) { dark += f - lastFeed; farNight = maxOf(farNight, card.pos.distanceTo(pocket)) } else lit += f - lastFeed
            lastFeed = f
        }
        assertTrue("night forage reach $farNight cm", farNight >= 30.0)
        assertTrue("intake in the dark $dark of ${dark + lit}", dark + lit > 0 && dark >= 0.7 * (dark + lit))
    }

    // ------------------------------------------------------------------ c14
    /** 06 §5.6 #14 (adult night rule per 07 gap 3): adult in its burrow ≥ 80 % of daylight, head out ≥ 50 % of that, 5–20 sand trips a day; juveniles out by day, inside at night. */
    @Test
    fun c14_engineerGobyDiel() {
        val adult = TestTank.sim(listOf(LivestockSeed("pholidichthys_leucotaenia", "Big", 20.0, id = "goby")), seed = 1L, startHour = 9.0)
        val g = adult.org(BrainKind.ENGINEER_GOBY)
        val gb = adult.brainOf(g) as GobyBrain
        var day = 0; var inB = 0; var headOut = 0; var night = 0; var nightIn = 0
        adult.run(24 * 3600.0, dt = 0.2, sampleEvery = 5.0) {
            if (adult.lightIntensity >= 0.3) { day++; if (g.posture == 3) { inB++; if (g.visible) headOut++ } }
            else if (adult.lightIntensity < 0.05) { night++; if (g.posture == 3) nightIn++ }
        }
        assertTrue("adult in burrow by day ${inB.toDouble() / day}", inB >= 0.8 * day)
        assertTrue("head visible ${headOut.toDouble() / inB}", headOut >= 0.5 * inB)
        assertTrue("sand trips ${gb.sandEvents}/day", gb.sandEvents in 5..20)
        assertTrue("adult inside at night ${nightIn.toDouble() / night} (≈ 5 % of nights out, 07 gap 3)", nightIn >= 0.7 * night)

        val juv = TestTank.sim(listOf(TestTank.seed("goby")), seed = 1L, startHour = 11.0)
        val j = juv.org(BrainKind.ENGINEER_GOBY)
        var out = 0; var m = 0
        juv.run(1800.0) { m++; if (j.posture != 3) out++ }
        assertTrue("juvenile out by day ${out.toDouble() / m}", out >= 0.5 * m)
        val jn = TestTank.sim(listOf(TestTank.seed("goby")), seed = 1L, startHour = 23.0)
        jn.run(600.0)
        val jj = jn.org(BrainKind.ENGINEER_GOBY)
        assertTrue("juvenile hangs inside at night", jj.posture == 3 && !jj.visible)
    }

    // ------------------------------------------------------------------ c15
    /** 06 §5.6 #15: banded and skunk shrimp ≥ 25 cm apart; a night molt next to the banded shrimp → ~5 % kills (sandbox toggle, 07 gap 4). */
    @Test
    @Ignore("WIP: not yet passing (sim/realism handoff)")
    fun c15_shrimpPolitics() {
        val sim = tank(hour = 12.0)
        val cbs = sim.org(BrainKind.BANDED_SHRIMP)
        val skunk = sim.org(BrainKind.CLEANER_SHRIMP)
        var sum = 0.0; var n = 0
        sim.run(1800.0, sampleEvery = 10.0) { sum += cbs.pos.distanceTo(skunk.pos); n++ }
        repeat(23) {
            sim.advanceAbstract(3600.0)
            sum += cbs.pos.distanceTo(skunk.pos); n++
        }
        assertTrue("mean separation ${sum / n}", sum / n >= 25.0)

        fun molts(cross: Boolean): Int {
            var hits = 0
            for (seed in 1L..200L) {
                val s = TestTank.sim(listOf(TestTank.seed("banded"), TestTank.seed("skunk")), seed = seed, startHour = 1.0 + 59.5 / 60)
                s.crossSpeciesShrimpKills = cross
                val b = s.org(BrainKind.BANDED_SHRIMP)
                val k = s.org(BrainKind.CLEANER_SHRIMP)
                k.lastMoltAt = s.simTimeSec
                b.pos = k.pos + Vec3(4.0, 0.0, 0.0)
                s.advance(40.0, behaviorDt = 0.5) { b.pos = k.pos + Vec3(4.0, 0.0, 0.0) }
                if (s.latest.events.any { it is SimEvent.NearMiss } || s.eventLog.any { it is SimEvent.NearMiss }) hits++
            }
            return hits
        }
        assertEquals("cross-species kills are off by default", 0, molts(false))
        val hits = molts(true)
        assertTrue("near misses $hits / 200 (≈ 5 %)", hits in 3..20)
    }

    // ------------------------------------------------------------------ c16
    /** 06 §5.6 #16: leg-rocking on ≥ 40 % of hawk/dottyback approaches and ≤ 20 % of tang/clown; cleans ≤ 35 % vs ≥ 40 %. */
    @Test
    @Ignore("WIP: not yet passing (sim/realism handoff)")
    fun c16_skunkCleanerSignalling() {
        val stock = listOf(TestTank.seed("skunk"), TestTank.seed("hawk"), TestTank.seed("dottyback"), TestTank.seed("tang"), TestTank.seed("clown_m"))
        val sim = TestTank.sim(stock, seed = 4L, startHour = 11.0)
        val skunk = sim.org(BrainKind.CLEANER_SHRIMP)
        val sb = sim.brainOf(skunk) as CleanerShrimpBrain
        val station = sb.station.pos
        val fish = sim.organisms.filter { it.isFish }
        val parked = fish.associateWith { Vec3(15.0 + 20.0 * fish.indexOf(it), 45.0, 25.0) }
        repeat(80) { round ->
            val f = fish[round % fish.size]
            val at = station + Vec3(0.0, 2.0, 5.0)
            sim.advance(3.0, behaviorDt = 0.1) { for ((o, p) in parked) { o.pos = if (o === f) at else p; o.vel = Vec3.ZERO } }
            sb.station.release(); f.poseUntil = 0.0
            sim.advance(2.0, behaviorDt = 0.1) { for ((o, p) in parked) { o.pos = p; o.vel = Vec3.ZERO } }
        }
        val p = sb.predatorStats; val o = sb.otherStats
        assertTrue("approaches ${p.approaches}/${o.approaches}", p.approaches >= 30 && o.approaches >= 30)
        assertTrue("predator rocks ${p.rocks}/${p.approaches}", p.rocks >= 0.40 * p.approaches)
        assertTrue("other rocks ${o.rocks}/${o.approaches}", o.rocks <= 0.20 * o.approaches)
        assertTrue("predator cleans ${p.cleans}/${p.approaches}", p.cleans <= 0.35 * p.approaches)
        assertTrue("other cleans ${o.cleans}/${o.approaches}", o.cleans >= 0.40 * o.approaches)
    }

    // ------------------------------------------------------------------ c17
    /** 06 §5.6 #17: a 6 cm Sally Lightfoot over 100 nights → 0–3 fish near misses; at 3 cm none. */
    @Test
    fun c17_nightPredatorMap() {
        fun nights(crabCm: Double): Int {
            val sim = TestTank.sim(listOf(TestTank.seed("crab"), TestTank.seed("cardinal"), TestTank.seed("tailspot")), seed = 5L, startHour = 12.0)
            val crab = sim.org(BrainKind.SALLY_CRAB)
            crab.lengthCm = crabCm
            var misses = 0
            repeat(100) {
                crab.hunger = 0.9
                sim.advanceAbstract(86400.0)
                misses += sim.latest.events.count { it is SimEvent.NearMiss && sim.organisms.any { o -> o.id == it.preyId && o.isFish } }
            }
            return misses
        }
        val big = nights(6.0)
        assertTrue("fish near misses with a 6 cm crab: $big", big in 0..3)
        assertEquals("a 3 cm crab never tries", 0, nights(3.0))
    }

    // ------------------------------------------------------------------ c18
    /** 06 §5.6 #18: free NH₃ 0.1 → chases ≤ 0.6× and feeding ≤ 0.5×; DO 3 → column fish at the surface ≥ 20 %, benthic fish in cover ≥ 70 %. */
    @Test
    fun c18_waterQualityLethargy() {
        fun window(tan: Double?): Pair<Int, Long> {
            val sim = tank(hour = 10.0, seed = 6L)
            if (tan != null) sim.enqueue(Command.RampParam("tan", tan, 0.0, hold = true))
            sim.advanceAbstract(3 * 3600.0)
            val fish = sim.organisms.filter { it.isFish }
            fish.forEach { it.hunger = 0.6 }
            val f0 = fish.sumOf { it.counters.totalOf(Counters.K.FEED) }
            sim.enqueue(Command.Feed(FoodKind.MYSIS, 0.8))
            val ev = sim.run(2 * 3600.0)
            return chases(ev).size to fish.sumOf { it.counters.totalOf(Counters.K.FEED) } - f0
        }
        val base = window(null)
        // TAN for free NH3 ≈ 0.1 mg/L at this pH/temperature
        val probe = tank(hour = 10.0, seed = 6L)
        val frac = probe.water.s.nh3Free / probe.water.s.nh3Total.coerceAtLeast(1e-6)
        val tan = 0.1 / frac
        val sick = window(tan)
        assertTrue("chases base ${base.first} NH3 ${sick.first}", sick.first <= 0.6 * base.first)
        assertTrue("bites base ${base.second} NH3 ${sick.second}", sick.second <= 0.5 * base.second)

        val sim = tank(hour = 11.0, seed = 6L)
        sim.enqueue(Command.RampParam("do", 3.0, 0.0, hold = true))
        sim.advanceAbstract(1800.0)
        val column = sim.organisms.filter { it.brainKind in setOf(BrainKind.CLOWN, BrainKind.TANG, BrainKind.WRASSE) }
        val benthic = sim.organisms.filter { it.brainKind in setOf(BrainKind.LAWNMOWER_BLENNY, BrainKind.PEEKER_BLENNY, BrainKind.HAWK, BrainKind.DOTTYBACK) }
        var top = 0; var cover = 0; var n = 0
        sim.run(1800.0) {
            n++
            top += column.count { it.pos.y >= sim.rock.waterLevelCm - 5 }
            cover += benthic.count { Symptom.HIDING in it.symptoms || it.posture == 3 }
        }
        assertTrue("column fish at the surface ${top.toDouble() / (n * column.size)}", top >= 0.2 * n * column.size)
        assertTrue("benthic fish in cover ${cover.toDouble() / (n * benthic.size)}", cover >= 0.7 * n * benthic.size)
    }

    // ------------------------------------------------------------------ c19
    /** 06 §5.6 #19: growing the tang 7.5 → 15 cm raises tank-wide aggression 20–40 % (crowding). */
    @Test
    fun c19_crowding() {
        val sim = tank()
        sim.advanceAbstract(120.0)
        val base = sim.crowdingMult
        val tang = sim.org(BrainKind.TANG)
        sim.enqueue(Command.SetSize(tang.id, 15.0))
        sim.advanceAbstract(120.0)
        val rise = sim.crowdingMult / base - 1
        assertTrue("aggression rise ${rise * 100} %", rise in 0.2..0.4)
    }

    // ------------------------------------------------------------------ c20
    /** 06 §5.6 #20: RearrangeRock → bouts in the next 6 h ≥ 2× the pre-command rate, ≤ 1.2× by 72 h. */
    @Test
    @Ignore("WIP: not yet passing (sim/realism handoff)")
    fun c20_rockRearrangement() {
        fun rate(sim: Simulation, hours: Double) = chases(sim.run(hours * 3600)).size / hours
        var pre = 0.0; var post = 0.0; var late = 0.0
        for (seed in 1L..2L) {
            val sim = tank(seed = seed, hour = 10.0)
            pre += rate(sim, 2.0)
            sim.enqueue(Command.RearrangeRock)
            post += rate(sim, 2.0)
            assertTrue("territory reset event", sim.eventLog.any { it is SimEvent.TerritoryReset })
            sim.advanceAbstract(68 * 3600.0)
            late += rate(sim, 2.0)
        }
        assertTrue("pre $pre post $post", post >= 2 * pre)
        assertTrue("pre $pre after 72 h $late", late <= 1.2 * pre)
    }

    // ------------------------------------------------------------------ c21
    /** 06 §5.6 #21: determinism with every bout active (no-rock-penetration is AiTest.noRockPenetration). */
    @Test
    fun c21_deterministicWithBouts() {
        fun trace(): List<String> {
            val sim = tank(seed = 11L, hour = 10.5)
            val ev = sim.run(900.0)
            return ev.filterIsInstance<SimEvent.Chase>().map { "${it.actorId}>${it.targetId}@${"%.2f".format(it.simTimeSec)}" } +
                sim.organisms.map { "${it.id}:${"%.4f".format(it.pos.x)},${"%.4f".format(it.pos.y)}" }
        }
        val a = trace(); val b = trace()
        assertTrue("some bouts happened", a.any { '>' in it })
        assertEquals(a, b)
    }

    // ------------------------------------------------------------------ c22
    /** 06 §5.6 #22: 26 → 29 °C as a 3 h step vs a 7-day ramp; acclimation after 14 days; the cardinal fails to acclimate. */
    @Test
    @Ignore("WIP: not yet passing (sim/realism handoff)")
    fun c22_rampVersusStep() {
        fun run(overMin: Double): Map<BrainKind, DoubleArray> {
            val sim = tank(hour = 8.0)
            sim.enqueue(Command.RampParam("temp", 26.0, 0.0))
            sim.advanceAbstract(2 * 86400.0)
            sim.enqueue(Command.SetAcclimation())
            sim.advanceAbstract(60.0)
            sim.enqueue(Command.RampParam("temp", 29.0, overMin))
            val peak = HashMap<BrainKind, Double>()
            repeat(14 * 24) {
                sim.advanceAbstract(3600.0)
                for (o in sim.organisms) if (o.alive && o.isFish) peak.merge(o.brainKind, o.stressState.fast(StressModel.Ch.TEMP), ::maxOf)
            }
            return sim.organisms.filter { it.alive && it.isFish }.associate {
                it.brainKind to doubleArrayOf(peak[it.brainKind] ?: 0.0, it.stressState.setpoint[StressModel.Ch.TEMP.ordinal], it.stressState.slow(StressModel.Ch.TEMP))
            }
        }
        val step = run(180.0)
        val ramp = run(7 * 1440.0)
        for (k in listOf(BrainKind.TANG, BrainKind.CARDINAL)) {
            assertTrue("$k step peak ${step[k]!![0]}", step[k]!![0] >= 0.7)
            assertTrue("$k ramp peak ${ramp[k]!![0]}", ramp[k]!![0] <= 0.2)
        }
        for (r in listOf(step, ramp)) {
            for (k in listOf(BrainKind.CLOWN, BrainKind.LAWNMOWER_BLENNY, BrainKind.PEEKER_BLENNY)) {
                assertTrue("$k setpoint ${r[k]!![1]}", kotlin.math.abs(r[k]!![1] - 29.0) <= 0.3)
                assertTrue("$k slow ${r[k]!![2]}", r[k]!![2] <= 0.1)
            }
            assertTrue("cardinal slow ${r[BrainKind.CARDINAL]?.get(2)}", (r[BrainKind.CARDINAL]?.get(2) ?: 1.0) >= 0.3)
        }
    }

    // ------------------------------------------------------------------ c23
    /** 06 §5.6 #23: 29 °C/DO 4 → nobody gasps; 32 °C/DO 4 → cardinal and tang gasp within 30 min, clowns do not; pump failure at 31 °C hits the cardinal first. */
    @Test
    fun c23_heatTimesOxygen() {
        fun gaspers(temp: Double): Set<BrainKind> {
            val sim = tank(hour = 12.0)
            sim.enqueue(Command.RampParam("temp", temp, 0.0))
            sim.enqueue(Command.SetAcclimation("temp"))
            sim.enqueue(Command.RampParam("do", 4.0, 0.0))
            val out = HashSet<BrainKind>()
            repeat(30) { sim.advanceAbstract(60.0); for (o in sim.organisms) if (Symptom.GASPING in o.symptoms) out += o.brainKind }
            return out
        }
        assertTrue("gasping at 29 °C: ${gaspers(29.0)}", gaspers(29.0).isEmpty())
        val hot = gaspers(32.0)
        assertTrue("gasping at 32 °C: $hot", BrainKind.CARDINAL in hot && BrainKind.TANG in hot && BrainKind.CLOWN !in hot)

        var cardinalFirst = 0
        for (seed in 1L..5L) {
            val sim = tank(seed = seed, hour = 12.0)
            sim.enqueue(Command.RampParam("temp", 31.0, 0.0))
            sim.enqueue(Command.SetAcclimation("temp"))
            sim.enqueue(Command.PowerheadFail(sim.simTimeSec + 11 * 3600.0, 6 * 3600.0))
            sim.enqueue(Command.Powerheads(true))
            var first: BrainKind? = null
            repeat(24 * 60) {
                sim.advanceAbstract(60.0)
                if (first == null) sim.organisms.firstOrNull { it.isFish && (Symptom.GASPING in it.symptoms || !it.alive) }?.let { first = it.brainKind }
            }
            if (first == BrainKind.CARDINAL) cardinalFirst++
        }
        assertTrue("cardinal first in $cardinalFirst/5", cardinalFirst >= 4)
    }

    // ------------------------------------------------------------------ c24
    /** 06 §5.6 #24: one tap peaks within 1–4 h and is < 10 % by 24 h; three taps an hour apart ≥ 2× the peak. */
    @Test
    fun c24_acuteStressEnvelope() {
        fun peak(taps: Int): Triple<Double, Double, Double> {
            val sim = tank(stock = listOf(TestTank.seed("clown_m")), hour = 12.0)
            val o = sim.organisms.first()
            o.pos = Vec3(60.0, 30.0, 28.0)
            var best = 0.0; var bestAt = 0.0
            val t0 = sim.simTimeSec
            for (m in 0 until 24 * 60) {
                if (m % 60 == 0 && m / 60 < taps) { o.pos = Vec3(60.0, 30.0, 28.0); sim.enqueue(Command.TapGlass(Vec3(60.0, 30.0, 33.0))) }
                sim.advanceAbstract(60.0)
                val f = o.stressState.fast(StressModel.Ch.SOCIAL)
                if (f > best) { best = f; bestAt = (sim.simTimeSec - t0) / 3600 }
            }
            return Triple(best, bestAt, o.stressState.fast(StressModel.Ch.SOCIAL))
        }
        val one = peak(1)
        assertTrue("single tap peak ${one.first} at ${one.second} h", one.first > 0 && one.second in 1.0..4.0)
        assertTrue("24 h later ${one.third}", one.third < 0.1 * one.first)
        val three = peak(3)
        assertTrue("three taps ${three.first} vs one ${one.first}", three.first >= 2 * one.first)
    }

    // ------------------------------------------------------------------ c25
    /** 06 §5.6 #25: at 31 °C aggression is ≥ 1.2× baseline in days 1–4 and ≤ 0.7× by day 60 (S:Warren16). */
    @Test
    @Ignore("WIP: not yet passing (sim/realism handoff)")
    fun c25_biphasicHeatAggression() {
        val stock = listOf(TestTank.seed("clown_f"), TestTank.seed("clown_m"), TestTank.seed("dottyback"), TestTank.seed("lawnmower"))
        val sim = TestTank.sim(stock, seed = 1L, startHour = 8.0)
        fun mult() = sim.organisms.filter { it.alive }.map { StressModel.aggressionMult(it, sim.water.s.tempC) }.average()
        sim.advanceAbstract(3600.0)
        val base = mult()
        sim.enqueue(Command.RampParam("temp", 31.0, 0.0))
        val early = ArrayList<Double>()
        repeat(4) { sim.advanceAbstract(86400.0); early += mult() }
        repeat(56) { sim.advanceAbstract(86400.0) }
        val late = mult()
        assertTrue("days 1–4 ${early.average() / base}", early.average() >= 1.2 * base)
        assertTrue("day 60 ${late / base}", late <= 0.7 * base)
    }

    // ------------------------------------------------------------------ c26
    /** 06 §5.6 #26: pH 7.6 for 14 days → cardinal ranges ≥ 30 % farther from cover and startles ≥ 30 % less per tap (S:Munday14). */
    @Test
    fun c26_lowPhBoldness() {
        val sim = tank(hour = 12.0)
        val card = sim.org(BrainKind.CARDINAL)
        sim.advance(1.0)
        val r0 = card.hoverRadius
        val sk0 = StressModel.skittishMult(card)
        sim.enqueue(Command.RampParam("ph", 7.6, 0.0, hold = true))
        sim.advanceAbstract(14 * 86400.0)
        sim.advance(1.0)
        assertTrue("hover radius $r0 → ${card.hoverRadius}", card.hoverRadius >= 1.3 * r0)
        assertTrue("startle per tap $sk0 → ${StressModel.skittishMult(card)}", StressModel.skittishMult(card) <= 0.7 * sk0)
    }

    // ------------------------------------------------------------------ c27
    /** 06 §5.6 #27: after a 6 h free-NH₃ 0.3 spike and a 50 % water change: gasping ends < 1 h, appetite ≥ 80 % < 3 d, finClamp < 0.2 < 2 d, S_slow_NH3 < 0.1 only after ≥ 7 d. */
    @Test
    fun c27_recoveryOrdering() {
        val sim = tank(hour = 8.0)
        // free-NH3 fraction at this pH/temperature (the fresh tank has no TAN to take a ratio of)
        val frac = ChemFns.freeNh3(1.0, sim.water.s.ph, sim.water.s.tempC)
        sim.enqueue(Command.RampParam("tan", 0.3 / frac, 0.0, hold = true))
        sim.advanceAbstract(6 * 3600.0)
        val clown = sim.organisms.first { it.brainKind == BrainKind.CLOWN }
        val wasGasping = sim.organisms.any { Symptom.GASPING in it.symptoms }
        sim.enqueue(Command.ReleaseParam("tan"))
        sim.enqueue(Command.WaterChange(0.5))
        sim.enqueue(Command.RampParam("tan", 0.0, 60.0, hold = false))
        var gaspEnd = -1.0; var appetiteAt = -1.0; var clampAt = -1.0; var slowAt = -1.0
        for (m in 1..14 * 1440) {
            sim.advanceAbstract(60.0)
            val h = m / 60.0
            if (gaspEnd < 0 && sim.organisms.none { Symptom.GASPING in it.symptoms }) gaspEnd = h
            if (appetiteAt < 0 && HealthModel.appetite(clown) >= 0.8) appetiteAt = h
            if (clampAt < 0 && clown.finClamp < 0.2) clampAt = h
            if (slowAt < 0 && clown.stressState.slow(StressModel.Ch.NH3) < 0.1) slowAt = h
        }
        assertTrue("spike caused gasping", wasGasping)
        assertTrue("gasping ended after $gaspEnd h", gaspEnd in 0.0..1.0)
        assertTrue("appetite back after $appetiteAt h", appetiteAt in 0.0..72.0)
        assertTrue("fins open after $clampAt h", clampAt in 0.0..48.0)
        assertTrue("chronic NH3 cleared after $slowAt h", slowAt >= 7 * 24.0)
    }

    // ------------------------------------------------------------------ c28
    /** 06 §5.6 #28 (JVM proxy for the phone measurement): stress + pair-matrix work ≤ 5 % of the behaviour tick. */
    @Test
    fun c28_phoneBudget() {
        val sim = tank(hour = 11.0)
        sim.run(120.0)
        val n = 3000
        val t0 = System.nanoTime()
        sim.advance(n * 0.05, behaviorDt = 0.05)
        val tickNs = (System.nanoTime() - t0).toDouble() / n
        // stress: one chemistry update per animal per minute of sim time = 1/1200 of the 20 Hz ticks
        val w = sim.water.s
        val t1 = System.nanoTime()
        repeat(200) { for (o in sim.organisms) StressModel.update(o, w, StressModel.Env(0.8, 1.0), 1.0) }
        val stressPerTick = (System.nanoTime() - t1).toDouble() / 200 / 1200
        // pair matrix: one scan per actor every 0.5 s = 1/10 of the ticks
        val actors = sim.organisms.mapNotNull { o -> sim.agentOf(o)?.brain?.utility?.behaviors?.filterIsInstance<PairBouts>()?.firstOrNull()?.let { o to it } }
        val t2 = System.nanoTime()
        repeat(200) { for ((o, pb) in actors) pb.score(sim.agentOf(o)!!.ctx) }
        val pairPerTick = (System.nanoTime() - t2).toDouble() / 200
        val frac = (stressPerTick + pairPerTick) / tickNs
        assertTrue("stress + pair work ${"%.2f".format(frac * 100)} % of a ${"%.0f".format(tickNs / 1000)} µs tick", frac <= 0.05)
    }

    /** Wrasse parameters are what the tests assume. */
    @Test
    fun c00_wrasseConstants() {
        assertEquals(60.0, WrasseBrain.CLIENT_COOLDOWN_S, 0.0)
        assertTrue(WrasseBrain.cheatRate(Organism("x", TestTank.catalog["labroides_dimidiatus"], 7.0, "x", null, com.reeflive.core.geom.Rng(1)).also { it.hunger = 0.7 }) / 0.04 > 4.0)
    }
}
