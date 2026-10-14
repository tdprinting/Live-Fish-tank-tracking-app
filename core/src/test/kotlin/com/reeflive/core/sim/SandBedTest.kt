package com.reeflive.core.sim

import com.reeflive.core.ai.DiamondGobyBrain
import com.reeflive.core.ai.GobyBrain
import com.reeflive.core.geom.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Dynamic sand bed, suspended clouds and the diamond goby (SIMULATION §16, research 16). */
class SandBedTest {
    private val diamond = "valenciennea_puellaris"

    private fun bed() = SandBed(121.9, 33.0, 3.0, 1L)

    @Test fun settlingVelocitiesMatchFergusonChurch() {
        // 0.5 mm aragonite ≈ 7–9 cm/s, 0.2 mm ≈ 2–3.5, 63 µm silt ≈ 0.25–0.45, detritus floc ≈ 0.1–0.5 (marine snow range)
        assertTrue(Grain.SAND.settlingCmS in 7.0..9.5)
        assertTrue(Grain.FINE.settlingCmS in 2.0..3.5)
        assertTrue(Grain.SILT.settlingCmS in 0.25..0.45)
        assertTrue(Grain.DETRITUS.settlingCmS in 0.1..0.5)
        // 1 mm special-grade sand ≈ 12–15 cm/s; quartz settles faster than an organic floc of the same size
        assertTrue(Settling.velocityCmS(1.0, Settling.ARAGONITE_DENSITY) in 12.0..15.0)
        assertTrue(Settling.velocityCmS(0.3, 2.65) > Settling.velocityCmS(0.3, 1.1) * 10)
    }

    @Test fun cloudsSettleWithinTheExpectedTime() {
        val s = bed()
        s.detritus.fill(1.2f)   // a dirty bed: plenty of detritus in the cloud
        val x = 60.0; val z = 20.0
        val before = s.meanHeight()
        s.puff(x, s.surfaceY(x, z) + 5.0, z, 60, 3.0, finesShare = 0.5)
        assertEquals(60, s.particleCount)
        var t = 0.0
        var coarseGoneAt = -1.0
        var allGoneAt = -1.0
        var finesUpAt5s = 0
        while (t < 120.0) {
            s.stepClouds(0.05, null)
            t += 0.05
            if (coarseGoneAt < 0 && s.suspended(Grain.SAND) + s.suspended(Grain.FINE) == 0) coarseGoneAt = t
            if (kotlin.math.abs(t - 5.0) < 0.026) finesUpAt5s = s.suspended(Grain.SILT) + s.suspended(Grain.DETRITUS)
            if (s.particleCount == 0) { allGoneAt = t; break }
        }
        // sand falls ~5 cm (+ the jet's rise) at 2.7–8 cm/s: down within ~3 s; the haze of silt and mulm takes 10–60 s
        assertTrue("coarse sand settled at $coarseGoneAt s", coarseGoneAt in 0.5..3.5)
        assertTrue("fines still up at 5 s: $finesUpAt5s", finesUpAt5s > 0)
        assertTrue("everything settled at $allGoneAt s", allGoneAt in 10.0..60.0)
        // a puff only moves sand: none is made or lost
        assertEquals(before, s.meanHeight(), 1e-3)
    }

    @Test fun strongFlowKeepsDetritusUpLonger() {
        fun lastDown(flow: Double): Double {
            val s = bed()
            s.detritus.fill(1.5f)
            s.puff(60.0, s.surfaceY(60.0, 20.0) + 4.0, 20.0, 80, 2.0, finesShare = 0.1)
            var t = 0.0
            val v = Vec3(flow, 0.0, 0.0)
            while (s.suspended(Grain.DETRITUS) > 0 && t < 400) { s.stepClouds(0.05) { _, _, _ -> v }; t += 0.05 }
            return t
        }
        assertTrue(lastDown(15.0) > lastDown(0.0))
    }

    @Test fun relaxSmoothsFasterInStrongFlowAndFreshnessFades() {
        fun afterDay(flow: Double): Pair<Double, Double> {
            val s = bed()
            s.dig(40.0, 20.0, 3.0, 2.0)
            repeat(1440) { s.relax(1.0, flow, 0.2) }
            return s.cellHeight(40.0, 20.0) to s.freshAt(40.0, 20.0)
        }
        val (still, fresh) = afterDay(0.0)
        val (strong, _) = afterDay(2.0)
        assertTrue("pit stays in still water: $still", still < -0.1)
        assertTrue("strong flow smooths the pit: $strong vs $still", strong > still)
        assertTrue("turned sand fades within a day: $fresh", fresh < 0.05)
    }

    @Test fun nassariusPloughsATrailAndBuryingThrowsACrater() {
        val sim = TestTank.sim(listOf(TestTank.seed("nass1")))
        val o = sim.organisms.first { it.brainKind == BrainKind.NASSARIUS }
        val z = 31.0  // the open sand lane in front of the left stack (rockscape LANES[0])
        o.posture = 0
        for (i in 0..40) {
            o.pos = Vec3(21.0 + i * 0.25, sim.rock.sandTopCm + 0.4, z)
            sim.sandWork.step(listOf(o))
        }
        assertTrue(sim.sandWork.nassariusMarks >= 10)
        assertTrue("trail is freshly turned: ${sim.sand.freshAt(26.0, z)}", sim.sand.freshAt(26.0, z) > 0.3)
        assertTrue("trail centre is ploughed down", sim.sand.cellHeight(26.0, z) < 0.0)
        o.posture = 5
        sim.sandWork.step(listOf(o))
        assertEquals(1L, sim.sandWork.buryEvents)
        assertTrue("burying throws a puff", sim.sand.particleCount > 0)
    }

    @Test fun hungryNassariusChangesSandCellsWhenFed() {
        val sim = TestTank.sim(listOf(TestTank.seed("nass1"), TestTank.seed("nass2")), startHour = 22.0)
        for (o in sim.organisms) o.hunger = 0.9
        sim.feed(FoodKind.PRAWN, 0.5, Vec3(60.0, 20.0, 26.0))
        sim.advance(900.0)
        assertTrue("nassarius left trails or craters", sim.sandWork.nassariusMarks + sim.sandWork.buryEvents > 0)
        assertTrue(sim.sand.fresh.any { it > 0.3f })
    }

    @Test fun engineerGobyDigsAPitAndBuildsAMound() {
        val sim = TestTank.sim(listOf(TestTank.seed("goby")), startHour = 11.0)
        val goby = sim.organisms.first()
        val brain = sim.agentOf(goby)!!.brain as GobyBrain
        sim.advance(1800.0)
        assertTrue("goby spat sand", brain.sandEvents > 0)
        val pit = brain.digPoint
        assertTrue("pit at the burrow mouth: ${sim.sand.cellHeight(pit.x, pit.z)}", sim.sand.cellHeight(pit.x, pit.z) < 0.0)
        assertTrue("a mound somewhere", sim.sand.height.any { it > 0.1f })
        assertTrue(sim.sand.movedCm3 > 0)
    }

    private fun diamondSim(startHour: Double, config: TankConfig = TankConfig(seed = 3L), pair: Boolean = false): Simulation {
        val sim = TestTank.sim(emptyList(), seed = 3L, startHour = startHour, config = config)
        sim.species.ensure(diamond)
        sim.addFromSeed(LivestockSeed(diamond, "Diamond", 12.0, id = "dg1"))
        if (pair) sim.addFromSeed(LivestockSeed(diamond, "Diamond 2", 11.0, id = "dg2"))
        return sim
    }

    @Test fun diamondGobyHasItsOwnBrainAndIsInTheOwnersTank() {
        val cat = SpeciesCatalog.fromClasspath(listOf(diamond))
        assertEquals(BrainKind.DIAMOND_GOBY, cat[diamond].brainKind)
        assertEquals("models/diamond_goby.glb", cat[diamond].model)
        assertTrue(cat[diamond].maxLengthCm in 14.0..18.0)
        assertTrue(Stocking.entry(diamond) != null)
        assertTrue("in the owner's tank (2026-10)", DefaultStock.all.count { it.speciesId == diamond } == 1)
        assertTrue(compatibilityWarnings(diamond, emptyList(), 110.0).any { it.code == "sand_sifter_tank_size" })
        assertTrue(compatibilityWarnings(diamond, listOf(diamond, diamond), 400.0).any { it.code == "diamond_goby_pair" })
        assertTrue(compatibilityWarnings(diamond, listOf(diamond), 400.0).none { it.code == "diamond_goby_pair" })
    }

    @Test fun diamondGobySiftsSandAndPuffsItOutOfItsGills() {
        val sim = diamondSim(11.0)
        val g = sim.organisms.first()
        val b = sim.agentOf(g)!!.brain as DiamondGobyBrain
        sim.advance(900.0)
        assertTrue("mouthfuls ${b.mouthfuls}", b.mouthfuls >= 20)
        assertTrue("puffs ${b.puffs}", b.puffs >= 15)
        assertTrue(b.stations >= 2)
        assertTrue("sifting cleans detritus", sim.sand.siftedLoad > 0)
        assertTrue("freshly turned sand", sim.sand.fresh.count { it > 0.3f } >= 5)
        assertTrue("the bed is still level on average", kotlin.math.abs(sim.sand.meanHeight()) < 0.05)
        assertTrue(g.alive)
    }

    @Test fun diamondGobyPairSharesABurrow() {
        val sim = diamondSim(11.0, pair = true)
        val (a, b) = sim.organisms.map { sim.agentOf(it)!!.brain as DiamondGobyBrain }
        assertTrue(a.mate === b.o && b.mate === a.o)
        assertEquals(a.burrow, b.burrow)
    }

    @Test fun diamondGobyDustsLowCorals() {
        val sim = diamondSim(11.0)
        val b = sim.agentOf(sim.organisms.first())!!.brain as DiamondGobyBrain
        // a frag on the sand right where it feeds
        sim.species.ensure("zoanthus_sp")
        val home = b.burrow
        for (dx in listOf(-12.0, -6.0, 0.0, 6.0, 12.0)) for (dz in listOf(-6.0, 0.0, 6.0))
            sim.addFromSeed(LivestockSeed("zoanthus_sp", "Frag", placement = "coral_zoa_front_center",
                offset = Vec3(home.x + dx, 4.0, (home.z + dz).coerceIn(4.0, 30.0)) - sim.rock.anchors.pos("coral_zoa_front_center", Vec3(60.0, 10.0, 20.0))))
        sim.advance(1200.0)
        assertTrue("sand landed on a low coral", b.coralsDusted > 0 && sim.sand.coralDustings > 0)
    }

    /**
     * Twelve days with no prepared food: on a mature bed it lives off the fauna (a 227 L bed with no refugium starts to
     * run out after ~10 days, research 16 §4); on dry rock and sterile sand it is thin within the fortnight.
     */
    @Test fun diamondGobyDeclinesWithoutSandFauna() {
        val cfg = TankConfig(seed = 3L, autoFeed = emptyList())
        fun run(dry: Boolean): Organism {
            val sim = diamondSim(8.0, cfg)
            val g = sim.organisms.first()
            g.acceptsPrepared = false
            if (dry) sim.water.micro.dryRock()
            sim.advanceAbstract(12 * 86400.0)
            return g
        }
        val mature = run(false)
        val sterile = run(true)
        assertTrue("starves on a sterile bed: condition ${sterile.condition}, alive ${sterile.alive}", !sterile.alive || sterile.condition < 0.6)
        assertTrue("holds condition on a living bed: ${mature.condition} vs ${sterile.condition}", mature.alive && mature.condition > 0.85)
    }

    @Test fun preyIndexFallsWhenTheBedIsSterile() {
        val sim = diamondSim(12.0)
        sim.advanceAbstract(3600.0)
        val living = sim.water.micro.sandPreyIndex()
        sim.water.micro.dryRock()
        assertTrue("mature bed index $living", living in 0.4..1.6)
        assertEquals(0.0, sim.water.micro.sandPreyIndex(), 1e-9)
    }
}
