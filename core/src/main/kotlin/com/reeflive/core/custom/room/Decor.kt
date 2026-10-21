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
            else -> null
        }
    }

    private fun level(key: String): Int {
        val p = key.split(':')
        return when (p[0]) {
            "stand", "color" -> byId[p.getOrNull(1)]?.level ?: 1
            "extra" -> StandExtra.byId(p.getOrNull(1) ?: "")?.level ?: 1
            else -> 1
        }
    }

    private fun owns(d: SavedDecor, key: String): Boolean {
        val p = key.split(':')
        return when (p[0]) {
            "stand" -> ownsStand(d, p[1])
            "color" -> ownsColor(d, p[1], p.getOrNull(2)?.toIntOrNull() ?: 0)
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
     * Puts something already owned to use: a stand (in its first colour), a stand colour (moving to that stand), or
     * toggles an extra. Not owned: unchanged.
     */
    fun use(d: SavedDecor, key: String): SavedDecor {
        if (!owns(d, key)) return d
        val p = key.split(':')
        return when (p[0]) {
            "stand" -> if (d.stand == p[1]) d else d.copy(stand = p[1], standColor = 0)
            "color" -> d.copy(stand = p[1], standColor = p.getOrNull(2)?.toIntOrNull() ?: 0)
            "extra" -> if (p[1] in d.extras) d.copy(extras = d.extras - p[1]) else d.copy(extras = d.extras + p[1])
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
            else -> key
        }
    }

    /** One tile on the room shop card. */
    data class Tile(val key: String, val label: String, val price: Int, val level: Int, val owned: Boolean, val inUse: Boolean, val locked: Boolean, val swatch: Int? = null, val swatch2: Int? = null)

    /** The stands, the current stand's colours and the extras, as the room shop card shows them for a keeper at [level]. */
    fun tiles(d: SavedDecor, level: Int): List<Tile> {
        val out = ArrayList<Tile>()
        for (s in STANDS) out += Tile(standKey(s.id), s.label, s.price, s.level, ownsStand(d, s.id), d.stand == s.id, level < s.level, s.colors[0].paint, s.colors[0].trim)
        val (cur, _) = look(d)
        cur.colors.forEachIndexed { n, c ->
            out += Tile(colorKey(cur.id, n), c.label, if (n == 0) 0 else RECOLOR, cur.level, ownsColor(d, cur.id, n), d.standColor == n, level < cur.level, c.paint, c.trim)
        }
        for (e in StandExtra.entries) out += Tile(extraKey(e.id), e.label, e.price, e.level, ownsExtra(d, e.id), e.id in d.extras, level < e.level)
        return out
    }
}
