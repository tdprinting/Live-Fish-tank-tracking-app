package com.reeflive.core.sim

import com.reeflive.core.geom.Quat
import com.reeflive.core.geom.Rng
import com.reeflive.core.geom.Vec3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * The bristleworms you can see (research 42 §B, 17 §12): a handful of individuals drawn from the
 * aggregate [MicrofaunaModel] population, simulated in the sim (not just drawn) so predators can find and catch them
 * and they can eat the food that reaches the bottom.
 *
 * How many are out follows the population and its diel exposure (half of them crawl about at night, a few heads poke
 * out of the rock by day) plus food on the bottom, which brings them out at any hour within minutes of a feed. Each
 * worm lives in a crevice in the rock (or a hole in the sand at the rock's foot), glides out head first along the
 * surface at 5-20 mm/s laying down a trail, wanders, makes for any settled food within reach and eats it, and backs
 * down its own trail into the hole at 30-50 mm/s when the lights come on, a fish noses at it, its outing ends or it is
 * full. Touched, its white chaetae flare. A predator that reaches its head ([catch]) pulls it out and the population
 * loses one.
 *
 * Only the visible few are individuals: the hundreds of an overfed tank stay one number, so a worm plague costs no more
 * CPU than a lean tank. Off in the abstract (fast-forward) mode. Allocation is a few Vec3 per worm per tick, at most
 * [CAP] worms. Deterministic: its own random stream.
 */
class WormField(private val sim: Simulation, seed: Long) {
    private val rng = Rng(seed xor 0xB215L)

    class Worm(val id: Int, val lengthCm: Double) {
        val key = "worm:$id"
        var hole = Vec3.ZERO
        var holeN = Vec3.Y
        var onSand = false
        var head = Vec3.ZERO
        var heading = Vec3.X
        var normal = Vec3.Y
        /** Head positions behind the current one, newest first, about [spacing] apart; it backs down them home. */
        val trail = ArrayList<Vec3>()
        val spacing: Double get() = lengthCm / 12
        var state = EMERGE
        var timer = 0.0
        var phase = 0.0
        var limb = 0.0
        /** Chaetae flared 0..1 (touched, or being grabbed). */
        var flare = 0.0
        /** Grams of food eaten this outing (a full worm goes home). */
        var ate = 0.0
        /** The animal holding it (CAUGHT) and how long it has been held. */
        var heldBy: Organism? = null
        var held = 0.0
        /** Out of its hole and in reach (not on its way in, not caught). */
        val exposed: Boolean get() = state == EMERGE || state == ACTIVE
    }

    val worms = ArrayList<Worm>()
    private var nextId = 0
    private var cooldown = 0.0
    private var holes: List<Pair<Vec3, Vec3>> = emptyList()
    private var holesRev = -1
    /** How many should be out now (tests, HUD). */
    var target = 0; private set
    /** Worms caught by predators, and food particles the worms ate (tests, diagnostics). */
    var caught = 0; private set
    var foodEaten = 0; private set

    /** Worms out and in reach. */
    fun exposedCount(): Int { var n = 0; for (w in worms) if (w.exposed) n++; return n }

    /** The nearest exposed worm whose head is within [r] cm of [p], or null. */
    fun nearest(p: Vec3, r: Double): Worm? {
        var best: Worm? = null
        var bd = r * r
        for (w in worms) {
            if (!w.exposed) continue
            val d = w.head.distanceSqTo(p)
            if (d < bd) { bd = d; best = w }
        }
        return best
    }

    /**
     * [by] grabs worm [w]: it flares and is pulled from the rock over a second or two, and the population loses one.
     * False if it is no longer exposed (already caught, or back in its hole).
     */
    fun catch(w: Worm, by: Organism): Boolean {
        if (!w.exposed) return false
        w.state = CAUGHT; w.heldBy = by; w.held = 0.0; w.flare = 1.0
        sim.water.micro.remove(MicroGroup.BRISTLEWORM, 1.0)
        caught++
        return true
    }

    /** Clears every visible worm (abstract mode, a tank reset). */
    fun clear() { worms.clear() }

    fun step(dt: Double) {
        val h = dt.coerceIn(0.0, 0.5)
        ensureHoles()
        val m = sim.water.micro.snapshot()
        target = wanted(m)
        cooldown -= h
        val out = worms.count { it.exposed }
        if (out < target && cooldown <= 0 && holes.isNotEmpty()) {
            spawn()
            cooldown = rng.range(2.0, 8.0)
        } else if (out > target) {
            worms.firstOrNull { it.exposed }?.let { it.state = RETREAT }
        }
        for (w in worms) stepWorm(w, h)
        worms.removeAll { it.state == GONE }
    }

    /**
     * How many to show: `CAP · E / (E + HALF)` of the expected number out (population × diel exposure), as the view of
     * the in-tank microfauna does, plus up to three more drawn out by food on the bottom (by day too). None while the
     * population is absent.
     */
    private fun wanted(m: MicrofaunaState): Int {
        val gi = MicroGroup.BRISTLEWORM.ordinal
        if (!m.present[gi]) return 0
        val total = m.total[gi]
        var e = total * m.exposed[gi]
        if (settledFood() > 0) e += min(3.0, LURE_SHARE * total)
        val n = floor(CAP * e / (e + HALF) + 0.35).toInt()
        return min(n, max(0, total.toInt()))
    }

    private fun settledFood(): Int {
        var n = 0
        for (f in sim.food.particles) if (!f.eaten && f.settled) n++
        return n
    }

    // ------------------------------------------------------------------ geometry

    private fun sandY(x: Double, z: Double) = sim.sand.surfaceY(x.coerceIn(0.0, sim.rock.lengthCm), z.coerceIn(0.0, sim.rock.widthCm))

    private fun inTank(p: Vec3) = p.x in 1.0..(sim.rock.lengthCm - 1) && p.z in 1.0..(sim.rock.widthCm - 1) && p.y < sim.rock.waterLevelCm - 2

    /** Crevices in the rock (high cover just off the surface) the worms live in; rebuilt when the rocks move. */
    private fun ensureHoles() {
        val rev = (sim.rock.sdf as? DynamicRockSdf)?.revision ?: 0
        if (holes.isNotEmpty() && rev == holesRev) return
        holesRev = rev
        val sdf = sim.rock.sdf
        val L = sim.rock.lengthCm; val W = sim.rock.widthCm
        val out = ArrayList<Pair<Vec3, Vec3>>()
        val any = ArrayList<Pair<Vec3, Vec3>>()
        var tries = 0
        while (out.size < HOLES && tries < 1500) {
            tries++
            val p = Vec3(rng.range(3.0, L - 3), rng.range(sim.rock.sandTopCm + 1, 0.8 * sim.rock.waterLevelCm), rng.range(2.0, W - 2))
            if (abs(sdf.dist(p)) > 10) continue
            val q = sdf.project(p)
            if (abs(sdf.dist(q)) > 0.3 || !inTank(q) || q.y < sandY(q.x, q.z) + 0.3) continue
            val n = sdf.gradient(q).normalized(Vec3.Y)
            if (any.size < HOLES) any += q to n
            if (sdf.cover(q + n * 0.8) > 0.45) out += q to n
        }
        holes = out.ifEmpty { any }
    }

    private fun spawn() {
        // adults 4-12 cm; a young population (just hitchhiked in, still growing) shows smaller worms (est.)
        val total = sim.water.micro.total(MicroGroup.BRISTLEWORM)
        val maxL = MIN_CM + (MAX_CM - MIN_CM) * (total / (total + 15.0)).coerceIn(0.3, 1.0)
        val w = Worm(nextId++, rng.range(MIN_CM, maxL))
        // a quarter live in the sand at the rock's foot, the rest in the rock (research 17 §12.4)
        val base = holes[rng.nextInt(holes.size)]
        if (rng.chance(SAND_SHARE)) {
            val x = (base.first.x + rng.signed() * 3).coerceIn(3.0, sim.rock.lengthCm - 3)
            val z = (base.first.z + rng.signed() * 3).coerceIn(2.0, sim.rock.widthCm - 2)
            val y = sandY(x, z)
            if (sim.rock.sdf.dist(Vec3(x, y + 0.5, z)) > 0.6) {
                w.onSand = true; w.hole = Vec3(x, y, z); w.holeN = Vec3.Y
            }
        }
        if (!w.onSand) { w.hole = base.first; w.holeN = base.second }
        w.normal = w.holeN
        w.head = w.hole + w.normal * lift(w)
        w.heading = randomTangent(w.normal)
        w.state = EMERGE
        w.timer = rng.range(OUTING_S * 0.5, OUTING_S * 1.5)
        w.phase = rng.nextDouble(); w.limb = rng.nextDouble()
        worms += w
    }

    private fun lift(w: Worm) = LIFT_BL * w.lengthCm

    // ------------------------------------------------------------------ behaviour

    private fun stepWorm(w: Worm, h: Double) {
        w.phase += 0.8 * h
        w.limb += 1.6 * h
        w.flare = max(0.0, w.flare - h * 0.5)
        when (w.state) {
            EMERGE, ACTIVE -> forage(w, h)
            RETREAT -> retreat(w, h)
            CAUGHT -> held(w, h)
        }
    }

    private fun forage(w: Worm, h: Double) {
        w.timer -= h
        // a fish nosing at it (not the predator closing in, which it does not sense in time): flare and back in
        if (fishNear(w.head, 2.5)) { w.flare = 1.0; w.limb += 6.0 * h; w.state = RETREAT; return }
        val lured = settledFood() > 0
        if (w.timer <= 0 || w.trail.size >= MAX_TRAIL || w.ate >= FULL_G || (!sim.perception.night && !lured && w.timer < OUTING_S * 0.8)) {
            w.state = RETREAT; return
        }
        val food = sim.food.nearest(w.head, FOOD_R) { it.settled }
        val turn = if (food != null) steerTo(w, food.pos) else rng.signed() * 0.7
        w.heading = Vec3.rotateAbout(w.heading, w.normal, turn * h)
        val speed = SPEED_CM_S * (if (food != null) 1.6 else 1.0)
        crawl(w, speed * h)
        if (food != null && food.pos.distanceTo(w.head) < EAT_R) {
            sim.food.consume(food)
            w.ate += food.grams
            foodEaten++
            w.flare = 0.3
        }
        if (w.state == EMERGE && trailLen(w) >= 0.8 * w.lengthCm) w.state = ACTIVE
    }

    /** Reverses down its own trail into the hole at 3 cm/s, then in. */
    private fun retreat(w: Worm, h: Double) {
        var d = RETREAT_CM_S * h
        while (d > 0 && w.trail.isNotEmpty()) {
            val p = w.trail[0]
            val step = w.head.distanceTo(p)
            if (step <= d) { d -= step; w.head = p; w.trail.removeAt(0) } else { w.head = w.head.lerp(p, d / step); d = 0.0 }
        }
        if (w.trail.isEmpty()) {
            w.head = w.head - w.holeN * d
            if (w.head.distanceTo(w.hole) > w.lengthCm + 0.5 || (w.head - w.hole).dot(w.holeN) < -w.lengthCm) w.state = GONE
        }
    }

    /** Held by a predator: pulled out of the rock toward its mouth, writhing, then gone (eaten). */
    private fun held(w: Worm, h: Double) {
        w.held += h
        w.limb += 8 * h
        w.flare = 1.0
        val by = w.heldBy
        if (by == null || !by.alive || w.held > HELD_S) { w.state = GONE; return }
        val mouth = by.pos + by.heading * (0.4 * by.lengthCm)
        val k = min(1.0, h * 2.5)
        val old = w.head
        w.head = w.head.lerp(mouth, k)
        if (w.head.distanceTo(old) > 1e-4) {
            w.trail.add(0, old)
            while (w.trail.size > 40) w.trail.removeAt(w.trail.size - 1)
        }
    }

    private fun crawl(w: Worm, d: Double) {
        if (d <= 0) return
        val old = w.head
        var p = w.head + w.heading * d
        val lift = lift(w)
        var ok = true
        if (w.onSand) {
            p = Vec3(p.x, sandY(p.x, p.z) + lift, p.z)
            if (!inTank(p) || sim.rock.sdf.dist(p) < 0.2) ok = false
        } else {
            val sdf = sim.rock.sdf
            val q = sdf.project(p - w.normal * lift)
            val n = sdf.gradient(q).normalized(w.normal)
            p = q + n * lift
            if (!inTank(p) || q.y < sandY(q.x, q.z) + 0.2 || q.distanceTo(old) > 3 * d + 0.5 || abs(sdf.dist(q)) > 0.4) ok = false
            else w.normal = n
        }
        if (!ok) { w.heading = Vec3.rotateAbout(-w.heading, w.normal, rng.signed() * 0.8); return }
        w.heading = (p - old).rejectFrom(w.normal).normalized(w.heading)
        w.head = p
        val last = if (w.trail.isEmpty()) old.also { w.trail.add(0, it) } else w.trail[0]
        if (w.head.distanceTo(last) >= w.spacing) w.trail.add(0, w.head)
    }

    private fun fishNear(p: Vec3, r: Double): Boolean {
        val r2 = r * r
        for (o in sim.organisms) {
            if (!o.alive || !o.isFish || PREDATORS.contains(o.brainKind)) continue
            if (o.pos.distanceSqTo(p) < r2) return true
        }
        return false
    }

    private fun trailLen(w: Worm): Double {
        var s = 0.0
        var prev = w.head
        for (p in w.trail) { s += prev.distanceTo(p); prev = p }
        return s
    }

    private fun steerTo(w: Worm, goal: Vec3): Double {
        val to = (goal - w.head).rejectFrom(w.normal)
        if (to.lengthSq() < 1e-6) return 0.0
        val side = (w.normal cross w.heading) dot to.normalized()
        return (side * 2.0).coerceIn(-1.2, 1.2)
    }

    private fun randomTangent(n: Vec3): Vec3 {
        val a = (if (abs(n.y) < 0.9) Vec3.Y else Vec3.X).rejectFrom(n).normalized()
        return Vec3.rotateAbout(a, n, rng.range(0.0, 2 * PI))
    }

    // ------------------------------------------------------------------ poses

    /** Drawable poses for the snapshot (same contract as the render-side critters: 16 stations head → tail). */
    fun poses(): List<CritterPose> {
        if (worms.isEmpty()) return emptyList()
        val out = ArrayList<CritterPose>(worms.size)
        for (w in worms) out += pose(w)
        return out
    }

    private fun pose(w: Worm): CritterPose {
        val out = CritterPose()
        out.id = w.key
        out.kind = CritterKind.BRISTLEWORM
        out.scale = w.lengthCm.toFloat()
        val seg = w.lengthCm / 15.0
        val st = arrayOfNulls<Vec3>(16)
        st[0] = w.head
        var prev = w.head
        var j = 0
        for (i in 1 until 16) {
            var need = seg
            var p = prev
            while (need > 0) {
                if (j >= w.trail.size) { p = p - (if (w.state == CAUGHT) w.heading else w.holeN) * need; need = 0.0; break }
                val s = w.trail[j]
                val d = p.distanceTo(s)
                if (d >= need) { p = p.lerp(s, need / d); need = 0.0 } else { need -= d; p = s; j++ }
            }
            st[i] = p; prev = p
        }
        for (i in 0 until 16) {
            val p = st[i]!!
            out.spine[i * 3] = p.x.toFloat(); out.spine[i * 3 + 1] = p.y.toFloat(); out.spine[i * 3 + 2] = p.z.toFloat()
            val fwd = when (i) {
                0 -> st[0]!! - st[1]!!
                15 -> st[14]!! - st[15]!!
                else -> st[i - 1]!! - st[i + 1]!!
            }.normalized(w.heading)
            val q = Quat.fromForwardUp(fwd, w.normal)
            out.frames[i * 4] = q.x.toFloat(); out.frames[i * 4 + 1] = q.y.toFloat(); out.frames[i * 4 + 2] = q.z.toFloat(); out.frames[i * 4 + 3] = q.w.toFloat()
        }
        out.swimPhase = (w.phase % 1000.0).toFloat()
        // flared chaetae beat the parapodia fast (the renderer's appendage sweep); a writhing body when held
        out.appendage = ((w.limb + 4 * w.flare * w.phase) % 1000.0).toFloat()
        out.amp = (0.035 + 0.05 * w.flare).toFloat()
        out.waveLen = 1.6f
        return out
    }

    companion object {
        const val EMERGE = 0
        const val ACTIVE = 1
        const val RETREAT = 2
        const val CAUGHT = 3
        const val GONE = 4
        /** At most this many worms out at once, and the exposed count at which half of them show (as CritterField). */
        const val CAP = 8
        const val HALF = 10.0
        /** Share of the population that food on the bottom draws out by day (research 10 §2.2: within minutes of a feed). */
        const val LURE_SHARE = 0.02
        const val HOLES = 60
        const val SAND_SHARE = 0.25
        const val MIN_CM = 2.5
        const val MAX_CM = 12.0
        const val LIFT_BL = 0.025
        /** Glide 5-20 mm/s, retreat 30-50 mm/s (research 17 §12.3). */
        const val SPEED_CM_S = 0.8
        const val RETREAT_CM_S = 3.0
        const val OUTING_S = 240.0
        const val MAX_TRAIL = 190
        const val FOOD_R = 15.0
        const val EAT_R = 0.7
        /** A worm goes home after this much food (g; a pellet or two, est.). */
        const val FULL_G = 0.06
        const val HELD_S = 2.5
        /** Fish that hunt worms: a worm does not shy from them in time (they strike from a perch). */
        val PREDATORS = setOf(BrainKind.HAWK, BrainKind.DOTTYBACK, BrainKind.LEOPARD_WRASSE)
    }
}
