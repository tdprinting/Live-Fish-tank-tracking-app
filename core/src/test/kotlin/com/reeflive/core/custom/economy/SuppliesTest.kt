package com.reeflive.core.custom.economy

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

class SuppliesTest {
    private val day = "2026-10-21"

    @Test fun newTankStartsWithFlakesOnly() {
        val e = Economy()
        assertEquals(30, e.supplyLeft("flake"))
        assertEquals(0, e.supplyLeft("mysis"))
        assertFalse(e.owns("glass_cleaner"))
        // free tools never run out
        assertEquals(SupplyBook.UNLIMITED, e.supplyLeft("knocker"))
        assertTrue(e.useSupply("knocker"))
    }

    @Test fun oldSavesGetAFullKit() {
        val e = Economy(SavedEconomy(coins = 100, day = day))
        assertEquals(15, e.supplyLeft("mysis"))
        assertEquals(1, e.supplyLeft("glass_cleaner"))
        assertTrue(e.state.suppliesSet)
    }

    @Test fun usingCountsDownAndStopsAtZero() {
        val e = Economy(SavedEconomy(supplies = mapOf("nori" to 2, "microscope" to 1), suppliesSet = true))
        assertTrue(e.useSupply("nori")); assertTrue(e.useSupply("nori"))
        assertFalse(e.useSupply("nori"))
        assertEquals(0, e.supplyLeft("nori"))
        // a kept tool is not used up
        repeat(5) { assertTrue(e.useSupply("microscope")) }
        assertEquals(1, e.supplyLeft("microscope"))
    }

    @Test fun feedHungryTakesAnyFishFood() {
        val e = Economy(SavedEconomy(supplies = mapOf("flake" to 1), suppliesSet = true))
        assertEquals("flake", e.useFishFood())
        assertNull(e.useFishFood())
    }

    @Test fun buyingPacksAtTheCounter() {
        val e = Economy(SavedEconomy(coins = 500, supplies = SupplyBook.STARTER, suppliesSet = true))
        val mysis = SupplyBook.pack("mysis")!!
        val salt = SupplyBook.pack("water_change")!!
        val bag = listOf(SupplyBook.bagId("mysis"), SupplyBook.bagId("mysis"), SupplyBook.bagId("water_change"), SupplyBook.bagId("glass_cleaner"))
        val c = e.checkout(FishStore.SHELLYS, bag, day)
        assertEquals(BuyResult.OK, c.result)
        assertTrue(c.bought.isEmpty())
        assertEquals(30, e.supplyLeft("mysis"))
        assertEquals(4, e.supplyLeft("water_change"))
        assertEquals(1, e.supplyLeft("glass_cleaner"))
        val cost = (mysis.usd * 2 + salt.usd + 30) * PriceBook.SCALE
        assertEquals(cost, c.total)
        assertEquals(500 - cost, e.state.coins)
        // a tool is bought once
        assertEquals(BuyResult.SOLD_OUT, e.checkout(FishStore.SHELLYS, listOf(SupplyBook.bagId("glass_cleaner")), day).result)
    }

    @Test fun suppliesAreHardEarned() {
        val e = Economy(SavedEconomy(coins = 5, supplies = emptyMap(), suppliesSet = true))
        assertEquals(BuyResult.TOO_POOR, e.checkout(FishStore.SHELLYS, listOf(SupplyBook.bagId("coral_food")), day).result)
        assertEquals(BuyResult.LEVEL_LOCKED, Economy(SavedEconomy(coins = 9999, suppliesSet = true)).checkout(FishStore.SHELLYS, listOf(SupplyBook.bagId("pest_laser")), day).result)
        // every store sells every supply at its own price level
        val p = SupplyBook.pack("coral_food")!!
        assertTrue(SupplyBook.price(FishStore.DEEP_REEF, p) > SupplyBook.price(FishStore.FRAG_SHACK, p))
    }
}
