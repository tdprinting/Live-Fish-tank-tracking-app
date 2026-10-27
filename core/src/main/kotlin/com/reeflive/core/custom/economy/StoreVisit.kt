package com.reeflive.core.custom.economy

import com.reeflive.core.custom.CustomTank
import com.reeflive.core.sim.RockData
import com.reeflive.core.sim.Simulation
import com.reeflive.core.sim.SpeciesCatalog
import com.reeflive.core.sim.StockGroup
import com.reeflive.core.sim.StockSize
import com.reeflive.core.sim.Stocking
import kotlin.math.min
import kotlin.time.TimeSource

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

/**
 * What happened at the counter: the result, the species bought (one entry per animal) and the supply packs bought
 * (supply id → packs, already on the shelf).
 */
data class Checkout(val result: BuyResult, val bought: List<String>, val total: Int, val supplies: Map<String, Int> = emptyMap())

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
        "euphyllia_glabrescens_gold", "homophyllia_australis",
        "micromussa_lordhowensis", "caulastrea_furcata", "trachyphyllia_geoffroyi", "plerogyra_sinuosa")
    private val SPS = setOf("montipora_capricornis", "seriatopora_hystrix", "acropora_millepora")
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
            Section("Small-polyp stony (SPS)") { it.speciesId in SPS },
            Section("Soft corals & rare frags") { true },
        )
        // the superstore: a long wall of tanks, one for each kind of animal
        FishStore.BIG_BLUE -> listOf(
            Section("Community fish") { fish(it) && cm(it) < 12 },
            Section("Bigger fish", ::fish),
            Section("Clean-up crew") { it.speciesId in CLEANUP },
            Section("Shrimp & crabs", ::inverts),
            Section("Easy corals") { corals(it) && it.level <= 2 },
            Section("Showy corals", ::corals),
        )
        FishStore.DEEP_REEF -> listOf(Section("Rare fish", ::fish), Section("Rare inverts", ::inverts), Section("Rare corals") { true })
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
     * The sim for one display tank: its setup's tank, rock and light ([DisplayBuild]; noon, so the lights are on whatever
     * the keeper's clock says) with today's animals in it at shop size. The host runs it like any tank while it is on
     * screen. Built all at once: the pages use [DisplayBuild] to spread the work over frames.
     */
    fun build(tank: DisplayTank, species: SpeciesCatalog, rock: RockData, seed: Long): Simulation = DisplayBuild(tank, species, rock, seed).run()

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

/**
 * A display tank's sim, built a little at a time. Its rockwork is a real rock-physics settle (several seconds of sim
 * time per pass) and takes far longer than a frame, so the host calls [step] with a few milliseconds of budget on each
 * animation frame (while the keeper is still on the street, or in the aisle for the next tank) and takes [sim] when
 * [step] returns true. [run] does it all in one go (tests, other hosts); the result is the same sim either way.
 *
 * The tank is [tank]'s kind's glass, its rockwork ([StoreDisplays.scapeSteps]), its light, the store's animals at shop
 * size, then the decor corals (after the stock, so the stock gets the best spots).
 */
class DisplayBuild(private val tank: DisplayTank, private val species: SpeciesCatalog, private val rock: RockData, private val seed: Long) {
    private var built: Simulation? = null
    private val work: Iterator<Unit> = iterator {
        for (item in tank.items) species.ensure(item.listing.speciesId)
        yield(Unit)
        val setup = tank.setup
        val sim = CustomTank.fresh(setup.kind.build, species, rock, 12 * 3600.0, seed)
        yield(Unit)
        for (u in StoreDisplays.scapeSteps(sim, setup, seed.toInt())) yield(u)
        for (c in 0 until min(setup.light.gains.size, sim.lights.gains.size)) sim.lights.gains[c] = setup.light.gains[c]
        val plugs = if (setup.scape == DisplayScape.RACKS) BooleanArray(StoreDisplays.plugs.size) else null
        // the store keeps its display tanks fed; frags sit on the racks' plugs
        val rng = sim.rng.fork(57)
        for (item in tank.items) {
            val id = item.listing.speciesId
            when {
                !species[id].isCoral -> Stocking.addMany(sim, id, StoreVisit.shown(item), size = StockSize.JUVENILE)
                plugs != null -> StoreDisplays.onPlugs(sim, id, StoreVisit.shown(item), plugs, rng)
                else -> StoreDisplays.onRock(sim, id, StoreVisit.shown(item))
            }
            yield(Unit)
        }
        val forSale = tank.items.map { it.listing.speciesId }.toSet()
        val decorRng = sim.rng.fork(91)
        for (id in setup.decor) {
            if (id in forSale) continue
            if (plugs != null) StoreDisplays.onPlugs(sim, id, 2, plugs, decorRng) else StoreDisplays.onRock(sim, id, 1)
            yield(Unit)
        }
        sim.stepWall(0.0)
        built = sim
    }

    /** The finished sim, or null while [step] has more to do. */
    val sim: Simulation? get() = built

    /** Works for about [budgetMs] (at least one unit, which can take a few tens of ms). True when the sim is done. */
    fun step(budgetMs: Double): Boolean {
        if (built != null) return true
        val t0 = TimeSource.Monotonic.markNow()
        while (work.hasNext()) {
            work.next()
            if (built != null) return true
            if (t0.elapsedNow().inWholeMicroseconds >= budgetMs * 1000.0) return false
        }
        return built != null
    }

    /** All of it now. */
    fun run(): Simulation { while (!step(Double.MAX_VALUE)) Unit; return built!! }
}
