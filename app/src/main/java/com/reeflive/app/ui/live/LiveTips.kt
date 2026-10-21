package com.reeflive.app.ui.live

/**
 * The words of the live screen's help (UI_STYLE §10.8): the first-time tips card for My tank and for Watch (also
 * opened from Settings → "What can I do?") and the tips Hermie gives when he is tapped. The old pretend Sandbox tank is
 * gone: [sandbox] and [hermieSandbox] now only cover Watch's fast-forward copy, which keeps Watch's menu. Plain strings, no Compose, so a
 * test can check they name only controls that exist. Written for a first-time grown-up or a child: one thing per
 * line, the button's own name in the words, no reef jargon.
 */
object LiveTips {
    const val WATCH_TITLE = "Welcome to your live tank!"
    const val SANDBOX_TITLE = "Fast-forward!"
    const val MY_TANK_TITLE = "This is your very own tank!"

    /** One line per tip, in the order the card shows them (the card pairs each with a glyph). */
    val watch = listOf(
        "Tap a fish to meet it. Press and hold a fish (or tap Follow on its card) to swim along with it.",
        "A fish's card says what it is thinking. Thought bubbles in Settings shows it in the tank.",
        "Tap the glass and see who comes to look. Tap the sand or a rock too.",
        "Hold a finger on the glass, then slide it: fish follow. Flick it to make a splash of current.",
        "Drag to look around. Pinch to zoom in and out.",
        "Supplies opens the shelf: tap a food or tool, then tap the tank to use it.",
        "Tap the cleaning magnet on the front glass, then rub the glass to clean it.",
        "Tank Health shows who is hungry and what the tank needs. One tap fixes it.",
        "Tap My tank at the top to build your very own tank. Your real tank stays safe.",
        "Stuck? Press Back, or tap Hermie at the top for a tip.",
    )

    val sandbox = listOf(
        "This is a fast-forward copy of your real tank. Your real tank keeps real time.",
        "Slide Time in Look back to real time to return to your real tank.",
        "Tap Watch at the top to go back to your real tank.",
    )

    /** My tank: the keeper's own tank, started empty and saved on this device. */
    val myTank = listOf(
        "Shop takes you to the fish stores. Buy an animal, then drag its bag onto the water.",
        "Tank Health shows who is hungry. Feed them all with one tap.",
        "Tank has the lights, heater, pumps and rocks. Its More button has the grown-up sliders.",
        "Everything here is saved: names, personalities and how much each animal grows.",
        "Tap an animal, then Give it a name.",
        "Time keeps going while the app is closed, so check in and feed them.",
        "Settings has Start over if you want a different tank.",
    )

    /** Hermie's tips, one per tap on him. */
    val hermieWatch = listOf(
        "Tap a fish to meet it!",
        "Press and hold a fish to ride along with it!",
        "Tap the glass and see who comes to look!",
        "Hold a finger on the glass and slide it. Fish follow!",
        "Tap the top of the water. Hungry fish come up!",
        "Tap a snail or crab. It hides in its shell!",
        "Drag to look around, pinch to zoom.",
        "Supplies has food. Tap one, then tap the water.",
        "Tap the magnet on the glass to clean it.",
        "What are the fish thinking? Try Thought bubbles in Settings!",
        "Tap My tank at the top to build your own tank!",
        "Lost? Settings has a What can I do? button.",
    )

    val hermieSandbox = listOf(
        "This is a fast-forward copy. Your real tank keeps real time.",
        "Tap the glass and see who comes to look!",
        "Hold the glass, then flick your finger. Whoosh!",
        "Thought bubbles in Settings show what the fish plan to do.",
        "Slide Time in Look back to real time to go back.",
    )

    val hermieMyTank = listOf(
        "This is your own tank. Everything here is saved!",
        "Tap Shop to buy new friends at the fish stores!",
        "Tap an animal to give it a name.",
        "Feed them every day so they grow big!",
        "Tank Health feeds every hungry one with one tap!",
        "Watch the water. Clean water means happy fish.",
        "Thought bubbles in Settings show what they are thinking.",
        "Tap Watch at the top to see your real tank again.",
    )

    /** Hermie's first line: tells a new user he is the help button. */
    const val HELLO = "Hi, I'm Hermie! Tap me any time for a tip."
}
