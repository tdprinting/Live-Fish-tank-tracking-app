package com.reeflive.core.sim

import com.reeflive.core.params.ParameterTable
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The tank check behind "Fix my tank": reads the water and the equipment ([CareState]) and lists what a careful keeper
 * would do today, most urgent first, each with the commands that do it. Targets are the soft-coral table
 * ([ParameterTable.SOFT_CORAL], research 01 §1.1); doses stay inside the daily swing limits the shelf already checks
 * ([DoseCheck.DAILY_MAX], 01 §2.1 / 05 §9-10); salinity moves at most 0.001 SG a day (01 §1.1); the skimmer cup and the
 * filter sock follow the upkeep cadence of 01 §3.5, the top-off 01 §3.4. Pure: the screens play each fix's
 * animation ([CareAct]) and send [CareFix.commands] when it reaches the water.
 *
 * Fixes are planned in order on a projected copy of the water, so a water change counts before the doses (it moves
 * alk, Ca and Mg itself) and a top-off counts before the salinity check (most high salinity is evaporation).
 */
object TankDoctor {
    private val T = ParameterTable.SOFT_CORAL.associateBy { it.id }

    /** Heater set point a soft-coral reef runs at (25.5 °C = 78 °F, the middle of the 76-80 °F table). */
    const val HEATER_C = 25.5
    /** Skimmer cup fill (0..1) worth emptying: past half the cup the foam soon backs up (01 §3.5 weekly). */
    const val CUP_EMPTY_AT = 0.6f
    /** A sock older than this starts rotting what it caught (WaterSim SOCK_ROT ramps from 48 h; 01 §3.5 2-3 days). */
    const val SOCK_SWAP_H = 48f
    /** Carbon this spent (share of its fresh adsorption left) is due for a swap (01 §3.5: every 2-4 weeks). */
    const val CARBON_SWAP_LEFT = 0.35f
    /** Tank volume below this share of the system means visible evaporation (01 §3.4). */
    const val TOP_OFF_BELOW = 0.985f
    /** Largest water change one fix does (01 §3.1: 10-25 % routine, bigger only in an emergency). */
    const val MAX_CHANGE = 0.25
    const val MIN_CHANGE = 0.10

    /** RO/DI water: what goes in at a top-off or when swapping some salt water out to lower the salinity. */
    val RO_WATER = WaterState(
        tempC = 25.0, sg = 1.000, ph = 7.0, alk = 0.0, ca = 0.0, mg = 0.0, nh3Total = 0.0, no2 = 0.0, no3 = 0.0, po4 = 0.0,
        doMgL = 7.5, k = 0.0, sr = 0.0, i = 0.0, detritus = 0.0, doc = 0.05, silicate = 0.02,
    )

    /**
     * Strong salt water (Instant Ocean mixed to SG 1.035, its minerals scaled with the salt) for raising a low salinity
     * a step at a time: a small swap lifts SG without a big change to anything else.
     */
    val STRONG_MIX: WaterState = WaterState.INSTANT_OCEAN_MIX.let { m ->
        val k = 35.0 / 25.0
        m.copy(sg = 1.035, alk = m.alk * k, ca = m.ca * k, mg = m.mg * k, k = m.k * k, sr = m.sr * k, i = m.i * k)
    }

    /** What the fix looks like in the tank (the renderer's CareScene plays one per act). */
    enum class Kind { EMPTY_SKIMMER, SWAP_SOCK, FRESH_CARBON, WATER_CHANGE, TOP_OFF, LOWER_SALT, RAISE_SALT, DOSE_MG, DOSE_CA, DOSE_ALK, HEATER, PUMPS, FEED_CORALS }

    /**
     * Everything the tank needs now, most urgent first; empty when all is well. [w] and [care] come from the latest
     * snapshot ([SimSnapshot.water], [SimSnapshot.care]).
     */
    fun diagnose(w: WaterState, care: CareState): List<CareFix> {
        val out = ArrayList<CareFix>()
        val p = w.copy()                      // the water as it will be after the fixes planned so far

        // ---- life support first: heat and water movement
        val tLo = fToC(T.getValue("temp").ideal.lo); val tHi = fToC(T.getValue("temp").acceptable.hi)
        val heaterOff = !care.heaterWorking || care.heaterStuck
        val setOff = kotlin.math.abs(care.heaterSetC - HEATER_C) > 0.6
        if (heaterOff || setOff || w.tempC < tLo - 0.1) {
            val why = when {
                !care.heaterWorking -> "The heater is broken and the water is ${c(w.tempC)}. A new heater set to ${c(HEATER_C)} warms it back slowly."
                care.heaterStuck -> "The heater is stuck on and cooking the tank (${c(w.tempC)}). Swap it and set ${c(HEATER_C)}."
                w.tempC < tLo -> "The water is a bit cold (${c(w.tempC)}). Set the heater to ${c(HEATER_C)}: it warms about a degree a day."
                else -> "The heater is set to ${c(care.heaterSetC.toDouble())}. Reef fish and corals like ${c(HEATER_C)}."
            }
            out += CareFix(Kind.HEATER, "Fix the heater", why, urgent = heaterOff || w.tempC < fToC(T.getValue("temp").acceptable.lo) || w.tempC > tHi,
                commands = listOf(Command.Heater(working = true, setC = HEATER_C, stuckOn = false)))
        }
        if (!w.flowOn) {
            out += CareFix(Kind.PUMPS, "Turn the pumps back on", "The water is standing still: no flow means less oxygen and food drifting past the corals.",
                urgent = true, commands = listOf(Command.Powerheads(true)))
        }

        // ---- the skimmer and the sock: emptying and swapping is routine upkeep, and cheap to get wrong
        val skimmerNeeded = !care.skimmerOn && (w.doc > 3.0 || w.doMgL < 5.5)
        if (care.skimmerCup >= CUP_EMPTY_AT || skimmerNeeded) {
            val pct = (care.skimmerCup * 100).roundToInt()
            val cmds = ArrayList<Command>().apply {
                add(Command.Maintain(MaintenanceTask.EMPTY_SKIMMER))
                if (skimmerNeeded) add(Command.SetParam("skimmer", 1.0))
            }
            val why = if (skimmerNeeded) "The skimmer is off and gunk is building up in the water. Empty its cup and switch it back on."
            else if (care.skimmerCup >= 0.9f) "The skimmer cup is $pct % full of brown gunk: it has almost stopped cleaning. Tip it out and rinse it."
            else "The skimmer cup is $pct % full of brown gunk. Empty it before it stops cleaning the water."
            out += CareFix(Kind.EMPTY_SKIMMER, if (skimmerNeeded) "Empty the skimmer and turn it on" else "Empty the skimmer cup", why,
                urgent = care.skimmerCup >= 0.95f, commands = cmds)
        }
        if (care.sockOn && care.sockAgeH >= SOCK_SWAP_H) {
            val days = care.sockAgeH / 24f
            out += CareFix(Kind.SWAP_SOCK, "Swap the filter sock",
                "The filter sock has been in ${num(days.toDouble(), 1)} days. Old muck in it starts rotting back into the water, so put in a clean one.",
                urgent = care.sockAgeH >= 120f, commands = listOf(Command.Maintain(MaintenanceTask.CHANGE_SOCK)))
        } else if (!care.sockOn && w.detritus > 0.6) {
            out += CareFix(Kind.SWAP_SOCK, "Put in a filter sock", "Lots of muck is drifting around. A felt filter sock catches it before it rots.",
                commands = listOf(Command.Maintain(MaintenanceTask.CHANGE_SOCK)))
        }

        // ---- evaporation: a top-off with RO/DI first (it also brings a high salinity back down)
        if (!care.ato && care.waterFull < TOP_OFF_BELOW) {
            val missing = ((1f - care.waterFull) * 100).roundToInt().coerceAtLeast(1)
            out += CareFix(Kind.TOP_OFF, "Top off with fresh water",
                "About $missing % of the water has evaporated, leaving the salt behind. Pure RO water brings the level and the salt back.",
                commands = listOf(Command.Maintain(MaintenanceTask.TOP_OFF)))
            val k = care.waterFull.toDouble().coerceIn(0.5, 1.0)
            p.sg = 1 + (p.sg - 1) * k; p.alk *= k; p.ca *= k; p.mg *= k; p.no3 *= k; p.po4 *= k; p.nh3Total *= k; p.no2 *= k
        }

        // ---- water change: waste that only dilution removes
        val no3Hi = T.getValue("no3").ideal.hi; val po4Hi = T.getValue("po4").acceptable.hi
        val waste = p.nh3Total > T.getValue("nh3").acceptable.hi * 2.5 || p.no2 > T.getValue("no2").acceptable.hi * 2
        val need = maxOf(
            if (p.no3 > no3Hi) 1 - 10.0 / p.no3 else 0.0,
            if (p.po4 > po4Hi) 1 - 0.06 / p.po4 else 0.0,
            if (waste) MAX_CHANGE else 0.0,
        )
        if (need > 0) {
            val f = (((need.coerceIn(MIN_CHANGE, MAX_CHANGE)) * 20).roundToInt() / 20.0)   // 10, 15, 20 or 25 %
            val mix = WaterState.INSTANT_OCEAN_MIX
            val reasons = buildList {
                if (waste) add("there is ammonia or nitrite (fish poison)")
                if (p.no3 > no3Hi) add("nitrate is high (${num(p.no3, 0)} ppm, best under ${num(no3Hi, 0)})")
                if (p.po4 > po4Hi) add("phosphate is high (${num(p.po4, 2)} ppm, best under ${num(po4Hi, 2)})")
            }
            out += CareFix(Kind.WATER_CHANGE, "Change ${(f * 100).roundToInt()} % of the water",
                "Swap old water for fresh salt water because ${reasons.joinToString(" and ")}.",
                urgent = waste || p.no3 > T.getValue("no3").acceptable.hi || p.po4 > (T.getValue("po4").dangerHigh ?: 0.15),
                amount = f, commands = listOf(Command.WaterChange(f, mix)))
            mixInto(p, f, mix)
        }

        // ---- salinity, a step a day
        val sgLo = T.getValue("sal").ideal.lo; val sgHi = T.getValue("sal").ideal.hi
        val sgStep = T.getValue("sal").maxChangePerDay ?: 0.001
        if (p.sg > sgHi + 0.0003) {
            val target = max(sgHi - 0.0005, p.sg - sgStep)
            val f = ((p.sg - target) / (p.sg - RO_WATER.sg)).coerceIn(0.0, MAX_CHANGE)
            out += CareFix(Kind.LOWER_SALT, "Swap a little salt water for fresh",
                "The water is too salty (SG ${num(p.sg, 3)}). Take out a jug of tank water and pour in pure RO water: about 0.001 a day is safe.",
                urgent = p.sg >= (T.getValue("sal").dangerHigh ?: 1.029), amount = f, commands = listOf(Command.WaterChange(f, RO_WATER)))
            mixInto(p, f, RO_WATER)
        } else if (p.sg < sgLo - 0.0003) {
            val target = min(sgLo + 0.0005, p.sg + sgStep)
            val f = ((target - p.sg) / (STRONG_MIX.sg - p.sg)).coerceIn(0.0, MAX_CHANGE)
            out += CareFix(Kind.RAISE_SALT, "Add a little extra salt",
                "The water is not salty enough (SG ${num(p.sg, 3)}). Swap some for stronger salt water: about 0.001 a day is safe.",
                urgent = p.sg <= (T.getValue("sal").dangerLow ?: 1.020), amount = f, commands = listOf(Command.WaterChange(f, STRONG_MIX)))
            mixInto(p, f, STRONG_MIX)
        }

        // ---- doses: magnesium first (it keeps calcium and alk in the water), each inside a day's safe swing
        dose(out, p, "mg", Kind.DOSE_MG, "Add magnesium", 1300.0, 50.0, "Magnesium is low (%s ppm). It helps calcium and alkalinity stay in the water.", 0)
        dose(out, p, "ca", Kind.DOSE_CA, "Add calcium", 420.0, 10.0, "Calcium is low (%s ppm). Corals and the pink coralline build with it.", 0)
        dose(out, p, "alk", Kind.DOSE_ALK, "Add alkalinity", 8.5, 0.25, "Alkalinity is low (%s dKH). Corals need it to build their skeletons.", 1)

        // ---- starving: a reef with no nutrients at all bleaches its soft corals
        if (p.no3 < (T.getValue("no3").dangerLow ?: 1.0) && p.po4 < T.getValue("po4").acceptable.lo) {
            out += CareFix(Kind.FEED_CORALS, "Feed the corals",
                "Nitrate and phosphate are almost zero, so the corals are hungry. A spoon of coral food helps.",
                commands = listOf(Command.Feed(FoodKind.CORAL_FOOD, FoodKind.CORAL_FOOD.typicalDoseGrams)))
        }
        return out.sortedBy { if (it.urgent) 0 else 1 }
    }

    private fun dose(out: MutableList<CareFix>, p: WaterState, id: String, kind: Kind, title: String, target: Double, round: Double, why: String, decimals: Int) {
        val now = p.get(id) ?: return
        val lo = T.getValue(id).ideal.lo
        if (now >= lo - 1e-9) return
        val cap = DoseCheck.DAILY_MAX[id] ?: return
        val delta = (kotlin.math.floor(min(target - now, cap) / round) * round)
        if (delta <= 0) return
        // never dose into a mix that would crust (high Ca × alk with low Mg); the water change or Mg comes first
        if (DoseCheck.check(id, delta, p) == DoseCheck.Verdict.PRECIPITATION) return
        out += CareFix(kind, title, why.replace("%s", num(now, decimals)) + if (target - now > cap + 1e-9) " A day's safe dose now, more tomorrow." else "",
            urgent = now < (T.getValue(id).dangerLow ?: 0.0), amount = delta, commands = listOf(Command.Dose(id, delta)))
        when (id) { "alk" -> p.alk += delta; "ca" -> p.ca += delta; "mg" -> p.mg += delta }
    }

    /** The same mixing as WaterSim.waterChange, for the projection. */
    private fun mixInto(p: WaterState, f: Double, mix: WaterState) {
        val q = SupplyPreview.afterWaterChange(p, f, mix)
        p.sg = q.sg; p.alk = q.alk; p.ca = q.ca; p.mg = q.mg; p.no3 = q.no3; p.po4 = q.po4; p.nh3Total = q.nh3Total; p.tempC = q.tempC
        p.no2 *= (1 - f)
    }

    private fun fToC(f: Double) = (f - 32) * 5 / 9
    private fun c(t: Double) = "${num(t, 1)} °C"

    /** Fixed decimals without java.text (core also builds for the web). */
    private fun num(v: Double, decimals: Int): String {
        if (decimals <= 0) return v.roundToInt().toString()
        var m = 1.0
        repeat(decimals) { m *= 10 }
        val r = kotlin.math.round(v * m).toLong()
        val neg = r < 0
        val a = kotlin.math.abs(r)
        val whole = a / m.toLong()
        val frac = (a % m.toLong()).toString().padStart(decimals, '0')
        return (if (neg) "-" else "") + "$whole.$frac"
    }
}

/**
 * One thing the tank needs. [title] and [why] are plain words for the fix list; [amount] is the dose (dKH, ppm) or
 * the share of water swapped; [commands] go to the sim when the animation reaches the water.
 */
data class CareFix(
    val kind: TankDoctor.Kind,
    val title: String,
    val why: String,
    val urgent: Boolean = false,
    val amount: Double = 0.0,
    val commands: List<Command>,
)
