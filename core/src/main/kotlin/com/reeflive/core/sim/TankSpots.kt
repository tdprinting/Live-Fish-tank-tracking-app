package com.reeflive.core.sim

import kotlin.math.roundToInt

/**
 * The tank's gear you can tap in the 3D view (the renderer finds which one is under the finger). A tap opens a small
 * pop-out menu right there ([TankSpots.menu]) instead of a trip through the menus: tap a full skimmer cup, get
 * "Empty the cup". [glyph] names the picture (ReefGlyphs on Android, glyphs.js on the web: the same names).
 */
enum class TankSpot(val label: String, val glyph: String) {
    SKIMMER("Protein skimmer", "skimmer"),
    FILTER("Hang-on filter", "sock"),
    HEATER("Heater", "thermometer"),
    PUMPS("Powerheads", "powerhead"),
    LIGHTS("Lights", "sun"),
    TOP_OFF("Top-off", "droplet"),
}

/**
 * One button in a spot's pop-out menu. A [fix] plays like the tank check's own fixes (the 3D care animation, then its
 * commands), so the hosts run it through their usual `playFix`; otherwise [commands] go straight in. [gear] options
 * switch equipment and only work on a tank you can edit (the hosts' care path already holds those back in Watch).
 */
data class SpotOption(
    val id: String,
    val label: String,
    val glyph: String,
    val fix: CareFix? = null,
    val commands: List<Command> = emptyList(),
    val urgent: Boolean = false,
    val gear: Boolean = false,
)

/** A spot's pop-out menu: a title, one plain status line ([warn] when it needs you) and its buttons. */
data class SpotMenu(val spot: TankSpot, val title: String, val status: String, val warn: Boolean, val options: List<SpotOption>)

/** What each piece of gear says about itself and offers when tapped (pure; built from the latest snapshot). */
object TankSpots {
    /**
     * The pop-out menu for [spot] from the water [w] and gear [care] now. [lightsOn] is whether the lamps are lit;
     * [editable] is false in Watch mode, where gear switches are left out (upkeep still works, as with Fix tank).
     */
    fun menu(spot: TankSpot, w: WaterState, care: CareState, lightsOn: Boolean, editable: Boolean): SpotMenu = when (spot) {
        TankSpot.SKIMMER -> {
            val pct = (care.skimmerCup * 100).roundToInt()
            val status = when {
                !care.skimmerOn -> "Switched off. Gunk is not being pulled out."
                care.skimmerCup >= 0.9f -> "Cup $pct % full: it has almost stopped cleaning!"
                care.skimmerCup >= TankDoctor.CUP_EMPTY_AT -> "Cup $pct % full of brown gunk. Time to empty it."
                else -> "Making foam. Cup $pct % full."
            }
            SpotMenu(spot, spot.label, status, warn = !care.skimmerOn || care.skimmerCup >= TankDoctor.CUP_EMPTY_AT, options = buildList {
                add(SpotOption("empty_cup", "Empty the cup", "skimmer", urgent = care.skimmerCup >= TankDoctor.CUP_EMPTY_AT,
                    fix = CareFix(TankDoctor.Kind.EMPTY_SKIMMER, "Empty the skimmer cup", "Tip out the gunk and rinse the cup so it skims again.",
                        commands = listOf(Command.Maintain(MaintenanceTask.EMPTY_SKIMMER)))))
                if (editable) add(SpotOption(if (care.skimmerOn) "skimmer_off" else "skimmer_on", if (care.skimmerOn) "Turn off" else "Turn on",
                    if (care.skimmerOn) "plugOff" else "bubbles", commands = listOf(Command.SetParam("skimmer", if (care.skimmerOn) 0.0 else 1.0)), gear = true))
            })
        }
        TankSpot.FILTER -> {
            val days = care.sockAgeH / 24f
            val sockLine = if (!care.sockOn) "No filter sock in." else "Sock ${num(days)} days old" + if (care.sockAgeH >= TankDoctor.SOCK_SWAP_H) ", getting mucky." else "."
            val carbonLine = if (care.carbonOn) " Carbon ${(care.carbonLeft * 100).roundToInt()} % left." else ""
            val dirty = care.sockOn && care.sockAgeH >= TankDoctor.SOCK_SWAP_H
            SpotMenu(spot, spot.label, sockLine + carbonLine, warn = dirty || (care.carbonOn && care.carbonLeft < 0.15f), options = listOf(
                SpotOption("swap_sock", if (care.sockOn) "Fresh sock" else "Put in a sock", "sock", urgent = dirty,
                    fix = CareFix(TankDoctor.Kind.SWAP_SOCK, "Swap the filter sock", "A clean sock catches the floating muck before it rots.",
                        commands = listOf(Command.Maintain(MaintenanceTask.CHANGE_SOCK)))),
                SpotOption("fresh_carbon", "Fresh carbon", "carbon", urgent = care.carbonOn && care.carbonLeft < 0.15f,
                    fix = CareFix(TankDoctor.Kind.FRESH_CARBON, "Fresh carbon", "New carbon soaks up the yellow tint and smells.",
                        commands = listOf(Command.Maintain(MaintenanceTask.REPLACE_CARBON)))),
            ))
        }
        TankSpot.HEATER -> {
            val broken = !care.heaterWorking || care.heaterStuck
            val status = when {
                !care.heaterWorking -> "Broken! The water is ${c(w.tempC)} and cooling."
                care.heaterStuck -> "Stuck on! The water is ${c(w.tempC)} and heating up."
                else -> "Set to ${c(care.heaterSetC.toDouble())}. The water is ${c(w.tempC)}."
            }
            SpotMenu(spot, spot.label, status, warn = broken, options = buildList {
                if (broken) add(SpotOption("heater_fix", "Swap the heater", "thermometer", urgent = true,
                    fix = CareFix(TankDoctor.Kind.HEATER, "Fix the heater", "A new heater set to ${c(TankDoctor.HEATER_C)}.",
                        urgent = true, commands = listOf(Command.Heater(working = true, setC = TankDoctor.HEATER_C, stuckOn = false)))))
                if (editable && !broken) {
                    val set = care.heaterSetC.toDouble()
                    add(SpotOption("heater_down", "Cooler", "snowflake", commands = listOf(Command.Heater(true, setC = (set - 0.5).coerceAtLeast(22.0))), gear = true))
                    add(SpotOption("heater_up", "Warmer", "flame", commands = listOf(Command.Heater(true, setC = (set + 0.5).coerceAtMost(29.0))), gear = true))
                }
            })
        }
        TankSpot.PUMPS -> SpotMenu(spot, spot.label,
            if (w.flowOn) "Pushing water around the rocks." else "Off! The water is standing still.", warn = !w.flowOn, options = buildList {
                if (!w.flowOn) add(SpotOption("pumps_on", "Turn on", "powerhead", urgent = true,
                    fix = CareFix(TankDoctor.Kind.PUMPS, "Turn the pumps back on", "Moving water brings oxygen and food to everyone.",
                        urgent = true, commands = listOf(Command.Powerheads(true)))))
                else if (editable) add(SpotOption("pumps_off", "Turn off", "plugOff", commands = listOf(Command.Powerheads(false)), gear = true))
            })
        TankSpot.LIGHTS -> SpotMenu(spot, spot.label,
            (if (lightsOn) "On. The corals are making food from the light." else "Off. Night time in the tank.") +
                if (care.lid) " A mesh lid keeps jumpers in." else " The top is open: firefish can jump out!",
            warn = false, options = if (!editable) emptyList() else listOf(
                if (lightsOn) SpotOption("lights_off", "Lights off", "moon", commands = listOf(Command.Lights(false)), gear = true)
                else SpotOption("lights_on", "Lights on", "sun", commands = listOf(Command.Lights(true)), gear = true),
                SpotOption("lights_auto", "Auto timer", "clockSun", commands = listOf(Command.Lights(null)), gear = true),
                // research 41 §1.8: the one sure fix for jumpers is a lid
                if (care.lid) SpotOption("lid_off", "Take the lid off", "tank", commands = listOf(Command.SetParam("lid", 0.0)), gear = true)
                else SpotOption("lid_on", "Put a lid on", "tank", commands = listOf(Command.SetParam("lid", 1.0)), gear = true),
            ))
        TankSpot.TOP_OFF -> {
            val missing = ((1f - care.waterFull) * 100).roundToInt()
            val low = care.waterFull < TankDoctor.TOP_OFF_BELOW
            SpotMenu(spot, spot.label,
                when {
                    care.ato -> "Auto top-off is on: it adds fresh water as it evaporates."
                    low -> "About $missing % of the water has evaporated."
                    else -> "The water is full."
                }, warn = low && !care.ato, options = listOf(
                    SpotOption("top_off", "Top off", "droplet", urgent = low && !care.ato,
                        fix = CareFix(TankDoctor.Kind.TOP_OFF, "Top off with fresh water", "Pure RO water brings the level back.",
                            commands = listOf(Command.Maintain(MaintenanceTask.TOP_OFF)))),
                    SpotOption("water_change", "Water change", "bucket",
                        fix = CareFix(TankDoctor.Kind.WATER_CHANGE, "Change 10 % of the water", "Fresh salt water dilutes the waste.",
                            amount = 0.1, commands = listOf(Command.WaterChange(0.1, WaterState.INSTANT_OCEAN_MIX)))),
                ))
        }
    }

    private fun c(t: Double): String = "${num(t.toFloat())} °C"

    /** One decimal without java.text (core also builds for the web). */
    private fun num(v: Float): String {
        val r = (v * 10).roundToInt()
        return "${r / 10}.${kotlin.math.abs(r % 10)}"
    }
}
