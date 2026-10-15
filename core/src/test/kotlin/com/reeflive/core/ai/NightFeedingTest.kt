package com.reeflive.core.ai

import com.reeflive.core.sim.BrainKind
import com.reeflive.core.sim.Command
import com.reeflive.core.sim.DefaultStock
import com.reeflive.core.sim.FoodKind
import com.reeflive.core.sim.TestTank
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tee's moonlight feeding clip (docs/research/video/tee_tank_night_feeding.md): food after dark wakes the day fish. */
class NightFeedingTest {
    @Test
    fun foodAfterDarkWakesTheDayFishThenTheyGoBackToSleep() {
        val sim = TestTank.sim(DefaultStock.all, seed = 1L, startHour = 22.5)
        sim.advance(120.0, behaviorDt = 0.1)
        val fish = sim.organisms.filter { it.isFish }
        fish.forEach { it.hunger = 0.7 }
        val woken = setOf(BrainKind.TANG, BrainKind.CLOWN, BrainKind.WRASSE, BrainKind.DIAMOND_GOBY)
        val stayPut = setOf(BrainKind.HAWK, BrainKind.LEOPARD_WRASSE)
        val fed = HashSet<String>()
        sim.enqueue(Command.Feed(FoodKind.MYSIS, 1.5))
        sim.advance(300.0, behaviorDt = 0.1) { fish.filter { it.behaviorName.startsWith("Feeding") || it.behaviorName.startsWith("Picking") }.forEach { fed += it.nickname } }
        val up = fish.filter { it.brainKind in woken }
        val ate = up.count { it.hunger < 0.6 }
        assertTrue("woken fish that ate: $ate of ${up.size} ${up.map { it.nickname + "=" + "%.2f".format(it.hunger) }}", ate >= up.size - 1)
        fish.filter { it.brainKind in stayPut }.forEach { assertTrue("${it.nickname} left its night rest to feed", it.nickname !in fed) }
        // the food is gone or stale: everyone is back at rest
        sim.advance(900.0, behaviorDt = 0.1)
        val awake = up.filter { !it.sleeping && it.visible }
        assertTrue("still up after the feeding: ${awake.map { it.nickname + ":" + it.behaviorName }}", awake.size <= 1)
    }
}
