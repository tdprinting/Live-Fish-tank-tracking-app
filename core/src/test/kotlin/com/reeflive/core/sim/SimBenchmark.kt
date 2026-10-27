package com.reeflive.core.sim

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.management.ManagementFactory

/**
 * Per-tick cost of the live tank (the default stock on the baked rockscape) at 1x, 60x and 1000x, driven the way the
 * web page drives it: [Simulation.stepWall] at 20 Hz, which also builds the renderer's [Simulation.snapshot].
 *
 * Wall time and allocated bytes are measured per tick on the sim thread. A sampling thread reads the sim thread's
 * stack every ~0.3 ms and charges each sample to (a) the step phase (the frame [Simulation.stepWall] /
 * [Simulation.advanceInternal] called) and (b) a subsystem (the innermost frame that belongs to a known one), so the
 * shares are statistical, not exact. Results are printed and written to `core/build/sim-benchmark.txt`.
 *
 * [crowdedTankTickCost] does the same for the default stock plus ~50 more animals (scaling of the neighbour work).
 *
 * Run: `./gradlew -PcoreOnly :core:test --offline --tests '*SimBenchmark*'`. Ticks per run can be raised with
 * `-PbenchTicks=N` (default 400, run 3 times); `-PbenchDetail=true` adds self / inclusive frame histograms.
 */
class SimBenchmark {

    private val detail = System.getProperty("reeflive.benchDetail") == "true" || System.getenv("REEFLIVE_BENCH_DETAIL") == "1"
    private val ticks = (System.getProperty("reeflive.benchTicks") ?: "400").toInt()

    @Test
    fun liveTankTickCost() {
        val sim = TestTank.sim(DefaultStock.all, seed = 1L, startHour = 12.0,
            water = WaterState(volumeL = TankConfig().systemLiters))
        run("default tank", sim, listOf(1.0, 60.0, 1000.0), "build/sim-benchmark.txt")
    }

    /** The default stock plus about 50 more animals from the shop list: how the tick scales with a crowded tank. */
    @Test
    fun crowdedTankTickCost() {
        val sim = Simulation(TankConfig(seed = 1L), SpeciesCatalog(), TestTank.rock, 1L, 12 * 3600.0, livestock = DefaultStock.all)
        for (e in Stocking.inGroup(StockGroup.FISH).take(8)) sim.enqueue(Command.AddMany(e.speciesId, 4))
        for (e in Stocking.inGroup(StockGroup.INVERTS).take(5)) sim.enqueue(Command.AddMany(e.speciesId, 4))
        sim.stepWall(0.0)
        run("crowded tank", sim, listOf(1.0, 60.0), "build/sim-benchmark-crowded.txt")
    }

    private fun run(name: String, sim: Simulation, speeds: List<Double>, file: String) {
        val out = StringBuilder()
        fun log(s: String) { println(s); out.append(s).append('\n') }
        log("SimBenchmark ($name): ${sim.organisms.size} organisms, ${sim.corals.size} corals, $ticks ticks x $ROUNDS per speed")
        // JIT warm-up at 1x and 60x
        sim.speed = 1.0; repeat(600) { sim.stepWall(TICK) }
        sim.speed = 60.0; repeat(200) { sim.stepWall(TICK) }

        val totals = ArrayList<Double>()
        val summary = ArrayList<String>()
        for (speed in speeds) {
            sim.speed = speed
            repeat(60) { sim.stepWall(TICK) }
            val r = measure(sim, ticks)
            totals += r.meanMs
            log("")
            log("== ${speed.toInt()}x  (abstract=${sim.abstractMode})")
            log("state hash %08x".format(stateHash(sim)))
            log("tick: cpu mean %.3f ms (best round %.3f)  wall p50 %.3f  p95 %.3f  max %.3f   alloc %.1f KB/tick".format(r.meanMs, r.bestMs, r.p50, r.p95, r.max, r.allocKb))
            summary += "%6sx  cpu mean %7.3f ms  best round %7.3f ms  wall p50 %7.3f ms  alloc %7.1f KB/tick".format(speed.toInt().toString(), r.meanMs, r.bestMs, r.p50, r.allocKb)
            log("-- by phase (share of tick, ms/tick)")
            r.phases.entries.sortedByDescending { it.value }.take(if (detail) 200 else 25).forEach { (k, v) ->
                log("  %-34s %5.1f%%  %7.3f".format(k, 100.0 * v / r.samples, r.meanMs * v / r.samples))
            }
            log("-- by subsystem (innermost known frame)")
            r.systems.entries.sortedByDescending { it.value }.forEach { (k, v) ->
                log("  %-34s %5.1f%%  %7.3f".format(k, 100.0 * v / r.samples, r.meanMs * v / r.samples))
            }
            if (detail) {
                log("-- top self frames")
                r.selfs.entries.sortedByDescending { it.value }.take(40).forEach { (k, v) -> log("  %-60s %5.1f%%".format(k, 100.0 * v / r.samples)) }
                log("-- top inclusive frames")
                r.incl.entries.sortedByDescending { it.value }.take(150).forEach { (k, v) -> log("  %-60s %5.1f%%".format(k, 100.0 * v / r.samples)) }
            }
        }
        log("")
        log("== summary ($name)")
        summary.forEach(::log)
        File("build").mkdirs()
        File(file).writeText(out.toString())
        assertTrue("ticks ran", totals.all { it > 0 })
    }

    /**
     * A hash over the sim state and the last snapshot's poses: an optimisation that keeps the behaviour bit-identical
     * (same RNG call order, same arithmetic) leaves it unchanged.
     */
    private fun stateHash(sim: Simulation): Int {
        var h = 17
        fun mix(v: Int) { h = h * 31 + v }
        fun mix(v: Double) = mix(v.toBits().hashCode())
        fun mix(v: com.reeflive.core.geom.Vec3) { mix(v.x); mix(v.y); mix(v.z) }
        for (o in sim.organisms) {
            mix(o.id.hashCode()); mix(o.pos); mix(o.heading); mix(o.vel); mix(o.up)
            mix(o.hunger); mix(o.alive.hashCode()); mix(o.behaviorName.hashCode())
        }
        for (p in sim.latest.organisms) { mix(p.id.hashCode()); mix(p.spine.contentHashCode()); mix(p.frames.contentHashCode()); mix(p.posture) }
        for (c in sim.corals) { mix(c.id.hashCode()); mix(c.tissue); mix(c.extension) }
        mix(sim.water.s.hashCode())
        mix(sim.simTimeSec)
        return h
    }

    private class Result(val meanMs: Double, val bestMs: Double, val p50: Double, val p95: Double, val max: Double, val allocKb: Double,
                         val samples: Int, val phases: Map<String, Int>, val systems: Map<String, Int>,
                         val selfs: Map<String, Int>, val incl: Map<String, Int>)

    private fun measure(sim: Simulation, n: Int): Result {
        val simThread = Thread.currentThread()
        val phases = HashMap<String, Int>(); val systems = HashMap<String, Int>(); val selfs = HashMap<String, Int>(); val incl = HashMap<String, Int>()
        val stop = java.util.concurrent.atomic.AtomicBoolean(false)
        var samples = 0
        val sampler = Thread {
            while (!stop.get()) {
                val st = simThread.stackTrace
                if (st.any { it.className.endsWith(".Simulation") && it.methodName == "stepWall" }) {
                    samples++
                    phases.merge(phaseOf(st), 1, Int::plus)
                    systems.merge(systemOf(st), 1, Int::plus)
                    if (st.isNotEmpty()) selfs.merge(st[0].className.substringAfterLast('.') + "." + st[0].methodName, 1, Int::plus)
                    val seen = HashSet<String>()
                    for (f in st) { val k = f.className.substringAfterLast('.') + "." + f.methodName; if (seen.add(k)) incl.merge(k, 1, Int::plus) }
                }
                Thread.sleep(0, 300_000)
            }
        }.apply { isDaemon = true; start() }
        val mx = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        val tid = @Suppress("DEPRECATION") simThread.id
        val times = DoubleArray(n * ROUNDS)
        val roundCpu = DoubleArray(ROUNDS)
        val a0 = mx.getThreadAllocatedBytes(tid)
        for (i in 0 until n * ROUNDS) {
            val c0 = mx.currentThreadCpuTime
            val t0 = System.nanoTime()
            sim.stepWall(TICK)
            times[i] = (System.nanoTime() - t0) / 1e6
            roundCpu[i / n] += (mx.currentThreadCpuTime - c0) / 1e6
        }
        val alloc = mx.getThreadAllocatedBytes(tid) - a0
        stop.set(true); sampler.join()
        val sorted = times.sorted()
        val m = sorted.size
        return Result(roundCpu.sum() / m, roundCpu.min() / n, sorted[m / 2], sorted[(m * 95) / 100], sorted.last(), alloc / 1024.0 / m,
            samples.coerceAtLeast(1), phases, systems, selfs, incl)
    }

    /** The frame that stepWall / advanceInternal / behaviorTick / snapshot called (outermost first). */
    private fun phaseOf(st: Array<StackTraceElement>): String {
        var i = st.indexOfLast { it.className.endsWith(".Simulation") && it.methodName == "stepWall" }
        val descend = setOf("stepWall", "advanceInternal")
        while (i > 0) {
            val f = st[i]
            if (!(f.className.endsWith(".Simulation") && f.methodName in descend)) break
            i--
        }
        if (i < 0) return "stepWall (self)"
        val f = st[i]
        val name = "${f.className.substringAfterLast('.')}.${f.methodName}"
        // one level deeper for the two big phases
        if (f.className.endsWith(".Simulation") && f.methodName in setOf("behaviorTick", "snapshot", "buildSnapshot", "chemTick", "abstractPlacement") && i > 0) {
            var j = i - 1
            while (j > 0 && st[j].className.endsWith(".Simulation") && st[j].methodName in setOf("buildSnapshot", "statsAll")) j--
            val g = st[j]
            return "$name > ${g.className.substringAfterLast('.').substringBefore('$')}.${g.methodName}"
        }
        return name
    }

    private fun systemOf(st: Array<StackTraceElement>): String {
        for (f in st) {
            val c = f.className
            val m = f.methodName
            val s = when {
                c.contains("PairMatrix") || c.contains(".ai.Social") || c.contains("Interactions") -> "social/PairMatrix"
                c.contains("Thoughts") || c.contains("ThoughtBook") || c.contains(".ai.Caption") -> "thoughts/captions (snapshot)"
                c.contains("BodySpline") || c.contains("EyeCollider") -> "pose/body spline"
                c.contains("RockGuard") || c.contains("BodyBounds") || c.contains("BodyProfile") -> "collision (rock guard/bounds)"
                c.contains("Steering") -> "steering"
                c.contains("Perception") || c.contains(".ai.Perceive") || c.contains("Senses") -> "perception"
                c.contains("RockPhysics") -> "rock physics"
                c.contains("SandBed") || c.contains("SandContact") || c.contains("SandWork") -> "sand"
                c.contains("CrawlerSeat") || c.contains("LimbRig") || c.contains("ShrimpLimbs") || c.contains("BrittleArms") || c.contains("SnailRig") || c.contains("AnemoneRig") || c.contains("ClamRig") || c.contains("SquidRig") -> "seats/limb rigs"
                c.contains("AlgaeModel") || c.contains("GlassMask") || c.contains("GlassTrails") -> "algae/glass"
                c.contains("CoralGrowth") || c.contains("CoralHealth") || c.contains("CoralSim") || c.contains("GspPolyps") || c.contains("Xenia") || c.contains("StonyCorals") -> "coral"
                c.contains("Microfauna") || c.contains("MicroCritters") || c.contains("Bristleworms") -> "microfauna/worms"
                c.contains("WaterSim") || c.contains(".chem.") || c.contains("BacteriaModel") || c.contains("WasteSystem") -> "water/chem/waste"
                c.contains("FoodSystem") -> "food"
                c.contains("FlowField") || c.contains("FluidSim") || c.contains("WaterSurface") -> "flow/surface"
                c.contains("HealthModel") || c.contains("StressModel") || c.contains("HeatModel") -> "health/stress"
                c.contains(".ai.") -> "AI brains"
                c.endsWith(".Simulation") && (m == "snapshot" || m == "buildSnapshot" || m == "statsAll") -> "snapshot (other)"
                c.contains(".Organism") && m == "toPose" -> "snapshot (other)"
                else -> null
            }
            if (s != null) return s
        }
        return "other"
    }

    companion object { const val TICK = 0.05; const val ROUNDS = 3 }
}
