package com.reeflive.app.render

import kotlin.math.sqrt

/**
 * Pose level of detail for the sim (core `Simulation.poseDetail`: organism id → rebuild its pose every N snapshots),
 * worked out from the camera a few times a second: an animal well outside the view gets [OFF_SCREEN], one only a few
 * pixels long gets [TINY], everything else (and the followed, selected and sub-watched animals) is left out (N = 1).
 *
 * No stale pose pops into view: "off screen" means outside the frustum by [OFF_MARGIN_CM], and every frame an
 * off-screen animal is re-tested with the smaller [REENTRY_MARGIN_CM]; once it is that close the map is republished at
 * once without it, so its next snapshot is posed afresh (core `Organism.snapshotPose` rebuilds as soon as N is 1)
 * while it is still outside the view. The gap between the two margins covers a stale pose (up to [OFF_SCREEN]
 * snapshots old) plus a snapshot and the interpolation delay at a fast fish's dart. At sim speeds above
 * [MAX_SPEED_OFF] animals travel too far between snapshots for any margin, so nothing is marked off screen then.
 *
 * Two maps are filled in turn and handed over whole, so the sim (another thread on Android) never reads a map that is
 * being refilled: one is published at least [MIN_GAP_NS] (longer than a snapshot) after the other. GL thread only.
 */
class PoseLod(private val frustum: Frustum) {
    private val maps = arrayOf(HashMap<String, Int>(), HashMap<String, Int>())
    private var flip = 0
    private var nextNs = 0L
    private var lastNs = 0L
    /** The last published map was not empty (an empty result after an empty one is not republished). */
    private var published = false
    private var keepA: String? = null
    private var keepB: String? = null
    private var keepC: String? = null

    /**
     * Called once per frame after the frustum is set. [keepA]..[keepC]: animals always posed every snapshot (followed,
     * selected, watched by the sub). [viewH]: view height in pixels. [sink] null = off.
     */
    fun update(
        nowNs: Long, rigs: Collection<FishRig>, viewProj: FloatArray, proj: FloatArray, viewH: Int, simSpeed: Double,
        keepA: String?, keepB: String?, keepC: String?, sink: ((Map<String, Int>) -> Unit)?,
    ) {
        if (sink == null) return
        var due = nowNs >= nextNs || keepA != this.keepA || keepB != this.keepB || keepC != this.keepC
        if (!due && nowNs - lastNs >= MIN_GAP_NS) {
            for (r in rigs) {
                if (r.poseEvery == OFF_SCREEN && sphere(r, REENTRY_MARGIN_CM)) { due = true; break }
            }
        }
        if (!due || nowNs - lastNs < MIN_GAP_NS) return
        this.keepA = keepA; this.keepB = keepB; this.keepC = keepC
        nextNs = nowNs + PERIOD_NS
        lastNs = nowNs
        val m = maps[flip]
        m.clear()
        val offOk = simSpeed <= MAX_SPEED_OFF
        // pixels per cm at clip w = 1 (proj[5] = cot(fov / 2) maps ±1 to the view's half height)
        val pxPerCm = proj[5] * 0.5f * viewH
        for (r in rigs) {
            val id = r.id
            var n = 1
            if (id != keepA && id != keepB && id != keepC) {
                if (offOk && !sphere(r, OFF_MARGIN_CM)) n = OFF_SCREEN
                else {
                    val s = r.spine
                    val cx = (s[0] + s[60]) * 0.5f; val cy = (s[1] + s[61]) * 0.5f; val cz = (s[2] + s[62]) * 0.5f
                    val w = viewProj[3] * cx + viewProj[7] * cy + viewProj[11] * cz + viewProj[15]
                    if (w > 1f && length(r) * pxPerCm / w < TINY_PX) n = TINY
                }
            }
            r.poseEvery = n
            if (n > 1) m[id] = n
        }
        if (m.isEmpty() && !published) return
        flip = flip xor 1
        published = m.isNotEmpty()
        sink(m)
    }

    /** The renderer's cull sphere (TankRenderer.inView: spine head to tail, padded for fins and legs) plus [margin]. */
    private fun sphere(r: FishRig, margin: Float): Boolean {
        val s = r.spine
        return frustum.sphere((s[0] + s[60]) * 0.5f, (s[1] + s[61]) * 0.5f, (s[2] + s[62]) * 0.5f, 0.5f * length(r) + r.scale + 3f + margin)
    }

    private fun length(r: FishRig): Float {
        val s = r.spine
        val dx = s[60] - s[0]; val dy = s[61] - s[1]; val dz = s[62] - s[2]
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    companion object {
        const val OFF_SCREEN = 4
        const val TINY = 2
        /** Projected body length (view pixels) below which an animal is posed every [TINY] snapshots. */
        const val TINY_PX = 12f
        /** Outside the view by this much (cm) = off screen. */
        const val OFF_MARGIN_CM = 40f
        /** An off-screen animal this close (cm) to the view is posed every snapshot again, at once. */
        const val REENTRY_MARGIN_CM = 25f
        /** Above this sim speed nothing is marked off screen. */
        const val MAX_SPEED_OFF = 2.0
        /** The regular republish interval (4 Hz). */
        const val PERIOD_NS = 250_000_000L
        /** Never two publishes closer than this (longer than one 20 Hz snapshot). */
        const val MIN_GAP_NS = 60_000_000L
    }
}
