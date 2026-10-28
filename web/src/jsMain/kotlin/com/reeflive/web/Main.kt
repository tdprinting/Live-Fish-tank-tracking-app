@file:OptIn(ExperimentalJsExport::class)

package com.reeflive.web

import android.content.Context
import android.content.res.AssetManager
import android.opengl.GLES30
import android.view.MotionEvent
import android.view.View
import com.reeflive.app.render.AdaptiveQuality
import com.reeflive.app.render.RenderQuality
import com.reeflive.app.render.TankRenderer
import com.reeflive.app.render.TankViewListener
import com.reeflive.app.render.TouchController
import com.reeflive.core.geom.Vec3
import com.reeflive.core.params.ParameterTable
import com.reeflive.core.sim.DefaultStock
import com.reeflive.core.sim.SimSnapshot
import com.reeflive.core.sim.Stocking
import com.reeflive.core.sim.TouchSurface
import com.reeflive.core.tracker.AcclimationMethod
import com.reeflive.core.tracker.AcclimationPlan
import com.reeflive.core.tracker.CycleReading
import com.reeflive.core.tracker.CycleTracker
import com.reeflive.app.render.BurstKind
import com.reeflive.app.render.CareAct
import com.reeflive.app.render.CareJob
import com.reeflive.app.render.CareShowControl
import com.reeflive.app.render.GlassStroke
import com.reeflive.app.render.RoomLook
import com.reeflive.app.render.SupplyShelfControl
import com.reeflive.app.render.TankBurst
import com.reeflive.app.render.TankBursts
import com.reeflive.app.ui.live.ThoughtBubblePicker
import com.reeflive.core.custom.economy.DisplaySetup
import com.reeflive.core.sim.BuiltRock
import java.util.concurrent.AssetPrefetch
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.pointerevents.PointerEvent
import kotlin.js.Promise
import kotlin.math.min
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Browser host for the shared 3D renderer (app/.../render), compiled from the same Kotlin sources as the Android app:
 * the part TankGlView plays on Android. It drives TankRenderer every animation frame from the sim's newest snapshot
 * and turns pointer events into MotionEvents for the shared TouchController. The sim itself is [ReefSim] (with
 * [ReefSandbox] and [ReefMyTank]), on this thread or in a Web Worker: this object reaches it only through [link] and
 * [snapshotSource], and the sim reaches the view through [applyFx] / [localFx]. The page's SimHost (simhost.js) sets
 * which ([bindInThread] or [bindRemote]).
 */
@JsExport
object ReefLive {
    internal var renderer: TankRenderer? = null
    /** Effect bursts the sandbox's actions play in the tank (render TankBursts). */
    internal val bursts = TankBursts()
    /** "Fix tank": the 3D upkeep show (render CareScene). */
    internal val care = CareShowControl()
    internal val shelf = SupplyShelfControl()
    /** A fish store visit drawn around the display tank (ReefMyTank.visit); taps on other tanks / the counter come back. */
    internal val room = com.reeflive.app.render.StoreRoomControl()
    /** The stand and room round the tank (My tank's bought ones; Tee's in Watch). */
    internal val homeRoom = com.reeflive.app.render.RoomControl()
    /** The record stand and the radio left of the tank (radio.js drives it). */
    internal val music = com.reeflive.app.render.MusicCornerControl()
    /** Tap-to-place food and gear taps (render TankAimControl). */
    private val aim = com.reeflive.app.render.TankAimControl()
    private var touch: TouchController? = null
    private var canvas: HTMLCanvasElement? = null
    private var listener: dynamic = null
    private var lastFrame = -1.0
    private var running = false
    private var pendingTouch: Pair<Vec3, TouchSurface>? = null
    /** Sends a command to the sim side: [ReefSim]'s function of that name with these arguments. */
    private var link: (String, Array<Any?>) -> Unit = { _, _ -> }
    /** The sim's newest snapshot (this thread's sim, or the copy the worker last sent). */
    private var snapshotSource: () -> SimSnapshot? = { null }
    /** Steps the sim on this thread each frame (a worker keeps its own clock). */
    private var simFrame: (Double) -> Unit = {}
    /** The worker's newest snapshot, decoded ([mirror]). */
    private var mirrorSnap: SimSnapshot? = null
    private fun latest(): SimSnapshot? = snapshotSource()
    /** Screensaver mode over the keeper's view (same as the Android Live tank): [screensaver], [cinema]. */
    private val cinemaSession = com.reeflive.app.render.CinemaSession()
    private var fingerDown = false
    /** Camera projection the renderer publishes every frame (bag drops and the bag timers). */
    private val projection = com.reeflive.app.render.TankProjection()
    private val bagPt = FloatArray(3)

    /**
     * Starts the live tank on [cnv]. [baseUrl] is where `assets/` and `res/` are served; [events] gets
     * `onStatus(text)`, `onPick(id)`, `onFollow(id)`, `onError(text)`. Resolves once the first frame is drawn.
     */
    fun start(cnv: HTMLCanvasElement, baseUrl: String, quality: String, events: dynamic): Promise<Unit> = Promise { resolve, reject ->
        Net.base = baseUrl.trimEnd('/') + "/"
        listener = events
        canvas = cnv
        MainScope().launch {
            try {
                status("Loading the tank…")
                // the asset index, shaders, sim tables and species sheets in one file (web/build.gradle.kts bootPack)
                Net.loadPack("boot.json")
                val index = Net.fetchJson("assets/index.json")
                val files = HashMap<String, List<String>>()
                for (k in js("Object").keys(index).unsafeCast<Array<String>>()) files[k] = index[k].unsafeCast<Array<String>>().toList()
                val boot = ArrayList<String>()
                files["shaders"]?.forEach { boot += "assets/shaders/$it" }
                boot += listOf("assets/sim/rock_sdf.bin", "assets/sim/anchors.json", "res/sim/interactions.json")
                val have = Net.fetchJson("res/species/index.json")
                // Every species with a sheet (the renderer reads their looks): the default stock plus everything Add life can release.
                val known = (have.species.unsafeCast<Array<String>?>() ?: emptyArray()).toSet()
                val speciesIds = (DefaultStock.all.map { it.speciesId } + Stocking.CATALOG.map { it.speciesId } + known)
                    .distinct().filter { known.isEmpty() || it in known }
                speciesIds.forEach { id -> boot += "res/species/$id.json" }
                Net.fetchAll(boot)
                AssetPrefetch.fetcher = { path, done -> MainScope().launch { Net.fetchWithRetry("assets/$path"); done() } }

                status("Building the reef…")

                val gl = cnv.getContext("webgl2", js("({antialias: false, alpha: false, depth: true, stencil: false, powerPreference: 'high-performance'})"))
                    ?: throw IllegalStateException("This browser has no WebGL 2")
                GLES30.gl = gl
                val q = parseQuality(quality) ?: RenderQuality.HIGH
                current = q
                val r = TankRenderer(Context(AssetManager(files)), q)
                r.snapshotProvider = { snapshotSource() }
                // pose level of detail (TankRenderer PoseLod → Simulation.poseDetail): off-screen and tiny animals are
                // posed less often (ReefSim.poseDetail)
                r.poseDetailSink = { m ->
                    val ids = arrayOfNulls<String>(m.size); val levels = IntArray(m.size); var i = 0
                    for ((k, v) in m) { ids[i] = k; levels[i] = v; i++ }
                    link("poseDetail", arrayOf(ids, levels))
                }
                r.projection = projection
                r.demoWhenEmpty = false
                // the canvas has no MSAA of its own (it doubled the work on 3x phones): PostFx anti-aliases
                r.msaaInPost = true
                r.listener = object : TankViewListener {
                    // a tap right on an animal also touches it: snails and crabs tuck in, fish just get picked (TankTouch)
                    override fun onPick(id: String?) { if (id != null) link("touchAnimal", arrayOf(id)); emit("onPick", id) }
                    override fun onFollowRequest(id: String?) { r.follow(id); emit("onFollow", id) }
                    // pressed and held on an animal: follow it (the page buzzes and says who)
                    override fun onHoldAnimal(id: String) { r.follow(id); emit("onFollow", id); emit("onHold", id) }
                    override fun onFollowLost(id: String) { if (cinemaSession.active) cinemaSession.followLost() else emit("onFollow", null) }
                    override fun onTapGlass(worldPoint: Vec3) { link("tapGlass", arrayOf(worldPoint.x, worldPoint.y, worldPoint.z)); emit("onKnock", null) }
                    // a tap with no fish under it: the page decides (undo a card / hidden buttons first), then touchTank()
                    override fun onTouchTank(worldPoint: Vec3, surface: TouchSurface) { pendingTouch = worldPoint to surface; emit("onTouch", surface.name) }
                    // as on Android (LiveTankScreen): a long press drops a pinch of pellets where the finger is
                    override fun onLongPress(worldPoint: Vec3) { link("pelletAt", arrayOf(worldPoint.x, worldPoint.y, worldPoint.z)); emit("onFed", "PELLET") }
                    // hold on the glass: a fingertip the fish follow (mouse: hold the button still, then drag); the page
                    // only hears when it goes down and when it lifts
                    override fun onFinger(worldPoint: Vec3?, speedCmS: Float, surface: TouchSurface) {
                        link("finger", arrayOf(worldPoint != null, worldPoint?.x ?: 0.0, worldPoint?.y ?: 0.0, worldPoint?.z ?: 0.0, speedCmS.toDouble(), surface.name))
                        val down = worldPoint != null
                        if (down != fingerDown) { fingerDown = down; emit("onFinger", if (down) surface.name else null) }
                    }
                    override fun onFlick(worldPoint: Vec3, velocity: Vec3) { link("puff", arrayOf(worldPoint.x, worldPoint.y, worldPoint.z, velocity.x, velocity.y, velocity.z)); emit("onFlick", null) }
                    override fun onScrape(stroke: GlassStroke) { link("scrape", arrayOf(stroke.x0.toDouble(), stroke.y0.toDouble(), stroke.x1.toDouble(), stroke.y1.toDouble())); emit("onScrape", stroke.lengthCm) }
                    override fun onFlipperTap() { emit("onFlipperTap", null) }
                    // a tap on a piece of gear: the page pops out its menu (ReefSandbox.spotMenu) where the finger landed
                    override fun onSpot(spot: com.reeflive.core.sim.TankSpot, x: Float, y: Float) {
                        val c = canvas ?: return
                        val k = c.getBoundingClientRect().width / c.width
                        emit("onSpot", obj { it.spot = spot.name; it.x = x * k; it.y = y * k })
                    }
                }
                aim.onAim = { h -> emit("onAim", obj { it.x = h.point.x; it.y = h.point.y; it.z = h.point.z; it.surface = h.surface.name; it.id = h.animalId }) }
                r.aim = aim
                shelf.onPick = { item, _, _, _ -> emit("onShelfPick", item) }
                r.shelf = shelf
                room.onStand = { i -> emit("onStoreStand", i) }
                room.onCounter = { emit("onStoreCounter", null) }
                room.onTrade = { emit("onStoreTrade", null) }
                r.storeRoom = room
                r.room = homeRoom
                music.onRecord = { i -> emit("onMusicRecord", i) }
                music.onVolume = { v, done -> emit("onMusicVolume", obj { it.volume = v; it.done = done }) }
                music.onTune = { emit("onMusicTune", null) }
                music.onPower = { emit("onMusicPower", null) }
                music.onView = { on -> emit("onMusicView", on) }
                r.music = music
                r.bursts = bursts
                r.care = care
                renderer = r
                r.onSurfaceCreated(null, null)
                resize()
                touch = TouchController(Context(AssetManager(files)), View(), r.input)
                resize()   // TouchController sets its own finger size from the slop; the page's px are device px
                attachPointers(cnv)
                window.addEventListener("resize", { resize() })
                running = true
                requestFrame()
                status("")
                resolve(Unit)
            } catch (t: Throwable) {
                console.error(t)
                emit("onError", t.message ?: t.toString())
                reject(t)
            }
        }
    }

    private var current = RenderQuality.HIGH

    /**
     * HIGH draws at up to 2 pixels per CSS pixel: with 4x MSAA that looks the same as 3x on a 3x iPhone screen, for
     * less than half the pixels (fill rate is what limits phones and iPads here). Lower presets cap it further.
     * [scaleStep] trims it only while frames run slow ([adapt]), so a fast device always gets the full resolution.
     */
    private fun pixelRatio(): Double = cap(current) * SCALES[scaleStep]
    private fun cap(q: RenderQuality): Double = min(window.devicePixelRatio, when (q) {
        RenderQuality.HIGH -> 2.0
        RenderQuality.MEDIUM -> 1.5
        RenderQuality.LOW -> 1.0
    })

    private var scaleStep = 0
    private var windowFrames = 0; private var windowTime = 0.0; private var goodWindows = 0; private var floorWindows = 0
    /** Picture "Auto" (the default): the preset follows the device; a preset picked by hand is kept as picked. */
    private var auto = true
    /** Seconds of frames not judged yet: loading and preset switches hitch, and must not count as a slow device. */
    private var grace = START_GRACE
    // the slow-down being tried: mean frame time before it, where it started, and steps taken since
    private var tryBase = -1.0; private var tryStep = 0; private var tryQuality = RenderQuality.HIGH; private var trySteps = 0
    /**
     * Fewer pixels did not help (a 30 fps cap in Low Power Mode, or the CPU is the limit): stop trading sharpness for
     * [HOLD_SECONDS]. Only a while, and only at the picture it went back to: a close zoom or a busy moment can be slow
     * for reasons pixels don't fix, and a hold that outlived it kept a sharper picture the device could not keep up
     * with once the view changed back.
     */
    private var frozen = false
    private var heldFor = 0.0
    /**
     * Seconds of judged frames this visit. A preset switch rebuilds every model and texture (TankRenderer: a new
     * AssetStore), which looks like the whole game reloading, so Auto only switches live during the first
     * [LIVE_SWITCH_SECONDS]; after that it sheds effects and pixels at runtime and saves its verdict for the next visit.
     */
    private var judged = 0.0
    /** The preset Auto has saved for the next visit, so each verdict is stored once. */
    private var savedLevel: RenderQuality? = null

    /** "HIGH" / "MEDIUM" / "LOW" picked by hand, or "AUTO" / "AUTO:<level it settled on last time>". */
    private fun parseQuality(q: String): RenderQuality? {
        val u = q.uppercase()
        auto = u.startsWith("AUTO")
        val level = if (auto) u.substringAfter(':', "HIGH") else u
        return runCatching { RenderQuality.valueOf(level) }.getOrNull()
    }

    /** Lowest [SCALES] notch: a hand-picked preset loses at most a fifth of its sharpness; Auto goes to 0.72 before it
     *  sheds effects, and all the way down only on Low. */
    private fun floorStep(): Int = when {
        !auto -> 2
        current != RenderQuality.LOW -> 3
        else -> SCALES.size - 1
    }

    /**
     * Dynamic resolution: over each [ADAPT_FRAMES]-frame window, a mean frame time above [SLOW_DT] steps the render
     * resolution down one [SCALES] notch, to [floorStep]; [GOOD_WINDOWS] windows in a row under [FAST_DT] step it back
     * up. Each frame counts at most 0.1 s, so one long hitch cannot step it down alone. On Auto, still slow at the
     * floor for [FLOOR_WINDOWS] windows drops one preset (HIGH → MEDIUM → LOW, shedding bloom, 4x MSAA, fish in the
     * shadow map...) at about the same pixel count; the page remembers it ("onQuality") for the next visit, and a
     * visit that runs fast at full resolution climbs back a preset next time. Only the first [LIVE_SWITCH_SECONDS]
     * switch presets live (a switch rebuilds every model and texture); later, Auto sheds effects instead. Every two
     * steps down are checked: if frames did not get at least 7% faster, pixels were not the limit, so the picture goes
     * back to where it was and holds there for [HOLD_SECONDS], or until frames are fast enough to climb.
     */
    private fun adapt(dt: Double) {
        if (dt <= 0.0) return
        if (grace > 0.0) { grace -= dt; return }
        if (frozen) { heldFor += dt; if (heldFor >= HOLD_SECONDS) frozen = false }
        judged += dt
        windowTime += min(dt, 0.1); windowFrames++
        if (windowFrames < ADAPT_FRAMES) return
        val mean = windowTime / windowFrames
        windowTime = 0.0; windowFrames = 0
        if (mean > SLOW_DT) {
            goodWindows = 0
            if (frozen) return
            if (tryBase < 0.0) { tryBase = mean; tryStep = scaleStep; tryQuality = current; trySteps = 0 }
            else if (trySteps >= 2) {
                if (mean > tryBase * 0.93) { setPicture(tryQuality, tryStep); frozen = true; heldFor = 0.0; tryBase = -1.0; return }
                tryBase = mean; tryStep = scaleStep; tryQuality = current; trySteps = 0
            }
            if (scaleStep < floorStep()) { scaleStep++; trySteps++; floorWindows = 0 }
            else if (auto && ++floorWindows >= FLOOR_WINDOWS) {
                floorWindows = 0
                val r = renderer
                val relief = r?.relief ?: AdaptiveQuality.MAX_RELIEF
                // at the floor: first shed effects at runtime (TankRenderer.relief: no reload, no hitch), then a preset
                val early = judged < LIVE_SWITCH_SECONDS
                if (r != null && relief < (if (current == RenderQuality.LOW || !early) AdaptiveQuality.MAX_RELIEF else RELIEF_BEFORE_PRESET)) {
                    r.relief = relief + 1; grace = SWITCH_GRACE
                } else if (current != RenderQuality.LOW) {
                    val v = RenderQuality.entries[current.ordinal + 1]
                    if (early) {
                        val from = cap(current) * SCALES[scaleStep]
                        r?.relief = 0
                        // keep about the pixel count the floor had, so the cheaper preset is not straight away slowed by more pixels
                        setPicture(v, SCALES.indices.firstOrNull { cap(v) * SCALES[it] <= from + 1e-6 } ?: SCALES.size - 1)
                        trySteps++
                    } else saveLevel(v)
                }
            }
        } else {
            tryBase = -1.0; floorWindows = 0
            if (mean >= FAST_DT) { goodWindows = 0; return }
            if (++goodWindows < GOOD_WINDOWS) return
            goodWindows = 0
            // effects come back before sharpness; climbing past the held picture means the view changed, so a slow
            // window may step down again
            renderer?.let { if (it.relief > 0) { it.relief--; frozen = false; return } }
            if (scaleStep > 0) { scaleStep--; frozen = false; return }
            // full resolution, every effect on and still fast: the next visit tries the richer preset (no live rebuild)
            if (auto && current != RenderQuality.HIGH) saveLevel(RenderQuality.entries[current.ordinal - 1])
        }
    }

    private fun saveLevel(q: RenderQuality) {
        if (!auto || savedLevel == q) return
        savedLevel = q
        emit("onQuality", q.name)
    }

    private fun setPicture(q: RenderQuality, step: Int) {
        scaleStep = step
        if (q != current) {
            current = q
            renderer?.setQuality(q)
            grace = SWITCH_GRACE
            if (auto) emit("onQuality", q.name)
        }
        resize()
    }

    private fun status(text: String) { emit("onStatus", text) }

    internal fun emit(event: String, arg: Any?) {
        val l = listener ?: return
        val f = l[event]
        if (f != null) f.call(l, arg)
    }

    private fun resize() {
        val c = canvas ?: return
        val dpr = pixelRatio()
        val w = (c.clientWidth * dpr).toInt().coerceAtLeast(1)
        val h = (c.clientHeight * dpr).toInt().coerceAtLeast(1)
        if (c.width != w || c.height != h) { c.width = w; c.height = h }
        renderer?.onSurfaceChanged(null, w, h)
        // a fingertip's radius for forgiving picks: ~22 CSS px (the Android 24 dp, a bit under half a 48 px target)
        renderer?.input?.fingerPx = (22.0 * dpr).toFloat()
    }

    private fun frame(t: Double) {
        frameRequested = false
        if (!running) return
        val dt = if (lastFrame < 0) 0.0 else ((t - lastFrame) / 1000.0).coerceIn(0.0, 0.25)
        lastFrame = t
        val t0 = if (statsOn) now() else 0.0
        simFrame(dt)
        val t1 = if (statsOn) now() else 0.0
        adapt(dt)
        val c = canvas
        if (c != null) { val r = pixelRatio(); if (c.width != (c.clientWidth * r).toInt() || c.height != (c.clientHeight * r).toInt()) resize() }
        cinemaSession.update(dt.toFloat(), latest())
        renderer?.onDrawFrame(null)
        if (statsOn) stats(dt, t1 - t0, now() - t1)
        requestFrame()
    }

    /**
     * One frame loop, however often it is (re)started: the page shows the tank (setVisible) before start() is done, and
     * each used to start its own loop, so every frame was stepped and drawn twice.
     */
    private var frameRequested = false
    private fun requestFrame() {
        if (frameRequested) return
        frameRequested = true
        window.requestAnimationFrame(::frame)
    }

    // ------------------------------------------------------------------ stats overlay (?stats in the page URL)
    // read twice a frame: a plain memo (a `by lazy` getter builds a property reference object per read)
    private var statsOnMemo: Boolean? = null
    private val statsOn: Boolean get() = statsOnMemo ?: (listener?.onStats != null).also { statsOnMemo = it }
    private fun now(): Double = js("performance.now()") as Double
    private var stFrames = 0; private var stTime = 0.0; private var stSim = 0.0; private var stDraw = 0.0; private var stWorst = 0.0
    /** The worker's ms per second in the sim and in packing snapshots ([workerStats]), or -1 when the sim runs here. */
    private var workerSimMs = -1.0; private var workerPackMs = 0.0; private var unpackMs = 0.0

    /** The worker's last second: ms spent stepping the sim and packing snapshots, and the page's ms unpacking them. */
    fun workerStats(simMs: Double, packMs: Double, unpackMsPerSec: Double) { workerSimMs = simMs; workerPackMs = packMs; unpackMs = unpackMsPerSec }

    /** Once a second: frame rate, worst frame, and the CPU ms per frame spent in the sim and in issuing draws. Frame
     *  time well above sim + draw means the GPU (pixels / shaders) is the limit. */
    private fun stats(dt: Double, simMs: Double, drawMs: Double) {
        stFrames++; stTime += dt; stSim += simMs; stDraw += drawMs; stWorst = max(stWorst, dt)
        if (stTime < 1.0) return
        val c = canvas
        fun f(v: Double) = ((v * 10).roundToInt() / 10.0).toString()
        emit("onStats", "${(stFrames / stTime).roundToInt()} fps · worst ${(stWorst * 1000).roundToInt()} ms\n" +
            (if (workerSimMs >= 0) "worker sim ${f(workerSimMs)} ms/s · pack ${f(workerPackMs)} · unpack ${f(unpackMs)} ms/s\n" else "") +
            "sim ${f(stSim / stFrames)} ms · draw ${f(stDraw / stFrames)} ms\n" +
            "${c?.width}×${c?.height} (scale ${SCALES[scaleStep]}) · ${current.name.lowercase()}${if (auto) " (auto)" else ""}${if (frozen) " · held" else ""}" +
            "${renderer?.relief?.takeIf { it > 0 }?.let { " · shed $it" } ?: ""}")
        stFrames = 0; stTime = 0.0; stSim = 0.0; stDraw = 0.0; stWorst = 0.0
    }

    /** Pause rendering and the sim while the page is hidden; on return the sim skips ahead like the Android app. */
    fun setVisible(visible: Boolean) {
        if (visible == running) return
        running = visible
        if (visible) {
            // (the page shows the tank before it has started: nothing was away then)
            val away = if (hiddenAt > 0) (js("Date.now()") as Double - hiddenAt) / 1000.0 else 0.0
            link("setVisible", arrayOf(true, away))
            lastFrame = -1.0
            requestFrame()
        } else {
            hiddenAt = js("Date.now()") as Double
            link("setVisible", arrayOf(false, 0.0))
        }
    }
    private var hiddenAt = 0.0

    // ------------------------------------------------------------------ gestures

    private val ids = ArrayList<Int>()
    private val xs = HashMap<Int, Float>()
    private val ys = HashMap<Int, Float>()

    private fun attachPointers(c: HTMLCanvasElement) {
        fun local(e: PointerEvent) {
            val r = c.getBoundingClientRect()
            val dpr = c.width / r.width
            xs[e.pointerId] = ((e.clientX - r.left) * dpr).toFloat(); ys[e.pointerId] = ((e.clientY - r.top) * dpr).toFloat()
        }
        fun send(action: Int, index: Int) {
            val n = ids.size
            touch?.onTouchEvent(MotionEvent(action, index, IntArray(n) { ids[it] }, FloatArray(n) { xs[ids[it]] ?: 0f },
                FloatArray(n) { ys[ids[it]] ?: 0f }, android.os.SystemClock.uptimeMillis()))
        }
        c.addEventListener("pointerdown", { ev ->
            val e = ev.unsafeCast<PointerEvent>(); e.preventDefault()
            c.asDynamic().setPointerCapture(e.pointerId)
            local(e); ids += e.pointerId
            send(if (ids.size == 1) MotionEvent.ACTION_DOWN else MotionEvent.ACTION_POINTER_DOWN, ids.size - 1)
        })
        c.addEventListener("pointermove", { ev ->
            val e = ev.unsafeCast<PointerEvent>()
            if (e.pointerId !in ids) return@addEventListener
            local(e); send(MotionEvent.ACTION_MOVE, 0)
        })
        val up = { ev: org.w3c.dom.events.Event ->
            val e = ev.unsafeCast<PointerEvent>()
            val i = ids.indexOf(e.pointerId)
            if (i >= 0) {
                local(e)
                if (ev.type == "pointercancel") send(MotionEvent.ACTION_CANCEL, i)
                else send(if (ids.size == 1) MotionEvent.ACTION_UP else MotionEvent.ACTION_POINTER_UP, i)
                ids.removeAt(i); xs.remove(e.pointerId); ys.remove(e.pointerId)
            }
        }
        c.addEventListener("pointerup", up)
        c.addEventListener("pointercancel", up)
        // Mouse wheel / trackpad pinch: a synthetic two-finger pinch around the cursor.
        c.addEventListener("wheel", { ev ->
            ev.preventDefault()
            val e = ev.asDynamic()
            val r = c.getBoundingClientRect(); val dpr = c.width / r.width
            val x = ((e.clientX as Double - r.left) * dpr).toFloat(); val y = ((e.clientY as Double - r.top) * dpr).toFloat()
            val d = (100.0 * (if ((e.deltaY as Double) > 0) 0.92 else 1.08)).toFloat()
            pinch(x, y, 100f, d)
        })
    }

    private fun pinch(x: Float, y: Float, from: Float, to: Float) {
        val t = android.os.SystemClock.uptimeMillis()
        val tc = touch ?: return
        val idA = 9001; val idB = 9002
        fun ev(a: Int, i: Int, d: Float, n: Int, dt: Long) = MotionEvent(a, i, intArrayOf(idA, idB).copyOf(n), floatArrayOf(x - d, x + d).copyOf(n), floatArrayOf(y, y).copyOf(n), t + dt)
        tc.onTouchEvent(ev(MotionEvent.ACTION_DOWN, 0, from, 1, 0))
        tc.onTouchEvent(ev(MotionEvent.ACTION_POINTER_DOWN, 1, from, 2, 0))
        tc.onTouchEvent(ev(MotionEvent.ACTION_MOVE, 0, from + (to - from) * 0.5f, 2, 400))
        tc.onTouchEvent(ev(MotionEvent.ACTION_MOVE, 0, to, 2, 800))
        tc.onTouchEvent(ev(MotionEvent.ACTION_POINTER_UP, 1, to, 2, 800))
        tc.onTouchEvent(ev(MotionEvent.ACTION_UP, 0, to, 1, 800))
    }

    // ------------------------------------------------------------------ sandbox controls (called from the page)

    /** 0 pauses. Watch mode runs the real tank at 1x; faster time is a sandbox thing (as on Android). */
    fun setSpeed(speed: Double) { link("setSpeed", arrayOf(speed)) }

    /** "Rocks can shift": the dynamic rockscape for the tank and the sandbox (off: rocks never move). */
    fun setDynamicRocks(on: Boolean) { link("setDynamicRocks", arrayOf(on)) }

    /** Puts the tank's rocks where they lay at the last visit (the text `onRocks` handed the page). */
    fun restoreRocks(state: String) { link("restoreRocks", arrayOf(state)) }
    fun follow(id: String?) { renderer?.follow(id) }
    /** The animal whose card is open (null = none): the renderer keeps it at full pose detail. */
    fun select(id: String?) { renderer?.selectedId = id?.takeIf { it.isNotEmpty() } }
    /** Follow camera buttons: [steps] > 0 zooms closer, < 0 further out (eases there, bounces at the limits). */
    fun followZoom(steps: Double) { renderer?.followCam?.zoom(steps.toFloat()) }
    /** Camera eye / aim / sand under the eye as JSON (visual QA scripts). */
    fun cam(): String = renderer?.camera?.debugState() ?: "null"
    /** Follow camera buttons: swing round the fish, [dir] +1 / −1 one 45° step. */
    fun followOrbit(dir: Double) { renderer?.followCam?.orbit(dir.toFloat() * com.reeflive.app.render.FollowCamControl.ORBIT_STEP) }
    /** Lamp looks (render only): REAL, DAY, DUSK, NIGHT, MOON, BLUE. */
    fun setViewLight(name: String) { renderer?.viewLight = runCatching { com.reeflive.app.render.ViewLight.valueOf(name) }.getOrDefault(com.reeflive.app.render.ViewLight.REAL) }
    /** The 3D supply shelf beside the tank: the camera swings over to it; tapping a prop calls `onShelfPick(id)`. */
    fun shelfOpen(on: Boolean) { shelf.setOpen(on, false) }
    /** Tap-to-place: while on, the next tap on the tank reports `onAim` ({x, y, z, surface, id}) instead of picking. */
    fun aimArm(on: Boolean) { aim.armed = on }
    /** Gear taps (`onSpot`) on or off (off while building with rock). */
    fun gearTaps(on: Boolean) { aim.gearTaps = on }
    fun shelfRow(row: Int) { shelf.focusRow(row) }
    fun shelfPick(id: String) { shelf.pick(id) }
    fun shelfLocked(ids: String) { shelf.locked = ids.split(",").filter { it.isNotBlank() }.toSet() }
    /** Supplies My tank has run out of: off the shelf (no prop, no taps) until more are bought. */
    fun shelfHidden(ids: String) { shelf.hidden = ids.split(",").filter { it.isNotBlank() }.toSet() }
    fun resetCamera() { renderer?.resetCamera() }
    /** Glide to the nursery tank beside the display (clownfish babies) or back. */
    fun nursery(on: Boolean) { renderer?.lookAtNursery(on) }
    /** Steps back to see the tank on its stand in its room (the room shop). */
    fun showRoom() { homeRoom.showRoom() }
    /** Glides to the music corner (the record stand and the radio) or back to the tank. */
    fun musicOpen(on: Boolean) { music.open(on) }
    /** The radio's state for the music corner: song playing (-1 off), playable records (bit per song), volume, offered record (-1 none). */
    fun musicState(playing: Int, ownedMask: Int, volume: Double, selected: Int) {
        music.playing = playing; music.ownedMask = ownedMask; music.volume = volume.toFloat(); music.selected = selected
    }
    /** Draws the stand and room round the tank or not (frame-cost checks, ?stats). */
    fun roomVisible(on: Boolean) { homeRoom.visible = on }
    /** The page's answer to `onTouch(surface)`: touch the tank where that tap landed (ripple or sand puff, Command.TouchTank). */
    /**
     * Aquascape builder: the page's answer to `onTouch` while it is open. Drops a dry rock of [shape]
     * ([com.reeflive.core.sim.DryRockShape] name) turned [turnDeg] where the tap landed, or with [takeOut] takes out
     * the rock there. On the tank on screen (your tank or the pretend copy).
     */
    fun scapeTouch(shape: String, turnDeg: Double, takeOut: Boolean): Boolean {
        val (p, surface) = pendingTouch ?: return false
        pendingTouch = null
        if (latest() == null) return false
        if (takeOut) link("removeRock", arrayOf(p.x, p.y, p.z))
        else {
            val z = if (surface == TouchSurface.GLASS || surface == TouchSurface.SURFACE) com.reeflive.app.render.Camera.TANK[2].toDouble() / 2 else p.z
            link("placeRock", arrayOf(shape, p.x, z, turnDeg))
        }
        return true
    }
    /** Aquascape builder: takes out the dry rock added last. */
    fun scapeUndo() { link("scapeUndo", emptyArray()) }
    /** Aquascape builder: takes every rock out, to build from scratch. */
    fun scapeClear() { link("scapeClear", emptyArray()) }
    /** The dry rock shapes for the builder bar: [{id, label}]. */
    fun scapeShapes(): String = JSON.stringify(com.reeflive.core.sim.DryRockShape.entries.map { e -> obj { it.id = e.name; it.label = e.label } }.toTypedArray())

    fun touchTank(): Boolean {
        val (p, surface) = pendingTouch ?: return false
        pendingTouch = null
        // as LiveTankScreen.onTouchTank: the sim's TouchTank plus a ripple or a sand puff, played here at once
        link("touchTank", arrayOf(p.x, p.y, p.z, surface.name))
        val glass = surface == TouchSurface.GLASS
        val kind = when (surface) {
            TouchSurface.SAND -> BurstKind.SAND_PUFF
            TouchSurface.SURFACE -> BurstKind.SURFACE_RIPPLE
            else -> BurstKind.GLASS_RIPPLE
        }
        bursts.emit(TankBurst(kind, p.x.toFloat(), p.y.toFloat(), p.z.toFloat(), amount = if (glass) 1f else 0.7f))
        return true
    }

    /** Screensaver mode on/off: slow cinematic moves and animal follows; read [cinema] each frame for the overlay. */
    fun screensaver(on: Boolean) {
        val r = renderer ?: return
        cinemaSession.attach(r) { (canvas?.height ?: 1).toFloat() }
        if (on) cinemaSession.enter() else cinemaSession.exit()
    }

    /** Screensaver overlay state as JSON {fade, caption, alpha, leaving}, or "null" once it is fully over. */
    fun cinema(): String {
        if (!cinemaSession.active) return "null"
        return JSON.stringify(obj { it.fade = cinemaSession.fade; it.caption = cinemaSession.caption ?: ""; it.alpha = cinemaSession.captionAlpha; it.leaving = cinemaSession.leaving })
    }
    /**
     * Submarine mode (as the Android live menu): launch or park the toy sub; [visible] false hides it from the fish;
     * [headlight] is its lamp. The camera rides with it while it is out.
     */
    fun submarine(on: Boolean, visible: Boolean, headlight: Boolean) {
        if (on) renderer?.follow(null)
        link("submarine", arrayOf(on, visible, headlight))
    }
    /** The pilot's stick, each axis -1..1 (send on change only). */
    fun steerSub(thrust: Double, turn: Double, rise: Double, strafe: Double) { link("steerSub", arrayOf(thrust, turn, rise, strafe)) }
    /** Ride inside the sub (first person) or chase it from behind. */
    fun subView(firstPerson: Boolean) { renderer?.subFirstPerson = firstPerson }
    /**
     * Watch an animal from the sub: it glides over and keeps it company at a polite distance. [mode] HOVER, CIRCLE or
     * ALONGSIDE; [id] null (or "") = back to free steering.
     */
    fun subWatch(id: String?, mode: String) { link("subWatch", arrayOf(id, mode)) }
    /** The sub's ‹ ›: the next / previous fish ([step] ±1), or the nearest when none is watched. */
    fun subWatchNext(step: Int) { link("subWatchNext", arrayOf(step)) }
    /** JSON {curious, wary, watch, mode} while the sub is out, else null: who reacts to it and whom it is watching. */
    fun subState(): String = latest()?.submarine?.let {
        JSON.stringify(obj { o -> o.curious = it.curious; o.wary = it.wary; o.watch = it.watchId; o.mode = it.watchMode.name })
    } ?: "null"
    fun setQuality(q: String) {
        val v = parseQuality(q) ?: return
        goodWindows = 0; floorWindows = 0; tryBase = -1.0; frozen = false; savedLevel = null; judged = 0.0
        windowTime = 0.0; windowFrames = 0
        renderer?.relief = 0
        if (v != current) { current = v; renderer?.setQuality(v); grace = SWITCH_GRACE }
        scaleStep = 0
        resize()
    }

    /** Logged readings from the tracker (base units, temperature in °F) → the live tank, like the Android reality pick-up. */
    fun applyReadings(json: String) { link("applyReadings", arrayOf(json)) }

    /** A "check units" note for an implausible logged value (nitrate over 50 ppm, phosphate over 0.5 ppm), or "". */
    fun readingWarning(id: String, value: Double): String = com.reeflive.core.live.ReadingSanity.warning(id, value) ?: ""

    /** Tracker parameter table (shared ParameterTable): id, name, unit, precision, ideal and acceptable ranges. */
    fun parameters(): String = JSON.stringify(ParameterTable.SOFT_CORAL.sortedBy { it.order }.map { d ->
        obj {
            it.id = d.id; it.name = d.name; it.unit = d.baseUnit.symbol; it.precision = d.precision
            it.idealLo = d.ideal.lo; it.idealHi = d.ideal.hi; it.okLo = d.acceptable.lo; it.okHi = d.acceptable.hi
            it.favorite = d.favorite; it.dangerLo = d.dangerLow; it.dangerHi = d.dangerHigh
        }
    }.toTypedArray())

    /**
     * New-tank cycle tracker (core CycleTracker): [json] is {start, now, boosted, readings: [{t, nh3, no2, no3}]}, epoch
     * ms and ppm. Returns the stage, day of the cycle and the days-to-cycled estimate.
     */
    fun cycleStatus(json: String): String {
        val o = JSON.parse<dynamic>(json)
        val num = { v: dynamic -> if (v == null || v == undefined) null else (v as? Number)?.toDouble() }
        val rs = (o.readings as? Array<dynamic>).orEmpty().mapNotNull { r ->
            val t = num(r.t) ?: return@mapNotNull null
            CycleReading(t, num(r.nh3), num(r.no2), num(r.no3))
        }
        val s = CycleTracker.assess(rs, num(o.start) ?: 0.0, num(o.now) ?: 0.0, o.boosted == true)
        return JSON.stringify(obj {
            it.stage = s.stage.name; it.label = s.stage.label; it.day = s.day
            it.daysLeft = s.daysLeft; it.daysLeftLo = s.daysLeftLo; it.daysLeftHi = s.daysLeftHi; it.zeroDays = s.zeroDays
            it.checkDays = CycleTracker.CHECK_DAYS; it.peakNh3 = s.peakNh3; it.peakNo2 = s.peakNo2
            it.nh3 = s.nh3; it.no2 = s.no2; it.no3 = s.no3; it.advice = s.advice
        })
    }

    /** Acclimation steps for a new arrival (core AcclimationPlan): [method] DRIP or FLOAT. */
    fun acclimationPlan(method: String, sensitive: Boolean): String {
        val m = runCatching { AcclimationMethod.valueOf(method) }.getOrDefault(AcclimationMethod.DRIP)
        return JSON.stringify(AcclimationPlan.steps(m, sensitive).map { s ->
            obj { it.title = s.title; it.detail = s.detail; it.seconds = s.seconds }
        }.toTypedArray())
    }

    /**
     * Tank x (cm) of the front glass under canvas pixel ([px], [py]) for dropping a shop bag there, or -1 when the
     * point is off the tank.
     */
    fun bagDropX(px: Double, py: Double): Double {
        if (!projection.planePointAt(px.toFloat(), py.toFloat(), com.reeflive.app.render.Camera.TANK[2] + 0.8f, bagPt)) return -1.0
        return if (bagPt[0] in 0f..com.reeflive.app.render.Camera.TANK[0] && bagPt[1] in 0f..com.reeflive.app.render.Camera.TANK[1]) bagPt[0].toDouble() else -1.0
    }

    /**
     * The floating acclimation bags for the page's timers: canvas pixel (x, y) just under each bag, minutes left,
     * progress 0..1, bag and tank temperature, phase, count and species; plus whether Hurry up is on.
     */
    fun bagState(): String {
        val snap = latest() ?: return "null"
        val o: dynamic = js("({})")
        o.hurry = snap.bagHurry
        o.bags = snap.bags.map { b ->
            val on = projection.project(b.x, b.y - b.radius * 1.8f, b.z, bagPt)
            obj {
                it.id = b.id; it.x = if (on) bagPt[0] else -1f; it.y = if (on) bagPt[1] else -1f
                it.min = b.minutesLeft; it.progress = b.progress; it.bagT = b.bagTempC; it.tankT = b.tankTempC
                it.phase = b.phase; it.count = b.count; it.name = b.common
            }
        }.toTypedArray()
        return JSON.stringify(o)
    }

    /**
     * My tank: the coral waiting for its spot, for the page's spot picker (corals.js): name, how many wait, and per
     * offered spot its rank (0 = best), stars, reason and the canvas pixel (x, y) just above it (−1 when off screen);
     * "null" when no coral is waiting.
     */
    fun coralPick(): String {
        val p = latest()?.coralPick ?: return "null"
        return JSON.stringify(obj {
            it.id = p.speciesId; it.name = p.name; it.waiting = p.waiting
            it.spots = p.spots.map { s ->
                val on = projection.project(s.x, s.y + 8.5f, s.z, bagPt)
                obj { o -> o.rank = s.rank; o.stars = s.stars; o.reason = s.reason; o.x = if (on) bagPt[0] else -1f; o.y = if (on) bagPt[1] else -1f }
            }.toTypedArray()
        })
    }

    /**
     * The last body the fish net took out (core FishNet), for the page's toilet goodbye (flush.js): key (new per
     * body), name, fish or not, and the cartoon colours; "null" before the first.
     */
    fun netFlush(): String {
        val f = latest()?.netFlush ?: return "null"
        return JSON.stringify(obj { it.key = "${f.seq}:${f.id}"; it.name = f.name; it.isFish = f.isFish; it.body = f.body; it.accent = f.accent })
    }

    // ------------------------------------------------------------------ over the tank (from the snapshot the view draws)

    private val bubblePicker = ThoughtBubblePicker()
    private val bubbleAnchor = FloatArray(3)
    private val bubblePt = FloatArray(3)

    /**
     * More → "Thought bubbles" (as on Android, ThoughtBubbleLayer): at most three animals with a speech bubble, as
     * JSON [{id, text, x, y, a}] with x, y in canvas pixels just above the animal. Call every animation frame while
     * on; [on] false clears the picker.
     */
    fun bubbles(on: Boolean): String {
        if (!on) { bubblePicker.clear(); return "[]" }
        val snap = latest() ?: return "[]"
        val r = renderer ?: return "[]"
        val proj = r.projection ?: com.reeflive.app.render.TankProjection().also { r.projection = it }
        val c = canvas ?: return "[]"
        val w = c.width.toFloat()
        val h = c.height.toFloat()
        val list = bubblePicker.update(snap.stats, (js("Date.now()") as Double).toLong()) { id ->
            if (ThoughtBubblePicker.anchorOf(snap, id, bubbleAnchor) && proj.project(bubbleAnchor[0], bubbleAnchor[1], bubbleAnchor[2], bubblePt) &&
                bubblePt[0] in 0f..w && bubblePt[1] in 0f..h
            ) floatArrayOf(bubblePt[0], bubblePt[1], bubblePt[2]) else null
        }
        return JSON.stringify(list.map { b -> obj { o -> o.id = b.id; o.text = b.text; o.x = b.x; o.y = b.y; o.a = b.alpha } }.toTypedArray())
    }

    // ---- the store's people (render StorePeople): their speech bubbles
    private val sbText = arrayOfNulls<String>(6)
    private val sbPos = FloatArray(18)
    private val sbAlpha = FloatArray(6)
    private val sbPt = FloatArray(3)

    /**
     * What the store's people are saying now, as JSON [{text, x, y, a}] with x, y in canvas pixels just over the
     * talker's head (as Android's StoreBubbleLayer). The page calls it every animation frame in a store.
     */
    fun storeBubbles(): String {
        if (room.storeId == null) return "[]"
        val r = renderer ?: return "[]"
        val proj = r.projection ?: com.reeflive.app.render.TankProjection().also { r.projection = it }
        val n = room.bubbles(sbText, sbPos, sbAlpha)
        val out = ArrayList<dynamic>()
        for (i in 0 until n) {
            val t = sbText[i] ?: continue
            if (!proj.project(sbPos[i * 3], sbPos[i * 3 + 1], sbPos[i * 3 + 2], sbPt)) continue
            out.add(obj { o -> o.text = t; o.x = sbPt[0]; o.y = sbPt[1]; o.a = sbAlpha[i] })
        }
        return JSON.stringify(out.toTypedArray())
    }

    // ------------------------------------------------------------------ where the sim runs (set by the page's SimHost)

    /** The sim runs on this thread: commands call [ReefSim] directly, the view draws its snapshots, effects play here. */
    fun bindInThread() {
        val sim: dynamic = ReefSim
        link = { name, args -> sim[name].apply(sim, args) }
        snapshotSource = { ReefSim.sim?.latest }
        simFrame = { dt -> ReefSim.frame(dt) }
        ReefSim.fx = localFx
    }

    /**
     * The sim runs in a worker: [post] (name, args) sends a command to its [ReefSim]; the page hands each decoded
     * snapshot to [mirror] and each effect to [applyFx].
     */
    fun bindRemote(post: (String, Array<Any?>) -> Unit) {
        link = post
        snapshotSource = { mirrorSnap }
        simFrame = {}
    }

    /** The worker's newest snapshot (a SimSnapshot, decoded by the page), or null when no tank is on screen. */
    fun mirror(snap: Any?) { mirrorSnap = snap?.unsafeCast<SimSnapshot>() }

    /** An effect the worker's sim asked for ([SimFx] by name; arguments as the worker's PostFx sends them). */
    fun applyFx(op: String, a: Array<Any?>) {
        val f = localFx
        when (op) {
            "burst" -> f.burst(BurstKind.entries[a[0] as Int], (a[1] as Number).toFloat(), (a[2] as Number).toFloat(), (a[3] as Number).toFloat(),
                (a[4] as Number).toFloat(), (a[5] as Number).toFloat(), (a[6] as Number).toFloat(), (a[7] as Number).toFloat())
            "care" -> f.care(a[0] as Int, CareAct.entries[a[1] as Int], a[2] as Int, (a[3] as Number).toFloat(), a[4]?.unsafeCast<FloatArray>())
            "cleaning" -> f.cleaning(a[0] as Boolean)
            "emit" -> f.emit(a[0] as String, a[1])
            "homeLook" -> f.homeLook(a[0].unsafeCast<RoomLook>())
            "storeEnter" -> f.storeEnter(a[0] as String, a[1] as Int, a[2] as Int, a[3].unsafeCast<List<DisplaySetup>>(), a[4] as String?, a[5] as Boolean)
            "storeShow" -> f.storeShow(a[0] as Int)
            "storeCounter" -> f.storeCounter(a[0] as Boolean)
            "storeTradeReply" -> f.storeTradeReply(a[0] as Boolean)
            "storeLeave" -> f.storeLeave()
            "meshRock" -> f.meshRock(a[0].unsafeCast<BuiltRock>())
            else -> console.warn("[ReefLive] unknown effect $op")
        }
    }

    /** Dry rock meshes a worker's display tank needs, made a few per frame ([meshRock]). */
    private val meshQueue = ArrayDeque<BuiltRock>()

    /** The effects played on this thread, whichever side asked. */
    private val localFx: SimFx = object : SimFx {
        override fun burst(kind: BurstKind, x: Float, y: Float, z: Float, r: Float, g: Float, b: Float, amount: Float) =
            bursts.emit(TankBurst(kind, x, y, z, r, g, b, amount))
        override fun care(id: Int, act: CareAct, tint: Int, amount: Float, at: FloatArray?) =
            care.play(CareJob(act, tint, amount, at, onImpact = { link("careImpact", arrayOf(id)) }, onDone = { link("careDone", arrayOf(id)) }))
        override fun cleaning(on: Boolean) { renderer?.setCleaning(on) }
        override fun emit(event: String, arg: Any?) = this@ReefLive.emit(event, arg)
        override fun homeLook(look: RoomLook) { homeRoom.look = look }
        override fun storeEnter(store: String, tanks: Int, at: Int, setups: List<DisplaySetup>, trade: String?, walkIn: Boolean) {
            room.walkIn = walkIn
            room.enter(store, tanks, at, setups, trade)
        }
        override fun storeShow(i: Int) = room.show(i)
        override fun storeCounter(on: Boolean) = room.counter(on)
        override fun storeTradeReply(yes: Boolean) = room.tradeReply(yes)
        override fun storeLeave() = room.leave()
        override fun meshRock(rock: BuiltRock) {
            // on this thread's sim the display tank build waits for the mesh (as before); a worker's queues it here
            if (ReefSim.fx === this) com.reeflive.app.render.DryRockMesh.cached(rock.shape, rock.seed)
            else { meshQueue.addLast(rock); if (!meshing) { meshing = true; window.requestAnimationFrame { meshSlice() } } }
        }
    }

    private var meshing = false

    private fun meshSlice() {
        val t0 = now()
        while (meshQueue.isNotEmpty() && now() - t0 < 4.0) { val r = meshQueue.removeFirst(); com.reeflive.app.render.DryRockMesh.cached(r.shape, r.seed) }
        if (meshQueue.isNotEmpty()) window.requestAnimationFrame { meshSlice() } else meshing = false
    }
    /** Render-resolution multipliers tried in order while frames are slow (1 → ⅔ of the device pixel ratio). */
    private val SCALES = doubleArrayOf(1.0, 0.9, 0.8, 0.72, 0.64, 0.57, 0.5)
    private const val ADAPT_FRAMES = 45
    private const val SLOW_DT = 1.0 / 48
    private const val FAST_DT = 1.0 / 57
    private const val GOOD_WINDOWS = 4
    private const val FLOOR_WINDOWS = 3
    /** Runtime relief steps (TankRenderer.relief) tried at the resolution floor before a preset is dropped (a reload). */
    private const val RELIEF_BEFORE_PRESET = 2
    private const val START_GRACE = 8.0
    private const val SWITCH_GRACE = 4.0
    /** How long a "fewer pixels did not help" verdict holds before the picture may be traded again. */
    private const val HOLD_SECONDS = 20.0
    /** Seconds after loading in which Auto may still switch presets live (it rebuilds every model and texture). */
    private const val LIVE_SWITCH_SECONDS = 30.0
}

internal fun obj(fill: (dynamic) -> Unit): dynamic { val o: dynamic = js("({})"); fill(o); return o }

fun main() {}
