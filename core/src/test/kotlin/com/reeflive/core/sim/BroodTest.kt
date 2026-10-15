package com.reeflive.core.sim

import com.reeflive.core.custom.CustomTank
import com.reeflive.core.custom.CustomTankCodec
import com.reeflive.core.custom.CustomTankSave
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Clownfish larvae, the nursery tank and fry (sim/Brood.kt, research 28). */
class BroodTest {
    private fun pair() = listOf(
        LivestockSeed("amphiprion_ocellaris", "Nemo", 8.0, role = "FEMALE", placement = "HOST_SHROOM", id = "clown_f"),
        LivestockSeed("amphiprion_ocellaris", "Marlin", 6.5, role = "MALE", placement = "HOST_SHROOM", id = "clown_m", variant = "darwin_black"),
    )

    /** Abstract-mode hours with the fish kept fed and calm. */
    private fun hours(sim: Simulation, n: Int, until: () -> Boolean = { false }): Boolean {
        repeat(n) {
            for (o in sim.organisms) if (o.alive) { o.hunger = 0.2; o.stress = 0.0 }
            sim.advanceAbstract(3600.0)
            if (until()) return true
        }
        return false
    }

    private fun spawnAndHatch(sim: Simulation) {
        sim.enqueue(Command.Breed(Brood.Act.SPAWN_NOW.name))
        assertTrue("forced spawning laid no eggs", hours(sim, 36) { sim.repro.clownClutch() != null })
        assertTrue("eggs never hatched", hours(sim, 14 * 24) { sim.brood.totalHatched > 0 })
    }

    @Test
    fun spawnNowLaysAClutchWithinHours() {
        val sim = TestTank.sim(pair(), seed = 3)
        sim.enqueue(Command.Breed(Brood.Act.SPAWN_NOW.name))
        assertTrue(hours(sim, 36) { sim.repro.clownClutch() != null })
        val c = sim.repro.clownClutch()!!
        assertTrue("eggs ${c.eggs}", c.eggs in 80..1000)
        assertEquals(c.eggs, sim.snapshot().eggClutches.single().eggs)
    }

    @Test
    fun larvaeLeftInTheDisplayAreGoneWithinTwoDays() {
        val sim = TestTank.sim(DefaultStock.fish.filter { it.speciesId != "amphiprion_ocellaris" } + pair(), seed = 4)
        spawnAndHatch(sim)
        val start = sim.brood.displayCohort()!!.hatched
        assertTrue(sim.snapshot().brood.displayLarvae > 0)
        hours(sim, 48)
        assertTrue("left ${sim.brood.displayCohort()?.alive} of $start", (sim.brood.displayCohort()?.alive ?: 0) == 0)
        assertNull(sim.brood.nurseryCohort())
    }

    @Test
    fun raisedLarvaeMetamorphoseWithTheBreederRoutine() {
        val sim = TestTank.sim(pair(), seed = 5)
        sim.enqueue(Command.Breed(Brood.Act.RAISE_ON.name))
        spawnAndHatch(sim)
        hours(sim, 2)
        val n = sim.brood.nurseryCohort()
        assertNotNull("larvae were not caught into the nursery", n)
        val caught = n!!.count
        sim.enqueue(Command.Breed(Brood.Act.RAISE_OFF.name))   // the next clutch stays in the display
        hours(sim, 16 * 24)
        val after = sim.brood.nurseryCohort()!!
        assertTrue("not metamorphosed at day ${after.ageDays(sim.simTimeSec)}", after.metamorphosed)
        val survival = after.count / caught
        assertTrue("survival to fry ${"%.2f".format(survival)}", survival in 0.2..0.8)
        assertTrue("fry ${after.lengthMm} mm", after.lengthMm in 8.0..14.0)
        // fry move into the display as ordinary clowns of a parent's colour form
        val before = sim.organisms.size
        sim.enqueue(Command.Breed(Brood.Act.RELEASE.name))
        hours(sim, 1)
        assertEquals(before + 1, sim.organisms.size)
        val baby = sim.organisms.last()
        assertEquals("amphiprion_ocellaris", baby.species.id)
        assertTrue(baby.variant == null || baby.variant == "darwin_black")
        assertTrue("baby ${baby.lengthCm} cm", baby.lengthCm < 1.5)
        sim.enqueue(Command.Breed(Brood.Act.AUTO_OFF.name))
        assertNull("a baby is not sexed yet", baby.sexFemale)
    }

    @Test
    fun unfedLarvaeStarve() {
        val sim = TestTank.sim(pair(), seed = 6)
        sim.enqueue(Command.Breed(Brood.Act.RAISE_ON.name))
        sim.enqueue(Command.Breed(Brood.Act.AUTO_OFF.name))
        spawnAndHatch(sim)
        hours(sim, 2)
        assertNotNull(sim.brood.nurseryCohort())
        hours(sim, 7 * 24)
        assertTrue("unfed larvae alive: ${sim.brood.nurseryCohort()?.alive}", (sim.brood.nurseryCohort()?.alive ?: 0) < 5)
    }

    @Test
    fun myTankSavesTheNurseryAndEggs() {
        val sim = TestTank.sim(pair(), seed = 5)
        sim.enqueue(Command.Breed(Brood.Act.RAISE_ON.name))
        spawnAndHatch(sim)
        hours(sim, 3 * 24)
        val n = sim.brood.nurseryCohort()!!
        val save = CustomTankCodec.decode(CustomTankCodec.encode(CustomTank.capture(sim, CustomTankSave(seed = 5), 0L)))!!
        val back = CustomTank.restore(save, TestTank.catalog, TestTank.rock)
        val m = back.brood.nurseryCohort()
        assertNotNull(m)
        assertEquals(n.count, m!!.count, 1e-6)
        assertEquals(n.devDays, m.devDays, 1e-6)
        assertEquals(n.speciesId, m.speciesId)
        // an older save without the brood block opens with an empty nursery
        val old = CustomTankCodec.decode("""{"version":1,"name":"x"}""")!!
        assertEquals(0, old.brood.cohorts.size)
    }

    @Test
    fun theRoutineGrowsJuvenilesAndMovesThemToTheTank() {
        val sim = TestTank.sim(pair(), seed = 5)
        sim.enqueue(Command.Breed(Brood.Act.RAISE_ON.name))
        spawnAndHatch(sim)
        hours(sim, 2)
        repeat(2) { sim.enqueue(Command.Breed(Brood.Act.FASTER.name)) }   // 1x -> 10x -> 100x
        hours(sim, 1)
        assertEquals(100, sim.snapshot().brood.growSpeed)
        val seq = sim.snapshot().brood.careSeq
        val clowns = { sim.organisms.count { it.alive && it.species.id == "amphiprion_ocellaris" } }
        assertTrue("no juvenile moved to the tank", hours(sim, 48) { clowns() > 2 })
        assertTrue("the routine never fed or siphoned", sim.snapshot().brood.careSeq > seq + 10)
        val baby = sim.organisms.last()
        assertTrue("juvenile ${baby.lengthCm} cm", baby.lengthCm in 2.0..3.5)
        hours(sim, 72)
        assertTrue("tank over its clown cap: ${clowns()}", clowns() <= BroodSpecs.CLOWN.displayCap)
        assertNull("nursery should empty once the tank is full", sim.brood.nurseryCohort())
    }
}
