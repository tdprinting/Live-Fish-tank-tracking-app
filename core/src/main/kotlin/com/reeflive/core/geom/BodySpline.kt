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

    // the last pose the collision resolve left clear of the rock and sand (restored when a wedge cannot be cleared)
    private var safeHead = Vec3.ZERO
    private var safeHeading = Vec3.X
    private val safeTrail = ArrayList<Vec3>(48)
    private var safeValid = false

    /** Remembers the current pose as clear of the environment. */
    fun saveSafe() {
        safeHead = head; safeHeading = heading
        safeTrail.clear(); safeTrail.addAll(trail)
        safeValid = true
    }

    val hasSafe: Boolean get() = safeValid

    /** Forgets the remembered pose (teleports, burrows, den paths). */
    fun dropSafe() { safeValid = false }

    /** Returns to the remembered clear pose; false when there is none. */
    fun restoreSafe(): Boolean {
        if (!safeValid) return false
        head = safeHead; heading = safeHeading
        trail.clear(); trail.addAll(safeTrail)
        return true
    }

    fun reset(headPos: Vec3, headingDir: Vec3) {
        safeValid = false
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

    /** Current head position (the snout; the organism's `pos`). */
    val headPos: Vec3 get() = head
    /** Current heading (unit). */
    val headingDir: Vec3 get() = heading

    /** A copy of the pose (head, heading, trail), for a trial move that may be taken back with [setPose]. */
    class Pose internal constructor(internal val head: Vec3, internal val heading: Vec3, internal val trail: List<Vec3>)
    fun pose(): Pose = Pose(head, heading, ArrayList(trail))
    fun setPose(p: Pose) { head = p.head; heading = p.heading; trail.clear(); trail.addAll(p.trail) }

    /**
     * Slides the body [d] cm back along its own spine, the way it came in (a fish backing out of a slot it cannot turn
     * in): the head moves onto the trail and the tail carries on straight past its end. A rigid body backs straight
     * along its heading; an [override] path (den, hole) is left alone.
     */
    fun backUp(d: Double) {
        if (override != null) return
        if (rigid || trail.isEmpty()) { head -= heading * d; return }
        var cur = head
        var prevPt = head
        var rem = d
        var j = 0
        while (j < trail.size) {
            prevPt = cur
            val dd = cur.distanceTo(trail[j])
            if (dd >= rem && dd > 1e-12) { cur = cur + (trail[j] - cur) * (rem / dd); rem = 0.0; break }
            rem -= dd; cur = trail[j]; j++
        }
        val tailDir = if (trail.size >= 2) (trail[trail.size - 1] - trail[trail.size - 2]).normalized(heading * -1.0) else heading * -1.0
        if (rem > 0) cur = cur + tailDir * rem
        // the new heading: along the stretch of spine the head now sits on
        heading = if (j < trail.size) (prevPt - trail[j]).let { if (it.lengthSq() > 1e-12) it.normalized(heading) else heading } else tailDir * -1.0
        val rest = if (j < trail.size) ArrayList(trail.subList(j, trail.size)) else ArrayList()
        head = cur
        trail.clear(); trail.addAll(rest)
        respace(if (trail.isEmpty()) length / 16 else head.distanceTo(trail[0]))
    }

    /**
     * Displaces the body (collision response, SIMULATION §4.3): [deltas] holds one displacement per spine station
     * (u = i / (POINTS − 1), head first). The head moves by `deltas[0]`, each trail point by the displacement
     * interpolated at its arclength, so a push at the tail bends the body there instead of moving the head.
     * A rigid body only translates (by `deltas[0]`); an [override] path (den, hole) is left alone.
     */
    fun displace(deltas: Array<Vec3>) {
        if (override != null) return
        val d0 = deltas[0]
        if (rigid || trail.isEmpty()) { head += d0; return }
        val seg = length / (POINTS - 1)
        val gap0 = head.distanceTo(trail[0])
        var s = gap0
        var prev = trail[0]
        head += d0
        for (i in trail.indices) {
            if (i > 0) { s += trail[i].distanceTo(prev); prev = trail[i] }
            val f = (s / seg).coerceIn(0.0, (POINTS - 1).toDouble())
            val k = kotlin.math.min(f.toInt(), POINTS - 2)
            val t = f - k
            trail[i] = trail[i] + deltas[k] * (1 - t) + deltas[k + 1] * t
        }
        respace(gap0)
    }

    /**
     * Re-spaces the trail at the recording step along its (displaced) polyline, keeping the gap from the head to the
     * newest point, so a push never leaves long or bunched-up segments for the relax step to amplify.
     */
    private fun respace(gap0: Double) {
        val step = length / 16
        val src = ArrayList(trail)
        trail.clear()
        var cur = head
        var j = 0
        var want = gap0.coerceIn(1e-6, step)
        var total = 0.0
        while (trail.size < MAX_TRAIL && total <= length * 1.25) {
            var remaining = want
            while (j < src.size) {
                val d = cur.distanceTo(src[j])
                if (d >= remaining && d > 1e-12) { cur = cur + (src[j] - cur) * (remaining / d); remaining = 0.0; break }
                remaining -= d; cur = src[j]; j++
            }
            if (remaining > 0) {
                // the polyline ran out: continue straight behind its last direction
                val dir = if (trail.size >= 2) (trail[trail.size - 1] - trail[trail.size - 2]).normalized(heading * -1.0) else heading * -1.0
                cur = cur + dir * remaining
            }
            trail.add(cur)
            total += want
            want = step
        }
    }

    /** Spine points only (no frames), head→tail, the same as [sample]. */
    fun samplePoints(pos: Array<Vec3>) = points(pos)

    /**
     * Fills [pos] (size ≥ POINTS) with spine points head→tail and [frames] with body frames
     * given the organism [up] vector (roll/posture already applied by Steering).
     */
    fun sample(up: Vec3, pos: Array<Vec3>, frames: Array<Quat>) {
        points(pos)
        for (i in 0 until POINTS) {
            val fwd = when (i) {
                0 -> pos[0] - pos[1]
                POINTS - 1 -> pos[POINTS - 2] - pos[POINTS - 1]
                else -> pos[i - 1] - pos[i + 1]
            }.normalized(heading)
            frames[i] = Quat.fromForwardUp(fwd, up)
        }
    }

    private fun points(pos: Array<Vec3>) {
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
        /**
         * Largest turn between neighbouring spine stations (rad per BL/15): at 0.42 rad the bend radius stays ≥ 0.16 BL,
         * wider than a fish's half-thickness plus a folded pectoral, so the body cannot pass through itself. Used by the
         * renderer (FishRig.limitBend). The sim's collision body follows the trail unlimited: clamping a folded trail
         * there has no continuous answer, and the body flicked between folds from tick to tick (FishGlitchTest).
         */
        const val MAX_BEND_PER_SEGMENT = 0.42
        /** Total head-to-tail bend (rad); a C-start or a U-shaped den path still fits, a spiral does not. */
        const val MAX_BEND_TOTAL = 3.1416
        /** Rate (1/s) at which the body eases into or out of an [override] path. */
        const val OVERRIDE_BLEND_RATE = 6.0
    }
}
