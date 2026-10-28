package com.reeflive.core.sim

import org.junit.Test
import java.io.File
import java.util.Locale

/**
 * Headless behaviour QA harness (tools/qa/README.md): runs the default tank the way the web page drives it
 * ([Simulation.stepWall] at 20 Hz wall) and writes one CSV row per animal per sample, for tools/qa/bqa/metrics.py,
 * cmp.py and htab.py. Not a check: it is left out of the normal test run and only runs with `-Pbqa`:
 *
 *   ./gradlew -PcoreOnly -Pbqa -PbqaOut=/tmp/run.csv -PbqaSpeed=60 -PbqaMinutes=360 -PbqaStartHour=8 :core:test --tests '*BehaviourQa'
 *
 * Knobs (all optional): -PbqaOut (default core/build/bqa.csv), -PbqaSpeed (1), -PbqaMinutes of sim time (60),
 * -PbqaStartHour (8), -PbqaEvery sample every N ticks (1), -PbqaSeed (1).
 */
class BehaviourQa {
    private fun prop(k: String, d: String) = System.getProperty("reeflive.bqa.$k") ?: d

    @Test
    fun run() {
        val out = File(prop("out", "build/bqa.csv")).also { it.absoluteFile.parentFile?.mkdirs() }
        bqaMain(arrayOf(out.path, prop("speed", "1"), prop("minutes", "60"), prop("startHour", "8"), prop("every", "1"), prop("seed", "1")))
    }
}

/** args: out.csv speed minutesSim startHour [sampleEveryTicks] [seed] — emulates the web stepWall loop at 20 Hz wall. */
private fun bqaMain(a: Array<String>) {
    Locale.setDefault(Locale.US)
    val out = File(a[0]); val speed = a[1].toDouble(); val simMin = a[2].toDouble(); val startH = a[3].toDouble()
    val every = a.getOrNull(4)?.toInt() ?: 1
    val seed = a.getOrNull(5)?.toLong() ?: 1L
    val ids = (DefaultStock.all.map { it.speciesId } + Stocking.CATALOG.map { it.speciesId }).distinct()
    val species = SpeciesCatalog.fromClasspath(ids)
    val rock = TestTank.rock
    val config = TankConfig(seed = seed)
    val s = Simulation(config = config, species = species, rock = rock, startTimeSec = startH * 3600, initialWater = WaterState(volumeL = config.systemLiters), livestock = DefaultStock.named)
    s.speed = speed
    s.stepWall(0.0)
    val w = out.bufferedWriter()
    w.write("t,id,name,sp,kind,x,y,z,hx,hy,hz,spd,beh,cap,post,sleep,att,inSand,leap,seat,sx,sy,sz,sandY,rockD,hunger,health,stress,dtmin,disp\n")
    val total = simMin * 60
    var tick = 0L
    val t0 = s.simTimeSec
    while (s.simTimeSec - t0 < total) {
        s.stepWall(0.05); tick++
        if (tick % every != 0L) continue
        val caps = s.latest.stats.associate { it.id to it.caption }
        val disp = s.latest.stats.associate { it.id to it.behavior }
        for (o in s.organisms) {
            if (!o.alive) continue
            val p = if (o.seatOn) o.seatPos else o.pos
            val sy = s.sand.surfaceY(p.x, p.z)
            w.write("%.2f,%s,%s,%s,%s,%.2f,%.2f,%.2f,%.3f,%.3f,%.3f,%.2f,%s,%s,%d,%d,%d,%d,%d,%d,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.3f,%s\n".format(
                s.simTimeSec - t0, o.id, o.nickname.replace(',', ' '), o.species.id, o.brainKind.name, o.pos.x, o.pos.y, o.pos.z,
                o.heading.x, o.heading.y, o.heading.z, o.vel.length(), o.behaviorName.replace(',', ';'), (caps[o.id] ?: "").replace(',', ';'),
                o.posture, if (o.sleeping) 1 else 0, if (o.attached) 1 else 0, if (o.inSand) 1 else 0, if (o.leap != null) 1 else 0, if (o.seatOn) 1 else 0,
                o.seatPos.x, o.seatPos.y, o.seatPos.z, sy, rock.sdf.distAt(p.x, p.y, p.z), o.hunger, o.health, o.stress, o.lengthCm, (disp[o.id] ?: "").replace(',', ';')))
        }
    }
    w.close()
    println("BehaviourQa: done ${s.organisms.size} animals, t=${s.simTimeSec - t0}")
}
