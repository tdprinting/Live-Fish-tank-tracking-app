package com.reeflive.core.sim

import com.reeflive.core.geom.Vec3
import com.reeflive.core.geom.smoothstep
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max

/** One coral / macroalgae colony (SIMULATION §3.6). */
class Coral(
    val id: String,
    val species: SimSpecies,
    val nickname: String,
    val anchor: String,
    pos: Vec3,
    normal: Vec3,
    swaySeed: Double,
    /** Sandbox copy: offset of this colony from its home anchor (the anchored model is drawn moved by it). */
    val offset: Vec3 = Vec3.ZERO,
) {
    /** Where the colony sits now: [restPos] carried along by its rock when the rock moved (dynamic rockscape). */
    var pos: Vec3 = pos
    var normal: Vec3 = normal
    /** Where it was placed, on the rock as built. */
    var restPos: Vec3 = pos
    var restNormal: Vec3 = normal
    /** The rock piece it grows on ([RockData.pieces]); −1 none (sand, glass), −2 not looked up yet. */
    var rockPiece = -2

    val kind: CoralKind = species.coralKind
    var extension = 0.8
    var bleach = 0.0
    var brown = 0.0
    var tissue = 1.0
    var algaeCover = 0.0
    var swayPhase = swaySeed
    var flowSpeed = 0.0
    var stress = 0.0
    var bleachingReported = false
    /** Disturbance (fish contact, food on it) retracts briefly; decays per minute. */
    var disturb = 0.0
    /** Recently caught food (coral powder, cyclops, plankton; 0..1): a small growth boost that fades over ~2 days. */
    var fed = 0.0
    /** Degree heating weeks above the bleaching threshold (NOAA logic, research 05 §1). */
    var dhw = 0.0
    /** Acclimated SG / alkalinity / light (rate-of-change shock); NaN until the first chem tick. */
    var acclSg = Double.NaN
    var acclAlk = Double.NaN
    var acclLight = Double.NaN
    /** Acute heat necrosis D_coral and the bare-skeleton fraction it has exposed (research 08 §5; irreversible). */
    var necrosis = 0.0
    var skeleton = 0.0
    val melting: Boolean get() = tissue < 0.2
    /** The biggest stress right now ([CoralNeed] ordinal), −1 when it is fine. */
    var need = -1
    /** Sim time it died (tissue gone; [CoralHealth.CLEAR_DAYS] later it is cleared away), NaN while alive. */
    var diedAt = Double.NaN
    /** Stony corals: night tentacles out 0..1 (Euphyllia sweepers, LPS feeding tentacles; [StonyCorals]). */
    var tentacles = 0.0
    /** Euphyllia: how developed its sweeper tentacles are 0..1 (grown against a neighbour in reach). */
    var sweepers = 0.0

    // ---- growth and competition (CoralGrowth; coverage gaps C-1, C-2) ----
    /** Colony linear size relative to the placed colony (1 = as placed; mats: √(area / starting area)). */
    var size = 1.0
    /** Encrusting colonies: share of the rock surface they can reach that the mat now covers (0..1); trees: size share of max. */
    var cover = 0.0
    /** Mat radius along the rock surface, cm (encrusting colonies; 0 until the first growth step). */
    var radiusCm = 0.0
    var patch: SurfacePatch? = null
    /** Geodesic distance along this mat's rock to other colonies (positions never move). */
    val geoCache = HashMap<String, Double>()
    /** Chemical/sting pressure from neighbours (0..1) and the fraction smothered by an overgrowing mat. */
    var neighborStress = 0.0
    var overgrown = 0.0
    /** Present when the tank was loaded (its neighbours' pressure then is its acclimated baseline). */
    var established = false
    var baseline: Pair<Double, Double>? = null
    /** GSP: recent touches that close the polyps around them ([GspPolyps.poke]), [GspPolyps.STRIDE] values each. */
    val pokes = DoubleArray(GspPolyps.MAX_POKES * GspPolyps.STRIDE)
    /** When this colony went into the tank (sim s; NaN until its first step), for [GspPolyps.settleFactor]. */
    var placedAtSec = Double.NaN
    /** Last time a disturbance fragmented this aiptasia polyp (AiptasiaModel). */
    var lastFragAt = -1e9
    /** Aiptasia being killed by a keeper's treatment ([AiptasiaModel.treat]): which one, and sim seconds until gone. */
    var treatment: AiptasiaTreatment? = null
    var treatLeftSec = 0.0
    /** Light here relative to mid-water ([CoralSpots.lightAt]; My tank) and the rock revision it was worked out at. */
    var lightHere = Double.NaN
    var lightAtRev = -1L

    fun toPose(simTimeSec: Double = 0.0) = CoralPose(
        id, extension.toFloat(), bleach.toFloat(), brown.toFloat(), algaeCover.toFloat(), swayPhase.toFloat(),
        species.id, anchor, flowSpeed.toFloat(), tissue.toFloat(), skeleton.toFloat(),
        offsetX = offset.x.toFloat(), offsetY = offset.y.toFloat(), offsetZ = offset.z.toFloat(),
        homeX = (restPos.x - offset.x).toFloat(), homeY = (restPos.y - offset.y).toFloat(), homeZ = (restPos.z - offset.z).toFloat(),
        size = size.toFloat(), cover = cover.toFloat(), rockPiece = rockPiece.coerceAtLeast(-1),
        restX = restPos.x.toFloat(), restY = restPos.y.toFloat(), restZ = restPos.z.toFloat(),
        restNx = restNormal.x.toFloat(), restNy = restNormal.y.toFloat(), restNz = restNormal.z.toFloat(),
        matRadius = if (kind == CoralKind.GSP) GspPolyps.matRadius(this).toFloat() else 0f,
        pokes = GspPolyps.pokesFor(this, simTimeSec),
        stress = stress.toFloat(), need = need, fed = fed.toFloat(),
        drawScale = CoralHealth.drawScale(this).toFloat(), tentacles = tentacles.toFloat(), bleachTint = CoralHealth.profile(species).tint, name = nickname.ifBlank { species.common },
    )
}

object CoralSim {
    data class Params(val temp: ToleranceRange, val nightExtension: Double, val flowSensitive: Boolean, val needsFlow: Boolean, val lightHi: Double, val lightLo: Double)

    fun params(k: CoralKind): Params = when (k) {
        // temp: research 05 §1 — normal 26–28, stress from 30, bleaching trigger 30–31 °C in a tank (est.)
        CoralKind.KENYA -> Params(ToleranceRange(24.0, 28.0, 22.5, 29.5, 18.0, 32.0), 0.5, true, false, 0.85, 0.15)
        CoralKind.GSP -> Params(ToleranceRange(24.0, 28.0, 22.5, 30.0, 18.0, 32.5), 0.15, false, true, 0.95, 0.15)
        CoralKind.ZOA -> Params(ToleranceRange(24.0, 28.0, 22.5, 30.0, 18.0, 32.5), 0.3, false, false, 0.9, 0.2)
        CoralKind.CLOVE -> Params(ToleranceRange(24.0, 28.0, 22.5, 29.5, 18.0, 32.0), 0.35, false, true, 0.85, 0.1)
        CoralKind.MUSHROOM -> Params(ToleranceRange(23.5, 28.0, 22.0, 30.0, 18.0, 32.5), 0.2, false, false, 0.9, 0.05)
        CoralKind.MACRO -> Params(ToleranceRange(22.0, 28.5, 20.0, 30.5, 17.0, 33.0), 1.0, false, false, 1.1, 0.0)
        CoralKind.ORGAN_PIPE -> Params(ToleranceRange(24.5, 28.0, 23.5, 29.5, 20.0, 31.5), 0.1, false, false, 0.95, 0.35)
        CoralKind.AIPTASIA -> Params(ToleranceRange(22.0, 29.0, 20.0, 31.0, 17.0, 34.0), 1.0, false, false, 1.1, 0.0)
        // Leathers (tee_tank_photos.md, [M] care guides): moderate light (PAR ~100-200), moderate indirect flow; polyps
        // retract at night; a direct jet makes them retract and fold (flowSensitive), stagnant water lets detritus and
        // the shed film sit on the cap (needsFlow).
        CoralKind.LEATHER -> Params(ToleranceRange(24.0, 28.0, 22.5, 29.5, 18.0, 32.0), 0.25, true, true, 0.95, 0.15)
        // Euphyllia (stony_corals.md §1): 24-26.7 °C, PAR ~75-200 (low-moderate light), low-moderate indirect flow: a
        // hard jet tears the tissue off the skeleton (flowSensitive). Day tentacles stay half out at night.
        CoralKind.EUPHYLLIA -> Params(ToleranceRange(24.0, 27.0, 22.5, 29.0, 18.0, 31.5), 0.5, true, false, 0.8, 0.2)
        // Meaty LPS: similar temperatures, PAR ~50-150, gentle flow; the mantle shrinks at night as feeding tentacles open.
        CoralKind.LPS -> Params(ToleranceRange(24.0, 27.0, 22.5, 29.0, 18.0, 31.5), 0.6, true, false, 0.75, 0.15)
        CoralKind.GENERIC -> Params(ToleranceRange(24.0, 28.0, 22.5, 29.5, 18.0, 32.0), 0.3, false, false, 0.9, 0.15)
    }

    /**
     * Extension driven by the light cycle: opens 1–2 h after lights-on, closes 30 min before
     * lights-off (03 §B).
     */
    fun lightOpen(k: CoralKind, minutesSinceOn: Double?, minutesUntilOff: Double?, speciesId: String? = null): Double {
        val p = params(k)
        val night = nightExtensionOf(k, speciesId)
        if (minutesSinceOn == null || minutesUntilOff == null) return night
        val open = smoothstep(45.0, 110.0, minutesSinceOn) * smoothstep(0.0, 30.0, minutesUntilOff)
        return night + (1 - night) * open
    }

    /**
     * Extension kept through the night. Hairy mushrooms (*Rhodactis*) keep their discs open after lights-out in Tee's
     * tank (owner observation; the clowns sleep on them), unlike the smooth Discosoma that half close.
     */
    fun nightExtensionOf(k: CoralKind, speciesId: String?): Double =
        if (speciesId != null && speciesId.lowercase().startsWith("rhodactis")) HAIRY_SHROOM_NIGHT_EXTENSION else params(k).nightExtension

    const val HAIRY_SHROOM_NIGHT_EXTENSION = 0.9

    /**
     * Leather wax shedding ([M] Lobophytum / Sarcophyton care guides, tee_tank_photos.md): "close once every couple
     * weeks to a month and develop a film covering that eventually gets shed off"; stronger flow clears it faster, a
     * shed lasting over a week points at flow or alkalinity. Each colony has its own phase (from its id).
     */
    @Ev(Evidence.DESIGN_ESTIMATE, "tee_tank_photos.md leather shedding [M]")
    const val SHED_PERIOD_DAYS = 24.0
    const val SHED_DAYS = 3.0
    /** Extension while shedding (polyps in, cap shrunk and glazed). */
    const val SHED_EXTENSION = 0.1

    fun shedding(c: Coral, simTimeSec: Double, flowStrength: Double = 1.0): Boolean {
        if (c.kind != CoralKind.LEATHER || c.tissue <= 0.0) return false
        val period = SHED_PERIOD_DAYS * 86400.0
        val offset = ((c.id.hashCode().toLong() and 0x7fffffffL) % 1000L) / 1000.0 * period
        // weak flow: the film hangs on about twice as long (up to ~a week)
        val days = SHED_DAYS * (if (flowStrength < 0.3) 2.0 else 1.0)
        return ((simTimeSec + offset) % period) < days * 86400.0
    }
    /** H2S band for corals (research 18 §3.3 [K]). */
    val H2S_CORAL = ToleranceRange(optHi = 0.005, tolHi = 0.02, lethalHi = 0.15)
    /** Tissue loss per minute at full Vibrio stress (white-syndrome-like lesion front, research 18 §5.5; est.). */
    @Ev(Evidence.DESIGN_ESTIMATE, "17 §5.5 [K]")
    const val PATHOGEN_TISSUE_LOSS = 0.0001

    /** Tissue loss per minute under severe all-round stress (on top of the bleached-tissue loss: ~5 weeks alone; coral_health §2, est.). */
    @Ev(Evidence.DESIGN_ESTIMATE, "coral_health §2")
    const val RECEDE_PER_MIN = 0.00002

    /** Lit minutes in a day (10 h photoperiod): [CoralProfile.darkFade] is per day of too-dim light. */
    const val DAYLIGHT_MIN = 600.0

    /** [CoralNeed] for each entry of the stress list in [step], in order. */
    private val NEED_OF_PART = arrayOf(
        CoralNeed.HEAT, CoralNeed.SALT, CoralNeed.SALT, CoralNeed.ALK, CoralNeed.ALK, CoralNeed.ALK, CoralNeed.POISON,
        CoralNeed.NUTRIENTS, CoralNeed.LIGHT_HIGH, CoralNeed.LIGHT_HIGH, CoralNeed.FLOW, CoralNeed.POISON, CoralNeed.SALT,
        CoralNeed.CA_MG, CoralNeed.CA_MG, CoralNeed.CA_MG, CoralNeed.NEIGHBOR, CoralNeed.POISON, CoralNeed.SICK,
        CoralNeed.IODINE, CoralNeed.HUNGRY, CoralNeed.ALGAE,
    )

    /** Bleaching threshold for the DHW accumulator (tank analogue of the local max monthly mean + 1 °C). */
    const val DHW_THRESHOLD_C = 29.0
    val SG_SHOCK = ToleranceRange(optHi = 0.4, tolHi = 1.0, lethalHi = 2.0)   // in units of 0.004 SG
    const val SG_STEP = 0.004
    const val ALK_STEP = 2.0
    /** Ca and Mg bands for the mild soft-coral stress (03 C1 row "Low/unstable calcium/magnesium"; 05 §10 bands). */
    val CA_SOFT = ToleranceRange(optLo = 380.0, tolLo = 330.0, lethalLo = 200.0)
    val MG_SOFT = ToleranceRange(optLo = 1200.0, tolLo = 1050.0, lethalLo = 800.0)
    /** Stony corals build their skeleton from it: retail care sheets ask Ca 400-450, Mg 1250-1350 (stony_corals.md). */
    val CA_STONY = ToleranceRange(optLo = 400.0, tolLo = 350.0, lethalLo = 250.0)
    val MG_STONY = ToleranceRange(optLo = 1250.0, tolLo = 1100.0, lethalLo = 850.0)

    fun step(
        c: Coral, w: WaterState, lightIntensity: Double, minutesSinceOn: Double?, minutesUntilOff: Double?,
        dtMin: Double, simTimeSec: Double, events: MutableList<SimEvent>, flowStrength: Double = if (w.flowOn) 1.0 else 0.0,
        pathogenStress: Double = 0.0,
    ) {
        val p = params(c.kind)
        if (c.acclSg.isNaN()) { c.acclSg = w.sg; c.acclAlk = w.alk }
        if (c.acclLight.isNaN()) c.acclLight = lightIntensity.coerceAtLeast(0.6)
        c.acclSg += (w.sg - c.acclSg) * (1 - exp(-dtMin / 2880.0))
        c.acclAlk += (w.alk - c.acclAlk) * (1 - exp(-dtMin / 1440.0))
        if (minutesSinceOn != null && lightIntensity > 0.05) c.acclLight += (lightIntensity - c.acclLight) * (1 - exp(-dtMin / 30240.0))
        val (sTemp, _) = HealthModel.stressOf(w.tempC, p.temp)
        val (sSg, _) = HealthModel.stressOf(w.sg, ToleranceRange(1.023, 1.027, 1.020, 1.029, 1.015, 1.033))
        val (sSgShock, sgLethal) = HealthModel.stressOf(abs(w.sg - c.acclSg) / SG_STEP, SG_SHOCK)
        val (sAlk, _) = HealthModel.stressOf(w.alk, ToleranceRange(7.0, 11.0, 6.0, 12.0, 4.0, 14.0))
        val (sAlkShock, _) = HealthModel.stressOf(abs(w.alk - c.acclAlk) / ALK_STEP, HealthModel.SHOCK_RANGE)
        val (sPh, _) = HealthModel.stressOf(w.ph, ToleranceRange(7.9, 8.4, 7.7, 8.55, 7.3, 8.9))
        val (sNh3, _) = HealthModel.stressOf(w.nh3Free, ToleranceRange(optHi = 0.02, tolHi = 0.1, lethalHi = 0.6))
        val (sPo4, _) = HealthModel.stressOf(w.po4, ToleranceRange(optHi = 0.1, tolHi = 0.3, lethalHi = 1.0))
        val (sCu, _) = HealthModel.stressOf(w.copper, ToleranceRange(optHi = 0.02, tolHi = 0.1, lethalHi = 0.3))
        // research 18: H2S from stirred deep sand, Vibrio-type infection in warm water (V. coralliilyticus ≥ 28 °C)
        val sH2s = if (w.h2s > 0) HealthModel.stressOf(w.h2s, H2S_CORAL).first else 0.0
        val sPath = pathogenStress.coerceIn(0.0, 1.0)
        // research 01 §1.1 / 05 §13: potassium out of 340-460 ppm stresses corals; low strontium (< 6) only mildly (skeletal element)
        val (sK, _) = HealthModel.stressOf(w.k, HealthModel.POTASSIUM)
        val (sSr, _) = HealthModel.stressOf(w.sr, HealthModel.STRONTIUM)
        // research 03 C1: low/unstable Ca or Mg mildly stresses soft corals (they "don't need it but benefit"); calcifiers more (05 §10)
        val (sCa, _) = HealthModel.stressOf(w.ca, if (c.kind.stony) CA_STONY else CA_SOFT)
        val (sMg, _) = HealthModel.stressOf(w.mg, if (c.kind.stony) MG_STONY else MG_SOFT)
        // Iodine (coral_health §2): soft corals that shed (leathers, Kenya) use it up; carbon strips it and only water
        // changes or a dose bring it back. Used up below ~0.025 ppm (est.).
        val pr = CoralHealth.profile(c.species)
        val sI = pr.iodineNeed * ((0.025 - w.i) / 0.025).coerceIn(0.0, 1.0)
        // Hunger: in ultra-clean water a coral lives on what it catches; caught food ([Coral.fed]) makes up for it.
        // Organ pipe, clove and mushrooms lean on food more than the light-fed leathers and Kenya (coral_health §2).
        val lowNut = 0.5 * ((1.0 - w.no3) / 1.0).coerceIn(0.0, 1.0) + 0.5 * ((0.02 - w.po4) / 0.02).coerceIn(0.0, 1.0)
        val sHunger = if (c.kind == CoralKind.MACRO || c.kind == CoralKind.AIPTASIA) 0.0 else (0.2 + 0.8 * pr.feedNeed) * (1 - c.fed.coerceIn(0.0, 1.0)) * lowNut
        val caMgWeight = if (c.kind == CoralKind.ORGAN_PIPE || c.kind.stony) 0.8 else if (c.kind == CoralKind.MACRO || c.kind == CoralKind.AIPTASIA) 0.0 else 0.3
        // stony corals: retail guides warn that alkalinity swings over ~1 dKH a day start tissue recession (Euphyllia)
        val alkShockWeight = if (c.kind.stony) 1.0 else 0.5
        val sLight = if (minutesSinceOn == null) 0.0 else when {
            lightIntensity > p.lightHi -> ((lightIntensity - p.lightHi) / 0.15).coerceIn(0.0, 1.0)
            lightIntensity < p.lightLo && lightIntensity > 0.05 -> ((p.lightLo - lightIntensity) / p.lightLo).coerceIn(0.0, 1.0) * 0.5
            else -> 0.0
        }
        // Sudden light jump vs the 3-week acclimated level (research 05 §14): retraction then paling.
        val sLightShock = if (minutesSinceOn == null || p.lightLo <= 0.0) 0.0 else ((lightIntensity - c.acclLight - 0.2) / 0.4).coerceIn(0.0, 1.0)
        // Flow (research 05 §15–16): low flow reduces extension; Kenya dislikes strong direct flow.
        var sFlow = 0.0
        if (p.flowSensitive && (c.flowSpeed > 25 || flowStrength > 1.5)) sFlow = 0.3
        if (p.needsFlow && flowStrength < 0.3) sFlow = 0.4
        val parts = doubleArrayOf(sTemp, sSg, sSgShock, sAlk, sAlkShock * alkShockWeight, sPh, sNh3, sPo4 * 0.3, sLight, sLightShock * 0.6, sFlow, sCu, sK * 0.5, sSr * 0.2, sCa * caMgWeight, sMg * caMgWeight, c.neighborStress, sH2s, sPath * 0.6, sI, sHunger * 0.5, if (c.kind == CoralKind.AIPTASIA) 0.0 else c.algaeCover * 0.3)
        var prod = 1.0
        var top = 0.15; var worst = -1
        for (i in parts.indices) {
            val s = parts[i]
            prod *= (1 - s)
            if (s > top) { top = s; worst = i }
        }
        c.stress = 1 - prod
        val day = minutesSinceOn != null
        // too dim while the lights are on: the algae in the tissue starve and the colony pales over weeks (coral_health §2)
        val dark = if (!day || p.lightLo <= 0.0) 0.0 else ((p.lightLo - lightIntensity) / p.lightLo).coerceIn(0.0, 1.0)
        // at night the light-driven reasons rest: keep the day's answer rather than flicker to "fine"
        if (worst >= 0 || day) c.need = if (worst < 0) -1 else NEED_OF_PART[worst].let { n ->
            if (n == CoralNeed.HEAT && w.tempC < (p.temp.optLo ?: 24.0)) CoralNeed.COLD.ordinal
            else if (n == CoralNeed.LIGHT_HIGH && lightIntensity < p.lightLo) CoralNeed.LIGHT_LOW.ordinal else n.ordinal
        }

        c.disturb = max(0.0, c.disturb - 0.25 * dtMin)
        val flowExt = 0.5 + 0.5 * smoothstep(0.05, 0.6, flowStrength)
        var target = lightOpen(c.kind, minutesSinceOn, minutesUntilOff, c.species.id) * (1 - 0.8 * c.stress) * flowExt
        target *= (1 - c.disturb).coerceIn(0.0, 1.0) * (1 - c.overgrown)
        // a newly placed GSP keeps its polyps mostly in for its first hours
        target *= GspPolyps.settleFactor(c, simTimeSec)
        // leathers close up for a few days every few weeks while they shed a waxy film
        if (shedding(c, simTimeSec, flowStrength)) target *= SHED_EXTENSION
        // acute heat (research 08 §5): heavy mucus and retraction from the first sign of necrosis
        if (c.necrosis > 0.02) target *= 0.2
        if (c.melting) target = 0.0
        val tau = if (c.disturb > 0.3 || sSgShock > 0.4) 2.0 else 20.0   // snap shut when touched or osmotically shocked
        c.extension += (target - c.extension) * (dtMin / tau).coerceAtMost(1.0)
        StonyCorals.stepTentacles(c, minutesSinceOn, minutesUntilOff, dtMin)

        // Heat: NOAA DHW accumulation (4 DHW significant, 8 severe) plus a fast acute response above the tolerance band.
        val wk = dtMin / (7 * 1440.0)
        c.dhw = if (w.tempC > DHW_THRESHOLD_C) c.dhw + (w.tempC - DHW_THRESHOLD_C) * wk else max(0.0, c.dhw - 0.5 * wk)
        val dhwTarget = smoothstep(3.0, 8.0, c.dhw)
        // Acute pathway (research 08 §5, distinct from DHW): above T_necro tissue sloughs instead of bleaching.
        HeatModel.coralStep(c, w.tempC, minutesSinceOn == null, dtMin)
        val acute = HeatModel.coralAcute(c, w.tempC)
        // ultra-low nutrients and no food pale soft corals over weeks
        val starving = 0.00002 * sHunger
        val heat = (0.0008 * max(0.0, sTemp - 0.5) * (if (acute) 0.0 else 1.0) + max(0.0, dhwTarget - c.bleach) / (3 * 1440.0)) * pr.heatBleach
        val recover = if (c.need >= 0) 0.0 else (if (c.stress < 0.15) 0.00001 else if (c.stress < 0.3) 0.000004 else 0.0) * pr.recovery
        c.bleach += (heat + (0.0003 * (if (dark > 0) 0.0 else sLight) + 0.0004 * sLightShock) * pr.lightBleach + pr.darkFade * dark / DAYLIGHT_MIN + starving - recover) * dtMin
        c.bleach = c.bleach.coerceIn(0.0, 1.0)
        if (c.bleach > 0.5 && !c.bleachingReported) { c.bleachingReported = true; events += SimEvent.CoralBleaching(c.id, simTimeSec) }
        if (c.bleach < 0.2) c.bleachingReported = false
        c.brown += (0.0002 * ((w.po4 - 0.1) / 0.2).coerceIn(0.0, 1.0) + 0.0001 * ((w.no3 - 25) / 25).coerceIn(0.0, 1.0) - 0.00003) * dtMin
        c.brown = c.brown.coerceIn(0.0, 1.0)
        // a long bleached, starving or badly stressed colony recedes: polyps stay shut, tissue melts back (coral_health §2)
        // bleached tissue starves while the stress lasts; once the cause is fixed it mostly waits for its algae to come back
        val loss = 0.00004 * smoothstep(0.7, 1.0, c.bleach) * (0.15 + 0.85 * smoothstep(0.2, 0.6, c.stress)) + 0.00005 * smoothstep(8.0, 12.0, c.dhw) + 0.0002 * sNh3 +
            0.00003 * c.algaeCover + 0.0003 * sCu + (if (sgLethal) 0.002 else 0.0) + 0.0002 * max(0.0, sPh - 0.6) +
            0.00002 * c.neighborStress + 0.00003 * c.overgrown + PATHOGEN_TISSUE_LOSS * sPath + 0.0003 * sH2s +
            (if (c.kind == CoralKind.AIPTASIA) 0.0 else RECEDE_PER_MIN * smoothstep(0.65, 0.95, c.stress)) + 0.00002 * sHunger * c.bleach
        c.tissue += (-loss * pr.recede + (if (c.stress < 0.1 && c.tissue < 1 && c.tissue > CoralHealth.DEAD_TISSUE) 0.00002 * pr.recovery else 0.0)) * dtMin
        // bare skeleton does not regrow tissue; more than half bare and the colony dies (08 §6, §7.2)
        if (c.skeleton > 0.5) c.tissue -= dtMin / 360.0
        c.tissue = c.tissue.coerceIn(0.0, 1.0 - c.skeleton)
        c.algaeCover += (0.001 * w.algaeHair + 0.0005 * w.cyano - 0.0003 * c.algaeCover) * dtMin / 60
        c.algaeCover = c.algaeCover.coerceIn(0.0, 1.0)
    }
}
