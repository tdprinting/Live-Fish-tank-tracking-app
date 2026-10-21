package com.reeflive.core.ai

import com.reeflive.core.geom.Vec3
import com.reeflive.core.sim.BrainKind
import com.reeflive.core.sim.ShrimpLimbs
import com.reeflive.core.sim.Simulation
import com.reeflive.core.sim.TestTank
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Shrimp limb hooks and the behaviours that drive them (research 17). */
class ShrimpMotionTest {
    private fun Simulation.org(kind: BrainKind) = organisms.first { it.brainKind == kind }
    private fun stock(vararg ids: String) = ids.map { TestTank.seed(it) }

    @Test
    fun bothShrimpGroomTheirAntennaeInShortBouts() {
        for (kind in listOf(BrainKind.BANDED_SHRIMP, BrainKind.CLEANER_SHRIMP)) {
            val s = TestTank.sim(stock(if (kind == BrainKind.BANDED_SHRIMP) "banded" else "skunk"), seed = 3L, startHour = 11.0)
            val o = s.org(kind)
            var peak = 0.0
            var groomingTicks = 0
            var ticks = 0
            s.advance(1800.0, behaviorDt = 0.1) { peak = maxOf(peak, o.groom); ticks++; if (o.groom > 0.5) groomingTicks++ }
            assertTrue("$kind groomed (peak ${o.groom} / $peak)", peak > 0.8)
            // bouts are short: grooming fills a small share of the time, not the whole half hour
            assertTrue("$kind grooming share ${groomingTicks.toDouble() / ticks}", groomingTicks.toDouble() / ticks < 0.25)
        }
    }

    @Test
    fun bandedShrimpRaisesItsClawsAtAnIntruderNotAtAClient() {
        val s = TestTank.sim(stock("banded", "tang"), seed = 2L, startHour = 12.0)
        val shrimp = s.org(BrainKind.BANDED_SHRIMP)
        val tang = s.org(BrainKind.TANG)
        s.advance(5.0, behaviorDt = 0.1) { tang.pos = Vec3(20.0, 45.0, 25.0); tang.vel = Vec3.ZERO }
        assertTrue("calm ${shrimp.clawDisplay}", shrimp.clawDisplay < 0.2)
        // a fish darting past within a few cm
        s.advance(2.0, behaviorDt = 0.1) { tang.pos = shrimp.pos + Vec3(3.0, -2.0, 0.0); tang.vel = Vec3(tang.cruiseSpeed * 2, 0.0, 0.0) }
        assertTrue("display ${shrimp.clawDisplay}", shrimp.clawDisplay > 0.6)
        // the signal reaches the renderer as limb entry 0, z (ShrimpLimbs)
        val shown = s.snapshot().organisms.first { it.id == shrimp.id }
        assertEquals(4 * ShrimpLimbs.ENTRIES, shown.limbs.size)
        assertTrue("pose signal ${shown.limbs[2]}", shown.limbs[2] > 0.5)
        s.advance(5.0, behaviorDt = 0.1) { tang.pos = Vec3(20.0, 45.0, 25.0); tang.vel = Vec3.ZERO }
        assertTrue("relaxed ${shrimp.clawDisplay}", shrimp.clawDisplay < 0.2)
        val pose = s.snapshot().organisms.first { it.id == shrimp.id }
        assertTrue(pose.limbs[2] < 0.2)
    }

    @Test
    fun walkingAdvancesTheStrideClockAndStandingStopsIt() {
        val s = TestTank.sim(stock("banded"), seed = 5L, startHour = 23.0)
        val o = s.org(BrainKind.BANDED_SHRIMP)
        var moved = 0.0
        var stepped = 0.0
        var last = o.walkPhase
        s.advance(600.0, behaviorDt = 0.1) {
            val d = (o.walkPhase - last + 1000.0) % 1000.0
            last = o.walkPhase
            moved += o.vel.length() * 0.1
            stepped += d
            assertTrue(o.walkAmt in 0.0..1.0)
        }
        // strides follow the distance walked (one per quarter body length)
        assertTrue("strides $stepped for $moved cm", stepped <= 1.2 * moved / (0.25 * o.lengthCm) + 1)
        if (moved > 5) assertTrue("strides $stepped for $moved cm", stepped > 0.2 * moved / (0.25 * o.lengthCm))
        val pose = s.snapshot().organisms.first { it.id == o.id }
        assertTrue(pose.limbs[0] in 0f..1f && pose.limbs[1] in 0f..1f && pose.motionMode == 2)
    }

    /** Tee 2026-10-21: every shrimp keeps its legs and mouthparts moving at rest, day and night, and never sinks into rock. */
    @Test
    fun everyShrimpMovesItsLegsAndStaysOutOfTheRock() {
        for (hour in listOf(12.0, 22.0)) {
            val s = TestTank.sim(stock("banded", "skunk"), seed = 3L, startHour = hour)
            s.species.ensure("lysmata_boggessi")
            s.addFromSeed(com.reeflive.core.sim.LivestockSeed("lysmata_boggessi", "Minty", 4.0, id = "pep"))
            s.advance(30.0)
            val shrimp = s.organisms.filter { it.brainKind.isShrimp }
            assertEquals(3, shrimp.size)
            val prev = shrimp.associate { it.id to it.toPose() }.toMutableMap()
            val legs = HashMap<String, Double>(); val picks = HashMap<String, Double>()
            repeat(1200) {
                s.advance(0.05)
                for (o in shrimp) {
                    val p = o.toPose(); val q = prev[o.id]!!
                    var d = 0.0; for (i in 4 until 28) d += kotlin.math.abs(p.limbs[i] - q.limbs[i])
                    var e = 0.0; for (i in 28 until 44) e += kotlin.math.abs(p.limbs[i] - q.limbs[i])
                    legs[o.id] = (legs[o.id] ?: 0.0) + d; picks[o.id] = (picks[o.id] ?: 0.0) + e
                    prev[o.id] = p
                    assertTrue("${o.brainKind} at $hour h clear of the rock (${s.rock.sdf.dist(o.pos)})", s.rock.sdf.dist(o.pos) > -0.05)
                }
            }
            for (o in shrimp) {
                println("shrimp ${o.brainKind} $hour h: legs ${legs[o.id]} picks ${picks[o.id]} (${o.behaviorName})")
                assertEquals(2, o.toPose().motionMode)
                assertTrue("${o.brainKind} at $hour h shifts its walking legs: ${legs[o.id]}", legs[o.id]!! > 10.0)
                assertTrue("${o.brainKind} at $hour h works its picking legs: ${picks[o.id]}", picks[o.id]!! > 5.0)
                assertTrue("${o.brainKind} in the water", s.rock.inWater(o.pos, 0.0))
            }
        }
    }

    /** The shrimp pack (research 44): fire, sexy, harlequin and pistol shrimp keep their legs moving too, while in view. */
    @Test
    fun packShrimpMoveTheirLegsAndStayOutOfTheRock() {
        val pack = listOf("lysmata_debelius", "thor_amboinensis", "hymenocera_picta", "alpheus_bellulus")
        for (hour in listOf(12.0, 22.0)) {
            val s = TestTank.sim(emptyList(), seed = 3L, startHour = hour)
            // the sexy shrimp's anemone and the pistol shrimp's goby
            for ((id, len) in listOf("entacmaea_quadricolor" to 8.0, "cryptocentrus_cinctus" to 7.0) + pack.map { it to 3.0 }) {
                s.species.ensure(id)
                s.addFromSeed(com.reeflive.core.sim.LivestockSeed(id, id, len, id = id))
            }
            s.advance(30.0)
            val shrimp = s.organisms.filter { it.brainKind.isShrimp }
            assertEquals(4, shrimp.size)
            val prev = shrimp.associate { it.id to it.toPose() }.toMutableMap()
            val legs = HashMap<String, Double>(); val picks = HashMap<String, Double>(); val seen = HashMap<String, Int>()
            repeat(1200) {
                s.advance(0.05)
                for (o in shrimp) {
                    val p = o.toPose(); val q = prev[o.id]!!
                    prev[o.id] = p
                    // the pistol shrimp spends spells down its burrow, out of sight
                    if (!o.visible) continue
                    seen[o.id] = (seen[o.id] ?: 0) + 1
                    var d = 0.0; for (i in 4 until 28) d += kotlin.math.abs(p.limbs[i] - q.limbs[i])
                    var e = 0.0; for (i in 28 until 44) e += kotlin.math.abs(p.limbs[i] - q.limbs[i])
                    legs[o.id] = (legs[o.id] ?: 0.0) + d; picks[o.id] = (picks[o.id] ?: 0.0) + e
                    assertTrue("${o.brainKind} at $hour h clear of the rock (${s.rock.sdf.dist(o.pos)})", s.rock.sdf.dist(o.pos) > -0.05)
                }
            }
            for (o in shrimp) {
                val n = seen[o.id] ?: 0
                println("shrimp ${o.brainKind} $hour h: seen $n, legs ${legs[o.id]} picks ${picks[o.id]} (${o.behaviorName})")
                if (n < 400) continue
                val f = 1200.0 / n
                assertEquals(2, o.toPose().motionMode)
                assertTrue("${o.brainKind} at $hour h shifts its walking legs: ${legs[o.id]}", legs[o.id]!! * f > 10.0)
                assertTrue("${o.brainKind} at $hour h works its picking legs: ${picks[o.id]}", picks[o.id]!! * f > 5.0)
                assertTrue("${o.brainKind} in the water", s.rock.inWater(o.pos, 0.0))
            }
            assertTrue("most of the pack in view", seen.count { it.value >= 400 } >= 3)
        }
    }
}
