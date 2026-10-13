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
    /** Receives the camera projection every frame (for overlays positioned in tank coordinates). */
    projection: TankProjection? = null,
    /** The supply shelf beside the tank (Supplies mode), driven through this handle; null = no shelf. */
    shelf: SupplyShelfControl? = null,
) {
    val latest = remember { AtomicReference<SimSnapshot?>(null) }
    LaunchedEffect(snapshots) { snapshots.collect { latest.set(it) } }
    TankView(
        provider = { latest.get() }, modifier = modifier, followId = followId, quality = quality, showHud = showHud,
        onPick = onPick, onTapGlass = onTapGlass, onLongPress = onLongPress, onFollowLost = onFollowLost,
        demoWhenEmpty = false, cleaning = cleaning, onScrape = onScrape, onFlipperTap = onFlipperTap, onFollow = onFollow, projection = projection,
        shelf = shelf,
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
    projection: TankProjection? = null,
    shelf: SupplyShelfControl? = null,
) {
    val pick by rememberUpdatedState(onPick)
    val followReq by rememberUpdatedState(onFollow)
    val scrape by rememberUpdatedState(onScrape)
    val flipperTap by rememberUpdatedState(onFlipperTap)
    val glass by rememberUpdatedState(onTapGlass)
    val press by rememberUpdatedState(onLongPress)
    val lost by rememberUpdatedState(onFollowLost)
    val currentProvider by rememberUpdatedState(provider)
    val listener = remember {
        object : TankViewListener {
            override fun onPick(id: String?) = pick(id)
            override fun onTapGlass(worldPoint: Vec3) = glass(worldPoint)
            override fun onLongPress(worldPoint: Vec3) = press(worldPoint)
            override fun onFollowLost(id: String) = lost(id)
            override fun onScrape(stroke: GlassStroke) = scrape(stroke)
            override fun onFlipperTap() = flipperTap()
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
                        v.shelf = shelf
                        view = v
                    }
                },
                update = { v ->
                    v.setQuality(quality)
                    v.follow(followId)
                    v.demoWhenEmpty = demoWhenEmpty
                    v.setCleaning(cleaning)
                    v.projection = projection
                    v.shelf = shelf
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
