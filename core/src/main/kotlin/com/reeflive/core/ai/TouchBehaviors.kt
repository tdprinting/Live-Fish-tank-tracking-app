package com.reeflive.core.ai

import com.reeflive.core.geom.Vec3
import com.reeflive.core.geom.smoothstep
import com.reeflive.core.sim.BrainKind
import com.reeflive.core.sim.TouchReaction
import kotlin.math.max
import kotlin.math.sin

/**
 * The answer to a gentle tap on the tank ([SpeciesBrain.onTouch], [com.reeflive.core.sim.TankTouch]), shared by every
 * brain. Idle (score 0, no rng, no utility noise) until a tap arrives, then for a few seconds:
 * INVESTIGATE swims to the glass by the spot and hovers facing it, mouth working as if for food; AVOID turns away and
 * drifts to cover; PEEK holds a blenny further out of its hole (or still on its perch) watching the spot; FREEZE holds a
 * shrimp or crab where it stands with its antennae drawn in; CHECK brings a sand-sifter or nassarius to the sand puff.
 * Startles (≥ 0.95) and committed behaviours still win.
 */
class TouchReact(val b: SpeciesBrain) : Behavior("touch", "Reacting to a tap") {
    override val noisy: Boolean get() = false
    private var kind = TouchReaction.NONE
    private var serial = -1
    private var at: Vec3 = Vec3.ZERO
    private var goal: Vec3 = Vec3.ZERO
    private var holdPos: Vec3 = Vec3.ZERO
    private var holdHeading: Vec3 = Vec3.X
    private var holdUp: Vec3 = Vec3.Y
    private var holdPosture = 0
    private var wasAttached = false

    override val label: String
        get() = when (kind) {
            TouchReaction.INVESTIGATE -> "Coming to see the tap"
            TouchReaction.AVOID -> "Turning away from the tap"
            TouchReaction.PEEK -> "Peeking out at the tap"
            TouchReaction.FREEZE -> "Freezing still"
            TouchReaction.CHECK -> "Checking the sand puff"
            else -> "Reacting to a tap"
        }

    override fun score(c: Ctx): Double {
        val k = b.touchKind
        if (k == TouchReaction.NONE || k == TouchReaction.DUCK || c.t < b.touchStartAt || c.t > b.touchUntil) return 0.0
        return when (k) {
            TouchReaction.FREEZE -> 0.9
            TouchReaction.AVOID -> 0.85
            TouchReaction.INVESTIGATE, TouchReaction.PEEK -> 0.8
            TouchReaction.CHECK -> 0.75
            else -> 0.0
        }
    }

    override fun enter(c: Ctx) {
        val o = c.o
        kind = b.touchKind; serial = b.touchSerial; at = b.touchFrom
        holdPos = o.pos; holdHeading = o.heading; holdUp = o.up; holdPosture = o.posture; wasAttached = o.attached
        goal = when (kind) {
            TouchReaction.INVESTIGATE -> {
                // hover just inside the glass at the spot (as GlassCurious): a few cm in from the nearest long pane
                val inward = Vec3(0.0, 0.0, if (at.z > c.rock.widthCm / 2) -1.0 else 1.0)
                c.safe(at + inward * (3.0 + 0.5 * c.L), 1.0)
            }
            TouchReaction.AVOID -> c.coverPoint(at, 25.0)
            TouchReaction.CHECK -> if (o.isFish) c.safe(Vec3(at.x, c.rock.sandTopCm + 1.0 + 0.3 * c.L, at.z), 0.8) else at.withY(c.rock.sandTopCm)
            else -> o.pos
        }
    }

    override fun update(c: Ctx, out: SteerIntent) {
        val o = c.o
        if (b.touchSerial != serial || c.t > b.touchUntil) {
            finished = true
            if (kind == TouchReaction.FREEZE || kind == TouchReaction.PEEK && wasAttached) hold(out)
            return
        }
        val toTap = (at - o.pos).normalized(o.heading)
        when (kind) {
            TouchReaction.INVESTIGATE -> {
                out.seek(goal, o.cruiseSpeed * 1.1, arrive = 2.0, urgency = 0.4)
                out.allowGlass = true; out.hover = true
                out.faceDir = toTap
                // expecting food: the mouth works as it nears the spot
                if (o.pos.distanceTo(goal) < 6.0) o.mouth = max(o.mouth, 0.35 + 0.25 * sin(c.t * 7.0))
            }
            TouchReaction.AVOID -> {
                if (elapsed < 0.6) { out.fleeFrom = at; out.urgency = 0.5 }
                else { out.seek(goal, o.cruiseSpeed * 0.8, arrive = 2.0, urgency = 0.3); out.hover = true }
                o.finClamp = max(o.finClamp, 0.4)
            }
            TouchReaction.PEEK -> {
                val r = b.peekRest(c)
                when {
                    r != null && o.pos.distanceTo(r.pos) < 8.0 -> out.rest(r, 3.0)
                    // perched on the rock: stay put and turn to look
                    wasAttached -> { out.place(holdPos, horizontalHeading(toTap, holdHeading), holdUp, 3.0); out.posture = holdPosture }
                    else -> { out.seek(holdPos, o.cruiseSpeed * 0.3, arrive = 1.5); out.hover = true; out.faceDir = toTap }
                }
                o.dorsalRaise = max(o.dorsalRaise, 0.5)
                o.eyeYaw = if ((o.heading.x * toTap.z - o.heading.z * toTap.x) > 0) -0.3 else 0.3
            }
            TouchReaction.FREEZE -> {
                hold(out)
                // shrimp draw their antennae in (the grooming pose's antenna stroke, held); hermits tuck their legs
                val k = smoothstep(0.0, 0.4, elapsed)
                if (o.brainKind == BrainKind.BANDED_SHRIMP || o.brainKind == BrainKind.CLEANER_SHRIMP) o.groom = max(o.groom, 0.8 * k)
                o.walkAmt = 0.0
            }
            TouchReaction.CHECK -> {
                if (o.isFish) {
                    out.seek(goal, o.cruiseSpeed * 0.9, arrive = 1.5, urgency = 0.3); out.hover = true
                    out.faceDir = toTap
                    if (o.pos.distanceTo(goal) < 3.0) { out.pitchBias = -0.35; o.mouth = max(o.mouth, 0.5 + 0.4 * sin(c.t * 5.0)) }
                } else {
                    out.crawl = true; out.target = goal; out.speed = o.cruiseSpeed; out.arriveRadius = 1.0
                    out.surfaceOffset = surfaceOffset(c)
                    o.extension = 1.0
                }
            }
            else -> finished = true
        }
    }

    /** Stay exactly where it was when the tap came (kinematic, same pose and posture). */
    private fun hold(out: SteerIntent) {
        out.place(holdPos, holdHeading, holdUp, 6.0)
        out.posture = holdPosture
    }
}
