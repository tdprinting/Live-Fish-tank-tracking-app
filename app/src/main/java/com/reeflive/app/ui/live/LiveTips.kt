package com.reeflive.app.ui.live

/**
 * The words of the live screen's help (UI_STYLE §10.8): the first-time tips card for Watch and for Sandbox (also
 * opened from More → "What can I do?") and the tips Hermie gives when he is tapped. Plain strings, no Compose, so a
 * test can check they name only controls that exist. Written for a first-time grown-up or a child: one thing per
 * line, the button's own name in the words, no reef jargon.
 */
object LiveTips {
    const val WATCH_TITLE = "Welcome to your live tank!"
    const val SANDBOX_TITLE = "This is your pretend tank!"

    /** One line per tip, in the order the card shows them (the card pairs each with a glyph). */
    val watch = listOf(
        "Tap a fish to meet it. Tap Follow on its card to swim along with it.",
        "Tap the glass and see who comes to look. Tap the sand or a rock too.",
        "Drag to look around. Pinch to zoom in and out.",
        "Supplies opens the shelf: tap a food or tool, then tap the tank to use it.",
        "Tap the cleaning magnet on the front glass, then rub the glass to clean it.",
        "Tap Sandbox at the top to try things out. Your real tank stays safe.",
        "Stuck? Press Back, or tap Hermie at the top for a tip.",
    )

    val sandbox = listOf(
        "Tap a big bubble, then a tile. Watch it happen in the tank. Your real tank never changes.",
        "Trouble breaks things on purpose. Speed up time in More to see what happens.",
        "Each card has a More button with the grown-up sliders.",
        "Made a mess? More has a big Reset tank button.",
        "Tap Watch at the top to go back to your real tank.",
    )

    /** Hermie's tips, one per tap on him. */
    val hermieWatch = listOf(
        "Tap a fish to meet it!",
        "Tap the glass and see who comes to look!",
        "Drag to look around, pinch to zoom.",
        "Supplies has food. Tap one, then tap the water.",
        "Tap the magnet on the glass to clean it.",
        "Tap Sandbox at the top to play. Your real tank stays safe.",
        "Lost? More has a What can I do? button.",
    )

    val hermieSandbox = listOf(
        "This is a pretend tank. Nothing here changes your real one.",
        "Tap the glass and see who comes to look!",
        "Try Trouble, then speed up time in More.",
        "Every card has a More button with the grown-up sliders.",
        "Tap the tank while something animates to skip it.",
        "Made a mess? More has a big Reset button.",
        "Tap Watch at the top to see your real tank again.",
    )

    /** Hermie's first line: tells a new user he is the help button. */
    const val HELLO = "Hi, I'm Hermie! Tap me any time for a tip."
}
