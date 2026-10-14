package com.reeflive.core.sim

import com.reeflive.core.geom.HeightField
import com.reeflive.core.geom.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Bodies never merge into geometry or each other (owner request): the elliptic-section capsule chain of every animal
 * stays out of the rock SDF, the sand surface (dynamic sand bed on the drawn sand mesh), the glass and the water
 * surface, and out of the other animals, except in the explicit burrow / buried / den states and cleaner contact.
 */
class BodyCollisionTest {
    private val tankGlb: File? = listOf("../app/src/main/assets/models/tank.glb", "app/src/main/assets/models/tank.glb").map(::File).firstOrNull { it.isFile }

    /** Tee's tank as the app builds it: the baked rock plus the drawn sand mesh as the sand base. */
    private fun teeRock(): RockData = TestTank.rock.forSimulation().also { r ->
        tankGlb?.let { r.sandBase = HeightField.fromGlb(it.readBytes(), r.lengthCm, r.widthCm) }
    }

    private fun teeSim(seed: Long, hour: Double) =
        Simulation(TankConfig(seed = seed), TestTank.catalog, teeRock(), seed, hour * 3600, WaterState(), DefaultStock.all)

    @Test
    fun heightFieldRasterizesASlope() {
        // a ramp: y = 2 at x = 0 rising to 6 at x = 10, over z 0..4
        val tris = floatArrayOf(
            0f, 2f, 0f, 10f, 6f, 0f, 10f, 6f, 4f,
            0f, 2f, 0f, 10f, 6f, 4f, 0f, 2f, 4f,
        )
        val f = HeightField.fromTriangles(tris, 10.0, 4.0, 0.25)!!
        assertEquals(4.0, f.at(5.0, 2.0), 0.05)
        assertEquals(2.0 + 0.4 * 7.5, f.at(7.5, 1.0), 0.05)
    }

    @Test
    fun drawnSandMeshIsTheFloor() {
        val glb = tankGlb ?: return
        val f = HeightField.fromGlb(glb.readBytes(), 121.9, 33.0)
        assertNotNull(f)
        f!!
        // the modelled slope: about 3.5 cm at the front glass, 6 cm at the back, mounds against the rock feet
        assertEquals(3.5, f.at(60.0, 32.5), 1.2)
        assertTrue("back of the bed ${f.at(30.0, 1.0)}", f.at(30.0, 1.0) > 4.5)
        assertTrue("max ${f.max()}", f.max() < 10.0)
    }

    @Test
    fun aBodyInTheSandIsPushedOut() {
        val sim = teeSim(3, 12.0)
        val tang = sim.organisms.first { it.brainKind == BrainKind.TANG }
        val x = 40.0; val z = 30.0
        val floor = sim.rock.floorY(x, z)
        tang.placeAt(Vec3(x, floor + 0.2, z), Vec3.X)
        tang.up = Vec3.Y
        assertTrue(sim.bodies.envPenetration(tang) > 0.3)
        repeat(4) { sim.bodies.resolveEnv(tang, 0.05) }
        assertTrue("penetration ${sim.bodies.envPenetration(tang)}", sim.bodies.envPenetration(tang) < 0.02)
        // the tang (deep body + pelvic and anal fins) now stands its whole half-depth off the sand
        val mid = tang.body.p[7]
        assertTrue(mid.y > sim.rock.floorY(mid.x, mid.z) + 0.25 * tang.lengthCm)
    }

    @Test
    fun twoOverlappingFishSeparate() {
        val sim = teeSim(4, 12.0)
        val a = sim.organisms.first { it.brainKind == BrainKind.TANG }
        val b = sim.organisms.first { it.brainKind == BrainKind.CARDINAL }
        a.placeAt(Vec3(60.0, 40.0, 20.0), Vec3.X); a.up = Vec3.Y
        // side by side, 4 mm apart centre to centre: the bodies overlap along most of their length
        b.placeAt(Vec3(59.0, 40.3, 20.4), Vec3(1.0, 0.0, 0.05)); b.up = Vec3.Y
        a.attached = false; b.attached = false
        assertTrue(sim.bodies.pairPenetration(a, b) > 0.5)
        repeat(6) { sim.bodies.resolvePairs(listOf(a, b), emptyList(), 0.05) }
        assertTrue("overlap ${sim.bodies.pairPenetration(a, b)}", sim.bodies.pairPenetration(a, b) < 0.05)
    }

    @Test
    fun crawlersStandOnTheirSole() {
        // hermit, snail and star models stand on their spine point; the shrimp stand on their legs below it
        val cat = TestTank.catalog
        for (id in listOf("clibanarius_tricolor", "lithopoma_tectum", "nassarius_sp")) {
            cat.all().firstOrNull { it.id == id }?.let { assertEquals(id, 0.0, BodyShapes.of(it).sole, 1e-9) }
        }
        cat.all().firstOrNull { it.brainKind == BrainKind.CLEANER_SHRIMP }?.let { assertTrue(BodyShapes.of(it).sole in 0.18..0.26) }
    }

    /**
     * tee-clips #6: a goby's tail through the front glass while it sits in its burrow. A body in a burrow or the sand
     * (posture 3 / 5) is let into the rock and sand, never through the glass.
     */
    @Test
    fun aBurrowedBodyStaysInsideTheGlass() {
        val sim = teeSim(5, 12.0)
        val goby = sim.organisms.first { it.brainKind == BrainKind.ENGINEER_GOBY }
        val z = sim.rock.widthCm - 0.3 * goby.lengthCm
        // nose in the sand by the front pane, the tail swung out through it
        goby.placeAt(Vec3(50.0, sim.rock.floorY(50.0, z) - 0.5, z), Vec3(0.3, 0.0, -1.0).normalized(Vec3.X))
        goby.posture = 3
        assertTrue(sim.bodies.exempt(goby))
        sim.bodies.resolveEnv(goby, 0.05)
        val b = goby.body
        b.refresh(goby)
        for (i in 0 until BodyState.N) {
            val c = b.p[i] + b.up[i] * b.ctr[i]
            val r = max(b.a[i], b.b[i])
            assertTrue("station $i at z ${c.z} + $r through the front glass (${sim.rock.widthCm})", c.z + r <= sim.rock.widthCm + 0.01)
            assertTrue("station $i through the back glass", c.z - r >= -0.01)
        }
    }

    /**
     * tee-clips #4, #5, #8: a fish standing on its nose on the rock summit, a fish edge-on in the sand, a clown near
     * vertical. A free-swimming fish is never pitched past its species limit (0.57, eels 0.87, sin of pitch) nor rolled
     * past 40 degrees, by day or by night; it slides along a surface level instead.
     */
    @Test
    fun freeSwimmersStayWithinPitchAndRollLimits() {
        var samples = 0
        var worstPitch = 0.0; var worstRoll = 0.0; var where = ""
        for ((seed, hour) in listOf(41L to 11.0, 42L to 23.0)) {
            val sim = teeSim(seed, hour)
            for (k in 0 until (0.25 * 3600 / 0.05).toInt()) {
                sim.advance(0.05, 0.05)
                if (k % 10 != 0) continue
                for (o in sim.organisms) {
                    if (!o.alive || !o.isFish || o.attached || o.posture != 0 || o.spline.override != null || !o.visible) continue
                    samples++
                    val limit = if (o.brainKind == BrainKind.EEL) 0.87 else 0.57
                    val pitch = kotlin.math.abs(o.heading.normalized(Vec3.X).y) - limit
                    val level = Vec3.Y.rejectFrom(o.heading).normalized(Vec3.Y)
                    val roll = 0.766 - (o.up.rejectFrom(o.heading).normalized(level) dot level)
                    if (pitch > worstPitch) { worstPitch = pitch; where = "${o.id} '${o.behaviorName}' pitch ${o.heading} seed $seed t=${sim.simTimeSec}" }
                    if (roll > worstRoll) { worstRoll = roll; where = "${o.id} '${o.behaviorName}' up ${o.up} heading ${o.heading} seed $seed t=${sim.simTimeSec}" }
                }
            }
        }
        println("BodyCollisionTest: pitch/roll over $samples free-swimmer samples, worst pitch excess $worstPitch, worst roll excess (cos) $worstRoll ($where)")
        assertTrue("pitch past the limit by $worstPitch: $where", worstPitch < 0.03)
        assertTrue("rolled past 40 degrees by $worstRoll (cos): $where", worstRoll < 0.03)
    }

    /**
     * Property run: Tee's preset for simulated hours by day and by night (full behaviour at 20 Hz), sampling every
     * body's surface (ellipse rings every ~1/30 BL) against rock, sand, glass and water surface, and every pair of
     * bodies, outside the allowed states. Reports the worst violation and the per-tick cost.
     */
    @Test
    fun noBodyPenetratesGeometryOrAnotherAnimal() {
        val hours = (System.getProperty("reeflive.collisionHours") ?: "1.0").toDouble()
        val tol = 0.2   // 2 mm
        var worstEnv = 0.0; var worstEnvWho = ""
        var worstPair = 0.0; var worstPairWho = ""
        var samples = 0L
        var ticks = 0L
        var nanos = 0L
        var restores = 0L
        val holdsBy = java.util.TreeMap<String, Long>()
        for ((seed, hour) in listOf(11L to 11.0, 12L to 22.5)) {
            val sim = teeSim(seed, hour)
            val dt = 0.05
            val total = (hours * 3600 / dt).toInt()
            for (k in 0 until total) {
                val t0 = System.nanoTime()
                sim.advance(dt, dt)
                nanos += System.nanoTime() - t0
                ticks++
                if (k == total - 1) { restores += sim.bodies.wedgeRestores; sim.bodies.wedgeBy.forEach { (id, n) -> holdsBy.merge(id, n) { x, y -> x + y } } }
                if (k % 20 != 0) continue
                val live = sim.organisms.filter { it.alive && !sim.bodies.exempt(it) }
                for (o in live) {
                    val v = ringPenetration(sim, o)
                    samples++
                    if (v > worstEnv) { worstEnv = v; worstEnvWho = "$o posture ${o.posture} '${o.behaviorName}' at ${o.pos} t=${sim.simTimeSec} [$lastTerm]" }
                }
                for (i in live.indices) for (j in i + 1 until live.size) {
                    val a = live[i]; val b = live[j]
                    if (sim.bodies.touchAllowedForTest(a, b, sim.perception.stations)) continue
                    val v = sim.bodies.pairPenetration(a, b)
                    if (v > worstPair) { worstPair = v; worstPairWho = "$a '${a.behaviorName}' / $b '${b.behaviorName}' t=${sim.simTimeSec}" }
                }
            }
        }
        val usPerTick = nanos / 1000.0 / ticks
        println("BodyCollisionTest: ${"%.1f".format(hours)} h day + night, $samples body samples, worst env %.3f cm ($worstEnvWho), worst pair %.3f cm ($worstPairWho), %.0f µs per sim tick, %d wedge holds (%s)".format(worstEnv, worstPair, usPerTick, restores, holdsBy.entries.sortedBy { -it.value }.take(6).joinToString { "${it.key} ${it.value}" }))
        // what the bodies cost: the same day sim with the collision on and off (warmed up first)
        fun cost(on: Boolean): Double {
            val sim = teeSim(11, 11.0)
            sim.bodies.enabled = on
            repeat(400) { sim.advance(0.05, 0.05) }
            val t0 = System.nanoTime()
            repeat(2000) { sim.advance(0.05, 0.05) }
            return (System.nanoTime() - t0) / 1000.0 / 2000
        }
        cost(true); cost(false)
        val on = cost(true); val off = cost(false)
        println("BodyCollisionTest: sim tick %.0f µs with bodies, %.0f µs without (collision %.0f µs per tick)".format(on, off, on - off))
        assertTrue("body into geometry by %.3f cm: $worstEnvWho".format(worstEnv), worstEnv <= tol)
        assertTrue("bodies overlap by %.3f cm: $worstPairWho".format(worstPair), worstPair <= tol)
    }

    /**
     * Independent check of the support-function resolve: points on each body's surface (12 around every ellipse,
     * stations and midpoints) tested directly against the rock SDF, the sand height, the glass and the surface.
     */
    private var lastTerm = ""

    private fun ringPenetration(sim: Simulation, o: Organism): Double {
        val b = o.body
        b.refresh(o)
        val r = sim.rock
        var worst = 0.0
        for (i in 0 until BodyState.N) for (half in 0..1) {
            if (half == 1 && i == BodyState.N - 1) continue
            val j = if (half == 1) i + 1 else i
            val t = if (half == 1) 0.5 else 0.0
            fun lerpD(x: DoubleArray) = x[i] + (x[j] - x[i]) * t
            val p = b.p[i] + (b.p[j] - b.p[i]) * t
            val up = (b.up[i] + (b.up[j] - b.up[i]) * t).normalized(Vec3.Y)
            val side = (b.side[i] + (b.side[j] - b.side[i]) * t).normalized(Vec3.Z)
            val c = lerpD(b.ctr); val a = lerpD(b.a); val w = lerpD(b.b)
            for (k in 0 until 12) {
                val th = k * Math.PI / 6
                val q = p + up * (c + a * cos(th)) + side * (w * sin(th))
                val terms = doubleArrayOf(-r.sdf.dist(q), r.floorY(q.x, q.z) - q.y, max(-q.x, q.x - r.lengthCm), max(-q.z, q.z - r.widthCm), q.y - r.waterLevelCm)
                for (m in terms.indices) if (terms[m] > worst) { worst = terms[m]; lastTerm = "${TERMS[m]} station $i${if (half == 1) "+" else ""} at $q" }
            }
        }
        return worst
    }

    private companion object { val TERMS = listOf("rock", "sand", "end glass", "front/back glass", "water surface") }
}
