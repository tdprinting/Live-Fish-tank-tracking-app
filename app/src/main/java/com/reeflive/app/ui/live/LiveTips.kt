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
    const val MY_TANK_TITLE = "This is your very own tank!"

    /** One line per tip, in the order the card shows them (the card pairs each with a glyph). */
    val watch = listOf(
        "Tap a fish to meet it. Tap Follow on its card to swim along with it.",
        "A fish's card says what it is thinking. Thought bubbles in More shows it in the tank.",
        "Tap the glass and see who comes to look. Tap the sand or a rock too.",
        "Hold a finger on the glass, then slide it: fish follow. Flick it to make a splash of current.",
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

    /** My tank: the keeper's own tank, started empty and saved on this device. */
    val myTank = listOf(
        "Add life brings in any fish, shrimp, crab or coral you like. Drag the bag onto the water.",
        "Everything here is saved: names, personalities and how much each animal grows.",
        "Tap an animal, then Give it a name.",
        "Time keeps going while the app is closed, so check in and feed them.",
        "More has Start over if you want a different tank.",
    )

    /** Hermie's tips, one per tap on him. */
    val hermieWatch = listOf(
        "Tap a fish to meet it!",
        "Tap the glass and see who comes to look!",
        "Hold a finger on the glass and slide it. Fish follow!",
        "Tap the top of the water. Hungry fish come up!",
        "Tap a snail or crab. It hides in its shell!",
        "Drag to look around, pinch to zoom.",
        "Supplies has food. Tap one, then tap the water.",
        "Tap the magnet on the glass to clean it.",
        "What are the fish thinking? Try Thought bubbles in More!",
        "Tap Sandbox at the top to play. Your real tank stays safe.",
        "Lost? More has a What can I do? button.",
    )

    val hermieSandbox = listOf(
        "This is a pretend tank. Nothing here changes your real one.",
        "Tap the glass and see who comes to look!",
        "Hold the glass, then flick your finger. Whoosh!",
        "Try Trouble, then speed up time in More.",
        "Every card has a More button with the grown-up sliders.",
        "Tap the tank while something animates to skip it.",
        "Thought bubbles in More show what the fish plan to do.",
        "Made a mess? More has a big Reset button.",
        "Tap Watch at the top to see your real tank again.",
    )

    val hermieMyTank = listOf(
        "This is your own tank. Everything here is saved!",
        "Add life brings in new friends in a bag.",
        "Tap an animal to give it a name.",
        "Feed them every day so they grow big!",
        "Watch the water. Clean water means happy fish.",
        "Thought bubbles in More show what they are thinking.",
        "Tap Watch at the top to see your real tank again.",
    )

    /** Hermie's first line: tells a new user he is the help button. */
    const val HELLO = "Hi, I'm Hermie! Tap me any time for a tip."
}
