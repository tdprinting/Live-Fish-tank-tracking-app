package com.reeflive.core.sim

import com.reeflive.core.ai.AnemoneBrain
import com.reeflive.core.geom.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rainbow bubble-tip anemone (docs/research/40_bubble_tip_anemone.md). */
class BubbleTipAnemoneTest {
    private val bta = "entacmaea_quadricolor"

    private fun btaSim(livestock: List<LivestockSeed> = emptyList(), seed: Long = 5L, startHour: Double = 11.0, cm: Double = 20.0): Simulation {
        val sim = TestTank.sim(livestock, seed = seed, startHour = startHour)
        sim.species.ensure(bta)
        sim.addFromSeed(LivestockSeed(bta, "Rainbow", cm, id = "bta1"))
        return sim
    }

    private fun Simulation.bta() = organisms.first { it.id == "bta1" }
    private fun Simulation.btaBrain() = agentOf(bta())!!.brain as AnemoneBrain

    private fun Simulation.hours(h: Double) { repeat((h * 60).toInt()) { advanceAbstract(60.0) } }

    @Test fun sheetAndCatalog() {
        val sp = SpeciesCatalog.fromClasspath(listOf(bta))[bta]
        assertEquals(BrainKind.ANEMONE, sp.brainKind)
        assertEquals("models/bubble_tip_anemone.glb", sp.model)
        assertNotNull(Stocking.entry(bta))
        assertTrue("not in the owner's tank yet", DefaultStock.all.none { it.speciesId == bta })
        assertEquals(Gait.SESSILE, MotionProfiles.of(BrainKind.ANEMONE).gait)
    }

    @Test fun gripsRockWithItsFootAndOpensUp() {
        val sim = btaSim()
        val o = sim.bta()
        sim.advance(30.0)
        val s = sim.anemones.state(o)
        assertTrue("foot on rock: dist ${sim.rock.sdf.dist(s.foot)}", sim.rock.sdf.dist(s.foot) < 1.0)
        assertTrue("disc above the foot", o.pos.y > s.foot.y)
        assertTrue("in the water", sim.rock.inWater(o.pos, 0.0))
        assertEquals(6, o.toPose().motionMode)
    }

    @Test fun catchesFoodAndPassesItToTheMouth() {
        val sim = btaSim()
        val o = sim.bta()
        sim.advance(600.0)
        o.hunger = 0.6
        val b = sim.btaBrain()
        val up = sim.anemones.discUp(sim.anemones.state(o))
        sim.food.spawn(FoodKind.MYSIS, FoodKind.MYSIS.gramsPerParticle, o.pos + up * 3.0, sim.simTimeSec)
        sim.food.active().forEach { it.pos = o.pos + up * 3.0; it.vel = Vec3.ZERO }
        sim.advance(300.0)
        assertTrue("ate the mysis (meals ${b.meals})", b.meals >= 1)
        assertTrue("fed level rose", sim.anemones.state(o).fed > 0.2)
    }

    @Test fun stingsAFishThatBlundersIn() {
        val sim = btaSim(listOf(LivestockSeed("sphaeramia_nematoptera", "Derprick", 6.0, id = "card")))
        sim.advance(600.0)
        val fish = sim.organisms.first { it.id == "card" }
        val b = sim.btaBrain()
        val before = b.stings
        repeat(20) {
            fish.placeAt(sim.bta().pos + sim.anemones.discUp(sim.anemones.state(sim.bta())) * 1.5, fish.heading)
            sim.advance(0.3)
        }
        assertTrue("stung the cardinal", b.stings > before)
    }

    @Test fun stingsCoralsInReach() {
        val sim = btaSim(DefaultStock.all.filter { TestTank.catalog[it.speciesId].kind == "CORAL" })
        val o = sim.bta()
        sim.advance(5.0)
        val coral = sim.corals.first { it.kind != CoralKind.MACRO }
        // walk it next to a coral and hold it there
        val s = sim.anemones.state(o)
        val (p, n) = sim.anemones.surfaceUnder(coral.pos + coral.normal * 2.0)
        s.foot = p; s.normal = n; s.settled = true
        val t0 = coral.tissue
        sim.anemones.step(60.0)
        assertTrue("stinging list has the coral", coral.id in s.stinging)
        assertTrue("coral lost tissue ${coral.tissue} < $t0", coral.tissue < t0)
    }

    @Test fun splitsIntoTwoClonesThatDoNotFight() {
        val sim = btaSim(cm = 24.0)
        val o = sim.bta()
        sim.advance(5.0)
        val child = sim.anemones.split(o)
        assertEquals(2, sim.anemones.anemones().size)
        assertEquals(24.0 / kotlin.math.sqrt(2.0), o.lengthCm, 1e-6)
        assertEquals(o.lengthCm, child.lengthCm, 1e-6)
        assertEquals(sim.anemones.state(o).lineage, sim.anemones.state(child).lineage)
        sim.hours(2.0)
        assertTrue("clones do not sting each other", !sim.anemones.state(o).stingingAnemone)
    }

    @Test fun bleachesAfterDaysOfHeat() {
        val sim = btaSim()
        val s = sim.anemones.state(sim.bta())
        repeat(10 * 24) { sim.water.s.tempC = 29.5; sim.advanceAbstract(3600.0) }
        assertTrue("bleached ${s.bleach}", s.bleach > 0.5)
    }

    @Test fun clownsSwitchFromTheMushroomsToTheAnemoneOverTime() {
        val clowns = DefaultStock.all.filter { it.speciesId == "amphiprion_ocellaris" }
        val sim = btaSim(clowns)
        sim.advanceAbstract(60.0)
        val nemo = sim.organisms.first { it.id == "clown_f" }
        val marlin = sim.organisms.first { it.id == "clown_m" }
        assertEquals("still on the mushrooms at first", null, sim.anemones.hostOf(nemo))
        // pin the coin flips: both willing, a 1-day acclimation
        for (h in sim.anemones.hosts.values) { h.willing = true; h.adoptDays = 1.0 }
        sim.hours(24.0 * 6)
        assertEquals(sim.bta(), sim.anemones.hostOf(nemo))
        assertEquals(sim.bta(), sim.anemones.hostOf(marlin))
        assertTrue(sim.anemones.welcomes(sim.bta(), nemo))
    }

    @Test fun tentaclesFollowNestlingClownsSmoothly() {
        val sim = btaSim(DefaultStock.all.filter { it.speciesId == "amphiprion_ocellaris" })
        sim.advance(600.0)
        val o = sim.bta()
        for (h in sim.anemones.hosts.values) { h.willing = true; h.hostId = o.id; h.acclim = 1.0 }
        val clowns = sim.organisms.filter { it.species.id == "amphiprion_ocellaris" }
        val up = sim.anemones.discUp(sim.anemones.state(o))
        val (x, z) = AnemoneRig.basis(up)
        val rig = o.limbRig as AnemoneRig
        var prev: FloatArray? = null
        var maxStep = 0f
        var maxJump = 0.0
        var bothBent = 0
        // the pair circles through the crown, half a turn apart
        for (i in 0 until 400) {
            val a = i * 0.03
            clowns.forEachIndexed { j, f ->
                val ang = a + j * Math.PI
                f.placeAt(o.pos + up * 2.5 + x * (4.0 * kotlin.math.cos(ang)) + z * (4.0 * kotlin.math.sin(ang)), f.heading)
            }
            sim.advance(0.05)
            val l = rig.out
            prev?.let { p ->
                maxStep = maxOf(maxStep, kotlin.math.abs(l[10] - p[10]), kotlin.math.abs(l[11] - p[11]))
                if (l[10] > 0.3f) maxJump = maxOf(maxJump, Vec3(l[5] - p[5].toDouble(), l[6] - p[6].toDouble(), l[7] - p[7].toDouble()).length())
            }
            if (i > 100 && l[10] > 0.6f && l[11] > 0.6f) bothBent++
            prev = l.copyOf()
        }
        assertTrue("strength eases, no snaps (max step $maxStep)", maxStep < 0.2f)
        assertTrue("contact point glides with the fish (max jump $maxJump cm)", maxJump < 1.5)
        assertTrue("both clowns part the tentacles ($bothBent ticks)", bothBent > 200)
    }

    @Test fun hostedAnemoneBubblesAndALonelyOneGoesStringy() {
        val clowns = DefaultStock.all.filter { it.speciesId == "amphiprion_ocellaris" }
        fun run(hosted: Boolean): Double {
            val sim = btaSim(clowns)
            sim.advanceAbstract(60.0)
            val o = sim.bta()
            val s = sim.anemones.state(o)
            s.bubbliness = 0.9
            for (h in sim.anemones.hosts.values) { h.willing = hosted; if (hosted) { h.hostId = o.id; h.acclim = 1.0 } }
            sim.hours(48.0)
            return s.bubble
        }
        val withClowns = run(true)
        val alone = run(false)
        assertTrue("hosted bubbles more ($withClowns vs $alone)", withClowns > alone + 0.25)
    }

    @Test fun saveStateRoundTrips() {
        val sim = btaSim(DefaultStock.all.filter { it.speciesId == "amphiprion_ocellaris" })
        sim.advanceAbstract(60.0)
        val o = sim.bta()
        val s = sim.anemones.state(o)
        s.bleach = 0.4; s.settled = true; s.fed = 0.7
        val nemo = sim.organisms.first { it.id == "clown_f" }
        sim.anemones.hosts[nemo.id]!!.let { it.hostId = o.id; it.acclim = 1.0 }
        val saved = sim.organisms.associate { it.id to sim.anemones.saveState(it) }

        val sim2 = btaSim(DefaultStock.all.filter { it.speciesId == "amphiprion_ocellaris" })
        for (x in sim2.organisms) sim2.anemones.loadState(x, saved[x.id].orEmpty())
        sim2.anemones.relinkHosts()
        val s2 = sim2.anemones.state(sim2.bta())
        assertEquals(0.4, s2.bleach, 1e-9)
        assertEquals(0.7, s2.fed, 1e-9)
        assertTrue(s2.settled)
        assertEquals(s.foot.x, s2.foot.x, 1e-9)
        assertEquals(sim2.bta(), sim2.anemones.hostOf(sim2.organisms.first { it.id == "clown_f" }))
    }
}
