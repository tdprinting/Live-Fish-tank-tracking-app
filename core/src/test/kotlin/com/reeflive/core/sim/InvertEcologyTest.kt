package com.reeflive.core.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Research 04 invertebrate ecology: hermit shells (B-4), asterina vs organ pipe (B-5), duster crown (B-6). */
class InvertEcologyTest {
    private fun hermit(id: String, size: Double, sp: String = "clibanarius_tricolor") = LivestockSeed(sp, id, size, id = id)
    private fun astrea(id: String, size: Double) = LivestockSeed("astraea_tecta", id, size, id = id)
    private fun nass(id: String, size: Double) = LivestockSeed("nassarius_vibex", id, size, id = id)

    private fun Simulation.tick(minutes: Int) = advanceAbstract(minutes * 60.0)
    private fun Simulation.shell(id: String) = invertEcology.shellOf(organisms.first { it.id == id })!!
    /** Steps the ecology alone at a frozen sim clock (swap/fight cooldowns then act as one-shot). */
    private fun InvertEcology.stepEcology(minutes: Int) = repeat(minutes) { step(1.0) }

    // ------------------------------------------------------------------ B-4
    @Test
    fun spareShellCommandAddsInventory() {
        val s = TestTank.sim(listOf(hermit("h1", 2.5)))
        assertEquals(0, s.invertEcology.spareShells.size)
        s.enqueue(Command.AddSpareShells(3, 3.5))
        s.enqueue(Command.AddSpareShells(2))   // auto size: roomy for the biggest hermit
        s.tick(2)
        val spares = s.invertEcology.spareShells
        assertEquals(5, spares.size)
        assertEquals(3, spares.count { it == 3.5 })
        assertTrue(spares.filter { it != 3.5 }.all { it > s.shell("h1").needCm })
    }

    @Test
    fun hermitOutgrowsItsShellOverTime() {
        val s = TestTank.sim(listOf(hermit("h1", 2.5), hermit("h2", 2.2)), seed = 5L)
        s.tick(2)
        val before = s.invertEcology.hermits.mapValues { it.value.tightness }
        assertTrue("start comfortable: $before", before.values.all { it < 1.0 })
        s.tick(60 * 24 * 90)
        val after = s.invertEcology.hermits
        assertTrue("grew snugger: ${after.mapValues { it.value.tightness }}", after.all { (k, v) -> v.tightness > before.getValue(k) * 1.1 })
        assertTrue("someone outgrew the shell after 90 days", after.values.any { it.outgrown })
    }

    @Test
    fun spareShellIsSwappedInAndOldOneLeftBehind() {
        val s = TestTank.sim(listOf(hermit("h1", 2.5)), seed = 2L)
        s.tick(2)
        val st = s.shell("h1")
        st.needCm = st.shellCm * 1.05   // outgrown
        val old = st.shellCm
        s.invertEcology.addSpareShells(1, st.needCm * 1.4)
        s.invertEcology.stepEcology(120)
        assertEquals(1, s.invertEcology.shellSwaps)
        assertTrue(st.shellCm > old && !st.outgrown)
        assertEquals(listOf(old), s.invertEcology.spareShells)
        assertEquals(0, s.invertEcology.shellKills)
    }

    @Test
    fun sparesStopHermitsKillingSnails() {
        // no spares: an outgrown hermit takes an Astrea's shell (sandbox, predation on)
        val a = TestTank.sim(listOf(hermit("h1", 2.5), astrea("a1", 3.0)), seed = 4L)
        a.predationEnabled = true
        a.tick(2)
        a.shell("h1").let { it.needCm = it.shellCm * 1.1 }
        val need = a.shell("h1").needCm
        a.organisms.first { it.id == "a1" }.lengthCm = need * 1.3
        a.invertEcology.stepEcology(60 * 24 * 10)
        assertFalse("astrea killed for its shell", a.organisms.first { it.id == "a1" }.alive)
        assertTrue(a.invertEcology.shellKills == 1 && !a.shell("h1").outgrown)
        assertTrue(a.eventLog.any { it is SimEvent.Died && it.id == "a1" })

        // same tank with a spare shell: the snail lives
        val b = TestTank.sim(listOf(hermit("h1", 2.5), astrea("a1", 3.0)), seed = 4L)
        b.predationEnabled = true
        b.tick(2)
        b.shell("h1").let { it.needCm = it.shellCm * 1.1 }
        b.organisms.first { it.id == "a1" }.lengthCm = b.shell("h1").needCm * 1.3
        b.invertEcology.addSpareShells(1, b.shell("h1").needCm * 1.4)
        b.invertEcology.stepEcology(60 * 24 * 10)
        assertTrue(b.organisms.first { it.id == "a1" }.alive)
        assertEquals(0, b.invertEcology.shellKills)
        assertEquals(1, b.invertEcology.shellSwaps)
    }

    @Test
    fun realityModeTurnsShellTheftIntoNearMiss() {
        val s = TestTank.sim(listOf(hermit("h1", 2.5), astrea("a1", 3.0)), seed = 4L)
        s.predationEnabled = false
        s.tick(2)
        s.shell("h1").let { it.needCm = it.shellCm * 1.1 }
        s.organisms.first { it.id == "a1" }.lengthCm = s.shell("h1").needCm * 1.3
        s.invertEcology.stepEcology(60 * 24 * 10)
        assertTrue(s.organisms.first { it.id == "a1" }.alive)
        assertEquals(0, s.invertEcology.shellKills)
        assertTrue(s.eventLog.any { it is SimEvent.NearMiss && it.bout == "shell-theft" })
    }

    @Test
    fun shellFightsHappenBetweenHermits() {
        val s = TestTank.sim(listOf(hermit("h1", 2.8), hermit("h2", 2.0)), seed = 9L)
        s.tick(2)
        val a = s.shell("h1"); val d = s.shell("h2")
        a.needCm = a.shellCm * 1.15      // outgrown, small shell
        d.shellCm = a.needCm * 1.6       // roomy shell worth taking
        d.needCm = d.shellCm * 0.5
        s.invertEcology.stepEcology(60 * 24 * 5)
        assertTrue("fights: ${s.invertEcology.shellFights}", s.invertEcology.shellFights >= 1)
        // a nassarius is too small to be a target and survives
        val n = TestTank.sim(listOf(hermit("h1", 2.5), nass("n1", 1.0)), seed = 4L)
        n.predationEnabled = true
        n.tick(2)
        n.shell("h1").let { it.needCm = it.shellCm * 1.1 }
        n.invertEcology.stepEcology(60 * 24 * 5)
        assertTrue(n.organisms.first { it.id == "n1" }.alive)
    }

    @Test
    fun scarletHermitIsLessPushyThanBlueLeg() {
        fun kills(sp: String, seeds: IntRange): Int = seeds.count { sd ->
            val s = TestTank.sim(listOf(hermit("h1", 2.5, sp), astrea("a1", 3.0)), seed = sd.toLong())
            s.predationEnabled = true
            s.tick(2)
            s.shell("h1").let { it.needCm = it.shellCm * 1.01 }
            s.organisms.first { it.id == "a1" }.lengthCm = s.shell("h1").needCm * 1.3
            s.invertEcology.stepEcology(60 * 24 * 2)
            !s.organisms.first { it.id == "a1" }.alive
        }
        val blue = kills("clibanarius_tricolor", 1..20)
        val scarlet = kills("paguristes_cadenati", 1..20)
        assertTrue("blue $blue vs scarlet $scarlet", blue > scarlet)
    }
}

/** Research 11 §7: organ-pipe corallivory is an opt-in sandbox scenario (SetParam "asterina_coral"), off by default. */
class AsterinaOrganPipeTest {
    private fun star(i: Int) = LivestockSeed("asterina_sp", "Asterina #$i", 1.0, id = "ast$i")
    private val organPipe = LivestockSeed("tubipora_musica", "Organ pipe", placement = "coral_organpipe_kenya_base", id = "organpipe")

    private fun tissueAfter(stars: Int, days: Double, scenario: Boolean = true): Pair<Double, Simulation> {
        val s = TestTank.sim(listOf(organPipe) + (1..stars).map(::star), seed = 6L)
        if (scenario) s.enqueue(Command.SetParam("asterina_coral", 1.0))
        s.advanceAbstract(days * 86400)
        return s.corals.first { it.id == "organpipe" }.tissue to s
    }

    @Test
    fun highPopulationGrazesOrganPipe() {
        val (low, _) = tissueAfter(2, 5.0)
        val (high, s) = tissueAfter(Simulation.ASTERINA_CAP, 5.0)
        assertTrue("2 stars leave tissue alone: $low", low > 0.95)
        assertTrue("capped population damages it: high=$high low=$low", high < low - 0.1)
        assertTrue(s.invertEcology.asterinaBitten > 0.1)
    }

    @Test
    fun corallivoryIsOffByDefault() {
        val (tissue, s) = tissueAfter(Simulation.ASTERINA_CAP, 5.0, scenario = false)
        assertTrue("default: no organ-pipe damage ($tissue)", tissue > 0.95)
        assertEquals(0.0, s.invertEcology.asterinaBitten, 0.0)
        assertEquals(0.0, s.paramValue("asterina_coral"))
    }

    @Test
    fun grazingRetractsThePolypsAndColonyCanDie() {
        val s = TestTank.sim(listOf(organPipe) + (1..4).map(::star), seed = 6L)
        s.asterinaCorallivoryScenario = true
        s.advanceAbstract(2 * 3600.0)
        val c = s.corals.first { it.id == "organpipe" }
        assertTrue("disturbed while grazed: ${c.disturb}", c.disturb >= 0.4)
        s.advanceAbstract(40 * 86400.0)
        assertTrue("stripped after weeks: ${c.tissue}", c.tissue < 0.2)
    }

    @Test
    fun otherCoralsAreLeftAlone() {
        val kenya = LivestockSeed("capnella_sp", "Kenya", placement = "coral_kenya", id = "kenya")
        val s = TestTank.sim(listOf(organPipe, kenya) + (1..4).map(::star), seed = 6L)
        s.asterinaCorallivoryScenario = true
        s.advanceAbstract(5 * 86400.0)
        val k = s.corals.first { it.id == "kenya" }
        assertTrue("kenya ${k.tissue}", k.tissue > 0.9)
    }
}

class DusterCrownTest {
    private val duster = LivestockSeed("sabellastarte_sp", "Duster", 8.0, id = "duster")
    private fun sim(seed: Long = 8L) = TestTank.sim(listOf(duster), seed = seed).also { it.advanceAbstract(120.0) }

    @Test
    fun longRetractionLosesTheCrownAndItRegrowsInOneToTwoMonths() {
        val s = sim()
        val d = s.organisms.first()
        val e = s.invertEcology
        assertEquals(1.0, e.crownOf(d), 0.0)
        // retracted for hours on end
        repeat(400) { d.extension = 0.0; e.step(1.0) }
        val st = e.dusters.getValue("duster")
        assertEquals(0.0, st.crown, 0.01)
        assertEquals(1, st.timesLost)
        val days = st.regrowMin / 1440.0
        assertTrue("regrow $days days in 30..60", days in 30.0..60.0)
        // 25 days later: partially back; fan capped by the regrowth
        fun days(n: Int) = repeat(n) { d.hunger = 0.2; s.advanceAbstract(86400.0) }   // well fed, so health stays up
        days(25)
        val mid = e.crownOf(d)
        assertTrue("partial crown at 25 days: $mid", mid > 0.2 && mid < 0.95)
        assertTrue(d.extension <= mid + 1e-9)
        // after 61 days it is whole again
        days(36)
        assertEquals("health ${d.health}", 1.0, e.crownOf(d), 1e-9)
        assertEquals(1, st.timesLost)
        assertTrue("fan reopened: ${d.extension}", d.extension > 0.9)
    }

    @Test
    fun brieflyFlinchingKeepsTheCrown() {
        val s = sim()
        val d = s.organisms.first()
        repeat(2000) { i -> d.extension = if (i % 10 < 2) 0.0 else 1.0; s.invertEcology.step(1.0) }
        assertEquals(1.0, s.invertEcology.crownOf(d), 0.0)
    }

    @Test
    fun noRegrowthWhileUnwell() {
        val s = sim()
        val d = s.organisms.first()
        s.invertEcology.loseCrown(d)
        d.health = 0.4
        repeat(60 * 24 * 10) { d.health = 0.4; s.invertEcology.step(1.0) }
        assertEquals(0.0, s.invertEcology.crownOf(d), 1e-9)
        d.health = 1.0
        repeat(60 * 24 * 10) { d.health = 1.0; s.invertEcology.step(1.0) }
        assertTrue(s.invertEcology.crownOf(d) > 0.1)
    }

    @Test
    fun crownLimitsFilterFeedingBehaviour() {
        val s = sim()
        val d = s.organisms.first()
        s.invertEcology.loseCrown(d)
        s.advance(60.0)
        assertTrue("fan stays shut without a crown: ${d.extension}", d.extension < 0.05)
        // a full crown opens again
        val s2 = sim(9L)
        s2.advance(60.0)
        assertTrue(s2.organisms.first().extension > 0.5)
    }

    @Test
    fun abstractPlanktonFeedingNeedsACrown() {
        val s = sim(); val d = s.organisms.first()
        s.behaviorEnabled = false
        s.invertEcology.loseCrown(d)
        d.hunger = 0.9
        s.enqueue(Command.Feed(FoodKind.PLANKTON, 0.2)); s.advanceAbstract(5.0)
        assertEquals(0.9, d.hunger, 1e-9)
    }
}
