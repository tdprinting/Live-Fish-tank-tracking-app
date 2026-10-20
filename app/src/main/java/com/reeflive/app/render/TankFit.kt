package com.reeflive.app.render

import com.reeflive.core.sim.TankFilter
import com.reeflive.core.sim.TankShape
import kotlin.math.sqrt

/**
 * The tank shell (tank.glb) and its equipment (equipment.glb) drawn at the size and look of the tank on screen
 * ([Camera.shape]; My tank builds come in four sizes, all modelled at Tee's 48 × 18 × 16 in).
 *
 * - Shell: every vertex goes through the [TankShape] nine-slice, so the panes keep their thickness, the rim its
 *   profile, the stand top its overhang and the sand bed its slope, while the box grows or shrinks to the build. A
 *   rimless build leaves the rim out. Built once per tank shape (new meshes, released on the next refit).
 * - Equipment: each piece moves rigidly with the glass or rim it hangs from (the shift of its top centre, or its centre
 *   for pieces low on the glass), sharing the loaded meshes. Pieces the build does not have (a hang-on filter on an
 *   all-in-one or sumped tank, a hang-on skimmer) are left out. The fixtures' light positions follow their lamps.
 *
 * Tee's own tank returns the loaded models unchanged. GL thread.
 */
class TankFit {
    private var shellFor: GpuModel? = null
    private var shellShape: TankShape? = null
    private var shell: GpuModel? = null
    private var gearFor: GpuModel? = null
    private var gearShape: TankShape? = null
    private var gear: GpuModel? = null

    /** The shell for [shape], or [base] itself for Tee's tank. */
    fun tank(base: GpuModel?, shape: TankShape): GpuModel? {
        if (base == null) return null
        if (shape.isStock && !shape.rimless) return base
        if (base !== shellFor || shape != shellShape) {
            shell?.parts?.forEach { it.mesh.release() }
            shell = fitShell(base, shape); shellFor = base; shellShape = shape
        }
        return shell
    }

    /** The equipment for [shape], or [base] itself for Tee's tank with Tee's filter. */
    fun equipment(base: GpuModel?, shape: TankShape): GpuModel? {
        if (base == null) return null
        if (base !== gearFor || shape != gearShape) {
            gear = if (shape.isStock && shape.filter == TankFilter.HOB && shape.skimmer) base else fitGear(base, shape)
            gearFor = base; gearShape = shape
            placeLights(gear ?: base)
        }
        return gear
    }

    private val panes = HashMap<DrawPart, Pair<TankShape, Mesh>>()

    /**
     * A part painted on a glass pane in a scene model (reef_details, algae_tufts: film, strands, the magnet cleaner),
     * stretched with the glass for [shape]: drawn with an identity model matrix. Cached per part.
     */
    fun pane(part: DrawPart, shape: TankShape): Mesh? {
        panes[part]?.let { (s, m) -> if (s == shape) return m else m.release() }
        val prim = part.prim ?: return null
        val v = stretch(prim, part.node.world, shape, null, null)
        val mesh = Mesh.create(v, prim.indices)
        panes[part] = shape to mesh
        return mesh
    }

    fun release() {
        panes.values.forEach { it.second.release() }
        panes.clear()
        shell?.parts?.forEach { it.mesh.release() }
        shell = null; shellFor = null; shellShape = null
        gear = null; gearFor = null; gearShape = null
    }

    private fun fitShell(base: GpuModel, s: TankShape): GpuModel {
        val parts = ArrayList<DrawPart>()
        for (part in base.parts) {
            if (s.rimless && part.part == "rim") continue
            val prim = part.prim ?: continue
            val lo = floatArrayOf(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE)
            val hi = floatArrayOf(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
            val v = stretch(prim, part.node.world, s, lo, hi)
            val fitted = GlbPrimitive(v, prim.indices, prim.material, lo, hi)
            val node = GlbNode(part.node.name, part.node.part, part.node.extras, M.identity(), listOf(fitted))
            // the sand bed is lifted per vertex by the sim's grid (SandFx): subdivided as the loader does
            val mesh = if (part.kind == MaterialKind.SAND) Mesh.subdivide(v, prim.indices) { _, y, _ -> y > SAND_LIFT }.let { (sv, si) -> Mesh.create(sv, si) }
                else Mesh.create(v, prim.indices)
            val c = part.center
            val center = floatArrayOf(s.mapX(c[0].toDouble()).toFloat(), s.mapY(c[1].toDouble()).toFloat(), s.mapZ(c[2].toDouble()).toFloat())
            parts += DrawPart(node, mesh, part.material, part.kind, center, fitted)
        }
        return GpuModel(base.path, base.glb, parts, emptyList())
    }

    /** [prim]'s vertices in world space ([w]) through the nine-slice of [s], normals bent to match; bounds into [lo]/[hi]. */
    private fun stretch(prim: GlbPrimitive, w: FloatArray, s: TankShape, lo: FloatArray?, hi: FloatArray?): FloatArray {
        val tmp = FloatArray(3)
        val src = prim.vertices
        val v = src.copyOf()
        val stride = GlbPrimitive.STRIDE
        for (i in 0 until prim.vertexCount) {
            val o = i * stride
            M.transformPoint(w, src[o], src[o + 1], src[o + 2], tmp)
            val x = tmp[0].toDouble(); val y = tmp[1].toDouble(); val z = tmp[2].toDouble()
            v[o] = s.mapX(x).toFloat(); v[o + 1] = s.mapY(y).toFloat(); v[o + 2] = s.mapZ(z).toFloat()
            // normals: the node's rotation, then the inverse of the local stretch
            val nx0 = w[0] * src[o + 3] + w[4] * src[o + 4] + w[8] * src[o + 5]
            val ny0 = w[1] * src[o + 3] + w[5] * src[o + 4] + w[9] * src[o + 5]
            val nz0 = w[2] * src[o + 3] + w[6] * src[o + 4] + w[10] * src[o + 5]
            val nx = nx0 / s.scaleX(x).toFloat(); val ny = ny0 / s.scaleY(y).toFloat(); val nz = nz0 / s.scaleZ(z).toFloat()
            val l = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-6f)
            v[o + 3] = nx / l; v[o + 4] = ny / l; v[o + 5] = nz / l
            if (lo != null && hi != null) for (k in 0 until 3) { lo[k] = minOf(lo[k], v[o + k]); hi[k] = maxOf(hi[k], v[o + k]) }
        }
        return v
    }

    private fun fitGear(base: GpuModel, s: TankShape): GpuModel {
        val shifts = HashMap<String, FloatArray>()
        val parts = ArrayList<DrawPart>()
        for (part in base.parts) {
            val name = part.node.name
            if (!has(name, s)) continue
            val lead = leader(name)
            val sh = shifts.getOrPut(lead) { shiftOf(base.parts.firstOrNull { it.node.name == lead } ?: part, s) }
            val world = M.mul(M.trs(sh, floatArrayOf(0f, 0f, 0f, 1f), floatArrayOf(1f, 1f, 1f)), part.node.world)
            val node = GlbNode(name, part.node.part, part.node.extras, world, part.node.primitives)
            val c = part.center
            parts += DrawPart(node, part.mesh, part.material, part.kind, floatArrayOf(c[0] + sh[0], c[1] + sh[1], c[2] + sh[2]), part.prim)
        }
        return GpuModel(base.path, base.glb, parts, emptyList())
    }

    /** Lens light positions ([LightingState.LIGHT_POS]) from the lamps as drawn. */
    private fun placeLights(m: GpuModel) {
        for (i in LIGHT_LENSES.indices) {
            val lens = m.parts.firstOrNull { it.node.name == LIGHT_LENSES[i] } ?: continue
            val p = lens.node.vec3("lightPos") ?: continue
            val w = lens.node.world
            // the lens' authored position, carried by the lamp's shift (translation column of its world)
            LightingState.LIGHT_POS[i][0] = p[0] + w[12]; LightingState.LIGHT_POS[i][1] = p[1] + w[13]; LightingState.LIGHT_POS[i][2] = p[2] + w[14]
        }
    }

    /** How far a piece moves: its top centre (hung from the rim) or, low on the glass, its centre, through the stretch. */
    private fun shiftOf(part: DrawPart, s: TankShape): FloatArray {
        val b = part.worldBounds
        val cx = (b[0] + b[3]) / 2.0; val cz = (b[2] + b[5]) / 2.0
        val ay = if (b[4] < LOW_PIECE_TOP_CM) (b[1] + b[4]) / 2.0 else b[4].toDouble()
        return floatArrayOf((s.mapX(cx) - cx).toFloat(), (s.mapY(ay) - ay).toFloat(), (s.mapZ(cz) - cz).toFloat())
    }

    companion object {
        /** Sand vertices above this are lifted by the sand map (as AssetStore's SAND_LIFT_Y). */
        private const val SAND_LIFT = 0.5f
        /** A piece whose top is below this (cm) sits low on the glass (the front magnet) and keeps its height. */
        private const val LOW_PIECE_TOP_CM = 20f
        private val LIGHT_LENSES = arrayOf("AiPrimeL_Lens", "AiPrimeR_Lens")
        private val HOB_PARTS = setOf("HobFilter", "IntakeTube", "ReturnPipe")

        /** Whether the build has this piece of equipment.glb. */
        fun has(name: String, s: TankShape): Boolean = when (name) {
            in HOB_PARTS -> s.filter == TankFilter.HOB
            "Skimmer" -> s.skimmer
            else -> true
        }

        /** The piece a small part moves with (a lens with its lamp, an indicator LED with its box). */
        fun leader(name: String): String = when {
            name.endsWith("_Lens") -> name.removeSuffix("_Lens")
            name == "HeaterLED" -> "Heater"
            name == "AtoLED" -> "AtoBox"
            else -> name
        }
    }
}
