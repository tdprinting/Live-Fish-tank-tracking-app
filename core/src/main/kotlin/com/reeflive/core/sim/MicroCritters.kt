package com.reeflive.core.sim

import com.reeflive.core.geom.Quat
import com.reeflive.core.geom.Rng
import com.reeflive.core.geom.Sdf
import com.reeflive.core.geom.Vec3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The microfauna big enough to see in the display (research 16 §17, research 10 §2.2 / §5.3), drawn as real animals
 * in the 3D tank instead of only under the microscope. [group] ties each kind to the aggregate [MicrofaunaModel]
 * population; [scenes] are the habitats it is seen on; [liftBL] is how far the spine sits above the surface (body
 * lengths); [speedCmS] its cruising speed. Lengths are the adult range an aquarist sees.
 */
enum class CritterKind(
    val group: MicroGroup, val speciesId: String, val minCm: Double, val maxCm: Double, val cap: Int,
    val speedCmS: Double, val liftBL: Double, vararg val scenes: MicroScene,
) {
    /** Eurythoe-type fireworm: glides head-first 5-20 mm/s, retreats into its hole at 30-50 mm/s (research 16 §10). */
    BRISTLEWORM(MicroGroup.BRISTLEWORM, "micro_bristleworm", 2.5, 12.0, 6, 0.8, 0.025, MicroScene.ROCK, MicroScene.SAND),
    /** Gammarid: stop-go scuttle 5-20 mm/s, side-swim bouts 30-80 mm/s (research 10 §5.3). */
    AMPHIPOD(MicroGroup.GAMMARID, "micro_amphipod", 0.6, 1.3, 12, 1.4, 0.2, MicroScene.ROCK, MicroScene.SAND, MicroScene.GLASS),
    /** Mysids hover in a loose swarm off the rock at night, 1-2 BL spacing (research 10 §5.3). */
    MYSID(MicroGroup.MYSID, "micro_mysid", 0.6, 0.9, 10, 0.4, 0.0, MicroScene.WATER),
    ISOPOD(MicroGroup.ISOPOD, "micro_isopod", 0.3, 0.6, 4, 0.5, 0.09, MicroScene.ROCK, MicroScene.GLASS),
    /** Harpacticoids on the glass: 1-3 mm/s crawl with short darts (research 10 §5.3); only shown near the camera. */
    COPEPOD(MicroGroup.HARPACTICOID, "micro_copepod", 0.08, 0.12, 16, 0.2, 0.12, MicroScene.GLASS),
    SNAIL(MicroGroup.MICROSNAIL, "micro_snail", 0.2, 0.4, 6, 0.1, 0.01, MicroScene.GLASS, MicroScene.ROCK),
    /** Red planaria: ciliary glide 0.5-1 mm/s (research 16 §17). Only present once the pest arrives. */
    FLATWORM(MicroGroup.FLATWORM, "micro_flatworm", 0.2, 0.5, 12, 0.08, 0.01, MicroScene.ROCK, MicroScene.GLASS, MicroScene.SAND),
    /** Skeleton shrimp clinging by its rear legs to turf and macroalgae on the rock tops, swaying (research 17 §5). */
    CAPRELLID(MicroGroup.CAPRELLID, "micro_caprellid", 0.6, 1.5, 6, 0.0, 0.0, MicroScene.ROCK),
    /** Podocopid ostracods scurrying on the glass near the camera, 1-3 mm/s (research 10 §5.3). */
    OSTRACOD(MicroGroup.OSTRACOD, "micro_ostracod", 0.06, 0.12, 10, 0.2, 0.3, MicroScene.GLASS),
    /**
     * Free-swimming calanoids (Parvocalanus) in the water near the camera: hop 1-3 mm then sink 0.5-1 mm/s, drawn
     * toward the light (research 10 §5.3, 17 §2).
     */
    PLANKTON(MicroGroup.CALANOID, "micro_calanoid", 0.03, 0.06, 20, 0.0, 0.0, MicroScene.WATER),
    /** Homotrema rubrum: sessile red dots on shaded rock undersides (research 17 §14.2). */
    FORAM(MicroGroup.FORAM, "micro_foram", 0.2, 0.5, 10, 0.0, 0.0, MicroScene.ROCK),
    /** One terebellid feeding tentacle; [TENTACLES_PER_WORM] of them per buried worm, laid over the sand. */
    TENTACLE(MicroGroup.SPAGHETTI_WORM, "micro_tentacle", 4.0, 10.0, 3, 0.12, 0.004, MicroScene.SAND),
    /**
     * Berghia nudibranch (aiptasia_control.md §6): not microfauna, driven by the pest population instead
     * ([BerghiaField]); [CritterField] never spawns it. Glide speed has no data: ~1.2 mm/s like other small aeolids (est.).
     */
    BERGHIA(MicroGroup.FLATWORM, "berghia", 1.4, 2.2, 8, 0.12, 0.02, MicroScene.ROCK);

    val model: String get() = "models/$speciesId.glb"

    companion object {
        const val TENTACLES_PER_WORM = 5
        private val bySpecies = entries.associateBy { it.speciesId }
        fun of(speciesId: String): CritterKind? = bySpecies[speciesId]
    }
}

/** One drawable critter for this frame: what the renderer's spline skinning needs (same contract as [OrganismPose]). */
class CritterPose {
    var id = ""
    var kind = CritterKind.AMPHIPOD
    /** 16 stations head → tail, xyz world cm. */
    val spine = FloatArray(48)
    /** 16 quaternions (xyzw) mapping model X forward / Y up / Z side to world. */
    val frames = FloatArray(64)
    var scale = 1f
    var swimPhase = 0f; var amp = 0f; var waveLen = 1f
    /** Appendage (leg / parapodium / thoracopod) cycle phase, in cycles. */
    var appendage = 0f
    fun headX() = spine[0]; fun headY() = spine[1]; fun headZ() = spine[2]
}

/**
 * Places, moves and retires the visible microfauna each frame from the aggregate population ([MicrofaunaState]):
 * the expected number in view on each habitat is the model's diel-exposed density × that habitat's area (research 10
 * §5.2), capped per kind; animals come out of crevices as it rises (lights-out, food on the sand) and crawl back in
 * as it falls. Movement follows the research 10 §5.3 primitives: worms glide head-first along their own trail and
 * reverse down it into their hole, amphipods scuttle stop-go and side-swim between perches, mysids hover in a
 * jittering swarm, pods and snails random-walk on the glass, spaghetti-worm tentacles creep out over the sand and
 * snap back when a fish passes. Purely visual: nothing here feeds back into the sim. Not thread-safe (render thread).
 */
class CritterField(
    private val sdf: Sdf?,
    private val config: TankConfig = TankConfig(),
    seed: Long = 7L,
) {
    private val rng = Rng(seed)
    private val L = config.lengthCm
    private val W = config.widthCm
    private val critters = ArrayList<Critter>()
    private val poses = ArrayList<CritterPose>()
    private var nextId = 0
    private val spawnCooldown = DoubleArray(CritterKind.entries.size)
    private var rockPts: List<Pair<Vec3, Vec3>> = emptyList()
    private var holes: List<Pair<Vec3, Vec3>> = emptyList()
    private var swarm: Vec3? = null
    private var sandGrid: SandGridSnapshot? = null
    private var foodSettled: List<Vec3> = emptyList()
    private var fishHeads: FloatArray = FloatArray(0)
    /** Kind → how many are wanted now (exposed for tests and the debug HUD). */
    val target = IntArray(CritterKind.entries.size)

    val active: List<CritterPose> get() = poses
    /** Animals of [k] out and active (not on their way back in). */
    fun count(k: CritterKind): Int = critters.count { it.kind == k && it.state != GONE && it.state != RETREAT }

    /**
     * Advances the critters by [dt] seconds of wall time. [focusX] is the camera's look-at x (cm) where the tiny glass
     * pods are shown; [qualityScale] (0..1) scales the caps. [food] is the snapshot's food xyz list and [fishHeads]
     * the xyz of every organism's head (tentacles retract from them).
     */
    fun update(
        state: MicrofaunaState, dt: Double, focusX: Double = L / 2, qualityScale: Double = 1.0,
        food: FloatArray = FloatArray(0), fishHeads: FloatArray = FloatArray(0), sand: SandGridSnapshot? = null,
    ): List<CritterPose> {
        ensurePools()
        sandGrid = sand
        this.fishHeads = fishHeads
        foodSettled = buildList {
            var i = 0
            while (i + 2 < food.size) {
                val p = Vec3(food[i].toDouble(), food[i + 1].toDouble(), food[i + 2].toDouble())
                if (p.y < sandY(p.x, p.z) + 1.5) add(p)
                i += 3
            }
        }
        val h = dt.coerceIn(0.0, 0.1)
        for (k in CritterKind.entries) {
            if (k == CritterKind.BERGHIA) continue
            target[k.ordinal] = wanted(k, state, qualityScale)
            spawnCooldown[k.ordinal] -= h
            var n = critters.count { it.kind == k && it.state != RETREAT && it.state != GONE }
            if (k == CritterKind.TENTACLE) n = (n + CritterKind.TENTACLES_PER_WORM - 1) / CritterKind.TENTACLES_PER_WORM   // worms, not tentacles
            if (n < target[k.ordinal] && spawnCooldown[k.ordinal] <= 0) {
                spawn(k, state, focusX)
                spawnCooldown[k.ordinal] = if (k == CritterKind.COPEPOD) 0.3 else rng.range(1.0, 4.0)
            } else if (n > target[k.ordinal]) {
                critters.firstOrNull { it.kind == k && it.state != RETREAT && it.state != GONE }?.let { retire(it) }
            }
        }
        for (c in critters) step(c, h, focusX)
        critters.removeAll { it.state == GONE }
        if (critters.none { it.kind == CritterKind.MYSID }) swarm = null
        while (poses.size < critters.size) poses += CritterPose()
        while (poses.size > critters.size) poses.removeAt(poses.size - 1)
        for (i in critters.indices) fillPose(critters[i], poses[i])
        return poses
    }

    // ------------------------------------------------------------------ population → count

    /** Animals of [k] expected in the whole display now (diel exposure applied), capped. */
    fun expected(k: CritterKind, s: MicrofaunaState): Double {
        var e = 0.0
        for (sc in k.scenes) e += s.visible(k.group, sc) * area(sc, s)
        // the worms stay in their tubes; their tentacles are out more at night and when food reaches the sand (research 10 §2.2)
        if (k == CritterKind.TENTACLE) e = s.total(k.group) * 0.5 * s.exposed[k.group.ordinal] + (if (foodSettled.isNotEmpty()) 1.0 else 0.0)
        if (k == CritterKind.BRISTLEWORM && foodSettled.isNotEmpty()) e += min(3.0, 0.02 * s.total(k.group))  // food brings them out by day
        return e
    }

    /**
     * How many to show: the cap scaled by a saturating share of the expected count, `cap · E / (E + K)`, so a lights-out
     * emergence still reads as "many more" once the tank-wide number exceeds what one view can hold. [K] is about the
     * exposed count at which half the cap shows (est., visual tuning).
     */
    private fun wanted(k: CritterKind, s: MicrofaunaState, q: Double): Int {
        if (!s.present[k.group.ordinal]) return 0
        val e = expected(k, s)
        val cap = k.cap * q
        if (k == CritterKind.TENTACLE) return min(cap.toInt(), floor(e + 0.35).toInt())
        val half = when (k) {
            CritterKind.BRISTLEWORM -> 10.0; CritterKind.AMPHIPOD -> 60.0; CritterKind.MYSID -> 10.0
            CritterKind.ISOPOD -> 30.0; CritterKind.COPEPOD -> 500.0; CritterKind.SNAIL -> 40.0
            CritterKind.FLATWORM -> 20.0; CritterKind.TENTACLE -> 1.0
            CritterKind.PLANKTON -> 2000.0; CritterKind.CAPRELLID -> 15.0; CritterKind.OSTRACOD -> 300.0; CritterKind.FORAM -> 40.0
            CritterKind.BERGHIA -> return 0
        }
        return floor(cap * e / (e + half) + 0.35).toInt()   // about two-thirds of an animal before one shows
    }

    private fun area(sc: MicroScene, s: MicrofaunaState): Double = when (sc) {
        MicroScene.ROCK -> s.rockCm2.takeIf { it > 0 } ?: (2.5 * L * W)
        MicroScene.SAND -> s.sandCm2.takeIf { it > 0 } ?: (L * W)
        MicroScene.GLASS -> s.glassCm2.takeIf { it > 0 } ?: (2 * (L + W) * 15)
        MicroScene.WATER -> config.systemLiters * 1000
    }

    // ------------------------------------------------------------------ geometry

    fun sandY(x: Double, z: Double): Double {
        var y = SandBed.BASE_FRONT + (SandBed.BASE_BACK - SandBed.BASE_FRONT) * ((W - z) / W).coerceIn(0.0, 1.0)
        val g = sandGrid
        if (g != null && g.nx > 1 && g.nz > 1) {
            val fx = (x / g.cellXCm - 0.5).coerceIn(0.0, g.nx - 1.001); val fz = (z / g.cellZCm - 0.5).coerceIn(0.0, g.nz - 1.001)
            val i = fx.toInt(); val k = fz.toInt(); val tx = fx - i; val tz = fz - k
            val a = g.height[k * g.nx + i]; val b = g.height[k * g.nx + i + 1]
            val c = g.height[(k + 1) * g.nx + i]; val d = g.height[(k + 1) * g.nx + i + 1]
            y += (a + (b - a) * tx) * (1 - tz) + (c + (d - c) * tx) * tz
        }
        return y
    }

    private fun inTank(p: Vec3) = p.x in 1.0..(L - 1) && p.z in 1.0..(W - 0.5) && p.y < config.waterLevelCm - 2

    /** Rock surface samples (with outward normals) and the crevice-like subset the worms live in, built once. */
    private fun ensurePools() {
        if (rockPts.isNotEmpty() || sdf == null) return
        val pts = ArrayList<Pair<Vec3, Vec3>>()
        var tries = 0
        while (pts.size < 260 && tries < 3000) {
            tries++
            val p = Vec3(rng.range(3.0, L - 3), rng.range(config.sandTopCm + 1, 0.75 * config.waterLevelCm), rng.range(2.0, W - 2))
            if (abs(sdf.dist(p)) > 12) continue
            val q = sdf.project(p)
            if (abs(sdf.dist(q)) > 0.3 || !inTank(q) || q.y < sandY(q.x, q.z) + 0.4) continue
            pts += q to sdf.gradient(q).normalized(Vec3.Y)
        }
        rockPts = pts
        // crevices: the space just off the surface is enclosed (high cover); fall back to any rock point
        holes = pts.take(90).filter { (q, n) -> sdf.cover(q + n * 0.8) > 0.45 }.ifEmpty { pts.take(20) }
    }

    // ------------------------------------------------------------------ critters

    private class Critter(val kind: CritterKind, val id: Int, val lengthCm: Double) {
        var scene = MicroScene.ROCK
        var head = Vec3.ZERO
        var heading = Vec3.X
        var normal = Vec3.Y
        /** Head positions behind the current one, newest first, spaced [spacing]; worms reverse along it. */
        val trail = ArrayList<Vec3>()
        val spacing get() = lengthCm / 12
        /** Where the body goes once the trail runs out: into the hole (worms, tentacles) or straight back. */
        var tailDir = Vec3.ZERO
        var state = EMERGE
        var timer = 0.0
        var moving = true
        var phase = 0.0
        var limb = 0.0
        var roll = 0.0
        // swarm offset (mysids) / tentacle path (anchor, direction, curvature, length)
        var offset = Vec3.ZERO
        var anchor = Vec3.ZERO
        var curl = 0.0
        var out = 0.0
        var outTarget = 1.0
        var hideFor = 0.0
    }

    private fun spawn(k: CritterKind, s: MicrofaunaState, focusX: Double) {
        if (k == CritterKind.TENTACLE) { spawnSpaghetti(); return }
        val len = rng.range(k.minCm, k.maxCm)
        val c = Critter(k, nextId++, len)
        val scene = pickScene(k, s)
        c.scene = scene
        val lift = k.liftBL * len
        when (scene) {
            MicroScene.GLASS -> {
                val x = if (k == CritterKind.COPEPOD || k == CritterKind.OSTRACOD) (focusX + rng.range(-12.0, 12.0)).coerceIn(2.0, L - 2) else rng.range(3.0, L - 3)
                c.head = Vec3(x, SandBed.BASE_FRONT + rng.range(0.4, 14.0), W - lift)
                c.normal = Vec3(0.0, 0.0, -1.0)
                c.heading = Vec3(cos(rng.range(0.0, 2 * PI)), sin(rng.range(0.0, 2 * PI)), 0.0).normalized()
                c.tailDir = -c.heading
            }
            MicroScene.SAND -> {
                val x = rng.range(4.0, L - 4); val z = rng.range(3.0, W - 2)
                c.head = Vec3(x, sandY(x, z) + lift, z)
                c.normal = Vec3.Y
                c.heading = Vec3(cos(rng.range(0.0, 2 * PI)), 0.0, sin(rng.range(0.0, 2 * PI))).normalized()
                c.tailDir = Vec3(0.0, -1.0, 0.0)   // burrow
            }
            MicroScene.WATER -> if (k == CritterKind.PLANKTON) {
                c.head = Vec3((focusX + rng.range(-10.0, 10.0)).coerceIn(2.0, L - 2), rng.range(10.0, config.waterLevelCm - 6), rng.range(3.0, W - 2))
                c.heading = Vec3(rng.signed(), rng.signed() * 0.3, rng.signed()).normalized()
                c.normal = Vec3.Y
                c.tailDir = -c.heading
            } else {
                val centre = swarm ?: (holes.randomOrNull(rng)?.let { (q, n) -> q + n * 3.0 } ?: Vec3(L / 2, 15.0, W / 2)).also { swarm = it }
                c.offset = Vec3(rng.signed() * 2.5, rng.signed() * 1.2, rng.signed() * 2.0)
                c.head = centre + c.offset
                c.heading = Vec3(-1.0, 0.0, rng.signed() * 0.3).normalized()
                c.normal = Vec3.Y
                c.tailDir = -c.heading
            }
            MicroScene.ROCK -> {
                val pool = when (k) {
                    CritterKind.BRISTLEWORM, CritterKind.ISOPOD -> holes
                    CritterKind.CAPRELLID -> rockPts.filter { it.second.y > 0.5 }.ifEmpty { rockPts }   // lit tops with turf
                    CritterKind.FORAM -> rockPts.filter { it.second.y < 0.2 }.ifEmpty { rockPts }       // shaded undersides
                    else -> rockPts
                }
                val (q, n) = pool.randomOrNull(rng)
                    ?: (Vec3(rng.range(10.0, L - 10), 12.0, W / 2) to Vec3.Y)
                c.normal = n
                c.head = q + n * lift
                c.heading = randomTangent(n)
                c.tailDir = -n   // into the crevice
                c.anchor = q
                c.offset = randomTangent(n)   // caprellid: its dorsal side / sway plane
            }
        }
        c.state = if (k == CritterKind.BRISTLEWORM) EMERGE else ACTIVE
        c.outTarget = rng.range(0.6, 1.0)
        c.timer = rng.range(40.0, 200.0)
        if (k == CritterKind.MYSID || k == CritterKind.PLANKTON) c.timer = 0.0
        c.phase = rng.range(0.0, 1.0); c.limb = rng.range(0.0, 1.0)
        critters += c
    }

    private fun spawnSpaghetti() {
        // a buried terebellid near the rock base: its tentacles fan out over the sand
        val base = rockPts.filter { (q, _) -> q.y < sandY(q.x, q.z) + 3 }.randomOrNull(rng)?.first
            ?: Vec3(rng.range(10.0, L - 10), 0.0, rng.range(8.0, W - 6))
        val ax = (base.x + rng.signed() * 2).coerceIn(4.0, L - 4); val az = (base.z + 2.5).coerceIn(3.0, W - 2)
        val anchor = Vec3(ax, sandY(ax, az), az)
        val a0 = rng.range(0.0, 2 * PI)
        repeat(CritterKind.TENTACLES_PER_WORM) {
            val c = Critter(CritterKind.TENTACLE, nextId++, rng.range(CritterKind.TENTACLE.minCm, CritterKind.TENTACLE.maxCm))
            c.scene = MicroScene.SAND
            c.anchor = anchor
            val a = a0 + rng.signed() * 1.1
            c.heading = Vec3(cos(a), 0.0, sin(a))
            c.curl = rng.signed() * 0.35
            c.normal = Vec3.Y
            c.tailDir = Vec3(0.0, -1.0, 0.0)
            c.state = EMERGE
            c.out = 0.0
            c.outTarget = rng.range(0.55, 1.0)
            c.phase = rng.range(0.0, 1.0)
            critters += c
        }
    }

    private fun pickScene(k: CritterKind, s: MicrofaunaState): MicroScene {
        if (k.scenes.size == 1) return k.scenes[0]
        var tot = 0.0
        val w = DoubleArray(k.scenes.size) { i -> (s.visible(k.group, k.scenes[i]) * area(k.scenes[i], s)).also { tot += it } }
        if (tot <= 0) return k.scenes[0]
        var r = rng.nextDouble() * tot
        for (i in w.indices) { r -= w[i]; if (r <= 0) return k.scenes[i] }
        return k.scenes.last()
    }

    private fun retire(c: Critter) {
        if (c.kind == CritterKind.TENTACLE) {
            // the whole worm pulls in together
            for (o in critters) if (o.kind == CritterKind.TENTACLE && o.anchor == c.anchor) { o.state = RETREAT }
        } else {
            c.state = RETREAT; c.timer = 0.0
        }
    }

    private fun step(c: Critter, h: Double, focusX: Double) {
        when (c.kind) {
            CritterKind.BRISTLEWORM -> worm(c, h)
            CritterKind.TENTACLE -> tentacle(c, h)
            CritterKind.MYSID -> mysid(c, h)
            CritterKind.PLANKTON -> plankton(c, h)
            CritterKind.AMPHIPOD -> amphipod(c, h)
            CritterKind.CAPRELLID -> caprellid(c, h)
            CritterKind.FORAM -> if (c.state == RETREAT) c.state = GONE else c.head = c.anchor + c.heading * (c.lengthCm / 2)
            else -> crawler(c, h)
        }
        if ((c.kind == CritterKind.COPEPOD || c.kind == CritterKind.OSTRACOD || c.kind == CritterKind.PLANKTON) && abs(c.head.x - focusX) > 30) c.state = GONE
    }

    /** Head-first glide on rock or sand; [EMERGE] and [ACTIVE] lay down the trail, [RETREAT] reverses down it. */
    private fun worm(c: Critter, h: Double) {
        c.phase += 0.8 * h; c.limb += 1.6 * h
        when (c.state) {
            EMERGE, ACTIVE -> {
                c.timer -= h
                // a fish nosing at it: chaetae flare (fast parapodial beat) and it backs into its hole (research 10 §5.3)
                if (fishNear(c.head, 2.5)) { c.limb += 6.0 * h; c.state = RETREAT; return }
                if (c.timer <= 0 || c.trail.size >= MAX_TRAIL) { c.state = RETREAT; return }
                val food = foodSettled.minByOrNull { it.distanceSqTo(c.head) }?.takeIf { it.distanceTo(c.head) < 12 }
                val turn = if (food != null) steerTo(c, food) else rng.signed() * 0.7
                c.heading = rotateAbout(c.heading, c.normal, turn * h)
                val speed = c.kind.speedCmS * if (food != null) 1.5 else 1.0
                crawlOnSurface(c, speed * h)
                if (c.state == EMERGE && trailLen(c) >= c.outTarget * c.lengthCm) c.state = ACTIVE
            }
            RETREAT -> {
                // reverse: pop the trail back toward the hole at 3 cm/s (research 16: 30-50 mm/s)
                var d = 3.0 * h
                while (d > 0 && c.trail.isNotEmpty()) {
                    val p = c.trail[0]
                    val step = c.head.distanceTo(p)
                    if (step <= d) { d -= step; c.head = p; c.trail.removeAt(0) } else { c.head = c.head.lerp(p, d / step); d = 0.0 }
                }
                if (c.trail.isEmpty()) {
                    c.head = c.head + c.tailDir * d
                    c.out -= 3.0 * h / c.lengthCm
                    if (c.out <= -1.0) c.state = GONE
                } else c.out = 0.0
            }
        }
    }

    /** Terebellid tentacle: creeps out by ciliary gliding, retracts in ~1-2 s when a fish comes close (research 16 §14.1). */
    private fun tentacle(c: Critter, h: Double) {
        c.phase += 0.15 * h
        val tip = tentaclePoint(c, c.out)
        var near = false
        var i = 0
        while (i + 2 < fishHeads.size) {
            val dx = fishHeads[i] - tip.x; val dy = fishHeads[i + 1] - tip.y; val dz = fishHeads[i + 2] - tip.z
            if (dx * dx + dy * dy + dz * dz < 16.0) { near = true; break }
            i += 3
        }
        if (near && c.state != RETREAT) { c.hideFor = rng.range(20.0, 60.0); c.out = max(0.0, c.out - 0.001) }
        if (c.hideFor > 0) {
            c.hideFor -= h
            c.out = max(0.0, c.out - h * 4.0 / c.lengthCm)
        } else when (c.state) {
            EMERGE, ACTIVE -> c.out = min(c.outTarget, c.out + h * c.kind.speedCmS / c.lengthCm)
            RETREAT -> { c.out -= h * 4.0 / c.lengthCm; if (c.out <= 0) c.state = GONE }
        }
        c.head = tentaclePoint(c, c.out)
    }

    /** Point at fraction [f] of the tentacle's full length along its gently curving path over the sand. */
    private fun tentaclePoint(c: Critter, f: Double): Vec3 {
        val s = f * c.lengthCm
        val a = atan2(c.heading.z, c.heading.x) + c.curl * s / c.lengthCm * 1.5
        // integrate the arc analytically: constant curvature k = curl * 1.5 / length
        val k = c.curl * 1.5 / c.lengthCm
        val a0 = atan2(c.heading.z, c.heading.x)
        val (dx, dz) = if (abs(k) < 1e-6) s * cos(a0) to s * sin(a0) else (sin(a) - sin(a0)) / k to (cos(a0) - cos(a)) / k
        val x = (c.anchor.x + dx).coerceIn(1.0, L - 1); val z = (c.anchor.z + dz).coerceIn(1.0, W - 0.6)
        return Vec3(x, sandY(x, z) + c.kind.liftBL * c.lengthCm + 0.02, z)
    }

    private fun mysid(c: Critter, h: Double) {
        c.phase += 2.5 * h; c.limb += 4.0 * h
        val centre = swarm ?: return
        c.offset = c.offset + Vec3(rng.signed(), rng.signed() * 0.4, rng.signed()) * (1.2 * h)
        // startle: a fish within 6 cm makes it tail-flip backward 5-10 BL (research 10 §5.3)
        if (c.timer <= 0 && fishNear(c.head, 6.0)) { c.offset = c.offset - c.heading * (7 * c.lengthCm); c.timer = 1.5 }
        if (c.state != RETREAT && c.timer > 0) c.timer -= h
        c.offset = Vec3(c.offset.x.coerceIn(-3.0, 3.0), c.offset.y.coerceIn(-1.5, 1.5), c.offset.z.coerceIn(-2.5, 2.5))
        val jitter = Vec3(sin(c.phase * 2 * PI), sin(c.phase * 2 * PI * 1.3 + 1), cos(c.phase * 2 * PI * 0.9)) * 0.15
        if (c.state == RETREAT) {
            c.offset = c.offset * (1 - h)
            c.timer = max(c.timer, 0.0) + h
            if (c.timer > 2.5) c.state = GONE
        }
        val goal = centre + c.offset + jitter + (if (c.state == RETREAT) (holes.firstOrNull()?.second ?: Vec3.Y) * (-c.timer) else Vec3.ZERO)
        c.head = c.head.lerp(goal, min(1.0, 3 * h))
        c.heading = rotateAbout(c.heading, Vec3.Y, rng.signed() * 0.4 * h)
        if (c.heading.x > -0.3) c.heading = rotateAbout(c.heading, Vec3.Y, 0.5 * h)   // head into the main flow (−x)
    }

    /**
     * Skeleton shrimp: rear pereopods gripping the perch, body standing off the surface and swaying as a pendulum at
     * 0.5-1 Hz in the flow (research 17 §5.4); gnathopods held up mantis-like (the leg sweep animates them slowly).
     * Lets go and drops back into the turf to hide.
     */
    private fun caprellid(c: Critter, h: Double) {
        c.phase += 0.7 * h; c.limb += 0.3 * h
        if (c.state == RETREAT) { c.timer += h; if (c.timer > 1.0) c.state = GONE }
        val sway = 0.3 * sin(c.phase * 2 * PI) - (if (c.state == RETREAT) c.timer * 1.4 else 0.0)
        val lean = (c.normal * cos(sway) + c.offset * sin(sway) + (c.normal cross c.offset) * (0.15 * sin(c.phase * PI))).normalized(c.normal)
        c.heading = lean
        c.head = c.anchor + lean * c.lengthCm
    }

    private fun fishNear(p: Vec3, r: Double): Boolean {
        val r2 = r * r
        var i = 0
        while (i + 2 < fishHeads.size) {
            val dx = fishHeads[i] - p.x; val dy = fishHeads[i + 1] - p.y; val dz = fishHeads[i + 2] - p.z
            if (dx * dx + dy * dy + dz * dz < r2) return true
            i += 3
        }
        return false
    }

    /** Calanoid hop-and-sink: a 1-3 mm hop every 0.5-2 s, sinking 0.7 mm/s between; hops lean toward the light (up). */
    private fun plankton(c: Critter, h: Double) {
        if (c.state == RETREAT) { c.state = GONE; return }
        c.timer -= h
        c.limb += 15 * h
        if (c.timer <= 0) {
            c.heading = (Vec3(rng.signed(), rng.signed() * 0.5 + 0.35, rng.signed())).normalized(Vec3.Y)
            c.offset = c.heading * rng.range(0.1, 0.3) * 20.0   // hop speed (cm/s) decays over ~50 ms
            c.timer = rng.range(0.5, 2.0)
        }
        c.offset = c.offset * max(0.0, 1 - 20 * h)
        val next = c.head + (c.offset + Vec3(0.0, -0.07, 0.0)) * h
        c.head = Vec3(next.x.coerceIn(1.5, L - 1.5), next.y.coerceIn(sandY(next.x, next.z) + 1, config.waterLevelCm - 1), next.z.coerceIn(1.5, W - 1))
    }

    /** Gammarid: stop-go scuttle, occasional side-swim bout to another perch; drops back into a crevice to hide. */
    private fun amphipod(c: Critter, h: Double) {
        if (c.state == SWIM) {
            c.timer -= h
            c.limb += 12 * h; c.phase += 3 * h
            c.roll = min(PI / 2, c.roll + 6 * h)
            c.heading = rotateAbout(c.heading, c.normal, 0.8 * h).normalized()
            val down = if (c.timer < 0.3) Vec3(0.0, -2.0, 0.0) else Vec3.ZERO
            val next = c.head + (c.heading * 5.0 + down) * h
            c.head = Vec3(next.x.coerceIn(2.0, L - 2), next.y.coerceIn(sandY(next.x, next.z) + 0.3, config.waterLevelCm - 4), next.z.coerceIn(2.0, W - 1))
            c.trail.clear()
            if (c.timer <= 0) land(c)
            return
        }
        c.roll = max(0.0, c.roll - 6 * h)
        if (c.state == RETREAT) { sink(c, h); return }
        // flees into cover when a fish comes within 5 cm (research 10 §5.3)
        if (fishNear(c.head, 5.0)) { c.state = RETREAT; c.timer = 0.0; return }
        c.timer -= h
        if (c.moving) {
            c.limb += 6.5 * h
            c.heading = rotateAbout(c.heading, c.normal, rng.signed() * 1.5 * h)
            crawlOnSurface(c, c.kind.speedCmS * h)
            if (rng.chance(0.9 * h)) c.moving = false
        } else {
            c.limb += 0.6 * h
            if (rng.chance(0.7 * h)) c.moving = true
        }
        if (rng.chance(0.04 * h) && c.scene != MicroScene.GLASS) {
            c.state = SWIM; c.timer = rng.range(0.5, 2.0)
            c.heading = (c.heading + c.normal * 0.6).normalized()
        }
    }

    private fun land(c: Critter) {
        c.state = ACTIVE
        val s = sdf
        if (s != null && s.dist(c.head) < 3.0) {
            val q = s.project(c.head)
            if (inTank(q) && q.y > sandY(q.x, q.z) + 0.3) {
                c.scene = MicroScene.ROCK; c.normal = s.gradient(q).normalized(Vec3.Y)
                c.head = q + c.normal * (c.kind.liftBL * c.lengthCm); c.heading = randomTangent(c.normal); return
            }
        }
        c.scene = MicroScene.SAND; c.normal = Vec3.Y
        c.head = Vec3(c.head.x, sandY(c.head.x, c.head.z) + c.kind.liftBL * c.lengthCm, c.head.z)
        c.heading = Vec3(c.heading.x, 0.0, c.heading.z).normalized(Vec3.X)
    }

    /** Pods, isopods, micro-snails and flatworms: correlated random walk on their surface, pauses, hides by sinking in. */
    private fun crawler(c: Critter, h: Double) {
        if (c.state == RETREAT) {
            if (c.scene == MicroScene.GLASS) { c.state = GONE; return }   // walks off out of view
            sink(c, h); return
        }
        val k = c.kind
        c.phase += 0.5 * h
        val turn = when (k) { CritterKind.COPEPOD -> 2.5; CritterKind.ISOPOD -> 1.2; else -> 0.5 }
        if (c.moving) {
            c.limb += (if (k == CritterKind.COPEPOD) 3.0 else 2.0) * h
            c.heading = rotateAbout(c.heading, c.normal, rng.signed() * turn * h)
            val dart = if (k == CritterKind.COPEPOD && rng.chance(0.05 * h)) rng.range(0.2, 0.5) else 0.0
            crawlOnSurface(c, k.speedCmS * h + dart)
            if (k != CritterKind.SNAIL && k != CritterKind.FLATWORM && rng.chance(0.4 * h)) c.moving = false
        } else if (rng.chance(0.6 * h)) c.moving = true
    }

    /** Hides by backing into the surface over about a second, then disappears. */
    private fun sink(c: Critter, h: Double) {
        c.timer += h
        c.head = c.head - c.normal * (1.2 * h)
        c.limb += 6 * h
        if (c.timer > 1.2) c.state = GONE
    }

    /** Moves the head [d] cm along its heading and keeps it on its surface; turns back at edges. */
    private fun crawlOnSurface(c: Critter, d: Double) {
        if (d <= 0) return
        val lift = c.kind.liftBL * c.lengthCm
        val old = c.head
        var p = c.head + c.heading * d
        var ok = true
        when (c.scene) {
            MicroScene.GLASS -> {
                p = Vec3(p.x, p.y, W - lift)
                if (p.x !in 2.0..(L - 2) || p.y < SandBed.BASE_FRONT + 0.3 || p.y > config.waterLevelCm - 6) ok = false
            }
            MicroScene.SAND -> {
                p = Vec3(p.x, sandY(p.x, p.z) + lift, p.z)
                if (!inTank(p) || (sdf != null && sdf.dist(p) < 0.2)) ok = false
            }
            MicroScene.ROCK -> {
                val s = sdf
                if (s == null) ok = false else {
                    val q = s.project(p - c.normal * lift)
                    val n = s.gradient(q).normalized(c.normal)
                    p = q + n * lift
                    if (!inTank(p) || q.y < sandY(q.x, q.z) + 0.2 || q.distanceTo(old) > 3 * d + 0.5 || abs(s.dist(q)) > 0.4) ok = false
                    else c.normal = n
                }
            }
            MicroScene.WATER -> {}
        }
        if (!ok) { c.heading = rotateAbout(-c.heading, c.normal, rng.signed() * 0.8); return }
        c.heading = (p - old).rejectFrom(c.normal).normalized(c.heading)
        c.head = p
        val last = c.trail.firstOrNull() ?: run { c.trail.add(0, old); old }
        if (c.head.distanceTo(last) >= c.spacing) c.trail.add(0, c.head)
        if (c.kind != CritterKind.BRISTLEWORM) while (c.trail.size > 14) c.trail.removeAt(c.trail.size - 1)
    }

    private fun trailLen(c: Critter): Double {
        var s = 0.0
        var prev = c.head
        for (p in c.trail) { s += prev.distanceTo(p); prev = p }
        return s
    }

    private fun steerTo(c: Critter, goal: Vec3): Double {
        val to = (goal - c.head).rejectFrom(c.normal)
        if (to.lengthSq() < 1e-6) return 0.0
        val side = (c.normal cross c.heading) dot to.normalized()
        return (side * 2.0).coerceIn(-1.2, 1.2)
    }

    private fun randomTangent(n: Vec3): Vec3 {
        val a = (if (abs(n.y) < 0.9) Vec3.Y else Vec3.X).rejectFrom(n).normalized()
        return rotateAbout(a, n, rng.range(0.0, 2 * PI))
    }

    // ------------------------------------------------------------------ pose

    private fun fillPose(c: Critter, out: CritterPose) {
        out.id = "micro:${c.kind.speciesId}:${c.id}"
        out.kind = c.kind
        out.scale = c.lengthCm.toFloat()
        val k = c.kind
        val trailBody = k == CritterKind.BRISTLEWORM || k == CritterKind.TENTACLE
        val stations = Array(16) { Vec3.ZERO }
        if (k == CritterKind.TENTACLE) {
            // tip first; along the path back to the anchor, then down into the sand
            val ext = c.out * c.lengthCm
            for (i in 0 until 16) {
                val s = c.lengthCm * i / 15.0
                stations[i] = if (s <= ext) tentaclePoint(c, (ext - s) / c.lengthCm) else c.anchor + c.tailDir * (s - ext)
            }
        } else if (trailBody) {
            var prev = c.head
            var j = 0
            var acc = 0.0
            var seg = if (c.trail.isNotEmpty()) c.trail[0] else null
            stations[0] = c.head
            for (i in 1 until 16) {
                var need = c.lengthCm / 15.0
                var p = prev
                while (need > 0) {
                    val s = seg
                    if (s == null) { p = p + c.tailDir * need; need = 0.0; break }
                    val d = p.distanceTo(s)
                    if (d >= need) { p = p.lerp(s, need / d); need = 0.0 } else {
                        need -= d; p = s; j++; seg = if (j < c.trail.size) c.trail[j] else null
                    }
                }
                acc += c.lengthCm / 15.0
                stations[i] = p; prev = p
            }
        } else {
            val back = -c.heading
            for (i in 0 until 16) stations[i] = c.head + back * (c.lengthCm * i / 15.0)
        }
        // frames: forward along the body, up = surface normal (amphipods roll onto their side to swim)
        val side0 = (c.heading cross c.normal).normalized(Vec3.Z)
        val up = if (k == CritterKind.CAPRELLID) c.offset else if (c.roll > 1e-3) (c.normal * cos(c.roll) + side0 * sin(c.roll)).normalized(c.normal) else c.normal
        for (i in 0 until 16) {
            val p = stations[i]
            out.spine[i * 3] = p.x.toFloat(); out.spine[i * 3 + 1] = p.y.toFloat(); out.spine[i * 3 + 2] = p.z.toFloat()
            val fwd = when (i) {
                0 -> stations[0] - stations[1]
                15 -> stations[14] - stations[15]
                else -> stations[i - 1] - stations[i + 1]
            }.normalized(c.heading)
            val q = Quat.fromForwardUp(fwd, up)
            out.frames[i * 4] = q.x.toFloat(); out.frames[i * 4 + 1] = q.y.toFloat(); out.frames[i * 4 + 2] = q.z.toFloat(); out.frames[i * 4 + 3] = q.w.toFloat()
        }
        out.swimPhase = (c.phase % 1000.0).toFloat()
        out.appendage = (c.limb % 1000.0).toFloat()
        when (k) {
            CritterKind.BRISTLEWORM -> { out.amp = 0.035f; out.waveLen = 1.6f }
            CritterKind.TENTACLE -> { out.amp = 0.02f; out.waveLen = 2.5f }
            CritterKind.FLATWORM -> { out.amp = 0.03f; out.waveLen = 2f }
            CritterKind.AMPHIPOD -> { out.amp = if (c.state == SWIM) 0.06f else 0.01f; out.waveLen = 0.8f }
            CritterKind.MYSID -> { out.amp = 0.02f; out.waveLen = 0.8f }
            else -> { out.amp = 0f; out.waveLen = 1f }
        }
    }

    companion object {
        private const val EMERGE = 0
        private const val ACTIVE = 1
        private const val RETREAT = 2
        private const val SWIM = 3
        private const val GONE = 4
        /** A worm roams at most this many trail points (≈ 16 body lengths) before heading home. */
        private const val MAX_TRAIL = 190

        private fun atan2(y: Double, x: Double) = kotlin.math.atan2(y, x)

        fun rotateAbout(v: Vec3, axis: Vec3, angle: Double): Vec3 = Vec3.rotateAbout(v, axis, angle)

        private fun <T> List<T>.randomOrNull(r: Rng): T? = if (isEmpty()) null else this[r.nextInt(size)]
    }
}
