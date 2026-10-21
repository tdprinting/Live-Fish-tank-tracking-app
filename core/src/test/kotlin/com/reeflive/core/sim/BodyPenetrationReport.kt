package com.reeflive.core.sim

import com.reeflive.core.geom.BodySpline
import com.reeflive.core.geom.Quat
import com.reeflive.core.geom.Vec3
import kotlin.math.max

/**
 * Measures how far the drawn bodies (the snapshot poses with each species' mesh profile, [BodyProfile]) reach into the
 * rock, the sand and the glass (SIMULATION §24.3). Shared by the property test and the per-species report.
 */
class BodyPenetrationReport(private val sim: Simulation) {
    class Worst { var cm = 0.0; var where = ""; var count = 0L }
    val rock = HashMap<String, Worst>()
    val sand = HashMap<String, Worst>()
    val glass = HashMap<String, Worst>()
    var checks = 0L; private set

    private fun note(m: HashMap<String, Worst>, key: String, cm: Double, where: () -> String) {
        if (cm <= TOL_CM) return
        val w = m.getOrPut(key) { Worst() }
        w.count++
        if (cm > w.cm) { w.cm = cm; w.where = where() }
    }

    fun sample() {
        val snap = sim.snapshot()
        val byId = sim.organisms.associateBy { it.id }
        val r = sim.rock
        for (p in snap.organisms) {
            val o = byId[p.id] ?: continue
            if (!o.alive || !p.visible || o.leap != null || o.netted) continue
            if (p.posture == 3 || p.posture == 5) continue   // in a hole or den, buried: their behaviours own the contact
            val prof = BodyProfile.of(o.species) ?: continue
            // crawlers on their seats and rigid shells are their seats' and rigs' (reported apart); a goby at its
            // burrow is in the sand pocket it dug, and burrowing animals are in the sand by design
            val seated = o.seatOn || o.drawnRigid || (o.crawling && !o.isFish)
            val key = o.species.id + if (seated) " [seat]" else ""
            val sandOk = SandContact.exempt(o) || o.atBurrow || o.spline.override != null
            val rockOk = o.atBurrow
            val l = p.scale.toDouble()
            val rigid = p.motionMode in RIGID_MODES
            val pts = ArrayList<Vec3>(64)
            val src = ArrayList<Int>(64)
            if (rigid && prof.hull.isNotEmpty()) {
                val q = quat(p.frames, 0); val s0 = vec(p.spine, 0)
                for (k in 0 until prof.hull.size / 3)
                    { src += -1; pts += s0 + q.rotate(Vec3(prof.hull[k * 3].toDouble(), prof.hull[k * 3 + 1].toDouble(), prof.hull[k * 3 + 2].toDouble()) * l) }
            } else {
                for (i in 0 until BodySpline.POINTS) {
                    val c = vec(p.spine, i); val q = quat(p.frames, i)
                    val up = q.rotate(Vec3.Y); val side = q.rotate(Vec3.Z)
                    // the section's extreme points toward the nearest rock, down and toward the four glass panes
                    val n = r.sdf.gradient(c).normalized(Vec3.Y)
                    for (v in arrayOf(n * -1.0, Vec3.Y * -1.0, Vec3.X, Vec3.X * -1.0, Vec3.Z, Vec3.Z * -1.0))
                    { src += i; pts += c + v * (prof.support(i, up dot v, side dot v) * l) }
                }
            }
            val br = RockHole.bodyRadius(o)
            for ((ei, e) in pts.withIndex()) {
                val si = src[ei]; val sc = if (si >= 0) vec(p.spine, si) else null
                checks++
                val sy = sim.sand.surfaceY(e.x, e.z)
                val d = r.sdf.dist(e)
                // inside rock above the sand, outside a hole that fits it
                if (!rockOk && d < -TOL_CM && e.y > sy && !r.holes.inFittingHole(e, br))
                    note(rock, key, -d) { "t=${"%.0f".format(sim.simTimeSec)} ${o.behaviorName}/p${p.posture} at $e st $si c $sc L $l" }
                if (!sandOk && d > 0.0) note(sand, key, sy - e.y) { "t=${"%.0f".format(sim.simTimeSec)} ${o.behaviorName}/p${p.posture} at $e st $si c $sc L $l" }
                val g = max(max(-e.x, e.x - r.lengthCm), max(-e.z, e.z - r.widthCm))
                note(glass, key, g) { "t=${"%.0f".format(sim.simTimeSec)} ${o.behaviorName}/p${p.posture} at $e st $si c $sc L $l" }
            }
        }
    }

    /** Worst depth (cm) over the bodies that are not on a seat. */
    fun worst(m: HashMap<String, Worst>): Pair<String, Worst>? = m.entries.filter { !it.key.endsWith("[seat]") }.maxByOrNull { it.value.cm }?.let { it.key to it.value }

    fun summary(): String = buildString {
        for ((name, m) in listOf("rock" to rock, "sand" to sand, "glass" to glass)) {
            appendLine("== $name")
            for ((k, w) in m.entries.sortedByDescending { it.value.cm })
                appendLine("  $k worst ${"%.2f".format(w.cm)} cm, ${w.count} points  (${w.where})")
        }
        append("checked $checks points")
    }

    companion object {
        /** The baked rock field is a 1 cm grid read trilinearly: closer than this counts as touching (cm). */
        const val TOL_CM = 0.3
        /** Pose motion modes drawn rigid at the organism (fish.vert): arm-rowing disc, crab, hermit, scallop, snail, urchin. */
        val RIGID_MODES = setOf(1, 3, 5, 7, 8, 9)

        private fun vec(a: FloatArray, i: Int) = Vec3(a[i * 3].toDouble(), a[i * 3 + 1].toDouble(), a[i * 3 + 2].toDouble())
        private fun quat(a: FloatArray, i: Int) = Quat(a[i * 4].toDouble(), a[i * 4 + 1].toDouble(), a[i * 4 + 2].toDouble(), a[i * 4 + 3].toDouble())
    }
}
