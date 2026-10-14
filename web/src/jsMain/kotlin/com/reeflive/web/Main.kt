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
    private var touch: TouchController? = null
    private var canvas: HTMLCanvasElement? = null
    private var listener: dynamic = null
    private var base = ""
    private var simAcc = 0.0
    private var lastFrame = -1.0
    private var running = false
    private var applied: Map<String, Double> = emptyMap()
    internal val scenarioQueue = ArrayList<Pair<Double, Command>>()

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

                val gl = cnv.getContext("webgl2", js("({antialias: true, alpha: false, depth: true, stencil: false, powerPreference: 'high-performance'})"))
                    ?: throw IllegalStateException("This browser has no WebGL 2")
                GLES30.gl = gl
                val q = runCatching { RenderQuality.valueOf(quality.uppercase()) }.getOrDefault(RenderQuality.HIGH)
                current = q
                val r = TankRenderer(Context(AssetManager(files)), q)
                r.snapshotProvider = { sim?.latest }
                r.demoWhenEmpty = false
                r.listener = object : TankViewListener {
                    override fun onPick(id: String?) { emit("onPick", id) }
                    override fun onFollowRequest(id: String?) { r.follow(id); emit("onFollow", id) }
                    override fun onFollowLost(id: String) { emit("onFollow", null) }
                    override fun onTapGlass(worldPoint: Vec3) { sim?.enqueue(Command.TapGlass(worldPoint)); emit("onKnock", null) }
                    // as on Android (LiveTankScreen): a long press drops a pinch of pellets where the finger is
                    override fun onLongPress(worldPoint: Vec3) { sim?.enqueue(Command.Feed(FoodKind.PELLET, 1.0, worldPoint)); emit("onFed", "PELLET") }
                    override fun onScrape(stroke: GlassStroke) { ReefSandbox.scrape(stroke); emit("onScrape", stroke.lengthCm) }
                    override fun onFlipperTap() { emit("onFlipperTap", null) }
                }
                shelf.onPick = { item, _, _, _ -> emit("onShelfPick", item) }
                r.shelf = shelf
                r.bursts = ReefSandbox.bursts
                renderer = r
                r.onSurfaceCreated(null, null)
                resize()
                touch = TouchController(Context(AssetManager(files)), View(), r.input)
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

    /** HIGH draws at the screen's full resolution (3x on iPhone Pro / Pro Max); lower presets cap it. */
    private fun pixelRatio(): Double = min(window.devicePixelRatio, when (current) {
        RenderQuality.HIGH -> 3.0
        RenderQuality.MEDIUM -> 2.0
        RenderQuality.LOW -> 1.5
    })

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
    }

    private fun frame(t: Double) {
        if (!running) return
        val dt = if (lastFrame < 0) 0.0 else ((t - lastFrame) / 1000.0).coerceIn(0.0, 0.25)
        lastFrame = t
        val s = sim
        if (s != null) {
            simAcc += dt
            var steps = 0
            while (simAcc >= TICK && steps < 3) {
                while (scenarioQueue.isNotEmpty() && scenarioQueue[0].first <= s.simTimeSec) s.enqueue(scenarioQueue.removeAt(0).second)
                s.stepWall(TICK); simAcc -= TICK; steps++
            }
            if (steps == 3) simAcc = 0.0
        }
        val c = canvas
        if (c != null && (c.width != (c.clientWidth * pixelRatio()).toInt())) resize()
        renderer?.onDrawFrame(null)
        window.requestAnimationFrame(::frame)
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
    fun follow(id: String?) { renderer?.follow(id) }
    /** Lamp looks (render only): REAL, DAY, DUSK, NIGHT, MOON. */
    fun setViewLight(name: String) { renderer?.viewLight = runCatching { com.reeflive.app.render.ViewLight.valueOf(name) }.getOrDefault(com.reeflive.app.render.ViewLight.REAL) }
    /** The 3D supply shelf beside the tank: the camera swings over to it; tapping a prop calls `onShelfPick(id)`. */
    fun shelfOpen(on: Boolean) { shelf.setOpen(on, false) }
    fun shelfRow(row: Int) { shelf.focusRow(row) }
    fun shelfPick(id: String) { shelf.pick(id) }
    fun shelfLocked(ids: String) { shelf.locked = ids.split(",").filter { it.isNotBlank() }.toSet() }
    fun resetCamera() { renderer?.resetCamera() }
    fun setQuality(q: String) {
        val v = runCatching { RenderQuality.valueOf(q.uppercase()) }.getOrNull() ?: return
        current = v
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

    /** HUD state: clock, light phase, water and the organisms (id, name, species, health). */
    fun state(): String {
        val s = sim ?: return "null"
        val snap: SimSnapshot = s.latest
        val w = snap.water
        val o: dynamic = js("({})")
        o.time = snap.simTimeSec; o.phase = snap.dayPhase; o.speed = s.speed; o.light = snap.lightIntensity
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
}

internal fun obj(fill: (dynamic) -> Unit): dynamic { val o: dynamic = js("({})"); fill(o); return o }

fun main() {}
