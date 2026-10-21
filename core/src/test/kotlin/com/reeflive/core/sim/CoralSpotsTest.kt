package com.reeflive.core.sim

import com.reeflive.core.custom.CustomTank
import com.reeflive.core.custom.CustomTankCodec
import com.reeflive.core.custom.CustomTankSave
import com.reeflive.core.custom.TankBuilds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** My tank: a bought coral waits for the keeper to pick one of its best spots ([CoralSpots], [CoralPicks]). */
class CoralSpotsTest {
    private val ids = listOf("zoanthus_sp", "capnella_sp", "discosoma_sp", "gracilaria_sp", "tubipora_musica")
    private fun catalog() = SpeciesCatalog.fromClasspath(ids)
    private fun myTank() = CustomTank.fresh(TankBuilds.DEFAULT, catalog(), TestTank.rock, 12 * 3600.0, seed = 3L)

    @Test fun spotsAreSpreadOutRankedAndFitTheSpecies() {
        val s = myTank()
        for (id in ids) {
            val sp = s.species[id]
            val spots = CoralSpots.suggest(s, sp)
            assertEquals(id, CoralSpots.OFFER, spots.size)
            assertTrue("best first", spots.zipWithNext().all { (a, b) -> a.score >= b.score })
            for ((i, a) in spots.withIndex()) for (b in spots.drop(i + 1)) assertTrue(a.pos.distanceTo(b.pos) >= CoralSpots.APART_CM)
            for (p in spots) {
                assertTrue("$id: upright surface", p.normal.y >= CoralSpots.MIN_UP)
                assertTrue("$id: room to grow under the surface", p.pos.y <= s.rock.waterLevelCm - CoralSpots.HEADROOM_CM)
                assertTrue("$id: says why", p.reason.isNotBlank() && p.reason.length < 60)
                if (CoralSpots.needs(sp).ground == "ROCK") assertTrue("$id is a rock coral", !p.onSand)
            }
        }
        // a shade-loving mushroom ends up in less light than the high-light organ pipe
        val shroom = CoralSpots.suggest(s, s.species["discosoma_sp"]).first()
        val pipe = CoralSpots.suggest(s, s.species["tubipora_musica"]).first()
        assertTrue("mushroom ${shroom.light} vs organ pipe ${pipe.light}", shroom.light < pipe.light)
    }

    @Test fun spotsKeepClearOfStingingNeighbours() {
        val s = myTank()
        val kenya = s.species["capnella_sp"]
        val k = CoralSpots.suggest(s, kenya).first()
        Stocking.placeCoral(s, kenya, k.pos)
        val zoa = s.species["zoanthus_sp"]
        val spots = CoralSpots.suggest(s, zoa)
        val reach = CoralSpots.reachCm(kenya)
        val kc = s.corals.single()
        for (p in spots) {
            val gap = p.pos.distanceTo(kc.pos) - CoralGrowth.extent(kc) - CoralSpots.newExtent(zoa)
            assertTrue("zoa spot ${p.pos} only ${"%.1f".format(gap)} cm from the Kenya", gap >= reach * 0.5)
        }
    }

    @Test fun boughtCoralWaitsForItsSpotThenGrowsThere() {
        val s = myTank()
        s.enqueue(Command.FloatBag("zoanthus_sp", 2, x = 40.0))
        s.enqueue(Command.HurryBags(true, openNow = true))
        s.advance(AcclimationBags.T_END + 5.0, behaviorDt = 0.05)
        assertTrue("nothing dropped at random", s.corals.isEmpty())
        val pick = assertNotNullPick(s.snapshot().coralPick)
        assertEquals("zoanthus_sp", pick.speciesId)
        assertEquals(2, pick.waiting)
        assertEquals(CoralSpots.OFFER, pick.spots.size)

        // the keeper taps the second spot
        val chosen = pick.spots[1]
        s.enqueue(Command.PlaceCoral(1))
        s.stepWall(0.05)
        val c = s.corals.single()
        assertTrue(c.pos.distanceTo(com.reeflive.core.geom.Vec3(chosen.x.toDouble(), chosen.y.toDouble(), chosen.z.toDouble())) < 0.01)
        val next = assertNotNullPick(s.snapshot().coralPick)
        assertEquals(1, next.waiting)
        assertTrue("the placed colony is not offered again", next.spots.all { it.x != chosen.x || it.z != chosen.z })

        // the second one is saved while it waits and is still waiting after reopening
        val save = CustomTank.capture(s, CustomTankSave(), 1000L)
        assertEquals(listOf("zoanthus_sp"), save.toPlace)
        val back = CustomTank.restore(CustomTankCodec.decode(CustomTankCodec.encode(save))!!, catalog(), TestTank.rock)
        assertEquals(1, back.corals.size)
        assertEquals(1, back.snapshot().coralPick?.waiting)
        // "pick for me": the best spot
        val best = back.snapshot().coralPick!!.spots[0]
        back.enqueue(Command.PlaceCoral(-1))
        back.stepWall(0.05)
        assertEquals(2, back.corals.size)
        assertTrue(back.corals.any { kotlin.math.abs(it.pos.x - best.x) < 0.01 && kotlin.math.abs(it.pos.z - best.z) < 0.01 })
        assertNull(back.snapshot().coralPick)
    }

    @Test fun deeperAndShadedSpotsAreDimmer() {
        val r = TestTank.rock
        val up = com.reeflive.core.geom.Vec3.Y
        val high = CoralSpots.lightAt(r, com.reeflive.core.geom.Vec3(10.0, r.waterLevelCm - 10, 10.0), up)
        val low = CoralSpots.lightAt(r, com.reeflive.core.geom.Vec3(10.0, r.sandTopCm + 1, 10.0), up)
        assertTrue("$high > $low", high > low)
    }

    private fun assertNotNullPick(p: CoralPickPose?): CoralPickPose { assertNotNull(p); return p!! }
}
