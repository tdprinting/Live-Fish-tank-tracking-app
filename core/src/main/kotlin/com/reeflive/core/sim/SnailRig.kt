package com.reeflive.core.sim

/**
 * A snail's pose for the renderer (fish.vert motion mode 8, `snailPose`), written each tick by the trochus and conch
 * brains (research 43). The shell is rigid at the organism; the soft parts (foot, snout, tentacles, eyes) are posed
 * from these entries. Every entry's x stays in [0, 1) (FishRig interpolates it as a phase):
 *  [0] pedal-wave phase | body out of the shell 0 (withdrawn) .. 1 | snout rasping 0..1 | righting reach 0..1
 *  [1] tentacle wave phase | aperture x, y, z (model space: where withdrawn parts gather)
 *  [2] leap phase | vault 0..1 (the conch's shell swung over its operculum) | eyestalks out 0..1 | 0
 *  [3] 0 | operculum tip x, y, z (model space: the vault pivot)
 * The model points come from the GLB's Root extras (`aperture`, `operculumTip`), copied into [Shape].
 */
class SnailRig(private val shape: Shape) : LimbRig(4) {
    /** Model-space points of one snail model (glTF axes, unit length). */
    class Shape(val apX: Double, val apY: Double, val apZ: Double, val tipX: Double = 0.0, val tipY: Double = 0.0, val tipZ: Double = 0.0)

    private var pedal = 0.0
    private var wave = 0.0

    init { set(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0) }

    override fun step(o: Organism, dt: Double, speed: Double) {
        // pedal waves run along the sole faster as it glides faster (a few waves a second at full stride, est.)
        pedal = (pedal + dt * (0.15 + 2.5 * (speed / o.lengthCm.coerceAtLeast(0.5)).coerceIn(0.0, 1.0))) % 1.0
        wave = (wave + dt * 0.12) % 1.0
    }

    /** Brain's view of the body this tick. */
    fun set(out: Double, rasp: Double, reach: Double, leap: Double, vault: Double, eyes: Double, unused: Double) {
        put(0, pedal, out.coerceIn(0.0, 1.0), rasp.coerceIn(0.0, 1.0), reach.coerceIn(0.0, 1.0))
        put(1, wave, shape.apX, shape.apY, shape.apZ)
        put(2, frac(leap), vault.coerceIn(0.0, 1.0), eyes.coerceIn(0.0, 1.0), unused)
        put(3, 0.0, shape.tipX, shape.tipY, shape.tipZ)
    }

    override fun beforePose(o: Organism) {
        // a dead snail's foot hangs limp half out of the shell, eyes in (no rasp, no reach)
        if (!o.alive) { out[1] = 0.4f; out[2] = 0f; out[3] = 0f; out[9] = 0f; out[10] = 0f }
    }

    companion object {
        /** trochus.glb Root extras (tools/blender/cleanup_crew.py prints them): aperture (0.18, -0.398, 0). */
        val TROCHUS = Shape(0.18, -0.398, 0.0)
        /** fighting_conch.glb: aperture (0.24, -0.126, 0), operculum tip (0.586, -0.299, -0.08). */
        val CONCH = Shape(0.24, -0.126, 0.0, 0.586, -0.299, -0.08)
    }
}

/**
 * The tuxedo urchin's pose (fish.vert motion mode 9, `urchinPose`), written by [com.reeflive.core.ai.UrchinBrain]:
 *  [0] tube-foot wave phase | tube feet reaching 0 (drawn in) .. 1 | debris cover 0..1 (pieces shown of the model's 7) | spines bristling 0..1
 *  [1] spine sway phase | test centre x, y, z (model space: hidden debris shrinks into it)
 *  [2] 0 | bristle direction x, y, z (model space, toward the poke; zero = none)
 */
class UrchinRig : LimbRig(3) {
    private var feet = 0.0
    private var sway = 0.0

    init { set(0.0, 0.0, 0.0, 0.0, 0.0, 0.0) }

    override fun step(o: Organism, dt: Double, speed: Double) {
        // tube feet step in slow waves (est.); the spines sway gently with the water
        feet = (feet + dt * (0.08 + 0.4 * (speed / o.lengthCm.coerceAtLeast(0.5)).coerceIn(0.0, 1.0))) % 1.0
        sway = (sway + dt * 0.2) % 1.0
    }

    fun set(reach: Double, cover: Double, bristle: Double, dirX: Double, dirY: Double, dirZ: Double) {
        put(0, feet, reach.coerceIn(0.0, 1.0), cover.coerceIn(0.0, 1.0), bristle.coerceIn(0.0, 1.0))
        put(1, sway, 0.0, CENTRE_Y, 0.0)
        put(2, 0.0, dirX, dirY, dirZ)
    }

    override fun beforePose(o: Organism) {
        // a dead urchin's tube feet go limp and its spines droop (no bristle)
        if (!o.alive) { out[1] = 0.1f; out[3] = 0f }
    }

    companion object {
        /** tuxedo_urchin.glb test centre (Root extras `testCentre`). */
        const val CENTRE_Y = -0.0504
        /** Debris pieces in the model. */
        const val DEBRIS = 7
    }
}
