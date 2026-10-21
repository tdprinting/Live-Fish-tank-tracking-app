@file:OptIn(ExperimentalJsExport::class)

package com.reeflive.web

import android.content.Context
import android.content.res.AssetManager
import android.opengl.GLES30
import android.view.MotionEvent
import android.view.View
import com.reeflive.app.render.RenderQuality
import com.reeflive.app.render.TankRenderer
import com.reeflive.app.render.TankViewListener
import com.reeflive.app.render.TouchController
import com.reeflive.core.geom.Vec3
import com.reeflive.core.live.RealityInit
import com.reeflive.core.live.Scenario
import com.reeflive.core.params.ParameterTable
import com.reeflive.core.sim.Command
import com.reeflive.core.sim.DefaultStock
import com.reeflive.core.sim.FoodKind
import com.reeflive.core.sim.RockData
import com.reeflive.core.sim.SimSnapshot
import com.reeflive.core.sim.Simulation
import com.reeflive.core.sim.SpeciesCatalog
import com.reeflive.core.sim.TankConfig
import com.reeflive.core.sim.Stocking
import com.reeflive.core.sim.TankDims
import com.reeflive.core.sim.TouchSurface
import com.reeflive.core.sim.WaterState
import com.reeflive.app.render.GlassStroke
import com.reeflive.app.render.SupplyShelfControl
import java.util.concurrent.AssetPrefetch
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.pointerevents.PointerEvent
import kotlin.js.Promise
import kotlin.math.min
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Browser host for the shared simulation (:core) and 3D renderer (app/.../render), both compiled from the same
 * Kotlin sources as the Android app. It plays the parts SimulationHolder and TankGlView play on Android: builds the
 * sim from the default stock, steps it at 20 Hz, drives TankRenderer every animation frame and turns pointer events
 * into MotionEvents for the shared TouchController. The page's UI talks to it through [ReefLive].
 */
@JsExport
object ReefLive {
    /** The sim on screen: [reality] in Watch mode, the sandbox fork in Sandbox mode (see [ReefSandbox]). */
    internal var sim: Simulation? = null
    /** The tank as logged (default stock + the tracker's readings); runs at 1x. */
    internal var reality: Simulation? = null
    internal var species: SpeciesCatalog? = null
    internal var rock: RockData? = null
    internal var renderer: TankRenderer? = null
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
    private var base = ""
    private var simAcc = 0.0
    private var lastFrame = -1.0
    private var running = false
    private var applied: Map<String, Double> = emptyMap()
    internal val scenarioQueue = ArrayList<Pair<Double, Command>>()
    private var pendingTouch: Pair<Vec3, TouchSurface>? = null
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
        base = baseUrl.trimEnd('/') + "/"
        listener = events
        canvas = cnv
        MainScope().launch {
            try {
                status("Loading the tank…")
                val index = fetchJson("assets/index.json")
                val files = HashMap<String, List<String>>()
                for (k in js("Object").keys(index).unsafeCast<Array<String>>()) files[k] = index[k].unsafeCast<Array<String>>().toList()
                val boot = ArrayList<String>()
                files["shaders"]?.forEach { boot += "assets/shaders/$it" }
                boot += listOf("assets/sim/rock_sdf.bin", "assets/sim/anchors.json", "res/sim/interactions.json")
                val have = fetchJson("res/species/index.json")
                // Every species with a sheet: the default stock plus everything Add life can release in the sandbox.
                val known = (have.species.unsafeCast<Array<String>?>() ?: emptyArray()).toSet()
                val speciesIds = (DefaultStock.all.map { it.speciesId } + Stocking.CATALOG.map { it.speciesId } + known)
                    .distinct().filter { known.isEmpty() || it in known }
                speciesIds.forEach { id -> boot += "res/species/$id.json" }
                fetchAll(boot)
                AssetPrefetch.fetcher = { path, done -> MainScope().launch { fetchWithRetry("assets/$path"); done() } }

                status("Building the reef…")
                val species = SpeciesCatalog.fromClasspath(speciesIds).also { this@ReefLive.species = it }
                val rock = runCatching {
                    RockData.fromFiles(kotlin.Resources.get("assets/sim/rock_sdf.bin")!!, kotlin.Resources.get("assets/sim/anchors.json")!!.decodeToString())
                }.getOrElse { RockData.defaultTank() }.also { this@ReefLive.rock = it }
                val now = js("new Date()")
                val secOfDay = (now.getHours() as Int) * 3600.0 + (now.getMinutes() as Int) * 60.0 + (now.getSeconds() as Int)
                val config = TankConfig()
                val s = Simulation(
                    config = config, species = species, rock = rock, startTimeSec = secOfDay,
                    initialWater = WaterState(volumeL = config.systemLiters), livestock = DefaultStock.all,
                )
                s.speed = 1.0
                s.stepWall(0.0)
                sim = s; reality = s

                val gl = cnv.getContext("webgl2", js("({antialias: false, alpha: false, depth: true, stencil: false, powerPreference: 'high-performance'})"))
                    ?: throw IllegalStateException("This browser has no WebGL 2")
                GLES30.gl = gl
                val q = runCatching { RenderQuality.valueOf(quality.uppercase()) }.getOrDefault(RenderQuality.HIGH)
                current = q
                val r = TankRenderer(Context(AssetManager(files)), q)
                r.snapshotProvider = { sim?.latest }
                r.projection = projection
                r.demoWhenEmpty = false
                // the canvas has no MSAA of its own (it doubled the work on 3x phones): PostFx anti-aliases
                r.msaaInPost = true
                r.listener = object : TankViewListener {
                    // a tap right on an animal also touches it: snails and crabs tuck in, fish just get picked (TankTouch)
                    override fun onPick(id: String?) { if (id != null) sim?.enqueue(Command.TouchAnimal(id)); emit("onPick", id) }
                    override fun onFollowRequest(id: String?) { r.follow(id); emit("onFollow", id) }
                    // pressed and held on an animal: follow it (the page buzzes and says who)
                    override fun onHoldAnimal(id: String) { r.follow(id); emit("onFollow", id); emit("onHold", id) }
                    override fun onFollowLost(id: String) { if (cinemaSession.active) cinemaSession.followLost() else emit("onFollow", null) }
                    override fun onTapGlass(worldPoint: Vec3) { sim?.enqueue(Command.TapGlass(worldPoint)); emit("onKnock", null) }
                    // a tap with no fish under it: the page decides (undo a card / hidden buttons first), then touchTank()
                    override fun onTouchTank(worldPoint: Vec3, surface: TouchSurface) { pendingTouch = worldPoint to surface; emit("onTouch", surface.name) }
                    // as on Android (LiveTankScreen): a long press drops a pinch of pellets where the finger is
                    override fun onLongPress(worldPoint: Vec3) { sim?.enqueue(Command.Feed(FoodKind.PELLET, 1.0, worldPoint)); emit("onFed", "PELLET") }
                    // hold on the glass: a fingertip the fish follow (mouse: hold the button still, then drag); the page
                    // only hears when it goes down and when it lifts
                    override fun onFinger(worldPoint: Vec3?, speedCmS: Float, surface: TouchSurface) {
                        sim?.enqueue(Command.Finger(worldPoint, speedCmS.toDouble(), surface))
                        val down = worldPoint != null
                        if (down != fingerDown) { fingerDown = down; emit("onFinger", if (down) surface.name else null) }
                    }
                    override fun onFlick(worldPoint: Vec3, velocity: Vec3) { sim?.enqueue(Command.WaterPuff(worldPoint, velocity)); emit("onFlick", null) }
                    override fun onScrape(stroke: GlassStroke) { ReefSandbox.scrape(stroke); emit("onScrape", stroke.lengthCm) }
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
                r.storeRoom = room
                r.room = homeRoom
                music.onRecord = { i -> emit("onMusicRecord", i) }
                music.onVolume = { v, done -> emit("onMusicVolume", obj { it.volume = v; it.done = done }) }
                music.onTune = { emit("onMusicTune", null) }
                music.onPower = { emit("onMusicPower", null) }
                music.onView = { on -> emit("onMusicView", on) }
                r.music = music
                r.bursts = ReefSandbox.bursts
                r.care = ReefSandbox.care
                renderer = r
                r.onSurfaceCreated(null, null)
                resize()
                touch = TouchController(Context(AssetManager(files)), View(), r.input)
                resize()   // TouchController sets its own finger size from the slop; the page's px are device px
                attachPointers(cnv)
                window.addEventListener("resize", { resize() })
                running = true
                window.requestAnimationFrame(::frame)
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
    private var slowFrames = 0; private var windowFrames = 0; private var windowTime = 0.0; private var goodWindows = 0
    private var floorWindows = 0

    /**
     * Dynamic resolution: over each [ADAPT_FRAMES]-frame window, a mean frame time above [SLOW_DT] steps the render
     * resolution down one [SCALES] notch; [GOOD_WINDOWS] windows in a row under [FAST_DT] step it back up. Pixel count
     * (MSAA samples, bloom, shafts, lens) dominates GPU time on 3x screens, so this keeps the frame rate up without
     * dropping any effect. Each frame counts at most 0.1 s, so one long hitch cannot step it down alone.
     * Still slow at the lowest scale for [FLOOR_WINDOWS] windows: the picture drops one preset (HIGH → MEDIUM → LOW,
     * shedding bloom, 4x MSAA, fish in the shadow map, caustic colour fringes...) at about the same pixel count, and
     * the page stores it as the new Picture setting ("onQuality"), so a slow device starts fast next time.
     */
    private fun adapt(dt: Double) {
        if (dt <= 0.0) return
        windowTime += min(dt, 0.1); windowFrames++
        if (windowFrames < ADAPT_FRAMES) return
        val mean = windowTime / windowFrames
        windowTime = 0.0; windowFrames = 0
        if (mean > SLOW_DT && scaleStep == SCALES.size - 1 && current != RenderQuality.LOW) {
            if (++floorWindows >= FLOOR_WINDOWS) stepQualityDown()
        }
        else if (mean > SLOW_DT && scaleStep < SCALES.size - 1) { scaleStep++; goodWindows = 0; floorWindows = 0 }
        else if (mean < FAST_DT && scaleStep > 0) { if (++goodWindows >= GOOD_WINDOWS) { scaleStep--; goodWindows = 0 } }
        else goodWindows = 0
    }

    private fun stepQualityDown() {
        val from = cap(current)
        val v = RenderQuality.entries[current.ordinal + 1]
        current = v; goodWindows = 0; floorWindows = 0
        // keep about the pixel count the floor had, so the cheaper preset is not straight away slowed by more pixels
        scaleStep = SCALES.indices.firstOrNull { cap(v) * SCALES[it] <= from * SCALES.last() + 1e-6 } ?: SCALES.size - 1
        renderer?.setQuality(v)
        resize()
        emit("onQuality", v.name)
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
        if (!running) return
        val dt = if (lastFrame < 0) 0.0 else ((t - lastFrame) / 1000.0).coerceIn(0.0, 0.25)
        lastFrame = t
        val s = sim
        val t0 = if (statsOn) now() else 0.0
        if (s != null) {
            simAcc += dt
            var steps = 0
            while (simAcc >= TICK && steps < 3) {
                while (scenarioQueue.isNotEmpty() && scenarioQueue[0].first <= s.simTimeSec) s.enqueue(scenarioQueue.removeAt(0).second)
                s.stepWall(TICK); simAcc -= TICK; steps++
                // a rock in the tank settled or toppled: the page keeps its new spot for the next visit
                if (s === reality && s.latest.events.any { it is com.reeflive.core.sim.SimEvent.RockShifted }) emit("onRocks", s.rockState())
                else if (s === reality && s.latest.events.any { it is com.reeflive.core.sim.SimEvent.RockscapeChanged }) emit("onRockscape", s.rockState())
            }
            if (steps == 3) simAcc = 0.0
        }
        val t1 = if (statsOn) now() else 0.0
        adapt(dt)
        val c = canvas
        if (c != null) { val r = pixelRatio(); if (c.width != (c.clientWidth * r).toInt() || c.height != (c.clientHeight * r).toInt()) resize() }
        cinemaSession.update(dt.toFloat(), sim?.latest)
        renderer?.onDrawFrame(null)
        if (statsOn) stats(dt, t1 - t0, now() - t1)
        window.requestAnimationFrame(::frame)
    }

    // ------------------------------------------------------------------ stats overlay (?stats in the page URL)
    private val statsOn by lazy { listener?.onStats != null }
    private fun now(): Double = js("performance.now()") as Double
    private var stFrames = 0; private var stTime = 0.0; private var stSim = 0.0; private var stDraw = 0.0; private var stWorst = 0.0

    /** Once a second: frame rate, worst frame, and the CPU ms per frame spent in the sim and in issuing draws. Frame
     *  time well above sim + draw means the GPU (pixels / shaders) is the limit. */
    private fun stats(dt: Double, simMs: Double, drawMs: Double) {
        stFrames++; stTime += dt; stSim += simMs; stDraw += drawMs; stWorst = max(stWorst, dt)
        if (stTime < 1.0) return
        val c = canvas
        fun f(v: Double) = ((v * 10).roundToInt() / 10.0).toString()
        emit("onStats", "${(stFrames / stTime).roundToInt()} fps · worst ${(stWorst * 1000).roundToInt()} ms\n" +
            "sim ${f(stSim / stFrames)} ms · draw ${f(stDraw / stFrames)} ms\n" +
            "${c?.width}×${c?.height} (scale ${SCALES[scaleStep]}) · ${current.name.lowercase()}")
        stFrames = 0; stTime = 0.0; stSim = 0.0; stDraw = 0.0; stWorst = 0.0
    }

    /** Pause rendering and the sim while the page is hidden; on return the sim skips ahead like the Android app. */
    fun setVisible(visible: Boolean) {
        if (visible == running) return
        running = visible
        if (visible) {
            val away = (js("Date.now()") as Double - hiddenAt) / 1000.0
            if (away > 5) sim?.advanceAbstract(min(away, 24 * 3600.0))
            lastFrame = -1.0
            window.requestAnimationFrame(::frame)
        } else hiddenAt = js("Date.now()") as Double
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
    fun setSpeed(speed: Double) { sim?.speed = speed.coerceIn(0.0, 1000.0) }

    /** "Rocks can shift": the dynamic rockscape for the tank and the sandbox (off: rocks never move). */
    fun setDynamicRocks(on: Boolean) {
        reality?.enqueue(Command.DynamicRocks(on))
        if (sim !== reality) sim?.enqueue(Command.DynamicRocks(on))
    }

    /** Puts the tank's rocks where they lay at the last visit (the text `onRocks` handed the page). */
    fun restoreRocks(state: String) { reality?.restoreRocks(state) }
    fun follow(id: String?) { renderer?.follow(id) }
    /** Follow camera buttons: [steps] > 0 zooms closer, < 0 further out (eases there, bounces at the limits). */
    fun followZoom(steps: Double) { renderer?.followCam?.zoom(steps.toFloat()) }
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
        val s = sim ?: return false
        if (takeOut) s.enqueue(Command.RemoveRock(p))
        else {
            val sh = runCatching { com.reeflive.core.sim.DryRockShape.valueOf(shape) }.getOrDefault(com.reeflive.core.sim.DryRockShape.BOULDER)
            val z = if (surface == TouchSurface.GLASS || surface == TouchSurface.SURFACE) com.reeflive.app.render.Camera.TANK[2].toDouble() / 2 else p.z
            s.enqueue(Command.PlaceRock(sh, p.x, z, turnDeg))
        }
        return true
    }
    /** Aquascape builder: takes out the dry rock added last. */
    fun scapeUndo() { sim?.enqueue(Command.RemoveRock(null)) }
    /** Aquascape builder: takes every rock out, to build from scratch. */
    fun scapeClear() { sim?.enqueue(Command.ClearRocks) }
    /** The dry rock shapes for the builder bar: [{id, label}]. */
    fun scapeShapes(): String = JSON.stringify(com.reeflive.core.sim.DryRockShape.entries.map { e -> obj { it.id = e.name; it.label = e.label } }.toTypedArray())

    fun touchTank(): Boolean {
        val (p, surface) = pendingTouch ?: return false
        pendingTouch = null
        ReefSandbox.touch(p, surface)
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
        sim?.enqueue(Command.SubmarineMode(on, visible, headlight))
    }
    /** The pilot's stick, each axis -1..1 (send on change only). */
    fun steerSub(thrust: Double, turn: Double, rise: Double, strafe: Double) {
        sim?.enqueue(Command.SteerSubmarine(com.reeflive.core.sim.SubControls(thrust, turn, rise, strafe)))
    }
    /** Ride inside the sub (first person) or chase it from behind. */
    fun subView(firstPerson: Boolean) { renderer?.subFirstPerson = firstPerson }
    /**
     * Watch an animal from the sub: it glides over and keeps it company at a polite distance. [mode] HOVER, CIRCLE or
     * ALONGSIDE; [id] null (or "") = back to free steering.
     */
    fun subWatch(id: String?, mode: String) {
        val m = runCatching { com.reeflive.core.sim.SubWatch.valueOf(mode) }.getOrDefault(com.reeflive.core.sim.SubWatch.HOVER)
        sim?.enqueue(Command.SubmarineWatch(id?.takeIf { it.isNotEmpty() }, m))
    }
    /** The sub's ‹ ›: the next / previous fish ([step] ±1), or the nearest when none is watched. */
    fun subWatchNext(step: Int) { sim?.enqueue(Command.SubmarineWatchNext(step)) }
    /** JSON {curious, wary, watch, mode} while the sub is out, else null: who reacts to it and whom it is watching. */
    fun subState(): String = sim?.latest?.submarine?.let {
        JSON.stringify(obj { o -> o.curious = it.curious; o.wary = it.wary; o.watch = it.watchId; o.mode = it.watchMode.name })
    } ?: "null"
    fun setQuality(q: String) {
        val v = runCatching { RenderQuality.valueOf(q.uppercase()) }.getOrNull() ?: return
        current = v; scaleStep = 0; goodWindows = 0; floorWindows = 0
        renderer?.setQuality(v)
        resize()
    }

    /** Logged readings from the tracker (base units, temperature in °F) → the live tank, like the Android reality pick-up. */
    fun applyReadings(json: String) {
        val o = JSON.parse<dynamic>(json)
        val latest = HashMap<String, Double>()
        for (k in js("Object").keys(o).unsafeCast<Array<String>>()) (o[k] as? Double)?.let { latest[k] = it }
        RealityInit.changedCommands(applied, latest).forEach { reality?.enqueue(it) }
        applied = latest
    }

    /** Tracker parameter table (shared ParameterTable): id, name, unit, precision, ideal and acceptable ranges. */
    fun parameters(): String = JSON.stringify(ParameterTable.SOFT_CORAL.sortedBy { it.order }.map { d ->
        obj {
            it.id = d.id; it.name = d.name; it.unit = d.baseUnit.symbol; it.precision = d.precision
            it.idealLo = d.ideal.lo; it.idealHi = d.ideal.hi; it.okLo = d.acceptable.lo; it.okHi = d.acceptable.hi
            it.favorite = d.favorite; it.dangerLo = d.dangerLow; it.dangerHi = d.dangerHigh
        }
    }.toTypedArray())

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
        val snap = sim?.latest ?: return "null"
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
        val p = sim?.latest?.coralPick ?: return "null"
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
        val f = sim?.latest?.netFlush ?: return "null"
        return JSON.stringify(obj { it.key = "${f.seq}:${f.id}"; it.name = f.name; it.isFish = f.isFish; it.body = f.body; it.accent = f.accent })
    }

    /** HUD state: clock, light phase, water and the organisms (id, name, species, health). */
    fun state(): String {
        val s = sim ?: return "null"
        val snap: SimSnapshot = s.latest
        val w = snap.water
        val o: dynamic = js("({})")
        o.time = snap.simTimeSec; o.phase = snap.dayPhase; o.speed = snap.speed; o.light = snap.lightIntensity
        o.tempC = w.tempC; o.sg = w.sg; o.ph = w.ph; o.alk = w.alk; o.ca = w.ca; o.mg = w.mg
        o.no3 = w.no3; o.po4 = w.po4; o.nh3 = w.nh3Total; o.no2 = w.no2; o.flow = snap.flowStrength
        o.organisms = s.organisms.filter { it.alive }.map { a ->
            obj { it.id = a.id; it.name = a.nickname; it.species = a.species.common; it.health = a.health }
        }.toTypedArray()
        o.events = snap.events.takeLast(3).map { it.toString() }.toTypedArray()
        return JSON.stringify(o)
    }

    // ------------------------------------------------------------------ loading

    private suspend fun fetchJson(path: String): dynamic {
        val r = window.fetch(base + path).await()
        if (!r.ok) throw IllegalStateException("Missing $path (${r.status})")
        return r.json().await()
    }

    private suspend fun fetchFile(path: String, timeoutMs: Int = 0) {
        if (kotlin.Resources.has(resKey(path))) return
        val init: dynamic = js("({})")
        val timer = if (timeoutMs > 0) {
            val ctl: dynamic = js("new AbortController()")
            init.signal = ctl.signal
            window.setTimeout({ ctl.abort() }, timeoutMs)
        } else null
        try {
            val r = window.fetch(base + path, init.unsafeCast<org.w3c.fetch.RequestInit>()).await()
            if (!r.ok) return
            val ab = r.arrayBuffer().await().unsafeCast<ArrayBuffer>()
            kotlin.Resources.put(resKey(path), Int8Array(ab).unsafeCast<ByteArray>())
        } finally {
            timer?.let { window.clearTimeout(it) }
        }
    }

    /**
     * The renderer marks a model that fails to load as failed for good, so a dropped download (common for the 10 MB
     * rockscape on a phone connection) would leave the tank without rock until reload. A stalled download is cut
     * off and retried too, since the load waits for it.
     */
    private suspend fun fetchWithRetry(path: String) {
        for (attempt in 0 until 4) {
            runCatching { fetchFile(path, timeoutMs = 45_000 shl attempt) }
            if (kotlin.Resources.has(resKey(path))) return
            kotlinx.coroutines.delay(500L shl attempt)
        }
        console.warn("[ReefLive] could not download $path")
    }

    /** "res/x" is a classpath resource "x"; "assets/x" stays "assets/x" (see AssetManager). */
    private fun resKey(path: String) = if (path.startsWith("res/")) path.removePrefix("res/") else path

    private suspend fun fetchAll(paths: List<String>) {
        val jobs = paths.map { p -> Promise<Unit> { ok, _ -> MainScope().launch { runCatching { fetchFile(p) }; ok(Unit) } } }
        Promise.all(jobs.toTypedArray()).await()
    }

    private const val TICK = 0.05
    /** Render-resolution multipliers tried in order while frames are slow (1 → ⅔ of the device pixel ratio). */
    private val SCALES = doubleArrayOf(1.0, 0.9, 0.8, 0.72, 0.64, 0.57, 0.5)
    private const val ADAPT_FRAMES = 45
    private const val SLOW_DT = 1.0 / 48
    private const val FAST_DT = 1.0 / 57
    private const val GOOD_WINDOWS = 4
    private const val FLOOR_WINDOWS = 3
}

internal fun obj(fill: (dynamic) -> Unit): dynamic { val o: dynamic = js("({})"); fill(o); return o }

fun main() {}
