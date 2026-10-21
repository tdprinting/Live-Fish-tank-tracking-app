package com.reeflive.core.sim

import com.reeflive.core.ai.FairyWrasseBrain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Red fairy wrasse, yellow tang and purple tang (research 42). */
class FairyWrasseZebrasomaTest {
    private val fairy = "cirrhilabrus_rubripinnis"
    private val yellow = "zebrasoma_flavescens"
    private val purple = "zebrasoma_xanthurum"

    private fun sim(hour: Double) = TestTank.sim(DefaultStock.corals, seed = 7L, startHour = hour).also { s ->
        for (id in listOf(fairy, yellow, purple)) s.species.ensure(id)
    }

    @Test fun sheetsResolveBrainsModelsAndShopItems() {
        val cat = SpeciesCatalog.fromClasspath(listOf(fairy, yellow, purple))
        assertEquals(BrainKind.FAIRY_WRASSE, cat[fairy].brainKind)
        assertEquals("models/fairy_wrasse.glb", cat[fairy].model)
        assertEquals(SexSystem.PROTOGYNOUS, LifeStageSpec.of(cat[fairy]).system)
        for (id in listOf(yellow, purple)) {
            assertEquals(BrainKind.TANG, cat[id].brainKind)
            assertTrue(cat[id].zebrasoma)
            assertEquals("night", LifeStageSpec.of(cat[id]).nightForm)
        }
        val male = Stocking.CATALOG.single { it.speciesId == fairy && it.role == "MALE" }
        assertEquals("$fairy@male", male.key)
        assertEquals(fairy, Stocking.speciesOf(male.key))
        assertEquals("MALE", Stocking.roleOf(male.key))
        assertEquals(male, Stocking.entry(male.key))
        assertTrue(LifeCycle.minVolumeL(purple, BrainKind.TANG) > LifeCycle.minVolumeL(yellow, BrainKind.TANG))
    }

    @Test fun shopMaleArrivesGrownAndMale() {
        val s = sim(12.0)
        assertEquals(1, Stocking.addMany(s, "$fairy@male", 1))
        val o = s.organisms.last()
        assertEquals("MALE", o.role)
        assertTrue("sold grown: ${o.lengthCm}", o.lengthCm >= LifeStageSpec.of(o.species).maturityCm)
        assertEquals(SexRole.MALE, LifeStages.sexRole(o))
        assertEquals("male", LifeStages.form(o).sexForm)
    }

    @Test fun maleFlashesToHisFemalesByDay() {
        val s = sim(10.0)
        val m = s.addFromSeed(LivestockSeed(fairy, "Red", 8.5, "MALE", id = "m"))
        s.addFromSeed(LivestockSeed(fairy, "F1", 7.0, "FEMALE", id = "f1"))
        s.addFromSeed(LivestockSeed(fairy, "F2", 6.8, "FEMALE", id = "f2"))
        val b = s.agentOf(m)!!.brain as FairyWrasseBrain
        s.advance(3600.0, behaviorDt = 0.1)
        assertTrue("flashes ${b.flashes}", b.flashes >= 1)
        assertTrue("plankton snaps ${b.snaps}", b.snaps >= 60)
    }

    @Test fun sleepsInACocoonAtNight() {
        val s = sim(19.5)
        val o = s.addFromSeed(LivestockSeed(fairy, "F", 7.0, "FEMALE", id = "f"))
        val b = s.agentOf(o)!!.brain as FairyWrasseBrain
        s.advance(3.0 * 3600, behaviorDt = 0.1)
        assertTrue(o.sleeping)
        assertTrue("cocoon ${o.cocoon}", o.cocoon > 0.5)
        assertTrue(b.cocoonsBuilt >= 1)
    }

    @Test fun yellowTangTurnsNightColours() {
        val s = sim(20.5)
        val o = s.addFromSeed(LivestockSeed(yellow, "Yellow", 16.0, id = "y"))
        s.advance(2.0 * 3600, behaviorDt = 0.1)
        assertTrue("night colour ${o.nightColor}", o.nightColor > 0.5)
        assertEquals("night", LifeStages.form(o).sexForm)
    }

    @Test fun zebrasomaTangsFight() {
        val s = sim(10.0)
        val a = s.addFromSeed(LivestockSeed(purple, "Purple", 18.0, id = "p"))
        val y = s.addFromSeed(LivestockSeed(yellow, "Yellow", 16.0, id = "y"))
        a.arrivalTime = s.simTimeSec; y.arrivalTime = s.simTimeSec
        val ev = ArrayList<SimEvent>()
        s.advance(2.0 * 3600, behaviorDt = 0.1) { ev += s.drainEvents() }
        val chases = ev.filterIsInstance<SimEvent.Chase>().count { it.actorId in setOf("p", "y") && it.targetId in setOf("p", "y") }
        assertTrue("tang-on-tang chases $chases", chases >= 3)
    }

    @Test fun compatibilityWarnings() {
        assertTrue(compatibilityWarnings(yellow, listOf(purple), 1000.0).any { it.code == "zebrasoma_pair" })
        assertTrue(compatibilityWarnings(purple, listOf("paracanthurus_hepatus"), 1000.0).any { it.code == "purple_tang_bully" })
        assertTrue(compatibilityWarnings(fairy, listOf(fairy), 150.0).map { it.code }.containsAll(listOf("fairy_wrasse_jumper", "fairy_wrasse_harem", "fairy_wrasse_tank_size")))
    }
}
