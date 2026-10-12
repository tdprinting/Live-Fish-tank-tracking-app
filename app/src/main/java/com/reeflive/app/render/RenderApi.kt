/**
 * # ReefLive 3D renderer (`com.reeflive.app.render`) — API note
 *
 * A custom OpenGL ES 3.0 renderer (ARCHITECTURE §8). It only *reads* the simulation: it consumes
 * [com.reeflive.core.sim.SimSnapshot] (§8.8) as-is and never calls into the sim.
 *
 * ## Drop-in Compose usage (LiveTankScreen, owned by the simulation track)
 * ```kotlin
 * TankView(
 *     snapshots = holder.snapshots,          // Flow<SimSnapshot> (or pass `provider = { holder.latest() }`)
 *     followId = followedId,                 // organism id to follow close up, or null for the orbit camera
 *     onPick = { id -> followedId = id },    // tap: organism id under the finger, or null (empty water)
 *     onTapGlass = { p -> sim.enqueue(Command.TapGlass(p)) },     // double tap, world cm on the front glass
 *     onLongPress = { p -> sim.enqueue(Command.Feed(FoodKind.FLAKE, 0.1, p)) }, // long press, world cm
 *     quality = RenderQuality.MEDIUM,
 *     showHud = BuildConfig-or-settings flag,
 * )
 * ```
 * The view pauses its GL thread when the lifecycle leaves RESUMED and resumes with it.
 *
 * ## Imperative API ([TankGlView])
 * - `setSnapshotProvider { SimSnapshot? }`: polled once per frame on the GL thread (must be thread-safe,
 *   e.g. an `AtomicReference.get()`); the renderer interpolates organisms between the last two distinct snapshots.
 * - `follow(id: String?)`: smooth close-up follow camera from the organism's SimSnapshot pose, collision-tested
 *   against the rock SDF (`assets/sim/rock_sdf.bin`). Orbit/pinch gestures adjust the follow offset.
 *   `null` returns to the orbit camera. If the organism disappears the view calls `onFollowLost`.
 * - `setQuality(RenderQuality)`: HIGH (4x MSAA FBO + bloom + light shafts), MEDIUM (2x MSAA, shafts),
 *   LOW (no MSAA, no shafts, no normal maps, half particles, textures ≤ 512²). Fish beyond 60 cm use LOD1.
 * - `resetCamera()`, `listener = TankViewListener` (pick / tap glass / long press / follow lost; main thread).
 * - `stats: StateFlow<RenderStats>`: fps, draw calls, triangles, GL errors, load progress (drives [DebugHud]).
 *
 * ## Conventions
 * World units cm, origin at the inside bottom-front-left corner (+X right, +Y up, +Z toward the viewer).
 * Shaders live in `assets/shaders/` (GLSL ES 3.00, `#include` resolved by [ShaderProgram]).
 * In debuggable builds every GL call is followed by `glGetError` ([Gl.check]); errors are logged under the
 * tag `ReefGL` and counted in [RenderStats.glErrors].
 */
package com.reeflive.app.render

import com.reeflive.core.geom.Vec3

/** Quality presets (ARCHITECTURE §8.7). */
enum class RenderQuality(
    val msaa: Int,
    val bloom: Boolean,
    val shafts: Boolean,
    val normalMaps: Boolean,
    val particles: Int,
    /** Largest texture edge uploaded (bigger PNGs are subsampled on decode). */
    val maxTexture: Int,
    /** Fraction of polyp instances drawn for the big GSP mats. */
    val polypDensity: Float,
    val shaderLevel: Int,
) {
    HIGH(4, true, true, true, 300, 2048, 1f, 2),
    MEDIUM(2, false, true, true, 300, 2048, 0.5f, 1),
    LOW(0, false, false, false, 120, 512, 0.5f, 0);

    companion object {
        /** Camera distance (cm) beyond which fish switch to their `_lod1.glb` (ARCHITECTURE §8.7). */
        const val LOD1_DISTANCE_CM = 60f
    }
}

/** Callbacks from the view; all delivered on the main thread. */
interface TankViewListener {
    /** Single tap: the organism id under the finger, or null when nothing was hit. */
    fun onPick(id: String?) {}
    /** Double tap: the world point (cm) on the front glass under the finger. */
    fun onTapGlass(worldPoint: Vec3) {}
    /** Long press: the first rock/sand/water point under the finger (cm), e.g. to drop food. */
    fun onLongPress(worldPoint: Vec3) {}
    /** The followed organism is gone from the snapshot (removed, eaten); the camera returned to orbit. */
    fun onFollowLost(id: String) {}
}

/** Frame statistics for the [DebugHud]. */
data class RenderStats(
    val fps: Float = 0f,
    val frameMs: Float = 0f,
    val drawCalls: Int = 0,
    val triangles: Int = 0,
    val glErrors: Int = 0,
    val modelsLoaded: Int = 0,
    val modelsTotal: Int = 0,
    val quality: RenderQuality = RenderQuality.MEDIUM,
    val following: String? = null,
    val organisms: Int = 0,
    val lightIntensity: Float = 0f,
    val glInfo: String = "",
)
