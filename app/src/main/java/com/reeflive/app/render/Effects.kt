package com.reeflive.app.render

import android.opengl.GLES30
import com.reeflive.core.geom.FlowGrid
import com.reeflive.core.sim.SimSnapshot
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * CPU particle field (ARCHITECTURE §8.4 item 9, SIMULATION §5.4): suspended particulate advected by the
 * flow (the snapshot's [FlowGrid] when the Navier-Stokes solver runs, a gentle analytic gyre otherwise)
 * plus Brownian jitter; sinking detritus (as much as the water's detritus load); bubbles entrained by the HOB return;
 * the sim's food particles, each kind with its own sprite and motion ([FoodVisuals]); and the action bursts and pump
 * jets ([BurstField]). Output is packed as (pos.xyz, size cm, r, g, b, a, shape, seed, angle, param) per point in
 * five ranges. Pure Kotlin.
 */
class ParticleField(var ambientCount: Int, seed: Int = 7) {
    private val rnd = Random(seed)
    private val amb = FloatArray(MAX_AMBIENT * 3)
    private val det = FloatArray(DETRITUS * 3)
    private val bub = FloatArray(MAX_BUBBLES * 4) // xyz, vy
    private var bubbles = 0
    private var bubbleAcc = 0f
    private val foodFx = FoodVisuals()
    /** Action bursts and pump jets; spawn into it with [burst]. */
    val bursts = BurstField()
    private var viewProj = FloatArray(16)

    /** Packed output; each range is (first, count) in points. */
    val out = FloatArray(CAPACITY * FLOATS)
    var ambientRange = 0 to 0; private set
    var bubbleRange = 0 to 0; private set
    /** Exactly one point per snapshot food particle, in snapshot order. */
    var foodRange = 0 to 0; private set
    /** Fish feces pellets ([WasteVisuals]), after the food. */
    var wasteRange = 0 to 0; private set
    /** Thawing cubes, the nori sheet and solid burst sprites (bubbles, streaks, sparkles, pods). */
    var fxRange = 0 to 0; private set
    /** Large soft clouds: phyto and powder food, dye plumes, silt. */
    var hazeRange = 0 to 0; private set
    private var k = 0

    init {
        for (i in 0 until MAX_AMBIENT) respawn(amb, i)
        for (i in 0 until DETRITUS) respawn(det, i)
    }

    private fun respawn(a: FloatArray, i: Int) {
        a[i * 3] = rnd.nextFloat() * (Camera.TANK[0] - 2f) + 1f
        a[i * 3 + 1] = rnd.nextFloat() * (Camera.WATER_Y - Camera.SAND_Y - 1f) + Camera.SAND_Y + 0.5f
        a[i * 3 + 2] = rnd.nextFloat() * (Camera.TANK[2] - 2f) + 1f
    }

    /** Starts an action burst (GL thread). */
    fun burst(b: TankBurst, snap: SimSnapshot?) = bursts.spawn(b, snap)

    /** [viewProjM] (column-major) orients streak sprites along their motion; omit it in tests. */
    fun step(dtIn: Float, time: Float, snap: SimSnapshot?, flowOn: Boolean = true, viewProjM: FloatArray? = null) {
        if (viewProjM != null) viewProj = viewProjM
        val dt = dtIn.coerceIn(0f, 0.1f)
        val grid = snap?.flowGrid
        val strength = (snap?.flowStrength ?: 1f) * (if (flowOn) 1f else 0.2f)
        val v = FloatArray(3)
        val n = ambientCount.coerceIn(0, MAX_AMBIENT)
        // time-lapse cue: while the sim runs fast the suspended specks race with it (1x → 1, 60x → ~3.7, 1000x → 5.5)
        val ff = fastForward(snap?.speed ?: 1.0)
        for (i in 0 until n) {
            flowAt(grid, amb[i * 3], amb[i * 3 + 1], amb[i * 3 + 2], time, v)
            for (c in 0 until 3) amb[i * 3 + c] += (v[c] * strength * 0.6f + (rnd.nextFloat() - 0.5f) * 2f) * dt * ff
            if (!inWater(amb, i)) respawn(amb, i)
        }
        for (i in 0 until DETRITUS) {
            flowAt(grid, det[i * 3], det[i * 3 + 1], det[i * 3 + 2], time, v)
            det[i * 3] += v[0] * strength * 0.3f * dt * ff
            det[i * 3 + 1] += (v[1] * 0.2f - 0.25f) * dt * ff
            det[i * 3 + 2] += v[2] * strength * 0.3f * dt * ff
            if (det[i * 3 + 1] < Camera.SAND_Y + 0.2f || !inWater(det, i)) { respawn(det, i); det[i * 3 + 1] = Camera.WATER_Y - 2f - rnd.nextFloat() * 30f }
        }
        // HOB return (equipment.glb ReturnNozzle `outflow` (73, 37.4, 2.3), back middle, video T97): bubbles plunge, slow down, then rise
        bubbleAcc += dt * 8f
        while (bubbleAcc >= 1f && bubbles < MAX_BUBBLES) {
            bubbleAcc -= 1f
            val b = bubbles++ * 4
            bub[b] = HOB[0] + (rnd.nextFloat() - 0.5f) * 6f; bub[b + 1] = HOB[1] - 0.5f; bub[b + 2] = HOB[2] + rnd.nextFloat() * 2f
            bub[b + 3] = -(10f + rnd.nextFloat() * 10f)
        }
        var w = 0
        for (i in 0 until bubbles) {
            val b = i * 4
            bub[b + 3] += 22f * dt                          // buoyancy
            bub[b + 3] = bub[b + 3].coerceAtMost(14f)
            bub[b + 1] += bub[b + 3] * dt
            bub[b] += sin(time * 6f + i) * 1.5f * dt
            bub[b + 2] += 3f * dt                           // pushed forward by the return jet
            if (bub[b + 1] < Camera.WATER_Y - 0.3f || bub[b + 3] < 0f) {
                if (w != i) System.arraycopy(bub, b, bub, w * 4, 4)
                w++
            }
        }
        bubbles = w
        bursts.step(dt, time, snap)
        pack(snap, time)
    }

    private fun inWater(a: FloatArray, i: Int): Boolean {
        val x = a[i * 3]; val y = a[i * 3 + 1]; val z = a[i * 3 + 2]
        return x in 0.3f..(Camera.TANK[0] - 0.3f) && y in Camera.SAND_Y..(Camera.WATER_Y - 0.3f) && z in 0.3f..(Camera.TANK[2] - 0.3f)
    }

    private val sink = PointSink { x, y, z, size, r, g, b, a, shape, seed, angle, param ->
        if (k < CAPACITY) k = put(k, x, y, z, size, r, g, b, a, shape, seed, angle, param)
    }

    private fun pack(snap: SimSnapshot?, time: Float) {
        k = 0
        // haze first: it sits behind the sharper sprites in the same blended pass
        if (snap != null) foodFx.packExtras(snap, time, haze = sink, solid = NULL_SINK, maxHaze = MAX_FOOD_HAZE)
        bursts.pack(viewProj, time, haze = sink, solid = NULL_SINK, maxHaze = MAX_BURST_HAZE, maxSolid = 0)
        hazeRange = 0 to k
        val a0 = k
        val n = ambientCount.coerceIn(0, MAX_AMBIENT)
        for (i in 0 until n) k = put(k, amb[i * 3], amb[i * 3 + 1], amb[i * 3 + 2], 0.07f, 0.85f, 0.92f, 1.0f, 0.15f)
        // suspended muck follows the water: a water change, the sock or the skimmer thin it out, overfeeding thickens it
        val load = ((snap?.water?.detritus ?: 0.2).toFloat() / 0.2f).coerceIn(0.3f, 1f)
        for (i in 0 until (DETRITUS * load).toInt()) k = put(k, det[i * 3], det[i * 3 + 1], det[i * 3 + 2], 0.1f, 0.45f, 0.36f, 0.24f, 0.35f)
        ambientRange = a0 to (k - a0)
        val b0 = k
        for (i in 0 until bubbles) k = put(k, bub[i * 4], bub[i * 4 + 1], bub[i * 4 + 2], 0.18f + (i % 3) * 0.05f, 0.95f, 0.98f, 1f, 0.45f)
        bubbleRange = b0 to (k - b0)
        val x0 = k
        if (snap != null) foodFx.packExtras(snap, time, haze = NULL_SINK, solid = sink, maxHaze = 0)
        bursts.pack(viewProj, time, haze = NULL_SINK, solid = sink, maxHaze = 0, maxSolid = BurstField.CAP)
        fxRange = x0 to (k - x0)
        val f0 = k
        if (snap != null) foodFx.packBodies(snap, time, MAX_FOOD, sink)
        foodRange = f0 to (k - f0)
        val w0 = k
        if (snap != null) WasteVisuals.pack(snap.waste, MAX_WASTE, sink)
        wasteRange = w0 to (k - w0)
    }

    private fun put(
        k: Int, x: Float, y: Float, z: Float, size: Float, r: Float, g: Float, b: Float, a: Float,
        shape: Float = Shape.DOT, seed: Float = 0f, angle: Float = 0f, param: Float = 1f,
    ): Int {
        val o = k * FLOATS
        out[o] = x; out[o + 1] = y; out[o + 2] = z; out[o + 3] = size
        out[o + 4] = r; out[o + 5] = g; out[o + 6] = b; out[o + 7] = a
        out[o + 8] = shape; out[o + 9] = seed; out[o + 10] = angle; out[o + 11] = param
        return k + 1
    }

    companion object {
        const val FLOATS = 12
        const val MAX_AMBIENT = 300
        const val DETRITUS = 40
        const val MAX_BUBBLES = 120
        const val MAX_FOOD = 400
        const val MAX_WASTE = com.reeflive.core.sim.WasteSystem.MAX
        /** Haze puffs for fine foods (one per particle at most). */
        const val MAX_FOOD_HAZE = 160
        const val MAX_BURST_HAZE = 200
        /** Cubes (one per frozen kind), the nori sheet (clip + tiles). */
        private const val MAX_FOOD_EXTRA = 16 + FoodVisuals.NORI_COLS * FoodVisuals.NORI_ROWS
        const val CAPACITY = MAX_AMBIENT + DETRITUS + MAX_BUBBLES + MAX_FOOD + MAX_FOOD_HAZE + MAX_BURST_HAZE + MAX_FOOD_EXTRA + BurstField.CAP + MAX_WASTE
        /** HOB return on the tank on screen ([TankRenderer] moves it with the glass for a My tank build). */
        val HOB = floatArrayOf(73f, Camera.WATER_Y - 0.7f, 2.3f)
        private val NULL_SINK = PointSink { _, _, _, _, _, _, _, _, _, _, _, _ -> }

        /** How much faster suspended specks drift at sim [speed] (log-scaled, capped so they stay readable). */
        fun fastForward(speed: Double): Float = (1.0 + 1.5 * kotlin.math.log10(speed.coerceAtLeast(1.0))).toFloat().coerceIn(1f, 5.5f)

        /** Flow velocity (cm/s) at a point: trilinear in the solver grid, or an analytic slow gyre. */
        fun flowAt(g: FlowGrid?, x: Float, y: Float, z: Float, t: Float, out: FloatArray) {
            if (g != null && g.vel.size >= g.nx * g.ny * g.nz * 3) {
                val fx = (x / g.cell - 0.5f).coerceIn(0f, g.nx - 1.001f)
                val fy = ((y - g.yOrigin) / g.cell - 0.5f).coerceIn(0f, g.ny - 1.001f)
                val fz = (z / g.cell - 0.5f).coerceIn(0f, g.nz - 1.001f)
                val i = fx.toInt(); val j = fy.toInt(); val k = fz.toInt()
                val tx = fx - i; val ty = fy - j; val tz = fz - k
                for (c in 0 until 3) {
                    fun at(a: Int, b: Int, d: Int) = g.vel[((a + g.nx * (b + g.ny * d)) * 3) + c]
                    val i1 = minOf(i + 1, g.nx - 1); val j1 = minOf(j + 1, g.ny - 1); val k1 = minOf(k + 1, g.nz - 1)
                    val c00 = at(i, j, k) + (at(i1, j, k) - at(i, j, k)) * tx
                    val c10 = at(i, j1, k) + (at(i1, j1, k) - at(i, j1, k)) * tx
                    val c01 = at(i, j, k1) + (at(i1, j, k1) - at(i, j, k1)) * tx
                    val c11 = at(i, j1, k1) + (at(i1, j1, k1) - at(i, j1, k1)) * tx
                    out[c] = (c00 + (c10 - c00) * ty) + ((c01 + (c11 - c01) * ty) - (c00 + (c10 - c00) * ty)) * tz
                }
                return
            }
            // analytic: return circulation (+x near the top, −x near the bottom) with slow wobble
            val s = if (y > 30f) 1.5f else -1.5f
            out[0] = s + 0.6f * sin(z * 0.2f + t * 0.3f)
            out[1] = 0.4f * sin(x * 0.05f + t * 0.2f)
            out[2] = 0.5f * cos(x * 0.04f + t * 0.25f)
        }
    }
}

/** GL side of the particles: one dynamic VBO, five ranges drawn as points (one draw call each). */
class ParticleRenderer {
    private val vao = Gl.genVertexArray()
    private val vbo = Gl.genBuffer()
    private val capacity = ParticleField.CAPACITY
    private val buffer = Gl.floats(FloatArray(capacity * ParticleField.FLOATS))

    init {
        val stride = ParticleField.FLOATS * 4
        GLES30.glBindVertexArray(vao); Gl.check("glBindVertexArray(particles)")
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo); Gl.check("glBindBuffer(particles)")
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, capacity * stride, null, GLES30.GL_DYNAMIC_DRAW); Gl.check("glBufferData(particles)")
        GLES30.glEnableVertexAttribArray(0); Gl.check("enable0")
        GLES30.glVertexAttribPointer(0, 4, GLES30.GL_FLOAT, false, stride, 0); Gl.check("ptr0")
        GLES30.glEnableVertexAttribArray(1); Gl.check("enable1")
        GLES30.glVertexAttribPointer(1, 4, GLES30.GL_FLOAT, false, stride, 16); Gl.check("ptr1")
        GLES30.glEnableVertexAttribArray(2); Gl.check("enable2")
        GLES30.glVertexAttribPointer(2, 4, GLES30.GL_FLOAT, false, stride, 32); Gl.check("ptr2")
        GLES30.glBindVertexArray(0); Gl.check("unbind")
    }

    /** Uploads [f] and draws; returns the number of draw calls issued. */
    fun draw(p: ShaderProgram, f: ParticleField, viewportH: Int): Int {
        val total = f.wasteRange.first + f.wasteRange.second
        if (total == 0) return 0
        buffer.position(0); buffer.put(f.out, 0, total * ParticleField.FLOATS); buffer.position(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo); Gl.check("glBindBuffer(particles)")
        GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, 0, total * ParticleField.FLOATS * 4, buffer); Gl.check("glBufferSubData(particles)")
        p.use()
        p.set1f("uViewportH", viewportH.toFloat())
        GLES30.glBindVertexArray(vao); Gl.check("glBindVertexArray(particles)")
        var calls = 0
        for ((range, clamp) in listOf(
            f.hazeRange to (4f to 96f),
            f.ambientRange to (1.5f to 4f),
            f.bubbleRange to (2f to 8f),
            f.fxRange to (2f to 40f),
            f.foodRange to (2f to 32f),
            f.wasteRange to (1.5f to 28f),
        )) {
            if (range.second == 0) continue
            p.set2f("uSizeClamp", clamp.first, clamp.second)
            GLES30.glDrawArrays(GLES30.GL_POINTS, range.first, range.second); Gl.check("glDrawArrays(points)")
            calls++
        }
        GLES30.glBindVertexArray(0); Gl.check("unbind")
        return calls
    }

    fun release() { Gl.deleteVertexArray(vao); Gl.deleteBuffer(vbo) }
}

/** Light shafts: 6 billboarded quads per fixture (static geometry; animation is in the shader). */
class LightShafts {
    private val vao = Gl.genVertexArray()
    private val vbo = Gl.genBuffer()
    private val ibo = Gl.genBuffer()
    private val indexCount: Int

    init {
        val rnd = Random(11)
        val verts = ArrayList<Float>()
        val idx = ArrayList<Short>()
        var base = 0
        for (light in 0 until 2) for (s in 0 until 6) {
            val ox = (rnd.nextFloat() - 0.5f) * 18f; val oz = (rnd.nextFloat() - 0.5f) * 10f
            val width = 3f + rnd.nextFloat() * 5f; val seed = rnd.nextFloat()
            for ((cx, cy) in listOf(-1f to 0f, 1f to 0f, -1f to 1f, 1f to 1f)) {
                verts += listOf(cx, cy, s.toFloat(), light.toFloat(), ox, width, oz, seed)
            }
            idx += listOf(base, base + 2, base + 1, base + 1, base + 2, base + 3).map { it.toShort() }
            base += 4
        }
        indexCount = idx.size
        GLES30.glBindVertexArray(vao); Gl.check("glBindVertexArray(shafts)")
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo); Gl.check("glBindBuffer(shafts)")
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, verts.size * 4, Gl.floats(verts.toFloatArray()), GLES30.GL_STATIC_DRAW); Gl.check("glBufferData(shafts)")
        GLES30.glEnableVertexAttribArray(0); Gl.check("enable0")
        GLES30.glVertexAttribPointer(0, 4, GLES30.GL_FLOAT, false, 32, 0); Gl.check("ptr0")
        GLES30.glEnableVertexAttribArray(1); Gl.check("enable1")
        GLES30.glVertexAttribPointer(1, 4, GLES30.GL_FLOAT, false, 32, 16); Gl.check("ptr1")
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ibo); Gl.check("glBindBuffer(shafts ibo)")
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, idx.size * 2, Gl.shorts(idx.toShortArray()), GLES30.GL_STATIC_DRAW); Gl.check("glBufferData(shafts ibo)")
        GLES30.glBindVertexArray(0); Gl.check("unbind")
    }

    fun draw(p: ShaderProgram) {
        p.use()
        GLES30.glBindVertexArray(vao); Gl.check("glBindVertexArray(shafts)")
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, indexCount, GLES30.GL_UNSIGNED_SHORT, 0); Gl.check("glDrawElements(shafts)")
        GLES30.glBindVertexArray(0); Gl.check("unbind")
    }

    val triangles: Int get() = indexCount / 3

    fun release() { Gl.deleteVertexArray(vao); Gl.deleteBuffer(vbo); Gl.deleteBuffer(ibo) }
}
