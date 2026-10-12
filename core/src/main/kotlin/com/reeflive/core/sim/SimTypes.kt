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
    data class AddOrganism(val speciesId: String, val sizeCm: Double, val nickname: String, val role: String? = null, val ageYears: Double? = null) : Command()
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
    /** Sandbox: drop [count] spare empty shells for the hermits ([sizeCm] 0 = sized for the largest hermit, with room to grow). */
    data class AddSpareShells(val count: Int = 1, val sizeCm: Double = 0.0) : Command()

    /**
     * Sandbox stocking test: add [count] of [speciesId] at once ([Stocking]). Fish are released spread along the
     * surface, inverts start at their usual spots, corals settle on free rock or sand. [sizeCm] 0 = typical adult.
     */
    data class AddMany(val speciesId: String, val count: Int, val sizeCm: Double = 0.0) : Command()

    /**
     * The owner slides the Flipper magnet scraper over [pane] from (x0, y0) to (x1, y1), in cm along the pane (x from
     * the tank's left end, y from the tank floor); the [widthCm] × [heightCm] pad clears the film it sweeps
     * ([AlgaeModel.scrapeGlass]). Allowed in Reality too: it is what the owner does to the real glass.
     */
    data class ScrapeGlass(
        val pane: GlassPane, val x0: Double, val y0: Double, val x1: Double, val y1: Double,
        val widthCm: Double = 6.0, val heightCm: Double = widthCm,
    ) : Command()

    // ---- equipment and maintenance (coverage gap W-6). Equipment toggles are SetParam ids:
    //      "skimmer", "sock", "carbon", "refugium", "uv" (1 = on, 0 = off). ----
    /** One-off maintenance: change the filter sock, replace the carbon, harvest the macroalgae. */
    data class Maintain(val task: MaintenanceTask) : Command()

    // ---- pests (coverage gap C-4) ----
    /** Sandbox add-pest: [count] aiptasia on the aiptasia anchors (then as lacerates). */
    data class AddAiptasia(val count: Int = 1) : Command()
    /** Remove up to [count] aiptasia, biggest first: injected ([chemical]) or scraped; both can leave fragments. */
    data class RemoveAiptasia(val count: Int = 1, val chemical: Boolean = true) : Command()
    /** Add aiptasia controllers (peppermint shrimp, Berghia nudibranchs). */
    data class AddPestControl(val controller: PestController, val count: Int = 1) : Command()

    // ---- disease treatments (coverage gap W-4) ----
    /**
     * Start a treatment for [days] (null = the kind's default; UV with 0 runs until stopped). [fishIds] limits
     * tank transfer / baths to those fish (null = every fish). Hyposalinity and copper change the display water.
     */
    data class StartTreatment(val kind: TreatmentKind, val days: Double? = null, val fishIds: List<String>? = null) : Command()
    data class StopTreatment(val kind: TreatmentKind) : Command()
    /** Sandbox: infect fish [id] (null = a random fish) with [disease] at [stage]. */
    data class Infect(val id: String?, val disease: Disease, val stage: Double = 0.05) : Command()
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
    /** Starting age in years; null = derived from size (LifeCycle.initialAgeSec). */
    val ageYears: Double? = null,
    /** Corals: where this colony sits relative to its [placement] anchor (a sandbox copy of an anchored model). */
    val offset: Vec3? = null,
)

/** Routine maintenance jobs (research 01 §3.5). */
enum class MaintenanceTask { CHANGE_SOCK, REPLACE_CARBON, HARVEST_MACRO }

/**
 * Fish diseases (research 01 §4.3). BROOKLYNELLA, URONEMA and FLUKES were added for the treatment model (W-4);
 * code that switches over this enum must handle them.
 */
enum class Disease { ICH, VELVET, HLLE, BACTERIAL, BROOKLYNELLA, URONEMA, FLUKES }

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
    /** Brooklynella: heavy mucus coat (01 §4.3). */
    MUCUS("Heavy mucus"),
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
    /** A new colony appeared from [parentId]: a dropped Kenya branch (03 A1) or an aiptasia lacerate (03 C2). */
    data class CoralSpawned(val parentId: String, val id: String, override val simTimeSec: Double) : SimEvent()
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
    /** Cardinal male with eggs in his mouth: draw the throat distended (coverage B-2). */
    val mouthbrooding: Boolean = false,
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
    /**
     * Sandbox copies ([Command.AddMany]): the anchored model is drawn moved by this offset (cm), keeping only the
     * parts near its home anchor at [homeX]/[homeY]/[homeZ]. All zero for the tank's own colonies.
     */
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val offsetZ: Float = 0f,
    val homeX: Float = 0f,
    val homeY: Float = 0f,
    val homeZ: Float = 0f,
    /**
     * Colony growth (CoralGrowth, research 03 C2): linear size relative to the placed colony (renderer instance
     * scale; 1 = as modelled) and, for encrusting mats, the share of their reachable rock now covered (polyp density).
     */
    val size: Float = 1f,
    val cover: Float = 0f,
) {
    val isCopy: Boolean get() = offsetX != 0f || offsetY != 0f || offsetZ != 0f
}

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
    /** Light-schedule phase for the HUD clock: Night, Dawn, Day or Dusk. */
    val dayPhase: String = "Day",
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
    /** Per-surface cover of every algae group for the A6 visual cues (research 09); headline covers are on [water]. */
    val algae: AlgaeCover = AlgaeCover.EMPTY,
    /** Egg clutches on the rock (clown nest, coverage B-1); empty when none. */
    val eggClutches: List<EggClutchPose> = emptyList(),
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
    /** "Female", "Male", "Changing sex" or "" (coverage B-1/B-3). */
    val sex: String = "",
    /** Carrying eggs in the mouth (cardinal male; he does not eat meanwhile, coverage B-2). */
    val mouthbrooding: Boolean = false,
    /** Chronological age in years (LifeCycle). */
    val ageYears: Float = 0f,
)

/**
 * An egg clutch for the renderer (coverage B-1): world position and the rock normal it sits on,
 * age in days against [hatchDays] (eggs darken and show eyes as [ageDays] nears it), the tending male.
 */
data class EggClutchPose(
    val id: String,
    val kind: String,
    val pos: Vec3,
    val normal: Vec3 = Vec3.Y,
    val ageDays: Float = 0f,
    val hatchDays: Float = 9f,
    val tenderId: String? = null,
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
