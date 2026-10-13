package com.reeflive.core.sim

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

/**
 * Tee's everyday feeding (video T97, docs/research/video/tee_tank_day.md "Feeding"): a hand pinch of tiny pale
 * granules dropped at the back middle beside the return; the return sweeps the cloud forward and sideways across about
 * a third of the tank in ~2 s while it sinks slowly; the clowns are up at the surface within ~2 s.
 */
class HandFeedTest {
    @Test
    fun pinchOfGranulesSpreadsWithTheReturnAndSinksSlowly() {
        val s = TestTank.sim(DefaultStock.all, seed = 5L)
        s.behaviorEnabled = false
        val at = s.feedPoint(null)
        assertTrue("drop point at the back middle: $at", at.z < 8.0 && at.x in 55.0..75.0)
        s.food.spawn(FoodKind.PELLET, FoodKind.PELLET.typicalDoseGrams, at, s.simTimeSec)
        val n = s.food.particles.count { it.kind == FoodKind.PELLET }
        assertTrue("40-80 granules per pinch: $n", n in 40..80)
        s.advance(2.0, fullBehavior = false)
        val ps = s.food.particles.filter { it.kind == FoodKind.PELLET && !it.eaten }
        val reach = ps.map { hypot(it.pos.x - at.x, it.pos.z - at.z) }.sorted()
        val far = reach[(reach.size * 0.9).toInt().coerceAtMost(reach.size - 1)]
        val drop = ps.map { at.y - it.pos.y }.average()
        println("hand feed: n=$n reach90=%.1f median=%.1f meanDrop=%.1f".format(far, reach[reach.size / 2], drop))
        assertTrue("the return sweeps the leading edge 12+ cm in 2 s: $far", far >= 12.0)
        assertTrue("streaming down into mid-water, not dropping like a stone: $drop", drop in 1.0..12.0)
    }

    @Test
    fun clownsReachTheSurfaceFast() {
        val s = TestTank.sim(DefaultStock.all, seed = 5L)
        s.advance(5.0)
        s.enqueue(Command.Feed(FoodKind.PELLET, FoodKind.PELLET.typicalDoseGrams))
        val top = s.rock.waterLevelCm
        var best = Double.MAX_VALUE
        var t = 0.0
        while (t < 4.0) {
            s.advance(0.25); t += 0.25
            val clowns = s.organisms.filter { it.alive && it.brainKind == BrainKind.CLOWN }
            if (clowns.isNotEmpty()) best = minOf(best, clowns.minOf { top - it.pos.y })
        }
        println("clown closest to surface within 4 s: %.1f cm".format(best))
        assertTrue("a clown comes up toward the falling food within ~4 s: $best", best < 20.0)
    }
}
