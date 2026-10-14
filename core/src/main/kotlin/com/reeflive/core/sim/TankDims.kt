package com.reeflive.core.sim

/**
 * Interior size of Tee's display tank: the one source of truth for the sim, the renderer (app `Camera.TANK`,
 * `Material.WATER_LEVEL`) and, by convention, the asset scripts (`tools/blender/common.py` TANK / WATER_Y,
 * which must carry the same numbers).
 *
 * Owner's measurement (2026-10-13): 48 in long × 18 in front-to-back × 16 in tall, a 60 US gal tank.
 * World frame (cm): x along the length (left glass x = 0), y up (glass bottom y = 0), z front-to-back
 * (back glass z = 0, front glass z = [DEPTH_CM]).
 */
object TankDims {
    /** x: 48 in. */
    const val LENGTH_CM = 121.9
    /** y: 16 in, glass bottom to the top of the glass. */
    const val HEIGHT_CM = 40.6
    /** z: 18 in, back glass to front glass. */
    const val DEPTH_CM = 45.7
    /** Open top with a rim: the water line sits ~2.5 cm under the top of the glass. */
    const val WATER_LEVEL_CM = 38.1
    /** Sim sand plane (the rendered bed slopes 3.5 cm at the front glass to 6 cm at the back). */
    const val SAND_TOP_CM = 3.0

    /** Float copies for the GL side (kept literal so they stay `const` in Kotlin). */
    const val LENGTH_F = 121.9f
    const val HEIGHT_F = 40.6f
    const val DEPTH_F = 45.7f
    const val WATER_LEVEL_F = 38.1f
    const val SAND_TOP_F = 3.0f

    /** Nominal display volume Tee gives for the tank (the chemistry volume; gross interior is ≈ 226 L). */
    const val DISPLAY_GALLONS = 60.0
    const val DISPLAY_LITERS = 227.0

    /** Gross interior volume (L). */
    val GROSS_LITERS: Double get() = LENGTH_CM * HEIGHT_CM * DEPTH_CM / 1000.0
}
