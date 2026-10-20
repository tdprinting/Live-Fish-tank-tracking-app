package com.reeflive.core.sim

/**
 * Running counts of what the keeper did to the tank (and a few milestones the tank reached), for My tank's daily care
 * list (core custom/economy). Plain counters bumped where the commands land, so reading them is free and the sim
 * needs no listeners; a reader keeps its own last-seen values and looks for increases. Not saved: they start at 0
 * with each sim.
 */
class CareLog {
    /** Feedings with fish food, and with coral / filter-feeder food (powder, cyclops, plankton, pods). */
    var fishFeeds = 0
    var coralFeeds = 0
    /** [Command.Maintain] count per [MaintenanceTask] ordinal. */
    val maintained = IntArray(MaintenanceTask.entries.size)
    /** Water changes of at least [MIN_CHANGE] of the system. */
    var waterChanges = 0
    /** Flipper passes on the glass ([Command.ScrapeGlass]). */
    var scrapes = 0
    var anemoneSplits = 0
    var babiesReleased = 0

    fun fed(kind: FoodKind) { if (kind.suspended) coralFeeds++ else fishFeeds++ }

    companion object {
        const val MIN_CHANGE = 0.05
    }
}
