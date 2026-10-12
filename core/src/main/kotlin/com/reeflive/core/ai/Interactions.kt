package com.reeflive.core.ai

import com.reeflive.core.geom.Vec3
import com.reeflive.core.sim.BrainKind
import com.reeflive.core.sim.Organism
import kotlin.math.max
import kotlin.math.sin

/**
 * Per-pair interaction matrix (research 05 §17). [aggression] is the chase drive of the attacker
 * toward the target (0 = tolerance). Pairs listed in [handledBySpecies] are driven by the species
 * brains' own territory behaviors (DefendHost, ChaseIntruder, BlennyRival, Territorial); the rest
 * use the generic [Rivalry] behavior below. Values are the research's qualitative codes mapped to
 * numbers: moderate A/C/T/H = 0.5–0.6, low = 0.15–0.25 (est., tunable, SIMULATION §9.6).
 */
object Interactions {
    private val M: Map<Pair<BrainKind, BrainKind>, Double> = buildMap {
        fun put(a: BrainKind, b: BrainKind, v: Double) = put(a to b, v)
        put(BrainKind.CLOWN, BrainKind.CLOWN, 0.3)            // pair hierarchy: female dominates male
        put(BrainKind.TANG, BrainKind.TANG, 0.6)              // H, A-C in tight tanks
        put(BrainKind.CARDINAL, BrainKind.CARDINAL, 0.1)      // H within the school
        put(BrainKind.WRASSE, BrainKind.WRASSE, 0.5)          // T with other cleaners
        put(BrainKind.HAWK, BrainKind.HAWK, 0.6)
        put(BrainKind.HAWK, BrainKind.DOTTYBACK, 0.25)        // low C
        put(BrainKind.LAWNMOWER_BLENNY, BrainKind.LAWNMOWER_BLENNY, 0.6)
        put(BrainKind.LAWNMOWER_BLENNY, BrainKind.PEEKER_BLENNY, 0.25)
        put(BrainKind.PEEKER_BLENNY, BrainKind.PEEKER_BLENNY, 0.5)
        put(BrainKind.PEEKER_BLENNY, BrainKind.LAWNMOWER_BLENNY, 0.2)
        put(BrainKind.DOTTYBACK, BrainKind.DOTTYBACK, 0.7)
        put(BrainKind.DOTTYBACK, BrainKind.CLOWN, 0.2)         // may challenge OC near the host
        put(BrainKind.DOTTYBACK, BrainKind.HAWK, 0.2)
        put(BrainKind.BANDED_SHRIMP, BrainKind.BANDED_SHRIMP, 0.8)   // kills unpaired conspecifics
        put(BrainKind.BANDED_SHRIMP, BrainKind.CLEANER_SHRIMP, 0.35)
    }

    private val handledBySpecies: Set<Pair<BrainKind, BrainKind>> = setOf(
        BrainKind.LAWNMOWER_BLENNY to BrainKind.LAWNMOWER_BLENNY, BrainKind.LAWNMOWER_BLENNY to BrainKind.PEEKER_BLENNY,
        BrainKind.PEEKER_BLENNY to BrainKind.PEEKER_BLENNY, BrainKind.PEEKER_BLENNY to BrainKind.LAWNMOWER_BLENNY,
        BrainKind.DOTTYBACK to BrainKind.DOTTYBACK,
    )

    fun aggression(attacker: BrainKind, target: BrainKind): Double = M[attacker to target] ?: 0.0

    /** Drive used by the generic [Rivalry] behavior (0 when a species behavior covers the pair). */
    fun genericDrive(attacker: Organism, target: Organism): Double {
        if (attacker.brainKind to target.brainKind in handledBySpecies) return 0.0
        val a = aggression(attacker.brainKind, target.brainKind)
        if (a <= 0) return 0.0
        // Hierarchy among conspecifics: only the dominant one (female clown, else larger, else bolder) chases.
        if (attacker.brainKind == target.brainKind && !dominates(attacker, target)) return 0.0
        return a
    }

    fun dominates(a: Organism, b: Organism): Boolean {
        if (a.brainKind == BrainKind.CLOWN) {
            if (a.role.equals("FEMALE", true) && !b.role.equals("FEMALE", true)) return true
            if (b.role.equals("FEMALE", true) && !a.role.equals("FEMALE", true)) return false
        }
        if (kotlin.math.abs(a.lengthCm - b.lengthCm) > 0.05 * max(a.lengthCm, b.lengthCm)) return a.lengthCm > b.lengthCm
        return a.aggression + a.boldness > b.aggression + b.boldness
    }

    /** Mean seconds between chases at drive 1 (drive 0.6 → every ~25 min, 0.2 → ~75 min). */
    const val CHASE_INTERVAL_S = 900.0
}

/**
 * Generic matrix-driven chase (research 05 §17): when a target the matrix says to chase comes
 * within the attacker's patch (home radius, or 2 BL + 6 cm), dart at it for up to 3 s; contact
 * startles the target and adds social stress (SIMULATION §3.2). Cooldown scales with 1/drive.
 */
class Rivalry(val b: SpeciesBrain) : Behavior("rivalry", "Chasing a rival") {
    private var who: Organism? = null
    private var nextAt = 0.0
    override val targetVerb: String get() = "chasing"
    override fun score(c: Ctx): Double {
        val o = c.o
        if (c.p.night || c.t < nextAt || o.stress > 0.6 || o.health < 0.5 || !o.visible) return 0.0
        val reach = 2 * c.L + 6
        var best: Organism? = null
        var bestDrive = 0.0
        for (n in c.p.neighbors(o.pos, reach)) {
            if (n === o || !n.alive || !n.visible || n.posture == 3) continue
            val d = Social.chaseDrive(o, n, Interactions.genericDrive(o, n))
            if (d > bestDrive) { bestDrive = d; best = n }
        }
        who = best
        if (best == null) return 0.0
        return (0.45 + 0.35 * bestDrive * (0.5 + o.aggression)).coerceAtMost(0.8)
    }

    override fun enter(c: Ctx) {
        val d = who?.let { Interactions.genericDrive(c.o, it) } ?: 0.3
        nextAt = c.t + Interactions.CHASE_INTERVAL_S / max(d, 0.05) * c.rng.range(0.6, 1.4)
    }

    override fun update(c: Ctx, out: SteerIntent) {
        val w = who ?: run { finished = true; return }
        c.o.focusId = w.id
        if (c.o.isFish) out.seek(w.pos, c.o.burstSpeed * 0.6, arrive = 0.5, urgency = 0.8)
        else { out.crawl = true; out.seek(w.pos, c.o.cruiseSpeed * 1.5, arrive = 0.5, urgency = 0.8) }
        if (c.o.pos.distanceTo(w.pos) < 0.6 * c.L + 0.5 * w.lengthCm) {
            val drive = Interactions.genericDrive(c.o, w)
            c.sim.agentOf(w)?.let { it.brain.onAttacked(it.ctx, c.o.pos, 0.3 + 0.4 * drive, c.o) }
            w.socialStress += 0.02 + 0.04 * drive
            finished = true
        }
        if (elapsed > 3.0 || !w.alive) finished = true
    }
}

/**
 * Sick-animal behavior (research 05 §4 sign order): lethargic animals hang low near cover and stop
 * exploring; moribund ones lie on the sand barely moving (fish) or stop moving (inverts).
 */
class Unwell(val b: SpeciesBrain) : Behavior("unwell", "Lethargic") {
    private var spot: Vec3? = null
    private var moribund = false
    override fun score(c: Ctx): Double {
        val o = c.o
        moribund = o.health < 0.2 || o.stress > 0.88
        val lethargic = o.health < 0.45 || o.stress > 0.65
        return when { moribund -> 0.9; lethargic -> 0.6 + 0.2 * o.stress; else -> 0.0 }
    }

    override fun enter(c: Ctx) {
        spot = null
    }

    override fun update(c: Ctx, out: SteerIntent) {
        val o = c.o
        if (!o.isFish) {
            // inverts: hold still where they are (attached postures keep their current placement)
            out.place(o.pos, o.heading, o.up, 2.0)
            out.posture = o.posture
            return
        }
        if (spot == null || c.rng.chance(c.dt / 60)) {
            val cover = c.coverPoint(o.pos + Vec3.Y * 10.0, 25.0)
            val y = if (moribund) c.rock.sandTopCm + 0.3 * c.L + 0.8 else c.zoneY(0.15)
            spot = c.safe(Vec3(cover.x, y, cover.z))
        }
        out.seek(spot!!, o.cruiseSpeed * (if (moribund) 0.25 else 0.4), arrive = 3.0)
        out.hover = true
        out.ampMul = if (moribund) 0.25 else 0.5
        out.pectoral = 0.3
        if (moribund) { out.pitchBias = -0.1; out.rollExtra = 0.25 + 0.1 * sin(c.t * 0.7) }
    }
}
