package com.reeflive.core.custom.economy

import com.reeflive.core.sim.Simulation
import com.reeflive.core.sim.StockGroup
import com.reeflive.core.sim.StockSize
import com.reeflive.core.sim.Stocking
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * What a store offers for one of the keeper's animals or corals (docs/ECONOMY.md §6). [amount] is 0 when the store
 * does not buy that kind of animal or the animal is too small yet ([tooSmallCm] then says the size it must reach).
 */
data class SellOffer(
    val id: String,
    val speciesId: String,
    val name: String,
    /** The keeper gave it a name: the screens ask "Say goodbye to Nemo?" first. */
    val pet: Boolean,
    val group: StockGroup,
    val coral: Boolean,
    val amount: Int,
    val wanted: Boolean,
    val lengthCm: Double,
    val tooSmallCm: Double?,
)

object Offers {
    /** Most a grown-up animal is worth against the shop-size one it was bought as. */
    const val MAX_SIZE_BONUS = 1.8

    /** Every living animal and coral in [sim] with [store]'s offer for it today; wanted ones first, then the best. */
    fun offers(store: FishStore, sim: Simulation, day: String): List<SellOffer> {
        val wanted = Stores.wanted(store, day).toSet()
        val animals = sim.organisms.filter { it.alive }.mapNotNull { o ->
            val l = PriceBook.listing(o.species.id) ?: return@mapNotNull null
            val entry = Stocking.entry(o.species.id)
            // stores sell juveniles: smaller than that is a baby still growing
            val shopCm = StockSize.JUVENILE.lengthCm(entry?.typicalCm ?: o.lengthCm)
            val small = o.lengthCm < shopCm * 0.95
            val bonus = (o.lengthCm / shopCm).coerceIn(1.0, MAX_SIZE_BONUS)
            val w = o.species.id in wanted
            val amount = if (small) 0 else price(l, store, o.health, bonus, w)
            SellOffer(
                o.id, o.species.id, o.nickname.ifBlank { entry?.common ?: o.species.common }, isPet(o.nickname, o.species.common), l.group, false,
                amount, w, o.lengthCm, if (small) shopCm else null,
            )
        }
        val corals = sim.corals.filter { it.tissue > 0.05 }.mapNotNull { c ->
            val l = PriceBook.listing(c.species.id) ?: return@mapNotNull null
            val w = c.species.id in wanted
            val amount = price(l, store, c.tissue, c.size.coerceIn(0.5, 2.0), w)
            SellOffer(c.id, c.species.id, c.nickname.ifBlank { Stocking.entry(c.species.id)?.common ?: c.species.common }, isPet(c.nickname, c.species.common), l.group, true, amount, w, 0.0, null)
        }
        return (animals + corals).sortedWith(compareBy({ !it.wanted }, { -it.amount }))
    }

    /** base × buy-back × health × size × wanted; 0 when the store does not buy the group. */
    fun price(l: Listing, store: FishStore, health: Double, size: Double, wanted: Boolean): Int {
        val rate = store.buyBack(l.group)
        if (rate <= 0) return 0
        val v = l.price * rate * health.coerceIn(0.0, 1.0) * size * (if (wanted) Stores.WANTED_BONUS else 1.0)
        return max(1, v.roundToInt())
    }

    /** Named by the keeper, not the sim's default ("Ocellaris clownfish #2"). */
    private fun isPet(nick: String, common: String) = nick.isNotBlank() && nick.substringBefore(" #") != common
}
