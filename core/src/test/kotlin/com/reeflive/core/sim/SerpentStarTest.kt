package com.reeflive.core.sim

import com.reeflive.core.ai.BrittleStarBrain
import com.reeflive.core.ai.SerpentStarBrain
import com.reeflive.core.custom.economy.PriceBook
import com.reeflive.core.custom.economy.Stores
import com.reeflive.core.geom.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Green serpent star (docs/research/45_clam_crab_star_cucumber.md §3). */
class SerpentStarTest {
    private val id = "ophiarachna_incrassata"

    private fun sim(hour: Double, vararg more: LivestockSeed): Simulation {
        val sim = TestTank.sim(listOf(TestTank.seed("brittle")), seed = 4L, startHour = hour)
        for (s in listOf(LivestockSeed(id, "Slinky", 35.0, id = "s")) + more) { sim.species.ensure(s.speciesId); sim.addFromSeed(s) }
        sim.advance(1.0, behaviorDt = 0.05)
        return sim
    }

    @Test fun sheetBrainAndStore() {
        val db = SpeciesCatalog.fromClasspath(listOf(id))
        assertEquals(BrainKind.BRITTLE_STAR, db[id].brainKind)
        assertEquals("models/serpent_star.glb", db[id].model)
        assertTrue(Stocking.entry(id) != null)
        assertEquals(35 * PriceBook.SCALE, PriceBook.listing(id)!!.price)
        val sim = sim(16.0, LivestockSeed("nemateleotris_magnifica", "Fire", 6.0, id = "ff"))
        val s = sim.organisms.first { it.id == "s" }
        val b = sim.agentOf(s)!!.brain
        assertTrue(b is SerpentStarBrain)
        // its own hide, not the black brittle star's crevice
        val black = sim.organisms.first { it.brainKind == BrainKind.BRITTLE_STAR && it.id != "s" }
        val bh = (sim.agentOf(black)!!.brain as BrittleStarBrain).home
        assertTrue("own hide ${(b as SerpentStarBrain).home} vs $bh", b.home.distanceTo(bh) > 12.0)
        assertTrue(Stores.warning(sim, id)!!.contains("fish"))
    }

    @Test fun hidesByDayAndRoamsAtNight() {
        val day = sim(13.0)
        val d = day.organisms.first { it.id == "s" }
        day.advance(1200.0, behaviorDt = 0.1)
        val home = (day.agentOf(d)!!.brain as SerpentStarBrain).home
        println("serpent star by day: ${d.behaviorName}, %.1f cm from its hide".format(d.pos.distanceTo(home)))
        assertTrue("in its hide by day (${d.behaviorName})", d.pos.distanceTo(home) < 8.0)

        val night = sim(1.0)
        val o = night.organisms.first { it.id == "s" }
        var moved = 0.0; var last = o.pos; var minClear = 9.0
        night.advance(1800.0, behaviorDt = 0.1) { moved += o.pos.distanceTo(last); last = o.pos; minClear = minOf(minClear, night.rock.sdf.dist(o.pos)) }
        println("serpent star at night: moved %.0f cm, nearest rock %.2f cm".format(moved, minClear))
        assertTrue("roams at night ($moved cm)", moved > 20)
        assertTrue("disc never in the rock ($minClear)", minClear > -0.2)
    }

    @Test fun aHungryStarCatchesASmallSleepingFishAtNight() {
        val sim = sim(1.0, LivestockSeed("nemateleotris_magnifica", "Fire", 5.0, id = "ff"))
        val s = sim.organisms.first { it.id == "s" }
        val fish = sim.organisms.first { it.id == "ff" }
        val b = sim.agentOf(s)!!.brain as SerpentStarBrain
        s.hunger = 0.8
        var t = 0.0
        while (t < 3 * 3600 && b.fishEaten == 0) {
            sim.advance(10.0, behaviorDt = 0.1) {
                // a fish asleep on the rock beside the star's disc
                if (fish.alive && fish.heldBy == null) {
                    fish.sleeping = true; fish.vel = Vec3.ZERO
                    fish.pos = sim.rock.sdf.project(s.pos + Vec3(6.0, 1.5, 0.0), 1.0)
                }
            }
            if (b.grabs > 0 && b.fishEaten == 0) b.nextTry = 0.0
            t += 10
        }
        println("serpent star: ${b.grabs} grabs, ${b.fishEaten} eaten in ${t / 60} min")
        assertEquals(1, b.fishEaten)
        assertTrue(!fish.alive)
        // fed: it leaves fish alone for days
        assertTrue(sim.simTimeSec < s.satietyUntil)

        // a fed star leaves a sleeping fish alone
        val fed = sim(1.0, LivestockSeed("nemateleotris_magnifica", "Fire", 5.0, id = "ff"))
        val s2 = fed.organisms.first { it.id == "s" }
        val f2 = fed.organisms.first { it.id == "ff" }
        s2.hunger = 0.2
        fed.advance(1800.0, behaviorDt = 0.1) { if (f2.alive) { f2.sleeping = true; f2.vel = Vec3.ZERO; f2.pos = fed.rock.sdf.project(s2.pos + Vec3(6.0, 1.5, 0.0), 1.0) } }
        assertEquals(0, (fed.agentOf(s2)!!.brain as SerpentStarBrain).grabs)
    }
}
