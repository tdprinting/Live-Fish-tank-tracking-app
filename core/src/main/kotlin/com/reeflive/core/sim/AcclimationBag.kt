package com.reeflive.core.sim

import com.reeflive.core.geom.Vec3
import kotlin.math.PI
import kotlin.math.cbrt
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * One acclimation bag as the renderer and the HUD see it ([SimSnapshot.bags]). ([x], [y], [z]) is the bag's
 * waterline centre (tank cm); the round, water-filled bulb hangs below it (centre `y - 0.7 radius`) and the knotted,
 * air-filled neck rides above. [tiltX] / [tiltZ] lean the bag about those axes (radians) and [yaw] turns it.
 */
data class BagPose(
    val id: Int,
    val x: Float, val y: Float, val z: Float,
    val radius: Float,
    val tiltX: Float, val tiltZ: Float, val yaw: Float,
    /** 0 falling in, 1 floating, 2 tipping the animals out, 3 lifted out of the water. */
    val phase: Int,
    /** 0..1: how far it has been lifted out after emptying (the renderer fades it). */
    val lift: Float,
    val speciesId: String,
    val common: String,
    val count: Int,
    val coral: Boolean,
    /** Sim minutes until it opens, and 0..1 of the float done. */
    val minutesLeft: Float,
    val progress: Float,
    /** Water in the bag and in the tank (°C): the float is what evens them out. */
    val bagTempC: Float,
    val tankTempC: Float,
)

/**
 * The bags new livestock arrives in (sandbox "Add life", [Command.FloatBag]). The keeper drops a sealed bag in, it
 * floats for [FLOAT_SEC] sim seconds while the bag water warms or cools to the tank (the standard float acclimation:
 * LiveAquaria and Fenner, *The Conscientious Marine Aquarist*: float 15-30 min to even out the temperature), then it
 * tips over and the animals swim, crawl or sink out into the tank as ordinary livestock.
 *
 * The bags move on wall time like the toy [Submarine] (the drop, the bobbing and the tip-out look the same at any sim
 * speed); the float timer and the temperature run on sim time, so [hurry] (the fast-forward button) runs the clock
 * at [HURRY_SPEED] until the last bag opens. Nothing passes through a bag: it stays off the glass, the equipment, the
 * rock and the other bags, and swimmers are pushed out of its bulb every behaviour step ([keepClear]).
 */
class AcclimationBags {
    /** One animal riding in a bag (drawn through the ordinary organism path; it becomes a real organism on release). */
    private class Rider(val o: Organism, val swimmer: Boolean, val anchor: Vec3, val phase: Double, val loopR: Double) {
        var released = false
    }

    /** A crawler that left the bag and is sinking to the rock or sand below. */
    private class Faller(val o: Organism, val target: Vec3, val bagId: Int) { var v = 0.0 }

    private class Bag(
        val id: Int,
        val speciesId: String,
        val common: String,
        val count: Int,
        val sizeCm: Double,
        val coral: Boolean,
        val radius: Double,
        var x: Double, var z: Double,
        val startTempC: Double,
    ) {
        var y = 0.0
        var vy = 0.0
        var vx = 0.0; var vz = 0.0
        var phase = DROP
        var floatStartSim = -1.0
        var t = 0.0
        var openT = 0.0
        var tiltX = 0.0; var tiltZ = 0.0
        var yaw = 0.0
        var lift = 0.0
        /** Tip direction (unit, horizontal) chosen when it opens: toward the open middle of the tank. */
        var tipX = 0.0; var tipZ = 1.0
        val riders = ArrayList<Rider>()
        /** Ids that left this bag: its own animals are not pushed back out of it while it empties. */
        val releasedIds = HashSet<String>()
        var bagTempC = startTempC
    }

    private val bags = ArrayList<Bag>()
    private val fallers = ArrayList<Faller>()
    private var nextId = 1

    /** The fast-forward button: the sim clock runs at least [HURRY_SPEED] while any bag floats. */
    var hurry = false

    val any: Boolean get() = bags.isNotEmpty() || fallers.isNotEmpty()
    val floating: Int get() = bags.count { it.phase <= FLOAT }

    /** Sim speed to run at: [speed], or [HURRY_SPEED] while [hurry] and a bag is still floating. */
    fun effectiveSpeed(speed: Double): Double {
        if (hurry && bags.none { it.phase <= FLOAT }) hurry = false
        return if (hurry) max(speed, HURRY_SPEED) else speed
    }

    fun clear() { bags.clear(); fallers.clear(); hurry = false }

    /**
     * Drops bags of [count] × [speciesId] in at tank x [x] (front-to-back [z]; null = the middle). Many animals come
     * in several bags, as shops pack them; the bags land side by side. Returns how many animals were bagged (the
     * same tank caps as [Stocking.addMany]).
     */
    fun float(sim: Simulation, speciesId: String, count: Int, size: StockSize, sizeCm: Double, x: Double, z: Double?): Int {
        sim.species.ensure(speciesId)
        val sp = sim.species[speciesId]
        val coral = sp.isCoral
        val inBags = bags.sumOf { if (it.coral == coral) it.count else 0 }
        val room = if (coral) Stocking.MAX_CORALS - sim.corals.size - inBags else Stocking.MAX_ANIMALS - sim.organisms.count { it.alive } - inBags
        val cap = if (coral) Stocking.MAX_CORALS_PER_ADD else Stocking.MAX_ANIMALS_PER_ADD
        val n = min(count.coerceIn(0, cap), max(0, room))
        if (n == 0) return 0
        val cm = if (coral) 0.0 else if (sizeCm > 0) sizeCm else size.lengthCm(sp.typicalAdultCm.coerceAtMost(sp.maxLengthCm))
        val swimmer = !coral && (sp.brainKind.isFish || MotionProfiles.of(sp.brainKind).gait == Gait.FIN_JET)
        val perBag = when { coral -> PER_BAG_CORAL; swimmer -> PER_BAG_SWIMMER; else -> PER_BAG_CRAWLER }
        val room4bags = max(1, MAX_BAGS - bags.size)
        val nBags = min(ceil(n / perBag.toDouble()).toInt(), room4bags)
        val rock = sim.rock
        val tank = sim.water.s.tempC
        var left = n
        var already = sim.organisms.count { it.species.id == sp.id } + bags.sumOf { b -> if (b.speciesId == sp.id) b.count else 0 }
        for (k in 0 until nBags) {
            val here = ceil(left / (nBags - k).toDouble()).toInt()
            left -= here
            val r = bagRadius(if (coral) 5.0 else cm, here)
            // side by side around the drop point; [constrain] then settles them apart
            val off = (k - (nBags - 1) / 2.0) * (2 * r + 1.0)
            val b = Bag(
                nextId++, sp.id, sp.common.ifBlank { sp.id }, here, cm, coral, r,
                x + off, z ?: (rock.widthCm * 0.5), tank + TRANSIT_TEMP_OFFSET_C,
            )
            b.y = rock.waterLevelCm + DROP_HEIGHT_CM + k * 1.5
            b.yaw = sim.rng.signed() * PI
            constrain(b, rock)
            if (!coral) addRiders(sim, b, sp, cm, swimmer, already)
            already += here
            bags += b
        }
        return n
    }

    private fun addRiders(sim: Simulation, b: Bag, sp: SimSpecies, cm: Double, swimmer: Boolean, already: Int) {
        val r = b.radius
        for (i in 0 until b.count) {
            val id = "${sp.id}#bag${b.id}_$i"
            val o = Organism(id, sp, cm, "${b.common} #${already + i + 1}", null, sim.rng.fork(id.hashCode().toLong()))
            val anchor = if (swimmer) {
                // spread through the bulb on a golden spiral, kept a body half-length off the plastic
                val room = max(0.3, r - 0.5 * cm - 0.6)
                val f = (i + 0.5) / b.count
                val yy = 1 - 2 * f
                val rr = sqrt(max(0.0, 1 - yy * yy))
                val a = i * 2.39996
                Vec3(cos(a) * rr * room * 0.55, -0.7 * r + yy * room * 0.45 - 0.3, sin(a) * rr * room * 0.55)
            } else {
                // crawlers rest on the bottom of the bag
                val a = i * 2.39996
                val d = if (b.count == 1) 0.0 else r * 0.45 * sqrt((i + 0.5) / b.count)
                Vec3(cos(a) * d, -1.7 * r + 0.25 + 0.15 * cm, sin(a) * d)
            }
            val loop = if (swimmer) max(0.4, min(r * 0.22, r - 0.5 * cm - 0.8 - anchor.length() * 0.2)) else 0.0
            val rd = Rider(o, swimmer, anchor, sim.rng.nextDouble() * 2 * PI, loop)
            o.placeAt(Vec3(b.x, b.y, b.z) + anchor, Vec3(cos(rd.phase), 0.0, sin(rd.phase)))
            b.riders += rd
        }
    }

    /** Opens every floating bag now (tests and "Release now"). */
    fun openAll() { for (b in bags) if (b.phase <= FLOAT) { b.phase = OPEN; b.openT = 0.0; if (b.floatStartSim < 0) b.floatStartSim = 0.0 } }

    // ============================================================== stepping

    /** Wall-time motion: the drop, the bobbing and drifting, the tip-out and lift; opens bags whose float is done. */
    fun stepWall(sim: Simulation, dt: Double) {
        if (bags.isEmpty() && fallers.isEmpty()) return
        // substeps keep the bobbing spring stable on long headless steps
        var left = dt
        while (left > 1e-9) {
            val h = min(left, MAX_STEP_S)
            stepOnce(sim, h)
            left -= h
        }
    }

    private fun stepOnce(sim: Simulation, dt: Double) {
        val rock = sim.rock
        val tankT = sim.water.s.tempC
        val water = rock.waterLevelCm
        var i = 0
        while (i < bags.size) {
            val b = bags[i]
            b.t += dt
            when (b.phase) {
                DROP -> {
                    b.vy -= GRAVITY_CM_S2 * dt
                    b.y += b.vy * dt
                    if (b.y <= water) {
                        // splash: the bag punches into the surface and bobs back up
                        sim.surface.impulse(b.x, b.z.coerceIn(0.0, rock.widthCm), 0.5 + 0.03 * b.radius, b.radius * 1.3)
                        b.vy *= 0.35
                        b.phase = FLOAT
                        b.floatStartSim = sim.simTimeSec
                    }
                }
                FLOAT -> {
                    bob(b, sim, dt)
                    drift(b, sim, dt)
                    val el = sim.simTimeSec - b.floatStartSim
                    b.bagTempC = tankT + (b.startTempC - tankT) * exp(-el / TEMP_TAU_SEC)
                    if (el >= FLOAT_SEC) { b.phase = OPEN; b.openT = 0.0; chooseTip(b, rock) }
                }
                OPEN -> {
                    bob(b, sim, dt)
                    b.openT += dt
                    val k = smooth(b.openT / TIP_S)
                    b.tiltX = b.tipZ * TIP_RAD * k
                    b.tiltZ = -b.tipX * TIP_RAD * k
                    if (b.openT >= RELEASE_AT_S && b.releasedIds.isEmpty()) release(sim, b)
                    if (b.openT >= TIP_S + HOLD_S) b.phase = LIFTED
                }
                LIFTED -> {
                    b.openT += dt
                    b.lift = min(1.0, b.lift + dt / LIFT_S)
                    b.y += 30.0 * dt
                    if (b.lift >= 1.0) { bags.removeAt(i); continue }
                }
            }
            if (b.phase <= OPEN) moveRiders(b, dt)
            i++
        }
        // a few relaxation passes: a push off one bag or the equipment can land it on another
        repeat(4) { separate(rock) }
        stepFallers(sim, dt)
    }

    private fun bob(b: Bag, sim: Simulation, dt: Double) {
        // a damped spring on the waterline plus the surface waves under it and a slow sway
        val rest = sim.rock.waterLevelCm + surfaceAt(sim, b.x, b.z) + 0.25 * sin(b.t * 1.4 + b.id)
        val a = -BOB_K * (b.y - rest) - BOB_C * b.vy
        b.vy += a * dt
        b.y += b.vy * dt
        if (b.phase == FLOAT) {
            b.tiltX = 0.07 * sin(b.t * 0.9 + b.id * 1.7) + 0.02 * b.vz
            b.tiltZ = 0.07 * sin(b.t * 0.7 + b.id * 2.3) - 0.02 * b.vx
        }
    }

    private fun drift(b: Bag, sim: Simulation, dt: Double) {
        // carried by the surface current at a fraction of its speed (the bag hangs deep), plus a slow wander
        val c = Vec3(b.x, sim.rock.waterLevelCm - 0.7 * b.radius, b.z)
        val f = sim.flow.velocityFast(c)
        val wx = DRIFT_FLOW * f.x + 0.6 * sin(b.t * 0.13 + b.id * 3.1)
        val wz = DRIFT_FLOW * f.z + 0.4 * sin(b.t * 0.11 + b.id * 1.3)
        val ease = min(1.0, dt * 0.8)
        b.vx += (wx - b.vx) * ease; b.vz += (wz - b.vz) * ease
        val s = sqrt(b.vx * b.vx + b.vz * b.vz)
        if (s > MAX_DRIFT_CM_S) { b.vx *= MAX_DRIFT_CM_S / s; b.vz *= MAX_DRIFT_CM_S / s }
        val px = b.x; val pz = b.z
        b.x += b.vx * dt; b.z += b.vz * dt
        constrain(b, sim.rock)
        // bumped into something: lose the speed into it
        if (dt > 0) { b.vx = (b.x - px) / dt; b.vz = (b.z - pz) / dt }
        b.yaw += 0.04 * dt * (if (b.id % 2 == 0) 1 else -1)
    }

    private fun surfaceAt(sim: Simulation, x: Double, z: Double): Double {
        val s = sim.surface
        val i = (x / s.dx).toInt().coerceIn(0, s.sx - 1)
        val k = (z / s.dz).toInt().coerceIn(0, s.sz - 1)
        return s.heights[i + s.sx * k].toDouble()
    }

    /** Keeps a bag's bulb inside the glass, off the equipment and the rock (horizontal pushes only: it floats). */
    private fun constrain(b: Bag, rock: RockData) {
        val r = b.radius
        repeat(3) {
            b.x = b.x.coerceIn(r + SIDE_KEEP_CM, rock.lengthCm - r - SIDE_KEEP_CM)
            b.z = b.z.coerceIn(r + BACK_KEEP_CM, rock.widthCm - r - GLASS_KEEP_CM)
            for (k in KEEP_OUT.indices step 3) {
                val dx = b.x - KEEP_OUT[k]; val dz = b.z - KEEP_OUT[k + 1]
                val need = r + KEEP_OUT[k + 2]
                val d = sqrt(dx * dx + dz * dz)
                if (d < need) {
                    val nx = if (d > 1e-6) dx / d else 0.0; val nz = if (d > 1e-6) dz / d else 1.0
                    b.x = KEEP_OUT[k] + nx * need; b.z = KEEP_OUT[k + 1] + nz * need
                }
            }
            // the rock: test the bulb and the bottom of the bag
            for (depth in BULB_PROBES) {
                val c = Vec3(b.x, rock.waterLevelCm + depth * r, b.z)
                val probeR = if (depth == BULB_PROBES[0]) r else 0.55 * r
                val d = rock.sdf.dist(c)
                if (d < probeR + 0.4) {
                    val g = rock.sdf.gradient(c)
                    val h = sqrt(g.x * g.x + g.z * g.z)
                    val push = probeR + 0.4 - d
                    if (h > 1e-4) { b.x += g.x / h * push; b.z += g.z / h * push } else b.z += push
                }
            }
        }
    }

    /** Bags bump apart instead of overlapping. */
    private fun separate(rock: RockData) {
        for (a in 0 until bags.size) for (c in a + 1 until bags.size) {
            val p = bags[a]; val q = bags[c]
            if (p.phase == LIFTED || q.phase == LIFTED) continue
            val dx = q.x - p.x; val dz = q.z - p.z
            val need = p.radius + q.radius + 0.6
            val d = sqrt(dx * dx + dz * dz)
            if (d < need) {
                val nx = if (d > 1e-6) dx / d else 1.0; val nz = if (d > 1e-6) dz / d else 0.0
                val push = (need - d) * 0.5
                p.x -= nx * push; p.z -= nz * push; q.x += nx * push; q.z += nz * push
                constrain(p, rock); constrain(q, rock)
            }
        }
    }

    private fun chooseTip(b: Bag, rock: RockData) {
        // tip toward the open middle of the tank, so the animals pour away from the glass
        val dx = rock.lengthCm / 2 - b.x; val dz = rock.widthCm / 2 - b.z
        val d = sqrt(dx * dx + dz * dz)
        if (d > 1e-3) { b.tipX = dx / d; b.tipZ = dz / d }
    }

    private fun moveRiders(b: Bag, dt: Double) {
        val base = Vec3(b.x, b.y, b.z)
        for (rd in b.riders) {
            if (rd.released) continue
            val o = rd.o
            if (rd.swimmer) {
                // a slow loop in the bag water, nose along the loop
                val w = 0.5 / max(1.0, rd.loopR)
                val ph = rd.phase + b.t * w
                val local = rd.anchor + Vec3(cos(ph) * rd.loopR, 0.25 * sin(2 * ph) * rd.loopR, sin(ph) * rd.loopR)
                val dir = Vec3(-sin(ph), 0.25 * cos(2 * ph), cos(ph))
                val p = base + local
                o.vel = dir * (w * rd.loopR)
                o.pos = p; o.heading = dir.normalized(o.heading)
                o.spline.update(p, o.heading, dt, 6.0)
                o.tailHz = 1.4; o.amp = 0.05
                o.swimPhase = (o.swimPhase + o.tailHz * dt) % 1000.0
                o.pecPhase = (o.pecPhase + 1.2 * dt) % 1000.0; o.pecAmp = 0.4; o.pectoral = 1.0
            } else {
                o.pos = base + rd.anchor
                o.spline.reset(o.pos, o.heading)
            }
        }
    }

    /** The bag tips: swimmers become ordinary organisms where they are, crawlers sink to the bottom first. */
    private fun release(sim: Simulation, b: Bag) {
        val rock = sim.rock
        if (b.coral) {
            Stocking.addMany(sim, b.speciesId, b.count)
            b.releasedIds += "coral"
            return
        }
        for (rd in b.riders) {
            rd.released = true
            val o = rd.o
            b.releasedIds += o.id
            if (rd.swimmer) {
                val real = sim.addFromSeed(LivestockSeed(b.speciesId, o.nickname, b.sizeCm, id = o.id))
                arrive(sim, real, b)
                real.placeAt(rock.clampToWater(o.pos, 1.0), Vec3(b.tipX, -0.3, b.tipZ).normalized(o.heading))
            } else if (o.brainKind.isSessile) {
                arrive(sim, sim.addFromSeed(LivestockSeed(b.speciesId, o.nickname, b.sizeCm, id = o.id)), b)
            } else {
                // sink straight down from the bag to the rock or sand under it (spread a little as they tumble out)
                val sx = (o.pos.x + b.tipX * 3 + sim.rng.signed() * 2).coerceIn(4.0, rock.lengthCm - 4)
                val sz = (o.pos.z + b.tipZ * 3 + sim.rng.signed() * 2).coerceIn(4.0, rock.widthCm - 4)
                val land = Stocking.surfaceSpot(rock, sx, sz)?.first ?: Vec3(sx, rock.sandTopCm, sz)
                fallers += Faller(o, land + Vec3(0.0, 0.3, 0.0), b.id)
            }
        }
    }

    private fun arrive(sim: Simulation, o: Organism, b: Bag) {
        // newcomers: nobody knows them yet (06 §3.3); the float matched the bag water to the tank's temperature
        o.arrivalTime = sim.simTimeSec; o.clock = sim.simTimeSec
        HeatModel.acclimate(o, b.bagTempC)
    }

    private fun stepFallers(sim: Simulation, dt: Double) {
        var i = 0
        while (i < fallers.size) {
            val f = fallers[i]
            val o = f.o
            f.v = min(SINK_CM_S, f.v + 40.0 * dt)
            val to = f.target - o.pos
            val d = to.length()
            val step = f.v * dt
            if (d <= step + 0.05) {
                val real = sim.addFromSeed(LivestockSeed(o.species.id, o.nickname, o.lengthCm, id = o.id))
                real.arrivalTime = sim.simTimeSec; real.clock = sim.simTimeSec
                HeatModel.acclimate(real, sim.water.s.tempC)
                real.homePos = f.target
                real.placeAt(f.target, o.heading)
                fallers.removeAt(i)
                continue
            }
            o.pos = o.pos + to * (step / d)
            o.spline.reset(o.pos, o.heading)
            i++
        }
    }

    /**
     * Pushes swimmers out of every bag's bulb (after the behaviour step): nothing swims through the plastic. A bag's
     * own animals are exempt while it empties.
     */
    fun keepClear(sim: Simulation) {
        if (bags.isEmpty()) return
        val rock = sim.rock
        for (b in bags) {
            if (b.phase == LIFTED) continue
            val cy = b.y - 0.7 * b.radius
            for (o in sim.organisms) {
                if (!o.alive || o.attached) continue
                if (b.releasedIds.isNotEmpty() && o.id in b.releasedIds) continue
                val need = b.radius + 0.3 * o.collisionLength + 0.4
                val dx = o.pos.x - b.x; val dy = o.pos.y - cy; val dz = o.pos.z - b.z
                val d2 = dx * dx + dy * dy + dz * dz
                if (d2 >= need * need) continue
                val d = sqrt(d2)
                // out sideways and down (the top of the bulb is at the surface: there is no going over it)
                var nx = dx; var ny = min(dy, 0.0); var nz = dz
                var nl = sqrt(nx * nx + ny * ny + nz * nz)
                if (nl < 1e-6) { nx = 0.0; ny = -1.0; nz = 0.0; nl = 1.0 }
                nx /= nl; ny /= nl; nz /= nl
                val p = rock.clampToWater(Vec3(b.x + nx * need, cy + ny * need, b.z + nz * need), 1.0)
                val vn = o.vel.x * nx + o.vel.y * ny + o.vel.z * nz
                if (vn < 0) o.vel = Vec3(o.vel.x - vn * nx, o.vel.y - vn * ny, o.vel.z - vn * nz)
                if (d < need) o.pos = p
            }
        }
    }

    // ============================================================== snapshot

    fun poses(tankTempC: Double, simTimeSec: Double): List<BagPose> = bags.map { b ->
        val el = if (b.floatStartSim < 0) 0.0 else simTimeSec - b.floatStartSim
        val left = if (b.phase >= OPEN) 0.0 else max(0.0, FLOAT_SEC - el)
        BagPose(
            b.id, b.x.toFloat(), b.y.toFloat(), b.z.toFloat(), b.radius.toFloat(),
            b.tiltX.toFloat(), b.tiltZ.toFloat(), b.yaw.toFloat(), b.phase, b.lift.toFloat(),
            b.speciesId, b.common, b.count, b.coral,
            (left / 60).toFloat(), (1 - left / FLOAT_SEC).toFloat().coerceIn(0f, 1f),
            b.bagTempC.toFloat(), tankTempC.toFloat(),
        )
    }

    /** The animals still in a bag or sinking out of it, as ordinary poses marked [OrganismPose.bagged]. */
    fun riderPoses(): List<OrganismPose> {
        if (bags.isEmpty() && fallers.isEmpty()) return emptyList()
        val out = ArrayList<OrganismPose>()
        for (b in bags) for (rd in b.riders) if (!rd.released) out += rd.o.toPose().copy(bagged = true)
        for (f in fallers) out += f.o.toPose().copy(bagged = true)
        return out
    }

    companion object {
        const val DROP = 0; const val FLOAT = 1; const val OPEN = 2; const val LIFTED = 3

        /** Float time before the bag opens (30 sim minutes). */
        const val FLOAT_SEC = 1800.0
        /** The fast-forward button's clock rate: 30 sim minutes in 30 s. */
        const val HURRY_SPEED = 60.0
        /**
         * Bag water evens out to the tank with a ~7 min time constant (a 0.5-1 L bag: ~95 % after 20 min, matching
         * the 15-30 min float the guides give); shipped bags arrive a few degrees cool.
         */
        const val TEMP_TAU_SEC = 420.0
        const val TRANSIT_TEMP_OFFSET_C = -2.5
        const val MAX_BAGS = 8
        const val PER_BAG_SWIMMER = 6
        const val PER_BAG_CRAWLER = 10
        const val PER_BAG_CORAL = 6

        const val DROP_HEIGHT_CM = 16.0
        private const val GRAVITY_CM_S2 = 700.0
        private const val MAX_STEP_S = 0.05
        private const val BOB_K = 30.0
        private const val BOB_C = 4.5
        private const val DRIFT_FLOW = 0.12
        private const val MAX_DRIFT_CM_S = 2.5
        private const val TIP_S = 1.2
        private const val TIP_RAD = 1.15
        private const val RELEASE_AT_S = 0.6
        private const val HOLD_S = 0.8
        private const val LIFT_S = 1.0
        private const val SINK_CM_S = 9.0
        private const val SIDE_KEEP_CM = 1.0
        private const val GLASS_KEEP_CM = 0.8
        /** Back strip: the HOB lip, its intake and the back powerhead hang there. */
        private const val BACK_KEEP_CM = 5.5
        /** Waterline-relative depths (× radius) probed against the rock: bulb centre, then the bottom. */
        private val BULB_PROBES = doubleArrayOf(-0.7, -1.25)

        /**
         * Equipment a floating bulb could reach, as (x, z, radius) cylinders (tank cm, from tools/blender/equipment.py):
         * left and right powerheads, the HOB intake, the back powerhead, the floating feed ring, the hose arc.
         */
        private val KEEP_OUT = doubleArrayOf(
            7.0, 18.0, 6.5,
            116.0, 13.0, 6.0,
            114.5, 5.0, 3.0,
            77.0, 3.0, 5.0,
            41.0, 23.4, 5.2,
            13.0, 18.0, 1.5,
            22.0, 16.0, 1.5,
            30.0, 13.0, 1.5,
        )

        /** Bulb radius (cm) for [n] animals of [cm] each: the body has to fit, and more animals need more water. */
        fun bagRadius(cm: Double, n: Int): Double =
            ((max(0.62 * cm, 3.0) * (1 + 0.25 * (cbrt(n.toDouble()) - 1))) + 1.5).coerceIn(4.5, 12.0)

        private fun smooth(t: Double): Double { val x = t.coerceIn(0.0, 1.0); return x * x * (3 - 2 * x) }
    }
}

