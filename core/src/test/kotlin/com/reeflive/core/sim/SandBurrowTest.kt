package com.reeflive.core.sim

import com.reeflive.core.ai.GobyBrain
import com.reeflive.core.geom.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Tunnels under rock, the engineer goby's burrows and bodies against the sand (SIMULATION §16.8–16.9). */
class SandBurrowTest {
    private val rock get() = TestTank.rock
    private fun Simulation.org(kind: BrainKind) = organisms.first { it.brainKind == kind }
    private fun bed(): SandBed = SandBed(rock.lengthCm, rock.widthCm, rock.sandTopCm, 7L).also { it.attachRock(rock.sdf) }
    private fun gobyAnchor(): Anchor = rock.anchors["BURROW_GOBY"] ?: rock.anchors["CAVE_EEL"]!!

    /** A 10 cm engineer goby (Tee's): body depth 0.8 cm × fit margin, width ratio. */
    private val bodyH = 0.08 * 10.0 * GobyBrain.FIT_MARGIN
    private val bodyW = bodyH * GobyBrain.WIDTH_RATIO

    @Test fun rockRoofIsReadFromTheSdf() {
        val s = bed()
        assertTrue(s.rockAttached)
        val roofed = (0 until s.nx * s.nz).count { s.roofed(it) }
        assertTrue("cells with rock to dig under: $roofed", roofed in 20..(s.nx * s.nz / 2))
        // open sand at the front middle, roof at the goby's burrow mouth
        assertTrue(s.gapAt(30.0, 31.0).isInfinite())
        val a = gobyAnchor()
        assertTrue(s.underRock(a.pos.x + a.dir!!.x * 2.0, a.pos.z + a.dir!!.z * 2.0))
    }

    @Test fun excavationConservesSandMass() {
        val s = bed()
        val a = gobyAnchor()
        val b = s.addBurrow(s.planBurrow("g", a.pos.x, a.pos.z, a.dir!!.x, a.dir!!.z, bodyH, 14.0, primary = true)!!)
        val v0 = s.sandVolumeCm3()
        var dug = 0.0
        repeat(60) { k ->
            val sd = s.nextDig(b, bodyH, bodyW, bodyH * GobyBrain.CHAMBER, 18.0) ?: return@repeat
            val got = s.takeMouthful(b.x(sd), b.z(sd), 0.6)
            dug += got
            // mid-trip: the mouthful is in the fish, still counted
            assertEquals(v0, s.sandVolumeCm3(), 0.02)
            val sp = Vec3(b.x(-5.0), 0.0, b.z(-5.0))
            s.spit(sp.x, s.surfaceY(sp.x, sp.z) + 1.0, sp.z, got, 1.4, plume = 6)
            if (k % 7 == 0) s.bowWave(sp.x + 3, sp.z, 1.0, 0.0, 0.6, 0.3)
            if (k % 11 == 0) s.impress(sp.x - 3, sp.z, 1.0, 0.4)
            repeat(40) { s.stepClouds(0.05) { _, _, _ -> Vec3(4.0, 0.0, 0.0) } }
        }
        assertTrue("dug $dug cm³", dug > 10.0)
        repeat(600) { s.stepClouds(0.1, null) }
        repeat(3 * 1440) { s.relax(1.0, 1.0, 0.3) }   // three days of slumping, smoothing and infill
        assertEquals("sand is moved, never made or lost", v0, s.sandVolumeCm3(), 0.05)
        assertEquals(0.0, s.carriedCm3, 1e-9)
    }

    /**
     * A burrow site where the undug gap is lower than the body. In Tee's 48 x 18 x 16 in layout the burrow anchor sits
     * in a natural cave a 10 cm goby already fits, so the test takes the best rock-edge site in the tank that needs digging.
     */
    private fun undugSite(s: SandBed): Burrow =
        s.burrowSites("g", rock.lengthCm / 2, rock.widthCm / 2, 200.0, bodyH, 6.0, 12.0, 4.0, emptyList(), 400)
            .first { s.reach(it, bodyH, bodyW) < it.lengthCm }

    @Test fun gobyCannotPassUnderRockUntilDug() {
        val s = bed()
        val b = s.addBurrow(undugSite(s))
        val before = s.reach(b, bodyH, bodyW)
        assertTrue("burrow runs under the rock: ${b.lengthCm}", b.lengthCm >= 6.0)
        assertTrue("undug, the body does not fit all the way: reach $before of ${b.lengthCm}", before < b.lengthCm)
        val face = before + SandBed.REACH_STEP
        assertTrue("the gap at the face is lower than the body", !s.fits(b.x(face), b.z(face), b.dirX, b.dirZ, bodyH, bodyW))
        val spent = s.excavate(b, bodyH, bodyW, bodyH, 0.6, b.x(-5.0), b.z(-5.0), 500.0)
        val after = s.reach(b, bodyH, bodyW)
        assertTrue("dug ${spent} cm³", spent > 0.0)
        assertEquals("open to its length once dug", b.lengthCm, after, 1e-9)
        assertTrue(s.fits(b.x(face), b.z(face), b.dirX, b.dirZ, bodyH, bodyW))
        // a tunnel left alone fills back in over days
        repeat(6 * 1440) { s.relax(1.0, 1.5, 0.3) }
        assertTrue("refilled: reach ${s.reach(b, bodyH, bodyW)}", s.reach(b, bodyH, bodyW) < b.lengthCm)
    }

    @Test fun liveGobyOnlyGoesUnderRockAsFarAsItHasDug() {
        val sim = TestTank.sim(listOf(TestTank.seed("goby")), startHour = 10.0)
        sim.sand.reset()   // a fresh bed: the burrows are planned again but nothing is dug
        val g = sim.org(BrainKind.ENGINEER_GOBY)
        val br = sim.agentOf(g)!!.brain as GobyBrain
        val bs = br.burrows
        val h = br.bodyH; val w = br.bodyW
        val reach0 = bs.map { sim.sand.reach(it, h, w) }
        // (Tee's burrow anchor is a natural cave the goby already fits; its other burrows have to be dug)
        assertTrue("a burrow still to dig at the start: $reach0 of ${bs.map { it.lengthCm }}", bs.indices.any { reach0[it] < bs[it].lengthCm })
        val v0 = sim.sand.sandVolumeCm3()
        var worst = -1e9
        var under = 0
        sim.advance(2400.0, behaviorDt = 0.1) {
            val q = g.pos
            for (p in bs) {
                val s = (q.x - p.mouthX) * p.dirX + (q.z - p.mouthZ) * p.dirZ
                val lat = abs(-(q.x - p.mouthX) * p.dirZ + (q.z - p.mouthZ) * p.dirX)
                if (lat < 1.5 && s > 0.5) {
                    under++
                    worst = maxOf(worst, s - sim.sand.reach(p, h, w))
                }
            }
        }
        val reach1 = bs.map { sim.sand.reach(it, h, w) }
        assertTrue("goby went into a burrow ($under ticks)", under > 0)
        assertTrue("head beyond the dug face by $worst cm", worst <= 0.8)
        assertTrue("it dug: ${br.sandEvents} trips, reach $reach1 from $reach0", br.sandEvents > 0 && bs.indices.any { reach1[it] > reach0[it] })
        assertTrue("spoil heaped at the entrance", sim.sand.height.any { it > 0.2f })
        assertEquals("sand moved, not made", v0, sim.sand.sandVolumeCm3(), 0.05 * kotlin.math.abs(v0) + 0.5)
    }

    @Test fun noBodyCentreBelowTheSandUnlessBurrowing() {
        val sim = TestTank.sim(DefaultStock.all, seed = 4L, startHour = 18.5)
        var checked = 0
        var worstHead = 0.0
        var worstMid = 0.0
        var who = ""
        var whoHead = ""
        sim.advance(1800.0, behaviorDt = 0.1) {
            for (o in sim.organisms) {
                if (!o.alive || SandContact.exempt(o)) continue
                val q = o.pos
                val sy = sim.sand.surfaceY(q.x, q.z)
                val dh = sy - q.y
                if (dh > worstHead) { worstHead = dh; whoHead = "${o.id} ${o.behaviorName} posture ${o.posture} at ${sim.simTimeSec}" }
                checked++
            }
        }
        // body centre: the middle of the spine (snapshot), once at the end and at night
        for (pose in sim.snapshot().organisms) {
            val o = sim.organisms.first { it.id == pose.id }
            if (!o.alive || SandContact.exempt(o)) continue
            val m = com.reeflive.core.geom.BodySpline.POINTS / 2
            val x = pose.spine[m * 3].toDouble(); val y = pose.spine[m * 3 + 1].toDouble(); val z = pose.spine[m * 3 + 2].toDouble()
            val d = sim.sand.surfaceY(x, z) - y
            if (d > worstMid) { worstMid = d; who = "${o.id} ${o.behaviorName} posture ${o.posture} head ${o.pos} mid ($x, $y, $z)" }
        }
        assertTrue(checked > 1000)
        assertTrue("a head $worstHead cm in the sand ($whoHead)", worstHead <= 1e-6)
        assertTrue("a body centre $worstMid cm in the sand ($who)", worstMid <= 0.3)
        assertTrue("bodies touching the sand moved it: bow ${sim.sand.bowWaves} rest ${sim.sand.impressions}",
            sim.sand.bowWaves + sim.sand.impressions > 0)
    }

    @Test fun burrowsCarryOverToAFork() {
        val sim = TestTank.sim(listOf(TestTank.seed("goby")), startHour = 11.0)
        val f = com.reeflive.core.sim.timelapse.TimelapseFork.of(sim)
        assertEquals(sim.sand.burrows.size, f.sand.burrows.size)
        assertEquals(sim.sand.sandVolumeCm3(), f.sand.sandVolumeCm3(), 1e-3)
        // the dug state goes with it: the same burrows, bed and cavity shading
        assertTrue(sim.sand.burrows.zip(f.sand.burrows).all { (a, b) -> a.mouthX == b.mouthX && a.mouthZ == b.mouthZ && a.lengthCm == b.lengthCm })
        assertTrue(sim.sand.gridSnapshot().cavity.contentEquals(f.sand.gridSnapshot().cavity))
        assertTrue(sim.sand.height.contentEquals(f.sand.height))
    }
}
