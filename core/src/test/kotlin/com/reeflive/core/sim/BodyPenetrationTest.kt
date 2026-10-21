package com.reeflive.core.sim

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * No body goes through the rock, the sand or the glass (SIMULATION §24.3): over full-stock runs by day, afternoon and
 * night, every point of every swimming or walking body (its model's sections on the drawn spine, [BodyProfile]) stays
 * out of them, within the 1 cm rock field's reading error. Crawlers on their seats are reported, not yet held to it.
 */
class BodyPenetrationTest {
    @Test
    fun wholeBodiesStayOutOfRockSandAndGlass() {
        val bad = ArrayList<String>()
        for ((seed, hour) in listOf(1L to 9.0, 2L to 14.0, 3L to 20.5)) {
            val sim = TestTank.sim(DefaultStock.all, seed = seed, startHour = hour)
            val rep = BodyPenetrationReport(sim)
            var tick = 0
            sim.advance(System.getProperty("reeflive.probeSec")?.toDouble() ?: 600.0) { if (++tick % 10 == 0) rep.sample() }
            println("---- seed $seed hour $hour\n" + rep.summary())
            for ((what, m, max) in listOf(Triple("rock", rep.rock, ROCK_CM), Triple("sand", rep.sand, SAND_CM), Triple("glass", rep.glass, GLASS_CM))) {
                val w = rep.worst(m) ?: continue
                if (w.second.cm > max) bad += "seed $seed $what: ${w.first} ${"%.2f".format(w.second.cm)} cm (${w.second.where})"
            }
        }
        assertTrue("bodies through rock, sand or glass:\n" + bad.joinToString("\n"), bad.isEmpty())
    }

    companion object {
        /** Deepest a body may read into the rock (cm): the baked field is a 1 cm grid read trilinearly. */
        const val ROCK_CM = 0.5
        /** (a body wedged between rock and sand pushes the sand from under itself over a few ticks) */
        const val SAND_CM = 0.4
        const val GLASS_CM = 0.3
    }
}
