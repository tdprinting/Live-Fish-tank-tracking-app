@file:OptIn(ExperimentalJsExport::class)

package com.reeflive.web

import com.reeflive.app.render.RoomLook
import com.reeflive.core.geom.CachedCoverSdf
import com.reeflive.core.geom.FlowGrid
import com.reeflive.core.geom.SdfGrid
import com.reeflive.core.geom.Vec3
import com.reeflive.core.sim.AlgaeCover
import com.reeflive.core.sim.BacteriaState
import com.reeflive.core.sim.BroodView
import com.reeflive.core.sim.Coral
import com.reeflive.core.sim.DynamicRockSdf
import com.reeflive.core.sim.MicrofaunaState
import com.reeflive.core.sim.NO_LIMBS
import com.reeflive.core.sim.Organism
import com.reeflive.core.sim.RockData
import com.reeflive.core.sim.SandGridSnapshot
import com.reeflive.core.sim.SimSnapshot
import com.reeflive.core.sim.SimSpecies
import com.reeflive.core.sim.Simulation
import com.reeflive.core.sim.SpeciesCatalog
import com.reeflive.core.sim.TankShape
import com.reeflive.core.sim.Thought

/**
 * What the sim worker and the page agree on to pass Kotlin objects between them (web/site/simwire.js packs them; the
 * same compiled classes are on both sides, so an object crosses as its class and its fields):
 *  - [canon]: objects both sides hold themselves (every enum entry of :core, a few shared constants), sent by position
 *    so `===` still holds on the other side;
 *  - [stickyClasses]: immutable values the view keys caches on (`!==` = changed), sent once and then by reference
 *    while each message still uses them;
 *  - [forbiddenClasses]: live sim objects that must never ride in a snapshot (a leak would copy the whole sim).
 * [snapshot] is the copy of the newest snapshot the worker sends: the slow parts (stats, microfauna, bacteria, life
 * forms) refresh about four times a second, and the moving-rock field is a plain grid ([WireRockSdf]).
 */
@JsExport
object ReefWire {
    private val canonList: Array<Any?> by lazy {
        val out = ArrayList<Any?>()
        for (e in WIRE_ENUMS) out.addAll(e)
        out.addAll(listOf(TankShape.STOCK, AlgaeCover.EMPTY, MicrofaunaState.EMPTY, BacteriaState.EMPTY, BroodView.EMPTY, SimSnapshot.EMPTY,
            Thought.NONE, NO_LIMBS, RoomLook.DEFAULT, emptyList<Any>(), emptyMap<Any, Any>(), emptySet<Any>(), Unit))
        out.toTypedArray()
    }

    fun canon(): Array<Any?> = canonList

    fun stickyClasses(): Array<dynamic> = arrayOf(TankShape::class.js, SandGridSnapshot::class.js, FlowGrid::class.js, WireRockSdf::class.js, SdfGrid::class.js)

    fun forbiddenClasses(): Array<dynamic> = arrayOf(Simulation::class.js, SpeciesCatalog::class.js, SimSpecies::class.js, Organism::class.js, Coral::class.js,
        DynamicRockSdf::class.js, RockData::class.js)

    // ---- the slow parts, refreshed at most every SLOW_MS (and at once when another sim comes on screen)
    private var slowSim: Simulation? = null
    private var slowAt = -1e18
    private var stats: List<com.reeflive.core.sim.OrganismStats> = emptyList()
    private var micro: MicrofaunaState = MicrofaunaState.EMPTY
    private var bacteria: BacteriaState = BacteriaState.EMPTY
    private var lifeForms: Map<String, com.reeflive.core.sim.LifeForm> = emptyMap()
    private var sdfFor: DynamicRockSdf? = null
    private var sdfRev = -1
    private var sdf: WireRockSdf? = null

    /** The wire copy of the sim's newest snapshot ([nowMs]: wall clock), or null with no tank on screen. */
    fun snapshot(nowMs: Double): Any? {
        val s = ReefSim.sim ?: return null
        val snap = s.latest
        if (s !== slowSim || nowMs - slowAt >= SLOW_MS) {
            slowSim = s; slowAt = nowMs
            stats = snap.stats; micro = snap.microfauna; bacteria = snap.bacteria; lifeForms = snap.lifeForms
        }
        return snap.copy(stats = stats, microfauna = micro, bacteria = bacteria, lifeForms = lifeForms, rockSdf = wireSdf(snap.rockSdf))
    }

    /** The objects [snapshot] reuses from earlier messages: sent once, then by reference. */
    fun slowParts(): Array<Any?> = arrayOf(stats, micro, bacteria, lifeForms)

    private fun wireSdf(f: com.reeflive.core.geom.Sdf?): com.reeflive.core.geom.Sdf? {
        val d = f as? DynamicRockSdf ?: return f
        val comp = d.composite ?: return null
        if (d !== sdfFor || d.revision != sdfRev || sdf == null) {
            sdfFor = d; sdfRev = d.revision
            sdf = WireRockSdf(d.nx, d.ny, d.nz, d.ox, d.oy, d.oz, d.cell, comp)
        }
        return sdf
    }

    private const val SLOW_MS = 250.0
}

/** The moved rockscape's field as a plain grid: what the view needs of [DynamicRockSdf] (taps, the camera, critters). */
internal class WireRockSdf(
    val nx: Int, val ny: Int, val nz: Int, val ox: Double, val oy: Double, val oz: Double, val cell: Double, val d: FloatArray,
) : CachedCoverSdf() {
    override fun dist(p: Vec3): Double = DynamicRockSdf.trilinear(d, nx, ny, nz, ox, oy, oz, cell, p.x, p.y, p.z)
    override fun distAt(x: Double, y: Double, z: Double): Double = DynamicRockSdf.trilinear(d, nx, ny, nz, ox, oy, oz, cell, x, y, z)
}
