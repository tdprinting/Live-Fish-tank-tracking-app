package com.reeflive.core.sim

import com.reeflive.core.geom.Vec3
import kotlin.math.abs

/**
 * The bubble-tip anemone's tentacle pose for the renderer (polyp.vert anemone mode), written each tick by
 * [com.reeflive.core.ai.AnemoneBrain]. No gait of its own. Every entry's x stays in [0, 1) (it is interpolated as a phase):
 *  [0] bubble 0 stringy .. 1 bubbled | contraction 0 open .. 1 deflated | feeding curl 0..1 | curl direction (rad, model frame)
 *  [1] 0 | contact A point x, y, z (world cm)
 *  [2] bleach 0..1 | contact A radius (cm) | contact A strength | contact B strength (springy: can dip a little below 0
 *      as the tentacles swing back past rest)
 *  [3] 0 | disc axis x, y, z (world): the model's +Y; [basis] gives its +X and +Z
 *  [4] 0 | contact B point x, y, z (world cm)
 *  [5] 0 | contact B radius (cm) | 0 | 0
 *  [6] 0 | contact A drift x, y, z (cm/s: the fish's smoothed velocity, the water it drags through the crown)
 *  [7] 0 | contact B drift x, y, z
 * Contacts are fish pressing into the crown (a nestling clown pair, a blundering tang); the tentacles bend away from them.
 */
class AnemoneRig : LimbRig(8) {
    init {
        // still in the bag or just dropped: balled up until the brain takes over
        put(0, 0.3, 0.8, 0.0, 0.0); put(3, 0.0, 0.0, 1.0, 0.0)
    }

    override fun step(o: Organism, dt: Double, speed: Double) {}

    fun set(bubble: Double, contract: Double, curl: Double, curlAngle: Double, bleach: Double, up: Vec3) {
        put(0, bubble.coerceIn(0.0, 0.999), contract.coerceIn(0.0, 1.0), curl.coerceIn(0.0, 1.0), curlAngle)
        out[8] = bleach.coerceIn(0.0, 0.999).toFloat()
        put(3, 0.0, up.x, up.y, up.z)
    }

    /** Contact [i] (0 = A, 1 = B): point and drift in world cm and cm/s, radius cm, strength about 0..1. */
    fun contact(i: Int, at: Vec3, radius: Double, strength: Double, drift: Vec3) {
        put(if (i == 0) 1 else 4, 0.0, at.x, at.y, at.z)
        put(if (i == 0) 6 else 7, 0.0, drift.x, drift.y, drift.z)
        if (i == 0) { out[9] = radius.toFloat(); out[10] = strength.toFloat() }
        else { out[21] = radius.toFloat(); out[11] = strength.toFloat() }
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
