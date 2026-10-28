package com.reeflive.core.sim

import com.reeflive.core.sim.HeatModel.Stage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.acos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

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

    /** Model up (y) and forward (x) of pose frame [k], rotated into the world. */
    private fun OrganismPose.axis(k: Int, x: Float, y: Float): FloatArray {
        val o = k * 4
        val qx = frames[o]; val qy = frames[o + 1]; val qz = frames[o + 2]; val qw = frames[o + 3]
        val v = floatArrayOf(x, y, 0f)
        val tx = 2 * (qy * v[2] - qz * v[1]); val ty = 2 * (qz * v[0] - qx * v[2]); val tz = 2 * (qx * v[1] - qy * v[0])
        return floatArrayOf(v[0] + qw * tx + (qy * tz - qz * ty), v[1] + qw * ty + (qz * tx - qx * tz), v[2] + qw * tz + (qx * ty - qy * tx))
    }

    private fun deg(a: FloatArray, b: FloatArray): Double {
        val la = sqrt((a[0] * a[0] + a[1] * a[1] + a[2] * a[2]).toDouble()); val lb = sqrt((b[0] * b[0] + b[1] * b[1] + b[2] * b[2]).toDouble())
        if (la < 1e-6 || lb < 1e-6) return 0.0
        return Math.toDegrees(acos(((a[0] * b[0] + a[1] * b[1] + a[2] * b[2]) / (la * lb)).coerceIn(-1.0, 1.0)))
    }

    /** Largest turn between neighbouring spine segments and largest twist of the up axis between neighbouring frames. */
    private fun OrganismPose.kinks(): Pair<Double, Double> {
        var bend = 0.0; var twist = 0.0
        for (k in 1 until 15) {
            val a = FloatArray(3) { spine[k * 3 + it] - spine[k * 3 - 3 + it] }
            val b = FloatArray(3) { spine[k * 3 + 3 + it] - spine[k * 3 + it] }
            bend = max(bend, deg(a, b))
        }
        for (k in 0 until 15) twist = max(twist, deg(axis(k, 0f, 1f), axis(k + 1, 0f, 1f)))
        return bend to twist
    }

    /**
     * A clown lying on the bottom, on its side and in its death throes keeps one continuous body at any sim speed
     * (QA: at 1000x the resting body broke into two or three kinked pieces), the death-throe spasm signal stays in
     * 0..1, and on its side the drawn frames are rolled over (QA: never seen on its side).
     */
    @Test
    fun heatStagesKeepTheBodyWholeAtAnySpeed() {
        for (speed in listOf(1.0, 60.0, 1000.0)) for (stage in listOf(Stage.LETHARGIC, Stage.LOE, Stage.DEATH)) {
            val sim = tank()
            val o = sim.clown()
            sim.speed = speed
            // 2 min of sim time to settle into the pose (eased in from the swimming shape), then 2 more checked
            val settle = (120.0 / (speed * 0.05)).toInt().coerceAtLeast(60)
            var worstBend = 0.0; var worstTwist = 0.0; var minRoll = 180.0
            for (i in 0 until 2 * settle) {
                o.heat.stage = stage; o.heat.peak = stage; o.heat.stageSince = sim.simTimeSec
                if (stage == Stage.DEATH) o.heat.spasmUntil = sim.simTimeSec + 1e6   // held in the throes
                sim.stepWall(0.05)
                if (i < settle) continue
                val p = sim.latest.organisms.first { it.id == o.id }
                assertTrue("$stage at ${speed}x: spasm ${p.spasm} in 0..1", p.spasm in 0f..1f)
                val (b, t) = p.kinks()
                worstBend = max(worstBend, b); worstTwist = max(worstTwist, t)
                minRoll = min(minRoll, deg(p.axis(0, 0f, 1f), floatArrayOf(0f, 1f, 0f)))
            }
            assertTrue("$stage at ${speed}x: body folded $worstBend° between segments", worstBend < 50.0)
            assertTrue("$stage at ${speed}x: frames twisted $worstTwist° between stations", worstTwist < 15.0)
            if (stage >= Stage.LOE) assertTrue("$stage at ${speed}x: rolled onto its side (least roll $minRoll°)", minRoll > 55.0)
            assertTrue("$stage at ${speed}x: still alive while held", o.alive)
        }
    }

    /** A dead fish stays in the tank as a whole body lying on its side on the bottom, also at fast-forward. */
    @Test
    fun deadFishStaysWholeOnTheBottomAtFastForward() {
        val sim = tank()
        val o = sim.clown()
        HealthModel.kill(o, "test", sim.simTimeSec, mutableListOf())
        sim.speed = 1000.0
        repeat(400) { sim.stepWall(0.05) }   // ~5.5 h
        val p = sim.latest.organisms.firstOrNull { it.id == o.id }
        assertTrue("the body is still in the tank", p != null && p.visible)
        p!!
        val (bend, twist) = p.kinks()
        assertTrue("dead body whole: bend $bend°, twist $twist°", bend < 50.0 && twist < 15.0)
        val low = sim.rock.sandTopCm + (sim.rock.waterLevelCm - sim.rock.sandTopCm) / 3
        assertTrue("sunk to the bottom: y=${o.pos.y}", o.pos.y < low)
        assertTrue("on its side", deg(p.axis(0, 0f, 1f), floatArrayOf(0f, 1f, 0f)) > 55.0)
    }

    /** The heat sequence never skips the loss of equilibrium: a fish is on its side for a while before the throes. */
    @Test
    fun aFishLosesEquilibriumBeforeItsDeathThroes() {
        val sim = tank()
        val o = sim.clown()
        o.heat.stage = Stage.LETHARGIC; o.heat.peak = Stage.LETHARGIC; o.heat.stageSince = sim.simTimeSec
        // far past CTmax: the temperature trigger alone says dying
        sim.enqueue(Command.SetParam("temp", 40.0))
        val seen = ArrayList<Stage>()
        var guard = 0
        while (o.alive && guard++ < 10) { sim.advanceAbstract(60.0); if (seen.lastOrNull() != o.heat.stage) seen += o.heat.stage }
        assertTrue("stages $seen", seen.indexOf(Stage.LOE) in 0 until seen.indexOf(Stage.DEATH).let { if (it < 0) Int.MAX_VALUE else it })
        assertFalse("still dies of the heat", o.alive)
        assertEquals("heat", o.deathCause)
    }

    /**
     * Sandbox "make a fish sick": the infection is still there with its signs showing after a full sim day at the
     * fastest speed (QA: infections seemed to vanish within a minute of fast-forward), and a second one picks a fish
     * that is not sick yet.
     */
    @Test
    fun sandboxInfectionLastsDaysAtFastForward() {
        data class Case(val d: Disease, val symptom: Symptom, val field: ((OrganismPose) -> Float)?)
        val cases = listOf(
            Case(Disease.ICH, Symptom.WHITE_SPOTS) { it.ich },
            Case(Disease.VELVET, Symptom.GOLD_DUST) { it.velvet },
            Case(Disease.BACTERIAL, Symptom.FRAYED_FINS) { it.finRot },
            Case(Disease.HLLE, Symptom.PITTED_SKIN) { it.hlle },
            Case(Disease.BROOKLYNELLA, Symptom.MUCUS) { it.mucus },
            Case(Disease.URONEMA, Symptom.LESIONS, null),
            Case(Disease.FLUKES, Symptom.FLASHING, null),
        )
        for (c in cases) {
            val sim = tank()
            sim.enqueue(Command.Infect(null, c.d, HealthModel.SANDBOX_INFECT_STAGE))
            sim.stepWall(0.05)
            val o = sim.organisms.single { it.disease == c.d }
            sim.speed = 1000.0
            val until = sim.simTimeSec + 86400.0
            while (sim.simTimeSec < until) sim.stepWall(0.05)
            val snap = sim.latest
            assertEquals("${c.d}: still infected after a day (stage ${o.diseaseStage})", c.d, o.disease)
            assertTrue("${c.d}: ${c.symptom} still showing, got ${snap.symptoms(o)}", c.symptom in snap.symptoms(o))
            c.field?.let { f -> assertTrue("${c.d}: skin sign still drawn (${f(snap.pose(o))})", f(snap.pose(o)) > 0.2f) }
        }
        // a second "make a fish sick" adds a patient
        val sim = tank()
        sim.enqueue(Command.Infect(null, Disease.VELVET, HealthModel.SANDBOX_INFECT_STAGE))
        sim.stepWall(0.05)
        sim.enqueue(Command.Infect(null, Disease.HLLE, HealthModel.SANDBOX_INFECT_STAGE))
        sim.stepWall(0.05)
        assertEquals("two sick fish", setOf(Disease.VELVET, Disease.HLLE), sim.organisms.mapNotNull { it.disease }.toSet())
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
