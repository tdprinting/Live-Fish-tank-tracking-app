package com.reeflive.core.sim

import com.reeflive.core.geom.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Grazing trails worn into the front-glass film by snails and stars ([GlassTrails], [GlassMask.graze]). */
class GlassTrailsTest {
    private val cfg = TankConfig()
    private val midY get() = (cfg.sandTopCm + cfg.waterLevelCm) / 2

    @Test
    fun aCrawlLeavesAnEvenNarrowTrailWithoutBeadsAtTheJoints() {
        val m = GlassMask(cfg.lengthCm, cfg.sandTopCm, cfg.waterLevelCm)
        // a snail crawling 40 cm along the pane in 0.6 cm steps, a 1 cm swath taking 60 % per pass
        var x = 30.0
        while (x < 70.0) { m.graze(x, midY, x + 0.6, midY, 1.0, 0.6); x += 0.6 }
        assertFalse("not published yet", m.version > 0)
        assertTrue(m.publish())
        assertFalse("nothing new to publish", m.publish())
        assertNotNull(m.export())
        // along the trail: cleared by about the pass's take, evenly (no darker beads where segments meet)
        var lo = 1f; var hi = 0f
        var px = 32.0
        while (px < 68.0) { val v = m.at(px, midY); lo = minOf(lo, v); hi = maxOf(hi, v); px += 0.25 }
        assertTrue("trail cleared: $hi", hi < 0.75f)
        assertTrue("trail even: $lo..$hi", hi - lo < 0.15f)
        // narrow: 2 cm to the side and past its ends the film is untouched
        assertEquals(1f, m.at(50.0, midY + 2.0), 0f)
        assertEquals(1f, m.at(50.0, midY - 2.0), 0f)
        assertEquals(1f, m.at(26.0, midY), 0f)
        assertEquals(1f, m.at(74.0, midY), 0f)
        // a 1 cm × 40 cm trail is a sliver of the pane
        assertTrue("mean ${m.mean}", m.mean in 0.99..0.9999)
    }

    @Test
    fun aSecondPassCleansDeeperAndTheFilmGrowsBackOverIt() {
        val m = GlassMask(cfg.lengthCm, cfg.sandTopCm, cfg.waterLevelCm)
        m.graze(40.0, midY, 60.0, midY, 1.0, 0.6)
        val once = m.at(50.0, midY)
        m.graze(40.0, midY, 60.0, midY, 1.0, 0.6)
        assertTrue(m.at(50.0, midY) < once * 0.75f)
        // the glass film's net growth (~0.3 /d): mostly back within a week
        val before = m.at(50.0, midY)
        m.regrow(0.3, 7.0)
        assertTrue("regrew ${m.at(50.0, midY)} from $before", m.at(50.0, midY) > 0.5f)
    }

    /** Astrea snails crawling the front pane wear trails into it; the snapshot carries them to the renderer. */
    @Test
    fun snailsOnTheFrontPaneWearTrailsIntoTheFilm() {
        val stock = listOf("astrea1", "astrea2", "astrea3").map { TestTank.seed(it) }
        val sim = TestTank.sim(stock, water = WaterState(no3 = 5.0, po4 = 0.05, film = 0.5, algaeHair = 0.0, cyano = 0.0, diatoms = 0.0, dinos = 0.0, bubble = 0.0))
        // put every snail on open front glass, foot on the pane
        val front = TestTank.rock.widthCm
        var k = 0
        for (o in sim.organisms) {
            val p = Vec3(25.0 + 30.0 * k++, midY, front - 1.2)
            o.homePos = p; o.placeAt(p, Vec3.Y); o.up = Vec3(0.0, 0.0, -1.0)
        }
        assertNull(sim.water.algae.snapshot().glassMask)
        // the 60 s chemistry tick regrows the mask: compare with the snails' share of it, not with a frozen pane
        var onPaneS = 0.0
        repeat(2 * 3600 * 2) {
            sim.advance(0.5, behaviorDt = 0.5)
            if (sim.organisms.any { it.pos.z > front - GlassTrails.ON_PANE_CM && it.up.z < -0.5 }) onPaneS += 0.5
        }
        val mask = sim.water.algae.glassMask
        val cleared = mask.cells.count { it < 0.7f }
        println("glass trails: ${"%.0f".format(onPaneS / 60)} min on the pane, $cleared cells below 0.7, mean ${"%.4f".format(mask.mean)}")
        assertTrue("snails were on the front pane", onPaneS > 600)
        assertTrue("trails worn: $cleared cells", cleared > 20)
        // trails, not a wipe: most of the film stays
        assertTrue("mean ${mask.mean}", mask.mean > 0.85)
        assertTrue("some pane untouched", mask.cells.count { it >= 0.999f } > GlassMask.W * GlassMask.H / 3)
        val snap = sim.latest.algae
        assertNotNull(snap.glassMask)
        assertNotNull(snap.glassBase)
    }

    @Test
    fun fishAndOffPaneGrazersLeaveNoTrail() {
        assertNull(GlassTrails.trailOf(GrazerType.TANG))
        assertNull(GlassTrails.trailOf(GrazerType.LAWNMOWER))
        assertNotNull(GlassTrails.trailOf(GrazerType.TURBO))
        assertNotNull(GlassTrails.trailOf(GrazerType.ASTERINA))
        val stock = listOf(TestTank.seed("astrea1"))
        val sim = TestTank.sim(stock)
        val o = sim.organisms.first()
        val trails = GlassTrails()
        val mask = GlassMask(cfg.lengthCm, cfg.sandTopCm, cfg.waterLevelCm)
        // on the back pane (up = +z): nothing on the front mask
        o.pos = Vec3(40.0, midY, 1.2); o.up = Vec3.Z
        repeat(20) { o.pos = o.pos + Vec3(1.0, 0.0, 0.0); trails.step(sim.organisms, mask, TestTank.rock.widthCm) }
        assertTrue(mask.isFull)
        // on the front pane it does
        o.pos = Vec3(40.0, midY, TestTank.rock.widthCm - 1.2); o.up = Vec3(0.0, 0.0, -1.0)
        repeat(20) { o.pos = o.pos + Vec3(1.0, 0.0, 0.0); trails.step(sim.organisms, mask, TestTank.rock.widthCm) }
        assertFalse(mask.isFull)
    }
}
