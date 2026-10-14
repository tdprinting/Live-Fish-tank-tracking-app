package com.reeflive.app.render

import kotlinx.serialization.json.JsonPrimitive
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
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
    /** Board thickness and the underside of the top board (over the feed row). */
    const val BOARD = 2f
    const val TOP_Y = 52.4f
    const val ROWS = 3

    /** Where the camera looks (x along the shelf, y up) when Supplies opens: the middle, feed and care rows. */
    const val START_X = 185f
    const val START_Y = 32f
    /** Look-point range (soft): the camera can look along the whole shelf and at every row. */
    const val LOOK_X_LO = 152f
    const val LOOK_X_HI = 218f
    const val LOOK_Y_LO = 4f
    const val LOOK_Y_HI = 44f

    /** Inner x span of the boards: props stand evenly spaced along it, centred in equal slots (supplies.py prop_xs). */
    const val SLOT_X0 = 136.4f
    const val SLOT_X1 = 233.6f
    /** Props per row (feed, care, tools), in the order of the script's lists and of SupplyItem. */
    val ROW_COUNT = intArrayOf(13, 10, 5)
    /** Node of the lit "Supplies" sign on the crest (drawn brighter than the rest). */
    const val SIGN_NODE = "ShelfSign"

    /** Centre x of the [index]th of [count] props in a row. */
    fun propX(index: Int, count: Int): Float {
        val pitch = slotWidth(count)
        return SLOT_X0 + pitch * (index + 0.5f)
    }

    /** Width of one prop's slot (cm) in a row of [count]. */
    fun slotWidth(count: Int): Float = (SLOT_X1 - SLOT_X0) / max(count, 1)

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
    private val pickRequest = AtomicReference<String?>(null)

    fun setOpen(on: Boolean, instant: Boolean) {
        this.instant = instant
        open = on
    }

    fun focusRow(row: Int) = rowRequest.set(row)

    /** Picks prop [id] as if it was tapped (its name tag on screen was): it lifts, and [onPick] hears it. */
    fun pick(id: String) = pickRequest.set(id)

    internal fun takeRowRequest(): Int = rowRequest.getAndSet(-1)

    internal fun takePickRequest(): String? = pickRequest.getAndSet(null)
}

/**
 * GL-thread side of the shelf: per-prop pick boxes, the lift / glow of the picked prop, and the draw. The shelf sits
 * outside the water, beside the tank, so it is drawn with the shelf program (shelf.frag): its own room light rig
 * (key, fill, the warm pucks under the boards, the tank's spill), analytic soft shadows and contact occlusion from
 * the props' footprints ([foot] / [footY]), and per-material shading ([kindOf]). The room dims at night while the
 * sign and pucks stay lit; the picked prop rises [LIFT_CM], grows a little and brightens.
 */
internal class SupplyShelfScene {
    private class Prop(val id: String, val box: FloatArray) {
        var lift = 0f
        /** Footprint slot (rows feed, care, tools in slot order) in [foot] / [footY], or -1. */
        var index = -1
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
    /** Per part of [model]: its shading kind (KIND_*) and footprint slot (-1 for the rack). */
    private var kinds = FloatArray(0)
    private var selves = FloatArray(0)
    /** Per prop slot: centre x, centre z, half width, half depth; then base y, top y (lifted), lift, 0 (shelf.frag). */
    private val foot = FloatArray(4 * PROPS)
    private val footY = FloatArray(4 * PROPS)

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
        foot.fill(0f); footY.fill(0f)
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
        for (p in props) {
            val i = slotOf(p.box)
            if (foot[i * 4 + 2] > 0f) continue // two props in one slot: the first keeps it
            p.index = i
            val b = p.box
            foot[i * 4] = (b[0] + b[3]) * 0.5f; foot[i * 4 + 1] = (b[2] + b[5]) * 0.5f
            foot[i * 4 + 2] = max((b[3] - b[0]) * 0.5f, 0.01f); foot[i * 4 + 3] = (b[5] - b[2]) * 0.5f
            footY[i * 4] = b[1]; footY[i * 4 + 1] = b[4]
        }
        kinds = FloatArray(m.parts.size) { kindOf(m.parts[it]) }
        selves = FloatArray(m.parts.size) { (byNode[m.parts[it].node.name]?.index ?: -1).toFloat() }
    }

    /** Shading kind of a part: the rack's tagged materials (`extras.shelf`, supplies.py), prints, metal, plain. */
    private fun kindOf(part: DrawPart): Float {
        val tag = (part.material.src.extras["shelf"] as? JsonPrimitive)?.contentOrNullSafe()
        return when {
            tag == "lamp" -> KIND_LAMP
            part.material.albedo != null -> if (part.node.name == BACK_NODE) KIND_BACKDROP else KIND_PRINT
            tag == "board" -> KIND_BOARD
            tag == "wood" -> KIND_WOOD
            tag == "glass" -> KIND_GLASS
            part.material.src.metallic > 0.5f -> KIND_METAL
            else -> KIND_PLAIN
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
        select(b, out)
        return b.id
    }

    /** Picks the prop [id] (a tap on its name tag): lifts it and writes its top-centre point; false if unknown. */
    fun pickId(id: String, out: FloatArray): Boolean {
        val b = byNode[SupplyShelfLayout.NODE_PREFIX + id] ?: return false
        select(b, out)
        return true
    }

    private fun select(b: Prop, out: FloatArray) {
        picked = b
        out[0] = (b.box[0] + b.box[3]) * 0.5f
        out[1] = b.box[4]
        out[2] = (b.box[2] + b.box[5]) * 0.5f
    }

    /** Draws the shelf with [p] (the shelf program). Returns the number of draw calls. */
    fun draw(m: GpuModel, p: ShaderProgram, white: Texture, locked: Set<String>): Int {
        bind(m)
        p.use()
        // footprints follow the pick: the lifted prop's shadow rises and softens with it
        for (prop in props) {
            val i = prop.index
            if (i < 0) continue
            val b = prop.box
            val y0 = b[1] + LIFT_CM * prop.lift
            footY[i * 4] = y0
            footY[i * 4 + 1] = y0 + (b[4] - b[1]) * (1f + SCALE_UP * prop.lift)
            footY[i * 4 + 2] = prop.lift
        }
        p.set4fv("uFoot", foot)
        p.set4fv("uFootY", footY)
        var calls = 0
        for (pi in m.parts.indices) {
            val part = m.parts[pi]
            if (part.material.blended) continue
            val prop = byNode[part.node.name]
            val lift = prop?.lift ?: 0f
            val dim = prop != null && prop.id in locked
            p.setMat4("uModel", if (lift > 0f && prop != null) lifted(prop, lift, part.node.world) else part.node.world)
            part.material.apply(p, white)
            val c = part.material.src.baseColor
            val k = if (dim) DIM else 1f
            if (dim) p.set4f("uBaseColor", c[0] * k + GREY, c[1] * k + GREY, c[2] * k + GREY, c.getOrElse(3) { 1f })
            val kind = kinds[pi]
            p.set1f("uKind", kind)
            p.set1f("uSelf", selves[pi])
            // the room lights the shelf (shelf.frag); the emissive (x the print in the shader) is only the lit pucks
            // and sign, a faint floor so nothing goes black, and the picked prop's gentle pulse
            val base = when {
                kind == KIND_LAMP -> LAMP_GLOW
                part.node.name == SupplyShelfLayout.SIGN_NODE && part.material.albedo != null -> SIGN_GLOW
                dim -> GLOW_DIM
                part.material.albedo != null -> GLOW_PRINT
                else -> GLOW
            }
            val glow = base + lift * (PICK_GLOW + 0.08f * sin(time * 7f))
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
        const val GLOW = 0.02f
        const val GLOW_DIM = 0f
        const val GLOW_PRINT = 0f
        const val SIGN_GLOW = 0.9f
        const val LAMP_GLOW = 2.4f
        const val BACK_NODE = "ShelfBack"
        /** Props on the shelf (footprint slots, the size of shelf.frag's uFoot / uFootY arrays). */
        val PROPS = SupplyShelfLayout.ROW_COUNT.sum()
        /** shelf.frag `uKind`. */
        const val KIND_PLAIN = 0f
        const val KIND_BOARD = 1f
        const val KIND_WOOD = 2f
        const val KIND_PRINT = 3f
        const val KIND_GLASS = 4f
        const val KIND_BACKDROP = 5f
        const val KIND_LAMP = 6f
        const val KIND_METAL = 7f
        const val PICK_GLOW = 0.35f
        /** Locked (sandbox-only in Watch mode) props: colour × DIM + GREY, a faded grey-blue. */
        const val DIM = 0.35f
        const val GREY = 0.06f

        /** Footprint slot of a prop standing on a row board: row by its base height, slot by its centre x. */
        internal fun slotOf(box: FloatArray): Int {
            var row = 0
            for (r in 1 until SupplyShelfLayout.ROWS) {
                if (abs(box[1] - SupplyShelfLayout.ROW_Y[r]) < abs(box[1] - SupplyShelfLayout.ROW_Y[row])) row = r
            }
            val n = SupplyShelfLayout.ROW_COUNT[row]
            val slot = floor(((box[0] + box[3]) * 0.5f - SupplyShelfLayout.SLOT_X0) / SupplyShelfLayout.slotWidth(n)).toInt().coerceIn(0, n - 1)
            var base = 0
            for (r in 0 until row) base += SupplyShelfLayout.ROW_COUNT[r]
            return base + slot
        }

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
