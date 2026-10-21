package com.reeflive.core.ai

import com.reeflive.core.geom.Vec3
import com.reeflive.core.sim.BrainKind
import com.reeflive.core.sim.Ev
import com.reeflive.core.sim.Evidence
import com.reeflive.core.sim.Organism
import com.reeflive.core.sim.ShrimpLimbs
import com.reeflive.core.sim.SimEvent
import com.reeflive.core.sim.Simulation
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// =====================================================================================
// Peacock mantis shrimp (Odontodactylus scyllarus, research 62 §2): a smasher. It digs a U-shaped burrow in the sand
// near the rock, two openings a body length or two apart, and spends much of the day at a mouth with its stalked eyes
// up, watching. A snail, hermit, crab or small shrimp that comes in range is stalked over the sand and hammered with
// the clubs (the dactyl heels of its raptorial appendages, out at up to 23 m/s, Patek et al. 2004) until the shell
// cracks; the meal goes back to the burrow. A small fish that strays right up to the mouth can be struck too. By night
// it stays down the burrow. It keeps the burrow open, carrying sand out and piling it by the mouth.
// =====================================================================================
class MantisShrimpBrain(c: Ctx) : SpeciesBrain(c) {
    /** The two openings (on the sand) and the direction from each down into the burrow (horizontal, out = minus). */
    private var mouthA: Vec3? = null
    private var mouthB: Vec3? = null
    private var axis = Vec3.X
    /** The opening it uses now (true = B); it pops out of the other one now and then. */
    private var atB = false
    /** The animal being struck, or carried and eaten (held: its brain and steering wait). */
    var prey: Organism? = null; private set
    var meal: Organism? = null; private set
    var strikes = 0; private set
    var kills = 0; private set
    var fishStrikes = 0; private set
    var loads = 0; private set
    var displays = 0; private set
    private var nextFishLook = 0.0

    private val limbs: ShrimpLimbs? get() = o.limbRig as? ShrimpLimbs
    private val stance: Double get() = shrimpStance(o)

    init {
        install(InBurrow(), Duck(), Watch(), Dig(), Forage(), Hunt(), Smash(), Carry(), MoltHide(this) { inside(it) })
    }

    // ---------------------------------------------------------------- the burrow

    /** Sand height at (x, z), never under the bare floor. */
    private fun bedY(x: Double, z: Double) = max(c.sim.sand.surfaceY(x, z), c.rock.sandTopCm - 0.6)

    /**
     * Picks the burrow: open sand a little way off the rock foot (research 60 §11: "U-shaped burrows in loose sediment
     * near reefs"), both openings clear of the rock and the glass, its axis along the rock edge.
     */
    private fun site(c: Ctx) {
        if (mouthA != null) return
        val r = c.rock
        val L = c.L
        val span = SPAN_BL * L
        var best: Pair<Vec3, Vec3>? = null; var bs = -1e9
        val others = c.sim.organisms.filter { it !== o && it.alive && (it.brainKind == BrainKind.WATCHMAN_GOBY || it.brainKind == BrainKind.ENGINEER_GOBY || it.brainKind == BrainKind.PISTOL_SHRIMP) }
        // near the rock first; where the rock leaves no such spot, further out on the open sand
        for (maxD in doubleArrayOf(ROCK_MAX_CM, 2 * ROCK_MAX_CM, 1e9)) {
            for (i in 0 until 120) {
                val p = Vec3(c.rng.range(L, r.lengthCm - L), r.sandTopCm, c.rng.range(0.6 * L, r.widthCm - 0.6 * L))
                val d = room(c, p)
                if (d < ROCK_MIN_CM || d > maxD) continue
                val g = r.sdf.gradient(p.withY(r.sandTopCm + 1.0))
                val edge = horizontalHeading(Vec3(-g.z, 0.0, g.x), Vec3.X)
                for (ax in arrayOf(edge, edge * -1.0, c.rng.unitVector().withY(0.0).normalized(Vec3.X))) {
                    val q = p + ax * span
                    if (q.x < L || q.x > r.lengthCm - L || q.z < 0.6 * L || q.z > r.widthCm - 0.6 * L) continue
                    // room to come out of either opening and dump a load: the head and the heap stay off the glass
                    val outA = p - ax * (OUT_BL * L); val outB = q + ax * (OUT_BL * L)
                    if (listOf(outA, outB).any { it.x < 0.3 * L || it.x > r.lengthCm - 0.3 * L || it.z < 0.3 * L || it.z > r.widthCm - 0.3 * L }) continue
                    if (room(c, outA) < 2.0 || room(c, outB) < 2.0) continue
                    if (room(c, q) < ROCK_MIN_CM || !lane(c, p, q, ROCK_MIN_CM - 1.0)) continue
                    val crowd = others.minOfOrNull { min(it.pos.distanceTo(p), it.pos.distanceTo(q)) } ?: 60.0
                    val sc = -0.3 * min(d, room(c, q)) + 0.05 * crowd.coerceAtMost(40.0) + 0.04 * p.z + c.rng.range(0.0, 1.0)
                    if (sc > bs) { bs = sc; best = p to q }
                }
            }
            if (best != null) break
        }
        val (a, b) = best ?: (Vec3(r.lengthCm * 0.35, r.sandTopCm, r.widthCm * 0.7) to Vec3(r.lengthCm * 0.35 + span, r.sandTopCm, r.widthCm * 0.7))
        mouthA = a.withY(bedY(a.x, a.z)); mouthB = b.withY(bedY(b.x, b.z))
        axis = (b - a).withY(0.0).normalized(Vec3.X)
        // the openings: two pits, the spoil heaped beside each (they ring the mouth with rubble [I])
        for ((m, out) in listOf(a to axis * -1.0, b to axis)) {
            c.sim.sand.dig(m.x, m.z, PIT_CM3 * L / 12.0, 0.18 * L)
            c.sim.sand.deposit(m.x + out.x * 0.3 * L, m.z + out.z * 0.3 * L, 0.6 * PIT_CM3 * L / 12.0, 0.15 * L)
        }
    }

    /** True when a straight walk from [a] to [b] over the sand keeps [clear] cm off the rock. */
    private fun lane(c: Ctx, a: Vec3, b: Vec3, clear: Double): Boolean {
        val n = (a.distanceTo(b) / 2.0).toInt().coerceAtLeast(1)
        for (k in 0..n) if (room(c, a.lerp(b, k.toDouble() / n)) < clear) return false
        return true
    }

    /**
     * Distance from the rock of a body lying on the sand at (x, z) of [p]: the least of the rock distance at the
     * body's heights over the bed, and a little under it (a rock foot running on under the sand, an overhang above).
     */
    private fun room(c: Ctx, p: Vec3): Double {
        val bed = c.sim.sand.surfaceY(p.x, p.z)
        val s = 0.1 * c.L
        var m = Double.MAX_VALUE
        for (dy in doubleArrayOf(-1.5, 0.3, s, 2.5 * s)) m = min(m, c.rock.sdf.dist(p.withY(bed + dy)))
        return m
    }

    /** The opening in use and the way out of it. */
    private fun door(c: Ctx): Pair<Vec3, Vec3> {
        site(c)
        return if (atB) mouthB!! to axis else mouthA!! to axis * -1.0
    }

    /** Head [k] body lengths out of the opening in use, facing out, on the sand. */
    fun mouth(c: Ctx, k: Double): Rest {
        val (m, out) = door(c)
        val p = m + out * (k * c.L)
        return Rest(p.withY(bedY(p.x, p.z) + stance), out, Vec3.Y, 0)
    }

    /** Down the burrow (out of sight) under the middle of the U. */
    fun inside(c: Ctx): Rest {
        site(c)
        val mid = mouthA!!.lerp(mouthB!!, 0.5)
        return Rest(mid.withY(c.rock.sandTopCm - 0.4 * c.L), door(c).second, Vec3.Y, 3)
    }

    val burrowMouths: List<Vec3> get() = listOfNotNull(mouthA, mouthB)

    override fun restSpot(c: Ctx, night: Boolean) = inside(c)
    override fun anchorPos(c: Ctx, anchor: String): Vec3? = if (anchor == "burrow" || anchor == "hole") door(c).first else super.anchorPos(c, anchor)
    override fun shelterSpot(c: Ctx): Vec3? = door(c).first
    override fun initialPlace(c: Ctx) {
        site(c)
        val r = mouth(c, 0.15)
        o.homePos = r.pos
        o.placeAt(r.pos, r.heading)
        o.up = Vec3.Y
    }

    override fun preTick(c: Ctx) {
        super.preTick(c)
        o.clawDisplay = max(0.0, o.clawDisplay - c.dt * 1.5)
        // a carried meal rides under its head
        meal?.let { m ->
            if (!m.alive || m !in c.sim.organisms) { meal = null; return@let }
            val p = o.pos + o.heading * (0.15 * m.lengthCm)
            m.pos = p.withY(max(p.y - stance, bedY(p.x, p.z) + 0.3 * m.lengthCm))
        }
        prey?.let { p -> if (!p.alive || p !in c.sim.organisms) prey = null }
    }

    /** Set when it leaves the burrow: it comes up out of the opening in use, not through the sand where it lay. */
    private var emerge = false

    private fun surface(c: Ctx) {
        if (!o.visible) emerge = true
        o.posture = 0
    }

    /**
     * A last word on the pose (after steering): the long, stiff body lies along the sand from the head back, tilted
     * so no part of it from head to tail fan sinks into a mound or the far side of a pit; out of the burrow it starts
     * at the opening.
     */
    override fun postSteer(c: Ctx) {
        // (also after a molt down the burrow: MoltHide shows it again where it lay)
        if (o.visible && o.posture != 3 && o.pos.y < bedY(o.pos.x, o.pos.z) - 0.3) emerge = true
        if (emerge) {
            emerge = false
            val r = mouth(c, 0.0)
            o.placeAt(r.pos, r.heading)
            o.up = Vec3.Y
            o.visible = true
        }
        if (!o.visible || !o.alive || o.posture == 3) return
        val L = c.L
        val fh = horizontalHeading(o.heading, axis)
        val clear = BELLY_BL * L
        // on the sand the legs hold the head at its stance over the bed (steering's y may be a lift from the last tick)
        val bed0 = bedY(o.pos.x, o.pos.z)
        var h0 = bed0 + stance
        // over a rock foot it rides up just enough to clear it
        var up = 0
        while (up < 20 && c.rock.sdf.dist(o.pos.withY(h0 - stance + clear)) < clear) { h0 += 0.2; up++ }
        var slope = 0.0
        for (k in 1..4) {
            val d = L * k / 4.0
            val q = o.pos - fh * d
            slope = min(slope, (h0 - bedY(q.x, q.z) - clear) / d)
        }
        if (slope < -MAX_TILT) { h0 += (-MAX_TILT - slope) * L; slope = -MAX_TILT }
        if (h0 != o.pos.y || slope != 0.0 || o.heading.y != 0.0) {
            o.pos = o.pos.withY(h0)
            o.heading = (fh + Vec3.Y * slope).normalized(fh)
            o.up = Vec3.Y.rejectFrom(o.heading).normalized(Vec3.Y)
        }
    }

    /** Hides it down the burrow from the opening in use. */
    private fun goDown(c: Ctx, out: SteerIntent, rate: Double) {
        val m = mouth(c, 0.2)
        if (o.visible && o.pos.distanceTo(m.pos) > 0.15 * c.L) {
            // facing out near the opening it scoots straight back in, tail first, as stomatopods do [M]
            if (o.pos.distanceTo(m.pos) < 1.5 * c.L && (o.heading dot m.heading) > 0.3) {
                val step = (m.pos - o.pos).clampLength(BACK_CM_S * c.dt)
                out.place(o.pos + step, m.heading, Vec3.Y, 12.0)
            } else walk(c, out, m.pos, o.cruiseSpeed * 3)
            return
        }
        val r = inside(c)
        out.place(r.pos, r.heading, Vec3.Y, rate)
        out.posture = 3
        o.visible = false
    }

    /** A crawl over the sand to [to], legs down, never onto the glass. */
    private fun walk(c: Ctx, out: SteerIntent, to: Vec3, speed: Double, arrive: Double = 0.1 * c.L) {
        // a stiff-bodied walker on the open sand: it turns on the spot, then walks head first, and goes round the
        // rock instead of over it (postSteer lays the body along the bed)
        val L = c.L
        val fh = horizontalHeading(o.heading, axis)
        val flat = (to - o.pos).withY(0.0)
        val dist = flat.length()
        if (dist < arrive) { out.place(o.pos, fh, Vec3.Y, KIN_RATE); return }
        val want = flat / dist
        var dir = want
        for (k in 0 until DETOURS.size) {
            val d = Vec3.rotateAbout(want, Vec3.Y, DETOURS[k] * side)
            if (open(c, o.pos + d * (WALK_PROBE_BL * L))) { dir = d; if (k > 0 && k % 2 == 0) side = -side; break }
        }
        val h = horizontalHeading(Vec3.rotateToward(fh, dir, TURN_RAD_S * c.dt, Vec3.Y cross fh), fh)
        val step = if ((h dot dir) > 0.7) min(speed * c.dt, dist) else 0.0
        val np = o.pos + h * step
        val (p, hd) = when {
            step > 0 && fits(c, np, h) -> np to h
            fits(c, o.pos, h) -> o.pos to h
            else -> o.pos to fh
        }
        out.place(p, hd, Vec3.Y, KIN_RATE)
    }

    /** Turns on the spot toward [dir], as far as the rock round it lets the tail swing. */
    private fun face(c: Ctx, out: SteerIntent, dir: Vec3) {
        val fh = horizontalHeading(o.heading, axis)
        val want = horizontalHeading(dir, fh)
        val h = horizontalHeading(Vec3.rotateToward(fh, want, TURN_RAD_S * c.dt, Vec3.Y cross fh), fh)
        out.place(o.pos, if (fits(c, o.pos, h)) h else fh, Vec3.Y, KIN_RATE)
    }

    /** The whole body, head at [p] and tail fan back along [h], has room on the sand. */
    private fun fits(c: Ctx, p: Vec3, h: Vec3): Boolean {
        for (k in 0..4) if (!open(c, p - h * (c.L * k / 4.0))) return false
        return true
    }

    /** Which way it last went round an obstacle (it keeps to one side rather than dithering). */
    private var side = 1.0

    /** Room for the body on the sand at [p]: off the rock, inside the glass. */
    private fun open(c: Ctx, p: Vec3): Boolean {
        val m = GLASS_BL * c.L
        if (p.x < m || p.z < m || p.x > c.rock.lengthCm - m || p.z > c.rock.widthCm - m) return false
        return room(c, p) >= WALK_CLEAR_BL * c.L
    }

    private fun release() { prey?.let { if (it.heldBy === o) it.heldBy = null }; prey = null }

    // ---------------------------------------------------------------- behaviors

    /** Down the burrow: all night, and stretches of the day (it spends much of its time inside [I]). */
    inner class InBurrow : Behavior("burrow", "In its burrow") {
        private var boutEnd = -1.0
        private var nextBout = -1.0
        override fun score(c: Ctx): Double {
            if (c.p.night) return 0.6
            // by day it goes down for rests of a few minutes between spells at the mouth
            if (nextBout < 0) nextBout = c.t + c.rng.range(DAY_OUT_MIN_S, DAY_OUT_MAX_S)
            if (c.t > nextBout) {
                boutEnd = c.t + c.rng.range(DAY_IN_MIN_S, DAY_IN_MAX_S)
                nextBout = boutEnd + c.rng.range(DAY_OUT_MIN_S, DAY_OUT_MAX_S)
            }
            return if (c.t < boutEnd) 0.55 else 0.3
        }
        override fun update(c: Ctx, out: SteerIntent) { goDown(c, out, 3.0) }
        override fun exit(c: Ctx) { surface(c) }
    }

    /** Startled or chased: a fast backwards dart down the hole, and it stays in a while. */
    inner class Duck : Behavior("duck", "Darting into its burrow") {
        private var until = 0.0
        override val committed: Boolean get() = !finished
        override fun score(c: Ctx): Double {
            if (threat?.alive == true || startledRecently && startleMag > 0.1) return 0.97
            return if (c.t < until) 0.9 else 0.0
        }
        override fun enter(c: Ctx) {
            startleAt = -1e9; until = c.t + c.rng.range(DUCK_MIN_S, DUCK_MAX_S)
            release()
            meal?.let { it.heldBy = null }; meal = null
        }
        override fun update(c: Ctx, out: SteerIntent) {
            goDown(c, out, 20.0)
            if (c.t > until && threat == null) finished = true
        }
        override fun exit(c: Ctx) { surface(c) }
    }

    /**
     * At the burrow mouth by day, eyes up and swivelling, antennules flicking. A fish that comes close gets the meral
     * spread (clubs raised and opened, the threat display [M]); a small one right at the mouth may be struck.
     */
    inner class Watch : Behavior("watch", "Watching from its burrow") {
        private val fidget = ShrimpFidget()
        private var swapAt = -1.0
        override fun score(c: Ctx) = if (c.p.night) 0.0 else 0.45
        override fun enter(c: Ctx) { if (swapAt < 0) swapAt = c.t + c.rng.range(600.0, 1800.0) }
        override fun update(c: Ctx, out: SteerIntent) {
            if (c.t > swapAt && !c.o.visible) { atB = !atB; swapAt = c.t + c.rng.range(600.0, 1800.0) }   // up the other arm of the U
            val r = mouth(c, 0.15)
            if (c.o.pos.distanceTo(r.pos) > 0.2 * c.L) { walk(c, out, r.pos, c.o.cruiseSpeed * 1.5); return }
            fidget.rest(c, out, r, 2.0)
            c.o.eyeYaw = 0.6 * sin(2 * PI * 0.13 * c.t + c.o.appendagePhase)
            if (c.t < nextFishLook) return
            nextFishLook = c.t + 1.0
            val fish = c.p.neighbors(c.o.pos, FISH_CM * c.L).firstOrNull { it.alive && it.isFish && it.visible && it.lengthCm < c.L * 0.9 } ?: return
            val d = fish.pos.distanceTo(c.o.pos)
            if (c.o.clawDisplay < 0.2) { c.o.clawDisplay = 1.0; displays++ }
            // research 60 §11: it eats fish too. Only a small one near the bottom, within the club's reach, and not often [E]
            if (fish.lengthCm < FISH_PREY_BL * c.L && d < STRIKE_BL * c.L + 0.3 * fish.lengthCm && fish.pos.y < bedY(fish.pos.x, fish.pos.z) + 0.6 * c.L &&
                c.o.hunger > 0.3 && c.t > c.o.satietyUntil && c.rng.chance(FISH_HIT_P)) {
                limbs?.smash(c.o, fish.pos)
                c.sim.emit(SimEvent.Snap(c.o.id, fish.id, c.sim.simTimeSec))
                fishStrikes++; strikes++
                c.sim.eaten(c.o, fish)
                kills++
            } else if (fish.lengthCm >= FISH_PREY_BL * c.L) c.sim.agentOf(fish)?.let { a -> if (d < 0.5 * c.L) a.brain.onAttacked(a.ctx, c.o.pos, 0.3, c.o) }
        }
    }

    /**
     * Keeping the burrow open: down one arm, up with a load, out a little way, dropped on the heap, and back. A new
     * burrow takes many loads (it starts digging as soon as it is put in).
     */
    inner class Dig : Behavior("dig", "Carrying sand out of its burrow") {
        private var phase = 0
        private var next = -1.0
        override fun score(c: Ctx): Double {
            if (c.o.molting || c.p.night) return 0.0
            if (next < 0) next = c.t + c.rng.range(5.0, 30.0)
            if (c.t < next) return 0.0
            return if (loads < FIRST_LOADS) 0.6 else 0.5
        }
        override fun enter(c: Ctx) { phase = 0 }
        override fun update(c: Ctx, out: SteerIntent) {
            val (m, dir) = door(c)
            c.o.mouth = max(c.o.mouth, 0.6 + 0.3 * sin(2 * PI * 2.0 * c.t))
            if (phase == 0) {
                val to = mouth(c, DUMP_BL).pos
                walk(c, out, to, c.o.cruiseSpeed)
                if (c.o.pos.distanceTo(to) < 0.1 * c.L || elapsed > 30) {
                    val L = c.L
                    c.sim.sand.dig(m.x, m.z, LOAD_CM3 * L / 12.0, 0.15 * L)
                    c.sim.sand.deposit(to.x + dir.x * 0.1 * L, to.z + dir.z * 0.1 * L, LOAD_CM3 * L / 12.0, 0.12 * L)
                    c.sim.sand.puff(to.x, bedY(to.x, to.z) + 0.3, to.z, 4, 1.5)
                    loads++
                    phase = 1
                }
            } else {
                goDown(c, out, 6.0)
                if (!c.o.visible) {
                    next = c.t + if (loads < FIRST_LOADS) c.rng.range(10.0, 40.0) else c.rng.range(DIG_GAP_MIN_S, DIG_GAP_MAX_S)
                    finished = true
                }
            }
        }
        override fun exit(c: Ctx) { surface(c) }
    }

    /** Meaty food that settles near the burrow: out, grab it in the small maxillipeds, back. */
    inner class Forage : Behavior("scavenge", "Grabbing food by its burrow") {
        private var f: com.reeflive.core.sim.FoodParticle? = null
        override fun score(c: Ctx): Double {
            if (c.o.hunger < 0.2 || c.o.molting || meal != null) return 0.0
            val b = door(c).first
            f = c.findFood(FORAGE_BL * c.L + 5, los = false) { it.settled && it.pos.distanceTo(b) < FORAGE_BL * c.L }
            return if (f != null) 0.62 + 0.3 * c.o.hunger else 0.0
        }
        override fun update(c: Ctx, out: SteerIntent) {
            val ff = f
            if (ff == null || ff.eaten) { finished = true; return }
            walk(c, out, ff.pos, c.o.cruiseSpeed * 2, 0.1 * c.L)
            if (c.o.pos.distanceTo(ff.pos) < 0.15 * c.L + 0.6) { c.bite(ff); f = null; finished = true }
            if (elapsed > 40) finished = true
        }
    }

    /** Off over the sand to a snail, hermit, crab or small shrimp near the burrow (it hunts by day, research 62 §2). */
    inner class Hunt : Behavior("hunt", "Stalking prey") {
        private var nextLook = 0.0
        override val targetVerb: String? get() = "stalking"
        override val label: String get() = "Stalking ${preyWord(prey)}"
        override fun score(c: Ctx): Double {
            if (meal != null || c.o.molting || c.p.night || c.t < c.o.satietyUntil || c.o.hunger < HUNT_HUNGER) return 0.0
            val p = prey
            if (p != null && p.alive) return 0.58 + 0.3 * c.o.hunger
            if (c.t < nextLook) return 0.0
            nextLook = c.t + 20.0
            prey = pick(c)
            return if (prey != null) 0.58 + 0.3 * c.o.hunger else 0.0
        }
        override fun update(c: Ctx, out: SteerIntent) {
            val p = prey
            if (p == null || !p.alive || p.heldBy != null && p.heldBy !== c.o) { prey = null; finished = true; return }
            val d = p.pos.distanceTo(c.o.pos)
            if (d > STRIKE_BL * c.L + 0.3 * p.lengthCm) {
                walk(c, out, approach(c, p), c.o.cruiseSpeed * 2, 0.05 * c.L)
                if (elapsed > 120) { release(); finished = true }
                return
            }
            // in range: it pins the prey with the next strike
            p.heldBy = c.o
            finished = true
        }
    }

    /** Hammering a shell with the clubs, a strike every second or two, until it cracks. */
    inner class Smash : Behavior("smash", "Smashing a shell") {
        private var need = 0
        private var hits = 0
        private var nextHit = 0.0
        override val targetVerb: String? get() = "smashing"
        override val label: String get() = "Smashing ${preyWord(prey)}"
        override val committed: Boolean get() = prey != null && threat == null
        override fun score(c: Ctx) = if (prey?.heldBy === c.o && meal == null) 0.92 else 0.0
        override fun enter(c: Ctx) {
            val p = prey ?: return
            hits = 0; nextHit = c.t + c.rng.range(0.3, 1.0)
            need = when {
                p.brainKind.isSnail -> SNAIL_HITS_MIN + c.rng.nextInt(SNAIL_HITS_MAX - SNAIL_HITS_MIN + 1)
                p.brainKind == BrainKind.HERMIT -> HERMIT_HITS_MIN + c.rng.nextInt(HERMIT_HITS_MAX - HERMIT_HITS_MIN + 1)
                p.brainKind.isCrab || p.brainKind == BrainKind.URCHIN -> 2 + c.rng.nextInt(4)
                else -> 1 + c.rng.nextInt(2)
            }
        }
        override fun update(c: Ctx, out: SteerIntent) {
            val p = prey ?: run { finished = true; return }
            face(c, out, p.pos - c.o.pos)
            c.o.clawDisplay = max(c.o.clawDisplay, 0.3)
            if (c.t < nextHit) return
            limbs?.smash(c.o, p.pos)
            c.sim.emit(SimEvent.Snap(c.o.id, p.id, c.sim.simTimeSec))
            c.sim.sand.puff(p.pos.x, bedY(p.pos.x, p.pos.z) + 0.2, p.pos.z, 2, 1.0)
            strikes++; hits++
            nextHit = c.t + c.rng.range(HIT_GAP_MIN_S, HIT_GAP_MAX_S)
            if (hits >= need) {
                // cracked: the meal is carried home
                meal = p; prey = null; kills++
                finished = true
            }
        }
        override fun exit(c: Ctx) { if (meal == null) release() }
    }

    /** Back to the burrow with the cracked meal under its head, and eaten at the mouth. */
    inner class Carry : Behavior("eatprey", "Eating its catch") {
        private var eatFor = 0.0
        private var eating = false
        override val committed: Boolean get() = meal != null && threat == null
        override val label: String get() = if (eating) "Eating ${preyWord(meal)}" else "Carrying ${preyWord(meal)} home"
        override fun score(c: Ctx) = if (meal != null) 0.93 else 0.0
        override fun enter(c: Ctx) { eating = false; eatFor = c.rng.range(MEAL_MIN_S, MEAL_MAX_S) }
        override fun update(c: Ctx, out: SteerIntent) {
            val m = meal ?: run { finished = true; return }
            val r = mouth(c, 0.15)
            if (!eating) {
                walk(c, out, r.pos, c.o.cruiseSpeed * 1.5)
                if (c.o.pos.distanceTo(r.pos) < 0.2 * c.L || elapsed > 90) { eating = true; elapsed = 0.0 }
                return
            }
            out.rest(r, 2.0)
            c.o.mouth = 0.6 + 0.35 * sin(2 * PI * 1.6 * c.t)
            if (elapsed > eatFor) {
                meal = null
                m.heldBy = null
                c.sim.eaten(c.o, m)
                finished = true
            }
        }
    }

    // ---------------------------------------------------------------- prey

    /** The best prey near the burrow: by [Predation.preyWeight], nearer first; low on the sand or rock foot, reachable over open sand. */
    private fun pick(c: Ctx): Organism? {
        val home = door(c).first
        var best: Organism? = null; var bs = 0.0
        for (n in c.sim.organisms) {
            if (n === o || !n.alive || n.isFish || n.heldBy != null || !n.visible) continue
            val w = Predation.preyWeight(o, n, c.p.night)
            if (w <= 0) continue
            val d = n.pos.distanceTo(home)
            if (d > SEARCH_BL * c.L) continue
            if (n.pos.y > bedY(n.pos.x, n.pos.z) + REACH_UP_BL * c.L) continue
            if (!lane(c, c.o.pos, approach(c, n), 1.0)) continue
            val s = w / (1.0 + d / c.L)
            if (s > bs) { bs = s; best = n }
        }
        return best
    }

    /** Where it stands to strike [p]: on the sand short of it, head toward it, inside the glass. */
    private fun approach(c: Ctx, p: Organism): Vec3 {
        val from = (c.o.pos - p.pos).withY(0.0).normalized(axis)
        val q = p.pos + from * (0.6 * STRIKE_BL * c.L)
        val r = c.rock
        val x = q.x.coerceIn(0.3 * c.L, r.lengthCm - 0.3 * c.L)
        val z = q.z.coerceIn(0.3 * c.L, r.widthCm - 0.3 * c.L)
        return Vec3(x, bedY(x, z) + stance, z)
    }

    private fun preyWord(p: Organism?): String = when {
        p == null -> "prey"
        p.brainKind.isSnail -> "a snail"
        p.brainKind == BrainKind.HERMIT -> "a hermit crab"
        p.brainKind.isCrab -> "a crab"
        p.brainKind.isShrimp -> "a shrimp"
        p.isFish -> "a fish"
        else -> "its prey"
    }

    companion object {
        /** The burrow: openings this many body lengths apart (a U, research 60 §11), this far off the rock, cm [E]. */
        @Ev(Evidence.DESIGN_ESTIMATE, "62 §2 WP: U-shaped burrows near the reef") const val SPAN_BL = 1.5
        const val ROCK_MIN_CM = 4.0
        const val ROCK_MAX_CM = 14.0
        /** Sand moved to open each pit, and one load carried out, cm³ for a 12 cm animal [E]. */
        const val PIT_CM3 = 6.0
        const val LOAD_CM3 = 1.0
        /** Loads to finish a new burrow; then upkeep every few minutes by day [E]. */
        const val FIRST_LOADS = 12
        const val DIG_GAP_MIN_S = 300.0
        const val DIG_GAP_MAX_S = 1500.0
        /** A load is dropped this far (body lengths) out of the mouth. */
        const val DUMP_BL = 0.6
        /** Clear sand needed beyond each opening (body lengths): the head out and the heap. */
        const val OUT_BL = 0.9
        /** Backing into the burrow, cm/s [E]. */
        const val BACK_CM_S = 12.0
        /** Daytime spells out at the mouth and rests down the burrow, s [E: "spends much of its time in the burrow"]. */
        const val DAY_OUT_MIN_S = 240.0
        const val DAY_OUT_MAX_S = 720.0
        const val DAY_IN_MIN_S = 90.0
        const val DAY_IN_MAX_S = 360.0
        /** Walking: turn rate, how far ahead it feels for rock, and the room it keeps from rock and glass [E]. */
        const val TURN_RAD_S = 2.5
        const val WALK_PROBE_BL = 0.3
        const val WALK_CLEAR_BL = 0.1
        const val GLASS_BL = 0.3
        const val KIN_RATE = 1000.0
        private val DETOURS = doubleArrayOf(0.0, 0.5, -0.5, 1.0, -1.0, 1.6, -1.6, 2.2, -2.2)
        const val DUCK_MIN_S = 20.0
        const val DUCK_MAX_S = 90.0
        /** It hunts this hungry, this far from the burrow (body lengths), prey no higher than this over the sand [E]. */
        @Ev(Evidence.DESIGN_ESTIMATE, "62 §2: fed 2-3 times a week in care guides; Simulation.eaten's 3-day satiety") const val HUNT_HUNGER = 0.35
        const val SEARCH_BL = 4.0
        const val REACH_UP_BL = 0.35
        /** The club reaches this far (body lengths) from the head. */
        const val STRIKE_BL = 0.35
        /** Strikes to crack a shell, and the gap between strikes, s (smashers hit repeatedly; Patek 2004 times one strike at ~3 ms) [E]. */
        @Ev(Evidence.DESIGN_ESTIMATE, "62 §2") const val SNAIL_HITS_MIN = 3
        const val SNAIL_HITS_MAX = 10
        const val HERMIT_HITS_MIN = 2
        const val HERMIT_HITS_MAX = 6
        const val HIT_GAP_MIN_S = 0.8
        const val HIT_GAP_MAX_S = 2.5
        /** A meal at the mouth, s [E]. */
        const val MEAL_MIN_S = 300.0
        const val MEAL_MAX_S = 900.0
        /** Food this near the burrow (body lengths) is fetched. */
        const val FORAGE_BL = 2.0
        /** Fish within this (body lengths) get the meral spread; fish under [FISH_PREY_BL] can be struck, at [FISH_HIT_P] a second [E]. */
        const val FISH_CM = 0.8
        /** The belly's clearance over the sand (body lengths, mantis_shrimp.glb: 0.062 deep) and the steepest the body tilts. */
        const val BELLY_BL = 0.075
        const val MAX_TILT = 0.5
        const val FISH_PREY_BL = 0.45
        @Ev(Evidence.DESIGN_ESTIMATE, "62 §2 WP: eats fish") const val FISH_HIT_P = 0.15

        /** Abstract mode (no behaviour layer): a hungry mantis eats a snail, hermit or crab from the tank. */
        fun abstractFeed(o: Organism, sim: Simulation) {
            if (o.hunger < HUNT_HUNGER || sim.simTimeSec < o.satietyUntil) return
            val p = sim.organisms.firstOrNull { it.alive && it !== o && !it.isFish && Predation.preyWeight(o, it, false) > 0 } ?: return
            sim.eaten(o, p)
        }
    }
}
