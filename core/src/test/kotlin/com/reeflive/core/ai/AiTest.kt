package com.reeflive.core.ai

import com.reeflive.core.geom.Vec3
import com.reeflive.core.sim.BrainKind
import com.reeflive.core.sim.Command
import com.reeflive.core.sim.DefaultStock
import com.reeflive.core.sim.FoodKind
import com.reeflive.core.sim.SimEvent
import com.reeflive.core.sim.Simulation
import com.reeflive.core.sim.TestTank
import org.junit.Assert.assertTrue
import org.junit.Test

class AiTest {
    /** The moray brain is kept for other tanks; Tee's "eel" is an engineer goby (see gobyDigsAndHides). */
    private val MORAY = com.reeflive.core.sim.LivestockSeed("echidna_nebulosa", "Snowflake", 45.0, id = "eel")

    private fun fullTank(seed: Long = 1L, hour: Double = 12.0) = TestTank.sim(DefaultStock.all, seed = seed, startHour = hour)

    /** SIMULATION §8.6: over 10 min, 95 % of samples within 3 BL of the host anchor. */
    @Test
    fun clownsStayNearHost() {
        val sim = fullTank()
        val host = sim.rock.anchors.pos("HOST_KENYA", Vec3.ZERO)
        val clowns = sim.organisms.filter { it.brainKind == BrainKind.CLOWN }
        var near = 0; var total = 0
        var t = 0.0
        sim.advance(600.0) {
            t += 0.05
            if (t >= 1.0) {
                t = 0.0
                for (c in clowns) { total++; if (c.pos.distanceTo(host) <= 3 * c.lengthCm) near++ }
            }
        }
        val frac = near.toDouble() / total
        assertTrue("clowns near host ${"%.3f".format(frac)}", frac >= 0.95)
    }

    /** SIMULATION §8.7: every tick, swimmers keep sdf > 0.3 L (collision length); attached animals sdf ≥ 0. */
    @Test
    fun noRockPenetration() {
        for ((seed, hour) in listOf(1L to 9.5, 2L to 12.0, 3L to 21.0)) {
            val sim = fullTank(seed, hour)
            var worst = ""
            var fails = 0
            var ticks = 0
            sim.advance(600.0) {
                ticks++
                if (ticks == 200) sim.enqueue(Command.Feed(FoodKind.MYSIS, 0.5))
                if (ticks == 2400) sim.enqueue(Command.TapGlass(Vec3(60.0, 30.0, 33.0)))
                for (o in sim.organisms) {
                    if (!o.alive) continue
                    val d = sim.rock.sdf.dist(o.pos)
                    val ok = if (o.attached) d >= -0.05 else d > 0.3 * o.collisionLength
                    if (!ok) { fails++; if (worst.isEmpty()) worst = "${o.nickname} ${o.behaviorName} sdf=${"%.2f".format(d)} at ${o.pos} attached=${o.attached} tick=$ticks" }
                }
            }
            assertTrue("seed $seed hour $hour: $fails violations, first: $worst", fails == 0)
        }
    }

    /** SIMULATION §8.8: hungry eel at night with a shrimp 5 cm from the den mouth → eaten within 30 min in ≥ 50 % of seeds. */
    @Test
    fun eelStrikesShrimpWhenHungry() {
        var eaten = 0
        val seeds = 1L..20L
        for (seed in seeds) {
            val sim = TestTank.sim(listOf(MORAY, TestTank.seed("banded")), seed = seed, startHour = 23.0)
            sim.predationEnabled = true
            val eel = sim.organisms.first { it.brainKind == BrainKind.EEL }
            val shrimp = sim.organisms.first { it.brainKind == BrainKind.BANDED_SHRIMP }
            eel.hunger = 0.9
            val mouth = sim.rock.anchors.pos("CAVE_EEL", Vec3(69.0, 5.0, 23.0))
            shrimp.placeAt(Vec3(mouth.x - 4.0, sim.rock.sandTopCm + 0.8, mouth.z + 3.0), Vec3.X)
            var t = 0.0
            while (t < 1800 && shrimp.alive) { sim.advance(10.0); t += 10 }
            if (sim.eventLog.any { it is SimEvent.Eaten }) eaten++
        }
        assertTrue("eaten in $eaten/${seeds.count()} seeds", eaten * 2 >= seeds.count())
    }

    /** Reality mode never kills by predation (SIMULATION §4.5). */
    @Test
    fun noPredationInRealityMode() {
        val sim = TestTank.sim(listOf(MORAY, TestTank.seed("banded")), startHour = 23.0)
        sim.organisms.first { it.brainKind == BrainKind.EEL }.hunger = 0.95
        sim.advance(1800.0, behaviorDt = 0.1)
        assertTrue(sim.eventLog.none { it is SimEvent.Eaten })
    }

    /** Feeding: fish chase particles and their hunger drops. */
    @Test
    fun feedingReducesHunger() {
        val sim = fullTank()
        val fish = sim.organisms.filter { it.isFish && it.brainKind != BrainKind.EEL && it.brainKind != BrainKind.LAWNMOWER_BLENNY }
        fish.forEach { it.hunger = 0.7 }
        sim.enqueue(Command.Feed(FoodKind.MYSIS, 0.8))
        sim.advance(300.0)
        val fed = fish.count { it.hunger < 0.6 }
        assertTrue("fed $fed of ${fish.size}: ${fish.map { it.nickname + "=" + "%.2f".format(it.hunger) }}", fed >= fish.size - 2)
    }

    /** Cleaning stations: someone gets cleaned within an hour of daytime. */
    @Test
    fun cleanersServeClients() {
        val sim = fullTank()
        sim.organisms.filter { it.isFish }.forEach { it.parasiteLoad = 0.9 }
        sim.advance(3600.0, behaviorDt = 0.1)
        val cleaned = sim.eventLog.filterIsInstance<SimEvent.Cleaned>()
        assertTrue("cleaning events ${cleaned.size}", cleaned.size >= 2)
    }

    /** Night: diurnal fish sleep, the eel and brittle star are out, the cardinal is near its cave. */
    @Test
    fun nightBehaviors() {
        val sim = fullTank(hour = 22.5)
        sim.advance(300.0, behaviorDt = 0.1)
        val byKind = sim.organisms.associateBy { it.brainKind }
        assertTrue("tang ${byKind[BrainKind.TANG]?.behaviorName}", byKind[BrainKind.TANG]!!.behaviorName == "Sleeping")
        assertTrue("wrasse ${byKind[BrainKind.WRASSE]?.behaviorName}", byKind[BrainKind.WRASSE]!!.behaviorName.contains("cocoon"))
        assertTrue("lawnmower ${byKind[BrainKind.LAWNMOWER_BLENNY]?.behaviorName}", byKind[BrainKind.LAWNMOWER_BLENNY]!!.posture == 3)
        assertTrue("brittle star ${byKind[BrainKind.BRITTLE_STAR]?.behaviorName}", byKind[BrainKind.BRITTLE_STAR]!!.posture != 3)
    }

    /** Every brain survives a day of mixed scenarios without NaN positions or leaving the tank. */
    @Test
    fun allBrainsStayFiniteAndInWater() {
        val sim = fullTank(seed = 7, hour = 7.5)
        val cmds = listOf<(Simulation) -> Unit>(
            { it.enqueue(Command.Feed(FoodKind.FLAKE, 0.4)) },
            { it.enqueue(Command.Feed(FoodKind.NORI, 1.0)) },
            { it.enqueue(Command.TapGlass(Vec3(30.0, 20.0, 33.0))) },
            { it.enqueue(Command.Shadow(Vec3(15.0, 56.0, 27.0))) },
            { it.enqueue(Command.Powerheads(false)) },
            { it.enqueue(Command.Powerheads(true)) },
            { it.enqueue(Command.Feed(FoodKind.PELLET, 0.3)) },
        )
        for (round in 0 until 14) {
            cmds[round % cmds.size](sim)
            sim.advance(120.0, behaviorDt = 0.1)
            sim.advanceAbstract(2400.0)
            for (o in sim.organisms) {
                assertTrue("${o.nickname} NaN", o.pos.isFinite() && o.heading.isFinite() && o.up.isFinite())
                assertTrue("${o.nickname} outside ${o.pos}", sim.rock.inWater(o.pos, -0.5))
            }
        }
        val pose = sim.snapshot().organisms.first()
        assertTrue(pose.spine.all { it.isFinite() } && pose.frames.all { it.isFinite() })
    }

    /** Engineer goby: digs sand piles by day near its burrow, never strays far, withdraws out of sight at night. */
    @Test
    fun gobyDigsAndHides() {
        val sim = fullTank()
        val goby = sim.organisms.first { it.brainKind == BrainKind.ENGINEER_GOBY }
        val mouth = (sim.agentOf(goby)!!.brain as GobyBrain).mouth
        var maxDist = 0.0
        sim.advance(3600.0, behaviorDt = 0.1) { maxDist = maxOf(maxDist, goby.pos.distanceTo(mouth)) }
        assertTrue("no sand piles after an hour", sim.sandPiles.isNotEmpty())
        assertTrue("goby strayed $maxDist cm from its burrow", maxDist < 30.0)
        val night = fullTank(hour = 22.5)
        night.advance(300.0, behaviorDt = 0.1)
        val g = night.organisms.first { it.brainKind == BrainKind.ENGINEER_GOBY }
        assertTrue("goby at night: ${g.behaviorName} posture ${g.posture} visible ${g.visible}", g.posture == 3 && !g.visible)
    }

    /** Relationship memory: a fish that was chased fears its attacker and keeps clear of it. */
    @Test
    fun victimsRememberAttackers() {
        val sim = fullTank()
        val a = sim.organisms.first { it.brainKind == BrainKind.DOTTYBACK }
        val v = sim.organisms.first { it.brainKind == BrainKind.PEEKER_BLENNY }
        repeat(3) { Social.recordAttack(a, v, 0.6) }
        v.pos = a.pos + com.reeflive.core.geom.Vec3(8.0, 0.0, 0.0)
        sim.advance(5.0, behaviorDt = 0.1)
        assertTrue("fear ${v.relations[a.id]?.fear}", (v.relations[a.id]?.fear ?: 0.0) > 0.5)
        assertTrue("wary of ${v.wary}", v.wary === a)
    }
}
