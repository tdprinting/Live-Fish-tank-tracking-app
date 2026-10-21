package com.reeflive.core.ai

import com.reeflive.core.geom.Vec3
import com.reeflive.core.sim.BrainKind
import com.reeflive.core.sim.Counters
import com.reeflive.core.sim.Ev
import com.reeflive.core.sim.Evidence
import com.reeflive.core.sim.HealthModel
import com.reeflive.core.sim.Organism
import com.reeflive.core.sim.SandBed
import com.reeflive.core.sim.Simulation
import com.reeflive.core.geom.BodySpline
import com.reeflive.core.geom.smoothstep
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Diamond goby, *Valenciennea puellaris* (orange-spotted sleeper / maiden goby; docs/research/16_diamond_goby.md).
 * A sand sifter: it hovers just over the bed, takes a mouthful of sand, sifts it through the gill rakers for small
 * crustaceans and worms and blows the sand out of the gill openings in a puff, then shuffles on; after a few mouthfuls
 * it moves to the next feeding station. It digs a shallow burrow under a rock, carrying the sand out mouthful by
 * mouthful, sleeps in it at night and dives into it when startled. Usually in monogamous pairs that share the burrow
 * and feed near each other.
 *
 * Food comes from the sand fauna ([MicrofaunaModel.sandPreyIndex]): in a young bed or a small tank that has been
 * sifted out, the mouthfuls bring little and it slowly starves unless it learned to take sinking prepared food. Its
 * puffs settle on corals placed low on the sand ([SandBed.coralDustings], the coral retracts).
 */
class DiamondGobyBrain(c: Ctx) : SpeciesBrain(c) {
    companion object {
        /** waryLevel above which it ducks into the burrow by day, and above which it stops sifting. */
        const val WARY_HIDE = 0.3
        const val WARY_SIFT = 0.4
        /** Hysteresis band on both thresholds (see band()). */
        const val WARY_BAND = 0.05
        /** Sand per mouthful (cm³): V. puellaris ≈ 0.18 cm³ at ≈ 9 mouthfuls a minute (Palaios 34(3), sand_bioturbation S1). */
        @Ev(Evidence.SOURCED, "sand_bioturbation S1: ~0.18 cm3 per mouthful") const val MOUTHFUL_CM3 = 0.18
        /** Nose-down pitch of the strike (rise over run, ~35°) and how deep the snout bites into the bed (cm). */
        @Ev(Evidence.DESIGN_ESTIMATE) const val SCOOP_PITCH = 0.7
        val SCOOP_PITCH_RAD = kotlin.math.atan(SCOOP_PITCH)
        const val MOUTH_IN_CM = 0.35
        /** The strike is a short lunge: it glides to this far short of the bite point (cm) and dives forward onto it. */
        @Ev(Evidence.DESIGN_ESTIMATE) const val LUNGE_CM = 1.2
        /** Head held slightly nose-up while it winnows (rad), drifting on this slowly (cm/s). */
        @Ev(Evidence.DESIGN_ESTIMATE) const val WINNOW_PITCH_RAD = -0.08
        const val WINNOW_DRIFT_CM_S = 0.3
        /**
         * Buccal pumping while it winnows (Hz): the mouthful is passed back and forth over the gill rakers and the
         * pharyngeal sorting pads by rhythmic flow reversals, as in the winnowing cichlids (Weller et al. 2017, strike →
         * winnow → eject); rate DESIGN ESTIMATE.
         */
        @Ev(Evidence.DESIGN_ESTIMATE, "Weller 2017 winnowing phases") const val WINNOW_HZ = 2.5
        /** Strike (to the bite) and lift out of the bed (s); the bite closes between STRIKE_S and SCOOP_S. */
        const val STRIKE_S = 0.35
        const val LIFT_S = 0.45
        /** Gap between the gill pumps that blow the mouthful out (s). */
        const val EXPEL_GAP_S = 0.16
        /** Time the drawn sifting body takes to ease in from the gliding body (s). */
        const val SHAPE_IN_S = 0.5
        /** Swims on this far (cm) to line its body up before the first strike at a patch, at most this many times. */
        const val STRAIGHTEN_CM = 3.0
        const val STRAIGHTEN_TRIES = 3
        /** A glide that has gained no ground for this long (s) is blocked by rock: it picks another patch. */
        const val STUCK_S = 1.5
        /** How fast the rear of the body swings after the head (rad/s at rest, plus this per body-length-per-second
         *  of speed), and the sharpest bend it is drawn with (rad, ~160°, a fish curled round). DESIGN ESTIMATE. */
        const val TAIL_FOLLOW_RAD_S = 1.5
        const val TAIL_FOLLOW_SWIM = 3.0
        const val MAX_BEND_RAD = 2.8
        /** Rate (1/s) at which the body behind the skull follows the head's pitch. DESIGN ESTIMATE. */
        const val BODY_PITCH_RATE = 5.0
        /** Points of the drawn sifting body, and the share of the head's pitch the tail keeps. */
        const val BODY_POINTS = 16
        const val BODY_LEVEL = 0.15
        /** Tightest turn while gliding over the sand (cm radius), and the slowest turn on the spot (rad/s). */
        @Ev(Evidence.DESIGN_ESTIMATE) const val GLIDE_RADIUS_CM = 2.5
        const val GLIDE_TURN_MIN_RAD_S = 0.8
        /** Gill openings behind the snout (share of body length) and the gill pumps that blow a mouthful out. */
        @Ev(Evidence.DESIGN_ESTIMATE, "fish_diamond_goby: sand pouring out of the gills") const val GILL_AT_BL = 0.23
        const val EXPEL_PULSES = 3
        private val SIGNS = doubleArrayOf(1.0, -1.0)
        /** Share of a mouthful that trickles from the corners of the mouth as it lifts out of the bed. */
        @Ev(Evidence.DESIGN_ESTIMATE) const val SPILL_SHARE = 0.06
        /** Gap kept between the fish and the rock face while it sifts at the rock foot (cm, render clearance). */
        const val ROCK_GAP_CM = 0.3
        /** A strike whose drawn body would come this close (cm) to the rock is not taken there... */
        const val BODY_ROCK_CM = 0.4
        /** ...and after this many such stations in a row the sifting bout ends. */
        const val SCOOP_MOVES = 3
        /** Strike + bite (head down in the sand) and winnow (chewing, gill flare) durations (s). */
        const val SCOOP_S = 0.6
        @Ev(Evidence.DESIGN_ESTIMATE) const val SIFT_MIN_S = 1.5
        const val SIFT_MAX_S = 3.5
        /** Chance a finished sifting bout is followed by a rest on the sand, and its length, s (DESIGN ESTIMATE to bring the
         *  mouthful rate near the measured ~9/min, research 30 §5). */
        const val REST_CHANCE = 0.5
        const val REST_MIN_S = 4.0
        const val REST_MAX_S = 15.0
        /** Mouthfuls per feeding station before moving on, and the hop to the next station (cm). */
        @Ev(Evidence.DESIGN_ESTIMATE) const val MOUTHFULS_MIN = 3
        const val MOUTHFULS_MAX = 8
        const val HOP_MIN_CM = 8.0
        const val HOP_MAX_CM = 25.0
        /** Daytime foraging range around the burrow (cm). */
        const val RANGE_CM = 35.0
        /**
         * Minutes of hunger rise one mouthful offsets on a well-stocked sand bed (prey index 1). With ≈ 8 mouthfuls per
         * daylight minute this is about break-even at prey index 0.8 (DESIGN ESTIMATE; research 16: "unless your
         * sandbed is crawling with pods, it's mostly exercise").
         */
        @Ev(Evidence.DESIGN_ESTIMATE, "16 diamond goby") const val SIFT_YIELD_MIN = 0.3
        /** Abstract mode: hunger relief per minute of daylight in units of the hunger rise, at prey index 1. */
        @Ev(Evidence.DESIGN_ESTIMATE) const val ABSTRACT_RELIEF = 2.4
        /** A puff settles on a coral within this horizontal distance (cm) that sits this low over the sand (cm). */
        @Ev(Evidence.SOURCED, "16: deposits sand on low-mounted corals (aquariadise)") const val DUST_RADIUS_CM = 7.0
        const val LOW_CORAL_CM = 8.0
        /** Pair members forage within this distance of each other (cm). */
        @Ev(Evidence.DESIGN_ESTIMATE) const val PAIR_RADIUS_CM = 14.0
        /** Burrow maintenance: a trip every 30-90 min by day, 3-6 loads per trip. */
        @Ev(Evidence.DESIGN_ESTIMATE) const val BURROW_EVERY_MIN_S = 1800.0
        const val BURROW_EVERY_MAX_S = 5400.0

        /** Sand fauna available to a sifter now (0 = sterile bed, 1 = well-stocked mature bed). */
        fun preyIndex(sim: Simulation): Double = sim.water.micro.sandPreyIndex().coerceIn(0.0, 1.5)
    }

    /** Burrow mouth on the sand at the foot of a rock, and the direction into it. */
    var burrow: Vec3; private set
    var inDir: Vec3; private set
    var mate: Organism? = null; private set
    /** Current feeding station (on the sand, x/z; y = hover height). */
    var station: Vec3 = Vec3.ZERO; private set

    // ---- counters (tests, captions) ----
    var mouthfuls = 0; private set
    var puffs = 0; private set
    var stations = 0; private set
    var burrowLoads = 0; private set
    var coralsDusted = 0; private set
    /** Hunger removed by the last mouthful (diagnostic). */
    var lastYield = 0.0; private set
    private var nextBurrowAt = 0.0

    init {
        // a goby added next to an unpaired one pairs with it and moves into its burrow (research 16: monogamous pairs)
        val partner = c.sim.organisms.firstOrNull { it !== o && it.alive && it.brainKind == BrainKind.DIAMOND_GOBY &&
            (c.sim.agentOf(it)?.brain as? DiamondGobyBrain)?.mate == null }
        val pb = partner?.let { c.sim.agentOf(it)?.brain as? DiamondGobyBrain }
        if (pb != null) {
            burrow = pb.burrow; inDir = pb.inDir
            mate = partner; pb.mate = o
        } else {
            val (p, d) = findBurrow(c)
            burrow = p; inDir = d
        }
        nextBurrowAt = c.t + c.rng.range(300.0, 1800.0)
        install(InBurrow(), Sift(), SandRest(), DigBurrow(), BottomFeed(),
            BurrowDash(), SurfaceGasp(this))
    }

    private val sand: SandBed get() = c.sim.sand

    /** Hover height of the head over the sand at (x, z). */
    private fun hoverY(x: Double, z: Double) = sand.surfaceY(x, z) + 0.5 * o.species.depthRatio * o.lengthCm + 0.2

    /** Sifting at the rock foot and lying in the burrow under it, the fish is kept a few mm off the rock face (the burrow is in the sand). */
    private fun offRock(p: Vec3): Vec3 = if (c.rock.sdf.dist(p) < ROCK_GAP_CM) c.rock.sdf.project(p, ROCK_GAP_CM) else p

    /** Glide low over the sand toward [to] (kinematic: it hugs the bed and the rock foot without the free-swimmer clearance). */
    private fun glide(c: Ctx, out: SteerIntent, to: Vec3, speedMul: Double = 0.8) {
        val v = to - o.pos
        val dist = v.length()
        val want = horizontalHeading(v, o.heading)
        // it swims round in an arc to face a spot behind it instead of backing along its own body (the body follows the
        // head's track: a reversal folded it in two); a few cm off and ahead of it, it steps straight there
        // (the turn is limited in curvature, not just rate: turning on the spot while creeping laid a kink in the track
        // the body follows, and the body straightening behind a fish that has all but stopped swings the tail round fast)
        val h = horizontalHeading(o.heading, want)
        val facing0 = (h dot want).coerceIn(-1.0, 1.0)
        val slow = 0.5 + 0.5 * max(0.0, facing0)
        val speed = o.cruiseSpeed * speedMul * slow
        val turned = Vec3.rotateToward(h, want, max(GLIDE_TURN_MIN_RAD_S, speed / GLIDE_RADIUS_CM) * c.dt)
        val facing = (turned dot want).coerceIn(-1.0, 1.0)
        val near = dist < 2.0 && facing0 > 0.85
        val dir = if (near || facing > 0.95) v.normalized(o.heading) else turned
        val step = min(dist, (if (near) o.cruiseSpeed * speedMul else speed) * c.dt)
        val n = o.pos + dir * step
        glideTo = offRock(Vec3(n.x, hoverY(n.x, n.z), n.z))
        out.place(glideTo, horizontalHeading(if (near) want else dir, o.heading), Vec3.Y, 30.0)
        out.ampMul = 0.8
    }

    /** Where the last [glide] placed the head (the placement eases toward it, so the head is still on its way). */
    private var glideTo = Vec3.ZERO

    /** A point just outside the burrow mouth, [k] cm out. */
    fun mouth(k: Double): Vec3 {
        val p = burrow - inDir * k
        return Vec3(p.x, hoverY(p.x, p.z), p.z)
    }

    private fun findBurrow(c: Ctx): Pair<Vec3, Vec3> {
        val r = c.rock
        var best: Vec3? = null
        var bs = -1e9
        var dir = Vec3(0.0, 0.0, -1.0)
        for (i in 0 until 80) {
            val x = c.rng.range(10.0, r.lengthCm - 10.0)
            val z = c.rng.range(6.0, r.widthCm - 4.0)
            val p = Vec3(x, r.sandTopCm + 1.0, z)
            val d = r.sdf.dist(p)
            if (d < 0.5 || d > 3.0) continue
            // a rock edge or overhang right above the mouth: the burrow goes in under it
            val over = r.sdf.dist(Vec3(x, r.sandTopCm + 4.0, z))
            val s = r.sdf.cover(p) - 0.3 * d - (if (over < 1.5) 0.0 else 0.6) - 0.02 * abs(z - 22.0)
            if (s > bs) { bs = s; best = p; dir = horizontalHeading(r.sdf.gradient(p) * -1.0, Vec3(0.0, 0.0, -1.0)) }
        }
        return (best ?: Vec3(r.lengthCm * 0.5, r.sandTopCm + 1.0, 26.0)) to dir
    }

    /** A sand spot to feed at: open sand within [RANGE_CM] of the burrow, near the mate's station when paired. */
    private fun pickStation(c: Ctx, from: Vec3): Vec3 {
        val r = c.rock
        val mateStation = (mate?.takeIf { it.alive }?.let { c.sim.agentOf(it)?.brain as? DiamondGobyBrain })?.station?.takeIf { it != Vec3.ZERO }
        for (i in 0 until 20) {
            val centre = mateStation ?: from
            val reach = if (mateStation != null) PAIR_RADIUS_CM else c.rng.range(HOP_MIN_CM, HOP_MAX_CM)
            val a = c.rng.range(0.0, 2 * Math.PI)
            val x = (centre.x + kotlin.math.cos(a) * reach).coerceIn(6.0, r.lengthCm - 6.0)
            val z = (centre.z + sin(a) * reach * 0.6).coerceIn(5.0, r.widthCm - 2.5)
            if (Vec3(x, 0.0, z).distanceTo(Vec3(burrow.x, 0.0, burrow.z)) > RANGE_CM) continue
            // clear of rock where it actually hovers (over the live sand, up to 3 cm above the flat plane at the back):
            // checked at the plane, a station tucked under a rock foot left it 0.3 cm off the rock when it swam off to feed
            if (r.sdf.dist(Vec3(x, hoverY(x, z), z)) < 2.5) continue
            return Vec3(x, hoverY(x, z), z)
        }
        return mouth(4.0)
    }

    override fun restSpot(c: Ctx, night: Boolean): Rest =
        if (night) Rest(burrow + inDir * 1.0, inDir, posture = 3)
        else mouth(4.0).let { Rest(it, inDir * -1.0) }

    /** Sand fauna eaten in one mouthful: hunger relief scales with the prey index. */
    private fun eatMouthful(c: Ctx) {
        val prey = preyIndex(c.sim)
        val y = HealthModel.hungerRatePerMin(o, c.sim.water.s.tempC) * SIFT_YIELD_MIN * prey
        lastYield = y
        if (y > 0) {
            o.hunger = max(0.0, o.hunger - y)
            if (prey > 0.2) o.counters.add(Counters.K.FEED, c.t)
        }
    }

    /** The sand blown out of the gills lands on any low coral close by (research 16). */
    private fun dustCorals(c: Ctx, at: Vec3) {
        val bed = sand.surfaceY(at.x, at.z)
        for (cor in c.sim.corals) {
            if (cor.tissue < 0.01 || cor.pos.y > bed + LOW_CORAL_CM) continue
            val dx = cor.pos.x - at.x; val dz = cor.pos.z - at.z
            if (dx * dx + dz * dz > DUST_RADIUS_CM * DUST_RADIUS_CM) continue
            cor.disturb = max(cor.disturb, 0.5)
            sand.coralDustings++
            coralsDusted++
        }
    }

    /** Daylight in abstract mode (> 240×): sifting as a rate, with marks on the bed near the burrow. */
    fun abstractSift(dtMin: Double) {
        val prey = preyIndex(c.sim)
        o.hunger = max(0.0, o.hunger - HealthModel.hungerRatePerMin(o, c.sim.water.s.tempC) * ABSTRACT_RELIEF * prey * dtMin)
        mouthfuls += (8 * dtMin).toInt()
        if (c.rng.chance(min(1.0, dtMin / 2.0))) {
            val s = pickStation(c, burrow)
            sand.dig(s.x, s.z, MOUTHFUL_CM3, 1.0)
            sand.deposit(s.x - inDir.x, s.z - inDir.z, MOUTHFUL_CM3, 1.8)
            sand.sift(s.x, s.z, 1.2, 0.4)
        }
    }

    // ================================================================ behaviours
    /**
     * Hysteresis on the wary thresholds: -[WARY_BAND] for the behaviour in hand, +[WARY_BAND] for the others. InBurrow
     * takes over above WARY_HIDE + 0.05 and lets go only below WARY_HIDE - 0.05; Sift, once going, keeps on up to
     * WARY_SIFT + 0.05 and starts again only below WARY_SIFT - 0.05. A waryLevel hovering at a threshold
     * no longer flips burrow / sift every few ticks (FishGlitch BEHAVIOUR_THRASH).
     */
    private fun band(b: Behavior): Double = if (utility.current === b) -WARY_BAND else WARY_BAND


    /** Night (and after a scare): tucked into the burrow under the rock. */
    inner class InBurrow : Behavior("burrow", "In its burrow") {
        override fun score(c: Ctx): Double = when {
            c.p.night -> 0.85 * Feed.nightRestDamp(c, this@DiamondGobyBrain)
            o.waryLevel > WARY_HIDE + band(this) || o.startle > 0.25 -> 0.7
            else -> 0.1
        }
        override fun update(c: Ctx, out: SteerIntent) {
            val night = c.p.night
            if (o.posture != 3 && o.pos.distanceTo(mouth(0.5)) > 3) {
                glide(c, out, mouth(0.5), 1.2)
                return
            }
            // head just inside the mouth by day, deeper at night (the pair shares the burrow)
            out.place(offRock(burrow + inDir * (if (night) 1.5 else 0.2)), inDir, Vec3.Y, 3.0)
            out.posture = 3
            o.visible = !night
            o.sleeping = night
            o.gill = 0.35 + 0.2 * sin(c.t * 2.5)
        }
        override fun exit(c: Ctx) { c.o.visible = true }
    }

    /**
     * Station sifting, as a sleeper goby feeds (research 16 §2): glide to the patch → strike (a short lunge forward
     * and down, mouth wide, the snout biting a few mm into the bed) → lift (mouth shut on the sand, body levelling) →
     * winnow (hovering, the throat pumping the mouthful over the gill rakers, fines seeping from the gill slits) →
     * expel (the gill covers flare and the cleaned sand pours out of both openings in a few pumps) → shuffle 1-2 cm on
     * and strike again; a new patch after [MOUTHFULS_MIN]..[MOUTHFULS_MAX] mouthfuls.
     *
     * The head (the organism's reference point) only ever moves forward or up and down, never back into its own trail,
     * and from the strike to the last pump the body is drawn as one smooth curve from the head ([siftBody]): the head
     * pitches down, the body behind it eases back toward level and never dips into the sand. (Before, the head was set
     * half a body length back up the pitch and the body followed the head's track, so every scoop folded it in two.)
     */
    inner class Sift : Behavior("sift", "Sifting sand") {
        private var phase = 0
        private var timer = 0.0
        private var left = 0
        private var chew = 2.0
        private var fwd = Vec3.X
        /** Sand in its mouth (cm³) between the strike and the last gill pump. */
        private var held = 0.0
        private var pulses = 0
        private var nextSeep = 0.0
        /** Head at the start of the strike, the bite point in the bed, and where it winnows. */
        private var from = Vec3.ZERO
        private var bite = Vec3.ZERO
        private var hover = Vec3.ZERO
        /** Nose-down pitch of the head (rad) and its value when the current move started. */
        private var pitch = 0.0
        private var pitch0 = 0.0
        private val body = ArrayList<Vec3>(BODY_POINTS)
        override val committed: Boolean get() = phase in 1..3
        override val label: String get() = when (phase) { 0 -> "Moving to a new patch of sand"; 1 -> "Taking a mouthful of sand"; 2 -> "Sifting sand through its gills"; else -> "Blowing sand out of its gills" }
        override fun score(c: Ctx): Double {
            if (c.p.night || o.waryLevel > WARY_SIFT - band(this)) return 0.0
            return (0.55 + 0.3 * o.hunger) * max(c.activity, 0.3)
        }
        /** Stations given up in a row because the strike there would put the body in the rock. */
        private var moves = 0
        /** Where the head bites at station [s]: [MOUTH_IN_CM] into the bed. */
        private fun biteAt(s: Vec3) = Vec3(s.x, sand.surfaceY(s.x, s.z) - MOUTH_IN_CM, s.z)
        /** Where it glides to before striking at [s]: a short lunge back from it, at hover height. */
        private fun approach(s: Vec3, f: Vec3): Vec3 { val q = s - f * LUNGE_CM; return Vec3(q.x, hoverY(q.x, q.z), q.z) }
        /** A strike at [s] (body drawn at full pitch from the bite) would lie in or against the rock. */
        private fun blocked(s: Vec3, f: Vec3): Boolean {
            val pts = siftBody(biteAt(s), f, SCOOP_PITCH_RAD, ArrayList(BODY_POINTS))
            for (p in pts) if (c.rock.sdf.dist(p) < BODY_ROCK_CM) return true
            return false
        }
        /** Closest it has got to the patch on this glide, and when (s into the glide). */
        private var bestD = 1e9
        private var bestAt = 0.0
        /** Lines-up taken at this patch (see phase 0). */
        private var straighten = 0
        /** The body lies roughly straight behind the head (its tail half within ~45° of the heading). */
        private fun straight(c: Ctx): Boolean {
            o.spline.sampleStations(start)
            val h = horizontalHeading(o.heading, fwd)
            return (horizontalHeading(start[BodySpline.POINTS / 2] - start[BodySpline.POINTS - 1], h) dot h) > 0.7 &&
                (horizontalHeading(start[0] - start[BodySpline.POINTS / 2], h) dot h) > 0.7
        }
        override fun enter(c: Ctx) {
            phase = 0; timer = 0.0; held = 0.0; pulses = 0; moves = 0; pitch = 0.0; straighten = 0; bestD = 1e9; bestAt = 0.0
            station = pickStation(c, if (station == Vec3.ZERO) burrow else station)
            left = MOUTHFULS_MIN + c.rng.nextInt(MOUTHFULS_MAX - MOUTHFULS_MIN + 1)
            fwd = horizontalHeading(station - o.pos, o.heading)
        }
        /** Pitch the body behind the skull has caught up to (rad). */
        private var bodyPitch = 0.0
        /** Which way the rear of the body points (horizontal): it swings after the head as the fish turns. */
        private var tailDir = Vec3.X
        /** Keeps the tail within [MAX_BEND_RAD] of the head's direction [h] (a sharper turn drags it round). */
        private fun limitBend(h: Vec3) {
            // (exactly reversed, turn it about the vertical: rotateToward's fallback axis would tip it on end)
            if ((h dot tailDir) < -0.999) tailDir = (h cross Vec3.Y).normalized(Vec3.Z)
            val ang = kotlin.math.acos((h dot tailDir).coerceIn(-1.0, 1.0))
            if (ang > MAX_BEND_RAD) tailDir = Vec3.rotateToward(tailDir, h, ang - MAX_BEND_RAD)
        }
        /** The body as it was when the strike began, head-relative (the drawn curve eases in from it, see [drawBody]). */
        private val start = Array(BodySpline.POINTS) { Vec3.ZERO }
        /** 0 = [start] shape .. 1 = the drawn sifting curve. */
        private var shapeIn = 1.0

        /**
         * Holds the body on the drawn curve from the head (see [siftBody]) from the strike to the last gill pump, eased
         * in from the shape it had when the strike began. The head's own track is not used there: the strike dips the
         * head and lifts it again on the spot, and a body following that track folded; a fish that had just turned to
         * its patch has its tail off to one side, and straightening it at once swung the tail 3 cm in a tick.
         */
        private fun drawBody(c: Ctx, out: SteerIntent) {
            val h = horizontalHeading(o.heading, fwd)
            // the rear follows the head round, faster the faster it swims (a C-shaped turn, never a hairpin)
            val sp = o.vel.length() / max(o.cruiseSpeed, 0.5)
            tailDir = Vec3.rotateToward(tailDir, h, (TAIL_FOLLOW_RAD_S + TAIL_FOLLOW_SWIM * sp) * c.dt)
            limitBend(h)
            // the body tips after the head, not with it (the head leads the strike)
            bodyPitch += (pitch - bodyPitch) * (1 - kotlin.math.exp(-BODY_PITCH_RATE * c.dt))
            siftBody(o.pos, h, pitch, body, tailDir, bodyPitch)
            shapeIn = min(1.0, shapeIn + c.dt / SHAPE_IN_S)
            if (shapeIn < 1.0) {
                val w = smoothstep(0.0, 1.0, shapeIn)
                for (i in 1 until body.size) body[i] = (o.pos + start[i]).lerp(body[i], w)
            }
            out.splineOverride = body
            out.ampMul = 0.15
            out.pectoral = 1.0
        }
        private fun startStrike(offGlide: Boolean) {
            // off a glide the head carries on from where the glide was taking it (starting from where it had got to
            // pulled it back a couple of mm first)
            phase = 1; timer = 0.0
            from = if (offGlide && glideTo.distanceTo(o.pos) < 2.0) glideTo else o.pos
            bite = biteAt(station); pitch0 = pitch
            if (o.spline.override == null) {
                // off a glide (the body lies along the head's track): the drawn body eases in from that shape, its rear
                // pointing the way the rear half lies now, and straightens from there
                o.spline.sampleStations(start)
                tailDir = horizontalHeading(start[BodySpline.POINTS / 2] - start[BodySpline.POINTS - 1], horizontalHeading(o.heading, fwd))
                for (i in start.indices) start[i] = start[i] - o.pos
                shapeIn = 0.0
                bodyPitch = 0.0
            }

        }
        override fun update(c: Ctx, out: SteerIntent) {
            timer += c.dt
            val s = station
            when (phase) {
                0 -> { // glide low over the sand to just short of the patch
                    val a = approach(s, fwd)
                    val d = o.pos.distanceTo(a)
                    glide(c, out, a)
                    // held against a rock between it and the patch (the glide is straight): no headway for a while
                    if (d < bestD - 0.5) { bestD = d; bestAt = timer }
                    val stuck = timer - bestAt > STUCK_S
                    val h = horizontalHeading(o.heading, fwd)
                    val facing = (horizontalHeading(s - o.pos, h) dot h) > 0.8
                    if (d < 1.0 && timer <= 25 && (!facing || !straight(c)) && straighten < STRAIGHTEN_TRIES) {
                        // just turned round to the patch, its body still curled along the turn, or the patch is beside
                        // or behind it (it had settled right by it): it swims on a little to line up first (a strike now
                        // would twist the head round and swing the whole body straight under it)
                        straighten++
                        if (!facing) fwd = h
                        val q = (if (facing) s else o.pos) + fwd * STRAIGHTEN_CM
                        station = Vec3(q.x.coerceIn(6.0, c.rock.lengthCm - 6.0), hoverY(q.x, q.z), q.z.coerceIn(5.0, c.rock.widthCm - 2.5))
                    } else if (d < 1.0 || timer > 25 || stuck) {
                        val f = horizontalHeading(s - o.pos, fwd)
                        // not got there (the straight glide is blocked by rock between): a strike from 9 cm away put
                        // its head through the rock foot, the body folding in after it. Nor where the body would lie in
                        // the rock (a patch by a rock foot, or the burrow-mouth fallback). It looks for open sand from
                        // where it is instead (a few tries, then the bout ends)
                        if (d >= 1.0 || blocked(s, f)) {
                            if (++moves >= SCOOP_MOVES) { finished = true; return }
                            station = pickStation(c, o.pos); timer = 0.0; bestD = 1e9; bestAt = 0.0
                            fwd = horizontalHeading(station - o.pos, fwd)
                        } else { fwd = f; startStrike(true) }
                    }
                }
                1 -> { // strike: a lunge forward and down, mouth wide, the snout into the bed; then the bite closes
                    val k = smoothstep(0.0, STRIKE_S, timer)
                    pitch = pitch0 + (SCOOP_PITCH_RAD - pitch0) * smoothstep(0.0, 0.7 * STRIKE_S, timer)
                    val head = from.lerp(bite, k)
                    out.place(offRock(head), pitched(fwd, pitch), Vec3.Y, 30.0)
                    drawBody(c, out)
                    o.inSand = true
                    o.mouth = if (timer < STRIKE_S) 0.3 + 0.7 * k else 1.0 - smoothstep(STRIKE_S, SCOOP_S, timer)
                    if (timer >= SCOOP_S) {
                        // the mouthful comes out of the bed (a pit at the mouth) and is held while it winnows
                        held = sand.scoop(s.x, s.z, MOUTHFUL_CM3, 0.9)
                        sand.sift(s.x, s.z, 1.2, 0.5)
                        // a few grains trickle from the corners of the mouth as it lifts out
                        val bed = sand.surfaceY(s.x, s.z)
                        val spill = held * SPILL_SHARE
                        sand.expel(s.x, bed + 0.2, s.z, spill, fwd.x * 0.5, 1.5, fwd.z * 0.5, fines = 3, maxGrains = 12)
                        held -= spill
                        mouthfuls++
                        phase = 2; timer = 0.0; chew = c.rng.range(SIFT_MIN_S, SIFT_MAX_S); nextSeep = LIFT_S
                        from = o.pos; pitch0 = pitch
                        hover = Vec3(s.x, hoverY(s.x, s.z), s.z)
                    }
                }
                2 -> { // lift out and winnow: mouth shut on the sand, the throat pumping, gills working, drifting on a little
                    val k = smoothstep(0.0, LIFT_S, timer)
                    pitch = pitch0 + (WINNOW_PITCH_RAD - pitch0) * k
                    val drift = hover + fwd * (WINNOW_DRIFT_CM_S * max(0.0, timer - LIFT_S))
                    val head = from.lerp(Vec3(drift.x, hoverY(drift.x, drift.z), drift.z), k)
                    out.place(offRock(head), pitched(fwd, pitch), Vec3.Y, 30.0)
                    drawBody(c, out)
                    o.inSand = o.pos.y < sand.surfaceY(o.pos.x, o.pos.z) + com.reeflive.core.sim.SandContact.clearance(o)
                    // buccal pumping: the mouth stays almost shut (the sand leaves by the gills), the jaw working
                    o.mouth = 0.12 * k * (0.5 + 0.5 * sin(2 * Math.PI * WINNOW_HZ * c.t))
                    o.gill = 0.5 + 0.35 * k
                    o.gillRate = WINNOW_HZ
                    if (timer >= nextSeep) {
                        nextSeep += 0.3
                        for (sg in SIGNS) { val g = gill(sg); val l = side(sg); sand.seep(g.x, g.y, g.z, 1, -fwd.x * 1.5 + l.x * 1.2, 0.3, -fwd.z * 1.5 + l.z * 1.2) }
                    }
                    if (timer >= LIFT_S + chew) { phase = 3; timer = 0.0; pulses = 0; from = o.pos }
                }
                else -> { // blow it out of the gill openings in a few pumps: the grains pour down beside and behind it
                    out.place(offRock(from), pitched(fwd, pitch), Vec3.Y, 30.0)
                    drawBody(c, out)
                    o.inSand = false
                    o.mouth = 0.0
                    o.gill = if (pulses < EXPEL_PULSES) 1.0 else 0.6
                    o.gillRate = 1.0 / EXPEL_GAP_S
                    if (pulses < EXPEL_PULSES && timer >= 0.1 + EXPEL_GAP_S * pulses) {
                        pulses++
                        val part = if (pulses == EXPEL_PULSES) held else held / (EXPEL_PULSES - pulses + 1)
                        for (sg in SIGNS) {
                            val g = gill(sg); val l = side(sg)
                            sand.expel(g.x, g.y, g.z, 0.5 * part, l.x * 3.5 - fwd.x * 5.0, -0.5, l.z * 3.5 - fwd.z * 5.0, fines = 3, maxGrains = 32)
                        }
                        held -= part
                        if (pulses == 1) {
                            val g = o.pos + o.heading * (-GILL_AT_BL * o.lengthCm)
                            eatMouthful(c)
                            dustCorals(c, g)
                            puffs++
                        }
                    }
                    if (timer >= 0.1 + EXPEL_GAP_S * EXPEL_PULSES + 0.1) {
                        o.gillRate = 1.0
                        left--
                        val p = o.pos
                        val next = Vec3(p.x + fwd.x * 2.0, hoverY(p.x + fwd.x * 2.0, p.z + fwd.z * 2.0), p.z + fwd.z * 2.0)
                        // the shuffle would run it into the rock foot, or it is not where it winnows (held off by the
                        // rock between): this patch is done
                        if (c.rock.sdf.dist(next) < 1.5 || p.distanceTo(offRock(p)) > 0.5) left = 0
                        if (left > 0) {
                            // shuffle on a little and strike again, a step ahead of the snout
                            station = Vec3(p.x + fwd.x * c.rng.range(0.8, 2.0), 0.0, p.z + fwd.z * c.rng.range(0.8, 2.0)).let { q ->
                                val x = q.x.coerceIn(5.0, c.rock.lengthCm - 5.0); val z = q.z.coerceIn(4.0, c.rock.widthCm - 2.0)
                                Vec3(x, hoverY(x, z), z)
                            }
                            // (nor where the strike would put the body in the rock, as at a patch: see phase 0)
                            if (blocked(station, fwd)) { stations++; finished = true } else startStrike(false)
                        } else {
                            stations++
                            // between sifting bouts it often settles on the sand for a while (research 30 §5: ~9 mouthfuls
                            // a minute counting rests, Myers et al. 2019; perching between bouts, hobby observation)
                            if (c.rng.chance(REST_CHANCE)) restUntil = c.t + c.rng.range(REST_MIN_S, REST_MAX_S)
                            finished = true
                        }
                    }
                }
            }
        }

        /** Unit vector to the fish's [sign] side (horizontal). */
        private fun side(sign: Double): Vec3 = (fwd cross Vec3.Y).normalized(Vec3.Z) * sign

        /** The gill opening on the [sign] side: [GILL_AT_BL] behind the snout (the head is the reference point), at the flank. */
        private fun gill(sign: Double): Vec3 =
            o.pos - pitched(fwd, pitch) * (GILL_AT_BL * o.lengthCm) + side(sign) * (0.3 * o.species.depthRatio * o.lengthCm)

        override fun exit(c: Ctx) {
            c.o.gillRate = 1.0
            c.o.inSand = false
            pitch = 0.0
            // interrupted with a mouthful: it lets the sand go where it is
            if (held > 0) { sand.expel(o.pos.x, o.pos.y, o.pos.z, held, 0.0, -1.0, 0.0, fines = 1, maxGrains = 20); held = 0.0 }
        }
    }

    /** [f] (horizontal) pitched [a] rad nose-down. */
    private fun pitched(f: Vec3, a: Double): Vec3 = Vec3(f.x * cos(a), -sin(a), f.z * cos(a))

    /**
     * The sifting body as one smooth curve back from the [head]: the front quarter (the skull) keeps the head's
     * nose-down [pitch] along [f] and the rear curves round to lie along [tail] (a turn); behind it, tipped by
     * [rearPitch] (the body catching up with the head), the pitch eases toward level ([BODY_LEVEL] of it is kept at the tail), so
     * a strike tips the head into the bed while the tail rides a little above it, as the fish does. No station is
     * left in the sand (it is raised to lie on it, belly clear) or in the rock. Fills and returns [out].
     */
    private fun siftBody(head: Vec3, f: Vec3, pitch: Double, out: ArrayList<Vec3>, tail: Vec3 = f, rearPitch: Double = pitch): ArrayList<Vec3> {
        out.clear()
        out.add(head)
        val seg = o.lengthCm / (BODY_POINTS - 1)
        val belly = 0.25 * o.species.depthRatio * o.lengthCm
        val bend = kotlin.math.acos((f dot tail).coerceIn(-1.0, 1.0))
        var p = head
        for (i in 1 until BODY_POINTS) {
            val u = (i - 0.5) / (BODY_POINTS - 1)
            val a = (pitch + (rearPitch - pitch) * smoothstep(0.1, 0.4, u)) * (1 - (1 - BODY_LEVEL) * smoothstep(0.2, 0.85, u))
            // and bends round from the head's direction to the tail's, the bend spread over the rear three quarters
            val d = if (tail === f) f else Vec3.rotateToward(f, tail, bend * smoothstep(0.15, 0.95, u))
            p -= pitched(d, a) * seg
            val floor = sand.surfaceY(p.x, p.z) + belly
            if (p.y < floor) p = p.withY(floor)
            // and none in the rock: a tail swinging round by a rock foot slides along its face (left in it, the rock
            // guard re-laid the body every tick and it writhed)
            if (p.y > floor) {
                val dr = c.rock.sdf.dist(p)
                // pushed straight out along the face normal by what it lacks (continuous: a nearest-surface projection
                // flipped a station from face to face tick by tick)
                if (dr < ROCK_GAP_CM) p += c.rock.sdf.gradient(p).normalized(Vec3.Y) * (ROCK_GAP_CM - dr)
            }
            out.add(p)
        }
        return out
    }

    /** When the current sand rest ends (set as a sifting bout finishes). */
    private var restUntil = -1.0

    /**
     * Settled on the sand between sifting bouts, propped on the pelvic fins with the gills slowing, eyes turning to
     * look about now and then (research 30 §5, hobby observation; durations DESIGN ESTIMATE).
     */
    inner class SandRest : Behavior("sandrest", "Resting on the sand") {
        private var at = Vec3.ZERO
        private var fwd = Vec3.X
        private var nextLook = 0.0
        override fun score(c: Ctx): Double = if (c.p.night || c.t >= restUntil || o.waryLevel > 0.4) 0.0 else 0.9
        override fun enter(c: Ctx) {
            fwd = horizontalHeading(o.heading, Vec3.X)
            at = offRock(Vec3(o.pos.x, hoverY(o.pos.x, o.pos.z) - 0.2, o.pos.z))
        }
        override fun update(c: Ctx, out: SteerIntent) {
            out.place(at, fwd, Vec3.Y, 3.0)
            o.gillRate = 0.8
            if (c.t > nextLook) { nextLook = c.t + c.rng.range(1.5, 4.0); o.eyeYaw = c.rng.signed() * 0.4 }
            if (c.t >= restUntil) finished = true
        }
        override fun exit(c: Ctx) { o.gillRate = 1.0; o.eyeYaw = 0.0 }
    }

    /** Burrow upkeep: duck in under the rock, come out with a mouthful and spit it a few cm away (a pit and a mound). */
    inner class DigBurrow : Behavior("dig", "Digging out its burrow") {
        private var phase = 0
        private var timer = 0.0
        private var loads = 0
        private var dump = Vec3.ZERO
        override val committed: Boolean get() = phase in 1..3
        override fun score(c: Ctx): Double = if (c.p.night || c.t < nextBurrowAt || o.waryLevel > 0.3) 0.0 else 0.8
        override fun enter(c: Ctx) { phase = 0; timer = 0.0; loads = 3 + c.rng.nextInt(4) }
        override fun update(c: Ctx, out: SteerIntent) {
            timer += c.dt
            when (phase) {
                0 -> {
                    glide(c, out, mouth(0.5))
                    if (o.pos.distanceTo(mouth(0.5)) < 2.0 || timer > 30) { phase = 1; timer = 0.0 }
                }
                1 -> { // in under the rock, digging
                    out.place(offRock(burrow + inDir * 1.2), inDir, Vec3.Y, 4.0)
                    out.posture = 3
                    o.mouth = 1.0
                    if (timer > 2.0 + 2.0 * c.rng.nextDouble()) {
                        phase = 2; timer = 0.0
                        val side = (inDir cross Vec3.Y).normalized(Vec3.X) * (c.rng.signed() * 4.0)
                        val d = burrow - inDir * c.rng.range(4.0, 8.0) + side
                        dump = Vec3(d.x.coerceIn(4.0, c.rock.lengthCm - 4.0), 0.0, d.z.coerceIn(3.0, c.rock.widthCm - 2.0))
                        dump = Vec3(dump.x, hoverY(dump.x, dump.z) + 0.8, dump.z)
                        sand.dig(burrow.x, burrow.z, MOUTHFUL_CM3 * 1.5, 1.3)
                    }
                }
                2 -> { // carry it out
                    o.mouth = 1.0
                    glide(c, out, dump)
                    if (o.pos.distanceTo(dump) < 1.8 || timer > 10) { phase = 3; timer = 0.0 }
                }
                else -> { // spit
                    o.mouth = 1.0
                    glide(c, out, dump, 0.1)
                    out.pitchBias = -0.4
                    if (timer > 0.5) {
                        c.sim.depositSand(dump, MOUTHFUL_CM3 * 1.5)
                        burrowLoads++
                        loads--
                        if (loads > 0) { phase = 0; timer = 0.0 }
                        else { nextBurrowAt = c.t + c.rng.range(BURROW_EVERY_MIN_S, BURROW_EVERY_MAX_S); finished = true }
                    }
                }
            }
        }
    }

    /** Startle: a flinch away, then a dash low over the sand into the burrow mouth (kinematic like its other moves, so it can reach the mouth under the rock). */
    inner class BurrowDash : Flee(this@DiamondGobyBrain, { _, _ -> mouth(0.5) }, "Diving into its burrow") {
        override fun update(c: Ctx, out: SteerIntent) {
            super.update(c, out)
            val burst = o.burstSpeed / max(o.cruiseSpeed, 0.1)
            val from = out.fleeFrom
            out.target = null; out.hover = false; out.fleeFrom = null
            if (from != null) {
                // the flinch: a burst straight away from the threat, along the bed
                val away = horizontalHeading(o.pos - from, o.heading)
                glide(c, out, o.pos + away * 10.0, burst)
            } else glide(c, out, mouth(0.5), 0.7 * burst)
        }
    }

    /** Prepared food that reaches the sand (it rarely rises for food in the water column). */
    inner class BottomFeed : Feed(this@DiamondGobyBrain, 40.0, "Picking food off the sand") {
        override fun accept(c: Ctx, f: com.reeflive.core.sim.FoodParticle): Boolean = f.settled || f.pos.y < c.rock.sandTopCm + 10.0
    }
}
