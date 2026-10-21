package com.reeflive.app.render

import com.reeflive.core.custom.economy.DisplayKind
import com.reeflive.core.custom.economy.DisplaySetup
import com.reeflive.core.custom.economy.Prop
import com.reeflive.core.custom.economy.StoreLife
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
    /**
     * The store's regular has walked up with today's swap (core StoreTrades): the host shows the trade card and answers
     * with [tradeReply].
     */
    @Volatile var onTrade: (() -> Unit)? = null
    /** What the regular says when they come over with a swap (core TradeOffer.line), or null: no swap this visit. */
    @Volatile var trade: String? = null
        private set
    /** The keeper's answer to the swap: 1 yes, -1 no, 0 not yet. */
    @Volatile var tradeAnswer: Int = 0
        private set
    /** Walk in from the street through the front door (off with Reduce Motion: straight to the aisle). */
    @Volatile var walkIn: Boolean = true

    fun enter(store: String, tanks: Int, at: Int, setups: List<DisplaySetup> = emptyList(), trade: String? = null) {
        this.tanks = tanks; this.at = at; atCounter = false
        this.setups = setups
        this.trade = trade; tradeAnswer = 0
        synchronized(lock) { bubbleCount = 0 }
        storeId = store
    }

    /** The keeper's answer on the trade card: the regular thanks them (or shrugs it off) and gets on with their day. */
    fun tradeReply(yes: Boolean) { tradeAnswer = if (yes) 1 else -1 }

    // ---- speech bubbles (GL thread writes, the host's overlay reads)
    private val lock = Any()
    private val bubbleText = arrayOfNulls<String>(StorePeople.MAX_BUBBLES + 2)
    private val bubblePos = FloatArray(bubbleText.size * 3)
    private val bubbleAlpha = FloatArray(bubbleText.size)
    private var bubbleCount = 0

    internal fun publishBubbles(texts: Array<String?>, pos: FloatArray, alpha: FloatArray, n: Int) {
        synchronized(lock) {
            bubbleCount = min(n, bubbleText.size)
            for (i in 0 until bubbleCount) { bubbleText[i] = texts[i]; bubbleAlpha[i] = alpha[i] }
            pos.copyInto(bubblePos, 0, 0, bubbleCount * 3)
        }
    }

    /**
     * The people's speech bubbles now: copies up to [texts].size of them ("Name: line"), their anchors over each head
     * ([pos], world cm, x y z each, for TankProjection) and how faded in they are ([alpha]). Returns how many.
     */
    fun bubbles(texts: Array<String?>, pos: FloatArray, alpha: FloatArray): Int = synchronized(lock) {
        val n = min(bubbleCount, texts.size)
        for (i in 0 until n) { texts[i] = bubbleText[i]; alpha[i] = bubbleAlpha[i] }
        bubblePos.copyInto(pos, 0, 0, n * 3)
        n
    }

    /** Walks to tank [i]. */
    fun show(i: Int) { at = i; atCounter = false }

    /** Walks to the counter ([on]) or back to the tank. */
    fun counter(on: Boolean) { atCounter = on }

    fun leave() { storeId = null; atCounter = false; trade = null; synchronized(lock) { bubbleCount = 0 } }
}

/** Store room layout shared by tools/blender/store_rooms.py, store.frag and the camera. */
object StoreRoomLayout {
    /** Bay pitch along x (cm) when a model doesn't say (each store's model has its own: root extras `bay`). */
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
}

/**
 * GL-thread side of a store visit: draws the store room around the live display tank and picks taps on its other
 * tanks, its people and the counter. The live tank is always the bay the keeper stands at, centred on its own glass;
 * the other bays repeat the model's modules (each store's own bay pitch apart, root extras `bay`):
 *
 * - Bay (floor, walls, ceiling and its lights in the store's finish) everywhere, from the Entry bay left of the first
 *   tank to the Back bay right of the counter; Decor0 / Decor1 alternate at the bay edges; EndL / EndR close the aisle
 *   and the store's Sign hangs over the first tank.
 * - Per display kind k (core DisplayKind, in walking order from [StoreRoomControl.setups]): Stand<k> and Lamp<k> in its
 *   bay, and where the live tank is not, PropTank<k>, PropReef<k> and PropFish<k>. The live frag tank gets Racks.
 * - Counter one bay past the last tank.
 * - The people (core StoreLife, [StorePeople]): P<slot>_Torso, _Head, _ArmL, _ArmR, _LegL, _LegR posed every frame,
 *   the Prop_<name> in their hand, the shop pet (Pet_Body + Pet_Tail, or Pet_Sleep). What they say goes to
 *   [StoreRoomControl.bubbles].
 * - Outside, drawn only while the eye is near the front: FrontBay / FrontDoor (the front wall, bay 0 has the door),
 *   Roof, the door leaves (DoorL / DoorR swing or slide, DoorUp rolls up) that open as someone comes near, FrontSign,
 *   Street and Sky at the door, a Neighbor past each end. The visit starts on the sidewalk ([Camera.storeEntry]).
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
    private var store: String? = null
    private var lastAt = -1
    private var lastCounter = false
    var theme = StoreRoomLayout.theme("")
        private set
    /** Seconds (the renderer's clock) for the people and the drifting fish. */
    var time = 0f
    private var lastTime = -1f

    // the model's layout (root extras)
    private var bay = StoreRoomLayout.BAY
    private var front = 540f
    private var wall = 14f
    private var ceil = 300f
    private var doorKind = DOOR_SWING1
    private var doorW = 100f
    private var entered = false
    private var castDue = false

    val people = StorePeople()
    /** Each person slot's joints (model cm, from the feet): hip, neck, shoulder L / R, hip joint L / R; and the grip. */
    private val joints = Array(MAX_PEOPLE) { FloatArray(18) }
    private val grip = Array(MAX_PEOPLE) { FloatArray(3) }
    /** Per slot, per part (Torso, Head, ArmL, ArmR, LegL, LegR): its pose this frame (world, column-major). */
    private val partMats = Array(MAX_PEOPLE) { Array(6) { FloatArray(16) } }
    private val propMats = Array(MAX_PEOPLE) { FloatArray(16) }
    private val petMat = FloatArray(16)
    private val tailMat = FloatArray(16)
    private var tailPivot = FloatArray(3)
    private var doorOpen = 0f
    private var outside = false
    /** Soft floor shadows (store.frag uShadows): world x, z, radius, strength for each person and the pet. */
    private val shadows = FloatArray(MAX_SHADOWS * 4)
    private var shadowN = 0
    private val fig = FloatArray(16)
    private val torso = FloatArray(16)

    /** In a store with its room loaded (the hosts still show the tank if the model is late). */
    val active: Boolean get() = store != null

    /** Follows the control: entering or leaving moves the camera to the aisle (or home); a new tank walks there. */
    fun apply(c: StoreRoomControl?, cam: Camera, assets: (String) -> GpuModel?) {
        val id = c?.storeId
        if (id != store) {
            store = id
            lastAt = -1
            model = null
            entered = false
            if (id != null) theme = StoreRoomLayout.theme(id)
            cam.enterStore(id != null)
            if (id == null) people.reset(null, 0L, null)
            castDue = id != null
        }
        if (id == null || c == null) return
        val m = assets(StoreRoomLayout.model(id))
        if (m != null && m !== model) bind(m)
        val at = c.at
        if (at != lastAt) {
            if (lastAt >= 0) cam.storeWalk((lastAt - at) * bay)
            lastAt = at
        }
        val focus = if (c.atCounter) (c.tanks - at) * bay else 0f
        if (c.atCounter != lastCounter || focus != cam.storeFocusX) {
            lastCounter = c.atCounter
            cam.storeFocus(focus)
        }
        if (model == null) return
        if (!entered) {
            // the visit starts on the sidewalk outside the front door (bay 0) and walks in
            entered = true
            if (c.walkIn) cam.storeEntry(bayX(c, 0), front + wall)
        }
        val dt = if (lastTime < 0f) 0f else (time - lastTime).coerceIn(0f, 0.1f)
        lastTime = time
        layout(c)
        if (castDue) {
            // the store's people take their places once the bays are known
            castDue = false
            people.reset(StoreLife.cast(id), id.hashCode().toLong() * 31 + (time * 1000f).toLong(), c.trade)
        }
        people.step(dt, c.trade, c.tradeAnswer)
        stepDoors(c, cam, dt)
        outside = cam.eye[2] > front - OUTSIDE_NEAR || cam.storeEntering
        pose(c)
        publishBubbles(c)
    }

    /** The store's tanks for the people: where each one stands and how deep its stand is. */
    private fun layout(c: StoreRoomControl) {
        val n = c.tanks
        if (people.halfLen.size != n) { people.halfLen = FloatArray(n); people.depth = FloatArray(n) }
        for (b in 0 until n) {
            val k = kindAt(c, b)
            people.halfLen[b] = KIND_LENGTH[k] * 0.5f
            people.depth[b] = KIND_DEPTH[k] + 6f
        }
        people.bay = bay; people.tanks = n; people.at = c.at; people.front = front
        people.entryZ = if (store == "deep_reef") 195f else 135f
    }

    private fun bind(m: GpuModel) {
        model = m
        bay = m.glb.rootFloat("bay") ?: StoreRoomLayout.BAY
        front = m.glb.rootFloat("front") ?: 540f
        wall = m.glb.rootFloat("wall") ?: 14f
        ceil = m.glb.rootFloat("ceil") ?: 300f
        doorW = m.glb.rootFloat("doorW") ?: 100f
        doorKind = when (m.glb.rootString("door")) { "swing2" -> DOOR_SWING2; "slide" -> DOOR_SLIDE; "rollup" -> DOOR_ROLLUP; else -> DOOR_SWING1 }
        kinds = FloatArray(m.parts.size) { kindOf(m.parts[it].material.src.name) }
        group = IntArray(m.parts.size) { groupOf(m.parts[it].node.name) }
        for (b in kindBox) reset(b)
        reset(counterBox)
        for (j in joints) j.fill(0f)
        for (pi in m.parts.indices) {
            val g = group[pi]
            val part = m.parts[pi]
            val w = part.worldBounds
            if (g in G_PERSON until G_PERSON + MAX_PEOPLE * 8) {
                val slot = (g - G_PERSON) / 8
                val k = (g - G_PERSON) % 8
                val wm = part.node.world
                joints[slot][k * 3] = wm[12]; joints[slot][k * 3 + 1] = wm[13]; joints[slot][k * 3 + 2] = wm[14]
                if (k == PART_HEAD) people.headTop[slot] = w[4]
                if (k == PART_ARM_R) part.node.vec3("grip")?.copyInto(grip[slot])
                continue
            }
            if (g == G_PET_TAIL) { tailPivot = floatArrayOf(part.node.world[12], part.node.world[13], part.node.world[14]) }
            val b = when {
                g == G_COUNTER -> counterBox
                g in G_STAND until G_STAND + KIND_COUNT -> kindBox[g - G_STAND]
                g in G_PROP until G_PROP + KIND_COUNT -> kindBox[g - G_PROP]
                else -> null
            } ?: continue
            for (k in 0 until 3) { b[k] = min(b[k], w[k]); b[k + 3] = max(b[k + 3], w[k + 3]) }
        }
        // the counter's pick box is the desk, not the shelves on the wall behind it or the sign above
        counterBox[2] = max(counterBox[2], 30f); counterBox[4] = min(counterBox[4], 60f)
    }

    private fun reset(b: FloatArray) { for (i in 0 until 3) { b[i] = Float.MAX_VALUE; b[i + 3] = -Float.MAX_VALUE } }

    /** The display kind at [bay] (a long tank when the host gave no setups). */
    private fun kindAt(c: StoreRoomControl, bay: Int): Int = c.setups.getOrNull(bay)?.kind?.ordinal ?: LONG

    /** World x of the centre of [bay]: the live tank's glass is centred on its own box, the bays follow it. */
    private fun bayX(c: StoreRoomControl, bay: Int): Float = Camera.TANK[0] * 0.5f + (bay - c.at) * this.bay

    /** World x of store x [sx] (cm from the first tank's bay centre). */
    private fun worldX(c: StoreRoomControl, sx: Float): Float = Camera.TANK[0] * 0.5f + sx - c.at * bay

    // ============================================================== doors, people, bubbles

    /** The doors swing (or slide, or roll) open while the eye or anyone in the store is near them. */
    private fun stepDoors(c: StoreRoomControl, cam: Camera, dt: Float) {
        val dx = cam.eye[0] - bayX(c, 0)
        val dz = cam.eye[2] - (front + wall)
        var want = dx * dx + dz * dz < DOOR_NEAR * DOOR_NEAR
        if (!want) for (a in people.agents) {
            if (!a.hidden && abs(a.x) < 90f && abs(a.z - front) < 110f) { want = true; break }
        }
        val target = if (want) 1f else 0f
        val speed = if (doorKind == DOOR_ROLLUP) 0.6f else 1.6f
        doorOpen = if (target > doorOpen) min(target, doorOpen + dt * speed) else max(target, doorOpen - dt * speed * 0.7f)
    }

    /** Every person's part matrices, their props and the pet, for this frame. */
    private fun pose(c: StoreRoomControl) {
        shadowN = 0
        for (a in people.agents) {
            val slot = a.person.slot
            if (slot >= MAX_PEOPLE || a.hidden) continue
            if (shadowN < MAX_SHADOWS) {
                val k = shadowN * 4
                shadows[k] = worldX(c, a.x); shadows[k + 1] = a.z; shadows[k + 2] = if (a.kid) 17f else 23f; shadows[k + 3] = 0.55f
                shadowN++
            }
            val j = joints[slot]
            val pm = partMats[slot]
            // the figure: feet on the floor, turned to face its way
            identity(fig)
            translate(fig, worldX(c, a.x), StoreRoomLayout.FLOOR_Y + a.bob, a.z)
            rotY(fig, a.yaw)
            // the torso leans about the hips; head and arms ride on it
            fig.copyInto(torso)
            translate(torso, j[0], j[1], j[2])
            rotX(torso, a.lean)
            torso.copyInto(pm[PART_TORSO])
            jointed(torso, j, PART_HEAD, pm[PART_HEAD]); rotY(pm[PART_HEAD], a.headYaw); rotX(pm[PART_HEAD], a.headPitch)
            jointed(torso, j, PART_ARM_L, pm[PART_ARM_L]); rotZ(pm[PART_ARM_L], -a.armLr); rotX(pm[PART_ARM_L], a.armLp)
            jointed(torso, j, PART_ARM_R, pm[PART_ARM_R]); rotZ(pm[PART_ARM_R], a.armRr); rotX(pm[PART_ARM_R], a.armRp)
            fig.copyInto(pm[PART_LEG_L]); translate(pm[PART_LEG_L], j[PART_LEG_L * 3], j[PART_LEG_L * 3 + 1], j[PART_LEG_L * 3 + 2]); rotX(pm[PART_LEG_L], a.legL)
            fig.copyInto(pm[PART_LEG_R]); translate(pm[PART_LEG_R], j[PART_LEG_R * 3], j[PART_LEG_R * 3 + 1], j[PART_LEG_R * 3 + 2]); rotX(pm[PART_LEG_R], a.legR)
            if (a.prop != Prop.NONE) {
                pm[PART_ARM_R].copyInto(propMats[slot])
                translate(propMats[slot], grip[slot][0], grip[slot][1], grip[slot][2])
            }
        }
        people.pet?.let { p ->
            if (shadowN < MAX_SHADOWS && p.y < 1f) {
                val k = shadowN * 4
                shadows[k] = worldX(c, p.x); shadows[k + 1] = p.z; shadows[k + 2] = 16f; shadows[k + 3] = 0.45f
                shadowN++
            }
            identity(petMat)
            translate(petMat, worldX(c, p.x), StoreRoomLayout.FLOOR_Y + p.y + if (p.walking) abs(sin(p.walked * 0.25f)) * 1.2f else 0f, p.z)
            rotY(petMat, p.yaw)
            petMat.copyInto(tailMat)
            translate(tailMat, tailPivot[0], tailPivot[1], tailPivot[2])
            rotY(tailMat, p.tail)
        }
    }

    /** [out] = [torso] · T(joint [k] − the hips): the joint's frame riding on the torso. */
    private fun jointed(torso: FloatArray, j: FloatArray, k: Int, out: FloatArray) {
        torso.copyInto(out)
        translate(out, j[k * 3] - j[0], j[k * 3 + 1] - j[1], j[k * 3 + 2] - j[2])
    }

    private val bText = arrayOfNulls<String>(StorePeople.MAX_BUBBLES + 2)
    private val bPos = FloatArray(bText.size * 3)
    private val bAlpha = FloatArray(bText.size)

    private fun publishBubbles(c: StoreRoomControl) {
        val n = people.bubbles(bText, bPos, bAlpha)
        for (i in 0 until n) { bPos[i * 3] = worldX(c, bPos[i * 3]); bPos[i * 3 + 1] += StoreRoomLayout.FLOOR_Y }
        c.publishBubbles(bText, bPos, bAlpha, n)
    }

    /** Once, when the regular has walked up to the keeper with a swap: the host shows the trade card. */
    fun takeTradeAsk(): Boolean {
        if (!people.tradeAsk) return false
        people.tradeAsk = false
        return true
    }

    /** The keeper tapped person [slot]. */
    fun hello(slot: Int) = people.hello(slot)

    // ============================================================== drawing

    /**
     * Draws the room's opaque ([blended] false) or see-through parts with [p] (the store program). Returns the draw
     * calls. [c] says how many bays there are, how each is set up and which is live.
     */
    fun draw(p: ShaderProgram, white: Texture, c: StoreRoomControl, blended: Boolean): Int {
        val m = model ?: return 0
        val tanks = c.tanks
        val at = c.at
        val liveKind = kindAt(c, at)
        p.use()
        p.set1f("uBay", bay)
        p.set1f("uCeil", ceil)
        p.set1f("uFront", front)
        p.set1f("uShadowN", shadowN.toFloat())
        if (shadowN > 0) p.set4fv("uShadows", shadows, shadowN)
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
            if (g in G_OUT_FIRST..G_OUT_LAST && !outside) continue
            part.material.apply(p, white)
            p.set1f("uKind", kinds[pi])
            when {
                g == G_BAY || g == G_DECOR0 || g == G_DECOR1 || g == G_ROOF -> for (bay in -1..tanks + 1) {
                    if (g == G_DECOR0 && (bay and 1) != 0 || g == G_DECOR1 && (bay and 1) == 0) continue
                    glow(p, c, bay)
                    calls += drawAt(p, part, bayX(c, bay) - StoreRoomLayout.CX)
                }
                g == G_FRONT_BAY -> for (bay in -1..tanks + 1) {
                    if (bay == 0) continue
                    glow(p, c, -1)
                    calls += drawAt(p, part, bayX(c, bay) - StoreRoomLayout.CX)
                }
                g == G_NEIGHBOR -> { glow(p, c, -1); calls += drawAt(p, part, bayX(c, -2) - StoreRoomLayout.CX) + drawAt(p, part, bayX(c, tanks + 2) - StoreRoomLayout.CX) }
                g == G_FRONT_DOOR || g == G_FRONT_SIGN || g == G_STREET || g == G_SKY -> { glow(p, c, -1); calls += drawAt(p, part, bayX(c, 0) - StoreRoomLayout.CX) }
                g == G_DOOR_L || g == G_DOOR_R || g == G_DOOR_UP -> { glow(p, c, -1); calls += drawDoor(p, part, g, bayX(c, 0) - StoreRoomLayout.CX) }
                g == G_COUNTER -> { glow(p, c, -1); calls += drawAt(p, part, bayX(c, tanks) - StoreRoomLayout.CX) }
                g == G_SIGN -> { glow(p, c, -1); calls += drawAt(p, part, bayX(c, 0) - StoreRoomLayout.CX) }
                g == G_ENTRY -> { glow(p, c, -1); calls += drawAt(p, part, bayX(c, -1) - StoreRoomLayout.CX) }
                g == G_BACK -> { glow(p, c, -1); calls += drawAt(p, part, bayX(c, tanks + 1) - StoreRoomLayout.CX) }
                g == G_END_L -> { glow(p, c, -1); calls += drawAt(p, part, bayX(c, -1) - StoreRoomLayout.CX) }
                g == G_END_R -> { glow(p, c, -1); calls += drawAt(p, part, bayX(c, tanks + 1) - StoreRoomLayout.CX) }
                g == G_RACKS -> if (liveKind == DisplayKind.FRAG.ordinal && tanks > 0) { glow(p, c, at); calls += drawAt(p, part, 0f) }
                g in G_PERSON until G_PERSON + MAX_PEOPLE * 8 -> {
                    val slot = (g - G_PERSON) / 8
                    val a = people.agents.getOrNull(slot) ?: continue
                    if (a.hidden) continue
                    glow(p, c, -1)
                    calls += drawPosed(p, part, partMats[slot][(g - G_PERSON) % 8])
                }
                g in G_ITEM until G_ITEM + PROP_COUNT -> {
                    glow(p, c, -1)
                    val prop = g - G_ITEM
                    for (a in people.agents) {
                        if (a.hidden || a.prop.ordinal != prop || a.person.slot >= MAX_PEOPLE) continue
                        calls += drawPosed(p, part, propMats[a.person.slot])
                    }
                }
                g == G_PET_BODY || g == G_PET_TAIL || g == G_PET_SLEEP -> {
                    val pet = people.pet ?: continue
                    val asleep = pet.asleep && pet.hop <= 0f
                    if ((g == G_PET_SLEEP) != asleep) continue
                    glow(p, c, -1)
                    calls += drawPosed(p, part, if (g == G_PET_TAIL) tailMat else petMat)
                }
                g >= G_STAND -> {
                    val k = (g - G_STAND) % 10
                    val family = g - k
                    for (bay in 0 until tanks) {
                        if (kindAt(c, bay) != k) continue
                        // the live tank is the real one: only its stand and lamp come from the room
                        if (bay == at && family != G_STAND && family != G_LAMP) continue
                        glow(p, c, bay)
                        val x = bayX(c, bay) - KIND_LENGTH[k] * 0.5f
                        calls += if (family == G_FISH) drawFish(p, part, x, bay) else drawAt(p, part, x)
                    }
                }
            }
        }
        return calls
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

    /** A posed part: [pose] is its joint's frame; the mesh is modelled about the node's origin (the joint). */
    private fun drawPosed(p: ShaderProgram, part: DrawPart, pose: FloatArray): Int {
        p.setMat4("uModel", pose)
        part.mesh.draw()
        return 1
    }

    /** A door leaf about its hinge (or slid aside, or rolled up into its housing) by how open the doors are. */
    private fun drawDoor(p: ShaderProgram, part: DrawPart, g: Int, dx: Float): Int {
        part.node.world.copyInto(mat)
        mat[12] += dx
        val u = smooth(doorOpen)
        when (doorKind) {
            DOOR_SLIDE -> mat[12] += (if (g == G_DOOR_L) -1f else 1f) * u * doorW * 0.48f
            DOOR_ROLLUP -> {
                // squashed up into the housing over the opening
                val k = u * 0.94f
                mat[13] += DOOR_H_ROLLUP * k
                mat[4] *= 1f - k; mat[5] *= 1f - k; mat[6] *= 1f - k
            }
            else -> rotY(mat, (if (g == G_DOOR_L) 1f else -1f) * u * DOOR_SWING)
        }
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

    /** What a tap ray ([o], [d]) hits: another tank's index, [COUNTER], [PERSON] + slot, or -1 (the live tank or nothing). */
    fun pick(c: StoreRoomControl, o: FloatArray, d: FloatArray): Int {
        if (model == null) return -1
        var best = -1
        var bestT = Float.MAX_VALUE
        for (a in people.agents) {
            if (a.hidden || a.person.slot >= MAX_PEOPLE) continue
            val x = worldX(c, a.x)
            shifted[0] = x - 22f; shifted[1] = StoreRoomLayout.FLOOR_Y; shifted[2] = a.z - 22f
            shifted[3] = x + 22f; shifted[4] = StoreRoomLayout.FLOOR_Y + people.headTop[a.person.slot]; shifted[5] = a.z + 22f
            val t = SupplyShelfScene.rayBox(o, d, shifted, 2f) ?: continue
            if (t < bestT) { bestT = t; best = PERSON + a.person.slot }
        }
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
        /** [pick] returns PERSON + slot for a tap on a person. */
        const val PERSON = 1_000_000
        private const val MAX_PEOPLE = 10
        private const val MAX_SHADOWS = 12
        private val KIND_COUNT = DisplayKind.entries.size
        private val PROP_COUNT = Prop.entries.size
        private val LONG = DisplayKind.LONG.ordinal
        /** Each display kind's glass length and depth (cm), as its build's box. */
        private val KIND_LENGTH = FloatArray(KIND_COUNT) { DisplayKind.entries[it].build.shape.lengthCm.toFloat() }
        private val KIND_DEPTH = FloatArray(KIND_COUNT) { DisplayKind.entries[it].build.shape.depthCm.toFloat() }
        /** The eye this close to the front wall (cm) draws the outside too. */
        private const val OUTSIDE_NEAR = 160f
        /** The doors open for the eye this close (cm). */
        private const val DOOR_NEAR = 300f
        private const val DOOR_SWING = 1.45f
        private const val DOOR_H_ROLLUP = 222f * 0.98f
        private const val DOOR_SWING1 = 0
        private const val DOOR_SWING2 = 1
        private const val DOOR_SLIDE = 2
        private const val DOOR_ROLLUP = 3

        private const val PART_TORSO = 0
        private const val PART_HEAD = 1
        private const val PART_ARM_L = 2
        private const val PART_ARM_R = 3
        private const val PART_LEG_L = 4
        private const val PART_LEG_R = 5
        private val PART_NAMES = arrayOf("Torso", "Head", "ArmL", "ArmR", "LegL", "LegR")

        private const val G_BAY = 0
        private const val G_COUNTER = 1
        private const val G_END_L = 2
        private const val G_END_R = 3
        private const val G_SIGN = 4
        private const val G_DECOR0 = 5
        private const val G_DECOR1 = 6
        private const val G_RACKS = 7
        private const val G_ENTRY = 8
        private const val G_BACK = 9
        private const val G_PET_BODY = 10
        private const val G_PET_TAIL = 11
        private const val G_PET_SLEEP = 12
        /** Outside (drawn only near the front): G_OUT_FIRST..G_OUT_LAST. */
        private const val G_FRONT_BAY = 13
        private const val G_FRONT_DOOR = 14
        private const val G_FRONT_SIGN = 15
        private const val G_STREET = 16
        private const val G_SKY = 17
        private const val G_NEIGHBOR = 18
        private const val G_ROOF = 19
        private const val G_DOOR_L = 70
        private const val G_DOOR_R = 71
        private const val G_DOOR_UP = 72
        private const val G_OUT_FIRST = G_FRONT_BAY
        private const val G_OUT_LAST = G_ROOF
        /** Per-kind families: G_STAND + k, G_LAMP + k, … (k = DisplayKind ordinal, < 10). */
        private const val G_STAND = 20
        private const val G_LAMP = 30
        private const val G_PROP = 40
        private const val G_REEF = 50
        private const val G_FISH = 60
        /** People: G_PERSON + slot · 8 + part; what they hold: G_ITEM + Prop ordinal. */
        private const val G_PERSON = 100
        private const val G_ITEM = 200

        private fun groupOf(node: String): Int = when (node) {
            "Bay" -> G_BAY
            "Counter" -> G_COUNTER
            "EndL" -> G_END_L
            "EndR" -> G_END_R
            "Sign" -> G_SIGN
            "Decor0" -> G_DECOR0
            "Decor1" -> G_DECOR1
            "Racks" -> G_RACKS
            "Entry" -> G_ENTRY
            "Back" -> G_BACK
            "Pet_Body" -> G_PET_BODY
            "Pet_Tail" -> G_PET_TAIL
            "Pet_Sleep" -> G_PET_SLEEP
            "FrontBay" -> G_FRONT_BAY
            "FrontDoor" -> G_FRONT_DOOR
            "FrontSign" -> G_FRONT_SIGN
            "Street" -> G_STREET
            "Sky" -> G_SKY
            "Neighbor" -> G_NEIGHBOR
            "Roof" -> G_ROOF
            "DoorL" -> G_DOOR_L
            "DoorR" -> G_DOOR_R
            "DoorUp" -> G_DOOR_UP
            else -> person(node) ?: item(node) ?: family(node, "Stand", G_STAND) ?: family(node, "Lamp", G_LAMP)
                ?: family(node, "PropTank", G_PROP) ?: family(node, "PropReef", G_REEF) ?: family(node, "PropFish", G_FISH) ?: -1
        }

        /** P<slot>_<part> (store_people.py). */
        private fun person(node: String): Int? {
            if (!node.startsWith("P") || '_' !in node) return null
            val slot = node.substring(1, node.indexOf('_')).toIntOrNull() ?: return null
            val part = PART_NAMES.indexOf(node.substringAfter('_'))
            if (slot !in 0 until MAX_PEOPLE || part < 0) return null
            return G_PERSON + slot * 8 + part
        }

        /** Prop_<NAME> (core Prop). */
        private fun item(node: String): Int? {
            if (!node.startsWith("Prop_")) return null
            val p = Prop.entries.firstOrNull { it.name == node.substring(5) } ?: return null
            return G_ITEM + p.ordinal
        }

        private fun family(node: String, prefix: String, base: Int): Int? {
            if (!node.startsWith(prefix)) return null
            val k = node.substring(prefix.length).toIntOrNull() ?: return null
            return if (k in 0 until KIND_COUNT) base + k else null
        }

        /** store.frag `uKind` from the material name (store_rooms.py / store_fronts.py name them by kind). */
        fun kindOf(material: String): Float = when {
            material.startsWith("floor_concrete") -> 11f
            material.startsWith("floor_marble") -> 12f
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
            material.startsWith("out_") -> 10f
            material.startsWith("sky") -> 13f
            else -> 0f
        }

        private fun smooth(u: Float): Float = u * u * (3f - 2f * u)

        // column-major in-place transforms: m = m · T / R
        fun identity(m: FloatArray) { m.fill(0f); m[0] = 1f; m[5] = 1f; m[10] = 1f; m[15] = 1f }

        fun translate(m: FloatArray, x: Float, y: Float, z: Float) {
            for (r in 0 until 4) m[12 + r] += m[r] * x + m[4 + r] * y + m[8 + r] * z
        }

        fun rotX(m: FloatArray, a: Float) {
            val c = cos(a); val s = sin(a)
            for (r in 0 until 4) {
                val y = m[4 + r]; val z = m[8 + r]
                m[4 + r] = y * c + z * s; m[8 + r] = -y * s + z * c
            }
        }

        fun rotY(m: FloatArray, a: Float) {
            val c = cos(a); val s = sin(a)
            for (r in 0 until 4) {
                val x = m[r]; val z = m[8 + r]
                m[r] = x * c - z * s; m[8 + r] = x * s + z * c
            }
        }

        fun rotZ(m: FloatArray, a: Float) {
            val c = cos(a); val s = sin(a)
            for (r in 0 until 4) {
                val x = m[r]; val y = m[4 + r]
                m[r] = x * c + y * s; m[4 + r] = -x * s + y * c
            }
        }
    }
}
