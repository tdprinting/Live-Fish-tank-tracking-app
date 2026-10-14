package com.reeflive.app.ui.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveTapsTest {
    private fun tap(
        justSkipped: Boolean = false,
        hidden: Boolean = false,
        following: Boolean = false,
        cardOpen: Boolean = false,
        fishPicked: Boolean = false,
    ) = LiveTaps.emptyWater(justSkipped, hidden, following, cardOpen, fishPicked)

    @Test fun aTapOnEmptyWaterNeverHidesTheButtons() {
        // there is no "hide" outcome at all, a plain tap touches the tank, and with the buttons already hidden every
        // tap that was not a skip brings them back
        assertFalse(WaterTap.entries.any { it.name.contains("HIDE") })
        assertEquals(WaterTap.TOUCH, tap())
        for (bits in 0 until 32) {
            val skipped = bits and 1 != 0
            val hidden = bits and 2 != 0
            val r = tap(skipped, hidden, bits and 4 != 0, bits and 8 != 0, bits and 16 != 0)
            if (hidden && !skipped) assertEquals(WaterTap.SHOW_CONTROLS, r)
            if (!hidden) assertTrue(r != WaterTap.SHOW_CONTROLS)
        }
    }

    @Test fun hiddenButtonsComeBackOnAnyWaterTap() {
        assertEquals(WaterTap.SHOW_CONTROLS, tap(hidden = true))
        assertEquals(WaterTap.SHOW_CONTROLS, tap(hidden = true, following = true, cardOpen = true, fishPicked = true))
    }

    @Test fun aTapThatSkippedAPictureDoesNothingElse() {
        assertEquals(WaterTap.NOTHING, tap(justSkipped = true, hidden = true))
        assertEquals(WaterTap.NOTHING, tap(justSkipped = true, cardOpen = true))
    }

    @Test fun followingIsNotDroppedByAMissedTap() {
        assertEquals(WaterTap.NOTHING, tap(following = true, fishPicked = true))
    }

    @Test fun cardsCloseBeforeTheFishIsPutDown() {
        assertEquals(WaterTap.CLOSE_CARD, tap(cardOpen = true, fishPicked = true))
        assertEquals(WaterTap.DESELECT, tap(fishPicked = true))
    }

    @Test fun aTapOnlyTouchesTheTankWhenNothingIsOpen() {
        // card open or buttons hidden: the tap only undoes that (PR #53); otherwise it is a fingertip on the glass
        assertEquals(WaterTap.TOUCH, tap())
        for (bits in 1 until 32) {
            val r = tap(bits and 1 != 0, bits and 2 != 0, bits and 4 != 0, bits and 8 != 0, bits and 16 != 0)
            assertTrue("bits $bits gave $r", r != WaterTap.TOUCH)
        }
    }

    @Test fun clampSafeNeverThrows() {
        assertEquals(5f, 3f.clampSafe(5f, 10f))
        assertEquals(10f, 30f.clampSafe(5f, 10f))
        assertEquals(7f, 7f.clampSafe(5f, 10f))
        // the room has gone negative (tiny window): the middle, not an IllegalArgumentException
        assertEquals(50f, 0f.clampSafe(60f, 40f))
    }
}
