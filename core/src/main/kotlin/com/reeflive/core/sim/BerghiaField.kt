package com.reeflive.core.sim

import com.reeflive.core.geom.Quat
import com.reeflive.core.geom.Rng
import com.reeflive.core.geom.Sdf
import com.reeflive.core.geom.Vec3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The Berghia nudibranchs the keeper can see (render side, like [CritterField]; nothing here feeds back into the sim).
 * The sim keeps the population ([PestState.berghia]) and which aiptasia it is eating ([SimSnapshot.berghiaPrey]);
 * this field shows up to [CAP] of them: after lights-out they leave their crevices and glide over the rock to that
 * polyp and crowd round its base while it shrinks; by day all but a hungry one or two crawl back into the rock
 * (aiptasia_control.md §6: nocturnal grazers that hide by day; supplier notes). Not thread-safe (render thread).
 */
class BerghiaField(private val sdf: Sdf?, seed: Long = 11L) {
    private val rng = Rng(seed)
    private val slugs = ArrayList<Slug>()
    private val poses = ArrayList<CritterPose>()
    private var nextId = 0
    private var spawnCooldown = 0.0

    val active: List<CritterPose> get() = poses

    private class Slug(val id: Int, val lengthCm: Double) {
        var head = Vec3.ZERO
        var heading = Vec3.X
        var normal = Vec3.Y
        val trail = ArrayList<Vec3>()
        var state = OUT
        var timer = 0.0
        var limb = 0.0
        var phase = 0.0
        var angle = 0.0
    }

    /**
     * Advances by [dt] wall seconds (already scaled by sim speed). [count] is the sim's population, [prey] / [preyNormal]
     * the polyp they are eating (null: none, they wander and go home), [night] lights-out.
     */
    fun update(count: Float, prey: Vec3?, preyNormal: Vec3?, night: Boolean, dt: Double): List<CritterPose> {
        val h = dt.coerceIn(0.0, 0.1)
        val pop = count.toDouble().roundToInt()
        val want = when {
            sdf == null || pop <= 0 -> 0
            night -> min(CAP, pop)
            prey != null -> min(DAY_CAP, pop)   // a hungry one or two keep at it by day
            else -> 0
        }
        val out = slugs.count { it.state != HOME && it.state != GONE }
        spawnCooldown -= h
        if (out < want && spawnCooldown <= 0 && prey != null) {
            spawn(prey, preyNormal ?: Vec3.Y)
            spawnCooldown = rng.range(2.0, 6.0)
        } else if (out > want) {
            slugs.firstOrNull { it.state != HOME && it.state != GONE }?.let { it.state = HOME; it.timer = 0.0 }
        }
        for (s in slugs) step(s, h, prey, preyNormal)
        slugs.removeAll { it.state == GONE }
        while (poses.size < slugs.size) poses += CritterPose()
        while (poses.size > slugs.size) poses.removeAt(poses.size - 1)
        for (i in slugs.indices) fill(slugs[i], poses[i])
        return poses
    }

    /** Out of a crack in the rock 6-14 cm from the prey. */
    private fun spawn(prey: Vec3, n: Vec3) {
        val s = sdf ?: return
        val k = CritterKind.BERGHIA
        repeat(10) {
            val off = Vec3(rng.signed(), rng.signed() * 0.6, rng.signed()).normalized() * rng.range(6.0, 14.0)
            val q = s.project(prey + off)
            if (abs(s.dist(q)) > 0.3 || q.y < prey.y - 12) return@repeat
            val slug = Slug(nextId++, rng.range(k.minCm, k.maxCm))
            slug.normal = s.gradient(q).normalized(n)
            slug.head = q + slug.normal * (k.liftBL * slug.lengthCm)
            slug.heading = (prey - q).rejectFrom(slug.normal).normalized(Vec3.X)
            slug.angle = rng.range(0.0, 2 * PI)
            slug.phase = rng.range(0.0, 1.0)
            slug.state = OUT
            slugs += slug
            return
        }
    }

    private fun step(s: Slug, h: Double, prey: Vec3?, preyNormal: Vec3?) {
        s.phase += 0.3 * h
        when (s.state) {
            OUT -> {
                if (prey == null) { s.state = HOME; return }
                // each takes its own place round the polyp's foot, head in
                val n = preyNormal ?: Vec3.Y
                val a = (if (abs(n.y) < 0.9) Vec3.Y else Vec3.X).rejectFrom(n).normalized()
                val goal = prey + Vec3.rotateAbout(a, n, s.angle) * FEED_RING_CM
                if (s.head.distanceTo(goal) < 0.6) { s.state = FEED; s.timer = 0.0; return }
                steer(s, goal, h)
                s.limb += 1.2 * h
                crawl(s, CritterKind.BERGHIA.speedCmS * h)
            }
            FEED -> {
                if (prey == null) { s.state = HOME; return }
                // head on the polyp, rocking slightly while it feeds
                s.limb += 0.4 * h
                s.heading = Vec3.rotateAbout((prey - s.head).rejectFrom(s.normal).normalized(s.heading), s.normal, 0.15 * sin(s.phase * 2 * PI))
                if (s.head.distanceTo(prey) > FEED_RING_CM * 3) s.state = OUT
            }
            HOME -> {
                // glide a little way, then slip into the rock
                s.timer += h
                s.limb += 1.2 * h
                if (s.timer < 4.0) crawl(s, CritterKind.BERGHIA.speedCmS * h)
                else { s.head = s.head - s.normal * (0.4 * h); if (s.timer > 7.0) s.state = GONE }
            }
        }
    }

    private fun steer(s: Slug, goal: Vec3, h: Double) {
        val to = (goal - s.head).rejectFrom(s.normal)
        if (to.lengthSq() < 1e-6) return
        val side = (s.normal cross s.heading) dot to.normalized()
        s.heading = Vec3.rotateAbout(s.heading, s.normal, (side * 1.5).coerceIn(-0.8, 0.8) * h + rng.signed() * 0.1 * h).normalized(s.heading)
    }

    /** Moves the head [d] cm along its heading on the rock, laying down the trail the body follows. */
    private fun crawl(s: Slug, d: Double) {
        val sd = sdf ?: return
        if (d <= 0) return
        val lift = CritterKind.BERGHIA.liftBL * s.lengthCm
        val old = s.head
        val q = sd.project(s.head + s.heading * d - s.normal * lift)
        if (abs(sd.dist(q)) > 0.4 || q.distanceTo(old) > 3 * d + 0.5) {
            s.heading = Vec3.rotateAbout(s.heading, s.normal, 0.6)
            return
        }
        val n = sd.gradient(q).normalized(s.normal)
        s.normal = n
        s.head = q + n * lift
        s.heading = (s.head - old).rejectFrom(n).normalized(s.heading)
        val last = s.trail.firstOrNull() ?: run { s.trail.add(0, old); old }
        if (s.head.distanceTo(last) >= s.lengthCm / 12) s.trail.add(0, s.head)
        while (s.trail.size > 16) s.trail.removeAt(s.trail.size - 1)
    }

    private fun fill(s: Slug, out: CritterPose) {
        out.id = "berghia:${s.id}"
        out.kind = CritterKind.BERGHIA
        out.scale = s.lengthCm.toFloat()
        val seg = s.lengthCm / 15.0
        var prev = s.head
        var j = 0
        val stations = STATIONS
        stations[0] = s.head
        for (i in 1 until 16) {
            var need = seg
            var p = prev
            while (need > 0) {
                val t = if (j < s.trail.size) s.trail[j] else null
                if (t == null) { p = p - s.heading * need; break }
                val d = p.distanceTo(t)
                if (d >= need) { p = p.lerp(t, need / d); need = 0.0 } else { need -= d; p = t; j++ }
            }
            stations[i] = p; prev = p
        }
        for (i in 0 until 16) {
            val p = stations[i]
            out.spine[i * 3] = p.x.toFloat(); out.spine[i * 3 + 1] = p.y.toFloat(); out.spine[i * 3 + 2] = p.z.toFloat()
            val fwd = when (i) {
                0 -> stations[0] - stations[1]
                15 -> stations[14] - stations[15]
                else -> stations[i - 1] - stations[i + 1]
            }.normalized(s.heading)
            val q = Quat.fromForwardUp(fwd, s.normal)
            out.frames[i * 4] = q.x.toFloat(); out.frames[i * 4 + 1] = q.y.toFloat(); out.frames[i * 4 + 2] = q.z.toFloat(); out.frames[i * 4 + 3] = q.w.toFloat()
        }
        out.swimPhase = (s.phase % 1000.0).toFloat()
        out.appendage = (s.limb % 1000.0).toFloat()
        // the foot's slow muscular wave; feeding barely moves
        out.amp = if (s.state == FEED) 0.004f else 0.012f
        out.waveLen = 2f
    }

    companion object {
        const val CAP = 8
        const val DAY_CAP = 2
        /** How far from the polyp's centre the feeders' heads sit (cm). */
        const val FEED_RING_CM = 0.9
        private const val OUT = 0
        private const val FEED = 1
        private const val HOME = 2
        private const val GONE = 3
        private val STATIONS = Array(16) { Vec3.ZERO }
    }
}
