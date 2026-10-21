package com.reeflive.core.sim

import com.reeflive.core.ai.HarlequinShrimpBrain
import com.reeflive.core.ai.PistolShrimpBrain
import com.reeflive.core.ai.SexyShrimpBrain
import com.reeflive.core.ai.WatchmanGobyBrain
import com.reeflive.core.custom.economy.PriceBook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Fire, sexy, harlequin and tiger pistol shrimp (docs/research/44_shrimp_pack.md). */
class ShrimpPackTest {
    private val fire = "lysmata_debelius"
    private val sexy = "thor_amboinensis"
    private val harl = "hymenocera_picta"
    private val pistol = "alpheus_bellulus"

    private fun sim(hour: Double, vararg add: LivestockSeed, seed: Long = 5L): Simulation {
        val sim = TestTank.sim(emptyList(), seed = seed, startHour = hour)
        for (a in add) { sim.species.ensure(a.speciesId); sim.addFromSeed(a) }
        sim.advance(1.0, behaviorDt = 0.05)
        return sim
    }

    /** Planted feet that sit off the crawl surface, out of all planted feet, over [s] seconds. */
    private fun feetOff(sim: Simulation, o: Organism, s: Double): Pair<Int, Int> {
        val rig = o.limbRig as ShrimpLimbs
        var planted = 0; var off = 0
        sim.advance(s, behaviorDt = 0.05) {
            if (!o.attached || !o.visible || o.ridingOn != null) return@advance
            for (i in 0 until 6) {
                val f = rig.stanceFoot(i) ?: continue
                planted++
                if (abs(sim.crawlSurface.dist(f)) > 0.3) off++
            }
        }
        return off to planted
    }

    @Test fun sheetsRigsAndStore() {
        val db = SpeciesCatalog.fromClasspath(listOf(fire, sexy, harl, pistol))
        val kinds = mapOf(fire to BrainKind.FIRE_SHRIMP, sexy to BrainKind.SEXY_SHRIMP, harl to BrainKind.HARLEQUIN_SHRIMP, pistol to BrainKind.PISTOL_SHRIMP)
        val models = mapOf(fire to "fire_shrimp", sexy to "sexy_shrimp", harl to "harlequin_shrimp", pistol to "pistol_shrimp")
        for ((id, k) in kinds) {
            assertEquals(k, db[id].brainKind)
            assertEquals(k, SpeciesCatalog.fallback(id).brainKind)
            assertEquals("models/${models[id]}.glb", db[id].model)
            assertTrue(k.isShrimp)
            assertTrue(LimbRig.forKind(k, 1L) is ShrimpLimbs)
            assertTrue(Stocking.entry(id) != null)
        }
        // a fire shrimp is a Lysmata but not the skunk cleaner
        assertEquals(BrainKind.CLEANER_SHRIMP, BrainKind.byId("lysmata_amboinensis"))
        assertEquals(60, PriceBook.listing(fire)!!.price)
        assertEquals(19, PriceBook.listing(sexy)!!.price)
        assertEquals(65, PriceBook.listing(harl)!!.price)
        assertEquals(45, PriceBook.listing(pistol)!!.price)
    }

    @Test fun fireShrimpKeepsADarkCaveByDayAndWalksTheRockAtNight() {
        val day = sim(16.0, LivestockSeed(fire, "Blaze", 3.5, id = "f1"))
        val d = day.organisms.first { it.id == "f1" }
        day.advance(1200.0, behaviorDt = 0.05)
        assertTrue("in its cave by day: ${d.behaviorName}", d.behaviorName.contains("cave"))
        assertTrue("a shaded spot (cover ${day.rock.sdf.cover(d.pos)})", day.rock.sdf.cover(d.pos) > 0.25)

        val night = sim(1.0, LivestockSeed(fire, "Blaze", 3.5, id = "f1"))
        val o = night.organisms.first { it.id == "f1" }
        o.hunger = 0.6
        var moved = 0.0; var last = o.pos
        val (off, planted) = feetOff(night, o, 1800.0)
        night.advance(1800.0, behaviorDt = 0.05) { moved += o.pos.distanceTo(last); last = o.pos }
        println("fire shrimp: moved %.0f cm at night, feet off $off / $planted".format(moved))
        assertTrue("walks about at night ($moved cm)", moved > 15)
        assertTrue("feet planted on the surface ($off / $planted)", off < planted * 0.03)
    }

    @Test fun sexyShrimpGroupDancesOnItsAnemoneWithTheTailWagging() {
        val sim = sim(11.0, LivestockSeed("entacmaea_quadricolor", "Bubbles", 8.0, id = "an"),
            LivestockSeed(sexy, "S1", 1.5, id = "s1"), LivestockSeed(sexy, "S2", 1.5, id = "s2"), LivestockSeed(sexy, "S3", 1.5, id = "s3"))
        val an = sim.organisms.first { it.id == "an" }
        val shrimp = sim.organisms.filter { it.brainKind == BrainKind.SEXY_SHRIMP }
        var wag = 0.0
        val s1 = shrimp.first()
        val (off, planted) = feetOff(sim, s1, 600.0)
        sim.advance(600.0, behaviorDt = 0.05) { wag = maxOf(wag, s1.bend) }
        for (s in shrimp) {
            val b = sim.agentOf(s)!!.brain as SexyShrimpBrain
            assertTrue("${s.nickname} lives on the anemone", b.hostAnemone === an)
            assertTrue("${s.nickname} beside it (${s.pos.distanceTo(an.pos)} cm)", s.pos.distanceTo(an.pos) < 12.0)
        }
        println("sexy shrimp: wag %.2f, feet off $off / $planted".format(wag))
        assertTrue("tail wagging", wag > 0.1)
        assertTrue("feet planted on the surface ($off / $planted)", off < planted * 0.03)
    }

    @Test fun harlequinPairFlipsAStarAndEatsIt() {
        val sim = sim(21.0, LivestockSeed(harl, "Harley", 4.0, id = "h1"), LivestockSeed(harl, "Quinn", 3.8, id = "h2"),
            TestTank.seed("asterina1"), TestTank.seed("asterina2"))
        val h = sim.organisms.filter { it.brainKind == BrainKind.HARLEQUIN_SHRIMP }
        h.forEach { it.hunger = 0.8 }
        val stars0 = sim.organisms.count { it.alive && it.brainKind == BrainKind.ASTERINA }
        val brains = h.map { sim.agentOf(it)!!.brain as HarlequinShrimpBrain }
        var flipped = false
        var t = 0.0
        while (t < 8 * 3600 && brains.sumOf { it.starsEaten } == 0) {
            sim.advance(60.0, behaviorDt = 0.1) {
                brains.forEach { b -> b.meal?.let { s -> if (s.heldBy != null) flipped = true } }
            }
            t += 60
        }
        val stars1 = sim.organisms.count { it.alive && it.brainKind == BrainKind.ASTERINA }
        println("harlequins: caught ${brains.sumOf { it.starsCaught }}, eaten ${brains.sumOf { it.starsEaten }}, shares ${brains.sumOf { it.mealShares }} in ${t / 3600} h; stars $stars0 -> $stars1")
        assertTrue("flipped and held a star", flipped)
        assertTrue("ate a star", brains.sumOf { it.starsEaten } >= 1)
        assertEquals(stars0 - brains.sumOf { it.starsEaten }, stars1)
        // fed: no more hunting for days
        assertTrue(h.any { sim.simTimeSec < it.satietyUntil })
    }

    @Test fun harlequinStarvesOnFoodAndTheStoreWarnsWithoutStars() {
        val sim = sim(12.0, LivestockSeed(harl, "Harley", 4.0, id = "h1"))
        val o = sim.organisms.first { it.id == "h1" }
        o.hunger = 0.8
        sim.enqueue(Command.Feed(FoodKind.MYSIS, 0.5, o.pos + com.reeflive.core.geom.Vec3(0.0, 3.0, 0.0)))
        sim.advance(600.0, behaviorDt = 0.1)
        assertTrue("mysis does not feed it (${o.hunger})", o.hunger > 0.75)
        assertTrue(com.reeflive.core.custom.economy.Stores.warning(sim, harl)!!.contains("starfish"))
    }

    @Test fun pistolShrimpDigsForItsWatchmanGobyAndDucksAtTheTailFlick() {
        val sim = sim(9.0, LivestockSeed("cryptocentrus_cinctus", "Watchy", 7.0, id = "g"), LivestockSeed(pistol, "Bang", 4.0, id = "p"))
        val g = sim.organisms.first { it.id == "g" }
        val p = sim.organisms.first { it.id == "p" }
        val pb = sim.agentOf(p)!!.brain as PistolShrimpBrain
        val gb = sim.agentOf(g)!!.brain as WatchmanGobyBrain
        sim.advance(2 * 3600.0, behaviorDt = 0.05)
        println("pistol: goby ${pb.goby?.nickname}, loads ${pb.loads}, out %.0f s, antenna on goby %.0f s, snaps ${pb.snapsMade}".format(pb.outS, pb.contactS))
        assertTrue("paired with the goby", pb.goby === g && gb.partnerShrimp === p)
        assertTrue("bulldozed sand (${pb.loads})", pb.loads >= 10)
        assertTrue("antenna on the goby while out", pb.contactS > 0.7 * pb.outS)
        // the flick: the goby dives, the shrimp ducks
        val ducks = pb.ducks
        var gone = false
        sim.agentOf(g)!!.let { a -> a.brain.onTap(a.ctx, g.pos + com.reeflive.core.geom.Vec3(3.0, 0.0, 0.0), 0.8) }
        sim.advance(5.0, behaviorDt = 0.05) { if (!p.visible) gone = true }
        assertTrue("goby flicked", gb.alarms > 0)
        assertTrue("ducked into the burrow", pb.ducks > ducks || gone)
    }
}
