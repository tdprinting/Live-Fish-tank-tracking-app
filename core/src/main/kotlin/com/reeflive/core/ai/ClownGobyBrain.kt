package com.reeflive.core.ai

import com.reeflive.core.geom.Vec3
import com.reeflive.core.sim.BrainKind
import com.reeflive.core.sim.Coral
import com.reeflive.core.sim.CoralGrowth
import com.reeflive.core.sim.Ev
import com.reeflive.core.sim.Evidence
import com.reeflive.core.sim.FoodParticle
import com.reeflive.core.sim.Organism
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Yellow clown goby, *Gobiodon okinawae* (docs/research/51_popular_fish_batch2.md §3). A tiny, boxy, lemon-yellow goby
 * that lives perched in the branches of a coral colony (Acropora in the wild; a tree coral such as the Kenya tree here,
 * or a rock tip when there is none). It sits propped on its pelvic disc, turns its head to watch, snaps the copepods
 * and baby brine that drift past within a few centimetres, and shuffles round its colony in short hops; it rarely
 * leaves it. Two clown gobies squabble over a colony, then usually pair, the bigger turning male (it can change sex
 * either way, Danireef; Reef Builders 2024). Its skin mucus is bitter, so most predators leave it alone (51 §3 fact 117).
 */
class ClownGobyBrain(c: Ctx) : SpeciesBrain(c) {
    /** Rock tops to sit on when there is no tree coral (traced once, when first needed). */
    private val rockTops by lazy { PerchSites.build(c.rock, { x, z -> c.sim.sand.surfaceY(x, z) }, c.rock.anchors.ofType("PERCH")) }
    /** The colony it lives in, or null (it sits on a rock top then). */
    var coral: Coral? = pickCoral(c); private set
    private var rockPerch: Vec3? = null
    /** Where it sits now on its colony and which way it faces. */
    var perch: Vec3 = Vec3.ZERO; private set
    private var perchOut: Vec3 = Vec3.Z
    var mate: Organism? = null; private set
    var squabbles = 0; private set
    var snaps = 0; private set
    var hops = 0; private set
    private var nextHome = 0.0

    init {
        o.spline.glassGuard = true
        newPerch(c)
        install(Perch(), Snap(), Squabble(), Flee(this, { _, _ -> perch }, "Ducking into the coral"), SurfaceGasp(this))
    }

    override fun restSpot(c: Ctx, night: Boolean): Rest = Rest(perch, perchOut, Vec3.Y, posture = if (night) 3 else 1)

    override fun shelterSpot(c: Ctx): Vec3? = perch

    override fun anchorPos(c: Ctx, anchor: String): Vec3? = if (anchor == "coral" || anchor == "perch") perch else super.anchorPos(c, anchor)

    override fun preTick(c: Ctx) {
        super.preTick(c)
        // its colony died or was moved off: find another
        if (c.t >= nextHome) {
            nextHome = c.t + 120.0
            val k = coral
            if (k == null || k.tissue < 0.1 || k !in c.sim.corals) { coral = pickCoral(c); newPerch(c) }
        }
        val m = mate
        if (m != null && !m.alive) mate = null
    }

    private val L: Double get() = o.lengthCm

    /** Canopy radius of a tree coral (cm), from its growth form and size. */
    private fun canopy(k: Coral): Double = CoralGrowth.growth(k.kind).r0 * k.size.coerceIn(0.4, 2.5)

    /**
     * A spot among the colony's outer branches: up the colony's axis and out to one side, facing out; or a rock top.
     * Kept clear of the rock (the tail too), so a small colony on a ledge still gives a clean perch.
     */
    private fun newPerch(c: Ctx) {
        val k = coral
        val clear = 0.5 * L + 0.6
        if (k != null) {
            val n = k.normal.normalized(Vec3.Y)
            val r = canopy(k)
            for (i in 0 until 12) {
                val a = c.rng.nextDouble() * 2 * PI
                val side = Vec3(cos(a), 0.0, sin(a)).rejectFrom(n).normalized(Vec3.X)
                // the outer crown first, then further up toward the tips when the rock is close round the colony
                val up = if (i < 6) c.rng.range(0.55, 0.9) else c.rng.range(0.8, 1.1)
                val p = k.pos + n * (r * up) + side * (r * c.rng.range(0.1, 0.5))
                val p2 = c.tailInside(c.offGlass(p, L + 0.5), side)
                if (c.rock.sdf.dist(p2) < clear || c.rock.sdf.dist(p2 - side * L) < clear || p2.y > c.rock.waterLevelCm - SURFACE_GAP_CM) continue
                perch = p2; perchOut = side
                return
            }
        }
        val tops = rockTops.filter { it.pos.y < c.rock.waterLevelCm - SURFACE_GAP_CM }
        val t = rockPerch?.let { rp -> tops.minByOrNull { it.pos.distanceTo(rp) } } ?: tops.getOrNull(c.rng.nextInt(tops.size.coerceAtLeast(1)))
        val n = (t?.normal ?: Vec3.Y).normalized(Vec3.Y)
        val out = Vec3(0.0, 0.0, 1.0).rejectFrom(n).normalized(Vec3.Z)
        perch = c.safe((t?.pos ?: Vec3(c.rock.lengthCm * 0.5, c.rock.sandTopCm + 12.0, c.rock.widthCm * 0.5)) + n * clear, clear)
        rockPerch = perch
        perchOut = out
    }

    // ================================================================ behaviours

    /**
     * Sitting in the branches (research 51 §3 facts 92-93): propped on its pelvic disc, head turning; now and then a short
     * hop to another spot on the same colony. At night it stays tucked in.
     */
    inner class Perch : Behavior("cperch", "Perched in its coral") {
        private var nextMove = 0.0
        private var nextLook = 0.0
        private var lookYaw = 0.0
        override val label: String get() = when { c.p.night -> "Asleep in its coral"; coral == null -> "Perched on the rock"; else -> "Perched in its coral" }
        override fun score(c: Ctx) = 0.5
        override fun enter(c: Ctx) { nextMove = c.t + c.rng.range(MOVE_MIN_S, MOVE_MAX_S) }
        override fun update(c: Ctx, out: SteerIntent) {
            val night = c.p.night
            if (!night && c.t > nextMove) { nextMove = c.t + c.rng.range(MOVE_MIN_S, MOVE_MAX_S); newPerch(c); hops++ }
            val d = o.pos.distanceTo(perch)
            if (d > HOP_CM) { out.seek(c.viaOpenWater(perch), o.cruiseSpeed * 1.2, arrive = 1.0); return }
            if (c.t > nextLook) { nextLook = c.t + c.rng.range(0.8, 3.0); lookYaw = c.rng.signed() * 0.7 }
            val face = Vec3.rotateAbout(perchOut, Vec3.Y, if (night) 0.0 else lookYaw)
            // the hop in: a quick slide along a short arc; then sitting still
            out.place(perch, face, Vec3.Y, if (d > 0.5) 3.0 else 8.0)
            out.posture = if (night) 3 else 1
            out.pectoral = 0.6; out.ampMul = 0.1; out.tailMul = 0.2
            o.eyeYaw = if (night) 0.0 else lookYaw * 0.4
            o.sleeping = night
        }
    }

    /** A copepod or a pinch of baby brine drifting within [SNAP_CM] of its perch: a dart out, a snap, and back (fact 102-103). */
    inner class Snap : Feed(this@ClownGobyBrain, SNAP_CM + 4.0, "Snapping at plankton") {
        override fun accept(c: Ctx, f: FoodParticle): Boolean = !f.settled && !f.onSurface && f.pos.distanceTo(perch) < SNAP_CM
        override fun exit(c: Ctx) { super.exit(c); snaps++ }
    }

    /**
     * Two on one colony (research 51 §3 facts 89, 91, 121): short chases and flank-to-flank shoving for a while; after a
     * few bouts they settle as a pair, the bigger one turning male, and share the colony.
     */
    inner class Squabble : Behavior("squabble", "Squabbling over its coral") {
        private var who: Organism? = null
        private var nextLook = 0.0
        private var nextBout = 0.0
        override val committed: Boolean get() = !finished
        override fun score(c: Ctx): Double {
            if (c.p.night || mate != null || c.t < nextBout) return 0.0
            if (c.t >= nextLook) {
                nextLook = c.t + 2.0
                who = c.p.neighbors(perch, SQUABBLE_CM).firstOrNull { it !== o && it.alive && it.brainKind == BrainKind.CLOWN_GOBY && it.visible }
            }
            return if (who != null) 0.55 else 0.0
        }
        override fun enter(c: Ctx) { squabbles++ }
        override fun exit(c: Ctx) {
            nextBout = c.t + c.rng.range(BOUT_GAP_MIN_S, BOUT_GAP_MAX_S)
            val w = who ?: return
            val wb = c.sim.agentOf(w)?.brain as? ClownGobyBrain ?: return
            if (squabbles >= PAIR_AFTER && wb.mate == null) {
                // they pair: the bigger becomes (or stays) male; the pair shares one colony
                mate = w; wb.mate = o
                o.mateId = w.id; w.mateId = o.id
                val iAmBigger = o.lengthCm >= w.lengthCm
                o.sexFemale = !iAmBigger; w.sexFemale = iAmBigger
                if (wb.coral != coral) { wb.coral = coral; wb.newPerch(wb.c) }
            }
        }
        override fun update(c: Ctx, out: SteerIntent) {
            val w = who ?: run { finished = true; return }
            out.seek(w.pos, o.burstSpeed * 0.4, arrive = 0.4, urgency = 0.6)
            if (o.pos.distanceTo(w.pos) < 0.6 * (L + w.lengthCm)) {
                c.sim.agentOf(w)?.let { it.brain.onAttacked(it.ctx, o.pos, 0.15, o) }
                finished = true
            }
            if (elapsed > 4.0) finished = true
        }
    }

    companion object {
        /** It moves to another spot on its colony this often (s), and swims rather than hops beyond [HOP_CM] [L]. */
        @Ev(Evidence.DESIGN_ESTIMATE, "51 §3 fact 93: sedentary") const val MOVE_MIN_S = 60.0
        const val MOVE_MAX_S = 400.0
        const val HOP_CM = 10.0
        /** Its perch stays this far (cm) under the water line. */
        const val SURFACE_GAP_CM = 6.0
        /** Plankton within this far of its perch (cm) gets a dart [L]. */
        @Ev(Evidence.DESIGN_ESTIMATE, "51 §3 facts 102-103") const val SNAP_CM = 8.0
        /** Another clown goby this close to its perch (cm) means a squabble; they pair after [PAIR_AFTER] bouts [L]. */
        @Ev(Evidence.DESIGN_ESTIMATE, "51 §3 facts 89, 91") const val SQUABBLE_CM = 12.0
        const val PAIR_AFTER = 4
        const val BOUT_GAP_MIN_S = 60.0
        const val BOUT_GAP_MAX_S = 300.0

        /**
         * The tree coral it lives in: one where a single unpaired clown goby lives (they squabble, then pair, as two added
         * together do), else the biggest healthy branching colony no clown goby holds.
         */
        private fun pickCoral(c: Ctx): Coral? {
            val others = c.sim.organisms.filter { it !== c.o && it.alive && it.brainKind == BrainKind.CLOWN_GOBY }
                .mapNotNull { c.sim.agentOf(it)?.brain as? ClownGobyBrain }
            val trees = c.sim.corals.filter { it.tissue > 0.3 && CoralGrowth.growth(it.kind).form == CoralGrowth.Form.TREE }
            val single = trees.firstOrNull { k -> others.count { it.coral === k } == 1 && others.first { it.coral === k }.mate == null }
            if (single != null) return single
            return trees.filter { k -> others.none { it.coral === k } }.maxByOrNull { it.size } ?: trees.maxByOrNull { it.size }
        }
    }
}
