package com.reeflive.core.sim

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Glass panes that carry a spatial algae mask ([GlassMask]). Only the front pane, the one the owner scrapes, has one. */
enum class GlassPane { FRONT }

/**
 * Where the film is on one glass pane: a [W] × [H] grid over the wetted pane, each cell the film's local density
 * relative to the pane-uniform level that [AlgaeModel] tracks (1 = untouched, 0 = scraped bare). Cell (i, j) covers
 * `x = i/W … (i+1)/W` of the tank length (from the left end) and `y = sand top + j/H … (j+1)/H` of the wetted height
 * (from the sand line up to the water line); cells are stored row by row, i fastest, so the array uploads as a
 * W × H texture with u = x / length and v = (y − sand top) / (water level − sand top).
 *
 * A Flipper pass ([scrape]) knocks a swept box of cells down to [AlgaeModel.SCRAPE_LEFT]; the cleared stripe grows
 * back logistically at the glass film's net growth rate ([regrow]), seeded from the film left at the edges. Grazing
 * snails and stars on the pane ([graze], [GlassTrails]) wear narrow trails into it the same way, written quietly and
 * shown in batches ([publish]) so the snapshot copy is not remade every tick.
 */
class GlassMask(val lengthCm: Double, val bottomCm: Double, val topCm: Double) {
    val cells = FloatArray(W * H) { 1f }
    /** Bumped on every change (renderer re-upload, snapshot cache). */
    var version = 0; private set
    /** Mean density over the pane (1 = untouched). */
    var mean = 1.0; private set
    private var exported: FloatArray? = null
    private var exportedVersion = -1

    /** Grazed cells not yet published (no [version] bump yet). */
    private var pending = false

    private val heightCm: Double get() = max(topCm - bottomCm, 1.0)
    val isFull: Boolean get() = mean >= 1.0 - 1e-6

    /** Density at a point of the pane (cm from the left end, cm from the tank floor); outside the wetted area = 1. */
    fun at(xCm: Double, yCm: Double): Float {
        val u = xCm / lengthCm; val v = (yCm - bottomCm) / heightCm
        if (u < 0 || u >= 1 || v < 0 || v >= 1) return 1f
        return cells[(v * H).toInt() * W + (u * W).toInt()]
    }

    /**
     * The scraper pad ([halfW] × [halfH] cm half-extents, axis-aligned) slides from (x0, y0) to (x1, y1) (pane cm, as
     * in [at]). Every cell whose centre the pad passes over keeps [left] of its film. Returns the drop of [mean].
     */
    fun scrape(x0: Double, y0: Double, x1: Double, y1: Double, halfW: Double, halfH: Double, left: Double): Double {
        val keep = left.coerceIn(0.0, 1.0).toFloat()
        val cw = lengthCm / W; val ch = heightCm / H
        val i0 = max(0, floor((min(x0, x1) - halfW) / cw).toInt()); val i1 = min(W - 1, ceil((max(x0, x1) + halfW) / cw).toInt())
        val j0 = max(0, floor((min(y0, y1) - halfH - bottomCm) / ch).toInt()); val j1 = min(H - 1, ceil((max(y0, y1) + halfH - bottomCm) / ch).toInt())
        if (i0 > i1 || j0 > j1) return 0.0
        val dx = x1 - x0; val dy = y1 - y0
        var removed = 0.0
        for (j in j0..j1) {
            val cy = bottomCm + (j + 0.5) * ch
            for (i in i0..i1) {
                val cx = (i + 0.5) * cw
                if (!swept(cx - x0, dx, halfW, cy - y0, dy, halfH)) continue
                val k = j * W + i
                val before = cells[k]
                val after = before * keep
                if (after < before) { cells[k] = after; removed += before - after }
            }
        }
        if (removed <= 0) return 0.0
        val drop = removed / (W * H)
        mean -= drop
        version++; pending = false
        return drop
    }

    /**
     * Logistic regrowth `dm/dt = r · (m + seed) · (1 − m)` for [days] at net rate [ratePerDay] (the glass film's
     * specific growth minus its die-back); the seed is the film the pad leaves in scratches and at the edges.
     */
    fun regrow(ratePerDay: Double, days: Double) {
        if (isFull || ratePerDay <= 0 || days <= 0) return
        val steps = max(1, ceil(ratePerDay * days / 0.05).toInt())
        val h = (ratePerDay * days / steps).toFloat()
        val seed = REGROW_SEED.toFloat()
        var sum = 0.0
        for (k in cells.indices) {
            var m = cells[k]
            if (m < 1f) {
                repeat(steps) { m += h * (m + seed) * (1f - m) }
                m = min(1f, m)
                // close enough to the surrounding film to be invisible: snap so a regrown pane goes back to "full"
                if (m > 1f - 1e-4f) m = 1f
                cells[k] = m
            }
            sum += m
        }
        mean = sum / cells.size
        version++; pending = false
    }

    /** Uniform film again (the whole pane wiped, or the cover set from outside). */
    fun fill() {
        if (isFull && cells.all { it == 1f }) return
        cells.fill(1f); mean = 1.0; version++; pending = false
    }

    /**
     * A grazer's mouth went from (x0, y0) to (x1, y1) (pane cm, as in [at]) clearing a band [widthCm] wide and taking
     * [take] of the film it passes over. A cell loses film in proportion to how much of it the band covers across the
     * track (a 1 cm band over a 0.95 cm cell takes nearly all of [take]; over half the cell, about half), and only
     * cells whose centre projects onto [0, 1) of the segment count, so consecutive segments of one path tile it
     * without double grazing the joints. Not published until [publish]. Returns the drop of [mean].
     */
    fun graze(x0: Double, y0: Double, x1: Double, y1: Double, widthCm: Double, take: Double): Double {
        val ux = x1 - x0; val uy = y1 - y0
        val len2 = ux * ux + uy * uy
        if (len2 < 1e-9 || take <= 0) return 0.0
        val cw = lengthCm / W; val ch = heightCm / H
        val len = kotlin.math.sqrt(len2)
        // cell size across the track (the band's normal is (−uy, ux) / len)
        val across = (kotlin.math.abs(uy) * cw + kotlin.math.abs(ux) * ch) / len
        val hw = widthCm / 2
        val reach = hw + across
        val i0 = max(0, floor((min(x0, x1) - reach) / cw).toInt()); val i1 = min(W - 1, ceil((max(x0, x1) + reach) / cw).toInt())
        val j0 = max(0, floor((min(y0, y1) - reach - bottomCm) / ch).toInt()); val j1 = min(H - 1, ceil((max(y0, y1) + reach - bottomCm) / ch).toInt())
        if (i0 > i1 || j0 > j1) return 0.0
        val tk = take.coerceIn(0.0, 1.0)
        var removed = 0.0
        for (j in j0..j1) {
            val py = bottomCm + (j + 0.5) * ch - y0
            for (i in i0..i1) {
                val px = (i + 0.5) * cw - x0
                val t = (px * ux + py * uy) / len2
                if (t < 0 || t >= 1) continue
                val d = kotlin.math.abs(px * uy - py * ux) / len
                // overlap of the cell's extent across the track [d − c/2, d + c/2] with the band [−hw, hw]
                val overlap = min(d + across / 2, hw) - max(d - across / 2, -hw)
                if (overlap <= 0) continue
                val k = j * W + i
                val before = cells[k]
                val after = (before * (1 - tk * min(1.0, overlap / across))).toFloat()
                if (after < before) { cells[k] = after; removed += before - after }
            }
        }
        if (removed <= 0) return 0.0
        val drop = removed / (W * H)
        mean -= drop
        pending = true
        return drop
    }

    /** Shows the trails grazed since the last call (bumps [version] once). Returns whether anything changed. */
    fun publish(): Boolean {
        if (!pending) return false
        pending = false
        version++
        return true
    }

    fun copyFrom(o: GlassMask) {
        o.cells.copyInto(cells); mean = o.mean; version++; pending = false
    }

    /** An immutable copy for the snapshot (shared until the mask changes), or null while the pane is untouched. */
    fun export(): FloatArray? {
        if (isFull) return null
        if (exportedVersion != version) { exported = cells.copyOf(); exportedVersion = version }
        return exported
    }

    companion object {
        /** 128 × 64: cells about 0.95 × 0.55 cm on the 122 × 35 cm wetted pane, fine enough for a snail's ~1 cm trail. */
        const val W = 128
        const val H = 64
        /** Relative density the regrowth starts from in a bare cell (film left in scratches and spreading from the edges). */
        const val REGROW_SEED = 0.03

        /** Does the box (±hw, ±hh) centred on the segment t·(dx, dy), t in 0..1, contain the point (px, py)? */
        private fun swept(px: Double, dx: Double, hw: Double, py: Double, dy: Double, hh: Double): Boolean {
            var lo = 0.0; var hi = 1.0
            if (kotlin.math.abs(dx) < 1e-9) { if (kotlin.math.abs(px) > hw) return false } else {
                val a = (px - hw) / dx; val b = (px + hw) / dx
                lo = max(lo, min(a, b)); hi = min(hi, max(a, b))
            }
            if (kotlin.math.abs(dy) < 1e-9) { if (kotlin.math.abs(py) > hh) return false } else {
                val a = (py - hh) / dy; val b = (py + hh) / dy
                lo = max(lo, min(a, b)); hi = min(hi, max(a, b))
            }
            return lo <= hi
        }
    }
}
