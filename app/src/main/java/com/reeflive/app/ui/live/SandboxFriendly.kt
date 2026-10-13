package com.reeflive.app.ui.live

import com.reeflive.app.ui.components.ReefGlyph
import com.reeflive.app.ui.components.ReefGlyphs
import com.reeflive.core.sim.Disease
import com.reeflive.core.sim.TreatmentKind
import java.util.Locale

/** Which animated glyph (LiveGlyphArt) a row's badge shows instead of its static one; follows the slider value. */
enum class ParamAnim { NONE, THERMOMETER, BUBBLES, SUN_MOON }

/**
 * Plain-words layer over [SandboxParams] so the sandbox is usable by anyone, kids included: a friendly title, a big
 * icon and a one-line explanation for every row. The expert name and units stay visible as secondary text on each
 * row, so nothing is hidden from experienced reefers. Pure UI copy and glyph data: changing it never changes what
 * the simulation does, and nothing here builds Compose objects, so it can be unit-tested on the JVM.
 *
 * [accentChannel] names a light channel ("royal", "blue", "white", "violet") whose colour tints the glyph accent.
 */
class FriendlyParam(
    val title: String,
    val glyph: ReefGlyph,
    val explain: String,
    val anim: ParamAnim = ParamAnim.NONE,
    val accentChannel: String? = null,
)

object SandboxFriendly {

    private val params: Map<String, FriendlyParam> = mapOf(
        // Water
        "temp" to FriendlyParam("Water warmth", ReefGlyphs.thermometer, "How warm the water is. Reef fish like it steady, around 25–26 °C (77–79 °F). Too hot and they run out of air.", ParamAnim.THERMOMETER),
        "sal" to FriendlyParam("Saltiness", ReefGlyphs.salt, "How salty the water is, measured as specific gravity (SG). The ocean is about 1.025."),
        "ph" to FriendlyParam("Water balance (pH)", ReefGlyphs.flask, "Is the water a bit sour or a bit soapy? Sea water sits at about 8.1–8.3."),
        "do" to FriendlyParam("Air in the water", ReefGlyphs.bubbles, "Oxygen that fish breathe through their gills. Warm water holds less of it.", ParamAnim.BUBBLES),
        // Chemistry
        "alk" to FriendlyParam("Shell builder: alkalinity", ReefGlyphs.shell, "Corals use it to build their skeletons. It also keeps the pH steady."),
        "ca" to FriendlyParam("Shell builder: calcium", ReefGlyphs.branch, "The main building block of coral skeletons and snail shells."),
        "mg" to FriendlyParam("Calcium helper: magnesium", ReefGlyphs.rings, "Keeps calcium and alkalinity from falling out of the water."),
        "k" to FriendlyParam("Coral colour: potassium", ReefGlyphs.palette, "A trace mineral corals need for good colour."),
        "sr" to FriendlyParam("Trace mineral: strontium", ReefGlyphs.gem, "A tiny extra that corals build into their skeletons."),
        "copper" to FriendlyParam("Copper medicine", ReefGlyphs.copperBottle, "A fish medicine. Even a little is poison for corals, shrimp and snails."),
        // Nutrients
        "nh3" to FriendlyParam("Fresh fish waste", ReefGlyphs.fishWaste, "Ammonia: what fish and rotting food give off first. Toxic, even a little."),
        "no2" to FriendlyParam("Half-cleaned waste", ReefGlyphs.halfClean, "Nitrite: bacteria are halfway through cleaning up ammonia. Still harmful."),
        "no3" to FriendlyParam("Food left over", ReefGlyphs.crumbs, "Nitrate: what is left after bacteria clean up waste and old food. A little is fine; lots feeds algae."),
        "po4" to FriendlyParam("Algae food", ReefGlyphs.seaweed, "Phosphate comes from fish food. Too much and algae grows fast."),
        "algae" to FriendlyParam("Green fuzz on the rocks", ReefGlyphs.fuzzyRock, "How much of the rock is covered in hair algae."),
        // Light & flow
        "light" to FriendlyParam("Light brightness", ReefGlyphs.sun, "How bright the lamps are compared with their normal schedule.", ParamAnim.SUN_MOON),
        "flow" to FriendlyParam("Water currents", ReefGlyphs.flow, "How hard the pumps push the water around.", ParamAnim.BUBBLES),
        "photoperiod" to FriendlyParam("Hours of daylight", ReefGlyphs.clockSun, "How many hours the lights are on each day."),
        "pump_left" to FriendlyParam("Left wave pump", ReefGlyphs.powerhead, "The pump on the left wall that makes the current."),
        "pump_right" to FriendlyParam("Right wave pump", ReefGlyphs.powerhead.mirrored(), "The pump on the right wall that makes the current."),
        "spectrum_royal" to FriendlyParam("Deep blue light", ReefGlyphs.led, "The royal blue LEDs. Corals glow under them.", accentChannel = "royal"),
        "spectrum_blue" to FriendlyParam("Blue light", ReefGlyphs.led, "The blue LEDs.", accentChannel = "blue"),
        "spectrum_white" to FriendlyParam("White light", ReefGlyphs.led, "The white LEDs. More white makes the tank look like daylight.", accentChannel = "white"),
        "spectrum_violet" to FriendlyParam("Violet light", ReefGlyphs.led, "The violet LEDs that make corals fluoresce.", accentChannel = "violet"),
        // Equipment and scenario switches
        "eq_skimmer" to FriendlyParam("Bubble cleaner (skimmer)", ReefGlyphs.skimmer, "Makes foam that carries waste out of the water before it rots."),
        "eq_sock" to FriendlyParam("Filter sock", ReefGlyphs.sock, "A cloth bag that catches floating bits. It needs changing when dirty."),
        "eq_carbon" to FriendlyParam("Carbon filter", ReefGlyphs.carbon, "Black grains that soak up yellow colour and smells."),
        "eq_refugium" to FriendlyParam("Algae garden (refugium)", ReefGlyphs.refugium, "A side tank where good algae grows and eats up leftover food."),
        "eq_uv" to FriendlyParam("Germ zapper (UV)", ReefGlyphs.uvLamp, "A UV lamp that kills floating germs and green water."),
        "sc_asterina_coral" to FriendlyParam("Tiny starfish eat corals", ReefGlyphs.starfish, "Test idea: Asterina starfish nibble organ pipe coral. Not proven."),
        "sc_crab_predation" to FriendlyParam("Crab hunts fish", ReefGlyphs.crab, "Test idea: the Sally Lightfoot crab catches small fish. Not proven."),
    )

    /** Friendly copy for [def]; rows without an entry fall back to their expert label. */
    fun of(def: SandboxParamDef): FriendlyParam = params[def.id] ?: FriendlyParam(def.label, ReefGlyphs.sparkleTip, "")

    fun has(id: String): Boolean = id in params

    /**
     * Secondary "expert" text for a row: the technical name and anything that helps adults (°F next to °C), e.g.
     * "Temperature · 78.8 °F". Null when the friendly title already is the technical name.
     */
    fun expert(def: SandboxParamDef, now: TankReading?): String? {
        val parts = mutableListOf<String>()
        val title = of(def).title
        if (!title.contains(def.label, ignoreCase = true)) parts += def.label
        if (def.id == "temp" && now != null) parts += String.format(Locale.US, "%.1f °F", now.water.tempC * 9.0 / 5.0 + 32.0)
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    /** Tab order in the dock: the playful tabs early, the expert chemistry later. */
    val tabs: List<SandboxCategory> = listOf(
        SandboxCategory.WATER,
        SandboxCategory.STOCK,
        SandboxCategory.EVENTS,
        SandboxCategory.LIGHT_FLOW,
        SandboxCategory.NUTRIENTS,
        SandboxCategory.CHEMISTRY,
        SandboxCategory.PESTS,
    ).let { order -> order + SandboxCategory.values().filter { it !in order } }

    fun tabLabel(c: SandboxCategory): String = when (c) {
        SandboxCategory.WATER -> "Water"
        SandboxCategory.CHEMISTRY -> "Minerals"
        SandboxCategory.NUTRIENTS -> "Waste & food"
        SandboxCategory.LIGHT_FLOW -> "Light & waves"
        SandboxCategory.PESTS -> "Bugs & medicine"
        SandboxCategory.STOCK -> "Add life"
        SandboxCategory.EVENTS -> "What if?"
    }

    fun tabGlyph(c: SandboxCategory): ReefGlyph = when (c) {
        SandboxCategory.WATER -> ReefGlyphs.droplet
        SandboxCategory.CHEMISTRY -> ReefGlyphs.flask
        SandboxCategory.NUTRIENTS -> ReefGlyphs.feedTin
        SandboxCategory.LIGHT_FLOW -> ReefGlyphs.sun
        SandboxCategory.PESTS -> ReefGlyphs.pestAnemone
        SandboxCategory.STOCK -> ReefGlyphs.fish
        SandboxCategory.EVENTS -> ReefGlyphs.whatIf
    }

    /** One short line under each tab's title so people know what it is for. */
    fun tabHelp(c: SandboxCategory): String = when (c) {
        SandboxCategory.WATER -> "Slide to change the water. The thin line marks your real tank."
        SandboxCategory.CHEMISTRY -> "Minerals corals use to grow. Small changes matter here."
        SandboxCategory.NUTRIENTS -> "Waste from fish and food. Lower is usually better."
        SandboxCategory.LIGHT_FLOW -> "Lamps and pumps. Flip a switch or slide."
        SandboxCategory.PESTS -> "Add pests, try medicines and switch equipment on or off."
        SandboxCategory.STOCK -> "Pick animals and corals to drop into the tank."
        SandboxCategory.EVENTS -> "Try what-ifs, like a power cut, and watch what happens."
    }

    /** Glyph for a what-if preset card. */
    fun presetGlyph(p: SandboxPreset): ReefGlyph = when (p) {
        SandboxPreset.HEATER_STUCK -> ReefGlyphs.flame
        SandboxPreset.POWER_OUTAGE -> ReefGlyphs.plugOff
        SandboxPreset.OVERFEEDING -> ReefGlyphs.crumbs
        SandboxPreset.MISSED_WATER_CHANGES -> ReefGlyphs.calendarX
        SandboxPreset.SALINITY_CRASH -> ReefGlyphs.dropDown
        SandboxPreset.COPPER_OVERDOSE -> ReefGlyphs.copperBottle
    }

    /** Glyph for a treatment row. */
    fun treatmentGlyph(k: TreatmentKind): ReefGlyph = when (k) {
        TreatmentKind.HYPOSALINITY -> ReefGlyphs.dropDown
        TreatmentKind.COPPER -> ReefGlyphs.copperBottle
        TreatmentKind.UV -> ReefGlyphs.uvLamp
        TreatmentKind.TANK_TRANSFER -> ReefGlyphs.bucket
        TreatmentKind.FORMALIN -> ReefGlyphs.flask
        else -> ReefGlyphs.bottle
    }

    /** Plain name for a disease, with the expert name in brackets. */
    fun diseaseLabel(d: Disease): String = when (d.name) {
        "ICH" -> "White spot (ich)"
        "VELVET" -> "Velvet"
        "HLLE" -> "Hole-in-head (HLLE)"
        "BACTERIAL" -> "Bacterial infection"
        "BROOKLYNELLA" -> "Clownfish disease (Brooklynella)"
        "URONEMA" -> "Uronema"
        "FLUKES" -> "Flukes"
        else -> d.name.take(1) + d.name.drop(1).lowercase()
    }

    /** "Real time", "1 hour every minute", ... for the speed selector. */
    fun speedHelp(speed: Double): String {
        if (speed <= 1.0) return "Real time: one second is one second"
        val minutesPerMinute = speed   // sim seconds per real second = sim minutes per real minute
        return if (minutesPerMinute < 60.0) {
            "${minutesPerMinute.toLong()} minutes pass every minute"
        } else {
            val h = minutesPerMinute / 60.0
            when {
                h == 1.0 -> "1 hour passes every minute"
                h % 1.0 == 0.0 -> "${h.toLong()} hours pass every minute"
                else -> "About ${String.format(Locale.US, "%.0f", h)} hours pass every minute"
            }
        }
    }

    /** Snowflake for the "Cooler" quick button (kept here so every sandbox icon lives in one place). */
    val coolerGlyph: ReefGlyph get() = ReefGlyphs.snowflake
}
