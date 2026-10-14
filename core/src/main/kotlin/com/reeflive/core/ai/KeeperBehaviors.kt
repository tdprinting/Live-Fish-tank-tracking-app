package com.reeflive.core.ai

import com.reeflive.core.geom.Vec3
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Food-anticipatory activity (research realism_gaps X3, REE02): in the hour before a learned feeding time the fish
 * leaves its routine and works back and forth under the place where the food usually goes in, a little higher in the
 * water and more active the closer the time gets, mouth working. Idle (score 0, no rng, no utility noise) when no
 * slot is due, so animals that have never been fed on a schedule behave exactly as before.
 */
class AnticipateFeed(val b: SpeciesBrain) : Behavior("anticipate", "Waiting for feeding time") {
    override val noisy: Boolean get() = false
    private var slotPlace: Vec3 = Vec3.ZERO
    private var ramp = 0.0

    override fun score(c: Ctx): Double {
        val o = c.o
        // the entrained clock, not hunger, drives it (REE02); a hungrier fish is only a little keener
        if (!o.alive || c.activity < 0.3) return 0.0
        val a = b.mind.anticipation(c.t) ?: return 0.0
        if (b.mind.mood.fear > 0.4) return 0.0
        slotPlace = a.first.place
        ramp = a.second
        return ((0.6 + 0.1 * ramp + 0.04 * o.hunger) * min(1.0, a.first.strength / 0.45)).coerceAtMost(MAX_SCORE)
    }

    override fun update(c: Ctx, out: SteerIntent) {
        val o = c.o
        val surfaceY = c.rock.waterLevelCm - 4.0 - 0.5 * c.L
        // higher in the water as the time nears; a slow figure-eight under the feeding spot (deterministic, no rng)
        val y = min(surfaceY, max(c.zoneY(0.45), slotPlace.y - 6.0 - (1 - ramp) * 10.0))
        val ph = c.t * (0.25 + 0.25 * ramp) + o.appendagePhase
        val goal = c.safe(Vec3(slotPlace.x + SWEEP_CM * sin(ph), y + 2.0 * sin(2 * ph), slotPlace.z + 0.4 * SWEEP_CM * sin(ph * 0.7)))
        val far = o.pos.distanceTo(goal) > 12
        out.seek(if (far) c.viaOpenWater(goal) else goal, o.cruiseSpeed * (0.8 + 0.5 * ramp), arrive = 2.0, urgency = 0.3)
        out.hover = !far
        if (!far) {
            out.faceDir = (Vec3(slotPlace.x, c.rock.waterLevelCm, slotPlace.z) - o.pos).normalized(o.heading)
            if (ramp > 0.6) o.mouth = max(o.mouth, 0.25 + 0.2 * sin(c.t * 6.0))
        }
    }

    companion object {
        /** Below any startle, a tap reaction and a chase; near the feeding time it outbids routine patrols (DESIGN ESTIMATE). */
        const val MAX_SCORE = 0.74
        const val SWEEP_CM = 8.0
    }
}

/**
 * Begging at the glass (research realism_gaps X3 keeper cue, TOM25 analogy; video fish_tailspot_blenny S2 "approaches
 * the glass when the observer is near"): a fish that has learned that the keeper means food answers a hand at the
 * tank (a gentle tap, a passing shadow, the scraper) by coming to the front glass where the hand was and hovering
 * there, face to the glass, for 20–60 s. A frightened fish does not come. Idle without a cue.
 */
class ComeToKeeper(val b: SpeciesBrain) : Behavior("beg", "Coming to the glass") {
    override val noisy: Boolean get() = false
    private var at: Vec3 = Vec3.ZERO
    private var seen = -1e9
    private var until = -1e9

    override fun score(c: Ctx): Double {
        val m = b.mind
        if (m.keeperCueAt != seen) {
            seen = m.keeperCueAt
            // a fresh cue: answered if the keeper has come to mean food and the fish is calm and awake
            val dur = 20.0 + 40.0 * c.o.curiosity
            until = if (m.keeperAssoc >= Mind.KEEPER_BEG && m.mood.fear < 0.3 && c.activity > 0.4 && !c.p.night) c.t + dur else -1e9
            at = m.keeperCuePos
        }
        if (c.t > until || c.t - seen < CUE_LAG_S) return 0.0
        if (m.mood.fear >= 0.3) { until = -1e9; return 0.0 }
        return (0.62 + 0.15 * m.keeperAssoc).coerceAtMost(0.76)
    }

    override fun update(c: Ctx, out: SteerIntent) {
        val o = c.o
        val inward = Vec3(0.0, 0.0, if (at.z > c.rock.widthCm / 2) -1.0 else 1.0)
        val spot = c.safe(at + inward * (3.0 + 0.5 * c.L) + Vec3(1.5 * sin(c.t * 0.8 + o.appendagePhase), 0.0, 0.0), 1.0)
        val far = o.pos.distanceTo(spot) > 12
        out.seek(if (far) c.viaOpenWater(spot) else spot, o.cruiseSpeed * 1.1, arrive = 2.0, urgency = 0.35)
        out.allowGlass = true; out.hover = !far
        if (!far) {
            out.faceDir = (at - o.pos).normalized(o.heading)
            o.mouth = max(o.mouth, 0.3 + 0.25 * sin(c.t * 7.0))
        }
        if (c.t > until) finished = true
    }

    companion object {
        /** It takes a moment to notice and turn (s) [L]. */
        const val CUE_LAG_S = 1.0
    }
}
