package com.reeflive.core.sim

import com.reeflive.core.geom.Vec3
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Every animal the sim can spawn collides (tee-clips clip3: a body that never went through the collision sank into the
 * rock and sand): each species sheet in the catalog (the production `species/` sheets and the test fixtures), spawned
 * alone, has a collision body ([BodyShapes], the fallback included) that [BodyCollision.exempt] does not let into the
 * geometry, and that body placed half into the sand comes out of it through [BodyCollision.resolveEnv]. The only
 * exceptions are the sessile animals listed in [SESSILE], and corals (colonies, not bodies).
 */
class BodyCoverageTest {
    private fun ids(dir: String): List<String> =
        listOf("src/main/resources/$dir", "core/src/main/resources/$dir", "src/test/resources/$dir", "core/src/test/resources/$dir")
            .map(::File).filter { it.isDirectory }.flatMap { d -> d.listFiles()!!.filter { it.name.endsWith(".json") }.map { it.name.removeSuffix(".json") } }

    @Test
    fun everySpawnableSpeciesHasACollidingBody() {
        val sheets = (ids("species").map { it to "species/" } + ids("sim-fixtures/species").map { it to "sim-fixtures/species/" }).distinctBy { it.first }
        assertTrue("species sheets found", sheets.size > 20)
        val problems = ArrayList<String>()
        var animals = 0
        for ((id, prefix) in sheets) {
            val catalog = SpeciesCatalog.fromClasspath(listOf(id), prefix)
            val sp = catalog.all().firstOrNull() ?: continue
            if (sp.isCoral) continue
            val sim = Simulation(TankConfig(seed = 3), catalog, TestTank.rock, 3, 12 * 3600.0, WaterState(), listOf(LivestockSeed(id, "x", id = "x"))).also { it.solidBodies = true }
            val o = sim.organisms.firstOrNull { it.id == "x" } ?: sim.organisms.firstOrNull()
            if (o == null) { problems += "$id: nothing spawned"; continue }
            animals++
            if (o.brainKind.isSessile) { if (id !in SESSILE) problems += "$id: sessile (${o.brainKind}) but not listed"; continue }
            val shape = BodyShapes.of(o.species)
            if (!(shape.maxRadius > 0 && shape.uMax > shape.uMin)) { problems += "$id: empty body shape"; continue }
            // a free body, out in the open on the sand with its belly half in it
            o.posture = 0; o.visible = true; o.spline.override = null
            val at = openSand(sim.rock)
            o.heading = Vec3.X; o.up = Vec3.Y
            o.placeAt(at.withY(sim.rock.floorY(at.x, at.z) - 0.3 - 0.15 * o.lengthCm), Vec3.X)
            o.body.emerging = 0
            if (sim.bodies.exempt(o)) { problems += "$id: exempt (${o.brainKind})"; continue }
            val before = sim.bodies.sectionDepth(o)
            repeat(6) { sim.bodies.resolveEnv(o, 0.05) }
            val after = sim.bodies.sectionDepth(o)
            if (before <= 0.0) problems += "$id: the test body did not start in the sand (%.2f)".format(before)
            else if (after > 0.1) problems += "$id: still %.2f cm in the sand after the resolve (from %.2f)".format(after, before)
        }
        println("BodyCoverageTest: ${sheets.size} sheets, $animals animals")
        if (problems.isNotEmpty()) fail(problems.joinToString("\n"))
    }

    /** A sand point with open water all round (no rock within 12 cm). */
    private fun openSand(r: RockData): Vec3 {
        var best = Vec3(r.lengthCm / 2, 0.0, r.widthCm / 2); var bd = -1.0
        var x = 8.0
        while (x < r.lengthCm - 8) {
            var z = 6.0
            while (z < r.widthCm - 6) {
                val p = Vec3(x, r.floorY(x, z) + 3.0, z)
                val d = r.sdf.dist(p)
                if (d > bd) { bd = d; best = p }
                z += 2.0
            }
            x += 2.0
        }
        return best
    }

    companion object {
        /** Sessile animals: rooted in place, no collision body (BrainKind.isSessile). */
        val SESSILE = setOf("sabellastarte_sp")
    }
}
