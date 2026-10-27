package com.reeflive.core.ai

import com.reeflive.core.geom.Vec3
import com.reeflive.core.sim.BrainKind
import com.reeflive.core.sim.Counters
import com.reeflive.core.sim.Organism
import com.reeflive.core.sim.SimEvent
import com.reeflive.core.sim.Simulation
import com.reeflive.core.sim.StressModel
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Row kinds finer than a BrainKind ([PairMatrix.subKind]). */
private val SUB_KINDS = setOf("ZEBRASOMA", "XANTHURUM", "FAIRY_WRASSE_M")

/** One row of `sim/interactions.json` (research 06 §5.3). See the file's `doc` field for units. */
@Serializable
data class PairRow(
    val id: String,
    val actor: String,
    /** Species ids the row is limited to within [actor]'s kind (empty = every species of that kind). */
    val actorSpecies: List<String> = emptyList(),
    val targets: List<String>,
    val filter: String? = null,
    val type: String,
    val anchor: String = "body",
    val triggerRadius: Double = 10.0,
    val triggerBL: Double = 0.0,
    val lingerS: Double = 0.5,
    val pEvent: Double,
    val maxRatePerH: Double = 0.0,
    val floorRatePerH: Double = 0.0,
    val bout: String,
    val chaseLen: Double = 0.0,
    val chaseLenLarge: Double? = null,
    val chaseSpeedBL: Double = 0.0,
    val stressToTarget: Double = 0.0,
    val habituation: Double = 0.0,
    val newcomerMult: Double = 1.0,
    val hungerScaled: Boolean = false,
    val sizeTerm: String? = null,
    val sensitise: Boolean = false,
    val period: String = "day",
    val lethal: Boolean = false,
    val perNight: Boolean = false,
    val minActorCm: Double = 0.0,
    val moltWindowH: Double = 0.0,
    /** Conspecific molt rows: the smallest group in which the row applies (a pair is stable; S:Wong11). */
    val minGroup: Int = 0,
    /** Night predation on fish ("sleeping" filter): largest prey length as a fraction of the actor's. */
    val maxPreyRatio: Double = 0.8,
    val killDay: Double = 0.0,
    val killDusk: Double = 0.0,
    val evidence: String = "",
) {
    /** The actor / a target is a [PairMatrix.subKind] (resolved once, so plain rows keep the cheap kind compare). */
    @kotlinx.serialization.Transient val subActor: Boolean = actor in SUB_KINDS
    @kotlinx.serialization.Transient val subTargets: Boolean = targets.any { it in SUB_KINDS }

    val displayOnly: Boolean get() = type == "standoff" || type == "ignore" || chaseLen <= 0.0 || bout == "MUTUAL_DISPLAY" || bout == "ORIENT_HOP"
}

@Serializable
private data class PairFile(val version: Int = 1, val rows: List<PairRow> = emptyList())

/**
 * The pair-interaction matrix plus the rank and crowding rules of research 06 §3 (SIMULATION §9.10).
 * Rows are loaded once from `sim/interactions.json` on the classpath.
 */
object PairMatrix {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    val rows: List<PairRow> by lazy {
        val text = PairMatrix::class.java.classLoader?.getResourceAsStream("sim/interactions.json")?.bufferedReader()?.use { it.readText() }
        if (text == null) emptyList() else runCatching { json.decodeFromString(PairFile.serializer(), text).rows }.getOrElse { emptyList() }
    }

    fun row(id: String): PairRow? = rows.firstOrNull { it.id == id }

    private val rivalKinds: Set<Pair<BrainKind, BrainKind>> by lazy {
        rows.flatMap { r -> BrainKind.values().filter { it.name == r.actor }.flatMap { a -> r.targets.mapNotNull { k -> BrainKind.values().firstOrNull { it.name == k }?.let { a to it } } } }.toSet()
    }

    /** [t] has a row of its own against [o]'s kind (a dottyback's standoff with the clowns): a rival, not a bystander. */
    fun isRival(t: Organism, o: Organism): Boolean = (t.brainKind to o.brainKind) in rivalKinds

    /** Behaviour rows (bouts run by [PairBouts]); predation/defensive rows are handled by their own code. */
    fun boutRowsFor(o: Organism, sim: Simulation): List<PairRow> =
        rows.filter { it.type != "predation" && it.type != "defensive" && actorMatches(it, o, sim) }

    fun actorMatches(r: PairRow, o: Organism, sim: Simulation): Boolean = when (r.actor) {
        "CLOWN_F" -> o.brainKind == BrainKind.CLOWN && ClownQueue.isDominant(o, sim)
        "CLOWN_M" -> o.brainKind == BrainKind.CLOWN && !ClownQueue.isDominant(o, sim)
        "BANGGAI_M" -> isBanggaiMale(o)
        else -> (if (r.subActor) subKind(r.actor, o) == true else o.brainKind.name == r.actor) &&
            (r.actorSpecies.isEmpty() || o.species.id in r.actorSpecies)
    }

    /**
     * Row kinds finer than a [BrainKind] (research 42): ZEBRASOMA = the sailfin tangs (yellow, purple), XANTHURUM = the
     * purple tang alone, FAIRY_WRASSE_M = a male fairy wrasse (or a female far through the change). Null for a plain kind.
     */
    fun subKind(k: String, o: Organism): Boolean? = when (k) {
        "ZEBRASOMA" -> o.brainKind == BrainKind.TANG && o.species.zebrasoma
        "XANTHURUM" -> o.brainKind == BrainKind.TANG && o.species.id == XANTHURUM_ID
        "FAIRY_WRASSE_M" -> o.brainKind == BrainKind.FAIRY_WRASSE && FairyWrasseBrain.isMale(o)
        else -> null
    }

    /** A brain gets the bout rows its kind (or one of its [subKind]s) can act in; the sub-kind rows are checked again each scan. */
    fun mayAct(r: PairRow, o: Organism): Boolean =
        r.actor == o.brainKind.name || o.brainKind == BrainKind.CLOWN && r.actor.startsWith("CLOWN_") ||
            r.actor == "FAIRY_WRASSE_M" && o.brainKind == BrainKind.FAIRY_WRASSE || r.subActor && subKind(r.actor, o) == true

    const val XANTHURUM_ID = "zebrasoma_xanthurum"

    fun targetMatches(r: PairRow, actor: Organism, t: Organism): Boolean {
        if (t === actor || !t.alive || !t.visible) return false
        // a recognised partner is not a stranger (research 12: banded shrimp tell a former mate from a stranger)
        // (the clown pair's dominance quiver is hierarchy upkeep between the mates themselves, so it still applies)
        if (actor.mateId != null && actor.mateId == t.id && r.type != "dominance") return false
        val kindOk = r.targets.any { k ->
            when (k) {
                // the cleaner is "never chased sustainedly because of the cleaning role" (06 §3.1, S:Cheney08);
                // rows that do target it name it (dottyback, other wrasse)
                "ANY_FISH" -> t.isFish && t.brainKind != BrainKind.WRASSE
                "BANGGAI_M" -> isBanggaiMale(t)
                else -> if (r.subTargets) subKind(k, t) ?: (t.brainKind.name == k) else t.brainKind.name == k
            }
        }
        if (!kindOk) return false
        // a cleaner at work is posed for, not attacked (06 §2.2, §2.4, §3.1: never chased sustainedly while cleaning)
        if (t.brainKind == BrainKind.WRASSE && isCleaning(t)) return false
        return when (r.filter) {
            "notClown" -> t.brainKind != BrainKind.CLOWN && t.brainKind != BrainKind.EEL
            "gobyJuvenile" -> t.brainKind != BrainKind.ENGINEER_GOBY || t.lengthCm < GobyBrain.ADULT_CM
            "notCleaning" -> !isCleaning(t)
            "sleeping" -> t.sleeping
            "notZebrasoma" -> !t.species.zebrasoma
            else -> true
        }
    }

    /** A male Banggai cardinal (the pajama cardinal shares its brain kind but not its squabbles; research 50 §2). */
    fun isBanggaiMale(o: Organism): Boolean = o.brainKind == BrainKind.CARDINAL && o.sexFemale == false && o.species.id.startsWith("pterapogon")

    fun isCleaning(t: Organism): Boolean = (t.brain.current?.name ?: "") == "work"

    // ------------------------------------------------------------------ rank (06 §3.1, §4.1.5)
    /** Base rank, highest first: clown ♀ > dottyback > lawnmower > tang > hawk > clown ♂ > wrasse > goby > tailspot > cardinal. */
    fun baseRank(o: Organism, sim: Simulation): Double = when (o.brainKind) {
        BrainKind.CLOWN -> if (ClownQueue.isDominant(o, sim)) 10.0 else 5.0
        BrainKind.DOTTYBACK -> 9.0
        BrainKind.LAWNMOWER_BLENNY -> 8.0
        // the tang grows past everyone within 6–12 months
        BrainKind.TANG -> 7.0 + max(0.0, (o.lengthCm - 7.5) / 2.0)
        BrainKind.HAWK -> 6.0
        BrainKind.WRASSE -> 4.0
        BrainKind.FAIRY_WRASSE -> 3.5
        BrainKind.ENGINEER_GOBY -> 3.0
        BrainKind.PEEKER_BLENNY -> 2.0
        BrainKind.CARDINAL -> 1.0
        // research 41: shy hoverers at the bottom of the order
        BrainKind.FIREFISH -> 1.5
        BrainKind.CHROMIS -> 2.5
        BrainKind.EEL -> 9.5
        // research 50: the gramma holds its cave against most small fish but gives way to a dottyback; the watchman
        // holds its burrow mouth
        BrainKind.GRAMMA -> 4.5
        BrainKind.WATCHMAN_GOBY -> 2.5
        // research 51: the six line bosses most small peaceful fish; the mandarin and clown goby give way to everyone
        BrainKind.SIXLINE -> 5.0
        BrainKind.MANDARIN -> 1.0
        BrainKind.CLOWN_GOBY -> 1.5
        // research 60: a butterfly is big but mild; it gives way to the tangs and the dottyback
        BrainKind.BUTTERFLY -> 5.0
        else -> if (o.isFish) 3.0 else 0.0
    }

    /** rank_eff = rank − 2·S_env − 1·S_slow_social; a holder outside its nucleus drops two places. */
    fun rankEff(o: Organism, sim: Simulation, atNucleus: Boolean = true): Double {
        val s = o.stressState
        return baseRank(o, sim) - (if (atNucleus) 0.0 else 2.0) - 2 * s.sEnv - s.slow(StressModel.Ch.SOCIAL)
    }

    // ------------------------------------------------------------------ crowding (06 §3.4)
    /**
     * Σ BL² / footprint for the default stock (the 06 baseline at current sizes). Calibrated as 0.14 on the earlier
     * 121.9 × 33 cm footprint; the default stock in Tee's measured tank is the baseline, so it scales with the depth.
     */
    const val CROWDING_BASE = 0.14 * 33.0 / com.reeflive.core.sim.TankDims.DEPTH_CM
    /**
     * Aggression gain per unit crowding above baseline; 8 on the 33 cm-deep footprint reproduced the §5.6 #19 band
     * (tang 7.5 → 15 cm ≈ +30 %), scaled with the footprint so the same growth gives the same gain.
     */
    const val CROWDING_GAIN = 8.0 * com.reeflive.core.sim.TankDims.DEPTH_CM / 33.0
    /** Tactile cleaning damping (S:Cheney08: chases cut by two-thirds). */
    const val AGG_DAMP = 0.35
    const val AGG_DAMP_S = 20 * 60.0
    /** Peak bout multiplier right after a RearrangeRock, and its decay time constant [I]. */
    const val REARRANGE_MULT = 3.0
    const val REARRANGE_TAU_S = 12 * 3600.0

    fun crowding(sim: Simulation): Double {
        var sum = 0.0
        for (o in sim.organisms) if (o.alive && o.isFish) sum += o.lengthCm * o.lengthCm
        return sum / (sim.rock.lengthCm * sim.rock.widthCm)
    }

    /** Bout-rate multiplier [dt] seconds after a RearrangeRock: 1 + 2·e^(−dt / 12 h) [I]. */
    fun rearrangeMult(dt: Double): Double = if (dt < 0) 1.0 else 1 + (REARRANGE_MULT - 1) * kotlin.math.exp(-dt / REARRANGE_TAU_S)

    fun crowdingMult(c: Double): Double = (1 + CROWDING_GAIN * (c - CROWDING_BASE)).coerceIn(0.5, 2.5)
}

/** Clown dominance is a size-ratio queue (S:Buston03 1.26, S:Colleye12 1.30): female first, then by length. */
object ClownQueue {
    const val STABLE_RATIO = 1.26

    fun ordered(sim: Simulation): List<Organism> =
        sim.organisms.filter { it.alive && it.brainKind == BrainKind.CLOWN }
            .sortedWith(compareByDescending<Organism> { it.role.equals("FEMALE", true) }.thenByDescending { it.lengthCm })

    fun isDominant(o: Organism, sim: Simulation): Boolean = ordered(sim).firstOrNull() === o

    /** Size ratio dominant / next; < 1.26 means an unsettled queue (more dominance bouts, nips). */
    fun ratio(sim: Simulation): Double {
        val q = ordered(sim)
        if (q.size < 2) return 2.0
        return q[0].lengthCm / q[1].lengthCm.coerceAtLeast(0.1)
    }
}

/**
 * Territory-radius triggers with target filters (research 06 §0 #3, §5.3–5.4). One instance per
 * animal that is an actor in the matrix. Every 0.5 s it checks who is inside each row's trigger
 * zone; a new entry (after `lingerS`) rolls `pEvent` with the modifiers below and, on success, runs
 * the row's display sequence (`Bout`): orient/display → chase → nip → return.
 *
 * p = pEvent × (1 − habituation·familiarity) × newcomer × sensitisation × hunger × size × stress
 *     × crowding × cleaner damping × rank, capped at the row's hourly rate (max → floor with familiarity).
 */
class PairBouts(val b: SpeciesBrain, val rows: List<PairRow>) : Behavior("bout", "Displaying") {
    private class Entry(val since: Double, val linger: Double) { var nextRoll = 0.0; var bouts = 0 }
    /** Per-row observation counters (acceptance tests, stress inspector): entries, entries with ≥ 1 bout, bouts, entry→charge latencies. */
    class RowStats { var entries = 0; var entriesWithBout = 0; var bouts = 0; val latencies = ArrayList<Double>() }
    val stats = HashMap<String, RowStats>()
    fun statsOf(rowId: String): RowStats = stats.getOrPut(rowId) { RowStats() }
    private class Pending(val row: PairRow, val target: Organism, val latency: Double)

    private val entries = HashMap<String, HashMap<String, Entry>>()   // row id → target id → entry
    /** Row id → rate-cap token bucket (refills at the row's current hourly cap, holds max([BURST], cap × [BURST_H])). */
    private class Bucket(var tokens: Double, var t: Double)
    private val buckets = HashMap<String, Bucket>()
    /** Set by [probability] when the row's cap bucket was empty. */
    private var capBlocked = false
    private var scanAt = 0.0
    private var pending: Pending? = null
    private var active: Pending? = null
    private var phase = 0
    private var phaseT = 0.0
    private var travelled = 0.0
    private var lastPos = Vec3.ZERO
    private var nip = false
    private var dart = false

    override val targetVerb: String get() = when (active?.row?.type ?: pending?.row?.type) {
        "dominance" -> "asserting itself over"
        "standoff", "ignore" -> "squaring up to"
        "perch" -> "driving off"
        "intimidation" -> "eyeing"
        else -> "chasing"
    }
    override val committed: Boolean get() = active != null && phase < 3

    fun anchorOf(c: Ctx, r: PairRow): Vec3 = if (r.anchor == "body") c.o.pos else b.anchorPos(c, r.anchor) ?: c.o.pos

    override fun score(c: Ctx): Double {
        val o = c.o
        if (!o.visible || o.health < 0.35 || o.sleeping) { pending = null; return 0.0 }
        if (c.t >= scanAt) { scanAt = c.t + 0.5; scan(c) }
        return if (pending != null) 0.82 else 0.0
    }

    private fun scan(c: Ctx) {
        val o = c.o
        val sim = c.sim
        for (r in rows) {
            if (!PairMatrix.actorMatches(r, o, sim)) { entries[r.id]?.clear(); continue }
            val night = c.p.night
            if (r.period == "day" && night || r.period == "night" && !night) { entries[r.id]?.clear(); continue }
            val a = anchorOf(c, r)
            val radius = r.triggerRadius + r.triggerBL * c.L
            val inside = HashSet<String>()
            val map = entries.getOrPut(r.id) { HashMap() }
            for (t in c.p.neighbors(a, radius)) {
                if (t.posture == 3 && t.brainKind != BrainKind.CLOWN) continue   // safe in its own hole
                // a neighbour whose own shelter lies in the host's halo is a resident there, not an intruder: it is let be
                // at its shelter, and a harmless one (no rival of the clowns, unlike the dottyback's standoff) anywhere in
                // the outer halo, chased only into the 10 cm core (06 §5.3 territory core / halo; a tailspot leaving its
                // hole 16 cm from the anemone was nipped back in on most trips out) [I]
                if (r.anchor == "host" && t !== o && t.homePos.distanceTo(a) < radius &&
                    (t.pos.distanceTo(t.homePos) < OWN_SHELTER_CM + 0.5 * t.lengthCm ||
                        t.pos.distanceTo(a) > HOST_CORE_CM && !PairMatrix.isRival(t, o))) continue
                if (t.pos.distanceTo(a) > radius || !PairMatrix.targetMatches(r, o, t)) continue
                // a settled standoff partitions the rock: past familiarity 0.6 only a close approach to the
                // cave counts, the halo shrinking to 40 % at familiarity 1 (06 §2.3) [I]
                if (r.type == "standoff" && t.pos.distanceTo(a) > radius * standoffReach(Social.familiarity(o, t, c.t))) continue
                if (r.type == "dominance" && !ClownQueue.isDominant(o, sim)) continue
                inside += t.id
                val e = map.getOrPut(t.id) {
                    if (r.sensitise || r.type == "aggression" || r.type == "perch") Social.recordIntrusion(o, t, c.t)
                    statsOf(r.id).entries++
                    // host-defence sensitisation shortens the stare as well as the display (S:Fleming24: latency −60–76 %)
                    Entry(c.t, r.lingerS * sensitisation(r, o, t))
                }
                if (pending == null && active == null && c.t - e.since >= e.linger && c.t >= e.nextRoll) {
                    // once a minute for a passer-by; a host defender facing an intruder that stays keeps re-deciding,
                    // every 20 s at first and far sooner as it sensitises (latency factor², → ~2 s by the third
                    // intrusion; S:Fleming24 latency −60–76 %)
                    e.nextRoll = c.t + if (r.sensitise) REROLL_SENS_S * sensitisation(r, o, t).let { it * it } else REROLL_S
                    val p = probability(c, r, t)
                    // a roll the hourly cap blocked was no decision: look again once the next token is in
                    if (capBlocked) e.nextRoll = min(e.nextRoll, c.t + CAP_RETRY_S)
                    if (c.rng.chance(p)) {
                        val lat = when {
                            r.sensitise -> 1.5 * sensitisation(r, o, t)
                            r.bout.startsWith("LATERAL") && Social.familiarity(o, t, c.t) < 0.2 -> 0.1   // newcomer: attack at once
                            else -> 0.6
                        }
                        pending = Pending(r, t, lat)
                        val st = statsOf(r.id)
                        if (e.bouts == 0) { st.entriesWithBout++; st.latencies += c.t - e.since + lat }
                        st.bouts++
                        e.bouts++
                        if (r.sensitise) e.nextRoll = c.t + 4.0
                    }
                }
            }
            // forget targets that left the zone (with a 3 cm margin)
            map.keys.removeAll { id -> id !in inside && sim.organisms.firstOrNull { it.id == id }?.let { it.pos.distanceTo(a) > radius + 3 || !it.alive } != false }
        }
    }

    /** Fraction of the trigger radius a standoff row still watches at familiarity [fam]: 1 up to 0.6, 0.4 at 1. */
    fun standoffReach(fam: Double): Double = 1 - 1.5 * (fam - 0.6).coerceIn(0.0, 0.4)

    /** Latency factor after repeated intrusions: 1 → 0.3 by the third entry (sensitising rows only). */
    fun sensitisation(r: PairRow, o: Organism, t: Organism): Double {
        if (!r.sensitise) return 1.0
        val intr = o.relations[t.id]?.intrusions ?: 1.0
        return 1 - 0.35 * min(2.0, max(0.0, intr - 1))
    }

    /** Per-entry probability with every modifier of 06 §3–§4. */
    fun probability(c: Ctx, r: PairRow, t: Organism): Double {
        capBlocked = false
        val o = c.o
        val fam = Social.familiarity(o, t, c.t)
        // hourly cap: new pair → maxRate, familiar → floorRate
        var cap = r.floorRatePerH + (r.maxRatePerH - r.floorRatePerH) * (1 - fam)
        // a pair of strangers, either one new in its first day: the hierarchy is still being settled
        val newcomer = c.t - t.arrivalTime < 86400 && fam < 0.5
        val strangers = fam < 0.5 && (newcomer || c.t - o.arrivalTime < 86400)
        // tactile damping cuts the bout rate, not only the per-entry odds: a rate-capped holder (the host
        // clown sees an intruder every few seconds) otherwise ignores the cleaner (S:Cheney08, 06 §3.5).
        // It calms known neighbours; between strangers the newcomer fights go ahead (S:Townsend04's 90 % and
        // the newcomer accounts of S:H-FC-dotty come from reefs and tanks with cleaners) [I]. The clown pair's
        // dominance quiver is hierarchy upkeep, not aggression, and is left alone.
        val damped = o.aggressionDampUntil > c.t && !strangers && r.type != "dominance"
        if (damped) cap *= PairMatrix.AGG_DAMP
        // rearranged rock resets every boundary: each holder is a newcomer to its own patch, so bout rate and
        // odds run up to ×3, fading over a day (06 §3.3 newcomer ×2–3 for ~24 h; S:H-FC-dotty) [I]
        val reset = if (r.type == "dominance") 1.0 else PairMatrix.rearrangeMult(c.t - c.sim.territoryResetAt)
        cap *= reset
        // a token bucket rather than a sliding hour, so the cap follows the damping as it comes and goes
        if (cap > 0) {
            // holds a quarter-hour of the cap (≥ 1): room for a short escalation (repeated intrusions, a newcomer)
            // without lifting the long-run rate
            val burst = max(BURST, cap * BURST_H)
            val bk = buckets.getOrPut(r.id) { Bucket(burst, c.t) }
            bk.tokens = min(burst, bk.tokens + max(0.0, c.t - bk.t) * cap / 3600)
            bk.t = c.t
            if (bk.tokens < 1) { capBlocked = true; return 0.0 }
        }
        var p = r.pEvent * (1 - r.habituation * fam)
        if (newcomer) p *= r.newcomerMult
        p *= reset
        if (r.sensitise) p *= 1 + min(1.0, max(0.0, ((o.relations[t.id]?.intrusions ?: 1.0) - 1) / 2))
        if (r.hungerScaled) {
            // normalised so an ordinary appetite (hunger ≥ 0.5) gives the sourced 90 % (07 gap 1); well fed → calmer
            p *= ((0.5 + 0.5 * o.hunger) / 0.75).coerceAtMost(1.0)
            if (o.hunger < 0.3 && c.sim.water.s.algaeHair >= 0.6) p *= 0.7   // well fed blenny is calmer (06 §4 table)
        }
        if (r.sizeTerm == "tang") p *= max(0.0, 1.5 - t.lengthCm / o.lengthCm.coerceAtLeast(0.5))
        // the lawnmower chases blennies of its own size or smaller only (S:Townsend04, 07 gap 1; "same size" ±10 % [I])
        if (r.sizeTerm == "notLarger" && t.lengthCm > NOT_LARGER_TOL * o.lengthCm) return 0.0
        if (r.type == "dominance" && ClownQueue.ratio(c.sim) < ClownQueue.STABLE_RATIO) p *= 2.0
        val temper = (o.aggression / o.species.temperament.aggression.coerceAtLeast(0.05)).coerceIn(0.7, 1.3)
        p *= temper * StressModel.aggressionMult(o, o.lastTempC) * c.sim.crowdingMult
        if (damped) p *= PairMatrix.AGG_DAMP
        // a much higher-ranked target is only displayed at (size > residency > history; S:Poulos16)
        if (r.type != "dominance" && PairMatrix.rankEff(t, c.sim) > PairMatrix.rankEff(o, c.sim) + 1.5) p *= 0.3
        return p.coerceIn(0.0, 1.0)
    }

    override fun enter(c: Ctx) {
        active = pending; pending = null
        phase = 0; phaseT = 0.0; nip = false; dart = c.rng.chance(0.2)
        travelled = 0.0; lastPos = c.o.pos
        val a = active ?: run { finished = true; return }
        buckets[a.row.id]?.let { it.tokens -= 1 }
    }

    override fun update(c: Ctx, out: SteerIntent) {
        val a = active ?: run { finished = true; return }
        val r = a.row
        val t = a.target
        val o = c.o
        if (!t.alive) { finish(c); return }
        o.focusId = t.id
        phaseT += c.dt
        travelled += o.pos.distanceTo(lastPos); lastPos = o.pos
        val toT = (t.pos - o.pos).normalized(o.heading)
        when (phase) {
            // 0: orient / display (latency); dorsal raised, slight roll toward the target
            0 -> {
                out.faceDir = toT; out.hover = true; out.pectoral = 1.0
                if (o.isFish) out.seek(o.pos + toT * 0.5, o.cruiseSpeed * 0.3, arrive = 1.0)
                o.dorsalRaise = 1.0
                if (r.bout.startsWith("LATERAL") || r.bout == "MUTUAL_DISPLAY" || r.bout.startsWith("STARE")) { out.rollExtra = 0.3; t.dorsalRaise = max(t.dorsalRaise, if (r.displayOnly) 1.0 else 0.0) }
                if (r.type == "dominance") {
                    out.seek(t.pos, o.cruiseSpeed, arrive = 0.5 * c.L)
                    if (o.pos.distanceTo(t.pos) < 0.8 * c.L + 0.5 * t.lengthCm || phaseT > 3) {
                        c.sim.agentOf(t)?.brain?.onDominated(c.sim.agentOf(t)!!.ctx, o)
                        phase = 2; phaseT = 0.0
                    }
                    return
                }
                if (t.brainKind == BrainKind.TANG) t.spineFlare = 1.0   // 06 §5.4 SPINE_FLARE reaction
                // a sailfin tang squaring up raises its fins and turns its scalpel toward the rival (research 42 §2-3)
                if (o.brainKind == BrainKind.TANG) o.spineFlare = 1.0
                // a male fairy wrasse facing another male flashes at him (research 42 §1)
                if (r.actor == "FAIRY_WRASSE_M") o.display = 1.0
                // a banded shrimp squaring up raises its big banded claws, spread toward the intruder (06 §5.3 CLAW_RAISE)
                if (r.bout == "CLAW_RAISE") o.clawDisplay = 1.0
                val displayS = if (r.displayOnly) 1.0 else a.latency
                if (phaseT >= displayS) {
                    phase = if (r.displayOnly && !dart) 2 else 1; phaseT = 0.0; travelled = 0.0
                }
            }
            // 1: chase ≤ chaseLen at chaseSpeed BL/s (≤ 1.5 s), nip on contact
            1 -> {
                val len = if (r.chaseLenLarge != null && (t.brainKind == BrainKind.TANG || t.brainKind == BrainKind.WRASSE)) r.chaseLenLarge else r.chaseLen
                val speed = min(r.chaseSpeedBL * c.L, o.burstSpeed).coerceAtLeast(o.cruiseSpeed)
                if (o.isFish) out.seek(t.pos, speed, arrive = 0.3 * c.L, urgency = 0.85)
                else { out.crawl = true; out.seek(t.pos, speed, arrive = 0.5, urgency = 0.85) }
                // the claws stay up through the shuffle forward (CLAW_RAISE)
                if (r.bout == "CLAW_RAISE") o.clawDisplay = 1.0
                val contact = o.pos.distanceTo(t.pos) < 0.3 * c.L + 0.5 * t.lengthCm + 0.5
                if (contact && !r.displayOnly) { nip = true; if (r.bout == "LATERAL_TAIL_SLAP") o.spineFlare = 1.0 else o.mouth = 1.0 }
                // a tail slap: alongside the rival it swings broadside and lashes the tail, spine out (research 42 §2-3)
                if (r.bout == "LATERAL_TAIL_SLAP" && o.pos.distanceTo(t.pos) < c.L + 0.5 * t.lengthCm) {
                    out.faceDir = (toT cross Vec3.Y).normalized(o.heading)
                    out.tailMul = 2.5; out.rollExtra = 0.2
                    o.spineFlare = 1.0
                }
                val limit = if (r.displayOnly) 2 * c.L else len
                // a host defender breaks off once the intruder is out of the halo: it does not leave its anemone [I]
                val fled = r.anchor == "host" && t.pos.distanceTo(anchorOf(c, r)) > r.triggerRadius + r.triggerBL * c.L
                if (contact || fled || travelled > limit || phaseT > 1.5) { phase = 2; phaseT = 0.0 }
            }
            // 2: apply the outcome, then return to the anchor
            2 -> {
                outcome(c, r, t)
                phase = 3; phaseT = 0.0
            }
            else -> {
                val home = anchorOf(c, r)
                if (o.isFish) out.seek(c.safe(home + Vec3(0.0, 2.0, 2.0), 1.0), o.cruiseSpeed * 1.4, arrive = 2.0)
                if (phaseT > 1.0) finish(c)
            }
        }
    }

    private fun outcome(c: Ctx, r: PairRow, t: Organism) {
        val o = c.o
        val chased = phase == 2 && !r.displayOnly || dart
        val received = StressModel.receivedGain(t)
        if (r.type != "dominance") {
            if (chased || nip) {
                c.sim.agentOf(t)?.let { it.brain.onAttacked(it.ctx, o.pos, if (nip) 0.6 else 0.35, o) }
            }
            if (r.stressToTarget > 0) {
                StressModel.addSocial(t, r.stressToTarget * 2 * received)
                t.socialStress += r.stressToTarget * received
            }
            if (t.brainKind == BrainKind.CARDINAL || t.brainKind == BrainKind.PEEKER_BLENNY) t.timidUntil = c.t + 600
        }
        o.counters.add(Counters.K.CHASE_GIVEN, c.t)
        t.counters.add(Counters.K.CHASE_RECEIVED, c.t)
        c.sim.emit(SimEvent.Chase(o.id, t.id, r.bout, nip, r.displayOnly && !dart, c.sim.simTimeSec))
    }

    private fun finish(c: Ctx) { active = null; finished = true; c.o.dorsalRaise = 0.0 }

    override fun exit(c: Ctx) {
        // a chase cut short by a higher-priority behaviour still happened: settle its outcome (and event). That covers
        // phase 2 too: the chase ended on the tick before the preemption and its outcome was due on the next update
        active?.let { a -> if ((phase == 1 || phase == 2) && a.target.alive) { phase = 2; outcome(c, a.row, a.target) } }
        active = null; c.o.dorsalRaise = 0.0
    }

    /** Clears every territory entry (RearrangeRock). */
    fun reset() { entries.clear(); pending = null }

    companion object {
        const val REROLL_S = 60.0
        /** A resident within this (cm, plus half its length) of its own shelter is not a host intruder [I]. */
        const val OWN_SHELTER_CM = 4.0
        /** Host territory core (cm, 06 §5.3 "core / halo 10 / 22"): defended against residents of the halo too. */
        const val HOST_CORE_CM = 10.0
        /** First re-roll interval of a sensitising (host-defence) row [I]. */
        const val REROLL_SENS_S = 20.0
        /** Re-roll delay after a roll the hourly cap blocked. */
        const val CAP_RETRY_S = 10.0
        /** "Same size or smaller" for the sizeTerm notLarger rows: up to 10 % longer still counts [I]. */
        const val NOT_LARGER_TOL = 1.1
        /** Bouts a row may run back to back before the hourly cap paces it. */
        const val BURST = 1.0
        /** ... or this many hours of the cap, if more. */
        const val BURST_H = 0.25
    }
}

/**
 * Reactions requested by another animal (06 §5.4–5.5): the subordinate clown's QUIVER_YIELD and a
 * client's pose for the cleaner. Score 0.96 while active so they override ordinary behaviours but
 * not escape (0.97+).
 */
class Yield(val b: SpeciesBrain) : Behavior("yield", "Yielding") {
    override val targetVerb: String get() = "yielding to"
    override val committed: Boolean get() = elapsed < 2.5
    override fun score(c: Ctx) = if (c.t < b.yieldUntil && b.yieldTo?.alive == true) 0.96 else 0.0
    override fun update(c: Ctx, out: SteerIntent) {
        val d = b.yieldTo ?: run { finished = true; return }
        val o = c.o
        o.focusId = d.id
        val quiverS = b.yieldUntil - b.yieldBackS
        if (c.t < quiverS) {
            // head-first lateral quiver ~8 Hz travelling tailward (S:Colleye12 form)
            o.quiver = 1.0
            out.hover = true; out.faceDir = (d.pos - o.pos).normalized(o.heading)
            out.seek(o.pos, 0.0, arrive = 1.0)
            out.waddleAmp = 0.05 * 8; out.waddleHz = 8.0
        } else {
            o.quiver = 0.0
            // back away 0.5–2 BL at 1 BL/s
            out.seek(c.safe(o.pos + (o.pos - d.pos).normalized(Vec3.X) * c.L, 1.0), c.L, arrive = 0.5)
            out.hover = true
        }
        if (c.t >= b.yieldUntil) finished = true
    }
    override fun exit(c: Ctx) { c.o.quiver = 0.0 }
}

/** Client side of the cleaning loop: hold a pose (fins spread, mouth open, pitch +25°, drift). */
class PoseForCleaner(val b: SpeciesBrain) : Behavior("pose", "Posing for the cleaner") {
    override val targetVerb: String get() = "posing for"
    override fun score(c: Ctx): Double {
        val o = c.o
        if (o.retaliateUntil > c.t && o.poseFor?.alive == true) return 0.96
        return if (o.poseUntil > c.t && o.poseFor?.alive == true) 0.96 else 0.0
    }
    /** A perched (benthic) client poses where it sits instead of lifting off the rock. */
    private var perch: Rest? = null
    /** Where a free-swimming client took up its pose: it holds that spot while the cleaner works round it. */
    private var hold: Vec3? = null
    override fun enter(c: Ctx) {
        perch = if (c.o.attached) Rest(c.o.pos, c.o.heading, c.o.up, posture = c.o.posture) else null
        hold = c.o.pos
    }
    override fun update(c: Ctx, out: SteerIntent) {
        val o = c.o
        val w = o.poseFor ?: run { finished = true; return }
        o.focusId = w.id
        perch?.let { p ->
            if (o.retaliateUntil <= c.t) {
                out.rest(p, 4.0); out.pectoral = 1.0
                o.mouth = 0.7; o.finClamp = 0.0
                if (o.poseUntil <= c.t) finished = true
                return
            }
        }
        if (o.retaliateUntil > c.t) {
            // chase the cheater (residents; S:Bshary01, 1–2 BL) about a body length from where it posed, then give up:
            // chasing a cleaner that kept fleeing took an engineer goby 32 cm from its burrow
            val from = hold
            if (from != null && o.pos.distanceTo(from) > c.L) { o.retaliateUntil = c.t; finished = true; return }
            out.seek(w.pos, o.burstSpeed * 0.6, arrive = 0.5, urgency = 0.8)
            return
        }
        // freeze head-up with fins spread, mouth agape and gill covers flared; only the pectorals scull to hold station
        out.hover = true; out.pitchBias = 0.44; out.pectoral = 1.0; out.ampMul = 0.1; out.tailMul = 0.3
        // it holds the spot where it began to pose (seeking just ahead of itself, the separation from the cleaner working
        // along its body pushed it off: a posing engineer goby was herded 25 cm along the front glass at 6-9 cm/s)
        val h = hold ?: o.pos.also { hold = it }
        val pushed = o.pos.distanceTo(h) > 2.0
        out.seek(h, o.cruiseSpeed * 0.5, arrive = 2.0)
        // pushed off its spot, it gives way less: separation grows as r/d near the cleaner nosing along its flank, which
        // outran the way back and herded a posing engineer goby 30 cm along the front glass at 4-5 cm/s
        if (pushed) out.separation = 0.25
        o.mouth = 0.7
        o.finClamp = 0.0
        o.gill = max(o.gill, 0.9)
        flared = true
        if (o.poseUntil <= c.t) finished = true
    }
    private var flared = false
    override fun exit(c: Ctx) { if (flared) { c.o.gill = 0.3; flared = false } }
}

/** Sine helper for display wobble. */
internal fun wobble(t: Double, hz: Double) = sin(2 * Math.PI * hz * t)

/**
 * Territory re-anchoring after a [com.reeflive.core.sim.Command.RearrangeRock] (06 §3.3, §5.6 #20). Moved rock resets
 * every boundary, so each territorial fish re-marks its patch: it swims a loop of [POINTS] stations round its anchor
 * (cave, hole, perch, host, station) at about [RING] of its trigger radius, pausing at each with the dorsal fin up and
 * facing out, then settles back at the anchor ([Organism.territoryAnchoredAt]); the hot bout rate of the following
 * hours ([PairMatrix.rearrangeMult]) does the rest. No utility noise and no random draws, so adding it leaves every
 * random sequence as it was until rock is moved. A crawler (the banded shrimp on its ledge) re-anchors in place.
 */
class ReclaimTerritory(val b: SpeciesBrain, val anchor: String, val radius: Double) : Behavior("reclaim", "Re-marking its territory") {
    override val noisy: Boolean get() = false
    override val targetVerb: String? get() = null
    private var points: Array<Vec3>? = null
    private var idx = 0
    private var legT = 0.0
    private var dwell = 0.0

    private fun due(c: Ctx) = c.sim.territoryResetAt > c.o.territoryAnchoredAt

    override fun score(c: Ctx): Double {
        val o = c.o
        if (!due(c)) return 0.0
        if (!o.isFish) { o.territoryAnchoredAt = c.t; return 0.0 }
        // the silt settles first; it does it while awake (rock moved at night is re-marked in the morning)
        if (c.t - c.sim.territoryResetAt < START_S || !o.visible || o.health < 0.35 || c.activity < 0.3) return 0.0
        return SCORE
    }

    /** The reset the loop was laid out for, and when it began: a bout that breaks in resumes the same loop. */
    private var builtFor = Double.NaN
    private var startedAt = 0.0

    override fun enter(c: Ctx) {
        val o = c.o
        dwell = 0.0; legT = 0.0
        if (points != null && builtFor == c.sim.territoryResetAt) return
        builtFor = c.sim.territoryResetAt; startedAt = c.t
        val home = b.anchorPos(c, anchor) ?: o.homePos
        val r = (RING * radius).coerceIn(RING_MIN_CM, RING_MAX_CM)
        // the loop starts on the side the fish is on and runs round (its id picks the way), never through the rock
        val start = kotlin.math.atan2(o.pos.z - home.z, o.pos.x - home.x)
        val dir = if (o.id.hashCode() and 1 == 0) 1.0 else -1.0
        points = Array(POINTS) { k ->
            val a = start + dir * k * 2 * Math.PI / POINTS
            c.safe(home + Vec3(kotlin.math.cos(a) * r, 2.0 + (k % 2) * 2.0, kotlin.math.sin(a) * r), 1.0)
        }
        idx = 0
    }

    override fun update(c: Ctx, out: SteerIntent) {
        val o = c.o
        val pts = points ?: run { finished = true; return }
        val home = b.anchorPos(c, anchor) ?: o.homePos
        legT += c.dt
        // a loop that cannot be finished (blocked, chased about) still ends: the patch is held from where it is
        if (c.t - startedAt > MAX_S) { o.territoryAnchoredAt = c.t; finished = true; return }
        if (idx < pts.size) {
            val p = pts[idx]
            if (dwell > 0.0 || o.pos.distanceTo(p) < ARRIVE_CM || legT > LEG_S) {
                // at a station: hover broadside, dorsal up, looking out over the new boundary
                dwell += c.dt
                out.hover = true; out.pectoral = 1.0
                out.seek(p, o.cruiseSpeed * 0.3, arrive = 1.5)
                out.faceDir = (p - home).let { Vec3(it.x, 0.0, it.z) }.normalized(o.heading)
                o.dorsalRaise = 1.0
                if (dwell >= DWELL_S) { idx++; dwell = 0.0; legT = 0.0; o.dorsalRaise = 0.0 }
            } else {
                out.seek(p, o.cruiseSpeed * 1.2, arrive = ARRIVE_CM)
            }
            return
        }
        // back to the anchor: the territory is re-formed round it
        out.seek(c.safe(home + Vec3(0.0, 2.0, 0.0), 1.0), o.cruiseSpeed, arrive = 2.0)
        if (o.pos.distanceTo(home) < HOME_CM + 0.5 * o.lengthCm || legT > LEG_S) {
            o.territoryAnchoredAt = c.t
            finished = true
        }
    }

    override fun exit(c: Ctx) { c.o.dorsalRaise = 0.0 }

    companion object {
        const val SCORE = 0.8
        const val START_S = 20.0
        const val POINTS = 4
        /** Loop radius as a share of the territory trigger radius, clamped (cm) [I]. */
        const val RING = 0.6
        const val RING_MIN_CM = 6.0
        const val RING_MAX_CM = 18.0
        const val ARRIVE_CM = 2.0
        const val DWELL_S = 1.2
        const val LEG_S = 10.0
        const val HOME_CM = 4.0
        const val MAX_S = 90.0
    }
}
