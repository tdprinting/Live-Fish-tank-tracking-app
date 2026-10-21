package com.reeflive.app.ui.live

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveTipsTest {
    private val all = LiveTips.watch + LiveTips.sandbox + LiveTips.hermieWatch + LiveTips.hermieSandbox + LiveTips.HELLO

    @Test fun everyListHasTips() {
        listOf(LiveTips.watch, LiveTips.sandbox, LiveTips.hermieWatch, LiveTips.hermieSandbox).forEach { assertTrue(it.isNotEmpty()) }
    }

    @Test fun tipsNameOnlyControlsThatExist() {
        // the mode toggle says My tank / Watch (HudMode: the pretend Sandbox tank is gone), there is no Feed or Clean
        // button any more (the supply shelf and the Flipper on the glass replaced them), the old More and Trouble
        // bubbles became Settings and Tank → More, and tapping the water no longer hides the buttons
        assertTrue(LiveTips.watch.any { "My tank" in it })
        assertTrue(LiveTips.sandbox.any { "Watch" in it })
        (all + LiveTips.myTank + LiveTips.hermieMyTank).forEach { t ->
            assertFalse(t, "Sandbox" in t)
            assertFalse(t, "in More" in t)
            assertFalse(t, "Trouble" in t)
            assertFalse(t, "Pretend at the top" in t)
            assertFalse(t, "Tap Feed" in t)
            assertFalse(t, t.startsWith("Clean lets you"))
            assertFalse(t, "empty water to hide" in t)
        }
        assertTrue(HudMode.entries.map { it.label } == listOf("My tank", "Watch"))
    }

    @Test fun hermieLinesFitHisBubble() {
        // three short lines in a ~220 dp bubble at 13 sp
        (LiveTips.hermieWatch + LiveTips.hermieSandbox + LiveTips.HELLO).forEach { assertTrue(it, it.length <= 70) }
    }

    @Test fun tapTheGlassIsTaught() {
        // a tap on the tank touches it (UI_STYLE §10.8): the card and Hermie both say so
        assertTrue(LiveTips.watch.any { "Tap the glass and see who comes to look" in it })
        assertTrue(LiveTips.hermieWatch.any { "Tap the glass and see who comes to look" in it })
    }

    @Test fun holdToFollowIsTaught() {
        // a press-and-hold right on an animal follows it (TankRenderer LONG_PRESS → onHoldAnimal)
        assertTrue(LiveTips.watch.any { "Press and hold a fish" in it })
        assertTrue(LiveTips.hermieWatch.any { "Press and hold a fish" in it })
    }

    @Test fun thoughtBubblesAreTaught() {
        // the fish card's "Thinking…" lines and Settings → "Thought bubbles" (UI_STYLE §10.9)
        assertTrue(LiveTips.watch.any { "thinking" in it && "Thought bubbles" in it })
        assertTrue(LiveTips.hermieWatch.any { "Thought bubbles" in it })
        assertTrue(LiveTips.hermieSandbox.any { "Thought bubbles" in it })
    }

    @Test fun theHelpButtonHermieMentionsIsInTheMenu() {
        assertTrue(LiveTips.hermieWatch.any { "What can I do?" in it })
    }
}
