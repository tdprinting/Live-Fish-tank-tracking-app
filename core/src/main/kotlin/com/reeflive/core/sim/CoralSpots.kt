package com.reeflive.core.sim

import com.reeflive.core.geom.Vec3
import com.reeflive.core.geom.smoothstep
import kotlinx.serialization.Serializable
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * What a coral species wants from its spot, read from the sheet's `coral` block (the same JSON as the species screens'
 * CoralInfo; unknown keys ignored). Light is the fixtures' output at the colony (0..1, after depth and shade, the
 * scale [CoralSpots.lightAt] gives); flow is a hobby band; [stingCm] is how far its stings, sweeper tentacles or
 * chemicals reach past its edge (0 = the default for its kind, [CoralSpots.reachCm]); [ground] is ROCK, SAND or ANY.
 * Light is in the same 0..1 fixture scale as the tank light ([CoralSpots.lightAt] × the midday output).
 * A new species (a hammer with long sweepers, a sand-bed plate coral) only needs its sheet.
 */
@Serializable
data class CoralNeeds(
    val lightLo: Double = 0.3,
    val lightHi: Double = 0.8,
    val flow: String = "MODERATE",
    val stingCm: Double = 0.0,
    val ground: String = "ROCK",
)

/** One suggested spot for a new colony: where, how good (1..3 stars, 0..1 score) and why, in a few kid words. */
data class CoralSpot(
    val pos: Vec3,
    val normal: Vec3,
    val score: Double,
    val stars: Int,
    val reason: String,
    /** Light here (fraction of the fixtures' output) and water speed (cm/s), for tests and the reason. */
    val light: Double,
    val flowCmS: Double,
    val onSand: Boolean,
)

/**
 * Good spots for a new coral (My tank: after its bag is emptied the keeper picks one, [CoralPicks]). Candidates are
 * the tops of the rock (and the open sand for corals that live there) on a grid, each with room above it to grow and
 * clear of other colonies; each is scored on the species' light and flow band ([CoralNeeds]) and on keeping clear of
 * what stings or poisons its neighbours (both ways: a Kenya next to zoas hurts the zoas). The best few, spread out
 * across the tank, come back best first.
 *
 * Light at a spot ([lightAt]) falls off with depth, toward the side glass and under rock; a My tank colony sees the
 * tank light × [lightAt] where it sits (Simulation, chemistry tick), so a shade coral put up top really gets too much.
 * Water speed is ranked against the tank's other spots ([flowTarget]).
 */
object CoralSpots {
    /** Grid spacing for candidates, cm. */
    const val GRID_CM = 3.5
    /** Spots offered at once, and how far apart they must be (so they are different choices). */
    const val OFFER = 3
    const val APART_CM = 12.0
    /** Rock faces steeper than this cannot hold an upright colony. */
    const val MIN_UP = 0.7
    /** Room left under the water line to grow, cm. */
    const val HEADROOM_CM = 12.0

    /**
     * Where a flow label sits among this tank's own spots (0 = the calmest, 1 = the strongest): water speed is judged
     * against the rest of the tank, so it works the same with the analytic jets, the fluid solver and every tank size.
     */
    fun flowTarget(flow: String): Double = when (flow.uppercase()) {
        "LOW" -> 0.15
        "HIGH" -> 0.85
        else -> 0.5
    }

    /**
     * Light falloff with depth, per cm. Hobby PAR maps under LED fixtures drop about 3× from just under the surface to
     * the sand of a 40-50 cm deep tank (≈ 250-350 at the top rock, 80-120 on the sand; retail PAR surveys, research 05
     * §14), much faster than clear water absorbs: the beam spreads. Under a ledge it is [AnemoneModel.SHADE].
     */
    @Ev(Evidence.ANALOGY, "hobby PAR maps 60-75 gal under 2 LED pucks")
    const val DEPTH_K = 0.03
    /** Depth that still gets the fixtures' full output (cm), and the dimming toward the side glass (beyond the pucks). */
    const val TOP_CM = 6.0
    const val SIDE_DIM = 0.2
    const val SIDE_CM = 15.0

    /**
     * Light here as a fraction of the fixtures' full output (1 = just under the surface in the middle): depth falloff,
     * a little less toward the side glass, shade from rock overhead.
     */
    fun lightAt(rock: RockData, p: Vec3, n: Vec3): Double {
        val depth = (rock.waterLevelCm - p.y - TOP_CM).coerceAtLeast(0.0)
        val start = p + n * 1.5 + Vec3.Y * 1.0
        val shaded = rock.sdf.raycast(start, Vec3.Y, (rock.waterLevelCm - start.y).coerceAtLeast(0.1)) != null
        val side = 1 - SIDE_DIM * (1 - smoothstep(0.0, SIDE_CM, min(p.x, rock.lengthCm - p.x)))
        return exp(-DEPTH_K * depth) * side * (if (shaded) AnemoneModel.SHADE else 1.0)
    }

    /** The species' needs: its sheet, or a soft coral's middle-of-the-road defaults. */
    fun needs(sp: SimSpecies): CoralNeeds = sp.coral ?: CoralNeeds()

    /** How far [sp]'s stings or chemicals reach past its edge, cm: the sheet's [CoralNeeds.stingCm], else by kind. */
    fun reachCm(sp: SimSpecies): Double {
        val s = sp.coral?.stingCm ?: 0.0
        if (s > 0) return s
        return when (sp.coralKind) {
            CoralKind.KENYA -> CoralGrowth.KENYA_RANGE_CM
            CoralKind.LEATHER -> CoralGrowth.LEATHER_RANGE_CM
            CoralKind.ZOA, CoralKind.AIPTASIA -> CoralGrowth.STING_RANGE_CM + 2.0
            // GSP smothers by growing over: keep a mat's growing edge away
            CoralKind.GSP -> 8.0
            else -> 0.0
        }
    }

    /** Footprint radius of a new colony (cm): as wide as the spacing the stocking code keeps around it. */
    fun newExtent(sp: SimSpecies): Double = 0.5 * Stocking.spacingCm(sp.coralKind)

    /** The tank's midday light (fraction of full output) through the water's clarity; 0.7 when the lights are forced off. */
    fun dayLight(sim: Simulation): Double {
        val day = kotlin.math.floor(sim.simTimeSec / 86400.0) * 86400.0
        val i = sim.lights.intensity(day + LightSchedule.MIDDAY_MIN * 60.0)
        return (if (i < 0.1) 0.7 else i) * sim.water.clarity
    }

    /** The best [OFFER] spots for one colony of [sp], best first (empty when the tank has no room). */
    fun suggest(sim: Simulation, sp: SimSpecies, offer: Int = OFFER): List<CoralSpot> {
        val rock = sim.rock
        val need = needs(sp)
        val ground = need.ground.uppercase()
        val day = dayLight(sim)
        val myReach = reachCm(sp)
        val myR = newExtent(sp)
        val spacing = Stocking.spacingCm(sp.coralKind)
        // stingers in the water with it: other colonies and anemones (organisms), each with its edge and reach
        val others = ArrayList<Neighbor>()
        for (c in sim.corals) if (c.tissue > 0.01) {
            val same = c.species.id == sp.id || c.kind == sp.coralKind && c.kind != CoralKind.GENERIC
            others += Neighbor(c.pos, CoralGrowth.extent(c), if (same) 0.0 else reachCm(c.species), same)
        }
        for (o in sim.organisms) if (o.alive && o.brainKind == BrainKind.ANEMONE) {
            others += Neighbor(o.pos, 0.5 * o.lengthCm, 0.5 * o.lengthCm + 4.0, false)
        }
        val found = ArrayList<Candidate>()
        var x = 7.0
        while (x <= rock.lengthCm - 7.0) {
            var z = 5.0
            while (z <= rock.widthCm - 3.0) {
                candidate(sim, x, z, ground, myReach, myR, spacing, others)?.let { found += it }
                z += GRID_CM
            }
            x += GRID_CM
        }
        if (found.isEmpty()) return emptyList()
        // water speed as a rank among this tank's spots
        val speeds = DoubleArray(found.size) { found[it].flow }.also { it.sort() }
        val target = flowTarget(need.flow)
        val scored = found.map { c ->
            val rank = rankOf(speeds, c.flow)
            val light = day * c.lightFactor
            val lightFit = when {
                // outside the band it falls away from the band's edge value (0.75)
                light < need.lightLo -> 0.75 * smoothstep(need.lightLo * 0.45, need.lightLo, light)
                light > need.lightHi -> 0.75 * (1 - smoothstep(need.lightHi, need.lightHi + 0.35, light))
                // inside the band the middle is best (room either way when the lights or the water change)
                else -> { val half = 0.5 * (need.lightHi - need.lightLo).coerceAtLeast(0.05); val off = (light - 0.5 * (need.lightLo + need.lightHi)) / half; 1 - 0.25 * off * off }
            }
            val flowFit = 1 - 0.3 * smoothstep(0.0, 0.15, kotlin.math.abs(rank - target)) - 0.7 * smoothstep(0.15, 0.6, kotlin.math.abs(rank - target))
            // a little extra room counts too (it can grow without bumping into anyone)
            val room = if (c.nearest == Double.MAX_VALUE) 1.0 else smoothstep(0.0, 15.0, c.nearest)
            val s = (0.45 * lightFit + 0.35 * flowFit + 0.2 * room) * (1 - 0.8 * c.harm)
            val stars = when { s >= 0.78 -> 3; s >= 0.55 -> 2; else -> 1 }
            val crowded = others.isNotEmpty() && c.nearest < 25.0
            CoralSpot(c.p, c.n, s, stars, reason(light, rank, c.onSand, c.harm, crowded), light, c.flow, c.onSand)
        }.sortedByDescending { it.score }
        val out = ArrayList<CoralSpot>()
        for (s in scored) {
            if (out.size >= offer) break
            if (out.any { it.pos.distanceTo(s.pos) < APART_CM }) continue
            out += s
        }
        return out
    }

    /** Share of [sorted] below [v] (0..1). */
    private fun rankOf(sorted: DoubleArray, v: Double): Double {
        var lo = 0; var hi = sorted.size
        while (lo < hi) { val m = (lo + hi) ushr 1; if (sorted[m] < v) lo = m + 1 else hi = m }
        return if (sorted.size <= 1) 0.5 else lo.toDouble() / (sorted.size - 1)
    }

    private class Neighbor(val pos: Vec3, val extent: Double, val reach: Double, val same: Boolean)
    private class Candidate(val p: Vec3, val n: Vec3, val onSand: Boolean, val lightFactor: Double, val flow: Double, val harm: Double, val nearest: Double)

    /** A spot under (x, z) a new colony can sit on, with its light, water speed and sting danger; null when it cannot. */
    private fun candidate(
        sim: Simulation, x: Double, z: Double, ground: String, myReach: Double, myR: Double, spacing: Double, others: List<Neighbor>,
    ): Candidate? {
        val rock = sim.rock
        val (p, n) = Stocking.surfaceSpot(rock, x, z) ?: return null
        val onSand = p.y <= rock.sandTopCm + 0.3
        if (onSand && ground == "ROCK" || !onSand && ground == "SAND") return null
        if (n.y < MIN_UP) return null
        if (p.y > rock.waterLevelCm - HEADROOM_CM) return null
        // room above to grow: no rock leaning over it (the colony is drawn upright, it must not poke into a ledge)
        for (h in ROOM_HEIGHTS) if (rock.sdf.dist(Vec3(p.x, p.y + h, p.z)) < min(0.5 * spacing, 0.8 * h)) return null
        // not on top of another colony, and the danger from anything that stings within reach (both ways)
        var harm = 0.0
        var nearest = Double.MAX_VALUE
        for (o in others) {
            val d = o.pos.distanceTo(p)
            if (d < spacing) return null
            val gap = d - o.extent - myR
            if (gap < 0.5) return null
            nearest = min(nearest, gap)
            val reach = max(o.reach, if (o.same) 0.0 else myReach)
            if (reach > 0) harm = max(harm, 1 - (gap / reach).coerceIn(0.0, 1.0))
        }
        // the water the polyps sit in, a few cm off the rock (right at the surface the rock's own shelter hides it)
        val flow = sim.flow.velocityFast(p + Vec3.Y * POLYP_CM).length()
        return Candidate(p, n, onSand, lightAt(rock, p, n), flow, harm, nearest)
    }

    /** Height of the colony's polyps above its base where its water speed is read, cm. */
    const val POLYP_CM = 4.0

    private val ROOM_HEIGHTS = doubleArrayOf(2.0, 5.0, 9.0)

    /**
     * Plain words for a spot: "Bright light, gentle flow" (+ "safe from stings" when stingers are near but clear).
     * [flowRank] is the spot's water speed among the tank's spots (0 calmest, 1 strongest).
     */
    fun reason(light: Double, flowRank: Double, onSand: Boolean, harm: Double, crowded: Boolean): String {
        val l = when { light < 0.3 -> "shady"; light < 0.5 -> "soft light"; else -> "bright light" }
        val f = when { flowRank < 0.33 -> "calm water"; flowRank < 0.7 -> "gentle flow"; else -> "strong flow" }
        val base = (if (onSand) "On the sand, $l" else l.replaceFirstChar { it.uppercase() }) + ", $f"
        return when {
            harm > 0.3 -> "$base, a bit close to a stinger"
            crowded -> "$base, safe from stings"
            else -> base
        }
    }
}
