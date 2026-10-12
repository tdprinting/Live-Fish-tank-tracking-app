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

/** One row of `sim/interactions.json` (research 06 §5.3). See the file's `doc` field for units. */
@Serializable
data class PairRow(
    val id: String,
    val actor: String,
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
    val killDay: Double = 0.0,
    val killDusk: Double = 0.0,
    val evidence: String = "",
) {
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

    /** Behaviour rows (bouts run by [PairBouts]); predation/defensive rows are handled by their own code. */
    fun boutRowsFor(o: Organism, sim: Simulation): List<PairRow> =
        rows.filter { it.type != "predation" && it.type != "defensive" && actorMatches(it, o, sim) }

    fun actorMatches(r: PairRow, o: Organism, sim: Simulation): Boolean = when (r.actor) {
        "CLOWN_F" -> o.brainKind == BrainKind.CLOWN && ClownQueue.isDominant(o, sim)
        "CLOWN_M" -> o.brainKind == BrainKind.CLOWN && !ClownQueue.isDominant(o, sim)
        else -> o.brainKind.name == r.actor
    }

    fun targetMatches(r: PairRow, actor: Organism, t: Organism): Boolean {
        if (t === actor || !t.alive || !t.visible) return false
        val kindOk = r.targets.any { k ->
            when (k) {
                "ANY_FISH" -> t.isFish
                else -> t.brainKind.name == k
            }
        }
        if (!kindOk) return false
        return when (r.filter) {
            "notClown" -> t.brainKind != BrainKind.CLOWN && t.brainKind != BrainKind.EEL
            "gobyJuvenile" -> t.brainKind != BrainKind.ENGINEER_GOBY || t.lengthCm < GobyBrain.ADULT_CM
            "notCleaning" -> (t.brain.current?.name ?: "") != "work"
            "sleeping" -> t.sleeping
            else -> true
        }
    }

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
        BrainKind.ENGINEER_GOBY -> 3.0
        BrainKind.PEEKER_BLENNY -> 2.0
        BrainKind.CARDINAL -> 1.0
        BrainKind.EEL -> 9.5
        else -> if (o.isFish) 3.0 else 0.0
    }

    /** rank_eff = rank − 2·S_env − 1·S_slow_social; a holder outside its nucleus drops two places. */
    fun rankEff(o: Organism, sim: Simulation, atNucleus: Boolean = true): Double {
        val s = o.stressState
        return baseRank(o, sim) - (if (atNucleus) 0.0 else 2.0) - 2 * s.sEnv - s.slow(StressModel.Ch.SOCIAL)
    }

    // ------------------------------------------------------------------ crowding (06 §3.4)
    /** Σ BL² / footprint for the default stock (the 06 baseline at current sizes). */
    const val CROWDING_BASE = 0.14
    /** Aggression gain per unit crowding above baseline; 8 reproduces the §5.6 #19 band (tang 7.5 → 15 cm ≈ +30 %). */
    const val CROWDING_GAIN = 8.0
    /** Tactile cleaning damping (S:Cheney08: chases cut by two-thirds). */
    const val AGG_DAMP = 0.35
    const val AGG_DAMP_S = 20 * 60.0

    fun crowding(sim: Simulation): Double {
        var sum = 0.0
        for (o in sim.organisms) if (o.alive && o.isFish) sum += o.lengthCm * o.lengthCm
        return sum / (sim.rock.lengthCm * sim.rock.widthCm)
    }

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
    private class Entry(val since: Double) { var nextRoll = 0.0; var bouts = 0 }
    private class Pending(val row: PairRow, val target: Organism, val latency: Double)

    private val entries = HashMap<String, HashMap<String, Entry>>()   // row id → target id → entry
    private val recent = HashMap<String, ArrayDeque<Double>>()         // row id → bout times (last hour)
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
                if (t.pos.distanceTo(a) > radius || !PairMatrix.targetMatches(r, o, t)) continue
                if (r.type == "dominance" && !ClownQueue.isDominant(o, sim)) continue
                inside += t.id
                val e = map.getOrPut(t.id) {
                    if (r.sensitise || r.type == "aggression" || r.type == "perch") Social.recordIntrusion(o, t, c.t)
                    Entry(c.t)
                }
                if (pending == null && active == null && c.t - e.since >= r.lingerS && c.t >= e.nextRoll) {
                    e.nextRoll = c.t + REROLL_S
                    val p = probability(c, r, t)
                    if (c.rng.chance(p)) {
                        val intr = o.relations[t.id]?.intrusions ?: 1.0
                        val lat = when {
                            r.sensitise -> 1.5 * (1 - 0.35 * min(2.0, max(0.0, intr - 1)))
                            r.bout.startsWith("LATERAL") && Social.familiarity(o, t, c.t) < 0.2 -> 0.1   // newcomer: attack at once
                            else -> 0.6
                        }
                        pending = Pending(r, t, lat)
                        e.bouts++
                        if (r.sensitise) e.nextRoll = c.t + 4.0
                    }
                }
            }
            // forget targets that left the zone (with a 3 cm margin)
            map.keys.removeAll { id -> id !in inside && sim.organisms.firstOrNull { it.id == id }?.let { it.pos.distanceTo(a) > radius + 3 || !it.alive } != false }
        }
    }

    /** Per-entry probability with every modifier of 06 §3–§4. */
    fun probability(c: Ctx, r: PairRow, t: Organism): Double {
        val o = c.o
        val fam = Social.familiarity(o, t, c.t)
        // hourly cap: new pair → maxRate, familiar → floorRate
        val cap = r.floorRatePerH + (r.maxRatePerH - r.floorRatePerH) * (1 - fam)
        val q = recent.getOrPut(r.id) { ArrayDeque() }
        while (q.isNotEmpty() && c.t - q.first() > 3600) q.removeFirst()
        if (cap > 0 && q.size >= cap) return 0.0
        var p = r.pEvent * (1 - r.habituation * fam)
        if (c.t - t.arrivalTime < 86400 && fam < 0.5) p *= r.newcomerMult
        if (r.sensitise) p *= 1 + min(1.0, max(0.0, ((o.relations[t.id]?.intrusions ?: 1.0) - 1) / 2))
        if (r.hungerScaled) {
            p *= 0.5 + 0.5 * o.hunger
            if (o.hunger < 0.3 && c.sim.water.s.algaeHair >= 0.6) p *= 0.7   // well fed blenny is calmer (06 §4 table)
        }
        if (r.sizeTerm == "tang") p *= max(0.0, 1.5 - t.lengthCm / o.lengthCm.coerceAtLeast(0.5))
        if (r.type == "dominance" && ClownQueue.ratio(c.sim) < ClownQueue.STABLE_RATIO) p *= 2.0
        val temper = (o.aggression / o.species.temperament.aggression.coerceAtLeast(0.05)).coerceIn(0.7, 1.3)
        p *= temper * StressModel.aggressionMult(o, o.lastTempC) * c.sim.crowdingMult
        if (o.aggressionDampUntil > c.t) p *= PairMatrix.AGG_DAMP
        // a much higher-ranked target is only displayed at (size > residency > history; S:Poulos16)
        if (r.type != "dominance" && PairMatrix.rankEff(t, c.sim) > PairMatrix.rankEff(o, c.sim) + 1.5) p *= 0.3
        return p.coerceIn(0.0, 1.0)
    }

    override fun enter(c: Ctx) {
        active = pending; pending = null
        phase = 0; phaseT = 0.0; nip = false; dart = c.rng.chance(0.2)
        travelled = 0.0; lastPos = c.o.pos
        val a = active ?: run { finished = true; return }
        recent.getOrPut(a.row.id) { ArrayDeque() }.addLast(c.t)
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
                val contact = o.pos.distanceTo(t.pos) < 0.3 * c.L + 0.5 * t.lengthCm + 0.5
                if (contact && !r.displayOnly) { nip = true; o.mouth = 1.0 }
                val limit = if (r.displayOnly) 2 * c.L else len
                if (contact || travelled > limit || phaseT > 1.5) { phase = 2; phaseT = 0.0 }
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
                StressModel.addSocial(t, r.stressToTarget * 4 * received)
                t.socialStress += r.stressToTarget * received
            }
            if (t.brainKind == BrainKind.CARDINAL || t.brainKind == BrainKind.PEEKER_BLENNY) t.timidUntil = c.t + 600
        }
        o.counters.add(Counters.K.CHASE_GIVEN, c.t)
        t.counters.add(Counters.K.CHASE_RECEIVED, c.t)
        c.sim.emit(SimEvent.Chase(o.id, t.id, r.bout, nip, r.displayOnly && !dart, c.sim.simTimeSec))
    }

    private fun finish(c: Ctx) { active = null; finished = true; c.o.dorsalRaise = 0.0 }

    override fun exit(c: Ctx) { active = null; c.o.dorsalRaise = 0.0 }

    /** Clears every territory entry (RearrangeRock). */
    fun reset() { entries.clear(); pending = null }

    companion object { const val REROLL_S = 60.0 }
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
    override fun update(c: Ctx, out: SteerIntent) {
        val o = c.o
        val w = o.poseFor ?: run { finished = true; return }
        o.focusId = w.id
        if (o.retaliateUntil > c.t) {
            // chase the cheater 1–2 BL (residents; S:Bshary01)
            out.seek(w.pos, o.burstSpeed * 0.6, arrive = 0.5, urgency = 0.8)
            return
        }
        out.hover = true; out.pitchBias = 0.44; out.pectoral = 1.0; out.ampMul = 0.2
        out.seek(o.pos + o.heading * 0.2, o.cruiseSpeed * 0.1, arrive = 2.0)
        o.mouth = 0.7
        o.finClamp = 0.0
        if (o.poseUntil <= c.t) finished = true
    }
}

/** Sine helper for display wobble. */
internal fun wobble(t: Double, hz: Double) = sin(2 * Math.PI * hz * t)
