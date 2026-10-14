package com.reeflive.core.sim

import com.reeflive.core.geom.Vec3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The pilot's stick for the toy submarine ([Command.SteerSubmarine]); every axis is -1..1:
 * [thrust] back / forward along the nose, [turn] left (-) / right (+) yaw, [rise] down / up, [strafe] left (-) / right (+).
 */
data class SubControls(val thrust: Double = 0.0, val turn: Double = 0.0, val rise: Double = 0.0, val strafe: Double = 0.0) {
    companion object { val IDLE = SubControls() }
}

/** The sub as the renderer draws it ([SimSnapshot.submarine]); yaw 0 = nose along +X, positive turns toward -Z. */
data class SubPose(
    val pos: Vec3,
    val yaw: Double,
    val pitch: Double,
    val roll: Double,
    /** Propeller angle (radians, wrapped). */
    val prop: Double,
    val speed: Double,
    /** False = the fish cannot see it ("invisible" mode); the renderer draws it as a faint ghost. */
    val visible: Boolean,
    val headlight: Boolean,
)

/**
 * A little toy submarine the viewer steers through the tank (Submarine mode). It moves on wall time
 * ([Simulation.stepWall]), so it handles the same at any sim speed, and it never passes through anything: the glass and
 * the water surface (analytic box), the sand bed ([SandBed.surfaceY]) and the rock ([RockData.sdf]) all push it back.
 * Fish see it as a moving object ([SubmarineRules], played by `ai.SubmarineReact`) unless [visible] is off.
 */
class Submarine {
    var active = false; private set
    var visible = true
    var headlight = true
    var pos: Vec3 = SPAWN; private set
    var vel: Vec3 = Vec3.ZERO; private set
    var yaw = 0.0; private set
    var controls = SubControls.IDLE
    private var yawRate = 0.0
    private var pitch = 0.0
    private var prop = 0.0

    val heading: Vec3 get() = Vec3(cos(yaw), 0.0, -sin(yaw))
    val speed: Double get() = vel.length()

    /** Launch at the tank's left end, nose to the right (or stop and park it). */
    fun setActive(on: Boolean, rock: RockData, sand: SandBed?) {
        if (on == active) return
        active = on
        controls = SubControls.IDLE
        vel = Vec3.ZERO; yawRate = 0.0; pitch = 0.0
        if (on) { yaw = 0.0; pos = collide(Vec3(SPAWN.x, SPAWN.y, rock.widthCm * 0.5), rock, sand) }
    }

    /** Puts the sub at [p] (tests; still pushed clear of rock, sand and glass). */
    fun teleport(p: Vec3, rock: RockData, sand: SandBed?, newYaw: Double = yaw) {
        pos = collide(p, rock, sand); vel = Vec3.ZERO; yaw = newYaw
    }

    /** Advances [dt] wall seconds under [controls] with collision. Sub-stepped so a fast sub never tunnels. */
    fun step(dt: Double, rock: RockData, sand: SandBed?) {
        if (!active || dt <= 0.0) return
        val c = controls
        val n = max(1, kotlin.math.ceil(dt / MAX_STEP_S).toInt())
        val h = dt / n
        repeat(n) {
            val kTurn = 1 - exp(-h / TURN_TAU_S)
            yawRate += (-c.turn.coerceIn(-1.0, 1.0) * TURN_RATE - yawRate) * kTurn
            yaw = wrap(yaw + yawRate * h)
            val fwd = heading
            val right = Vec3(sin(yaw), 0.0, cos(yaw))
            val want = fwd * (c.thrust.coerceIn(-1.0, 1.0) * if (c.thrust < 0) MAX_SPEED * 0.5 else MAX_SPEED) +
                right * (c.strafe.coerceIn(-1.0, 1.0) * MAX_SPEED * 0.5) + Vec3.Y * (c.rise.coerceIn(-1.0, 1.0) * RISE_SPEED)
            vel = vel + (want - vel) * (1 - exp(-h / DRAG_TAU_S))
            val next = collide(pos + vel * h, rock, sand)
            // what the walls took away is gone (the sub slides along glass, sand and rock); a push-out never speeds it up
            vel = ((next - pos) / h).clampLength(vel.length())
            pos = next
            val horiz = sqrt(vel.x * vel.x + vel.z * vel.z)
            pitch += (atan2(vel.y, max(horiz, 2.0)) * 0.8 - pitch) * (1 - exp(-h / 0.4))
            prop = wrap(prop + h * (PROP_IDLE + PROP_RATE * maxOf(abs(c.thrust), abs(c.strafe) * 0.6, abs(c.rise) * 0.4)) * if (c.thrust < 0) -1 else 1)
        }
    }

    /** The nearest free spot to [p]: inside the glass and under the surface, above the sand, out of the rock. */
    fun collide(p0: Vec3, rock: RockData, sand: SandBed?): Vec3 {
        var p = box(p0, rock, sand)
        for (i in 0 until 4) {
            val d = rock.sdf.dist(p)
            if (d >= RADIUS_CM) break
            p = box(p + rock.sdf.gradient(p) * (RADIUS_CM - d + 0.02), rock, sand)
        }
        return p
    }

    private fun box(p: Vec3, rock: RockData, sand: SandBed?): Vec3 {
        val x = p.x.coerceIn(RADIUS_CM, rock.lengthCm - RADIUS_CM)
        val z = p.z.coerceIn(RADIUS_CM, rock.widthCm - RADIUS_CM)
        val floor = (sand?.surfaceY(x, z) ?: rock.sandTopCm) + RADIUS_CM
        val y = p.y.coerceIn(floor, max(floor, rock.waterLevelCm - RADIUS_CM - 0.5))
        return Vec3(x, y, z)
    }

    fun pose(): SubPose = SubPose(pos, yaw, pitch, -yawRate * 0.18, prop, speed, visible, headlight)

    fun reset() { active = false; visible = true; headlight = true; controls = SubControls.IDLE; vel = Vec3.ZERO; pos = SPAWN; yaw = 0.0 }

    private fun wrap(a: Double): Double { var r = a % (2 * PI); if (r > PI) r -= 2 * PI; if (r < -PI) r += 2 * PI; return r }

    companion object {
        /** Hull length and collision radius (cm): about a small clownfish, so it fits through the arch. */
        const val LENGTH_CM = 4.6
        const val RADIUS_CM = 1.4
        /** Top speed (cm/s): a cruising tang; reverse is half. */
        const val MAX_SPEED = 12.0
        const val RISE_SPEED = 7.0
        const val TURN_RATE = 1.8
        const val TURN_TAU_S = 0.25
        const val DRAG_TAU_S = 0.55
        const val PROP_IDLE = 1.5
        const val PROP_RATE = 28.0
        const val MAX_STEP_S = 0.05
        val SPAWN = Vec3(14.0, 30.0, 16.5)
    }
}

/**
 * How one animal answers the submarine ([SubmarineRules.reaction]), played by `ai.SubmarineReact`:
 * - [INVESTIGATE]: comes over and hovers a few cm off, facing it, and follows it about (curious, food-conditioned fish).
 * - [POSTURE]: guards its spot: a clown between the sub and its host, a dottyback at its cave, fins up with short
 *   darts at the intruder; a perched hawkfish turns to watch it.
 * - [AVOID]: keeps its distance and drifts to cover (shy fish).
 * - [FREEZE]: a shrimp or crab stops dead, antennae in.
 * - [HIDE]: the species' own retreat (goby into the burrow, blenny into its hole, hermit into its shell, duster crown).
 * - [STARTLE]: a fast approach: the full flee of a glass knock.
 */
enum class SubReaction { NONE, INVESTIGATE, POSTURE, AVOID, FREEZE, HIDE, STARTLE }

/**
 * The submarine as a perceived object (research 02 / 04 "Human at glass" per species, 06 §2 habituation, the same
 * kinds as [TankTouch]): bold, food-conditioned fish investigate a slow sub, territorial ones posture when it comes
 * near what they guard, shy and nocturnal fish keep clear, burrowers and crawlers retract. A sub closing fast startles
 * anything within reach (wider for skittish fish). A sub that loiters harmlessly is habituated to over minutes
 * ([SubMemory]); a startle sensitises again. Invisible = no perception at all.
 */
object SubmarineRules {
    /** Closing speed (cm/s) toward an animal that counts as a charge. */
    @Ev(Evidence.DESIGN_ESTIMATE, "06 §2 looming stimulus: fast approach elicits escape") const val STARTLE_SPEED = 7.0
    /** Base reach of a charge (cm), + [STARTLE_SKITTISH_CM] × skittishness. */
    const val STARTLE_CM = 9.0
    const val STARTLE_SKITTISH_CM = 10.0
    /** How far a curious fish notices a slow sub, and how close a shy one lets it come. */
    const val SEE_CM = 28.0
    const val SHY_CM = 14.0
    /** Distance from the sub to a clown's host / a dottyback's cave that makes them guard it. */
    const val GUARD_CM = 14.0
    /** Loitering exposure (s) that habituates fully; decay of habituation once the sub has gone; ignore level. */
    @Ev(Evidence.DESIGN_ESTIMATE, "06 §2 habituation over repeated harmless exposure, minutes")
    const val HABIT_BUILD_S = 200.0
    const val HABIT_TAU_S = 20 * 60.0
    const val HABIT_IGNORE = 0.75
    /** Sensitisation: a startle takes this much habituation away. */
    const val HABIT_STARTLE_LOSS = 0.2

    private val CRUSTACEANS = setOf(BrainKind.BANDED_SHRIMP, BrainKind.CLEANER_SHRIMP, BrainKind.SALLY_CRAB)
    private val BLENNIES = setOf(BrainKind.LAWNMOWER_BLENNY, BrainKind.PEEKER_BLENNY)
    private val BURROWERS = setOf(BrainKind.ENGINEER_GOBY, BrainKind.DIAMOND_GOBY, BrainKind.LEOPARD_WRASSE)
    /** Animals with nothing to show (snails, stars) or no escape to play. */
    private val INDIFFERENT = setOf(BrainKind.ASTREA, BrainKind.ASTERINA, BrainKind.BRITTLE_STAR, BrainKind.GENERIC_INVERT)

    private fun interest(o: Organism) = 0.5 * o.curiosity + 0.5 * o.boldness

    /** Reach of a charge at [o] (cm), smaller once it is used to the sub. */
    fun startleReach(o: Organism, habit: Double): Double = (STARTLE_CM + STARTLE_SKITTISH_CM * o.skittishness) * (1 - 0.5 * habit)

    /**
     * The reaction of [o] to a sub [d] cm away closing at [closing] cm/s, [guardD] cm from what [o] guards (clown host,
     * dottyback cave; +inf when nothing), with lights out = [night] and habituation [habit] (0..1).
     */
    fun reaction(o: Organism, d: Double, closing: Double, guardD: Double, night: Boolean, habit: Double): SubReaction {
        if (!o.alive || o.molting) return SubReaction.NONE
        val k = o.brainKind
        if (k in INDIFFERENT) return SubReaction.NONE
        if (k == BrainKind.FEATHER_DUSTER) return if (d < 8.0 * (1 - 0.5 * habit)) SubReaction.HIDE else SubReaction.NONE
        // a charge: everything that can get away does
        if (closing > STARTLE_SPEED && d < startleReach(o, habit)) return when {
            o.isFish -> SubReaction.STARTLE
            k == BrainKind.NASSARIUS -> SubReaction.NONE
            else -> SubReaction.HIDE
        }
        if (habit >= HABIT_IGNORE) return SubReaction.NONE
        if (night && o.isFish) return if (d < 8.0) SubReaction.AVOID else SubReaction.NONE
        return when (k) {
            BrainKind.CLOWN -> when {
                guardD < GUARD_CM && d < GUARD_CM + 6 -> SubReaction.POSTURE
                interest(o) >= 0.35 && d < SEE_CM -> SubReaction.INVESTIGATE
                else -> SubReaction.NONE
            }
            BrainKind.DOTTYBACK -> when {
                guardD < GUARD_CM && d < GUARD_CM + 6 -> SubReaction.POSTURE
                interest(o) >= 0.35 && d < SEE_CM * 0.8 -> SubReaction.INVESTIGATE
                else -> SubReaction.NONE
            }
            BrainKind.HAWK -> if (d < SEE_CM) SubReaction.POSTURE else SubReaction.NONE
            BrainKind.EEL -> if (d < 12.0) SubReaction.POSTURE else SubReaction.NONE
            BrainKind.TANG, BrainKind.WRASSE -> when {
                interest(o) >= 0.35 -> if (d < SEE_CM) SubReaction.INVESTIGATE else SubReaction.NONE
                d < SHY_CM -> SubReaction.AVOID
                else -> SubReaction.NONE
            }
            BrainKind.CARDINAL -> if (d < SHY_CM) SubReaction.AVOID else SubReaction.NONE
            in BURROWERS -> when {
                d < 8.0 -> SubReaction.HIDE
                d < SHY_CM + 4 -> SubReaction.AVOID
                else -> SubReaction.NONE
            }
            in BLENNIES -> if (d < 9.0) SubReaction.HIDE else SubReaction.NONE
            in CRUSTACEANS -> when {
                d < 5.0 -> SubReaction.HIDE
                d < 13.0 -> SubReaction.FREEZE
                else -> SubReaction.NONE
            }
            BrainKind.HERMIT, BrainKind.NASSARIUS -> if (d < 7.0) SubReaction.HIDE else SubReaction.NONE
            BrainKind.BOBTAIL_SQUID -> if (d < 10.0) SubReaction.HIDE else SubReaction.NONE
            BrainKind.GENERIC_FISH -> when {
                interest(o) >= 0.6 && d < SEE_CM -> SubReaction.INVESTIGATE
                o.skittishness >= 0.6 && d < SHY_CM -> SubReaction.AVOID
                else -> SubReaction.NONE
            }
            else -> SubReaction.NONE
        }
    }

    /** Strength 0..1 of a startle from a sub closing at [closing] cm/s [d] cm away. */
    fun startleMag(closing: Double, d: Double, reach: Double): Double =
        (0.45 + 0.04 * (closing - STARTLE_SPEED)).coerceIn(0.45, 0.95) * (1.2 - 0.4 * d / max(reach, 1.0)).coerceIn(0.6, 1.0)
}

/**
 * Per-animal habituation to the sub: exposure while it loiters nearby builds it over [SubmarineRules.HABIT_BUILD_S],
 * it decays over [SubmarineRules.HABIT_TAU_S] otherwise, and a startle knocks some of it back.
 */
class SubMemory {
    private val habit = HashMap<String, Double>()
    private val habitAt = HashMap<String, Double>()

    fun habituation(id: String, t: Double): Double {
        val h = habit[id] ?: return 0.0
        return h * exp(-max(0.0, t - (habitAt[id] ?: t)) / SubmarineRules.HABIT_TAU_S)
    }

    /** [id] spent [dt] s near a sub that did nothing to it. */
    fun exposed(id: String, t: Double, dt: Double) {
        habit[id] = min(1.0, habituation(id, t) + dt / SubmarineRules.HABIT_BUILD_S); habitAt[id] = t
    }

    fun startled(id: String, t: Double) {
        habit[id] = max(0.0, habituation(id, t) - SubmarineRules.HABIT_STARTLE_LOSS); habitAt[id] = t
    }

    fun clear() { habit.clear(); habitAt.clear() }
}
