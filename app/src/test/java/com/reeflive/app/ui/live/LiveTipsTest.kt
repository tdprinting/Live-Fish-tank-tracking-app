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
        // the mode toggle says Watch / Sandbox (HudMode), there is no Feed or Clean button any more (the supply shelf
        // and the Flipper on the glass replaced them), and tapping the water no longer hides the buttons
        assertTrue(LiveTips.watch.any { "Sandbox" in it })
        assertTrue(LiveTips.sandbox.any { "Watch" in it })
        all.forEach { t ->
            assertFalse(t, "Pretend at the top" in t)
            assertFalse(t, "Tap Feed" in t)
            assertFalse(t, t.startsWith("Clean lets you"))
            assertFalse(t, "empty water to hide" in t)
        }
        assertTrue(HudMode.entries.map { it.label }.containsAll(listOf("Watch", "Sandbox")))
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

    @Test fun theHelpButtonHermieMentionsIsInTheMenu() {
        assertTrue(LiveTips.hermieWatch.any { "What can I do?" in it })
    }
}
