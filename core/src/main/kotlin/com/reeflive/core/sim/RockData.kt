package com.reeflive.core.sim

import com.reeflive.core.geom.AnalyticSdf
import com.reeflive.core.geom.Ellipsoid
import com.reeflive.core.geom.HeightField
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
import kotlin.math.min

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
    val lengthCm: Double = 121.9,
    val widthCm: Double = 33.0,
    val sandTopCm: Double = 3.0,
    val waterLevelCm: Double = 56.0,
) {
    /**
     * The drawn sand surface without the animals' digging (tank.glb's sand mesh, rasterized by the app; see
     * [HeightField.fromGlb]). Null = the sand bed's linear slope. The Simulation hands it to its [SandBed].
     */
    var sandBase: HeightField? = null

    /**
     * Sand surface height at (x, z) the animals stand on: set by the Simulation to its dynamic sand bed
     * ([SandBed.surfaceY]); null = the flat [sandTopCm] (tests, tools).
     */
    @Volatile var floorAt: ((Double, Double) -> Double)? = null

    /**
     * A copy sharing the rock, anchors and modelled sand ([sandBase]) with its own [floorAt]: each Simulation stands its
     * animals on its own sand bed (a time-lapse fork must not read its parent's sand). Keep in step with the constructor.
     */
    fun forSimulation(): RockData = RockData(sdf, anchors, lengthCm, widthCm, sandTopCm, waterLevelCm).also { it.sandBase = sandBase }

    /** Height of the sand floor (cm) at (x, z). */
    fun floorY(x: Double, z: Double): Double = floorAt?.invoke(x, z) ?: sandTopCm

    /** Distance to the nearest of rock, sand, glass and surface (for particle settling etc.). */
    fun envDist(p: Vec3): Double = minOf(
        sdf.dist(p), p.y - floorY(p.x, p.z), waterLevelCm - p.y, p.x, lengthCm - p.x, p.z, widthCm - p.z,
    )

    fun inWater(p: Vec3, margin: Double = 0.0) =
        p.x in margin..(lengthCm - margin) && p.z in margin..(widthCm - margin) && p.y in (floorY(p.x, p.z) + margin)..(waterLevelCm - margin)

    fun clampToWater(p: Vec3, margin: Double): Vec3 {
        val x = p.x.coerceIn(margin, lengthCm - margin)
        val z = p.z.coerceIn(margin, widthCm - margin)
        val lo = floorY(x, z) + margin
        return Vec3(x, p.y.coerceIn(min(lo, waterLevelCm - margin), waterLevelCm - margin), z)
    }

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
 * Analytic rockscape matching the ASSETS §3.1 layout table (cm; X right, Y up, Z front; back glass z=0).
 * Used until `rock_sdf.bin` from Track B lands; anchors are snapped to this surface like the bake does.
 */
object DefaultScape {
    private fun v(x: Double, y: Double, z: Double) = Vec3(x, y, z)
    private fun e(x: Double, y: Double, z: Double, rx: Double, ry: Double, rz: Double) = Ellipsoid(v(x, y, z), v(rx, ry, rz))

    val blobs: List<Ellipsoid> = listOf(
        // R1 left back wall stack (x 11–56, top ~55, z 0–15) with the big cavern under its right overhang
        e(22.0, 20.0, 6.0, 12.0, 18.0, 8.0), e(30.0, 42.0, 6.0, 18.0, 13.0, 8.0), e(46.0, 46.0, 5.0, 10.0, 8.0, 6.0), e(16.0, 50.0, 5.0, 7.0, 6.0, 5.0),
        // R2 left pillar (x 12–26, top 33, z 15–25)
        e(19.0, 15.0, 20.0, 6.0, 16.0, 5.0), e(19.0, 28.0, 20.0, 4.5, 5.5, 4.0),
        // R3 center pillar (x 44–55, top 29)
        e(49.5, 13.5, 21.0, 5.0, 15.5, 4.5),
        // R4 center arch / back shelf (opening x 57–68, y 5–24)
        e(63.0, 29.0, 5.0, 10.0, 5.0, 6.0), e(56.0, 14.0, 4.0, 3.5, 12.0, 4.0),
        // R5 Kenya rock + front lip + den overhang + zoa rubble
        e(72.0, 22.0, 15.0, 7.5, 16.0, 7.0), e(75.0, 9.0, 13.0, 11.0, 8.0, 10.0), e(70.0, 34.5, 20.0, 6.0, 3.5, 5.0),
        e(68.0, 8.5, 24.0, 6.0, 2.5, 4.0), e(71.0, 3.5, 28.0, 3.0, 2.0, 2.5),
        // R6 right arch (legs + top) and the shrimp shelf
        e(81.0, 22.0, 6.0, 5.0, 22.0, 6.0), e(100.0, 22.0, 6.0, 5.0, 22.0, 6.0), e(90.0, 46.0, 6.0, 14.0, 8.0, 6.0), e(89.0, 39.0, 12.0, 9.0, 3.5, 6.0),
        // R7 GSP mound (front right)
        e(104.0, 6.0, 19.0, 10.0, 18.0, 8.0),
        // R8 far-right rubble
        e(116.0, 8.0, 23.0, 4.0, 13.0, 7.0),
    )

    /** Raw (un-snapped) anchors from ASSETS §3.4 plus Track C additions for the new species. */
    fun anchorsRaw(): AnchorSet {
        fun a(n: String, t: String, x: Double, y: Double, z: Double, dir: Vec3? = null, normal: Vec3? = null, radius: Double? = null, depth: Double? = null, pts: List<Vec3>? = null) =
            Anchor(n, t, v(x, y, z), dir, normal, radius, depth, pts)
        return AnchorSet(listOf(
            a("HOST_KENYA", "HOST", 72.0, 40.0, 15.0, radius = 10.0),
            // Owner: the clowns host a hairy green mushroom (Rhodactis) cluster. It sits where the mushroom_hairy.glb
            // model is built: the centroid of its disc tufts, on a ledge of the arch's left leg beside HOST_ARCH_CLOWNS.
            a("HOST_SHROOM", "HOST", 82.3, 20.3, 15.2, normal = v(0.7, 0.7, -0.18), radius = 6.0),
            a("HOST_GSP_RIGHT", "HOST", 104.0, 14.0, 22.0, radius = 8.0),
            a("CAVE_EEL", "CAVE_EEL", 69.0, 5.0, 23.0, dir = v(0.5, 0.1, -0.85)),
            a("den_path", "PATH", 69.0, 5.0, 23.0, pts = listOf(v(69.0, 5.0, 23.0), v(73.0, 6.0, 19.0), v(77.0, 7.0, 15.0), v(80.0, 9.0, 11.0), v(83.0, 10.0, 8.0), v(86.0, 10.0, 5.0), v(88.0, 9.0, 3.0), v(90.0, 8.0, 2.0))),
            a("CAVE_SMALL", "CAVE", 62.0, 12.0, 8.0),
            a("CAVE_BIG", "CAVE", 42.0, 18.0, 6.0),
            a("HOLE_BLENNY", "HOLE", 25.0, 18.0, 24.0, dir = v(0.6, 0.2, 0.78), depth = 6.0),
            a("PERCH_1", "PERCH", 19.0, 33.0, 20.0, normal = v(0.0, 1.0, 0.0)),
            a("PERCH_2", "PERCH", 49.0, 29.0, 21.0, normal = v(0.0, 1.0, 0.0)),
            a("PERCH_3", "PERCH", 92.0, 54.0, 8.0, normal = v(0.2, 1.0, 0.3)),
            a("PERCH_4", "PERCH", 70.0, 38.0, 24.0, normal = v(0.0, 0.8, 0.6)),
            a("PERCH_5", "PERCH", 104.0, 25.0, 20.0, normal = v(0.0, 1.0, 0.2)),
            a("STATION_CLEANER", "STATION", 62.0, 20.0, 18.0),
            a("SHRIMP_LEDGE", "LEDGE", 88.0, 35.0, 15.0, normal = v(0.0, -1.0, 0.3)),
            a("SLEEP_TANG", "SLEEP", 36.0, 25.0, 7.0, dir = v(-0.7, 0.2, -0.7)),
            a("SURFACE_FEED", "FEED", 41.0, 55.0, 20.0),
            a("NORI_CLIP", "FEED", 2.0, 35.0, 18.0),
            a("HOB_OUTFLOW", "FLOW", 71.0, 55.0, 1.0),
            a("GRAZE_1", "GRAZE", 12.0, 20.0, 30.0), a("GRAZE_2", "GRAZE", 30.0, 40.0, 12.0),
            a("GRAZE_3", "GRAZE", 44.0, 8.0, 27.0), a("GRAZE_4", "GRAZE", 52.0, 30.0, 9.0),
            a("GRAZE_5", "GRAZE", 80.0, 20.0, 25.0), a("GRAZE_6", "GRAZE", 96.0, 45.0, 6.0),
            a("GRAZE_7", "GRAZE", 110.0, 12.0, 28.0), a("GRAZE_8", "GRAZE", 60.0, 4.0, 30.0),
            a("coral_kenya_main", "CORAL", 72.0, 38.0, 15.0, normal = v(0.0, 1.0, 0.2)),
            a("coral_gsp_upper_left", "CORAL", 21.0, 42.0, 12.0, normal = v(0.3, 0.6, 0.75)),
            a("coral_gsp_lower_right", "CORAL", 105.0, 12.0, 22.0, normal = v(0.0, 0.7, 0.7)),
            a("coral_zoa_front_center", "CORAL", 71.0, 5.0, 27.0, normal = v(0.0, 1.0, 0.0)),
            a("coral_clove_kenya_base", "CORAL", 77.0, 18.0, 22.0, normal = v(0.6, 0.5, 0.6)),
            a("coral_shroom_front_left", "CORAL", 53.0, 4.0, 28.0, normal = v(0.0, 1.0, 0.0)),
            a("coral_shroom_hairy", "CORAL", 82.3, 20.3, 15.2, normal = v(0.7, 0.7, -0.18)),
            a("coral_macro_back_left", "CORAL", 47.0, 36.0, 10.0, normal = v(0.2, 0.5, 0.8)),
            a("coral_aiptasia_kenya_base", "CORAL", 68.0, 37.0, 14.0, normal = v(-0.3, 0.9, 0.3)),
            // ---- Track C additions (new livestock, 04_more_species); Track B: please add to anchors.json ----
            a("coral_organpipe_kenya_base", "CORAL", 68.0, 37.0, 14.0, normal = v(-0.3, 0.9, 0.3)),
            a("HOLE_LAWNMOWER", "HOLE", 30.0, 10.0, 11.0, dir = v(0.3, 0.1, 0.95), depth = 7.0),
            a("STATION_SHRIMP", "STATION", 50.0, 18.0, 26.0, normal = v(0.0, 0.0, 1.0)),
            a("DUSTER_TUBE", "TUBE", 15.0, 3.0, 27.0, normal = v(0.0, 1.0, 0.0)),
            a("BRITTLE_HOME", "CAVE", 95.0, 4.0, 15.0),
            a("CAVE_DOTTYBACK", "CAVE", 42.0, 18.0, 6.0),
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
