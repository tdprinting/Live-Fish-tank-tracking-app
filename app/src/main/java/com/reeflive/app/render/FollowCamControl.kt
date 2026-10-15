package com.reeflive.app.render

/**
 * The follow camera's on-screen buttons (zoom in / out, swing round the fish): any thread queues presses, the GL
 * thread hands them to the [Camera] once per frame ([drain]). Presses add up, so quick taps go further.
 */
class FollowCamControl {
    private var zoomSteps = 0f
    private var orbitRadians = 0f

    /** One zoom step closer (> 0) or further out (< 0). */
    @Synchronized fun zoom(steps: Float) { zoomSteps += steps }

    /** Swings the view round the followed fish by [radians] (positive = the camera moves to the fish's right). */
    @Synchronized fun orbit(radians: Float) { orbitRadians += radians }

    /** GL thread. */
    @Synchronized fun drain(camera: Camera) {
        if (zoomSteps != 0f) { camera.nudgeZoom(zoomSteps); zoomSteps = 0f }
        if (orbitRadians != 0f) { camera.nudgeOrbit(orbitRadians); orbitRadians = 0f }
    }

    companion object {
        /** One press of a swing button (radians, 45°). */
        const val ORBIT_STEP = 0.785f
    }
}
