package com.reeflive.core.ai

import com.reeflive.core.geom.Vec3
import com.reeflive.core.geom.smoothstep
import com.reeflive.core.sim.BrainKind
import com.reeflive.core.sim.ClamRig
import com.reeflive.core.sim.Ev
import com.reeflive.core.sim.Evidence
import com.reeflive.core.sim.FoodKind
import com.reeflive.core.sim.HealthModel
import com.reeflive.core.sim.HoleKind
import com.reeflive.core.sim.RockHole
import com.reeflive.core.sim.Simulation
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// =====================================================================================
// Flame scallop (rough fileclam Ctenoides scaber, Limidae; research 42 Part 2). Not a true scallop. It sits wedged in a
// shaded crevice with the valves a little open, the red mantle lips showing and a fringe of long sticky tentacles
// writhing in the water, pumping water through its gills to strain out phytoplankton. Touched or shaded it snaps shut;
// pestered by crabs, shrimp or a picking fish, or left in bright light, it claps its valves and jets off, hinge first
// with the tentacles trailing, to find another crevice. In a tank it mostly starves: there is too little phytoplankton
// unless the keeper doses it every day (Kurtz 2014, research 42 §2.4).
//
// Pose (ClamRig, fish.vert mode 7): +X is the gape (the open edge), the hinge at -X; +Y is the upper valve's side.
// =====================================================================================
class FileClamBrain(c: Ctx) : SpeciesBrain(c) {
    companion object {
        /** Valve gape while feeding, fraction of the widest (research 42 §2.2: valves part to show the mantle) [E]. */
        const val GAPE_FEEDING = 0.7
        /** Seconds between the valve "coughs" that flush pseudofaeces (bivalve habit; interval unmeasured for Ctenoides) [E]. */
        @Ev(Evidence.DESIGN_ESTIMATE, "42 §2.3") const val COUGH_MIN_S = 60.0
        const val COUGH_MAX_S = 200.0
        /** Valve claps per second while swimming (research 42 §2.3: unmeasured; ~1-3 claps/s sim estimate) [E]. */
        @Ev(Evidence.DESIGN_ESTIMATE, "42 §2.3") const val CLAP_HZ = 2.2
        /** Swimming speed while clapping, cm/s (0.2-0.5 m per burst of a few seconds, research 42 sim table) [E]. */
        const val SWIM_CM_S = 9.0
        /** Sinking speed between bursts, cm/s (a heavy shell) [E]. */
        const val SINK_CM_S = 2.5
        /** Longest swim before it gives up and settles where it lands, s. */
        const val SWIM_MAX_S = 30.0
        /** Pestering it puts up with (s of something picking at its gape within a few minutes) before it swims [E]. */
        const val PESTER_LIMIT_S = 6.0
        /** Bright light it tolerates at its spot (light × (1 − cover)); above it a stress clock runs [E]. */
        const val BRIGHT = 0.45
        /** Hours in bright light before it leaves (research 42 §2.6: cryptic, avoids light [M]) [E]. */
        const val BRIGHT_LEAVE_H = 3.0
        /** Suspended food caught near the gape, radius cm. */
        const val CATCH_CM = 5.0

        /**
         * Phytoplankton the animal can live on, 0..1 (research 42 §2.4): the tank's own phyto (cells/mL, saturating at a
         * dosed level ~10^5) plus a little from fine detritus and bacteria. A normal reef tank sits near 0.15-0.25, well
         * short of the 1 it needs; a daily phyto dose lifts it near 1 for a day or two.
         */
        @Ev(Evidence.DESIGN_ESTIMATE, "42 §2.4 Kurtz 2014 (starves without dosing)")
        fun foodAvailable(sim: Simulation): Double {
            val phyto = sim.water.micro.phytoPerMl
            val p = phyto / (phyto + PHYTO_HALF)
            val det = 0.12 * (sim.water.s.detritus / 0.2).coerceIn(0.0, 1.5)
            return (p + det).coerceIn(0.0, 1.0)
        }
        /** Phyto (cells/mL) at which half its needs are met; one 50 mL dose in Tee's 227 L gives ~2 × 10^5 [E]. */
        const val PHYTO_HALF = 3.0e4
        /** Hunger it can work off per hunger it gains, at full food (filtering all day) [E]. */
        const val FILTER_GAIN = 1.6

        /** Filter feeding without the behaviour layer (fast time, away catch-up): the same balance as [Live]. */
        fun abstractFeed(o: com.reeflive.core.sim.Organism, sim: Simulation, dtMin: Double) {
            val rise = HealthModel.hungerRatePerMin(o, sim.water.s.tempC)
            o.hunger = max(0.0, o.hunger - FILTER_GAIN * rise * foodAvailable(sim) * dtMin)
        }
    }

    private enum class Mode { WEDGED, SWIM, LANDED }

    private var mode = Mode.WEDGED
    private var home: Rest? = null
    private var hole: RockHole? = null
    private var gape = 0.05
    private var ext = 0.0
    private var wave = 0.0
    private var clap = 0.0
    private var trail = 0.0
    private var flinch = 0.0
    private var nextCough = 0.0
    private var coughUntil = -1.0
    private var pester = 0.0
    private var brightS = 0.0
    private var swimStart = 0.0
    private var swimGoal: Rest? = null
    private var landedUntil = 0.0
    private var placedAt = Vec3.ZERO
    private var nextFeed = 0.0
    /** Swims taken (tests, captions). */
    var swims = 0; private set
    var snaps = 0; private set

    init { install(Live()) }

    override fun restSpot(c: Ctx, night: Boolean): Rest = home ?: findHome(c)

    override fun initialPlace(c: Ctx) {
        val r = findHome(c)
        o.homePos = r.pos
        o.placeAt(r.pos, r.heading)
        o.up = r.up ?: Vec3.Y
        placedAt = o.pos
    }

    /** Valve-plane normal for a body along [axis], from a preferred up. */
    private fun upFor(axis: Vec3, prefer: Vec3): Vec3 = prefer.rejectFrom(axis).normalized((Vec3.Z cross axis).normalized(Vec3.Y))

    /**
     * Its spot: a free crevice or hole in the rock that fits its shell (claimed, one clam per hole), sitting a little way
     * in with the gape at the mouth and the valves parallel to the slot's faces; failing that, the most shaded rock face
     * near [near], lying on its side under the overhang with the gape toward open water.
     */
    private fun findHome(c: Ctx, near: Vec3? = null): Rest {
        hole?.let { c.sim.holeClaims.release(it, o) }
        val from = near ?: o.pos.takeIf { it != Vec3.ZERO }
        val h = c.rock.holes.claimHome(o, setOf(HoleKind.CREVICE, HoleKind.HOLE, HoleKind.CAVE, HoleKind.OVERHANG), from, c.sim.holeClaims)
        val L = o.lengthCm
        val r = if (h != null && c.rock.holes.all.isNotEmpty()) {
            hole = h
            val axis = h.axis.normalized(Vec3.Z)
            val inset = min(0.45 * L, max(0.0, h.depth - 0.5 * L)).coerceAtLeast(0.15 * L)
            val p = h.along(inset)
            val up = if (h.kind.slot) h.up.rejectFrom(axis).normalized(Vec3.Y) else upFor(axis, Vec3.Y)
            Rest(p, axis, up, posture = 3)
        } else {
            hole = null
            val centre = from ?: Vec3(c.rock.lengthCm * 0.5, c.rock.sandTopCm + 10, c.rock.widthCm * 0.5)
            val thick = 0.5 * o.species.depthRatio * L
            val hit = c.rock.perchNear(centre, 18.0, step = 2.0, accept = { q, n -> n.y < 0.6 && q.y > c.rock.sandTopCm + 2 },
                score = { q, n -> 10.0 * (1 - c.rock.sdf.cover(q + n * 3.0)) + 0.15 * q.distanceTo(centre) + 2.0 * max(0.0, n.y) })
            if (hit != null) {
                val (q, n) = hit
                // gape toward the open front and a little down, the flat of the shell against the rock
                val out = (Vec3(0.0, -0.3, 1.0).rejectFrom(n)).normalized((Vec3.Z cross n).normalized(Vec3.X))
                Rest(q + n * (thick + 0.2), out, n, posture = 3)
            } else Rest(c.safe(centre, thick + 0.5), Vec3.Z, Vec3.Y, posture = 3)
        }
        home = r
        return r
    }

    override fun preTick(c: Ctx) {
        super.preTick(c)
        // moved from outside (the bag tipped it out, a hand or the rock builder moved it): find a crevice from here
        if (mode != Mode.SWIM && o.pos.distanceTo(placedAt) > 3.0) startSwim(c, settleOnly = true)
    }

    override fun onTap(c: Ctx, at: Vec3, s: Double) { super.onTap(c, at, s); flinch = max(flinch, 0.4 + 0.6 * s); snaps++ }
    override fun onShadow(c: Ctx, at: Vec3) {
        super.onShadow(c, at)
        // it has rows of simple eyes on the mantle and closes to a shadow (Limidae [M])
        if (at.distanceTo(o.pos) < 25) flinch = max(flinch, 0.35)
    }
    override fun onTouch(c: Ctx, at: Vec3, kind: com.reeflive.core.sim.TouchReaction, s: Double) {
        super.onTouch(c, at, kind, s)
        if (at.distanceTo(o.pos) < o.lengthCm + 3) { flinch = 1.0; pester += 3.0 }
    }
    override fun onAttacked(c: Ctx, from: Vec3, mag: Double, by: com.reeflive.core.sim.Organism?) {
        super.onAttacked(c, from, mag, by)
        flinch = 1.0; pester += 4.0 * mag
    }

    /** Animals that pick at a file clam's tentacles and mantle (research 42 §2.6 [M]: crabs, shrimp, hermits; some wrasses). */
    private fun pests(k: BrainKind) = k == BrainKind.BANDED_SHRIMP || k == BrainKind.SALLY_CRAB || k == BrainKind.HERMIT ||
        k == BrainKind.DOTTYBACK || k == BrainKind.LEOPARD_WRASSE || k == BrainKind.PEEKER_BLENNY

    private fun startSwim(c: Ctx, settleOnly: Boolean = false) {
        mode = Mode.SWIM
        swimStart = c.t
        if (!settleOnly) swims++
        pester = 0.0; brightS = 0.0
        // a new crevice: away from what bothered it (any free one that fits; the old claim goes)
        val away = o.pos + o.heading * (if (settleOnly) 0.0 else -15.0)
        swimGoal = findHome(c, if (settleOnly) o.pos else away)
    }

    inner class Live : Behavior("clam", "Flame scallop") {
        override val noisy: Boolean get() = false
        override val label: String get() = when {
            mode == Mode.SWIM && trail > 0.5 -> "Swimming away by clapping its shell"
            mode == Mode.SWIM -> "Looking for a new crevice"
            mode == Mode.LANDED -> "Resting where it landed"
            flinch > 0.4 -> "Snapped shut"
            c.t < coughUntil -> "Flushing its gills"
            o.hunger > 0.8 -> "Starving: filtering, but there is too little phytoplankton"
            o.hunger > 0.55 -> "Hungry: filter feeding"
            else -> "Wedged in its crevice, filter feeding"
        }
        override fun score(c: Ctx) = 0.5
        override fun update(c: Ctx, out: SteerIntent) = live(c, out)
    }

    private fun live(c: Ctx, out: SteerIntent) {
        val dt = c.dt
        flinch *= exp(-dt / 12.0)
        pester = max(0.0, pester - dt / 60.0)
        when (mode) {
            Mode.SWIM -> swim(c, out)
            Mode.LANDED -> {
                out.place(o.pos, o.heading, o.up, 4.0); out.posture = 3
                if (c.t > landedUntil) startSwim(c, settleOnly = true)
            }
            Mode.WEDGED -> {
                val r = home ?: findHome(c)
                out.rest(r, 3.0)
                placedAt = r.pos
                o.homePos = r.pos
                sense(c, r)
            }
        }
        // valves: open to feed, shut on a flinch, a quick partial close every minute or so (the cough)
        if (c.t >= nextCough) { nextCough = c.t + c.rng.range(COUGH_MIN_S, COUGH_MAX_S); coughUntil = c.t + 1.2 }
        val weak = smoothstep(0.6, 1.0, o.hunger) * 0.5 + (1 - o.health).coerceIn(0.0, 1.0) * 0.4
        val gTarget = when {
            mode == Mode.SWIM -> 0.25 + 0.55 * (0.5 + 0.5 * sin(2 * PI * clap))
            flinch > 0.3 -> 0.03
            c.t < coughUntil -> 0.25
            else -> GAPE_FEEDING * (1 - 0.35 * weak) * (if (c.p.night) 1.0 else 0.9)
        }
        // shutting is fast (adductor), opening slow (the hinge ligament)
        gape += (gTarget - gape) * min(1.0, dt * if (gTarget < gape) 12.0 else 1.2)
        val eTarget = when {
            mode == Mode.SWIM -> 0.6
            flinch > 0.3 -> 0.0
            else -> 1.0 - 0.45 * weak
        }
        ext += (eTarget - ext) * min(1.0, dt * if (eTarget < ext) 6.0 else 0.4)
        trail += ((if (mode == Mode.SWIM) 1.0 else 0.0) - trail) * min(1.0, dt * 2.0)
        // the tentacles writhe slowly all the time (no rate measured; ~0.3 Hz reads right) [E]
        wave += dt * (0.25 + 0.15 * (1 - weak))
        o.extension = ext
        o.mouth = gape
        (o.limbRig as? ClamRig)?.set(wave, gape, ext, trail, clap, 0.6 + 0.4 * (1 - weak), smoothstep(0.75, 1.0, o.hunger) * 0.7)
    }

    /** Wedged: feeding, things picking at it, too much light. */
    private fun sense(c: Ctx, r: Rest) {
        val dt = c.dt
        if (c.t >= nextFeed) {
            nextFeed = c.t + 1.0
            // filtering, while the valves are open: the water's phytoplankton, and suspended particles that drift past
            if (gape > 0.3) {
                val rise = HealthModel.hungerRatePerMin(o, c.sim.water.s.tempC)
                o.hunger = max(0.0, o.hunger - FILTER_GAIN * rise * foodAvailable(c.sim) / 60.0)
                val mouth = r.pos + r.heading * (0.5 * o.lengthCm)
                c.food.nearest(mouth, CATCH_CM) { !it.settled && it.kind.suspended && o.species.dietMatch(it.kind) > 0 }?.let { c.bite(it) }
            }
            // something picking at the gape or the tentacles
            val gapeAt = r.pos + r.heading * (0.5 * o.lengthCm)
            val picker = c.p.neighbors(gapeAt, 6.0).firstOrNull { it !== o && it.alive && pests(it.brainKind) && it.pos.distanceTo(gapeAt) < 3.0 + 0.3 * it.lengthCm }
            if (picker != null) {
                pester += 1.0
                flinch = max(flinch, 0.6)
                // a crab or the banded shrimp tears at the mantle now and then (research 42 §2.6 [M]; damage [E])
                if (gape > 0.2 && c.rng.chance(0.02)) o.health = max(0.05, o.health - 0.02)
            }
            val bright = c.p.light * (1 - 0.7 * c.rock.sdf.cover(gapeAt + r.heading * 2.0))
            brightS = if (bright > BRIGHT) brightS + 1.0 else max(0.0, brightS - 0.5)
        }
        if (pester > PESTER_LIMIT_S || brightS > BRIGHT_LEAVE_H * 3600 || startledRecently && startleMag > 0.85) startSwim(c)
        if (dt > 0 && hole != null && c.rock.holes.live(hole) == null) startSwim(c, settleOnly = true)
    }

    /**
     * Jet swimming (research 42 §2.3): bursts of valve claps at [CLAP_HZ] drive it hinge first, wobbling, the tentacles
     * trailing; between bursts it sinks. It heads for [swimGoal] and settles there, or lands on whatever it reaches
     * when it tires. The body is kept off the rock, sand and glass (no clipping): a step into the rock slides along it.
     */
    private fun swim(c: Ctx, out: SteerIntent) {
        val dt = c.dt
        val goal = swimGoal ?: findHome(c).also { swimGoal = it }
        val e = c.t - swimStart
        val bursting = (e % 6.0) < 3.5
        if (bursting) clap += dt * CLAP_HZ
        val toGoal = goal.pos - o.pos
        val dist = toGoal.length()
        if (dist < 1.2 || e > SWIM_MAX_S && dist < 4.0) { settle(c, goal); return }
        if (e > SWIM_MAX_S + 10) {
            // tired out: rest where it is and try again in a while
            mode = Mode.LANDED; landedUntil = c.t + c.rng.range(60.0, 240.0); placedAt = o.pos
            return
        }
        val thick = 0.5 * o.species.depthRatio * o.lengthCm + 0.3
        val dir = toGoal.normalized(Vec3.Y)
        val pulse = 0.6 + 0.4 * sin(2 * PI * clap)
        val v = if (bursting) dir * (SWIM_CM_S * pulse) + Vec3(0.0, 1.5, 0.0) else dir * 2.0 + Vec3(0.0, -SINK_CM_S, 0.0)
        // near the goal it slows and creeps in
        val step = v * (dt * if (dist < 6.0) 0.5 else 1.0)
        var next = o.pos + step
        val d = c.rock.envDist(next)
        if (d < thick) next += c.rock.envNormal(next) * (thick - d)
        next = c.rock.clampToWater(next, thick)
        // hinge first: the gape (+X) points back along its travel, tentacles streaming
        val back = (o.pos - next).normalized(o.heading)
        val wob = 0.25 * sin(2 * PI * clap * 0.5)
        val up = Vec3.rotateAbout(upFor(back, Vec3.Y), back, wob)
        out.place(next, back, up, 8.0)
        out.posture = 3
        placedAt = next
    }

    private fun settle(c: Ctx, r: Rest) {
        mode = Mode.WEDGED; home = r; swimGoal = null; placedAt = r.pos; o.homePos = r.pos
        flinch = 0.6
    }

    override fun abstractPose(c: Ctx, out: SteerIntent) {
        val r = home ?: findHome(c)
        out.place(r.pos, r.heading, r.up)
        out.posture = 3
        placedAt = r.pos
        (o.limbRig as? ClamRig)?.set(wave, GAPE_FEEDING, 1.0, 0.0, 0.0, 0.8, smoothstep(0.75, 1.0, o.hunger) * 0.7)
    }
}
