package com.reeflive.core.ai

import com.reeflive.core.geom.Rng
import com.reeflive.core.geom.Vec3
import com.reeflive.core.sim.BrainKind
import com.reeflive.core.sim.Command
import com.reeflive.core.sim.Coral
import com.reeflive.core.sim.CoralGrowth
import com.reeflive.core.sim.Counters
import com.reeflive.core.sim.DefaultStock
import com.reeflive.core.sim.FoodKind
import com.reeflive.core.sim.HermitLegs
import com.reeflive.core.sim.RockData
import com.reeflive.core.sim.SimEvent
import com.reeflive.core.sim.Simulation
import com.reeflive.core.sim.TestTank
import com.reeflive.core.sim.WaterState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The remaining research 06 behaviours (HANDOFF open item 4): feeding displacement by rank (§3.2), the blue tang's
 * `blueDeepen` (§4.1.3, §5.4), the banded shrimp's and hermits' claw threat postures (§2.14, §2.18), territory
 * re-anchoring after RearrangeRock (§5.6 #20) and GSP overgrowth held off by neighbours that fight back (03 D).
 */
class BehavioursRankTest {
    private fun stock(vararg ids: String) = ids.map { TestTank.seed(it) }
    private fun Simulation.org(id: String) = organisms.first { it.id == id }
    private fun f(v: Double) = "%.2f".format(v)

    // ------------------------------------------------------------------ feeding displacement (06 §3.2)
    private class FeedRun(val domBites: Long, val subBites: Long, val displaced: Long, val given: Long, val waitedS: Double, val nearestWaitCm: Double)

    /** Dottyback and cardinal side by side with food dropped between them, displacement on or off. */
    private fun feedRun(seed: Long, displacement: Boolean): FeedRun {
        val sim = TestTank.sim(stock("dottyback", "cardinal"), seed = seed, startHour = 12.0)
        sim.feedDisplacement = displacement
        sim.advance(30.0, behaviorDt = 0.1)
        val dom = sim.org("dottyback")
        val sub = sim.org("cardinal")
        val spot = Vec3(60.0, 30.0, 20.0)
        var waited = 0.0
        var nearest = Double.MAX_VALUE
        repeat(4) {
            dom.hunger = 0.9; sub.hunger = 0.9
            // both come at the food from the same side, as they do when they rush a pinch
            dom.pos = spot + Vec3(-1.5, 8.0, 0.0); sub.pos = spot + Vec3(1.5, 8.0, 0.0)
            dom.vel = Vec3.ZERO; sub.vel = Vec3.ZERO
            sim.enqueue(Command.Feed(FoodKind.PELLET, 0.5, spot))
            sim.advance(60.0, behaviorDt = 0.1) {
                // both stay hungry: the food, not appetite, runs out
                dom.hunger = 0.9; sub.hunger = 0.9
                if (sub.brain.current?.label == "Waiting its turn at the food") {
                    waited += 0.1
                    nearest = minOf(nearest, sub.pos.distanceTo(dom.pos))
                }
            }
        }
        return FeedRun(dom.counters.totalOf(Counters.K.FEED), sub.counters.totalOf(Counters.K.FEED),
            sub.counters.totalOf(Counters.K.DISPLACED), dom.counters.totalOf(Counters.K.DISPLACE_GIVEN), waited, nearest)
    }

    @Test
    fun subordinateGivesWayAndGetsLessFood() {
        var on = 0L; var off = 0L; var domOn = 0L; var domOff = 0L; var displaced = 0L; var given = 0L; var waited = 0.0
        for (seed in 1L..3L) {
            val a = feedRun(seed, true)
            val b = feedRun(seed, false)
            println("06 §3.2 seed $seed: displacement on dotty ${a.domBites} cardinal ${a.subBites} (displaced ${a.displaced}, waited ${f(a.waitedS)} s, closest ${f(a.nearestWaitCm)} cm); off dotty ${b.domBites} cardinal ${b.subBites}")
            on += a.subBites; off += b.subBites; domOn += a.domBites; domOff += b.domBites
            displaced += a.displaced; given += a.given; waited += a.waitedS
            assertEquals("no displacement when off", 0L, b.displaced)
        }
        assertTrue("the cardinal gave way: $displaced", displaced >= 3)
        assertEquals("each yield is counted on both sides", displaced, given)
        assertTrue("the cardinal ate less with displacement: $on vs $off", on < off)
        assertTrue("the dottyback ate at least as much: $domOn vs $domOff", domOn >= domOff)
    }

    @Test
    fun rankDecidesWhoYields() {
        val sim = TestTank.sim(stock("dottyback", "cardinal", "tang"), startHour = 12.0)
        val dotty = sim.org("dottyback"); val card = sim.org("cardinal")
        // off its cave the dottyback drops two places but still ranks far above the cardinal (06 §3.1)
        dotty.pos = dotty.homePos + Vec3(40.0, 0.0, 0.0)
        assertTrue(Feed.feedRank(dotty, sim) > Feed.feedRank(card, sim) + Feed.RANK_MARGIN)
        // the yield roll is a hash, not a random draw: the same pair and particle always decide the same way, and about
        // 70 % of contests end in a yield
        val p = com.reeflive.core.sim.FoodParticle(17, FoodKind.MYSIS, Vec3.ZERO, Vec3.ZERO, 0.0, 1)
        assertEquals(Feed.contestRoll(card, p), Feed.contestRoll(card, p), 0.0)
        val share = (0 until 2000).count { Feed.contestRoll(card, com.reeflive.core.sim.FoodParticle(it, FoodKind.MYSIS, Vec3.ZERO, Vec3.ZERO, 0.0, 1)) < Feed.YIELD_P } / 2000.0
        assertTrue("yield share $share", share in 0.66..0.74)
    }

    // ------------------------------------------------------------------ blue tang colour (06 §4.1.3, §5.4)
    @Test
    fun blueTangDeepensInADisplayAndFades() {
        val sim = TestTank.sim(stock("tang"), startHour = 12.0)
        val tang = sim.org("tang")
        sim.advance(5.0, behaviorDt = 0.1)
        assertTrue("calm tang ${tang.blueDeepen}", tang.blueDeepen < 0.05)
        // one spine flare held for a second (SPINE_FLARE: +0.3)
        sim.advance(1.0, behaviorDt = 0.1) { tang.spineFlare = 1.0 }
        val peak = tang.blueDeepen
        println("06 §5.4 blueDeepen after a 1 s flare ${f(peak)}")
        assertTrue("deepened by a flare: $peak", peak in 0.25..0.5)
        assertEquals(peak.toFloat(), tang.toPose().blueDeepen, 0.02f)
        // repeated flares stack up toward full
        repeat(4) { sim.advance(1.0, behaviorDt = 0.1) { tang.spineFlare = 1.0 }; sim.advance(1.0, behaviorDt = 0.1) }
        assertTrue("stacked: ${tang.blueDeepen}", tang.blueDeepen > peak + 0.4)
        // it fades over a couple of minutes once the display is over
        sim.advance(150.0, behaviorDt = 0.1)
        assertTrue("faded: ${tang.blueDeepen}", tang.blueDeepen < 0.05)
        // other fish never change colour this way
        val sim2 = TestTank.sim(stock("dottyback"), startHour = 12.0)
        val d = sim2.org("dottyback")
        sim2.advance(2.0, behaviorDt = 0.1) { d.spineFlare = 1.0 }
        assertEquals(0.0, d.blueDeepen, 0.0)
    }

    // ------------------------------------------------------------------ claw threat postures (06 §2.14, §2.18)
    @Test
    fun hermitRaisesItsClawAtARival() {
        val sim = TestTank.sim(stock("hermit1", "hermit2"), startHour = 12.0)
        val a = sim.org("hermit1"); val b = sim.org("hermit2")
        val legs = a.limbRig as HermitLegs
        sim.advance(5.0, behaviorDt = 0.1)
        // apart: claws down
        sim.advance(5.0, behaviorDt = 0.1) { b.pos = a.pos + Vec3(30.0, 0.0, 0.0) }
        assertTrue("apart ${legs.threatS}", legs.threatS < 0.1)
        // a rival shell to shell: the claws come up within a second and the pose carries them to the renderer
        var peak = 0.0
        sim.advance(2.0, behaviorDt = 0.1) {
            b.pos = a.pos + a.heading * (0.5 * (a.lengthCm + b.lengthCm) + 0.5); b.vel = Vec3.ZERO
            peak = maxOf(peak, legs.threatS)
        }
        println("06 §2.18 hermit claw threat ${f(peak)}, pose slot ${a.toPose().limbs[HermitLegs.THREAT_SLOT]}")
        assertTrue("claws up: $peak", peak > 0.6)
        assertTrue("pose carries it", a.toPose().limbs[HermitLegs.THREAT_SLOT] > 0.5f)
        // withdrawn into its shell, no display
        a.extension = 0.0
        sim.advance(3.0, behaviorDt = 0.1) { a.extension = 0.0; b.pos = a.pos + a.heading * 3.0 }
        assertTrue("withdrawn ${legs.threatS}", legs.threatS < 0.2)
    }

    @Test
    fun bandedShrimpRaisesItsClawsInABoutAndTheHermitHidesLonger() {
        val sim = TestTank.sim(stock("banded", "hermit1"), seed = 4L, startHour = 23.0)
        val cbs = sim.org("banded"); val hermit = sim.org("hermit1")
        val bouts = sim.agentOf(cbs)!!.brain.utility.behaviors.filterIsInstance<PairBouts>().first()
        val ledge = bouts.anchorOf(sim.agentOf(cbs)!!.ctx, PairMatrix.row("cbs_hermit")!!)
        var clawsInBout = 0.0
        var hidS = 0.0
        var longestHide = 0.0
        var raised = false
        val events = ArrayList<SimEvent>()
        sim.advance(30.0, behaviorDt = 0.1)
        var t = 0.0
        while (t < 3 * 3600.0 && longestHide < 30.0) {
            sim.advance(60.0, behaviorDt = 0.1) {
                if (!raised) { hermit.pos = ledge + Vec3(2.0, 0.0, 1.0); hermit.vel = Vec3.ZERO }
                if (bouts === cbs.brain.current) { clawsInBout = maxOf(clawsInBout, cbs.clawDisplay); raised = true }
                if (raised) {
                    if (hermit.brain.current?.name == "retreat") { hidS += 0.1; longestHide = maxOf(longestHide, hidS) } else if (hidS > 0) hidS = 0.0
                }
            }
            events += sim.latest.events
            t += 60.0
        }
        println("06 §2.14 banded shrimp claw display in a bout ${f(clawsInBout)}, hermit hid ${f(longestHide)} s")
        assertTrue("claws raised in the bout: $clawsInBout", clawsInBout > 0.9)
        assertTrue("hermit stayed in its shell 30-60 s: $longestHide", longestHide >= 29.0 && longestHide <= 61.0)
    }

    // ------------------------------------------------------------------ territory re-anchoring (06 §5.6 #20)
    @Test
    fun territoriesReanchorWithin30MinutesOfARearrange() {
        val sim = TestTank.sim(DefaultStock.fish, seed = 2L, startHour = 10.0)
        sim.advance(60.0, behaviorDt = 0.1)
        val holders = sim.organisms.filter { o -> sim.agentOf(o)!!.brain.utility.behaviors.any { it is ReclaimTerritory } }
        assertTrue("territorial fish: ${holders.map { it.id }}", holders.map { it.id }.containsAll(listOf("dottyback", "lawnmower", "hawk", "clown_f")))
        // idle until the rock is moved
        sim.advance(600.0, behaviorDt = 0.1)
        assertTrue(holders.all { it.territoryAnchoredAt < 0 })
        sim.enqueue(Command.RearrangeRock)
        val seenOut = HashMap<String, Double>()
        sim.advance(1800.0, behaviorDt = 0.1) {
            for (h in holders) if (h.brain.current?.name == "reclaim") {
                val b = sim.agentOf(h)!!.brain.utility.behaviors.filterIsInstance<ReclaimTerritory>().first()
                val home = b.b.anchorPos(sim.agentOf(h)!!.ctx, b.anchor) ?: h.homePos
                seenOut[h.id] = maxOf(seenOut[h.id] ?: 0.0, h.pos.distanceTo(home))
            }
        }
        val reset = sim.territoryResetAt
        println("06 §5.6 #20 re-anchored after (s): ${holders.associate { it.id to f(it.territoryAnchoredAt - reset) }}; loop reach (cm): ${seenOut.mapValues { f(it.value) }}")
        for (h in holders) assertTrue("${h.id} re-anchored within 30 min", h.territoryAnchoredAt >= reset && h.territoryAnchoredAt - reset <= 1800.0)
        // visibly: each went out round its patch, not just a flag flip
        assertTrue("loops swum: $seenOut", holders.count { (seenOut[it.id] ?: 0.0) > 4.0 } >= holders.size - 1)
    }

    // ------------------------------------------------------------------ GSP overgrowth (03 D, sweepers #162)
    private fun mound() = RockData(com.reeflive.core.geom.AnalyticSdf(listOf(com.reeflive.core.geom.Ellipsoid(Vec3(60.0, 3.0, 16.0), Vec3(40.0, 18.0, 14.0)))), com.reeflive.core.sim.AnchorSet(emptyList()))

    private val torchCatalog by lazy { com.reeflive.core.sim.SpeciesCatalog.fromClasspath(listOf("euphyllia_glabrescens")) }

    private fun coral(id: String, speciesId: String, rock: RockData, near: Vec3): Coral {
        val p = rock.sdf.project(near, 0.2)
        val sp = if (speciesId.startsWith("euphyllia")) torchCatalog[speciesId] else TestTank.catalog[speciesId]
        return Coral(id, sp, id, "", p, rock.sdf.gradient(p), 0.0)
    }

    @Test
    fun gspOvergrowsSoftNeighboursButStingersHoldItOff() {
        val rock = mound()
        val ws = WaterState()
        // the same mat front reaching a mushroom, a zoa colony and a Kenya tree at the same distance
        fun overAfter(victimSpecies: String): Double {
            val gsp = coral("gsp", "briareum_sp", rock, Vec3(40.0, 22.0, 16.0))
            val v = coral("v", victimSpecies, rock, Vec3(54.0, 23.0, 16.0))
            val rng = Rng(3)
            repeat(160) {
                repeat(24) { CoralGrowth.step(gsp, WaterState(no3 = 12.0, po4 = 0.06), rock, rng, 60.0, 0) { "x" } }
                CoralGrowth.compete(listOf(gsp, v), ws, 0.0)
            }
            return v.overgrown
        }
        val shroom = overAfter("discosoma_sp")
        val zoa = overAfter("zoanthus_sp")
        val kenya = overAfter("capnella_sp")
        println("03 D GSP overgrowth after 160 d: mushroom ${f(shroom)}, zoa ${f(zoa)}, Kenya ${f(kenya)}")
        assertTrue("mushroom smothered: $shroom", shroom > 0.2)
        assertTrue("zoas sting it back: $zoa vs $shroom", zoa < 0.4 * shroom)
        assertTrue("Kenya terpenes slow it: $kenya vs $shroom", kenya < 0.8 * shroom && kenya > zoa)
        // a Euphyllia with full sweepers stops it almost dead (StonyCorals stings, #162)
        val gsp = coral("gsp", "briareum_sp", rock, Vec3(40.0, 22.0, 16.0))
        val torch = coral("torch", "euphyllia_glabrescens", rock, Vec3(54.0, 23.0, 16.0)).also { it.sweepers = 1.0 }
        assertTrue("sweepers hold it: ${CoralGrowth.holdBack(torch, gsp, 0.0)}", CoralGrowth.holdBack(torch, gsp, 0.0) > 0.6)
        assertEquals(0.0, CoralGrowth.holdBack(coral("m", "discosoma_sp", rock, Vec3(54.0, 23.0, 16.0)), gsp, 0.0), 0.0)
    }

    @Test
    fun anOvergrownColonyIsReportedOnce() {
        val sim = TestTank.sim(DefaultStock.all, startHour = 12.0)
        sim.advanceAbstract(3600.0)
        // the GSP colony with a neighbour its mat can reach along the rock, and that neighbour
        val pairs = sim.corals.filter { it.kind == com.reeflive.core.sim.CoralKind.GSP && it.patch != null }.flatMap { g ->
            sim.corals.filter { it.kind != com.reeflive.core.sim.CoralKind.GSP && it.kind != com.reeflive.core.sim.CoralKind.MACRO && CoralGrowth.holdBack(it, g, 0.0) == 0.0 }
                .map { v -> Triple(g, v, g.patch!!.distTo(v.pos, 3.0 + CoralGrowth.extent(v) * 0.5)) }
        }.filter { it.third.isFinite() && it.third < it.first.patch!!.maxReach }
        println("03 D reachable neighbours: ${pairs.map { "${it.first.id}->${it.second.id} ${f(it.third)} cm" }}")
        val (gsp, victim, _) = pairs.minByOrNull { it.third }!!
        // the mat grown out as far as its rock goes, over the neighbour (a newcomer: no coexistence baseline)
        val events = ArrayList<SimEvent>()
        repeat(3) {
            gsp.radiusCm = gsp.patch!!.maxReach + 1.0
            victim.baseline = 0.0 to 0.0
            sim.advanceAbstract(3600.0); events += sim.eventLog.toList()
        }
        val over = events.filterIsInstance<SimEvent.CoralOvergrown>().distinct()
        println("03 D overgrowth event: $over (victim ${victim.id} overgrown ${f(victim.overgrown)})")
        assertTrue("smothered: ${victim.overgrown}", victim.overgrown >= CoralGrowth.OVERGROWN_NOTE)
        assertEquals("reported once: $over", 1, over.count { it.id == victim.id })
        assertTrue(com.reeflive.core.live.LivePeek.describe(over.first { it.id == victim.id })!!.contains("growing over"))
    }
}
