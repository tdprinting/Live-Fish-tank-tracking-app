package com.reeflive.core.sim

import com.reeflive.core.geom.Rng
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Microscope magnifications (research 10 §5.1, §5.2): field of view on a surface (cm²), the water volume in focus (mL)
 * and the long edge of the field (mm) that sets what is resolvable on a ~1080 px wide view.
 */
enum class MicroZoom(val label: String, val fieldCm2: Double, val volumeMl: Double, val edgeMm: Double) {
    X1("1x", 510.0, 2500.0, 300.0),
    X10("10x", 5.0, 25.0, 30.0),
    X40("40x", 0.3, 0.3, 7.5),
}

/** One group's share of a field of view: sprites to draw and the uncapped expectation (for the shimmer layer). */
class MicroCount(val group: MicroGroup, val sprites: Int, val expected: Double, val nauplii: Int)

/**
 * What the microscope view shows (research 10 §5.2, §6.2 steps 2-3): expected counts per field from the model's
 * visible densities, Poisson draws capped at [SPRITE_CAP] sprites, a nauplius share when the juvenile pool is large,
 * and the phyto haze. Pure and deterministic for a given seed, so the app and the tests agree.
 */
object MicroView {
    const val SPRITE_CAP = 300
    /** Pixels across the field the §5.1 resolution rule assumes. */
    const val VIEW_PX = 1080.0
    /** Nauplii appear in the 10x/40x views only when juveniles exceed this share of the adults (§6.3 #11). */
    const val NAUPLII_MIN_RATIO = 0.2
    /** Copepod nauplius length, mm (Apocyclops N1 0.07 × 0.10, §1). */
    const val NAUPLIUS_MM = 0.09

    /** A group renders only when it spans at least 2 pixels at this magnification (§5.1). */
    fun resolvable(g: MicroGroup, zoom: MicroZoom): Boolean = g.sizeMm >= 2 * zoom.edgeMm / VIEW_PX

    /** Too big to fit sensibly in the field (a 5 cm bristleworm at 40x): left out. */
    fun fits(g: MicroGroup, zoom: MicroZoom): Boolean = g.sizeMm <= zoom.edgeMm * 0.6

    /** Expected animals of [g] in view: density × field area (surfaces) or × focus volume (water). */
    fun expected(s: MicrofaunaState, g: MicroGroup, scene: MicroScene, zoom: MicroZoom): Double {
        if (!s.present[g.ordinal] || g == MicroGroup.PHYTO) return 0.0
        val d = s.visible(g, scene)
        return d * (if (scene == MicroScene.WATER) zoom.volumeMl else zoom.fieldCm2)
    }

    /** Expected nauplii of a copepod group in view (0 unless the juvenile pool exceeds [NAUPLII_MIN_RATIO]). */
    fun nauplii(s: MicrofaunaState, g: MicroGroup, scene: MicroScene, zoom: MicroZoom): Double {
        if (!g.isCopepod || zoom == MicroZoom.X1) return 0.0
        val ratio = s.juvenileRatio[g.ordinal]
        if (ratio <= NAUPLII_MIN_RATIO) return 0.0
        // visible densities count a juvenile as a third of an adult (they are mostly below 1x resolution)
        return expected(s, g, scene, zoom) * ratio
    }

    /**
     * Phyto haze opacity 0..1: linear in log10(cells/mL) from 10^3 (clear) to 10^6 (opaque green), §6.2 step 5. At 40x
     * the cells themselves begin to show, so the haze is lighter.
     */
    fun phytoHaze(s: MicrofaunaState, zoom: MicroZoom): Float {
        val c = s.phytoPerMl
        if (c <= 1e3) return 0f
        val a = ((kotlin.math.log10(c) - 3.0) / 3.0).coerceIn(0.0, 1.0)
        return (a * (if (zoom == MicroZoom.X40) 0.6 else 0.9)).toFloat()
    }

    /** Phytoplankton cells drawn as dots at 40x (Tetraselmis-size cells, a capped share of the field). */
    fun phytoCells(s: MicrofaunaState, zoom: MicroZoom): Int =
        if (zoom != MicroZoom.X40) 0 else min(60.0, s.phytoPerMl * zoom.volumeMl * 1e-3).toInt()

    /**
     * Sample a field of view: one Poisson draw per resolvable group (§6.2 step 3), scaled down together when the total
     * passes [SPRITE_CAP]. [seed] is per view location and hour, so reopening the same spot looks the same.
     */
    fun sample(s: MicrofaunaState, scene: MicroScene, zoom: MicroZoom, seed: Long): List<MicroCount> {
        val rng = Rng(seed)
        val raw = ArrayList<MicroCount>()
        for (g in MicroGroup.entries) {
            if (g == MicroGroup.PHYTO || !resolvable(g, zoom) || !fits(g, zoom)) continue
            val lam = expected(s, g, scene, zoom)
            val nl = if (NAUPLIUS_MM >= 2 * zoom.edgeMm / VIEW_PX) nauplii(s, g, scene, zoom) else 0.0
            val n = poisson(rng, lam)
            val nn = poisson(rng, nl)
            if (n > 0 || nn > 0 || lam > 0.05) raw += MicroCount(g, n, lam, nn)
        }
        val total = raw.sumOf { it.sprites + it.nauplii }
        if (total <= SPRITE_CAP) return raw
        val k = SPRITE_CAP.toDouble() / total
        return raw.map { MicroCount(it.group, (it.sprites * k).toInt(), it.expected, (it.nauplii * k).toInt()) }
    }

    /** Knuth for small means, normal approximation above 30. */
    fun poisson(rng: Rng, lambda: Double): Int {
        if (lambda <= 0) return 0
        if (lambda > 30) {
            val u1 = rng.nextDouble().coerceAtLeast(1e-12); val u2 = rng.nextDouble()
            val z = sqrt(-2 * ln(u1)) * kotlin.math.cos(2 * Math.PI * u2)
            return (lambda + sqrt(lambda) * z).toInt().coerceAtLeast(0)
        }
        val l = exp(-lambda)
        var k = 0
        var p = 1.0
        do { k++; p *= rng.nextDouble() } while (p > l)
        return k - 1
    }
}
