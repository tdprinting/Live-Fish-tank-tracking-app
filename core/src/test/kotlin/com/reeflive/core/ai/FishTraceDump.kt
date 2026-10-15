package com.reeflive.core.ai

import com.reeflive.core.geom.Vec3
import com.reeflive.core.sim.DefaultStock
import com.reeflive.core.sim.TestTank
import org.junit.Test
import java.io.File

/**
 * Not an assertion test: dumps every fish's path (5 Hz) and a rock height map for the top-down movement previews.
 * Run on demand with REEFLIVE_TRACE=<out dir>; optional REEFLIVE_TRACE_HOUR (default 13) and REEFLIVE_TRACE_S (180).
 */
class FishTraceDump {
    @Test
    fun dump() {
        val dir = System.getenv("REEFLIVE_TRACE") ?: return
        val hour = System.getenv("REEFLIVE_TRACE_HOUR")?.toDouble() ?: 13.0
        val secs = System.getenv("REEFLIVE_TRACE_S")?.toDouble() ?: 180.0
        val sim = TestTank.sim(DefaultStock.all, seed = 7, startHour = hour)
        sim.advance(1200.0, behaviorDt = 0.05)
        val fish = sim.organisms.filter { it.isFish }
        val sb = StringBuilder("t,name,kind,x,y,z,hx,hy,hz,label\n")
        var t = 0.0
        var n = 0
        sim.advance(secs, behaviorDt = 0.05) {
            t += 0.05
            if (++n % 4 == 0) for (o in fish) {
                if (!o.alive) continue
                val lab = (if (o.visible) o.brain?.currentLabel ?: "" else "(hidden) " + (o.brain?.currentLabel ?: "")).replace(',', ' ')
                sb.append("%.2f,%s,%s,%.2f,%.2f,%.2f,%.3f,%.3f,%.3f,%s\n".format(t, o.nickname, o.brainKind.name, o.pos.x, o.pos.y, o.pos.z,
                    o.heading.x, o.heading.y, o.heading.z, lab))
            }
        }
        File(dir).mkdirs()
        File(dir, "trace.csv").writeText(sb.toString())
        // rock top height (cm) on a 1 cm grid, and front-view rock silhouette (max over z), for the backdrop
        val r = sim.rock
        val hm = StringBuilder()
        var z = 0.5
        while (z < r.widthCm) {
            var x = 0.5
            val row = ArrayList<String>()
            while (x < r.lengthCm) {
                var y = r.waterLevelCm - 1.0
                var top = 0.0
                while (y > r.sandTopCm) { if (r.sdf.dist(Vec3(x, y, z)) < 0.0) { top = y; break }; y -= 0.5 }
                row.add("%.1f".format(top)); x += 1.0
            }
            hm.append(row.joinToString(",")).append('\n'); z += 1.0
        }
        File(dir, "rock_top.csv").writeText(hm.toString())
        File(dir, "dims.txt").writeText("${r.lengthCm} ${r.widthCm} ${r.sandTopCm} ${r.waterLevelCm}\n")
    }
}
