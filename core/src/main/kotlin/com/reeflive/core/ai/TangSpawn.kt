package com.reeflive.core.ai

import com.reeflive.core.geom.Vec3
import com.reeflive.core.sim.Reproduction
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Blue tang spawning (research 41, [Reproduction.tangBreeding] keeps the clock): at dusk around the full moon the
 * pair courts in open water, the male chasing close behind the female in wide loops while both pale from deep to pale
 * blue ([S]); then the two race up together to just under the surface, let their eggs and milt go in a cloud at the
 * top, and dart back down (the upward spawning rush of acanthurids, [A]).
 */
class TangSpawn(val b: TangBrain) : Behavior("tangspawn", "Courting") {
    private var loopAt = Vec3.ZERO
    private var angle = 0.0
    private var rushTop: Vec3? = null
    private var back = false

    override val label: String get() = if (phase() == Reproduction.TangPhase.RUSH) "Spawning rush" else "Courting"

    private fun phase() = b.c.sim.repro.tangPhase
    private fun female(c: Ctx) = c.sim.repro.tangFemale === c.o
    private fun inPair(c: Ctx) = c.sim.repro.let { it.tangFemale === c.o || it.tangMale === c.o }

    override fun score(c: Ctx): Double = if (phase() != Reproduction.TangPhase.IDLE && inPair(c)) 0.95 else 0.0

    // 0 or urgent only, so no whim noise (and no extra draws on the shared rng)
    override val noisy: Boolean get() = false

    override val committed: Boolean get() = phase() == Reproduction.TangPhase.RUSH

    override fun enter(c: Ctx) {
        val r = c.rock
        loopAt = c.safe(Vec3(r.lengthCm * c.rng.range(0.35, 0.65), c.zoneY(0.6), r.widthCm * 0.65))
        angle = c.rng.range(0.0, 6.28)
        rushTop = null; back = false
    }

    override fun update(c: Ctx, out: SteerIntent) {
        val o = c.o
        val repro = c.sim.repro
        if (!inPair(c) || phase() == Reproduction.TangPhase.IDLE) { finished = true; return }
        // both pale from deep blue as they court and spawn [S]
        o.desat = max(o.desat, PALE)
        if (phase() == Reproduction.TangPhase.RUSH) { rush(c, out); return }
        val f = repro.tangFemale ?: return
        if (female(c)) {
            // she swims wide, slow loops in open water above the rock
            angle += c.dt * LOOP_RAD_S
            val p = c.safe(loopAt + Vec3(cos(angle) * LOOP_CM, sin(angle * 2) * 3.0, sin(angle) * LOOP_CM * 0.45))
            out.seek(c.viaOpenWater(p), o.cruiseSpeed * 0.9, arrive = 2.0)
            out.tailMul = 1.2
        } else {
            // he chases close behind and beside her, nose to her flank
            val side = (f.heading cross Vec3.Y).normalized(Vec3.Z)
            val p = c.safe(f.pos - f.heading.normalized(Vec3.X) * (0.6 * f.lengthCm) + side * (0.35 * f.lengthCm))
            out.seek(p, o.burstSpeed * 0.7, arrive = 1.0, urgency = 0.6)
            out.tailMul = 1.4
        }
    }

    private fun rush(c: Ctx, out: SteerIntent) {
        val o = c.o
        val f = c.sim.repro.tangFemale ?: return
        val top = rushTop ?: c.safe(Vec3(f.pos.x, c.rock.waterLevelCm - 1.0, f.pos.z)).also { rushTop = it }
        if (!back) {
            // straight up side by side, fast
            out.seek(top + (if (female(c)) Vec3.ZERO else Vec3(0.0, -1.5, 0.4 * o.lengthCm)), o.burstSpeed, arrive = 1.0, urgency = 0.9)
            out.tailMul = 1.7
            if (o.pos.y > top.y - 2.0) { back = true; rushes++ }
        } else {
            out.seek(c.safe(Vec3(top.x, c.zoneY(0.5), top.z)), o.burstSpeed * 0.8, arrive = 2.0, urgency = 0.7)
            out.tailMul = 1.4
        }
    }

    /** Rushes to the top so far (tests). */
    var rushes = 0; private set

    companion object {
        const val PALE = 0.4
        const val LOOP_CM = 14.0
        const val LOOP_RAD_S = 0.35
    }
}
