package com.reeflive.app.render

import com.reeflive.core.geom.BodySpline
import com.reeflive.core.sim.CritterPose
import com.reeflive.core.sim.OrganismPose
import com.reeflive.core.sim.SimSnapshot
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Per-organism render state (ARCHITECTURE §8.2 `FishRig`): the 16 spline control points + frames and
 * the animation scalars, interpolated between the previous and current [SimSnapshot] (§8.7: the sim runs
 * at ~20 Hz, the renderer interpolates). Pure Kotlin; arrays are reused between frames.
 */
class FishRig(val id: String) {
    var speciesId = ""
    var variant: String? = null
    /** uSpine: 16 × vec4 (xyz world cm, w = 0). */
    val spine = FloatArray(64)
    /** uFrame: 16 × vec4 quaternion xyzw mapping model X/Y/Z to world. */
    val frames = FloatArray(64)
    var phase = 0f; var amp = 0f; var waveLen = 1f; var pectoral = 0f; var finClamp = 0f
    var gill = 0f; var gillRate = 1f; var desat = 0f; var ich = 0f; var thin = 0f; var eyeCloud = 0f
    var scale = 1f; var eyeYaw = 0f; var appendage = 0f; var extension = 1f; var mouth = 0f
    var alive = true; var visible = true; var posture = 0
    /** Locomotion (core MotionProfile): pectoral stroke phase (cycles) and sweep, C-start bend (−1..1), motion mode. */
    var pecPhase = 0f; var pecAmp = 0f; var bend = 0f; var motionMode = 0
    /**
     * uLimb: per-limb pose from the core LimbRig (SIMULATION §19), up to 15 × vec4 (the hermit: 10 + 5 surface-contact entries); [limbCount] entries are valid,
     * 0 = none. Crawlers send 10 (posed about model pivots), the shrimp one (stride, stepping, signal, grooming).
     */
    val limbs = FloatArray(60)
    var limbCount = 0
    /** Seen in the latest snapshot (rigs not seen are dropped). */
    var touched = false

    fun headX() = spine[0]; fun headY() = spine[1]; fun headZ() = spine[2]

    /** Fills this rig with lerp(a, b, t); a may be null (new organism). */
    fun set(a: OrganismPose?, b: OrganismPose, t: Float) {
        speciesId = b.speciesId
        variant = variantOf(b)
        val pa = a ?: b
        val n = minOf(16, b.spine.size / 3, pa.spine.size / 3)
        for (i in 0 until 16) {
            val k = minOf(i, maxOf(n - 1, 0))
            for (c in 0 until 3) {
                val va = pa.spine.getOrElse(k * 3 + c) { 0f }; val vb = b.spine.getOrElse(k * 3 + c) { 0f }
                spine[i * 4 + c] = va + (vb - va) * t
            }
            spine[i * 4 + 3] = 0f
            // nlerp along the shorter arc
            var dot = 0f
            for (c in 0 until 4) dot += pa.frames.getOrElse(k * 4 + c) { if (c == 3) 1f else 0f } * b.frames.getOrElse(k * 4 + c) { if (c == 3) 1f else 0f }
            val sgn = if (dot < 0f) -1f else 1f
            var len = 0f
            for (c in 0 until 4) {
                val qa = pa.frames.getOrElse(k * 4 + c) { if (c == 3) 1f else 0f } * sgn
                val qb = b.frames.getOrElse(k * 4 + c) { if (c == 3) 1f else 0f }
                val q = qa + (qb - qa) * t
                frames[i * 4 + c] = q; len += q * q
            }
            len = sqrt(len).coerceAtLeast(1e-6f)
            for (c in 0 until 4) frames[i * 4 + c] /= len
        }
        phase = lerpPhase(pa.swimPhase, b.swimPhase, t)
        amp = lerp(pa.amp, b.amp, t); waveLen = lerp(pa.waveLen, b.waveLen, t)
        pectoral = lerp(pa.pectoral, b.pectoral, t); finClamp = lerp(pa.finClamp, b.finClamp, t)
        gill = lerp(pa.gill, b.gill, t); gillRate = lerp(pa.gillRate, b.gillRate, t)
        desat = lerp(pa.desat, b.desat, t); ich = lerp(pa.ich, b.ich, t); thin = lerp(pa.thin, b.thin, t)
        eyeCloud = lerp(pa.eyeCloud, b.eyeCloud, t); scale = lerp(pa.scale, b.scale, t).coerceAtLeast(0.1f)
        eyeYaw = lerp(pa.eyeYaw, b.eyeYaw, t); appendage = lerpPhase(pa.appendagePhase, b.appendagePhase, t, APPENDAGE_WRAP)
        extension = lerp(pa.extension, b.extension, t); mouth = lerp(pa.mouth, b.mouth, t)
        alive = b.alive; visible = b.visible; posture = b.posture
        pecPhase = lerpPhase(pa.pecPhase, b.pecPhase, t); pecAmp = lerp(pa.pecAmp, b.pecAmp, t)
        bend = lerp(pa.bend, b.bend, t); motionMode = b.motionMode
        lerpLimbs(if (pa.limbs.size == b.limbs.size) pa.limbs else b.limbs, b.limbs, t)
        limitBend(spine, frames, MAX_BEND_PER_SEGMENT, MAX_BEND_TOTAL, bendScratch)
        touched = true
    }

    private val bendScratch = FloatArray(64)

    /** Fills this rig from an in-tank microfauna pose (core [CritterPose]); critters are not interpolated. */
    fun setCritter(p: CritterPose) {
        speciesId = p.kind.speciesId
        variant = null
        for (i in 0 until 16) {
            spine[i * 4] = p.spine[i * 3]; spine[i * 4 + 1] = p.spine[i * 3 + 1]; spine[i * 4 + 2] = p.spine[i * 3 + 2]; spine[i * 4 + 3] = 0f
            for (c in 0 until 4) frames[i * 4 + c] = p.frames[i * 4 + c]
        }
        phase = p.swimPhase; amp = p.amp; waveLen = p.waveLen; appendage = p.appendage
        pectoral = 0f; finClamp = 0f; gill = 0f; gillRate = 1f; desat = 0f; ich = 0f; thin = 0f; eyeCloud = 0f
        scale = p.scale; eyeYaw = 0f; extension = 1f; mouth = 0f
        alive = true; visible = true; posture = 0
        pecPhase = 0f; pecAmp = 0f; bend = 0f; motionMode = 0
        limbCount = 0
        touched = true
    }

    /** x of each limb is a phase in [0, 1) cycles (interpolated across the 1 → 0 wrap); y, z, w are plain scalars. */
    private fun lerpLimbs(a: FloatArray, b: FloatArray, t: Float) {
        val n = minOf(limbs.size, b.size) / 4
        limbCount = n
        for (i in 0 until n) {
            val j = i * 4
            var d = b[j] - a[j]
            d -= kotlin.math.round(d)
            var x = a[j] + d * t
            x -= kotlin.math.floor(x)
            limbs[j] = x
            for (c in 1 until 4) limbs[j + c] = a[j + c] + (b[j + c] - a[j + c]) * t
        }
    }

    /** Distance from a point to the head (cm), for LOD selection. */
    fun distanceTo(x: Float, y: Float, z: Float): Float {
        val dx = spine[0] - x; val dy = spine[1] - y; val dz = spine[2] - z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    companion object {
        fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

        /** Phases are in cycles and may wrap (e.g. mod 1000); interpolate across the wrap. */
        fun lerpPhase(a: Float, b: Float, t: Float, period: Float = 1000f): Float {
            var d = b - a
            if (d > 0.5f * period) d -= period
            if (d < -0.5f * period) d += period
            return a + d * t
        }

        /** The core's appendage phase wrap (rad, a whole number of turns: core Organism.APPENDAGE_WRAP). */
        val APPENDAGE_WRAP = com.reeflive.core.sim.Organism.APPENDAGE_WRAP.toFloat()

        /** The pose's model variant (darwin_black clown, …) when the core exposes one. */
        fun variantOf(p: OrganismPose): String? = p.variant

        /**
         * Largest yaw the renderer feeds the skinning shader between neighbouring spine stations (rad per BL/15).
         * The body trail can fold into a hairpin on a sharp turn; at 0.42 rad the bend radius stays ≥ 0.16 BL,
         * wider than a fish's half-thickness plus a folded pectoral, so the body cannot pass through itself.
         */
        val MAX_BEND_PER_SEGMENT = BodySpline.MAX_BEND_PER_SEGMENT.toFloat()
        /** Total head-to-tail bend (rad); a C-start or a U-shaped den path still fits, a spiral does not. */
        val MAX_BEND_TOTAL = BodySpline.MAX_BEND_TOTAL.toFloat()

        /**
         * Caps the bend of a stride-4 [spine] (16 stations, head first) in place: walking head → tail, each segment
         * keeps its length but turns at most [maxPerSeg] from the previous one and at most [maxTotal] in all. The
         * head segment and the head position are kept, so picking and the sim's head stay put. Frames whose
         * station tangent changed are rotated by the same swing, which keeps their roll. Returns true if anything
         * moved. [scratch] (≥ 64 floats) receives a copy of the original spine.
         */
        fun limitBend(spine: FloatArray, frames: FloatArray, maxPerSeg: Float, maxTotal: Float, scratch: FloatArray): Boolean {
            spine.copyInto(scratch, 0, 0, 64)
            val old = scratch
            // previous segment direction (head → station 1)
            var px = old[4] - old[0]; var py = old[5] - old[1]; var pz = old[6] - old[2]
            val pl = sqrt(px * px + py * py + pz * pz)
            if (pl < 1e-6f) return false
            px /= pl; py /= pl; pz /= pl
            var budget = maxTotal
            var changed = false
            for (i in 1 until 15) {
                var dx = old[(i + 1) * 4] - old[i * 4]; var dy = old[(i + 1) * 4 + 1] - old[i * 4 + 1]; var dz = old[(i + 1) * 4 + 2] - old[i * 4 + 2]
                val len = sqrt(dx * dx + dy * dy + dz * dz)
                if (len < 1e-6f) {
                    // coincident points: continue straight
                    spine[(i + 1) * 4] = spine[i * 4]; spine[(i + 1) * 4 + 1] = spine[i * 4 + 1]; spine[(i + 1) * 4 + 2] = spine[i * 4 + 2]
                    continue
                }
                dx /= len; dy /= len; dz /= len
                val c = (px * dx + py * dy + pz * dz).coerceIn(-1f, 1f)
                val ang = kotlin.math.acos(c)
                val allowed = minOf(maxPerSeg, budget.coerceAtLeast(0f))
                if (ang > allowed + 1e-4f) {
                    // rotate the previous direction toward this one by `allowed`, in their common plane
                    var qx = dx - px * c; var qy = dy - py * c; var qz = dz - pz * c
                    var ql = sqrt(qx * qx + qy * qy + qz * qz)
                    if (ql < 1e-5f) {
                        // exact reversal: bend about world up (a yaw), or about X if the body is vertical
                        qx = -pz; qy = 0f; qz = px
                        ql = sqrt(qx * qx + qz * qz)
                        if (ql < 1e-5f) { qx = 0f; qy = -pz; qz = py; ql = sqrt(qy * qy + qz * qz).coerceAtLeast(1e-6f) }
                    }
                    qx /= ql; qy /= ql; qz /= ql
                    val ca = kotlin.math.cos(allowed); val sa = kotlin.math.sin(allowed)
                    dx = px * ca + qx * sa; dy = py * ca + qy * sa; dz = pz * ca + qz * sa
                    budget -= allowed
                    changed = true
                } else {
                    budget -= ang
                }
                spine[(i + 1) * 4] = spine[i * 4] + dx * len
                spine[(i + 1) * 4 + 1] = spine[i * 4 + 1] + dy * len
                spine[(i + 1) * 4 + 2] = spine[i * 4 + 2] + dz * len
                px = dx; py = dy; pz = dz
            }
            if (!changed) return false
            for (i in 0 until 16) {
                // station tangent as the sim computes it (central difference, one-sided at the ends), old vs new
                val a = if (i == 0) 0 else i - 1; val b = if (i == 15) 15 else i + 1
                var ox = old[a * 4] - old[b * 4]; var oy = old[a * 4 + 1] - old[b * 4 + 1]; var oz = old[a * 4 + 2] - old[b * 4 + 2]
                var nx = spine[a * 4] - spine[b * 4]; var ny = spine[a * 4 + 1] - spine[b * 4 + 1]; var nz = spine[a * 4 + 2] - spine[b * 4 + 2]
                val ol = sqrt(ox * ox + oy * oy + oz * oz); val nl = sqrt(nx * nx + ny * ny + nz * nz)
                if (ol < 1e-6f || nl < 1e-6f) continue
                ox /= ol; oy /= ol; oz /= ol; nx /= nl; ny /= nl; nz /= nl
                val d = ox * nx + oy * ny + oz * nz
                if (d > 0.99999f) continue
                // r = rotation taking old → new: (old × new, 1 + old·new), normalized
                var rx = oy * nz - oz * ny; var ry = oz * nx - ox * nz; var rz = ox * ny - oy * nx; var rw = 1f + d
                if (rw < 1e-5f) {
                    // opposite tangents: half-turn about an axis perpendicular to the old tangent
                    rx = -oz; ry = 0f; rz = ox; rw = 0f
                    if (rx * rx + rz * rz < 1e-8f) { rx = 1f; ry = 0f; rz = 0f }
                }
                val rl = sqrt(rx * rx + ry * ry + rz * rz + rw * rw)
                rx /= rl; ry /= rl; rz /= rl; rw /= rl
                val k = i * 4
                val qx = frames[k]; val qy = frames[k + 1]; val qz = frames[k + 2]; val qw = frames[k + 3]
                // frames[i] = r * q (Hamilton, xyzw; same as core Quat.times)
                var fx = rw * qx + rx * qw + ry * qz - rz * qy
                var fy = rw * qy - rx * qz + ry * qw + rz * qx
                var fz = rw * qz + rx * qy - ry * qx + rz * qw
                var fw = rw * qw - rx * qx - ry * qy - rz * qz
                val fl = sqrt(fx * fx + fy * fy + fz * fz + fw * fw).coerceAtLeast(1e-6f)
                fx /= fl; fy /= fl; fz /= fl; fw /= fl
                frames[k] = fx; frames[k + 1] = fy; frames[k + 2] = fz; frames[k + 3] = fw
            }
            return true
        }
    }
}

/**
 * Buffers the last few distinct snapshots and blends organisms between them on a smoothed timeline. Feed it once per
 * frame with whatever the provider returns.
 *
 * Each snapshot gets a timeline stamp that follows its arrival time but is low-passed toward the steady snapshot
 * interval, and the scene is drawn one interval behind the newest stamp. The drawn pose is therefore a continuous
 * function of wall time: a snapshot that lands a few ms early or late changes how fast the blend runs, never where
 * it is. (Restarting the blend at 0 whenever a snapshot arrived made every early snapshot jump the fish forward and
 * every late one freeze it: visible judder on a phone whose sim thread and vsync drift against each other.)
 */
class SnapshotInterpolator {
    private class Entry(val snap: SimSnapshot, var stampNs: Long) {
        val byId: Map<String, OrganismPose> = snap.organisms.associateBy { it.id }
    }

    private val buf = ArrayList<Entry>(MAX_BUFFERED)
    /** The snapshot before [current] (null until two have arrived). */
    val previous: SimSnapshot? get() = if (buf.size >= 2) buf[buf.size - 2].snap else null
    /** The newest snapshot (lights, flow and the HUD read it). */
    val current: SimSnapshot? get() = buf.lastOrNull()?.snap
    private var intervalNs = 50_000_000L
    /** Wall ns per sim second between snapshots (1e9 at 1×), smoothed. */
    private var nsPerSimSec = 1e9
    /** Mean deviation of the arrivals from the steady cadence (ns). */
    private var jitterNs = 0.0
    /** How far behind the newest snapshot the scene is drawn: one interval plus room for a late arrival. */
    private val delayNs: Long get() = intervalNs + min(2 * jitterNs, intervalNs.toDouble()).toLong()
    private var lastArrivalNs = 0L
    val rigs = LinkedHashMap<String, FishRig>()
    fun push(s: SimSnapshot?, nowNs: Long) {
        if (s == null) return
        val cur = buf.lastOrNull()
        if (cur != null && (s === cur.snap || s.tick != 0L && s.tick == cur.snap.tick && s.simTimeSec == cur.snap.simTimeSec)) return
        var stamp = nowNs
        if (cur != null) {
            val dt = nowNs - lastArrivalNs
            val simDt = s.simTimeSec - cur.snap.simTimeSec
            if (dt in 5_000_000L..500_000_000L) {
                intervalNs = (intervalNs * 0.8 + dt * 0.2).toLong()
                if (simDt > 1e-6) nsPerSimSec = nsPerSimSec * 0.8 + dt / simDt * 0.2
            }
            // phase-lock to the arrivals: the steady cadence in sim time (a snapshot the provider skipped leaves a
            // two-tick gap, not a double-speed tick), nudged an eighth of the way toward the actual arrival (and
            // never more than an interval off it, so the clock cannot drift)
            val step = if (simDt > 1e-6) (simDt * nsPerSimSec).toLong().coerceIn(intervalNs / 4, 4 * intervalNs) else intervalNs
            val expected = cur.stampNs + step
            stamp = (expected + (nowNs - expected) / 8).coerceIn(nowNs - intervalNs, nowNs + intervalNs / 2)
            val stalled = stamp - cur.stampNs > 3 * intervalNs
            // (a stall is not jitter: it would push the render clock back by a whole interval)
            if (!stalled) jitterNs = jitterNs * 0.9 + min(abs(nowNs - expected).toDouble(), intervalNs.toDouble()) * 0.1
            if (stalled) {
                // after a stall (pause, a hitch) the blend restarts from the last pose, which is where the render
                // clock now stands, and runs to the new one over one interval instead of jumping to it
                cur.stampNs = nowNs - delayNs
                stamp = cur.stampNs + intervalNs
            }
            // two snapshots in quick succession still get a spaced pair (no instant sweep between them)
            stamp = maxOf(stamp, cur.stampNs + intervalNs / 4)
        }
        lastArrivalNs = nowNs
        buf.add(Entry(s, stamp))
        if (buf.size > MAX_BUFFERED) buf.removeAt(0)
    }

    /** Blend factor of the pair drawn at [nowNs] (0 = the older, 1 = the newer snapshot of [bracket]). */
    fun alpha(nowNs: Long): Float = bracket(nowNs).second

    /** Index of the newer snapshot of the drawn pair and the blend factor toward it. */
    private fun bracket(nowNs: Long): Pair<Int, Float> {
        val n = buf.size
        if (n < 2) return Pair(n - 1, 1f)
        val renderNs = nowNs - delayNs
        for (i in 1 until n) {
            val a = buf[i - 1].stampNs; val b = buf[i].stampNs
            if (renderNs < b || i == n - 1) {
                val span = (b - a).coerceAtLeast(1L)
                return Pair(i, ((renderNs - a).toFloat() / span).coerceIn(0f, 1f))
            }
        }
        return Pair(n - 1, 1f)
    }

    /** Updates [rigs] for this frame and returns them (insertion order = snapshot order). */
    fun update(nowNs: Long): Collection<FishRig> {
        if (buf.isEmpty()) { rigs.clear(); return rigs.values }
        val (bi, t) = bracket(nowNs)
        val newer = buf[bi]
        val older = if (bi > 0) buf[bi - 1] else null
        for (r in rigs.values) r.touched = false
        for (p in newer.snap.organisms) {
            val rig = rigs.getOrPut(p.id) { FishRig(p.id) }
            // Teleports (respawn, reset) snap instead of sweeping across the tank.
            val prev = older?.byId?.get(p.id)?.takeIf { pp -> pp.spine.size >= 3 && p.spine.size >= 3 &&
                sq(pp.spine[0] - p.spine[0]) + sq(pp.spine[1] - p.spine[1]) + sq(pp.spine[2] - p.spine[2]) < 900f }
            rig.set(prev, p, t)
        }
        rigs.values.removeAll { !it.touched }
        return rigs.values
    }

    private fun sq(v: Float) = v * v

    companion object {
        /** Snapshots kept: the drawn pair plus one or two that arrived ahead of the render clock. */
        const val MAX_BUFFERED = 6
    }
}
