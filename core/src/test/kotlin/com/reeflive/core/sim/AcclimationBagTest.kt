package com.reeflive.core.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sandbox "Add life" in bags: drop, float 30 sim minutes, tip out ([AcclimationBags], [Command.FloatBag]). */
class AcclimationBagTest {
    private fun sim(stock: List<LivestockSeed> = emptyList()): Simulation =
        Simulation(TankConfig(seed = 5L), SpeciesCatalog(), TestTank.rock, 5L, 12 * 3600.0, livestock = stock)

    private fun clowns(s: Simulation) = s.organisms.filter { it.species.id == "amphiprion_ocellaris" }

    @Test fun bagFloatsThirtyMinutesThenTheFishSwimOut() {
        val s = sim()
        s.enqueue(Command.FloatBag("amphiprion_ocellaris", 2, x = 60.0))
        repeat(20) { s.stepWall(0.05) }
        val snap = s.snapshot()
        assertEquals(1, snap.bags.size)
        val bag = snap.bags[0]
        assertEquals(1, bag.phase)                      // landed and floating
        assertTrue("still in the bag", clowns(s).isEmpty())
        assertEquals(2, snap.organisms.count { it.bagged })
        assertTrue("bag water starts cooler than the tank", bag.bagTempC < bag.tankTempC - 1)
        // inside the glass with the bulb clear of the rock
        val r = s.rock
        assertTrue(bag.x - bag.radius > 0 && bag.x + bag.radius < r.lengthCm && bag.z - bag.radius > 0 && bag.z + bag.radius < r.widthCm)
        assertTrue(r.sdf.dist(com.reeflive.core.geom.Vec3(bag.x.toDouble(), bag.y - 0.7 * bag.radius.toDouble(), bag.z.toDouble())) > bag.radius - 0.5)

        s.advance(25 * 60.0, behaviorDt = 0.5, fullBehavior = false)
        assertTrue("not yet at 25 min", clowns(s).isEmpty())
        val mid = s.snapshot().bags.single()
        assertTrue("water evened out", kotlin.math.abs(mid.bagTempC - mid.tankTempC) < 0.1)

        s.advance(6 * 60.0, behaviorDt = 0.5, fullBehavior = false)
        val out = clowns(s)
        assertEquals(2, out.size)
        assertTrue(out.all { it.alive && s.rock.inWater(it.pos) && it.id.contains("#bag") })
        s.advance(5.0, behaviorDt = 0.05)
        val after = s.snapshot()
        assertTrue("bag lifted out", after.bags.isEmpty())
        assertEquals(0, after.organisms.count { it.bagged })
        assertEquals(2, after.stats.count { it.speciesId == "amphiprion_ocellaris" })
    }

    @Test fun fastForwardRunsTheClockUntilTheBagsOpen() {
        val s = sim()
        s.enqueue(Command.FloatBag("paracanthurus_hepatus", 1, x = 40.0))
        s.stepWall(0.05)
        s.enqueue(Command.HurryBags(true))
        s.stepWall(0.05)
        assertEquals(AcclimationBags.HURRY_SPEED, s.snapshot().speed, 1e-9)
        val t0 = s.simTimeSec
        // 30 sim minutes at 60x is 30 s of wall time
        repeat(20 * 32) { s.stepWall(0.05) }
        assertTrue(s.simTimeSec - t0 >= AcclimationBags.FLOAT_SEC)
        repeat(20 * 3) { s.stepWall(0.05) }
        assertEquals(1, s.organisms.count { it.species.id == "paracanthurus_hepatus" })
        assertEquals("back to the chosen speed", 1.0, s.snapshot().speed, 1e-9)
    }

    @Test fun manyAnimalsComeInSeveralBagsThatDoNotOverlap() {
        val s = sim()
        s.enqueue(Command.FloatBag("amphiprion_ocellaris", 20, x = 60.0))
        repeat(40) { s.stepWall(0.05) }
        val bags = s.snapshot().bags
        assertTrue(bags.size >= 4)
        assertEquals(20, bags.sumOf { it.count })
        for (i in bags.indices) for (j in i + 1 until bags.size) {
            val a = bags[i]; val b = bags[j]
            val d = kotlin.math.hypot((a.x - b.x).toDouble(), (a.z - b.z).toDouble())
            assertTrue("bags ${a.id} and ${b.id} overlap ($d)", d >= a.radius + b.radius - 0.05)
        }
    }

    @Test fun swimmersAreKeptOutOfAFloatingBag() {
        val s = sim(DefaultStock.all.filter { it.speciesId == "paracanthurus_hepatus" || it.speciesId == "amphiprion_ocellaris" })
        s.enqueue(Command.FloatBag("ecsenius_stigmatura", 1, x = 60.0))
        repeat(40) { s.stepWall(0.05) }
        val bag = s.snapshot().bags.single()
        // park a resident right in the bulb: the next behaviour step puts it outside
        val tang = s.organisms.first { it.species.id == "paracanthurus_hepatus" }
        tang.placeAt(com.reeflive.core.geom.Vec3(bag.x.toDouble(), bag.y - 0.7 * bag.radius.toDouble(), bag.z.toDouble()), com.reeflive.core.geom.Vec3.X)
        repeat(60) {
            s.stepWall(0.05)
            val b = s.snapshot().bags.single()
            val c = com.reeflive.core.geom.Vec3(b.x.toDouble(), b.y - 0.7 * b.radius.toDouble(), b.z.toDouble())
            for (o in s.organisms) if (o.alive && !o.attached) assertTrue("${o.id} inside the bag", o.pos.distanceTo(c) >= b.radius - 0.2)
        }
    }

    @Test fun crawlersSinkToTheBottomAndCoralsSettle() {
        val s = sim()
        s.enqueue(Command.FloatBag("clibanarius_tricolor", 3, x = 80.0))
        s.enqueue(Command.FloatBag("zoanthus_sp", 2, x = 30.0))
        repeat(20) { s.stepWall(0.05) }
        val corals0 = s.corals.size
        s.enqueue(Command.HurryBags(false, openNow = true))
        repeat(20 * 8) { s.stepWall(0.05) }
        val hermits = s.organisms.filter { it.species.id == "clibanarius_tricolor" }
        assertEquals(3, hermits.size)
        assertTrue(hermits.all { s.rock.envDist(it.pos) < 2.0 })
        assertEquals(corals0 + 2, s.corals.size)
        assertTrue(s.snapshot().bags.isEmpty())
    }
}
