package com.reeflive.core.geom

import kotlin.math.exp

/**
 * Body spline shared by sim and renderer (SIMULATION §4.8).
 *
 * The organism keeps a trail of its head positions, recorded every `BL/16` cm of travel, so the
 * body occupies the last `BL` of trail. [sample] returns [POINTS] spine points at arclength
 * `i × BL/15` behind the head plus one frame (quaternion) per point. When the fish hovers or turns
 * in place the trail relaxes toward a straight line behind the heading (rate passed by Steering).
 *
 * `rigid = true` (shrimp, crabs, snails) skips the trail: the body is always straight behind the
 * heading. An [override] polyline (eel in its den, blenny in its hole) replaces the trail.
 */
class BodySpline(length: Double, val rigid: Boolean = false) {
    var length: Double = length
        set(v) { field = v.coerceAtLeast(0.1) }

    private var head = Vec3.ZERO
    private var heading = Vec3.X
    private val trail = ArrayList<Vec3>(48)   // trail[0] = most recent point behind the head

    /**
     * Head→tail polyline that replaces the trail while set (den / hole paths). It is drawn from the current head
     * (translated so its first point sits on the head) and blended in and out over ~1/[OVERRIDE_BLEND_RATE] s, so
     * sliding into a hole or leaving it never pops the body shape or snaps the head to the path's end.
     */
    var override: List<Vec3>? = null
    /** 0 = trail shape, 1 = override shape; eased in [update]. */
    private var overrideMix = 0.0
    /** The override being blended out after it was cleared. */
    private var lastOverride: List<Vec3>? = null
    private val scratch = Array(POINTS) { Vec3.ZERO }

    fun reset(headPos: Vec3, headingDir: Vec3) {
        head = headPos; heading = headingDir.normalized()
        trail.clear()
        val step = length / 16
        for (i in 1..17) trail.add(head - heading * (step * i))
        // a placement is a cut: take the current override shape at once
        overrideMix = if (override != null) 1.0 else 0.0
        lastOverride = override
    }

    fun update(headPos: Vec3, headingDir: Vec3, dt: Double, relaxRate: Double) {
        if (!(headPos.x.isFinite() && headPos.y.isFinite() && headPos.z.isFinite())) return
        head = headPos
        heading = headingDir.normalized(heading)
        // ease the body into and out of an override path instead of switching shape in one tick
        override?.let { if (it.isNotEmpty()) lastOverride = it }
        val target = if (override != null) 1.0 else 0.0
        overrideMix += (target - overrideMix) * (1 - exp(-OVERRIDE_BLEND_RATE * dt.coerceAtLeast(0.0)))
        if (kotlin.math.abs(target - overrideMix) < 1e-3) overrideMix = target
        if (overrideMix == 0.0) lastOverride = null
        if (rigid) return
        val step = length / 16
        if (trail.isEmpty()) { reset(headPos, headingDir); return }
        // Record a new trail point whenever the head has travelled one step from the last one.
        var d = head.distanceTo(trail[0])
        var guard = 0
        if (d > 2 * length) { reset(headPos, headingDir); return }
        while (d >= step && guard++ < 64) {
            val dir = (head - trail[0]) / d
            trail.add(0, trail[0] + dir * step)
            d -= step
        }
        // Relax toward the straight line behind the heading.
        if (relaxRate > 0) {
            val k = 1 - exp(-relaxRate * dt)
            var s = head.distanceTo(trail[0])
            var prev = trail[0]
            for (i in trail.indices) {
                if (i > 0) { s += trail[i].distanceTo(prev); prev = trail[i] }
                val ideal = head - heading * s
                trail[i] = trail[i] + (ideal - trail[i]) * k
            }
        }
        // Trim beyond 1.25 BL.
        var acc = head.distanceTo(trail[0])
        var keep = trail.size
        for (i in 1 until trail.size) {
            acc += trail[i].distanceTo(trail[i - 1])
            if (acc > length * 1.25) { keep = i + 1; break }
        }
        // Hovering fish can pile up near-coincident points that never reach 1.25 BL of arclength; cap the
        // trail (20 points cover 1.25 BL) so the relax loop stays O(1) per tick.
        keep = minOf(keep, MAX_TRAIL)
        while (trail.size > keep) trail.removeAt(trail.size - 1)
    }

    /**
     * Fills [pos] (size ≥ POINTS) with spine points head→tail and [frames] with body frames
     * given the organism [up] vector (roll/posture already applied by Steering).
     */
    fun sample(up: Vec3, pos: Array<Vec3>, frames: Array<Quat>) {
        val seg = length / (POINTS - 1)
        val ov = override ?: lastOverride
        val mix = if (ov == null || ov.isEmpty()) 0.0 else overrideMix
        if (mix < 1.0) {
            if (rigid) for (i in 0 until POINTS) pos[i] = head - heading * (seg * i)
            else walk(trail, 0, head, seg, pos)
        }
        if (mix > 0.0) {
            // the path starts at the head: shift it so its first point coincides with the current head
            val shift = head - ov!!.first()
            val shifted = if (shift.lengthSq() < 1e-12) ov else ov.map { it + shift }
            val dst = if (mix >= 1.0) pos else scratch
            walk(shifted, 1, head, seg, dst)
            if (mix < 1.0) for (i in 0 until POINTS) pos[i] = pos[i].lerp(scratch[i], mix)
        }
        for (i in 0 until POINTS) {
            val fwd = when (i) {
                0 -> pos[0] - pos[1]
                POINTS - 1 -> pos[POINTS - 2] - pos[POINTS - 1]
                else -> pos[i - 1] - pos[i + 1]
            }.normalized(heading)
            frames[i] = Quat.fromForwardUp(fwd, up)
        }
    }

    /** Stations every [seg] cm along head → [poly][startIdx..], extended straight past its end. */
    private fun walk(poly: List<Vec3>, startIdx: Int, startHead: Vec3, seg: Double, out: Array<Vec3>) {
        out[0] = startHead
        var prev = startHead
        var j = startIdx
        var lastDir = heading * -1.0
        for (i in 1 until POINTS) {
            var remaining = seg
            var cur = prev
            while (true) {
                if (j >= poly.size) { cur = cur + lastDir * remaining; break }
                val nxt = poly[j]
                val d = cur.distanceTo(nxt)
                if (d >= remaining && d > 1e-9) {
                    val dir = (nxt - cur) / d
                    lastDir = dir
                    cur = cur + dir * remaining
                    break
                }
                if (d > 1e-9) lastDir = (nxt - cur) / d
                remaining -= d
                cur = nxt
                j++
            }
            out[i] = cur
            prev = cur
        }
    }

    /** Current trail as head→tail polyline (for tests/debug). */
    fun trailSnapshot(): List<Vec3> = listOf(head) + trail

    companion object {
        const val POINTS = 16
        const val MAX_TRAIL = 48
        /** Rate (1/s) at which the body eases into or out of an [override] path. */
        const val OVERRIDE_BLEND_RATE = 6.0
    }
}
