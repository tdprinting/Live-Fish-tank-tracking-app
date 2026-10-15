package com.reeflive.core.sim

import com.reeflive.core.geom.Vec3
import kotlin.math.abs

/**
 * The bubble-tip anemone's tentacle pose for the renderer (polyp.vert anemone mode), written each tick by
 * [com.reeflive.core.ai.AnemoneBrain]. No gait of its own. Every entry's x stays in [0, 1) (it is interpolated as a phase):
 *  [0] bubble 0 stringy .. 1 bubbled | contraction 0 open .. 1 deflated | feeding curl 0..1 | curl direction (rad, model frame)
 *  [1] fish push strength 0..1 | push point x, y, z (world cm)
 *  [2] bleach 0..1 | push radius (cm) | 0 | 0
 *  [3] 0 | disc axis x, y, z (world): the model's +Y; [basis] gives its +X and +Z
 */
class AnemoneRig : LimbRig(4) {
    init {
        // still in the bag or just dropped: balled up until the brain takes over
        put(0, 0.3, 0.8, 0.0, 0.0); put(3, 0.0, 0.0, 1.0, 0.0)
    }

    override fun step(o: Organism, dt: Double, speed: Double) {}

    fun set(bubble: Double, contract: Double, curl: Double, curlAngle: Double, push: Vec3, pushR: Double, pushK: Double, bleach: Double, up: Vec3) {
        put(0, bubble.coerceIn(0.0, 0.999), contract.coerceIn(0.0, 1.0), curl.coerceIn(0.0, 1.0), curlAngle)
        put(1, pushK.coerceIn(0.0, 0.999), push.x, push.y, push.z)
        put(2, bleach.coerceIn(0.0, 0.999), pushR, 0.0, 0.0)
        put(3, 0.0, up.x, up.y, up.z)
    }

    companion object {
        /** Model +X and +Z for disc axis [up] (+Y): the identity frame when [up] is straight up. Mirrored in TankRenderer. */
        fun basis(up: Vec3): Pair<Vec3, Vec3> {
            val u = up.normalized(Vec3.Y)
            val ref = if (abs(u.x) < 0.9) Vec3.X else Vec3.Z
            val z = (ref cross u).normalized(Vec3.Z)
            val x = u cross z
            return x to z
        }
    }
}
