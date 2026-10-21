package com.reeflive.core.ai

import com.reeflive.core.geom.Vec3
import com.reeflive.core.sim.AlgaeGroup
import com.reeflive.core.sim.AlgaeSurface
import com.reeflive.core.sim.CoralKind
import com.reeflive.core.sim.CrabLegs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// =====================================================================================
// Emerald crab (Mithraculus sculptus, research 43 §1): a small, shy majid crab. By day it sits wedged in a crevice
// near home, legs drawn in, now and then scooping at the film just inside; from dusk it walks the rock picking bubble
// algae, hair algae and film with its spoon-tipped claws and takes any meaty scrap it finds, which it prefers to algae
// (Figueiredo et al. 2008: pellets chosen 77 % of the time, mysis 69 %). An underfed one nips soft coral polyps. A tap
// or a fish rushing at it sends it straight back into its crevice.
// =====================================================================================
class EmeraldCrabBrain(c: Ctx) : SpeciesBrain(c) {
    val legs: CrabLegs = o.limbRig as CrabLegs

    /** Claw scoops of algae (tests, captions). */
    var scoops = 0; private set
    /** Polyps nipped while hungry. */
    var coralNips = 0; private set
    var retreats = 0; private set

    private var crevice: Rest? = null
    private var creviceAt = -1e9

    init {
        install(
            Retreat(), Wedge(), EmeraldScavenge(), GrazeAlgae(), NipPolyp(),
            CrawlWander(this, { it.o.homePos }, 30.0, { c -> if (c.p.night) 0.25 + 0.15 * c.o.hunger else 0.03 }, "Walking over the rock"),
            MoltHide(this) { restSpot(it, false) },
        )
    }

    /** Its crevice: the most sheltered rock spot near home (a slit or an overhang), kept a day unless the rock moves. */
    override fun restSpot(c: Ctx, night: Boolean): Rest {
        val cached = crevice
        if (cached != null && c.t - creviceAt < CREVICE_KEEP_S && kotlin.math.abs(c.rock.sdf.dist(cached.pos) - surfaceOffset(c)) < 1.0) return cached
        val around = o.homePos.takeIf { it != Vec3.ZERO } ?: o.pos
        var best: Pair<Vec3, Vec3>? = null
        var bs = -1.0
        for (i in 0 until 8) {
            val sp = c.rockSurfacePoint(around, 8.0, minNormalY = -0.2) ?: continue
            val s = c.rock.sdf.cover(sp.first + sp.second * 1.0)
            if (s > bs) { bs = s; best = sp }
        }
        val r = best?.let { (p, n) -> Rest(p + n * surfaceOffset(c), o.heading.rejectFrom(n).normalized(Vec3.X.rejectFrom(n).normalized(Vec3.Z)), n, 0) }
            ?: Rest(around, o.heading, o.up, 0)
        crevice = r; creviceAt = c.t
        return r
    }

    override fun initialPlace(c: Ctx) {
        val start = c.rockSurfacePoint(Vec3(c.rock.lengthCm * c.rng.range(0.25, 0.75), c.rock.sandTopCm + 8, c.rock.widthCm * 0.45), 20.0, minNormalY = 0.2)
        o.homePos = start?.let { (p, n) -> p + n * surfaceOffset(c) } ?: sandSpot(c, 20.0..100.0, 15.0..30.0, surfaceOffset(c))
        super.initialPlace(c)
    }

    override fun preTick(c: Ctx) {
        super.preTick(c)
        legs.tucked = false
        legs.pick = 0.0
    }

    /** A steady little crab walk, quicker when it heads home in a hurry. */
    private fun walk(c: Ctx, out: SteerIntent, target: Vec3, fast: Boolean = false) {
        out.crawl = true; out.target = target; out.surfaceOffset = surfaceOffset(c); out.arriveRadius = 0.8
        out.allowGlass = true
        out.speed = if (fast) c.o.burstSpeed else min(c.o.cruiseSpeed * 1.5, max(c.o.cruiseSpeed, c.o.pos.distanceTo(target)))
        out.urgency = if (fast) 0.7 else 0.3
    }

    /** Darts back into its crevice from a tap, a touch or a big fish coming at it, and stays in a while. */
    inner class Retreat : Behavior("retreat", "Darting back into its crevice") {
        private var arrived = false
        override val committed: Boolean get() = elapsed < HIDE_MIN_S
        override fun score(c: Ctx): Double {
            if (threat != null || startledRecently && startleMag > 0.1) return 0.97
            val big = nearestOther(c, 5.0) { it.isFish && it.lengthCm > c.o.lengthCm && it.vel.length() > 4.0 }
            return if (big != null) 0.9 else 0.0
        }
        override fun enter(c: Ctx) { retreats++; arrived = false; startleAt = -1e9 }
        override fun update(c: Ctx, out: SteerIntent) {
            val r = restSpot(c, false)
            if (arrived || c.o.pos.distanceTo(r.pos) < 1.2) {
                arrived = true
                out.rest(r, 1.0); legs.tucked = true; c.o.extension = 0.1
                if (elapsed > HIDE_MIN_S && threat == null) finished = true
                return
            }
            walk(c, out, r.pos, fast = true)
        }
    }

    /** By day (and while wary) it sits pressed into the crevice, eyes out, claws scooping the film inside now and then. */
    inner class Wedge : Behavior("hide", "Hiding in a crevice") {
        override fun score(c: Ctx) = 0.65 * (1 - c.activity) + 0.12
        override fun update(c: Ctx, out: SteerIntent) {
            val r = restSpot(c, false)
            if (c.o.pos.distanceTo(r.pos) > 1.5) { walk(c, out, r.pos); if (elapsed > 120) creviceAt = -1e9; return }
            out.rest(r, 1.0)
            legs.tucked = true
            c.o.extension = 0.35 + 0.2 * sin(c.t * 0.25 + c.o.appendagePhase)
            if (sin(c.t * 0.19 + c.o.appendagePhase) > 0.65) { legs.pick = 0.4; c.o.mouth = 0.4 }
        }
    }

    /** Leftover food beats algae: it goes for a settled scrap first whenever one is in range (research 43 §1). */
    inner class EmeraldScavenge : Scavenge(this, 30.0, urgentAbove = 0.4, label = "Grabbing a bit of food") {
        override fun score(c: Ctx) = super.score(c).let { if (it > 0) it + 0.15 else 0.0 }
        override fun update(c: Ctx, out: SteerIntent) { super.update(c, out); legs.pick = 0.8 }
    }

    /**
     * A night bout on the rock: it walks to a shaded spot (where bubble algae grow) and scoops with both claws, one
     * scoop every couple of seconds, for half a minute to a minute and a half. More bubble and hair algae on the rock
     * keeps it at it longer; the algae model counts what it removes ([com.reeflive.core.sim.GrazerType.EMERALD_CRAB]).
     */
    inner class GrazeAlgae : Behavior("graze", "Scooping bubble algae") {
        private var spot: Pair<Vec3, Vec3>? = null
        private var dur = 45.0
        private var next = 0.0
        override fun score(c: Ctx) = (0.25 + 0.3 * c.o.hunger + 0.4 * algaeHere(c)) * (0.25 + 0.75 * c.activity)
        override fun enter(c: Ctx) {
            spot = null
            var bs = -1.0
            for (i in 0 until 5) {
                val sp = c.rockSurfacePoint(c.o.pos, 15.0, minNormalY = -0.1) ?: continue
                val s = c.rock.sdf.cover(sp.first + sp.second * 1.0)
                if (s > bs) { bs = s; spot = sp }
            }
            dur = c.rng.range(30.0, 90.0) * (0.6 + algaeHere(c))
        }
        override fun update(c: Ctx, out: SteerIntent) {
            val s = spot ?: run { finished = true; return }
            if (c.o.pos.distanceTo(s.first) > 1.2 + surfaceOffset(c)) { walk(c, out, s.first + s.second * surfaceOffset(c)); if (elapsed > 60) finished = true; return }
            out.crawl = true; out.surfaceOffset = surfaceOffset(c); out.speed = 0.0
            legs.pick = 1.0
            grazeTick(c, 0.6 + 0.4 * algaeHere(c))
            if (c.t > next) { next = c.t + c.rng.range(1.5, 3.0); scoops++; c.o.mouth = 1.0 }
            if (elapsed > dur) finished = true
        }
    }

    /**
     * An underfed crab at night picks at a soft coral or zoanthid colony beside it (research 43 §1, LiveAquaria: it may
     * eat polyps when not fed): a few nips that make the colony pull in, then it moves on.
     */
    inner class NipPolyp : Behavior("nip", "Nipping at coral polyps") {
        private var target: com.reeflive.core.sim.Coral? = null
        private var bites = 0
        private var nextBite = 0.0
        private var nextLook = 0.0
        override val targetVerb: String? get() = "nipping"
        override fun score(c: Ctx): Double {
            if (!c.p.night || c.o.hunger < NIP_HUNGER || c.t < nextLook) return 0.0
            nextLook = c.t + 120.0
            target = c.sim.corals.filter { weight(it.kind) > 0 && it.tissue > 0.2 && it.pos.distanceTo(c.o.pos) < 15.0 }.minByOrNull { it.pos.distanceTo(c.o.pos) }
            return if (target != null) 0.4 + 0.3 * c.o.hunger else 0.0
        }
        override fun enter(c: Ctx) { bites = 2 + c.rng.nextInt(3); nextBite = 0.0 }
        override fun update(c: Ctx, out: SteerIntent) {
            val k = target ?: run { finished = true; return }
            if (bites <= 0 || elapsed > 60) { finished = true; nextLook = c.t + 1800.0; return }
            if (c.o.pos.distanceTo(k.pos) > 1.5 + 0.5 * c.L) { walk(c, out, k.pos + k.normal * surfaceOffset(c)); return }
            out.crawl = true; out.surfaceOffset = surfaceOffset(c); out.speed = 0.0
            legs.pick = 1.0
            if (c.t > nextBite) {
                nextBite = c.t + c.rng.range(2.0, 4.0)
                bites--; coralNips++
                k.disturb = max(k.disturb, NipCorals.DISTURB)
                k.stress = min(1.0, k.stress + 0.01)
                k.tissue = max(0.0, k.tissue - 0.002)
                c.o.hunger = max(0.0, c.o.hunger - 0.03)
                c.o.mouth = 1.0
            }
        }
    }

    /** How much bubble and hair algae there is on the rock for it (0..1). */
    private fun algaeHere(c: Ctx): Double {
        val a = c.sim.water.algae
        val bubble = max(a.cover(AlgaeGroup.BUBBLE, AlgaeSurface.ROCK_SHADE), a.cover(AlgaeGroup.BUBBLE, AlgaeSurface.ROCK_TOP))
        val hair = a.cover(AlgaeGroup.HAIR, AlgaeSurface.ROCK_TOP)
        return (2.0 * bubble + hair).coerceIn(0.0, 1.0)
    }

    companion object {
        const val CREVICE_KEEP_S = 86400.0
        /** It stays pressed in at least this long after a fright (s). */
        const val HIDE_MIN_S = 45.0
        /** Hunger above which it nips coral polyps (LiveAquaria: "if not fed"). */
        const val NIP_HUNGER = 0.7
        fun weight(k: CoralKind): Double = when (k) {
            CoralKind.ZOA -> 1.0
            CoralKind.CLOVE -> 0.8
            CoralKind.GSP -> 0.6
            CoralKind.KENYA -> 0.4
            else -> 0.0
        }
    }
}
