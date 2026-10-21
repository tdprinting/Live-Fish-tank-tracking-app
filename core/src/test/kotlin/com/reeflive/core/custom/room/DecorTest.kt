package com.reeflive.core.custom.room

import com.reeflive.core.custom.CustomTankCodec
import com.reeflive.core.custom.CustomTankSave
import com.reeflive.core.custom.economy.Economy
import com.reeflive.core.custom.economy.SavedEconomy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The room shop: stands, their colours and extras are bought with sand dollars, kept in the save and put to use. */
class DecorTest {
    private val day = "2026-10-21"
    /** A keeper at level 3 (stars past 30·3·2) with [coins]. */
    private fun eco(coins: Int) = Economy(SavedEconomy(coins = coins, stars = 200))

    @Test fun everyTankStartsOnTheClassicWhiteStand() {
        val (s, c) = Decor.look(SavedDecor())
        assertEquals("classic", s.id)
        assertEquals("White", c.label)
        assertTrue(Decor.ownsStand(SavedDecor(), "classic"))
        assertFalse(Decor.ownsStand(SavedDecor(), "shaker"))
    }

    @Test fun buyingAStandPaysAndPutsItToUse() {
        val e = eco(500)
        val (d, r) = Decor.buy(SavedDecor(), "stand:shaker", e, day)
        assertEquals(DecorResult.OK, r)
        assertEquals(500 - 240, e.state.coins)
        assertEquals("shaker", d.stand)
        assertEquals(0, d.standColor)
        assertEquals(-240, e.state.ledger.first().amount)
        // bought once: a second buy is refused and costs nothing
        assertEquals(DecorResult.OWNED, Decor.buy(d, "stand:shaker", e, day).second)
        assertEquals(260, e.state.coins)
    }

    @Test fun tooPoorOrTooLowALevelBuysNothing() {
        val poor = eco(100)
        assertEquals(DecorResult.TOO_POOR, Decor.buy(SavedDecor(), "stand:shaker", poor, day).second)
        assertEquals(100, poor.state.coins)
        assertEquals(DecorResult.LEVEL_LOCKED, Decor.buy(SavedDecor(), "stand:reef_pro", eco(5000), day).second)
        assertEquals(DecorResult.UNKNOWN, Decor.buy(SavedDecor(), "stand:nope", eco(5000), day).second)
    }

    @Test fun coloursAreBoughtPerStandAndSwitchingBackIsFree() {
        val e = eco(1000)
        var d = Decor.buy(SavedDecor(), "color:classic:2", e, day).first
        assertEquals(1000 - Decor.RECOLOR, e.state.coins)
        assertEquals("Navy", Decor.look(d).second.label)
        d = Decor.buy(d, "stand:iron", e, day).first
        assertEquals("iron", d.stand)
        // the navy classic is still owned: going back costs nothing
        val coins = e.state.coins
        d = Decor.use(d, "color:classic:2")
        assertEquals("classic", d.stand)
        assertEquals(2, d.standColor)
        assertEquals(coins, e.state.coins)
        // a colour not owned does nothing
        assertEquals(d, Decor.use(d, "color:iron:3"))
    }

    @Test fun extrasToggleOnceOwned() {
        val e = eco(1000)
        var d = Decor.buy(SavedDecor(), "extra:glow", e, day).first
        assertEquals(listOf("glow"), d.extras)
        d = Decor.use(d, "extra:glow")
        assertTrue(d.extras.isEmpty())
        d = Decor.use(d, "extra:glow")
        assertEquals(listOf("glow"), d.extras)
    }

    @Test fun decorSurvivesTheSaveAndOldSavesOpenOnTheDefault() {
        val d = SavedDecor(owned = listOf("stand:modern", "extra:brass"), stand = "modern", standColor = 0, extras = listOf("brass"))
        val back = CustomTankCodec.decode(CustomTankCodec.encode(CustomTankSave(decor = d)))!!
        assertEquals(d, back.decor)
        val old = CustomTankCodec.decode("""{"version":1,"name":"Old tank"}""")!!
        assertEquals(SavedDecor(), old.decor)
        // a stand from a later version reads as the classic
        assertEquals("classic", Decor.look(SavedDecor(stand = "future_stand")).first.id)
    }

    @Test fun everyStandHasFourColoursAndAPrice() {
        for (s in Decor.STANDS) {
            assertEquals(s.id, 4, s.colors.size)
            assertEquals(s.price, Decor.price("stand:${s.id}"))
        }
        assertEquals(0, Decor.STANDS.count { it.price == 0 } - 1)
    }
}
