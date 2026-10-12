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
