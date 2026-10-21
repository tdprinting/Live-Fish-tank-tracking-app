package com.reeflive.core.ai

import com.reeflive.core.geom.Vec3
import com.reeflive.core.geom.smoothstep
import com.reeflive.core.sim.BrainKind
import com.reeflive.core.sim.CoralKind
import com.reeflive.core.sim.Ev
import com.reeflive.core.sim.Evidence
import com.reeflive.core.sim.FoodParticle
import com.reeflive.core.sim.HoleKind
import com.reeflive.core.sim.Organism
import com.reeflive.core.sim.RockHole
import kotlin.math.max
import kotlin.math.min

/**
 * Royal gramma, *Gramma loreto* (fairy basslet; docs/research/50_popular_fish_batch1.md §1). It claims a cave or an
 * overhang and spends the day hanging just outside it, picking drifting plankton. It keeps its belly toward the nearest
 * rock, so under a ledge it hangs upside down and beside a wall it hangs on its side (Wikipedia: "not to be mistaken
 * for illness"). An intruder near its cave gets a wide-open-mouth threat and a short chase (the pair rows
 * gramma_gramma, gramma_dottyback); at a fright it darts into the cave, where it also sleeps. A male with a female in the
 * tank carries bits of algae into the cave to build a nest (Asoh & Yoshikawa 1996, Wikipedia).
 */
class GrammaBrain(c: Ctx) : SpeciesBrain(c) {
    private var home0: RockHole? = pickCave(c)
    /** Its cave now (rides with its rock when the rocks shift). */
    val home: RockHole? get() = c.rock.holes.live(home0)
    private val shelterAt: Vec3 = c.rock.sdf.project(home0?.mouth ?: defaultNear(c), 0.5)
    val cave: Vec3 get() = home?.mouth ?: shelterAt
    private val caveOut: Vec3 get() = home?.axis ?: c.rock.sdf.gradient(shelterAt).normalized(Vec3.Z)
    private var nextCaveCheck = 0.0

    /** Gapes shown and nest loads carried (tests, captions). */
    var gapes = 0; private set
    var nestLoads = 0; private set

    init {
        o.spline.glassGuard = true
        install(CaveHover(), GrammaFeed(), Gape(), NestBuild(),
            Flee(this, { _, _ -> caveMouth(c) }, "Darting into its cave"),
            InHole(this, home, cave, "Resting in its cave", { c ->
                val o = c.o
                val sick = if (o.health < 0.6 || o.disease != null && o.diseaseStage > 0.3) 1.0 else 0.0
                (0.75 * (1 - c.activity) + 0.25 * o.stress + 0.3 * sick).coerceAtMost(0.9)
            }, { c -> if (c.p.night) -0.8 else 0.3 }),
            VisitCleaner(this), SurfaceGasp(this), Flash(this))
    }

    override fun restSpot(c: Ctx, night: Boolean): Rest =
        home?.let { holeRest(c, it, if (night) -0.8 else 0.3) } ?: Rest(caveMouth(c), caveOut.horizontal().normalized(Vec3.Z))

    override fun anchorPos(c: Ctx, anchor: String): Vec3? = if (anchor == "cave") cave else super.anchorPos(c, anchor)

    override fun shelterSpot(c: Ctx): Vec3? = caveMouth(c)

    override fun peekRest(c: Ctx): Rest? = home?.let { holeRest(c, it, 1.2) }

    override fun preTick(c: Ctx) {
        super.preTick(c)
        if (o.sexFemale == null) o.sexFemale = if (o.role != null) !o.role.equals("MALE", true) else o.rng.chance(0.5)
        // a male and a female settle into a pair (the cave row leaves a mate alone)
        if (o.mateId == null) mate(c)?.takeIf { it.mateId == null }?.let { m -> o.mateId = m.id; m.mateId = o.id }
        if (c.t >= nextCaveCheck) { nextCaveCheck = c.t + 300; if (home == null) pickCave(c)?.let { home0 = it } }
    }

    /** Just outside the cave mouth, where it hangs and where a fright sends it before it backs in. */
    fun caveMouth(c: Ctx): Vec3 = c.safe(cave + caveOut * (c.clearance + 0.8))

    /** The female gramma it is paired with: one of the opposite sex in the tank (two males fight, research 50 §1). */
    fun mate(c: Ctx): Organism? = c.sim.organisms.firstOrNull {
        it !== o && it.alive && it.brainKind == BrainKind.GRAMMA && it.sexFemale != null && it.sexFemale != o.sexFemale
    }

    /**
     * Belly to the rock (research 50 §1, fact 13): the body's up turns toward the rock's outward normal as the fish comes
     * within [ORIENT_CM] of it, so under an overhang it hangs upside down. Out in the water it swims upright.
     */
    private fun bellyToRock(c: Ctx, out: SteerIntent) {
        val p = c.o.pos
        val d = c.rock.sdf.dist(p)
        // the swim keeps the body's centre [clearance] off the rock, so the band is measured from there
        val w = 1 - smoothstep(c.clearance + ORIENT_NEAR_CM, c.clearance + ORIENT_CM, d)
        if (w <= 0.02) return
        val n = c.rock.sdf.gradient(p).normalized(Vec3.Y)
        out.upOverride = (Vec3.Y * (1 - w) + n * w).rejectFrom(c.o.heading).normalized(Vec3.Y)
    }

    /**
     * The day hang (research 50 §1): within a body length or two of the cave mouth, nearly still on sculling pectorals,
     * belly to the rock, facing out into the water; a snap now and then at a drifting particle. A newcomer, a scared
     * or sick fish keeps closer.
     */
    inner class CaveHover : Behavior("gramhover", "Hanging by its cave") {
        private var p = Vec3.ZERO
        private var next = 0.0
        private var yaw = 0.0
        private var nextSnap = 0.0
        override fun score(c: Ctx) = if (c.p.night) 0.0 else 0.45
        override fun enter(c: Ctx) { next = 0.0 }
        override fun update(c: Ctx, out: SteerIntent) {
            if (c.t > next) {
                next = c.t + c.rng.range(6.0, 16.0)
                val days = (c.t - c.o.arrivalTime) / 86400.0
                var reach = c.L * (0.6 + 1.4 * min(1.0, days / 5.0)) * (0.6 + 0.8 * c.o.boldness)
                if (c.o.timidUntil > c.t || c.o.health < 0.6) reach *= 0.5
                val u = c.rng.unitVector()
                // along the rock face around the mouth more than straight out from it
                val side = Vec3(u.x, u.y * 0.8, u.z).rejectFrom(caveOut)
                yaw = c.rng.signed() * 0.6
                val face = Vec3.rotateAbout(caveOut.horizontal().normalized(Vec3.Z), Vec3.Y, yaw)
                p = c.tailInside(c.safe(caveMouth(c) + side * reach + caveOut * (reach * 0.3 * c.rng.nextDouble())), face)
            }
            out.seek(p, c.o.cruiseSpeed * 0.5, arrive = 1.5)
            out.hover = true; out.ampMul = 0.3; out.pectoral = 1.0; out.tailMul = 0.35
            out.faceDir = Vec3.rotateAbout(caveOut.horizontal().normalized(Vec3.Z), Vec3.Y, yaw)
            bellyToRock(c, out)
            if (c.t > nextSnap) { nextSnap = c.t + c.rng.range(10.0, 35.0); c.o.mouth = 1.0 }
        }
    }

    /** Drifting food near its cave; it seldom swims far for it (research 50 §1, facts 25-26). */
    inner class GrammaFeed : Feed(this@GrammaBrain, 40.0) {
        override fun accept(c: Ctx, f: FoodParticle): Boolean {
            if (f.settled || f.onSurface) return false
            val days = (c.t - c.o.arrivalTime) / 86400.0
            return f.pos.distanceTo(cave) < (if (days < 2 || c.o.timidUntil > c.t) 12.0 else FORAGE_CM)
        }
        override fun scoreMul(c: Ctx, f: FoodParticle): Double = if (f.pos.distanceTo(cave) < 15.0) 1.0 else 0.6
        override fun bonus(c: Ctx, f: FoodParticle) = if (f.kind == com.reeflive.core.sim.FoodKind.MYSIS) 0.1 else 0.0
    }

    /**
     * The open-mouth threat (research 50 §1, fact 19): a fish its size or smaller that comes close to the cave is faced
     * with the mouth gaping wide, then chased a few centimetres if it stays. Its mate is let past. Bigger fish are
     * gaped at but never chased.
     */
    inner class Gape : Behavior("gape", "Gaping at an intruder") {
        private var who: Organism? = null
        private var nextLook = 0.0
        override fun score(c: Ctx): Double {
            if (c.p.night) return 0.0
            if (c.t >= nextLook) {
                nextLook = c.t + 0.5
                val m = mate(c)
                who = c.p.neighbors(cave, GAPE_CM).firstOrNull { n ->
                    n.alive && n !== c.o && n !== m && n.isFish && n.posture != 3 && n.visible && n.lengthCm < 3.0 * c.L &&
                        n.pos.distanceTo(cave) < GAPE_CM
                }
            }
            val w = who ?: return 0.0
            return if (w.brainKind == BrainKind.GRAMMA || w.brainKind == BrainKind.DOTTYBACK) 0.6 else 0.35 + 0.3 * c.o.aggression
        }
        override fun enter(c: Ctx) { gapes++ }
        override fun update(c: Ctx, out: SteerIntent) {
            val w = who ?: run { finished = true; return }
            val to = (w.pos - c.o.pos).normalized(c.o.heading)
            c.o.mouth = 1.0
            if (elapsed < GAPE_S || w.lengthCm > 1.2 * c.L) {
                // hold station at the mouth, side-on and gaping
                out.seek(caveMouth(c), c.o.cruiseSpeed * 0.4, arrive = 1.0)
                out.faceDir = to; out.hover = true; out.pectoral = 1.0
                bellyToRock(c, out)
            } else {
                out.seek(w.pos, c.o.burstSpeed * 0.5, arrive = 0.8, urgency = 0.7)
                if (c.o.pos.distanceTo(w.pos) < 0.6 * c.L + 0.5 * w.lengthCm) {
                    c.sim.agentOf(w)?.let { it.brain.onAttacked(it.ctx, c.o.pos, 0.3, c.o) }
                    w.socialStress += 0.02
                    finished = true
                }
            }
            if (elapsed > GAPE_S + 2.5 || w.pos.distanceTo(cave) > GAPE_CM + 8) finished = true
        }
    }

    /**
     * Nest building (Asoh & Yoshikawa 1996; Wikipedia): a male with a female in the tank fetches bits of algae and
     * debris and packs them into the back of his cave. A trip is a swim to the nearest macroalga (or turf on the rock),
     * a tug, and the swim back into the cave; a few loads a day when well fed [L].
     */
    inner class NestBuild : Behavior("nest", "Carrying algae to its nest") {
        private var nextTrip = c.t + c.rng.range(1800.0, 7200.0)
        private var source = Vec3.ZERO
        private var loaded = false
        private var tugAt = 0.0
        override val committed: Boolean get() = !finished
        override fun score(c: Ctx): Double {
            if (c.p.night || c.t < nextTrip || home == null) return 0.0
            if (c.o.sexFemale != false || c.o.lengthCm < (c.o.species.lifeStages?.maturityCm ?: 4.5)) return 0.0
            if (c.o.hunger > 0.6 || c.o.stress > 0.5 || mate(c) == null) return 0.0
            return 0.5
        }
        override fun enter(c: Ctx) {
            loaded = false; tugAt = 0.0
            val macro = c.sim.corals.filter { it.tissue > 0.2 && it.kind == CoralKind.MACRO }
                .minByOrNull { it.pos.distanceTo(cave) }?.pos?.takeIf { it.distanceTo(cave) < NEST_REACH_CM }
            source = c.safe(macro ?: c.rockSurfacePoint(cave + c.rng.unitVector() * 10.0, 12.0)?.let { it.first + it.second * c.clearance } ?: caveMouth(c))
        }
        override fun exit(c: Ctx) { nextTrip = c.t + c.rng.range(NEST_GAP_MIN_S, NEST_GAP_MAX_S) }
        override fun update(c: Ctx, out: SteerIntent) {
            if (!loaded) {
                out.seek(c.viaOpenWater(source), c.o.cruiseSpeed * 1.2, arrive = 1.0)
                if (c.o.pos.distanceTo(source) < 1.5 + 0.2 * c.L) {
                    if (tugAt == 0.0) tugAt = c.t
                    out.hover = true; c.o.mouth = 0.6
                    if (c.t - tugAt > 1.2) loaded = true
                }
                if (elapsed > 40) finished = true
            } else {
                val h = home ?: run { finished = true; return }
                val inside = h.along(min(h.depth - 0.5, 0.6 * c.L))
                out.seek(c.viaOpenWater(if (c.o.pos.distanceTo(caveMouth(c)) > 2.0) caveMouth(c) else inside), c.o.cruiseSpeed * 1.2, arrive = 0.8)
                if (c.o.pos.distanceTo(inside) < 1.2 || c.o.pos.distanceTo(caveMouth(c)) < 1.0) { nestLoads++; finished = true }
                if (elapsed > 60) finished = true
            }
        }
    }

    companion object {
        /** Within this far (cm) beyond its swim clearance from the rock the belly turns fully to it, fading out by [ORIENT_CM] [L]. */
        @Ev(Evidence.DESIGN_ESTIMATE, "50 §1 fact 13") const val ORIENT_NEAR_CM = 1.5
        const val ORIENT_CM = 5.0
        /** It picks food only this far from its cave (cm) [L]. */
        @Ev(Evidence.DESIGN_ESTIMATE, "50 §1 facts 25-26") const val FORAGE_CM = 35.0
        /** An intruder this close to the cave mouth (cm) gets the gape, held for [GAPE_S] s before a chase [L]. */
        @Ev(Evidence.DESIGN_ESTIMATE, "50 §1 fact 19") const val GAPE_CM = 12.0
        const val GAPE_S = 1.2
        /** Nest trips: a macroalga this close (cm) is used, else turf off the nearby rock; gap between trips (s) [L]. */
        @Ev(Evidence.DESIGN_ESTIMATE, "50 §1 facts 30-31") const val NEST_REACH_CM = 45.0
        const val NEST_GAP_MIN_S = 3600.0
        const val NEST_GAP_MAX_S = 4 * 3600.0
        private val KINDS = setOf(HoleKind.CAVE, HoleKind.CREVICE, HoleKind.HOLE, HoleKind.THROUGH)

        private fun defaultNear(c: Ctx): Vec3 =
            Vec3(c.rock.lengthCm * c.o.rng.range(0.25, 0.75), c.rock.sandTopCm + 14.0, c.rock.widthCm * 0.55)

        /** A free cave or hole that fits its body, mid-height on the rock, away from the dottyback's cave if it can. */
        private fun pickCave(c: Ctx): RockHole? {
            val near = c.rock.anchors["CAVE_GRAMMA"]?.pos ?: defaultNear(c)
            return c.rock.holes.claimHome(c.o, KINDS, near, c.sim.holeClaims)
        }
    }
}
