package com.reeflive.core.sim

import kotlinx.serialization.Serializable
import kotlin.math.min
import kotlin.math.pow
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
    /** Sessile colonies handled by CoralSim: corals and macroalgae (Track A sheets use kind "ALGAE" for Gracilaria). */
    val isCoral: Boolean get() = kind.equals("CORAL", ignoreCase = true) || kind.equals("ALGAE", ignoreCase = true) || kind.equals("MACROALGAE", ignoreCase = true)
    val coralKind: CoralKind get() = CoralKind.resolve(id)

    private fun has(tag: String): Boolean = diet.any { it.equals(tag, ignoreCase = true) }

    /**
     * Does the sheet's diet list cover [food]? Sheets use food names (FLAKE, MYSIS, BRINE, KRILL, NORI, ...) plus
     * broader tags (MEATY, ALGAE, SPIRULINA, PLANKTON, PHYTOPLANKTON, ZOOPLANKTON, PODS, PARASITES) that map onto the
     * particle kinds here.
     */
    fun eats(food: FoodKind): Boolean = has(food.name) || when (food) {
        FoodKind.PODS -> has("PODS") || has("PARASITES")
        FoodKind.PRAWN, FoodKind.KRILL -> has("MEATY")
        FoodKind.PLANKTON, FoodKind.CORAL_FOOD -> has("PHYTOPLANKTON") || has("ZOOPLANKTON") || has("PLANKTON")
        FoodKind.CYCLOPS -> has("ZOOPLANKTON") || has("PODS") || has("PLANKTON")
        FoodKind.ALGAE_WAFER -> has("SPIRULINA") || has("NORI") || (grazer && has("ALGAE"))
        FoodKind.FLOATING_PELLET -> has("PELLET")
        else -> false
    }

    /**
     * How well this species does on [food] (research 04 §15 item 9): [dietMatch] scaled by a
     * species-specific preference, e.g. Nassarius on raw prawn or a cleaner shrimp on mysis.
     */
    fun foodPreference(food: FoodKind): Double = dietMatch(food) * when {
        food == FoodKind.PRAWN && brainKind == BrainKind.NASSARIUS -> 1.5
        food == FoodKind.MYSIS && brainKind == BrainKind.CLEANER_SHRIMP -> 1.3
        food == FoodKind.PLANKTON && brainKind == BrainKind.FEATHER_DUSTER -> 1.2
        // hawkfish are krill specialists in the shops' experience; grazers prefer a wafer to a pellet (design estimates)
        food == FoodKind.KRILL && brainKind == BrainKind.HAWK -> 1.3
        food == FoodKind.ALGAE_WAFER && (brainKind == BrainKind.TANG || brainKind == BrainKind.LAWNMOWER_BLENNY || brainKind == BrainKind.ASTREA) -> 1.2
        food == FoodKind.CYCLOPS && (brainKind == BrainKind.CARDINAL || brainKind == BrainKind.PEEKER_BLENNY || brainKind == BrainKind.FEATHER_DUSTER) -> 1.2
        else -> 1.0
    }

    /**
     * 1 = listed in the diet, 0 = ignored, in between = eaten when offered though not a staple: flake for any
     * mysis/pellet eater, brine shrimp for any mysis eater, krill picked at by smaller mouths, a wafer nibbled by
     * omnivores and the coral-food cloud by flake eaters (design estimates).
     */
    fun dietMatch(food: FoodKind): Double = when {
        eats(food) -> 1.0
        food == FoodKind.FLAKE && (eats(FoodKind.MYSIS) || eats(FoodKind.PELLET)) -> 0.5
        food == FoodKind.BRINE && eats(FoodKind.MYSIS) -> 0.8
        food == FoodKind.CYCLOPS && (eats(FoodKind.BRINE) || eats(FoodKind.MYSIS)) -> 0.7
        food == FoodKind.KRILL && eats(FoodKind.MYSIS) -> 0.6
        food == FoodKind.FLOATING_PELLET && eats(FoodKind.FLAKE) -> 0.6
        food == FoodKind.ALGAE_WAFER && has("ALGAE") -> 0.4
        food == FoodKind.ALGAE_WAFER && eats(FoodKind.PELLET) -> 0.25
        food == FoodKind.CORAL_FOOD && eats(FoodKind.FLAKE) -> 0.3
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

/**
 * Swimming speeds (research 07 gap 2). No species-level adult BL/s values exist for this stock: the
 * per-species `cruiseBL` / `burstBL` are [I], checked against family-level field data. Fulton 2007
 * (Coral Reefs 26:217): labriform reef fishes average up to 68.8 cm/s in the field (≈ 70 % of Ucrit,
 * max 82.4 cm/s), body-caudal swimmers ≤ 39 cm/s. Bellwood & Fisher 2001: relative speed (BL/s) falls
 * with size. So cruise is capped at the family field speed, and burst BL/s scales as
 * (typical adult / length)^0.5 once a fish outgrows its typical adult size.
 */
object SwimSpeed {
    const val LABRIFORM_FIELD_CM_S = 68.8
    const val BODY_CAUDAL_FIELD_CM_S = 39.0
    const val BURST_SIZE_EXP = 0.5

    fun fieldCeiling(mode: String): Double = if (mode == "LABRIFORM") LABRIFORM_FIELD_CM_S else BODY_CAUDAL_FIELD_CM_S

    fun cruise(sp: SimSpecies, lengthCm: Double): Double {
        val v = sp.locomotion.cruiseBL * lengthCm
        return if (sp.kind == "FISH") min(v, fieldCeiling(sp.locomotion.mode)) else v
    }

    fun burst(sp: SimSpecies, lengthCm: Double): Double {
        val adult = sp.typicalAdultCm
        val rel = if (sp.kind == "FISH" && adult > 0 && lengthCm > adult) (adult / lengthCm).pow(BURST_SIZE_EXP) else 1.0
        return sp.locomotion.burstBL * rel * lengthCm
    }
}

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
 * Food kinds (SIMULATION §4.6 table). [gramsPerParticle] sets how many particles a dose makes, [sinkCmS] the
 * terminal sink speed, [floatSeconds] how long a buoyant food sits on the surface first, and [nutrientFactor]
 * converts grams as fed into dry-flake-equivalent grams for the nitrogen/phosphate load (frozen foods are
 * 75–90 % water; nori and live pods are light). New kinds are appended: the renderer's colour and size
 * tables are indexed by ordinal.
 */
enum class FoodKind(val gramsPerParticle: Double, val sinkCmS: Double, val floatSeconds: Double, val nutrientFactor: Double) {
    FLAKE(1.0 / 40, 0.6, 45.0, 1.0),
    MYSIS(1.0 / 25, 1.5, 0.0, 0.3),
    PELLET(1.0 / 20, 3.0, 0.0, 1.0),
    NORI(0.0, 0.0, 0.0, 0.3),
    /** Live copepods/"pods" (sandbox, cleaner wrasse rescue: SIMULATION §3.4) and plankton dosing. */
    PODS(1.0 / 200, 0.3, 0.0, 0.2),
    /** Raw prawn chunk for Nassarius and other scavengers (research 04 §15 item 9, [GS-NAS]); sinks fast. */
    PRAWN(1.0 / 10, 4.0, 0.0, 0.4),
    /** Plankton / phytoplankton dosed near a feather duster's crown ([TFH-SI]); a drifting cloud that does not settle. */
    PLANKTON(1.0 / 400, 0.1, 0.0, 0.15),
    /** Frozen adult brine shrimp (Artemia, ~8–10 mm): a slow sinker fish pick off mid-water; mostly water. */
    BRINE(1.0 / 60, 0.5, 0.0, 0.2),
    /** Frozen krill pieces (Euphausia): bigger meaty chunks for hawkfish, dottybacks, shrimp and scavengers. */
    KRILL(1.0 / 8, 2.5, 0.0, 0.35),
    /** Floating pellets: sit on the surface for minutes, then waterlog and sink slowly (design estimate). */
    FLOATING_PELLET(1.0 / 20, 1.5, 300.0, 1.0),
    /** Sinking algae wafer for snails, hermits, blennies and the tang: drops fast, holds together for hours. */
    ALGAE_WAFER(1.0 / 6, 3.5, 0.0, 1.0),
    /** Coral food powder (Reef-Roids class, ~150–200 µm): a fine suspended cloud corals and filter feeders catch. */
    CORAL_FOOD(1.0 / 300, 0.05, 0.0, 1.0),
    /** Frozen Cyclops ("Cyclop-eeze", ~0.8 mm red copepods): drifts; small-mouthed fish and filter feeders love it. */
    CYCLOPS(1.0 / 250, 0.15, 0.0, 0.3);

    /** Drifting live/suspended foods: advected by the flow, never settle on rock or sand. */
    val suspended: Boolean get() = this == PODS || this == PLANKTON || this == CORAL_FOOD || this == CYCLOPS

    /** Sits on the surface for [floatSeconds] before sinking (flake, floating pellets). */
    val floats: Boolean get() = floatSeconds > 0.0

    /** Fine suspended foods that coral polyps and feather dusters catch out of the water column. */
    val coralCatchable: Boolean get() = this == CORAL_FOOD || this == CYCLOPS || this == PLANKTON || this == PODS

    /** Uneaten particles expire to detritus after this long (SIMULATION §4.6): 20 min, 30 min drifting, 3 h for a wafer. */
    val lifeSeconds: Double get() = when {
        this == ALGAE_WAFER -> 3 * 3600.0
        suspended -> 1800.0
        else -> 1200.0
    }

    /** Grams one tap of the feed button drops (a pinch, a cube, a sheet, a small dose of powder). */
    val typicalDoseGrams: Double get() = when (this) {
        PLANKTON -> 0.2
        PODS, CORAL_FOOD -> 0.3
        CYCLOPS, ALGAE_WAFER -> 0.5
        else -> 1.0
    }

    val group: FoodGroup get() = when (this) {
        FLAKE, PELLET, FLOATING_PELLET -> FoodGroup.DRY
        MYSIS, BRINE, KRILL, PRAWN -> FoodGroup.FROZEN
        NORI, ALGAE_WAFER -> FoodGroup.GREENS
        PODS, PLANKTON, CORAL_FOOD, CYCLOPS -> FoodGroup.TINY
    }

    /** Plain-words name for buttons and toasts. */
    val label: String get() = when (this) {
        FLAKE -> "Flakes"
        MYSIS -> "Mysis shrimp"
        PELLET -> "Sinking pellets"
        NORI -> "Nori sheet"
        PODS -> "Live pods"
        PRAWN -> "Prawn chunk"
        PLANKTON -> "Phytoplankton"
        BRINE -> "Brine shrimp"
        KRILL -> "Krill"
        FLOATING_PELLET -> "Floating pellets"
        ALGAE_WAFER -> "Algae wafer"
        CORAL_FOOD -> "Coral food"
        CYCLOPS -> "Cyclops"
    }

    /** One line a child can read: who eats it and what it does in the water. */
    val blurb: String get() = when (this) {
        FLAKE -> "Floats first, then sinks. Most fish grab some."
        MYSIS -> "Frozen little shrimp. The favourite of nearly every fish."
        PELLET -> "Sinks fast so bottom fish and shrimp get a share."
        NORI -> "Seaweed on a clip. The tang and blennies nibble it all day."
        PODS -> "Tiny live bugs. The wrasse and cardinal pick them out of the water."
        PRAWN -> "A chunk of raw prawn for the snails and crabs on the sand."
        PLANKTON -> "Green water for the feather duster and the corals."
        BRINE -> "Frozen orange shrimp. Easy to eat, but mostly water."
        KRILL -> "Chunky red shrimp. Hawkfish and shrimp love it."
        FLOATING_PELLET -> "Stays on top a while. Clownfish come up to grab them."
        ALGAE_WAFER -> "A green disc that sinks. Snails and hermits crowd round it."
        CORAL_FOOD -> "Fine powder that corals catch with their polyps."
        CYCLOPS -> "Tiny red bugs. Small fish and corals snap them up."
    }

    companion object {
        fun of(s: String): FoodKind = entries.firstOrNull { it.name.equals(s, true) } ?: FLAKE
    }
}

/** Shelf the food comes from, for grouping the picker. */
enum class FoodGroup(val label: String) { FROZEN("Frozen"), DRY("Dry"), GREENS("Greens"), TINY("Tiny & live") }

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
    SALLY_CRAB(listOf("percnon"), false),
    BRITTLE_STAR(listOf("ophiocoma", "ophiarachna", "ophiolepis", "ophiomastix"), false),
    FEATHER_DUSTER(listOf("sabellastarte", "sabella", "bispira"), false),
    NASSARIUS(listOf("nassarius"), false),
    ASTREA(listOf("lithopoma", "astraea", "astrea"), false),
    ASTERINA(listOf("asterina", "aquilonastra"), false),
    /** Sand-sifting sleeper gobies (diamond goby, *Valenciennea puellaris*; research 16). Sandbox only. */
    DIAMOND_GOBY(listOf("valenciennea"), true),
    /**
     * Bobtail squid (*Euprymna berryi* and relatives, Sepiolidae; research cephalopod_squid). Sandbox only. A mollusc, so not a fish
     * for the health, disease and social rules ([isFish] false); its FIN_JET gait still uses the swim integrator.
     */
    BOBTAIL_SQUID(listOf("euprymna", "sepiola", "sepietta", "rondeletiola"), false),
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
            "banded_shrimp" to BANDED_SHRIMP, "cleaner_shrimp" to CLEANER_SHRIMP, "hermit" to HERMIT, "crab" to SALLY_CRAB, "sally_lightfoot" to SALLY_CRAB, "sally_crab" to SALLY_CRAB, "brittle_star" to BRITTLE_STAR,
            "feather_duster" to FEATHER_DUSTER, "nassarius" to NASSARIUS, "snail" to ASTREA, "asterina" to ASTERINA,
            "diamond_goby" to DIAMOND_GOBY, "sand_sifter" to DIAMOND_GOBY, "sleeper_goby" to DIAMOND_GOBY,
            "bobtail_squid" to BOBTAIL_SQUID, "squid" to BOBTAIL_SQUID, "sepiolid" to BOBTAIL_SQUID,
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
