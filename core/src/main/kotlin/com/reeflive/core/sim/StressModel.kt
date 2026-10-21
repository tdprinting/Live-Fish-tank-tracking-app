package com.reeflive.core.sim

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Two-time-constant stress model per parameter channel (research 06 §4.1, SIMULATION §9.10).
 *
 * Every organism carries, per channel, an acute `fast` state (cortisol-like), a chronic `slow` state
 * and an acclimated setpoint `A`. All are first-order filters integrated on the chemistry tick
 * (≈ 9 channels × 4 floats per animal; a few hundred multiply-adds per tick for the whole tank).
 *
 *     dev   = (value − A) × sens / w             (DO: distance below 1.3 × DO_crit(T))
 *     drive = smoothstep(0, 1, |dev|)^k
 *     fast  → drive with τ_rise while rising, → 0 with τ_fall otherwise
 *     slow  → max(slowGain × fast, chronic) with τ_slowRise / τ_slowFall
 *     A     → value with τ_accl, clamped to the species' acclimation range
 *     S_env = 1 − Π (1 − weight × max(fast, slow))
 *
 * Because drive is measured against the acclimated setpoint, a slow ramp moves the setpoint and
 * produces little acute stress while a step of the same size saturates it (ramp vs step without
 * special cases). Constants are in [Ch]; the per-species sensitivity table is [sens].
 */
object StressModel {
    /**
     * Channel constants. Times in minutes. [w] is the deviation (in the channel's unit) that saturates
     * the drive for a species with sensitivity 1. [weight] scales the channel's share of S_env.
     */
    enum class Ch(
        val key: String, val label: String, val w: Double, val k: Double,
        val tauRise: Double, val tauFall: Double, val tauSlowRise: Double, val tauSlowFall: Double, val tauAccl: Double,
        val weight: Double,
        /** Share of S_fast that becomes chronic load (S_slow's target is slowGain × S_fast). */
        val slowGain: Double = 1.0,
    ) {
        // 06 §4.1.1 table. τ_accl temp 3 d (not 7 d) so that the §5.6 #22 ramp/step pair holds; see SIMULATION §9.10.
        TEMP("temp", "temperature", 4.0, 2.0, 60.0, 720.0, 3 * 1440.0, 3 * 1440.0, 3 * 1440.0, 1.0),
        SAL("sal", "salinity", 0.006, 2.0, 120.0, 1440.0, 8 * 1440.0, 8 * 1440.0, 2 * 1440.0, 1.0),
        PH("ph", "pH", 0.5, 2.0, 240.0, 1440.0, 14 * 1440.0, 14 * 1440.0, 14 * 1440.0, 0.8),
        // NH3: S_slow is gill damage. 06 §4.1.6 has it linger 1–2 weeks after a spike while clamping
        // (0.8·S_slow) eases in 1–2 days, so an acute spike leaves only ~1/3 of S_fast as chronic load.
        NH3("nh3", "ammonia", 0.15, 1.0, 30.0, 2880.0, 12 * 60.0, 7 * 1440.0, 0.0, 1.0, slowGain = 0.35),
        NO2("no2", "nitrite", 2.0, 2.0, 120.0, 4320.0, 7 * 1440.0, 7 * 1440.0, 0.0, 0.8),
        DO("do", "low oxygen", 1.0, 1.0, 5.0, 60.0, 1440.0, 1440.0, 0.0, 1.0),
        LIGHT("light", "light", 0.6, 2.0, 1.0, 10.0, 2 * 1440.0, 2 * 1440.0, 2 * 1440.0, 0.3),
        FLOW("flow", "flow", 0.8, 2.0, 15.0, 60.0, 1440.0, 1440.0, 1440.0, 0.25),
        SOCIAL("social", "harassment", 1.0, 1.0, 60.0, 480.0, 7 * 1440.0, 7 * 1440.0, 0.0, 0.6);
    }
    val CHANNELS = Ch.entries
    const val N = 9

    /**
     * τ of the acute-input reservoir for chases/taps. With τ_rise 1 h this puts the S_social peak
     * ~1.4 h after a single event and leaves < 10 % of it at 24 h (S:Mathew23: elevated from 10 min,
     * maximal ~4 h, baseline by 24 h); repeated events an hour apart stack (06 §5.6 #24).
     */
    const val SOCIAL_INPUT_TAU = 120.0
    /** Chronic heat exposure filter (biphasic heat aggression, S:Warren16): τ 20 d. */
    const val HEAT_SLOW_TAU = 20 * 1440.0
    /** Low-pH boldness filter (S:Munday14). */
    const val LOWPH_TAU = 2 * 1440.0
    /** DO stress starts below this multiple of DO_crit(T). */
    const val DO_ONSET = 1.4

    /** Per-animal state; arrays indexed by [Ch.ordinal]. */
    class State {
        val fast = DoubleArray(N)
        val slow = DoubleArray(N)
        val setpoint = DoubleArray(N) { Double.NaN }
        var socialInput = 0.0
        var heatSlow = 0.0
        var lowPh = 0.0
        var sEnv = 0.0
        /** Latest DO_crit(T) (mg/L) for the inspector. */
        var doCrit = 3.0
        fun fast(c: Ch) = fast[c.ordinal]
        fun slow(c: Ch) = slow[c.ordinal]
        fun level(c: Ch) = max(fast[c.ordinal], slow[c.ordinal])
    }

    // -------------------------------------------------------------- species table (06 §4.1.2)
    /** Sensitivity multipliers: temp, sal, pH, NH3, NO2, DO, light, flow (social 1.0 for all). */
    private val SENS: Map<BrainKind, DoubleArray> = mapOf(
        BrainKind.CLOWN to doubleArrayOf(0.9, 0.9, 0.9, 1.0, 0.9, 0.9, 0.8, 0.8),
        BrainKind.TANG to doubleArrayOf(1.3, 1.1, 1.0, 1.2, 1.1, 1.3, 1.0, 1.1),
        BrainKind.CARDINAL to doubleArrayOf(1.3, 1.0, 1.0, 1.2, 1.1, 1.4, 1.5, 0.9),
        BrainKind.WRASSE to doubleArrayOf(1.2, 1.1, 1.1, 1.2, 1.0, 1.2, 0.9, 0.9),
        // fairy wrasse (est.): a plankton picker from the current, sensitive to low oxygen; likes flow
        BrainKind.FAIRY_WRASSE to doubleArrayOf(1.1, 1.0, 1.0, 1.2, 1.0, 1.3, 0.9, 0.8),
        // butterflyfish (est., research 60): clear outer-reef water; touchy about temperature, ammonia, nitrite and oxygen
        BrainKind.BUTTERFLY to doubleArrayOf(1.3, 1.1, 1.1, 1.3, 1.2, 1.3, 0.9, 0.9),
        BrainKind.ENGINEER_GOBY to doubleArrayOf(0.8, 0.9, 0.9, 1.0, 1.0, 1.1, 0.7, 0.8),
        BrainKind.EEL to doubleArrayOf(0.8, 0.9, 0.9, 0.8, 1.0, 1.0, 0.7, 0.8),
        BrainKind.HAWK to doubleArrayOf(1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 0.9, 0.9),
        BrainKind.LAWNMOWER_BLENNY to doubleArrayOf(0.9, 0.9, 0.9, 1.0, 1.0, 1.1, 0.8, 0.9),
        BrainKind.PEEKER_BLENNY to doubleArrayOf(0.9, 0.9, 0.9, 1.0, 1.0, 1.1, 0.8, 0.9),
        BrainKind.DOTTYBACK to doubleArrayOf(0.9, 0.9, 0.9, 1.0, 1.0, 1.0, 0.8, 0.9),
        BrainKind.CLEANER_SHRIMP to doubleArrayOf(1.3, 1.6, 1.3, 1.3, 1.3, 1.4, 0.6, 1.0),
        BrainKind.BANDED_SHRIMP to doubleArrayOf(1.3, 1.6, 1.3, 1.3, 1.3, 1.4, 0.6, 1.0),
        BrainKind.PEPPERMINT_SHRIMP to doubleArrayOf(1.3, 1.6, 1.3, 1.3, 1.3, 1.4, 0.6, 1.0),
        BrainKind.FIRE_SHRIMP to doubleArrayOf(1.3, 1.6, 1.3, 1.3, 1.3, 1.4, 0.6, 1.0),
        BrainKind.SEXY_SHRIMP to doubleArrayOf(1.3, 1.6, 1.3, 1.3, 1.3, 1.4, 0.6, 1.0),
        // research 44 §2: sensitive to quick temperature and chemistry changes and to low oxygen
        BrainKind.HARLEQUIN_SHRIMP to doubleArrayOf(1.5, 1.7, 1.4, 1.4, 1.4, 1.5, 0.6, 1.0),
        BrainKind.PISTOL_SHRIMP to doubleArrayOf(1.2, 1.5, 1.2, 1.2, 1.2, 1.3, 0.6, 1.0),
        BrainKind.HERMIT to doubleArrayOf(1.1, 1.4, 1.2, 1.2, 1.2, 1.3, 0.6, 0.8),
        BrainKind.SALLY_CRAB to doubleArrayOf(1.1, 1.4, 1.2, 1.2, 1.2, 1.3, 0.6, 0.8),
        BrainKind.ARROW_CRAB to doubleArrayOf(1.1, 1.4, 1.2, 1.2, 1.2, 1.3, 0.6, 0.8),
        BrainKind.EMERALD_CRAB to doubleArrayOf(1.0, 1.3, 1.2, 1.1, 1.1, 1.2, 0.6, 0.8),
        BrainKind.PORCELAIN_CRAB to doubleArrayOf(1.0, 1.3, 1.2, 1.1, 1.1, 1.2, 0.6, 0.8),
        BrainKind.NASSARIUS to doubleArrayOf(1.1, 1.2, 1.4, 1.2, 1.1, 1.2, 0.6, 0.7),
        BrainKind.ASTREA to doubleArrayOf(1.1, 1.2, 1.4, 1.2, 1.1, 1.2, 0.6, 0.7),
        BrainKind.TROCHUS to doubleArrayOf(1.1, 1.2, 1.4, 1.2, 1.1, 1.2, 0.6, 0.7),
        BrainKind.CONCH to doubleArrayOf(1.1, 1.2, 1.4, 1.2, 1.1, 1.2, 0.6, 0.7),
        // echinoderms: poor osmoregulators, salinity swings hit hard (research 43 §2)
        BrainKind.URCHIN to doubleArrayOf(1.1, 1.6, 1.4, 1.2, 1.1, 1.2, 0.6, 0.7),
        BrainKind.ASTERINA to doubleArrayOf(1.1, 1.2, 1.4, 1.2, 1.1, 1.2, 0.6, 0.7),
        BrainKind.BRITTLE_STAR to doubleArrayOf(1.1, 1.4, 1.2, 1.2, 1.1, 1.3, 0.5, 0.8),
        BrainKind.FEATHER_DUSTER to doubleArrayOf(1.0, 1.2, 0.9, 0.7, 0.7, 0.8, 1.0, 1.2),
        // a filter-feeding bivalve: touchy about salinity, pH and oxygen like the tube worm, more about temperature (est.)
        BrainKind.FILE_CLAM to doubleArrayOf(1.2, 1.3, 1.0, 0.8, 0.8, 0.9, 1.1, 1.2),
        // research 45 §1: wants stable water; a reef-flat animal, more tolerant of heat and light than the file clam (est.)
        BrainKind.GIANT_CLAM to doubleArrayOf(1.0, 1.3, 1.0, 0.9, 0.9, 1.0, 1.0, 1.1),
        // an echinoderm: salinity- and copper-sensitive, wants stable water (research 45 §4)
        BrainKind.SEA_CUCUMBER to doubleArrayOf(1.1, 1.6, 1.3, 1.2, 1.1, 1.2, 0.6, 0.7),
    )
    private val SENS_DEFAULT_FISH = doubleArrayOf(1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 0.9, 0.9)
    private val SENS_DEFAULT_INVERT = doubleArrayOf(1.1, 1.3, 1.2, 1.2, 1.1, 1.3, 0.6, 0.8)

    fun sens(kind: BrainKind, isFish: Boolean, ch: Ch): Double =
        if (ch == Ch.SOCIAL) 1.0 else (SENS[kind] ?: if (isFish) SENS_DEFAULT_FISH else SENS_DEFAULT_INVERT)[ch.ordinal]

    /** DO_crit(T) slope per °C (S:Nilsson10: +71 % cardinalfish, +23 % damselfish per +3 °C). */
    fun doCritSlope(kind: BrainKind): Double = when (kind) {
        BrainKind.CARDINAL, BrainKind.TANG -> 0.24
        else -> 0.08
    }

    /** Critical dissolved O2 (mg/L) at temperature [t]: 3.0 fish / 4.0 crustaceans at 29 °C (06 §4.1.4). */
    fun doCrit(kind: BrainKind, isFish: Boolean, t: Double): Double {
        val base = if (isFish) 3.0 else 4.0
        // only warming raises it: below 29 °C the critical level stays at its 29 °C value (no extrapolation down)
        return base * max(1.0, 1 + doCritSlope(kind) * (t - 29))
    }

    /**
     * Thermal acclimation ceiling (°C): the setpoint cannot follow beyond it and the excess drives the
     * chronic channel. Cardinalfish-type species fail to acclimate to +3 °C (S:Dabruzzi21); the tang
     * stops feeding above 28 (02 §2).
     */
    fun thermalCeiling(kind: BrainKind, isFish: Boolean): Double = when (kind) {
        BrainKind.CARDINAL -> 28.0
        BrainKind.TANG -> 28.5
        BrainKind.WRASSE -> 29.5
        // research 60: a 20-25 °C fish stops coping well above 27
        BrainKind.BUTTERFLY -> 27.5
        else -> if (isFish) 30.5 else 29.5
    }

    fun smoothstep(x: Double): Double { val t = x.coerceIn(0.0, 1.0); return t * t * (3 - 2 * t) }
    private fun f(dtMin: Double, tau: Double) = if (tau <= 0) 1.0 else 1 - exp(-dtMin / tau)

    /** Inputs that are not water chemistry. */
    class Env(val light: Double = 0.5, val flowStrength: Double = 1.0)

    /** Chases received / taps: adds to the acute social reservoir (0..~1 per event). */
    fun addSocial(o: Organism, amount: Double) { o.stressState.socialInput = min(2.0, o.stressState.socialInput + amount) }

    /** One chemistry tick for [o] (dtMin minutes). Returns S_env. */
    fun update(o: Organism, w: WaterState, env: Env, dtMin: Double): Double {
        val s = o.stressState
        val kind = o.brainKind
        val fish = o.isFish
        // social reservoir decays; the fast channel follows it (two-compartment cortisol envelope)
        val socialDrive = min(1.0, s.socialInput)
        s.socialInput *= exp(-dtMin / SOCIAL_INPUT_TAU)
        s.doCrit = doCrit(kind, fish, w.tempC)
        // molting shrimp take ×2 salinity/NH3 drive (06 §4.1.4 #6)
        val soft = if (o.molting) 2.0 else 1.0
        var prod = 1.0
        for (c in CHANNELS) {
            val i = c.ordinal
            val sn = sens(kind, fish, c)
            val value = when (c) {
                Ch.TEMP -> w.tempC
                Ch.SAL -> w.sg
                Ch.PH -> w.ph
                Ch.NH3 -> w.nh3Free
                Ch.NO2 -> w.no2
                Ch.DO -> w.doMgL
                Ch.LIGHT -> env.light
                Ch.FLOW -> env.flowStrength
                Ch.SOCIAL -> socialDrive
            }
            if (s.setpoint[i].isNaN()) s.setpoint[i] = when (c) { Ch.FLOW -> 1.0; Ch.LIGHT -> max(1.0, value); else -> value }
            var chronic = 0.0
            val dev = when (c) {
                Ch.NH3 -> value * sn * soft / c.w
                Ch.NO2 -> value * sn / c.w
                // onset at DO_ONSET × DO_crit, saturating 0.6 × DO_crit lower (tuned to 06 §5.6 #18 and #23 together)
                Ch.DO -> max(0.0, (DO_ONSET * s.doCrit - value) / (0.6 * s.doCrit)) * sn
                Ch.SOCIAL -> value
                // only brighter-than-acclimated light (or flow loss) is a stressor
                Ch.LIGHT -> max(0.0, value - s.setpoint[i]) * sn / c.w
                Ch.FLOW -> max(0.0, s.setpoint[i] - value) * sn / c.w
                Ch.SAL -> (value - s.setpoint[i]) * sn * soft / c.w
                else -> (value - s.setpoint[i]) * sn / c.w
            }
            if (c == Ch.TEMP) {
                val ceil = thermalCeiling(kind, fish)
                chronic = smoothstep((value - ceil) / 2.0)
            }
            // the social channel is the cortisol envelope itself: linear in the reservoir so small
            // events (a glass tap) still show and repeated ones add up (06 §4.1.1, S:Mathew23)
            val drive = if (c == Ch.SOCIAL) dev.coerceIn(0.0, 1.0) else smoothstep(abs(dev)).pow(c.k)
            val fast = s.fast[i]
            s.fast[i] = if (drive > fast) fast + (drive - fast) * f(dtMin, c.tauRise) else fast + (drive - fast) * f(dtMin, c.tauFall)
            val target = max(s.fast[i] * c.slowGain, chronic)
            val sl = s.slow[i]
            s.slow[i] = sl + (target - sl) * f(dtMin, if (target > sl) c.tauSlowRise else c.tauSlowFall)
            // setpoint
            // light: darkness is not "dimmer light" (only the lit part of the day re-acclimates)
            if (c.tauAccl > 0 && !(c == Ch.LIGHT && value < 0.3)) {
                // light: the setpoint tracks the daily peak (brighter acclimates in ~2 d, dimmer is forgotten over 30 d)
                val tau = if (c == Ch.LIGHT && value < s.setpoint[i]) 30 * 1440.0 else c.tauAccl
                var a = s.setpoint[i] + (value - s.setpoint[i]) * f(dtMin, tau)
                if (c == Ch.TEMP) a = a.coerceAtMost(thermalCeiling(kind, fish))
                if (c == Ch.LIGHT) a = max(a, 0.3)
                s.setpoint[i] = a
            }
            prod *= 1 - c.weight * max(s.fast[i], s.slow[i]).coerceIn(0.0, 1.0)
        }
        // chronic heat exposure above 28 °C and sustained low pH (behavioural modifiers)
        val heat = ((w.tempC - 28.0) / 3.0).coerceIn(0.0, 1.0)
        s.heatSlow += (heat - s.heatSlow) * f(dtMin, HEAT_SLOW_TAU)
        val low = ((7.8 - w.ph) / 0.2).coerceIn(0.0, 1.0)
        s.lowPh += (low - s.lowPh) * f(dtMin, LOWPH_TAU)
        s.sEnv = 1 - prod
        return s.sEnv
    }

    /** Re-acclimate every setpoint to the current water (sandbox "acclimate all to current"). */
    fun acclimate(o: Organism, w: WaterState, light: Double, flow: Double, param: String? = null, value: Double? = null) {
        val s = o.stressState
        for (c in CHANNELS) {
            if (param != null && !param.equals(c.key, true)) continue
            if (c.tauAccl <= 0) continue
            s.setpoint[c.ordinal] = value ?: when (c) {
                Ch.TEMP -> w.tempC; Ch.SAL -> w.sg; Ch.PH -> w.ph; Ch.LIGHT -> max(0.3, light); Ch.FLOW -> flow
                else -> s.setpoint[c.ordinal]
            }
        }
    }

    // -------------------------------------------------------------- behaviour hooks (06 §4, §4.1.3–4.1.5)
    /** S_env without the temperature channel (heat has its own biphasic aggression rule). */
    fun sEnvNoHeat(o: Organism): Double {
        val s = o.stressState
        var prod = 1.0
        for (c in CHANNELS) if (c != Ch.TEMP && c != Ch.SOCIAL) prod *= 1 - c.weight * s.level(c).coerceIn(0.0, 1.0)
        return 1 - prod
    }

    /** Appetite (06 §4.1.3): × (1 − 0.7·NH3)(1 − 0.5·heat)(1 − 0.5·DO). */
    fun appetite(o: Organism, tempC: Double): Double {
        val s = o.stressState
        val heat = if (tempC > s.setpoint[Ch.TEMP.ordinal].let { if (it.isNaN()) tempC else it } || tempC > 28) s.fast(Ch.TEMP) else 0.0
        val tangStop = if (o.brainKind == BrainKind.TANG && tempC > 28.5) 0.5 else 1.0
        return (1 - 0.7 * s.fast(Ch.NH3)) * (1 - 0.5 * heat) * (1 - 0.5 * s.fast(Ch.DO)) * (1 - 0.4 * s.fast(Ch.SAL)) * tangStop
    }

    /**
     * Initiated-aggression multiplier (06 §4 general rule, §4.1.4 #4):
     * - lethargy: any non-heat S_env > 0.5 halves it; ammonia ×(1 − 0.4·NH3).
     * - biphasic heat: short heat raises it, chronic heat lowers it (S:Warren16).
     * - hunger and the cleaner's tactile damping are applied by the caller.
     */
    fun aggressionMult(o: Organism, tempC: Double): Double {
        val s = o.stressState
        val acute = ((tempC - 27.5) / 3.5).coerceIn(0.0, 1.0) * (1 - s.heatSlow)
        val heat = (1 + 0.45 * acute) * (1 - 0.5 * s.heatSlow)
        val lethargy = if (sEnvNoHeat(o) > 0.5) 0.5 else 1.0
        return heat * lethargy * (1 - 0.4 * s.fast(Ch.NH3)) * (1 - 0.5 * s.fast(Ch.DO))
    }

    /** Received-stress gain: a stressed animal is harmed more by harassment (×1.5 at S_env > 0.5). */
    fun receivedGain(o: Organism): Double = if (o.stressState.sEnv > 0.5) 1.5 else 1.0

    /** Low-pH boldness (S:Munday14): skittishness ×0.6 and flee radius ×0.7 when pH < 7.8 is sustained. */
    fun skittishMult(o: Organism): Double = 1 - 0.4 * o.stressState.lowPh
    fun fleeRadiusMult(o: Organism): Double = 1 - 0.3 * o.stressState.lowPh

    /** Hiding drive: home radius × (1 − 0.6·S_env); Hide += 0.5·S_env. */
    fun homeRadiusMult(o: Organism): Double = 1 - 0.6 * o.stressState.sEnv

    /** Breathing rate (Hz): Q10 2.5 around 26 °C × (1 + 1.5·DO + NH3 + 0.5·ich). */
    fun gillHz(o: Organism, tempC: Double): Double {
        val s = o.stressState
        return 2.5.pow((tempC - 26) / 10) * (1 + 1.5 * s.fast(Ch.DO) + 1.0 * s.fast(Ch.NH3) + 0.5 * o.ich + 0.3 * s.fast(Ch.NO2))
    }

    /** Column swimmers surface to gasp; benthic fish hide instead (S:Yu23). */
    fun isColumnFish(kind: BrainKind, night: Boolean) = when (kind) {
        BrainKind.CLOWN, BrainKind.TANG, BrainKind.WRASSE, BrainKind.FAIRY_WRASSE, BrainKind.BUTTERFLY -> true
        BrainKind.CARDINAL -> true
        else -> false
    }

    /** Fin clamping (06 §4.1.3): NH3 (chronic), cold, ich, cardinal in bright exposed light. */
    fun finClamp(o: Organism, tempC: Double, brightExposed: Boolean): Double {
        val s = o.stressState
        val cold = if (tempC < s.setpoint[Ch.TEMP.ordinal].let { if (it.isNaN()) tempC else it }) s.fast(Ch.TEMP) else 0.0
        val card = if (o.brainKind == BrainKind.CARDINAL && brightExposed) 0.6 else 0.0
        return (0.8 * max(s.slow(Ch.NH3), 0.5 * s.fast(Ch.NH3)) + 0.6 * cold + 0.5 * o.ich + card + 0.4 * s.fast(Ch.NO2)).coerceIn(0.0, 1.0)
    }

    /** The channel contributing most right now (UI "why"); null when calm. */
    fun mainChannel(o: Organism): Ch? {
        val s = o.stressState
        val c = CHANNELS.maxByOrNull { it.weight * s.level(it) } ?: return null
        return if (c.weight * s.level(c) > 0.15) c else null
    }
}
