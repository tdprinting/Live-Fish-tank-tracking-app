package com.reeflive.core.sim

import com.reeflive.core.geom.Ellipsoid
import com.reeflive.core.geom.Vec3
import kotlin.math.exp
import kotlin.random.Random

/**
 * White dry rock for building a rockscape (the aquascape builder): the shapes a reef shop sells by the pound, from
 * fist-sized rubble to a tall pillar. Each rock is a few smooth-unioned ellipsoids ([RockPieceDef]) in its own frame:
 * centred on x = z = 0 with its lowest point at y = 0. [seed] jitters the lumps so no two rocks of a shape match.
 * Sizes suit a 48 × 18 in tank (a rock is 8 to 26 cm across).
 */
enum class DryRockShape(val label: String, val blurb: String) {
    BOULDER("Boulder", "A chunky base rock"),
    SLAB("Flat slab", "A wide shelf for corals"),
    PILLAR("Tall pillar", "Stands up high"),
    ARCH("Arch", "Fish swim under it"),
    BRANCH("Branchy", "Twisty fingers"),
    RUBBLE("Small rock", "A little filler piece"),
}

object DryRock {
    /** The rock's blobs (cm), lowest point at y = 0, centred on x = z = 0. */
    fun def(shape: DryRockShape, seed: Int, name: String = "D${shape.ordinal}"): RockPieceDef {
        val r = Random(seed * 7919 + shape.ordinal)
        fun j(a: Double) = a * (0.85 + 0.3 * r.nextDouble())
        fun s() = r.nextDouble() * 2 - 1
        val b = ArrayList<Ellipsoid>()
        fun e(x: Double, y: Double, z: Double, rx: Double, ry: Double, rz: Double) = b.add(Ellipsoid(Vec3(x, y, z), Vec3(j(rx), j(ry), j(rz))))
        // a broad, flat underside (shop rock is usually cut or broken flat at the base), so it stands where it lands
        fun foot(rx: Double, rz: Double) = b.add(Ellipsoid(Vec3(0.0, 1.4, 0.0), Vec3(rx, 1.4, rz)))
        when (shape) {
            DryRockShape.BOULDER -> {
                e(0.0, 6.0, 0.0, 8.0, 6.0, 7.0)
                foot(7.0, 6.0)
                repeat(4) { val a = it * 1.57 + s() * 0.5; e(5.5 * kotlin.math.cos(a), 5.0 + 3 * s(), 4.5 * kotlin.math.sin(a), 4.0, 3.5, 3.5) }
            }
            DryRockShape.SLAB -> {
                e(0.0, 2.6, 0.0, 12.0, 2.6, 8.0)
                e(5.0 * s(), 3.0, 3.0 * s(), 6.0, 2.4, 5.0)
                e(-6.0, 2.2, 2.0 * s(), 4.0, 2.0, 4.0)
            }
            DryRockShape.PILLAR -> {
                e(0.0, 4.5, 0.0, 6.5, 4.5, 6.0)
                foot(7.5, 7.0)
                e(1.0 * s(), 10.0, 1.0 * s(), 4.8, 4.5, 4.5)
                e(1.5 * s(), 15.5, 1.5 * s(), 4.0, 4.0, 3.8)
                e(2.0 * s(), 20.0, 2.0 * s(), 3.2, 3.0, 3.0)
            }
            DryRockShape.ARCH -> {
                e(-9.0, 4.5, 0.0, 4.0, 4.5, 4.5)
                e(9.0, 4.5, 0.0, 4.0, 4.5, 4.5)
                e(-6.0, 9.5, 0.5 * s(), 4.5, 3.0, 4.0)
                e(6.0, 9.5, 0.5 * s(), 4.5, 3.0, 4.0)
                e(0.0, 11.5, 0.5 * s(), 6.0, 2.6, 4.0)
            }
            DryRockShape.BRANCH -> {
                e(0.0, 3.0, 0.0, 6.0, 3.0, 5.0)
                foot(6.5, 5.5)
                var x = 0.0; var y = 4.0; var z = 0.0
                val dx = s() * 0.8 + 1.0; val dz = s() * 0.6
                repeat(4) { x += 2.6 * dx + s(); y += 3.2; z += 2.0 * dz; e(x, y, z, 2.6, 2.4, 2.4) }
                e(-3.5, 6.0, 1.5 * s(), 2.4, 3.5, 2.4)
                e(-5.0, 9.5, 2.0 * s(), 2.0, 2.6, 2.0)
            }
            DryRockShape.RUBBLE -> {
                e(0.0, 3.0, 0.0, 4.5, 3.0, 4.0)
                e(2.0 * s(), 3.5, 1.5 * s(), 2.8, 2.6, 2.6)
            }
        }
        // rest the lowest point on y = 0 and centre the footprint
        val minY = b.minOf { it.center.y - it.radii.y }
        val cx = (b.minOf { it.center.x - it.radii.x } + b.maxOf { it.center.x + it.radii.x }) / 2
        val cz = (b.minOf { it.center.z - it.radii.z } + b.maxOf { it.center.z + it.radii.z }) / 2
        return RockPieceDef(name, b.map { Ellipsoid(Vec3(it.center.x - cx, it.center.y - minY, it.center.z - cz), it.radii) })
    }

    /**
     * How white a dry rock still is after [ageDays] in the tank (1 = fresh from the bag, 0 = fully live). A bacterial
     * and diatom film browns it over the first weeks; coralline spots follow from about a month and cover most of it
     * by half a year (cycling dry rock: BRS / Reef2Reef cure logs).
     */
    fun dryness(ageDays: Double): Double = if (ageDays <= 0.0) 1.0 else exp(-ageDays / CURE_DAYS)

    /** e-folding time of the cure (days). */
    const val CURE_DAYS = 60.0
}

/** A dry rock added with the aquascape builder, as the renderer needs it: its piece, shape, seed and [dryness]. */
class BuiltRock(val piece: Int, val shape: DryRockShape, val seed: Int, val dryness: Float)
