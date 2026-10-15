package com.reeflive.core.ai

import com.reeflive.core.geom.Vec3
import com.reeflive.core.sim.AnemoneModel
import com.reeflive.core.sim.AnemoneRig
import com.reeflive.core.sim.FoodKind
import com.reeflive.core.sim.FoodParticle
import com.reeflive.core.sim.Organism
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

// =====================================================================================
// Bubble-tip anemone (Entacmaea quadricolor; research 40). The slow life (walking, settling, bubbles, deflation,
// bleaching, splitting, coral stings, clown acclimation) is AnemoneModel on the chemistry tick; this brain does the
// second-to-second part: holding the disc on its column, catching food and passing it to the mouth, stinging fish
// that blunder in, flinching when touched, and the tentacle pose the renderer draws (AnemoneRig).
// =====================================================================================
class AnemoneBrain(c: Ctx) : SpeciesBrain(c) {
    private val model: AnemoneModel get() = c.sim.anemones
    private val st: AnemoneModel.State get() = model.state(o)

    /** Visible contraction 0 open .. 1 deflated (eases to the model's target), and a touch flinch on top. */
    var contract = 0.5; private set
    var flinch = 0.0; private set
    private var held: FoodParticle? = null
    private var heldFrom = Vec3.ZERO
    private var heldAt = 0.0
    private var heldFor = 60.0
    private var curl = 0.0
    private var curlAngle = 0.0
    private var nextScan = 0.0
    private var pushPos = Vec3.ZERO
    private var pushR = 0.0
    private var push = 0.0
    private var placedAt = Vec3.ZERO
    private val lastSting = HashMap<String, Double>()
    /** Fish stung so far (tests, HUD). */
    var stings = 0; private set
    var meals = 0; private set

    init { install(Live()) }

    override fun restSpot(c: Ctx, night: Boolean): Rest {
        val s = st
        val up = model.discUp(s)
        return Rest(model.discCenter(o, s, contract), up, Vec3.Z.normalized(Vec3.Z), posture = 1)
    }

    override fun initialPlace(c: Ctx) {
        val s = st
        // a newcomer lands somewhere on the rock (Stocking / the bag may move it after; preTick follows)
        val anchor = o.placement?.let { c.rock.anchors[it] }
        if (anchor != null) {
            val (p, n) = model.surfaceUnder(anchor.pos); s.foot = p; s.normal = n
        } else if (o.pos == Vec3.ZERO || c.rock.sdf.dist(o.pos) > 8) {
            var best: Pair<Vec3, Vec3>? = null
            var bs = -1.0
            repeat(16) {
                val q = Vec3(o.rng.range(10.0, c.rock.lengthCm - 10), o.rng.range(c.rock.sandTopCm + 4, c.rock.waterLevelCm - 10), o.rng.range(5.0, c.rock.widthCm - 5))
                val (p, n) = model.surfaceUnder(q)
                if (n.y < 0.1 || !c.rock.inWater(p, 3.0)) return@repeat
                val sc = model.siteScore(o, p, n, 0.7)
                if (sc > bs) { bs = sc; best = p to n }
            }
            best?.let { s.foot = it.first; s.normal = it.second }
        }
        settle()
    }

    private fun settle() {
        val s = st
        val up = model.discUp(s)
        o.homePos = model.discCenter(o, s, contract)
        o.placeAt(o.homePos, up)
        o.up = up
        placedAt = o.pos
    }

    override fun preTick(c: Ctx) {
        super.preTick(c)
        // moved from outside (the bag dropped it, a hand or the shelf moved it): grip where it is now and look again
        if (o.pos.distanceTo(placedAt) > 3.0) {
            val (p, n) = model.surfaceUnder(o.pos)
            val s = st
            s.foot = p; s.normal = n; s.settled = false; s.target = null; s.dwellMin = 0.0
            contract = max(contract, 0.6)
            settle()
        }
    }

    override fun onTap(c: Ctx, at: Vec3, s: Double) { super.onTap(c, at, s); flinch = max(flinch, 0.5 * s + 0.2) }
    override fun onTouch(c: Ctx, at: Vec3, kind: com.reeflive.core.sim.TouchReaction, s: Double) {
        super.onTouch(c, at, kind, s)
        if (at.distanceTo(o.pos) < 0.6 * o.lengthCm + 4) flinch = 1.0
    }
    override fun onShadow(c: Ctx, at: Vec3) { /* no eyes; ignores passing shadows */ }

    /** Anything meaty that drifts into the tentacles (not algae or the fine coral foods). */
    private fun catchable(f: FoodParticle): Boolean = !f.eaten && f.claimedBy == null && when (f.kind) {
        FoodKind.MYSIS, FoodKind.KRILL, FoodKind.PRAWN, FoodKind.PELLET, FoodKind.BRINE, FoodKind.FLAKE, FoodKind.FLOATING_PELLET -> true
        else -> false
    }

    inner class Live : Behavior("anemone", "Anemone") {
        override val noisy: Boolean get() = false
        override val label: String get() {
            val s = st
            return when {
                held != null -> "Passing food to its mouth"
                flinch > 0.4 -> "Pulled in its tentacles"
                c.t < s.deflateUntil -> "Deflated, flushing out waste"
                s.healing > 0.9 -> "Healing after splitting in two"
                !s.settled && s.target != null -> "Walking on its foot to a better spot"
                !s.settled -> "Looking for a crevice to settle in"
                contract > 0.5 && c.p.night -> "Partly closed for the night"
                s.bubble > 0.6 -> "Bubbled up and settled"
                else -> "Settled, tentacles out"
            }
        }
        override fun score(c: Ctx) = 0.5
        override fun update(c: Ctx, out: SteerIntent) = live(c, out)
    }

    private fun live(c: Ctx, out: SteerIntent) {
        val s = st
        val dt = c.dt
        // contraction: deflating and flinching are quick, opening back up takes most of an hour
        flinch *= exp(-dt / 45.0)
        val target = max(s.contractTarget, min(1.0, flinch))
        val tau = if (target > contract) (if (flinch > 0.3) 1.5 else 900.0) else 2400.0
        contract += (target - contract) * min(1.0, dt / tau)
        o.extension = 1 - contract
        val up = model.discUp(s)
        val disc = model.discCenter(o, s, contract)
        out.place(disc, up, Vec3.Z, rate = 2.0)
        out.posture = 1
        placedAt = disc
        o.homePos = disc
        // a gaping mouth is the classic distress sign (research 40 §4.5)
        if (o.stress > 0.6) o.mouth = max(o.mouth, 0.6)

        if (c.t >= nextScan) {
            nextScan = c.t + 0.25
            scan(c, disc, up)
        }
        feed(c, disc, up)
        push *= exp(-dt / 0.8)
        curl += ((if (held != null) 1.0 else 0.0) - curl) * min(1.0, dt / 6.0)
        (o.limbRig as? AnemoneRig)?.set(s.bubble, contract, curl, curlAngle, pushPos, pushR, push, s.bleach, up)
    }

    /** Fish in the crown: hosting clowns nestle (the tentacles part around them); anything else gets stung. */
    private fun scan(c: Ctx, disc: Vec3, up: Vec3) {
        val r = model.crownRadius(o, contract)
        var best: Organism? = null
        var bd = Double.MAX_VALUE
        for (n in c.p.neighbors(disc, r + 8)) {
            if (n === o || !n.alive || !n.isFish) continue
            val rel = n.pos - disc
            val h = rel dot up
            if (h < -1.5 || h > 0.8 * r + 0.3 * n.lengthCm) continue
            val side = (rel - up * h).length()
            if (side > r + 0.3 * n.lengthCm) continue
            val d = rel.length()
            if (d < bd) { bd = d; best = n }
            if (!model.welcomes(o, n) && contract < 0.85) sting(c, n, disc)
        }
        best?.let { n -> pushPos = n.pos; pushR = 0.6 * n.lengthCm + 2.0; push = 1.0 }
    }

    private fun sting(c: Ctx, n: Organism, disc: Vec3) {
        if (c.t - (lastSting[n.id] ?: -1e9) < 4.0) return
        lastSting[n.id] = c.t
        stings++
        // a clown still testing the anemone gets a mild sting and darts off; anything else bolts
        val testing = model.candidateOf(n) === o
        val mag = if (testing) 0.3 else 0.7
        c.sim.agentOf(n)?.let { it.brain.onAttacked(it.ctx, disc, mag, o) }
        n.stress = min(1.0, n.stress + if (testing) 0.005 else 0.03)
        flinch = max(flinch, if (testing) 0.15 else 0.35)
    }

    private fun feed(c: Ctx, disc: Vec3, up: Vec3) {
        val s = st
        val f = held
        if (f == null) {
            if (contract > 0.6 || o.hunger < 0.02) return
            val reach = model.crownRadius(o, contract) * 1.1
            val g = c.food.nearest(disc + up * (0.25 * reach), reach) { catchable(it) } ?: return
            if ((g.pos - disc) dot up < -2.0) return
            g.claimedBy = o.id
            held = g; heldFrom = g.pos; heldAt = c.t
            // capture-to-mouth 30-120 s (research 40 §4.2: no measurement; DESIGN ESTIMATE)
            heldFor = o.rng.range(30.0, 120.0) * (if (g.kind == FoodKind.PRAWN || g.kind == FoodKind.KRILL) 1.5 else 1.0)
            curlAngle = sideAngle(g.pos - disc, up)
            return
        }
        if (f.eaten) { held = null; return }
        val k = ((c.t - heldAt) / heldFor).coerceIn(0.0, 1.0)
        val e = k * k * (3 - 2 * k)
        f.pos = heldFrom + (disc + up * 0.4 - heldFrom) * e
        f.vel = Vec3.ZERO
        if (k >= 1.0) {
            c.food.consume(f)
            o.eat(f.grams * o.species.foodPreference(f.kind).coerceAtLeast(0.5))
            o.mouth = 1.0
            s.fed = min(1.0, s.fed + 0.35)
            s.lastMealAt = c.t
            meals++
            held = null
        }
    }

    companion object {
        /** Angle of [d] about the disc axis [up] in the renderer's model frame ([AnemoneRig.basis]). */
        fun sideAngle(d: Vec3, up: Vec3): Double {
            val (x, z) = AnemoneRig.basis(up)
            return atan2(d dot z, d dot x)
        }
    }
}
