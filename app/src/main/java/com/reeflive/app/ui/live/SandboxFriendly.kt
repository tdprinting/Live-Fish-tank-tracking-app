package com.reeflive.app.ui.live

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.BubbleChart
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.Grass
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Timelapse
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.ui.graphics.vector.ImageVector
import com.reeflive.core.sim.Disease
import java.util.Locale

/**
 * Plain-words layer over [SandboxParams] so the sandbox is usable by anyone, kids included: a friendly title, a big icon
 * and a one-line explanation for every row. The expert name and units stay visible as secondary text on each row, so
 * nothing is hidden from experienced reefers. Pure UI copy: changing it never changes what the simulation does.
 */
class FriendlyParam(val title: String, private val iconOf: () -> ImageVector, val explain: String) {
    /** Looked up on use, so the copy can be read (and unit-tested) without building any vector art. */
    val icon: ImageVector get() = iconOf()
}

object SandboxFriendly {

    private val params: Map<String, FriendlyParam> = mapOf(
        // Water
        "temp" to FriendlyParam("Water warmth", { Icons.Rounded.Thermostat }, "How warm the water is. Reef fish like it steady, around 25–26 °C (77–79 °F). Too hot and they run out of air."),
        "sal" to FriendlyParam("Saltiness", { Icons.Rounded.WaterDrop }, "How salty the water is, measured as specific gravity (SG). The ocean is about 1.025."),
        "ph" to FriendlyParam("Water balance (pH)", { Icons.Rounded.Science }, "Is the water a bit sour or a bit soapy? Sea water sits at about 8.1–8.3."),
        "do" to FriendlyParam("Air in the water", { Icons.Rounded.Air }, "Oxygen that fish breathe through their gills. Warm water holds less of it."),
        // Chemistry
        "alk" to FriendlyParam("Shell builder: alkalinity", { Icons.Rounded.Layers }, "Corals use it to build their skeletons. It also keeps the pH steady."),
        "ca" to FriendlyParam("Shell builder: calcium", { Icons.Rounded.Spa }, "The main building block of coral skeletons and snail shells."),
        "mg" to FriendlyParam("Calcium helper: magnesium", { Icons.Rounded.Spa }, "Keeps calcium and alkalinity from falling out of the water."),
        "k" to FriendlyParam("Coral colour: potassium", { Icons.Rounded.Palette }, "A trace mineral corals need for good colour."),
        "sr" to FriendlyParam("Trace mineral: strontium", { Icons.Rounded.Science }, "A tiny extra that corals build into their skeletons."),
        "copper" to FriendlyParam("Copper medicine", { Icons.Rounded.Medication }, "A fish medicine. Even a little is poison for corals, shrimp and snails."),
        // Nutrients
        "nh3" to FriendlyParam("Fresh fish waste", { Icons.Rounded.Pets }, "Ammonia: what fish and rotting food give off first. Toxic, even a little."),
        "no2" to FriendlyParam("Half-cleaned waste", { Icons.Rounded.Pets }, "Nitrite: bacteria are halfway through cleaning up ammonia. Still harmful."),
        "no3" to FriendlyParam("Food left over", { Icons.Rounded.Restaurant }, "Nitrate: what is left after bacteria clean up waste and old food. A little is fine; lots feeds algae."),
        "po4" to FriendlyParam("Algae food", { Icons.Rounded.Grass }, "Phosphate comes from fish food. Too much and algae grows fast."),
        "algae" to FriendlyParam("Green fuzz on the rocks", { Icons.Rounded.Eco }, "How much of the rock is covered in hair algae."),
        // Light & flow
        "light" to FriendlyParam("Light brightness", { Icons.Rounded.LightMode }, "How bright the lamps are compared with their normal schedule."),
        "flow" to FriendlyParam("Water currents", { Icons.Rounded.Waves }, "How hard the pumps push the water around."),
        "photoperiod" to FriendlyParam("Hours of daylight", { Icons.Rounded.Schedule }, "How many hours the lights are on each day."),
        "pump_left" to FriendlyParam("Left wave pump", { Icons.Rounded.Waves }, "The pump on the left wall that makes the current."),
        "pump_right" to FriendlyParam("Right wave pump", { Icons.Rounded.Waves }, "The pump on the right wall that makes the current."),
        "spectrum_royal" to FriendlyParam("Deep blue light", { Icons.Rounded.Palette }, "The royal blue LEDs. Corals glow under them."),
        "spectrum_blue" to FriendlyParam("Blue light", { Icons.Rounded.Palette }, "The blue LEDs."),
        "spectrum_white" to FriendlyParam("White light", { Icons.Rounded.Palette }, "The white LEDs. More white makes the tank look like daylight."),
        "spectrum_violet" to FriendlyParam("Violet light", { Icons.Rounded.Palette }, "The violet LEDs that make corals fluoresce."),
        // Equipment and scenario switches
        "eq_skimmer" to FriendlyParam("Bubble cleaner (skimmer)", { Icons.Rounded.BubbleChart }, "Makes foam that carries waste out of the water before it rots."),
        "eq_sock" to FriendlyParam("Filter sock", { Icons.Rounded.FilterAlt }, "A cloth bag that catches floating bits. It needs changing when dirty."),
        "eq_carbon" to FriendlyParam("Carbon filter", { Icons.Rounded.FilterAlt }, "Black grains that soak up yellow colour and smells."),
        "eq_refugium" to FriendlyParam("Algae garden (refugium)", { Icons.Rounded.Eco }, "A side tank where good algae grows and eats up leftover food."),
        "eq_uv" to FriendlyParam("Germ zapper (UV)", { Icons.Rounded.Lightbulb }, "A UV lamp that kills floating germs and green water."),
        "sc_asterina_coral" to FriendlyParam("Tiny starfish eat corals", { Icons.Rounded.BugReport }, "Test idea: Asterina starfish nibble organ pipe coral. Not proven."),
        "sc_crab_predation" to FriendlyParam("Crab hunts fish", { Icons.Rounded.BugReport }, "Test idea: the Sally Lightfoot crab catches small fish. Not proven."),
    )

    /** Friendly copy for [def]; rows without an entry fall back to their expert label. */
    fun of(def: SandboxParamDef): FriendlyParam = params[def.id] ?: FriendlyParam(def.label, { Icons.Rounded.Tune }, "")

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

    fun tabIcon(c: SandboxCategory): ImageVector = when (c) {
        SandboxCategory.WATER -> Icons.Rounded.WaterDrop
        SandboxCategory.CHEMISTRY -> Icons.Rounded.Science
        SandboxCategory.NUTRIENTS -> Icons.Rounded.Restaurant
        SandboxCategory.LIGHT_FLOW -> Icons.Rounded.LightMode
        SandboxCategory.PESTS -> Icons.Rounded.BugReport
        SandboxCategory.STOCK -> Icons.Rounded.Pets
        SandboxCategory.EVENTS -> Icons.Rounded.Timelapse
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
    val coolerIcon: ImageVector get() = Icons.Rounded.AcUnit
}
