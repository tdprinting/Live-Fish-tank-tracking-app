package com.reeflive.core.ai

import com.reeflive.core.geom.Vec3
import com.reeflive.core.sim.BrainKind
import com.reeflive.core.sim.Command
import com.reeflive.core.sim.DefaultStock
import com.reeflive.core.sim.FoodKind
import com.reeflive.core.sim.LivestockSeed
import com.reeflive.core.sim.SimSpecies
import com.reeflive.core.sim.Simulation
import com.reeflive.core.sim.TestTank
import com.reeflive.core.sim.Thought
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** "What is it thinking?" readout (ai/Thoughts.kt, SIMULATION §4.10). */
class ThoughtsTest {

    /** Every brain kind: the default stock, a moray, a bobtail squid and two sheet-less animals on the generic brains. */
    private fun everyBrain(seed: Long = 5L, hour: Double = 11.0): Simulation {
        val sim = TestTank.sim(DefaultStock.all + LivestockSeed("echidna_nebulosa", "Snowflake", 45.0, id = "eel"), seed = seed, startHour = hour)
        sim.species.ensure("euprymna_berryi")
        sim.addFromSeed(LivestockSeed("euprymna_berryi", "Squiddy", 6.0, id = "squid"))
        sim.species.ensure("entacmaea_quadricolor")
        sim.addFromSeed(LivestockSeed("entacmaea_quadricolor", "Bubbles", 20.0, id = "bta"))
        sim.species.put(SimSpecies("mystery_fish", kind = "FISH", common = "Mystery fish"))
        sim.species.put(SimSpecies("mystery_critter", kind = "INVERT", common = "Mystery critter"))
        sim.addFromSeed(LivestockSeed("mystery_fish", "Mo", 6.0, id = "gfish"))
        sim.addFromSeed(LivestockSeed("mystery_critter", "", 3.0, id = "ginv"))
        return sim
    }

    private fun run(thoughts: Boolean): List<String> {
        val sim = TestTank.sim(DefaultStock.all, seed = 11L, startHour = 17.5)
        sim.thoughtsEnabled = thoughts
        val trace = ArrayList<String>()
        var words = 0
        for (step in 0 until 480) {
            if (step == 40) sim.enqueue(Command.Feed(FoodKind.MYSIS, 1.0, Vec3(60.0, 50.0, 20.0)))
            if (step == 200) sim.enqueue(Command.TapGlass(Vec3(40.0, 30.0, 0.5)))
            sim.advance(0.25)
            val snap = sim.snapshot()
            words += snap.stats.count { it.thought.now.isNotBlank() }
            if (step % 20 == 0) trace += sim.organisms.joinToString(";") { o -> "${o.id}:${o.pos}:${o.hunger}:${o.behaviorName}:${o.rng.nextDouble()}" }
        }
        if (thoughts) assertTrue("readout produced sentences", words > 0) else assertEquals("readout off", 0, words)
        trace += sim.water.s.toString()
        return trace
    }

    @Test
    fun readoutDoesNotChangeTheSim() {
        val on = run(true)
        val off = run(false)
        assertEquals(off.size, on.size)
        on.zip(off).forEachIndexed { i, (a, b) -> assertEquals("trace $i differs with the readout on", b, a) }
    }

    private val leak = Regex("[A-Z]{3,}|_|\\bnull\\b|Unknown|unknown|\\{|\\}")

    private fun assertPlain(where: String, s: String) {
        assertFalse("$where: raw words leak into \"$s\"", leak.containsMatchIn(s))
        assertFalse("$where: double spaces in \"$s\"", s.contains("  "))
    }

    @Test
    fun everyBehaviourOfEverySpeciesHasItsOwnSentence() {
        val sim = everyBrain()
        sim.advance(2.0)
        val kinds = HashSet<BrainKind>()
        var checked = 0
        for (o in sim.organisms.toList()) {
            val brain = sim.agentOf(o)?.brain ?: continue
            kinds += o.brainKind
            val other = sim.organisms.first { it !== o && it.alive }
            for (b in o.brain.behaviors) {
                for (target in listOf(null, other)) {
                    val l = Thoughts.line(Thoughts.Env(o, sim, brain, target), b)
                    val where = "${o.brainKind}/${b.name} (${b.label})"
                    assertTrue("$where has no sentence of its own", l != null)
                    l!!
                    assertTrue("$where: empty sentence", l.now.isNotBlank() && l.bubble.isNotBlank())
                    assertTrue("$where: no next plan words", l.next.isNotBlank())
                    assertFalse("$where: the sentence is the raw behaviour name", l.now == b.name || l.now.equals(b.label, true))
                    assertPlain(where, l.now); assertPlain(where, l.next); assertPlain(where, l.bubble)
                    checked++
                }
            }
        }
        val missing = BrainKind.entries.toSet() - kinds
        assertTrue("brains not covered: $missing", missing.isEmpty())
        assertTrue("checked $checked", checked > 200)
    }

    @Test
    fun dayIntoNightThoughtsReadAsPlainEnglish() {
        // 18:45 to 20:45: feeding, dusk and the lights going out (default photoperiod 08:45-19:45)
        val sim = everyBrain(seed = 9L, hour = 18.75)
        sim.enqueue(Command.Feed(FoodKind.PELLET, 1.0, Vec3(60.0, 50.0, 20.0)))
        val seen = HashMap<String, MutableSet<String>>()
        val moods = HashSet<String>()
        var withNext = 0
        var total = 0
        for (step in 0 until 240) {
            sim.advance(30.0, behaviorDt = 0.25)
            if (step == 120) sim.enqueue(Command.Feed(FoodKind.MYSIS, 1.0, Vec3(40.0, 50.0, 20.0)))
            for (st in sim.snapshot().stats) {
                val t = st.thought
                if (!st.alive) continue
                total++
                assertTrue("${st.id}: empty thought", t.now.isNotBlank() && t.feeling.isNotBlank() && t.mood.isNotBlank() && t.why.isNotBlank())
                assertTrue("${st.id}: sentence starts with the name: ${t.now}", t.now.startsWith(st.nickname.ifBlank { "The " }))
                assertTrue("${st.id}: a sentence ends with a full stop: ${t.now}", t.now.endsWith("."))
                assertTrue("${st.id}: feeling line: ${t.feeling}", t.feeling.startsWith("Feeling: ") && t.feeling.endsWith("."))
                if (t.next.isNotBlank()) { withNext++; assertTrue(t.next, t.next.startsWith("Next: ") && t.next.endsWith(".")) }
                assertTrue("interest ${t.interest}", t.interest in 0..3)
                listOf(t.now, t.next, t.feeling, t.why, t.bubble, t.mood).forEach { assertPlain(st.id, it) }
                assertTrue("busy is not a raw fallback: ${t.now}", !t.now.contains("doing its own thing"))
                seen.getOrPut(st.speciesId) { LinkedHashSet() } += t.now + " " + t.next + " " + t.feeling
                moods += t.mood
            }
        }
        assertTrue("some animals have a next plan: $withNext / $total", withNext > total / 10)
        assertTrue("more than one mood: $moods", moods.size >= 4)
        // a handful of sample sentences per species for the docs / PR
        for ((sp, lines) in seen.toSortedMap()) println("THOUGHTS $sp\n  " + lines.take(6).joinToString("\n  "))
    }

    @Test
    fun namesTargetsAndFeelings() {
        val sim = everyBrain()
        sim.advance(1.0)
        val nemo = sim.organisms.first { it.id == "clown_f" }
        val tang = sim.organisms.first { it.id == "tang" }
        val marlin = sim.organisms.first { it.id == "clown_m" }
        val hermit = sim.organisms.first { it.id == "hermit2" }
        assertEquals("Blue the tang", Thoughts.refer(nemo, tang))
        assertEquals("Marlin", Thoughts.refer(nemo, marlin))
        assertEquals("Hermit 2", Thoughts.refer(nemo, hermit))
        assertEquals("the mystery critter", Thoughts.nameOf(sim.organisms.first { it.id == "ginv" }))
        // the clown's host is the hairy mushroom coral it lives in
        val host = Thoughts.Env(nemo, sim, sim.agentOf(nemo)!!.brain, null).host
        assertTrue("host: $host", host.startsWith("the ") && host != "its host")
        // a warm tank: a stressed fish says why in plain words
        nemo.envStress.clear(); nemo.envStress["temp"] = 0.6; nemo.stress = 0.6
        sim.water.s.tempC = 28.5
        val t: Thought = Thoughts.of(nemo, sim, null).first
        assertTrue(t.feeling, t.feeling.contains("water is warm") || t.mood == "scared" || t.mood == "unwell")
        assertTrue(t.now, t.now.startsWith("Nemo "))
    }
}
