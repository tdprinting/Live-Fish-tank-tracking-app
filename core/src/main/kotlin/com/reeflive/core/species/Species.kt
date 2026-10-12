package com.reeflive.core.species

import kotlinx.serialization.Serializable

@Serializable
enum class SpeciesKind { FISH, INVERT, CORAL, ALGAE }

@Serializable
data class Locomotion(
    /** LABRIFORM, ANGUILLIFORM, PERCHING, WALKING, CRAWLING, SESSILE … */
    val mode: String,
    val cruiseBL: Double = 1.0,
    val burstBL: Double = 4.0,
    val tailHzCruise: Double = 3.0,
    val turnRateDeg: Double = 180.0,
    val waveLen: Double = 0.9,
    val ampCruise: Double = 0.04,
    val ampBurst: Double = 0.09,
)

/** Vertical band of the water column (0 = sand, 1 = surface) plus open-water / cover preference. */
@Serializable
data class Zone(val yMin: Double, val yMax: Double, val openWater: Double, val coverAffinity: Double)

@Serializable
data class Temperament(
    val aggression: Double = 0.2,
    val boldness: Double = 0.5,
    val curiosity: Double = 0.5,
    val skittishness: Double = 0.5,
)

@Serializable
data class SizeInfo(val lod0Tris: Int = 0)

/** Coral/sessile extras for the app and CoralSim. Light values are fixture intensity 0..1. */
@Serializable
data class CoralInfo(
    val lightLo: Double = 0.3,
    val lightHi: Double = 0.8,
    /** LOW / MODERATE / HIGH */
    val flow: String = "MODERATE",
    val growth: String = "Moderate",
    val aggression: String = "None",
    val fluorescence: Double = 0.0,
    val photosynthetic: Boolean = true,
)

/**
 * Species sheet (SIMULATION §3.2). One JSON file per species in `core/src/main/resources/species/`.
 * Fish, invertebrates, corals and algae share the format, discriminated by [kind]. Fields beyond
 * the simulation schema ([scientific], [minTankGallons], [careNotes]…) feed the livestock screens.
 * Temperatures are °C; `nh3Free` is free NH3 (total × 0.08 at pH 8.2 when the research gave total).
 */
@Serializable
data class SpeciesSheet(
    val id: String,
    val kind: SpeciesKind,
    val common: String,
    val scientific: String = "",
    val family: String = "",
    val maxLengthCm: Double,
    val typicalAdultCm: Double = maxLengthCm,
    val growthCmPerYear: Double = 0.0,
    val lifespanYears: Double = 0.0,
    val depthRatio: Double = 0.3,
    val massCoeff: Double = 0.02,
    val locomotion: Locomotion? = null,
    val zone: Zone? = null,
    /** DIURNAL, NOCTURNAL, CREPUSCULAR */
    val activity: String = "DIURNAL",
    val schooling: Boolean = false,
    val territorialRadius: Double = 0.0,
    val homeRadiusCm: Double = 30.0,
    val temperament: Temperament = Temperament(),
    val diet: List<String> = emptyList(),
    val feedsPerDay: Double = 2.0,
    val grazer: Boolean = false,
    val tolerance: Map<String, ToleranceBand> = emptyMap(),
    val diseaseSusceptibility: Map<String, Double> = emptyMap(),
    val hardiness: Double = 0.6,
    val starvationDays: Double = 21.0,
    val size: SizeInfo = SizeInfo(),
    val model: String? = null,
    val colors: Map<String, String> = emptyMap(),
    val iridescence: Double = 0.0,
    /** Species brain / behaviour id used by the simulation (e.g. "clown", "tang", "hermit"). */
    val brain: String? = null,
    /** Minimum display volume for an adult; drives the tank-size warning. */
    val minTankGallons: Double? = null,
    val careLevel: String = "Easy",
    val reefSafe: String = "Yes",
    val dietSummary: String = "",
    val temperamentSummary: String = "",
    val onePerTank: Boolean = false,
    val copperSensitive: Boolean = false,
    val careNotes: List<String> = emptyList(),
    val coral: CoralInfo? = null,
) {
    val isFish: Boolean get() = kind == SpeciesKind.FISH
    val isSessile: Boolean get() = kind == SpeciesKind.CORAL || kind == SpeciesKind.ALGAE || locomotion?.mode == "SESSILE"

    /** Group label for the livestock list sections. */
    val section: String get() = when (kind) {
        SpeciesKind.FISH -> "Fish"
        SpeciesKind.INVERT -> "Inverts"
        SpeciesKind.CORAL -> "Corals"
        SpeciesKind.ALGAE -> "Algae"
    }
}
