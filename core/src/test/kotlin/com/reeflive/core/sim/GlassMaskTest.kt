package com.reeflive.core.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The front-glass film mask ([GlassMask]), Flipper scraping ([AlgaeModel.scrapeGlass], [Command.ScrapeGlass]) and regrowth. */
class GlassMaskTest {
    private val cfg = TankConfig()

    /** A tank with a visible green film on the glass and the nutrients to grow it back. */
    private fun filmed() = WaterState(no3 = 5.0, po4 = 0.05, film = 0.5, algaeHair = 0.0, cyano = 0.0, diatoms = 0.0, dinos = 0.0, bubble = 0.0)

    private fun model(w: WaterState): AlgaeModel = AlgaeModel(cfg).also { it.step(w, 0.25, 1.0, 1.0) }

    private val midY get() = (cfg.sandTopCm + cfg.waterLevelCm) / 2

    @Test
    fun scrapeClearsTheSweptBandOnly() {
        val m = GlassMask(cfg.lengthCm, cfg.sandTopCm, cfg.waterLevelCm)
        assertTrue(m.isFull)
        assertNull(m.export())
        // a horizontal stroke right across the pane, 6 cm wide × 20 cm tall pad, centred at mid height
        val drop = m.scrape(0.0, midY, cfg.lengthCm, midY, 3.0, 10.0, AlgaeModel.SCRAPE_LEFT)
        assertEquals(m.at(60.0, midY).toDouble(), AlgaeModel.SCRAPE_LEFT, 1e-6)
        assertEquals(m.at(60.0, midY + 8.0).toDouble(), AlgaeModel.SCRAPE_LEFT, 1e-6)
        assertEquals(1.0, m.at(60.0, midY + 12.0).toDouble(), 1e-9)
        assertEquals(1.0, m.at(60.0, midY - 12.0).toDouble(), 1e-9)
        // 20 cm of the 53 cm wetted height ≈ 12 of 32 rows, each down by 90 %
        val band = 20.0 / (cfg.waterLevelCm - cfg.sandTopCm)
        assertEquals(band * (1 - AlgaeModel.SCRAPE_LEFT), drop, 0.05)
        assertEquals(1 - drop, m.mean, 1e-9)
        assertNotNull(m.export())
        // a second pass over the same band takes most of what is left
        val again = m.scrape(0.0, midY, cfg.lengthCm, midY, 3.0, 10.0, AlgaeModel.SCRAPE_LEFT)
        assertTrue("second pass $again", again > 0 && again < drop * 0.2)
    }

    @Test
    fun diagonalStrokeCoversTheSweptBoxNotItsBoundingBox() {
        val m = GlassMask(cfg.lengthCm, cfg.sandTopCm, cfg.waterLevelCm)
        m.scrape(10.0, 10.0, 100.0, 50.0, 3.0, 3.0, 0.0)
        assertEquals(0.0, m.at(55.0, 30.0).toDouble(), 1e-9)      // on the path
        assertEquals(1.0, m.at(90.0, 15.0).toDouble(), 1e-9)      // inside the bounding box, off the path
        assertEquals(1.0, m.at(15.0, 45.0).toDouble(), 1e-9)
    }

    @Test
    fun scrapingLowersGlassCoverAndKeepsUntouchedFilmAsItWas() {
        val w = filmed()
        val a = model(w)
        val gl = AlgaeSurface.GLASS
        val b0 = a.biomass(AlgaeGroup.FILM, gl)
        val c0 = a.cover(AlgaeGroup.FILM, gl)
        val film0 = w.film
        val dead0 = a.deadDw
        val removed = a.scrapeGlass(w, 0.0, midY, cfg.lengthCm, midY, 6.0, 20.0)
        val drop = 1 - a.glassMask.mean
        assertTrue("removed $removed g", removed > 0)
        // biomass under the swept cells goes: the front pane's share of the glass, by the mask drop
        val expected = b0 * a.frontShare * drop
        assertEquals(expected, b0 - a.biomass(AlgaeGroup.FILM, gl), expected * 1e-6 + 1e-9)
        assertTrue("glass cover ${a.cover(AlgaeGroup.FILM, gl)} vs $c0", a.cover(AlgaeGroup.FILM, gl) < c0)
        // the headline film cover follows at once (no chemistry tick needed)
        assertTrue("headline ${w.film} vs $film0", w.film < film0)
        assertEquals(a.headline(AlgaeGroup.FILM), w.film, 1e-12)
        // half of it sinks as detritus into the dead pool
        assertEquals(removed * AlgaeModel.SCRAPE_TO_DEAD, a.deadDw - dead0, 1e-9)
        // glass the pad never touched looks just as filmed as before
        val snap = a.snapshot()
        assertNotNull(snap.glassMask)
        assertEquals(GlassMask.W * GlassMask.H, snap.glassMask!!.size)
        assertEquals(c0, snap.glassBase!![AlgaeGroup.FILM.ordinal].toDouble(), 1e-5)
        // the next step does not mistake the new headline for an outside edit (which would re-seed a uniform film)
        a.step(w, 0.25, 1.0, 1.0)
        assertTrue("mask reset by the step", !a.glassMask.isFull)
    }

    @Test
    fun stripesGrowBackInTheLightNotInTheDark() {
        val w = filmed()
        val a = model(w)
        a.scrapeGlass(w, 0.0, midY, cfg.lengthCm, midY, 6.0, 20.0)
        val m0 = a.glassMask.mean
        // a night: no light, no growth, the stripe stays
        repeat(40) { a.step(w, 0.25, 0.0, 1.0) }
        assertEquals(m0, a.glassMask.mean, 1e-9)
        // lit days with nutrients held: the film creeps back over the stripe within days, not hours
        var after1d = 0.0
        val lights = LightSchedule()
        var t = 0.0
        repeat(14 * 96) { k ->
            w.no3 = 5.0; w.po4 = 0.05
            a.step(w, 0.25, lights.intensity(t), 1.0)
            t += 900.0
            if (k == 95) after1d = a.glassMask.mean
        }
        println("glass mask mean: scraped ${"%.3f".format(m0)}, 1 d ${"%.3f".format(after1d)}, 14 d ${"%.3f".format(a.glassMask.mean)}")
        assertTrue("regrew too fast in a day: $after1d", after1d < 1 - (1 - m0) * 0.3)
        assertTrue("did not regrow in 14 d: ${a.glassMask.mean}", a.glassMask.mean > 1 - (1 - m0) * 0.2)
        assertTrue(after1d > m0)
    }

    @Test
    fun wipeCopyAndOutsideEditsResetOrCarryTheMask() {
        val w = filmed()
        val a = model(w)
        a.scrapeGlass(w, 0.0, midY, 60.0, midY, 6.0, 20.0)
        val b = AlgaeModel(cfg).also { it.copyFrom(a) }
        assertEquals(a.glassMask.mean, b.glassMask.mean, 1e-12)
        assertEquals(a.glassMask.at(30.0, midY), b.glassMask.at(30.0, midY))
        a.wipeGlass()
        assertTrue(a.glassMask.isFull)
        // a sandbox slider sets the film cover: uniform film again
        b.step(w.also { it.film = 0.3 }, 0.25, 1.0, 1.0)
        assertTrue(b.glassMask.isFull)
    }

    @Test
    fun simulationCommandScrapesTheFrontGlass() {
        val sim = TestTank.sim(emptyList(), water = filmed())
        sim.advance(60.0, behaviorDt = 0.5)
        val film0 = sim.water.s.film
        sim.enqueue(Command.ScrapeGlass(GlassPane.FRONT, 5.0, 23.0, 115.0, 23.0, 6.0, 20.0))
        sim.advance(0.5, behaviorDt = 0.5)
        val snap = sim.latest
        assertNotNull(snap.algae.glassMask)
        assertTrue("film ${sim.water.s.film} vs $film0", sim.water.s.film < film0)
        assertTrue(snap.algae.glassMask!![GlassMask.W * 6 + 32] < 0.5f)
    }
}
