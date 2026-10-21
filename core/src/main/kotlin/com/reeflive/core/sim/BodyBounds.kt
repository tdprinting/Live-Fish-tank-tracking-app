package com.reeflive.core.sim

import com.reeflive.core.geom.BodySpline
import com.reeflive.core.geom.Vec3
import kotlin.math.PI
import kotlin.math.max

/**
 * Whole bodies inside the glass and on the sand (SIMULATION §24.3), run every tick before the [RockGuard]. The guards
 * before kept the head point (and, opted in, the trail line) in: a body's thickness, its tail swung round at the glass
 * or its belly on the sand went through. Here every spine station carries the animal's real section from its model
 * ([BodyProfile]), oriented by the drawn body frame, and no point of it may be past the glass or under the live sand
 * surface ([SandBed.surfaceY]).
 *
 * A body that reaches out is moved back: a rigid body (shrimp, snails) slides as a whole, a swimming trail has the
 * stations that reach out moved in and takes that shape (the tail slides along the glass, the belly rests on the sand).
 * Bodies whose behaviour owns their contact are left to it: a crawler on its seat ([CrawlerSeat] rests it on its feet),
 * an animal burrowing or buried ([SandContact.exempt]) or on a behaviour's own body path (into a burrow) for the sand.
 *
 * Allocation-free per tick apart from a corrected polyline; most animals, most ticks, are far from the glass and the sand
 * and are rejected from their head alone.
 */
class BodyBounds(
    private val sand: SandBed,
    private val lengthCm: Double,
    private val widthCm: Double,
    private val rock: com.reeflive.core.geom.Sdf? = null,
) {
    private val pos = Array(BodySpline.POINTS) { Vec3.ZERO }
    private val out = ArrayList<Vec3>(BodySpline.POINTS)
    private val fr = StationFrame()

    /** Bodies moved back inside the glass / lifted onto the sand since the start (diagnostics, tests). */
    var glassFixes = 0L; private set
    var sandFixes = 0L; private set

    fun apply(o: Organism) {
        if (!o.alive || o.seatOn || o.leap != null || o.netted) return
        val prof = BodyProfile.spineOf(o) ?: return
        val l = o.lengthCm
        val reach = prof.maxReach * l + MARGIN
        val h = o.pos
        val span = o.spline.length + h.distanceTo(o.spline.headPos) + reach
        val sandOn = !SandContact.exempt(o) && o.spline.override == null && !o.atBurrow
        val nearGlass = h.x < span || h.x > lengthCm - span || h.z < span || h.z > widthCm - span
        val nearSand = sandOn && h.y - span < sand.surfaceY(h.x, h.z) + MOUND_SLACK_CM
        if (!nearGlass && !nearSand) return
        o.spline.sampleStations(pos)
        val n = BodySpline.POINTS
        var moved = false
        var gx = false; var sy = false
        // the shift that brings the worst station back (for a rigid body, moved as a whole)
        var shX = 0.0; var shY = 0.0; var shZ = 0.0
        out.clear()
        for (i in 0 until n) {
            val p = pos[i]
            var x = p.x; var y = p.y; var z = p.z
            // (a station whose body cannot reach the glass or the sand in any direction is left alone)
            val ri = prof.reach(i) * l + MARGIN
            val glassHere = nearGlass && (x < ri || x > lengthCm - ri || z < ri || z > widthCm - ri)
            val sandHere = nearSand && y - ri < sand.surfaceY(x, z) + SIDE_SLACK_CM
            if (!glassHere && !sandHere) { out.add(p); continue }
            fr.set(o, pos, i)
            if (glassHere) {
                val lo = fr.reach(prof, i, l, -1.0, 0.0, 0.0) + MARGIN; val hi = fr.reach(prof, i, l, 1.0, 0.0, 0.0) + MARGIN
                if (x < lo) { x = lo; gx = true } else if (x > lengthCm - hi) { x = lengthCm - hi; gx = true }
                val loZ = fr.reach(prof, i, l, 0.0, 0.0, -1.0) + MARGIN; val hiZ = fr.reach(prof, i, l, 0.0, 0.0, 1.0) + MARGIN
                if (z < loZ) { z = loZ; gx = true } else if (z > widthCm - hiZ) { z = widthCm - hiZ; gx = true }
            }
            if (sandHere) {
                val down = fr.reach(prof, i, l, 0.0, -1.0, 0.0)
                // the sand under the belly's sides too (a body across a slope rests on the higher side)
                val w = prof.halfWidth(i) * l
                val wx = fr.sx * w; val wz = fr.sz * w
                val need = max(sand.surfaceY(x, z), max(sand.surfaceY(x + wx, z + wz), sand.surfaceY(x - wx, z - wz))) + down
                if (y < need) {
                    // never lifted into the rock: wedged under a rock foot (a goby at its burrow mouth) the rock wins
                    val r = rock
                    var lift = need - y
                    if (r != null) {
                        // (a station already at or in a buried rock foot stays where it is: lifting it would bring it up
                        // through the rock above the sand)
                        val keep = SandContact.ROCK_KEEP
                        if (r.distAt(x, y, z) < keep) lift = 0.0
                        else if (r.distAt(x, need, z) < keep) {
                            var lo = 0.0; var hi = lift
                            repeat(5) { val m = 0.5 * (lo + hi); if (r.distAt(x, y + m, z) >= keep) lo = m else hi = m }
                            lift = lo
                        }
                    }
                    if (lift < need - y - 1e-4) {
                        // wedged between the rock and the sand: the body pushes the sand from under itself (mass-conserving,
                        // a little each tick) instead of lying in it
                        val r0 = max(max(sand.cellX, sand.cellZ), fr.reach(prof, i, l, fr.sx, fr.sy, fr.sz))
                        sand.bowWave(x, z, o.heading.x, o.heading.z, r0, (need - y - lift) * PI * r0 * r0 * 0.6)
                    }
                    if (lift > 1e-4) { y += lift; sy = true }
                }
            }
            if (x != p.x || y != p.y || z != p.z) {
                moved = true
                if (abs(x - p.x) > abs(shX)) shX = x - p.x
                if (y - p.y > shY) shY = y - p.y
                if (abs(z - p.z) > abs(shZ)) shZ = z - p.z
                out.add(Vec3(x, y, z))
            } else out.add(p)
        }
        if (!moved) return
        if (gx) glassFixes++
        if (sy) sandFixes++
        if (o.spline.rigid || o.spline.override != null) {
            // a rigid body (or one drawn on a behaviour's path) moves back as a whole, keeping its shape
            val s = Vec3(shX, shY, shZ)
            o.pos = o.pos + s
            o.spline.update(o.pos, o.heading, 0.0, 0.0)
            if (s.y > 0.0 && o.vel.y < 0.0) o.vel = o.vel.withY(0.0)
            return
        }
        // a trail: the head where its station went, the rest of the body takes the moved shape
        val head = out[0]
        if (head != pos[0]) {
            o.pos = o.pos + (head - pos[0])
            if (head.y > pos[0].y && o.vel.y < 0.0) o.vel = o.vel.withY(0.0)
        }
        o.spline.adopt(out)
        o.spline.update(o.pos, o.heading, 0.0, 0.0)
    }

    private fun abs(v: Double) = if (v < 0.0) -v else v

    companion object {
        /** Clearance (cm) kept between the body and the glass. */
        const val MARGIN = 0.1
        /** How much higher (cm) the sand under a belly's side may be than under its middle (slopes, mounds). */
        const val SIDE_SLACK_CM = 1.0
        /** How much higher (cm) the sand under any station may be than under the head (mounds, burrow spoil). */
        const val MOUND_SLACK_CM = 6.0
    }
}
