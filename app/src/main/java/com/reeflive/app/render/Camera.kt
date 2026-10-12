package com.reeflive.app.render

import com.reeflive.core.geom.Sdf
import com.reeflive.core.geom.Vec3
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Orbit camera (target, yaw, pitch, distance, fov 50°) plus a follow mode that tracks one organism close
 * up from its [FishRig] pose (ARCHITECTURE §8.2). The follow camera frames the fish in profile from the
 * viewer's side, smooths position and aim exponentially, and is collision-tested against the rock SDF so it
 * never ends up inside rock. Pure Kotlin (no GL); runs on the GL thread.
 */
class Camera {
    // ---- orbit state ----
    val target = floatArrayOf(DEFAULT_TARGET[0], DEFAULT_TARGET[1], DEFAULT_TARGET[2])
    var yaw = 0f; private set          // radians; 0 = in front of the tank (+Z)
    var pitch = DEFAULT_PITCH; private set
    var distance = DEFAULT_DISTANCE; private set
    val fovY = 50f
    var near = 0.5f; var far = 800f

    // ---- follow state ----
    var followId: String? = null; private set
    private var followYaw = 0f
    private var followPitch = 0.12f
    private var followZoom = 1f
    private var followFresh = true
    /**
     * Smoothed profile heading around the followed fish (critically damped). Headings are wrapped to (−π, π] with
     * 0 = in front (+Z) and kept in the front band, and the spring moves linearly between them, so a flank change
     * always swings the camera round through the front glass, never behind the fish.
     */
    private val followHeading = YawSpring(FOLLOW_YAW_OMEGA, shortestArc = false)

    // ---- outputs (valid after update) ----
    val eye = FloatArray(3)
    private val aim = FloatArray(3)
    val view = FloatArray(16)
    val proj = FloatArray(16)
    val viewProj = FloatArray(16)
    val invViewProj = FloatArray(16)
    var aspect = 1f; private set
    private var initialized = false

    fun reset() {
        target[0] = DEFAULT_TARGET[0]; target[1] = DEFAULT_TARGET[1]; target[2] = DEFAULT_TARGET[2]
        yaw = 0f; pitch = DEFAULT_PITCH; distance = DEFAULT_DISTANCE
        followId = null
    }

    /** Starts (id != null) or stops following. Stopping keeps the current view and resumes orbiting around it. */
    fun follow(id: String?) {
        if (id == followId) return
        if (id == null) releaseFollow() else { followId = id; followYaw = 0f; followPitch = 0.12f; followZoom = 1f; followFresh = true; followHeading.reset() }
    }

    /** One-finger drag in pixels. In follow mode it swings the camera around the fish. */
    fun orbit(dxPx: Float, dyPx: Float, viewHeightPx: Float) {
        val k = (PI.toFloat() / max(viewHeightPx, 1f))
        if (followId != null) {
            followYaw -= dxPx * k
            followPitch = (followPitch + dyPx * k).coerceIn(-0.9f, 1.2f)
        } else {
            yaw = (yaw - dxPx * k).coerceIn(-MAX_YAW, MAX_YAW)
            pitch = (pitch + dyPx * k).coerceIn(MIN_PITCH, MAX_PITCH)
        }
    }

    /** Pinch: factor > 1 zooms in. */
    fun zoom(factor: Float) {
        if (factor <= 0f) return
        if (followId != null) followZoom = (followZoom / factor).coerceIn(0.4f, 3f)
        else distance = (distance / factor).coerceIn(MIN_DISTANCE, MAX_DISTANCE)
    }

    /** Two-finger drag in pixels: moves the orbit target in the view plane (ignored while following). */
    fun pan(dxPx: Float, dyPx: Float, viewHeightPx: Float) {
        if (followId != null) return
        val worldPerPx = 2f * effectiveDistance * kotlin.math.tan(Math.toRadians(fovY / 2.0).toFloat()) / max(viewHeightPx, 1f)
        // camera right = (cos yaw, 0, -sin yaw); up ≈ world Y
        target[0] = (target[0] - dxPx * worldPerPx * cos(yaw)).coerceIn(0f, TANK[0])
        target[2] = (target[2] + dxPx * worldPerPx * sin(yaw)).coerceIn(0f, TANK[2])
        target[1] = (target[1] + dyPx * worldPerPx).coerceIn(0f, TANK[1])
    }

    /**
     * Advances smoothing and rebuilds matrices. [rigFor] resolves the followed organism; when it returns
     * null the follow ends and [onFollowLost] is called once.
     */
    fun update(dt: Float, widthPx: Int, heightPx: Int, sdf: Sdf?, rigFor: (String) -> FishRig?, onFollowLost: (String) -> Unit) {
        aspect = if (heightPx > 0) widthPx.toFloat() / heightPx else 1f
        val desiredEye = FloatArray(3); val desiredAim = FloatArray(3)
        val fid = followId
        val rig = fid?.let(rigFor)
        var rate = 6f
        if (fid != null && rig == null) {
            releaseFollow(); onFollowLost(fid)
        }
        if (followId != null && rig != null) {
            followTarget(rig, sdf, desiredEye, desiredAim, dt.coerceIn(0f, 0.1f))
            rate = if (followFresh) 2.5f else 5f
        } else {
            orbitEye(desiredEye); desiredAim[0] = target[0]; desiredAim[1] = target[1]; desiredAim[2] = target[2]
        }
        if (!initialized) {
            desiredEye.copyInto(eye); desiredAim.copyInto(aim); initialized = true
        } else {
            val a = 1f - exp(-rate * dt.coerceIn(0f, 0.1f))
            for (i in 0 until 3) { eye[i] += (desiredEye[i] - eye[i]) * a; aim[i] += (desiredAim[i] - aim[i]) * a }
            if (followFresh && dist(eye, desiredEye) < 2f) followFresh = false
        }
        // Keep the eye out of rock even mid-transition.
        if (sdf != null) pushOut(eye, sdf, CLEARANCE)
        M.lookAt(eye[0], eye[1], eye[2], aim[0], aim[1], aim[2], view)
        near = if (followId != null) 0.3f else 1f
        M.perspective(fovY, aspect, near, far, proj)
        M.mul(proj, view, viewProj)
        M.invert(viewProj, invViewProj)
    }

    private fun orbitEye(out: FloatArray) {
        val cp = cos(pitch)
        val d = effectiveDistance
        out[0] = target[0] + d * sin(yaw) * cp
        out[1] = target[1] + d * sin(pitch)
        out[2] = target[2] + d * cos(yaw) * cp
    }

    /** Orbit distance after fitting the tank to the viewport ([fitDistance]); user zoom scales it. */
    val effectiveDistance: Float get() = distance * fitDistance(aspect) / DEFAULT_DISTANCE

    /** Desired follow eye/aim for [rig]: profile view from the front-glass side, ~3 body lengths away. */
    internal fun followTarget(rig: FishRig, sdf: Sdf?, eyeOut: FloatArray, aimOut: FloatArray, dt: Float = 0f) {
        val bl = rig.scale
        val s = rig.spine
        // forward = head − a point 1/3 down the body
        var fx = s[0] - s[20]; var fy = s[1] - s[21]; var fz = s[2] - s[22]
        val fl = sqrt(fx * fx + fy * fy + fz * fz)
        if (fl < 1e-4f) { fx = 1f; fy = 0f; fz = 0f } else { fx /= fl; fy /= fl; fz /= fl }
        // aim at mid-body (spine index 6 ≈ 0.4 BL behind the head)
        aimOut[0] = s[24]; aimOut[1] = s[25]; aimOut[2] = s[26]
        // horizontal side vector (either flank); a fish heading straight up/down keeps the last heading
        var sx = -fz; var sz = fx
        val sl = sqrt(sx * sx + sz * sz)
        val profile: Float
        if (sl < 1e-3f) {
            profile = if (followHeading.initialized) followHeading.value else 0f
        } else {
            sx /= sl; sz /= sl
            // Keep the flank the camera is already on (no 180° flip each time the fish turns through
            // facing the glass); only cross over once it would look in from well behind (through the
            // back glass / rock). A fresh follow starts on the viewer's (+Z) side.
            val a = atan2(sx, sz)
            val b = wrapAngle(a + PI.toFloat())
            profile = if (!followHeading.initialized) {
                if (sz >= 0f) a else b
            } else {
                val cur = followHeading.target
                var pick = if (kotlin.math.abs(a - cur) <= kotlin.math.abs(b - cur)) a else b
                if (cos(pick) < -FLANK_SWITCH_COS) pick = if (pick == a) b else a
                pick
            }
        }
        // Smooth the heading with a critically damped spring so turns swing the camera instead of stepping it.
        val heading = if (!followHeading.initialized || dt <= 0f) followHeading.snap(profile) else followHeading.step(profile, dt)
        // profile direction, then the user's swing (one-finger drag)
        val base = heading + followYaw
        val d = (bl * 3.2f).coerceIn(MIN_FOLLOW, MAX_FOLLOW) * followZoom
        val cp = cos(followPitch)
        var ex = aimOut[0] + d * sin(base) * cp
        var ey = aimOut[1] + d * sin(followPitch)
        var ez = aimOut[2] + d * cos(base) * cp
        // stay inside the tank sides/sand/surface, but may sit outside the front glass
        ex = ex.coerceIn(1f, TANK[0] - 1f); ey = ey.coerceIn(SAND_Y + 1f, WATER_Y - 1f); ez = ez.coerceIn(1f, TANK[2] + 60f)
        if (sdf != null) {
            // march from the aim point toward the eye; stop CLEARANCE before rock
            var dx = ex - aimOut[0]; var dy = ey - aimOut[1]; var dz = ez - aimOut[2]
            val len = sqrt(dx * dx + dy * dy + dz * dz)
            if (len > 1e-3f) {
                dx /= len; dy /= len; dz /= len
                val hit = marchClear(sdf, aimOut[0], aimOut[1], aimOut[2], dx, dy, dz, len, CLEARANCE)
                val t = max(minOf(hit, len), MIN_FOLLOW * 0.4f)
                ex = aimOut[0] + dx * t; ey = aimOut[1] + dy * t; ez = aimOut[2] + dz * t
            }
        }
        eyeOut[0] = ex; eyeOut[1] = ey; eyeOut[2] = ez
    }

    private fun releaseFollow() {
        followId = null
        if (!initialized) return
        // continue orbiting from the current view
        aim.copyInto(target)
        for (i in 0 until 3) target[i] = target[i].coerceIn(0f, TANK[i])
        val dx = eye[0] - target[0]; val dy = eye[1] - target[1]; val dz = eye[2] - target[2]
        val d = sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(1f)
        distance = (d * DEFAULT_DISTANCE / fitDistance(aspect)).coerceIn(MIN_DISTANCE, MAX_DISTANCE)
        pitch = kotlin.math.asin((dy / d).coerceIn(-1f, 1f)).coerceIn(MIN_PITCH, MAX_PITCH)
        yaw = atan2(dx, dz).coerceIn(-MAX_YAW, MAX_YAW)
    }

    /** Ray through pixel (x, y): origin = eye, returns normalized direction in [dirOut]. */
    fun ray(xPx: Float, yPx: Float, widthPx: Int, heightPx: Int, dirOut: FloatArray) {
        val nx = 2f * xPx / max(widthPx, 1) - 1f
        val ny = 1f - 2f * yPx / max(heightPx, 1)
        val far4 = unproject(nx, ny, 1f); val near4 = unproject(nx, ny, -1f)
        var dx = far4[0] - near4[0]; var dy = far4[1] - near4[1]; var dz = far4[2] - near4[2]
        val l = sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(1e-6f)
        dx /= l; dy /= l; dz /= l
        dirOut[0] = dx; dirOut[1] = dy; dirOut[2] = dz
    }

    private fun unproject(x: Float, y: Float, z: Float): FloatArray {
        val m = invViewProj
        val rx = m[0] * x + m[4] * y + m[8] * z + m[12]
        val ry = m[1] * x + m[5] * y + m[9] * z + m[13]
        val rz = m[2] * x + m[6] * y + m[10] * z + m[14]
        val rw = (m[3] * x + m[7] * y + m[11] * z + m[15]).let { if (kotlin.math.abs(it) < 1e-9f) 1e-9f else it }
        return floatArrayOf(rx / rw, ry / rw, rz / rw)
    }

    companion object {
        val TANK = floatArrayOf(121.9f, 61f, 33f)
        const val SAND_Y = 3f
        const val WATER_Y = 56f
        val DEFAULT_TARGET = floatArrayOf(61f, 27f, 16f)
        const val DEFAULT_DISTANCE = 150f
        const val DEFAULT_PITCH = 0.08f
        const val MIN_DISTANCE = 25f
        const val MAX_DISTANCE = 300f
        const val MAX_YAW = 1.35f
        const val MIN_PITCH = -0.25f
        const val MAX_PITCH = 1.3f
        const val MIN_FOLLOW = 7f
        const val MAX_FOLLOW = 45f
        const val CLEARANCE = 1.5f
        /** Follow-heading spring stiffness (rad/s); critically damped, settles in ≈ 4.7 / ω ≈ 1.3 s. */
        const val FOLLOW_YAW_OMEGA = 3.5f
        /** Cross to the other flank once the kept one would view from behind by more than this (cos of the angle off +Z). */
        const val FLANK_SWITCH_COS = 0.35f

        /** Wraps an angle in radians to (−π, π]. */
        fun wrapAngle(a: Float): Float {
            val twoPi = (2.0 * PI).toFloat()
            var r = a % twoPi
            if (r > PI.toFloat()) r -= twoPi
            if (r <= -PI.toFloat()) r += twoPi
            return r
        }

        /**
         * Default orbit distance that makes the whole front glass fill the viewport of [aspect] (w / h): close in
         * for short, wide viewports (landscape, or the tank area above the docked sandbox panel) so the tank is as
         * large as possible, never further out than [DEFAULT_DISTANCE] (tall portrait keeps today's framing).
         */
        fun fitDistance(aspect: Float): Float {
            val tanHalf = kotlin.math.tan(Math.toRadians(50.0 / 2.0)).toFloat()
            val front = TANK[2] - DEFAULT_TARGET[2]          // front glass is this much nearer than the target
            val byHeight = TANK[1] * 1.04f / (2f * tanHalf)
            val byWidth = TANK[0] * 1.04f / (2f * tanHalf * max(aspect, 0.1f))
            return (front + max(byHeight, byWidth)).coerceIn(MIN_FIT_DISTANCE, DEFAULT_DISTANCE)
        }
        const val MIN_FIT_DISTANCE = 80f

        private fun dist(a: FloatArray, b: FloatArray): Float {
            val dx = a[0] - b[0]; val dy = a[1] - b[1]; val dz = a[2] - b[2]
            return sqrt(dx * dx + dy * dy + dz * dz)
        }

        /** Sphere-traces from (ox,oy,oz) along unit (dx,dy,dz); returns the distance where clearance is lost (or maxT). */
        fun marchClear(sdf: Sdf, ox: Float, oy: Float, oz: Float, dx: Float, dy: Float, dz: Float, maxT: Float, clearance: Float): Float {
            var t = 0f
            // If the start is already tight (fish nestled in rock) skip ahead until clear.
            var i = 0
            while (t < maxT && i < 96) {
                val s = sdf.dist(Vec3((ox + dx * t).toDouble(), (oy + dy * t).toDouble(), (oz + dz * t).toDouble())).toFloat() - clearance
                if (s < 0.02f && t > 0.5f) return t
                t += max(s, 0.25f)
                i++
            }
            return maxT
        }

        fun pushOut(p: FloatArray, sdf: Sdf, clearance: Float) {
            repeat(4) {
                val v = Vec3(p[0].toDouble(), p[1].toDouble(), p[2].toDouble())
                val d = sdf.dist(v).toFloat()
                if (d >= clearance) return
                val g = sdf.gradient(v)
                val push = clearance - d
                p[0] += (g.x * push).toFloat(); p[1] += (g.y * push).toFloat(); p[2] += (g.z * push).toFloat()
            }
        }
    }
}

/**
 * Critically damped spring on an angle (radians), stable for any dt (exact solution of the damped ODE); from rest
 * it never overshoots. Targets and [value] are wrapped to (−π, π]. With [shortestArc] the spring takes the short
 * way across the ±π seam; without it, it moves linearly inside (−π, π] (never crossing ±π), which the follow
 * camera uses so its heading always swings through the front (0).
 */
class YawSpring(val omega: Float, val shortestArc: Boolean = true) {
    var value = 0f; private set
    var velocity = 0f; private set
    /** The last target, wrapped. */
    var target = 0f; private set
    var initialized = false; private set

    fun reset() { initialized = false; value = 0f; velocity = 0f; target = 0f }

    fun snap(angle: Float): Float {
        value = Camera.wrapAngle(angle); target = value; velocity = 0f; initialized = true
        return value
    }

    fun step(angle: Float, dt: Float): Float {
        if (!initialized) return snap(angle)
        target = Camera.wrapAngle(angle)
        if (dt <= 0f) return value
        // x = offset from the target along the shortest arc; closed-form critically damped update
        val x0 = if (shortestArc) Camera.wrapAngle(value - target) else value - target
        val w = omega
        val e = exp(-w * dt)
        val c = velocity + w * x0
        val x1 = (x0 + c * dt) * e
        velocity = (c - w * (x0 + c * dt)) * e
        value = if (shortestArc) Camera.wrapAngle(target + x1) else (target + x1).coerceIn(-PI.toFloat(), PI.toFloat())
        return value
    }
}
