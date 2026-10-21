package com.reeflive.core.sim

import com.reeflive.core.geom.Vec3

/** How the tank is filtered, which decides the equipment drawn on it. */
enum class TankFilter {
    /** Hang-on-back filter with its intake and return pipe (Tee's tank). */
    HOB,
    /** All-in-one: the filter is a chamber behind the back glass, nothing hangs on the back. */
    AIO,
    /** Sump under the stand: only the return nozzle shows in the tank. */
    SUMP,
}

/**
 * A tank's interior box and look, and how anything built for Tee's 48 × 18 × 16 in tank ([TankDims]: the equipment,
 * the glass and rim model, flow jets, feeding spots) is placed in it.
 *
 * The placement is a nine-slice stretch per axis: within [EDGE_CM] of a glass (and below [FLOOR_Y_CM] or within
 * [TOP_CM] of the top) nothing changes size, it only moves with that glass; the stretch between scales. So a pane
 * keeps its thickness, a rim keeps its profile, a heater hanging from the back-right rim stays at the back-right rim,
 * and the sand and anything on it keep their height. Everything is in the world frame of [TankDims] (left glass x = 0,
 * glass bottom y = 0, back glass z = 0).
 */
class TankShape(
    val lengthCm: Double,
    val heightCm: Double,
    val depthCm: Double,
    /** No black frame round the top and bottom of the glass. */
    val rimless: Boolean = false,
    val filter: TankFilter = TankFilter.HOB,
    /** A hang-on protein skimmer on the back rim (an all-in-one or sump hides its own). */
    val skimmer: Boolean = true,
) {
    /** Open top: the water line sits [WATER_GAP_CM] under the top of the glass, as in Tee's tank. */
    val waterLevelCm: Double get() = heightCm - WATER_GAP_CM
    val sandTopCm: Double get() = TankDims.SAND_TOP_CM
    val isStock: Boolean get() = near(lengthCm, TankDims.LENGTH_CM) && near(heightCm, TankDims.HEIGHT_CM) && near(depthCm, TankDims.DEPTH_CM)

    private val dx = lengthCm - TankDims.LENGTH_CM
    private val dy = heightCm - TankDims.HEIGHT_CM
    private val dz = depthCm - TankDims.DEPTH_CM

    fun mapX(x: Double): Double = slice(x, EDGE_CM, TankDims.LENGTH_CM - EDGE_CM, dx)
    fun mapY(y: Double): Double = slice(y, FLOOR_Y_CM, TankDims.HEIGHT_CM - TOP_CM, dy)
    fun mapZ(z: Double): Double = slice(z, EDGE_CM, TankDims.DEPTH_CM - EDGE_CM, dz)
    fun map(p: Vec3): Vec3 = if (isStock) p else Vec3(mapX(p.x), mapY(p.y), mapZ(p.z))

    /** How much the stretch scales each axis at a point (1 outside the stretched middle): for bending normals. */
    fun scaleX(x: Double): Double = scale(x, EDGE_CM, TankDims.LENGTH_CM - EDGE_CM, dx)
    fun scaleY(y: Double): Double = scale(y, FLOOR_Y_CM, TankDims.HEIGHT_CM - TOP_CM, dy)
    fun scaleZ(z: Double): Double = scale(z, EDGE_CM, TankDims.DEPTH_CM - EDGE_CM, dz)

    /** [p] kept inside the water, [margin] cm from the glass, sand and surface. */
    fun clampToWater(p: Vec3, margin: Double): Vec3 = Vec3(
        p.x.coerceIn(margin, lengthCm - margin),
        p.y.coerceIn(sandTopCm + margin, waterLevelCm - margin),
        p.z.coerceIn(margin, depthCm - margin),
    )

    /** Same box and look (snapshots compare by value so a reopened tank does not refit the renderer). */
    override fun equals(other: Any?): Boolean = other is TankShape && other.lengthCm == lengthCm && other.heightCm == heightCm &&
        other.depthCm == depthCm && other.rimless == rimless && other.filter == filter && other.skimmer == skimmer
    override fun hashCode(): Int = ((lengthCm * 31 + heightCm) * 31 + depthCm).hashCode() * 31 + filter.ordinal + (if (rimless) 7 else 0) + (if (skimmer) 13 else 0)

    companion object {
        /** Glass bands that move without stretching (cm): the glass, its silicone, the rim profile. */
        const val EDGE_CM = 3.0
        /** Everything this low (the sand bed, rubble, the algae film line) keeps its height. */
        const val FLOOR_Y_CM = 10.0
        /** The top band (rim, water line, things hanging from the rim) moves up and down with the top of the glass. */
        const val TOP_CM = 5.0
        const val WATER_GAP_CM = TankDims.HEIGHT_CM - TankDims.WATER_LEVEL_CM

        /** Tee's own tank. */
        val STOCK = TankShape(TankDims.LENGTH_CM, TankDims.HEIGHT_CM, TankDims.DEPTH_CM)

        private fun near(a: Double, b: Double) = kotlin.math.abs(a - b) < 0.05

        private fun slice(v: Double, a0: Double, a1: Double, d: Double): Double =
            if (d == 0.0) v else v + d * ((v - a0) / (a1 - a0)).coerceIn(0.0, 1.0)

        private fun scale(v: Double, a0: Double, a1: Double, d: Double): Double =
            if (v <= a0 || v >= a1) 1.0 else (a1 - a0 + d) / (a1 - a0)
    }
}
