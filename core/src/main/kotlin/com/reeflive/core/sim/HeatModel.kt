package com.reeflive.core.sim

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Catastrophic heat, 33–40 °C (research 08 §7). Runs beside the chronic stress path (StressModel,
 * tolerance curves) on the chemistry tick and owns the acute sequence: agitation, gasping,
 * lethargy on the bottom, loss of equilibrium (LOE), death, species by species.
 *
 *     t_death(T) = anchor × 10^(−slope × (T − 36))          floor 3 min (08 §7.1)
 *     D        += dt / t_death(T − refuge − ΔCTmax)          only while T − refuge > T_ag
 *     D        += dt / t_LOE(T − refuge)                     while that is ≥ CTmax − 1.5 (LOE dwell, 08 §2 stage 4–5)
 *     D        × exp(−dt / 24 h)                              once T < T_ag (08 §6)
 *     stage     = max(stage(T − CTmax), stage(D))             up at once, down one step per 2 h (AGITATED → NORMAL 30 min)
 *
 * CTmax moves with long-term acclimation (+0.4 °C per °C above 26, cap +2; shrimp 0), heat hardening
 * (+0.5 °C for 7 d after a LETHARGIC episode) and the slow-creep penalty (−1 °C, 08 §4). T_ag does not
 * move (McDonnell & Chapman 2015). The LOE dwell term is an addition to §7.1: §2 gives LOE 10–40 min
 * at a hold near CTmax and §6 a 5–10 min rescue window, which the §7.1 curve alone (cardinal 16 h at
 * CTmax − 1.5) cannot produce on a 1 °C/h ramp. Surface seeking is an oxygen behaviour, never a heat one
 * ([surfaceAllowed], 08 §3).
 */
object HeatModel {
    enum class Stage(val label: String) {
        NORMAL(""),
        AGITATED("Agitated by the heat"),
        GASPING("Breathing hard in the heat"),
        LETHARGIC("Lethargic on the bottom"),
        LOE("Loss of equilibrium"),
        DEATH("Heat death"),
    }

    /**
     * Per-group thermal profile (08 §7.1, 26 °C acclimation). [anchorMin] is t_death at 36 °C, [slope] the
     * decades per °C, [refuge] the cooler-microhabitat offset, [loeMin] the LOE dwell time at CTmax − 1.5 (est.),
     * [pLethargic]/[pLoe] the delayed-mortality roll for a peak at LETHARGIC / LOE (08 §6, §7.2).
     */
    data class Profile(
        val group: String, val ctmax: Double, val tChronic: Double, val anchorMin: Double, val slope: Double,
        val refuge: Double, val loeMin: Double, val acclimates: Boolean = true,
        val pLethargic: Double = 0.10, val pLoe: Double = 0.35,
    ) {
        /** Agitation temperature: CTmax − 4 at 26 °C acclimation; does not move with acclimation. */
        val tAg: Double get() = ctmax - 4
    }

    // 08 §7.1 rows; loeMin est. from §2 (LOE 10–40 min at a hold; tangs and the low-scope groups shortest)
    val CARDINAL = Profile("cardinalfish", 36.0, 31.0, 30.0, 1.0, 0.5, 12.0)
    val SHRIMP = Profile("cleaner shrimp", 35.0, 30.0, 20.0, 1.2, 0.5, 10.0, acclimates = false, pLethargic = 0.5, pLoe = 0.9)
    /** Turbo/Trochus/Astraea: 0.5 refuge in the rock, 0 on glass (0.25 average); detached snails re-attach 50/50. */
    val TURBO_SNAIL = Profile("Turbo/Astraea snail", 35.0, 30.0, 20.0, 1.0, 0.25, 15.0, pLethargic = 0.5, pLoe = 0.5)
    val TANG = Profile("tang", 36.5, 30.5, 90.0, 1.0, 0.0, 15.0)
    val CLOWN = Profile("clownfish", 36.5, 31.5, 120.0, 1.0, 0.3, 20.0)
    val CLEANER_WRASSE = Profile("cleaner wrasse", 36.5, 31.0, 90.0, 1.0, 0.3, 15.0)
    val SALLY = Profile("Sally Lightfoot", 36.5, 31.0, 60.0, 1.0, 1.0, 20.0)
    val DOTTYBACK = Profile("dottyback", 37.5, 32.0, 480.0, 0.9, 0.5, 25.0)
    val HAWKFISH = Profile("hawkfish", 37.5, 32.0, 480.0, 0.9, 0.5, 25.0)
    val ENGINEER_GOBY = Profile("engineer goby", 38.0, 32.5, 2160.0, 0.9, 1.0, 25.0)
    val BLENNY = Profile("blenny", 38.5, 33.0, 2160.0, 0.9, 0.7, 25.0)
    val HERMIT = Profile("hermit crab", 38.5, 33.0, 2160.0, 0.9, 1.0, 25.0)
    val NERITE_SNAIL = Profile("nerite/cerith snail", 39.0, 34.0, 7200.0, 0.9, 0.5, 30.0, pLethargic = 0.5, pLoe = 0.5)
    // Not in §7.1 (est.): moray like the dottyback/hawkfish (robust reef-crest predators); echinoderms and
    // tube worms like the subtidal snails' neighbours, between shrimp and cardinal.
    val MORAY = Profile("moray (est.)", 37.5, 32.0, 480.0, 0.9, 0.5, 25.0)
    val ECHINODERM = Profile("brittle star/asterina (est.)", 36.0, 30.5, 60.0, 1.0, 0.5, 15.0)
    val TUBE_WORM = Profile("feather duster (est.)", 36.0, 30.5, 90.0, 1.0, 0.0, 15.0)
    // Diamond goby (est.): a lagoon sand goby, taken as a cooler-water burrower than the reef-flat blennies.
    val SAND_GOBY = Profile("diamond goby (est.)", 37.5, 32.0, 1440.0, 0.9, 0.8, 25.0)
    // Bobtail squid (est., research cephalopod_squid): cultured at 20–25 °C (Hanlon 1997, Jolly 2022); taken as heat-sensitive as the
    // shrimp (no acclimation reserve, short LOE), with a sand refuge.
    val CEPHALOPOD = Profile("bobtail squid (est.)", 33.5, 28.5, 20.0, 1.2, 0.5, 10.0, acclimates = false, pLethargic = 0.5, pLoe = 0.9)

    // Bubble-tip anemone (est., research 40 §8.1): wild stock bleaches at 27 °C over days and dies near 29 °C sustained
    // (Hill & Scott 2012); the slow bleaching is AnemoneModel's, this is only the acute heat response, like the tube worm.
    val ANEMONE = Profile("bubble tip anemone (est.)", 35.0, 30.0, 120.0, 1.0, 0.0, 15.0)

    fun profile(kind: BrainKind): Profile = when (kind) {
        BrainKind.CARDINAL -> CARDINAL
        BrainKind.CLEANER_SHRIMP, BrainKind.BANDED_SHRIMP, BrainKind.PEPPERMINT_SHRIMP -> SHRIMP
        BrainKind.ASTREA -> TURBO_SNAIL
        BrainKind.TANG -> TANG
        BrainKind.CLOWN, BrainKind.GENERIC_FISH -> CLOWN
        BrainKind.WRASSE, BrainKind.LEOPARD_WRASSE, BrainKind.FAIRY_WRASSE -> CLEANER_WRASSE
        BrainKind.SALLY_CRAB -> SALLY
        BrainKind.DOTTYBACK -> DOTTYBACK
        BrainKind.HAWK -> HAWKFISH
        BrainKind.ENGINEER_GOBY -> ENGINEER_GOBY
        BrainKind.DIAMOND_GOBY -> SAND_GOBY
        BrainKind.BOBTAIL_SQUID -> CEPHALOPOD
        BrainKind.LAWNMOWER_BLENNY, BrainKind.PEEKER_BLENNY -> BLENNY
        BrainKind.HERMIT -> HERMIT
        BrainKind.NASSARIUS -> NERITE_SNAIL
        BrainKind.EEL -> MORAY
        BrainKind.BRITTLE_STAR, BrainKind.ASTERINA, BrainKind.GENERIC_INVERT -> ECHINODERM
        BrainKind.FEATHER_DUSTER -> TUBE_WORM
        BrainKind.ANEMONE -> ANEMONE
        // a bivalve: no escape from heat but the swim, little acclimation (est., like the tube worm)
        BrainKind.FILE_CLAM -> TUBE_WORM
    }

    fun isSnail(kind: BrainKind) = kind == BrainKind.ASTREA || kind == BrainKind.NASSARIUS
    fun isShrimp(kind: BrainKind) = kind.isShrimp
    /** Semi-terrestrial crabs leave the water (08 §1.3): they climb to the waterline when agitated. */
    fun climbsOut(kind: BrainKind) = kind == BrainKind.HERMIT || kind == BrainKind.SALLY_CRAB
    /** Blennies and gobies skip the visible agitation stage: they hide low instead of darting (08 §2). */
    fun hidesLow(kind: BrainKind) = kind == BrainKind.LAWNMOWER_BLENNY || kind == BrainKind.PEEKER_BLENNY || kind == BrainKind.ENGINEER_GOBY ||
        kind == BrainKind.DIAMOND_GOBY

    // ---------------------------------------------------------------- constants (08 §6, §7)
    const val D_AGITATED = 0.05
    const val D_GASPING = 0.25
    const val D_LETHARGIC = 0.50
    const val D_LOE = 0.85
    /** D decay below T_ag (minutes). */
    const val DECAY_TAU = 24 * 60.0
    /** Long-term acclimation filter for the CTmax shift (minutes). */
    const val ACCL_TAU = 7 * 1440.0
    /** dT/dt filter (minutes): a 10-min hot-water dump registers as a spike. */
    const val RATE_TAU = 10.0
    const val SPIKE_RATE_CPH = 5.0
    const val CREEP_RATE_CPH = 0.3
    const val HARDENING_C = 0.5
    const val HARDENING_SEC = 7 * 86400.0
    const val T_DEATH_FLOOR_MIN = 3.0
    const val T_LOE_FLOOR_MIN = 1.0
    /** Detached snails that are not back on their foot in 12 h are dead (08 §6). */
    const val SNAIL_DETACH_SEC = 12 * 3600.0
    /** Absolute DO at or below which any fish may surface (06 §5.6 #18 / fish lethal DO bound). */
    const val DO_ASR_MGL = 3.0

    /** Per-animal heat state. */
    class State {
        var d = 0.0
        var dPeak = 0.0
        var stage = Stage.NORMAL
        /** Worst stage of the current episode (reset once fully recovered). */
        var peak = Stage.NORMAL
        var stageSince = 0.0
        /** Long-term acclimation temperature (°C), NaN until the first tick. */
        var acclC = Double.NaN
        var lastT = Double.NaN
        /** Smoothed dT/dt (°C/h). */
        var rateCph = 0.0
        /** Hourly temperature samples (24 h window) for the slow-creep rule. */
        internal val hourly = DoubleArray(25)
        internal var hourlyN = 0
        internal var hourAcc = 0.0
        var minutesAboveChronic = 0.0
        var hardenUntil = -1e18
        var noFeedUntil = -1e18
        /** Scheduled delayed death (sim seconds), NaN when none. */
        var deathAt = Double.NaN
        var rolled = false
        var detachedSince = Double.NaN
        /** Effective CTmax at the last tick (inspector) and that tick's sim time. */
        var ctmax = 0.0
        var lastTick = 0.0
    }

    /** t_death (minutes) at [t] for profile [p] (08 §7.1). */
    fun tDeathMin(p: Profile, t: Double): Double = max(T_DEATH_FLOOR_MIN, p.anchorMin * 10.0.pow(-p.slope * (t - 36)))

    /** LOE dwell time (minutes) at an effective temperature [tEff] against CTmax [ct]: 10× shorter per 2 °C. */
    fun tLoeMin(p: Profile, tEff: Double, ct: Double): Double = max(T_LOE_FLOOR_MIN, p.loeMin * 10.0.pow(-0.5 * (tEff - (ct - 1.5))))

    /** Effective CTmax: acclimation, heat hardening and the slow-creep penalty (08 §4, §6, §7.1). */
    fun ctmax(o: Organism, p: Profile = profile(o.brainKind), now: Double): Double {
        val s = o.heat
        val accl = if (p.acclimates && !s.acclC.isNaN()) (0.4 * (s.acclC - 26)).coerceIn(0.0, 2.0) else 0.0
        val harden = if (now < s.hardenUntil) HARDENING_C else 0.0
        val creep = if (s.hourlyN >= s.hourly.size && creepRate(s) < CREEP_RATE_CPH && s.minutesAboveChronic > 24 * 60) 1.0 else 0.0
        return p.ctmax + accl + harden - creep
    }

    /** Mean dT/dt over the last 24 h (°C/h); 0 until a day of samples exists. */
    private fun creepRate(s: State): Double {
        if (s.hourlyN < s.hourly.size) return 0.0
        val n = s.hourly.size
        val newest = s.hourly[(s.hourlyN - 1) % n]
        val oldest = s.hourly[s.hourlyN % n]
        return (newest - oldest) / 24.0
    }

    /** Stage reached by temperature alone (08 §7.2, ramp triggers on the water temperature). */
    fun tStage(t: Double, p: Profile, ct: Double): Stage = when {
        t >= ct + 1 -> Stage.DEATH
        t >= ct - 1.5 -> Stage.LOE
        t >= ct - 2 -> Stage.LETHARGIC
        t >= ct - 3 -> Stage.GASPING
        t >= p.tAg -> Stage.AGITATED
        else -> Stage.NORMAL
    }

    fun dStage(d: Double): Stage = when {
        d >= 1.0 -> Stage.DEATH
        d >= D_LOE -> Stage.LOE
        d >= D_LETHARGIC -> Stage.LETHARGIC
        d >= D_GASPING -> Stage.GASPING
        d >= D_AGITATED -> Stage.AGITATED
        else -> Stage.NORMAL
    }

    /** Re-acclimate (sandbox "acclimate all to current"). */
    fun acclimate(o: Organism, t: Double) { o.heat.acclC = min(t, StressModel.thermalCeiling(o.brainKind, o.isFish)) }

    /**
     * One chemistry tick. Returns true when the animal died of heat this tick (killed through
     * [HealthModel.kill] with cause "heat").
     */
    fun step(o: Organism, w: WaterState, now: Double, dtMin: Double, events: MutableList<SimEvent>): Boolean {
        val p = profile(o.brainKind)
        val s = o.heat
        val t = w.tempC
        val ceiling = StressModel.thermalCeiling(o.brainKind, o.isFish)
        if (s.lastT.isNaN()) { s.lastT = t; s.acclC = min(t, ceiling); s.stageSince = now }
        // rate of change, long-term acclimation, 24 h window
        s.rateCph += ((t - s.lastT) / (dtMin / 60) - s.rateCph) * (1 - exp(-dtMin / RATE_TAU))
        s.lastT = t
        s.acclC += (min(t, ceiling) - s.acclC) * (1 - exp(-dtMin / ACCL_TAU))
        s.hourAcc += dtMin
        if (s.hourlyN == 0 || s.hourAcc >= 60) { s.hourAcc = 0.0; s.hourly[s.hourlyN % s.hourly.size] = t; s.hourlyN++ }
        s.minutesAboveChronic = if (t > p.tChronic) s.minutesAboveChronic + dtMin else 0.0
        val ct = ctmax(o, p, now)
        s.ctmax = ct
        s.lastTick = now

        // ---- damage integrator (08 §4, §7.1) ----
        val tEff = t - p.refuge
        if (tEff > p.tAg) {
            var rate = 1 / tDeathMin(p, tEff - (ct - p.ctmax))
            if (s.rateCph > SPIKE_RATE_CPH) rate /= 0.8
            if (tEff >= ct - 1.5) rate += 1 / tLoeMin(p, tEff, ct)
            s.d += rate * dtMin
        } else if (t < p.tAg) s.d *= exp(-dtMin / DECAY_TAU)
        s.dPeak = max(s.dPeak, s.d)

        // ---- stage: max of the T and D triggers; recovery rate-limited ----
        val target = maxOf(tStage(t, p, ct), dStage(s.d))
        if (target > s.stage) {
            s.stage = target; s.stageSince = now
            if (target > s.peak) s.peak = target
        } else if (target < s.stage) {
            val hold = if (s.stage == Stage.AGITATED) 30 * 60.0 else 2 * 3600.0
            if (now - s.stageSince >= hold) { s.stage = Stage.entries[s.stage.ordinal - 1]; s.stageSince = now; recovering(o, p, now) }
        }
        if (s.stage == Stage.NORMAL && s.d < D_AGITATED) { s.peak = Stage.NORMAL; s.dPeak = s.d; s.rolled = false }

        // ---- deaths ----
        if (s.stage == Stage.DEATH) { HealthModel.kill(o, "heat", now, events); return true }
        if (isSnail(o.brainKind)) {
            if (s.stage >= Stage.LETHARGIC) { if (s.detachedSince.isNaN()) s.detachedSince = now }
            else if (s.stage <= Stage.GASPING) s.detachedSince = Double.NaN
            if (!s.detachedSince.isNaN() && now - s.detachedSince > SNAIL_DETACH_SEC) { HealthModel.kill(o, "heat", now, events); return true }
        }
        if (!s.deathAt.isNaN() && now >= s.deathAt) { HealthModel.kill(o, "heat", now, events); return true }
        return false
    }

    /**
     * First step down from the episode's peak (08 §6, §7.2): the delayed-mortality roll on the organism's
     * seeded RNG, appetite loss, and heat hardening after a LETHARGIC episode.
     */
    private fun recovering(o: Organism, p: Profile, now: Double) {
        val s = o.heat
        val noFeedH = when (s.peak) { Stage.LOE, Stage.DEATH -> 48.0; Stage.LETHARGIC -> 36.0; Stage.GASPING -> 12.0; else -> 0.0 }
        s.noFeedUntil = max(s.noFeedUntil, now + noFeedH * 3600)
        if (s.rolled) return
        s.rolled = true
        val sev = max(s.dPeak, when { s.peak >= Stage.LOE -> D_LOE; s.peak >= Stage.LETHARGIC -> D_LETHARGIC; else -> 0.0 })
        if (sev >= D_LETHARGIC) s.hardenUntil = now + HARDENING_SEC
        val pDie = when { sev >= D_LOE -> p.pLoe; sev >= D_LETHARGIC -> p.pLethargic; else -> 0.0 }
        if (pDie > 0 && o.rng.chance(pDie)) {
            val window = if (sev >= D_LOE) 48.0 else 72.0
            s.deathAt = now + o.rng.range(1.0, window) * 3600
        }
    }

    /** Feeding: none while the heat stage is AGITATED or worse, nor during the post-episode appetite loss. */
    fun appetiteMult(o: Organism): Double = if (o.heat.stage >= Stage.AGITATED || o.heat.lastTick < o.heat.noFeedUntil) 0.0 else 1.0

    /** O2 saturation (0..1) of [w]. */
    fun o2Sat(w: WaterState): Double = w.doMgL / ChemFns.oxygenSaturation(w.tempC, w.ppt)

    /** ASR threshold (fraction of saturation) at [t] (08 §3: 20 % + 1.5 %·(T − 28)). */
    fun asrThreshold(t: Double): Double = 0.20 + 0.015 * (t - 28)

    /**
     * Aquatic surface respiration is an oxygen response (08 §3): only with low O2 (saturation under the
     * ASR threshold, or the absolute low-DO bound of research 06), and only for an animal that can still
     * swim (NORMAL, AGITATED, GASPING). A LETHARGIC or LOE fish never holds station at the surface.
     */
    fun surfaceAllowed(o: Organism, w: WaterState): Boolean =
        o.heat.stage <= Stage.GASPING && (o2Sat(w) < asrThreshold(w.tempC) || w.doMgL <= DO_ASR_MGL + 1e-9)

    /** Behaviour text for the current stage, per animal type (08 §2 species adjustments, §7.2). */
    fun label(o: Organism): String {
        val st = o.heat.stage
        val k = o.brainKind
        return when {
            st == Stage.NORMAL -> ""
            isShrimp(k) -> when (st) { Stage.LETHARGIC -> "Motionless on the rock"; Stage.LOE -> "Lying on its side"; else -> st.label }
            isSnail(k) -> when (st) { Stage.AGITATED, Stage.GASPING -> "Climbing toward the surface"; Stage.LETHARGIC, Stage.LOE -> "Lost its grip"; else -> st.label }
            climbsOut(k) -> when (st) { Stage.AGITATED, Stage.GASPING -> "Climbing to the waterline"; Stage.LETHARGIC, Stage.LOE -> "Fallen off the rock"; else -> st.label }
            st == Stage.LOE && o.isFish -> "Loss of equilibrium, on its side"
            else -> st.label
        }
    }

    // ================================================================ corals (08 §5, §7.1 acute pathway)
    /** Acute necrosis threshold and lethal-time anchor at 36 °C (hours). */
    data class CoralProfile(val group: String, val tNecro: Double, val anchorH: Double, val slope: Double = 0.8)

    val CORAL_SPS = CoralProfile("Acropora (SPS)", 33.0, 3.0)
    val CORAL_POCILLOPORID = CoralProfile("Pocillopora/Stylophora/Montipora", 34.0, 6.0)
    val CORAL_LPS = CoralProfile("LPS", 34.5, 12.0)
    val CORAL_SOFT = CoralProfile("soft coral/zoanthid", 35.5, 24.0)
    const val CORAL_NIGHT_MULT = 1.7

    private val LPS_GENERA = listOf("euphyllia", "fimbriaphyllia", "favia", "dipsastraea", "caulastrea", "duncanopsammia", "acanthastrea",
        "blastomussa", "lobophyllia", "trachyphyllia", "goniopora", "alveopora", "turbinaria", "galaxea", "plerogyra", "cynarina", "scolymia",
        "micromussa", "echinophyllia", "favites", "platygyra", "fungia", "heliofungia")

    /** Coral heat profile by genus; null for macroalgae (no acute pathway). Organ pipe treated as LPS (est.). */
    fun coralProfile(c: Coral): CoralProfile? {
        if (c.kind == CoralKind.MACRO) return null
        val genus = c.species.id.lowercase().substringBefore('_')
        return when {
            genus.startsWith("acropora") -> CORAL_SPS
            listOf("pocillopora", "stylophora", "montipora", "seriatopora", "porites").any { genus.startsWith(it) } -> CORAL_POCILLOPORID
            c.kind == CoralKind.ORGAN_PIPE || LPS_GENERA.any { genus.startsWith(it) } -> CORAL_LPS
            else -> CORAL_SOFT
        }
    }

    fun coralTDeathMin(p: CoralProfile, t: Double): Double = max(T_DEATH_FLOOR_MIN, p.anchorH * 60 * 10.0.pow(-p.slope * (t - 36)))

    /**
     * Acute coral necrosis (08 §5, §7.2): D_coral integrates only above T_necro, ×1.7 in the dark. Visual
     * stages: mucus (D < 0.2), tissue strands (0.2–0.7), bare skeleton = clamp((D − 0.7)/0.3). Tissue lost to
     * bare skeleton never comes back; the colony dies when more than half is bare. Separate from DHW bleaching.
     */
    fun coralStep(c: Coral, t: Double, night: Boolean, dtMin: Double) {
        val p = coralProfile(c) ?: return
        if (t > p.tNecro) c.necrosis += dtMin / coralTDeathMin(p, t) * (if (night) CORAL_NIGHT_MULT else 1.0)
        else c.necrosis *= exp(-dtMin / DECAY_TAU)
        c.skeleton = max(c.skeleton, ((c.necrosis - 0.7) / 0.3).coerceIn(0.0, 1.0))
    }

    /** Above its necrosis threshold a coral sloughs instead of bleaching (08 §1.4, §5). */
    fun coralAcute(c: Coral, t: Double): Boolean = coralProfile(c)?.let { t > it.tNecro } ?: false

    /** Acute stage words for the coral inspector ("" when none). */
    fun coralStage(c: Coral): String = when {
        c.skeleton > 0.5 -> "Dead: bare skeleton"
        c.skeleton > 0 -> "Bare skeleton spreading"
        c.necrosis >= 0.2 -> "Tissue peeling in strands"
        c.necrosis > 0.02 -> "Heavy mucus"
        else -> ""
    }
}
