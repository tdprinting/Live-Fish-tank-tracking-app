package com.reeflive.core.sim

import com.reeflive.core.geom.FlowField
import com.reeflive.core.geom.Rng
import com.reeflive.core.geom.Vec3

class FoodParticle(
    val id: Int,
    val kind: FoodKind,
    var pos: Vec3,
    var vel: Vec3,
    val bornAt: Double,
    val feedId: Int,
    /** Sim time the particle entered the water ([bornAt] carries a random expiry offset); drives the renderer's thaw / flutter / dispersal. */
    val spawnedAt: Double = bornAt,
) {
    val grams: Double = kind.gramsPerParticle
    var settled = false
    var eaten = false
    /** Organism currently targeting this particle (first to arrive wins; others may still race). */
    var claimedBy: String? = null
    val onSurface: Boolean get() = !settled && vel.y == 0.0 && pos.y > 50
}

/** A nori sheet on the clip (SIMULATION §4.6): fish nibble it until [remaining] reaches 0. */
class NoriSheet(val pos: Vec3, var remaining: Double, val bornAt: Double)

/**
 * Food particles (SIMULATION §4.6): spawn, sink/float with the flow, settle on rock or sand,
 * expire to detritus after [FoodKind.lifeSeconds] (20 min for most; a wafer lasts hours). Max 400 particles.
 */
class FoodSystem(private val rock: RockData, private val flow: FlowField, private val rng: Rng) {
    val particles = ArrayList<FoodParticle>()
    var nori: NoriSheet? = null
    private var nextId = 1
    private var nextFeed = 1
    var lastFeedTime = -1e9; private set
    var lastFeedPos: Vec3 = Vec3.ZERO; private set
    var lastFeedKind: FoodKind = FoodKind.FLAKE; private set

    private data class FeedRecord(val id: Int, val spawned: Int, var remaining: Int, val at: Double)
    private val feeds = ArrayList<FeedRecord>()

    /** Uneaten grams expiring this tick; the Simulation turns them into detritus (SIMULATION §5.5). */
    var expiredGrams = 0.0

    fun spawn(kind: FoodKind, grams: Double, at: Vec3, simTime: Double): Int {
        val fid = nextFeed++
        lastFeedTime = simTime; lastFeedPos = at; lastFeedKind = kind
        if (kind == FoodKind.NORI) {
            nori = NoriSheet(rock.anchors.pos("NORI_CLIP", Vec3(2.0, 35.0, 18.0)), 1.0, simTime)
            return fid
        }
        val count = minOf((grams / kind.gramsPerParticle).toInt().coerceAtLeast(1), MAX - particles.size).coerceAtLeast(0)
        val surface = at.y >= rock.waterLevelCm - 2
        for (i in 0 until count) {
            val spread = if (kind.suspended) 6.0 else 2.5
            var p = at + Vec3(rng.gaussian() * spread, if (surface) 0.0 else rng.gaussian() * 1.0, rng.gaussian() * spread)
            p = rock.clampToWater(p, 0.5)
            if (surface) p = p.withY(rock.waterLevelCm - 0.3)
            val vy = if (surface && kind.floats) 0.0 else -kind.sinkCmS
            // a loose pinch is caught by the current it lands in at once (video T97: the return sweeps it away)
            val w = if (kind.floats && surface) Vec3.ZERO else flow.velocityFast(p)
            particles += FoodParticle(nextId++, kind, p, Vec3(w.x, vy + w.y, w.z), simTime + rng.nextDouble() * 10, fid, spawnedAt = simTime)
        }
        if (count > 0) feeds += FeedRecord(fid, count, count, simTime)
        return fid
    }

    fun step(dt: Double, t: Double) {
        expiredGrams = 0.0
        val it = particles.iterator()
        while (it.hasNext()) {
            val f = it.next()
            if (f.eaten) { it.remove(); continue }
            val age = t - f.bornAt
            if (age > f.kind.lifeSeconds) {
                expiredGrams += f.grams
                it.remove(); continue
            }
            if (f.settled) continue
            val w = flow.velocityFast(f.pos)
            val floating = f.kind.floats && age < f.kind.floatSeconds && f.pos.y > rock.waterLevelCm - 1
            val sink = if (floating) 0.0 else f.kind.sinkCmS
            val target = if (floating) Vec3(w.x, 0.0, w.z) else w * (if (f.kind.suspended) 1.0 else 0.7) + Vec3(0.0, -sink, 0.0)
            val k = (2.0 * dt).coerceAtMost(1.0)
            f.vel = f.vel + (target - f.vel) * k + (if (f.kind.suspended) rng.unitVector() * (1.2 * dt) else Vec3.ZERO)
            var p = f.pos + f.vel * dt
            if (floating) p = p.withY(rock.waterLevelCm - 0.3)
            p = rock.clampToWater(p, 0.3)
            if (!f.kind.suspended) {
                if (rock.sdf.dist(p) < 0.3) {
                    p = rock.sdf.project(p, 0.3); f.settled = true; f.vel = Vec3.ZERO
                } else if (p.y <= rock.sandTopCm + 0.35) {
                    p = p.withY(rock.sandTopCm + 0.3); f.settled = true; f.vel = Vec3.ZERO
                }
            }
            f.pos = p
        }
        nori?.let { if (it.remaining <= 0.01 || t - it.bornAt > 4 * 3600) nori = null }
    }

    fun consume(f: FoodParticle) {
        if (f.eaten) return
        f.eaten = true
        feeds.firstOrNull { it.id == f.feedId }?.let { it.remaining--; if (it.remaining <= 0) feeds.remove(it) }
    }

    fun active(): List<FoodParticle> = particles.filter { !it.eaten }

    fun nearest(p: Vec3, radius: Double, filter: (FoodParticle) -> Boolean): FoodParticle? {
        var best: FoodParticle? = null
        var bd = radius * radius
        for (f in particles) {
            if (f.eaten || !filter(f)) continue
            val d = f.pos.distanceSqTo(p)
            if (d < bd) { bd = d; best = f }
        }
        return best
    }

    fun countNear(p: Vec3, radius: Double): Int = particles.count { !it.eaten && it.pos.distanceSqTo(p) < radius * radius }

    val any: Boolean get() = particles.any { !it.eaten } || nori != null

    fun clear() { particles.clear(); nori = null; feeds.clear() }

    /**
     * Per live particle, in [export] order: seconds since it entered the water, 1 when settled on rock / sand (else 0),
     * and a stable 0..1 seed from its id ([META_STRIDE] floats each). Visual only: the renderer uses it for each food's
     * look (a frozen cube thawing, a flake tumbling, a cloud of phyto spreading out).
     */
    fun exportMeta(t: Double): FloatArray {
        val live = particles.filter { !it.eaten }
        val out = FloatArray(live.size * META_STRIDE)
        live.forEachIndexed { i, f ->
            out[i * META_STRIDE] = (t - f.spawnedAt).coerceAtLeast(0.0).toFloat()
            out[i * META_STRIDE + 1] = if (f.settled) 1f else 0f
            out[i * META_STRIDE + 2] = ((f.id * 2654435761L) ushr 8 and 0xFFFFL).toFloat() / 65535f
        }
        return out
    }

    /** Flattened xyz and kinds for the snapshot. */
    fun export(): Pair<FloatArray, ByteArray> {
        val live = particles.filter { !it.eaten }
        val xyz = FloatArray(live.size * 3)
        val kinds = ByteArray(live.size)
        live.forEachIndexed { i, f ->
            xyz[i * 3] = f.pos.x.toFloat(); xyz[i * 3 + 1] = f.pos.y.toFloat(); xyz[i * 3 + 2] = f.pos.z.toFloat()
            kinds[i] = f.kind.ordinal.toByte()
        }
        return xyz to kinds
    }

    companion object {
        const val MAX = 400
        /** Floats per particle in [exportMeta]: age (s), settled, seed. */
        const val META_STRIDE = 3
    }
}
