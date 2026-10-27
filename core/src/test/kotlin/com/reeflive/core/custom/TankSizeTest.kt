package com.reeflive.core.custom

import com.reeflive.core.geom.Vec3
import com.reeflive.core.sim.Command
import com.reeflive.core.sim.Simulation
import com.reeflive.core.sim.SpeciesCatalog
import com.reeflive.core.sim.TankDims
import com.reeflive.core.sim.TankShape
import com.reeflive.core.sim.TestTank
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every My tank build is its own size: the box, Tee's rocks cut down to fit, spots inside the glass, saves that keep it. */
class TankSizeTest {
    private fun catalog() = SpeciesCatalog.fromClasspath(listOf("amphiprion_ocellaris", "clibanarius_tricolor"))

    private fun rocksInside(sim: Simulation, label: String) {
        val f = sim.rock.dynamic ?: return
        val l = sim.rock.lengthCm; val d = sim.rock.widthCm
        // sample the live rock field along the glass: no rock may sit in the glass or past it
        var y = 4.0
        while (y < sim.rock.waterLevelCm) {
            var t = 0.0
            while (t <= 1.0) {
                for (p in listOf(Vec3(0.3, y, t * d), Vec3(l - 0.3, y, t * d), Vec3(t * l, y, d - 0.3))) {
                    assertTrue("$label: rock in the glass at $p", f.dist(p) > -0.5)
                }
                t += 0.02
            }
            y += 2.0
        }
    }

    private fun spotsInside(sim: Simulation, label: String) {
        val r = sim.rock
        for (a in r.anchors.all) {
            assertTrue("$label: ${a.name} at ${a.pos} outside the glass", a.pos.x in 0.0..r.lengthCm && a.pos.z in 0.0..r.widthCm && a.pos.y <= r.heightCm)
        }
        for (h in r.holes.all) assertTrue("$label: hole ${h.name} at ${h.mouth}", h.mouth.x in 0.0..r.lengthCm && h.mouth.z in 0.0..r.widthCm)
    }

    @Test fun everyBuildIsItsOwnBox() {
        for (b in TankBuilds.all) {
            val sim = CustomTank.fresh(b, catalog(), TestTank.rock, 12 * 3600.0, seed = 3L)
            val s = b.shape
            assertEquals(b.lengthIn * 2.54, sim.config.lengthCm, 0.06)
            assertEquals(b.depthIn * 2.54, sim.rock.widthCm, 0.06)
            assertEquals(s.waterLevelCm, sim.rock.waterLevelCm, 1e-9)
            assertEquals(s, sim.shape)
            for (j in sim.config.powerheads) assertTrue("${b.id}: jet ${j.name} at ${j.pos}", j.pos.x in 0.0..s.lengthCm && j.pos.z in 0.0..s.depthCm)
            rocksInside(sim, b.id)
            spotsInside(sim, b.id)
        }
        assertTrue("Tee's size is his tank", TankBuilds.LONG_REEF.shape.isStock)
    }

    @Test fun everyBuildsPumpsTurnItOverLikeTeesTank() {
        // research 09 Part B: 20-40×/h is normal; a nano on a 60 gal's powerheads read 83× and "needs help" on day 1
        for (b in TankBuilds.all) {
            val sim = CustomTank.fresh(b, catalog(), TestTank.rock, 12 * 3600.0, seed = 3L)
            assertTrue("${b.id}: ${sim.turnoverPerHour}×/h", sim.turnoverPerHour in 20.0..40.0)
        }
    }

    @Test fun smallTanksGetTeesRocksCutDownAndCentred() {
        val nano = CustomTank.fresh(TankBuilds.NANO, catalog(), TestTank.rock, 12 * 3600.0, seed = 3L)
        val rp = nano.rockPhysics ?: return
        assertTrue("some rock stays in the nano", rp.bodies.isNotEmpty())
        assertTrue("rocks were taken out of the nano", nano.rock.dynamic!!.gone.any { it })
        assertTrue("the kept rocks moved to the nano's glass", nano.scapeShift.x < 0)
        // the 60 is Tee's own: nothing moves
        val sixty = CustomTank.fresh(TankBuilds.LONG_REEF, catalog(), TestTank.rock, 12 * 3600.0, seed = 3L)
        assertEquals(Vec3.ZERO, sixty.scapeShift)
        assertEquals("", sixty.rockState())
    }

    @Test fun fishInANanoStayInsideItsGlass() {
        val sim = CustomTank.fresh(TankBuilds.NANO, catalog(), TestTank.rock, 12 * 3600.0, seed = 9L)
        sim.enqueue(Command.AddOrganism("amphiprion_ocellaris", 5.0, "Nemo"))
        sim.enqueue(Command.AddMany("clibanarius_tricolor", 2, 1.2))
        sim.forceFullBehavior = true
        repeat(600) { sim.stepWall(0.05) }
        for (o in sim.organisms) {
            assertTrue("${o.nickname} at ${o.pos}", o.pos.x in -0.5..(sim.rock.lengthCm + 0.5) && o.pos.z in -0.5..(sim.rock.widthCm + 0.5))
        }
    }

    @Test fun sizedTankReopensWithItsRocks() {
        val sim = CustomTank.fresh(TankBuilds.BREEDER, catalog(), TestTank.rock, 12 * 3600.0, seed = 5L)
        if (sim.rockPhysics == null) return
        sim.enqueue(Command.AddMany("clibanarius_tricolor", 2, 1.2))
        sim.advance(4.0, 0.05)
        val save = CustomTank.capture(sim, CustomTankSave(buildId = TankBuilds.BREEDER.id, seed = 5L), nowMs = 1L)
        val re = CustomTank.restore(CustomTankCodec.decode(CustomTankCodec.encode(save))!!, catalog(), TestTank.rock)
        assertEquals(sim.scapeShift, re.scapeShift)
        assertEquals(sim.rockPhysics!!.bodies.size, re.rockPhysics!!.bodies.size)
        val cave = sim.rock.anchors["CAVE_BIG"]!!.pos; val back = re.rock.anchors["CAVE_BIG"]!!.pos
        assertTrue("cave kept its spot: $cave vs $back", cave.distanceTo(back) < 0.05)
        spotsInside(re, "reopened breeder")
        rocksInside(re, "reopened breeder")
    }

    /** A nano saved before tanks were drawn to size ran in Tee's 48 in box: it opens with its rocks fitted to its glass. */
    @Test fun oldNanoSaveIsFittedOnOpening() {
        val old = CustomTankSave(version = 1, buildId = TankBuilds.NANO.id, seed = 2L, rocks = "")
        val re = CustomTank.restore(old, catalog(), TestTank.rock)
        assertEquals(TankBuilds.NANO.shape, re.shape)
        rocksInside(re, "old nano")
        spotsInside(re, "old nano")
    }

    @Test fun stretchKeepsGlassBandsAndMovesTheTop() {
        val s = TankBuilds.DISPLAY_120.shape
        assertEquals(1.0, s.mapX(1.0), 1e-9)
        assertEquals(TankDims.LENGTH_CM - 1.0 + (s.lengthCm - TankDims.LENGTH_CM), s.mapX(TankDims.LENGTH_CM - 1.0), 1e-9)
        assertEquals(s.waterLevelCm, s.mapY(TankDims.WATER_LEVEL_CM), 1e-9)
        assertEquals(5.0, s.mapY(5.0), 1e-9)
        assertEquals(TankShape.STOCK.map(Vec3(10.0, 20.0, 30.0)), Vec3(10.0, 20.0, 30.0))
    }
}
