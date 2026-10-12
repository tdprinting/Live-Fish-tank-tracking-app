package com.reeflive.core.species

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

@Serializable
enum class Risk(val label: String, val level: Int) {
    NONE("Peaceful", 0), LOW("Low risk", 1), MODERATE("Moderate risk", 2), MODERATE_HIGH("Moderate–high risk", 3), HIGH("High risk", 4);

    companion object {
        fun parse(code: String): Risk = when (code.trim().uppercase().replace(" ", "")) {
            "N", "NONE" -> NONE
            "L", "LOW" -> LOW
            "M", "MODERATE" -> MODERATE
            "M-H", "MH", "MODERATE_HIGH" -> MODERATE_HIGH
            "H", "HIGH" -> HIGH
            else -> NONE
        }
    }
}

/** One pair from 02 §9 / 04 §14. [threat] names the species that poses the risk, when directional. */
@Serializable
data class Interaction(val a: String, val b: String, val risk: String, val note: String = "", val threat: String? = null)

/** Symmetric pair lookup; unspecified pairs are [Risk.NONE]. */
class InteractionMatrix(entries: List<Interaction>) {
    private val map: Map<Pair<String, String>, Interaction> = buildMap {
        for (e in entries) {
            val k = key(e.a, e.b)
            val prev = get(k)
            if (prev == null || Risk.parse(e.risk).level > Risk.parse(prev.risk).level) put(k, e)
        }
    }

    private fun key(a: String, b: String) = if (a <= b) a to b else b to a

    fun risk(a: String, b: String): Risk = if (a == b) sameSpecies(a) else map[key(a, b)]?.let { Risk.parse(it.risk) } ?: Risk.NONE

    fun entry(a: String, b: String): Interaction? = map[key(a, b)]

    /** Conspecific risk (territorial species fight their own kind), when listed as an a==b pair. */
    private fun sameSpecies(a: String): Risk = map[a to a]?.let { Risk.parse(it.risk) } ?: Risk.NONE

    val size: Int get() = map.size

    companion object {
        val EMPTY = InteractionMatrix(emptyList())

        fun parse(text: String): InteractionMatrix =
            InteractionMatrix(SpeciesDb.JSON.decodeFromString(ListSerializer(Interaction.serializer()), text))
    }
}
