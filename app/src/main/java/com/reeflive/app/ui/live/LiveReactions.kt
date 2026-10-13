package com.reeflive.app.ui.live

import androidx.compose.ui.graphics.Color
import com.reeflive.app.render.BurstKind
import com.reeflive.app.ui.components.ReefGlyphs
import com.reeflive.core.sim.Disease
import com.reeflive.core.sim.FoodKind
import com.reeflive.core.sim.MaintenanceTask
import com.reeflive.core.sim.MicroAction
import com.reeflive.core.sim.PestController
import com.reeflive.core.sim.StockGroup
import com.reeflive.core.sim.TreatmentKind
import java.util.Locale

/** How Hermie looks while he says something. */
enum class HermieMood { HAPPY, EXCITED, WORRIED, SURPRISED, SLEEPY }

/**
 * What one user action shows in the tank ([fx]: the picture over the water), what Hermie says about it, and the
 * [feedback]: the banner line, the live readout it changes and the 3D bursts in the tank itself.
 */
class Reaction(val fx: TankFx, val say: String, val mood: HermieMood = HermieMood.HAPPY, val feedback: Feedback = Feedback.from(fx))

/**
 * The one table from user actions to their in-tank picture and Hermie's line (UI_STYLE §10.2). Pure data: no Compose
 * state, so the mapping is easy to read in one place and to test. The screen pairs each [Reaction] with the sim
 * command (`ActionFxState.play(fx) { vm.feed(...) }`).
 */
object LiveReactions {
    // colours of what is dosed (bottle liquid and the surface swirl)
    private val alk = Color(0xFFB18CFF)
    private val ca = Color(0xFFE9F3FF)
    private val mg = Color(0xFF8EE66B)
    private val trace = Color(0xFFFFE14D)
    private val waste = Color(0xFF9C7A4A)
    private val algaeFood = Color(0xFF8DA34A)
    private val copper = Color(0xFFD9773A)
    private val medicine = Color(0xFF5FD7FF)
    private val phyto = Color(0xFF6DBF5A)
    private val pods = Color(0xFFD8CBA8)

    fun feed(k: FoodKind) = Reaction(
        TankFx(FxKind.FEED, if (k == FoodKind.NORI) FxSpots.NORI else FxSpots.FEED, food = k, caption = k.label),
        when (feedTool(k)) {
            FeedTool.PINCH -> "A pinch by the return. Watch the current carry it across!"
            FeedTool.SHAKER -> "Shake shake! ${k.label} for everyone."
            FeedTool.CUBE -> "A frozen ${k.label.lowercase(Locale.US)} cube. It thaws as it sinks."
            FeedTool.CLIP -> "Nori on the clip. The grazers will love it."
            FeedTool.PIPETTE -> "A squirt of ${k.label.lowercase(Locale.US)} for the corals and filter feeders."
        },
        HermieMood.EXCITED,
    )

    fun dose(paramId: String, label: String): Reaction {
        val tint = when (paramId) {
            "alk" -> alk; "ca" -> ca; "mg" -> mg; "k", "sr" -> trace
            "no3" -> waste; "po4" -> algaeFood; "copper" -> copper; else -> medicine
        }
        val say = when (paramId) {
            "alk" -> "Drip drip. Shell builder makes coral skeletons strong."
            "ca" -> "Calcium in. Corals and snails build with it."
            "mg" -> "Magnesium keeps the calcium in the water."
            "k", "sr" -> "A tiny bit of trace for bright corals."
            "no3", "po4" -> "That is waste. Algae will like it more than the fish."
            else -> "In it goes."
        }
        val effect = when (paramId) {
            "alk" -> "dKH rising: coral skeletons grow"
            "ca" -> "Calcium rising"
            "mg" -> "Magnesium rising"
            "k", "sr" -> "Trace level rising"
            "no3" -> "Nitrate rising: algae food"
            "po4" -> "Phosphate rising: algae food"
            "copper" -> "Copper rising: deadly for shrimp and corals"
            else -> "Spreading through the tank"
        }
        return Reaction(
            TankFx(FxKind.DOSE, FxSpots.DOSE, tint = tint, caption = label), say,
            feedback = Feedback("Dosing $label", effect, listOf(Feedback.burst(BurstKind.DYE, FxSpots.DOSE, tint)), watch = paramId.takeIf { SandboxParams.byId(it) != null }, durationMs = 3200),
        )
    }

    fun waterChange(fraction: Double) = Reaction(
        TankFx(FxKind.WATER_CHANGE, FxSpots.SIPHON, amount = fraction.toFloat(), caption = "Water change ${(fraction * 100).toInt()} %"),
        "Out with the old water, in with fresh salt water!",
        HermieMood.EXCITED,
    )

    fun heater(working: Boolean) = if (working) {
        Reaction(TankFx(FxKind.HEATER_FIX, FxSpots.HEATER, caption = "Heater fixed"), "Warm and cosy again.")
    } else {
        Reaction(TankFx(FxKind.HEATER_BREAK, FxSpots.HEATER, caption = "Heater broke"), "Uh oh, the heater broke. The water will cool down.", HermieMood.WORRIED)
    }

    fun lights(on: Boolean?) = when (on) {
        true -> Reaction(TankFx(FxKind.LIGHTS_ON, FxSpots.LIGHT, caption = "Lights on"), "Lights on! Corals love a sunny day.")
        false -> Reaction(TankFx(FxKind.LIGHTS_OFF, FxSpots.LIGHT, caption = "Lights off"), "Lights out. Sleepy time for the fish.", HermieMood.SLEEPY)
        null -> Reaction(TankFx(FxKind.LIGHTS_AUTO, FxSpots.LIGHT, caption = "Normal light schedule"), "Back to the normal day and night.")
    }

    fun pump(rowId: String, on: Boolean): Reaction {
        val spot = if (rowId == "pump_right") FxSpots.PUMP_RIGHT else FxSpots.PUMP_LEFT
        val side = if (rowId == "pump_right") "Right" else "Left"
        return if (on) Reaction(TankFx(FxKind.PUMP_ON, spot, caption = "$side pump on"), "Whoosh! The water is moving again.")
        else Reaction(TankFx(FxKind.PUMP_OFF, spot, caption = "$side pump off"), "The pump stopped. Less wave, less air.", HermieMood.WORRIED)
    }

    fun pumpsTonight() = Reaction(
        TankFx(FxKind.PUMP_FAIL_TONIGHT, FxSpots.PUMP_LEFT, caption = "Pumps stop tonight 23:00"),
        "Tonight at 11 the pumps will stop for six hours. Speed up time to see it.",
        HermieMood.WORRIED,
    )

    /** An on/off equipment or scenario row. */
    fun toggle(rowId: String, on: Boolean): Reaction {
        val f = SandboxParams.byId(rowId)?.let { SandboxFriendly.of(it) }
        val name = f?.title ?: rowId
        val glyph = f?.glyph
        return if (rowId.startsWith("sc_")) {
            Reaction(TankFx(FxKind.SPARKLE, FxSpots.CENTRE, glyph = glyph, caption = "$name: ${if (on) "on" else "off"}", tint = if (on) Color(0xFFFF5C6C) else Color(0xFF8EE66B)), if (on) "A test idea is switched on. Watch closely." else "Test idea switched off.", HermieMood.SURPRISED)
        } else {
            val short = name.substringBefore(" (")
            val (effect, burst) = deviceEffect(rowId, on)
            val fx = TankFx(if (on) FxKind.DEVICE_ON else FxKind.DEVICE_OFF, FxSpots.SUMP, glyph = glyph, caption = "$short ${if (on) "on" else "off"}")
            Reaction(
                fx,
                if (on) "$short is on and working." else "$short is off.",
                if (on) HermieMood.HAPPY else HermieMood.SURPRISED,
                feedback = Feedback.from(fx).withEffect(effect).let { fb -> if (burst != null) Feedback(fb.title, fb.effect, listOf(burst), null, fb.durationMs) else fb },
            )
        }
    }

    /** What switching an equipment row does to the water, and its burst at the return (null = the default sparkle). */
    private fun deviceEffect(rowId: String, on: Boolean): Pair<String, com.reeflive.app.render.TankBurst?> {
        val at = FxSpots.DOSE
        return when (rowId) {
            "eq_skimmer" -> if (on) "Fine bubbles pull waste out as foam" to Feedback.burst(BurstKind.MICROBUBBLES, at) else "Waste builds up a little faster" to null
            "eq_sock" -> if (on) "Catches the floating muck" to Feedback.burst(BurstKind.SUCTION, at) else "Muck stays in the water longer" to null
            "eq_carbon" -> if (on) "Soaks up yellow tint and smells: the water clears" to Feedback.burst(BurstKind.CLARITY, at) else "Yellow tint can build up again" to null
            "eq_refugium" -> if (on) "The algae garden soaks up nitrate and phosphate" to Feedback.burst(BurstKind.CLARITY, at, Color(0xFF8EE66B)) else "Nutrients are no longer taken up there" to null
            "eq_uv" -> if (on) "Zaps germs and algae spores floating past" to Feedback.burst(BurstKind.UV_GLOW, at) else "Germs in the water are not zapped" to null
            else -> (if (on) "Running" else "Stopped") to null
        }
    }

    /**
     * A slider or quick button moved [rowId] from [old] to [new] (display units). Keyed by the row, so dragging plays
     * one picture, not one per step.
     */
    fun param(rowId: String, old: Double, new: Double): Reaction? {
        val r = paramPicture(rowId, old, new) ?: return null
        val watched = rowId.takeIf { !it.startsWith("eq_") && !it.startsWith("sc_") && !it.startsWith("pump_") && SandboxParams.byId(it) != null }
        val fb = r.feedback.withWatch(watched ?: r.feedback.watch).let { fb ->
            when {
                rowId == "flow" -> fb.withEffect(if (new >= old) "Stronger flow: the pump jets reach further" else "Gentler flow: the jets shorten")
                rowId.startsWith("spectrum") -> fb.plus(listOf(Feedback.burst(BurstKind.LIGHT_MOTES, FxSpots.LIGHT, amount = 0.5f))).withEffect("The light colour shifts")
                rowId == "photoperiod" -> fb.withEffect("Longer or shorter days from now on")
                else -> fb
            }
        }
        return Reaction(r.fx, r.say, r.mood, fb)
    }

    private fun paramPicture(rowId: String, old: Double, new: Double): Reaction? {
        val up = new >= old
        val key = "param:$rowId"
        return when (rowId) {
            "temp" -> Reaction(TankFx(if (up) FxKind.THERMO_UP else FxKind.THERMO_DOWN, FxSpots.THERMO, key = key, caption = String.format(Locale.US, "%.1f °C", new)), if (up) "Warmer water. Not too hot!" else "Cooler water. Brrr.", if (new > 30) HermieMood.WORRIED else HermieMood.HAPPY)
            "sal" -> if (up) Reaction(TankFx(FxKind.SALT_UP, FxSpots.SURFACE, key = key, caption = String.format(Locale.US, "Salt %.3f", new)), "A scoop of salt.")
            else Reaction(TankFx(FxKind.SALT_DOWN, FxSpots.SURFACE, key = key, caption = String.format(Locale.US, "Salt %.3f", new)), "Fresh water makes it less salty.")
            "light" -> lights(if (up) true else new > 0.5).let { Reaction(TankFx(if (up) FxKind.LIGHTS_ON else FxKind.LIGHTS_OFF, FxSpots.LIGHT, key = key, caption = "Light ${new.toInt()} %"), it.say, it.mood) }
            "flow" -> Reaction(TankFx(if (up) FxKind.PUMP_ON else FxKind.PUMP_OFF, FxSpots.PUMP_RIGHT, key = key, caption = "Flow ${new.toInt()} %"), if (up) "More waves!" else "Calmer water.")
            "pump_left", "pump_right" -> pump(rowId, new >= 0.5)
            "alk", "ca", "mg", "k", "sr", "copper", "no3", "po4" -> dose(rowId, SandboxParams.byId(rowId)?.let { SandboxFriendly.of(it).title } ?: rowId).let {
                Reaction(TankFx(FxKind.DOSE, FxSpots.DOSE, tint = it.fx.tint, key = key, caption = it.fx.caption), it.say, it.mood)
            }
            "ph", "do", "nh3", "no2", "algae", "photoperiod", "spectrum_royal", "spectrum_blue", "spectrum_white", "spectrum_violet" -> {
                val def = SandboxParams.byId(rowId)
                val f = def?.let { SandboxFriendly.of(it) }
                Reaction(TankFx(FxKind.SPARKLE, if (rowId.startsWith("spectrum") || rowId == "photoperiod") FxSpots.LIGHT else FxSpots.CENTRE, glyph = f?.glyph, key = key, caption = f?.title), "${f?.title ?: "That"} changed.")
            }
            else -> if (rowId.startsWith("eq_") || rowId.startsWith("sc_")) toggle(rowId, new >= 0.5) else null
        }
    }

    fun preset(p: SandboxPreset): Reaction = when (p) {
        SandboxPreset.HEATER_STUCK -> Reaction(TankFx(FxKind.HEATER_STUCK, FxSpots.HEATER, caption = "Heater stuck on"), "The heater won't switch off! It keeps getting hotter.", HermieMood.WORRIED)
        SandboxPreset.POWER_OUTAGE -> Reaction(TankFx(FxKind.POWER_OUT, FxSpots.LIGHT, caption = "Power cut for 4 hours"), "Power cut! No pumps, no heater, no lights.", HermieMood.SURPRISED)
        SandboxPreset.OVERFEEDING -> Reaction(
            TankFx(FxKind.FEED, FxSpots.FEED, food = FoodKind.PELLET, caption = "Way too much food"), "That is a LOT of food. Leftovers turn into waste.", HermieMood.WORRIED,
            Feedback("Way too much food", "Six big feedings in half an hour: leftovers rot into ammonia", listOf(Feedback.burst(BurstKind.SPLASH, FxSpots.FEED, amount = 3f)), watch = "nh3", durationMs = 3600),
        )
        SandboxPreset.MISSED_WATER_CHANGES -> Reaction(
            TankFx(FxKind.ALERT, FxSpots.CENTRE, glyph = ReefGlyphs.calendarX, tint = Color(0xFF9C7A4A), caption = "Two months, no water change"), "Nobody changed the water for ages. Yuck.", HermieMood.WORRIED,
            Feedback("Two months, no water change", "Nutrients pile up, minerals run low, algae spreads", listOf(Feedback.burst(BurstKind.HAZE, FxSpots.CENTRE, waste), Feedback.burst(BurstKind.SILT, FxSpots.SAND, waste)), watch = "no3", durationMs = 3000),
        )
        SandboxPreset.SALINITY_CRASH -> Reaction(TankFx(FxKind.SALT_DOWN, FxSpots.SURFACE, caption = "Salt crash"), "Too much fresh water! The salt is dropping fast.", HermieMood.SURPRISED).let {
            Reaction(it.fx, it.say, it.mood, it.feedback.plus(listOf(Feedback.burst(BurstKind.FRESH, FxSpots.SURFACE, amount = 1.5f))).withEffect("The top-off floods fresh water in for half an hour"))
        }
        SandboxPreset.COPPER_OVERDOSE -> Reaction(TankFx(FxKind.DOSE, FxSpots.DOSE, tint = copper, caption = "Copper overdose"), "Too much copper. Shrimp and corals hate it!", HermieMood.WORRIED).let {
            Reaction(it.fx, it.say, it.mood, it.feedback.withWatch("copper").withEffect("Copper floods in: deadly for shrimp and corals"))
        }
    }

    fun heatWave() = Reaction(TankFx(FxKind.THERMO_UP, FxSpots.THERMO, caption = "Heat wave"), "A heat wave! Warm water holds less air.", HermieMood.WORRIED).let {
        Reaction(it.fx, it.say, it.mood, it.feedback.withEffect("Warming 3 °C over the next day: less oxygen in warm water"))
    }

    fun infect(d: Disease) = Reaction(TankFx(FxKind.ALERT, FxSpots.CENTRE, caption = SandboxFriendly.diseaseLabel(d)), "Oh no, a fish caught ${SandboxFriendly.diseaseLabel(d).lowercase(Locale.US)}. Can you spot it?", HermieMood.WORRIED)

    fun addAiptasia() = Reaction(TankFx(FxKind.PEST_ADD, FxSpots.AIPTASIA, glyph = ReefGlyphs.pestAnemone, caption = "Pest anemone"), "A pest anemone popped up! It stings corals.", HermieMood.SURPRISED)
    fun removeAiptasia() = Reaction(TankFx(FxKind.PEST_REMOVE, FxSpots.AIPTASIA, glyph = ReefGlyphs.pestAnemone, caption = "Pest removed"), "Zap! One pest anemone gone.")

    fun pestControl(c: PestController) = when (c) {
        PestController.PEPPERMINT_SHRIMP -> Reaction(TankFx(FxKind.BAG, FxSpots.RELEASE, glyph = ReefGlyphs.shrimp, tint = Color(0xFFFF7AA8), caption = "Peppermint shrimp"), "Peppermint shrimp eat pest anemones. Yum!", HermieMood.EXCITED)
        PestController.BERGHIA -> Reaction(TankFx(FxKind.BAG, FxSpots.RELEASE, glyph = ReefGlyphs.slug, tint = Color(0xFFB18CFF), caption = "Berghia sea slugs"), "Berghia slugs only eat pest anemones.", HermieMood.EXCITED)
    }

    fun treatment(k: TreatmentKind, start: Boolean): Reaction {
        val name = k.label
        if (!start) return Reaction(
            TankFx(FxKind.SPARKLE, FxSpots.DOSE, glyph = SandboxFriendly.treatmentGlyph(k), caption = "Stopped $name"), "$name stopped.",
            feedback = Feedback("Stopped $name", "The treatment ends; the water recovers", listOf(Feedback.burst(BurstKind.CLARITY, FxSpots.DOSE))),
        )
        return when (k) {
            TreatmentKind.UV -> toggle("eq_uv", true).let { Reaction(it.fx, "UV on: it zaps germs floating in the water.", it.mood, it.feedback) }
            TreatmentKind.HYPOSALINITY -> Reaction(TankFx(FxKind.SALT_DOWN, FxSpots.SURFACE, caption = name), "Less salt for a few weeks: ich can't stand it.")
            TreatmentKind.TANK_TRANSFER -> Reaction(TankFx(FxKind.NET, FxSpots.CENTRE, caption = name), "Moving the fish to clean tanks, again and again.")
            TreatmentKind.COPPER -> Reaction(TankFx(FxKind.DOSE, FxSpots.DOSE, tint = copper, caption = name), "Copper medicine. Careful: it hurts inverts.", HermieMood.WORRIED).let {
                Reaction(it.fx, it.say, it.mood, it.feedback.withWatch("copper").withEffect("Copper ramps up over two days, then holds"))
            }
            else -> Reaction(TankFx(FxKind.DOSE, FxSpots.DOSE, tint = medicine, caption = name), "Medicine time. Get well soon, fishies.").let {
                Reaction(it.fx, it.say, it.mood, it.feedback.withEffect("Medicine spreading through the tank"))
            }
        }
    }

    fun micro(a: MicroAction): Reaction {
        val tint = when (a) {
            MicroAction.DOSE_PHYTO -> phyto
            MicroAction.DOSE_ROTIFERS -> Color(0xFFE9F3FF)
            MicroAction.TREAT_FLATWORMS, MicroAction.TREAT_FENBENDAZOLE, MicroAction.TREAT_PRAZIQUANTEL -> medicine
            else -> pods
        }
        val podsFb = Feedback(a.label, "Tiny pods hop off toward the rock to hide and breed", listOf(Feedback.burst(BurstKind.PODS, FxSpots.FEED)), durationMs = 3000)
        val fb = when (a) {
            MicroAction.DOSE_TISBE, MicroAction.DOSE_TIGGER, MicroAction.DOSE_TIGRIOPUS -> podsFb
            MicroAction.DOSE_AMPHIPODS, MicroAction.DOSE_MYSIDS -> Feedback(a.label, "Released at the surface; they scurry into the rock", listOf(Feedback.burst(BurstKind.PODS, FxSpots.RELEASE, amount = 1.3f), Feedback.burst(BurstKind.BUBBLES, FxSpots.RELEASE, pods, 0.5f)), durationMs = 3000)
            MicroAction.DOSE_PHYTO -> Feedback(a.label, "A green haze drifts through the flow: food for pods and filter feeders", listOf(Feedback.burst(BurstKind.HAZE, FxSpots.FEED, phyto)), durationMs = 3000)
            MicroAction.DOSE_ROTIFERS -> Feedback(a.label, "A faint cloud of rotifers for corals and tiny mouths", listOf(Feedback.burst(BurstKind.HAZE, FxSpots.FEED, Color(0xFFE9F3FF), 0.7f)), durationMs = 3000)
            MicroAction.LIVE_ROCK -> Feedback(a.label, "New rock settles in a puff of silt, full of tiny critters", listOf(Feedback.burst(BurstKind.SILT, FxSpots.ROCK)), durationMs = 3000)
            MicroAction.FLATWORM_FRAG -> Feedback(a.label, "Hitchhikers on a new frag: watch the rock", listOf(Feedback.burst(BurstKind.SILT, FxSpots.ROCK, amount = 0.4f)), durationMs = 3000)
            else -> Feedback(a.label, "Medicine spreading through the tank", listOf(Feedback.burst(BurstKind.HAZE, FxSpots.FEED, medicine)), durationMs = 3000)
        }
        return when (a) {
            MicroAction.LIVE_ROCK -> Reaction(TankFx(FxKind.ROCKS, FxSpots.ROCK, caption = a.label), "New live rock, full of tiny critters.")
            MicroAction.FLATWORM_FRAG -> Reaction(TankFx(FxKind.ALERT, FxSpots.ROCK, glyph = ReefGlyphs.slug, caption = a.label), "Hitchhikers! Flatworms came in on a frag.", HermieMood.SURPRISED)
            MicroAction.DOSE_AMPHIPODS, MicroAction.DOSE_MYSIDS -> Reaction(TankFx(FxKind.BAG, FxSpots.RELEASE, glyph = ReefGlyphs.shrimp, tint = pods, caption = a.label), "Little critters for the clean-up crew.", HermieMood.EXCITED)
            else -> Reaction(TankFx(FxKind.PIPETTE, FxSpots.FEED, tint = tint, caption = a.label), if (tint == medicine) "A careful dose of medicine." else "Tiny food for tiny life!")
        }.let { Reaction(it.fx, it.say, it.mood, fb) }
    }

    fun maintain(t: MaintenanceTask) = when (t) {
        MaintenanceTask.CHANGE_SOCK -> Reaction(
            TankFx(FxKind.MAINTAIN, FxSpots.SUMP, glyph = ReefGlyphs.sock, caption = "Fresh filter sock"), "Dirty sock out, clean sock in!",
            feedback = Feedback("Fresh filter sock", "A clean sock pulls floating muck out again", listOf(Feedback.burst(BurstKind.SUCTION, FxSpots.DOSE))),
        )
        MaintenanceTask.REPLACE_CARBON -> Reaction(
            TankFx(FxKind.MAINTAIN, FxSpots.SUMP, glyph = ReefGlyphs.carbon, caption = "Fresh carbon"), "New carbon soaks up yellow and smells.",
            feedback = Feedback("Fresh carbon", "New carbon clears the yellow tint", listOf(Feedback.burst(BurstKind.CLARITY, FxSpots.DOSE))),
        )
        MaintenanceTask.HARVEST_MACRO -> Reaction(TankFx(FxKind.SNIP, FxSpots.SUMP, glyph = ReefGlyphs.refugium, caption = "Algae garden trimmed"), "Snip snip! Trimming takes waste out of the water.")
    }

    fun shells() = Reaction(TankFx(FxKind.SHELLS, FxSpots.SAND, caption = "Spare shells"), "New shells for hermits like me! Moving day!", HermieMood.EXCITED)
    fun rocks() = Reaction(TankFx(FxKind.ROCKS, FxSpots.ROCK, caption = "Rocks moved"), "Rocks moved. Everyone has to find a new home spot.", HermieMood.SURPRISED)
    fun acclimate() = Reaction(TankFx(FxKind.HEARTS, FxSpots.CENTRE, caption = "Everyone settles in"), "Everyone is used to the water now.")
    fun tapGlass(at: FxPoint = FxSpots.GLASS) = Reaction(TankFx(FxKind.TAP_GLASS, at), "Knock knock! Please don't scare the fish too much.", HermieMood.SURPRISED)
    fun predation(on: Boolean) = Reaction(TankFx(FxKind.SPARKLE, FxSpots.CENTRE, glyph = ReefGlyphs.fish, tint = if (on) Color(0xFFFF5C6C) else Color(0xFF8EE66B), caption = if (on) "Fish can eat each other" else "Everyone is safe"), if (on) "Big fish may eat small ones now." else "No eating each other. Phew!", if (on) HermieMood.WORRIED else HermieMood.HAPPY)
    fun resize(bigger: Boolean, name: String) = Reaction(TankFx(FxKind.GROW, FxSpots.CENTRE, amount = if (bigger) 1f else 0f, key = "resize", caption = name), if (bigger) "$name is growing!" else "$name got smaller.")
    fun remove(name: String) = Reaction(TankFx(FxKind.NET, FxSpots.CENTRE, caption = "Bye, $name"), "$name went out in the net. Reset brings them back.")

    fun addLife(card: StockCard, count: Int) = Reaction(
        TankFx(
            FxKind.BAG, FxSpots.RELEASE,
            glyph = when (card.group) { StockGroup.FISH -> ReefGlyphs.fish; StockGroup.INVERTS -> ReefGlyphs.shrimp; StockGroup.CORALS -> ReefGlyphs.branch },
            tint = when (card.group) { StockGroup.FISH -> Color(0xFFFFB13D); StockGroup.INVERTS -> Color(0xFFFF7AA8); StockGroup.CORALS -> Color(0xFF8EE66B) },
            amount = count.toFloat(),
            caption = if (count == 1) card.name else "$count × ${card.name}",
        ),
        if (count == 1) "Welcome, ${card.name}! Float the bag, then let it out." else "$count new ${card.name}s. That's a crowd!",
        HermieMood.EXCITED,
    )

    fun addSimple(name: String) = Reaction(TankFx(FxKind.BAG, FxSpots.RELEASE, caption = name), "Welcome, $name!", HermieMood.EXCITED)

    fun speed(x: Double) = Reaction(
        TankFx(FxKind.SPEED, FxSpots.LIGHT, amount = when { x >= 1000 -> 4f; x >= 240 -> 3f; x >= 60 -> 2f; else -> 1f }, caption = formatSpeed(x)),
        if (x <= 1.0) "Back to real time." else "Zoom! ${formatSpeed(x)} speed.",
        if (x <= 1.0) HermieMood.HAPPY else HermieMood.EXCITED,
    )

    fun reset() = Reaction(TankFx(FxKind.RESET, FxSpots.CENTRE, caption = "Back to your real tank"), "Whoosh! Everything is back like your real tank.", HermieMood.EXCITED)
    fun timelapse() = Reaction(TankFx(FxKind.TIMELAPSE, FxSpots.CENTRE, caption = "Time-lapse"), "Let's see the future!", HermieMood.EXCITED)
    fun cleanGlass(on: Boolean) = Reaction(TankFx(FxKind.SPARKLE, FxSpots.GLASS, glyph = ReefGlyphs.flipper, tint = Color(0xFF8EE66B), caption = if (on) "Slide to clean the glass" else "Glass cleaning done"), if (on) "Rub the glass with the scraper. Squeaky clean!" else "Sparkly glass!", HermieMood.HAPPY).let {
        Reaction(it.fx, it.say, it.mood, it.feedback.withEffect(if (on) "Drag on the front glass: the scraper wipes the algae film off" else "Clear glass: a better view"))
    }
}
