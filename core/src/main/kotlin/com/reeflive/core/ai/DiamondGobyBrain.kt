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
import kotlin.math.abs
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
        /** Sand per mouthful for a ~12 cm fish (cm³). */
        @Ev(Evidence.DESIGN_ESTIMATE, "16 diamond goby: mouthful size not measured") const val MOUTHFUL_CM3 = 0.35
        /** Gap kept between the fish and the rock face while it sifts at the rock foot (cm, render clearance). */
        const val ROCK_GAP_CM = 0.3
        /** Scoop (head down in the sand) and sift (chewing, gill flare) durations (s). */
        const val SCOOP_S = 0.6
        @Ev(Evidence.DESIGN_ESTIMATE) const val SIFT_MIN_S = 1.5
        const val SIFT_MAX_S = 3.5
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
        install(InBurrow(), Sift(), DigBurrow(), BottomFeed(),
            BurrowDash(), SurfaceGasp(this))
    }

    private val sand: SandBed get() = c.sim.sand

    /** Hover height of the head over the sand at (x, z). */
    /** Belly height over the sand at a head position (x, z): the highest bed under the body (the mounds it dumps), not just under the head. */
    private fun hoverY(x: Double, z: Double): Double {
        val h = horizontalHeading(o.heading, Vec3.X)
        var g = sand.surfaceY(x, z)
        for (k in 1..4) { val f = o.lengthCm * k / 4.0; g = max(g, sand.surfaceY(x - h.x * f, z - h.z * f)) }
        return g + 0.5 * o.species.depthRatio * o.lengthCm + 0.2
    }

    /** Sifting at the rock foot and lying in the burrow under it, the fish is kept a few mm off the rock face (the burrow is in the sand). */
    /** [p] (the head, facing [fwd]) moved so the whole body, tail included, stays inside the glass. */
    private fun inGlass(p: Vec3, fwd: Vec3): Vec3 {
        val m = 0.6
        val tail = p - fwd * o.lengthCm
        fun shift(h: Double, t: Double, hi: Double) = max(m - min(h, t), 0.0) - max(max(h, t) - (hi - m), 0.0)
        return Vec3(p.x + shift(p.x, tail.x, c.rock.lengthCm), p.y, p.z + shift(p.z, tail.z, c.rock.widthCm))
    }

    private fun offRock(p: Vec3): Vec3 = if (c.rock.sdf.dist(p) < ROCK_GAP_CM) c.rock.sdf.project(p, ROCK_GAP_CM) else p

    /** Glide low over the sand toward [to] (kinematic: it hugs the bed and the rock foot without the free-swimmer clearance). */
    private fun glide(c: Ctx, out: SteerIntent, to: Vec3, speedMul: Double = 0.8) {
        val v = to - o.pos
        val step = min(v.length(), o.cruiseSpeed * speedMul * c.dt)
        val dir = v.normalized(o.heading)
        val n = o.pos + dir * step
        val hd = horizontalHeading(dir, o.heading)
        out.place(offRock(inGlass(Vec3(n.x, hoverY(n.x, n.z), n.z), hd)), hd, Vec3.Y, 30.0)
        out.ampMul = 0.8
    }

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
            if (r.sdf.dist(Vec3(x, r.sandTopCm + 1.5, z)) < 2.5) continue
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

    /** Night (and after a scare): tucked into the burrow under the rock. */
    inner class InBurrow : Behavior("burrow", "In its burrow") {
        override fun score(c: Ctx): Double = when {
            c.p.night -> 0.85
            o.waryLevel > 0.3 || o.startle > 0.25 -> 0.7
            else -> 0.1
        }
        /** In at the mouth (the glide nosing into it is posture 3 too, but still gliding there). */
        private var inside = false
        override fun enter(c: Ctx) { inside = false }
        override fun update(c: Ctx, out: SteerIntent) {
            val night = c.p.night
            if (!inside && o.pos.distanceTo(mouth(0.5)) > 3) {
                glide(c, out, mouth(0.5), 1.2)
                // nosing into the mouth under the rock: in the burrow (an allowed contact, as in [DigBurrow])
                if (o.pos.distanceTo(burrow) < 0.8 * o.lengthCm) out.posture = 3
                return
            }
            inside = true
            // head just inside the mouth by day, deeper at night (the pair shares the burrow)
            // eased in a step at a time, each step kept off the rock (the burrow is in the sand under it: the straight
            // ease from the mouth cut the corner of the rock lip with the head)
            val to = offRock(burrow + inDir * (if (night) 1.5 else 0.2))
            out.place(offRock(o.pos.lerp(to, 1 - kotlin.math.exp(-3.0 * c.dt))), inDir, Vec3.Y, 1e3)
            out.posture = 3
            o.visible = !night
            o.sleeping = night
            o.gill = 0.35 + 0.2 * sin(c.t * 2.5)
        }
        override fun exit(c: Ctx) { c.o.visible = true }
    }

    /**
     * Station sifting: travel → scoop (head down, mouth full of sand) → sift (hover, gills working) → expel (sand puff
     * from the gill openings) → shuffle on; a new station after [MOUTHFULS_MIN]..[MOUTHFULS_MAX] mouthfuls.
     */
    inner class Sift : Behavior("sift", "Sifting sand") {
        private var phase = 0
        private var timer = 0.0
        private var left = 0
        private var chew = 2.0
        private var fwd = Vec3.X
        override val committed: Boolean get() = phase in 1..3
        override val label: String get() = when (phase) { 0 -> "Moving to a new patch of sand"; 1 -> "Taking a mouthful of sand"; 2 -> "Sifting sand through its gills"; else -> "Blowing sand out of its gills" }
        override fun score(c: Ctx): Double {
            if (c.p.night || o.waryLevel > 0.4) return 0.0
            return (0.55 + 0.3 * o.hunger) * max(c.activity, 0.3)
        }
        override fun enter(c: Ctx) {
            phase = 0; timer = 0.0
            station = pickStation(c, if (station == Vec3.ZERO) burrow else station)
            left = MOUTHFULS_MIN + c.rng.nextInt(MOUTHFULS_MAX - MOUTHFULS_MIN + 1)
            fwd = horizontalHeading(station - o.pos, o.heading)
        }
        override fun update(c: Ctx, out: SteerIntent) {
            timer += c.dt
            val s = station
            when (phase) {
                0 -> { // glide low over the sand to the station
                    val d = o.pos.distanceTo(s)
                    glide(c, out, s)
                    if (d < 2.0 || timer > 25) { phase = 1; timer = 0.0; fwd = horizontalHeading(o.heading, fwd) }
                }
                1 -> { // nose into the sand
                    val head = Vec3(s.x, sand.surfaceY(s.x, s.z) + 0.3, s.z)
                    out.place(offRock(inGlass(head, fwd)), Vec3(fwd.x, -0.6, fwd.z).normalized(fwd), Vec3.Y, 5.0)
                    o.mouth = 1.0
                    if (timer >= SCOOP_S) {
                        sand.dig(s.x, s.z, MOUTHFUL_CM3, 1.0)
                        sand.sift(s.x, s.z, 1.2, 0.5)
                        mouthfuls++
                        phase = 2; timer = 0.0; chew = c.rng.range(SIFT_MIN_S, SIFT_MAX_S)
                    }
                }
                2 -> { // hover and sift: cheeks working, gills flared, drifting forward
                    val p = s + fwd * (timer * 0.6)
                    out.place(offRock(inGlass(Vec3(p.x, hoverY(p.x, p.z), p.z), fwd)), fwd, Vec3.Y, 3.0)
                    o.mouth = 0.3 + 0.2 * sin(c.t * 9.0)
                    o.gill = 0.8 + 0.2 * sin(c.t * 7.0)
                    o.gillRate = 2.5
                    if (timer >= chew) { phase = 3; timer = 0.0 }
                }
                else -> { // expel from the gill openings
                    val p = s + fwd * (chew * 0.6)
                    out.place(offRock(inGlass(Vec3(p.x, hoverY(p.x, p.z), p.z), fwd)), fwd, Vec3.Y, 3.0)
                    o.gill = 1.0
                    if (timer >= 0.15 && timer - c.dt < 0.15) {
                        val g = o.pos - o.heading * (0.22 * o.lengthCm)
                        val bed = sand.surfaceY(g.x, g.z)
                        sand.puff(g.x, max(bed + 0.5, g.y - 0.3), g.z, 8, 5.0, -fwd.x * 1.5, -fwd.z * 1.5, finesShare = 0.5)
                        sand.deposit(g.x, g.z, MOUTHFUL_CM3 - 8 * SandBed.GRAIN_CM3, 1.8)
                        eatMouthful(c)
                        dustCorals(c, g)
                        puffs++
                    }
                    if (timer >= 0.5) {
                        o.gillRate = 1.0
                        left--
                        val next = Vec3(p.x + fwd.x * 2.0, hoverY(p.x + fwd.x * 2.0, p.z + fwd.z * 2.0), p.z + fwd.z * 2.0)
                        // the shuffle would run it into the rock foot: this station is done
                        if (c.rock.sdf.dist(next) < 1.5) left = 0
                        if (left > 0) {
                            // shuffle on a little and take the next mouthful
                            station = Vec3(p.x + fwd.x * c.rng.range(0.5, 2.0), 0.0, p.z + fwd.z * c.rng.range(0.5, 2.0)).let { q ->
                                val x = q.x.coerceIn(5.0, c.rock.lengthCm - 5.0); val z = q.z.coerceIn(4.0, c.rock.widthCm - 2.0)
                                Vec3(x, hoverY(x, z), z)
                            }
                            phase = 1; timer = 0.0
                        } else {
                            stations++
                            finished = true
                        }
                    }
                }
            }
        }
        override fun exit(c: Ctx) { c.o.gillRate = 1.0 }
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
            // at the burrow mouth (under the rock, in its own pit) it is working the burrow: an allowed contact
            // (and on the way out, up to 1.5 L, while any of the body is still in the sand or rock round the mouth)
            val db = o.pos.distanceTo(burrow)
            if (db < 0.8 * o.lengthCm || db < 1.5 * o.lengthCm && o.posture == 3 && c.sim.bodies.sectionDepth(o) > 0.0) out.posture = 3
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
