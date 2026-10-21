package com.reeflive.core.custom.economy

import com.reeflive.core.geom.Rng
import com.reeflive.core.sim.Command
import com.reeflive.core.sim.Simulation
import com.reeflive.core.sim.StockGroup
import com.reeflive.core.sim.Stocking
import kotlin.math.max
import kotlin.math.sqrt

/**
 * A swap a store's regular ([Role.TRADER]) offers the keeper (docs/research/fish_stores.md: hobbyists trade frags and
 * spare cleanup crew with each other at the shop). The keeper gives [giveName] (a frag cut from one of their corals,
 * or one spare snail, hermit or the like) and gets one [getSpecies] in a bag.
 */
data class TradeOffer(
    val store: FishStore,
    val trader: String,
    /** The keeper's coral or animal id. */
    val giveId: String,
    val giveName: String,
    /** A frag is cut from the coral (it stays, a little smaller); otherwise the animal leaves. */
    val giveFrag: Boolean,
    val getSpecies: String,
    val getName: String,
    /** What the trader says when they walk up. */
    val line: String,
)

object StoreTrades {
    /** Share of a colony's size a frag takes. */
    const val FRAG_CUT = 0.25
    /** Smallest colony (size) the trader asks a frag of: a coral that has grown a bit since it came home. */
    const val MIN_FRAG_SIZE = 0.9
    /** A spare animal is one of at least this many of its kind. */
    const val SPARE_COUNT = 3

    /** What each store's regular takes and what they would rather give. */
    private class Taste(val takes: Set<StockGroup>, val gives: Set<StockGroup>, val favourites: List<String>)

    private val TASTES = mapOf(
        // Joe breeds clownfish and keeps a cleanup crew; he takes coral frags and spare snails
        FishStore.SHELLYS to Taste(setOf(StockGroup.CORALS, StockGroup.INVERTS), setOf(StockGroup.FISH, StockGroup.INVERTS), listOf("amphiprion_ocellaris", "lysmata_amboinensis")),
        // Mia swaps coral for coral
        FishStore.FRAG_SHACK to Taste(setOf(StockGroup.CORALS), setOf(StockGroup.CORALS), listOf("zoanthus_sp", "rhodactis_sp", "duncanopsammia_axifuga")),
        // Dave's club swaps everything
        FishStore.BIG_BLUE to Taste(setOf(StockGroup.CORALS, StockGroup.INVERTS), setOf(StockGroup.FISH, StockGroup.INVERTS, StockGroup.CORALS), emptyList()),
        // Ms. Okafor trades rare corals for frags of nice ones
        FishStore.DEEP_REEF to Taste(setOf(StockGroup.CORALS), setOf(StockGroup.CORALS, StockGroup.INVERTS), listOf("amplexidiscus_fenestrafer", "tubipora_musica", "euphyllia_glabrescens")),
    )

    private class Give(val id: String, val speciesId: String, val name: String, val frag: Boolean, val value: Double, val group: StockGroup)

    /**
     * Today's trade at [store] for the keeper's tank [sim], or null: one a day per store ([Economy.tradedToday]), never
     * a named pet, the last of a kind or a colony too small to cut. What the keeper gets is unlocked at their level,
     * new to their tank and worth about as much as what they give, or a little more (the regular is being friendly).
     */
    fun offer(store: FishStore, sim: Simulation, economy: Economy, day: String, seed: Long = 0L): TradeOffer? {
        if (economy.tradedToday(store, day) || !economy.canSee(store)) return null
        val trader = StoreLife.cast(store).trader ?: return null
        val taste = TASTES.getValue(store)
        val r = Rng(day.hashCode().toLong() * 31 + store.ordinal * 7L + seed)
        val gives = candidates(sim).filter { it.group in taste.takes }
        if (gives.isEmpty()) return null
        val give = gives[r.nextInt(gives.size)]
        val have = sim.organisms.filter { it.alive }.map { it.species.id }.toSet() + sim.corals.map { it.species.id }
        val pool = PriceBook.all.filter {
            it.group in taste.gives && economy.unlocked(it) && it.species != give.speciesId && it.species !in have &&
                Stocking.entry(it.speciesId) != null && it.price.toDouble() in give.value * 0.8..max(give.value * 2.5, 12.0)
        }
        if (pool.isEmpty()) return null
        val fav = pool.filter { it.species in taste.favourites }
        val pick = (fav.ifEmpty { pool.sortedByDescending { it.price }.take(3) }).let { it[r.nextInt(it.size)] }
        val getName = Stocking.entry(pick.speciesId)?.common ?: pick.speciesId
        return TradeOffer(store, trader.name, give.id, give.name, give.frag, pick.speciesId, getName, line(store, give, getName, r))
    }

    /** Frags of healthy grown corals and spares from a big cleanup crew, never a named animal. */
    private fun candidates(sim: Simulation): List<Give> {
        val out = ArrayList<Give>()
        for (c in sim.corals) {
            if (c.tissue < 0.6 || c.size < MIN_FRAG_SIZE || isNamed(c.nickname, c.species.common)) continue
            val l = PriceBook.listing(c.species.id) ?: continue
            out += Give(c.id, c.species.id, Stocking.entry(c.species.id)?.common ?: c.species.common, true, l.price * 0.6 * c.tissue, StockGroup.CORALS)
        }
        val alive = sim.organisms.filter { it.alive }
        for ((sp, list) in alive.groupBy { it.species.id }) {
            if (list.size < SPARE_COUNT) continue
            val l = PriceBook.listing(sp) ?: continue
            val o = list.filter { !isNamed(it.nickname, it.species.common) && it.health > 0.6 }.maxByOrNull { it.health } ?: continue
            out += Give(o.id, sp, Stocking.entry(sp)?.common ?: o.species.common, false, l.price * o.health, l.group)
        }
        return out
    }

    private fun line(store: FishStore, give: Give, getName: String, r: Rng): String {
        val g = if (give.frag) "a frag of your ${give.name}" else "one of your ${give.name}s"
        val lines = when (store) {
            FishStore.SHELLYS -> listOf("Say, young reefer! I'd trade you a $getName for $g. What do you think?", "My $getName could use a new home. Trade you for $g?")
            FishStore.FRAG_SHACK -> listOf("Hey! Would you trade me $g? I'll give you a $getName!", "Ooh, you keep ${give.name}? Swap me $g for my $getName?")
            FishStore.BIG_BLUE -> listOf("Club swap special! My $getName for $g. Deal?", "I've got a spare $getName. Trade you for $g?")
            FishStore.DEEP_REEF -> listOf("Darling, might I offer my $getName for $g?", "A proposal: my $getName, for $g.")
        }
        return lines[r.nextInt(lines.size)]
    }

    /**
     * Does the swap in the keeper's tank: the frag is cut (the colony shrinks by [FRAG_CUT]) or the animal leaves, and
     * the new animal arrives in a bag at the front glass. The caller records it with [Economy.traded].
     */
    fun apply(sim: Simulation, offer: TradeOffer) {
        if (offer.giveFrag) {
            val c = sim.corals.firstOrNull { it.id == offer.giveId }
            if (c != null) {
                val k = 1.0 - FRAG_CUT
                c.size *= k
                c.radiusCm *= sqrt(k)
                c.cover *= k
            }
        } else {
            sim.enqueue(Command.RemoveOrganism(offer.giveId))
        }
        sim.enqueue(Command.FloatBag(offer.getSpecies, 1, sim.rock.lengthCm * 0.5))
    }

    private fun isNamed(nick: String, common: String) = nick.isNotBlank() && nick.substringBefore(" #") != common
}
