package com.reeflive.core.sim

import com.reeflive.core.geom.Vec3

/** Sandbox / UI commands (SIMULATION §1.3). Applied at the start of the next behavior tick. */
sealed class Command {
    data class SetParam(val paramId: String, val value: Double) : Command()
    /**
     * Drop [grams] of [kind] at [at] (world cm) or the feeding ring; [FoodKind.typicalDoseGrams] is one tap of the button.
     * With [target], [at] is what the keeper aimed at (a fish, a coral, a spot on the rock or sand) and the food goes in
     * the way a keeper target-feeds it there ([Simulation.targetRelease]).
     */
    data class Feed(val kind: FoodKind, val grams: Double, val at: Vec3? = null, val target: Boolean = false) : Command()
    /** Tank Health "Feed the hungry ones": each hungry animal gets its favourite food dropped above it ([HungryFeed]). */
    data object FeedHungry : Command()
    data class WaterChange(val fraction: Double, val mix: WaterState = WaterState.FRESH_MIX) : Command()
    data class Dose(val paramId: String, val delta: Double) : Command()
    /** Tank Health's "Care for corals and inverts": doses and feeds what [CoralCare.plan] lists for the tank now. */
    object ReefCare : Command()
    data class Lights(val on: Boolean?, val overrideIntensity: Double? = null) : Command()
    data class Heater(val working: Boolean, val setC: Double? = null, val stuckOn: Boolean = false) : Command()
    data class Powerheads(val on: Boolean) : Command()
    data class AddOrganism(val speciesId: String, val sizeCm: Double, val nickname: String, val role: String? = null, val ageYears: Double? = null) : Command()
    data class RemoveOrganism(val id: String) : Command()
    /** Takes the coral colony [id] out (My tank: sold to a fish store). */
    data class RemoveCoral(val id: String) : Command()
    /** The keeper names an animal (My tank: the name is saved with it). Blank names are ignored. */
    data class Rename(val id: String, val nickname: String) : Command()
    data class TapGlass(val worldPoint: Vec3) : Command()
    /**
     * Submarine mode ([Submarine]): launch ([on]) or park the toy sub; [visible] false hides it from the animals
     * (they ignore it entirely; it still cannot pass through glass, sand or rock); [headlight] is its lamp.
     */
    data class SubmarineMode(val on: Boolean, val visible: Boolean = true, val headlight: Boolean = true) : Command()
    /** The pilot's stick for the sub; it holds until the next one, so hosts send it only when the input changes. */
    data class SteerSubmarine(val controls: SubControls) : Command()
    /**
     * Watch an animal from the sub ([Submarine.watch]): the autopilot glides over and keeps it company at a polite
     * distance in [mode]; the stick then moves the sub round the fish. [id] null = back to free steering.
     */
    data class SubmarineWatch(val id: String?, val mode: SubWatch = SubWatch.HOVER) : Command()
    /**
     * The sub's ‹ › buttons: watch the next ([step] > 0) or previous living fish after the one being watched, in tank
     * order; with none watched, the fish nearest the sub. Keeps the current mode. Invertebrates and corals are skipped.
     */
    data class SubmarineWatchNext(val step: Int = 1) : Command()
    /**
     * A gentle fingertip tap on the tank at [point] (world cm): the front glass or water, the sand, the rock or a coral
     * ([TankTouch]). Nearby animals react by species and personality, get used to it when nothing follows, and a quick
     * run of taps in one spot becomes a [TapGlass] knock. [strength] 0..1 scales the reach (1 = an ordinary tap).
     */
    data class TouchTank(val point: Vec3, val surface: TouchSurface, val strength: Double = 1.0) : Command()
    /**
     * Dynamic rockscape ([RockPhysics]): rocks can settle or topple when the sand under them is dug out. Off = static
     * rock (no physics at all). Rocks that already moved stay where they came to rest either way.
     */
    data class DynamicRocks(val on: Boolean) : Command()
    /**
     * Sandbox "Rockslide": the sand is dug out from under one side of a rock that stands on sand (as a burrowing goby
     * would, all at once), so with dynamic rocks on it settles or topples. [rock] picks it by name (null: any).
     */
    data class UndermineRock(val rock: String? = null) : Command()
    /**
     * Aquascape builder: drops a white dry rock of [shape] over (x, z), turned [yawDeg] about the vertical; it falls and
     * settles on what is under it. [seed] varies the rock (−1: a new one each time).
     */
    data class PlaceRock(val shape: DryRockShape, val x: Double, val z: Double, val yawDeg: Double = 0.0, val seed: Int = -1) : Command()
    /** Aquascape builder: takes out the rock nearest [at] (null: the dry rock added last). */
    data class RemoveRock(val at: com.reeflive.core.geom.Vec3? = null) : Command()
    /** Aquascape builder: empties the tank of rock, to build a scape from scratch. */
    data object ClearRocks : Command()
    /**
     * A finger held on the glass (or the water surface) after a long press, at [point] (world cm), moving at [speedCmS]
     * along the pane ([TankTouch] finger rules): food-conditioned bold fish come and follow it, nipping at the glass when
     * it rests; a fast-moving finger makes them hang back. [point] null = the finger lifted.
     */
    data class Finger(val point: Vec3?, val speedCmS: Double = 0.0, val surface: TouchSurface = TouchSurface.GLASS) : Command()
    /**
     * A flick of the finger in the water: a gentle puff at [point] moving at [velocity] (cm/s, capped at
     * [com.reeflive.core.geom.FlowGust.MAX_SPEED]) that sways corals, the nori and sand grains, retracts polyps it hits
     * and makes shy animals in its path flinch ([TankTouch.puffReaction]).
     */
    data class WaterPuff(val point: Vec3, val velocity: Vec3) : Command()
    /** A tap right on an animal ([id]): snails, crabs and hermits tuck in, shrimp freeze, dusters snap shut; fish ignore it. */
    data class TouchAnimal(val id: String) : Command()
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
     * surface, inverts start at their usual spots, corals settle on free rock or sand. [sizeCm] > 0 sets the length outright;
     * otherwise [size] picks it (juveniles by default, as shops sell them).
     */
    data class AddMany(val speciesId: String, val count: Int, val sizeCm: Double = 0.0, val size: StockSize = StockSize.JUVENILE) : Command()

    /**
     * Sandbox "Add life" the way a keeper does it: [count] of [speciesId] arrive in sealed bags dropped onto the water
     * at tank x [x] (and front-to-back [z], null = the middle). They float for 30 sim minutes, then tip and the
     * animals come out ([AcclimationBags]).
     */
    data class FloatBag(
        val speciesId: String, val count: Int, val x: Double, val z: Double? = null,
        val sizeCm: Double = 0.0, val size: StockSize = StockSize.JUVENILE,
    ) : Command()
    /**
     * Take the dead animal [id] out with the fish net ([FishNet]): it dips in, scoops the body and lifts it out, then
     * the body leaves the sim. Ignored for a living animal; more taps queue up.
     */
    data class NetOut(val id: String) : Command()
    /** Fast-forward while bags float ([on]) or back to the chosen speed; [openNow] tips every floating bag at once. */
    data class HurryBags(val on: Boolean, val openNow: Boolean = false) : Command()
    /** Put the coral waiting for a spot on suggestion [spot] of [SimSnapshot.coralPick] (−1: the best one). */
    data class PlaceCoral(val spot: Int = -1) : Command()

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
    /**
     * Add-pest: [count] aiptasia on the aiptasia anchors (then as lacerates). With [nearCoralId] it is a hitchhiker
     * that came in on that new frag (My tank stores): one small polyp on the rock beside it.
     */
    data class AddAiptasia(val count: Int = 1, val nearCoralId: String? = null) : Command()
    /** Kill one aiptasia ([polypId], null = the biggest) with a keeper's product; a closed polyp often escapes. */
    data class TreatAiptasia(val treatment: AiptasiaTreatment, val polypId: String? = null) : Command()
    /** Remove up to [count] aiptasia, biggest first: injected ([chemical]) or scraped; both can leave fragments. */
    data class RemoveAiptasia(val count: Int = 1, val chemical: Boolean = true) : Command()
    /**
     * Pull the bubble algae off the rock (research 09 A2, A5): lifted out whole ([careful]) or squeezed off, when some
     * cells burst and seed new ones nearby ([AlgaeModel.removeBubble]).
     */
    data class RemoveBubbleAlgae(val careful: Boolean = true) : Command()
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
    /** A microfauna action (research 10): dose pods, phyto or rotifers, seed live rock, treat flatworms; [amount] scales the bottle. */
    data class Micro(val action: MicroAction, val amount: Double = 1.0) : Command()
    /** A bacteria-layer action (research 18): bottled nitrifiers, carbon dosing, antibiotic, UV, stir the sand, probiotic. */
    data class Bacteria(val action: BacteriaAction, val amount: Double = 1.0) : Command()
    /** A breeding / nursery action ([Brood.Act] name: raise the babies, feed rotifers, move a baby to the tank…). */
    data class Breed(val act: String) : Command()
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

/** Routine maintenance jobs (research 01 §3.5, §3.4): EMPTY_SKIMMER tips out the collection cup, TOP_OFF adds RO/DI for what evaporated,
 * RINSE_FILTER rinses the HOB floss, SIPHON_SAND gravel-vacs fish waste and mulm (research 18_fish_waste). */
enum class MaintenanceTask { CHANGE_SOCK, REPLACE_CARBON, HARVEST_MACRO, EMPTY_SKIMMER, TOP_OFF, RINSE_FILTER, SIPHON_SAND }

/**
 * Equipment and upkeep at a glance ([WaterSim.careState]): the skimmer cup's fill (0..1), the sock's age, how much
 * life the carbon has left (1 fresh .. 0 spent), the heater, and how full the tank is against its system volume
 * (evaporation lowers it until a top-off). The tank check (core care TankDoctor) and the renderer read it.
 */
data class CareState(
    val skimmerOn: Boolean = true,
    val skimmerCup: Float = 0.3f,
    val sockOn: Boolean = false,
    val sockAgeH: Float = 0f,
    val carbonOn: Boolean = false,
    val carbonLeft: Float = 0f,
    val heaterWorking: Boolean = true,
    val heaterStuck: Boolean = false,
    val heaterSetC: Float = 25.5f,
    val waterFull: Float = 1f,
    val ato: Boolean = false,
    /** A mesh lid on the open top: jumpers bounce back in ([JumpModel]). */
    val lid: Boolean = false,
) {
    /** How dirty the sock looks: clean when new, brown after about three days in. */
    val sockDirt: Float get() = (sockAgeH / 72f).coerceIn(0f, 1f)
}

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
    /** A pistol shrimp's claw snap (a cavitation crack, research 44 §4), at fish [toId] if any. */
    data class Snap(val id: String, val toId: String?, override val simTimeSec: Double) : SimEvent()
    /** A new colony appeared from [parentId]: a dropped Kenya branch (03 A1) or an aiptasia lacerate (03 C2). */
    data class CoralSpawned(val parentId: String, val id: String, override val simTimeSec: Double) : SimEvent()
    /** A colony's tissue is gone ([CoralHealth]); [cause] is plain words for its main stress at the end. */
    data class CoralDied(val id: String, val name: String, val cause: String, override val simTimeSec: Double) : SimEvent()
    /** The keeper dosed aiptasia [polypId] with [treatment]: [killed] false = it snapped shut into the rock in time. */
    data class AiptasiaTreated(val polypId: String, val treatment: AiptasiaTreatment, val killed: Boolean, override val simTimeSec: Double) : SimEvent()
    /** Breeding ([Brood], research 28): a clutch laid, larvae hatched (and whether the keeper is catching them), the nursery's larvae became fry, a baby moved to the display. */
    data class EggsLaid(val spec: String, val eggs: Int, override val simTimeSec: Double) : SimEvent()
    data class Hatched(val spec: String, val larvae: Int, val raising: Boolean, override val simTimeSec: Double) : SimEvent()
    data class Metamorphosed(val spec: String, val fry: Int, override val simTimeSec: Double) : SimEvent()
    data class BabyReleased(val id: String, override val simTimeSec: Double) : SimEvent()
    /**
     * A loose rock settled or toppled after the sand under it was dug out (dynamic rockscape, [RockPhysics]): [rock] is
     * its name in the scape (R0…R10, glued rocks joined by '+'), moved [moveCm] and turned [turnDeg].
     */
    data class RockShifted(val rock: String, val moveCm: Double, val turnDeg: Double, override val simTimeSec: Double) : SimEvent()
    /** The aquascape builder added ([added]) or took out a rock (save the tank's rock layout). */
    data class RockscapeChanged(val rock: String, val added: Boolean, override val simTimeSec: Double) : SimEvent()
    /** A fish leapt at the surface ([JumpModel]): [what] it did (leapt, bumped the lid, went over the rim). */
    data class Jumped(val id: String, val what: Jump, override val simTimeSec: Double) : SimEvent()
    /** A pest or critter rode in uninvited ([Hitchhikers]): [kind] its id, [label] how to say it ("A bristleworm"). */
    data class Hitchhiker(val kind: String, val label: String, override val simTimeSec: Double) : SimEvent()
    /** The bristleworms are booming ([count] in the tank, far past a lean tank's): too much food is reaching the bottom. */
    data class WormBoom(val count: Int, override val simTimeSec: Double) : SimEvent()
}

/**
 * Per-organism render state (ARCHITECTURE §8.8). Spine = 16 × xyz world cm (head→tail);
 * frames = 16 × quaternion xyzw mapping model X(forward)/Y(up)/Z(side) to world.
 * Fields after `scale` are Track C additions with defaults (contract extension, backward compatible).
 */
/** Shared empty [OrganismPose.limbs]. */
val NO_LIMBS = FloatArray(0)

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
    /** Pectoral stroke (brittle star: arm row) phase in cycles, wrapping at 1000, and stroke size 0..1 (SIMULATION §15). */
    val pecPhase: Float = 0f,
    val pecAmp: Float = 0f,
    /** Signed escape flexion −1..1: fish C-start bend (lateral), shrimp tail-flip (ventral abdomen). */
    val bend: Float = 0f,
    /** How the renderer applies [pecPhase]/[bend]: 0 fish, 1 brittle-star arm rowing, 2 shrimp (ventral flexion), 3 crab legs. */
    val motionMode: Int = 0,
    /**
     * Per-limb pose from the organism's [LimbRig] (SIMULATION §19), vec4 per entry, empty for animals without one:
     * [LimbRig.LIMBS] entries for the brittle star arms and the crab's legs and chelae (with model pivots the renderer
     * centres the model on the head station and poses each limb from these); one entry for the shrimp
     * ([ShrimpLimbs]: stride phase, stepping, rock / claw-display signal, grooming).
     */
    val limbs: FloatArray = NO_LIMBS,
    /** Still in an acclimation bag ([AcclimationBags]): drawn, but not pickable and not yet in the tank. */
    val bagged: Boolean = false,
    /** Courtship flash 0..1 ([Organism.display]): fins flared and colours brightened. */
    val display: Float = 0f,
    /** Mucus cocoon 0..1 around a sleeping wrasse ([Organism.cocoon]). */
    val cocoon: Float = 0f,
    /** Firefish dorsal flick (research 41 §1.2): 0 = the long first dorsal raised as built, 1 = folded down along the back. */
    val finFold: Float = 0f,
    /** Skin shade −1 pale .. +1 dark and mottled (a fish's own colour change, [Organism.skinTone]). */
    val tone: Float = 0f,
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
    /** The rock piece the colony grows on ([SimSnapshot.rockPoses] index), −1 none: drawn moved with that rock. */
    val rockPiece: Int = -1,
    /** Where the colony was placed, in the frame of the rock it grows on (that rock as built), and its surface normal. */
    val restX: Float = 0f,
    val restY: Float = 0f,
    val restZ: Float = 0f,
    val restNx: Float = 0f,
    val restNy: Float = 1f,
    val restNz: Float = 0f,
    /** Encrusting mat (GSP): how far the mat reaches along the rock from [restX]/[restY]/[restZ], cm; 0 for other kinds. */
    val matRadius: Float = 0f,
    /**
     * GSP touches closing the polyps around them ([GspPolyps]): per touch dx, dy, dz from the colony's current
     * position (world axes), first and last touch (sim s), strength; null when none.
     */
    val pokes: FloatArray? = null,
    /** Overall stress 0..1 and the main reason ([CoralNeed] ordinal, −1 none), for the Tank Health card. */
    val stress: Float = 0f,
    val need: Int = -1,
    /** Recent caught food 0..1 ([Coral.fed]): the care button feeds the corals when it runs low. */
    val fed: Float = 0f,
    /** Size the renderer draws the colony at about its home spot (grown or shrunk; 1 = as modelled). */
    val drawScale: Float = 1f,
    /** Stony corals: night tentacles out 0..1 (Euphyllia sweepers, LPS feeding tentacles); renderer `uPolypDrive`. */
    val tentacles: Float = 0f,
    /** Linear RGB its tissue fades to as it bleaches ([CoralProfile.tint]); null = white. Shared, never copied. */
    val bleachTint: FloatArray? = null,
    /** The colony's name (the keeper's, else its species'). */
    val name: String = "",
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
    /** Life stage and sex per fish id (LifeStages.kt), for the renderer's stage textures and the fish card. */
    val lifeForms: Map<String, LifeForm> = emptyMap(),
    /** Shed shrimp exoskeletons lying on the sand (24 h each). */
    val moltShells: List<Vec3> = emptyList(),
    /** Nori sheet remaining on the clip (0 = none). */
    val noriRemaining: Float = 0f,
    /** Goby sand piles: x, z, radius, height (cm) per pile. */
    val sandPiles: FloatArray = FloatArray(0),
    /** Sandbox pump strength (0..2). */
    val flowStrength: Float = 1f,
    /** A tank started from scratch (My tank, [Simulation.bareScape]): the renderer leaves out the owner's static colonies. */
    val bareScape: Boolean = false,
    /** The tank's box and look ([Simulation.shape]): the renderer draws the glass, equipment and camera to it. */
    val shape: TankShape = TankShape.STOCK,
    /** Water-surface heightfield (cm, sx × sz, x fastest) and its dims (SIMULATION §9.7). */
    val surfaceHeights: FloatArray = FloatArray(0),
    val surfaceDims: IntArray = IntArray(2),
    /** Flow solver velocities (cell centres) when the Navier–Stokes flow is on; refreshed ~4×/s. */
    val flowGrid: com.reeflive.core.geom.FlowGrid? = null,
    /** Per-surface cover of every algae group for the A6 visual cues (research 09); headline covers are on [water]. */
    val algae: AlgaeCover = AlgaeCover.EMPTY,
    /** Egg clutches on the rock (clown nest, coverage B-1); empty when none. */
    val eggClutches: List<EggClutchPose> = emptyList(),
    /** Pods, plankton and the other microfauna (research 10) for the microscope view and its readout. */
    val microfauna: MicrofaunaState = MicrofaunaState.EMPTY,
    /** The bristleworms the sim has out (individually simulated, catchable); the renderer draws these instead of its own. */
    val worms: List<CritterPose> = emptyList(),
    /** Bacterial guilds (research 18) for the Bacteria card: levels, trends and what each is doing to the tank. */
    val bacteria: BacteriaState = BacteriaState.EMPTY,
    /** The toy submarine while Submarine mode is on (null = off). */
    val submarine: SubPose? = null,
    /** Dynamic sand bed grid (SIMULATION §16); refreshed ≤ 4×/s and shared until it changes. Null = flat, untouched sand. */
    val sandGrid: SandGridSnapshot? = null,
    /** Suspended sand / detritus particles: x, y, z (cm) and `Grain.ordinal + age fraction` per particle. */
    val sandClouds: FloatArray = FloatArray(0),
    /**
     * Per [food] particle (same order), [com.reeflive.core.sim.FoodSystem.META_STRIDE] floats: seconds in the water,
     * settled (1/0) and a stable 0..1 seed. Renderer only (thawing cubes, fluttering flakes, spreading plankton haze).
     */
    val foodMeta: FloatArray = FloatArray(0),
    /** Fish feces pellets, [WasteSystem.STRIDE] floats each: x, y, z, length (cm), tint, settled (0 / 1 + breakdown), seed, tumble. */
    val waste: FloatArray = FloatArray(0),
    /**
     * Water movers for the renderer, [PUMP_STRIDE] floats each: nozzle x, y, z (cm), unit jet axis x, y, z, the jet's
     * current output (0 = off, 1 = as installed, up to 2 with the sandbox flow slider) and its exit speed (cm/s).
     */
    val pumps: FloatArray = FloatArray(0),
    /** Berghia nudibranchs in the tank (fractional population) and the aiptasia polyp they are eating now. */
    val berghia: Float = 0f,
    val berghiaPrey: String? = null,
    /** The nori sheet on its clip as a cloth (node positions and material per cell), null when there is none. */
    val nori: NoriPose? = null,
    /** Live touch puffs in the water, [com.reeflive.core.geom.FlowGust.STRIDE] floats each (coral sway, burst specks). */
    val gusts: FloatArray = FloatArray(0),
    /** Acclimation bags floating in the tank (sandbox Add life) and whether the clock is hurried for them. */
    val bags: List<BagPose> = emptyList(),
    val bagHurry: Boolean = false,
    /** Equipment upkeep (skimmer cup, sock, carbon, heater, water level) for the tank check and the renderer. */
    val care: CareState = CareState(),
    /**
     * Rest → world 4×4 matrix (column-major) per rock piece ([RockData.pieces]) once any rock has moved, else null
     * (every rock where it was built). Shared between snapshots until a rock moves again ([rockVersion]).
     */
    val rockPoses: FloatArray? = null,
    val rockVersion: Long = 0,
    /** The fish net while it fetches a dead animal ([FishNet]), and the last body it took out (for the goodbye). */
    val net: NetPose? = null,
    val netFlush: NetFlush? = null,
    /** Larvae and fry ([Brood]): counts for the renderer, the parents' card panel for the UI. */
    val brood: BroodView = BroodView.EMPTY,
    /** Dry rocks added with the aquascape builder (drawn from their shape at [rockPoses]), or null for none. */
    val builtRocks: List<BuiltRock>? = null,
    /** The live rock field once rocks have moved or been added / taken out (taps and the camera hit rocks where they are now). */
    val rockSdf: com.reeflive.core.geom.Sdf? = null,
    /** Sim time of the last care-button feeding ([CoralCare]); far past when never. */
    val reefCareAt: Double = -1e12,
    /** My tank: the coral out of its bag waiting for the keeper to pick a spot, with its best spots ([CoralPicks]). */
    val coralPick: CoralPickPose? = null,
) {
    override fun equals(other: Any?) = this === other
    override fun hashCode() = tick.hashCode()

    companion object {
        val EMPTY = SimSnapshot(0.0, FloatArray(8), WaterState(), emptyList(), emptyList(), FloatArray(0), ByteArray(0), 0f, emptyList())
        /** Floats per pump in [pumps]. */
        const val PUMP_STRIDE = 8
    }
}

/**
 * The nori cloth for the renderer ([NoriCloth]): [cols] × [rows] node positions (xyz, row-major from the clip edge)
 * and the material left in each of the (cols − 1) × (rows − 1) cells (0 = gone), plus the clip and the pane normal.
 */
class NoriPose(
    val cols: Int,
    val rows: Int,
    val nodes: FloatArray,
    val cells: FloatArray,
    val clipX: Float,
    val clipY: Float,
    val clipZ: Float,
    val inX: Float,
    val inY: Float,
    val inZ: Float,
)

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
    /** "What is it thinking?" readout (ai/Thoughts.kt); [Thought.NONE] when the readout is off. */
    val thought: Thought = Thought.NONE,
)

/**
 * An animal's plan in plain English (ai/Thoughts.kt, SIMULATION §4.10), for the fish card, follow mode and the thought
 * bubbles. Every line is a finished, kid-friendly sentence; nothing here feeds back into the sim.
 */
data class Thought(
    /** The current plan as a sentence: "Nemo is heading home to the hairy mushrooms to rest." */
    val now: String = "",
    /** The runner-up or time-of-day plan: "Next: graze the nori clip." ("" when there is none). */
    val next: String = "",
    /** "Feeling: a bit stressed (water is warm)." */
    val feeling: String = "",
    /** One mood word or two: happy, relaxed, hungry, curious, busy, feisty, scared, sleepy, stressed, unwell, too hot. */
    val mood: String = "",
    /** The top driver in a few words: "it is hungry", "Falco the hawkfish is close". */
    val why: String = "",
    /** A short first-person caption for a speech bubble: "Yum, mysis!", "Eek! Hide!", "Zzz…". */
    val bubble: String = "",
    /** Stable id of the current plan (behaviour + target): the UI holds a line until this changes. */
    val planKey: String = "",
    /** 0 (asleep, hidden) .. 3 (eating, fleeing, cleaning, hunting): which animals get a bubble first. */
    val interest: Int = 0,
) {
    companion object { val NONE = Thought() }
}

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
    val eggs: Int = 300,
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
