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
    /** Algae groups, their nutrient uptake, grazing and die-back (research 09 Part A). */
    val algae = AlgaeModel(config)
    /** Pods, plankton and the other microfauna (research 10 Part 2). */
    val micro = MicrofaunaModel(config)

    // ---- external drivers (set by Simulation before each step) ----
    var lightIntensity = 0.0
    /** Σ massG^0.8 × metabolicMult over living animals. */
    var bioMassIndex = 0.0
    var coralDemandDkhPerDay = 0.15
    var coralBiomass = 0.9
    /**
     * Hair-algae removal reported by the behaviour layer, as hair units per hour. Informational only: algae grazing is
     * computed by [algae] from the grazers present ([AlgaeModel.grazers]), so it is the same in every mode.
     */
    var grazingPerHour = 0.0
    /** Sandbox pump strength (0..2, 1 = normal); only counts while `s.flowOn`. */
    var flowStrength = 1.0
    private val flowEff: Double get() = if (s.flowOn) flowStrength else 0.0
    /** O2 demand from decomposition + nitrification in the last step (mg/L per hour). */
    var oxygenDemandPerHour = 0.0; private set

    // ---- equipment ----
    var heaterWorking = true
    var heaterStuckOn = false
    var heaterSetC = config.heaterSetC
    private var heaterPowered = false
    /** Heater output right now (W), for the flow solver's heater plume. */
    val heaterOutputW: Double get() = if (heaterPowered) config.heaterWatts else 0.0
    var ato = config.atoEnabled
    /** Manual top-off cadence (days) when ATO is off; 0 disables. */
    var manualTopOffDays = 3
    private var hoursSinceTopOff = 0.0

    // ---- filtration and export equipment (research 01 §3.5; coverage gap W-6) ----
    /** Protein skimmer: strips DOC and organics before they mineralise and adds aeration. On in Tee's tank. */
    var skimmer = true
    /** Filter sock: traps detritus; left in past ~2-3 days it rots and releases NH3/PO4 back (01 §3.5). */
    var filterSock = false
    /** Activated carbon: adsorbs DOC, soft-coral allelochemicals and some copper; exhausted over 2-4 weeks. */
    var carbon = false
    /** Chaetomorpha refugium on a reverse light cycle: grows on NO3/PO4 and exports them at harvest. */
    var refugium = false
        set(v) { field = v; if (v && s.chaeto <= 0.0) s.chaeto = CHAETO_START; if (!v) s.chaeto = 0.0 }
    /** UV steriliser on the return: kills free-swimming parasite stages (01 §4.3 ich: theronts). Used by the treatments. */
    var uv = false
    /** Auto-maintenance cadence in days (0 = never): sock swap, carbon swap, refugium harvest (01 §3.5). */
    var sockChangeDays = 3.0
    var carbonChangeDays = 21.0
    var refugiumHarvestDays = 45.0
    /** The display red macro is trimmed back to 70 % of this index when it outgrows it (0 = never trim). */
    var macroTrimAbove = 0.6
    var sockAgeH = 0.0; private set
    var carbonAgeH = 0.0; private set
    var refugiumAgeH = 0.0; private set
    /** Detritus held in the sock (ppm TAN-equivalent of the tank volume). */
    var sockLoad = 0.0; private set
    /** Allelochemical release by the soft corals this hour (index/h), set by Simulation from colony sizes (03 D). */
    var alleloSourcePerHour = 0.0
    /** Fraction of the light reaching the corals through DOC-yellowed water (1 = clear; carbon clears it). */
    val clarity: Double get() = 1 - 0.2 * ((s.doc - 3.0) / 5.0).coerceIn(0.0, 1.0)
    /** Carbon adsorption efficiency: fresh 1, exhausted after ~3 weeks (01 §3.5 change every 2-4 weeks). */
    val carbonEfficiency: Double get() = if (carbon) exp(-carbonAgeH / (24 * 14.0)) else 0.0
    /** NO3 taken up by the display macro and the refugium in the last step, ppm/day. */
    var macroUptakePerDay = 0.0; private set

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
        filtration(h)
        macroalgae(h)
        algae(h)
        microfauna(h)
        gases(h)
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
        val pumpHeat = PUMP_W * flowEff
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
        val v1 = max(v0 - evapLitersPerHour() * h, minOf(v0, config.systemLiters * 0.7))
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
        s.nh3Total *= k; s.no2 *= k; s.no3 *= k; s.po4 *= k; s.detritus *= k; s.silicate *= k
        s.doc *= k; s.allelo *= k; sockLoad *= k
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
        // O2 consumed by decomposition (BOD ≈ 20 mg O2 per mg TAN-equivalent) and nitrification (research 05 §11)
        oxygenDemandPerHour = (detritusDecay * BOD_PER_TAN + n1 * 3.4 + n2 * 0.35) / h.coerceAtLeast(1e-9)
        // Stronger flow keeps detritus suspended for the filter/skimmer (research 05 §16)
        val detExport = if (!skimmer && !filterSock) 0.0 else
            s.detritus * 0.004 * (min(flowEff, 2.0) - 1.0).coerceAtLeast(0.0) * h   // only extra pump strength adds export
        s.detritus -= detExport
        nExportMg += detExport * N_TAN * v
        // Export: corals and the skimmer's organics removal (macroalgae and algae uptake are stoichiometric: macroalgae(), AlgaeModel)
        val export = min((0.004 * coralBiomass + (if (skimmer) 0.0015 * s.no3 * min(flowEff, 1.2) else 0.0)) * h, s.no3)
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
            0.0003 * s.po4 * h -
            PO4_ADSORB * max(0.0, s.po4 - 0.02).let { it / (1 + it / PO4_ADSORB_HALF) } * h
        s.po4 = max(0.0, s.po4 + d)
    }

    /**
     * Ca / alk / Mg coupling (research 01 §2.1, 05 §10). Corals and abiotic precipitation both remove CaCO3, so Ca
     * falls by [CA_PER_DKH] ppm for every dKH of alkalinity used. Corals slow down when Ca or alk run low. Abiotic
     * precipitation starts when the Ca × alk product passes a threshold that Mg sets: Mg in range holds it off,
     * low Mg (< 1200 ppm) lets normal water precipitate. Strontium is taken up with the skeleton (Sr/Ca ≈ 9 mmol/mol).
     */
    private fun carbonate(h: Double) {
        val demand = coralDemandDkhPerDay / 24 * h * calcificationFactor(s)
        val precip = precipitationDkhPerHour(s) * h
        precipDkhPerDay = precip / h.coerceAtLeast(1e-9) * 24
        s.alk -= demand + precip
        s.ca -= (demand + precip) * CA_PER_DKH
        s.mg -= demand * 0.1 + precip * MG_PER_DKH_PRECIP
        s.sr = max(0.0, s.sr - demand * CA_PER_DKH * SR_PER_CA)
    }

    /** Abiotic CaCO3 precipitation in the last step, dKH/day (white crust on heaters and pumps, cloudy water). */
    var precipDkhPerDay = 0.0; private set

    private fun gases(h: Double) {
        val v = s.volumeL
        val resp = K_RESP * bioMassIndex / v + 0.1 * s.bacteriaAob + 0.05 * s.algaeHair + 0.05 * s.cyano + oxygenDemandPerHour
        val photo = lightIntensity * (coralBiomass + s.algaeHair + 0.5 * s.macro)
        val gasEx = if (s.flowOn) (0.4 + 0.6 * min(flowStrength, 1.5)) else 0.4
        // CO2 index → pH (tune: ~0.2 day/night swing at alk 8.5)
        s.co2Index += (CO2_RESP * resp - CO2_PHOTO * photo - CO2_EXCH * (s.co2Index - 1) * gasEx) * h
        s.co2Index = s.co2Index.coerceIn(0.2, 4.0)
        s.ph = 8.15 + 0.045 * (s.alk - 8.5) - 0.35 * (s.co2Index - 1)
        // Dissolved oxygen
        val sat = ChemFns.oxygenSaturation(s.tempC, s.ppt)
        val kla = (if (s.flowOn) 1.8 * (0.3 + 0.7 * min(flowStrength, 1.5)) else 0.25) * (if (skimmer) 1.0 else 0.85)
        s.doMgL += (kla * (sat - s.doMgL) + photo * DO_PHOTO - resp) * h
        s.doMgL = s.doMgL.coerceIn(0.0, sat * 1.3)
    }

    /**
     * Filter sock, skimmer, carbon and DOC (research 01 §3.5). DOC comes from food and photosynthetic exudate and
     * leaves by bacterial breakdown, the skimmer and carbon. A sock captures detritus (export) until it is older than
     * ~2 days, then rots and releases NH3/PO4. Excess DOC (above a mature tank's ~2 mg/L, already inside K_RESP)
     * costs oxygen and yellows the water ([clarity]).
     */
    private fun filtration(h: Double) {
        val v = s.volumeL
        sockAgeH += h; carbonAgeH += h; refugiumAgeH += h
        if (filterSock) {
            val cap = s.detritus * SOCK_CAPTURE * min(flowEff, 1.5) * h
            s.detritus -= cap; sockLoad += cap
            nExportMg += cap * N_TAN * v
            val rel = sockLoad * SOCK_ROT * smooth(48.0, 120.0, sockAgeH) * h
            sockLoad -= rel
            s.nh3Total += rel; s.po4 += rel * 0.04
            nInputMg += rel * N_TAN * v
            if (sockChangeDays > 0 && sockAgeH >= sockChangeDays * 24) changeSock()
        }
        if (carbon && carbonChangeDays > 0 && carbonAgeH >= carbonChangeDays * 24) carbonAgeH = 0.0
        val ce = carbonEfficiency
        val docIn = DOC_EXUDATE * lightIntensity * coralBiomass * h
        val docOut = s.doc * (DOC_DECAY + (if (skimmer) DOC_SKIM * min(flowEff, 1.2).coerceAtLeast(0.3) else 0.0) + DOC_CARBON * ce) * h
        s.doc = max(0.0, s.doc + docIn - min(docOut, s.doc))
        oxygenDemandPerHour += (s.doc - 2.0).coerceAtLeast(0.0) * DOC_DECAY * 2.67   // mg O2 per mg C respired
        val aOut = s.allelo * (ALLELO_DECAY + (if (skimmer) 0.01 else 0.0) + 0.1 * ce) * h
        s.allelo = max(0.0, s.allelo + alleloSourcePerHour * h - min(aOut, s.allelo))
        if (ce > 0) s.copper = max(0.0, s.copper - s.copper * 0.01 * ce * h)
    }

    /** Carries the equipment state over to a rebuilt or forked water model. */
    fun copyEquipmentFrom(o: WaterSim) {
        skimmer = o.skimmer; filterSock = o.filterSock; carbon = o.carbon; uv = o.uv
        refugium = o.refugium; s.chaeto = o.s.chaeto
        sockChangeDays = o.sockChangeDays; carbonChangeDays = o.carbonChangeDays; refugiumHarvestDays = o.refugiumHarvestDays
        macroTrimAbove = o.macroTrimAbove
        sockAgeH = o.sockAgeH; carbonAgeH = o.carbonAgeH; refugiumAgeH = o.refugiumAgeH; sockLoad = o.sockLoad
        alleloSourcePerHour = o.alleloSourcePerHour
        pendingFeedTan = o.pendingFeedTan; pendingFeedPo4 = o.pendingFeedPo4
        pendingDose.clear(); pendingDose.putAll(o.pendingDose)
        hoursSinceTopOff = o.hoursSinceTopOff; manualTopOffDays = o.manualTopOffDays; heaterPowered = o.heaterPowered
    }

    fun changeSock() { sockLoad = 0.0; sockAgeH = 0.0 }

    /**
     * Red macroalgae in the display and Chaetomorpha in the refugium (research 03 A1, C1; 01 §3.5). Logistic growth on
     * light and the scarcer of NO3/PO4 (Liebig); uptake is stoichiometric (macroalgal N:P ≈ 30:1 molar, 19.6 g NO3 per g PO4) and is
     * the export, removed for good when the algae are harvested or trimmed. Light above ~1.0 bleaches the red macro.
     */
    private fun macroalgae(h: Double) {
        val v = s.volumeL
        val nut = min(s.no3 / (s.no3 + 1.0), s.po4 / (s.po4 + 0.005))
        val fl = lightIntensity / (lightIntensity + 0.3)
        val gM = MACRO_MU * s.macro * (1 - s.macro / MACRO_CAP).coerceAtLeast(0.0) * fl * nut * h
        val bleach = MACRO_BLEACH * ((lightIntensity - 1.0) / 0.3).coerceIn(0.0, 1.0) + MACRO_DECAY
        val lM = s.macro * bleach * h
        var gC = 0.0; var lC = 0.0
        if (refugium) {
            gC = MACRO_MU * s.chaeto * (1 - s.chaeto / CHAETO_CAP).coerceAtLeast(0.0) * REFUGIUM_LIGHT * nut * h
            lC = s.chaeto * MACRO_DECAY * h
        }
        var uptake = (gM + gC) * MACRO_YIELD
        val scale = if (uptake > 0) min(1.0, min(s.no3 / uptake, s.po4 * PO4_REDFIELD / uptake)) else 1.0
        uptake *= scale
        s.macro += gM * scale - lM
        s.chaeto += gC * scale - lC
        s.no3 -= uptake; s.po4 = max(0.0, s.po4 - uptake / PO4_REDFIELD)
        nExportMg += uptake * N_NO3 * v
        // shed or bleached tissue rots in the tank
        val lostN = (lM + lC) * MACRO_YIELD * N_NO3
        s.detritus += lostN / N_TAN
        nInputMg += lostN * v
        macroUptakePerDay = uptake / h.coerceAtLeast(1e-9) * 24
        if (macroTrimAbove > 0 && s.macro > macroTrimAbove) s.macro = macroTrimAbove * 0.7
        if (refugium && refugiumHarvestDays > 0 && refugiumAgeH >= refugiumHarvestDays * 24) harvestMacro()
    }

    /** Harvest: half the refugium chaeto and the display macro trimmed by a third (their nutrients leave with them). */
    fun harvestMacro() {
        s.chaeto *= 0.5
        s.macro *= 0.67
        refugiumAgeH = 0.0
    }

    /** One-off maintenance (sandbox buttons, routine schedule). */
    fun maintain(task: MaintenanceTask) {
        when (task) {
            MaintenanceTask.CHANGE_SOCK -> changeSock()
            MaintenanceTask.REPLACE_CARBON -> carbonAgeH = 0.0
            MaintenanceTask.HARVEST_MACRO -> harvestMacro()
        }
    }

    private fun smooth(a: Double, b: Double, x: Double): Double { val t = ((x - a) / (b - a)).coerceIn(0.0, 1.0); return t * t * (3 - 2 * t) }

    /** Algae growth and uptake (research 09 Part A); its decay adds to the oxygen demand used by [gases]. */
    private fun algae(h: Double) {
        algae.step(s, h, lightIntensity, flowEff)
        nExportMg += algae.nUptakeMg
        nInputMg += algae.nReleaseMg
        oxygenDemandPerHour += algae.o2DemandPerHour
    }

    /** Microfauna populations (research 10); their dead biomass decomposes in the detritus pool. */
    private fun microfauna(h: Double) {
        micro.step(s, h, lightIntensity, flowEff, algae)
        if (micro.deadWetG > 0) {
            addDetritusTanGrams(micro.deadWetG * MICRO_TAN_PER_WET_G)
            micro.deadWetG = 0.0
        }
    }

    private fun clampAll() {
        s.nh3Total = max(0.0, s.nh3Total); s.no2 = max(0.0, s.no2); s.no3 = max(0.0, s.no3)
        s.alk = max(0.0, s.alk); s.ca = max(0.0, s.ca); s.mg = max(0.0, s.mg); s.detritus = max(0.0, s.detritus)
        s.copper = max(0.0, s.copper - s.copper * 0.002 / 60)   // copper slowly binds to rock/sand
    }

    // ===================================================================================
    // Inputs from FoodSystem / Commands

    /** Food added to the tank: its N/P leaches over ~6 h (τ = 2 h). */
    fun addFeed(grams: Double) {
        s.doc += grams * DOC_PER_G * 1000 / s.volumeL
        val tan = grams * FEED_TAN_PER_G * 1000 / s.volumeL
        pendingFeedTan += tan
        nInputMg += tan * N_TAN * s.volumeL
        pendingFeedPo4 += grams * FEED_PO4_PER_G * 1000 / s.volumeL
    }

    /**
     * Detritus stirred off the sand bed and kept in suspension reaches the filtration (SIMULATION §16.4): [fraction] of
     * the detritus pool goes into the filter sock (it can rot there like any sock load) or out with the skimmate.
     * Nothing happens without a sock or skimmer: the cloud just settles again.
     */
    fun captureResuspended(fraction: Double) {
        if (fraction <= 0 || (!filterSock && !skimmer)) return
        val cap = s.detritus * fraction.coerceIn(0.0, 0.05)
        if (cap <= 0) return
        s.detritus -= cap
        if (filterSock) sockLoad += cap
        nExportMg += cap * N_TAN * s.volumeL
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
        s.silicate = m(s.silicate, mix.silicate)
        s.doc = m(s.doc, mix.doc); s.allelo = m(s.allelo, mix.allelo)
        s.detritus *= (1 - f)
        micro.waterChange(f)
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
            // algae covers: AlgaeModel re-seeds the group's biomass from the new value on its next step
            "algae", "algaehair" -> s.algaeHair = value.coerceIn(0.0, 1.0)
            "cyano" -> s.cyano = value.coerceIn(0.0, 1.0)
            "diatoms" -> s.diatoms = value.coerceIn(0.0, 1.0)
            "dinos" -> s.dinos = value.coerceIn(0.0, 1.0)
            "film" -> s.film = value.coerceIn(0.0, 1.0)
            "bubble" -> s.bubble = value.coerceIn(0.0, 1.0)
            "coralline" -> s.coralline = value.coerceIn(0.0, 1.0)
            "silicate", "si" -> s.silicate = max(0.0, value)
            "glasswipe" -> if (value > 0) algae.wipeGlass(value.coerceIn(0.0, 1.0))
            "detritus" -> { nInputMg += (value - s.detritus) * N_TAN * v; s.detritus = value }
"gfo" -> s.gfoActive = value > 0.5
            "skimmer" -> skimmer = value > 0.5
            "sock", "filtersock" -> filterSock = value > 0.5
            "carbon" -> { if (value > 0.5 && !carbon) carbonAgeH = 0.0; carbon = value > 0.5 }
            "refugium" -> refugium = value > 0.5
            "uv" -> uv = value > 0.5
            "macro" -> s.macro = value.coerceIn(0.0, MACRO_CAP)
            "chaeto" -> s.chaeto = value.coerceIn(0.0, CHAETO_CAP)
            "doc" -> s.doc = max(0.0, value)
            "allelo" -> s.allelo = max(0.0, value)
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
        const val BOD_PER_TAN = 20.0
        const val N_TAN = 14.0 / 17.0
        const val N_NO2 = 14.0 / 46.0
        const val N_NO3 = 14.0 / 62.0
        const val NO2_PER_NH3 = 46.0 / 17.0
        const val NO3_PER_NO2 = 62.0 / 46.0
        const val CA_PER_DKH = 7.14
        /** Mg carried down with abiotic (high-Mg) carbonate, ppm per dKH precipitated (est.). */
        const val MG_PER_DKH_PRECIP = 1.0
        /** Sr taken up per ppm Ca in coral aragonite (Sr/Ca ≈ 9 mmol/mol → 0.02 by mass, est.). */
        const val SR_PER_CA = 0.02

        /**
         * Calcification slows when Ca or alk run low (05 §10: Ca < 350 strong loss; 05 §9: alk < 6 tissue stress).
         * 1 in the normal band, 0 at Ca 200 ppm or alk 3 dKH.
         */
        fun calcificationFactor(w: WaterState): Double =
            ((w.ca - 200) / 150).coerceIn(0.0, 1.0) * ((w.alk - 3) / 2.5).coerceIn(0.0, 1.0)

        /**
         * Abiotic precipitation, dKH/h. Saturation proxy = (Ca/420)(alk/8.5)(pH factor); it precipitates above a
         * threshold of 1.0 at Mg 1200, 1.4 at Mg ≥ 1350 and 0.6 at Mg ≤ 1050 (01 §2.1, 05 §10: "Mg below 1200:
         * alkalinity and calcium precipitate"; alk > 12 or Ca > 480: precipitation risk). 0.02 dKH/h per 0.2 excess.
         */
        fun precipitationDkhPerHour(w: WaterState): Double {
            val phF = (1 + (w.ph - 8.2)).coerceIn(0.5, 1.5)
            val sat = (w.ca / 420.0) * (w.alk / 8.5) * phF
            val thr = 1.0 + 0.4 * ((w.mg - 1200) / 150).coerceIn(-1.0, 1.0)
            val ex = sat - thr
            return if (ex <= 0) 0.0 else 0.02 * min(ex / 0.2, 3.0)
        }
        /** g TAN-equivalent per g wet microfauna that dies (same as a fish corpse, Simulation.CORPSE_TAN_PER_G). */
        const val MICRO_TAN_PER_WET_G = 0.02

        // Tuned constants (see SIMULATION §2 "Tuned constants")
        const val K_LOSS = 9.0                 // W/°C
        const val LIGHT_W_PER_FIXTURE = 8.0    // W reaching the water per AI Prime at full power (tune)
        const val PUMP_W = 15.0
        const val EVAP_HEAT_FRACTION = 0.25    // share of latent heat drawn from the water (tune)
        const val K_EXCRETION = 0.016          // mg TAN/h per g^0.8 (retuned after the moray → engineer goby correction cut biomass)
        const val FEED_TAN_PER_G = 0.04        // g TAN leached per g food (tune)
        const val FEED_PO4_PER_G = 0.004       // g PO4 per g food, net (tune)
        const val PO4_ADSORB = 0.015           // /h rock & sand adsorption above 0.02 ppm (tune)
        const val PO4_ADSORB_HALF = 0.05       // ppm excess at which rock binding runs at half its linear rate (binding sites saturate)
        const val K_AOB = 0.1; const val K_NOB = 0.15; const val KS = 0.05   // ppm/h at B = 1 (tune: cycle timeline §8.2)
        const val MU_AOB = 0.012; const val MU_NOB = 0.007; const val B_DECAY = 0.0005; const val B_MIN = 0.005
        const val K_RESP = 1.19                // mg O2/L/h per (g^0.8 / L)
        const val DO_PHOTO = 1.1
        // Equipment / DOC / macroalgae (W-6, C-7; est. unless noted)
        const val SOCK_CAPTURE = 0.03          // /h of suspended detritus caught at normal flow (tank turns over through it)
        const val SOCK_ROT = 0.02              // /h of the sock's load released once it is older than ~2-5 days
        const val DOC_PER_G = 0.04             // g DOC leached per g food
        const val DOC_EXUDATE = 0.004          // mg/L/h per (light × coral biomass)
        const val DOC_DECAY = 0.01             // /h bacterial breakdown (τ ≈ 4 days)
        const val DOC_SKIM = 0.02              // /h skimmer removal at normal flow
        const val DOC_CARBON = 0.08            // /h fresh carbon
        const val ALLELO_DECAY = 0.02          // /h breakdown of terpenoids in water (τ ≈ 2 days)
        const val MACRO_MU = 0.1 / 24          // /h max specific growth (refugium chaeto doubles in ~2-3 weeks)
        const val MACRO_CAP = 1.0
        const val CHAETO_CAP = 2.0
        const val CHAETO_START = 0.3
        const val REFUGIUM_LIGHT = 0.6         // light-limitation factor of a reverse-cycle refugium light
        const val MACRO_YIELD = 14.0           // ppm NO3 per unit biomass index
        const val MACRO_BLEACH = 0.15 / 24     // /h loss at full over-light (03 C1 too much light: bleaching)
        const val MACRO_DECAY = 0.001 / 24     // /h shedding
        const val PO4_REDFIELD = 19.6          // g NO3 per g PO4 taken up (macroalgae N:P ≈ 30:1 molar, above Redfield 16:1; est.)
        const val CO2_RESP = 0.06; const val CO2_PHOTO = 0.10; const val CO2_EXCH = 0.08
    }
}
