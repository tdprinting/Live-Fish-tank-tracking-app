package com.reeflive.app.ui.live

import com.reeflive.app.ui.components.ReefSliderSpec
import com.reeflive.app.ui.components.SliderZones
import com.reeflive.core.sim.WaterState
import java.util.Locale

/**
 * Sandbox parameter table: the ONE place to tune what the inline sandbox panel offers.
 *
 * Each row is a slider: display range (deliberately extreme, so catastrophic what-ifs are possible), step, zones and how
 * it maps onto the simulation. Ranges and zones follow the extreme-ranges research (Part B of
 * research/09_algae_and_extremes.md; temperature from 08_extreme_heat.md: caution from 30 °C, danger from 33 °C).
 *
 * Adding a parameter = adding a row. Rows whose [SandboxParamDef.simId] is null are shown disabled with
 * [SandboxParamDef.pendingNote] until the simulation supports them.
 *
 * TODO(sim/realism): when that branch lands, add rows for its new sandbox parameters here and flip the
 *  photoperiod / per-pump / spectrum rows below to their new SetParam ids.
 */
object SandboxParams {

    private const val PENDING = "Coming with the next simulation update"

    val all: List<SandboxParamDef> = listOf(
        // ---------------------------------------------------------------- Water
        SandboxParamDef(
            "temp", "Temperature", "Temp", "°C", SandboxCategory.WATER,
            min = 15.0, max = 40.0, step = 0.1, decimals = 1,
            zones = SliderZones(dangerBelow = 18.0, safeFrom = 23.0, safeTo = 30.0, dangerAbove = 33.0),
            read = { it.water.tempC },
        ),
        SandboxParamDef(
            "sal", "Salinity", "Sal", "SG", SandboxCategory.WATER,
            min = 1.000, max = 1.045, step = 0.001, decimals = 3,
            zones = SliderZones(dangerBelow = 1.017, safeFrom = 1.021, safeTo = 1.028, dangerAbove = 1.032),
            read = { it.water.sg },
        ),
        SandboxParamDef(
            "ph", "pH", "pH", "", SandboxCategory.WATER,
            min = 6.5, max = 9.5, step = 0.01, decimals = 2,
            zones = SliderZones(dangerBelow = 7.6, safeFrom = 7.9, safeTo = 8.4, dangerAbove = 8.8),
            read = { it.water.ph },
        ),
        SandboxParamDef(
            "do", "Dissolved oxygen", "O2", "mg/L", SandboxCategory.WATER,
            min = 0.0, max = 20.0, step = 0.1, decimals = 1,
            zones = SliderZones(dangerBelow = 3.5, safeFrom = 5.0, safeTo = 9.0, dangerAbove = 12.0),
            read = { it.water.doMgL },
        ),

        // ---------------------------------------------------------------- Chemistry
        SandboxParamDef(
            "alk", "Alkalinity", "Alk", "dKH", SandboxCategory.CHEMISTRY,
            min = 0.0, max = 25.0, step = 0.1, decimals = 1,
            zones = SliderZones(dangerBelow = 4.5, safeFrom = 6.5, safeTo = 11.0, dangerAbove = 14.0),
            read = { it.water.alk },
        ),
        SandboxParamDef(
            "ca", "Calcium", "Ca", "ppm", SandboxCategory.CHEMISTRY,
            min = 100.0, max = 800.0, step = 5.0, decimals = 0,
            zones = SliderZones(dangerBelow = 280.0, safeFrom = 370.0, safeTo = 480.0, dangerAbove = 550.0),
            read = { it.water.ca },
        ),
        SandboxParamDef(
            "mg", "Magnesium", "Mg", "ppm", SandboxCategory.CHEMISTRY,
            min = 600.0, max = 2500.0, step = 10.0, decimals = 0,
            zones = SliderZones(dangerBelow = 1050.0, safeFrom = 1200.0, safeTo = 1600.0, dangerAbove = 2200.0),
            read = { it.water.mg },
        ),
        SandboxParamDef(
            "copper", "Copper", "Cu", "ppm", SandboxCategory.CHEMISTRY,
            min = 0.0, max = 2.0, step = 0.005, decimals = 3, curve = 2.0,
            zones = SliderZones(dangerBelow = null, safeFrom = 0.0, safeTo = 0.005, dangerAbove = 0.05),
            read = { it.water.copper },
        ),

        // ---------------------------------------------------------------- Nutrients
        // The sim's ammonia is total ammonia (TAN); the toxic un-ionised part depends on pH and temperature, so it is
        // shown as the caption. Zones on TAN assume pH ~8.2 / 26 °C (free NH3 ≈ 7 % of TAN).
        SandboxParamDef(
            "nh3", "Ammonia (total)", "NH3", "ppm", SandboxCategory.NUTRIENTS,
            min = 0.0, max = 50.0, step = 0.01, decimals = 2, curve = 3.0,
            zones = SliderZones(dangerBelow = null, safeFrom = 0.0, safeTo = 0.25, dangerAbove = 1.0),
            read = { it.water.nh3Total },
            caption = { r -> "free NH3 " + String.format(Locale.US, "%.3f", r.water.nh3Free) },
        ),
        SandboxParamDef(
            "no2", "Nitrite", "NO2", "ppm", SandboxCategory.NUTRIENTS,
            min = 0.0, max = 100.0, step = 0.01, decimals = 2, curve = 3.0,
            zones = SliderZones(dangerBelow = null, safeFrom = 0.0, safeTo = 0.5, dangerAbove = 5.0),
            read = { it.water.no2 },
        ),
        SandboxParamDef(
            "no3", "Nitrate", "NO3", "ppm", SandboxCategory.NUTRIENTS,
            min = 0.0, max = 500.0, step = 0.5, decimals = 1, curve = 2.0,
            zones = SliderZones(dangerBelow = null, safeFrom = 1.0, safeTo = 20.0, dangerAbove = 80.0),
            read = { it.water.no3 },
        ),
        SandboxParamDef(
            "po4", "Phosphate", "PO4", "ppm", SandboxCategory.NUTRIENTS,
            min = 0.0, max = 10.0, step = 0.01, decimals = 2, curve = 2.5,
            zones = SliderZones(dangerBelow = null, safeFrom = 0.02, safeTo = 0.15, dangerAbove = 0.6),
            read = { it.water.po4 },
        ),
        SandboxParamDef(
            "algae", "Hair algae cover", "Algae", "%", SandboxCategory.NUTRIENTS,
            min = 0.0, max = 100.0, step = 1.0, decimals = 0, scale = 100.0,
            zones = SliderZones(dangerBelow = null, safeFrom = 0.0, safeTo = 20.0, dangerAbove = 60.0),
            read = { it.water.algaeHair * 100.0 },
        ),

        // ---------------------------------------------------------------- Light & flow
        // Light is a multiplier on the schedule (sim intensity override 0..1.5); the live PAR is the caption.
        // TODO(sim/realism): switch to an absolute PAR slider (0–1500 µmol/m²/s per research Part B) if the sim exposes one.
        SandboxParamDef(
            "light", "Light intensity", "Light", "%", SandboxCategory.LIGHT_FLOW,
            min = 0.0, max = 150.0, step = 1.0, decimals = 0, scale = 100.0,
            zones = SliderZones(dangerBelow = null, safeFrom = 50.0, safeTo = 110.0, dangerAbove = null),
            read = { it.lightPct },
            caption = { r -> "PAR now " + String.format(Locale.US, "%.0f", r.water.lightPar) },
        ),
        // Flow strength 0..2 of the tank's normal pumps (sim SetParam "flow" → Simulation.setFlowStrength).
        // TODO(sim/realism): research Part B wants 0–100x turnover; widen once the sim models more than 2x.
        SandboxParamDef(
            "flow", "Pump flow", "Flow", "%", SandboxCategory.LIGHT_FLOW,
            min = 0.0, max = 200.0, step = 5.0, decimals = 0, scale = 100.0,
            zones = SliderZones(dangerBelow = 15.0, safeFrom = 60.0, safeTo = 140.0, dangerAbove = null),
            read = { it.flowPct },
        ),
        // ---- not in the sim on main yet: shown disabled so nothing is silently dead
        SandboxParamDef(
            "photoperiod", "Photoperiod", "Photo", "h", SandboxCategory.LIGHT_FLOW,
            min = 0.0, max = 24.0, step = 0.5, decimals = 1,
            zones = SliderZones(dangerBelow = null, safeFrom = 8.0, safeTo = 12.0, dangerAbove = 14.0),
            simId = null, pendingNote = PENDING, // TODO(sim/realism): photoperiod SetParam id
            read = { null }, fallback = 10.0,
        ),
        SandboxParamDef(
            "pump_left", "Left powerhead", "Pump L", "%", SandboxCategory.LIGHT_FLOW,
            min = 0.0, max = 200.0, step = 5.0, decimals = 0, scale = 100.0,
            simId = null, pendingNote = PENDING, // TODO(sim/realism): per-pump strength command
            read = { null }, fallback = 100.0,
        ),
        SandboxParamDef(
            "pump_right", "Right powerhead", "Pump R", "%", SandboxCategory.LIGHT_FLOW,
            min = 0.0, max = 200.0, step = 5.0, decimals = 0, scale = 100.0,
            simId = null, pendingNote = PENDING, // TODO(sim/realism): per-pump strength command
            read = { null }, fallback = 100.0,
        ),
        SandboxParamDef(
            "spectrum_blue", "Royal blue channel", "Blue", "%", SandboxCategory.LIGHT_FLOW,
            min = 0.0, max = 100.0, step = 1.0, decimals = 0, scale = 100.0,
            simId = null, pendingNote = PENDING, // TODO(sim/realism): spectrum channel command
            read = { null }, fallback = 70.0,
        ),
        SandboxParamDef(
            "spectrum_white", "White channel", "White", "%", SandboxCategory.LIGHT_FLOW,
            min = 0.0, max = 100.0, step = 1.0, decimals = 0, scale = 100.0,
            simId = null, pendingNote = PENDING, // TODO(sim/realism): spectrum channel command
            read = { null }, fallback = 40.0,
        ),
    )

    private val byId = all.associateBy { it.id }
    fun byId(id: String): SandboxParamDef? = byId[id]
    fun inCategory(c: SandboxCategory): List<SandboxParamDef> = all.filter { it.category == c }
}

/** Panel tabs. [EVENTS] holds the scenario presets and one-off actions instead of sliders. */
enum class SandboxCategory(val label: String) {
    WATER("Water"), CHEMISTRY("Chemistry"), NUTRIENTS("Nutrients"), LIGHT_FLOW("Light & Flow"), EVENTS("Events"),
}

/**
 * How a slider change is applied. Only [SET] exists on main.
 * TODO(sim/realism): enable STEP / RAMP / SCHEDULE (flip [available]) and route them in LiveTankScreen's onSet.
 * The panel shows the selector automatically once more than one mode is available.
 */
enum class SandboxControlMode(val label: String, val available: Boolean) {
    SET("Set now", true),
    STEP("Step", false),
    RAMP("Ramp", false),
    SCHEDULE("Schedule", false),
    ;

    companion object {
        val enabled: List<SandboxControlMode> get() = values().filter { it.available }
    }
}

/** What the panel reads: the water plus light/flow, as shown on the sliders (display units). */
data class TankReading(val water: WaterState, val lightPct: Double, val flowPct: Double)

/**
 * One sandbox slider row. Values on the slider are display values; the simulation gets `display / scale`
 * through `Command.SetParam(simId, ...)` (light and flow have dedicated handling in the screen).
 */
data class SandboxParamDef(
    val id: String,
    val label: String,
    val shortLabel: String,
    val unit: String,
    val category: SandboxCategory,
    val min: Double,
    val max: Double,
    val step: Double,
    val decimals: Int,
    val zones: SliderZones? = null,
    val curve: Double = 1.0,
    /** Display value = sim value × [scale]. */
    val scale: Double = 1.0,
    /** `Command.SetParam` id; null = not supported by the simulation yet (row shown disabled). */
    val simId: String? = id,
    val pendingNote: String? = null,
    val read: (TankReading) -> Double?,
    val caption: ((TankReading) -> String)? = null,
    /** Shown when [read] has nothing (unsupported rows). */
    val fallback: Double = min,
) {
    val supported: Boolean get() = simId != null
    val spec: ReefSliderSpec get() = ReefSliderSpec(label, unit, min, max, step, decimals, zones, curve)
    fun valueIn(r: TankReading?): Double = (r?.let(read) ?: fallback).coerceIn(min, max)
}

/** One-tap what-if presets (Events tab). Commands live in LiveTankViewModel.runPreset. */
enum class SandboxPreset(val label: String, val blurb: String, val focus: SandboxCategory, val hint: String) {
    HEATER_STUCK("Heater stuck on", "Heater runs nonstop and the water keeps warming.", SandboxCategory.WATER, "Watch Temperature and Oxygen; try 240x."),
    POWER_OUTAGE("Power outage", "Pumps, heater and lights off for 4 hours.", SandboxCategory.WATER, "Watch Oxygen and Temperature fall; try 60x."),
    OVERFEEDING("Overfeeding", "Six heavy feedings in half an hour.", SandboxCategory.NUTRIENTS, "Watch Ammonia, then Nitrate; try 240x."),
    MISSED_WATER_CHANGES("Missed water changes", "About two months of build-up: nutrients up, alk, Ca and Mg down.", SandboxCategory.NUTRIENTS, "Nitrate and Phosphate jumped; algae follows at 1000x."),
    SALINITY_CRASH("Salinity crash", "Top-off floods fresh water: SG falls to 1.012.", SandboxCategory.WATER, "Salinity is in the danger zone; watch the fish cards."),
    COPPER_OVERDOSE("Copper overdose", "Copper reaches 0.5 ppm: lethal to inverts and corals.", SandboxCategory.CHEMISTRY, "Copper is far past the danger line; try 60x."),
}
