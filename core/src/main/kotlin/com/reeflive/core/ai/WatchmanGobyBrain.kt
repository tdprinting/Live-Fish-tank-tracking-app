package com.reeflive.core.ai

import com.reeflive.core.geom.Vec3
import com.reeflive.core.sim.BrainKind
import com.reeflive.core.sim.Ev
import com.reeflive.core.sim.Evidence
import com.reeflive.core.sim.FoodParticle
import com.reeflive.core.sim.Organism
import com.reeflive.core.sim.SandBed
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Yellow watchman goby, *Cryptocentrus cinctus* (docs/research/50_popular_fish_batch1.md §3). A shrimp goby: it lives
 * in a burrow in the sand under the edge of a rock and spends the day lying at the mouth propped up on its fused
 * pelvic fins, head raised, eyes swivelling, watching. Food that drifts or lands near the burrow gets a quick hop out
 * and a snap. At danger it flicks its tail (the warning a partner pistol shrimp feels through its antenna, Reef Builders
 * 2015) and dives head first into the burrow, where it also spends the night. Without a shrimp it keeps the mouth open
 * itself, spitting out a mouthful of sand now and then.
 *
 * Two watchmen that arrive together may be a mated pair and share the burrow; any other shrimp goby near the burrow is
 * chased off (pair row watchman_watchman).
 */
class WatchmanGobyBrain(c: Ctx) : SpeciesBrain(c) {
    /** Burrow mouth on the sand at the foot of a rock, and the direction into it. */
    var burrow: Vec3; private set
    var inDir: Vec3; private set
    var mate: Organism? = null; private set
    /** Sim time of its last tail-flick warning (read by a partner shrimp and by tests). */
    var alarmAt = -1e9; private set
    var alarms = 0; private set
    var snaps = 0; private set
    var spits = 0; private set
    private var nextSpit = 0.0

    init {
        val partner = c.sim.organisms.firstOrNull { it !== o && it.alive && it.brainKind == BrainKind.WATCHMAN_GOBY &&
            abs(it.arrivalTime - o.arrivalTime) < 3600.0 && (c.sim.agentOf(it)?.brain as? WatchmanGobyBrain)?.mate == null }
        val pb = partner?.let { c.sim.agentOf(it)?.brain as? WatchmanGobyBrain }
        if (pb != null && o.rng.chance(PAIR_CHANCE)) {
            burrow = pb.burrow; inDir = pb.inDir
            mate = partner; pb.mate = o
            o.mateId = partner.id; partner.mateId = o.id
        } else {
            val (p, d) = findBurrow(c)
            burrow = p; inDir = d
        }
        nextSpit = c.t + c.rng.range(600.0, 3600.0)
        o.spline.glassGuard = true
        install(Lookout(), InBurrow(), SnapFeed(), BurrowDive(), SpitSand(), SurfaceGasp(this), Flash(this))
    }

    private val sand: SandBed get() = c.sim.sand
    private val halfH: Double get() = 0.5 * o.species.depthRatio * o.lengthCm
    /** Head (the organism's position) this far out from the mouth at the watch: the body lies along the apron, tail at the mouth. */
    private val lookoutOut: Double get() = LOOKOUT_OUT_CM + 0.9 * o.lengthCm

    /** Kept a few mm off the rock face at the rock foot (the burrow itself is in the sand). */
    private fun offRock(p: Vec3): Vec3 = if (c.rock.sdf.dist(p) < ROCK_GAP_CM) c.rock.sdf.project(p, ROCK_GAP_CM) else p

    /** Body centre height over the bed: clear of it even with the head propped up (the tail end drops). */
    private val lift: Double get() = max(halfH, 0.5 * o.lengthCm * sin(PROP_PITCH)) + 0.15

    /** Bed height under [x],[z]; a pit dug by the burrow or a sifter never pulls it under the nominal sand line. */
    private fun bedY(x: Double, z: Double) = max(sand.surfaceY(x, z), c.rock.sandTopCm - 0.2)

    /** A point on the sand [k] cm out from the burrow mouth (the head riding just over the bed). */
    fun mouth(k: Double): Vec3 {
        val p = burrow - inDir * k
        return offRock(Vec3(p.x, bedY(p.x, p.z) + lift, p.z))
    }

    /** Shifts a lying head point facing [face] so the tail end behind it (toward the rock edge) stays off the rock too. */
    private fun clearTail(at: Vec3, face: Vec3): Vec3 {
        val tail = at - face * o.lengthCm - Vec3.Y * (0.5 * o.lengthCm * sin(PROP_PITCH))
        if (c.rock.sdf.dist(tail) >= ROCK_GAP_CM + 0.5) return at
        return offRock(at + (c.rock.sdf.project(tail, ROCK_GAP_CM + 0.5) - tail))
    }

    override fun restSpot(c: Ctx, night: Boolean): Rest =
        if (night) Rest(offRock(burrow + inDir * 1.5), inDir, posture = 3)
        else Rest(mouth(lookoutOut), inDir * -1.0)

    override fun anchorPos(c: Ctx, anchor: String): Vec3? = if (anchor == "burrow" || anchor == "hole") burrow else super.anchorPos(c, anchor)

    override fun shelterSpot(c: Ctx): Vec3? = mouth(0.5)

    override fun peekRest(c: Ctx): Rest = Rest(mouth(0.3), inDir * -1.0, posture = 3)

    /** Sand at the foot of a rock, under an edge or overhang, low toward the front, spread out from other burrowers. */
    private fun findBurrow(c: Ctx): Pair<Vec3, Vec3> {
        val r = c.rock
        val others = c.sim.organisms.filter { it !== o && it.alive && (it.brainKind == BrainKind.WATCHMAN_GOBY || it.brainKind == BrainKind.DIAMOND_GOBY) }
            .mapNotNull { c.sim.agentOf(it)?.brain?.anchorPos(c, "burrow") }
        var best: Vec3? = null
        var bs = -1e9
        var dir = Vec3(0.0, 0.0, -1.0)
        for (i in 0 until 80) {
            val x = c.rng.range(10.0, r.lengthCm - 10.0)
            val z = c.rng.range(6.0, r.widthCm - 4.0)
            val p = Vec3(x, r.sandTopCm + 1.0, z)
            val d = r.sdf.dist(p)
            if (d < 0.5 || d > 3.0) continue
            val over = r.sdf.dist(Vec3(x, r.sandTopCm + 4.0, z))
            var s = r.sdf.cover(p) - 0.3 * d - (if (over < 1.5) 0.0 else 0.6) + 0.02 * z
            for (q in others) if (q.distanceTo(p) < SPACING_CM) s -= 2.0
            if (s > bs) { bs = s; best = p; dir = horizontalHeading(r.sdf.gradient(p) * -1.0, Vec3(0.0, 0.0, -1.0)) }
        }
        return (best ?: Vec3(r.lengthCm * 0.4, r.sandTopCm + 1.0, r.widthCm * 0.7)) to dir
    }

    /** The tail-flick warning before a dive: a partner shrimp (or the mate) goes in with it. */
    private fun alarm(c: Ctx) {
        if (c.t - alarmAt < 2.0) return
        alarmAt = c.t; alarms++
        mate?.takeIf { it.alive }?.let { m -> c.sim.agentOf(m)?.let { a -> (a.brain as? WatchmanGobyBrain)?.heardAlarm(a.ctx) } }
    }

    /** The mate's tail flick reaches it too: it dives with it. */
    fun heardAlarm(c: Ctx) { if (startleAt < c.t - 1.0) { startleAt = c.t; startleMag = 0.3; startleFrom = burrow - inDir * 10.0 } }

    /**
     * Gets to the burrow mouth [m]: swims while far, then slides along the bed the last stretch (the mouth is tucked
     * under a rock edge, inside the clearance the swim steering keeps from rock). True once there.
     */
    private fun toMouth(out: SteerIntent, m: Vec3, speed: Double, rate: Double, urgency: Double = 0.0): Boolean {
        val d = o.pos.distanceTo(m)
        if (d <= 1.0) return true
        if (d > APPROACH_CM) out.seek(m, speed, arrive = 1.0, urgency = urgency)
        else out.place(m, horizontalHeading(m - o.pos, o.heading), null, rate)
        return false
    }

    // ================================================================ behaviours

    /**
     * The watch (research 50 §3 facts 90-95): lying on the sand just outside the mouth, propped on the pelvic disc with
     * the head lifted, facing out, the eyes swivelling independently; the pectorals fan slowly. The mate lies beside it.
     */
    inner class Lookout : Behavior("lookout", "Keeping watch at its burrow") {
        private var nextLook = 0.0
        private var lookYaw = 0.0
        override fun score(c: Ctx) = if (c.p.night) 0.0 else 0.45
        override fun update(c: Ctx, out: SteerIntent) {
            val side = if (mate != null && o.id > (mate?.id ?: "")) 1.0 else if (mate != null) -1.0 else 0.0
            val lat = Vec3(-inDir.z, 0.0, inDir.x) * (side * 0.6 * c.L)
            val spot = mouth(lookoutOut) + lat
            if (c.t > nextLook) { nextLook = c.t + c.rng.range(1.5, 5.0); lookYaw = c.rng.signed() * 0.5 }
            val face = Vec3.rotateAbout(horizontalHeading(inDir * -1.0, o.heading), Vec3.Y, lookYaw)
            val at = clearTail(offRock(Vec3(spot.x, bedY(spot.x, spot.z) + lift, spot.z)), face)
            if (o.pos.distanceTo(at) > 8.0) {
                out.seek(at, o.cruiseSpeed * 0.8, arrive = 1.0)
                return
            }
            // a short shuffle (the spot moved when the bed shifted or the mate settled) slides rather than swims
            out.place(at, face, null, if (o.pos.distanceTo(at) > 1.0) 1.5 else 4.0)
            // still sliding out of the mouth (after a spit or a dive): the tail end is down the burrow yet
            if (o.pos.distanceTo(burrow) < 0.8 * o.lengthCm) out.posture = 3
            out.pitchBias = -PROP_PITCH
            out.pectoral = 0.5; out.ampMul = 0.1; out.tailMul = 0.2
            o.eyeYaw = 0.45 * sin(2 * PI * 0.3 * c.t + o.appendagePhase)
            o.gill = 0.3 + 0.15 * sin(c.t * 2.2)
        }
    }

    /** Night, sickness or a long scare: in the burrow, head just inside by day, out of sight at night. */
    inner class InBurrow : Behavior("burrow", "In its burrow") {
        override fun score(c: Ctx): Double = when {
            c.p.night -> 0.85 * Feed.nightRestDamp(c, this@WatchmanGobyBrain)
            o.waryLevel > 0.35 || o.health < 0.4 -> 0.7
            else -> 0.05
        }
        override fun update(c: Ctx, out: SteerIntent) {
            val night = c.p.night
            if (o.posture != 3 && !toMouth(out, mouth(0.5), o.cruiseSpeed, 3.0)) return
            out.place(offRock(burrow + inDir * (if (night) 1.5 else 0.2)), inDir, Vec3.Y, 3.0)
            out.posture = 3
            o.visible = !night
            o.sleeping = night
        }
        override fun exit(c: Ctx) { c.o.visible = true }
    }

    /** At a threat or a startle: the tail flick, then head first into the burrow; it stays in a while, then peeks. */
    inner class BurrowDive : Behavior("dive", "Diving into its burrow") {
        private var hideUntil = 0.0
        override val committed: Boolean get() = !finished
        override fun score(c: Ctx): Double {
            threat?.let { if (it.alive) return 0.96 }
            if (startledRecently && startleMag > 0.05) return 0.96
            return if (c.t < hideUntil) 0.9 else 0.0
        }
        override fun enter(c: Ctx) {
            val fresh = threat != null || startledRecently
            startleAt = -1e9
            if (fresh) { hideUntil = c.t + c.rng.range(HIDE_MIN_S, HIDE_MAX_S) * (0.5 + o.skittishness); alarm(c) }
        }
        override fun update(c: Ctx, out: SteerIntent) {
            if (elapsed < FLICK_S) {
                // the warning: a burst of tail beats on the spot
                out.place(o.pos, o.heading, null, 6.0)
                out.tailMul = 3.0; out.ampMul = 1.8
                return
            }
            val m = mouth(0.4)
            if (o.posture != 3 && !toMouth(out, m, o.burstSpeed, 8.0, urgency = 1.0)) return
            out.place(offRock(burrow + inDir * 1.2), inDir, Vec3.Y, 20.0)
            out.posture = 3
            o.visible = false
            if (c.t > hideUntil && threat == null) { o.visible = true; finished = true }
        }
        override fun exit(c: Ctx) { c.o.visible = true }
    }

    /** Food that drifts or lands within [SNAP_CM] of the burrow: a hop out, a snap, and straight back (research 50 §3 fact 98). */
    inner class SnapFeed : Feed(this@WatchmanGobyBrain, SNAP_CM + 10.0, "Snapping up food") {
        override fun accept(c: Ctx, f: FoodParticle): Boolean =
            !f.onSurface && f.pos.distanceTo(burrow) < SNAP_CM && f.pos.y < c.rock.sandTopCm + SNAP_UP_CM
        override fun bonus(c: Ctx, f: FoodParticle) = if (f.settled) 0.1 else 0.0
        override fun exit(c: Ctx) { super.exit(c); snaps++ }
    }

    /**
     * Burrow upkeep without a shrimp [L]: every so often it goes in and comes out with a mouthful of sand that it spits
     * onto the apron, keeping the mouth open (shrimp gobies are reported to dig when unpaired; Reef Builders 2015).
     */
    inner class SpitSand : Behavior("spit", "Spitting sand out of its burrow") {
        override fun score(c: Ctx) = if (!c.p.night && c.t > nextSpit && threat == null) 0.5 else 0.0
        override fun update(c: Ctx, out: SteerIntent) {
            if (elapsed < 2.0) {
                val m = mouth(0.4)
                if (o.posture != 3 && o.pos.distanceTo(m) > 1.5) { out.place(m, o.heading, null, 3.0); return }
                out.place(offRock(burrow + inDir * 0.8), inDir, Vec3.Y, 6.0); out.posture = 3
                return
            }
            val m = mouth(1.2)
            // head and shoulders out of the mouth, the rest of it still down the burrow
            out.place(m, horizontalHeading(inDir * -1.0, o.heading), null, 6.0); out.posture = 3
            o.mouth = 1.0
            if (elapsed > 2.4) {
                sand.dig(burrow.x, burrow.z, SPIT_CM3, 0.9)
                sand.deposit(m.x - inDir.x * 1.5, m.z - inDir.z * 1.5, SPIT_CM3, 1.6)
                sand.puff(m.x, sand.surfaceY(m.x, m.z) + 0.3, m.z, 3, 1.5)
                spits++
                nextSpit = c.t + c.rng.range(SPIT_GAP_MIN_S, SPIT_GAP_MAX_S)
                finished = true
            }
        }
    }

    companion object {
        /** Tail tip this far (cm) out from the mouth by day, head raised this much (rad, ~14°) on the pelvic disc [L]. */
        @Ev(Evidence.DESIGN_ESTIMATE, "50 §3 facts 90-95") const val LOOKOUT_OUT_CM = 1.2
        const val PROP_PITCH = 0.25
        /** Food within this far of the burrow (cm) and this high over the sand (cm) is worth a hop [L]. */
        @Ev(Evidence.DESIGN_ESTIMATE, "50 §3 fact 98") const val SNAP_CM = 18.0
        const val SNAP_UP_CM = 12.0
        /** The tail-flick warning (s) and time hidden after a dive (s, × 0.5 + skittishness) [L]. */
        @Ev(Evidence.DESIGN_ESTIMATE, "50 §3 fact 96") const val FLICK_S = 0.35
        const val HIDE_MIN_S = 15.0
        const val HIDE_MAX_S = 60.0
        /** Two watchmen added within the hour are a mated pair this often [L]. */
        const val PAIR_CHANCE = 0.5
        /** Burrow upkeep: one mouthful (cm³) every 20-60 min [L]. */
        const val SPIT_CM3 = 0.25
        const val SPIT_GAP_MIN_S = 1200.0
        const val SPIT_GAP_MAX_S = 3600.0
        const val ROCK_GAP_CM = 0.3
        /** Within this far (cm) of the mouth it slides in along the bed instead of swimming. */
        const val APPROACH_CM = 8.0
        /** A new burrow keeps this far (cm) from another goby's. */
        const val SPACING_CM = 20.0
    }
}
