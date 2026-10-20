package com.reeflive.app.render

import com.reeflive.core.geom.Sdf
import com.reeflive.core.geom.Vec3
import com.reeflive.core.sim.CoralPose
import com.reeflive.core.sim.DefaultScape
import com.reeflive.core.sim.DryRock
import com.reeflive.core.sim.GspMatBuilder
import com.reeflive.core.sim.GspPolyps
import com.reeflive.core.sim.RockPieceDef
import com.reeflive.core.sim.TankDims
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Green star polyp colonies drawn on the rock they grow on ([GspMatBuilder]): the purple stolon mat is a copy of the
 * drawn rock's own triangles (gsp_mat.frag, cut to the mat's current reach), and the long-tentacle polyps from the
 * colony's GLB are planted on those triangles (polyp.vert, uMatRadius). The baked GLB mat only shows while a colony
 * has no rock under it (on sand) or its rock mesh has not loaded yet.
 *
 * Built once per colony, placement, rock and polyp density (a few ms); a grown or shrunk mat only changes uniforms.
 */
class GspMatDraw {
    private class Entry(
        val x: Float, val y: Float, val z: Float, val piece: Int, val restricted: Boolean, val built: Long, val density: Float,
        val mat: Mesh?, val polyps: Mesh?, val result: GspMatBuilder.Result?, val protoPrim: GlbPrimitive?,
    )

    private val entries = HashMap<String, Entry>()
    private val pokeA = FloatArray(4 * MAX_POKES)
    private val pokeB = FloatArray(4 * MAX_POKES)
    private val pose = FloatArray(16)

    /** True when [m] is a green star polyp colony model (its root `species` extra). */
    fun isGsp(m: GpuModel): Boolean = m.glb.rootString("species")?.contains("star_polyps") == true

    /**
     * The colony's mat, built on first use: on the builder rock it sits on when [builtRock] is set, else on the
     * rockscape's [rock] part (limited to its own loose rock once any rock has moved). Null: draw the GLB instead.
     */
    fun ready(
        c: CoralPose, m: GpuModel, rock: DrawPart?, sdf: Sdf?, rockDraw: RockPoseDraw, density: Float,
    ): Boolean = entry(c, m, rock, sdf, rockDraw, density)?.result != null

    private fun entry(c: CoralPose, m: GpuModel, rock: DrawPart?, sdf: Sdf?, rockDraw: RockPoseDraw, density: Float): Entry? {
        val built = rockDraw.built?.firstOrNull { it.piece == c.rockPiece }
        val builtKey = if (built == null) -1L else built.shape.ordinal.toLong() shl 32 or (built.seed.toLong() and 0xffffffffL)
        val restricted = built == null && c.rockPiece >= 0 && rockDraw.moved != 0L
        val e = entries[c.id]
        if (e != null && e.x == c.restX && e.y == c.restY && e.z == c.restZ && e.piece == c.rockPiece && e.restricted == restricted &&
            e.built == builtKey && e.density == density) return e
        e?.mat?.release(); e?.polyps?.release()
        val proto = m.parts.firstOrNull { it.kind == MaterialKind.POLYP }?.prim
        val res: GspMatBuilder.Result? = when {
            proto == null -> null
            built != null -> {
                val def = DryRock.def(built.shape, built.seed)
                val d = DryRockMesh.build(def, built.seed)
                build(c, d.vertices, d.indices, null, density) { x, y, z -> def.dist(x, y, z) }
            }
            rock?.prim != null && sdf != null -> {
                val pieces = DefaultScape.pieces
                val keep: ((Double, Double, Double) -> Boolean)? =
                    if (restricted) { x, y, z -> RockPieceDef.labelOf(pieces, x, y, z, 4.0) == c.rockPiece } else null
                build(c, rock.prim.vertices, rock.prim.indices, rock.node.world, density, keep) { x, y, z ->
                    min(sdf.distAt(x, y, z), y - TankDims.SAND_TOP_CM)
                }
            }
            else -> null
        }
        // no rock yet (still loading): try again next frame rather than caching the miss
        if (res == null && proto != null && built == null && (rock?.prim == null || sdf == null)) return null
        val mat = res?.let { Mesh.create(it.vertices, it.indices) }
        val pol = if (res != null && proto != null && res.instanceCount > 0) Mesh.create(proto.vertices, proto.indices, res.instances) else null
        val ne = Entry(c.restX, c.restY, c.restZ, c.rockPiece, restricted, builtKey, density, mat, pol, res, proto)
        entries[c.id] = ne
        return ne
    }

    private fun build(
        c: CoralPose, v: FloatArray, idx: IntArray, world: FloatArray?, density: Float,
        keep: ((Double, Double, Double) -> Boolean)? = null, clearance: (Double, Double, Double) -> Double,
    ): GspMatBuilder.Result? = GspMatBuilder.build(
        v, idx, world, Vec3(c.restX.toDouble(), c.restY.toDouble(), c.restZ.toDouble()),
        Vec3(c.restNx.toDouble(), c.restNy.toDouble(), c.restNz.toDouble()), c.id.hashCode(),
        SPACING_CM / sqrt(density.coerceIn(0.1f, 1f).toDouble()), (MAX_POLYPS * density).toInt().coerceAtLeast(200), clearance, keep,
    )

    /**
     * The rock frame → world matrix for [c]'s mat: its builder rock's pose, its moved loose rock's pose, or identity.
     */
    private fun frame(c: CoralPose, rockDraw: RockPoseDraw): FloatArray {
        val p = if (rockDraw.built?.any { it.piece == c.rockPiece } == true) rockDraw.pose(c.rockPiece) else rockDraw.matrix(c.rockPiece)
        if (p == null) { IDENTITY.copyInto(pose); return pose }
        p.copyInto(pose)
        return pose
    }

    /** Draws the stolon mat (opaque pass, [p] = the gsp mat program). */
    fun drawMat(p: ShaderProgram, c: CoralPose, rockDraw: RockPoseDraw): Int {
        val e = entries[c.id] ?: return 0
        val mesh = e.mat ?: return 0
        p.use()
        p.setMat4("uModel", frame(c, rockDraw))
        p.set1f("uMatRadius", c.matRadius)
        p.set1f("uBleach", c.bleach); p.set1f("uBrown", c.brown); p.set1f("uTissue", c.tissue)
        mesh.draw()
        return mesh.triangles
    }

    /**
     * Draws the colony's polyps with the program and material [drawCoral] already bound ([p] = polyp program),
     * only those the mat has reached, closed around recent touches. Returns triangles drawn (0: none).
     */
    fun drawPolyps(p: ShaderProgram, c: CoralPose, rockDraw: RockPoseDraw, nowSec: Double): Int {
        val e = entries[c.id] ?: return 0
        val mesh = e.polyps ?: return 0
        val res = e.result ?: return 0
        mesh.instanceCount = res.countWithin(c.matRadius + EDGE_REACH_CM)
        if (mesh.instanceCount == 0) return 0
        val m = frame(c, rockDraw)
        p.setMat4("uModel", m)
        p.set1f("uMatRadius", c.matRadius.coerceAtLeast(0.01f))
        // touches: offsets in world axes from the colony -> the rock's frame (inverse rotation = transpose)
        val pk = c.pokes
        var n = 0
        if (pk != null) {
            val k = minOf(pk.size / GspPolyps.STRIDE, MAX_POKES)
            for (i in 0 until k) {
                val o = i * GspPolyps.STRIDE
                val dx = pk[o]; val dy = pk[o + 1]; val dz = pk[o + 2]
                pokeA[4 * n] = c.restX + m[0] * dx + m[1] * dy + m[2] * dz
                pokeA[4 * n + 1] = c.restY + m[4] * dx + m[5] * dy + m[6] * dz
                pokeA[4 * n + 2] = c.restZ + m[8] * dx + m[9] * dy + m[10] * dz
                pokeA[4 * n + 3] = (nowSec - pk[o + 3]).toFloat()
                pokeB[4 * n] = (nowSec - pk[o + 4]).toFloat()
                pokeB[4 * n + 1] = pk[o + 5]
                n++
            }
        }
        p.set1f("uPokeCount", n.toFloat())
        if (n > 0) { p.set4fv("uPokes", pokeA, MAX_POKES); p.set4fv("uPokeT", pokeB, MAX_POKES) }
        mesh.draw()
        p.set1f("uMatRadius", 0f)
        p.set1f("uPokeCount", 0f)
        return mesh.triangles
    }

    fun onContextLost() { entries.clear() }

    fun release() { for (e in entries.values) { e.mat?.release(); e.polyps?.release() }; entries.clear() }

    companion object {
        /** Polyp spacing at full density (cm): long-tentacle crowns overlap into a carpet (Tee's photos [P p12]). */
        const val SPACING_CM = 0.65
        /** Polyp budget at full density (≈ 42 triangles each). */
        const val MAX_POLYPS = 1800
        /** Farthest past the mat radius the margin's runners can carry polyps (gspEdge: lobes 1.4 + runners 2.4 cm). */
        const val EDGE_REACH_CM = 3.9f
        const val MAX_POKES = 8
        private val IDENTITY = M.identity()
    }
}
