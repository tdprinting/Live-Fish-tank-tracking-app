package com.reeflive.core.sim

import com.reeflive.core.geom.Rng
import com.reeflive.core.geom.Vec3
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * How one kind of fish breeds and how its young develop (docs/research/28_clownfish_breeding.md). [Brood] runs every
 * spec the same way: eggs from [Reproduction] hatch into a larval cohort, the larvae drift in the display (where fish,
 * corals and the filter take nearly all of them) unless the keeper raises them in the nursery tank, they metamorphose
 * into fry, and grown fry can be moved into the display as ordinary animals. A new species needs only a spec here
 * and its spawning behaviour in [Reproduction].
 */
data class BroodSpec(
    /** Stable key (saves, model names: `models/<key>_larva.glb`). */
    val key: String,
    val brainKind: BrainKind,
    /** Shown in the UI: "baby clowns". */
    val babyName: String,
    // ---- eggs
    /** Eggs per clutch for a female of [femaleCm], before the first-clutch discount and the ±20 % spread. */
    val eggsBase: Double, val eggsPerCm: Double, val eggsRefCm: Double,
    /** Incubation (days) at [hatchRefC]; shorter by [hatchDaysPerC] per °C warmer; clamped to [hatchMin]..[hatchMax]. */
    val hatchDaysRef: Double, val hatchRefC: Double, val hatchDaysPerC: Double, val hatchMin: Double, val hatchMax: Double,
    /** Share of a tended clutch that hatches. */
    val hatchSuccess: Double,
    // ---- larvae
    val hatchLengthMm: Double,
    /** Days the larvae live on their yolk before they must catch food. */
    val yolkDays: Double,
    /** Development days to metamorphosis when well fed (slower when underfed) and the length reached there. */
    val metamorphDays: Double, val metamorphLengthMm: Double,
    /** From this development day the larvae need baby brine (Artemia nauplii) on top of rotifers. */
    val brineFromDay: Double,
    // ---- fry growth (von Bertalanffy after metamorphosis)
    val linfMm: Double, val growthKPerDay: Double,
    /** Length (mm) from which fry are big enough to be safe-ish in a community display. */
    val releaseMm: Double,
    /** Days after metamorphosis the routine weans them from baby brine onto crushed pellets. */
    val weanDays: Double = 10.0,
    /** Most animals of this kind the display takes (fry beyond that stay in the nursery). */
    val displayCap: Int,
    /** Hazard weights (per hour, per adult-sized fish) for fish eating larvae in the display, by brain. */
    val larvaPredators: Map<BrainKind, Double>,
    /** Clutch size limits. */
    val eggsMin: Int = 80, val eggsMax: Int = 1000,
    /**
     * Eggs scattered in open water (tangs): no nest and no parent care; they float for a day, then hatch. A keeper
     * raising the babies skims them off the surface with an egg collector at once, up to [nurseryStock] eggs.
     */
    val pelagic: Boolean = false,
    val nurseryStock: Int = Int.MAX_VALUE,
    /** First food: copepod nauplii until this development day (0: none, rotifers from the start). */
    val copepodsUntil: Double = 0.0,
    /** Rotifers are small enough for their mouths from this development day. */
    val rotifersFrom: Double = 0.0,
    /** The visible change into a little fish: starts [metaLead] days before [metamorphDays] and takes [metaSpan] days. */
    val metaLead: Double = 2.0, val metaSpan: Double = 3.0,
    /** Model stage "postlarva": from this development day (null: once half changed) and while shorter than [postlarvaMaxMm]. */
    val postlarvaFromDay: Double? = null, val postlarvaMaxMm: Double = 12.0,
    /** Daily hazard of a well-fed larva, and the known die-offs on top of it (development days). */
    val larvaHazard: Double = 0.04,
    val crashes: List<BroodCrash> = emptyList(),
    /** Daily hazard per °C outside 24-29.5 °C. */
    val tempHazard: Double = 0.3,
    /** Food eaten and waste made per larva, relative to a clown larva (tang larvae are a fraction of the size). */
    val loadPerLarva: Double = 1.0,
    /** Fry a 10-gallon grow-out holds before crowding costs. */
    val fryCap: Double = 150.0,
    /** Food density the breeder's routine keeps up (1 = a full feeding density). */
    val routineFood: Double = 0.75,
    val words: BroodWords = BroodWords(),
)

/** A die-off window in development days (first feeding, flexion, metamorphosis), extra daily hazard. */
data class BroodCrash(val fromDay: Double, val toDay: Double, val perDay: Double)

/** The card's words for one species' young (kid-readable). */
data class BroodWords(
    val adults: String = "clowns",
    val larva: String = "larvae (see-through, eating rotifers)",
    val postlarva: String = "changing into little fish (first white stripe coming)",
    val fry: String = "fry with white stripes",
    val juvenile: String = "juveniles, big enough for the tank",
    val careFirst: String = "The routine feeds rotifers in green water twice a day, siphons the bottom and keeps a 14 hour day.",
    val careSecond: String = "",
    val careChanging: String = "The routine adds baby brine shrimp now. Changing into little fish is the riskiest time.",
    val raiseNote: String = "Catch the larvae at hatching and grow them in a nursery tank.",
    val raisingNote: String = "At hatching you lure the larvae with a flashlight and move them to a nursery tank.",
    val smallNote: String = "Still small: the hawkfish or dottyback may eat it.",
)

object BroodSpecs {
    /**
     * Ocellaris clownfish (research 28): 12-15 day spawning cycle, clutch 100-1000 eggs growing with the female,
     * 7 d at 28.5 °C to ~10 d at 26 °C, hatch ~1 h after lights-out, ~4-5 mm larvae, metamorphosis (first white
     * head bar) at about 8-12 days in hobby rearing, Artemia from day 5-6 (Setiawati 2008), 3-4 cm at 6-8 months.
     */
    val CLOWN = BroodSpec(
        key = "clown", brainKind = BrainKind.CLOWN, babyName = "baby clowns",
        eggsBase = 300.0, eggsPerCm = 70.0, eggsRefCm = 8.0,
        hatchDaysRef = 10.0, hatchRefC = 26.0, hatchDaysPerC = 1.2, hatchMin = 6.5, hatchMax = 12.0,
        hatchSuccess = 0.9,
        hatchLengthMm = 4.5, yolkDays = 1.5, metamorphDays = 10.0, metamorphLengthMm = 9.0, brineFromDay = 5.0,
        // research 29 §7: ~19 mm at 40 days post hatch (CMFRI)
        linfMm = 110.0, growthKPerDay = 0.0035, releaseMm = 25.0, displayCap = 8,
        larvaPredators = mapOf(
            // pajama cardinals feed on plankton at night, exactly when the larvae hatch (research 02 §3C)
            BrainKind.CARDINAL to 0.9, BrainKind.DOTTYBACK to 0.4, BrainKind.HAWK to 0.35, BrainKind.TANG to 0.3,
            BrainKind.CLOWN to 0.25, BrainKind.LEOPARD_WRASSE to 0.2, BrainKind.WRASSE to 0.1, BrainKind.DIAMOND_GOBY to 0.1,
        ),
    )

    /**
     * Blue tang (research 41): group spawning at dusk around the full moon, pelagic eggs (0.8 mm) hatching in about a
     * day, first food copepod nauplii at day 3, rotifers from day 12, Artemia from day 20 (UF Tropical Aquaculture Lab,
     * DiMaggio 2017 [M]); die-offs at first feeding (days 5-8) and flexion (day 20) [M]; first blue at day 50 and
     * adult colours by day 78 [M]/[S]. UF raised 27 juveniles from 50,000 eggs: survival here is boosted for the
     * game (about 4 % with the routine), and the settlement length and juvenile growth are estimates (see the doc).
     */
    val TANG = BroodSpec(
        key = "tang", brainKind = BrainKind.TANG, babyName = "baby tangs",
        // ~8,000 eggs per female per spawn at the UF broodstock (50,000 from two females over 3 days) [M], scaled down for home-sized fish [E]
        eggsBase = 5000.0, eggsPerCm = 500.0, eggsRefCm = 18.0, eggsMin = 500, eggsMax = 15000,
        // ~20 h at 26 °C (yellow tang [A]), ~24 h (blue tang [S]); Q10 ~2 [E]
        hatchDaysRef = 0.9, hatchRefC = 26.0, hatchDaysPerC = 0.06, hatchMin = 0.65, hatchMax = 1.2,
        hatchSuccess = 0.8,
        // hatch ~1.8 mm [E], ~3 mm at day 5 [S]; acronurus settles ~25 mm [A: acanthurid acronurus 20-30 mm, E]
        hatchLengthMm = 1.8, yolkDays = 3.0, metamorphDays = 60.0, metamorphLengthMm = 25.0, brineFromDay = 20.0,
        // ~12 cm at 9-12 months [S]; K a DESIGN ESTIMATE toward the 31 cm maximum
        linfMm = 310.0, growthKPerDay = 0.0022, releaseMm = 40.0, weanDays = 14.0, displayCap = 4,
        larvaPredators = mapOf(
            BrainKind.CARDINAL to 0.9, BrainKind.CLOWN to 0.4, BrainKind.DOTTYBACK to 0.4, BrainKind.HAWK to 0.3,
            BrainKind.TANG to 0.3, BrainKind.LEOPARD_WRASSE to 0.2, BrainKind.WRASSE to 0.15, BrainKind.DIAMOND_GOBY to 0.1,
        ),
        pelagic = true, nurseryStock = 1500,   // 50 eggs/L as at UF [M], in the 38 L nursery
        copepodsUntil = 25.0, rotifersFrom = 12.0,
        metaLead = 10.0, metaSpan = 12.0, postlarvaFromDay = 30.0, postlarvaMaxMm = 0.0,
        larvaHazard = 0.03,
        crashes = listOf(BroodCrash(5.0, 8.0, 0.25), BroodCrash(19.0, 22.0, 0.15), BroodCrash(50.0, 60.0, 0.03)),
        tempHazard = 0.8,   // "larvae can die within hours of a temperature drop" (UF [S])
        loadPerLarva = 0.2, fryCap = 40.0,
        // copepod nauplii fed three times a day at UF [M]: the routine keeps them dense, tang larvae starve fast
        routineFood = 0.95,
        words = BroodWords(
            adults = "tangs",
            larva = "larvae (tiny and see-through, eating copepods)",
            postlarva = "see-through silver babies with long spines",
            fry = "little blue tangs",
            juvenile = "young tangs, big enough for the tank",
            careFirst = "The routine feeds baby copepods three times a day in green water. Tang babies have tiny mouths, so the first week is the hardest.",
            careSecond = "Now eating copepods and rotifers. Around day 20 their fins form, and many don't make it.",
            careChanging = "Eating baby brine. At about 7 weeks they turn blue and settle to the bottom.",
            raiseNote = "When they spawn, skim the floating eggs with an egg collector and grow them in a nursery tank.",
            raisingNote = "When they spawn you skim the floating eggs off the top and move them to a nursery tank.",
            smallNote = "Still small: the hawkfish may eat it.",
        ),
    )

    val ALL = listOf(CLOWN, TANG)
    fun of(kind: BrainKind): BroodSpec? = ALL.firstOrNull { it.brainKind == kind }
    fun byKey(key: String): BroodSpec? = ALL.firstOrNull { it.key == key }
}

/** One hatch of larvae and what is left of it, tracked as a number (not as animals) so hundreds cost nothing. */
class BroodCohort(
    val id: Int,
    val spec: BroodSpec,
    val hatchedAt: Double,
    /** Larvae at hatching. */
    val hatched: Int,
    /** Where they hatched (the nest), for the visuals. */
    val origin: Vec3,
    /** Parent colour forms (Organism.variant: null = orange, "darwin_black"), for the babies moved to the display. */
    val parentVariants: List<String?>,
    /** The parents' species sheet id (the babies are the same species). */
    val speciesId: String,
) {
    var count = hatched.toDouble()
    var inNursery = false
    /** Development days (feeding speeds it up, hunger slows it). */
    var devDays = 0.0
    var lengthMm = spec.hatchLengthMm
    /** How well fed they are, 0 starving .. 1 full. */
    var fed = 1.0
    var released = 0
    fun ageDays(now: Double) = (now - hatchedAt) / 86400.0
    /** Days left before pelagic eggs hatch (0 once hatched) and days since hatching, both on the cohort's own clock
     *  (the nursery's runs [Brood.growSpeed] times faster). */
    var eggDays = 0.0
    var lifeDays = 0.0
    val eggs: Boolean get() = eggDays > 0
    val metamorphosed: Boolean get() = devDays >= spec.metamorphDays
    val alive: Int get() = count.roundToInt()
}

/** What the renderer and the UI need of the babies (built once per chemistry tick). */
data class BroodView(
    /** Larvae drifting in the display (they hatched there and are being eaten). */
    val displayLarvae: Int = 0,
    /** Eggs floating in the display (tangs), not yet hatched. */
    val displayEggs: Int = 0,
    /** Where the display larvae hatched, and whether the keeper is luring them to a flashlight right now. */
    val displayOrigin: Vec3 = Vec3.ZERO,
    val luring: Boolean = false,
    /** Babies in the nursery tank: count, length (mm), stage 0 larva .. 1 fully metamorphosed, foods 0..1. */
    val nursery: Int = 0,
    val nurseryLengthMm: Float = 0f,
    val nurseryMeta: Float = 0f,
    /** Which model the nursery young use: 0 larva, 1 postlarva, 2 fry; and whether they are still eggs. */
    val nurseryStage: Int = 0,
    val nurseryEggs: Boolean = false,
    val copepods: Float = 0f,
    val rotifers: Float = 0f,
    val brine: Float = 0f,
    val specKey: String = "",
    /** The breeder's routine at work, for the nursery animation: bumped per feeding / siphon, and what it was. */
    val careSeq: Int = 0,
    val careKind: CareKind = CareKind.ROTIFERS,
    /** How many times faster the nursery runs than the tank (the "grow faster" button). */
    val growSpeed: Int = 1,
    /** The panels on parents' cards, one per breeding species that lives here. */
    val panels: List<BroodPanel> = emptyList(),
) {
    /** The panel for an animal of [speciesId], or null. */
    fun panelFor(speciesId: String): BroodPanel? = panels.firstOrNull { it.shows(speciesId) }
    companion object { val EMPTY = BroodView() }
}

/** What the keeper just did in the nursery (drives its animation). */
enum class CareKind { ROTIFERS, BRINE, PELLETS, SIPHON, NET, COPEPODS }

/** A short status and the actions a keeper can take, the same on Android and the web. */
data class BroodPanel(val title: String, val lines: List<String>, val actions: List<BroodAction>, val parentSpecies: Set<String>) {
    /** Whether the card of an animal of [speciesId] shows this panel. */
    fun shows(speciesId: String) = speciesId in parentSpecies
}

/** [id] is a [Brood.Act] name (with ":<spec key>" for species other than clowns), or [Brood.VIEW_NURSERY] (a camera move the host does itself). */
data class BroodAction(val id: String, val label: String, val enabled: Boolean = true, val note: String = "", val sandboxOnly: Boolean = false)

/**
 * The babies: hatching, the larvae's fate in the display, the nursery tank and the fry's growth, stepped in the
 * chemistry tick (60 s) so it runs the same in abstract mode and catch-up. Randomness is seeded from the tank seed.
 */
class Brood(private val sim: Simulation) {
    enum class Act { RAISE_ON, RAISE_OFF, ROTIFERS, BRINE, WATER_CHANGE, AUTO_ON, AUTO_OFF, RELEASE, STOP_NURSERY, SPAWN_NOW, FASTER, COPEPODS }

    private val rng = Rng(sim.config.seed * 53 + 0xB800D)
    val cohorts = ArrayList<BroodCohort>()
    private var nextId = 1

    /** Species whose young the keeper raises (clowns: lured with a flashlight at hatching; tangs: eggs skimmed at spawning). */
    private val raising = HashSet<String>()
    fun raises(spec: BroodSpec) = spec.key in raising
    /** The clowns' setting (kept for older callers and saves). */
    var raise: Boolean
        get() = raises(BroodSpecs.CLOWN)
        set(v) { if (v) raising += BroodSpecs.CLOWN.key else raising -= BroodSpecs.CLOWN.key }
    /** The breeder's routine: rotifers and green water, baby brine from day 5, food three times a day, water changes. */
    var autoCare = true
    /** Nursery foods (0..1 of a good feeding density) and how dirty its water is (0 clean .. 1 foul). */
    var rotifers = 0.0; private set
    var copepods = 0.0; private set
    var brine = 0.0; private set
    var dirt = 0.0; private set
    /** Display larvae being lured to a flashlight until this sim time (then the keeper scoops them out). */
    private var lureUntil = -1.0

    var totalHatched = 0; private set
    var totalEatenInDisplay = 0.0; private set
    var totalReleased = 0; private set
    /** A spawning the keeper asked for (sandbox): the next step lays at once. */
    var forceSpawn = false
    var forceTangSpawn = false

    var view = BroodView.EMPTY; private set

    /** Nursery time multiplier: 1, 10 or 100 (the tank keeps its own pace). */
    var growSpeed = 1; private set
    /** Nursery days on the routine's clock, for its feedings, siphons and moving juveniles out. */
    private var careClock = 0.0
    private var careSeq = 0
    private var careKind = CareKind.ROTIFERS
    private var nextFeed = 0.0
    private var nextSiphon = 0.0
    private var nextMove = 0.0
    /** Juveniles the routine moved to the tank, and extras that went to the fish store once the tank was full. */
    var autoMoved = 0; private set
    var toStore = 0; private set

    fun displayCohort(): BroodCohort? = cohorts.firstOrNull { !it.inNursery }
    fun nurseryCohort(): BroodCohort? = cohorts.firstOrNull { it.inNursery }

    fun reset() {
        cohorts.clear(); rotifers = 0.0; copepods = 0.0; brine = 0.0; dirt = 0.0; lureUntil = -1.0; forceSpawn = false; forceTangSpawn = false; raising.clear()
        totalHatched = 0; totalEatenInDisplay = 0.0; totalReleased = 0; view = BroodView.EMPTY
        growSpeed = 1; careClock = 0.0; careSeq = 0; nextFeed = 0.0; nextSiphon = 0.0; nextMove = 0.0; autoMoved = 0; toStore = 0
    }

    // ============================================================== events from Reproduction

    /** Eggs per clutch for this female: bigger females lay more, first clutches are smaller (research 28 §1). */
    fun clutchSize(spec: BroodSpec, femaleCm: Double, clutchNo: Int): Int {
        val base = spec.eggsBase + spec.eggsPerCm * (femaleCm - spec.eggsRefCm)
        val maturity = 0.65 + 0.35 * minOf(1.0, (clutchNo - 1) / 4.0)
        return (base * maturity * rng.range(0.8, 1.2)).roundToInt().coerceIn(spec.eggsMin, spec.eggsMax)
    }

    fun hatchDays(spec: BroodSpec, tempC: Double): Double =
        (spec.hatchDaysRef - spec.hatchDaysPerC * (tempC - spec.hatchRefC)).coerceIn(spec.hatchMin, spec.hatchMax) + rng.range(-0.3, 0.3)

    /** A clutch hatched (at night): the larvae enter the display, and the keeper starts luring them if raising. */
    fun hatch(spec: BroodSpec, eggs: Int, at: Vec3, parents: List<Organism>) {
        val n = (eggs * spec.hatchSuccess * rng.range(0.9, 1.05)).roundToInt().coerceAtLeast(1)
        // an older display cohort is long gone by the next hatch; fold any stragglers into this one
        cohorts.removeAll { !it.inNursery }
        val c = BroodCohort(nextId++, spec, sim.simTimeSec, n, at, parents.map { it.variant }, parents.firstOrNull()?.species?.id ?: SPECIES_FALLBACK)
        cohorts += c
        totalHatched += n
        if (raises(spec)) lureUntil = sim.simTimeSec + LURE_MIN * 60
        sim.emit(SimEvent.Hatched(spec.key, n, raises(spec), sim.simTimeSec))
    }

    /**
     * A group spawning (tangs): [eggs] scattered at [at] near the surface. They float for about a day and then hatch
     * (the cohort's hatch time is in the future until then). Raising, the keeper skims them off the top right away
     * with an egg collector into the nursery, if it is free; the rest drift in the display, where the filter and the
     * fish take nearly all of them.
     */
    fun spawnPelagic(spec: BroodSpec, eggs: Int, at: Vec3, parents: List<Organism>) {
        val now = sim.simTimeSec
        val n = (eggs * spec.hatchSuccess * rng.range(0.9, 1.05)).roundToInt().coerceAtLeast(1)
        val eggDays = hatchDays(spec, sim.water.s.tempC)
        cohorts.removeAll { !it.inNursery }
        val variants = parents.map { it.variant }
        val sp = parents.firstOrNull()?.species?.id ?: SPECIES_FALLBACK
        val c = BroodCohort(nextId++, spec, now, n, at, variants, sp)
        c.eggDays = eggDays
        totalHatched += n
        if (raises(spec) && nurseryCohort() == null) {
            val caught = minOf(n * LURE_CATCH, spec.nurseryStock.toDouble())
            val nc = BroodCohort(nextId++, spec, now, n, at, variants, sp)
            nc.count = caught; nc.inNursery = true; nc.eggDays = eggDays
            cohorts += nc
            copepods = 0.0; rotifers = 0.0; brine = 0.0; dirt = 0.0
            c.count -= caught
        }
        if (c.count >= 1) cohorts += c
        sim.emit(SimEvent.EggsLaid(spec.key, eggs, now))
    }

    // ============================================================== commands

    fun act(a: Act, spec: BroodSpec = BroodSpecs.CLOWN) {
        val nc = nurseryCohort()
        when (a) {
            Act.RAISE_ON -> {
                raising += spec.key
                if (!spec.pelagic && displayCohort()?.let { it.spec == spec && !it.eggs && it.lifeDays < 0.5 } == true) lureUntil = sim.simTimeSec + LURE_MIN * 60
            }
            Act.RAISE_OFF -> raising -= spec.key
            Act.ROTIFERS -> if (nc != null) { rotifers = minOf(1.0, rotifers + 0.8); dirt = minOf(1.0, dirt + 0.02) }
            Act.COPEPODS -> if (nc != null) { copepods = minOf(1.0, copepods + 0.8); dirt = minOf(1.0, dirt + 0.01) }
            Act.BRINE -> if (nc != null) { brine = minOf(1.0, brine + 0.8); dirt = minOf(1.0, dirt + 0.04) }
            Act.WATER_CHANGE -> dirt = maxOf(0.0, dirt - 0.7)
            Act.AUTO_ON -> autoCare = true
            Act.AUTO_OFF -> autoCare = false
            Act.RELEASE -> if (nc != null) releaseOne(nc)
            Act.FASTER -> growSpeed = when (growSpeed) { 1 -> 10; 10 -> 100; else -> 1 }
            Act.STOP_NURSERY -> { cohorts.remove(nc); rotifers = 0.0; copepods = 0.0; brine = 0.0; dirt = 0.0 }
            Act.SPAWN_NOW -> if (spec == BroodSpecs.TANG) forceTangSpawn = true else forceSpawn = true
        }
        refreshView()
    }

    /** Most of this kind the display holds (fry past it stay in the nursery). */
    private fun displayFull(spec: BroodSpec) = sim.organisms.count { it.alive && it.brainKind == spec.brainKind } >= spec.displayCap

    private fun releaseOne(c: BroodCohort) {
        if (!c.metamorphosed || c.alive < 1 || displayFull(c.spec)) return
        val sp = c.speciesId
        // an orange × black (darwin) pair: each baby takes one parent's form (research 28 §3: assumption, see doc)
        val variant = c.parentVariants.takeIf { it.isNotEmpty() }?.let { it[rng.nextInt(it.size)] }
        c.released++; totalReleased++
        val o = sim.addFromSeed(LivestockSeed(sp, "Baby ${c.released}", c.lengthMm / 10.0, variant = variant, ageYears = c.lifeDays / 365.0))
        o.arrivalTime = sim.simTimeSec; o.clock = sim.simTimeSec
        o.hunger = 0.3
        // let go at the surface by the nursery side of the tank, like a cup released by the keeper
        o.placeAt(Vec3(8.0 + rng.range(0.0, 6.0), sim.rock.waterLevelCm - 4, sim.rock.widthCm * 0.7), Vec3(1.0, 0.0, rng.signed() * 0.3))
        c.count -= 1.0
        if (c.count < 0.5) cohorts.remove(c)
        sim.careLog.babiesReleased++
        sim.emit(SimEvent.BabyReleased(o.id, sim.simTimeSec))
    }

    // ============================================================== step

    fun step(dtSec: Double) {
        val now = sim.simTimeSec
        val dtH = dtSec / 3600.0
        val dtD = dtSec / 86400.0
        val it = cohorts.iterator()
        while (it.hasNext()) {
            val c = it.next()
            if (c.inNursery) nursery(c, dtD * growSpeed) else display(c, now, dtH)
            if (c.count < 0.5) it.remove()
        }
        caughtInto?.let { cohorts += it; caughtInto = null }
        nurseryCohort()?.let { if (autoCare) routine(it, dtD * growSpeed) }
        if (nurseryCohort() == null) { rotifers = 0.0; copepods = 0.0; brine = 0.0; dirt = maxOf(0.0, dirt - dtD) }
        refreshView()
    }

    /**
     * Larvae loose in the display: night-feeding planktivores, every fish that is awake, coral polyps and the filter
     * intake take them; past the yolk they starve (no rotifer-sized food). Nearly none see the next evening, as in
     * every reef tank where clowns spawn (research 28 §2: no quantitative source; the rates are design estimates).
     */
    private fun display(c: BroodCohort, now: Double, dtH: Double) {
        val spec = c.spec
        if (c.eggs) { c.eggDays -= dtH / 24; floatingEggs(c, dtH); return }
        c.lifeDays += dtH / 24
        val age = c.lifeDays
        var h = 0.0
        if (sim.predationEnabled) for (o in sim.organisms) {
            if (!o.alive || !o.isFish) continue
            val w = spec.larvaPredators[o.brainKind] ?: 0.05
            val awake = if (o.sleeping && o.brainKind != BrainKind.CARDINAL) 0.15 else 1.0
            h += w * awake * (o.lengthCm / 5.0).coerceIn(0.3, 3.0)
        }
        h += 0.02 * sim.corals.count { it.tissue > 0.2 }
        if (sim.water.s.flowOn) h += 0.15 * sim.flowStrength.coerceIn(0.2, 2.0)
        if (age > spec.yolkDays) h += 0.03 + 0.02 * (age - spec.yolkDays) * 24 / 24
        val before = c.count
        c.count *= exp(-h * dtH)
        c.devDays += dtH / 24 * 0.5
        c.lengthMm = larvalLength(c)
        val lost = before - c.count
        totalEatenInDisplay += lost
        // the eaten ones are a bite for the fish; the rest rot into the bed (a larva is ~0.05 mg dry)
        sim.water.addDetritusTanGrams(lost * 0.000005)
        // the keeper's flashlight: they swim to it, and are scooped into the nursery when the lure ends
        if (!spec.pelagic && lureUntil > 0 && now >= lureUntil) {
            lureUntil = -1.0
            if (raise && c.count >= 1) {
                val caught = c.count * LURE_CATCH
                val n = nurseryCohort()
                if (n == null) {
                    val nc = BroodCohort(nextId++, spec, c.hatchedAt, c.hatched, c.origin, c.parentVariants, c.speciesId)
                    nc.count = caught; nc.inNursery = true; nc.devDays = c.devDays; nc.lengthMm = c.lengthMm; nc.lifeDays = c.lifeDays
                    caughtInto = nc   // joins `cohorts` after the step's loop
                    rotifers = maxOf(rotifers, if (autoCare) 0.7 else 0.0); brine = 0.0; dirt = 0.0
                } else n.count += caught   // a second batch joins the first (real breeders keep them apart; a design shortcut)
                c.count -= caught
            }
        }
    }

    /**
     * Pelagic eggs in the display (tangs): they float at the top, where the filter's intake and the skimmer pull them
     * out and planktivores pick them off (a design estimate: nearly none are left by hatching).
     */
    private fun floatingEggs(c: BroodCohort, dtH: Double) {
        var h = 0.0
        if (sim.water.s.flowOn) h += 0.12 * sim.flowStrength.coerceIn(0.2, 2.0)
        if (sim.water.skimmer) h += 0.08
        if (sim.predationEnabled) for (o in sim.organisms) {
            if (!o.alive || !o.isFish || o.sleeping) continue
            h += 0.5 * (c.spec.larvaPredators[o.brainKind] ?: 0.05) * (o.lengthCm / 5.0).coerceIn(0.3, 3.0)
        }
        val before = c.count
        c.count *= exp(-h * dtH)
        totalEatenInDisplay += before - c.count
        sim.water.addDetritusTanGrams((before - c.count) * 0.000002)
    }

    private var caughtInto: BroodCohort? = null
    val luring: Boolean get() = lureUntil > 0

    private fun larvalLength(c: BroodCohort): Double {
        val s = c.spec
        val f = (c.devDays / s.metamorphDays).coerceIn(0.0, 1.0)
        return s.hatchLengthMm + (s.metamorphLengthMm - s.hatchLengthMm) * f
    }

    /**
     * The nursery tank (a black-sided 10-gallon larval tank beside the display): food decides survival and the pace
     * of development. With the breeder's routine about half reach metamorphosis (research 28 §2, Setiawati 2008
     * 62-81 % with Artemia; Olivotto 2006 analogy: unfed or unenriched none past day 7).
     */
    private fun nursery(c: BroodCohort, dtD: Double) {
        val s = c.spec
        if (c.eggs) {
            // still eggs, floating in the nursery's gentle upwelling: a few don't hatch
            c.eggDays -= dtD
            c.count *= exp(-0.05 * dtD)
            dirt = if (autoCare) maxOf(0.0, dirt - dtD) else minOf(1.0, dirt + dtD * 0.05)
            return
        }
        c.lifeDays += dtD
        val age = c.lifeDays
        if (autoCare) {
            // first foods kept up while larvae (copepod nauplii for tangs, rotifers for clowns), baby brine from its
            // day, three feeds a day, daily water changes
            val k = s.routineFood
            if (s.copepodsUntil > 0 && c.devDays < s.copepodsUntil) copepods = maxOf(copepods, k)
            if (c.devDays >= s.rotifersFrom - 1 && (!c.metamorphosed || c.devDays < s.metamorphDays + 5)) rotifers = maxOf(rotifers, k)
            if (c.devDays >= s.brineFromDay - 1) brine = maxOf(brine, k)
            dirt = maxOf(0.0, dirt - dtD * 1.5)
        }
        // food is eaten and dies off; dirt builds from waste and leftovers
        val load = c.count * s.loadPerLarva
        val eat = 1.0 + load / 300.0
        // what they had over the step: the mean of the food before and after it was eaten down (a long catch-up
        // step at grow speed 100x would otherwise see only the empty tank at its end)
        val r0 = rotifers; val c0 = copepods; val b0 = brine
        rotifers = maxOf(0.0, rotifers - dtD * 1.2 * eat)
        copepods = maxOf(0.0, copepods - dtD * 1.0 * eat)
        brine = maxOf(0.0, brine - dtD * 2.0 * eat)
        dirt = minOf(1.0, dirt + dtD * (0.12 + load / 2000.0 + 0.1 * (rotifers + brine) + 0.03 * copepods))
        val rot = (r0 + rotifers) / 2; val cop = (c0 + copepods) / 2; val bri = (b0 + brine) / 2
        // what they can eat at this stage: the smallest live food first (tang mouths are too small for rotifers at first)
        val small = if (s.copepodsUntil <= 0) rot else if (c.devDays < s.rotifersFrom) cop else maxOf(cop, 0.8 * rot)
        val food = when {
            age < s.yolkDays -> 1.0
            c.devDays < s.brineFromDay -> small
            !c.metamorphosed -> maxOf(small * 0.6, bri) * 0.5 + minOf(small, bri) * 0.5 + 0.25 * maxOf(small, bri)
            else -> bri
        }.coerceIn(0.0, 1.0)
        c.fed += (food - c.fed) * minOf(1.0, dtD / 0.4)
        // development and growth
        val pace = 0.3 + 0.7 * c.fed
        val wasLarva = !c.metamorphosed
        c.devDays += dtD * pace
        if (!c.metamorphosed) c.lengthMm = larvalLength(c)
        else c.lengthMm += s.growthKPerDay * (s.linfMm - c.lengthMm) * dtD * c.fed * (1.0 - 0.6 * dirt)
        if (wasLarva && c.metamorphosed) sim.emit(SimEvent.Metamorphosed(s.key, c.alive, sim.simTimeSec))
        // daily hazard
        var h = if (!c.metamorphosed) s.larvaHazard else if (c.devDays < s.metamorphDays + 20) 0.01 else 0.002
        if (age > s.yolkDays) h += (if (!c.metamorphosed) 1.4 else 0.3) * (1 - c.fed) * (1 - c.fed)
        for (cr in s.crashes) if (c.devDays in cr.fromDay..cr.toDay) h += cr.perDay   // first feeding, flexion, metamorphosis
        h += 0.5 * maxOf(0.0, dirt - 0.5)
        val t = sim.water.s.tempC
        h += s.tempHazard * (maxOf(0.0, 24.0 - t) + maxOf(0.0, t - 29.5))
        if (c.metamorphosed && c.count > s.fryCap) h += 0.05 * (c.count / s.fryCap - 1)   // nipping in a crowd (Roux 2021)
        c.count *= exp(-h * dtD)
    }

    /**
     * The breeder's routine on its own clock (research 29): feedings through the day (rotifers, then baby brine, then
     * crushed pellets once weaned), a bottom siphon and water change daily, and from juvenile size one baby a half-day
     * moved to the display (acclimated in a cup) until the tank holds as many clowns as it can; extras go to the fish store.
     */
    private fun routine(c: BroodCohort, dtD: Double) {
        val s = c.spec
        careClock += dtD
        if (careClock >= nextFeed && !c.eggs) {
            nextFeed = careClock + FEED_EVERY_D
            care(when {
                !c.metamorphosed && c.devDays < s.brineFromDay -> if (s.copepodsUntil > 0 && c.devDays < s.rotifersFrom) CareKind.COPEPODS else CareKind.ROTIFERS
                c.devDays < s.metamorphDays + s.weanDays -> CareKind.BRINE
                else -> CareKind.PELLETS
            })
        }
        if (careClock >= nextSiphon) { nextSiphon = careClock + 1.0; if (careClock > 0.5) care(CareKind.SIPHON) }
        if (c.metamorphosed && c.lengthMm >= s.releaseMm && careClock >= nextMove) {
            nextMove = careClock + MOVE_EVERY_D
            if (!displayFull(s)) { care(CareKind.NET); releaseOne(c); autoMoved++ }
            else { toStore += c.alive; cohorts.remove(c) }
        }
    }

    private fun care(k: CareKind) { careKind = k; careSeq++ }

    // ============================================================== view

    private fun refreshView() {
        val d = displayCohort()
        val n = nurseryCohort()
        val now = sim.simTimeSec
        val dEggs = d != null && d.eggs
        view = BroodView(
            displayLarvae = if (dEggs) 0 else d?.alive ?: 0, displayEggs = if (dEggs) d!!.alive else 0,
            displayOrigin = d?.origin ?: Vec3.ZERO, luring = luring,
            nursery = n?.alive ?: 0, nurseryLengthMm = (n?.lengthMm ?: 0.0).toFloat(),
            nurseryMeta = n?.let { metaProgress(it).toFloat() } ?: 0f,
            nurseryStage = n?.let { stageOf(it) } ?: 0, nurseryEggs = n?.eggs == true,
            rotifers = rotifers.toFloat(), brine = brine.toFloat(), copepods = copepods.toFloat(),
            specKey = (n ?: d)?.spec?.key ?: BroodSpecs.CLOWN.key,
            careSeq = careSeq, careKind = careKind, growSpeed = growSpeed,
            panels = BroodSpecs.ALL.mapNotNull { panel(it) },
        )
    }

    /** 0 → 1 through the visible change into a little fish. */
    private fun metaProgress(c: BroodCohort) = ((c.devDays - (c.spec.metamorphDays - c.spec.metaLead)) / c.spec.metaSpan).coerceIn(0.0, 1.0)

    /** The model the young use now: 0 larva, 1 postlarva (clown: first bar; tang: the spiny silver acronurus), 2 fry. */
    private fun stageOf(c: BroodCohort): Int {
        val s = c.spec
        val meta = metaProgress(c)
        val from = s.postlarvaFromDay
        return when {
            from != null && c.devDays < from -> 0
            from == null && meta < 0.5 -> 0
            meta < 0.5 || c.lengthMm < s.postlarvaMaxMm -> 1
            else -> 2
        }
    }

    /** The action id for [a] on [spec]'s card: clowns keep the bare names (older hosts and tests). */
    private fun id(a: Act, spec: BroodSpec) = if (spec == BroodSpecs.CLOWN) a.name else "${a.name}:${spec.key}"

    /** The parents' lines: eggs, courtship, what they are waiting for. */
    private fun parentLines(spec: BroodSpec, now: Double, lines: MutableList<String>) {
        val r = sim.repro
        if (spec == BroodSpecs.TANG) { r.tangLines(lines); return }
        val clutch = r.clownClutch()
        when {
            clutch != null -> {
                val day = clutch.ageDays(now)
                val look = when (day / clutch.hatchDays) { in 0.0..0.3 -> "bright orange"; in 0.3..0.65 -> "turning dark"; else -> "silver eyes showing" }
                lines += "About ${clutch.eggs} eggs on the rock, day ${day.toInt() + 1} of about ${clutch.hatchDays.roundToInt()} ($look)."
                lines += "Dad fans them with his fins and picks out bad ones. They hatch about an hour after lights out."
            }
            r.clownPhase == Reproduction.Phase.CLEANING -> lines += "Cleaning a spot on the rock to lay eggs."
            r.clownPhase == Reproduction.Phase.LAYING -> lines += "Laying eggs right now!"
            else -> lines += r.clownWaitLine()
        }
    }

    private fun canSpawnNow(spec: BroodSpec): Boolean {
        val r = sim.repro
        return if (spec == BroodSpecs.TANG) r.tangPhase == Reproduction.TangPhase.IDLE && r.hasTangPair()
        else r.clownClutch() == null && r.clownPhase == Reproduction.Phase.IDLE && r.hasClownPair()
    }

    /** Card text and buttons for a parent of [spec] (null when no such fish lives here). */
    private fun panel(spec: BroodSpec): BroodPanel? {
        if (sim.organisms.none { it.alive && it.brainKind == spec.brainKind }) return null
        val now = sim.simTimeSec
        val w = spec.words
        val lines = ArrayList<String>()
        val acts = ArrayList<BroodAction>()
        parentLines(spec, now, lines)
        val d = displayCohort()?.takeIf { it.spec == spec }
        if (d != null && d.alive > 0) lines += when {
            d.eggs -> "${d.alive} eggs floating near the top. They hatch in about ${hoursText(d.eggDays * 24)}; the filter and hungry fish get nearly all of them."
            luring -> "${d.alive} tiny larvae are swimming to your flashlight."
            else -> "${d.alive} larvae drifting in the tank. Hungry fish and the filter will get nearly all of them."
        }
        val anyNursery = nurseryCohort()
        val n = anyNursery?.takeIf { it.spec == spec }
        if (anyNursery != null && n == null) lines += "The nursery tank is busy with the ${anyNursery.spec.babyName}."
        if (n != null) {
            val age = n.lifeDays
            if (n.eggs) lines += "Nursery: ${n.alive} floating eggs, hatching in about ${hoursText(n.eggDays * 24 / growSpeed)}."
            else {
                val stage = when {
                    stageOf(n) == 0 -> w.larva
                    !n.metamorphosed -> w.postlarva
                    n.lengthMm < spec.releaseMm -> w.fry
                    else -> w.juvenile
                }
                lines += "Nursery: ${n.alive} $stage, day ${age.toInt() + 1}, ${"%.1f".fmt(n.lengthMm)} mm."
            }
            if (autoCare) lines += when {
                !n.metamorphosed && n.devDays < (if (w.careSecond.isEmpty()) spec.brineFromDay else spec.rotifersFrom) -> w.careFirst
                !n.metamorphosed && n.devDays < spec.brineFromDay -> w.careSecond
                !n.metamorphosed -> w.careChanging
                n.lengthMm < spec.releaseMm -> "Eating baby brine and crushed pellets. They move to your tank at ${spec.releaseMm.roundToInt()} mm (about ${(spec.releaseMm / 25.4 * 10).roundToInt() / 10.0} inch)."
                else -> "Big enough! One moves to your tank every half day."
            }
            if (!autoCare) {
                val needs = buildList {
                    if (spec.copepodsUntil > 0 && n.devDays < spec.copepodsUntil && copepods < 0.3) add("baby copepods")
                    if (n.devDays >= spec.rotifersFrom - 1 && !n.metamorphosed && rotifers < 0.3) add("rotifers")
                    if (n.devDays >= spec.brineFromDay - 1 && brine < 0.3) add("baby brine")
                    if (dirt > 0.5) add("a water change")
                }
                if (needs.isNotEmpty() && !n.eggs) lines += "They need ${needs.joinToString(" and ")}."
            }
        }
        if (n == null && anyNursery == null && autoMoved > 0) lines += "$autoMoved babies grew up in the nursery and moved to your tank." + (if (toStore > 0) " $toStore extras went to the fish store." else "")
        if (n == null) acts += if (raises(spec)) BroodAction(id(Act.RAISE_OFF, spec), "Raising the babies: on", note = w.raisingNote)
        else BroodAction(id(Act.RAISE_ON, spec), "Raise the babies", note = w.raiseNote)
        if (n != null) {
            acts += BroodAction(Brood.VIEW_NURSERY, "Look at the nursery")
            acts += BroodAction(id(Act.FASTER, spec), "Grow speed: ${growSpeed}x", note = "Only the nursery runs faster; the tank keeps its pace.")
            acts += if (autoCare) BroodAction(id(Act.AUTO_OFF, spec), "Breeder routine: on", note = "Live foods and water changes are done for you.")
            else BroodAction(id(Act.AUTO_ON, spec), "Breeder routine: off")
            if (!autoCare) {
                if (spec.copepodsUntil > 0) acts += BroodAction(id(Act.COPEPODS, spec), "Feed baby copepods", n.devDays < spec.copepodsUntil)
                acts += BroodAction(id(Act.ROTIFERS, spec), "Feed rotifers", n.devDays >= spec.rotifersFrom - 1 && (!n.metamorphosed || n.devDays < spec.metamorphDays + 5))
                acts += BroodAction(id(Act.BRINE, spec), if (n.devDays > spec.metamorphDays + 20) "Feed baby food" else "Feed baby brine", n.devDays >= spec.brineFromDay - 1)
                acts += BroodAction(id(Act.WATER_CHANGE, spec), "Change nursery water", dirt > 0.05)
            }
            val full = displayFull(spec)
            acts += BroodAction(
                id(Act.RELEASE, spec), "Move a baby to the tank", n.metamorphosed && !full,
                when { !n.metamorphosed -> "Wait until they turn into little fish."; full -> "The tank has enough ${w.adults}."; n.lengthMm < spec.releaseMm -> w.smallNote; else -> "" },
            )
            acts += BroodAction(id(Act.STOP_NURSERY, spec), "Give them to the fish store")
        }
        acts += BroodAction(id(Act.SPAWN_NOW, spec), "Spawn now", canSpawnNow(spec), sandboxOnly = true)
        val species = sim.organisms.filter { it.alive && it.brainKind == spec.brainKind }.mapTo(HashSet()) { it.species.id }
        return BroodPanel("Family", lines, acts, species)
    }

    private fun hoursText(h: Double): String = if (h < 1.5) "an hour" else "${h.roundToInt()} hours"

    private fun String.fmt(v: Double): String { val t = (v * 10).roundToInt(); return "${t / 10}.${t % 10}" }

    // ============================================================== save

    fun save(): SavedBroodState = SavedBroodState(
        clutches = sim.repro.clutches.map { SavedClutch(it.kind, it.pos.x, it.pos.y, it.pos.z, it.normal.x, it.normal.y, it.normal.z, it.laidAt, it.hatchDays, it.eggs) },
        raise = raise, autoCare = autoCare, rotifers = rotifers, brine = brine, dirt = dirt, growSpeed = growSpeed,
        cohorts = cohorts.filter { it.inNursery }.map {
            SavedCohort(it.spec.key, it.hatchedAt, it.hatched, it.count, it.devDays, it.lengthMm, it.fed, it.released, it.parentVariants.map { v -> v ?: "" }, it.speciesId, it.eggDays, it.lifeDays)
        },
        raising = raising.sorted(), copepods = copepods,
    )

    fun load(s: SavedBroodState) {
        for (c in s.clutches) sim.repro.restoreClutch(c)
        raising.clear(); raising += s.raising.filter { BroodSpecs.byKey(it) != null }
        raise = s.raise || BroodSpecs.CLOWN.key in raising
        autoCare = s.autoCare; rotifers = s.rotifers; brine = s.brine; dirt = s.dirt; copepods = s.copepods
        growSpeed = s.growSpeed.takeIf { it in setOf(1, 10, 100) } ?: 1
        cohorts.removeAll { it.inNursery }
        for (c in s.cohorts) {
            val spec = BroodSpecs.byKey(c.spec) ?: continue
            val bc = BroodCohort(nextId++, spec, c.hatchedAt, c.hatched, Vec3.ZERO, c.parentVariants.map { it.ifBlank { null } }, c.speciesId)
            bc.inNursery = true; bc.count = c.count; bc.devDays = c.devDays; bc.lengthMm = c.lengthMm; bc.fed = c.fed; bc.released = c.released
            bc.eggDays = c.eggDays; bc.lifeDays = if (c.lifeDays >= 0) c.lifeDays else bc.ageDays(sim.simTimeSec).coerceAtLeast(0.0)
            cohorts += bc
        }
        refreshView()
    }

    companion object {
        /** UI action that only moves the camera to the nursery tank (hosts handle it). */
        const val VIEW_NURSERY = "VIEW_NURSERY"
        /** Minutes the keeper lures the larvae to a flashlight before scooping them out, and the share caught. */
        const val LURE_MIN = 40.0
        const val LURE_CATCH = 0.8
        /** The routine feeds every 4 nursery hours and moves a juvenile out every half day. */
        const val FEED_EVERY_D = 4.0 / 24
        const val MOVE_EVERY_D = 0.5
        const val SPECIES_FALLBACK = "amphiprion_ocellaris"
    }
}

/** The nursery as saved in My tank (display larvae are gone within a night, so they are not saved). */
@kotlinx.serialization.Serializable
data class SavedBroodState(
    /** Eggs on the rock (the male's tending resumes on reopen). */
    val clutches: List<SavedClutch> = emptyList(),
    val raise: Boolean = false,
    val autoCare: Boolean = true,
    val growSpeed: Int = 1,
    val rotifers: Double = 0.0,
    val brine: Double = 0.0,
    val dirt: Double = 0.0,
    val cohorts: List<SavedCohort> = emptyList(),
    /** Species whose young are raised ([BroodSpec.key]); older saves have only [raise] (clowns). */
    val raising: List<String> = emptyList(),
    val copepods: Double = 0.0,
)

@kotlinx.serialization.Serializable
data class SavedClutch(
    val kind: String,
    val x: Double, val y: Double, val z: Double,
    val nx: Double = 0.0, val ny: Double = 1.0, val nz: Double = 0.0,
    val laidAt: Double,
    val hatchDays: Double,
    val eggs: Int = 300,
)

@kotlinx.serialization.Serializable
data class SavedCohort(
    val spec: String,
    val hatchedAt: Double,
    val hatched: Int,
    val count: Double,
    val devDays: Double,
    val lengthMm: Double,
    val fed: Double = 1.0,
    val released: Int = 0,
    val parentVariants: List<String> = emptyList(),
    val speciesId: String = Brood.SPECIES_FALLBACK,
    val eggDays: Double = 0.0,
    /** Days since hatching on the nursery clock; older saves (-1) count it from [hatchedAt]. */
    val lifeDays: Double = -1.0,
)
