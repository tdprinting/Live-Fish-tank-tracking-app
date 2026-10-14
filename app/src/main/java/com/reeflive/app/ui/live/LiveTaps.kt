package com.reeflive.app.ui.live

/** What one tap on empty water (no fish under the finger) does on the live screen (UI_STYLE §10.8). */
enum class WaterTap { NOTHING, SHOW_CONTROLS, CLOSE_CARD, DESELECT }

/**
 * Plain rules for taps on the tank, kept out of Compose so they can be tested. A tap on the water never hides
 * anything: hiding the buttons is only ever a choice made from More → Hide buttons, so a missed fish cannot make the
 * whole screen go blank. The water tap only undoes things: it brings hidden buttons back, shuts an open card, or
 * puts down the picked fish. While a fish is being followed it does nothing (the follow bar's button and Back leave).
 */
object LiveTaps {
    fun emptyWater(
        justSkipped: Boolean,
        controlsHidden: Boolean,
        following: Boolean,
        cardOpen: Boolean,
        fishPicked: Boolean,
    ): WaterTap = when {
        justSkipped -> WaterTap.NOTHING
        controlsHidden -> WaterTap.SHOW_CONTROLS
        following -> WaterTap.NOTHING
        cardOpen -> WaterTap.CLOSE_CARD
        fishPicked -> WaterTap.DESELECT
        else -> WaterTap.NOTHING
    }
}

/**
 * [coerceIn] that never throws: when the room between [lo] and [hi] has gone negative (a tiny split-screen window,
 * a long caption on a narrow phone) it returns the middle instead of crashing the draw pass.
 */
fun Float.clampSafe(lo: Float, hi: Float): Float = if (hi < lo) (lo + hi) / 2f else coerceIn(lo, hi)
