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
        LivestockSeed("amphiprion_ocellaris", "Nemo", sizeF, role = "FEMALE", placement = "HOST_SHROOM", id = "clown_f"),
        LivestockSeed("amphiprion_ocellaris", "Marlin", sizeM, role = "MALE", placement = "HOST_SHROOM", id = "clown_m"),
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

    // ---------------------------------------------------------------- B-2 cardinal mouthbrooding

    private fun cardinals(sizeF: Double = 5.5, sizeM: Double = 6.0) = listOf(
        LivestockSeed("sphaeramia_nematoptera", "Pajama", sizeM, id = "card_m"),
        LivestockSeed("sphaeramia_nematoptera", "Pajama2", sizeF, id = "card_f"),
    )

    @Test
    fun cardinalMaleBroodsAboutAWeekThenReleases() {
        val sim = TestTank.sim(cardinals(), seed = 4)
        val male = sim.organisms.first { it.id == "card_m" }
        days(sim, 20) { male.mouthbrooding }
        assertTrue("male never brooded", male.mouthbrooding)
        assertFalse(sim.organisms.first { it.id == "card_f" }.mouthbrooding)
        assertTrue(sim.snapshot().stats.first { it.id == "card_m" }.mouthbrooding)
        assertTrue(sim.snapshot().organisms.first { it.id == "card_m" }.mouthbrooding)
        val start = sim.simTimeSec
        days(sim, 15) { !male.mouthbrooding }
        val d = (sim.simTimeSec - start) / 86400.0
        assertFalse(male.mouthbrooding)
        assertEquals(1, sim.repro.hatchedBroods)
        assertTrue("brooded ${"%.1f".format(d)} d", d in 5.5..9.0)
        assertFalse(sim.snapshot().stats.first { it.id == "card_m" }.mouthbrooding)
    }

    @Test
    fun brooderDoesNotEat() {
        val sim = TestTank.sim(cardinals(), seed = 4)
        val male = sim.organisms.first { it.id == "card_m" }
        days(sim, 20) { male.mouthbrooding }
        assertTrue(male.mouthbrooding)
        // hunger is capped, and mysis at night go to the female only
        male.hunger = 0.55
        sim.advance(60.0)
        sim.enqueue(Command.Feed(FoodKind.MYSIS, 0.5))
        val fedBefore = male.counters.totalOf(Counters.K.FEED)
        sim.advance(900.0, behaviorDt = 0.1) { male.hunger = 0.55 }
        assertEquals(fedBefore, male.counters.totalOf(Counters.K.FEED))
        sim.advanceAbstract(3 * 86400.0)
        assertTrue("hunger capped ${male.hunger}", male.hunger <= Reproduction.BROOD_MAX_HUNGER + 0.05)
    }

    @Test
    fun stressedBrooderLosesTheBroodAndLoneCardinalNeverBroods() {
        val sim = TestTank.sim(cardinals(), seed = 4)
        val male = sim.organisms.first { it.id == "card_m" }
        days(sim, 20) { male.mouthbrooding }
        male.stress = 0.9
        sim.advanceAbstract(120.0)
        assertFalse(male.mouthbrooding)
        assertEquals(1, sim.repro.lostBroods)
        val lone = TestTank.sim(listOf(cardinals().first()), seed = 4)
        days(lone, 20)
        assertFalse(lone.organisms.single().mouthbrooding)
    }

    // ---------------------------------------------------------------- B-3 wrasse harem

    private fun wrasses(vararg sizes: Double) = sizes.mapIndexed { i, cm ->
        LivestockSeed("labroides_dimidiatus", "Doc$i", cm, id = "w$i")
    }

    @Test
    fun largestWrasseBecomesMaleWhenThereIsMoreThanOne() {
        val sim = TestTank.sim(wrasses(7.0, 9.0, 6.0), seed = 2)
        days(sim, 20)
        val ws = sim.organisms.associateBy { it.id }
        assertEquals(false, ws["w1"]!!.sexFemale)
        assertEquals("Male", ws["w1"]!!.sexLabel())
        assertTrue(ws["w0"]!!.sexFemale != false && ws["w2"]!!.sexFemale != false)
        // the male dies: the next largest takes over
        ws["w1"]!!.alive = false; sim.removeOrganism(ws["w1"]!!)
        days(sim, 20)
        assertEquals(false, ws["w0"]!!.sexFemale)
        assertTrue(ws["w2"]!!.sexFemale != false)
    }

    @Test
    fun aSingleWrasseKeepsItsSex() {
        val sim = TestTank.sim(wrasses(8.0), seed = 2)
        days(sim, 40)
        val w = sim.organisms.single()
        assertEquals(0.0, w.sexChange, 0.0)
        assertTrue(w.sexFemale != false)
    }

    // ---------------------------------------------------------------- C-3 tang nips soft corals

    @Test
    fun tangOccasionallyNipsZoasAndSoftCoralsButNotMushrooms() {
        val stock = listOf(TestTank.seed("tang"), TestTank.seed("zoa"), TestTank.seed("kenya"), TestTank.seed("shroom"))
        val sim = TestTank.sim(stock, seed = 9, startHour = 11.0)
        val tang = sim.agentOf(sim.organisms.single())!!.brain as TangBrain
        val zoa = sim.corals.first { it.id == "zoa" }
        val kenya = sim.corals.first { it.id == "kenya" }
        val shroom = sim.corals.first { it.id == "shroom" }
        var zoaPeak = 0.0; var shroomPeak = 0.0; var saw = false
        val first = sim.simTimeSec
        sim.advance(3 * 3600.0, behaviorDt = 0.2) {
            zoaPeak = maxOf(zoaPeak, zoa.disturb, kenya.disturb); shroomPeak = maxOf(shroomPeak, shroom.disturb)
            if (sim.organisms.single().behaviorName == "Nipping at the coral") saw = true
        }
        assertTrue("nips ${tang.coralNips}", tang.coralNips in 2..60)
        assertTrue("behaviour shown", saw)
        assertTrue("zoa or kenya disturbed $zoaPeak", zoaPeak > 0.2)
        assertEquals("mushrooms are left alone", 0.0, shroomPeak, 0.0)
        // minor: no tissue loss from the nips
        assertTrue(zoa.tissue > 0.95 && kenya.tissue > 0.95)
        assertTrue(sim.simTimeSec - first > 3 * 3600 - 1)
    }

    @Test
    fun noNipsWithoutTangAndNoneAtNight() {
        val sim = TestTank.sim(listOf(TestTank.seed("zoa"), TestTank.seed("clown_f")), seed = 9, startHour = 11.0)
        var peak = 0.0
        sim.advance(3600.0, behaviorDt = 0.2) { peak = maxOf(peak, sim.corals.single().disturb) }
        assertEquals(0.0, peak, 0.0)
        val night = TestTank.sim(listOf(TestTank.seed("tang"), TestTank.seed("zoa")), seed = 9, startHour = 23.0)
        night.advance(3600.0, behaviorDt = 0.2)
        assertEquals(0, (night.agentOf(night.organisms.single())!!.brain as TangBrain).coralNips)
    }
}
