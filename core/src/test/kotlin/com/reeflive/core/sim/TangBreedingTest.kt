package com.reeflive.core.sim

import com.reeflive.core.ai.TangBrain
import com.reeflive.core.ai.TangSpawn
import com.reeflive.core.custom.CustomTank
import com.reeflive.core.custom.CustomTankCodec
import com.reeflive.core.custom.CustomTankSave
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Blue tang spawning, floating eggs, the nursery and baby tangs (sim/Brood.kt TANG, research 41). */
class TangBreedingTest {
    private val tang = "paracanthurus_hepatus"
    private fun pair() = listOf(
        LivestockSeed(tang, "Dori", 15.0, role = "FEMALE", id = "tang_f"),
        LivestockSeed(tang, "Bruce", 14.0, role = "MALE", id = "tang_m"),
    )
    private fun act(sim: Simulation, a: Brood.Act) = sim.enqueue(Command.Breed("${a.name}:tang"))

    private fun hours(sim: Simulation, n: Int, until: () -> Boolean = { false }): Boolean {
        repeat(n) {
            for (o in sim.organisms) if (o.alive) { o.hunger = 0.2; o.stress = 0.0 }
            sim.advanceAbstract(3600.0)
            if (until()) return true
        }
        return false
    }

    private fun spawn(sim: Simulation) {
        act(sim, Brood.Act.SPAWN_NOW)
        assertTrue("forced spawning made no eggs", hours(sim, 3) { sim.repro.tangSpawns > 0 })
    }

    @Test
    fun spawnNowLeavesFloatingEggsThatHatchInADay() {
        val sim = TestTank.sim(pair(), seed = 3)
        spawn(sim)
        val d = sim.brood.displayCohort()!!
        assertTrue("eggs ${d.hatched}", d.hatched in 400..15000)
        assertTrue("eggs, not larvae, at first", d.eggs && sim.snapshot().brood.displayEggs > 0)
        assertEquals(0, sim.snapshot().brood.displayLarvae)
        // they hatch within about a day, and by then the filter and the fish have taken nearly all of them
        val h = d.eggDays * 24
        assertTrue("hatch in ${"%.1f".format(h)} h", h in 14.0..30.0)
        hours(sim, 48)
        assertTrue("left ${sim.brood.displayCohort()?.alive}", (sim.brood.displayCohort()?.alive ?: 0) < 0.01 * d.hatched)
        assertTrue("the panel shows on a tang's card", sim.snapshot().brood.panelFor(tang) != null)
        assertTrue("and not on a clown's", sim.snapshot().brood.panelFor("amphiprion_ocellaris") == null)
    }

    @Test
    fun raisedTangsGoThroughEveryStageWithTheRoutine() {
        val sim = TestTank.sim(pair(), seed = 5)
        act(sim, Brood.Act.RAISE_ON)
        spawn(sim)
        val n = sim.brood.nurseryCohort()
        assertNotNull("the eggs were not skimmed into the nursery", n); n!!
        assertTrue("stocked ${n.count}", n.count in 300.0..BroodSpecs.TANG.nurseryStock.toDouble())
        assertTrue(sim.snapshot().brood.nurseryEggs)
        val caught = n.count
        repeat(2) { act(sim, Brood.Act.FASTER) }   // 100x nursery
        val stages = HashSet<Int>()
        val foods = HashSet<CareKind>()
        var juvenileCount = -1.0
        // minute steps through the first week of food changes (each feeding shows for a moment), then hours
        repeat(8 * 60) {
            for (o in sim.organisms) if (o.alive) { o.hunger = 0.2; o.stress = 0.0 }
            sim.advanceAbstract(60.0)
            val v = sim.snapshot().brood
            foods += v.careKind
            if (v.nursery > 0 && !v.nurseryEggs) stages += v.nurseryStage
        }
        hours(sim, 24) {
            val v = sim.snapshot().brood
            if (v.nursery > 0 && !v.nurseryEggs) stages += v.nurseryStage
            foods += v.careKind
            val c = sim.brood.nurseryCohort()
            if (c != null && c.metamorphosed && juvenileCount < 0) juvenileCount = c.count
            false
        }
        assertEquals("larva, spiny silver postlarva and fry", setOf(0, 1, 2), stages)
        assertTrue("first food was copepods: $foods", CareKind.COPEPODS in foods && CareKind.ROTIFERS in foods && CareKind.BRINE in foods)
        val survival = juvenileCount / caught
        assertTrue("survival to juveniles ${"%.3f".format(survival)}", survival in 0.01..0.12)
        assertTrue("babies moved to the tank or the fish store", sim.brood.autoMoved > 0 || sim.brood.toStore > 0)
        val tangs = sim.organisms.count { it.alive && it.species.id == tang }
        assertTrue("tank over its tang cap: $tangs", tangs <= BroodSpecs.TANG.displayCap)
    }

    @Test
    fun larvaeFedOnlyRotifersStarve() {
        val sim = TestTank.sim(pair(), seed = 6)
        act(sim, Brood.Act.RAISE_ON)
        act(sim, Brood.Act.AUTO_OFF)
        spawn(sim)
        // rotifers are too big for a newly hatched tang's mouth: without copepods the first-feeding larvae starve
        repeat(10 * 6) { act(sim, Brood.Act.ROTIFERS); hours(sim, 4) }
        assertTrue("alive on rotifers alone: ${sim.brood.nurseryCohort()?.alive}", (sim.brood.nurseryCohort()?.alive ?: 0) < 10)
    }

    @Test
    fun aGrownPairSpawnsAtDuskAroundTheFullMoon() {
        val sim = TestTank.sim(pair(), seed = 7)
        for (o in sim.organisms) o.arrivalTime = sim.simTimeSec - 30 * 86400.0
        assertTrue("no spawning in two lunar months", hours(sim, 60 * 24) { sim.repro.tangSpawns > 0 })
        val moon = sim.repro.moonDay()
        assertTrue("moon day ${"%.1f".format(moon)}", kotlin.math.abs(moon - Reproduction.LUNAR_DAYS / 2) <= Reproduction.TANG_MOON_WINDOW_DAYS + 0.2)
        val hour = (sim.simTimeSec % 86400.0) / 3600.0
        assertTrue("lights still on just after spawning (hour ${"%.1f".format(hour)})", sim.lights.intensity(sim.simTimeSec) >= LightSchedule.LIGHTS_ON || hour > 17)
    }

    @Test
    fun theCourtingPairChasesAndRushesToTheSurface() {
        val sim = TestTank.sim(pair(), seed = 4, startHour = 18.0)
        val f = sim.organisms.first { it.id == "tang_f" }; val m = sim.organisms.first { it.id == "tang_m" }
        sim.advance(20.0, behaviorDt = 0.1)
        act(sim, Brood.Act.SPAWN_NOW)
        var near = 0; var courting = 0; var top = 0.0
        sim.advance(2400.0, behaviorDt = 0.1) {
            if (sim.repro.tangPhase == Reproduction.TangPhase.COURTING) { courting++; if (m.pos.distanceTo(f.pos) < 2.5 * f.lengthCm) near++ }
            if (sim.repro.tangPhase == Reproduction.TangPhase.RUSH) top = maxOf(top, f.pos.y)
        }
        assertTrue("never courted", courting > 0)
        assertTrue("male close to the female $near of $courting", near >= 0.6 * courting)
        assertTrue("rush reached ${"%.1f".format(top)} of ${sim.rock.waterLevelCm}", top > sim.rock.waterLevelCm - 8)
        assertTrue("spawned", sim.repro.tangSpawns == 1)
        val spawnB = (sim.agentOf(f)!!.brain as TangBrain).utility.behaviors.filterIsInstance<TangSpawn>().single()
        assertTrue("rushes ${spawnB.rushes}", spawnB.rushes >= 1)
    }

    @Test
    fun myTankSavesTheTangNursery() {
        val sim = TestTank.sim(pair(), seed = 5)
        act(sim, Brood.Act.RAISE_ON)
        spawn(sim)
        hours(sim, 4 * 24)
        val n = sim.brood.nurseryCohort()!!
        val save = CustomTankCodec.decode(CustomTankCodec.encode(CustomTank.capture(sim, CustomTankSave(seed = 5), 0L)))!!
        val back = CustomTank.restore(save, TestTank.catalog, TestTank.rock)
        val m = back.brood.nurseryCohort()!!
        assertEquals(BroodSpecs.TANG, m.spec)
        assertEquals(n.count, m.count, 1e-6)
        assertTrue("still raising tangs", back.brood.raises(BroodSpecs.TANG))
        assertTrue("sexes kept", back.organisms.first { it.id == "tang_f" }.sexFemale == true)
    }
}
