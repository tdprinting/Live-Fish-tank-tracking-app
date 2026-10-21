package com.reeflive.core.sim

import com.reeflive.core.ai.GiantClamBrain
import com.reeflive.core.ai.MantisShrimpBrain
import com.reeflive.core.custom.economy.PriceBook
import com.reeflive.core.custom.economy.Stores
import com.reeflive.core.geom.Vec3
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rare inverts (docs/research/62_rare_inverts.md). */
class RareInvertsTest {
    private fun sim(id: String, hour: Double, len: Double): Pair<Simulation, Organism> {
        val sim = TestTank.sim(emptyList(), seed = 3L, startHour = hour)
        sim.species.ensure(id)
        sim.addFromSeed(LivestockSeed(id, id, len, id = "x"))
        sim.advance(1.0, behaviorDt = 0.05)
        return sim to sim.organisms.first { it.id == "x" }
    }

    @Test fun croceaSheetAndShop() {
        val id = "tridacna_crocea"
        val db = SpeciesCatalog.fromClasspath(listOf(id))
        assertEquals(BrainKind.GIANT_CLAM, db[id].brainKind)
        assertEquals("models/crocea_clam.glb", db[id].model)
        assertTrue(Stocking.entry(id) != null)
        assertEquals(150 * PriceBook.SCALE, PriceBook.listing(id)!!.price)
        assertTrue(PriceBook.listing(id)!!.level > PriceBook.listing("tridacna_maxima")!!.level)
        val (sim, _) = sim(id, 16.0, 6.0)
        assertTrue(Stores.warning(sim, id)!!.contains("rock"))
    }

    /** The boring clam settles on the rock (never the sand), gape up in the light, and starves of light sooner than a maxima. */
    @Test fun croceaBoresIntoBrightRock() {
        val (sim, o) = sim("tridacna_crocea", 16.0, 6.0)
        sim.advance(120.0, behaviorDt = 0.05)
        val sandY = sim.sand.surfaceY(o.pos.x, o.pos.z)
        println("crocea at %s heading %s PAR %.0f sand %.1f".format(o.pos, o.heading, GiantClamBrain.parAt(sim, o), sandY))
        assertTrue("on the rock, not the sand (y ${o.pos.y} vs sand $sandY)", o.pos.y - 0.5 * o.lengthCm > sandY + 1.0)
        assertTrue("gape up (${o.heading})", o.heading.y > 0.55)
        assertTrue("mantle out by day", o.extension > 0.9)
        assertEquals(GiantClamBrain.PAR_LOW_CROCEA, GiantClamBrain.parLow(o), 0.0)
        // at a day-average of 220 PAR a maxima is fine, a crocea loses its algae
        val m = sim("tridacna_maxima", 16.0, 12.0).second
        o.bleach = 0.0; m.bleach = 0.0
        repeat(10) { GiantClamBrain.symbionts(o, 26.0, 220.0, true, 86400.0); GiantClamBrain.symbionts(m, 26.0, 220.0, true, 86400.0) }
        assertTrue("crocea pales (${o.bleach})", o.bleach > 0.02)
        assertEquals("maxima keeps its colour", 0.0, m.bleach, 1e-9)
    }

    private val mantis = "odontodactylus_scyllarus"

    @Test fun mantisSheetAndShop() {
        val db = SpeciesCatalog.fromClasspath(listOf(mantis))
        assertEquals(BrainKind.MANTIS, db[mantis].brainKind)
        assertEquals(BrainKind.MANTIS, SpeciesCatalog.fallback(mantis).brainKind)
        assertEquals("models/mantis_shrimp.glb", db[mantis].model)
        assertTrue(LimbRig.forKind(BrainKind.MANTIS, 1L) is ShrimpLimbs)
        assertTrue(Stocking.entry(mantis) != null)
        assertEquals(180 * PriceBook.SCALE, PriceBook.listing(mantis)!!.price)
        assertEquals(7, PriceBook.listing(mantis)!!.level)
        val sim = TestTank.sim(DefaultStock.all, seed = 3L)
        assertTrue(Stores.warning(sim, mantis)!!.contains("snails"))
    }

    /** Every visible station of the body (head to tail fan) is in open water: off the rock, the sand and the glass. */
    private fun assertClear(sim: Simulation, o: Organism, t: Double) {
        if (!o.visible) return
        for (k in 0..4) {
            val p = o.pos - o.heading * (o.lengthCm * k / 4.0)
            assertTrue("t=$t ${sim.agentOf(o)?.brain?.utility?.currentLabel} station $k in rock/glass (${sim.rock.envDist(p)}) at $p", sim.rock.envDist(p) > -0.4)
            assertTrue("t=$t ${sim.agentOf(o)?.brain?.utility?.currentLabel} station $k under the sand at $p", p.y > sim.rock.sandTopCm - 0.6)
        }
    }

    /** It digs its U-shaped burrow in the open sand near the rock and spends the day at a mouth, watching or digging. */
    @Test fun mantisDigsAUBurrowAndWatches() {
        val (sim, o) = sim(mantis, 10.0, 12.0)
        o.hunger = 0.0
        val b = sim.agentOf(o)!!.brain as MantisShrimpBrain
        var seen = 0; var n = 0
        sim.advance(1800.0, behaviorDt = 0.05) { n++; if (o.visible) seen++; if (n % 20 == 0) assertClear(sim, o, sim.simTimeSec) }
        val (a, c) = b.burrowMouths
        println("mantis burrow %s..%s span %.1f, loads %d, out %.0f%%, at %s".format(a, c, a.distanceTo(c), b.loads, 100.0 * seen / n, o.pos))
        assertTrue("two openings about 1.5 body lengths apart", a.distanceTo(c) in 12.0..24.0)
        for (m in listOf(a, c)) {
            assertTrue("opening on open sand ($m)", sim.rock.sdf.dist(m.withY(sim.rock.sandTopCm + 1.0)) > 2.0)
            val side = m + Vec3(-(c - a).z, 0.0, (c - a).x).normalized(Vec3.Z) * 5.0
            assertTrue("a pit at the opening", sim.sand.surfaceY(m.x, m.z) < sim.sand.surfaceY(side.x, side.z) - 0.3)
        }
        assertTrue("dug sand out (${b.loads})", b.loads >= 3)
        assertTrue("out at the mouth much of the day", seen > n / 5)
        assertTrue("and rests down the burrow too", seen < n * 19 / 20)
    }

    /** A hungry mantis stalks a hermit crab over the sand, hammers it with its clubs until the shell cracks and eats it. */
    @Test fun mantisSmashesAHermit() {
        val (sim, o) = sim(mantis, 10.0, 12.0)
        val b = sim.agentOf(o)!!.brain as MantisShrimpBrain
        val (m, far) = b.burrowMouths
        sim.species.ensure("clibanarius_tricolor")
        sim.addFromSeed(LivestockSeed("clibanarius_tricolor", "Snack", 2.0, id = "h"))
        val h = sim.organisms.first { it.id == "h" }
        // out on the open sand in front of the opening
        val q = m + (m - far).withY(0.0).normalized() * 8.0
        h.pos = q.withY(sim.sand.surfaceY(q.x, q.z) + 0.8)
        o.hunger = 0.8; o.satietyUntil = 0.0
        var smashT = -1.0; var n = 0
        val limbs = o.limbRig as ShrimpLimbs
        sim.advance(1800.0, behaviorDt = 0.05) {
            n++
            if (limbs.smashing && smashT < 0) smashT = sim.simTimeSec
            if (n % 20 == 0) assertClear(sim, o, sim.simTimeSec)
        }
        println("mantis strikes %d, kills %d, club strikes %d, hermit alive %s, hunger %.2f".format(b.strikes, b.kills, limbs.smashes, h.alive, o.hunger))
        assertTrue("struck with its clubs", b.strikes >= 2 && limbs.smashes >= 2)
        assertTrue("ate the hermit", !h.alive && h !in sim.organisms)
        assertTrue("fed", o.hunger < 0.5)
    }

    /** One strike throws both clubs out to the target within a tick and folds them back within half a second. */
    @Test fun clubStrikeIsFastAndFoldsBack() {
        val (sim, o) = sim(mantis, 10.0, 12.0)
        val limbs = o.limbRig as ShrimpLimbs
        fun reach(i: Int) = limbs.out.let { sqrt((it[i * 4 + 1] * it[i * 4 + 1] + it[i * 4 + 2] * it[i * 4 + 2] + it[i * 4 + 3] * it[i * 4 + 3]).toDouble()) }
        limbs.smash(o, o.pos + o.heading * 3.0)
        limbs.plant(o, sim.crawlSurface, 0.05)
        assertTrue("clubs out after one tick (${reach(13)}, ${reach(14)})", reach(13) > 0.1 && reach(14) > 0.1)
        repeat(10) { limbs.plant(o, sim.crawlSurface, 0.05) }
        assertTrue("folded again", reach(13) < 0.01 && !limbs.smashing)
    }
}
