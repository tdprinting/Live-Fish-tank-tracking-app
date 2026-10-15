package com.reeflive.core.sim

/**
 * The brittle star's arms against the rock, sand and glass, judged by exactly BrittleMotionTest's arm criterion
 * (`armsLieOnRockAndSandWithoutPassingIntoThem`): a sample is inside when its skin is more than 0.1 cm in; under 2 % of
 * the samples may be, and none deeper than 1.5 cm. The arm rig (main's) owns how its arms lie; the collision tests
 * hold it to that one standard, the disc and the body to their own strict tolerance.
 */
class BrittleArmCheck {
    var samples = 0L; private set
    var inside = 0L; private set
    var worst = 0.0; private set
    var worstAt = ""; private set

    /** One arm sample [depth] cm into the surface (≤ 0 = clear). */
    fun add(depth: Double, where: () -> String) {
        samples++
        if (depth > INSIDE_CM) inside++
        if (depth > worst) { worst = depth; worstAt = where() }
    }

    val share: Double get() = inside.toDouble() / maxOf(1L, samples)

    /** Null when the arms meet the criterion, else what failed. */
    fun failure(): String? = when {
        share >= MAX_SHARE -> "brittle arm skin inside rock/sand/glass: ${"%.2f".format(100 * share)} % of $samples points"
        worst >= MAX_DEPTH_CM -> "brittle arm deepest clip ${"%.2f".format(worst)} cm: $worstAt"
        else -> null
    }

    override fun toString() = "brittle arms: ${"%.2f".format(100 * share)} % of $samples points inside, deepest ${"%.3f".format(worst)} cm"

    companion object {
        const val INSIDE_CM = 0.1
        const val MAX_SHARE = 0.02
        const val MAX_DEPTH_CM = 1.5
    }
}
