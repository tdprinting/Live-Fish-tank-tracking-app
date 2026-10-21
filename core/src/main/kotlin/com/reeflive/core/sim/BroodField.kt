package com.reeflive.core.sim

import com.reeflive.core.geom.Quat
import com.reeflive.core.geom.Rng
import com.reeflive.core.geom.Sdf
import com.reeflive.core.geom.Vec3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The nursery tank beside the display (left of the left glass, on the same stand): a black-sided 10-gallon larval
 * tank like hobby clownfish breeders use (50 × 25 × 30 cm). World cm, the display's frame (left glass x = 0).
 */
object NurseryTank {
    const val X0 = -60.0
    const val X1 = -9.0
    const val Z0 = 10.0
    const val Z1 = 35.4
    const val Y0 = 0.0
    const val Y1 = 30.5
    const val WATER = 27.5
    val CENTER = Vec3((X0 + X1) / 2, WATER * 0.55, (Z0 + Z1) / 2)
}

/** One egg clutch, larva or fry for the renderer (spline-skinned like the microfauna). */
class BroodPose {
    var id = ""
    /** Model name: `models/<speciesId>.glb`. */
    var speciesId = ""
    /** 16 stations head → tail, xyz world cm. */
    val spine = FloatArray(48)
    /** 16 quaternions (xyzw). */
    val frames = FloatArray(64)
    var scale = 1f
    var swimPhase = 0f; var amp = 0f; var waveLen = 1f
}

/**
 * Places and moves the visible eggs, larvae and fry each frame from the snapshot's counts ([BroodView], the egg
 * clutches): purely visual, nothing feeds back into the sim (the counts come from [Brood]). Larvae hover and dart in
 * short bursts (5-7 body lengths), drift up toward light at night and swim to the keeper's flashlight while being
 * lured; when the count drops, the one nearest a fish's mouth goes (it was eaten). Fry in the nursery cruise in a
 * loose group facing the gentle current. Render thread only; fixed pools, so nothing is allocated per animal.
 */
class BroodField(private val sdf: Sdf?, private val config: TankConfig = TankConfig(), seed: Long = 11L) {
    private val rng = Rng(seed)

    private class Swarm(val cap: Int, val tag: String) {
        val x = DoubleArray(cap); val y = DoubleArray(cap); val z = DoubleArray(cap)
        val vx = DoubleArray(cap); val vy = DoubleArray(cap); val vz = DoubleArray(cap)
        val hx = DoubleArray(cap); val hy = DoubleArray(cap); val hz = DoubleArray(cap)
        val phase = DoubleArray(cap); val dart = DoubleArray(cap); val size = DoubleArray(cap)
        val serial = IntArray(cap)
        val ids = Array(cap) { "" }
        var n = 0
        fun removeAt(i: Int) {
            val j = n - 1
            x[i] = x[j]; y[i] = y[j]; z[i] = z[j]; vx[i] = vx[j]; vy[i] = vy[j]; vz[i] = vz[j]
            hx[i] = hx[j]; hy[i] = hy[j]; hz[i] = hz[j]; phase[i] = phase[j]; dart[i] = dart[j]; size[i] = size[j]; serial[i] = serial[j]; ids[i] = ids[j]
            n--
        }
    }

    private val display = Swarm(MAX_DISPLAY, "lv")
    private val nursery = Swarm(MAX_NURSERY, "ny")
    private val poses = ArrayList<BroodPose>()

    // ---- the breeder's routine, animated (core Brood.routine bumps BroodView.careSeq)
    /** What the keeper's hand is doing right now (null idle) and seconds into it. */
    var care: CareKind? = null; private set
    var careT = 0.0; private set
    private var lastSeq = -1
    /** The tool tip (dropper, siphon mouth or net) in tank cm. */
    val tool = DoubleArray(3)
    /** The food cloud: centre, radius (cm) and opacity 0..1 (0 = none). */
    val food = DoubleArray(3)
    var foodRadius = 0.0; private set
    var foodAlpha = 0.0; private set
    var foodKind = CareKind.ROTIFERS; private set
    private var used = 0
    private var serial = 0

    /**
     * Advances by [dt] seconds of wall time (already scaled by the sim speed). [quality] 0..1 scales the counts;
     * [fishHeads] holds the xyz of every organism's head.
     */
    fun update(s: SimSnapshot, dt: Double, quality: Double = 1.0, fishHeads: FloatArray = FloatArray(0)): List<BroodPose> {
        used = 0
        val h = dt.coerceIn(0.0, 0.1)
        val b = s.brood
        for (c in s.eggClutches) eggPose(c)
        val night = s.lightIntensity < LightSchedule.LIGHTS_ON
        // ---- larvae loose in the display
        val key = b.specKey.ifBlank { "clown" }
        val dEggs = b.displayEggs > 0
        val dCount = if (dEggs) b.displayEggs else b.displayLarvae
        val wantD = min((dCount * quality).toInt().coerceAtMost(dCount), (MAX_DISPLAY * quality).toInt())
        while (display.n > wantD) display.removeAt(nearestToMouth(display, fishHeads))
        while (display.n < wantD) spawn(display, b.displayOrigin, if (dEggs) 4.0 else 2.0, if (dEggs) EGG_CM else 0.45)
        if (dEggs) {
            // pelagic eggs (tangs) ride the surface current until they hatch
            stepEggs(display, h, nurseryBox = false)
            for (i in 0 until display.n) eggSpeck(display, i, "brood_${key}_egg")
        } else {
            val lure = if (b.luring) LURE else null
            stepSwarm(display, h, larva = true, nurseryBox = false, attract = lure, night = night)
            val larvaModel = "brood_${key}_larva"
            for (i in 0 until display.n) swimmerPose(display, i, larvaModel, display.size[i], true)
        }
        // ---- the nursery tank
        val wantN = min((b.nursery * quality).toInt().coerceAtMost(b.nursery), (MAX_NURSERY * quality).toInt())
        while (nursery.n > wantN) nursery.removeAt(rng.nextInt(nursery.n))
        while (nursery.n < wantN) spawn(nursery, NurseryTank.CENTER, 8.0, b.nurseryLengthMm / 10.0)
        stepCare(b.careSeq, b.careKind, b.nursery > 0, dt)
        if (b.nurseryEggs) {
            stepEggs(nursery, h, nurseryBox = true)
            for (i in 0 until nursery.n) eggSpeck(nursery, i, "brood_${key}_egg")
            return if (used == poses.size) poses else poses.subList(0, used)
        }
        val fry = b.nurseryMeta >= 0.5
        stepSwarm(nursery, h, larva = !fry, nurseryBox = true, attract = if (foodAlpha > 0.2) foodAt else null, night = night)
        val model = "brood_${key}_" + when (b.nurseryStage) { 0 -> "larva"; 1 -> "postlarva"; else -> "fry" }
        for (i in 0 until nursery.n) {
            nursery.size[i] = b.nurseryLengthMm / 10.0 * (0.9 + 0.2 * ((nursery.serial[i] * 0.618) % 1.0))
            swimmerPose(nursery, i, model, nursery.size[i], !fry)
        }
        return if (used == poses.size) poses else poses.subList(0, used)
    }

    fun clear() { display.n = 0; nursery.n = 0; used = 0 }

    private var foodAt = Vec3.ZERO
    private var careX = 0.0
    private var careZ = 0.0

    /**
     * Plays one keeper action per new [seq]: a dropper puffs rotifers / baby brine / crushed pellets in at the
     * surface and the young chase the cloud; a siphon tube sweeps the bottom; a net dips out a juvenile for the
     * display. One at a time (a newer one waits until the hand is out; at high nursery speed some are skipped).
     */
    private fun stepCare(seq: Int, kind: CareKind, any: Boolean, dt: Double) {
        if (lastSeq < 0 || !any) { lastSeq = seq; if (!any) { care = null; foodAlpha = 0.0 } }
        if (care == null && seq != lastSeq) {
            lastSeq = seq; care = kind; careT = 0.0
            careX = rng.range(NurseryTank.X0 + 10.0, NurseryTank.X1 - 10.0)
            careZ = rng.range(NurseryTank.Z0 + 6.0, NurseryTank.Z1 - 6.0)
        }
        val k = care
        val top = NurseryTank.Y1 + 10.0
        val surf = NurseryTank.WATER
        if (k != null) {
            careT += dt.coerceIn(0.0, 0.1)
            val t = careT
            fun dip(down: Double, hold: Double, depth: Double) = when {
                t < down -> top + (depth - top) * smooth(t / down)
                t < down + hold -> depth
                else -> depth + (top - depth) * smooth((t - down - hold) / down)
            }
            when (k) {
                CareKind.SIPHON -> {
                    val bottom = NurseryTank.Y0 + 0.8
                    tool[1] = dip(1.2, 4.0, bottom)
                    val f = ((t - 1.2) / 4.0).coerceIn(0.0, 1.0)
                    tool[0] = NurseryTank.X0 + 4 + (NurseryTank.X1 - NurseryTank.X0 - 8) * f
                    tool[2] = careZ
                    if (t > 6.4) care = null
                }
                CareKind.NET -> {
                    tool[0] = careX; tool[2] = careZ; tool[1] = dip(1.0, 1.2, surf - 8)
                    if (t > 3.2) care = null
                }
                else -> {
                    tool[0] = careX; tool[2] = careZ; tool[1] = dip(0.9, 0.8, surf + 1.5)
                    if (t > 1.3 && foodAlpha == 0.0) {
                        foodKind = k; food[0] = careX; food[1] = surf - 1.0; food[2] = careZ; foodRadius = 0.6; foodAlpha = 1.0
                    }
                    if (t > 2.6) care = null
                }
            }
        }
        if (foodAlpha > 0) {
            val h = dt.coerceIn(0.0, 0.1)
            val sink = if (foodKind == CareKind.PELLETS) 1.2 else 0.35
            food[1] = maxOf(NurseryTank.Y0 + 1.0, food[1] - sink * h)
            foodRadius = minOf(9.0, foodRadius + 1.6 * h)
            foodAlpha = maxOf(0.0, foodAlpha - h / 7.0)
            foodAt = Vec3(food[0], food[1], food[2])
        }
    }

    private fun smooth(x: Double): Double { val c = x.coerceIn(0.0, 1.0); return c * c * (3 - 2 * c) }

    private fun next(): BroodPose {
        if (used == poses.size) poses += BroodPose()
        return poses[used++]
    }

    private fun spawn(sw: Swarm, at: Vec3, spread: Double, sizeCm: Double) {
        val i = sw.n++
        sw.x[i] = at.x + rng.signed() * spread; sw.y[i] = at.y + rng.signed() * spread * 0.5; sw.z[i] = at.z + rng.signed() * spread
        sw.vx[i] = 0.0; sw.vy[i] = 0.0; sw.vz[i] = 0.0
        val a = rng.range(0.0, 2 * PI)
        sw.hx[i] = cos(a); sw.hy[i] = 0.0; sw.hz[i] = sin(a)
        sw.phase[i] = rng.range(0.0, 10.0); sw.dart[i] = rng.range(0.0, 1.5); sw.size[i] = sizeCm
        sw.serial[i] = serial++
        sw.ids[i] = sw.tag + sw.serial[i]
    }

    private fun nearestToMouth(sw: Swarm, heads: FloatArray): Int {
        var best = sw.n - 1
        var bd = Double.MAX_VALUE
        var k = 0
        while (k + 2 < heads.size) {
            val fx = heads[k].toDouble(); val fy = heads[k + 1].toDouble(); val fz = heads[k + 2].toDouble()
            for (i in 0 until sw.n) {
                val dx = sw.x[i] - fx; val dy = sw.y[i] - fy; val dz = sw.z[i] - fz
                val d = dx * dx + dy * dy + dz * dz
                if (d < bd) { bd = d; best = i }
            }
            k += 3
        }
        return best
    }

    /**
     * Hover-and-dart: a larva drifts (sinking a little), then every 0.5-2 s darts 5-7 body lengths, heading biased to
     * [attract] (the flashlight) or upward at night (phototaxis toward the moonlit surface). Fry cruise slowly into the
     * current with small turns. Walls, sand, surface and rock push back.
     */
    private fun stepSwarm(sw: Swarm, h: Double, larva: Boolean, nurseryBox: Boolean, attract: Vec3?, night: Boolean) {
        if (h <= 0) return
        val lo = if (nurseryBox) Vec3(NurseryTank.X0 + 1.5, NurseryTank.Y0 + 2.0, NurseryTank.Z0 + 1.5) else Vec3(1.0, TankDims.SAND_TOP_CM + 1.5, 1.0)
        val hi = if (nurseryBox) Vec3(NurseryTank.X1 - 1.5, NurseryTank.WATER - 1.0, NurseryTank.Z1 - 1.5) else Vec3(config.lengthCm - 1.0, config.waterLevelCm - 0.8, config.widthCm - 1.0)
        for (i in 0 until sw.n) {
            val bl = sw.size[i].coerceAtLeast(0.3)
            sw.dart[i] -= h
            if (sw.dart[i] <= 0) {
                // pick a new burst direction
                var dx = rng.signed(); var dy = rng.signed() * 0.4; var dz = rng.signed()
                if (attract != null) { dx = dx * 0.4 + (attract.x - sw.x[i]); dy = dy * 0.4 + (attract.y - sw.y[i]); dz = dz * 0.4 + (attract.z - sw.z[i]) }
                else if (larva && (night || nurseryBox)) dy += 0.5          // toward the light above
                if (!larva) { dx += 0.6; dy *= 0.5 }                          // fry hold station facing the gentle flow
                val l = sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(1e-6)
                val speed = if (larva) bl * rng.range(5.0, 7.0) else bl * rng.range(1.0, 2.5)
                sw.vx[i] = dx / l * speed; sw.vy[i] = dy / l * speed; sw.vz[i] = dz / l * speed
                sw.hx[i] = dx / l; sw.hy[i] = dy / l * 0.6; sw.hz[i] = dz / l
                sw.dart[i] = if (larva) rng.range(0.5, 2.0) else rng.range(1.0, 3.0)
            }
            // drag: a burst coasts out in a fraction of a second; larvae sink slowly between bursts
            val drag = if (larva) 4.0 else 1.5
            sw.vx[i] -= sw.vx[i] * min(1.0, drag * h); sw.vy[i] -= sw.vy[i] * min(1.0, drag * h); sw.vz[i] -= sw.vz[i] * min(1.0, drag * h)
            if (larva) sw.vy[i] -= 0.05 * h
            sw.x[i] += sw.vx[i] * h; sw.y[i] += sw.vy[i] * h; sw.z[i] += sw.vz[i] * h
            // keep inside the water and out of the rock
            if (sw.x[i] < lo.x) { sw.x[i] = lo.x; sw.vx[i] = abs(sw.vx[i]) } else if (sw.x[i] > hi.x) { sw.x[i] = hi.x; sw.vx[i] = -abs(sw.vx[i]) }
            if (sw.y[i] < lo.y) { sw.y[i] = lo.y; sw.vy[i] = abs(sw.vy[i]) } else if (sw.y[i] > hi.y) { sw.y[i] = hi.y; sw.vy[i] = -abs(sw.vy[i]) * 0.3 }
            if (sw.z[i] < lo.z) { sw.z[i] = lo.z; sw.vz[i] = abs(sw.vz[i]) } else if (sw.z[i] > hi.z) { sw.z[i] = hi.z; sw.vz[i] = -abs(sw.vz[i]) }
            if (!nurseryBox && sdf != null) {
                val p = Vec3(sw.x[i], sw.y[i], sw.z[i])
                val d = sdf.dist(p)
                if (d < 0.4) {
                    val e = 0.3
                    val gx = sdf.dist(Vec3(p.x + e, p.y, p.z)) - sdf.dist(Vec3(p.x - e, p.y, p.z))
                    val gy = sdf.dist(Vec3(p.x, p.y + e, p.z)) - sdf.dist(Vec3(p.x, p.y - e, p.z))
                    val gz = sdf.dist(Vec3(p.x, p.y, p.z + e)) - sdf.dist(Vec3(p.x, p.y, p.z - e))
                    val gl = sqrt(gx * gx + gy * gy + gz * gz).coerceAtLeast(1e-6)
                    val push = 0.4 - d
                    sw.x[i] += gx / gl * push; sw.y[i] += gy / gl * push; sw.z[i] += gz / gl * push
                }
            }
            // tail beat: fast while bursting, a slow scull while hovering
            val sp = sqrt(sw.vx[i] * sw.vx[i] + sw.vy[i] * sw.vy[i] + sw.vz[i] * sw.vz[i])
            sw.phase[i] += h * (2.0 + 12.0 * (sp / bl).coerceAtMost(8.0) / 6.0)
        }
    }

    /** Floating eggs: a slow upwelling drift and a little jitter, held under the surface. */
    private fun stepEggs(sw: Swarm, h: Double, nurseryBox: Boolean) {
        if (h <= 0) return
        val top = if (nurseryBox) NurseryTank.WATER - 0.6 else config.waterLevelCm - 0.4
        val lo = if (nurseryBox) Vec3(NurseryTank.X0 + 1.5, NurseryTank.Y0 + 2.0, NurseryTank.Z0 + 1.5) else Vec3(1.0, config.waterLevelCm - 8.0, 1.0)
        val hi = if (nurseryBox) Vec3(NurseryTank.X1 - 1.5, top, NurseryTank.Z1 - 1.5) else Vec3(config.lengthCm - 1.0, top, config.widthCm - 1.0)
        for (i in 0 until sw.n) {
            sw.dart[i] -= h
            if (sw.dart[i] <= 0) {
                sw.vx[i] = rng.signed() * 0.6 + (if (nurseryBox) 0.0 else 0.8); sw.vy[i] = rng.signed() * 0.3 + 0.15; sw.vz[i] = rng.signed() * 0.6
                sw.dart[i] = rng.range(1.0, 3.0)
            }
            sw.x[i] = (sw.x[i] + sw.vx[i] * h).coerceIn(lo.x, hi.x)
            sw.y[i] = (sw.y[i] + sw.vy[i] * h).coerceIn(lo.y, hi.y)
            sw.z[i] = (sw.z[i] + sw.vz[i] * h).coerceIn(lo.z, hi.z)
            if (!nurseryBox && (sw.x[i] <= lo.x || sw.x[i] >= hi.x)) sw.vx[i] = -sw.vx[i]
            sw.phase[i] += h
        }
    }

    private fun eggSpeck(sw: Swarm, i: Int, model: String) {
        val p = next()
        p.id = sw.ids[i]
        p.speciesId = model
        val a = sw.phase[i] * 0.4
        val fx = cos(a); val fz = sin(a)
        writeStraight(p, Vec3(sw.x[i] + fx * EGG_CM / 2, sw.y[i], sw.z[i] + fz * EGG_CM / 2), Vec3(fx, 0.0, fz), Vec3.Y, EGG_CM)
        p.swimPhase = 0f; p.amp = 0f; p.waveLen = 1f
    }

    private fun swimmerPose(sw: Swarm, i: Int, model: String, lengthCm: Double, larva: Boolean) {
        val p = next()
        p.id = sw.ids[i]
        p.speciesId = model
        val fwd = Vec3(sw.hx[i], sw.hy[i], sw.hz[i]).normalized(Vec3.X)
        writeStraight(p, Vec3(sw.x[i] + fwd.x * lengthCm / 2, sw.y[i] + fwd.y * lengthCm / 2, sw.z[i] + fwd.z * lengthCm / 2), fwd, Vec3.Y, lengthCm)
        val sp = sqrt(sw.vx[i] * sw.vx[i] + sw.vy[i] * sw.vy[i] + sw.vz[i] * sw.vz[i]) / lengthCm.coerceAtLeast(0.1)
        p.swimPhase = (sw.phase[i] % 1000.0).toFloat()
        p.amp = if (larva) (0.03 + 0.012 * sp.coerceAtMost(6.0)).toFloat() else (0.02 + 0.01 * sp.coerceAtMost(4.0)).toFloat()
        p.waveLen = if (larva) 0.9f else 1.1f
    }

    private fun eggPose(c: EggClutchPose) {
        val p = next()
        p.id = c.id
        val f = if (c.hatchDays > 0f) c.ageDays / c.hatchDays else 0f
        p.speciesId = "brood_${if (c.kind == Reproduction.CLOWN) "clown" else c.kind.lowercase()}_eggs_" + when {
            f < 0.3f -> "fresh"
            f < 0.65f -> "dark"
            else -> "eyed"
        }
        val n = c.normal.normalized(Vec3.Y)
        var fwd = n cross Vec3.Y
        if (fwd.lengthSq() < 1e-4) fwd = Vec3.X
        fwd = fwd.normalized(Vec3.X)
        val len = (3.0 * sqrt(c.eggs / 250.0)).coerceIn(2.0, 4.5)
        val centre = c.pos + n * 0.12
        writeStraight(p, centre + fwd * (len / 2), fwd, n, len)
        p.swimPhase = 0f; p.amp = 0f; p.waveLen = 1f
    }

    private fun writeStraight(p: BroodPose, head: Vec3, fwd: Vec3, up: Vec3, len: Double) {
        val q = Quat.fromForwardUp(fwd, up)
        for (i in 0 until 16) {
            val s = len * i / 15.0
            p.spine[i * 3] = (head.x - fwd.x * s).toFloat(); p.spine[i * 3 + 1] = (head.y - fwd.y * s).toFloat(); p.spine[i * 3 + 2] = (head.z - fwd.z * s).toFloat()
            p.frames[i * 4] = q.x.toFloat(); p.frames[i * 4 + 1] = q.y.toFloat(); p.frames[i * 4 + 2] = q.z.toFloat(); p.frames[i * 4 + 3] = q.w.toFloat()
        }
        p.scale = len.toFloat()
    }

    companion object {
        /** Most larvae drawn in the display / young drawn in the nursery (High quality; Medium scales by 0.5). */
        const val MAX_DISPLAY = 36
        /** A pelagic egg's size (tang eggs ~0.8 mm, research 41 [S]; drawn at 0.9 mm). */
        const val EGG_CM = 0.09
        const val MAX_NURSERY = 28
        /** The keeper's flashlight against the front glass at the left end (larvae swim to light, research 28 §2). */
        val LURE = Vec3(8.0, 30.0, TankDims.DEPTH_CM - 2.0)

        fun modelFor(speciesId: String): String? = if (speciesId.startsWith("brood_")) "models/$speciesId.glb" else null
    }
}
