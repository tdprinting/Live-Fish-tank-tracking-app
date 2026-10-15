package com.reeflive.core.geom

import kotlin.math.exp
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

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
        // Fully in an override path, the trail follows the drawn body: the head's own track (into a tunnel, a den, under
        // a ledge) is not where the body lies, and when the override is let go the body must ease out of the shape it
        // was drawn in, not out of a track through the sand (a goby leaving its burrow had its middle 1.7 cm under the
        // sand mound at the mouth, on the line from its dig face to the open water).
        val ov = override
        if (ov != null && ov.size > 1 && overrideMix >= 1.0) {
            val shift = head - ov.first()
            walk(ov, 1, head, length / (POINTS - 1), scratch, shift)
            for (i in 1 until POINTS) { if (i - 1 < trail.size) trail[i - 1] = scratch[i] else trail.add(scratch[i]) }
            while (trail.size > POINTS - 1) trail.removeAt(trail.size - 1)
        }
    }

    /**
     * Fills [pos] (size ≥ POINTS) with spine points head→tail and [frames] with body frames
     * given the organism [up] vector (roll/posture already applied by Steering).
     */
    fun sample(up: Vec3, pos: Array<Vec3>, frames: Array<Quat>) {
        samplePositions(pos)
        for (i in 0 until POINTS) {
            val fwd = when (i) {
                0 -> pos[0] - pos[1]
                POINTS - 1 -> pos[POINTS - 2] - pos[POINTS - 1]
                else -> pos[i - 1] - pos[i + 1]
            }.normalized(heading)
            frames[i] = Quat.fromForwardUp(fwd, up)
        }
    }

    /**
     * [sample] straight into pose arrays: spine xyz into [sp] (POINTS × 3) and frame quaternions xyzw into [fr]
     * (POINTS × 4). The frames use the same arithmetic as [Quat.fromForwardUp] without allocating (this runs for
     * every organism on every snapshot); [pos] is scratch for the spine points.
     */
    fun sampleInto(up: Vec3, pos: Array<Vec3>, sp: FloatArray, fr: FloatArray) {
        samplePositions(pos)
        for (i in 0 until POINTS) {
            val p = pos[i]
            sp[i * 3] = p.x.toFloat(); sp[i * 3 + 1] = p.y.toFloat(); sp[i * 3 + 2] = p.z.toFloat()
            val a = pos[if (i == 0) 0 else i - 1]; val b = pos[if (i == POINTS - 1) POINTS - 1 else i + 1]
            frameInto(a.x - b.x, a.y - b.y, a.z - b.z, up, fr, i * 4)
        }
    }

    /** Quat.fromForwardUp((dx, dy, dz).normalized(heading), up), written as floats to [out] at [o]. */
    private fun frameInto(dx: Double, dy: Double, dz: Double, up: Vec3, out: FloatArray, o: Int) {
        // forward: normalized(heading), then normalized again as fromForwardUp does
        var fx: Double; var fy: Double; var fz: Double
        val l0 = sqrt(dx * dx + dy * dy + dz * dz)
        if (l0 < 1e-9) { fx = heading.x; fy = heading.y; fz = heading.z } else { fx = dx / l0; fy = dy / l0; fz = dz / l0 }
        val l1 = sqrt(fx * fx + fy * fy + fz * fz)
        if (l1 < 1e-9) { fx = 1.0; fy = 0.0; fz = 0.0 } else { fx /= l1; fy /= l1; fz /= l1 }
        // up rejected from forward (fallback Y or X), normalized
        var d = up.x * fx + up.y * fy + up.z * fz
        var ux = up.x - fx * d; var uy = up.y - fy * d; var uz = up.z - fz * d
        if (ux * ux + uy * uy + uz * uz < 1e-10) {
            val bx: Double; val by: Double
            if (abs(fy) < 0.9) { bx = 0.0; by = 1.0 } else { bx = 1.0; by = 0.0 }
            d = bx * fx + by * fy + 0.0 * fz
            ux = bx - fx * d; uy = by - fy * d; uz = 0.0 - fz * d
        }
        val lu = sqrt(ux * ux + uy * uy + uz * uz)
        if (lu < 1e-9) { ux = 1.0; uy = 0.0; uz = 0.0 } else { ux /= lu; uy /= lu; uz /= lu }
        // side = forward × up; quaternion from the columns (f, u, s), as Quat.fromColumns
        val sx = fy * uz - fz * uy; val sy = fz * ux - fx * uz; val sz = fx * uy - fy * ux
        val m00 = fx; val m10 = fy; val m20 = fz
        val m01 = ux; val m11 = uy; val m21 = uz
        val m02 = sx; val m12 = sy; val m22 = sz
        val tr = m00 + m11 + m22
        var qx: Double; var qy: Double; var qz: Double; var qw: Double
        if (tr > 0) {
            val s = sqrt(tr + 1.0) * 2
            qx = (m21 - m12) / s; qy = (m02 - m20) / s; qz = (m10 - m01) / s; qw = 0.25 * s
        } else if (m00 > m11 && m00 > m22) {
            val s = sqrt(1.0 + m00 - m11 - m22) * 2
            qx = 0.25 * s; qy = (m01 + m10) / s; qz = (m02 + m20) / s; qw = (m21 - m12) / s
        } else if (m11 > m22) {
            val s = sqrt(1.0 + m11 - m00 - m22) * 2
            qx = (m01 + m10) / s; qy = 0.25 * s; qz = (m12 + m21) / s; qw = (m02 - m20) / s
        } else {
            val s = sqrt(1.0 + m22 - m00 - m11) * 2
            qx = (m02 + m20) / s; qy = (m12 + m21) / s; qz = 0.25 * s; qw = (m10 - m01) / s
        }
        val lq = sqrt(qx * qx + qy * qy + qz * qz + qw * qw)
        if (lq < 1e-12) { qx = 0.0; qy = 0.0; qz = 0.0; qw = 1.0 } else { qx /= lq; qy /= lq; qz /= lq; qw /= lq }
        out[o] = qx.toFloat(); out[o + 1] = qy.toFloat(); out[o + 2] = qz.toFloat(); out[o + 3] = qw.toFloat()
    }

    /** Spine points head→tail into [pos] (trail or rigid line, eased into an [override] path). */
    private fun samplePositions(pos: Array<Vec3>) {
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
    }

    /** Stations every [seg] cm along head → [poly][startIdx..], extended straight past its end. */
    private fun walk(poly: List<Vec3>, startIdx: Int, startHead: Vec3, seg: Double, out: Array<Vec3>, shift: Vec3? = null) {
        out[0] = startHead
        var prev = startHead
        var j = startIdx
        var lastDir = heading * -1.0
        for (i in 1 until POINTS) {
            var remaining = seg
            var cur = prev
            while (true) {
                if (j >= poly.size) { cur = cur + lastDir * remaining; break }
                val nxt = if (shift == null) poly[j] else poly[j] + shift
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

    /** Ground height under (x, z) a trail point must stay above ([liftTrail]). */
    fun interface Floor { fun y(x: Double, z: Double): Double }

    /**
     * Raises trail points that lie under [floor] by at most [maxLift] cm each call (a body that turned round over a
     * mound, or whose track ran down a pit that has since been filled, lies on the sand instead of through it; the limit
     * keeps a large correction from snapping the body shape in one tick). The head is the caller's to place.
     */
    fun liftTrail(floor: Floor, maxLift: Double) {
        if (rigid) return
        for (i in trail.indices) {
            val p = trail[i]
            val f = floor.y(p.x, p.z)
            if (p.y < f) trail[i] = p.withY(min(f, p.y + maxLift))
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
