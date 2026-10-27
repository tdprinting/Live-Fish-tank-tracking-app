package com.reeflive.core.sim

import com.reeflive.core.sim.HeatModel.Stage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sick and dying fish must reach the renderer (SIMULATION §3.5, §10): every heat stage and every disease's visible
 * signs are in the snapshot, as the pose's render fields and the fish card's symptom list.
 */
class FishHealthVisualsTest {
    private fun tank() = Simulation(TankConfig(seed = 3L), TestTank.catalog, TestTank.rock, 3L, 12 * 3600.0, WaterState(), DefaultStock.all)
        .also { it.enqueue(Command.SetParam("temp", 26.0)); it.advanceAbstract(120.0) }

    private fun Simulation.clown() = organisms.first { it.alive && it.brainKind == BrainKind.CLOWN }

    private fun SimSnapshot.pose(o: Organism) = organisms.first { it.id == o.id }
    private fun SimSnapshot.symptoms(o: Organism) = stats.first { it.id == o.id }.symptoms

    /** Puts [o] into heat [stage] (held there: the stage only steps down after hours) and runs one chemistry tick. */
    private fun Simulation.heatStage(o: Organism, stage: Stage): SimSnapshot {
        o.heat.stage = stage; o.heat.peak = stage; o.heat.stageSince = simTimeSec
        advanceAbstract(60.0)
        return snapshot()
    }

    @Test
    fun heatStagesAreInTheSnapshot() {
        val expect = mapOf(
            Stage.AGITATED to Symptom.ERRATIC,
            Stage.GASPING to Symptom.RAPID_GILLS,
            Stage.LETHARGIC to Symptom.ON_BOTTOM,
            Stage.LOE to Symptom.LOSS_OF_EQUILIBRIUM,
        )
        for ((stage, symptom) in expect) {
            val sim = tank()
            val o = sim.clown()
            val snap = sim.heatStage(o, stage)
            val pose = snap.pose(o)
            assertTrue("$stage: alive", pose.alive)
            assertEquals("$stage: pose carries the stage", stage.ordinal, pose.heatStage)
            assertTrue("$stage: shows $symptom, got ${snap.symptoms(o)}", symptom in snap.symptoms(o))
            assertEquals("$stage: no spasms yet", 0f, pose.spasm, 0f)
            when (stage) {
                Stage.GASPING -> { assertTrue("gasping pumps the gills fast", pose.gillRate >= 3f); assertEquals(1f, pose.gill, 1e-6f) }
                Stage.LETHARGIC -> {
                    assertTrue("lethargic: clamped fins", pose.finClamp >= 0.8f && Symptom.CLAMPED_FINS in snap.symptoms(o))
                    assertTrue("lethargic: paler", pose.desat >= 0.25f)
                }
                Stage.LOE -> assertTrue("on its side: paler", pose.desat >= 0.35f)
                else -> {}
            }
        }
    }

    @Test
    fun heatDeathSpasmsThenDies() {
        val sim = tank()
        val o = sim.clown()
        val snap = sim.heatStage(o, Stage.DEATH)
        val pose = snap.pose(o)
        assertTrue("spasms before it dies", pose.alive)
        assertEquals(Stage.DEATH.ordinal, pose.heatStage)
        assertEquals(1f, pose.spasm, 0f)
        assertTrue(Symptom.SPASMS in snap.symptoms(o))
        sim.advanceAbstract(60.0)
        val dead = sim.snapshot().pose(o)
        assertFalse("dead after the death throes (${HeatModel.SPASM_SEC} s)", dead.alive)
        assertEquals(0f, dead.spasm, 0f)
        assertTrue("dead fish fade", dead.desat >= 0.8f)
        assertEquals("heat", o.deathCause)
    }

    @Test
    fun deadFishSinksOnItsSideBeforeItBloats() {
        val sim = tank()
        val o = sim.clown()
        // die in open water, over a clear column of sand (a body that falls under a rock ledge stays there)
        val r = sim.rock
        val z = r.widthCm - 5
        val x = (10..110 step 4).map { it.toDouble() }.first { x ->
            var y = r.sandTopCm + 1
            var clear = true
            while (y < r.waterLevelCm - 1) { if (r.sdf.dist(com.reeflive.core.geom.Vec3(x, y, z)) < 4) clear = false; y += 1 }
            clear
        }
        o.placeAt(com.reeflive.core.geom.Vec3(x, r.waterLevelCm - 10, z), com.reeflive.core.geom.Vec3.X)
        HealthModel.kill(o, "test", sim.simTimeSec, mutableListOf())
        sim.advance(5 * 60.0, behaviorDt = 0.5)
        // (on the sand or on the rock it fell onto: in the bottom third, never up at the surface)
        val low = sim.rock.sandTopCm + (sim.rock.waterLevelCm - sim.rock.sandTopCm) / 3
        assertTrue("sunk, not at the surface: y=${o.pos.y}", o.pos.y < low)
        assertTrue("lying on its side (up ${o.up})", kotlin.math.abs(o.up.y) < 0.5)
        sim.advanceAbstract(10 * 3600.0)
        assertTrue("still on the bottom before it bloats: y=${o.pos.y}", o.pos.y < low)
        // (abstract steps lift it slowly: give it a few hours to reach the surface)
        sim.advanceAbstract(Simulation.BLOAT_SEC - 10 * 3600.0 + 4 * 3600.0)
        assertTrue("a bloated clown floats: y=${o.pos.y}", o.pos.y > sim.rock.waterLevelCm - 5)
    }

    @Test
    fun diseaseSignsAreInTheSnapshot() {
        data class Case(val d: Disease, val symptom: Symptom, val field: (OrganismPose) -> Float)
        val cases = listOf(
            Case(Disease.ICH, Symptom.WHITE_SPOTS) { it.ich },
            Case(Disease.VELVET, Symptom.GOLD_DUST) { it.velvet },
            Case(Disease.BACTERIAL, Symptom.FRAYED_FINS) { it.finRot },
            Case(Disease.HLLE, Symptom.PITTED_SKIN) { it.hlle },
            Case(Disease.BROOKLYNELLA, Symptom.MUCUS) { it.mucus },
        )
        for (c in cases) {
            val sim = tank()
            val o = sim.clown()
            sim.enqueue(Command.Infect(o.id, c.d, 0.35))
            sim.advanceAbstract(60.0)
            val snap = sim.snapshot()
            val pose = snap.pose(o)
            val sym = snap.symptoms(o)
            assertTrue("${c.d}: shows ${c.symptom}, got $sym", c.symptom in sym)
            assertTrue("${c.d}: render intensity ${c.field(pose)}", c.field(pose) > 0.2f)
            // only its own skin sign
            val others = cases.filter { it !== c }.map { it.field(pose) }
            assertTrue("${c.d}: other skin signs stay off $others", others.all { it == 0f })
            // shared stress signs: velvet and Brooklynella clamp the fins and speed the breathing, fin rot clamps
            if (c.d == Disease.VELVET || c.d == Disease.BROOKLYNELLA) {
                assertTrue("${c.d}: clamped fins", Symptom.CLAMPED_FINS in sym && pose.finClamp >= 0.6f)
                assertTrue("${c.d}: rapid breathing", Symptom.RAPID_GILLS in sym && pose.gillRate >= 2.4f)
            }
            if (c.d == Disease.BACTERIAL) assertTrue("fin rot: clamped fins", Symptom.CLAMPED_FINS in sym)
        }
    }
}
