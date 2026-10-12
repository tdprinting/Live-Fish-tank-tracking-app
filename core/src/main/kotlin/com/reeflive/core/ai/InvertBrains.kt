package com.reeflive.core.ai

import com.reeflive.core.geom.Vec3
import com.reeflive.core.sim.Anchor
import com.reeflive.core.sim.BrainKind
import com.reeflive.core.sim.FoodParticle
import com.reeflive.core.sim.Organism
import com.reeflive.core.sim.SimEvent
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * Invertebrate brains (research 04, SIMULATION §4.5 additions). Same utility architecture with
 * simpler behavior sets. Crawlers move with `SteerIntent.crawl` (surface-constrained), sessile
 * animals with kinematic placement.
 */

/** Crawl to settled/low food within [radius] and eat it. */
open class Scavenge(val b: SpeciesBrain, val radius: Double, val urgentAbove: Double = 0.5, label: String = "Scavenging", val settledOnly: Boolean = true) : Behavior("scavenge", label) {
    private var f: FoodParticle? = null
    private var lastSeen = 0.0
    override fun score(c: Ctx): Double {
        if (c.o.hunger < 0.12) return 0.0
        f = c.findFood(radius, los = false) { !settledOnly || it.settled || it.pos.y < c.rock.sandTopCm + 6 }
        return if (f != null) (0.55 + 0.35 * c.o.hunger) else 0.0
    }
    override fun enter(c: Ctx) { lastSeen = c.t }
    override fun update(c: Ctx, out: SteerIntent) {
        var ff = f
        if (ff == null || ff.eaten) { ff = c.findFood(radius, los = false) { !settledOnly || it.settled || it.pos.y < c.rock.sandTopCm + 6 }; f = ff }
        if (ff == null) { if (c.t - lastSeen > 3) finished = true; return }
        lastSeen = c.t
        out.crawl = true
        out.target = ff.pos
        out.speed = if (c.o.hunger > urgentAbove) c.o.burstSpeed else c.o.cruiseSpeed * 1.5
        out.urgency = if (c.o.hunger > urgentAbove) 0.8 else 0.3
        out.arriveRadius = 1.0
        out.surfaceOffset = surfaceOffset(c)
        if (c.o.pos.distanceTo(ff.pos) < 1.5 + 0.3 * c.L) { c.bite(ff); f = null }
    }
}

/** A random open-sand point (clear of rock by 2 cm) at height [lift] above the sand. */
fun sandSpot(c: Ctx, xr: ClosedFloatingPointRange<Double>, zr: ClosedFloatingPointRange<Double>, lift: Double): Vec3 {
    var p = Vec3(c.rng.range(xr.start, xr.endInclusive), c.rock.sandTopCm + lift, c.rng.range(zr.start, zr.endInclusive))
    for (i in 0 until 30) {
        if (c.rock.sdf.dist(p) > 2.0) return p
        p = Vec3(c.rng.range(xr.start, xr.endInclusive), c.rock.sandTopCm + lift, c.rng.range(zr.start, zr.endInclusive))
    }
    return p
}

fun surfaceOffset(c: Ctx) = 0.5 * c.o.species.depthRatio * c.L + 0.2

/** Wander along surfaces around a centre. */
open class CrawlWander(val b: SpeciesBrain, val centre: (Ctx) -> Vec3, val radius: Double, val weight: (Ctx) -> Double, label: String = "Wandering") : Behavior("crawl", label) {
    private var wp = Vec3.ZERO
    private var next = 0.0
    override fun score(c: Ctx) = weight(c)
    override fun enter(c: Ctx) { next = 0.0 }
    override fun update(c: Ctx, out: SteerIntent) {
        if (c.t > next || c.o.pos.distanceTo(wp) < 1.0) {
            next = c.t + c.rng.range(20.0, 60.0)
            val ce = centre(c)
            wp = c.rockSurfacePoint(ce, radius)?.first ?: Vec3(ce.x + c.rng.signed() * radius, c.rock.sandTopCm, ce.z + c.rng.signed() * radius * 0.5)
            wp = c.rock.clampToWater(wp, 1.0)
        }
        out.crawl = true; out.target = wp; out.speed = c.o.cruiseSpeed; out.arriveRadius = 1.0
        out.surfaceOffset = surfaceOffset(c)
    }
}

/** Hidden while molting (24–48 h); the shell lies on the sand (Simulation.moltShells). */
class MoltHide(val b: SpeciesBrain, val spot: (Ctx) -> Rest) : Behavior("molt", "Molting (hidden)") {
    override fun score(c: Ctx) = if (c.o.molting) 0.99 else 0.0
    override val committed: Boolean get() = true
    override fun update(c: Ctx, out: SteerIntent) {
        val r = spot(c)
        out.rest(r.copy(posture = 6), 1.0)
        c.o.visible = false
        if (!c.o.molting) finished = true
    }
    override fun exit(c: Ctx) { c.o.visible = true }
}

/** Shrimp tail-flip escape: 0.5 s backward burst, then crawl back to shelter. */
class TailFlip(val b: SpeciesBrain, val home: (Ctx) -> Vec3) : Behavior("tailflip", "Tail-flip escape") {
    private var from = Vec3.ZERO
    override val committed: Boolean get() = elapsed < 6
    override fun score(c: Ctx): Double {
        b.threat?.let { return 0.96 }
        return if (b.startledRecently && b.startleMag > 0.3) 0.9 else 0.0
    }
    override fun enter(c: Ctx) { from = b.threat?.pos ?: b.startleFrom; b.startleAt = -1e9 }
    override fun update(c: Ctx, out: SteerIntent) {
        if (elapsed < 0.5) {
            out.fleeFrom = from; out.urgency = 1.0
            out.faceDir = (from - c.o.pos).normalized(c.o.heading)
            return
        }
        out.crawl = true; out.target = home(c); out.speed = c.o.burstSpeed * 0.5; out.urgency = 0.8
        out.surfaceOffset = surfaceOffset(c)
        if (elapsed > 6 && b.threat == null) finished = true
    }
}

fun hangPose(c: Ctx, a: Anchor?, fallback: Vec3, inverted: Boolean): Rest {
    val p = a?.pos ?: fallback
    val n = (a?.normal ?: if (inverted) Vec3(0.0, -1.0, 0.3) else Vec3.Y).normalized(Vec3.Y)
    val h = Vec3.X.rejectFrom(n).normalized(Vec3.Z)
    return Rest(p + n * surfaceOffset(c), h, n, posture = if (inverted) 2 else 1)
}

// =====================================================================================
// Banded coral shrimp (Stenopus hispidus): hangs under ledges, antennae, scavenges, molts
// =====================================================================================
class BandedShrimpBrain(c: Ctx) : SpeciesBrain(c) {
    companion object { const val KEEP_APART_CM = 30.0 }
    private val requested: Anchor? = c.rock.anchors[o.hostAnchor ?: "SHRIMP_LEDGE"] ?: c.rock.anchors["SHRIMP_LEDGE"]
    var ledge: Anchor? = requested; private set
    private var nextLedgeCheck = 0.0
    fun hang(c: Ctx) = hangPose(c, ledge, Vec3(88.0, 35.0, 15.0), inverted = true)

    /**
     * Banded and skunk shrimp keep apart (06 §2.14, §5.6 #15): with a skunk cleaner in the tank the
     * banded shrimp takes the ledge farthest from it when its own is within [KEEP_APART_CM].
     */
    fun chooseLedge(c: Ctx) {
        val skunks = c.sim.organisms.filter { it.alive && it.brainKind == BrainKind.CLEANER_SHRIMP }.map { it.homePos.takeIf { h -> h != Vec3.ZERO } ?: it.pos }
        if (skunks.isEmpty()) { ledge = requested; return }
        fun sep(a: Anchor) = skunks.minOf { it.distanceTo(a.pos) }
        val req = requested ?: return
        if (sep(req) >= KEEP_APART_CM) { ledge = req; return }
        ledge = c.rock.anchors.ofType("LEDGE").maxByOrNull { sep(it) }?.takeIf { sep(it) > sep(req) } ?: req
    }

    override fun preTick(c: Ctx) {
        super.preTick(c)
        if (c.t >= nextLedgeCheck) { nextLedgeCheck = c.t + 600; chooseLedge(c) }
    }

    override fun abstractPose(c: Ctx, out: SteerIntent) {
        if (c.t >= nextLedgeCheck) { nextLedgeCheck = c.t + 600; chooseLedge(c) }
        super.abstractPose(c, out)
    }

    init {
        install(Hang(), Scavenge(this, 30.0, urgentAbove = 0.4, settledOnly = false), NightForage(), Clean(), MoltHide(this) { hang(it) }, TailFlip(this) { hang(it).pos })
    }

    override fun restSpot(c: Ctx, night: Boolean) = hang(c)

    override fun anchorPos(c: Ctx, anchor: String): Vec3? = if (anchor == "ledge") ledge?.pos ?: hang(c).pos else super.anchorPos(c, anchor)

    inner class Hang : Behavior("hang", "Hanging under the ledge") {
        private var shuffle = Vec3.ZERO
        private var next = 0.0
        override fun score(c: Ctx) = if (c.p.night) 0.25 else 0.5
        override fun update(c: Ctx, out: SteerIntent) {
            val r = hang(c)
            if (c.o.pos.distanceTo(r.pos) > 3) {
                out.crawl = true; out.target = r.pos; out.speed = c.o.cruiseSpeed * 1.5; out.surfaceOffset = surfaceOffset(c)
                return
            }
            if (c.t > next) { next = c.t + c.rng.range(5.0, 10.0); shuffle = (c.rng.unitVector().rejectFrom(r.up!!)) * 0.8 }
            out.rest(r.copy(pos = r.pos + shuffle), 1.5)
            c.o.extension = 1.0
        }
    }

    inner class NightForage : CrawlWander(this, { ledge?.pos ?: Vec3(88.0, 35.0, 15.0) }, 30.0, { c -> 0.45 * c.activity }, "Foraging")

    /** Cosmetic cleaning: a slow fish within 5 cm of the shrimp gets picked. */
    inner class Clean : Behavior("clean", "Cleaning a fish") {
        private var client: Organism? = null
        private var nextPick = 0.0
        override fun score(c: Ctx): Double {
            if (c.t < nextPick) return 0.0
            client = nearestOther(c, 6.0) { it.isFish && it.brainKind != BrainKind.HAWK && it.vel.length() < 0.3 * it.cruiseSpeed + 0.5 && it.pos.distanceTo(c.o.pos) < 5 }
            return if (client != null) 0.6 else 0.0
        }
        override fun update(c: Ctx, out: SteerIntent) {
            val cl = client ?: run { finished = true; return }
            out.rest(Rest(c.o.pos, (cl.pos - c.o.pos).normalized(c.o.heading), c.o.up, c.o.posture), 2.0)
            c.o.appendagePhase += c.dt * 6
            if (elapsed > 4) {
                cl.parasiteLoad = max(0.0, cl.parasiteLoad - 0.3)
                c.o.hunger = max(0.0, c.o.hunger - 0.03)
                nextPick = c.t + 120
                finished = true
            }
        }
    }
}

// =====================================================================================
// Skunk cleaner shrimp (Lysmata amboinensis): station, rocking advert, climbs onto clients
// =====================================================================================
class CleanerShrimpBrain(c: Ctx) : SpeciesBrain(c) {
    val anchor: Anchor? = c.rock.anchors["STATION_SHRIMP"]
    fun sit(c: Ctx) = hangPose(c, anchor, Vec3(50.0, 18.0, 26.0), inverted = false).let { r ->
        Rest(r.pos, Vec3.Y.rejectFrom(r.up!!).normalized(r.heading), r.up, 1)
    }
    val station = CleaningStation(o, (anchor?.pos ?: Vec3(50.0, 18.0, 26.0)) + (anchor?.normal ?: Vec3.Z) * 4.0, isShrimp = true)

    init {
        c.p.stations += station
        install(Station(), Climb(), Scavenge(this, 25.0, label = "Grabbing food", settledOnly = false), MoltHide(this) { sit(it) }, TailFlip(this) { sit(it).pos })
    }

    override fun restSpot(c: Ctx, night: Boolean) = sit(c)

    /** Approach statistics (06 §5.6 #16): approaches, leg-rocks and cleans offered, for predators and others. */
    class ApproachStats { var approaches = 0; var rocks = 0; var cleans = 0 }
    val predatorStats = ApproachStats()
    companion object { const val APPROACH_CM = 10.0 }
    val otherStats = ApproachStats()
    private val near = HashSet<String>()
    private var rockUntil = -1e9

    /** Hawk and dottyback count as predators of a cleaner shrimp (S:Caves19 signalling context). */
    fun isPredator(f: Organism) = f.brainKind == BrainKind.HAWK || f.brainKind == BrainKind.DOTTYBACK || f.brainKind == BrainKind.EEL

    /**
     * A fish arriving within [APPROACH_CM] of the station: rock the legs (predators 0.51, others 0.12;
     * S:Caves19 51 % vs 12 %) and offer a cleaning (predators 0.28, others 0.51; 06 §5.3).
     */
    private fun approaches(c: Ctx) {
        if (!station.open) return
        val inside = HashSet<String>()
        for (f in c.p.neighbors(station.pos, APPROACH_CM + 4)) {
            if (!f.isFish || !f.alive || f.pos.distanceTo(station.pos) > APPROACH_CM) continue
            inside += f.id
            if (!near.add(f.id)) continue
            val pred = isPredator(f)
            val st = if (pred) predatorStats else otherStats
            st.approaches++
            if (c.rng.chance(if (pred) 0.51 else 0.12)) {
                st.rocks++
                rockUntil = c.t + c.rng.range(1.0, 3.0)
                c.sim.emit(SimEvent.LegRock(c.o.id, f.id, c.sim.simTimeSec))
            }
            if (station.client == null && c.rng.chance(if (pred) 0.28 else 0.51)) {
                st.cleans++
                station.client = f; station.clientReady = true
                f.poseFor = c.o; f.poseUntil = c.t + 25.0
            }
        }
        near.retainAll { id -> id in inside || c.sim.organisms.any { it.id == id && it.alive && it.pos.distanceTo(station.pos) < APPROACH_CM + 4 } }
    }

    inner class Station : Behavior("station", "Advertising at station") {
        override fun score(c: Ctx) = 0.5
        override fun update(c: Ctx, out: SteerIntent) {
            val r = sit(c)
            if (c.o.pos.distanceTo(r.pos) > 3) {
                out.crawl = true; out.target = r.pos; out.speed = c.o.cruiseSpeed * 1.5; out.surfaceOffset = surfaceOffset(c)
                return
            }
            out.rest(r, 2.0)
            val client = c.p.neighbors(station.pos, 30.0).any { it.isFish && it.alive && it.brainKind != BrainKind.EEL }
            station.advertising = client
            if (client) out.rollExtra = 0.3 * sin(2 * PI * 1.2 * c.t)    // rocks side to side
            approaches(c)
            val rocking = c.t < rockUntil
            c.o.legRock = if (rocking) 1.0 else max(0.0, c.o.legRock - c.dt * 2)
            // leg-rocking: forelegs at 4 Hz (S:Caves19)
            c.o.appendagePhase += c.dt * (if (rocking) 8.0 * PI else if (client) 4.0 else 1.0)
        }
    }

    /** Climb onto a posing client and pick along its body for 8–20 s. */
    inner class Climb : Behavior("climb", "Cleaning a client") {
        private var dur = 12.0
        override val committed: Boolean get() = elapsed < dur
        override fun score(c: Ctx): Double {
            val cl = station.client ?: return 0.0
            return if (station.clientReady && cl.alive && cl.pos.distanceTo(c.o.pos) < 15) 0.9 else 0.0
        }
        override fun enter(c: Ctx) { dur = c.rng.range(8.0, 20.0) }
        override fun update(c: Ctx, out: SteerIntent) {
            val cl = station.client
            if (cl == null || !cl.alive) { finished = true; station.release(); return }
            val k = (elapsed / dur).coerceIn(0.0, 1.0)
            val onBack = cl.pos + cl.heading * (cl.lengthCm * (0.3 - 0.6 * k)) + cl.up * (cl.lengthCm * cl.species.depthRatio * 0.5 + 0.6)
            out.place(onBack, cl.heading, cl.up, 5.0)
            out.posture = 1
            c.o.mouth = 0.5 + 0.5 * sin(2 * PI * 2 * c.t)
            c.o.appendagePhase += c.dt * 6
            cl.poseUntil = max(cl.poseUntil, c.t + 0.5)
            if (elapsed > dur) { finishClean(c, cl, isShrimp = true); cl.poseUntil = c.t; station.release(); finished = true }
        }
    }
}

// =====================================================================================
// Hermit crabs: walk rock and sand, pick/graze, scavenge, retreat into the shell
// =====================================================================================
class HermitBrain(c: Ctx) : SpeciesBrain(c) {
    private val start = c.rock.anchors.ofType("GRAZE").let { g -> if (g.isEmpty()) Vec3(60.0, 4.0, 28.0) else g[o.rng.nextInt(g.size)].pos }

    init {
        install(CrawlWander(this, { it.o.homePos }, 25.0, { 0.35 }, "Wandering"), Pick(), Scavenge(this, 35.0), Retreat())
    }

    override fun restSpot(c: Ctx, night: Boolean): Rest {
        val sp = c.rockSurfacePoint(o.homePos.takeIf { it != Vec3.ZERO } ?: start, 6.0, minNormalY = 0.3)
        return sp?.let { (p, n) -> Rest(p + n * surfaceOffset(c), Vec3.X.rejectFrom(n).normalized(Vec3.Z), n, 0) }
            ?: Rest(Vec3(start.x, c.rock.sandTopCm + surfaceOffset(c), start.z), Vec3.X, Vec3.Y)
    }

    inner class Pick : Behavior("pick", "Picking at rock") {
        private var spot: Pair<Vec3, Vec3>? = null
        private var next = 0.0
        override fun score(c: Ctx) = 0.3 + 0.3 * c.o.hunger
        override fun enter(c: Ctx) { spot = c.rockSurfacePoint(c.o.pos, 12.0, minNormalY = 0.0) }
        override fun update(c: Ctx, out: SteerIntent) {
            // stay on the surface even on the frame it gives up (a hermit never floats free of the rock)
            out.crawl = true; out.surfaceOffset = surfaceOffset(c)
            val s = spot ?: run { finished = true; return }
            if (c.o.pos.distanceTo(s.first) > 1.5) { out.target = s.first; out.speed = c.o.cruiseSpeed; return }
            if (c.t > next) {
                next = c.t + 2
                c.o.hunger = max(0.0, c.o.hunger - 0.004)
                c.sim.addGrazing(c.sim.grazeRemovalPerMin(c.o) / 15)
                c.o.mouth = 1.0
            }
            c.o.appendagePhase += c.dt * 5
            if (elapsed > c.rng.range(20.0, 60.0)) finished = true
        }
    }

    inner class Retreat : Behavior("retreat", "Hiding in shell") {
        private var dur = 15.0
        override val committed: Boolean get() = elapsed < dur
        override fun score(c: Ctx) = if (threat != null || startledRecently && startleMag > 0.1) 0.95 else 0.0
        override fun enter(c: Ctx) { dur = c.rng.range(10.0, 30.0); startleAt = -1e9 }
        override fun update(c: Ctx, out: SteerIntent) {
            out.rest(Rest(c.o.pos, c.o.heading, c.o.up, 6), 1.0)
            c.o.extension = 0.0
            if (elapsed > dur) { finished = true; c.o.extension = 1.0 }
        }
        override fun exit(c: Ctx) { c.o.extension = 1.0 }
    }
}

// =====================================================================================
// Black brittle star: hides under rock by day (arms poke out), roams at night, races to food
// =====================================================================================
class BrittleStarBrain(c: Ctx) : SpeciesBrain(c) {
    val home: Vec3 = c.rock.anchors.pos("BRITTLE_HOME", Vec3(95.0, 4.0, 15.0))

    init {
        install(HideUnder(), CrawlWander(this, { home }, 40.0, { c -> 0.55 * c.activity }, "Roaming"),
            RaceToFood(), Retract())
    }

    override fun restSpot(c: Ctx, night: Boolean) = Rest(Vec3(home.x, c.rock.sandTopCm + 0.4, home.z), Vec3.X, Vec3.Y, posture = 3)

    inner class HideUnder : Behavior("hide", "Hiding under rock") {
        override fun score(c: Ctx) = 0.65 * (1 - c.activity) + 0.1
        override fun update(c: Ctx, out: SteerIntent) {
            val r = restSpot(c, false)
            if (c.o.pos.distanceTo(r.pos) > 2.5) {
                out.crawl = true; out.target = r.pos; out.speed = c.o.cruiseSpeed * 1.5; out.surfaceOffset = 0.4
                c.o.extension = 1.0
                return
            }
            out.rest(r, 1.0)
            c.o.extension = 0.35 + 0.15 * sin(c.t * 0.4 + c.o.appendagePhase)
        }
    }

    inner class RaceToFood : Scavenge(this, 50.0, urgentAbove = 0.0, label = "Racing to food") {
        override fun update(c: Ctx, out: SteerIntent) { super.update(c, out); c.o.extension = 1.0 }
    }

    inner class Retract : Behavior("retract", "Pulling arms in") {
        override val committed: Boolean get() = elapsed < 5
        override fun score(c: Ctx) = if (startledRecently || threat != null) 0.9 else 0.0
        override fun enter(c: Ctx) { startleAt = -1e9 }
        override fun update(c: Ctx, out: SteerIntent) {
            out.rest(Rest(c.o.pos, c.o.heading, c.o.up, c.o.posture), 1.0)
            c.o.extension = max(0.1, c.o.extension - c.dt * 2)
            if (elapsed > 5) finished = true
        }
    }
}

// =====================================================================================
// Brown feather duster: sessile, crown filters, snaps into tube on shadow/touch/food above
// =====================================================================================
class FeatherDusterBrain(c: Ctx) : SpeciesBrain(c) {
    val tube: Vec3 = c.rock.anchors.pos("DUSTER_TUBE", Vec3(15.0, 3.0, 27.0))
    val crown: Vec3 get() = Vec3(tube.x, c.rock.sandTopCm + 0.6 * o.lengthCm, tube.z)

    init { install(Filter(), Snap()) }

    override fun restSpot(c: Ctx, night: Boolean) = Rest(crown, Vec3.Y, Vec3(0.0, 0.0, 1.0), posture = 0)

    override fun initialPlace(c: Ctx) {
        o.homePos = crown
        o.placeAt(crown, Vec3.Y)
        o.up = Vec3(0.0, 0.0, 1.0)
    }

    override fun onFood(c: Ctx, kind: com.reeflive.core.sim.FoodKind, at: Vec3) {
        super.onFood(c, kind, at)
        // food dosed right above the crown makes it flinch first (04 §15)
        if (Vec3(at.x - crown.x, 0.0, at.z - crown.z).length() < 15) { startleAt = c.t; startleMag = 0.5; startleFrom = at }
    }

    inner class Filter : Behavior("filter", "Filter feeding") {
        private var next = 0.0
        override fun score(c: Ctx) = 0.5
        override fun update(c: Ctx, out: SteerIntent) {
            out.rest(restSpot(c, false), 3.0)
            c.o.extension = min(1.0, c.o.extension + c.dt / 10)
            c.o.posture = 0
            c.o.appendagePhase += c.dt * 0.8
            if (c.t > next && c.o.extension > 0.7) {
                next = c.t + 1.0
                val f = c.food.nearest(crown + Vec3(0.0, 1.0, 0.0), 8.0) { !it.settled }
                if (f != null && c.o.hunger > 0.05) c.bite(f)
                c.o.hunger = max(0.0, c.o.hunger - 0.8 * com.reeflive.core.sim.HealthModel.hungerRatePerMin(c.o, c.sim.water.s.tempC) / 60 * (c.sim.water.s.detritus / 0.2).coerceIn(0.3, 1.2))
            }
        }
    }

    inner class Snap : Behavior("snap", "Retracted into tube") {
        private var dur = 30.0
        override val committed: Boolean get() = elapsed < dur
        override fun score(c: Ctx): Double {
            if (startledRecently || c.t - shadowAt < 0.6) return 0.95
            val touch = c.p.neighbors(crown, 6.0).any { it !== c.o && it.isFish && it.pos.distanceTo(crown) < 3 + 0.3 * it.collisionLength }
            return if (touch) 0.95 else 0.0
        }
        override fun enter(c: Ctx) { dur = c.rng.range(20.0, 60.0); startleAt = -1e9; shadowAt = -1e9 }
        override fun update(c: Ctx, out: SteerIntent) {
            out.rest(restSpot(c, false).copy(posture = 6), 3.0)
            c.o.extension = max(0.0, c.o.extension - c.dt * 8)
            if (elapsed > dur) finished = true
        }
    }
}

// =====================================================================================
// Nassarius snails: buried with siphon up, erupt and race to food, re-bury
// =====================================================================================
class NassariusBrain(c: Ctx) : SpeciesBrain(c) {
    private var buryAt: Vec3 = Vec3.ZERO

    init { install(Buried(), Erupt(), CrawlWander(this, { it.o.homePos }, 20.0, { c -> 0.15 * c.activity + 0.05 }, "Roaming the sand")) }

    override fun restSpot(c: Ctx, night: Boolean): Rest {
        val p = if (buryAt == Vec3.ZERO) o.homePos else buryAt
        return Rest(Vec3(p.x, c.rock.sandTopCm + 0.1, p.z), Vec3.X, Vec3.Y, posture = 5)
    }

    override fun initialPlace(c: Ctx) {
        o.homePos = sandSpot(c, 10.0..110.0, 24.0..31.0, 0.1)
        buryAt = o.homePos
        o.placeAt(o.homePos, Vec3.X)
    }

    inner class Buried : Behavior("buried", "Buried, siphon up") {
        private var sink = 0.0
        override fun score(c: Ctx) = 0.45
        override fun enter(c: Ctx) {
            val p = c.o.pos
            // only bury in open sand; a snail up on the rock walks back down to its sand patch first
            buryAt = if (p.y < c.rock.sandTopCm + 1.5 && c.rock.sdf.dist(p) > 2.0) p else o.homePos
            sink = 0.0
        }
        override fun update(c: Ctx, out: SteerIntent) {
            if (sink == 0.0 && c.o.pos.distanceTo(buryAt) > 2.0) {
                out.crawl = true; out.target = buryAt; out.speed = c.o.cruiseSpeed; out.surfaceOffset = surfaceOffset(c)
                return
            }
            sink = min(1.0, sink + c.dt / 3)
            val r = restSpot(c, c.p.night)
            out.rest(if (sink < 1) r.copy(posture = 0) else r, 1.0)
            c.o.extension = 0.6 + 0.2 * sin(c.t * 0.5 + c.o.appendagePhase)   // siphon
        }
    }

    inner class Erupt : Scavenge(this, 40.0, urgentAbove = 0.0, label = "Racing to food") {
        override fun score(c: Ctx): Double {
            val s = super.score(c)
            return if (s > 0 || (c.t - foodAlertAt < 90 && c.o.hunger > 0.1 && c.food.any)) max(s, 0.75) else 0.0
        }
        override fun update(c: Ctx, out: SteerIntent) {
            super.update(c, out)
            if (!out.crawl) { out.crawl = true; out.target = foodAlertPos.withY(c.rock.sandTopCm); out.speed = c.o.burstSpeed; out.surfaceOffset = surfaceOffset(c) }
            c.o.extension = 1.0
        }
    }
}

// =====================================================================================
// Astrea snails: slow grazing on glass and rock; may flip and get stuck
// =====================================================================================
class AstreaBrain(c: Ctx) : SpeciesBrain(c) {
    var flippedUntil = -1.0

    init { install(GlassGraze(this), Flipped()) }

    override fun restSpot(c: Ctx, night: Boolean): Rest = if (o.homePos == Vec3.ZERO) Rest(Vec3(60.0, 30.0, c.rock.widthCm - surfaceOffset(c)), Vec3.Y, Vec3(0.0, 0.0, -1.0))
        else Rest(o.homePos, o.heading, o.up)

    override fun initialPlace(c: Ctx) {
        var onFront = o.rng.chance(0.5)
        var p = Vec3(o.rng.range(10.0, 110.0), o.rng.range(10.0, 45.0), if (onFront) c.rock.widthCm - surfaceOffset(c) else surfaceOffset(c))
        // never start where rock leans on the back pane
        var tries = 0
        while (c.rock.sdf.dist(p) < 2.0 && tries++ < 20) {
            onFront = onFront || tries > 10
            p = Vec3(o.rng.range(10.0, 110.0), o.rng.range(10.0, 45.0), if (onFront) c.rock.widthCm - surfaceOffset(c) else surfaceOffset(c))
        }
        o.homePos = p
        o.placeAt(p, Vec3.Y)
        o.up = if (onFront) Vec3(0.0, 0.0, -1.0) else Vec3.Z
    }

    inner class Flipped : Behavior("flipped", "Flipped over") {
        override val committed: Boolean get() = c.t < flippedUntil
        override fun score(c: Ctx): Double {
            if (c.t < flippedUntil) return 0.99
            // fell from the glass onto the sand: small chance per hour to land upside down
            val onSand = c.o.pos.y < c.rock.sandTopCm + 1.5
            if (onSand && c.rng.chance(c.dt / 3600 * 0.3)) { flippedUntil = c.t + c.rng.range(2.0, 6.0) * 3600; return 0.99 }
            return 0.0
        }
        override fun update(c: Ctx, out: SteerIntent) {
            out.rest(Rest(c.o.pos.withY(c.rock.sandTopCm + 0.6), c.o.heading, Vec3.Y * -1.0, 7), 1.0)
            c.o.startle = max(c.o.startle, 0.1)
            if (c.t >= flippedUntil) finished = true
        }
    }
}

/** Slow film/hair grazing over glass and rock (astrea, asterina). */
class GlassGraze(val b: SpeciesBrain) : Behavior("glassgraze", "Grazing") {
    private var wp = Vec3.ZERO
    private var next = 0.0
    override fun score(c: Ctx) = 0.5
    override fun update(c: Ctx, out: SteerIntent) {
        val r = c.rock
        if (c.t > next || c.o.pos.distanceTo(wp) < 0.8) {
            next = c.t + c.rng.range(60.0, 240.0)
            val o = c.o
            // stay on the current surface type most of the time
            val onGlass = o.pos.z < 1.5 || o.pos.z > r.widthCm - 1.5 || o.pos.x < 1.5 || o.pos.x > r.lengthCm - 1.5
            wp = if (onGlass || c.rng.chance(0.3)) {
                val z = if (o.pos.z > r.widthCm / 2) r.widthCm - 0.3 else 0.3
                // glass spots where rock leans on the pane are not reachable: keep only open glass
                (0 until 8).asSequence().map {
                    Vec3((o.pos.x + c.rng.signed() * 15).coerceIn(3.0, r.lengthCm - 3), (o.pos.y + c.rng.signed() * 10).coerceIn(r.sandTopCm + 2, r.waterLevelCm - 4), z)
                }.firstOrNull { p -> r.sdf.dist(p) > 2.0 && r.sdf.dist((p + o.pos) * 0.5) > 1.0 } ?: (c.rockSurfacePoint(o.pos, 15.0)?.first ?: o.pos)
            } else c.rockSurfacePoint(o.pos, 15.0)?.first ?: o.pos
        }
        out.crawl = true; out.target = wp; out.speed = c.o.cruiseSpeed; out.surfaceOffset = surfaceOffset(c)
        c.sim.addGrazing(c.sim.grazeRemovalPerMin(c.o) * c.dt / 60)
        c.o.hunger = max(0.0, c.o.hunger - 1.6 * com.reeflive.core.sim.HealthModel.hungerRatePerMin(c.o, c.sim.water.s.tempC) * c.dt / 60)
        c.o.nutritionGreens = 1.0
    }
}

// =====================================================================================
// Asterina starfish: tiny, crawl the glass and rock grazing film
// =====================================================================================
class AsterinaBrain(c: Ctx) : SpeciesBrain(c) {
    init { install(GlassGraze(this), Scavenge(this, 6.0)) }

    override fun restSpot(c: Ctx, night: Boolean): Rest = Rest(o.pos, o.heading, o.up)

    override fun initialPlace(c: Ctx) {
        val p = Vec3(o.rng.range(5.0, 115.0), o.rng.range(5.0, 20.0), 0.3 + surfaceOffset(c))
        o.homePos = p
        o.placeAt(p, Vec3.Y)
        o.up = Vec3.Z
    }
}

// =====================================================================================
// Generic invertebrate: crawl and scavenge
// =====================================================================================
class GenericInvertBrain(c: Ctx) : SpeciesBrain(c) {
    init { install(CrawlWander(this, { it.o.homePos }, 25.0, { 0.4 }), Scavenge(this, 30.0)) }
    override fun restSpot(c: Ctx, night: Boolean) = Rest(o.homePos, o.heading, o.up)
    override fun initialPlace(c: Ctx) {
        val p = sandSpot(c, 10.0..110.0, 20.0..30.0, surfaceOffset(c))
        o.homePos = p; o.placeAt(p, Vec3.X)
    }
}

// =====================================================================================
// Sally Lightfoot crab (Percnon gibbesi): wedged in a rock crevice by day, darts out sideways,
// grazes hair algae and scavenges; a large, underfed one may rarely grab a sleeping small fish
// or a snail at night (SIMULATION §9.6).
// =====================================================================================
class SallyCrabBrain(c: Ctx) : SpeciesBrain(c) {
    val crevice: Anchor? = c.rock.anchors[o.hostAnchor ?: "CREVICE_CRAB"] ?: c.rock.anchors["CREVICE_CRAB"] ?: c.rock.anchors["CAVE_SMALL"]
    val home: Vec3 = crevice?.pos ?: Vec3(63.0, 12.0, 8.0)

    init {
        install(HideCrevice(), Graze(), CrabScavenge(), Dart(), NightGrab(), MoltHide(this) { restSpot(it, false) })
    }

    /** Wedged flat into the crevice; posture 4 lets it sit inside the rock's skin (Steering skips SDF projection). */
    override fun restSpot(c: Ctx, night: Boolean): Rest = hangPose(c, crevice, home, inverted = false).copy(pos = home, posture = 4)

    /** Stop-and-go sideways scuttle: short bursts with pauses, like the real crab. */
    private fun scuttle(c: Ctx, out: SteerIntent, target: Vec3, fast: Boolean) {
        out.crawl = true; out.sideways = true; out.target = target; out.surfaceOffset = surfaceOffset(c); out.arriveRadius = 1.0
        val cycle = (c.t + o.appendagePhase) % 2.0
        if (fast || cycle < 0.6) { out.speed = c.o.burstSpeed * (if (fast) 1.0 else 0.5); out.urgency = 0.8 }
        else { out.speed = 0.0; out.urgency = 0.0 }
    }

    inner class HideCrevice : Behavior("hide", "Hiding in a crevice") {
        override fun score(c: Ctx) = 0.7 * (1 - c.activity) + 0.12
        override fun update(c: Ctx, out: SteerIntent) {
            val r = restSpot(c, false)
            if (c.o.pos.distanceTo(r.pos) > 2.0) { scuttle(c, out, r.pos, fast = false); return }
            out.rest(r, 1.0)
            c.o.extension = 0.3 + 0.2 * sin(c.t * 0.3 + c.o.appendagePhase)   // legs and eyes peek out
        }
    }

    inner class Graze : Behavior("graze", "Grazing hair algae") {
        private var spot: Pair<Vec3, Vec3>? = null
        private var next = 0.0
        private var dur = 30.0
        override fun score(c: Ctx): Double {
            val hair = c.sim.water.s.algaeHair
            val food = hair / (hair + 0.02)
            // By day it only slips out to rock right next to its crevice.
            return (0.3 + 0.3 * c.o.hunger) * (0.35 + 0.65 * food) * (0.25 + 0.75 * c.activity)
        }
        override fun enter(c: Ctx) {
            val night = c.activity > 0.5
            spot = c.rockSurfacePoint(if (night) c.o.pos + Vec3(c.rng.signed() * 15, 0.0, c.rng.signed() * 6) else home, if (night) 25.0 else 10.0, minNormalY = 0.0)
            dur = c.rng.range(15.0, 45.0)
        }
        override fun update(c: Ctx, out: SteerIntent) {
            val s = spot ?: run { finished = true; return }
            if (elapsed > dur + 40) { finished = true; return }   // unreachable spot
            if (c.o.pos.distanceTo(s.first) > 1.5) { scuttle(c, out, s.first, fast = false); return }
            out.crawl = true; out.sideways = true; out.surfaceOffset = surfaceOffset(c)
            if (c.t > next) {
                next = c.t + 2
                c.o.hunger = max(0.0, c.o.hunger - 0.004)
                c.sim.addGrazing(c.sim.grazeRemovalPerMin(c.o) / 15)
                c.o.mouth = 1.0
            }
            c.o.appendagePhase += c.dt * 6   // claw-picking
            if (elapsed > dur) finished = true
        }
    }

    inner class CrabScavenge : Scavenge(this, 40.0, urgentAbove = 0.3, label = "Scavenging") {
        override fun update(c: Ctx, out: SteerIntent) { super.update(c, out); out.sideways = true }
    }

    /** Very fast sideways dash back into the crevice when startled or a bigger fish comes close. */
    inner class Dart : Behavior("dart", "Darting for cover") {
        override val committed: Boolean get() = elapsed < 3
        override fun score(c: Ctx): Double {
            if (threat != null || startledRecently && startleMag > 0.1) return 0.97
            val big = nearestOther(c, 6.0) { it.isFish && it.lengthCm > 0.8 * c.o.lengthCm && it.vel.length() > 3.0 }
            return if (big != null) 0.92 else 0.0
        }
        override fun enter(c: Ctx) { startleAt = -1e9 }
        override fun update(c: Ctx, out: SteerIntent) {
            val r = restSpot(c, false)
            if (c.o.pos.distanceTo(r.pos) > 1.5) scuttle(c, out, r.pos, fast = true)
            else { out.rest(r, 1.0); c.o.extension = 0.1 }
            if (elapsed > 8 && threat == null) finished = true
        }
    }

    /** Rare: a large crab that has gone hungry grabs a sleeping small fish or a snail at night. */
    inner class NightGrab : Behavior("grab", "Grabbing prey") {
        private var prey: Organism? = null
        private var nextRoll = 0.0
        override val committed: Boolean get() = elapsed < 4
        override val targetVerb: String? get() = "grabbing at"
        override fun score(c: Ctx): Double {
            val o = c.o
            if (!c.sim.predationEnabled || !c.p.night || o.lengthCm < GRAB_MIN_CM || o.hunger < GRAB_HUNGER) return 0.0
            if (c.t < nextRoll) return if (prey?.alive == true) 0.85 else 0.0
            nextRoll = c.t + 3600.0
            prey = null
            if (!c.rng.chance(GRAB_TRY_PER_HOUR)) return 0.0
            prey = nearestOther(c, 15.0) { Predation.preyWeight(o, it, true) > 0 }
            return if (prey != null) 0.85 else 0.0
        }
        override fun update(c: Ctx, out: SteerIntent) {
            val p = prey ?: run { finished = true; return }
            if (!p.alive) { prey = null; finished = true; return }
            c.o.focusId = p.id
            scuttle(c, out, p.pos, fast = true)
            if (c.o.pos.distanceTo(p.pos) < 1.0 + 0.4 * p.lengthCm) {
                if (c.rng.chance(GRAB_SUCCESS)) c.sim.eaten(c.o, p) else c.sim.agentOf(p)?.let { it.brain.onAttacked(it.ctx, c.o.pos, 1.0, c.o) }
                prey = null
                finished = true
            }
            if (elapsed > 20) { prey = null; finished = true }
        }
    }

    companion object {
        /** Leg span (cm) above which a crab may take live prey. */
        const val GRAB_MIN_CM = 5.0
        const val GRAB_HUNGER = 0.85
        const val GRAB_TRY_PER_HOUR = 0.15
        const val GRAB_SUCCESS = 0.3
    }
}
