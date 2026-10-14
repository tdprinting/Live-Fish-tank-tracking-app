package com.reeflive.core.ai

import com.reeflive.core.geom.Vec3
import com.reeflive.core.geom.smoothstep
import com.reeflive.core.sim.Anchor
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
    /** The owner's clowns host the hairy green mushrooms (Rhodactis) beside the arch (HOST_SHROOM). */
    private val hostName = o.hostAnchor ?: "HOST_SHROOM"
    fun host(c: Ctx): Vec3 = c.rock.anchors.pos(hostName, if (flatHost) Vec3(82.3, 20.3, 15.2) else Vec3(72.0, 40.0, 15.0))
    /**
     * A flat host (corallimorph discs on rock) rather than a branching soft coral or an anemone: the pair hugs the
     * discs, belly 1–3 cm over the tentacles (owner observation), instead of weaving up through branches.
     */
    val flatHost: Boolean = hostName.uppercase().let { "SHROOM" in it || "MUSHROOM" in it || "RHODACTIS" in it }
    fun partner(c: Ctx): Organism? = c.sim.organisms.firstOrNull { it !== o && it.alive && it.brainKind == BrainKind.CLOWN }
    fun isFemale(c: Ctx): Boolean = c.sim.repro.clownIsFemale(o)

    init {
        install(HoverHost(), NestleHost(), ClownFeed(), GlassCurious(this), CleanNestSite(this), LayEggs(this), TendEggs(this),
            Flee(this, { c, _ -> c.safe(host(c) + Vec3(0.0, 2.0, 0.0), 1.5) }, "Darting into the host"),
            VisitCleaner(this, 0.1), SurfaceGasp(this), Flash(this))
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
            }
            if (c.t > nextWiggle) {
                // hosting bout: the clown drops onto the host and shimmies its body against it (the "host-wiggle"
                // tell, research video fish_ocellaris_clownfish [L]); every 20–60 s, 2–4 s long (DESIGN ESTIMATE)
                nextWiggle = c.t + c.rng.range(20.0, 60.0)
                wiggleUntil = c.t + c.rng.range(2.0, 4.0)
                wiggleAt = c.safe(h + Vec3(c.rng.range(-2.0, 2.0), if (flatHost) FLAT_LO else 2.0, c.rng.range(-1.5, 1.0)), 1.0)
            }
            // back from a cleaner visit or a chase across the tank: home over the rock, not into it
            val away = c.o.pos.distanceTo(h) > 4 * L
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
                val d = if (flatHost) Vec3(c.rng.range(-3.0, 3.0), FLAT_LO, c.rng.range(-2.0, 1.5)) else c.rng.unitVector() * 3.0 + Vec3(0.0, 1.5, 0.0)
                out.seek(c.safe(h + d, 1.0), c.o.burstSpeed * 0.5, arrive = 1.0, urgency = 0.5)
            } else {
                out.seek(spot, c.o.cruiseSpeed * 0.8, arrive = if (flatHost) 0.5 * L else L)
            }
            out.waddleAmp = 0.3; out.waddleHz = 1.3
            out.pitchBias = -0.14 * sin(2 * PI * 0.7 * c.t)
            out.hover = true
            out.separation = 0.4
        }
    }

    /** Night: nestled in the host, female closer to the centre. */
    inner class NestleHost : Behavior("nestle", "Nestled in host") {
        override fun score(c: Ctx) = 0.8 * (1 - c.activity)
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
        override fun score(c: Ctx): Double {
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

    companion object {
        /** Flat host: body-centre height above the disc surface, cm (belly 1–3 cm over the disc tops; owner). */
        const val FLAT_LO = 3.6
        const val FLAT_HI = 4.6
        /** Body-shimmy frequency of a hosting wiggle bout, Hz (DESIGN ESTIMATE from owner video). */
        const val WIGGLE_HZ = 3.0
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

    init {
        install(Patrol(), ColumnPick(), Graze(this, GRAZE_WEIGHT), NibbleNori(this, 1.2), TangFeed(), NipCorals(this), PlayDead(),
            Flee(this, { c, _ -> c.safe(sleepAnchor(c)?.pos ?: Vec3(36.0, 25.0, 9.0)) }, "Darting to cover"),
            TangSleep(), VisitCleaner(this, 0.1), Flash(this), GlassCurious(this), SurfaceGasp(this))
    }

    override fun restSpot(c: Ctx, night: Boolean): Rest {
        val a = sleepAnchor(c)
        return if (night && a != null) Rest(c.safe(a.pos, 0.6 * c.o.collisionLength + 0.5), a.dir ?: Vec3.X * -1.0)
        else Rest(c.safe(Vec3(60.0, c.zoneY(0.6), 24.0)), Vec3.X)
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
        /** Play-dead roll, rad (~85°, "rolled 90 deg onto side", [S1][M]) and passive sink rate, cm/s (DESIGN ESTIMATE). */
        const val PLAY_DEAD_ROLL = 1.48
        const val PLAY_DEAD_SINK = 1.5
        /** Chance a hard startle (tap, attack) makes it play dead (DESIGN ESTIMATE; juveniles far more often). */
        const val PLAY_DEAD_JUVENILE = 0.4
        const val PLAY_DEAD_ADULT = 0.05
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

    /** Long sweeps along the front lane, surfing in strong flow. */
    inner class Patrol : Behavior("patrol", "Patrolling") {
        private var wp = Vec3.ZERO
        private var goRight = true
        private var surfUntil = -1.0
        private var nextSurfCheck = 0.0
        override fun score(c: Ctx) = 0.15 + 0.4 * c.activity
        override fun enter(c: Ctx) { pick(c) }
        private fun pick(c: Ctx) {
            val L = c.L
            val p = if (L > 15) c.o.pos + c.rng.unitVector().let { Vec3(it.x, it.y * 0.3, it.z) } * max(0.5 * L, 10.0)
            else {
                goRight = !goRight
                Vec3(if (goRight) c.rng.range(95.0, 108.0) else c.rng.range(12.0, 25.0), c.zoneY(c.rng.range(0.35, 0.9)), c.rng.range(21.0, 29.0))
            }
            wp = c.safe(p)
        }
        override fun update(c: Ctx, out: SteerIntent) {
            val o = c.o
            if (o.pos.distanceTo(wp) < 2 * c.L) pick(c)
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
            out.seek(wp, o.cruiseSpeed * 1.1, arrive = 2 * c.L)
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
        override fun update(c: Ctx, out: SteerIntent) {
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
    private val spots = listOf(Vec3(62.0, 17.0, 15.0), Vec3(28.0, 22.0, 26.0), Vec3(43.0, 22.0, 27.0))
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
        if (night) Rest(c.safe(c.rock.anchors.pos("CAVE_SMALL", Vec3(62.0, 12.0, 8.0)) + Vec3(0.0, 0.0, 6.0)), Vec3.Z * -1.0)
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

    /** Nearly motionless hover beside cover with pectoral sculling, within [dayRadius] of the pocket. */
    inner class HoverCover : Behavior("hovercover", "Hovering by cover") {
        private var p = Vec3.ZERO
        private var next = 0.0
        override fun score(c: Ctx) = 0.45
        override fun update(c: Ctx, out: SteerIntent) {
            if (c.t > next) {
                next = c.t + c.rng.range(5.0, 15.0)
                val r = c.o.hoverRadius * kotlin.math.sqrt(c.rng.nextDouble())
                p = c.safe(schoolTarget(c, spot + c.rng.unitVector() * r))
                if (p.distanceTo(spot) > c.o.hoverRadius + 2) p = c.safe(spot)
            }
            out.seek(p, c.o.cruiseSpeed * 0.3, arrive = 3.0)
            out.hover = true; out.ampMul = 0.3; out.pectoral = 1.0; out.tailMul = 0.4
            if (c.o.brightExposed) out.ampMul = 0.2
        }
    }

    /**
     * Dusk and night (light < [FORAGE_LIGHT]): forage up to 40 cm from the pocket for drifting
     * zooplankton (S:Marnane02 — 64–93 % of intake by dawn), picking particles within 15 cm.
     */
    inner class NocturnalForage : Behavior("dusk", "Night foraging") {
        private var wp = Vec3.ZERO
        private var next = 0.0
        override fun score(c: Ctx): Double = if (c.p.light < FORAGE_LIGHT && !c.o.mouthbrooding) 0.55 else 0.0
        override fun update(c: Ctx, out: SteerIntent) {
            val o = c.o
            if (c.t > next || o.pos.distanceTo(wp) < c.L) {
                next = c.t + c.rng.range(4.0, 9.0)
                wp = c.safe(schoolTarget(c, spot + c.rng.unitVector() * (forageRadius(c) * (0.3 + 0.7 * c.rng.nextDouble()))))
            }
            val f = if (o.hunger > 0.05) c.food.nearest(o.pos, 15.0) { it.kind == FoodKind.PODS || o.species.dietMatch(it.kind) > 0 && o.acceptsPrepared } else null
            if (f != null) {
                out.seek(f.pos, o.cruiseSpeed * 1.4, arrive = 0.8)
                if (o.pos.distanceTo(f.pos) < 1.2 + 0.1 * c.L) c.bite(f)
            } else out.seek(wp, o.cruiseSpeed, arrive = c.L)
            out.ampMul = 0.6
        }
    }

    /** By day faster tankmates leave it little (×0.4 unless the food drifts within 10 cm); dusk/night ×1.5 (06 §3.2). */
    inner class CardinalFeed : Feed(this@CardinalBrain, 60.0) {
        override fun bonus(c: Ctx, f: FoodParticle) = if (f.kind == FoodKind.MYSIS || f.kind == FoodKind.PODS) 0.1 else 0.0
        override fun accept(c: Ctx, f: FoodParticle) = !c.o.mouthbrooding && !f.settled && !f.onSurface
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
    fun forageRadius(c: Ctx): Double = FORAGE_RADIUS * (0.5 + 0.5 * (1 - com.reeflive.core.geom.smoothstep(0.02, FORAGE_LIGHT, c.p.light)))

    companion object {
        const val BASE_RADIUS = 12.0
        const val FORAGE_RADIUS = 40.0
        const val FORAGE_LIGHT = 0.3
    }
}

// =====================================================================================
// Cleaner wrasse (WrasseBrain)
// =====================================================================================
class WrasseBrain(c: Ctx) : SpeciesBrain(c) {
    val stationPos: Vec3 = c.safe(c.rock.anchors.pos("STATION_CLEANER", Vec3(62.0, 20.0, 18.0)), 0.6 * o.collisionLength + 1.0)
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
        val cave = c.rock.anchors.pos("CAVE_SMALL", Vec3(62.0, 12.0, 8.0))
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
            // back to the station over the rock, not into it: a body that cannot pass through rock needs a way round
            val spot = stationPos + Vec3(sin(c.t * 0.5) * 1.5, sin(c.t * 0.31) + bob, 0.0)
            out.seek(if (c.o.pos.distanceTo(stationPos) > 8) c.viaOpenWater(spot) else spot, c.o.cruiseSpeed * (if (dancing) 0.9 else 0.5), arrive = if (dancing) 0.8 else 2.0)
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
            val mean = 6.7 * sqrt(sqrt(client?.let { clientArea(it) } ?: 1.0)).coerceIn(0.8, 1.25)
            val sigma2 = ln(1 + (4.0 / 6.7) * (4.0 / 6.7))
            val mu = ln(mean) - sigma2 / 2
            inspectS = exp(mu + sqrt(sigma2) * c.rng.gaussian()).coerceIn(2.0, 20.0)
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
                    // (the wrasse's body does not fit into the crevice: held at its mouth a little farther out counts too)
                    if (o.pos.distanceTo(cl.pos) < c.L + 0.5 * cl.lengthCm + 1.5 || (phaseT > 2.0 && o.pos.distanceTo(goal) < 2.0) ||
                        (phaseT > 3.0 && o.pos.distanceTo(cl.pos) < c.L + 0.5 * cl.lengthCm + 5.0 && o.vel.length() < 0.3 * o.cruiseSpeed) ||
                        (phaseT > 8.0 && o.pos.distanceTo(cl.pos) < c.L + 0.5 * cl.lengthCm + 6.0)) {
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
                    val keen = (sincePose / POSE_REFRACT_S).coerceAtMost(1.0)
                    val need = (keen * keen + 3 * ichS + cl.parasiteLoad).coerceIn(0.1, 1.0)
                    if (!c.rng.chance((0.3 + 0.6 * trust).coerceIn(0.0, 1.0) * need)) { encounters.refused++; done(c, cl, false); return }
                    encounters.accepted++
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
        const val DANCE_HZ = 2.0
        const val DANCE_BOB_CM = 0.8
        /** Per-client cooldown: 60 s, not 10 min (D:Grutter96). */
        const val CLIENT_COOLDOWN_S = 60.0
        /** A client's keenness to pose recovers (quadratically: it saturates, so poses come at a steady pace) over this long after its last full pose [I]. */
        const val POSE_REFRACT_S = 2250.0
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
        override fun score(c: Ctx) = 0.85 * (1 - c.activity)
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
    val mouth: Vec3 = cave?.pos ?: Vec3(69.0, 5.0, 23.0)
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
                else Vec3(c.rng.range(8.0, 114.0), c.rock.sandTopCm + 4, if (c.rng.chance(0.5)) c.rng.range(26.0, 30.0) else c.rng.range(14.0, 30.0))
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
                o.focusId = p.id   // the jaws may reach the prey's body (BodyCollision.touchAllowed)
                val hit = d < 2.0 + 0.5 * p.lengthCm
                if (hit || c.t - strikeAt > 0.4) {
                    if (hit && c.sim.predationEnabled && c.rng.nextDouble() >= 0.55) {
                        c.sim.eaten(o, p)
                    } else {
                        c.sim.agentOf(p)?.let { it.brain.onAttacked(it.ctx, o.pos, 1.0) }
                        p.startle = max(p.startle, 0.4)
                    }
                    strikeAt = -1.0
                    if (o.focusId == p.id) o.focusId = null
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
    val perches: List<Anchor> = c.rock.anchors.ofType("PERCH").ifEmpty { listOf(Anchor("PERCH_X", "PERCH", Vec3(49.0, 29.0, 21.0), normal = Vec3.Y)) }
    var current: Anchor = perches.minBy { it.pos.distanceTo(Vec3(49.0, 29.0, 21.0)) }

    init {
        install(PerchHop(), Pounce(), DuckToPerch(), SurfaceGasp(this), VisitCleaner(this))
    }

    fun perchPose(a: Anchor): Rest {
        val n = (a.normal ?: Vec3.Y).normalized(Vec3.Y)
        val outward = Vec3(0.0, 0.0, 1.0).rejectFrom(n).normalized((Vec3.X cross n).normalized(Vec3.X))
        return Rest(a.pos + n * (0.3 * o.lengthCm), outward, n, posture = 1)
    }

    fun lowestSheltered(): Anchor = perches.minBy { it.pos.y }

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
        /** Perch dwell, s (5–20 hops/h: DESIGN ESTIMATE, research 10 §5 "not established"). */
        @com.reeflive.core.sim.Ev(com.reeflive.core.sim.Evidence.DESIGN_ESTIMATE, "10 §5") const val DWELL_MIN_S = 120.0
        const val DWELL_MAX_S = 600.0
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
        override fun update(c: Ctx, out: SteerIntent) {
            super.update(c, out)
            val o = c.o
            val pose = perchPose(current)
            val t = threat
            if (elapsed > 0.8 && o.pos.distanceTo(pose.pos) < 4.0 + 0.5 * o.lengthCm && (t == null || t.pos.distanceTo(o.pos) > 8.0)) {
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

    override fun restSpot(c: Ctx, night: Boolean): Rest = perchPose(if (night) lowestSheltered() else current)

    /** Sit on a perch on the pectorals (20–120 s, head turns), then hop to another perch. */
    inner class PerchHop : Behavior("perch", "Perched") {
        private var state = 0               // 0 perched, 1 hopping, 2 settling
        private var dwell = 30.0
        private var since = 0.0
        private var target: Anchor = current
        private var nextLook = 0.0
        private var lookYaw = 0.0
        override fun score(c: Ctx) = 0.5
        override fun enter(c: Ctx) {
            val pose = perchPose(current)
            state = if (c.o.pos.distanceTo(pose.pos) < 2) 0 else 1
            target = if (state == 1) perches.minBy { it.pos.distanceTo(c.o.pos) } else current
            since = c.t; dwell = c.rng.range(DWELL_MIN_S, DWELL_MAX_S)   // 5–20 hops per hour (06 §5.6 #11; ambush economy; DESIGN ESTIMATE)
        }
        override fun update(c: Ctx, out: SteerIntent) {
            val o = c.o
            when (state) {
                0 -> {
                    val pose = perchPose(current)
                    if (c.t > nextLook) {
                        nextLook = c.t + c.rng.range(1.5, 5.0)
                        events.scans++
                        val passer = nearestOther(c, 30.0) { it.isFish }
                        lookYaw = if (passer != null) {
                            val to = (passer.pos - o.pos).rejectFrom(pose.up!!)
                            val sgn = if (((pose.heading cross to) dot pose.up!!) > 0) 1.0 else -1.0
                            (Vec3.angle(pose.heading, to) * sgn).coerceIn(-0.44, 0.44)
                        } else c.rng.signed() * 0.44
                    }
                    val h = Vec3.rotateAbout(pose.heading, pose.up!!, lookYaw)
                    // propped on the thick lower pectoral rays, head up 10–20° (research video fish_falco_hawkfish [S1][M])
                    val side = (h cross pose.up!!).normalized(Vec3.Z)
                    out.place(pose.pos, Vec3.rotateAbout(h, side, PERCH_HEAD_UP), Vec3.rotateAbout(pose.up!!, side, PERCH_HEAD_UP), 6.0)
                    out.posture = 1; out.pectoral = 1.0
                    o.eyeYaw = lookYaw * 0.5
                    o.sleeping = c.p.night
                    if (!c.p.night && c.t - since > dwell) {
                        val others = perches.filter { it !== current }
                        if (others.isNotEmpty()) {
                            target = others.maxBy { a -> 1.0 / (10 + a.pos.distanceTo(o.pos)) + c.rng.nextDouble() * 0.05 + (if (c.food.countNear(a.pos, 20.0) > 0) 0.1 else 0.0) }
                            state = 1; since = c.t; events.departures++
                        }
                    }
                    if (c.p.night && current !== lowestSheltered()) { target = lowestSheltered(); state = 1 }
                }
                1 -> {
                    val pose = perchPose(target)
                    val n = pose.up!!
                    val land = c.safe(pose.pos + n * (c.clearance), c.clearance * 0.8)
                    val dist = o.pos.distanceTo(land)
                    val arc = if (dist > 8) Vec3(0.0, 4.0, 0.0) else Vec3.ZERO
                    out.seek(c.safe(land + arc), 3 * o.lengthCm, arrive = 2.0, urgency = 0.6)
                    if (dist < 2.0) { state = 2; since = c.t }
                    if (c.t - since > 15) { state = 2; since = c.t }
                }
                2 -> {
                    val pose = perchPose(target)
                    out.place(pose.pos, pose.heading, pose.up, 8.0); out.posture = 1
                    out.pitchBias = -0.1
                    if (c.t - since > 0.3 && o.pos.distanceTo(pose.pos) < 0.8) { current = target; state = 0; since = c.t; dwell = c.rng.range(DWELL_MIN_S, DWELL_MAX_S); events.landings++ }
                    if (c.t - since > 3) { current = target; state = 0; since = c.t; events.landings++ }
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
// Hole dwellers: shared hole placement
// =====================================================================================
/** Kinematic in-hole pose: head [headOut] cm outside the mouth along the hole axis, body along the tunnel. */
fun holeRest(c: Ctx, hole: Anchor?, fallback: Vec3, headOut: Double): Rest {
    val pos = hole?.pos ?: fallback
    val dir = (hole?.dir ?: Vec3.Z).normalized(Vec3.Z)
    var head = pos + dir * headOut
    // a hole whose tunnel the baked rock doesn't carve fully: slide the head out until it is in water
    var k = 0
    while (c.rock.sdf.dist(head) < 0.05 && k++ < 40) head += dir * 0.1
    val depth = hole?.depth ?: 6.0
    return Rest(head, dir, Vec3.Y.rejectFrom(dir).normalized(Vec3.Y), posture = 3, spline = listOf(head, pos, pos - dir * depth, pos - dir * (depth + c.L)))
}

/** Go to the hole mouth (swim), then slide in (kinematic). [headOut] < 0 = fully inside. */
open class InHole(val b: SpeciesBrain, var holeName: String, var fallback: Vec3, label: String, val scoreFn: (Ctx) -> Double, val headOutFn: (Ctx) -> Double) : Behavior("hole_$holeName", label) {
    private var inside = false
    override fun score(c: Ctx) = scoreFn(c)
    override fun enter(c: Ctx) { inside = c.o.posture == 3 }
    override fun update(c: Ctx, out: SteerIntent) {
        val hole = c.rock.anchors[holeName]
        val r = holeRest(c, hole, fallback, headOutFn(c))
        val mouth = c.safe((hole?.pos ?: fallback) + r.heading * (c.clearance + 1.0), c.clearance * 0.8)
        if (!inside) {
            out.seek(mouth, c.o.burstSpeed * 0.5, arrive = 1.5, urgency = 0.6)
            if (c.o.pos.distanceTo(mouth) < 2.0) inside = true
            return
        }
        out.rest(r, 5.0)
        c.o.sleeping = c.p.night
        c.o.eyeYaw = 0.35 * sin(2 * PI * 0.5 * c.t + c.o.appendagePhase)
    }
}

// =====================================================================================
// Lawnmower blenny (Salarias fasciatus): perch, hop, constant grazing, territorial to blennies
// =====================================================================================
class LawnmowerBlennyBrain(c: Ctx) : SpeciesBrain(c) {
    val hole: Anchor? = c.rock.anchors["HOLE_LAWNMOWER"] ?: c.rock.anchors["HOLE_BLENNY"]
    val territory: Vec3 get() = hole?.pos ?: Vec3(30.0, 10.0, 11.0)

    init {
        install(RockGraze(),
            InHole(this, hole?.name ?: "HOLE_LAWNMOWER", territory, "Resting in its hole", { c -> 0.8 * (1 - c.activity) + (if (c.o.health < 0.5) 0.3 else 0.0) }, { c -> if (c.p.night) -0.5 else 1.0 }),
            BenthicFeed(this, 35.0),
            Flee(this, { c, _ -> c.safe((hole?.pos ?: territory) + (hole?.dir ?: Vec3.Z) * (c.clearance + 1.0)) }, "Darting home"), SurfaceGasp(this))
    }

    override fun anchorPos(c: Ctx, anchor: String): Vec3? = if (anchor == "hole" || anchor == "patch") territory else super.anchorPos(c, anchor)

    override fun restSpot(c: Ctx, night: Boolean): Rest =
        if (night) holeRest(c, hole, territory, -0.5)
        else c.rockSurfacePoint(territory, 10.0)?.let { (p, n) -> perchOn(p, n) } ?: holeRest(c, hole, territory, 1.0)

    /** Rasps taken (one per second of grazing). */
    var rasps = 0; private set

    companion object {
        /** Hop speed (research video fish_lawnmower_blenny: "3 BL/s per hop" [L]), longest single hop, propped head-up angle (rad). */
        const val HOP_BL_S = 3.0
        const val HOP_MAX_BL = 3.0
        const val HEAD_UP = 0.26
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
        val h = Vec3.Y.rejectFrom(n).let { if (it.length() < 0.2) Vec3.X.rejectFrom(n) else it }.normalized(Vec3.X)
        return Rest(p + n * (0.5 * o.species.depthRatio * o.lengthCm + 0.3), h, n, posture = 1)
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
        private fun pick(c: Ctx) {
            val centre = if (c.o.pos.distanceTo(territory) > 20) territory else c.o.pos
            val sp = c.rockSurfacePoint(centre, c.rng.range(5.0, 15.0), minNormalY = 0.0)
            perch = sp?.let { perchOn(it.first, it.second) }
            state = 1
            until = c.t + 8
            hopT = -1.0
        }
        override fun update(c: Ctx, out: SteerIntent) {
            val p = perch ?: run { pick(c); out.crawl = true; return }
            when (state) {
                1 -> {
                    val o = c.o
                    if (hopT < 0 && o.pos.distanceTo(p.pos) > HOP_MAX_BL * o.lengthCm) {
                        // too far for one hop (back from feeding): skip along the rock on its pectorals
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
                    if (u >= 1.0) { state = 0; hopT = -1.0; until = c.t + c.rng.range(5.0, 20.0) }
                }
                else -> {
                    out.rest(p, 6.0)
                    if (c.t > nextRasp) {
                        nextRasp = c.t + 1.0
                        c.o.hunger = max(0.0, c.o.hunger - 0.006 * matrixQuality(c))
                        rasps++
                        c.o.nutritionGreens = 1.0
                        c.sim.addGrazing(c.sim.grazeRemovalPerMin(c.o) / 30)
                        c.o.mouth = 1.0
                    }
                    // head dips to the rock for each rasp, then lifts back to the propped head-up scan
                    val side = (p.heading cross p.up!!).normalized(Vec3.Z)
                    val pitch = HEAD_UP * smoothstep(0.0, 0.35, c.t - (nextRasp - 1.0)) - 0.1
                    out.kinHeading = Vec3.rotateAbout(Vec3.rotateAbout(p.heading, p.up!!, 0.15 * sin(c.t * 1.7)), side, pitch)
                    out.kinUp = Vec3.rotateAbout(p.up!!, side, pitch)
                    out.pectoral = 1.0
                    if (c.t > until) pick(c)
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

// =====================================================================================
// Tailspot blenny (Ecsenius stigmatura): peeks from its hole, darts up for plankton
// =====================================================================================
class PeekerBlennyBrain(c: Ctx) : SpeciesBrain(c) {
    // the rebuilt rockscape names the tailspot's own hole HOLE_TAILSPOT; HOLE_BLENNY is the lawnmower's
    var hole: Anchor? = c.rock.anchors["HOLE_TAILSPOT"] ?: c.rock.anchors["HOLE_BLENNY"]; private set
    val holePos: Vec3 get() = hole?.pos ?: Vec3(25.0, 18.0, 24.0)
    private var peekDur = 40.0
    private var nextHoleCheck = 0.0
    private val inHole = InHole(this, hole?.name ?: "HOLE_TAILSPOT", holePos, "Peeking from its hole",
        { c -> if (c.p.night) 0.9 else (if (c.o.timidUntil > c.t) 0.75 else 0.45) + (if (c.o.health < 0.5) 0.3 else 0.0) },
        // head-out inspection by day; a full retreat (head in) while threatened, freshly startled or timid (research 10 §7)
        { c -> if (c.p.night || threat != null || c.o.timidUntil > c.t || c.t - startleAt < 20) -0.5 else 1.0 + 0.6 * sin(c.t * 0.2) })

    init {
        install(
            inHole,
            PlanktonDash(), PerchNearHole(), Graze(this, 0.4), Prospect(),
            Flee(this, { c, _ -> c.safe(holePos + (hole?.dir ?: Vec3.Z) * (c.clearance + 1.0)) }, "Darting home"), SurfaceGasp(this))
    }

    override fun restSpot(c: Ctx, night: Boolean): Rest = holeRest(c, hole, holePos, if (night) -0.5 else 1.5)

    override fun anchorPos(c: Ctx, anchor: String): Vec3? = if (anchor == "hole") holePos else super.anchorPos(c, anchor)

    override fun initialPlace(c: Ctx) {
        super.initialPlace(c)
        o.posture = 3
    }

    override fun preTick(c: Ctx) {
        super.preTick(c)
        if (c.t >= nextHoleCheck) { nextHoleCheck = c.t + 300; reconsiderHole(c) }
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
        // only holes carved deep enough to take the blenny (water along the tunnel), so it never sits inside rock
        fun open(a: com.reeflive.core.sim.Anchor): Boolean {
            val d = (a.dir ?: Vec3.Z).normalized(Vec3.Z)
            return (1..4).all { k -> c.rock.sdf.dist(a.pos - d * (k * (a.depth ?: 6.0) / 4)) > 0.0 }
        }
        val best = c.rock.anchors.ofType("HOLE").filter { it.pos.distanceTo(lh) >= MOVE_AWAY_CM && open(it) }.maxByOrNull { it.pos.distanceTo(lh) } ?: return
        hole = best
        inHole.holeName = best.name; inHole.fallback = best.pos
        o.homePos = best.pos
    }

    /** Short excursions from the hole and how many ended back at it (research 10 §7: inspect → excursion → return). */
    var excursions = 0; private set
    var returns = 0; private set

    companion object {
        /** DESIGN ESTIMATE (research 10 §7: "does not establish that the tailspot must move exactly 50 cm away"). */
        @com.reeflive.core.sim.Ev(com.reeflive.core.sim.Evidence.DESIGN_ESTIMATE, "10 §7") const val MOVE_AWAY_CM = 50.0
        /** A newly added tailspot prospects holes and grazing patches tank-wide for its first day [I]. */
        const val PROSPECT_S = 86400.0
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
        override fun score(c: Ctx): Double {
            if (c.p.night || c.t - c.o.arrivalTime > PROSPECT_S) return 0.0
            return 0.55
        }
        override fun enter(c: Ctx) {
            val sites = (c.rock.anchors.ofType("HOLE") + c.rock.anchors.ofType("GRAZE")).map { it.pos }.filter { it.distanceTo(holePos) > 15 }
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
            f = c.findFood(45.0) { !it.settled }
            if (f != null && c.o.hunger > 0.12) return 0.8
            return if (c.t > next && c.o.hunger > 0.2) 0.5 + 0.35 * c.o.hunger else 0.0
        }
        override fun enter(c: Ctx) {
            back = false
            excursions++
            next = c.t + c.rng.range(30.0, 90.0)
            target = f?.pos ?: c.safe(holePos + (hole?.dir ?: Vec3.Z) * 6.0 + Vec3(c.rng.signed() * 5, c.rng.range(10.0, 20.0), 0.0))
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
                val mouth = c.safe(holePos + (hole?.dir ?: Vec3.Z) * (c.clearance + 1.0), c.clearance * 0.8)
                out.seek(mouth, o.burstSpeed * 0.5, arrive = 1.5, urgency = 0.6)
                if (o.pos.distanceTo(mouth) < 2.5) { returns++; finished = true }
                else if (elapsed > 12) finished = true
            }
        }
    }

    /** Rest on the rock beside the hole with the tail curled (posture 1). */
    inner class PerchNearHole : Behavior("perchhole", "Perched by its hole") {
        private var rest: Rest? = null
        private var stand = Vec3.ZERO
        private var settled = false
        private var dur = 20.0
        override fun score(c: Ctx) = 0.35 * c.activity
        override fun enter(c: Ctx) {
            settled = false
            dur = c.rng.range(10.0, 30.0)
            val sp = c.rockSurfacePoint(holePos, 6.0, minNormalY = 0.1)
            rest = sp?.let { (p, n) -> Rest(p + n * (0.5 * c.o.species.depthRatio * c.L + 0.3), Vec3.Y.rejectFrom(n).normalized(Vec3.X), n, posture = 1) }
            stand = rest?.let { c.safe(it.pos + it.up!! * c.clearance, c.clearance * 0.8) } ?: Vec3.ZERO
        }
        override fun update(c: Ctx, out: SteerIntent) {
            val r = rest ?: run { finished = true; return }
            if (!settled) {
                out.seek(stand, c.o.cruiseSpeed * 1.5, arrive = 1.5)
                if (c.o.pos.distanceTo(stand) < 1.8) settled = true
                if (elapsed > 6) finished = true
                return
            }
            out.rest(r, 5.0)
            if (elapsed > dur) finished = true
        }
    }
}

// =====================================================================================
// Indigo dottyback (Pseudochromis): secretive cave darter, territorial
// =====================================================================================
class DottybackBrain(c: Ctx) : SpeciesBrain(c) {
    val cave: Vec3 = c.rock.anchors.pos("CAVE_DOTTYBACK", c.rock.anchors.pos("CAVE_BIG", Vec3(42.0, 18.0, 6.0)))
    val caveSafe: Vec3 = c.safe(cave + Vec3(0.0, 0.0, 3.0))

    init {
        install(CaveDart(), DottyFeed(),
            Flee(this, { _, _ -> caveSafe }, "Darting into its cave"),
            Sleep(this, "Resting in its cave"), VisitCleaner(this), SurfaceGasp(this), Flash(this))
    }

    override fun restSpot(c: Ctx, night: Boolean): Rest = Rest(caveSafe, Vec3.Z)

    override fun anchorPos(c: Ctx, anchor: String): Vec3? = if (anchor == "cave") cave else super.anchorPos(c, anchor)

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
        val home = if (o.homePos == Vec3.ZERO) c.safe(Vec3(60.0, c.zoneY((o.species.zone.yMin + o.species.zone.yMax) / 2), 20.0)) else o.homePos
        return Rest(if (night) c.safe(home.withY(c.zoneY(0.2))) else home, Vec3.X)
    }

    override fun initialPlace(c: Ctx) {
        val z = o.species.zone
        o.homePos = c.safe(Vec3(c.rng.range(20.0, 100.0), c.zoneY((z.yMin + z.yMax) / 2), c.rng.range(12.0, 26.0)))
        o.placeAt(o.homePos, Vec3.X)
    }
}

@Suppress("unused")
private fun absD(v: Double) = abs(v)
