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
}
