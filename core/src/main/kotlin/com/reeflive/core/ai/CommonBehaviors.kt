package com.reeflive.core.ai

import com.reeflive.core.geom.Vec3
import com.reeflive.core.geom.smoothstep
import com.reeflive.core.sim.BrainKind
import com.reeflive.core.sim.Disease
import com.reeflive.core.sim.FoodKind
import com.reeflive.core.sim.FoodParticle
import com.reeflive.core.sim.SimEvent
import com.reeflive.core.sim.Symptom
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * Common behaviors (SIMULATION §4.4). Each takes its brain so it can read shared perception
 * state (startle, threat, food alerts). Scores follow the §4.4 table; species brains wrap or
 * re-weight them.
 */

/** Idle: hover at a jittered point within the home radius, facing the flow. */
open class HoverHome(val b: SpeciesBrain, val center: (Ctx) -> Vec3, val radius: (Ctx) -> Double, label: String = "Hovering") : Behavior("hover", label) {
    private var spot: Vec3 = Vec3.ZERO
    private var next = 0.0
    override fun score(c: Ctx): Double {
        val home = center(c)
        val r = max(radius(c), 4.0)
        return 0.2 + 0.3 * (1 - c.o.hunger) + 0.3 * (c.o.pos.distanceTo(home) / r).coerceAtMost(1.0)
    }
    override fun enter(c: Ctx) { next = 0.0 }
    override fun update(c: Ctx, out: SteerIntent) {
        if (c.t > next) {
            next = c.t + c.rng.range(4.0, 10.0)
            spot = c.safe(center(c) + c.rng.unitVector().let { Vec3(it.x, it.y * 0.5, it.z) } * (radius(c) * c.rng.nextDouble()))
        }
        out.seek(spot, c.o.cruiseSpeed * 0.6, arrive = max(2.0, c.L))
        out.hover = true
        out.wander = 0.1
        val fl = c.sim.flow.velocityFast(c.o.pos)
        if (fl.length() > 1.0 && c.o.vel.length() < 0.4 * c.o.cruiseSpeed) out.faceDir = (fl * -1.0).normalized()
    }
}

/** Wander / patrol with zone bias, waypoint every 4–10 s. */
open class Wander(val b: SpeciesBrain, val weight: Double = 0.35, label: String = "Exploring") : Behavior("wander", label) {
    private var wp: Vec3 = Vec3.ZERO
    private var next = 0.0
    override fun score(c: Ctx) = c.o.species.zone.openWater * weight * c.activity + 0.05
    override fun enter(c: Ctx) { next = 0.0 }
    override fun update(c: Ctx, out: SteerIntent) {
        val z = c.o.species.zone
        if (c.t > next || c.o.pos.distanceTo(wp) < c.L) {
            next = c.t + c.rng.range(4.0, 10.0)
            val home = c.o.homePos
            val hr = max(c.o.species.homeRadiusCm, 15.0)
            val p = Vec3(home.x + c.rng.signed() * hr, c.zoneY(c.rng.range(z.yMin, z.yMax)), c.rng.range(4.0, c.rock.widthCm - 4))
            wp = c.safe(p)
        }
        out.seek(wp, c.o.cruiseSpeed, arrive = c.L * 1.5)
        out.wander = 0.3
    }
}

/**
 * Feed (SIMULATION §4.4/§4.6): chase the nearest reachable particle, bite within 1.2 cm.
 * Frenzy: on a fresh feed every diet-matching fish with hunger > 0.2 scores ≥ 0.6.
 */
open class Feed(val b: SpeciesBrain, val radius: Double = 80.0, label: String = "Feeding") : Behavior("feed", label) {
    protected var target: FoodParticle? = null
    private var retarget = 0.0
    private var lastFood = 0.0
    private var cachedScoreAt = -1.0
    private var cached: FoodParticle? = null
    /** Progress guard: a particle it cannot reach (pinned between rock and glass) is given up on for a while. */
    private var bestD = Double.MAX_VALUE
    private var progressAt = 0.0
    private var giveUpUntil = -1.0
    private var bestFor: FoodParticle? = null
    open fun accept(c: Ctx, f: FoodParticle): Boolean = true
    open fun bonus(c: Ctx, f: FoodParticle): Double = 0.0
    open fun scoreMul(c: Ctx, f: FoodParticle): Double = 1.0

    protected fun candidate(c: Ctx): FoodParticle? {
        if (c.t - cachedScoreAt < 0.4 && cached?.eaten == false) return cached
        cachedScoreAt = c.t
        cached = c.findFood(radius) { accept(c, it) }
        return cached
    }

    override fun score(c: Ctx): Double {
        val o = c.o
        if (o.hunger < 0.12 || c.t < giveUpUntil) return 0.0
        val f = candidate(c) ?: return 0.0
        val match = o.species.dietMatch(f.kind)
        val act = max(c.activity, 0.4)
        var s = 0.5 + 0.5 * o.hunger * match * act + bonus(c, f)
        if (c.t - b.foodAlertAt < 30 && o.hunger > 0.2) s = max(s, 0.62)
        val app = com.reeflive.core.sim.HealthModel.appetite(o)
        if (app < 0.3) return 0.0
        return (s * scoreMul(c, f) * app).coerceAtMost(0.93)
    }

    override fun enter(c: Ctx) { target = null; lastFood = c.t; retarget = 0.0; bestFor = null }

    companion object {
        /** No closer by 1 cm in this long → the food is out of reach; ignore food for [GIVE_UP_S] (DESIGN ESTIMATES). */
        const val STUCK_S = 8.0
        const val GIVE_UP_S = 20.0
    }

    override fun update(c: Ctx, out: SteerIntent) {
        val o = c.o
        retarget -= c.dt
        var f = target
        if (f == null || f.eaten || retarget <= 0) {
            f = candidate(c); target = f; retarget = 0.5
        }
        if (f == null) {
            if (c.t - lastFood > 3) finished = true
            out.hover = true; out.wander = 0.2
            return
        }
        lastFood = c.t
        val d = o.pos.distanceTo(f.pos)
        if (f !== bestFor) { bestFor = f; bestD = d; progressAt = c.t }
        else if (d < bestD - 1.0) { bestD = d; progressAt = c.t }
        if (c.t - progressAt > STUCK_S) { giveUpUntil = c.t + GIVE_UP_S; finished = true; return }
        val reach = 1.2 + 0.1 * o.lengthCm
        if (d < reach) {
            c.bite(f)
            target = null
            if (o.hunger < 0.05) finished = true
            return
        }
        val fast = o.hunger > 0.4 && (o.brainKind == BrainKind.CLOWN || o.brainKind == BrainKind.TANG || o.brainKind == BrainKind.HAWK || o.brainKind == BrainKind.DOTTYBACK)
        out.seek(f.pos, if (fast) o.burstSpeed * 0.6 else o.cruiseSpeed * 1.8, arrive = reach, urgency = if (fast) 0.75 else 0.5)
        out.faceDir = (f.pos - o.pos).normalized(o.heading)
        out.separation = 0.4
        if (d < 4) o.mouth = max(o.mouth, 0.5)
    }
}

/** Nibble the nori sheet on its clip (grazers; SIMULATION §4.5 tang). */
class NibbleNori(val b: SpeciesBrain, val weight: Double = 1.0) : Behavior("nori", "Eating nori") {
    private var nextBite = 0.0
    private var noriLimit = 120.0
    override fun enter(c: Ctx) { noriLimit = c.rng.range(120.0, 300.0) }
    override fun score(c: Ctx): Double {
        val n = c.food.nori ?: return 0.0
        if (n.remaining <= 0.02 || c.o.hunger < 0.1) return 0.0
        return ((0.55 + 0.4 * c.o.hunger) * weight * max(c.activity, 0.3)).coerceAtMost(0.9)
    }
    override fun update(c: Ctx, out: SteerIntent) {
        val n = c.food.nori ?: run { finished = true; return }
        val spot = c.safe(n.pos + Vec3(1.5 + 0.3 * c.L, c.rng.signed() * 0.2, 0.0), 0.5)
        out.seek(spot, c.o.cruiseSpeed * 1.5, arrive = 1.5)
        out.allowGlass = true
        out.faceDir = (n.pos - c.o.pos).normalized(c.o.heading)
        if (c.o.pos.distanceTo(spot) < 2.5 && c.t > nextBite) {
            nextBite = c.t + 1.5
            n.remaining -= 0.004 * c.L / 8
            c.o.hunger = max(0.0, c.o.hunger - 0.025)
            c.o.nutritionGreens = 1.0
            c.o.mouth = 1.0
            out.pitchBias = 0.15
        }
        if (c.o.hunger < 0.05 || elapsed > noriLimit) finished = true
    }
}

/** Graze hair algae on rock (grazers): nose to rock, rasps every 1.5 s. */
open class Graze(val b: SpeciesBrain, val weight: Double = 1.0, label: String = "Grazing") : Behavior("graze", label) {
    private var spot: Vec3 = Vec3.ZERO
    private var normal: Vec3 = Vec3.Y
    private var nextRasp = 0.0
    private var dur = 20.0
    override fun score(c: Ctx): Double {
        if (!c.o.species.grazer) return 0.0
        val hair = c.sim.water.s.algaeHair
        return ((0.3 + 0.5 * c.o.hunger * (0.4 + hair * 3).coerceAtMost(1.2)) * c.activity * weight).coerceAtMost(0.75)
    }
    override fun enter(c: Ctx) {
        val patches = c.rock.anchors.ofType("GRAZE")
        val near = patches.sortedBy { it.pos.distanceTo(c.o.pos) + c.rng.nextDouble() * 40 }.firstOrNull()
        val sp = near?.let { c.rockSurfacePoint(it.pos, 4.0) } ?: c.rockSurfacePoint(c.o.pos, 20.0)
        if (sp == null) { finished = true; return }
        spot = sp.first; normal = sp.second
        dur = c.rng.range(10.0, 40.0)
    }
    override fun update(c: Ctx, out: SteerIntent) {
        val stand = spot + normal * (0.5 * c.o.collisionLength + 0.6)
        out.seek(stand, c.o.cruiseSpeed * 1.2, arrive = 1.5)
        out.faceDir = (normal * -1.0)
        out.hover = true
        if (c.o.pos.distanceTo(stand) < 2.5) {
            if (c.t > nextRasp) {
                nextRasp = c.t + 1.5
                c.o.hunger = max(0.0, c.o.hunger - 0.01)
                c.o.nutritionGreens = 1.0
                c.sim.addGrazing(c.sim.grazeRemovalPerMin(c.o) / 20)
                c.o.mouth = 1.0
            }
            out.pitchBias = 0.17 * sin(2 * PI * (c.t - nextRasp) / 1.5)
        }
        if (elapsed > dur) finished = true
    }
}

/**
 * Flee (SIMULATION §4.4/§5.6): burst away from a tap or predator toward cover, stay 10–40 s
 * scaled by skittishness. Small startles (≤ 0.35) only flinch for 0.4 s.
 */
open class Flee(val b: SpeciesBrain, val cover: (Ctx, Vec3) -> Vec3, label: String = "Startled") : Behavior("flee", label) {
    private var from: Vec3 = Vec3.ZERO
    private var dur = 10.0
    private var flinch = false
    private var hide: Vec3 = Vec3.ZERO
    override val committed: Boolean get() = elapsed < dur
    override fun score(c: Ctx): Double {
        b.threat?.let { if (it.alive) return 0.96 }
        if (b.startledRecently && b.startleMag > 0.05) return if (b.startleMag > 0.35) 0.95 else 0.7
        return 0.0
    }
    override fun enter(c: Ctx) {
        val t = b.threat
        from = t?.pos ?: b.startleFrom
        flinch = t == null && b.startleMag <= 0.35
        dur = if (flinch) 0.4 else c.rng.range(10.0, 40.0) * (0.4 + c.o.skittishness)
        hide = cover(c, from)
        if (!flinch) c.o.startle = max(c.o.startle, 0.15)
        b.startleAt = -1e9
    }
    override fun update(c: Ctx, out: SteerIntent) {
        val t = b.threat
        if (t != null) from = t.pos
        if (flinch || elapsed < 0.8) {
            out.fleeFrom = from; out.urgency = 1.0
        } else {
            out.seek(hide, c.o.burstSpeed * 0.7, arrive = 2.0, urgency = 0.6)
            out.hover = true
            if (t != null && t.pos.distanceTo(c.o.pos) < 6) { out.fleeFrom = t.pos; out.urgency = 1.0 }
        }
        if (elapsed > dur && (t == null || !t.alive)) finished = true
    }
}

/** Hide / rest / sleep at a species spot (SIMULATION §4.4, §4.7). */
open class Sleep(val b: SpeciesBrain, label: String = "Sleeping", val nightWeight: Double = 0.7) : Behavior("sleep", label) {
    override fun score(c: Ctx): Double {
        val o = c.o
        val sick = if (o.health < 0.6 || o.disease != null && o.diseaseStage > 0.3) 1.0 else 0.0
        return (nightWeight * (1 - c.activity) + 0.25 * o.stress + 0.5 * sick * 0.6).coerceAtMost(0.9)
    }
    override fun update(c: Ctx, out: SteerIntent) {
        val r = b.restSpot(c, c.p.night)
        val d = c.o.pos.distanceTo(r.pos)
        if (r.posture != 0 && d < 3.0) {
            out.rest(r, 2.0)
            c.o.sleeping = c.p.night
        } else {
            out.seek(r.pos, c.o.cruiseSpeed * (if (d > 10) 0.8 else 0.3), arrive = 3.0)
            out.hover = true
            if (d < 4) { c.o.sleeping = c.p.night; out.faceDir = r.heading; out.ampMul = 0.3; out.pectoral = 0.5 }
        }
    }
}

/** Visit a cleaning station (wrasse or skunk shrimp): pose until cleaned (SIMULATION §4.4, 04 §13C). */
open class VisitCleaner(val b: SpeciesBrain, val extra: Double = 0.0) : Behavior("visit", "Visiting cleaner") {
    private var station: CleaningStation? = null
    override fun score(c: Ctx): Double {
        val o = c.o
        if (c.t - o.lastCleanedAt < 600 || c.p.night) return 0.0
        val st = choose(c) ?: return 0.0
        if (st.client != null && st.client !== o) return 0.0
        val ich = if (o.disease == Disease.ICH) o.diseaseStage * 3 else 0.0
        // a client the wrasse has not reached for a long time (e.g. one wedged in a crevice) solicits it
        val neglected = if (overdue(c)) 0.5 else 0.0
        return (0.15 + 0.6 * ich.coerceAtMost(1.0) + 0.5 * o.parasiteLoad + neglected + extra).coerceAtMost(0.85)
    }
    private var firstSeen = Double.NaN
    /** Not touched by a cleaner for [WrasseBrain.OVERDUE_S] (a fish never cleaned counts from when it was first simulated). */
    private fun overdue(c: Ctx): Boolean {
        if (firstSeen.isNaN()) firstSeen = c.t
        return c.t - maxOf(c.o.lastCleanerContactAt, firstSeen) > WrasseBrain.OVERDUE_S
    }
    private fun choose(c: Ctx): CleaningStation? =
        // the wrasse comes to its clients (06 §5.5); fish only seek out its station when infested with ich
        c.p.stations.filter { it.open && it.cleaner !== c.o && (!it.isShrimp || c.o.brainKind != BrainKind.HAWK) && (it.isShrimp || c.o.disease == Disease.ICH || overdue(c)) }
            .maxByOrNull { it.fitness / (1 + it.pos.distanceTo(c.o.pos) / 30) }
    override fun enter(c: Ctx) { station = choose(c) }
    override fun update(c: Ctx, out: SteerIntent) {
        val st = station
        if (st == null || !st.open) { finished = true; return }
        val o = c.o
        val pose = c.safe(st.pos + Vec3(0.0, 0.0, 1.0) * (o.lengthCm * 0.8 + 2))
        val d = o.pos.distanceTo(pose)
        if (st.client == null) st.client = o
        if (st.client !== o) {
            out.seek(c.viaOpenWater(c.safe(pose + Vec3(0.0, 4.0, 3.0))), o.cruiseSpeed * 0.6, arrive = 3.0); out.hover = true
            if (elapsed > 40) finished = true
            return
        }
        out.seek(if (d > 8) c.viaOpenWater(pose) else pose, o.cruiseSpeed * (if (d > 8) 1.0 else 0.4), arrive = 3.0)
        out.hover = true
        if (d < 4) {
            st.clientReady = true
            out.pitchBias = 0.44; out.pectoral = 1.0; out.ampMul = 0.2
            o.mouth = 0.7
            o.finClamp = 0.0
        }
        if (c.t - o.lastCleanedAt < 2) { st.release(); finished = true }
        if (elapsed > 90) { if (st.client === o) st.release(); finished = true }
    }
    override fun exit(c: Ctx) { station?.let { if (it.client === c.o) it.release() } }
}

/** Curious approach to a glass tap (clowns, tang, wrasse): hover at 4 cm for 5–15 s. */
class GlassCurious(val b: SpeciesBrain) : Behavior("glass", "Curious at the glass") {
    private var dur = 10.0
    private var point: Vec3 = Vec3.ZERO
    override fun score(c: Ctx): Double {
        val o = c.o
        val tap = c.p.taps.lastOrNull { c.t - it.time in 3.0..20.0 && it.point.distanceTo(o.pos) < 60 } ?: return 0.0
        if (o.curiosity <= 0.5 || o.stress > 0.3 || tap.night) return 0.0
        point = tap.point
        return 0.4 + 0.2 * o.curiosity
    }
    override fun enter(c: Ctx) { dur = c.rng.range(5.0, 15.0) }
    override fun update(c: Ctx, out: SteerIntent) {
        val inward = Vec3(0.0, 0.0, -1.0) * (if (point.z > c.rock.widthCm / 2) 1.0 else -1.0)
        val spot = c.safe(point + inward * 4.0, 1.0)
        out.seek(spot, c.o.cruiseSpeed, arrive = 2.0)
        out.allowGlass = true; out.hover = true
        out.faceDir = (point - c.o.pos).normalized(c.o.heading)
        if (elapsed > dur + 6) finished = true
    }
}

/**
 * Low DO: hover at the surface near the HOB outflow (score 0.95). Surfacing is an oxygen behaviour, not a
 * heat one (research 08 §3): it needs low O2 saturation and a fish still able to swim (heat stage ≤ GASPING).
 */
class SurfaceGasp(val b: SpeciesBrain) : Behavior("gasp", "Gasping at the surface") {
    private var lastEvent = -1e9
    override fun score(c: Ctx): Double {
        val o = c.o
        if (!com.reeflive.core.sim.HeatModel.surfaceAllowed(o, c.sim.water.s)) return 0.0
        val sDo = o.envStress["do"] ?: 0.0
        return if (sDo > 0.5 || o.gillRate > 2.4 || Symptom.GASPING in o.symptoms) 0.95 else 0.0
    }
    override fun update(c: Ctx, out: SteerIntent) {
        val hob = c.rock.anchors.pos("HOB_OUTFLOW", Vec3(71.0, 55.0, 1.0))
        val spot = c.safe(Vec3(hob.x + c.rng.signed() * 3, c.rock.waterLevelCm - 1.5 - 0.2 * c.L, hob.z + 6 + 0.5 * c.L), 1.0)
        out.seek(spot, c.o.cruiseSpeed, arrive = 3.0)
        out.hover = true; out.pitchBias = 0.3
        c.o.mouth = 0.5 + 0.5 * sin(c.t * 8)
        if (c.t - lastEvent > 1800) { lastEvent = c.t; c.sim.emit(SimEvent.Gasping(c.o.id, c.sim.simTimeSec)) }
    }
}

/** Flashing (ich): dart to a rock face and roll/scrape 0.5 s, repeat. */
class Flash(val b: SpeciesBrain) : Behavior("flash", "Flashing") {
    private var spot: Vec3 = Vec3.ZERO
    private var normal: Vec3 = Vec3.Y
    private var nextAllowed = 0.0
    private var scrapeAt = -1.0
    override fun score(c: Ctx): Double {
        val o = c.o
        if (o.disease != Disease.ICH && o.disease != Disease.VELVET) return 0.0
        if (c.t < nextAllowed) return 0.0
        // the itch wins over routine behaviour when a flash is due (2–10 /h at ich 0.3, 06 §5.6 #12)
        return (0.7 + 0.5 * o.diseaseStage).coerceAtMost(0.9)
    }
    override fun enter(c: Ctx) {
        val sp = c.rockSurfacePoint(c.o.pos, 25.0) ?: run { finished = true; return }
        spot = sp.first; normal = sp.second; scrapeAt = -1.0
    }
    override fun update(c: Ctx, out: SteerIntent) {
        val stand = spot + normal * (0.5 * c.o.collisionLength + 0.5)
        val d = c.o.pos.distanceTo(stand)
        if (scrapeAt < 0) {
            out.seek(stand, c.o.burstSpeed * 0.7, arrive = 1.0, urgency = 0.8)
            if (d < 2) scrapeAt = c.t
        } else {
            out.rollExtra = 0.7
            out.seek(stand + (c.o.heading * 3.0), c.o.cruiseSpeed, arrive = 1.0)
            if (c.t - scrapeAt > 0.5) {
                finished = true
                c.o.counters.add(com.reeflive.core.sim.Counters.K.FLASH, c.t)
                // ≈ 2 + 15·stage flashes per hour (06 §5.6 #12: 2–10/h at ich 0.3)
                nextAllowed = c.t + 3600.0 / (2.0 + 15.0 * c.o.diseaseStage) * c.rng.range(0.6, 1.4)
            }
        }
        if (elapsed > 8) { finished = true; nextAllowed = c.t + 60 }
    }
}

/** Schooling helpers (cardinals when more are added): cohesion/alignment folded into a target. */
fun schoolTarget(c: Ctx, base: Vec3): Vec3 {
    val mates = c.p.neighbors(c.o.pos, 25.0).filter { it !== c.o && it.brainKind == c.o.brainKind }
    if (mates.isEmpty()) return base
    var centre = Vec3.ZERO
    var vel = Vec3.ZERO
    for (m in mates) { centre += m.pos; vel += m.vel }
    centre /= mates.size.toDouble()
    vel /= mates.size.toDouble()
    return base.lerp(centre, 0.4) + vel * 0.3
}

fun dayLight(c: Ctx) = smoothstep(0.05, 0.3, c.p.light)

fun Ctx.foodKindsPresent(): Set<FoodKind> = food.particles.filter { !it.eaten }.map { it.kind }.toSet()

fun nearestOther(c: Ctx, r: Double, filter: (com.reeflive.core.sim.Organism) -> Boolean) =
    c.p.neighbors(c.o.pos, r).filter { it !== c.o && it.alive && filter(it) }.minByOrNull { it.pos.distanceTo(c.o.pos) }

fun minD(a: Double, b: Double) = min(a, b)
