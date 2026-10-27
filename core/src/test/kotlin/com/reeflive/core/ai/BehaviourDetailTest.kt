package com.reeflive.core.ai

import com.reeflive.core.geom.Vec3
import com.reeflive.core.sim.BrainKind
import com.reeflive.core.sim.Command
import com.reeflive.core.sim.Evidence
import com.reeflive.core.sim.EvidenceLedger
import com.reeflive.core.sim.FoodKind
import com.reeflive.core.sim.HealthModel
import com.reeflive.core.sim.InvertEcology
import com.reeflive.core.sim.LivestockSeed
import com.reeflive.core.sim.ShrimpBreeding
import com.reeflive.core.sim.SimEvent
import com.reeflive.core.sim.Simulation
import com.reeflive.core.sim.SpeciesCatalog
import com.reeflive.core.sim.TestTank
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Research 10–15 behaviour detail (docs/research/10–15; SIMULATION §14). */
class BehaviourDetailTest {
    private fun Simulation.org(kind: BrainKind) = organisms.first { it.brainKind == kind }
    private fun Simulation.brainOf(kind: BrainKind) = agentOf(org(kind))!!.brain
    private fun stock(vararg ids: String) = ids.map { TestTank.seed(it) }

    // ------------------------------------------------------------------ evidence + sheets
    @Test
    fun ledgerLabelsTheUnsourcedQuotas() {
        fun level(sp: String, prefix: String) = EvidenceLedger.of(sp).first { it.name.startsWith(prefix) }.level
        assertEquals(Evidence.DESIGN_ESTIMATE, level("pseudochromis_indigo", "≥ 3 chases"))
        assertEquals(Evidence.DESIGN_ESTIMATE, level("sabellastarte_sp", "crown regrowth"))
        assertEquals(Evidence.DESIGN_ESTIMATE, level("labroides_dimidiatus", "WrasseBrain.OVERDUE_S"))
        assertEquals(Evidence.SOURCED, level("stenopus_hispidus", "clean per fish visit"))
        assertTrue(EvidenceLedger.byLevel(Evidence.ANALOGY).isNotEmpty())
    }

    @Test
    fun currentFishBaseMaxLengths() {
        val cat = SpeciesCatalog.fromClasspath(listOf("labroides_dimidiatus", "cirrhitichthys_falco"))
        assertEquals(14.0, cat["labroides_dimidiatus"].maxLengthCm, 0.0)
        assertEquals(8.0, cat["cirrhitichthys_falco"].maxLengthCm, 0.0)
    }

    // ------------------------------------------------------------------ ocellaris colour forms
    @Test
    fun clownColourFormsBehaveIdentically() {
        fun run(variant: String?): List<Vec3> {
            val seeds = DefaultClowns.map { it.copy(variant = variant) }
            val s = TestTank.sim(seeds, seed = 3L, startHour = 11.0)
            s.advance(120.0, behaviorDt = 0.1)
            return s.organisms.map { it.pos }
        }
        val orange = run(null)
        val black = run("darwin_black")
        orange.zip(black).forEach { (a, b) -> assertTrue("colour changed behaviour: $a vs $b", a.distanceTo(b) < 1e-9) }
    }

    // ------------------------------------------------------------------ banded coral shrimp
    @Test
    fun bandedShrimpCleansOnlyARareVisit() {
        val s = TestTank.sim(stock("banded", "tang"), seed = 2L, startHour = 23.0)
        val shrimp = s.org(BrainKind.BANDED_SHRIMP)
        val fish = s.org(BrainKind.TANG)
        val bb = s.brainOf(BrainKind.BANDED_SHRIMP) as BandedShrimpBrain
        val away = Vec3(20.0, 45.0, 25.0)
        repeat(150) {
            s.advance(1.0, behaviorDt = 0.1) { fish.pos = shrimp.pos + Vec3(0.0, -2.0, 2.0); fish.vel = Vec3.ZERO }
            s.advance(1.0, behaviorDt = 0.1) { fish.pos = away; fish.vel = Vec3.ZERO }
        }
        val v = bb.visitStats
        assertTrue("visits ${v.visits}", v.visits >= 100)
        assertTrue("cleans ${v.cleans}/${v.visits} should be rare (≈ 5.6 %)", v.cleans <= 0.15 * v.visits)
    }

    @Test
    fun bandedShrimpForaysFollowLightNotClock() {
        val s = TestTank.sim(stock("banded"), seed = 1L, startHour = 12.0)
        s.advance(1.0)
        val a = s.agentOf(s.org(BrainKind.BANDED_SHRIMP))!!
        val b = a.brain as BandedShrimpBrain
        a.ctx.p.light = 1.0; val bright = b.forayDrive(a.ctx)
        a.ctx.p.light = 0.15; val dim = b.forayDrive(a.ctx)
        a.ctx.p.light = 0.0; val dark = b.forayDrive(a.ctx)
        assertTrue("bright $bright < dim $dim < dark $dark", bright < dim && dim < dark)
    }

    @Test
    fun stenopusIntermoltIsAboutTwentyFiveDaysAt26C() {
        assertEquals(25.5, ShrimpBreeding.stenopusIntermoltDays(26.0), 1e-9)
        assertTrue(ShrimpBreeding.stenopusIntermoltDays(24.0) > 25.5)
        val s = TestTank.sim(stock("banded"), seed = 4L)
        s.advanceAbstract(600.0)
        val o = s.org(BrainKind.BANDED_SHRIMP)
        val days = (o.nextMoltAt - s.simTimeSec) / 86400
        assertTrue("first molt scheduled in $days d", days in 0.0..ShrimpBreeding.stenopusIntermoltDays(s.water.s.tempC) * 1.1)
    }

    @Test
    fun bandedPairRecognisesItsMateAndBreedsAfterTheFemaleMolts() {
        val f = LivestockSeed("stenopus_hispidus", "Her", 6.0, role = "FEMALE", placement = "SHRIMP_LEDGE", id = "cbsF")
        val m = LivestockSeed("stenopus_hispidus", "Him", 5.5, role = "MALE", placement = "SHRIMP_LEDGE", id = "cbsM")
        val x = LivestockSeed("stenopus_hispidus", "Stranger", 5.5, role = "MALE", placement = "SHRIMP_LEDGE", id = "cbsX")
        val s = TestTank.sim(listOf(f, m, x), seed = 5L)
        val her = s.organisms.first { it.id == "cbsF" }
        val him = s.organisms.first { it.id == "cbsM" }
        val stranger = s.organisms.first { it.id == "cbsX" }
        Social.rel(her, him, s.simTimeSec).familiarity = 0.8
        Social.rel(him, her, s.simTimeSec).familiarity = 0.8
        s.advanceAbstract(120.0)
        assertEquals(him.id, her.mateId)
        assertNull("the stranger is not a mate", stranger.mateId)
        val row = PairMatrix.row("cbs_cbs")!!
        assertFalse("mate is not attacked", PairMatrix.targetMatches(row, her, him))
        assertTrue("stranger can be", PairMatrix.targetMatches(row, her, stranger))
        // molt → mating → a fertile clutch on a ≈ 23-day clock
        her.nextMoltAt = s.simTimeSec + 60
        stranger.pos = Vec3(5.0, 40.0, 5.0)
        s.advanceAbstract(3 * 86400.0)
        val clutch = s.shrimpBreeding.clutchOf(her)
        assertTrue("mated after her molt (matings ${s.shrimpBreeding.matings})", clutch != null && clutch.fertile)
        val d = (clutch!!.hatchAt - clutch.laidAt) / 86400
        assertTrue("incubation $d d", d in ShrimpBreeding.INCUBATION_DAYS_MIN..ShrimpBreeding.INCUBATION_DAYS_MAX)
    }

    @Test
    fun lysmataNeedsAPartnerForFertileEggs() {
        fun clutch(n: Int): ShrimpBreeding.Clutch? {
            val seeds = (1..n).map { LivestockSeed("lysmata_amboinensis", "Skunk $it", 5.5, id = "sk$it") }
            val s = TestTank.sim(seeds, seed = 7L)
            val o = s.organisms.first()
            s.advanceAbstract(120.0)
            o.nextMoltAt = s.simTimeSec + 60
            s.advanceAbstract(2.5 * 86400)
            assertFalse("molt state is separate from eggs", o.molting)
            return s.shrimpBreeding.clutchOf(o)
        }
        val alone = clutch(1)
        val paired = clutch(2)
        assertTrue("isolated shrimp carries infertile eggs", alone != null && !alone.fertile)
        assertTrue("paired shrimp carries fertile eggs", paired != null && paired.fertile)
    }

    // ------------------------------------------------------------------ skunk cleaner shrimp
    @Test
    fun skunkRocksMoreInTheDarkAndTapsBeforeCleaning() {
        for (hour in listOf(11.0, 23.0)) {
            val s = TestTank.sim(stock("skunk", "tang"), seed = 4L, startHour = hour)
            s.advance(1.0)
            val a = s.agentOf(s.org(BrainKind.CLEANER_SHRIMP))!!
            val sb = a.brain as CleanerShrimpBrain
            val tang = s.org(BrainKind.TANG)
            if (hour > 20) assertTrue(sb.rockChance(a.ctx, false) > 0.12 && sb.rockChance(a.ctx, true) > 0.51)
            else assertEquals(0.12, sb.rockChance(a.ctx, false), 1e-6)
            sb.station.client = tang; sb.station.clientReady = true
            val at = sb.station.pos
            val ev = ArrayList<SimEvent>()
            s.advance(30.0, behaviorDt = 0.1) { tang.pos = at; tang.vel = Vec3.ZERO }
            ev += s.eventLog
            assertEquals("antenna tap first (hour $hour)", 1, sb.antennaTaps)
            assertTrue("cleaned at hour $hour", ev.any { it is SimEvent.Cleaned && it.clientId == tang.id })
            if (hour > 20) assertEquals("cleans at night too", 1, sb.nightCleans)
        }
    }

    // ------------------------------------------------------------------ cleaner wrasse
    @Test
    fun wrasseEncountersKeepFailuresInTheDenominator() {
        val s = TestTank.sim(stock("wrasse", "tang", "clown_f", "clown_m", "hawk"), seed = 2L, startHour = 10.0)
        s.advance(3600.0, behaviorDt = 0.1)
        val e = (s.brainOf(BrainKind.WRASSE) as WrasseBrain).encounters
        assertTrue("approaches ${e.approaches}", e.approaches > 0)
        assertTrue("accepted + refused ≤ approaches", e.accepted + e.refused + e.failedApproaches <= e.approaches)
        assertTrue("completed ${e.completed} + jolts ${e.jolts} ≤ accepted ${e.accepted}", e.completed + e.jolts <= e.accepted)
    }

    // ------------------------------------------------------------------ blue tang
    @Test
    fun tangPicksInOpenWater() {
        assertTrue(TangBrain.GRAZE_WEIGHT <= 1.0)
        val s = TestTank.sim(stock("tang"), seed = 3L, startHour = 11.0)
        s.advance(1800.0, behaviorDt = 0.1) { s.org(BrainKind.TANG).hunger = maxOf(0.7, s.org(BrainKind.TANG).hunger) }
        assertTrue("column snaps", (s.brainOf(BrainKind.TANG) as TangBrain).columnSnaps > 0)
    }

    // ------------------------------------------------------------------ cardinal
    @Test
    fun cardinalRangeFollowsTheLightRamp() {
        val s = TestTank.sim(stock("cardinal"), seed = 1L, startHour = 12.0)
        s.advance(1.0)
        val a = s.agentOf(s.org(BrainKind.CARDINAL))!!
        val cb = a.brain as CardinalBrain
        val r = listOf(0.29, 0.15, 0.05, 0.0).map { a.ctx.p.light = it; cb.forageRadius(a.ctx) }
        assertTrue("monotone ramp $r", r.zipWithNext().all { (x, y) -> y >= x } && r.last() == CardinalBrain.FORAGE_RADIUS && r.first() < r.last())
    }

    // ------------------------------------------------------------------ hawkfish
    @Test
    fun hawkStrikesHaveOutcomesAndKillOnlySoftOrSmallShrimp() {
        val s = TestTank.sim(stock("hawk", "skunk", "banded"), seed = 6L)
        s.predationEnabled = true
        s.advance(1.0)
        val ha = s.agentOf(s.org(BrainKind.HAWK))!!
        val hb = ha.brain as HawkBrain
        val hard = s.org(BrainKind.CLEANER_SHRIMP)
        hard.lastMoltAt = -1e9
        repeat(40) { hard.health = 1.0; hb.strikeOutcome(ha.ctx, hard) }
        assertTrue("hard adult shrimp survives strikes", hard.alive)
        assertEquals(0, hb.events.kills)
        assertTrue("misses ${hb.events.misses} injuries ${hb.events.injuries}", hb.events.misses > 0 && hb.events.injuries > 0)
        val soft = s.org(BrainKind.BANDED_SHRIMP)
        soft.lastMoltAt = s.simTimeSec
        var n = 0
        while (soft.alive && n++ < 40) hb.strikeOutcome(ha.ctx, soft)
        assertTrue("freshly molted shrimp can be killed", !soft.alive && hb.events.kills == 1)
    }

    // ------------------------------------------------------------------ blennies
    @Test
    fun lawnmowerFeedsOnTheWholeFilmMatrix() {
        val s = TestTank.sim(stock("lawnmower"), seed = 1L)
        s.advance(1.0)
        val a = s.agentOf(s.org(BrainKind.LAWNMOWER_BLENNY))!!
        val lb = a.brain as LawnmowerBlennyBrain
        s.water.s.algaeHair = 0.0
        s.water.s.detritus = 0.0
        val bare = lb.matrixQuality(a.ctx)
        s.water.s.detritus = 0.4
        val withDetritus = lb.matrixQuality(a.ctx)
        assertTrue("detritus counts as food without hair algae: $bare → $withDetritus", withDetritus > bare)
    }

    @Test
    fun tailspotMakesExcursionsAndReturns() {
        val s = TestTank.sim(stock("tailspot"), seed = 2L, startHour = 11.0)
        val o = s.org(BrainKind.PEEKER_BLENNY)
        s.advance(1800.0, behaviorDt = 0.1) { o.hunger = maxOf(o.hunger, 0.5) }
        val b = s.brainOf(BrainKind.PEEKER_BLENNY) as PeekerBlennyBrain
        assertTrue("excursions ${b.excursions} returns ${b.returns}", b.excursions > 0 && b.returns > 0)
    }

    // ------------------------------------------------------------------ hermits
    @Test
    fun smallerHermitsRarelyWinShellFights() {
        var fights = 0; var wins = 0
        for (sd in 1L..6L) {
            val s = TestTank.sim(listOf(LivestockSeed("clibanarius_tricolor", "small", 2.0, id = "h1"), LivestockSeed("clibanarius_tricolor", "big", 2.8, id = "h2")), seed = sd)
            s.advanceAbstract(120.0)
            val e = s.invertEcology
            val a = e.shellOf(s.organisms.first { it.id == "h1" })!!
            val d = e.shellOf(s.organisms.first { it.id == "h2" })!!
            repeat(20 * 24) {
                a.needCm = maxOf(a.needCm, a.shellCm * 1.15); d.shellCm = maxOf(d.shellCm, a.needCm * 1.4); d.needCm = minOf(d.needCm, d.shellCm * 0.5)
                d.moltedAt = -1e9; a.moltedAt = -1e9
                s.advanceAbstract(3600.0)
            }
            fights += e.smallAttackerFights; wins += e.smallAttackerWins
        }
        assertTrue("small-attacker fights $fights", fights >= 20)
        assertTrue("small attackers won $wins / $fights", wins <= 0.2 * fights)
        assertEquals(0.08, InvertEcology.SMALL_ATTACKER_WIN, 0.0)
    }

    // ------------------------------------------------------------------ Sally Lightfoot
    @Test
    fun crabFishPredationIsAnOptInScenario() {
        val s = TestTank.sim(stock("crab", "cardinal"), seed = 5L)
        assertFalse(s.crabLiveFishScenario)
        assertEquals(0.0, s.paramValue("crab_predation"))
        s.enqueue(Command.SetParam("crab_predation", 1.0))
        s.advanceAbstract(60.0)
        assertTrue(s.crabLiveFishScenario)
        val crab = s.org(BrainKind.SALLY_CRAB)
        crab.lengthCm = 6.0
        val r = TestTank.sim(stock("crab", "cardinal", "tailspot"), seed = 5L, startHour = 12.0)
        r.predationEnabled = true
        val c2 = r.org(BrainKind.SALLY_CRAB); c2.lengthCm = 6.0
        repeat(40) { c2.hunger = 0.95; r.advanceAbstract(86400.0) }
        assertTrue("fish alive", r.organisms.filter { it.isFish }.all { it.alive })
        assertTrue("no fish taken or attacked by default",
            r.eventLog.none { (it is SimEvent.Eaten && it.predatorId == c2.id) || (it is SimEvent.NearMiss && it.predatorId == c2.id) })
    }

    @Test
    fun crabScavengesTheDeadWithoutPredation() {
        val s = TestTank.sim(stock("crab", "cardinal"), seed = 5L, startHour = 23.0)
        s.advance(1.0)
        val crab = s.org(BrainKind.SALLY_CRAB)
        val card = s.org(BrainKind.CARDINAL)
        val ev = ArrayList<SimEvent>()
        HealthModel.kill(card, "test", s.simTimeSec, ev)
        // in the 48x18x16 layout CREVICE_SALLY sits ~22 cm up, above the low-carcass band (sand + 15): drop the body
        // beside the rock near the crab's den and let it settle (a fresh body sinks and stays where it lands,
        // Simulation.deadPhysics), then start the crab on the sand 3 cm from it (one offset alone can be inside rock)
        card.pos = s.rock.sdf.project(Vec3(crab.pos.x, s.rock.sandTopCm + 5.0, crab.pos.z + 3.0), 1.5)
        s.advance(10.0, behaviorDt = 0.1)
        crab.pos = listOf(Vec3(3.0, 0.0, 0.0), Vec3(-3.0, 0.0, 0.0), Vec3(0.0, 0.0, 3.0), Vec3(0.0, 0.0, -3.0))
            .map { val p = card.pos + it; Vec3(p.x, s.sand.surfaceY(p.x, p.z) + 0.3, p.z) }
            .first { s.rock.inWater(it, 0.5) && s.rock.sdf.dist(it) > 0.5 }
        s.advance(180.0, behaviorDt = 0.1) { crab.hunger = maxOf(crab.hunger, 0.6) }
        val cb = s.brainOf(BrainKind.SALLY_CRAB) as SallyCrabBrain
        assertTrue("carcass bites ${cb.carcassBites}", cb.carcassBites > 0)
        assertTrue(s.eventLog.none { it is SimEvent.Eaten })
    }

    // ------------------------------------------------------------------ nassarius
    @Test
    fun nassariusRespondsFasterWhenFastedAndFeedsInMinuteLongBouts() {
        assertTrue(NassariusBrain.cueLatency(0.9, 0.5) < NassariusBrain.cueLatency(0.2, 0.5))
        val rng = com.reeflive.core.geom.Rng(11L)
        val bouts = (1..4000).map { NassariusBrain.boutSeconds(rng.gaussian()) / 60 }
        val mean = bouts.average()
        val le8 = bouts.count { it <= 8.0 }.toDouble() / bouts.size
        assertTrue("mean bout $mean min", mean in 5.3..6.7)
        assertTrue("≤ 8 min share $le8", le8 in 0.70..0.86)
        val s = TestTank.sim(stock("nass1"), seed = 3L, startHour = 12.0)
        val n = s.org(BrainKind.NASSARIUS); n.hunger = 0.9
        s.advance(1.0)
        s.enqueue(Command.Feed(FoodKind.PRAWN, 0.3, n.pos.withY(s.rock.sandTopCm + 8)))
        s.advance(1500.0, behaviorDt = 0.2)
        val nb = s.brainOf(BrainKind.NASSARIUS) as NassariusBrain
        assertTrue("cue response ${nb.cueResponses}", nb.cueResponses >= 1)
        assertTrue("fed: hunger ${n.hunger}", n.hunger < 0.9)
    }

    // ------------------------------------------------------------------ astrea
    @Test
    fun overturnedAstreaKeepsTryingAndNeverDiesOfInversion() {
        val s = TestTank.sim(stock("astrea1"), seed = 9L, startHour = 12.0)
        s.advance(1.0)
        val a = s.agentOf(s.org(BrainKind.ASTREA))!!
        val ab = a.brain as AstreaBrain
        a.ctx.o.pos = a.ctx.o.pos.withY(s.rock.sandTopCm + 0.6).let { Vec3(60.0, it.y, 20.0) }
        ab.flip(a.ctx)
        s.advance(30 * 3600.0, behaviorDt = 0.5)
        assertTrue(s.org(BrainKind.ASTREA).alive)
        assertTrue("attempts ${ab.rightingAttempts}", ab.rightingAttempts >= 1)
        assertTrue("righted after ${ab.rightingAttempts} attempts", !ab.flipped && ab.rightings == 1)
    }

    // ------------------------------------------------------------------ asterina
    @Test
    fun asterinaTravelsLessNearFood() {
        val s = TestTank.sim(stock("asterina1"), seed = 4L, startHour = 12.0)
        s.advance(1.0)
        val o = s.org(BrainKind.ASTERINA)
        val b = s.brainOf(BrainKind.ASTERINA) as AsterinaBrain
        s.advance(1800.0, behaviorDt = 0.2)
        val bare = b.pathCm / b.timeS
        val p0 = b.pathCm; val t0 = b.timeS
        s.advance(1800.0, behaviorDt = 0.2) {
            if (s.food.countNear(o.pos, 5.0) == 0) s.food.spawn(FoodKind.PELLET, 0.001, o.pos, s.simTimeSec)
        }
        val fed = (b.pathCm - p0) / (b.timeS - t0)
        assertTrue("speed near food $fed vs without $bare", fed < 0.7 * bare)
    }

    // ------------------------------------------------------------------ brittle star
    @Test
    fun brittleStarFeedsWithAnArmWhileTheDiscStaysHidden() {
        val s = TestTank.sim(stock("brittle"), seed = 2L, startHour = 12.0)
        s.advance(60.0, behaviorDt = 0.1)
        val o = s.org(BrainKind.BRITTLE_STAR); o.hunger = 0.8
        val disc = o.pos
        s.food.spawn(FoodKind.PELLET, 0.02, Vec3(disc.x + 3.0, s.rock.sandTopCm + 0.3, disc.z), s.simTimeSec)
        s.advance(40.0, behaviorDt = 0.1) { o.hunger = maxOf(o.hunger, 0.5) }
        val b = s.brainOf(BrainKind.BRITTLE_STAR) as BrittleStarBrain
        assertTrue("arm feeds ${b.armFeeds}", b.armFeeds >= 1)
        assertTrue("disc stayed put (${o.pos.distanceTo(disc)} cm)", o.pos.distanceTo(disc) < 2.5)
    }

    // ------------------------------------------------------------------ feather duster
    @Test
    fun dusterRetractionCrownLossAndAbandonmentAreDistinct() {
        val s = TestTank.sim(listOf(TestTank.seed("duster")), seed = 8L)
        s.advanceAbstract(120.0)
        val d = s.organisms.first()
        val e = s.invertEcology
        d.extension = 0.0
        assertEquals(InvertEcology.DusterState.RETRACTED, e.dusterState(d))
        e.loseCrown(d)
        assertEquals(InvertEcology.DusterState.CROWN_LOST, e.dusterState(d))
        repeat(4 * 1440) { d.health = 0.3; e.step(1.0) }
        assertEquals(InvertEcology.DusterState.TUBE_ABANDONED, e.dusterState(d))
        assertTrue(d.alive && !d.visible)
        d.health = 0.9; e.step(1.0)
        assertEquals(InvertEcology.DusterState.CROWN_LOST, e.dusterState(d))
        assertTrue(d.visible)
    }

    private companion object {
        val DefaultClowns = listOf(TestTank.seed("clown_f"), TestTank.seed("clown_m"))
    }
}

@Suppress("unused")
private fun near(a: Double, b: Double, tol: Double) = abs(a - b) <= tol
