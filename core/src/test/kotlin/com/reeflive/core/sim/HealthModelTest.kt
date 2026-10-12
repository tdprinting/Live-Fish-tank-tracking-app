package com.reeflive.core.sim

import org.junit.Assert.assertTrue
import org.junit.Test

class HealthModelTest {
    /** SIMULATION §8.3: 30 °C for 72 h, immunity 0.8 → ich stage > 0.15 within 7 days in > 90 % of 50 seeds. */
    @Test
    fun tangIchAtHighTemp() {
        var hits = 0
        for (seed in 1L..50L) {
            val sim = TestTank.sim(listOf(TestTank.seed("tang")), seed = seed)
            val tang = sim.organisms.first()
            tang.immunity = 0.8
            sim.enqueue(Command.SetParam("temp", 30.0))
            var maxStage = 0.0
            for (h in 0 until 7 * 24) {
                if (h == 72) sim.enqueue(Command.SetParam("temp", 25.5))
                sim.advanceAbstract(3600.0)
                if (tang.disease == Disease.ICH) maxStage = maxOf(maxStage, tang.diseaseStage)
            }
            if (maxStage > 0.15) hits++
        }
        assertTrue("ich > 0.15 in $hits/50 seeds", hits > 45)
    }

    /** SIMULATION §8.4: ideal water for 90 days with scheduled feeding → all alive, health > 0.9. */
    @Test
    fun healthyTankNoDeaths() {
        val sim = TestTank.sim(DefaultStock.all)
        sim.organisms.filter { it.brainKind == BrainKind.WRASSE }.forEach { it.acceptsPrepared = true }
        sim.advanceAbstract(90 * 86400.0)
        val dead = sim.eventLog.filterIsInstance<SimEvent.Died>()
        assertTrue("deaths: $dead", dead.isEmpty())
        for (o in sim.organisms) assertTrue("${o.nickname} health ${o.health} hunger ${o.hunger} stress ${o.stress} disease ${o.disease}", o.alive && o.health > 0.9)
        val s = sim.water.s
        assertTrue("water drifted: $s", s.nh3Free < 0.01 && s.no2 < 0.1 && s.tempC in 24.5..26.5 && s.sg in 1.023..1.027)
    }

    /** SIMULATION §8.5: wrasse refusing prepared food with no clients starves between day 20 and 60. */
    @Test
    fun wrasseStarves() {
        val sim = TestTank.sim(listOf(TestTank.seed("wrasse")))
        val w = sim.organisms.first()
        w.acceptsPrepared = false
        var day = 0
        while (w.alive && day < 90) { sim.advanceAbstract(86400.0); day++ }
        assertTrue("wrasse died on day $day (alive=${w.alive}, cause=${w.deathCause})", !w.alive && day in 20..60)
    }

    /** Heater stuck on in a hot room: fish get heat stress, then gasp (low DO) before dying. */
    @Test
    fun heaterStuckOnStressesFish() {
        val sim = Presets.create(Presets.heaterFailureSummer, TestTank.catalog, TestTank.rock)
        sim.advanceAbstract(36 * 3600.0)
        assertTrue("temp ${sim.water.s.tempC}", sim.water.s.tempC > 29.0)
        val tang = sim.organisms.first { it.brainKind == BrainKind.TANG }
        assertTrue("tang stress ${tang.stress}", tang.stress > 0.2)
    }

    /** Copper is the global invertebrate kill switch (04 §15). */
    @Test
    fun copperKillsInverts() {
        val sim = TestTank.sim(listOf(TestTank.seed("tang"), TestTank.seed("skunk"), TestTank.seed("hermit1")))
        sim.enqueue(Command.SetParam("copper", 0.25))
        sim.advanceAbstract(5 * 86400.0)
        val inverts = sim.organisms.filter { !it.isFish }
        assertTrue("inverts alive: ${inverts.filter { it.alive }}", inverts.none { it.alive })
        assertTrue("tang should survive therapeutic copper", sim.organisms.first { it.isFish }.alive)
    }
}
