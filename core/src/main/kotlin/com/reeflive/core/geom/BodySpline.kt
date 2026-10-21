package com.reeflive.core.geom

import kotlin.math.exp
import kotlin.math.abs
import kotlin.math.max
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
    /**
     * Opt-in for [keepInside] (set by a species brain). Off by default: turning it on for the whole stock shifts the
     * chaotic whole-tank runs, so the existing kinds wait for the body-collision work to adopt it.
     */
    var glassGuard = false
    /** Glass box from [keepInside] (unbounded until it is called). */
    private var boxMinX = Double.NEGATIVE_INFINITY
    private var boxMaxX = Double.POSITIVE_INFINITY
    private var boxMinZ = Double.NEGATIVE_INFINITY
    private var boxMaxZ = Double.POSITIVE_INFINITY
    private var boxMaxY = Double.POSITIVE_INFINITY

    fun reset(headPos: Vec3, headingDir: Vec3) {
        head = headPos; heading = headingDir.normalized()
        trail.clear()
        val step = length / 16
        for (i in 1..17) trail.add(head - heading * (step * i))
        // a placement is a cut: take the current override shape at once
        overrideMix = if (override != null) 1.0 else 0.0
        lastOverride = override
    }

    /**
     * Show a corrected head→tail polyline (the sim's rock guard) at once, for a rigid body or one already on an
     * [override] path: the polyline was taken from the shape currently shown, so it does not pop, and easing it in
     * would leave the uncorrected shape (in the rock) mostly showing. It eases back out once [override] is cleared.
     */
    fun correct(poly: List<Vec3>) {
        if (!rigid && overrideMix < 1.0 && poly.size >= 2) {
            // part way through easing into a path (a goby sliding into its burrow): the corrected shape becomes the
            // trail and the ease starts again from it. Shown at full weight, the path the behaviour sets again next
            // tick replaced it at once (the rest of the ease and the correction in one 7 cm pop)
            adopt(poly)
            overrideMix = 0.0
            return
        }
        override = poly
        lastOverride = poly
        overrideMix = 1.0
        held = true
    }

    /** A [correct]ion or [adopt]ed trail the rock guard is holding (see [holdCorrection], [update]). */
    private var held = false
    /** Whether a correction is being held (with none, [holdCorrection] and [releaseCorrection] change nothing). */
    val holding: Boolean get() = held

    /**
     * Keep the last [correct]ed polyline on at full weight while the rock guard still needs it (the plain rigid body
     * would cut the rock), instead of letting it ease out and correcting it afresh every few ticks. False when no
     * correction is being held; [releaseCorrection] lets it ease out.
     */
    fun holdCorrection(): Boolean {
        if (!held || !rigid) return false
        override = lastOverride ?: run { held = false; return false }
        overrideMix = 1.0
        return true
    }

    fun releaseCorrection() {
        // a trail let go after a hold relaxes back over a few updates, not in one long fast-forward step
        if (held && !rigid) relaxEase = RELAX_EASE_UPDATES
        held = false
    }

    /** Updates left in which the trail's relax is capped at [RELAX_EASE_STEP] (after a hold is let go). */
    private var relaxEase = 0

    /** Replace the trail with a corrected head→tail polyline (the sim's rock guard), so it carries on from there. */
    fun adopt(poly: List<Vec3>) {
        if (rigid || poly.size < 2) return
        held = true
        head = poly[0]
        trail.clear()
        for (i in 1 until poly.size) trail.add(poly[i])
    }

    fun update(headPos: Vec3, headingDir: Vec3, dt: Double, relaxRate: Double) {
        if (!(headPos.x.isFinite() && headPos.y.isFinite() && headPos.z.isFinite())) return
        head = headPos
        heading = headingDir.normalized(heading)
        // ease the body into and out of an override path instead of switching shape in one tick
        override?.let { if (it.isNotEmpty()) lastOverride = it }
        val target = if (override != null) 1.0 else 0.0
        // (at most MAX_BLEND_STEP per update: a long fast-forward tick would otherwise swap the shape in one step)
        overrideMix += ((target - overrideMix) * (1 - exp(-OVERRIDE_BLEND_RATE * dt.coerceAtLeast(0.0)))).coerceIn(-MAX_BLEND_STEP, MAX_BLEND_STEP)
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
        // Relax toward the straight line behind the heading (not while the rock guard holds an adopted shape: the
        // straight line would put the body back into the rock)
        if (relaxRate > 0 && !held) {
            // capped per tick: at the 0.5 s fast-forward tick a body that had just stopped out of a curve straightened
            // 86 % in one step (a visible body pop); at the live 20 Hz tick this changes nothing
            var k = 1 - exp(-min(relaxRate * dt, MAX_RELAX_STEP))
            if (relaxEase > 0) { relaxEase--; k = minOf(k, RELAX_EASE_STEP) }
            // and capped in distance: a fish that pivoted on the spot (a grazing blenny turning about at the rock)
            // has its tail a body length off the straight line, and a fifth of that in one tick popped the tail 5 cm
            var far = 0.0
            var s = head.distanceTo(trail[0])
            var prev = trail[0]
            for (i in trail.indices) {
                if (i > 0) { s += trail[i].distanceTo(prev); prev = trail[i] }
                if (s > length) break
                far = max(far, (head - heading * s).distanceTo(trail[i]))
            }
            if (far * k > MAX_RELAX_BL * length) k = MAX_RELAX_BL * length / far
            s = head.distanceTo(trail[0])
            prev = trail[0]
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

    /** The head the stations start from (the last [update]'s). */
    val headPos: Vec3 get() = head

    /** [sample]'s spine points only, without the frames (the rock guard's per-tick check). */
    fun sampleStations(pos: Array<Vec3>) = samplePositions(pos)

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
                if (j >= poly.size) {
                    cur = cur + lastDir * remaining
                    if (cur.x < boxMinX || cur.x > boxMaxX || cur.z < boxMinZ || cur.z > boxMaxZ || cur.y > boxMaxY)
                        cur = Vec3(cur.x.coerceIn(boxMinX, boxMaxX), min(cur.y, boxMaxY), cur.z.coerceIn(boxMinZ, boxMaxZ))
                    break
                }
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
    fun interface Floor {
        fun y(x: Double, z: Double): Double
        /** Whether trail point [p] may be raised to [to] (false: it stays where it is, e.g. under it is a buried rock foot). */
        fun canLift(p: Vec3, to: Double): Boolean = true
    }

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
            if (p.y < f && floor.canLift(p, f)) trail[i] = p.withY(min(f, p.y + maxLift))
        }
    }

    /**
     * Keeps trail points inside the glass box [minX]..[maxX] × [minZ]..[maxZ] (a fish that turned about at the glass
     * had its tail relax straight back through it), and the stations sampled past the trail's end too (a hovering fish
     * has a short trail and its tail is drawn straight on from it), and under the water line [maxY] (a deep-bodied fish
     * nosing down near the top lifted its tail out of the water). The head is the caller's to place.
     */
    fun keepInside(minX: Double, maxX: Double, minZ: Double, maxZ: Double, maxY: Double = Double.POSITIVE_INFINITY) {
        if (rigid || !glassGuard) return
        boxMinX = minX; boxMaxX = maxX; boxMinZ = minZ; boxMaxZ = maxZ; boxMaxY = maxY
        for (i in trail.indices) {
            val p = trail[i]
            if (p.x < minX || p.x > maxX || p.z < minZ || p.z > maxZ || p.y > maxY) trail[i] = Vec3(p.x.coerceIn(minX, maxX), min(p.y, maxY), p.z.coerceIn(minZ, maxZ))
        }
    }

    /** Current trail as head→tail polyline (for tests/debug). */
    fun trailSnapshot(): List<Vec3> = listOf(head) + trail

    companion object {
        const val POINTS = 16
        const val MAX_TRAIL = 48
        /** Rate (1/s) at which the body eases into or out of an [override] path. */
        const val OVERRIDE_BLEND_RATE = 6.0
        /** Largest relax exponent one tick may apply (k ≤ 1 − e^−0.5 ≈ 0.39). */
        const val MAX_RELAX_STEP = 0.5
        /** Largest change of the override blend in one update (the live 20 Hz tick moves it at most 0.26). */
        const val MAX_BLEND_STEP = 0.35
        /** Farthest a trail point moves in one relax update, as a fraction of the body length. */
        const val MAX_RELAX_BL = 0.15
        /** After a held trail is let go, its relax is capped at this fraction per update for that many updates. */
        const val RELAX_EASE_STEP = 0.25
        const val RELAX_EASE_UPDATES = 6
    }
}
