package com.reeflive.core.tracker

/** One step of an acclimation. [seconds] 0 is a hands-on step the keeper ticks off; otherwise a timer runs. */
data class AcclimationStep(val title: String, val detail: String, val seconds: Int)

enum class AcclimationMethod(val label: String) { DRIP("Drip"), FLOAT("Float and add water") }

/**
 * New-arrival acclimation steps (research 01 §4.2): float the bag 15 to 20 min for temperature, then drip at 1 to 2
 * drops a second for 60 to 90 min until the water is about 4x the bag volume; slower for sensitive animals (shrimp,
 * starfish, urchins, some corals). Bag water never goes into the display.
 */
object AcclimationPlan {
    fun steps(method: AcclimationMethod, sensitive: Boolean): List<AcclimationStep> {
        val float = if (sensitive) 20 else 15
        val out = ArrayList<AcclimationStep>()
        out += AcclimationStep("Lights down", "Dim the tank and room lights. New arrivals have spent hours in a dark box.", 0)
        out += AcclimationStep("Float the bag", "Float the sealed bag in the tank or sump so the water inside reaches tank temperature.", float * 60)
        when (method) {
            AcclimationMethod.DRIP -> {
                val half = if (sensitive) 45 else 30
                val drops = if (sensitive) "about 1 drop a second" else "1 to 2 drops a second"
                out += AcclimationStep("Set up the drip", "Tip the bag into a clean bucket, keeping the animal covered. Run airline tubing from the tank with a loose knot and start a drip at $drops.", 0)
                out += AcclimationStep("Drip until the water doubles", "Let it drip. The bucket should hold about twice the bag water when this ends.", half * 60)
                out += AcclimationStep("Pour off half", "Pour away half of the bucket water (down the drain, not into the tank).", 0)
                out += AcclimationStep("Drip until it doubles again", "Keep dripping. By the end the water has been diluted about four times.", half * 60)
            }
            AcclimationMethod.FLOAT -> {
                val wait = if (sensitive) 8 else 5
                for (i in 1..4) out += AcclimationStep("Add tank water ($i of 4)", "Open the bag, roll the top so it floats, and add tank water equal to about a quarter of the bag. Then wait.", wait * 60)
            }
        }
        out += AcclimationStep("Into its new home", "Check the bucket or bag is within 0.002 of tank salinity. Net the animal into quarantine or the tank and throw the bag water away.", 0)
        return out
    }

    /** Minutes of timers in the plan. */
    fun timedMinutes(steps: List<AcclimationStep>): Int = steps.sumOf { it.seconds } / 60
}
