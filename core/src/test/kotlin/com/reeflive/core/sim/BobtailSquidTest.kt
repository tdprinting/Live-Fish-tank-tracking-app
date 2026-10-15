package com.reeflive.core.sim

import com.reeflive.core.ai.BobtailSquidBrain
import com.reeflive.core.ai.Predation
import com.reeflive.core.geom.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Hummingbird bobtail squid, sandbox only (docs/research/cephalopod_squid.md, SIMULATION §20). */
class BobtailSquidTest {
    private val squid = "euprymna_berryi"

    private fun squidSim(startHour: Double, config: TankConfig = TankConfig(seed = 3L), seed: Long = 3L, extra: List<LivestockSeed> = emptyList()): Simulation {
        val sim = TestTank.sim(extra, seed = seed, startHour = startHour, config = config.copy(seed = seed))
        sim.species.ensure(squid)
        sim.addFromSeed(LivestockSeed(squid, "Squid", 6.0, id = "sq1"))
        return sim
    }

    private fun Simulation.squidOrg() = organisms.first { it.species.id == squid }
    private fun Simulation.squidBrain() = agentOf(squidOrg())!!.brain as BobtailSquidBrain

    @Test fun sheetAndCatalog() {
        val cat = SpeciesCatalog.fromClasspath(listOf(squid))
        val sp = cat[squid]
        assertEquals(BrainKind.BOBTAIL_SQUID, sp.brainKind)
        assertEquals("models/bobtail_squid.glb", sp.model)
        assertEquals("NOCTURNAL", sp.activity)
        assertTrue("a mollusc, not a fish", !sp.brainKind.isFish && sp.kind == "INVERT")
        assertTrue("mantle ~3.5 cm, ~6 cm with arms", sp.typicalAdultCm in 5.0..7.0 && sp.maxLengthCm <= 10.0)
        assertTrue("lives months, not years", sp.lifespanYears <= 0.75)
        assertTrue(Stocking.entry(squid) != null)
        assertTrue("not in the owner's tank", DefaultStock.all.none { it.speciesId == squid })
        assertEquals(Gait.FIN_JET, MotionProfiles.of(BrainKind.BOBTAIL_SQUID).gait)
        // the cooler-water band replaces the generic invert one
        val t = HealthModel.tolerancesFor(sp)["temp"]!!
        assertTrue("optimum tops out near 25 °C: ${t.optHi}", t.optHi!! <= 26.0)
    }

    @Test fun honestCompatibilityWarningsForTeesTank() {
        val tee = DefaultStock.all.map { it.speciesId }
        val w = compatibilityWarnings(squid, tee, 227.0)
        assertTrue(w.any { it.code == "squid_predator_fish" && it.severity == CompatibilityWarning.Severity.SEVERE })
        assertTrue(w.any { it.code == "squid_eats_crustaceans" })
        assertTrue(w.any { it.code == "squid_busy_fish" })
        assertTrue(w.any { it.code == "squid_short_life" })
        // a species tank: only the life-span note
        assertEquals(listOf("squid_short_life"), compatibilityWarnings(squid, emptyList(), 100.0).map { it.code })
        // adding the hawkfish to a tank that has a squid
        assertTrue(compatibilityWarnings("cirrhitichthys_falco", listOf(squid), 227.0).any { it.code == "squid_predator_fish" })
        val db = com.reeflive.core.species.SpeciesDb.load()
        assertTrue(db.interactions.risk(squid, "cirrhitichthys_falco").level >= com.reeflive.core.species.Risk.HIGH.level)
        assertEquals("euprymna_berryi", db.interactions.entry(squid, "lysmata_amboinensis")?.threat)
    }

    @Test fun buriesInTheSandByDayWithItsEyesOut() {
        val sim = squidSim(12.5)
        val o = sim.squidOrg()
        val b = sim.squidBrain()
        sim.advance(120.0)
        assertTrue("buried", b.buried)
        assertEquals(5, o.posture)
        val surface = sim.sand.surfaceY(o.pos.x, o.pos.z)
        val half = 0.5 * o.species.depthRatio * o.lengthCm
        assertTrue("mantle below the sand line: y ${o.pos.y} surface $surface", o.pos.y < surface + half)
        assertTrue("eyes above it", o.pos.y + half > surface)
        assertTrue("no glow while buried", b.rig.glow < 0.05)
    }

    @Test fun neverGoesIntoTheSandExceptByDiggingIn() {
        // hovering and hunting before dawn, then burying at lights-on
        val sim = squidSim(6.0)
        val o = sim.squidOrg()
        val b = sim.squidBrain()
        var worst = Double.MAX_VALUE
        sim.advance(4 * 3600.0) {
            if (o.posture == 5) return@advance
            val p = o.toPose()
            for (i in 0 until p.spine.size / 3) {
                val x = p.spine[i * 3].toDouble(); val z = p.spine[i * 3 + 2].toDouble()
                worst = minOf(worst, p.spine[i * 3 + 1] - sim.sand.surfaceY(x, z))
            }
        }
        assertTrue("its body went ${"%.2f".format(-worst)} cm into the sand while swimming", worst > 0.0)
        assertTrue("buried by mid-morning", b.buried && b.burials >= 1)
        assertTrue("in a trench it dug", sim.sand.buriedOf(o.id) != null)
    }

    @Test fun emergesAtNightAndHoversWithFinsAndGlow() {
        val sim = squidSim(22.0)
        val o = sim.squidOrg()
        val b = sim.squidBrain()
        var finPhaseMoved = false
        val f0 = b.rig.finPhase
        var maxY = 0.0
        sim.advance(900.0) {
            if (kotlin.math.abs(b.rig.finPhase - f0) > 0.05) finPhaseMoved = true
            maxY = maxOf(maxY, o.pos.y - sim.rock.sandTopCm)
        }
        assertFalse(b.buried)
        assertTrue("swims up off the sand: $maxY cm", maxY > 3.0)
        assertTrue("fins beating", finPhaseMoved && b.rig.finAmp > 0.2)
        assertTrue("counter-illumination glow ${b.rig.glow}", b.rig.glow > 0.1)
        val pose = o.toPose()
        assertEquals(4, pose.motionMode)
        assertEquals(20, pose.limbs.size)
    }

    @Test fun huntsPodsAtNightWithTentacleStrikes() {
        val sim = squidSim(22.0)
        val o = sim.squidOrg()
        val b = sim.squidBrain()
        o.hunger = 0.9
        var tentacleOut = 0.0
        sim.advance(1800.0) { tentacleOut = maxOf(tentacleOut, b.rig.tentacle) }
        assertTrue("strikes ${b.strikes}", b.strikes >= 3)
        assertTrue("caught pods ${b.podCatches}", b.podCatches >= 1)
        assertTrue("tentacles shot out: $tentacleOut", tentacleOut > 0.6)
        assertTrue("hunger fell: ${o.hunger}", o.hunger < 0.8)
    }

    @Test fun eatsASmallShrimpButNotAnAdultCleaner() {
        var eaten = 0
        for (seed in 1L..3L) {
            val sim = squidSim(22.0, TankConfig(predationEnabled = true), seed)
            val o = sim.squidOrg()
            o.hunger = 0.95
            sim.species.ensure("lysmata_amboinensis")
            val small = sim.addFromSeed(LivestockSeed("lysmata_amboinensis", "Juvenile", 3.0, id = "js$seed"))
            val b = sim.squidBrain()
            assertTrue(b.preyWeight(small) > 0)
            small.placeAt(o.pos + Vec3(6.0, -1.0, 0.0), Vec3.X)
            sim.advance(3600.0)
            if (!small.alive) eaten++
        }
        assertTrue("small shrimp eaten in $eaten of 3 nights", eaten >= 1)
        val sim = squidSim(22.0)
        sim.species.ensure("lysmata_amboinensis")
        val adult = sim.addFromSeed(LivestockSeed("lysmata_amboinensis", "Adult", 6.5, id = "ad"))
        assertEquals(0.0, sim.squidBrain().preyWeight(adult), 0.0)
    }

    @Test fun inksAndJetsBackwardWhenStartled() {
        val sim = squidSim(22.0)
        val o = sim.squidOrg()
        val b = sim.squidBrain()
        sim.advance(600.0)
        val start = o.pos
        // tap the nearest pane (front or back glass)
        val tapAt = Vec3(start.x, start.y, if (start.z > 0.5 * sim.rock.widthCm) sim.rock.widthCm else 0.0)
        sim.enqueue(Command.TapGlass(tapAt))
        var maxSpeed = 0.0
        var faceTap = 0.0
        sim.advance(3.0) { maxSpeed = maxOf(maxSpeed, o.vel.length()); if (b.rig.jet > 0.5) faceTap = maxOf(faceTap, o.heading.dot((tapAt - o.pos).normalized())) }
        assertTrue("jetted ${b.jets}", b.jets >= 1)
        assertTrue("inked ${b.inkPuffs}", b.inkPuffs >= 1 && b.rig.inking)
        assertTrue("fast: $maxSpeed cm/s", maxSpeed > 15.0)
        assertTrue("moved away from the tap", o.pos.distanceTo(tapAt) > start.distanceTo(tapAt))
        assertTrue("mantle first, arms toward the threat: $faceTap", faceTap > 0.3)
        // the ink pose reaches the renderer
        val pose = o.toPose()
        assertTrue("ink density sent", pose.limbs[19] > 0f)
    }

    @Test fun shyOfTheHawkfishAndHiddenFromItInTheSand() {
        val sim = squidSim(22.0, TankConfig(predationEnabled = true), extra = listOf(TestTank.seed("hawk")))
        val o = sim.squidOrg()
        val b = sim.squidBrain()
        val hawk = sim.organisms.first { it.brainKind == BrainKind.HAWK }
        // the hawk sits right beside it
        sim.advance(60.0)
        hawk.placeAt(o.pos + Vec3(4.0, 0.0, 0.0), Vec3.X * -1.0)
        sim.advance(5.0)
        assertTrue("jetted from the hawk", b.jets >= 1)
        // buried, the hawk cannot find it
        o.posture = 5
        assertEquals(0.0, Predation.preyWeight(hawk, o, true), 0.0)
        assertEquals(0.0, Predation.threatRadius(hawk, o), 0.0)
        o.posture = 0
        assertTrue(Predation.preyWeight(hawk, o, true) > 0)
    }

    @Test fun ventsItsSymbiontsAtDawn() {
        val sim = squidSim(LightScheduleHelper.beforeLightsOn())
        val b = sim.squidBrain()
        sim.advance(1800.0, behaviorDt = 0.1)
        val before = b.symbionts
        sim.advance(4 * 3600.0, behaviorDt = 0.2)
        assertTrue("vented after lights-on: $before -> ${b.symbionts}", b.symbionts < 0.5 * before || b.symbionts < 0.6)
    }

    @Test fun livesOnPodsButStarvesOnDryRock() {
        val cfg = TankConfig(seed = 3L, autoFeed = emptyList())
        fun run(dry: Boolean): Organism {
            val sim = squidSim(8.0, cfg)
            val o = sim.squidOrg()
            o.acceptsPrepared = false
            if (dry) sim.water.micro.dryRock()
            sim.advanceAbstract(8 * 86400.0)
            return o
        }
        val fed = run(false)
        val dry = run(true)
        assertTrue("starves without pods: ${dry.condition} alive ${dry.alive}", !dry.alive || dry.condition < 0.7)
        assertTrue("holds condition on pods: ${fed.condition}", fed.alive && fed.condition > dry.condition + 0.1)
    }

    @Test fun addFromTheStockingGallery() {
        val sim = Simulation(TankConfig(seed = 3L), SpeciesCatalog(), TestTank.rock, 3L, 12 * 3600.0)
        sim.enqueue(Command.AddMany(squid, 3))
        sim.stepWall(0.05)
        val sq = sim.organisms.filter { it.species.id == squid }
        assertEquals(3, sq.size)
        assertTrue(sq.all { it.alive && it.species.common == "Hummingbird bobtail squid" })
        sim.advance(300.0)
        assertTrue("all three bury by day", sq.all { (sim.agentOf(it)!!.brain as BobtailSquidBrain).buried })
    }
}

/** Hour of the day well before the lights come on (default photoperiod centred on the midday constant). */
private object LightScheduleHelper {
    fun beforeLightsOn(): Double = LightSchedule.MIDDAY_MIN / 60.0 - LightSchedule.DEFAULT_PHOTOPERIOD_H / 2 - 1.5
}
