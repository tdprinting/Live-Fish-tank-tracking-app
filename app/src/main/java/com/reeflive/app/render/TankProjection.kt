package com.reeflive.app.render

/**
 * World → screen projection of the live camera, for Compose overlays drawn over the GL view (the in-tank action
 * animations, `ui/live/ActionFx.kt`). The GL thread [publish]es the camera's view-projection after every
 * `Camera.update`; any thread may [project] with the latest copy. Writes and reads share one small lock and copy 18
 * floats, so neither side allocates per frame and a reader never sees a half-written matrix.
 *
 * Pixel coordinates are the GL view's own (origin top-left, y down), which equal Compose pixels of a layer laid out
 * at the same position and size as the view.
 */
class TankProjection {
    private val lock = Any()
    private val m = FloatArray(16)
    private var proj5 = 1f
    private var widthPx = 0
    private var heightPx = 0
    @Volatile private var ready = false

    /** True once the renderer has drawn a frame. */
    val isReady: Boolean get() = ready

    /** GL thread: copies the current camera matrices and viewport. */
    fun publish(viewProj: FloatArray, proj: FloatArray, w: Int, h: Int) {
        synchronized(lock) {
            System.arraycopy(viewProj, 0, m, 0, 16)
            proj5 = proj[5]
            widthPx = w; heightPx = h
        }
        ready = true
    }

    /**
     * Projects the world point ([x], [y], [z] in tank cm) into view pixels: [out] = (x px, y px, px per cm at that
     * depth). Returns false (and leaves [out] untouched) before the first frame or when the point is behind the eye.
     */
    fun project(x: Float, y: Float, z: Float, out: FloatArray): Boolean {
        if (!ready) return false
        synchronized(lock) {
            val cx = m[0] * x + m[4] * y + m[8] * z + m[12]
            val cy = m[1] * x + m[5] * y + m[9] * z + m[13]
            val cw = m[3] * x + m[7] * y + m[11] * z + m[15]
            if (cw <= 1e-3f || widthPx == 0) return false
            out[0] = (0.5f + 0.5f * cx / cw) * widthPx
            out[1] = (0.5f - 0.5f * cy / cw) * heightPx
            out[2] = 0.5f * proj5 * heightPx / cw
        }
        return true
    }

    private val gp = FloatArray(3)

    /**
     * Which point on the plane z = [z] (tank cm x, y) is under view pixel ([sx], [sy]): a few Newton steps on [project]
     * (a homography, so it converges in 3–4). Writes x, y into [out]; false if it did not converge (point behind the
     * camera or off at a grazing angle). Call from one thread at a time (it reuses a scratch buffer).
     */
    fun planePointAt(sx: Float, sy: Float, z: Float, out: FloatArray): Boolean {
        val p = gp
        var x = Camera.TANK[0] / 2; var y = 61f / 2
        val h = 1f
        repeat(8) {
            if (!project(x, y, z, p)) return false
            val fx = p[0] - sx; val fy = p[1] - sy
            if (kotlin.math.abs(fx) < 0.5f && kotlin.math.abs(fy) < 0.5f) { out[0] = x; out[1] = y; return true }
            val px = p[0]; val py = p[1]
            if (!project(x + h, y, z, p)) return false
            val a = (p[0] - px) / h; val c = (p[1] - py) / h
            if (!project(x, y + h, z, p)) return false
            val b = (p[0] - px) / h; val d = (p[1] - py) / h
            val det = a * d - b * c
            if (kotlin.math.abs(det) < 1e-6f) return false
            x -= (d * fx - b * fy) / det
            y -= (-c * fx + a * fy) / det
            x = x.coerceIn(-400f, 500f); y = y.coerceIn(-400f, 400f)
        }
        if (!project(x, y, z, p)) return false
        out[0] = x; out[1] = y
        return kotlin.math.abs(p[0] - sx) < 3f && kotlin.math.abs(p[1] - sy) < 3f
    }
}
