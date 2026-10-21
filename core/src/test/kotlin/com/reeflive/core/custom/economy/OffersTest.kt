package com.reeflive.core.custom.economy

import com.reeflive.core.custom.CustomTank
import com.reeflive.core.custom.TankBuilds
import com.reeflive.core.sim.Command
import com.reeflive.core.sim.SpeciesCatalog
import com.reeflive.core.sim.StockSize
import com.reeflive.core.sim.TestTank
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Selling to the fish stores (docs/ECONOMY.md §6). */
class OffersTest {
    private val day = "2026-10-20"
    private val clown = "amphiprion_ocellaris"

    private fun tank(size: StockSize, n: Int = 1) = CustomTank.fresh(
        TankBuilds.LONG_REEF, SpeciesCatalog.fromClasspath(listOf(clown, "zoanthus_sp")), TestTank.rock, 12 * 3600.0, 9L,
    ).also {
        it.enqueue(Command.AddMany(clown, n, size = size))
        it.enqueue(Command.AddMany("zoanthus_sp", 1))
        it.stepWall(0.0)
    }

    @Test fun grownFishAreWorthMoreAndNeverMoreThanTheShopPrice() {
        val small = Offers.offers(FishStore.SHELLYS, tank(StockSize.JUVENILE), day).first { it.speciesId == clown }
        val big = Offers.offers(FishStore.SHELLYS, tank(StockSize.ADULT), day).first { it.speciesId == clown }
        assertTrue(small.amount in 1 until PriceBook.listing(clown)!!.price)
        assertTrue(big.amount > small.amount)
        // buy-back stays under the shop's own price even grown: buy-and-sell never makes money
        assertTrue(small.amount < Stores.stock(FishStore.SHELLYS, day).first { it.listing.speciesId == clown }.price)
    }

    @Test fun babiesAreTooSmallUntilShopSize() {
        val sim = tank(StockSize.JUVENILE)
        val o = sim.organisms.first { it.species.id == clown }
        o.lengthCm = 1.0
        val baby = Offers.offers(FishStore.SHELLYS, sim, day).first { it.id == o.id }
        assertEquals(0, baby.amount)
        assertNotNull(baby.tooSmallCm)
    }

    @Test fun storesBuyOnlyTheirKindsAndSellingPaysWithoutStars() {
        val sim = tank(StockSize.YOUNG)
        val frag = Offers.offers(FishStore.FRAG_SHACK, sim, day)
        assertTrue(frag.first { it.speciesId == clown }.amount == 0)
        assertTrue(frag.first { it.coral }.amount > 0)
        val e = Economy(SavedEconomy(coins = 0, stars = 0))
        val offer = Offers.offers(FishStore.SHELLYS, sim, day).first { it.speciesId == clown }
        assertTrue(e.sell(FishStore.SHELLYS, offer, day))
        assertTrue(e.state.coins >= offer.amount)
        assertTrue(Goal.FIRST_SALE.id in e.state.goals)
        assertFalse(e.sell(FishStore.BIG_BLUE, offer, day))   // not open at level 1
    }
}
