package com.reeflive.app.render

import com.reeflive.core.geom.EyeCollider
import com.reeflive.core.geom.Sdf
import com.reeflive.core.geom.Vec3
import com.reeflive.core.sim.TankDims
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.min
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
 * side at ~3 body lengths, and tracks a smoothed body centre (not the wagging head) with velocity feed-forward so the
 * springs do not trail behind a swimming fish. The user can swing round it, pinch closer, and pinch / drag releases
 * coast ([Inertia]); [nudgeZoom] / [nudgeOrbit] are the on-screen buttons.
 *
 * Collision ([colliders], an [EyeCollider]): the eye is a small ball that is carried from where it was to where the
 * springs want it with a sphere trace that slides along surfaces, so it can never pass through rock, sand, corals,
 * equipment or animals (the glass is clamped separately and may be looked through). The follow framing also casts
 * from the fish toward the eye and pulls in before anything in the way. Clearances always exceed the near-plane
 * corner distance, so nothing close can be cut open by the near plane. Pure Kotlin (no GL); GL thread.
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
    /** Smoothed body centre of the followed fish (filters the swimming wag out of the framing). */
    private val bodyS = Spring3(BODY_OMEGA)
    private var hasBody = false
    /**
     * Line-of-sight pull-in as a fraction of the framing distance (1 = clear). It drops at once when something comes
     * between the fish and the eye and eases back out at [LOS_RELEASE] when it is clear again, so the pull-in never
     * pumps in and out as the view passes a coral branch. A ratio, so zooming does not lag behind it.
     */
    private var losRatio = 1f

    // ---- collision ----
    /** What the eye must not go into (rock, sand, corals, equipment, animals). The renderer fills it every frame. */
    val colliders = EyeCollider().apply {
        lengthX = TANK[0]; depthZ = TANK[2]
        sandFront = com.reeflive.core.sim.SandBed.BASE_FRONT.toFloat(); sandBack = com.reeflive.core.sim.SandBed.BASE_BACK.toFloat()
    }
    /** The collided eye, carried from frame to frame. */
    private val ball = FloatArray(3)
    private var ballOn = false
    private var stuckS = 0f
    /** Followed animal's capsule tag in [colliders] (the line-of-sight cast starts inside it, so it skips it). */
    var followOwnerTag = FOLLOW_OWNER

    // ---- zoom inertia (pinch release) ----
    private val zoomInertia = Inertia(ZOOM_FRICTION)
    private var zoomPending = 0f
    private var zoomRate = 0f
    private var wasTouching = false

    // ---- supply shelf (SupplyShelf.kt): a second framing beside the tank, blended over the tank view ----
    /** True while the camera looks at (or travels to) the supply shelf; drags then look along the shelf rows. */
    var shelfOn = false; private set
    /** Looking at the nursery tank beside the display (core NurseryTank); any drag, pinch or reset glides back. */
    var nurseryOn = false; private set
    private var nurseryMix = 0f
    /** Looking at the music corner left of the tank (MusicCorner.kt); a drag, pinch or reset glides back. */
    var musicOn = false; private set
    /** Travel progress to the music corner (0 the tank view, 1 there). */
    var musicMix = 0f; private set
    /**
     * Submarine mode ([SubmarineScene]): while on, the eye and aim ease to [rideEye] / [rideAim] (set every frame) in
     * place of the orbit or follow camera; turning it off glides back to the default tank view.
     */
    var rideOn = false
    val rideEye = FloatArray(3)
    val rideAim = FloatArray(3)
    private var riding = false
    private val rideEyeS = Spring3(RIDE_OMEGA)
    private val rideAimS = Spring3(RIDE_OMEGA * 1.5f)
    /** The sub's centre (the chase view's line of sight starts there) and velocity (cm/s, fed forward past the springs' lag). */
    val rideAnchor = FloatArray(3)
    val rideVel = FloatArray(3)
    /** Riding inside the sub (first person): tighter springs and clearances, no line-of-sight pull-in. */
    var rideFirstPerson = false
    /** A drag while riding looks round (swings the chase view round the sub); it eases back when the finger lifts. */
    private var rideYaw = 0f
    private var ridePitch = 0f
    private val rideYawS = Spring(OMEGA_TOUCH)
    private val ridePitchS = Spring(OMEGA_TOUCH)
    private var rideLos = 1f
    /** Travel progress: 0 = the tank view, 1 = the shelf view (eased with smoothstep, arcing up and back). */
    var shelfMix = 0f; private set
    private val shelfGoal = floatArrayOf(SupplyShelfLayout.START_X, SupplyShelfLayout.START_Y)
    private val shelfX = Spring(OMEGA_TOUCH)
    private val shelfY = Spring(OMEGA_TOUCH)
    private val shelfEye = FloatArray(3)
    private val shelfAim = FloatArray(3)

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
        nurseryOn = false
        musicOn = false
        if (followId != null) releaseFollow()
        glide()
        if (storeOn) {
            // in a store: the aisle view, the tank on its stand with the neighbours either side
            target[0] = TANK[0] / 2; target[1] = STORE_TARGET_Y; target[2] = DEFAULT_TARGET[2]
            yawGoal = 0f; pitchGoal = STORE_PITCH; distLogGoal = ln(storeAisleDistance())
            return
        }
        target[0] = DEFAULT_TARGET[0]; target[1] = DEFAULT_TARGET[1]; target[2] = DEFAULT_TARGET[2]
        yawGoal = 0f; pitchGoal = DEFAULT_PITCH; distLogGoal = ln(DEFAULT_DISTANCE)
    }

    /** A step back and a little to the side to see the tank on its stand in its room (the room shop's try-on). */
    fun roomView() {
        if (storeOn || shelfOn) return
        nurseryOn = false
        musicOn = false
        if (followId != null) releaseFollow()
        glide()
        target[0] = TANK[0] / 2; target[1] = ROOM_TARGET_Y; target[2] = DEFAULT_TARGET[2]
        yawGoal = ROOM_YAW; pitchGoal = ROOM_PITCH; distLogGoal = ln(storeAisleDistance())
    }

    // ---- fish store (StoreRoom.kt): the aisle around the live display tank ----
    /** In a store: the orbit's limits keep the eye inside the shop, [reset] frames the aisle. */
    var storeOn = false; private set
    /** Where the walk is heading: the counter is one or more bays to the right of the live tank (cm along x). */
    var storeFocusX = 0f; private set
    private val storeShift = Spring(OMEGA_WALK)

    private val maxYaw: Float get() = if (storeOn) STORE_MAX_YAW else MAX_YAW
    private val minPitch: Float get() = if (storeOn) STORE_MIN_PITCH else MIN_PITCH
    private val maxPitch: Float get() = if (storeOn) STORE_MAX_PITCH else MAX_PITCH
    private val maxDistance: Float get() = if (storeOn) STORE_MAX_EFFECTIVE * DEFAULT_DISTANCE / fitDistance(aspect) else MAX_DISTANCE

    /** Aisle framing (user units): the stand and tank top to floor, and a slice of the bays either side. */
    private fun storeAisleDistance(): Float {
        val tanHalf = kotlin.math.tan(Math.toRadians(fovY / 2.0)).toFloat()
        val want = max(STORE_FIT_HEIGHT / tanHalf, STORE_FIT_HALF_WIDTH / (tanHalf * max(aspect, 0.1f))) + DEFAULT_TARGET[2]
        return want.coerceAtMost(STORE_MAX_EFFECTIVE * 0.9f) * DEFAULT_DISTANCE / fitDistance(aspect)
    }

    /** Walks into a store ([on]: the aisle view) or home again (the tank view). Stops any follow. */
    fun enterStore(on: Boolean) {
        if (followId != null) releaseFollow()
        storeOn = on
        storeFocusX = 0f
        storeShift.snap(0f)
        reset()
    }

    /**
     * The live tank changed to another bay: the room re-centres on it at once, so the eye jumps by [dx] (cm) to stay
     * where it was, then walks over to the new tank.
     */
    fun storeWalk(dx: Float) {
        if (!storeOn) return
        storeShift.snap(storeShift.value + dx)
    }

    /** Walks to [x] (cm along the aisle from the live tank): the counter, or 0 for the tank. */
    fun storeFocus(x: Float) {
        storeFocusX = x
        if (x != 0f) reset()
    }

    /** A tap on the live tank from the aisle: a close look at it (the normal tank view). False when already close. */
    fun storeZoomIn(): Boolean {
        if (!storeOn || storeFocusX != 0f || effectiveDistance < STORE_CLOSE_EFFECTIVE) return false
        glide()
        target[0] = DEFAULT_TARGET[0]; target[1] = DEFAULT_TARGET[1]; target[2] = DEFAULT_TARGET[2]
        yawGoal = 0f; pitchGoal = DEFAULT_PITCH; distLogGoal = ln(DEFAULT_DISTANCE)
        return true
    }

    /**
     * Swaps to the supply shelf beside the tank ([on]) or back to the tank. The camera glides there in
     * [SHELF_TRAVEL_S] on an arc, or cuts at once with [instant] (Reduce Motion). Going back also eases the tank view
     * back to its default framing, so the action picture that follows is in view. Stops any follow.
     */
    fun lookAtShelf(on: Boolean, instant: Boolean) {
        if (on) {
            if (followId != null) releaseFollow()
            yawInertia.stop(); pitchInertia.stop()
            if (!shelfOn) {
                shelfGoal[0] = SupplyShelfLayout.START_X; shelfGoal[1] = SupplyShelfLayout.START_Y
                shelfX.snap(shelfGoal[0]); shelfY.snap(shelfGoal[1])
            }
        } else if (shelfOn) {
            reset()
        }
        shelfOn = on
        if (instant) shelfMix = if (on) 1f else 0f
    }

    /** Glides the shelf view to one row (0 feed, 1 care, 2 tools). */
    fun shelfFocusRow(row: Int) {
        if (!shelfOn) return
        shelfX.omega = OMEGA_GLIDE; shelfY.omega = OMEGA_GLIDE
        shelfGoal[1] = SupplyShelfLayout.rowLookY(row).coerceIn(SupplyShelfLayout.LOOK_Y_LO, SupplyShelfLayout.LOOK_Y_HI)
    }

    /** One-finger drag on the shelf: the shelf follows the finger, along the rows and between them. */
    private fun shelfDrag(dxPx: Float, dyPx: Float, viewHeightPx: Float) {
        shelfX.omega = OMEGA_TOUCH; shelfY.omega = OMEGA_TOUCH
        val cmPerPx = 2f * shelfDistance * kotlin.math.tan(Math.toRadians(fovY / 2.0).toFloat()) / max(viewHeightPx, 1f)
        shelfGoal[0] = SoftLimit.drag(shelfGoal[0], -dxPx * cmPerPx, SupplyShelfLayout.LOOK_X_LO, SupplyShelfLayout.LOOK_X_HI, SHELF_GIVE)
        shelfGoal[1] = SoftLimit.drag(shelfGoal[1], dyPx * cmPerPx, SupplyShelfLayout.LOOK_Y_LO, SupplyShelfLayout.LOOK_Y_HI, SHELF_GIVE)
    }

    /** Shelf viewing distance (cm from the front of the boards): close in, further out on tall narrow screens. */
    val shelfDistance: Float
        get() = max(SHELF_DISTANCE, SHELF_MIN_WIDTH / (2f * kotlin.math.tan(Math.toRadians(fovY / 2.0).toFloat()) * max(aspect, 0.1f)))

    /**
     * Blends the nursery framing (in front of the nursery tank, a little above) over [eye] / [aim] on the shelf's
     * travel arc. True while the eye is mostly there (the display's glass clamp must not pull it back).
     */
    private fun blendNursery(dt: Float): Boolean {
        val goal = if (nurseryOn) 1f else 0f
        if (nurseryMix != goal) {
            val d = dt / SHELF_TRAVEL_S
            nurseryMix = if (goal > nurseryMix) kotlin.math.min(goal, nurseryMix + d) else max(goal, nurseryMix - d)
        }
        if (nurseryMix <= 0f) return false
        val c = com.reeflive.core.sim.NurseryTank.CENTER
        val dist = 62f
        val e = smooth01(nurseryMix)
        val ex = c.x.toFloat() + 6f; val ey = c.y.toFloat() + 9f; val ez = com.reeflive.core.sim.NurseryTank.Z1.toFloat() + dist
        val ax = c.x.toFloat(); val ay = c.y.toFloat() - 1f; val az = c.z.toFloat()
        eye[0] += (ex - eye[0]) * e; eye[1] += (ey - eye[1]) * e; eye[2] += (ez - eye[2]) * e
        aim[0] += (ax - aim[0]) * e; aim[1] += (ay - aim[1]) * e; aim[2] += (az - aim[2]) * e
        eye[1] += sin(PI.toFloat() * e) * SHELF_ARC_UP
        return nurseryMix > 0.5f
    }

    /**
     * Blends the music corner framing (the record stand and the radio, square on and a little above) over [eye] /
     * [aim] on the same arc. True while the eye is mostly there.
     */
    private fun blendMusic(dt: Float): Boolean {
        val goal = if (musicOn) 1f else 0f
        if (musicMix != goal) {
            val d = dt / SHELF_TRAVEL_S
            musicMix = if (goal > musicMix) kotlin.math.min(goal, musicMix + d) else max(goal, musicMix - d)
        }
        if (musicMix <= 0f) return false
        val a = MusicCornerLayout.VIEW_AIM
        val tanHalf = kotlin.math.tan(Math.toRadians(fovY / 2.0).toFloat())
        val dist = max(MusicCornerLayout.VIEW_HALF_H / tanHalf, MusicCornerLayout.VIEW_HALF_W / (tanHalf * max(aspect, 0.1f)))
        val e = smooth01(musicMix)
        val ex = a[0] + dist * 0.08f; val ey = a[1] + dist * 0.22f; val ez = a[2] + dist
        eye[0] += (ex - eye[0]) * e; eye[1] += (ey - eye[1]) * e; eye[2] += (ez - eye[2]) * e
        aim[0] += (a[0] - aim[0]) * e; aim[1] += (a[1] - aim[1]) * e; aim[2] += (a[2] - aim[2]) * e
        eye[1] += sin(PI.toFloat() * e) * SHELF_ARC_UP
        return musicMix > 0.5f
    }

    /** Advances the shelf travel and look springs, then blends the shelf framing over [eye] / [aim]. */
    private fun blendShelf(dt: Float) {
        val goal = if (shelfOn) 1f else 0f
        if (shelfMix != goal) {
            val d = dt / SHELF_TRAVEL_S
            shelfMix = if (goal > shelfMix) kotlin.math.min(goal, shelfMix + d) else max(goal, shelfMix - d)
        }
        if (!touching) {
            shelfGoal[0] = SoftLimit.relax(shelfGoal[0], SupplyShelfLayout.LOOK_X_LO, SupplyShelfLayout.LOOK_X_HI, RELAX_RATE, dt)
            shelfGoal[1] = SoftLimit.relax(shelfGoal[1], SupplyShelfLayout.LOOK_Y_LO, SupplyShelfLayout.LOOK_Y_HI, RELAX_RATE, dt)
        }
        val lx = shelfX.step(shelfGoal[0], dt)
        val ly = shelfY.step(shelfGoal[1], dt)
        if (shelfMix <= 0f) return
        val dist = shelfDistance
        shelfEye[0] = lx; shelfEye[1] = ly + dist * SHELF_EYE_RISE; shelfEye[2] = SupplyShelfLayout.Z_FRONT + dist
        shelfAim[0] = lx; shelfAim[1] = ly; shelfAim[2] = SupplyShelfLayout.Z_PROP
        val e = smooth01(shelfMix)
        val arc = sin(PI.toFloat() * e)
        for (i in 0 until 3) {
            eye[i] += (shelfEye[i] - eye[i]) * e
            aim[i] += (shelfAim[i] - aim[i]) * e
        }
        eye[1] += arc * SHELF_ARC_UP
        eye[2] += arc * SHELF_ARC_BACK
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
        if (followId != null || shelfOn) return
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

    /**
     * A tap on a coral: a gentle closer look at [x], [y], [z] (about 30 % closer, never nearer than [FOCUS_DISTANCE]).
     * Unlike [focusOn] it never toggles back out; already close in, it only re-aims.
     */
    fun peekAt(x: Float, y: Float, z: Float) {
        if (followId != null || shelfOn) return
        glide()
        target[0] = x.coerceIn(TARGET_LO[0], TARGET_HI[0])
        target[1] = y.coerceIn(TARGET_LO[1], TARGET_HI[1])
        target[2] = z.coerceIn(TARGET_LO[2], TARGET_HI[2])
        distLogGoal = max(distLogGoal + ln(0.7f), ln(FOCUS_DISTANCE))
    }

    /** A saved orbit / follow view: screensaver mode puts it back when the keeper taps to leave. */
    class Bookmark internal constructor(
        internal val target: FloatArray, internal val yaw: Float, internal val pitch: Float, internal val distLog: Float,
        internal val followId: String?, internal val fYaw: Float, internal val fPitch: Float, internal val fZoom: Float,
    )

    fun bookmark() = Bookmark(target.copyOf(), yawGoal, pitchGoal, distLogGoal, followId, followYaw, followPitch, followZoomLog)

    /** Puts [b] back at once (no glide: the caller fades through black). */
    fun restore(b: Bookmark) {
        nurseryOn = false
        musicOn = false
        val id = b.followId
        // follow state first: releasing a follow re-aims the orbit goals at the eye, which the bookmark then replaces
        if (id == null) { if (followId != null) releaseFollow() } else {
            follow(id)
            followYaw = b.fYaw; followPitch = b.fPitch; followZoomLog = b.fZoom
            fYawS.snap(followYaw); fPitchS.snap(followPitch); fZoomS.snap(followZoomLog)
        }
        yawInertia.stop(); pitchInertia.stop()
        for (i in 0 until 3) target[i] = b.target[i]
        yawGoal = b.yaw; pitchGoal = b.pitch; distLogGoal = b.distLog
        yawS.snap(yawGoal); pitchS.snap(pitchGoal); distLogS.snap(distLogGoal); targetS.snap(target)
        snappy()
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
        hasBody = false
        losRatio = 1f
        fishVel.fill(0f)
        fZoomS.omega = OMEGA_TOUCH; fYawS.omega = OMEGA_TOUCH; fPitchS.omega = OMEGA_TOUCH
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
        yawInertia.stop(); pitchInertia.stop(); fYawInertia.stop(); fPitchInertia.stop(); zoomInertia.stop()
        zoomRate = 0f
    }

    /**
     * On-screen zoom button: [steps] > 0 moves closer, < 0 further out (one step ≈ 30 %). It eases there, and pushing
     * past the closest / furthest framing gives a little and bounces back, so a press always visibly does something.
     */
    fun nudgeZoom(steps: Float) {
        if (steps == 0f || shelfOn) return
        zoomInertia.stop()
        val d = -steps * ZOOM_STEP_LOG
        if (followId != null) {
            fZoomS.omega = OMEGA_BUTTON
            followZoomLog = SoftLimit.drag(followZoomLog, d, FOLLOW_MIN_ZOOM_LOG, FOLLOW_MAX_ZOOM_LOG, ZOOM_GIVE)
        } else {
            glide()
            distLogGoal = SoftLimit.drag(distLogGoal, d, ln(MIN_DISTANCE), ln(maxDistance), ZOOM_GIVE)
        }
    }

    /** On-screen swing button: turns the view round the followed fish (or the orbit) by [radians], easing there. */
    fun nudgeOrbit(radians: Float) {
        if (radians == 0f || shelfOn) return
        if (followId != null) {
            fYawInertia.stop(); fYawS.omega = OMEGA_BUTTON
            followYaw += radians
        } else {
            glide()
            yawGoal = SoftLimit.drag(yawGoal, radians, -maxYaw, maxYaw, YAW_GIVE)
        }
    }

    private fun touchFeel() {
        if (fYawS.omega != OMEGA_TOUCH) { fYawS.omega = OMEGA_TOUCH; fPitchS.omega = OMEGA_TOUCH }
        if (fZoomS.omega != OMEGA_TOUCH) fZoomS.omega = OMEGA_TOUCH
    }

    /** Glides to the music corner ([on]) or back to the tank view; stops any follow. */
    fun lookAtMusic(on: Boolean) {
        if (on && (storeOn || shelfOn)) return
        if (on && followId != null) releaseFollow()
        if (on) nurseryOn = false
        musicOn = on
    }

    /** Glides to the nursery tank ([on]) or back to the tank view; stops any follow. */
    fun lookAtNursery(on: Boolean) {
        if (on && followId != null) releaseFollow()
        nurseryOn = on
    }

    /** One-finger drag in pixels. In follow mode it swings the camera around the fish. */
    fun orbit(dxPx: Float, dyPx: Float, viewHeightPx: Float) {
        if (shelfOn) { shelfDrag(dxPx, dyPx, viewHeightPx); return }
        if (nurseryOn) { nurseryOn = false; return }
        if (musicOn) { musicOn = false; return }
        val k = PI.toFloat() / max(viewHeightPx, 1f)
        if (rideOn) {
            rideYaw -= dxPx * k
            ridePitch = (ridePitch + dyPx * k).coerceIn(-RIDE_MAX_PITCH, RIDE_MAX_PITCH)
            return
        }
        if (followId != null) {
            touchFeel()
            followYaw -= dxPx * k
            followPitch = SoftLimit.drag(followPitch, dyPx * k, FOLLOW_MIN_PITCH, FOLLOW_MAX_PITCH, PITCH_GIVE)
        } else {
            snappy()
            yawGoal = SoftLimit.drag(yawGoal, -dxPx * k, -maxYaw, maxYaw, YAW_GIVE)
            pitchGoal = SoftLimit.drag(pitchGoal, dyPx * k, minPitch, maxPitch, PITCH_GIVE)
        }
    }

    /** One-finger release velocity (px/s): keeps the orbit turning and coasting to a stop. */
    fun fling(vxPx: Float, vyPx: Float, viewHeightPx: Float) {
        if (shelfOn || rideOn) return
        val k = PI.toFloat() / max(viewHeightPx, 1f)
        val wy = (-vxPx * k).coerceIn(-MAX_SPIN, MAX_SPIN)
        val wp = (vyPx * k * 0.6f).coerceIn(-MAX_SPIN, MAX_SPIN)
        if (followId != null) { fYawInertia.kick(wy); fPitchInertia.kick(wp) } else { yawInertia.kick(wy); pitchInertia.kick(wp) }
    }

    /** Two-finger twist (radians, screen): turns the view about the vertical axis. */
    fun rotate(radians: Float) {
        if (shelfOn) return
        if (followId != null) { touchFeel(); followYaw -= radians }
        else { snappy(); yawGoal = SoftLimit.drag(yawGoal, -radians, -maxYaw, maxYaw, YAW_GIVE) }
    }

    /** Pinch: factor > 1 zooms in (about the view centre). */
    fun zoom(factor: Float) {
        if (factor <= 0f || shelfOn) return
        if (nurseryOn) { nurseryOn = false; return }
        if (musicOn) { musicOn = false; return }
        if (touching) zoomPending += -ln(factor)
        if (followId != null) { touchFeel(); followZoomLog = SoftLimit.drag(followZoomLog, -ln(factor), FOLLOW_MIN_ZOOM_LOG, FOLLOW_MAX_ZOOM_LOG, ZOOM_GIVE) }
        else { snappy(); distLogGoal = SoftLimit.drag(distLogGoal, -ln(factor), ln(MIN_DISTANCE), ln(maxDistance), ZOOM_GIVE) }
    }

    /**
     * Pinch about the fingers' focus (px): factor > 1 zooms in, and the point under the fingers stays put (the
     * orbit target slides toward it), so you can zoom straight into a gap in the rockwork.
     */
    fun zoomAt(factor: Float, focusXPx: Float, focusYPx: Float, widthPx: Int, heightPx: Int) {
        if (factor <= 0f || factor.isNaN() || shelfOn) return
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
        if (followId != null || shelfOn) return
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
        advanceZoomInertia(step)
        colliders.sdf = sdf
        // riding: a coarse coral or kit box the sub is gliding through is water there, so the chase eye may follow it in
        if (rideOn && !rideFirstPerson) colliders.passBoxesAround(rideAnchor[0], rideAnchor[1], rideAnchor[2]) else colliders.clearPass()
        if (rideOn) {
            if (!riding) {
                rideEyeS.snap(if (initialized) eye else rideEye); rideAimS.snap(if (initialized) aim else rideAim); riding = true
                rideYaw = 0f; ridePitch = 0f; rideYawS.snap(0f); ridePitchS.snap(0f); rideLos = 1f
            }
            rideTarget(step)
            val w = if (rideFirstPerson) RIDE_FP_OMEGA else RIDE_OMEGA
            rideEyeS.omega = w; rideAimS.omega = w * 1.5f
            rideEyeS.step(desiredEye, step, eye)
            rideAimS.step(desiredAim, step, aim)
            initialized = true
        } else if (riding) {
            riding = false
            releaseFollow(); reset()
            advanceOrbit(step); orbitEye(eye); targetS.valueInto(aim)
        } else if (followId != null && rig != null) {
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
        blendShelf(step)
        val inNursery = blendNursery(step) or blendMusic(step)
        // Clearances from the near plane this framing will get, so the near plane can never cut into anything solid.
        val pre = eyeAimDistance()
        val corner = EyeCollider.nearCornerDistance(nearFor(pre), fovY, aspect)
        if (rideOn && rideFirstPerson) {
            // inside the hull, which keeps its own radius off everything: only the near plane needs room
            colliders.rockClearance = max(0.6f, corner + 0.3f)
            colliders.sandClearance = max(0.6f, corner + 0.3f)
            colliders.boxClearance = max(0.5f, corner + 0.2f)
            colliders.bodyClearance = max(0.4f, corner + 0.2f)
        } else {
            colliders.rockClearance = max(EYE_CLEARANCE, corner + 1f)
            colliders.sandClearance = max(SAND_CLEARANCE, corner + 0.6f)
            colliders.boxClearance = max(BOX_CLEARANCE, corner + 0.6f)
            colliders.bodyClearance = max(BODY_CLEARANCE, corner + 0.4f)
        }
        if (inNursery) {
            // the nursery tank sits outside the display: no display collision
            ballOn = false
        } else if (shelfMix > 0f) {
            // the shelf beside the tank: only the old rock / glass clamp
            clampEye(eye, sdf)
            ballOn = false
        } else {
            collide(step)
        }
        if (storeOn || storeShift.value != 0f) {
            val sx = storeShift.step(storeFocusX, step)
            eye[0] += sx; aim[0] += sx
        }
        M.lookAt(eye[0], eye[1], eye[2], aim[0], aim[1], aim[2], view)
        // near plane: 3 % of the view distance, never so far out that its corners reach past the tightest clearance
        val nearCap = (minOf(colliders.bodyClearance, colliders.boxClearance) - 0.2f) / max(EyeCollider.nearCornerDistance(1f, fovY, aspect), 1e-3f)
        near = minOf(nearFor(eyeAimDistance()), max(nearCap, 0.1f))
        M.perspective(fovY, aspect, near, far, proj)
        M.mul(proj, view, viewProj)
        M.invert(viewProj, invViewProj)
    }

    /**
     * Ride camera goals into [desiredEye] / [desiredAim]: the sub's own framing ([rideEye] / [rideAim]) swung by the
     * user's look-round drag, pulled in before anything between the sub and the chase eye, and fed forward by the sub's
     * velocity times the springs' lag (2 / ω) so the camera keeps pace instead of trailing.
     */
    private fun rideTarget(dt: Float) {
        if (!touching) {
            val k = exp(-RIDE_RELAX * dt)
            rideYaw *= k; ridePitch *= k
        }
        val yo = rideYawS.step(rideYaw, dt); val po = ridePitchS.step(ridePitch, dt)
        val cy = cos(yo); val sy = sin(yo)
        if (rideFirstPerson) {
            // look round from the eye
            desiredEye[0] = rideEye[0]; desiredEye[1] = rideEye[1]; desiredEye[2] = rideEye[2]
            val dx = rideAim[0] - rideEye[0]; val dz = rideAim[2] - rideEye[2]
            desiredAim[0] = rideEye[0] + dx * cy + dz * sy
            desiredAim[2] = rideEye[2] - dx * sy + dz * cy
            desiredAim[1] = rideAim[1] + sqrt(dx * dx + dz * dz) * po
        } else {
            // swing the chase eye round the sub
            val dx = rideEye[0] - rideAnchor[0]; val dz = rideEye[2] - rideAnchor[2]
            var ex = rideAnchor[0] + dx * cy + dz * sy
            var ez = rideAnchor[2] - dx * sy + dz * cy
            var ey = rideEye[1] + sqrt(dx * dx + dz * dz) * po
            // line of sight from the sub: stop before anything in the way (in at once, out gently)
            var lx = ex - rideAnchor[0]; var ly = ey - rideAnchor[1]; var lz = ez - rideAnchor[2]
            val len = sqrt(lx * lx + ly * ly + lz * lz)
            if (len > 1e-3f) {
                lx /= len; ly /= len; lz /= len
                val hit = colliders.cast(rideAnchor[0], rideAnchor[1], rideAnchor[2], lx, ly, lz, len, followOwnerTag)
                val want = (hit / len).coerceIn(0f, 1f)
                rideLos = if (want <= rideLos || dt <= 0f) want else want + (rideLos - want) * exp(-LOS_RELEASE * dt)
                val t = max(len * rideLos, minOf(len, RIDE_MIN_CHASE))
                ex = rideAnchor[0] + lx * t; ey = rideAnchor[1] + ly * t; ez = rideAnchor[2] + lz * t
            }
            desiredEye[0] = ex; desiredEye[1] = ey; desiredEye[2] = ez
            desiredAim[0] = rideAim[0]; desiredAim[1] = rideAim[1]; desiredAim[2] = rideAim[2]
        }
        val w = if (rideFirstPerson) RIDE_FP_OMEGA else RIDE_OMEGA
        val le = 2f / w; val la = 2f / (w * 1.5f)
        for (i in 0 until 3) { desiredEye[i] += rideVel[i] * le; desiredAim[i] += rideVel[i] * la }
    }

    private fun eyeAimDistance(): Float {
        val dx = aim[0] - eye[0]; val dy = aim[1] - eye[1]; val dz = aim[2] - eye[2]
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    private fun nearFor(viewDistance: Float): Float = (viewDistance * 0.03f).coerceIn(0.25f, 1f)

    /**
     * Carries the collided eye ([ball]) to where the springs put [eye] without passing through anything, then clamps it
     * off the glass. If it stays blocked well short of a clear spot for [STUCK_S] (the fish swam round the far side of
     * a rock), it cuts there instead of grinding along the rock face.
     */
    private fun collide(dt: Float) {
        if (!ballOn) {
            ball[0] = eye[0]; ball[1] = eye[1]; ball[2] = eye[2]
            glassClamp(ball)
            colliders.pushOut(ball)
            ballOn = true
            stuckS = 0f
        }
        colliders.move(ball, eye[0], eye[1], eye[2])
        if (glassClamp(ball)) colliders.pushOut(ball)
        val dx = eye[0] - ball[0]; val dy = eye[1] - ball[1]; val dz = eye[2] - ball[2]
        val off = sqrt(dx * dx + dy * dy + dz * dz)
        // riding: the goal is already clear in sight of the sub, so a ball wedged short of it (behind a rock or a piece of
        // kit the sub glided past) cuts there quickly rather than leaving the sub to sail on out of view
        val tol = if (rideOn) RIDE_STUCK_CM else max(STUCK_CM, eyeAimDistance() * 0.35f)
        stuckS = if (off > tol) stuckS + dt else 0f
        // following and the fish is out of sight behind something: cut sooner
        val limit = if (rideOn || (stuckS > 0f && followId != null && !sees(ball))) STUCK_HIDDEN_S else STUCK_S
        if (stuckS > limit && colliders.free(eye[0], eye[1], eye[2]) >= 0f) {
            ball[0] = eye[0]; ball[1] = eye[1]; ball[2] = eye[2]
            glassClamp(ball)
            colliders.pushOut(ball)
            stuckS = 0f
        }
        eye[0] = ball[0]; eye[1] = ball[1]; eye[2] = ball[2]
    }

    /** True when nothing solid lies between the aim point and [p] (the followed animal itself does not count). */
    private fun sees(p: FloatArray): Boolean {
        var dx = p[0] - aim[0]; var dy = p[1] - aim[1]; var dz = p[2] - aim[2]
        val len = sqrt(dx * dx + dy * dy + dz * dz)
        if (len < 1e-3f) return true
        dx /= len; dy /= len; dz /= len
        return colliders.cast(aim[0], aim[1], aim[2], dx, dy, dz, len, followOwnerTag) >= len - 0.5f
    }

    /**
     * Inside the tank's footprint (or the margin band just outside the panes): stay inside, a margin off the glass and
     * under the water's top. Outside it, nothing. Returns true when it moved [e].
     */
    private fun glassClamp(e: FloatArray): Boolean {
        val m = EYE_GLASS_MARGIN
        if (!(e[0] > -m && e[0] < TANK[0] + m && e[2] > -m && e[2] < TANK[2] + m && e[1] < WATER_Y + m)) return false
        val x = e[0].coerceIn(m, TANK[0] - m)
        val z = e[2].coerceIn(m, TANK[2] - m)
        val y = minOf(max(e[1], SAND_Y + 1.5f), WATER_Y - m)
        val moved = x != e[0] || y != e[1] || z != e[2]
        e[0] = x; e[1] = y; e[2] = z
        return moved
    }

    /** Pinch releases coast: the zoom rate while the fingers moved carries on and fades out. */
    private fun advanceZoomInertia(dt: Float) {
        if (touching) {
            if (dt > 1e-4f) {
                val a = 1f - exp(-ZOOM_RATE_SMOOTH * dt)
                zoomRate += (zoomPending / dt - zoomRate) * a
            }
        } else if (wasTouching) {
            if (kotlin.math.abs(zoomRate) > MIN_ZOOM_FLING) zoomInertia.kick(zoomRate.coerceIn(-MAX_ZOOM_FLING, MAX_ZOOM_FLING))
            zoomRate = 0f
        }
        zoomPending = 0f
        wasTouching = touching
        if (touching || !zoomInertia.active) return
        if (followId != null) {
            val over = SoftLimit.overshoot(followZoomLog, FOLLOW_MIN_ZOOM_LOG, FOLLOW_MAX_ZOOM_LOG)
            val d = zoomInertia.advance(dt, if (over > 0f) LIMIT_BRAKE else 0f)
            if (d != 0f) followZoomLog = SoftLimit.drag(followZoomLog, d, FOLLOW_MIN_ZOOM_LOG, FOLLOW_MAX_ZOOM_LOG, ZOOM_GIVE)
        } else if (!shelfOn) {
            val over = SoftLimit.overshoot(distLogGoal, ln(MIN_DISTANCE), ln(maxDistance))
            val d = zoomInertia.advance(dt, if (over > 0f) LIMIT_BRAKE else 0f)
            if (d != 0f) distLogGoal = SoftLimit.drag(distLogGoal, d, ln(MIN_DISTANCE), ln(maxDistance), ZOOM_GIVE)
        } else zoomInertia.stop()
    }

    private fun clampEye(e: FloatArray, sdf: Sdf?) {
        if (sdf != null) pushOut(e, sdf, EYE_CLEARANCE)
        val m = EYE_GLASS_MARGIN
        // within the tank's footprint (plus the margin band outside the panes): stay inside, a margin off the glass
        if (e[0] > -m && e[0] < TANK[0] + m && e[2] > -m && e[2] < TANK[2] + m && e[1] < WATER_Y + m) {
            e[0] = e[0].coerceIn(m, TANK[0] - m)
            e[2] = e[2].coerceIn(m, TANK[2] - m)
            if (e[1] < SAND_Y + 1.5f) e[1] = SAND_Y + 1.5f
            if (e[1] > WATER_Y - m) e[1] = WATER_Y - m
            if (sdf != null) pushOut(e, sdf, EYE_CLEARANCE)
        }
    }

    private fun advanceOrbit(dt: Float) {
        if (!touching) {
            // fling momentum, resisted (and braked harder) past the soft limits
            val yo = SoftLimit.overshoot(yawGoal, -maxYaw, maxYaw)
            val po = SoftLimit.overshoot(pitchGoal, minPitch, maxPitch)
            val dy = yawInertia.advance(dt, if (yo > 0f) LIMIT_BRAKE else 0f)
            val dp = pitchInertia.advance(dt, if (po > 0f) LIMIT_BRAKE else 0f)
            if (dy != 0f) yawGoal = SoftLimit.drag(yawGoal, dy, -maxYaw, maxYaw, YAW_GIVE)
            if (dp != 0f) pitchGoal = SoftLimit.drag(pitchGoal, dp, minPitch, maxPitch, PITCH_GIVE)
            yawGoal = SoftLimit.relax(yawGoal, -maxYaw, maxYaw, RELAX_RATE, dt)
            pitchGoal = SoftLimit.relax(pitchGoal, minPitch, maxPitch, RELAX_RATE, dt)
            distLogGoal = SoftLimit.relax(distLogGoal, ln(MIN_DISTANCE), ln(maxDistance), RELAX_RATE, dt)
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
     *
     * It frames a spring-smoothed body centre (the mean of the front two thirds of the spine), so the head's side to
     * side wag never shakes the view, and feeds the fish's velocity forward by the eye / aim springs' lag (2 / ω for a
     * critically damped spring) so a cruising fish stays centred instead of trailing off the edge of the frame. The eye
     * is then pulled in before anything between it and the fish ([colliders] cast; see [losRatio]).
     */
    internal fun followTarget(rig: FishRig, sdf: Sdf?, eyeOut: FloatArray, aimOut: FloatArray, dt: Float = 0f) {
        val bl = rig.scale
        val s = rig.spine
        // body axis: the front of the body minus the middle (both averaged, so the wag mostly cancels)
        var fx = (s[0] + s[4] + s[8]) - (s[28] + s[32] + s[36])
        var fy = (s[1] + s[5] + s[9]) - (s[29] + s[33] + s[37])
        var fz = (s[2] + s[6] + s[10]) - (s[30] + s[34] + s[38])
        val fl = sqrt(fx * fx + fy * fy + fz * fz)
        if (fl < 1e-4f) { fx = 1f; fy = 0f; fz = 0f } else { fx /= fl; fy /= fl; fz /= fl }
        // body centre: mean of spine points 1..9, then a critically damped spring on top
        var cx = 0f; var cy = 0f; var cz = 0f
        for (i in 1..9) { cx += s[i * 4]; cy += s[i * 4 + 1]; cz += s[i * 4 + 2] }
        tmpTarget[0] = cx / 9f; tmpTarget[1] = cy / 9f; tmpTarget[2] = cz / 9f
        if (!hasBody || dt <= 0f) { bodyS.snap(tmpTarget); hasBody = true }
        else bodyS.step(tmpTarget, dt, tmpDir)
        val mx = bodyS[0]; val my = bodyS[1]; val mz = bodyS[2]
        // smoothed velocity of the raw centre (the springs' own lag is compensated below)
        if (hasPrevMid && dt > 1e-4f) {
            val a = 1f - exp(-VEL_SMOOTH * dt)
            fishVel[0] += ((tmpTarget[0] - prevMid[0]) / dt - fishVel[0]) * a
            fishVel[1] += ((tmpTarget[1] - prevMid[1]) / dt - fishVel[1]) * a
            fishVel[2] += ((tmpTarget[2] - prevMid[2]) / dt - fishVel[2]) * a
        }
        prevMid[0] = tmpTarget[0]; prevMid[1] = tmpTarget[1]; prevMid[2] = tmpTarget[2]; hasPrevMid = true
        // aim: lead by LEAD_S of travel plus the aim spring's and the body spring's lag
        val aimLag = LEAD_S + 2f / (FOLLOW_OMEGA * 1.4f) + 2f / BODY_OMEGA
        var lx = fishVel[0] * aimLag; var ly = fishVel[1] * aimLag * 0.5f; var lz = fishVel[2] * aimLag
        val ll = sqrt(lx * lx + ly * ly + lz * lz)
        val maxLead = bl * MAX_LEAD_BL + MAX_LAG_COMP_CM
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
        // orbit about a point half way to the lead, plus the eye spring's lag, so the eye keeps pace too
        val eyeLag = LEAD_S * 0.5f + 2f / FOLLOW_OMEGA + 2f / BODY_OMEGA
        var ox = fishVel[0] * eyeLag; var oy = fishVel[1] * eyeLag * 0.5f; var oz = fishVel[2] * eyeLag
        val ol = sqrt(ox * ox + oy * oy + oz * oz)
        if (ol > maxLead) { val k = maxLead / ol; ox *= k; oy *= k; oz *= k }
        ox += mx; oy += my; oz += mz
        var ex = ox + d * sin(base) * cp
        var ey = oy + d * sin(pitch)
        var ez = oz + d * cos(base) * cp
        // stay inside the tank sides/sand/surface, but may sit outside the front glass
        ex = ex.coerceIn(1f, TANK[0] - 1f); ey = ey.coerceIn(SAND_Y + 1f, WATER_Y - 1f); ez = ez.coerceIn(1f, TANK[2] + 60f)
        // line of sight: cast from the aim point toward the eye; stop before anything in the way (not the fish itself)
        var dx = ex - aimOut[0]; var dy = ey - aimOut[1]; var dz = ez - aimOut[2]
        val len = sqrt(dx * dx + dy * dy + dz * dz)
        if (len > 1e-3f) {
            dx /= len; dy /= len; dz /= len
            colliders.sdf = sdf
            val hit = colliders.cast(aimOut[0], aimOut[1], aimOut[2], dx, dy, dz, len, followOwnerTag)
            val want = (hit / len).coerceIn(0f, 1f)
            losRatio = if (want <= losRatio || dt <= 0f) want else want + (losRatio - want) * exp(-LOS_RELEASE * dt)
            val t = max(len * losRatio, minOf(len, MIN_FOLLOW * 0.4f))
            ex = aimOut[0] + dx * t; ey = aimOut[1] + dy * t; ez = aimOut[2] + dz * t
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
        val user = (d * DEFAULT_DISTANCE / fitDistance(aspect)).coerceIn(MIN_DISTANCE * 0.7f, maxDistance * 1.3f)
        distLogGoal = ln(user)
        pitchGoal = kotlin.math.asin((dy / d).coerceIn(-1f, 1f)).coerceIn(minPitch - PITCH_GIVE, maxPitch + PITCH_GIVE)
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
        /**
         * Tank interior (cm): x length, y height, z depth (back glass z = 0) of the tank on screen: Tee's ([TankDims])
         * unless a My tank build of another size is showing ([applyShape]; one tank is drawn at a time).
         */
        val TANK = floatArrayOf(TankDims.LENGTH_F, TankDims.HEIGHT_F, TankDims.DEPTH_F)
        const val SAND_Y = TankDims.SAND_TOP_F
        var WATER_Y = TankDims.WATER_LEVEL_F
            private set
        /** Orbit target: mid-length, a little under mid-water, mid-depth. */
        val DEFAULT_TARGET = floatArrayOf(TankDims.LENGTH_F / 2f, 19f, TankDims.DEPTH_F / 2f)
        /** Default orbit distance: 150 cm for Tee's tank, nearer for a small one, further for a tall, deep one. */
        var DEFAULT_DISTANCE = 150f
            private set
        /** The shape [TANK] and the framing follow, so a renderer only refits when the tank on screen changes. */
        var shape: com.reeflive.core.sim.TankShape = com.reeflive.core.sim.TankShape.STOCK
            private set

        /** Sizes the box, water line, default framing and target box to [s] (GL thread, before the frame is drawn). */
        fun applyShape(s: com.reeflive.core.sim.TankShape) {
            shape = s
            TANK[0] = s.lengthCm.toFloat(); TANK[1] = s.heightCm.toFloat(); TANK[2] = s.depthCm.toFloat()
            WATER_Y = s.waterLevelCm.toFloat()
            DEFAULT_TARGET[0] = TANK[0] / 2f; DEFAULT_TARGET[1] = 19f * WATER_Y / TankDims.WATER_LEVEL_F; DEFAULT_TARGET[2] = TANK[2] / 2f
            // same share of the screen as Tee's tank by width, or by height (a tall cube) when that needs more room
            val k = max(TANK[0] / TankDims.LENGTH_F, 0.75f * TANK[1] / TankDims.HEIGHT_F)
            DEFAULT_DISTANCE = (TANK[2] - DEFAULT_TARGET[2]) + (150f - TankDims.DEPTH_F / 2f) * k
            TARGET_HI[0] = TANK[0] + 4f; TARGET_HI[1] = WATER_Y + 2f; TARGET_HI[2] = TANK[2] + 6f
        }
        const val DEFAULT_PITCH = 0.08f
        /** Closest orbit (user units; ≈ 10 cm from the target in portrait): right up to the rockwork. */
        const val MIN_DISTANCE = 12f
        const val MAX_DISTANCE = 320f
        /** Soft yaw range: a little past square-on to either side glass. */
        const val MAX_YAW = 1.75f
        /** Store aisle (StoreRoom.kt): orbit target height, pitch and limits that keep the eye in the shop. */
        const val STORE_TARGET_Y = 6f
        /** The room view ([roomView]): aimed between the tank and its stand, from a little right and above. */
        const val ROOM_TARGET_Y = -16f
        const val ROOM_YAW = 0.22f
        const val ROOM_PITCH = 0.14f
        const val STORE_PITCH = 0.16f
        const val STORE_MAX_YAW = 0.4f
        const val STORE_MIN_PITCH = 0f
        const val STORE_MAX_PITCH = 0.5f
        /** Aisle framing: floor to above the tank (half height from the target) and the tank plus some aisle (half width). */
        const val STORE_FIT_HEIGHT = 92f
        const val STORE_FIT_HALF_WIDTH = 105f
        const val STORE_MAX_EFFECTIVE = 480f
        /** Further out than this (cm), a tap on the live tank walks up to it. */
        const val STORE_CLOSE_EFFECTIVE = 200f
        /** Walking between tanks (rad/s of the critically damped slide). */
        const val OMEGA_WALK = 4.5f
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
        /** Submarine ride camera stiffness (rad/s). */
        const val RIDE_OMEGA = 7f
        /** Inside the sub the view rides tighter (it is the pilot's eye). */
        const val RIDE_FP_OMEGA = 16f
        /** Look-round drag while riding: pitch range (as a slope) and how fast it eases back after the finger lifts (1/s). */
        const val RIDE_MAX_PITCH = 0.9f
        const val RIDE_RELAX = 1.2f
        /** The chase view never pulls in closer to the sub than this (cm). */
        const val RIDE_MIN_CHASE = 3.5f
        const val FLY_IN_S = 1.4f
        const val FLY_OMEGA = 2.4f
        const val FOLLOW_OMEGA = 6.5f
        /** Lead the frame by this many seconds of the fish's velocity, at most [MAX_LEAD_BL] body lengths. */
        const val LEAD_S = 0.35f
        const val MAX_LEAD_BL = 0.8f
        /** Fish velocity smoothing rate (1/s). */
        const val VEL_SMOOTH = 4f
        /** Eye-to-rock margin (cm): above the largest near plane so the rock never fills the screen edge. */
        const val EYE_CLEARANCE = 3f
        /** Least eye margins (cm) off the sand, coral / equipment boxes and animal bodies (raised to clear the near plane). */
        const val SAND_CLEARANCE = 1.5f
        const val BOX_CLEARANCE = 1f
        const val BODY_CLEARANCE = 0.8f
        /** Capsule tag of the followed animal in [colliders]. */
        const val FOLLOW_OWNER = 1
        /** Blocked this far (cm, or 35 % of the view distance) short of the springs' eye for this long: cut there. */
        const val STUCK_CM = 6f
        const val STUCK_S = 0.6f
        const val STUCK_HIDDEN_S = 0.25f
        /** Riding: how far (cm) the collided eye may lag its sight-checked goal before it counts as wedged. */
        const val RIDE_STUCK_CM = 3f
        /** Line-of-sight pull-in eases back out at this rate (1/s) once the view is clear again. */
        const val LOS_RELEASE = 2.5f
        /** Body-centre smoothing (rad/s): well under the tail-beat rate, quick next to a turn. */
        const val BODY_OMEGA = 14f
        /** Most the spring-lag feed-forward adds to the lead (cm). */
        const val MAX_LAG_COMP_CM = 8f
        /** Zoom button step (log distance, ≈ 30 %) and the stiffness buttons ease with. */
        val ZOOM_STEP_LOG = ln(1.35f)
        const val OMEGA_BUTTON = 6f
        /** Pinch release coasting: friction (1/s), rate smoothing (1/s), least and most fling rates (log distance / s). */
        const val ZOOM_FRICTION = 4f
        const val ZOOM_RATE_SMOOTH = 14f
        const val MIN_ZOOM_FLING = 0.4f
        const val MAX_ZOOM_FLING = 3f
        const val EYE_GLASS_MARGIN = 1.5f
        /** Follow-heading spring stiffness (rad/s); critically damped, settles in ≈ 4.7 / ω ≈ 1.3 s. */
        const val FOLLOW_YAW_OMEGA = 3.5f
        /** Cross to the other flank once the kept one would view from behind by more than this (cos of the angle off +Z). */
        const val FLANK_SWITCH_COS = 0.35f

        /** Supply shelf: travel time tank ↔ shelf, closest viewing distance, the width a narrow screen still shows. */
        const val SHELF_TRAVEL_S = 1.0f
        const val SHELF_DISTANCE = 50f
        const val SHELF_MIN_WIDTH = 42f
        /** The eye sits this far up per cm of distance (looks a little down onto the boards). */
        const val SHELF_EYE_RISE = 0.16f
        /** The glide arcs this much up and back at its middle. */
        const val SHELF_ARC_UP = 12f
        const val SHELF_ARC_BACK = 25f
        /** Soft give (cm) past the ends of the shelf. */
        const val SHELF_GIVE = 6f

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
            return (front + max(byHeight, byWidth)).coerceIn(min(MIN_FIT_DISTANCE, DEFAULT_DISTANCE), DEFAULT_DISTANCE)
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
            repeat(6) {
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
