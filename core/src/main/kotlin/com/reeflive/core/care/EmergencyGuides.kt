package com.reeflive.core.care

/** An offline step-by-step guide for a bad moment. [now] are the first-ten-minutes steps; [then] follow-up. */
data class Guide(val id: String, val title: String, val summary: String, val now: List<String>, val then: List<String>, val avoid: List<String> = emptyList())

/** A fish disease / problem matched by what the keeper sees (research 01 §4.3–4.5). */
data class Condition(val name: String, val signs: List<String>, val action: String, val urgent: Boolean)

/** Built-in emergency guides and the symptom checker. Reference only — always follow product labels. */
object EmergencyGuides {
    val guides: List<Guide> = listOf(
        Guide(
            "power", "Power outage", "Flow and oxygen matter most; temperature changes slowly in a 60 gal tank.",
            now = listOf(
                "Unplug the return pump and heater so nothing restarts dry or overheats when power returns.",
                "Run a battery air pump or a battery powerhead; if you have neither, gently stir the surface every 15–20 minutes.",
                "Keep the room warm or cool: insulate the tank with blankets in winter, keep lights off and the room shaded in summer.",
                "Do not feed until power is back.",
            ),
            then = listOf(
                "After 2–3 h without flow, check the sump and filter socks: rinse anything smelly before restarting.",
                "Restart the return, check for siphon overflow, then the skimmer once the water level settles.",
                "Test ammonia, temperature and salinity within 24 h; watch for fish breathing fast.",
                "Log the outage in the Journal so it shows on the graphs.",
            ),
            avoid = listOf("Running a generator indoors.", "Big water changes with cold or unheated water."),
        ),
        Guide(
            "ammonia", "Ammonia or nitrite spike", "Any ammonia above 0 is an emergency for fish and shrimp.",
            now = listOf(
                "Re-test to rule out a reagent mistake, then look for the cause: a dead fish, snail or rotting food.",
                "Remove anything dead and siphon uneaten food.",
                "Do a 20–30 % water change with temperature- and salinity-matched water.",
                "Dose a bottled ammonia binder/bacteria product per its label; stop feeding for 24–48 h.",
            ),
            then = listOf(
                "Test ammonia and nitrite daily until both read 0 for three days.",
                "Check the skimmer and flow; add fresh carbon.",
                "Do not add livestock until it has settled.",
            ),
            avoid = listOf("Changing everything at once (rock, sand, media) — that removes the bacteria you need."),
        ),
        Guide(
            "heat", "Overheating (above 84 °F)", "Warm water holds less oxygen; act before it passes 86 °F.",
            now = listOf(
                "Turn the lights off and unplug the heater.",
                "Open the lid, aim a fan across the water surface, add more surface agitation.",
                "Float sealed bags or bottles of ice in the sump — lower 1–2 °F per hour, not faster.",
            ),
            then = listOf("Check the heater for a stuck controller.", "Consider a temperature controller or chiller for summer."),
            avoid = listOf("Adding ice or cold water straight into the tank."),
        ),
        Guide(
            "cold", "Too cold (below 74 °F)", "Usually a failed heater.",
            now = listOf(
                "Check the heater light and controller; swap in a spare heater if you have one.",
                "Insulate the tank and warm the room; raise the temperature by about 2 °F per hour.",
            ),
            then = listOf("Keep a spare heater on the supplies list.", "Set the danger alert in Targets so ReefLive warns you sooner."),
        ),
        Guide(
            "bleaching", "Coral bleaching or pale-out", "Pale or white tissue that is still attached: the coral expelled its algae.",
            now = listOf(
                "Check temperature first, then salinity and alkalinity — log a full test.",
                "If you recently changed lights, lower intensity 20–30 % or move the coral lower.",
                "Hold parameters steady; stop dosing changes until you know the cause.",
            ),
            then = listOf(
                "Very low nitrate and phosphate (NO3 < 1, PO4 < 0.02) can starve corals — feed a little more.",
                "Most bleached soft corals recover in weeks if conditions are stable.",
            ),
            avoid = listOf("Chasing several parameters at once."),
        ),
        Guide(
            "salinity", "Salinity way off", "Usually a stuck or empty top-off.",
            now = listOf(
                "Re-check with a calibrated refractometer (1.026 solution).",
                "Too high: top off with fresh RO/DI slowly — no more than 0.002 SG per day.",
                "Too low: replace water with slightly saltier mix, again no more than 0.002 per day.",
            ),
            then = listOf("Fix the top-off sensor or reservoir.", "Log daily until it is back in range."),
        ),
        Guide(
            "fish_down", "A fish is missing or dead", "A decaying fish fouls the water fast.",
            now = listOf(
                "Look under rocks, behind the overflow, in the sump and on the floor (jumpers).",
                "Remove the body with a net or tongs if you find it.",
                "Test ammonia the same day and again the next day.",
            ),
            then = listOf("Mark it in Livestock (deceased) with a note so health history stays complete."),
        ),
    )

    val conditions: List<Condition> = listOf(
        Condition("Marine ich (white spot)", listOf("White salt-grain spots", "Scratching on rock", "Fast breathing"),
            "Quarantine and treat with copper (14–28 days) or tank transfer; the display must stay fishless 72 days to clear it.", urgent = true),
        Condition("Marine velvet", listOf("Gold or dusty sheen", "Clamped fins", "Fast breathing", "Scratching on rock"),
            "Very fast and deadly: move to quarantine and treat with copper today.", urgent = true),
        Condition("Brooklynella", listOf("Thick mucus or slime", "Clamped fins", "Gasping at surface"),
            "Common in clownfish: freshwater dip, then formalin baths in quarantine. Act within hours.", urgent = true),
        Condition("Uronema", listOf("Red sores or lesions", "Erratic swimming", "Rapid decline"),
            "Isolate immediately; formalin or copper in quarantine.", urgent = true),
        Condition("Flukes", listOf("Scratching on rock", "Cloudy eye", "Fast breathing"),
            "Praziquantel in quarantine; repeat the dose after 5–7 days for hatching eggs.", urgent = false),
        Condition("HLLE (head and lateral line erosion)", listOf("Pits on head or lateral line"),
            "Not contagious: improve diet (vitamins, nori, varied food), water quality, and remove carbon dust.", urgent = false),
        Condition("Bacterial fin rot", listOf("Ragged or frayed fins", "Red sores or lesions"),
            "Improve water quality; antibiotics in quarantine if it spreads.", urgent = false),
        Condition("Stress / poor water", listOf("Fast breathing", "Hiding and not eating", "Clamped fins"),
            "Test ammonia, nitrite, temperature, salinity and pH now; check for a bully tankmate.", urgent = false),
        Condition("Pop-eye", listOf("Cloudy eye", "Swollen eye"),
            "Usually injury or water quality: clean water, Epsom salt bath; antibiotics if both eyes.", urgent = false),
    )

    /** Every sign used by [conditions], for the symptom picker. */
    val allSigns: List<String> get() = conditions.flatMap { it.signs }.distinct()

    /** Conditions ranked by how many of [signs] they share (at least one). */
    fun match(signs: Set<String>): List<Condition> =
        conditions.map { it to it.signs.count { s -> s in signs } }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<Condition, Int>> { it.second }.thenByDescending { it.first.urgent })
            .map { it.first }
}
