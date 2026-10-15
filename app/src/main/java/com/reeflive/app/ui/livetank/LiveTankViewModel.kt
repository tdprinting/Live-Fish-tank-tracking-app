package com.reeflive.app.ui.livetank

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reeflive.app.live.SimMode
import com.reeflive.app.live.SimulationHolder
import com.reeflive.app.render.GlassStroke
import com.reeflive.app.ui.live.SandboxControlMode
import com.reeflive.app.ui.live.SandboxParams
import com.reeflive.app.ui.live.SandboxPreset
import com.reeflive.app.ui.live.TankReading
import com.reeflive.app.ui.nav.LiveFocus
import com.reeflive.core.geom.Vec3
import com.reeflive.core.live.LivePeek
import com.reeflive.core.live.Scenario
import com.reeflive.core.sim.BacteriaAction
import com.reeflive.core.sim.Command
import com.reeflive.core.sim.StockSize
import com.reeflive.core.sim.Disease
import com.reeflive.core.sim.FoodKind
import com.reeflive.core.sim.GlassPane
import com.reeflive.core.sim.MaintenanceTask
import com.reeflive.core.sim.MicroAction
import com.reeflive.core.sim.OrganismStats
import com.reeflive.core.sim.PestController
import com.reeflive.core.sim.SimSnapshot
import com.reeflive.core.sim.TouchSurface
import com.reeflive.core.sim.TreatmentKind
import com.reeflive.core.sim.WaterState
import com.reeflive.core.sim.timelapse.TimelapseSpec
import com.reeflive.core.sim.timelapse.TimelapseState
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Renderer quality tier (ARCHITECTURE §8.7). */
enum class LiveQuality { LOW, MEDIUM, HIGH }

/**
 * State and actions for the Live Tank tab (ARCHITECTURE §5.2). Wraps the app-wide [SimulationHolder]:
 * the renderer reads [snapshots] (full rate), the overlay reads [hud] (4 Hz).
 */
@OptIn(FlowPreview::class)
class LiveTankViewModel(private val holder: SimulationHolder) : ViewModel() {

    val mode: StateFlow<SimMode> = holder.mode
    /** Sandbox speed multiplier (reality is always 1x). */
    val speed: StateFlow<Double> = holder.speed

    private val _selectedOrganismId = MutableStateFlow<String?>(null)
    val selectedOrganismId: StateFlow<String?> = _selectedOrganismId.asStateFlow()

    private val _followId = MutableStateFlow<String?>(null)
    /** Organism the camera follows (set by "View in Live Tank" via LiveFocus, or by [follow]). */
    val followId: StateFlow<String?> = _followId.asStateFlow()

    private val _quality = MutableStateFlow(LiveQuality.MEDIUM)
    val quality: StateFlow<LiveQuality> = _quality.asStateFlow()

    /** Full-rate snapshots for the renderer. */
    val snapshots: StateFlow<SimSnapshot> = holder.snapshots

    /** HUD snapshot throttled to 4 Hz: every collector recomposes the whole live screen, and readouts need no more. */
    val hud: StateFlow<SimSnapshot> = holder.snapshots.sample(HUD_PERIOD_MS)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SimSnapshot.EMPTY)

    /** Stats of the selected organism (null when nothing is selected), at HUD rate. */
    val selectedStats: StateFlow<OrganismStats?> = combine(hud, _selectedOrganismId) { s, id ->
        id?.let { sel -> s.stats.firstOrNull { it.id == sel } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _real = MutableStateFlow<TankReading?>(null)
    /**
     * The real tank as last seen while in Watch mode (reality runs at 1x, so it barely moves during a sandbox session).
     * Drives the "your tank" markers on the sandbox sliders without touching the holder API.
     */
    val real: StateFlow<TankReading?> = _real.asStateFlow()

    private val _lightPct = MutableStateFlow(100.0)
    /** Sandbox light multiplier in % (sim intensity override × 100); 100 = the schedule as is. */
    val lightPct: StateFlow<Double> = _lightPct.asStateFlow()

    private val _extras = MutableStateFlow(DEFAULT_EXTRAS)
    /** Sandbox values the snapshot doesn't carry, by slider id: photoperiod (h), pumps (1 on / 0 off), spectrum gains. */
    val extras: StateFlow<Map<String, Double>> = _extras.asStateFlow()

    private val _predation = MutableStateFlow(true)
    /** Predation in the sandbox (the fork starts with it on). */
    val predation: StateFlow<Boolean> = _predation.asStateFlow()

    /** Time-lapse state: idle / running with progress / done with the recording and report. */
    val timelapse: StateFlow<TimelapseState> = holder.timelapse
    fun startTimelapse(spec: TimelapseSpec) = holder.startTimelapse(spec)
    fun cancelTimelapse() = holder.cancelTimelapse()
    fun clearTimelapse() = holder.clearTimelapse()

    /** Summary for the Dashboard peek (fish count, any sick fish, last event). */
    val peek: StateFlow<LivePeek> = holder.peek

    init {
        viewModelScope.launch {
            holder.snapshots.sample(REAL_SAMPLE_MS).collect { snap ->
                if (holder.mode.value == SimMode.REALITY && snap !== SimSnapshot.EMPTY) {
                    _real.value = TankReading(snap.water, lightPct = 100.0, flowPct = snap.flowStrength * 100.0)
                }
            }
        }
        viewModelScope.launch {
            LiveFocus.followId.collect { pending ->
                if (pending != null) {
                    val id = LiveFocus.consume() ?: return@collect
                    follow(id)
                }
            }
        }
    }

    // ---- lifecycle (call from the Live tab's RESUMED / not-RESUMED transitions) ----
    fun onResume() = holder.onForeground()
    fun onPause() = holder.onBackground()

    override fun onCleared() { holder.onBackground() }

    // ---- view state ----
    fun setMode(m: SimMode) {
        if (m != mode.value) { _lightPct.value = 100.0; _predation.value = true; _extras.value = DEFAULT_EXTRAS }
        holder.setMode(m)
    }
    fun setSpeed(v: Double) = holder.setSpeed(v)
    fun select(id: String?) { _selectedOrganismId.value = id; if (id == null) _followId.value = null }
    fun follow(id: String?) { _followId.value = id; if (id != null) _selectedOrganismId.value = id }
    fun setQuality(q: LiveQuality) { _quality.value = q }

    // ---- actions allowed in both modes ----
    fun feed(kind: FoodKind = FoodKind.FLAKE, grams: Double = 1.0, at: Vec3? = null) = holder.enqueue(Command.Feed(kind, grams, at))
    fun tapGlass(worldPoint: Vec3) = holder.enqueue(Command.TapGlass(worldPoint))
    /** A gentle fingertip tap on the tank at [point] (glass, sand, rock or coral): nearby animals answer (TankTouch). */
    fun touchTank(point: Vec3, surface: TouchSurface) = holder.enqueue(Command.TouchTank(point, surface))
    /** True while a fingertip is held on the tank (between the first [finger] with a point and the one with null). */
    var fingerDown = false
        private set
    /** A fingertip held on the glass or water top (null = lifted): bold fish follow it and nip when it rests. */
    fun finger(point: Vec3?, speedCmS: Float, surface: TouchSurface) {
        fingerDown = point != null
        holder.enqueue(Command.Finger(point, speedCmS.toDouble(), surface))
    }
    /** A flick of the held finger: a short puff of current at [point] along [velocity] (cm/s). */
    fun puff(point: Vec3, velocity: Vec3) = holder.enqueue(Command.WaterPuff(point, velocity))
    /** A tap right on an animal: snails and crabs tuck in, shrimp freeze, a hermit ducks into its shell. */
    fun touchAnimal(id: String) = holder.enqueue(Command.TouchAnimal(id))

    private val _submarine = MutableStateFlow(com.reeflive.app.ui.live.SubUi())
    /** Submarine mode (SubmarineControls): the toy sub is out, seen by the fish, ridden from inside, lamp on. */
    val submarine: StateFlow<com.reeflive.app.ui.live.SubUi> = _submarine.asStateFlow()
    fun setSubmarine(s: com.reeflive.app.ui.live.SubUi) {
        _submarine.value = s
        if (s.on) follow(null)
        holder.enqueue(Command.SubmarineMode(s.on, s.visible, s.light))
    }
    fun steerSubmarine(c: com.reeflive.core.sim.SubControls) = holder.enqueue(Command.SteerSubmarine(c))
    fun shadow(worldPoint: Vec3) = holder.enqueue(Command.Shadow(worldPoint))
    /**
     * A Flipper stroke on the front glass (clean-glass mode). Allowed in Watch too: scraping the glass is what the owner
     * does to the real tank, and Reality's algae are simulated from the logged water anyway, so its glass cover drops.
     */
    fun scrapeGlass(s: GlassStroke) = holder.enqueue(
        Command.ScrapeGlass(
            GlassPane.FRONT, s.x0.toDouble(), s.y0.toDouble(), s.x1.toDouble(), s.y1.toDouble(),
            GlassStroke.FLIPPER_W.toDouble(), GlassStroke.FLIPPER_H.toDouble(),
        ),
    )

    // ---- sandbox actions (ignored in REALITY mode) ----
    private inline fun sandbox(block: () -> Unit) { if (mode.value == SimMode.SANDBOX) block() }

    fun setParam(paramId: String, value: Double) = sandbox { holder.enqueue(Command.SetParam(paramId, value)) }

    /**
     * A sandbox slider change applied per [mode] (research 06 §4.2): set once, step and hold, ramp over a day and
     * hold, or a one-hour spike that returns to [current]. [rowId] keys values the snapshot doesn't carry.
     */
    fun applyParam(rowId: String, simId: String, value: Double, current: Double, mode: SandboxControlMode) = sandbox {
        if (rowId in DEFAULT_EXTRAS) _extras.value = _extras.value + (rowId to (if (rowId.startsWith("pump_") || rowId in TOGGLE_DEFAULTS) (if (value >= 0.5) 1.0 else 0.0) else value))
        if (rowId == "light") _lightPct.value = value * 100.0
        when (mode) {
            SandboxControlMode.SET -> holder.enqueue(Command.SetParam(simId, value))
            SandboxControlMode.STEP -> holder.enqueue(Command.RampParam(simId, value, 0.0, hold = true))
            SandboxControlMode.RAMP -> holder.enqueue(Command.RampParam(simId, value, mode.rampMin, hold = true))
            SandboxControlMode.SCHEDULE -> {
                holder.enqueue(Command.RampParam(simId, value, 0.0, hold = true))
                holder.enqueue(Command.Schedule(hud.value.simTimeSec + mode.rampMin * 60, Command.RampParam(simId, current, 0.0, hold = false)))
            }
        }
    }
    // care props from the shelf also work in Watch, like feeding (Reality re-syncs from the next logged reading)
    fun acclimateAll() = holder.enqueue(Command.SetAcclimation(null))
    fun rearrangeRock() = sandbox { holder.enqueue(Command.RearrangeRock) }
    /** Powerheads fail at the next 23:00 for six hours (the heat × oxygen × night test). */
    fun pumpFailTonight() = sandbox {
        val t = hud.value.simTimeSec
        var at = kotlin.math.floor(t / 86400) * 86400 + 23 * 3600
        if (at <= t) at += 86400
        holder.enqueue(Command.PowerheadFail(at, 6 * 3600.0))
    }
    fun dose(paramId: String, delta: Double) = holder.enqueue(Command.Dose(paramId, delta))
    fun waterChange(fraction: Double, mix: WaterState = WaterState.FRESH_MIX) = holder.enqueue(Command.WaterChange(fraction, mix))
    fun setLights(on: Boolean?, intensity: Double? = null) = sandbox { holder.enqueue(Command.Lights(on, intensity)) }
    fun setHeater(working: Boolean, setC: Double? = null, stuckOn: Boolean = false) = sandbox { holder.enqueue(Command.Heater(working, setC, stuckOn)) }
    fun setPowerheads(on: Boolean) = sandbox { holder.enqueue(Command.Powerheads(on)) }
    fun setPredation(enabled: Boolean) = sandbox { _predation.value = enabled; holder.enqueue(Command.SetPredation(enabled)) }
    /** Light multiplier slider, in % (0..150). */
    fun setLightPct(pct: Double) = sandbox {
        _lightPct.value = pct
        holder.enqueue(Command.SetParam("light", pct / 100.0))
    }
    fun setSize(id: String, sizeCm: Double) = sandbox { holder.enqueue(Command.SetSize(id, sizeCm)) }
    fun addOrganism(speciesId: String, sizeCm: Double, nickname: String, role: String? = null) =
        sandbox { holder.enqueue(Command.AddOrganism(speciesId, sizeCm, nickname, role)) }
    /** Sandbox stocking test: [count] of [speciesId] at once (core caps it; corals settle on free rock or sand). */
    fun addMany(speciesId: String, count: Int, size: StockSize = StockSize.JUVENILE) = sandbox { holder.enqueue(Command.AddMany(speciesId, count, size = size)) }
    /** Drops a shop bag of [count] × [speciesId] onto the water at tank x [x]; they come out after the float. */
    fun floatBag(speciesId: String, count: Int, size: StockSize, x: Float) = sandbox { holder.enqueue(Command.FloatBag(speciesId, count, x.toDouble(), size = size)) }
    /** Fast-forwards the clock while bags float ([on]), or back to the chosen speed. */
    fun hurryBags(on: Boolean) = sandbox { holder.enqueue(Command.HurryBags(on)) }
    fun removeOrganism(id: String) = sandbox {
        holder.enqueue(Command.RemoveOrganism(id))
        if (_selectedOrganismId.value == id) select(null)
    }
    fun runScenario(s: Scenario) = sandbox { holder.runScenario(s) }

    // pests, treatments, maintenance (sandbox only)
    fun addAiptasia(count: Int = 1) = sandbox { holder.enqueue(Command.AddAiptasia(count)) }
    fun removeAiptasia(count: Int = 1) = holder.enqueue(Command.RemoveAiptasia(count))
    fun addPestControl(controller: PestController, count: Int = 1) = sandbox { holder.enqueue(Command.AddPestControl(controller, count)) }
    /** Starts [kind] for its default duration (UV runs until stopped) on every fish. */
    fun startTreatment(kind: TreatmentKind) = sandbox { holder.enqueue(Command.StartTreatment(kind)) }
    fun stopTreatment(kind: TreatmentKind) = sandbox { holder.enqueue(Command.StopTreatment(kind)) }
    /** Infects a random fish with [disease] (test aid). */
    fun infect(disease: Disease) = sandbox { holder.enqueue(Command.Infect(null, disease)) }
    fun maintain(task: MaintenanceTask) {
        holder.enqueue(Command.Maintain(task))
        // a fresh sock or fresh carbon fits one when none was running (WaterSim.maintain): keep the sandbox's
        // equipment switches in step with the tank
        if (mode.value != SimMode.SANDBOX) return
        val row = when (task) {
            MaintenanceTask.CHANGE_SOCK -> "eq_sock"
            MaintenanceTask.REPLACE_CARBON -> "eq_carbon"
            MaintenanceTask.HARVEST_MACRO -> null
        }
        if (row != null) _extras.value = _extras.value + (row to 1.0)
    }
    fun addSpareShells(count: Int = 1) = sandbox { holder.enqueue(Command.AddSpareShells(count)) }
    /** Pod / phyto doses and pest treatments from the microscope (research 10). */
    fun micro(action: MicroAction) = sandbox { holder.enqueue(Command.Micro(action)) }
    /** Bottled nitrifiers, carbon dosing, antibiotic, UV, stirring the sand, probiotic (research 18). */
    fun bacteria(action: BacteriaAction) = sandbox { holder.enqueue(Command.Bacteria(action)) }

    /**
     * One-tap what-if presets. Power outage and overfeeding reuse the core scenarios; the rest are built from commands
     * the sim already supports.
     */
    fun runPreset(p: SandboxPreset) = sandbox {
        val w = hud.value.water
        when (p) {
            SandboxPreset.HEATER_STUCK -> setHeater(working = true, stuckOn = true)
            SandboxPreset.POWER_OUTAGE -> runScenario(Scenario.POWER_OUTAGE)
            SandboxPreset.OVERFEEDING -> runScenario(Scenario.OVERFEEDING)
            // TODO(sim/realism): replace with "skip routine water changes" once the sim exposes the water-change
            //  schedule; for now jump straight to roughly two months of build-up.
            SandboxPreset.MISSED_WATER_CHANGES -> {
                setParam("no3", w.no3 + 40.0)
                setParam("po4", w.po4 + 0.3)
                setParam("alk", (w.alk - 1.5).coerceAtLeast(0.0))
                setParam("ca", (w.ca - 40.0).coerceAtLeast(100.0))
                setParam("mg", (w.mg - 80.0).coerceAtLeast(600.0))
                setParam("algae", (w.algaeHair + 0.15).coerceAtMost(1.0))
            }
            // the top-off floods fresh water in over ~30 min, then the sim takes over again
            SandboxPreset.SALINITY_CRASH -> holder.enqueue(Command.RampParam("sal", 1.012, 30.0, hold = false))
            SandboxPreset.COPPER_OVERDOSE -> setParam("copper", 0.5)
        }
    }

    /** Restores the sandbox to the state it was forked from. */
    fun resetSandbox() = sandbox {
        _lightPct.value = 100.0
        _extras.value = DEFAULT_EXTRAS
        holder.enqueue(Command.Photoperiod(DEFAULT_EXTRAS.getValue("photoperiod")))
        for ((id, v) in DEFAULT_EXTRAS) when {
            id.startsWith("pump_") -> holder.enqueue(Command.SetParam(PUMP_IDS.getValue(id), v))
            id.startsWith("spectrum_") -> holder.enqueue(Command.SetParam("spectrum." + id.removePrefix("spectrum_"), v))
        }
        for ((id, v) in TOGGLE_DEFAULTS) SandboxParams.byId(id)?.simId?.let { holder.enqueue(Command.SetParam(it, v)) }
        _predation.value = true
        holder.resetSandbox()
        // ResetToReality restores water and livestock; also undo the overrides that live outside them
        holder.enqueue(Command.Lights(on = null, overrideIntensity = 1.0))
        holder.enqueue(Command.Heater(working = true, stuckOn = false))
        holder.enqueue(Command.SetPredation(true))
    }

    companion object {
        const val HUD_PERIOD_MS = 250L   // 4 Hz
        const val REAL_SAMPLE_MS = 500L
        val PUMP_IDS = mapOf("pump_left" to "pump.PH-B", "pump_right" to "pump.PH-A")
        /** On/off sandbox rows (equipment, unproven scenarios) → default; the skimmer is the only one that starts on. */
        val TOGGLE_DEFAULTS: Map<String, Double> = mapOf(
            "eq_skimmer" to 1.0, "eq_sock" to 0.0, "eq_carbon" to 0.0, "eq_refugium" to 0.0, "eq_uv" to 0.0,
            "sc_asterina_coral" to 0.0, "sc_crab_predation" to 0.0,
        )
        val DEFAULT_EXTRAS: Map<String, Double> = mapOf(
            "photoperiod" to com.reeflive.core.sim.LightSchedule.DEFAULT_PHOTOPERIOD_H,
            "pump_left" to 1.0, "pump_right" to 1.0,
            "spectrum_royal" to 1.0, "spectrum_blue" to 1.0, "spectrum_white" to 1.0, "spectrum_violet" to 1.0,
        ) + TOGGLE_DEFAULTS
    }
}
