package com.reeflive.core.sim

import com.reeflive.core.geom.AnalyticSdf
import com.reeflive.core.geom.Ellipsoid
import com.reeflive.core.geom.Sdf
import com.reeflive.core.geom.SdfGrid
import com.reeflive.core.geom.Tunnel
import com.reeflive.core.geom.Vec3
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Named point of interest in the rockscape (ASSETS §3.4 anchors.json). */
data class Anchor(
    val name: String,
    val type: String,
    val pos: Vec3,
    val dir: Vec3? = null,
    val normal: Vec3? = null,
    val radius: Double? = null,
    val depth: Double? = null,
    val points: List<Vec3>? = null,
)

class AnchorSet(anchors: Collection<Anchor>) {
    private val byName = LinkedHashMap<String, Anchor>().apply { anchors.forEach { put(it.name, it) } }
    val all: Collection<Anchor> get() = byName.values
    operator fun get(name: String): Anchor? = byName[name] ?: byName.values.firstOrNull { it.name.equals(name, true) }
    fun ofType(type: String): List<Anchor> = byName.values.filter { it.type.equals(type, true) }
    fun withPrefix(prefix: String): List<Anchor> = byName.values.filter { it.name.startsWith(prefix, true) }
    fun pos(name: String, fallback: Vec3): Vec3 = get(name)?.pos ?: fallback

    /** This set plus any anchors of [defaults] whose names are missing here (new species anchors). */
    fun withDefaults(defaults: AnchorSet): AnchorSet = AnchorSet(all + defaults.all.filter { this[it.name] == null })

    companion object {
        fun parse(json: String): AnchorSet {
            val root = Json.parseToJsonElement(json).jsonObject
            val arr = root["anchors"]?.jsonArray ?: JsonArray(emptyList())
            fun vec(o: JsonObject, k: String): Vec3? = (o[k] as? JsonArray)?.let { a ->
                Vec3(a[0].jsonPrimitive.doubleOrNull ?: 0.0, a[1].jsonPrimitive.doubleOrNull ?: 0.0, a[2].jsonPrimitive.doubleOrNull ?: 0.0)
            }
            return AnchorSet(arr.mapNotNull { el ->
                val o = el as? JsonObject ?: return@mapNotNull null
                val name = o["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val type = o["type"]?.jsonPrimitive?.content ?: ""
                val pts = (o["points"] as? JsonArray)?.map { p ->
                    val a = p.jsonArray
                    Vec3(a[0].jsonPrimitive.doubleOrNull ?: 0.0, a[1].jsonPrimitive.doubleOrNull ?: 0.0, a[2].jsonPrimitive.doubleOrNull ?: 0.0)
                }
                Anchor(
                    name, type, vec(o, "pos") ?: pts?.firstOrNull() ?: Vec3.ZERO, vec(o, "dir"), vec(o, "normal"),
                    o["radius"]?.jsonPrimitive?.doubleOrNull, o["depth"]?.jsonPrimitive?.doubleOrNull, pts,
                )
            })
        }
    }
}

/**
 * Rock SDF + anchors + the analytic tank box (sand plane and glass are not in the SDF).
 */
class RockData(
    val sdf: Sdf,
    val anchors: AnchorSet,
    val lengthCm: Double = TankDims.LENGTH_CM,
    /** Front-to-back depth (z), back glass z = 0. */
    val widthCm: Double = TankDims.DEPTH_CM,
    val sandTopCm: Double = TankDims.SAND_TOP_CM,
    val waterLevelCm: Double = TankDims.WATER_LEVEL_CM,
) {
    /** Distance to the nearest of rock, sand, glass and surface (for particle settling etc.). */
    fun envDist(p: Vec3): Double = minOf(
        sdf.dist(p), p.y - sandTopCm, waterLevelCm - p.y, p.x, lengthCm - p.x, p.z, widthCm - p.z,
    )

    fun inWater(p: Vec3, margin: Double = 0.0) =
        p.x in margin..(lengthCm - margin) && p.z in margin..(widthCm - margin) && p.y in (sandTopCm + margin)..(waterLevelCm - margin)

    fun clampToWater(p: Vec3, margin: Double): Vec3 = Vec3(
        p.x.coerceIn(margin, lengthCm - margin),
        p.y.coerceIn(sandTopCm + margin, waterLevelCm - margin),
        p.z.coerceIn(margin, widthCm - margin),
    )

    companion object {
        /** Analytic stand-in for an arbitrary set of spheres (tests). */
        fun ofSpheres(spheres: List<Pair<Vec3, Double>>, anchors: AnchorSet = DefaultScape.anchorsRaw()): RockData =
            RockData(AnalyticSdf(spheres.map { (c, r) -> Ellipsoid(c, Vec3(r, r, r)) }), anchors)

        /** Track B's baked files (`assets/sim/rock_sdf.bin`, `anchors.json`), with default anchors for missing names. */
        fun fromFiles(sdfBytes: ByteArray, anchorsJson: String): RockData =
            RockData(SdfGrid.parse(sdfBytes), AnchorSet.parse(anchorsJson).withDefaults(DefaultScape.anchorsRaw()))

        /** The analytic recreation of Tee's rockscape (ASSETS §3.1) with snapped anchors and carved holes. */
        fun defaultTank(): RockData = DefaultScape.build()
    }
}

/**
 * Analytic stand-in for the baked rock: the positive metaballs of `tools/blender/rockscape.py` `ROCKS` (cm; X right, Y up,
 * Z front; back glass z=0), for tests and as a fallback. Anchor defaults mirror the baked `anchors.json`; they fill in
 * names missing from it and are snapped to this surface like the bake does.
 */
object DefaultScape {
    private fun v(x: Double, y: Double, z: Double) = Vec3(x, y, z)
    private fun e(x: Double, y: Double, z: Double, rx: Double, ry: Double, rz: Double) = Ellipsoid(v(x, y, z), v(rx, ry, rz))

    val blobs: List<Ellipsoid> = listOf(
        // R0 (Z1)
        e(4.5, 5.2, 26.0, 4.0, 3.3, 6.5), e(6.0, 7.1, 27.3, 2.5, 1.7, 3.2),
        // R2 (Z1)
        e(10.0, 4.5, 26.0, 6.5, 3.3, 8.5), e(9.7, 9.1, 26.0, 5.6, 4.0, 7.3), e(9.3, 13.7, 26.0, 4.9, 4.0, 6.4), e(8.6, 17.7, 25.7, 4.2, 3.3, 5.5), e(7.0, 21.7, 24.7, 5.0, 2.6, 5.2),
        // R1 (Z2)
        e(22.0, 11.1, 10.4, 11.0, 8.6, 10.4), e(19.0, 20.3, 9.1, 7.0, 4.0, 7.8), e(26.0, 28.9, 10.4, 12.0, 7.3, 10.4), e(36.0, 31.6, 9.1, 10.0, 5.9, 9.1), e(31.0, 36.8, 9.8, 4.0, 1.3, 5.2),
        // R4 (Z3)
        e(44.0, 31.6, 9.1, 10.0, 4.6, 9.1), e(54.0, 30.9, 7.8, 10.0, 4.0, 7.8), e(61.0, 28.9, 6.5, 7.0, 4.0, 6.5), e(54.0, 27.6, 11.7, 4.5, 2.6, 3.9),
        // R3 (Z1b)
        e(42.0, 4.5, 28.6, 5.8, 3.3, 6.8), e(42.2, 9.1, 28.6, 4.8, 4.0, 6.0), e(42.5, 13.7, 28.6, 4.6, 3.3, 5.7), e(43.0, 18.4, 28.6, 5.0, 2.3, 5.2),
        // R5 (Z4)
        e(67.0, 7.8, 23.4, 9.0, 5.3, 10.4), e(70.0, 13.1, 19.5, 7.0, 4.0, 7.8), e(66.0, 14.4, 27.3, 6.0, 1.7, 6.5), e(63.5, 7.8, 32.0, 5.0, 1.8, 3.2), e(71.0, 4.6, 27.0, 3.5, 2.6, 3.8),
        // R6 (Z5)
        e(78.0, 14.4, 5.2, 4.0, 11.9, 5.2), e(84.0, 25.0, 11.0, 9.0, 2.2, 9.0), e(95.0, 26.0, 8.0, 8.0, 3.0, 8.0), e(95.0, 13.0, 7.8, 6.0, 10.5, 7.8),
        // R9 (Z6)
        e(106.0, 15.0, 8.0, 6.5, 9.0, 8.0), e(104.0, 25.0, 6.5, 5.0, 3.0, 6.0),
        // R7 (Z7)
        e(107.0, 5.5, 33.0, 10.0, 5.0, 9.0), e(105.0, 10.0, 31.0, 7.5, 4.5, 7.5),
        // R8 (Z8)
        e(116.0, 5.0, 34.0, 6.0, 3.3, 8.0), e(108.0, 17.0, 15.0, 3.5, 2.5, 3.5),
        // R10 (Z8)
        e(82.0, 5.5, 22.0, 7.0, 4.0, 6.0), e(81.5, 9.0, 21.0, 5.0, 2.5, 4.5),
    )

    /** Raw (un-snapped) anchors from ASSETS §3.4 plus Track C additions for the new species. */
    fun anchorsRaw(): AnchorSet {
        fun a(n: String, t: String, x: Double, y: Double, z: Double, dir: Vec3? = null, normal: Vec3? = null, radius: Double? = null, depth: Double? = null, pts: List<Vec3>? = null) =
            Anchor(n, t, v(x, y, z), dir, normal, radius, depth, pts)
        return AnchorSet(listOf(
            a("HOST_KENYA", "HOST", 82.0, 16.0, 21.0, radius = 10.0),
            // Owner: the clowns host a hairy green mushroom (Rhodactis) carpet, low on the front-right mound (R7), where
            // mushroom_hairy.glb is built. Positions below are the baked anchors.json values (resized 48 x 18 x 16 in tank).
            a("HOST_SHROOM", "HOST", 81.6, 13.2, 21.1, normal = v(-0.13, 0.95, -0.3), radius = 6.0),
            a("HOST_GSP_RIGHT", "HOST", 107.0, 14.0, 32.0, radius = 8.0),
            a("CAVE_EEL", "CAVE_EEL", 62.0, 3.8, 32.5, dir = v(0.55, 0.1, -0.83)),
            a("den_path", "PATH", 62.0, 3.8, 32.5, pts = listOf(v(62.0, 3.8, 32.5), v(66.0, 4.5, 26.0), v(71.0, 5.2, 19.5), v(74.5, 5.8, 14.0))),
            a("CAVE_SMALL", "CAVE", 56.0, 11.8, 7.8),
            a("CAVE_BIG", "CAVE", 36.0, 10.4, 7.8),
            a("HOLE_BLENNY", "HOLE", 10.0, 13.0, 32.3, dir = v(0.1, 0.1, 0.99), depth = 6.0),
            a("PERCH_1", "PERCH", 7.2, 24.9, 24.7, normal = v(0.3, 0.92, 0.24)),
            a("PERCH_2", "PERCH", 43.1, 20.2, 28.7, normal = v(-0.47, 0.83, -0.3)),
            a("PERCH_3", "PERCH", 103.8, 28.9, 6.8, normal = v(-0.34, 0.91, -0.24)),
            a("PERCH_4", "PERCH", 66.3, 15.5, 27.6, normal = v(-0.45, 0.84, -0.32)),
            a("PERCH_5", "PERCH", 47.9, 35.4, 12.8, normal = v(0.47, 0.56, 0.69)),
            a("STATION_CLEANER", "STATION", 38.0, 10.0, 14.0),
            a("SHRIMP_LEDGE", "LEDGE", 40.0, 26.2, 16.6, normal = v(-0.05, -0.97, 0.23)),
            a("SLEEP_TANG", "SLEEP", 38.0, 9.1, 5.2, dir = v(-0.7, 0.2, -0.7)),
            a("SURFACE_FEED", "FEED", 41.0, 37.5, 23.4),
            a("NORI_CLIP", "FEED", 2.0, 27.0, 40.0),
            a("HOB_OUTFLOW", "FLOW", 73.0, 37.4, 1.3),
            a("GRAZE_1", "GRAZE", 12.0, 14.2, 32.0), a("GRAZE_2", "GRAZE", 30.5, 33.1, 18.6),
            a("GRAZE_3", "GRAZE", 39.9, 14.3, 33.7), a("GRAZE_4", "GRAZE", 50.0, 35.6, 13.0),
            a("GRAZE_5", "GRAZE", 67.9, 11.2, 31.0), a("GRAZE_6", "GRAZE", 96.1, 29.2, 10.0),
            a("GRAZE_7", "GRAZE", 104.6, 27.7, 9.4), a("GRAZE_8", "GRAZE", 60.0, 6.5, 39.0),
            a("coral_kenya_main", "CORAL", 69.0, 17.2, 21.9, normal = v(-0.12, 0.94, -0.31)),
            a("coral_gsp_upper_left", "CORAL", 20.3, 27.7, 19.4, normal = v(0.31, 0.5, 0.81)),
            a("coral_gsp_lower_right", "CORAL", 107.0, 14.0, 32.0, normal = v(-0.26, 0.95, -0.18)),
            a("coral_zoa_front_center", "CORAL", 63.0, 7.2, 38.0, normal = v(0.0, 1.0, 0.0)),
            a("coral_clove_kenya_base", "CORAL", 71.3, 10.5, 31.5, normal = v(0.36, 0.7, 0.62)),
            a("coral_shroom_front_left", "CORAL", 50.0, 4.4, 38.0, normal = v(0.0, 1.0, 0.0)),
            a("coral_shroom_hairy", "CORAL", 81.6, 13.2, 21.1, normal = v(-0.13, 0.95, -0.3)),
            a("coral_macro_back_left", "CORAL", 35.9, 30.0, 20.9, normal = v(0.66, -0.3, 0.69)),
            a("coral_aiptasia_kenya_base", "CORAL", 63.9, 16.5, 21.4, normal = v(0.22, 0.8, -0.56)),
            // ---- Track C additions (new livestock, 04_more_species); Track B: please add to anchors.json ----
            a("coral_organpipe_kenya_base", "CORAL", 71.8, 15.5, 25.2, normal = v(-0.08, 0.84, 0.53)),
            a("HOLE_LAWNMOWER", "HOLE", 10.0, 13.0, 32.3, dir = v(0.1, 0.1, 0.99), depth = 7.0),
            a("STATION_SHRIMP", "STATION", 106.0, 28.0, 16.0, normal = v(0.28, 0.35, 0.9)),
            a("DUSTER_TUBE", "TUBE", 86.0, 7.0, 27.3, normal = v(0.0, 1.0, 0.0)),
            a("BRITTLE_HOME", "CAVE", 36.0, 5.5, 10.4),
            a("CAVE_DOTTYBACK", "CAVE", 106.0, 7.5, 13.0),
            // ---- corals from Tee's photos 2026-10-13 (tee_tank_photos.md); snapped by rockscape.py ANCHORS ----
            a("coral_toadstool_front", "CORAL", 60.4, 9.1, 29.0, normal = v(0.42, 0.9, 0.09)),
            a("coral_leather_pink_front", "CORAL", 58.1, 8.7, 31.8, normal = v(-0.43, 0.75, 0.5)),
            a("coral_finger_leather", "CORAL", 75.9, 26.8, 11.9, normal = v(0.13, 0.6, 0.79)),
            a("coral_elephant_ear", "CORAL", 41.0, 34.1, 16.5, normal = v(-0.02, 0.93, -0.36)),
        ))
    }

    private val SURFACE_TYPES = setOf("PERCH", "HOLE", "LEDGE", "GRAZE", "CORAL", "STATION")

    fun build(): RockData {
        val raw = anchorsRaw()
        val plain = AnalyticSdf(blobs)
        // Snap surface anchors (except sand-level ones) to the rock like the Track B bake does.
        val snapped = raw.all.map { a ->
            if (a.type in SURFACE_TYPES && a.pos.y > 4.5 && a.name != "STATION_CLEANER") {
                val p = plain.project(a.pos)
                if (p.distanceTo(a.pos) < 12) a.copy(pos = p, normal = a.normal ?: plain.gradient(p)) else a
            } else a
        }
        val set = AnchorSet(snapped)
        val carves = ArrayList<Tunnel>()
        set["den_path"]?.points?.let { carves += Tunnel(listOf(it.first() + Vec3(-1.0, 0.0, 3.0)) + it, 3.0) }
        for (h in set.ofType("HOLE")) {
            val dir = (h.dir ?: Vec3.Z).normalized()
            val depth = h.depth ?: 6.0
            carves += Tunnel(listOf(h.pos + dir * 1.5, h.pos - dir * depth), if (h.name == "HOLE_LAWNMOWER") 1.8 else 1.4)
        }
        return RockData(AnalyticSdf(blobs, carves), set)
    }
}
