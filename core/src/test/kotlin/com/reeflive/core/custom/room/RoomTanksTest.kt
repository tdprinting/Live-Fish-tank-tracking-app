package com.reeflive.core.custom.room

import com.reeflive.core.custom.CustomTankCodec
import com.reeflive.core.custom.CustomTankSave
import com.reeflive.core.custom.economy.Economy
import com.reeflive.core.custom.economy.SavedEconomy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** More tanks round the room: each on its own wall, one in view, the money and the room's look shared. */
class RoomTanksTest {
    private val day = "2026-10-21"
    private val room = SavedDecor(scene = "beach")

    private fun first() = CustomTankSave(name = "First", buildId = "rimless_40", economy = SavedEconomy(coins = 5000, stars = 2000), decor = room)

    @Test fun aBoughtTankGoesOnTheNextWallAndTheFirstWaitsSaved() {
        val cur = first()
        val e = Economy(cur.economy)
        assertEquals(DecorResult.OK, RoomTanks.buy(cur, "long_reef_60", e, day))
        assertEquals(5000 - 1400, e.state.coins)
        val two = RoomTanks.withNew(cur.copy(economy = e.state), CustomTankSave(name = "Second", buildId = "long_reef_60"))!!
        assertEquals("Second", two.name)
        assertEquals(1, two.side)
        assertEquals(listOf(0, 1), RoomTanks.sides(two))
        assertEquals(5000 - 1400, two.economy.coins)
        assertEquals("beach", two.decor.scene)
        // the first tank is kept whole, minus the room's money and look
        assertEquals("First", two.others.single().name)
        assertEquals(SavedEconomy(), two.others.single().economy)
        // and the whole room survives the save
        val back = CustomTankCodec.decode(CustomTankCodec.encode(two))!!
        assertEquals("rimless_40", back.others.single().buildId)
    }

    @Test fun turningRoundSwapsTheTankInViewAndKeepsTheMoney() {
        val cur = first()
        var two = RoomTanks.withNew(cur, CustomTankSave(name = "Second", buildId = "nano_cube_20"))!!
        two = two.copy(economy = two.economy.copy(coins = 123))
        assertEquals(0, RoomTanks.next(two, 1))
        val back = RoomTanks.switchTo(two, 0)!!
        assertEquals("First", back.name)
        assertEquals(123, back.economy.coins)
        assertEquals("beach", back.decor.scene)
        assertEquals("Second", back.others.single().name)
        assertNull(RoomTanks.switchTo(back, 0))
        assertNull(RoomTanks.switchTo(back, 3))
    }

    @Test fun fourWallsFourTanksAndStartingOverOneKeepsTheRest() {
        var s = first()
        for (k in 1..3) s = RoomTanks.withNew(s, CustomTankSave(name = "T$k"))!!
        assertEquals(listOf(0, 1, 2, 3), RoomTanks.sides(s))
        assertNull(RoomTanks.freeSide(s))
        assertEquals(DecorResult.ROOM_FULL, RoomTanks.buy(s, "nano_cube_20", Economy(SavedEconomy(coins = 9999, stars = 9999)), day))
        assertTrue(RoomTanks.tiles(s, 9).all { it.locked })
        val dropped = RoomTanks.dropActive(s)!!
        assertEquals(3, RoomTanks.sides(dropped).size)
        assertTrue(RoomTanks.sides(dropped).none { it == s.side })
        assertEquals(s.economy, dropped.economy)
        assertNull(RoomTanks.dropActive(first()))
    }

    @Test fun bigTanksWaitForLevelsAndMoney() {
        assertEquals(DecorResult.LEVEL_LOCKED, RoomTanks.buy(first(), "display_120", Economy(SavedEconomy(coins = 9999)), day))
        val poor = Economy(SavedEconomy(coins = 10, stars = 2000))
        assertEquals(DecorResult.TOO_POOR, RoomTanks.buy(first(), "nano_cube_20", poor, day))
        assertEquals(10, poor.state.coins)
    }
}
