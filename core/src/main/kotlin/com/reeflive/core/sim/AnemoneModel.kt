package com.reeflive.core.sim

import com.reeflive.core.geom.Rng
import com.reeflive.core.geom.Vec3
import com.reeflive.core.geom.smoothstep
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Bubble-tip anemones (*Entacmaea quadricolor*, research 40): the slow side, stepped on the chemistry tick so it runs
 * the same at 1x, 240x and in abstract catch-up. Each anemone glides on its foot over the rock until it finds a spot
 * with good light, moderate flow and a crevice for the foot, then settles; it bubbles or goes stringy, deflates now
 * and then to flush waste, bleaches in sustained heat, stings corals and other anemones in reach, and splits in two
 * when big and well fed. It also tracks the clownfish adopting it ([HostState]): a clown already hosting the hairy
 * mushrooms acclimates to a new anemone over days, the female first, and the pair then moves in.
 *
 * The fast side (catching food, stinging fish, how far the tentacles are open this second) is [com.reeflive.core.ai.AnemoneBrain].
 * Uses its own random stream, so tanks without an anemone draw exactly the numbers they did before.
 */
class AnemoneModel(private val sim: Simulation) {
    /** Per-anemone state, keyed by organism id. */
    class State(val id: String) {
        /** Where the pedal disc grips (on the rock or sand surface) and the surface normal there. */
        var foot = Vec3.ZERO
        var normal = Vec3.Y
        var target: Vec3? = null
        var settled = false
        /** Minutes spent at an acceptable spot while unsettled, and below the unsettle score while settled. */
        var dwellMin = 0.0
        var unhappyMin = 0.0
        var site = 0.5
        var lightScore = 0.5
        var flowCmS = 0.0
        var cover = 0.0
        /** Average daytime light the animal sees (fraction of the tank's full output, after depth and shade). */
        var dayLight = Double.NaN
        /** Smoothed tentacle shape 0 stringy .. 1 bubbled, and this animal's own tendency to bubble. */
        var bubble = 0.4
        var bubbliness = 0.8
        /** Slow contraction target 0 open .. 1 deflated (night, waste flush, digestion, stress); the brain eases to it. */
        var contractTarget = 0.0
        var deflateUntil = -1.0
        var nextDeflateAt = Double.NaN
        var lastMealAt = -1e9
        /** Recent feeding 0..1 (fades over about a week) and split recovery 1 → 0 over [SCAR_DAYS]. */
        var fed = 0.0
        var healing = 0.0
        var bleach = 0.0
        /** Degree-days above [BLEACH_C] (decays when cool), as the coral DHW but per day. */
        var heatDays = 0.0
        /** Clone line: anemones split from the same animal share it and do not sting each other. */
        var lineage = id
        var splits = 0
        /** Corals in reach this tick (ids), for the HUD and tests. */
        val stinging = ArrayList<String>()
        var stingingAnemone = false
    }

    /** A clownfish's relationship to the anemones in the tank. */
    class HostState(val clownId: String) {
        /** 0..1 acclimation to [candidateId]; at 1 the clown can host it. */
        var acclim = 0.0
        var candidateId: String? = null
        /** The anemone this clown now hosts (null = its old host, the mushrooms). */
        var hostId: String? = null
        /** Some ocellaris never take to a bubble tip (research 40 §7.1, §7.6). */
        var willing = true
        /** Days this clown needs, sampled once (hobby range: days to weeks). */
        var adoptDays = ADOPT_MEDIAN_DAYS
        var initialized = false
    }

    val states = LinkedHashMap<String, State>()
    val hosts = LinkedHashMap<String, HostState>()
    private var rng = Rng(0x5EA_A9E40L)
    private var seeded = false

    fun anemones(): List<Organism> = sim.organisms.filter { it.alive && it.brainKind == BrainKind.ANEMONE }

    fun state(o: Organism): State = states.getOrPut(o.id) {
        State(o.id).also { s ->
            ensureRng()
            s.bubbliness = rng.range(0.55, 1.0)
            val (p, n) = surfaceUnder(o.pos)
            s.foot = p; s.normal = n
        }
    }

    private fun ensureRng() { if (!seeded) { rng = Rng(sim.rng.nextLong() xor 0x5EA_A9E40L); seeded = true } }

    // ------------------------------------------------------------------ geometry

    /** Oral disc centre for an anemone gripping at [s].foot: the column holds the disc up toward the light. */
    fun discCenter(o: Organism, s: State = state(o), contract: Double = s.contractTarget): Vec3 {
        val up = discUp(s)
        val col = COLUMN_FRAC * o.lengthCm * (1 - 0.3 * contract)
        var p = s.foot + up * col
        // never inside the rock: lift along the column axis until the disc clears it
        var i = 0
        while (sim.rock.sdf.dist(p) < 0.4 && i++ < 8) p += up * 0.6
        return sim.rock.clampToWater(p, 1.0)
    }

    /** Disc axis: the rock normal bent toward the light (the disc faces up out of the crevice). */
    fun discUp(s: State): Vec3 = (s.normal + Vec3.Y * 1.6).normalized(Vec3.Y)

    /** Tentacle crown radius (cm) at the current opening. */
    fun crownRadius(o: Organism, contract: Double): Double = 0.5 * o.lengthCm * (0.45 + 0.55 * (1 - contract))

    /** Nearest rock (or sand) surface point under or around [p] and its outward normal. */
    fun surfaceUnder(p: Vec3): Pair<Vec3, Vec3> {
        val rock = sim.rock
        val sandY = sim.sand.surfaceY(p.x, p.z)
        val q = rock.sdf.project(p, 0.0)
        val onRock = rock.sdf.dist(q) < 0.6 && q.y > sandY + 0.3 && rock.inWater(q, 1.0)
        if (onRock && (q.distanceTo(p) < 6.0 || p.y > sandY + 4.0)) return q to rock.sdf.gradient(q)
        return Vec3(p.x, sandY, p.z) to Vec3.Y
    }

    /** Light here as a fraction of the fixtures' full output: depth falloff and shade from rock overhead. */
    fun lightFactor(foot: Vec3, normal: Vec3): Double {
        val rock = sim.rock
        val depth = (rock.waterLevelCm - foot.y).coerceAtLeast(0.0)
        val start = foot + normal * 1.5 + Vec3.Y * 1.0
        val shaded = rock.sdf.raycast(start, Vec3.Y, (rock.waterLevelCm - start.y).coerceAtLeast(0.1)) != null
        return exp(-DEPTH_K * depth) * (if (shaded) SHADE else 1.0)
    }

    /** How good a spot is (0..1): light in the preferred band, moderate flow, a crevice to hold the foot. */
    fun siteScore(o: Organism, foot: Vec3, normal: Vec3, dayLight: Double, out: State? = null): Double {
        val light = dayLight * lightFactor(foot, normal)
        val lightScore = when {
            light < LIGHT_LO -> smoothstep(LIGHT_LO * 0.4, LIGHT_LO, light)
            light > LIGHT_HI -> 1 - smoothstep(LIGHT_HI, LIGHT_HI + 0.35, light)
            else -> 1.0
        }
        val disc = foot + (normal + Vec3.Y * 1.6).normalized(Vec3.Y) * (COLUMN_FRAC * o.lengthCm)
        val flow = sim.flow.velocityFast(disc).length()
        val flowScore = when {
            flow < FLOW_LO -> 0.4 + 0.6 * smoothstep(0.0, FLOW_LO, flow)
            flow > FLOW_HI -> 1 - smoothstep(FLOW_HI, FLOW_HI * 2.2, flow)
            else -> 1.0
        }
        val onSand = foot.y <= sim.sand.surfaceY(foot.x, foot.z) + 0.4
        val cover = if (onSand) 0.0 else sim.rock.sdf.cover(foot + normal * 1.0)
        val crevice = smoothstep(0.12, 0.4, cover) * (1 - 0.5 * smoothstep(0.7, 0.95, cover))
        out?.let { it.lightScore = lightScore; it.flowCmS = flow; it.cover = cover }
        val s = 0.45 * lightScore + 0.3 * flowScore + 0.25 * crevice
        return if (onSand) s * 0.6 else s
    }

    // ------------------------------------------------------------------ chemistry-tick step

    fun step(dtMin: Double) {
        val list = anemones()
        if (list.isEmpty() && hosts.isEmpty()) return
        ensureRng()
        states.keys.retainAll(list.mapTo(HashSet()) { it.id })
        val w = sim.water
        val light = sim.lightIntensity * w.clarity
        val night = sim.lightIntensity < LightSchedule.LIGHTS_ON
        val t = sim.simTimeSec
        for (o in list) {
            val s = state(o)
            if (s.dayLight.isNaN()) s.dayLight = if (night) 0.7 else light.coerceAtLeast(0.3)
            if (!night) s.dayLight += (light - s.dayLight) * min(1.0, dtMin / 360.0)
            s.site = siteScore(o, s.foot, s.normal, s.dayLight, s)
            wander(o, s, dtMin)
            glide(o, s, dtMin)
            shape(o, s, night, dtMin, t)
            health(o, s, w.s.tempC, light, night, dtMin)
            stingNeighbours(o, s, list, dtMin)
        }
        clowns(list, night, dtMin)
        if (list.size < MAX_ANEMONES) for (o in list) if (maybeSplit(o, state(o), dtMin)) break
    }

    private fun wander(o: Organism, s: State, dtMin: Double) {
        val stressed = o.stress > 0.6
        if (s.settled) {
            s.unhappyMin = if (s.site < UNSETTLE_SCORE || stressed) s.unhappyMin + dtMin else max(0.0, s.unhappyMin - dtMin)
            if (s.unhappyMin > UNHAPPY_HOURS * 60) { s.settled = false; s.unhappyMin = 0.0; s.dwellMin = 0.0 }
            return
        }
        if (s.target != null) return
        if (s.site >= SETTLE_SCORE && !stressed) {
            s.dwellMin += dtMin
            if (s.dwellMin >= SETTLE_DWELL_H * 60) { s.settled = true; s.dwellMin = 0.0; return }
        }
        // look around for something better within a body length or so (only now and then: the foot is slow)
        if (!rng.chance(min(1.0, dtMin / LOOK_EVERY_MIN))) return
        var best: Pair<Vec3, Vec3>? = null
        var bestScore = s.site + 0.04
        val r = SEARCH_CM + 0.5 * o.lengthCm
        repeat(8) {
            val c = s.foot + Vec3(rng.signed() * r, rng.signed() * r * 0.6, rng.signed() * r * 0.6)
            val (p, n) = surfaceUnder(c)
            if (n.y < -0.1 || !sim.rock.inWater(p, 3.0) || p.y > sim.rock.waterLevelCm - 6) return@repeat
            val sc = siteScore(o, p, n, s.dayLight)
            if (sc > bestScore) { bestScore = sc; best = p to n }
        }
        best?.let { s.target = it.first; s.dwellMin = 0.0 }
        // nothing better close by and still unhappy: an exploratory walk
        if (best == null && s.site < SETTLE_SCORE && rng.chance(0.5)) {
            val c = s.foot + Vec3(rng.signed() * r * 1.5, rng.signed() * r * 0.5, rng.signed() * r * 0.8)
            val (p, n) = surfaceUnder(c)
            if (n.y > -0.1 && sim.rock.inWater(p, 3.0)) s.target = p
        }
    }

    /** The pedal disc creeps along the surface toward [State.target] at [GLIDE_CM_H]. */
    private fun glide(o: Organism, s: State, dtMin: Double) {
        val tgt = s.target ?: return
        val step = GLIDE_CM_H * dtMin / 60.0 * (if (s.contractTarget > 0.7) 0.3 else 1.0)
        val d = tgt - s.foot
        val dl = d.length()
        if (dl <= step) {
            s.foot = tgt; s.normal = surfaceUnder(tgt).second; s.target = null
            return
        }
        val (p, n) = surfaceUnder(s.foot + d * (step / dl))
        // stuck (the projection pulled it back): give up on this target
        if (p.distanceTo(tgt) >= dl - 0.05 * step) { s.target = null; return }
        s.foot = p; s.normal = n
    }

    private fun shape(o: Organism, s: State, night: Boolean, dtMin: Double, t: Double) {
        // waste flush: deflate fully every few days for a few hours (research 40 §3, anecdotal)
        if (s.nextDeflateAt.isNaN()) s.nextDeflateAt = t + rng.range(DEFLATE_EVERY_D.start, DEFLATE_EVERY_D.endInclusive) * 86400
        if (t >= s.nextDeflateAt && s.deflateUntil < t) {
            s.deflateUntil = t + rng.range(DEFLATE_H.start, DEFLATE_H.endInclusive) * 3600
            s.nextDeflateAt = s.deflateUntil + rng.range(DEFLATE_EVERY_D.start, DEFLATE_EVERY_D.endInclusive) * 86400
        }
        val deflating = t < s.deflateUntil
        if (!deflating && s.deflateUntil > 0 && t - s.deflateUntil < dtMin * 60) {
            // the flush: the water it squeezes out carries waste
            sim.water.addDetritusTanGrams(WASTE_G_PER_CM * o.lengthCm)
        }
        val sinceMeal = (t - s.lastMealAt) / 3600
        var c = if (night) NIGHT_CONTRACT else 0.0
        if (sinceMeal in POST_MEAL_H) c = max(c, 0.7)
        if (deflating) c = max(c, 0.92)
        c = max(c, 0.85 * smoothstep(0.35, 0.9, o.stress))
        if (s.healing > 1 - 0.25 / SCAR_DAYS) c = max(c, 0.6)
        if (!s.settled && s.target != null) c = max(c, 0.25)
        c = max(c, 0.35 * s.bleach)
        s.contractTarget = c
        // bubbles: bright, settled, fed and calm animals bubble; hungry ones stretch out stringy sweepers (research 40 §1.3:
        // the cause is not settled, so this is a soft mix of the suspected drivers)
        val calm = 1 - smoothstep(0.2, 0.7, o.stress)
        var b = s.bubbliness * (0.2 + 0.8 * s.lightScore) * calm * (0.75 + 0.25 * s.fed) * (if (s.settled) 1.0 else 0.75)
        if (o.hunger > 0.6) b *= 0.6
        b *= 1 - 0.6 * s.bleach
        s.bubble += (b.coerceIn(0.0, 1.0) - s.bubble) * min(1.0, dtMin / (BUBBLE_TAU_H * 60))
    }

    private fun health(o: Organism, s: State, tempC: Double, light: Double, night: Boolean, dtMin: Double) {
        val days = dtMin / 1440.0
        s.fed *= exp(-days / 7.0)
        s.healing = max(0.0, s.healing - days / SCAR_DAYS)
        // heat bleaching: degree-days above the threshold, visible from day 3-4 (research 40 §8.1, Hill & Scott 2012)
        s.heatDays = if (tempC > BLEACH_C) s.heatDays + (tempC - BLEACH_C) * days else max(0.0, s.heatDays - 0.5 * days)
        val local = if (night) 0.0 else light * lightFactor(s.foot, s.normal)
        // strong light makes heat bleaching worse; on its own only well above the comfortable band
        val lightBoost = 1 + smoothstep(LIGHT_HI, LIGHT_HI + 0.4, local)
        val target = max(smoothstep(3.0, 8.0, s.heatDays * lightBoost), 0.6 * smoothstep(LIGHT_HI + 0.15, LIGHT_HI + 0.6, local))
        val hosted = hosts.values.any { it.hostId == o.id }
        s.bleach += if (target > s.bleach) (target - s.bleach) * min(1.0, days / 3.0)
            else -min(s.bleach - target, days / RECOVER_DAYS * (if (hosted) 1.5 else 1.0) * (0.5 + 0.5 * s.fed.coerceAtLeast(0.3)))
        s.bleach = s.bleach.coerceIn(0.0, 1.0)
        // a long-bleached anemone starves (its algae fed it); deep heat kills via the species tolerance band
        if (s.bleach > 0.7) o.health = max(0.0, o.health - 0.02 * days * s.bleach)
        // photosynthesis covers most of its food, hosted clowns' waste a bit more (Roopin 2007)
        val rate = HealthModel.hungerRatePerMin(o, tempC)
        val photo = if (night) 0.0 else PHOTO_SHARE * 2 * s.lightScore * (1 - s.bleach)
        o.hunger = (o.hunger - rate * dtMin * (photo + if (hosted) HOST_SHARE else 0.0)).coerceIn(0.0, 1.0)
    }

    /** Corals and other anemones in tentacle reach are stung (research 40 §6); clones leave each other alone. */
    private fun stingNeighbours(o: Organism, s: State, list: List<Organism>, dtMin: Double) {
        s.stinging.clear(); s.stingingAnemone = false
        val disc = discCenter(o, s)
        val reach = crownRadius(o, s.contractTarget) * SWEEPER_REACH
        for (c in sim.corals) {
            if (c.tissue <= 0.0 || c.kind == CoralKind.MACRO) continue
            val gap = c.pos.distanceTo(disc) - CoralGrowth.extent(c)
            if (gap > reach) continue
            s.stinging += c.id
            val k = (1 - (gap / reach).coerceIn(0.0, 1.0)).coerceAtLeast(0.3)
            c.disturb = max(c.disturb, 0.8 * k)
            c.tissue = (c.tissue - STING_TISSUE_PER_DAY * k * dtMin / 1440).coerceAtLeast(0.0)
        }
        for (other in list) {
            if (other === o) continue
            val so = state(other)
            if (so.lineage == s.lineage) continue
            if (discCenter(other, so).distanceTo(disc) > reach + crownRadius(other, so.contractTarget)) continue
            s.stingingAnemone = true
            o.stress = min(1.0, o.stress + 0.02 * dtMin / 60)
        }
    }

    /** Longitudinal fission (research 40 §5.2-5.3): big, fed, healthy, settled animals now and then pull in two. */
    private fun maybeSplit(o: Organism, s: State, dtMin: Double): Boolean {
        if (!s.settled || s.healing > 0 || o.lengthCm < SPLIT_MIN_CM || o.health < 0.85 || s.bleach > 0.3) return false
        val rate = if (s.fed > 0.25 && o.stress < 0.3) 1.0 / SPLIT_MEAN_DAYS else if (o.stress in 0.35..0.7) 1.0 / STRESS_SPLIT_DAYS else 0.0
        if (!rng.chance(rate * dtMin / 1440)) return false
        split(o, s)
        return true
    }

    /** Splits [o] now: two halves of 1/√2 the span each, side by side; returns the clone. */
    fun split(o: Organism, s: State = state(o)): Organism {
        val half = o.lengthCm / sqrt(2.0)
        o.lengthCm = half
        val base = o.nickname.substringBefore(" #")
        val n = anemones().size + 1
        val child = sim.addFromSeed(LivestockSeed(o.species.id, "$base #$n", half))
        child.arrivalTime = o.arrivalTime
        val side = (s.normal cross Vec3.X).normalized(Vec3.Z)
        val (p, nrm) = surfaceUnder(s.foot + side * (0.45 * half))
        val cs = state(child)
        cs.foot = p; cs.normal = nrm; cs.settled = true; cs.lineage = s.lineage
        cs.bubbliness = s.bubbliness; cs.dayLight = s.dayLight; cs.bubble = s.bubble * 0.5; cs.healing = 1.0
        s.healing = 1.0; s.splits++; s.bubble *= 0.5
        sim.careLog.anemoneSplits++
        // the parent moves half a disc the other way
        surfaceUnder(s.foot - side * (0.45 * half)).let { (q, qn) -> s.foot = q; s.normal = qn }
        child.placeAt(discCenter(child, cs), o.heading)
        child.up = discUp(cs)
        return child
    }

    // ------------------------------------------------------------------ clownfish

    private fun clowns(list: List<Organism>, night: Boolean, dtMin: Double) {
        val clowns = sim.organisms.filter { it.alive && it.brainKind == BrainKind.CLOWN }
        hosts.keys.retainAll(clowns.mapTo(HashSet()) { it.id })
        if (list.isEmpty()) { for (h in hosts.values) { h.hostId = null; h.candidateId = null }; return }
        val ids = list.mapTo(HashSet()) { it.id }
        val female = clowns.firstOrNull { sim.repro.clownIsFemale(it) }
        for (c in clowns) {
            val h = hosts.getOrPut(c.id) { HostState(c.id) }
            if (!h.initialized) {
                h.initialized = true
                h.willing = rng.chance(ADOPT_P)
                h.adoptDays = (ADOPT_MEDIAN_DAYS * exp(ADOPT_SPREAD * rng.gaussian())).coerceIn(0.15, 30.0)
            }
            if (h.hostId != null && h.hostId !in ids) {
                // its anemone died: back to the mushrooms; a clown that knows anemones takes to the next one faster (Madhu 2009)
                h.hostId = null; h.candidateId = null; h.acclim = 0.5
            }
            if (h.hostId != null || !h.willing) continue
            val cand = h.candidateId?.let { id -> list.firstOrNull { it.id == id } }
                ?: list.minByOrNull { it.pos.distanceTo(c.pos) }!!.also { h.candidateId = it.id }
            if (night) continue
            val isFemale = c === female || female == null
            val femaleHosts = female != null && hosts[female.id]?.hostId == cand.id
            // the female leads; the male mostly follows her in once she has moved
            val pace = if (isFemale) 1.0 else if (femaleHosts) 3.0 else 0.5
            h.acclim = min(1.0, h.acclim + dtMin / 1440.0 / h.adoptDays * DAY_FRACTION_INV * pace)
            if (h.acclim >= 1.0 || (!isFemale && femaleHosts && h.acclim >= 0.6)) h.hostId = cand.id
        }
    }

    /** The anemone [clown] hosts, or null (it keeps its old host). */
    fun hostOf(clown: Organism): Organism? {
        val id = hosts[clown.id]?.hostId ?: return null
        return sim.organisms.firstOrNull { it.id == id && it.alive }
    }

    /** The anemone [clown] is getting to know (acclimating, not hosting yet), or null. */
    fun candidateOf(clown: Organism): Organism? {
        val h = hosts[clown.id] ?: return null
        if (h.hostId != null || !h.willing) return null
        val id = h.candidateId ?: return null
        return sim.organisms.firstOrNull { it.id == id && it.alive }
    }

    fun acclimation(clown: Organism): Double = hosts[clown.id]?.acclim ?: 0.0

    /** True when [fish] may nestle in [anemone] unharmed (a clown that has acclimated to it). */
    fun welcomes(anemone: Organism, fish: Organism): Boolean {
        if (fish.brainKind != BrainKind.CLOWN) return false
        val h = hosts[fish.id] ?: return false
        return h.hostId == anemone.id || (h.candidateId == anemone.id && h.acclim >= 0.5)
    }

    // ------------------------------------------------------------------ saves (My tank)

    fun saveState(o: Organism): Map<String, Double> {
        val m = LinkedHashMap<String, Double>()
        states[o.id]?.let { s ->
            m["foot_x"] = s.foot.x; m["foot_y"] = s.foot.y; m["foot_z"] = s.foot.z
            m["normal_x"] = s.normal.x; m["normal_y"] = s.normal.y; m["normal_z"] = s.normal.z
            m["settled"] = if (s.settled) 1.0 else 0.0
            m["bubble"] = s.bubble; m["bubbliness"] = s.bubbliness; m["bleach"] = s.bleach; m["heat_days"] = s.heatDays
            m["fed"] = s.fed; m["healing"] = s.healing
            if (!s.dayLight.isNaN()) m["day_light"] = s.dayLight
        }
        hosts[o.id]?.let { h ->
            m["host_acclim"] = h.acclim; m["host_willing"] = if (h.willing) 1.0 else 0.0; m["host_days"] = h.adoptDays
            m["host_on"] = if (h.hostId != null) 1.0 else 0.0
        }
        return m
    }

    /** Restores [saveState]; clown host ids are re-linked to the first anemone (ids are kept across saves). */
    fun loadState(o: Organism, m: Map<String, Double>) {
        if (m.isEmpty()) return
        if (o.brainKind == BrainKind.ANEMONE) {
            val s = state(o)
            val fx = m["foot_x"]; val fy = m["foot_y"]; val fz = m["foot_z"]
            if (fx != null && fy != null && fz != null) s.foot = Vec3(fx, fy, fz)
            s.normal = Vec3(m["normal_x"] ?: 0.0, m["normal_y"] ?: 1.0, m["normal_z"] ?: 0.0).normalized(Vec3.Y)
            s.settled = (m["settled"] ?: 0.0) > 0.5
            m["bubble"]?.let { s.bubble = it }; m["bubbliness"]?.let { s.bubbliness = it }
            m["bleach"]?.let { s.bleach = it }; m["heat_days"]?.let { s.heatDays = it }
            m["fed"]?.let { s.fed = it }; m["healing"]?.let { s.healing = it }; m["day_light"]?.let { s.dayLight = it }
            o.placeAt(discCenter(o, s), o.heading); o.up = discUp(s)
        }
        if (o.brainKind == BrainKind.CLOWN && m.containsKey("host_acclim")) {
            val h = hosts.getOrPut(o.id) { HostState(o.id) }
            h.initialized = true
            h.acclim = m["host_acclim"] ?: 0.0; h.willing = (m["host_willing"] ?: 1.0) > 0.5; h.adoptDays = m["host_days"] ?: ADOPT_MEDIAN_DAYS
            if ((m["host_on"] ?: 0.0) > 0.5) pendingHost += o.id
        }
    }

    private val pendingHost = HashSet<String>()

    /** After every animal is loaded: clowns that hosted an anemone take the nearest one again. */
    fun relinkHosts() {
        val list = anemones()
        for (id in pendingHost) {
            val c = sim.organisms.firstOrNull { it.id == id } ?: continue
            val a = list.minByOrNull { it.pos.distanceTo(c.pos) } ?: continue
            hosts[id]?.let { it.hostId = a.id; it.candidateId = a.id }
        }
        pendingHost.clear()
    }

    companion object {
        /** No measured glide rate (research 40 §2.1); a slow drift that crosses a rock in a day or two. */
        @Ev(Evidence.DESIGN_ESTIMATE, "40 §2.1 no data") const val GLIDE_CM_H = 3.0
        /** Wanders a few days to a week before settling (40 §2.2, hobby reports). */
        @Ev(Evidence.DESIGN_ESTIMATE, "40 §2.2") const val SETTLE_DWELL_H = 12.0
        const val SETTLE_SCORE = 0.62
        const val UNSETTLE_SCORE = 0.38
        const val UNHAPPY_HOURS = 36.0
        const val LOOK_EVERY_MIN = 45.0
        const val SEARCH_CM = 8.0
        /** Preferred light (fraction of full fixture output at the disc): moderate-high, PAR ~150-350 (40 §2.3, §8.4). */
        @Ev(Evidence.ANALOGY, "40 §2.3 retail 150-350 PAR") const val LIGHT_LO = 0.32
        const val LIGHT_HI = 0.85
        /** Moderate flow at the disc, cm/s (40 §2.3, retail). */
        const val FLOW_LO = 3.0
        const val FLOW_HI = 18.0
        /** Light loss with depth below the surface (per cm) and under a rock overhang. */
        const val DEPTH_K = 0.012
        const val SHADE = 0.35
        /** Column height as a fraction of the tentacle span (the model: 5.6 cm column under an 18 cm crown). */
        const val COLUMN_FRAC = 0.31
        const val NIGHT_CONTRACT = 0.35
        @Ev(Evidence.DESIGN_ESTIMATE, "40 §3 anecdotal") val DEFLATE_EVERY_D = 2.0..6.0
        val DEFLATE_H = 2.0..5.0
        /** Post-feed deflation (hours after the meal; 40 §3 anecdotal 2-6 h). */
        val POST_MEAL_H = 0.5..4.0
        const val WASTE_G_PER_CM = 0.002
        const val BUBBLE_TAU_H = 6.0
        /** Bleaching: sustained heat above this (27 °C for 6 d bleached wild animals, Hill & Scott 2012; captive stock runs warmer). */
        @Ev(Evidence.ANALOGY, "40 §8.1 Hill & Scott 2012") const val BLEACH_C = 27.5
        const val RECOVER_DAYS = 45.0
        /** Share of the anemone's food from its algae and from hosted clowns' waste (Roopin 2007: fish N > 100 % of need). */
        const val PHOTO_SHARE = 0.85
        const val HOST_SHARE = 0.15
        /** Sweeper tentacles reach past the crown (40 §10 assumption 1.5-2x tentacle length). */
        @Ev(Evidence.DESIGN_ESTIMATE, "40 §6, §10") const val SWEEPER_REACH = 1.25
        /** Tissue a soft coral loses per day in full contact (a touched coral dies back over 1-2 weeks; est.). */
        const val STING_TISSUE_PER_DAY = 0.1
        /** Fission (40 §5.2): rare; a big, fed animal about twice a year, stressed ones now and then. */
        @Ev(Evidence.DESIGN_ESTIMATE, "40 §5.2 hobby") const val SPLIT_MEAN_DAYS = 180.0
        const val STRESS_SPLIT_DAYS = 400.0
        const val SPLIT_MIN_CM = 20.0
        /** Scar covered by day 28 (Scott 2014: 96 %). */
        @Ev(Evidence.SOURCED, "40 §5.3 Scott 2014") const val SCAR_DAYS = 28.0
        const val MAX_ANEMONES = 8
        /** Ocellaris and a bubble tip: most adopt it, over days to weeks (40 §7.2 hobby; Madhu 2009 minutes for naive fish). */
        @Ev(Evidence.DESIGN_ESTIMATE, "40 §7.2, §7.6") const val ADOPT_P = 0.88
        const val ADOPT_MEDIAN_DAYS = 2.0
        const val ADOPT_SPREAD = 0.9
        /** Acclimation only progresses in daylight; this converts whole-day rates to daylight minutes. */
        const val DAY_FRACTION_INV = 2.0
    }
}
