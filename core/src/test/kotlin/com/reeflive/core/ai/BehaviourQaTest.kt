package com.reeflive.core.ai

import com.reeflive.core.sim.BrainKind
import com.reeflive.core.sim.Counters
import com.reeflive.core.sim.DefaultStock
import com.reeflive.core.sim.FoodKind
import com.reeflive.core.sim.Organism
import com.reeflive.core.sim.Simulation
import com.reeflive.core.sim.TankConfig
import com.reeflive.core.sim.TestTank
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The numeric behaviour QA of 2026-10-28 (tang dash and hide, speed scaling, label churn, feeding reach, blenny sleep,
 * hermit roaming), as regression checks.
 */
class BehaviourQaTest {
    private fun Simulation.named(n: String) = organisms.first { it.nickname == n || it.id == n }
    private fun Simulation.tang() = organisms.first { it.brainKind == BrainKind.TANG }
    private fun Simulation.brainOf(o: Organism) = agentOf(o)!!.brain
    private inline fun <reified T : Behavior> Simulation.behaviour(o: Organism): T = brainOf(o).utility.behaviors.filterIsInstance<T>().single()

    /** Steps the running tank the way the web app does (20 Hz wall ticks) at [speed] for [simS] sim seconds. */
    private fun Simulation.run(speed: Double, simS: Double, each: () -> Unit = {}) {
        this.speed = speed
        val end = simTimeSec + simS
        while (simTimeSec < end) { stepWall(0.05); each() }
    }

    // ------------------------------------------------------------------ 1. the dash ends
    @Test
    fun tangDashIsOneRunAndIsNotEnteredAgain() {
        for (seed in 1L..3L) {
            val sim = TestTank.sim(DefaultStock.all, seed = seed, startHour = 12.0)
            val tang = sim.tang()
            val zoom = sim.behaviour<TangZoom>(tang)
            sim.advance(20.0)
            tang.hunger = 0.2; tang.stress = 0.0
            zoom.nextAt = sim.simTimeSec
            var start = -1.0; var end = -1.0; var again = false
            sim.advance(400.0) {
                val cur = sim.brainOf(tang).utility.current === zoom
                if (cur && start < 0) start = sim.simTimeSec
                if (!cur && start >= 0 && end < 0) end = sim.simTimeSec
                if (cur && end >= 0) again = true
            }
            assertTrue("seed $seed: the dash started", start >= 0)
            assertTrue("seed $seed: the dash ended", end >= 0)
            assertTrue("seed $seed: dash lasted ${end - start} s", end - start <= 2 * TangZoom.DASH_S + 1.0)
            assertTrue("seed $seed: re-entered right after it ended", !again)
        }
    }

    // ------------------------------------------------------------------ 2. hides are short and scaled by stress
    @Test
    fun hideHoldIsShortAndScalesWithStress() {
        val calm = TangHide.holdFor(0.5, 0.5, 0.0)
        val nervous = TangHide.holdFor(0.5, 0.5, 0.8)
        assertTrue("calm hold $calm s", calm in 20.0..120.0)
        assertTrue("stressed hold $nervous > calm $calm", nervous > calm * 1.5)
        assertTrue("never over the cap", TangHide.holdFor(1.0, 1.5, 1.0) <= TangHide.HIDE_MAX_S)
    }

    @Test
    fun aScaredTangComesOutWithinMinutesAtAnySpeed() {
        for (speed in listOf(1.0, 240.0)) {
            val sim = TestTank.sim(DefaultStock.all, seed = 1L, startHour = 12.0)
            val tang = sim.tang(); val b = sim.brainOf(tang) as TangBrain
            val hide = sim.behaviour<TangHide>(tang)
            sim.advance(20.0)
            b.onAttacked(sim.agentOf(tang)!!.ctx, tang.pos + com.reeflive.core.geom.Vec3(5.0, 0.0, 0.0), 0.6, null)
            var hiding = 0.0; var longest = 0.0; var run = 0.0
            val dt = { 0.05 * sim.speed }
            sim.run(speed, 20 * 60.0) {
                if (sim.brainOf(tang).utility.current === hide) { hiding += dt(); run += dt(); longest = maxOf(longest, run) } else run = 0.0
            }
            assertTrue("${speed}x: it hid", hiding > 0)
            // one scare: a hide of at most the cap plus the swim in and out, not the ~10 min it took for the fear to fade
            assertTrue("${speed}x: longest hide ${longest.toInt()} s", longest <= TangHide.HIDE_MAX_S + 120.0)
            assertTrue("${speed}x: hid ${hiding.toInt()} s of 20 min", hiding < 8 * 60.0)
        }
    }

    @Test
    fun theTangLeavesItsHideAtLightsOutToSleep() {
        val sim = TestTank.sim(DefaultStock.all, seed = 1L, startHour = 12.0)
        val tang = sim.tang(); val b = sim.brainOf(tang) as TangBrain
        val hide = sim.behaviour<TangHide>(tang)
        sim.run(240.0, 3600.0 * 6)   // to 18:00
        var hidingAtNight = 0; var night = 0
        sim.run(240.0, 3600.0 * 6) {
            // a fresh scare every half hour keeps it nervous through the evening
            if (sim.tickCount % 150L == 0L) b.onAttacked(sim.agentOf(tang)!!.ctx, tang.pos, 0.6, null)
            if (sim.perception.night && sim.minuteOfDay > 21 * 60) { night++; if (sim.brainOf(tang).utility.current === hide) hidingAtNight++ }
        }
        assertTrue("night ticks $night", night > 100)
        assertTrue("hiding $hidingAtNight of $night night ticks", hidingAtNight < 0.05 * night)
    }

    // ------------------------------------------------------------------ 3-4. speed scaling
    @Test
    fun behaviourDurationsRunOnSimTime() {
        val sim = TestTank.sim(DefaultStock.all, seed = 1L, startHour = 12.0)
        val o = sim.tang()
        val ctx = sim.agentOf(o)!!.ctx
        class Hold : Behavior("hold", "Holding") {
            var endedAt = -1.0
            override fun score(c: Ctx) = if (endedAt < 0) 0.5 else 0.0
            override fun update(c: Ctx, out: SteerIntent) { if (elapsed > 60.0 && endedAt < 0) { endedAt = c.t; finished = true } }
        }
        for (warp in listOf(1.0, 6.0, 24.0)) {
            val h = Hold()
            val brain = UtilityBrain(listOf(h))
            val t0 = ctx.p.t
            ctx.dt = 0.5; ctx.simDt = 0.5 * warp
            while (h.endedAt < 0 && ctx.p.t - t0 < 600) { brain.tick(ctx, SteerIntent()); ctx.p.t += ctx.simDt }
            val took = h.endedAt - t0
            assertTrue("warp $warp: a 60 s hold took $took sim s", took in 60.0..60.0 + ctx.simDt + 1e-6)
            ctx.p.t = t0
        }
    }

    @Test
    fun hungerIsTheSameAtNormalAndFastSpeed() {
        // 10x steps the behaviour at 0.5 s for 0.5 s of sim time; 240x steps it at 0.5 s for 12 s: the same six hours,
        // with the 09:30 flake feed, must leave every animal about as hungry
        fun meanHunger(speed: Double): Map<String, Double> {
            val sim = TestTank.sim(DefaultStock.all, seed = 1L, startHour = 8.0, config = TankConfig(seed = 1L))
            val sum = HashMap<String, Double>(); var n = 0
            // the fish and the grazing snails and stars (at 10x the 0.5 s step lets the slow scavengers reach food they
            // rarely reach at 1x, so they are no reference there; the harness compares those against real 1x)
            val watched = sim.organisms.filter { it.isFish || it.brainKind == BrainKind.ASTREA || it.brainKind == BrainKind.ASTERINA }
            sim.run(speed, 6 * 3600.0) {
                if (sim.tickCount % (if (speed > 100) 1L else 24L) == 0L) { n++; for (o in watched) if (o.alive) sum[o.id] = (sum[o.id] ?: 0.0) + o.hunger }
            }
            return sum.mapValues { it.value / n }
        }
        val slow = meanHunger(10.0)
        val fast = meanHunger(240.0)
        val bad = slow.keys.filter { abs((fast[it] ?: 0.0) - slow[it]!!) > HUNGER_TOL }
            .map { "$it ${"%.2f".format(slow[it])} vs ${"%.2f".format(fast[it])}" }
        assertTrue("hunger differs at 240x: $bad", bad.size <= 1)
        assertTrue("fed at 240x: max mean hunger ${fast.values.max()}", fast.values.max() < 0.7)
    }

    // ------------------------------------------------------------------ 5. label dwell
    @Test
    fun shownLabelHoldsThroughQuickFlips() {
        val sim = TestTank.sim(DefaultStock.all, seed = 1L, startHour = 12.0)
        val ctx = sim.agentOf(sim.tang())!!.ctx
        class Flip(name: String, val phase: Int) : Behavior(name, name) {
            override val noisy: Boolean get() = false
            override fun score(c: Ctx) = if (((c.t / 1.0).toInt() % 2) == phase) 0.8 else 0.2
            override fun update(c: Ctx, out: SteerIntent) {}
        }
        val brain = UtilityBrain(listOf(Flip("A", 0), Flip("B", 1)))
        ctx.dt = 0.05; ctx.simDt = 0.05
        val t0 = ctx.p.t
        var rawChanges = 0; var shownChanges = 0; var lastRaw = ""; var lastShown = ""
        repeat(1200) {   // 60 s of A/B flipping every second
            brain.tick(ctx, SteerIntent()); ctx.p.t += 0.05
            if (brain.currentLabel != lastRaw) { rawChanges++; lastRaw = brain.currentLabel }
            if (brain.shownLabel != lastShown) { shownChanges++; lastShown = brain.shownLabel }
        }
        ctx.p.t = t0
        assertTrue("raw label flipped $rawChanges times", rawChanges > 40)
        assertTrue("shown label changed $shownChanges times", shownChanges <= 2)
    }

    @Test
    fun cardAndCaptionShowTheSameHeldLabel() {
        val sim = TestTank.sim(DefaultStock.all, seed = 2L, startHour = 11.0)
        var mismatch = 0
        val changes = HashMap<String, Int>(); val last = HashMap<String, String>()
        sim.run(1.0, 600.0) {
            for (st in sim.latest.stats) {
                // the caption is "<name> · <label>" or "<name> <verb> <target>": never a different behaviour than the card
                if (st.caption.contains(" · ") && !st.caption.endsWith(st.behavior.replaceFirstChar { it.lowercase() })) mismatch++
                if (last[st.id] != st.behavior) { changes[st.id] = (changes[st.id] ?: 0) + 1; last[st.id] = st.behavior }
            }
        }
        assertEquals("captions naming another behaviour than the card", 0, mismatch)
        val worst = changes.maxBy { it.value }
        // 10 min: no animal's card changes more than ~3.5 times a minute (the goby's sift phases did 31 a minute)
        assertTrue("card label changes ${worst.key} ${worst.value} in 10 min", worst.value <= 35)
    }

    // ------------------------------------------------------------------ 6. every fish can get to food
    @Test
    fun everyFishSpeciesEatsAtAFeed() {
        val sim = TestTank.sim(DefaultStock.all, seed = 1L, startHour = 11.0, config = TankConfig(seed = 1L, autoFeed = emptyList()))
        sim.advance(30.0)
        val fish = sim.organisms.filter { it.isFish && it.acceptsPrepared }
        for (o in fish) o.hunger = 0.6
        val bites0 = fish.associateWith { it.counters.totalOf(Counters.K.FEED) }
        // the keeper target-feeds: a pinch of the food each fish eats let go just above where it lives
        for (o in fish) {
            val kind = listOf(FoodKind.MYSIS, FoodKind.FLAKE, FoodKind.PELLET).maxBy { o.species.dietMatch(it) }
            sim.feed(kind, 0.15, sim.targetRelease(kind, o.homePos.takeIf { it != com.reeflive.core.geom.Vec3.ZERO } ?: o.pos))
        }
        sim.advance(8 * 60.0)
        val none = fish.filter { it.counters.totalOf(Counters.K.FEED) - bites0[it]!! < 1 && it.hunger > 0.5 }
        assertTrue("no bite of fed food: ${none.map { "${it.nickname} (${it.brainKind})" }}", none.isEmpty())
    }

    // ------------------------------------------------------------------ 7. peeker blennies sleep
    @Test
    fun peekerBlenniesSleepInTheirHoleAtNight() {
        val sim = TestTank.sim(DefaultStock.all, seed = 1L, startHour = 23.0)
        val blennies = sim.organisms.filter { it.brainKind == BrainKind.PEEKER_BLENNY }
        assertTrue(blennies.isNotEmpty())
        val asleep = HashMap<String, Int>(); var n = 0
        sim.run(1.0, 600.0) {
            n++
            for (o in blennies) if (o.sleeping && o.displayLabel.startsWith("Asleep")) asleep[o.id] = (asleep[o.id] ?: 0) + 1
        }
        for (o in blennies) {
            val share = (asleep[o.id] ?: 0).toDouble() / n
            assertTrue("${o.nickname} asleep ${(share * 100).toInt()} % of the night", share > 0.8)
            val b = sim.brainOf(o) as PeekerBlennyBrain
            if (b.hole != null) assertEquals("${o.nickname} in its hole", 3, o.posture)
        }
    }

    // ------------------------------------------------------------------ 8. hermits roam
    @Test
    fun hermitsForageAcrossTheRockAndRest() {
        val sim = TestTank.sim(DefaultStock.all, seed = 1L, startHour = 12.0)
        val hermits = sim.organisms.filter { it.brainKind == BrainKind.HERMIT }
        val xs = hermits.associateWith { ArrayList<Double>() }; val zs = hermits.associateWith { ArrayList<Double>() }
        val top = HashMap<Organism, Int>(); val still = HashMap<Organism, Int>(); var n = 0
        val wl = sim.rock.waterLevelCm
        sim.advance(3600.0, behaviorDt = 0.1) {
            n++
            for (o in hermits) {
                val p = if (o.seatOn) o.seatPos else o.pos
                xs[o]!!.add(p.x); zs[o]!!.add(p.z)
                if (p.y > wl - 8.0) top[o] = (top[o] ?: 0) + 1
                if (o.vel.length() < 0.3) still[o] = (still[o] ?: 0) + 1
            }
        }
        fun spread(v: List<Double>) = v.sorted().let { it[(it.size * 0.95).toInt()] - it[(it.size * 0.05).toInt()] }
        val roamers = hermits.count { maxOf(spread(xs[it]!!), spread(zs[it]!!)) >= 20.0 }
        assertTrue("hermits ranging 20+ cm in an hour: $roamers of ${hermits.size}", roamers >= hermits.size - 1)
        for (o in hermits) {
            assertTrue("${o.nickname} at the water line ${top[o] ?: 0} of $n", (top[o] ?: 0) < 0.2 * n)
            assertTrue("${o.nickname} stops to pick or rest ${(still[o] ?: 0)} of $n", (still[o] ?: 0) > 0.15 * n)
        }
    }

    companion object {
        /** Mean hunger over six hours, 10x vs 240x (absolute) [L]. */
        const val HUNGER_TOL = 0.15
    }
}
