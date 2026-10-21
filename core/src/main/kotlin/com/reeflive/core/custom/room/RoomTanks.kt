package com.reeflive.core.custom.room

import com.reeflive.core.custom.CustomTankSave
import com.reeflive.core.custom.TankBuilds
import com.reeflive.core.custom.economy.Economy
import com.reeflive.core.custom.economy.SavedEconomy
import kotlin.math.roundToInt

/** A tank the room shop sells for another wall. */
data class TankOffer(val buildId: String, val price: Int, val level: Int)

/**
 * More tanks in the room (docs/ECONOMY.md §14): up to four, one against each wall, seen one at a time; the keeper
 * turns round the room to visit the others. The active tank is the top-level [CustomTankSave]; the rest ride in its
 * [CustomTankSave.others] as whole saves, so a tank not being looked at is simply saved, and on its turn it opens
 * like a tank reopened after time away (the hosts' catch-up). The money and the room's look belong to the room: they
 * stay on whichever save is on top. Everything here is pure; the hosts capture, restore and catch up.
 */
object RoomTanks {
    const val SIDES = 4
    /** What each wall is called (the side a tank stands on). */
    val SIDE_NAMES = listOf("Back wall", "Right wall", "Front wall", "Left wall")

    val OFFERS: List<TankOffer> = listOf(
        TankOffer(TankBuilds.NANO.id, 400, 1),
        TankOffer(TankBuilds.BREEDER.id, 800, 2),
        TankOffer(TankBuilds.LONG_REEF.id, 1400, 3),
        TankOffer(TankBuilds.DISPLAY_120.id, 2600, 5),
    )

    fun offer(buildId: String): TankOffer? = OFFERS.firstOrNull { it.buildId == buildId }

    /** The walls with a tank, round the room from the back. */
    fun sides(save: CustomTankSave): List<Int> = (save.others.map { it.side } + save.side).distinct().sorted()

    fun freeSide(save: CustomTankSave): Int? {
        val used = sides(save)
        // the next wall round from the tank in view first
        return (1..SIDES).map { (save.side + it) % SIDES }.firstOrNull { it !in used }
    }

    /** The next tank round the room in direction [dir] (+1 / -1) from the one in view, or null when it is the only one. */
    fun next(save: CustomTankSave, dir: Int): Int? {
        val used = sides(save)
        if (used.size < 2) return null
        for (k in 1 until SIDES) {
            val s = ((save.side + dir * k) % SIDES + SIDES) % SIDES
            if (s in used) return s
        }
        return null
    }

    /** How a tank is kept while another is in view: no tanks of its own, no money, no look. */
    private fun stored(s: CustomTankSave) = s.copy(others = emptyList(), economy = SavedEconomy(), decor = SavedDecor())

    /**
     * The save to open to look at the tank on wall [side]: that tank on top, carrying the room's money and look, with
     * [current] (just captured) put away among the others. Null when there is no tank there or it is already in view.
     */
    fun switchTo(current: CustomTankSave, side: Int): CustomTankSave? {
        if (side == current.side) return null
        val target = current.others.firstOrNull { it.side == side } ?: return null
        return target.copy(economy = current.economy, decor = current.decor, others = current.others.filter { it !== target } + stored(current))
    }

    /** A new tank ([fresh], just set up) goes on the next free wall and into view; [current] is put away. */
    fun withNew(current: CustomTankSave, fresh: CustomTankSave): CustomTankSave? {
        val side = freeSide(current) ?: return null
        return fresh.copy(side = side, economy = current.economy, decor = current.decor, others = current.others + stored(current))
    }

    /** "Start over" on one tank of several: it goes, and the next one round comes into view. Null when it was the only one. */
    fun dropActive(current: CustomTankSave): CustomTankSave? {
        val side = next(current, 1) ?: return null
        val target = current.others.first { it.side == side }
        return target.copy(economy = current.economy, decor = current.decor, others = current.others.filter { it !== target })
    }

    /** Checks and pays for a new tank of build [buildId]; the host then sets it up and calls [withNew]. */
    fun buy(current: CustomTankSave, buildId: String, eco: Economy, today: String): DecorResult {
        val o = offer(buildId) ?: return DecorResult.UNKNOWN
        if (freeSide(current) == null) return DecorResult.ROOM_FULL
        if (eco.level < o.level) return DecorResult.LEVEL_LOCKED
        if (!eco.spend(o.price, "New tank: ${TankBuilds.byId(buildId).label}", today)) return DecorResult.TOO_POOR
        return DecorResult.OK
    }

    /** The tanks for sale as room shop tiles (`tank:<build>`): locked by level, or for all of them once every wall has one. */
    fun tiles(current: CustomTankSave?, level: Int): List<Decor.Tile> {
        val full = current != null && freeSide(current) == null
        return OFFERS.map { o ->
            val b = TankBuilds.byId(o.buildId)
            Decor.Tile("tank:${o.buildId}", "${b.gallons.roundToInt()} gallon", o.price, o.level, false, false, full || level < o.level)
        }
    }
}
