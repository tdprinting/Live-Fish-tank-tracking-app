package com.reeflive.app.render

import com.reeflive.core.custom.economy.DisplayKind
import com.reeflive.core.custom.economy.DisplaySetup
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

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
    /**
     * How each display tank is set up, in walking order (core StoreDisplays): its kind decides the stand, glass and
     * lamp drawn in its bay, its light the colour its water and lamp glow. Empty: every bay a long tank.
     */
    @Volatile var setups: List<DisplaySetup> = emptyList()
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

    fun enter(store: String, tanks: Int, at: Int, setups: List<DisplaySetup> = emptyList()) {
        this.tanks = tanks; this.at = at; atCounter = false
        this.setups = setups
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
    /** Bay centre in the model (Tee's tank's centre: the bay modules are authored around it). */
    const val CX = 60.95f
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

    /** Where the people stand (store_rooms.py models each at the origin, facing +z; WALK_HIP, the clerk's head). */
    const val CLERK_Z = 15f
    const val CLERK_HEAD_Y = 158.6f
    const val WALK_HIP = 83f
    const val WALK_Z = 132f
    /** Walking pace (cm/s) and stride (cm per leg swing cycle). */
    const val WALK_SPEED = 55f
    const val STRIDE = 120f
}

/**
 * GL-thread side of a store visit: draws the store room around the live display tank and picks taps on its other
 * tanks and the counter. The live tank is always the bay the keeper stands at, centred on its own glass; the other bays
 * repeat the model's modules ([StoreRoomLayout.BAY] apart):
 *
 * - Bay (floor, walls, poster, aisle light) everywhere, from an entry bay left of the first tank to a spare bay right
 *   of the counter; Decor0 / Decor1 (the store's props) alternate at the bay edges; EndL / EndR close the aisle and
 *   the store's Sign hangs over the first tank.
 * - Per display kind k (core DisplayKind, in walking order from [StoreRoomControl.setups]): Stand<k> and Lamp<k> in its
 *   bay, and where the live tank is not, PropTank<k> (glass, water, gear), PropReef<k> (rock, corals) and
 *   PropFish<k> (a few fish drifting). The live frag tank gets the Racks node (egg crate and plugs) under its frags.
 * - Counter one bay past the last tank, the Clerk behind it (body swaying, head looking round, turning to the keeper
 *   at the till); two shoppers at the next tank and a Walker strolling the aisle beside it, legs swinging.
 *
 * Water and tank lamps glow in each tank's light colour (DisplayLight.glow); bubbles, the shimmer and the egg crate
 * are store.frag's. A walk to another tank slides the camera ([Camera.storeWalk]).
 */
internal class StoreRoomScene {
    private var model: GpuModel? = null
    private var kinds = FloatArray(0)
    private var group = IntArray(0)
    /** Local bounds (min xyz, max xyz) of each display kind's stand + prop tank, and of the counter, for picking. */
    private val kindBox = Array(KIND_COUNT) { FloatArray(6) }
    private val counterBox = FloatArray(6)
    private val mat = FloatArray(16)
    private val pose = FloatArray(16)
    private var store: String? = null
    private var lastAt = -1
    private var lastCounter = false
    var theme = StoreRoomLayout.theme("")
        private set
    /** Seconds (the renderer's clock) for the people and the drifting fish. */
    var time = 0f

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
        for (b in kindBox) reset(b)
        reset(counterBox)
        for (pi in m.parts.indices) {
            val g = group[pi]
            val b = when {
                g == G_COUNTER -> counterBox
                g in G_STAND until G_STAND + KIND_COUNT -> kindBox[g - G_STAND]
                g in G_PROP until G_PROP + KIND_COUNT -> kindBox[g - G_PROP]
                else -> null
            } ?: continue
            val w = m.parts[pi].worldBounds
            for (k in 0 until 3) { b[k] = min(b[k], w[k]); b[k + 3] = max(b[k + 3], w[k + 3]) }
        }
        // the counter's pick box is the desk, not the shelves on the wall behind it or the sign above
        counterBox[2] = max(counterBox[2], 30f); counterBox[4] = min(counterBox[4], 60f)
    }

    private fun reset(b: FloatArray) { for (i in 0 until 3) { b[i] = Float.MAX_VALUE; b[i + 3] = -Float.MAX_VALUE } }

    /** The display kind at [bay] (a long tank when the host gave no setups). */
    private fun kindAt(c: StoreRoomControl, bay: Int): Int = c.setups.getOrNull(bay)?.kind?.ordinal ?: LONG

    /** World x of the centre of [bay]: the live tank's glass is centred on its own box, the bays follow it. */
    private fun bayX(c: StoreRoomControl, bay: Int): Float = Camera.TANK[0] * 0.5f + (bay - c.at) * StoreRoomLayout.BAY

    /**
     * Draws the room's opaque ([blended] false) or see-through parts with [p] (the store program). Returns the draw
     * calls. [c] says how many bays there are, how each is set up and which is live.
     */
    fun draw(p: ShaderProgram, white: Texture, c: StoreRoomControl, blended: Boolean, viewProj: FloatArray): Int {
        val m = model ?: return 0
        val tanks = c.tanks
        val at = c.at
        val liveKind = kindAt(c, at)
        p.use()
        p.set1f("uBay", StoreRoomLayout.BAY)
        p.set3f("uLampCol", theme.lamp[0], theme.lamp[1], theme.lamp[2])
        p.set3f("uFillCol", theme.fill[0], theme.fill[1], theme.fill[2])
        // the room's light repeats every bay from the live tank's centre (store.frag)
        p.set1f("uRoomX", Camera.TANK[0] * 0.5f)
        var calls = 0
        for (pi in m.parts.indices) {
            val part = m.parts[pi]
            if (part.material.blended != blended) continue
            val g = group[pi]
            if (g < 0) continue
            part.material.apply(p, white)
            p.set1f("uKind", kinds[pi])
            when {
                g == G_BAY || g == G_DECOR0 || g == G_DECOR1 -> for (bay in -1..tanks + 1) {
                    if (g == G_DECOR0 && (bay and 1) != 0 || g == G_DECOR1 && (bay and 1) == 0) continue
                    if (!inView(viewProj, bayX(c, bay))) continue
                    glow(p, c, bay)
                    calls += drawAt(p, part, bayX(c, bay) - StoreRoomLayout.CX)
                }
                g == G_COUNTER -> { glow(p, c, -1); calls += drawAt(p, part, bayX(c, tanks) - StoreRoomLayout.CX) }
                g == G_SIGN -> { glow(p, c, -1); calls += drawAt(p, part, bayX(c, 0) - StoreRoomLayout.CX) }
                g == G_END_L -> { glow(p, c, -1); calls += drawAt(p, part, bayX(c, -1) - StoreRoomLayout.CX) }
                g == G_END_R -> { glow(p, c, -1); calls += drawAt(p, part, bayX(c, tanks + 1) - StoreRoomLayout.CX) }
                g == G_RACKS -> if (liveKind == DisplayKind.FRAG.ordinal && tanks > 0) { glow(p, c, at); calls += drawAt(p, part, 0f) }
                g >= G_STAND -> {
                    val k = (g - G_STAND) % 10
                    val family = g - k
                    for (bay in 0 until tanks) {
                        if (kindAt(c, bay) != k) continue
                        // the live tank is the real one: only its stand and lamp come from the room
                        if (bay == at && family != G_STAND && family != G_LAMP) continue
                        if (!inView(viewProj, bayX(c, bay))) continue
                        glow(p, c, bay)
                        val x = bayX(c, bay) - KIND_LENGTH[k] * 0.5f
                        calls += if (family == G_FISH) drawFish(p, part, x, bay) else drawAt(p, part, x)
                    }
                }
                else -> calls += drawPeople(p, part, g, c)
            }
        }
        return calls
    }

    /**
     * False when everything of a bay centred at world [x] lies outside the left or right clip plane of [vp]. Conservative:
     * the box is the bay's pitch plus a wide margin for the stand, props and fish, and the room's full height and depth.
     */
    private fun inView(vp: FloatArray, x: Float): Boolean {
        var left = true; var right = true
        for (i in 0 until 8) {
            val px = if (i and 1 == 0) x - CULL_HALF_X else x + CULL_HALF_X
            val py = if (i and 2 == 0) StoreRoomLayout.FLOOR_Y else CULL_TOP_Y
            val pz = if (i and 4 == 0) CULL_MIN_Z else CULL_MAX_Z
            val w = vp[3] * px + vp[7] * py + vp[11] * pz + vp[15]
            val cx = vp[0] * px + vp[4] * py + vp[8] * pz + vp[12]
            if (cx >= -w) left = false
            if (cx <= w) right = false
        }
        return !left && !right
    }

    /** The tank colour for [bay]'s water, lamp and the glow it spills (−1: the store's own). */
    private fun glow(p: ShaderProgram, c: StoreRoomControl, bay: Int) {
        val g = c.setups.getOrNull(bay)?.light?.glow ?: theme.glow
        p.set3f("uGlowCol", g[0], g[1], g[2])
    }

    private fun drawAt(p: ShaderProgram, part: DrawPart, dx: Float): Int {
        part.node.world.copyInto(mat)
        mat[12] += dx
        p.setMat4("uModel", mat)
        part.mesh.draw()
        return 1
    }

    /** A prop tank's fish: the school drifts a little along the glass and bobs, each bay out of step. */
    private fun drawFish(p: ShaderProgram, part: DrawPart, dx: Float, bay: Int): Int {
        val t = time + bay * 7.3f
        part.node.world.copyInto(mat)
        mat[12] += dx + sin(t * 0.35f) * 5f
        mat[13] += sin(t * 0.9f) * 0.8f
        mat[14] += sin(t * 0.23f) * 2f
        p.setMat4("uModel", mat)
        part.mesh.draw()
        return 1
    }

    /** The clerk, the two shoppers and the walker, placed and animated by [time]. */
    private fun drawPeople(p: ShaderProgram, part: DrawPart, g: Int, c: StoreRoomControl): Int {
        val t = time
        val tanks = c.tanks
        glow(p, c, -1)
        when (g) {
            G_CLERK, G_CLERK_HEAD -> {
                val x = bayX(c, tanks)
                val sway = sin(t * 0.5f) * 0.12f
                if (g == G_CLERK) {
                    place(x, StoreRoomLayout.FLOOR_Y + sin(t * 1.6f) * 0.4f, StoreRoomLayout.CLERK_Z, sway, 0f)
                } else {
                    // looks round the shop; faces the keeper when they come to the till
                    val look = if (c.atCounter) 0f else sin(t * 0.37f) * 0.7f + sin(t * 0.11f) * 0.3f
                    place(x, StoreRoomLayout.FLOOR_Y + StoreRoomLayout.CLERK_HEAD_Y + sin(t * 1.6f) * 0.4f, StoreRoomLayout.CLERK_Z, sway + look, 0f)
                }
            }
            G_SHOP0, G_SHOP1 -> {
                // a parent and a kid at the next tank along (or the one before at the end of the row)
                val bay = if (c.at + 1 < tanks) c.at + 1 else c.at - 1
                if (bay < 0) return 0
                val front = KIND_DEPTH[kindAt(c, bay)]
                val x = bayX(c, bay)
                if (g == G_SHOP0) place(x - 58f, StoreRoomLayout.FLOOR_Y, front + 42f, PI + 0.25f + sin(t * 0.3f) * 0.15f, 0f)
                else place(x - 30f, StoreRoomLayout.FLOOR_Y + abs(sin(t * 2.2f)) * 2.5f, front + 30f, PI - 0.35f + sin(t * 0.8f) * 0.25f, 0f)
            }
            G_WALKER, G_LEG_L, G_LEG_R -> {
                // strolls the aisle right of the live tank (never across it: there is always a tank or the counter
                // there) and back, pausing at each end
                val a = bayX(c, c.at) + 0.62f * StoreRoomLayout.BAY
                val b = a + StoreRoomLayout.BAY * 1.4f
                val len = b - a
                val walkT = len / StoreRoomLayout.WALK_SPEED
                val cycle = 2f * (walkT + PAUSE)
                val u = t % cycle
                val out = u < walkT + PAUSE
                val s = if (out) min(u, walkT) else min(u - walkT - PAUSE, walkT)
                val moving = if (out) u < walkT else u - walkT - PAUSE < walkT
                val f = s / walkT
                val x = if (out) a + (b - a) * f else b + (a - b) * f
                val dir = if (out) 1f else -1f
                val yaw = dir * PI * 0.5f
                val phase = s * StoreRoomLayout.WALK_SPEED / StoreRoomLayout.STRIDE * 2f * PI
                val swing = if (moving) sin(phase) * 0.45f else 0f
                val bob = if (moving) abs(cos(phase)) * 1.6f else 0f
                when (g) {
                    G_WALKER -> place(x, StoreRoomLayout.FLOOR_Y + bob, StoreRoomLayout.WALK_Z, yaw, 0f)
                    G_LEG_L -> place(x, StoreRoomLayout.FLOOR_Y + StoreRoomLayout.WALK_HIP + bob, StoreRoomLayout.WALK_Z, yaw, swing)
                    else -> place(x, StoreRoomLayout.FLOOR_Y + StoreRoomLayout.WALK_HIP + bob, StoreRoomLayout.WALK_Z, yaw, -swing)
                }
            }
            else -> return 0
        }
        M.mul(pose, part.node.world, mat)
        p.setMat4("uModel", mat)
        part.mesh.draw()
        return 1
    }

    /** [pose] = translate (x, y, z) · turn [yaw] about y · tip [pitch] about x (column-major). */
    private fun place(x: Float, y: Float, z: Float, yaw: Float, pitch: Float) {
        val c = cos(yaw); val s = sin(yaw); val cp = cos(pitch); val sp = sin(pitch)
        pose[0] = c; pose[1] = 0f; pose[2] = -s; pose[3] = 0f
        pose[4] = s * sp; pose[5] = cp; pose[6] = c * sp; pose[7] = 0f
        pose[8] = s * cp; pose[9] = -sp; pose[10] = c * cp; pose[11] = 0f
        pose[12] = x; pose[13] = y; pose[14] = z; pose[15] = 1f
    }

    /** What a tap ray ([o], [d]) hits: another tank's index, [COUNTER], or -1 (the live tank or nothing). */
    fun pick(c: StoreRoomControl, o: FloatArray, d: FloatArray): Int {
        if (model == null) return -1
        var best = -1
        var bestT = Float.MAX_VALUE
        for (bay in 0..c.tanks) {
            if (bay == c.at && bay < c.tanks) continue
            val counter = bay == c.tanks
            val box = if (counter) counterBox else kindBox[kindAt(c, bay)]
            if (box[0] > box[3]) continue
            val dx = if (counter) bayX(c, bay) - StoreRoomLayout.CX else bayX(c, bay) - KIND_LENGTH[kindAt(c, bay)] * 0.5f
            shifted[0] = box[0] + dx; shifted[1] = box[1]; shifted[2] = box[2]
            shifted[3] = box[3] + dx; shifted[4] = box[4]; shifted[5] = box[5]
            val t = SupplyShelfScene.rayBox(o, d, shifted, 2f) ?: continue
            if (t < bestT) { bestT = t; best = if (counter) COUNTER else bay }
        }
        return best
    }

    private val shifted = FloatArray(6)

    companion object {
        const val COUNTER = Int.MAX_VALUE
        private const val PI = 3.1415927f
        /** Seconds the walker stands at each end of its stroll. */
        private const val PAUSE = 4f
        /** Bay cull box (cm) around a bay centre: wide enough for any stand, prop tank or aisle decor of the bay. */
        private const val CULL_HALF_X = 220f
        private const val CULL_TOP_Y = 320f
        private const val CULL_MIN_Z = -200f
        private const val CULL_MAX_Z = 500f
        private val KIND_COUNT = DisplayKind.entries.size
        private val LONG = DisplayKind.LONG.ordinal
        /** Each display kind's glass length and depth (cm), as its build's box. */
        private val KIND_LENGTH = FloatArray(KIND_COUNT) { DisplayKind.entries[it].build.shape.lengthCm.toFloat() }
        private val KIND_DEPTH = FloatArray(KIND_COUNT) { DisplayKind.entries[it].build.shape.depthCm.toFloat() }

        private const val G_BAY = 0
        private const val G_COUNTER = 1
        private const val G_END_L = 2
        private const val G_END_R = 3
        private const val G_SIGN = 4
        private const val G_DECOR0 = 5
        private const val G_DECOR1 = 6
        private const val G_RACKS = 7
        private const val G_CLERK = 8
        private const val G_CLERK_HEAD = 9
        private const val G_SHOP0 = 10
        private const val G_SHOP1 = 11
        private const val G_WALKER = 12
        private const val G_LEG_L = 13
        private const val G_LEG_R = 14
        /** Per-kind families: G_STAND + k, G_LAMP + k, … (k = DisplayKind ordinal, < 10). */
        private const val G_STAND = 20
        private const val G_LAMP = 30
        private const val G_PROP = 40
        private const val G_REEF = 50
        private const val G_FISH = 60

        private fun groupOf(node: String): Int = when (node) {
            "Bay" -> G_BAY
            "Counter" -> G_COUNTER
            "EndL" -> G_END_L
            "EndR" -> G_END_R
            "Sign" -> G_SIGN
            "Decor0" -> G_DECOR0
            "Decor1" -> G_DECOR1
            "Racks" -> G_RACKS
            "Clerk" -> G_CLERK
            "ClerkHead" -> G_CLERK_HEAD
            "Shopper0" -> G_SHOP0
            "Shopper1" -> G_SHOP1
            "Walker" -> G_WALKER
            "WalkerLegL" -> G_LEG_L
            "WalkerLegR" -> G_LEG_R
            else -> family(node, "Stand", G_STAND) ?: family(node, "Lamp", G_LAMP) ?: family(node, "PropTank", G_PROP)
                ?: family(node, "PropReef", G_REEF) ?: family(node, "PropFish", G_FISH) ?: -1
        }

        private fun family(node: String, prefix: String, base: Int): Int? {
            if (!node.startsWith(prefix)) return null
            val k = node.substring(prefix.length).toIntOrNull() ?: return null
            return if (k in 0 until KIND_COUNT) base + k else null
        }

        /** store.frag `uKind` from the material name (store_rooms.py names them by kind). */
        fun kindOf(material: String): Float = when {
            material.startsWith("floor") -> 1f
            material.startsWith("lamp_tank") -> 6f
            material.startsWith("lamp") || material.startsWith("screen") || material.startsWith("neon") || material.startsWith("sign_text") ||
                material.startsWith("glow") -> 2f
            material.startsWith("glass") -> 3f
            material.startsWith("water") -> 4f
            material.startsWith("gold") || material.startsWith("light_housing") -> 5f
            material.startsWith("bubbles") -> 7f
            material.startsWith("eggcrate") -> 8f
            material.startsWith("paint_vcol") -> 9f
            else -> 0f
        }
    }
}
