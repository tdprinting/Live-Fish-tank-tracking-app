package com.reeflive.core.sim

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * The microfauna groups of research 10 §1. [sizeMm] is the adult length used by the microscope view (§5.1 "resolve a
 * group only when it exceeds 2 pixels"); [planktonic] groups live in the water column only.
 */
enum class MicroGroup(val label: String, val plural: String, val sizeMm: Double, val planktonic: Boolean = false) {
    HARPACTICOID("Harpacticoid copepod", "Harpacticoids", 0.8),
    CALANOID("Tigger pod (calanoid)", "Tigger pods", 0.3, planktonic = true),
    CYCLOPOID("Cyclopoid copepod", "Cyclopoids", 0.9),
    GAMMARID("Gammarid amphipod", "Amphipods", 6.0),
    CAPRELLID("Skeleton shrimp", "Skeleton shrimp", 10.0),
    MYSID("Mysid shrimp", "Mysids", 7.0),
    OSTRACOD("Ostracod", "Ostracods", 0.8),
    ISOPOD("Isopod", "Isopods", 2.0),
    ROTIFER("Rotifer", "Rotifers", 0.2, planktonic = true),
    CILIATE("Ciliate", "Ciliates", 0.1, planktonic = true),
    PHYTO("Phytoplankton", "Phytoplankton", 0.005, planktonic = true),
    BRISTLEWORM("Bristleworm", "Bristleworms", 50.0),
    MICROSNAIL("Micro-snail", "Micro-snails", 2.0),
    SPAGHETTI_WORM("Spaghetti worm", "Spaghetti worms", 30.0),
    FORAM("Foraminiferan", "Forams", 3.0),
    FLATWORM("Red flatworm", "Flatworms", 3.0),
    ;

    /** Copepods, amphipods and mysids: the groups with a juvenile (nauplius / young) sub-pool (research 10 §6.2). */
    val hasJuveniles: Boolean get() = this == HARPACTICOID || this == CALANOID || this == CYCLOPOID || this == GAMMARID || this == MYSID
    val isCopepod: Boolean get() = this == HARPACTICOID || this == CALANOID || this == CYCLOPOID
    val isCrustacean: Boolean get() = isCopepod || this == GAMMARID || this == CAPRELLID || this == MYSID || this == OSTRACOD || this == ISOPOD
}

/**
 * Habitat compartments (research 10 §3.1). Benthic densities are per cm² of [ROCK], [SAND], [GLASS] (the lower glass
 * band the pods use) or [CHAETO] filament surface; [COLUMN] densities are per litre.
 */
enum class MicroHabitat { ROCK, SAND, GLASS, CHAETO, COLUMN }

/** What the microscope view is pointed at (research 10 §5.2): a surface or a water sample. */
enum class MicroScene(val label: String) { ROCK("Rock"), GLASS("Glass"), SAND("Sand"), WATER("Water") }

/** One-tap sandbox actions for the microfauna (research 10 §3.3 scenarios, §4 medications). */
enum class MicroAction(val label: String) {
    DOSE_TISBE("Dose Tisbe pods"),
    DOSE_TIGGER("Dose Tigger pods"),
    DOSE_PHYTO("Dose phyto"),
    DOSE_ROTIFERS("Dose rotifers"),
    DOSE_AMPHIPODS("Add amphipods"),
    DOSE_MYSIDS("Add mysids"),
    LIVE_ROCK("Add live rock"),
    FLATWORM_FRAG("Flatworm hitchhikers"),
    TREAT_FLATWORMS("Flatworm treatment"),
    TREAT_FENBENDAZOLE("Fenbendazole"),
    TREAT_PRAZIQUANTEL("Praziquantel"),
}

/**
 * Pod predators of research 10 §2.6. [podEqPerDay] is the saturated intake in harpacticoid equivalents per active
 * period (a gammarid is ~10 pod-eq, a bristleworm 50); [weights] are the prey preferences per [MicroGroup] ordinal;
 * [day]/[night] say when it hunts; [hiddenAccess] is its reach into pores and under rubble (§3.1 a_p,h); [sandAccess]
 * its reach on the sand bed. [halfSat] is the prey within reach (pod-eq, tank-wide) at which it eats half its maximum:
 * fed generalists only top up on pods, the night plankton picker and the mandarin are better searchers (est.; sets the
 * per-capita risk at low density, which must stay below the pods' growth rate for the rock refuge to work).
 */
enum class PodPredator(
    val podEqPerDay: Double, val day: Boolean, val night: Boolean, val fish: Boolean,
    val weights: DoubleArray, val sandAccess: Double = 0.3, val hiddenAccess: Double = 0.05, val halfSat: Double = 20000.0,
) {
    //                                             H    C    Y    G    K    M    O    I    R    Ci  Ph  B    S    Sp  Fo  F
    CARDINAL(300.0, false, true, true, doubleArrayOf(0.8, 0.6, 1.0, 0.9, 0.3, 1.0, 0.3, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0), halfSat = 10000.0),
    CLEANER_WRASSE(250.0, true, false, true, doubleArrayOf(0.7, 0.3, 0.5, 0.3, 0.3, 0.2, 0.3, 0.0, 0.0, 0.0, 0.0, 0.0, 0.2, 0.0, 0.0, 0.3)),
    HAWKFISH(300.0, true, false, true, doubleArrayOf(0.3, 0.1, 0.2, 1.0, 0.6, 0.8, 0.3, 0.3, 0.0, 0.0, 0.0, 0.1, 0.3, 0.0, 0.0, 0.0)),
    DOTTYBACK(250.0, true, false, true, doubleArrayOf(0.3, 0.1, 0.2, 1.0, 0.5, 0.7, 0.3, 0.3, 0.0, 0.0, 0.0, 0.3, 0.3, 0.0, 0.0, 0.0), hiddenAccess = 0.1),
    SAND_GOBY(100.0, true, false, true, doubleArrayOf(0.2, 0.0, 0.1, 0.6, 0.1, 0.3, 0.6, 0.2, 0.0, 0.0, 0.0, 0.2, 0.0, 0.0, 0.0, 0.0), sandAccess = 1.0),
    BLENNY(50.0, true, false, true, doubleArrayOf(0.3, 0.0, 0.0, 0.1, 0.0, 0.0, 0.2, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)),
    CLOWN(60.0, true, false, true, doubleArrayOf(0.4, 0.6, 0.4, 0.1, 0.0, 0.3, 0.0, 0.0, 0.3, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)),
    TANG(10.0, true, false, true, doubleArrayOf(0.1, 0.1, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)),
    /** Mandarin-class specialist (research 10 §3.3 "predator overgrazing"): 1,000-3,000 pods a day, picks into the film. */
    MANDARIN(2000.0, true, false, true, doubleArrayOf(1.0, 0.5, 0.8, 0.3, 0.2, 0.3, 0.5, 0.2, 0.2, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0), sandAccess = 0.5, hiddenAccess = 0.15, halfSat = 800.0),
    BANDED_SHRIMP(60.0, false, true, false, doubleArrayOf(0.1, 0.0, 0.0, 0.5, 0.3, 0.3, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.1, 0.0, 0.0, 0.0), sandAccess = 1.0, hiddenAccess = 0.2),
    CLEANER_SHRIMP(20.0, true, true, false, doubleArrayOf(0.1, 0.0, 0.0, 0.3, 0.2, 0.2, 0.0, 0.0, 0.0, 0.0, 0.0, 0.2, 0.0, 0.0, 0.0, 0.0), sandAccess = 1.0, hiddenAccess = 0.1),
    HERMIT(8.0, true, true, false, doubleArrayOf(0.1, 0.0, 0.0, 0.3, 0.1, 0.0, 0.1, 0.0, 0.0, 0.0, 0.0, 0.3, 0.5, 0.0, 0.0, 0.0), sandAccess = 1.0, hiddenAccess = 0.2),
    CRAB(25.0, false, true, false, doubleArrayOf(0.1, 0.0, 0.0, 0.3, 0.1, 0.0, 0.1, 0.0, 0.0, 0.0, 0.0, 0.3, 0.5, 0.0, 0.0, 0.0), sandAccess = 1.0, hiddenAccess = 0.2),
    BRITTLE_STAR(8.0, false, true, false, doubleArrayOf(0.0, 0.0, 0.0, 0.2, 0.0, 0.0, 0.1, 0.0, 0.0, 0.0, 0.0, 0.1, 0.0, 0.0, 0.0, 0.0), sandAccess = 1.0, hiddenAccess = 0.3),
    ;

    companion object {
        /** Predator row for an animal, or null when it does not eat pods. Mandarins / scooter dragonets match by genus. */
        fun of(o: Organism): PodPredator? {
            val id = o.species.id.lowercase()
            if (id.startsWith("synchiropus") || id.startsWith("dactylopus") || id.startsWith("neosynchiropus")) return MANDARIN
            return when (o.brainKind) {
                BrainKind.CARDINAL -> CARDINAL
                BrainKind.WRASSE -> CLEANER_WRASSE
                BrainKind.HAWK -> HAWKFISH
                BrainKind.DOTTYBACK -> DOTTYBACK
                BrainKind.ENGINEER_GOBY -> SAND_GOBY
                BrainKind.LAWNMOWER_BLENNY, BrainKind.PEEKER_BLENNY -> BLENNY
                BrainKind.CLOWN -> CLOWN
                BrainKind.TANG -> TANG
                BrainKind.BANDED_SHRIMP -> BANDED_SHRIMP
                BrainKind.CLEANER_SHRIMP -> CLEANER_SHRIMP
                BrainKind.HERMIT -> HERMIT
                BrainKind.SALLY_CRAB -> CRAB
                BrainKind.BRITTLE_STAR -> BRITTLE_STAR
                else -> null
            }
        }
    }
}

/** One pod predator present and how many animals it counts for (health-scaled). */
class MicroPredator(val type: PodPredator, val units: Double)

/**
 * Per-group state for the HUD and the microscope view (research 10 §6.2 step 6). Totals are individuals in the
 * tank; densities are per cm² (benthic) or per litre (column groups; phyto per mL). `visible[g * SCENES + scene]` is the
 * expected number of animals in view per cm² of surface ([MicroScene.ROCK]/[MicroScene.GLASS]/[MicroScene.SAND]) or per
 * mL of water ([MicroScene.WATER]) right now, with the diel exposure applied (§5.2).
 */
class MicrofaunaState(
    val total: DoubleArray,
    val juvenileRatio: DoubleArray,
    val breeding: DoubleArray,
    val exposed: DoubleArray,
    val trendPerDay: DoubleArray,
    val present: BooleanArray,
    val visible: FloatArray,
    val phytoPerMl: Double,
    val night: Boolean,
    /** Torch estimate: pods per 100 cm² of glass at lights-out + 1 h (§6.2 step 6). */
    val torchPer100Cm2: Double,
    /** Pod budget: copepods born vs eaten per day (§6.2 step 6). */
    val podsBornPerDay: Double,
    val podsEatenPerDay: Double,
    /** Prey taken by the corals per day and whether they are getting a feeding bonus (§3.4). */
    val coralCapturePerDay: Double,
    val coralFed: Boolean,
    /** A mass flatworm kill is releasing toxin (§3.3 flatworm row). */
    val toxin: Boolean,
    val refugiumL: Double,
) {
    fun total(g: MicroGroup) = total[g.ordinal]
    fun visible(g: MicroGroup, s: MicroScene): Double = visible[g.ordinal * SCENES + s.ordinal].toDouble()
    fun copepods(): Double = total[MicroGroup.HARPACTICOID.ordinal] + total[MicroGroup.CALANOID.ordinal] + total[MicroGroup.CYCLOPOID.ordinal]

    companion object {
        val GROUPS = MicroGroup.entries.size
        val SCENES = MicroScene.entries.size
        val EMPTY = MicrofaunaState(
            DoubleArray(GROUPS), DoubleArray(GROUPS), DoubleArray(GROUPS), DoubleArray(GROUPS), DoubleArray(GROUPS),
            BooleanArray(GROUPS), FloatArray(GROUPS * SCENES), 0.0, false, 0.0, 0.0, 0.0, 0.0, false, false, 0.0,
        )
    }
}

/**
 * Pods, plankton and the other small animals of research 10 Part 2 as aggregate populations per group and habitat.
 *
 * Every group follows §3.1: `dN/dt = N · r(T, S, Cu, DO, pH) · f_food · (1 − N/K) − P − m·N`, with the temperature
 * hump `exp(−((T − Topt)/w)²)`, Holling food terms on the research 09 pools (film and diatoms from [AlgaeModel],
 * detritus, the model's own phytoplankton, nauplii and rotifers), carrying capacity `K = K_base · area · (0.3 + 0.7 f)`
 * per habitat (column groups: set by their food), the parameter mortality tiers of §4, and multi-prey Holling II
 * predation on the EXPOSED animals by the fish and inverts present ([predators], §2.6) plus polyp capture by the corals
 * (§3.4). Copepods, amphipods and mysids carry a juvenile pool (births → juveniles → adults after ~0.6 G(T)) so a
 * phyto-fed boom peaks a generation later (§3.3) and the 40x view knows when nauplii are about.
 *
 * Diel exposure relaxes toward the day or night fraction of §2.2 with a 30 min lag (emergence within 1-2 h of
 * lights-out). Established groups keep a hidden seed in the rock (they never hit zero) unless poisoned out (copper,
 * medication); a dose, live rock or a frag re-seeds them. Steps internally every 15 sim minutes, so it costs nothing at
 * 1x. Feeds back into the tank: copepod grazing on the film ([AlgaeModel.podGrazeGPerDay]) and dead biomass into the
 * detritus pool ([deadWetG], drained by [WaterSim]). Deterministic: no randomness.
 */
class MicrofaunaModel(private val config: TankConfig) {
    private val ng = MicrofaunaState.GROUPS
    private val nh = MicroHabitat.entries.size

    /** Habitat areas in cm² (column: litres, chaeto: cm² per litre of refugium chaeto × litres). */
    private val rockCm2: Double
    private val sandCm2: Double
    private val glassCm2: Double
    private val litRockCm2: Double
    private val shadeRockCm2: Double

    init {
        val foot = config.lengthCm * config.widthCm
        rockCm2 = 1.2 * foot                                  // same rock area as AlgaeModel (top + shade)
        litRockCm2 = 0.5 * foot
        shadeRockCm2 = 0.7 * foot
        sandCm2 = foot
        glassCm2 = 2 * (config.lengthCm + config.widthCm) * GLASS_BAND_CM
    }

    /** Adults (all individuals for groups without a juvenile pool), [g * nh + h]. Phyto in cells. */
    val adults = DoubleArray(ng * nh)
    /** Juveniles (nauplii, young), [g * nh + h]; zero for groups without the pool. */
    val juveniles = DoubleArray(ng * nh)
    /** Diel exposure state per group (relaxes toward the §2.2 day/night fraction). */
    private val expo = DoubleArray(ng) { -1.0 }
    /** Breeding index per group (0..1; the egg-sac carrying fraction, §6.3 #7). */
    val breeding = DoubleArray(ng) { 0.5 }
    /** Established in the tank (seed floor applies); false = absent until dosed/seeded. */
    val present = BooleanArray(ng) { P[it].presentByDefault }
    /** Has reached [ESTABLISHED_FRAC] of K at least once: only then does the rock keep a hidden seed of it. */
    private val established = BooleanArray(ng) { P[it].presentByDefault }
    /** Days of residual copper leaching from rock after a treatment (r × 0.5, §4 copper). */
    var copperResidualDays = 0.0; private set
    var fenbendazoleDays = 0.0
    var praziquantelDays = 0.0
    /** Sim hours left of flatworm toxin after a mass kill (§3.3). */
    var toxinHours = 0.0; private set
    /** Coral feeding bonus days left (§3.4: +5 % growth for 3 days per well-fed day). */
    var coralFedDays = 0.0; private set
    private var acc = 0.0
    private var initialized = false
    /**
     * Recent phyto (cells/mL): jumps up with a dose, fades over [PHYTO_MEMORY_D]. Sets the column grazers' carrying
     * capacity, so eggs laid in a bloom still hatch and grow up after the cells are gone (§3.3 Tigger boom).
     */
    private var phytoMem = 0.0

    // ---- drivers set by the owner (Simulation / tests) ----
    var predators: List<MicroPredator> = emptyList()
    /** Coral polyps that catch plankton: small-polyp octocorals (c_max 1/h), zoanthids (0.3/h), mushrooms (engulf). */
    var smallPolyps = 0.0
    var zoaPolyps = 0.0
    var mushrooms = 0.0
    /** Litres of macroalgae in the display (pods use it like rock) and of chaeto in a sump refugium (no fish reach it). */
    var displayMacroL = 0.0
    var refugiumL = 0.0
    /** Food override in g DW m-2 of film + detritus (tests); null = from [AlgaeModel] and the detritus pool. */
    var foodOverride: Double? = null
    /** Lit-rock area override for the flatworm habitat (cm², tests). */
    var flatwormAreaOverride: Double? = null

    // ---- outputs of the last step ----
    /** Dead biomass (g wet) waiting to be added to the detritus pool; the owner drains it. */
    var deadWetG = 0.0
    val eatenPerDay = DoubleArray(ng)
    val bornPerDay = DoubleArray(ng)
    val trendPerDay = DoubleArray(ng)
    /** Pods eaten per day by each predator type (diagnostics, research 10 §6.3 #3). */
    val eatenByPredator = DoubleArray(PodPredator.entries.size)
    var coralCapturePerDay = 0.0; private set
    var night = false; private set
    var filmGm2 = 0.0; private set
    private var cached: MicrofaunaState? = null

    // ============================================================== queries
    fun idx(g: MicroGroup, h: MicroHabitat) = g.ordinal * nh + h.ordinal
    fun total(g: MicroGroup): Double { var t = 0.0; for (h in 0 until nh) t += adults[g.ordinal * nh + h] + juveniles[g.ordinal * nh + h]; return t }
    fun adultsOf(g: MicroGroup): Double { var t = 0.0; for (h in 0 until nh) t += adults[g.ordinal * nh + h]; return t }
    fun juvenilesOf(g: MicroGroup): Double { var t = 0.0; for (h in 0 until nh) t += juveniles[g.ordinal * nh + h]; return t }
    fun exposure(g: MicroGroup): Double = if (expo[g.ordinal] < 0) expTarget(g.ordinal, night) else expo[g.ordinal]

    /** Area (cm²) or volume (L) of a habitat. */
    fun size(h: MicroHabitat): Double = when (h) {
        MicroHabitat.ROCK -> rockCm2 + displayMacroL * CHAETO_CM2_PER_L
        MicroHabitat.SAND -> sandCm2
        MicroHabitat.GLASS -> glassCm2
        MicroHabitat.CHAETO -> refugiumL * CHAETO_CM2_PER_L
        MicroHabitat.COLUMN -> volumeL
    }
    private var volumeL = config.systemLiters

    /** Density in a habitat: per cm² (benthic) or per litre (column). */
    fun density(g: MicroGroup, h: MicroHabitat): Double {
        val a = if (g == MicroGroup.FLATWORM && h == MicroHabitat.ROCK) flatwormArea() else size(h)
        return if (a > 0) (adults[idx(g, h)] + juveniles[idx(g, h)]) / a else 0.0
    }

    /** Phytoplankton, cells per mL. */
    val phytoPerMl: Double get() = adults[idx(MicroGroup.PHYTO, MicroHabitat.COLUMN)] / (volumeL * 1000)

    /** Total carrying capacity of a group right now (individuals). */
    fun capacity(g: MicroGroup): Double { var t = 0.0; for (h in 0 until nh) t += kNow[g.ordinal * nh + h]; return t }
    private val kNow = DoubleArray(ng * nh)

    fun snapshot(): MicrofaunaState = cached ?: buildState().also { cached = it }

    // ============================================================== edits
    fun copyFrom(o: MicrofaunaModel) {
        o.adults.copyInto(adults); o.juveniles.copyInto(juveniles); o.expo.copyInto(expo); o.breeding.copyInto(breeding)
        o.present.copyInto(present); o.established.copyInto(established); o.kNow.copyInto(kNow)
        copperResidualDays = o.copperResidualDays; fenbendazoleDays = o.fenbendazoleDays; praziquantelDays = o.praziquantelDays
        toxinHours = o.toxinHours; coralFedDays = o.coralFedDays; acc = o.acc; initialized = o.initialized; phytoMem = o.phytoMem
        displayMacroL = o.displayMacroL; refugiumL = o.refugiumL; volumeL = o.volumeL; night = o.night
        cached = null
    }

    /** Set a group's tank total (spread over its habitats by capacity) and mark it present (sandbox, tests). */
    fun setTotal(g: MicroGroup, n: Double) {
        ensureInit(null)
        val gi = g.ordinal
        var k = 0.0
        for (h in 0 until nh) k += kRef(gi, h)
        for (h in 0 until nh) {
            val share = if (k > 0) kRef(gi, h) / k else 0.0
            adults[gi * nh + h] = n * share * (if (g.hasJuveniles) 0.75 else 1.0)
            juveniles[gi * nh + h] = if (g.hasJuveniles) n * share * 0.25 else 0.0
        }
        present[gi] = n > 0
        established[gi] = n > 0
        cached = null
    }

    /** Set a group to a fraction of its reference capacity (well-fed K). */
    fun setFractionOfK(g: MicroGroup, f: Double) { ensureInit(null); setTotal(g, f * refCapacity(g)) }

    /** Reference capacity (individuals) at full food, used for starting values and the seed floor. */
    fun refCapacity(g: MicroGroup): Double { var k = 0.0; for (h in 0 until nh) k += kRef(g.ordinal, h); return k }

    /**
     * A new tank on dry rock (§3.3): no pods, worms or snails until live rock, a frag or a bottle brings them; ciliates
     * and the phyto background arrive with the water.
     */
    fun dryRock() {
        ensureInit(null)
        for (gi in 0 until ng) {
            val g = MicroGroup.entries[gi]
            if (g == MicroGroup.CILIATE || g == MicroGroup.PHYTO) continue
            for (h in 0 until nh) { adults[gi * nh + h] = 0.0; juveniles[gi * nh + h] = 0.0 }
            present[gi] = false; established[gi] = false
        }
        cached = null
    }

    /** Phytoplankton to [cellsPerMl] (tests, sandbox). */
    fun setPhyto(cellsPerMl: Double) { adults[idx(MicroGroup.PHYTO, MicroHabitat.COLUMN)] = cellsPerMl * volumeL * 1000; cached = null }

    /** Add [n] individuals of [g] to habitat [h] (a bottle dose: adults). */
    fun add(g: MicroGroup, h: MicroHabitat, n: Double) {
        ensureInit(null)
        adults[idx(g, h)] += n
        present[g.ordinal] = true
        cached = null
    }

    /** A sandbox action ([amount] scales the default bottle). */
    fun apply(a: MicroAction, amount: Double = 1.0, s: WaterState? = null) {
        ensureInit(s)
        val k = amount.coerceAtLeast(0.0)
        when (a) {
            // a bottle of 2,000-5,000 Tisbe onto the glass and rock face (§3.3)
            MicroAction.DOSE_TISBE -> { add(MicroGroup.HARPACTICOID, MicroHabitat.ROCK, 2500.0 * k); add(MicroGroup.HARPACTICOID, MicroHabitat.GLASS, 1000.0 * k) }
            // a 2,000-pod bottle of Parvocalanus: 9 per L in 227 L (§2.5)
            MicroAction.DOSE_TIGGER -> add(MicroGroup.CALANOID, MicroHabitat.COLUMN, 2000.0 * k)
            // 50 mL of 10^9 cells/mL: 2 × 10^5 cells/mL in 227 L (§2.5)
            MicroAction.DOSE_PHYTO -> { adults[idx(MicroGroup.PHYTO, MicroHabitat.COLUMN)] += 5e10 * k; present[MicroGroup.PHYTO.ordinal] = true }
            // a rotifer culture: 5 per mL (§2.5: 5-50 per mL for 1-2 d after a dose)
            MicroAction.DOSE_ROTIFERS -> add(MicroGroup.ROTIFER, MicroHabitat.COLUMN, 5.0 * 1000 * volumeL * k)
            MicroAction.DOSE_AMPHIPODS -> add(MicroGroup.GAMMARID, MicroHabitat.ROCK, 100.0 * k)
            MicroAction.DOSE_MYSIDS -> add(MicroGroup.MYSID, MicroHabitat.COLUMN, 50.0 * k)
            // live rock carries the common crawlers at 2 % of K (§3.3 "new tank, live rock")
            MicroAction.LIVE_ROCK -> for (g in LIVE_ROCK_GROUPS) {
                val gi = g.ordinal
                for (h in 0 until nh) if (h != MicroHabitat.COLUMN.ordinal || g == MicroGroup.CYCLOPOID) adults[gi * nh + h] += 0.02 * k * kRef(gi, h)
                present[gi] = true
            }
            // one frag carries 5 flatworms (§3.3)
            MicroAction.FLATWORM_FRAG -> add(MicroGroup.FLATWORM, MicroHabitat.ROCK, 5.0 * k)
            // levamisole-type killer: flatworms gone within the hour, pods −20 %, toxin dump (§4 medications)
            MicroAction.TREAT_FLATWORMS -> {
                val fi = MicroGroup.FLATWORM.ordinal
                var killed = 0.0
                for (h in 0 until nh) { killed += adults[fi * nh + h]; adults[fi * nh + h] = 0.0 }
                present[fi] = false; established[fi] = false
                deadWetG += killed * P[fi].wetMg / 1000
                if (killed > 1000) toxinHours = TOXIN_H
                for (g in MicroGroup.entries) if (g.isCopepod) scale(g.ordinal, 0.8)
            }
            MicroAction.TREAT_FENBENDAZOLE -> fenbendazoleDays = 3.0 * max(k, 0.1)
            MicroAction.TREAT_PRAZIQUANTEL -> praziquantelDays = 3.0 * max(k, 0.1)
        }
        cached = null
    }

    /** A water change removes [fraction] of everything swimming in the column (plankton, phyto, swimming pods). */
    fun waterChange(fraction: Double) {
        val f = fraction.coerceIn(0.0, 1.0)
        if (f <= 0) return
        for (gi in 0 until ng) {
            val i = gi * nh + MicroHabitat.COLUMN.ordinal
            adults[i] *= 1 - f; juveniles[i] *= 1 - f
        }
        val pi = idx(MicroGroup.PHYTO, MicroHabitat.COLUMN)
        adults[pi] = max(adults[pi], PHYTO_SEED * volumeL * 1000)
        cached = null
    }

    private fun scale(gi: Int, f: Double) {
        for (h in 0 until nh) {
            val i = gi * nh + h
            deadWetG += (adults[i] + juveniles[i] * JUV_MASS) * (1 - f) * P[gi].wetMg / 1000
            adults[i] *= f; juveniles[i] *= f
        }
    }

    // ============================================================== step
    /**
     * Advance [dtH] hours (internally in [STEP_H] chunks). [light] is the tank light intensity (0..1), [flowEff] the
     * pump strength (0 = off). Reads the film, diatoms and hair biomass and DOC index of [algae] when given and writes
     * the copepod film grazing back into it.
     */
    fun step(s: WaterState, dtH: Double, light: Double, flowEff: Double, algae: AlgaeModel? = null) {
        ensureInit(s)
        night = light < LightSchedule.LIGHTS_ON
        acc += dtH
        if (acc < STEP_H - 1e-9) return
        val h = acc
        acc = 0.0
        advance(s, h, light, flowEff, algae)
        cached = null
    }

    private fun advance(s: WaterState, dtH: Double, light: Double, flowEff: Double, algae: AlgaeModel?) {
        val days = dtH / 24.0
        volumeL = s.volumeL.coerceAtLeast(1.0)
        val vMl = volumeL * 1000

        // ---- relax the diel exposure toward the day or night fraction (§2.2) ----
        val kExp = 1 - exp(-dtH / EXPOSURE_LAG_H)
        for (gi in 0 until ng) { val t = expTarget(gi, night); expo[gi] = if (expo[gi] < 0) t else expo[gi] + (t - expo[gi]) * kExp }

        // ---- food pools (§2.3): film + detritus (g DW m-2), phyto (cells/mL), nauplii + rotifers (per L), DOC ----
        filmGm2 = foodOverride ?: run {
            val det = DETRITUS_G_M2_PER_PPM * s.detritus
            val bio = BIOFILM_G_M2 * s.microbes.coerceIn(0.2, 1.0)
            val alg = if (algae != null) {
                var b = algae.biomass(AlgaeGroup.FILM) + algae.biomass(AlgaeGroup.DIATOMS) + HAIR_FILM_SHARE * algae.biomass(AlgaeGroup.HAIR)
                var a = 0.0; for (x in algae.area) a += x
                if (a > 0) b / a else 0.0
            } else 0.0
            det + bio + alg
        }
        val phyto = phytoPerMl
        phytoMem = if (phyto > phytoMem) phyto else phytoMem + (phyto - phytoMem) * (1 - exp(-days / PHYTO_MEMORY_D))
        val naupliiPerL = (juvenilesOf(MicroGroup.HARPACTICOID) * 0.05 + juvenilesOf(MicroGroup.CALANOID) + juvenilesOf(MicroGroup.CYCLOPOID) * 0.3) / volumeL +
            adultsOf(MicroGroup.ROTIFER) / volumeL
        val ciliatesPerMl = adultsOf(MicroGroup.CILIATE) / vMl
        val doc = algae?.docIndex ?: ((s.detritus - 0.3) / 1.5).coerceIn(0.0, 1.0)

        // ---- per-group factors ----
        val fFood = DoubleArray(ng)
        val growthMult = DoubleArray(ng)
        val death = DoubleArray(ng)
        val breedHeat = DoubleArray(ng)
        val ppt = s.ppt
        val tanN = s.nh3Total * (14.0 / 17.0)
        for (gi in 0 until ng) {
            val p = P[gi]
            val g = MicroGroup.entries[gi]
            val food = when (p.food) {
                Food.FILM -> filmGm2
                Food.DETRITUS -> filmGm2 * 0.5 + DETRITUS_G_M2_PER_PPM * s.detritus * 0.5
                Food.PHYTO -> phyto + CILIATE_CELL_EQ * ciliatesPerMl
                Food.PHYTO_OR_DETRITUS -> phyto + PHYTO_PER_FILM * filmGm2
                Food.NAUPLII -> naupliiPerL
                Food.DOC -> doc
                Food.LIGHT -> light
                Food.NUTRIENTS -> 1.0
            }
            fFood[gi] = if (p.food == Food.NUTRIENTS) 1.0 else food / (p.ks + food)
            if (g == MicroGroup.CILIATE) fFood[gi] = max(fFood[gi], 0.3)                 // bacteria are always about
            if (g == MicroGroup.FORAM) fFood[gi] = max(fFood[gi], 0.5)                   // soritids photosynthesise
            if (g == MicroGroup.FLATWORM) fFood[gi] = 1.0                                 // symbionts by day, small pods by night
            // temperature hump and the parameter rules of §4
            var r = exp(-((s.tempC - p.tOpt) / p.tWidth).let { it * it })
            var d = heatDeath(s.tempC, p.tLethal)
            breedHeat[gi] = ((p.tLethal - 2 - s.tempC) / 2).coerceIn(0.0, 1.0)
            // salinity: 28-38 ppt no effect
            when {
                ppt < 28 && ppt >= 20 -> if (g.isCopepod && g != MicroGroup.CYCLOPOID || g == MicroGroup.GAMMARID || g == MicroGroup.CAPRELLID) r *= 0.7
                ppt > 50 -> { r *= 0.5; d += 0.22 }
                ppt > 40 -> r *= 0.5
            }
            d += when (g) {
                MicroGroup.HARPACTICOID -> 0.69 * ((22.5 - ppt) / 3).coerceIn(0.0, 1.0)
                MicroGroup.MYSID -> 2.0 * ((24 - ppt) / 3).coerceIn(0.0, 1.0)
                MicroGroup.GAMMARID, MicroGroup.CAPRELLID -> 0.36 * ((22 - ppt) / 3).coerceIn(0.0, 1.0)
                MicroGroup.CALANOID -> 0.5 * ((22 - ppt) / 3).coerceIn(0.0, 1.0)
                else -> if (g.isCrustacean) 0.3 * ((20 - ppt) / 3).coerceIn(0.0, 1.0) else 0.0
            }
            // copper: copepods stop breeding at 0.02 ppm; 0.15 ppm clears everything but ciliates and phyto in 3 days
            val cu = s.copper
            when (p.tox) {
                Tox.COPEPOD -> { if (cu >= 0.02) r = 0.0; d += interp(CU_X, CU_COPEPOD, cu) }
                Tox.INVERT -> { r *= (1 - cu / 0.05).coerceIn(0.0, 1.0); d += interp(CU_X, CU_INVERT, cu) }
                Tox.TOLERANT -> {}
            }
            if (copperResidualDays > 0 && p.tox != Tox.TOLERANT) r *= 0.5
            // dissolved oxygen
            val dO = s.doMgL
            if (dO < 2) when (g) {
                MicroGroup.CALANOID, MicroGroup.MYSID -> d += 0.36
                MicroGroup.GAMMARID, MicroGroup.CAPRELLID -> d += 0.22
                MicroGroup.HARPACTICOID -> r *= 0.2
                else -> if (g.isCrustacean) r *= 0.5
            }
            if (dO < 0.5) when (g) {
                MicroGroup.GAMMARID, MicroGroup.CAPRELLID -> d += 0.47
                MicroGroup.HARPACTICOID -> d += 0.105
                else -> if (g.isCrustacean) d += 0.3
            }
            // pH: calcifiers slow below 7.6, copepods above 8.6
            if (s.ph < 7.6 && (g == MicroGroup.OSTRACOD || g == MicroGroup.FORAM || g == MicroGroup.MICROSNAIL)) r *= 0.5
            if (s.ph > 8.6 && g.isCopepod) r *= 0.7
            // ammonia (NH3-N) and nitrate
            if (g.isCrustacean) {
                if (tanN > 0.1) r *= 0.5
                if (tanN > 0.5) d += 0.22
            }
            if (s.no3 > 100) r *= 0.8
            // medications (hobby reports, §4)
            if (fenbendazoleDays > 0) d += when (g) {
                MicroGroup.BRISTLEWORM, MicroGroup.SPAGHETTI_WORM, MicroGroup.FLATWORM -> 2.3
                MicroGroup.MICROSNAIL -> 0.69
                else -> if (g.isCrustacean) 0.22 else 0.0
            }
            if (praziquantelDays > 0) d += when (g) {
                MicroGroup.FLATWORM -> 0.69
                else -> if (g.isCopepod) 0.05 else 0.0
            }
            // flatworm symbionts: +30 % in strong light on lit rock (380 PAR at full light), ramping in from 250 PAR
            if (g == MicroGroup.FLATWORM) r *= 1 + FLATWORM_LIGHT_BONUS * ((light * 380 - 250) / 100).coerceIn(0.0, 1.0)
            growthMult[gi] = r
            death[gi] = d
        }

        // ---- carrying capacity per habitat ----
        for (gi in 0 until ng) for (h in 0 until nh) kNow[gi * nh + h] = kOf(gi, h, fFood[gi], naupliiPerL, doc, s)

        // ---- predation (§2.6, §3.1): multi-prey Holling II on exposed animals, per predator type ----
        val eat = DoubleArray(ng * nh)        // adults eaten this step
        val eatJ = DoubleArray(ng * nh)       // juveniles eaten this step
        eatenPerDay.fill(0.0); eatenByPredator.fill(0.0)
        val avail = DoubleArray(ng * nh)
        val availJ = DoubleArray(ng * nh)
        for (pt in PodPredator.entries) {
            var units = 0.0
            for (p in predators) if (p.type == pt) units += p.units
            if (units <= 0) continue
            val active = if (night) pt.night else pt.day
            if (!active) continue
            val hoursActive = if (pt.day && pt.night) 24.0 else if (pt.night) NIGHT_H else DAY_H
            val cap = pt.podEqPerDay * units * dtH / hoursActive
            // encounters (exposed animals within reach, weighted by preference); each costs its size in handling
            var sum = 0.0
            for (gi in 0 until ng) {
                val w = pt.weights[gi]
                if (w <= 0) continue
                val e = expo[gi]
                val eq = P[gi].podEq
                for (h in 0 until nh) {
                    val i = gi * nh + h
                    val hidden = if (h == MicroHabitat.CHAETO.ordinal) 0.0 else pt.hiddenAccess
                    val a = w * (e * access(pt, h) + (1 - e) * hidden)
                    avail[i] = a * adults[i]
                    availJ[i] = a * (if (pt.fish && pt != PodPredator.MANDARIN) JUV_PREF_FISH else 1.0) * juveniles[i]
                    sum += (avail[i] + availJ[i] * JUV_MASS) * eq
                }
            }
            if (sum <= 0) continue
            val k = cap / (pt.halfSat * units + sum)
            for (gi in 0 until ng) {
                if (pt.weights[gi] <= 0) continue
                for (h in 0 until nh) {
                    val i = gi * nh + h
                    val nA = avail[i] * k
                    val nJ = availJ[i] * k
                    eat[i] += nA; eatJ[i] += nJ
                    eatenPerDay[gi] += (nA + nJ) / days
                    if (MicroGroup.entries[gi].isCopepod) eatenByPredator[pt.ordinal] += (nA + nJ) / days
                }
            }
        }
        // mysids, caprellids and flatworms eat nauplii and rotifers (§2.6 bottom rows)
        run {
            val eaters = adultsOf(MicroGroup.MYSID) * 150.0 + adultsOf(MicroGroup.CAPRELLID) * 10.0 + adultsOf(MicroGroup.FLATWORM) * 2.0
            if (eaters > 0) {
                val rot = adults[idx(MicroGroup.ROTIFER, MicroHabitat.COLUMN)]
                var juv = 0.0
                for (g in MicroGroup.entries) if (g.isCopepod) for (h in 0 until nh) juv += juveniles[g.ordinal * nh + h]
                val prey = rot + juv
                if (prey > 0) {
                    val take = eaters * days * prey / (prey + 50.0 * volumeL)
                    val fr = (take / prey).coerceAtMost(0.5)
                    eat[idx(MicroGroup.ROTIFER, MicroHabitat.COLUMN)] += rot * fr
                    eatenPerDay[MicroGroup.ROTIFER.ordinal] += rot * fr / days
                    for (g in MicroGroup.entries) if (g.isCopepod) for (h in 0 until nh) {
                        val i = g.ordinal * nh + h
                        eatJ[i] += juveniles[i] * fr; eatenPerDay[g.ordinal] += juveniles[i] * fr / days
                    }
                }
            }
        }
        // corals: per-polyp Holling II on the column density (§3.4); adult copepods escape (× 0.3)
        coralCapturePerDay = 0.0
        run {
            val polypsC = smallPolyps + mushrooms * 2
            val polypsZ = zoaPolyps
            if (polypsC + polypsZ > 0) {
                var dTot = 0.0
                val dA = DoubleArray(ng); val dJ = DoubleArray(ng)
                for (gi in 0 until ng) {
                    val g = MicroGroup.entries[gi]
                    if (CORAL_W[gi] <= 0) continue
                    val col = colFrac(gi)
                    var a = 0.0; var j = 0.0
                    for (h in 0 until nh) {
                        val i = gi * nh + h
                        val f = if (h == MicroHabitat.COLUMN.ordinal) 1.0 else if (h == MicroHabitat.CHAETO.ordinal) 0.0 else col
                        a += adults[i] * f; j += juveniles[i] * f
                    }
                    dA[gi] = a / vMl * (if (g.isCopepod || g == MicroGroup.MYSID) ADULT_ESCAPE else 1.0)
                    dJ[gi] = j / vMl
                    dTot += a / vMl + j / vMl
                }
                if (dTot > 0) {
                    val perPolypH = (polypsC * CORAL_CMAX + polypsZ * ZOA_CMAX) / (CORAL_DHALF + dTot) * dtH
                    for (gi in 0 until ng) {
                        val w = CORAL_W[gi]
                        if (w <= 0 || dA[gi] + dJ[gi] <= 0) continue
                        val takeA = perPolypH * w * dA[gi]
                        val takeJ = perPolypH * w * dJ[gi]
                        val totA = adultsOf(MicroGroup.entries[gi]); val totJ = juvenilesOf(MicroGroup.entries[gi])
                        val fa = if (totA > 0) (takeA / totA).coerceAtMost(0.5) else 0.0
                        val fj = if (totJ > 0) (takeJ / totJ).coerceAtMost(0.5) else 0.0
                        for (h in 0 until nh) {
                            val i = gi * nh + h
                            if (h == MicroHabitat.CHAETO.ordinal) continue
                            eat[i] += adults[i] * fa; eatJ[i] += juveniles[i] * fj
                            coralCapturePerDay += (adults[i] * fa + juveniles[i] * fj) / days
                        }
                        eatenPerDay[gi] += (totA * fa + totJ * fj) / days
                    }
                }
            }
            val perPolypDay = if (polypsC + polypsZ > 0) coralCapturePerDay / (polypsC + polypsZ) else 0.0
            if (perPolypDay > CORAL_FED_PER_POLYP) coralFedDays = CORAL_FED_DAYS
            else coralFedDays = max(0.0, coralFedDays - days)
        }

        // ---- populations ----
        bornPerDay.fill(0.0)
        var deadWet = 0.0
        for (gi in 0 until ng) {
            val p = P[gi]
            val g = MicroGroup.entries[gi]
            if (g == MicroGroup.PHYTO) continue
            val before = total(g)
            val r = p.rMax * growthMult[gi] * fFood[gi]
            val dTox = death[gi]
            val starve = p.starve * (1 - fFood[gi])
            val flush = if (g == MicroGroup.CALANOID || g == MicroGroup.ROTIFER) FLUSH_PER_DAY * min(flowEff, 1.5) else 0.0
            // breeding index (egg-sac carrying fraction): follows the birth drive with a half-day lag
            val drive = (growthMult[gi] * fFood[gi] * breedHeat[gi]).coerceIn(0.0, 1.0)
            breeding[gi] += (drive - breeding[gi]) * (1 - exp(-days / BREED_LAG_D))
            for (h in 0 until nh) {
                val i = gi * nh + h
                val k = kNow[i]
                val a = adults[i]
                if (g.hasJuveniles) {
                    val j = juveniles[i]
                    val tau = p.tauAt(s.tempC)
                    val crowd = if (k > 0) (1 - (a + j) / k).coerceIn(-1.0, 1.0) else -1.0
                    val births = p.birth * growthMult[gi] * fFood[gi] * breedHeat[gi] * a * max(0.0, crowd) * days
                    val mature = j * (1 - exp(-days / tau))
                    val dA = p.mBase + starve + flush + dTox + (if (crowd < 0) -crowd * 0.2 else 0.0)
                    val dJ = p.mJuv + starve + 0.5 * flush + dTox       // nauplii keep near the bottom, half the flushing
                    val diedA = a * (1 - exp(-dA * days))
                    val diedJ = j * (1 - exp(-dJ * days))
                    adults[i] = max(0.0, a + mature - diedA - min(eat[i], a))
                    juveniles[i] = max(0.0, j + births - mature - diedJ - min(eatJ[i], j))
                    bornPerDay[gi] += births / days
                    deadWet += (diedA + diedJ * JUV_MASS) * p.wetMg / 1000
                } else {
                    val grow = if (k > 0) a * r * (1 - a / k) * days else -a * 0.2 * days
                    val dA = p.mBase + starve + flush + dTox
                    val died = a * (1 - exp(-dA * days))
                    adults[i] = max(0.0, a + max(grow, -a * 0.5) - died - min(eat[i], a))
                    if (grow > 0) bornPerDay[gi] += grow / days
                    deadWet += died * p.wetMg / 1000
                }
            }
            // spill toward the habitat with spare room (pods in the pores re-seed the faces, the refugium feeds the tank)
            migrate(gi, days)
            trendPerDay[gi] = if (before > 1e-9) (total(g) - before) / before / days else 0.0
        }

        // ---- phytoplankton: N, P and light limited division; skimmer, filters, benthic filter feeders and grazers ----
        run {
            val pi = idx(MicroGroup.PHYTO, MicroHabitat.COLUMN)
            val cells = adults[pi]
            val nEq = s.no3 + s.nh3Total * (62.0 / 17.0)
            val fN = nEq / (PHYTO_KS_NO3 + nEq)
            val fP = s.po4 / (PHYTO_KS_PO4 + s.po4)
            val fL = 1 - exp(-light * 200 / 100)
            val fT = exp(-((s.tempC - 27) / 7).let { it * it }) * (if (s.tempC > 35) 0.0 else 1.0)
            val mu = P[MicroGroup.PHYTO.ordinal].rMax * PHYTO_DAY_GAIN * min(fN, fP) * fL * fT * (1 - phyto / PHYTO_K)
            // grazers: clearance in mL per animal per day
            val clear = (adultsOf(MicroGroup.CALANOID) * 3.0 + adultsOf(MicroGroup.CYCLOPOID) * colFrac(MicroGroup.CYCLOPOID.ordinal) * 1.0 +
                adultsOf(MicroGroup.ROTIFER) * 0.36 + adultsOf(MicroGroup.MYSID) * 20.0) / vMl
            val loss = PHYTO_EXPORT * min(max(flowEff, 0.3), 1.5) + PHYTO_BENTHIC_CLEAR + clear
            adults[pi] = max(PHYTO_SEED * vMl, cells * exp((mu - loss) * days))
            trendPerDay[MicroGroup.PHYTO.ordinal] = (adults[pi] - cells) / max(cells, 1.0) / days
        }

        // ---- hidden seeds and extinction ----
        for (gi in 0 until ng) {
            val g = MicroGroup.entries[gi]
            if (g == MicroGroup.PHYTO) continue
            val t = total(g)
            val ref = refCapacity(g)
            val poisoned = death[gi] > 0.5
            if (present[gi] && t < EXTINCT_FRAC * ref && poisoned) {
                for (h in 0 until nh) { adults[gi * nh + h] = 0.0; juveniles[gi * nh + h] = 0.0 }
                present[gi] = false; established[gi] = false
                continue
            }
            if (t >= ESTABLISHED_FRAC * ref) established[gi] = true
            if (!present[gi]) { for (h in 0 until nh) { adults[gi * nh + h] = 0.0; juveniles[gi * nh + h] = 0.0 }; continue }
            if (!poisoned && established[gi] && P[gi].seedFrac > 0) {
                val floor = P[gi].seedFrac * ref
                if (t < floor) {
                    // the seed lives in the rock pores (or the column for planktonic groups)
                    val h = if (g.planktonic) MicroHabitat.COLUMN.ordinal else MicroHabitat.ROCK.ordinal
                    adults[gi * nh + h] += floor - t
                }
            }
        }

        // ---- timers and feedback ----
        if (s.copper >= 0.1) copperResidualDays = COPPER_RESIDUAL_DAYS else if (s.copper < 0.02) copperResidualDays = max(0.0, copperResidualDays - days)
        fenbendazoleDays = max(0.0, fenbendazoleDays - days)
        praziquantelDays = max(0.0, praziquantelDays - days)
        toxinHours = max(0.0, toxinHours - dtH)
        deadWetG += deadWet
        // copepod grazing on the film: 0.005 mg DW per harpacticoid per day (§3.1 food feedback)
        algae?.podGrazeGPerDay = (total(MicroGroup.HARPACTICOID) * HARP_FILM_MG + total(MicroGroup.MICROSNAIL) * SNAIL_FILM_MG) / 1000
    }

    private fun migrate(gi: Int, days: Double) {
        var n = 0.0; var k = 0.0
        for (h in 0 until nh) { val i = gi * nh + h; n += adults[i] + juveniles[i]; k += kNow[i] }
        if (k <= 0 || n <= 0) return
        val ratio = n / k
        val f = 1 - exp(-MIGRATION_PER_DAY * days)
        for (h in 0 until nh) {
            val i = gi * nh + h
            val target = ratio * kNow[i]
            val cur = adults[i] + juveniles[i]
            val move = (target - cur) * f
            if (cur > 0) {
                val fa = adults[i] / cur
                adults[i] = max(0.0, adults[i] + move * fa); juveniles[i] = max(0.0, juveniles[i] + move * (1 - fa))
            } else adults[i] = max(0.0, move)
        }
    }

    /** Predator reach into a habitat for exposed animals (§3.1 a_p,h). */
    private fun access(pt: PodPredator, h: Int): Double = when (MicroHabitat.entries[h]) {
        MicroHabitat.ROCK, MicroHabitat.GLASS -> 1.0
        MicroHabitat.SAND -> pt.sandAccess
        MicroHabitat.CHAETO -> if (pt.fish) 0.0 else 0.02
        MicroHabitat.COLUMN -> if (pt.fish) 1.0 else 0.2
    }

    private fun flatwormArea(): Double = flatwormAreaOverride ?: litRockCm2

    /** Capacity of habitat [h] for group [gi] at food factor [f] (§3.1: K_base · refuge · (0.3 + 0.7 f)). */
    private fun kOf(gi: Int, h: Int, f: Double, naupliiPerL: Double, doc: Double, s: WaterState): Double {
        val p = P[gi]
        val g = MicroGroup.entries[gi]
        val hab = MicroHabitat.entries[h]
        val base = p.k[h]
        if (base <= 0) return 0.0
        return when {
            g == MicroGroup.FLATWORM -> if (hab == MicroHabitat.ROCK) base * flatwormArea() else 0.0
            hab == MicroHabitat.COLUMN -> when (g) {
                // Tigger pods: 0.3 per L at background, a few per L on a 3x/week phyto routine, ~40 per L after a big dose
                // (§3.2 says 5 per L with phyto >= 10^4; steeper here so a 9 per L bottle can boom, §3.3)
                MicroGroup.CALANOID -> max(0.3, base * (phytoMem / CAL_K_HALF).pow(3).let { it / (1 + it) }) * volumeL
                // rotifers: 0.05 per mL background, ~50 per mL with phyto >= 10^5 (§3.2)
                MicroGroup.ROTIFER -> max(0.05, base * phytoMem / (phytoMem + 1e5)) * volumeL * 1000
                // ciliates: 100 per mL, 10^3-10^4 during a new-tank bacterial bloom (ammonia, DOC)
                MicroGroup.CILIATE -> (base + 1e4 * (s.nh3Total / 1.0).coerceIn(0.0, 1.0) + 1e3 * doc) * volumeL * 1000
                // mysids: 0.5 per L with pod nauplii > 2 per L, else 0.05 (§3.2)
                MicroGroup.MYSID -> (if (naupliiPerL > 2) base else 0.05) * volumeL
                else -> base * (0.3 + 0.7 * f) * volumeL
            }
            else -> base * size(hab) * (0.3 + 0.7 * f)
        }
    }

    /** Capacity at full food (reference for seeds, starting values and "% of K"). */
    private fun kRef(gi: Int, h: Int): Double {
        val g = MicroGroup.entries[gi]
        val hab = MicroHabitat.entries[h]
        val base = P[gi].k[h]
        if (base <= 0) return 0.0
        return when {
            g == MicroGroup.FLATWORM -> if (hab == MicroHabitat.ROCK) base * flatwormArea() else 0.0
            hab == MicroHabitat.COLUMN -> when (g) {
                MicroGroup.CALANOID -> 1.0 * volumeL
                MicroGroup.ROTIFER -> 0.05 * volumeL * 1000
                MicroGroup.CILIATE -> base * volumeL * 1000
                MicroGroup.MYSID -> 0.5 * volumeL
                MicroGroup.PHYTO -> 0.0
                else -> base * volumeL
            }
            else -> base * size(hab)
        }
    }

    private fun expTarget(gi: Int, night: Boolean) = if (night) P[gi].expNight else P[gi].expDay
    private fun colFrac(gi: Int): Double {
        val p = P[gi]
        if (MicroGroup.entries[gi].planktonic) return 1.0
        val e = if (expo[gi] < 0) expTarget(gi, night) else expo[gi]
        // column fraction follows the exposure state between its day and night values
        val span = p.expNight - p.expDay
        val t = if (kotlin.math.abs(span) > 1e-9) ((e - p.expDay) / span).coerceIn(0.0, 1.0) else (if (night) 1.0 else 0.0)
        return p.colDay + (p.colNight - p.colDay) * t
    }

    /** First call: start every present group at its §3.2 mature value. */
    private fun ensureInit(s: WaterState?) {
        if (initialized) return
        initialized = true
        cached = null
        if (s != null) volumeL = s.volumeL.coerceAtLeast(1.0)
        for (gi in 0 until ng) {
            val g = MicroGroup.entries[gi]
            if (g == MicroGroup.PHYTO) { adults[idx(g, MicroHabitat.COLUMN)] = PHYTO_START * volumeL * 1000; continue }
            if (!present[gi]) continue
            for (h in 0 until nh) {
                val k = kRef(gi, h) * P[gi].start
                adults[gi * nh + h] = if (g.hasJuveniles) k * 0.75 else k
                juveniles[gi * nh + h] = if (g.hasJuveniles) k * 0.25 else 0.0
            }
        }
    }

    private fun buildState(): MicrofaunaState {
        val tot = DoubleArray(ng) { total(MicroGroup.entries[it]) }
        val juv = DoubleArray(ng) { gi -> val a = adultsOf(MicroGroup.entries[gi]); if (a > 0) juvenilesOf(MicroGroup.entries[gi]) / a else 0.0 }
        val ex = DoubleArray(ng) { exposure(MicroGroup.entries[it]) }
        val vis = FloatArray(ng * MicrofaunaState.SCENES)
        for (gi in 0 until ng) for (sc in MicroScene.entries) vis[gi * MicrofaunaState.SCENES + sc.ordinal] = visibleDensity(gi, sc).toFloat()
        // torch test: harpacticoids, cyclopoids, amphipods and ostracods on 100 cm² of glass an hour after lights-out
        var torch = 0.0
        for (g in listOf(MicroGroup.HARPACTICOID, MicroGroup.CYCLOPOID, MicroGroup.GAMMARID, MicroGroup.OSTRACOD)) {
            torch += 100 * glassDensity(g.ordinal, P[g.ordinal].expNight)
        }
        var born = 0.0; var eaten = 0.0
        for (g in MicroGroup.entries) if (g.isCopepod) { born += bornPerDay[g.ordinal]; eaten += eatenPerDay[g.ordinal] }
        return MicrofaunaState(
            tot, juv, breeding.copyOf(), ex, trendPerDay.copyOf(), present.copyOf(), vis, phytoPerMl, night, torch,
            born, eaten, coralCapturePerDay, coralFedDays > 0, toxinHours > 0, refugiumL,
        )
    }

    /** Animals per cm² of the lower glass at exposure [e]: the glass population plus the night emergence from the rock (§2.2). */
    private fun glassDensity(gi: Int, e: Double): Double {
        val g = adults[gi * nh + MicroHabitat.GLASS.ordinal] + juveniles[gi * nh + MicroHabitat.GLASS.ordinal] * JUV_VISIBLE
        val rock = adults[gi * nh + MicroHabitat.ROCK.ordinal] + juveniles[gi * nh + MicroHabitat.ROCK.ordinal] * JUV_VISIBLE
        val spill = GLASS_SPILL * ((e - P[gi].expDay) / max(1e-9, P[gi].expNight - P[gi].expDay)).coerceIn(0.0, 1.0)
        return e * (g + spill * rock) / glassCm2
    }

    /** Expected animals in view per cm² (surfaces) or per mL (water) right now (§5.2: density × exposure). */
    private fun visibleDensity(gi: Int, sc: MicroScene): Double {
        val g = MicroGroup.entries[gi]
        val e = exposure(g)
        fun at(h: MicroHabitat) = adults[gi * nh + h.ordinal] + juveniles[gi * nh + h.ordinal] * JUV_VISIBLE
        return when (sc) {
            MicroScene.ROCK -> if (g == MicroGroup.FLATWORM) at(MicroHabitat.ROCK) / flatwormArea() * LIT_SHARE_IN_VIEW
                else e * at(MicroHabitat.ROCK) / size(MicroHabitat.ROCK)
            MicroScene.SAND -> e * at(MicroHabitat.SAND) / sandCm2
            MicroScene.GLASS -> if (g.planktonic) 0.0 else glassDensity(gi, e)
            MicroScene.WATER -> {
                if (g == MicroGroup.PHYTO) return phytoPerMl
                val col = colFrac(gi)
                var n = 0.0
                for (h in MicroHabitat.entries) if (h != MicroHabitat.CHAETO) n += at(h) * (if (h == MicroHabitat.COLUMN) 1.0 else col)
                n / (volumeL * 1000)
            }
        }
    }

    // ============================================================== parameters
    enum class Food { FILM, DETRITUS, PHYTO, PHYTO_OR_DETRITUS, NAUPLII, DOC, LIGHT, NUTRIENTS }
    enum class Tox { COPEPOD, INVERT, TOLERANT }

    /**
     * Per-group parameters (research 10 §6.1). Rates per day at 27 °C; [gen] is G(27) in days; [k] per [MicroHabitat]
     * (cm² for benthic habitats with the §3.1 refuge factor folded in, litres for the column); [ks] in the units of
     * [food]; [podEq] prey size in harpacticoid equivalents; [wetMg] wet mass per adult; [start] fraction of the
     * reference capacity at the start of a mature tank (§3.2); [seedFrac] the hidden floor while present.
     */
    class Params(
        val rMax: Double, val gen: Double, val tOpt: Double, val tWidth: Double, val tLethal: Double,
        val mBase: Double, val mJuv: Double, val starve: Double, val food: Food, val ks: Double,
        val expDay: Double, val expNight: Double, val colDay: Double, val colNight: Double,
        val k: DoubleArray, val podEq: Double, val wetMg: Double, val start: Double, val seedFrac: Double,
        val presentByDefault: Boolean, val tox: Tox,
        /** Juvenile stage (nauplii, young) as a share of the generation time; calanoid copepodites join the adults early. */
        juvShare: Double = 0.6,
    ) {
        /** Juvenile stage length at 27 °C. */
        private val tau27 = juvShare * gen
        /**
         * Per-adult birth rate chosen so the juvenile/adult system grows at exactly [rMax] with full food at 27 °C
         * (Euler-Lotka for two stages: (r + mA)(r + 1/τ + mJ) = b/τ).
         */
        val birth: Double = tau27 * (rMax + mBase) * (rMax + 1 / tau27 + mJuv)
        fun tauAt(t: Double): Double = (tau27 * 2.5.pow((27 - t) / 10)).coerceIn(0.5, 120.0)
    }

    companion object {
        const val STEP_H = 0.25
        const val EXPOSURE_LAG_H = 0.4
        const val DAY_H = 11.0
        const val NIGHT_H = 13.0
        /** Lower glass band the pods use (cm above the sand). */
        const val GLASS_BAND_CM = 8.0
        /** Filament surface per litre of loose chaeto (research 10 unit notes: 1 L ≈ 0.3 m²). */
        const val CHAETO_CM2_PER_L = 3000.0
        /** Detritus pool (ppm TAN-eq) to g m-2 of settled detritus, and the bacterial/diatom biofilm of a mature tank. */
        const val DETRITUS_G_M2_PER_PPM = 3.0
        const val BIOFILM_G_M2 = 2.5
        const val HAIR_FILM_SHARE = 0.1
        /** Ciliates and film as calanoid / cyclopoid food in phyto-cell equivalents. */
        const val CILIATE_CELL_EQ = 50.0
        const val PHYTO_PER_FILM = 500.0
        /** Fish take juveniles at a fifth of the adult preference (§3.1); a nauplius is a tenth of an adult. */
        const val JUV_PREF_FISH = 0.2
        const val JUV_MASS = 0.1
        /** Juveniles count as a third of an adult in the 1x/10x counts (they are mostly below the resolution). */
        const val JUV_VISIBLE = 0.3
        /** Share of the rock population that crawls out onto the glass at night (§2.2 "onto open rock, glass"). */
        const val GLASS_SPILL = 0.5
        const val LIT_SHARE_IN_VIEW = 1.0
        const val MIGRATION_PER_DAY = 0.3
        const val BREED_LAG_D = 0.5
        /** Calanoids and rotifers swept to the overflow (§4 flow: 0.1 d-1 with a skimmer). */
        const val FLUSH_PER_DAY = 0.1
        /** Adult copepods escape coral polyps (§3.4 suggests × 0.3; 0.5 so §6.3 #9's resident capture reaches 5-40 a day). */
        const val ADULT_ESCAPE = 0.5
        const val CORAL_CMAX = 1.0
        const val ZOA_CMAX = 0.3
        const val CORAL_DHALF = 2.0
        /** Prey per polyp per day that counts as a well-fed day (§3.4). */
        const val CORAL_FED_PER_POLYP = 0.5
        const val CORAL_FED_DAYS = 3.0
        const val HARP_FILM_MG = 0.005
        const val SNAIL_FILM_MG = 0.5
        const val FLATWORM_LIGHT_BONUS = 0.3
        const val TOXIN_H = 48.0
        /**
         * Copper leaching back out of rock after a treatment keeps r × 0.5 (§4 says 30 d; held for 60 d after the copper
         * is gone so §6.3 #6's re-seed on day 30 still feels it).
         */
        const val COPPER_RESIDUAL_DAYS = 60.0
        const val EXTINCT_FRAC = 1e-4
        const val ESTABLISHED_FRAC = 0.2
        // phytoplankton
        const val PHYTO_START = 100.0
        const val PHYTO_MEMORY_D = 6.0
        const val CAL_K_HALF = 1.2e5
        const val PHYTO_SEED = 100.0
        const val PHYTO_K = 1e7
        const val PHYTO_KS_NO3 = 0.06          // ppm NO3 ≈ 1 µM N
        const val PHYTO_KS_PO4 = 0.01          // ppm PO4 ≈ 0.1 µM P
        const val PHYTO_DAY_GAIN = 2.4
        /** Skimmer, socks and the overflow (§3.3: 0.3 d-1) and the benthic filter feeders that clear a dose in 1-2 days. */
        const val PHYTO_EXPORT = 0.3
        const val PHYTO_BENTHIC_CLEAR = 1.8

        /** Copper mortality tiers (§4): ppm → per-day death rate. */
        private val CU_X = doubleArrayOf(0.0, 0.01, 0.02, 0.05, 0.15)
        private val CU_COPEPOD = doubleArrayOf(0.0, 0.0, 0.105, 0.69, 2.0)
        private val CU_INVERT = doubleArrayOf(0.0, 0.0, 0.05, 0.36, 2.0)

        /**
         * Coral prey preferences per group (§2.6 GSP / clove row has copepods 0.3, rotifers 0.6; the copepod 0.3 is
         * already the §3.4 escape factor [ADULT_ESCAPE], so planktonic copepods count 1 here). Benthic harpacticoids 0.
         */
        private val CORAL_W = DoubleArray(MicroGroup.entries.size).also {
            it[MicroGroup.CALANOID.ordinal] = 1.0; it[MicroGroup.CYCLOPOID.ordinal] = 1.0
            it[MicroGroup.ROTIFER.ordinal] = 0.6; it[MicroGroup.MYSID.ordinal] = 0.1
        }

        val LIVE_ROCK_GROUPS = listOf(
            MicroGroup.HARPACTICOID, MicroGroup.CYCLOPOID, MicroGroup.GAMMARID, MicroGroup.OSTRACOD, MicroGroup.ISOPOD,
            MicroGroup.BRISTLEWORM, MicroGroup.MICROSNAIL, MicroGroup.SPAGHETTI_WORM, MicroGroup.FORAM, MicroGroup.CILIATE,
        )

        /**
         * Heat mortality per day (§4, §6.1): none below T_lethal − 2, 20 %/d at T_lethal − 2 rising to 50 %/d at
         * T_lethal, then steeper (the §6.3 #7 ordering).
         */
        fun heatDeath(t: Double, tl: Double): Double = when {
            t < tl - 2 -> 0.0
            t < tl -> 0.223 + (0.69 - 0.223) * (t - (tl - 2)) / 2
            else -> min(3.0, 0.69 + 0.9 * (t - tl))
        }

        fun interp(xs: DoubleArray, ys: DoubleArray, x: Double): Double = AlgaeModel.interp(xs, ys, x)

        private fun k(rock: Double = 0.0, sand: Double = 0.0, glass: Double = 0.0, chaeto: Double = 0.0, column: Double = 0.0) =
            doubleArrayOf(rock, sand, glass, chaeto, column)

        /** Order: [MicroGroup] entries. */
        val P: Array<Params> = arrayOf(
            // harpacticoid (Tisbe): r 0.30, G 10 d at 27 C, Topt 28, lethal 37; K 30 per cm² rock film × refuge, 10 sand × 0.4, 5 glass
            Params(0.30, 10.0, 28.0, 6.0, 37.0, 0.03, 0.10, 0.05, Food.FILM, 5.0, 0.15, 0.6, 0.01, 0.05,
                k(rock = 5.0, sand = 1.5, glass = 1.5, chaeto = 20.0), 1.0, 0.01, 0.6, 0.05, true, Tox.COPEPOD),
            // calanoid (Parvocalanus "Tigger"): r 0.45, G 7 d, Topt 26, lethal 34; phyto-limited column K
            Params(0.6, 7.0, 26.0, 6.0, 34.0, 0.05, 0.05, 0.0, Food.PHYTO, 5000.0, 1.0, 1.0, 1.0, 1.0,
                k(column = 150.0), 0.5, 0.004, 0.2, 0.05, true, Tox.COPEPOD, juvShare = 0.45),
            // cyclopoid (Apocyclops, Oithona): r 0.40, G 7 d, Topt 28, lethal 37; rock pores and the column
            Params(0.40, 7.0, 28.0, 6.0, 37.0, 0.04, 0.15, 0.05, Food.PHYTO_OR_DETRITUS, 2000.0, 0.3, 0.8, 0.2, 0.6,
                k(rock = 0.5, chaeto = 5.0), 1.0, 0.012, 0.4, 0.02, true, Tox.COPEPOD),
            // gammarid amphipod: r 0.08, G 30 d, Topt 27, lethal 36 (§6.3 #7: 30-60 % lost at 35 C)
            Params(0.08, 30.0, 27.0, 6.0, 36.0, 0.02, 0.05, 0.02, Food.FILM, 3.0, 0.05, 0.5, 0.005, 0.05,
                k(rock = 0.5, sand = 0.05, chaeto = 0.3), 10.0, 5.0, 0.3, 0.01, true, Tox.INVERT),
            // caprellid: r 0.06, G 40, lethal 33; soft-coral / macro / chaeto surface; absent unless seeded
            Params(0.06, 40.0, 25.0, 6.0, 33.0, 0.02, 0.0, 0.02, Food.DETRITUS, 2.0, 0.6, 0.6, 0.0, 0.0,
                k(rock = 0.02, chaeto = 0.2), 8.0, 3.0, 0.0, 0.01, false, Tox.INVERT),
            // mysid: r 0.10, G 25, Topt 26, lethal 32; column, nauplii-fed; rarely self-sustaining
            Params(0.10, 25.0, 26.0, 6.0, 32.0, 0.03, 0.05, 0.1, Food.NAUPLII, 2.0, 0.3, 0.9, 0.3, 0.9,
                k(column = 0.5), 10.0, 6.0, 0.0, 0.0, false, Tox.INVERT),
            // ostracod: r 0.05, G 35, lethal 36; sand 5 per cm²
            Params(0.05, 35.0, 27.0, 6.0, 36.0, 0.02, 0.0, 0.02, Food.DETRITUS, 1.0, 0.05, 0.3, 0.0, 0.0,
                k(sand = 5.0, rock = 0.3, chaeto = 1.0), 1.0, 0.02, 0.2, 0.01, true, Tox.INVERT),
            // isopod (harmless sphaeromatids): r 0.04, G 45, lethal 35; rubble
            Params(0.04, 45.0, 27.0, 6.0, 35.0, 0.01, 0.0, 0.01, Food.DETRITUS, 1.0, 0.1, 0.4, 0.0, 0.0,
                k(rock = 0.1), 3.0, 1.0, 0.3, 0.01, true, Tox.INVERT),
            // rotifer (Brachionus): r 0.70, G 1.3, Topt 28, lethal 40; m 0.2; column, phyto-fed
            Params(0.70, 1.3, 28.0, 7.0, 40.0, 0.2, 0.0, 0.3, Food.PHYTO, 2e4, 1.0, 1.0, 1.0, 1.0,
                k(column = 60.0), 0.1, 0.0005, 0.2, 0.04, true, Tox.COPEPOD),
            // ciliate: r 1.4, G 0.7, lethal 40; 100 per mL, bloom with ammonia/DOC
            Params(1.4, 0.7, 28.0, 12.0, 40.0, 0.3, 0.0, 0.0, Food.DOC, 0.3, 1.0, 1.0, 0.5, 0.5,
                k(column = 100.0), 0.01, 0.0, 0.1, 0.05, true, Tox.TOLERANT),
            // phytoplankton (handled separately: N, P, light; export and grazing)
            Params(0.8, 0.9, 27.0, 7.0, 36.0, 0.3, 0.0, 0.0, Food.NUTRIENTS, 1.0, 1.0, 1.0, 1.0, 1.0,
                k(), 0.0, 0.0, 0.0, 0.0, true, Tox.TOLERANT),
            // bristleworm: r 0.03, G 60, lethal 36; 0.02 per cm² sand + rock
            Params(0.03, 60.0, 27.0, 6.0, 36.0, 0.005, 0.0, 0.01, Food.DETRITUS, 1.0, 0.02, 0.5, 0.0, 0.0,
                k(rock = 0.02, sand = 0.02), 50.0, 200.0, 0.5, 0.05, true, Tox.INVERT),
            // micro-snails (Collonista): r 0.03, G 60, lethal 35; 0.05 per cm² rock face
            Params(0.03, 60.0, 27.0, 6.0, 35.0, 0.005, 0.0, 0.01, Food.FILM, 1.0, 0.1, 0.7, 0.0, 0.0,
                k(rock = 0.05, glass = 0.005), 5.0, 10.0, 0.4, 0.05, true, Tox.INVERT),
            // spaghetti worm: r 0.01, G 90; one per 50 cm² of the rock-sand junction (~0.005 per cm² sand, est.)
            Params(0.01, 90.0, 27.0, 6.0, 36.0, 0.002, 0.0, 0.0, Food.DETRITUS, 1.0, 0.6, 1.0, 0.0, 0.0,
                k(sand = 0.005), 0.0, 500.0, 0.6, 0.1, true, Tox.INVERT),
            // forams: r 0.01, G 90; 10 per cm² shaded rock (folded into the rock habitat at the shade share)
            Params(0.01, 90.0, 27.0, 6.0, 35.0, 0.002, 0.0, 0.0, Food.DETRITUS, 1.0, 1.0, 1.0, 0.0, 0.0,
                k(rock = 5.0), 0.0, 0.05, 0.3, 0.05, true, Tox.INVERT),
            // red flatworm (Convolutriloba): r 0.15 (+30 % in strong light), G 4.5, Topt 26, lethal 32; lit rock 20 per cm²
            Params(0.15, 4.5, 26.0, 6.0, 32.0, 0.0, 0.0, 0.0, Food.LIGHT, 0.3, 1.0, 1.0, 0.0, 0.0,
                k(rock = 20.0), 2.0, 0.1, 0.0, 0.0, false, Tox.INVERT),
        )
    }
}
