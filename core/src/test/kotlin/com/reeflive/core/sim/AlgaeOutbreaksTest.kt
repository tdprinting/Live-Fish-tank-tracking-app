package com.reeflive.core.sim

import com.reeflive.core.sim.AlgaeGroup.BUBBLE
import com.reeflive.core.sim.AlgaeGroup.CYANO
import com.reeflive.core.sim.AlgaeGroup.DINOS
import com.reeflive.core.sim.AlgaeGroup.HAIR
import com.reeflive.core.sim.AlgaeSurface.GLASS
import com.reeflive.core.sim.AlgaeSurface.ROCK_SHADE
import com.reeflive.core.sim.AlgaeSurface.ROCK_TOP
import com.reeflive.core.sim.AlgaeSurface.SAND
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The sandbox outbreak triggers ([AlgaeOutbreaks]) on the stocked tank, and algae state across a water rebuild. */
class AlgaeOutbreaksTest {
    private fun f(v: Double) = "%.3f".format(v)
    private fun stocked() = TestTank.sim(DefaultStock.all, seed = 3L)
    private fun run(sim: Simulation, cmds: List<Command>) = cmds.forEach(sim::enqueue)

    /**
     * `Simulation.rebuildWater()` (a room-temperature change) builds a new [WaterSim]: the algae biomass, internal
     * stores, dead pools, dead-spot flow, dino lift, glass mask and drivers must carry over, so the tank goes on exactly
     * as a twin that was never rebuilt.
     */
    @Test
    fun rebuildWaterCarriesAlgaeState() {
        fun setUp(): Simulation = stocked().also { sim ->
            run(sim, AlgaeOutbreaks.cyano(sim.water.s) + AlgaeOutbreaks.bubble(sim.water.s) +
                listOf(Command.SetParam("dinos", 0.2), Command.SetParam("uv", 1.0)))
            sim.advanceAbstract(2 * 86400.0 + 12 * 3600.0)   // noon start: ends at midnight, dinos lifted
            sim.water.algae.kill(HAIR, 0.5)                    // something in the dead pool
            sim.water.algae.scrapeGlass(sim.water.s, 10.0, 10.0, 60.0, 10.0, 6.0, 6.0)
        }
        val rebuilt = setUp(); val twin = setUp()
        val before = rebuilt.water
        val a0 = before.algae
        val biomass = a0.biomass.copyOf(); val dead = a0.deadDw; val lift = a0.dinoLift; val cover = a0.snapshot()
        assertTrue("dinos not lifted at night: $lift", lift > 0.5)
        assertTrue("dead pool empty", dead > 0)
        rebuilt.enqueue(Command.SetParam("roomtemp", rebuilt.config.roomC))
        rebuilt.advanceAbstract(1e-3)
        val a1 = rebuilt.water.algae
        assertNotSame("water was not rebuilt", before, rebuilt.water)
        assertArrayEquals(biomass, a1.biomass, 1e-6)
        assertEquals(dead, a1.deadDw, 1e-6)
        assertEquals(lift, a1.dinoLift, 1e-3)
        assertEquals(AlgaeOutbreaks.DEAD_SPOT_CM_S, a1.flowOverride[SAND.ordinal], 0.0)
        assertTrue("uv lost", a1.uv)
        assertArrayEquals(cover.vigor, a1.snapshot().vigor, 1e-6f)
        assertArrayEquals(cover.glassMask, a1.snapshot().glassMask, 1e-6f)
        // and it then runs on like the twin that was never rebuilt
        twin.advanceAbstract(1e-3)
        rebuilt.advanceAbstract(3 * 86400.0); twin.advanceAbstract(3 * 86400.0)
        for (g in listOf(CYANO, DINOS, BUBBLE, HAIR)) {
            val r = rebuilt.water.algae.biomass(g); val t = twin.water.algae.biomass(g)
            println("rebuild +3 d: ${g.name} ${f(r)} g vs twin ${f(t)} g")
            assertEquals("${g.name} diverged after the rebuild", t, r, 1e-3 * t + 1e-6)
        }
    }

    /** Dino preset: ultra-low nutrients and a young community; within three weeks the dinos take sand and rock, not hair. */
    @Test
    fun dinoPresetStartsAnOutbreak() {
        val sim = stocked()
        run(sim, AlgaeOutbreaks.dinos())
        var at = -1
        for (d in 1..21) {
            sim.advanceAbstract(86400.0)
            if (at < 0 && sim.water.s.dinos >= 0.3) at = d
        }
        val s = sim.water.s
        val a = sim.latest.algae
        println("dino preset 21 d: dinos ${f(s.dinos)} (30 % on day $at), sand ${f(a.cover(DINOS, SAND).toDouble())} rock ${f(a.cover(DINOS, ROCK_TOP).toDouble())}, hair ${f(s.algaeHair)}, NO3 ${f(s.no3)} PO4 ${f(s.po4)}")
        assertTrue("dinos only ${s.dinos} after 21 d", at in 1..21)
        assertTrue("hair grew under ULN: ${s.algaeHair}", s.algaeHair < 0.1)
        // releasing the held nutrients lets the tank recover
        sim.enqueue(Command.ReleaseParam("no3")); sim.enqueue(Command.ReleaseParam("po4"))
        sim.enqueue(Command.SetParam("no3", 5.0)); sim.enqueue(Command.SetParam("po4", 0.05)); sim.enqueue(Command.SetParam("microbes", 1.0))
        val peak = s.dinos
        sim.advanceAbstract(21 * 86400.0)
        println("dino preset released: ${f(peak)} → ${f(sim.water.s.dinos)} after 21 d")
        assertTrue("dinos ${sim.water.s.dinos} did not fall from $peak", sim.water.s.dinos < peak / 2)
    }

    /** Cyano preset: a dead spot, carbon dosing and phosphate with little nitrate: red slime takes the sand first. */
    @Test
    fun cyanoPresetStartsAnOutbreak() {
        val sim = stocked()
        run(sim, AlgaeOutbreaks.cyano(sim.water.s))
        sim.advanceAbstract(14 * 86400.0)
        val a = sim.latest.algae
        val sand = a.cover(CYANO, SAND).toDouble(); val shade = a.cover(CYANO, ROCK_SHADE).toDouble(); val top = a.cover(CYANO, ROCK_TOP).toDouble()
        println("cyano preset 14 d: sand ${f(sand)} shade ${f(shade)} top ${f(top)} DOC index ${f(sim.water.algae.docIndex)} NO3 ${f(sim.water.s.no3)} PO4 ${f(sim.water.s.po4)}")
        assertTrue("cyano sand cover $sand after 14 d", sand > 0.3)
        assertTrue("low-flow rock $shade vs flushed rock tops $top", shade > top)
    }

    /** Bubble preset: a few cells on the rock that spread slowly; picking them off clears most, squeezing spreads them. */
    @Test
    fun bubblePresetSpreadsSlowlyAndIsPulled() {
        val sim = stocked()
        run(sim, AlgaeOutbreaks.bubble(sim.water.s))
        sim.advanceAbstract(600.0)
        val start = sim.water.s.bubble
        sim.advanceAbstract(30 * 86400.0)
        val month = sim.water.s.bubble
        val a = sim.latest.algae
        println("bubble preset: ${f(start)} → ${f(month)} in 30 d (sand ${a.cover(BUBBLE, SAND)}, glass ${a.cover(BUBBLE, GLASS)})")
        assertTrue("bubble algae did not spread: $start → $month", month > start * 1.2)
        assertTrue("bubble algae spread too fast: $start → $month", month < 0.3)
        assertEquals(0f, a.cover(BUBBLE, SAND), 0f)
        sim.enqueue(Command.RemoveBubbleAlgae(careful = true))
        sim.advanceAbstract(60.0)
        assertTrue("picking left ${sim.water.s.bubble} of $month", sim.water.s.bubble < 0.5 * month)
    }
}
