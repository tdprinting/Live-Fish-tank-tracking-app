package com.reeflive.app.render

import kotlin.math.max
import kotlin.math.min

/**
 * Walking around a fish store (docs/ECONOMY.md §5 "Visiting a store"): the hosts tell the renderer which store the
 * keeper is in, how many display tanks it has and which one they stand at; the renderer builds the shop around the
 * live display tank ([StoreRoomScene]) and reports taps on the other tanks and on the counter. Any thread writes,
 * the GL thread reads.
 */
class StoreRoomControl {
    @Volatile var storeId: String? = null
        private set
    /** Display tanks in the store (bays 0 until [tanks]; the counter is bay [tanks]). */
    @Volatile var tanks: Int = 0
        private set
    /** The tank the keeper stands at: the live display sim. */
    @Volatile var at: Int = 0
        private set
    /** Standing at the checkout counter (the last tank stays live beside it). */
    @Volatile var atCounter: Boolean = false
        private set
    /** Tap on another display tank (its index): the host shows that tank. Main thread on Android, the page on the web. */
    @Volatile var onStand: ((Int) -> Unit)? = null
    /** Tap on the checkout counter. */
    @Volatile var onCounter: (() -> Unit)? = null

    fun enter(store: String, tanks: Int, at: Int) {
        this.tanks = tanks; this.at = at; atCounter = false
        storeId = store
    }

    /** Walks to tank [i]. */
    fun show(i: Int) { at = i; atCounter = false }

    /** Walks to the counter ([on]) or back to the tank. */
    fun counter(on: Boolean) { atCounter = on }

    fun leave() { storeId = null; atCounter = false }
}

/** Store room layout shared by tools/blender/store_rooms.py, store.frag and the camera. */
object StoreRoomLayout {
    /** Bay pitch along x (cm): one stand and the aisle space beside it. */
    const val BAY = 180f
    const val FLOOR_Y = -82f
    fun model(storeId: String) = "models/store_room_$storeId.glb"

    /** The store's light rig (store.frag): tank-light colour, hemisphere fill, tank glow (linear). */
    class Theme(val lamp: FloatArray, val fill: FloatArray, val glow: FloatArray)

    private val WARM = Theme(floatArrayOf(1.0f, 0.93f, 0.82f), floatArrayOf(0.34f, 0.31f, 0.27f), floatArrayOf(0.10f, 0.35f, 0.55f))
    fun theme(storeId: String): Theme = when (storeId) {
        "frag_shack" -> Theme(floatArrayOf(0.62f, 0.62f, 1.0f), floatArrayOf(0.09f, 0.09f, 0.16f), floatArrayOf(0.30f, 0.25f, 0.95f))
        "big_blue" -> Theme(floatArrayOf(0.95f, 0.97f, 1.0f), floatArrayOf(0.38f, 0.40f, 0.44f), floatArrayOf(0.08f, 0.38f, 0.70f))
        "deep_reef" -> Theme(floatArrayOf(1.0f, 0.86f, 0.62f), floatArrayOf(0.06f, 0.055f, 0.07f), floatArrayOf(0.08f, 0.30f, 0.75f))
        else -> WARM
    }
}

/**
 * GL-thread side of a store visit: draws the store room around the live display tank and picks taps on its other
 * tanks and the counter. The live tank is always the bay the keeper stands at, at the origin; the other bays repeat
 * the model's modules ([StoreRoomLayout.BAY] apart): Bay (floor, walls, lights) everywhere, from an entry bay left of
 * the first tank to a spare bay right of the counter, Stand under every tank, PropTank (a stocked tank) where the live
 * one is not, Counter one bay past the last tank, EndL / EndR closing the aisle and the store's Sign over the first tank. A walk to another tank slides the camera ([Camera.storeWalk]).
 */
internal class StoreRoomScene {
    private var model: GpuModel? = null
    private var kinds = FloatArray(0)
    private var group = IntArray(0)
    /** Local bounds (min xyz, max xyz) of the PropTank and Counter nodes, for picking. */
    private val tankBox = FloatArray(6)
    private val counterBox = FloatArray(6)
    private val mat = FloatArray(16)
    private var store: String? = null
    private var lastAt = -1
    private var lastCounter = false
    var theme = StoreRoomLayout.theme("")
        private set

    /** In a store with its room loaded (the hosts still show the tank if the model is late). */
    val active: Boolean get() = store != null

    /** Follows the control: entering or leaving moves the camera to the aisle (or home); a new tank walks there. */
    fun apply(c: StoreRoomControl?, cam: Camera, assets: (String) -> GpuModel?) {
        val id = c?.storeId
        if (id != store) {
            store = id
            lastAt = -1
            model = null
            if (id != null) theme = StoreRoomLayout.theme(id)
            cam.enterStore(id != null)
        }
        if (id == null || c == null) return
        val m = assets(StoreRoomLayout.model(id))
        if (m != null && m !== model) bind(m)
        val at = c.at
        if (at != lastAt) {
            if (lastAt >= 0) cam.storeWalk((lastAt - at) * StoreRoomLayout.BAY)
            lastAt = at
        }
        val focus = if (c.atCounter) (c.tanks - at) * StoreRoomLayout.BAY else 0f
        if (c.atCounter != lastCounter || focus != cam.storeFocusX) {
            lastCounter = c.atCounter
            cam.storeFocus(focus)
        }
    }

    private fun bind(m: GpuModel) {
        model = m
        kinds = FloatArray(m.parts.size) { kindOf(m.parts[it].material.src.name) }
        group = IntArray(m.parts.size) { groupOf(m.parts[it].node.name) }
        for (i in 0 until 3) { tankBox[i] = Float.MAX_VALUE; tankBox[i + 3] = -Float.MAX_VALUE; counterBox[i] = Float.MAX_VALUE; counterBox[i + 3] = -Float.MAX_VALUE }
        for (pi in m.parts.indices) {
            val b = when (group[pi]) { G_PROP, G_STAND -> tankBox; G_COUNTER -> counterBox; else -> null } ?: continue
            val w = m.parts[pi].worldBounds
            for (k in 0 until 3) { b[k] = min(b[k], w[k]); b[k + 3] = max(b[k + 3], w[k + 3]) }
        }
        // the counter's pick box is the desk, not the shelves on the wall behind it or the sign above
        counterBox[2] = max(counterBox[2], 30f); counterBox[4] = min(counterBox[4], 60f)
    }

    /**
     * Draws the room's opaque ([blended] false) or see-through parts with [p] (the store program). Returns the draw
     * calls. [c] says how many bays there are and which is live.
     */
    fun draw(p: ShaderProgram, white: Texture, c: StoreRoomControl, blended: Boolean): Int {
        val m = model ?: return 0
        val tanks = c.tanks
        val at = c.at
        p.use()
        p.set1f("uBay", StoreRoomLayout.BAY)
        p.set3f("uLampCol", theme.lamp[0], theme.lamp[1], theme.lamp[2])
        p.set3f("uFillCol", theme.fill[0], theme.fill[1], theme.fill[2])
        p.set3f("uGlowCol", theme.glow[0], theme.glow[1], theme.glow[2])
        var calls = 0
        for (pi in m.parts.indices) {
            val part = m.parts[pi]
            if (part.material.blended != blended) continue
            val g = group[pi]
            part.material.apply(p, white)
            p.set1f("uKind", kinds[pi])
            for (bay in -1..tanks + 1) {
                val show = when (g) {
                    G_BAY -> true
                    G_STAND -> bay in 0 until tanks
                    G_PROP -> bay in 0 until tanks && bay != at
                    G_COUNTER -> bay == tanks
                    G_SIGN -> bay == 0
                    G_END_L -> bay == -1
                    G_END_R -> bay == tanks + 1
                    else -> false
                }
                if (!show) continue
                part.node.world.copyInto(mat)
                mat[12] += (bay - at) * StoreRoomLayout.BAY
                p.setMat4("uModel", mat)
                part.mesh.draw()
                calls++
            }
        }
        return calls
    }

    /** What a tap ray ([o], [d]) hits: another tank's index, [COUNTER], or -1 (the live tank or nothing). */
    fun pick(c: StoreRoomControl, o: FloatArray, d: FloatArray): Int {
        if (model == null) return -1
        var best = -1
        var bestT = Float.MAX_VALUE
        for (bay in 0..c.tanks) {
            val dx = (bay - c.at) * StoreRoomLayout.BAY
            val box = if (bay == c.tanks) counterBox else tankBox
            if (bay == c.at && bay < c.tanks) continue
            shifted[0] = box[0] + dx; shifted[1] = box[1]; shifted[2] = box[2]
            shifted[3] = box[3] + dx; shifted[4] = box[4]; shifted[5] = box[5]
            val t = SupplyShelfScene.rayBox(o, d, shifted, 2f) ?: continue
            if (t < bestT) { bestT = t; best = if (bay == c.tanks) COUNTER else bay }
        }
        return best
    }

    private val shifted = FloatArray(6)

    companion object {
        const val COUNTER = Int.MAX_VALUE
        private const val G_BAY = 0
        private const val G_STAND = 1
        private const val G_PROP = 2
        private const val G_COUNTER = 3
        private const val G_END_L = 4
        private const val G_END_R = 5
        private const val G_SIGN = 6

        private fun groupOf(node: String): Int = when (node) {
            "Bay" -> G_BAY
            "Stand" -> G_STAND
            "PropTank" -> G_PROP
            "Counter" -> G_COUNTER
            "EndL" -> G_END_L
            "EndR" -> G_END_R
            "Sign" -> G_SIGN
            else -> -1
        }

        /** store.frag `uKind` from the material name (store_rooms.py names them by kind). */
        fun kindOf(material: String): Float = when {
            material.startsWith("floor") -> 1f
            material.startsWith("lamp") || material.startsWith("screen") || material.startsWith("neon") || material.startsWith("sign_text") -> 2f
            material.startsWith("glass") -> 3f
            material.startsWith("water") -> 4f
            material.startsWith("gold") || material.startsWith("light_housing") -> 5f
            else -> 0f
        }
    }
}
