package com.reeflive.core.custom.economy

import com.reeflive.core.geom.Rng
import com.reeflive.core.sim.LifeCycle
import com.reeflive.core.sim.Simulation
import com.reeflive.core.sim.StockGroup
import com.reeflive.core.sim.Stocking
import kotlin.math.max
import kotlin.math.roundToInt

/** One species the stores can sell: when it unlocks and what it costs before a store's price level (ECONOMY §5). */
data class Listing(val speciesId: String, val group: StockGroup, val level: Int, val price: Int)

/**
 * Prices and unlock levels, close to 2026 US store prices (one sand dollar a dollar). A species added to the sim
 * later and not listed here gets [fallback] from its gallery group and size until it is.
 */
object PriceBook {
    private fun l(id: String, level: Int, price: Int) = Listing(id, Stocking.entry(id)?.group ?: StockGroup.FISH, level, price)

    val LISTINGS: List<Listing> = listOf(
        l("nassarius_sp", 1, 4), l("lithopoma_tectum", 1, 3), l("clibanarius_tricolor", 1, 3), l("paguristes_cadenati", 1, 8),
        l("chromis_viridis", 1, 12), l("amphiprion_ocellaris", 1, 25), l("sphaeramia_nematoptera", 1, 20), l("salarias_fasciatus", 1, 25),
        l("capnella_sp", 1, 15), l("discosoma_sp", 1, 15), l("briareum_sp", 1, 20), l("clavularia_sp", 1, 20),
        l("ecsenius_bicolor", 2, 30), l("nemateleotris_magnifica", 2, 30), l("lysmata_amboinensis", 2, 30), l("stenopus_hispidus", 2, 25), l("percnon_gibbesi", 2, 20), l("ophiocoma_sp", 2, 20),
        l("sabellastarte_sp", 2, 20), l("zoanthus_sp", 2, 25), l("rhodactis_sp", 2, 35), l("sinularia_sp", 2, 30),
        l("ecsenius_stigmatura", 3, 35), l("valenciennea_puellaris", 3, 35), l("pholidichthys_leucotaenia", 3, 30),
        l("labroides_dimidiatus", 3, 30), l("sarcophyton_sp", 3, 45), l("lobophytum_sp", 3, 40),
        l("cirrhitichthys_falco", 4, 45), l("ecsenius_midas", 4, 55), l("pseudochromis_indigo", 4, 50), l("entacmaea_quadricolor", 4, 70),
        l("amplexidiscus_fenestrafer", 4, 55), l("tubipora_musica", 5, 60), l("paracanthurus_hepatus", 5, 80),
        l("macropharyngodon_bipartitus", 6, 70), l("euprymna_berryi", 7, 150),
    )
    private val byId = LISTINGS.associateBy { it.speciesId }

    /** Species that are never sold: hitchhikers that come in on rock by themselves. */
    val NEVER_SOLD = setOf("asterina_sp")

    fun listing(speciesId: String): Listing? = byId[speciesId] ?: Stocking.entry(speciesId)?.takeIf { speciesId !in NEVER_SOLD }?.let { fallback(it.speciesId, it.group, it.typicalCm) }

    /** Unlisted species: about a dollar per adult centimetre plus a base, unlocking with the middle stores. */
    fun fallback(id: String, group: StockGroup, adultCm: Double): Listing =
        Listing(id, group, 3, (10 + adultCm * 2.5).roundToInt().coerceIn(5, 120))

    /** Every species a store can carry, in gallery order. */
    val all: List<Listing> get() = Stocking.CATALOG.mapNotNull { listing(it.speciesId) }
}

/** The four fish stores (ECONOMY §5); [id] names the Blender model and its picture (`store_<id>`). */
enum class FishStore(
    val id: String,
    val label: String,
    val greeting: String,
    /** Player level the store opens at. */
    val level: Int,
    /** Price level against [PriceBook] prices. */
    val priceMult: Double,
    /** Share of a species' price the store pays for one of yours, by group (0 = does not buy that group). */
    val buyFish: Double,
    val buyInverts: Double,
    val buyCorals: Double,
    /** Items on the shelf each day. */
    val stockSize: Int,
) {
    SHELLYS("shellys", "Shelly's Pet Shop", "Hi there! Every reef starts with a few good friends.", 1, 1.0, 0.4, 0.4, 0.4, 6),
    FRAG_SHACK("frag_shack", "Frag Shack", "Corals, corals, corals! Fresh frags under the blue lights.", 2, 0.85, 0.0, 0.0, 0.65, 6),
    BIG_BLUE("big_blue", "Big Blue Aquatics", "Welcome to the wall of tanks! We have a bit of everything.", 4, 1.1, 0.6, 0.6, 0.5, 12),
    DEEP_REEF("deep_reef", "Deep Reef Imports", "Only the rarest, healthiest animals. Have a look.", 6, 1.25, 0.75, 0.75, 0.75, 4);

    fun carries(l: Listing): Boolean = when (this) {
        SHELLYS -> l.level <= 3 && (l.group != StockGroup.CORALS || l.level <= 1)
        FRAG_SHACK -> l.group == StockGroup.CORALS
        BIG_BLUE -> l.level <= 5
        DEEP_REEF -> l.level >= 4
    }

    fun buyBack(group: StockGroup): Double = when (group) {
        StockGroup.FISH -> buyFish
        StockGroup.INVERTS -> buyInverts
        StockGroup.CORALS -> buyCorals
    }

    companion object {
        fun byId(id: String): FishStore? = entries.firstOrNull { it.id == id }
    }
}

/** One thing on a store's shelf today. [count] is how many the store had this morning. */
data class StockItem(val listing: Listing, val price: Int, val count: Int, val deal: Boolean)

/** Why a Buy did not go through (or [OK]). */
enum class BuyResult { OK, STORE_LOCKED, LEVEL_LOCKED, TOO_POOR, SOLD_OUT, NOT_IN_STOCK }

/** The day's shelves, deals and wanted lists: the same all day for a given date, different the next. */
object Stores {
    const val DEAL_OFF = 0.30
    const val WANTED_BONUS = 1.5

    private fun rng(store: FishStore, day: String) = Rng(day.hashCode().toLong() * 7919 + store.ordinal * 104729L + 17)

    /** Today's shelf at [store]: up to [FishStore.stockSize] species it carries, a few of each, one of them on a deal. */
    fun stock(store: FishStore, day: String): List<StockItem> {
        val r = rng(store, day)
        val pool = PriceBook.all.filter { store.carries(it) }.toMutableList()
        // the beginner shop always has clownfish and snails: the first tank never finds the shelf empty of basics
        val keep = if (store == FishStore.SHELLYS) pool.filter { it.speciesId == "amphiprion_ocellaris" || it.speciesId == "nassarius_sp" } else emptyList()
        pool.removeAll(keep)
        for (i in pool.size - 1 downTo 1) { val j = r.nextInt(i + 1); val t = pool[i]; pool[i] = pool[j]; pool[j] = t }
        val picked = (keep + pool.take(max(0, store.stockSize - keep.size))).sortedWith(compareBy({ it.group.ordinal }, { it.price }))
        val dealAt = if (picked.isEmpty()) -1 else r.nextInt(picked.size)
        return picked.mapIndexed { i, l ->
            val base = l.price * store.priceMult
            val count = when {
                l.price <= 8 -> 6 + r.nextInt(5)     // snails and hermits by the handful
                l.level >= 6 -> 1
                else -> 1 + r.nextInt(3)
            }
            StockItem(l, max(1, (if (i == dealAt) base * (1 - DEAL_OFF) else base).roundToInt()), count, i == dealAt)
        }
    }

    /** What [store] wants today (it pays [WANTED_BONUS] for these): one or two species it buys. */
    fun wanted(store: FishStore, day: String): List<String> {
        val r = rng(store, day + "w")
        val pool = PriceBook.all.filter { store.buyBack(it.group) > 0 && it.level >= 2 }.toMutableList()
        // the anemone split and coral frags are what keepers have to sell: wanted more often
        if (store.buyBack(StockGroup.INVERTS) > 0) pool += PriceBook.listing("entacmaea_quadricolor")!!
        val n = if (store == FishStore.BIG_BLUE) 2 else 1
        val out = ArrayList<String>()
        while (out.size < n && pool.isNotEmpty()) out += pool.removeAt(r.nextInt(pool.size)).speciesId
        return out.distinct()
    }

    /** One line when [speciesId] is a poor fit for this tank (the store still sells it: kids learn, not blocked). */
    fun warning(sim: Simulation, speciesId: String): String? {
        val sp = runCatching { sim.species[speciesId] }.getOrNull() ?: return null
        val alive = sim.organisms.filter { it.alive }
        val minL = LifeCycle.minVolumeL(sp.brainKind)
        if (minL > 0 && sim.water.s.volumeL < minL) return "${sp.common.ifBlank { "This fish" }} grows big and needs a tank of about ${(minL / 3.785).roundToInt()} gallons."
        val shrimp = alive.any { it.species.id == "lysmata_amboinensis" || it.species.id == "stenopus_hispidus" }
        if (speciesId == "cirrhitichthys_falco" && shrimp) return "Hawkfish sometimes eat small shrimp."
        if (speciesId in setOf("lysmata_amboinensis", "stenopus_hispidus") && alive.any { it.species.id == "cirrhitichthys_falco" }) return "Your hawkfish might eat a small shrimp."
        if (speciesId == "pseudochromis_indigo" && alive.any { it.species.id == speciesId }) return "Two dottybacks will fight. One is best."
        if (speciesId == "stenopus_hispidus" && alive.any { it.species.id == speciesId }) return "Two coral banded shrimp fight unless they are a pair."
        if (speciesId == "amphiprion_ocellaris" && alive.count { it.species.id == speciesId } >= 2) return "Clownfish live in pairs. A third one gets picked on."
        return null
    }
}
