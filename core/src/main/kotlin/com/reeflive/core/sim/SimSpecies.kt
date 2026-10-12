package com.reeflive.core.sim

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The simulation's view of a species sheet (SIMULATION §3.2). It deserializes the same JSON that
 * Track A ships in `core/src/main/resources/species/<id>.json` (unknown keys ignored, every field
 * defaulted) so the sim does not depend on Track A's `species` classes; an adapter is not needed.
 */
@Serializable
data class SimSpecies(
    val id: String,
    val kind: String = "FISH",                 // FISH | INVERT | CORAL
    val common: String = id,
    val family: String = "",
    val maxLengthCm: Double = 10.0,
    val typicalAdultCm: Double = 8.0,
    val growthCmPerYear: Double = 0.0,
    val lifespanYears: Double = 5.0,
    val depthRatio: Double = 0.35,
    val massCoeff: Double = 0.02,
    val locomotion: Locomotion = Locomotion(),
    val zone: Zone = Zone(),
    val activity: String = "DIURNAL",          // DIURNAL | NOCTURNAL | CREPUSCULAR
    val schooling: Boolean = false,
    val territorialRadius: Double = 0.0,
    val homeRadiusCm: Double = 30.0,
    val temperament: Temperament = Temperament(),
    val diet: List<String> = listOf("FLAKE", "MYSIS", "PELLET"),
    val feedsPerDay: Double = 2.0,
    val grazer: Boolean = false,
    val tolerance: Map<String, ToleranceRange> = emptyMap(),
    val diseaseSusceptibility: Map<String, Double> = emptyMap(),
    val hardiness: Double = 0.6,
    val starvationDays: Double = 21.0,
    val model: String? = null,
    val colors: Map<String, String> = emptyMap(),
    val iridescence: Double = 0.1,
    /** Optional explicit brain id (BrainKind name); otherwise derived from the genus. */
    val brain: String? = null,
    /** Dead body floats (true) or sinks (false); default by brain (SIMULATION §3.4). */
    val buoyantWhenDead: Boolean? = null,
) {
    val brainKind: BrainKind get() = BrainKind.resolve(this)
    val isCoral: Boolean get() = kind.equals("CORAL", ignoreCase = true)
    val coralKind: CoralKind get() = CoralKind.resolve(id)

    fun eats(food: FoodKind): Boolean = diet.any { it.equals(food.name, ignoreCase = true) } ||
        (food == FoodKind.PODS && diet.any { it.equals("PODS", true) || it.equals("PARASITES", true) })

    fun dietMatch(food: FoodKind): Double = when {
        eats(food) -> 1.0
        food == FoodKind.FLAKE && (eats(FoodKind.MYSIS) || eats(FoodKind.PELLET)) -> 0.5
        else -> 0.0
    }

    companion object {
        val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
        fun parse(text: String): SimSpecies = json.decodeFromString(serializer(), text)
    }
}

@Serializable
data class Locomotion(
    val mode: String = "LABRIFORM",            // LABRIFORM | ANGUILLIFORM | WALKING | SESSILE | CRAWLING
    val cruiseBL: Double = 1.2,
    val burstBL: Double = 5.0,
    val tailHzCruise: Double = 3.0,
    val turnRateDeg: Double = 220.0,
    val waveLen: Double = 0.9,
    val ampCruise: Double = 0.04,
    val ampBurst: Double = 0.09,
)

@Serializable
data class Zone(
    val yMin: Double = 0.1,
    val yMax: Double = 0.8,
    val openWater: Double = 0.5,
    val coverAffinity: Double = 0.4,
)

@Serializable
data class Temperament(
    val aggression: Double = 0.2,
    val boldness: Double = 0.5,
    val curiosity: Double = 0.5,
    val skittishness: Double = 0.5,
)

/** Tolerance bounds for one parameter; a missing side means "not applicable" (SIMULATION §3.3). */
@Serializable
data class ToleranceRange(
    val optLo: Double? = null,
    val optHi: Double? = null,
    val tolLo: Double? = null,
    val tolHi: Double? = null,
    val lethalLo: Double? = null,
    val lethalHi: Double? = null,
)

/**
 * Food kinds. [nutrientFactor] converts grams as fed into dry-flake-equivalent grams for the
 * nitrogen/phosphate load (frozen mysis is ~75 % water, nori and live pods are light). (tune)
 */
enum class FoodKind(val gramsPerParticle: Double, val sinkCmS: Double, val floatSeconds: Double, val nutrientFactor: Double) {
    FLAKE(1.0 / 40, 0.6, 45.0, 1.0),
    MYSIS(1.0 / 25, 1.5, 0.0, 0.3),
    PELLET(1.0 / 20, 3.0, 0.0, 1.0),
    NORI(0.0, 0.0, 0.0, 0.3),
    /** Live copepods/"pods" (sandbox, cleaner wrasse rescue: SIMULATION §3.4) and plankton dosing. */
    PODS(1.0 / 200, 0.3, 0.0, 0.2);

    companion object {
        fun of(s: String): FoodKind = entries.firstOrNull { it.name.equals(s, true) } ?: FLAKE
    }
}

/**
 * Behaviour archetype. Resolved from the sheet's `brain` field or the scientific genus in the id,
 * so Track A's sheet ids (genus_species) map automatically.
 */
enum class BrainKind(val genera: List<String>, val isFish: Boolean) {
    CLOWN(listOf("amphiprion", "premnas"), true),
    TANG(listOf("paracanthurus", "zebrasoma", "acanthurus"), true),
    CARDINAL(listOf("sphaeramia", "pterapogon", "apogon"), true),
    WRASSE(listOf("labroides"), true),
    EEL(listOf("echidna", "gymnothorax", "gymnomuraena"), true),
    ENGINEER_GOBY(listOf("pholidichthys"), true),
    HAWK(listOf("cirrhitichthys", "neocirrhites", "oxycirrhites", "paracirrhites"), true),
    LAWNMOWER_BLENNY(listOf("salarias"), true),
    PEEKER_BLENNY(listOf("ecsenius", "meiacanthus"), true),
    DOTTYBACK(listOf("pseudochromis", "pictichromis"), true),
    BANDED_SHRIMP(listOf("stenopus"), false),
    CLEANER_SHRIMP(listOf("lysmata"), false),
    HERMIT(listOf("clibanarius", "paguristes", "calcinus"), false),
    BRITTLE_STAR(listOf("ophiocoma", "ophiarachna", "ophiolepis", "ophiomastix"), false),
    FEATHER_DUSTER(listOf("sabellastarte", "sabella", "bispira"), false),
    NASSARIUS(listOf("nassarius"), false),
    ASTREA(listOf("lithopoma", "astraea", "astrea"), false),
    ASTERINA(listOf("asterina", "aquilonastra"), false),
    GENERIC_FISH(emptyList(), true),
    GENERIC_INVERT(emptyList(), false);

    /** Organisms that never swim freely (Steering uses surface-constrained or no movement). */
    val isBenthic: Boolean get() = !isFish
    val isSessile: Boolean get() = this == FEATHER_DUSTER

    companion object {
        fun resolve(s: SimSpecies): BrainKind {
            s.brain?.let { b ->
                entries.firstOrNull { it.name.equals(b, true) }?.let { return it }
                ALIASES[b.lowercase()]?.let { return it }
            }
            return byId(s.id) ?: if (s.kind.equals("FISH", true)) GENERIC_FISH else GENERIC_INVERT
        }

        /** Track A sheet `brain` strings that differ from the enum names; unknown strings fall back to genus/kind. */
        private val ALIASES = mapOf(
            "goby" to ENGINEER_GOBY, "engineer_goby" to ENGINEER_GOBY, "burrower" to ENGINEER_GOBY,
            "clownfish" to CLOWN, "tang" to TANG, "cardinal" to CARDINAL, "cleaner_wrasse" to WRASSE, "wrasse" to WRASSE,
            "hawkfish" to HAWK, "lawnmower" to LAWNMOWER_BLENNY, "blenny" to PEEKER_BLENNY, "dottyback" to DOTTYBACK,
            "banded_shrimp" to BANDED_SHRIMP, "cleaner_shrimp" to CLEANER_SHRIMP, "hermit" to HERMIT, "brittle_star" to BRITTLE_STAR,
            "feather_duster" to FEATHER_DUSTER, "nassarius" to NASSARIUS, "snail" to ASTREA, "asterina" to ASTERINA,
        )

        fun byId(id: String): BrainKind? {
            val genus = id.lowercase().substringBefore('_').substringBefore('-')
            return entries.firstOrNull { k -> k.genera.any { genus == it || genus.startsWith(it) } }
        }
    }
}

/** Coral archetypes (SIMULATION §3.6). */
enum class CoralKind(val genera: List<String>, val demandDkhPerDay: Double, val biomass: Double) {
    // Soft corals take up little carbonate; organ pipe builds a calcite skeleton. (tune: alk ≈ 7 with 10 %/week water changes)
    KENYA(listOf("capnella"), 0.004, 0.30),
    GSP(listOf("briareum", "pachyclavularia"), 0.006, 0.20),
    ZOA(listOf("zoanthus", "palythoa"), 0.003, 0.10),
    CLOVE(listOf("clavularia"), 0.002, 0.06),
    MUSHROOM(listOf("discosoma", "rhodactis", "ricordea"), 0.001, 0.04),
    MACRO(listOf("gracilaria", "botryocladia", "chaetomorpha"), 0.0, 0.15),
    ORGAN_PIPE(listOf("tubipora"), 0.008, 0.08),
    AIPTASIA(listOf("aiptasia", "exaiptasia"), 0.0, 0.02),
    GENERIC(emptyList(), 0.01, 0.05);

    companion object {
        fun resolve(id: String): CoralKind {
            val genus = id.lowercase().substringBefore('_')
            return entries.firstOrNull { k -> k.genera.any { genus.startsWith(it) } } ?: GENERIC
        }
    }
}
