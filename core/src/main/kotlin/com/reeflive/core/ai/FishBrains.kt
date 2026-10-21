package com.reeflive.core.ai

import com.reeflive.core.geom.Vec3
import com.reeflive.core.geom.smoothstep
import com.reeflive.core.sim.Anchor
import com.reeflive.core.sim.HoleKind
import com.reeflive.core.sim.HoleSet
import com.reeflive.core.sim.RockHole
import com.reeflive.core.sim.BrainKind
import com.reeflive.core.sim.Disease
import com.reeflive.core.sim.FoodKind
import com.reeflive.core.sim.FoodParticle
import com.reeflive.core.sim.Organism
import com.reeflive.core.sim.SimEvent
import com.reeflive.core.sim.Counters
import com.reeflive.core.sim.StressModel
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// =====================================================================================
// Ocellaris clownfish (SIMULATION §4.5 ClownBrain)
// =====================================================================================
class ClownBrain(c: Ctx) : SpeciesBrain(c) {
    /** The owner's clowns host the hairy green mushrooms (Rhodactis) on the low rock under the right shelf (HOST_SHROOM). */
    private val hostName = o.hostAnchor ?: "HOST_SHROOM"
    /** The bubble-tip anemone this clown has moved into (research 40 §7), or null while it keeps its old host. */
    fun anemoneHost(): Organism? = c.sim.anemones.hostOf(o)
    fun host(c: Ctx): Vec3 = anemoneHost()?.pos ?: c.rock.anchors.pos(hostName, if (flatName) Vec3(81.5, 11.5, 21.0) else Vec3(82.0, 16.0, 21.0))
    private val flatName: Boolean = hostName.uppercase().let { "SHROOM" in it || "MUSHROOM" in it || "RHODACTIS" in it }
    /**
     * A flat host (corallimorph discs on rock) rather than a branching soft coral or an anemone: the pair hugs the
     * discs, belly 1–3 cm over the tentacles (owner observation), instead of weaving up through branches.
     */
    val flatHost: Boolean get() = flatName && anemoneHost() == null
    fun partner(c: Ctx): Organism? = c.sim.organisms.firstOrNull { it !== o && it.alive && it.brainKind == BrainKind.CLOWN }
    fun isFemale(c: Ctx): Boolean = c.sim.repro.clownIsFemale(o)

    init {
        install(HoverHost(), Sortie(), NestleHost(), ClownFeed(), GlassCurious(this), CleanNestSite(this), LayEggs(this), TendEggs(this),
            Flee(this, { c, _ -> c.safe(host(c) + Vec3(0.0, 2.0, 0.0), 1.5) }, "Darting into the host"),
            VisitCleaner(this, 0.1), SurfaceGasp(this), Flash(this), ExploreAnemone(), FeedAnemone(), DefendHost())
    }

    override fun anchorPos(c: Ctx, anchor: String): Vec3? = if (anchor == "host") host(c) else super.anchorPos(c, anchor)

    override fun restSpot(c: Ctx, night: Boolean): Rest {
        val h = host(c)
        if (flatHost) {
            // night: both settle onto the open discs (they stay open after lights-out), female lowest and central
            val up = if (isFemale(c)) FLAT_LO else FLAT_LO + 1.2
            return Rest(c.safe(h + Vec3(if (isFemale(c)) 0.0 else 2.0, if (night) up else up + 1.0, if (night) 0.0 else -1.0), 1.0), Vec3(0.0, 0.0, 1.0))
        }
        val up = if (isFemale(c)) 2.5 else 4.5
        return Rest(c.safe(h + Vec3(0.0, if (night) up else 3.0 + up, if (night) 0.0 else 2.0), 1.2), Vec3(0.0, 0.0, 1.0))
    }

    override fun initialPlace(c: Ctx) {
        super.initialPlace(c)
        o.homePos = host(c)
    }

    /** Stay within 1–3 BL of the host with the waddle and occasional darts into the branches. */
    inner class HoverHost : Behavior("host", "Hosting") {
        private var spot = Vec3.ZERO
        private var next = 0.0
        private var dartUntil = -1.0
        private var dartTo = Vec3.ZERO
        private var nextDart = 0.0
        private var nextWiggle = 0.0
        private var wiggleUntil = -1.0
        private var wiggleAt = Vec3.ZERO
        private var wiggling = false
        override val label: String get() = if (wiggling) "Wiggling in the host" else "Hosting"
        override fun score(c: Ctx) = 0.25 + 0.3 * c.activity
        override fun update(c: Ctx, out: SteerIntent) {
            val h = host(c)
            val L = c.L
            val mate = partner(c)
            val female = isFemale(c)
            if (c.t > next || c.o.pos.distanceTo(spot) < 0.6 * L) {
                next = c.t + c.rng.range(3.0, 7.0)
                val ang = c.rng.nextDouble() * 2 * PI
                val r = L * c.rng.range(0.5, 1.6)
                var p = if (flatHost) h + Vec3(cos(ang) * r * 0.55, c.rng.range(FLAT_LO, FLAT_HI), sin(ang) * r * 0.35 - 0.5)
                else h + Vec3(cos(ang) * r, c.rng.range(1.5, 1.0 + 1.1 * L), sin(ang) * r * 0.7)
                if (!female && mate != null) {
                    // male keeps 0.5–2 BL from the female and yields when she comes close
                    val dm = mate.pos.distanceTo(c.o.pos)
                    if (dm > 2 * L || dm < 0.5 * L) p = mate.pos + (c.o.pos - mate.pos).normalized(Vec3.X) * (1.2 * L) + Vec3(0.0, 1.0, 0.0)
                }
                if (flatHost) {
                    // never drift far off the disc cluster or high above it
                    val off = Vec3(p.x - h.x, 0.0, p.z - h.z)
                    val maxR = 1.1 * L
                    val flat = if (off.length() > maxR) off * (maxR / off.length()) else off
                    p = Vec3(h.x + flat.x, (p.y).coerceIn(h.y + FLAT_LO, h.y + FLAT_HI + 1.5), h.z + flat.z)
                }
                spot = c.safe(p, 1.2)
            }
            if (c.t > nextDart) {
                nextDart = c.t + c.rng.range(6.0, 15.0)
                dartUntil = c.t + 0.9
                // one dart, one spot (a fresh random spot every tick made the clown jitter on the spot for 0.9 s)
                val d = if (flatHost) Vec3(c.rng.range(-3.0, 3.0), FLAT_LO, c.rng.range(-2.0, 1.5)) else c.rng.unitVector() * 3.0 + Vec3(0.0, 1.5, 0.0)
                dartTo = c.safe(h + d, 1.0)
            }
            if (c.t > nextWiggle) {
                // hosting bout: the clown drops onto the host and shimmies its body against it (the "host-wiggle"
                // tell, research video fish_ocellaris_clownfish [L]); every 20–60 s, 2–4 s long (DESIGN ESTIMATE)
                nextWiggle = c.t + c.rng.range(20.0, 60.0)
                wiggleUntil = c.t + c.rng.range(2.0, 4.0)
                wiggleAt = c.safe(h + Vec3(c.rng.range(-2.0, 2.0), if (flatHost) FLAT_LO else 2.0, c.rng.range(-1.5, 1.0)), 1.0)
            }
            // back from a cleaner visit or a chase across the tank: home over the rock, not into it (also from closer
            // than 4 BL when rock stands between it and the host: a straight seek pinned it behind the rock for minutes)
            val away = c.o.pos.distanceTo(h) > 4 * L || c.o.pos.distanceTo(h) > 1.5 * L && !c.rock.sdf.segmentClear(c.o.pos, spot, 0.5)
            if (away) {
                out.seek(c.viaOpenWater(spot), c.o.cruiseSpeed, arrive = L)
                out.waddleAmp = 0.3; out.waddleHz = 1.3
                return
            }
            wiggling = c.t < wiggleUntil
            if (wiggling) {
                out.seek(wiggleAt, c.o.cruiseSpeed * 0.5, arrive = 1.0)
                out.waddleAmp = 0.8; out.waddleHz = WIGGLE_HZ
                out.rollExtra = 0.28 * sin(2 * PI * WIGGLE_HZ * c.t)
                out.ampMul = 1.8; out.tailMul = 1.6; out.pectoral = 1.0
                out.pitchBias = if (flatHost) -0.2 else -0.1
                out.hover = true; out.separation = 0.2
                return
            }
            if (c.t < dartUntil) {
                out.seek(dartTo, c.o.burstSpeed * 0.5, arrive = 1.0, urgency = 0.5)
            } else {
                out.seek(spot, c.o.cruiseSpeed * 0.8, arrive = if (flatHost) 0.5 * L else L)
            }
            out.waddleAmp = 0.3; out.waddleHz = 1.3
            out.pitchBias = -0.14 * sin(2 * PI * 0.7 * c.t)
            out.hover = true
            out.separation = 0.4
        }
    }

    /**
     * Short trips up off the host to pick zooplankton from the passing water (research 30 §1: wild ocellaris feed on
     * plankton in the water column just above the host and stay within a few body lengths of it). Most frequent in the
     * first hours after lights-on, fewest at midday ([diel]). The male tags along a body length behind the female,
     * a little lower; he goes up on his own less often.
     */
    inner class Sortie : Behavior("sortie", "Picking plankton above the host") {
        private var restUntil = -1.0
        private var spot = Vec3.ZERO
        private var nextSpot = 0.0
        private var nextSnap = 0.0
        private var until = 0.0
        private var follow = false
        private var followAfter = -1.0
        override fun score(c: Ctx): Double {
            if (c.p.night || c.o.mouthbrooding) return 0.0
            if (utility.current === this) return if (finished) 0.0 else 0.8
            val mate = partner(c)
            follow = !isFemale(c) && mate != null && c.t > followAfter && c.sim.agentOf(mate)?.brain?.utility?.current?.name == "sortie"
            if (follow) return 0.75
            if (restUntil < 0) restUntil = c.t + c.rng.range(0.0, SORTIE_GAP_S)   // fish start out of step
            return if (c.t < restUntil) 0.0 else 0.75 * c.activity
        }
        override fun enter(c: Ctx) {
            until = c.t + c.rng.range(SORTIE_MIN_S, SORTIE_MAX_S)
            nextSpot = 0.0; nextSnap = c.t + c.rng.range(1.0, 3.0)
        }
        override fun exit(c: Ctx) {
            val gap = c.rng.range(0.5, 1.5) * SORTIE_GAP_S / diel(c)
            restUntil = c.t + (if (isFemale(c)) gap else 1.8 * gap)
            followAfter = c.t + 15.0   // back at the host for a while before tagging along again
        }
        override fun update(c: Ctx, out: SteerIntent) {
            val h = host(c)
            val L = c.L
            val mate = partner(c)
            val top = c.rock.waterLevelCm - 4.0
            if (c.t > nextSpot) {
                nextSpot = c.t + c.rng.range(3.0, 6.0)
                val ang = c.rng.nextDouble() * 2 * PI
                val r = L * c.rng.range(0.2, 1.0)
                val base = h.y + (if (flatHost) FLAT_HI else 1.0)
                spot = c.safe(Vec3(h.x + cos(ang) * r, (base + L * c.rng.range(SORTIE_UP_MIN, SORTIE_UP_MAX)).coerceAtMost(top), h.z + sin(ang) * r * 0.7), 1.2)
            }
            var goal = spot
            if (follow && mate != null) {
                // a length behind and a little under her, never level with her
                val back = (c.o.pos - mate.pos).horizontal().normalized(Vec3.X)
                goal = c.safe(mate.pos + back * L + Vec3(0.0, -0.4 * L, 0.0), 1.2)
                if (c.sim.agentOf(mate)?.brain?.utility?.current?.name != "sortie") finished = true   // she went home
            }
            out.seek(goal, c.o.cruiseSpeed * 0.7, arrive = 0.6 * L)
            out.hover = true; out.pectoral = 1.0
            out.waddleAmp = 0.3; out.waddleHz = 1.3
            val fl = c.sim.flow.velocityFast(c.o.pos).horizontal()
            if (fl.length() > 1.0 && c.o.pos.distanceTo(goal) < L) out.faceDir = (fl * -1.0).normalized(c.o.heading)
            if (c.t > nextSnap) {
                // a snap at a passing particle: mouth open and a quick lunge a few cm toward it
                nextSnap = c.t + c.rng.range(SNAP_MIN_S, SNAP_MAX_S)
                c.o.mouth = 1.0
                c.o.hunger = max(0.0, c.o.hunger - SNAP_YIELD)
                snaps++
                spot = c.safe(spot + c.rng.unitVector() * (0.4 * L), 1.2)
            }
            if (c.t > until || c.o.pos.distanceTo(h) > SORTIE_LEASH_L * L) finished = true
        }
    }

    /** Plankton snaps taken on sorties (counts for tests). */
    var snaps = 0; private set

    /** Night: nestled in the host, female closer to the centre. */
    inner class NestleHost : Behavior("nestle", "Nestled in host") {
        override fun score(c: Ctx) = 0.8 * (1 - c.activity) * Feed.nightRestDamp(c, this@ClownBrain)
        override fun update(c: Ctx, out: SteerIntent) {
            val r = restSpot(c, true)
            val drift = if (flatHost) 0.5 else 0.8
            out.seek(r.pos + Vec3(sin(c.t * 0.3) * drift, 0.0, cos(c.t * 0.23) * drift), c.o.cruiseSpeed * 0.4, arrive = 2.0)
            out.hover = true; out.ampMul = 0.15; out.pectoral = 0.6; out.separation = 0.2
            if (c.o.pos.distanceTo(r.pos) < 4) c.o.sleeping = true
        }
    }

    /** Chase non-partner fish away from the host (female 0.6, male 0.3 aggression). */
    inner class DefendHost : Behavior("defend", "Defending host") {
        private var intruder: Organism? = null
        private var nipped = false
        // installed with the anemone behaviours: only an anemone-hosting pair guards its host (research 40 §7.4); with
        // the mushrooms they never have (owner video), so this stays silent and draws nothing until then
        override val noisy: Boolean get() = false
        override fun score(c: Ctx): Double {
            if (anemoneHost() == null) return 0.0
            if (c.o.aggression < 0.2 || c.p.night) return 0.0
            val h = host(c)
            val mate = partner(c)
            intruder = c.p.neighbors(h, 1.5 * c.L + 4).firstOrNull {
                it !== c.o && it !== mate && it.alive && it.isFish && it.brainKind != BrainKind.EEL && it.brainKind != BrainKind.CLOWN && it.pos.distanceTo(h) < 1.5 * c.L + 2
            }
            return if (intruder != null) (0.4 + 0.4 * c.o.aggression).coerceAtMost(0.7) else 0.0
        }
        override fun enter(c: Ctx) { nipped = false }
        override fun update(c: Ctx, out: SteerIntent) {
            val i = intruder ?: run { finished = true; return }
            if (!nipped) {
                out.seek(i.pos, c.o.burstSpeed * 0.6, arrive = 0.5 * c.L, urgency = 0.75)
                if (c.o.pos.distanceTo(i.pos) < 0.5 * c.L + 0.5 * i.lengthCm + 1) {
                    nipped = true
                    c.o.mouth = 1.0
                    i.socialStress += 0.03
                    if (i.lengthCm < 1.8 * c.L) c.sim.agentOf(i)?.let { it.brain.onAttacked(it.ctx, c.o.pos, 0.4, c.o) }
                }
            } else {
                out.seek(c.safe(host(c) + Vec3(0.0, 3.0, 0.0), 1.2), c.o.cruiseSpeed, arrive = c.L)
                if (elapsed > 3) finished = true
            }
            if (elapsed > 6) finished = true
        }
    }

    /**
     * Getting to know a new bubble-tip anemone (research 40 §7.2): bouts of approach, a quick touch of the tentacles
     * and a dart back up, the touches growing longer and turning into bumping and wallowing as it acclimates. How far
     * along it is lives in AnemoneModel.HostState (time-based, so it also progresses at high speed); this is what it looks like.
     */
    inner class ExploreAnemone : Behavior("anemone", "Checking out the anemone") {
        override val noisy: Boolean get() = false
        private var nextBout = -1.0
        private var boutLen = 60.0
        private var phase = 0
        private var phaseUntil = 0.0
        private var dipTo = Vec3.ZERO
        override val committed: Boolean get() = elapsed < boutLen
        override val label: String get() {
            val a = c.sim.anemones.acclimation(o)
            return when (phase) {
                2 -> if (a < 0.4) "Stung, darting back from the anemone" else "Popping back out of the anemone"
                1, 3 -> if (a < 0.4) "Touching the anemone to get used to it" else "Bumping and wiggling into the anemone"
                else -> "Checking out the anemone"
            }
        }
        override fun score(c: Ctx): Double {
            c.sim.anemones.candidateOf(o) ?: return 0.0
            if (c.p.night) return 0.0
            if (nextBout < 0) nextBout = c.t + c.rng.range(60.0, 600.0)
            return if (c.t >= nextBout) 0.55 else 0.0
        }
        override fun enter(c: Ctx) { boutLen = c.rng.range(40.0, 120.0); phase = 0 }
        override fun exit(c: Ctx) {
            val a = c.sim.anemones.acclimation(o)
            nextBout = c.t + c.rng.range(8.0, 30.0) * 60 * (if (a > 0.7) 0.5 else 1.0)
        }
        override fun update(c: Ctx, out: SteerIntent) {
            val an = c.sim.anemones.candidateOf(o) ?: run { finished = true; return }
            val disc = an.pos
            val acc = c.sim.anemones.acclimation(o)
            val above = c.safe(disc + Vec3(0.0, 0.3 * an.lengthCm + 3.0, 0.0), 1.0)
            when (phase) {
                0 -> {
                    out.seek(c.viaOpenWater(above), c.o.cruiseSpeed, arrive = 1.5)
                    if (c.o.pos.distanceTo(above) < 2.5) {
                        phase = 1; phaseUntil = c.t + 3.0
                        val r = 0.25 * an.lengthCm
                        dipTo = disc + Vec3(c.rng.signed() * r, 1.0 + 0.1 * an.lengthCm, c.rng.signed() * r * 0.7)
                    }
                }
                1 -> {
                    out.seek(dipTo, c.o.cruiseSpeed * 0.8, arrive = 0.6, urgency = 0.4)
                    out.pitchBias = -0.25
                    if (c.o.pos.distanceTo(dipTo) < 1.5 || c.t > phaseUntil) { phase = 3; phaseUntil = c.t + c.rng.range(0.3, 0.8) + 4.0 * acc }
                }
                3 -> {
                    // the touch: a brief brush at first, longer shimmies into the tentacles as the mucus coat builds
                    out.seek(dipTo, c.o.cruiseSpeed * 0.3, arrive = 1.0)
                    out.waddleAmp = 0.4 + 0.5 * acc; out.waddleHz = WIGGLE_HZ
                    out.rollExtra = 0.2 * acc * sin(2 * PI * WIGGLE_HZ * c.t)
                    out.hover = true; out.separation = 0.2
                    if (c.t > phaseUntil) { phase = 2; phaseUntil = c.t + 1.5 }
                }
                else -> {
                    out.seek(above + Vec3(0.0, 3.0, 0.0), c.o.burstSpeed * (if (acc < 0.4) 0.6 else 0.3), arrive = 1.0, urgency = if (acc < 0.4) 0.6 else 0.2)
                    if (c.t > phaseUntil) phase = 0
                }
            }
            out.separation = min(out.separation, 0.3)
            if (elapsed > boutLen) finished = true
        }
    }

    /**
     * An anemone-hosting clown carries a piece of food to its anemone now and then (research 40 §7.3: anemonefish give
     * larger food items to the host; hobby observation): grab it, swim it in, drop it on the tentacles.
     */
    inner class FeedAnemone : Behavior("feedanemone", "Carrying food to its anemone") {
        override val noisy: Boolean get() = false
        private var target: FoodParticle? = null
        private var carrying: FoodParticle? = null
        private var lastFeed = -1
        private var nextOk = 0.0
        override val committed: Boolean get() = carrying != null && elapsed < 25
        override fun score(c: Ctx): Double {
            anemoneHost() ?: return 0.0
            if (carrying != null) return 0.7
            if (c.t < nextOk || c.p.night) return 0.0
            val f = c.food.nearest(c.o.pos, 30.0) { !it.eaten && it.claimedBy == null && it.feedId != lastFeed && it.kind in CARRY } ?: return 0.0
            lastFeed = f.feedId
            // one decision per feeding: about a third of them, once it has had a few bites itself
            if (c.o.hunger > 0.5 || !c.rng.chance(0.35)) { nextOk = c.t + 20; return 0.0 }
            target = f
            return 0.6
        }
        override fun update(c: Ctx, out: SteerIntent) {
            val an = anemoneHost() ?: run { finished = true; return }
            val held = carrying
            if (held == null) {
                val f = target
                if (f == null || f.eaten || f.claimedBy != null) { finished = true; return }
                out.seek(f.pos, c.o.burstSpeed * 0.5, arrive = 0.3 * c.L, urgency = 0.5)
                if (c.o.pos.distanceTo(f.pos) < 0.6 * c.L) { carrying = f; f.claimedBy = c.o.id; c.o.mouth = 1.0 }
                if (elapsed > 12) finished = true
                return
            }
            if (held.eaten) { carrying = null; finished = true; return }
            held.pos = c.o.pos + c.o.heading * (0.5 * c.L); held.vel = Vec3.ZERO
            val drop = c.safe(an.pos + Vec3(0.0, 2.0 + 0.1 * an.lengthCm, 0.0), 1.0)
            out.seek(c.viaOpenWater(drop), c.o.cruiseSpeed, arrive = 1.0)
            if (c.o.pos.distanceTo(drop) < 0.6 * c.L + 1.0 || elapsed > 24) {
                held.claimedBy = null; held.pos = an.pos + Vec3(0.0, 1.5, 0.0)
                carrying = null; nextOk = c.t + 60; finished = true
            }
        }
        override fun exit(c: Ctx) { carrying?.let { it.claimedBy = null }; carrying = null; target = null }
    }

    companion object {
        private val CARRY = setOf(FoodKind.MYSIS, FoodKind.KRILL, FoodKind.PRAWN, FoodKind.PELLET)
        /** Flat host: body-centre height above the disc surface, cm (belly 1–3 cm over the disc tops; owner). */
        const val FLAT_LO = 3.6
        const val FLAT_HI = 4.6
        /** Body-shimmy frequency of a hosting wiggle bout, Hz (DESIGN ESTIMATE from owner video). */
        const val WIGGLE_HZ = 3.0
        /** Plankton sortie: height over the host's top in body lengths, length and mean gap at the dawn peak, s (DESIGN ESTIMATE
         *  inside the 1–3 BL host leash of research 30 §1). */
        const val SORTIE_UP_MIN = 0.8
        const val SORTIE_UP_MAX = 2.0
        const val SORTIE_MIN_S = 8.0
        const val SORTIE_MAX_S = 25.0
        const val SORTIE_GAP_S = 60.0
        /** Farthest from the host on a sortie, body lengths. */
        const val SORTIE_LEASH_L = 2.9
        /** Seconds between snaps at drifting plankton, and the hunger each one meets (sparse tank plankton). */
        const val SNAP_MIN_S = 2.0
        const val SNAP_MAX_S = 5.0
        const val SNAP_YIELD = 0.002

        /**
         * Daytime activity of juvenile clowns (research 30 §1, Nguyen 2019 [M]): a peak in the first hours after lights-on,
         * a dip through midday and a smaller afternoon rise. 1 at the peak; scales how often it leaves the host.
         */
        fun diel(c: Ctx): Double {
            val ph = c.sim.lights.photoperiodHours ?: com.reeflive.core.sim.LightSchedule.DEFAULT_PHOTOPERIOD_H
            val hour = (c.t / 3600.0) % 24.0
            val m = ((hour * 60 - com.reeflive.core.sim.LightSchedule.MIDDAY_MIN) / (ph * 60) + 0.5)   // 0 lights-on .. 1 lights-off
            return when {
                m < 0.2 -> 1.0
                m < 0.35 -> 1.0 - 0.55 * (m - 0.2) / 0.15
                m < 0.7 -> 0.45
                else -> 0.45 + 0.3 * ((m - 0.7) / 0.3).coerceAtMost(1.0)
            }
        }
    }

    /** Rise fast to the surface and snatch; female eats first. */
    inner class ClownFeed : Feed(this@ClownBrain, 90.0) {
        override fun bonus(c: Ctx, f: FoodParticle) = if (f.pos.y > c.rock.waterLevelCm - 8) 0.2 else 0.0
        override fun scoreMul(c: Ctx, f: FoodParticle): Double {
            if (isFemale(c)) return 1.0
            val fem = partner(c) ?: return 1.0
            return if (fem.pos.distanceTo(f.pos) < 2 * fem.lengthCm) 0.7 else 1.0
        }
    }
}

// =====================================================================================
// Blue tang (TangBrain)
// =====================================================================================
class TangBrain(c: Ctx) : SpeciesBrain(c) {
    /** Coral bites so far (C-3; each disturbs the colony briefly). */
    var coralNips = 0
    fun sleepAnchor(c: Ctx): Anchor? = c.rock.anchors["SLEEP_TANG"]
    /** Overhangs in the rockwork it can tuck under (ai/TangShelter.kt). */
    val shelters by lazy { TangShelters(c) }
    /** When and where it was last scared (a hard tap, an attack, a predator in view), for [TangHide]. */
    var scaredAt = -1e9; private set
    var scareFrom: Vec3 = Vec3.ZERO; private set
    /** Its favourite sleeping crevice: the overhang nearest the SLEEP_TANG anchor that fits its body lying on its side. */
    private var bed: TangShelters.Pose? = null
    private var bedFor = -1.0

    init {
        install(Patrol(), ColumnPick(), Graze(this, GRAZE_WEIGHT), NibbleNori(this, 1.2), TangFeed(), NipCorals(this), PlayDead(),
            Flee(this, { c, from -> shelters.pick(c.o.pos, from, c.o.lengthCm, TangHide.HIDE_ROLL)?.entry ?: c.safe(sleepAnchor(c)?.pos ?: Vec3(38.0, 9.1, 5.2)) }, "Darting to cover"),
            TangSleep(), TangHide(this), TangRub(this), TangZoom(this), VisitCleaner(this, 0.1), Flash(this), GlassCurious(this), SurfaceGasp(this))
    }

    override fun preTick(c: Ctx) {
        super.preTick(c)
        threat?.let { if (it.alive) { scaredAt = c.t; scareFrom = it.pos } }
    }

    override fun onTap(c: Ctx, at: Vec3, s: Double) {
        super.onTap(c, at, s)
        if (s > 0.35) { scaredAt = c.t; scareFrom = at }
    }

    override fun onAttacked(c: Ctx, from: Vec3, mag: Double, by: Organism?) {
        super.onAttacked(c, from, mag, by)
        scaredAt = c.t; scareFrom = from
    }

    /** The sleeping crevice pose for the tang's current size (refitted if it has grown). */
    fun bedPose(c: Ctx): TangShelters.Pose? {
        // spots is read first: it rescans the rockwork if the rocks have moved, which bumps the generation
        shelters.spots
        val gen = shelters.generation
        // in it or gliding in or out: keep the pose it is on (a refit mid-glide would steer it through rock) unless
        // the rocks moved and it no longer fits there or has no roof over it any more (its rock was taken out)
        val cur = bed
        if (bedLocked && cur != null && (gen == bedGen || bedStillGood(c, cur))) { bedGen = gen; return cur }
        if (bedFor < 0 || abs(bedFor - c.o.lengthCm) > 0.5 || gen != bedGen) {
            bedFor = c.o.lengthCm; bedGen = gen
            val a = sleepAnchor(c)?.pos ?: Vec3(38.0, 9.1, 5.2)
            // the most walled-in crevice of the overhangs near its usual corner (P. hepatus wedges itself into a
            // crevice or under a ledge to sleep [S3][M]; a spot that is only in a rock's shadow will not do)
            bed = shelters.pick(a, null, c.o.lengthCm, SIDE_REST_ROLL, prefer = a, tries = BED_TRIES, snuggest = true, avoid = badBeds)
        }
        return bed
    }

    private fun bedStillGood(c: Ctx, p: TangShelters.Pose) =
        shelters.clear(p.pos, p.heading, p.up, c.o.lengthCm) && c.rock.sdf.raycast(p.pos, Vec3.Y, shelters.roofMax(c.o.lengthCm)) != null

    /** A bed it could not get into (the way in was blocked): passed over when the bed is chosen again. */
    fun bedBlocked(c: Ctx) {
        bed?.let { badBeds += it.pos; if (badBeds.size > 4) badBeds.removeAt(0) }
        bedFor = -1.0
    }
    private val badBeds = ArrayList<Vec3>()
    /** Set by [TangSleep] while it is inside the crevice's doorway. */
    private var bedLocked = false
    private var bedGen = -1

    override fun restSpot(c: Ctx, night: Boolean): Rest {
        val a = sleepAnchor(c)
        if (night) bedPose(c)?.let { return Rest(it.pos, it.heading) }
        return if (night && a != null) Rest(c.safe(a.pos, 0.6 * c.o.collisionLength + 0.5), a.dir ?: Vec3.X * -1.0)
        else Rest(c.safe(c.inTank(Vec3(60.0, c.zoneY(0.6), 24.0))), Vec3.X)
    }

    inner class TangFeed : Feed(this@TangBrain, 100.0) {
        // drifting food is intercepted in open water first (research 10 §2: zooplankton is the primary natural diet)
        override fun bonus(c: Ctx, f: FoodParticle) = (if (f.pos.y > c.rock.waterLevelCm - 8) 0.2 else 0.0) + (if (!f.settled) 0.1 else 0.0)
    }

    /** Snaps at drifting particles in open water (counts for tests). */
    var columnSnaps = 0; private set

    companion object {
        /** Surface-picking weight (was 1.5): algae are secondary for P. hepatus (research 10 §2; value DESIGN ESTIMATE). */
        @com.reeflive.core.sim.Ev(com.reeflive.core.sim.Evidence.DESIGN_ESTIMATE, "10 §2") const val GRAZE_WEIGHT = 1.0
        /** Hunger met per column snap (DESIGN ESTIMATE: the tank's zooplankton is sparse). */
        const val SNAP_YIELD = 0.002
        /** Night side-rest roll, rad (~70°; observed 30–90°, research video fish_blue_tang [S3][M]). */
        const val SIDE_REST_ROLL = 1.22
        /** Crevices weighed when choosing a bed, and seconds it tries to get into one before picking another (DESIGN ESTIMATE). */
        const val BED_TRIES = 14
        const val BED_GIVE_UP_S = 120.0
        /** Play-dead roll, rad (~85°, "rolled 90 deg onto side", [S1][M]) and passive sink rate, cm/s (DESIGN ESTIMATE). */
        const val PLAY_DEAD_ROLL = 1.48
        const val PLAY_DEAD_SINK = 1.5
        /** Chance a hard startle (tap, attack) makes it play dead (DESIGN ESTIMATE; juveniles far more often). */
        const val PLAY_DEAD_JUVENILE = 0.4
        const val PLAY_DEAD_ADULT = 0.05
        /** Patrol legs along the rock, cm; chance per leg of turning back early, and of a short look-about hover (DESIGN ESTIMATE). */
        const val LEG_MIN_CM = 12.0
        const val LEG_MAX_CM = 30.0
        const val TURN_BACK = 0.12
        const val PAUSE_CHANCE = 0.2
    }

    /**
     * Open-water food interception (research 10 §2): by day it holds station in the current above the rock, head into
     * the flow, and snaps at passing zooplankton every 3–8 s. Separate from picking at surfaces ([Graze]).
     */
    inner class ColumnPick : Behavior("columnpick", "Picking plankton in open water") {
        private var spot = Vec3.ZERO
        private var nextSnap = 0.0
        override fun score(c: Ctx) = if (c.o.hunger < 0.1) 0.0 else (0.35 + 0.4 * c.o.hunger) * c.activity
        override fun enter(c: Ctx) { spot = c.safe(Vec3(c.rng.range(25.0, 95.0), c.zoneY(c.rng.range(0.45, 0.8)), c.rng.range(14.0, 26.0))) }
        override fun update(c: Ctx, out: SteerIntent) {
            val o = c.o
            val fl = c.sim.flow.velocityFast(o.pos)
            out.seek(spot, o.cruiseSpeed * 0.6, arrive = 4.0)
            out.hover = true; out.pectoral = 1.0
            if (fl.length() > 1.0) out.faceDir = (fl * -1.0).normalized(o.heading)
            if (c.t >= nextSnap) {
                nextSnap = c.t + c.rng.range(3.0, 8.0)
                o.mouth = 1.0
                o.hunger = max(0.0, o.hunger - SNAP_YIELD)
                columnSnaps++
            }
            if (elapsed > 90) finished = true
        }
    }

    /**
     * A loose circuit that skirts the rockwork (research 30 §2: juveniles stay near a refuge and pick plankton in the
     * flow above the reef, "1–2 m above the bottom" scaled to the tank): along the rock face at one height, over the
     * top or round the end, and back at another, a body length or two off the rock. Legs vary in height and pace, it
     * eases to a hover now and then to look about, and sometimes turns back early. (It swept wall to wall at one
     * speed before: research 30 §2, measured with FishBehaviorProbe.)
     */
    inner class Patrol : Behavior("patrol", "Patrolling") {
        private var wp = Vec3.ZERO
        private var goRight = true
        private var surfUntil = -1.0
        private var nextSurfCheck = 0.0
        private var pace = 1.0
        private var pauseUntil = -1.0
        private var pauseFace = Vec3.X
        private var band = 0.6
        override fun score(c: Ctx) = 0.15 + 0.4 * c.activity
        override fun enter(c: Ctx) { goRight = c.rng.chance(0.5); band = c.rng.range(0.35, 0.85); pick(c) }
        private fun pick(c: Ctx) {
            val L = c.L
            val o = c.o
            if (L > 15) { wp = c.safe(o.pos + c.rng.unitVector().let { Vec3(it.x, it.y * 0.3, it.z) } * max(0.5 * L, 10.0)); return }
            val lo = 12.0; val hi = c.rock.lengthCm - 12.0
            // at an end, or now and then mid-run, turn back on a new height band
            if (goRight && o.pos.x > hi - 6 || !goRight && o.pos.x < lo + 6 || c.rng.chance(TURN_BACK)) {
                goRight = !goRight
                band = (band + c.rng.range(0.2, 0.4) * (if (band > 0.6) -1 else 1)).coerceIn(0.3, 0.9)
            }
            val x = (o.pos.x + (if (goRight) 1 else -1) * c.rng.range(LEG_MIN_CM, LEG_MAX_CM)).coerceIn(lo, hi)
            val y = c.zoneY((band + c.rng.range(-0.08, 0.08)).coerceIn(0.25, 0.92))
            wp = c.safe(Vec3(x, y, rockFront(c, x, y) + c.rng.range(1.0, 2.0) * L), 1.5)
            pace = c.rng.range(0.7, 1.3)
            if (c.rng.chance(PAUSE_CHANCE)) {
                pauseUntil = c.t + c.rng.range(1.5, 4.0)
                pauseFace = Vec3.rotateAbout(o.heading.horizontal().normalized(Vec3.X), Vec3.Y, c.rng.signed() * 1.2)
            }
        }
        /** The front face of the rock at (x, y), cm along z, or a mid-tank lane where there is no rock at that height. */
        private fun rockFront(c: Ctx, x: Double, y: Double): Double {
            val w = c.rock.widthCm
            var z = w - 3.0
            while (z > 3.0) { if (c.rock.sdf.dist(Vec3(x, y, z)) < 0.5) return min(z, w - 6.0); z -= 2.0 }
            return w * 0.55
        }
        override fun update(c: Ctx, out: SteerIntent) {
            val o = c.o
            if (c.t < pauseUntil) {
                // ease to a hover and look about, then swim on
                out.target = o.pos; out.speed = 0.0
                out.hover = true; out.pectoral = 1.0
                out.faceDir = pauseFace
                return
            }
            if (o.pos.distanceTo(wp) < 1.5 * c.L) pick(c)
            if (c.t > nextSurfCheck) {
                nextSurfCheck = c.t + 2
                val fl = c.sim.flow.velocityFast(o.pos).length()
                if (fl > 8 && c.t > surfUntil + 10 && c.rng.chance(0.4)) surfUntil = c.t + c.rng.range(3.0, 8.0)
            }
            if (c.t < surfUntil) {
                val fl = c.sim.flow.velocityFast(o.pos)
                out.target = o.pos; out.speed = 0.0
                out.hover = true; out.pectoral = 1.0
                out.faceDir = (fl * -1.0).normalized(o.heading)
                out.tailMul = 1.3
                return
            }
            // out of a cave first, over the rock: a straight seek from a hiding pocket at the rock foot held the tang
            // there, nose to the rock, for minutes (it never came back to a loitering sub, SubmarineTest)
            out.seek(c.viaOpenWater(wp), o.cruiseSpeed * 1.1 * pace, arrive = c.L)
            out.wander = 0.15
        }
    }

    /** Juvenile P. hepatus (first life stage of the sheet's `lifeStages`, < 9 cm) feign death far more readily than adults. */
    val juvenile: Boolean get() = com.reeflive.core.sim.LifeStages.isJuvenile(o)

    /** +1 rolls the back toward the right side (model +Z), −1 toward the left: whichever side the rock is on. */
    fun leanSign(c: Ctx, p: Vec3, heading: Vec3): Double {
        val side = (heading cross Vec3.Y).normalized(Vec3.Z)
        return if (-(side dot c.rock.sdf.gradient(p)) >= 0) 1.0 else -1.0
    }

    /** Up vector of a fish facing [h] rolled by [angle] about its long axis. */
    private fun rolledUp(h: Vec3, angle: Double): Vec3 = Vec3.rotateAbout(Vec3.Y.rejectFrom(h).normalized(Vec3.Y), h, angle)

    /**
     * Night (research video fish_blue_tang [S3][M]): wedged in its crevice and lying over on its side, rolled ~70°
     * (observed 30–90°) with the back against the rock, rigid and pale. The roll builds over the first ~20 s.
     */
    inner class TangSleep : Sleep(this@TangBrain) {
        private var lyingFor = 0.0
        private val dock = Dock()
        private var waking = false
        /** Once it has started gliding into its crevice it only comes out the way it went in (no swimming through rock). */
        override val committed: Boolean get() = dock.inside
        private var triedAt = 0.0
        private var lastBed: TangShelters.Pose? = null
        override fun enter(c: Ctx) { dock.reset(); waking = false; lyingFor = 0.0; triedAt = 0.0; lastBed = null }
        override fun exit(c: Ctx) { bedLocked = false }
        override fun update(c: Ctx, out: SteerIntent) {
            val o = c.o
            bedLocked = dock.inside
            val bp = if (c.p.night || dock.inside) bedPose(c) else null
            // the rockwork changed under it and it has a new bed: swim there from where it is
            if (bp !== lastBed) { if (lastBed != null && bp != null) dock.reset(); lastBed = bp }
            if (bp == null) { plainUpdate(c, out); return }
            // morning, or food in the water it wants: glide back out the way it came, then let the day begin
            if (waking || !c.p.night || (Feed.rousedBy(c, this@TangBrain) && o.hunger > 0.2)) {
                waking = true
                if (dock.leave(c, out, bp)) finished = true
                return
            }
            if (!dock.drive(c, out, bp, o.cruiseSpeed * 0.8, rate = 1.5)) {
                lyingFor = 0.0
                // something is in the way (a rock moved, a coral grew, another fish sits there): another crevice
                if (!dock.inside && elapsed - triedAt > BED_GIVE_UP_S) { bedBlocked(c); dock.reset(); triedAt = elapsed }
                return
            }
            // wedged under the overhang lying on its side, back to the rock; the pose was fitted so no part of the
            // body is in the rock, and it glided in along a path cleared for that pose
            o.sleeping = true
            o.desat = max(o.desat, 0.3)
            lyingFor += c.dt
            out.place(bp.pos, bp.heading, bp.up, 1.5)
            out.pectoral = 0.2
        }

        private fun plainUpdate(c: Ctx, out: SteerIntent) {
            super.update(c, out)
            val o = c.o
            if (!o.sleeping) { lyingFor = 0.0; return }
            o.desat = max(o.desat, 0.3)
            val r = restSpot(c, true)
            if (o.pos.distanceTo(r.pos) < 4.0) {
                lyingFor += c.dt
                val h = r.heading.horizontal().normalized(o.heading.horizontal().normalized(Vec3.X))
                val roll = leanSign(c, r.pos, h) * SIDE_REST_ROLL * smoothstep(0.0, 20.0, lyingFor)
                out.place(r.pos, h, rolledUp(h, roll), 1.5)
                out.pectoral = 0.2
            } else {
                lyingFor = 0.0
                out.rollExtra = 0.52
            }
        }
    }

    /**
     * Feigning death (research video fish_blue_tang [S1][M], V1): after a hard startle the tang flips onto its side
     * within ~0.4 s and lies rigid and pale (−20 % saturation) for 3–8 s, settling against the nearest rock or
     * sinking slowly, then rights itself and swims off. Odds per hard startle are a DESIGN ESTIMATE.
     */
    inner class PlayDead : Behavior("playdead", "Playing dead") {
        private var seen = -1e9
        private var armed = false
        private var hold = 5.0
        private var spot = Vec3.ZERO
        private var h = Vec3.X
        private var sign = 1.0
        override val committed: Boolean get() = elapsed < hold
        override fun score(c: Ctx): Double {
            if (startleAt != seen && startledRecently) {
                seen = startleAt
                armed = startleMag > 0.35 && c.rng.chance(if (juvenile) PLAY_DEAD_JUVENILE else PLAY_DEAD_ADULT)
            }
            return if (armed && startledRecently) 0.97 else 0.0
        }
        override fun enter(c: Ctx) {
            armed = false
            startleAt = -1e9
            hold = c.rng.range(3.0, 8.0)
            playDeadCount++
            h = c.o.heading.horizontal().normalized(Vec3.X)
            spot = c.o.pos
            sign = leanSign(c, spot, h)
            c.o.startle = max(c.o.startle, 0.2)
        }
        override fun update(c: Ctx, out: SteerIntent) {
            val o = c.o
            // sink slowly (no swimming) until the body rests on rock or sand
            val clear = 0.25 * o.lengthCm + 0.3
            if (c.rock.sdf.dist(spot) > clear && spot.y > c.rock.sandTopCm + clear) spot -= Vec3.Y * (PLAY_DEAD_SINK * c.dt)
            out.place(spot, h, rolledUp(h, sign * PLAY_DEAD_ROLL * smoothstep(0.0, 0.4, elapsed)), 10.0)
            out.ampMul = 0.0; out.pectoral = 0.0; out.tailMul = 0.3
            o.desat = max(o.desat, 0.2)
            if (elapsed > hold) finished = true
        }
    }

    /** Times it has played dead (tests). */
    var playDeadCount = 0; private set
}

// =====================================================================================
// Pajama cardinal (CardinalBrain) — crepuscular hoverer
// =====================================================================================
class CardinalBrain(c: Ctx) : SpeciesBrain(c) {
    private val spots = listOf(Vec3(60.0, 13.0, 22.0), Vec3(28.0, 15.0, 34.0), Vec3(50.0, 15.0, 36.0)).map { c.inTank(it) }
    private var spot: Vec3 = spots[0]
    /** The shade pocket it hovers beside by day (06 §1.3 SHADE_CARD). */
    val shadePocket: Vec3 get() = spot
    /** Sandbox / tests: move its shade pocket. */
    fun setPocket(p: Vec3) { spot = p; o.homePos = p }

    init {
        install(HoverCover(), NocturnalForage(), CardinalFeed(),
            Flee(this, { c, from -> if (threat?.brainKind == BrainKind.EEL) c.safe(c.o.pos + Vec3(0.0, 15.0, 0.0) + (c.o.pos - from).horizontal().normalized(Vec3.Z) * 8.0) else c.coverPoint(from) }, "Retreating"),
            Sleep(this, "Resting at the cave", 0.35), VisitCleaner(this), SurfaceGasp(this), Flash(this))
    }

    override fun initialPlace(c: Ctx) {
        spot = c.rock.anchors["SHADE_CARD"]?.pos ?: c.rock.anchors["HOVER_CARDINAL"]?.pos ?: spots.minBy { it.distanceTo(o.homePos.takeIf { h -> h != Vec3.ZERO } ?: spots[o.rng.nextInt(spots.size)]) }
        super.initialPlace(c)
    }

    override fun restSpot(c: Ctx, night: Boolean): Rest =
        if (night) Rest(c.safe(c.rock.anchors.pos("CAVE_SMALL", Vec3(56.0, 11.8, 7.8)) + Vec3(0.0, 0.0, 6.0)), Vec3.Z * -1.0)
        else Rest(c.safe(spot), Vec3.Z)

    override fun preTick(c: Ctx) {
        super.preTick(c)
        val o = c.o
        o.hoverRadius = dayRadius(c)
        // clamped fins out of cover under bright light (06 §5.6 #13)
        o.brightExposed = !c.p.night && c.p.light > 0.6 && c.rock.sdf.cover(o.pos) < 0.3
    }

    /**
     * Daytime hover radius (06 §1.3, §5.6 #7, #26): [BASE_RADIUS] around the shade pocket; a newcomer
     * keeps to 0.6× for its first week and relaxes to 1× by week 3 unless the dottyback's cave is within
     * 40 cm of its pocket; ×0.7 for 10 min after being chased; sustained low pH makes it bolder (S:Munday14).
     */
    fun dayRadius(c: Ctx): Double {
        val o = c.o
        val days = (c.t - o.arrivalTime) / 86400.0
        var k = when {
            days < 7 -> 0.6
            days < 21 -> 0.6 + 0.4 * (days - 7) / 14
            else -> 1.0
        }
        val dotty = c.sim.organisms.firstOrNull { it.alive && it.brainKind == BrainKind.DOTTYBACK && it !== o }
        if (dotty != null) {
            val cave = c.sim.agentOf(dotty)?.brain?.anchorPos(c, "cave") ?: dotty.homePos
            if (cave.distanceTo(spot) <= 40.0) k = min(k, 0.6)
        }
        if (o.timidUntil > c.t) k *= 0.7
        k *= 1 + 0.5 * o.stressState.lowPh
        k *= StressModel.homeRadiusMult(o).coerceAtLeast(0.4)
        return BASE_RADIUS * k
    }

    /**
     * Nearly motionless hover beside cover with pectoral sculling, within [dayRadius] of the pocket (research
     * video/fish_pajama_cardinalfish; "groups hover near branching corals", Fishes of Australia [M]; Tee's tank T96 0:09 [H]).
     * The body stays level ([HOVER_PITCH]): depth changes come from the fins, not from pointing the snout. It faces
     * into the current (or out of the rock when the water is still) with a small per-bout yaw, so a group hangs
     * roughly aligned. Station changes are small and slow: a new spot every 8–20 s at a quarter of cruise.
     */
    inner class HoverCover : Behavior("hovercover", "Hovering by cover") {
        private var p = Vec3.ZERO
        private var next = 0.0
        private var yaw = 0.0
        private var bestD = 0.0
        private var progressAt = 0.0
        private var detour: Vec3? = null
        private var nextLook = 0.0
        private var lookUntil = -1.0
        private var lookDir = Vec3.Z
        private var nextSnap = 0.0
        override fun score(c: Ctx) = 0.45
        override fun enter(c: Ctx) { bestD = c.o.pos.distanceTo(spot); progressAt = c.t; detour = null; nextSnap = c.t + c.rng.range(DAY_SNAP_MIN_S, DAY_SNAP_MAX_S) }
        override fun update(c: Ctx, out: SteerIntent) {
            if (c.t > next) {
                next = c.t + c.rng.range(8.0, 20.0)
                val r = c.o.hoverRadius * kotlin.math.sqrt(c.rng.nextDouble())
                val u = c.rng.unitVector()
                p = c.safe(schoolTarget(c, spot + Vec3(u.x, 0.3 * u.y, u.z) * r))
                if (p.distanceTo(spot) > c.o.hoverRadius + 2) p = c.safe(spot)
                yaw = c.rng.signed() * 0.5
            }
            // outside its zone with rock in the way (left on the far side of a ridge after a cleaner visit), the slow
            // straight seek stalls against the rock: take a waypoint with a clear line both ways (over the ridge or
            // around it), then hover on as before
            val d = c.o.pos.distanceTo(spot)
            if (d < bestD - 0.5) { bestD = d; progressAt = c.t }
            if (d <= c.o.hoverRadius + 2) { bestD = d; progressAt = c.t; detour = null }
            if (detour == null && c.t - progressAt > STALL_S) {
                detour = detourTo(c, c.o.pos, spot)
                progressAt = c.t; bestD = d
            }
            detour?.let { w ->
                if (c.o.pos.distanceTo(w) < 2.0 || c.t - progressAt > 3 * STALL_S) detour = null
                else { out.seek(w, c.o.cruiseSpeed * 0.6, arrive = 2.0); out.hover = true; out.maxPitch = HOVER_PITCH; return }
            }
            out.seek(p, c.o.cruiseSpeed * 0.25, arrive = 4.0)
            out.hover = true; out.ampMul = 0.3; out.pectoral = 1.0; out.tailMul = 0.4
            out.maxPitch = HOVER_PITCH
            // small life in the long day rest (research 30 §3): a slow pivot to watch a fish passing close, and now and
            // then a snap at a drifting particle that comes within reach (it feeds by day only on what reaches it)
            if (c.t > nextLook) {
                nextLook = c.t + c.rng.range(4.0, 10.0)
                val o = c.o
                val passer = c.p.neighbors(o.pos, LOOK_CM).firstOrNull { it !== o && it.alive && it.isFish && it.vel.length() > 0.5 * it.lengthCm }
                if (passer != null) { lookUntil = c.t + c.rng.range(1.5, 3.0); lookDir = (passer.pos - o.pos).horizontal().normalized(o.heading) }
            }
            out.faceDir = if (c.t < lookUntil) lookDir else hoverFacing(c, yaw)
            if (c.t > nextSnap) {
                nextSnap = c.t + c.rng.range(DAY_SNAP_MIN_S, DAY_SNAP_MAX_S)
                c.o.mouth = 1.0
            }
            if (c.o.brightExposed) out.ampMul = 0.2
        }
    }

    /** A waypoint from [a] toward [b] with open water on both legs (≥ 1 cm off rock between the ends): above, in front of
     *  or beside the midpoint, nearest first. */
    private fun detourTo(c: Ctx, a: Vec3, b: Vec3): Vec3? {
        val mid = (a + b) * 0.5
        for (k in 1..6) for (dir in DETOUR_DIRS) {
            val w = c.safe(mid + dir * (5.0 * k))
            if (clearLine(c, a, w) && clearLine(c, w, b)) return w
        }
        return null
    }

    private fun clearLine(c: Ctx, a: Vec3, b: Vec3): Boolean {
        for (k in 2..17) if (c.rock.sdf.dist(a.lerp(b, k / 20.0)) < 1.0) return false
        return true
    }

    /** Into the current when there is one, else away from the nearest rock toward the open front; turned by [yaw] rad. */
    fun hoverFacing(c: Ctx, yaw: Double): Vec3 {
        val f = c.sim.flow.velocityFast(c.o.pos).horizontal()
        val base = if (f.length() > 0.5) f * -1.0 else (c.rock.sdf.gradient(c.o.pos).horizontal() + Vec3(0.0, 0.0, 0.6)).normalized(Vec3.Z)
        return Vec3.rotateAbout(base.normalized(Vec3.Z), Vec3.Y, yaw)
    }

    /**
     * Dusk and night (light < [FORAGE_LIGHT]): forage up to 40 cm from the pocket for drifting
     * zooplankton (S:Marnane02 — 64–93 % of intake by dawn), picking particles within 15 cm.
     */
    inner class NocturnalForage : Behavior("dusk", "Night foraging") {
        private var wp = Vec3.ZERO
        private var next = 0.0
        private var chasing: FoodParticle? = null
        private var dropped: FoodParticle? = null
        private var bestD = 0.0
        private var progressAt = 0.0
        override fun score(c: Ctx): Double = if (c.p.light < FORAGE_LIGHT && !c.o.mouthbrooding) 0.55 else 0.0
        override fun update(c: Ctx, out: SteerIntent) {
            val o = c.o
            if (c.t > next || o.pos.distanceTo(wp) < c.L) {
                next = c.t + c.rng.range(4.0, 9.0)
                wp = c.safe(schoolTarget(c, spot + c.rng.unitVector() * (forageRadius(c) * (0.3 + 0.7 * c.rng.nextDouble()))))
            }
            // drifting plankton only: a particle on the sand or wedged against the rock is out of reach of a mid-water
            // picker (the body cannot get its head there), and one it has chased for STUCK_S without closing is dropped
            val f = if (o.hunger > 0.05) c.food.nearest(o.pos, 15.0) {
                (it.kind == FoodKind.PODS || o.species.dietMatch(it.kind) > 0 && o.acceptsPrepared) && openWaterFood(c, it) && it !== dropped
            } else null
            if (f != null) {
                val d = o.pos.distanceTo(f.pos)
                if (f !== chasing) { chasing = f; bestD = d; progressAt = c.t } else if (d < bestD - 0.5) { bestD = d; progressAt = c.t }
                if (c.t - progressAt > Feed.STUCK_S) { dropped = f; chasing = null }
                out.seek(f.pos, o.cruiseSpeed * 1.4, arrive = 0.8)
                if (d < 1.2 + 0.1 * c.L) c.bite(f)
            } else out.seek(wp, o.cruiseSpeed * 0.8, arrive = c.L)
            out.ampMul = 0.6
            // slow, deliberate night cruise: level body, pitching only to snap up a particle
            out.maxPitch = if (f != null) 0.45 else 0.3
        }
    }

    /** By day faster tankmates leave it little (×0.4 unless the food drifts within 10 cm); dusk/night ×1.5 (06 §3.2). */
    inner class CardinalFeed : Feed(this@CardinalBrain, 60.0) {
        override fun bonus(c: Ctx, f: FoodParticle) = if (f.kind == FoodKind.MYSIS || f.kind == FoodKind.PODS) 0.1 else 0.0
        override fun accept(c: Ctx, f: FoodParticle) = !c.o.mouthbrooding && !f.settled && !f.onSurface && openWaterFood(c, f)
        override fun scoreMul(c: Ctx, f: FoodParticle): Double = when {
            c.p.light < FORAGE_LIGHT -> 1.5
            f.pos.distanceTo(c.o.pos) <= 10.0 -> 1.0
            else -> 0.4
        }
    }

    /**
     * How far it ranges from cover follows the light ramp, not a clock label (research 10 §3): half of
     * [FORAGE_RADIUS] when the light first drops below [FORAGE_LIGHT], all of it in darkness.
     */
    /** A particle a hovering mid-water picker can reach: not settled and clear of the rock by its head room (swim resolve keeps 0.45 Lc). */
    fun openWaterFood(c: Ctx, f: FoodParticle): Boolean =
        !f.settled && f.pos.y > c.rock.sandTopCm + 1.0 && c.rock.sdf.dist(f.pos) > 0.45 * c.o.collisionLength - (1.2 + 0.1 * c.L)

    fun forageRadius(c: Ctx): Double = FORAGE_RADIUS * (0.5 + 0.5 * (1 - com.reeflive.core.geom.smoothstep(0.02, FORAGE_LIGHT, c.p.light)))

    companion object {
        const val BASE_RADIUS = 12.0
        /** Largest |heading.y| while hovering (~10°), DESIGN ESTIMATE from Tee's clip (level hover) and photos. */
        const val HOVER_PITCH = 0.17
        /** Seconds outside its zone without closing on the pocket before the hover takes a detour round the rock. */
        const val STALL_S = 8.0
        /** Day rest: range at which it turns to watch a passing fish, cm, and seconds between snaps at drifting particles (DESIGN ESTIMATE). */
        const val LOOK_CM = 18.0
        const val DAY_SNAP_MIN_S = 20.0
        const val DAY_SNAP_MAX_S = 60.0
        private val DETOUR_DIRS = listOf(Vec3.Y, Vec3.Z, Vec3(0.0, 0.7, 0.7).normalized(), Vec3.X, Vec3.X * -1.0, Vec3.Z * -1.0)
        const val FORAGE_RADIUS = 40.0
        const val FORAGE_LIGHT = 0.3
    }
}

// =====================================================================================
// Cleaner wrasse (WrasseBrain)
// =====================================================================================
class WrasseBrain(c: Ctx) : SpeciesBrain(c) {
    val stationPos: Vec3 = c.safe(c.rock.anchors.pos("STATION_CLEANER", Vec3(38.0, 10.0, 14.0)), 0.6 * o.collisionLength + 1.0)
    val station = CleaningStation(o, stationPos, isShrimp = false)

    init {
        c.p.stations += station
        install(RunStation(), Work(), Surf(), Feed(this, 70.0),
            Flee(this, { c, from -> c.coverPoint(from) }, "Fleeing"),
            Cocoon(), SurfaceGasp(this), Flash(this), GlassCurious(this))
    }

    override fun anchorPos(c: Ctx, anchor: String): Vec3? = if (anchor == "station") stationPos else super.anchorPos(c, anchor)

    override fun restSpot(c: Ctx, night: Boolean): Rest {
        if (!night) return Rest(stationPos, Vec3.Z)
        val cave = c.rock.anchors.pos("CAVE_SMALL", Vec3(56.0, 11.8, 7.8))
        return Rest(c.safe(cave, 0.6), Vec3.X, posture = 4)
    }

    /**
     * Hover at the station facing out; advertise with the "dance" when a client is near: the rear body see-saws up
     * and down about ±20° at ~2 Hz with the tail beating wide (research video fish_cleaner_wrasse [S1][S2][M]). An
     * unfamiliar fish arriving within [GREET_CM] gets an immediate 2–4 s greeting dance.
     */
    inner class RunStation : Behavior("station", "Running cleaning station") {
        private var nextDance = 0.0
        private var danceUntil = -1.0
        private var greeted: Organism? = null
        override val label: String get() = if (station.advertising) "Dancing for a client" else "Running cleaning station"
        override fun score(c: Ctx) = 0.2 + 0.2 * c.activity + (if (c.t < stationBias) 0.3 else 0.0)
        override fun update(c: Ctx, out: SteerIntent) {
            val dancing = c.t < danceUntil
            val bob = if (dancing) DANCE_BOB_CM * sin(2 * PI * DANCE_HZ * c.t) else 0.0
            out.seek(stationPos + Vec3(sin(c.t * 0.5) * 1.5, sin(c.t * 0.31) + bob, 0.0), c.o.cruiseSpeed * (if (dancing) 0.9 else 0.5), arrive = if (dancing) 0.8 else 2.0)
            out.hover = true; out.faceDir = Vec3.Z
            val near = c.p.neighbors(stationPos, 35.0).filter { it.isFish && it !== c.o && it.alive && it.brainKind != BrainKind.EEL }
            val newcomer = near.firstOrNull { it.pos.distanceTo(stationPos) < GREET_CM && it !== greeted }
            if (newcomer != null && !dancing) { greeted = newcomer; nextDance = c.t + c.rng.range(10.0, 25.0); danceUntil = c.t + c.rng.range(2.0, 4.0) }
            else if (near.isNotEmpty() && c.t > nextDance) { nextDance = c.t + c.rng.range(10.0, 25.0); danceUntil = c.t + 2 }
            if (greeted != null && greeted!!.pos.distanceTo(stationPos) > 2 * GREET_CM) greeted = null
            station.advertising = c.t < danceUntil
            if (station.advertising) {
                // see-saw: pitch swings with the vertical bob, tail beats wide and fast
                out.pitchBias = 0.35 * cos(2 * PI * DANCE_HZ * c.t); out.ampMul = 1.8; out.tailMul = 1.4
            }
        }
    }

    /**
     * The cleaning loop (research 06 §5.5): choose a client, approach to 1 BL, tactile dance for
     * high-ranked or hungry clients (calms their aggression for 20 min), ask for a pose, inspect for a
     * lognormal 2–20 s, cheat (a mucus bite → client jolts) at 0.04/s, 0.17/s when hungry.
     */
    inner class Work : Behavior("work", "Cleaning a client") {
        private var client: Organism? = null
        private var candidate: Organism? = null
        private var phase = 0
        private var phaseT = 0.0
        private var inspectS = 6.7
        /** Per client, the quantile its last inspection length was drawn at (see [drawInspection]). */
        private val inspectU = HashMap<String, Double>()
        private var danceS = 1.5
        private var nextScan = 0.0
        private var cheatAcc = 0.0
        private val lastPoseAt = HashMap<String, Double>()
        private val unreachableUntil = HashMap<String, Double>()
        override val targetVerb: String get() = if (phase == 3) "inspecting" else "approaching"
        override val committed: Boolean get() = client != null && phase in 1..3

        override fun score(c: Ctx): Double {
            val o = c.o
            if (c.p.night || o.sleeping || c.activity < 0.3 || o.health < 0.35) return 0.0
            // a bout under way keeps its score (the candidate is consumed on entry; without this the
            // approach lost to the station on the very next tick and the wrasse barely ever cleaned)
            client?.let { if (it.alive) return 0.6 + 0.2 * o.hunger }
            if (c.t < restUntil) return 0.0
            if (c.t >= nextScan) { nextScan = c.t + 1.0; candidate = choose(c) }
            val cand = candidate ?: return 0.0
            if (!cand.alive) { candidate = null; return 0.0 }
            return 0.6 + 0.2 * o.hunger
        }

        /** Client weight w = area × (1 + 3·ich + 0.5·posing) × (1 − 0.5·threat), plus due, overdue and buffer terms. */
        fun choose(c: Ctx): Organism? {
            val o = c.o
            var best: Organism? = null
            var bw = 0.0
            // a juvenile (black with the neon dorsal stripe) works close to its station (life_stages.md §4 [L])
            val searchCm = SEARCH_CM * (if (com.reeflive.core.sim.LifeStages.isJuvenile(o)) JUVENILE_SEARCH else 1.0)
            for (f in c.sim.organisms) {
                if (f === o || !f.alive || !f.isFish || !f.visible || f.sleeping || f.brainKind == BrainKind.WRASSE || f.brainKind == BrainKind.EEL) continue
                if (c.t - f.lastCleanerContactAt < CLIENT_COOLDOWN_S) continue
                if ((unreachableUntil[f.id] ?: -1.0) > c.t) continue
                val d = f.pos.distanceTo(o.pos)
                val overdue = c.t - f.lastCleanerContactAt > OVERDUE_S
                val atStation = station.client === f && station.clientReady
                // an active aggressor whose damping has lapsed is sought out across the tank, like an overdue client
                val aggressor = f.aggressionDampUntil < c.t + DAMP_RENEW_S && f.counters.lastHour(Counters.K.CHASE_GIVEN, c.t) > 0
                if (d > searchCm && !overdue && !atStation && !aggressor) continue
                val ich = if (f.disease == Disease.ICH) f.diseaseStage else 0.0
                val threat = o.relations[f.id]?.fear ?: 0.0
                // lateral area (length² × body depth, S:Grutter95; gives 06 §2.11's order tang > clowns > dottyback >
                // lawnmower > hawk > cardinal > blennies) × parasites × a client posing at the station × wariness (06 §5.5)
                val area = clientArea(f)
                var w = area * (1 + 3 * ich + (if (atStation) 0.5 else 0.0)) * (1 - 0.5 * threat)
                // calming the tank: aggressors whose tactile damping has expired are worth a visit (06 §3.5)
                // ... the more so the more it has been chasing lately (06 §3.5: the buffer is aimed at aggressors)
                if (wantsTactile(f, c) && f.aggressionDampUntil < c.t + DAMP_RENEW_S)
                    w *= 1 + min(AGGRESSOR_WEIGHT, f.counters.lastHour(Counters.K.CHASE_GIVEN, c.t).toDouble())
                // clients come due again as time passes; an overdue one outranks everyone (06 §5.6 #4)
                w *= 1 + min(c.t - f.lastCleanerContactAt, OVERDUE_S) / DUE_S
                // ... and the longer it has been neglected the harder it solicits (an unserved client must not starve behind the big ones)
                if (overdue) w *= OVERDUE_WEIGHT * (1 + (c.t - f.lastCleanerContactAt - OVERDUE_S) / DUE_S)
                // infested clients seek the cleaner out and are served first (06 §5.7: ich visits at ×3 weight; S:Grutter99)
                if (ich > 0.05) w *= 3.0
                // a client whose next full pose is due again is worth seeking out: poses then come soon after the
                // minimum spacing rather than whenever the two next happen to meet
                if (c.t - (lastPoseAt[f.id] ?: -1e9) > POSE_MIN_GAP_S) w *= POSE_DUE_WEIGHT
                w /= 1 + d / SEARCH_CM
                if (w > bw) { bw = w; best = f }
            }
            return best
        }

        override fun enter(c: Ctx) {
            client = candidate; candidate = null
            phase = 0; phaseT = 0.0; cheatAcc = 0.0
            // inspection length lognormal (mean 6.7 s, sd 4 s), clamped 2–20 s (D:Grutter96); longer on a
            // larger client, ∝ area^¼ (duration scales with surface area, S:Grutter95; exponent [I])
            danceS = c.rng.range(1.0, 2.0)
            if (client == null) finished = true else encounters.approaches++
        }

        override fun update(c: Ctx, out: SteerIntent) {
            val o = c.o
            val cl = client ?: run { finished = true; return }
            if (!cl.alive || !cl.visible || cl.sleeping) { done(c, cl, false); return }
            o.focusId = cl.id
            phaseT += c.dt
            val toC = (cl.pos - o.pos).normalized(o.heading)
            out.separation = 0.0
            when (phase) {
                // approach to 1 BL at 1.8 BL/s
                0 -> {
                    val goal = c.safe(cl.pos - toC * (0.6 * c.L + 0.5 * cl.lengthCm))
                    out.seek(c.viaOpenWater(goal), 1.8 * c.L, arrive = 1.0, urgency = 0.4)
                    // a client wedged in a crevice is worked from the mouth: arriving at the (rock-clamped) approach point counts
                    if (o.pos.distanceTo(cl.pos) < c.L + 0.5 * cl.lengthCm + 1.5 || (phaseT > 2.0 && o.pos.distanceTo(goal) < 2.0)) {
                        phase = if (wantsTactile(cl, c)) 1 else 2
                        if (phase == 1) encounters.advertised++
                        phaseT = 0.0
                    }
                    // no path-finding: a client it cannot reach (rock in the way) is left alone for a while
                    if (phaseT > APPROACH_GIVE_UP_S) { encounters.failedApproaches++; unreachableUntil[cl.id] = c.t + UNREACHABLE_S; done(c, cl, false) }
                }
                // tactile dance: pitch ±20° at 2 Hz within 15 cm, stroking with the pelvic fins
                1 -> {
                    out.hover = true; out.faceDir = toC
                    out.pitchBias = 0.35 * sin(2 * PI * 2 * c.t); out.ampMul = 1.6
                    out.seek(c.safe(cl.pos + cl.up * (0.3 * cl.lengthCm + 1)), o.cruiseSpeed, arrive = 0.8)
                    if (phaseT >= danceS) {
                        cl.aggressionDampUntil = c.t + PairMatrix.AGG_DAMP_S
                        cl.lastCleanerContactAt = c.t
                        phase = 2; phaseT = 0.0
                    }
                }
                // ask for a pose: p = 0.3 + 0.6·trust (S:Grutter94)
                2 -> {
                    cl.lastCleanerContactAt = c.t
                    val trust = Social.rel(cl, o, c.t).cleanerTrust
                    // a client that posed recently is less keen (few parasites to shed): full poses are spaced out,
                    // the other contacts stay brief passes ("tang 1–3 full poses/h plus several brief passes", 06 §5.5)
                    val sincePose = c.t - (lastPoseAt[cl.id] ?: -1e9)
                    val ichS = if (cl.disease == Disease.ICH) cl.diseaseStage else 0.0
                    // ... and within [POSE_MIN_GAP_S] of its last full pose an uninfested client only takes a brief pass: the
                    // pose count no longer rides on how often the two happen to meet (a busy morning gave the tang 3.2/h)
                    if (sincePose < POSE_MIN_GAP_S && ichS < 0.05) { encounters.refused++; done(c, cl, false); return }
                    val keen = (sincePose / POSE_REFRACT_S).coerceAtMost(1.0)
                    val need = (0.6 + keen + 3 * ichS + cl.parasiteLoad).coerceIn(0.1, 1.0)
                    if (!c.rng.chance((0.3 + 0.6 * trust).coerceIn(0.0, 1.0) * need)) { encounters.refused++; done(c, cl, false); return }
                    encounters.accepted++
                    inspectS = drawInspection(c, cl)
                    lastPoseAt[cl.id] = c.t
                    cl.poseFor = o; cl.poseUntil = c.t + inspectS + 1.0
                    cl.counters.add(Counters.K.POSE, c.t)
                    c.sim.emit(SimEvent.Pose(cl.id, o.id, c.sim.simTimeSec))
                    phase = 3; phaseT = 0.0
                }
                // inspect along a 4-point spine path, nibbling at 1.5 Hz
                else -> {
                    val k = ((phaseT / inspectS) * 4).toInt().coerceAtMost(3) / 4.0
                    val side = (cl.heading cross cl.up).normalized(Vec3.Z) * (if (((phaseT / inspectS) * 8).toInt() % 2 == 0) 1.0 else -1.0)
                    val skin = cl.pos - cl.heading * (cl.lengthCm * (k - 0.3)) + side * (cl.lengthCm * cl.species.depthRatio * 0.35 + 1.0 + 0.3 * c.L)
                    out.seek(skin, o.cruiseSpeed * 1.2, arrive = 1.0)
                    out.faceDir = toC; out.hover = true
                    o.mouth = 0.5 + 0.5 * sin(2 * PI * 1.5 * c.t)
                    cl.poseUntil = max(cl.poseUntil, c.t + 0.5)
                    val ich = cl.disease == Disease.ICH
                    // honest gleaning feeds in proportion to what is there (a tank has few parasites)
                    o.hunger = max(0.0, o.hunger - c.dt * (HONEST_YIELD_PER_S * cl.parasiteLoad.coerceIn(0.0, 1.0) + if (ich) 0.015 * max(0.2, cl.diseaseStage) else 0.0))
                    // cheating: 0.02/s baseline (S:Pinto11: 2–6 jolts per 100 s) → 0.19/s when hungry (13–21 per 100 s, S:Pinto11)
                    cheatAcc += c.dt
                    while (cheatAcc >= 1.0) {
                        cheatAcc -= 1.0
                        if (c.rng.chance(cheatRate(o))) { jolt(c, cl); return }
                    }
                    if (phaseT >= inspectS) {
                        val r = Social.rel(cl, o, c.t)
                        r.cleanerTrust = min(1.0, r.cleanerTrust + 0.05)
                        finishClean(c, cl, isShrimp = false, feed = false)
                        encounters.completed++
                        done(c, cl, true)
                    }
                }
            }
        }

        /**
         * Inspection length, lognormal (mean 6.7 s, sd 4 s), clamped 2–20 s (D:Grutter96); longer on a larger client,
         * ∝ area^¼ (duration scales with surface area, S:Grutter95; exponent [I]). Drawn when the client poses, at the
         * quantile u = u₀ + k·φ (mod 1) of the client's k-th pose with a random u₀: each length is still lognormal,
         * but a client's handful of poses in a few hours averages close to the mean instead of wandering past 10 s
         * on an unlucky run (independent draws did on one seed in five).
         */
        private fun drawInspection(c: Ctx, cl: Organism): Double {
            val mean = 6.7 * sqrt(sqrt(clientArea(cl))).coerceIn(0.8, 1.25)
            val sigma2 = ln(1 + (4.0 / 6.7) * (4.0 / 6.7))
            val mu = ln(mean) - sigma2 / 2
            val u0 = inspectU.getOrPut(cl.id) { c.rng.nextDouble() }
            val u = (u0 + GOLDEN) % 1.0
            inspectU[cl.id] = u
            // a well-fed cleaner gives poorer service (Triki et al. 2025, research 30 §1): shorter inspections when sated
            val sated = SATED_SERVICE + (1 - SATED_SERVICE) * (c.o.hunger / 0.3).coerceIn(0.0, 1.0)
            return (exp(mu + sqrt(sigma2) * normalQuantile(u.coerceIn(1e-6, 1 - 1e-6))) * sated).coerceIn(2.0, 20.0)
        }

        private fun jolt(c: Ctx, cl: Organism) {
            val o = c.o
            val r = Social.rel(cl, o, c.t)
            r.cleanerTrust = max(0.0, r.cleanerTrust - 0.2)
            o.hunger = max(0.0, o.hunger - 0.01)   // the mucus bite is what it wanted
            cl.poseUntil = c.t
            // the client leaves (0.7) or, as a resident, chases the cheater 1–2 BL (0.3; S:Bshary01)
            val chase = c.rng.chance(0.3)
            if (chase) cl.retaliateUntil = c.t + c.rng.range(0.8, 1.6)
            cl.counters.add(Counters.K.JOLT, c.t)
            encounters.jolts++
            StressModel.addSocial(cl, 0.05)
            c.sim.emit(SimEvent.Jolt(o.id, cl.id, chase, c.sim.simTimeSec))
            done(c, cl, false)
        }

        private fun done(c: Ctx, cl: Organism, served: Boolean) {
            if (cl.poseFor === c.o) cl.poseUntil = min(cl.poseUntil, c.t)
            if (station.client === cl) station.release()
            client = null; finished = true
            if (served) c.o.counters.add(Counters.K.CLEAN_GIVEN, c.t)
            // pause between bouts (few clients, scarce parasites), shorter the hungrier it is: a hungry cleaner
            // keeps working and takes mucus where parasites are lacking (06 §2.11); half the time back to the station
            restUntil = c.t + c.rng.range(REST_MIN_S, REST_MAX_S) * (1.3 - c.o.hunger).coerceAtLeast(0.3)
            stationBias = if (c.rng.chance(0.5)) c.t + 60 else -1.0
        }

        override fun exit(c: Ctx) {
            client?.let { if (it.poseFor === c.o) it.poseUntil = min(it.poseUntil, c.t) }
            if (client != null) restUntil = c.t + 10.0
            client = null
        }
    }

    /**
     * Encounter sequence counts (research 10 §4): approach → (advertise) → client accepts/poses or refuses → inspect →
     * jolt or completed; failed approaches stay in the denominator. A client passing by is not a cleaning visit.
     */
    class Encounters { var approaches = 0; var failedApproaches = 0; var advertised = 0; var accepted = 0; var refused = 0; var jolts = 0; var completed = 0 }
    val encounters = Encounters()

    /**
     * Who gets the tactile dance first (06 §5.5, §2.11): high-ranked ("predatory") clients, hungry
     * ones, and any client that has started a chase in the last hour: tactile stimulation is
     * pre-conflict management aimed at likely aggressors (S:Grutter04, S:Cheney08).
     */
    /** Relative lateral area of a client: (L / 7 cm)² × √(depth / 0.35); 1 for a 7 cm, mid-depth fish. */
    fun clientArea(f: Organism): Double = (f.lengthCm / 7.0).let { it * it } * sqrt(f.species.depthRatio / 0.35)

    fun wantsTactile(f: Organism, c: Ctx): Boolean =
        PairMatrix.baseRank(f, c.sim) >= TACTILE_RANK || f.hunger > 0.6 || f.counters.lastHour(Counters.K.CHASE_GIVEN, c.t) > 0

    /** Pause until then between bouts; after a bout the wrasse goes back to its station half the time (06 §5.5). */
    var restUntil = 0.0
    var stationBias = -1.0

    companion object {
        /** Greeting dance: trigger radius (DESIGN ESTIMATE), see-saw rate and the small vertical bob that goes with it. */
        const val GREET_CM = 15.0
        /** Inspection length of a fully sated cleaner relative to a hungry one (direction SOURCED-snippet, size DESIGN ESTIMATE). */
        const val SATED_SERVICE = 0.8
        const val DANCE_HZ = 2.0
        const val DANCE_BOB_CM = 0.8
        /** Per-client cooldown: 60 s, not 10 min (D:Grutter96). */
        const val CLIENT_COOLDOWN_S = 60.0
        /** A client's keenness to pose recovers linearly over this long after its last full pose, past [POSE_MIN_GAP_S] [I]. */
        const val POSE_REFRACT_S = 2250.0
        /**
         * Shortest spacing of a client's full poses (21.5 min): at most 15 in a 5 h day, 3 per hour, inside the tang's 1–3/h
         * (06 §5.5), however often the cleaner and client meet [I].
         */
        const val POSE_MIN_GAP_S = 1290.0
        /** Client weight factor once a full pose is due again (past [POSE_MIN_GAP_S]) [I]. */
        const val POSE_DUE_WEIGHT = 3.0
        /** Golden-ratio step of the per-client inspection quantile sequence. */
        const val GOLDEN = 0.6180339887498949
        /** Search radius for clients (06 §5.5). */
        const val SEARCH_CM = 40.0
        /** Juvenile cleaners search a smaller radius around the station (life_stages.md §4: DESIGN ESTIMATE [L]). */
        const val JUVENILE_SEARCH = 0.6
        /**
         * A client not touched for this long gets a higher weight and is sought anywhere in the tank (06 §5.6 #4). A DESIGN
         * ESTIMATE that biases choice only: research 10 §4 finds no visit guarantee, and a client can still refuse.
         */
        @com.reeflive.core.sim.Ev(com.reeflive.core.sim.Evidence.DESIGN_ESTIMATE, "10 §4") const val OVERDUE_S = 30 * 60.0
        /** Base rank from which a client gets the tactile dance first (hawk = 6; S:Grutter04, S:Cheney08). */
        const val TACTILE_RANK = 6.0
        /** An aggressor whose tactile damping has lapsed (or lapses within this many s) is worth up to 1 + this much more (06 §3.5). */
        const val DAMP_RENEW_S = 120.0
        const val AGGRESSOR_WEIGHT = 3.0
        /** A client's weight grows by 1× per this many seconds since its last contact (capped at [OVERDUE_S]). */
        const val DUE_S = 900.0
        const val OVERDUE_WEIGHT = 10.0
        /** Lateral area (length² × depth ratio) of a 7 cm fish of depth ratio 0.35: weight 1. */
        const val AREA_REF = 49.0 * 0.35
        /** Approach timeout, and how long a client it could not reach is skipped. */
        const val APPROACH_GIVE_UP_S = 20.0
        const val UNREACHABLE_S = 300.0
        /** Honest gleaning: hunger −0.002/s at full parasite load. */
        const val HONEST_YIELD_PER_S = 0.002
        const val REST_MIN_S = 15.0
        const val REST_MAX_S = 75.0
        /** Cheating per second of inspection: 0.04 baseline → 0.17 when hunger > 0.6 (S:Pinto11). */
        fun cheatRate(w: Organism): Double = if (w.hunger > 0.6) 0.19 else 0.02
    }

    /** Between clients: zip along the rock face in the lower-mid zone. */
    inner class Surf : Behavior("surf", "Patrolling the rocks") {
        private var wp = Vec3.ZERO
        private var next = 0.0
        private var centre = Vec3.ZERO
        private var nextCentre = 0.0
        override fun score(c: Ctx) = 0.4 * c.activity
        override fun update(c: Ctx, out: SteerIntent) {
            // roams the whole reef between clients, a stretch at a time (06 §5.2: whole tank, station bias)
            if (c.t > nextCentre) {
                nextCentre = c.t + c.rng.range(20.0, 45.0)
                centre = if (c.rng.chance(0.4)) stationPos else Vec3(c.rng.range(12.0, c.rock.lengthCm - 12), stationPos.y, stationPos.z)
            }
            if (c.t > next || c.o.pos.distanceTo(wp) < 1.5) {
                next = c.t + c.rng.range(1.5, 3.0)
                val sp = c.rockSurfacePoint(centre + Vec3(c.rng.signed() * 20, c.rng.signed() * 10, 0.0), 12.0)
                wp = sp?.let { c.safe(it.first + it.second * (c.clearance + 0.5)) } ?: c.safe(stationPos + c.rng.unitVector() * 15.0)
                if (wp.y > c.zoneY(0.6)) wp = c.safe(wp.withY(c.zoneY(0.45)))
            }
            out.seek(wp, c.o.cruiseSpeed * 1.3, arrive = 2.0)
            out.wander = 0.2
        }
    }

    /** Night: motionless in a mucus cocoon in the small cave. */
    inner class Cocoon : Behavior("cocoon", "Sleeping in cocoon") {
        override fun score(c: Ctx) = 0.85 * (1 - c.activity) * Feed.nightRestDamp(c, this@WrasseBrain)
        override fun update(c: Ctx, out: SteerIntent) {
            val r = restSpot(c, true)
            // the cave-mouth steering keeps the fish 4.5-7 cm off the spot, so settle from within 8 cm
            if (c.o.pos.distanceTo(r.pos) > 8) { out.seek(r.pos, c.o.cruiseSpeed, arrive = 2.0); return }
            out.rest(r, 1.5)
            c.o.sleeping = true
        }
    }
}

/** Shared end-of-cleaning bookkeeping for wrasse and skunk shrimp (SIMULATION §4.5, 04 §13C). */
fun finishClean(c: Ctx, client: Organism, isShrimp: Boolean, feed: Boolean = true) {
    val ich = client.disease == Disease.ICH
    client.lastCleanedAt = c.t
    client.lastCleanerContactAt = c.t
    val load = client.parasiteLoad
    client.parasiteLoad = 0.0
    if (ich) client.diseaseStage = max(0.0, client.diseaseStage - 0.02)
    if (feed) c.o.hunger = max(0.0, c.o.hunger - (if (ich) 0.08 else 0.02) * (if (isShrimp) 0.8 else 1.0) - 0.03 * load)
    Social.recordCleaning(c.o, client, c.t)
    c.sim.emit(SimEvent.Cleaned(c.o.id, client.id, c.sim.simTimeSec))
}

// =====================================================================================
// Snowflake eel (EelBrain)
// =====================================================================================
class EelBrain(c: Ctx) : SpeciesBrain(c) {
    val cave: Anchor? = c.rock.anchors["CAVE_EEL"]
    val mouth: Vec3 = cave?.pos ?: Vec3(62.0, 3.8, 32.5)
    /** Direction from the mouth into the den. */
    val inDir: Vec3 = (cave?.dir ?: Vec3(0.5, 0.1, -0.85)).normalized()
    val outDir: Vec3 = (inDir * -1.0).let { Vec3(it.x, 0.05, it.z).normalized(Vec3.Z) }
    val denPath: List<Vec3> = c.rock.anchors["den_path"]?.points ?: listOf(mouth, mouth + inDir * 10.0, mouth + inDir * 20.0)
    private val strikeChance = HashMap<String, Double>()

    init {
        install(HeadOut(), Roam(), Hunt(), Feed(this, 30.0),
            Flee(this, { _, _ -> mouthOut(10.0) }, "Retreating to den"), SurfaceGasp(this))
    }

    fun mouthOut(k: Double) = Vec3(mouth.x, max(mouth.y, c.rock.sandTopCm + 1.5), mouth.z) + outDir * k

    fun denSpline(head: Vec3): List<Vec3> = listOf(head, mouth) + denPath.drop(1) + listOf(denPath.last() + (denPath.last() - denPath[denPath.size - 2]).normalized(Vec3.X) * 30.0)

    override fun restSpot(c: Ctx, night: Boolean): Rest {
        val k = if (night) 8.0 else 2.5
        val head = mouthOut(k)
        return Rest(head, outDir, posture = 3, spline = denSpline(head))
    }

    override fun findThreat(c: Ctx): Organism? = null

    companion object { const val STRIKE_SPEED = 100.0 }

    /** In the den with the head out, slow sway, gill/mouth pumping; lunges at food within 8 cm. */
    inner class HeadOut : Behavior("headout", "Head out of den") {
        private var hidden = false
        private var nextHide = 0.0
        private var outK = 6.0
        private var nextBite = 0.0
        private var approaching = true
        override fun score(c: Ctx): Double {
            val o = c.o
            if (c.t < o.satietyUntil) return 0.7
            return if (c.p.night) 0.3 + 0.3 * (1 - o.hunger) else 0.65
        }
        override fun enter(c: Ctx) { approaching = !(c.o.posture == 3 || c.o.pos.distanceTo(mouth) < 14) }
        override fun update(c: Ctx, out: SteerIntent) {
            val o = c.o
            if (approaching) {
                out.seek(mouthOut(9.0), o.cruiseSpeed, arrive = 3.0)
                if (o.pos.distanceTo(mouthOut(9.0)) < 4) approaching = false
                return
            }
            if (c.t > nextHide) {
                nextHide = c.t + c.rng.range(60.0, 240.0)
                hidden = !c.p.night && c.rng.chance(0.3)
                outK = if (c.p.night) c.rng.range(5.0, 12.0) else 2.5
            }
            val sway = sin(2 * PI * 0.2 * c.t) * 2.0
            val side = (outDir cross Vec3.Y).normalized(Vec3.X)
            var head = c.rock.clampToWater(mouthOut(if (hidden) 0.5 else outK) + side * sway, 2.5)
            // lunge at particles sinking within 8 cm of the head
            val f = if (o.hunger > 0.1) c.food.nearest(head, 8.0) { o.species.dietMatch(it.kind) > 0 } else null
            if (f != null) {
                head = c.rock.clampToWater(mouthOut(1.0) + (f.pos - mouthOut(1.0)).clampLength(14.0), 2.5)
                if (o.pos.distanceTo(f.pos) < 1.8 && c.t > nextBite) { c.bite(f); nextBite = c.t + 0.8 }
            }
            val heading = (head - mouth).normalized(outDir)
            out.place(head, heading, Vec3.Y, if (f != null) 8.0 else 2.0)
            out.posture = 3
            out.splineOverride = denSpline(o.pos)
            o.gill = 0.5 + 0.5 * sin(2 * PI * 0.4 * c.t)
            o.mouth = max(o.mouth, o.gill * 0.6)
            o.sleeping = !c.p.night && hidden
        }
    }

    /** Night (or hungry): anguilliform travel along the sand/rock interface, exploring crevices. */
    inner class Roam : Behavior("roam", "Roaming") {
        private var wp = Vec3.ZERO
        private var next = 0.0
        override fun score(c: Ctx): Double {
            val o = c.o
            if (c.t < o.satietyUntil) return 0.0
            return if (c.p.night) 0.5 + 0.3 * o.hunger else if (o.hunger > 0.6) 0.4 + 0.3 * o.hunger else 0.0
        }
        override fun enter(c: Ctx) { next = 0.0; wp = mouthOut(15.0) }
        override fun update(c: Ctx, out: SteerIntent) {
            val o = c.o
            if (c.t > next || o.pos.distanceTo(wp) < 6) {
                next = c.t + c.rng.range(8.0, 20.0)
                val targets = c.rock.anchors.ofType("CAVE") + c.rock.anchors.ofType("HOLE") + c.rock.anchors.ofType("GRAZE").filter { it.pos.y < 15 }
                val p = if (targets.isNotEmpty() && c.rng.chance(0.6)) targets[c.rng.nextInt(targets.size)].pos
                else Vec3(c.rng.range(8.0, 114.0), c.rock.sandTopCm + 4, c.rock.widthCm * (if (c.rng.chance(0.5)) c.rng.range(0.79, 0.91) else c.rng.range(0.42, 0.91)))
                wp = c.safe(p.withY(min(p.y, c.zoneY(0.35))))
            }
            out.seek(wp, o.cruiseSpeed, arrive = 6.0)
            out.wander = 0.2
            o.gill = 0.5 + 0.5 * sin(2 * PI * 0.4 * c.t)
        }
    }

    /** Hungry: stalk prey and strike within 30° of the head (SIMULATION §4.5, 04 §13). */
    inner class Hunt : Behavior("hunt", "Hunting") {
        private var prey: Organism? = null
        private var strikeAt = -1.0
        private var cooldown = 0.0
        override val committed: Boolean get() = strikeAt >= 0
        override fun score(c: Ctx): Double {
            val o = c.o
            if (o.hunger < 0.7 || c.t < o.satietyUntil || c.t < cooldown) return 0.0
            if (!c.p.night && o.hunger < 0.85) return 0.0
            val p = choose(c) ?: return 0.0
            if (strikeAt < 0) prey = p
            return (0.75 + 0.2 * o.hunger) * (if (c.p.night) 1.0 else 0.8)
        }
        private fun choose(c: Ctx): Organism? {
            var best: Organism? = null
            var bs = 0.0
            for (n in c.p.neighbors(c.o.pos, 50.0)) {
                var w = Predation.preyWeight(c.o, n, c.p.night)
                if (w <= 0 || !n.visible) continue
                if (n.posture == 3 || n.posture == 5) continue                  // in a hole / buried
                if (n.posture == 2 && n.brainKind == BrainKind.BANDED_SHRIMP) {
                    // ledge shrimp is safe unless the roam passes within 6 cm on a hungry night (p = 0.3 per pass)
                    if (n.pos.distanceTo(c.o.pos) > 6) continue
                    val roll = strikeChance.getOrPut(n.id) { c.rng.nextDouble() }
                    if (roll > 0.3) continue
                }
                val d = n.pos.distanceTo(c.o.pos)
                w /= (1 + d / 20)
                if (w > bs) { bs = w; best = n }
            }
            return best
        }
        override fun enter(c: Ctx) { strikeAt = -1.0 }
        override fun update(c: Ctx, out: SteerIntent) {
            val o = c.o
            val p = prey
            if (p == null || !p.alive || p !in c.sim.organisms) { finished = true; return }
            val to = p.pos - o.pos
            val d = to.length()
            if (strikeAt < 0) {
                out.seek(p.pos, o.cruiseSpeed * 1.3, arrive = 2.0, urgency = 0.5)
                val ang = Vec3.angle(o.heading, to)
                if ((d < min(1.5 * o.lengthCm, 14.0) && ang < PI / 6) || d < 4.0) { strikeAt = c.t; o.mouth = 1.0 }   // close contact: the head whips round
                if (d > 60 || elapsed > 120) { finished = true; cooldown = c.t + 30 }
            } else {
                // moray strike: a fast lunge (≈ 1 m/s), resolved the moment the jaws reach the prey
                out.seek(p.pos, STRIKE_SPEED, arrive = 0.2, urgency = 1.0)
                o.mouth = 1.0
                val hit = d < 2.0 + 0.5 * p.lengthCm
                if (hit || c.t - strikeAt > 0.4) {
                    // the kill roll is unsourced (realism_gaps X7): only behind the evidence gate; otherwise an attack
                    if (hit && c.sim.predationEnabled && c.rng.nextDouble() >= 0.55 && c.sim.unsourcedPredation) {
                        c.sim.eaten(o, p)
                    } else {
                        c.sim.agentOf(p)?.let { it.brain.onAttacked(it.ctx, o.pos, 1.0) }
                        p.startle = max(p.startle, 0.4)
                    }
                    strikeAt = -1.0
                    cooldown = c.t + 15
                    finished = true
                }
            }
        }
    }
}

// =====================================================================================
// Falco hawkfish (HawkBrain)
// =====================================================================================
class HawkBrain(c: Ctx) : SpeciesBrain(c) {
    /** Named perches plus rock tops found at start-up ([PerchSites]); the numbered PERCH_n aliases are dropped. */
    val perches: List<Anchor> = PerchSites.build(c.rock, { x, z -> c.sim.sand.surfaceY(x, z) }, c.rock.anchors.ofType("PERCH"))
        .ifEmpty { listOf(Anchor("PERCH_X", "PERCH", Vec3(43.0, 20.1, 28.8), normal = Vec3.Y)) }
    /** The last few perches it sat on; it moves on round its range instead of returning straight away. */
    private val recent = ArrayDeque<Anchor>()
    /** Perches it set off for and could not reach, with when (skipped for [UNREACHABLE_S]). */
    private val unreachable = HashMap<Anchor, Double>()
    var current: Anchor = perches.minBy { it.pos.distanceTo(Vec3(43.0, 20.1, 28.8)) }
    /** Night rest on open sand at the rock base (ai/HawkNight.kt; Tee's footage T89 [H]). */
    val sandRest = SandRest(this)

    init {
        install(sandRest, PerchHop(), Pounce(), DuckToPerch(), SurfaceGasp(this), VisitCleaner(this))
    }


    fun perchPose(a: Anchor): Rest {
        val n = (a.normal ?: Vec3.Y).normalized(Vec3.Y)
        val outward = Vec3(0.0, 0.0, 1.0).rejectFrom(n).normalized((Vec3.X cross n).normalized(Vec3.X))
        return Rest(a.pos + n * (0.3 * o.lengthCm), outward, n, posture = 1)
    }

    fun lowestSheltered(): Anchor = perches.filter { !it.name.startsWith("PERCH_TOP") }.ifEmpty { perches }.minBy { it.pos.y }

    /**
     * Perch → scan → hop → landing and strike outcomes as separate counts (research 10 §5). A strike on a live animal
     * is approach → strike → no contact (miss) / contact → injury or kill; a kill needs a soft (recently molted) or
     * small shrimp (research 10 §5, 11 §10: no fixed capture probability).
     */
    class Events { var scans = 0; var departures = 0; var landings = 0; var foodStrikes = 0; var preyApproaches = 0; var misses = 0; var injuries = 0; var kills = 0 }
    val events = Events()

    companion object {
        /** Contact per strike, and kill per contact on a soft or small shrimp (DESIGN ESTIMATES, research 10 §5). */
        @com.reeflive.core.sim.Ev(com.reeflive.core.sim.Evidence.DESIGN_ESTIMATE, "10 §5") const val STRIKE_CONTACT_P = 0.5
        const val KILL_SOFT_P = 0.5
        /** Hours after a molt that a shrimp's shell is still soft. */
        const val SOFT_SHELL_H = 48.0
        /** Shrimp below this fraction of the hawk's length count as small (DESIGN ESTIMATE). */
        const val SMALL_PREY_FRAC = 0.5
        /**
         * Perch dwell, s: a floor plus an exponential tail with a 66–79 s mean between perch changes (*Paracirrhites
         * forsteri*, Coker et al. 2017 PLoS ONE 12:e0169079: 7.6–9.1 changes per 10 min; research 30 §3). The older
         * 120–600 s (5–20 hops/h) had no source.
         */
        @com.reeflive.core.sim.Ev(com.reeflive.core.sim.Evidence.ANALOGY, "Coker 2017, P. forsteri; research 30 §3") const val DWELL_FLOOR_S = 15.0
        const val DWELL_MEAN_S = 57.0
        const val DWELL_MAX_S = 300.0
        /** Preferred hop length, cm (most perch changes are short; DESIGN ESTIMATE scaled to the tank), and the longest. */
        const val HOP_PREF_CM = 22.0
        const val HOP_MAX_CM = 70.0
        /** A duck no closer to its approach point for this long (s) clamps onto the perch from where it is ... */
        const val DUCK_STALL_S = 2.0
        /** ... when the perch is this near (cm) with open water between. */
        const val DUCK_REACH_CM = 15.0
        const val UNREACHABLE_S = 900.0
        /** Perched head-up angle, rad (~15°; research video fish_falco_hawkfish [S1][M]). */
        const val PERCH_HEAD_UP = 0.26
    }

    /** The perch farthest from [from] among those within reach (else the nearest), made current; returns its approach point. */
    private fun escapePerch(c: Ctx, from: Vec3): Vec3 {
        val near = perches.filter { it.pos.distanceTo(c.o.pos) < 40.0 }.ifEmpty { listOf(perches.minBy { it.pos.distanceTo(c.o.pos) }) }
        current = near.maxBy { it.pos.distanceTo(from) }
        val pose = perchPose(current)
        return c.safe(pose.pos + pose.up!! * c.clearance, c.clearance * 0.8)
    }

    /**
     * Startle without a swim bladder (research video fish_falco_hawkfish [S2][M]): a C-start burst to a perch away from
     * the threat, then it clamps onto the rock there instead of hovering in cover like a mid-water fish.
     */
    inner class DuckToPerch : Flee(this@HawkBrain, { c, from -> escapePerch(c, from) }, "Ducking behind rock") {
        private var bestD = Double.MAX_VALUE
        private var progressAt = 0.0
        override fun enter(c: Ctx) { super.enter(c); bestD = Double.MAX_VALUE; progressAt = c.t }
        override fun update(c: Ctx, out: SteerIntent) {
            super.update(c, out)
            val o = c.o
            val pose = perchPose(current)
            val t = threat
            // as close to the approach point as the water lets it: an approach point over a perch just under the surface
            // (PERCH_SHELF) is clamped up to where the steering keeps a swimmer off the surface, and it hung a few cm
            // short of it, unclamped, for the whole duck (MotionTest hawkfishNeverCruises). Stalled with open water to
            // the perch, it clamps on from there
            val dh = o.pos.distanceTo(hideTarget)
            if (dh < bestD - 0.5) { bestD = dh; progressAt = c.t }
            val stalled = c.t - progressAt > DUCK_STALL_S && o.pos.distanceTo(pose.pos) < DUCK_REACH_CM &&
                c.rock.sdf.segmentClear(o.pos, pose.pos, 0.5)
            // at the perch, or at its approach point (on a rock top that sits a clearance above it: it hovered there unclamped)
            val there = o.pos.distanceTo(pose.pos) < 4.0 + 0.5 * o.lengthCm || dh < 2.5 || stalled
            if (elapsed > 0.8 && there && (t == null || t.pos.distanceTo(o.pos) > 8.0)) {
                out.place(pose.pos, pose.heading, pose.up, 8.0); out.posture = 1; out.pectoral = 1.0
                out.target = null; out.fleeFrom = null; out.hover = false
            }
        }
    }

    /** Outcome of a strike that reached a live animal (sandbox only; predation must be on). */
    fun strikeOutcome(c: Ctx, prey: Organism): String {
        val o = c.o
        if (!c.rng.chance(STRIKE_CONTACT_P)) { events.misses++; c.sim.agentOf(prey)?.let { it.brain.onAttacked(it.ctx, o.pos, 0.6, o) }; return "miss" }
        val soft = prey.molting || c.t - prey.lastMoltAt < SOFT_SHELL_H * 3600
        val small = prey.lengthCm < SMALL_PREY_FRAC * o.lengthCm
        if ((soft || small) && c.rng.chance(KILL_SOFT_P)) { events.kills++; c.sim.eaten(o, prey); return "kill" }
        events.injuries++
        prey.health = max(0.05, prey.health - 0.1)
        c.sim.agentOf(prey)?.let { it.brain.onAttacked(it.ctx, o.pos, 1.0, o) }
        return "injury"
    }

    override fun anchorPos(c: Ctx, anchor: String): Vec3? = if (anchor == "perch") current.pos else super.anchorPos(c, anchor)

    override fun restSpot(c: Ctx, night: Boolean): Rest = if (night) sandRest.restPose(c) ?: perchPose(lowestSheltered()) else perchPose(current)

    /** A fresh perch dwell, s ([DWELL_FLOOR_S] + exponential tail; shorter when hungry). */
    private fun dwellFor(c: Ctx): Double =
        ((DWELL_FLOOR_S - ln(1 - c.rng.nextDouble()) * DWELL_MEAN_S) * (1.25 - 0.5 * c.o.hunger)).coerceAtMost(DWELL_MAX_S)

    /**
     * The next perch, drawn at random by weight: hops of about [HOP_PREF_CM] are most likely, perches it just used,
     * perches another animal sits on and the clowns' host are avoided, and drifting food or fish moving nearby draw
     * it (an ambush hunter repositions toward activity; research 30 §3). Null if no other perch is usable.
     */
    private fun nextPerch(c: Ctx): Anchor? {
        val o = c.o
        var total = 0.0
        val w = DoubleArray(perches.size)
        val host = c.sim.organisms.firstOrNull { it.brainKind == BrainKind.CLOWN && it.alive }?.let { c.sim.agentOf(it)?.brain?.anchorPos(c, "host") }
        for ((i, a) in perches.withIndex()) {
            if (a === current) continue
            val d = a.pos.distanceTo(o.pos)
            if (d < 6.0 || d > HOP_MAX_CM || !PerchSites.valid(c.rock, a)) continue
            val failedAt = unreachable[a]
            if (failedAt != null && c.t - failedAt < UNREACHABLE_S) continue
            val g = (d - HOP_PREF_CM) / 18.0
            var x = exp(-g * g) + 0.05
            if (a in recent) x *= 0.15
            // taken: any animal on or beside it (a hawkfish landing on a grazing hermit sent it into its shell)
            if (c.p.neighbors(a.pos, o.lengthCm + 8.0).any { it !== o && it.alive && it.pos.distanceTo(a.pos) < max(6.0, 1.2 * it.lengthCm + 3.0) }) continue
            if (host != null && a.pos.distanceTo(host) < 12.0) x *= 0.1
            if (c.p.stations.any { it.pos.distanceTo(a.pos) < 14.0 }) x *= 0.2
            if (c.food.countNear(a.pos, 20.0) > 0) x += 0.8
            val busy = c.p.neighbors(a.pos, 25.0).count { it !== o && it.isFish && it.alive && it.vel.length() > 0.5 * it.lengthCm }
            x *= 1.0 + 0.15 * min(busy, 3) + 0.25 * ((a.pos.y - c.rock.sandTopCm) / (c.rock.waterLevelCm - c.rock.sandTopCm))
            if (x > 0) { w[i] = x; total += x }   // (NaN-safe: a NaN weight is dropped)
        }
        if (!(total > 0)) return null
        var r = c.rng.nextDouble() * total
        for (i in perches.indices) { r -= w[i]; if (w[i] > 0 && r <= 0) return perches[i] }
        return w.indices.lastOrNull { w[it] > 0 }?.let { perches[it] }
    }

    /**
     * Sit propped on the pectorals, scanning with quick head turns that lock onto passing fish and drifting food, now
     * and then pivoting to face another way; then eye the next perch, crouch, and hop (one change every ~70 s on
     * average, research 30 §3).
     */
    inner class PerchHop : Behavior("perch", "Perched") {
        private var state = 0               // 0 perched, 1 hopping, 2 settling, 3 eyeing the next perch
        private var dwell = 30.0
        private var since = 0.0
        private var target: Anchor = current
        private var nextLook = 0.0
        private var lookYaw = 0.0
        private var baseYaw = 0.0
        private var nextPivot = 0.0
        private var retargeted = false
        private var bestDist = 1e9
        private var bestAt = 0.0
        override fun score(c: Ctx) = 0.5
        override fun enter(c: Ctx) {
            val pose = perchPose(current)
            state = if (c.o.pos.distanceTo(pose.pos) < 2) 0 else 1
            target = if (state == 1) perches.minBy { it.pos.distanceTo(c.o.pos) } else current
            since = c.t; dwell = dwellFor(c); retargeted = false; bestDist = 1e9; bestAt = c.t
        }
        private fun land(c: Ctx) {
            current = target; state = 0; since = c.t; dwell = dwellFor(c); events.landings++
            recent.addLast(current); while (recent.size > 4) recent.removeFirst()
            baseYaw = c.rng.signed() * 0.5; nextPivot = c.t + c.rng.range(12.0, 40.0)
        }
        override fun update(c: Ctx, out: SteerIntent) {
            val o = c.o
            when (state) {
                0 -> {
                    val pose = perchPose(current)
                    val up = pose.up!!
                    if (c.t > nextPivot) { nextPivot = c.t + c.rng.range(12.0, 40.0); baseYaw = (baseYaw + c.rng.signed() * 1.2).coerceIn(-1.4, 1.4) }
                    val base = Vec3.rotateAbout(pose.heading, up, baseYaw)
                    if (c.t > nextLook) {
                        // saccade-like glances: quick turns, held 0.6–3 s, onto food or the nearest moving fish when there is one
                        nextLook = c.t + c.rng.range(1.2, 4.0)
                        events.scans++
                        val food = c.findFood(40.0, los = false)
                        val passer = food?.pos ?: nearestOther(c, 35.0) { it.isFish && it.vel.length() > 0.3 * it.lengthCm }?.pos
                        lookYaw = if (passer != null) {
                            val to = (passer - o.pos).rejectFrom(up)
                            val sgn = if (((base cross to) dot up) > 0) 1.0 else -1.0
                            (Vec3.angle(base, to) * sgn).coerceIn(-0.5, 0.5)
                        } else c.rng.signed() * 0.4
                    }
                    val h = Vec3.rotateAbout(base, up, lookYaw)
                    // propped on the thick lower pectoral rays, head up 10–20° (research video fish_falco_hawkfish [S1][M])
                    val side = (h cross up).normalized(Vec3.Z)
                    out.place(pose.pos, Vec3.rotateAbout(h, side, PERCH_HEAD_UP), Vec3.rotateAbout(up, side, PERCH_HEAD_UP), 9.0)
                    out.posture = 1; out.pectoral = 1.0
                    o.eyeYaw = lookYaw * 0.5
                    o.sleeping = c.p.night
                    if (!c.p.night && c.t - since > dwell) {
                        val next = nextPerch(c)
                        if (next != null) { target = next; state = 3; since = c.t } else { since = c.t; dwell = dwellFor(c) }
                    }
                    if (c.p.night && current !== lowestSheltered()) { target = lowestSheltered(); state = 1; bestDist = 1e9; bestAt = c.t }
                }
                3 -> {
                    // turn on the perch to face the next one and crouch (0.4–0.8 s), then go
                    val pose = perchPose(current)
                    val up = pose.up!!
                    val to = (target.pos - o.pos).rejectFrom(up).normalized(pose.heading)
                    val side = (to cross up).normalized(Vec3.Z)
                    out.place(pose.pos, Vec3.rotateAbout(to, side, 0.1), up, 7.0)
                    out.posture = 1; out.pectoral = 1.0; o.eyeYaw = 0.0
                    if (c.t - since > 0.4 + 0.4 * o.whim / 0.055) { state = 1; since = c.t; retargeted = false; bestDist = 1e9; bestAt = c.t; events.departures++ }
                }
                1 -> {
                    val pose = perchPose(target)
                    val n = pose.up!!
                    val land = c.safe(pose.pos + n * (c.clearance), c.clearance * 0.8)
                    val dist = o.pos.distanceTo(land)
                    val arc = if (dist > 8) Vec3(0.0, 4.0, 0.0) else Vec3.ZERO
                    out.seek(c.viaOpenWater(c.safe(land + arc)), 3 * o.lengthCm, arrive = 2.0, urgency = 0.6)
                    // stalled short of the landing spot (pressed against rock, no progress for 1.5 s): settle, don't hover
                    if (dist < bestDist - 0.3) { bestDist = dist; bestAt = c.t }
                    val stalled = dist < 6.0 && c.t - bestAt > 1.5
                    if (dist < 2.0 || stalled || c.t - since > 8 && dist < 6.0 || c.t - since > 18) { state = 2; since = c.t }
                    else if (c.t - since > 8 && !retargeted) {
                        // could not get there (no way round the rock it can find): remember it and settle on the nearest
                        // other perch instead; it only clamps on kinematically from close by (a 13 cm snap was a pop)
                        unreachable[target] = c.t
                        target = perches.filter { it !== target && PerchSites.valid(c.rock, it) && it !in unreachable }.ifEmpty { perches }
                            .minBy { it.pos.distanceTo(o.pos) }
                        retargeted = true; bestDist = 1e9; bestAt = c.t
                    }
                }
                2 -> {
                    val pose = perchPose(target)
                    out.place(pose.pos, pose.heading, pose.up, 8.0); out.posture = 1
                    out.pitchBias = -0.1
                    if (c.t - since > 0.3 && o.pos.distanceTo(pose.pos) < 0.8 || c.t - since > 3) land(c)
                }
            }
        }
    }

    /** Food (or small prey in sandbox) within 15 cm and in view → burst strike, then return. */
    inner class Pounce : Behavior("pounce", "Pouncing") {
        private var f: FoodParticle? = null
        private var prey: Organism? = null
        override fun enter(c: Ctx) { if (f == null && prey != null) events.preyApproaches++ }
        override fun score(c: Ctx): Double {
            val o = c.o
            if (o.hunger < 0.15) return 0.0
            val r = if (c.t - foodAlertAt < 90) 40.0 else if (o.hunger > 0.3) 25.0 else 15.0
            f = c.findFood(r) { Vec3.angle(o.heading, it.pos - o.pos) < 1.9 || it.pos.distanceTo(o.pos) < 8 }
            if (f != null) return 0.75
            if (c.sim.predationEnabled && o.hunger > 0.5) {
                prey = nearestOther(c, 15.0) { Predation.preyWeight(o, it, c.p.night) > 0 && it.posture != 3 }
                if (prey != null) return 0.65
            }
            return 0.0
        }
        override fun update(c: Ctx, out: SteerIntent) {
            val o = c.o
            val ff = f
            if (ff != null) {
                if (ff.eaten) { finished = true; return }
                out.seek(ff.pos, o.burstSpeed * 0.8, arrive = 0.5, urgency = 0.8)
                if (o.pos.distanceTo(ff.pos) < 1.2 + 0.1 * o.lengthCm) { c.bite(ff); events.foodStrikes++; finished = true }
            } else {
                val p = prey ?: run { finished = true; return }
                out.seek(p.pos, o.burstSpeed, arrive = 0.5, urgency = 1.0)
                if (o.pos.distanceTo(p.pos) < 1.5 + 0.5 * p.lengthCm) {
                    strikeOutcome(c, p)
                    finished = true
                }
            }
            if (elapsed > 4) finished = true
        }
    }

    /** Short 1-s charge at a wrasse or blenny within 1 BL of its perch. */
    inner class ChaseIntruder : Behavior("chase", "Chasing off") {
        private var who: Organism? = null
        override fun score(c: Ctx): Double {
            if (c.p.night || c.o.aggression < 0.2) return 0.0
            val pp = current.pos
            who = c.p.neighbors(pp, c.L + 4).firstOrNull {
                it.alive && it !== c.o && (it.brainKind == BrainKind.WRASSE || it.brainKind == BrainKind.LAWNMOWER_BLENNY || it.brainKind == BrainKind.PEEKER_BLENNY) && it.pos.distanceTo(pp) < c.L
            }
            return if (who != null) 0.6 else 0.0
        }
        override fun update(c: Ctx, out: SteerIntent) {
            val w = who ?: run { finished = true; return }
            out.seek(w.pos, c.o.burstSpeed * 0.6, arrive = 1.0, urgency = 0.75)
            if (c.o.pos.distanceTo(w.pos) < c.L * 0.8) { c.sim.agentOf(w)?.let { it.brain.onAttacked(it.ctx, c.o.pos, 0.4, c.o) }; w.socialStress += 0.02; finished = true }
            if (elapsed > 1.0) finished = true
        }
    }
}

// =====================================================================================
// Hole dwellers: shared hole placement (SIMULATION §24)
// =====================================================================================
/**
 * How far in from the mouth the head sits for a [headOut] cm head-out pose (negative = inside). A hole shorter than
 * the body keeps the head further out, so the tail never reaches past the end of the hole into rock.
 */
fun holeHeadDepth(hole: RockHole, bodyLength: Double, headOut: Double): Double =
    min(-headOut, hole.depth - bodyLength - 0.2).coerceAtLeast(-bodyLength)

/**
 * Kinematic in-hole pose (posture 3): head [headOut] cm out of the mouth along the hole's axis (negative = inside),
 * facing out, the body lying back along the hole with the tail deepest (blennies back in tail first and perch with
 * only the head out; docs/research/video/fish_tailspot_blenny.md).
 */
fun holeRest(c: Ctx, hole: RockHole, headOut: Double): Rest {
    val d = holeHeadDepth(hole, c.L, headOut)
    val up = Vec3.Y.rejectFrom(hole.axis).normalized(hole.up)
    return Rest(hole.along(d), hole.axis, up, posture = 3, spline = hole.polyline(d, d + c.L))
}

/** How far a blenny's head comes out of its hole to watch a tap on the glass (cm; its day pose is 1–1.6). */
const val PEEK_HEAD_OUT_CM = 2.2

/** Where a hole-dweller with no fitting hole shelters: pressed against the rock face near [near], facing out. */
fun rockShelterRest(c: Ctx, near: Vec3): Rest {
    val p0 = c.rock.sdf.project(near, 0.0)
    val n = c.rock.sdf.gradient(p0)
    val h = Vec3.Y.rejectFrom(n).let { if (it.length() < 0.2) Vec3.X.rejectFrom(n) else it }.normalized(Vec3.X)
    return Rest(p0 + n * (0.5 * c.o.species.depthRatio * c.L + 0.3), h, n, posture = 1)
}

/**
 * Go home to a real hole the way a blenny does: swim to a point a body length out in front of the mouth, turn to face
 * out, back in tail first along the hole, then sit with the head out ([headOutFn] > 0) or fully in (< 0). [hole] must
 * fit the fish (RockHole.fits); with none it shelters against the rock face at [shelter] instead of entering rock.
 */
open class InHole(val b: SpeciesBrain, var hole: RockHole?, var shelter: Vec3, label: String, val scoreFn: (Ctx) -> Double, val headOutFn: (Ctx) -> Double) : Behavior("hole", label) {
    /** 0 swim to the stand point, 1 turn to face out, 2 back in tail first, 3 in the hole. */
    var phase = 0; private set
    /** The phase now, for tests and captions (same as [phase]). */
    val phaseNow: Int get() = phase
    private var phaseAt = 0.0
    private var dHead = 0.0
    /** Completed tail-first entries (tests, captions). */
    var backIns = 0; private set
    private var shelterRest: Rest? = null

    override val label: String get() = if (phase == 2) "Backing into its hole" else super.label
    override fun score(c: Ctx) = scoreFn(c)
    override fun enter(c: Ctx) {
        val h = c.rock.holes.live(hole)
        phase = if (h != null && c.o.posture == 3 && h.contains(c.o.pos, 0.8)) 3 else 0
        phaseAt = c.t
    }
    override fun exit(c: Ctx) {
        // the body lay along the hole: start the swim-out trail there instead of the stale pre-hole trail
        if (phase >= 2) c.o.spline.reset(c.o.pos, c.o.heading)
        phase = 0
    }
    override fun update(c: Ctx, out: SteerIntent) {
        // its own hole only: one another animal holds is never entered (it shelters on the rock face until its brain
        // finds a free one); claiming here also re-takes its hole after a reload
        val h = c.rock.holes.live(hole)?.takeIf { c.sim.holeClaims.claim(it, c.o) }
        if (h == null) {
            val r = shelterRest ?: rockShelterRest(c, shelter).also { shelterRest = it }
            if (c.o.pos.distanceTo(r.pos) > 2.0 && c.o.posture != 1) out.seek(c.viaOpenWater(c.safe(r.pos + r.up!! * c.clearance)), c.o.burstSpeed * 0.5, arrive = 1.5, urgency = 0.6)
            else out.rest(r, 4.0)
            c.o.sleeping = c.p.night
            return
        }
        val L = c.L
        val startD = -(L + 0.8)   // head here = tail tip just outside the mouth
        val stand = h.along(startD)
        // moved off its line (pushed, placed, startled away): go round again, tail first
        if (phase >= 2 && c.o.pos.distanceTo(h.along(if (phase == 2) dHead else holeHeadDepth(h, L, headOutFn(c)))) > 3.0 + 0.3 * L) {
            phase = 0; phaseAt = c.t
        }
        when (phase) {
            0 -> {
                out.seek(c.viaOpenWater(stand), c.o.burstSpeed * 0.5, arrive = 1.5, urgency = 0.6)
                if (c.o.pos.distanceTo(stand) < max(1.5, 0.2 * L) || c.t - phaseAt > 20) { phase = 1; phaseAt = c.t }
            }
            1 -> {
                // U-turn on the spot to face out of the hole
                // (it only starts backing once it faces out: a slow turn is pushed harder, then the approach restarts)
                out.place(stand, h.axis, null, if (c.t - phaseAt > 2.5) 20.0 else 6.0)
                out.pectoral = 1.0
                if ((c.o.heading dot h.axis) > 0.94) { phase = 2; phaseAt = c.t; dHead = startD }
                else if (c.t - phaseAt > 6.0) { phase = 0; phaseAt = c.t }
            }
            2 -> {
                val target = holeHeadDepth(h, L, headOutFn(c))
                // reversing on the pectorals with a tail wriggle, about a body length a second (faster when scared)
                val rate = if (b.startledRecently || b.threat != null) 1.6 else 0.8
                dHead = min(target, dHead + rate * L * c.dt)
                val up = Vec3.Y.rejectFrom(h.axis).normalized(h.up)
                out.place(h.along(dHead), h.axis, up, 30.0)
                out.posture = if (dHead + L > 0.3) 3 else 0
                out.splineOverride = h.polyline(dHead, dHead + L)
                out.ampMul = 1.4; out.tailMul = 1.3; out.pectoral = 1.0
                if (dHead >= target - 0.05) { phase = 3; backIns++ }
            }
            else -> {
                out.rest(holeRest(c, h, headOutFn(c)), 5.0)
                c.o.sleeping = c.p.night
                c.o.eyeYaw = 0.35 * sin(2 * PI * 0.5 * c.t + c.o.appendagePhase)
            }
        }
    }
}

// =====================================================================================
// Lawnmower blenny (Salarias fasciatus): perch, hop, constant grazing, territorial to blennies
// =====================================================================================
class LawnmowerBlennyBrain(c: Ctx) : SpeciesBrain(c) {
    /** Its home: a modelled hole that fits its body (ASSETS §3.6, HOLE_LAWNMOWER), or null (then it shelters on the rock face). */
    private val hole0: RockHole? = c.rock.holes.claimHome(o, setOf(HoleKind.HOLE, HoleKind.CAVE),
        (c.rock.anchors["HOLE_LAWNMOWER"] ?: c.rock.anchors["HOLE_BLENNY"])?.pos, c.sim.holeClaims)
    /** Where its hole is now (it rides with its rock when the rocks shift, sim/RockPhysics.kt). */
    val hole: RockHole? get() = c.rock.holes.live(hole0)
    private val faceShelter: Vec3 by lazy { c.rock.sdf.project(c.rock.anchors.pos("GRAZE_2", Vec3(30.0, 40.0, 14.0)), 0.5) }
    /** Territory centre: the hole mouth, else a rock face on the left stack (never inside rock). */
    val territory: Vec3 get() = hole?.mouth ?: faceShelter

    init {
        install(RockGraze(),
            InHole(this, hole, territory, "Resting in its hole",
                { c -> 0.8 * (1 - c.activity) + (if (c.o.health < 0.5) 0.3 else 0.0) + (if (c.t - startleAt < 12) 0.35 else 0.0) },
                { c -> if (c.p.night) -0.5 else 1.0 }),
            BenthicFeed(this, 35.0),
            Flee(this, { c, _ -> c.safe(hole?.along(-(c.clearance + 1.0)) ?: territory) }, "Darting home"), SurfaceGasp(this),
            // rasps a nori sheet on its clip (research 04: nori in its diet); quiet: no rng drawn while there is none
            NibbleNori(this, 0.75, quiet = true))
    }

    override fun anchorPos(c: Ctx, anchor: String): Vec3? = if (anchor == "hole" || anchor == "patch") territory else super.anchorPos(c, anchor)

    override fun shelterSpot(c: Ctx): Vec3? = hole?.let { c.safe(it.along(-(c.clearance + 1.0))) }

    /** A tap on the glass: head and shoulders out of the hole to look (research 04 §1C "watches from rock"). */
    override fun peekRest(c: Ctx): Rest? = hole?.let { holeRest(c, it, PEEK_HEAD_OUT_CM) }

    override fun restSpot(c: Ctx, night: Boolean): Rest {
        val h = hole
        return if (night) (if (h != null) holeRest(c, h, -0.5) else rockShelterRest(c, territory))
        else c.rockSurfacePoint(territory, 10.0)?.let { (p, n) -> perchOn(p, n) } ?: (if (h != null) holeRest(c, h, 1.0) else rockShelterRest(c, territory))
    }

    /** Rasps taken (one per second of grazing). */
    var rasps = 0; private set

    companion object {
        /** Hop speed (research video fish_lawnmower_blenny: "3 BL/s per hop" [L]), longest single hop, propped head-up angle (rad). */
        const val HOP_BL_S = 3.0
        const val HOP_MAX_BL = 3.0
        /** Points along a hop arc checked for rock before it takes off ([RockGraze.hopClear]). */
        const val HOP_PROBES = 10
        const val HEAD_UP = 0.26
        /**
         * Grazing comes in bite bouts with look-round pauses between them: 3,427 bites a day in the field, ~5 a minute
         * over the active day (Townsend & Tibbetts 2004, J Fish Biol 65:77; research 30), so the old one rasp every
         * second (60/min) was a burst ceiling held all day. Here ~20 a minute while it grazes. Bout length, bite
         * interval and pause are DESIGN ESTIMATES.
         */
        @com.reeflive.core.sim.Ev(com.reeflive.core.sim.Evidence.SOURCED, "Townsend & Tibbetts 2004; research 30") const val BOUT_MIN = 2
        const val BOUT_MAX = 5
        const val BITE_GAP_S = 0.65
        const val SCAN_MIN_S = 4.0
        const val SCAN_MAX_S = 12.0
        /** Hunger relief per rasp (×matrix quality), scaled up with the lower bite rate so it eats as much as before. */
        const val RASP_HUNGER = 0.018
        /** Its grazing range around its hole, cm (a territory the size of a dinner plate on the reef; DESIGN ESTIMATE). */
        const val RANGE_CM = 28.0
    }

    /**
     * Food value of the epilithic matrix it rasps (research 10 §6, Christiansen 2010: the bites remove photosynthetic
     * film *and* organic detritus, not just hair algae): rock film cover, hair algae and detritus, as a 0.5–1.5
     * multiplier. The old rule used hair algae alone (0.5 + 2·hair).
     */
    fun matrixQuality(c: Ctx): Double {
        val w = c.sim.water
        val film = 0.5 * (w.algae.cover(com.reeflive.core.sim.AlgaeGroup.FILM, com.reeflive.core.sim.AlgaeSurface.ROCK_TOP) +
            w.algae.cover(com.reeflive.core.sim.AlgaeGroup.DIATOMS, com.reeflive.core.sim.AlgaeSurface.ROCK_TOP))
        val hair = w.s.algaeHair
        val det = (w.s.detritus / 0.4).coerceIn(0.0, 1.0)
        return (0.5 + 0.8 * film + 1.2 * hair + 0.4 * det).coerceIn(0.5, 1.5)
    }

    fun perchOn(p: Vec3, n: Vec3): Rest {
        var h = Vec3.Y.rejectFrom(n).let { if (it.length() < 0.2) Vec3.X.rejectFrom(n) else it }.normalized(Vec3.X)
        val at = p + n * (0.5 * o.species.depthRatio * o.lengthCm + 0.3)
        // head-up low on a face would hang the body down into the sand (SandBed.surfaceY): lie along the face instead
        if (at.y - o.lengthCm * h.y < c.sim.sand.surfaceY(at.x, at.z) + 0.5) {
            val side = Vec3.Y.cross(n).let { if ((it dot o.heading) < 0) it * -1.0 else it }
            if (side.length() > 0.2) h = side.normalized(h)
        }
        return Rest(at, h, n, posture = 1)
    }

    /**
     * Perch on rock rasping algae (5–20 s) then hop 5–15 cm to the next patch. With no swim bladder (research video
     * fish_lawnmower_blenny [S2][M]) it does not swim between patches: it kicks off with a tail flick and lands in a
     * short ballistic arc at ~3 BL/s, and between bites it sits propped head-up on its pectoral and pelvic fins.
     */
    inner class RockGraze : Behavior("rockgraze", "Grazing on rock") {
        private var perch: Rest? = null
        private var state = 1
        private var until = 0.0
        private var nextRasp = 0.0
        private var hopT = -1.0
        private var hopDur = 0.5
        private var hopFrom = Vec3.ZERO
        private var hopUp = Vec3.Y
        override fun score(c: Ctx) = (0.3 + 0.3 * c.o.hunger + 0.1) * c.activity + 0.05
        override fun enter(c: Ctx) { pick(c) }
        private var biteLeft = 0
        private var lastBiteAt = -1e9
        private var scanYaw = 0.0
        private fun pick(c: Ctx) {
            // mostly a short hop to the next patch; sometimes across its range to another patch it knows
            val far = c.o.pos.distanceTo(territory) > RANGE_CM
            val centre = when {
                far -> territory
                c.rng.chance(0.2) -> territory + c.rng.unitVector() * (RANGE_CM * c.rng.nextDouble())
                else -> c.o.pos
            }
            val sp = c.rockSurfacePoint(centre, c.rng.range(5.0, 15.0), minNormalY = 0.0)
            perch = sp?.let { perchOn(it.first, it.second) }
            state = 1
            until = c.t + 8
            hopT = -1.0
            biteLeft = 0
        }
        /** The hop arc from here to [p] (as [update] flies it) stays out of the rock. */
        private fun hopClear(c: Ctx, p: Rest): Boolean {
            val from = c.o.pos
            val d = from.distanceTo(p.pos)
            val up = (c.o.up + p.up!!).normalized(Vec3.Y)
            for (k in 1 until HOP_PROBES) {
                val u = k.toDouble() / HOP_PROBES
                if (c.rock.sdf.dist(from.lerp(p.pos, u) + up * (4 * (0.3 * d + 1.0) * u * (1 - u))) < 0.0) return false
            }
            return true
        }

        override fun update(c: Ctx, out: SteerIntent) {
            val p = perch ?: run { pick(c); out.crawl = true; return }
            when (state) {
                1 -> {
                    val o = c.o
                    if (hopT < 0 && (o.pos.distanceTo(p.pos) > HOP_MAX_BL * o.lengthCm || !hopClear(c, p))) {
                        // too far for one hop (back from feeding), or a hop that would pass through the rock (round a
                        // spur or a ridge: the arc pressed onto the face, the body cut through behind it): skip along
                        // the rock on its pectorals
                        out.crawl = true
                        out.surfaceOffset = 0.5 * o.species.depthRatio * o.lengthCm + 0.3
                        out.seek(p.pos, o.burstSpeed * 0.4, arrive = 1.5, urgency = 0.5)
                        if (c.t > until) pick(c)
                        return
                    }
                    if (hopT < 0) {
                        hopT = 0.0; hopFrom = o.pos; hopUp = o.up
                        hopDur = (o.pos.distanceTo(p.pos) / (HOP_BL_S * o.lengthCm)).coerceIn(0.25, 1.2)
                    }
                    hopT += c.dt
                    val u = (hopT / hopDur).coerceIn(0.0, 1.0)
                    val d = hopFrom.distanceTo(p.pos)
                    val lift = (hopUp + p.up!!).normalized(Vec3.Y) * (4 * (0.3 * d + 1.0) * u * (1 - u))
                    // keep the arc clear of the rock in a concave gap (never through it)
                    var q = hopFrom.lerp(p.pos, u) + lift
                    val clear = 0.5 * o.species.depthRatio * o.lengthCm
                    if (c.rock.sdf.dist(q) < clear) q = c.rock.sdf.project(q, clear)
                    out.place(q, (p.pos - hopFrom).normalized(o.heading), null, 40.0)
                    // tail flick at take-off, fins folded in the air
                    out.ampMul = if (u < 0.3) 2.2 else 0.3; out.tailMul = if (u < 0.3) 1.6 else 0.5; out.pectoral = 0.2
                    if (u >= 1.0) { state = 0; hopT = -1.0; until = c.t + c.rng.range(8.0, 30.0); biteLeft = 0; nextRasp = c.t + c.rng.range(0.5, 2.0) }
                }
                else -> {
                    out.rest(p, 6.0)
                    if (c.t > nextRasp) {
                        if (biteLeft <= 0) {
                            // a new bite bout after the look round
                            biteLeft = BOUT_MIN + c.rng.nextInt(BOUT_MAX - BOUT_MIN + 1)
                        }
                        c.o.hunger = max(0.0, c.o.hunger - RASP_HUNGER * matrixQuality(c))
                        rasps++
                        c.o.nutritionGreens = 1.0
                        c.sim.addGrazing(c.sim.grazeRemovalPerMin(c.o) / 10)
                        c.o.mouth = 1.0
                        lastBiteAt = c.t
                        biteLeft--
                        if (biteLeft > 0) nextRasp = c.t + BITE_GAP_S * (0.8 + 0.4 * c.rng.nextDouble())
                        else { nextRasp = c.t + c.rng.range(SCAN_MIN_S, SCAN_MAX_S); scanYaw = c.rng.signed() * 0.45 }
                    }
                    // head dips to the rock for each rasp of a bout; between bouts it sits propped head-up, looking round
                    // with its head and the big swivelling eyes the species is known for
                    val side = (p.heading cross p.up!!).normalized(Vec3.Z)
                    val biting = biteLeft > 0
                    val pitch = if (biting) HEAD_UP * smoothstep(0.0, 0.3, c.t - lastBiteAt) - 0.15 else HEAD_UP * smoothstep(0.0, 0.5, c.t - lastBiteAt)
                    val yaw = if (biting) 0.0 else scanYaw * smoothstep(0.0, 0.8, c.t - lastBiteAt) * smoothstep(0.0, 0.6, nextRasp - c.t)
                    out.kinHeading = Vec3.rotateAbout(Vec3.rotateAbout(p.heading, p.up!!, yaw + 0.08 * sin(c.t * 1.7)), side, pitch)
                    out.kinUp = Vec3.rotateAbout(p.up!!, side, pitch)
                    if (!biting) c.o.eyeYaw = 0.4 * sin(c.t * 1.3 + c.o.appendagePhase)
                    out.pectoral = 1.0
                    if (c.t > until && biteLeft <= 0) pick(c)
                }
            }
        }
    }
}

/** Food that has settled or sinks low near a benthic fish (blennies, dottyback). */
class BenthicFeed(b: SpeciesBrain, r: Double) : Feed(b, r) {
    override fun accept(c: Ctx, f: FoodParticle) = f.settled || f.pos.y < c.o.pos.y + 15
}

/** One-blenny-per-tank rule (04 §15): chase the other blenny out of its territory. */
class BlennyRival(val b: SpeciesBrain, val radius: Double, val centre: Vec3) : Behavior("rival", "Chasing rival") {
    private var rival: Organism? = null
    override fun score(c: Ctx): Double {
        if (c.p.night || c.o.aggression < 0.15) return 0.0
        rival = c.p.neighbors(centre, radius + 10).firstOrNull {
            it !== c.o && it.alive && (it.brainKind == BrainKind.LAWNMOWER_BLENNY || it.brainKind == BrainKind.PEEKER_BLENNY) &&
                it.pos.distanceTo(centre) < radius && it.posture != 3
        }
        return if (rival != null) 0.55 + 0.3 * c.o.aggression else 0.0
    }
    override fun update(c: Ctx, out: SteerIntent) {
        val r = rival ?: run { finished = true; return }
        out.seek(r.pos, c.o.burstSpeed * 0.5, arrive = 1.0, urgency = 0.75)
        if (c.o.pos.distanceTo(r.pos) < 0.6 * c.L + 0.5 * r.lengthCm) {
            c.sim.agentOf(r)?.let { it.brain.onAttacked(it.ctx, c.o.pos, 0.45, c.o) }
            r.socialStress += 0.03
            finished = true
        }
        if (elapsed > 3) finished = true
    }
}

/**
 * How one Ecsenius species spends its day (research 30, 50). The tailspot, bicolor and midas share the hole life (tail
 * first in, head out, perch beside it, home at night) but split the daylight differently: the bicolor is a benthic
 * grazer that rarely leaves the rock, the midas a plankton picker that spends most of the day up in the water column.
 */
class EcseniusStyle(
    /** Day share of the hole's score (the rest goes to perching, grazing and excursions). */
    val inHoleDay: Double,
    /** Seconds between unprompted plankton darts, and how far it sees drifting food. */
    val dashMinS: Double, val dashMaxS: Double, val dashSeeCm: Double,
    /** How high above the hole a dart climbs (cm). */
    val dashRiseLo: Double, val dashRiseHi: Double,
    /** Weight of rock grazing. */
    val graze: Double,
    /** Weight of open-water foraging (0 = never; the midas only). */
    val midwater: Double = 0.0,
    /** Rasps a nori sheet on its clip. */
    val nori: Boolean = false,
) {
    companion object {
        /** Tailspot: peeks, darts up for plankton every minute or so, grazes a little (research 30, 10 §7). */
        val TAILSPOT = EcseniusStyle(0.38, 30.0, 90.0, 45.0, 10.0, 20.0, 0.4)
        /**
         * Bicolor: "found solitary in abandoned tubes" (FishBase), grazes film and hair algae round its hole and starves
         * without it (research 50 §1c-d); a water-column dart is rare (DESIGN ESTIMATE 0.03 per bout).
         */
        @com.reeflive.core.sim.Ev(com.reeflive.core.sim.Evidence.DESIGN_ESTIMATE, "50 §4a") val BICOLOR = EcseniusStyle(0.35, 180.0, 420.0, 25.0, 6.0, 12.0, 0.9, nori = true)
        /**
         * Midas: feeds on zooplankton 2-3 m above the benthos (FishBase), in and out of crevices, ~20 % of the day at its
         * hole (research 50 §2c, §4b DESIGN ESTIMATE); darts often and climbs high.
         */
        @com.reeflive.core.sim.Ev(com.reeflive.core.sim.Evidence.DESIGN_ESTIMATE, "50 §4b") val MIDAS = EcseniusStyle(0.2, 15.0, 45.0, 70.0, 15.0, 35.0, 0.25, midwater = 1.0)

        fun of(speciesId: String): EcseniusStyle = when (speciesId) {
            "ecsenius_bicolor" -> BICOLOR
            "ecsenius_midas" -> MIDAS
            else -> TAILSPOT
        }
    }
}

// =====================================================================================
// Ecsenius blennies (tailspot E. stigmatura, bicolor E. bicolor, midas E. midas): peek from a hole, perch,
// graze, dart up for plankton; the midas also forages in open water
// =====================================================================================
class PeekerBlennyBrain(c: Ctx) : SpeciesBrain(c) {
    /** This species' day budget (research 50). */
    val style: EcseniusStyle = EcseniusStyle.of(o.species.id)
    /** Its home: a modelled hole that fits its body, its own (HOLE_TAILSPOT) first (ASSETS §3.6); null = rock-face shelter. */
    private var hole0: RockHole? = c.rock.holes.claimHome(o, setOf(HoleKind.HOLE),
        (c.rock.anchors["HOLE_TAILSPOT"] ?: c.rock.anchors["HOLE_BLENNY_2"])?.pos, c.sim.holeClaims)
    /** Where its hole is now (it rides with its rock when the rocks shift, sim/RockPhysics.kt). */
    var hole: RockHole?
        get() = c.rock.holes.live(hole0)
        private set(v) { hole0 = v }
    private val shelterAt: Vec3 = c.rock.sdf.project(c.rock.anchors.pos("HOLE_TAILSPOT", Vec3(10.0, 13.0, 32.3)), 0.5)
    val holePos: Vec3 get() = hole?.mouth ?: shelterAt
    /** Outward direction at the home (the hole's axis, else the rock face's normal). */
    private val holeOut: Vec3 get() = hole?.axis ?: c.rock.sdf.gradient(shelterAt)
    private var peekDur = 40.0
    private var nextHoleCheck = 0.0
    val inHole = InHole(this, hole, holePos, "Peeking from its hole",
        // by day it spends about half its time head-out in the hole and the rest perched round it or darting out
        // (research 30: Ecsenius perch on rock by their holes; DESIGN ESTIMATE share); it was in the hole 95 % of the day
        { c -> if (c.p.night) 0.9 else (if (c.o.timidUntil > c.t) 0.75 else style.inHoleDay) + (if (c.o.health < 0.5) 0.3 else 0.0) },
        // head-out inspection by day; a full retreat (head in) while threatened, freshly startled or timid (research 10 §7)
        { c -> if (c.p.night || threat != null || c.o.timidUntil > c.t || c.t - startleAt < 20) -0.5 else 1.0 + 0.6 * sin(c.t * 0.2) })

    init {
        install(*listOfNotNull(
            inHole,
            PlanktonDash(), PerchNearHole(), Graze(this, style.graze), Prospect(), GlassCurious(this),
            Flee(this, { c, _ -> c.safe(holePos + holeOut * (c.clearance + 1.0)) }, "Darting home"), SurfaceGasp(this),
            if (style.midwater > 0) MidwaterForage() else null,
            if (style.nori) NibbleNori(this, 0.6, quiet = true) else null, HoleFight()).toTypedArray())
    }

    override fun restSpot(c: Ctx, night: Boolean): Rest = hole?.let { holeRest(c, it, if (night) -0.5 else 1.5) } ?: rockShelterRest(c, shelterAt)

    override fun anchorPos(c: Ctx, anchor: String): Vec3? = if (anchor == "hole") holePos else super.anchorPos(c, anchor)

    override fun shelterSpot(c: Ctx): Vec3? = if (hole != null) c.safe(holePos + holeOut * (c.clearance + 1.0)) else null

    /** A tap on the glass: further out of its hole to look (research 04 §2C "peeks from rock"). */
    override fun peekRest(c: Ctx): Rest? = hole?.let { holeRest(c, it, PEEK_HEAD_OUT_CM) }

    override fun initialPlace(c: Ctx) {
        super.initialPlace(c)
        if (hole != null) o.posture = 3
    }

    override fun preTick(c: Ctx) {
        super.preTick(c)
        if (c.t >= nextHoleCheck) {
            nextHoleCheck = c.t + 300
            leaveOutgrownHole(c); findSpareHole(c); reconsiderHole(c); yieldToRival(c); pickHoleToFight(c)
        }
        takeWonHole(c)
        // no hole of its own: a blenny out on the open rock face is chronically on edge (it has nowhere to retreat to)
        if (hole == null && o.socialStress < HOMELESS_STRESS) o.socialStress = HOMELESS_STRESS
    }

    /** A hole held by a blenny it could beat, to fight for (null = none worth trying); refreshed with the hole checks. */
    private var fightFor: RockHole? = null
    private var nextFight = 0.0
    /** A hole it won, moved into once the loser has backed out of it (two bodies are never in one hole). */
    private var wonHole: RockHole? = null
    /** Hole fights started and won (tests, captions). */
    var holeFights = 0; private set
    var holeWins = 0; private set

    /**
     * Homeless (every fitting hole held): it picks the nearest fitting hole held by another Ecsenius no more than a little
     * bigger than itself, to fight for. Blennies compete for holes and bigger ones win them (Wilson, Fisher & Pratchett
     * 2013; research 50 §1c, §2c); the lawnmower's own hole is left alone (it is the bigger, more aggressive fish).
     */
    private fun pickHoleToFight(c: Ctx) {
        fightFor = null
        if (hole != null || wonHole != null || c.t < nextFight) return
        val claims = c.sim.holeClaims
        var best: RockHole? = null
        var bestD = Double.MAX_VALUE
        for (h in c.rock.holes.all) {
            if (h.kind != HoleKind.HOLE || !h.fits(o) || h.depth < HoleSet.MIN_DEPTH_CM) continue
            val holder = claims.holder(h) ?: continue
            if (holder === o || holder.brainKind != BrainKind.PEEKER_BLENNY || holder.lengthCm > FIGHT_SIZE_RATIO * o.lengthCm) continue
            val d = h.mouth.distanceTo(o.pos)
            if (d < bestD) { bestD = d; best = h }
        }
        fightFor = best
    }

    /** Moves into a hole it won once the loser's body is out of it. */
    private fun takeWonHole(c: Ctx) {
        val h = c.rock.holes.live(wonHole) ?: run { wonHole = null; return }
        val holder = c.sim.holeClaims.holder(h)
        if (holder != null && holder !== o) { wonHole = null; return }   // someone else got there first
        for (n in c.p.neighbors(h.mouth, h.depth + 2.0)) if (n !== o && n.alive && h.contains(n.pos, 0.3)) return
        wonHole = null
        moveTo(c, h)
    }

    /** It lost its hole in a fight: it backs out and shelters on the rock face, hiding for a while (timidUntil). */
    fun evicted(c: Ctx) {
        hole?.let { c.sim.holeClaims.release(it, o) }
        hole = null
        inHole.hole = null; inHole.shelter = shelterAt
        o.homePos = shelterAt
        o.timidUntil = c.t + 600
        nextFight = c.t + c.rng.range(FIGHT_COOLDOWN_S, 2 * FIGHT_COOLDOWN_S)
    }

    /**
     * The fight for a hole: it swims to the mouth, raises its dorsal and charges the holder; the holder answers from the
     * hole. The bigger fish usually wins and the resident has the edge (prior residence; [RESIDENT_EDGE] is a design
     * estimate). The loser hides; the winner moves in once the hole is empty.
     */
    inner class HoleFight : Behavior("holefight", "Fighting for a hole") {
        private var target: RockHole? = null
        override val committed: Boolean get() = !finished && elapsed < 25
        /** No score noise (and no random draw) while it has nothing to fight for. */
        override val noisy: Boolean get() = fightFor != null
        override fun score(c: Ctx): Double {
            if (c.p.night || threat != null || fightFor == null || hole != null || c.t < nextFight || c.o.timidUntil > c.t) return 0.0
            return 0.5 + 0.3 * c.o.aggression
        }
        override fun enter(c: Ctx) { target = fightFor; holeFights++ }
        override fun update(c: Ctx, out: SteerIntent) {
            val h = c.rock.holes.live(target) ?: run { finished = true; return }
            val holder = c.sim.holeClaims.holder(h)
            if (holder == null || holder === c.o) { fightFor = null; finished = true; return }
            val stand = c.safe(h.mouth + h.axis * (c.clearance + 1.5), c.clearance * 0.8)
            out.seek(c.viaOpenWater(stand), c.o.burstSpeed * 0.5, arrive = 1.5, urgency = 0.7)
            out.faceDir = h.axis * -1.0
            if (c.o.pos.distanceTo(stand) < 3.0 || elapsed > 20) {
                val hb = c.sim.agentOf(holder)?.brain as? PeekerBlennyBrain
                hb?.onAttacked(c.sim.agentOf(holder)!!.ctx, c.o.pos, 0.4, c.o)
                val me = c.o.lengthCm
                val them = holder.lengthCm * RESIDENT_EDGE
                if (hb != null && c.rng.chance(me / (me + them))) {
                    hb.evicted(c.sim.agentOf(holder)!!.ctx)
                    wonHole = h
                    holeWins++
                } else {
                    // lost: it backs off, stressed, and hides before it tries again
                    c.o.socialStress += 0.05
                    c.o.timidUntil = c.t + 600
                }
                holder.socialStress += 0.03
                fightFor = null
                nextFight = c.t + c.rng.range(FIGHT_COOLDOWN_S, 2 * FIGHT_COOLDOWN_S)
                finished = true
            }
        }
    }

    /**
     * The blenny war (06 §2.7, §5.6 #9): a tailspot harassed by a lawnmower whose hole is within 50 cm
     * moves to the hole farthest from it (an established resident has already done so).
     */
    fun reconsiderHole(c: Ctx) {
        val lmb = c.sim.organisms.firstOrNull { it.alive && it.brainKind == BrainKind.LAWNMOWER_BLENNY } ?: return
        val lh = c.sim.agentOf(lmb)?.brain?.anchorPos(c, "hole") ?: return
        if (holePos.distanceTo(lh) >= MOVE_AWAY_CM) return
        val r = o.relations[lmb.id]
        val harassed = (r?.harassedBy ?: 0.0) > 0.2 || (r?.fear ?: 0.0) > 0.3
        val settled = c.t - o.arrivalTime > 3 * 86400
        if (!harassed && !settled) return
        // only real holes that fit the blenny's body (measured on the built rock), so it never sits inside rock
        val claims = c.sim.holeClaims
        val best = c.rock.holes.all.filter { it.kind == HoleKind.HOLE && it.fits(o) && it.mouth.distanceTo(lh) >= MOVE_AWAY_CM && claims.usableBy(it, o) }
            .maxByOrNull { it.mouth.distanceTo(lh) } ?: return
        if (best === hole) return
        moveTo(c, best)
    }

    /**
     * Ecsenius squabbles (research 50 §1c, §2c: bicolor and midas fight other blennies over holes): an Ecsenius chased off
     * by another one whose hole is within [MOVE_AWAY_CM] moves to the free fitting hole farthest from it. Only the
     * harassed one moves, so the bully keeps its home; with no such hole it stays and keeps hiding (timidUntil).
     */
    fun yieldToRival(c: Ctx) {
        val claims = c.sim.holeClaims
        for (r in c.sim.organisms) {
            if (r === o || !r.alive || r.brainKind != BrainKind.PEEKER_BLENNY) continue
            val rel = o.relations[r.id] ?: continue
            if (rel.harassedBy <= 0.2 && rel.fear <= 0.3) continue
            val rh = (c.sim.agentOf(r)?.brain as? PeekerBlennyBrain)?.holePos ?: continue
            if (holePos.distanceTo(rh) >= MOVE_AWAY_CM) continue
            val best = c.rock.holes.all.filter { it.kind == HoleKind.HOLE && it.fits(o) && it.mouth.distanceTo(rh) >= MOVE_AWAY_CM && claims.usableBy(it, o) }
                .maxByOrNull { it.mouth.distanceTo(rh) } ?: return
            if (best !== hole) moveTo(c, best)
            return
        }
    }

    /**
     * A blenny that has grown too thick for its hole moves to a bigger one (hole diameter rises with blenny length,
     * Wilson, Fisher & Pratchett 2013); with none free it gives the hole up and shelters on the rock face, so it never
     * backs into a bore narrower than its body.
     */
    private fun leaveOutgrownHole(c: Ctx) {
        val h = hole ?: return
        if (h.fits(o)) return
        val bigger = c.rock.holes.homeFor(o, setOf(HoleKind.HOLE), holePos, claims = c.sim.holeClaims)
        if (bigger != null) { moveTo(c, bigger); return }
        c.sim.holeClaims.release(h, o)
        hole = null
        inHole.hole = null; inHole.shelter = shelterAt
        o.homePos = shelterAt
    }

    /** Move house: claim [h] first, then let the old hole go (it is never shared, not even for a tick). */
    private fun moveTo(c: Ctx, h: RockHole) {
        if (!c.sim.holeClaims.claim(h, o)) return
        hole?.let { c.sim.holeClaims.release(it, o) }
        hole = h
        inHole.hole = h; inHole.shelter = h.mouth
        o.homePos = h.mouth
    }

    /**
     * A blenny without a hole (every fitting one held when it arrived) keeps looking: it takes the first spare hole that
     * fits. A held hole is contested through the blenny war (pair row lmb_tailspot), whose loser moves out, and by
     * [HoleFight] against another Ecsenius.
     */
    private fun findSpareHole(c: Ctx) {
        if (hole != null) return
        c.rock.holes.homeFor(o, setOf(HoleKind.HOLE), shelterAt, claims = c.sim.holeClaims)?.let { moveTo(c, it) }
    }

    /** Short excursions from the hole and how many ended back at it (research 10 §7: inspect → excursion → return). */
    var excursions = 0; private set
    var returns = 0; private set

    companion object {
        /** DESIGN ESTIMATE (research 10 §7: "does not establish that the tailspot must move exactly 50 cm away"). */
        @com.reeflive.core.sim.Ev(com.reeflive.core.sim.Evidence.DESIGN_ESTIMATE, "10 §7") const val MOVE_AWAY_CM = 50.0
        /** A newly added tailspot prospects holes and grazing patches tank-wide for its first day [I]. */
        const val PROSPECT_S = 86400.0
        /** Longest committed trip to a prospecting site (s) [L]. */
        const val PROSPECT_TRIP_S = 25.0
        /** How far above its hole a midas holds station, and how far from home it forages (cm; 2-3 m on the reef scaled to the tank) [L]. */
        const val MIDWATER_RISE_LO = 15.0
        const val MIDWATER_RISE_HI = 35.0
        const val MIDWATER_RANGE_CM = 50.0
        /** Social stress floor while it has no hole (out on the rock face) [L]. */
        @com.reeflive.core.sim.Ev(com.reeflive.core.sim.Evidence.DESIGN_ESTIMATE, "50; owner direction 2026-10-20") const val HOMELESS_STRESS = 0.12
        /** It only fights holders up to this much longer than itself [L]. */
        const val FIGHT_SIZE_RATIO = 1.15
        /** The resident's edge in a hole fight, as a length multiplier (prior-residence advantage) [L]. */
        const val RESIDENT_EDGE = 1.3
        /** Shortest wait between hole fights (s) [L]. */
        const val FIGHT_COOLDOWN_S = 1200.0
        /** Open water round a foraging station: this far from any rock (cm) [L]. */
        const val MIDWATER_CLEAR_CM = 5.0
    }

    /**
     * Newcomer prospecting (06 §3.3, §2.7, §5.6 #9): for its first day a new tailspot has no settled
     * range and samples every hole and grazing patch, the lawnmower's included; the lawnmower's chases
     * (and the 10 min hide that follows each) are what teach it to keep to the far hole. [I]
     */
    inner class Prospect : Behavior("prospect", "Exploring its new tank") {
        private var site = Vec3.ZERO
        private var stand = Vec3.ZERO
        private var stay = 30.0
        private var arrived = -1.0
        /**
         * The trip out to a site is one inspection: only an urgent behaviour (flee) breaks it off on the way, not a
         * plankton snap (research 10 §7 inspect → excursion → return) [I].
         */
        override val committed: Boolean get() = !finished && arrived < 0 && elapsed < PROSPECT_TRIP_S
        override fun score(c: Ctx): Double {
            if (c.p.night || c.t - c.o.arrivalTime > PROSPECT_S) return 0.0
            return 0.55
        }
        override fun enter(c: Ctx) {
            val sites = (c.rock.holes.fitting(RockHole.bodyRadius(c.o), setOf(HoleKind.HOLE)).map { it.mouth } +
                c.rock.anchors.ofType("GRAZE").map { it.pos }).filter { it.distanceTo(holePos) > 15 }
            if (sites.isEmpty()) { finished = true; return }
            site = sites[c.rng.nextInt(sites.size)]
            val sp = c.rockSurfacePoint(site, 6.0)
            stand = sp?.let { (p, n) -> c.safe(p + n * (c.clearance + 0.5), c.clearance * 0.8) } ?: c.safe(site, c.clearance)
            stay = c.rng.range(20.0, 60.0)
            arrived = -1.0
        }
        override fun update(c: Ctx, out: SteerIntent) {
            out.seek(stand, c.o.cruiseSpeed * 1.2, arrive = 2.0)
            if (arrived < 0 && c.o.pos.distanceTo(stand) < 3.0) arrived = c.t
            if (arrived >= 0) { out.hover = true; out.pectoral = 1.0 }
            if (arrived >= 0 && c.t - arrived > stay || elapsed > stay + 40) finished = true
        }
    }

    /** Dart up into the water column to snap plankton or a drifting particle, then back. */
    inner class PlanktonDash : Behavior("dash", "Darting for plankton") {
        private var target = Vec3.ZERO
        private var f: FoodParticle? = null
        private var next = 0.0
        private var back = false
        /**
         * Out and back is one excursion (research 10 §7): only an urgent behaviour (flee) interrupts it. Before this the
         * dash lost to the hole the tick after it began, because its own score drops to 0 once it has started.
         */
        override val committed: Boolean get() = !finished && elapsed < 16
        override fun score(c: Ctx): Double {
            if (c.p.night) return 0.0
            f = c.findFood(style.dashSeeCm) { !it.settled }
            if (f != null && c.o.hunger > 0.12) return 0.8
            return if (c.t > next && c.o.hunger > 0.2) 0.5 + 0.35 * c.o.hunger else 0.0
        }
        override fun enter(c: Ctx) {
            back = false
            excursions++
            next = c.t + c.rng.range(style.dashMinS, style.dashMaxS)
            target = f?.pos ?: c.safe(holePos + holeOut * 6.0 + Vec3(c.rng.signed() * 5, c.rng.range(style.dashRiseLo, style.dashRiseHi), 0.0))
        }
        override fun update(c: Ctx, out: SteerIntent) {
            val o = c.o
            val ff = f
            if (!back) {
                val tp = if (ff != null && !ff.eaten) ff.pos else target
                out.seek(tp, o.burstSpeed * 0.7, arrive = 0.8, urgency = 0.8)
                if (o.pos.distanceTo(tp) < 1.2 + 0.1 * o.lengthCm) {
                    if (ff != null && !ff.eaten) c.bite(ff) else { o.hunger = max(0.0, o.hunger - 0.01); o.mouth = 1.0 }
                    back = true
                }
                if (elapsed > 4) back = true
            } else {
                // swim back to the hole mouth; the InHole behaviour then slides it in head-out
                val mouth = c.safe(holePos + holeOut * (c.clearance + 1.0), c.clearance * 0.8)
                out.seek(mouth, o.burstSpeed * 0.5, arrive = 1.5, urgency = 0.6)
                if (o.pos.distanceTo(mouth) < 2.5) { returns++; finished = true }
                else if (elapsed > 12) finished = true
            }
        }
    }

    /** Open-water bouts so far (tests, captions). */
    var midwaterBouts = 0; private set

    /**
     * The midas blenny's open-water foraging (research 50 §2c: zooplankton 2-3 m above the benthos; the orange phase
     * schools with lyretail anthias, FishBase). It climbs to a station well above its hole, holds there sculling into the
     * current and snaps passing plankton, and keeps station with a nearby mid-water fish (a chromis or anthias school, a
     * cardinal) when one is there. It never strays past [MIDWATER_RANGE_CM] from home and drops home to its hole when
     * startled (Flee outranks it).
     */
    inner class MidwaterForage : Behavior("midwater", "Picking plankton in open water") {
        private var base = Vec3.ZERO
        private var station = Vec3.ZERO
        private var nextDrift = 0.0
        private var dur = 60.0
        private var nextSnap = 0.0
        private var restUntil = -1e9
        private var buddy: Organism? = null
        private var nextBuddy = 0.0
        override fun score(c: Ctx): Double {
            if (c.p.night || threat != null || c.t < restUntil || c.o.timidUntil > c.t) return 0.0
            // outranks perching and grazing (it lives up in the water by day), not a food rush or a flight
            return (0.7 + 0.2 * c.o.hunger) * c.activity * style.midwater
        }
        override fun enter(c: Ctx) {
            midwaterBouts++
            dur = c.rng.range(40.0, 150.0)
            val up = c.rng.range(MIDWATER_RISE_LO, MIDWATER_RISE_HI)
            val y = min(holePos.y + up, c.zoneY(c.o.species.zone.yMax))
            // a station in open water out in front of the hole, clear of the rock (a point inside a rock arch would be
            // projected down onto the rock face, and it would "forage" sitting on the rock)
            station = c.safe(holePos + holeOut * 8.0 + Vec3(0.0, 0.5 * up, 0.0))
            for (i in 0 until 6) {
                val p = Vec3(holePos.x + c.rng.signed() * 15, y, (holePos.z + holeOut.z * 10 + c.rng.signed() * 6).coerceIn(6.0, c.rock.widthCm - 6))
                if (c.rock.sdf.dist(p) > MIDWATER_CLEAR_CM) { station = c.safe(p); break }
            }
            base = station; nextDrift = c.t + c.rng.range(2.0, 6.0)
            buddy = null; nextBuddy = 0.0
        }
        override fun exit(c: Ctx) { restUntil = c.t + c.rng.range(10.0, 40.0) }
        override fun update(c: Ctx, out: SteerIntent) {
            val o = c.o
            // every few seconds: the nearest mid-water fish of similar size to keep station beside (a school mate; not a
            // sand dweller, nor a clown that would defend its host)
            if (c.t >= nextBuddy) {
                nextBuddy = c.t + 4.0
                buddy = c.p.neighbors(o.pos, 30.0).firstOrNull {
                    it !== o && it.alive && it.isFish && it.brainKind != BrainKind.PEEKER_BLENNY && it.brainKind != BrainKind.LAWNMOWER_BLENNY &&
                        it.brainKind != BrainKind.CLOWN && it.species.zone.openWater >= 0.3 && it.species.zone.yMin >= 0.15 &&
                    it.pos.y > holePos.y && it.lengthCm < 2.5 * o.lengthCm
                }
            }
            // it never hangs still: it shifts about its station every few seconds, as a plankton picker works the water
            if (c.t >= nextDrift) {
                nextDrift = c.t + c.rng.range(2.0, 6.0)
                val p = base + Vec3(c.rng.signed() * 8, c.rng.signed() * 4, c.rng.signed() * 5)
                if (c.rock.sdf.dist(p) > MIDWATER_CLEAR_CM) station = c.safe(p)
            }
            val b = buddy
            val goal = if (b != null && b.alive && b.pos.distanceTo(holePos) < MIDWATER_RANGE_CM) c.safe(b.pos + Vec3(0.0, -2.0, 0.0) - b.heading * 3.0) else station
            out.seek(goal, o.cruiseSpeed * (if (b != null) 1.1 else 0.7), arrive = 3.0)
            if (o.pos.distanceTo(goal) < 4.0) { out.hover = true; out.pectoral = 1.0 }
            // snap at drifting particles in reach, else pick unseen plankton now and then
            if (c.t >= nextSnap) {
                nextSnap = c.t + c.rng.range(2.5, 7.0)
                val f = c.findFood(8.0) { !it.settled }
                if (f != null) c.bite(f) else if (o.hunger > 0.15) { o.hunger = max(0.0, o.hunger - 0.004); o.mouth = 1.0 }
            }
            if (elapsed > dur || o.pos.distanceTo(holePos) > MIDWATER_RANGE_CM) finished = true
        }
    }

    /** Rest on the rock beside the hole with the tail curled (posture 1). */
    inner class PerchNearHole : Behavior("perchhole", "Perched by its hole") {
        private var rest: Rest? = null
        private var stand = Vec3.ZERO
        private var settled = false
        private var dur = 20.0
        private var lookYaw = 0.0
        private var nextLook = 0.0
        private var restUntil = -1e9
        override fun score(c: Ctx) = if (c.t < restUntil || threat != null) 0.0 else 0.42 * c.activity
        override fun exit(c: Ctx) { restUntil = c.t + c.rng.range(10.0, 40.0) }
        override fun enter(c: Ctx) {
            settled = false
            dur = c.rng.range(15.0, 60.0)
            val sp = c.rockSurfacePoint(holePos, c.rng.range(4.0, 12.0), minNormalY = 0.1)
            // lying along the rock with the head level-ish (on a steep face "up the face" pointed it straight up, and the
            // swim that followed snapped the pitch back in one tick: a > 800°/s turn)
            rest = sp?.let { (p, n) ->
                val along = (n cross Vec3.Y).let { if (it.length() < 0.2) Vec3.X.rejectFrom(n) else it }.normalized(Vec3.X)
                val away = if ((along dot (p - holePos)) < 0) along * -1.0 else along
                val h = (away + Vec3.Y.rejectFrom(n).normalized(Vec3.Y) * 0.35).rejectFrom(n).normalized(away)
                Rest(p + n * (0.5 * c.o.species.depthRatio * c.L + 0.3), h, n, posture = 1)
            }
            stand = rest?.let { c.safe(it.pos + it.up!! * c.clearance, c.clearance * 0.8) } ?: Vec3.ZERO
        }
        override fun update(c: Ctx, out: SteerIntent) {
            val r = rest ?: run { finished = true; return }
            if (!settled) {
                // a calm swim to the spot (a fast seek to a stand a few cm off the hole spun it round at > 800°/s)
                out.seek(stand, c.o.cruiseSpeed * 0.8, arrive = 3.0)
                // close by it settles kinematically: a swim seek crawling the last cm let the heading spin on the spot
                if (c.o.pos.distanceTo(stand) < 4.0) settled = true
                if (elapsed > 8) finished = true
                return
            }
            // perched, it looks round in quick turns of the head and bobs it now and then (DESIGN ESTIMATE)
            if (c.t > nextLook) { nextLook = c.t + c.rng.range(1.2, 4.0); lookYaw = c.rng.signed() * 0.45 }
            val up = r.up ?: Vec3.Y
            val side = (r.heading cross up).normalized(Vec3.Z)
            val bob = 0.12 * max(0.0, sin(c.t * 2.2 + c.o.appendagePhase)).let { it * it * it }
            out.place(r.pos, Vec3.rotateAbout(Vec3.rotateAbout(r.heading, up, lookYaw), side, 0.2 + bob), up, 6.0)
            out.posture = r.posture
            out.pectoral = 1.0
            c.o.eyeYaw = lookYaw * 0.6
            if (elapsed > dur) finished = true
        }
    }
}

// =====================================================================================
// Indigo dottyback (Pseudochromis): secretive cave darter, territorial
// =====================================================================================
class DottybackBrain(c: Ctx) : SpeciesBrain(c) {
    /** Its cave: the modelled CAVE_DOTTYBACK when it fits the body (ASSETS §3.6); null = it shelters on the rock face. */
    private val home0: RockHole? = c.rock.holes.claimHome(o, setOf(HoleKind.CAVE), c.rock.anchors["CAVE_DOTTYBACK"]?.pos, c.sim.holeClaims)
        ?.takeIf { it.depth < 20.0 }
    /** Where its cave is now (it rides with its rock when the rocks shift, sim/RockPhysics.kt). */
    val home: RockHole? get() = c.rock.holes.live(home0)
    private val caveFace: Vec3 by lazy { c.rock.sdf.project(c.rock.anchors.pos("CAVE_DOTTYBACK", c.rock.anchors.pos("CAVE_BIG", Vec3(106.0, 7.5, 13.0))), 0.0) }
    val cave: Vec3 get() = home?.mouth ?: caveFace
    private var safeFor: com.reeflive.core.sim.HoleSet? = null
    private var safeAt = Vec3.ZERO
    /** Just outside the mouth (where it pauses and where a fright sends it before it backs in); redone if its rock moved. */
    val caveSafe: Vec3 get() {
        if (safeFor !== c.rock.holes) {
            val cv = cave
            safeAt = c.safe(home?.along(-3.0) ?: (cv + c.rock.sdf.gradient(cv) * 3.0)); safeFor = c.rock.holes
        }
        return safeAt
    }

    init {
        // a learned crevice route out of the cave (ai/DottyPatrol.kt; research 10 §8) in place of random darts
        install(CavePatrol(this, { cave }, { caveSafe }), DottyFeed(),
            Flee(this, { _, _ -> caveSafe }, "Darting into its cave"),
            // night, sickness or stress: in its cave, head just inside the mouth (was a Sleep hover outside it)
            InHole(this, home, cave, "Resting in its cave", { c ->
                val o = c.o
                val sick = if (o.health < 0.6 || o.disease != null && o.diseaseStage > 0.3) 1.0 else 0.0
                (0.7 * (1 - c.activity) + 0.25 * o.stress + 0.3 * sick).coerceAtMost(0.9)
            }, { c -> if (c.p.night) -0.6 else 0.4 }),
            VisitCleaner(this), SurfaceGasp(this), Flash(this))
    }

    override fun restSpot(c: Ctx, night: Boolean): Rest = home?.let { holeRest(c, it, if (night) -0.6 else 0.4) } ?: Rest(caveSafe, Vec3.Z)

    override fun anchorPos(c: Ctx, anchor: String): Vec3? = if (anchor == "cave") cave else super.anchorPos(c, anchor)

    override fun shelterSpot(c: Ctx): Vec3? = if (home != null) caveSafe else null

    inner class DottyFeed : Feed(this@DottybackBrain, 50.0) {
        override fun bonus(c: Ctx, f: FoodParticle) = 0.1
    }

    /** Short rock-hugging darts between crevices near the cave, pausing in cover. */
    inner class CaveDart : Behavior("cavedart", "Darting between crevices") {
        private var wp = Vec3.ZERO
        private var pauseUntil = 0.0
        private var moving = false
        override fun score(c: Ctx) = 0.2 + 0.35 * c.activity
        override fun update(c: Ctx, out: SteerIntent) {
            val o = c.o
            if (!moving && c.t > pauseUntil) {
                val sp = c.rockSurfacePoint(cave + c.rng.unitVector() * 12.0, 14.0)
                wp = sp?.let { c.safe(it.first + it.second * (c.clearance + 0.3)) } ?: caveSafe
                moving = true
            }
            if (moving) {
                out.seek(wp, o.burstSpeed * 0.45, arrive = 1.2, urgency = 0.6)
                if (o.pos.distanceTo(wp) < 1.5) { moving = false; pauseUntil = c.t + c.rng.range(2.0, 8.0) }
            } else {
                // pause in the crevice mouth facing out, watching, before the next dart
                out.target = wp; out.speed = o.cruiseSpeed * 0.2; out.hover = true; out.ampMul = 0.4; out.pectoral = 1.0
                out.faceDir = (wp - cave).horizontal().normalized(o.heading)
            }
        }
    }

    /** Moderate aggression to blennies, low to the cardinal, none to shrimp (04 §13). */
    inner class Territorial : Behavior("territory", "Guarding territory") {
        private var who: Organism? = null
        override fun score(c: Ctx): Double {
            if (c.p.night) return 0.0
            who = c.p.neighbors(cave, 18.0).firstOrNull { n ->
                n.alive && n !== c.o && n.posture != 3 && when (n.brainKind) {
                    BrainKind.LAWNMOWER_BLENNY, BrainKind.PEEKER_BLENNY -> n.pos.distanceTo(cave) < 15
                    BrainKind.CARDINAL -> n.pos.distanceTo(cave) < 6
                    BrainKind.DOTTYBACK -> true
                    // striped indigo hybrid: semi-aggressive, briefly chases smaller fish near its cave
                    else -> n.isFish && n.lengthCm < c.L && n.pos.distanceTo(cave) < 10
                }
            }
            return if (who != null) 0.5 + 0.4 * c.o.aggression else 0.0
        }
        override fun update(c: Ctx, out: SteerIntent) {
            val w = who ?: run { finished = true; return }
            out.seek(w.pos, c.o.burstSpeed * 0.55, arrive = 1.0, urgency = 0.75)
            if (c.o.pos.distanceTo(w.pos) < 0.6 * c.L + 0.5 * w.lengthCm) {
                c.sim.agentOf(w)?.let { it.brain.onAttacked(it.ctx, c.o.pos, 0.4, c.o) }
                w.socialStress += 0.03
                finished = true
            }
            if (elapsed > 2.5) finished = true
        }
    }
}

// =====================================================================================
// Generic fish (any sheet without a dedicated brain)
// =====================================================================================
class GenericFishBrain(c: Ctx) : SpeciesBrain(c) {
    init {
        install(HoverHome(this, { it.o.homePos }, { it.o.species.homeRadiusCm.coerceAtMost(30.0) }), Wander(this), Feed(this),
            Graze(this), NibbleNori(this), Flee(this, { c, from -> c.coverPoint(from) }), Sleep(this), VisitCleaner(this),
            GlassCurious(this), SurfaceGasp(this), Flash(this))
    }

    override fun restSpot(c: Ctx, night: Boolean): Rest {
        val home = if (o.homePos == Vec3.ZERO) c.safe(c.inTank(Vec3(60.0, c.zoneY((o.species.zone.yMin + o.species.zone.yMax) / 2), 20.0))) else o.homePos
        return Rest(if (night) c.safe(home.withY(c.zoneY(0.2))) else home, Vec3.X)
    }

    override fun initialPlace(c: Ctx) {
        val z = o.species.zone
        o.homePos = c.safe(Vec3(c.rng.range(20.0, 100.0), c.zoneY((z.yMin + z.yMax) / 2), c.rock.widthCm * c.rng.range(0.36, 0.79)))
        o.placeAt(o.homePos, Vec3.X)
    }
}

@Suppress("unused")
private fun absD(v: Double) = abs(v)

/** Standard normal quantile Φ⁻¹(p), 0 < p < 1 (Acklam's rational approximation, relative error < 1.2e-9). */
internal fun normalQuantile(p: Double): Double {
    val a = doubleArrayOf(-3.969683028665376e1, 2.209460984245205e2, -2.759285104469687e2, 1.383577518672690e2, -3.066479806614716e1, 2.506628277459239)
    val b = doubleArrayOf(-5.447609879822406e1, 1.615858368580409e2, -1.556989798598866e2, 6.680131188771972e1, -1.328068155288572e1)
    val cc = doubleArrayOf(-7.784894002430293e-3, -3.223964580411365e-1, -2.400758277161838, -2.549732539343734, 4.374664141464968, 2.938163982698783)
    val d = doubleArrayOf(7.784695709041462e-3, 3.224671290700398e-1, 2.445134137142996, 3.754408661907416)
    fun tail(q: Double) = (((((cc[0] * q + cc[1]) * q + cc[2]) * q + cc[3]) * q + cc[4]) * q + cc[5]) / ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1)
    return when {
        p < 0.02425 -> tail(sqrt(-2 * ln(p)))
        p > 1 - 0.02425 -> -tail(sqrt(-2 * ln(1 - p)))
        else -> {
            val q = p - 0.5
            val r = q * q
            (((((a[0] * r + a[1]) * r + a[2]) * r + a[3]) * r + a[4]) * r + a[5]) * q / (((((b[0] * r + b[1]) * r + b[2]) * r + b[3]) * r + b[4]) * r + 1)
        }
    }
}
