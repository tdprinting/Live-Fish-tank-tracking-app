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

    fun of(o: Organism, sim: Simulation): String {
        val name = o.species.common.ifBlank { o.nickname }
        if (!o.alive) return "$name (dead)"
        val b = runCatching { o.brain.current }.getOrNull()
        val label = b?.label ?: o.behaviorName
        val verb = b?.targetVerb ?: VERBS[b?.name]
        if (verb != null) {
            val target = o.focusId?.let { id -> sim.organisms.firstOrNull { it.id == id && it.alive } } ?: guessTarget(o, sim, b?.name)
            if (target != null) return "$name $verb ${refer(o, target)}"
            if (b?.name in WATCHERS) return "$name ${label.replaceFirstChar { it.lowercase() }}"
        }
        return "$name · ${label.replaceFirstChar { it.lowercase() }}"
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
