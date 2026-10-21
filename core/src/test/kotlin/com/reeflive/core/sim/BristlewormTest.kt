package com.reeflive.core.sim

import com.reeflive.core.ai.ArrowCrabBrain
import com.reeflive.core.geom.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.min

/**
 * Bristleworms and the arrow crab (research 42): worms hitchhike in slowly, breed faster on leftovers, show themselves
 * at night and after a feed, eat what reaches the bottom and get caught; the arrow crab hunts them on planted legs.
 */
class BristlewormTest {
    private val arrow = "stenorhynchus_seticornis"

    @Test fun wormsHitchhikeInOnLiveRockAndNotOnDryRock() {
        val sim = TestTank.sim(DefaultStock.corals, seed = 3L)
        sim.water.micro.setTotal(MicroGroup.BRISTLEWORM, 0.0)
        // off (the default tank): nothing arrives
        repeat(50) { Hitchhikers.onAdded(sim, HitchSource.LIVE_ROCK, null) }
        assertEquals(0.0, sim.water.micro.total(MicroGroup.BRISTLEWORM), 1e-9)
        sim.hitchhikersEnabled = true
        repeat(50) { Hitchhikers.onAdded(sim, HitchSource.DRY_ROCK, null) }
        assertEquals(0.0, sim.water.micro.total(MicroGroup.BRISTLEWORM), 1e-9)
        Hitchhikers.onAdded(sim, HitchSource.LIVE_ROCK, Vec3(60.0, 10.0, 20.0))
        Hitchhikers.onAdded(sim, HitchSource.LIVE_ROCK, Vec3(60.0, 10.0, 20.0))
        val n = sim.water.micro.total(MicroGroup.BRISTLEWORM)
        assertTrue("live rock brought worms ($n)", n in 1.0..6.0)
        assertTrue("an arrival event", sim.drainEvents().any { it is SimEvent.Hitchhiker && it.kind == "bristleworm" })
    }

    @Test fun backgroundArrivalIsSlow() {
        // a stocked dry-rock tank gets its first worm within months, not days (1/60 per day)
        var firstDays = 0.0
        for (seed in 1L..20L) {
            val sim = TestTank.sim(DefaultStock.corals, seed = seed)
            sim.water.micro.setTotal(MicroGroup.BRISTLEWORM, 0.0)
            sim.hitchhikersEnabled = true
            var day = 0
            while (day < 400 && sim.water.micro.total(MicroGroup.BRISTLEWORM) < 1) { Hitchhikers.step(sim, 1440.0); day++ }
            firstDays += day
        }
        val mean = firstDays / 20
        assertTrue("mean first arrival $mean days", mean in 20.0..150.0)
    }

    @Test fun leftoversMakeTheWormsBoomAndTheyEatDetritus() {
        fun run(leftover: Double): MicrofaunaModel {
            val w = WaterState(tempC = 26.0)
            val m = MicrofaunaModel(TankConfig())
            m.setTotal(MicroGroup.BRISTLEWORM, 10.0)
            val lights = LightSchedule()
            var t = 0.0
            repeat(120 * 24) {
                w.detritus = 0.3
                m.leftoverGPerDay = leftover
                m.step(w, 1.0, lights.intensity(t), 1.0)
                t += 3600
            }
            return m
        }
        val lean = run(0.0)
        val fat = run(3.0)
        val nLean = lean.total(MicroGroup.BRISTLEWORM)
        val nFat = fat.total(MicroGroup.BRISTLEWORM)
        println("worms after 120 d: lean $nLean, overfed $nFat (ref K ${lean.refCapacity(MicroGroup.BRISTLEWORM)})")
        assertTrue("an overfed tank grows far more worms", nFat > 2 * nLean)
        assertTrue("they scavenge", fat.scavengedTanG > 0)
    }

    @Test fun wormsComeOutAtNightEatSettledFoodAndCanBeCaught() {
        val sim = TestTank.sim(DefaultStock.corals + TestTank.seed("crab"), seed = 5L, startHour = 1.0)
        sim.water.micro.setTotal(MicroGroup.BRISTLEWORM, 80.0)
        // (the population's night exposure eases in over the first hour)
        sim.advance(3600.0, behaviorDt = 0.1)
        val f = sim.worms
        assertTrue("worms out at night (${f.exposedCount()}, target ${f.target})", f.exposedCount() >= 2)
        assertTrue("poses in the snapshot", sim.snapshot().worms.isNotEmpty())
        // every worm head sits on the rock or sand, not in it
        for (w in f.worms) if (w.exposed) {
            val d = if (w.onSand) w.head.y - sim.sand.surfaceY(w.head.x, w.head.z) else sim.rock.sdf.dist(w.head)
            assertTrue("worm ${w.id} head off the surface by $d", d > -0.3 && d < 1.0)
        }
        // food dropped near the rock reaches them
        val w0 = f.worms.first { it.exposed }
        sim.enqueue(Command.Feed(FoodKind.PELLET, 0.3, w0.head + Vec3(0.0, 4.0, 0.0)))
        sim.advance(900.0, behaviorDt = 0.05)
        assertTrue("worms ate settled food (${f.foodEaten})", f.foodEaten > 0)
        val before = sim.water.micro.total(MicroGroup.BRISTLEWORM)
        val w = f.worms.firstOrNull { it.exposed } ?: return
        assertTrue(f.catch(w, sim.organisms.first()))
        assertEquals(before - 1, sim.water.micro.total(MicroGroup.BRISTLEWORM), 1e-6)
    }

    @Test fun arrowCrabHuntsWormsOnPlantedLegs() {
        val sim = TestTank.sim(DefaultStock.corals, seed = 7L, startHour = 21.0)
        sim.species.ensure(arrow)
        sim.water.micro.setTotal(MicroGroup.BRISTLEWORM, 150.0)
        sim.enqueue(Command.AddOrganism(arrow, 4.5, "Arrow"))
        sim.advance(1.0, behaviorDt = 0.05)
        val o = sim.organisms.first { it.brainKind == BrainKind.ARROW_CRAB }
        o.hunger = 0.8
        val b = sim.agentOf(o)!!.brain as ArrowCrabBrain
        val surf = sim.crawlSurface
        var samples = 0; var bodyIn = 0; var footIn = 0; var worstFoot = 0.0; var moved = 0.0
        var last = o.pos
        val why = HashMap<String, Int>(); var worstBody = 0.0
        sim.advance(3 * 3600.0, behaviorDt = 0.05) {
            val seat = o.seat ?: return@advance
            if (!o.alive || !o.visible || !o.seatOn || seat.blend < 1.0) return@advance
            samples++
            moved += o.pos.distanceTo(last); last = o.pos
            val m = seat.crab!!
            val c = seat.shape.contacts
            var minD = Double.MAX_VALUE
            var i = 0
            while (i < m.stanceFrom * 3) { minD = min(minD, surf.dist(seat.world(c[i], c[i + 1], c[i + 2], o.lengthCm))); i += 3 }
            if (minD < -0.1) { bodyIn++; val k = "${o.behaviorName} p${o.posture}"; why[k] = (why[k] ?: 0) + 1; worstBody = min(worstBody, minD) }
            for (k in 0 until 8) {
                if (seat.legTucked[k]) continue
                val d = surf.dist(seat.footTip[k])
                worstFoot = min(worstFoot, d)
                if (d < -0.1) footIn++
            }
            if (o.hunger < 0.5) o.hunger = 0.8   // keep it hunting
        }
        println("arrow crab: $samples seated ticks, moved %.0f cm, worms caught ${b.wormsCaught}, crevices probed ${b.crevicesProbed}, body in $bodyIn, feet in $footIn (worst %.2f), body worst %.2f by $why".format(moved, -worstFoot, -worstBody))
        assertTrue("seated", samples > 10000)
        assertTrue("walks about", moved > 50)
        assertTrue("catches worms", b.wormsCaught > 0)
        assertTrue("carapace in the surface on $bodyIn ticks", bodyIn <= samples / 500)
        assertTrue("feet in the surface on $footIn leg-ticks", footIn <= samples / 200)
    }

    /**
     * Tee 2026-10-21: the worms clipped in and out of things, wobbled and rolled. Every drawn station of every worm is
     * either clear of rock and sand by its belly height or hidden well inside (down its hole), and the body's up turns
     * smoothly from tick to tick.
     */
    @Test fun wormsLieOnTheSurfaceAndMoveSmoothly() {
        val sim = TestTank.sim(DefaultStock.corals + TestTank.seed("crab"), seed = 12L, startHour = 1.0)
        sim.water.micro.setTotal(MicroGroup.BRISTLEWORM, 200.0)
        sim.advance(3600.0, behaviorDt = 0.1)
        var stations = 0; var clipped = 0; var worst = 0.0
        val lastUp = HashMap<String, FloatArray>()
        val turns = ArrayList<Double>()
        val fastTurns = ArrayList<Double>()
        var shown = 0
        val why = HashMap<String, Int>()
        val bigTurn = HashMap<String, Int>()
        sim.advance(1800.0, behaviorDt = 0.05) {
            val poses = sim.worms.poses()
            shown = maxOf(shown, poses.size)
            for (p in poses) {
                val L = p.scale.toDouble()
                val belly = WormField.LIFT_BL * L
                val hidden = WormField.BODY_R_BL * L
                val ups = FloatArray(48)
                val seen = BooleanArray(16)
                for (i in 0 until 16) {
                    val q = Vec3(p.spine[i * 3].toDouble(), p.spine[i * 3 + 1].toDouble(), p.spine[i * 3 + 2].toDouble())
                    val d = min(sim.rock.sdf.dist(q), q.y - sim.sand.surfaceY(q.x, q.z))
                    stations++
                    seen[i] = d > -hidden
                    if (d < belly - 0.05 && d > -hidden) {
                        clipped++; worst = min(worst, d - belly)
                        val st = sim.worms.worms.firstOrNull { it.key == p.id }?.state ?: -1
                        val k = "s$st i${i / 4}"; why[k] = (why[k] ?: 0) + 1
                    }
                    val x = p.frames[i * 4]; val y = p.frames[i * 4 + 1]; val z = p.frames[i * 4 + 2]; val w = p.frames[i * 4 + 3]
                    ups[i * 3] = 2 * (x * y - w * z); ups[i * 3 + 1] = 1 - 2 * (x * x + z * z); ups[i * 3 + 2] = 2 * (y * z + w * x)
                }
                val state = sim.worms.worms.firstOrNull { it.key == p.id }?.state ?: -1
                val caught = state == WormField.CAUGHT
                // a startled worm whips back down its hole about four times faster than it crawls, so its body
                // sweeps the rock's curvature faster too: judged separately
                val into = if (state == WormField.RETREAT) fastTurns else turns
                lastUp[p.id]?.let { prev ->
                    // (stations hidden down the hole are not seen, so only the drawn body is judged)
                    if (!caught) for (i in 0 until 16) if (seen[i]) {
                        val dot = (prev[i * 3] * ups[i * 3] + prev[i * 3 + 1] * ups[i * 3 + 1] + prev[i * 3 + 2] * ups[i * 3 + 2]).toDouble()
                        val a = Math.toDegrees(kotlin.math.acos(dot.coerceIn(-1.0, 1.0)))
                        into += a
                        if (a > 4) { val st = sim.worms.worms.firstOrNull { it.key == p.id }?.state ?: -1; val k = "s$st i${i / 4}"; bigTurn[k] = (bigTurn[k] ?: 0) + 1 }
                    }
                }
                lastUp[p.id] = ups
            }
        }
        fun p99(l: ArrayList<Double>): Double { l.sort(); return if (l.isEmpty()) 0.0 else l[(l.size * 0.99).toInt().coerceAtMost(l.size - 1)] }
        val p99 = p99(turns)
        val fastP99 = p99(fastTurns)
        println("worms: up to $shown out, $stations stations, $clipped clipped (worst %.2f cm), up-vector turn per tick p99 %.2f° (retreating %.2f°), max %.1f° clipped by $why, big turns by $bigTurn".format(worst, p99, fastP99, turns.lastOrNull() ?: 0.0))
        assertTrue("worms out", shown >= 2)
        assertTrue("stations in the surface: $clipped of $stations", clipped <= stations / 500)
        assertTrue("body rolls ${"%.2f".format(p99)}° a tick", p99 < 4.0)
        assertTrue("retreating body rolls ${"%.2f".format(fastP99)}° a tick", fastP99 < 12.0)
    }
}
