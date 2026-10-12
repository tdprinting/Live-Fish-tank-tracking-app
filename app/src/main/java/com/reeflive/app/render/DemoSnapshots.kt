package com.reeflive.app.render

import com.reeflive.core.sim.OrganismPose
import com.reeflive.core.sim.SimSnapshot
import com.reeflive.core.sim.WaterState
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Test harness (PLAN C2 acceptance): fish whose spline follows a circle, used when no snapshot provider
 * is attached (previews, renderer bring-up). Frames follow the BodySpline convention: quaternion
 * mapping model X (forward) / Y (up) / Z (side) to world. Pure Kotlin.
 */
object DemoSnapshots {
    private val FISH = listOf(
        Triple("demo_tang", "paracanthurus_hepatus", 9f),
        Triple("demo_clown", "amphiprion_ocellaris", 7f),
        Triple("demo_wrasse", "labroides_dimidiatus", 8f),
        Triple("demo_cardinal", "sphaeramia_nematoptera", 6f),
    )

    /** Daytime channels (white, blue, royal, red, green, violet, uv, moon). */
    val DAY = floatArrayOf(0.55f, 0.9f, 0.95f, 0.25f, 0.3f, 0.6f, 0.4f, 0f)

    fun at(timeSec: Double, ich: Float = 0f): SimSnapshot {
        val poses = FISH.mapIndexed { i, (id, sp, bl) ->
            val radius = 18f + i * 6f
            val cx = 61f; val cz = 16f
            val cy = 22f + i * 7f
            val omega = 0.25f / (1f + i * 0.2f) * (if (i % 2 == 0) 1f else -1f)
            circlePose(id, sp, bl, timeSec.toFloat(), cx, cy, cz, radius, omega, phase = i * 1.3f, ich = if (i == 0) ich else 0f)
        }
        return SimSnapshot(timeSec, DAY.copyOf(), WaterState(), poses, emptyList(), FloatArray(0), ByteArray(0), 0f, emptyList(),
            tick = (timeSec * 20).toLong(), lightIntensity = 0.8f)
    }

    /** A fish of length [bl] swimming along a horizontal circle (radius cm, angular speed rad/s). */
    fun circlePose(id: String, speciesId: String, bl: Float, t: Float, cx: Float, cy: Float, cz: Float, radius: Float, omega: Float, phase: Float = 0f, ich: Float = 0f): OrganismPose {
        val spine = FloatArray(48)
        val frames = FloatArray(64)
        val stepAngle = (bl / 15f) / radius
        val dir = if (omega >= 0f) 1f else -1f
        for (k in 0 until 16) {
            val a = omega * t + phase - dir * stepAngle * k
            val x = cx + radius * cos(a); val z = cz + radius * 0.55f * sin(a)
            val y = cy + 1.5f * sin(a * 2f)
            spine[k * 3] = x; spine[k * 3 + 1] = y; spine[k * 3 + 2] = z
        }
        for (k in 0 until 16) {
            val a = if (k < 15) k else 14
            var fx = spine[a * 3] - spine[(a + 1) * 3]; var fy = spine[a * 3 + 1] - spine[(a + 1) * 3 + 1]; var fz = spine[a * 3 + 2] - spine[(a + 1) * 3 + 2]
            val l = sqrt(fx * fx + fy * fy + fz * fz).coerceAtLeast(1e-6f); fx /= l; fy /= l; fz /= l
            val q = frameQuat(fx, fy, fz)
            q.copyInto(frames, k * 4)
        }
        return OrganismPose(id, speciesId, spine, frames, swimPhase = t * 2.2f, amp = 0.06f, waveLen = 0.9f, pectoral = 0.6f, finClamp = 0f,
            gill = 0.2f, gillRate = 1.5f, desat = 0f, ich = ich, thin = 0f, eyeCloud = 0f, alive = true, scale = bl,
            appendagePhase = t * 0.5f, nickname = id.removePrefix("demo_"))
    }

    /** Quaternion rotating model +X onto the unit forward vector f with world-up kept (no roll). */
    fun frameQuat(fx: Float, fy: Float, fz: Float): FloatArray {
        val yaw = atan2(-fz, fx)                  // rotation about +Y
        val pitch = kotlin.math.asin(fy.coerceIn(-1f, 1f)) // rotation about +Z (after yaw)
        val cy = cos(yaw / 2); val sy = sin(yaw / 2)
        val cp = cos(pitch / 2); val sp = sin(pitch / 2)
        // q = qYaw(Y) * qPitch(Z)
        // (x, y, z, w) of qY * qZ with qY = (0, sy, 0, cy), qZ = (0, 0, sp, cp)
        return floatArrayOf(sy * sp, sy * cp, cy * sp, cy * cp)
    }

}
