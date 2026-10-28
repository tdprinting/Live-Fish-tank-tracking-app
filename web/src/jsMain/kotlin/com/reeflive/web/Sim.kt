@file:OptIn(ExperimentalJsExport::class, kotlinx.coroutines.DelicateCoroutinesApi::class)

package com.reeflive.web

import com.reeflive.app.render.BurstKind
import com.reeflive.app.render.CareAct
import com.reeflive.app.render.GlassStroke
import com.reeflive.app.render.RoomLook
import com.reeflive.core.custom.economy.DisplaySetup
import com.reeflive.core.geom.Vec3
import com.reeflive.core.live.RealityInit
import com.reeflive.core.sim.BuiltRock
import com.reeflive.core.sim.Command
import com.reeflive.core.sim.DefaultStock
import com.reeflive.core.sim.FoodKind
import com.reeflive.core.sim.GlassPane
import com.reeflive.core.sim.RockData
import com.reeflive.core.sim.SimSnapshot
import com.reeflive.core.sim.Simulation
import com.reeflive.core.sim.SpeciesCatalog
import com.reeflive.core.sim.Stocking
import com.reeflive.core.sim.TankConfig
import com.reeflive.core.sim.TouchSurface
import com.reeflive.core.sim.WaterState
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise
import kotlin.js.Promise
import kotlin.math.min

/**
 * The simulation side of the web app: the tanks (the real one, the sandbox fork, My tank), stepping them at 20 Hz and
 * the commands the 3D view sends (taps, fingers, the pose level of detail). It runs where the sim runs: in a Web
 * Worker (web/site/simworker.js), or on the page thread when workers are off (`?noworker`) or fail. Either way the
 * page reaches it only through SimHost (web/site/simhost.js), and it reaches the 3D view only through [fx].
 * [ReefSandbox] and [ReefMyTank] live on this side too.
 */
@JsExport
object ReefSim {
    /** The sim on screen: [reality] in Watch mode, the sandbox fork in Sandbox mode (see [ReefSandbox]). */
    internal var sim: Simulation? = null
    /** The tank as logged (default stock + the tracker's readings); runs at 1x. */
    internal var reality: Simulation? = null
    /** The sim the pose level of detail was last handed to. */
    private var lodSim: Simulation? = null
    internal var species: SpeciesCatalog? = null
    internal var rock: RockData? = null
    internal val scenarioQueue = ArrayList<Pair<Double, Command>>()
    private var applied: Map<String, Double> = emptyMap()
    private var simAcc = 0.0
    /** Where effects for the 3D view and events for the page go (the view's own handlers, or a message to the page). */
    internal var fx: SimFx = NoFx
    /** Where My tank is saved (this browser's storage, or a copy the worker keeps and writes back through the page). */
    internal var store: KeyStore = BrowserStore
    /** The page asks for less motion (no walk in from the street). */
    internal var reducedMotion = false

    /** Loads the species sheets and the rock and builds the real tank. [baseUrl] is where `assets/` and `res/` are. */
    fun boot(baseUrl: String): Promise<Unit> = GlobalScope.promise {
        Net.base = baseUrl.trimEnd('/') + "/"
        // the sim tables and species sheets in one file (web/build.gradle.kts bootPack; on the page already loaded)
        if (!kotlin.Resources.has("species/index.json")) Net.loadPack("boot.json")
        val have = Net.fetchJson("res/species/index.json")
        // Every species with a sheet: the default stock plus everything Add life can release in the sandbox.
        val known = (have.species.unsafeCast<Array<String>?>() ?: emptyArray()).toSet()
        val ids = (DefaultStock.all.map { it.speciesId } + Stocking.CATALOG.map { it.speciesId } + known)
            .distinct().filter { known.isEmpty() || it in known }
        Net.fetchAll(ids.map { "res/species/$it.json" } + listOf("assets/sim/rock_sdf.bin", "assets/sim/anchors.json", "res/sim/interactions.json"))
        build(ids)
    }

    private fun build(speciesIds: List<String>) {
        val species = SpeciesCatalog.fromClasspath(speciesIds).also { this.species = it }
        val rock = runCatching {
            RockData.fromFiles(kotlin.Resources.get("assets/sim/rock_sdf.bin")!!, kotlin.Resources.get("assets/sim/anchors.json")!!.decodeToString())
        }.getOrElse { RockData.defaultTank() }.also { this.rock = it }
        val now: dynamic = js("new Date()")
        val secOfDay = (now.getHours() as Int) * 3600.0 + (now.getMinutes() as Int) * 60.0 + (now.getSeconds() as Int)
        val config = TankConfig()
        val s = Simulation(
            config = config, species = species, rock = rock, startTimeSec = secOfDay,
            initialWater = WaterState(volumeL = config.systemLiters), livestock = DefaultStock.named,
        )
        s.speed = 1.0
        s.stepWall(0.0)
        sim = s; reality = s
    }

    /**
     * Runs the sim for [dt] wall seconds of frame time: 20 Hz ticks, at most three per call (a slow device falls behind
     * instead of spiralling). Returns how many ticks ran.
     */
    fun frame(dt: Double): Int {
        val s = sim ?: return 0
        simAcc += dt
        var steps = 0
        while (simAcc >= TICK && steps < 3) {
            while (scenarioQueue.isNotEmpty() && scenarioQueue[0].first <= s.simTimeSec) s.enqueue(scenarioQueue.removeAt(0).second)
            s.stepWall(TICK); simAcc -= TICK; steps++
            // a rock in the tank settled or toppled: the page keeps its new spot for the next visit
            if (s === reality && s.latest.events.any { it is com.reeflive.core.sim.SimEvent.RockShifted }) fx.emit("onRocks", s.rockState())
            else if (s === reality && s.latest.events.any { it is com.reeflive.core.sim.SimEvent.RockscapeChanged }) fx.emit("onRockscape", s.rockState())
        }
        if (steps == 3) simAcc = 0.0
        return steps
    }

    /** The newest snapshot of the sim on screen (a SimSnapshot), or null with none (My tank before one is set up). */
    fun latest(): Any? = sim?.latest

    /** The page is back after [awaySec] seconds hidden: the sim skips ahead like the Android app. */
    fun resume(awaySec: Double) {
        if (awaySec > 5) sim?.advanceAbstract(min(awaySec, 24 * 3600.0))
    }

    /** The page was hidden ([visible] false; the sim stops with the frames) or is back after [awaySec] seconds. */
    fun setVisible(visible: Boolean, awaySec: Double) { if (visible) resume(awaySec) }

    // ------------------------------------------------------------------ commands from the page

    /** 0 pauses. Watch mode runs the real tank at 1x; faster time is a sandbox thing (as on Android). */
    fun setSpeed(speed: Double) { sim?.speed = speed.coerceIn(0.0, 1000.0) }

    /** "Rocks can shift": the dynamic rockscape for the tank and the sandbox (off: rocks never move). */
    fun setDynamicRocks(on: Boolean) {
        reality?.enqueue(Command.DynamicRocks(on))
        if (sim !== reality) sim?.enqueue(Command.DynamicRocks(on))
    }

    /** Puts the tank's rocks where they lay at the last visit (the text `onRocks` handed the page). */
    fun restoreRocks(state: String) { reality?.restoreRocks(state) }

    /** Aquascape builder: takes out the dry rock added last. */
    fun scapeUndo() { sim?.enqueue(Command.RemoveRock(null)) }
    /** Aquascape builder: takes every rock out, to build from scratch. */
    fun scapeClear() { sim?.enqueue(Command.ClearRocks) }

    /**
     * Submarine mode (as the Android live menu): launch or park the toy sub; [visible] false hides it from the fish;
     * [headlight] is its lamp. (The view stops following first.)
     */
    fun submarine(on: Boolean, visible: Boolean, headlight: Boolean) { sim?.enqueue(Command.SubmarineMode(on, visible, headlight)) }
    /** The pilot's stick, each axis -1..1 (send on change only). */
    fun steerSub(thrust: Double, turn: Double, rise: Double, strafe: Double) {
        sim?.enqueue(Command.SteerSubmarine(com.reeflive.core.sim.SubControls(thrust, turn, rise, strafe)))
    }
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

    /** Logged readings from the tracker (base units, temperature in °F) → the live tank, like the Android reality pick-up. */
    fun applyReadings(json: String) {
        val o = JSON.parse<dynamic>(json)
        val latest = HashMap<String, Double>()
        for (k in js("Object").keys(o).unsafeCast<Array<String>>()) (o[k] as? Double)?.let { latest[k] = it }
        RealityInit.changedCommands(applied, latest).forEach { reality?.enqueue(it) }
        applied = latest
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
        // per-surface algae cover the renderer draws (group-major: glass, sand, rock top, rock shade per group)
        o.algaeCover = snap.algae.cover.map { kotlin.math.round(it * 1000f) / 1000f }.toTypedArray()
        return JSON.stringify(o)
    }

    // ------------------------------------------------------------------ touches from the 3D view (ReefLive)

    /** A tap right on an animal also touches it: snails and crabs tuck in, fish just get picked (TankTouch). */
    fun touchAnimal(id: String) { sim?.enqueue(Command.TouchAnimal(id)) }
    fun tapGlass(x: Double, y: Double, z: Double) { sim?.enqueue(Command.TapGlass(Vec3(x, y, z))) }
    /** As on Android (LiveTankScreen): a long press drops a pinch of pellets where the finger is. */
    fun pelletAt(x: Double, y: Double, z: Double) { sim?.enqueue(Command.Feed(FoodKind.PELLET, 1.0, Vec3(x, y, z))) }
    /** A fingertip on the glass the fish follow ([down] false: lifted). */
    fun finger(down: Boolean, x: Double, y: Double, z: Double, speedCmS: Double, surface: String) {
        sim?.enqueue(Command.Finger(if (down) Vec3(x, y, z) else null, speedCmS, TouchSurface.valueOf(surface)))
    }
    fun puff(x: Double, y: Double, z: Double, vx: Double, vy: Double, vz: Double) { sim?.enqueue(Command.WaterPuff(Vec3(x, y, z), Vec3(vx, vy, vz))) }
    /** A stroke of the glass scraper (flipper size) on the front pane. */
    fun scrape(x0: Double, y0: Double, x1: Double, y1: Double) {
        sim?.enqueue(Command.ScrapeGlass(GlassPane.FRONT, x0, y0, x1, y1, GlassStroke.FLIPPER_W.toDouble(), GlassStroke.FLIPPER_H.toDouble()))
    }
    /** A fingertip tap on the tank (as LiveTankScreen.onTouchTank); the view plays the ripple or sand puff. */
    fun touchTank(x: Double, y: Double, z: Double, surface: String) { sim?.enqueue(Command.TouchTank(Vec3(x, y, z), TouchSurface.valueOf(surface))) }
    /** Aquascape builder: a dry rock of [shape] turned [turnDeg] dropped at ([x], [z]). */
    fun placeRock(shape: String, x: Double, z: Double, turnDeg: Double) {
        val sh = runCatching { com.reeflive.core.sim.DryRockShape.valueOf(shape) }.getOrDefault(com.reeflive.core.sim.DryRockShape.BOULDER)
        sim?.enqueue(Command.PlaceRock(sh, x, z, turnDeg))
    }
    /** Aquascape builder: takes out the rock at the tapped point. */
    fun removeRock(x: Double, y: Double, z: Double) { sim?.enqueue(Command.RemoveRock(Vec3(x, y, z))) }

    /**
     * Pose level of detail (TankRenderer PoseLod → Simulation.poseDetail): off-screen and tiny animals are posed less
     * often; a sim that stops being the shown one goes back to full detail.
     */
    fun poseDetail(ids: Array<String>, levels: IntArray) {
        val m = HashMap<String, Int>(ids.size * 2)
        for (i in ids.indices) m[ids[i]] = levels[i]
        val s = sim
        if (s !== lodSim) { lodSim?.poseDetail = emptyMap(); lodSim = s }
        s?.poseDetail = m
    }

    // ------------------------------------------------------------------ the 3D care shows' callbacks

    private val careJobs = HashMap<Int, Pair<() -> Unit, () -> Unit>>()
    private var careSeq = 0

    /** Plays a care animation in the 3D view; [onImpact] runs when it reaches the water, [onDone] when it is over. */
    internal fun careShow(act: CareAct, tint: Int, amount: Float = 0.25f, at: FloatArray? = null, onImpact: () -> Unit = {}, onDone: () -> Unit = {}) {
        val id = ++careSeq
        careJobs[id] = onImpact to onDone
        fx.care(id, act, tint, amount, at)
    }
    fun careImpact(id: Int) { careJobs[id]?.first?.invoke() }
    fun careDone(id: Int) { careJobs.remove(id)?.second?.invoke() }

    // ------------------------------------------------------------------ where the sim runs (the page's SimHost sets these)

    /** In a worker: effects for the 3D view and page events go to the page as messages through [post] (op, args). */
    fun useRemoteFx(post: (String, Array<Any?>) -> Unit) { fx = PostFx(post) }

    /**
     * In a worker: My tank's saves go to a copy of the page's storage ([initial]: {key: text}) and every write is handed
     * to [post] (key, text or null to remove) for the page to store.
     */
    fun useRemoteStore(initial: dynamic, post: (String, String?) -> Unit) {
        val m = HashMap<String, String>()
        if (initial != null) for (k in js("Object").keys(initial).unsafeCast<Array<String>>()) (initial[k] as? String)?.let { m[k] = it }
        store = MirrorStore(m, post)
    }

    fun setReducedMotion(on: Boolean) { reducedMotion = on }

    internal const val TICK = 0.05
}

/** What the sim side asks of the 3D view and the page ([ReefSim.fx]). */
internal interface SimFx {
    fun burst(kind: BurstKind, x: Float, y: Float, z: Float, r: Float, g: Float, b: Float, amount: Float)
    /** A care show (render CareScene); the view calls back [ReefSim.careImpact] / [ReefSim.careDone] with [id]. */
    fun care(id: Int, act: CareAct, tint: Int, amount: Float, at: FloatArray?)
    fun cleaning(on: Boolean)
    /** A page event (`onFixed`, `onRocks`...), as the page's listener on [ReefLive.start] hears it. */
    fun emit(event: String, arg: Any?)
    fun homeLook(look: RoomLook)
    fun storeEnter(store: String, tanks: Int, at: Int, setups: List<DisplaySetup>, trade: String?, walkIn: Boolean)
    fun storeShow(i: Int)
    fun storeCounter(on: Boolean)
    fun storeTradeReply(yes: Boolean)
    fun storeLeave()
    /** A built display tank's dry rock: the view makes its mesh ahead of the first frame that draws it. */
    fun meshRock(rock: BuiltRock)
}

private object NoFx : SimFx {
    override fun burst(kind: BurstKind, x: Float, y: Float, z: Float, r: Float, g: Float, b: Float, amount: Float) {}
    override fun care(id: Int, act: CareAct, tint: Int, amount: Float, at: FloatArray?) {}
    override fun cleaning(on: Boolean) {}
    override fun emit(event: String, arg: Any?) {}
    override fun homeLook(look: RoomLook) {}
    override fun storeEnter(store: String, tanks: Int, at: Int, setups: List<DisplaySetup>, trade: String?, walkIn: Boolean) {}
    override fun storeShow(i: Int) {}
    override fun storeCounter(on: Boolean) {}
    override fun storeTradeReply(yes: Boolean) {}
    override fun storeLeave() {}
    override fun meshRock(rock: BuiltRock) {}
}

/** Effects as messages to the page (the sim runs in a worker); the page hands them to [ReefLive.applyFx]. */
private class PostFx(private val post: (String, Array<Any?>) -> Unit) : SimFx {
    override fun burst(kind: BurstKind, x: Float, y: Float, z: Float, r: Float, g: Float, b: Float, amount: Float) =
        post("burst", arrayOf(kind.ordinal, x, y, z, r, g, b, amount))
    override fun care(id: Int, act: CareAct, tint: Int, amount: Float, at: FloatArray?) = post("care", arrayOf(id, act.ordinal, tint, amount, at))
    override fun cleaning(on: Boolean) = post("cleaning", arrayOf(on))
    override fun emit(event: String, arg: Any?) = post("emit", arrayOf(event, arg))
    override fun homeLook(look: RoomLook) = post("homeLook", arrayOf(look))
    override fun storeEnter(store: String, tanks: Int, at: Int, setups: List<DisplaySetup>, trade: String?, walkIn: Boolean) =
        post("storeEnter", arrayOf(store, tanks, at, setups, trade, walkIn))
    override fun storeShow(i: Int) = post("storeShow", arrayOf(i))
    override fun storeCounter(on: Boolean) = post("storeCounter", arrayOf(on))
    override fun storeTradeReply(yes: Boolean) = post("storeTradeReply", arrayOf(yes))
    override fun storeLeave() = post("storeLeave", emptyArray())
    override fun meshRock(rock: BuiltRock) = post("meshRock", arrayOf(rock))
}

/** Text storage for My tank's saves. */
internal interface KeyStore {
    fun read(key: String): String?
    fun write(key: String, v: String)
    fun remove(key: String)
}

/** This browser's localStorage (the page thread). */
internal object BrowserStore : KeyStore {
    override fun read(key: String): String? = runCatching { kotlinx.browser.localStorage.getItem(key) }.getOrNull()
    override fun write(key: String, v: String) { runCatching { kotlinx.browser.localStorage.setItem(key, v) } }
    override fun remove(key: String) { runCatching { kotlinx.browser.localStorage.removeItem(key) } }
}

/** A worker's copy of the page's storage: reads answer from the copy, writes update it and go to the page. */
private class MirrorStore(private val m: HashMap<String, String>, private val post: (String, String?) -> Unit) : KeyStore {
    override fun read(key: String): String? = m[key]
    override fun write(key: String, v: String) { m[key] = v; post(key, v) }
    override fun remove(key: String) { m.remove(key); post(key, null) }
}
