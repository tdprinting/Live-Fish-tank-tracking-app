package com.reeflive.core.ai

import com.reeflive.core.geom.Vec3
import com.reeflive.core.sim.Ev
import com.reeflive.core.sim.Evidence
import kotlin.math.max

/**
 * The dottyback's cave patrol (research 10 §8: "record the individual's cave, patrol route, retreats"; video
 * fish_indigo_dottyback "cave-mouth darts" [M], "shy yet aggressive"). It replaces a random dart between crevices with
 * a learned route:
 *
 * - the route is [ROUTE_STOPS] crevice mouths within its territory, learned once and kept in place memory
 *   (PlaceKind.PATROL), so it visits the same spots day after day;
 * - one round: from the cave mouth it darts, hugging the rock, to each stop in turn, pausing in the crevice mouth
 *   facing out for a few seconds (watching), then darts back to the cave mouth and sits in it before the next round;
 * - now and then (one round in [RESHUFFLE_ROUNDS]) one stop is swapped for a new crevice, so the route drifts slowly.
 *
 * Distances follow the sheet's territorial radius (15 cm; the care-guide 8 BL is not used, research realism_gaps §3.9).
 */
class CavePatrol(val b: SpeciesBrain, val cave: () -> Vec3, val caveSafe: () -> Vec3) : Behavior("cavedart", "Patrolling its caves") {
    private var stop = -1
    private var moving = false
    private var pauseUntil = 0.0
    private var wp = Vec3.ZERO
    private var face = Vec3.Z
    private var leftAt = 0.0
    /** Rounds completed (tests). */
    var rounds = 0; private set
    override val label: String get() = if (stop < 0) "Watching from its cave" else "Patrolling its caves"

    override fun score(c: Ctx) = 0.2 + 0.35 * c.activity

    /** The learned route, learning or repairing it as needed. */
    fun route(c: Ctx): List<Vec3> {
        val mem = b.mind.places
        val have = mem.of(PlaceKind.PATROL).filter { mem.strength(it, c.t) > PlaceMemory.MIN_STRENGTH }
        if (have.size >= ROUTE_STOPS) return have.take(ROUTE_STOPS).map { it.pos }
        val r = max(8.0, c.o.species.territorialRadius)
        for (i in have.size until ROUTE_STOPS) {
            val sp = c.rockSurfacePoint(cave() + c.rng.unitVector() * (0.7 * r), r) ?: continue
            mem.note(PlaceKind.PATROL, c.safe(sp.first + sp.second * (c.clearance + 0.3)), 1.0, c.t, mergeCm = 4.0)
        }
        return mem.of(PlaceKind.PATROL).take(ROUTE_STOPS).map { it.pos }
    }

    override fun enter(c: Ctx) { moving = false; pauseUntil = c.t }

    override fun update(c: Ctx, out: SteerIntent) {
        val o = c.o
        val stops = route(c)
        if (!moving && c.t > pauseUntil) {
            stop++
            if (stop >= stops.size) {
                // back to the cave mouth to end the round
                stop = -1
                rounds++
                if (rounds % RESHUFFLE_ROUNDS == 0) b.mind.places.of(PlaceKind.PATROL).minByOrNull { it.t }?.let { b.mind.places.items.remove(it) }
            }
            wp = if (stop < 0) caveSafe() else stops[stop]
            moving = true
            leftAt = c.t
        }
        if (moving) {
            out.seek(wp, o.burstSpeed * 0.45, arrive = 1.2, urgency = 0.6)
            // a stop it cannot get to (pinned by rock or glass) is dropped from the route and replaced later
            val stuck = c.t - leftAt > DART_GIVE_UP_S
            if (stuck && stop >= 0) b.mind.places.items.firstOrNull { it.kind == PlaceKind.PATROL && it.pos.distanceTo(wp) < 0.5 }?.let { b.mind.places.items.remove(it) }
            if (o.pos.distanceTo(wp) < 1.5 || stuck) {
                moving = false
                // refresh the stop's memory each visit, then watch from the crevice (or the cave) for a while
                if (stop >= 0 && !stuck) b.mind.places.note(PlaceKind.PATROL, wp, 1.0, c.t, mergeCm = 4.0)
                pauseUntil = c.t + if (stop < 0) c.rng.range(CAVE_PAUSE_MIN_S, CAVE_PAUSE_MAX_S) else c.rng.range(STOP_PAUSE_MIN_S, STOP_PAUSE_MAX_S)
                face = (wp - cave()).horizontal().normalized(o.heading)
            }
        } else {
            // pause in the crevice mouth facing out, watching, before the next dart
            out.target = wp; out.speed = o.cruiseSpeed * 0.2; out.hover = true; out.ampMul = 0.4; out.pectoral = 1.0
            out.faceDir = if (stop < 0) Vec3.Z else face
        }
    }

    companion object {
        /** Stops on the route and how often one is swapped (rounds) [L]. */
        @Ev(Evidence.DESIGN_ESTIMATE, "research 10 §8 'patrol route' (record, no numbers); [L]") const val ROUTE_STOPS = 4
        const val RESHUFFLE_ROUNDS = 6
        /** Pauses at a stop (as the old crevice dart, 2–8 s) and in the cave mouth (s) [L]. */
        const val STOP_PAUSE_MIN_S = 2.0
        const val STOP_PAUSE_MAX_S = 8.0
        const val CAVE_PAUSE_MIN_S = 5.0
        const val CAVE_PAUSE_MAX_S = 20.0
        /** A dart that has not arrived after this long is abandoned (s). */
        const val DART_GIVE_UP_S = 10.0
    }
}
