package com.reeflive.app.ui.live

import com.reeflive.core.params.ParameterDef
import com.reeflive.core.params.ParameterTable
import com.reeflive.core.sim.OrganismStats
import com.reeflive.core.sim.WaterState
import com.reeflive.core.status.Status
import java.util.Locale

/** Watch = read-only live view of the real tank; Sandbox = what-if controls. */
enum class HudMode(val label: String) { WATCH("Watch"), SANDBOX("Sandbox") }

enum class DayPhase(val label: String) { NIGHT("Night"), DAWN("Dawn"), DAY("Day"), DUSK("Dusk") }

/** One cell of the compact water strip. [status] drives colour + icon (never colour only). */
data class WaterReadout(val id: String, val label: String, val valueText: String, val status: Status)

data class HudToast(val id: Long, val text: String, val severity: Status = Status.UNKNOWN)

data class HudState(
    val mode: HudMode = HudMode.WATCH,
    val speed: Double = 1.0,
    val speeds: List<Double> = listOf(1.0, 60.0, 240.0, 1000.0),
    val simTimeSec: Double = 0.0,
    val dayPhase: DayPhase = DayPhase.DAY,
    val readouts: List<WaterReadout> = emptyList(),
    val toast: HudToast? = null,
)

fun formatSpeed(s: Double): String = "${s.toLong()}x"

/** "Day 3 · 14:25" from simulated seconds (day 1 starts at 00:00). */
fun formatSimClock(simTimeSec: Double): String {
    val total = simTimeSec.toLong().coerceAtLeast(0)
    val day = total / 86_400 + 1
    val h = (total % 86_400) / 3600
    val m = (total % 3600) / 60
    return String.format(Locale.US, "Day %d · %02d:%02d", day, h, m)
}

// ---------------------------------------------------------------- bands

enum class BandZone { SAFE, STRESS, LETHAL }

/**
 * Safe / stress / lethal bands for a slider. Between [lethalLo]..[safeLo] and [safeHi]..[lethalHi] is stress;
 * outside the lethal bounds is lethal. A null lethal bound means "stress all the way to the end of the track".
 */
data class SliderBands(val lethalLo: Double?, val safeLo: Double, val safeHi: Double, val lethalHi: Double?) {
    fun zoneOf(v: Double): BandZone = when {
        lethalLo != null && v < lethalLo -> BandZone.LETHAL
        lethalHi != null && v > lethalHi -> BandZone.LETHAL
        v >= safeLo && v <= safeHi -> BandZone.SAFE
        else -> BandZone.STRESS
    }

    fun status(v: Double): Status = when (zoneOf(v)) {
        BandZone.SAFE -> Status.GOOD
        BandZone.STRESS -> Status.CAUTION
        BandZone.LETHAL -> Status.DANGER
    }

    companion object {
        /**
         * Bands from core [ParameterTable]: safe = ideal range (acceptable when the ideal is degenerate, e.g. ammonia),
         * lethal = beyond the danger thresholds. [conv] maps the table's base unit to the slider unit.
         */
        fun fromDef(def: ParameterDef, useAcceptableAsSafe: Boolean = false, conv: (Double) -> Double = { it }): SliderBands {
            val safe = if (useAcceptableAsSafe || def.ideal.span <= 0.0) def.acceptable else def.ideal
            return SliderBands(def.dangerLow?.let(conv), conv(safe.lo), conv(safe.hi), def.dangerHigh?.let(conv))
        }
    }
}

/** Slider spec: range, display and optional bands. [id] is the Command.SetParam id (or a UI-only id). */
data class SandboxSliderSpec(
    val id: String,
    val label: String,
    val unit: String,
    val min: Double,
    val max: Double,
    val decimals: Int,
    val bands: SliderBands? = null,
)

/** Default specs. Bands come from core [ParameterTable] where a row exists, otherwise from simple local rules. */
object SandboxSpecs {
    private fun def(id: String): ParameterDef? = ParameterTable.byId(id)
    private fun fToC(f: Double): Double = (f - 32.0) * 5.0 / 9.0

    private fun table(
        id: String, label: String, unit: String, min: Double, max: Double, decimals: Int,
        acceptable: Boolean = false, conv: (Double) -> Double = { it },
    ) = SandboxSliderSpec(id, label, unit, min, max, decimals, def(id)?.let { SliderBands.fromDef(it, acceptable, conv) })

    val temp = table("temp", "Temperature", "°C", 20.0, 34.0, 1, conv = { fToC(it) })
    val salinity = table("sal", "Salinity", "SG", 1.015, 1.035, 3)
    val nitrate = table("no3", "Nitrate", "ppm", 0.0, 100.0, 1)
    val phosphate = table("po4", "Phosphate", "ppm", 0.0, 1.0, 2)
    val light = SandboxSliderSpec("light", "Light intensity", "%", 0.0, 100.0, 0, SliderBands(null, 30.0, 80.0, null))
    val photoperiod = SandboxSliderSpec("photoperiod", "Photoperiod", "h", 0.0, 24.0, 1, SliderBands(null, 8.0, 12.0, null))
    val flow = SandboxSliderSpec("flow", "Pump flow", "x", 0.0, 2.0, 2, SliderBands(null, 0.6, 1.4, null))

    val alk = table("alk", "Alkalinity", "dKH", 4.0, 14.0, 1)
    val calcium = table("ca", "Calcium", "ppm", 300.0, 550.0, 0)
    val magnesium = table("mg", "Magnesium", "ppm", 1000.0, 1600.0, 0)
    val ph = table("ph", "pH", "", 7.4, 8.8, 2)
    val ammonia = table("nh3", "Ammonia", "ppm", 0.0, 1.0, 2, acceptable = true)
    val oxygen = table("do", "Oxygen", "mg/L", 2.0, 10.0, 1, acceptable = true)
    val algaeSeed = SandboxSliderSpec("algae", "Algae seed", "%", 0.0, 100.0, 0, null)

    val basic: List<SandboxSliderSpec> get() = listOf(temp, salinity, nitrate, phosphate, light, photoperiod, flow)
    val advanced: List<SandboxSliderSpec> get() = listOf(alk, calcium, magnesium, ph, ammonia, oxygen, algaeSeed)
}

// ---------------------------------------------------------------- sandbox

/** Current sandbox values; keys are [SandboxSliderSpec.id]. Missing keys fall back to the spec minimum. */
data class SandboxState(
    val values: Map<String, Double> = emptyMap(),
    val pumps: List<PumpUi> = emptyList(),
    val spectrum: List<SpectrumChannelUi> = emptyList(),
    val advancedExpanded: Boolean = false,
) {
    fun valueOf(spec: SandboxSliderSpec): Double = (values[spec.id] ?: spec.min).coerceIn(spec.min, spec.max)

    companion object {
        /** Sandbox values mirroring a core [WaterState]; [light01] 0..1, [photoperiodH] hours, [flowStrength] 0..2. */
        fun from(w: WaterState, light01: Double, photoperiodH: Double, flowStrength: Double, algae01: Double = 0.0) = SandboxState(
            values = mapOf(
                "temp" to w.tempC, "sal" to w.sg, "no3" to w.no3, "po4" to w.po4,
                "light" to light01 * 100, "photoperiod" to photoperiodH, "flow" to flowStrength,
                "alk" to w.alk, "ca" to w.ca, "mg" to w.mg, "ph" to w.ph, "nh3" to w.nh3Total, "do" to w.doMgL,
                "algae" to algae01 * 100,
            ),
        )
    }
}

/** One pump in the advanced section, [strength] 0..2 (1 = nominal). */
data class PumpUi(val id: String, val label: String, val strength: Float)

/** One light spectrum channel (share 0..1), e.g. royal blue / white / violet. */
data class SpectrumChannelUi(val id: String, val label: String, val value: Float)

enum class SandboxScenario(val label: String) {
    HEAT_WAVE("Heat wave"), NITRATE_SPIKE("Nitrate spike"), SALINITY_SWING("Salinity swing"),
    POWER_OUTAGE("Power outage"), ALGAE_BLOOM("Algae bloom"), OVERFEEDING("Overfeeding"),
}

/** Everything the panel can ask for. [onSetParam] ids are Command.SetParam ids plus "light", "photoperiod", "flow", "algae". */
data class SandboxActions(
    val onSetParam: (id: String, value: Double) -> Unit = { _, _ -> },
    val onSetPump: (id: String, strength: Float) -> Unit = { _, _ -> },
    val onSetSpectrum: (channelId: String, value: Float) -> Unit = { _, _ -> },
    val onFeed: () -> Unit = {},
    val onTapGlass: () -> Unit = {},
    val onScenario: (SandboxScenario) -> Unit = {},
    val onReset: () -> Unit = {},
    val onToggleAdvanced: (Boolean) -> Unit = {},
)

// ---------------------------------------------------------------- fish

enum class RelationKind(val label: String) {
    FRIEND("Friend"), NEUTRAL("Neutral"), WARY("Wary"), RIVAL("Rival"), MATE("Mate"), PREY("Prey"), PREDATOR("Predator"),
}

/** How this fish feels about one tank mate (social-memory entry). [affinity] -1 (hostile)..+1 (bonded). */
data class RelationshipUi(
    val otherName: String,
    val otherSpecies: String,
    val kind: RelationKind,
    val affinity: Float,
    val note: String = "",
)

data class FishCardState(
    val id: String,
    val speciesName: String,
    val name: String,
    val health: Float,
    val stress: Float,
    val hunger: Float,
    val mood: String,
    val behavior: String,
    val alive: Boolean = true,
    val symptoms: List<String> = emptyList(),
    val relationships: List<RelationshipUi> = emptyList(),
)

fun OrganismStats.toFishCardState(relationships: List<RelationshipUi> = emptyList()) = FishCardState(
    id = id, speciesName = common, name = nickname.ifBlank { common },
    health = health, stress = stress, hunger = hunger, mood = mood.label, behavior = behavior,
    alive = alive, symptoms = symptoms.map { it.label }, relationships = relationships,
)

data class FollowState(
    val fishName: String,
    val speciesName: String,
    val behaviorLine: String,
    val index: Int = 0,
    val count: Int = 1,
)

/** Water strip helper: temp, salinity, NO3, PO4 from [w] against [SandboxSpecs] bands, plus light % and flow. */
fun waterReadouts(w: WaterState, lightPct: Double, flowStrength: Double): List<WaterReadout> {
    fun f(v: Double, d: Int) = String.format(Locale.US, "%.${d}f", v)
    fun r(spec: SandboxSliderSpec, short: String, v: Double, text: String) =
        WaterReadout(spec.id, short, text, spec.bands?.status(v) ?: Status.UNKNOWN)
    return listOf(
        r(SandboxSpecs.temp, "Temp", w.tempC, f(w.tempC, 1) + "°"),
        r(SandboxSpecs.salinity, "Sal", w.sg, f(w.sg, 3)),
        r(SandboxSpecs.nitrate, "NO3", w.no3, f(w.no3, 1)),
        r(SandboxSpecs.phosphate, "PO4", w.po4, f(w.po4, 2)),
        r(SandboxSpecs.light, "Light", lightPct, f(lightPct, 0) + "%"),
        r(SandboxSpecs.flow, "Flow", flowStrength, f(flowStrength, 1) + "x"),
    )
}
