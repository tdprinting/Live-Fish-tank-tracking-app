package com.reeflive.core.ai

import com.reeflive.core.geom.Vec3
import com.reeflive.core.sim.Anchor
import com.reeflive.core.sim.BrainKind
import com.reeflive.core.sim.Disease
import com.reeflive.core.sim.FoodKind
import com.reeflive.core.sim.FoodParticle
import com.reeflive.core.sim.Organism
import com.reeflive.core.sim.SimEvent
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
    private val hostName = o.hostAnchor ?: "HOST_KENYA"
    fun host(c: Ctx): Vec3 = c.rock.anchors.pos(hostName, Vec3(72.0, 40.0, 15.0))
    fun partner(c: Ctx): Organism? = c.sim.organisms.firstOrNull { it !== o && it.alive && it.brainKind == BrainKind.CLOWN }
    fun isFemale(c: Ctx): Boolean {
        val p = partner(c) ?: return true
        o.role?.let { return it.equals("FEMALE", true) }
        p.role?.let { return !it.equals("FEMALE", true) }
        return o.lengthCm >= p.lengthCm
    }

    init {
        install(HoverHost(), NestleHost(), DefendHost(), ClownFeed(), GlassCurious(this),
            Flee(this, { c, _ -> c.safe(host(c) + Vec3(0.0, 2.0, 0.0), 1.5) }, "Darting into the host"),
            VisitCleaner(this, 0.1), SurfaceGasp(this), Flash(this))
    }

    override fun restSpot(c: Ctx, night: Boolean): Rest {
        val h = host(c)
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
                var p = h + Vec3(cos(ang) * r, c.rng.range(1.5, 1.0 + 1.1 * L), sin(ang) * r * 0.7)
                if (!female && mate != null) {
                    // male keeps 0.5–2 BL from the female and yields when she comes close
                    val dm = mate.pos.distanceTo(c.o.pos)
                    if (dm > 2 * L || dm < 0.5 * L) p = mate.pos + (c.o.pos - mate.pos).normalized(Vec3.X) * (1.2 * L) + Vec3(0.0, 1.0, 0.0)
                }
                spot = c.safe(p, 1.2)
            }
            if (c.t > nextDart) {
                nextDart = c.t + c.rng.range(6.0, 15.0)
                dartUntil = c.t + 0.9
            }
            if (c.t < dartUntil) {
                out.seek(c.safe(h + c.rng.unitVector() * 3.0 + Vec3(0.0, 1.5, 0.0), 1.0), c.o.burstSpeed * 0.5, arrive = 1.0, urgency = 0.5)
            } else {
                out.seek(spot, c.o.cruiseSpeed * 0.8, arrive = L)
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
            out.seek(r.pos + Vec3(sin(c.t * 0.3) * 0.8, 0.0, cos(c.t * 0.23) * 0.8), c.o.cruiseSpeed * 0.4, arrive = 2.0)
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
                    if (i.lengthCm < 1.8 * c.L) c.sim.agentOf(i)?.let { it.brain.onAttacked(it.ctx, c.o.pos, 0.4) }
                }
            } else {
                out.seek(c.safe(host(c) + Vec3(0.0, 3.0, 0.0), 1.2), c.o.cruiseSpeed, arrive = c.L)
                if (elapsed > 3) finished = true
            }
            if (elapsed > 6) finished = true
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
    fun sleepAnchor(c: Ctx): Anchor? = c.rock.anchors["SLEEP_TANG"]

    init {
        install(Patrol(), Graze(this, 1.5), NibbleNori(this, 1.2), TangFeed(),
            Flee(this, { c, _ -> c.safe(sleepAnchor(c)?.pos ?: Vec3(36.0, 25.0, 9.0)) }, "Darting to cover"),
            TangSleep(), VisitCleaner(this, 0.1), Flash(this), GlassCurious(this), SurfaceGasp(this))
    }

    override fun restSpot(c: Ctx, night: Boolean): Rest {
        val a = sleepAnchor(c)
        return if (night && a != null) Rest(c.safe(a.pos, 0.6 * c.o.collisionLength + 0.5), a.dir ?: Vec3.X * -1.0)
        else Rest(c.safe(Vec3(60.0, c.zoneY(0.6), 24.0)), Vec3.X)
    }

    inner class TangFeed : Feed(this@TangBrain, 100.0) {
        override fun bonus(c: Ctx, f: FoodParticle) = if (f.pos.y > c.rock.waterLevelCm - 8) 0.2 else 0.0
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

    /** Night: wedged in the crevice, tilted 30°, pale. */
    inner class TangSleep : Sleep(this@TangBrain) {
        override fun update(c: Ctx, out: SteerIntent) {
            super.update(c, out)
            if (c.o.sleeping) { out.rollExtra = 0.52; c.o.desat = max(c.o.desat, 0.3) }
        }
    }
}

// =====================================================================================
// Pajama cardinal (CardinalBrain) — crepuscular hoverer
// =====================================================================================
class CardinalBrain(c: Ctx) : SpeciesBrain(c) {
    private val spots = listOf(Vec3(62.0, 17.0, 15.0), Vec3(28.0, 22.0, 26.0), Vec3(43.0, 22.0, 27.0))
    private var spot: Vec3 = spots[0]

    init {
        install(HoverCover(), DuskHunt(), CardinalFeed(),
            Flee(this, { c, from -> if (threat?.brainKind == BrainKind.EEL) c.safe(c.o.pos + Vec3(0.0, 15.0, 0.0) + (c.o.pos - from).horizontal().normalized(Vec3.Z) * 8.0) else c.coverPoint(from) }, "Retreating"),
            Sleep(this, "Resting at the cave", 0.35), VisitCleaner(this), SurfaceGasp(this), Flash(this))
    }

    override fun initialPlace(c: Ctx) {
        spot = spots.minBy { it.distanceTo(o.homePos.takeIf { h -> h != Vec3.ZERO } ?: spots[o.rng.nextInt(spots.size)]) }
        super.initialPlace(c)
    }

    override fun restSpot(c: Ctx, night: Boolean): Rest =
        if (night) Rest(c.safe(c.rock.anchors.pos("CAVE_SMALL", Vec3(62.0, 12.0, 8.0)) + Vec3(0.0, 0.0, 6.0)), Vec3.Z * -1.0)
        else Rest(c.safe(spot), Vec3.Z)

    /** Nearly motionless hover beside cover with pectoral sculling. */
    inner class HoverCover : Behavior("hovercover", "Hovering by cover") {
        private var p = Vec3.ZERO
        private var next = 0.0
        override fun score(c: Ctx) = 0.45
        override fun update(c: Ctx, out: SteerIntent) {
            if (c.t > next) { next = c.t + c.rng.range(5.0, 15.0); p = c.safe(schoolTarget(c, spot + c.rng.unitVector() * 2.5)) }
            out.seek(p, c.o.cruiseSpeed * 0.3, arrive = 3.0)
            out.hover = true; out.ampMul = 0.3; out.pectoral = 1.0; out.tailMul = 0.4
        }
    }

    /** Dawn/dusk: wider wander (40 cm), picking at drifting particles. */
    inner class DuskHunt : Behavior("dusk", "Dusk foraging") {
        private var wp = Vec3.ZERO
        private var next = 0.0
        override fun score(c: Ctx): Double {
            val l = c.p.light
            return if (l in 0.03..0.35 && !c.p.night || l in 0.03..0.35) 0.55 else 0.0
        }
        override fun update(c: Ctx, out: SteerIntent) {
            if (c.t > next || c.o.pos.distanceTo(wp) < c.L) {
                next = c.t + c.rng.range(4.0, 9.0)
                wp = c.safe(schoolTarget(c, spot + c.rng.unitVector() * (40 * c.rng.nextDouble())))
            }
            out.seek(wp, c.o.cruiseSpeed, arrive = c.L)
            out.ampMul = 0.6
        }
    }

    inner class CardinalFeed : Feed(this@CardinalBrain, 60.0) {
        override fun bonus(c: Ctx, f: FoodParticle) = if (f.kind == FoodKind.MYSIS || f.kind == FoodKind.PODS) 0.1 else 0.0
        override fun accept(c: Ctx, f: FoodParticle) = !f.settled && !f.onSurface
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
        install(RunStation(), Clean(), Surf(), Feed(this, 70.0),
            Flee(this, { c, from -> c.coverPoint(from) }, "Fleeing"),
            Cocoon(), SurfaceGasp(this), Flash(this), GlassCurious(this))
    }

    override fun restSpot(c: Ctx, night: Boolean): Rest {
        if (!night) return Rest(stationPos, Vec3.Z)
        val cave = c.rock.anchors.pos("CAVE_SMALL", Vec3(62.0, 12.0, 8.0))
        return Rest(c.safe(cave, 0.6), Vec3.X, posture = 4)
    }

    /** Hover at the station facing out; advertise dance when a client is near. */
    inner class RunStation : Behavior("station", "Running cleaning station") {
        private var nextDance = 0.0
        private var danceUntil = -1.0
        override fun score(c: Ctx) = 0.2 + 0.3 * c.activity + 0.2 * c.o.hunger
        override fun update(c: Ctx, out: SteerIntent) {
            out.seek(stationPos + Vec3(sin(c.t * 0.5) * 1.5, sin(c.t * 0.31), 0.0), c.o.cruiseSpeed * 0.5, arrive = 2.0)
            out.hover = true; out.faceDir = Vec3.Z
            val client = c.p.neighbors(stationPos, 35.0).any { it.isFish && it !== c.o && it.alive && it.brainKind != BrainKind.EEL }
            if (client && c.t > nextDance) { nextDance = c.t + c.rng.range(10.0, 25.0); danceUntil = c.t + 2 }
            station.advertising = c.t < danceUntil
            if (station.advertising) { out.pitchBias = 0.35 * sin(2 * PI * 2 * c.t); out.ampMul = 1.6 }
        }
    }

    /** Pick parasites along the client's body (8–20 s), then return. */
    inner class Clean : Behavior("clean", "Cleaning a client") {
        private var dur = 12.0
        override val committed: Boolean get() = elapsed < dur
        override fun score(c: Ctx): Double {
            val cl = station.client ?: return 0.0
            return if (station.clientReady && cl.alive && cl.pos.distanceTo(c.o.pos) < 30) 0.9 else 0.0
        }
        override fun enter(c: Ctx) { dur = c.rng.range(8.0, 20.0) }
        override fun update(c: Ctx, out: SteerIntent) {
            val cl = station.client
            if (cl == null || !cl.alive) { finished = true; return }
            val k = ((elapsed / dur) * 4).toInt().coerceAtMost(3) / 4.0
            val side = (cl.heading cross cl.up).normalized(Vec3.Z) * (if (((elapsed / dur) * 8).toInt() % 2 == 0) 1.0 else -1.0)
            val skin = cl.pos - cl.heading * (cl.lengthCm * (k - 0.3)) + side * (cl.lengthCm * cl.species.depthRatio * 0.35 + 1.0 + 0.3 * c.L)
            out.seek(skin, c.o.cruiseSpeed * 1.2, arrive = 1.0)
            out.faceDir = (cl.pos - c.o.pos).normalized(c.o.heading)
            out.separation = 0.0; out.hover = true
            c.o.mouth = 0.5 + 0.5 * sin(2 * PI * 1.5 * c.t)
            if (elapsed > dur) {
                finishClean(c, cl, isShrimp = false)
                finished = true
            }
        }
        override fun exit(c: Ctx) { if (finished) station.release() }
    }

    /** Between clients: zip along the rock face in the lower-mid zone. */
    inner class Surf : Behavior("surf", "Patrolling the rocks") {
        private var wp = Vec3.ZERO
        private var next = 0.0
        override fun score(c: Ctx) = 0.4 * c.activity
        override fun update(c: Ctx, out: SteerIntent) {
            if (c.t > next || c.o.pos.distanceTo(wp) < 1.5) {
                next = c.t + c.rng.range(1.5, 3.0)
                val sp = c.rockSurfacePoint(stationPos + Vec3(c.rng.signed() * 25, c.rng.signed() * 8, 0.0), 12.0)
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
            if (c.o.pos.distanceTo(r.pos) > 4) { out.seek(r.pos, c.o.cruiseSpeed, arrive = 2.0); return }
            out.rest(r, 1.5)
            c.o.sleeping = true
        }
    }
}

/** Shared end-of-cleaning bookkeeping for wrasse and skunk shrimp (SIMULATION §4.5, 04 §13C). */
fun finishClean(c: Ctx, client: Organism, isShrimp: Boolean) {
    val ich = client.disease == Disease.ICH
    client.lastCleanedAt = c.t
    client.parasiteLoad = 0.0
    if (ich) client.diseaseStage = max(0.0, client.diseaseStage - 0.02)
    c.o.hunger = max(0.0, c.o.hunger - (if (ich) 0.08 else 0.02) * (if (isShrimp) 0.8 else 1.0) - 0.03 * client.parasiteLoad)
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
            var head = mouthOut(if (hidden) 0.5 else outK) + side * sway
            // lunge at particles sinking within 8 cm of the head
            val f = if (o.hunger > 0.1) c.food.nearest(head, 8.0) { o.species.dietMatch(it.kind) > 0 } else null
            if (f != null) {
                head = mouthOut(1.0) + (f.pos - mouthOut(1.0)).clampLength(14.0)
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
            prey = p
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
                    if (hit && c.sim.predationEnabled && c.rng.nextDouble() >= 0.55) {
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
    val perches: List<Anchor> = c.rock.anchors.ofType("PERCH").ifEmpty { listOf(Anchor("PERCH_X", "PERCH", Vec3(49.0, 29.0, 21.0), normal = Vec3.Y)) }
    var current: Anchor = perches.minBy { it.pos.distanceTo(Vec3(49.0, 29.0, 21.0)) }

    init {
        install(PerchHop(), Pounce(), ChaseIntruder(),
            Flee(this, { c, from -> c.coverPoint(from, 20.0) }, "Ducking behind rock"), SurfaceGasp(this), VisitCleaner(this))
    }

    fun perchPose(a: Anchor): Rest {
        val n = (a.normal ?: Vec3.Y).normalized(Vec3.Y)
        val outward = Vec3(0.0, 0.0, 1.0).rejectFrom(n).normalized((Vec3.X cross n).normalized(Vec3.X))
        return Rest(a.pos + n * (0.3 * o.lengthCm), outward, n, posture = 1)
    }

    fun lowestSheltered(): Anchor = perches.minBy { it.pos.y }

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
            since = c.t; dwell = c.rng.range(20.0, 120.0)
        }
        override fun update(c: Ctx, out: SteerIntent) {
            val o = c.o
            when (state) {
                0 -> {
                    val pose = perchPose(current)
                    if (c.t > nextLook) {
                        nextLook = c.t + c.rng.range(1.5, 5.0)
                        val passer = nearestOther(c, 30.0) { it.isFish }
                        lookYaw = if (passer != null) {
                            val to = (passer.pos - o.pos).rejectFrom(pose.up!!)
                            val sgn = if (((pose.heading cross to) dot pose.up!!) > 0) 1.0 else -1.0
                            (Vec3.angle(pose.heading, to) * sgn).coerceIn(-0.44, 0.44)
                        } else c.rng.signed() * 0.44
                    }
                    val h = Vec3.rotateAbout(pose.heading, pose.up!!, lookYaw)
                    out.place(pose.pos, h, pose.up, 6.0); out.posture = 1; out.pectoral = 1.0; out.pitchBias = 0.17
                    o.eyeYaw = lookYaw * 0.5
                    o.sleeping = c.p.night
                    if (!c.p.night && c.t - since > dwell) {
                        val others = perches.filter { it !== current }
                        if (others.isNotEmpty()) {
                            target = others.maxBy { a -> 1.0 / (10 + a.pos.distanceTo(o.pos)) + c.rng.nextDouble() * 0.05 + (if (c.food.countNear(a.pos, 20.0) > 0) 0.1 else 0.0) }
                            state = 1; since = c.t
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
                    if (c.t - since > 0.3 && o.pos.distanceTo(pose.pos) < 0.8) { current = target; state = 0; since = c.t; dwell = c.rng.range(20.0, 120.0) }
                    if (c.t - since > 3) { current = target; state = 0; since = c.t }
                }
            }
        }
    }

    /** Food (or small prey in sandbox) within 15 cm and in view → burst strike, then return. */
    inner class Pounce : Behavior("pounce", "Pouncing") {
        private var f: FoodParticle? = null
        private var prey: Organism? = null
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
                if (o.pos.distanceTo(ff.pos) < 1.2 + 0.1 * o.lengthCm) { c.bite(ff); finished = true }
            } else {
                val p = prey ?: run { finished = true; return }
                out.seek(p.pos, o.burstSpeed, arrive = 0.5, urgency = 1.0)
                if (o.pos.distanceTo(p.pos) < 1.5 + 0.5 * p.lengthCm) {
                    if (c.rng.nextDouble() < 0.4) c.sim.eaten(o, p) else c.sim.agentOf(p)?.let { it.brain.onAttacked(it.ctx, o.pos, 1.0) }
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
            if (c.o.pos.distanceTo(w.pos) < c.L * 0.8) { c.sim.agentOf(w)?.let { it.brain.onAttacked(it.ctx, c.o.pos, 0.4) }; w.socialStress += 0.02; finished = true }
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
    val head = pos + dir * headOut
    val depth = hole?.depth ?: 6.0
    return Rest(head, dir, Vec3.Y.rejectFrom(dir).normalized(Vec3.Y), posture = 3, spline = listOf(head, pos, pos - dir * depth, pos - dir * (depth + c.L)))
}

/** Go to the hole mouth (swim), then slide in (kinematic). [headOut] < 0 = fully inside. */
open class InHole(val b: SpeciesBrain, val holeName: String, val fallback: Vec3, label: String, val scoreFn: (Ctx) -> Double, val headOutFn: (Ctx) -> Double) : Behavior("hole_$holeName", label) {
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
            BenthicFeed(this, 35.0), BlennyRival(this, 12.0, territory),
            Flee(this, { c, _ -> c.safe((hole?.pos ?: territory) + (hole?.dir ?: Vec3.Z) * (c.clearance + 1.0)) }, "Darting home"), SurfaceGasp(this), VisitCleaner(this))
    }

    override fun restSpot(c: Ctx, night: Boolean): Rest =
        if (night) holeRest(c, hole, territory, -0.5)
        else c.rockSurfacePoint(territory, 10.0)?.let { (p, n) -> perchOn(p, n) } ?: holeRest(c, hole, territory, 1.0)

    fun perchOn(p: Vec3, n: Vec3): Rest {
        val h = Vec3.Y.rejectFrom(n).let { if (it.length() < 0.2) Vec3.X.rejectFrom(n) else it }.normalized(Vec3.X)
        return Rest(p + n * (0.5 * o.species.depthRatio * o.lengthCm + 0.3), h, n, posture = 1)
    }

    /** Perch on rock rasping algae (5–20 s) then hop 5–15 cm to the next patch. */
    inner class RockGraze : Behavior("rockgraze", "Grazing on rock") {
        private var perch: Rest? = null
        private var state = 1
        private var until = 0.0
        private var nextRasp = 0.0
        override fun score(c: Ctx) = (0.3 + 0.3 * c.o.hunger + 0.1) * c.activity + 0.05
        override fun enter(c: Ctx) { pick(c) }
        private fun pick(c: Ctx) {
            val centre = if (c.o.pos.distanceTo(territory) > 20) territory else c.o.pos
            val sp = c.rockSurfacePoint(centre, c.rng.range(5.0, 15.0), minNormalY = 0.0)
            perch = sp?.let { perchOn(it.first, it.second) }
            state = 1
            until = c.t + 8
        }
        override fun update(c: Ctx, out: SteerIntent) {
            val p = perch ?: run { pick(c); out.hover = true; return }
            when (state) {
                1 -> {
                    val stand = c.safe(p.pos + p.up!! * c.clearance, c.clearance * 0.8)
                    out.seek(stand, c.o.burstSpeed * 0.4, arrive = 1.5, urgency = 0.5)
                    if (c.o.pos.distanceTo(stand) < 1.8) { state = 0; until = c.t + c.rng.range(5.0, 20.0) }
                    else if (c.t > until) pick(c)
                }
                else -> {
                    out.rest(p, 6.0)
                    if (c.t > nextRasp) {
                        nextRasp = c.t + 1.0
                        c.o.hunger = max(0.0, c.o.hunger - 0.006 * (0.5 + 2 * c.sim.water.s.algaeHair).coerceAtMost(1.5))
                        c.o.nutritionGreens = 1.0
                        c.sim.addGrazing(c.sim.grazeRemovalPerMin(c.o) / 30)
                        c.o.mouth = 1.0
                    }
                    out.kinHeading = Vec3.rotateAbout(p.heading, p.up!!, 0.15 * sin(c.t * 1.7))
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
            c.sim.agentOf(r)?.let { it.brain.onAttacked(it.ctx, c.o.pos, 0.45) }
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
    val hole: Anchor? = c.rock.anchors["HOLE_BLENNY"]
    val holePos: Vec3 get() = hole?.pos ?: Vec3(25.0, 18.0, 24.0)
    private var peekDur = 40.0

    init {
        install(
            InHole(this, hole?.name ?: "HOLE_BLENNY", holePos, "Peeking from its hole",
                { c -> if (c.p.night) 0.9 else 0.45 + (if (c.o.health < 0.5) 0.3 else 0.0) },
                { c -> if (c.p.night) -0.5 else 1.0 + 0.6 * sin(c.t * 0.2) }),
            PlanktonDash(), PerchNearHole(), Graze(this, 0.4),
            Flee(this, { c, _ -> c.safe(holePos + (hole?.dir ?: Vec3.Z) * (c.clearance + 1.0)) }, "Darting home"), BlennyRival(this, 8.0, holePos), SurfaceGasp(this))
    }

    override fun restSpot(c: Ctx, night: Boolean): Rest = holeRest(c, hole, holePos, if (night) -0.5 else 1.5)

    override fun initialPlace(c: Ctx) {
        super.initialPlace(c)
        o.posture = 3
    }

    /** Dart up into the water column to snap plankton or a drifting particle, then back. */
    inner class PlanktonDash : Behavior("dash", "Darting for plankton") {
        private var target = Vec3.ZERO
        private var f: FoodParticle? = null
        private var next = 0.0
        private var back = false
        override fun score(c: Ctx): Double {
            if (c.p.night) return 0.0
            f = c.findFood(45.0) { !it.settled }
            if (f != null && c.o.hunger > 0.12) return 0.8
            return if (c.t > next && c.o.hunger > 0.2) 0.5 + 0.35 * c.o.hunger else 0.0
        }
        override fun enter(c: Ctx) {
            back = false
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
                finished = true
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
        install(CaveDart(), DottyFeed(), Territorial(),
            Flee(this, { _, _ -> caveSafe }, "Darting into its cave"),
            Sleep(this, "Resting in its cave"), VisitCleaner(this), SurfaceGasp(this), Flash(this))
    }

    override fun restSpot(c: Ctx, night: Boolean): Rest = Rest(caveSafe, Vec3.Z)

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
                out.target = wp; out.speed = o.cruiseSpeed * 0.2; out.hover = true; out.ampMul = 0.4; out.pectoral = 1.0
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
                    else -> false
                }
            }
            return if (who != null) 0.5 + 0.4 * c.o.aggression else 0.0
        }
        override fun update(c: Ctx, out: SteerIntent) {
            val w = who ?: run { finished = true; return }
            out.seek(w.pos, c.o.burstSpeed * 0.55, arrive = 1.0, urgency = 0.75)
            if (c.o.pos.distanceTo(w.pos) < 0.6 * c.L + 0.5 * w.lengthCm) {
                c.sim.agentOf(w)?.let { it.brain.onAttacked(it.ctx, c.o.pos, 0.4) }
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
