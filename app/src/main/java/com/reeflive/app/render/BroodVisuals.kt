package com.reeflive.app.render

import com.reeflive.core.geom.Sdf
import com.reeflive.core.sim.BroodField
import com.reeflive.core.sim.SimSnapshot

/**
 * Clownfish eggs on the rock, larvae in the display and the young in the nursery tank (core [BroodField]), drawn
 * with the organism shaders like the microfauna and never picked. Medium draws half as many, Low a quarter. The nursery
 * tank itself is [NurseryScene]. GL thread only.
 */
class BroodVisuals {
    /** The young and the keeper's routine animation (null until something breeds). */
    var field: BroodField? = null; private set
    private val rigs = HashMap<String, FishRig>()
    private val drawn = ArrayList<FishRig>()
    private var fishHeads = FloatArray(0)

    /** Rigs to draw this frame. */
    val visible: List<FishRig> get() = drawn

    /** Young in the nursery right now (the tank is drawn only while it holds any). */
    var nurseryCount = 0; private set

    fun update(s: SimSnapshot?, sdf: Sdf?, dt: Float, quality: RenderQuality) {
        drawn.clear()
        nurseryCount = s?.brood?.nursery ?: 0
        if (s == null) return
        if (s.eggClutches.isEmpty() && s.brood.displayLarvae == 0 && s.brood.nursery == 0) { if (rigs.isNotEmpty()) release(); return }
        val f = field ?: BroodField(sdf).also { field = it }
        if (fishHeads.size != s.organisms.size * 3) fishHeads = FloatArray(s.organisms.size * 3)
        for ((i, o) in s.organisms.withIndex()) if (o.spine.size >= 3) {
            fishHeads[i * 3] = o.spine[0]; fishHeads[i * 3 + 1] = o.spine[1]; fishHeads[i * 3 + 2] = o.spine[2]
        }
        val poses = f.update(s, (dt * s.speed.coerceIn(1.0, 4.0)).toDouble(), when (quality) { RenderQuality.HIGH -> 1.0; RenderQuality.MEDIUM -> 0.5; else -> 0.25 }, fishHeads)
        for (r in rigs.values) r.touched = false
        for (p in poses) {
            val r = rigs.getOrPut(p.id) { FishRig(p.id) }
            r.setBrood(p)
            drawn += r
        }
        if (rigs.size > drawn.size) rigs.values.removeAll { !it.touched }
    }

    fun release() { field = null; rigs.clear(); drawn.clear() }

    companion object {
        fun modelFor(speciesId: String): String? = BroodField.modelFor(speciesId)
    }
}
