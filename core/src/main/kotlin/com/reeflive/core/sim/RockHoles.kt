package com.reeflive.core.sim

import com.reeflive.core.geom.Vec3
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.max
import kotlin.math.sqrt

/**
 * What a modelled opening in the rock is (ASSETS §3.6). [slot] kinds are flat: their [RockHole.radius] is half the
 * narrow opening (along [RockHole.up]) and [RockHole.width] half the wide one.
 */
enum class HoleKind {
    /** A round boring for one small fish (blennies). */
    HOLE,
    /** A larger round cave (the dottyback's), or an open cavern. */
    CAVE,
    CREVICE,
    /** A low ledge at the sand line with a burrow running on under it (diamond goby). */
    LEDGE,
    /** A roof pocket under a rock shelf (the shrimp hang from its roof). */
    OVERHANG,
    /** A tunnel along a path (the engineer goby's burrow). */
    BURROW,
    /** A procedural through-hole of the porosity pass, open at both ends. */
    THROUGH;

    val slot: Boolean get() = this == CREVICE || this == LEDGE || this == OVERHANG
}

/**
 * Who lives in which hole (owner bug 2026-10-14, Tee clip B: two blennies used the same hole). A single-occupant
 * opening ([RockHole.singleOccupant]: a boring, a small cave, a crevice) has at most one holder: an animal claims its
 * hole before it heads there and keeps the claim through its excursions; the claim goes when it moves house
 * ([release]) or dies or leaves the tank (a dead or removed holder no longer counts). A claimed hole is never entered by
 * anyone else: a newcomer takes a spare hole that fits, or shelters on the rock face and keeps looking. Open caverns,
 * ledges, overhangs, the goby burrow and through-holes are shared and are not claimed.
 *
 * Per simulation ([Simulation.holeClaims]); the rock's [HoleSet] is shared between simulations and holds no state.
 */
class HoleClaims(private val present: (Organism) -> Boolean = { it.alive }) {
    private val holders = HashMap<String, Organism>()

    /** The live holder of [hole], or null when it is free (or not single-occupant). */
    fun holder(hole: RockHole): Organism? {
        val h = holders[hole.name] ?: return null
        if (h.alive && present(h)) return h
        holders.remove(hole.name)
        return null
    }

    /** True when [o] may use [hole]: it is shared, free, or already [o]'s. */
    fun usableBy(hole: RockHole, o: Organism): Boolean = !hole.singleOccupant || holder(hole).let { it == null || it === o }

    /** Claim [hole] for [o]; false (and nothing changes) when another animal holds it. */
    fun claim(hole: RockHole, o: Organism): Boolean {
        if (!hole.singleOccupant) return true
        val h = holder(hole)
        if (h != null && h !== o) return false
        holders[hole.name] = o
        return true
    }

    /** Give up [hole] (moving house); only its holder can. */
    fun release(hole: RockHole, o: Organism) {
        if (holders[hole.name] === o) holders.remove(hole.name)
    }

    /** The holes [o] holds now. */
    fun heldBy(o: Organism): List<String> = holders.filterValues { it === o }.keys.sorted()
}

/**
 * One real opening in the rock, measured on the built mesh by `tools/blender/rockscape.py` (so it exists in the
 * baked SDF): the [mouth] on the rock face, the outward [axis], the clear half-opening [radius], the open [depth]
 * along [path] (mouth → far end), and the species it was cut for.
 */
data class RockHole(
    val name: String,
    val kind: HoleKind,
    val mouth: Vec3,
    /** Outward unit axis at the mouth (into the water). */
    val axis: Vec3,
    val radius: Double,
    val depth: Double,
    val path: List<Vec3> = listOf(mouth, mouth - axis * depth),
    val up: Vec3 = Vec3.Y,
    val width: Double = radius,
    val species: List<String> = emptyList(),
) {
    /** True if an animal of half-thickness [bodyRadius] (cm) fits inside without touching the walls. */
    fun fits(bodyRadius: Double): Boolean = radius >= bodyRadius + FIT_MARGIN_CM

    fun fits(o: Organism): Boolean = fits(bodyRadius(o))

    fun isFor(speciesId: String): Boolean = species.any { it.equals(speciesId, true) }

    /** One animal at a time: a blenny-size boring, a small cave (the dottyback's) or a crevice; see [HoleClaims]. */
    val singleOccupant: Boolean get() = kind == HoleKind.HOLE || kind == HoleKind.CREVICE || kind == HoleKind.CAVE && radius < SHARED_CAVE_R

    /** Inside the open volume proper (from the mouth inward, no padding): where a head counts as "in the hole". */
    fun holds(p: Vec3): Boolean = contains(p, 0.0) && ((p - mouth) dot axis) <= 0.0

    /** The point [d] cm in from the mouth along the path (d < 0: out in front of the mouth along the axis). */
    fun along(d: Double): Vec3 {
        if (d <= 0.0) return mouth + axis * (-d)
        var left = d
        for (i in 0 until path.size - 1) {
            val a = path[i]; val b = path[i + 1]
            val l = a.distanceTo(b)
            if (left <= l || i == path.size - 2) return if (l < 1e-9) a else a.lerp(b, (left / l).coerceAtMost(1.0))
            left -= l
        }
        return path.last()
    }

    /** Polyline along the hole from [from] cm in to [to] cm in (a body lying in it, head first). */
    fun polyline(from: Double, to: Double, step: Double = 0.5): List<Vec3> {
        val n = max(1, ((to - from) / step).toInt())
        return List(n + 1) { along(from + (to - from) * it / n) }
    }

    /**
     * Inside the open volume, padded by [pad] cm: from [pad] in front of the mouth to the far end, within the
     * opening (round, or the flat ellipse of a slot).
     */
    fun contains(p: Vec3, pad: Double = 0.0): Boolean {
        for (i in 0 until path.size - 1) {
            val a = path[i]; val b = path[i + 1]
            val ab = b - a
            val l2 = ab.lengthSq()
            if (l2 < 1e-9) continue
            var t = ((p - a) dot ab) / l2
            val lo = if (i == 0) -pad / sqrt(l2) else 0.0
            if (t < lo || t > 1.0) {
                if (i == path.size - 2 && t > 1.0 && t <= 1.0 + pad / sqrt(l2)) t = 1.0 else continue
            }
            val lat = p - (a + ab * t)
            if (kind.slot) {
                val dir = ab / sqrt(l2)
                val u = up.rejectFrom(dir).normalized(Vec3.Y)
                val s = (dir cross u).normalized(Vec3.X)
                val du = (lat dot u) / (radius + pad)
                val ds = (lat dot s) / (width + pad)
                if (du * du + ds * ds <= 1.0) return true
            } else if (lat.length() <= radius + pad) return true
        }
        return false
    }

    companion object {
        /** Clearance between body and wall for a hole to count as fitting (cm). */
        const val FIT_MARGIN_CM = 0.1
        /** A cave this wide or wider (half-opening, cm) is an open cavern several animals share. */
        const val SHARED_CAVE_R = 3.0

        /** Half the body's thickness (cm): half its depth (height) from the species sheet. */
        fun bodyRadius(o: Organism): Double = 0.5 * o.species.depthRatio * o.lengthCm
    }
}

/** The rock's holes and caves, with the lookups the brains use. */
class HoleSet(val all: List<RockHole>) {
    private val byName = all.associateBy { it.name }

    operator fun get(name: String): RockHole? = byName[name]

    /** [h] where it is now: the same-named hole of this set (a rock that moved carries its holes); null once its rock is gone. */
    fun live(h: RockHole?): RockHole? = if (h == null) null else byName[h.name]

    fun isEmpty() = all.isEmpty()

    /**
     * A home for [o]: a hole of one of [kinds] that fits it, preferring one cut for its species, then the nearest to
     * [near]; [exclude] holes are skipped (taken by another animal). Null when nothing fits: the animal then shelters
     * against the rock face or under an overhang instead of entering rock.
     */
    fun homeFor(
        o: Organism, kinds: Set<HoleKind>, near: Vec3? = null, exclude: Set<String> = emptySet(), claims: HoleClaims? = null,
    ): RockHole? {
        val br = RockHole.bodyRadius(o)
        val ok = all.filter {
            it.kind in kinds && it.fits(br) && it.name !in exclude && it.depth >= MIN_DEPTH_CM && (claims == null || claims.usableBy(it, o))
        }
        fun d(h: RockHole) = (near ?: h.mouth).distanceTo(h.mouth)
        return ok.filter { it.isFor(o.species.id) }.minByOrNull(::d)
            // an untagged hole before one cut for another species (the lawnmower's hole is not the tailspot's fallback)
            ?: ok.filter { it.species.isEmpty() }.minByOrNull { d(it) - (if (it.kind == HoleKind.HOLE) 0.0 else 5.0) }
            ?: ok.minByOrNull { d(it) - (if (it.kind == HoleKind.HOLE) 0.0 else 5.0) }
    }

    /**
     * [homeFor] among the holes nobody else holds, claimed for [o] at once (so two animals never pick the same one).
     * Null when no free hole fits.
     */
    fun claimHome(o: Organism, kinds: Set<HoleKind>, near: Vec3?, claims: HoleClaims, exclude: Set<String> = emptySet()): RockHole? =
        homeFor(o, kinds, near, exclude, claims)?.takeIf { claims.claim(it, o) }

    /** The holes of [kinds] that fit an animal of half-thickness [bodyRadius]. */
    fun fitting(bodyRadius: Double, kinds: Set<HoleKind> = HoleKind.entries.toSet()): List<RockHole> =
        all.filter { it.kind in kinds && it.fits(bodyRadius) }

    /** True when [p] lies in the open volume of a hole that fits an animal of half-thickness [bodyRadius]. */
    fun inFittingHole(p: Vec3, bodyRadius: Double, pad: Double = 0.3): Boolean =
        all.any { it.fits(bodyRadius) && it.contains(p, pad) }

    companion object {
        val EMPTY = HoleSet(emptyList())
        const val MIN_DEPTH_CM = 2.0
        /** Who an older anchor file's blenny holes were cut for (anchors without a "holes" array). */
        private val ANCHOR_SPECIES = mapOf(
            "HOLE_LAWNMOWER" to listOf("salarias_fasciatus"), "HOLE_BLENNY" to listOf("salarias_fasciatus"),
            "HOLE_TAILSPOT" to listOf("ecsenius_stigmatura"), "HOLE_BLENNY_2" to listOf("ecsenius_stigmatura"),
        )

        /** The `"holes"` array of anchors.json (written by rockscape.py); empty when absent. */
        fun parse(json: String): List<RockHole> {
            val root = Json.parseToJsonElement(json).jsonObject
            val arr = root["holes"] as? JsonArray ?: return emptyList()
            fun vec(e: Any?): Vec3? = (e as? JsonArray)?.let { a ->
                Vec3(a[0].jsonPrimitive.doubleOrNull ?: 0.0, a[1].jsonPrimitive.doubleOrNull ?: 0.0, a[2].jsonPrimitive.doubleOrNull ?: 0.0)
            }
            return arr.mapNotNull { el ->
                val o = el as? JsonObject ?: return@mapNotNull null
                val name = o["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val kind = runCatching { HoleKind.valueOf(o["kind"]?.jsonPrimitive?.content ?: "HOLE") }.getOrDefault(HoleKind.HOLE)
                val mouth = vec(o["mouth"]) ?: return@mapNotNull null
                val axis = (vec(o["axis"]) ?: Vec3.Z).normalized(Vec3.Z)
                val radius = o["radius"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
                val depth = o["depth"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                val path = (o["path"] as? JsonArray)?.mapNotNull { vec(it.jsonArray) }?.takeIf { it.size >= 2 } ?: listOf(mouth, mouth - axis * depth)
                val species = o["species"]?.jsonPrimitive?.content?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
                RockHole(name, kind, mouth, axis, radius, depth, path, vec(o["up"]) ?: Vec3.Y,
                    o["width"]?.jsonPrimitive?.doubleOrNull ?: radius, species)
            }
        }

        /**
         * Holes implied by an anchor set without a `"holes"` array (older anchors.json, the analytic scape): every HOLE
         * anchor with an axis (radius default 1.4 cm, depth 6 cm) and the den path as a burrow.
         */
        fun fromAnchors(anchors: AnchorSet): List<RockHole> = buildList {
            for (a in anchors.ofType("HOLE")) {
                if (a.type.isEmpty() || a.dir == null) continue
                val axis = a.dir.normalized(Vec3.Z)
                add(RockHole(a.name, HoleKind.HOLE, a.pos, axis, a.radius ?: 1.4, a.depth ?: 6.0, species = ANCHOR_SPECIES[a.name].orEmpty()))
            }
            anchors["den_path"]?.points?.takeIf { it.size >= 2 }?.let { pts ->
                add(RockHole("BURROW_GOBY", HoleKind.BURROW, pts.first(), (pts[0] - pts[1]).normalized(Vec3.Z), 2.8,
                    pts.zipWithNext { a, b -> a.distanceTo(b) }.sum(), pts))
            }
        }
    }
}
