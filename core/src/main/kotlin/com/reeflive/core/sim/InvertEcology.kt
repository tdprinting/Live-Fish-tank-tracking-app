package com.reeflive.core.sim

import com.reeflive.core.geom.Rng
import kotlin.math.max
import kotlin.math.min

/**
 * Slow invertebrate ecology that spans several animals (research 04): hermit shell swaps and
 * fights, asterina grazing on organ pipe coral and the feather duster's crown cycle. Stepped once
 * per chemistry tick from [Simulation]; it owns its own seeded [Rng] so it never perturbs the
 * streams the rest of the simulation draws from.
 */
class InvertEcology(private val sim: Simulation) {
    private val rng = Rng(sim.config.seed * 31L + 0x1AE5L)

    // ============================================================== hermit shells (B-4)
    /**
     * One hermit's shell state. Sizes are shell length in cm: [needCm] is the smallest shell that
     * fits the body now and grows [growPerDay] (fraction per day); [shellCm] is the shell it wears.
     */
    class HermitShell(var shellCm: Double, var needCm: Double, val growPerDay: Double) {
        var swapUntil = -1e9
        var nextAttemptAt = 0.0
        /** Outgrown: the body no longer fits (tightness above 1). */
        val tightness: Double get() = needCm / shellCm
        val outgrown: Boolean get() = tightness > 1.0
    }

    /** Spare empty shells in the tank (shell length in cm), consumed by hermits that outgrow theirs. */
    val spareShells = ArrayList<Double>()
    val hermits = HashMap<String, HermitShell>()
    var shellSwaps = 0; private set
    var shellFights = 0; private set
    var shellKills = 0; private set
    private val harvested = HashSet<String>()

    /** Sandbox: drop [count] spare shells of [sizeCm] (0 = sized for the largest hermit's body now, with room to grow). */
    fun addSpareShells(count: Int, sizeCm: Double = 0.0) {
        val size = if (sizeCm > 0) sizeCm else {
            val biggest = sim.organisms.filter { it.alive && it.brainKind == BrainKind.HERMIT }.maxOfOrNull { hermitState(it).needCm } ?: 2.5
            biggest * 1.3
        }
        repeat(count.coerceIn(0, MAX_SPARES)) { if (spareShells.size < MAX_SPARES) spareShells += size }
    }

    fun shellOf(o: Organism): HermitShell? = hermits[o.id]

    /** True while the hermit is out of its shell mid-swap (the brain holds still, drawn in). */
    fun swapping(o: Organism): Boolean = hermits[o.id]?.let { sim.simTimeSec < it.swapUntil } ?: false

    private fun hermitState(o: Organism): HermitShell = hermits.getOrPut(o.id) {
        HermitShell(o.lengthCm * rng.range(1.1, 1.4), o.lengthCm, rng.range(0.002, 0.006))
    }

    /** Fight/steal aggressiveness by genus: blue-leg hermits fight conspecifics a lot, scarlet hermits rarely (04 §5-6). */
    private fun pushiness(o: Organism) = if (o.species.id.startsWith("paguristes")) 0.25 else 1.0

    fun reset() { spareShells.clear(); hermits.clear(); harvested.clear(); dusters.clear(); asterinaBitten = 0.0 }

    fun step(dtMin: Double) {
        val t = sim.simTimeSec
        hermitStep(dtMin, t)
        asterinaStep(dtMin)
        dusterStep(dtMin)
    }

    // ============================================================== feather duster crown (B-6)
    /**
     * A feather duster's crown (research 04 §6 [TFH-SI]): [crown] 1 = full fan, 0 = bare tube. A lost
     * crown regrows over [regrowMin] (1-2 months) while the worm is healthy; it stalls when it is not.
     */
    class DusterCrown {
        var crown = 1.0
        var regrowMin = 45.0 * 1440
        /** Net minutes spent retracted (builds while pulled in, drains at half rate while open). */
        var retractedMin = 0.0
        var lostAt = Double.NaN
        var timesLost = 0
    }

    val dusters = HashMap<String, DusterCrown>()

    /** Crown size 0..1 for the brain/pose; 1 for anything that is not a tracked duster. */
    fun crownOf(o: Organism): Double = dusters[o.id]?.crown ?: 1.0

    /** The crown is lost (disturbance, poor water or a sandbox call): bare tube until it regrows 30-60 days later. */
    fun loseCrown(o: Organism) {
        val st = dusters.getOrPut(o.id) { DusterCrown() }
        st.crown = 0.0; st.retractedMin = 0.0; st.lostAt = sim.simTimeSec; st.timesLost++
        st.regrowMin = rng.range(REGROW_DAYS_MIN, REGROW_DAYS_MAX) * 1440.0
        o.extension = 0.0
    }

    private fun dusterStep(dtMin: Double) {
        val live = sim.organisms.filter { it.alive && it.brainKind == BrainKind.FEATHER_DUSTER }
        dusters.keys.retainAll(live.map { it.id }.toSet())
        for (d in live) {
            val st = dusters.getOrPut(d.id) { DusterCrown() }
            if (st.crown >= 1.0) {
                st.retractedMin = max(0.0, st.retractedMin + if (d.extension < 0.2) dtMin else -0.5 * dtMin)
                val sick = d.health < 0.5 && rng.chance(dtMin / (2 * 1440.0))   // poor water sheds the crown
                if (st.retractedMin >= LOSS_RETRACTED_MIN || sick) loseCrown(d)
            } else {
                if (d.health > 0.5) {
                    st.crown = min(1.0, st.crown + dtMin / st.regrowMin)
                    if (st.crown >= 1.0) { st.retractedMin = 0.0; d.extension = 1.0 }   // fully regrown: the fan opens again
                }
                else if (d.health < 0.3) st.crown = max(0.0, st.crown - dtMin / (14 * 1440.0))   // a shrinking regrowth: death may follow
            }
            if (st.crown < 1.0) d.extension = min(d.extension, st.crown)
        }
    }

    // ============================================================== asterina vs organ pipe (B-5)
    /** Colony tissue the asterina stars have grazed off organ pipe coral in total (UI / tests). */
    var asterinaBitten = 0.0; private set

    /**
     * Asterina stars nibble organ pipe polyps once their population is high (research 04 §11, matrix
     * M risk): at [Simulation.ASTERINA_CAP] stars or more every organ pipe colony loses tissue through
     * the normal [Coral] tissue/[Coral.disturb] mechanism (the polyps retract while grazed). Below
     * the cap that [Simulation] itself enforces, they only graze film.
     */
    private fun asterinaStep(dtMin: Double) {
        val stars = sim.organisms.count { it.alive && it.brainKind == BrainKind.ASTERINA }
        if (stars < Simulation.ASTERINA_CAP) return
        for (c in sim.corals) {
            if (c.kind != CoralKind.ORGAN_PIPE || c.tissue <= 0.0) continue
            val before = c.tissue
            c.tissue = max(0.0, before - ASTERINA_TISSUE_PER_STAR_MIN * stars * dtMin)
            c.disturb = max(c.disturb, 0.5)
            val eaten = before - c.tissue
            asterinaBitten += eaten
            sim.water.addDetritusTanGrams(eaten * c.kind.biomass * Simulation.CORAL_TAN_PER_COLONY)
        }
    }

    private fun hermitStep(dtMin: Double, t: Double) {
        val live = sim.organisms.filter { it.alive && it.brainKind == BrainKind.HERMIT }
        hermits.keys.retainAll(live.map { it.id }.toSet())
        // empty shells of snails that died of other causes become spares
        for (s in sim.organisms) if (!s.alive && s.deadSince != null && s.brainKind in SNAILS && harvested.add(s.id) && spareShells.size < MAX_SPARES) spareShells += s.lengthCm
        for (h in live) { val st = hermitState(h); st.needCm += st.needCm * st.growPerDay * dtMin / 1440.0 }
        for (h in live) {
            val st = hermits[h.id] ?: continue
            if (t < st.swapUntil || st.tightness < SNUG) continue
            val spare = bestSpare(st.needCm)
            if (spare >= 0) {
                if (rng.chance((dtMin * SWAP_PER_MIN).coerceAtMost(1.0))) swapIn(h, st, spare, t)
                continue
            }
            if (!st.outgrown) continue
            // outgrown with no spare: cramped and stressed, then fights and shell theft
            h.socialStress = min(0.25, h.socialStress + 0.02 * dtMin * (st.tightness - 0.95))
            if (t < st.nextAttemptAt) continue
            val push = pushiness(h)
            val rival = live.filter { it !== h && it.alive }.mapNotNull { r -> hermits[r.id]?.let { r to it } }
                .filter { (_, rs) -> rs.shellCm > st.shellCm && rs.shellCm >= st.needCm && rs.tightness < st.tightness && t >= rs.swapUntil }
                .minByOrNull { (_, rs) -> rs.shellCm }
            if (rival != null && rng.chance((dtMin * FIGHT_PER_MIN * push).coerceAtMost(1.0))) { fight(h, st, rival.first, rival.second, t); continue }
            val snail = sim.organisms.filter { it.alive && it.brainKind in SNAILS && it.lengthCm >= st.needCm && it.lengthCm <= st.needCm * 2.2 }
                .minByOrNull { it.lengthCm }
            if (snail != null && rng.chance((dtMin * SNAIL_KILL_PER_MIN * push * (1 + 4 * (st.tightness - 1))).coerceAtMost(1.0))) takeSnailShell(h, st, snail, t)
        }
    }

    /** Index of the spare shell that fits [need] best (smallest with room ≥ 5 %, within 1.8×), or -1. */
    private fun bestSpare(need: Double): Int {
        var best = -1
        for (i in spareShells.indices) {
            val s = spareShells[i]
            if (s >= need * 1.05 && s <= need * 1.8 && (best < 0 || s < spareShells[best])) best = i
        }
        return best
    }

    private fun swapIn(h: Organism, st: HermitShell, spareIdx: Int, t: Double) {
        val new = spareShells.removeAt(spareIdx)
        if (spareShells.size < MAX_SPARES) spareShells += st.shellCm   // the old shell is left behind for the next hermit
        st.shellCm = new
        st.swapUntil = t + rng.range(10.0, 25.0)
        shellSwaps++
    }

    private fun fight(a: Organism, sa: HermitShell, d: Organism, sd: HermitShell, t: Double) {
        shellFights++
        val pWin = (0.5 + 0.4 * (a.lengthCm - d.lengthCm) / max(a.lengthCm, d.lengthCm) + 0.1 * (sa.tightness - sd.tightness)).coerceIn(0.2, 0.85)
        StressModel.addSocial(a, 0.3); StressModel.addSocial(d, 0.5)
        a.startle = max(a.startle, 0.2); d.startle = max(d.startle, 0.6)
        sa.nextAttemptAt = t + 3600.0
        if (rng.chance(pWin)) {
            // attacker flips the rival and shakes it out; the loser is left with the attacker's old, too-small shell
            val tmp = sa.shellCm; sa.shellCm = sd.shellCm; sd.shellCm = tmp
            sa.swapUntil = t + rng.range(15.0, 40.0); sd.swapUntil = sa.swapUntil
            StressModel.addSocial(d, 0.4)
        }
    }

    private fun takeSnailShell(h: Organism, st: HermitShell, snail: Organism, t: Double) {
        st.nextAttemptAt = t + 12 * 3600.0
        if (!sim.predationEnabled) {
            // Reality mode: a scare instead of a death (06 §5.3 reality rule)
            snail.counters.add(Counters.K.NEAR_MISS, t)
            StressModel.addSocial(snail, 0.8)
            snail.startle = max(snail.startle, 0.8)
            sim.emit(SimEvent.NearMiss(h.id, snail.id, "shell-theft", t))
            return
        }
        val events = ArrayList<SimEvent>()
        HealthModel.kill(snail, "shell taken by ${h.nickname}", t, events)
        events.forEach { sim.emit(it) }
        harvested += snail.id
        if (spareShells.size < MAX_SPARES) spareShells += st.shellCm
        st.shellCm = snail.lengthCm
        st.swapUntil = t + rng.range(20.0, 45.0)
        h.hunger = max(0.0, h.hunger - 0.2)
        shellKills++
        shellSwaps++
    }

    companion object {
        const val MAX_SPARES = 30
        /** Below this need/shell ratio a hermit is comfortable; above it it starts looking for a bigger shell. */
        const val SNUG = 0.9
        /** Chance per minute a hermit with a fitting spare shell at hand makes the swap (~10 min). */
        const val SWAP_PER_MIN = 0.1
        /** Per minute, an outgrown hermit with no spare picks a fight over a roomier shell (blue-leg ≈ 2/day). */
        const val FIGHT_PER_MIN = 0.0015
        /** Per minute, an outgrown hermit with no spare takes a snail's shell: moderate risk (04 §14), ≈ 0.2/day. */
        const val SNAIL_KILL_PER_MIN = 0.00015
        /** Tissue fraction one star removes per minute once the population is high; 4 stars ≈ 17 days to bare skeleton net of regrowth (moderate risk, est.). */
        const val ASTERINA_TISSUE_PER_STAR_MIN = 0.000015
        /** About 6 h net retracted (flinching nearly all the time) and the crown is shed. */
        const val LOSS_RETRACTED_MIN = 360.0
        const val REGROW_DAYS_MIN = 30.0
        const val REGROW_DAYS_MAX = 60.0
        private val SNAILS = setOf(BrainKind.ASTREA, BrainKind.NASSARIUS)
    }
}
