package com.reeflive.core.custom.room

import com.reeflive.core.custom.economy.Economy
import kotlinx.serialization.Serializable

/**
 * One finish a stand can wear: its main colour ([paint]) and its second one ([trim]: the top, the frame or the shelves),
 * both sRGB 0xRRGGBB. tools/blender/stands.py models the parts; the renderer tints them.
 */
data class StandColor(val label: String, val paint: Int, val trim: Int)

/**
 * A stand model the keeper can buy for My tank (tools/blender/stands.py builds `models/stand_<id>.glb`, one set of
 * parts per tank build). The first of [colors] comes with it; the others cost [Decor.RECOLOR] each.
 */
data class StandModel(
    val id: String,
    val label: String,
    val blurb: String,
    /** Sand dollars (0: the one every tank starts on). */
    val price: Int,
    val level: Int,
    val colors: List<StandColor>,
) {
    val model: String get() = "models/stand_$id.glb"
}

/** Add-ons that fit any stand. */
enum class StandExtra(val id: String, val label: String, val blurb: String, val price: Int, val level: Int) {
    GLOW("glow", "Glow strip", "A soft blue light under the stand that glows on the floor.", 150, 2),
    BRASS("brass", "Gold handles", "Shiny gold handles and trim.", 80, 1);

    companion object {
        fun byId(id: String): StandExtra? = entries.firstOrNull { it.id == id }
    }
}

/**
 * A wall or floor finish: room.frag's pattern [style] ([RoomStyle]) in two colours (sRGB 0xRRGGBB). The ones in
 * [Decor.WALLS] / [Decor.FLOORS] are sold; each [RoomScene] also brings its own (price 0, not in the shop).
 */
data class Finish(val id: String, val label: String, val price: Int, val level: Int, val style: Int, val a: Int, val b: Int)

/** room.frag's wall (uWallStyle) and floor (uFloorStyle) patterns. */
object RoomStyle {
    const val PAINT = 0
    const val STRIPES = 1
    const val WAVES = 2
    const val BRICK = 3
    const val PANELS = 4
    const val LOGS = 5
    const val METAL = 6
    const val SHIPLAP = 7

    const val PLANKS = 0
    const val TILE = 1
    const val CHECKER = 2
    const val CARPET = 3
    const val SLATE = 4
    const val DECK = 5
    const val TATAMI = 6
}

/**
 * A room the tank can stand in (tools/blender/rooms.py builds `models/room_<id>.glb`; its windows show room.frag's
 * outside views). [sky] and [wall] colour the shop tile.
 */
data class RoomScene(
    val id: String,
    val label: String,
    val blurb: String,
    val price: Int,
    val level: Int,
    val wall: Finish,
    val floor: Finish,
    val sky: Int,
)

/**
 * What the keeper bought for the room and what is in use (docs/ECONOMY.md §10 "Your room"). Saved with My tank
 * ([com.reeflive.core.custom.CustomTankSave.decor], a defaulted field: older saves open on the classic white stand).
 * [owned] holds [Decor] ids: `stand:<id>`, `color:<stand>:<n>`, `extra:<id>`.
 */
@Serializable
data class SavedDecor(
    val owned: List<String> = emptyList(),
    val stand: String = Decor.DEFAULT_STAND,
    /** Index into the stand's colours. */
    val standColor: Int = 0,
    /** Extras switched on (bought ones can be switched off again). */
    val extras: List<String> = emptyList(),
    /** The room scene ([Decor.SCENES]). */
    val scene: String = Decor.DEFAULT_SCENE,
    /** Wallpaper and floor ([Decor.WALLS], [Decor.FLOORS]); empty: the scene's own. */
    val wall: String = "",
    val floor: String = "",
)

/** Why a decor purchase did not go through. */
enum class DecorResult { OK, OWNED, LEVEL_LOCKED, TOO_POOR, UNKNOWN }

/**
 * The room shop: stands, their colours and add-ons, priced against the care pay (about 40-80 sand dollars a day, so a
 * new stand is a few days of chores). Everything here is pure: the hosts keep the [SavedDecor] and pass the economy.
 */
object Decor {
    const val DEFAULT_STAND = "classic"
    /** A new colour for a stand already owned. */
    const val RECOLOR = 40

    private fun c(label: String, paint: Int, trim: Int) = StandColor(label, paint, trim)

    val STANDS: List<StandModel> = listOf(
        // Tee's own stand: white with an X-lattice door and open shelves (tank.py's StandBody)
        StandModel("classic", "Classic cabinet", "A white cabinet with a criss-cross door and open shelves.", 0, 1, listOf(
            c("White", 0xF1EDE6, 0x3B2518), c("Black", 0x26272A, 0x3B2518), c("Navy", 0x22344F, 0xD9CBB4), c("Sage", 0x8FA38A, 0xEFE8DC))),
        StandModel("shaker", "Shaker cabinet", "Two panel doors and a solid top, like a kitchen cabinet.", 240, 1, listOf(
            c("Oak", 0xB98A5A, 0x8B6240), c("Walnut", 0x5A3A26, 0x3B2518), c("Dove grey", 0xB7B8B4, 0x5A3A26), c("Cream", 0xEFE4CC, 0xB98A5A))),
        StandModel("iron", "Iron frame", "An open metal frame with two wooden shelves.", 300, 2, listOf(
            c("Black and oak", 0x1E1F22, 0xB98A5A), c("Black and walnut", 0x1E1F22, 0x5A3A26), c("White and oak", 0xE9E7E2, 0xB98A5A), c("Bronze and walnut", 0x6B4A2E, 0x4A2E1E))),
        StandModel("modern", "Modern gloss", "Smooth shiny doors with no handles and a shadow gap at the bottom.", 360, 2, listOf(
            c("Gloss white", 0xF4F4F2, 0x2A2B2E), c("Gloss black", 0x18191B, 0x2A2B2E), c("Coral", 0xE2725B, 0x2A2B2E), c("Ocean blue", 0x2F6E9E, 0x2A2B2E))),
        StandModel("rustic", "Barn wood", "Rough plank doors with cross braces and black hinges.", 420, 3, listOf(
            c("Weathered", 0x9C8A74, 0x2B2622), c("Honey", 0xC48A45, 0x2B2622), c("Charcoal", 0x48433F, 0x1B1917), c("Seafoam", 0x8CC2B0, 0x2B2622))),
        StandModel("reef_pro", "Reef pro cabinet", "A tall pro cabinet with a window onto the sump and a glowing top edge.", 700, 5, listOf(
            c("Graphite", 0x3A3D42, 0x8A9096), c("Arctic white", 0xEEF1F3, 0x8A9096), c("Midnight", 0x1C2433, 0x6E7F99), c("Lagoon", 0x2D8A8A, 0xD5E2E2))),
    )
    private val byId = STANDS.associateBy { it.id }

    const val DEFAULT_SCENE = "living"
    /** The tile that puts a scene's own wall or floor back. */
    const val OWN = "scene"

    private fun own(style: Int, a: Int, b: Int) = Finish(OWN, "Room's own", 0, 1, style, a, b)

    val SCENES: List<RoomScene> = listOf(
        RoomScene("living", "Cozy living room", "Warm walls, an oak floor, a sofa and a reading lamp.", 0, 1,
            own(RoomStyle.PAINT, 0xE6DCCB, 0xD8CCB6), own(RoomStyle.PLANKS, 0xA7774E, 0x8A6040), 0xE6DCCB),
        RoomScene("sunny", "Sunny window", "A big bright window onto a green garden, with soft curtains.", 300, 1,
            own(RoomStyle.PAINT, 0xF2EAD8, 0xE4D8C0), own(RoomStyle.PLANKS, 0xC29A6B, 0xA67E52), 0x9FD0EE),
        RoomScene("beach", "Beach house", "White boards, a surfboard and the sea right outside.", 450, 2,
            own(RoomStyle.SHIPLAP, 0xF1EFE8, 0xDAD6CC), own(RoomStyle.PLANKS, 0xD9C4A0, 0xBFA77F), 0x7CC4E0),
        RoomScene("cabin", "Snowy cabin", "Log walls, a crackling fireplace and snowy pines outside.", 500, 3,
            own(RoomStyle.LOGS, 0x8A5A34, 0x6B4226), own(RoomStyle.PLANKS, 0x7A5235, 0x5E3E27), 0xC9D6E2),
        RoomScene("blossom", "Blossom garden", "Paper screens, a low table and pink cherry trees outside.", 550, 3,
            own(RoomStyle.PAINT, 0xEFE6D6, 0xC9A27A), own(RoomStyle.TATAMI, 0xC9B98A, 0x6E7A4A), 0xF2C4D2),
        RoomScene("city", "City loft", "Brick walls and a tall window over the city lights.", 650, 4,
            own(RoomStyle.BRICK, 0x9A5440, 0xC9BBA8), own(RoomStyle.SLATE, 0x7A7A78, 0x5E5E5C), 0x4A5C7E),
        RoomScene("space", "Space station", "Metal walls, round windows and the Earth floating by.", 900, 5,
            own(RoomStyle.METAL, 0xB8C0C8, 0x8A949E), own(RoomStyle.DECK, 0x6C747C, 0x4A5158), 0x0B1430),
    )
    private val scenes = SCENES.associateBy { it.id }

    val WALLS: List<Finish> = listOf(
        Finish("seafoam", "Seafoam paint", 60, 1, RoomStyle.PAINT, 0xBFDDD2, 0xA9CBBF),
        Finish("navy", "Navy paint", 60, 1, RoomStyle.PAINT, 0x2E3E5C, 0x24324C),
        Finish("stripes", "Sunny stripes", 90, 1, RoomStyle.STRIPES, 0xF4E7C6, 0xE9C98A),
        Finish("shiplap", "White boards", 100, 2, RoomStyle.SHIPLAP, 0xF1EFE8, 0xDAD6CC),
        Finish("waves", "Wave print", 120, 2, RoomStyle.WAVES, 0xD9ECEE, 0x6FAFC0),
        Finish("brick", "Brick", 140, 2, RoomStyle.BRICK, 0x9A5440, 0xC9BBA8),
        Finish("panels", "Wood panels", 140, 3, RoomStyle.PANELS, 0x9C7148, 0x7A5236),
    )
    val FLOORS: List<Finish> = listOf(
        Finish("walnut", "Walnut planks", 80, 1, RoomStyle.PLANKS, 0x5E3E27, 0x4A2E1E),
        Finish("carpet", "Blue carpet", 90, 1, RoomStyle.CARPET, 0x5A7FA6, 0x4A6B8F),
        Finish("tile", "White tiles", 100, 1, RoomStyle.TILE, 0xEDEBE6, 0xB9B4AA),
        Finish("checker", "Checkerboard", 120, 2, RoomStyle.CHECKER, 0xEFEDE8, 0x2A2B2E),
        Finish("slate", "Slate", 140, 3, RoomStyle.SLATE, 0x6E7176, 0x55585C),
    )
    private val walls = WALLS.associateBy { it.id }
    private val floors = FLOORS.associateBy { it.id }

    /** A scene by id; an unknown one falls back to the living room. */
    fun scene(id: String?): RoomScene = scenes[id] ?: scenes.getValue(DEFAULT_SCENE)

    /** The wall and floor in use: the bought one, or the scene's own. */
    fun wall(d: SavedDecor): Finish = walls[d.wall] ?: scene(d.scene).wall
    fun floor(d: SavedDecor): Finish = floors[d.floor] ?: scene(d.scene).floor

    fun ownsScene(d: SavedDecor, id: String): Boolean = scenes[id]?.price == 0 || "scene:$id" in d.owned

    /** A stand by id; an unknown one (removed in a later version) falls back to the classic so the room still draws. */
    fun stand(id: String?): StandModel = byId[id] ?: byId.getValue(DEFAULT_STAND)

    fun standKey(id: String) = "stand:$id"
    fun colorKey(stand: String, n: Int) = "color:$stand:$n"
    fun extraKey(id: String) = "extra:$id"

    fun ownsStand(d: SavedDecor, id: String): Boolean = byId[id]?.price == 0 || standKey(id) in d.owned
    /** The first colour comes with the stand. */
    fun ownsColor(d: SavedDecor, stand: String, n: Int): Boolean = ownsStand(d, stand) && (n == 0 || colorKey(stand, n) in d.owned)
    fun ownsExtra(d: SavedDecor, id: String): Boolean = extraKey(id) in d.owned

    /** The stand in use and its colour (a colour index out of range reads as the first). */
    fun look(d: SavedDecor): Pair<StandModel, StandColor> {
        val s = stand(d.stand)
        return s to (s.colors.getOrNull(d.standColor) ?: s.colors[0])
    }

    /** The price of a decor id, or null when there is no such thing. */
    fun price(key: String): Int? {
        val p = key.split(':')
        return when (p[0]) {
            "stand" -> byId[p.getOrNull(1)]?.price
            "color" -> byId[p.getOrNull(1)]?.let { s -> p.getOrNull(2)?.toIntOrNull()?.takeIf { it in 1 until s.colors.size }?.let { RECOLOR } }
            "extra" -> StandExtra.byId(p.getOrNull(1) ?: "")?.price
            "scene" -> scenes[p.getOrNull(1)]?.price
            "wall" -> if (p.getOrNull(1) == OWN) 0 else walls[p.getOrNull(1)]?.price
            "floor" -> if (p.getOrNull(1) == OWN) 0 else floors[p.getOrNull(1)]?.price
            else -> null
        }
    }

    private fun level(key: String): Int {
        val p = key.split(':')
        return when (p[0]) {
            "stand", "color" -> byId[p.getOrNull(1)]?.level ?: 1
            "extra" -> StandExtra.byId(p.getOrNull(1) ?: "")?.level ?: 1
            "scene" -> scenes[p.getOrNull(1)]?.level ?: 1
            "wall" -> walls[p.getOrNull(1)]?.level ?: 1
            "floor" -> floors[p.getOrNull(1)]?.level ?: 1
            else -> 1
        }
    }

    private fun owns(d: SavedDecor, key: String): Boolean {
        val p = key.split(':')
        return when (p[0]) {
            "stand" -> ownsStand(d, p[1])
            "color" -> ownsColor(d, p[1], p.getOrNull(2)?.toIntOrNull() ?: 0)
            "scene" -> ownsScene(d, p[1])
            "wall", "floor" -> p.getOrNull(1) == OWN || key in d.owned
            else -> key in d.owned
        }
    }

    /**
     * Buys [key] with sand dollars from [eco] and puts it to use straight away (a stand with its first colour, a colour
     * on its stand, an extra switched on). Returns the new decor (unchanged unless [DecorResult.OK]).
     */
    fun buy(d: SavedDecor, key: String, eco: Economy, today: String): Pair<SavedDecor, DecorResult> {
        val price = price(key) ?: return d to DecorResult.UNKNOWN
        if (owns(d, key)) return d to DecorResult.OWNED
        if (eco.level < level(key)) return d to DecorResult.LEVEL_LOCKED
        if (!eco.spend(price, label(key), today)) return d to DecorResult.TOO_POOR
        return use(d.copy(owned = d.owned + key), key) to DecorResult.OK
    }

    /**
     * Puts something already owned to use: a stand (in its first colour), a stand colour (moving to that stand), a
     * scene (with its own wall and floor), a wallpaper or floor, or toggles an extra. Not owned: unchanged.
     */
    fun use(d: SavedDecor, key: String): SavedDecor {
        if (!owns(d, key)) return d
        val p = key.split(':')
        return when (p[0]) {
            "stand" -> if (d.stand == p[1]) d else d.copy(stand = p[1], standColor = 0)
            "color" -> d.copy(stand = p[1], standColor = p.getOrNull(2)?.toIntOrNull() ?: 0)
            "extra" -> if (p[1] in d.extras) d.copy(extras = d.extras - p[1]) else d.copy(extras = d.extras + p[1])
            "scene" -> if (d.scene == p[1]) d else d.copy(scene = p[1], wall = "", floor = "")
            "wall" -> d.copy(wall = if (p[1] == OWN) "" else p[1])
            "floor" -> d.copy(floor = if (p[1] == OWN) "" else p[1])
            else -> d
        }
    }

    /** How the room would look with [key] in use, owned or not (the shop's try-it-on preview). */
    fun preview(d: SavedDecor, key: String): SavedDecor =
        if (price(key) == null) d else use(d.copy(owned = d.owned + key), key).let { if (key.startsWith("extra:")) it.copy(extras = (d.extras + key.removePrefix("extra:")).distinct()) else it }

    /** What the ledger and the shop card call [key]. */
    fun label(key: String): String {
        val p = key.split(':')
        return when (p[0]) {
            "stand" -> byId[p.getOrNull(1)]?.label ?: "Stand"
            "color" -> byId[p.getOrNull(1)]?.let { s -> "${s.label}: ${s.colors.getOrNull(p.getOrNull(2)?.toIntOrNull() ?: 0)?.label ?: ""}" } ?: "Stand colour"
            "extra" -> StandExtra.byId(p.getOrNull(1) ?: "")?.label ?: "Extra"
            "scene" -> scenes[p.getOrNull(1)]?.label ?: "Room"
            "wall" -> walls[p.getOrNull(1)]?.label ?: "Room's own walls"
            "floor" -> floors[p.getOrNull(1)]?.label ?: "Room's own floor"
            else -> key
        }
    }

    /** One tile on the room shop card. */
    data class Tile(val key: String, val label: String, val price: Int, val level: Int, val owned: Boolean, val inUse: Boolean, val locked: Boolean, val swatch: Int? = null, val swatch2: Int? = null)

    /**
     * The stands, the current stand's colours, the extras, the scenes, then the walls and floors (the scene's own
     * first), as the room shop card shows them for a keeper at [level].
     */
    fun tiles(d: SavedDecor, level: Int): List<Tile> {
        val out = ArrayList<Tile>()
        for (s in STANDS) out += Tile(standKey(s.id), s.label, s.price, s.level, ownsStand(d, s.id), d.stand == s.id, level < s.level, s.colors[0].paint, s.colors[0].trim)
        val (cur, _) = look(d)
        cur.colors.forEachIndexed { n, c ->
            out += Tile(colorKey(cur.id, n), c.label, if (n == 0) 0 else RECOLOR, cur.level, ownsColor(d, cur.id, n), d.standColor == n, level < cur.level, c.paint, c.trim)
        }
        for (e in StandExtra.entries) out += Tile(extraKey(e.id), e.label, e.price, e.level, ownsExtra(d, e.id), e.id in d.extras, level < e.level)
        for (r in SCENES) out += Tile("scene:${r.id}", r.label, r.price, r.level, ownsScene(d, r.id), scene(d.scene).id == r.id, level < r.level, r.sky, r.wall.a)
        val sc = scene(d.scene)
        out += Tile("wall:$OWN", "Room's own", 0, 1, true, wall(d) === sc.wall, false, sc.wall.a, sc.wall.b)
        for (w in WALLS) out += Tile("wall:${w.id}", w.label, w.price, w.level, "wall:${w.id}" in d.owned, d.wall == w.id, level < w.level, w.a, w.b)
        out += Tile("floor:$OWN", "Room's own", 0, 1, true, floor(d) === sc.floor, false, sc.floor.a, sc.floor.b)
        for (f in FLOORS) out += Tile("floor:${f.id}", f.label, f.price, f.level, "floor:${f.id}" in d.owned, d.floor == f.id, level < f.level, f.a, f.b)
        return out
    }
}
