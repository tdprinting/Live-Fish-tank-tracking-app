package com.reeflive.core.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Sandbox "Add life": many of one species at once ([Command.AddMany], [Stocking]). */
class StockingTest {
    private fun prodSim(stock: List<LivestockSeed> = emptyList(), seed: Long = 3L): Simulation =
        Simulation(TankConfig(seed = seed), SpeciesCatalog(), TestTank.rock, seed, 12 * 3600.0, livestock = stock)

    private fun tankStock() = DefaultStock.all.filter { it.speciesId in Stocking.CATALOG.map { e -> e.speciesId } }

    @Test fun everyCatalogEntryHasASheetAndABundledModel() {
        val models = listOf("../app/src/main/assets", "app/src/main/assets").map(::File).firstOrNull { it.isDirectory }
        val cat = SpeciesCatalog.fromClasspath(Stocking.CATALOG.map { it.speciesId })
        for (e in Stocking.CATALOG) {
            assertTrue("no sheet for ${e.speciesId}", cat.has(e.speciesId))
            val sp = cat[e.speciesId]
            // an entry that sells one sex names it: "Red fairy wrasse (male)"
            assertEquals(e.speciesId, sp.common, e.common.substringBefore(" ("))
            assertEquals(e.speciesId, sp.typicalAdultCm, e.typicalCm, 1e-9)
            assertEquals(e.speciesId, e.group == StockGroup.CORALS, sp.isCoral)
            val model = sp.model
            assertTrue("no model for ${e.speciesId}", model != null)
            if (models != null) assertTrue("missing asset $model", File(models, model!!).isFile)
        }
        assertTrue(Stocking.inGroup(StockGroup.FISH).size >= 9)
        assertTrue(Stocking.inGroup(StockGroup.INVERTS).size >= 10)
        assertTrue(Stocking.inGroup(StockGroup.CORALS).size >= 6)
    }

    @Test fun addingTwentyTangsYieldsTwentyLiveFishSpreadInTheWater() {
        val sim = prodSim()
        sim.enqueue(Command.AddMany("paracanthurus_hepatus", 20))
        sim.stepWall(0.05)
        val tangs = sim.organisms.filter { it.species.id == "paracanthurus_hepatus" }
        assertEquals(20, tangs.size)
        assertTrue(tangs.all { it.alive && sim.rock.inWater(it.pos) })
        assertTrue("real sheet loaded", tangs.first().species.common == "Blue tang")
        // shops sell juveniles: a new tang is ~7.5 cm, not the 25 cm adult
        assertEquals(7.5, tangs.first().lengthCm, 1e-9)
        // released along the surface, not stacked on one point
        val xs = tangs.map { it.pos.x }
        assertTrue("spread ${xs.minOrNull()}..${xs.maxOrNull()}", xs.maxOrNull()!! - xs.minOrNull()!! > 60)
        assertEquals(20, tangs.map { it.id }.toSet().size)
        // they show up in the HUD stats with their species name
        assertEquals(20, sim.snapshot().stats.count { it.speciesId == "paracanthurus_hepatus" })
    }

    @Test fun addSizeChoiceSetsLength() {
        val sim = prodSim()
        sim.enqueue(Command.AddMany("paracanthurus_hepatus", 1, size = StockSize.ADULT))
        sim.enqueue(Command.AddMany("amphiprion_ocellaris", 1))
        sim.stepWall(0.05)
        val tang = sim.organisms.last { it.species.id == "paracanthurus_hepatus" }
        assertEquals(25.0, tang.lengthCm, 1e-9)
        // tiny species keep a visible floor: a juvenile clown is 3 cm, not 2.4
        val clown = sim.organisms.last { it.species.id == "amphiprion_ocellaris" }
        assertEquals(3.0, clown.lengthCm, 1e-9)
        assertEquals(4.8, StockSize.YOUNG.lengthCm(8.0), 1e-9)
    }

    @Test fun addManyInvertsScattersCrawlers() {
        val sim = prodSim()
        sim.enqueue(Command.AddMany("clibanarius_tricolor", 12))
        sim.stepWall(0.05)
        val crabs = sim.organisms.filter { it.species.id == "clibanarius_tricolor" }
        assertEquals(12, crabs.size)
        assertTrue(crabs.map { Math.round(it.pos.x) to Math.round(it.pos.z) }.toSet().size > 6)
    }

    @Test fun addingCoralsPlacesThemOnFreeRockOrSand() {
        val sim = prodSim(tankStock())
        val before = sim.corals.toList()
        sim.enqueue(Command.AddMany("capnella_sp", 6))
        sim.enqueue(Command.AddMany("zoanthus_sp", 10))
        sim.stepWall(0.05)
        val kenyas = sim.corals.filter { it.species.id == "capnella_sp" } - before.toSet()
        val zoas = sim.corals.filter { it.species.id == "zoanthus_sp" } - before.toSet()
        assertEquals(6, kenyas.size)
        assertEquals(10, zoas.size)
        val rock = sim.rock
        for (c in kenyas + zoas) {
            assertTrue("${c.id} out of the tank: ${c.pos}", c.pos.x in 0.0..rock.lengthCm && c.pos.z in 0.0..rock.widthCm)
            assertTrue("${c.id} not on a surface: ${c.pos}", c.pos.y <= rock.sandTopCm + 1e-6 || kotlin.math.abs(rock.sdf.dist(c.pos)) < 0.5)
            assertTrue("${c.id} too high: ${c.pos}", c.pos.y <= rock.waterLevelCm - 15)
            // a copy of the anchored model: drawn moved from the species' home anchor
            val pose = c.toPose()
            assertTrue(pose.isCopy)
            assertEquals(c.anchor, Stocking.homeAnchor(rock, c.species)?.name ?: "")
        }
        // spaced apart from each other and from the tank's own colonies
        val all = sim.corals
        for ((i, a) in (kenyas + zoas).withIndex()) for (b in all) if (a !== b) {
            assertTrue("${a.id} on top of ${b.id}", a.pos.distanceTo(b.pos) > 4.0)
        }
        // the tank's own colonies stay where they were
        before.forEach { assertTrue(!it.toPose().isCopy) }
    }

    @Test fun caps() {
        val sim = prodSim()
        assertEquals(Stocking.MAX_ANIMALS_PER_ADD, Stocking.addMany(sim, "amphiprion_ocellaris", 500))
        assertEquals(Stocking.MAX_CORALS_PER_ADD.coerceAtMost(Stocking.MAX_CORALS), Stocking.addMany(sim, "discosoma_sp", 500))
        assertEquals(0, Stocking.addMany(sim, "amphiprion_ocellaris", 0))
    }

    @Test fun overstockingRaisesBioloadAmmoniaAndOxygenDemand() {
        class Run(val bio: Double, val nh3: Double, val doMgL: Double, val dead: Int)
        fun run(extra: Int): Run {
            val sim = prodSim(tankStock(), seed = 5L)
            if (extra > 0) sim.enqueue(Command.AddMany("amphiprion_ocellaris", extra, size = StockSize.ADULT))
            sim.stepWall(0.05)
            sim.advanceAbstract(6 * 3600.0)
            return Run(sim.water.bioMassIndex, sim.water.s.nh3Total, sim.water.s.doMgL, sim.eventLog.count { it is SimEvent.Died })
        }
        val base = run(0)
        val many = run(30)
        assertTrue("bioload ${base.bio} -> ${many.bio}", many.bio > base.bio * 1.5)
        assertTrue("ammonia ${base.nh3} -> ${many.nh3}", many.nh3 > base.nh3)
        assertTrue("oxygen ${base.doMgL} -> ${many.doMgL}", many.doMgL < base.doMgL)
        assertEquals("the tank alone is fine", 0, base.dead)
    }

    @Test fun aGrosslyOverstockedTankCrashes() {
        // thirty adult blue tangs in 60 gallons: oxygen runs out and the tank crashes within a day
        val sim = prodSim(tankStock(), seed = 5L)
        sim.enqueue(Command.AddMany("paracanthurus_hepatus", 30, size = StockSize.ADULT))
        sim.stepWall(0.05)
        sim.advanceAbstract(86400.0)
        assertTrue(sim.eventLog.count { it is SimEvent.Died } > 10)
    }

    @Test fun moreCoralsDrawMoreAlkalinity() {
        val base = prodSim(tankStock(), seed = 7L)
        val many = prodSim(tankStock(), seed = 7L)
        many.enqueue(Command.AddMany("tubipora_musica", 20))
        base.stepWall(0.05); many.stepWall(0.05)
        base.advanceAbstract(86400.0); many.advanceAbstract(86400.0)
        assertTrue(many.water.coralDemandDkhPerDay > base.water.coralDemandDkhPerDay)
        assertTrue("alk ${base.water.s.alk} vs ${many.water.s.alk}", many.water.s.alk < base.water.s.alk)
    }

    @Test fun resetToRealityRemovesTheAddedLife() {
        val stock = tankStock()
        val sim = prodSim(stock)
        val n0 = sim.organisms.size; val c0 = sim.corals.size
        sim.enqueue(Command.AddMany("pseudochromis_indigo", 15))
        sim.enqueue(Command.AddMany("briareum_sp", 5))
        sim.stepWall(0.05)
        assertEquals(n0 + 15, sim.organisms.size)
        assertEquals(c0 + 5, sim.corals.size)
        sim.enqueue(Command.ResetToReality(WaterState(), stock))
        sim.stepWall(0.05)
        assertEquals(n0, sim.organisms.size)
        assertEquals(c0, sim.corals.size)
    }

    @Test fun timelapseForkKeepsCoralCopiesWhereTheyAre() {
        val sim = prodSim(tankStock())
        sim.enqueue(Command.AddMany("clavularia_sp", 4))
        sim.stepWall(0.05)
        val fork = com.reeflive.core.sim.timelapse.TimelapseFork.of(sim)
        for (c in sim.corals) {
            val f = fork.corals.first { it.id == c.id }
            assertTrue("${c.id}: ${c.pos} vs ${f.pos}", c.pos.distanceTo(f.pos) < 1e-6)
        }
    }
}
