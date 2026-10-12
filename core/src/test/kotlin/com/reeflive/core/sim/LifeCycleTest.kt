package com.reeflive.core.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** B-8: lifespan, senescence, chronic-stress lifespan penalty, "old age" deaths. */
class LifeCycleTest {
    private val year = LifeCycle.SEC_PER_YEAR

    @Test
    fun initialStockAgesAreSensible() {
        val sim = TestTank.sim(DefaultStock.all)
        for (o in sim.organisms) {
            val frac = o.ageSec / LifeCycle.lifespanSec(o.species)
            assertTrue("${o.nickname} age fraction $frac", frac in 0.05..0.6)
        }
        // a juvenile tang is younger (as a fraction of life) than a full-grown clownfish
        val tang = sim.organisms.first { it.brainKind == BrainKind.TANG }
        assertTrue(tang.ageSec > 0 && tang.ageYears < tang.species.lifespanYears * 0.4)
    }

    @Test
    fun explicitSeedAgeWins() {
        val sim = TestTank.sim(listOf(TestTank.seed("tang").copy(ageYears = 3.0)))
        assertEquals(3.0, sim.organisms.first().ageYears, 1e-9)
    }

    @Test
    fun agePassesWithSimTimeOnly() {
        val sim = TestTank.sim(listOf(TestTank.seed("tang")))
        val o = sim.organisms.first()
        val a0 = o.ageSec
        sim.advanceAbstract(30 * 86400.0)   // time-lapse path: behaviour off
        assertEquals(30 * 86400.0, o.ageSec - a0, 120.0)
        assertTrue(sim.snapshot().stats.first { it.id == o.id }.ageYears > a0 / year)
    }

    @Test
    fun oldAnimalDiesOfOldAge() {
        val seed = TestTank.seed("clown_f")
        var died = 0
        for (s in 1L..6L) {
            val sim = TestTank.sim(listOf(seed), seed = s)   // species lifespan 5 y in the fixtures
            val o = sim.organisms.first(); o.acceptsPrepared = true
            val life = o.species.lifespanYears
            sim.organisms.first().ageSec = life * 1.3 * year
            sim.advanceAbstract(2 * year)
            if (!o.alive && o.deathCause == LifeCycle.OLD_AGE) died++
            // frail animals may also succumb to disease first, but they never survive 2 y past their lifespan
            assertTrue("seed $s: alive=${o.alive} cause=${o.deathCause}", !o.alive)
        }
        assertTrue("old-age deaths $died/6", died >= 3)
    }

    @Test
    fun youngAnimalDoesNotDieOfOldAge() {
        val sim = TestTank.sim(listOf(TestTank.seed("clown_f")))
        sim.advanceAbstract(90 * 86400.0)
        val o = sim.organisms.first()
        assertNull(o.deathCause)
        assertTrue(o.alive)
    }

    @Test
    fun senescenceRaisesSusceptibilityAndLowersImmunity() {
        val sim = TestTank.sim(listOf(TestTank.seed("tang")))
        val o = sim.organisms.first()
        o.ageSec = 0.1 * LifeCycle.lifespanSec(o.species)
        assertEquals(0.0, LifeCycle.senescence(o), 0.0)
        assertEquals(1.0, LifeCycle.susceptibility(o), 0.0)
        o.ageSec = 1.2 * LifeCycle.lifespanSec(o.species)
        assertTrue(LifeCycle.susceptibility(o) > 1.5)
        val before = o.immunity
        val ev = ArrayList<SimEvent>()
        LifeCycle.step(o, 3600.0, 227.0, 0.0, ev)
        assertTrue(o.immunity < before)
        assertTrue(LifeCycle.hazardPerYear(0.5) == 0.0 && LifeCycle.hazardPerYear(1.2) > LifeCycle.hazardPerYear(0.9))
    }

    @Test
    fun crampedTangAgesFasterThanInBigTank() {
        val sim = TestTank.sim(listOf(TestTank.seed("tang")))
        val tang = sim.organisms.first()
        tang.lengthCm = 14.0
        val small = LifeCycle.extraAgingRate(tang, 227.0)
        assertTrue("cramped tang extra rate $small", small > 0.1)
        assertEquals(0.0, LifeCycle.extraAgingRate(tang, 500.0), 1e-9)
        tang.chronic = 0.6
        assertTrue(LifeCycle.extraAgingRate(tang, 500.0) > 0.5)   // chronic stress alone also ages
        // and the debt accrues in a real run
        val sim2 = TestTank.sim(listOf(TestTank.seed("tang")))
        sim2.advanceAbstract(10 * 86400.0)
        assertTrue(sim2.organisms.first().agingDebtSec > 0.0)
    }
}
