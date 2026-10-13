package com.reeflive.core.sim

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * The bacterial guilds of research 18 ("Reef bacteria layer"). [source] says where the guild's numbers come from;
 * [evidence] is the weakest level among its main constants (most are research-17 [K] recollections, so design
 * estimates).
 */
enum class BacteriaGuild(val label: String, val shortLabel: String, val good: Boolean?, val evidence: Evidence, val source: String) {
    AOB("Ammonia oxidisers (Nitrosomonas)", "AOB", true, Evidence.DESIGN_ESTIMATE, "17 §1.1-1.4; WaterSim cycle tuning (SIMULATION §8.2)"),
    AOA("Ammonia-oxidising archaea (Nitrosopumilus)", "AOA", true, Evidence.DESIGN_ESTIMATE, "17 §1.1 [S] AOA < 0.01 % on reef live rock; affinity [K]"),
    NOB("Nitrite oxidisers (Nitrospira)", "NOB", true, Evidence.DESIGN_ESTIMATE, "17 §1.2 [S] Nitrospira doubling 12-32 h"),
    HETEROTROPHS("Heterotrophs (free-living and biofilm)", "Heterotrophs", null, Evidence.DESIGN_ESTIMATE, "17 §2, §7.1 [K] counts and yields"),
    DENITRIFIERS("Denitrifiers (sand bed and rock interiors)", "Denitrifiers", true, Evidence.DESIGN_ESTIMATE, "17 §3.1 [S] Heron Island O2 threshold; rates [K]"),
    SULFATE_REDUCERS("Sulfate reducers (deep sand)", "Sulfate reducers", false, Evidence.DESIGN_ESTIMATE, "17 §3.3 [K]"),
    CYANO("Cyanobacteria (red slime)", "Cyanobacteria", false, Evidence.ANALOGY, "17 §4 [S] PO4-driven; AlgaeModel (research 09)"),
    VIBRIO("Pathogens (Vibrio-type)", "Pathogens", false, Evidence.DESIGN_ESTIMATE, "17 §5.1 [S] V. coralliilyticus temperature dependence"),
    BIOFILM("Beneficial biofilm (mature community)", "Biofilm", true, Evidence.DESIGN_ESTIMATE, "17 §7.2 [K]; AlgaeModel microbes (research 09 A2)"),
    PROBIOTIC("Probiotic (Pseudoalteromonas-type)", "Probiotic", true, Evidence.ANALOGY, "17 §6.2 [S] McH1-7 aquarium trial, Red Sea probiotic"),
}

/** One-tap sandbox actions for the bacteria (research 18 "Sandbox actions"). [amount] scales the default dose. */
enum class BacteriaAction(val label: String) {
    DOSE_NITRIFIERS("Dose bottled nitrifiers"),
    CARBON_DOSE_START("Start carbon dosing (vinegar)"),
    CARBON_DOSE_STOP("Stop carbon dosing"),
    ANTIBIOTIC("Antibiotic (erythromycin)"),
    UV_ON("UV sterilizer on"),
    UV_OFF("UV sterilizer off"),
    STIR_SAND("Stir the sand bed"),
    DOSE_PROBIOTIC("Dose probiotic"),
}

/** HUD row for one guild: [level] 0..1 for a bar, [trendPerDay] change of that level per day, [amount] and [effect] as text. */
class BacteriaGuildState(
    val guild: BacteriaGuild,
    val level: Double,
    val trendPerDay: Double,
    val amount: String,
    val effect: String,
)

/** Snapshot of the bacteria layer for the HUD and tests. */
class BacteriaState(
    val guilds: List<BacteriaGuildState>,
    /** Free-living bacteria, cells/mL (17 §7.1: 1e5-1e6 mature, > 1e7 cloudy). */
    val freeCellsPerMl: Double,
    /** Vibrio-type load, cells/mL. */
    val vibrioPerMl: Double,
    /** Dissolved H2S in the tank, mg/L, and the sulfide held in the deep sand (mg/L if it were all released). */
    val h2sMgL: Double,
    val sulfideStoreMgL: Double,
    val carbonDoseMgCPerLDay: Double,
    val antibioticMgL: Double,
    val uvOn: Boolean,
    /** Milky white water from a bacterial bloom (free count above ~1e7/mL). */
    val cloudy: Boolean,
    /** 0..1: Vibrio-type pressure on fish (feeds the BACTERIAL disease) and on warm corals. */
    val pathogenPressure: Double,
    val coralPathogenStress: Double,
    val sandDepthCm: Double,
    val anoxicDepthCm: Double,
    /** NO3 removed by denitrification and by carbon-fed bacteria (skimmed), ppm NO3 per day. */
    val denitNo3PerDay: Double,
    val assimNo3PerDay: Double,
) {
    fun guild(g: BacteriaGuild): BacteriaGuildState = guilds.first { it.guild == g }

    companion object {
        val EMPTY = BacteriaState(emptyList(), 5e5, 1e3, 0.0, 0.0, 0.0, 0.0, false, false, 0.0, 0.0, 3.0, 1.0, 0.0, 0.0)
    }
}

/**
 * The bacteria layer of research 18 as aggregate pools, beside the water chemistry. Guilds and what they do here:
 *
 * - **Nitrifiers.** AOB and NOB biomass stay in [WaterState.bacteriaAob]/[WaterState.bacteriaNob] and are grown by
 *   [WaterSim] (its tuned Monod kinetics, SIMULATION §8.2). This model only adds what changes them: the antibiotic and
 *   copper inhibition factors ([aobFactor], [nobFactor]) that [WaterSim] multiplies into their rates, antibiotic kill,
 *   and bottled-bacteria inoculum. AOA (archaea) are a small extra pool with a very high ammonia affinity (17 §1.1) that
 *   antibiotics do not touch.
 * - **Heterotrophs.** The DOC breakdown already in [WaterSim] (first order, `DOC_DECAY`) is the resident community's
 *   work; its N and P are the food's own, so it is not counted again. Dosed carbon (vinegar / vodka) is a separate
 *   labile pool that heterotrophs take up by Monod kinetics. Growth on it needs N and P from the water (C:N:P 50:10:1
 *   replete, flexible to ~100:10:0.2 when P-starved, 17 §2.2), respires O2, and the new biomass is either skimmed out
 *   (export) or dies back into the detritus pool. Free-living cells follow from both pools; UV strips the water column.
 * - **Anaerobes.** The sand bed below its oxic layer and the rock interiors hold denitrifiers. The mature default bed
 *   (3 cm) is the reference: its denitrification is part of the nitrate export [WaterSim] was tuned with, so only the
 *   extra from a deeper bed or from dosed carbon reaching the anoxic zones is applied. Below ~1 cm of anoxic sand,
 *   sulfate reducers build a sulfide store that stirring the bed releases as an H2S pulse ([WaterState.h2s]).
 * - **Cyanobacteria** are the AlgaeModel CYANO group ([WaterState.cyano]); this model adds the antibiotic kill.
 * - **Pathogens.** A Vibrio-type load (log10 cells/mL) relaxes toward a level set by temperature, organics, low O2, an
 *   immature community and a bloom, lowered by probiotics, UV and antibiotics. Above ~3e3/mL it raises the fish
 *   BACTERIAL disease onset ([pathogenPressure]); above ~1e4/mL in water over 28 °C it stresses corals.
 * - **Biofilm / probiotic.** Community maturity is [WaterState.microbes] (also AlgaeModel's dino gate); antibiotics knock
 *   it back. A probiotic dose is a pool that decays over days and competes with the pathogens.
 *
 * Steps internally every [STEP_H] hours; deterministic. Numbers marked [K] in research 18 are design estimates here.
 */
class BacteriaModel(private val config: TankConfig) {
    // ---------------------------------------------------------------- state
    /** AOA pool relative to a mature tank (1 = mature, research 18 table A: 0.2 mg C/m², new 0.05). */
    var aoa = 1.0
    /** Dosed labile carbon not yet taken up, mg C/L. */
    var labileC = 0.0
    /** Resident heterotroph community relative to a mature tank on its usual DOC. */
    var hetBase = 1.0
    /** Biomass grown on dosed carbon: carbon, nitrogen, phosphorus (mg/L). */
    var hetC = 0.0
    var hetN = 0.0
    var hetP = 0.0
    /** Free-living bacteria, cells/mL. */
    var freeCells = FREE_MATURE
    /** Denitrifier pool relative to the mature reference bed. */
    var denit = 1.0
    /** How far anoxia has re-formed in the sand since it was last stirred (0..1). */
    var anoxicMaturity = 1.0
    /** Sulfate reducer pool (0..1) and the sulfide store in the deep sand, mg/L-of-tank equivalent. */
    var srb = 0.0
    var sulfideStore = 0.0
    /** H2S still leaving the stirred sand, mg/L. */
    var pendingH2s = 0.0
    /** Vibrio-type load, log10 cells/mL. */
    var vibrioLog = VIBRIO_BASE_LOG
    /** Probiotic pool (1 = 1e6 cells/mL, research 18 K_prob). */
    var probiotic = 0.0
    /** Antibiotic in the water, mg/L (erythromycin-type). */
    var antibiotic = 0.0
    /** Bottled nitrifiers still establishing (AOB equivalents). */
    var inoculum = 0.0
    /** Carbon dosing regime, mg C/L/day (0 = off). */
    var carbonDose = 0.0
        set(v) { field = v.coerceIn(0.0, MAX_CARBON_DOSE) }
    /** Sand bed depth, cm (default: the tank's sand top). */
    var sandDepthCm = config.sandTopCm
        set(v) { field = v.coerceIn(0.0, 15.0) }

    // ---------------------------------------------------------------- drivers (set by WaterSim / Simulation)
    /** Sand stirrers alive (sand-sifting gobies, nassarius): bioturbation deepens the oxic layer. */
    var bioturbation = 0.0
    /** AOB / NOB activity in the last WaterSim step, ppm NH3 and ppm NO2 per day (HUD only). */
    var aobPpmPerDay = 0.0
    var nobPpmPerDay = 0.0
    /** AOA ammonia oxidation potential, ppm NH3 per day (HUD). */
    var aoaPpmPerDay = 0.0; private set

    // ---------------------------------------------------------------- outputs
    /** Multipliers on AOB / NOB activity and growth (antibiotic, copper); read by [WaterSim.nitrogen]. */
    var aobFactor = 1.0; private set
    var nobFactor = 1.0; private set
    /** O2 drawn by bacterial respiration, AOA nitrification and H2S oxidation, mg/L per hour. */
    var o2DemandPerHour = 0.0; private set
    /** Extra die-back of cyanobacteria per day (antibiotic), read by [AlgaeModel]. */
    var cyanoDeathPerDay = 0.0; private set
    /** N accounting since [WaterSim] last drained them (mg N): taken into biomass, returned as detritus, lost as N2. */
    var nUptakeMg = 0.0
    var nReleaseMg = 0.0
    var nDenitMg = 0.0
    /** 0..1 pathogen pressure on fish and Vibrio stress on warm corals. */
    val pathogenPressure: Double get() = ((vibrioLog - VIBRIO_FISH_LOG) / 2.0).coerceIn(0.0, 1.0)
    var coralPathogenStress = 0.0; private set
    /** 0..1 milky haze from a bloom (dims the light a little, [WaterSim.clarity]). */
    val cloudiness: Double get() = ((log10(freeCells.coerceAtLeast(1.0)) - 6.7) / 0.6).coerceIn(0.0, 1.0)
    val anoxicDepthCm: Double get() = max(0.0, sandDepthCm - oxicCm)
    private var oxicCm = OXIC_CM
    private var denitNo3PerDay = 0.0
    private var assimNo3PerDay = 0.0

    private var acc = 0.0
    private var initialized = false
    private var cached: BacteriaState? = null
    private val lastLevel = DoubleArray(BacteriaGuild.entries.size) { Double.NaN }
    private val trend = DoubleArray(BacteriaGuild.entries.size)
    private var lastWater: WaterState? = null
    private var uvOn = false

    fun snapshot(): BacteriaState = cached ?: buildState().also { cached = it }

    // ================================================================ edits
    fun copyFrom(o: BacteriaModel) {
        aoa = o.aoa; labileC = o.labileC; hetBase = o.hetBase; hetC = o.hetC; hetN = o.hetN; hetP = o.hetP
        freeCells = o.freeCells; denit = o.denit; anoxicMaturity = o.anoxicMaturity; srb = o.srb; sulfideStore = o.sulfideStore
        pendingH2s = o.pendingH2s; vibrioLog = o.vibrioLog; probiotic = o.probiotic; antibiotic = o.antibiotic; inoculum = o.inoculum
        carbonDose = o.carbonDose; sandDepthCm = o.sandDepthCm; bioturbation = o.bioturbation
        aobFactor = o.aobFactor; nobFactor = o.nobFactor; o2DemandPerHour = o.o2DemandPerHour; cyanoDeathPerDay = o.cyanoDeathPerDay
        coralPathogenStress = o.coralPathogenStress; oxicCm = o.oxicCm; acc = o.acc; initialized = o.initialized
        o.lastLevel.copyInto(lastLevel); o.trend.copyInto(trend); uvOn = o.uvOn
        cached = null
    }

    /**
     * A new tank on dry rock and fresh sand (research 18 table A "new tank start"): few AOA, a sparse heterotroph
     * film, no anoxic zones or sulfate reducers yet (they form over weeks).
     */
    fun newTank() {
        initialized = true
        aoa = 0.25; hetBase = 0.4; freeCells = 2e5; denit = 0.05; anoxicMaturity = 0.0; srb = 0.0; sulfideStore = 0.0
        vibrioLog = VIBRIO_BASE_LOG; probiotic = 0.0
        cached = null
    }

    /** A sandbox action ([amount] scales the default dose). UV and stirring act through [w]. */
    fun apply(a: BacteriaAction, amount: Double = 1.0, w: WaterSim? = null) {
        val k = amount.coerceAtLeast(0.0)
        val s = w?.s
        when (a) {
            // bottled nitrifiers establish better with food in the water (17 §6.1, table D.1: 0.3 low food, 0.6 with food)
            BacteriaAction.DOSE_NITRIFIERS -> {
                val food = if (s == null) 0.5 else max((s.nh3Total / 0.25).coerceIn(0.0, 1.0), (s.doc / DOC_REF).coerceIn(0.0, 1.0))
                inoculum += INOCULUM_DOSE * k * (ESTABLISH_LOW + (ESTABLISH_FED - ESTABLISH_LOW) * food) * (1 - inhibitionAob())
            }
            BacteriaAction.CARBON_DOSE_START -> carbonDose = CARBON_DOSE_DEFAULT * max(k, 0.1)
            BacteriaAction.CARBON_DOSE_STOP -> carbonDose = 0.0
            BacteriaAction.ANTIBIOTIC -> antibiotic += ANTIBIOTIC_DOSE * k
            BacteriaAction.UV_ON -> w?.uv = true
            BacteriaAction.UV_OFF -> w?.uv = false
            BacteriaAction.STIR_SAND -> if (s != null) stirSand(s, k.coerceIn(0.0, 1.0)) else stirSand(null, k.coerceIn(0.0, 1.0))
            BacteriaAction.DOSE_PROBIOTIC -> probiotic = (probiotic + PROBIOTIC_DOSE * k).coerceAtMost(1.0)
        }
        cached = null
    }

    /**
     * Stirring or vacuuming the sand (17 §3.3): [fraction] of the sulfide store comes out over ~20 minutes, the
     * stirred layer is oxygenated (anoxia has to re-form) and part of the anaerobic pools is lost.
     */
    fun stirSand(s: WaterState?, fraction: Double = 1.0) {
        ensureInit(s)
        val f = fraction.coerceIn(0.0, 1.0)
        val out = sulfideStore * STIR_RELEASE * f
        sulfideStore -= out
        pendingH2s += out
        anoxicMaturity *= 1 - 0.9 * f
        denit *= 1 - 0.3 * f
        srb *= 1 - 0.5 * f
        cached = null
    }

    /** A water change dilutes everything in the water column. */
    fun waterChange(fraction: Double) {
        val f = fraction.coerceIn(0.0, 1.0)
        if (f <= 0) return
        labileC *= 1 - f; antibiotic *= 1 - f; pendingH2s *= 1 - f
        freeCells = freeCells * (1 - f) + FREE_FRESH * f
        probiotic *= 1 - f
        // Vibrio in the column fall with the change; most live on surfaces and come back within days
        vibrioLog = max(VIBRIO_MIN_LOG, log10(10.0.pow(vibrioLog) * (1 - f) + 10.0.pow(VIBRIO_MIN_LOG) * f))
        cached = null
    }

    /** Evaporation or top-off: solutes in the water concentrate by [k]. */
    fun concentrate(k: Double) { labileC *= k; antibiotic *= k; hetC *= k; hetN *= k; hetP *= k }

    // ================================================================ step
    /**
     * Advance [dtH] hours (internally in [STEP_H] chunks). [flowEff] is the pump strength (0 = off); [skimmer] and
     * [uv] the equipment state; [algae] receives the cyano die-back.
     */
    fun step(s: WaterState, dtH: Double, flowEff: Double, skimmer: Boolean, uv: Boolean, algae: AlgaeModel? = null) {
        ensureInit(s)
        uvOn = uv
        acc += dtH
        if (acc < STEP_H - 1e-9) return
        val h = acc
        acc = 0.0
        advance(s, h, flowEff, skimmer, uv)
        algae?.cyanoExtraDeathPerDay = cyanoDeathPerDay
        lastWater = s.copy()
        cached = null
    }

    private fun ensureInit(s: WaterState?) {
        if (initialized) return
        initialized = true
        // a tank loaded with a young bed (cycle preset, tests): start the anaerobes and AOA young too
        if (s != null && s.bacteriaAob < 0.1) newTank() else matureBed(s)
    }

    /** Anaerobic pools at equilibrium for the current bed depth (a mature tank). */
    fun matureBed(s: WaterState? = null) {
        initialized = true
        oxicCm = oxicDepth(s?.detritus ?: 0.2)
        anoxicMaturity = 1.0
        denit = anoxicCapacity()
        srb = if (srbZoneCm() > 0) 1.0 else 0.0
        sulfideStore = SULFIDE_PER_CM * srbZoneCm() * srb
        cached = null
    }

    private fun oxicDepth(detritus: Double): Double =
        OXIC_CM / (1 + 0.5 * max(0.0, detritus - 0.2)) * (1 + 0.25 * min(bioturbation, 4.0))

    /** Anoxic sand in use relative to the 3 cm reference bed plus the rock interiors (1 = reference mature tank). */
    private fun anoxicCapacity(): Double =
        ROCK_SHARE + (1 - ROCK_SHARE) * (anoxicDepthCm * anoxicMaturity / REF_ANOXIC_CM)

    private fun srbZoneCm(): Double = max(0.0, anoxicDepthCm * anoxicMaturity - SRB_START_CM)

    private fun inhibitionAob() = antibiotic / (antibiotic + KINH_ANTIBIOTIC_AOB)
    private fun inhibitionNob() = antibiotic / (antibiotic + KINH_ANTIBIOTIC_NOB)

    private fun advance(s: WaterState, dtH: Double, flowEff: Double, skimmer: Boolean, uv: Boolean) {
        val days = dtH / 24.0
        val v = s.volumeL.coerceAtLeast(1.0)
        val tempC = s.tempC
        var o2 = 0.0   // mg O2/L consumed over the step

        // ---- inhibitors (17 §1.4): antibiotic half-life ~2 d, copper inhibition (marine nitrifiers tolerant) ----
        antibiotic *= exp(-ln(2.0) / ANTIBIOTIC_HALF_LIFE_D * days)
        if (antibiotic < 1e-4) antibiotic = 0.0
        val inhA = inhibitionAob()
        val inhN = inhibitionNob()
        val cu = s.copper / (s.copper + KINH_COPPER)
        aobFactor = (1 - inhA) * (1 - cu)
        nobFactor = (1 - inhN) * (1 - cu)
        val fTq = q10(tempC, NITRIFIER_Q10)
        val fPh = 1.0 / (1.0 + 10.0.pow(7.2 - s.ph))

        // ---- nitrifiers: antibiotic kill and bottled inoculum ----
        if (inhA > 0) s.bacteriaAob = max(WaterSim.B_MIN, s.bacteriaAob * exp(-ANTIBIOTIC_KILL_PER_DAY * inhA * days))
        if (inhN > 0) s.bacteriaNob = max(WaterSim.B_MIN, s.bacteriaNob * exp(-ANTIBIOTIC_KILL_PER_DAY * inhN * days))
        if (inoculum > 1e-6) {
            val est = inoculum * (1 - exp(-dtH / INOCULUM_TAU_H))
            inoculum -= est
            s.bacteriaAob = (s.bacteriaAob + est).coerceAtMost(1.0)
            s.bacteriaNob = (s.bacteriaNob + est * 0.8).coerceAtMost(1.0)
        }

        // ---- AOA: high-affinity ammonia oxidation; archaea are not hit by antibacterials (copper still inhibits) ----
        // In a normal tank their small share is inside WaterSim's tuned AOB rates (AOB dominate reef rock, 17 §1.1);
        // what is applied here is the part that carries on while an antibiotic holds the bacterial AOB back.
        val aoaF = (1 - cu) * fTq * fPh
        val sub = s.nh3Total / (s.nh3Total + AOA_KS)
        aoaPpmPerDay = AOA_K * aoa * sub * aoaF * 24
        val rAoa = min(AOA_K * aoa * sub * aoaF * inhA * dtH, s.nh3Total * 0.5)
        s.nh3Total -= rAoa
        s.no2 += rAoa * WaterSim.NO2_PER_NH3
        o2 += rAoa * 3.4
        aoa += (AOA_MU * aoa * (1 - aoa) * sub * aoaF - AOA_DECAY * aoa * (1 - sub)) * days
        aoa = aoa.coerceIn(AOA_MIN, 1.0)

        // ---- heterotrophs on natural DOC (resident community; its DOC breakdown is WaterSim's DOC_DECAY) ----
        val hetTarget = (s.doc / DOC_REF).coerceIn(0.2, 10.0) * (0.4 + 0.6 * s.microbes)
        hetBase += (hetTarget - hetBase) * (1 - exp(-days / HET_TAU_D))
        hetBase *= exp(-ANTIBIOTIC_HET_KILL * inhA * days)
        hetBase = hetBase.coerceAtLeast(0.05)

        // ---- dosed labile carbon (17 §2.1-2.2) ----
        labileC += carbonDose * days
        var denitC = 0.0
        if (labileC > 1e-6) {
            val nAvail = s.nh3Total * WaterSim.N_TAN + s.no3 * WaterSim.N_NO3               // mg N/L
            val fN = nAvail / (nAvail + HET_KS_N)
            val fP = s.po4 / (s.po4 + HET_KS_PO4)
            val lim = max(HET_MIN_LIM, min(fN, fP))
            val fO2 = s.doMgL / (s.doMgL + HET_KS_O2)
            val biomass = hetC + HET_BASE_C * hetBase
            // uptake is not held back by N/P: short of them the cells grow less and respire the rest (overflow)
            var u = HET_UPTAKE * biomass * labileC / (labileC + HET_KS_C) * fO2 * q10(tempC, 2.0) * (1 - 0.8 * inhA) * dtH
            u = min(u, labileC)
            labileC -= u
            // a share reaches the anoxic zones and drives denitrification there (C:N ~5 by mass, 17 §2.1)
            denitC = u * DEN_C_SHARE * min(1.0, denit)
            val ua = u - denitC
            var g = HET_YIELD * ua * lim
            // N and P for the new cells: C:N 4.3 → 8.6, C:P 19 → 194 by mass as they run short (17 §2.2, flexible)
            var nPerC = N_PER_C * (0.5 + 0.5 * fN)
            var pPerC = P_PER_C_MIN + (P_PER_C - P_PER_C_MIN) * fP
            val po4AsP = s.po4 / PO4_PER_P
            val scale = min(1.0, min(nAvail * 0.9 / max(g * nPerC, 1e-12), po4AsP * 0.9 / max(g * pPerC, 1e-12)))
            g *= scale
            val nTake = g * nPerC
            val pTake = g * pPerC
            val fromNh3 = min(nTake, s.nh3Total * WaterSim.N_TAN * 0.9)
            s.nh3Total -= fromNh3 / WaterSim.N_TAN
            s.no3 = max(0.0, s.no3 - (nTake - fromNh3) / WaterSim.N_NO3)
            s.po4 = max(0.0, s.po4 - pTake * PO4_PER_P)
            hetC += g; hetN += nTake; hetP += pTake
            nUptakeMg += nTake * v
            // respired carbon costs O2; carbon that could not become cells (no N/P) is respired too ("burned")
            o2 += (ua - g) * O2_PER_C
        }

        // ---- carbon-fed biomass losses: grazing/death to detritus, skimmer, UV, antibiotic ----
        if (hetC > 1e-9) {
            val skimK = if (skimmer) HET_SKIM_PER_H * min(flowEff, 1.2).coerceAtLeast(0.3) else 0.0
            val uvK = if (uv) UV_PER_H * FREE_FRACTION * min(flowEff, 1.2).coerceAtLeast(0.3) else 0.0
            val dieK = HET_DEATH_PER_DAY / 24 + ANTIBIOTIC_HET_KILL / 24 * inhA + uvK
            val lossF = 1 - exp(-(dieK + skimK) * dtH)
            val dieShare = if (dieK + skimK > 0) dieK / (dieK + skimK) else 1.0
            val dN = hetN * lossF; val dP = hetP * lossF
            hetC *= 1 - lossF; hetN -= dN; hetP -= dP
            // dead cells: N to the detritus pool (TAN-equivalent), P straight back; skimmed cells are gone (export)
            s.detritus += dN * dieShare / WaterSim.N_TAN
            nReleaseMg += dN * dieShare * v
            s.po4 += dP * dieShare * PO4_PER_P
            if (hetC < 1e-9) { hetC = 0.0; hetN = 0.0; hetP = 0.0 }
        }

        // ---- free-living count: follows the pools, UV strips the column (17 §6.3: biofilm untouched) ----
        val target = FREE_MATURE * hetBase + FREE_FRACTION * hetC * CELLS_PER_ML_PER_MGC_L
        val uvKill = if (uv) UV_PER_H * min(flowEff, 1.2).coerceAtLeast(0.3) else 0.0
        val k = FREE_RELAX_PER_H
        // exact solution of dN/dt = k (target − N) − uvKill N over the step
        val eq = k * target / (k + uvKill)
        freeCells = eq + (freeCells - eq) * exp(-(k + uvKill) * dtH)
        freeCells = freeCells * exp(-ANTIBIOTIC_HET_KILL / 24 * inhA * dtH)
        freeCells = freeCells.coerceIn(1e3, 1e9)

        // ---- anaerobes: oxic layer, denitrifiers, sulfate reducers (17 §3) ----
        oxicCm = oxicDepth(s.detritus)
        anoxicMaturity += (1 - anoxicMaturity) * (1 - exp(-days / ANOXIA_TAU_D))
        val cap = anoxicCapacity().coerceAtLeast(DENIT_MIN)
        denit += DEN_MU * denit * (1 - denit / cap) * days
        denit = denit.coerceIn(DENIT_MIN, 3.0)
        val fTd = q10(tempC, 2.0)
        val no3Term = s.no3 / (s.no3 + DEN_KS_NO3)
        // natural denitrification beyond the reference bed (the reference is inside WaterSim's tuned export)
        val extraNat = DEN_BASE_NO3_PER_H * max(0.0, denit - 1.0) * no3Term * fTd * dtH
        // carbon-fed denitrification in the anoxic zones
        val cDen = denitC / DEN_C_PER_N / WaterSim.N_NO3 * no3Term
        val den = min(extraNat + cDen, s.no3)
        s.no3 -= den
        nDenitMg += den * WaterSim.N_NO3 * v
        denitNo3PerDay = smoothRate(denitNo3PerDay, (DEN_BASE_NO3_PER_H * denit * no3Term * fTd * dtH + cDen) / days, days)
        // sulfate reducers below the nitrate-reducing layer; the store builds up over weeks
        val zone = srbZoneCm()
        val srbTarget = if (zone > 0) 1.0 else 0.0
        srb += (srbTarget - srb) * (1 - exp(-days / SRB_TAU_D))
        val storeTarget = SULFIDE_PER_CM * zone * srb * fTd
        sulfideStore += (storeTarget - sulfideStore) * (1 - exp(-days / SULFIDE_TAU_D))
        sulfideStore = sulfideStore.coerceAtLeast(0.0)
        // H2S: the stirred pulse comes out over ~20 min and oxidises in oxygenated water (half-life ~30 min)
        if (pendingH2s > 1e-9) {
            val rel = pendingH2s * (1 - exp(-dtH / H2S_RELEASE_TAU_H))
            pendingH2s -= rel
            s.h2s += rel
        }
        if (s.h2s > 1e-9) {
            val ox = s.h2s * (1 - exp(-H2S_OX_PER_H * (s.doMgL / (s.doMgL + 1.0)) * dtH))
            s.h2s -= ox
            o2 += ox * O2_PER_H2S
            if (s.h2s < 1e-6) s.h2s = 0.0
        }

        // ---- cyanobacteria: erythromycin is the classic red-slime remover (17 §1.4 macrolides) ----
        cyanoDeathPerDay = CYANO_ANTIBIOTIC_KILL * inhA

        // ---- community maturity: antibiotics strip the biofilm community (AlgaeModel raises it again over months) ----
        if (inhA > 0) s.microbes = (s.microbes - MICROBES_ANTIBIOTIC_PER_DAY * inhA * days).coerceIn(0.0, 1.0)

        // ---- probiotic pool (17 §6.2): no bloom, decays over days, UV and antibiotic kill it ----
        probiotic *= exp(-(PROBIOTIC_DECAY + (if (uv) 0.3 else 0.0) + 1.0 * inhA) * days)
        if (probiotic < 1e-4) probiotic = 0.0

        // ---- pathogens (17 §5.1, model B "Pathogens"): log load relaxes toward a level set by the conditions ----
        val heat = ((tempC - 27.0) / 3.0).coerceIn(0.0, 1.0)
        val org = (((s.doc - DOC_DIRTY) / 3.0).coerceIn(0.0, 1.0) + ((s.detritus - 0.4) / 1.0).coerceIn(0.0, 1.0) +
            0.5 * ((s.no2 - 0.3) / 1.0).coerceIn(0.0, 1.0)).coerceAtMost(1.5)
        val lowO2 = ((5.0 - s.doMgL) / 2.0).coerceIn(0.0, 1.0)
        val bloom = 0.5 * ((log10(freeCells) - 6.5) / 1.0).coerceIn(0.0, 1.0)
        val young = 0.7 * (1 - s.microbes)
        val vTarget = (VIBRIO_BASE_LOG + 2.5 * heat + 1.0 * org + 0.5 * lowO2 + bloom + young -
            1.5 * probiotic - (if (uv) 0.7 else 0.0) - 2.0 * inhA).coerceIn(VIBRIO_MIN_LOG, VIBRIO_MAX_LOG)
        val rate = if (vTarget > vibrioLog) VIBRIO_UP_PER_DAY else VIBRIO_DOWN_PER_DAY
        vibrioLog += (vTarget - vibrioLog) * (1 - exp(-rate * days))
        coralPathogenStress = ((vibrioLog - VIBRIO_CORAL_LOG) / 1.5).coerceIn(0.0, 1.0) * ((tempC - 28.0) / 2.0).coerceIn(0.0, 1.0)

        o2DemandPerHour = o2 / dtH
        assimNo3PerDay = smoothRate(assimNo3PerDay, (hetN * HET_SKIM_PER_H * (if (skimmer) 24.0 else 0.0)) / WaterSim.N_NO3, days)
        updateTrends(s, days)
    }

    private fun smoothRate(old: Double, now: Double, days: Double) = old + (now - old) * min(1.0, days / 0.5)

    private fun q10(t: Double, q: Double): Double = when {
        t >= 36 -> 0.0
        t > 30 -> q.pow(0.4) * (36 - t) / 6
        else -> q.pow((t - 26) / 10)
    }

    // ================================================================ HUD
    private fun level(g: BacteriaGuild, s: WaterState): Double = when (g) {
        BacteriaGuild.AOB -> s.bacteriaAob
        BacteriaGuild.AOA -> aoa
        BacteriaGuild.NOB -> s.bacteriaNob
        BacteriaGuild.HETEROTROPHS -> ((log10(freeCells) - 4.5) / 3.0).coerceIn(0.0, 1.0)
        BacteriaGuild.DENITRIFIERS -> (denit / 2.0).coerceIn(0.0, 1.0)
        BacteriaGuild.SULFATE_REDUCERS -> (sulfideStore / 0.3).coerceIn(0.0, 1.0).let { max(it, 0.3 * srb) }
        BacteriaGuild.CYANO -> (s.cyano / 0.5).coerceIn(0.0, 1.0)
        BacteriaGuild.VIBRIO -> ((vibrioLog - 2.0) / 4.0).coerceIn(0.0, 1.0)
        BacteriaGuild.BIOFILM -> s.microbes
        BacteriaGuild.PROBIOTIC -> probiotic
    }

    private fun updateTrends(s: WaterState, days: Double) {
        if (days <= 0) return
        for (g in BacteriaGuild.entries) {
            val i = g.ordinal
            val l = level(g, s)
            if (!lastLevel[i].isNaN()) trend[i] += ((l - lastLevel[i]) / days - trend[i]) * min(1.0, days / 0.5)
            lastLevel[i] = l
        }
    }

    private fun buildState(): BacteriaState {
        val s = lastWater ?: WaterState()
        val guilds = BacteriaGuild.entries.map { g -> BacteriaGuildState(g, level(g, s), trend[g.ordinal], amount(g, s), effect(g, s)) }
        return BacteriaState(
            guilds, freeCells, 10.0.pow(vibrioLog), s.h2s, sulfideStore, carbonDose, antibiotic, uvOn, cloudiness > 0.3,
            pathogenPressure, coralPathogenStress, sandDepthCm, anoxicDepthCm, denitNo3PerDay, assimNo3PerDay,
        )
    }

    private fun pct(x: Double) = "${(x * 100).toInt()} %"
    private fun sci(x: Double): String {
        if (x <= 0) return "0"
        val e = kotlin.math.floor(log10(x)).toInt()
        val m = x / 10.0.pow(e)
        return "%.1f×10^%d".format(m, e)
    }
    private fun f2(x: Double) = "%.2f".format(x)

    private fun amount(g: BacteriaGuild, s: WaterState): String = when (g) {
        BacteriaGuild.AOB -> "${pct(s.bacteriaAob)} of a mature bed"
        BacteriaGuild.AOA -> "${pct(aoa)} of a mature bed"
        BacteriaGuild.NOB -> "${pct(s.bacteriaNob)} of a mature bed"
        BacteriaGuild.HETEROTROPHS -> "${sci(freeCells)} cells/mL in the water"
        BacteriaGuild.DENITRIFIERS -> "${"%.1f".format(anoxicDepthCm * anoxicMaturity)} cm anoxic sand of ${"%.1f".format(sandDepthCm)} cm"
        BacteriaGuild.SULFATE_REDUCERS -> "sulfide store ${f2(sulfideStore)} mg/L"
        BacteriaGuild.CYANO -> "${pct(s.cyano)} cover"
        BacteriaGuild.VIBRIO -> "${sci(10.0.pow(vibrioLog))} cells/mL"
        BacteriaGuild.BIOFILM -> "${pct(s.microbes)} mature"
        BacteriaGuild.PROBIOTIC -> if (probiotic > 0) "${sci(probiotic * 1e6)} cells/mL" else "none dosed"
    }

    private fun effect(g: BacteriaGuild, s: WaterState): String = when (g) {
        BacteriaGuild.AOB -> when {
            aobFactor < 0.5 -> "Held back by the antibiotic: ammonia can build up"
            s.bacteriaAob < 0.3 -> "Still growing: ammonia is not being cleared yet"
            else -> "Turning ${f2(aobPpmPerDay)} ppm ammonia a day into nitrite"
        }
        BacteriaGuild.AOA -> when {
            aoa < 0.5 -> "A small, slow pool still settling in"
            aobFactor < 0.7 -> "Still oxidising ammonia (~${f2(aoaPpmPerDay)} ppm/day) while the antibiotic holds the bacteria back"
            else -> "Part of the ammonia clean-up at trace levels; antibiotics do not touch it"
        }
        BacteriaGuild.NOB -> when {
            nobFactor < 0.5 -> "Held back by the antibiotic: nitrite can build up"
            s.bacteriaNob < 0.3 -> "Lagging behind: nitrite builds up in a new tank"
            else -> "Turning ${f2(nobPpmPerDay)} ppm nitrite a day into nitrate"
        }
        BacteriaGuild.HETEROTROPHS -> when {
            cloudiness > 0.3 -> "Bacterial bloom: milky water, using up oxygen"
            carbonDose > 0 -> "Eating the dosed carbon and taking up nitrate and phosphate (${f2(assimNo3PerDay)} ppm NO3/day skimmed out)"
            else -> "Breaking down leftover food and dissolved organics"
        }
        BacteriaGuild.DENITRIFIERS -> if (denitNo3PerDay > 0.01) "Turning ${f2(denitNo3PerDay)} ppm nitrate a day into nitrogen gas" else "Too little anoxic space to remove nitrate yet"
        BacteriaGuild.SULFATE_REDUCERS -> when {
            s.h2s > 0.005 -> "Hydrogen sulfide in the water (${"%.3f".format(s.h2s)} mg/L): toxic to fish and corals"
            sulfideStore > 0.05 -> "Rotten-egg gas stored deep in the sand: stirring it releases H2S"
            else -> "Sand is shallow or stirred enough: no sulfide build-up"
        }
        BacteriaGuild.CYANO -> when {
            cyanoDeathPerDay > 0.1 -> "Dying back under the antibiotic"
            s.cyano > 0.15 -> "Red slime smothering sand and rock (feeds on phosphate and organics)"
            else -> "Kept in check"
        }
        BacteriaGuild.VIBRIO -> when {
            coralPathogenStress > 0.1 -> "Warm water lets them attack coral tissue"
            pathogenPressure > 0.3 -> "High: stressed fish can get bacterial infections"
            pathogenPressure > 0 -> "Rising: warm or dirty water helps them"
            else -> "Low: kept down by a healthy community"
        }
        BacteriaGuild.BIOFILM -> if (s.microbes < 0.5) "Young or knocked back: pathogens and dinoflagellates find room" else "Mature film on rock and sand that crowds out pathogens"
        BacteriaGuild.PROBIOTIC -> if (probiotic > 0.02) "Competing with the pathogens; fades over days unless re-dosed" else "Not dosed"
    }

    companion object {
        const val STEP_H = 1.0 / 6.0

        // ---- nitrifiers (17 §1) ----
        @Ev(Evidence.DESIGN_ESTIMATE, "17 §1.3 Q10 2-2.5 [K]")
        const val NITRIFIER_Q10 = 2.2
        @Ev(Evidence.DESIGN_ESTIMATE, "17 model B Kinh 0.5 mg/L erythromycin [K]")
        const val KINH_ANTIBIOTIC_AOB = 0.5
        @Ev(Evidence.DESIGN_ESTIMATE, "17 §1.4 NOB inhibited at low mg/L [K]; 1.0 mg/L chosen")
        const val KINH_ANTIBIOTIC_NOB = 1.0
        @Ev(Evidence.DESIGN_ESTIMATE, "17 §1.4 marine nitrifiers more Cu-tolerant than freshwater EC50 0.1-1 mg/L [K]")
        const val KINH_COPPER = 1.0
        /** One antibiotic dose, mg/L in the tank (erythromycin label doses are a few mg/L). */
        @Ev(Evidence.DESIGN_ESTIMATE, "17 §1.4 erythromycin inhibits AOB at 1-10 mg/L [K]")
        const val ANTIBIOTIC_DOSE = 3.0
        @Ev(Evidence.DESIGN_ESTIMATE, "17 D.3 half-life 1-3 days [K]")
        const val ANTIBIOTIC_HALF_LIFE_D = 2.0
        const val ANTIBIOTIC_KILL_PER_DAY = 0.25
        const val ANTIBIOTIC_HET_KILL = 0.8
        /** Bottled nitrifiers: one bottle = 5 % of a mature bed before establishment losses (labels are not validated, 17 §6.1). */
        @Ev(Evidence.DESIGN_ESTIMATE, "17 §6.1 no independent validation; D.1 lower effective start than the label")
        const val INOCULUM_DOSE = 0.05
        @Ev(Evidence.DESIGN_ESTIMATE, "17 D.1 establishment 0.3 low food, 0.6 with food [K]")
        const val ESTABLISH_LOW = 0.3
        const val ESTABLISH_FED = 0.6
        const val INOCULUM_TAU_H = 48.0
        /** AOA: ~2 % of the AOB bed's capacity, half-saturation ~0.1 µM NH3 (1.4 µg N/L = 0.0017 ppm NH3). */
        @Ev(Evidence.DESIGN_ESTIMATE, "17 §1.1 AOA a minor pool (< 0.01 % of cells on reef live rock [S]); 2 % of the AOB bed chosen")
        const val AOA_K = 0.002
        @Ev(Evidence.DESIGN_ESTIMATE, "17 §1.1 Martens-Habbena 2009 Ks ~0.1 µM [K]")
        const val AOA_KS = 0.0017
        @Ev(Evidence.DESIGN_ESTIMATE, "17 §1.3 AOA mu_max 0.1-0.5 /d [K]")
        const val AOA_MU = 0.3
        const val AOA_DECAY = 0.02
        const val AOA_MIN = 0.02

        // ---- heterotrophs (17 §2) ----
        /** DOC a fed mature tank settles at in this model (mg/L; WaterSim's DOC budget), the resident community's reference. */
        const val DOC_REF = 0.4
        const val HET_TAU_D = 1.0
        /** Resident heterotroph carbon that switches onto dosed carbon, mg C/L (water column + accessible film). */
        @Ev(Evidence.DESIGN_ESTIMATE, "17 §7 counts × ~20 fg C/cell, plus film [K]")
        const val HET_BASE_C = 0.1
        /** Specific uptake of labile carbon per hour: mu_het 2/d ÷ yield 0.4 (17 model B). */
        @Ev(Evidence.DESIGN_ESTIMATE, "17 model B mu_het 2.0 /d, yield 0.3-0.5 [K]")
        const val HET_UPTAKE = 2.0 / 0.4 / 24
        @Ev(Evidence.DESIGN_ESTIMATE, "17 §2.1 yield 0.3-0.5 g/g C [K]")
        const val HET_YIELD = 0.4
        const val HET_KS_C = 0.1
        const val HET_KS_N = 0.05
        const val HET_KS_PO4 = 0.0005
        const val HET_KS_O2 = 0.2
        /** Growth yield keeps this share when N or P run out; the rest of the carbon is respired (overflow respiration). */
        const val HET_MIN_LIM = 0.3
        /** mg N per mg C in new cells at C:N 5 (molar, 50:10 of 17 §2.2), mg P per mg C at C:P 50 and, P-starved, at ~500. */
        @Ev(Evidence.DESIGN_ESTIMATE, "17 §2.2 bacterial C:N:P 50:10:1 (forum [S], unverified) vs Redfield; flexible when starved")
        const val N_PER_C = 14.0 * 10 / (12.0 * 50)
        const val P_PER_C = 31.0 / (12.0 * 50)
        const val P_PER_C_MIN = 31.0 / (12.0 * 500)
        const val PO4_PER_P = 95.0 / 31.0
        /** mg O2 per mg C respired (CO2 stoichiometry; 17 §2.4 quotes 3.4 incl. biomass [K]). */
        const val O2_PER_C = 32.0 / 12.0
        @Ev(Evidence.DESIGN_ESTIMATE, "17 §2.3 grazing catches up, bloom clears in 3-7 d [K]")
        const val HET_DEATH_PER_DAY = 0.2
        /** Skimmer removal of flocculated carbon-fed bacteria, per hour (17 §2.5: 0.002-0.01 /h). */
        @Ev(Evidence.DESIGN_ESTIMATE, "17 §2.5 k = 0.002-0.01 /h [K]")
        const val HET_SKIM_PER_H = 0.01
        @Ev(Evidence.DESIGN_ESTIMATE, "17 §7.1 mature 1e5-1e6 cells/mL [K]")
        const val FREE_MATURE = 5e5
        const val FREE_FRESH = 1e5
        /** Share of carbon-fed biomass free in the water (the rest is film and floc). */
        const val FREE_FRACTION = 0.1
        /** cells/mL per mg C/L at ~20 fg C per cell. */
        const val CELLS_PER_ML_PER_MGC_L = 5e7
        const val FREE_RELAX_PER_H = 0.3
        /** UV-C kill of free-living cells at normal flow (17 §6.3 k_UV 0.5-2 /h). */
        @Ev(Evidence.DESIGN_ESTIMATE, "17 §6.3 k_UV 0.5-2 /h [K]")
        const val UV_PER_H = 1.0
        /** Carbon dosing: 2 mg C/L/day default (17 D.2), guard at 10. */
        @Ev(Evidence.DESIGN_ESTIMATE, "17 D.2 default 2 mg C/L/day, 5 max guard [K]")
        const val CARBON_DOSE_DEFAULT = 2.0
        const val MAX_CARBON_DOSE = 10.0

        // ---- anaerobes (17 §3) ----
        /** Oxic sand layer at moderate flow and normal organics, cm (17 §3.1: anoxic from 0.5-3 cm down). */
        @Ev(Evidence.DESIGN_ESTIMATE, "17 §3.1 anoxic layer depth 0.5-3 cm in fine sand [K]")
        const val OXIC_CM = 2.0
        const val REF_ANOXIC_CM = 1.0
        /** Share of a mature tank's denitrification in the rock interiors (17 §3.2 "may be a larger sink"). */
        @Ev(Evidence.DESIGN_ESTIMATE, "17 §3.2 [K] verify")
        const val ROCK_SHARE = 0.5
        const val ANOXIA_TAU_D = 14.0
        const val DEN_MU = 0.1
        const val DENIT_MIN = 0.02
        /** Reference-bed denitrification at saturating NO3: ~6 mg N/day in 227 L (sand 2-4, rock similar) = 0.117 ppm NO3/day. */
        @Ev(Evidence.DESIGN_ESTIMATE, "17 §3.1 0.5-1 mmol N/m²/d sand, 2-4 mg NO3-N/d for 227 L [K]")
        const val DEN_BASE_NO3_PER_H = 0.117 / 24
        /** K_NO3 0.2 mg NO3-N/L = 0.89 ppm NO3. */
        const val DEN_KS_NO3 = 0.89
        /** Share of dosed carbon that reaches the anoxic zones, and mg C per mg N denitrified (17 §2.1 C:N 4-7). */
        @Ev(Evidence.DESIGN_ESTIMATE, "17 §2.1 C:N 4-7 for heterotrophic denitrification [K]; 25 % share chosen")
        const val DEN_C_SHARE = 0.25
        const val DEN_C_PER_N = 5.0
        /** Sulfate reduction starts this far into the anoxic layer, cm. */
        const val SRB_START_CM = 1.0
        const val SRB_TAU_D = 20.0
        /** Sulfide store per cm of sulfate-reducing sand, mg/L of tank if all released (5 cm bed → 0.1-0.5 mg/L, 17 §3.3). */
        @Ev(Evidence.DESIGN_ESTIMATE, "17 §3.3 stirring a 5 cm bed gives 0.1-0.5 mg/L H2S in 1 h [K]")
        const val SULFIDE_PER_CM = 0.15
        const val SULFIDE_TAU_D = 14.0
        const val STIR_RELEASE = 0.8
        const val H2S_RELEASE_TAU_H = 0.33
        /** Sulfide oxidation in oxygenated water, per hour (half-life ~30 min). */
        @Ev(Evidence.DESIGN_ESTIMATE, "no source in 17; half-life ~30 min chosen")
        const val H2S_OX_PER_H = 1.4
        const val O2_PER_H2S = 64.0 / 34.0

        // ---- cyano, biofilm, probiotic ----
        const val CYANO_ANTIBIOTIC_KILL = 0.8
        const val MICROBES_ANTIBIOTIC_PER_DAY = 0.15
        const val PROBIOTIC_DOSE = 0.3
        /** Probiotic loss per day (17 §6.2: detectable ≥ 1 day, no bloom). */
        @Ev(Evidence.ANALOGY, "17 §6.2 Pseudoalteromonas McH1-7 aquarium trial [S]; rate D")
        const val PROBIOTIC_DECAY = 0.25

        // ---- pathogens (17 §5) ----
        @Ev(Evidence.DESIGN_ESTIMATE, "17 table A B_vib 1e3 cells/mL [K]")
        const val VIBRIO_BASE_LOG = 3.0
        const val VIBRIO_MIN_LOG = 2.0
        const val VIBRIO_MAX_LOG = 6.5
        /** Fish pressure starts at ~3e3/mL; coral stress from 1e4/mL above 28 °C (17 model B coral coupling). */
        const val VIBRIO_FISH_LOG = 3.5
        @Ev(Evidence.SOURCED, "17 §5.1 V. coralliilyticus infection at or above 28-31 °C [S]; 1e4 threshold [K]")
        const val VIBRIO_CORAL_LOG = 4.0
        /** Relaxation of the log load (decades per decade of gap per day). 17's mu 0.5/d cannot meet its own 10× in 3 d. */
        @Ev(Evidence.DESIGN_ESTIMATE, "17 E.7: 26 → 29 °C for 3 d raises B_vib ≥ 10× [K]")
        const val VIBRIO_UP_PER_DAY = 0.6
        const val VIBRIO_DOWN_PER_DAY = 0.4
        /** DOC above which the water counts as organically loaded for the pathogens, mg/L (~4× this model's mature tank). */
        const val DOC_DIRTY = 1.5
    }
}
