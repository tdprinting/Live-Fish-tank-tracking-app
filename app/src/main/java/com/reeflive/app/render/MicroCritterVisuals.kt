package com.reeflive.app.render

import com.reeflive.core.geom.Sdf
import com.reeflive.core.sim.CritterField
import com.reeflive.core.sim.CritterKind
import com.reeflive.core.sim.SimSnapshot

/**
 * The visible microfauna in the 3D tank (core [CritterField]): bristleworms, amphipods, mysids, isopods, pods on the
 * glass, micro-snails, red planaria and spaghetti-worm tentacles, driven by the snapshot's aggregate microfauna. They
 * are drawn with the organism (spline-skinned) shaders like the inverts but never picked or followed. Nearest first,
 * skipping any smaller than a few pixels, until [triBudget] model triangles are spent (tools/validate_glb.py
 * MICRO_TRIS_*). GL thread only.
 */
class MicroCritterVisuals {
    private var field: CritterField? = null
    private val rigs = HashMap<String, FishRig>()
    private val drawn = ArrayList<FishRig>()
    private val order = ArrayList<FishRig>()
    private var fishHeads = FloatArray(0)

    /** Rigs to draw this frame (already culled and budgeted). */
    val visible: List<FishRig> get() = drawn

    fun update(s: SimSnapshot?, sdf: Sdf?, dt: Float, camera: Camera, quality: RenderQuality, triBudget: Int, tris: (CritterKind) -> Int) {
        drawn.clear()
        if (s == null || sdf == null || quality == RenderQuality.LOW) return
        val f = field ?: CritterField(sdf).also { field = it }
        if (fishHeads.size != s.organisms.size * 3) fishHeads = FloatArray(s.organisms.size * 3)
        for ((i, o) in s.organisms.withIndex()) if (o.spine.size >= 3) {
            fishHeads[i * 3] = o.spine[0]; fishHeads[i * 3 + 1] = o.spine[1]; fishHeads[i * 3 + 2] = o.spine[2]
        }
        val speed = s.speed.coerceIn(1.0, 4.0)
        val poses = f.update(
            s.microfauna, dt * speed, focusX = camera.target[0].toDouble(),
            qualityScale = if (quality == RenderQuality.HIGH) 1.0 else 0.6,
            food = s.food, fishHeads = fishHeads, sand = s.sandGrid,
        )
        for (r in rigs.values) r.touched = false
        order.clear()
        for (p in poses) {
            val r = rigs.getOrPut(p.id) { FishRig(p.id) }
            r.setCritter(p)
            order += r
        }
        rigs.values.removeAll { !it.touched }
        val ex = camera.eye[0]; val ey = camera.eye[1]; val ez = camera.eye[2]
        order.sortBy { it.distanceTo(ex, ey, ez) }
        var spent = 0
        for (r in order) {
            val d = r.distanceTo(ex, ey, ez).coerceAtLeast(1f)
            if (r.scale / d < MIN_ANGULAR_SIZE) continue
            val k = CritterKind.of(r.speciesId) ?: continue
            val t = tris(k)
            if (spent + t > triBudget) break
            spent += t
            drawn += r
        }
    }

    fun release() { field = null; rigs.clear(); drawn.clear(); order.clear() }

    companion object {
        /** Body length / distance below which a critter is a sub-pixel speck at phone resolution (~3 px at 60° FOV). */
        const val MIN_ANGULAR_SIZE = 0.0035f
        const val MICRO_TRIS_HIGH = 12000
        const val MICRO_TRIS_MEDIUM = 2000
    }
}
