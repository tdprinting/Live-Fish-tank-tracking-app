package com.reeflive.app.render

import com.reeflive.core.geom.Sdf
import com.reeflive.core.geom.Vec3
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Orbit camera (target, yaw, pitch, distance, fov 50°) plus a follow mode that tracks one organism close up from
 * its [FishRig] pose (ARCHITECTURE §8.2 camera).
 *
 * Gestures move *goals*; the camera eases toward them with critically damped [Spring]s (exact for any dt, so the
 * feel is the same at 30, 60 or 120 fps). Distance is smoothed in log space so zooming feels even at every range.
 * Limits are soft ([SoftLimit]): a drag can push a little past the yaw / pitch / zoom / pan range against growing
 * resistance and the view eases back when the finger lifts. A one-finger release flings the orbit ([Inertia]).
 *
 * Follow mode flies in (slow spring that stiffens over [FLY_IN_S]), frames the fish in profile from the viewer's
 * side at ~3 body lengths, leads in its direction of travel, and lets the user swing round it and pinch closer.
 * The eye is collision-tested against the rock SDF so it never ends up inside rock. Pure Kotlin (no GL); GL thread.
 */
class Camera {
    // ---- orbit goals (moved by gestures) ----
    /** Orbit target goal (cm). */
    val target = floatArrayOf(DEFAULT_TARGET[0], DEFAULT_TARGET[1], DEFAULT_TARGET[2])
    private var yawGoal = 0f
    private var pitchGoal = DEFAULT_PITCH
    private var distLogGoal = ln(DEFAULT_DISTANCE)

    // ---- smoothed orbit state ----
    private val yawS = Spring(OMEGA_TOUCH)
    private val pitchS = Spring(OMEGA_TOUCH)
    private val distLogS = Spring(OMEGA_TOUCH)
    private val targetS = Spring3(OMEGA_TOUCH)
    private val yawInertia = Inertia(FLING_FRICTION)
    private val pitchInertia = Inertia(FLING_FRICTION)

    /** Smoothed yaw (radians; 0 = in front of the tank, +Z). */
    val yaw: Float get() = yawS.value
    val pitch: Float get() = pitchS.value
    /** Smoothed orbit distance in user units (see [effectiveDistance]). */
    val distance: Float get() = exp(distLogS.value)
    val fovY = 50f
    var near = 0.5f; var far = 800f

    /** True while a finger is on the view (GL thread, from [CameraInput.drain]): soft limits only relax when false. */
    var touching = false

    // ---- follow state ----
    var followId: String? = null; private set
    private var followYaw = 0f
    private var followPitch = FOLLOW_PITCH
    private var followZoomLog = 0f
    private val fYawS = Spring(OMEGA_TOUCH)
    private val fPitchS = Spring(OMEGA_TOUCH)
    private val fZoomS = Spring(OMEGA_TOUCH)
    private val fYawInertia = Inertia(FLING_FRICTION)
    private val fPitchInertia = Inertia(FLING_FRICTION)
    /** Seconds since the follow started (fly-in blend). */
    private var followTime = 0f
    /**
     * Smoothed profile heading around the followed fish (critically damped). Headings are wrapped to (−π, π] with
     * 0 = in front (+Z) and kept in the front band, and the spring moves linearly between them, so a flank change
     * always swings the camera round through the front glass, never behind the fish.
     */
    private val followHeading = YawSpring(FOLLOW_YAW_OMEGA, shortestArc = false)
    private val eyeS = Spring3(FOLLOW_OMEGA)
    private val aimS = Spring3(FOLLOW_OMEGA)
    private val fishVel = FloatArray(3)
    private val prevMid = FloatArray(3)
    private var hasPrevMid = false

    // ---- outputs (valid after update) ----
    val eye = FloatArray(3)
    private val aim = FloatArray(3)
    val view = FloatArray(16)
    val proj = FloatArray(16)
    val viewProj = FloatArray(16)
    val invViewProj = FloatArray(16)
    var aspect = 1f; private set
    private var initialized = false

    // ---- scratch (no per-frame allocation) ----
    private val desiredEye = FloatArray(3)
    private val desiredAim = FloatArray(3)
    private val tmpDir = FloatArray(3)
    private val tmpTarget = FloatArray(3)
    private val un = FloatArray(3)

    // ------------------------------------------------------------------ programmatic moves

    /** Animated return to the default view; stops any follow. */
    fun reset() {
        if (followId != null) releaseFollow()
        glide()
        target[0] = DEFAULT_TARGET[0]; target[1] = DEFAULT_TARGET[1]; target[2] = DEFAULT_TARGET[2]
        yawGoal = 0f; pitchGoal = DEFAULT_PITCH; distLogGoal = ln(DEFAULT_DISTANCE)
    }

    /** Square-on, level view of the whole front glass (clean-glass mode); stops any follow. */
    fun faceGlass() {
        if (followId != null) releaseFollow()
        glide()
        target[0] = TANK[0] / 2; target[1] = (SAND_Y + WATER_Y) / 2; target[2] = DEFAULT_TARGET[2]
        yawGoal = 0f; pitchGoal = 0f; distLogGoal = ln(DEFAULT_DISTANCE)
    }

    /**
     * Double tap on the scene: glide in to [x], [y], [z] (cm) at about 40 % of the current distance. A double tap
     * when already close in glides back out to the default framing.
     */
    fun focusOn(x: Float, y: Float, z: Float) {
        if (followId != null) return
        glide()
        if (distLogGoal <= ln(FOCUS_TOGGLE_DISTANCE)) {
            target[0] = DEFAULT_TARGET[0]; target[1] = DEFAULT_TARGET[1]; target[2] = DEFAULT_TARGET[2]
            distLogGoal = ln(DEFAULT_DISTANCE)
            return
        }
        target[0] = x.coerceIn(TARGET_LO[0], TARGET_HI[0])
        target[1] = y.coerceIn(TARGET_LO[1], TARGET_HI[1])
        target[2] = z.coerceIn(TARGET_LO[2], TARGET_HI[2])
        distLogGoal = max(distLogGoal + ln(0.4f), ln(FOCUS_DISTANCE))
    }

    /** Starts (id != null) or stops following. Stopping keeps the current view and resumes orbiting around it. */
    fun follow(id: String?) {
        if (id == followId) return
        if (id == null) { releaseFollow(); return }
        followId = id
        followYaw = 0f; followPitch = FOLLOW_PITCH; followZoomLog = 0f
        fYawS.snap(0f); fPitchS.snap(FOLLOW_PITCH); fZoomS.snap(0f)
        fYawInertia.stop(); fPitchInertia.stop(); yawInertia.stop(); pitchInertia.stop()
        followTime = 0f
        followHeading.reset()
        hasPrevMid = false
        fishVel.fill(0f)
        if (initialized) { eyeS.snap(eye); aimS.snap(aim) }
    }

    /** Double tap while following: toggle between the normal and a closer framing. */
    fun toggleFollowCloseUp() {
        if (followId == null) return
        followZoomLog = if (followZoomLog > ln(0.7f)) ln(0.5f) else 0f
    }

    // ------------------------------------------------------------------ gestures (GL thread, via CameraInput)

    /** A finger touched down: catch any fling. */
    fun catchMotion() {
        yawInertia.stop(); pitchInertia.stop(); fYawInertia.stop(); fPitchInertia.stop()
    }

    /** One-finger drag in pixels. In follow mode it swings the camera around the fish. */
    fun orbit(dxPx: Float, dyPx: Float, viewHeightPx: Float) {
        val k = PI.toFloat() / max(viewHeightPx, 1f)
        if (followId != null) {
            followYaw -= dxPx * k
            followPitch = SoftLimit.drag(followPitch, dyPx * k, FOLLOW_MIN_PITCH, FOLLOW_MAX_PITCH, PITCH_GIVE)
        } else {
            snappy()
            yawGoal = SoftLimit.drag(yawGoal, -dxPx * k, -MAX_YAW, MAX_YAW, YAW_GIVE)
            pitchGoal = SoftLimit.drag(pitchGoal, dyPx * k, MIN_PITCH, MAX_PITCH, PITCH_GIVE)
        }
    }

    /** One-finger release velocity (px/s): keeps the orbit turning and coasting to a stop. */
    fun fling(vxPx: Float, vyPx: Float, viewHeightPx: Float) {
        val k = PI.toFloat() / max(viewHeightPx, 1f)
        val wy = (-vxPx * k).coerceIn(-MAX_SPIN, MAX_SPIN)
        val wp = (vyPx * k * 0.6f).coerceIn(-MAX_SPIN, MAX_SPIN)
        if (followId != null) { fYawInertia.kick(wy); fPitchInertia.kick(wp) } else { yawInertia.kick(wy); pitchInertia.kick(wp) }
    }

    /** Two-finger twist (radians, screen): turns the view about the vertical axis. */
    fun rotate(radians: Float) {
        if (followId != null) followYaw -= radians
        else { snappy(); yawGoal = SoftLimit.drag(yawGoal, -radians, -MAX_YAW, MAX_YAW, YAW_GIVE) }
    }

    /** Pinch: factor > 1 zooms in (about the view centre). */
    fun zoom(factor: Float) {
        if (factor <= 0f) return
        if (followId != null) followZoomLog = SoftLimit.drag(followZoomLog, -ln(factor), FOLLOW_MIN_ZOOM_LOG, FOLLOW_MAX_ZOOM_LOG, ZOOM_GIVE)
        else { snappy(); distLogGoal = SoftLimit.drag(distLogGoal, -ln(factor), ln(MIN_DISTANCE), ln(MAX_DISTANCE), ZOOM_GIVE) }
    }

    /**
     * Pinch about the fingers' focus (px): factor > 1 zooms in, and the point under the fingers stays put (the
     * orbit target slides toward it), so you can zoom straight into a gap in the rockwork.
     */
    fun zoomAt(factor: Float, focusXPx: Float, focusYPx: Float, widthPx: Int, heightPx: Int) {
        if (factor <= 0f || factor.isNaN()) return
        if (followId != null || !initialized) { zoom(factor); return }
        val before = exp(distLogGoal)
        zoom(factor)
        val ratio = exp(distLogGoal) / before
        if (ratio == 1f) return
        ray(focusXPx, focusYPx, widthPx, heightPx, tmpDir)
        val dx = aim[0] - eye[0]; val dy = aim[1] - eye[1]; val dz = aim[2] - eye[2]
        val d = sqrt(dx * dx + dy * dy + dz * dz)
        for (i in 0 until 3) {
            val p = eye[i] + tmpDir[i] * d
            val next = target[i] + (p - target[i]) * (1f - ratio) * FOCUS_FOLLOW
            target[i] = SoftLimit.drag(target[i], next - target[i], TARGET_LO[i], TARGET_HI[i], TARGET_GIVE)
        }
    }

    /** Two-finger drag in pixels: moves the orbit target in the view plane (ignored while following). */
    fun pan(dxPx: Float, dyPx: Float, viewHeightPx: Float) {
        if (followId != null) return
        snappy()
        val wpp = 2f * effectiveDistance * kotlin.math.tan(Math.toRadians(fovY / 2.0).toFloat()) / max(viewHeightPx, 1f)
        val y = yawS.value; val p = pitchS.value
        // camera right = (cos yaw, 0, −sin yaw); camera up = (−sin p sin yaw, cos p, −sin p cos yaw)
        val rx = cos(y); val rz = -sin(y)
        val ux = -sin(p) * sin(y); val uy = cos(p); val uz = -sin(p) * cos(y)
        val d0 = -dxPx * wpp * rx + dyPx * wpp * ux
        val d1 = dyPx * wpp * uy
        val d2 = -dxPx * wpp * rz + dyPx * wpp * uz
        target[0] = SoftLimit.drag(target[0], d0, TARGET_LO[0], TARGET_HI[0], TARGET_GIVE)
        target[1] = SoftLimit.drag(target[1], d1, TARGET_LO[1], TARGET_HI[1], TARGET_GIVE)
        target[2] = SoftLimit.drag(target[2], d2, TARGET_LO[2], TARGET_HI[2], TARGET_GIVE)
    }

    // ------------------------------------------------------------------ per frame

    /**
     * Advances smoothing and rebuilds matrices. [rigFor] resolves the followed organism; when it returns
     * null the follow ends and [onFollowLost] is called once.
     */
    fun update(dt: Float, widthPx: Int, heightPx: Int, sdf: Sdf?, rigFor: (String) -> FishRig?, onFollowLost: (String) -> Unit) {
        aspect = if (heightPx > 0) widthPx.toFloat() / heightPx else 1f
        val step = dt.coerceIn(0f, 0.1f)
        val fid = followId
        val rig = fid?.let(rigFor)
        if (fid != null && rig == null) {
            releaseFollow(); onFollowLost(fid)
        }
        if (!initialized) {
            yawS.snap(yawGoal); pitchS.snap(pitchGoal); distLogS.snap(distLogGoal); targetS.snap(target)
        }
        if (followId != null && rig != null) {
            advanceFollowInput(step)
            followTarget(rig, sdf, desiredEye, desiredAim, step)
            if (!initialized) { eyeS.snap(desiredEye); aimS.snap(desiredAim); initialized = true }
            followTime += step
            val k = smooth01(followTime / FLY_IN_S)
            eyeS.omega = FLY_OMEGA + (FOLLOW_OMEGA - FLY_OMEGA) * k
            aimS.omega = (FLY_OMEGA + (FOLLOW_OMEGA - FLY_OMEGA) * k) * 1.4f
            eyeS.step(desiredEye, step, eye)
            aimS.step(desiredAim, step, aim)
        } else {
            advanceOrbit(step)
            orbitEye(eye)
            targetS.valueInto(aim)
            initialized = true
        }
        // Keep the eye out of rock and out of the sand bed.
        if (sdf != null) pushOut(eye, sdf, CLEARANCE)
        if (eye[0] > 0f && eye[0] < TANK[0] && eye[2] > 0f && eye[2] < TANK[2] && eye[1] < SAND_Y + 1.5f) eye[1] = SAND_Y + 1.5f
        M.lookAt(eye[0], eye[1], eye[2], aim[0], aim[1], aim[2], view)
        val dx = aim[0] - eye[0]; val dy = aim[1] - eye[1]; val dz = aim[2] - eye[2]
        near = (sqrt(dx * dx + dy * dy + dz * dz) * 0.03f).coerceIn(0.25f, 1f)
        M.perspective(fovY, aspect, near, far, proj)
        M.mul(proj, view, viewProj)
        M.invert(viewProj, invViewProj)
    }

    private fun advanceOrbit(dt: Float) {
        if (!touching) {
            // fling momentum, resisted (and braked harder) past the soft limits
            val yo = SoftLimit.overshoot(yawGoal, -MAX_YAW, MAX_YAW)
            val po = SoftLimit.overshoot(pitchGoal, MIN_PITCH, MAX_PITCH)
            val dy = yawInertia.advance(dt, if (yo > 0f) LIMIT_BRAKE else 0f)
            val dp = pitchInertia.advance(dt, if (po > 0f) LIMIT_BRAKE else 0f)
            if (dy != 0f) yawGoal = SoftLimit.drag(yawGoal, dy, -MAX_YAW, MAX_YAW, YAW_GIVE)
            if (dp != 0f) pitchGoal = SoftLimit.drag(pitchGoal, dp, MIN_PITCH, MAX_PITCH, PITCH_GIVE)
            yawGoal = SoftLimit.relax(yawGoal, -MAX_YAW, MAX_YAW, RELAX_RATE, dt)
            pitchGoal = SoftLimit.relax(pitchGoal, MIN_PITCH, MAX_PITCH, RELAX_RATE, dt)
            distLogGoal = SoftLimit.relax(distLogGoal, ln(MIN_DISTANCE), ln(MAX_DISTANCE), RELAX_RATE, dt)
            for (i in 0 until 3) target[i] = SoftLimit.relax(target[i], TARGET_LO[i], TARGET_HI[i], RELAX_RATE, dt)
        }
        yawS.step(yawGoal, dt)
        pitchS.step(pitchGoal, dt)
        distLogS.step(distLogGoal, dt)
        targetS.step(target, dt, tmpTarget)
    }

    private fun advanceFollowInput(dt: Float) {
        if (!touching) {
            followYaw += fYawInertia.advance(dt)
            val po = SoftLimit.overshoot(followPitch, FOLLOW_MIN_PITCH, FOLLOW_MAX_PITCH)
            val dp = fPitchInertia.advance(dt, if (po > 0f) LIMIT_BRAKE else 0f)
            if (dp != 0f) followPitch = SoftLimit.drag(followPitch, dp, FOLLOW_MIN_PITCH, FOLLOW_MAX_PITCH, PITCH_GIVE)
            followPitch = SoftLimit.relax(followPitch, FOLLOW_MIN_PITCH, FOLLOW_MAX_PITCH, RELAX_RATE, dt)
            followZoomLog = SoftLimit.relax(followZoomLog, FOLLOW_MIN_ZOOM_LOG, FOLLOW_MAX_ZOOM_LOG, RELAX_RATE, dt)
        }
        fYawS.step(followYaw, dt)
        fPitchS.step(followPitch, dt)
        fZoomS.step(followZoomLog, dt)
    }

    private fun orbitEye(out: FloatArray) {
        val p = pitchS.value; val y = yawS.value
        val cp = cos(p)
        val d = effectiveDistance
        out[0] = targetS[0] + d * sin(y) * cp
        out[1] = targetS[1] + d * sin(p)
        out[2] = targetS[2] + d * cos(y) * cp
    }

    /** Orbit distance (cm) after fitting the tank to the viewport ([fitDistance]); user zoom scales it. */
    val effectiveDistance: Float get() = distance * fitDistance(aspect) / DEFAULT_DISTANCE

    /**
     * Desired follow eye/aim for [rig]: profile view from the front-glass side, ~3 body lengths away, aimed a little
     * ahead of the fish along its (smoothed) velocity so the frame leads where it is going.
     */
    internal fun followTarget(rig: FishRig, sdf: Sdf?, eyeOut: FloatArray, aimOut: FloatArray, dt: Float = 0f) {
        val bl = rig.scale
        val s = rig.spine
        // forward = head − a point 1/3 down the body
        var fx = s[0] - s[20]; var fy = s[1] - s[21]; var fz = s[2] - s[22]
        val fl = sqrt(fx * fx + fy * fy + fz * fz)
        if (fl < 1e-4f) { fx = 1f; fy = 0f; fz = 0f } else { fx /= fl; fy /= fl; fz /= fl }
        // mid-body (spine index 6 ≈ 0.4 BL behind the head) and its smoothed velocity
        val mx = s[24]; val my = s[25]; val mz = s[26]
        if (hasPrevMid && dt > 1e-4f) {
            val a = 1f - exp(-VEL_SMOOTH * dt)
            fishVel[0] += ((mx - prevMid[0]) / dt - fishVel[0]) * a
            fishVel[1] += ((my - prevMid[1]) / dt - fishVel[1]) * a
            fishVel[2] += ((mz - prevMid[2]) / dt - fishVel[2]) * a
        }
        prevMid[0] = mx; prevMid[1] = my; prevMid[2] = mz; hasPrevMid = true
        var lx = fishVel[0] * LEAD_S; var ly = fishVel[1] * LEAD_S * 0.5f; var lz = fishVel[2] * LEAD_S
        val ll = sqrt(lx * lx + ly * ly + lz * lz)
        val maxLead = bl * MAX_LEAD_BL
        if (ll > maxLead) { val k = maxLead / ll; lx *= k; ly *= k; lz *= k }
        aimOut[0] = mx + lx; aimOut[1] = my + ly; aimOut[2] = mz + lz
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
        val heading = if (!followHeading.initialized) followHeading.snap(profile) else followHeading.step(profile, dt)
        // profile direction, then the user's swing (one-finger drag) and zoom (pinch)
        val base = heading + fYawS.value
        val pitch = fPitchS.value
        val d = (bl * 3.2f).coerceIn(MIN_FOLLOW, MAX_FOLLOW) * exp(fZoomS.value)
        val cp = cos(pitch)
        // orbit about a point half way to the lead, so the eye leads too but less than the aim
        val ox = mx + lx * 0.5f; val oy = my + ly * 0.5f; val oz = mz + lz * 0.5f
        var ex = ox + d * sin(base) * cp
        var ey = oy + d * sin(pitch)
        var ez = oz + d * cos(base) * cp
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
        catchMotion()
        if (!initialized) return
        // continue orbiting from the current view, with no jump: goals and springs start where the eye is
        for (i in 0 until 3) target[i] = aim[i].coerceIn(TARGET_LO[i], TARGET_HI[i])
        val dx = eye[0] - target[0]; val dy = eye[1] - target[1]; val dz = eye[2] - target[2]
        val d = sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(1f)
        val user = (d * DEFAULT_DISTANCE / fitDistance(aspect)).coerceIn(MIN_DISTANCE * 0.7f, MAX_DISTANCE * 1.3f)
        distLogGoal = ln(user)
        pitchGoal = kotlin.math.asin((dy / d).coerceIn(-1f, 1f)).coerceIn(MIN_PITCH - PITCH_GIVE, MAX_PITCH + PITCH_GIVE)
        yawGoal = atan2(dx, dz)
        yawS.snap(yawGoal); pitchS.snap(pitchGoal); distLogS.snap(distLogGoal); targetS.snap(target)
        // then ease (slowly) back inside the soft limits if the follow ended outside them
        glide()
    }

    private fun glide() {
        yawS.omega = OMEGA_GLIDE; pitchS.omega = OMEGA_GLIDE; distLogS.omega = OMEGA_GLIDE; targetS.omega = OMEGA_GLIDE
        yawInertia.stop(); pitchInertia.stop()
    }

    private fun snappy() {
        if (yawS.omega == OMEGA_TOUCH) return
        yawS.omega = OMEGA_TOUCH; pitchS.omega = OMEGA_TOUCH; distLogS.omega = OMEGA_TOUCH; targetS.omega = OMEGA_TOUCH
    }

    /**
     * Projects a world sphere (centre, radius cm) with the current view-projection: [out] = (u, v, radius) in
     * texture coordinates (0..1, v up; radius in v units). False when the centre is behind the camera.
     */
    fun projectSphere(x: Float, y: Float, z: Float, r: Float, out: FloatArray): Boolean {
        val m = viewProj
        val cx = m[0] * x + m[4] * y + m[8] * z + m[12]
        val cy = m[1] * x + m[5] * y + m[9] * z + m[13]
        val cw = m[3] * x + m[7] * y + m[11] * z + m[15]
        if (cw <= 1e-3f) return false
        out[0] = 0.5f + 0.5f * cx / cw
        out[1] = 0.5f + 0.5f * cy / cw
        out[2] = 0.5f * r * proj[5] / cw
        return true
    }

    /** Ray through pixel (x, y): origin = eye, returns normalized direction in [dirOut]. */
    fun ray(xPx: Float, yPx: Float, widthPx: Int, heightPx: Int, dirOut: FloatArray) {
        val nx = 2f * xPx / max(widthPx, 1) - 1f
        val ny = 1f - 2f * yPx / max(heightPx, 1)
        unproject(nx, ny, 1f)
        val fx = un[0]; val fy = un[1]; val fz = un[2]
        unproject(nx, ny, -1f)
        var dx = fx - un[0]; var dy = fy - un[1]; var dz = fz - un[2]
        val l = sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(1e-6f)
        dx /= l; dy /= l; dz /= l
        dirOut[0] = dx; dirOut[1] = dy; dirOut[2] = dz
    }

    private fun unproject(x: Float, y: Float, z: Float) {
        val m = invViewProj
        val rx = m[0] * x + m[4] * y + m[8] * z + m[12]
        val ry = m[1] * x + m[5] * y + m[9] * z + m[13]
        val rz = m[2] * x + m[6] * y + m[10] * z + m[14]
        val rw = (m[3] * x + m[7] * y + m[11] * z + m[15]).let { if (kotlin.math.abs(it) < 1e-9f) 1e-9f else it }
        un[0] = rx / rw; un[1] = ry / rw; un[2] = rz / rw
    }

    companion object {
        val TANK = floatArrayOf(121.9f, 61f, 33f)
        const val SAND_Y = 3f
        const val WATER_Y = 56f
        val DEFAULT_TARGET = floatArrayOf(61f, 27f, 16f)
        const val DEFAULT_DISTANCE = 150f
        const val DEFAULT_PITCH = 0.08f
        /** Closest orbit (user units; ≈ 10 cm from the target in portrait): right up to the rockwork. */
        const val MIN_DISTANCE = 12f
        const val MAX_DISTANCE = 320f
        /** Soft yaw range: a little past square-on to either side glass. */
        const val MAX_YAW = 1.75f
        const val MIN_PITCH = -0.3f
        const val MAX_PITCH = 1.38f
        const val YAW_GIVE = 0.3f
        const val PITCH_GIVE = 0.1f
        /** Zoom give, in log distance. */
        const val ZOOM_GIVE = 0.22f
        const val TARGET_GIVE = 6f
        /** Soft box for the orbit target (cm): the tank interior plus a margin. */
        val TARGET_LO = floatArrayOf(-4f, SAND_Y, -4f)
        val TARGET_HI = floatArrayOf(TANK[0] + 4f, WATER_Y + 2f, TANK[2] + 6f)
        /** Double tap: glide in no closer than this, and when already this close, glide back out. */
        const val FOCUS_DISTANCE = 22f
        const val FOCUS_TOGGLE_DISTANCE = 40f
        /** How much of the pinch-about-focus shift the target takes (1 = the point stays exactly under the fingers). */
        const val FOCUS_FOLLOW = 0.9f

        /** Spring stiffness (1/s) while gestures drive the camera: close to the finger, without jitter. */
        const val OMEGA_TOUCH = 16f
        /** Programmatic glides (reset, double tap, end of follow). */
        const val OMEGA_GLIDE = 4.5f
        /** Soft limits ease back at this rate (1/s) once the finger lifts. */
        const val RELAX_RATE = 7f
        const val FLING_FRICTION = 3.2f
        /** Extra fling braking past a soft limit. */
        const val LIMIT_BRAKE = 12f
        /** Max fling spin (rad/s). */
        const val MAX_SPIN = 5f

        const val MIN_FOLLOW = 7f
        const val MAX_FOLLOW = 45f
        const val FOLLOW_PITCH = 0.12f
        const val FOLLOW_MIN_PITCH = -0.7f
        const val FOLLOW_MAX_PITCH = 1.2f
        val FOLLOW_MIN_ZOOM_LOG = ln(0.3f)
        val FOLLOW_MAX_ZOOM_LOG = ln(3f)
        /** Fly-in: the follow springs stiffen from [FLY_OMEGA] to [FOLLOW_OMEGA] over this many seconds. */
        const val FLY_IN_S = 1.4f
        const val FLY_OMEGA = 2.4f
        const val FOLLOW_OMEGA = 6.5f
        /** Lead the frame by this many seconds of the fish's velocity, at most [MAX_LEAD_BL] body lengths. */
        const val LEAD_S = 0.35f
        const val MAX_LEAD_BL = 0.8f
        /** Fish velocity smoothing rate (1/s). */
        const val VEL_SMOOTH = 4f
        const val CLEARANCE = 1.5f
        /** Follow-heading spring stiffness (rad/s); critically damped, settles in ≈ 4.7 / ω ≈ 1.3 s. */
        const val FOLLOW_YAW_OMEGA = 3.5f
        /** Cross to the other flank once the kept one would view from behind by more than this (cos of the angle off +Z). */
        const val FLANK_SWITCH_COS = 0.35f

        private fun smooth01(x: Float): Float { val t = x.coerceIn(0f, 1f); return t * t * (3f - 2f * t) }

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
