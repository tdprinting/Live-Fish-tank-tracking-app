package com.reeflive.core.ai

import com.reeflive.core.geom.Vec3
import com.reeflive.core.sim.BrainKind
import com.reeflive.core.sim.Counters
import com.reeflive.core.sim.Ev
import com.reeflive.core.sim.Evidence
import com.reeflive.core.sim.FoodParticle
import com.reeflive.core.sim.HealthModel
import com.reeflive.core.sim.HoleKind
import com.reeflive.core.sim.Organism
import com.reeflive.core.sim.PodPredator
import com.reeflive.core.sim.RockHole
import com.reeflive.core.sim.SimEvent
import com.reeflive.core.sim.Simulation
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Mandarin dragonet, *Synchiropus splendidus* (docs/research/51_popular_fish_batch2.md §1). A slow bottom fish that
 * spends the whole day hopping along the lower rock and the rubble on its big pelvic fins, hovering a moment over each
 * spot to peck copepods and amphipods off it, a peck every second or so. Its food is the tank's own microfauna
 * ([com.reeflive.core.sim.MicrofaunaModel], row [PodPredator.MANDARIN]): in a young tank, or with other pod eaters
 * (the leopard wrasse, the diamond goby), it slowly starves unless it is a captive-bred fish that learnt frozen food
 * (research 51 §1 facts 22-34). Shy: it keeps low, and slips into the rock at night.
 *
 * A male and a female pair: at dusk the male raises his tall first dorsal and circles her, then the two rise together
 * in a slow spiral to just under the surface and release their eggs (Gensou; Practical Fishkeeping), and drift back down.
 */
class MandarinBrain(c: Ctx) : SpeciesBrain(c) {
    private var home0: RockHole? = c.rock.holes.claimHome(o, KINDS, defaultNear(c), c.sim.holeClaims)
    /** Its night crevice now (rides with its rock when the rocks shift). */
    val home: RockHole? get() = c.rock.holes.live(home0)
    private val shelterAt: Vec3 = c.rock.sdf.project(home0?.mouth ?: defaultNear(c), 0.5)
    val den: Vec3 get() = home?.mouth ?: shelterAt

    var pecks = 0; private set
    var lastYield = 0.0; private set
    var spawns = 0; private set
    /** The mate it is rising with now (both sides), and the spawn's phase: 0 none, 1 courting, 2 rising, 3 sinking. */
    var spawnWith: Organism? = null; private set
    var spawnPhase = 0; private set
    private var lastSpawnNight = -1
    private var rolledNight = -1
    private var tonight = false

    init {
        o.spline.glassGuard = true
        install(PodHop(), MandarinFeed(), DuskSpawn(), SpawnFollow(),
            Flee(this, { cc, from -> cc.coverPoint(from) }, "Slipping into cover"),
            InHole(this, home, den, "Resting in the rock", { c ->
                val o = c.o
                val sick = if (o.health < 0.6 || o.disease != null && o.diseaseStage > 0.3) 1.0 else 0.0
                (0.8 * (1 - c.activity) + 0.25 * o.stress + 0.3 * sick).coerceAtMost(0.9)
            }, { c -> if (c.p.night) -0.6 else 0.3 }),
            SurfaceGasp(this))
    }

    override fun restSpot(c: Ctx, night: Boolean): Rest =
        home?.let { holeRest(c, it, if (night) -0.6 else 0.3) } ?: rockShelterRest(c, den)

    override fun shelterSpot(c: Ctx): Vec3? = c.safe(den + (home?.axis ?: Vec3.Y) * (c.clearance + 0.5))

    override fun preTick(c: Ctx) {
        super.preTick(c)
        if (o.sexFemale == null) o.sexFemale = if (o.role != null) !o.role.equals("MALE", true) else o.rng.chance(0.5)
        if (o.mateId == null) mate(c)?.takeIf { it.mateId == null }?.let { m -> o.mateId = m.id; m.mateId = o.id }
        // the male's tall first dorsal goes up in the courtship and stays half raised while he rises
        o.dorsalRaise = when { o.sexFemale != false -> 0.0; spawnPhase == 1 -> 1.0; spawnPhase == 2 -> 0.6; else -> 0.0 }
    }

    /** A mandarin of the other sex in the tank. */
    fun mate(c: Ctx): Organism? = c.sim.organisms.firstOrNull {
        it !== o && it.alive && it.brainKind == BrainKind.MANDARIN && it.sexFemale != null && it.sexFemale != o.sexFemale
    }

    private fun eatPeck(c: Ctx) {
        val prey = preyIndex(c.sim)
        val y = HealthModel.hungerRatePerMin(o, c.sim.water.s.tempC) * PECK_YIELD_MIN * prey
        lastYield = y
        if (y > 0) {
            o.hunger = max(0.0, o.hunger - y)
            if (prey > 0.2) o.counters.add(Counters.K.FEED, c.t)
        }
    }

    /** Daylight in abstract mode (> 240×): pecking as a rate on the tank's pods. */
    fun abstractPick(dtMin: Double) {
        if (!o.visible) return
        o.hunger = max(0.0, o.hunger - HealthModel.hungerRatePerMin(o, c.sim.water.s.tempC) * ABSTRACT_RELIEF * preyIndex(c.sim) * dtMin)
        pecks += (30 * dtMin).toInt()
    }

    /** The evening ramp (lights going down, not yet out): the spawning window (research 51 §1 fact 39). */
    private fun dusk(c: Ctx): Boolean {
        val hour = (c.t / 3600.0) % 24.0
        return !c.p.night && hour >= 12.0 && c.p.light in DUSK_LIGHT_LO..DUSK_LIGHT_HI
    }

    // ================================================================ behaviours

    /**
     * The pod hunt (research 51 §1 facts 15, 22-23): a short hop to a spot a few cm along the lower rock or the sand by
     * it, a hover there a body depth off the surface with the pectorals fanning, head down, pecking every second or so;
     * a few pecks, then on to the next spot. It keeps to the bottom third of the tank.
     */
    inner class PodHop : Behavior("podhop", "Hopping along the rock for pods") {
        private var spot = Vec3.ZERO
        private var stand = Vec3.ZERO
        private var nextPeck = 0.0
        private var left = 0
        private var bestD = Double.MAX_VALUE
        private var progressAt = 0.0
        private var pathDir = Vec3.X
        override fun score(c: Ctx): Double = if (c.p.night || dusk(c) && spawnPhase > 0) 0.0 else ((0.55 + 0.3 * o.hunger) * max(c.activity, 0.3)).coerceAtMost(0.8)
        override fun enter(c: Ctx) { newSite(c) }

        private fun newSite(c: Ctx) {
            bestD = Double.MAX_VALUE; progressAt = c.t
            left = PECKS_MIN + c.rng.nextInt(PECKS_MAX - PECKS_MIN + 1)
            nextPeck = c.t + c.rng.range(0.5, 1.2)
            val top = c.rock.sandTopCm + LOW_CM
            for (attempt in 0 until 5) {
                pathDir = Vec3.rotateAbout(pathDir, Vec3.Y, c.rng.signed() * 1.0).horizontal().normalized(Vec3.X)
                val home = den - o.pos
                if (home.horizontal().length() > RANGE_CM) pathDir = (pathDir + home.horizontal().normalized(Vec3.X) * 1.5).normalized(Vec3.X)
                val from = if (spot == Vec3.ZERO) o.pos else spot
                val centre = from + pathDir * c.rng.range(HOP_MIN_CM, HOP_MAX_CM)
                val sp = c.rockSurfacePoint(Vec3(centre.x, centre.y.coerceAtMost(top), centre.z), 6.0) ?: continue
                if (sp.first.y > top && attempt < 4) continue
                val want0 = sp.first + sp.second * (STAND_OF_L * o.lengthCm + 1.0)
                // never down in a dip of the sand bed (its tail would trail through the sand round it)
                val want = want0.withY(maxOf(want0.y, c.rock.sandTopCm + SAND_CLEAR_OF_L * o.lengthCm + 0.5))
                val st = c.safe(want, 0.6 * o.collisionLength)
                if (st.distanceTo(want) > 1.5) continue
                spot = sp.first; stand = st
                return
            }
            spot = den; stand = c.safe(den + Vec3.Y * (c.clearance + 0.5))
        }

        override fun update(c: Ctx, out: SteerIntent) {
            val d = o.pos.distanceTo(stand)
            if (d < bestD - 0.8) { bestD = d; progressAt = c.t }
            if (d > 1.5 && c.t - progressAt > 6.0) { newSite(c); return }
            if (d > 1.5) {
                // the hop: a short glide, low over the rock, nose down
                out.seek(if (d > 12) c.viaOpenWater(stand) else stand, o.cruiseSpeed * HOP_SPEED, arrive = 1.2)
                out.hover = true; out.pectoral = 1.0
                if (d < 10) out.pitchBias = -0.2
                return
            }
            // hovering over the spot on fanning pectorals, head down, pecking
            out.seek(stand, o.cruiseSpeed * 0.3, arrive = 1.0)
            out.hover = true; out.pectoral = 1.0; out.ampMul = 0.3; out.tailMul = 0.3
            out.faceDir = (spot - o.pos).normalized(o.heading)
            out.pitchBias = -0.45 + 0.08 * sin(c.t * 2.0)
            o.eyeYaw = 0.3 * sin(c.t * 0.7 + o.appendagePhase)
            if (c.t >= nextPeck) {
                nextPeck = c.t + c.rng.range(PECK_MIN_S, PECK_MAX_S)
                o.mouth = 1.0; pecks++
                eatPeck(c)
                if (--left <= 0) { if (elapsed > 120 + 240 * c.rng.nextDouble()) finished = true else newSite(c) }
            }
        }
    }

    /**
     * Prepared food: only a fish that learnt it (captive-bred, research 51 §1 facts 32-34), and only what settles near
     * the bottom close by; it is a slow eater, so faster fish get most of it.
     */
    inner class MandarinFeed : Feed(this@MandarinBrain, 25.0, "Pecking at frozen food") {
        override fun accept(c: Ctx, f: FoodParticle): Boolean = !f.onSurface && f.pos.y < c.rock.sandTopCm + LOW_CM + 4.0
        override fun scoreMul(c: Ctx, f: FoodParticle): Double = 0.6
    }

    /**
     * The male's spawning rise (research 51 §1 facts 39-41): in the evening, well fed, with a female in the tank, every
     * few nights: he swims to her, raises his tall dorsal and circles her for a minute or so; then the two rise side by
     * side in a slow spiral to [SPAWN_UNDER_CM] under the surface, release the eggs and sink back to the rock.
     */
    inner class DuskSpawn : Behavior("spawn", "Courting at dusk") {
        private var phaseAt = 0.0
        private var top = Vec3.ZERO
        private var base = Vec3.ZERO
        override val committed: Boolean get() = !finished
        override val label: String get() = when (spawnPhase) { 2 -> "Rising to spawn"; 3 -> "Sinking back down"; else -> "Courting at dusk" }
        override fun score(c: Ctx): Double {
            if (o.sexFemale != false || !dusk(c)) return 0.0
            val night = (c.t / 86400.0).toInt()
            if (night == lastSpawnNight || o.hunger > 0.4 || o.stress > 0.4 || o.lengthCm < (o.species.lifeStages?.maturityCm ?: 5.0)) return 0.0
            val f = mate(c) ?: return 0.0
            if (f.hunger > 0.5 || f.stress > 0.5 || !f.visible || f.posture == 3) return 0.0
            // every few nights, not every night: one roll per evening [L]
            if (night != rolledNight) { rolledNight = night; tonight = c.rng.chance(SPAWN_NIGHT_CHANCE) }
            if (!tonight) return 0.0
            spawnWith = f
            return 0.7
        }
        override fun enter(c: Ctx) {
            lastSpawnNight = (c.t / 86400.0).toInt()
            spawnPhase = 1; phaseAt = c.t
            val f = spawnWith ?: return
            (c.sim.agentOf(f)?.brain as? MandarinBrain)?.join(o)
        }
        override fun exit(c: Ctx) {
            val f = spawnWith
            spawnPhase = 0; spawnWith = null
            f?.let { (c.sim.agentOf(it)?.brain as? MandarinBrain)?.leave() }
        }
        override fun update(c: Ctx, out: SteerIntent) {
            val f = spawnWith ?: run { finished = true; return }
            if (!f.alive || c.p.night && spawnPhase < 2) { finished = true; return }
            when (spawnPhase) {
                1 -> {
                    // circling her a body length or so off, dorsal up
                    val a = (c.t - phaseAt) * 0.9
                    val ring = Vec3(cos(a), 0.0, sin(a)) * (1.2 * c.L)
                    out.seek(c.safe(f.pos + ring), o.cruiseSpeed * 0.8, arrive = 0.8)
                    out.faceDir = (f.pos - o.pos).normalized(o.heading)
                    out.hover = true; out.pectoral = 1.0
                    if (c.t - phaseAt > COURT_S) {
                        spawnPhase = 2; phaseAt = c.t
                        base = c.safe(f.pos)
                        top = c.openAt(base.x, base.z, c.rock.waterLevelCm - SPAWN_UNDER_CM, c.L)
                    }
                }
                2 -> {
                    // the slow rise, the pair turning about each other as they go
                    val k = ((c.t - phaseAt) / RISE_S).coerceIn(0.0, 1.0)
                    val a = (c.t - phaseAt) * 2 * PI / 8.0
                    // up and out over the reef when rock is overhead (no ledge to stick under), turning about each other
                    val p = base.lerp(top, k) + Vec3(cos(a), 0.0, sin(a)) * (0.5 * c.L)
                    out.seek(c.viaOpenWater(c.safe(p)), o.cruiseSpeed * 0.9, arrive = 0.8)
                    out.hover = true; out.pectoral = 1.0
                    if (k >= 1.0 && o.pos.distanceTo(top) < 3.0 + c.L || c.t - phaseAt > RISE_S + 40) {
                        spawns++
                        c.sim.emit(SimEvent.EggsLaid("mandarin", c.rng.range(EGGS_MIN, EGGS_MAX).toInt(), c.t))
                        spawnPhase = 3; phaseAt = c.t
                    }
                }
                3 -> {
                    out.seek(c.viaOpenWater(c.safe(den + Vec3.Y * (c.clearance + 0.5))), o.cruiseSpeed, arrive = 2.0)
                    if (c.t - phaseAt > 20 || o.pos.y < c.rock.sandTopCm + LOW_CM) finished = true
                }
            }
        }
    }

    /** Tests: this evening is a spawning evening (skips the every-few-nights roll). */
    internal fun spawnTonight(t: Double) { rolledNight = (t / 86400.0).toInt(); tonight = true }

    /** The female side of the rise: the courted female holds still, then rises at the male's side. */
    private fun join(male: Organism) { spawnWith = male; spawnPhase = 1 }
    private fun leave() { spawnWith = null; spawnPhase = 0 }

    inner class SpawnFollow : Behavior("spawnf", "Rising with her mate") {
        override fun score(c: Ctx): Double = if (o.sexFemale != false && spawnWith != null) 0.75 else 0.0
        override val label: String get() = if (spawnPhaseOf(spawnWith) == 1) "Being courted" else "Rising with her mate"
        override fun update(c: Ctx, out: SteerIntent) {
            val m = spawnWith ?: run { finished = true; return }
            if (!m.alive) { leave(); finished = true; return }
            val ph = spawnPhaseOf(m)
            if (ph == 0) { leave(); finished = true; return }
            if (ph == 1) {
                // she hovers still and faces him as he circles
                out.seek(o.pos, o.cruiseSpeed * 0.2, arrive = 1.0)
                out.faceDir = (m.pos - o.pos).horizontal().normalized(o.heading)
                out.hover = true; out.pectoral = 1.0
            } else {
                // belly to belly beside him, a little to one side
                val side = Vec3(-m.heading.z, 0.0, m.heading.x).normalized(Vec3.X) * (0.5 * c.L)
                out.seek(c.safe(m.pos + side), o.cruiseSpeed * 1.1, arrive = 0.6)
                out.hover = true; out.pectoral = 1.0
                spawnPhase = ph
            }
        }
        override fun exit(c: Ctx) { spawnPhase = 0 }
        private fun spawnPhaseOf(m: Organism?): Int = (m?.let { c.sim.agentOf(it)?.brain } as? MandarinBrain)?.spawnPhase ?: 0
    }

    companion object {
        /** Pecks per spot, seconds between pecks, and the hop to the next spot (cm) at this × cruise speed [L]. */
        @Ev(Evidence.DESIGN_ESTIMATE, "51 §1 facts 15, 23: hops along rubble, thousands of pods a day") const val PECKS_MIN = 2
        const val PECKS_MAX = 7
        const val PECK_MIN_S = 0.7
        const val PECK_MAX_S = 1.6
        const val HOP_MIN_CM = 2.0
        const val HOP_MAX_CM = 7.0
        const val HOP_SPEED = 1.0
        /** Hover distance off the surface it pecks (× body length + 1 cm). */
        const val STAND_OF_L = 0.25
        /** Its hover spot stays this × body length (+ 0.5 cm) over the sand bed's level. */
        const val SAND_CLEAR_OF_L = 0.25
        /** It keeps within this height over the sand (cm), and this far round its crevice (cm) [L]. */
        const val LOW_CM = 14.0
        const val RANGE_CM = 45.0
        /**
         * Hunger relief per peck at prey index 1 (minutes of hunger rise). At ~30 pecks per foraging minute, foraging about
         * 70 % of the day, this breaks even near prey index 0.5: a mature tank feeds it, a young one starves it (51 §1
         * facts 24, 30-31) [L].
         */
        @Ev(Evidence.DESIGN_ESTIMATE, "51 §1 facts 24, 30-31") const val PECK_YIELD_MIN = 0.1
        const val ABSTRACT_RELIEF = 1.4
        /** The spawning window: evening light between these levels (the ramp before lights-out) [L]. */
        @Ev(Evidence.DESIGN_ESTIMATE, "51 §1 fact 39: 20-40 min before lights out") const val DUSK_LIGHT_LO = 0.05
        const val DUSK_LIGHT_HI = 0.45
        /** Chance that a ready pair spawns on a given evening (every few nights) [L]. */
        const val SPAWN_NIGHT_CHANCE = 0.35
        /** Courtship (s), the rise (s), and how far under the surface they release (cm) (51 §1 facts 39-40). */
        @Ev(Evidence.DESIGN_ESTIMATE, "51 §1 facts 39-40: rise to 5-15 cm under the surface") const val COURT_S = 60.0
        const val RISE_S = 30.0
        const val SPAWN_UNDER_CM = 8.0
        /** Eggs in one release (51 §1 fact 43). */
        @Ev(Evidence.SOURCED, "51 §1 fact 43: 30-150 buoyant eggs") const val EGGS_MIN = 30.0
        const val EGGS_MAX = 150.0
        private val KINDS = setOf(HoleKind.CREVICE, HoleKind.HOLE, HoleKind.CAVE, HoleKind.THROUGH)

        /** Pods it finds now (0 = dry rock, ≈ 1 = a mature tank's fauna). */
        fun preyIndex(sim: Simulation): Double = sim.water.micro.podPreyIndex(PodPredator.MANDARIN).coerceIn(0.0, 1.5)

        private fun defaultNear(c: Ctx): Vec3 =
            Vec3(c.rock.lengthCm * c.o.rng.range(0.3, 0.7), c.rock.sandTopCm + 3.0, c.rock.widthCm * 0.55)
    }
}
