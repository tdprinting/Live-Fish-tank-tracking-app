package com.reeflive.core.sim

import com.reeflive.core.ai.GobyBrain
import com.reeflive.core.ai.PairBouts
import com.reeflive.core.ai.PairMatrix
import com.reeflive.core.geom.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The five open gaps of research 06 §6 as resolved by research 07 (docs/research/07_gap_sources.md). */
class ResearchGapsTest {

    private fun Simulation.org(kind: BrainKind) = organisms.first { it.brainKind == kind }
    private fun Simulation.pairBouts(o: Organism) = agentOf(o)!!.brain.utility.behaviors.filterIsInstance<PairBouts>().first()

    /** Gap 1: lawnmower chases ~90 % of intruding blennies of its size or smaller, ~20 % of damsel-like grazers; no larger blenny. */
    @Test
    fun gap1_lawnmowerChaseProportions() {
        val sim = TestTank.sim(DefaultStock.all, seed = 1L, startHour = 11.0)
        sim.advance(1.0)
        val lmb = sim.org(BrainKind.LAWNMOWER_BLENNY)
        val ts = sim.org(BrainKind.PEEKER_BLENNY)
        val ctx = sim.agentOf(lmb)!!.ctx
        val pb = sim.pairBouts(lmb)
        val row = PairMatrix.row("lmb_tailspot")!!
        assertEquals("sourced intrusion probability", 0.9, row.pEvent, 1e-9)
        assertEquals("damsel analogue (tang)", 0.2, PairMatrix.row("lmb_tang")!!.pEvent, 1e-9)
        lmb.hunger = 0.6
        assertTrue("a smaller blenny is chased", pb.probability(ctx, row, ts) > 0.0)
        val small = ts.lengthCm
        ts.lengthCm = 1.3 * lmb.lengthCm
        assertEquals("a larger blenny is not", 0.0, pb.probability(ctx, row, ts), 0.0)
        ts.lengthCm = small
        // the dottyback rows carry no sourced rate: their evidence says so
        assertTrue(PairMatrix.rows.filter { it.actor == "DOTTYBACK" }.all { "[I]" in it.evidence })
    }

    /** Gap 2: cruise capped at the family field speed (Fulton07); burst BL/s falls once a fish outgrows adult size. */
    @Test
    fun gap2_swimSpeedsFollowFamilyData() {
        val cat = TestTank.catalog
        val fish = DefaultStock.all.map { cat[it.speciesId] }.filter { it.kind == "FISH" }.distinctBy { it.id }
        for (sp in fish) {
            val bl = SwimSpeed.cruise(sp, sp.typicalAdultCm) / sp.typicalAdultCm
            assertTrue("${sp.id} cruise $bl BL/s", bl in 0.2..2.0)
            assertTrue("${sp.id} cruise under its family field speed", SwimSpeed.cruise(sp, sp.typicalAdultCm) <= SwimSpeed.fieldCeiling(sp.locomotion.mode))
        }
        val tang = cat["paracanthurus_hepatus"]
        assertTrue("a giant tang cruises no faster than labriform field speed", SwimSpeed.cruise(tang, 60.0) <= SwimSpeed.LABRIFORM_FIELD_CM_S)
        val atAdult = SwimSpeed.burst(tang, tang.typicalAdultCm) / tang.typicalAdultCm
        val big = SwimSpeed.burst(tang, 1.5 * tang.typicalAdultCm) / (1.5 * tang.typicalAdultCm)
        assertTrue("relative burst falls with size ($atAdult → $big BL/s)", big < atAdult)
        assertEquals("unchanged up to adult size", tang.locomotion.burstBL, SwimSpeed.burst(tang, 10.0) / 10.0, 1e-9)
    }

    /** Gap 3: an adult engineer goby patrols outside on ~5 % of nights; a juvenile never does. */
    @Test
    fun gap3_adultGobyRarelyPatrols() {
        val sim = TestTank.sim(DefaultStock.all, seed = 2L, startHour = 21.0)
        sim.advance(1.0)
        val goby = sim.org(BrainKind.ENGINEER_GOBY)
        val agent = sim.agentOf(goby)!!
        val brain = agent.brain as GobyBrain
        fun nights(len: Double): Int {
            goby.lengthCm = len
            var n = 0
            for (i in 1..400) { agent.ctx.p.lightsOffAt = i * 86400.0; brain.rollNight(agent.ctx); if (brain.patrolTonight) n++ }
            return n
        }
        val adult = nights(GobyBrain.ADULT_CM + 10)
        assertTrue("adult patrol nights $adult / 400 (≈ 5 %)", adult in 6..40)
        assertEquals("juvenile never patrols", 0, nights(GobyBrain.ADULT_CM - 5))
    }

    /** Gap 4: no cross-species shrimp kills by default; conspecific kills only after a molt, and only past a pair. */
    @Test
    fun gap4_shrimpKillsAreConspecificAndMoltBound() {
        fun kills(n: Int, molted: Boolean, cross: Boolean = false): Int {
            var hits = 0
            for (seed in 1L..150L) {
                val stock = List(n) { TestTank.seed("skunk").copy(id = "skunk$it") } + (if (cross) listOf(TestTank.seed("banded")) else emptyList())
                val s = TestTank.sim(stock, seed = seed, startHour = 1.0 + 59.5 / 60)
                val shrimp = s.organisms.filter { it.brainKind == BrainKind.CLEANER_SHRIMP }
                val victim = shrimp.first()
                if (molted) victim.lastMoltAt = s.simTimeSec
                for ((i, o) in s.organisms.withIndex()) if (o !== victim && !o.isFish) o.pos = victim.pos + Vec3(3.0 + i, 0.0, 0.0)
                s.advance(40.0, behaviorDt = 0.5)
                if (s.eventLog.any { it is SimEvent.NearMiss || it is SimEvent.Eaten }) hits++
            }
            return hits
        }
        assertEquals("a molted skunk next to a banded shrimp: off by default", 0, kills(1, molted = true, cross = true))
        assertEquals("a pair is stable (S:Wong11)", 0, kills(2, molted = true))
        assertEquals("no molt, no kill", 0, kills(3, molted = false))
        val trio = kills(3, molted = true)
        assertTrue("a third skunk just molted: $trio / 150 nights (row p 0.06 per roller)", trio in 4..40)
    }

    /** Gap 5: a hungry Sally Lightfoot takes at most one snail a night, never a fish ≥ 0.8 of its span, never when fed. */
    @Test
    fun gap5_sallyLightfootIsAnOpportunist() {
        fun run(hunger: Double, crabCm: Double, gate: Boolean = true): Pair<Int, Int> {
            var snails = 0; var fish = 0
            for (seed in 1L..60L) {
                val stock = listOf(TestTank.seed("crab")) + List(8) { TestTank.seed("astrea1").copy(id = "astrea$it") } + listOf(TestTank.seed("tailspot"))
                val s = TestTank.sim(stock, seed = seed, startHour = 1.0 + 59.5 / 60)
                s.predationEnabled = true
                // the snail roll is unsourced: it runs only behind the evidence gate (realism_gaps X7)
                s.unsourcedPredation = gate
                val crab = s.org(BrainKind.SALLY_CRAB)
                crab.lengthCm = crabCm
                for ((i, o) in s.organisms.withIndex()) if (o !== crab) o.pos = crab.pos + Vec3(4.0 + i, 0.0, 2.0)
                val kinds = s.organisms.associate { it.id to it.brainKind }
                s.advance(40.0, behaviorDt = 0.5) { crab.hunger = hunger }
                val eaten = s.eventLog.filterIsInstance<SimEvent.Eaten>().filter { it.predatorId == crab.id }
                assertTrue("one prey per night at most", eaten.size <= 1)
                snails += eaten.count { kinds[it.preyId] == BrainKind.ASTREA }
                fish += eaten.count { kinds[it.preyId]?.isFish == true }
            }
            return snails to fish
        }
        val fed = run(0.2, 6.5)
        assertEquals("a fed crab never hunts", 0 to 0, fed)
        val hungry = run(0.95, 6.5)
        assertTrue("hungry crab snails ${hungry.first} / 60 nights (row p 0.05)", hungry.first in 1..12)
        // tailspot 5.5 cm is not under 0.8 × 6.5 cm: size-gated out
        assertEquals("no fish at or above 0.8 × leg span", 0, hungry.second)
        assertEquals("evidence gate off (the default): no snail kills either", 0 to 0, run(0.95, 6.5, gate = false))
    }
}
