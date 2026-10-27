package com.reeflive.app.ui.live

import com.reeflive.app.ui.components.ReefSliderSpec
import com.reeflive.app.ui.components.SliderZones
import com.reeflive.core.sim.AlgaeModel
import com.reeflive.core.sim.AlgaeSurface
import com.reeflive.core.sim.LightSchedule
import com.reeflive.core.sim.Simulation
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
 * How a change is applied (set now, step and hold, ramp, spike) is chosen with [SandboxControlMode].
 */
object SandboxParams {

    val all: List<SandboxParamDef> = listOf(
        // ---------------------------------------------------------------- Water
        SandboxParamDef(
            "temp", "Temperature", "Temp", "°C", SandboxCategory.WATER,
            min = 10.0, max = 40.0, step = 0.1, decimals = 1,
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
        // Potassium and strontium (research 01 §1.1: K 360-440 acceptable, danger < 340 / > 460; Sr 7-11, danger < 6).
        SandboxParamDef(
            "k", "Potassium", "K", "ppm", SandboxCategory.CHEMISTRY,
            min = 100.0, max = 800.0, step = 5.0, decimals = 0,
            zones = SliderZones(dangerBelow = 340.0, safeFrom = 360.0, safeTo = 440.0, dangerAbove = 460.0),
            read = { it.water.k },
        ),
        SandboxParamDef(
            "sr", "Strontium", "Sr", "ppm", SandboxCategory.CHEMISTRY,
            min = 0.0, max = 30.0, step = 0.5, decimals = 1,
            zones = SliderZones(dangerBelow = 6.0, safeFrom = 7.0, safeTo = 11.0, dangerAbove = 15.0),
            read = { it.water.sr },
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
        // Hair-algae cover on the rock; the sim's AlgaeModel re-seeds its hair biomass from this and grows it from there.
        // Rock cover tops out at 90 % (research 09 A6 cover limit), so the slider does too.
        SandboxParamDef(
            "algae", "Hair algae cover", "Algae", "%", SandboxCategory.NUTRIENTS,
            min = 0.0, max = 90.0, step = 1.0, decimals = 0, scale = 100.0,
            zones = SliderZones(dangerBelow = null, safeFrom = 0.0, safeTo = 20.0, dangerAbove = 60.0),
            read = { it.water.algaeHair * 100.0 },
        ),
        // Nuisance algae seeds (research 09 A2, A6): the cover the AlgaeModel grows (or loses) from; the outbreak presets
        // in Events set up the conditions that drive each one. Cyano and dinos cover sand and rock; bubble algae only
        // reach 30 % of the rock (A6 cover limit).
        SandboxParamDef(
            "cyano", "Red slime (cyano) cover", "Cyano", "%", SandboxCategory.NUTRIENTS,
            min = 0.0, max = 90.0, step = 1.0, decimals = 0, scale = 100.0,
            zones = SliderZones(dangerBelow = null, safeFrom = 0.0, safeTo = 5.0, dangerAbove = 30.0),
            read = { it.water.cyano * 100.0 },
        ),
        SandboxParamDef(
            "dinos", "Dinoflagellate cover", "Dinos", "%", SandboxCategory.NUTRIENTS,
            min = 0.0, max = 90.0, step = 1.0, decimals = 0, scale = 100.0,
            zones = SliderZones(dangerBelow = null, safeFrom = 0.0, safeTo = 5.0, dangerAbove = 30.0),
            read = { it.water.dinos * 100.0 },
        ),
        SandboxParamDef(
            "bubble", "Bubble algae cover", "Bubble", "%", SandboxCategory.NUTRIENTS,
            min = 0.0, max = 30.0, step = 1.0, decimals = 0, scale = 100.0,
            zones = SliderZones(dangerBelow = null, safeFrom = 0.0, safeTo = 3.0, dangerAbove = 15.0),
            read = { it.water.bubble * 100.0 },
        ),

        // ---------------------------------------------------------------- Light & flow
        // Light as absolute PAR (µmol m⁻² s⁻¹) at the rock tops at the brightest point of the day (research 09 Part B:
        // 0-1500). The photoperiod schedule keeps its shape and is scaled to this peak (sim SetParam "par"); corals,
        // algae and photoinhibition run on the real PAR. Zones are for this soft-coral tank (05 §14: 200-400 typical;
        // soft corals pale and starve below ~50-100; 500+ bleaches in weeks, 800+ photoinhibits in days). The caption is
        // the live PAR reaching the sand bed and the rock tops right now (it follows the day's ramp and the water's clarity).
        SandboxParamDef(
            "light", "Peak light", "PAR", "PAR", SandboxCategory.LIGHT_FLOW,
            min = 0.0, max = LightSchedule.MAX_PAR, step = 5.0, decimals = 0, curve = 1.6, simId = "par",
            zones = SliderZones(dangerBelow = 50.0, safeFrom = 100.0, safeTo = 350.0, dangerAbove = 600.0),
            read = { r -> r.peakPar ?: (r.lightPct / 100.0 * DEFAULT_PEAK_PAR) },
            caption = { r -> parCaption(r.water.lightPar) },
        ),
        // Flow as tank turnover, volumes per hour (research 09 Part B: 0-100×, normal 20-40×). The sim splits it over the
        // pumps that are on (sim SetParam "turnover"); Tee's two powerheads and HOB give ~27×. Past ~60× the jets blast
        // soft-coral polyps shut, blow the sand about and send the fish into the rock; 0 means low oxygen and cyano.
        SandboxParamDef(
            "flow", "Turnover", "Flow", "×/h", SandboxCategory.LIGHT_FLOW,
            min = 0.0, max = 100.0, step = 1.0, decimals = 0, simId = "turnover",
            zones = SliderZones(dangerBelow = 5.0, safeFrom = 15.0, safeTo = 45.0, dangerAbove = 60.0),
            read = { r -> r.turnover ?: (r.flowPct / 100.0 * Simulation.NOMINAL_TURNOVER) },
            caption = { r -> flowCaption(r.turnover ?: (r.flowPct / 100.0 * Simulation.NOMINAL_TURNOVER)) },
        ),
        // Photoperiod: the programmed light day stretched about midday (sim clamps 4–16 h).
        SandboxParamDef(
            "photoperiod", "Photoperiod", "Photo", "h", SandboxCategory.LIGHT_FLOW,
            min = 4.0, max = 16.0, step = 0.5, decimals = 1,
            zones = SliderZones(dangerBelow = null, safeFrom = 8.0, safeTo = 12.0, dangerAbove = 14.0),
            read = { it.photoperiodH }, fallback = LightSchedule.DEFAULT_PHOTOPERIOD_H,
        ),
        // Individual pumps are on/off in the sim (0 = off, 1 = on); PH-B sits on the left wall, PH-A on the right.
        SandboxParamDef(
            "pump_left", "Left powerhead", "Pump L", "", SandboxCategory.LIGHT_FLOW,
            min = 0.0, max = 1.0, step = 1.0, decimals = 0, simId = "pump.PH-B",
            caption = { r -> if ((r.pumps["pump_left"] ?: 1.0) >= 0.5) "on" else "off" },
            read = { it.pumps["pump_left"] }, fallback = 1.0,
        ),
        SandboxParamDef(
            "pump_right", "Right powerhead", "Pump R", "", SandboxCategory.LIGHT_FLOW,
            min = 0.0, max = 1.0, step = 1.0, decimals = 0, simId = "pump.PH-A",
            caption = { r -> if ((r.pumps["pump_right"] ?: 1.0) >= 0.5) "on" else "off" },
            read = { it.pumps["pump_right"] }, fallback = 1.0,
        ),
        // Spectrum: gain on the programmed channel (100 % = as programmed).
        SandboxParamDef(
            "spectrum_royal", "Royal blue channel", "Royal", "%", SandboxCategory.LIGHT_FLOW,
            min = 0.0, max = 200.0, step = 5.0, decimals = 0, scale = 100.0, simId = "spectrum.royal",
            read = { it.gains["royal"]?.times(100.0) }, fallback = 100.0,
        ),
        SandboxParamDef(
            "spectrum_blue", "Blue channel", "Blue", "%", SandboxCategory.LIGHT_FLOW,
            min = 0.0, max = 200.0, step = 5.0, decimals = 0, scale = 100.0, simId = "spectrum.blue",
            read = { it.gains["blue"]?.times(100.0) }, fallback = 100.0,
        ),
        SandboxParamDef(
            "spectrum_white", "White channel", "White", "%", SandboxCategory.LIGHT_FLOW,
            min = 0.0, max = 200.0, step = 5.0, decimals = 0, scale = 100.0, simId = "spectrum.white",
            read = { it.gains["white"]?.times(100.0) }, fallback = 100.0,
        ),
        SandboxParamDef(
            "spectrum_violet", "Violet channel", "Violet", "%", SandboxCategory.LIGHT_FLOW,
            min = 0.0, max = 200.0, step = 5.0, decimals = 0, scale = 100.0, simId = "spectrum.violet",
            read = { it.gains["violet"]?.times(100.0) }, fallback = 100.0,
        ),

        // ---------------------------------------------------------------- Pests & treatments
        // On/off rows like the pumps (0 = off, 1 = on). Equipment ids are the sim's SetParam ids; the values live in the
        // view model's extras (the snapshot doesn't carry them). Defaults mirror WaterSim: skimmer on, the rest off.
        toggle("eq_skimmer", "Protein skimmer", "Skimmer", "skimmer", on = true),
        toggle("eq_sock", "Filter sock", "Sock", "sock"),
        toggle("eq_carbon", "Carbon reactor", "Carbon", "carbon"),
        toggle("eq_refugium", "Refugium", "Refugium", "refugium"),
        toggle("eq_uv", "UV steriliser", "UV", "uv"),
        // Scenario switches (sim SetParam, 1 = on, default off); unproven behaviours kept behind a toggle.
        toggle("sc_asterina_coral", "Asterina eat organ pipe (unproven)", "Asterina", "asterina_coral"),
        toggle("sc_crab_predation", "Sally Lightfoot hunts fish (unproven)", "Crab", "crab_predation"),
        // Routine upkeep: the sim's weekly 10 % water change is skipped, so nitrate and phosphate build up (and alk, Ca
        // and Mg run down) at the tank's own pace; speed time up to watch it.
        toggle("sc_skip_wc", "Skip routine water changes", "No WC", "skip_water_changes", category = SandboxCategory.NUTRIENTS),
    )

    /** Rock-top PAR at the default schedule's peak (the old "100 %"; ~300). */
    val DEFAULT_PEAK_PAR: Double = LightSchedule().programmedPeakPar

    /** "now: sand 103 · rock top 300": live PAR at the substrate and on the rock tops (rock-top [par] → sand by the sim's ratio). */
    fun parCaption(par: Double): String {
        val sand = par * LightSchedule.SAND_PAR_PER_INTENSITY / LightSchedule.PAR_PER_INTENSITY
        return "now: sand " + String.format(Locale.US, "%.0f", sand) + " · rock top " + String.format(Locale.US, "%.0f", par)
    }

    /** The current on the rock tops for a turnover (algae model's 15 cm/s at Tee's normal ~27×/h). */
    fun flowCaption(turnover: Double): String =
        if (turnover < 0.5) "pumps off" else "rock tops ~" + String.format(Locale.US, "%.0f", AlgaeModel.BASE_FLOW[AlgaeSurface.ROCK_TOP.ordinal] * turnover / Simulation.NOMINAL_TURNOVER) + " cm/s"


    /** An on/off sandbox row driven through `Command.SetParam(simId, 0 | 1)`; the state is kept in the view model's extras. */
    private fun toggle(id: String, label: String, shortLabel: String, simId: String, on: Boolean = false, category: SandboxCategory = SandboxCategory.PESTS) = SandboxParamDef(
        id, label, shortLabel, "", category,
        min = 0.0, max = 1.0, step = 1.0, decimals = 0, simId = simId,
        caption = { r -> if ((r.toggles[id] ?: (if (on) 1.0 else 0.0)) >= 0.5) "on" else "off" },
        read = { it.toggles[id] }, fallback = if (on) 1.0 else 0.0,
    )

    private val byId = all.associateBy { it.id }
    fun byId(id: String): SandboxParamDef? = byId[id]
    fun inCategory(c: SandboxCategory): List<SandboxParamDef> = all.filter { it.category == c }
}

/** Panel tabs. [EVENTS] holds the scenario presets and one-off actions instead of sliders. */
enum class SandboxCategory(val label: String) {
    WATER("Water"), CHEMISTRY("Chemistry"), NUTRIENTS("Nutrients"), LIGHT_FLOW("Light & Flow"), PESTS("Pests & treatments"), STOCK("Add life"), EVENTS("Events"),
}

/**
 * How a slider change is applied (research 06 §4.2: fish cope with a slow ramp far better than a step).
 * [SET] changes the value once and lets the chemistry drift; [STEP] jumps and holds it there; [RAMP] moves
 * there linearly over [rampMin] and holds; [SCHEDULE] is a spike: jump now, return to the old value after an hour.
 */
enum class SandboxControlMode(val label: String, val available: Boolean, val rampMin: Double = 0.0) {
    SET("Set now", true),
    STEP("Step & hold", true),
    RAMP("Ramp over a day", true, rampMin = 24 * 60.0),
    SCHEDULE("1-hour spike", true, rampMin = 60.0),
    ;

    companion object {
        val enabled: List<SandboxControlMode> get() = values().filter { it.available }
    }
}

/** What the panel reads: the water plus light/flow, as shown on the sliders (display units). */
data class TankReading(
    val water: WaterState,
    /** Old relative light (% of the schedule) and pump strength (% of normal): the gauges; rows prefer [peakPar] / [turnover]. */
    val lightPct: Double,
    val flowPct: Double,
    val photoperiodH: Double? = null,
    /** Peak rock-top PAR the sandbox is set to (sim snapshot `peakPar`). */
    val peakPar: Double? = null,
    /** Tank turnover now, volumes per hour (sim snapshot `turnover`). */
    val turnover: Double? = null,
    /** Pump row id → 1 on / 0 off. */
    val pumps: Map<String, Double> = emptyMap(),
    /** Spectrum channel name → gain (1 = as programmed). */
    val gains: Map<String, Double> = emptyMap(),
    /** On/off row id (equipment `eq_*`, scenario `sc_*`) → 1 on / 0 off. */
    val toggles: Map<String, Double> = emptyMap(),
)

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
    MISSED_WATER_CHANGES("Skip water changes", "Routine water changes stop: waste builds up at the tank's own pace.", SandboxCategory.NUTRIENTS, "Try 1000x and watch Nitrate and Phosphate climb over the weeks."),
    SALINITY_CRASH("Salinity crash", "Top-off floods fresh water: SG falls to 1.012.", SandboxCategory.WATER, "Salinity is in the danger zone; watch the fish cards."),
    COPPER_OVERDOSE("Copper overdose", "Copper reaches 0.5 ppm: lethal to inverts and corals.", SandboxCategory.CHEMISTRY, "Copper is far past the danger line; try 60x."),
    ROCKSLIDE("Rockslide", "The sand is dug out from under one side of a rock, like a busy goby would.", SandboxCategory.EVENTS, "Watch the rock settle or topple; fish dart away."),
    // Algae outbreaks (research 09 A2; core AlgaeOutbreaks sets the conditions and the seed)
    DINO_OUTBREAK("Dinoflagellate outbreak", "Nitrate and phosphate stripped to almost zero and held there, with few good microbes.", SandboxCategory.NUTRIENTS, "Brown snotty strands with bubbles by day, gone at night; try 1000x."),
    CYANO_OUTBREAK("Red slime (cyano)", "A dead spot over the sand, rotting organics, phosphate and little nitrate.", SandboxCategory.NUTRIENTS, "Red-maroon slime sheets take the sand first, then shaded rock; try 1000x."),
    BUBBLE_ALGAE("Bubble algae", "A patch of bubble algae on the shaded rock; phosphate moderate, pH 7.95.", SandboxCategory.NUTRIENTS, "Green bubbles spread slowly over weeks; pull them off under Make trouble. Try 1000x."),
}
