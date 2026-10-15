package com.reeflive.app.render

import com.reeflive.core.sim.BuiltRock
import com.reeflive.core.sim.DefaultScape
import com.reeflive.core.sim.RockPieceDef

/**
 * Dynamic rockscape on screen: the sim's rest → world matrix per loose rock ([com.reeflive.core.sim.SimSnapshot.rockPoses])
 * and, for the models that sit on the rock (rockscape, reef details, algae tufts), each part's triangles grouped by the
 * rock they belong to so a moved rock draws at its own pose.
 *
 * Nothing is split until a rock has actually moved: until then every part draws whole, as before (no extra draw calls
 * in a static tank). After a move a part that touches moved rocks draws as one mesh for the rock still in place plus
 * one per moved rock. Triangles are labelled once per part, by the nearest [DefaultScape.pieces] entry (the same rule
 * the sim labels its rock cells with).
 */
class RockPoseDraw(private val pieces: List<RockPieceDef> = DefaultScape.pieces) {
    /** Bit i set: rock i has moved. */
    var moved = 0L; private set
    private var poses: FloatArray? = null
    private var version = Long.MIN_VALUE
    private val mat = FloatArray(16)

    private class Split(val key: Long, val meshes: List<Pair<Int, Mesh>>?)
    private val labels = HashMap<DrawPart, ByteArray>()
    private val splits = HashMap<DrawPart, Split>()

    /** Dry rocks added with the aquascape builder (drawn at [pose] with [builtMesh]), or null. */
    var built: List<BuiltRock>? = null; private set
    private val builtMeshes = HashMap<Long, Mesh>()

    /** Takes this frame's poses (cheap unless they changed). */
    fun update(rockPoses: FloatArray?, rockVersion: Long, builtRocks: List<BuiltRock>? = null) {
        built = builtRocks
        if (rockPoses === poses && rockVersion == version) return
        poses = rockPoses; version = rockVersion
        var m = 0L
        if (rockPoses != null) for (p in 0 until minOf(pieces.size, rockPoses.size / 16, 63)) {
            val o = p * 16
            var id = true
            for (k in 0 until 16) if (kotlin.math.abs(rockPoses[o + k] - (if (k % 5 == 0) 1f else 0f)) > 1e-5f) { id = false; break }
            if (!id) m = m or (1L shl p)
        }
        moved = m
    }

    fun isMoved(piece: Int): Boolean = piece in 0..62 && (moved shr piece) and 1L == 1L

    /** [world] carried by rock [piece]'s pose into [out]; [world] itself when that rock never moved. */
    fun apply(piece: Int, world: FloatArray, out: FloatArray): FloatArray {
        val p = poses ?: return world
        if (!isMoved(piece)) return world
        System.arraycopy(p, piece * 16, mat, 0, 16)
        return M.mul(mat, world, out)
    }

    /** Rock [piece]'s pose, or null when it has not moved. */
    fun matrix(piece: Int): FloatArray? {
        val p = poses ?: return null
        if (!isMoved(piece)) return null
        System.arraycopy(p, piece * 16, mat, 0, 16)
        return mat
    }

    /** Rock [piece]'s rest → world matrix whether or not it moved (builder rocks), or null without poses. */
    fun pose(piece: Int): FloatArray? {
        val p = poses ?: return null
        if ((piece + 1) * 16 > p.size) return null
        System.arraycopy(p, piece * 16, mat, 0, 16)
        return mat
    }

    /** The mesh of a builder rock (made once per shape and seed). */
    fun builtMesh(r: BuiltRock): Mesh = builtMeshes.getOrPut(r.shape.ordinal.toLong() shl 32 or (r.seed.toLong() and 0xffffffffL)) {
        val d = DryRockMesh.build(r.shape, r.seed)
        Mesh.create(d.vertices, d.indices)
    }

    /**
     * The meshes to draw [part] with, each with its rock (−1 = the rock still in place), or null to draw the part
     * whole: nothing moved, or none of its triangles are on a moved rock.
     */
    fun meshes(part: DrawPart): List<Pair<Int, Mesh>>? {
        if (moved == 0L) return null
        val prim = part.prim ?: return null
        splits[part]?.let { if (it.key == moved) return it.meshes }
        val lab = labels.getOrPut(part) { label(part, prim) }
        val groups = HashMap<Int, ArrayList<Int>>()
        val n = prim.indices.size / 3
        for (t in 0 until n) {
            val l = lab[t].toInt()
            val g = if (l >= 0 && isMoved(l)) l else -1
            groups.getOrPut(g) { ArrayList() }.add(t)
        }
        val meshes = if (groups.keys.all { it < 0 }) null else groups.map { (g, tris) -> g to subMesh(prim, tris) }
        splits.remove(part)?.meshes?.forEach { it.second.release() }
        splits[part] = Split(moved, meshes)
        return meshes
    }

    private fun label(part: DrawPart, prim: GlbPrimitive): ByteArray {
        val v = prim.vertices; val idx = prim.indices; val s = GlbPrimitive.STRIDE
        val w = part.node.world
        val out = FloatArray(3)
        return ByteArray(idx.size / 3) { t ->
            var x = 0f; var y = 0f; var z = 0f
            for (c in 0 until 3) { val o = idx[t * 3 + c] * s; x += v[o]; y += v[o + 1]; z += v[o + 2] }
            M.transformPoint(w, x / 3f, y / 3f, z / 3f, out)
            RockPieceDef.labelOf(pieces, out[0].toDouble(), out[1].toDouble(), out[2].toDouble(), LABEL_MAX_CM).toByte()
        }
    }

    private fun subMesh(prim: GlbPrimitive, tris: List<Int>): Mesh {
        val s = GlbPrimitive.STRIDE
        val remap = HashMap<Int, Int>()
        val idx = IntArray(tris.size * 3)
        val verts = ArrayList<Int>()
        var k = 0
        for (t in tris) for (c in 0 until 3) {
            val vi = prim.indices[t * 3 + c]
            idx[k++] = remap.getOrPut(vi) { verts.add(vi); verts.size - 1 }
        }
        val out = FloatArray(verts.size * s)
        for ((i, vi) in verts.withIndex()) System.arraycopy(prim.vertices, vi * s, out, i * s, s)
        return Mesh.create(out, idx)
    }

    /** GL context lost: the split meshes died with it (parts are rebuilt on reload). */
    fun onContextLost() { labels.clear(); splits.clear(); builtMeshes.clear() }

    companion object {
        /** A vertex farther than this from every rock (glass decals, equipment) stays put. */
        const val LABEL_MAX_CM = 8.0
    }
}
