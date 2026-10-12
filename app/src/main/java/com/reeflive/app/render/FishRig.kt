package com.reeflive.app.render

import com.reeflive.core.sim.OrganismPose
import com.reeflive.core.sim.SimSnapshot
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
        eyeYaw = lerp(pa.eyeYaw, b.eyeYaw, t); appendage = lerpPhase(pa.appendagePhase, b.appendagePhase, t)
        extension = lerp(pa.extension, b.extension, t); mouth = lerp(pa.mouth, b.mouth, t)
        alive = b.alive; visible = b.visible; posture = b.posture
        touched = true
    }

    /** Distance from a point to the head (cm), for LOD selection. */
    fun distanceTo(x: Float, y: Float, z: Float): Float {
        val dx = spine[0] - x; val dy = spine[1] - y; val dz = spine[2] - z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    companion object {
        fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

        /** Phases are in cycles and may wrap (e.g. mod 1000); interpolate across the wrap. */
        fun lerpPhase(a: Float, b: Float, t: Float): Float {
            var d = b - a
            if (d > 500f) d -= 1000f
            if (d < -500f) d += 1000f
            return a + d * t
        }

        /** The pose's model variant (darwin_black clown, …) when the core exposes one. */
        fun variantOf(p: OrganismPose): String? = p.variant
    }
}

/**
 * Keeps the last two distinct snapshots and blends organisms between them. Feed it once per frame with
 * whatever the provider returns; [alpha] advances with wall time over the measured snapshot interval.
 */
class SnapshotInterpolator {
    var previous: SimSnapshot? = null; private set
    var current: SimSnapshot? = null; private set
    private var currentAtNs = 0L
    private var intervalNs = 50_000_000L
    val rigs = LinkedHashMap<String, FishRig>()

    fun push(s: SimSnapshot?, nowNs: Long) {
        if (s == null || s === current) return
        if (current != null && s.tick != 0L && s.tick == current!!.tick && s.simTimeSec == current!!.simTimeSec) return
        if (current != null) {
            val dt = nowNs - currentAtNs
            if (dt in 5_000_000L..500_000_000L) intervalNs = (intervalNs * 0.8 + dt * 0.2).toLong()
        }
        previous = current
        current = s
        currentAtNs = nowNs
    }

    fun alpha(nowNs: Long): Float = ((nowNs - currentAtNs).toFloat() / intervalNs).coerceIn(0f, 1f)

    /** Updates [rigs] for this frame and returns them (insertion order = snapshot order). */
    fun update(nowNs: Long): Collection<FishRig> {
        val cur = current ?: return emptyList<FishRig>().also { rigs.clear() }
        val t = alpha(nowNs)
        val prevById = previous?.organisms?.associateBy { it.id } ?: emptyMap()
        for (r in rigs.values) r.touched = false
        for (p in cur.organisms) {
            val rig = rigs.getOrPut(p.id) { FishRig(p.id) }
            // Teleports (respawn, reset) snap instead of sweeping across the tank.
            val prev = prevById[p.id]?.takeIf { pp -> pp.spine.size >= 3 && p.spine.size >= 3 &&
                sq(pp.spine[0] - p.spine[0]) + sq(pp.spine[1] - p.spine[1]) + sq(pp.spine[2] - p.spine[2]) < 900f }
            rig.set(prev, p, t)
        }
        rigs.values.removeAll { !it.touched }
        return rigs.values
    }

    private fun sq(v: Float) = v * v
}
