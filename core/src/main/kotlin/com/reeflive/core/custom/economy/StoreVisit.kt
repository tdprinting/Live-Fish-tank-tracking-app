package com.reeflive.core.custom.economy

import com.reeflive.core.sim.RockData
import com.reeflive.core.sim.Simulation
import com.reeflive.core.sim.SpeciesCatalog
import com.reeflive.core.sim.StockGroup
import com.reeflive.core.sim.StockSize
import com.reeflive.core.sim.Stocking
import kotlin.math.min

/**
 * One display tank in a store (ECONOMY §5 "Visiting a store"): a section's sign, what swims in it today and how the
 * tank is set up ([StoreDisplays]: its size and type, rock, light and decor corals).
 */
data class DisplayTank(val title: String, val items: List<StockItem>, val setup: DisplaySetup = StoreDisplays.setup(FishStore.SHELLYS, title)) {
    /** Animals swimming in it (a few of each; snails by the handful but not all of them). */
    val animals: Int get() = items.sumOf { StoreVisit.shown(it) }
}

/** One animal's card in a store, as the keeper sees it today. */
data class ShelfCard(
    val speciesId: String,
    val name: String,
    val group: StockGroup,
    val price: Int,
    /** Left on the shelf today (after what this keeper already bought). */
    val left: Int,
    val deal: Boolean,
    val wanted: Boolean,
    val level: Int,
    val locked: Boolean,
    /** One line when it fits the keeper's tank badly ([Stores.warning]); the store still sells it. */
    val warning: String?,
)

/** What happened at the counter: the result and the species bought, one entry per animal. */
data class Checkout(val result: BuyResult, val bought: List<String>, val total: Int)

/**
 * Walking around a store: its day's shelf split into display tanks by section, and the little sim each tank runs while
 * it is on screen (one at a time; the keeper's own tank waits meanwhile and catches up after).
 */
object StoreVisit {
    /** Most of one species swimming in a display tank (the shelf can hold more; they are "in the back"). */
    const val MAX_SHOWN = 6

    private val MUSHROOMS_POLYPS = setOf("discosoma_sp", "rhodactis_sp", "amplexidiscus_fenestrafer", "zoanthus_sp", "clavularia_sp", "briareum_sp", "rhodactis_inchoata")
    private val LEATHERS = setOf("sarcophyton_sp", "lobophytum_sp", "sinularia_sp")
    private val STONY = setOf("duncanopsammia_axifuga", "fimbriaphyllia_ancora", "fimbriaphyllia_divisa", "euphyllia_glabrescens",
        "euphyllia_glabrescens_gold", "homophyllia_australis")
    private val CLEANUP = setOf("nassarius_sp", "lithopoma_tectum", "clibanarius_tricolor", "paguristes_cadenati", "percnon_gibbesi", "ophiocoma_sp", "lysmata_boggessi")

    private class Section(val title: String, val takes: (Listing) -> Boolean)

    private fun cm(l: Listing) = Stocking.entry(l.speciesId)?.typicalCm ?: 10.0
    private fun fish(l: Listing) = l.group == StockGroup.FISH
    private fun inverts(l: Listing) = l.group == StockGroup.INVERTS
    private fun corals(l: Listing) = l.group == StockGroup.CORALS

    private fun sections(store: FishStore): List<Section> = when (store) {
        FishStore.SHELLYS -> listOf(Section("Starter fish", ::fish), Section("Clean-up crew", ::inverts), Section("Easy corals", ::corals))
        FishStore.FRAG_SHACK -> listOf(
            Section("Mushrooms & polyps") { it.speciesId in MUSHROOMS_POLYPS },
            Section("Leathers") { it.speciesId in LEATHERS },
            Section("Stony corals (LPS)") { it.speciesId in STONY },
            Section("Soft corals & rare frags") { true },
        )
        FishStore.BIG_BLUE -> listOf(
            Section("Community fish") { fish(it) && cm(it) < 12 },
            Section("Bigger fish", ::fish),
            Section("Shrimp & crabs", ::inverts),
            Section("Corals", ::corals),
        )
        FishStore.DEEP_REEF -> listOf(Section("Rare fish", ::fish), Section("Rare inverts & corals") { true })
    }

    /** Today's display tanks at [store], in walking order; empty sections are skipped. */
    fun tanks(store: FishStore, day: String): List<DisplayTank> {
        val left = Stores.stock(store, day).toMutableList()
        val out = ArrayList<DisplayTank>()
        // Deep Reef's window tank: its priciest animal, alone under a spotlight
        if (store == FishStore.DEEP_REEF) left.maxByOrNull { it.price }?.let { left -= it; out += DisplayTank("The showpiece", listOf(it), StoreDisplays.setup(store, "The showpiece")) }
        for (s in sections(store)) {
            val mine = left.filter { s.takes(it.listing) }
            if (mine.isEmpty()) continue
            left -= mine.toSet()
            out += DisplayTank(s.title, mine, StoreDisplays.setup(store, s.title))
        }
        if (left.isNotEmpty()) out += DisplayTank("More", left, StoreDisplays.setup(store, "More"))
        return out
    }

    fun shown(item: StockItem): Int = min(item.count, MAX_SHOWN)

    /**
     * The sim for one display tank: its setup's tank, rock and light ([StoreDisplays.build]; noon, so the lights are
     * on whatever the keeper's clock says) with today's animals in it at shop size. The host runs it like any tank
     * while it is on screen.
     */
    fun build(tank: DisplayTank, species: SpeciesCatalog, rock: RockData, seed: Long): Simulation {
        tank.items.forEach { species.ensure(it.listing.speciesId) }
        val sim = StoreDisplays.build(tank, species, rock, seed) { s, plugs ->
            // the store keeps its display tanks fed; frags sit on the racks' plugs
            val rng = s.rng.fork(57)
            for (it in tank.items) {
                val id = it.listing.speciesId
                when {
                    !species[id].isCoral -> Stocking.addMany(s, id, shown(it), size = StockSize.JUVENILE)
                    plugs != null -> StoreDisplays.onPlugs(s, id, shown(it), plugs, rng)
                    else -> StoreDisplays.onRock(s, id, shown(it))
                }
            }
        }
        sim.stepWall(0.0)
        return sim
    }

    /** The cards of [tank] for this keeper ([mine] is their tank, for the warnings). */
    fun cards(e: Economy, store: FishStore, tank: DisplayTank, mine: Simulation, day: String): List<ShelfCard> {
        val wanted = Stores.wanted(store, day).toSet()
        return tank.items.map { i ->
            val id = i.listing.speciesId
            ShelfCard(
                id, Stocking.entry(id)?.common ?: id, i.listing.group, i.price, e.left(store, i, day), i.deal, id in wanted,
                i.listing.level, !e.unlocked(i.listing), Stores.warning(mine, id),
            )
        }
    }

    /** The card for an animal tapped in a display tank ([speciesId] and sex [role] of the organism or coral). */
    fun itemFor(tank: DisplayTank, speciesId: String, role: String? = null): StockItem? {
        val same = tank.items.filter { it.listing.species == speciesId }
        return same.firstOrNull { Stocking.roleOf(it.listing.speciesId).equals(role, true) } ?: same.firstOrNull()
    }
}
