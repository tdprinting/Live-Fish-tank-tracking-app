package com.reeflive.core.sim

import com.reeflive.core.geom.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

/**
 * Hermit crabs sit on the rock, sand and glass they walk on (SIMULATION §19.6, owner clip C 1.5–7 s: shell standing
 * off a vertical face with legs and antennae in the mesh, hanging over a sand slope). Every tick of a multi-hour run
 * of the full stock, every seated hermit's shell rests on the surface ([CrawlSurface]: rock SDF, live sand bed, glass)
 * with at most a 0.3 cm gap under it (or on the hard decor or equipment it climbed onto: [StaticObstacles], whose solids
 * the body collision holds it out of), and no shell point, planted foot tip or knee is more than 0.1 cm inside.
 */
class HermitSeatTest {

    private class Stats {
        var samples = 0L
        var maxGap = 0.0
        var worstShell = 0.0
        var worstFoot = 0.0
        var worstKnee = 0.0
        var gapFails = 0
        var shellFails = 0
        var footFails = 0
        var kneeFails = 0
        var planted = 0L
        var tucked = 0L
        var onRock = 0L
        var steep = 0L
        val notes = ArrayList<String>()
        val counts = HashMap<String, Int>()
        val worst = HashMap<String, Pair<Double, String>>()
        fun note(s: String) {
            val k = s.substringBefore(' ') + (if (s.startsWith("foot") || s.startsWith("knee")) "" else "")
            val v = s.split(" ").firstNotNullOfOrNull { it.toDoubleOrNull() } ?: 0.0
            val key = k.substringBefore(' ')
            val n = counts.getOrElse(key) { 0 }; counts[key] = n + 1; if (n < 3) notes += s
            if (v > (worst[key]?.first ?: -1.0)) worst[key] = v to s
        }
    }

    private fun check(sim: Simulation, st: Stats) {
        val surf = sim.crawlSurface
        for (o in sim.organisms) {
            if (o.brainKind != BrainKind.HERMIT || !o.alive || !o.visible) continue
            val seat = o.seat ?: continue
            if (!o.seatOn || seat.blend < 1.0) continue
            val m = seat.hermit!!
            val L = o.lengthCm
            st.samples++
            if (sim.rock.sdf.dist(seat.pos) < 0.5) st.onRock++
            if (seat.up.y < 0.5) st.steep++
            var minD = Double.MAX_VALUE
            // the body's contact points: shell, shield and the coxae
            val c = seat.shape.contacts
            var i = 0
            // (gap: to the rock, sand or glass, or to hard decor or equipment it climbed onto: resting on a heater is resting)
            var minS = Double.MAX_VALUE
            while (i < c.size) {
                val w = seat.world(c[i], c[i + 1], c[i + 2], L)
                val d = surf.dist(w)
                minD = min(minD, d)
                minS = min(minS, min(d, 0.5 - sim.obstacles.depth(w, 0.5)))
                i += 3
            }
            st.maxGap = max(st.maxGap, minS)
            st.worstShell = min(st.worstShell, minD)
            val where = "t=%.0f %s %s p%d at %s up %s simPos %s d0 %.2f sdf %.2f".format(sim.simTimeSec, o.nickname, o.behaviorName, o.posture, seat.pos, seat.up, o.pos, surf.dist(o.pos), sim.rock.sdf.dist(o.pos))
            if (minS > GAP_MAX) { st.gapFails++; st.note("gap %.2f cm $where".format(minS)) }
            if (minD < -PEN_MAX) { st.shellFails++; st.note("shell %.2f cm inside $where".format(-minD)) }
            if (!seat.legsPlanted) continue
            st.planted++
            val rig = o.limbRig as HermitLegs
            for (k in 0 until 4) {
                // (a leg wedged where it has no clear pose is drawn pulled into the shell)
                if (seat.legTucked[k]) { st.tucked++; continue }
                val d = surf.dist(seat.footTip[k])
                st.worstFoot = min(st.worstFoot, d)
                if (d < -PEN_MAX) { st.footFails++; st.note("foot $k %.2f cm inside swing=${seat.footSwing[k]} target %.2f miss %.2f retract %.2f $where".format(-d, surf.dist(seat.footTarget[k]), seat.footTip[k].distanceTo(seat.footTarget[k]), rig.retract)) }
                val knee = seat.posedLeg(m, k, m.knee, k * 3, CrawlerSeat.KNEE_T, L, rig.out)
                val dk = surf.dist(knee)
                st.worstKnee = min(st.worstKnee, dk)
                if (dk < -PEN_MAX) { st.kneeFails++; st.note("knee $k %.2f cm inside $where".format(-dk)) }
            }
        }
    }

    private fun run(seed: Long, hour: Double, hours: Double, dt: Double): Stats {
        val sim = TestTank.solidSim(DefaultStock.all, seed = seed, startHour = hour)
        val st = Stats()
        var left = hours * 3600
        while (left > 1e-6) {
            val chunk = minOf(600.0, left)
            sim.advance(chunk, behaviorDt = dt) { check(sim, st) }
            left -= chunk
        }
        return st
    }

    @Test
    fun hermitsRestOnTheSurfaceWithoutClipping() {
        // an evening into the night (the hermits roam the rock after dark) at the live tick, and a day at 10x
        val runs = listOf("seed 7 dusk 20 Hz" to run(7, 18.0, 3.0, 0.05), "seed 8 day 10x" to run(8, 9.0, 4.0, 0.5))
        for ((name, st) in runs) {
            println("[$name] %d samples (%d on rock, %d on faces steeper than 60°, %d with legs planted, %d leg-ticks pulled in): max gap %.3f cm, deepest shell %.3f, foot %.3f, knee %.3f cm"
                .format(st.samples, st.onRock, st.steep, st.planted, st.tucked, st.maxGap, -st.worstShell, -st.worstFoot, -st.worstKnee))
            st.notes.forEach { println("   $it") }
            st.worst.values.forEach { println("   WORST ${it.second}") }
            println("   counts ${st.counts}")
        }
        for ((name, st) in runs) {
            assertTrue("$name: hermits seated", st.samples > 1000)
            assertEquals("$name: shell gap over $GAP_MAX cm", 0, st.gapFails)
            assertEquals("$name: shell inside the surface", 0, st.shellFails)
            assertEquals("$name: foot tips inside the surface", 0, st.footFails)
            assertEquals("$name: knees inside the surface", 0, st.kneeFails)
        }
        assertTrue("some time on the rock", runs.sumOf { it.second.onRock } > 0)
    }

    @Test
    fun aHermitSettlesOntoItsSurfaceAndThePoseCarriesTheSeat() {
        val sim = TestTank.solidSim(listOf(TestTank.seed("hermit1")), seed = 3L, startHour = 23.0)
        val o = sim.organisms.first { it.brainKind == BrainKind.HERMIT }
        sim.advance(120.0, behaviorDt = 0.05)
        val seat = o.seat!!
        assertTrue("seated", o.seatOn)
        val surf = sim.crawlSurface
        val m = seat.hermit!!
        var minD = Double.MAX_VALUE
        var i = 0
        while (i < m.contacts.size) { minD = min(minD, surf.dist(seat.world(m.contacts[i], m.contacts[i + 1], m.contacts[i + 2], o.lengthCm))); i += 3 }
        assertTrue("shell on the surface (%.3f cm)".format(minD), minD in -0.1..0.3)
        // the drawn origin is on the surface, not surfaceOffset above it like the sim position
        assertTrue("seat on the surface (%.3f cm)".format(surf.dist(seat.pos)), surf.dist(seat.pos) < 0.35)
        if (seat.legsPlanted) for (k in 0 until 4) assertTrue("foot $k on the surface", surf.dist(seat.footTip[k]) in -0.1..0.6)
        val pose = o.toPose()
        assertEquals(HermitLegs.COUNT * 4, pose.limbs.size)
        assertEquals(seat.pos.x, pose.spine[0].toDouble(), 1e-3)
        assertTrue("legs planted flag", !seat.legsPlanted || pose.limbs[14 * 4 + 3] == 1f)
        assertTrue("drawn up is the seat's", seat.up.distanceTo(o.seatUp) < 1e-9)
    }

    companion object {
        const val GAP_MAX = 0.3
        const val PEN_MAX = 0.1
    }
}
