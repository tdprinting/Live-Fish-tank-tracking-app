package com.reeflive.app.render

import com.reeflive.core.geom.Sdf
import com.reeflive.core.geom.Vec3
import com.reeflive.core.sim.BerghiaField
import com.reeflive.core.sim.CritterField
import com.reeflive.core.sim.CritterKind
import com.reeflive.core.sim.MicroGroup
import com.reeflive.core.sim.MicroScene
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
    private var berghia: BerghiaField? = null
    private val rigs = HashMap<String, FishRig>()
    private val drawn = ArrayList<FishRig>()
    private val order = ArrayList<FishRig>()
    private var fishHeads = FloatArray(0)

    /** Rigs to draw this frame (already culled and budgeted). */
    val visible: List<FishRig> get() = drawn

    /**
     * Animals too small for a model, drawn by rock.frag where the rock is close enough to resolve them (uMicro): x
     * harpacticoid copepods, y ostracods, z forams, per cm² of rock in view (MicrofaunaState.visible). Zero on Low.
     */
    val rockLife = FloatArray(3)

    fun update(s: SimSnapshot?, sdf: Sdf?, dt: Float, camera: Camera, quality: RenderQuality, triBudget: Int, tris: (CritterKind) -> Int) {
        drawn.clear()
        if (s == null || quality == RenderQuality.LOW) { rockLife.fill(0f) } else {
            val m = s.microfauna
            rockLife[0] = m.visible(MicroGroup.HARPACTICOID, MicroScene.ROCK).toFloat().coerceIn(0f, MAX_ROCK_DENSITY)
            rockLife[1] = m.visible(MicroGroup.OSTRACOD, MicroScene.ROCK).toFloat().coerceIn(0f, MAX_ROCK_DENSITY)
            rockLife[2] = m.visible(MicroGroup.FORAM, MicroScene.ROCK).toFloat().coerceIn(0f, MAX_ROCK_DENSITY)
        }
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
        // the Berghia nudibranchs the keeper added crawl to the aiptasia the sim says they are eating
        if (s.berghia > 0f || berghia != null) {
            val bf = berghia ?: BerghiaField(sdf).also { berghia = it }
            var prey: Vec3? = null; var preyN: Vec3? = null
            val pid = s.berghiaPrey
            if (pid != null) for (c in s.corals) if (c.id == pid) {
                prey = Vec3(c.restX.toDouble(), c.restY.toDouble(), c.restZ.toDouble())
                preyN = Vec3(c.restNx.toDouble(), c.restNy.toDouble(), c.restNz.toDouble())
                break
            }
            for (p in bf.update(s.berghia, prey, preyN, s.dayPhase == "Night", (dt * speed).toDouble())) {
                val r = rigs.getOrPut(p.id) { FishRig(p.id) }
                r.setCritter(p)
                order += r
            }
        }
        rigs.values.removeAll { !it.touched }
        val ex = camera.eye[0]; val ey = camera.eye[1]; val ez = camera.eye[2]
        order.sortBy { it.distanceTo(ex, ey, ez) }
        var spent = 0
        for (r in order) {
            val d = r.distanceTo(ex, ey, ez).coerceAtLeast(1f)
            val ang = r.scale / d
            if (ang < MIN_ANGULAR_SIZE) continue
            // fade into the water as it shrinks toward a speck instead of popping out at the cutoff
            r.fade = 1f - ((ang - MIN_ANGULAR_SIZE) / (FADE_ANGULAR_SIZE - MIN_ANGULAR_SIZE)).coerceIn(0f, 1f)
            val k = CritterKind.of(r.speciesId) ?: continue
            val t = tris(k)
            if (spent + t > triBudget) break
            spent += t
            drawn += r
        }
    }

    fun release() { field = null; berghia = null; rigs.clear(); drawn.clear(); order.clear() }

    companion object {
        /** Body length / distance below which a critter is a sub-pixel speck at phone resolution (~3 px at 60° FOV). */
        const val MIN_ANGULAR_SIZE = 0.0035f
        /** Angular size above which a critter shows at full contrast (fully faded at [MIN_ANGULAR_SIZE]). */
        const val FADE_ANGULAR_SIZE = 0.009f
        /** Cap on the per-cm² rock densities handed to the shader (one animal per lattice cell is its own limit). */
        const val MAX_ROCK_DENSITY = 8f
        const val MICRO_TRIS_HIGH = 12000
        const val MICRO_TRIS_MEDIUM = 2000
    }
}
