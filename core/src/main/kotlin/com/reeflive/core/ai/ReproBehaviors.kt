package com.reeflive.core.ai

import com.reeflive.core.geom.Vec3
import com.reeflive.core.sim.Reproduction
import kotlin.math.PI
import kotlin.math.sin

// Visible breeding behaviours (coverage B-1). The timers and outcomes live in sim/Reproduction.kt; these only
// move the animals. Scores sit above hosting (0.25-0.55) and below flee (0.95).

/** Stand-off point in front of the nest site; null when no site is chosen. */
private fun nestStand(c: Ctx, off: Double): Vec3? {
    val r = c.sim.repro
    val s = r.site ?: r.clownClutch()?.pos ?: return null
    val n = r.site?.let { r.siteNormal } ?: r.clownClutch()?.normal ?: Vec3.Y
    return c.safe(s + n * off, 0.8)
}

/** Both partners pick at and mouth the rock beside the host before spawning. */
class CleanNestSite(val b: ClownBrain) : Behavior("cleansite", "Cleaning the nest site") {
    override fun score(c: Ctx) = if (c.sim.repro.clownPhase == Reproduction.Phase.CLEANING && c.sim.repro.site != null) 0.7 else 0.0
    override fun update(c: Ctx, out: SteerIntent) {
        if (c.sim.repro.clownPhase != Reproduction.Phase.CLEANING) { finished = true; return }
        val stand = nestStand(c, 0.3 * c.o.collisionLength + 0.8) ?: run { finished = true; return }
        out.seek(stand, c.o.cruiseSpeed * 0.8, arrive = 1.0)
        out.faceDir = (c.sim.repro.site!! - stand).normalized(Vec3.X)
        out.hover = true; out.pectoral = 0.8
        if (c.o.pos.distanceTo(stand) < 2.5) {
            out.pitchBias = 0.15 * sin(2 * PI * c.t / 1.8)
            if ((c.t * 0.55).toInt() != ((c.t - c.dt) * 0.55).toInt()) c.o.mouth = 1.0
        }
    }
}

/** The female presses her belly to the cleaned site and lays; the male follows and fertilises. */
class LayEggs(val b: ClownBrain) : Behavior("layeggs", "Laying eggs") {
    override fun score(c: Ctx) = if (c.sim.repro.clownPhase == Reproduction.Phase.LAYING && c.sim.repro.site != null) 0.72 else 0.0
    override fun update(c: Ctx, out: SteerIntent) {
        if (c.sim.repro.clownPhase != Reproduction.Phase.LAYING) { finished = true; return }
        val female = b.isFemale(c)
        val stand = nestStand(c, (if (female) 0.2 else 0.5) * c.o.collisionLength + 0.6) ?: run { finished = true; return }
        out.seek(stand + (if (female) Vec3.ZERO else Vec3(0.0, 0.0, 0.6 * c.L)), c.o.cruiseSpeed * 0.5, arrive = 0.8)
        out.faceDir = (c.sim.repro.site!! - stand).normalized(Vec3.X)
        out.hover = true; out.pectoral = 0.7
        out.ampMul = 0.4
        if (female) out.rollExtra = 0.2
    }
}

/** The male stays at the clutch, fanning it with his pectorals and mouthing dead eggs; leaves only when ravenous. */
class TendEggs(val b: ClownBrain) : Behavior("tendeggs", "Tending the eggs") {
    private var nextMouth = 0.0
    override fun score(c: Ctx): Double {
        val cl = c.sim.repro.clownClutch() ?: return 0.0
        if (cl.tenderId != c.o.id) return 0.0
        return if (c.o.hunger > 0.8) 0.5 else 0.86
    }
    override fun update(c: Ctx, out: SteerIntent) {
        val cl = c.sim.repro.clownClutch() ?: run { finished = true; return }
        val stand = c.safe(cl.pos + cl.normal * (0.35 * c.o.collisionLength + 1.0), 0.8)
        out.seek(stand, c.o.cruiseSpeed * 0.6, arrive = 1.0)
        out.faceDir = (cl.pos - stand).normalized(Vec3.X)
        out.hover = true
        out.pectoral = 1.0                  // fanning
        out.ampMul = 0.3
        out.pitchBias = 0.08 * sin(2 * PI * 1.5 * c.t)
        if (c.t > nextMouth) { nextMouth = c.t + c.rng.range(15.0, 45.0); c.o.mouth = 1.0 }
    }
}
