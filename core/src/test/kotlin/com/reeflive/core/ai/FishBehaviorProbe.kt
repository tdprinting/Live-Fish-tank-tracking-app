package com.reeflive.core.ai

import com.reeflive.core.geom.Vec3
import com.reeflive.core.sim.DefaultStock
import com.reeflive.core.sim.Organism
import com.reeflive.core.sim.Simulation
import com.reeflive.core.sim.TestTank
import org.junit.Test
import java.io.File

/**
 * Not an assertion test: a movement report for every fish (speed, stop-go bouts, turning, depth, rock distance,
 * ground covered, behaviour time budget). Run on demand with REEFLIVE_PROBE=1; writes build/fish_probe.txt.
 */
class FishBehaviorProbe {
    private class Acc(val o: Organism) {
        var n = 0; var still = 0; var sumBl = 0.0; val bl = ArrayList<Double>()
        var moving = false; var flips = 0; var turnSum = 0.0; var lastHead: Vec3? = null
        var ySum = 0.0; var y2 = 0.0; var rockSum = 0.0; var rockN = 0
        val cells = HashSet<Int>(); val beh = HashMap<String, Int>(); var attached = 0
    }

    @Test
    fun probe() {
        if (System.getenv("REEFLIVE_PROBE") != "1") return
        val out = StringBuilder()
        for ((label, hour, secs) in listOf(Triple("DAY 13:00", 13.0, 1800.0), Triple("NIGHT 23:00", 23.0, 900.0))) {
            val sim = TestTank.sim(DefaultStock.all, seed = 7, startHour = hour)
            sim.advance(120.0, behaviorDt = 0.05)
            val accs = sim.organisms.filter { it.isFish }.map { Acc(it) }
            val dt = 0.05
            var tick = 0
            val t0 = System.nanoTime()
            sim.advance(secs, behaviorDt = dt) {
                tick++
                for (a in accs) sample(sim, a, dt, tick % 20 == 0)
            }
            val ms = (System.nanoTime() - t0) / 1e6
            out.append("== $label (${secs.toInt()} s, ${"%.0f".format(ms)} ms wall)\n")
            out.append(String.format("%-22s %5s %5s %5s %5s %6s %6s %5s %5s %6s %5s  %s\n",
                "fish", "mBL", "p50", "p90", "still", "bout/m", "turn°s", "yAvg", "ySd", "rockcm", "area", "behaviours"))
            for (a in accs) {
                val s = a.bl.sorted()
                val top = a.beh.entries.sortedByDescending { it.value }.take(5)
                    .joinToString(", ") { "${it.key} ${100 * it.value / a.n}%" }
                val ym = a.ySum / a.n
                out.append(String.format("%-22s %5.2f %5.2f %5.2f %4d%% %6.1f %6.1f %5.1f %5.1f %6.1f %5d  %s\n",
                    a.o.nickname.take(12) + "/" + a.o.brainKind.name.take(9), a.sumBl / a.n, s[s.size / 2], s[s.size * 9 / 10],
                    100 * a.still / a.n, a.flips / (secs / 60), a.turnSum / secs, ym,
                    kotlin.math.sqrt(maxOf(0.0, a.y2 / a.n - ym * ym)), if (a.rockN > 0) a.rockSum / a.rockN else -1.0,
                    a.cells.size * 16, top))
            }
        }
        File("build/fish_probe.txt").writeText(out.toString())
    }

    private fun sample(sim: Simulation, a: Acc, dt: Double, slow: Boolean) {
        val o = a.o
        if (!o.alive) return
        a.n++
        val bl = o.vel.length() / o.lengthCm
        a.sumBl += bl
        if (slow) a.bl.add(bl)
        if (bl < 0.15) a.still++
        val mv = if (a.moving) bl > 0.2 else bl > 0.4
        if (mv != a.moving) { a.flips++; a.moving = mv }
        a.lastHead?.let { a.turnSum += Math.toDegrees(Vec3.angle(it, o.heading)) }
        a.lastHead = o.heading
        a.ySum += o.pos.y; a.y2 += o.pos.y * o.pos.y
        if (o.attached) a.attached++
        val name = (o.brain?.current?.name ?: "-").let { when (it) { "keepclear" -> "keepclear(" + (o.wary?.nickname ?: "?") + ")"; "pose" -> "pose(" + (o.poseFor?.nickname ?: "?") + ")"; else -> it } }
        a.beh[name] = (a.beh[name] ?: 0) + 1
        if (slow) {
            a.rockSum += sim.rock.sdf.dist(o.pos); a.rockN++
            a.cells.add((o.pos.x / 4).toInt() * 1000 + (o.pos.z / 4).toInt())
        }
    }
}
