package com.reeflive.core.sim

import com.reeflive.core.geom.Quat
import com.reeflive.core.geom.Rng
import com.reeflive.core.geom.Vec3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The bristleworms you can see (research 42 §B, 17 §12): a handful of individuals drawn from the
 * aggregate [MicrofaunaModel] population, simulated in the sim (not just drawn) so predators can find and catch them
 * and they can eat the food that reaches the bottom.
 *
 * How many are out follows the population and its diel exposure (they crawl about at night, a few by day) plus food on
 * the bottom, which brings them out at any hour within minutes of a feed. Each worm lives in a crevice of the rock (or
 * under a rock's edge on the sand): it appears lying along the back of the crack, out of sight, glides out head first
 * along the same surface at 5-20 mm/s, meanders, makes for any settled food within reach and eats it, and backs down its
 * own trail into the crack at 30-50 mm/s when the lights come on, a fish noses at it, its outing ends or it is full.
 * Touched, its white chaetae flare. A predator that reaches its head ([catch]) pulls it off and the population loses one.
 *
 * Its body is never inside anything (Tee 2026-10-21: no clipping): the whole length lies on the surface it crawled
 * over, every station a belly height off the rock or sand with that spot's own normal as its up, so it bends over the
 * rock without cutting into it or rolling.
 *
 * Only the visible few are individuals: the hundreds of an overfed tank stay one number, so a worm plague costs no more
 * CPU than a lean tank. Off in the abstract (fast-forward) mode. At most [CAP] worms, a few Vec3 each per tick.
 * Deterministic: its own random stream.
 */
class WormField(private val sim: Simulation, seed: Long) {
    private val rng = Rng(seed xor 0xB215L)

    class Worm(val id: Int, val lengthCm: Double) {
        val key = "worm:$id"
        /** Lives under a rock's edge on the sand rather than in the rock. */
        var onSand = false
        var head = Vec3.ZERO
        var heading = Vec3.X
        var normal = Vec3.Y
        /** Head positions behind the current one, newest first, about [spacing] apart; it backs down them home. */
        val trail = ArrayList<Vec3>()
        /** The surface normal at each [trail] point. */
        val trailN = ArrayList<Vec3>()
        /** The oldest [laid] trail points are its den, deep in the crack: home when it has backed down to them. */
        var laid = 0
        /** On the way out: the path from the den to the crack's mouth, next point last. */
        val guide = ArrayList<Vec3>()
        val guideN = ArrayList<Vec3>()
        val spacing: Double get() = lengthCm / 12
        /** Wander turn rate (rad/s), a slow random walk: it meanders instead of zigzagging. */
        var turn = 0.0
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
        /** Out of its crack and in reach (not on its way in, not caught). */
        val exposed: Boolean get() = state == EMERGE || state == ACTIVE
    }

    val worms = ArrayList<Worm>()
    private var nextId = 0
    private var cooldown = 0.0
    /** Crack spots (surface point, normal, on the sand) the worms live in; rebuilt when the rocks move. */
    private var dens: List<Triple<Vec3, Vec3, Boolean>> = emptyList()
    private var densRev = -1
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
     * [by] grabs worm [w]: it flares and is pulled off the rock over a second or two, and the population loses one.
     * False if it is no longer exposed (already caught, or back in its crack).
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
        ensureDens()
        val m = sim.water.micro.snapshot()
        target = wanted(m)
        cooldown -= h
        val out = worms.count { it.exposed }
        if (out < target && cooldown <= 0 && dens.isNotEmpty()) {
            spawn()
            cooldown = rng.range(3.0, 10.0)
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

    /** Spine height over the surface: the body's half-height plus a hair, so its belly rests on the rock, not in it. */
    private fun lift(w: Worm) = LIFT_BL * w.lengthCm + 0.05

    /** Cracks in the rock, and sand under a rock's edge (high cover just off the surface); rebuilt when the rocks move. */
    private fun ensureDens() {
        val rev = (sim.rock.sdf as? DynamicRockSdf)?.revision ?: 0
        if (dens.isNotEmpty() && rev == densRev) return
        densRev = rev
        val sdf = sim.rock.sdf
        val L = sim.rock.lengthCm; val W = sim.rock.widthCm
        val out = ArrayList<Triple<Vec3, Vec3, Boolean>>()
        val any = ArrayList<Triple<Vec3, Vec3, Boolean>>()
        var tries = 0
        while (out.size < DENS && tries < 1500) {
            tries++
            if (rng.chance(SAND_SHARE)) {
                // the sand right at a rock's foot, under its edge
                val x = rng.range(3.0, L - 3); val z = rng.range(2.0, W - 2)
                val q = Vec3(x, sandY(x, z), z)
                val d = sdf.dist(q + Vec3.Y * 0.5)
                if (d < 0.6 || d > 3.0) continue
                if (any.size < DENS) any += Triple(q, Vec3.Y, true)
                if (sdf.cover(q + Vec3.Y * 0.8) > DEN_COVER) out += Triple(q, Vec3.Y, true)
                continue
            }
            val p = Vec3(rng.range(3.0, L - 3), rng.range(sim.rock.sandTopCm + 1, 0.8 * sim.rock.waterLevelCm), rng.range(2.0, W - 2))
            if (abs(sdf.dist(p)) > 10) continue
            val q = sdf.project(p)
            if (abs(sdf.dist(q)) > 0.3 || !inTank(q) || q.y < sandY(q.x, q.z) + 0.3) continue
            val n = sdf.gradient(q).normalized(Vec3.Y)
            if (any.size < DENS) any += Triple(q, n, false)
            if (sdf.cover(q + n * 0.8) > DEN_COVER) out += Triple(q, n, false)
        }
        dens = out.ifEmpty { any }
    }

    /**
     * One step of [d] cm from [p] (a belly height over the surface, normal [n]) along [dir], staying on the worm's
     * surface: the new spine point and its surface normal, or null where the way is blocked (a wall, the glass, the
     * sand line, the rock's edge for a sand worm).
     */
    private fun surfaceStep(p: Vec3, n: Vec3, dir: Vec3, d: Double, lift: Double, onSand: Boolean): Pair<Vec3, Vec3>? {
        val sdf = sim.rock.sdf
        if (onSand) {
            val t = p + dir * d
            val q = Vec3(t.x, sandY(t.x, t.z) + lift, t.z)
            return if (!inTank(q) || sdf.dist(q) < lift + 0.1) null else q to Vec3.Y
        }
        val base = p - n * lift
        val q = sdf.project(base + dir * d)
        if (abs(sdf.dist(q)) > 0.15 || q.distanceTo(base) > 3 * d + 0.5) return null
        // (a wide stencil: the porous rock's fine relief would tip the body this way and that every step)
        val n2 = sdf.gradient(q, NORMAL_H).normalized(n)
        val s = q + n2 * lift
        if (!inTank(s) || q.y < sandY(q.x, q.z) + 0.2) return null
        return s to n2
    }

    /** How shut in a spine point is: the less open water just over it, the deeper in the crack (cheap: one sample). */
    private fun shutIn(p: Vec3, n: Vec3) = -sim.rock.sdf.dist(p + n * 1.2)

    private fun spawn() {
        // adults 4-9 cm; a young population (just hitchhiked in, still growing) shows smaller worms (est.)
        val total = sim.water.micro.total(MicroGroup.BRISTLEWORM)
        val maxL = MIN_CM + (MAX_CM - MIN_CM) * (total / (total + 15.0)).coerceIn(0.3, 1.0)
        val w = Worm(nextId++, rng.range(MIN_CM, maxL))
        val lift = lift(w)
        repeat(4) {
            val den = dens[rng.nextInt(dens.size)]
            w.onSand = den.third
            // lay its body out of sight: walk from the crack spot ever deeper in (where the least open water is over
            // it), two body lengths; the worm starts on the far half and crawls back out along the near half
            val pts = ArrayList<Vec3>(); val ns = ArrayList<Vec3>()
            var p = den.first + den.second * lift
            var n = den.second
            var dir = randomTangent(n)
            pts += p; ns += n
            var len = 0.0
            var guard = 0
            while (len < 2 * w.lengthCm && guard++ < 80) {
                var best: Pair<Vec3, Vec3>? = null
                var bs = -1e9
                for (k in -2..2) {
                    val dk = Vec3.rotateAbout(dir, n, k * 0.35)
                    val s = surfaceStep(p, n, dk, w.spacing, lift, w.onSand) ?: continue
                    // no doubling back over itself
                    if (pts.size > 2 && s.first.distanceTo(pts[pts.size - 3]) < 1.5 * w.spacing) continue
                    val sc = shutIn(s.first, s.second) + 0.05 * rng.signed()
                    if (sc > bs) { bs = sc; best = s }
                }
                val b = best ?: break
                dir = (b.first - p).rejectFrom(b.second).normalized(dir)
                len += b.first.distanceTo(p)
                p = b.first; n = b.second
                pts += p; ns += n
            }
            if (len < 1.6 * w.lengthCm) return@repeat
            // head at the point one body length in; trail = the deeper rest (newest first); guide = the way out
            var acc = 0.0
            var m = 0
            while (m + 1 < pts.size && acc < len - w.lengthCm - w.spacing) { acc += pts[m].distanceTo(pts[m + 1]); m++ }
            w.head = pts[m]; w.normal = ns[m]
            for (i in m + 1 until pts.size) { w.trail += pts[i]; w.trailN += ns[i] }
            w.laid = w.trail.size
            for (i in 0 until m) { w.guide += pts[i]; w.guideN += ns[i] }   // next point last
            w.heading = (if (m > 0) pts[m - 1] - pts[m] else pts[m] - pts[m + 1]).rejectFrom(w.normal).normalized(randomTangent(w.normal))
            w.state = EMERGE
            w.timer = rng.range(OUTING_S * 0.5, OUTING_S * 1.5)
            w.phase = rng.nextDouble(); w.limb = rng.nextDouble()
            worms += w
            return
        }
    }

    // ------------------------------------------------------------------ behaviour

    private fun stepWorm(w: Worm, h: Double) {
        w.phase += 0.5 * h
        w.limb += 1.0 * h
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
        if (w.state == EMERGE) { emerge(w, h); return }
        val food = sim.food.nearest(w.head, FOOD_R) { it.settled }
        // a slow meander (a random walk in turn rate), or a steady turn toward food
        w.turn = (w.turn * exp(-h * 0.6) + rng.signed() * h * 0.8).coerceIn(-MAX_TURN, MAX_TURN)
        val turn = if (food != null) steerTo(w, food.pos) else w.turn
        w.heading = Vec3.rotateAbout(w.heading, w.normal, turn * h)
        crawl(w, SPEED_CM_S * (if (food != null) 1.5 else 1.0) * h)
        if (food != null && food.pos.distanceTo(w.head) < EAT_R) {
            sim.food.consume(food)
            w.ate += food.grams
            foodEaten++
            w.flare = 0.3
        }
    }

    /** Out of the crack along the path its body was laid on, then free. */
    private fun emerge(w: Worm, h: Double) {
        var d = SPEED_CM_S * h
        while (d > 0 && w.guide.isNotEmpty()) {
            val g = w.guide[w.guide.size - 1]
            val step = w.head.distanceTo(g)
            val p = if (step <= d) g else w.head.lerp(g, d / step)
            if (step <= d) { d -= step; w.normal = w.guideN[w.guideN.size - 1]; w.guide.removeAt(w.guide.size - 1); w.guideN.removeAt(w.guideN.size - 1) }
            else { w.normal = w.normal.lerp(w.guideN[w.guideN.size - 1], d / step).normalized(w.normal); d = 0.0 }
            val dir = p - w.head
            if (dir.lengthSq() > 1e-10) w.heading = dir.rejectFrom(w.normal).normalized(w.heading)
            w.head = p
            if (w.head.distanceTo(w.trail[0]) >= w.spacing) { w.trail.add(0, w.head); w.trailN.add(0, w.normal) }
        }
        if (w.guide.isEmpty()) w.state = ACTIVE
    }

    /** Reverses down its own trail at 3 cm/s; home (gone from view) once it is back over its den. */
    private fun retreat(w: Worm, h: Double) {
        w.guide.clear(); w.guideN.clear()
        var d = RETREAT_CM_S * h
        while (d > 0 && w.trail.size > w.laid) {
            val p = w.trail[0]
            val step = w.head.distanceTo(p)
            if (step <= d) { d -= step; w.head = p; w.normal = w.trailN[0]; w.trail.removeAt(0); w.trailN.removeAt(0) }
            else { val t = d / step; w.head = w.head.lerp(p, t); w.normal = w.normal.lerp(w.trailN[0], t).normalized(w.normal); d = 0.0 }
        }
        if (w.trail.size <= w.laid) w.state = GONE
    }

    /** Held by a predator: pulled off the rock toward its mouth, writhing, then gone (eaten). */
    private fun held(w: Worm, h: Double) {
        w.held += h
        w.limb += 8 * h
        w.flare = 1.0
        val by = w.heldBy
        if (by == null || !by.alive || w.held > HELD_S) { w.state = GONE; return }
        val mouth = by.pos + by.heading * (0.4 * by.lengthCm)
        val old = w.head
        w.head = w.head.lerp(mouth, min(1.0, h * 2.5))
        if (w.head.distanceTo(old) > 1e-4) {
            w.trail.add(0, old); w.trailN.add(0, w.normal)
            while (w.trail.size > 40) { w.trail.removeAt(w.trail.size - 1); w.trailN.removeAt(w.trailN.size - 1) }
        }
    }

    private fun crawl(w: Worm, d: Double) {
        if (d <= 0) return
        val s = surfaceStep(w.head, w.normal, w.heading, d, lift(w), w.onSand)
        // blocked (a wall, the glass, the sand line): it stops and turns away over the next second, not in one snap
        if (s == null) { w.turn = if (w.turn >= 0) MAX_TURN else -MAX_TURN; w.heading = Vec3.rotateAbout(w.heading, w.normal, w.turn * 0.05); return }
        val old = w.head
        // the body's up eases onto the new surface (a raw finite-difference normal flickers: the worm rolled)
        w.normal = w.normal.lerp(s.second, NORMAL_EASE).normalized(s.second)
        w.heading = (s.first - old).rejectFrom(w.normal).normalized(w.heading)
        w.head = s.first
        if (w.head.distanceTo(w.trail[0]) >= w.spacing) { w.trail.add(0, w.head); w.trailN.add(0, w.normal.lerp(w.trailN[0], 0.5).normalized(w.normal)) }
    }

    private fun fishNear(p: Vec3, r: Double): Boolean {
        val r2 = r * r
        for (o in sim.organisms) {
            if (!o.alive || !o.isFish || PREDATORS.contains(o.brainKind)) continue
            if (o.pos.distanceSqTo(p) < r2) return true
        }
        return false
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

    private val st = arrayOfNulls<Vec3>(16)
    private val stN = arrayOfNulls<Vec3>(16)
    private val raw = arrayOfNulls<Vec3>(16)
    private val smX = DoubleArray(16)
    private val smY = DoubleArray(16)
    private val smZ = DoubleArray(16)

    /**
     * The body laid along the head's trail, 16 stations head → tail, each with the surface normal it lies on and kept a
     * belly height clear of rock and sand (a chord between trail points cuts a curved rock). Forward is taken over two
     * stations either side, so the frames turn smoothly as the stations slide along the trail.
     */
    private fun pose(w: Worm): CritterPose {
        val out = CritterPose()
        out.id = w.key
        out.kind = CritterKind.BRISTLEWORM
        out.scale = w.lengthCm.toFloat()
        val seg = w.lengthCm / 15.0
        val lift = lift(w)
        val caught = w.state == CAUGHT
        st[0] = if (caught) w.head else clearOf(w.head, w.normal, lift, w.onSand); stN[0] = w.normal; raw[0] = w.head
        // walk the trail polyline: each station's point and normal interpolated along the segment it falls on
        var a = w.head; var na = w.normal
        var j = 0
        var u = 0.0
        for (i in 1 until 16) {
            var need = seg
            var p: Vec3
            var n: Vec3
            while (true) {
                if (j >= w.trail.size) {
                    // (a caught worm dangles behind its head; otherwise the trail always covers the body)
                    val back = if (caught) -w.heading else (st[i - 1]!! - st[max(0, i - 3)]!!).normalized(-w.heading)
                    p = st[i - 1]!! + back * seg; n = stN[i - 1]!!
                    break
                }
                val bp = w.trail[j]
                val len = a.distanceTo(bp)
                if (len - u >= need) {
                    u += need
                    val t = if (len > 1e-9) u / len else 1.0
                    p = a.lerp(bp, t); n = na.lerp(w.trailN[j], t).normalized(na)
                    break
                }
                need -= len - u
                a = bp; na = w.trailN[j]; u = 0.0; j++
            }
            raw[i] = p
            if (!caught) p = clearOf(p, n, lift, w.onSand)
            st[i] = p; stN[i] = n
        }
        // the up of each station: the surface normals along the body, smoothed over its neighbours
        // ((a + b * 2 + c).normalized(b) per station, in scalars: twelve passes of Vec3s per worm per snapshot before)
        if (!caught) {
            val nx = smX; val ny = smY; val nz = smZ
            for (i in 0 until 16) { val v = stN[i]!!; nx[i] = v.x; ny[i] = v.y; nz[i] = v.z }
            repeat(SMOOTH_PASSES) {
                var ax = nx[0]; var ay = ny[0]; var az = nz[0]
                for (i in 0 until 16) {
                    val bx = nx[i]; val by = ny[i]; val bz = nz[i]
                    val k = min(15, i + 1)
                    val x = ax + bx * 2.0 + nx[k]; val y = ay + by * 2.0 + ny[k]; val z = az + bz * 2.0 + nz[k]
                    val l = sqrt(x * x + y * y + z * z)
                    if (l >= 1e-9) { nx[i] = x / l; ny[i] = y / l; nz[i] = z / l }
                    ax = bx; ay = by; az = bz
                }
            }
            for (i in 0 until 16) stN[i] = Vec3(nx[i], ny[i], nz[i])
        }
        for (i in 0 until 16) {
            val p = st[i]!!
            out.spine[i * 3] = p.x.toFloat(); out.spine[i * 3 + 1] = p.y.toFloat(); out.spine[i * 3 + 2] = p.z.toFloat()
            // (forward along the laid path, not the pushed-clear points: those step about as the rock's pits pass)
            val a = raw[max(0, i - 2)]!!; val b = raw[min(15, i + 2)]!!
            val f = a - b
            val fwd = if (f.lengthSq() < 1e-8) w.heading else f.normalized(w.heading)
            val q = Quat.fromForwardUp(fwd, stN[i]!!)
            out.frames[i * 4] = q.x.toFloat(); out.frames[i * 4 + 1] = q.y.toFloat(); out.frames[i * 4 + 2] = q.z.toFloat(); out.frames[i * 4 + 3] = q.w.toFloat()
        }
        out.swimPhase = (w.phase % 1000.0).toFloat()
        // a faint body wave as it glides; the chaetae flare and the parapodia beat when touched or held
        out.appendage = ((w.limb + 3 * w.flare * w.phase) % 1000.0).toFloat()
        out.amp = (AMP + FLARE_AMP * w.flare).toFloat()
        out.waveLen = 1.6f
        return out
    }

    /** [p] (on the surface with normal [n]) moved off the rock and sand until it is [r] clear of both. */
    private fun clearOf(p: Vec3, n: Vec3, r: Double, onSand: Boolean): Vec3 {
        val sdf = sim.rock.sdf
        var q = p
        repeat(4) {
            val d = sdf.dist(q)
            if (d < r) q += (if (onSand) sdf.gradient(q, 0.5) else sdf.gradient(q, 0.5).lerp(n, 0.3).normalized(n)) * (r - d + 0.01)
        }
        val sy = sandY(q.x, q.z) + r
        if (q.y < sy) q = Vec3(q.x, sy, q.z)
        return q
    }

    companion object {
        const val EMERGE = 0
        const val ACTIVE = 1
        const val RETREAT = 2
        const val CAUGHT = 3
        const val GONE = 4
        /** At most this many worms out at once, and the exposed count at which half of them show. */
        const val CAP = 5
        const val HALF = 12.0
        /** Share of the population that food on the bottom draws out by day (research 10 §2.2: within minutes of a feed). */
        const val LURE_SHARE = 0.02
        const val DENS = 60
        /** Share of dens on the sand under a rock's edge; how enclosed a den must be. */
        const val SAND_SHARE = 0.25
        const val DEN_COVER = 0.5
        const val MIN_CM = 2.5
        const val MAX_CM = 9.0
        /** Spine height and body half-width (× length) of micro_bristleworm.glb, chaetae included. */
        const val LIFT_BL = 0.03
        const val BODY_R_BL = 0.065
        /** Body wave and its extra when flared (× length): small, the worm glides rather than swims. */
        const val AMP = 0.012
        const val FLARE_AMP = 0.03
        /** Fastest meander turn (rad/s) and how much of a new surface normal it takes per step. */
        const val MAX_TURN = 0.6
        const val NORMAL_EASE = 0.25
        /** Finite-difference step (cm) for the surface normal the body lies on. */
        const val NORMAL_H = 1.5
        /** Glide 5-20 mm/s, retreat 30-50 mm/s (research 17 §12.3). */
        const val SPEED_CM_S = 0.6
        const val RETREAT_CM_S = 2.0
        /** Passes of the (1 2 1) filter over the body's up: about three stations wide, so pitted rock does not roll it. */
        const val SMOOTH_PASSES = 12
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
