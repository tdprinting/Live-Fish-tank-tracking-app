package com.reeflive.app.render

import com.reeflive.core.custom.room.Decor
import com.reeflive.core.custom.room.Finish
import com.reeflive.core.custom.room.SavedDecor
import kotlin.math.abs
import kotlin.math.pow

/**
 * How the room around the tank looks (core custom/room/Decor.kt): the stand model and its two colours (sRGB
 * 0xRRGGBB), the extras, the room scene and its walls and floor (a RoomStyle pattern in two colours each). One
 * immutable value: the hosts swap it whole ([RoomControl.look]).
 */
data class RoomLook(
    val stand: String = "classic",
    val paint: Int = 0xF1EDE6,
    val trim: Int = 0x3B2518,
    val glow: Boolean = false,
    val brass: Boolean = false,
    val scene: String = "living",
    val wall: Finish = Decor.scene("living").wall,
    val floor: Finish = Decor.scene("living").floor,
) {
    companion object {
        /** Tee's own tank (Watch): the white cabinet in the living room. */
        val DEFAULT = RoomLook()

        /** The look of a My tank room. */
        fun of(d: SavedDecor): RoomLook {
            val (s, c) = Decor.look(d)
            return RoomLook(s.id, c.paint, c.trim, "glow" in d.extras, "brass" in d.extras, Decor.scene(d.scene).id, Decor.wall(d), Decor.floor(d))
        }
    }
}

/** What the hosts tell the renderer about the room. Any thread writes, the GL thread reads. */
class RoomControl {
    @Volatile var look: RoomLook = RoomLook.DEFAULT
    /** Draw the room at all (off in the fish stores, which bring their own). */
    @Volatile var visible: Boolean = true
    /** Bumped by [showRoom]: the camera steps back to see the tank on its stand. */
    @Volatile var viewRequest: Int = 0
        private set

    /** Steps the camera back to see the tank on its stand in its room (the room shop's try-on). */
    fun showRoom() { viewRequest++ }
}

/** Room layout shared by tools/blender/rooms.py, stands.py and room.frag. */
object HomeRoomLayout {
    const val FLOOR_Y = -82f
    /** The room models are authored around Tee's 4 ft tank: its length and centre. */
    const val STOCK_LENGTH = 121.9f
    /**
     * Top centre of the side table left of the stand (x, y, z cm): kept free for a small prop such as a radio. It does
     * not move with the tank's size (every tank's left glass is at x = 0).
     */
    val RADIO = floatArrayOf(-32f, -24f, 22f)
    fun stand(id: String) = "models/stand_$id.glb"
    fun room(scene: String) = "models/room_$scene.glb"
}

/**
 * GL-thread side of the room: the stand under the tank and the room it stands in, drawn with room.frag. The stand
 * model holds one node per tank build (stands.py); the one nearest the glass on screen ([Camera.TANK]) is drawn. The
 * room's Left* props stay put (the left glass is always at x = 0), Right* ones follow the right glass, the rest is
 * centred on the tank. About 25 draw calls and 6k triangles, all static: no per-frame allocation.
 */
internal class HomeRoomScene {
    private class Piece(val part: DrawPart, val kind: Float, val grain: Float, val anchor: Int, val glowAlways: Boolean, val view: Float)

    private var standModel: GpuModel? = null
    private var roomModel: GpuModel? = null
    private var standFor = -1f
    private var pieces: Array<Piece> = emptyArray()
    private var roomPieces: Array<Piece> = emptyArray()
    private val mat = FloatArray(16)
    private val paint = FloatArray(3)
    private val trim = FloatArray(3)
    private val wallA = FloatArray(3)
    private val wallB = FloatArray(3)
    private val floorA = FloatArray(3)
    private val floorB = FloatArray(3)
    private var lastLook: RoomLook? = null
    private val lampPos = FloatArray(4)
    private val lampCol = FloatArray(3)
    private var lampAnchor = ANCHOR_LEFT

    /** Loads (asynchronously) what [look] needs; true once both models are on the GPU. */
    fun ready(look: RoomLook, assets: (String) -> GpuModel?): Boolean {
        val s = assets(HomeRoomLayout.stand(look.stand)) ?: assets(HomeRoomLayout.stand(RoomLook.DEFAULT.stand))
        val r = assets(HomeRoomLayout.room(look.scene)) ?: assets(HomeRoomLayout.room(RoomLook.DEFAULT.scene))
        if (s !== standModel || Camera.TANK[0] + Camera.TANK[2] * 1000f != standFor) {
            standModel = s
            standFor = Camera.TANK[0] + Camera.TANK[2] * 1000f
            pieces = if (s == null) emptyArray() else pick(s)
        }
        if (r !== roomModel) {
            roomModel = r
            roomPieces = if (r == null) emptyArray() else r.parts.map { piece(it, anchorOf(it.node.name)) }.toTypedArray()
            lampPos[3] = 0f
            r?.parts?.firstOrNull { it.node.vec3("lampPos") != null }?.node?.let { n ->
                val p = n.vec3("lampPos")!!
                lampAnchor = anchorOf(n.name)
                lampPos[0] = p[0]; lampPos[1] = p[1]; lampPos[2] = p[2]; lampPos[3] = 1f
                val c = n.vec3("lampColor") ?: floatArrayOf(1f, 0.75f, 0.45f)
                lampCol[0] = c[0]; lampCol[1] = c[1]; lampCol[2] = c[2]
            }
        }
        if (look != lastLook) {
            lastLook = look
            srgb(look.paint, paint); srgb(look.trim, trim)
            srgb(look.wall.a, wallA); srgb(look.wall.b, wallB); srgb(look.floor.a, floorA); srgb(look.floor.b, floorB)
        }
        return s != null || r != null
    }

    /** The stand node whose footprint is nearest the tank on screen. */
    private fun pick(m: GpuModel): Array<Piece> {
        var best: String? = null
        var bestD = Float.MAX_VALUE
        for (part in m.parts) {
            val len = part.node.float("length") ?: continue
            val dep = part.node.float("depth") ?: continue
            val d = abs(len - Camera.TANK[0]) + abs(dep - Camera.TANK[2])
            if (d < bestD) { bestD = d; best = part.node.name }
        }
        return m.parts.filter { it.node.name == best }.map { piece(it, ANCHOR_STAND) }.toTypedArray()
    }

    private fun piece(p: DrawPart, anchor: Int): Piece {
        val n = p.material.src.name
        return Piece(p, kindOf(n), if ("wood" in n || "plank" in n) 1f else 0f, anchor, n == "glow_pro", viewOf(n))
    }

    /**
     * Draws the room and stand: opaque parts ([blended] false) or the clear ones. Time of day [day] 0..1 (noon 1).
     * Returns the draw calls.
     */
    fun draw(p: ShaderProgram, white: Texture, look: RoomLook, blended: Boolean, day: Float): Int {
        if (pieces.isEmpty() && roomPieces.isEmpty()) return 0
        val len = Camera.TANK[0]
        val dep = Camera.TANK[2]
        p.use()
        p.set3f("uPaint", paint[0], paint[1], paint[2])
        p.set3f("uTrim", trim[0], trim[1], trim[2])
        p.set1f("uBrass", if (look.brass) 1f else 0f)
        p.set1f("uGlowStrip", if (look.glow) 1f else 0f)
        p.set1f("uDay", day)
        p.set4f("uLampPos", lampPos[0] + shiftOf(lampAnchor, len), lampPos[1], lampPos[2], lampPos[3])
        val lampOn = 1f - day * 0.85f
        p.set3f("uLampCol", lampCol[0] * lampOn, lampCol[1] * lampOn, lampCol[2] * lampOn)
        p.set4f("uTankBox", len, Camera.TANK[1], dep, 0f)
        p.set4f("uFoot", -2f, len + 2f, -2f, dep + 2f)
        val centre = shiftOf(ANCHOR_CENTRE, len)
        p.set4f("uRoomBox", centre + HomeRoomLayout.STOCK_LENGTH * 0.5f - ROOM_HALF, centre + HomeRoomLayout.STOCK_LENGTH * 0.5f + ROOM_HALF, ROOM_BACK, HomeRoomLayout.FLOOR_Y)
        p.set1f("uFloorStyle", look.floor.style.toFloat())
        p.set1f("uWallStyle", look.wall.style.toFloat())
        p.set3f("uFloorA", floorA[0], floorA[1], floorA[2])
        p.set3f("uFloorB", floorB[0], floorB[1], floorB[2])
        p.set3f("uWallA", wallA[0], wallA[1], wallA[2])
        p.set3f("uWallB", wallB[0], wallB[1], wallB[2])
        var calls = 0
        for (pc in roomPieces) calls += drawPiece(p, white, pc, blended, look, len)
        for (pc in pieces) calls += drawPiece(p, white, pc, blended, look, len)
        return calls
    }

    private fun drawPiece(p: ShaderProgram, white: Texture, pc: Piece, blended: Boolean, look: RoomLook, len: Float): Int {
        val part = pc.part
        if (part.material.blended != blended) return 0
        if (pc.kind == KIND_GLOW && !pc.glowAlways && !look.glow) return 0
        part.material.apply(p, white)
        p.set1f("uKind", pc.kind)
        p.set1f("uGrain", pc.grain)
        p.set1f("uGlowOn", if (pc.glowAlways || look.glow) 1f else 0f)
        p.set1f("uOutside", pc.view)
        part.node.world.copyInto(mat)
        mat[12] += shiftOf(pc.anchor, len)
        p.setMat4("uModel", mat)
        part.mesh.draw()
        return 1
    }

    companion object {
        private const val ROOM_HALF = 360f
        private const val ROOM_BACK = -45f
        private const val ANCHOR_STAND = 0
        private const val ANCHOR_CENTRE = 1
        private const val ANCHOR_LEFT = 2
        private const val ANCHOR_RIGHT = 3
        private const val KIND_GLOW = 5f

        private fun anchorOf(node: String): Int = when {
            node.startsWith("Left") -> ANCHOR_LEFT
            node.startsWith("Right") -> ANCHOR_RIGHT
            else -> ANCHOR_CENTRE
        }

        /** How far a room node moves for a tank of length [len]: the room is authored around Tee's 4 ft tank. */
        private fun shiftOf(anchor: Int, len: Float): Float = when (anchor) {
            ANCHOR_CENTRE -> (len - HomeRoomLayout.STOCK_LENGTH) * 0.5f
            ANCHOR_RIGHT -> len - HomeRoomLayout.STOCK_LENGTH
            else -> 0f
        }

        private fun srgb(c: Int, out: FloatArray) {
            for (i in 0 until 3) {
                val v = ((c shr (16 - 8 * i)) and 0xFF) / 255f
                out[i] = v.pow(2.2f)
            }
        }

        /** room.frag `uKind` from the material name (stands.py / rooms.py name them by kind). */
        fun kindOf(material: String): Float = when {
            material.startsWith("paint") -> 1f
            material.startsWith("trim") && !material.startsWith("trim_baseboard") -> 2f
            material.startsWith("metal") -> 3f
            material.startsWith("dark") -> 4f
            material.startsWith("glow") -> 5f
            material.startsWith("window") -> 6f
            material.startsWith("floor") -> 7f
            material.startsWith("wall") -> 8f
            material.startsWith("ceiling") -> 9f
            material.startsWith("lamp") -> 10f
            material.startsWith("rug") -> 11f
            material.startsWith("leaf") -> 12f
            material.startsWith("bin_clear") || material.startsWith("sump_glass") -> 13f
            material.startsWith("view") -> 14f
            material.startsWith("fire") -> 15f
            else -> 0f
        }

        /** room.frag `uOutside` for a window material named `view_<scene>` (rooms.py). */
        fun viewOf(material: String): Float = when (material.removePrefix("view_")) {
            "garden" -> 1f
            "beach" -> 2f
            "city" -> 3f
            "snow" -> 4f
            "blossom" -> 5f
            "space" -> 6f
            else -> 0f
        }
    }
}
