package com.reeflive.app.render

import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The aiptasia treatments played by [CareScene] ([CareAct.INJECT], [CareAct.LASER]) with the props of
 * models/pest_tools.glb (tools/blender/pests.py): each prop is authored with its working tip at the origin and its
 * body along +Y, so a pose is "tip at P, body pointing back along A" (the laser's beam mesh already runs along −Y,
 * from the lens toward the polyp). The tool comes down from above the water,
 * glides in along the polyp's open side (its rock normal tipped toward the viewer and up, so it never crosses the
 * rock it sits on), does its work and goes back out. GL thread only; allocation-free per frame.
 */
internal class PestScene {
    private enum class Node(val glb: String) {
        SYRINGE("Syringe"), TIP("SyringeTip"), FLANGE("SyringeFlange"), PLUNGER("SyringePlunger"), FILL("SyringeFill"),
        LASER("Laser"), BUTTON("LaserButton"), LENS("LaserLens"), BEAM("LaserBeam"),
    }

    private var bound: GpuModel? = null
    private val parts = arrayOfNulls<Array<DrawPart>>(Node.entries.size)
    private var n = 0
    private val node = IntArray(MAX)
    private val mats = FloatArray(MAX * 16)
    private val tint = FloatArray(MAX * 4)
    private val tinted = BooleanArray(MAX)
    private val m = FloatArray(16)
    private val tmp = FloatArray(16)
    /** Tool axis (unit, tip → handle) and the working point, this frame. */
    private val ax = FloatArray(3)
    private val tip = FloatArray(3)

    fun clear() { n = 0 }

    /** Where the tool works: the polyp's mouth, a little out from its foot along its axis. */
    private fun approach(j: CareJob) {
        val a = j.at ?: DEFAULT
        // out from the rock, toward the viewer (+z) and up: the open side of the polyp
        var x = a[3] * 0.6f; var y = a[4] * 0.6f + 0.55f; var z = a[5] * 0.6f + 0.45f
        val l = sqrt(x * x + y * y + z * z).coerceAtLeast(1e-4f)
        x /= l; y /= l; z /= l
        ax[0] = x; ax[1] = y; ax[2] = z
    }

    /** Point [d] cm out along the approach from the polyp's mouth. */
    private fun along(j: CareJob, d: Float, out: FloatArray) {
        val a = j.at ?: DEFAULT
        val mouthY = 1.8f   // ~ an adult polyp's oral disc above its foot (aiptasia.glb column 2.4 cm, leaned)
        out[0] = a[0] + a[3] * mouthY * 0.6f + ax[0] * d
        out[1] = a[1] + a[4] * mouthY * 0.6f + mouthY * 0.4f + ax[1] * d
        out[2] = a[2] + a[5] * mouthY * 0.6f + ax[2] * d
    }

    /**
     * Syringe ([CareJob.tint] = the product's colour): pops in above the water over the polyp, glides down its
     * approach, the tip touches the mouth, the plunger goes down a centimetre, out again. [t] seconds into the job.
     */
    fun inject(j: CareJob, t: Float) {
        approach(j)
        val pop = CareScene.popIn(t, 0f) * CareScene.popOut(t, 4.7f)
        // distance of the tip from the mouth: far above the water, in close, touching, back out
        val far = farDistance(j)
        val inward = CareScene.seg(t, 0.4f, 2.4f) - CareScene.seg(t, 3.9f, 4.8f)
        val touch = CareScene.seg(t, 2.4f, 2.8f) - CareScene.seg(t, 3.6f, 3.9f)
        val d = far + (1.5f - far) * inward - 1.3f * touch
        along(j, d, tip)
        val push = CareScene.seg(t, 2.8f, 3.5f)
        val r = cr(j.tint); val g = cg(j.tint); val b = cb(j.tint)
        val s = pop
        add(Node.TIP, 0f, s); add(Node.SYRINGE, 0f, s); add(Node.FLANGE, 0f, s)
        // plunger seal starts 5.6 cm up the barrel (6.5 ml of 7) and pushes ~1 cm; the fill shrinks with it
        val seal = 8.4f - 1.0f * push
        add(Node.PLUNGER, seal, s)
        add(Node.FILL, 3.2f, s, sy = (seal - 3.2f) * s, r = r, g = g, b = b, a = 1f)
    }

    /** Laser pen: comes in to 4 cm off the polyp, the beam flickers on for about a second, back out. */
    fun laser(j: CareJob, t: Float) {
        approach(j)
        val pop = CareScene.popIn(t, 0f) * CareScene.popOut(t, 4.1f)
        val far = farDistance(j)
        val inward = CareScene.seg(t, 0.4f, 2.0f) - CareScene.seg(t, 3.4f, 4.2f)
        val d = far + (4f - far) * inward
        along(j, d, tip)
        add(Node.LASER, 0f, pop); add(Node.LENS, 0f, pop); add(Node.BUTTON, 0f, pop)
        val on = CareScene.seg(t, 2.2f, 2.3f) - CareScene.seg(t, 3.2f, 3.3f)
        if (on > 0.01f) {
            val flicker = 0.75f + 0.25f * sin(t * 60f)
            add(Node.BEAM, 0f, pop, sy = d * on, r = 0.55f, g = 1f, b = 0.5f, a = 0.85f * flicker)
        }
    }

    /** How far up the approach the tool starts so it begins above the water (and the rim). */
    private fun farDistance(j: CareJob): Float {
        val a = j.at ?: DEFAULT
        val rise = Camera.WATER_Y + 6f - a[1]
        return max(8f, rise / max(ax[1], 0.35f))
    }

    /**
     * Queues [nd] with its origin at [tip] + axis × [offY] (cm along the tool), uniform scale [s] (and [sy] along
     * the tool when set). Basis: Y = tool axis, X/Z any perpendicular pair.
     */
    private fun add(nd: Node, offY: Float, s: Float, sy: Float = -1f, r: Float = -1f, g: Float = 0f, b: Float = 0f, a: Float = 1f) {
        if (n == MAX || s <= 0.01f) return
        val yx = ax[0]; val yy = ax[1]; val yz = ax[2]
        // X = normalize(up × Y) or world X when Y is vertical
        var xx = yz; var xy = 0f; var xz = -yx
        var l = sqrt(xx * xx + xz * xz)
        if (l < 1e-3f) { xx = 1f; xz = 0f; l = 1f }
        xx /= l; xz /= l
        val zx = xy * yz - xz * yy; val zy = xz * yx - xx * yz; val zz = xx * yy - xy * yx
        val o = n * 16
        val syy = if (sy >= 0f) sy else s
        mats[o] = xx * s; mats[o + 1] = xy * s; mats[o + 2] = xz * s; mats[o + 3] = 0f
        mats[o + 4] = yx * syy; mats[o + 5] = yy * syy; mats[o + 6] = yz * syy; mats[o + 7] = 0f
        mats[o + 8] = zx * s; mats[o + 9] = zy * s; mats[o + 10] = zz * s; mats[o + 11] = 0f
        mats[o + 12] = tip[0] + yx * offY * s; mats[o + 13] = tip[1] + yy * offY * s; mats[o + 14] = tip[2] + yz * offY * s; mats[o + 15] = 1f
        node[n] = nd.ordinal
        tinted[n] = r >= 0f
        if (r >= 0f) { tint[n * 4] = r; tint[n * 4 + 1] = g; tint[n * 4 + 2] = b; tint[n * 4 + 3] = a }
        n++
    }

    /** The mouth point of [j] (for the bursts): → [out]. */
    fun mouth(j: CareJob, out: FloatArray) { approach(j); along(j, 0.2f, out) }

    private fun bind(model: GpuModel) {
        if (bound === model) return
        bound = model
        for (nd in Node.entries) parts[nd.ordinal] = model.parts.filter { it.node.name == nd.glb }.toTypedArray()
    }

    fun draw(model: GpuModel, p: ShaderProgram, white: Texture, blended: Boolean): Int {
        if (n == 0) return 0
        bind(model)
        p.use()
        p.set1f("uLedDies", 0f)
        var calls = 0
        for (k in 0 until n) {
            val list = parts[node[k]] ?: continue
            for (part in list) {
                if (part.material.src.blended != blended) continue
                System.arraycopy(mats, k * 16, m, 0, 16)
                p.setMat4("uModel", if (part.identityWorld) m else M.mul(m, part.node.world, tmp))
                part.material.apply(p, white)
                if (tinted[k]) {
                    val o = k * 4
                    p.set4f("uBaseColor", lin(tint[o]), lin(tint[o + 1]), lin(tint[o + 2]), tint[o + 3])
                }
                part.mesh.draw()
                calls++
            }
        }
        return calls
    }

    companion object {
        const val MODEL = "models/pest_tools.glb"
        const val MAX = 12
        /** Where a job with no polyp works (the old aiptasia spot by the Kenya base; ActionFx FxSpots.AIPTASIA). */
        private val DEFAULT = floatArrayOf(64f, 16.5f, 21.4f, 0.22f, 0.8f, -0.56f)
        private fun cr(rgb: Int) = ((rgb shr 16) and 0xFF) / 255f
        private fun cg(rgb: Int) = ((rgb shr 8) and 0xFF) / 255f
        private fun cb(rgb: Int) = (rgb and 0xFF) / 255f
        private fun lin(c: Float): Float = if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
    }
}
