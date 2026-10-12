package com.reeflive.core.sim

import com.reeflive.core.geom.Vec3

/** Sandbox / UI commands (SIMULATION §1.3). Applied at the start of the next behavior tick. */
sealed class Command {
    data class SetParam(val paramId: String, val value: Double) : Command()
    data class Feed(val kind: FoodKind, val grams: Double, val at: Vec3? = null) : Command()
    data class WaterChange(val fraction: Double, val mix: WaterState = WaterState.FRESH_MIX) : Command()
    data class Dose(val paramId: String, val delta: Double) : Command()
    data class Lights(val on: Boolean?, val overrideIntensity: Double? = null) : Command()
    data class Heater(val working: Boolean, val setC: Double? = null, val stuckOn: Boolean = false) : Command()
    data class Powerheads(val on: Boolean) : Command()
    data class AddOrganism(val speciesId: String, val sizeCm: Double, val nickname: String, val role: String? = null) : Command()
    data class RemoveOrganism(val id: String) : Command()
    data class TapGlass(val worldPoint: Vec3) : Command()
    data class SetSpeed(val speed: Double) : Command()
    data class ResetToReality(val water: WaterState, val livestock: List<LivestockSeed>) : Command()
    /** Sandbox: set an organism's length (the "grow fish" slider, SIMULATION §4.5 tang). */
    data class SetSize(val id: String, val sizeCm: Double) : Command()
    /** Sandbox toggle "eel can eat tankmates" (SIMULATION §4.5). */
    data class SetPredation(val enabled: Boolean) : Command()
    /** A shadow/hand passing over the tank at [worldPoint] (feather duster retracts, fish glance). */
    data class Shadow(val worldPoint: Vec3) : Command()

    // ---- research 06 §4.2 sandbox controls for realistic change tests ----
    /**
     * Move [paramId] from its current value to [target] linearly over [overMin] sim minutes
     * (0 = a step), then keep it there against the chemistry ([hold]) until [ReleaseParam].
     * Any [SetParam] id works; "light" and "flow" ramp the light override and pump strength.
     */
    data class RampParam(val paramId: String, val target: Double, val overMin: Double = 0.0, val hold: Boolean = true) : Command()
    /** Stop ramping/holding [paramId]; the chemistry takes over again. */
    data class ReleaseParam(val paramId: String) : Command()
    /** Run [command] when the sim clock reaches [atSimSec] (absolute sim seconds). */
    data class Schedule(val atSimSec: Double, val command: Command) : Command()
    /** Powerheads off for [durationSec] from [atSimSec] (night-time pump failure scenario). */
    data class PowerheadFail(val atSimSec: Double, val durationSec: Double) : Command()
    /** Rearrange the rock: every territory re-anchors and familiarity drops to 0.5 (06 §3.3). */
    object RearrangeRock : Command()
    /** Acclimate every animal's setpoints to the current water ([paramId] null = all channels). */
    data class SetAcclimation(val paramId: String? = null) : Command()
    /** Photoperiod in hours (lights-on length, centred on the current midday). */
    data class Photoperiod(val hours: Double) : Command()
}

/** One animal or coral to place in the tank (from the livestock table or the sandbox). */
data class LivestockSeed(
    val speciesId: String,
    val nickname: String,
    val sizeCm: Double = 0.0,
    val role: String? = null,           // FEMALE / MALE
    val placement: String? = null,      // anchor name (corals, clown host, etc.)
    val id: String = "",
    val variant: String? = null,
)

enum class Disease { ICH, VELVET, HLLE, BACTERIAL }

enum class Symptom(val label: String) {
    COLOR_FADE("Faded color"),
    CLAMPED_FINS("Clamped fins"),
    RAPID_GILLS("Rapid breathing"),
    GASPING("Gasping"),
    WHITE_SPOTS("White spots"),
    LETHARGY("Lethargic"),
    HIDING("Hiding"),
    THIN("Thin"),
    FLASHING("Flashing"),
    LESIONS("Lesions"),
    MOLTING("Molting"),
    /** Research 08 heat stages. */
    ERRATIC("Darting erratically"),
    ON_BOTTOM("Resting on the bottom"),
    LOSS_OF_EQUILIBRIUM("Loss of equilibrium"),
    DETACHED("Lost its grip"),
    DEAD("Dead"),
}

enum class Mood(val label: String) { CALM("Calm"), ALERT("Alert"), HUNGRY("Hungry"), STRESSED("Stressed"), SICK("Sick"), SLEEPING("Sleeping"), DEAD("Dead") }

/** Discrete simulation events (SIMULATION §6). [simTimeSec] is when it happened. */
sealed class SimEvent {
    abstract val simTimeSec: Double
    data class Eaten(val predatorId: String, val preyId: String, override val simTimeSec: Double) : SimEvent()
    data class Died(val id: String, val cause: String, override val simTimeSec: Double) : SimEvent()
    data class DiseaseOnset(val id: String, val disease: Disease, override val simTimeSec: Double) : SimEvent()
    data class Recovered(val id: String, val disease: Disease, override val simTimeSec: Double) : SimEvent()
    data class Molted(val id: String, override val simTimeSec: Double) : SimEvent()
    data class CoralBleaching(val id: String, override val simTimeSec: Double) : SimEvent()
    data class AlgaeBloom(val level: Double, override val simTimeSec: Double) : SimEvent()
    data class Gasping(val id: String, override val simTimeSec: Double) : SimEvent()
    data class Fed(val kind: FoodKind, val grams: Double, override val simTimeSec: Double) : SimEvent()
    data class Cleaned(val cleanerId: String, val clientId: String, override val simTimeSec: Double) : SimEvent()
    /** Research 06 §5.7 events. [displayOnly] = no chase or nip (standoff, dorsal raise). */
    data class Chase(val actorId: String, val targetId: String, val bout: String, val nip: Boolean, val displayOnly: Boolean, override val simTimeSec: Double) : SimEvent()
    data class Pose(val clientId: String, val cleanerId: String, override val simTimeSec: Double) : SimEvent()
    data class Jolt(val cleanerId: String, val clientId: String, val retaliated: Boolean, override val simTimeSec: Double) : SimEvent()
    /** A lethal roll that Reality mode (predation off) turned into a scare (06 §5.3 reality rule). */
    data class NearMiss(val predatorId: String, val preyId: String, val bout: String, override val simTimeSec: Double) : SimEvent()
    data class TerritoryReset(override val simTimeSec: Double) : SimEvent()
    data class Quiver(val id: String, val toId: String, override val simTimeSec: Double) : SimEvent()
    data class LegRock(val id: String, val toId: String, override val simTimeSec: Double) : SimEvent()
}

/**
 * Per-organism render state (ARCHITECTURE §8.8). Spine = 16 × xyz world cm (head→tail);
 * frames = 16 × quaternion xyzw mapping model X(forward)/Y(up)/Z(side) to world.
 * Fields after `scale` are Track C additions with defaults (contract extension, backward compatible).
 */
data class OrganismPose(
    val id: String,
    val speciesId: String,
    val spine: FloatArray,
    val frames: FloatArray,
    val swimPhase: Float,
    val amp: Float,
    val waveLen: Float,
    val pectoral: Float,
    val finClamp: Float,
    val gill: Float,
    val gillRate: Float,
    val desat: Float,
    val ich: Float,
    val thin: Float,
    val eyeCloud: Float,
    val alive: Boolean,
    val scale: Float,
    /** Behaviour archetype name (BrainKind) so the renderer can pick placeholder geometry. */
    val brain: String = "",
    /** 0 swim, 1 perched, 2 hanging, 3 in hole/den, 4 cocoon, 5 buried, 6 retracted, 7 flipped. */
    val posture: Int = 0,
    /** Eye swivel (radians) and mouth/jaw open (0..1). */
    val eyeYaw: Float = 0f,
    val mouth: Float = 0f,
    /** Appendage animation: antenna sweep phase / crown or arm extension (0..1). */
    val appendagePhase: Float = 0f,
    val extension: Float = 1f,
    /** False while hidden (molting deep in rock, fully buried). */
    val visible: Boolean = true,
    val nickname: String = "",
    /** Model variant from the livestock row (e.g. "darwin_black" clown); the renderer picks `variantModels`. */
    val variant: String? = null,
) {
    fun headPos() = Vec3(spine[0].toDouble(), spine[1].toDouble(), spine[2].toDouble())
    fun spinePoint(i: Int) = Vec3(spine[i * 3].toDouble(), spine[i * 3 + 1].toDouble(), spine[i * 3 + 2].toDouble())
    override fun equals(other: Any?) = this === other
    override fun hashCode() = id.hashCode()
}

data class CoralPose(
    val id: String,
    val extension: Float,
    val bleach: Float,
    val brown: Float,
    val algae: Float,
    val swayPhase: Float,
    /** Track C additions. */
    val speciesId: String = "",
    val anchor: String = "",
    val flow: Float = 0f,
    val tissue: Float = 1f,
    /** Bare skeleton exposed by acute heat necrosis (research 08 §5), 0..1. */
    val skeleton: Float = 0f,
)

/** Everything the renderer and HUD need for one frame (ARCHITECTURE §8.8). */
data class SimSnapshot(
    val simTimeSec: Double,
    val lights: FloatArray,
    val water: WaterState,
    val organisms: List<OrganismPose>,
    val corals: List<CoralPose>,
    val food: FloatArray,
    val foodKinds: ByteArray,
    val algaeBloom: Float,
    val events: List<SimEvent>,
    /** Track C additions for the HUD. */
    val speed: Double = 1.0,
    val lightIntensity: Float = 0f,
    val stats: List<OrganismStats> = emptyList(),
    val tick: Long = 0,
    val abstractMode: Boolean = false,
    /** Shed shrimp exoskeletons lying on the sand (24 h each). */
    val moltShells: List<Vec3> = emptyList(),
    /** Nori sheet remaining on the clip (0 = none). */
    val noriRemaining: Float = 0f,
    /** Goby sand piles: x, z, radius, height (cm) per pile. */
    val sandPiles: FloatArray = FloatArray(0),
    /** Sandbox pump strength (0..2). */
    val flowStrength: Float = 1f,
    /** Water-surface heightfield (cm, sx × sz, x fastest) and its dims (SIMULATION §9.7). */
    val surfaceHeights: FloatArray = FloatArray(0),
    val surfaceDims: IntArray = IntArray(2),
    /** Flow solver velocities (cell centres) when the Navier–Stokes flow is on; refreshed ~4×/s. */
    val flowGrid: com.reeflive.core.geom.FlowGrid? = null,
) {
    override fun equals(other: Any?) = this === other
    override fun hashCode() = tick.hashCode()

    companion object {
        val EMPTY = SimSnapshot(0.0, FloatArray(8), WaterState(), emptyList(), emptyList(), FloatArray(0), ByteArray(0), 0f, emptyList())
    }
}

/** Live stats for the fish card (DESIGN §5.6). */
data class OrganismStats(
    val id: String,
    val nickname: String,
    val speciesId: String,
    val common: String,
    val health: Float,
    val hunger: Float,
    val stress: Float,
    val mood: Mood,
    val behavior: String,
    val symptoms: List<Symptom>,
    val disease: Disease?,
    val diseaseStage: Float,
    val alive: Boolean,
    val lengthCm: Float,
    val temperamentTags: List<String>,
    /** Follow-mode caption, e.g. "Falco hawkfish eyeing the skunk cleaner shrimp" (Track C). */
    val caption: String = "",
    val focusId: String? = null,
    /** Per-parameter stress parts for the Advanced inspector (keys: HealthModel.label). */
    val envStress: Map<String, Double> = emptyMap(),
    val cause: String? = null,
    /** Current intent without the name, e.g. "eyeing the skunk cleaner shrimp" (Track C). */
    val intent: String = "",
    /** Animal the intent is about (focusId, or the nearest likely target). */
    val intentTargetId: String? = null,
    /** Social memory, most salient first (at most 5): who it fears, chases, is chased by, knows. */
    val relations: List<RelationInfo> = emptyList(),
    /** The animal it is currently keeping clear of, if any. */
    val waryOfId: String? = null,
)

/**
 * One remembered relationship for the FishCard and follow mode (ai/Social.kt). Values are 0..1
 * except [cleanings] (count). [label] is a short human phrase: "Afraid of", "Chased by", "Chases",
 * "Dominates", "Cleaning partner", "Knows".
 */
data class RelationInfo(
    val otherId: String,
    val otherName: String,
    val otherCommon: String,
    val label: String,
    val familiarity: Float,
    val fear: Float,
    val harassedBy: Float,
    val harassed: Float,
    val dominance: Float,
    val cleanings: Int,
    val secondsSinceSeen: Float,
    /** Territory intrusions by the other (24 h half-life) and, toward a cleaner, the client's trust (06 §5.1). */
    val intrusions: Float = 0f,
    val cleanerTrust: Float = 0.7f,
)

/** A mound of sand dug out by a burrowing goby (cone: volume = π r² h / 3, h ≈ 0.45 r). */
class SandPile(val pos: com.reeflive.core.geom.Vec3, var volume: Double) {
    val radius: Double get() = Math.cbrt(volume * 3 / (Math.PI * 0.45))
    val height: Double get() = 0.45 * radius
}
