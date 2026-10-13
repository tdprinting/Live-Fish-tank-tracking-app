package com.reeflive.core.sim

import com.reeflive.core.geom.smoothstep
import kotlin.math.max
import kotlin.math.min

/**
 * Swimming / walking mode of a species (SIMULATION §15). The steering layer turns the animal's speed
 * through the water into fin and body kinematics with [MotionProfile.stroke]; the renderer only plays
 * back the phases and amplitudes it is given.
 */
enum class Gait {
    /** Pectoral rowing/flapping at cruise, body held stiff; the tail joins above the gait-transition speed (wrasse, tang, clown). */
    LABRIFORM,
    /** Body and caudal fin undulation; pectorals only scull while hovering (dottyback, cardinal). */
    BODY_CAUDAL,
    /** Whole-body wave (engineer goby, moray). */
    ANGUILLIFORM,
    /** No swim bladder: sits propped on the pectorals, hops with a tail flick and sinks when it stops (hawkfish, blennies). */
    PERCH_HOP,
    /** Legged walkers (shrimp, crabs, hermits). */
    WALK,
    /** Brittle star: arms row sideways in the plane of the disc. */
    ARM_ROW,
    /** Muscular foot or tube feet gliding (snails, asterina). */
    GLIDE,
    SESSILE,
}

/**
 * Per-species motion parameters (SIMULATION §15, the research/video files). Values carry the evidence tag
 * of the research files: [M] = a published source, [L] = estimate. Speeds are in body lengths per
 * second (BL/s) unless the name says cm/s.
 *
 * Fish kinematics follow three published rules:
 * - **Tail-beat frequency grows linearly with speed** (Bainbridge 1958, J Exp Biol 35:129: U = L(3f − 4)/4,
 *   so f ≈ 1.33 + 1.33 U/L) at a peak-to-peak tail amplitude ≈ 0.2 L. Written here as
 *   f = [tailHzMin] + ([strouhal] / [tailPP]) · U/L, i.e. a constant Strouhal number St = f·A/U
 *   (Taylor, Nudds & Thomas 2003, Nature 425:707: cruising swimmers sit at St 0.2–0.4).
 * - **Labriform swimmers row with the pectorals** at cruise; pectoral beat frequency rises linearly with
 *   speed and amplitude plateaus, until the pectoral–caudal gait transition speed U(p−c), where the tail
 *   starts to undulate (Drucker & Jensen 1996, J Exp Biol 199:2235).
 * - **Beat-and-glide** intermittent swimming saves energy at low speed (Videler & Weihs 1982, J Exp Biol 97:169).
 */
data class MotionProfile(
    val gait: Gait,
    /** Pectoral → caudal gait transition speed U(p−c) in BL/s (labriform); 0 = the tail always drives. */
    val gaitTransitionBL: Double = 0.0,
    /** Pectoral beat while hovering (sculling) and at U(p−c) (Hz). */
    val pecHzHover: Double = 2.0,
    val pecHzMax: Double = 4.0,
    /** Strouhal number of the caudal stroke (0.2–0.4) and tail peak-to-peak amplitude in BL (≈ 0.2). */
    val strouhal: Double = 0.3,
    val tailPP: Double = 0.2,
    /** Intercept of the frequency–speed line (Hz); Bainbridge's fit gives ≈ 1.3 Hz. */
    val tailHzMin: Double = 1.3,
    /** Half-amplitude (BL at the tail) of the body while rowing on the pectorals: a stiff labriform body ≈ 0.02. */
    val rowBodyAmp: Double = 0.02,
    /** Beat-and-glide: mean glide (s) after [beatsPerBout] tail beats at low speed; 0 = continuous beating. */
    val glideS: Double = 0.0,
    val beatsPerBout: Double = 3.0,
    /** Roll into turns per unit of lateral acceleration (1 = the original 10° at full turn). */
    val bankGain: Double = 1.0,
    /** No swim bladder: sinking speed (cm/s) while it is not actively swimming. */
    val sinkCmS: Double = 0.0,
    /** Escape C-start: largest turn away from the threat (deg). */
    val cStartDeg: Double = 120.0,
    /** Rolling "waddle" while rowing (clownfish): roll amplitude (rad) and frequency (Hz). */
    val wobbleRoll: Double = 0.0,
    val wobbleHz: Double = 1.3,
    /** Pitch bob while rowing (cleaner wrasse's bobbing travel): rad and Hz. */
    val bobPitch: Double = 0.0,
    val bobHz: Double = 2.0,
    /** Invertebrate walking speeds in cm/s from the video research (null = from the species sheet). */
    val walkCmS: Double? = null,
    val walkBurstCmS: Double? = null,
    /** Stop-go walking: seconds moving, seconds paused (0 = continuous). */
    val stopGoMoveS: Double = 0.0,
    val stopGoPauseS: Double = 0.0,
    /** Arm rowing (brittle star): stroke cycle at cruise (Hz) and arm sweep (rad). */
    val rowHz: Double = 0.5,
    val rowSweep: Double = 0.0,
) {
    /** Kinematics for a swimming speed through the water of [bl] body lengths per second. */
    fun stroke(bl: Double): Stroke {
        val u = max(bl, 0.0)
        val tailHz = tailHzMin + (strouhal / tailPP) * u
        // amplitude grows toward 0.2 L by ~5 Hz, then holds (Bainbridge 1958)
        val tailAmp = 0.5 * tailPP * (0.45 + 0.55 * min(1.0, tailHz / 5.0))
        return when (gait) {
            Gait.LABRIFORM -> {
                val upc = max(gaitTransitionBL, 0.1)
                val r = u / upc
                val share = smoothstep(0.75, 1.05, r)
                val pecHz = pecHzHover + (pecHzMax - pecHzHover) * min(r, 1.0)
                // amplitude rises over low speeds, plateaus, then the fins tuck as the tail takes over
                val pecAmp = (0.4 + 0.6 * smoothstep(0.0, 0.5, r)) * (1 - 0.75 * smoothstep(1.1, 1.8, r))
                // while rowing the body only makes small steering beats at half the fin rhythm
                Stroke(tailHz = lerpD(0.5 * pecHz, tailHz, share), amp = lerpD(rowBodyAmp, tailAmp, share), pecHz = pecHz, pecAmp = pecAmp, caudalShare = share)
            }
            Gait.BODY_CAUDAL, Gait.PERCH_HOP -> {
                val hover = 1 - smoothstep(0.1, 0.5, u)
                Stroke(tailHz = tailHz, amp = lerpD(tailAmp, 0.3 * tailAmp, hover), pecHz = pecHzHover, pecAmp = 0.15 + 0.35 * hover, caudalShare = 1.0)
            }
            Gait.ANGUILLIFORM -> Stroke(tailHz = tailHz, amp = 0.5 * tailPP, pecHz = pecHzHover, pecAmp = 0.15, caudalShare = 1.0)
            else -> Stroke(0.0, 0.0, 0.0, 0.0, 0.0)
        }
    }

    private fun lerpD(a: Double, b: Double, t: Double) = a + (b - a) * t
}

/** Fin and body kinematics for one speed: tail (body wave) Hz and half-amplitude (BL), pectoral Hz and stroke (0..1). */
data class Stroke(val tailHz: Double, val amp: Double, val pecHz: Double, val pecAmp: Double, val caudalShare: Double)

/** The per-species table (the research/video files; SIMULATION §15 lists the sources per row). */
object MotionProfiles {
    private val table: Map<BrainKind, MotionProfile> = mapOf(
        // pectoral-dominant with a rolling waddle and nose-down tilt; pecs 4–6 Hz [L]; caudal bursts 6–8 BL/s
        BrainKind.CLOWN to MotionProfile(Gait.LABRIFORM, gaitTransitionBL = 1.8, pecHzHover = 3.0, pecHzMax = 6.0, rowBodyAmp = 0.025,
            wobbleRoll = 0.09, wobbleHz = 1.3, cStartDeg = 110.0),
        // acanthuriform pectoral swimming, pecs 3–5 Hz, small tail; caudal only for bursts [L]
        BrainKind.TANG to MotionProfile(Gait.LABRIFORM, gaitTransitionBL = 2.2, pecHzHover = 2.5, pecHzMax = 5.0, rowBodyAmp = 0.015,
            strouhal = 0.28, tailPP = 0.18, bankGain = 1.3, cStartDeg = 130.0),
        // slow deep-bodied hoverer: fanning pectorals, gentle tail 1.5 Hz, glides between beats [M][L]
        BrainKind.CARDINAL to MotionProfile(Gait.BODY_CAUDAL, pecHzHover = 2.0, pecHzMax = 3.0, strouhal = 0.25, tailPP = 0.16, tailHzMin = 0.9,
            glideS = 0.8, beatsPerBout = 2.0, bankGain = 0.7, cStartDeg = 100.0),
        // labriform flapper, pecs ~5 Hz, rear body wiggles, bobbing travel [M][L]
        BrainKind.WRASSE to MotionProfile(Gait.LABRIFORM, gaitTransitionBL = 3.0, pecHzHover = 3.5, pecHzMax = 6.5, rowBodyAmp = 0.035,
            bobPitch = 0.05, bobHz = 2.0, bankGain = 1.2, cStartDeg = 140.0),
        BrainKind.EEL to MotionProfile(Gait.ANGUILLIFORM, strouhal = 0.35, tailPP = 0.12, tailHzMin = 0.5, bankGain = 0.3, cStartDeg = 60.0),
        // elongate juvenile, body wave ~3 Hz [L]
        BrainKind.ENGINEER_GOBY to MotionProfile(Gait.ANGUILLIFORM, strouhal = 0.35, tailPP = 0.14, tailHzMin = 1.2, bankGain = 0.4, cStartDeg = 90.0),
        // no swim bladder (Reef Builders): hops 2–4 BL with a tail flick, sinks when it stops [M]
        BrainKind.HAWK to MotionProfile(Gait.PERCH_HOP, pecHzHover = 2.0, strouhal = 0.32, tailPP = 0.22, tailHzMin = 1.5,
            glideS = 0.35, beatsPerBout = 2.0, sinkCmS = 2.5, bankGain = 0.6, cStartDeg = 90.0),
        // combtooth blennies outside the fangblenny clade lack a swim bladder (Wikipedia: Combtooth blenny) [M]
        BrainKind.LAWNMOWER_BLENNY to MotionProfile(Gait.PERCH_HOP, pecHzHover = 2.0, strouhal = 0.32, tailPP = 0.22, tailHzMin = 1.5,
            glideS = 0.3, beatsPerBout = 2.0, sinkCmS = 2.0, bankGain = 0.5, cStartDeg = 100.0),
        BrainKind.PEEKER_BLENNY to MotionProfile(Gait.PERCH_HOP, pecHzHover = 2.5, strouhal = 0.32, tailPP = 0.22, tailHzMin = 1.5,
            glideS = 0.3, beatsPerBout = 2.0, sinkCmS = 1.5, bankGain = 0.5, cStartDeg = 110.0),
        // sand-sifting goby: sits on the sand, hops 8–25 cm between sifting stations (research 16 [M]) in a short
        // beat-and-glide burst; gobies are benthic and negatively buoyant (no functional swim bladder [L]), so it
        // settles back onto the sand as soon as it stops beating
        BrainKind.DIAMOND_GOBY to MotionProfile(Gait.PERCH_HOP, pecHzHover = 2.0, strouhal = 0.3, tailPP = 0.2, tailHzMin = 1.5,
            glideS = 0.4, beatsPerBout = 2.0, sinkCmS = 2.0, bankGain = 0.5, cStartDeg = 110.0),
        // body-caudal darter, tail 3–4 Hz, burst-and-coast between rock cover [L]
        BrainKind.DOTTYBACK to MotionProfile(Gait.BODY_CAUDAL, pecHzHover = 2.5, strouhal = 0.3, tailPP = 0.2, tailHzMin = 1.5,
            glideS = 0.5, beatsPerBout = 3.0, bankGain = 1.1, cStartDeg = 140.0),
        BrainKind.GENERIC_FISH to MotionProfile(Gait.LABRIFORM, gaitTransitionBL = 2.0, pecHzHover = 2.5, pecHzMax = 5.0),
        // walking legs 1–3 cm/s with a pause every 1–2 s; tail-flip 25–40 cm/s [M][L]
        BrainKind.BANDED_SHRIMP to MotionProfile(Gait.WALK, walkCmS = 2.0, walkBurstCmS = 30.0, stopGoMoveS = 1.5, stopGoPauseS = 0.8),
        BrainKind.CLEANER_SHRIMP to MotionProfile(Gait.WALK, walkCmS = 1.5, walkBurstCmS = 25.0, stopGoMoveS = 1.5, stopGoPauseS = 0.8),
        // jerky shell-dragging walk, 1–3 cm/s, stops every 1–3 s [L]
        BrainKind.HERMIT to MotionProfile(Gait.WALK, walkCmS = 1.5, walkBurstCmS = 3.0, stopGoMoveS = 2.0, stopGoPauseS = 1.2),
        // very fast for a crab; the species sheet's speeds stand (bursts 4–8 BL/s) and the brain's scuttle supplies the stop-go [L]
        BrainKind.SALLY_CRAB to MotionProfile(Gait.WALK),
        // arms row sideways, stroke ~2 s per cycle (Astley 2012), 2–5 cm/s, food rush up to 8 cm/s [M][L]
        BrainKind.BRITTLE_STAR to MotionProfile(Gait.ARM_ROW, walkCmS = 3.0, walkBurstCmS = 7.0, rowHz = 0.5, rowSweep = 0.35),
        // foot gliding 0.5–1 cm/s; the food rush races at 3–5 cm/s [M][L]
        BrainKind.NASSARIUS to MotionProfile(Gait.GLIDE, walkCmS = 0.7, walkBurstCmS = 4.0),
        BrainKind.ASTREA to MotionProfile(Gait.GLIDE, walkCmS = 0.5, walkBurstCmS = 0.8),
        // tube-foot glide ≈ 0.2 cm/s (A. stellifera crawling paper) [M][L]
        BrainKind.ASTERINA to MotionProfile(Gait.GLIDE, walkCmS = 0.2, walkBurstCmS = 0.3),
        BrainKind.FEATHER_DUSTER to MotionProfile(Gait.SESSILE),
        BrainKind.GENERIC_INVERT to MotionProfile(Gait.WALK),
    )

    fun of(kind: BrainKind): MotionProfile = table[kind] ?: if (kind.isFish) table.getValue(BrainKind.GENERIC_FISH) else table.getValue(BrainKind.GENERIC_INVERT)
}
