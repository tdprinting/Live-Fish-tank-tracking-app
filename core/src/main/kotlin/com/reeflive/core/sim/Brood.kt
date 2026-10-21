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

    val ALL = listOf(CLOWN)
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
    val metamorphosed: Boolean get() = devDays >= spec.metamorphDays
    val alive: Int get() = count.roundToInt()
}

/** What the renderer and the UI need of the babies (built once per chemistry tick). */
data class BroodView(
    /** Larvae drifting in the display (they hatched there and are being eaten). */
    val displayLarvae: Int = 0,
    /** Where the display larvae hatched, and whether the keeper is luring them to a flashlight right now. */
    val displayOrigin: Vec3 = Vec3.ZERO,
    val luring: Boolean = false,
    /** Babies in the nursery tank: count, length (mm), stage 0 larva .. 1 fully metamorphosed, foods 0..1. */
    val nursery: Int = 0,
    val nurseryLengthMm: Float = 0f,
    val nurseryMeta: Float = 0f,
    val rotifers: Float = 0f,
    val brine: Float = 0f,
    val specKey: String = "",
    /** The breeder's routine at work, for the nursery animation: bumped per feeding / siphon, and what it was. */
    val careSeq: Int = 0,
    val careKind: CareKind = CareKind.ROTIFERS,
    /** How many times faster the nursery runs than the tank (the "grow faster" button). */
    val growSpeed: Int = 1,
    /** The panel on a parent's card; null when the species does not breed here. */
    val panel: BroodPanel? = null,
) {
    companion object { val EMPTY = BroodView() }
}

/** What the keeper just did in the nursery (drives its animation). */
enum class CareKind { ROTIFERS, BRINE, PELLETS, SIPHON, NET }

/** A short status and the actions a keeper can take, the same on Android and the web. */
data class BroodPanel(val title: String, val lines: List<String>, val actions: List<BroodAction>, val parentSpecies: Set<String>) {
    /** Whether the card of an animal of [speciesId] shows this panel. */
    fun shows(speciesId: String) = speciesId in parentSpecies
}

/** [id] is a [Brood.Act] name, or [Brood.VIEW_NURSERY] (a camera move the host does itself). */
data class BroodAction(val id: String, val label: String, val enabled: Boolean = true, val note: String = "", val sandboxOnly: Boolean = false)

/**
 * The babies: hatching, the larvae's fate in the display, the nursery tank and the fry's growth, stepped in the
 * chemistry tick (60 s) so it runs the same in abstract mode and catch-up. Randomness is seeded from the tank seed.
 */
class Brood(private val sim: Simulation) {
    enum class Act { RAISE_ON, RAISE_OFF, ROTIFERS, BRINE, WATER_CHANGE, AUTO_ON, AUTO_OFF, RELEASE, STOP_NURSERY, SPAWN_NOW, FASTER }

    private val rng = Rng(sim.config.seed * 53 + 0xB800D)
    val cohorts = ArrayList<BroodCohort>()
    private var nextId = 1

    /** The keeper catches the larvae at hatching with a flashlight and moves them to the nursery tank. */
    var raise = false
    /** The breeder's routine: rotifers and green water, baby brine from day 5, food three times a day, water changes. */
    var autoCare = true
    /** Nursery foods (0..1 of a good feeding density) and how dirty its water is (0 clean .. 1 foul). */
    var rotifers = 0.0; private set
    var brine = 0.0; private set
    var dirt = 0.0; private set
    /** Display larvae being lured to a flashlight until this sim time (then the keeper scoops them out). */
    private var lureUntil = -1.0

    var totalHatched = 0; private set
    var totalEatenInDisplay = 0.0; private set
    var totalReleased = 0; private set
    /** A spawning the keeper asked for (sandbox): the next step lays at once. */
    var forceSpawn = false

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
        cohorts.clear(); rotifers = 0.0; brine = 0.0; dirt = 0.0; lureUntil = -1.0; forceSpawn = false
        totalHatched = 0; totalEatenInDisplay = 0.0; totalReleased = 0; view = BroodView.EMPTY
        growSpeed = 1; careClock = 0.0; careSeq = 0; nextFeed = 0.0; nextSiphon = 0.0; nextMove = 0.0; autoMoved = 0; toStore = 0
    }

    // ============================================================== events from Reproduction

    /** Eggs per clutch for this female: bigger females lay more, first clutches are smaller (research 28 §1). */
    fun clutchSize(spec: BroodSpec, femaleCm: Double, clutchNo: Int): Int {
        val base = spec.eggsBase + spec.eggsPerCm * (femaleCm - spec.eggsRefCm)
        val maturity = 0.65 + 0.35 * minOf(1.0, (clutchNo - 1) / 4.0)
        return (base * maturity * rng.range(0.8, 1.2)).roundToInt().coerceIn(80, 1000)
    }

    fun hatchDays(spec: BroodSpec, tempC: Double): Double =
        (spec.hatchDaysRef - spec.hatchDaysPerC * (tempC - spec.hatchRefC)).coerceIn(spec.hatchMin, spec.hatchMax) + rng.range(-0.3, 0.3)

    /** A clutch hatched (at night): the larvae enter the display, and the keeper starts luring them if raising. */
    fun hatch(spec: BroodSpec, eggs: Int, at: Vec3, parents: List<Organism>) {
        val n = (eggs * spec.hatchSuccess * rng.range(0.9, 1.05)).roundToInt().coerceAtLeast(1)
        // an older display cohort is long gone by the next hatch; fold any stragglers into this one
        cohorts.removeAll { !it.inNursery }
        val c = BroodCohort(nextId++, spec, sim.lifeSec, n, at, parents.map { it.variant }, parents.firstOrNull()?.species?.id ?: SPECIES_FALLBACK)
        cohorts += c
        totalHatched += n
        if (raise) lureUntil = sim.simTimeSec + LURE_MIN * 60
        sim.emit(SimEvent.Hatched(spec.key, n, raise, sim.simTimeSec))
    }

    // ============================================================== commands

    fun act(a: Act) {
        val nc = nurseryCohort()
        when (a) {
            Act.RAISE_ON -> { raise = true; if (displayCohort()?.let { it.ageDays(sim.lifeSec) < 0.5 } == true) lureUntil = sim.simTimeSec + LURE_MIN * 60 }
            Act.RAISE_OFF -> raise = false
            Act.ROTIFERS -> if (nc != null) { rotifers = minOf(1.0, rotifers + 0.8); dirt = minOf(1.0, dirt + 0.02) }
            Act.BRINE -> if (nc != null) { brine = minOf(1.0, brine + 0.8); dirt = minOf(1.0, dirt + 0.04) }
            Act.WATER_CHANGE -> dirt = maxOf(0.0, dirt - 0.7)
            Act.AUTO_ON -> autoCare = true
            Act.AUTO_OFF -> autoCare = false
            Act.RELEASE -> if (nc != null) releaseOne(nc)
            Act.FASTER -> growSpeed = when (growSpeed) { 1 -> 10; 10 -> 100; else -> 1 }
            Act.STOP_NURSERY -> { cohorts.remove(nc); rotifers = 0.0; brine = 0.0; dirt = 0.0 }
            Act.SPAWN_NOW -> forceSpawn = true
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
        val o = sim.addFromSeed(LivestockSeed(sp, "Baby ${c.released}", c.lengthMm / 10.0, variant = variant, ageYears = c.ageDays(sim.lifeSec) / 365.0))
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

    /**
     * [dtSec] is on the game clock ([Simulation.lifePace]): the nursery raises fry at that pace. Larvae loose in the
     * display live and die within a night on the sim clock (they are eaten as the keeper watches), so they step at
     * real time. Cohort ages are on [Simulation.lifeSec].
     */
    fun step(dtSec: Double) {
        val now = sim.simTimeSec
        val dtH = dtSec / sim.lifePace / 3600.0
        val dtD = dtSec / 86400.0
        val it = cohorts.iterator()
        while (it.hasNext()) {
            val c = it.next()
            if (c.inNursery) nursery(c, dtD * growSpeed) else display(c, now, dtH)
            if (c.count < 0.5) it.remove()
        }
        caughtInto?.let { cohorts += it; caughtInto = null }
        nurseryCohort()?.let { if (autoCare) routine(it, dtD * growSpeed) }
        if (nurseryCohort() == null) { rotifers = 0.0; brine = 0.0; dirt = maxOf(0.0, dirt - dtD) }
        refreshView()
    }

    /**
     * Larvae loose in the display: night-feeding planktivores, every fish that is awake, coral polyps and the filter
     * intake take them; past the yolk they starve (no rotifer-sized food). Nearly none see the next evening, as in
     * every reef tank where clowns spawn (research 28 §2: no quantitative source; the rates are design estimates).
     */
    private fun display(c: BroodCohort, now: Double, dtH: Double) {
        val spec = c.spec
        val age = c.ageDays(sim.lifeSec)
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
        if (lureUntil > 0 && now >= lureUntil) {
            lureUntil = -1.0
            if (raise && c.count >= 1) {
                val caught = c.count * LURE_CATCH
                val n = nurseryCohort()
                if (n == null) {
                    val nc = BroodCohort(nextId++, spec, c.hatchedAt, c.hatched, c.origin, c.parentVariants, c.speciesId)
                    nc.count = caught; nc.inNursery = true; nc.devDays = c.devDays; nc.lengthMm = c.lengthMm
                    caughtInto = nc   // joins `cohorts` after the step's loop
                    rotifers = maxOf(rotifers, if (autoCare) 0.7 else 0.0); brine = 0.0; dirt = 0.0
                } else n.count += caught   // a second batch joins the first (real breeders keep them apart; a design shortcut)
                c.count -= caught
            }
        }
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
        val age = c.ageDays(sim.lifeSec)
        if (autoCare) {
            // rotifers kept up while larvae, baby brine from day 5, three feeds a day, daily water changes
            if (!c.metamorphosed || c.devDays < s.metamorphDays + 5) rotifers = maxOf(rotifers, 0.75)
            if (c.devDays >= s.brineFromDay - 1) brine = maxOf(brine, 0.75)
            dirt = maxOf(0.0, dirt - dtD * 1.5)
        }
        // food is eaten and dies off; dirt builds from waste and leftovers
        val eat = 1.0 + c.count / 300.0
        rotifers = maxOf(0.0, rotifers - dtD * 1.2 * eat)
        brine = maxOf(0.0, brine - dtD * 2.0 * eat)
        dirt = minOf(1.0, dirt + dtD * (0.12 + c.count / 2000.0 + 0.1 * (rotifers + brine)))
        // what they can eat at this stage
        val food = when {
            age < s.yolkDays -> 1.0
            c.devDays < s.brineFromDay -> rotifers
            !c.metamorphosed -> maxOf(rotifers * 0.6, brine) * 0.5 + minOf(rotifers, brine) * 0.5 + 0.25 * maxOf(rotifers, brine)
            else -> brine
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
        var h = if (!c.metamorphosed) 0.04 else if (c.devDays < s.metamorphDays + 20) 0.01 else 0.002
        if (age > s.yolkDays) h += (if (!c.metamorphosed) 1.4 else 0.3) * (1 - c.fed) * (1 - c.fed)
        if (c.devDays in (s.metamorphDays - 2)..(s.metamorphDays + 1.5)) h += 0.06   // the metamorphosis crash
        h += 0.5 * maxOf(0.0, dirt - 0.5)
        val t = sim.water.s.tempC
        h += 0.3 * (maxOf(0.0, 24.0 - t) + maxOf(0.0, t - 29.5))
        if (c.metamorphosed && c.count > NURSERY_FRY_CAP) h += 0.05 * (c.count / NURSERY_FRY_CAP - 1)   // nipping in a crowd (Roux 2021)
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
        if (careClock >= nextFeed) {
            nextFeed = careClock + FEED_EVERY_D
            care(when { !c.metamorphosed && c.devDays < s.brineFromDay -> CareKind.ROTIFERS; c.devDays < s.metamorphDays + s.weanDays -> CareKind.BRINE; else -> CareKind.PELLETS })
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
        view = BroodView(
            displayLarvae = d?.alive ?: 0, displayOrigin = d?.origin ?: Vec3.ZERO, luring = luring,
            nursery = n?.alive ?: 0, nurseryLengthMm = (n?.lengthMm ?: 0.0).toFloat(),
            nurseryMeta = n?.let { ((it.devDays - (it.spec.metamorphDays - 2)) / 3.0).coerceIn(0.0, 1.0).toFloat() } ?: 0f,
            rotifers = rotifers.toFloat(), brine = brine.toFloat(),
            specKey = (n ?: d)?.spec?.key ?: BroodSpecs.CLOWN.key,
            careSeq = careSeq, careKind = careKind, growSpeed = growSpeed,
            panel = panel(BroodSpecs.CLOWN),
        )
    }

    /** Card text and buttons for a parent of [spec] (null when no such fish lives here). */
    private fun panel(spec: BroodSpec): BroodPanel? {
        if (sim.organisms.none { it.alive && it.brainKind == spec.brainKind }) return null
        val now = sim.lifeSec
        val lines = ArrayList<String>()
        val acts = ArrayList<BroodAction>()
        val r = sim.repro
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
        val d = displayCohort()
        if (d != null && d.alive > 0) lines += if (luring) "${d.alive} tiny larvae are swimming to your flashlight." else "${d.alive} larvae drifting in the tank. Hungry fish and the filter will get nearly all of them."
        val n = nurseryCohort()
        if (n != null) {
            val day = n.ageDays(now).toInt() + 1
            val stage = when {
                !n.metamorphosed && n.devDays < spec.metamorphDays - 2 -> "larvae (see-through, eating rotifers)"
                !n.metamorphosed -> "changing into little fish (first white stripe coming)"
                n.lengthMm < spec.releaseMm -> "fry with white stripes"
                else -> "juveniles, big enough for the tank"
            }
            lines += "Nursery: ${n.alive} $stage, day $day, ${"%.1f".fmt(n.lengthMm)} mm."
            if (autoCare) lines += when {
                !n.metamorphosed && n.devDays < spec.brineFromDay -> "The routine feeds rotifers in green water twice a day, siphons the bottom and keeps a 14 hour day."
                !n.metamorphosed -> "The routine adds baby brine shrimp now. Changing into little fish is the riskiest time."
                n.lengthMm < spec.releaseMm -> "Eating baby brine and crushed pellets. They move to your tank at ${spec.releaseMm.roundToInt()} mm (about ${(spec.releaseMm / 25.4 * 10).roundToInt() / 10.0} inch)."
                else -> "Big enough! One moves to your tank every half day."
            }
            if (!autoCare) {
                val needs = buildList {
                    if (!n.metamorphosed && rotifers < 0.3) add("rotifers")
                    if (n.devDays >= spec.brineFromDay - 1 && brine < 0.3) add("baby brine")
                    if (dirt > 0.5) add("a water change")
                }
                if (needs.isNotEmpty()) lines += "They need ${needs.joinToString(" and ")}."
            }
        }
        if (n == null && autoMoved > 0) lines += "$autoMoved babies grew up in the nursery and moved to your tank." + (if (toStore > 0) " $toStore extras went to the fish store." else "")
        if (n == null) acts += if (raise) BroodAction(Act.RAISE_OFF.name, "Raising the babies: on", note = "At hatching you lure the larvae with a flashlight and move them to a nursery tank.")
        else BroodAction(Act.RAISE_ON.name, "Raise the babies", note = "Catch the larvae at hatching and grow them in a nursery tank.")
        if (n != null) {
            acts += BroodAction(Brood.VIEW_NURSERY, "Look at the nursery")
            acts += BroodAction(Act.FASTER.name, "Grow speed: ${growSpeed}x", note = "Only the nursery runs faster; the tank keeps its pace.")
            acts += if (autoCare) BroodAction(Act.AUTO_OFF.name, "Breeder routine: on", note = "Rotifers, baby brine and water changes are done for you.")
            else BroodAction(Act.AUTO_ON.name, "Breeder routine: off")
            if (!autoCare) {
                acts += BroodAction(Act.ROTIFERS.name, "Feed rotifers", !n.metamorphosed || n.devDays < spec.metamorphDays + 5)
                acts += BroodAction(Act.BRINE.name, if (n.devDays > spec.metamorphDays + 20) "Feed baby food" else "Feed baby brine", n.devDays >= spec.brineFromDay - 1)
                acts += BroodAction(Act.WATER_CHANGE.name, "Change nursery water", dirt > 0.05)
            }
            val full = displayFull(spec)
            acts += BroodAction(
                Act.RELEASE.name, "Move a baby to the tank", n.metamorphosed && !full,
                when { !n.metamorphosed -> "Wait until they turn into little fish."; full -> "The tank has enough clowns."; n.lengthMm < spec.releaseMm -> "Still small: the hawkfish or dottyback may eat it."; else -> "" },
            )
            acts += BroodAction(Act.STOP_NURSERY.name, "Give them to the fish store")
        }
        acts += BroodAction(Act.SPAWN_NOW.name, "Spawn now", clutch == null && r.clownPhase == Reproduction.Phase.IDLE && r.hasClownPair(), sandboxOnly = true)
        val species = sim.organisms.filter { it.alive && it.brainKind == spec.brainKind }.mapTo(HashSet()) { it.species.id }
        return BroodPanel("Family", lines, acts, species)
    }

    private fun String.fmt(v: Double): String { val t = (v * 10).roundToInt(); return "${t / 10}.${t % 10}" }

    // ============================================================== save

    fun save(): SavedBroodState = SavedBroodState(
        clutches = sim.repro.clutches.map { SavedClutch(it.kind, it.pos.x, it.pos.y, it.pos.z, it.normal.x, it.normal.y, it.normal.z, it.laidAt, it.hatchDays, it.eggs) },
        raise = raise, autoCare = autoCare, rotifers = rotifers, brine = brine, dirt = dirt, growSpeed = growSpeed,
        cohorts = cohorts.filter { it.inNursery }.map {
            SavedCohort(it.spec.key, it.hatchedAt, it.hatched, it.count, it.devDays, it.lengthMm, it.fed, it.released, it.parentVariants.map { v -> v ?: "" }, it.speciesId)
        },
    )

    fun load(s: SavedBroodState) {
        for (c in s.clutches) sim.repro.restoreClutch(c)
        raise = s.raise; autoCare = s.autoCare; rotifers = s.rotifers; brine = s.brine; dirt = s.dirt
        growSpeed = s.growSpeed.takeIf { it in setOf(1, 10, 100) } ?: 1
        cohorts.removeAll { it.inNursery }
        for (c in s.cohorts) {
            val spec = BroodSpecs.byKey(c.spec) ?: continue
            val bc = BroodCohort(nextId++, spec, c.hatchedAt, c.hatched, Vec3.ZERO, c.parentVariants.map { it.ifBlank { null } }, c.speciesId)
            bc.inNursery = true; bc.count = c.count; bc.devDays = c.devDays; bc.lengthMm = c.lengthMm; bc.fed = c.fed; bc.released = c.released
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
        /** Fry a 10-gallon grow-out holds before crowding costs (Roux 2021: juvenile fighting). */
        const val NURSERY_FRY_CAP = 150.0
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
)
