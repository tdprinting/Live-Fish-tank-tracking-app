package com.reeflive.app.render

import com.reeflive.core.sim.SimSnapshot
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.tan

/**
 * Screensaver camera: a calm, endless sequence of scenes (slow eased dollies at different angles and heights,
 * then a slow follow of one animal), joined by long glides or a gentle dip through black. It only feeds the
 * ordinary camera input ([CameraInput] orbit / pan / zoom, follow, reset), so the camera's own springs smooth it
 * further; pure Kotlin, shared by the Android TV / phone screensaver and the web build.
 *
 * Drive it with [update] once per UI frame; draw a black layer at [fade] alpha and [caption] at [captionAlpha].
 */
class CinemaDirector(private val input: CameraInput, private val control: Control) {
    interface Control {
        fun follow(id: String?)
        fun resetCamera()
        /** Height of the tank view in pixels (the camera input's units). */
        fun viewHeightPx(): Float
    }

    /** Camera pose this director believes it set (orbit units of [Camera]); x is the target's sideways shift, cm. */
    private class Pose(var yaw: Float, var pitch: Float, var dist: Float, var x: Float) {
        fun set(o: Pose) { yaw = o.yaw; pitch = o.pitch; dist = o.dist; x = o.x }
    }

    private class Shot(val from: Pose, val to: Pose, val seconds: Float)

    private enum class Phase { SHOT, GLIDE, DIP_OUT, BLACK, FOLLOW }

    /** 0 = clear, 1 = black. */
    var fade = 0f; private set
    var caption: String? = null; private set
    var captionAlpha = 0f; private set

    private val pose = Pose(0f, DEFAULT_PITCH, DEFAULT_DIST, 0f)
    private val goal = Pose(0f, DEFAULT_PITCH, DEFAULT_DIST, 0f)
    private val glideFrom = Pose(0f, 0f, 0f, 0f)
    private var phase = Phase.BLACK
    private var t = 0f
    private var shot: Shot = SHOTS[0]
    private var shotOrder = SHOTS.indices.shuffled().toMutableList()
    private var scenes = 0
    private var followId: String? = null
    private var followName: String? = null
    private val recent = ArrayDeque<String>()
    private var blackReset = false
    private var blackPlaced = false
    private var nextIsFollow = false
    private var fadeIn = 0f

    /** Start (or restart) from black. */
    fun start() {
        enterBlack(follow = false)
        fade = 1f
    }

    /** Skip to the next scene through black (remote "next"). */
    fun next() { if (phase != Phase.BLACK && phase != Phase.DIP_OUT) { nextIsFollow = phase != Phase.FOLLOW; phase = Phase.DIP_OUT; t = 0f } }

    /** The followed animal left the tank: move on. */
    fun followLost() { if (phase == Phase.FOLLOW) next() }

    fun update(dtIn: Float, snap: SimSnapshot?) {
        val dt = dtIn.coerceIn(0f, 0.1f)
        t += dt
        if (fadeIn > 0f) fade = max(0f, fade - dt / FADE_IN_S).also { if (it == 0f) fadeIn = 0f }
        when (phase) {
            Phase.SHOT -> {
                lerp(shot.from, shot.to, smooth(t / shot.seconds), goal)
                drive()
                if (t >= shot.seconds) advance()
            }
            Phase.GLIDE -> {
                lerp(glideFrom, shot.from, smoother(t / GLIDE_S), goal)
                drive()
                if (t >= GLIDE_S) { phase = Phase.SHOT; t = 0f }
            }
            Phase.FOLLOW -> {
                // slow swing around the animal; the follow camera keeps it framed
                orbitBy(FOLLOW_SWING * dt, 0f)
                val name = followName
                val live = snap?.stats?.firstOrNull { it.id == followId }
                caption = live?.caption?.takeIf { it.isNotBlank() && t > 9f } ?: name
                captionAlpha = when {
                    t < 2.5f -> 0f
                    t < 4f -> (t - 2.5f) / 1.5f
                    t > FOLLOW_S - 4f -> ((FOLLOW_S - 2.5f - t) / 1.5f).coerceIn(0f, 1f)
                    else -> 1f
                }
                if (t >= FOLLOW_S) { nextIsFollow = false; phase = Phase.DIP_OUT; t = 0f }
            }
            Phase.DIP_OUT -> {
                fade = smooth(t / FADE_OUT_S)
                captionAlpha = minOf(captionAlpha, 1f - fade)
                if (t >= FADE_OUT_S) enterBlack(follow = nextIsFollow)
            }
            Phase.BLACK -> {
                fade = 1f
                if (!blackReset) { control.follow(null); control.resetCamera(); blackReset = true }
                if (!blackPlaced && t > 0.35f) {
                    // the reset has landed on the GL thread: pose is the default view again
                    pose.set(DEFAULT)
                    blackPlaced = true
                    if (nextIsFollow && pickAnimal(snap)) {
                        control.follow(followId)
                    } else {
                        nextIsFollow = false
                        shot = nextShot()
                        goal.set(shot.from); drive()
                    }
                }
                if (t >= BLACK_S) {
                    fadeIn = 1f
                    t = 0f
                    phase = if (nextIsFollow && followId != null) Phase.FOLLOW else Phase.SHOT
                    scenes++
                }
            }
        }
        if (phase == Phase.SHOT || phase == Phase.GLIDE) captionAlpha = 0f
    }

    /** After a shot: every third scene follows an animal (through black); other shots alternate glide / dip. */
    private fun advance() {
        t = 0f
        nextIsFollow = scenes % 3 == 2
        if (!nextIsFollow && scenes % 2 == 0) {
            glideFrom.set(pose)
            shot = nextShot()
            phase = Phase.GLIDE
            scenes++
        } else {
            phase = Phase.DIP_OUT
        }
    }

    private fun enterBlack(follow: Boolean) {
        phase = Phase.BLACK; t = 0f
        nextIsFollow = follow
        followId = null; followName = null; caption = null; captionAlpha = 0f
        blackReset = false; blackPlaced = false
    }

    private fun nextShot(): Shot {
        if (shotOrder.isEmpty()) shotOrder = SHOTS.indices.shuffled().toMutableList()
        return SHOTS[shotOrder.removeAt(0)]
    }

    private fun pickAnimal(snap: SimSnapshot?): Boolean {
        val stats = snap?.stats?.filter { it.alive } ?: return false
        if (stats.isEmpty()) return false
        val pick = stats.filter { it.id !in recent }.ifEmpty { stats }.random()
        recent.addLast(pick.id); while (recent.size > minOf(4, stats.size - 1).coerceAtLeast(0)) recent.removeFirst()
        followId = pick.id
        followName = if (pick.nickname.isNotBlank() && !pick.nickname.equals(pick.common, true)) "${pick.nickname} · ${pick.common}" else pick.common
        return true
    }

    /** Sends the difference between [goal] and what was last set as camera input. */
    private fun drive() {
        orbitBy(goal.yaw - pose.yaw, goal.pitch - pose.pitch)
        if (goal.dist != pose.dist) {
            input.zoom(exp(ln(pose.dist) - ln(goal.dist)))   // factor > 1 moves closer
            pose.dist = goal.dist
        }
        val dx = goal.x - pose.x
        if (dx != 0f) {
            val h = max(control.viewHeightPx(), 1f)
            val cmPerPx = 2f * pose.dist * tan(FOV_HALF) / h
            val c = cos(pose.yaw).let { if (it < 0.3f) 0.3f else it }
            input.pan(-dx / (cmPerPx * c), 0f)
            pose.x = goal.x
        }
    }

    private fun orbitBy(dYaw: Float, dPitch: Float) {
        if (dYaw == 0f && dPitch == 0f) return
        val k = PI.toFloat() / max(control.viewHeightPx(), 1f)
        input.orbit(-dYaw / k, dPitch / k)
        pose.yaw += dYaw; pose.pitch += dPitch
    }

    private fun lerp(a: Pose, b: Pose, f: Float, out: Pose) {
        out.yaw = a.yaw + (b.yaw - a.yaw) * f
        out.pitch = a.pitch + (b.pitch - a.pitch) * f
        out.dist = exp(ln(a.dist) + (ln(b.dist) - ln(a.dist)) * f)
        out.x = a.x + (b.x - a.x) * f
    }

    private fun smooth(x: Float): Float { val c = x.coerceIn(0f, 1f); return c * c * (3f - 2f * c) }
    private fun smoother(x: Float): Float { val c = x.coerceIn(0f, 1f); return c * c * c * (c * (c * 6f - 15f) + 10f) }

    private companion object {
        const val DEFAULT_PITCH = 0.08f
        const val DEFAULT_DIST = 150f
        val DEFAULT = Pose(0f, DEFAULT_PITCH, DEFAULT_DIST, 0f)
        val FOV_HALF = (25.0 * PI / 180.0).toFloat()
        const val GLIDE_S = 8f
        const val FADE_OUT_S = 1.8f
        const val FADE_IN_S = 2.4f
        const val BLACK_S = 0.9f
        const val FOLLOW_S = 30f
        const val FOLLOW_SWING = 0.035f

        private fun s(y0: Float, p0: Float, d0: Float, x0: Float, y1: Float, p1: Float, d1: Float, x1: Float, sec: Float) =
            Shot(Pose(y0, p0, d0, x0), Pose(y1, p1, d1, x1), sec)

        /** yaw (rad, 0 = front glass), pitch (rad, up), distance (cm), sideways target shift (cm) at start and end. */
        val SHOTS = listOf(
            s(-0.10f, 0.10f, 160f, 0f, 0.12f, 0.06f, 135f, 0f, 26f),          // whole tank, slow push in
            s(-0.60f, -0.04f, 75f, -28f, -0.38f, 0.03f, 62f, -16f, 24f),     // low across the left rockwork
            s(0.55f, 0.14f, 80f, 26f, 0.32f, 0.09f, 66f, 16f, 24f),          // right reef from the side
            s(-0.15f, 0.75f, 115f, -12f, 0.15f, 0.62f, 100f, 12f, 26f),      // drifting over the top of the reef
            s(0.22f, -0.16f, 58f, 18f, -0.22f, -0.10f, 52f, -18f, 28f),      // sand-level dolly along the glass
            s(-0.30f, 0.30f, 95f, 30f, 0.30f, 0.24f, 95f, -30f, 30f),        // long tracking pass, right to left
            s(0.05f, 0.02f, 70f, -6f, -0.05f, 0.05f, 48f, 6f, 24f),          // slow move into the middle of the reef
        )
    }
}
