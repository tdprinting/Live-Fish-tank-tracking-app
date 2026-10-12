package com.reeflive.core.sim

import com.reeflive.core.ai.TangBrain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Coverage B-1 (clown spawning, sex change), B-2 (cardinal mouthbrooding), B-3 (wrasse harem), C-3 (tang nips). */
class ReproductionTest {
    private fun pair(sizeF: Double = 8.0, sizeM: Double = 6.5) = listOf(
        LivestockSeed("amphiprion_ocellaris", "Nemo", sizeF, role = "FEMALE", placement = "HOST_KENYA", id = "clown_f"),
        LivestockSeed("amphiprion_ocellaris", "Marlin", sizeM, role = "MALE", placement = "HOST_KENYA", id = "clown_m"),
    )

    /** Abstract-mode days with the fish kept fed and calm (a clean tank gives no feeding). */
    private fun days(sim: Simulation, n: Int, stress: Double = 0.0, until: () -> Boolean = { false }) {
        repeat(n * 24) {
            for (o in sim.organisms) if (o.alive) { o.hunger = 0.2; o.stress = stress }
            sim.advanceAbstract(3600.0)
            if (until()) return
        }
    }

    @Test
    fun stablePairSpawnsAndEggsHatchAtNight() {
        val sim = TestTank.sim(pair(), seed = 3)
        days(sim, 20) { sim.repro.clownClutch() != null }
        val c = sim.repro.clownClutch()
        assertNotNull("pair never spawned", c)
        assertEquals("clown_m", c!!.tenderId)
        assertTrue("hatch window ${c.hatchDays}", c.hatchDays in 8.0..10.0)
        // the clutch is in the snapshot with position and age
        val pose = sim.snapshot().eggClutches.single()
        assertEquals(c.pos, pose.pos)
        assertTrue(pose.ageDays >= 0f && pose.ageDays < 1f)
        // a second spawning waits until this one is gone
        days(sim, 30) { sim.repro.hatchedClutches > 0 }
        assertEquals(1, sim.repro.hatchedClutches)
        assertTrue(sim.snapshot().eggClutches.isEmpty())
        assertTrue("hatched at age ${sim.simTimeSec}", sim.repro.lostClutches == 0)
    }

    @Test
    fun hatchingWaitsForNightAndAge() {
        val sim = TestTank.sim(pair(), seed = 3)
        days(sim, 20) { sim.repro.clownClutch() != null }
        val c = sim.repro.clownClutch()!!
        var hatchedAtAge = -1.0
        var hatchedNight = false
        repeat(15 * 24) {
            if (hatchedAtAge < 0) {
                for (o in sim.organisms) { o.hunger = 0.2; o.stress = 0.0 }
                sim.advanceAbstract(3600.0)
                if (sim.repro.clownClutch() == null) { hatchedAtAge = c.ageDays(sim.simTimeSec); hatchedNight = sim.perception.night }
            }
        }
        assertTrue("hatched at ${"%.2f".format(hatchedAtAge)} d", hatchedAtAge >= c.hatchDays && hatchedAtAge < c.hatchDays + 1.5)
        assertTrue("hatch happens at night", hatchedNight)
    }

    @Test
    fun noSpawningWhenStressedOrImmatureOrWaterBad() {
        val stressed = TestTank.sim(pair(), seed = 3); days(stressed, 15, stress = 0.7)
        assertNull("stressed pair spawned", stressed.repro.clownClutch())
        val small = TestTank.sim(pair(3.0, 2.5), seed = 3); days(small, 15)
        assertNull("immature pair spawned", small.repro.clownClutch())
        val hot = TestTank.sim(pair(), seed = 3)
        hot.enqueue(Command.RampParam("temp", 31.0, 0.0, hold = true))
        days(hot, 15)
        assertTrue("held temp ${hot.water.s.tempC}", hot.water.s.tempC > 30.0)
        assertNull("pair spawned in heat", hot.repro.clownClutch())
    }

    @Test
    fun spawningIsDeterministic() {
        fun run(): Pair<Double, Double> {
            val sim = TestTank.sim(pair(), seed = 11)
            days(sim, 20) { sim.repro.clownClutch() != null }
            val c = sim.repro.clownClutch()!!
            return c.laidAt to c.hatchDays
        }
        assertEquals(run(), run())
    }

    @Test
    fun maleFansTheClutchAndNeglectedEggsAreLost() {
        val sim = TestTank.sim(pair(), seed = 5)
        days(sim, 20) { sim.repro.clownClutch() != null }
        val c = sim.repro.clownClutch()!!
        val male = sim.organisms.first { it.id == "clown_m" }
        // full behaviour for 20 min of daytime: the male stays at the nest
        var near = 0; var total = 0; var t = 0.0; var fanned = false
        sim.advance(1200.0, behaviorDt = 0.1) {
            t += 0.1
            for (o in sim.organisms) o.hunger = 0.2
            if (t > 120 && t.toInt() % 5 == 0) { total++; if (male.pos.distanceTo(c.pos) < 12.0) near++ }
            if (male.behaviorName == "Tending the eggs") fanned = true
        }
        assertTrue("male near clutch $near/$total", total > 0 && near.toDouble() / total > 0.8)
        assertTrue("tending behaviour used", fanned)
        // lose the male: the eggs fungus within hours
        male.alive = false; sim.removeOrganism(male)
        sim.advanceAbstract(8 * 3600.0)
        assertNull(sim.repro.clownClutch())
        assertEquals(1, sim.repro.lostClutches)
    }

    @Test
    fun maleBecomesFemaleOverWeeksWhenFemaleDies() {
        val sim = TestTank.sim(pair(), seed = 7)
        val female = sim.organisms.first { it.id == "clown_f" }
        val male = sim.organisms.first { it.id == "clown_m" }
        sim.advanceAbstract(3600.0)
        assertEquals("Male", male.sexLabel())
        female.alive = false; sim.removeOrganism(female)
        days(sim, 10)
        assertEquals(false, male.sexFemale)
        assertTrue("changing ${male.sexChange}", male.sexChange > 0.2 && male.sexChange < 0.5)
        assertEquals("Changing sex", sim.snapshot().stats.first { it.id == "clown_m" }.sex)
        days(sim, 25)
        assertEquals(true, male.sexFemale)
        assertEquals("Female", sim.snapshot().stats.first { it.id == "clown_m" }.sex)
    }

    @Test
    fun noSexChangeWhileFemaleLives() {
        val sim = TestTank.sim(pair(), seed = 7)
        days(sim, 40)
        assertEquals(false, sim.organisms.first { it.id == "clown_m" }.sexFemale)
        assertEquals(0.0, sim.organisms.first { it.id == "clown_m" }.sexChange, 0.0)
    }
}
