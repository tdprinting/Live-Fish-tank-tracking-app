package com.reeflive.core.sim

import com.reeflive.core.geom.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The fish net as the renderer sees it ([SimSnapshot.net]). ([x], [y], [z]) is the hoop centre (tank cm); the
 * mouth faces ([nx], [ny], [nz]) and the mesh bag trails behind it. The handle leaves the top of the hoop along
 * ([hx], [hy], [hz]) and out through the open top. [phase] is [FishNet.DIP] … [FishNet.OUT] with [t] 0..1 through it.
 */
data class NetPose(
    val x: Float, val y: Float, val z: Float,
    val nx: Float, val ny: Float, val nz: Float,
    val hx: Float, val hy: Float, val hz: Float,
    val radius: Float,
    val phase: Int,
    val t: Float,
    /** The animal it is going for (and, once scooped, carrying). */
    val targetId: String,
    /** True once the body is in the bag. */
    val carrying: Boolean,
)

/**
 * The last body the net took out ([SimSnapshot.netFlush]): the page shows it going down the toilet. [seq] counts
 * up by one per body so the UI can tell a new one from the last. [body] / [accent] are the cartoon's colours
 * (0xRRGGBB, [FishNet.cartoonColors]) so Android and the web draw the same little fish.
 */
data class NetFlush(
    val seq: Int, val id: String, val name: String, val speciesId: String, val common: String, val isFish: Boolean,
    val body: Int, val accent: Int,
)

/**
 * Taking a dead animal out with a fish net ([Command.NetOut]), the way a keeper does it: the net dips in beside the
 * body, sweeps across so it slides into the bag, turns mouth-up and lifts out through the open top. Once it is out
 * the animal is removed from the sim (so its carcass stops leaching ammonia, see Simulation.corpses) and
 * [lastFlush] tells the UI to play the toilet goodbye.
 *
 * Runs on wall time like the bags ([AcclimationBags]) so it looks the same at any sim speed. The hoop is kept off the
 * rock, the sand and the glass (a sphere of the hoop's radius), the sweep direction is picked so the handle and the
 * path down stay clear of rock, and living animals are pushed out of the hoop's way ([keepClear]). Tapping more dead
 * animals queues them; the net fetches them one after another.
 */
class FishNet {
    private class Job(
        val id: String,
        val radius: Double,
        /** Sweep direction (horizontal unit). */
        val dir: Vec3,
        /** Handle direction (unit, up and back). */
        val handle: Vec3,
        /** Above-water start, the dip point beside the body, and the hoop's stop just past the body. */
        val start: Vec3,
        val approach: Vec3,
        val end: Vec3,
    ) {
        var phase = DIP
        var t = 0.0
        var pos: Vec3 = start
        var facing: Vec3 = Vec3(0.0, -1.0, 0.0)
        var carrying = false
        var captureT = 0.0
        var captureFrom: Vec3 = Vec3.ZERO
        var liftFrom: Vec3 = Vec3.ZERO
    }

    private var job: Job? = null
    private val queue = ArrayList<String>()
    private var seq = 0

    /** The last body flushed, or null if none yet. */
    var lastFlush: NetFlush? = null
        private set

    val busy: Boolean get() = job != null

    /** Ids the net is fetching or will fetch next. */
    fun pending(): List<String> = (job?.let { listOf(it.id) } ?: emptyList()) + queue

    /** Queues [id] for the net if it is a dead, loose animal not already queued. Returns whether it was taken. */
    fun request(sim: Simulation, id: String): Boolean {
        val o = sim.organisms.firstOrNull { it.id == id } ?: return false
        if (o.alive || o.netted || job?.id == id || id in queue) return false
        queue += id
        if (job == null) next(sim)
        return true
    }

    fun clear() {
        job = null; queue.clear()
    }

    private fun next(sim: Simulation) {
        while (queue.isNotEmpty()) {
            val id = queue.removeAt(0)
            val o = sim.organisms.firstOrNull { it.id == id } ?: continue
            if (o.alive) continue
            job = plan(sim, o)
            o.netted = true
            o.seatOn = false
            return
        }
        job = null
    }

    /** Picks the sweep direction whose path (down beside the body, across it, handle out the top) is clearest. */
    private fun plan(sim: Simulation, o: Organism): Job {
        val rock = sim.rock
        val r = (o.collisionLength * 0.75).coerceIn(MIN_RADIUS, MAX_RADIUS)
        val p = o.pos
        var best: Job? = null
        var bestScore = Double.NEGATIVE_INFINITY
        for (k in 0 until DIRS) {
            val a = 2 * PI * k / DIRS + 0.3
            val d = Vec3(cos(a), 0.0, sin(a))
            val handle = (Vec3.Y + d * -0.35).normalized(Vec3.Y)
            val end = hoopSpot(sim, p + d * (r * 0.5), r)
            val approach = hoopSpot(sim, p - d * (r * 1.9), r)
            val start = approach.withY(rock.waterLevelCm + ABOVE_CM)
            var score = 0.0
            var worst = Double.POSITIVE_INFINITY
            // straight down to the dip point, then across the body
            for (i in 0..8) worst = min(worst, rock.sdf.dist(start.lerp(approach, i / 8.0)) - r)
            for (i in 0..6) worst = min(worst, rock.sdf.dist(approach.lerp(end, i / 6.0)) - r)
            // the handle from the top of the hoop out through the open top, at the dip point and at the end
            for (h in listOf(approach, end)) {
                val top = h + Vec3.Y * r
                for (i in 0..10) {
                    val q = top + handle * (i * 3.0)
                    if (q.y > rock.waterLevelCm + 4) break
                    worst = min(worst, rock.sdf.dist(q) - HANDLE_R)
                    worst = min(worst, glassGap(rock, q) - HANDLE_R)
                }
            }
            // how far the hoop had to be pushed off the body (rock, glass or sand in the way)
            score -= approach.distanceTo(p - d * (r * 1.9)) * 0.4 + end.distanceTo(p + d * (r * 0.5))
            score += min(worst, 4.0) * 2
            // prefer sweeping toward the front glass a little: the keeper's view
            score += d.z * 0.3
            if (score > bestScore) {
                bestScore = score
                best = Job(o.id, r, d, handle, start, approach, end)
            }
        }
        return best!!
    }

    /** [p] moved where a hoop sphere of radius [r] fits: inside the glass, above the sand, under the surface, off rock. */
    private fun hoopSpot(sim: Simulation, p: Vec3, r: Double): Vec3 {
        val rock = sim.rock
        var q = p
        repeat(3) {
            if (rock.sdf.dist(q) < r) q = rock.sdf.project(q, r)
            q = clampGlass(rock, q, r)
            val sandY = sim.sand.surfaceY(q.x, q.z)
            q = q.withY(q.y.coerceIn(sandY + r * 0.85 + 0.3, rock.waterLevelCm - 0.4))
        }
        return q
    }

    private fun clampGlass(rock: RockData, p: Vec3, r: Double) = Vec3(
        p.x.coerceIn(r + GLASS_KEEP, rock.lengthCm - r - GLASS_KEEP),
        p.y,
        p.z.coerceIn(r + GLASS_KEEP, rock.widthCm - r - GLASS_KEEP),
    )

    private fun glassGap(rock: RockData, q: Vec3) = minOf(q.x, rock.lengthCm - q.x, q.z, rock.widthCm - q.z)

    fun stepWall(sim: Simulation, dt: Double) {
        val j = job ?: return
        val o = sim.organisms.firstOrNull { it.id == j.id }
        if (o == null && !j.carrying) { next(sim); return }   // gone (the clean-up crew got there first)
        val rock = sim.rock
        val water = rock.waterLevelCm
        val wasY = j.pos.y
        j.t += dt / DURATION[j.phase]
        val e = ease(min(j.t, 1.0))
        when (j.phase) {
            DIP -> {
                // down beside the body, rim first, turning to face the sweep
                j.pos = j.start.lerp(j.approach, e)
                j.facing = Vec3(0.0, -1.0, 0.0).lerp(j.dir, smooth(e * 1.4)).normalized(j.dir)
                if (wasY >= water && j.pos.y < water) {
                    sim.surface.impulse(j.pos.x, j.pos.z, 0.5, j.radius * 1.2)
                    // everyone nearby darts off from the shadow and the splash
                    sim.scareFrom(j.pos)
                }
            }
            SWEEP -> {
                j.pos = j.approach.lerp(j.end, e)
                j.facing = j.dir
                if (!j.carrying && o != null && ((o.pos - j.pos) dot j.dir) < j.radius * 0.2) {
                    j.carrying = true; j.captureT = 0.0; j.captureFrom = o.pos
                }
            }
            LIFT -> {
                // mouth turns up so the body rolls into the bottom of the bag, then straight up and out
                j.facing = j.dir.lerp(Vec3.Y, smooth(min(1.0, j.t * 1.6))).normalized(Vec3.Y)
                j.pos = j.liftFrom.withY(lerpD(j.liftFrom.y, water + LIFT_CM, e))
                if (wasY < water && j.pos.y >= water) sim.surface.impulse(j.pos.x, j.pos.z, 0.45, j.radius * 1.2)
            }
            OUT -> {
                j.pos = j.liftFrom.withY(water + LIFT_CM + OUT_CM * e)
            }
        }
        // keep the hoop itself off the rock and the glass while it is in the water
        if (j.pos.y < water + j.radius) {
            if (rock.sdf.dist(j.pos) < j.radius) j.pos = rock.sdf.project(j.pos, j.radius)
            j.pos = clampGlass(rock, j.pos, j.radius)
        }
        if (j.carrying && o != null) carry(sim, j, o, dt)
        if (j.t >= 1.0) {
            j.t = 0.0
            if (j.phase == SWEEP && !j.carrying && o != null) { j.carrying = true; j.captureT = 0.0; j.captureFrom = o.pos }
            if (j.phase < OUT) { j.phase++; if (j.phase == LIFT) j.liftFrom = j.pos }
            else finish(sim, j, o)
        }
    }

    /** The body slides into the bag, then rides at its bottom. */
    private fun carry(@Suppress("UNUSED_PARAMETER") sim: Simulation, j: Job, o: Organism, dt: Double) {
        j.captureT = min(1.0, j.captureT + dt / CAPTURE_SEC)
        val bag = j.pos - j.facing * (j.radius * BAG_DEPTH * 0.75)
        o.pos = if (j.captureT < 1.0) j.captureFrom.lerp(bag, smooth(j.captureT)) else bag
        o.vel = Vec3.ZERO; o.amp = 0.0; o.tailHz = 0.0
        o.spline.override = null
        o.spline.update(o.pos, o.heading, dt, 3.0)
    }

    private fun finish(sim: Simulation, j: Job, o: Organism?) {
        if (o != null) {
            seq++
            val (body, accent) = cartoonColors(o.species.id, o.isFish)
            lastFlush = NetFlush(seq, o.id, o.nickname.ifBlank { o.species.common }, o.species.id, o.species.common, o.isFish, body, accent)
            sim.removeOrganism(o)
        }
        job = null
        next(sim)
    }

    /** Pushes living animals out of the hoop and bag (no fish swims through the net). */
    fun keepClear(sim: Simulation) {
        val j = job ?: return
        if (j.pos.y > sim.rock.waterLevelCm + j.radius) return
        val c = j.pos - j.facing * (j.radius * 0.4)
        val rock = sim.rock
        for (o in sim.organisms) {
            if (!o.alive || o.attached || o.id == j.id) continue
            val need = j.radius * 1.15 + 0.3 * o.collisionLength + 0.3
            val dx = o.pos.x - c.x; val dy = o.pos.y - c.y; val dz = o.pos.z - c.z
            val d2 = dx * dx + dy * dy + dz * dz
            if (d2 >= need * need) continue
            val d = sqrt(d2)
            var n = Vec3(dx, dy, dz)
            n = if (d < 1e-6) Vec3(0.0, -1.0, 0.0) else n / d
            val vn = o.vel dot n
            if (vn < 0) o.vel = o.vel - n * vn
            var p = rock.clampToWater(c + n * need, 1.0)
            if (rock.sdf.dist(p) < 0.5) p = rock.sdf.project(p, 0.5)
            o.pos = p
        }
    }

    fun pose(): NetPose? {
        val j = job ?: return null
        val p = j.pos; val f = j.facing; val h = j.handle
        return NetPose(
            p.x.toFloat(), p.y.toFloat(), p.z.toFloat(),
            f.x.toFloat(), f.y.toFloat(), f.z.toFloat(),
            h.x.toFloat(), h.y.toFloat(), h.z.toFloat(),
            j.radius.toFloat(), j.phase, min(j.t, 1.0).toFloat(), j.id, j.carrying,
        )
    }

    companion object {
        const val DIP = 0; const val SWEEP = 1; const val LIFT = 2; const val OUT = 3

        /** Wall seconds per phase: dip in, sweep across, lift out, carry away. */
        private val DURATION = doubleArrayOf(1.7, 1.2, 1.7, 0.9)
        /** Total wall time from tap to flush. */
        val TOTAL_SEC: Double get() = DURATION.sum()

        /** Hoop radius (cm) from the body length: a 3-5 in aquarium net. */
        const val MIN_RADIUS = 4.0
        const val MAX_RADIUS = 7.0
        /** Bag depth as a multiple of the hoop radius (the renderer's mesh matches). */
        const val BAG_DEPTH = 1.25
        const val HANDLE_R = 0.6
        private const val GLASS_KEEP = 0.6
        private const val ABOVE_CM = 10.0
        private const val LIFT_CM = 9.0
        private const val OUT_CM = 30.0
        private const val CAPTURE_SEC = 0.45
        private const val DIRS = 12

        /** Body and fin/stripe colours (0xRRGGBB) for the goodbye cartoon, by genus; a warm orange otherwise. */
        fun cartoonColors(speciesId: String, isFish: Boolean): Pair<Int, Int> = when (speciesId.substringBefore('_')) {
            "amphiprion" -> 0xFF8A1F to 0xFFFFFF
            "paracanthurus" -> 0x2F6BFF to 0xFFD23F
            "sphaeramia" -> 0xE8C86A to 0xB0412E
            "labroides" -> 0x7CC8F2 to 0x1C1C2A
            "cirrhitichthys" -> 0xF2EEE6 to 0xD8453A
            "salarias" -> 0x9C946E to 0x5E5840
            "ecsenius" -> 0xD9B98C to 0x3A2F2A
            "pseudochromis" -> 0x5B3FD6 to 0xF07A2A
            "macropharyngodon" -> 0x3E9B66 to 0x1F3FA8
            "valenciennea" -> 0xF2F2EC to 0x58B7E8
            "pholidichthys" -> 0x2E2E36 to 0xF2F2F2
            else -> if (isFish) 0xFFB13D to 0xFFE14D else 0xE8574A to 0xFFD2B0
        }

        private fun ease(x: Double) = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).let { it * it * it } / 2
        private fun smooth(x: Double): Double { val c = x.coerceIn(0.0, 1.0); return c * c * (3 - 2 * c) }
        private fun lerpD(a: Double, b: Double, t: Double) = a + (b - a) * t
    }
}
