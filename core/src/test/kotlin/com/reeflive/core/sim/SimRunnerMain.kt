package com.reeflive.core.sim

import java.util.Locale

/**
 * Headless SimRunner (Track C tuning tool). Usage (from the repo root):
 *   ./gradlew -PcoreOnly :core:simRunner                      # 24 h scenario with the default stock
 *   ./gradlew -PcoreOnly :core:simRunner --args="long 90"     # 90 days health/chemistry only, daily log
 *   ./gradlew -PcoreOnly :core:simRunner --args="preset ich_outbreak 14"
 *
 * The 24 h scenario runs every hour as 2 min of full behavior (20 Hz) followed by 58 min of
 * abstract time, plus full-behavior windows around the feeds, and prints behavior mixes, hunger,
 * stress and water parameters.
 */
fun main(args: Array<String>) {
    Locale.setDefault(Locale.US)
    when (args.getOrNull(0) ?: "day") {
        "long" -> long(args.getOrNull(1)?.toInt() ?: 90, Presets.realityDefault)
        "preset" -> long(args.getOrNull(2)?.toInt() ?: 14, Presets.byId(args.getOrNull(1) ?: "reality_default"))
        else -> day()
    }
}

private fun waterLine(s: WaterState) = "T %.2f SG %.4f pH %.2f alk %.2f Ca %.0f TAN %.3f(free %.4f) NO2 %.3f NO3 %.1f PO4 %.3f DO %.2f hair %.3f AOB %.2f NOB %.2f det %.3f".format(
    s.tempC, s.sg, s.ph, s.alk, s.ca, s.nh3Total, s.nh3Free, s.no2, s.no3, s.po4, s.doMgL, s.algaeHair, s.bacteriaAob, s.bacteriaNob, s.detritus)

private fun clock(t: Double): String {
    val d = (t / 86400).toInt(); val m = ((t % 86400) / 60).toInt()
    return "d%d %02d:%02d".format(d, m / 60, m % 60)
}

private fun long(days: Int, p: Preset) {
    val sim = Presets.create(p, TestTank.catalog, TestTank.rock, startTimeSec = 0.0)
    val seen = HashSet<SimEvent>()
    println("== ${p.label}: $days days, ${sim.organisms.size} animals, ${sim.corals.size} corals")
    for (d in 0 until days) {
        repeat(24) { sim.advanceAbstract(3600.0) }
        println("${clock(sim.simTimeSec)}  ${waterLine(sim.water.s)}")
        val notable = sim.organisms.filter { it.health < 0.95 || it.hunger > 0.6 || it.stress > 0.2 || it.disease != null || !it.alive }
        for (o in notable) println("    %-12s h %.2f hunger %.2f stress %.2f imm %.2f cond %.2f %s %s %s".format(o.nickname, o.health, o.hunger, o.stress, o.immunity, o.condition, o.envStress.entries.joinToString(",") { "${it.key}=%.2f".format(it.value) },
            o.disease?.let { "$it %.2f".format(o.diseaseStage) } ?: "", if (o.alive) "" else "DEAD (${o.deathCause})"))
        for (e in sim.eventLog) if (seen.add(e)) println("    event: $e")
        for (c in sim.corals) if (c.bleach > 0.2 || c.tissue < 0.9) println("    coral %s ext %.2f bleach %.2f tissue %.2f".format(c.nickname, c.extension, c.bleach, c.tissue))
    }
}

private fun day() {
    val sim = Presets.create(Presets.realityDefault, TestTank.catalog, TestTank.rock, startTimeSec = 0.0)
    val seen = HashSet<SimEvent>()
    println("== 24 h scenario, default stock (${sim.organisms.size} animals, ${sim.corals.size} corals)")
    val windows = (0 until 24).map { it * 3600.0 } + listOf(9 * 3600.0 + 29 * 60, 17 * 3600.0 + 29 * 60)
    var t = 0.0
    for (start in windows.sorted()) {
        if (start > t) { sim.advanceAbstract(start - t); t = start }
        val len = if (start % 3600.0 == 0.0) 120.0 else 720.0
        val counts = HashMap<String, HashMap<String, Int>>()
        sim.advance(len, behaviorDt = 0.05) {
            for (o in sim.organisms) counts.getOrPut(o.nickname) { HashMap() }.merge(o.behaviorName, 1, Int::plus)
        }
        t += len
        println("\n-- ${clock(start)} +${len.toInt()} s  light %.2f  ${waterLine(sim.water.s)}".format(sim.lightIntensity))
        for (o in sim.organisms) {
            val mix = counts[o.nickname].orEmpty().entries.sortedByDescending { it.value }.joinToString(", ") { (k, v) -> "$k ${100 * v / (len / 0.05).toInt()}%" }
            println("  %-12s hunger %.2f stress %.2f hp %.2f pos (%5.1f,%4.1f,%4.1f) post %d | %s".format(o.nickname, o.hunger, o.stress, o.health, o.pos.x, o.pos.y, o.pos.z, o.posture, mix))
        }
        for (e in sim.eventLog) if (seen.add(e)) println("  event: $e")
        println("  food particles left: ${sim.food.active().size}, corals ext: " + sim.corals.joinToString(" ") { "%s=%.2f".format(it.nickname.take(6), it.extension) })
    }
}
