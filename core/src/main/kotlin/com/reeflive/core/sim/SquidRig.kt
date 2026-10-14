package com.reeflive.core.sim

import com.reeflive.core.geom.Vec3
import com.reeflive.core.geom.smoothstep
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Bobtail squid render signals (research cephalopod_squid, SIMULATION §20). The brain sets the targets (arm posture, chromatophore
 * state, light-organ glow, ink); [step] eases them, runs the fin and mantle clocks from the swimming speed and packs
 * [out] for the renderer (`OrganismPose.limbs`, shader `uLimb[0..4]`, motion mode 4):
 *
 * - `[0]` fins: x fin-wave phase (cycles), y fin stroke 0..1, z mantle contraction 0..1 (jet / ventilation), w fins
 *   wrapped against the mantle 0..1 (jetting)
 * - `[1]` arms: x arm-wave phase (cycles), y arm spread 0..1 (0 = bundled into a cone, 1 = flared), z first arm pair
 *   raised 0..1 (alarm / hunting posture), w tentacle extension 0..1 (the strike)
 * - `[2]` skin: x passing-cloud phase (cycles), y chromatophore expansion 0..1 (0 = pale, 1 = dark), z passing-cloud
 *   strength 0..1, w light-organ glow 0..1 (counter-illumination)
 * - `[3]` ink: x 0, y/z/w ink cloud centre (world cm)
 * - `[4]` ink: x 0, y cloud age (s; ≥ [INK_LIFE_S] = none), z radius (cm), w density 0..1
 *
 * Component x of every entry is a phase or 0 (FishRig interpolates x across the 1 → 0 wrap).
 */
class SquidRig : LimbRig(5) {
    // ---- targets set by the brain ----
    var armSpreadTarget = 0.3
    var armRaiseTarget = 0.0
    var tentacleTarget = 0.0
    var darkTarget = 0.35
    var cloudTarget = 0.0
    var glowTarget = 0.0
    /** Jetting: mantle contracts in pulses, fins wrap, arms bundle. Set each tick by the brain (decays otherwise). */
    var jet = 0.0

    // ---- eased state ----
    var finPhase = 0.0; private set
    var finAmp = 0.3; private set
    var mantle = 0.0; private set
    var finWrap = 0.0; private set
    var armPhase = 0.0; private set
    var armSpread = 0.3; private set
    var armRaise = 0.0; private set
    var tentacle = 0.0; private set
    var cloudPhase = 0.0; private set
    var dark = 0.35; private set
    var cloud = 0.0; private set
    var glow = 0.0; private set
    private var ventPhase = 0.0

    // ---- ink cloud ----
    var inkPos: Vec3 = Vec3.ZERO
    var inkAge = INK_LIFE_S
    var inkRadius = 0.0
    var inkDensity = 0.0
    val inking: Boolean get() = inkAge < INK_LIFE_S

    /** Releases a puff of ink at [at]: a dark pseudomorph about one mantle across that spreads and fades. */
    fun ink(at: Vec3, mantleCm: Double) {
        inkPos = at; inkAge = 0.0; inkRadius = max(0.8, 0.8 * mantleCm); inkDensity = 1.0
    }

    /** Moves the ink with the water (cm/s) and grows it; called by the brain every tick. */
    fun driftInk(flow: Vec3, dt: Double) {
        if (!inking) return
        inkAge += dt
        inkPos += flow * (0.8 * dt)
        inkRadius += dt * (0.5 + 1.5 * exp(-inkAge / 2.0))
        inkDensity = max(0.0, 1 - inkAge / INK_LIFE_S)
    }

    override fun step(o: Organism, dt: Double, speed: Double) {
        val cruise = max(o.cruiseSpeed, 0.3)
        val r = speed / cruise
        val mp = o.motion
        val j = jet.coerceIn(0.0, 1.0)
        // fins: a wave runs along each fin; beat rises with speed, fins wrap round the mantle in a jet
        val finHz = mp.pecHzHover + (mp.pecHzMax - mp.pecHzHover) * min(r, 1.5) / 1.5
        finPhase = LimbRig.frac(finPhase + finHz * dt * (1 - 0.7 * j))
        val finTarget = (0.35 + 0.65 * smoothstep(0.0, 1.2, r)) * (1 - 0.8 * j) * (if (o.sleeping) 0.2 else 1.0)
        finAmp += (finTarget - finAmp) * min(1.0, dt * 4)
        finWrap += (j - finWrap) * min(1.0, dt * 10)
        // mantle: slow ventilation, sharp contractions while jetting (~3 Hz pulses)
        ventPhase = LimbRig.frac(ventPhase + dt * (if (j > 0.05) JET_PULSE_HZ else VENT_HZ * (if (o.sleeping) 0.6 else 1.0)))
        val vent = 0.08 + 0.06 * sin(2 * Math.PI * ventPhase)
        val pulse = max(0.0, sin(2 * Math.PI * ventPhase))
        mantle = vent * (1 - j) + pulse * j
        // arms: bundle into a cone while jetting or cruising fast, flare when hovering or hunting
        val spreadT = if (j > 0.05) 0.0 else armSpreadTarget * (1 - 0.6 * smoothstep(1.0, 3.0, r))
        armSpread += (spreadT - armSpread) * min(1.0, dt * 5)
        armRaise += (armRaiseTarget - armRaise) * min(1.0, dt * 4)
        // tentacles shoot out in a fraction of a second and are drawn back more slowly
        val k = if (tentacleTarget > tentacle) TENTACLE_OUT_RATE else TENTACLE_IN_RATE
        tentacle += (tentacleTarget - tentacle) * min(1.0, dt * k)
        armPhase = LimbRig.frac(armPhase + dt * (0.25 + 0.4 * smoothstep(0.0, 1.0, r)))
        // chromatophores open and close within a fraction of a second (neural, not hormonal)
        dark += (darkTarget - dark) * min(1.0, dt * CHROMA_RATE)
        cloud += (cloudTarget - cloud) * min(1.0, dt * 3)
        cloudPhase = LimbRig.frac(cloudPhase + dt * CLOUD_HZ)
        glow += (glowTarget - glow) * min(1.0, dt * 0.5)
        jet = max(0.0, jet - dt * 2.0)
        put(0, finPhase, finAmp, mantle, finWrap)
        put(1, armPhase, armSpread, armRaise, tentacle)
        put(2, cloudPhase, dark, cloud, glow)
        put(3, 0.0, inkPos.x, inkPos.y, inkPos.z)
        put(4, 0.0, min(inkAge, INK_LIFE_S), inkRadius, if (inking) inkDensity else 0.0)
    }

    companion object {
        /** Ink pseudomorph stays visible this long before the skimmer and the flow have taken it (s; design estimate). */
        const val INK_LIFE_S = 25.0
        /** Ventilation (resting mantle breathing) and jet pulse rates (Hz; design estimates). */
        const val VENT_HZ = 0.8
        const val JET_PULSE_HZ = 3.0
        const val TENTACLE_OUT_RATE = 25.0
        const val TENTACLE_IN_RATE = 3.0
        /** Chromatophore expansion rate (1/s): cephalopod chromatophores change within ~0.3–1 s (Hanlon & Messenger 2018). */
        const val CHROMA_RATE = 4.0
        /** "Passing cloud" dark bands travelling over the mantle (Hz; design estimate). */
        const val CLOUD_HZ = 1.2
    }
}
