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
    val pos: Vec3,
    val normal: Vec3,
    swaySeed: Double,
    /** Sandbox copy: offset of this colony from its home anchor (the anchored model is drawn moved by it). */
    val offset: Vec3 = Vec3.ZERO,
) {
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
    /** Last time a disturbance fragmented this aiptasia polyp (AiptasiaModel). */
    var lastFragAt = -1e9

    fun toPose() = CoralPose(
        id, extension.toFloat(), bleach.toFloat(), brown.toFloat(), algaeCover.toFloat(), swayPhase.toFloat(),
        species.id, anchor, flowSpeed.toFloat(), tissue.toFloat(), skeleton.toFloat(),
        offsetX = offset.x.toFloat(), offsetY = offset.y.toFloat(), offsetZ = offset.z.toFloat(),
        homeX = (pos.x - offset.x).toFloat(), homeY = (pos.y - offset.y).toFloat(), homeZ = (pos.z - offset.z).toFloat(),
        size = size.toFloat(), cover = cover.toFloat(),
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

    /** Bleaching threshold for the DHW accumulator (tank analogue of the local max monthly mean + 1 °C). */
    const val DHW_THRESHOLD_C = 29.0
    val SG_SHOCK = ToleranceRange(optHi = 0.4, tolHi = 1.0, lethalHi = 2.0)   // in units of 0.004 SG
    const val SG_STEP = 0.004
    const val ALK_STEP = 2.0
    /** Ca and Mg bands for the mild soft-coral stress (03 C1 row "Low/unstable calcium/magnesium"; 05 §10 bands). */
    val CA_SOFT = ToleranceRange(optLo = 380.0, tolLo = 330.0, lethalLo = 200.0)
    val MG_SOFT = ToleranceRange(optLo = 1200.0, tolLo = 1050.0, lethalLo = 800.0)

    fun step(
        c: Coral, w: WaterState, lightIntensity: Double, minutesSinceOn: Double?, minutesUntilOff: Double?,
        dtMin: Double, simTimeSec: Double, events: MutableList<SimEvent>, flowStrength: Double = if (w.flowOn) 1.0 else 0.0,
        pathogenStress: Double = 0.0,
    ) {
        val p = params(c.kind)
        if (c.acclSg.isNaN()) { c.acclSg = w.sg; c.acclAlk = w.alk; c.acclLight = lightIntensity.coerceAtLeast(0.6) }
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
        val (sCa, _) = HealthModel.stressOf(w.ca, CA_SOFT)
        val (sMg, _) = HealthModel.stressOf(w.mg, MG_SOFT)
        val caMgWeight = if (c.kind == CoralKind.ORGAN_PIPE) 0.8 else if (c.kind == CoralKind.MACRO || c.kind == CoralKind.AIPTASIA) 0.0 else 0.3
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
        val parts = doubleArrayOf(sTemp, sSg, sSgShock, sAlk, sAlkShock * 0.5, sPh, sNh3, sPo4 * 0.3, sLight, sLightShock * 0.6, sFlow, sCu, sK * 0.5, sSr * 0.2, sCa * caMgWeight, sMg * caMgWeight, c.neighborStress, sH2s, sPath * 0.6)
        var prod = 1.0
        for (s in parts) prod *= (1 - s)
        c.stress = 1 - prod

        c.disturb = max(0.0, c.disturb - 0.25 * dtMin)
        val flowExt = 0.5 + 0.5 * smoothstep(0.05, 0.6, flowStrength)
        var target = lightOpen(c.kind, minutesSinceOn, minutesUntilOff, c.species.id) * (1 - 0.8 * c.stress) * flowExt
        target *= (1 - c.disturb).coerceIn(0.0, 1.0) * (1 - c.overgrown)
        // leathers close up for a few days every few weeks while they shed a waxy film
        if (shedding(c, simTimeSec, flowStrength)) target *= SHED_EXTENSION
        // acute heat (research 08 §5): heavy mucus and retraction from the first sign of necrosis
        if (c.necrosis > 0.02) target *= 0.2
        if (c.melting) target = 0.0
        val tau = if (c.disturb > 0.3 || sSgShock > 0.4) 2.0 else 20.0   // snap shut when touched or osmotically shocked
        c.extension += (target - c.extension) * (dtMin / tau).coerceAtMost(1.0)

        // Heat: NOAA DHW accumulation (4 DHW significant, 8 severe) plus a fast acute response above the tolerance band.
        val wk = dtMin / (7 * 1440.0)
        c.dhw = if (w.tempC > DHW_THRESHOLD_C) c.dhw + (w.tempC - DHW_THRESHOLD_C) * wk else max(0.0, c.dhw - 0.5 * wk)
        val dhwTarget = smoothstep(3.0, 8.0, c.dhw)
        // Acute pathway (research 08 §5, distinct from DHW): above T_necro tissue sloughs instead of bleaching.
        HeatModel.coralStep(c, w.tempC, minutesSinceOn == null, dtMin)
        val acute = HeatModel.coralAcute(c, w.tempC)
        val starving = if (w.no3 < 0.5 && w.po4 < 0.01) 0.00002 else 0.0     // ultra-low nutrients pale soft corals over weeks
        c.bleach += (0.0008 * max(0.0, sTemp - 0.5) * (if (acute) 0.0 else 1.0) + max(0.0, dhwTarget - c.bleach) / (3 * 1440.0) + 0.0003 * sLight + 0.0004 * sLightShock + starving -
            (if (c.stress < 0.15) 0.00001 else 0.0)) * dtMin
        c.bleach = c.bleach.coerceIn(0.0, 1.0)
        if (c.bleach > 0.5 && !c.bleachingReported) { c.bleachingReported = true; events += SimEvent.CoralBleaching(c.id, simTimeSec) }
        if (c.bleach < 0.2) c.bleachingReported = false
        c.brown += (0.0002 * ((w.po4 - 0.1) / 0.2).coerceIn(0.0, 1.0) + 0.0001 * ((w.no3 - 25) / 25).coerceIn(0.0, 1.0) - 0.00003) * dtMin
        c.brown = c.brown.coerceIn(0.0, 1.0)
        c.tissue += (-0.00005 * (if (c.bleach > 0.8) 1.0 else 0.0) - 0.00005 * smoothstep(8.0, 12.0, c.dhw) - 0.0002 * sNh3 -
            0.00003 * c.algaeCover - 0.0003 * sCu - (if (sgLethal) 0.002 else 0.0) - 0.0002 * max(0.0, sPh - 0.6) -
            0.00002 * c.neighborStress - 0.00003 * c.overgrown - PATHOGEN_TISSUE_LOSS * sPath - 0.0003 * sH2s +
            (if (c.stress < 0.1 && c.tissue < 1) 0.00002 else 0.0)) * dtMin
        // bare skeleton does not regrow tissue; more than half bare and the colony dies (08 §6, §7.2)
        if (c.skeleton > 0.5) c.tissue -= dtMin / 360.0
        c.tissue = c.tissue.coerceIn(0.0, 1.0 - c.skeleton)
        c.algaeCover += (0.001 * w.algaeHair + 0.0005 * w.cyano - 0.0003 * c.algaeCover) * dtMin / 60
        c.algaeCover = c.algaeCover.coerceIn(0.0, 1.0)
    }
}
