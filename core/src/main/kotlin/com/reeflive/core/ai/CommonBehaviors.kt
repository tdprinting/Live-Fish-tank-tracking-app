package com.reeflive.core.ai

import com.reeflive.core.geom.Vec3
import com.reeflive.core.geom.smoothstep
import com.reeflive.core.sim.BrainKind
import com.reeflive.core.sim.Disease
import com.reeflive.core.sim.Ev
import com.reeflive.core.sim.Evidence
import com.reeflive.core.sim.FoodKind
import com.reeflive.core.sim.FoodParticle
import com.reeflive.core.sim.SimEvent
import com.reeflive.core.sim.Symptom
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * Common behaviors (SIMULATION §4.4). Each takes its brain so it can read shared perception
 * state (startle, threat, food alerts). Scores follow the §4.4 table; species brains wrap or
 * re-weight them.
 */

/** Idle: hover at a jittered point within the home radius, facing the flow. */
open class HoverHome(val b: SpeciesBrain, val center: (Ctx) -> Vec3, val radius: (Ctx) -> Double, label: String = "Hovering") : Behavior("hover", label) {
    private var spot: Vec3 = Vec3.ZERO
    private var next = 0.0
    override fun score(c: Ctx): Double {
        val home = center(c)
        val r = max(radius(c), 4.0)
        return 0.2 + 0.3 * (1 - c.o.hunger) + 0.3 * (c.o.pos.distanceTo(home) / r).coerceAtMost(1.0)
    }
    override fun enter(c: Ctx) { next = 0.0 }
    override fun update(c: Ctx, out: SteerIntent) {
        if (c.t > next) {
            next = c.t + c.rng.range(4.0, 10.0)
            spot = c.safe(center(c) + c.rng.unitVector().let { Vec3(it.x, it.y * 0.5, it.z) } * (radius(c) * c.rng.nextDouble()))
        }
        out.seek(spot, c.o.cruiseSpeed * 0.6, arrive = max(2.0, c.L))
        out.hover = true
        out.wander = 0.1
        val fl = c.sim.flow.velocityFast(c.o.pos)
        if (fl.length() > 1.0 && c.o.vel.length() < 0.4 * c.o.cruiseSpeed) out.faceDir = (fl * -1.0).normalized()
    }
}

/** Wander / patrol with zone bias, waypoint every 4–10 s. */
open class Wander(val b: SpeciesBrain, val weight: Double = 0.35, label: String = "Exploring") : Behavior("wander", label) {
    private var wp: Vec3 = Vec3.ZERO
    private var next = 0.0
    override fun score(c: Ctx) = c.o.species.zone.openWater * weight * c.activity + 0.05
    override fun enter(c: Ctx) { next = 0.0 }
    override fun update(c: Ctx, out: SteerIntent) {
        val z = c.o.species.zone
        if (c.t > next || c.o.pos.distanceTo(wp) < c.L) {
            next = c.t + c.rng.range(4.0, 10.0)
            val home = c.o.homePos
            val hr = max(c.o.species.homeRadiusCm, 15.0)
            val p = Vec3(home.x + c.rng.signed() * hr, c.zoneY(c.rng.range(z.yMin, z.yMax)), c.rng.range(4.0, c.rock.widthCm - 4))
            wp = c.safe(p)
        }
        out.seek(wp, c.o.cruiseSpeed, arrive = c.L * 1.5)
        out.wander = 0.3
    }
}

/**
 * Feed (SIMULATION §4.4/§4.6): chase the nearest reachable particle, bite within 1.2 cm.
 * Frenzy: on a fresh feed every diet-matching fish with hunger > 0.2 scores ≥ 0.6.
 */
open class Feed(val b: SpeciesBrain, val radius: Double = 80.0, label: String = "Feeding") : Behavior("feed", label) {
    private val baseLabel = label
    protected var target: FoodParticle? = null
    /** Displacement by rank (06 §3.2): the higher-ranked fish it gave way to, and until when it keeps clear of it. */
    private var yieldTo: com.reeflive.core.sim.Organism? = null
    private var yieldUntil = -1.0
    /** Waiting at the edge of the dominant fish's feeding spot (no other food in reach). */
    private var waiting = false
    /** The particle a contest was last looked at for (the yield roll for a pair and particle is fixed: [contestRoll]). */
    private var contested: FoodParticle? = null
    private var contestAt = 0.0
    override val label: String get() = if (waiting) "Waiting its turn at the food" else baseLabel
    override val targetVerb: String? get() = if (waiting) "giving way to" else null
    private var retarget = 0.0
    private var lastFood = 0.0
    private var cachedScoreAt = -1.0
    private var cached: FoodParticle? = null
    /** Progress guard: a particle it cannot reach (pinned between rock and glass) is given up on for a while. */
    private var bestD = Double.MAX_VALUE
    private var progressAt = 0.0
    private var giveUpUntil = -1.0
    private var bestFor: FoodParticle? = null
    open fun accept(c: Ctx, f: FoodParticle): Boolean = true
    open fun bonus(c: Ctx, f: FoodParticle): Double = 0.0
    open fun scoreMul(c: Ctx, f: FoodParticle): Double = 1.0

    protected fun candidate(c: Ctx): FoodParticle? {
        if (c.t - cachedScoreAt < 0.4 && cached?.eaten == false) return cached
        cachedScoreAt = c.t
        // a roused fish follows the scent round the rock out of its cave or burrow, so no line of sight is needed
        val r = roused(c)
        cached = c.findFood(radius, los = !r, roused = r) { accept(c, it) }
        return cached
    }

    override fun score(c: Ctx): Double {
        val o = c.o
        if (o.hunger < 0.12 || c.t < giveUpUntil) return 0.0
        val f = candidate(c) ?: return 0.0
        val match = o.species.dietMatch(f.kind)
        val act = max(c.activity, 0.4)
        var s = 0.5 + 0.5 * o.hunger * match * act + bonus(c, f)
        if (c.t - b.foodAlertAt < 30 && o.hunger > 0.2) s = max(s, 0.62)
        // food dropped after dark rouses the day fish that saw it from their night rest (Tee's moonlight feeding clip:
        // tang, clowns, cleaner wrasse and both gobies came out to eat; the hawkfish, blennies and leopard wrasse stayed
        // put). The floor clears every night-rest score (0.7-0.85); once the food is gone the rest wins again.
        val app = com.reeflive.core.sim.HealthModel.appetite(o)
        if (app < 0.3) return 0.0
        // a frightened animal comes to food less eagerly (Mind.kt X4; 1 when calm)
        val night = if (roused(c) && o.hunger > 0.2) NIGHT_WAKE[o.brainKind]!! * (NIGHT_WAKE_FLOOR + 0.07 * o.hunger) * app * b.mind.mood.feedFactor else 0.0
        return max(s * scoreMul(c, f) * app * b.mind.mood.feedFactor, night).coerceAtMost(0.93)
    }

    override fun enter(c: Ctx) { target = null; lastFood = c.t; retarget = 0.0; bestFor = null; waiting = false }

    override fun exit(c: Ctx) { c.o.feedingOn = null; waiting = false }

    protected fun roused(c: Ctx): Boolean = rousedBy(c, b)

    companion object {
        /** No closer by 1 cm in this long → the food is out of reach; ignore food for [GIVE_UP_S] (DESIGN ESTIMATES). */
        const val STUCK_S = 8.0
        const val GIVE_UP_S = 20.0
        /** How long after a night feeding the food keeps the day fish up (owner clip IMG_0106: ~2.5 min of feeding). */
        const val NIGHT_WAKE_S = 150.0
        const val NIGHT_WAKE_FLOOR = 0.86
        /** How readily food after dark wakes each kind, 0..1 ([H] tee_tank_night_feeding.md; unlisted kinds stay asleep). */
        val NIGHT_WAKE = mapOf(BrainKind.TANG to 1.0, BrainKind.CLOWN to 1.0, BrainKind.WRASSE to 1.0,
            BrainKind.ENGINEER_GOBY to 1.0, BrainKind.DIAMOND_GOBY to 1.0)

        /** Night, a feeding within [NIGHT_WAKE_S], and a kind that wakes for it ([NIGHT_WAKE]). */
        fun rousedBy(c: Ctx, b: SpeciesBrain): Boolean =
            c.p.night && c.t - b.foodAlertAt < NIGHT_WAKE_S && (NIGHT_WAKE[c.o.brainKind] ?: 0.0) > 0

        /** Multiplier on a night-rest score: it steps aside while a night feeding has a hungry fish up. */
        fun nightRestDamp(c: Ctx, b: SpeciesBrain): Double = if (c.o.hunger > 0.2 && rousedBy(c, b)) 0.6 else 1.0

        /** Two fish go for the same particle when their targets are this close (06 §3.2: "within 2 cm"). */
        @Ev(Evidence.DESIGN_ESTIMATE, "06 §3.2") const val CONTEST_CM = 2.0
        /** The lower-ranked fish gives way this often (06 §3.2: 70 %) [I]. */
        @Ev(Evidence.DESIGN_ESTIMATE, "06 §3.2") const val YIELD_P = 0.7
        /** Ranks closer than this do not displace (two equals both go for it) [I]. */
        const val RANK_MARGIN = 0.5
        /** A rival counts only when it is this close to the particle (cm, plus 2 body lengths): it is at the food too [I]. */
        const val CONTEST_REACH_CM = 10.0
        /** A contest is looked at again this often (s): the rival may only now have gone for the same particle. */
        const val CONTEST_EVERY_S = 0.5
        /** Seconds a displaced fish keeps clear of the dominant one's spot before it tries again [I]. */
        const val YIELD_S = 4.0
        /** Radius of the dominant fish's spot: this (cm) plus 1.5 of its body lengths; the yielder waits at its edge [I]. */
        const val SPOT_CM = 5.0

        /** The spot a dominant [d] holds: food inside it is left to [d] while a yield lasts. */
        fun spotRadius(d: com.reeflive.core.sim.Organism): Double = SPOT_CM + 1.5 * d.lengthCm

        /** A holder at (or near) its own shelter keeps its nucleus rank; elsewhere it drops two places (06 §3.1). */
        fun feedRank(o: com.reeflive.core.sim.Organism, sim: com.reeflive.core.sim.Simulation): Double =
            PairMatrix.rankEff(o, sim, atNucleus = o.homePos == Vec3.ZERO || o.pos.distanceTo(o.homePos) < NUCLEUS_CM)
        const val NUCLEUS_CM = 25.0

        /**
         * Deterministic 0..1 roll for the contest of [o] over particle [f] (a hash, not the brain's random stream: the
         * contest must leave every existing random sequence as it was).
         */
        fun contestRoll(o: com.reeflive.core.sim.Organism, f: FoodParticle): Double {
            var h = o.id.hashCode() * 0x9E3779B1.toInt() xor (f.id * 0x85EBCA6B.toInt())
            h = h xor (h ushr 16); h *= 0x7FEB352D; h = h xor (h ushr 15); h *= 0x846CA68B.toInt(); h = h xor (h ushr 16)
            return (h ushr 8) / 16777216.0
        }
    }

    /**
     * The higher-ranked fish [o] gives way to at particle [f] (06 §3.2), or null: a tankmate going for a particle within
     * [CONTEST_CM] of [f], at the food itself, ranked at least [RANK_MARGIN] above [o]; [o] yields [YIELD_P] of the time.
     */
    private fun displacer(c: Ctx, f: FoodParticle): com.reeflive.core.sim.Organism? {
        val o = c.o
        if (!o.isFish) return null
        var best: com.reeflive.core.sim.Organism? = null
        var bestRank = 0.0
        var myRank = Double.NaN
        for (n in c.p.neighbors(f.pos, CONTEST_REACH_CM + 30.0)) {
            if (n === o || !n.alive || !n.isFish) continue
            val nf = n.feedingOn ?: continue
            if (nf.eaten || nf.pos.distanceTo(f.pos) > CONTEST_CM) continue
            if (n.pos.distanceTo(f.pos) > CONTEST_REACH_CM + 2 * n.lengthCm) continue
            if (myRank.isNaN()) myRank = feedRank(o, c.sim)
            val r = feedRank(n, c.sim)
            if (r >= myRank + RANK_MARGIN && (best == null || r > bestRank)) { best = n; bestRank = r }
        }
        return best?.takeIf { contestRoll(o, f) < YIELD_P }
    }

    override fun update(c: Ctx, out: SteerIntent) {
        val o = c.o
        retarget -= c.dt
        var f = target
        if (f == null || f.eaten || retarget <= 0) {
            f = candidate(c); target = f; retarget = 0.5
        }
        if (f == null) {
            if (c.t - lastFood > 3) finished = true
            out.hover = true; out.wander = 0.2
            o.feedingOn = null; waiting = false
            return
        }
        lastFood = c.t
        // displacement by rank (06 §3.2): a fish going for a particle a higher-ranked tankmate is taking gives way, keeps
        // clear of its spot for a few seconds and takes food elsewhere, or waits at the edge of the spot for its turn
        if (c.sim.feedDisplacement && (f !== contested || c.t >= contestAt)) {
            contested = f; contestAt = c.t + CONTEST_EVERY_S
            val d = displacer(c, f)
            if (d != null) {
                if (yieldTo !== d || c.t >= yieldUntil) {
                    o.counters.add(com.reeflive.core.sim.Counters.K.DISPLACED, c.t)
                    d.counters.add(com.reeflive.core.sim.Counters.K.DISPLACE_GIVEN, c.t)
                }
                yieldTo = d; yieldUntil = c.t + YIELD_S
            }
        }
        val y = yieldTo?.takeIf { c.t < yieldUntil && it.alive }
        if (y == null) yieldTo = null
        if (y != null && f.pos.distanceTo(y.pos) < spotRadius(y)) {
            val r = roused(c)
            val alt = c.findFood(radius, los = !r, roused = r) { accept(c, it) && it.pos.distanceTo(y.pos) >= spotRadius(y) }
            if (alt != null) { f = alt; target = alt; contested = alt }
            else {
                waitAtEdge(c, out, y, f)
                return
            }
        }
        waiting = false
        o.feedingOn = f
        val d = o.pos.distanceTo(f.pos)
        if (f !== bestFor) { bestFor = f; bestD = d; progressAt = c.t }
        else if (d < bestD - 1.0) { bestD = d; progressAt = c.t }
        if (c.t - progressAt > STUCK_S) { giveUpUntil = c.t + GIVE_UP_S; finished = true; return }
        val reach = 1.2 + 0.1 * o.lengthCm
        // the mouth is half a body length ahead of the body centre: a particle settled on rock is picked off from as
        // close as the rock clearance lets the body come (a wrasse stalled 4 cm short of mysis on a ledge, never eating)
        val mouthD = (o.pos + o.heading * (0.5 * o.lengthCm)).distanceTo(f.pos)
        if (d < reach || mouthD < reach) {
            c.bite(f)
            target = null
            if (o.hunger < 0.05) finished = true
            return
        }
        val fast = o.hunger > 0.4 && (o.brainKind == BrainKind.CLOWN || o.brainKind == BrainKind.TANG || o.brainKind == BrainKind.HAWK || o.brainKind == BrainKind.DOTTYBACK)
        out.seek(f.pos, if (fast) o.burstSpeed * 0.6 else o.cruiseSpeed * 1.8, arrive = reach, urgency = if (fast) 0.75 else 0.5)
        out.faceDir = (f.pos - o.pos).normalized(o.heading)
        out.separation = 0.4
        if (d < 4) o.mouth = max(o.mouth, 0.5)
    }

    /** Hold station at the edge of [y]'s spot, on this fish's side, watching the food [f] (06 §3.2). */
    private fun waitAtEdge(c: Ctx, out: SteerIntent, y: com.reeflive.core.sim.Organism, f: FoodParticle) {
        val o = c.o
        waiting = true
        o.feedingOn = null
        o.focusId = y.id
        // waiting is not being stuck: the progress guard restarts when it goes for food again
        bestFor = null; progressAt = c.t
        val away = (o.pos - y.pos).let { v -> Vec3(v.x, 0.3 * v.y, v.z) }.normalized(o.heading * -1.0)
        val edge = c.safe(y.pos + away * (spotRadius(y) + 1.0), 1.0)
        out.seek(edge, o.cruiseSpeed, arrive = 2.0)
        out.hover = true
        out.faceDir = (f.pos - o.pos).normalized(o.heading)
        out.separation = 0.4
    }
}

/**
 * Nibble the nori sheet on its clip (grazers; SIMULATION §4.5 tang, §4.6 [com.reeflive.core.sim.NoriCloth]). The sheet
 * streams downstream in the current, so a grazer holds station on its downstream side facing up-current (the way fish
 * hold in flow and pick at drifting weed) and bites at the free trailing edge: a tang takes a mouthful and tugs back
 * with a head shake, tearing it; a lawnmower blenny rasps quick small bites in short bouts. Bites tear ragged edges
 * that break off and drift away as food. [quiet]: no utility noise while there is no sheet (added to a brain whose
 * random sequence must stay as it was).
 */
class NibbleNori(val b: SpeciesBrain, val weight: Double = 1.0, private val quiet: Boolean = false) : Behavior("nori", "Eating nori") {
    override val noisy: Boolean get() = !quiet || b.c.food.nori != null
    private var nextBite = 0.0
    private var noriLimit = 120.0
    private var tugUntil = -1.0
    private var boutLeft = 0
    private var bestD = Double.MAX_VALUE
    private var progressAt = 0.0
    private var giveUpUntil = -1e9
    private var cell = -1
    private var repickAt = 0.0
    private val rasper: Boolean get() = b.o.brainKind == BrainKind.LAWNMOWER_BLENNY
    override fun enter(c: Ctx) { noriLimit = c.rng.range(120.0, 300.0); nextBite = c.t + 0.4; tugUntil = -1.0; boutLeft = 0; bestD = Double.MAX_VALUE; progressAt = c.t; cell = -1; repickAt = 0.0 }

    /**
     * Present cells from the most downstream back, the first whose station ([back] cm downstream of it) is open water
     * (the clearance check moves it less than 1.5 cm). When rock or glass crowds every station (a sheet hanging close
     * to the rockwork), the cell whose station is moved least while staying on its downstream side, so the grazer still
     * comes in from downstream and snouts in where it can; −1 only when no cell can be reached from downstream at all.
     */
    private fun pickCell(c: Ctx, cloth: com.reeflive.core.sim.NoriCloth, down: Vec3, back: Double): Int {
        val order = (0 until cloth.cellCount).filter { cloth.present(it) }.sortedByDescending { (cloth.cellCentre(it) dot down) + (if (cloth.isEdge(it)) 1.5 else 0.0) }
        var fallback = -1
        var fallbackMove = Double.MAX_VALUE
        for (k in order) {
            val at = cloth.cellCentre(k)
            val want = at + down * back
            val got = c.safe(want)
            val moved = got.distanceTo(want)
            if (moved < 1.5) return k
            if (((got - at) dot down) > 0.25 * back && moved < fallbackMove) { fallback = k; fallbackMove = moved }
        }
        return fallback
    }
    override fun score(c: Ctx): Double {
        val n = c.food.nori ?: return 0.0
        if (n.remaining <= 0.02 || c.o.hunger < 0.1 || c.t < giveUpUntil) return 0.0
        return ((0.55 + 0.4 * c.o.hunger) * weight * max(c.activity, 0.3)).coerceAtMost(0.9)
    }
    override fun update(c: Ctx, out: SteerIntent) {
        val o = c.o
        val n = c.food.nori ?: run { finished = true; return }
        val cloth = n.cloth
        val mid = cloth.centroid()
        // downstream at the sheet: where the water takes it; in still water the open side away from the glass
        val w = c.sim.flow.velocity(mid, c.t)
        val flowDir = if (w.length() > 1.5) w.normalized() else cloth.inward
        // a current pressing the sheet onto its pane leaves no room behind it: come in along the glass instead
        val into = flowDir dot cloth.inward
        val down = if (into < 0.0) (flowDir - cloth.inward * into).normalized(cloth.inward) else flowDir
        val tugging = c.t < tugUntil
        val back = 0.5 * c.L + (if (tugging) 1.4 else 0.4)
        // the cell to go for: the most downstream piece of edge it can hold station behind (re-picked each second)
        if (c.t >= repickAt || cell < 0 || !cloth.present(cell)) { cell = pickCell(c, cloth, down, back); repickAt = c.t + 1.0 }
        if (cell < 0) { giveUpUntil = c.t + GIVE_UP_S; finished = true; return }
        val at = cloth.cellCentre(cell)
        val station = c.safe(at + down * back)
        out.seek(station, o.cruiseSpeed * 1.5, arrive = 1.0, urgency = 0.4)
        out.allowGlass = true; out.hover = true
        out.separation = 0.5
        // nose up-current into the sheet
        out.faceDir = (at - o.pos).normalized(down * -1.0)
        if (tugging) { out.rollExtra = 0.25 * sin(c.t * 30.0); o.mouth = 1.0 }
        val dist = o.pos.distanceTo(station)
        // blocked on the way in (rock, a tankmate): give up for now rather than hang there
        if (dist < bestD - 0.5) { bestD = dist; progressAt = c.t }
        if (c.t - progressAt > STUCK_S) { giveUpUntil = c.t + GIVE_UP_S; finished = true; return }
        // in reach: at its station, or (where rock keeps it from squaring up) snout to the sheet from its downstream side
        val close = dist < 1.6 + 0.1 * c.L || o.pos.distanceTo(at) < 0.5 * c.L + 2.0 && ((o.pos - at) dot down) > 0.0
        if (close) progressAt = c.t
        if (close && c.t > nextBite) {
            val share = NORI_BITE * c.L / 8
            if (rasper) {
                // comb-toothed rasping: 4–8 quick small bites, then a look round (research 10 §6 rasping bites)
                if (boutLeft <= 0) boutLeft = 4 + c.rng.nextInt(5)
                boutLeft--
                n.bite(o.pos, at, share * 0.45, Vec3.ZERO, down)
                nextBite = c.t + if (boutLeft > 0) 0.35 else c.rng.range(1.8, 3.5)
                o.hunger = max(0.0, o.hunger - 0.012)
            } else {
                // a mouthful, then the head shake that tears it off: the bitten edge is pulled downstream
                n.bite(o.pos, at, share, down * (6.0 + 0.6 * c.L), down)
                tugUntil = c.t + 0.35
                nextBite = c.t + c.rng.range(1.1, 1.9)
                o.hunger = max(0.0, o.hunger - 0.025)
            }
            o.nutritionGreens = 1.0
            o.mouth = 1.0
            out.pitchBias = 0.15
        }
        if (o.hunger < 0.05 || elapsed > noriLimit) finished = true
    }

    companion object {
        /** Share of a whole sheet one bite of an 8 cm fish takes (scales with length). */
        const val NORI_BITE = 0.004
        /** Seconds without getting closer before it gives up for a while, and how long that while is. */
        const val STUCK_S = 8.0
        const val GIVE_UP_S = 60.0
    }
}

/** Graze hair algae on rock (grazers): nose to rock, rasps every 1.5 s. */
open class Graze(val b: SpeciesBrain, val weight: Double = 1.0, label: String = "Grazing") : Behavior("graze", label) {
    /** Whether the rock spot at [p] (outward [n]) will do; a species can refuse spots its body would not fit at. */
    open fun accept(c: Ctx, p: Vec3, n: Vec3): Boolean = true
    private var spot: Vec3 = Vec3.ZERO
    private var normal: Vec3 = Vec3.Y
    private var nextRasp = 0.0
    private var dur = 20.0
    override fun score(c: Ctx): Double {
        if (!c.o.species.grazer) return 0.0
        val hair = c.sim.water.s.algaeHair
        return ((0.3 + 0.5 * c.o.hunger * (0.4 + hair * 3).coerceAtMost(1.2)) * c.activity * weight).coerceAtMost(0.75)
    }
    override fun enter(c: Ctx) {
        val patches = c.rock.anchors.ofType("GRAZE")
        val near = patches.sortedBy { it.pos.distanceTo(c.o.pos) + c.rng.nextDouble() * 40 }.firstOrNull()
        val sp = near?.let { c.rockSurfacePoint(it.pos, 4.0) } ?: c.rockSurfacePoint(c.o.pos, 20.0)
        if (sp == null || !accept(c, sp.first, sp.second)) { finished = true; return }
        spot = sp.first; normal = sp.second
        dur = c.rng.range(10.0, 40.0)
    }
    override fun update(c: Ctx, out: SteerIntent) {
        val stand = spot + normal * (0.5 * c.o.collisionLength + 0.6)
        out.seek(stand, c.o.cruiseSpeed * 1.2, arrive = 1.5)
        out.faceDir = (normal * -1.0)
        out.hover = true
        if (c.o.pos.distanceTo(stand) < 2.5) {
            if (c.t > nextRasp) {
                nextRasp = c.t + 1.5
                c.o.hunger = max(0.0, c.o.hunger - 0.01)
                c.o.nutritionGreens = 1.0
                c.sim.addGrazing(c.sim.grazeRemovalPerMin(c.o) / 20)
                c.o.mouth = 1.0
            }
            out.pitchBias = 0.17 * sin(2 * PI * (c.t - nextRasp) / 1.5)
        }
        if (elapsed > dur) finished = true
    }
}

/**
 * Flee (SIMULATION §4.4/§5.6): burst away from a tap or predator toward cover, stay 10–40 s
 * scaled by skittishness. Small startles (≤ 0.35) only flinch for 0.4 s.
 */
open class Flee(val b: SpeciesBrain, val cover: (Ctx, Vec3) -> Vec3, label: String = "Startled") : Behavior("flee", label) {
    private var from: Vec3 = Vec3.ZERO
    private var dur = 10.0
    private var flinch = false
    private var hide: Vec3 = Vec3.ZERO
    override val committed: Boolean get() = elapsed < dur
    override fun score(c: Ctx): Double {
        b.threat?.let { if (it.alive) return 0.96 }
        if (b.startledRecently && b.startleMag > 0.05) return if (b.startleMag > 0.35) 0.95 else 0.7
        return 0.0
    }
    override fun enter(c: Ctx) {
        val t = b.threat
        from = t?.pos ?: b.startleFrom
        flinch = t == null && b.startleMag <= 0.35
        dur = if (flinch) 0.4 else c.rng.range(10.0, 40.0) * (0.4 + c.o.skittishness)
        hide = cover(c, from)
        // a refuge it has used before, when it is nearer than the species' own cover and not toward the threat: the
        // nearest familiar shelter (Mind.kt X2)
        if (!flinch && remembersRefuges) b.mind.refugeFor(c, from)?.let { r ->
            if (r.distanceTo(c.o.pos) < hide.distanceTo(c.o.pos)) hide = c.safe(r, 1.0)
        }
        reached = false
        if (!flinch) c.o.startle = max(c.o.startle, 0.15)
        b.startleAt = -1e9
    }
    private var reached = false
    /** The hawkfish ducks to a perch it picks itself ([HawkBrain.DuckToPerch]); everyone else may use a remembered refuge. */
    open val remembersRefuges: Boolean get() = b.o.brainKind != BrainKind.HAWK
    /** Where the last full flee was headed (tests). */
    val hideTarget: Vec3 get() = hide
    override fun update(c: Ctx, out: SteerIntent) {
        val t = b.threat
        if (t != null) from = t.pos
        if (flinch || elapsed < 0.8) {
            out.fleeFrom = from; out.urgency = 1.0
        } else {
            out.seek(hide, c.o.burstSpeed * 0.7, arrive = 2.0, urgency = 0.6)
            out.hover = true
            if (!reached && c.o.pos.distanceTo(hide) < 4.0) { reached = true; b.mind.onRefuge(c, hide) }
            if (t != null && t.pos.distanceTo(c.o.pos) < 6) { out.fleeFrom = t.pos; out.urgency = 1.0 }
        }
        if (elapsed > dur && (t == null || !t.alive)) finished = true
    }
}

/** Hide / rest / sleep at a species spot (SIMULATION §4.4, §4.7). */
open class Sleep(val b: SpeciesBrain, label: String = "Sleeping", val nightWeight: Double = 0.7) : Behavior("sleep", label) {
    override fun score(c: Ctx): Double {
        val o = c.o
        val sick = if (o.health < 0.6 || o.disease != null && o.diseaseStage > 0.3) 1.0 else 0.0
        return (nightWeight * (1 - c.activity) * Feed.nightRestDamp(c, b) + 0.25 * o.stress + 0.5 * sick * 0.6).coerceAtMost(0.9)
    }
    override fun update(c: Ctx, out: SteerIntent) {
        val r = b.restSpot(c, c.p.night)
        val d = c.o.pos.distanceTo(r.pos)
        if (r.posture != 0 && d < 3.0) {
            out.rest(r, 2.0)
            c.o.sleeping = c.p.night
        } else {
            out.seek(r.pos, c.o.cruiseSpeed * (if (d > 10) 0.8 else 0.3), arrive = 3.0)
            out.hover = true
            if (d < 4) { c.o.sleeping = c.p.night; out.faceDir = r.heading; out.ampMul = 0.3; out.pectoral = 0.5 }
        }
    }
}

/** Visit a cleaning station (wrasse or skunk shrimp): pose until cleaned (SIMULATION §4.4, 04 §13C). */
open class VisitCleaner(val b: SpeciesBrain, val extra: Double = 0.0) : Behavior("visit", "Visiting cleaner") {
    private var station: CleaningStation? = null
    /** A hawkfish that gave up hovering at the station goes back to its perch until then; any fish that could not reach
     *  its pose spot leaves the station for [RETRY_S] (see [update]). */
    private var retryAt = -1e9
    override fun score(c: Ctx): Double {
        val o = c.o
        if (c.t - o.lastCleanedAt < 600 || c.p.night || c.t < retryAt) return 0.0
        val st = choose(c) ?: return 0.0
        if (st.client != null && st.client !== o) return 0.0
        val ich = if (o.disease == Disease.ICH) o.diseaseStage * 3 else 0.0
        // a client the wrasse has not reached for a long time (e.g. one wedged in a crevice) solicits it
        val neglected = if (overdue(c)) 0.5 else 0.0
        return (0.15 + 0.6 * ich.coerceAtMost(1.0) + 0.5 * o.parasiteLoad + neglected + extra).coerceAtMost(0.85)
    }
    private var firstSeen = Double.NaN
    /** Not touched by a cleaner for [WrasseBrain.OVERDUE_S] (a fish never cleaned counts from when it was first simulated). */
    private fun overdue(c: Ctx): Boolean {
        if (firstSeen.isNaN()) firstSeen = c.t
        return c.t - maxOf(c.o.lastCleanerContactAt, firstSeen) > WrasseBrain.OVERDUE_S
    }
    private fun choose(c: Ctx): CleaningStation? =
        // the wrasse comes to its clients (06 §5.5); fish only seek out its station when infested with ich
        c.p.stations.filter { it.open && it.cleaner !== c.o && (!it.isShrimp || c.o.brainKind != BrainKind.HAWK) && (it.isShrimp || c.o.disease == Disease.ICH || overdue(c)) }
            .maxByOrNull { it.fitness / (1 + it.pos.distanceTo(c.o.pos) / 30) }
    override fun enter(c: Ctx) { station = choose(c) }
    override fun update(c: Ctx, out: SteerIntent) {
        val st = station
        if (st == null || !st.open) { finished = true; return }
        val o = c.o
        // no swim bladder: a hawkfish hovers only briefly, then waits on a perch for the cleaner to come to it
        val maxWait = if (o.brainKind == BrainKind.HAWK) HAWK_HOVER_S else GIVE_UP_S
        if (o.brainKind == BrainKind.HAWK && elapsed > maxWait) retryAt = c.t + HAWK_RETRY_S
        val pose = c.safe(st.pos + Vec3(0.0, 0.0, 1.0) * (o.lengthCm * 0.8 + 2))
        val d = o.pos.distanceTo(pose)
        if (st.client == null) st.client = o
        if (st.client !== o) {
            out.seek(c.viaOpenWater(c.safe(pose + Vec3(0.0, 4.0, 3.0))), o.cruiseSpeed * 0.6, arrive = 3.0); out.hover = true
            if (elapsed > min(40.0, maxWait)) finished = true
            return
        }
        out.seek(if (d > 8) c.viaOpenWater(pose) else pose, o.cruiseSpeed * (if (d > 8) 1.0 else 0.4), arrive = 3.0)
        out.hover = true
        if (d < 4) {
            st.clientReady = true
            out.pitchBias = 0.44; out.pectoral = 1.0; out.ampMul = 0.2
            o.mouth = 0.7
            o.finClamp = 0.0
        }
        if (c.t - o.lastCleanedAt < 2) { st.release(); finished = true }
        // a pose spot it cannot get to (a dottyback under a ledge, the climb out too tight for its steering) held
        // the station for 80 min, re-picked every 90 s, and the wrasse could not reach it there either: go about its
        // day and let the cleaner find it elsewhere
        if (elapsed > maxWait) {
            if (o.brainKind != BrainKind.HAWK && !(st.client === o && st.clientReady)) retryAt = c.t + RETRY_S
            if (st.client === o) st.release()
            finished = true
        }
    }

    companion object {
        /** Longest a hawkfish hovers at a station (s) and how long it then stays on its perch before trying again. */
        @Ev(Evidence.DESIGN_ESTIMATE, "Cirrhitidae lack a swim bladder: perch-bound, short hovering bouts") const val HAWK_HOVER_S = 12.0
        const val HAWK_RETRY_S = 300.0
        const val GIVE_UP_S = 90.0
        /** [I]: long enough for the fish to move on and meet the wrasse on its rounds (06 §5.6 #4 allows 90 min). */
        const val RETRY_S = 600.0
    }
    override fun exit(c: Ctx) {
        // handing over to the pose for this cleaner (it has started on the client) is not leaving: releasing the station
        // there stopped the shrimp's clean at its antenna tap, the pose lapsed, the fish came back to visit, and the two
        // looped every 1.5 s (a dottyback at the skunk shrimp for minutes)
        station?.let { if (it.client === c.o && !(c.o.poseFor === it.cleaner && c.o.poseUntil > c.t)) it.release() }
    }
}

/** Curious approach to a glass tap (clowns, tang, wrasse): hover at 4 cm for 5–15 s. */
class GlassCurious(val b: SpeciesBrain) : Behavior("glass", "Curious at the glass") {
    private var dur = 10.0
    private var point: Vec3 = Vec3.ZERO
    override fun score(c: Ctx): Double {
        val o = c.o
        val tap = c.p.taps.lastOrNull { c.t - it.time in 3.0..20.0 && it.point.distanceTo(o.pos) < 60 } ?: return 0.0
        if (o.curiosity <= 0.5 || o.stress > 0.3 || tap.night || b.mind.mood.fear > 0.3) return 0.0
        point = tap.point
        return 0.4 + 0.2 * o.curiosity
    }
    override fun enter(c: Ctx) { dur = c.rng.range(5.0, 15.0) }
    override fun update(c: Ctx, out: SteerIntent) {
        val inward = Vec3(0.0, 0.0, -1.0) * (if (point.z > c.rock.widthCm / 2) 1.0 else -1.0)
        val spot = c.safe(point + inward * 4.0, 1.0)
        out.seek(spot, c.o.cruiseSpeed, arrive = 2.0)
        out.allowGlass = true; out.hover = true
        out.faceDir = (point - c.o.pos).normalized(c.o.heading)
        if (elapsed > dur + 6) finished = true
    }
}

/**
 * Low DO: hover at the surface near the HOB outflow (score 0.95). Surfacing is an oxygen behaviour, not a
 * heat one (research 08 §3): it needs low O2 saturation and a fish still able to swim (heat stage ≤ GASPING).
 */
class SurfaceGasp(val b: SpeciesBrain) : Behavior("gasp", "Gasping at the surface") {
    private var lastEvent = -1e9
    override fun score(c: Ctx): Double {
        val o = c.o
        if (!com.reeflive.core.sim.HeatModel.surfaceAllowed(o, c.sim.water.s)) return 0.0
        val sDo = o.envStress["do"] ?: 0.0
        return if (sDo > 0.5 || o.gillRate > 2.4 || Symptom.GASPING in o.symptoms) 0.95 else 0.0
    }
    override fun update(c: Ctx, out: SteerIntent) {
        val hob = c.rock.anchors.pos("HOB_OUTFLOW", Vec3(73.0, c.rock.waterLevelCm - 1, 1.0))
        // in the surface ripple a hand's width in front of the return, above its downward jet rather than in it (the
        // resized tank's nozzle sits 0.7 cm under the surface and its jet only drops ~4 cm over the first 10 cm)
        val spot = c.safe(Vec3(hob.x + c.rng.signed() * 3, c.rock.waterLevelCm - 1.5 - 0.2 * c.L, hob.z + 12 + 0.5 * c.L), 1.0)
        // from under an overhang (the R4 / R6 shelves beside the return) the way up is around the rock, not through it
        out.seek(c.viaOpenWater(spot), c.o.cruiseSpeed, arrive = 3.0)
        out.hover = true; out.pitchBias = 0.3
        // gasping is done at the surface film: the steering's glass / surface stand-off (0.5 L + 1 cm below the
        // surface) would otherwise hold it ~5 cm down
        out.allowGlass = true
        c.o.mouth = 0.5 + 0.5 * sin(c.t * 8)
        if (c.t - lastEvent > 1800) { lastEvent = c.t; c.sim.emit(SimEvent.Gasping(c.o.id, c.sim.simTimeSec)) }
    }
}

/** Flashing (ich): dart to a rock face and roll/scrape 0.5 s, repeat. */
class Flash(val b: SpeciesBrain) : Behavior("flash", "Flashing") {
    private var spot: Vec3 = Vec3.ZERO
    private var normal: Vec3 = Vec3.Y
    private var nextAllowed = 0.0
    private var scrapeAt = -1.0
    override fun score(c: Ctx): Double {
        val o = c.o
        if (o.disease != Disease.ICH && o.disease != Disease.VELVET) return 0.0
        if (c.t < nextAllowed) return 0.0
        // the itch wins over routine behaviour when a flash is due (2–10 /h at ich 0.3, 06 §5.6 #12)
        return (0.7 + 0.5 * o.diseaseStage).coerceAtMost(0.9)
    }
    override fun enter(c: Ctx) {
        val sp = c.rockSurfacePoint(c.o.pos, 25.0) ?: run { finished = true; return }
        spot = sp.first; normal = sp.second; scrapeAt = -1.0
    }
    override fun update(c: Ctx, out: SteerIntent) {
        val stand = spot + normal * (0.5 * c.o.collisionLength + 0.5)
        val d = c.o.pos.distanceTo(stand)
        if (scrapeAt < 0) {
            out.seek(stand, c.o.burstSpeed * 0.7, arrive = 1.0, urgency = 0.8)
            if (d < 2) scrapeAt = c.t
        } else {
            out.rollExtra = 0.7
            out.seek(stand + (c.o.heading * 3.0), c.o.cruiseSpeed, arrive = 1.0)
            if (c.t - scrapeAt > 0.5) {
                finished = true
                c.o.counters.add(com.reeflive.core.sim.Counters.K.FLASH, c.t)
                // ≈ 2 + 15·stage flashes per hour (06 §5.6 #12: 2–10/h at ich 0.3)
                nextAllowed = c.t + 3600.0 / (2.0 + 15.0 * c.o.diseaseStage) * c.rng.range(0.6, 1.4)
            }
        }
        if (elapsed > 8) { finished = true; nextAllowed = c.t + 60 }
    }
}

/** Schooling helpers (cardinals when more are added): cohesion/alignment folded into a target. */
fun schoolTarget(c: Ctx, base: Vec3): Vec3 {
    val mates = c.p.neighbors(c.o.pos, 25.0).filter { it !== c.o && it.brainKind == c.o.brainKind }
    if (mates.isEmpty()) return base
    var centre = Vec3.ZERO
    var vel = Vec3.ZERO
    for (m in mates) { centre += m.pos; vel += m.vel }
    centre /= mates.size.toDouble()
    vel /= mates.size.toDouble()
    return base.lerp(centre, 0.4) + vel * 0.3
}

fun dayLight(c: Ctx) = smoothstep(0.05, 0.3, c.p.light)

fun Ctx.foodKindsPresent(): Set<FoodKind> = food.particles.filter { !it.eaten }.map { it.kind }.toSet()

fun nearestOther(c: Ctx, r: Double, filter: (com.reeflive.core.sim.Organism) -> Boolean) =
    c.p.neighbors(c.o.pos, r).filter { it !== c.o && it.alive && filter(it) }.minByOrNull { it.pos.distanceTo(c.o.pos) }

fun minD(a: Double, b: Double) = min(a, b)

/**
 * A fish that eats bristleworms (research 42 §B.5: hawkfish, dottybacks, leopard and six-line wrasses) darts at one
 * out on the rock within [radius] cm and pulls it off before it backs into its hole; [chance] of a grip per strike.
 * Mostly at dusk and after a feed, when the worms come out while the fish are still awake.
 */
class WormSnap(val b: SpeciesBrain, val radius: Double, val chance: Double, val weight: Double = 0.7) : Behavior("wormsnap", "Snapping up a bristleworm") {
    private var w: com.reeflive.core.sim.WormField.Worm? = null
    var caught = 0; private set
    override val committed: Boolean get() = elapsed < 2.5
    // idle unless a worm is out: no utility noise, so the other behaviours' random draws are unchanged
    override val noisy: Boolean get() = false
    /** A worm it could not get to in one dart, left alone a while: tucked where the fish cannot reach, it kept a
     * hawkfish darting at it, one 5 s try after another, for most of a minute off its perch. */
    private var skip: String? = null
    private var skipUntil = 0.0
    override fun score(c: Ctx): Double {
        if (c.o.hunger < 0.2 || c.o.sleeping) return 0.0
        w = c.sim.worms.nearest(c.o.pos, radius)
        if (w != null && w?.key == skip && c.t < skipUntil) w = null
        return if (w != null) weight * (0.6 + 0.4 * c.o.hunger) else 0.0
    }
    override fun update(c: Ctx, out: SteerIntent) {
        val ww = w ?: run { finished = true; return }
        if (!ww.exposed) { w = null; finished = true; return }
        // the mouth goes to the worm, the body stays off the rock it lies on
        val at = ww.head + ww.normal * (0.25 * c.L)
        out.seek(at, c.o.burstSpeed * 0.8, arrive = 0.4, urgency = 0.8)
        if (c.o.pos.distanceTo(at) < 1.0 + 0.15 * c.L) {
            if (c.rng.chance(chance) && c.sim.worms.catch(ww, c.o)) { caught++; c.o.eat(WORM_SNAP_G); c.o.mouth = 1.0 }
            w = null; finished = true
        }
        if (elapsed > 5) { skip = ww.key; skipUntil = c.t + SKIP_S; w = null; finished = true }
    }
    companion object { const val WORM_SNAP_G = 0.05; const val SKIP_S = 90.0 }
}

/**
 * A current too strong to hold station in (sandbox turnover past ~60×/h, research 09 Part B): the fish gives up
 * swimming in the open, tucks in low behind the rock out of the jets and stops feeding (HealthModel.flowAppetite).
 * Installed on every fish; scores 0 and draws no rng while the flow is ordinary.
 */
class FlowShelter(val b: SpeciesBrain) : Behavior("flowShelter", "Hiding from the current") {
    private var spot: Vec3? = null
    private var nextPick = 0.0
    override val noisy: Boolean get() = false
    private fun flowStress(c: Ctx) = c.o.envStress["flow"] ?: 0.0
    override fun score(c: Ctx): Double =
        if (c.o.isFish && c.o.brainKind != BrainKind.EEL && flowStress(c) > com.reeflive.core.sim.HealthModel.FLOW_HIDE_STRESS) 0.9 else 0.0
    override fun enter(c: Ctx) { spot = null; nextPick = 0.0 }
    override fun update(c: Ctx, out: SteerIntent) {
        val o = c.o
        if (spot == null || c.t > nextPick) {
            nextPick = c.t + 45.0
            val cover = c.coverPoint(o.pos + Vec3.Y * 8.0, 25.0)
            spot = c.safe(Vec3(cover.x, c.zoneY(0.12), cover.z))
        }
        out.seek(spot!!, o.burstSpeed * 0.6, arrive = 3.0, urgency = 0.5)
        out.hover = true
        out.pectoral = 0.6
        if (flowStress(c) < com.reeflive.core.sim.HealthModel.FLOW_HIDE_STRESS - 0.1) finished = true
    }
}
