package com.reeflive.core.ai

import com.reeflive.core.geom.Vec3
import com.reeflive.core.sim.BrainKind
import com.reeflive.core.sim.Coral
import com.reeflive.core.sim.CoralKind
import com.reeflive.core.sim.Ev
import com.reeflive.core.sim.Evidence
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// =====================================================================================
// Peppermint shrimp (Lysmata boggessi / L. wurdemanni; research 42): a small, shy Lysmata. By day it hides deep in the
// rock with only its antennae out, often in a little group; after dark it walks the rock picking at it, and it hunts
// the pest anemone Aiptasia, the reason keepers buy it. A hungry shrimp eats more aiptasia than a well-fed one, small
// polyps far more readily than big established ones. It scavenges at feeding time, molts every 1-3 weeks, and is food
// for the hawkfish and (soft after a molt) the coral banded shrimp, which also chases it off its ledge.
// =====================================================================================
class PeppermintShrimpBrain(c: Ctx) : SpeciesBrain(c) {
    companion object {
        /** Night foray radius around its hiding spot, cm (small Lysmata stay near home; scaled like the banded shrimp's) [E]. */
        const val FORAY_CM = 35.0
        /** How far it goes for an aiptasia at night, cm [E]. */
        const val AIPTASIA_REACH_CM = 60.0
        /**
         * Aiptasia eaten per shrimp per day, starved vs well fed (research 42 §1.5: no rate exists for L. boggessi or
         * L. wurdemanni; the proxy L. seticaudata ate 86-89 % of polyps offered when starved, 27-36 % when fed, Calado &
         * Narciso 2005) [E].
         */
        @Ev(Evidence.DESIGN_ESTIMATE, "42 §1.5 Calado & Narciso 2005 (proxy species)") const val AIPTASIA_STARVED_PER_DAY = 1.0
        const val AIPTASIA_FED_PER_DAY = 0.15
        /** Chance that a hungry shrimp gets the better of an established (big) polyp; most are ignored (research 42 §1.5 [M]). */
        const val LARGE_KILL_P = 0.25
        /** Below this hunger an established polyp is not worth the stinging (research 42 §1.5 [M]). */
        const val LARGE_HUNGER = 0.45
        /** Picking time at one polyp, s [E]. */
        const val PICK_MIN_S = 25.0
        const val PICK_MAX_S = 80.0
        /** A starving peppermint may nip zoanthid polyps (research 42 §1.6 [E], anecdotal). */
        const val ZOA_NIP_HUNGER = 0.75
    }

    val nav = ShrimpNav(o)
    private var den: Rest? = null
    /** Rock cover over the hiding spot (static while the spot stands). */
    private var denShade = 0.0
    /** Aiptasia polyps eaten (tests, captions). */
    var aiptasiaEaten = 0; private set
    var aiptasiaRefused = 0; private set
    var zoaNips = 0; private set
    private var nextHuntAt = -1.0

    private val hunt = HuntAiptasia()

    init {
        install(Hide(), ShrimpForage(this, nav, 30.0, 6.0, "Grabbing food"), NightForage(), hunt,
            MoltHide(this) { den(it) }, TailFlip(this, nav) { den(it) })
    }

    override fun restSpot(c: Ctx, night: Boolean) = den(c)

    /** Surface point under its hiding spot (for spacing the group). */
    val denPoint: Vec3? get() = den?.let { it.pos - (it.up ?: Vec3.Y) * shrimpStance(o) }

    /**
     * Its hiding spot: a deeply shaded face (rock cover over it, an overhang roof or the side of a cave) near the other
     * peppermints, which hide together (research 42 §1.4 [M]), and as far from a coral banded shrimp's ledge as it can
     * get. Found once on the real rock and again after the rock is rebuilt.
     */
    fun den(c: Ctx): Rest {
        // kept while its face is still there (My tank's rock builder can move or remove the rock under it)
        den?.let { d -> if (kotlin.math.abs(c.rock.sdf.dist(d.pos) - shrimpStance(o)) < 0.6) return d }
        val mates = c.sim.organisms.filter { it !== o && it.alive && it.brainKind == BrainKind.PEPPERMINT_SHRIMP }
            .mapNotNull { (c.sim.agentOf(it)?.brain as? PeppermintShrimpBrain)?.denPoint }
        val banded = c.sim.organisms.filter { it.alive && it.brainKind == BrainKind.BANDED_SHRIMP }.map { it.homePos.takeIf { h -> h != Vec3.ZERO } ?: it.pos }
        val near = mates.firstOrNull() ?: c.rock.anchors.ofType("LEDGE").maxByOrNull { a ->
            (banded.minOfOrNull { it.distanceTo(a.pos) } ?: 50.0) + 3.0 * kotlin.math.sin(a.name.hashCode() + o.id.hashCode().toDouble())
        }?.pos ?: o.homePos.takeIf { it != Vec3.ZERO } ?: Vec3(c.rock.lengthCm * 0.5, c.rock.sandTopCm + 8, c.rock.widthCm * 0.5)
        den = shrimpPerch(c, near, 12.0, 2, mates, Vec3(0.0, 0.0, 1.0),
            accept = { _, n -> n.y < 0.5 },
            score = { q, n ->
                val shade = c.rock.sdf.cover(q + n * 2.0)
                q.distanceTo(near) * 0.3 + 8.0 * (1 - shade) + 2.0 * max(0.0, n.y) +
                    banded.sumOf { b -> max(0.0, 20.0 - b.distanceTo(q)) * 0.3 }
            }) ?: hangPose(c, null, near, inverted = false)
        val d = den!!
        denShade = c.rock.sdf.cover(d.pos + (d.up ?: Vec3.Y) * 2.0)
        return d
    }

    override fun preTick(c: Ctx) {
        super.preTick(c)
        o.groom = max(0.0, o.groom - c.dt * 2)
    }

    /** Out-of-hiding drive: low light lets it out (nocturnal Lysmata, research 42 §1.4); a hungry one comes out sooner. */
    fun forayDrive(c: Ctx): Double {
        den(c)
        val local = c.p.light * (1 - 0.6 * denShade)
        return (1 - 0.9 * com.reeflive.core.geom.smoothstep(0.06, 0.35, local)) * (0.7 + 0.3 * c.o.hunger) + 0.15 * max(0.0, c.o.hunger - 0.6)
    }

    val groomClock = GroomClock()

    /** Hidden in the rock by day, antennae out of the crack (research 42 §1.4 [M]). */
    inner class Hide : Behavior("hide", "Hiding in the rock") {
        override val label: String get() = when {
            nav.swimming -> "Swimming back to its hiding spot"
            !home -> "Walking back to its hiding spot"
            groomClock.grooming -> "Grooming its antennae"
            c.t < pickUntil -> "Picking at the rock"
            c.p.night -> "Resting in its hiding spot"
            else -> "Hiding in the rock"
        }
        private var home = true
        private var nextShuffle = 0.0
        private var shuffle = Vec3.ZERO
        private var pickUntil = -1.0
        private var nextPick = 0.0
        override fun score(c: Ctx) = 0.5 * (1.2 - forayDrive(c)).coerceIn(0.1, 1.0)
        override fun enter(c: Ctx) { nav.reset(); shuffle = Vec3.ZERO; nextPick = c.t + c.rng.range(2.0, 6.0) }
        override fun update(c: Ctx, out: SteerIntent) {
            val r = den(c)
            val n = r.up ?: Vec3.Y
            if (c.o.pos.distanceTo(r.pos) > 2.0 || nav.swimming) {
                home = nav.drive(c, out, r.pos, n, c.o.cruiseSpeed * 1.3, arrive = 1.0) == ShrimpNav.Status.ARRIVED
                if (!home) return
            }
            home = true
            // a hidden Lysmata is never still: it shifts its footing, picks at the rock in front of it and waves its
            // antennae out of the crack (research 42 §1.4 [M]; the same idle as the banded shrimp's ledge, research 12)
            if (c.t > nextShuffle) {
                nextShuffle = c.t + c.rng.range(4.0, 9.0)
                val st = shrimpStance(c.o)
                val foot = c.rock.sdf.project(r.pos - n * st + c.rng.unitVector().rejectFrom(n) * 0.6, 0.0)
                shuffle = if (kotlin.math.abs(c.rock.sdf.dist(foot)) < 0.2) foot + c.rock.sdf.gradient(foot) * st - r.pos else Vec3.ZERO
            }
            out.rest(r.copy(pos = r.pos + shuffle), 1.5)
            c.o.extension = 1.0
            val calm = threat == null
            if (calm && !groomClock.grooming && c.t > nextPick) {
                pickUntil = c.t + c.rng.range(2.5, 6.0); nextPick = pickUntil + c.rng.range(5.0, 14.0)
            }
            val picking = calm && c.t < pickUntil
            if (picking) c.o.mouth = max(c.o.mouth, 0.5 + 0.3 * sin(2 * PI * 1.8 * c.t))
            c.o.appendagePhase += c.dt * (if (picking) 3.0 else if (calm) 1.6 else 0.6)
            groomClock.tick(c, calm && !picking)
        }
    }

    /**
     * Night walk over the rock near its hiding spot, stopping to pick at the surface (research 42 §1.4: a nocturnal
     * scavenger). A starving one sometimes nips a zoanthid polyp it passes (§1.6, anecdotal [E]).
     */
    inner class NightForage : Behavior("crawl", "Foraging") {
        private var wp: Rest? = null
        private var next = 0.0
        private var dwellUntil = -1.0
        override val label: String get() = if (c.t < dwellUntil) "Picking at the rock" else "Foraging"
        override fun score(c: Ctx) = 0.45 * forayDrive(c)
        override fun enter(c: Ctx) { next = 0.0; nav.reset() }
        private fun pick(c: Ctx, around: Vec3, radius: Double) {
            val hit = c.rockSurfacePoint(around, radius, minNormalY = -1.0)
            wp = if (hit != null) Rest(hit.first + hit.second * shrimpStance(c.o), c.o.heading, hit.second, 0)
            else Rest(sandSpot(c, (around.x - radius)..(around.x + radius), 8.0..(c.rock.widthCm - 3), shrimpStance(c.o)), c.o.heading, Vec3.Y, 0)
            next = c.t + c.rng.range(25.0, 70.0)
        }
        override fun update(c: Ctx, out: SteerIntent) {
            if (wp == null || c.t > next) pick(c, den(c).pos, FORAY_CM)
            val w = wp!!
            if (c.t < dwellUntil) {
                out.crawl = true; out.target = null; out.surfaceOffset = shrimpStance(c.o)
                c.o.mouth = max(c.o.mouth, 0.5 + 0.3 * sin(2 * PI * 1.8 * c.t))
                return
            }
            when (nav.drive(c, out, w.pos, w.up, c.o.cruiseSpeed, arrive = 1.2, canHop = c.rock.envDist(c.o.pos) > shrimpStance(c.o) + 1.5)) {
                ShrimpNav.Status.ARRIVED -> {
                    dwellUntil = c.t + c.rng.range(4.0, 14.0); next = dwellUntil + 1.0; wp = null
                    out.crawl = true; out.surfaceOffset = shrimpStance(c.o)
                    if (c.o.hunger > ZOA_NIP_HUNGER) nipZoa(c)
                }
                ShrimpNav.Status.STALLED -> pick(c, c.o.pos, 12.0)
                else -> {}
            }
        }
    }

    private fun nipZoa(c: Ctx) {
        val z = c.sim.corals.firstOrNull { it.kind == CoralKind.ZOA && it.tissue > 0.05 && it.pos.distanceTo(c.o.pos) < 5.0 } ?: return
        if (!c.rng.chance(0.15)) return
        zoaNips++
        z.disturb = max(z.disturb, 1.0)
        z.stress = min(1.0, z.stress + 0.02)
        z.tissue = max(0.0, z.tissue - 0.005)
        c.o.hunger = max(0.0, c.o.hunger - 0.05)
    }

    /**
     * The aiptasia hunt (research 42 §1.5): after dark (or when hungry) it walks to the nearest live polyp that no other
     * peppermint is working on, small ones first, and picks it apart with its little chelae. How often it goes depends on
     * hunger ([AIPTASIA_FED_PER_DAY] .. [AIPTASIA_STARVED_PER_DAY]); a small polyp is eaten, an established one usually
     * shrugged off (it stings and pulls in) unless the shrimp is hungry. Nothing happens while it is soft from a molt.
     */
    inner class HuntAiptasia : Behavior("aiptasia", "Hunting aiptasia") {
        var target: Coral? = null; private set
        private var pickUntil = -1.0
        private var picking = false
        override val label: String get() = if (picking) "Eating a pest anemone (aiptasia)" else "Hunting aiptasia"
        override val targetVerb: String? get() = "hunting"

        private fun rate(c: Ctx) = AIPTASIA_FED_PER_DAY + (AIPTASIA_STARVED_PER_DAY - AIPTASIA_FED_PER_DAY) * c.o.hunger.coerceIn(0.0, 1.0)

        override fun score(c: Ctx): Double {
            if (c.o.molting || c.t - c.o.lastMoltAt < 6 * 3600) return 0.0
            // a newcomer goes looking within its first night out; after that the gap between kills follows hunger
            if (nextHuntAt < 0) nextHuntAt = c.t + 1800.0 * c.rng.nextDouble()
            if (c.t < nextHuntAt || forayDrive(c) < 0.5 && c.o.hunger < 0.7) return 0.0
            // nothing to hunt: look again in half an hour rather than every tick
            if (target?.let { it.tissue > 0.01 } != true) target = choose(c) ?: run { nextHuntAt = c.t + 1800.0; return 0.0 }
            return 0.7
        }

        private fun choose(c: Ctx): Coral? {
            val taken = c.sim.organisms.filter { it !== c.o && it.alive && it.brainKind == BrainKind.PEPPERMINT_SHRIMP }
                .mapNotNull { (c.sim.agentOf(it)?.brain as? PeppermintShrimpBrain)?.hunt?.target }
            var best: Coral? = null
            var bs = Double.MAX_VALUE
            for (p in c.sim.corals) {
                if (p.kind != CoralKind.AIPTASIA || p.tissue <= 0.01 || p.treatment != null || p in taken) continue
                val d = p.pos.distanceTo(c.o.pos)
                if (d > AIPTASIA_REACH_CM) continue
                val big = p.size >= com.reeflive.core.sim.AiptasiaModel.MATURE
                if (big && c.o.hunger < LARGE_HUNGER) continue
                val s = d + if (big) 25.0 else 0.0
                if (s < bs) { bs = s; best = p }
            }
            return best
        }

        override fun enter(c: Ctx) { nav.reset(); picking = false }

        override fun update(c: Ctx, out: SteerIntent) {
            val p = target
            if (p == null || p.tissue <= 0.01) { finish(c, null); return }
            val n = p.normal.normalized(Vec3.Y)
            val st = shrimpStance(c.o)
            // stand beside the polyp on its rock, facing it
            val side = (n cross Vec3.Y).let { if (it.length() < 0.2) Vec3.X else it.normalized(Vec3.X) }
            val foot = c.rock.sdf.project(p.pos + side * (0.4 * c.L + 0.6), 0.0)
            val stand = foot + c.rock.sdf.gradient(foot) * st
            if (!picking) {
                when (nav.drive(c, out, stand, n, c.o.cruiseSpeed * 1.2, arrive = 1.0)) {
                    ShrimpNav.Status.ARRIVED -> { picking = true; pickUntil = c.t + c.rng.range(PICK_MIN_S, PICK_MAX_S) * (0.6 + 0.6 * p.size) }
                    ShrimpNav.Status.STALLED -> if (elapsed > 90) { target = null; finish(c, null) }
                    else -> {}
                }
                if (elapsed > 600) { target = null; finish(c, null) }
                return
            }
            out.rest(Rest(stand, (p.pos - stand).rejectFrom(n).normalized(c.o.heading), c.rock.sdf.gradient(foot), 0), 2.0)
            c.o.mouth = max(c.o.mouth, 0.6 + 0.4 * sin(2 * PI * 2.2 * c.t))
            c.o.appendagePhase += c.dt * 3
            p.extension = min(p.extension, 0.3)
            if (c.t >= pickUntil) finish(c, p)
        }

        private fun finish(c: Ctx, p: Coral?) {
            if (p != null) {
                val big = p.size >= com.reeflive.core.sim.AiptasiaModel.MATURE
                if (!big || c.rng.chance(LARGE_KILL_P)) {
                    // eaten whole (the pest model's bite: it pulls in, dies and is cleared like any other)
                    com.reeflive.core.sim.AiptasiaModel.bite(c.sim, p, 2.0)
                    aiptasiaEaten++
                    c.o.eat(c.o.mealGrams * (if (big) 0.8 else 0.4))
                    c.o.counters.add(com.reeflive.core.sim.Counters.K.FEED, c.t)
                } else {
                    // stung and let go: the polyp pulls in, the shrimp gives up on it for now
                    aiptasiaRefused++
                    p.disturb = max(p.disturb, 0.4)
                }
            }
            target = null; picking = false
            nextHuntAt = c.t + 86400.0 / rate(c) * (0.5 + c.rng.nextDouble())
            finished = true
        }
    }
}
