package com.reeflive.app.render

import com.reeflive.core.sim.FoodKind
import com.reeflive.core.sim.FoodSystem
import com.reeflive.core.sim.NoriPose
import com.reeflive.core.sim.SimSnapshot
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** Point-sprite shape ids drawn by `particles.frag` (keep in step with the shader). */
object Shape {
    const val DOT = 0f
    const val FLAKE = 1f
    const val SPHERE = 2f
    const val MYSIS = 3f
    const val POD = 4f
    const val HAZE = 5f
    const val BRINE = 6f
    const val KRILL = 7f
    const val CHUNK = 8f
    const val WAFER = 9f
    const val CYCLOPS = 10f
    const val CUBE = 11f
    const val NORI = 12f
    const val BUBBLE = 13f
    const val GLOW = 14f
    const val STREAK = 15f
}

/** Where packed points go: (position, size cm, linear rgba, shape, seed, screen angle, shape parameter). */
fun interface PointSink {
    fun put(x: Float, y: Float, z: Float, size: Float, r: Float, g: Float, b: Float, a: Float, shape: Float, seed: Float, angle: Float, param: Float)
}

/**
 * What every food looks like and how it moves in the tank (pure Kotlin, GL thread). The sim owns where each particle
 * is (sink speed, floating time, settling, drift: FoodKind / FoodSystem) and fish target those positions; this adds
 * the look on top: a shape per kind, colours, small visual-only motion (a flake tumbling, a pod hopping, a brine shrimp
 * wiggling, at most ~0.6 cm off the sim point and zero on the first frame), thawing cubes for frozen food, haze clouds
 * for phyto and coral powder, and the nori sheet on its clip skinned over the sim's flow-driven cloth, thinning and
 * fraying as fish graze it.
 *
 * Everything is packed as points into the shared particle buffer: [packBodies] writes exactly one point per food
 * particle (in snapshot order), [packExtras] the cubes, clouds and the nori sheet.
 */
class FoodVisuals {
    private val cubeX = FloatArray(FoodKind.entries.size); private val cubeY = FloatArray(FoodKind.entries.size)
    private val cubeZ = FloatArray(FoodKind.entries.size); private val cubeAge = FloatArray(FoodKind.entries.size)
    private val cubeN = IntArray(FoodKind.entries.size)
    /** Per tile: the material its cell must keep for the tile to show (free edges and bottom go first). */
    private val tileThreshold: FloatArray = noriTileThresholds()

    /** One point per food particle, in snapshot order (at most [max]). */
    fun packBodies(snap: SimSnapshot, time: Float, max: Int, sink: PointSink) {
        val food = snap.food
        val meta = snap.foodMeta
        val n = min(food.size / 3, max)
        val hasMeta = meta.size >= n * FoodSystem.META_STRIDE
        for (i in 0 until n) {
            val ord = (snap.foodKinds.getOrNull(i)?.toInt() ?: 0).coerceIn(0, LOOKS.size - 1)
            val age = if (hasMeta) meta[i * FoodSystem.META_STRIDE] else 0f
            val settled = hasMeta && meta[i * FoodSystem.META_STRIDE + 1] > 0.5f
            val seed = if (hasMeta) meta[i * FoodSystem.META_STRIDE + 2] else ((i * 0.618034f) % 1f)
            body(ord, food[i * 3], food[i * 3 + 1], food[i * 3 + 2], age, settled, seed, time, sink)
        }
    }

    private fun body(ord: Int, x: Float, y: Float, z: Float, age: Float, settled: Boolean, seed: Float, time: Float, sink: PointSink) {
        val look = LOOKS[ord]
        val c = if (ord == FoodKind.FLAKE.ordinal) FLAKE_MIX[(seed * FLAKE_MIX.size).toInt().coerceIn(0, FLAKE_MIX.size - 1)] else look.color
        // visual motion ramps in over the first seconds, so a fresh particle sits exactly on the sim point
        val ramp = min(age / 2f, 1f)
        val ph = seed * 6.283f
        var ox = 0f; var oy = 0f; var oz = 0f
        var angle = ph
        var param = 1f
        val onSurface = !settled && y > Camera.WATER_Y - 0.8f
        var alpha = 0.95f
        when (FoodKind.entries[ord]) {
            FoodKind.FLAKE -> when {
                settled -> { param = 0.45f }
                onSurface -> { param = 0.3f; oy = sin(time * 1.6f + ph) * 0.05f }         // lying flat on the film
                else -> {                                                                 // fluttering down
                    param = 0.18f + 0.82f * abs(sin(age * 2.3f + ph))
                    ox = sin(age * 1.7f + ph * 3f) * 0.45f * ramp
                    oz = cos(age * 1.3f + ph * 2f) * 0.3f * ramp
                    angle = ph + age * 0.8f
                }
            }
            FoodKind.PELLET -> { if (!settled) angle = ph + age * 2f }
            FoodKind.FLOATING_PELLET -> { if (onSurface) oy = sin(time * 2.1f + ph) * 0.07f }
            FoodKind.MYSIS, FoodKind.BRINE, FoodKind.KRILL, FoodKind.CYCLOPS -> {
                // frozen: freed from the thawing cube over its first seconds in the water
                param = thawOut(age)
                alpha *= param
                if (FoodKind.entries[ord] == FoodKind.BRINE) {
                    param = sin(time * 5f + ph * 4f) * 0.6f                                 // limp tail wiggle in the flow
                    angle = ph + sin(time * 0.7f + ph) * 0.4f
                } else if (!settled) {
                    angle = ph + age * (0.4f + seed)                                        // slow tumble
                }
                if (FoodKind.entries[ord] == FoodKind.CYCLOPS) { param = 1f; ox = sin(time * 3f + ph * 5f) * 0.08f * ramp }
            }
            FoodKind.PRAWN -> { if (!settled) angle = ph + age * 1.2f }
            FoodKind.PODS -> {
                // live pods: dart, glide, dart again: hops between random points within 0.6 cm of the sim position
                val rate = 0.9f + seed * 0.8f
                val hp = age * rate + seed * 7f
                val k = floor(hp); val f = hp - k
                val e = 1f - (1f - f).pow(6)
                ox = (lerp(hj(k, seed, 0), hj(k + 1, seed, 0), e)) * 0.6f * ramp
                oy = (lerp(hj(k, seed, 1), hj(k + 1, seed, 1), e)) * 0.4f * ramp
                oz = (lerp(hj(k, seed, 2), hj(k + 1, seed, 2), e)) * 0.6f * ramp
                angle = atan2(hj(k + 1, seed, 1) - hj(k, seed, 1), hj(k + 1, seed, 0) - hj(k, seed, 0))
                param = 0f
            }
            FoodKind.PLANKTON, FoodKind.CORAL_FOOD -> { alpha = 0.55f; ox = sin(time * 2f + ph * 9f) * 0.05f }
            FoodKind.ALGAE_WAFER -> {
                param = if (settled) 0.38f else 0.25f + 0.75f * abs(sin(age * 1.6f + ph))
                if (!settled) angle = ph + age * 0.5f
            }
            FoodKind.NORI -> Unit
        }
        sink.put(x + ox, y + oy, z + oz, look.size, c[0], c[1], c[2], alpha, look.shape, seed, angle, param)
    }

    /** Thawing cubes (frozen foods), phyto / powder haze, and the nori sheet with its torn flecks. */
    fun packExtras(snap: SimSnapshot, time: Float, haze: PointSink, solid: PointSink, maxHaze: Int) {
        val food = snap.food
        val meta = snap.foodMeta
        val n = food.size / 3
        val hasMeta = meta.size >= n * FoodSystem.META_STRIDE
        cubeN.fill(0); cubeX.fill(0f); cubeY.fill(0f); cubeZ.fill(0f); cubeAge.fill(0f)
        var hazeLeft = maxHaze
        for (i in 0 until n) {
            val ord = (snap.foodKinds.getOrNull(i)?.toInt() ?: 0).coerceIn(0, LOOKS.size - 1)
            val kind = FoodKind.entries[ord]
            val age = if (hasMeta) meta[i * FoodSystem.META_STRIDE] else 0f
            val seed = if (hasMeta) meta[i * FoodSystem.META_STRIDE + 2] else 0.5f
            val x = food[i * 3]; val y = food[i * 3 + 1]; val z = food[i * 3 + 2]
            if (kind in FROZEN && age < THAW_S) {
                cubeN[ord]++; cubeX[ord] += x; cubeY[ord] += y; cubeZ[ord] += z; cubeAge[ord] = max(cubeAge[ord], age)
            }
            // fine foods: a coloured cloud that spreads and fades as it disperses in the flow
            if ((kind == FoodKind.PLANKTON || kind == FoodKind.CORAL_FOOD || kind == FoodKind.CYCLOPS) && hazeLeft > 0) {
                val spread = min(age / 40f, 1f)
                val c = if (kind == FoodKind.CYCLOPS) CYCLOPS_HAZE else LOOKS[ord].color
                val a = (if (kind == FoodKind.CYCLOPS) 0.07f else 0.16f) * (1f - 0.7f * spread)
                haze.put(x, y, z, 1.6f + 4.5f * spread + seed, c[0], c[1], c[2], a, Shape.HAZE, seed, seed * 6f, 0f)
                hazeLeft--
            }
        }
        for (ord in cubeN.indices) {
            val cnt = cubeN[ord]
            if (cnt == 0) continue
            val thaw = (cubeAge[ord] / THAW_S).coerceIn(0f, 1f)
            val c = LOOKS[ord].color
            solid.put(
                cubeX[ord] / cnt, cubeY[ord] / cnt + 0.4f, cubeZ[ord] / cnt, 2.2f * (1f - 0.65f * thaw),
                c[0], c[1], c[2], 1f, Shape.CUBE, ord * 0.13f, time * 0.4f + ord, thaw,
            )
        }
        packNori(snap.nori, time, solid)
    }

    /**
     * The nori sheet skinned over the sim's cloth ([NoriPose]): each of the [NORI_COLS] × [NORI_ROWS] tiles sits at its
     * bilinear spot between the four cloth nodes around it, so the sheet streams, flutters and tugs exactly as the sim
     * moves it. A tile shows while its cell still has more material than the tile's own threshold, so a bitten cell
     * thins out tile by tile into a ragged edge, and a torn-off cell is simply gone (its piece drifts as food).
     */
    private fun packNori(n: NoriPose?, time: Float, sink: PointSink) {
        if (n == null || n.cols < 2 || n.rows < 2 || n.nodes.size < n.cols * n.rows * 3) return
        // the clip on its pane, just out from the glass
        sink.put(n.clipX + n.inX * 0.4f, n.clipY + 0.6f, n.clipZ + n.inZ * 0.4f, 1.8f, 0.10f, 0.32f, 0.12f, 1f, Shape.SPHERE, 0.3f, 0f, 0f)
        val cc = n.cols - 1; val cr = n.rows - 1
        for (t in 0 until NORI_COLS * NORI_ROWS) {
            val col = t % NORI_COLS; val row = t / NORI_COLS
            val gu = (col + 0.5f) / NORI_COLS * cc; val gv = (row + 0.5f) / NORI_ROWS * cr
            val ci = gu.toInt().coerceIn(0, cc - 1); val cj = gv.toInt().coerceIn(0, cr - 1)
            val left = n.cells.getOrElse(cj * cc + ci) { 0f }
            if (left <= tileThreshold[t]) continue
            val fu = gu - ci; val fv = gv - cj
            val a = (cj * n.cols + ci) * 3; val b = a + 3; val c = a + n.cols * 3; val d = c + 3
            val w0 = (1 - fu) * (1 - fv); val w1 = fu * (1 - fv); val w2 = (1 - fu) * fv; val w3 = fu * fv
            val x = n.nodes[a] * w0 + n.nodes[b] * w1 + n.nodes[c] * w2 + n.nodes[d] * w3
            val y = n.nodes[a + 1] * w0 + n.nodes[b + 1] * w1 + n.nodes[c + 1] * w2 + n.nodes[d + 1] * w3
            val z = n.nodes[a + 2] * w0 + n.nodes[b + 2] * w1 + n.nodes[c + 2] * w2 + n.nodes[d + 2] * w3
            // thinned, chewed tiles look lighter and a touch smaller
            val shade = (0.85f + 0.15f * sin(col * 9f / NORI_COLS + row * 5f / NORI_ROWS)) * (0.8f + 0.2f * left)
            sink.put(x, y, z, 1.9f * (0.8f + 0.2f * left), NORI_COLOR[0] * shade, NORI_COLOR[1] * shade, NORI_COLOR[2] * shade, 0.92f,
                Shape.NORI, (t * 0.37f) % 1f, sin(time * 0.9f + t * 1.3f) * 0.12f, 0f)
        }
    }

    companion object {
        /** Seconds a frozen cube takes to thaw and let its food go. */
        const val THAW_S = 6f
        val FROZEN = setOf(FoodKind.MYSIS, FoodKind.BRINE, FoodKind.KRILL, FoodKind.CYCLOPS)
        /** Nori tiles across and down the sheet (the cloth itself comes from the sim: NoriPose). */
        const val NORI_COLS = 7
        const val NORI_ROWS = 9
        /** Wet nori: very dark, slightly translucent green (#24401C linear-ish). */
        val NORI_COLOR = floatArrayOf(0.035f, 0.11f, 0.025f)

        class Look(val shape: Float, val size: Float, val color: FloatArray)

        private fun lin(hex: Int): FloatArray = floatArrayOf(
            ((hex shr 16 and 0xFF) / 255f).pow(2.2f), ((hex shr 8 and 0xFF) / 255f).pow(2.2f), ((hex and 0xFF) / 255f).pow(2.2f),
        )

        /** By FoodKind ordinal (keep in step with core FoodKind). Sizes are world cm of the sprite (a little generous to read). */
        val LOOKS = arrayOf(
            Look(Shape.FLAKE, 0.6f, lin(0xC8A060)),       // FLAKE (colour picked per flake from FLAKE_MIX)
            Look(Shape.MYSIS, 0.95f, lin(0xE9DCCB)),      // MYSIS: pale, see-through, curled
            Look(Shape.SPHERE, 0.2f, lin(0xD9C9A2)),      // PELLET: tiny pale ~1 mm granule (Tee's pinch, video T97)
            Look(Shape.NORI, 1.4f, lin(0x24401C)),        // NORI (sheet drawn by packNori; a stray bit as a tile)
            Look(Shape.POD, 0.32f, lin(0xD8CBA8)),        // PODS: tiny tan hoppers
            Look(Shape.CHUNK, 0.85f, lin(0xE8A090)),      // PRAWN: raw pink chunk
            Look(Shape.HAZE, 0.25f, lin(0x6FA048)),       // PLANKTON: green phyto speck + haze
            Look(Shape.BRINE, 0.95f, lin(0xF08A48)),      // BRINE: slender orange adult Artemia
            Look(Shape.KRILL, 1.3f, lin(0xE0604C)),       // KRILL: banded red-pink pieces, black eyes
            Look(Shape.SPHERE, 0.34f, lin(0xC89A5C)),     // FLOATING_PELLET: lighter tan sphere
            Look(Shape.WAFER, 1.5f, lin(0x3A5226)),       // ALGAE_WAFER: dark green disc
            Look(Shape.HAZE, 0.2f, lin(0xD8A0A8)),        // CORAL_FOOD: pink-tan powder
            Look(Shape.CYCLOPS, 0.22f, lin(0xE8401E)),    // CYCLOPS: tiny red copepods
        )
        /** Flake food is a mix of colours: orange, red, green, tan. */
        val FLAKE_MIX = arrayOf(lin(0xE07A2E), lin(0xC0392B), lin(0x6E8B3D), lin(0xC8A060))
        val CYCLOPS_HAZE = lin(0xC85030)

        /** Opacity of a frozen food particle as its cube thaws: hidden in the block, then let go. */
        fun thawOut(age: Float): Float = ((age - 1f) / (THAW_S - 1f)).coerceIn(0.15f, 1f)

        private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

        /** Hop target in −1..1 for hop [k], per particle [seed] and axis. */
        private fun hj(k: Float, seed: Float, axis: Int): Float {
            val s = sin(k * 12.9898f + seed * 78.233f + axis * 37.719f) * 43758.547f
            return (s - floor(s)) * 2f - 1f
        }

        /**
         * Per tile, the material left in its cell below which the tile is gone: tiles toward the free bottom and side
         * edges go first, with a little noise so a bitten cell frays rather than shrinking as a square.
         */
        private fun noriTileThresholds(): FloatArray = FloatArray(NORI_COLS * NORI_ROWS) { t ->
            val u = (t % NORI_COLS + 0.5f) / NORI_COLS; val v = (t / NORI_COLS + 0.5f) / NORI_ROWS
            val h = sin(t * 91.7f) * 43758.5f
            val keep = (1f - v * 0.75f - abs(u - 0.5f) * 0.6f) * 0.6f + (h - floor(h)) * 0.4f
            (0.92f - 0.85f * keep).coerceIn(0.02f, 0.9f)
        }

        /** Screen-space angle of world direction ([dx], [dy], [dz]) at ([x], [y], [z]) under [m] (column-major view-proj). */
        fun screenAngle(m: FloatArray, x: Float, y: Float, z: Float, dx: Float, dy: Float, dz: Float): Float {
            fun sx(px: Float, py: Float, pz: Float): Pair<Float, Float> {
                val cx = m[0] * px + m[4] * py + m[8] * pz + m[12]
                val cy = m[1] * px + m[5] * py + m[9] * pz + m[13]
                val cw = (m[3] * px + m[7] * py + m[11] * pz + m[15]).let { if (abs(it) < 1e-4f) 1e-4f else it }
                return cx / cw to cy / cw
            }
            val (ax, ay) = sx(x, y, z)
            val (bx, by) = sx(x + dx, y + dy, z + dz)
            // gl_PointCoord has y down: flip the screen y
            return atan2(-(by - ay), bx - ax)
        }
    }
}
