package com.reeflive.core.ai

import com.reeflive.core.geom.Vec3
import com.reeflive.core.geom.smoothstep
import com.reeflive.core.sim.Ev
import com.reeflive.core.sim.Evidence
import com.reeflive.core.sim.FoodKind
import com.reeflive.core.sim.FoodParticle
import com.reeflive.core.sim.HealthModel
import com.reeflive.core.sim.SnailRig
import com.reeflive.core.sim.UrchinRig
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// =====================================================================================
// Cleanup crew pack (docs/research/43_cleanup_crew.md): trochus snail, fighting conch, tuxedo urchin.
// Their soft parts are posed by the renderer from SnailRig / UrchinRig (fish.vert modes 8 and 9).
// =====================================================================================

/** Grazing on the move: algae removal for the abstract hair index and hunger relief, per tick (as [GlassGraze]). */
internal fun grazeTick(c: Ctx, share: Double = 1.0) {
    c.sim.addGrazing(c.sim.grazeRemovalPerMin(c.o) * share * c.dt / 60)
    c.o.hunger = max(0.0, c.o.hunger - 1.6 * share * HealthModel.hungerRatePerMin(c.o, c.sim.water.s.tempC) * c.dt / 60)
    c.o.nutritionGreens = 1.0
}

/** A world direction in the organism's model axes (x heading, y up, z = heading × up), for rig pokes. */
internal fun toModel(c: Ctx, d: Vec3): Vec3 {
    val f = c.o.heading.normalized(Vec3.X)
    val u = c.o.up.rejectFrom(f).normalized(Vec3.Y)
    val s = f.cross(u)
    return Vec3(d dot f, d dot u, d dot s)
}

// -------------------------------------------------------------------------------------
// Trochus / Tectus top snail
// -------------------------------------------------------------------------------------
/**
 * Banded trochus (research 43 §4): grazes diatom and green film off the glass and rock day and night (the grazing
 * itself is [GrazerType.TROCHUS] in the algae model), draws into its shell when poked or a predator comes close, and
 * when it falls on its back it reaches out with its long foot and flips itself over, which astreas often cannot
 * (keepers' reports, no controlled data: the odds are DESIGN ESTIMATES, better than the astrea's).
 */
class TrochusBrain(c: Ctx) : SpeciesBrain(c) {
    val rig: SnailRig? = o.limbRig as? SnailRig
    var flippedSince = Double.NaN; private set
    val flipped: Boolean get() = !flippedSince.isNaN()
    var rightingAttempts = 0; private set
    var rightings = 0; private set
    var withdrawals = 0; private set
    private var nextAttempt = 0.0
    private var reachUntil = -1.0
    private var out = 1.0
    private val graze = GlassGraze(this)

    companion object {
        /** Righting attempts every 3–12 min; success 0.55 with rock or glass in reach, 0.25 on open sand (DESIGN ESTIMATE). */
        @Ev(Evidence.DESIGN_ESTIMATE, "43 §4") const val ATTEMPT_MIN_S = 180.0
        const val ATTEMPT_MAX_S = 720.0
        const val P_RIGHT_ANCHORED = 0.55
        const val P_RIGHT_OPEN = 0.25
        /** One righting reach lasts this long (s): the foot stretches out, grips and twists. */
        const val REACH_S = 25.0
        /** Withdrawn this long after a poke or a close predator (s, DESIGN ESTIMATE). */
        const val WITHDRAW_MIN_S = 20.0
        const val WITHDRAW_MAX_S = 70.0
    }

    init { install(graze, Flipped(), Withdraw()) }

    /** Sandbox / tests: knock it over now. */
    fun flip(c: Ctx) { flippedSince = c.t; nextAttempt = c.t + c.rng.range(60.0, ATTEMPT_MAX_S) }

    override fun restSpot(c: Ctx, night: Boolean): Rest = if (o.homePos == Vec3.ZERO)
        Rest(Vec3(0.5 * c.rock.lengthCm, 0.5 * c.rock.waterLevelCm, c.rock.widthCm - surfaceOffset(c)), Vec3.Y, Vec3(0.0, 0.0, -1.0))
    else Rest(o.homePos, o.heading, o.up)

    override fun initialPlace(c: Ctx) {
        // most start on the front or back glass, clear of rock leaning on the pane
        val r = c.rock
        var front = o.rng.chance(0.6)
        var p = Vec3.ZERO
        for (i in 0 until 24) {
            if (i > 12) front = true
            p = Vec3(o.rng.range(6.0, r.lengthCm - 6), o.rng.range(r.sandTopCm + 4, r.waterLevelCm - 6), if (front) r.widthCm - surfaceOffset(c) else surfaceOffset(c))
            if (r.sdf.dist(p) > 2.5) break
        }
        o.homePos = p
        o.placeAt(p, Vec3.Y)
        o.up = if (front) Vec3(0.0, 0.0, -1.0) else Vec3.Z
    }

    override fun postSteer(c: Ctx) {
        val target = when {
            !c.o.alive -> 0.4
            utility.current is Withdraw -> 0.0
            flipped -> if (c.t < reachUntil) 1.0 else 0.55
            else -> 1.0
        }
        // withdrawing is quick (about a second), coming back out slow (tens of seconds, est.)
        out += (target - out) * min(1.0, c.dt * (if (target < out) 3.0 else 0.15))
        val reach = if (flipped && c.t < reachUntil) 0.5 + 0.5 * sin(PI * (reachUntil - c.t) / REACH_S) else 0.0
        val rasp = if (utility.current === graze) 0.5 + 0.5 * c.o.hunger else 0.0
        rig?.set(out, rasp * out, reach, 0.0, 0.0, out, 0.0)
        c.o.extension = out
    }

    /** Draws into the shell and clamps down (the foot stays on the surface), then slowly comes back out. */
    inner class Withdraw : Behavior("withdraw", "Pulled into its shell") {
        private var until = 0.0
        override val noisy: Boolean get() = false
        override fun score(c: Ctx): Double {
            if (c.t < until) return 0.92
            val close = threat?.let { it.pos.distanceTo(c.o.pos) < 2.0 * c.L + 3 } ?: false
            return if (startledRecently && startleMag > 0.08 || close) 0.92 else 0.0
        }
        override fun enter(c: Ctx) { until = c.t + c.rng.range(WITHDRAW_MIN_S, WITHDRAW_MAX_S); withdrawals++; startleAt = -1e9 }
        override fun update(c: Ctx, out: SteerIntent) {
            out.rest(Rest(c.o.pos, c.o.heading, c.o.up, 0), 1.0)
            if (c.t >= until) finished = true
        }
    }

    /**
     * On its back on the sand (it lost its grip on the glass or rock): every few minutes the foot reaches out, grips
     * and twists; with something to grip (rock or glass close by) most attempts work (research 43 §4).
     */
    inner class Flipped : Behavior("flipped", "Flipped over, trying to turn back") {
        override val committed: Boolean get() = flipped
        override fun score(c: Ctx): Double {
            if (flipped) return 0.99
            val onSand = c.o.pos.y < c.sim.sand.surfaceY(c.o.pos.x, c.o.pos.z) + 1.5
            if (onSand && c.rng.chance(c.dt / 3600 * 0.15)) { flip(c); return 0.99 }
            return 0.0
        }
        override fun update(c: Ctx, out: SteerIntent) {
            val p = c.o.pos
            out.rest(Rest(p.withY(c.sim.sand.surfaceY(p.x, p.z) + 0.5 * c.o.species.depthRatio * c.L), c.o.heading, Vec3.Y * -1.0, 7), 1.0)
            if (c.t >= nextAttempt && c.t >= reachUntil) {
                rightingAttempts++
                reachUntil = c.t + REACH_S
                val anchored = c.rock.envDist(p) < 2.5 + 0.5 * c.L && (c.rock.sdf.dist(p) < 1.5 + c.L || p.z < 3.0 || p.z > c.rock.widthCm - 3.0)
                if (c.rng.chance(if (anchored) P_RIGHT_ANCHORED else P_RIGHT_OPEN)) {
                    // it rolls over at the end of this reach
                    nextAttempt = Double.MAX_VALUE
                } else nextAttempt = reachUntil + c.rng.range(ATTEMPT_MIN_S, ATTEMPT_MAX_S)
            }
            if (nextAttempt == Double.MAX_VALUE && c.t >= reachUntil) {
                flippedSince = Double.NaN; rightings++; nextAttempt = 0.0; finished = true
            }
        }
    }
}

// -------------------------------------------------------------------------------------
// Florida fighting conch
// -------------------------------------------------------------------------------------
/**
 * Florida fighting conch, *Strombus alatus* (research 43 §3): a sand grazer of diatom film and detritus. It moves in
 * leaps: the foot reaches forward, digs the sickle operculum into the sand and vaults the heavy shell over it
 * (strombid "leaping", SeaLifeBase; leap length and rate have no data: DESIGN ESTIMATES), then pauses with its eyes
 * up on their stalks. It buries for hours with only the eyes and snout tip out, races (leap after leap) away from a
 * predator, and comes out to settled food (algae wafers, leftovers).
 */
class ConchBrain(c: Ctx) : SpeciesBrain(c) {
    val rig: SnailRig? = o.limbRig as? SnailRig
    var leaps = 0; private set
    var buriedS = 0.0; private set
    var escapes = 0; private set

    companion object {
        /** One leap: foot reach, vault, settle (s), then a pause; it covers this much (× shell length). DESIGN ESTIMATES. */
        @Ev(Evidence.DESIGN_ESTIMATE, "43 §3") const val REACH_S = 0.8
        const val VAULT_S = 0.4
        const val SETTLE_S = 0.4
        const val PAUSE_MIN_S = 1.5
        const val PAUSE_MAX_S = 7.0
        const val LEAP_L = 0.35
        /** Buried bouts (h) and how deep the shell goes (× body height). Hobby reports only (research 43 §3). */
        const val BURY_MIN_H = 1.0
        const val BURY_MAX_H = 6.0
        const val BURY_DEPTH = 0.75
    }

    // leap state
    private var phase = 0.0          // seconds into the current cycle
    private var pause = 2.0
    private var leapFrom = Vec3.ZERO
    private var leapTo = Vec3.ZERO
    private var vault = 0.0
    private var eyes = 1.0
    private var out = 1.0
    private var buriedBy = 0.0       // 0 on the sand .. 1 buried

    private val forage = Forage()
    private val bury = Bury()

    init { install(forage, bury, ToFood(), Escape()) }

    override fun restSpot(c: Ctx, night: Boolean): Rest {
        val p = if (o.homePos == Vec3.ZERO) Vec3(0.5 * c.rock.lengthCm, 0.0, 0.7 * c.rock.widthCm) else o.homePos
        return Rest(Vec3(p.x, c.sim.sand.surfaceY(p.x, p.z) + surfaceOffset(c) - 0.2, p.z), o.heading.horizontal().normalized(Vec3.X), Vec3.Y)
    }

    override fun initialPlace(c: Ctx) {
        val r = c.rock
        o.homePos = sandSpot(c, 0.15 * r.lengthCm..0.85 * r.lengthCm, 0.55 * r.widthCm..0.85 * r.widthCm, 0.0)
        o.homePos = o.homePos.withY(c.sim.sand.surfaceY(o.homePos.x, o.homePos.z) + surfaceOffset(c) - 0.2)
        o.placeAt(o.homePos, Vec3(o.rng.signed(), 0.0, o.rng.signed()).normalized(Vec3.X))
    }

    /** True where the conch can be: open sand with no rock over it. */
    private fun sandOk(c: Ctx, p: Vec3) = rockClear(c, p) > 0.5 + 0.3 * c.L &&
        p.x > 0.4 * c.L + 1 && p.x < c.rock.lengthCm - 0.4 * c.L - 1 && p.z > 0.3 * c.L + 1 && p.z < c.rock.widthCm - 0.3 * c.L - 1

    private fun rockClear(c: Ctx, p: Vec3) = c.rock.sdf.dist(p.withY(c.sim.sand.surfaceY(p.x, p.z) + 1.0))

    /** An open-sand point about [dist] cm from the conch, roughly toward [toward] (or anywhere); the clearest tried if none fits. */
    fun sandTarget(c: Ctx, dist: Double, toward: Vec3? = null): Vec3 {
        val p = c.o.pos
        var best = p
        var bestClear = -1e9
        for (i in 0 until 16) {
            val dir = if (toward != null && i < 10) (toward - p).horizontal().normalized(Vec3.X).let { Vec3.rotateAbout(it, Vec3.Y, c.rng.signed() * 0.5 * (1 + i / 4)) }
                else Vec3(c.rng.signed(), 0.0, c.rng.signed()).normalized(Vec3.X)
            val q = c.rock.clampToWater(p + dir * dist, 0.4 * c.L + 1)
            if (sandOk(c, q)) return q.withY(c.sim.sand.surfaceY(q.x, q.z) + surfaceOffset(c) - 0.2)
            val cl = rockClear(c, q)
            if (cl > bestClear) { bestClear = cl; best = q }
        }
        return best.withY(c.sim.sand.surfaceY(best.x, best.z) + surfaceOffset(c) - 0.2)
    }

    /**
     * One step of leaping toward [goal]: the body only moves during the vault (a jump of [LEAP_L] shell lengths, shorter
     * when the goal is nearer), turning to face the goal while the foot reaches. Holds still the rest of the cycle.
     */
    private fun leapToward(c: Ctx, out: SteerIntent, goal: Vec3, hurry: Boolean) {
        val o = c.o
        val dt = c.dt
        val cycle = REACH_S + VAULT_S + SETTLE_S
        if (phase == 0.0) {
            // start of a cycle: aim the next leap
            val d = (goal - o.pos).horizontal()
            val step = min(LEAP_L * c.L, d.length())
            val dir = d.normalized(o.heading.horizontal().normalized(Vec3.X))
            var to = o.pos + dir * step
            // blocked: try veering either way before giving up this leap (it turns on the spot instead)
            if (!sandOk(c, to)) {
                to = listOf(0.6, -0.6, 1.2, -1.2).map { o.pos + Vec3.rotateAbout(dir, Vec3.Y, it) * step }.firstOrNull { sandOk(c, it) } ?: o.pos
            }
            leapFrom = o.pos; leapTo = to.withY(c.sim.sand.surfaceY(to.x, to.z) + surfaceOffset(c) - 0.2)
        }
        phase += dt
        val face = (leapTo - leapFrom).horizontal().let { if (it.lengthSq() > 1e-6) it.normalized(o.heading) else o.heading.horizontal().normalized(Vec3.X) }
        val pos: Vec3
        when {
            phase < REACH_S -> { pos = leapFrom; vault = 0.0 }
            phase < REACH_S + VAULT_S -> {
                val u = (phase - REACH_S) / VAULT_S
                pos = leapFrom.lerp(leapTo, smoothstep(0.0, 1.0, u))
                vault = sin(PI * u)
            }
            phase < cycle -> { pos = leapTo; vault = 0.0 }
            else -> {
                pos = leapTo; vault = 0.0
                if (phase >= cycle + pause) {
                    if (leapTo.distanceTo(leapFrom) > 0.1) leaps++
                    phase = 0.0
                    pause = if (hurry) c.rng.range(0.1, 0.5) else c.rng.range(PAUSE_MIN_S, PAUSE_MAX_S)
                }
            }
        }
        out.place(pos, o.heading.horizontal().normalized(face).lerp(face, min(1.0, dt * 2)).normalized(face), Vec3.Y, rate = 30.0)
    }

    private fun leapPhase(): Double {
        val cycle = REACH_S + VAULT_S + SETTLE_S
        return if (phase <= 0.0 || phase >= cycle) 0.0 else (phase / cycle).coerceIn(0.0, 0.999)
    }

    override fun postSteer(c: Ctx) {
        val cur = utility.current
        val eyesTarget = when {
            !c.o.alive -> 0.0
            cur is Escape && startledRecently -> 0.0
            else -> 1.0
        }
        eyes += (eyesTarget - eyes) * min(1.0, c.dt * (if (eyesTarget < eyes) 4.0 else 0.3))
        val outTarget = if (cur === bury && buriedBy > 0.9) 0.35 else 1.0
        out += (outTarget - out) * min(1.0, c.dt * 0.5)
        val rasp = if (cur === forage && phase >= REACH_S + VAULT_S + SETTLE_S) 0.7 else 0.0
        rig?.set(out, rasp, 0.0, leapPhase(), vault, eyes, 0.0)
        c.o.extension = eyes
    }

    /** Leap across open sand from patch to patch, grazing the film at each stop. */
    inner class Forage : Behavior("forage", "Grazing the sand") {
        private var goal = Vec3.ZERO
        override fun score(c: Ctx) = 0.4 + 0.2 * c.o.hunger + 0.1 * c.activity
        override fun enter(c: Ctx) { goal = sandTarget(c, c.rng.range(8.0, 25.0)) }
        override fun update(c: Ctx, out: SteerIntent) {
            if (c.o.pos.distanceTo(goal) < 0.4 * c.L || elapsed > 600) goal = sandTarget(c, c.rng.range(8.0, 25.0))
            leapToward(c, out, goal, hurry = false)
            if (phase == 0.0 || phase > REACH_S + VAULT_S + SETTLE_S) grazeTick(c)
        }
    }

    /** Digs in until only the eyes and snout tip show; stays for a bout of hours (more by day, est.). */
    inner class Bury : Behavior("bury", "Buried in the sand") {
        private var until = 0.0
        override val committed: Boolean get() = buriedBy > 0.05
        override fun score(c: Ctx): Double {
            if (c.t < until) return 0.7
            // sated conchs bury; more by day (hobby reports, research 43 §3)
            return if (c.o.hunger < 0.35) 0.25 + 0.25 * (1 - c.activity) else 0.05
        }
        override fun enter(c: Ctx) { until = c.t + 3600 * c.rng.range(BURY_MIN_H, BURY_MAX_H) }
        override fun update(c: Ctx, out: SteerIntent) {
            buriedBy = if (c.t < until) min(1.0, buriedBy + c.dt / 40) else max(0.0, buriedBy - c.dt / 15)
            if (buriedBy > 0.5) buriedS += c.dt
            val p = c.o.pos
            val top = c.sim.sand.surfaceY(p.x, p.z) + surfaceOffset(c) - 0.2
            val h = c.o.species.depthRatio * c.L
            out.place(p.withY(top - BURY_DEPTH * h * buriedBy), c.o.heading, Vec3.Y, rate = 2.0)
            out.posture = if (buriedBy > 0.5) 5 else 0
            // hungry, or food on the sand nearby: it comes up early
            if (c.t < until && (c.o.hunger > 0.6 || c.findFood(25.0, los = false) { it.settled } != null)) until = c.t
            if (c.t >= until && buriedBy <= 0.0) finished = true
        }
        override fun exit(c: Ctx) { buriedBy = 0.0 }
    }

    /** Leaps to settled food on the sand (algae wafers, nori, leftovers) and grazes it with the proboscis. */
    inner class ToFood : Behavior("scavenge", "Leaping to food") {
        private var f: FoodParticle? = null
        override fun score(c: Ctx): Double {
            if (c.o.hunger < 0.15 || buriedBy > 0.05) return 0.0
            f = c.findFood(35.0, los = false) { it.settled && sandOk(c, it.pos) && (o.species.dietMatch(it.kind) > 0 || it.kind == FoodKind.NORI) }
            return if (f != null) 0.65 + 0.2 * c.o.hunger else 0.0
        }
        override fun update(c: Ctx, out: SteerIntent) {
            val ff = f?.takeIf { !it.eaten } ?: run { finished = true; return }
            if (c.o.pos.distanceTo(ff.pos) < 0.45 * c.L + 1.0) {
                out.place(c.o.pos, c.o.heading, Vec3.Y)
                c.o.mouth = 0.6 + 0.4 * sin(c.t * 2.0)
                if (elapsed > 4) { c.bite(ff); f = null; finished = true }
            } else leapToward(c, out, ff.pos, hurry = false)
        }
    }

    /** Leap after leap away from a predator or a poke (strombid escape leaping), eyes pulled in at first. */
    inner class Escape : Behavior("escape", "Leaping away") {
        private var goal = Vec3.ZERO
        private var until = 0.0
        override fun score(c: Ctx): Double {
            if (c.t < until) return 0.9
            val close = threat?.let { it.pos.distanceTo(c.o.pos) < c.L + 4 } ?: false
            return if (startledRecently && startleMag > 0.1 || close) 0.9 else 0.0
        }
        override fun enter(c: Ctx) {
            escapes++
            until = c.t + c.rng.range(6.0, 15.0)
            val from = threat?.pos ?: startleFrom
            goal = sandTarget(c, 30.0, c.o.pos + (c.o.pos - from).horizontal().normalized(Vec3.X) * 30.0)
            buriedBy = 0.0
            startleAt = -1e9
        }
        override fun update(c: Ctx, out: SteerIntent) {
            leapToward(c, out, goal, hurry = true)
            if (c.t >= until) finished = true
        }
    }
}

// -------------------------------------------------------------------------------------
// Tuxedo urchin
// -------------------------------------------------------------------------------------
/**
 * Tuxedo urchin, *Mespilia globulus* (research 43 §2): crawls the rock and glass on its tube feet grazing film and
 * coralline algae (the coralline loss is real: [GrazerType.URCHIN]), more by night. Under bright light it holds bits
 * of shell, rubble and algae over its top with its tube feet (the covering reaction; studied in other urchins, where it
 * tracks UV more than visible light, Adams 2001, Sigg 2007; no Mespilia data, so the light curve is a DESIGN ESTIMATE)
 * and drops most of them at night. A poke makes it draw in its tube feet and point its spines at the poke.
 */
class UrchinBrain(c: Ctx) : SpeciesBrain(c) {
    val rig: UrchinRig? = o.limbRig as? UrchinRig
    /** Debris cover 0..1 (how many of the model's pieces it holds). */
    var cover = 0.0; private set
    var bristles = 0; private set
    private var reach = 1.0
    private var bristle = 0.0
    private var pokeDir = Vec3.ZERO
    private var nextPick = 0.0

    companion object {
        /** A piece picked up or dropped every few minutes (DESIGN ESTIMATE; covering builds over tens of minutes, Adams 2001). */
        @Ev(Evidence.DESIGN_ESTIMATE, "43 §2") const val PICK_MIN_S = 120.0
        const val PICK_MAX_S = 480.0
        /** Cover held at full light and in the dark (fractions of the 7 pieces). */
        const val COVER_BRIGHT = 0.9
        const val COVER_DARK = 0.15
        const val BRISTLE_S = 12.0
    }

    init { install(GlassGraze(this), Hold()) }

    /** How much cover it wants now: light-driven, a little more when stressed. */
    fun coverWanted(c: Ctx): Double =
        (COVER_DARK + (COVER_BRIGHT - COVER_DARK) * smoothstep(0.15, 0.75, c.p.light) + 0.2 * c.o.stress).coerceIn(0.0, 1.0)

    override fun restSpot(c: Ctx, night: Boolean): Rest = if (o.homePos == Vec3.ZERO)
        Rest(Vec3(0.5 * c.rock.lengthCm, 0.4 * c.rock.waterLevelCm, surfaceOffset(c)), Vec3.Y, Vec3.Z)
    else Rest(o.homePos, o.heading, o.up)

    override fun initialPlace(c: Ctx) {
        val r = c.rock
        val s = c.rockSurfacePoint(Vec3(o.rng.range(0.25, 0.75) * r.lengthCm, r.sandTopCm + 10, 0.5 * r.widthCm), 25.0, minNormalY = 0.3)
        if (s != null) {
            val (p, n) = s
            o.homePos = p + n * surfaceOffset(c)
            o.placeAt(o.homePos, n.cross(Vec3.Z).normalized(Vec3.X))
            o.up = n
        } else {
            o.homePos = Vec3(0.5 * r.lengthCm, r.sandTopCm + 6, surfaceOffset(c))
            o.placeAt(o.homePos, Vec3.X)
            o.up = Vec3.Z
        }
        cover = coverWanted(c)
    }

    override fun onTouch(c: Ctx, at: Vec3, kind: com.reeflive.core.sim.TouchReaction, s: Double) {
        super.onTouch(c, at, kind, s)
        poke(c, at)
    }

    override fun onTap(c: Ctx, at: Vec3, s: Double) {
        super.onTap(c, at, s)
        if (at.distanceTo(o.pos) < 3 * c.L + 10) poke(c, at)
    }

    private fun poke(c: Ctx, at: Vec3) {
        bristles++
        bristle = 1.0
        pokeDir = toModel(c, (at - o.pos).normalized(o.up))
    }

    override fun postSteer(c: Ctx) {
        val dt = c.dt
        // pick up or drop one piece at a time toward the cover it wants
        if (c.t >= nextPick) {
            nextPick = c.t + c.rng.range(PICK_MIN_S, PICK_MAX_S)
            val want = coverWanted(c)
            val step = 1.0 / UrchinRig.DEBRIS
            cover = if (want > cover + 0.5 * step) min(1.0, cover + step) else if (want < cover - 0.5 * step) max(0.0, cover - step) else cover
        }
        bristle = max(0.0, bristle - dt / BRISTLE_S)
        val reachTarget = if (!c.o.alive) 0.1 else if (bristle > 0.3) 0.2 else 1.0
        reach += (reachTarget - reach) * min(1.0, dt * (if (reachTarget < reach) 3.0 else 0.2))
        val d = if (bristle > 0) pokeDir else Vec3.ZERO
        rig?.set(reach, cover, bristle, d.x, d.y, d.z)
        c.o.extension = reach
    }

    /** A poke: it clamps down on its tube feet and stays put a while, spines toward the poke. */
    inner class Hold : Behavior("hold", "Clamped down, spines out") {
        override val noisy: Boolean get() = false
        override fun score(c: Ctx) = if (bristle > 0.3) 0.9 else 0.0
        override fun update(c: Ctx, out: SteerIntent) { out.rest(Rest(c.o.pos, c.o.heading, c.o.up, 0), 1.0) }
    }
}
