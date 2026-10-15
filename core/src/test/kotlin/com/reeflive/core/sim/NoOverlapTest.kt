package com.reeflive.core.sim

import com.reeflive.core.geom.HeightField
import com.reeflive.core.geom.Vec3
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * The owner's hard no-clipping rule (tee-clips ISSUES #1, #3, #6, #7, #9, #10) as a property run: Tee's full stock
 * ([DefaultStock.all]) for several simulated hours by day and by night on several seeds, at 20 Hz with full behaviour,
 * every animal's collision body ([BodyState], the [BodyShape] stations) sampled every [EVERY] ticks (0.1 s):
 *
 * 1. **Body vs body**: no two bodies overlap ([BodyCollision.pairPenetration]) by more than [TOL].
 * 2. **Environment**: no point of any station's section (12 around each ellipse, at every station and halfway between
 *    stations) is inside the rock SDF, under the sand surface or through the glass by more than [TOL].
 * 3. **Static obstacles**: no such point is inside a [StaticObstacles] solid (hard decor, equipment, the feather
 *    duster) by more than [TOL] beyond the obstacle's give, under the animal's [StaticObstacles.passMask]. Coral is
 *    soft (Tee): bodies push through it and it bends, so a body in a colony is a contact
 *    ([StaticObstacles.contacts], counted and reported), never clipping.
 *
 * Failure lists the worst offenders by species for each check (samples over tolerance, worst depth, an example).
 *
 * Legitimate exceptions, and only these:
 * - Pairs: a cleaner (cleaner wrasse, skunk or banded shrimp) touching the client it is cleaning (its focus, a posing
 *   client, or an active cleaning station pair); the two clownfish together in their host (both within the host
 *   anchor's radius); mates in contact (the banded shrimp pair's [Organism.mateId]: mating, moult guarding); a predator's
 *   strike reaching its prey (open mouth on its focus).
 * - Whole bodies: the explicit states [BodyCollision.exempt] lets into the geometry (in a hole, den or burrow, posture 3;
 *   buried in the sand, posture 5; a scripted den / hole path; the sessile feather duster; hidden animals) and dead ones.
 * - Obstacles: every coral colony (soft, see above); the pass rules documented on [StaticObstacles] (small crawlers
 *   and the shrimp in the duster crown).
 *
 * Hours per run: system property `reeflive.noOverlapHours` or env `REEFLIVE_NO_OVERLAP_HOURS` (default 2; four runs, two
 * by day and two by night).
 */
class NoOverlapTest {
    private val tankGlb: File? = listOf("../app/src/main/assets/models/tank.glb", "app/src/main/assets/models/tank.glb").map(::File).firstOrNull { it.isFile }

    private fun teeSim(seed: Long, hour: Double): Simulation {
        val rock = TestTank.rock.forSimulation().also { r -> tankGlb?.let { r.sandBase = HeightField.fromGlb(it.readBytes(), r.lengthCm, r.widthCm) } }
        return Simulation(TankConfig(seed = seed), TestTank.catalog, rock, seed, hour * 3600, WaterState(), DefaultStock.all)
    }

    /** Violations of one check for one species: samples over [TOL], the worst depth (cm) and where it happened. */
    private class Tally { var over = 0L; var worst = 0.0; var where = "" }

    private class Check(val name: String) {
        val by = java.util.TreeMap<String, Tally>()
        var samples = 0L
        var worst = 0.0
        fun note(species: String, depth: Double, where: () -> String) {
            samples++
            if (depth > worst) worst = depth
            if (depth <= TOL) return
            val t = by.getOrPut(species) { Tally() }
            t.over++
            if (depth > t.worst) { t.worst = depth; t.where = where() }
        }
        fun report(): String = "$name: worst %.3f cm over $samples samples".format(worst) +
            by.entries.sortedByDescending { it.value.worst }.joinToString("") { (sp, t) -> "\n    $sp: ${t.over} over, worst %.3f cm (${t.where})".format(t.worst) }
        val failed: Boolean get() = worst > TOL
    }

    @Test
    fun noBodyOverlapsAnotherOrPassesIntoRockSandGlassOrDecor() {
        val hours = (System.getProperty("reeflive.noOverlapHours") ?: System.getenv("REEFLIVE_NO_OVERLAP_HOURS") ?: "2.0").toDouble()
        val pairs = Check("body vs body")
        val env = Check("rock / sand / glass")
        val obs = Check("static obstacles")
        // body stations in a coral colony (soft: reported for the coral motion, not clipping)
        var coralContacts = 0L
        for ((seed, hour) in RUNS) {
            val sim = teeSim(seed, hour)
            val dt = 0.05
            val total = (hours * 3600 / dt).toInt()
            for (k in 0 until total) {
                sim.advance(dt, dt)
                if (k % EVERY != 0) continue
                val t = sim.simTimeSec
                coralContacts += sim.obstacles.contacts.size
                val live = sim.organisms.filter { it.alive && !sim.bodies.exempt(it) }
                for (o in live) sampleBody(sim, o, env, obs, seed, t)
                for (i in live.indices) for (j in i + 1 until live.size) {
                    val a = live[i]; val b = live[j]
                    if (allowedContact(sim, a, b) != null) continue
                    val v = sim.bodies.pairPenetration(a, b)
                    val where = { "${a.id} '${a.behaviorName}' / ${b.id} '${b.behaviorName}' seed $seed t=${"%.1f".format(t)}" }
                    pairs.note(a.species.id, v, where)
                    if (v > TOL) pairs.by.getOrPut(b.species.id) { Tally() }.let { tb -> tb.over++; if (v > tb.worst) { tb.worst = v; tb.where = where() } }
                }
            }
        }
        val report = "NoOverlapTest: ${"%.1f".format(hours)} h x ${RUNS.size} runs (seeds/hours $RUNS), tolerance $TOL cm\n  " +
            listOf(pairs, env, obs).joinToString("\n  ") { it.report() } + "\n  coral contacts (soft, not counted): $coralContacts"
        println(report)
        assertTrue(report, !pairs.failed && !env.failed && !obs.failed)
    }

    /** Every section point of [o]'s body against the rock, sand and glass ([env]) and the static obstacles ([obs]). */
    private fun sampleBody(sim: Simulation, o: Organism, env: Check, obs: Check, seed: Long, t: Double) {
        val b = o.body
        b.refresh(o)
        val r = sim.rock
        val mask = StaticObstacles.passMask(o)
        var worstEnv = Double.NEGATIVE_INFINITY; var envWhere = ""
        var worstObs = Double.NEGATIVE_INFINITY; var obsWhere = ""
        for (i in 0 until BodyState.N) for (half in 0..1) {
            if (half == 1 && i == BodyState.N - 1) continue
            val j = if (half == 1) i + 1 else i
            val f = if (half == 1) 0.5 else 0.0
            val p = b.p[i] + (b.p[j] - b.p[i]) * f
            val up = (b.up[i] + (b.up[j] - b.up[i]) * f).normalized(Vec3.Y)
            val side = (b.side[i] + (b.side[j] - b.side[i]) * f).normalized(Vec3.Z)
            val c = b.ctr[i] + (b.ctr[j] - b.ctr[i]) * f
            val a = b.a[i] + (b.a[j] - b.a[i]) * f
            val w = b.b[i] + (b.b[j] - b.b[i]) * f
            for (k in 0..RIM) {
                // k = RIM: the section centre (a body wholly inside a thin obstacle has no rim point in it)
                val q = if (k == RIM) p + up * c else p + up * (c + a * cos(k * 2 * Math.PI / RIM)) + side * (w * sin(k * 2 * Math.PI / RIM))
                val terms = doubleArrayOf(-r.sdf.dist(q), r.floorY(q.x, q.z) - q.y, max(-q.x, q.x - r.lengthCm), max(-q.z, q.z - r.widthCm))
                for (m in terms.indices) if (terms[m] > worstEnv) { worstEnv = terms[m]; envWhere = "${TERMS[m]} station $i${if (half == 1) "+" else ""}" }
                val d = sim.obstacles.depth(q, 0.0, mask)
                if (d > worstObs) { worstObs = d; obsWhere = "${sim.obstacles.lastOwner} station $i${if (half == 1) "+" else ""}" }
            }
        }
        val who = "${o.id} '${o.behaviorName}' posture ${o.posture}"
        env.note(o.species.id, worstEnv) { "$who, $envWhere at ${fmt(o.pos)} seed $seed t=${"%.1f".format(t)}" }
        obs.note(o.species.id, worstObs) { "$who, $obsWhere at ${fmt(o.pos)} seed $seed t=${"%.1f".format(t)}" }
    }

    /** Why [a] and [b] may touch (the exceptions in the class doc), or null when they must not overlap. */
    private fun allowedContact(sim: Simulation, a: Organism, b: Organism): String? {
        fun cleaner(x: Organism) = x.brainKind == BrainKind.WRASSE || x.brainKind == BrainKind.CLEANER_SHRIMP || x.brainKind == BrainKind.BANDED_SHRIMP
        if (cleaner(a) && a.focusId == b.id || cleaner(b) && b.focusId == a.id) return "cleaning"
        if (a.poseFor === b || b.poseFor === a) return "cleaning (posing client)"
        for (s in sim.perception.stations) if (s.client != null && (s.cleaner === a && s.client === b || s.cleaner === b && s.client === a)) return "cleaning station"
        if (a.brainKind == BrainKind.CLOWN && b.brainKind == BrainKind.CLOWN && inHost(sim, a) && inHost(sim, b)) return "clowns in the host"
        if (a.mateId == b.id || b.mateId == a.id) return "mates"
        if (a.mouth > 0.5 && a.focusId == b.id || b.mouth > 0.5 && b.focusId == a.id) return "strike"
        return null
    }

    private fun inHost(sim: Simulation, o: Organism): Boolean {
        val h = o.hostAnchor?.let { sim.rock.anchors[it] } ?: return false
        return o.body.centre.distanceTo(h.pos) <= (h.radius ?: 8.0) + 0.5 * o.lengthCm
    }

    private fun fmt(v: Vec3) = "(%.1f, %.1f, %.1f)".format(v.x, v.y, v.z)

    private companion object {
        /** Allowed overlap (cm): 1 mm, twice the resolver's skin. */
        const val TOL = 0.1
        /** Sample every this many 20 Hz ticks (0.1 s). */
        const val EVERY = 2
        const val RIM = 12
        val TERMS = listOf("rock", "sand", "end glass", "front/back glass")
        /** (seed, start hour): two day runs, two night runs. */
        val RUNS = listOf(31L to 10.0, 32L to 14.5, 33L to 22.5, 34L to 2.0)
    }
}
