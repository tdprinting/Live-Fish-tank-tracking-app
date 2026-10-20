package com.reeflive.core.custom.economy

import com.reeflive.core.sim.StockGroup
import com.reeflive.core.sim.Stocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The fish stores (docs/ECONOMY.md §5): prices, daily shelves, locks and buying. */
class StoresTest {
    private val day = "2026-10-20"

    @Test fun everyGalleryAnimalHasAPriceExceptHitchhikers() {
        for (e in Stocking.CATALOG) {
            if (e.speciesId in PriceBook.NEVER_SOLD) continue
            val l = PriceBook.listing(e.speciesId)
            assertTrue("${e.speciesId} priced", l != null && l.price > 0 && l.level in 1..Levels.MAX)
        }
    }

    @Test fun shelvesAreStableForADayAndChangeTheNext() {
        for (s in FishStore.entries) {
            val a = Stores.stock(s, day)
            assertEquals(a, Stores.stock(s, day))
            assertTrue("${s.id} has stock", a.isNotEmpty() && a.size <= s.stockSize)
            assertTrue("${s.id} carries only its own", a.all { s.carries(it.listing) })
            assertEquals(1, a.count { it.deal })
        }
        assertNotEquals(Stores.stock(FishStore.BIG_BLUE, day), Stores.stock(FishStore.BIG_BLUE, "2026-10-21"))
        assertTrue(Stores.stock(FishStore.FRAG_SHACK, day).all { it.listing.group == StockGroup.CORALS })
        assertTrue(Stores.stock(FishStore.SHELLYS, day).any { it.listing.speciesId == "amphiprion_ocellaris" })
    }

    @Test fun buyingSpendsAndRespectsLocksStockAndMoney() {
        val e = Economy()
        val clown = Stores.stock(FishStore.SHELLYS, day).first { it.listing.speciesId == "amphiprion_ocellaris" }
        assertEquals(BuyResult.STORE_LOCKED, e.buy(FishStore.BIG_BLUE, "amphiprion_ocellaris", day))
        repeat(clown.count) { assertEquals(BuyResult.OK, e.buy(FishStore.SHELLYS, "amphiprion_ocellaris", day)) }
        assertEquals(Economy.START_COINS - clown.price * clown.count, e.state.coins)
        assertEquals(BuyResult.SOLD_OUT, e.buy(FishStore.SHELLYS, "amphiprion_ocellaris", day))
        // the next day the shelf is restocked
        val tomorrow = Stores.stock(FishStore.SHELLYS, "2026-10-21").first { it.listing.speciesId == "amphiprion_ocellaris" }
        assertEquals(tomorrow.count, e.left(FishStore.SHELLYS, tomorrow, "2026-10-21"))
        val poor = Economy(SavedEconomy(coins = 1))
        assertEquals(BuyResult.TOO_POOR, poor.buy(FishStore.SHELLYS, "amphiprion_ocellaris", day))
        assertEquals(0, e.state.stars)
    }
}
