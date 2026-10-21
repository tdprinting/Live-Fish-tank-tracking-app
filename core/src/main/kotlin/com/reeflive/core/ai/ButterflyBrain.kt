package com.reeflive.core.ai

import com.reeflive.core.geom.Vec3
import com.reeflive.core.sim.BrainKind
import com.reeflive.core.sim.Coral
import com.reeflive.core.sim.CoralKind
import com.reeflive.core.sim.Ev
import com.reeflive.core.sim.Evidence
import com.reeflive.core.sim.FoodParticle
import com.reeflive.core.sim.Organism
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Butterflyfish (*Chaetodon*; the wrought iron butterfly, *C. daedalma*, docs/research/60_rare_fish_batch1.md §2). A
 * deep, thin disc that roves the rock faces all day in mid-water, stopping every few seconds to tilt nose-down and peck
 * at the rock for the small animals living on it (fanworms, hydroids, tunicates, barnacles, tiny snails; Wikipedia).
 * That diet is the catch in a reef tank: it pecks feather duster crowns (each peck snaps the fan shut and now and then
 * bites a radiole off, so a pestered worm sheds its crown) and, when hungry, nips soft polyps. In the wild it travels
 * in pairs and loose schools; two in a tank keep together. At night it settles flank-on under an overhang, fitted to
 * its body like the tangs' crevices ([TangShelters]).
 */
class ButterflyBrain(c: Ctx) : SpeciesBrain(c) {
    /** Crevices fitted to the butterfly's disc: about as long and deep as a sailfin tang, a little thinner [L]. */
    val shelters by lazy { TangShelters(c, 0.47, 0.3, 0.065) }
    private var bed: TangShelters.Pose? = null
    /** Where its crevice is tonight (other crevice sleepers keep out of it). */
    val bedPos: Vec3? get() = bed?.pos
    private var bedLocked = false
    private val badBeds = ArrayList<Vec3>()

    /** Rock pecks, feather duster pecks, crowns bitten off and coral nips so far (tests, captions). */
    var rockPecks = 0; private set
    var dusterPecks = 0; private set
    var crownsBitten = 0; private set
    var coralNips = 0; private set

    init {
        o.spline.glassGuard = true
        val flee = Flee(this, { c, from -> shelters.pick(c.o.pos, from, c.o.lengthCm, ROLL)?.entry ?: c.coverPoint(from) }, "Darting to cover")
        install(Rove(), RockPeck(), DusterPeck(), CoralNip(), ButterflyFeed(), NibbleNori(this, 0.6), flee, ButterflySleep(),
            VisitCleaner(this, 0.1), Flash(this), GlassCurious(this), SurfaceGasp(this))
    }

    /**
     * Without a fitted crevice: level, a little off the rock face near the tangs' sleeping corner, the body along the
     * face (a deep disc held head-up against the rock would put its tail in the sand).
     */
    override fun restSpot(c: Ctx, night: Boolean): Rest {
        bed?.let { return Rest(it.pos, it.heading, it.up) }
        val r = openRest?.takeIf { roomy(c, it.pos) != null } ?: findOpenRest(c).also { openRest = it }
        return r
    }

    /** Where it rests without a crevice tonight (kept until it no longer fits, so it does not wander about at night). */
    private var openRest: Rest? = null

    /**
     * Level, a body length off the rock near the tangs' sleeping corner, its body along the face, in water roomy enough
     * for the disc and away from the other butterfly's spot (a deep disc held head-up against the rock, or two sharing a
     * spot, would push their tails through the sand and each other).
     */
    private fun findOpenRest(c: Ctx): Rest {
        val others = c.sim.organisms.mapNotNull { n -> if (n === o || !n.alive) null else (c.sim.agentOf(n)?.brain as? ButterflyBrain)?.openRest?.pos }
        var best: Rest? = null
        var bestD = Double.MAX_VALUE
        val home = sleepNear(c)
        for (i in 0 until 12) {
            val q = home + Vec3(c.rng.signed() * 25.0, c.rng.range(0.0, 10.0), c.rng.signed() * 10.0)
            val p0 = c.rock.sdf.project(q, 0.0)
            val n = c.rock.sdf.gradient(p0).horizontal().normalized(Vec3.Z)
            val h = Vec3(-n.z, 0.0, n.x)
            val lift = c.rock.sandTopCm + 0.5 * c.o.species.depthRatio * c.L + 2.0
            val p = roomy(c, c.tailInside(c.safe(Vec3(p0.x, max(p0.y, lift), p0.z) + n * (c.clearance + 1.5)), h)) ?: continue
            if (others.any { it.distanceTo(p) < 1.5 * c.L }) continue
            val d = p.distanceTo(home)
            if (d < bestD) { bestD = d; best = Rest(p, h) }
        }
        return best ?: Rest(pickWaypoint(c), c.o.heading.horizontal().normalized(Vec3.X))
    }

    override fun shelterSpot(c: Ctx): Vec3? = bed?.entry

    private fun sleepNear(c: Ctx): Vec3 = c.rock.anchors["SLEEP_TANG"]?.pos ?: c.o.homePos

    /** The other butterfly of its kind it swims with (the first one alive, research 60 §2 fact 14). */
    fun partner(c: Ctx): Organism? = c.sim.organisms.firstOrNull {
        it !== o && it.alive && it.brainKind == BrainKind.BUTTERFLY && it.species.id == o.species.id
    }

    override fun preTick(c: Ctx) {
        super.preTick(c)
        if (o.mateId == null) partner(c)?.takeIf { it.mateId == null }?.let { m -> o.mateId = m.id; m.mateId = o.id }
    }

    /**
     * The day's rove (fact 13): along the rock faces in the middle of the water, a body length or two off the rock,
     * pausing to look. A partner keeps within a few body lengths, swimming just behind and beside the leader.
     */
    inner class Rove : Behavior("rove", "Roving the reef") {
        private var wp = Vec3.ZERO
        private var next = 0.0
        override fun score(c: Ctx) = 0.2 + 0.35 * c.activity
        override fun enter(c: Ctx) { next = 0.0 }
        override fun update(c: Ctx, out: SteerIntent) {
            val o = c.o
            val mate = partner(c)
            val follow = mate != null && mate.id < o.id && mate.pos.distanceTo(o.pos) < PAIR_REACH_CM
            if (follow && mate != null) {
                // the follower: a body length behind and to one side of its partner
                val side = mate.heading.cross(Vec3.Y).normalized(Vec3.Z)
                val p = roomy(c, c.safe(mate.pos - mate.heading * (1.2 * c.L) + side * (0.6 * c.L))) ?: mate.pos
                out.seek(c.viaOpenWater(c.tailInside(p, mate.heading)), max(o.cruiseSpeed, mate.vel.length() * 1.1), arrive = c.L)
                return
            }
            if (c.t > next || o.pos.distanceTo(wp) < c.L) {
                next = c.t + c.rng.range(5.0, 12.0)
                wp = pickWaypoint(c)
            }
            out.seek(c.viaOpenWater(wp), o.cruiseSpeed * 0.8, arrive = c.L)
            out.wander = 0.1
            out.maxPitch = 0.35
        }
    }

    /**
     * A rove waypoint: a rock face within [ROVE_CM], a body length or two out from it, with room round it for the deep
     * disc (the narrow gaps behind the rockwork and the glass corners would pin it). Mid-water when none fits.
     */
    private fun pickWaypoint(c: Ctx): Vec3 {
        for (i in 0 until 6) {
            val near = c.rockSurfacePoint(c.o.pos + c.rng.unitVector() * ROVE_CM, ROVE_CM) ?: continue
            val p = near.first + near.second * (c.clearance + c.rng.range(0.8, 2.0) * c.L)
            roomy(c, c.safe(Vec3(p.x, p.y.coerceIn(c.zoneY(0.2), c.zoneY(0.8)), p.z), 1.5))?.let { return it }
        }
        val w = c.rock.widthCm
        return c.safe(Vec3(c.rng.range(0.25, 0.75) * c.rock.lengthCm, c.zoneY(c.rng.range(0.4, 0.75)), w - c.rng.range(0.25, 0.4) * w), 1.5)
    }

    /** [p] if the butterfly fits there: half a body length clear of rock and most of a body length off the glass. */
    private fun roomy(c: Ctx, p: Vec3): Vec3? {
        val m = 0.75 * c.L
        if (p.x < m || p.x > c.rock.lengthCm - m || p.z < m || p.z > c.rock.widthCm - m) return null
        return p.takeIf { c.rock.sdf.dist(it) >= ROOM_L * c.L }
    }

    /**
     * Pecking the rock (fact 9): a hover a few cm off the rock, the body tilted nose-down, short sharp pecks at the
     * film of tiny animals and algae. Each peck takes a little of its hunger away [L].
     */
    inner class RockPeck : Behavior("rockpeck", "Pecking at the rock") {
        private var spot = Vec3.ZERO
        private var normal = Vec3.Y
        private var nextPeck = 0.0
        private var dur = 10.0
        override fun score(c: Ctx) = if (c.p.night) 0.0 else ((0.3 + 0.45 * c.o.hunger) * c.activity).coerceAtMost(0.7)
        override fun enter(c: Ctx) {
            // a face it fits at: nose to the rock, the tail a body length out still inside the glass
            val sp = c.rockSurfacePoint(c.o.pos, 18.0, minNormalY = -0.1)?.takeIf { roomy(c, it.first + it.second * (c.clearance + c.L)) != null }
                ?: run { finished = true; return }
            spot = sp.first; normal = sp.second
            dur = c.rng.range(4.0, 14.0)
        }
        override fun update(c: Ctx, out: SteerIntent) {
            val stand = c.safe(spot + normal * (0.5 * c.o.collisionLength + 0.8), 0.8)
            out.seek(stand, c.o.cruiseSpeed, arrive = 1.2)
            out.faceDir = normal * -1.0
            out.hover = true; out.pectoral = 1.0
            if (c.o.pos.distanceTo(stand) < 2.0) {
                if (c.t > nextPeck) {
                    nextPeck = c.t + c.rng.range(0.6, 1.6)
                    c.o.mouth = 1.0
                    c.o.hunger = max(0.0, c.o.hunger - PECK_FOOD)
                    rockPecks++
                }
                out.pitchBias = -0.25 + 0.12 * sin(2 * PI * (c.t - nextPeck) / 1.0)
            }
            if (elapsed > dur) finished = true
        }
    }

    /**
     * Fanworms are its natural food (fact 9; research 60 §2 care: "will eat tubeworms"). Every hour or so it goes to an
     * open feather duster and pecks the crown: the fan snaps shut each time, and each peck has [CROWN_BITE_P] of
     * taking the crown (the worm then regrows it over 1-2 months, InvertEcology).
     */
    inner class DusterPeck : Behavior("duster", "Pecking at the feather duster") {
        private var nextAt = Double.NaN
        private var target: Organism? = null
        private var pecks = 0
        private var nextPeck = 0.0
        override val targetVerb: String get() = "pecking at"
        override fun score(c: Ctx): Double {
            if (nextAt.isNaN()) nextAt = c.t + c.rng.range(600.0, 2400.0)
            if (c.p.night || c.t < nextAt || c.activity < 0.5) return 0.0
            target = c.sim.organisms.filter { it.alive && it.brainKind == BrainKind.FEATHER_DUSTER && it.visible && it.extension > 0.5 }
                .minByOrNull { it.pos.distanceTo(c.o.pos) }
            return if (target != null) 0.45 + 0.3 * c.o.hunger else 0.0
        }
        override fun enter(c: Ctx) { pecks = 1 + c.rng.nextInt(3); nextPeck = 0.0; c.o.focusId = target?.id }
        override fun exit(c: Ctx) { nextAt = c.t + c.rng.range(DUSTER_GAP_MIN_S, DUSTER_GAP_MAX_S) }
        override fun update(c: Ctx, out: SteerIntent) {
            val w = target ?: run { finished = true; return }
            if (!w.alive || pecks <= 0 || elapsed > 60) { finished = true; return }
            val crown = w.pos + w.up * (0.6 * w.lengthCm)
            val stand = c.safe(crown + Vec3(c.o.pos.x - crown.x, 0.0, c.o.pos.z - crown.z).normalized(Vec3.Z) * (0.5 * c.o.collisionLength + 1.5), 0.8)
            out.seek(stand, c.o.cruiseSpeed, arrive = 1.2)
            out.faceDir = (crown - stand).normalized(c.o.heading)
            out.hover = true; out.pectoral = 1.0
            if (c.o.pos.distanceTo(stand) < 2.5 && c.t > nextPeck) {
                nextPeck = c.t + c.rng.range(2.0, 5.0)
                pecks--
                c.o.mouth = 1.0
                dusterPecks++
                w.extension = 0.0
                c.sim.agentOf(w)?.let { it.brain.onTap(it.ctx, c.o.pos, 0.6) }
                if (c.sim.invertEcology.crownOf(w) >= 1.0 && c.rng.chance(CROWN_BITE_P)) {
                    c.sim.invertEcology.loseCrown(w)
                    crownsBitten++
                    c.o.hunger = max(0.0, c.o.hunger - 0.15)
                }
            }
        }
    }

    /**
     * Nipping coral polyps (fact 10, [I]): not a coral eater like the obligate corallivores, but a hungry wrought iron
     * picks at soft polyps the way it picks at the rock. Each nip makes the colony pull in briefly; nothing is eaten.
     */
    inner class CoralNip : Behavior("nipcoral", "Nipping at the coral") {
        private var nextAt = Double.NaN
        private var target: Coral? = null
        private var bites = 0
        private var nextBite = 0.0
        override val targetVerb: String get() = "nipping"
        override fun score(c: Ctx): Double {
            if (nextAt.isNaN()) nextAt = c.t + c.rng.range(900.0, 3600.0)
            if (c.p.night || c.t < nextAt || c.o.hunger < NIP_HUNGER) return 0.0
            return if (c.sim.corals.any { nipWeight(it) > 0 && it.tissue > 0.3 }) 0.35 + 0.4 * c.o.hunger else 0.0
        }
        override fun enter(c: Ctx) {
            val cand = c.sim.corals.filter { nipWeight(it) > 0 && it.tissue > 0.3 && it.extension > 0.15 }
            var r = c.rng.nextDouble() * cand.sumOf { nipWeight(it) }
            target = cand.firstOrNull { r -= nipWeight(it); r <= 0 } ?: cand.lastOrNull()
            if (target == null) { finished = true; return }
            bites = 1 + c.rng.nextInt(3); nextBite = 0.0
        }
        override fun exit(c: Ctx) { nextAt = c.t + c.rng.range(1800.0, 5400.0) }
        override fun update(c: Ctx, out: SteerIntent) {
            val k = target
            if (k == null || bites <= 0 || elapsed > 60) { finished = true; return }
            val stand = c.safe(k.pos + k.normal * (0.5 * c.o.collisionLength + 1.5), 0.8)
            out.seek(stand, c.o.cruiseSpeed, arrive = 1.2)
            out.faceDir = (k.pos - stand).normalized(c.o.heading)
            out.hover = true; out.pectoral = 1.0
            if (c.o.pos.distanceTo(stand) < 2.5 && c.t > nextBite) {
                nextBite = c.t + c.rng.range(1.5, 3.5)
                bites--
                c.o.mouth = 1.0
                if (k.kind == CoralKind.GSP) com.reeflive.core.sim.GspPolyps.poke(k, k.pos, c.sim.simTimeSec, 1.0, 4.0)
                else k.disturb = max(k.disturb, NipCorals.DISTURB)
                coralNips++
            }
        }
    }

    /** Meaty food anywhere in the water; it comes for it boldly once settled (fact 12: "vigorous appetite"). */
    inner class ButterflyFeed : Feed(this@ButterflyBrain, 70.0) {
        override fun bonus(c: Ctx, f: FoodParticle) = if (f.kind == com.reeflive.core.sim.FoodKind.MYSIS) 0.08 else 0.0
    }

    /**
     * Night (fact 15): flank-on under an overhang, back to the rock, the same cleared glide in and out as the tangs'
     * crevice ([Dock]). With no crevice that fits it rests against the rock face near the tang's corner.
     */
    inner class ButterflySleep : Sleep(this@ButterflyBrain, nightWeight = 0.8) {
        private val dock = Dock()
        private var waking = false
        private var triedAt = 0.0
        override val committed: Boolean get() = dock.inside
        override fun enter(c: Ctx) { dock.reset(); waking = false; triedAt = 0.0 }
        override fun exit(c: Ctx) { bedLocked = false }
        override fun update(c: Ctx, out: SteerIntent) {
            val o = c.o
            bedLocked = dock.inside
            val bp = if (c.p.night || dock.inside) bedPose(c) else null
            if (bp == null) { super.update(c, out); return }
            if (waking || !c.p.night || (Feed.rousedBy(c, this@ButterflyBrain) && o.hunger > 0.2)) {
                waking = true
                if (dock.leave(c, out, bp)) finished = true
                return
            }
            if (!dock.drive(c, out, bp, o.cruiseSpeed * 0.7, rate = 1.5)) {
                if (!dock.inside && elapsed - triedAt > BED_GIVE_UP_S) {
                    badBeds += bp.pos; if (badBeds.size > 4) badBeds.removeAt(0)
                    bed = null; dock.reset(); triedAt = elapsed
                }
                return
            }
            o.sleeping = true
            o.desat = max(o.desat, 0.25)
            out.place(bp.pos, bp.heading, bp.up, 1.5)
            out.pectoral = 0.2
        }
    }

    private fun bedPose(c: Ctx): TangShelters.Pose? {
        val cur = bed
        if (cur != null && (bedLocked || shelters.clear(cur.pos, cur.heading, cur.up, c.o.lengthCm))) return cur
        val a = sleepNear(c)
        bed = shelters.pick(a, null, c.o.lengthCm, ROLL, prefer = a, tries = 6, snuggest = true, avoid = badBeds + otherBeds(c))
        return bed
    }

    /** Crevices other sleepers (tangs, the other butterfly) hold tonight. */
    private fun otherBeds(c: Ctx): List<Vec3> = c.sim.organisms.mapNotNull { n ->
        if (n === o || !n.alive) null
        else when (val b = c.sim.agentOf(n)?.brain) {
            is ButterflyBrain -> b.bedPos
            is TangBrain -> b.bedPos
            else -> null
        }
    }

    companion object {
        /** Side roll lying in its crevice (rad): nearly upright, wedged flank to the rock [L]. */
        const val ROLL = 0.35
        /** The pair keeps within this distance (cm); beyond it each roves alone until they meet again [L]. */
        @Ev(Evidence.DESIGN_ESTIMATE, "60 §2 fact 14") const val PAIR_REACH_CM = 60.0
        /** Rove waypoints are rock faces within this distance (cm) of where it is [L]. */
        const val ROVE_CM = 30.0
        /** Clear water round a rove waypoint, in body lengths [L]. */
        const val ROOM_L = 0.5
        /** Hunger taken by one rock peck (the rock's tiny animals are small meals) [L]. */
        @Ev(Evidence.DESIGN_ESTIMATE, "60 §2 fact 9") const val PECK_FOOD = 0.002
        /** Odds one peck bites a feather duster's crown off; time between duster visits (s) [L]. */
        @Ev(Evidence.DESIGN_ESTIMATE, "60 §2 facts 9, 11") const val CROWN_BITE_P = 0.04
        const val DUSTER_GAP_MIN_S = 1800.0
        const val DUSTER_GAP_MAX_S = 7200.0
        /** It nips coral only above this hunger [L]. */
        @Ev(Evidence.DESIGN_ESTIMATE, "60 §2 fact 10") const val NIP_HUNGER = 0.45
        /** Seconds before it gives up a crevice it cannot get into. */
        const val BED_GIVE_UP_S = 40.0

        /** Polyps a hungry butterfly picks at (soft polyps more than fleshy LPS) [I]. */
        fun nipWeight(k: Coral): Double = when (k.kind) {
            CoralKind.ZOA -> 1.0
            CoralKind.CLOVE -> 0.8
            CoralKind.GSP -> 0.5
            CoralKind.KENYA -> 0.3
            else -> 0.0
        }
    }
}
