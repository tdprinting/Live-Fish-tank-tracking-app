package com.reeflive.core.sim

import com.reeflive.core.ai.PeekerBlennyBrain
import com.reeflive.core.geom.BodySpline
import com.reeflive.core.geom.Quat
import com.reeflive.core.geom.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * No animal enters solid rock (SIMULATION §24): over long runs of the full default stock, by day, afternoon and
 * night, neither the head nor any spine station of any organism lies inside the baked rock SDF, except within the
 * open volume of a modelled hole that fits its body (RockHole.fits; the 1 cm SDF grid reads such a hole as rock
 * no deeper than RockGuard.HOLE_SLACK). The hole-dwelling blennies still use their real
 * holes, backing in tail first.
 */
class RockPenetrationTest {
    private val pos = Array(BodySpline.POINTS) { Vec3.ZERO }
    private val fr = Array(BodySpline.POINTS) { Quat.IDENTITY }

    @Test
    fun noBodyInsideRockExceptFittingHoles() {
        val bad = ArrayList<String>()
        var checks = 0L
        var inHoleStations = 0L
        var backIns = 0
        for ((seed, hour) in listOf(1L to 9.0, 2L to 14.0, 3L to 20.5)) {
            val sim = TestTank.sim(DefaultStock.all, seed = seed, startHour = hour)
            val rock = sim.rock
            assertTrue("the rock carries hole descriptors", !rock.holes.isEmpty())
            var tick = 0
            sim.advance(1800.0) {
                if (++tick % 2 != 0) return@advance
                for (o in sim.organisms) {
                    if (!o.alive) continue
                    val br = RockHole.bodyRadius(o)
                    val blenny = o.brainKind == BrainKind.LAWNMOWER_BLENNY || o.brainKind == BrainKind.PEEKER_BLENNY
                    o.spline.sample(o.up, pos, fr)
                    for (i in -1 until BodySpline.POINTS) {
                        val p = if (i < 0) o.pos else pos[i]
                        checks++
                        // a blenny station inside the open volume of a blenny-size HOLE that fits it (the holes cut on the resized
                        // tank read as open water in the 1 cm SDF, so "in rock but in a fitting hole" no longer sees them)
                        if (blenny && rock.holes.all.any { it.kind == HoleKind.HOLE && it.fits(br) && it.contains(p, 0.0) }) inHoleStations++
                        val d = rock.sdf.dist(p)
                        if (d >= -TOL_CM || p.y < sim.sand.surfaceY(p.x, p.z) + RockGuard.SAND_SKIN) continue
                        if (!RockGuard.blocked(rock, p, br, TOL_CM) { x, z -> sim.sand.surfaceY(x, z) }) continue
                        if (bad.size < 12) bad += "seed $seed t=${"%.0f".format(sim.simTimeSec)} ${o.species.id}/${o.behaviorName}/p${o.posture} " +
                            "station $i d=${"%.2f".format(d)} at $p"
                        else if (bad.size == 12) bad += "…"
                    }
                }
            }
            backIns += sim.organisms.mapNotNull { sim.agentOf(it)?.brain as? PeekerBlennyBrain }
                .sumOf { it.inHole.backIns }
        }
        assertEquals("stations inside rock outside fitting holes (of $checks):\n" + bad.joinToString("\n"), 0, bad.size)
        assertTrue("blennies sat in their holes ($inHoleStations stations)", inHoleStations > 0)
        assertTrue("the tailspot backed into a hole tail first ($backIns)", backIns > 0)
    }

    @Test
    fun everyHoleHomeFitsItsAnimal() {
        val sim = TestTank.sim(DefaultStock.all, seed = 4L, startHour = 10.0)
        for (h in sim.rock.holes.all) for (sp in h.species) {
            val o = sim.organisms.firstOrNull { it.species.id == sp } ?: continue
            assertTrue("${h.name} (r ${h.radius}) fits $sp (half-thickness ${RockHole.bodyRadius(o)})", h.fits(o))
        }
    }

    companion object {
        /** The baked SDF is a 1 cm grid read trilinearly: a station this close to the surface counts as outside (cm). */
        const val TOL_CM = 0.3
    }
}
