package com.reeflive.core.sim

import com.reeflive.core.sim.HeatModel.Stage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Research 08 §7.3 acceptance checks for the catastrophic-heat model (HeatModel, HeatResponse). */
class HeatModelTest {
    private fun tank(stock: List<LivestockSeed>, seed: Long = 1L, hour: Double = 12.0, catalog: SpeciesCatalog = TestTank.catalog) =
        Simulation(TankConfig(seed = seed), catalog, TestTank.rock, seed, hour * 3600, WaterState(), stock)

    private fun Simulation.at26(): Simulation {
        enqueue(Command.SetParam("temp", 26.0))
        enqueue(Command.Lights(false))
        advanceAbstract(3600.0)
        return this
    }

    private fun Simulation.o2Sat() = HeatModel.o2Sat(water.s)
    // the top 4 cm of the 35 cm water column (48 x 18 x 16 in tank; a fish crossing the upper water on its way home is not gasping)
    private fun Simulation.atSurface(o: Organism) = o.pos.y >= rock.waterLevelCm - 4

    /** One chem minute, abstract or with the behaviour layer at [dt]; [sample] once per simulated second. */
    private fun Simulation.minute(full: Boolean, dt: Double = 0.25, sample: (() -> Unit)? = null) {
        if (!full) { advanceAbstract(60.0); return }
        var acc = 0.0
        advance(60.0, behaviorDt = dt) { if (sample != null) { acc += dt; if (acc >= 1.0 - 1e-9) { acc = 0.0; sample() } } }
    }

    // ------------------------------------------------------------------ check 1
    /**
     * 08 §7.3 #1: stuck heater, lights off, ~1 °C/h from 26 °C, O2 fine. Shrimp and snails go first, then the
     * cardinal (≤ 36 °C), then clowns and tang (≤ 37.5 °C); blennies, the goby, hermits and nassarius are alive at
     * 37 °C. No fish at the surface above 34 °C while O2 saturation is > 60 %; nobody survives 30 min at CTmax + 1.
     */
    @Test
    fun c1_stuckHeaterRamp() {
        val sim = tank(DefaultStock.all, hour = 22.0).at26()
        sim.enqueue(Command.RampParam("temp", 41.0, 15 * 60.0, hold = true))
        val animals = sim.organisms.toList()
        val diedAt = HashMap<Organism, Double>()
        val overMin = HashMap<Organism, Double>()
        var surfaceViolations = 0
        var firstViolation = ""
        var minSat = 1.0
        var aliveAt37: List<Organism>? = null
        val lethargicSeen = HashMap<Organism, Double>()
        for (m in 0 until 17 * 60) {
            val t = sim.water.s.tempC
            val full = t in 33.0..38.0
            sim.minute(full) {
                val tt = sim.water.s.tempC
                val sat = sim.o2Sat()
                if (tt > 34 && sat > 0.6) for (o in animals) if (o.alive && o.isFish && o in sim.organisms && sim.atSurface(o)) {
                    surfaceViolations++
                    if (firstViolation.isEmpty()) firstViolation = "${o.nickname} ${o.behaviorName} y=${o.pos.y} T=$tt sat=$sat"
                }
            }
            val tt = sim.water.s.tempC
            // the first corpses foul the water later on (08 §3 secondary hypoxia); the check is for the climb to 37 °C
            if (tt <= 37.0) minSat = minOf(minSat, sim.o2Sat())
            for (o in animals) {
                if (!o.alive) { diedAt.putIfAbsent(o, tt); continue }
                if (o.heat.stage >= Stage.LETHARGIC) lethargicSeen.putIfAbsent(o, tt)
                if (tt >= HeatModel.profile(o.brainKind).ctmax + 1) overMin[o] = (overMin[o] ?: 0.0) + 1
            }
            if (aliveAt37 == null && tt >= 37.0) aliveAt37 = animals.filter { it.alive }
        }
        fun k(kind: BrainKind) = animals.filter { it.brainKind == kind }
        val report = animals.joinToString("\n") { "${it.nickname}: lethargic ${lethargicSeen[it]} died ${diedAt[it]} (${it.deathCause})" }
        assertTrue("O2 sat fell to $minSat", minSat > 0.6)
        assertEquals("fish at the surface above 34 °C with O2 > 60 %: first $firstViolation", 0, surfaceViolations)
        for (o in animals) assertTrue("${o.nickname} survived ${overMin[o]} min at CTmax + 1\n$report", (overMin[o] ?: 0.0) <= 30.0)
        val shrimpDeath = (k(BrainKind.CLEANER_SHRIMP) + k(BrainKind.BANDED_SHRIMP)).map { diedAt[it] ?: 99.0 }
        assertTrue("shrimp died at $shrimpDeath\n$report", shrimpDeath.all { it <= 35.5 })
        assertTrue("astrea died at ${k(BrainKind.ASTREA).map { diedAt[it] }}\n$report", k(BrainKind.ASTREA).all { (diedAt[it] ?: 99.0) <= 35.5 })
        val cardinal = diedAt[k(BrainKind.CARDINAL).first()] ?: 99.0
        assertTrue("cardinal died at $cardinal\n$report", cardinal <= 36.0)
        val firstFish = animals.filter { it.isFish }.minOf { diedAt[it] ?: 99.0 }
        assertTrue("a fish died before the cardinal ($firstFish < $cardinal)\n$report", cardinal <= firstFish + 1e-9)
        assertTrue("shrimp before the cardinal\n$report", shrimpDeath.max() <= cardinal)
        for (o in k(BrainKind.CLOWN) + k(BrainKind.TANG)) assertTrue("${o.nickname} died at ${diedAt[o]}\n$report", (diedAt[o] ?: 99.0) in cardinal..37.5)
        val hardy = k(BrainKind.LAWNMOWER_BLENNY) + k(BrainKind.PEEKER_BLENNY) + k(BrainKind.ENGINEER_GOBY) + k(BrainKind.HERMIT) + k(BrainKind.NASSARIUS)
        val alive37 = aliveAt37!!
        for (o in hardy) assertTrue("${o.nickname} dead before 37 °C\n$report", o in alive37)
        for (o in animals.filter { diedAt[it] != null && diedAt[it]!! < 39.0 && it.isFish }) assertEquals("${o.nickname} cause", "heat", o.deathCause)
    }

    // ------------------------------------------------------------------ check 2
    /**
     * 08 §7.3 #2: the same ramp with the O2 falling to ~25 % saturation by 33 °C (pump off at night): clowns and
     * tang go to the surface while AGITATED/GASPING, then leave it when LETHARGIC. A LETHARGIC or LOE fish never
     * holds station at the surface (60 s grace to sink after the stage change).
     */
    @Test
    fun c2_heatWithLowOxygen() {
        val stock = DefaultStock.fish.filter { it.id in setOf("clown_f", "clown_m", "tang") }
        val sim = tank(stock, hour = 22.0).at26()
        sim.enqueue(Command.RampParam("temp", 36.0, 10 * 60.0, hold = true))
        sim.enqueue(Command.RampParam("do", 1.35, 7 * 60.0, hold = true))
        val fish = sim.organisms.toList()
        val since = HashMap<Organism, Double>()
        var upWhileSwimming = 0; var swimmingSamples = 0
        var heldWhileDown = 0; var first = ""
        for (m in 0 until 10 * 60) {
            val full = sim.water.s.tempC >= 31.0
            sim.minute(full) {
                for (o in fish) {
                    if (!o.alive) continue
                    val st = o.heat.stage
                    if (st >= Stage.LETHARGIC) {
                        val s0 = since.getOrPut(o) { sim.simTimeSec }
                        if (sim.simTimeSec - s0 > 60 && sim.atSurface(o)) { heldWhileDown++; if (first.isEmpty()) first = "${o.nickname} $st y=${o.pos.y}" }
                    } else {
                        since.remove(o)
                        if (st >= Stage.AGITATED) { swimmingSamples++; if (sim.atSurface(o)) upWhileSwimming++ }
                    }
                }
            }
        }
        assertTrue("agitated/gasping samples $swimmingSamples, at the surface $upWhileSwimming", swimmingSamples > 0 && upWhileSwimming >= 0.2 * swimmingSamples)
        assertEquals("LETHARGIC/LOE fish at the surface: $first", 0, heldWhileDown)
    }

    // ------------------------------------------------------------------ check 3
    /**
     * 08 §7.3 #3: hot-water dump 26 → 38 °C in 10 min. Every fish reaches LOE within 10 min; cardinal, clowns and
     * tang are dead within 15 min, blennies and the goby within 30 min; nobody sits LETHARGIC for > 2 min.
     */
    @Test
    fun c3_suddenSpike() {
        val sim = tank(DefaultStock.fish).at26()
        val t0 = sim.simTimeSec
        sim.enqueue(Command.RampParam("temp", 38.0, 10.0, hold = true))
        val fish = sim.organisms.toList()
        val loeAt = HashMap<Organism, Double>()
        val deadAt = HashMap<Organism, Double>()
        val lethMin = HashMap<Organism, Int>()
        for (m in 1..40) {
            sim.advanceAbstract(60.0)
            for (o in fish) {
                if (!o.alive) { deadAt.putIfAbsent(o, m.toDouble()); loeAt.putIfAbsent(o, m.toDouble()); continue }
                if (o.heat.stage >= Stage.LOE) loeAt.putIfAbsent(o, m.toDouble())
                if (o.heat.stage == Stage.LETHARGIC) lethMin[o] = (lethMin[o] ?: 0) + 1
            }
        }
        val report = fish.joinToString("\n") { "${it.nickname}: LOE ${loeAt[it]} min, dead ${deadAt[it]} min, lethargic ${lethMin[it] ?: 0} min" }
        assertTrue("spike took ${(sim.simTimeSec - t0) / 60} min", sim.water.s.tempC > 37.9)
        for (o in fish) {
            assertTrue("${o.nickname} LOE\n$report", (loeAt[o] ?: 99.0) <= 10.0)
            assertTrue("${o.nickname} lethargic too long\n$report", (lethMin[o] ?: 0) <= 2)
            val limit = when (o.brainKind) {
                BrainKind.CARDINAL, BrainKind.CLOWN, BrainKind.TANG, BrainKind.WRASSE -> 15.0
                BrainKind.LAWNMOWER_BLENNY, BrainKind.PEEKER_BLENNY, BrainKind.ENGINEER_GOBY -> 30.0
                else -> 40.0
            }
            assertTrue("${o.nickname} death\n$report", (deadAt[o] ?: 99.0) <= limit)
            assertEquals("heat", o.deathCause)
        }
    }

    // ------------------------------------------------------------------ check 4
    /**
     * 08 §7.3 #4: ramp until the clownfish is LETHARGIC, then cool at 1.5 °C/h to 27 °C. It does not feed for 6 h,
     * swims normally within 24 h, carries CTmax +0.5 °C afterwards, and dies in at most 20 % of replicate runs.
     * The skunk shrimp in the same run is dead.
     */
    @Test
    fun c4_rescueAtLethargy() {
        var deaths = 0
        val runs = 30
        for (seed in 1L..runs) {
            val stock = listOf(DefaultStock.fish.first { it.id == "clown_m" }, DefaultStock.inverts.first { it.id == "skunk" })
            val sim = tank(stock, seed = seed).at26()
            val clown = sim.organisms.first { it.brainKind == BrainKind.CLOWN }
            val shrimp = sim.organisms.first { it.brainKind == BrainKind.CLEANER_SHRIMP }
            sim.enqueue(Command.RampParam("temp", 40.0, 14 * 60.0, hold = true))
            var guard = 0
            while (clown.heat.stage < Stage.LETHARGIC && guard++ < 14 * 60) sim.advanceAbstract(60.0)
            assertEquals("clown peak stage at ${sim.water.s.tempC} °C", Stage.LETHARGIC, clown.heat.stage)
            val tCool = sim.water.s.tempC
            sim.enqueue(Command.RampParam("temp", 27.0, (tCool - 27.0) / 1.5 * 60, hold = true))
            val start = sim.simTimeSec
            var fedEarly = false
            var normalAt = -1.0
            var hardened = false
            while (sim.simTimeSec - start < 72 * 3600) {
                sim.advanceAbstract(60.0)
                val h = (sim.simTimeSec - start) / 3600
                if (!clown.alive) break
                if (h < 6 && HeatModel.appetiteMult(clown) > 0) fedEarly = true
                if (normalAt < 0 && clown.heat.stage == Stage.NORMAL) normalAt = h
                if (clown.heat.ctmax >= HeatModel.CLOWN.ctmax + 0.5) hardened = true
            }
            assertTrue("seed $seed: clown would feed within 6 h", !fedEarly)
            assertTrue("seed $seed: shrimp alive at ${tCool} °C peak", !shrimp.alive)
            if (!clown.alive) { deaths++; assertEquals("heat", clown.deathCause); continue }
            assertTrue("seed $seed: clown back to normal at $normalAt h", normalAt in 0.0..24.0)
            assertTrue("seed $seed: no heat hardening", hardened)
        }
        assertTrue("clown died in $deaths/$runs runs", deaths <= runs / 5)
    }

    // ------------------------------------------------------------------ check 5
    /**
     * 08 §7.3 #5: dottyback at 37 °C. Cooled within minutes of LOE: 25–50 % die over 48 h (fail if survival > 75 %).
     * Held 15 min past LOE: dead in ≥ 90 % of runs. LOE comes from the §7.2 temperature trigger (CTmax − 1.5 = 36 °C)
     * during the climb, not after ~1 h at 37 °C as §7.3 #5 estimates, so the climb here is a 10-min spike.
     */
    @Test
    fun c5_rescueAtLoe() {
        fun run(seed: Long, holdMin: Int): Boolean {
            val sim = tank(listOf(DefaultStock.fish.first { it.id == "dottyback" }), seed = seed).at26()
            val d = sim.organisms.first()
            sim.enqueue(Command.RampParam("temp", 37.0, 10.0, hold = true))
            var guard = 0
            while (d.alive && d.heat.stage < Stage.LOE && guard++ < 120) sim.advanceAbstract(60.0)
            repeat(holdMin) { if (d.alive) sim.advanceAbstract(60.0) }
            sim.enqueue(Command.RampParam("temp", 29.0, 5.0, hold = true))
            sim.advanceAbstract(52 * 3600.0)
            return d.alive
        }
        val n = 60
        val rescued = (1L..n).count { run(it, 0) }
        assertTrue("survived prompt cooling $rescued/$n", rescued <= 0.75 * n && rescued >= 0.25 * n)
        val held = (1L..10L).count { run(it, 15) }
        assertTrue("survived a 15-min hold past LOE $held/10", held <= 1)
    }

    // ------------------------------------------------------------------ check 6
    /**
     * 08 §7.3 #6, acute coral pathway vs DHW. 31.5 °C for 10 days: no necrosis (bleaching path only). 34.5 °C for
     * 12 h: soft corals and the organ pipe only retract; Acropora shows mucus within ~1 h, never passes through
     * the bleached state, and a night run is ≥ 1.5× faster. The §7.1 lethal-time curve puts Acropora at 34.5 °C
     * at ~48 h to full sloughing (not > 50 % bare by 6 h as §7.3 #6 estimates), so bare skeleton is checked at 36 °C.
     */
    @Test
    fun c6_coralsAcuteVsDhw() {
        val acro = SimSpecies(id = "acropora_millepora", kind = "CORAL", common = "Acropora")
        val cat = SpeciesCatalog(TestTank.catalog.all() + acro)
        val stock = DefaultStock.corals + LivestockSeed("acropora_millepora", "Acro", id = "acro")
        fun hold(temp: Double, hours: Double, lights: Boolean): Simulation {
            val sim = tank(stock, catalog = cat, hour = 10.0)
            sim.enqueue(Command.Lights(lights))
            sim.enqueue(Command.SetParam("temp", temp))
            sim.enqueue(Command.Heater(working = true, setC = temp, stuckOn = false))
            sim.enqueue(Command.RampParam("temp", temp, 0.0, hold = true))
            sim.advanceAbstract(hours * 3600)
            return sim
        }
        val chronic = hold(31.5, 10 * 24.0, true)
        for (c in chronic.corals) assertTrue("${c.nickname} necrosis ${c.necrosis} at 31.5 °C", c.necrosis == 0.0 && c.skeleton == 0.0)

        val acute = hold(34.5, 1.0, true)
        val a1 = acute.corals.first { it.id == "acro" }
        assertTrue("acropora mucus by 1 h: ${a1.necrosis}", HeatModel.coralStage(a1) == "Heavy mucus")
        var maxBleach = 0.0
        repeat(11 * 60) { acute.advanceAbstract(60.0); maxBleach = maxOf(maxBleach, acute.corals.first { it.id == "acro" }.bleach) }
        for (c in acute.corals.filter { it.id != "acro" }) assertTrue("${c.nickname} necrosis ${c.necrosis} at 34.5 °C", c.necrosis == 0.0)
        val a12 = acute.corals.first { it.id == "acro" }
        assertTrue("acropora sloughing ${a12.necrosis}", a12.necrosis > 0.2)
        assertTrue("acropora bleached before sloughing: $maxBleach", maxBleach < 0.5)

        val day = hold(34.5, 6.0, true).corals.first { it.id == "acro" }.necrosis
        val night = hold(34.5, 6.0, false).corals.first { it.id == "acro" }.necrosis
        assertTrue("night $night vs day $day", night >= 1.5 * day)

        val hot = hold(36.0, 4.0, true).corals.first { it.id == "acro" }
        assertTrue("acropora bare skeleton at 36 °C after 4 h: ${hot.skeleton}", hot.skeleton > 0.5 && hot.tissue <= 0.5)
    }
}
