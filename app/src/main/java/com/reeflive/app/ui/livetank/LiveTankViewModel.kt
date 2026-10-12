package com.reeflive.app.ui.livetank

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reeflive.app.live.SimMode
import com.reeflive.app.live.SimulationHolder
import com.reeflive.app.ui.live.SandboxPreset
import com.reeflive.app.ui.live.TankReading
import com.reeflive.app.ui.nav.LiveFocus
import com.reeflive.core.geom.Vec3
import com.reeflive.core.live.LivePeek
import com.reeflive.core.live.Scenario
import com.reeflive.core.sim.Command
import com.reeflive.core.sim.FoodKind
import com.reeflive.core.sim.OrganismStats
import com.reeflive.core.sim.SimSnapshot
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
 * the renderer reads [snapshots] (full rate), the overlay reads [hud] (10 Hz).
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

    /** HUD snapshot throttled to 10 Hz. */
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
        if (m != mode.value) { _lightPct.value = 100.0; _predation.value = true }
        holder.setMode(m)
    }
    fun setSpeed(v: Double) = holder.setSpeed(v)
    fun select(id: String?) { _selectedOrganismId.value = id; if (id == null) _followId.value = null }
    fun follow(id: String?) { _followId.value = id; if (id != null) _selectedOrganismId.value = id }
    fun setQuality(q: LiveQuality) { _quality.value = q }

    // ---- actions allowed in both modes ----
    fun feed(kind: FoodKind = FoodKind.FLAKE, grams: Double = 1.0, at: Vec3? = null) = holder.enqueue(Command.Feed(kind, grams, at))
    fun tapGlass(worldPoint: Vec3) = holder.enqueue(Command.TapGlass(worldPoint))
    fun shadow(worldPoint: Vec3) = holder.enqueue(Command.Shadow(worldPoint))

    // ---- sandbox actions (ignored in REALITY mode) ----
    private inline fun sandbox(block: () -> Unit) { if (mode.value == SimMode.SANDBOX) block() }

    fun setParam(paramId: String, value: Double) = sandbox { holder.enqueue(Command.SetParam(paramId, value)) }
    fun dose(paramId: String, delta: Double) = sandbox { holder.enqueue(Command.Dose(paramId, delta)) }
    fun waterChange(fraction: Double, mix: WaterState = WaterState.FRESH_MIX) = sandbox { holder.enqueue(Command.WaterChange(fraction, mix)) }
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
    fun removeOrganism(id: String) = sandbox {
        holder.enqueue(Command.RemoveOrganism(id))
        if (_selectedOrganismId.value == id) select(null)
    }
    fun runScenario(s: Scenario) = sandbox { holder.runScenario(s) }

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
            // TODO(sim/realism): use a ramp (fresh water flooding in over ~30 min) instead of a step.
            SandboxPreset.SALINITY_CRASH -> setParam("sal", 1.012)
            SandboxPreset.COPPER_OVERDOSE -> setParam("copper", 0.5)
        }
    }

    /** Restores the sandbox to the state it was forked from. */
    fun resetSandbox() = sandbox {
        _lightPct.value = 100.0
        _predation.value = true
        holder.resetSandbox()
        // ResetToReality restores water and livestock; also undo the overrides that live outside them
        holder.enqueue(Command.Lights(on = null, overrideIntensity = 1.0))
        holder.enqueue(Command.Heater(working = true, stuckOn = false))
        holder.enqueue(Command.SetPredation(true))
    }

    companion object {
        const val HUD_PERIOD_MS = 100L   // 10 Hz
        const val REAL_SAMPLE_MS = 500L
    }
}
