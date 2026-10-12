package com.reeflive.core.sim

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * Water chemistry model (SIMULATION §2). [step] advances `dt` seconds (normally 60). All rates
 * below are per hour unless noted; constants marked (tune) were set with the SimRunner logs and
 * are documented in SIMULATION §2 "Tuned constants".
 */
class WaterSim(val config: TankConfig, initial: WaterState = WaterState()) {
    val s: WaterState = initial.copy(volumeL = initial.volumeL.takeIf { it > 0 } ?: config.systemLiters)

    // ---- external drivers (set by Simulation before each step) ----
    var lightIntensity = 0.0
    /** Σ massG^0.8 × metabolicMult over living animals. */
    var bioMassIndex = 0.0
    var coralDemandDkhPerDay = 0.15
    var coralBiomass = 0.9
    /** Hair-algae removal by grazers in the last interval, as hair units per hour. */
    var grazingPerHour = 0.0

    // ---- equipment ----
    var heaterWorking = true
    var heaterStuckOn = false
    var heaterSetC = config.heaterSetC
    private var heaterPowered = false
    var ato = config.atoEnabled
    /** Manual top-off cadence (days) when ATO is off; 0 disables. */
    var manualTopOffDays = 3
    private var hoursSinceTopOff = 0.0

    // ---- pending inputs ----
    private var pendingFeedTan = 0.0   // ppm TAN still to be released from food (first order, τ = 2 h)
    private var pendingFeedPo4 = 0.0
    private val pendingDose = HashMap<String, Double>()

    // ---- nitrogen accounting (mg N) for the mass-balance test (SIMULATION §8.1) ----
    var nInputMg = 0.0; private set
    var nExportMg = 0.0; private set

    fun nitrogenInSystemMg(): Double =
        (s.nh3Total * N_TAN + s.no2 * N_NO2 + s.no3 * N_NO3 + s.detritus * N_TAN + pendingFeedTan * N_TAN) * s.volumeL

    // ===================================================================================
    fun step(dt: Double = 60.0) {
        val h = dt / 3600.0
        applyPendingDoses(dt)
        temperature(h)
        evaporation(h)
        nitrogen(h)
        phosphate(h)
        carbonate(h)
        gases(h)
        algae(h)
        s.nh3Free = ChemFns.freeNh3(s.nh3Total, s.ph, s.tempC)
        s.lightPar = lightIntensity
        clampAll()
    }

    private fun temperature(h: Double) {
        val t = s.tempC
        heaterPowered = when {
            heaterStuckOn -> true
            !heaterWorking -> false
            t < heaterSetC - 0.3 -> true
            t > heaterSetC + 0.1 -> false
            else -> heaterPowered
        }
        val heater = if (heaterPowered) config.heaterWatts else 0.0
        val lightHeat = LIGHT_W_PER_FIXTURE * lightIntensity * 2
        val pumpHeat = if (s.flowOn) PUMP_W else 0.0
        val loss = K_LOSS * (t - config.roomC)
        val evapCool = evapLitersPerHour() * 1000 * 0.63 * EVAP_HEAT_FRACTION
        val dTdt = (heater + lightHeat + pumpHeat - loss - evapCool) * 3600 / (s.volumeL * 4186)
        s.tempC += dTdt * h
    }

    private fun evapLitersPerHour() =
        (config.evapLitersPerDay / 24 * (1 + 0.08 * (s.tempC - 25)) * (if (s.flowOn) 1.0 else 0.8)).coerceAtLeast(0.0)

    private fun evaporation(h: Double) {
        hoursSinceTopOff += h
        if (ato) return
        val v0 = s.volumeL
        val v1 = max(v0 - evapLitersPerHour() * h, v0 * 0.7)
        concentrate(v0, v1)
        if (manualTopOffDays > 0 && hoursSinceTopOff >= manualTopOffDays * 24) topOff()
    }

    /** RO top-off back to the system volume (dilutes everything; salt mass conserved). */
    fun topOff() {
        concentrate(s.volumeL, config.systemLiters)
        hoursSinceTopOff = 0.0
    }

    /** Volume change with conserved solute masses. */
    private fun concentrate(v0: Double, v1: Double) {
        if (v1 <= 0 || v0 == v1) return
        val k = v0 / v1
        val salt = (s.sg - 1) * v0
        s.sg = 1 + salt / v1
        s.alk *= k; s.ca *= k; s.mg *= k; s.k *= k; s.sr *= k; s.i *= k; s.copper *= k
        s.nh3Total *= k; s.no2 *= k; s.no3 *= k; s.po4 *= k; s.detritus *= k
        pendingFeedTan *= k; pendingFeedPo4 *= k
        s.volumeL = v1
    }

    private fun nitrogen(h: Double) {
        val v = s.volumeL
        val tF = (1 + 0.07 * (s.tempC - 25)).coerceIn(0.3, 1.5) * (if (s.ph < 7.6) 0.5 else 1.0)
        // Inputs
        val excretion = K_EXCRETION * bioMassIndex / v * h
        val feedRelease = pendingFeedTan * (1 - exp(-h / 2.0))
        pendingFeedTan -= feedRelease
        val detritusDecay = s.detritus * (1 - exp(-0.02 * h))
        s.detritus -= detritusDecay
        nInputMg += excretion * N_TAN * v
        s.nh3Total += excretion + feedRelease + detritusDecay
        // Nitrification (saturating Monod), limited by available substrate
        val n1 = min(K_AOB * s.bacteriaAob * s.nh3Total / (s.nh3Total + KS) * tF * h, s.nh3Total)
        s.nh3Total -= n1
        s.no2 += n1 * NO2_PER_NH3
        val n2 = min(K_NOB * s.bacteriaNob * s.no2 / (s.no2 + KS) * tF * h, s.no2)
        s.no2 -= n2
        s.no3 += n2 * NO3_PER_NO2
        // Export: corals, algae, skimmer
        val export = min((0.004 * coralBiomass + 0.02 * s.algaeHair + 0.01 * s.macro + (if (s.flowOn) 0.0015 * s.no3 else 0.0)) * h, s.no3)
        s.no3 -= export
        nExportMg += export * N_NO3 * v
        // Bacteria: logistic growth on substrate, slow die-off without it
        val sA = s.nh3Total / (s.nh3Total + KS)
        val sN = s.no2 / (s.no2 + KS)
        val kill = if (s.doMgL < 2 || s.tempC > 35) 0.029 else 0.0   // 50 %/day
        s.bacteriaAob += (MU_AOB * s.bacteriaAob * (1 - s.bacteriaAob) * sA * tF - B_DECAY * s.bacteriaAob * (1 - sA) - kill * s.bacteriaAob) * h
        s.bacteriaNob += (MU_NOB * s.bacteriaNob * (1 - s.bacteriaNob) * sN * tF - B_DECAY * s.bacteriaNob * (1 - sN) - kill * s.bacteriaNob) * h
        s.bacteriaAob = s.bacteriaAob.coerceIn(B_MIN, 1.0)
        s.bacteriaNob = s.bacteriaNob.coerceIn(B_MIN, 1.0)
    }

    private fun phosphate(h: Double) {
        val rel = pendingFeedPo4 * (1 - exp(-h / 2.0))
        pendingFeedPo4 -= rel
        val d = rel + s.detritus * 0.0008 * h -
            (if (s.gfoActive) 0.05 * s.po4 else 0.0) * h -
            0.0015 * s.algaeHair * h - 0.0003 * s.po4 * h -
            PO4_ADSORB * max(0.0, s.po4 - 0.02) * h
        s.po4 = max(0.0, s.po4 + d)
    }

    private fun carbonate(h: Double) {
        val demand = coralDemandDkhPerDay / 24 * h
        var precip = if (s.alk > 11 && s.ca > 460) 0.02 * h else 0.0
        if (s.mg < 1150) precip *= 2
        s.alk -= demand + precip
        s.ca -= demand * CA_PER_DKH + precip * 7.0
        s.mg -= demand * 0.1
    }

    private fun gases(h: Double) {
        val v = s.volumeL
        val resp = K_RESP * bioMassIndex / v + 0.1 * s.bacteriaAob + 0.05 * s.algaeHair
        val photo = lightIntensity * (coralBiomass + s.algaeHair + 0.5 * s.macro)
        val gasEx = if (s.flowOn) 1.0 else 0.4
        // CO2 index → pH (tune: ~0.2 day/night swing at alk 8.5)
        s.co2Index += (CO2_RESP * resp - CO2_PHOTO * photo - CO2_EXCH * (s.co2Index - 1) * gasEx) * h
        s.co2Index = s.co2Index.coerceIn(0.2, 4.0)
        s.ph = 8.15 + 0.045 * (s.alk - 8.5) - 0.35 * (s.co2Index - 1)
        // Dissolved oxygen
        val sat = ChemFns.oxygenSaturation(s.tempC, s.ppt)
        val kla = if (s.flowOn) 1.8 else 0.25
        s.doMgL += (kla * (sat - s.doMgL) + photo * DO_PHOTO - resp) * h
        s.doMgL = s.doMgL.coerceIn(0.0, sat * 1.3)
    }

    private fun algae(h: Double) {
        val g = clamp01((s.po4 - 0.03) / 0.12) * 0.6 + clamp01((s.no3 - 5) / 20) * 0.4
        s.algaeHair += (ALGAE_GROWTH * lightIntensity * g * (1 - s.algaeHair) - grazingPerHour - 0.0004 * s.algaeHair - 0.002 * s.algaeHair * (1 - g)) * h
        s.algaeHair = s.algaeHair.coerceIn(0.0, 1.0)
        s.cyano += (0.002 * (if (s.flowOn) 0.2 else 1.0) * clamp01(s.detritus / 5) * (1 - s.cyano) - 0.0008 * s.cyano) * h
        s.cyano = s.cyano.coerceIn(0.0, 1.0)
    }

    private fun clamp01(v: Double) = v.coerceIn(0.0, 1.0)

    private fun clampAll() {
        s.nh3Total = max(0.0, s.nh3Total); s.no2 = max(0.0, s.no2); s.no3 = max(0.0, s.no3)
        s.alk = max(0.0, s.alk); s.ca = max(0.0, s.ca); s.mg = max(0.0, s.mg); s.detritus = max(0.0, s.detritus)
        s.copper = max(0.0, s.copper - s.copper * 0.002 / 60)   // copper slowly binds to rock/sand
    }

    // ===================================================================================
    // Inputs from FoodSystem / Commands

    /** Food added to the tank: its N/P leaches over ~6 h (τ = 2 h). */
    fun addFeed(grams: Double) {
        val tan = grams * FEED_TAN_PER_G * 1000 / s.volumeL
        pendingFeedTan += tan
        nInputMg += tan * N_TAN * s.volumeL
        pendingFeedPo4 += grams * FEED_PO4_PER_G * 1000 / s.volumeL
    }

    /** Uneaten food / corpses become detritus (grams of TAN-equivalent organics). */
    fun addDetritusTanGrams(gramsTan: Double) {
        val ppm = gramsTan * 1000 / s.volumeL
        s.detritus += ppm
        nInputMg += ppm * N_TAN * s.volumeL
    }

    fun waterChange(fraction: Double, mix: WaterState = WaterState.FRESH_MIX) {
        val f = fraction.coerceIn(0.0, 1.0)
        val before = nitrogenInSystemMg() - pendingFeedTan * N_TAN * s.volumeL
        fun m(a: Double, b: Double) = a * (1 - f) + b * f
        // top off first so the change uses the full system volume
        if (s.volumeL < config.systemLiters) topOff()
        s.tempC = m(s.tempC, mix.tempC); s.sg = m(s.sg, mix.sg); s.alk = m(s.alk, mix.alk); s.ca = m(s.ca, mix.ca); s.mg = m(s.mg, mix.mg)
        s.nh3Total = m(s.nh3Total, mix.nh3Total); s.no2 = m(s.no2, mix.no2); s.no3 = m(s.no3, mix.no3); s.po4 = m(s.po4, mix.po4)
        s.k = m(s.k, mix.k); s.sr = m(s.sr, mix.sr); s.i = m(s.i, mix.i); s.copper = m(s.copper, mix.copper); s.doMgL = m(s.doMgL, mix.doMgL)
        s.detritus *= (1 - f)
        val after = nitrogenInSystemMg() - pendingFeedTan * N_TAN * s.volumeL
        val mixN = (mix.nh3Total * N_TAN + mix.no2 * N_NO2 + mix.no3 * N_NO3) * f * s.volumeL
        nInputMg += mixN
        nExportMg += before + mixN - after
    }

    /** Dose (sandbox): alk/ca/mg are rate limited (1 dKH per 10 min); others are instant. */
    fun dose(paramId: String, delta: Double) {
        when (paramId.lowercase()) {
            "alk", "ca", "mg" -> pendingDose[paramId.lowercase()] = (pendingDose[paramId.lowercase()] ?: 0.0) + delta
            else -> setParam(paramId, (s.get(paramId) ?: 0.0) + delta)
        }
    }

    private fun applyPendingDoses(dt: Double) {
        val minutes = dt / 60
        for ((k, rem) in pendingDose.entries.toList()) {
            val cap = when (k) { "alk" -> 0.1; "ca" -> 1.0; else -> 2.0 } * minutes
            val d = rem.coerceIn(-cap, cap)
            when (k) { "alk" -> s.alk += d; "ca" -> s.ca += d; "mg" -> s.mg += d }
            val left = rem - d
            if (kotlin.math.abs(left) < 1e-9) pendingDose.remove(k) else pendingDose[k] = left
        }
    }

    /** Direct parameter edit (sandbox slider). */
    fun setParam(id: String, value: Double) {
        val v = s.volumeL
        when (id.lowercase()) {
            "temp", "tempc" -> { s.tempC = value; heaterSetC = value }
            "heaterset" -> heaterSetC = value
            "sg", "sal" -> s.sg = value
            "ph" -> s.co2Index = 1 + (8.15 + 0.045 * (s.alk - 8.5) - value) / 0.35
            "alk" -> s.alk = value
            "ca" -> s.ca = value
            "mg" -> s.mg = value
            "nh3", "nh3total", "tan" -> { nInputMg += (value - s.nh3Total) * N_TAN * v; s.nh3Total = value }
            "no2" -> { nInputMg += (value - s.no2) * N_NO2 * v; s.no2 = value }
            "no3" -> { nInputMg += (value - s.no3) * N_NO3 * v; s.no3 = value }
            "po4" -> s.po4 = value
            "do" -> s.doMgL = value
            "k" -> s.k = value
            "sr" -> s.sr = value
            "i" -> s.i = value
            "copper", "cu" -> s.copper = value
            "algae", "algaehair" -> s.algaeHair = value.coerceIn(0.0, 1.0)
            "detritus" -> { nInputMg += (value - s.detritus) * N_TAN * v; s.detritus = value }
            "gfo" -> s.gfoActive = value > 0.5
            "ato" -> ato = value > 0.5
            "bacteria" -> { s.bacteriaAob = value.coerceIn(B_MIN, 1.0); s.bacteriaNob = value.coerceIn(B_MIN, 1.0) }
            "heaterstuck" -> heaterStuckOn = value > 0.5
            "roomtemp" -> {} // handled by Simulation (config copy)
        }
        if (id.equals("ph", true)) s.ph = value
        s.nh3Free = ChemFns.freeNh3(s.nh3Total, s.ph, s.tempC)
    }

    val heaterIsHeating: Boolean get() = heaterPowered

    companion object {
        // Stoichiometric N fractions (mass of N per mass of species)
        const val N_TAN = 14.0 / 17.0
        const val N_NO2 = 14.0 / 46.0
        const val N_NO3 = 14.0 / 62.0
        const val NO2_PER_NH3 = 46.0 / 17.0
        const val NO3_PER_NO2 = 62.0 / 46.0
        const val CA_PER_DKH = 7.14

        // Tuned constants (see SIMULATION §2 "Tuned constants")
        const val K_LOSS = 9.0                 // W/°C
        const val LIGHT_W_PER_FIXTURE = 8.0    // W reaching the water per AI Prime at full power (tune)
        const val PUMP_W = 15.0
        const val EVAP_HEAT_FRACTION = 0.25    // share of latent heat drawn from the water (tune)
        const val K_EXCRETION = 0.0074         // mg TAN/h per g^0.8 (tune: basal excretion)
        const val FEED_TAN_PER_G = 0.04        // g TAN leached per g food (tune)
        const val FEED_PO4_PER_G = 0.004       // g PO4 per g food, net (tune)
        const val PO4_ADSORB = 0.015           // /h rock & sand adsorption above 0.02 ppm (tune)
        const val K_AOB = 0.1; const val K_NOB = 0.15; const val KS = 0.05   // ppm/h at B = 1 (tune: cycle timeline §8.2)
        const val MU_AOB = 0.012; const val MU_NOB = 0.007; const val B_DECAY = 0.0005; const val B_MIN = 0.005
        const val K_RESP = 1.19                // mg O2/L/h per (g^0.8 / L)
        const val DO_PHOTO = 1.1
        const val ALGAE_GROWTH = 0.0025
        const val CO2_RESP = 0.06; const val CO2_PHOTO = 0.10; const val CO2_EXCH = 0.08
    }
}
