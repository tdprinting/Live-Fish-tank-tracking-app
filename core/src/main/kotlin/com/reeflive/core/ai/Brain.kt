package com.reeflive.core.ai

import com.reeflive.core.geom.Vec3
import com.reeflive.core.sim.Anchor
import com.reeflive.core.sim.BrainKind
import com.reeflive.core.sim.FoodKind
import com.reeflive.core.sim.FoodParticle
import com.reeflive.core.sim.Organism
import com.reeflive.core.sim.Symptom
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/** One organism's runtime AI: context, brain, reusable intent and its steering. */
class Agent(val ctx: Ctx, val brain: SpeciesBrain) {
    val intent = SteerIntent()
    val steering = Steering(ctx)
}

/** Where an animal rests (abstract mode, sleep): position, heading, optional up and posture. */
data class Rest(val pos: Vec3, val heading: Vec3, val up: Vec3? = null, val posture: Int = 0, val spline: List<Vec3>? = null)

/**
 * Base for every species brain (SIMULATION §4.5). Holds perception-derived state shared by its
 * behaviors (startle, threat, food alert) and the hooks the Simulation calls (food, tap, shadow).
 */
abstract class SpeciesBrain(val c: Ctx) {
    val o: Organism get() = c.o
    lateinit var utility: UtilityBrain

    // startle (glass tap, lights-on, attack)
    var startleAt = -1e9
    var startleFrom: Vec3 = Vec3.ZERO
    var startleMag = 0.0
    var foodAlertAt = -1e9
    var foodAlertPos: Vec3 = Vec3.ZERO
    var shadowAt = -1e9
    /** The nearest predator within its threat radius (refreshed 4×/s). */
    var threat: Organism? = null
    private var threatCheck = 0.0
    private var perceiveIn = 0.0
    private var eyeTarget = 0.0
    private var eyeNext = 0.0
    private var lastLightsOnSeen = -1e9
    /** Set by a dominant clown's approach (06 §5.4 QUIVER_YIELD); read by [Yield]. */
    var yieldUntil = -1e9
    var yieldTo: Organism? = null
    var yieldBackS = 1.0
    /** The latest gentle tap this animal answers ([onTouch], shown by [TouchReact]): what, where, from when to when. */
    var touchKind = com.reeflive.core.sim.TouchReaction.NONE
    var touchFrom: Vec3 = Vec3.ZERO
    var touchStartAt = -1e9
    var touchUntil = -1e9
    /** Bumped by every [onTouch], so a running reaction restarts on a new tap. */
    var touchSerial = 0
    /** Glass nips at a resting finger ([TouchReact] FOLLOW); tests and captions. */
    var touchNips = 0
    /** Place memory, feeding clock, mood and personality drift (ai/Mind.kt; realism_gaps X2–X4). */
    val mind = Mind(c.o)

    /** World position of a matrix anchor (host, cave, hole, perch, station, ...) for this animal, or null. */
    open fun anchorPos(c: Ctx, anchor: String): Vec3? = if (anchor == "body") o.pos else o.homePos

    /** A dominant clown approached: the subordinate quivers 1–3 s, then backs off 0.5–2 BL (S:Colleye12). */
    open fun onDominated(c: Ctx, by: Organism) {
        yieldTo = by
        val quiver = c.rng.range(1.0, 3.0)
        yieldBackS = c.rng.range(0.5, 2.0)
        yieldUntil = c.t + quiver + yieldBackS
        o.counters.add(com.reeflive.core.sim.Counters.K.QUIVER, c.t)
        c.sim.emit(com.reeflive.core.sim.SimEvent.Quiver(o.id, by.id, c.sim.simTimeSec))
    }

    protected fun install(vararg behaviors: Behavior) {
        // Shared layers for every brain: matrix-driven rivalry (research 05 §17) and the sick/moribund behavior.
        val extra = buildList {
            // research 06 pair matrix (territory triggers + display bouts); the generic rivalry only for animals without rows
            val pairRows = PairMatrix.rows.filter { r ->
                r.type != "predation" && r.type != "defensive" &&
                    (r.actor == o.brainKind.name || o.brainKind == BrainKind.CLOWN && r.actor.startsWith("CLOWN_"))
            }
            if (pairRows.isNotEmpty()) add(PairBouts(this@SpeciesBrain, pairRows))
            else if (o.isFish && o.brainKind != BrainKind.ENGINEER_GOBY && o.brainKind != BrainKind.CARDINAL) add(Rivalry(this@SpeciesBrain))
            if (o.brainKind == BrainKind.CLOWN) add(Yield(this@SpeciesBrain))
            if (o.isFish) add(PoseForCleaner(this@SpeciesBrain))
            if (o.isFish) add(KeepClear(this@SpeciesBrain))
            add(Unwell(this@SpeciesBrain))
            add(HeatResponse(this@SpeciesBrain))
            // a gentle tap on the tank (Command.TouchTank); scores 0 and draws no rng until a tap arrives
            add(TouchReact(this@SpeciesBrain))
            // the toy submarine (Submarine mode); scores 0 and draws no rng while the sub is away, hidden or off
            add(SubmarineReact(this@SpeciesBrain))
            // learned feeding times and the keeper as a food cue (Mind.kt); idle until learned, no rng
            if (Mind.anticipates(o.brainKind)) { add(AnticipateFeed(this@SpeciesBrain)); add(ComeToKeeper(this@SpeciesBrain)) }
        }
        utility = UtilityBrain(behaviors.toList() + extra)
        o.brain = utility
    }

    open fun preTick(c: Ctx) {
        val dt = c.dt
        o.sleeping = false
        o.clock = c.t
        o.mouth *= exp(-dt * 5)
        o.spineFlare *= exp(-dt * 3)
        if (o.isFish && (o.posture == 3 || o.posture == 4 || o.posture == 6 || Symptom.HIDING in o.symptoms)) o.counters.cover(dt, c.t)
        o.extraRoll = 0.0
        if (o.isFish) o.parasiteLoad = min(1.0, o.parasiteLoad + dt / (3600.0 * 10))
        threatCheck -= dt
        perceiveIn -= dt
        if (perceiveIn <= 0) { Social.perceive(c, Social.PERCEIVE_INTERVAL - perceiveIn); perceiveIn = Social.PERCEIVE_INTERVAL * (0.8 + 0.4 * c.rng.nextDouble()) }
        if (threatCheck <= 0) { threatCheck = 0.25; threat = findThreat(c) }
        // eye glances
        if (c.t > eyeNext) { eyeTarget = c.rng.signed() * 0.35; eyeNext = c.t + c.rng.range(0.6, 3.0) }
        o.eyeYaw += (eyeTarget - o.eyeYaw) * min(1.0, dt * 8)
        // lights-on startle for diurnal fish (SIMULATION §4.7)
        if (o.isFish && c.p.lightsOnAt > lastLightsOnSeen && c.t - c.p.lightsOnAt < 120) {
            lastLightsOnSeen = c.p.lightsOnAt
            if (c.rng.chance(0.6)) { startleAt = c.t; startleFrom = o.pos + Vec3.Y * 10.0; startleMag = 0.2 }
            o.startle = max(o.startle, 0.05)
        }
        mind.tick(c)
    }

    abstract fun restSpot(c: Ctx, night: Boolean): Rest

    /** Abstract mode (speed > 240×): park at the rest spot for the time of day. */
    open fun abstractPose(c: Ctx, out: SteerIntent) {
        val night = c.p.night
        val r = restSpot(c, night)
        out.place(r.pos, r.heading, r.up)
        out.posture = r.posture
        out.splineOverride = r.spline
        o.sleeping = if (o.species.activity.equals("NOCTURNAL", true)) !night else night
    }

    open fun onFood(c: Ctx, kind: FoodKind, at: Vec3) {
        if (o.species.dietMatch(kind) > 0 || o.species.grazer && kind == FoodKind.NORI) { foodAlertAt = c.t; foodAlertPos = at }
    }

    open fun onTap(c: Ctx, at: Vec3, s: Double) { startleAt = c.t; startleFrom = at; startleMag = s; mind.onStartle(c, s) }

    /**
     * A gentle tap at [at] that this animal answers with [kind] ([com.reeflive.core.sim.TankTouch]); [s] 0..1 is its
     * strength after distance and habituation. DUCK goes through the species' own startle ([onTap]); the others are
     * played by [TouchReact] for a few seconds, a little after the tap (a nassarius takes longer: it smells for it).
     */
    open fun onTouch(c: Ctx, at: Vec3, kind: com.reeflive.core.sim.TouchReaction, s: Double) {
        if (kind == com.reeflive.core.sim.TouchReaction.NONE) return
        mind.onKeeperCue(c, at)
        if (kind == com.reeflive.core.sim.TouchReaction.DUCK) { onTap(c, at, 0.4 + 0.3 * s); return }
        val delay = when {
            kind == com.reeflive.core.sim.TouchReaction.CHECK && o.brainKind == BrainKind.NASSARIUS -> c.rng.range(4.0, 12.0)
            kind == com.reeflive.core.sim.TouchReaction.FREEZE || kind == com.reeflive.core.sim.TouchReaction.TUCK -> c.rng.range(0.05, 0.2)
            else -> c.rng.range(0.2, 1.2)
        }
        val dur = when (kind) {
            com.reeflive.core.sim.TouchReaction.INVESTIGATE -> c.rng.range(5.0, 12.0) * (0.6 + 0.6 * s)
            com.reeflive.core.sim.TouchReaction.AVOID -> c.rng.range(8.0, 20.0) * (0.5 + o.skittishness)
            com.reeflive.core.sim.TouchReaction.PEEK -> c.rng.range(4.0, 9.0)
            com.reeflive.core.sim.TouchReaction.FREEZE -> c.rng.range(2.0, 5.0) * (0.6 + 0.6 * s)
            com.reeflive.core.sim.TouchReaction.CHECK -> c.rng.range(20.0, 45.0)
            // held until the finger lifts (the sim then shortens it to a short linger)
            com.reeflive.core.sim.TouchReaction.FOLLOW -> 600.0
            com.reeflive.core.sim.TouchReaction.RISE -> c.rng.range(4.0, 9.0) * (0.6 + 0.6 * s)
            com.reeflive.core.sim.TouchReaction.TUCK -> c.rng.range(5.0, 15.0) * (0.6 + 0.6 * s)
            else -> 0.0
        }
        touchKind = kind; touchFrom = at; touchStartAt = c.t + delay; touchUntil = c.t + delay + dur; touchSerial++
    }

    /** A blenny's head-further-out pose at its hole for a peek at a tap ([TouchReact] PEEK); null = no hole. */
    open fun peekRest(c: Ctx): Rest? = null

    open fun onShadow(c: Ctx, at: Vec3) {
        shadowAt = c.t
        mind.onKeeperCue(c, at)
        if (o.isFish && o.skittishness > 0.5) { startleAt = c.t; startleFrom = at; startleMag = 0.2 }
    }

    /** Hit by an attack (eel strike, hawk pounce, territorial charge) that it survived. */
    open fun onAttacked(c: Ctx, from: Vec3, mag: Double, by: Organism? = null) {
        if (by != null) Social.recordAttack(by, o, mag)
        mind.onAttacked(c, from, mag)
        startleAt = c.t; startleFrom = from; startleMag = max(0.5, mag)
        o.startle = max(o.startle, 0.3 * mag)
    }

    open fun findThreat(c: Ctx): Organism? {
        var best: Organism? = null
        var bd = Double.MAX_VALUE
        for (n in c.p.neighbors(o.pos, 40.0)) {
            if (n === o || !n.alive) continue
            // night vision: prey notice a predator later in the dark
            val r0 = Predation.threatRadius(n, o)
            if (r0 <= 0) continue
            // sight fades in the dark; the lateral line still feels a close approach (Perceive.kt, realism_gaps X6)
            val r = r0 * Perceive.threatScale(c, o, r0)
            val d = n.pos.distanceTo(o.pos)
            if (d >= r || d >= bd) continue
            // a predator only alarms when it moves toward me, or when it is very close (SIMULATION §4.4)
            val closing = n.vel dot (o.pos - n.pos).normalized(Vec3.Y)
            if (closing < 0.5 && d > 0.5 * r) continue
            bd = d; best = n
        }
        return best
    }

    val startledRecently: Boolean get() = c.t - startleAt < 0.6

    /** Initial placement and home (called once by [SpeciesBrains.create]). */
    open fun initialPlace(c: Ctx) {
        val r = restSpot(c, false)
        // a rest spot from an older anchor set can sit just inside the rebuilt rock: start on its surface
        val pos = if (c.rock.sdf.dist(r.pos) < 0.0) c.rock.sdf.project(r.pos, 0.2) else r.pos
        o.homePos = pos
        o.placeAt(pos, r.heading)
        o.up = (r.up ?: Vec3.Y).rejectFrom(o.heading).normalized(Vec3.Y)
    }
}

/** A sheltered open-water point near the animal, away from [from] (SIMULATION §4.4 Flee). */
fun Ctx.coverPoint(from: Vec3, radius: Double = 30.0): Vec3 {
    var best: Vec3? = null
    var bs = -1e9
    val clear = clearance
    for (i in 0 until 14) {
        val p = o.pos + rng.unitVector() * (radius * (0.3 + 0.7 * rng.nextDouble()))
        if (!rock.inWater(p, 2.0)) continue
        val d = rock.sdf.dist(p)
        if (d < clear || d > clear + 8) continue
        val s = rock.sdf.cover(p) + 0.015 * p.distanceTo(from)
        if (s > bs) { bs = s; best = p }
    }
    return best ?: safe(o.pos + (o.pos - from).normalized(Vec3.Y) * 15.0)
}

/** Cross-species predation and threat table (SIMULATION §4.5, research 04 §13). */
object Predation {
    /** Radius within which [pred] makes [me] flee (0 = no threat). */
    fun threatRadius(pred: Organism, me: Organism): Double {
        val L = me.lengthCm
        return when (pred.brainKind) {
            BrainKind.EEL -> when {
                pred.posture == 3 -> L + 4.0                // head out of the den: only right in front of it
                me.brainKind == BrainKind.WRASSE -> 2 * L
                me.brainKind == BrainKind.BANDED_SHRIMP || me.brainKind == BrainKind.CLEANER_SHRIMP -> 12.0
                me.brainKind == BrainKind.HERMIT -> 8.0
                me.brainKind == BrainKind.BRITTLE_STAR -> 4.0
                me.isFish && me.brainKind != BrainKind.EEL -> 3 * L
                else -> 0.0
            }
            BrainKind.HAWK -> when (me.brainKind) {
                BrainKind.LAWNMOWER_BLENNY, BrainKind.PEEKER_BLENNY -> 1.5 * L
                BrainKind.CLEANER_SHRIMP -> 8.0
                BrainKind.HERMIT -> 5.0
                BrainKind.CARDINAL -> if (L < 0.5 * pred.lengthCm) 1.5 * L else 0.0
                BrainKind.WRASSE -> if (pred.attached) pred.lengthCm else 0.0
                // research cephalopod_squid: a bobtail squid out of the sand is the size of the hawk's usual prey
                BrainKind.BOBTAIL_SQUID -> if (me.posture == 5) 0.0 else 2.5 * L
                else -> 0.0
            }
            else -> 0.0
        }
    }

    /** Prey preference weight of [prey] for [pred] (0 = never targeted). */
    fun preyWeight(pred: Organism, prey: Organism, night: Boolean): Double {
        if (!prey.alive || prey === pred || prey.molting && !prey.visible) return 0.0
        return when (pred.brainKind) {
            BrainKind.EEL -> when (prey.brainKind) {
                BrainKind.BANDED_SHRIMP, BrainKind.CLEANER_SHRIMP -> 1.0
                BrainKind.HERMIT -> 0.8
                BrainKind.CARDINAL -> if (night) 0.5 else 0.1
                BrainKind.LAWNMOWER_BLENNY, BrainKind.PEEKER_BLENNY, BrainKind.DOTTYBACK -> if (prey.posture == 3) 0.0 else 0.3
                BrainKind.WRASSE -> if (night) 0.2 else 0.0
                BrainKind.CLOWN -> 0.1
                BrainKind.BRITTLE_STAR -> 0.1
                else -> 0.0
            }
            BrainKind.SALLY_CRAB -> when {
                pred.lengthCm < SallyCrabBrain.GRAB_MIN_CM -> 0.0
                prey.brainKind == BrainKind.ASTREA || prey.brainKind == BrainKind.NASSARIUS -> 0.6
                prey.isFish && prey.sleeping && prey.lengthCm < 0.8 * pred.lengthCm -> 0.4
                else -> 0.0
            }
            BrainKind.HAWK -> when (prey.brainKind) {
                BrainKind.CLEANER_SHRIMP -> if (prey.lengthCm < 0.9 * pred.lengthCm) 0.7 else 0.0
                BrainKind.BANDED_SHRIMP -> if (prey.lengthCm < 0.6 * pred.lengthCm) 0.4 else 0.0
                BrainKind.HERMIT -> 0.4
                BrainKind.CARDINAL -> if (prey.lengthCm < 0.5 * pred.lengthCm) 0.3 else 0.0
                BrainKind.BRITTLE_STAR -> 0.1
                // research cephalopod_squid: an exposed bobtail squid is soft prey for a hawkfish (buried in the sand it is not found)
                BrainKind.BOBTAIL_SQUID -> if (prey.posture == 5 || prey.lengthCm > 1.2 * pred.lengthCm) 0.0 else 0.5
                else -> 0.0
            }
            else -> 0.0
        }
    }
}

// ============================================================== Ctx helpers
val Ctx.food get() = sim.food
val Ctx.brain: SpeciesBrain get() = sim.agentOf(o)!!.brain

/** y for a fraction of the water column (0 = sand top, 1 = surface). */
fun Ctx.zoneY(f: Double) = rock.sandTopCm + f * (rock.waterLevelCm - rock.sandTopCm)

/** Clearance a swimmer keeps from rock (SIMULATION §4.3). */
val Ctx.clearance: Double get() = 0.6 * o.collisionLength + 1.0

/** Push a point into open water with at least [clear] cm to rock and a glass margin. */
fun Ctx.safe(p: Vec3, clear: Double = clearance): Vec3 {
    val m = max(1.0, min(4.0, 0.3 * L))
    var q = rock.clampToWater(p, m)
    for (i in 0 until 3) {
        if (rock.sdf.dist(q) >= clear) break
        q = rock.clampToWater(rock.sdf.project(q, clear), m)
    }
    return q
}

/**
 * Next point to steer for on the way to [goal]. There is no path-finding: with rock in the way the
 * swimmer goes over the top, as reef fish do: it climbs to a height from which the goal is in clear
 * view, crosses, then drops to it.
 */
fun Ctx.viaOpenWater(goal: Vec3): Vec3 {
    val sdf = rock.sdf
    if (sdf.segmentClear(o.pos, goal, 0.5)) return goal
    for (h in listOf(8.0, 16.0, 24.0, 32.0)) {
        val above = safe(goal + Vec3.Y * h)
        if (!sdf.segmentClear(above, goal, 0.5)) continue
        if (sdf.segmentClear(o.pos, above, 0.5)) return above
        val up = safe(Vec3(o.pos.x, above.y, o.pos.z))
        if (sdf.segmentClear(up, above, 0.5)) return up
        // the climb alone leaves the swimmer pinned under a ridge: slide across at its own height first, along either axis
        for (mid in listOf(Vec3(above.x, up.y, o.pos.z), Vec3(o.pos.x, up.y, above.z))) {
            val m = safe(mid)
            if (sdf.segmentClear(o.pos, m, 0.5) && sdf.segmentClear(m, above, 0.5)) return m
        }
        return up
    }
    // under an overhang: a doorway beside the goal that the swimmer can see
    for (d in listOf(8.0, 16.0)) for (dir in DOORWAYS) {
        val door = safe(goal + dir * d)
        if (sdf.segmentClear(door, goal, 0.5) && sdf.segmentClear(o.pos, door, 0.5)) return door
    }
    return goal
}

private val DOORWAYS = listOf(Vec3(0.0, 0.0, -1.0), Vec3(0.0, 0.0, 1.0), Vec3(-1.0, 0.0, 0.0), Vec3(1.0, 0.0, 0.0),
    Vec3(0.0, 0.7, -0.7), Vec3(0.0, 0.7, 0.7), Vec3(-0.7, 0.7, 0.0), Vec3(0.7, 0.7, 0.0))

/** A point on the rock surface near [center] with its outward normal; null if none found. */
fun Ctx.rockSurfacePoint(center: Vec3, radius: Double, minNormalY: Double = -0.2, tries: Int = 12): Pair<Vec3, Vec3>? {
    for (i in 0 until tries) {
        val p = center + rng.unitVector() * (radius * rng.nextDouble())
        if (rock.sdf.dist(p) > 12) continue
        val q = rock.sdf.project(p, 0.0)
        if (kotlin.math.abs(rock.sdf.dist(q)) > 0.15) continue
        val n = rock.sdf.gradient(q)
        if (n.y < minNormalY) continue
        if (!rock.inWater(q, 1.5)) continue
        // open water in front of the spot (not the inside of a narrow hole or crevice)
        if (rock.sdf.dist(q + n * 3.0) < 2.4 || rock.sdf.dist(q + n * 1.0) < 0.8) continue
        return q to n
    }
    return null
}

fun Ctx.anchorOf(name: String): Anchor? = rock.anchors[name]

/** Standoff point in front of an anchor along its normal (or straight up). */
fun Ctx.standoff(a: Anchor, d: Double): Vec3 = a.pos + (a.normal ?: Vec3.Y).normalized(Vec3.Y) * d

fun Ctx.isPrepared(f: FoodParticle) = !f.kind.suspended

/** Nearest diet-matching food this animal accepts within [radius], optionally with a line-of-sight check. */
fun Ctx.findFood(radius: Double, los: Boolean = true, extra: (FoodParticle) -> Boolean = { true }): FoodParticle? {
    val f = food.nearest(o.pos, radius) { f ->
        o.species.dietMatch(f.kind) > 0 && (o.acceptsPrepared || f.kind.suspended) && extra(f) && Perceive.sensesFood(this, f)
    } ?: return null
    if (los && !rock.sdf.segmentClear(o.pos, f.pos, 0.2)) {
        // try the second nearest without LOS cost explosion: accept if near
        return if (f.pos.distanceTo(o.pos) < 6) f else null
    }
    return f
}

/** Take a bite: particle consumed, hunger reduced by its grams (diet match scaled). */
fun Ctx.bite(f: FoodParticle) {
    if (f.eaten) return
    food.consume(f)
    o.eat(f.grams * o.species.foodPreference(f.kind).coerceAtLeast(0.5))
    o.counters.add(com.reeflive.core.sim.Counters.K.FEED, t)
    o.mouth = 1.0
    sim.agentOf(o)?.brain?.mind?.onBite(t)
}

/** Moving an anchor-relative rest into a kinematic placement. */
fun SteerIntent.rest(r: Rest, rate: Double = 4.0) {
    place(r.pos, r.heading, r.up, rate)
    posture = r.posture
    splineOverride = r.spline
}

fun headingTo(from: Vec3, to: Vec3, fallback: Vec3 = Vec3.X): Vec3 = (to - from).normalized(fallback)

fun horizontalHeading(v: Vec3, fallback: Vec3 = Vec3.X): Vec3 = Vec3(v.x, 0.0, v.z).normalized(fallback)

fun minOfD(a: Double, b: Double) = min(a, b)
