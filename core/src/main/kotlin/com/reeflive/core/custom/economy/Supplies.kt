package com.reeflive.core.custom.economy

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * One supply the fish stores sell for the shelf (docs/ECONOMY.md §5 "Supplies"). [id] is the shelf prop's id
 * (Android SupplyItem.id, web live.js SUPPLIES). [usd] is the real 2025-26 US price of the pack, which holds [uses]
 * (feedings, doses, water changes); [uses] 0 is a tool that is bought once and kept.
 */
data class SupplyPack(val id: String, val name: String, val pack: String, val usd: Int, val uses: Int, val level: Int = 1) {
    val keeps: Boolean get() = uses == 0

    /** Sand dollars before a store's price level. */
    val price: Int get() = usd * PriceBook.SCALE
}

/**
 * What the shelf holds in My tank and what refills cost (Tee, 2026-10-21: "you have to earn points and purchase them
 * at the fish shops"). Watch mode (the real tank) never counts. The counts live in [SavedEconomy.supplies].
 */
object SupplyBook {
    /** Shop bag entries for a pack of a supply are "supply:<id>"; everything else in the bag is an animal. */
    const val BAG = "supply:"
    /** [Economy.supplyLeft] for a supply that is never used up (free, or not sold). */
    const val UNLIMITED = -1

    val PACKS: List<SupplyPack> = listOf(
        // foods: a tub, a bag of frozen cubes or a bottle, priced like the product printed on the prop
        SupplyPack("flake", "Flakes", "30 feeds", 6, 30),
        SupplyPack("pellet", "Pellets", "30 feeds", 7, 30),
        SupplyPack("floating_pellet", "Floating pellets", "30 feeds", 8, 30),
        SupplyPack("algae_wafer", "Algae wafers", "20 wafers", 6, 20),
        SupplyPack("mysis", "Mysis shrimp", "15 frozen cubes", 9, 15),
        SupplyPack("brine", "Brine shrimp", "15 frozen cubes", 7, 15),
        SupplyPack("krill", "Krill", "15 feeds", 12, 15),
        SupplyPack("prawn", "Prawn", "8 pieces", 8, 8),
        SupplyPack("cyclops", "Cyclops", "15 frozen cubes", 9, 15),
        SupplyPack("nori", "Nori", "10 sheets", 5, 10),
        SupplyPack("coral_food", "Coral food", "20 feeds", 18, 20),
        SupplyPack("plankton", "Phytoplankton", "15 doses", 14, 15),
        SupplyPack("pods", "Live pods", "1 bottle", 35, 1),
        // care: salt by the bag (enough for so many changes of a 60 gallon tank), dosing bottles, socks, carbon
        SupplyPack("water_change", "Coral Pro salt", "4 big water changes", 28, 4),
        SupplyPack("water_small", "Instant Ocean salt", "10 small water changes", 20, 10),
        SupplyPack("dose_alk", "Alkalinity", "20 doses", 25, 20),
        SupplyPack("dose_ca", "Calcium", "20 doses", 25, 20),
        SupplyPack("dose_mg", "Magnesium", "20 doses", 25, 20),
        SupplyPack("new_sock", "Filter socks", "3 socks", 15, 3),
        SupplyPack("fresh_carbon", "Carbon", "4 refills", 15, 4),
        SupplyPack("trim_algae", "Algae scissors", "keep forever", 15, 0),
        SupplyPack("zap_pest", "Aiptasia-X", "10 shots", 16, 10),
        SupplyPack("settle_in", "Drip kit", "keep forever", 12, 0),
        // tools
        SupplyPack("glass_cleaner", "Glass cleaner", "keep forever", 30, 0),
        SupplyPack("test_kit", "Test kit", "20 tests", 45, 20),
        SupplyPack("microscope", "Microscope", "keep forever", 40, 0),
        SupplyPack("joes_juice", "Joe's Juice", "10 shots", 15, 10),
        SupplyPack("kalk_paste", "Kalk paste", "10 shots", 5, 10),
        SupplyPack("pest_laser", "Pest laser", "keep forever", 120, 0, level = 3),
    )
    private val byId = PACKS.associateBy { it.id }

    fun pack(id: String): SupplyPack? = byId[id]

    /** A new My tank's shelf: some basic fish food and nothing else. */
    val STARTER: Map<String, Int> = mapOf("flake" to 30)

    /**
     * A My tank saved before the shelf counted: one pack of everything and every tool, so nobody loses what they
     * were using.
     */
    val LEGACY_KIT: Map<String, Int> get() = PACKS.associate { it.id to (if (it.keeps) 1 else it.uses) }

    /** Fish foods, in the order a "feed every hungry fish" tap takes one serving from. */
    val FISH_FOODS = listOf("pellet", "flake", "floating_pellet", "mysis", "brine", "krill", "cyclops", "algae_wafer", "nori", "prawn")

    /** A pack's price at [store] (its price level, like the animals). */
    fun price(store: FishStore, p: SupplyPack): Int = max(1, (p.price * store.priceMult).roundToInt())

    /** The supplies behind [store]'s counter for this keeper (every store sells them all, at its own price level). */
    fun cards(e: Economy, store: FishStore): List<SupplyCard> =
        PACKS.map { SupplyCard(it, price(store, it), e.supplyLeft(it.id), e.level < it.level) }

    fun bagId(id: String) = BAG + id
    fun fromBag(entry: String): String? = if (entry.startsWith(BAG)) entry.substring(BAG.length) else null
}

/** One supply on the shelf behind a store's counter, as the keeper sees it. [have] is what is on their shelf now. */
data class SupplyCard(val pack: SupplyPack, val price: Int, val have: Int, val locked: Boolean) {
    /** A kept tool the keeper already owns: nothing more to buy. */
    val owned: Boolean get() = pack.keeps && have > 0
}
