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
 * and rock checks, so a body pushed toward the rock is still kept out of it, and once more after them ([settle]).
 *
 * A fish perched or resting where its behaviour placed it, on its own path (hole, den, burrow) or at its burrow holds its
 * place and the other gives way (shoving a fish grazing against the rock pushed it into the rock);
 * crawlers on their seats and rigid shells keep their own rules. Allocation-free unless two bodies touch.
 */
class BodyContact {
    private class Body {
        var o: Organism? = null
        var prof: BodyProfile? = null
        var l = 0.0
        var m = 0.0
        var reach = 0.0
        /** Perched, resting, on a behaviour's own path or at its burrow: it holds its place and the other gives way. */
        var anchored = false
        /** Kept off the rock or the glass this tick ([pin]): in the second pass it does not move at all. */
        var pinned = false
        var moved = false
        val pos = Array(BodySpline.POINTS) { Vec3.ZERO }
    }

    private val bodies = ArrayList<Body>()
    private var n = 0
    private val fa = StationFrame()
    private val fb = StationFrame()

    /** Pairs pushed apart since the start (diagnostics, tests). */
    var pushes = 0L; private set

    /** Bodies the rock guard moved this tick, after the first pass ([settle]). */
    private val pins = HashSet<Organism>()
    /** Bodies the second pass moved: the simulation keeps them off the rock, sand and glass again. */
    val moved = ArrayList<Organism>()

    /** [o] was just kept out of the rock: a body it was pushed into must give way to it ([settle]). */
    fun pin(o: Organism) { pins.add(o) }

    /** First pass, after steering: every two bodies that touch are pushed apart. */
    fun step(organisms: List<Organism>) = run(organisms, false)

    /**
     * Second pass, after the rock and glass checks: a body the rock guard pushed back out of the rock into another
     * (a clown pair in a host against the rock) holds there and the other gives way, instead of being pushed back in.
     */
    fun settle(organisms: List<Organism>) {
        moved.clear()
        if (pins.isEmpty()) return
        run(organisms, true)
        pins.clear()
    }

    private fun run(organisms: List<Organism>, second: Boolean) {
        n = 0
        for (o in organisms) {
            if (!o.alive || !o.visible || o.leap != null || o.netted || o.heldBy != null || o.seatOn) continue
            if (o.posture == 3 || o.posture == 5) continue
            val prof = BodyProfile.spineOf(o) ?: continue
            if (n == bodies.size) bodies.add(Body())
            val b = bodies[n++]
            b.o = o; b.prof = prof; b.l = o.lengthCm; b.m = o.massG
            b.anchored = o.spline.override != null || o.atBurrow || o.attached
            b.reach = o.spline.length + o.pos.distanceTo(o.spline.headPos) + prof.maxReach * o.lengthCm
            b.pinned = second && o in pins
            b.moved = false
            o.spline.sampleStations(b.pos)
        }
        for (pass in 0 until PASSES) {
            var any = false
            for (i in 0 until n) for (j in i + 1 until n) {
                val a = bodies[i]; val b = bodies[j]
                // the second pass only settles a pinned body against one that is free to move
                if (second && a.pinned == b.pinned) continue
                if (resolve(a, b)) any = true
            }
            if (!any) break
        }
        for (k in 0 until n) { val b = bodies[k]; if (b.moved) moved.add(b.o!!); b.o = null }
    }

    /** Pushes [a] and [b] apart if their bodies overlap; true if it did. */
    private fun resolve(a: Body, b: Body): Boolean {
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
        // (two that both hold their place, a clown pair nestled in their host, share it by weight as two swimmers do)
        val wa = if (a.pinned) 0.0 else if (b.pinned) 1.0
            else if (a.anchored == b.anchored) b.m / (a.m + b.m) else if (a.anchored) 0.0 else 1.0
        if (wa > 0) move(a, -bx * push * wa, -by * push * wa, -bz * push * wa)
        if (wa < 1) move(b, bx * push * (1 - wa), by * push * (1 - wa), bz * push * (1 - wa))
        pushes++
        return true
    }

    private fun move(b: Body, dx: Double, dy: Double, dz: Double) {
        val o = b.o!!
        b.moved = true
        o.pos = Vec3(o.pos.x + dx, o.pos.y + dy, o.pos.z + dz)
        o.spline.translate(dx, dy, dz)
        for (k in b.pos.indices) b.pos[k] = Vec3(b.pos[k].x + dx, b.pos[k].y + dy, b.pos[k].z + dz)
    }

    companion object {
        /** Overlap (cm) read as touching: the baked sections are a mesh's envelope, fins at half give. */
        const val TOL_CM = 0.15
        /** Most a body is pushed per pair per pass (cm): a deep overlap clears over the passes instead of popping. */
        const val MAX_PUSH_CM = 1.0
        /** Passes over the pairs per tick (a body pushed into a third; a clown swinging its head 3 cm into its mate). */
        const val PASSES = 8
    }
}
