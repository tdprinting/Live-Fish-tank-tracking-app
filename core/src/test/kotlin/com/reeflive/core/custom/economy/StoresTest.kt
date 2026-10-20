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

    @Test fun displayTanksHoldTheWholeShelfInSections() {
        for (s in FishStore.entries) {
            val tanks = StoreVisit.tanks(s, day)
            assertEquals(Stores.stock(s, day).toSet(), tanks.flatMap { it.items }.toSet())
            assertEquals(tanks.sumOf { it.items.size }, tanks.flatMap { it.items }.size)
            assertTrue(tanks.all { it.items.isNotEmpty() && it.animals in 1..60 })
        }
        assertEquals("The showpiece", StoreVisit.tanks(FishStore.DEEP_REEF, day).first().title)
    }

    @Test fun checkoutPaysForTheWholeBagOrNothing() {
        val e = Economy(SavedEconomy(coins = 1000))
        // only what a new player may buy: Shelly's also shows level-locked stock, and its order moves as species are added
        val shelf = Stores.stock(FishStore.SHELLYS, day).filter { e.unlocked(it.listing) }
        val two = shelf.take(2).map { it.listing.speciesId }
        val total = shelf.take(2).sumOf { it.price }
        val c = e.checkout(FishStore.SHELLYS, two, day)
        assertEquals(BuyResult.OK, c.result)
        assertEquals(1000 - total, e.state.coins)
        val poor = Economy(SavedEconomy(coins = total - 1))
        assertEquals(BuyResult.TOO_POOR, poor.checkout(FishStore.SHELLYS, two, day).result)
        assertEquals(total - 1, poor.state.coins)
        // more than the shelf holds: nothing is bought
        val tooMany = List(shelf[0].count + 1) { shelf[0].listing.speciesId }
        assertEquals(BuyResult.SOLD_OUT, Economy(SavedEconomy(coins = 5000)).checkout(FishStore.SHELLYS, tooMany, day).result)
    }

    @Test fun cheapFishCostLessThanTangs() {
        val p = { id: String -> PriceBook.listing(id)!!.price }
        assertTrue(p("chromis_viridis") * 5 < p("paracanthurus_hepatus"))
        assertTrue(p("amphiprion_ocellaris") < p("zebrasoma_flavescens") && p("zebrasoma_flavescens") < p("zebrasoma_xanthurum"))
    }

    @Test fun aDisplayTankSwimsWithTheShelf() {
        val tank = StoreVisit.tanks(FishStore.SHELLYS, day).first { it.title == "Starter fish" }
        val sim = StoreVisit.build(tank, com.reeflive.core.sim.SpeciesCatalog(), com.reeflive.core.sim.TestTank.rock, 3L)
        // the animals are in as soon as it is built (the host maps taps to cards right away)
        val ids = sim.organisms.filter { it.alive }.map { it.species.id }
        for (it in tank.items) assertEquals(it.listing.speciesId, StoreVisit.shown(it), ids.count { id -> id == it.listing.speciesId })
        assertTrue(sim.organisms.all { o -> StoreVisit.itemFor(tank, o.species.id) != null })
        repeat(20) { sim.stepWall(0.05) }
        assertEquals(ids.size, sim.organisms.count { it.alive })
    }
}
