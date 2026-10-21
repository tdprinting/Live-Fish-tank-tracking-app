package com.reeflive.core.sim

import com.reeflive.core.geom.FlowField
import com.reeflive.core.geom.Rng
import com.reeflive.core.geom.Vec3
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Fish waste (research 18_fish_waste.md). Every fish keeps a gut: a fifth of its nitrogen output ([WaterSim.FECAL_SHARE])
 * and part of each meal it swallows ([FEED_FECAL_SHARE] of the food's N, which then no longer leaches into the water)
 * pass through it over the gut transit time and come out as fecal pellets at the vent. A pellet sinks at 0.3–1.5 cm/s,
 * drifts with the flow, leaches NH4/PO4 as it goes ([LEACH_PER_H]) and then:
 * - is pulled into the HOB intake and trapped in the floss ([WaterSim.hobCapture]; it rots there unless the media is rinsed),
 * - is mouthed by a coprophagous fish (often spat out again) or eaten by the clean-up crew once it lies on the bottom,
 * - is lifted again by a strong current, or
 * - breaks down where it lies into mulm: the detritus pool ([WaterSim.wasteToDetritus]) and a dark patch in the sand
 *   ([SandBed.mulch]), so waste piles up in the dead spots the flow leaves.
 *
 * N is conserved: gut and pellet contents are held outside the water (exported) and every fate puts them back or exports
 * them for good. At high speeds (abstract mode) pellets are not simulated one by one: guts empty straight into the same
 * fates in fixed shares.
 */
class WasteSystem(private val rock: RockData, private val flow: FlowField, private val sand: SandBed, private val rng: Rng) {
    /** A fish's gut: fresh contents still being digested and contents ready to leave (ppm TAN-eq of the system). */
    private class Gut(var lastEatenG: Double) {
        var fresh = 0.0
        var ready = 0.0
        var nextTry = 0.0
    }
    private val guts = HashMap<Organism, Gut>()

    // pellets, structure of arrays (no per-pellet objects)
    private val px = DoubleArray(MAX); private val py = DoubleArray(MAX); private val pz = DoubleArray(MAX)
    private val vx = DoubleArray(MAX); private val vy = DoubleArray(MAX); private val vz = DoubleArray(MAX)
    private val load = DoubleArray(MAX)
    private val size = FloatArray(MAX)
    private val sink = FloatArray(MAX)
    private val tint = ByteArray(MAX)
    private val seed = FloatArray(MAX)
    private val settledAt = DoubleArray(MAX)
    private val onSand = BooleanArray(MAX)
    private val spun = FloatArray(MAX)
    var count = 0; private set

    /** Running totals (ppm TAN-eq) for tests and the HUD. */
    var madePpm = 0.0; private set
    var hobPpm = 0.0; private set
    var eatenPpm = 0.0; private set
    var mulmPpm = 0.0; private set
    var leachedPpm = 0.0; private set
    var pelletsMade = 0; private set
    var spatOut = 0; private set

    private var eatAcc = 0.0

    /** Waste held in guts and pellets right now (ppm TAN-eq). */
    fun heldPpm(): Double { var s = 0.0; for (g in guts.values) s += g.fresh + g.ready; for (i in 0 until count) s += load[i]; return s }

    fun settledCount(): Int { var n = 0; for (i in 0 until count) if (settledAt[i] >= 0) n++; return n }

    /**
     * Once per chemistry tick: share out the fecal N the fish made ([WaterSim.takeFecalPending]) by body mass, and move
     * the N of food swallowed since the last tick from the water's leaching pool into the gut.
     */
    fun collect(organisms: List<Organism>, water: WaterSim) {
        val made = water.takeFecalPending()
        var wsum = 0.0
        for (o in organisms) if (o.alive && o.isFish && !o.sleeping) wsum += o.massG.pow(0.8)
        for (o in organisms) {
            if (!o.isFish) continue
            if (!o.alive) { guts.remove(o)?.let { g -> water.wasteToDetritus(g.fresh + g.ready) }; continue }
            val g = guts.getOrPut(o) { Gut(o.eatenG) }
            if (wsum > 0 && !o.sleeping) g.fresh += made * o.massG.pow(0.8) / wsum
            val ate = o.eatenG - g.lastEatenG
            g.lastEatenG = o.eatenG
            if (ate > 0) g.fresh += water.divertFeedToGut(ate * WaterSim.FEED_TAN_PER_G * 1000 / water.s.volumeL * FEED_FECAL_SHARE)
        }
        if (wsum <= 0 && made > 0) water.wasteToDetritus(made)
        madePpm += made
        val it = guts.keys.iterator()
        while (it.hasNext()) { val o = it.next(); if (o !in organisms) { val g = guts[o]!!; water.wasteToDetritus(g.fresh + g.ready); it.remove() } }
    }

    /**
     * Digestion and defecation for [dt] sim seconds. With [individual] the pellets are released at each fish's vent and
     * simulated; otherwise (abstract mode, behaviour off) the ready contents go straight to their usual fates.
     */
    fun step(dt: Double, t: Double, tempC: Double, organisms: List<Organism>, water: WaterSim, individual: Boolean, hobOn: Boolean) {
        val tau = GUT_TRANSIT_H_25C * Q10.pow((25.0 - tempC) / 10.0) * 3600
        val k = 1 - exp(-dt / tau)
        for (o in organisms) {
            if (!o.isFish || !o.alive) continue
            val g = guts[o] ?: continue
            val move = g.fresh * k * (if (o.sleeping) 0.4 else 1.0)
            g.fresh -= move; g.ready += move
            if (o.sleeping || t < g.nextTry) continue
            val pellet = pelletLoad(o, water.s.volumeL)
            if (g.ready < pellet) continue
            g.nextTry = t + rng.range(20.0, 90.0)
            if (individual && count < MAX) spawn(o, g.ready)
            else abstractFate(g.ready, water, hobOn)
            g.ready = 0.0
        }
        if (individual) stepPellets(min(dt, 1.0), t, tempC, organisms, water, hobOn)
        else if (count > 0) { for (i in 0 until count) settle(i, water, mulch = true); count = 0 }
    }

    /** One pellet's worth of gut contents for this fish: about 1.5 h of its fecal output at 25 °C. */
    private fun pelletLoad(o: Organism, volumeL: Double): Double =
        WaterSim.K_EXCRETION * o.massG.pow(0.8) * WaterSim.FECAL_SHARE * PELLET_HOURS / volumeL

    private fun spawn(o: Organism, ppm: Double) {
        val i = count++
        // the vent sits about 55 % of the body length behind the snout, a little below the midline
        val vent = o.pos - o.heading * (0.12 * o.lengthCm) - Vec3(0.0, 0.08 * o.lengthCm, 0.0)
        val p = rock.clampToWater(vent, 0.3)
        px[i] = p.x; py[i] = p.y; pz[i] = p.z
        vx[i] = o.vel.x * 0.3; vy[i] = -0.3; vz[i] = o.vel.z * 0.3
        load[i] = ppm
        // string length ~ 6 % of the fish (3–6 mm for Tee's 5–10 cm fish), mucus-bound strings sink faster
        size[i] = (0.06 * o.lengthCm).coerceIn(0.15, 0.9).toFloat()
        sink[i] = (SINK_MIN + (SINK_MAX - SINK_MIN) * rng.nextDouble()).toFloat()
        tint[i] = when (o.brainKind) {
            BrainKind.TANG, BrainKind.LAWNMOWER_BLENNY -> 1     // algae diet: green-brown
            BrainKind.CARDINAL, BrainKind.WRASSE, BrainKind.FAIRY_WRASSE -> 2            // plankton and parasites: pale
            else -> 0
        }.toByte()
        seed[i] = rng.nextDouble().toFloat()
        settledAt[i] = -1.0
        onSand[i] = false
        spun[i] = 0f
        pelletsMade++
    }

    private fun abstractFate(ppm: Double, water: WaterSim, hobOn: Boolean) {
        val toHob = if (hobOn) ABSTRACT_HOB_SHARE else 0.0
        water.hobCapture(ppm * toHob); hobPpm += ppm * toHob
        val leach = ppm * ABSTRACT_LEACH_SHARE
        water.releaseWaste(leach); leachedPpm += leach
        val rest = ppm - ppm * toHob - leach
        water.wasteToDetritus(rest); mulmPpm += rest
    }

    /** [HOB_INTAKE] on this tank's glass (My tank builds of other sizes). */
    private val intakeAt: Vec3 by lazy { TankShape(rock.lengthCm, rock.heightCm, rock.widthCm).map(HOB_INTAKE) }

    private fun stepPellets(dt: Double, t: Double, tempC: Double, organisms: List<Organism>, water: WaterSim, hobOn: Boolean) {
        if (count == 0) return
        val intake = rock.anchors.pos("HOB_INTAKE", intakeAt)
        val leachK = 1 - exp(-dt * LEACH_PER_H / 3600)
        val breakdownS = BREAKDOWN_H_25C * Q10.pow((25.0 - tempC) / 10.0) * 3600
        var leached = 0.0
        var i = 0
        while (i < count) {
            val l = load[i] * leachK
            load[i] -= l; leached += l
            if (settledAt[i] >= 0) {
                // lying on the bottom: a strong current lifts it again, otherwise it falls apart into mulm
                if (t - settledAt[i] > breakdownS) { settle(i, water, mulch = true); remove(i); continue }
                val w = flow.velocityFast(Vec3(px[i], py[i] + 0.5, pz[i]))
                if (w.x * w.x + w.z * w.z > RESUSPEND_CMS * RESUSPEND_CMS && rng.chance(dt * 0.5)) {
                    settledAt[i] = -1.0; vy[i] = 1.0; vx[i] = w.x * 0.5; vz[i] = w.z * 0.5
                }
                i++; continue
            }
            val pos = Vec3(px[i], py[i], pz[i])
            val w = flow.velocityFast(pos)
            var tx = w.x * FLOW_COUPLING; var ty = w.y * FLOW_COUPLING - sink[i]; var tz = w.z * FLOW_COUPLING
            if (hobOn) {
                // the HOB intake strainer draws water (and waste) in from a few cm around it
                val dx = intake.x - px[i]; val dy = intake.y - py[i]; val dz = intake.z - pz[i]
                val d = sqrt(dx * dx + dy * dy + dz * dz)
                if (d < HOB_CAPTURE_CM) { water.hobCapture(load[i]); hobPpm += load[i]; remove(i); continue }
                if (d < HOB_REACH_CM) {
                    val s = HOB_SUCTION_CMS * (1 - d / HOB_REACH_CM) / d
                    tx += dx * s; ty += dy * s; tz += dz * s
                }
            }
            val kk = (2.0 * dt).coerceAtMost(1.0)
            vx[i] += (tx - vx[i]) * kk; vy[i] += (ty - vy[i]) * kk; vz[i] += (tz - vz[i]) * kk
            var p = rock.clampToWater(Vec3(px[i] + vx[i] * dt, py[i] + vy[i] * dt, pz[i] + vz[i] * dt), 0.2)
            spun[i] += (dt * (1.5 + 3 * seed[i])).toFloat()
            val bed = sand.surfaceY(p.x, p.z) + 0.15
            if (rock.sdf.dist(p) < 0.2) {
                p = rock.sdf.project(p, 0.2)
                // a pellet stays on rock only where the water is slow; otherwise it tumbles on
                if (w.x * w.x + w.z * w.z < RESUSPEND_CMS * RESUSPEND_CMS * 0.5) { settledAt[i] = t; onSand[i] = false; vx[i] = 0.0; vy[i] = 0.0; vz[i] = 0.0 }
            } else if (p.y <= bed) {
                p = p.withY(bed); settledAt[i] = t; onSand[i] = true; vx[i] = 0.0; vy[i] = 0.0; vz[i] = 0.0
            }
            px[i] = p.x; py[i] = p.y; pz[i] = p.z
            i++
        }
        if (leached > 0) { water.releaseWaste(leached); leachedPpm += leached }
        eatAcc += dt
        if (eatAcc >= EAT_CHECK_S) { eaters(eatAcc, organisms, water); eatAcc = 0.0 }
    }

    /**
     * Coprophagy (Robertson 1982): fish that meet a drifting pellet often mouth it and spit most of them out; the clean-up
     * crew (hermits, nassarius, brittle stars, crabs, shrimp) eats what lies on the bottom within reach.
     */
    private fun eaters(dt: Double, organisms: List<Organism>, water: WaterSim) {
        for (o in organisms) {
            if (!o.alive || o.sleeping) continue
            val fish = o.isFish
            val rate = if (fish) COPROPHAGY[o.brainKind] ?: continue else CUC[o.brainKind] ?: continue
            val reach = if (fish) 2.0 + 0.2 * o.lengthCm else 1.5 + 0.4 * o.lengthCm
            val r2 = reach * reach
            for (i in 0 until count) {
                if (fish == (settledAt[i] >= 0)) continue   // fish take drifting pellets, the crew settled ones
                val dx = px[i] - o.pos.x; val dy = py[i] - o.pos.y; val dz = pz[i] - o.pos.z
                if (dx * dx + dy * dy + dz * dz > r2 || !rng.chance(min(1.0, rate * dt))) continue
                o.mouth = 1.0
                if (fish && rng.chance(SPIT_SHARE)) {
                    // mouthed and rejected: blown back out, a little broken up
                    vx[i] = o.heading.x * 4; vy[i] = 1.0; vz[i] = o.heading.z * 4; size[i] *= 0.8f; spatOut++
                    break
                }
                val got = load[i]
                water.releaseWaste(got * EATEN_EXCRETED); eatenPpm += got
                remove(i)
                break
            }
        }
    }

    private fun settle(i: Int, water: WaterSim, mulch: Boolean) {
        val ppm = load[i]
        water.wasteToDetritus(ppm); mulmPpm += ppm
        if (mulch && onSand[i]) sand.mulch(px[i], pz[i], ppm / SandBed.DETRITUS_REF_PPM * rock.lengthCm * rock.widthCm, 1.6)
    }

    private fun remove(i: Int) {
        val j = --count
        if (i == j) return
        px[i] = px[j]; py[i] = py[j]; pz[i] = pz[j]; vx[i] = vx[j]; vy[i] = vy[j]; vz[i] = vz[j]
        load[i] = load[j]; size[i] = size[j]; sink[i] = sink[j]; tint[i] = tint[j]; seed[i] = seed[j]
        settledAt[i] = settledAt[j]; onSand[i] = onSand[j]; spun[i] = spun[j]
    }

    /** Gravel vac: every pellet lying on the sand goes out with the siphon. */
    fun siphon() {
        var i = 0
        // pellet N is held outside the water already: taking them out is the export
        while (i < count) { if (settledAt[i] >= 0 && onSand[i]) remove(i) else i++ }
    }

    /**
     * Pellets for the renderer, [STRIDE] floats each: x, y, z (cm), length (cm), tint (0 brown, 1 green-brown, 2 pale),
     * settled (1/0), seed 0..1, tumble angle (rad). Allocated per snapshot like the other snapshot arrays.
     */
    fun export(t: Double): FloatArray {
        val out = FloatArray(count * STRIDE)
        for (i in 0 until count) {
            val o = i * STRIDE
            out[o] = px[i].toFloat(); out[o + 1] = py[i].toFloat(); out[o + 2] = pz[i].toFloat()
            // a settled pellet slowly slumps and fades as it breaks down
            val age = if (settledAt[i] >= 0) ((t - settledAt[i]) / (BREAKDOWN_H_25C * 3600)).toFloat().coerceIn(0f, 1f) else 0f
            out[o + 3] = size[i] * (1f - 0.3f * age)
            out[o + 4] = tint[i].toFloat()
            out[o + 5] = if (settledAt[i] >= 0) 1f + age else 0f
            out[o + 6] = seed[i]
            out[o + 7] = spun[i]
        }
        return out
    }

    companion object {
        const val MAX = 240
        const val STRIDE = 8
        /** Tee's HOB intake strainer (FluidSim.Pump.defaults HOB intake). */
        val HOB_INTAKE = Vec3(62.0, 40.0, 2.5)

        // research 18_fish_waste.md
        const val FEED_FECAL_SHARE = 0.13     // feed N in fecal solids (13 %; Canary Is. sea bream budget)
        const val GUT_TRANSIT_H_25C = 5.0     // h at 25 °C: damselfish retain food 6.5 h, grazing surgeonfish defecate every 5-10 min
        const val Q10 = 2.2                    // gastric evacuation (from Wurtsbaugh 1993)
        const val PELLET_HOURS = 1.5          // fecal output per pellet (h of production)
        const val SINK_MIN = 0.2; const val SINK_MAX = 1.2   // cm/s: sea bream feces 0.48 mean, sea bass 0.70
        const val LEACH_PER_H = 0.12          // /h: most fecal N dissolves within hours (tuna feces > 90 % before the seabed; est.)
        const val BREAKDOWN_H_25C = 10.0      // h until a pellet lying still falls apart into mulm
        const val RESUSPEND_CMS = 8.0         // current that lifts a settled pellet again
        const val FLOW_COUPLING = 0.85
        const val HOB_REACH_CM = 9.0; const val HOB_CAPTURE_CM = 2.0; const val HOB_SUCTION_CMS = 6.0
        const val SPIT_SHARE = 0.6            // mouthed pellets rejected again
        const val EATEN_EXCRETED = 0.5        // share of an eaten pellet's N excreted again (the rest is growth)
        const val EAT_CHECK_S = 0.5
        const val ABSTRACT_HOB_SHARE = 0.25; const val ABSTRACT_LEACH_SHARE = 0.15

        /** Pellet encounters per second that end in a bite: coprophagous fish (Robertson 1982) and the clean-up crew. */
        val COPROPHAGY = mapOf(BrainKind.TANG to 0.6, BrainKind.CLOWN to 0.3, BrainKind.LAWNMOWER_BLENNY to 0.3,
            BrainKind.PEEKER_BLENNY to 0.4, BrainKind.DOTTYBACK to 0.2, BrainKind.CARDINAL to 0.2)
        val CUC = mapOf(BrainKind.HERMIT to 0.25, BrainKind.NASSARIUS to 0.4, BrainKind.CONCH to 0.3, BrainKind.BRITTLE_STAR to 0.3,
            BrainKind.SALLY_CRAB to 0.2, BrainKind.ARROW_CRAB to 0.2, BrainKind.EMERALD_CRAB to 0.15, BrainKind.CLEANER_SHRIMP to 0.15, BrainKind.BANDED_SHRIMP to 0.15, BrainKind.PEPPERMINT_SHRIMP to 0.1,
            BrainKind.FIRE_SHRIMP to 0.1, BrainKind.SEXY_SHRIMP to 0.03, BrainKind.PISTOL_SHRIMP to 0.15, BrainKind.SEA_CUCUMBER to 0.3)
    }
}
