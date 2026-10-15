package com.reeflive.app.render

import com.reeflive.core.sim.BroodField
import com.reeflive.core.sim.CareKind
import com.reeflive.core.sim.NurseryTank
import kotlin.math.sin

/**
 * The nursery tank beside the display (core [NurseryTank]): a black-sided 10-gallon larval tank with a clear front,
 * green water (the rotifers' algae) and a small LED bar over it, drawn only while it holds larvae or fry. Procedural
 * boxes with the generic program; the young inside are [BroodVisuals]. GL thread only, allocation-free per frame.
 */
internal class NurseryScene {
    private var panels: Mesh? = null
    private var glass: Mesh? = null
    private var water: Mesh? = null
    private var led: Mesh? = null
    // the keeper's tools (unit-ish meshes placed by uModel) and a cloud of food specks in a unit sphere
    private var dropper: Mesh? = null
    private var bulb: Mesh? = null
    private var tube: Mesh? = null
    private var net: Mesh? = null
    private var specks: Mesh? = null
    private val model = FloatArray(16)

    fun release() {
        for (m in listOf(panels, glass, water, led, dropper, bulb, tube, net, specks)) m?.release()
        panels = null; glass = null; water = null; led = null; dropper = null; bulb = null; tube = null; net = null; specks = null
    }

    private class Builder {
        val v = ArrayList<Float>(); val i = ArrayList<Int>()
        private val count: Int get() = v.size / GlbPrimitive.STRIDE
        /** A quad from four corners (counter-clockwise seen from [n]). */
        fun quad(a: FloatArray, b: FloatArray, c: FloatArray, d: FloatArray, n: FloatArray) {
            val base = count
            for ((k, p) in listOf(a, b, c, d).withIndex()) {
                v += p[0]; v += p[1]; v += p[2]; v += n[0]; v += n[1]; v += n[2]
                v += if (k == 1 || k == 2) 1f else 0f; v += if (k >= 2) 1f else 0f; v += 1f; v += 1f; v += 1f; v += 1f
            }
            i += base; i += base + 1; i += base + 2; i += base; i += base + 2; i += base + 3
        }
        /** Axis-aligned box faces; [skip] names faces left out ("front", "top"). */
        fun box(x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float, skip: Set<String> = emptySet()) {
            fun p(x: Float, y: Float, z: Float) = floatArrayOf(x, y, z)
            if ("front" !in skip) quad(p(x0, y0, z1), p(x1, y0, z1), p(x1, y1, z1), p(x0, y1, z1), floatArrayOf(0f, 0f, 1f))
            if ("back" !in skip) quad(p(x1, y0, z0), p(x0, y0, z0), p(x0, y1, z0), p(x1, y1, z0), floatArrayOf(0f, 0f, -1f))
            if ("left" !in skip) quad(p(x0, y0, z0), p(x0, y0, z1), p(x0, y1, z1), p(x0, y1, z0), floatArrayOf(-1f, 0f, 0f))
            if ("right" !in skip) quad(p(x1, y0, z1), p(x1, y0, z0), p(x1, y1, z0), p(x1, y1, z1), floatArrayOf(1f, 0f, 0f))
            if ("top" !in skip) quad(p(x0, y1, z1), p(x1, y1, z1), p(x1, y1, z0), p(x0, y1, z0), floatArrayOf(0f, 1f, 0f))
            if ("bottom" !in skip) quad(p(x0, y0, z0), p(x1, y0, z0), p(x1, y0, z1), p(x0, y0, z1), floatArrayOf(0f, -1f, 0f))
        }
        fun mesh() = Mesh.create(v.toFloatArray(), i.toIntArray())
    }

    private fun ensure() {
        if (panels != null) return
        val x0 = NurseryTank.X0.toFloat(); val x1 = NurseryTank.X1.toFloat()
        val y0 = NurseryTank.Y0.toFloat(); val y1 = NurseryTank.Y1.toFloat()
        val z0 = NurseryTank.Z0.toFloat(); val z1 = NurseryTank.Z1.toFloat()
        val w = NurseryTank.WATER.toFloat()
        // black back, sides and bottom (painted glass; larvae strike clear walls), a thin black rim on top
        panels = Builder().apply {
            box(x0 - 0.4f, y0, z0 - 0.4f, x1 + 0.4f, y1, z0, setOf("front"))
            box(x0 - 0.4f, y0, z0, x0, y1, z1, setOf("left"))
            box(x1, y0, z0, x1 + 0.4f, y1, z1, setOf("right"))
            box(x0, y0 - 0.4f, z0, x1, y0, z1)
            box(x0 - 0.4f, y1 - 1.2f, z1, x1 + 0.4f, y1, z1 + 0.4f)
            box(x0 - 0.4f, y0, z1, x1 + 0.4f, y0 + 1.0f, z1 + 0.4f)
        }.mesh()
        glass = Builder().apply { quad(floatArrayOf(x0, y0 + 1f, z1 + 0.2f), floatArrayOf(x1, y0 + 1f, z1 + 0.2f), floatArrayOf(x1, y1 - 1.2f, z1 + 0.2f), floatArrayOf(x0, y1 - 1.2f, z1 + 0.2f), floatArrayOf(0f, 0f, 1f)) }.mesh()
        water = Builder().apply { box(x0 + 0.1f, y0 + 0.1f, z0 + 0.1f, x1 - 0.1f, w, z1 - 0.1f, setOf("back", "left", "right", "bottom")) }.mesh()
        // a small LED bar on legs over the water
        led = Builder().apply { box(x0 + 8f, y1 + 6f, (z0 + z1) / 2 - 3f, x1 - 8f, y1 + 7.5f, (z0 + z1) / 2 + 3f) }.mesh()
        // tools, tip at the origin, reaching up +y: a pipette with a rubber bulb, a siphon hose, a fry net on a handle
        dropper = Builder().apply { box(-0.25f, 0f, -0.25f, 0.25f, 12f, 0.25f) }.mesh()
        bulb = Builder().apply { box(-0.7f, 12f, -0.7f, 0.7f, 15f, 0.7f) }.mesh()
        tube = Builder().apply { box(-0.6f, 0f, -0.6f, 0.6f, 40f, 0.6f) }.mesh()
        net = Builder().apply {
            box(-0.2f, 2.5f, -0.2f, 0.2f, 36f, 0.2f)                     // handle
            box(-2.5f, 0f, -2.5f, 2.5f, 0.3f, 2.5f)                       // mesh bag bottom
            box(-2.5f, 0f, -2.5f, -2.3f, 2.5f, 2.5f); box(2.3f, 0f, -2.5f, 2.5f, 2.5f, 2.5f)
            box(-2.5f, 2.3f, -2.5f, 2.5f, 2.5f, 2.5f, setOf("bottom"))    // rim
        }.mesh()
        specks = Builder().apply {
            // fixed pseudo-random specks (golden-angle spiral) so the cloud never re-randomizes
            for (k in 0 until 70) {
                val f = (k + 0.5f) / 70f
                val r = kotlin.math.sqrt(f)
                val a = k * 2.39996f
                val y = (((k * 0.6180339f) % 1f) - 0.5f) * 1.6f
                val x = r * kotlin.math.cos(a); val z = r * sin(a)
                val e = 0.035f
                box(x - e, y - e, z - e, x + e, y + e, z + e)
            }
        }.mesh()
    }

    private fun place(x: Double, y: Double, z: Double, scale: Float) {
        model.fill(0f); model[0] = scale; model[5] = scale; model[10] = scale; model[15] = 1f
        model[12] = x.toFloat(); model[13] = y.toFloat(); model[14] = z.toFloat()
    }

    /** The keeper's hand at work ([BroodField.care]): pipette, siphon hose or fry net. Returns draw calls. */
    private fun drawTool(p: ShaderProgram, f: BroodField): Int {
        val k = f.care ?: return 0
        place(f.tool[0], f.tool[1], f.tool[2], 1f)
        p.setMat4("uModel", model)
        return when (k) {
            CareKind.SIPHON -> { p.set4f("uBaseColor", 0.55f, 0.62f, 0.6f, 1f); p.set1f("uRoughness", 0.3f); tube?.draw(); 1 }
            CareKind.NET -> { p.set4f("uBaseColor", 0.15f, 0.35f, 0.25f, 1f); p.set1f("uRoughness", 0.7f); net?.draw(); 1 }
            else -> {
                p.set4f("uBaseColor", 0.85f, 0.9f, 0.92f, 1f); p.set1f("uRoughness", 0.1f); dropper?.draw()
                p.set4f("uBaseColor", 0.75f, 0.12f, 0.08f, 1f); p.set1f("uRoughness", 0.6f); bulb?.draw(); 2
            }
        }
    }

    /** Opaque parts (panels, rim, LED bar): with the other opaque scenery. Returns draw calls. */
    fun drawSolid(p: ShaderProgram, white: Texture, lightOn: Boolean, care: BroodField?): Int {
        ensure()
        p.use(); white.bind(0)
        p.setMat4("uModel", IDENTITY)
        p.set1f("uHasTexture", 0f); p.set1f("uAlphaCutoff", 0f); p.set1f("uLedDies", 0f)
        p.set3f("uEmissive", 0f, 0f, 0f)
        p.set4f("uBaseColor", 0.03f, 0.03f, 0.035f, 1f); p.set1f("uRoughness", 0.3f)
        panels?.draw()
        p.set4f("uBaseColor", 0.12f, 0.12f, 0.13f, 1f); p.set1f("uRoughness", 0.4f)
        if (lightOn) p.set3f("uEmissive", 0.9f, 0.95f, 1.0f)
        led?.draw()
        p.set3f("uEmissive", 0f, 0f, 0f)
        val tools = if (care != null) drawTool(p, care) else 0
        p.setMat4("uModel", IDENTITY)
        return 2 + tools
    }

    /** See-through parts (green water, front glass): after the display's water. [green] 0..1 rotifer greenwater. */
    fun drawClear(p: ShaderProgram, white: Texture, green: Float, lightOn: Boolean, care: BroodField?): Int {
        ensure()
        p.use(); white.bind(0)
        p.setMat4("uModel", IDENTITY)
        p.set1f("uHasTexture", 0f); p.set1f("uAlphaCutoff", 0f); p.set1f("uLedDies", 0f)
        val g = green.coerceIn(0f, 1f)
        val glow = if (lightOn) 1f else 0.25f
        p.set4f("uBaseColor", 0.35f - 0.15f * g, 0.6f + 0.15f * g, 0.55f - 0.2f * g, 0.12f + 0.22f * g)
        p.set3f("uEmissive", (0.01f + 0.01f * g) * glow, (0.03f + 0.05f * g) * glow, (0.03f + 0.01f * g) * glow)
        p.set1f("uRoughness", 0.1f)
        var calls = 0
        // the food cloud first (inside the water box): rotifers a pale haze, baby brine orange, pellets brown
        if (care != null && care.foodAlpha > 0) {
            place(care.food[0], care.food[1], care.food[2], care.foodRadius.toFloat())
            p.setMat4("uModel", model)
            val a = care.foodAlpha.toFloat()
            when (care.foodKind) {
                CareKind.BRINE -> p.set4f("uBaseColor", 1f, 0.45f, 0.15f, 0.9f * a)
                CareKind.PELLETS -> p.set4f("uBaseColor", 0.45f, 0.28f, 0.15f, 0.95f * a)
                else -> p.set4f("uBaseColor", 0.85f, 0.95f, 0.8f, 0.7f * a)
            }
            p.set3f("uEmissive", 0.02f * glow, 0.02f * glow, 0.015f * glow)
            specks?.draw(); calls++
            p.setMat4("uModel", IDENTITY)
            p.set4f("uBaseColor", 0.35f - 0.15f * g, 0.6f + 0.15f * g, 0.55f - 0.2f * g, 0.12f + 0.22f * g)
            p.set3f("uEmissive", (0.01f + 0.01f * g) * glow, (0.03f + 0.05f * g) * glow, (0.03f + 0.01f * g) * glow)
        }
        water?.draw()
        p.set4f("uBaseColor", 0.9f, 0.97f, 1f, 0.12f)
        p.set3f("uEmissive", 0.01f, 0.012f, 0.014f)
        p.set1f("uRoughness", 0.05f)
        glass?.draw()
        p.set3f("uEmissive", 0f, 0f, 0f)
        return 2 + calls
    }

    companion object {
        private val IDENTITY = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
    }
}
