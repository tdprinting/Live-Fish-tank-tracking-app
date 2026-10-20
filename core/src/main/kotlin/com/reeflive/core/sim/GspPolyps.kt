package com.reeflive.core.sim

import com.reeflive.core.geom.Vec3
import com.reeflive.core.geom.smoothstep
import kotlin.math.max

/**
 * Green star polyp (*Briareum* / *Pachyclavularia*) polyp behaviour on top of the colony extension in [CoralSim]
 * (research 03 C1-C2 and the GSP rows of the compendium, docs/research/41_green_star_polyps.md):
 *
 * - Local retraction. Each polyp sits in its own calyx on a shared stolon mat; one that is touched snaps into the
 *   mat within a second and the polyps around it follow in a short spreading ring (a few cm), while the rest of a big
 *   mat stays open. A tap, a puff of water, a tang's nip, a snail, hermit or crab walking over the mat each leave a
 *   [poke]: a spot that closes, then reopens over tens of seconds once nothing touches it any more. A mat only shuts
 *   as a whole when the stimulus covers most of it (a small frag), so [poke] scales the colony-wide [Coral.disturb]
 *   by the share of the mat it reaches.
 * - Settling. A newly placed or moved colony keeps its polyps mostly in for its first hours (widely reported in the
 *   hobby: "may stay closed for a day or two after being moved"); a colony loaded from a save is already settled.
 *
 * Pokes are stored as offsets from the colony's position in world axes, so the renderer can carry them into the frame
 * of the rock the mat grows on (a rock that moved rotates them with it).
 */
object GspPolyps {
    /** Pokes kept per colony (oldest replaced first). */
    const val MAX_POKES = 8
    /** Floats per poke in [Coral.pokes] / [CoralPose.pokes]: dx, dy, dz, first touch (sim s), last touch (sim s), strength. */
    const val STRIDE = 6
    /** A touch within this distance of a live poke refreshes it instead of adding one (cm). */
    const val MERGE_CM = 2.5
    /** Spreading ring of retraction around a touch: speed (cm/s) and largest radius at full strength (cm). */
    @Ev(Evidence.DESIGN_ESTIMATE, "GSP retraction spreads a few cm from the touched polyp (hobby video)") const val WAVE_CM_S = 3.0
    const val WAVE_MAX_CM = 3.5
    /** Polyps stay in this long after the last touch, then reopen over [REOPEN_S] (sim s). */
    @Ev(Evidence.DESIGN_ESTIMATE, "GSP reopens within about a minute of a light touch") const val HOLD_S = 4.0
    const val REOPEN_S = 40.0
    /** A poke is forgotten this long after its last touch. */
    const val LIFE_S = HOLD_S + REOPEN_S + 2.0
    /** Organisms closer than this to the mat's surface (plus a share of their length) touch it (cm). */
    const val CONTACT_CM = 0.9
    /** How often walkers on the mat are checked (sim s). */
    const val CONTACT_DT = 1.0
    /** Settling after placement: extension at placement and the time to open fully (sim min). */
    @Ev(Evidence.DESIGN_ESTIMATE, "hobby: GSP may stay closed a day or two after being moved")
    const val SETTLE_OPEN = 0.3
    const val SETTLE_MIN = 18 * 60.0

    fun isGsp(c: Coral) = c.kind == CoralKind.GSP

    /** Mat radius along the rock (cm): the growth radius once growth ran, else the starting one. */
    fun matRadius(c: Coral): Double = if (c.radiusCm > 0) c.radiusCm else CoralGrowth.growth(c.kind).r0

    /** Extension factor while a newly placed colony settles in (1 once settled or loaded from a save). */
    fun settleFactor(c: Coral, simTimeSec: Double): Double {
        if (c.established || !isGsp(c)) return 1.0
        if (c.placedAtSec.isNaN()) c.placedAtSec = simTimeSec
        val min = (simTimeSec - c.placedAtSec) / 60.0
        return SETTLE_OPEN + (1 - SETTLE_OPEN) * smoothstep(0.0, SETTLE_MIN, min)
    }

    /**
     * A touch at world point [p] (on or near the mat) with [strength] 0..1. Returns false when [p] is not on this
     * colony's mat (farther than [tolerance] cm off its reachable rock, or beyond the mat's edge).
     */
    fun poke(c: Coral, p: Vec3, simTimeSec: Double, strength: Double = 1.0, tolerance: Double = 2.0): Boolean {
        if (!isGsp(c) || c.tissue < 0.05) return false
        val r = matRadius(c)
        val geo = c.patch?.distTo(p, tolerance) ?: p.distanceTo(c.pos)
        if (!geo.isFinite() || geo > r + 1.0) return false
        // the touch lands on the mat surface nearest to it (a puff passes over the mat, an animal stands on it)
        addPoke(c, c.patch?.nearestCell(p, tolerance) ?: p, simTimeSec, strength)
        // the colony as a whole closes only by the share of the mat the retraction ring reaches
        val share = (WAVE_MAX_CM * WAVE_MAX_CM / max(r * r, WAVE_MAX_CM * WAVE_MAX_CM)).coerceIn(0.0, 1.0)
        c.disturb = max(c.disturb, 0.6 * strength * share)
        return true
    }

    private fun addPoke(c: Coral, p: Vec3, t: Double, strength: Double) {
        val a = c.pokes
        val dx = p.x - c.pos.x; val dy = p.y - c.pos.y; val dz = p.z - c.pos.z
        var slot = -1; var slotT = Double.MAX_VALUE
        for (i in 0 until MAX_POKES) {
            val o = i * STRIDE
            val live = a[o + 5] > 0.0 && t - a[o + 4] <= LIFE_S
            if (!live) { if (slotT > Double.NEGATIVE_INFINITY) { slot = i; slotT = Double.NEGATIVE_INFINITY }; continue }
            val ex = a[o] - dx; val ey = a[o + 1] - dy; val ez = a[o + 2] - dz
            if (ex * ex + ey * ey + ez * ez < MERGE_CM * MERGE_CM) {
                // still touching: hold it shut; a poke that had already reopened starts a new ring
                if (t - a[o + 4] > HOLD_S) a[o + 3] = t
                a[o + 4] = t; a[o + 5] = max(a[o + 5], strength)
                return
            }
            if (a[o + 4] < slotT) { slot = i; slotT = a[o + 4] }
        }
        val o = slot * STRIDE
        a[o] = dx; a[o + 1] = dy; a[o + 2] = dz; a[o + 3] = t; a[o + 4] = t; a[o + 5] = strength
    }

    /** The live pokes for the snapshot (null when none). */
    fun pokesFor(c: Coral, simTimeSec: Double): FloatArray? {
        if (!isGsp(c)) return null
        val a = c.pokes
        var n = 0
        for (i in 0 until MAX_POKES) if (a[i * STRIDE + 5] > 0.0 && simTimeSec - a[i * STRIDE + 4] <= LIFE_S) n++
        if (n == 0) return null
        val out = FloatArray(n * STRIDE)
        var k = 0
        for (i in 0 until MAX_POKES) {
            val o = i * STRIDE
            if (a[o + 5] <= 0.0 || simTimeSec - a[o + 4] > LIFE_S) continue
            for (j in 0 until STRIDE) out[k * STRIDE + j] = a[o + j].toFloat()
            k++
        }
        return out
    }

    /**
     * Closed share 0..1 of the polyps at world point [p] from the colony's pokes at [simTimeSec] (the same rule
     * polyp.vert draws; tests and the HUD use it).
     */
    fun closedAt(c: Coral, p: Vec3, simTimeSec: Double): Double {
        val a = c.pokes
        var closed = 0.0
        for (i in 0 until MAX_POKES) {
            val o = i * STRIDE
            if (a[o + 5] <= 0.0) continue
            closed = max(closed, closure(
                p.distanceTo(Vec3(c.pos.x + a[o], c.pos.y + a[o + 1], c.pos.z + a[o + 2])),
                simTimeSec - a[o + 3], simTimeSec - a[o + 4], a[o + 5],
            ))
        }
        return closed
    }

    /** One poke's closure at distance [d] (cm), [sinceFirst] / [sinceLast] touch (s), [strength]. */
    fun closure(d: Double, sinceFirst: Double, sinceLast: Double, strength: Double): Double {
        if (sinceFirst < 0 || sinceLast > LIFE_S) return 0.0
        val ring = (0.8 + WAVE_CM_S * sinceFirst).coerceAtMost(WAVE_MAX_CM * (0.5 + 0.5 * strength))
        val space = 1 - smoothstep(ring - 0.8, ring, d)
        val time = 1 - smoothstep(HOLD_S, HOLD_S + REOPEN_S, sinceLast)
        return (strength * space * time).coerceIn(0.0, 1.0)
    }

    /** Walkers and resting animals on a mat close the polyps under them (checked every [CONTACT_DT] s). */
    fun contacts(organisms: List<Organism>, corals: List<Coral>, rock: RockData, simTimeSec: Double) {
        for (c in corals) {
            if (!isGsp(c) || c.tissue < 0.05) continue
            val r = matRadius(c) + 1.0
            for (o in organisms) {
                if (!o.alive || o.netted) continue
                val p = o.pos
                val reach = CONTACT_CM + 0.15 * o.collisionLength
                if (p.distanceTo(c.pos) > r + reach + 2.0) continue
                if (rock.sdf.dist(p) > reach) continue
                poke(c, p, simTimeSec, if (o.isFish) 0.8 else 1.0, tolerance = reach + SurfacePatch.CELL)
            }
        }
    }
}
