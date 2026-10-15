package com.reeflive.core.custom

import com.reeflive.core.sim.Command
import com.reeflive.core.sim.SpeciesCatalog
import com.reeflive.core.sim.TestTank
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** "My tank": a fresh tank, saved and reopened with every animal's name, personality, growth and health. */
class CustomTankTest {
    private val squid = "euprymna_berryi"
    private val hermit = "clibanarius_tricolor"

    private fun catalog() = SpeciesCatalog.fromClasspath(listOf(squid, hermit, "amphiprion_ocellaris", "zoanthus_sp"))

    @Test fun freshTankIsEmptyAndSizedByTheBuild() {
        val sim = CustomTank.fresh(TankBuilds.NANO, catalog(), TestTank.rock, 9 * 3600.0, seed = 4L)
        assertTrue(sim.organisms.isEmpty() && sim.corals.isEmpty())
        assertEquals(TankBuilds.NANO.liters, sim.water.s.volumeL, 1e-6)
        assertTrue("no skimmer on the nano", !sim.water.skimmer)
        assertEquals(0.0, sim.water.s.algaeHair, 1e-9)
    }

    @Test fun squidAndHermitsSurviveSaveAndReopen() {
        val sim = CustomTank.fresh(TankBuilds.BREEDER, catalog(), TestTank.rock, 12 * 3600.0, seed = 7L)
        sim.enqueue(Command.AddOrganism(squid, 4.0, "Inky"))
        sim.enqueue(Command.AddMany(hermit, 3, 1.2))
        sim.stepWall(0.05)
        sim.advanceAbstract(2 * 86400.0)
        val sq = sim.organisms.first { it.species.id == squid }
        sim.enqueue(Command.Rename(sq.id, "Squidward"))
        sim.stepWall(0.05)

        val base = CustomTankSave(buildId = TankBuilds.BREEDER.id, createdAtMs = 1000L, seed = 7L)
        val text = CustomTankCodec.encode(CustomTank.capture(sim, base, nowMs = 5000L))
        val back = CustomTankCodec.decode(text)!!
        assertEquals(5000L, back.savedAtMs)
        val re = CustomTank.restore(back, catalog(), TestTank.rock)

        assertEquals(sim.organisms.count { it.alive }, re.organisms.size)
        for (o in sim.organisms.filter { it.alive }) {
            val r = re.organisms.first { it.id == o.id }
            assertEquals(o.nickname, r.nickname)
            assertEquals(o.lengthCm, r.lengthCm, 1e-9)
            assertEquals(o.ageSec, r.ageSec, 1e-6)
            assertEquals(o.boldness, r.boldness, 1e-12)
            assertEquals(o.aggression, r.aggression, 1e-12)
            assertEquals(o.health, r.health, 1e-9)
        }
        assertEquals("Squidward", re.organisms.first { it.species.id == squid }.nickname)
        assertEquals(sim.simTimeSec, re.simTimeSec, 1e-6)
        assertEquals(TankBuilds.BREEDER.liters, re.water.s.volumeL, 1e-6)
        assertEquals(sim.water.s.no3, re.water.s.no3, 1e-9)

        // a newcomer after reopening never takes a saved animal's id
        re.enqueue(Command.AddMany(hermit, 2, 1.2))
        re.stepWall(0.05)
        assertEquals(re.organisms.size, re.organisms.map { it.id }.toSet().size)
    }

    /**
     * A save exactly as version 1 wrote it, frozen: every later version must still open it. Also carries a field
     * from the future (ignored) and leaves fields out (defaults), the two ways a format drifts.
     */
    @Test fun myTankKeepsItsOwnRockwork() {
        val sim = CustomTank.fresh(TankBuilds.DEFAULT, catalog(), TestTank.rock, 12 * 3600.0, seed = 5L)
        assertTrue("My tank's rocks can shift", sim.rockPhysics == null || sim.dynamicRocks)
        val rp = sim.rockPhysics ?: return
        sim.enqueue(Command.PlaceRock(com.reeflive.core.sim.DryRockShape.BOULDER, 50.0, 25.0, seed = 3))
        sim.advance(8.0, 0.05)
        val added = rp.added.single()
        val text = CustomTankCodec.encode(CustomTank.capture(sim, CustomTankSave(seed = 5L), nowMs = 1L))
        val re = CustomTank.restore(CustomTankCodec.decode(text)!!, catalog(), TestTank.rock)
        val back = re.rockPhysics!!.added.single()
        assertEquals(added.shape, back.shape)
        assertEquals(added.pos[0], back.pos[0], 0.01)
        assertEquals(added.pos[1], back.pos[1], 0.01)
    }

    @Test fun versionOneSaveStillOpens() {
        val v1 = """
            {"version":1,"name":"Tee's nano","buildId":"nano_cube_20","createdAtMs":1760500000000,"savedAtMs":1760503600000,
             "simTimeSec":172800.0,"seed":3,"speed":1.0,
             "water":{"temp_c":25.8,"sg":1.026,"no3":4.0,"po4":0.03,"some_future_param":9.0},
             "equipment":{"heater_set_c":25.5,"skimmer":0.0},
             "micro":{"HARPACTICOID":1500.0},
             "animals":[
               {"id":"amphiprion_ocellaris#1","speciesId":"amphiprion_ocellaris","nickname":"Nemo","lengthCm":5.5,
                "ageSec":31536000.0,"boldness":0.81,"aggression":0.33,"health":0.9,"futureField":true},
               {"id":"gone#2","speciesId":"species_from_a_later_update","nickname":"Mystery","lengthCm":3.0}
             ],
             "corals":[{"id":"zoa#3","speciesId":"zoanthus_sp","nickname":"Zoas","anchor":"coral_zoa_front_center","tissue":0.8}],
             "nextIds":{"organism":4,"frag":1,"bag":1},
             "addedInV7":{"x":1}}
        """.trimIndent()
        val save = CustomTankCodec.decode(v1)
        assertNotNull(save); save!!
        val cat = catalog()
        val sim = CustomTank.restore(save, cat, TestTank.rock)
        val nemo = sim.organisms.single()
        assertEquals("Nemo", nemo.nickname)
        assertEquals(5.5, nemo.lengthCm, 1e-9)
        assertEquals(0.81, nemo.boldness, 1e-12)
        assertEquals(1.0, nemo.ageYears, 1e-6)
        assertEquals(25.8, sim.water.s.tempC, 1e-9)
        assertEquals(TankBuilds.NANO.liters, sim.water.s.volumeL, 1e-6)
        assertEquals(0.8, sim.corals.single { it.id == "zoa#3" }.tissue, 1e-9)

        // the animal this version cannot load is kept and written back
        val base = CustomTank.baseAfterRestore(save, cat)
        val again = CustomTankCodec.decode(CustomTankCodec.encode(CustomTank.capture(sim, base, 2L)))!!
        assertEquals("Mystery", again.dormant.single().nickname)
        assertEquals("Tee's nano", again.name)
    }

    @Test fun corruptTextIsNotASave() {
        assertNull(CustomTankCodec.decode("not json"))
        assertNull(CustomTankCodec.decode("[1,2]"))
    }

    @Test fun catchUpIsCapped() {
        assertEquals(3600.0, CustomTank.catchUpSeconds(0L, 3_600_000L), 1e-6)
        assertEquals(24 * 3600.0, CustomTank.catchUpSeconds(0L, 30L * 86_400_000L), 1e-6)
        assertEquals(0.0, CustomTank.catchUpSeconds(5_000L, 1_000L), 1e-9)
    }
}
