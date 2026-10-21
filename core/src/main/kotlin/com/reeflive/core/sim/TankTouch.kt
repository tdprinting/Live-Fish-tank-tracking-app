package com.reeflive.core.sim

import com.reeflive.core.geom.Vec3
import kotlin.math.exp
import kotlin.math.max

/** What a fingertip tap on the tank landed on (the renderer's hit test, [Command.TouchTank]); SURFACE = the water's top. */
enum class TouchSurface { GLASS, SAND, ROCK, CORAL, SURFACE }

/**
 * How one animal answers a gentle tap ([TankTouch.reaction]), shown by the shared `TouchReact` behaviour:
 * - [INVESTIGATE]: swims over and hovers at the glass facing the spot, expecting food.
 * - [AVOID]: turns away and drifts to cover.
 * - [PEEK]: a blenny pops further out of its hole (or stops where it is) and watches the spot.
 * - [FREEZE]: a shrimp or crab stops still and draws its antennae in for a few seconds.
 * - [CHECK]: a sand-sifter or nassarius comes over to see what stirred the sand.
 * - [DUCK]: hides at once (the species' own startle: shrimp tail-flip, hermit retreat, blenny darts home, duster crown).
 * - [FOLLOW]: tracks a finger held on the glass, hovering at it and nipping the glass when it rests ([Command.Finger]).
 * - [RISE]: comes up under a tap on the water surface, mouth working, expecting food.
 * - [TUCK]: a snail, crab or other slow invert tapped right on the body pulls in where it is for a few seconds.
 */
enum class TouchReaction { NONE, INVESTIGATE, AVOID, PEEK, FREEZE, CHECK, DUCK, FOLLOW, RISE, TUCK }

/**
 * The rules for a gentle tap (research 02 / 04 "Human at glass" lines per species, 06 §2 habituation):
 * - Glass or water: food-conditioned, bold fish come to look (ocellaris, blue tang, cleaner wrasse, hawkfish,
 *   dottyback: research 02 §1C, §2C, §4C, §6C and 04 §3D, which describe them approaching or watching from the glass);
 *   shy or nocturnal fish turn away to cover (pajama cardinal by day 02 §3C "retreats into rock", engineer goby,
 *   diamond goby, leopard wrasse); blennies pop out of their holes to watch (02 §7D, 04 §1C, §2C); shrimp and crabs
 *   pause (02 §8C, 04 §4C, §5C "withdraws into shell"). Feather dusters retract their crown close by (04 §8C).
 * - Sand: a little puff; sand-sifters and nassarius come over to check it (research 16, 04 §9C: nassarius surface
 *   for scent and disturbance), shy fish and shrimp right next to it react as on the glass.
 * - Rock or coral: polyp corals (GSP, zoas, clove) close by retract briefly; crabs, shrimp, blennies, hawkfish and
 *   dusters duck into cover; shy fish turn away.
 * Strength falls off with distance; personality (curiosity, boldness, skittishness) decides the borderline animals.
 */
object TankTouch {
    /** Reach of a tap on the glass for fish that come to look (they see it from across the tank). */
    @Ev(Evidence.DESIGN_ESTIMATE, "02 §1C/§2C approach the glass nearest the observer") const val SEE_CM = 50.0
    /** Reach of the vibration for animals that hide or freeze. */
    @Ev(Evidence.DESIGN_ESTIMATE) const val FEEL_CM = 28.0
    /** Rock / sand tap reach (the knock travels through the rock, not the water column). */
    @Ev(Evidence.DESIGN_ESTIMATE) const val ROCK_CM = 20.0
    /** Polyp corals within this distance of a rock / coral tap retract. */
    @Ev(Evidence.DESIGN_ESTIMATE, "03 polyp retraction to touch") const val CORAL_CM = 12.0
    /** A rock tap within this distance of a coral counts as a tap on the coral. */
    const val ON_CORAL_CM = 5.0
    /** Retraction of the tapped colony and its neighbours (a glass tap on a coral is 0.6, SIMULATION coral disturb). */
    const val CORAL_DISTURB = 0.6
    const val CORAL_DISTURB_HIT = 0.8

    /** Habituation added by each tap an animal reacts to, its decay time, and the level at which it stops caring. */
    @Ev(Evidence.DESIGN_ESTIMATE, "06 §2 habituation: response fades over repeated harmless trials, recovers over time")
    const val HABIT_STEP = 0.22
    const val HABIT_TAU_S = 20 * 60.0
    const val HABIT_IGNORE = 0.75

    /** Taps in one spot ([RUN_CM]) within [RUN_WINDOW_S] wall seconds that turn into a startling knock. */
    const val RUN_TAPS = 3
    const val RUN_CM = 10.0
    const val RUN_WINDOW_S = 4.0

    private val POLYPS = setOf(CoralKind.GSP, CoralKind.ZOA, CoralKind.CLOVE)
    private val CRUSTACEANS = setOf(BrainKind.BANDED_SHRIMP, BrainKind.CLEANER_SHRIMP, BrainKind.PEPPERMINT_SHRIMP, BrainKind.FIRE_SHRIMP, BrainKind.SEXY_SHRIMP, BrainKind.HARLEQUIN_SHRIMP, BrainKind.PISTOL_SHRIMP, BrainKind.SALLY_CRAB, BrainKind.ARROW_CRAB, BrainKind.EMERALD_CRAB, BrainKind.HERMIT)
    private val BLENNIES = setOf(BrainKind.LAWNMOWER_BLENNY, BrainKind.PEEKER_BLENNY)
    private val FOOD_CONDITIONED = setOf(BrainKind.CLOWN, BrainKind.TANG, BrainKind.WRASSE, BrainKind.HAWK, BrainKind.DOTTYBACK, BrainKind.CHROMIS)
    private val SHY = setOf(BrainKind.CARDINAL, BrainKind.ENGINEER_GOBY, BrainKind.DIAMOND_GOBY, BrainKind.LEOPARD_WRASSE, BrainKind.FAIRY_WRASSE, BrainKind.GRAMMA)

    /** Polyp corals retract to touch; the other kinds (leathers, mushrooms, macro) do not visibly react to a tap. */
    fun retracts(kind: CoralKind): Boolean = kind in POLYPS

    /** Fish that come to look: food-conditioned kinds with enough curiosity and boldness, or a bold generic fish. */
    fun bold(o: Organism): Boolean {
        val interest = 0.5 * o.curiosity + 0.5 * o.boldness
        return when (o.brainKind) {
            in FOOD_CONDITIONED -> interest >= 0.35
            BrainKind.GENERIC_FISH -> interest >= 0.6
            else -> false
        }
    }

    /** Fish that turn away from a tap: the shy kinds, or any skittish generic fish. */
    fun shy(o: Organism, night: Boolean): Boolean = when {
        o.brainKind in SHY -> !(o.brainKind == BrainKind.CARDINAL && night)   // the cardinal is out and unbothered at night
        o.brainKind == BrainKind.GENERIC_FISH -> o.skittishness >= 0.65
        else -> false
    }

    /**
     * The reaction of [o] to a tap on [surface] [d] cm away ([night] = lights out), before habituation. Animals busy
     * moulting or dead do not react.
     */
    fun reaction(o: Organism, surface: TouchSurface, d: Double, night: Boolean): TouchReaction {
        if (!o.alive || o.molting) return TouchReaction.NONE
        val k = o.brainKind
        if (k == BrainKind.FEATHER_DUSTER) return if (d < 15.0) TouchReaction.DUCK else TouchReaction.NONE
        // an anemone has no eyes: only a touch on or right next to it makes it pull in
        if (k == BrainKind.ANEMONE) return if (d < 0.5 * o.lengthCm + 3.0) TouchReaction.DUCK else TouchReaction.NONE
        // the flame scallop's mantle eyes see a hand at the glass: it snaps shut
        if (k == BrainKind.FILE_CLAM) return if (d < 12.0) TouchReaction.DUCK else TouchReaction.NONE
        return when (surface) {
            TouchSurface.GLASS -> when {
                // asleep at night: a tap close by only makes a fish shift away; nobody comes to look in the dark
                night && o.isFish -> if (d < 20.0 && shy(o, true)) TouchReaction.AVOID else TouchReaction.NONE
                k in BLENNIES -> if (d < SEE_CM * 0.7) TouchReaction.PEEK else TouchReaction.NONE
                bold(o) -> if (d < SEE_CM && o.stress < 0.5) TouchReaction.INVESTIGATE else TouchReaction.NONE
                shy(o, night) -> if (d < FEEL_CM) TouchReaction.AVOID else TouchReaction.NONE
                k in CRUSTACEANS -> if (d < FEEL_CM) still(k) else TouchReaction.NONE
                k == BrainKind.BOBTAIL_SQUID -> if (d < FEEL_CM) TouchReaction.DUCK else TouchReaction.NONE
                // research 41: a firefish dives into its hole at a knock close by
                k == BrainKind.FIREFISH -> if (d < FEEL_CM) TouchReaction.DUCK else TouchReaction.NONE
                else -> TouchReaction.NONE
            }
            TouchSurface.SAND -> when {
                k == BrainKind.DIAMOND_GOBY && !night -> if (d < 30.0) TouchReaction.CHECK else TouchReaction.NONE
                // research 50 §3: the watchman dives into its burrow at a knock on the sand near it
                k == BrainKind.WATCHMAN_GOBY -> if (d < 20.0) TouchReaction.DUCK else TouchReaction.NONE
                k == BrainKind.NASSARIUS -> if (d < 22.0 && o.curiosity >= 0.4) TouchReaction.CHECK else TouchReaction.NONE
                d >= 15.0 -> TouchReaction.NONE
                k in CRUSTACEANS -> still(k)
                o.isFish && shy(o, night) -> TouchReaction.AVOID
                k == BrainKind.BOBTAIL_SQUID -> TouchReaction.DUCK
                else -> TouchReaction.NONE
            }
            // the water's top: fed fish learn that a disturbance up there means food (the lid, the feeding ring)
            TouchSurface.SURFACE -> when {
                night && o.isFish -> TouchReaction.NONE
                bold(o) -> if (d < SEE_CM && o.stress < 0.5) TouchReaction.RISE else TouchReaction.NONE
                shy(o, night) -> if (d < FEEL_CM * 0.7) TouchReaction.AVOID else TouchReaction.NONE
                else -> TouchReaction.NONE
            }
            TouchSurface.ROCK, TouchSurface.CORAL -> when {
                d >= ROCK_CM -> TouchReaction.NONE
                k in CRUSTACEANS || k in BLENNIES || k == BrainKind.HAWK || k == BrainKind.BOBTAIL_SQUID || k == BrainKind.FIREFISH || k == BrainKind.GRAMMA || k == BrainKind.WATCHMAN_GOBY -> TouchReaction.DUCK
                o.isFish && shy(o, night) -> TouchReaction.AVOID
                else -> TouchReaction.NONE
            }
        }
    }

    /** Shrimp and the sally crab stop dead; a hermit pulls into its shell (04 §5C "withdraws into shell"). */
    private fun still(k: BrainKind): TouchReaction = if (k == BrainKind.HERMIT) TouchReaction.DUCK else TouchReaction.FREEZE

    /** Finger held on the glass: who notices it from how far, and the speed (cm/s) past which it reads as a lunging hand. */
    @Ev(Evidence.DESIGN_ESTIMATE, "02 §1C clown: retreats when hand-level motion is fast") const val FINGER_FAST_CM_S = 35.0
    /** Seconds a finger rests (moves < [FINGER_STILL_CM]) before followers start nipping the glass at it. */
    @Ev(Evidence.DESIGN_ESTIMATE, "hobby reports: tangs and clowns peck at a fingertip on the glass") const val NIP_AFTER_S = 1.5
    const val FINGER_STILL_CM = 1.0
    /** Followers keep watching this long after the finger lifts. */
    const val FINGER_LINGER_S = 2.5
    /** Reach of a water puff for flinching animals and retracting polyps (cm from the puff's path). */
    const val PUFF_CM = 12.0
    /** Polyp retraction from a puff that reaches a colony. */
    const val PUFF_DISTURB = 0.4

    /**
     * The reaction of [o] to a finger put on [surface] (glass or the water's top) [d] cm away: the tap rules, except
     * that the fish that would come to look ([TouchReaction.INVESTIGATE] / [TouchReaction.RISE]) now follow the finger.
     */
    fun fingerReaction(o: Organism, surface: TouchSurface, d: Double, night: Boolean): TouchReaction =
        when (val r = reaction(o, surface, d, night)) {
            TouchReaction.INVESTIGATE, TouchReaction.RISE -> TouchReaction.FOLLOW
            else -> r
        }

    /** A finger whisked past fast: shy fish and crustaceans close by react as to a lunge; nobody new comes to follow. */
    fun fastFingerReaction(o: Organism, d: Double, night: Boolean): TouchReaction = when {
        !o.alive || o.molting || d >= FEEL_CM -> TouchReaction.NONE
        o.brainKind in BLENNIES -> TouchReaction.DUCK
        o.isFish && shy(o, night) -> TouchReaction.AVOID
        o.brainKind in CRUSTACEANS -> still(o.brainKind)
        else -> TouchReaction.NONE
    }

    /**
     * A tap right on the animal (research 04 §5C hermit "withdraws into shell", 02 / 04 crustaceans freeze; a snail
     * touched clamps its shell down and pulls its foot and tentacles in, design estimate): snails, the crab and other
     * slow inverts pull in ([TouchReaction.TUCK]),
     * hermits withdraw into the shell, shrimp freeze, dusters, brittle stars and the squid hide. Fish just get picked.
     */
    fun animalReaction(o: Organism): TouchReaction {
        if (!o.alive || o.molting || o.isFish) return TouchReaction.NONE
        return when (o.brainKind) {
            BrainKind.HERMIT, BrainKind.FEATHER_DUSTER, BrainKind.BRITTLE_STAR, BrainKind.BOBTAIL_SQUID, BrainKind.ANEMONE, BrainKind.FILE_CLAM -> TouchReaction.DUCK
            BrainKind.BANDED_SHRIMP, BrainKind.CLEANER_SHRIMP, BrainKind.PEPPERMINT_SHRIMP, BrainKind.FIRE_SHRIMP,
            BrainKind.SEXY_SHRIMP, BrainKind.HARLEQUIN_SHRIMP -> TouchReaction.FREEZE
            BrainKind.PISTOL_SHRIMP -> TouchReaction.DUCK
            else -> TouchReaction.TUCK
        }
    }

    /** A puff of water [d] cm from its path: shy fish flinch away, crustaceans freeze, dusters snap shut. */
    fun puffReaction(o: Organism, d: Double, night: Boolean): TouchReaction = when {
        !o.alive || o.molting || d >= PUFF_CM -> TouchReaction.NONE
        o.brainKind == BrainKind.FEATHER_DUSTER -> TouchReaction.DUCK
        o.isFish && shy(o, night) -> TouchReaction.AVOID
        o.brainKind in CRUSTACEANS -> TouchReaction.FREEZE
        else -> TouchReaction.NONE
    }

    /** Into the water from the pane (or the surface) nearest [p]. */
    fun inwardAt(p: Vec3, rock: RockData): Vec3 {
        val d = listOf(
            p.z to Vec3.Z, rock.widthCm - p.z to Vec3(0.0, 0.0, -1.0), p.x to Vec3.X, rock.lengthCm - p.x to Vec3(-1.0, 0.0, 0.0),
            rock.waterLevelCm - p.y to Vec3(0.0, -1.0, 0.0),
        )
        return d.minBy { it.first }.second
    }

    /** Strength 0..1 of the reaction at [d] cm for a tap of [strength] (linear fall-off to the reaction's reach). */
    fun falloff(d: Double, reach: Double, strength: Double): Double = (strength * (1 - d / reach)).coerceIn(0.0, 1.0)
}

/**
 * Per-tank memory of recent taps: each animal's habituation (0..1, decaying over [TankTouch.HABIT_TAU_S]) and the run
 * of quick taps that escalates into a knock. A feed clears habituation: taps followed by food are worth answering.
 */
class TouchMemory {
    private val habit = HashMap<String, Double>()
    private val habitAt = HashMap<String, Double>()
    private val run = ArrayList<Pair<Vec3, Double>>()

    /** Current habituation of [id] at sim time [t]. */
    fun habituation(id: String, t: Double): Double {
        val h = habit[id] ?: return 0.0
        val at = habitAt[id] ?: t
        return h * exp(-max(0.0, t - at) / TankTouch.HABIT_TAU_S)
    }

    /** [id] answered a tap at [t]: it cares a little less about the next one. */
    fun reacted(id: String, t: Double) {
        habit[id] = (habituation(id, t) + TankTouch.HABIT_STEP).coerceAtMost(1.0)
        habitAt[id] = t
    }

    /** Food arrived: the taps paid off, so everyone answers them again. */
    fun fed() { habit.clear(); habitAt.clear() }

    /**
     * Records a tap at [p] at sim time [t]; true when it completes a quick run in one spot ([TankTouch.RUN_TAPS] taps
     * within [windowS] sim seconds and [TankTouch.RUN_CM]); the run then starts over.
     */
    fun tap(p: Vec3, t: Double, windowS: Double): Boolean {
        run.removeAll { t - it.second > windowS || it.first.distanceTo(p) > TankTouch.RUN_CM }
        run += p to t
        if (run.size >= TankTouch.RUN_TAPS) { run.clear(); return true }
        return false
    }

    fun clear() { habit.clear(); habitAt.clear(); run.clear() }
}

/**
 * A finger held on the glass ([Command.Finger]): where it is, how fast it moves, since when it has rested, who has
 * been considered this time (each animal answers once per hold) and who follows it.
 */
class FingerState(var pos: Vec3, val surface: TouchSurface, val downAt: Double) {
    var speedCmS = 0.0; private set
    var lastAt = downAt; private set
    private var stillAt = pos
    private var stillSince = downAt
    val considered = HashSet<String>()
    val followers = LinkedHashSet<String>()
    /** Who answered the finger and how (tests). */
    val reactions = LinkedHashMap<String, TouchReaction>()

    fun move(p: Vec3, speed: Double, t: Double) {
        if (p.distanceTo(stillAt) > TankTouch.FINGER_STILL_CM) { stillAt = p; stillSince = t }
        pos = p; speedCmS = speed; lastAt = t
    }

    /** Seconds the finger has rested in one spot. */
    fun stillFor(t: Double): Double = t - stillSince
}

/** What the last [Command.TouchTank] did ([Simulation.lastTouch]): who answered how, or that it became a knock. */
data class TouchOutcome(
    val surface: TouchSurface,
    val point: Vec3,
    val knock: Boolean,
    val reactions: Map<String, TouchReaction>,
    val corals: Int,
    val simTimeSec: Double,
) {
    fun count(r: TouchReaction): Int = reactions.values.count { it == r }
}
