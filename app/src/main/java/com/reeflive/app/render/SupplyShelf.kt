package com.reeflive.app.render

import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Layout of `supply_shelf.glb` (tools/blender/supplies.py, ASSETS.md §5.1): a driftwood rack to the right of the
 * tank, three rows of supply props (row 0 feed on top, row 1 care, row 2 tools), each prop one node `Supply_<id>`.
 * World cm like the tank. Keep in sync with the script.
 */
object SupplyShelfLayout {
    const val MODEL = "models/supply_shelf.glb"
    const val NODE_PREFIX = "Supply_"
    const val X0 = 134f
    const val X1 = 236f
    /** Front edge of the boards and the props' centre line (z). */
    const val Z_FRONT = 24f
    const val Z_PROP = 13f
    /** Board tops of the rows: feed, care, tools. */
    val ROW_Y = floatArrayOf(36.4f, 17.4f, -3.6f)
    const val ROWS = 3

    /** Where the camera looks (x along the shelf, y up) when Supplies opens: the middle, feed and care rows. */
    const val START_X = 185f
    const val START_Y = 32f
    /** Look-point range (soft): the camera can look along the whole shelf and at every row. */
    const val LOOK_X_LO = 152f
    const val LOOK_X_HI = 218f
    const val LOOK_Y_LO = 4f
    const val LOOK_Y_HI = 44f

    /** Look height for a row (a little above its board, so the props sit in the middle of the frame). */
    fun rowLookY(row: Int): Float = ROW_Y[row.coerceIn(0, ROWS - 1)] + if (row >= 2) 8f else 6.5f
}

/**
 * The UI's handle on the supply shelf (any thread; the GL thread reads it once per frame). [setOpen] swaps the camera
 * between the tank and the shelf (a smooth glide, or a cut with `instant`), [focusRow] glides to one row, [locked]
 * item ids are drawn dimmed (they still report taps, so the UI can explain why), and [onPick] hears every prop the
 * user taps on the main thread: its id (`Supply_<id>` without the prefix) and the world point (cm) on top of it.
 */
class SupplyShelfControl {
    @Volatile var open: Boolean = false
        private set
    @Volatile var instant: Boolean = false
        private set
    @Volatile var locked: Set<String> = emptySet()
    @Volatile var onPick: ((item: String, x: Float, y: Float, z: Float) -> Unit)? = null
    private val rowRequest = AtomicInteger(-1)

    fun setOpen(on: Boolean, instant: Boolean) {
        this.instant = instant
        open = on
    }

    fun focusRow(row: Int) = rowRequest.set(row)

    internal fun takeRowRequest(): Int = rowRequest.getAndSet(-1)
}

/**
 * GL-thread side of the shelf: per-prop pick boxes, the lift / glow of the picked prop, and the draw. The shelf sits
 * outside the water, beside the tank, so it is drawn with the generic program and a soft self-glow (props stay
 * readable at night); the picked prop rises [LIFT_CM], grows a little and brightens.
 */
internal class SupplyShelfScene {
    private class Prop(val id: String, val box: FloatArray) {
        var lift = 0f
    }

    private var model: GpuModel? = null
    private val props = ArrayList<Prop>()
    private val byNode = HashMap<String, Prop>()
    private var picked: Prop? = null
    private var open = false
    private var time = 0f
    private val liftM = M.identity()
    private val modelM = FloatArray(16)
    private val tmp = FloatArray(3)

    /** Applies the UI's open / row requests to [cam]. */
    fun apply(c: SupplyShelfControl?, cam: Camera) {
        val on = c?.open == true
        if (on != open) {
            open = on
            cam.lookAtShelf(on, c?.instant == true)
            if (!on) picked = null
        }
        val row = c?.takeRowRequest() ?: -1
        if (row >= 0 && on) cam.shelfFocusRow(row)
    }

    /** Builds the prop boxes the first time [m] is seen (and after a context loss re-uploads it). */
    fun bind(m: GpuModel) {
        if (m === model) return
        model = m
        props.clear(); byNode.clear(); picked = null
        for (part in m.parts) {
            val name = part.node.name
            if (!name.startsWith(SupplyShelfLayout.NODE_PREFIX)) continue
            val prop = byNode.getOrPut(name) {
                Prop(name.removePrefix(SupplyShelfLayout.NODE_PREFIX), floatArrayOf(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)).also { props += it }
            }
            for (pr in part.node.primitives) for (cx in 0..1) for (cy in 0..1) for (cz in 0..1) {
                M.transformPoint(part.node.world, if (cx == 0) pr.bmin[0] else pr.bmax[0], if (cy == 0) pr.bmin[1] else pr.bmax[1], if (cz == 0) pr.bmin[2] else pr.bmax[2], tmp)
                for (k in 0 until 3) { prop.box[k] = min(prop.box[k], tmp[k]); prop.box[k + 3] = max(prop.box[k + 3], tmp[k]) }
            }
        }
    }

    /** Eases every prop toward its lift (1 for the picked one). */
    fun update(dt: Float) {
        time = (time + dt) % 4096f
        val a = 1f - exp(-LIFT_RATE * dt)
        for (p in props) {
            val goal = if (p === picked) 1f else 0f
            p.lift += (goal - p.lift) * a
            if (kotlin.math.abs(goal - p.lift) < 1e-3f) p.lift = goal
        }
    }

    /**
     * Ray (origin [o], unit [d]) against the props' boxes (a little padded so small props are easy to hit): picks
     * and lifts the nearest, and returns its id and top-centre point, or null.
     */
    fun pick(o: FloatArray, d: FloatArray, out: FloatArray): String? {
        var best: Prop? = null
        var bestT = Float.MAX_VALUE
        for (p in props) {
            val t = rayBox(o, d, p.box, PICK_PAD) ?: continue
            if (t < bestT) { bestT = t; best = p }
        }
        val b = best ?: return null
        picked = b
        out[0] = (b.box[0] + b.box[3]) * 0.5f
        out[1] = b.box[4]
        out[2] = (b.box[2] + b.box[5]) * 0.5f
        return b.id
    }

    /** Draws the shelf with [p] (the generic program). Returns the number of draw calls. */
    fun draw(m: GpuModel, p: ShaderProgram, white: Texture, locked: Set<String>): Int {
        bind(m)
        p.use()
        var calls = 0
        for (part in m.parts) {
            if (part.material.blended) continue
            val prop = byNode[part.node.name]
            val lift = prop?.lift ?: 0f
            val dim = prop != null && prop.id in locked
            p.setMat4("uModel", if (lift > 0f && prop != null) lifted(prop, lift, part.node.world) else part.node.world)
            part.material.apply(p, white)
            val c = part.material.src.baseColor
            val k = if (dim) DIM else 1f
            if (dim) p.set4f("uBaseColor", c[0] * k + GREY, c[1] * k + GREY, c[2] * k + GREY, c.getOrElse(3) { 1f })
            // self-glow so the shelf reads at night, brighter (and gently pulsing) on the picked prop
            val glow = (if (dim) GLOW_DIM else GLOW) + lift * (PICK_GLOW + 0.08f * sin(time * 7f))
            p.set3f("uEmissive", c[0] * k * glow + lift * 0.05f, c[1] * k * glow + lift * 0.05f, c[2] * k * glow + lift * 0.05f)
            part.mesh.draw()
            calls++
        }
        return calls
    }

    /** The prop's node matrix raised by [lift] × [LIFT_CM] and scaled about its base centre. */
    private fun lifted(prop: Prop, lift: Float, world: FloatArray): FloatArray {
        val s = 1f + SCALE_UP * lift
        val bx = (prop.box[0] + prop.box[3]) * 0.5f; val by = prop.box[1]; val bz = (prop.box[2] + prop.box[5]) * 0.5f
        liftM[0] = s; liftM[5] = s; liftM[10] = s
        liftM[12] = bx * (1f - s)
        liftM[13] = by * (1f - s) + LIFT_CM * lift
        liftM[14] = bz * (1f - s)
        return M.mul(liftM, world, modelM)
    }

    companion object {
        const val LIFT_CM = 2.6f
        const val SCALE_UP = 0.1f
        const val LIFT_RATE = 12f
        const val PICK_PAD = 0.8f
        const val GLOW = 0.32f
        const val GLOW_DIM = 0.12f
        const val PICK_GLOW = 0.35f
        /** Locked (sandbox-only in Watch mode) props: colour × DIM + GREY, a faded grey-blue. */
        const val DIM = 0.35f
        const val GREY = 0.06f

        /** Slab test: entry t (≥ 0) of the ray into the box padded by [pad], or null. */
        fun rayBox(o: FloatArray, d: FloatArray, box: FloatArray, pad: Float): Float? {
            var t0 = 0f; var t1 = Float.MAX_VALUE
            for (k in 0 until 3) {
                val lo = box[k] - pad; val hi = box[k + 3] + pad
                if (kotlin.math.abs(d[k]) < 1e-6f) {
                    if (o[k] < lo || o[k] > hi) return null
                    continue
                }
                var a = (lo - o[k]) / d[k]; var b = (hi - o[k]) / d[k]
                if (a > b) { val s = a; a = b; b = s }
                t0 = max(t0, a); t1 = min(t1, b)
                if (t0 > t1) return null
            }
            return t0
        }
    }
}
