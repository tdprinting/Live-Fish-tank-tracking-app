package com.reeflive.core.ai

import com.reeflive.core.geom.Vec3
import com.reeflive.core.sim.Ev
import com.reeflive.core.sim.Evidence
import kotlin.math.PI
import kotlin.math.sin

/**
 * A blenny goes home tail first (owner direction 2026-10-14; the familiar combtooth-blenny habit of sitting in a hole
 * with only the head out, research 10 §7 "head-out shelter inspection", video fish_tailspot_blenny "hole-peeking
 * idle"). The old [InHole] swam to the mouth and was placed head-out at once; this plays the whole sequence:
 *
 * 1. swim to a stand-off point in front of the hole mouth;
 * 2. pivot on the spot until it faces out of the hole ([TURN_RATE] rad/s, pectorals braking);
 * 3. scull backwards into the tunnel along its axis at [BACK_BL_S] body lengths per second, tail beating, body laid
 *    along the tunnel, until only the head is out;
 * 4. rest head-out (or fully in at night) as before.
 *
 * Leaving is head first, by whichever behaviour takes over. Speeds are DESIGN ESTIMATES [L].
 */
class TailFirstHole(
    b: SpeciesBrain, holeName: String, fallback: Vec3, label: String, scoreFn: (Ctx) -> Double, headOutFn: (Ctx) -> Double,
) : InHole(b, holeName, fallback, label, scoreFn, headOutFn) {
    private var phase = APPROACH
    private var backFrom = Vec3.ZERO
    private var backT = 0.0
    /** Times it has backed into its hole (tests). */
    var backIns = 0; private set
    /** The phase now, for tests and captions: 0 approach, 1 turn, 2 back in, 3 in. */
    val phaseNow: Int get() = phase
    override val label: String get() = when (phase) {
        APPROACH, TURN -> "Heading home to its hole"
        BACK -> "Backing into its hole"
        else -> super.label
    }

    override fun enter(c: Ctx) { phase = if (c.o.posture == 3) IN else APPROACH }

    override fun update(c: Ctx, out: SteerIntent) {
        val o = c.o
        val hole = c.rock.anchors[holeName]
        val r = holeRest(c, hole, fallback, headOutFn(c))
        val dir = r.heading
        val holePos = hole?.pos ?: fallback
        val mouth = c.safe(holePos + dir * (c.clearance + 1.0), c.clearance * 0.8)
        when (phase) {
            APPROACH -> {
                out.seek(mouth, o.burstSpeed * 0.5, arrive = 1.5, urgency = 0.6)
                if (o.pos.distanceTo(mouth) < 2.0) phase = TURN
            }
            TURN -> {
                // pivot in place, braking with the pectorals, until the head points out of the hole
                val h = Vec3.rotateToward(o.heading, dir, TURN_RATE * c.dt)
                out.place(mouth, h, Vec3.Y.rejectFrom(h).normalized(Vec3.Y), 8.0)
                out.pectoral = 1.0; out.ampMul = 0.6
                if (Vec3.angle(h, dir) < 0.15) { phase = BACK; backFrom = o.pos; backT = 0.0 }
            }
            BACK -> {
                // tail first along the tunnel axis, the body following the tunnel behind the head
                val dist = backFrom.distanceTo(r.pos).coerceAtLeast(0.5)
                backT += c.dt
                val u = (backT * BACK_BL_S * o.lengthCm / dist).coerceIn(0.0, 1.0)
                val head = backFrom.lerp(r.pos, u)
                out.place(head, dir, r.up, 10.0)
                out.splineOverride = listOf(head, head - dir * (0.5 * o.lengthCm), head - dir * o.lengthCm)
                out.ampMul = 1.2; out.tailMul = 1.3; out.pectoral = 0.8
                o.bend = 0.15 * sin(2 * PI * 3.0 * c.t)
                if (u > 0.3) out.posture = 3
                if (u >= 1.0) { phase = IN; backIns++ }
            }
            else -> {
                // moved off the rest spot (pushed, placed, or the hole changed): go round again, tail first
                if (o.pos.distanceTo(r.pos) > 3.0 + 0.3 * o.lengthCm) { phase = APPROACH; return }
                out.rest(r, 5.0)
                c.o.sleeping = c.p.night
                c.o.eyeYaw = 0.35 * sin(2 * PI * 0.5 * c.t + c.o.appendagePhase)
            }
        }
    }

    companion object {
        private const val APPROACH = 0
        private const val TURN = 1
        private const val BACK = 2
        private const val IN = 3
        /** Pivot rate at the mouth (rad/s) and backing speed (BL/s) [L]. */
        @Ev(Evidence.DESIGN_ESTIMATE, "owner direction; blenny holes, research 10 §7 sequence; rates [L]") const val TURN_RATE = 6.0
        const val BACK_BL_S = 0.8
    }
}
