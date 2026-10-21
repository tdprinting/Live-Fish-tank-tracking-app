package com.reeflive.core.ai

import com.reeflive.core.geom.Vec3
import com.reeflive.core.sim.BrainKind
import com.reeflive.core.sim.Counters
import com.reeflive.core.sim.Ev
import com.reeflive.core.sim.Evidence
import com.reeflive.core.sim.HealthModel
import com.reeflive.core.sim.HoleKind
import com.reeflive.core.sim.Organism
import com.reeflive.core.sim.RockHole
import kotlin.math.max

/**
 * Six line wrasse, *Pseudocheilinus hexataenia* (docs/research/51_popular_fish_batch2.md §2). A small, fast, slinky
 * wrasse that never stops: it darts from crack to crack through the rockwork, pausing for a peck or two at each, hunting
 * amphipods, copepods, worms and other tiny animals (the picks are fed by the tank's microfauna, like the leopard
 * wrasse's). It is the tank's busybody: fish added after it, other wrasses and small peaceful fish near its patch get
 * chased and nipped (Reef Builders 2014; Fishtankadvisor). At night it slips into a hole in the rock and wraps itself
 * in a mucus cocoon (Reef Builders 2014), which it leaves behind in the morning.
 */
class SixlineBrain(c: Ctx) : SpeciesBrain(c) {
    private var home0: RockHole? = c.rock.holes.claimHome(o, KINDS, defaultNear(c), c.sim.holeClaims)
    /** Its sleeping hole now (rides with its rock when the rocks shift). */
    val home: RockHole? get() = c.rock.holes.live(home0)
    private val shelterAt: Vec3 = c.rock.sdf.project(home0?.mouth ?: defaultNear(c), 0.5)
    val den: Vec3 get() = home?.mouth ?: shelterAt
    private var nextHoleCheck = 0.0

    /** Cocooned for the night (captions, tests); set once it is in its hole after dark. */
    var cocooned = false; private set
    var cocoons = 0; private set
    var picks = 0; private set
    var chases = 0; private set

    init {
        o.spline.glassGuard = true
        install(Dart(), Harass(), SixlineFeed(),
            Flee(this, { cc, from -> if (cc.o.pos.distanceTo(den) < 30.0) denMouth(cc) else cc.coverPoint(from) }, "Darting into the rock"),
            CocoonSleep(), VisitCleaner(this), SurfaceGasp(this), Flash(this))
    }

    override fun restSpot(c: Ctx, night: Boolean): Rest =
        home?.let { holeRest(c, it, if (night) -0.8 else 0.3) } ?: rockShelterRest(c, den)

    override fun shelterSpot(c: Ctx): Vec3? = denMouth(c)

    override fun peekRest(c: Ctx): Rest? = home?.let { holeRest(c, it, 1.0) }

    override fun preTick(c: Ctx) {
        super.preTick(c)
        if (c.t >= nextHoleCheck) { nextHoleCheck = c.t + 300; if (home == null) c.rock.holes.claimHome(o, KINDS, den, c.sim.holeClaims)?.let { home0 = it } }
    }

    private fun denMouth(c: Ctx): Vec3 = c.safe(den + (home?.axis ?: Vec3.Y) * (c.clearance + 0.8))

    private fun eatPick(c: Ctx) {
        val prey = LeopardWrasseBrain.preyIndex(c.sim)
        val y = HealthModel.hungerRatePerMin(o, c.sim.water.s.tempC) * PICK_YIELD_MIN * prey
        if (y > 0) {
            o.hunger = max(0.0, o.hunger - y)
            if (prey > 0.2) o.counters.add(Counters.K.FEED, c.t)
        }
    }

    /** Daylight in abstract mode (> 240×): picking as a rate on the rock's microfauna. */
    fun abstractPick(dtMin: Double) {
        if (!o.visible) return
        val prey = LeopardWrasseBrain.preyIndex(c.sim)
        o.hunger = max(0.0, o.hunger - HealthModel.hungerRatePerMin(o, c.sim.water.s.tempC) * ABSTRACT_RELIEF * prey * dtMin)
        picks += (12 * dtMin).toInt()
    }

    /** Settled in: it only starts pushing others about once it has been in the tank a while itself. */
    private fun settled(c: Ctx) = c.t - o.arrivalTime > SETTLE_S

    // ================================================================ behaviours

    /**
     * The hunt (research 51 §2 facts 62-63, 71): quick darts of a few to fifteen centimetres between spots on the rock
     * near its patch, a peck or two at each, sometimes a poke into a crack; it seldom stops for more than a few seconds.
     */
    inner class Dart : Behavior("dart", "Darting through the rock") {
        private var spot = Vec3.ZERO
        private var stand = Vec3.ZERO
        private var face = Vec3.X
        private var nextPick = 0.0
        private var left = 0
        private var bestD = Double.MAX_VALUE
        private var progressAt = 0.0
        override fun score(c: Ctx): Double = if (c.p.night) 0.0 else ((0.5 + 0.3 * o.hunger) * max(c.activity, 0.3)).coerceAtMost(0.75)
        override fun enter(c: Ctx) { newSite(c) }

        private fun newSite(c: Ctx) {
            bestD = Double.MAX_VALUE; progressAt = c.t
            left = 1 + c.rng.nextInt(3)
            nextPick = c.t + c.rng.range(0.3, 0.8)
            val from = if (o.pos.distanceTo(den) > RANGE_CM) den else o.pos
            for (attempt in 0 until 4) {
                val sp = c.rockSurfacePoint(from + c.rng.unitVector() * c.rng.range(DART_MIN_CM, DART_MAX_CM), 8.0) ?: continue
                val want = sp.first + sp.second * (0.3 * o.lengthCm + 1.0)
                val st = c.safe(want, 0.6 * o.collisionLength)
                if (st.distanceTo(want) > 1.5) continue
                // it picks head-down at the spot, so the body trails back and up from the stand: that must be clear too
                val f = (sp.first - st).normalized(o.heading)
                val back = f * -o.lengthCm + Vec3.Y * (PICK_PITCH * o.lengthCm)
                if (c.rock.sdf.dist(st + back * 0.5) < TAIL_CLEAR_CM || c.rock.sdf.dist(st + back) < TAIL_CLEAR_CM) continue
                spot = sp.first; stand = st; face = f
                return
            }
            spot = den; stand = denMouth(c); face = (den - stand).normalized(o.heading)
        }

        override fun update(c: Ctx, out: SteerIntent) {
            val d = o.pos.distanceTo(stand)
            if (d < bestD - 1.0) { bestD = d; progressAt = c.t }
            if (d > 2.0 && c.t - progressAt > 5.0) { newSite(c); return }
            if (d > 2.0) {
                // a dart: quick and direct, banking round the rock
                out.seek(if (d > 15) c.viaOpenWater(stand) else stand, o.cruiseSpeed * DART_SPEED, arrive = 1.5, urgency = 0.5)
                return
            }
            out.seek(stand, o.cruiseSpeed * 0.4, arrive = 1.0)
            out.hover = true; out.pectoral = 1.0
            // the way it was checked to fit from the stand (aiming at the spot from wherever it is swings the tail about)
            out.faceDir = face
            out.pitchBias = -PICK_PITCH
            if (c.t >= nextPick) {
                nextPick = c.t + c.rng.range(PICK_MIN_S, PICK_MAX_S)
                o.mouth = 1.0; picks++
                eatPick(c)
                if (--left <= 0) { if (elapsed > 60 + 90 * c.rng.nextDouble()) finished = true else newSite(c) }
            }
        }
    }

    /**
     * The bully (research 51 §2 facts 64-66, 83-84): once settled, it goes for a fish added after it, another wrasse, or a
     * small peaceful fish that comes within its patch: a fast run at it and a nip at the flank, every so often. Bigger,
     * established fish are left alone; a fish that bites back (a dottyback, the hawkfish) is not picked on.
     */
    inner class Harass : Behavior("harass", "Chasing a newcomer") {
        private var who: Organism? = null
        private var nextLook = 0.0
        private var nextChase = c.t + c.rng.range(600.0, 1800.0)
        override val committed: Boolean get() = !finished
        override fun score(c: Ctx): Double {
            if (c.p.night || c.t < nextChase || !settled(c) || o.hunger > 0.7 || o.stress > 0.5) return 0.0
            if (c.t >= nextLook) {
                nextLook = c.t + 1.0
                who = c.p.neighbors(o.pos, PATCH_CM).firstOrNull { victim(c, it) }
            }
            return if (who != null) 0.4 + 0.3 * o.aggression else 0.0
        }
        private fun victim(c: Ctx, n: Organism): Boolean {
            if (!n.alive || n === o || !n.isFish || !n.visible || n.posture == 3) return false
            if (n.brainKind in TOUGH || n.lengthCm > 2.5 * c.L || n.pos.distanceTo(den) > RANGE_CM + PATCH_CM) return false
            val newcomer = n.arrivalTime > o.arrivalTime && c.t - n.arrivalTime < NEWCOMER_S
            val wrasse = n.brainKind in WRASSES
            val meek = n.brainKind in MEEK && n.lengthCm < 1.2 * c.L
            return newcomer || wrasse || meek
        }
        override fun enter(c: Ctx) { chases++ }
        override fun exit(c: Ctx) { nextChase = c.t + c.rng.range(CHASE_GAP_MIN_S, CHASE_GAP_MAX_S) * (1.4 - o.aggression) }
        override fun update(c: Ctx, out: SteerIntent) {
            val w = who ?: run { finished = true; return }
            if (!w.alive || !w.visible || w.posture == 3) { finished = true; return }
            out.seek(w.pos, o.burstSpeed * 0.6, arrive = 0.5, urgency = 0.8)
            if (o.pos.distanceTo(w.pos) < 0.5 * c.L + 0.5 * w.lengthCm) {
                c.sim.agentOf(w)?.let { it.brain.onAttacked(it.ctx, o.pos, NIP_MAG, o) }
                w.socialStress += 0.03
                finished = true
            }
            if (elapsed > 6.0 || w.pos.distanceTo(den) > RANGE_CM + PATCH_CM + 10) finished = true
        }
    }

    /** Drifting and settling food near its patch; it eats almost anything offered (research 51 §2 fact 71). */
    inner class SixlineFeed : Feed(this@SixlineBrain, 50.0)

    /**
     * Night in the rock (research 51 §2 facts 67-68): into its hole at dusk, then a mucus cocoon around it (masking its
     * scent from night hunters); it leaves the cocoon in the morning.
     */
    inner class CocoonSleep : InHole(this@SixlineBrain, home, den, "Sleeping in its mucus cocoon", { c ->
        val o = c.o
        val sick = if (o.health < 0.6 || o.disease != null && o.diseaseStage > 0.3) 1.0 else 0.0
        (0.8 * (1 - c.activity) + 0.25 * o.stress + 0.3 * sick).coerceAtMost(0.9)
    }, { c -> if (c.p.night) -0.8 else 0.3 }) {
        private var inSince = -1.0
        override val label: String get() = if (cocooned) "Sleeping in its mucus cocoon" else "Slipping into the rock"
        override fun update(c: Ctx, out: SteerIntent) {
            hole = home
            super.update(c, out)
            if (phaseNow == 3 && c.p.night) {
                if (inSince < 0) inSince = c.t
                if (!cocooned && c.t - inSince > COCOON_S) { cocooned = true; cocoons++ }
            } else inSince = -1.0
        }
        override fun exit(c: Ctx) { super.exit(c); cocooned = false; inSince = -1.0 }
    }

    companion object {
        /** Head-down tilt (rad) while it picks, and the room its body needs behind the stand (cm) [L]. */
        const val PICK_PITCH = 0.3
        const val TAIL_CLEAR_CM = 1.0
        /** Darts of this length (cm) between picking spots, at this × cruise speed [L]. */
        @Ev(Evidence.DESIGN_ESTIMATE, "51 §2 facts 62-63") const val DART_MIN_CM = 4.0
        const val DART_MAX_CM = 15.0
        const val DART_SPEED = 1.8
        /** Seconds between pecks at a spot [L]. */
        const val PICK_MIN_S = 0.6
        const val PICK_MAX_S = 1.4
        /** Hunger relief per pick at prey index 1 (minutes of hunger rise), and in abstract mode per minute [L]. */
        @Ev(Evidence.DESIGN_ESTIMATE, "51 §2 fact 71") const val PICK_YIELD_MIN = 0.12
        const val ABSTRACT_RELIEF = 1.5
        /** Its hunting patch around its hole (cm). */
        const val RANGE_CM = 35.0
        /** It goes for a fish this close to it (cm), within its hunting range, once it has been in the tank [SETTLE_S] s; newcomers count for [NEWCOMER_S] [L]. */
        @Ev(Evidence.DESIGN_ESTIMATE, "51 §2 facts 64-66") const val PATCH_CM = 25.0
        const val SETTLE_S = 2 * 86400.0
        const val NEWCOMER_S = 7 * 86400.0
        /** Gap between chases (s, × 1.4 − aggression) and the nip's strength [L]. */
        const val CHASE_GAP_MIN_S = 600.0
        const val CHASE_GAP_MAX_S = 2400.0
        const val NIP_MAG = 0.3
        /** Time in the hole after dark before the cocoon is finished (s) [L]. */
        @Ev(Evidence.DESIGN_ESTIMATE, "51 §2 facts 67-68") const val COCOON_S = 600.0
        private val KINDS = setOf(HoleKind.CREVICE, HoleKind.HOLE, HoleKind.CAVE, HoleKind.THROUGH)
        private val WRASSES = setOf(BrainKind.WRASSE, BrainKind.LEOPARD_WRASSE, BrainKind.SIXLINE)
        private val MEEK = setOf(BrainKind.CARDINAL, BrainKind.CLOWN_GOBY, BrainKind.MANDARIN, BrainKind.GRAMMA, BrainKind.WATCHMAN_GOBY, BrainKind.DIAMOND_GOBY)
        private val TOUGH = setOf(BrainKind.DOTTYBACK, BrainKind.HAWK, BrainKind.TANG, BrainKind.EEL)

        private fun defaultNear(c: Ctx): Vec3 =
            Vec3(c.rock.lengthCm * c.o.rng.range(0.3, 0.7), c.rock.sandTopCm + 10.0, c.rock.widthCm * 0.5)
    }
}
