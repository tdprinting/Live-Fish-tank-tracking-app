package com.reeflive.core.ai

import com.reeflive.core.geom.Vec3
import com.reeflive.core.geom.smoothstep
import com.reeflive.core.sim.BrainKind
import com.reeflive.core.sim.Counters
import com.reeflive.core.sim.Ev
import com.reeflive.core.sim.Evidence
import com.reeflive.core.sim.FoodParticle
import com.reeflive.core.sim.HealthModel
import com.reeflive.core.sim.Organism
import com.reeflive.core.sim.PodPredator
import com.reeflive.core.sim.SandBed
import com.reeflive.core.sim.SquidRig
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Hummingbird bobtail squid, *Euprymna berryi* (Sepiolidae; docs/research/cephalopod_squid.md). Sandbox only.
 *
 * - **Day:** buried in the sand with only the eyes out (Jolly 2022, Hanlon 1997, Shears 1988). It blows a hollow with
 *   the funnel, settles in and throws sand over its back with the arms; it stays put for small disturbances.
 * - **Night:** emerges and hovers over the sand and the rock face on its fins, arms spread, glowing faintly from the
 *   ventral light organ to match the downwelling light (counter-illumination, Jones & Nishiguchi 2004). At lights-on
 *   it vents most of its *Vibrio fischeri* symbionts (Nyholm & McFall-Ngai 2021) and the glow builds again by dusk.
 * - **Hunting:** stalks mysids, amphipods and small shrimp, raises the first arm pair, then shoots the two tentacles
 *   (Hanlon 1997: rapid forward attack). Pod-prey success follows the tank's microfauna ([PodPredator.BOBTAIL_SQUID]).
 *   Small or freshly molted shrimp and tiny hermits are real prey; fish slip out of its suckers (Hanlon 1997).
 * - **Escape:** blanch → ink → backward jet away from the threat (Hanlon 1997: "swift backwards jet escape with
 *   inking"), then back to the sand. Very shy of the hawkfish, dottyback and moray.
 * - **Colour:** chromatophores darken when alarmed or hunting, pale when stalking, "passing clouds" while it hovers.
 */
class BobtailSquidBrain(c: Ctx) : SpeciesBrain(c) {
    companion object {
        /** Burial takes several seconds of funnel-blowing and arm-sweeping (s; Shears 1988 describes it, timing est.). */
        @Ev(Evidence.DESIGN_ESTIMATE, "cephalopod_squid §5 burial sequence") const val BURY_S = 8.0
        /** Eyes stay above the sand: depth of the mantle centre below the surface as a fraction of the body depth. */
        const val BURY_DEPTH = 0.32
        /** Sand over the mantle once buried (cm): a thin layer, the eyes stay out. */
        const val COVER_CM = 0.4
        /** Steepest it pitches its body while swimming (sin of the angle): it swims level on its fins. */
        const val MAX_PITCH = 0.35
        /** Strike reach (BL, mantle tip to tentacle club) and the stalk distance it closes to before striking. */
        @Ev(Evidence.ANALOGY, "cephalopod_squid §5 Hanlon 1997 paralarval attack 12 BL/s; adult reach est.") const val STRIKE_BL = 0.9
        /** Hunger relief of one pod-sized catch (minutes of hunger rise) at pod index 1 (design estimate). */
        @Ev(Evidence.DESIGN_ESTIMATE) const val POD_YIELD_MIN = 40.0
        /** Chance a tentacle strike on a mysid or amphipod connects in a mature tank (design estimate). */
        @Ev(Evidence.DESIGN_ESTIMATE) const val POD_STRIKE_P = 0.6
        /** Shrimp this much shorter than the squid are prey (molting ones up to its own length). */
        @Ev(Evidence.DESIGN_ESTIMATE, "cephalopod_squid §4 Jolly 2022 feeds glass shrimp; size rule est.") const val SHRIMP_PREY_FRAC = 0.75
        /** Ink reserve: one full ink puff, refilled in about an hour (design estimate). */
        @Ev(Evidence.DESIGN_ESTIMATE) const val INK_REFILL_S = 3600.0
        /** Threat radii (BL of the squid): hawkfish and moray, dottyback, banded shrimp at night. */
        @Ev(Evidence.DESIGN_ESTIMATE, "cephalopod_squid §4") const val HAWK_WARY_BL = 3.5
        const val DOTTY_WARY_BL = 2.0
        const val CBS_WARY_BL = 1.2
        /** Symbiont light: fraction vented at dawn and the regrowth time constant (h). */
        @Ev(Evidence.SOURCED, "cephalopod_squid §5 Nyholm & McFall-Ngai 2021: ~70-95 % vented at dawn") const val VENT_FRACTION = 0.9
        @Ev(Evidence.DESIGN_ESTIMATE) const val SYMBIONT_REGROW_H = 6.0
        /** Abstract mode: hunger relief per night minute (units of the hunger rise) at pod index 1. */
        @Ev(Evidence.DESIGN_ESTIMATE) const val ABSTRACT_RELIEF = 2.6
    }

    val rig: SquidRig = o.limbRig as? SquidRig ?: SquidRig()
    /** Where it buries by day: open sand near cover, away from the hawkfish perches, deep enough to dig into. */
    var bed: Vec3; private set
    /** The way it lies in its bed (unit, along x): its trench runs back from the head that way. */
    private var bedDir = Vec3.X
    var buried = false; private set
    /** 0..1 light-organ symbiont population (vented at lights-on, regrows through the day). */
    var symbionts = 1.0; private set
    var inkReserve = 1.0; private set

    // ---- counters (tests, captions) ----
    var burials = 0; private set
    var strikes = 0; private set
    var podCatches = 0; private set
    var kills = 0; private set
    var jets = 0; private set
    var inkPuffs = 0; private set
    var nightsHovering = 0.0; private set
    private var lastLightsOn = -1e9

    init {
        // research cephalopod_squid: lab squid are trained onto thawed shrimp in about a week; many never take dead food
        o.acceptsPrepared = c.rng.chance(0.5)
        bed = findBed(c)
        install(Buried(), Jet(), Hunt(), TargetFed(), Hover())
    }

    private val sand: SandBed get() = c.sim.sand

    private fun bodyHalfDepth() = 0.5 * o.species.depthRatio * o.lengthCm

    /** Mantle length (cm): the model is mantle + head + arms; the mantle is about 60 % of it. */
    val mantleCm: Double get() = 0.6 * o.lengthCm

    private fun hawks(c: Ctx) = c.sim.organisms.filter { it.alive && (it.brainKind == BrainKind.HAWK || it.brainKind == BrainKind.EEL) }

    /** A sand spot: open, near rock cover, far from hawk perches. */
    private fun findBed(c: Ctx): Vec3 {
        val r = c.rock
        var best: Vec3? = null
        var bs = -1e9
        val hs = hawks(c).map { it.homePos }
        for (i in 0 until 60) {
            val x = c.rng.range(8.0, r.lengthCm - 8.0)
            val z = c.rng.range(5.0, r.widthCm - 3.0)
            val p = Vec3(x, sand.surfaceY(x, z), z)
            val d = r.sdf.dist(p + Vec3.Y * 1.5)
            if (d < 3.0 || !fitsAt(x, z, 1.0) && !fitsAt(x, z, -1.0)) continue
            val s = -0.08 * d + r.sdf.cover(p + Vec3.Y * 2.0) + hs.sumOf { min(it.distanceTo(p), 40.0) * 0.02 } + 0.3 * c.rng.nextDouble()
            if (s > bs) { bs = s; best = p }
        }
        return best ?: Vec3(r.lengthCm * 0.3, sand.surfaceY(r.lengthCm * 0.3, r.widthCm * 0.6), r.widthCm * 0.6)
    }

    /** The trench it digs: the mantle and head, a body depth wide, the body less a little, under a thin cover. */
    private val slotLen: Double get() = 0.8 * o.lengthCm
    private val slotW: Double get() = o.species.depthRatio * o.lengthCm
    private val slotThick: Double get() = 0.8 * slotW
    private fun slotHead(x: Double, z: Double, ux: Double) = Vec3(x + ux * 0.5 * slotLen, 0.0, z)
    private fun fitsAt(x: Double, z: Double, ux: Double): Boolean {
        val h = slotHead(x, z, ux)
        return sand.canBury(h.x, h.z, ux, 0.0, slotLen, slotW, slotThick, COVER_CM)
    }

    /**
     * Buried: the head at the front of its trench with the eyes at the sand line, the mantle under the cover. Without
     * a trench (abstract mode, or the sand was too shallow) BURY_DEPTH of the body sits below the live surface.
     */
    private fun buriedPos(): Vec3 {
        sand.buriedOf(o.id)?.let { return Vec3(it.headX, it.bodyY + 0.35 * it.thickCm, it.headZ) }
        return Vec3(bed.x, sand.surfaceY(bed.x, bed.z) - BURY_DEPTH * bodyHalfDepth() * 2 + bodyHalfDepth(), bed.z)
    }

    /** Starts the trench at the bed (null when the sand there is too shallow now). */
    private fun startTrench(): com.reeflive.core.sim.SandBed.BuriedBody? {
        sand.buriedOf(o.id)?.let { return it }
        val ux = if (fitsAt(bed.x, bed.z, bedDir.x)) bedDir.x else -bedDir.x
        bedDir = Vec3(ux, 0.0, 0.0)
        val h = slotHead(bed.x, bed.z, ux)
        return sand.startBurial(o.id, h.x, h.z, ux, 0.0, slotLen, slotW, slotThick, COVER_CM)
    }

    /**
     * One step of the trench: dug to [digTo], covered to [coverTo] ([SandBed.buryStep]). The funnel blows the sand out
     * to the sides, not ahead of the eyes, so what landed in front is moved to the side rims (the cover comes back
     * from there).
     */
    private fun dig(b: com.reeflive.core.sim.SandBed.BuriedBody, digTo: Double, coverTo: Double) {
        sand.buryStep(b, digTo, coverTo)
        if (b.rim[0] <= 1e-6) return
        val t = sand.takeExact(b.rimX(0), b.rimZ(0), b.rim[0], 1.0)
        b.rim[0] -= t
        for (k in 1..2) { sand.depositExact(b.rimX(k), b.rimZ(k), 0.5 * t, 1.0); b.rim[k] += 0.5 * t }
    }

    /** Lowest the head may go so no part of the body is in the sand (the body trails straight back from the head). */
    private fun clearY(p: Vec3, h: Vec3): Double {
        val back = h.horizontal().normalized(Vec3.X)
        var y = -1e9
        for (k in 0..4) {
            val s = k / 4.0 * o.lengthCm
            val q = p - back * s
            // the body may tilt: whatever the tail drops below the head adds to the clearance
            y = max(y, sand.surfaceY(q.x, q.z) + bodyHalfDepth() + 0.3 + max(0.0, h.y) * s)
        }
        return y
    }

    /**
     * After steering: out of the sand, nothing goes into it. Squid swim level (Hanlon 1997), so the pitch is held
     * shallow, and the head is lifted until the whole body clears the live sand surface.
     */
    override fun postSteer(c: Ctx) {
        if (buried || o.posture == 5 || !o.alive) return
        var h = o.heading
        if (kotlin.math.abs(h.y) > MAX_PITCH) {
            val flat = h.horizontal().normalized(Vec3.X)
            h = Vec3(flat.x, if (h.y > 0) MAX_PITCH else -MAX_PITCH, flat.z).normalized(flat)
        }
        val floor = clearY(o.pos, h)
        if (o.pos.y >= floor && h === o.heading) return
        o.heading = h
        if (o.pos.y < floor) {
            o.pos = Vec3(o.pos.x, min(floor, c.rock.waterLevelCm - 1.0), o.pos.z)
            if (o.vel.y < 0) o.vel = Vec3(o.vel.x, 0.0, o.vel.z)
        }
        o.spline.reset(o.pos, o.heading)
    }

    override fun restSpot(c: Ctx, night: Boolean): Rest =
        if (night) Rest(c.safe(Vec3(bed.x, c.zoneY(0.2), bed.z)), o.heading.horizontal().normalized(Vec3.X))
        else Rest(buriedPos(), o.heading.horizontal().normalized(Vec3.X), Vec3.Y, posture = 5)

    override fun initialPlace(c: Ctx) {
        super.initialPlace(c)
        if (!c.p.night) { buried = true; o.posture = 5 }
    }

    /** Predators it watches for (SpeciesBrain's table has no rows for a squid): any hawkfish or moray close by, the
     * dottyback nearer, a banded shrimp at night, and anything big charging at it. */
    override fun findThreat(c: Ctx): Organism? {
        var best: Organism? = null
        var bd = Double.MAX_VALUE
        val L = o.lengthCm
        for (n in c.p.neighbors(o.pos, 40.0)) {
            if (n === o || !n.alive) continue
            val r = when (n.brainKind) {
                BrainKind.HAWK, BrainKind.EEL -> HAWK_WARY_BL * L
                BrainKind.DOTTYBACK -> DOTTY_WARY_BL * L
                BrainKind.BANDED_SHRIMP -> if (c.p.night) CBS_WARY_BL * L else 0.0
                else -> if (n.isFish && n.lengthCm > 1.5 * L) 1.2 * L else 0.0
            } * (if (buried) 0.4 else 1.0)
            if (r <= 0) continue
            val d = n.pos.distanceTo(o.pos)
            if (d >= r || d >= bd) continue
            val closing = n.vel dot (o.pos - n.pos).normalized(Vec3.Y)
            // the hawk only has to be close; others must be coming at it
            if (n.brainKind != BrainKind.HAWK && closing < 1.0 && d > 0.5 * r) continue
            bd = d; best = n
        }
        return best
    }

    override fun preTick(c: Ctx) {
        super.preTick(c)
        val dt = c.dt
        // dawn venting of the light-organ symbionts, regrowth over the day
        if (c.p.lightsOnAt > lastLightsOn && c.t - c.p.lightsOnAt < 120) {
            lastLightsOn = c.p.lightsOnAt
            symbionts *= 1 - VENT_FRACTION
        }
        symbionts += (1 - symbionts) * min(1.0, dt / (SYMBIONT_REGROW_H * 3600))
        inkReserve = min(1.0, inkReserve + dt / INK_REFILL_S)
        rig.driftInk(c.sim.flow.velocityFast(rig.inkPos), dt)
        // defaults each tick; behaviours override
        rig.glowTarget = if (c.p.night && !buried) symbionts * (0.25 + 0.75 * smoothstep(0.0, 0.05, c.p.light)) else 0.0
        rig.cloudTarget = 0.0
        rig.tentacleTarget = 0.0
        rig.armRaiseTarget = 0.0
        val wary = o.waryLevel.coerceIn(0.0, 1.0)
        rig.darkTarget = 0.35 + 0.3 * wary + 0.2 * o.stress
    }

    override fun onTap(c: Ctx, at: Vec3, s: Double) {
        // buried, it trusts its sand cover unless the tap is close and hard
        if (buried && (s < 0.6 || at.distanceTo(o.pos) > 15)) { rig.darkTarget = 0.6; return }
        super.onTap(c, at, s)
    }

    override fun onShadow(c: Ctx, at: Vec3) {
        shadowAt = c.t
        if (!buried && o.skittishness > 0.5) { startleAt = c.t; startleFrom = at; startleMag = 0.3 }
    }

    private var lastAbstractT = Double.NaN

    override fun abstractPose(c: Ctx, out: SteerIntent) {
        super.abstractPose(c, out)
        val night = c.p.night
        buried = !night
        // abstract steps cover up to a minute of sim time while the behaviour dt is capped: use the sim clock
        val now = c.sim.simTimeSec
        val dtS = if (lastAbstractT.isNaN()) 0.0 else (now - lastAbstractT).coerceIn(0.0, 120.0)
        lastAbstractT = now
        if (night) {
            // hunting as a rate (the pods it takes come out of the microfauna model's predator row)
            val prey = c.sim.water.micro.podPreyIndex(PodPredator.BOBTAIL_SQUID).coerceIn(0.0, 1.5)
            o.hunger = max(0.0, o.hunger - HealthModel.hungerRatePerMin(o, c.sim.water.s.tempC) * ABSTRACT_RELIEF * prey * dtS / 60)
            nightsHovering += dtS
        }
    }

    // ================================================================ behaviours

    /** Day: fly to the bed, blow a hollow, settle in and sweep sand over the mantle; eyes out. */
    inner class Buried : Behavior("bury", "Buried in the sand") {
        private var phase = 0
        private var timer = 0.0
        private var nextPuff = 0.0
        override val committed: Boolean get() = phase == 1
        override val label: String get() = when (phase) { 0 -> "Heading for the sand"; 1 -> "Burying itself in the sand"; else -> "Buried, eyes out" }
        override fun score(c: Ctx): Double {
            val day = 1 - c.activity
            if (buried) return (0.9 * day + 0.3 * o.waryLevel).coerceAtMost(0.93)
            return (0.85 * day + 0.3 * o.waryLevel + (if (c.t - startleAt < 60 && !c.p.night) 0.2 else 0.0)).coerceAtMost(0.93)
        }
        override fun enter(c: Ctx) {
            timer = 0.0
            phase = if (buried) 2 else 0
            // a scare far from the bed: bury where it is if there is open sand
            if (!buried && o.pos.horizontal().distanceTo(bed.horizontal()) > 30) {
                val here = Vec3(o.pos.x, sand.surfaceY(o.pos.x, o.pos.z), o.pos.z)
                if (c.rock.sdf.dist(here + Vec3.Y * 1.5) > 3.0 && (fitsAt(here.x, here.z, 1.0) || fitsAt(here.x, here.z, -1.0))) bed = here
            }
            bedDir = Vec3(if (o.heading.x >= 0) 1.0 else -1.0, 0.0, 0.0)
        }
        override fun update(c: Ctx, out: SteerIntent) {
            timer += c.dt
            val top = Vec3(bed.x, sand.surfaceY(bed.x, bed.z) + bodyHalfDepth() + 1.0, bed.z)
            when (phase) {
                0 -> {
                    out.seek(c.viaOpenWater(top), o.cruiseSpeed * 1.2, arrive = 2.0, urgency = 0.4)
                    out.hover = true
                    rig.armSpreadTarget = 0.2
                    if (o.pos.distanceTo(top) < 2.5 || timer > 40) {
                        if (startTrench() == null) { bed = findBed(c); timer = 0.0; return }
                        phase = 1; timer = 0.0; nextPuff = 0.0
                    }
                }
                1 -> {
                    // the funnel blows a trench (the sand thrown to the rim), it settles in as it deepens, then the
                    // arms sweep the sand back over the mantle: the body goes into a hole it made, never through sand
                    val k = smoothstep(0.0, BURY_S, timer)
                    val body = sand.buriedOf(o.id)
                    if (body != null) dig(body, min(1.0, timer / (0.6 * BURY_S)), (timer / BURY_S - 0.6) / 0.4)
                    val p = top.lerp(buriedPos(), smoothstep(0.0, 0.6 * BURY_S, timer))
                    out.place(p, bedDir, Vec3.Y, 4.0)
                    out.posture = 5
                    rig.armSpreadTarget = 0.8
                    rig.armRaiseTarget = 0.6 * sin(timer * 4.0).coerceAtLeast(0.0)
                    rig.jet = 0.15
                    if (timer >= nextPuff) {
                        nextPuff = timer + c.rng.range(0.6, 1.4)
                        sand.puff(bed.x, sand.surfaceY(bed.x, bed.z) + 0.3, bed.z, 4, 3.0, finesShare = 0.5)
                        sand.sift(bed.x, bed.z, 1.5, 0.2)
                    }
                    if (timer >= BURY_S) {
                        sand.buriedOf(o.id)?.let { dig(it, 1.0, 1.0) }
                        phase = 2; buried = true; burials++
                    }
                }
                else -> {
                    // already buried when it arrived (placed by day): its trench is made at once
                    if (sand.buriedOf(o.id) == null) startTrench()?.let { dig(it, 1.0, 1.0) }
                    out.place(buriedPos(), bedDir, Vec3.Y, 3.0)
                    out.posture = 5
                    o.sleeping = c.activity < 0.5
                    rig.armSpreadTarget = 0.1
                    rig.darkTarget = 0.45 + 0.2 * o.waryLevel
                }
            }
        }
        override fun exit(c: Ctx) {
            if (sand.buriedOf(o.id) != null) {
                // emerging: it pushes up out of its trench, throwing the cover aside, and shakes off a puff of sand
                sand.emergeFrom(o.id, burstShare = 1.0)
                sand.puff(bed.x, sand.surfaceY(bed.x, bed.z) + 0.5, bed.z, 6, 4.0, finesShare = 0.5)
            }
            buried = false
            phase = 0
        }
    }

    /** Night: hover and cruise low on the fins, arms spread, passing clouds over the mantle; keeps away from the hawk. */
    inner class Hover : Behavior("hover", "Hovering on its fins") {
        private var wp = Vec3.ZERO
        private var next = 0.0
        private var pause = 0.0
        override fun score(c: Ctx): Double = 0.45 * c.activity + 0.05
        override fun enter(c: Ctx) { next = 0.0 }
        override fun update(c: Ctx, out: SteerIntent) {
            nightsHovering += c.dt
            if (c.t > next) {
                next = c.t + c.rng.range(6.0, 18.0)
                pause = c.t + c.rng.range(1.0, 5.0)
                wp = pickWaypoint(c)
            }
            if (c.t < pause) {
                // hold station: fins ripple, arms open
                out.hover = true
                out.faceDir = headingTo(o.pos, wp, o.heading).horizontal().normalized(o.heading)
                out.seek(o.pos, 0.0, arrive = 1.0)
            } else {
                out.seek(c.viaOpenWater(wp), o.cruiseSpeed, arrive = 3.0, urgency = 0.2)
                out.hover = true
            }
            rig.armSpreadTarget = 0.55
            rig.cloudTarget = 0.45
            rig.darkTarget = 0.3 + 0.25 * o.waryLevel
        }

        private fun pickWaypoint(c: Ctx): Vec3 {
            val hs = hawks(c)
            var best = o.pos
            var bs = -1e9
            for (i in 0 until 10) {
                val p = c.safe(Vec3(
                    (bed.x + c.rng.signed() * o.species.homeRadiusCm).coerceIn(6.0, c.rock.lengthCm - 6.0),
                    c.zoneY(c.rng.range(o.species.zone.yMin + 0.05, o.species.zone.yMax)),
                    c.rng.range(5.0, c.rock.widthCm - 3.0)))
                val s = hs.sumOf { -1.0 / (1.0 + it.pos.distanceTo(p) / 10.0) } + 0.2 * c.rng.nextDouble()
                if (s > bs) { bs = s; best = p }
            }
            return best
        }
    }

    /**
     * Night hunting: pick a prey (a small shrimp or hermit in range, else a mysid or amphipod over the sand or rock),
     * stalk to within a strike, raise the first arms, shoot the tentacles, then handle and eat.
     */
    inner class Hunt : Behavior("hunt", "Hunting") {
        private var phase = 0
        private var timer = 0.0
        private var prey: Organism? = null
        private var spot = Vec3.ZERO
        private var cooldown = 0.0
        private var handleS = 0.0
        private var caught = false
        override val committed: Boolean get() = phase >= 2
        override val targetVerb: String? get() = if (prey != null) "stalking" else null
        override val label: String get() = when (phase) {
            0 -> "Stalking prey"
            1 -> "Arms raised, about to strike"
            2 -> "Shooting out its tentacles"
            else -> if (caught) "Eating its catch" else "Missed"
        }
        override fun score(c: Ctx): Double {
            if (c.t < cooldown || o.hunger < 0.25 || c.activity < 0.4) return 0.0
            if (HealthModel.appetite(o) < 0.3) return 0.0
            return (0.45 + 0.45 * o.hunger) * c.activity
        }
        override fun enter(c: Ctx) {
            phase = 0; timer = 0.0; caught = false
            prey = if (c.sim.predationEnabled) choosePrey(c) else null
            o.focusId = prey?.id
            spot = prey?.pos ?: podSpot(c)
        }
        override fun update(c: Ctx, out: SteerIntent) {
            timer += c.dt
            val p = prey
            if (p != null && (!p.alive || p !in c.sim.organisms)) { finish(c, 20.0); return }
            val target = p?.pos ?: spot
            val toT = target - o.pos
            val d = toT.length()
            val reach = STRIKE_BL * o.lengthCm
            when (phase) {
                0 -> {
                    // slow, pale approach; stops short of the prey
                    val stand = target - toT.normalized(o.heading) * (0.8 * reach)
                    out.seek(c.viaOpenWater(stand), o.cruiseSpeed * 0.7, arrive = 2.0, urgency = 0.2)
                    out.hover = true
                    out.faceDir = toT.normalized(o.heading)
                    rig.darkTarget = 0.15
                    rig.armSpreadTarget = 0.35
                    if (d < 1.15 * reach && Vec3.angle(o.heading, toT) < 0.6) { phase = 1; timer = 0.0 }
                    if (timer > 45 || d > 60) finish(c, 30.0)
                }
                1 -> {
                    // poised: the first arm pair up, a dark flash before the strike
                    out.place(o.pos, toT.normalized(o.heading), null, 6.0)
                    rig.armRaiseTarget = 1.0
                    rig.armSpreadTarget = 0.2
                    rig.darkTarget = 0.6
                    if (timer > 0.6) { phase = 2; timer = 0.0; strikes++; o.mouth = 1.0 }
                }
                2 -> {
                    // tentacle strike with a short lunge (mantle jet)
                    rig.tentacleTarget = 1.0
                    rig.armRaiseTarget = 1.0
                    rig.jet = 0.4
                    val lunge = o.pos + toT.normalized(o.heading) * min(d * 0.4, 0.3 * o.lengthCm)
                    out.place(c.safe(lunge, 0.5), toT.normalized(o.heading), null, 10.0)
                    if (timer > 0.3) {
                        caught = resolveStrike(c, p, d <= 1.3 * reach)
                        phase = 3; timer = 0.0
                        handleS = if (caught) (if (p != null) c.rng.range(20.0, 40.0) else c.rng.range(4.0, 12.0)) else 1.0
                    }
                }
                else -> {
                    // handling: arms wrapped round the prey, tentacles drawn back; drifts down a little
                    val down = o.pos + Vec3.Y * (-0.3 * c.dt)
                    out.place(down.withY(max(down.y, clearY(down, o.heading))), o.heading, null, 2.0)
                    rig.armSpreadTarget = 0.0
                    rig.darkTarget = if (caught) 0.55 else 0.3
                    if (timer > handleS) finish(c, if (caught) c.rng.range(60.0, 180.0) else c.rng.range(5.0, 15.0))
                }
            }
        }
        private fun finish(c: Ctx, cool: Double) { cooldown = c.t + cool; finished = true; prey = null }
        override fun exit(c: Ctx) { prey = null; phase = 0 }

        /** Small shrimp (or a molting one), tiny hermits; nothing it cannot hold (fish slip out of its suckers). */
        private fun choosePrey(c: Ctx): Organism? {
            var best: Organism? = null
            var bs = 0.0
            for (n in c.p.neighbors(o.pos, 25.0)) {
                val w = preyWeight(n)
                if (w <= 0 || !n.visible || n.posture == 3) continue
                val s = w / (1 + n.pos.distanceTo(o.pos) / 10)
                if (s > bs) { bs = s; best = n }
            }
            return best
        }

        /** A mysid or amphipod somewhere near: over the sand or just off the rock face. */
        private fun podSpot(c: Ctx): Vec3 {
            for (i in 0 until 8) {
                val p = o.pos + c.rng.unitVector() * c.rng.range(5.0, 18.0)
                val q = if (c.rng.chance(0.5)) Vec3(p.x, sand.surfaceY(p.x, p.z) + 1.0, p.z) else p
                if (c.rock.inWater(q, 1.5) && c.rock.sdf.dist(q) > 0.8) return c.safe(q, 0.8)
            }
            return c.safe(o.pos + o.heading * 8.0)
        }

        private fun resolveStrike(c: Ctx, p: Organism?, inReach: Boolean): Boolean {
            if (p == null) {
                val idx = c.sim.water.micro.podPreyIndex(PodPredator.BOBTAIL_SQUID).coerceIn(0.0, 1.5)
                if (!inReach || !c.rng.chance(POD_STRIKE_P * min(1.0, idx))) return false
                podCatches++
                o.hunger = max(0.0, o.hunger - HealthModel.hungerRatePerMin(o, c.sim.water.s.tempC) * POD_YIELD_MIN)
                o.counters.add(Counters.K.FEED, c.t)
                return true
            }
            if (!inReach || !c.rng.chance(0.55)) {
                c.sim.agentOf(p)?.let { it.brain.onAttacked(it.ctx, o.pos, 0.6, o) }
                return false
            }
            kills++
            c.sim.eaten(o, p)
            // a squid meal is small next to a moray's: hungry again the next night
            o.satietyUntil = c.t + 6 * 3600.0
            o.counters.add(Counters.K.FEED, c.t)
            return true
        }
    }

    /** Prey weight for the squid's hunt: 0 = never. */
    fun preyWeight(n: Organism): Double {
        if (!n.alive || n === o) return 0.0
        val L = o.lengthCm
        val soft = n.molting || (!o.clock.isNaN() && o.clock - n.lastMoltAt < 2 * 3600)
        return when (n.brainKind) {
            BrainKind.CLEANER_SHRIMP, BrainKind.BANDED_SHRIMP ->
                if (n.lengthCm < SHRIMP_PREY_FRAC * L || soft && n.lengthCm < 1.1 * L) 1.0 else 0.0
            BrainKind.HERMIT -> if (n.lengthCm < 0.35 * L) 0.25 else 0.0
            else -> 0.0
        }
    }

    /** Hand-fed or drifting frozen food, for a squid that learned to take it (research cephalopod_squid: about a week of training). */
    inner class TargetFed : Feed(this@BobtailSquidBrain, 30.0, "Snatching food with its tentacles") {
        override fun accept(c: Ctx, f: FoodParticle): Boolean = !f.settled || f.pos.y < c.rock.sandTopCm + 6.0
        override fun scoreMul(c: Ctx, f: FoodParticle): Double = if (buried && !c.p.night && o.hunger < 0.75) 0.0 else 1.0
        override fun update(c: Ctx, out: SteerIntent) {
            super.update(c, out)
            out.hover = true
            target?.let { t ->
                if (t.pos.distanceTo(o.pos) < 1.5 * o.lengthCm) { rig.tentacleTarget = 1.0; rig.armRaiseTarget = 0.8 }
            }
        }
    }

    /**
     * Startled or a predator closing: blanch, ink, jet backwards away from the threat with the arms toward it, then
     * hover in cover (it goes back to the sand by day).
     */
    inner class Jet : Behavior("jet", "Jetting away") {
        private var from = Vec3.ZERO
        private var to = Vec3.ZERO
        private var face = Vec3.X
        private var phase = 0
        private var timer = 0.0
        private var dur = 6.0
        override val committed: Boolean get() = phase < 2 && timer < 2.0
        override val label: String get() = if (phase == 0) "Inking and jetting away" else "Hiding after a scare"
        override fun score(c: Ctx): Double {
            threat?.let { if (it.alive) return 0.97 }
            if (startledRecently && startleMag > 0.25) return 0.96
            return 0.0
        }
        override fun enter(c: Ctx) {
            val t = threat
            from = t?.pos ?: startleFrom
            startleAt = -1e9
            phase = 0; timer = 0.0
            dur = c.rng.range(8.0, 25.0) * (0.5 + o.skittishness)
            face = (from - o.pos).normalized(o.heading)
            val away = (o.pos - from).normalized(o.heading * -1.0)
            // a jet of 6-10 body lengths, mantle first, kept off the rock and glass
            var best = c.safe(o.pos + away * (6.0 * o.lengthCm))
            for (k in 0 until 6) {
                val dir = (away + c.rng.unitVector() * 0.5).normalized(away)
                val cand = c.safe(o.pos + dir * (c.rng.range(6.0, 10.0) * o.lengthCm))
                if (c.rock.sdf.segmentClear(o.pos, cand, 0.5) && cand.distanceTo(from) > best.distanceTo(from)) best = cand
            }
            to = best
            jets++
            buried = false
            // the ink puff: a dark pseudomorph left where it was, if it has ink
            if ((t != null || startleMag > 0.3) && inkReserve > 0.3) {
                rig.ink(o.pos + face * (-0.2 * o.lengthCm), mantleCm)
                inkReserve -= 0.7
                inkPuffs++
            }
            o.startle = max(o.startle, 0.3)
            o.counters.add(Counters.K.FLASH, c.t)
        }
        override fun update(c: Ctx, out: SteerIntent) {
            timer += c.dt
            val t = threat
            if (t != null && t.alive) from = t.pos
            if (phase == 0) {
                // blanch (pale) then the jet: arms bundled toward the threat, mantle leading away
                rig.jet = 1.0
                rig.darkTarget = if (timer < 0.15) 0.0 else 0.8
                out.place(to, face, null, 3.5)
                if (timer > 1.2 || o.pos.distanceTo(to) < 1.0) { phase = 1; timer = 0.0 }
            } else {
                // hover low near cover, dark and still; back to fins
                val hide = c.safe(Vec3(to.x, min(to.y, c.zoneY(0.25)), to.z))
                out.seek(hide, o.cruiseSpeed, arrive = 2.0, urgency = 0.3)
                out.hover = true
                out.faceDir = (from - o.pos).horizontal().normalized(o.heading)
                rig.darkTarget = 0.7
                rig.armSpreadTarget = 0.2
                if (t != null && t.alive && t.pos.distanceTo(o.pos) < 2.0 * o.lengthCm && timer > 1.0) {
                    // still on its tail: jet again
                    phase = 0; timer = 0.0; enter(c); return
                }
                if (timer > dur && (t == null || !t.alive)) finished = true
            }
        }
    }
}
