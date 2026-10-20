package com.reeflive.app.render

import com.reeflive.core.sim.CoralKind
import com.reeflive.core.sim.SimSnapshot
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Aiptasia polyps: one models/aiptasia.glb polyp per AIPTASIA coral, stood on its rock spot (tools/blender/pests.py).
 * The sim's numbers become the look: [com.reeflive.core.sim.CoralPose.size] grows a lacerate into an adult,
 * extension pulls it down toward its pedal disc (touched, being eaten, dosed), tissue shrivels it while a treatment or
 * a Berghia finishes it, bleach pales it (Aiptasia-X and the calcium hydroxide pastes coat it white). Values ease per
 * frame so a 20 Hz snapshot (or a 60 s chem tick) reads as a smooth flinch; a polyp gone from the snapshot shrinks
 * away instead of popping. GL thread only; nothing allocates per frame once the polyps are known.
 */
internal class AiptasiaDraw {
    class Polyp(val id: String) {
        var x = 0f; var y = 0f; var z = 0f
        var nx = 0f; var ny = 1f; var nz = 0f
        var phase = 0f
        var rockPiece = -1
        var flow = 8f
        // targets from the snapshot and the eased values drawn
        var tExt = 1f; var tSize = 1f; var tTissue = 1f; var tBleach = 0f
        var ext = 0f; var size = 0f; var tissue = 1f; var bleach = 0f
        var gone = false
        var seen = false
        /** Model matrix (column-major) built by [matrix]. */
        val m = FloatArray(16)
        /** Visible height (cm) this frame, for the sway amplitude. */
        var height = 0f
    }

    private val byId = HashMap<String, Polyp>()
    val polyps = ArrayList<Polyp>()
    private var forSnap: SimSnapshot? = null
    private val isAiptasia = HashMap<String, Boolean>()

    fun isAiptasia(speciesId: String): Boolean = isAiptasia.getOrPut(speciesId) { CoralKind.resolve(speciesId) == CoralKind.AIPTASIA }

    fun update(s: SimSnapshot?, dt: Float) {
        if (s !== forSnap) {
            forSnap = s
            for (p in polyps) p.seen = false
            if (s != null) for (c in s.corals) {
                if (!isAiptasia(c.speciesId)) continue
                val p = byId.getOrPut(c.id) { Polyp(c.id).also { polyps += it; it.size = c.size * 0.3f } }
                p.seen = true; p.gone = false
                p.x = c.restX; p.y = c.restY; p.z = c.restZ
                p.nx = c.restNx; p.ny = c.restNy; p.nz = c.restNz
                p.phase = c.swayPhase; p.rockPiece = c.rockPiece; p.flow = c.flow
                p.tExt = c.extension; p.tSize = c.size; p.tTissue = c.tissue; p.tBleach = c.bleach
            }
            for (p in polyps) if (!p.seen) { p.gone = true; p.tTissue = 0f }
        }
        // flinch shut fast (a few seconds), open slowly; grow and shrivel smoothly
        val fast = 1f - exp(-dt / 0.8f); val slow = 1f - exp(-dt / 6f); val mid = 1f - exp(-dt / 2f)
        var i = polyps.size - 1
        while (i >= 0) {
            val p = polyps[i]
            p.ext += (p.tExt - p.ext) * (if (p.tExt < p.ext) fast else slow)
            p.size += (p.tSize - p.size) * mid
            p.tissue += (p.tTissue - p.tissue) * mid
            p.bleach += (p.tBleach - p.bleach) * mid
            if (p.gone && p.tissue < 0.03f) { polyps.removeAt(i); byId.remove(p.id) }
            i--
        }
    }

    /**
     * [p]'s model matrix: Y along its foot's normal (leaning a little toward the light), turned by its sway phase,
     * scaled by size, retraction (down more than across) and shrivel.
     */
    fun matrix(p: Polyp): FloatArray {
        // axis: the rock normal bent a third of the way toward straight up
        var ax = p.nx; var ay = p.ny + 0.35f; var az = p.nz
        var l = sqrt(ax * ax + ay * ay + az * az).coerceAtLeast(1e-4f)
        ax /= l; ay /= l; az /= l
        // a side axis perpendicular to it, spun by the sway phase
        var sx = ay; var sy = -ax; var sz = 0f
        if (ax * ax + ay * ay < 0.04f) { sx = 0f; sy = az; sz = -ay }
        l = sqrt(sx * sx + sy * sy + sz * sz).coerceAtLeast(1e-4f); sx /= l; sy /= l; sz /= l
        val c = cos(p.phase); val s = sin(p.phase)
        // rotate the side axis about the main axis: v cos + (a × v) sin
        val cx = ay * sz - az * sy; val cy = az * sx - ax * sz; val cz = ax * sy - ay * sx
        val ux = sx * c + cx * s; val uy = sy * c + cy * s; val uz = sz * c + cz * s
        val wx = uy * az - uz * ay; val wy = uz * ax - ux * az; val wz = ux * ay - uy * ax
        val grown = 0.3f + 0.7f * p.size.coerceIn(0f, 1.2f)
        val shrivel = 0.25f + 0.75f * p.tissue.coerceIn(0f, 1f)
        val e = p.ext.coerceIn(0f, 1f)
        val along = grown * shrivel * (0.22f + 0.78f * e)
        val across = grown * shrivel * (0.55f + 0.45f * e)
        p.height = 2.4f * along
        val m = p.m
        m[0] = ux * across; m[1] = uy * across; m[2] = uz * across; m[3] = 0f
        m[4] = ax * along; m[5] = ay * along; m[6] = az * along; m[7] = 0f
        m[8] = wx * across; m[9] = wy * across; m[10] = wz * across; m[11] = 0f
        // the pedal disc sits a hair into the rock so no gap shows under a lean
        m[12] = p.x - p.nx * 0.15f; m[13] = p.y - p.ny * 0.15f; m[14] = p.z - p.nz * 0.15f; m[15] = 1f
        return m
    }

    fun release() { byId.clear(); polyps.clear(); forSnap = null }

    companion object {
        const val MODEL = "models/aiptasia.glb"
    }
}
