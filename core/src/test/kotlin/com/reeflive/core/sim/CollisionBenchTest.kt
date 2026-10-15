package com.reeflive.core.sim

import com.reeflive.core.geom.HeightField
import org.junit.Test
import java.io.File

/**
 * Cost of the body collision on Tee's preset (the owner's tank: baked rock, drawn sand mesh, default stock): the same
 * day sim with the collision on and off, µs per 20 Hz tick. Off unless REEFLIVE_BENCH_MIN (sim minutes per run) is
 * set, so the suite does not pay for it:
 *   REEFLIVE_BENCH_MIN=3 ./gradlew -PcoreOnly --offline :core:test --tests '*CollisionBenchTest' -i | grep CollisionBench
 */
class CollisionBenchTest {
    @Test
    fun collisionCostPerTick() {
        val minutes = System.getenv("REEFLIVE_BENCH_MIN")?.toDoubleOrNull() ?: return
        run(minutes)
    }

    companion object {
        private val tankGlb: File? = listOf("../app/src/main/assets/models/tank.glb", "app/src/main/assets/models/tank.glb").map(::File).firstOrNull { it.isFile }

        private fun teeSim(seed: Long, hour: Double): Simulation {
            val rock = TestTank.rock.forSimulation().also { r -> tankGlb?.let { r.sandBase = HeightField.fromGlb(it.readBytes(), r.lengthCm, r.widthCm) } }
            return Simulation(TankConfig(seed = seed), TestTank.catalog, rock, seed, hour * 3600, WaterState(), DefaultStock.all)
        }

        fun cost(on: Boolean, minutes: Double, seed: Long = 11, hour: Double = 11.0): Double {
            val sim = teeSim(seed, hour)
            sim.bodies.enabled = on
            repeat(400) { sim.advance(0.05, 0.05) }
            val n = (minutes * 60 / 0.05).toInt()
            val e0 = sim.nav.expanded
            var h = 0.0
            val mx = java.lang.management.ManagementFactory.getThreadMXBean()
            val c0 = mx.currentThreadCpuTime
            val t0 = System.nanoTime()
            repeat(n) { sim.advance(0.05, 0.05) }
            val wall = (System.nanoTime() - t0) / 1000.0 / n
            // thread CPU time: steadier than wall time on a shared machine
            val us = (mx.currentThreadCpuTime - c0) / 1000.0 / n
            for (o in sim.organisms) h += o.pos.x * 1.1 + o.pos.y * 1.3 + o.pos.z * 1.7 + o.heading.x + o.up.y
            println("CollisionBench:   nav cells %.0f/tick, wall %.0f µs/tick, state hash %.12f".format((sim.nav.expanded - e0).toDouble() / n, wall, h))
            println("CollisionBench: %s seed %d hour %.1f: %.0f µs CPU/tick over %d ticks (env calls %d, env pushes %d, pair pushes %d, obstacle pushes %d, holds %d)".format(
                if (on) "on " else "off", seed, hour, us, n, sim.bodies.envCalls, sim.bodies.envPushes, sim.bodies.pairPushes, sim.bodies.obstaclePushes, sim.bodies.wedgeRestores))
            return us
        }

        fun run(minutes: Double) {
            cost(true, 0.5); cost(false, 0.5)   // warm-up
            val on = (cost(true, minutes) + cost(true, minutes, 12, 22.5)) / 2
            val off = (cost(false, minutes) + cost(false, minutes, 12, 22.5)) / 2
            println("CollisionBench: %.0f µs/tick on, %.0f µs/tick off, collision %.0f µs/tick".format(on, off, on - off))
        }

        @JvmStatic fun main(args: Array<String>) {
            if (args.getOrNull(0) == "on") { cost(true, 0.5); cost(true, args.getOrNull(1)?.toDouble() ?: 5.0); return }
            run(args.getOrNull(0)?.toDouble() ?: 3.0)
        }
    }
}
