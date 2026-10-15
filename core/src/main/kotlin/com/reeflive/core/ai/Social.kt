package com.reeflive.core.ai

import com.reeflive.core.geom.Vec3
import com.reeflive.core.sim.Organism
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * What one individual remembers about another (SIMULATION §9.6). All values 0..1 except
 * [dominance] (−1 = it dominates me, +1 = I dominate it). Fear and grudge fade with half-lives of
 * hours; familiarity builds over days of shared space ("dear enemy": familiar neighbours are chased less).
 */
class Relation(val otherId: String) {
    var familiarity = 0.0
    var fear = 0.0
    /** Recent aggression I received from / gave to the other. */
    var harassedBy = 0.0
    var harassed = 0.0
    var dominance = 0.0
    var lastCleanedAt = -1e9
    var cleanings = 0
    var lastSeenAt = -1e9
    /** Halo intrusions by the other into my territory (+1 each, half-life 24 h; host-defence sensitisation, 06 §5.1). */
    var intrusions = 0.0
    /** Client's trust in this cleaner: +0.05 per honest inspection, −0.2 per jolt, relaxes to 0.7 over ~6 h (S:Pinto11). */
    var cleanerTrust = 0.7
    var lastInteractionAt = -1e9
}

/** Perception of others and relationship memory (research 05 §17; owner request: socially aware, non-repetitive). */
object Social {
    /**
     * The [max] most salient relationships of [o] for the FishCard / follow mode. Others that died
     * or left the tank are skipped; weak memories (salience < 0.08) are left out.
     */
    fun summarize(o: Organism, byId: Map<String, Organism>, now: Double, max: Int = 5): List<com.reeflive.core.sim.RelationInfo> {
        if (o.relations.isEmpty()) return emptyList()
        return o.relations.values.asSequence()
            .mapNotNull { r -> byId[r.otherId]?.takeIf { it.alive }?.let { r to it } }
            .map { (r, other) -> Triple(r, other, salience(r)) }
            .filter { it.third >= 0.08 }
            .sortedByDescending { it.third }
            .take(max)
            .map { (r, other, _) ->
                com.reeflive.core.sim.RelationInfo(
                    other.id, other.nickname, other.species.common, labelOf(r),
                    r.familiarity.toFloat(), r.fear.toFloat(), r.harassedBy.toFloat(), r.harassed.toFloat(), r.dominance.toFloat(),
                    r.cleanings, if (r.lastSeenAt < -1e8) -1f else (now - r.lastSeenAt).toFloat().coerceAtLeast(0f),
                    r.intrusions.toFloat(), r.cleanerTrust.toFloat(),
                )
            }.toList()
    }

    fun salience(r: Relation): Double =
        r.fear + r.harassedBy + r.harassed + 0.5 * kotlin.math.abs(r.dominance) + 0.3 * r.familiarity + 0.1 * min(r.cleanings, 5)

    fun labelOf(r: Relation): String = when {
        r.fear > 0.5 -> "Afraid of"
        r.harassedBy > 0.3 && r.harassedBy >= r.harassed -> "Chased by"
        r.harassed > 0.3 -> "Chases"
        r.dominance > 0.3 -> "Dominates"
        r.dominance < -0.3 -> "Defers to"
        r.cleanings > 0 -> "Cleaning partner"
        r.fear > 0.2 -> "Wary of"
        else -> "Knows"
    }

    const val PERCEIVE_INTERVAL = 1.0          // s between full scans per animal
    /** Co-resident familiarity growth: 0.15/day, baseline aggression by ~day 7 (06 §3.3). */
    const val FAMILIARITY_PER_DAY = 0.15
    /** Threat memory: +0.3 per chase received, half-life 6 h (06 §5.1). */
    const val FEAR_HALF_LIFE_S = 6 * 3600.0
    const val INTRUSION_HALF_LIFE_S = 24 * 3600.0
    const val TRUST_BASE = 0.7
    const val TRUST_TAU_S = 6 * 3600.0
    const val GRUDGE_HALF_LIFE_S = 6 * 3600.0

    /**
     * Memory of [other], created on first use. A new record starts from the time the two have shared
     * the tank (0.15/day), so the initial stock already knows each other and a newcomer starts at 0.
     */
    fun rel(o: Organism, other: Organism, now: Double = Double.NaN): Relation = o.relations.getOrPut(other.id) {
        Relation(other.id).also { r ->
            // without an explicit time use the animals' own clocks (set every behaviour tick); never-ticked: 30 days on
            val clock = max(o.clock, other.clock)
            val t = when {
                !now.isNaN() -> now
                !clock.isNaN() -> clock
                else -> max(o.arrivalTime, other.arrivalTime) + 30 * 86400.0
            }
            val days = (t - max(o.arrivalTime, other.arrivalTime)) / 86400.0
            r.familiarity = (FAMILIARITY_PER_DAY * days).coerceIn(0.0, 1.0)
        }
    }

    /** Familiarity with [other] right now (0 for an unknown newcomer). */
    fun familiarity(o: Organism, other: Organism, now: Double): Double =
        o.relations[other.id]?.familiarity ?: (FAMILIARITY_PER_DAY * (now - max(o.arrivalTime, other.arrivalTime)) / 86400.0).coerceIn(0.0, 1.0)

    fun recordIntrusion(holder: Organism, intruder: Organism, now: Double) {
        val r = rel(holder, intruder, now)
        r.intrusions += 1.0
        r.lastInteractionAt = now
    }

    /**
     * Full scan of every other living animal: identity, species, size, distance, state. Updates
     * familiarity and decays memories; sets [Organism.wary] to the individual it currently fears most.
     */
    fun perceive(c: Ctx, dt: Double) {
        val o = c.o
        val fearK = exp(-dt * 0.693 / FEAR_HALF_LIFE_S)
        val grudgeK = exp(-dt * 0.693 / GRUDGE_HALF_LIFE_S)
        val intrK = exp(-dt * 0.693 / INTRUSION_HALF_LIFE_S)
        val trustK = 1 - exp(-dt / TRUST_TAU_S)
        val famGain = FAMILIARITY_PER_DAY * dt / 86400.0
        var wary: Organism? = null
        var waryScore = 0.0
        for (n in c.sim.organisms) {
            if (n === o || !n.alive) continue
            val d = n.pos.distanceTo(o.pos)
            val r = o.relations[n.id] ?: if (d < 30.0 && n.visible) rel(o, n, c.t) else null
            if (r != null) {
                // co-residents become familiar at 0.15/day whether or not they meet (06 §3.3)
                r.familiarity = min(1.0, r.familiarity + famGain)
                if (d < 30.0 && n.visible) r.lastSeenAt = c.t
                fade(r, fearK, grudgeK, intrK, trustK)
            }
            // Wariness toward a specific individual: remembered fear, plus a prior for a much larger aggressive fish.
            if (n.isFish && n.visible && d < 2.5 * c.L + 15) {
                val prior = if (n.lengthCm > 1.6 * o.lengthCm) 0.15 * n.aggression * (1 - (r?.familiarity ?: 0.0)) else 0.0
                val score = ((r?.fear ?: 0.0) + prior) * (1 - d / (2.5 * c.L + 15))
                if (score > waryScore) { waryScore = score; wary = n }
            }
        }
        o.wary = if (waryScore > 0.12) wary else null
        o.waryLevel = waryScore
    }

    private fun fade(r: Relation, fearK: Double, grudgeK: Double, intrK: Double, trustK: Double) {
        r.fear *= fearK; r.harassedBy *= grudgeK; r.harassed *= grudgeK; r.intrusions *= intrK
        r.cleanerTrust += (TRUST_BASE - r.cleanerTrust) * trustK
    }

    /**
     * Fades every remembered fear, grudge, intrusion count and cleaner trust of [o] over [dt] s while the behaviour layer
     * is off ([com.reeflive.core.sim.Simulation.advanceAbstract]): [perceive] only runs on behaviour ticks, so without
     * this a chase from before a 3-day skip was still feared at full strength afterwards.
     */
    fun fadeMemories(o: Organism, dt: Double) {
        if (o.relations.isEmpty()) return
        val fearK = exp(-dt * 0.693 / FEAR_HALF_LIFE_S)
        val grudgeK = exp(-dt * 0.693 / GRUDGE_HALF_LIFE_S)
        val intrK = exp(-dt * 0.693 / INTRUSION_HALF_LIFE_S)
        val trustK = 1 - exp(-dt / TRUST_TAU_S)
        for (r in o.relations.values) fade(r, fearK, grudgeK, intrK, trustK)
    }

    /** Called whenever [attacker] chases/nips [victim] (any behavior). */
    fun recordAttack(attacker: Organism, victim: Organism, mag: Double) {
        val v = rel(victim, attacker)
        v.fear = min(1.0, v.fear + 0.3 * mag.coerceIn(0.3, 1.0) / 0.6)
        v.harassedBy = min(1.0, v.harassedBy + 0.3)
        v.dominance = max(-1.0, v.dominance - 0.15)
        val a = rel(attacker, victim)
        a.harassed = min(1.0, a.harassed + 0.3)
        a.dominance = min(1.0, a.dominance + 0.15)
    }

    fun recordCleaning(cleaner: Organism, client: Organism, t: Double) {
        val r = rel(client, cleaner)
        r.lastCleanedAt = t; r.cleanings++
        r.fear = max(0.0, r.fear - 0.2)
        r.familiarity = min(1.0, r.familiarity + 0.05)
        rel(cleaner, client).cleanings++
    }

    /**
     * Matrix drive modulated by memory: familiar neighbours are tolerated more ("dear enemy"),
     * an individual that recently provoked gets chased more, and a dominant one is not challenged.
     */
    fun chaseDrive(attacker: Organism, target: Organism, base: Double): Double {
        val r = attacker.relations[target.id] ?: return base
        if (r.dominance < -0.4) return 0.0
        return base * (1 - 0.5 * r.familiarity) * (1 + 0.6 * r.harassedBy + 0.3 * max(0.0, r.dominance))
    }
}

/**
 * Keep clear of the individual this animal currently fears (after being chased, or a much larger
 * aggressive fish). Moves to the far side of nearby cover; intensity follows the remembered fear.
 */
class KeepClear(val b: SpeciesBrain) : Behavior("keepclear", "Keeping clear") {
    private var spot: Vec3? = null
    override val targetVerb: String get() = "keeping clear of"
    override fun score(c: Ctx): Double {
        c.o.wary ?: return 0.0
        if (c.p.night || c.o.attached) return 0.0
        return (0.35 + 0.5 * c.o.waryLevel).coerceAtMost(0.75)
    }
    override fun enter(c: Ctx) { spot = null }
    override fun update(c: Ctx, out: SteerIntent) {
        val w = c.o.wary ?: run { finished = true; return }
        c.o.focusId = w.id
        if (spot == null || elapsed > 6) { spot = c.coverPoint(w.pos, 25.0); elapsed = 0.0 }
        out.seek(spot!!, c.o.cruiseSpeed * 1.1, arrive = 3.0, urgency = 0.5)
        out.hover = true
        out.faceDir = (w.pos - c.o.pos).normalized(Vec3.X)   // keeps an eye on it
        if (c.o.pos.distanceTo(w.pos) > 3 * c.L + 20) finished = true
    }
}
