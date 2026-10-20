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
 * air-filled neck rides above. ([tiltX], 0, [tiltZ]) is the bag's lean as a rotation vector (axis × angle, radians;
 * [BagFrame]) applied after [yaw].
 *
 * Releasing ([phase] 2, 3) also shows the bucket, the net and the pour: once [cut] the knot is gone and the bag is
 * open at the neck; [water] is the bag water left (it all goes into the bucket); [netR] > 0 draws the net (hoop
 * centre, mouth direction, handle direction, like [NetPose]); [bucketR] > 0 draws the bucket (rim centre, height);
 * [streamR] > 0 draws the water pouring from the bag mouth to the net.
 */
data class BagPose(
    val id: Int,
    val x: Float, val y: Float, val z: Float,
    val radius: Float,
    val tiltX: Float, val tiltZ: Float, val yaw: Float,
    /** 0 falling in, 1 floating, 2 being emptied into the net, 3 the net is done and everything is leaving. */
    val phase: Int,
    /** 0..1: how far it has been lifted away after emptying (the renderer fades it). */
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
    /** Release step ([AcclimationBags.STEP_LIFT] … [AcclimationBags.STEP_NET_OUT]), -1 before it opens. */
    val step: Int = -1,
    val cut: Boolean = false,
    val water: Float = 1f,
    val netX: Float = 0f, val netY: Float = 0f, val netZ: Float = 0f,
    val netFx: Float = 0f, val netFy: Float = 1f, val netFz: Float = 0f,
    val netHx: Float = 0f, val netHy: Float = 1f, val netHz: Float = 0f,
    val netR: Float = 0f,
    val bucketX: Float = 0f, val bucketY: Float = 0f, val bucketZ: Float = 0f,
    val bucketR: Float = 0f, val bucketH: Float = 0f,
    /** 0..1 fade of the bucket and the net (in at the start of the release, out at the end). */
    val gearFade: Float = 0f,
    val streamX0: Float = 0f, val streamY0: Float = 0f, val streamZ0: Float = 0f,
    val streamX1: Float = 0f, val streamY1: Float = 0f, val streamZ1: Float = 0f,
    val streamR: Float = 0f,
)

/**
 * The bag's rotation, shared by the sim (riders ride in the bag's frame) and the renderer (the bag mesh), so the
 * animals and the plastic always turn together: rotation vector ([tiltX], 0, [tiltZ]) after a turn of [yaw] about Y
 * (x' = cos·x + sin·z, z' = −sin·x + cos·z). [out] is row-major: world = out · local.
 */
object BagFrame {
    fun rotation(tiltX: Double, tiltZ: Double, yaw: Double, out: DoubleArray) {
        val cy = cos(yaw); val sy = sin(yaw)
        val th = sqrt(tiltX * tiltX + tiltZ * tiltZ)
        // R = I cosθ + sinθ [k]x + (1 - cosθ) k kᵀ, k = (kx, 0, kz)
        val kx = if (th > 1e-9) tiltX / th else 1.0; val kz = if (th > 1e-9) tiltZ / th else 0.0
        val c = cos(th); val s = sin(th); val t = 1 - c
        val r00 = c + t * kx * kx; val r01 = -s * kz; val r02 = t * kx * kz
        val r10 = s * kz; val r11 = c; val r12 = -s * kx
        val r20 = t * kx * kz; val r21 = s * kx; val r22 = c + t * kz * kz
        // · Ry: columns of Ry are (cy, 0, -sy), (0, 1, 0), (sy, 0, cy)
        out[0] = r00 * cy - r02 * sy; out[1] = r01; out[2] = r00 * sy + r02 * cy
        out[3] = r10 * cy - r12 * sy; out[4] = r11; out[5] = r10 * sy + r12 * cy
        out[6] = r20 * cy - r22 * sy; out[7] = r21; out[8] = r20 * sy + r22 * cy
    }
}

/**
 * The bags new livestock arrives in (sandbox "Add life", [Command.FloatBag]). The keeper drops a sealed bag in, it
 * floats for [FLOAT_SEC] sim seconds while the bag water warms or cools to the tank (the standard float acclimation:
 * LiveAquaria and Fenner, *The Conscientious Marine Aquarist*: float 15-30 min to even out the temperature).
 *
 * Then it is emptied the way shops and the guides say to, so no shop water reaches the tank: the bag is lifted out
 * of the water, cut open below the knot and tipped so it pours into a net held over a bucket. The bag water drains
 * into the bucket, the animals stay in the net, and the net is lowered into the tank and turned so they swim, crawl
 * or sink out as ordinary livestock. One bag is emptied at a time.
 *
 * The bags move on wall time like the toy [Submarine] (the drop, the bobbing and the release look the same at any
 * sim speed); the float timer and the temperature run on sim time, so [hurry] (the fast-forward button) runs the
 * clock at [HURRY_SPEED] until the last bag opens. Nothing passes through a bag or the net: the bag stays off the
 * glass, the equipment, the rock and the other bags, the net goes in where it clears the rock, residents are pushed
 * out of both ([keepClear]), and the animals inside are whole bodies kept inside the plastic or the mesh and apart
 * from each other.
 */
class AcclimationBags {
    /** One animal in a bag or the net (drawn through the ordinary organism path; it becomes a real organism on release). */
    private class Rider(val o: Organism, val swimmer: Boolean) {
        val len = o.lengthCm.coerceAtLeast(0.5)
        /** A brittle star is a flat disc with arms all round: a ball of its arm span, not a line. */
        val star = o.limbRig is BrittleArms
        val half = if (star) 0.0 else 0.5 * len
        /** Body half-thickness: the deeper of the width and the depth (tangs are tall), plus the fins. */
        val w = if (star) 0.5 * len + 0.3 else max(0.12, 0.5 * o.species.depthRatio) * len + 0.25
        /** Body centre and heading in the frame it rides in (the bag's, then the net's), cm. */
        var lp = Vec3.ZERO
        var lh = Vec3.X
        var goal = Vec3.ZERO
        var goalT = 0.0
        var stage = IN_BAG
        var exitAt = 0.0
        var t = 0.0
        var from = Vec3.ZERO
        var to = Vec3.ZERO
        var fromH = Vec3.X
    }

    /** A crawler that left the net and is sinking to the rock or sand below. */
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
        /** The float is done; it waits its turn to be emptied. */
        var ready = false
        var t = 0.0
        var openT = 0.0
        var tiltX = 0.0; var tiltZ = 0.0
        var yaw = 0.0
        var lift = 0.0
        /** Tip direction (unit, horizontal) chosen when it opens: toward the open middle of the tank. */
        var tipX = 0.0; var tipZ = 1.0
        val riders = ArrayList<Rider>()
        /** Ids that left this bag: its own animals are not pushed back out of the net while it empties. */
        val releasedIds = HashSet<String>()
        var bagTempC = startTempC
        val rot = DoubleArray(9)

        // the release: where the bag is lifted from, the net and bucket, the dip spot
        var step = -1
        var fromY = 0.0
        var cut = false
        var water = 1.0
        var netR = 0.0
        var netPos = Vec3.ZERO
        var netFacing = Vec3.Y
        var netHandle = Vec3.Y
        var catchPos = Vec3.ZERO
        var dipPos = Vec3.ZERO
        var bucketR = 0.0
        var bucketH = 0.0
        var bucketTop = Vec3.ZERO
        var gearFade = 0.0
        var streamR = 0.0
        var stream0 = Vec3.ZERO
        var stream1 = Vec3.ZERO
        var released = false
        val nx = DoubleArray(3); val ny = DoubleArray(3); val nz = DoubleArray(3)
    }

    private val bags = ArrayList<Bag>()
    private val fallers = ArrayList<Faller>()
    private var nextId = 1

    /** New bags (and their riders' ids) start at [next] or later ([Simulation.reserveIds]). */
    fun reserveIds(next: Int) { nextId = max(nextId, next) }

    /** The fast-forward button: the sim clock runs at least [HURRY_SPEED] while any bag floats. */
    var hurry = false

    val any: Boolean get() = bags.isNotEmpty() || fallers.isNotEmpty()
    val floating: Int get() = bags.count { it.phase <= FLOAT && !it.ready }

    /** Sim speed to run at: [speed], or [HURRY_SPEED] while [hurry] and a bag is still floating. */
    fun effectiveSpeed(speed: Double): Double {
        if (hurry && floating == 0) hurry = false
        return if (hurry) max(speed, HURRY_SPEED) else speed
    }

    fun clear() { bags.clear(); fallers.clear(); hurry = false }

    /** Animals still in a bag or sinking out of one: not tank organisms yet, but a saved tank must keep them. */
    fun unreleased(): List<Organism> {
        if (bags.isEmpty() && fallers.isEmpty()) return emptyList()
        val out = ArrayList<Organism>()
        for (b in bags) for (rd in b.riders) if (rd.stage != RELEASED) out += rd.o
        for (f in fallers) out += f.o
        return out
    }

    /** Coral bags that have not opened yet: (species id, colonies). */
    fun unopenedCorals(): List<Pair<String, Int>> = bags.filter { it.coral && !it.released }.map { it.speciesId to it.count }

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
            val rd = Rider(o, swimmer)
            // spread through the bulb (swimmers) or over its floor (crawlers); [settle] then fits the whole bodies
            val a = i * 2.39996
            val f = (i + 0.5) / b.count
            val d = if (b.count == 1) 0.0 else r * 0.45 * sqrt(f)
            rd.lp = if (swimmer) Vec3(cos(a) * d, -BULB_C * r + (0.5 - f) * 0.5 * r, sin(a) * d) else Vec3(cos(a) * d, -1.5 * r, sin(a) * d)
            rd.lh = Vec3(cos(a + 1.7), 0.0, sin(a + 1.7))
            rd.goal = rd.lp
            b.riders += rd
        }
        BagFrame.rotation(b.tiltX, b.tiltZ, b.yaw, b.rot)
        repeat(6) { settle(b.riders, IN_BAG, bagCentre(b), BAG_IN * r, -BAG_TOP * r) }
        placeRiders(b, 0.0)
        for (rd in b.riders) rd.o.spline.reset(rd.o.pos, rd.o.heading)
    }

    /** Opens every floating bag now (tests and "Release now"); they are still emptied one at a time. */
    fun openAll() { for (b in bags) if (b.phase <= FLOAT) { b.ready = true; if (b.floatStartSim < 0) b.floatStartSim = 0.0 } }

    // ============================================================== stepping

    /** Wall-time motion: the drop, the bobbing and drifting, the release; opens bags whose float is done. */
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
        val busy = bags.any { it.phase >= OPEN }
        var started = false
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
                    if (el >= FLOAT_SEC) b.ready = true
                    // one bag at a time: the next waits until the last one's net is out
                    if (b.ready && !busy && !started) { started = true; startRelease(b, sim) }
                }
                else -> if (stepRelease(b, sim, dt)) { bags.removeAt(i); continue }
            }
            if (b.phase <= FLOAT) {
                BagFrame.rotation(b.tiltX, b.tiltZ, b.yaw, b.rot)
                swimInBag(b, sim, dt)
                placeRiders(b, dt)
            }
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
        val p = clearSpot(rock, b.x, b.z, r, rock.waterLevelCm - BULB_PROBES[0] * r, rock.waterLevelCm - BULB_PROBES[1] * r, 0.55 * r)
        b.x = p.x; b.z = p.z
    }

    /**
     * Pushes a column at ([x], [z]) clear of the glass, the equipment and the rock: a ball of radius [r] at height
     * [y0] and one of radius [r1] at [y1] (horizontal pushes only). Returns the spot (y = 0).
     */
    private fun clearSpot(rock: RockData, x0: Double, z0: Double, r: Double, y0: Double, y1: Double, r1: Double): Vec3 {
        var x = x0; var z = z0
        repeat(3) {
            x = x.coerceIn(r + SIDE_KEEP_CM, rock.lengthCm - r - SIDE_KEEP_CM)
            z = z.coerceIn(r + BACK_KEEP_CM, rock.widthCm - r - GLASS_KEEP_CM)
            for (k in KEEP_OUT.indices step 3) {
                val dx = x - KEEP_OUT[k]; val dz = z - KEEP_OUT[k + 1]
                val need = r + KEEP_OUT[k + 2]
                val d = sqrt(dx * dx + dz * dz)
                if (d < need) {
                    val nx = if (d > 1e-6) dx / d else 0.0; val nz = if (d > 1e-6) dz / d else 1.0
                    x = KEEP_OUT[k] + nx * need; z = KEEP_OUT[k + 1] + nz * need
                }
            }
            for (j in 0..1) {
                val c = Vec3(x, if (j == 0) y0 else y1, z)
                val probeR = if (j == 0) r else r1
                val d = rock.sdf.dist(c)
                if (d < probeR + 0.4) {
                    val g = rock.sdf.gradient(c)
                    val h = sqrt(g.x * g.x + g.z * g.z)
                    val push = probeR + 0.4 - d
                    if (h > 1e-4) { x += g.x / h * push; z += g.z / h * push } else z += push
                }
            }
        }
        return Vec3(x, 0.0, z)
    }

    /** Bags bump apart instead of overlapping. */
    private fun separate(rock: RockData) {
        for (a in 0 until bags.size) for (c in a + 1 until bags.size) {
            val p = bags[a]; val q = bags[c]
            if (p.phase >= OPEN || q.phase >= OPEN) continue
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

    // ============================================================== riders

    private fun bagCentre(b: Bag) = Vec3(0.0, -BULB_C * b.radius, 0.0)

    /** World position of a point in the bag's frame. */
    private fun bagToWorld(b: Bag, l: Vec3): Vec3 {
        val m = b.rot
        return Vec3(
            b.x + m[0] * l.x + m[1] * l.y + m[2] * l.z,
            b.y + m[3] * l.x + m[4] * l.y + m[5] * l.z,
            b.z + m[6] * l.x + m[7] * l.y + m[8] * l.z,
        )
    }

    private fun bagDirToWorld(b: Bag, l: Vec3): Vec3 {
        val m = b.rot
        return Vec3(m[0] * l.x + m[1] * l.y + m[2] * l.z, m[3] * l.x + m[4] * l.y + m[5] * l.z, m[6] * l.x + m[7] * l.y + m[8] * l.z)
    }

    /** World down in the bag's frame (the transpose of its rotation). */
    private fun bagDown(b: Bag): Vec3 { val m = b.rot; return Vec3(-m[3], -m[4], -m[5]) }

    private fun netToWorld(b: Bag, l: Vec3) = Vec3(
        b.netPos.x + b.nx[0] * l.x + b.ny[0] * l.y + b.nz[0] * l.z,
        b.netPos.y + b.nx[1] * l.x + b.ny[1] * l.y + b.nz[1] * l.z,
        b.netPos.z + b.nx[2] * l.x + b.ny[2] * l.y + b.nz[2] * l.z,
    )

    private fun netDirToWorld(b: Bag, l: Vec3) = Vec3(
        b.nx[0] * l.x + b.ny[0] * l.y + b.nz[0] * l.z,
        b.nx[1] * l.x + b.ny[1] * l.y + b.nz[1] * l.z,
        b.nx[2] * l.x + b.ny[2] * l.y + b.nz[2] * l.z,
    )

    private fun netDown(b: Bag) = Vec3(-b.nx[1], -b.ny[1], -b.nz[1])

    /** The net's frame from its hoop facing and handle (as NetScene draws it: X = Y × Z, Y = facing, Z = handle side). */
    private fun netFrame(b: Bag) {
        val f = b.netFacing
        val zz = b.netHandle.rejectFrom(f).normalized(if (kotlin.math.abs(f.z) < 0.9) Vec3.Z.rejectFrom(f).normalized() else Vec3.X)
        val xx = f cross zz
        b.nx[0] = xx.x; b.nx[1] = xx.y; b.nx[2] = xx.z
        b.ny[0] = f.x; b.ny[1] = f.y; b.ny[2] = f.z
        b.nz[0] = zz.x; b.nz[1] = zz.y; b.nz[2] = zz.z
    }

    /** Swimmers potter about in the bag water; crawlers settle on its floor. Then whole bodies are fitted inside. */
    private fun swimInBag(b: Bag, sim: Simulation, dt: Double) {
        if (b.riders.isEmpty() || dt <= 0) return
        val r = b.radius
        val c = bagCentre(b)
        val down = bagDown(b)
        for (rd in b.riders) {
            if (rd.stage != IN_BAG) continue
            if (rd.swimmer) {
                rd.goalT -= dt
                val room = max(0.0, BAG_IN * r - rd.half - rd.w - 0.3)
                if (rd.goalT <= 0 || rd.lp.distanceTo(rd.goal) < 0.4) {
                    val rng = rd.o.rng
                    val g = Vec3(rng.signed(), rng.signed() * 0.4, rng.signed()).clampLength(1.0) * room
                    rd.goal = Vec3(c.x + g.x, min(c.y + g.y, -BAG_TOP * r - rd.w - 0.3), c.z + g.z)
                    rd.goalT = 3.0 + 3.0 * rng.nextDouble()
                }
                val want = (rd.goal - rd.lp).normalized(rd.lh)
                rd.lh = Vec3.rotateToward(rd.lh, Vec3(want.x, want.y * 0.4, want.z).normalized(rd.lh), 1.4 * dt)
                val v = min(0.35 * rd.len, 2.5)
                rd.lp = rd.lp + rd.lh * (v * dt)
            } else {
                // a crawler slides to the low side of the bag
                rd.lp = rd.lp + down * (3.0 * dt)
                rd.lh = rd.lh.rejectFrom(down).normalized(rd.lh)
            }
        }
        settle(b.riders, IN_BAG, c, BAG_IN * r, if (b.cut) Double.MAX_VALUE else -BAG_TOP * r)
    }

    /**
     * Fits every body of [stage] inside a ball ([c], [rad]) under the ceiling [top] (nose, middle and tail each a
     * ball of the body's half-thickness) and pushes bodies apart, all in the shared local frame.
     */
    private fun settle(rs: List<Rider>, stage: Int, c: Vec3, rad: Double, top: Double) {
        repeat(3) {
            for (rd in rs) if (rd.stage == stage) contain(rd, c, rad, top)
            for (i in rs.indices) {
                val a = rs[i]
                if (a.stage != stage) continue
                for (j in i + 1 until rs.size) {
                    val q = rs[j]
                    if (q.stage != stage) continue
                    apart(a, q)
                }
            }
        }
        for (rd in rs) if (rd.stage == stage) contain(rd, c, rad, top)
    }

    private fun contain(rd: Rider, c: Vec3, rad: Double, top: Double) {
        val lim = rad - rd.w
        // too long to lie straight in this ball: turn the body until it fits (it bends round the side)
        if (rd.half > lim * 0.98) rd.lh = rd.lh.rejectFrom((rd.lp - c).normalized(Vec3.Y)).normalized(rd.lh)
        for (pass in 0..1) for (s in -1..1) {
            val p = rd.lp + rd.lh * (s * rd.half)
            val d = p - c
            val len = d.length()
            if (len > lim && len > 1e-9) rd.lp = rd.lp - d * ((len - lim) / len)
            val over = rd.lp.y + rd.lh.y * (s * rd.half) + rd.w - top
            if (over > 0) rd.lp = Vec3(rd.lp.x, rd.lp.y - over, rd.lp.z)
        }
    }

    /** Pushes two bodies apart (closest of their nose, middle and tail balls). */
    private fun apart(a: Rider, q: Rider) {
        var best = Double.MAX_VALUE
        var dv = Vec3.ZERO
        for (s in -1..1) for (u in -1..1) {
            val pa = a.lp + a.lh * (s * a.half)
            val pq = q.lp + q.lh * (u * q.half)
            val d = pq - pa
            val l2 = d.lengthSq()
            if (l2 < best) { best = l2; dv = d }
        }
        val need = a.w + q.w + 0.15
        val d = sqrt(best)
        if (d >= need) return
        val n = if (d > 1e-6) dv / d else Vec3(1.0, 0.0, 0.0)
        val push = (need - d) * 0.5
        a.lp = a.lp - n * push
        q.lp = q.lp + n * push
    }

    /** Sets each rider's organism pose from its frame (head forward of the body centre: pos is the head). */
    private fun placeRiders(b: Bag, dt: Double) {
        for (rd in b.riders) {
            val o = rd.o
            val (c, h) = when (rd.stage) {
                IN_BAG, SLIDING -> bagToWorld(b, rd.lp) to bagDirToWorld(b, rd.lh)
                IN_NET -> netToWorld(b, rd.lp) to netDirToWorld(b, rd.lh)
                FALLING -> rd.lp to rd.lh
                else -> continue
            }
            val hn = h.normalized(o.heading)
            o.pos = c + hn * rd.half
            o.heading = hn
            o.vel = Vec3.ZERO
            o.spline.reset(o.pos, o.heading)
            if (rd.star) {
                // lies flat on the low side of the bag; falling out of the net, the arms trail
                o.up = Vec3.Y
                (o.limbRig as BrittleArms).carried(dt, if (rd.stage == FALLING) 1.0 else 0.0)
            }
            if (rd.swimmer) {
                o.tailHz = if (rd.stage == IN_BAG) 1.4 else 3.0; o.amp = if (rd.stage == IN_BAG) 0.05 else 0.09
                o.swimPhase = (o.swimPhase + o.tailHz * dt) % 1000.0
                o.pecPhase = (o.pecPhase + 1.2 * dt) % 1000.0; o.pecAmp = 0.4; o.pectoral = 1.0
            }
        }
    }

    // ============================================================== the release

    private fun startRelease(b: Bag, sim: Simulation) {
        val rock = sim.rock
        b.phase = OPEN; b.openT = 0.0; b.step = STEP_LIFT
        if (b.floatStartSim < 0) b.floatStartSim = 0.0
        chooseTip(b, rock)
        b.fromY = b.y
        val r = b.radius
        val n = max(1, b.count)
        val maxL = b.riders.maxOfOrNull { it.len } ?: 5.0
        b.netR = ((0.62 * maxL + 1.2) * (1 + 0.2 * (cbrt(n.toDouble()) - 1))).coerceIn(NET_MIN_R, NET_MAX_R)
        b.bucketR = b.netR + 1.2
        b.bucketH = FishNet.BAG_DEPTH * b.netR + 2.0
        // the net sits under the bag mouth when the bag is tipped right over; the bucket under the net, its
        // bottom clear of the tank water
        val mouthOut = MOUTH * r * sin(POUR_RAD)
        val nx = (b.x + b.tipX * mouthOut).coerceIn(b.bucketR, rock.lengthCm - b.bucketR)
        val nz = (b.z + b.tipZ * mouthOut).coerceIn(b.bucketR, rock.widthCm - b.bucketR)
        val bottom = rock.waterLevelCm + BUCKET_CLEAR_CM
        b.bucketTop = Vec3(nx, bottom + b.bucketH, nz)
        b.catchPos = Vec3(nx, b.bucketTop.y + 0.3, nz)
        b.netPos = b.catchPos
        b.netFacing = Vec3.Y
        b.netHandle = Vec3(-b.tipX * 0.6, 1.0, -b.tipZ * 0.6).normalized()
        netFrame(b)
        // where the net goes in: under the water, clear of the rock and the glass
        val depth = FishNet.BAG_DEPTH * b.netR
        val hoopY = rock.waterLevelCm - (0.35 * b.netR + 1.0)
        val spot = clearSpot(rock, nx, nz, b.netR + 0.5, hoopY - 0.35 * b.netR, hoopY - 0.8 * depth, 0.6 * b.netR + 0.5)
        b.dipPos = Vec3(spot.x, hoopY, spot.z)
        // the order they slide out of the bag
        val pourStart = T_CUT + 0.9
        val gap = min(0.35, (T_POUR - 0.5 - pourStart) / n)
        for ((k, rd) in b.riders.withIndex()) rd.exitAt = pourStart + k * gap
    }

    /** Bag height while tipping: the bulb's bottom never comes down into the bucket. */
    private fun bagY(b: Bag, th: Double): Double = b.bucketTop.y + 0.6 + b.radius * (1 + BULB_C * max(cos(th), 0.0))

    /** One release tick; true when it is finished and the bag can go. */
    private fun stepRelease(b: Bag, sim: Simulation, dt: Double): Boolean {
        val rock = sim.rock
        b.openT += dt
        val t = b.openT
        val r = b.radius
        // the bag: up out of the water, cut, tipped over the net, then away
        val th = POUR_RAD * smooth((t - T_CUT) / (T_TIP - T_CUT))
        when {
            t < T_LIFT -> {
                b.step = STEP_LIFT
                b.y = b.fromY + (bagY(b, 0.0) - b.fromY) * smooth(t / T_LIFT)
                b.tiltX *= (1 - min(1.0, dt * 3)); b.tiltZ *= (1 - min(1.0, dt * 3))
            }
            t < T_POUR -> {
                b.step = if (t < T_CUT) STEP_CUT else STEP_POUR
                if (t >= T_LIFT + 0.3) b.cut = true
                b.tiltX = b.tipZ * th; b.tiltZ = -b.tipX * th
                b.y = bagY(b, th)
            }
            else -> {
                b.step = if (t < T_NET_IN) STEP_NET_IN else if (t < T_NET_TURN) STEP_NET_TURN else STEP_NET_OUT
                b.lift = min(1.0, (t - T_POUR) / BAG_AWAY_S)
                b.y += 25.0 * dt * b.lift
            }
        }
        b.gearFade = min(1.0, t / T_LIFT) * (1 - smooth((t - T_NET_TURN) / (T_END - T_NET_TURN)))
        // the bag water: pours out from when the neck reaches the net until it is empty
        val pour0 = T_CUT + 0.7
        b.water = 1 - smooth((t - pour0) / (T_POUR - pour0))
        BagFrame.rotation(b.tiltX, b.tiltZ, b.yaw, b.rot)
        val mouth = bagToWorld(b, Vec3(0.0, MOUTH * r, 0.0))
        b.streamR = if (t > pour0 && b.water > 0.02 && b.lift <= 0.0) 0.07 * r * sqrt(b.water) + 0.15 else 0.0
        b.stream0 = mouth; b.stream1 = b.catchPos

        // the net: catches, goes down into the tank, turns so they swim out, lifts out
        when {
            t < T_POUR -> { b.netPos = b.catchPos; b.netFacing = Vec3.Y }
            t < T_NET_IN -> {
                val k = smooth((t - T_POUR) / (T_NET_IN - T_POUR))
                // up and over, then down: a little arc so it never drags through the bucket rim
                val p = b.catchPos.lerp(b.dipPos, k)
                b.netPos = Vec3(p.x, p.y + 4.0 * sin(PI * k) * (1 - k), p.z)
            }
            t < T_NET_TURN -> {
                b.netPos = b.dipPos
                val k = smooth((t - T_NET_IN) / (T_NET_TURN - T_NET_IN))
                b.netFacing = Vec3.rotateAbout(Vec3.Y, Vec3(b.tipZ, 0.0, -b.tipX), NET_TURN_RAD * k)
                if (!b.released && k >= 0.55) release(sim, b)
            }
            else -> {
                if (!b.released) release(sim, b)
                val k = smooth((t - T_NET_TURN) / (T_END - T_NET_TURN))
                b.netPos = b.dipPos + Vec3(0.0, k * (b.catchPos.y + 6 - b.dipPos.y), 0.0)
            }
        }
        netFrame(b)

        // the animals: slide out of the mouth, drop into the net, ride it down
        val netC = Vec3(0.0, -NET_C * b.netR, 0.0)
        for (rd in b.riders) {
            if (rd.stage == RELEASED) continue
            rd.t += dt
            when (rd.stage) {
                IN_BAG -> if (t >= rd.exitAt) {
                    rd.stage = SLIDING; rd.t = 0.0; rd.from = rd.lp; rd.fromH = rd.lh
                    // nose first along the neck
                    rd.to = Vec3(0.0, MOUTH * r + rd.half, 0.0)
                }
                SLIDING -> {
                    val k = smooth(rd.t / SLIDE_S)
                    rd.lp = rd.from.lerp(rd.to, k)
                    rd.lh = rd.fromH.lerp(Vec3.Y, k).normalized(Vec3.Y)
                    if (rd.t >= SLIDE_S) {
                        rd.stage = FALLING; rd.t = 0.0
                        rd.from = bagToWorld(b, rd.lp); rd.fromH = bagDirToWorld(b, rd.lh)
                        val k2 = b.riders.indexOf(rd)
                        val a = k2 * 2.39996
                        val slot = Vec3(cos(a) * 0.3 * b.netR, -NET_C * b.netR - 0.3 * b.netR, sin(a) * 0.3 * b.netR)
                        rd.to = netToWorld(b, slot)
                        rd.lp = rd.from; rd.lh = rd.fromH
                    }
                }
                FALLING -> {
                    val k = (rd.t / FALL_S).coerceIn(0.0, 1.0)
                    rd.lp = rd.from.lerp(rd.to, k * k)
                    rd.lh = rd.fromH.lerp(Vec3(-b.tipX, -0.2, -b.tipZ), k).normalized(rd.fromH)
                    if (rd.t >= FALL_S) {
                        rd.stage = IN_NET; rd.t = 0.0
                        rd.lp = worldToNet(b, rd.to)
                        rd.lh = worldDirToNet(b, rd.lh).let { Vec3(it.x, 0.0, it.z).normalized(Vec3.X) }
                    }
                }
                IN_NET -> {
                    // they lie in the bottom of the mesh, sliding to its low side as it turns
                    rd.lp = rd.lp + netDown(b) * (4.0 * dt)
                    rd.lh = rd.lh.rejectFrom(netDown(b)).normalized(rd.lh)
                }
            }
        }
        settle(b.riders, IN_NET, netC, NET_IN * b.netR, -NET_TOP * b.netR)
        settle(b.riders, SLIDING, bagCentre(b), Double.MAX_VALUE, Double.MAX_VALUE)
        swimInBag(b, sim, dt)
        placeRiders(b, dt)
        // clamp the bodies in the tank water to it once the net is in
        return t >= T_END && b.riders.none { it.stage != RELEASED }
    }

    private fun worldToNet(b: Bag, p: Vec3): Vec3 {
        val d = p - b.netPos
        return worldDirToNet(b, d)
    }

    private fun worldDirToNet(b: Bag, d: Vec3) = Vec3(
        b.nx[0] * d.x + b.nx[1] * d.y + b.nx[2] * d.z,
        b.ny[0] * d.x + b.ny[1] * d.y + b.ny[2] * d.z,
        b.nz[0] * d.x + b.nz[1] * d.y + b.nz[2] * d.z,
    )

    private fun chooseTip(b: Bag, rock: RockData) {
        // tip toward the open middle of the tank, so the net goes in away from the glass
        val dx = rock.lengthCm / 2 - b.x; val dz = rock.widthCm / 2 - b.z
        val d = sqrt(dx * dx + dz * dz)
        if (d > 1e-3) { b.tipX = dx / d; b.tipZ = dz / d }
    }

    /** The net is down and turned: swimmers become ordinary organisms where they are, crawlers sink to the bottom. */
    private fun release(sim: Simulation, b: Bag) {
        b.released = true
        val rock = sim.rock
        // stowaways ride in on the frag plugs or in the bag water (research 42)
        val at = Vec3(b.x, rock.sandTopCm + 4, b.z)
        if (b.coral) repeat(b.count) { Hitchhikers.onAdded(sim, HitchSource.CORAL_FRAG, at) } else Hitchhikers.onAdded(sim, HitchSource.ANIMAL, at)
        if (b.coral) {
            Stocking.addMany(sim, b.speciesId, b.count)
            b.releasedIds += "coral"
            return
        }
        for (rd in b.riders) {
            if (rd.stage == RELEASED) continue
            // anyone still on the way out goes with the net
            rd.stage = RELEASED
            val o = rd.o
            b.releasedIds += o.id
            if (rd.swimmer) {
                val real = sim.addFromSeed(LivestockSeed(b.speciesId, o.nickname, b.sizeCm, id = o.id))
                arrive(sim, real, b)
                val out = b.netFacing.horizontal().normalized(Vec3(b.tipX, 0.0, b.tipZ))
                real.placeAt(rock.clampToWater(o.pos, 1.0), (out + Vec3(0.0, -0.2, 0.0)).normalized(o.heading))
            } else if (o.brainKind.isSessile) {
                arrive(sim, sim.addFromSeed(LivestockSeed(b.speciesId, o.nickname, b.sizeCm, id = o.id)), b)
            } else {
                // sink straight down from the net to the rock or sand under it (spread a little as they tumble out)
                val sx = (o.pos.x + sim.rng.signed() * 1.5).coerceIn(4.0, rock.lengthCm - 4)
                val sz = (o.pos.z + sim.rng.signed() * 1.5).coerceIn(4.0, rock.widthCm - 4)
                val land = Stocking.surfaceSpot(rock, sx, sz)?.first ?: Vec3(sx, rock.sandTopCm, sz)
                o.pos = rock.clampToWater(o.pos, 0.5)
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
            o.up = Vec3.Y
            (o.limbRig as? BrittleArms)?.carried(dt, 1.0)
            i++
        }
    }

    /**
     * Pushes swimmers out of every bag's bulb while it is in the water, and out of a net that is in the tank (after
     * the behaviour step): nothing swims through the plastic or the mesh. A bag's own animals are exempt from its net.
     */
    fun keepClear(sim: Simulation) {
        if (bags.isEmpty()) return
        val rock = sim.rock
        for (b in bags) {
            if (b.phase <= OPEN && b.y - (BULB_C + 1) * b.radius < rock.waterLevelCm) {
                pushOut(sim, b, Vec3(b.x, b.y - BULB_C * b.radius, b.z), b.radius, downOnly = true)
            }
            if (b.netR > 0 && b.phase >= OPEN && b.netPos.y - FishNet.BAG_DEPTH * b.netR < rock.waterLevelCm) {
                pushOut(sim, b, netToWorld(b, Vec3(0.0, -NET_C * b.netR, 0.0)), b.netR, downOnly = false)
            }
        }
    }

    private fun pushOut(sim: Simulation, b: Bag, c: Vec3, rad: Double, downOnly: Boolean) {
        val rock = sim.rock
        for (o in sim.organisms) {
            if (!o.alive || o.attached) continue
            if (b.releasedIds.isNotEmpty() && o.id in b.releasedIds) continue
            val need = rad + 0.3 * o.collisionLength + 0.4
            val dx = o.pos.x - c.x; val dy = o.pos.y - c.y; val dz = o.pos.z - c.z
            val d2 = dx * dx + dy * dy + dz * dz
            if (d2 >= need * need) continue
            // out sideways and down (the top of a bulb is at the surface: there is no going over it)
            var nx = dx; var ny = if (downOnly) min(dy, 0.0) else dy; var nz = dz
            var nl = sqrt(nx * nx + ny * ny + nz * nz)
            if (nl < 1e-6) { nx = 0.0; ny = -1.0; nz = 0.0; nl = 1.0 }
            nx /= nl; ny /= nl; nz /= nl
            val p = rock.clampToWater(Vec3(c.x + nx * need, c.y + ny * need, c.z + nz * need), 1.0)
            val vn = o.vel.x * nx + o.vel.y * ny + o.vel.z * nz
            if (vn < 0) o.vel = Vec3(o.vel.x - vn * nx, o.vel.y - vn * ny, o.vel.z - vn * nz)
            o.pos = p
        }
    }

    // ============================================================== snapshot

    fun poses(tankTempC: Double, simTimeSec: Double): List<BagPose> = bags.map { b ->
        val el = if (b.floatStartSim < 0) 0.0 else simTimeSec - b.floatStartSim
        val left = if (b.ready || b.phase >= OPEN) 0.0 else max(0.0, FLOAT_SEC - el)
        val phase = when { b.phase < OPEN -> b.phase; b.step >= STEP_NET_OUT -> 3; else -> 2 }
        val gear = b.phase >= OPEN
        BagPose(
            b.id, b.x.toFloat(), b.y.toFloat(), b.z.toFloat(), b.radius.toFloat(),
            b.tiltX.toFloat(), b.tiltZ.toFloat(), b.yaw.toFloat(), phase, b.lift.toFloat(),
            b.speciesId, b.common, b.count, b.coral,
            (left / 60).toFloat(), (1 - left / FLOAT_SEC).toFloat().coerceIn(0f, 1f),
            b.bagTempC.toFloat(), tankTempC.toFloat(),
            step = if (gear) b.step else -1, cut = b.cut, water = b.water.toFloat(),
            netX = b.netPos.x.toFloat(), netY = b.netPos.y.toFloat(), netZ = b.netPos.z.toFloat(),
            netFx = b.netFacing.x.toFloat(), netFy = b.netFacing.y.toFloat(), netFz = b.netFacing.z.toFloat(),
            netHx = b.netHandle.x.toFloat(), netHy = b.netHandle.y.toFloat(), netHz = b.netHandle.z.toFloat(),
            netR = if (gear) b.netR.toFloat() else 0f,
            bucketX = b.bucketTop.x.toFloat(), bucketY = b.bucketTop.y.toFloat(), bucketZ = b.bucketTop.z.toFloat(),
            bucketR = if (gear) b.bucketR.toFloat() else 0f, bucketH = b.bucketH.toFloat(),
            gearFade = b.gearFade.toFloat(),
            streamX0 = b.stream0.x.toFloat(), streamY0 = b.stream0.y.toFloat(), streamZ0 = b.stream0.z.toFloat(),
            streamX1 = b.stream1.x.toFloat(), streamY1 = b.stream1.y.toFloat(), streamZ1 = b.stream1.z.toFloat(),
            streamR = b.streamR.toFloat(),
        )
    }

    /** The animals still in a bag, the net or sinking out of it, as ordinary poses marked [OrganismPose.bagged]. */
    fun riderPoses(): List<OrganismPose> {
        if (bags.isEmpty() && fallers.isEmpty()) return emptyList()
        val out = ArrayList<OrganismPose>()
        for (b in bags) for (rd in b.riders) if (rd.stage != RELEASED) out += rd.o.toPose().copy(bagged = true)
        for (f in fallers) out += f.o.toPose().copy(bagged = true)
        return out
    }

    companion object {
        const val DROP = 0; const val FLOAT = 1; const val OPEN = 2

        /** Release steps ([BagPose.step]). */
        const val STEP_LIFT = 0; const val STEP_CUT = 1; const val STEP_POUR = 2
        const val STEP_NET_IN = 3; const val STEP_NET_TURN = 4; const val STEP_NET_OUT = 5

        private const val IN_BAG = 0; private const val SLIDING = 1; private const val FALLING = 2
        private const val IN_NET = 3; private const val RELEASED = 4

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
        private const val SINK_CM_S = 9.0
        private const val SIDE_KEEP_CM = 1.0
        private const val GLASS_KEEP_CM = 0.8
        /** Back strip: the HOB lip, its intake and the back powerhead hang there. */
        private const val BACK_KEEP_CM = 5.5
        /** Waterline-relative depths (× radius) probed against the rock: bulb centre, then the bottom. */
        private val BULB_PROBES = doubleArrayOf(-0.7, -1.25)

        /** The bag's inside (× radius, its own frame): the water ball's centre depth and radius, the water top. */
        const val BULB_C = 0.72
        const val BAG_IN = 0.94
        const val BAG_TOP = 0.08
        /** The cut neck (× radius above the waterline centre), and how far over it is tipped to pour. */
        const val MOUTH = 0.6
        const val POUR_RAD = 2.1
        /** The net's mesh bag as a ball (× hoop radius): centre depth, radius, and the top they stay under. */
        const val NET_C = 0.35
        const val NET_IN = 0.88
        const val NET_TOP = 0.05
        const val NET_MIN_R = 4.0
        const val NET_MAX_R = 10.0
        private const val NET_TURN_RAD = 1.3
        /** The bucket's bottom above the tank water: the bag water never touches the tank. */
        const val BUCKET_CLEAR_CM = 1.2

        /** Release timeline (wall s from the start): lift out, cut, tip, pour, net in, turn, out. */
        const val T_LIFT = 1.4
        const val T_CUT = 2.0
        const val T_TIP = 3.4
        const val T_POUR = 4.8
        const val T_NET_IN = 6.2
        const val T_NET_TURN = 7.0
        const val T_END = 8.0
        private const val BAG_AWAY_S = 0.8
        private const val SLIDE_S = 0.3
        private const val FALL_S = 0.35

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

        /** Bulb radius (cm) for [n] animals of [cm] each: the whole body has to fit, and more animals need more water. */
        fun bagRadius(cm: Double, n: Int): Double =
            ((max(0.66 * cm + 0.6, 3.0) * (1 + 0.25 * (cbrt(n.toDouble()) - 1))) + 1.0).coerceIn(4.5, 14.0)

        private fun smooth(t: Double): Double { val x = t.coerceIn(0.0, 1.0); return x * x * (3 - 2 * x) }
    }
}
