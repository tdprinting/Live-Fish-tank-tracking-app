package com.reeflive.core.sim

/**
 * The flame scallop's pose for the renderer (fish.vert motion mode 7), written each tick by
 * [com.reeflive.core.ai.FileClamBrain]. Every entry's x stays in [0, 1) (it is interpolated as a phase):
 *  [0] tentacle wave phase (cycles) | valve gape 0 shut .. 1 widest | mantle and tentacles out 0 .. 1 | swim trail 0..1
 *  [1] clap phase (cycles) | tentacle writhe amplitude 0..1 | pallor (starving) 0..1 | 0
 * A dead clam gapes wide with everything pulled in (bivalves gape at death: the adductor lets go).
 */
class ClamRig : LimbRig(2) {
    init {
        // bagged or just dropped: shut tight
        put(0, 0.0, 0.05, 0.0, 0.0); put(1, 0.0, 0.0, 0.0, 0.0)
    }

    override fun step(o: Organism, dt: Double, speed: Double) {}

    fun set(wave: Double, gape: Double, extension: Double, trail: Double, clap: Double, writhe: Double, pallor: Double) {
        put(0, frac(wave), gape.coerceIn(0.0, 1.0), extension.coerceIn(0.0, 1.0), trail.coerceIn(0.0, 1.0))
        put(1, frac(clap), writhe.coerceIn(0.0, 1.0), pallor.coerceIn(0.0, 1.0), 0.0)
    }

    override fun beforePose(o: Organism) {
        if (!o.alive) { out[1] = 1f; out[2] = 0f; out[3] = 0f; out[5] = 0f }
    }
}
