package com.reeflive.core.sim

import com.reeflive.core.geom.Vec3
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The things in the water that are not rock, sand or glass: the coral colonies, the decor and the equipment.
 * Equipment and hard decor are solid (owner rule, tee-clips ISSUES #3 / #7: no body passes through them). Coral is
 * soft (Tee): every animal pushes through a colony and the colony bends; nothing is pushed off it. Each body station
 * inside a colony is reported in [contacts] instead, for the coral motion to bend its polyps and branches. Each is a set of capsules (a sphere is a
 * capsule with equal ends) fitted offline to the model the renderer draws ([StaticObstacleData], baked by
 * `tools/sdf/bake_obstacles.py` from the GLBs, which are in world cm), plus the feather duster built from its organism.
 *
 * Kept in a uniform grid of [CELL] cm cells over the tank (flat index arrays): a query reads only the cells its sphere
 * touches and allocates nothing unless it finds a contact. Kotlin/JS-safe (no java.*).
 *
 * ## Rules per obstacle
 * Each solid capsule has a `give` (cm a body may press into it) and a pass mask (the animals it lets through; see
 * [passMask]):
 *
 * | Obstacle | give | passes | why |
 * |---|---|---|---|
 * | every coral colony (Kenya, macro, mushrooms incl. the clowns' hairy mushroom host and decor `MushroomsRed`, leathers, GSP, clove, zoa, organ pipe) | – | everyone (soft) | swimmers push through coral and the coral bends ([contacts]) |
 * | feather duster crown (organism) | 1.0 | [PASS_SMALL] | a fan of radioles that snaps shut on contact; scaled by the crown's extension and regrowth |
 * | feather duster tube (organism) | 0 | none | parchment tube, firm |
 * | hard decor (`reef_details` except the mushrooms) | 0 | none | hard |
 * | equipment in the water (heater, HOB intake tube, powerheads, HOB / skimmer lips, magnet cleaners, feed ring, hose, cable) | 0 | none | hard |
 *
 * Polyps (Kenya tufts, GSP, zoa, clove, leather polyps) are not obstacles: they are drawn over the body mesh and
 * retract when touched. A tank colony keeps its model's shape whatever its tissue state (the renderer still draws it);
 * a sandbox copy ([Coral.offset]) carries the model's nodes near its home anchor moved by the offset, like the renderer.
 * Kenya branch drops ([CoralGrowth.dropBranch]) are not drawn, so they are no obstacle either. There is no clam or
 * gorgonian model in the tank: the branching colony in the clips is the Kenya tree.
 *
 * ## Updating
 * [sync] (called by the Simulation every behaviour tick) rebuilds the grid only when the set of colonies or dusters
 * changes (added, removed, a sandbox copy placed; a cheap identity check otherwise) and rescales the duster crowns in
 * place. [invalidate] forces a rebuild (decor edited by a future shelf / sandbox action).
 */
class StaticObstacles(private val rock: RockData) {
    // ---- capsules: ends, radius (full size), current radius, give, pass mask, owner ----
    private var n = 0
    private var ax = DoubleArray(0); private var ay = DoubleArray(0); private var az = DoubleArray(0)
    private var bx = DoubleArray(0); private var by = DoubleArray(0); private var bz = DoubleArray(0)
    private var rad0 = DoubleArray(0)
    private var rad = DoubleArray(0)
    private var give = DoubleArray(0)
    private var pass = IntArray(0)
    /** Bounding sphere of each capsule at full size (midpoint, half length + radius) for a quick reject. */
    private var mx = DoubleArray(0); private var my = DoubleArray(0); private var mz = DoubleArray(0); private var br = DoubleArray(0)
    private var owner = IntArray(0)
    private val ownerNames = ArrayList<String>()
    /** Coral id of each owner (null: solid equipment, decor or a duster): a capsule whose owner has one is soft. */
    private val ownerCoral = ArrayList<String?>()
    /** Capsule range [first, end) of each duster crown and its organism (rescaled by [sync]). */
    private val crownFirst = ArrayList<Int>()
    private val crownEnd = ArrayList<Int>()
    private val crownOf = ArrayList<Organism>()

    // ---- grid (compressed rows: the capsules of cell c are items[start[c] until start[c + 1]]) ----
    private val nx = ceil(rock.lengthCm / CELL).toInt() + 1
    private val ny = ceil((rock.waterLevelCm + 4) / CELL).toInt() + 1
    private val nz = ceil(rock.widthCm / CELL).toInt() + 1
    private val start = IntArray(nx * ny * nz + 1)
    private var items = IntArray(0)

    // ---- change detection for [sync] ----
    private var seenCorals = arrayOfNulls<Coral>(0)
    private var seenDusters = arrayOfNulls<Organism>(0)
    private var dirty = true

    // ---- coral contacts this tick (pooled: filled by [push], cleared by [sync]) ----
    private val pool = ArrayList<CoralContact>()
    private var nContacts = 0

    /**
     * Every body station inside a coral colony this behaviour tick: one entry per animal and colony (the deepest
     * station), filled by the body collision's per-station queries and cleared at the start of the next tick by
     * [sync]. The list and its entries are reused from tick to tick: read them within the tick, copy what is kept.
     */
    val contacts: List<CoralContact> = object : AbstractList<CoralContact>() {
        override val size: Int get() = nContacts
        override fun get(index: Int): CoralContact {
            if (index < 0 || index >= nContacts) throw IndexOutOfBoundsException("$index of $nContacts")
            return pool[index]
        }
    }

    /** The last [push] overlapped a soft (coral) capsule. */
    var softTouched = false; private set

    /** Owner label of the capsule the last [depth] / [push] found deepest (tests, logs), or null. */
    var lastOwner: String? = null; private set
    /** Capsules in the grid now. */
    val size: Int get() = n
    /** Grid rebuilds so far (tests). */
    var rebuilds = 0; private set

    /** Forces a rebuild at the next [sync] (decor moved or removed). */
    fun invalidate() { dirty = true }

    /**
     * Brings the obstacles in line with [sim]'s colonies and dusters: rebuilds when the set changed, then rescales the
     * duster crowns (extension × regrowth). Cheap when nothing changed: an identity pass over the colonies and dusters.
     */
    fun sync(sim: Simulation) {
        nContacts = 0
        val corals = sim.corals
        val orgs = sim.organisms
        var changed = dirty || corals.size != seenCorals.size
        if (!changed) for (i in corals.indices) if (corals[i] !== seenCorals[i]) { changed = true; break }
        var d = 0
        for (o in orgs) if (o.alive && o.brainKind == BrainKind.FEATHER_DUSTER) {
            if (d >= seenDusters.size || seenDusters[d] !== o) changed = true
            d++
        }
        if (d != seenDusters.size) changed = true
        if (changed) rebuild(corals, orgs)
        for (k in crownOf.indices) {
            val o = crownOf[k]
            val s = (o.extension * sim.invertEcology.crownOf(o)).coerceIn(0.0, 1.0)
            for (i in crownFirst[k] until crownEnd[k]) rad[i] = rad0[i] * s
        }
    }

    /** Rebuilds every capsule and the grid from these colonies and the live feather dusters among [organisms]. */
    fun rebuild(corals: List<Coral>, organisms: List<Organism>) {
        dirty = false
        rebuilds++
        seenCorals = arrayOfNulls<Coral>(corals.size).also { a -> for (i in corals.indices) a[i] = corals[i] }
        val dusters = organisms.filter { it.alive && it.brainKind == BrainKind.FEATHER_DUSTER }
        seenDusters = arrayOfNulls<Organism>(dusters.size).also { a -> for (i in dusters.indices) a[i] = dusters[i] }
        val b = Builder()
        val used = HashSet<String>()
        for (c in corals) {
            val models = MODELS_BY_SPECIES[c.species.id] ?: MODELS_BY_KIND[c.kind] ?: continue
            val anchored = models.firstOrNull { m -> DATA[m]?.anchor == c.anchor && c.anchor.isNotEmpty() }
            val copy = c.offset.x != 0.0 || c.offset.y != 0.0 || c.offset.z != 0.0
            val name = if (copy) anchored ?: models.first() else anchored ?: models.firstOrNull { it !in used } ?: continue
            val m = DATA[name] ?: continue
            if (!copy && !used.add(name)) continue
            val rule = RULES[name] ?: Rule(0.0, 0)
            val owner = b.owner("coral ${c.id} ($name)", c.id)
            val home = c.pos - c.offset
            for (node in m.nodes) {
                // a copy carries only the nodes near its home anchor (TankRenderer.nearHome, COPY_RADIUS_CM)
                if (copy && node.centre.distanceTo(home) > COPY_RADIUS_CM) continue
                val r = rule
                for (k in 0 until node.caps.size / 7) {
                    val q = k * 7
                    val o = c.offset
                    b.add(node.caps[q] + o.x, node.caps[q + 1] + o.y, node.caps[q + 2] + o.z, node.caps[q + 3] + o.x, node.caps[q + 4] + o.y, node.caps[q + 5] + o.z, node.caps[q + 6], r.give, r.pass, owner)
                }
            }
        }
        // decor and equipment: always present
        for (name in FIXED) {
            val m = DATA[name] ?: continue
            for (node in m.nodes) {
                val r = RULES[name] ?: Rule(0.0, 0)
                // (the decor's mushroom corals are coral too: soft)
                val owner = b.owner("$name ${node.name}", if ("$name/${node.name}" in DECOR_CORAL) "$name/${node.name}" else null)
                for (k in 0 until node.caps.size / 7) {
                    val q = k * 7
                    b.add(node.caps[q], node.caps[q + 1], node.caps[q + 2], node.caps[q + 3], node.caps[q + 4], node.caps[q + 5], node.caps[q + 6], r.give, r.pass, owner)
                }
            }
        }
        // feather dusters (feather_duster.glb, cm model drawn unrotated at the organism, tube base at the origin):
        // tube 0..13.1 cm, Ø ≈ 1.5 cm; crown centred at 13.2–14.2 cm, ≈ 8.4 cm across, a shallow funnel
        crownFirst.clear(); crownEnd.clear(); crownOf.clear()
        for (o in dusters) {
            val p = o.pos
            val owner = b.owner("duster ${o.id} tube")
            b.add(p.x, p.y + 0.4, p.z, p.x, p.y + 12.8, p.z, 0.75, 0.0, 0, owner)
            val crown = b.owner("duster ${o.id} crown")
            val first = b.count
            b.add(p.x, p.y + 13.4, p.z, p.x, p.y + 14.4, p.z, 1.6, 1.0, PASS_SMALL, crown)
            for (k in 0 until 8) {
                val a = k * kotlin.math.PI / 4
                val cx = p.x + kotlin.math.cos(a) * 2.9; val cz = p.z + kotlin.math.sin(a) * 2.9
                b.add(cx, p.y + 14.0, cz, cx, p.y + 14.8, cz, 1.3, 1.0, PASS_SMALL, crown)
            }
            crownFirst += first; crownEnd += b.count; crownOf += o
        }
        b.commit()
    }

    /**
     * Push-out for a sphere of radius [r] (cm) centred at [p], against every obstacle [mask] does not pass
     * ([passMask] of the animal; 0 = everything is solid).
     *
     * Returns the **push-out vector** (cm, world): the translation to ADD to [p] so the sphere ends no deeper than each
     * capsule's `give` (it does not include the caller's skin). Several contacts combine per axis as the largest push
     * each way (the same rule as [BodyCollision.resolveEnv]), so a sphere wedged between two branches gets one vector
     * that serves both. Returns null when the sphere is clear (the common case: no allocation). The vector points out
     * along each capsule's surface normal at the contact; a centre exactly on a capsule axis is pushed straight up.
     *
     * Coral is soft: it never pushes. A sphere inside a colony sets [softTouched], and with [who] (the animal's id)
     * is reported in [contacts].
     *
     * Call: `obstacles.push(p: Vec3, r: Double, mask: Int = 0, who: String? = null): Vec3?`, e.g. per body station
     * from `BodyCollision.resolveEnv` with the station's section centre and bounding radius and `obstacles.passMask(o)`.
     */
    fun push(p: Vec3, r: Double, mask: Int = 0, who: String? = null): Vec3? {
        softTouched = false
        if (n == 0) return null
        var hiX = 0.0; var hiY = 0.0; var hiZ = 0.0; var loX = 0.0; var loY = 0.0; var loZ = 0.0
        var any = false
        var best = 0.0
        lastOwner = null
        val x0 = cx(p.x - r); val x1 = cx(p.x + r)
        val y0 = cy(p.y - r); val y1 = cy(p.y + r)
        val z0 = cz(p.z - r); val z1 = cz(p.z + r)
        for (iz in z0..z1) for (iy in y0..y1) for (ix in x0..x1) {
            val c = ix + nx * (iy + ny * iz)
            for (s in start[c] until start[c + 1]) {
                val i = items[s]
                if (pass[i] and mask != 0) continue
                val qx = p.x - mx[i]; val qy = p.y - my[i]; val qz = p.z - mz[i]; val qr = br[i] + r
                if (qx * qx + qy * qy + qz * qz >= qr * qr) continue
                // closest point on the capsule axis
                val dx = bx[i] - ax[i]; val dy = by[i] - ay[i]; val dz = bz[i] - az[i]
                val l2 = dx * dx + dy * dy + dz * dz
                var t = if (l2 > 1e-12) ((p.x - ax[i]) * dx + (p.y - ay[i]) * dy + (p.z - az[i]) * dz) / l2 else 0.0
                t = t.coerceIn(0.0, 1.0)
                val ex = p.x - (ax[i] + dx * t); val ey = p.y - (ay[i] + dy * t); val ez = p.z - (az[i] + dz * t)
                val d2 = ex * ex + ey * ey + ez * ez
                val coral = ownerCoral[owner[i]]
                if (coral != null) {
                    val reachC = r + rad[i]
                    if (d2 < reachC * reachC) {
                        softTouched = true
                        if (who != null) {
                            val d = sqrt(d2)
                            // the point on the colony's surface toward the body, and how deep the sphere is past it
                            val k = if (d > 1e-9) rad[i] / d else 0.0
                            note(who, coral, Vec3(ax[i] + dx * t + ex * k, ay[i] + dy * t + ey * k + (if (d > 1e-9) 0.0 else rad[i]), az[i] + dz * t + ez * k), reachC - d)
                        }
                    }
                    continue
                }
                val reach = r + rad[i] - give[i]
                if (reach <= 0 || d2 >= reach * reach) continue
                val d = sqrt(d2)
                val pen = reach - d
                val nxv: Double; val nyv: Double; val nzv: Double
                if (d > 1e-9) { nxv = ex / d; nyv = ey / d; nzv = ez / d } else { nxv = 0.0; nyv = 1.0; nzv = 0.0 }
                val px = nxv * pen; val py = nyv * pen; val pz = nzv * pen
                hiX = max(hiX, px); hiY = max(hiY, py); hiZ = max(hiZ, pz)
                loX = min(loX, px); loY = min(loY, py); loZ = min(loZ, pz)
                any = true
                if (pen > best) { best = pen; lastOwner = ownerNames[owner[i]] }
            }
        }
        return if (any) Vec3(hiX + loX, hiY + loY, hiZ + loZ) else null
    }

    /**
     * Whether any obstacle [mask] does not pass (solid or soft) comes near the sphere ([p], [r]): false means [push] of
     * any sphere inside it finds nothing (it would return null and touch no coral), so a caller may skip those calls.
     * No side effects.
     */
    fun near(p: Vec3, r: Double, mask: Int = 0): Boolean {
        if (n == 0) return false
        val x0 = cx(p.x - r); val x1 = cx(p.x + r)
        val y0 = cy(p.y - r); val y1 = cy(p.y + r)
        val z0 = cz(p.z - r); val z1 = cz(p.z + r)
        for (iz in z0..z1) for (iy in y0..y1) for (ix in x0..x1) {
            val c = ix + nx * (iy + ny * iz)
            for (s in start[c] until start[c + 1]) {
                val i = items[s]
                if (pass[i] and mask != 0) continue
                val qx = p.x - mx[i]; val qy = p.y - my[i]; val qz = p.z - mz[i]; val qr = br[i] + r
                if (qx * qx + qy * qy + qz * qz < qr * qr) return true
            }
        }
        return false
    }

    /**
     * Deepest penetration (cm) of the sphere ([p], [r]) into any obstacle [mask] does not pass, beyond that obstacle's
     * give: > 0 is a violation, ≤ 0 clear (−∞ when nothing is near). [lastOwner] names the deepest one.
     */
    fun depth(p: Vec3, r: Double, mask: Int = 0): Double {
        var worst = Double.NEGATIVE_INFINITY
        lastOwner = null
        if (n == 0) return worst
        val x0 = cx(p.x - r); val x1 = cx(p.x + r)
        val y0 = cy(p.y - r); val y1 = cy(p.y + r)
        val z0 = cz(p.z - r); val z1 = cz(p.z + r)
        for (iz in z0..z1) for (iy in y0..y1) for (ix in x0..x1) {
            val c = ix + nx * (iy + ny * iz)
            for (s in start[c] until start[c + 1]) {
                val i = items[s]
                if (pass[i] and mask != 0 || ownerCoral[owner[i]] != null) continue
                val qx = p.x - mx[i]; val qy = p.y - my[i]; val qz = p.z - mz[i]; val qr = br[i] + r
                if (worst > -1.0 && qx * qx + qy * qy + qz * qz >= qr * qr) continue
                val dx = bx[i] - ax[i]; val dy = by[i] - ay[i]; val dz = bz[i] - az[i]
                val l2 = dx * dx + dy * dy + dz * dz
                var t = if (l2 > 1e-12) ((p.x - ax[i]) * dx + (p.y - ay[i]) * dy + (p.z - az[i]) * dz) / l2 else 0.0
                t = t.coerceIn(0.0, 1.0)
                val ex = p.x - (ax[i] + dx * t); val ey = p.y - (ay[i] + dy * t); val ez = p.z - (az[i] + dz * t)
                val v = r + rad[i] - give[i] - sqrt(ex * ex + ey * ey + ez * ez)
                if (v > worst) { worst = v; lastOwner = ownerNames[owner[i]] }
            }
        }
        return worst
    }

    /** Records (or deepens) the contact of animal [who] with colony [coral] this tick. */
    private fun note(who: String, coral: String, point: Vec3, depth: Double) {
        for (k in 0 until nContacts) {
            val c = pool[k]
            if (c.organismId == who && c.coralId == coral) {
                if (depth > c.depthCm) { c.point = point; c.depthCm = depth }
                return
            }
        }
        if (nContacts == pool.size) pool += CoralContact()
        val c = pool[nContacts++]
        c.organismId = who; c.coralId = coral; c.point = point; c.depthCm = depth
    }

    /** Whether capsule [i] is coral (soft: passed by everyone, reported in [contacts]). */
    fun isSoft(i: Int): Boolean = ownerCoral[owner[i]] != null

    /** Capsule [i] as (ax, ay, az, bx, by, bz, radius, give) and its pass mask and owner (tests, debug overlays). */
    fun capsule(i: Int): DoubleArray = doubleArrayOf(ax[i], ay[i], az[i], bx[i], by[i], bz[i], rad[i], give[i])
    fun passOf(i: Int): Int = pass[i]
    fun ownerOf(i: Int): String = ownerNames[owner[i]]

    private fun cx(v: Double) = floor(v / CELL).toInt().coerceIn(0, nx - 1)
    private fun cy(v: Double) = floor(v / CELL).toInt().coerceIn(0, ny - 1)
    private fun cz(v: Double) = floor(v / CELL).toInt().coerceIn(0, nz - 1)

    /** Collects capsules into growable arrays, then [commit] swaps them in and fills the grid. */
    private inner class Builder {
        var count = 0
        private var cap = 1024
        private var arrs = Array(8) { DoubleArray(cap) }
        var p = IntArray(cap)
        var w = IntArray(cap)
        init { ownerNames.clear(); ownerCoral.clear() }

        fun owner(label: String, coral: String? = null): Int { ownerNames += label; ownerCoral += coral; return ownerNames.size - 1 }

        fun add(x0: Double, y0: Double, z0: Double, x1: Double, y1: Double, z1: Double, r: Double, g: Double, mask: Int, who: Int) {
            if (count == cap) {
                cap *= 2
                arrs = Array(8) { k -> arrs[k].copyOf(cap) }
                p = p.copyOf(cap); w = w.copyOf(cap)
            }
            val v = arrs
            v[0][count] = x0; v[1][count] = y0; v[2][count] = z0; v[3][count] = x1; v[4][count] = y1; v[5][count] = z1
            v[6][count] = r; v[7][count] = g
            p[count] = mask; w[count] = who
            count++
        }

        fun commit() {
            val v = arrs
            n = count
            ax = v[0].copyOf(n); ay = v[1].copyOf(n); az = v[2].copyOf(n)
            bx = v[3].copyOf(n); by = v[4].copyOf(n); bz = v[5].copyOf(n)
            rad0 = v[6].copyOf(n); rad = v[6].copyOf(n); give = v[7].copyOf(n)
            pass = p.copyOf(n); owner = w.copyOf(n)
            mx = DoubleArray(n) { (ax[it] + bx[it]) / 2 }; my = DoubleArray(n) { (ay[it] + by[it]) / 2 }; mz = DoubleArray(n) { (az[it] + bz[it]) / 2 }
            br = DoubleArray(n) { 0.5 * sqrt((bx[it] - ax[it]).let { d -> d * d } + (by[it] - ay[it]).let { d -> d * d } + (bz[it] - az[it]).let { d -> d * d }) + rad0[it] }
            // two passes over each capsule's cell box (full-size radius: a crown grows back without a rebuild)
            start.fill(0)
            fun cells(i: Int, f: (Int) -> Unit) {
                val r = rad0[i]
                for (iz in cz(min(az[i], bz[i]) - r)..cz(max(az[i], bz[i]) + r))
                    for (iy in cy(min(ay[i], by[i]) - r)..cy(max(ay[i], by[i]) + r))
                        for (ix in cx(min(ax[i], bx[i]) - r)..cx(max(ax[i], bx[i]) + r)) f(ix + nx * (iy + ny * iz))
            }
            for (i in 0 until n) cells(i) { c -> start[c + 1]++ }
            for (c in 1 until start.size) start[c] += start[c - 1]
            items = IntArray(start[start.size - 1])
            val fill = start.copyOf()
            for (i in 0 until n) cells(i) { c -> items[fill[c]++] = i }
        }
    }

    private class Rule(val give: Double, val pass: Int)
    private class Node(val name: String, val centre: Vec3, val caps: DoubleArray)
    private class Model(val anchor: String, val nodes: List<Node>)

    companion object {
        /** Grid cell (cm). */
        const val CELL = 4.0
        /** TankRenderer.COPY_RADIUS_CM: a sandbox copy keeps the model's nodes this close to its home anchor. */
        const val COPY_RADIUS_CM = 25.0
        /** Small crawlers and the cleaner / banded shrimp pass the feather duster's crown. */
        const val PASS_SMALL = 2
        /** Bodies shorter than this (cm) count as small ([PASS_SMALL]): snails, hermits, asterina, juveniles. */
        const val SMALL_CM = 3.0

        /** The pass mask of [o]: which obstacle rules let it through (0 = everything is solid to it). */
        fun passMask(o: Organism): Int {
            var m = 0
            if (o.brainKind == BrainKind.CLEANER_SHRIMP || o.brainKind == BrainKind.BANDED_SHRIMP || o.lengthCm < SMALL_CM) m = m or PASS_SMALL
            return m
        }

        // (coral capsules are soft whatever their rule: these only describe the solid equipment and decor)
        private val RULES: Map<String, Rule> = mapOf(
            "equipment" to Rule(0.0, 0),
            "reef_details" to Rule(0.0, 0),
        )
        /** Decor nodes that are coral (soft). */
        private val DECOR_CORAL = setOf("reef_details/MushroomsRed")
        private val FIXED = listOf("reef_details", "equipment")
        private val MODELS_BY_KIND: Map<CoralKind, List<String>> = mapOf(
            CoralKind.KENYA to listOf("kenya"), CoralKind.GSP to listOf("gsp_mat", "gsp_neon"), CoralKind.ZOA to listOf("zoa"),
            CoralKind.CLOVE to listOf("clove"), CoralKind.MACRO to listOf("macro_red"), CoralKind.ORGAN_PIPE to listOf("organ_pipe"),
        )

        private val MODELS_BY_SPECIES = HashMap<String, List<String>>()
        private val DATA: Map<String, Model> = parse()

        private fun parse(): Map<String, Model> {
            val out = LinkedHashMap<String, Model>()
            var name: String? = null; var anchor = ""
            var nodes = ArrayList<Node>()
            var nodeName = ""; var centre = Vec3.ZERO
            val caps = ArrayList<Double>()
            fun endNode() { if (nodeName.isNotEmpty()) nodes += Node(nodeName, centre, caps.toDoubleArray()); caps.clear(); nodeName = "" }
            fun endModel() { endNode(); name?.let { out[it] = Model(anchor, nodes) }; nodes = ArrayList() }
            for (chunk in StaticObstacleData.CHUNKS) for (line in chunk.split('\n')) {
                if (line.isBlank()) continue
                val f = line.trim().split(' ')
                when (f[0]) {
                    "S" -> MODELS_BY_SPECIES[f[1]] = f.drop(2)
                    "M" -> { endModel(); name = f[1]; anchor = if (f[2] == "-") "" else f[2] }
                    "N" -> { endNode(); nodeName = f[1]; centre = Vec3(f[2].toDouble(), f[3].toDouble(), f[4].toDouble()) }
                    "C" -> for (k in 1..7) caps += f[k].toDouble()
                }
            }
            endModel()
            return out
        }
    }
}

/**
 * A body station of an animal inside a coral colony this tick ([StaticObstacles.contacts]): the animal, the colony
 * ([Coral.id]; `reef_details/MushroomsRed` for the decor's mushrooms), the point on the colony's surface nearest the
 * body (world cm) and how deep the station's bounding sphere is past it (cm). Pooled: reused every tick.
 */
class CoralContact internal constructor() {
    var organismId: String = ""; internal set
    var coralId: String = ""; internal set
    var point: Vec3 = Vec3.ZERO; internal set
    var depthCm: Double = 0.0; internal set
}
