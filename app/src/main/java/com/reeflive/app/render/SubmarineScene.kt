package com.reeflive.app.render

import com.reeflive.core.sim.SubPose
import com.reeflive.core.sim.Submarine
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Procedural meshes for the toy submarine (Submarine mode): nose along +X, up +Y, starboard +Z, about
 * [Submarine.LENGTH_CM] long. Pure Kotlin (STRIDE-12 vertices, as [Placeholder]).
 */
object SubmarineMesh {
    class Part(val vertices: FloatArray, val indices: IntArray)

    private class Builder {
        val v = ArrayList<Float>(); val i = ArrayList<Int>()
        fun vertex(x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float, u: Float, w: Float) {
            v += x; v += y; v += z; v += nx; v += ny; v += nz; v += u; v += w; v += 1f; v += 1f; v += 1f; v += 1f
        }
        val count: Int get() = v.size / GlbPrimitive.STRIDE

        /** An ellipsoid at (cx, cy, cz) with radii (rx, ry, rz), turned [rollX] radians about the X axis. */
        fun ellipsoid(cx: Float, cy: Float, cz: Float, rx: Float, ry: Float, rz: Float, rollX: Float = 0f, rings: Int = 10, segs: Int = 14) {
            val base = count
            val cr = cos(rollX); val sr = sin(rollX)
            for (a in 0..rings) {
                val th = (PI * a / rings).toFloat()
                for (b in 0..segs) {
                    val ph = (2 * PI * b / segs).toFloat()
                    val ux = cos(th); val uy = sin(th) * cos(ph); val uz = sin(th) * sin(ph)
                    val px = cx + rx * ux; val py0 = cy + ry * uy; val pz0 = cz + rz * uz
                    var nx = ux / rx; var ny = uy / ry; var nz = uz / rz
                    val nl = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-5f); nx /= nl; ny /= nl; nz /= nl
                    // roll about X (position and normal)
                    val py = py0 * cr - pz0 * sr; val pz = py0 * sr + pz0 * cr
                    val qy = ny * cr - nz * sr; val qz = ny * sr + nz * cr
                    vertex(px, py, pz, nx, qy, qz, b.toFloat() / segs, a.toFloat() / rings)
                }
            }
            for (a in 0 until rings) for (b in 0 until segs) {
                val p = base + a * (segs + 1) + b; val q = p + segs + 1
                i += p; i += q; i += p + 1; i += p + 1; i += q; i += q + 1
            }
        }

        /** An open cone along +X from radius [r0] at x0 to [r1] at x1 (the headlight beam). */
        fun cone(x0: Float, x1: Float, r0: Float, r1: Float, y: Float, segs: Int = 18) {
            val base = count
            for (e in 0..1) for (b in 0..segs) {
                val ph = (2 * PI * b / segs).toFloat()
                val r = if (e == 0) r0 else r1
                vertex(if (e == 0) x0 else x1, y + r * cos(ph), r * sin(ph), 0f, cos(ph), sin(ph), b.toFloat() / segs, e.toFloat())
            }
            for (b in 0 until segs) { val p = base + b; val q = p + segs + 1; i += p; i += q; i += p + 1; i += p + 1; i += q; i += q + 1 }
        }

        fun build() = Part(v.toFloatArray(), i.toIntArray())
    }

    private const val H = (Submarine.LENGTH_CM / 2).toFloat()

    /** Yellow hull, sail, periscope and tail fins. */
    fun hull(): Part = Builder().apply {
        ellipsoid(0f, 0f, 0f, H, 1.0f, 1.0f, rings = 14, segs = 18)
        ellipsoid(0.35f, 0.85f, 0f, 0.75f, 0.6f, 0.36f)
        ellipsoid(0.55f, 1.55f, 0f, 0.09f, 0.4f, 0.09f, segs = 8)
        ellipsoid(0.75f, 1.9f, 0f, 0.22f, 0.08f, 0.08f, segs = 8)
        ellipsoid(-H + 0.35f, 0f, 0f, 0.42f, 0.8f, 0.06f, rings = 6, segs = 10)
        ellipsoid(-H + 0.35f, 0f, 0f, 0.42f, 0.06f, 0.8f, rings = 6, segs = 10)
        // side dive planes on the sail
        ellipsoid(0.45f, 0.95f, 0f, 0.22f, 0.04f, 0.65f, rings = 5, segs = 8)
    }.build()

    /** Glass: the big nose window and two portholes a side. */
    fun windows(): Part = Builder().apply {
        ellipsoid(H - 0.32f, 0.12f, 0f, 0.36f, 0.5f, 0.6f)
        for (s in listOf(-1f, 1f)) for (x in listOf(-0.5f, 0.6f)) ellipsoid(x, 0.12f, 0.93f * s, 0.24f, 0.24f, 0.08f, segs = 10)
    }.build()

    /** Hub and three blades, centred on the origin, spinning about X. */
    fun prop(): Part = Builder().apply {
        ellipsoid(0f, 0f, 0f, 0.22f, 0.18f, 0.18f, segs = 10)
        for (k in 0 until 3) ellipsoid(-0.02f, 0.42f, 0f, 0.05f, 0.42f, 0.17f, rollX = (2 * PI * k / 3).toFloat(), rings = 6, segs = 8)
    }.build()

    /** The headlamp under the nose window. */
    fun lamp(): Part = Builder().apply { ellipsoid(H - 0.15f, -0.45f, 0f, 0.16f, 0.17f, 0.2f, segs = 10) }.build()

    /** Nested beam shells (soft edge), from the lamp forward. */
    fun beam(): Part = Builder().apply {
        cone(H, H + 12f, 0.2f, 2.6f, -0.45f)
        cone(H, H + 10f, 0.25f, 3.6f, -0.45f)
        cone(H, H + 8f, 0.3f, 4.6f, -0.45f)
    }.build()

    const val PROP_X = -(Submarine.LENGTH_CM / 2).toFloat() - 0.12f
}

/**
 * The submarine in the renderer (GL thread): interpolates [SubPose] between snapshots, draws the hull with the generic
 * program (a faint ghost when it is invisible to the fish), spins the propeller, lights the headlamp and beam, and
 * sets up the ride camera's goals for [Camera] (which smooths them and keeps the eye out of everything):
 * - chase: behind and a little above along a smoothed heading; while watching a fish ([SubPose.watchId]) it looks over
 *   the sub's shoulder at the fish instead, so the fish and the sub share the frame;
 * - first person: from inside the nose, looking ahead, or straight at the watched fish.
 * Allocation-free per frame.
 */
internal class SubmarineScene {
    private var hull: Mesh? = null
    private var windows: Mesh? = null
    private var prop: Mesh? = null
    private var lamp: Mesh? = null
    private var beam: Mesh? = null
    private val model = FloatArray(16)
    private val propModel = FloatArray(16)
    private val spin = M.identity()
    private val pos = FloatArray(3)
    private var yaw = 0f; private var pitch = 0f; private var roll = 0f; private var propAngle = 0f
    private var has = false
    var visible = true; private set
    /** 1 = solid; below 1 the hull fades so it never blocks the view when the camera is pushed up close ([nearFade]). */
    private var fade = 1f
    /** Drawn in the opaque pass (seen and not faded); otherwise it is drawn see-through after the particles. */
    val opaque: Boolean get() = visible && fade >= 0.999f
    var headlight = true; private set
    val active: Boolean get() = has
    val eye = FloatArray(3)
    val aim = FloatArray(3)
    /** The sub's centre (the chase camera's line of sight starts here) and its smoothed velocity (cm/s). */
    val anchor = FloatArray(3)
    val vel = FloatArray(3)
    /** Half the hull axis (nose minus centre, cm), for the eye's collision capsule. */
    val halfAxis = FloatArray(3)
    private val lastPos = FloatArray(3)
    private var hasLast = false
    /** Chase heading, smoothed so a quick turn swings the camera round instead of snapping it. */
    private val chaseYaw = YawSpring(CHASE_YAW_OMEGA)
    /** Smoothed body centre of the watched fish (no tail-beat wag in the framing). */
    private val fishS = Spring3(FISH_OMEGA)
    private var hasFish = false
    private val fishRaw = FloatArray(3)
    private val fishOut = FloatArray(3)

    fun release() { listOf(hull, windows, prop, lamp, beam).forEach { it?.release() }; hull = null; windows = null; prop = null; lamp = null; beam = null }

    private fun ensure() {
        if (hull != null) return
        fun mk(p: SubmarineMesh.Part) = Mesh.create(p.vertices, p.indices)
        hull = mk(SubmarineMesh.hull()); windows = mk(SubmarineMesh.windows()); prop = mk(SubmarineMesh.prop())
        lamp = mk(SubmarineMesh.lamp()); beam = mk(SubmarineMesh.beam())
    }

    /**
     * Interpolated pose for this frame (from the previous and current snapshots) and the ride camera's goals; [watched]
     * is the rig of the fish the sub is watching, if any. False = no sub.
     */
    fun update(prev: SubPose?, cur: SubPose?, alpha: Float, firstPerson: Boolean, dt: Float, watched: FishRig?): Boolean {
        if (cur == null) { has = false; hasLast = false; hasFish = false; chaseYaw.reset(); return false }
        val a = if (prev == null) 1f else alpha
        val p = prev ?: cur
        pos[0] = lerp(p.pos.x, cur.pos.x, a); pos[1] = lerp(p.pos.y, cur.pos.y, a); pos[2] = lerp(p.pos.z, cur.pos.z, a)
        yaw = lerpAngle(p.yaw, cur.yaw, a); pitch = lerp(p.pitch, cur.pitch, a); roll = lerp(p.roll, cur.roll, a)
        propAngle = lerpAngle(p.prop, cur.prop, a)
        visible = cur.visible; headlight = cur.headlight
        has = true
        // smoothed velocity of the drawn sub (for the camera's feed-forward)
        if (hasLast && dt > 1e-4f) {
            val k = 1f - kotlin.math.exp(-VEL_SMOOTH * dt)
            for (i in 0 until 3) vel[i] += ((pos[i] - lastPos[i]) / dt - vel[i]) * k
        } else vel.fill(0f)
        pos.copyInto(lastPos); hasLast = true
        anchor[0] = pos[0]; anchor[1] = pos[1]; anchor[2] = pos[2]
        buildModel()
        val hl = (Submarine.LENGTH_CM / 2).toFloat() - 1f
        halfAxis[0] = model[0] * hl; halfAxis[1] = model[1] * hl; halfAxis[2] = model[2] * hl
        val fish = if (watched != null && cur.watchId != null) watched else null
        if (fish != null) {
            val sp = fish.spine
            var cx = 0f; var cy = 0f; var cz = 0f
            for (i in 1..9) { cx += sp[i * 4]; cy += sp[i * 4 + 1]; cz += sp[i * 4 + 2] }
            fishRaw[0] = cx / 9f; fishRaw[1] = cy / 9f; fishRaw[2] = cz / 9f
            if (!hasFish || dt <= 0f) { fishS.snap(fishRaw); hasFish = true } else fishS.step(fishRaw, dt, fishOut)
        } else hasFish = false
        if (chaseYaw.initialized) chaseYaw.step(yaw, dt) else chaseYaw.snap(yaw)
        aimCamera(firstPerson, fish != null)
        return true
    }

    private fun buildModel() {
        val cp = cos(pitch); val fx = cos(yaw) * cp; val fy = sin(pitch); val fz = -sin(yaw) * cp
        // right = f x Y, up = r x f, then roll about f
        var rx = -fz; var rz = fx; val rl = sqrt(rx * rx + rz * rz).coerceAtLeast(1e-5f); rx /= rl; rz /= rl
        var ux = -rz * fy; var uy = rz * fx - rx * fz; var uz = rx * fy
        val cr = cos(roll); val sr = sin(roll)
        val r2x = rx * cr + ux * sr; val r2y = uy * sr; val r2z = rz * cr + uz * sr
        ux = ux * cr - rx * sr; uy = uy * cr; uz = uz * cr - rz * sr
        model[0] = fx; model[1] = fy; model[2] = fz; model[3] = 0f
        model[4] = ux; model[5] = uy; model[6] = uz; model[7] = 0f
        model[8] = r2x; model[9] = r2y; model[10] = r2z; model[11] = 0f
        model[12] = pos[0]; model[13] = pos[1]; model[14] = pos[2]; model[15] = 1f
        // propeller: at the tail, turned about the hull axis
        val c = cos(propAngle); val s = sin(propAngle)
        spin[5] = c; spin[6] = s; spin[9] = -s; spin[10] = c; spin[12] = SubmarineMesh.PROP_X
        M.mul(model, spin, propModel)
    }

    private fun aimCamera(firstPerson: Boolean, watching: Boolean) {
        val fx = model[0]; val fy = model[1]; val fz = model[2]
        if (firstPerson) {
            // inside the hull (the hull keeps clear of rock by its radius), looking ahead or at the watched fish
            eye[0] = pos[0] + fx * FP_NOSE; eye[1] = pos[1] + 0.2f + fy * FP_NOSE; eye[2] = pos[2] + fz * FP_NOSE
            if (watching) { aim[0] = fishS[0]; aim[1] = fishS[1]; aim[2] = fishS[2] }
            else { aim[0] = eye[0] + fx * FP_LOOK; aim[1] = eye[1] + fy * FP_LOOK; aim[2] = eye[2] + fz * FP_LOOK }
            return
        }
        if (watching) {
            // over the sub's shoulder: behind it (away from the fish), off to one side and a little up, so the sub sits
            // low in a corner and the fish fills the middle; aimed most of the way to the fish
            var dx = pos[0] - fishS[0]; var dz = pos[2] - fishS[2]
            val l = sqrt(dx * dx + dz * dz)
            if (l < 1e-3f) { dx = -cos(chaseYaw.value); dz = sin(chaseYaw.value) } else { dx /= l; dz /= l }
            eye[0] = pos[0] + dx * WATCH_BACK - dz * WATCH_SIDE; eye[1] = pos[1] + WATCH_UP; eye[2] = pos[2] + dz * WATCH_BACK + dx * WATCH_SIDE
            aim[0] = pos[0] + (fishS[0] - pos[0]) * WATCH_AIM; aim[1] = pos[1] + (fishS[1] - pos[1]) * WATCH_AIM; aim[2] = pos[2] + (fishS[2] - pos[2]) * WATCH_AIM
            return
        }
        val h = chaseYaw.value
        aim[0] = pos[0] + cos(h) * 3f; aim[1] = pos[1] + 0.6f; aim[2] = pos[2] - sin(h) * 3f
        eye[0] = pos[0] - cos(h) * CHASE_BACK; eye[1] = pos[1] + CHASE_UP; eye[2] = pos[2] + sin(h) * CHASE_BACK
    }

    /**
     * Fades the hull when the (collided) camera eye ends up close to it, so it never fills the screen, and while it sits
     * in the line of sight to the watched fish, so it never hides the fish. Eased over [dt] so it never pops.
     */
    fun nearFade(camEye: FloatArray, dt: Float) {
        val dx = camEye[0] - anchor[0]; val dy = camEye[1] - anchor[1]; val dz = camEye[2] - anchor[2]
        val d = sqrt(dx * dx + dy * dy + dz * dz)
        var goal = (FADE_MIN + (1f - FADE_MIN) * ((d - FADE_NEAR) / (FADE_FAR - FADE_NEAR))).coerceIn(FADE_MIN, 1f)
        if (hasFish) {
            // distance from the sub to the eye → fish sight line (only between them)
            val sx = fishS[0] - camEye[0]; val sy = fishS[1] - camEye[1]; val sz = fishS[2] - camEye[2]
            val ss = sx * sx + sy * sy + sz * sz
            if (ss > 1e-4f) {
                val t = ((-dx) * sx + (-dy) * sy + (-dz) * sz) / ss
                if (t > 0f && t < 1f) {
                    val px = camEye[0] + sx * t - anchor[0]; val py = camEye[1] + sy * t - anchor[1]; val pz = camEye[2] + sz * t - anchor[2]
                    val off = sqrt(px * px + py * py + pz * pz)
                    val k = ((off - BLOCK_NEAR) / (BLOCK_FAR - BLOCK_NEAR)).coerceIn(0f, 1f)
                    goal = min(goal, BLOCK_ALPHA + (1f - BLOCK_ALPHA) * k)
                }
            }
        }
        fade = if (dt <= 0f) goal else goal + (fade - goal) * exp(-FADE_RATE * dt)
    }

    /** Opaque (or ghost) hull, windows, prop and lamp with the generic program. Returns draw calls. */
    fun draw(p: ShaderProgram, white: Texture, firstPerson: Boolean, time: Float): Int {
        if (!has || firstPerson) return 0
        ensure()
        p.use()
        white.bind(0)
        p.set1f("uHasTexture", 0f); p.set1f("uAlphaCutoff", 0f); p.set1f("uLedDies", 0f)
        val a = min(if (visible) 1f else GHOST_ALPHA, fade)
        // a ghost glows faintly blue so the pilot still sees where it is
        val gl = if (visible) 0f else 0.25f
        var calls = 0
        calls += part(p, hull, model, a, 1f, 0.78f, 0.1f, 0.35f, 0.06f + gl * 0.3f, 0.04f + gl * 0.6f, gl)
        calls += part(p, windows, model, a, 0.2f, 0.55f, 0.75f, 0.1f, 0.05f, 0.18f + 0.04f * sin(time * 2f), 0.28f)
        calls += part(p, prop, propModel, a, 0.55f, 0.58f, 0.62f, 0.3f, gl * 0.3f, gl * 0.5f, gl)
        val lampOn = if (headlight) 1.6f else 0f
        calls += part(p, lamp, model, a, 0.9f, 0.9f, 0.8f, 0.2f, lampOn, lampOn * 0.95f, lampOn * 0.7f)
        return calls
    }

    private fun part(p: ShaderProgram, m: Mesh?, mat: FloatArray, a: Float, r: Float, g: Float, b: Float, rough: Float, er: Float, eg: Float, eb: Float): Int {
        val mesh = m ?: return 0
        p.setMat4("uModel", mat)
        p.set4f("uBaseColor", r, g, b, a)
        p.set1f("uRoughness", rough)
        p.set3f("uEmissive", er, eg, eb)
        mesh.draw()
        return 1
    }

    /** The headlight beam, additive, after the opaque scene. Returns draw calls. */
    fun drawBeam(p: ShaderProgram, white: Texture): Int {
        if (!has || !headlight) return 0
        val m = beam ?: return 0
        p.use()
        white.bind(0)
        p.set1f("uHasTexture", 0f); p.set1f("uAlphaCutoff", 0f); p.set1f("uLedDies", 0f)
        p.setMat4("uModel", model)
        p.set4f("uBaseColor", 0f, 0f, 0f, BEAM_ALPHA)
        p.set1f("uRoughness", 1f)
        p.set3f("uEmissive", 0.9f, 0.85f, 0.6f)
        m.draw()
        return 1
    }

    val triangles: Int get() = (hull?.triangles ?: 0) + (windows?.triangles ?: 0) + (prop?.triangles ?: 0) + (lamp?.triangles ?: 0)

    private fun lerp(a: Double, b: Double, t: Float) = (a + (b - a) * t).toFloat()
    private fun lerpAngle(a: Double, b: Double, t: Float): Float {
        var d = b - a
        while (d > PI) d -= 2 * PI
        while (d < -PI) d += 2 * PI
        return (a + d * t).toFloat()
    }

    companion object {
        const val CHASE_BACK = 10f
        const val CHASE_UP = 3.2f
        /** Watching: the eye this far behind the sub (away from the fish) and above it; the aim this share of the way to the fish. */
        const val WATCH_BACK = 6f
        const val WATCH_SIDE = 5f
        const val WATCH_UP = 3.5f
        const val WATCH_AIM = 0.85f
        /** Camera-to-sub distances (cm) over which the hull fades out: solid beyond FADE_FAR, faint at FADE_NEAR. */
        const val FADE_NEAR = 3f
        const val FADE_FAR = 6.5f
        const val FADE_MIN = 0.22f
        /** The hull in the sight line to the watched fish: faded to BLOCK_ALPHA within BLOCK_NEAR cm of it, clear past BLOCK_FAR. */
        const val BLOCK_NEAR = 1.6f
        const val BLOCK_FAR = 3.2f
        const val BLOCK_ALPHA = 0.3f
        /** How quickly (1/s) the hull eases to its fade. */
        const val FADE_RATE = 6f
        /** First person: the eye this far forward of the hull centre (inside its radius), looking this far ahead. */
        const val FP_NOSE = 0.6f
        const val FP_LOOK = 10f
        const val CHASE_YAW_OMEGA = 3.2f
        const val FISH_OMEGA = 10f
        const val VEL_SMOOTH = 6f
        const val GHOST_ALPHA = 0.35f
        const val BEAM_ALPHA = 0.07f
    }
}
