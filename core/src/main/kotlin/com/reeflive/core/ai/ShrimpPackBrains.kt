package com.reeflive.core.ai

import com.reeflive.core.geom.Vec3
import com.reeflive.core.sim.BrainKind
import com.reeflive.core.sim.Coral
import com.reeflive.core.sim.CoralKind
import com.reeflive.core.sim.Ev
import com.reeflive.core.sim.Evidence
import com.reeflive.core.sim.HealthModel
import com.reeflive.core.sim.Organism
import com.reeflive.core.sim.SimEvent
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// =====================================================================================
// Shrimp pack (research 44): fire shrimp, sexy shrimp, harlequin shrimp and tiger pistol shrimp. They share the
// Lysmata leg rig (core ShrimpLimbs), molting, tail-flip escapes and the shrimp rules; each brain here adds what is
// its own: the fire shrimp's cave and shy cleaning (a [CleanerShrimpBrain] with shy = true), the sexy shrimp's
// host and tail wag, the harlequin pair's starfish hunt, and the pistol shrimp's goby burrow, bulldozing and snap.
// =====================================================================================

/**
 * A shrimp's walk over the rock around [home] when [drive] says it is out, stopping to pick at the surface. The same
 * night foray as the peppermint's (research 42 §1.4), shared by the new shrimp.
 */
class ShrimpRoam(val b: SpeciesBrain, val nav: ShrimpNav, val home: (Ctx) -> Vec3, val radius: Double,
                 val drive: (Ctx) -> Double, label: String) : Behavior("crawl", label) {
    private val baseLabel = label
    private var wp: Rest? = null
    private var next = 0.0
    private var dwellUntil = -1.0
    override val label: String get() = if (b.c.t < dwellUntil) "Picking at the rock" else baseLabel
    override fun score(c: Ctx) = drive(c)
    override fun enter(c: Ctx) { next = 0.0; nav.reset() }
    private fun pick(c: Ctx, around: Vec3, r: Double) {
        val hit = c.rockSurfacePoint(around, r, minNormalY = -1.0)
        wp = if (hit != null) Rest(hit.first + hit.second * shrimpStance(c.o), c.o.heading, hit.second, 0)
        else Rest(sandSpot(c, (around.x - r)..(around.x + r), 8.0..(c.rock.widthCm - 3), shrimpStance(c.o)), c.o.heading, Vec3.Y, 0)
        next = c.t + c.rng.range(25.0, 70.0)
    }
    override fun update(c: Ctx, out: SteerIntent) {
        if (wp == null || c.t > next) pick(c, home(c), radius)
        val w = wp!!
        if (c.t < dwellUntil) {
            out.crawl = true; out.target = null; out.surfaceOffset = shrimpStance(c.o)
            c.o.mouth = max(c.o.mouth, 0.5 + 0.3 * sin(2 * PI * 1.8 * c.t))
            return
        }
        // on the move the picking legs keep tapping the rock ahead, lighter and slower than a picking stop
        c.o.mouth = max(c.o.mouth, 0.3 + 0.2 * sin(2 * PI * 1.2 * c.t))
        when (nav.drive(c, out, w.pos, w.up, c.o.cruiseSpeed, arrive = 1.2, canHop = c.rock.envDist(c.o.pos) > shrimpStance(c.o) + 1.5)) {
            ShrimpNav.Status.ARRIVED -> {
                dwellUntil = c.t + c.rng.range(4.0, 14.0); next = dwellUntil + 1.0; wp = null
                out.crawl = true; out.surfaceOffset = shrimpStance(c.o)
            }
            ShrimpNav.Status.STALLED -> pick(c, c.o.pos, 12.0)
            else -> {}
        }
    }
}

/**
 * Idle life for a resting shrimp (the peppermint's fix, thread "Peppermint shrimp and flame scallop"): every 4-9 s the
 * rest spot shifts a few mm so the feet re-plant, and every 5-14 s a 2.5-6 s picking bout drives the small chelae and
 * maxillipeds (o.mouth); the antennae sweep at 1.6 rad/s, 3 while picking. Without it a resting shrimp stands frozen.
 */
class ShrimpFidget {
    private var nudgeAt = -1.0
    private var off = Vec3.ZERO
    private var pickFrom = -1.0
    private var pickTo = -1.0
    fun rest(c: Ctx, out: SteerIntent, r: Rest, rate: Double) {
        if (nudgeAt < 0) { nudgeAt = c.t + c.rng.range(4.0, 9.0); pickFrom = c.t + c.rng.range(5.0, 14.0) }
        if (c.t > nudgeAt) {
            val up = r.up ?: Vec3.Y
            off = (c.rng.unitVector().rejectFrom(up)).normalized(Vec3.X) * (FIDGET_BL * c.L)
            nudgeAt = c.t + c.rng.range(4.0, 9.0)
        }
        if (c.t > pickTo && c.t > pickFrom) { pickTo = c.t + c.rng.range(2.5, 6.0); pickFrom = pickTo + c.rng.range(5.0, 14.0) }
        val picking = c.t < pickTo
        out.rest(Rest(r.pos + off, r.heading, r.up, r.posture), rate)
        if (picking) c.o.mouth = max(c.o.mouth, 0.5 + 0.3 * sin(2 * PI * 1.8 * c.t))
        c.o.appendagePhase += c.dt * (if (picking) 3.0 else 1.6)
    }
    companion object { const val FIDGET_BL = 0.06 }
}

/** A deeply shaded rock face near [near] for a shrimp to hide on (an overhang roof or the side of a cave). */
internal fun shadedPerch(c: Ctx, near: Vec3, radius: Double, taken: List<Vec3>): Rest =
    shrimpPerch(c, near, radius, 2, taken, Vec3(0.0, 0.0, 1.0),
        accept = { _, n -> n.y < 0.5 },
        score = { q, n -> q.distanceTo(near) * 0.3 + 8.0 * (1 - c.rock.sdf.cover(q + n * 2.0)) + 2.0 * max(0.0, n.y) })
        ?: hangPose(c, null, near, inverted = false)

/** A ledge to start a search from: spread out from [avoid] (other shrimp), varied per animal. */
internal fun ledgeNear(c: Ctx, avoid: List<Vec3>): Vec3 =
    c.rock.anchors.ofType("LEDGE").maxByOrNull { a ->
        (avoid.minOfOrNull { it.distanceTo(a.pos) } ?: 50.0).coerceAtMost(40.0) + 3.0 * sin(a.name.hashCode() + c.o.id.hashCode().toDouble())
    }?.pos ?: c.o.homePos.takeIf { it != Vec3.ZERO } ?: Vec3(c.rock.lengthCm * 0.5, c.rock.sandTopCm + 8, c.rock.widthCm * 0.5)

// =====================================================================================
// Fire shrimp (Lysmata debelius, research 44 §1): a CleanerShrimpBrain with shy = true. It keeps a dark cave instead of
// an open station, cleans fish that come to the cave mouth far less eagerly than the skunk cleaner ("not as earnest"),
// and walks the rock at night. One per tank or a pair: others of its kind are chased (Interactions) and a third one
// is at risk after a molt (interactions.json fire_fire_molt).
// =====================================================================================
class FireShrimpBrain(c: Ctx) : CleanerShrimpBrain(c, shy = true) {
    companion object {
        /** Share of the skunk cleaner's cleaning offers it makes (research 44 §1: a facultative, "not as earnest" cleaner) [E]. */
        @Ev(Evidence.DESIGN_ESTIMATE, "44 §1 care guides") const val CLEAN_SHARE = 0.4
        /** Night foray radius around its cave, cm [E]. */
        const val NIGHT_FORAY_CM = 30.0
    }
}

// =====================================================================================
// Sexy shrimp (Thor amboinensis, research 44 §3): a 1.5 cm shrimp living in a little group on a host (a bubble-tip
// anemone, else a mushroom, zoanthid or other soft coral). It sits among the host's tentacles with its abdomen cocked
// up over its back and sways and vibrates it as it walks (the name), picks at the host's mucus and trapped plankton,
// and grabs food that falls near. Day-active around its host (est.).
// =====================================================================================
class SexyShrimpBrain(c: Ctx) : SpeciesBrain(c) {
    val nav = ShrimpNav(o)
    private var spot: Rest? = null
    private var spotAt = -1e9
    /** The host it lives on: an anemone, else a coral colony (tests, captions). */
    var hostAnemone: Organism? = null; private set
    var hostCoral: Coral? = null; private set
    /** Wag cycles shown (tests). */
    var wagS = 0.0; private set
    var mucusPicks = 0; private set

    init {
        install(Dance(), HostPick(), ShrimpForage(this, nav, 14.0, 6.0, "Grabbing a bit of food"),
            MoltHide(this) { home(it) }, TailFlip(this, nav) { home(it) })
    }

    override fun restSpot(c: Ctx, night: Boolean) = home(c)

    /** Surface point under its spot (for spacing the group). */
    val spotPoint: Vec3? get() = spot?.let { it.pos - (it.up ?: Vec3.Y) * shrimpStance(o) }

    private fun findHost(c: Ctx): Vec3 {
        val from = o.homePos.takeIf { it != Vec3.ZERO } ?: o.pos
        val a = c.sim.organisms.filter { it.alive && it.brainKind == BrainKind.ANEMONE }.minByOrNull { it.pos.distanceTo(from) }
        hostAnemone = a
        if (a != null) { hostCoral = null; return a.pos }
        // research 44 §3: "uses some corals when no anemone is available"; mushrooms and zoanthids are the usual stand-ins
        val k = c.sim.corals.filter { it.tissue > 0.3 && hostWeight(it.kind) > 0 }
            .maxByOrNull { hostWeight(it.kind) - 0.01 * it.pos.distanceTo(from) }
        hostCoral = k
        return k?.pos ?: from
    }

    /** Its spot on the host: on the rock right beside the anemone's foot or the coral, next to the rest of the group. */
    fun home(c: Ctx): Rest {
        spot?.let { s ->
            val hostGone = hostAnemone?.alive == false || hostCoral?.let { it.tissue < 0.1 } == true
            if (!hostGone && c.t - spotAt < 3600 && kotlin.math.abs(c.rock.envDist(s.pos) - shrimpStance(o)) < 0.8) return s
        }
        val h = findHost(c)
        val mates = c.sim.organisms.filter { it !== o && it.alive && it.brainKind == BrainKind.SEXY_SHRIMP }
            .mapNotNull { (c.sim.agentOf(it)?.brain as? SexyShrimpBrain)?.spotPoint }
        val r = shrimpPerch(c, h, HOST_CM, 1, mates, Vec3(0.0, 1.0, 0.3),
            accept = { _, n -> n.y > -0.3 },
            score = { q, _ -> q.distanceTo(h) }) ?: hangPose(c, null, h, inverted = false)
        spot = r; spotAt = c.t
        return r
    }

    override fun initialPlace(c: Ctx) {
        o.homePos = findHost(c)
        super.initialPlace(c)
    }

    override fun preTick(c: Ctx) {
        super.preTick(c)
        o.groom = max(0.0, o.groom - c.dt * 2)
    }

    /** The tail wag: the cocked abdomen bobs and sways, faster while it walks (research 44 §3; rate est.). */
    private fun wag(c: Ctx, walking: Boolean) {
        val hz = if (walking) WAG_HZ_WALK else WAG_HZ
        o.bend = WAG_AMP * (0.5 + 0.5 * sin(2 * PI * hz * c.t + o.appendagePhase))
        wagS += c.dt
    }

    /** On its host: shuffling about a little, tail up and wagging, antennae sweeping. */
    inner class Dance : Behavior("dance", "Dancing on its host") {
        private val fidget = ShrimpFidget()
        override val label: String get() = when {
            hostAnemone != null -> "Dancing on its anemone"
            hostCoral != null -> "Dancing on its coral"
            else -> "Dancing on the rock"
        }
        override fun score(c: Ctx) = 0.5
        override fun enter(c: Ctx) { nav.reset() }
        override fun update(c: Ctx, out: SteerIntent) {
            val r = home(c)
            if (c.o.pos.distanceTo(r.pos) > 1.2 || nav.swimming) {
                nav.drive(c, out, r.pos, r.up, c.o.cruiseSpeed * 1.3, arrive = 0.6)
                wag(c, true)
                return
            }
            fidget.rest(c, out, r, 2.0)
            out.rollExtra = 0.08 * sin(2 * PI * 0.7 * c.t + c.o.appendagePhase)
            wag(c, false)
        }
    }

    /** Picks mucus and trapped plankton off its host now and then (research 44 §3: lives on the host's mucus). */
    inner class HostPick : Behavior("hostpick", "Picking at its host") {
        private var next = -1.0
        override val label: String get() = if (hostAnemone != null) "Picking food off its anemone" else "Picking food off its coral"
        override fun score(c: Ctx): Double {
            if (next < 0) next = c.t + c.rng.range(60.0, 300.0)
            return if (c.t > next && (hostAnemone != null || hostCoral != null) && c.o.hunger > 0.15) 0.6 else 0.0
        }
        override fun update(c: Ctx, out: SteerIntent) {
            val r = home(c)
            if (c.o.pos.distanceTo(r.pos) > 1.2) { nav.drive(c, out, r.pos, r.up, c.o.cruiseSpeed, arrive = 0.6); return }
            out.rest(r, 2.0)
            c.o.mouth = 0.5 + 0.4 * sin(2 * PI * 2.0 * c.t)
            wag(c, false)
            // the host's mucus and the plankton caught on it: about a third of its needs while it picks [E]
            c.o.hunger = max(0.0, c.o.hunger - HealthModel.hungerRatePerMin(c.o, c.sim.water.s.tempC) * 20.0 * c.simDt / 60.0)
            if (elapsed > c.rng.range(8.0, 20.0)) { mucusPicks++; next = c.t + c.rng.range(120.0, 600.0); finished = true }
        }
    }

    companion object {
        /** How far from the host's centre (cm) the group sits. */
        const val HOST_CM = 6.0
        /** Tail wag: abdomen flex amplitude (shader bend units) and rate at rest / walking (research 44 §3: no rate found; 1-3 Hz est.). */
        @Ev(Evidence.DESIGN_ESTIMATE, "44 §3 (no measured rate)") const val WAG_AMP = 0.22
        const val WAG_HZ = 1.2
        const val WAG_HZ_WALK = 2.2
        fun hostWeight(k: CoralKind): Double = when (k) {
            CoralKind.MUSHROOM -> 1.0
            CoralKind.ZOA -> 0.8
            CoralKind.CLOVE, CoralKind.GSP -> 0.5
            CoralKind.KENYA, CoralKind.LEATHER -> 0.3
            else -> 0.0
        }
    }
}

// =====================================================================================
// Harlequin shrimp (Hymenocera picta, research 44 §2): a pair hidden under the rock by day that creeps out, slowly and
// in waves, its flat claws waving almost all the time, to hunt starfish: here the asterina stars on the glass and
// rock. It flips the star onto its back so it cannot crawl off, and eats it over a few hours, tube feet first; its
// mate comes over to share. It eats nothing else: no stars, no food (it slowly starves). About one small star every
// 5-7 days for a pair (care guide), which the 3-day satiety after a meal and its slow hunger give.
// =====================================================================================
class HarlequinShrimpBrain(c: Ctx) : SpeciesBrain(c) {
    val nav = ShrimpNav(o)
    private var den: Rest? = null
    /** The star it is eating, flipped on its back and held (tests, captions). */
    var meal: Organism? = null; private set
    var starsCaught = 0; private set
    var starsEaten = 0; private set
    var mealShares = 0; private set

    init {
        install(Hide(), Hunt(), Eat(), Share(),
            ShrimpRoam(this, nav, { den(it).pos }, 25.0, { c -> if (c.p.night && !c.o.molting) 0.3 * (0.5 + c.o.hunger) else 0.0 }, "Creeping over the rock"),
            MoltHide(this) { den(it) }, TailFlip(this, nav) { den(it) })
    }

    override fun restSpot(c: Ctx, night: Boolean) = den(c)

    val denPoint: Vec3? get() = den?.let { it.pos - (it.up ?: Vec3.Y) * shrimpStance(o) }

    private val mateBrain: HarlequinShrimpBrain?
        get() = c.sim.organisms.firstOrNull { it !== o && it.alive && it.brainKind == BrainKind.HARLEQUIN_SHRIMP }
            ?.let { c.sim.agentOf(it)?.brain as? HarlequinShrimpBrain }

    /** Under the rock: a shaded face; a pair shares one (research 44 §2: pairs live and hunt together). */
    fun den(c: Ctx): Rest {
        den?.let { d -> if (kotlin.math.abs(c.rock.sdf.dist(d.pos) - shrimpStance(o)) < 0.6) return d }
        val m = mateBrain?.denPoint
        val near = m ?: ledgeNear(c, emptyList())
        den = shadedPerch(c, near, 12.0, listOfNotNull(m))
        return den!!
    }

    override fun preTick(c: Ctx) {
        super.preTick(c)
        // the flat claws are never still: a slow wave, wider while it walks or works on a star (research 44 §2)
        val k = if (meal != null) 0.45 else 0.2 + 0.15 * c.o.walkAmt
        o.clawDisplay = k * (0.5 + 0.5 * sin(2 * PI * CLAW_HZ * c.t + o.appendagePhase))
        meal?.let { s -> if (!s.alive || s !in c.sim.organisms) meal = null }
    }

    /** Light keeps it under the rock; hunger brings it out sooner. */
    private fun outDrive(c: Ctx) = (1 - 0.85 * dayLight(c)) * (0.6 + 0.4 * c.o.hunger)

    inner class Hide : Behavior("hide", "Hiding under the rock") {
        private val fidget = ShrimpFidget()
        override val label: String get() = if (nav.swimming || c.o.pos.distanceTo(den(c).pos) > 2.0) "Creeping back under the rock" else "Hiding under the rock"
        override fun score(c: Ctx) = 0.5 * (1.2 - outDrive(c)).coerceIn(0.1, 1.0)
        override fun enter(c: Ctx) { nav.reset() }
        override fun update(c: Ctx, out: SteerIntent) {
            val r = den(c)
            if (c.o.pos.distanceTo(r.pos) > 1.5 || nav.swimming) {
                if (nav.drive(c, out, r.pos, r.up, c.o.cruiseSpeed, arrive = 1.0) != ShrimpNav.Status.ARRIVED) return
            }
            fidget.rest(c, out, r, 1.5)
        }
    }

    /** Off to the nearest star nobody is eating; the mate's target if it is already hunting one. */
    inner class Hunt : Behavior("starhunt", "Hunting a starfish") {
        private var target: Organism? = null
        private var nextLook = 0.0
        override val targetVerb: String? get() = "stalking"
        override fun score(c: Ctx): Double {
            if (meal != null || c.o.molting || c.t < c.o.satietyUntil || c.o.hunger < HUNT_HUNGER) return 0.0
            val t = target
            if (t != null && t.alive && t.heldBy == null) return 0.55 + 0.3 * c.o.hunger
            if (c.t < nextLook) return 0.0
            nextLook = c.t + 120.0
            target = stars(c).minByOrNull { it.pos.distanceTo(c.o.pos) }
            return if (target != null) 0.55 + 0.3 * c.o.hunger else 0.0
        }
        override fun enter(c: Ctx) { nav.reset() }
        override fun update(c: Ctx, out: SteerIntent) {
            val t = target
            if (t == null || !t.alive || t.heldBy != null) { target = null; finished = true; return }
            val reach = 0.5 * c.L + 0.6
            if (c.o.pos.distanceTo(t.pos) > reach) {
                if (nav.drive(c, out, t.pos + t.up * shrimpStance(c.o), t.up, c.o.cruiseSpeed, arrive = reach * 0.8) == ShrimpNav.Status.STALLED && elapsed > 120) finished = true
                return
            }
            // the flip: the star is turned onto its back and held (its tube feet up, it cannot crawl away)
            t.heldBy = c.o
            t.up = t.up * -1.0
            t.pos = t.pos - t.up * 0.3
            meal = t
            starsCaught++
            target = null
            finished = true
        }
    }

    /** Eating the flipped star over a few hours, tube feet first (Prakash & Kumar 2013); the star is gone at the end. */
    inner class Eat : Behavior("stareat", "Eating a starfish") {
        private var dur = 3600.0
        override val committed: Boolean get() = meal != null && threat == null
        override fun score(c: Ctx) = if (meal != null) 0.9 else 0.0
        override fun enter(c: Ctx) { dur = c.rng.range(MEAL_MIN_S, MEAL_MAX_S) }
        override fun update(c: Ctx, out: SteerIntent) {
            val s = meal ?: run { finished = true; return }
            val face = (s.pos - c.o.pos)
            out.crawl = true; out.target = null; out.surfaceOffset = shrimpStance(c.o)
            out.faceDir = face.rejectFrom(c.o.up).normalized(c.o.heading)
            c.o.mouth = 0.5 + 0.4 * sin(2 * PI * 1.5 * c.t)
            c.o.legRock = 0.3
            if (elapsed > dur) {
                meal = null
                s.heldBy = null
                starsEaten++
                c.sim.eaten(c.o, s)
                // the mate gets its share (pairs feed together)
                mateBrain?.let { m -> if (m.o.pos.distanceTo(c.o.pos) < 6.0) { m.o.hunger = max(0.0, m.o.hunger - 0.5); m.o.satietyUntil = c.sim.simTimeSec + 2 * 86400; m.mealShares++ } }
                finished = true
            }
        }
        override fun exit(c: Ctx) { c.o.legRock = 0.0 }
    }

    /** Its mate has a star: it walks over and eats beside it. */
    inner class Share : Behavior("starshare", "Sharing a starfish with its mate") {
        override fun score(c: Ctx): Double {
            val m = mateBrain ?: return 0.0
            return if (m.meal != null && meal == null && !c.o.molting && c.o.hunger > 0.2) 0.88 else 0.0
        }
        override fun enter(c: Ctx) { nav.reset() }
        override fun update(c: Ctx, out: SteerIntent) {
            val s = mateBrain?.meal ?: run { finished = true; return }
            val side = (c.o.pos - s.pos).rejectFrom(s.up).normalized(Vec3.X)
            val spot = s.pos + side * (0.5 * c.L + 0.8)
            if (c.o.pos.distanceTo(spot) > 1.0) { nav.drive(c, out, spot, null, c.o.cruiseSpeed, arrive = 0.8); return }
            out.crawl = true; out.target = null; out.surfaceOffset = shrimpStance(c.o)
            out.faceDir = (s.pos - c.o.pos).rejectFrom(c.o.up).normalized(c.o.heading)
            c.o.mouth = 0.5 + 0.4 * sin(2 * PI * 1.5 * c.t + 1.0)
        }
    }

    /** Stars it can find: asterina and other sea stars (not brittle stars), not already held. */
    private fun stars(c: Ctx): List<Organism> = c.sim.organisms.filter {
        it.alive && it.brainKind == BrainKind.ASTERINA && it.heldBy == null && it.pos.distanceTo(c.o.pos) < SEARCH_CM
    }

    companion object {
        /** Hunger at which it goes hunting [E]; the 3-day satiety after a meal (Simulation.eaten) sets the meal gap. */
        @Ev(Evidence.DESIGN_ESTIMATE, "44 §2 care guide: one small asterina per shrimp every 5-7 days") const val HUNT_HUNGER = 0.4
        /** How far it searches, cm (they find stars by smell across a tank; est.). */
        const val SEARCH_CM = 90.0
        /** Eating one small asterina, s (Prakash & Kumar 2013 time the lifting phase at 30-60 min; whole meal est.). */
        @Ev(Evidence.DESIGN_ESTIMATE, "44 §2 JoTT-HP") const val MEAL_MIN_S = 5400.0
        const val MEAL_MAX_S = 10800.0
        /** Claw wave rate, Hz ("claws move almost constantly", WP; rate est.). */
        const val CLAW_HZ = 0.5

        /** Abstract mode (no behaviour layer): a hungry harlequin eats a star in the tank. */
        fun abstractFeed(o: Organism, sim: com.reeflive.core.sim.Simulation) {
            if (o.hunger < HUNT_HUNGER || sim.simTimeSec < o.satietyUntil) return
            val s = sim.organisms.firstOrNull { it.alive && it.brainKind == BrainKind.ASTERINA } ?: return
            sim.eaten(o, s)
        }
    }
}

// =====================================================================================
// Tiger pistol shrimp (Alpheus bellulus, research 44 §4): nearly blind, it lives in a sand burrow it digs and keeps
// open. Paired with a watchman goby it takes the goby's burrow: by day it bulldozes loads of sand out of the mouth with
// its claws while the goby keeps watch, one antenna always on the goby, and dives in the moment the goby flicks its
// tail. Alone it digs its own burrow at the foot of a rock and only comes out at night. The big right claw snaps shut
// with a crack (a cavitation bubble, 218 dB at the source, WP/Versluis 2000): at a fish crowding the burrow, at food.
// =====================================================================================
class PistolShrimpBrain(c: Ctx) : SpeciesBrain(c) {
    val nav = ShrimpNav(o)
    /** Its goby partner (a watchman), if it has one. */
    var goby: Organism? = null; private set
    private var ownBurrow: Pair<Vec3, Vec3>? = null
    var loads = 0; private set
    var snapsMade = 0; private set
    var ducks = 0; private set
    /** Seconds spent out of the burrow with an antenna on the goby (tests). */
    var contactS = 0.0; private set
    var outS = 0.0; private set
    private var lastAlarmSeen = -1e9
    private var nextSnap = 0.0

    init {
        install(InBurrow(), Duck(), Bulldoze(), Snap(), BurrowForage(), MoltHide(this) { inside(it) })
    }

    private val gobyBrain: WatchmanGobyBrain? get() = goby?.takeIf { it.alive }?.let { c.sim.agentOf(it)?.brain as? WatchmanGobyBrain }

    /** Finds a watchman goby without a shrimp and moves in with it. */
    private fun pairUp(c: Ctx) {
        if (gobyBrain != null) return
        val taken = c.sim.organisms.filter { it !== o && it.alive && it.brainKind == BrainKind.PISTOL_SHRIMP }
            .mapNotNull { (c.sim.agentOf(it)?.brain as? PistolShrimpBrain)?.goby }
        goby = c.sim.organisms.filter { it.alive && it.brainKind == BrainKind.WATCHMAN_GOBY && it !in taken }
            .minByOrNull { it.pos.distanceTo(o.pos) }
        gobyBrain?.partnerShrimp = o
    }

    /** Burrow point (in the sand under the rock edge) and the direction into it. */
    private fun burrow(c: Ctx): Pair<Vec3, Vec3> {
        gobyBrain?.let { return it.burrow to it.inDir }
        ownBurrow?.let { return it }
        val r = c.rock
        var best: Vec3? = null; var bs = -1e9; var dir = Vec3(0.0, 0.0, -1.0)
        for (i in 0 until 60) {
            val p = Vec3(c.rng.range(10.0, r.lengthCm - 10.0), r.sandTopCm + 1.0, c.rng.range(6.0, r.widthCm - 4.0))
            val d = r.sdf.dist(p)
            if (d < 0.5 || d > 3.0) continue
            val s = r.sdf.cover(p) - 0.3 * d + 0.02 * p.z
            if (s > bs) { bs = s; best = p; dir = horizontalHeading(r.sdf.gradient(p) * -1.0, Vec3(0.0, 0.0, -1.0)) }
        }
        val b = (best ?: Vec3(r.lengthCm * 0.6, r.sandTopCm + 1.0, r.widthCm * 0.7)) to dir
        ownBurrow = b
        return b
    }

    private fun bedY(x: Double, z: Double) = max(c.sim.sand.surfaceY(x, z), c.rock.sandTopCm - 0.2)

    /** On the sand [k] cm out from the burrow mouth, legs on the bed. */
    fun mouth(c: Ctx, k: Double): Vec3 {
        val (b, d) = burrow(c)
        val p = b - d * k
        val q = Vec3(p.x, bedY(p.x, p.z) + shrimpStance(o), p.z)
        return if (c.rock.sdf.dist(q) < shrimpStance(o)) c.rock.sdf.project(q, shrimpStance(o)) else q
    }

    /** Down the burrow (out of sight). */
    fun inside(c: Ctx): Rest {
        val (b, d) = burrow(c)
        return Rest(Vec3(b.x, bedY(b.x, b.z) - 0.5, b.z) + d * 0.8, d, Vec3.Y, 3)
    }

    override fun restSpot(c: Ctx, night: Boolean) = inside(c)
    override fun anchorPos(c: Ctx, anchor: String): Vec3? = if (anchor == "burrow" || anchor == "hole") burrow(c).first else super.anchorPos(c, anchor)
    override fun shelterSpot(c: Ctx): Vec3? = mouth(c, 0.3)
    override fun initialPlace(c: Ctx) {
        pairUp(c)
        o.homePos = mouth(c, 0.5)
        super.initialPlace(c)
    }

    override fun preTick(c: Ctx) {
        super.preTick(c)
        if (c.rng.chance(c.dt / 30.0)) pairUp(c)
        o.clawDisplay = max(0.0, o.clawDisplay - c.dt * 4)
        val g = gobyBrain
        // the goby's tail flick reaches it through the antenna it keeps on the goby (research 44 §4, 50 §3 fact 95-96)
        if (g != null && g.alarmAt > lastAlarmSeen) {
            lastAlarmSeen = g.alarmAt
            if (o.visible && o.pos.distanceTo(g.o.pos) < ANTENNA_CM) { startleAt = c.t; startleMag = 0.4; startleFrom = g.o.pos }
        }
        if (o.visible && o.posture != 3) {
            outS += c.dt
            if (g != null && o.pos.distanceTo(g.o.pos) < ANTENNA_CM) contactS += c.dt
        }
    }

    /** Who it may go out with: by day with its goby on watch; alone only at night. */
    private fun guarded(c: Ctx): Boolean {
        val g = gobyBrain ?: return c.p.night
        return !c.p.night && g.o.visible && g.o.posture != 3 && g.o.pos.distanceTo(burrow(c).first) < 3 * g.o.lengthCm
    }

    private fun hideIn(c: Ctx, out: SteerIntent, rate: Double) {
        val r = inside(c)
        val m = mouth(c, 0.3)
        if (o.posture != 3 && o.pos.distanceTo(m) > 1.0) { out.crawl = true; out.target = m; out.surfaceOffset = shrimpStance(o); out.speed = o.cruiseSpeed * 2; return }
        out.place(r.pos, r.heading, Vec3.Y, rate)
        out.posture = 3
        o.visible = false
    }

    /** Down the burrow, out of sight. */
    inner class InBurrow : Behavior("burrow", "In its burrow") {
        override fun score(c: Ctx) = 0.4
        override fun update(c: Ctx, out: SteerIntent) { hideIn(c, out, 3.0) }
        override fun exit(c: Ctx) { c.o.visible = true }
    }

    /** The goby flicked its tail (or something rushed it): straight back down, and it stays down a while. */
    inner class Duck : Behavior("duck", "Ducking into its burrow") {
        private var until = 0.0
        override val committed: Boolean get() = !finished
        override fun score(c: Ctx): Double {
            if (threat?.alive == true || startledRecently && startleMag > 0.05) return 0.97
            return if (c.t < until) 0.9 else 0.0
        }
        override fun enter(c: Ctx) { ducks++; startleAt = -1e9; until = c.t + c.rng.range(DUCK_MIN_S, DUCK_MAX_S) }
        override fun update(c: Ctx, out: SteerIntent) {
            hideIn(c, out, 20.0)
            if (c.t > until && threat == null) finished = true
        }
        override fun exit(c: Ctx) { c.o.visible = true }
    }

    /**
     * Bulldozing: out of the mouth head first, claws held together in front pushing a load of sand, a few body lengths
     * onto the apron, the load dumped, and backwards down the hole again (research 44 §4: it "continually removes
     * sediment"; its openings keep moving). With its goby on watch this goes on through the day.
     */
    inner class Bulldoze : Behavior("bulldoze", "Bulldozing sand out of its burrow") {
        private var phase = 0
        private var dumpAt = 2.0
        private var next = -1.0
        override fun score(c: Ctx): Double {
            if (c.o.molting || !guarded(c)) return 0.0
            if (next < 0) next = c.t + c.rng.range(10.0, 60.0)
            return if (c.t > next) 0.55 else 0.0
        }
        override fun enter(c: Ctx) { phase = 0; dumpAt = c.rng.range(1.5, 3.0) * c.L * 0.5 + 1.0 }
        override fun update(c: Ctx, out: SteerIntent) {
            val (b, d) = burrow(c)
            out.crawl = true; out.surfaceOffset = shrimpStance(c.o); out.allowGlass = false
            // the slender second legs sort through the load for food as it goes (research 44 §4) [M]
            c.o.mouth = max(c.o.mouth, 0.45 + 0.3 * sin(2 * PI * 1.6 * c.t))
            when (phase) {
                0 -> {   // shoving the load out
                    val to = mouth(c, dumpAt)
                    out.target = to; out.speed = c.o.cruiseSpeed; out.arriveRadius = 0.4
                    out.faceDir = d * -1.0
                    c.o.legRock = 0.5
                    if (c.o.pos.distanceTo(to) < 0.6 || elapsed > 20) {
                        c.sim.sand.dig(b.x, b.z, LOAD_CM3, 0.8)
                        c.sim.sand.deposit(to.x - d.x * 0.6, to.z - d.z * 0.6, LOAD_CM3, 1.2)
                        c.sim.sand.puff(to.x, bedY(to.x, to.z) + 0.2, to.z, 3, 1.0)
                        loads++
                        phase = 1
                    }
                }
                else -> {   // backing in
                    c.o.legRock = 0.0
                    val m = mouth(c, 0.2)
                    out.target = m; out.speed = c.o.cruiseSpeed; out.arriveRadius = 0.4
                    out.faceDir = d * -1.0
                    if (c.o.pos.distanceTo(m) < 0.6 || elapsed > 40) {
                        // goby on watch: the next load soon; alone at night: fewer trips
                        next = c.t + if (gobyBrain != null) c.rng.range(LOAD_GAP_MIN_S, LOAD_GAP_MAX_S) else c.rng.range(120.0, 600.0)
                        finished = true
                    }
                }
            }
        }
        override fun exit(c: Ctx) { c.o.legRock = 0.0 }
    }

    /** The snap: a fish crowding the burrow mouth while it is out gets a crack from the big claw and backs off. */
    inner class Snap : Behavior("clawsnap", "Snapping its claw") {
        private var at: Organism? = null
        override val targetVerb: String? get() = "snapping at"
        override fun score(c: Ctx): Double {
            if (c.t < nextSnap || !c.o.visible || c.o.posture == 3) return 0.0
            at = c.p.neighbors(c.o.pos, SNAP_CM).firstOrNull { it.alive && it.isFish && it !== goby && it.brainKind != BrainKind.WATCHMAN_GOBY && it.pos.distanceTo(c.o.pos) < SNAP_CM }
            return if (at != null) 0.8 else 0.0
        }
        override fun update(c: Ctx, out: SteerIntent) {
            val f = at
            out.crawl = true; out.target = null; out.surfaceOffset = shrimpStance(c.o)
            if (f != null) out.faceDir = (f.pos - c.o.pos).rejectFrom(c.o.up).normalized(c.o.heading)
            if (elapsed > 0.4) {
                c.o.clawDisplay = 1.0
                snapsMade++
                c.sim.emit(SimEvent.Snap(c.o.id, f?.id, c.sim.simTimeSec))
                if (f != null) c.sim.agentOf(f)?.let { a -> a.brain.onAttacked(a.ctx, c.o.pos, 0.5, c.o) }
                nextSnap = c.t + c.rng.range(SNAP_GAP_MIN_S, SNAP_GAP_MAX_S)
                finished = true
            }
        }
    }

    /** Food that lands by the burrow: out, grab, back. It mostly lives on detritus it digs up. */
    inner class BurrowForage : Behavior("scavenge", "Grabbing food by its burrow") {
        private var f: com.reeflive.core.sim.FoodParticle? = null
        override fun score(c: Ctx): Double {
            if (c.o.hunger < 0.2 || c.o.molting) return 0.0
            val b = burrow(c).first
            f = c.findFood(FORAGE_CM + 5, los = false) { it.settled && it.pos.distanceTo(b) < FORAGE_CM }
            return if (f != null) 0.6 + 0.3 * c.o.hunger else 0.0
        }
        override fun update(c: Ctx, out: SteerIntent) {
            val ff = f
            if (ff == null || ff.eaten) { finished = true; return }
            out.crawl = true; out.target = ff.pos; out.surfaceOffset = shrimpStance(c.o); out.speed = c.o.cruiseSpeed * 1.5; out.arriveRadius = 0.5
            if (c.o.pos.distanceTo(ff.pos) < 0.6 + 0.3 * c.L) { c.bite(ff); f = null; finished = true }
            if (elapsed > 30) finished = true
        }
    }

    companion object {
        /** It keeps an antenna on the goby within this far, cm (research 44 §4: constant antennal contact). */
        const val ANTENNA_CM = 6.0
        /** Hidden this long after a duck, s [E]. */
        const val DUCK_MIN_S = 10.0
        const val DUCK_MAX_S = 40.0
        /** One load of sand, cm³, and the gap between loads with the goby on watch, s [E] ("continually removes sediment"). */
        @Ev(Evidence.DESIGN_ESTIMATE, "44 §4 WP-TPS") const val LOAD_CM3 = 0.4
        const val LOAD_GAP_MIN_S = 20.0
        const val LOAD_GAP_MAX_S = 120.0
        /** A fish this close (cm) to it while it is out gets snapped at; gap between snaps, s (no rate found; est.). */
        const val SNAP_CM = 4.0
        const val SNAP_GAP_MIN_S = 60.0
        const val SNAP_GAP_MAX_S = 300.0
        /** Food this close to the burrow (cm) is fetched. */
        const val FORAGE_CM = 10.0
    }
}
