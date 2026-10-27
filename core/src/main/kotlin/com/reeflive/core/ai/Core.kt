package com.reeflive.core.ai

import com.reeflive.core.geom.Rng
import com.reeflive.core.geom.SpatialHash
import com.reeflive.core.geom.Vec3
import com.reeflive.core.geom.smoothstep
import com.reeflive.core.sim.BrainKind
import com.reeflive.core.sim.Organism
import com.reeflive.core.sim.RockData
import com.reeflive.core.sim.Simulation

/**
 * What a behavior asks Steering to do this tick (SIMULATION §4.1 layer 3). Reset every tick.
 */
class SteerIntent {
    var target: Vec3? = null
    /** Desired speed in cm/s toward [target] (Steering caps at cruise or burst). */
    var speed = 0.0
    var arriveRadius = 2.0
    /** 0..1; > 0.7 allows burst speed and faster turning. */
    var urgency = 0.0
    var faceDir: Vec3? = null
    /** Hold station against the current (labriform hoverers). */
    var hover = false
    var wander = 0.0
    var fleeFrom: Vec3? = null
    var allowGlass = false
    var separation = 1.0
    /** Clown waddle: lateral sinusoid amplitude (BL/s) and frequency (Hz). */
    var waddleAmp = 0.0
    var waddleHz = 1.3
    var pitchBias = 0.0
    var rollExtra = 0.0
    var ampMul = 1.0
    var pectoral: Double? = null
    var tailMul = 1.0
    /** Kinematic placement (attached postures): Steering eases pos toward [kinPos]. */
    var kinematic = false
    var kinPos: Vec3? = null
    var kinHeading: Vec3? = null
    var kinUp: Vec3? = null
    var kinRate = 6.0
    var upOverride: Vec3? = null
    /** Benthic crawlers: move along the nearest surface at [surfaceOffset] cm. */
    var crawl = false
    var surfaceOffset = 0.5
    /** Crabs: crawl with the body at right angles to the direction of travel. */
    var sideways = false
    /**
     * Radial crawlers (brittle star): > 0 = number of arms. The body never turns; the travel direction is led by
     * whichever arm points closest to it, so the heading jumps by whole arm steps (research 11 §4, Astley 2012).
     */
    var armGait = 0
    /** Spline override polyline (den / hole). */
    var splineOverride: List<Vec3>? = null
    var posture = 0
    var ignoreRock = false
    /**
     * Swimmers: largest |heading.y| this tick (null = the default 0.57, ~35°). Hoverers keep the body level and change
     * depth with the fins (the pajama cardinal), instead of pointing nose-up or nose-down at every small move.
     */
    var maxPitch: Double? = null

    fun reset() {
        target = null; speed = 0.0; arriveRadius = 2.0; urgency = 0.0; faceDir = null; hover = false; wander = 0.0
        fleeFrom = null; allowGlass = false; separation = 1.0; waddleAmp = 0.0; waddleHz = 1.3; pitchBias = 0.0
        rollExtra = 0.0; ampMul = 1.0; pectoral = null; tailMul = 1.0; kinematic = false; kinPos = null; kinHeading = null
        kinUp = null; kinRate = 6.0; upOverride = null; crawl = false; surfaceOffset = 0.5; sideways = false; armGait = 0; splineOverride = null
        posture = 0; ignoreRock = false; maxPitch = null
    }

    fun seek(t: Vec3, speed: Double, arrive: Double = 2.0, urgency: Double = 0.3) {
        target = t; this.speed = speed; arriveRadius = arrive; this.urgency = urgency
    }

    fun place(pos: Vec3, heading: Vec3, up: Vec3? = null, rate: Double = 6.0) {
        kinematic = true; kinPos = pos; kinHeading = heading; kinUp = up; kinRate = rate
    }
}

data class Tap(val point: Vec3, val time: Double, val night: Boolean)

/** A cleaning station run by a cleaner wrasse or cleaner shrimp (SIMULATION §4.5, research 04 §13C). */
class CleaningStation(val cleaner: Organism, var pos: Vec3, val isShrimp: Boolean) {
    var client: Organism? = null
    var clientReady = false
    var cleaningSince = 0.0
    var advertising = false
    val open: Boolean get() = cleaner.alive && !cleaner.sleeping && !cleaner.molting

    /** Willingness weight used by clients to choose a station (hungry cleaners advertise harder). */
    val fitness: Double get() = (0.5 + 0.5 * cleaner.hunger) * cleaner.health * (if (isShrimp) 0.8 else 1.0)

    fun release() { client = null; clientReady = false }
}

/**
 * Shared per-tick perception (SIMULATION §4.2): spatial hash, light state, taps, stations.
 */
class Perception(val sim: Simulation) {
    val hash = SpatialHash<Organism>(15.0) { it.pos }
    var t = 0.0
    var light = 0.0
    var night = false
    var lightsOnAt = -1e9
    var lightsOffAt = -1e9
    val taps = ArrayList<Tap>()
    val stations = ArrayList<CleaningStation>()
    val rock: RockData get() = sim.rock

    fun rebuild() {
        hash.rebuild(sim.organisms.filter { it.alive && it.visible })
        taps.removeAll { t - it.time > 60 }
        stations.removeAll { !it.cleaner.alive || it.cleaner !in sim.organisms }
    }

    fun neighbors(p: Vec3, r: Double): List<Organism> = hash.neighbors(p, r)

    /** Activity curve (SIMULATION §4.4). */
    fun activity(o: Organism): Double {
        val day = smoothstep(0.05, 0.3, light)
        // (compared ignoring case in place: an uppercase() copy per call was a string per animal per tick)
        val a = o.species.activity
        return when {
            a.equals("NOCTURNAL", ignoreCase = true) -> 1 - day * 0.85
            a.equals("CREPUSCULAR", ignoreCase = true) -> {
                val dim = if (light in 0.03..0.35) 1.0 else 0.0
                if (dim > 0) 1.0 else if (night) 0.7 else 0.5
            }
            else -> day
        }
    }

    fun recentTap(o: Organism, within: Double, radius: Double): Tap? =
        taps.lastOrNull { t - it.time <= within && it.point.distanceTo(o.pos) <= radius }

    fun nearestOfKind(o: Organism, kind: BrainKind, radius: Double): Organism? =
        sim.organisms.filter { it !== o && it.alive && it.brainKind == kind && it.pos.distanceTo(o.pos) <= radius }
            .minByOrNull { it.pos.distanceTo(o.pos) }
}

/** Per-organism behavior context, reused every tick. */
class Ctx(val sim: Simulation, val o: Organism) {
    lateinit var p: Perception
    var dt = 0.05
    val t: Double get() = p.t
    val rng: Rng get() = o.rng
    val rock: RockData get() = sim.rock
    val L: Double get() = o.lengthCm
    val activity: Double get() = p.activity(o)
    fun anchor(name: String, fallback: Vec3) = rock.anchors.pos(name, fallback)
}

/**
 * Utility behavior (SIMULATION §4.1 layer 2): [score] 0..1, small state machine via
 * [enter]/[update]/[exit]. [finished] lets the brain switch even without the hysteresis margin.
 */
abstract class Behavior(val name: String, label: String) {
    /** UI label; open so a behavior can describe its current phase (heat stages). */
    open val label: String = label
    /** Verb phrase used in captions when the behavior has a focus organism ("chasing", "cleaning"…). */
    open val targetVerb: String? get() = null
    var finished = false
    var elapsed = 0.0
    var enteredAt = 0.0
    /** While true the brain will not preempt this behavior except for score ≥ 0.95 (flee, dead). */
    open val committed: Boolean get() = false
    /**
     * False for a behavior that is idle almost always (a reaction to the user): it gets no utility noise, so adding it
     * draws no rng and leaves every other random sequence exactly as it was.
     */
    open val noisy: Boolean get() = true

    abstract fun score(c: Ctx): Double
    open fun enter(c: Ctx) {}
    abstract fun update(c: Ctx, out: SteerIntent)
    open fun exit(c: Ctx) {}
}

class UtilityBrain(val behaviors: List<Behavior>) {
    var current: Behavior? = null; private set
    val currentLabel: String get() = current?.label ?: ""
    val hysteresis = 0.12
    private var lastScores = DoubleArray(behaviors.size)
    /** Ornstein–Uhlenbeck noise per behavior (τ 20 s, amplitude = the individual's whim). */
    private val noise = DoubleArray(behaviors.size)

    fun scores(): Map<String, Double> = behaviors.mapIndexed { i, b -> b.name to lastScores[i] }.toMap()

    /** The last tick's (noisy) score of behaviour [i] of [behaviors]; read-only (the thought readout's "next" plan). */
    fun lastScore(i: Int): Double = lastScores[i]

    fun tick(c: Ctx, intent: SteerIntent) {
        var best: Behavior? = null
        var bestScore = -1.0
        var curScore = -1.0
        val k = c.dt / NOISE_TAU
        val amp = c.o.whim * kotlin.math.sqrt(2 * k)
        // (an index loop: withIndex() boxed an IndexedValue per behaviour per animal per tick)
        for (i in behaviors.indices) {
            val b = behaviors[i]
            if (b.noisy) noise[i] += -noise[i] * k + amp * c.rng.gaussian()
            val raw = b.score(c)
            // stochastic utility: only nudges ordinary scores; urgent ones (≥ 0.95) and zeros stay exact
            val s = if (raw > 0.0 && raw < 0.95) (raw + noise[i]).coerceIn(0.01, 0.94) else raw
            lastScores[i] = s
            if (b === current) curScore = s
            if (s > bestScore) { bestScore = s; best = b }
        }
        val cur = current
        val switch = when {
            cur == null -> true
            cur.finished -> true
            best === cur -> false
            cur.committed -> bestScore >= 0.95 && curScore < 0.95
            else -> bestScore > curScore + hysteresis
        }
        if (switch && best != null && (best !== cur || cur.finished)) {
            cur?.exit(c)
            c.o.focusId = null
            best.finished = false
            best.elapsed = 0.0
            best.enteredAt = c.t
            current = best
            best.enter(c)
        }
        current?.let { it.elapsed += c.dt; it.update(c, intent) }
    }

    fun force(c: Ctx, b: Behavior) {
        current?.exit(c)
        b.finished = false; b.elapsed = 0.0; b.enteredAt = c.t
        current = b
        b.enter(c)
    }

    companion object { const val NOISE_TAU = 20.0 }
}
