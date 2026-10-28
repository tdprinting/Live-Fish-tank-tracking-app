package com.reeflive.core.ai

import com.reeflive.core.sim.BrainKind
import com.reeflive.core.sim.Organism
import com.reeflive.core.sim.Simulation

/**
 * Follow-mode caption: what the animal is doing and to whom, e.g.
 * "Falco hawkfish watching the skunk cleaner shrimp", "Cleaner wrasse cleaning the blue tang".
 */
object Caption {
    /** Verb phrases for species behaviors that involve another animal (by Behavior.name). */
    private val VERBS = mapOf(
        "defend" to "driving off", "chase" to "chasing off", "rival" to "chasing", "territory" to "chasing",
        "clean" to "cleaning", "climb" to "cleaning", "visit" to "waiting to be cleaned by", "pounce" to "pouncing on",
        "hunt" to "stalking", "perch" to "watching", "perchhole" to "watching", "hang" to "watching",
        "rivalry" to "chasing", "grab" to "grabbing at", "keepclear" to "keeping clear of",
    )
    private val WATCHERS = setOf("perch", "perchhole", "hang")

    /** Caption plus its parts: [intent] is the phrase without the name ("eyeing the skunk cleaner shrimp"). */
    data class Described(val caption: String, val intent: String, val targetId: String?)

    fun of(o: Organism, sim: Simulation): String = describe(o, sim).caption

    fun describe(o: Organism, sim: Simulation): Described {
        val name = o.species.common.ifBlank { o.nickname }
        if (!o.alive) return Described("$name (dead)", "dead", null)
        // the label on screen (held for a minimum dwell, Organism.displayLabel): the caption says the same thing as the
        // card, and names a target only while that label is the live behaviour's own
        val label = o.displayLabel
        val b = runCatching { o.brain.current }.getOrNull()?.takeIf { it.label == label }
        val lower = label.replaceFirstChar { it.lowercase() }
        val verb = b?.targetVerb ?: VERBS[b?.name]
        if (verb != null) {
            val target = o.focusId?.let { id -> sim.organisms.firstOrNull { it.id == id && it.alive } } ?: guessTarget(o, sim, b?.name)
            if (target != null) { val i = "$verb ${refer(o, target)}"; return Described("$name $i", i, target.id) }
            if (b?.name in WATCHERS) return Described("$name $lower", lower, null)
        }
        return Described("$name · $lower", lower, null)
    }

    private fun refer(o: Organism, t: Organism): String {
        val common = t.species.common.ifBlank { t.nickname }.lowercase()
        return if (t.species.id == o.species.id) "another ${common.removePrefix("ocellaris ")}" else "the $common"
    }

    private fun guessTarget(o: Organism, sim: Simulation, behavior: String?): Organism? {
        val reach = if (behavior in WATCHERS) 25.0 else 2 * o.lengthCm + 10
        return sim.organisms.asSequence()
            .filter { it !== o && it.alive && it.visible && it.pos.distanceTo(o.pos) < reach }
            .filter { behavior != "visit" || it.brainKind == BrainKind.WRASSE || it.brainKind == BrainKind.CLEANER_SHRIMP }
            .filter { behavior !in WATCHERS || it.vel.length() > 0.5 }
            .minByOrNull { it.pos.distanceTo(o.pos) }
    }
}
