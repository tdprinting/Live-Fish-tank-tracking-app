package com.reeflive.core.ai

import com.reeflive.core.geom.Vec3
import com.reeflive.core.sim.BrainKind
import com.reeflive.core.sim.DefaultStock
import com.reeflive.core.sim.LivestockSeed
import com.reeflive.core.sim.Organism
import com.reeflive.core.sim.Simulation
import com.reeflive.core.sim.TestTank
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Pajama cardinal, coral banded shrimp and skunk cleaner shrimp against the research and Tee's tank
 * (docs/research/video/fish_pajama_cardinalfish.md, invert_banded_shrimp.md, invert_cleaner_shrimp.md, 2026-10-14 pass).
 */
class CritterAccuracyTest {
    private fun Simulation.org(kind: BrainKind) = organisms.first { it.brainKind == kind }
    private fun stock(vararg ids: String) = ids.map { TestTank.seed(it) }
    private fun Simulation.brain(o: Organism) = agentOf(o)!!.brain

    // ------------------------------------------------------------------ banded coral shrimp
    @Test
    fun bandedShrimpHangsUpsideDownUnderTheLeftLedgeByDay() {
        // full stock: the skunk cleaner's keep-apart rule must not push it off Tee's left ledge
        val s = TestTank.sim(DefaultStock.all, seed = 1L, startHour = 10.0)
        val o = s.org(BrainKind.BANDED_SHRIMP)
        var hanging = 0
        var samples = 0
        s.advance(1800.0, behaviorDt = 0.1) {
            if (s.simTimeSec % 10.0 < 0.1) {
                samples++
                if (o.posture == 2 && o.up.y < -0.4 && s.rock.envDist(o.pos) < shrimpStance(o) + 1.0) hanging++
            }
        }
        val ledge = s.rock.anchors["SHRIMP_LEDGE_L"]!!.pos
        assertTrue("near the left ledge: ${o.pos} vs $ledge", o.pos.distanceTo(ledge) < 12.0)
        assertTrue("inverted under the ledge $hanging / $samples", hanging > 0.8 * samples)
    }

    @Test
    fun bandedShrimpStuckOnAWallFindsItsWayHome() {
        // where the old surface walk stalled for a whole day (diagnostic run, 2026-10-14)
        val s = TestTank.sim(stock("banded"), seed = 3L, startHour = 11.0)
        val o = s.org(BrainKind.BANDED_SHRIMP)
        val b = s.brain(o) as BandedShrimpBrain
        o.pos = Vec3(68.9, 42.8, 5.5); o.up = Vec3.X; o.heading = Vec3(0.0, 0.3, -0.95).normalized()
        s.advance(600.0, behaviorDt = 0.1)
        val home = b.hang(s.agentOf(o)!!.ctx)
        assertTrue("home after 10 min: ${o.pos} vs ${home.pos} (${o.behaviorName}), hops ${b.nav.hops}", o.pos.distanceTo(home.pos) < 3.0)
        assertTrue("never inside rock", s.rock.sdf.dist(o.pos) > 0.0)
    }

    @Test
    fun bandedShrimpForagesAtNightWalkingOverTheRock() {
        val s = TestTank.sim(stock("banded"), seed = 2L, startHour = 22.0)
        val o = s.org(BrainKind.BANDED_SHRIMP)
        val b = s.brain(o) as BandedShrimpBrain
        val home = b.hang(s.agentOf(o)!!.ctx).pos
        var maxAway = 0.0
        var onSurface = 0
        var n = 0
        var walkedInverted = false
        s.advance(3600.0, behaviorDt = 0.1) {
            maxAway = maxOf(maxAway, o.pos.distanceTo(home))
            n++
            if (s.rock.envDist(o.pos) < shrimpStance(o) + 1.5) onSurface++
            if (o.walkAmt > 0.3 && o.up.y < -0.5) walkedInverted = true
            assertTrue("inside rock at ${o.pos}", s.rock.sdf.dist(o.pos) > -0.05)
        }
        assertTrue("forays away from the ledge ($maxAway cm)", maxAway > 6.0)
        assertTrue("stays within its territory ($maxAway cm)", maxAway < BandedShrimpBrain.FORAY_CM + 15)
        assertTrue("mostly on foot ($onSurface / $n)", onSurface > 0.95 * n)
        assertTrue("walks upside down on ceilings at some point", walkedInverted)
    }

    // ------------------------------------------------------------------ skunk cleaner shrimp
    @Test
    fun skunkSitsOnRockAtItsStationNotOnTheSand() {
        val s = TestTank.sim(stock("skunk"), seed = 1L, startHour = 12.0)
        val o = s.org(BrainKind.CLEANER_SHRIMP)
        val b = s.brain(o) as CleanerShrimpBrain
        s.advance(600.0, behaviorDt = 0.1)
        val spot = b.homeSpot!!
        assertTrue("perch on the rock: sdf ${s.rock.sdf.dist(spot)}", abs(s.rock.sdf.dist(spot)) < 0.2)
        assertTrue("not on the sand: ${o.pos}", o.pos.y > s.rock.sandTopCm + 3.0)
        assertTrue("at its station: ${o.pos} vs $spot", o.pos.distanceTo(spot) < 3.0)
        assertTrue("clients pose in open water in front of it", s.rock.sdf.dist(b.station.pos) > 1.5 && b.station.pos.distanceTo(spot) < 6.0)
    }

    @Test
    fun twoSkunksShareTheStationOnSeparateSpots() {
        val seeds = listOf(LivestockSeed("lysmata_amboinensis", "A", 5.5, id = "skA"), LivestockSeed("lysmata_amboinensis", "B", 5.5, id = "skB"))
        val s = TestTank.sim(seeds, seed = 2L, startHour = 12.0)
        s.advance(300.0, behaviorDt = 0.1)
        val spots = s.organisms.map { (s.brain(it) as CleanerShrimpBrain).homeSpot!! }
        assertTrue("spots ${spots[0]} / ${spots[1]}", spots[0].distanceTo(spots[1]) >= 3.4)
        assertTrue("same station area", spots[0].distanceTo(spots[1]) < 20.0)
    }

    @Test
    fun skunkSwimsOntoAClientInsteadOfJumping() {
        val s = TestTank.sim(stock("skunk", "tang"), seed = 4L, startHour = 11.0)
        val o = s.org(BrainKind.CLEANER_SHRIMP)
        val b = s.brain(o) as CleanerShrimpBrain
        val tang = s.org(BrainKind.TANG)
        s.advance(60.0, behaviorDt = 0.1) { tang.pos = Vec3(20.0, 45.0, 25.0); tang.vel = Vec3.ZERO }
        b.station.client = tang; b.station.clientReady = true
        val at = b.station.pos
        var maxStep = 0.0
        var last = o.pos
        var rode = false
        s.advance(30.0, behaviorDt = 0.1) {
            tang.pos = at; tang.vel = Vec3.ZERO
            maxStep = maxOf(maxStep, o.pos.distanceTo(last) / 0.1)
            last = o.pos
            if (o.behaviorName == "Cleaning a client" && o.pos.distanceTo(tang.pos) < tang.lengthCm) rode = true
        }
        assertTrue("rode the client", rode)
        assertTrue("max speed ${"%.1f".format(maxStep)} cm/s", maxStep < CleanerShrimpBrain.MOUNT_CM_S * 1.6)
        // and back home afterwards
        s.advance(120.0, behaviorDt = 0.1) { tang.pos = Vec3(20.0, 45.0, 25.0); tang.vel = Vec3.ZERO }
        assertTrue("home again ${o.pos} vs ${b.homeSpot}", o.pos.distanceTo(b.homeSpot!!) < 3.0)
    }

    // ------------------------------------------------------------------ pajama cardinal
    @Test
    fun cardinalHoversLevelAndSlowByDay() {
        val s = TestTank.sim(stock("cardinal"), seed = 5L, startHour = 11.0)
        val o = s.org(BrainKind.CARDINAL)
        var level = 0
        var slow = 0
        var n = 0
        s.advance(1800.0, behaviorDt = 0.1) {
            if (o.behaviorName == "Hovering by cover") {
                n++
                if (abs(o.heading.y) < 0.25) level++
                if (o.vel.length() < 0.5 * o.lengthCm) slow++
            }
        }
        assertTrue("hovered ($n ticks)", n > 9000)
        assertTrue("level $level / $n", level > 0.95 * n)
        assertTrue("slow $slow / $n", slow > 0.95 * n)
    }

    @Test
    fun appendagePhaseWrapsAtWholeTurns() {
        val w = Organism.APPENDAGE_WRAP
        assertEquals(0.0, kotlin.math.sin(w), 1e-9)
        assertEquals(1.0, kotlin.math.cos(w), 1e-9)
    }
}
