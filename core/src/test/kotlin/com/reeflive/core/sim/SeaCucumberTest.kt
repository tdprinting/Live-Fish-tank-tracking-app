package com.reeflive.core.sim

import com.reeflive.core.ai.SeaCucumberBrain
import com.reeflive.core.custom.economy.PriceBook
import com.reeflive.core.custom.economy.Stores
import com.reeflive.core.geom.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.min

/** Tiger tail sea cucumber (docs/research/45_clam_crab_star_cucumber.md §4). */
class SeaCucumberTest {
    private val id = "holothuria_hilla"

    private fun sim(hour: Double, vararg more: LivestockSeed): Simulation {
        val sim = TestTank.sim(emptyList(), seed = 9L, startHour = hour)
        for (s in listOf(LivestockSeed(id, "Stripes", 25.0, id = "cuke")) + more) { sim.species.ensure(s.speciesId); sim.addFromSeed(s) }
        sim.advance(1.0, behaviorDt = 0.05)
        return sim
    }

    /** Deepest any body station sits in the rock (sdf at the body's mid-height) and below the sand (cm; < 0 = in). */
    private fun clearances(sim: Simulation, o: Organism): Pair<Double, Double> {
        val pose = o.toPose()
        var rock = 9.0
        var sand = 9.0
        for (i in 0 until pose.spine.size / 3) {
            val p = pose.spinePoint(i)
            rock = min(rock, sim.rock.sdf.dist(p + Vec3.Y * (0.044 * o.lengthCm)) - 0.066 * o.lengthCm)
            sand = min(sand, p.y - sim.sand.surfaceY(p.x, p.z))
        }
        return rock to sand
    }

    @Test fun sheetBrainAndStore() {
        val sim = sim(12.0)
        val o = sim.organisms.first { it.id == "cuke" }
        assertEquals("models/tiger_tail_cucumber.glb", o.species.model)
        assertEquals(BrainKind.SEA_CUCUMBER, o.brainKind)
        assertEquals(10, o.toPose().motionMode)
        assertTrue(sim.agentOf(o)!!.brain is SeaCucumberBrain)
        assertEquals(40 * PriceBook.SCALE, PriceBook.listing(id)!!.price)
        assertTrue(Stocking.entry(id) != null)
        assertTrue(Stores.warning(sim, id)!!.contains("per tank"))
    }

    @Test fun liesUnderTheRockByDayAndMopsTheSandAtNight() {
        val day = sim(13.0)
        val d = day.organisms.first { it.id == "cuke" }
        val home = (day.agentOf(d)!!.brain as SeaCucumberBrain).restSpot(day.agentOf(d)!!.ctx, false)
        var worst = 9.0 to 9.0
        day.advance(1800.0, behaviorDt = 0.1) { val c = clearances(day, d); worst = min(worst.first, c.first) to min(worst.second, c.second) }
        println("cucumber by day: ${d.behaviorName}, %.1f cm from its spot, cover %.2f, worst rock %.2f sand %.2f".format(d.pos.distanceTo(home.pos), day.rock.sdf.cover(d.pos + Vec3.Y * 1.1), worst.first, worst.second))
        assertTrue("at its spot by day (${d.behaviorName})", d.pos.distanceTo(home.pos) < 3.0)
        assertTrue("head under rock cover", day.rock.sdf.cover(d.pos + Vec3.Y * 1.1) > 0.2)

        val night = sim(1.0)
        val o = night.organisms.first { it.id == "cuke" }
        val b = night.agentOf(o)!!.brain as SeaCucumberBrain
        var out = 0.0
        worst = 9.0 to 9.0
        night.advance(3600.0, behaviorDt = 0.1) {
            out = maxOf(out, o.mouth)
            val c = clearances(night, o); worst = min(worst.first, c.first) to min(worst.second, c.second)
        }
        println("cucumber at night: crawled %.0f cm, sifted %.2f, tentacles %.2f, worst rock %.2f sand %.2f (${o.behaviorName})".format(b.crawled, b.sifted, out, worst.first, worst.second))
        assertTrue("creeps over the sand (${b.crawled} cm)", b.crawled > 40.0)
        assertTrue("sifts the sand film", b.sifted > 0.0)
        assertTrue("tentacles out", out > 0.8)
        assertTrue("body clear of the rock (${worst.first})", worst.first > -0.3)
        assertTrue("body on, not in, the sand (${worst.second})", worst.second > -0.2)
    }

    @Test fun ballsUpWhenTouched() {
        val sim = sim(1.0)
        val o = sim.organisms.first { it.id == "cuke" }
        sim.advance(120.0, behaviorDt = 0.1)
        val b = sim.agentOf(o)!!.brain as SeaCucumberBrain
        b.onTap(sim.agentOf(o)!!.ctx, o.pos, 0.6)
        sim.advance(10.0, behaviorDt = 0.1)
        assertEquals("Balled up tight", o.behaviorName)
        assertTrue("contracted (${b.contraction})", b.contraction > 0.8)
        assertTrue("tentacles in (${o.mouth})", o.mouth < 0.2)
    }

    @Test fun aDeadOrBittenCucumberPoisonsTheFish() {
        val sim = sim(14.0, LivestockSeed("amphiprion_ocellaris", "Nemo", 7.0, id = "nemo"))
        val o = sim.organisms.first { it.id == "cuke" }
        val fish = sim.organisms.first { it.id == "nemo" }
        val b = sim.agentOf(o)!!.brain as SeaCucumberBrain
        val store = SeaCucumberBrain.storeIndex(o, sim)
        b.onAttacked(sim.agentOf(o)!!.ctx, o.pos + Vec3.X, 0.8)
        assertEquals("a bite spills a quarter", SeaCucumberBrain.SPILL_SHARE * store, sim.water.s.holothurin, 1e-9)
        sim.killNow(o, "test")
        sim.advance(6 * 3600.0, behaviorDt = 0.5)
        val h = sim.water.s.holothurin
        println("holothurin: store %.2f, after death %.2f, fish stress ${fish.envStress["holothurin"]}".format(store, h))
        assertTrue("the dead body leaches the rest ($h)", h > 0.5 * store)
        assertTrue("the fish feel it", (fish.envStress["holothurin"] ?: 0.0) > 0.3 || !fish.alive)
    }
}
