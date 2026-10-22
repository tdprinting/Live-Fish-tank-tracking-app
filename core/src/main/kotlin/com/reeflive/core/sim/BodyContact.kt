package com.reeflive.core.sim

import com.reeflive.core.geom.BodySpline
import com.reeflive.core.geom.Vec3
import kotlin.math.min
import kotlin.math.sqrt

/**
 * No body goes through another (SIMULATION §24.3): after steering each tick, any two swimming bodies whose drawn
 * sections ([BodyProfile] on the spine stations) overlap are pushed apart along the line between the two stations that
 * overlap most, the lighter body moving more. Steering's separation keeps most fish apart; this catches the bodies a
 * behaviour brings into contact (a cleaner at its client, a clown pair in their host). It runs before the sand, glass
 * and rock checks, so a body pushed toward the rock is still kept out of it.
 *
 * A body on a behaviour's own path (hole, den, burrow) or a goby at its burrow holds its place and the other gives way;
 * crawlers on their seats and rigid shells keep their own rules. Allocation-free unless two bodies touch.
 */
class BodyContact {
    private class Body {
        var o: Organism? = null
        var prof: BodyProfile? = null
        var l = 0.0
        var m = 0.0
        var reach = 0.0
        /** On a behaviour's own path or at its burrow: it holds its place and the other body gives way. */
        var anchored = false
        val pos = Array(BodySpline.POINTS) { Vec3.ZERO }
    }

    private val bodies = ArrayList<Body>()
    private var n = 0
    private val fa = StationFrame()
    private val fb = StationFrame()

    /** Pairs pushed apart since the start (diagnostics, tests). */
    var pushes = 0L; private set

    fun step(organisms: List<Organism>) {
        n = 0
        for (o in organisms) {
            if (!o.alive || !o.visible || o.leap != null || o.netted || o.heldBy != null || o.seatOn) continue
            if (o.posture == 3 || o.posture == 5) continue
            val prof = BodyProfile.spineOf(o) ?: continue
            if (n == bodies.size) bodies.add(Body())
            val b = bodies[n++]
            b.o = o; b.prof = prof; b.l = o.lengthCm; b.m = o.massG
            b.anchored = o.spline.override != null || o.atBurrow
            b.reach = o.spline.length + o.pos.distanceTo(o.spline.headPos) + prof.maxReach * o.lengthCm
            o.spline.sampleStations(b.pos)
        }
        for (pass in 0 until PASSES) {
            var moved = false
            for (i in 0 until n) for (j in i + 1 until n) if (resolve(bodies[i], bodies[j])) moved = true
            if (!moved) break
        }
        for (k in 0 until n) bodies[k].o = null
    }

    /** Pushes [a] and [b] apart if their bodies overlap; true if it did. */
    private fun resolve(a: Body, b: Body): Boolean {
        if (a.anchored && b.anchored) return false
        val oa = a.o!!; val ob = b.o!!
        val pa = a.prof!!; val pb = b.prof!!
        val ha = oa.pos; val hb = ob.pos
        val dx0 = ha.x - hb.x; val dy0 = ha.y - hb.y; val dz0 = ha.z - hb.z
        val far = a.reach + b.reach
        if (dx0 * dx0 + dy0 * dy0 + dz0 * dz0 > far * far) return false
        var best = TOL_CM; var bx = 0.0; var by = 0.0; var bz = 0.0
        for (i in 0 until BodySpline.POINTS) {
            val ci = a.pos[i]
            val ri = pa.reach(i) * a.l
            var framed = false
            for (j in 0 until BodySpline.POINTS) {
                val cj = b.pos[j]
                val dx = cj.x - ci.x; val dy = cj.y - ci.y; val dz = cj.z - ci.z
                val rr = ri + pb.reach(j) * b.l
                val d2 = dx * dx + dy * dy + dz * dz
                if (d2 > rr * rr) continue
                val d = sqrt(d2)
                if (d < 1e-6) continue
                val vx = dx / d; val vy = dy / d; val vz = dz / d
                if (!framed) { fa.set(oa, a.pos, i); framed = true }
                fb.set(ob, b.pos, j)
                val ov = fa.reach(pa, i, a.l, vx, vy, vz) + fb.reach(pb, j, b.l, -vx, -vy, -vz) - d
                if (ov > best) { best = ov; bx = vx; by = vy; bz = vz }
            }
        }
        if (best <= TOL_CM) return false
        // the lighter body gives way more (a goby nudged by a tang, a clown pair sharing the push)
        val push = min(best - TOL_CM * 0.5, MAX_PUSH_CM)
        val wa = if (a.anchored) 0.0 else if (b.anchored) 1.0 else b.m / (a.m + b.m)
        if (wa > 0) move(a, -bx * push * wa, -by * push * wa, -bz * push * wa)
        if (wa < 1) move(b, bx * push * (1 - wa), by * push * (1 - wa), bz * push * (1 - wa))
        pushes++
        return true
    }

    private fun move(b: Body, dx: Double, dy: Double, dz: Double) {
        val o = b.o!!
        o.pos = Vec3(o.pos.x + dx, o.pos.y + dy, o.pos.z + dz)
        o.spline.translate(dx, dy, dz)
        for (k in b.pos.indices) b.pos[k] = Vec3(b.pos[k].x + dx, b.pos[k].y + dy, b.pos[k].z + dz)
    }

    companion object {
        /** Overlap (cm) read as touching: the baked sections are a mesh's envelope, fins at half give. */
        const val TOL_CM = 0.15
        /** Most a body is pushed per pair per tick (cm): a deep overlap clears over a few ticks instead of popping. */
        const val MAX_PUSH_CM = 1.0
        /** Passes over the pairs per tick (a body pushed into a third). */
        const val PASSES = 2
    }
}
