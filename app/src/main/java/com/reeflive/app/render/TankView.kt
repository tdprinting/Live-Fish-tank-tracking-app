package com.reeflive.app.render

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.reeflive.core.geom.Vec3
import com.reeflive.core.sim.SimSnapshot
import com.reeflive.core.sim.TouchSurface
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.atomic.AtomicReference

/**
 * Live Tank 3D view for Compose, fed by a snapshot [Flow] (e.g. the SimulationHolder's StateFlow).
 * See the package API note in RenderApi.kt.
 */
@Composable
fun TankView(
    snapshots: Flow<SimSnapshot>,
    modifier: Modifier = Modifier,
    followId: String? = null,
    quality: RenderQuality = RenderQuality.MEDIUM,
    showHud: Boolean = false,
    onPick: (String?) -> Unit = {},
    onTapGlass: (Vec3) -> Unit = {},
    onLongPress: (Vec3) -> Unit = {},
    onFollowLost: (String) -> Unit = {},
    cleaning: Boolean = false,
    onScrape: (GlassStroke) -> Unit = {},
    onFlipperTap: () -> Unit = {},
    /** Double tap on a fish (follow it), a tap on another fish while following, or null to stop following. */
    onFollow: (String?) -> Unit = {},
    /** A tap with no animal under the finger: where and on what it landed ([TankViewListener.onTouchTank]); null = [onPick] (null). */
    onTouchTank: ((Vec3, TouchSurface) -> Unit)? = null,
    /** A fingertip held on the glass or the water's top after a long press (null point = lifted; [TankViewListener.onFinger]). */
    onFinger: (Vec3?, Float, TouchSurface) -> Unit = { _, _, _ -> },
    /** The held finger flicked as it lifted: a puff of water ([TankViewListener.onFlick]). */
    onFlick: (Vec3, Vec3) -> Unit = { _, _ -> },
    /** Receives the camera projection every frame (for overlays positioned in tank coordinates). */
    projection: TankProjection? = null,
    /** In-tank feedback for actions (dye plumes, bubbles, pump jets): the screen emits, the renderer draws. */
    bursts: TankBursts? = null,
    /** Watch-mode lamp look (render only). */
    viewLight: ViewLight = ViewLight.REAL,
    /** The supply shelf beside the tank (Supplies mode), driven through this handle; null = no shelf. */
    shelf: SupplyShelfControl? = null,
    /** "Fix my tank" upkeep shows (skimmer cup, filter sock, dosing, water change), played through this handle. */
    care: CareShowControl? = null,
    /** Submarine mode: ride inside the sub instead of chasing it. */
    subFirstPerson: Boolean = false,
    /** Bumped for each "Look at the nursery" press: the camera glides to the nursery tank. */
    nurseryRequest: Int = 0,
    /** The follow camera's on-screen buttons (zoom, swing round); null = gestures only. */
    followCam: FollowCamControl? = null,
    /** A tap on a piece of gear: pop out its menu at that view pixel ([TankViewListener.onSpot]); null = [onPick] (null). */
    onSpot: ((com.reeflive.core.sim.TankSpot, Float, Float) -> Unit)? = null,
    /** Tap-to-place: while armed, the next tap aims instead of picking ([TankAimControl]). */
    aim: TankAimControl? = null,
    /** Screensaver mode over this view ([CinemaSession]); it drives the camera while active. */
    cinema: CinemaSession? = null,
) {
    val latest = remember { AtomicReference<SimSnapshot?>(null) }
    LaunchedEffect(snapshots) { snapshots.collect { latest.set(it) } }
    TankView(
        provider = { latest.get() }, modifier = modifier, followId = followId, quality = quality, showHud = showHud,
        onPick = onPick, onTapGlass = onTapGlass, onLongPress = onLongPress, onFollowLost = onFollowLost,
        demoWhenEmpty = false, cleaning = cleaning, onScrape = onScrape, onFlipperTap = onFlipperTap, onFollow = onFollow, onTouchTank = onTouchTank,
        onFinger = onFinger, onFlick = onFlick, projection = projection,
        bursts = bursts, viewLight = viewLight, shelf = shelf, care = care, subFirstPerson = subFirstPerson, followCam = followCam,
        nurseryRequest = nurseryRequest, onSpot = onSpot, aim = aim, cinema = cinema,
    )
}

/**
 * Live Tank 3D view for Compose, polling [provider] on the GL thread once per frame (must be
 * thread-safe and cheap, e.g. `AtomicReference::get`). With [demoWhenEmpty] the circling-fish test
 * harness is shown while the provider returns null.
 */
@Composable
fun TankView(
    provider: () -> SimSnapshot?,
    modifier: Modifier = Modifier,
    followId: String? = null,
    quality: RenderQuality = RenderQuality.MEDIUM,
    showHud: Boolean = false,
    onPick: (String?) -> Unit = {},
    onTapGlass: (Vec3) -> Unit = {},
    onLongPress: (Vec3) -> Unit = {},
    onFollowLost: (String) -> Unit = {},
    demoWhenEmpty: Boolean = true,
    /** Clean-glass mode: the camera faces the front glass and one-finger drags slide the Flipper ([onScrape]). */
    cleaning: Boolean = false,
    onScrape: (GlassStroke) -> Unit = {},
    onFlipperTap: () -> Unit = {},
    /** Double tap on a fish (follow it), a tap on another fish while following, or null to stop following. */
    onFollow: (String?) -> Unit = {},
    /** A tap with no animal under the finger: where and on what it landed ([TankViewListener.onTouchTank]); null = [onPick] (null). */
    onTouchTank: ((Vec3, TouchSurface) -> Unit)? = null,
    onFinger: (Vec3?, Float, TouchSurface) -> Unit = { _, _, _ -> },
    onFlick: (Vec3, Vec3) -> Unit = { _, _ -> },
    projection: TankProjection? = null,
    bursts: TankBursts? = null,
    viewLight: ViewLight = ViewLight.REAL,
    shelf: SupplyShelfControl? = null,
    care: CareShowControl? = null,
    subFirstPerson: Boolean = false,
    nurseryRequest: Int = 0,
    followCam: FollowCamControl? = null,
    onSpot: ((com.reeflive.core.sim.TankSpot, Float, Float) -> Unit)? = null,
    aim: TankAimControl? = null,
    cinema: CinemaSession? = null,
) {
    val pick by rememberUpdatedState(onPick)
    val cine by rememberUpdatedState(cinema)
    val followReq by rememberUpdatedState(onFollow)
    val scrape by rememberUpdatedState(onScrape)
    val flipperTap by rememberUpdatedState(onFlipperTap)
    val glass by rememberUpdatedState(onTapGlass)
    val touch by rememberUpdatedState(onTouchTank)
    val spotTap by rememberUpdatedState(onSpot)
    val finger by rememberUpdatedState(onFinger)
    val flick by rememberUpdatedState(onFlick)
    val press by rememberUpdatedState(onLongPress)
    val lost by rememberUpdatedState(onFollowLost)
    val currentProvider by rememberUpdatedState(provider)
    val listener = remember {
        object : TankViewListener {
            override fun onPick(id: String?) = pick(id)
            override fun onTapGlass(worldPoint: Vec3) = glass(worldPoint)
            override fun onTouchTank(worldPoint: Vec3, surface: TouchSurface) {
                val f = touch
                if (f != null) f(worldPoint, surface) else pick(null)
            }
            override fun onLongPress(worldPoint: Vec3) = press(worldPoint)
            override fun onFinger(worldPoint: Vec3?, speedCmS: Float, surface: TouchSurface) = finger(worldPoint, speedCmS, surface)
            override fun onFlick(worldPoint: Vec3, velocity: Vec3) = flick(worldPoint, velocity)
            override fun onFollowLost(id: String) {
                // a screensaver follow is the director's, not the keeper's
                val c = cine
                if (c != null && c.active) c.followLost() else lost(id)
            }
            override fun onScrape(stroke: GlassStroke) = scrape(stroke)
            override fun onFlipperTap() = flipperTap()
            override fun onSpot(spot: com.reeflive.core.sim.TankSpot, x: Float, y: Float) {
                val f = spotTap
                if (f != null) f(spot, x, y) else pick(null)
            }
            override fun onFollowRequest(id: String?) = followReq(id)
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var view by remember { mutableStateOf<TankGlView?>(null) }

    Box(modifier) {
        // Window MSAA is fixed at EGL config time: recreate the view when the preset's MSAA changes.
        key(if (quality.bloom) 0 else quality.msaa) {
            AndroidView(
                factory = { ctx ->
                    TankGlView(ctx, quality).also { v ->
                        v.listener = listener
                        v.setSnapshotProvider { currentProvider() }
                        v.projection = projection
                        v.bursts = bursts
                        v.viewLight = viewLight
                        v.shelf = shelf
                        v.care = care
                        v.aim = aim
                        if (followCam != null) v.followCam = followCam
                        cinema?.attach(v.renderer) { v.height.toFloat() }
                        view = v
                    }
                },
                update = { v ->
                    v.setQuality(quality)
                    // while the screensaver runs it owns the follow; the keeper's comes back with their view
                    if (cinema?.active != true) v.follow(followId)
                    cinema?.attach(v.renderer) { v.height.toFloat() }
                    v.demoWhenEmpty = demoWhenEmpty
                    v.setCleaning(cleaning)
                    v.projection = projection
                    v.bursts = bursts
                    v.viewLight = viewLight
                    v.shelf = shelf
                    v.care = care
                    v.aim = aim
                    v.subFirstPerson = subFirstPerson
                    v.nurseryRequest = nurseryRequest
                    if (followCam != null) v.followCam = followCam
                },
                onRelease = { v -> v.onPause(); if (view === v) view = null },
            )
        }
        val v = view
        if (showHud && v != null) {
            DebugHud(v.stats, Modifier.align(Alignment.TopStart).padding(8.dp))
        }
    }

    // Pause the GL thread when the screen leaves RESUMED (ARCHITECTURE §9).
    DisposableEffect(lifecycle, view) {
        val v = view
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> v?.onResume()
                Lifecycle.Event.ON_PAUSE -> v?.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
}
