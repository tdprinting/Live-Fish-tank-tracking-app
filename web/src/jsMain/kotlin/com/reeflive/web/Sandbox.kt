@file:OptIn(ExperimentalJsExport::class)

package com.reeflive.web

import com.reeflive.app.audio.TankSoundMix
import com.reeflive.app.render.BurstKind
import com.reeflive.app.render.CareAct
import com.reeflive.app.render.CareJob
import com.reeflive.app.render.CareShowControl
import com.reeflive.app.render.GlassStroke
import com.reeflive.app.render.TankBurst
import com.reeflive.app.render.TankBursts
import com.reeflive.app.ui.live.LiveTips
import com.reeflive.app.ui.live.SandboxCategory
import com.reeflive.app.ui.live.SandboxControlMode
import com.reeflive.app.ui.live.SandboxParams
import com.reeflive.app.ui.live.SandboxPreset
import com.reeflive.app.ui.live.TankReading
import com.reeflive.app.ui.live.ThoughtBubblePicker
import com.reeflive.app.ui.live.ThoughtHold
import com.reeflive.app.ui.live.toFishCardState
import com.reeflive.app.ui.live.waterReadouts
import com.reeflive.core.live.Scenario
import com.reeflive.core.sim.AiptasiaTreatment
import com.reeflive.core.sim.BacteriaAction
import com.reeflive.core.sim.CoralKind
import com.reeflive.core.sim.CareFix
import com.reeflive.core.sim.Command
import com.reeflive.core.sim.Disease
import com.reeflive.core.sim.FoodKind
import com.reeflive.core.sim.GlassPane
import com.reeflive.core.sim.LightSchedule
import com.reeflive.core.sim.LivestockSeed
import com.reeflive.core.sim.MaintenanceTask
import com.reeflive.core.sim.MicroAction
import com.reeflive.core.sim.MicroGroup
import com.reeflive.core.sim.MicroScene
import com.reeflive.core.sim.MicroView
import com.reeflive.core.sim.MicroZoom
import com.reeflive.core.sim.OrganismStats
import com.reeflive.core.sim.PestController
import com.reeflive.core.sim.Simulation
import com.reeflive.core.sim.StockGroup
import com.reeflive.core.sim.StockSize
import com.reeflive.core.sim.Stocking
import com.reeflive.core.sim.SpotMenu
import com.reeflive.core.sim.TankDoctor
import com.reeflive.core.sim.TankSpot
import com.reeflive.core.sim.TankSpots
import com.reeflive.core.sim.HungryFeed
import com.reeflive.core.sim.CoralCare
import com.reeflive.core.sim.CoralHealth
import com.reeflive.core.sim.rows
import com.reeflive.core.sim.TreatmentKind
import com.reeflive.core.sim.WaterState
import com.reeflive.core.sim.timelapse.TimelapseChange
import com.reeflive.core.sim.timelapse.TimelapseFork
import com.reeflive.core.sim.timelapse.TimelapseRun
import com.reeflive.core.sim.timelapse.TimelapseSpec
import com.reeflive.core.status.Status
import kotlin.math.floor

/**
 * The live screen's controls for the page, mirroring the Android LiveTankViewModel / SimulationHolder pair: Watch
 * mode shows the real tank (default stock + the tracker's readings) at 1x; Sandbox forks it into a pretend tank where
 * every lever works, and Reset tank restores the fork. Shared tables (SandboxParams, LiveTips, TankSoundMix,
 * LiveUiState) are the Android app's own files compiled into this build, so both apps offer the same sandbox.
 */
@JsExport
object ReefSandbox {
    private val live get() = ReefLive
    /** Every lever works: the pretend tank or My tank ([mine]). */
    private var sandbox = false
    /** My tank (ReefMyTank): the keeper's own saved tank. */
    private var mine = false
    private var fork: Pair<WaterState, List<LivestockSeed>>? = null
    private var leftRealityAt = 0.0
    private var lightPct = 100.0
    private var predation = true
    private val extras = HashMap<String, Double>()
    internal val bursts = TankBursts()
    /** "Fix tank": the 3D upkeep show (render CareScene) and the fixes still queued or playing in it. */
    internal val care = CareShowControl()
    private val fixQueued = HashSet<TankDoctor.Kind>()
    private var fixList: List<CareFix> = emptyList()

    // ------------------------------------------------------------------ catalog (static tables for the page)

    fun catalog(): String = JSON.stringify(obj { c ->
        c.params = SandboxParams.all.map { p ->
            obj {
                it.id = p.id; it.label = p.label; it.short = p.shortLabel; it.unit = p.unit; it.category = p.category.name
                it.min = p.min; it.max = p.max; it.step = p.step; it.decimals = p.decimals; it.curve = p.curve
                it.scale = p.scale; it.simId = p.simId; it.toggle = p.min == 0.0 && p.max == 1.0 && p.step == 1.0
                p.zones?.let { z -> it.zones = obj { o -> o.dangerBelow = z.dangerBelow; o.safeFrom = z.safeFrom; o.safeTo = z.safeTo; o.dangerAbove = z.dangerAbove } }
            }
        }.toTypedArray()
        c.categories = SandboxCategory.entries.map { e -> obj { it.id = e.name; it.label = e.label } }.toTypedArray()
        c.controlModes = SandboxControlMode.enabled.map { e -> obj { it.id = e.name; it.label = e.label } }.toTypedArray()
        c.presets = SandboxPreset.entries.map { e -> obj { it.id = e.name; it.label = e.label; it.blurb = e.blurb; it.hint = e.hint } }.toTypedArray()
        c.scenarios = Scenario.entries.map { e -> obj { it.id = e.name; it.label = e.label } }.toTypedArray()
        c.foods = FoodKind.entries.map { f -> obj { it.id = f.name; it.label = f.label; it.blurb = f.blurb; it.group = f.group.label; it.grams = f.typicalDoseGrams } }.toTypedArray()
        c.stock = Stocking.CATALOG.filter { live.species?.has(it.speciesId) != false }.map { e ->
            obj { it.id = e.speciesId; it.group = e.group.name; it.groupLabel = e.group.label; it.name = e.common; it.cm = e.typicalCm }
        }.toTypedArray()
        c.sizes = StockSize.entries.map { e -> obj { it.id = e.name; it.label = e.label } }.toTypedArray()
        c.diseases = Disease.entries.map { e -> obj { it.id = e.name; it.label = DISEASE_LABELS[e] ?: e.name } }.toTypedArray()
        c.treatments = TreatmentKind.entries.map { e -> obj { it.id = e.name; it.label = e.label; it.days = e.defaultDays } }.toTypedArray()
        c.micro = MicroAction.entries.map { e -> obj { it.id = e.name; it.label = e.label } }.toTypedArray()
        c.bacteria = BacteriaAction.entries.map { e -> obj { it.id = e.name; it.label = e.label } }.toTypedArray()
        c.microScenes = MicroScene.entries.map { e -> obj { it.id = e.name; it.label = e.label } }.toTypedArray()
        c.microZooms = MicroZoom.entries.map { e -> obj { it.id = e.name; it.label = e.label } }.toTypedArray()
        c.lapseParams = LAPSE_PARAMS.map { lp ->
            val p = SandboxParams.byId(lp.row)!!
            obj { it.id = lp.id; it.label = p.label; it.unit = p.unit; it.min = lp.lo ?: p.min; it.max = lp.hi ?: p.max; it.step = p.step; it.decimals = p.decimals; it.curve = p.curve }
        }.toTypedArray()
        c.lapsePresets = LAPSE_PRESETS.map { p ->
            obj { it.label = p.label; it.param = p.param; it.target = p.target; it.ramp = p.ramp; it.rampHours = p.rampHours; it.hours = p.hours }
        }.toTypedArray()
        c.tips = obj { t ->
            t.watchTitle = LiveTips.WATCH_TITLE; t.sandboxTitle = LiveTips.SANDBOX_TITLE; t.hello = LiveTips.HELLO
            t.watch = LiveTips.watch.toTypedArray(); t.sandbox = LiveTips.sandbox.toTypedArray()
            t.hermieWatch = LiveTips.hermieWatch.toTypedArray(); t.hermieSandbox = LiveTips.hermieSandbox.toTypedArray()
            t.myTankTitle = LiveTips.MY_TANK_TITLE; t.myTank = LiveTips.myTank.toTypedArray(); t.hermieMyTank = LiveTips.hermieMyTank.toTypedArray()
        }
    })

    // ------------------------------------------------------------------ mode

    fun isSandbox(): Boolean = sandbox
    fun isMine(): Boolean = mine

    /**
     * "watch", "sandbox" or "mine". Leaving Sandbox drops the pretend tank; leaving My tank saves it; the real one skips
     * ahead by the time away.
     */
    fun setMode(m: String) {
        val wantMine = m == "mine"
        val wantSandbox = m == "sandbox"
        if (wantMine == mine && wantSandbox == (sandbox && !mine)) return
        val real = live.reality ?: return
        lightPct = 100.0; predation = true; extras.clear(); extras.putAll(DEFAULT_EXTRAS)
        live.scenarioQueue.clear()
        val wasWatch = !sandbox
        if (mine) ReefMyTank.leave()
        if (wasWatch) leftRealityAt = now()
        when {
            wantSandbox -> {
                val sb = forkOf(real)
                sb.speed = 1.0
                sb.stepWall(0.0)
                live.sim = sb
            }
            wantMine -> { fork = null; mine = true; live.sim = ReefMyTank.enter() }
            else -> {
                val away = (now() - leftRealityAt) / 1000.0
                if (away > 5) real.advanceAbstract(minOf(away, 24 * 3600.0)) else real.stepWall(0.0)
                real.speed = 1.0
                live.sim = real
                fork = null
            }
        }
        sandbox = wantSandbox || wantMine
        mine = wantMine
    }

    private fun forkOf(real: Simulation): Simulation {
        val seeds = real.organisms.filter { it.alive }.map {
            LivestockSeed(it.species.id, it.nickname, it.lengthCm, it.role, it.placement, it.id, it.variant)
        } + real.corals.map { LivestockSeed(it.species.id, it.nickname, 0.0, null, it.anchor.ifBlank { null }, it.id) }
        val water = real.water.s.copy()
        fork = water.copy() to seeds
        return Simulation(
            config = real.config.copy(predationEnabled = true),
            species = live.species!!,
            rock = live.rock!!,
            seed = real.config.seed + 17,
            startTimeSec = real.simTimeSec,
            initialWater = water,
            livestock = seeds,
            lights = LightSchedule(real.lights.points),
        ).also { it.copyRocksFrom(real) }
    }

    private fun now(): Double = js("Date.now()") as Double

    // ------------------------------------------------------------------ actions

    private val sim: Simulation? get() = live.sim

    /**
     * A pest product from the shelf (render CareScene INJECT / LASER): the tool goes to the biggest whole aiptasia
     * and doses it when it reaches it; once the tool is out the page hears "onPestTreated" (1 it took, 0 it hid,
     * -1 nothing to treat).
     */
    private fun treatAiptasia(t: AiptasiaTreatment): Boolean {
        val s = sim ?: return false
        val target = s.latest.corals.filter { CoralKind.resolve(it.speciesId) == CoralKind.AIPTASIA && it.tissue > 0.5f }.maxByOrNull { it.size }
        if (target == null) { live.emit("onPestTreated", -1); return true }
        val act = if (t == AiptasiaTreatment.LASER) CareAct.LASER else CareAct.INJECT
        val tint = when (t) { AiptasiaTreatment.KALK_PASTE -> 0xF4F4EE; AiptasiaTreatment.JOES_JUICE -> 0xE8F0FF; else -> 0xF2EEE2 }
        val at = floatArrayOf(target.restX, target.restY, target.restZ, target.restNx, target.restNy, target.restNz)
        care.play(CareJob(act, tint, at = at, onImpact = { send(Command.TreatAiptasia(t, target.id)) }, onDone = {
            val c = sim?.corals?.firstOrNull { it.id == target.id }
            live.emit("onPestTreated", if (c == null || c.treatment != null) 1 else 0)
        }))
        return true
    }
    private fun send(c: Command) { sim?.enqueue(c) }

    /**
     * One live-screen action by name (the Android view model's methods). [arg] is an enum name or id, [num] a number
     * where the action takes one. Returns false when it needs the sandbox and the page is in Watch mode.
     */
    fun act(name: String, arg: String?, num: Double): Boolean {
        val s = sim ?: return false
        // both modes: feeding, knocking, cleaning the glass (what the owner does to the real tank)
        when (name) {
            "feed" -> {
                val k = runCatching { FoodKind.valueOf(arg ?: "FLAKE") }.getOrDefault(FoodKind.FLAKE)
                send(Command.Feed(k, if (num > 0) num else k.typicalDoseGrams))
                burst(if (k == FoodKind.ALGAE_WAFER || k == FoodKind.PRAWN || k == FoodKind.KRILL) BurstKind.PLOP else BurstKind.SPLASH, FEED_SPOT)
                return true
            }
            // Tank Health "Feed the hungry ones" (core HungryFeed): each hungry animal's favourite food dropped above it
            "feedHungry" -> { send(Command.FeedHungry); return true }
            // Tank Health "Care for corals and inverts" (core CoralCare): doses and feeds what they need now
            // in My tank the supplies cost [num] sand dollars (docs/ECONOMY.md)
            "reefCare" -> { if (mine && !ReefMyTank.spendCare(num.toInt())) return false; send(Command.ReefCare); return true }
            "knock" -> { val t = com.reeflive.app.render.Camera.TANK; send(Command.TapGlass(com.reeflive.core.geom.Vec3(t[0] / 2.0, 25.0, t[2].toDouble()))); burst(BurstKind.TAP, floatArrayOf(t[0] / 2f, 25f, t[2] - 1.7f)); return true }
            "cleaning" -> { live.renderer?.setCleaning(num > 0); return true }
            // scoop a dead animal out with the fish net (core FishNet): the keeper does this in the real tank too
            "netOut" -> { send(Command.NetOut(arg ?: return false)); return true }
            // clownfish and blue tang breeding and the nursery (core Brood): "Spawn now" is the only sandbox-only one
            "breed" -> {
                val a = arg ?: return false
                if (a.startsWith("SPAWN_NOW") && !sandbox) return false
                send(Command.Breed(a)); return true
            }
        }
        if (!sandbox) return false
        when (name) {
            "lights" -> {
                send(Command.Lights(when (arg) { "on" -> true; "off" -> false; else -> null }))
                if (arg == "on") burst(BurstKind.LIGHT_MOTES, LIGHT_SPOT)
            }
            "pumps" -> {
                val on = num > 0
                send(Command.Powerheads(on)); extras["pump_left"] = if (on) 1.0 else 0.0; extras["pump_right"] = extras["pump_left"]!!
                burst(if (on) BurstKind.PUMP_ON else BurstKind.PUMP_OFF, PUMP_LEFT); burst(if (on) BurstKind.PUMP_ON else BurstKind.PUMP_OFF, PUMP_RIGHT)
            }
            "heater" -> when (arg) {
                "break" -> { send(Command.Heater(false)); burst(BurstKind.SPARKS, HEATER_SPOT) }
                "fix" -> { send(Command.Heater(true)); burst(BurstKind.HEAT, HEATER_SPOT) }
                "stuck" -> { send(Command.Heater(true, stuckOn = true)); burst(BurstKind.HEAT, HEATER_SPOT) }
                "set" -> send(Command.Heater(true, setC = num))
            }
            "pumpFailTonight" -> {
                val t = s.simTimeSec
                var at = floor(t / 86400) * 86400 + 23 * 3600
                if (at <= t) at += 86400
                send(Command.PowerheadFail(at, 6 * 3600.0))
            }
            "dose" -> { send(Command.Dose(arg ?: return false, num)); burst(BurstKind.DYE, DOSE_SPOT, DOSE_TINT[arg] ?: WHITE) }
            "waterChange" -> { send(Command.WaterChange(num)); burst(BurstKind.WATER_CHANGE, floatArrayOf(40f, 20f, 25f)) }
            "maintain" -> {
                val t = MaintenanceTask.valueOf(arg ?: return false)
                send(Command.Maintain(t))
                burst(when (t) { MaintenanceTask.CHANGE_SOCK -> BurstKind.SUCTION; MaintenanceTask.REPLACE_CARBON -> BurstKind.CLARITY; MaintenanceTask.HARVEST_MACRO -> BurstKind.SPARKLE; MaintenanceTask.EMPTY_SKIMMER -> BurstKind.MICROBUBBLES; MaintenanceTask.TOP_OFF -> BurstKind.FRESH; MaintenanceTask.RINSE_FILTER -> BurstKind.WATER_CHANGE; MaintenanceTask.SIPHON_SAND -> BurstKind.SUCTION }, if (t == MaintenanceTask.SIPHON_SAND) SAND_SPOT else SUMP_SPOT)
            }
            "acclimate" -> send(Command.SetAcclimation(null))
            "rearrangeRock" -> { send(Command.RearrangeRock); burst(BurstKind.SILT, ROCK_SPOT, amount = 1.5f) }
            "predation" -> { predation = num > 0; send(Command.SetPredation(predation)) }
            "add" -> {
                val e = Stocking.CATALOG.firstOrNull { it.speciesId == arg } ?: return false
                val count = num.toInt().coerceAtLeast(1)
                if (count == 1) send(Command.AddOrganism(e.speciesId, if (e.group == StockGroup.CORALS) 0.0 else e.typicalCm * StockSize.JUVENILE.fraction, ""))
                else send(Command.AddMany(e.speciesId, count))
                burst(BurstKind.BUBBLES, RELEASE_SPOT)
            }
            "bag" -> {
                // arg = "speciesId:SIZE:x" (tank cm along the front glass), num = count: a shop bag floated there
                val parts = (arg ?: return false).split(":")
                val size = runCatching { StockSize.valueOf(parts.getOrElse(1) { "JUVENILE" }) }.getOrDefault(StockSize.JUVENILE)
                val x = parts.getOrNull(2)?.toDoubleOrNull() ?: 60.0
                send(Command.FloatBag(parts[0], num.toInt().coerceAtLeast(1), x, size = size))
            }
            "hurryBags" -> send(Command.HurryBags(num > 0))
            // My tank: the coral out of its bag goes on offered spot [num] (−1: the best one)
            "placeCoral" -> send(Command.PlaceCoral(num.toInt()))
            "addSized" -> {
                // arg = "speciesId:SIZE", num = count
                val (id, size) = (arg ?: return false).split(":").let { it[0] to StockSize.valueOf(it.getOrElse(1) { "JUVENILE" }) }
                send(Command.AddMany(id, num.toInt().coerceAtLeast(1), size = size))
                burst(BurstKind.BUBBLES, RELEASE_SPOT)
            }
            "remove" -> send(Command.RemoveOrganism(arg ?: return false))
            "setSize" -> send(Command.SetSize(arg ?: return false, num))
            "aiptasia" -> if (num >= 0) send(Command.AddAiptasia(1)) else { send(Command.RemoveAiptasia(1)); burst(BurstKind.ZAP, ROCK_SPOT) }
            "pestControl" -> {
                val c = PestController.valueOf(arg ?: return false)
                send(Command.AddPestControl(c, if (c == PestController.BERGHIA) 3 else 1)); burst(BurstKind.BUBBLES, RELEASE_SPOT)
            }
            "treatAiptasia" -> return treatAiptasia(AiptasiaTreatment.valueOf(arg ?: "AIPTASIA_X"))
            "spareShells" -> { send(Command.AddSpareShells(1)); burst(BurstKind.SHELLS, RELEASE_SPOT) }
            "treatment" -> {
                val k = TreatmentKind.valueOf(arg ?: return false)
                if (num > 0) { send(Command.StartTreatment(k)); burst(BurstKind.HAZE, DOSE_SPOT, floatArrayOf(0.37f, 0.84f, 1f)) } else send(Command.StopTreatment(k))
            }
            "infect" -> send(Command.Infect(null, Disease.valueOf(arg ?: return false)))
            "micro" -> {
                val a = MicroAction.valueOf(arg ?: return false)
                send(Command.Micro(a))
                burst(if (a in POD_ACTIONS) BurstKind.PODS else BurstKind.HAZE, FEED_SPOT)
            }
            "bacteria" -> { send(Command.Bacteria(BacteriaAction.valueOf(arg ?: return false))); burst(BurstKind.HAZE, DOSE_SPOT, floatArrayOf(0.9f, 0.95f, 1f), 0.6f) }
            "scenario" -> {
                val sc = runCatching { Scenario.valueOf(arg ?: "") }.getOrNull() ?: return false
                for (step in sc.steps()) live.scenarioQueue += (s.simTimeSec + step.atSimSec) to step.command
                live.scenarioQueue.sortBy { it.first }
            }
            "preset" -> runPreset(SandboxPreset.valueOf(arg ?: return false), s)
            "heatWave" -> {
                val t = s.water.s.tempC + 3.0
                send(Command.RampParam("temp", t, 24 * 60.0, hold = true)); burst(BurstKind.HEAT, HEATER_SPOT)
            }
            "reset" -> reset()
            else -> return false
        }
        return true
    }

    private fun runPreset(p: SandboxPreset, s: Simulation) {
        val w = s.water.s
        when (p) {
            SandboxPreset.HEATER_STUCK -> act("heater", "stuck", 0.0)
            SandboxPreset.POWER_OUTAGE -> act("scenario", Scenario.POWER_OUTAGE.name, 0.0)
            SandboxPreset.OVERFEEDING -> act("scenario", Scenario.OVERFEEDING.name, 0.0)
            SandboxPreset.MISSED_WATER_CHANGES -> {
                send(Command.SetParam("no3", w.no3 + 40.0))
                send(Command.SetParam("po4", w.po4 + 0.3))
                send(Command.SetParam("alk", (w.alk - 1.5).coerceAtLeast(0.0)))
                send(Command.SetParam("ca", (w.ca - 40.0).coerceAtLeast(100.0)))
                send(Command.SetParam("mg", (w.mg - 80.0).coerceAtLeast(600.0)))
                send(Command.SetParam("algae", (w.algaeHair + 0.15).coerceAtMost(1.0)))
            }
            SandboxPreset.SALINITY_CRASH -> { send(Command.RampParam("sal", 1.012, 30.0, hold = false)); burst(BurstKind.FRESH, DOSE_SPOT) }
            SandboxPreset.COPPER_OVERDOSE -> { send(Command.SetParam("copper", 0.5)); burst(BurstKind.DYE, DOSE_SPOT, floatArrayOf(0.85f, 0.47f, 0.23f)) }
            SandboxPreset.ROCKSLIDE -> act("scenario", Scenario.ROCKSLIDE.name, 0.0)
        }
    }

    private fun reset() {
        val f = fork ?: return
        lightPct = 100.0; predation = true; extras.clear(); extras.putAll(DEFAULT_EXTRAS)
        live.scenarioQueue.clear()
        send(Command.Photoperiod(DEFAULT_EXTRAS.getValue("photoperiod")))
        for ((id, v) in DEFAULT_EXTRAS) when {
            id.startsWith("pump_") -> send(Command.SetParam(PUMP_IDS.getValue(id), v))
            id.startsWith("spectrum_") -> send(Command.SetParam("spectrum." + id.removePrefix("spectrum_"), v))
        }
        for ((id, v) in TOGGLE_DEFAULTS) SandboxParams.byId(id)?.simId?.let { send(Command.SetParam(it, v)) }
        send(Command.ResetToReality(f.first.copy(), f.second))
        send(Command.Lights(on = null, overrideIntensity = 1.0))
        send(Command.Heater(working = true, stuckOn = false))
        send(Command.SetPredation(true))
    }

    /**
     * A sandbox slider: [display] in the row's display units, applied per [controlMode] (SET / STEP / RAMP / SCHEDULE,
     * research 06 §4.2), exactly as LiveTankViewModel.applyParam does.
     */
    fun applyParam(rowId: String, display: Double, controlMode: String): Boolean {
        if (!sandbox) return false
        val row = SandboxParams.byId(rowId) ?: return false
        val simId = row.simId ?: return false
        val s = sim ?: return false
        val value = display / row.scale
        val current = (row.read(reading(s)) ?: row.fallback) / row.scale
        if (rowId in DEFAULT_EXTRAS) extras[rowId] = if (rowId.startsWith("pump_") || rowId in TOGGLE_DEFAULTS) (if (value >= 0.5) 1.0 else 0.0) else value
        if (rowId == "light") lightPct = value * 100.0
        if (rowId == "photoperiod") { send(Command.Photoperiod(value)); return true }
        val mode = runCatching { SandboxControlMode.valueOf(controlMode) }.getOrDefault(SandboxControlMode.SET)
        // on/off rows and the light/flow multipliers act at once
        val m = if (row.step == 1.0 && row.max == 1.0 || rowId == "light" || rowId == "flow" || rowId.startsWith("spectrum_")) SandboxControlMode.SET else mode
        when (m) {
            SandboxControlMode.SET -> send(Command.SetParam(simId, value))
            SandboxControlMode.STEP -> send(Command.RampParam(simId, value, 0.0, hold = true))
            SandboxControlMode.RAMP -> send(Command.RampParam(simId, value, m.rampMin, hold = true))
            SandboxControlMode.SCHEDULE -> {
                send(Command.RampParam(simId, value, 0.0, hold = true))
                send(Command.Schedule(s.simTimeSec + m.rampMin * 60, Command.RampParam(simId, current, 0.0, hold = false)))
            }
        }
        sliderBurst(rowId, value, current)
        return true
    }

    private fun sliderBurst(rowId: String, value: Double, current: Double) {
        val up = value > current
        when {
            rowId == "temp" -> burst(if (up) BurstKind.HEAT else BurstKind.COOL, HEATER_SPOT)
            rowId == "sal" -> burst(if (up) BurstKind.SALT else BurstKind.FRESH, DOSE_SPOT)
            rowId.startsWith("pump_") -> burst(if (value >= 0.5) BurstKind.PUMP_ON else BurstKind.PUMP_OFF, if (rowId == "pump_left") PUMP_LEFT else PUMP_RIGHT)
            rowId == "eq_skimmer" && value >= 0.5 -> burst(BurstKind.MICROBUBBLES, DOSE_SPOT)
            rowId == "eq_uv" && value >= 0.5 -> burst(BurstKind.UV_GLOW, DOSE_SPOT)
            rowId == "eq_carbon" && value >= 0.5 -> burst(BurstKind.CLARITY, DOSE_SPOT)
            rowId in DOSE_TINT -> burst(BurstKind.DYE, DOSE_SPOT, DOSE_TINT.getValue(rowId), 0.7f)
        }
    }

    /** A fingertip tap on the tank (as LiveTankScreen.onTouchTank): the sim's TouchTank plus a ripple or a sand puff. */
    internal fun touch(p: com.reeflive.core.geom.Vec3, surface: com.reeflive.core.sim.TouchSurface) {
        send(Command.TouchTank(p, surface))
        val glass = surface == com.reeflive.core.sim.TouchSurface.GLASS
        val kind = when (surface) {
            com.reeflive.core.sim.TouchSurface.SAND -> BurstKind.SAND_PUFF
            com.reeflive.core.sim.TouchSurface.SURFACE -> BurstKind.SURFACE_RIPPLE
            else -> BurstKind.GLASS_RIPPLE
        }
        burst(kind, floatArrayOf(p.x.toFloat(), p.y.toFloat(), p.z.toFloat()), amount = if (glass) 1f else 0.7f)
    }

    internal fun scrape(s: GlassStroke) {
        send(Command.ScrapeGlass(GlassPane.FRONT, s.x0.toDouble(), s.y0.toDouble(), s.x1.toDouble(), s.y1.toDouble(),
            GlassStroke.FLIPPER_W.toDouble(), GlassStroke.FLIPPER_H.toDouble()))
    }

    // ------------------------------------------------------------------ Fix tank (Android LiveTankScreen.playFix)

    /** What the tank check (core TankDoctor) says the tank needs now, minus fixes already playing: JSON for the card. */
    fun fixes(): String {
        val s = sim ?: return "null"
        val snap = s.latest
        fixList = TankDoctor.diagnose(snap.water, snap.care).filter { it.kind !in fixQueued }
        return JSON.stringify(obj { o ->
            o.busy = fixQueued.size
            o.list = fixList.map { f -> obj { it.kind = f.kind.name; it.title = f.title; it.why = f.why; it.urgent = f.urgent } }.toTypedArray()
        })
    }

    /**
     * Plays fix [index] of the last [fixes] list (-1 = all of them, one after another): the 3D animation, then its
     * commands when it reaches the water. The page hears "onFixed" (kind) as each lands and "onFixedAll" at the end.
     */
    fun fix(index: Int): Boolean {
        val l = if (index < 0) fixList else listOfNotNull(fixList.getOrNull(index))
        for (f in l) playFix(f)
        return l.isNotEmpty()
    }

    private fun playFix(f: CareFix) {
        if (!fixQueued.add(f.kind)) return
        val act = when (f.kind) {
            TankDoctor.Kind.EMPTY_SKIMMER -> CareAct.EMPTY_CUP
            TankDoctor.Kind.SWAP_SOCK -> CareAct.SWAP_SOCK
            TankDoctor.Kind.FRESH_CARBON -> CareAct.SWAP_CARBON
            TankDoctor.Kind.DOSE_MG, TankDoctor.Kind.DOSE_CA, TankDoctor.Kind.DOSE_ALK -> CareAct.DOSE
            TankDoctor.Kind.TOP_OFF -> CareAct.TOP_OFF
            TankDoctor.Kind.WATER_CHANGE, TankDoctor.Kind.RAISE_SALT -> CareAct.WATER_CHANGE
            TankDoctor.Kind.LOWER_SALT -> CareAct.RO_SWAP
            TankDoctor.Kind.HEATER, TankDoctor.Kind.PUMPS, TankDoctor.Kind.FEED_CORALS -> CareAct.PAUSE
        }
        val tint = when (f.kind) {
            TankDoctor.Kind.DOSE_ALK -> 0xB08CFF
            TankDoctor.Kind.DOSE_CA -> 0xE8F2FF
            TankDoctor.Kind.DOSE_MG -> 0x8FE66B
            TankDoctor.Kind.RAISE_SALT -> 0xD6F2FF
            else -> 0xBFE6FF
        }
        care.play(CareJob(act, tint, f.amount.toFloat(), onImpact = {
            careCommands(f.commands)
            when (f.kind) {
                TankDoctor.Kind.HEATER -> burst(BurstKind.HEAT, HEATER_SPOT)
                TankDoctor.Kind.PUMPS -> { burst(BurstKind.PUMP_ON, PUMP_LEFT); burst(BurstKind.PUMP_ON, PUMP_RIGHT) }
                TankDoctor.Kind.FEED_CORALS -> burst(BurstKind.SPLASH, FEED_SPOT)
                else -> Unit
            }
            live.emit("onFixed", f.kind.name)
        }, onDone = {
            fixQueued.remove(f.kind)
            if (fixQueued.isEmpty()) live.emit("onFixedAll", null)
        }))
    }

    // ------------------------------------------------------------------ touch controls (Android LiveTankScreen onSpot / onAim)

    /**
     * Food from the shelf aimed at a tapped spot (world cm, the renderer's `onAim`): it goes in right there, floating
     * food on the surface above it, the rest let go just above it (core Simulation.targetRelease).
     */
    fun feedAt(kind: String, x: Double, y: Double, z: Double): Boolean {
        val s = sim ?: return false
        val k = runCatching { FoodKind.valueOf(kind) }.getOrDefault(FoodKind.FLAKE)
        val aim = com.reeflive.core.geom.Vec3(x, y, z)
        send(Command.Feed(k, k.typicalDoseGrams, aim, target = true))
        val p = s.targetRelease(k, aim)
        burst(if (k == FoodKind.ALGAE_WAFER || k == FoodKind.PRAWN || k == FoodKind.KRILL) BurstKind.PLOP else BurstKind.SPLASH, floatArrayOf(p.x.toFloat(), p.y.toFloat(), p.z.toFloat()))
        return true
    }

    private fun spotOf(name: String): TankSpot? = runCatching { TankSpot.valueOf(name) }.getOrNull()

    private fun spotMenuOf(spot: TankSpot): SpotMenu? {
        val snap = sim?.latest ?: return null
        return TankSpots.menu(spot, snap.water, snap.care, snap.lightIntensity > 0.05f, sandbox)
    }

    /** A tapped piece of gear's pop-out menu (core TankSpots) as JSON: title, status, warn, glyph, options [{id, label, glyph, urgent}]. */
    fun spotMenu(spot: String): String {
        val m = spotOf(spot)?.let { spotMenuOf(it) } ?: return "null"
        return JSON.stringify(obj { o ->
            o.spot = m.spot.name; o.title = m.title; o.status = m.status; o.warn = m.warn; o.glyph = m.spot.glyph
            o.options = m.options.map { x -> obj { it.id = x.id; it.label = x.label; it.glyph = x.glyph; it.urgent = x.urgent } }.toTypedArray()
        })
    }

    /** Runs option [id] of [spot]'s menu: a fix plays in the 3D tank like Fix tank's; a switch goes straight in. */
    fun spotAct(spot: String, id: String): Boolean {
        val o = spotOf(spot)?.let { spotMenuOf(it) }?.options?.firstOrNull { it.id == id } ?: return false
        val f = o.fix
        if (f != null) playFix(f) else careCommands(o.commands)
        return true
    }

    /** Upkeep, doses and water changes work in both modes (the owner's own care); gear switches are sandbox-only. */
    private fun careCommands(cmds: List<Command>) {
        for (c in cmds) when (c) {
            is Command.Heater -> if (sandbox) send(c)
            is Command.Powerheads -> if (sandbox) { send(c); extras["pump_left"] = if (c.on) 1.0 else 0.0; extras["pump_right"] = extras["pump_left"]!! }
            is Command.SetParam -> if (sandbox) { send(c); if (c.paramId == "skimmer") extras["eq_skimmer"] = c.value }
            is Command.Maintain -> { send(c); if (sandbox && c.task == MaintenanceTask.CHANGE_SOCK) extras["eq_sock"] = 1.0; if (sandbox && c.task == MaintenanceTask.REPLACE_CARBON) extras["eq_carbon"] = 1.0 }
            else -> send(c)
        }
    }

    private fun burst(kind: BurstKind, at: FloatArray, tint: FloatArray = WHITE, amount: Float = 1f) =
        bursts.emit(TankBurst(kind, at[0], at[1], at[2], tint[0], tint[1], tint[2], amount))

    // ------------------------------------------------------------------ readings for the page

    private fun reading(s: Simulation): TankReading {
        val snap = s.latest
        return TankReading(
            snap.water, lightPct = lightPct, flowPct = snap.flowStrength * 100.0,
            photoperiodH = extras["photoperiod"],
            pumps = extras.filterKeys { it.startsWith("pump_") },
            gains = extras.filterKeys { it.startsWith("spectrum_") }.mapKeys { it.key.removePrefix("spectrum_") },
            toggles = extras.filterKeys { it.startsWith("eq_") || it.startsWith("sc_") },
        )
    }

    /** Every sandbox row's current display value, zone status and caption; plus mode, happiness and water strip. */
    fun panel(): String {
        val s = sim ?: return "null"
        val r = reading(s)
        val snap = s.latest
        val readouts = waterReadouts(snap.water, lightPct, snap.flowStrength.toDouble())
        return JSON.stringify(obj { o ->
            o.sandbox = sandbox; o.mine = mine; o.predation = predation; o.speed = s.latest.speed
            o.values = obj { v ->
                for (p in SandboxParams.all) {
                    val x = p.valueIn(r)
                    v[p.id] = obj { it.v = x; it.status = p.zones?.status(x)?.name ?: "UNKNOWN"; it.caption = p.caption?.invoke(r) }
                }
            }
            o.readouts = readouts.map { w -> obj { it.id = w.id; it.label = w.label; it.text = w.valueText; it.status = w.status.name } }.toTypedArray()
            o.happy = tankHappiness(snap.stats, readouts.map { it.status })
            o.aiptasia = s.aiptasiaCount
        })
    }

    /** The [n]th "Did you know?" this browser has shown: {title, text} (core ReefFacts). */
    fun fact(n: Double): String = com.reeflive.core.live.ReefFacts.nth(n.toLong()).let { f ->
        JSON.stringify(obj { it.title = f.kind.title; it.text = f.text })
    }

    /**
     * Tank Health's corals (core CoralHealth / CoralCare): {words, price, corals: [{name, health, pale, need}]}, where
     * [words] is what the care button would give now ("" = nothing needed) and [need] a colony's main trouble.
     */
    fun corals(): String {
        val s = sim ?: return "{}"
        val plan = CoralCare.plan(s.latest)
        val list = s.corals.filter { it.kind != com.reeflive.core.sim.CoralKind.AIPTASIA && it.tissue > CoralHealth.DEAD_TISSUE }.map { c ->
            obj {
                it.name = c.nickname.ifBlank { c.species.common }
                it.health = CoralHealth.health(c)
                it.pale = c.bleach > 0.3
                it.need = if (c.need >= 0) com.reeflive.core.sim.CoralNeed.entries[c.need].words else null
            }
        }
        return JSON.stringify(obj { it.words = plan.words; it.price = plan.price; it.corals = list.toTypedArray() })
    }

    /** Tank Health's hunger list (core HungryFeed): [{id, name, hunger, fish, hungry, food}], fish first. */
    fun hunger(): String {
        val s = sim ?: return "[]"
        val sp = live.species ?: return "[]"
        return JSON.stringify(HungryFeed.rows(s.latest.stats) { id -> sp.takeIf { it.has(id) }?.get(id) }.map { r ->
            obj { it.id = r.id; it.name = r.name; it.hunger = r.hunger; it.fish = r.isFish; it.hungry = r.hungry; it.food = r.food?.label }
        }.toTypedArray())
    }

    /** Living fish and inverts for the follow list / tour, with their card data. */
    fun animals(): String {
        val s = sim ?: return "[]"
        return JSON.stringify(s.latest.stats.filter { it.alive }.map { st -> card(st) }.toTypedArray())
    }

    fun animal(id: String): String {
        val st = sim?.latest?.stats?.firstOrNull { it.id == id } ?: return "null"
        return JSON.stringify(card(st))
    }

    private fun card(st: OrganismStats): dynamic {
        val c = st.toFishCardState()
        return obj {
            it.id = c.id; it.name = c.name; it.species = c.speciesName; it.speciesId = st.speciesId
            it.health = c.health; it.stress = c.stress; it.hunger = c.hunger; it.mood = c.mood; it.behavior = c.behavior
            it.alive = c.alive; it.stage = c.lifeStage; it.cm = st.lengthCm; it.caption = st.caption
            // "Thinking…" lines (core ai/Thoughts.kt), each held ≥ 1.5 s like the Android card (ThoughtHold)
            val held = holds.getOrPut(st.id) { ThoughtHold() }.update(st.id, st.thought, now().toLong())
            it.thinking = held.now; it.thinkNext = held.next; it.feeling = held.feeling; it.moodWord = st.thought.mood
            it.disease = st.disease?.let { d -> DISEASE_LABELS[d] ?: d.name }
            it.symptoms = c.symptoms.toTypedArray()
            // the parents' "Family" panel (core Brood): eggs, larvae, the nursery and its buttons
            it.family = sim?.latest?.brood?.panelFor(st.speciesId)?.let { p ->
                obj { f ->
                    f.title = p.title; f.lines = p.lines.toTypedArray()
                    f.actions = p.actions.filter { a -> !a.sandboxOnly || sandbox }.map { a ->
                        obj { o -> o.id = a.id; o.label = a.label; o.enabled = a.enabled; o.note = a.note }
                    }.toTypedArray()
                }
            }
            it.relations = c.relationships.take(6).map { r ->
                obj { o -> o.name = r.otherName; o.species = r.otherSpecies; o.label = r.labelOverride ?: r.kind.label; o.affinity = r.affinity; o.note = r.note }
            }.toTypedArray()
        }
    }

    private val holds = HashMap<String, ThoughtHold>()
    private val bubblePicker = ThoughtBubblePicker()

    /**
     * More → "Thought bubbles" (as on Android, ThoughtBubbleLayer): at most three animals with a speech bubble, as
     * JSON [{id, text, x, y, a}] with x, y in canvas pixels just above the animal. Call every animation frame while
     * on; [on] false clears the picker.
     */
    fun bubbles(on: Boolean): String {
        if (!on) { bubblePicker.clear(); return "[]" }
        val s = sim ?: return "[]"
        val r = live.renderer ?: return "[]"
        val proj = r.projection ?: com.reeflive.app.render.TankProjection().also { r.projection = it }
        val snap = s.latest
        val canvas = kotlinx.browser.document.getElementById("tank") as? org.w3c.dom.HTMLCanvasElement ?: return "[]"
        val w = canvas.width.toFloat()
        val h = canvas.height.toFloat()
        val anchor = FloatArray(3)
        val pt = FloatArray(3)
        val list = bubblePicker.update(snap.stats, now().toLong()) { id ->
            if (ThoughtBubblePicker.anchorOf(snap, id, anchor) && proj.project(anchor[0], anchor[1], anchor[2], pt) &&
                pt[0] in 0f..w && pt[1] in 0f..h
            ) floatArrayOf(pt[0], pt[1], pt[2]) else null
        }
        return JSON.stringify(list.map { b -> obj { o -> o.id = b.id; o.text = b.text; o.x = b.x; o.y = b.y; o.a = b.alpha } }.toTypedArray())
    }

    /** "Tank happy" 0..100 (Hermie.kt tankHappiness): health and calm of the animals minus water and death penalties. */
    private fun tankHappiness(stats: List<OrganismStats>, statuses: List<Status>): Int {
        val alive = stats.filter { it.alive }
        val animals = if (alive.isEmpty()) 1.0 else {
            val health = alive.map { it.health.toDouble().coerceIn(0.0, 1.0) }.average()
            val calm = 1.0 - alive.map { it.stress.toDouble().coerceIn(0.0, 1.0) }.average()
            0.6 * health + 0.4 * calm
        }
        val dead = stats.count { !it.alive }
        val penalty = statuses.sumOf { when (it) { Status.DANGER -> 10.0; Status.CAUTION -> 3.0; else -> 0.0 } } + dead * 6.0
        return (animals * 100.0 - penalty).coerceIn(0.0, 100.0).toInt()
    }

    /** Ambience bed volumes (TankSoundMix) and the cues for events since the last call. */
    private var lastTick = -1L
    fun sound(volume: Double): String {
        val s = sim ?: return "null"
        val snap = s.latest
        val pumpShare = extras.filterKeys { it.startsWith("pump_") }.values.average()
        val amb = TankSoundMix.ambience(snap.flowStrength, pumpShare.toFloat(), snap.flowStrength > 0.02f || snap.lightIntensity > 0f, snap.lightIntensity, volume.toFloat())
        val cues = if (snap.tick != lastTick) snap.events.mapNotNull { TankSoundMix.cueFor(it)?.name }.distinct() else emptyList()
        lastTick = snap.tick
        return JSON.stringify(obj { it.flow = amb.flow; it.hum = amb.hum; it.hob = amb.hob; it.bubbles = amb.bubbles; it.cues = cues.toTypedArray() })
    }

    // ------------------------------------------------------------------ microscope

    /** One field of view: sprite counts per group (MicroView.sample), phyto haze and cells, and the bacteria layer. */
    fun micro(scene: String, zoom: String, seed: Double): String {
        val s = sim ?: return "null"
        val snap = s.latest
        val m = snap.microfauna
        val sc = runCatching { MicroScene.valueOf(scene) }.getOrDefault(MicroScene.ROCK)
        val z = runCatching { MicroZoom.valueOf(zoom) }.getOrDefault(MicroZoom.X10)
        val counts = MicroView.sample(m, sc, z, seed.toLong())
        val b = snap.bacteria
        return JSON.stringify(obj { o ->
            o.groups = counts.filter { it.sprites > 0 || it.nauplii > 0 }.map { c ->
                obj { it.id = c.group.name; it.label = c.group.plural; it.sizeMm = c.group.sizeMm; it.sprites = c.sprites; it.nauplii = c.nauplii; it.expected = c.expected }
            }.toTypedArray()
            o.edgeMm = z.edgeMm
            o.haze = MicroView.phytoHaze(m, z); o.cells = MicroView.phytoCells(m, z); o.night = m.night
            o.census = MicroGroup.entries.filter { it != MicroGroup.PHYTO && m.present[it.ordinal] }.map { g ->
                obj { it.label = g.plural; it.total = m.total[g.ordinal]; it.trend = m.trendPerDay[g.ordinal] }
            }.toTypedArray()
            o.phyto = m.phytoPerMl
            o.bacteria = obj { bo ->
                bo.guilds = b.guilds.map { g -> obj { it.label = g.guild.label; it.short = g.guild.shortLabel; it.good = g.guild.good; it.level = g.level; it.amount = g.amount; it.effect = g.effect; it.trend = g.trendPerDay } }.toTypedArray()
                bo.cloudy = b.cloudy; bo.h2s = b.h2sMgL; bo.uv = b.uvOn; bo.free = b.freeCellsPerMl; bo.pathogen = b.pathogenPressure
            }
        })
    }

    // ------------------------------------------------------------------ time-lapse

    /**
     * Runs a time-lapse on a fork of the tank on screen (TimelapseRun: the abstract stepper, a few seconds for four
     * weeks) and returns the per-species verdicts and the parameter / health curves. The live tank is not touched.
     */
    fun timelapse(param: String, target: Double, ramp: Boolean, rampHours: Double, hours: Double, label: String): String {
        val s = sim ?: return "null"
        val p = LAPSE_PARAMS.firstOrNull { it.id == param } ?: return "null"
        val row = SandboxParams.byId(p.row)!!
        val name = label.ifBlank { "${row.label} → ${target.asFixed(row.decimals)}" }
        val change = if (ramp) TimelapseChange.Ramp(param, target, rampHours.coerceAtMost(hours)) else TimelapseChange.Step(param, target)
        val spec = TimelapseSpec(listOf(change), hours, label = name)
        val res = TimelapseRun(TimelapseFork.of(s), spec).run()
        val samples = res.recording.samples
        return JSON.stringify(obj { o ->
            o.label = name; o.headline = res.report.headline(); o.allOk = res.report.allOk
            o.hours = samples.map { it.hour }.toTypedArray()
            o.param = samples.map { it.water.get(param) ?: 0.0 }.toTypedArray()
            o.species = res.report.species.map { sp ->
                obj {
                    it.name = sp.common; it.coral = sp.isCoral; it.verdict = sp.verdict.label; it.severity = sp.verdict.severity
                    it.count = sp.individuals.size; it.died = sp.diedCount; it.onset = sp.firstOnsetHour; it.cause = sp.cause?.label
                    it.health = samples.map { smp ->
                        smp.organisms.filter { o2 -> o2.speciesId == sp.speciesId }.let { l -> if (l.isEmpty()) 0.0 else l.map { o2 -> o2.health.toDouble() }.average() }
                    }.toTypedArray()
                }
            }.sortedByDescending { it.severity as Int }.toTypedArray()
        })
    }

    private fun Double.asFixed(d: Int): String = asDynamic().toFixed(d) as String

    // ------------------------------------------------------------------ tables

    private class LapseParam(val id: String, val row: String, val lo: Double?, val hi: Double?)
    private class LapsePreset(val label: String, val param: String, val target: Double, val ramp: Boolean, val rampHours: Double, val hours: Double)

    /** The time-lapse setup of the Android TimelapseSheet (LapseParams / lapsePresets); keep in step with it. */
    private val LAPSE_PARAMS = listOf(
        LapseParam("sg", "sal", 1.010, null), LapseParam("temp", "temp", 18.0, 36.0), LapseParam("no3", "no3", null, 200.0),
        LapseParam("po4", "po4", null, 3.0), LapseParam("alk", "alk", 2.0, 20.0), LapseParam("ca", "ca", null, null),
        LapseParam("mg", "mg", null, null), LapseParam("ph", "ph", null, null), LapseParam("nh3", "nh3", null, 2.0),
        LapseParam("do", "do", null, null),
    )
    private val LAPSE_PRESETS = listOf(
        LapsePreset("Salinity 1.035", "sg", 1.035, false, 0.0, 72.0),
        LapsePreset("Salinity creep to 1.032", "sg", 1.032, true, 168.0, 336.0),
        LapsePreset("Heat wave 31°C", "temp", 31.0, true, 24.0, 168.0),
        LapsePreset("Nitrate 80", "no3", 80.0, false, 0.0, 336.0),
        LapsePreset("Alk crash to 5", "alk", 5.0, false, 0.0, 168.0),
        LapsePreset("Ammonia 0.5", "nh3", 0.5, false, 0.0, 72.0),
    )

    private val PUMP_IDS = mapOf("pump_left" to "pump.PH-B", "pump_right" to "pump.PH-A")
    /** LiveTankViewModel.TOGGLE_DEFAULTS / DEFAULT_EXTRAS. */
    private val TOGGLE_DEFAULTS: Map<String, Double> = mapOf(
        "eq_skimmer" to 1.0, "eq_sock" to 0.0, "eq_carbon" to 0.0, "eq_refugium" to 0.0, "eq_uv" to 0.0,
        "sc_asterina_coral" to 0.0, "sc_crab_predation" to 0.0,
    )
    private val DEFAULT_EXTRAS: Map<String, Double> get() = mapOf(
        "photoperiod" to LightSchedule.DEFAULT_PHOTOPERIOD_H,
        "pump_left" to 1.0, "pump_right" to 1.0,
        "spectrum_royal" to 1.0, "spectrum_blue" to 1.0, "spectrum_white" to 1.0, "spectrum_violet" to 1.0,
    ) + TOGGLE_DEFAULTS

    private val DISEASE_LABELS = mapOf(
        Disease.ICH to "Ich (white spot)", Disease.VELVET to "Velvet", Disease.HLLE to "Head and lateral line erosion",
        Disease.BACTERIAL to "Bacterial infection", Disease.BROOKLYNELLA to "Brooklynella", Disease.URONEMA to "Uronema",
        Disease.FLUKES to "Flukes",
    )

    private val POD_ACTIONS = setOf(MicroAction.DOSE_TISBE, MicroAction.DOSE_TIGGER, MicroAction.DOSE_TIGRIOPUS, MicroAction.DOSE_AMPHIPODS, MicroAction.DOSE_MYSIDS)

    // ActionFx FxSpots (tank cm)
    private val FEED_SPOT = floatArrayOf(68f, 55.7f, 4.5f)
    private val DOSE_SPOT = floatArrayOf(71f, 55.7f, 3f)
    private val RELEASE_SPOT = floatArrayOf(61f, 55.7f, 20f)
    private val HEATER_SPOT = floatArrayOf(7f, 30f, 4f)
    private val PUMP_LEFT = floatArrayOf(1.5f, 42f, 10f)
    private val PUMP_RIGHT = floatArrayOf(84f, 48f, 1.5f)
    private val LIGHT_SPOT = floatArrayOf(61f, 66f, 16f)
    private val SUMP_SPOT = floatArrayOf(108f, 62f, 3f)
    private val SAND_SPOT = floatArrayOf(58f, 4f, 26f)
    private val ROCK_SPOT = floatArrayOf(72f, 28f, 15f)
    private val WHITE = floatArrayOf(1f, 1f, 1f)
    private val DOSE_TINT = mapOf(
        "alk" to floatArrayOf(0.69f, 0.55f, 1f), "ca" to floatArrayOf(0.91f, 0.95f, 1f), "mg" to floatArrayOf(0.56f, 0.9f, 0.42f),
        "no3" to floatArrayOf(0.61f, 0.48f, 0.29f), "po4" to floatArrayOf(0.61f, 0.48f, 0.29f), "copper" to floatArrayOf(0.85f, 0.47f, 0.23f),
        "k" to floatArrayOf(1f, 0.88f, 0.3f), "sr" to floatArrayOf(1f, 0.88f, 0.3f),
    )

    init { extras.putAll(DEFAULT_EXTRAS) }
}
