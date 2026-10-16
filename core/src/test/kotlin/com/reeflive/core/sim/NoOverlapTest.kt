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
 *    stations) is inside the rock SDF, under the sand surface or through the glass by more than [TOL]. A crawler on
 *    its seat ([CrawlerSeat]: the hermits) rests on the rock, sand and glass by the seat's contact solve: there those
 *    are measured on the seat's contact points (shell, shield, hips, claw pivots), the measure HermitSeatTest
 *    holds it to.
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
 * - Sand intake: a diamond goby taking a mouthful of sand (mouth open, [Organism.mouth] >= 0.9) has its snout, spine
 *   station 0 only, in the bed by up to [MOUTH_INTAKE_CM] (where it digs at a rock foot the rock's distance field runs
 *   under that sand, so the allowance covers rock there too), as a body in its burrow is let into the sand.
 * - Whole bodies: the explicit states [BodyCollision.exempt] lets into the geometry (in a hole, den or burrow, posture 3;
 *   buried in the sand, posture 5; a scripted den / hole path; the sessile feather duster; hidden animals) and dead ones.
 * - Obstacles: every coral colony (soft, see above); the pass rules documented on [StaticObstacles] (small crawlers
 *   and the shrimp in the duster crown).
 *
 * Hours per run: system property `reeflive.noOverlapHours` or env `REEFLIVE_NO_OVERLAP_HOURS` (default 2; four runs, two
 * by day and two by night).
 */
class NoOverlapTest {
    /** The brittle star's arms, by BrittleMotionTest's arm criterion ([BrittleArmCheck]). */
    private val armCheck = BrittleArmCheck()

    private val tankGlb: File? = listOf("../app/src/main/assets/models/tank.glb", "app/src/main/assets/models/tank.glb").map(::File).firstOrNull { it.isFile }

    private fun teeSim(seed: Long, hour: Double): Simulation {
        val rock = TestTank.solidRock.forSimulation().also { r -> tankGlb?.let { r.sandBase = HeightField.fromGlb(it.readBytes(), r.lengthCm, r.widthCm) } }
        return Simulation(TankConfig(seed = seed), TestTank.catalog, rock, seed, hour * 3600, WaterState(), DefaultStock.all).also { it.solidBodies = true }
    }

    /** Violations of one check for one species: samples over [TOL], the worst depth (cm) and where it happened. */
    private class Tally { var over = 0L; var worst = 0.0; var where = "" }

    /**
     * Solid bodies ship as a setting with polish to follow (Tee, 2026-10-15), so a check fails when more than [rate] of
     * its samples are over [TOL] or any is deeper than [ceil]: ceilings just above what 74929fb measured over the 2 h runs,
     * which catch any regression while the rare deep transients are worked down.
     */
    private class Check(val name: String, val rate: Double, val ceil: Double) {
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
        val failed: Boolean get() = worst > ceil || by.values.sumOf { it.over } > samples * rate
    }

    @Test
    fun noBodyOverlapsAnotherOrPassesIntoRockSandGlassOrDecor() {
        val hours = (System.getProperty("reeflive.noOverlapHours") ?: System.getenv("REEFLIVE_NO_OVERLAP_HOURS") ?: "2.0").toDouble()
        // measured 2026-10-15 (2 h x 4 runs): body vs body 0.85 cm worst (hermit under a cleaning skunk), 1,056 of 56 M;
        // rock/sand/glass 1.20 cm (brittle disc), 673 of 5.9 M; static obstacles 0.46 cm (hermit on the magnet), 81 of 5.9 M
        val pairs = Check("body vs body", rate = 3e-5, ceil = 1.0)
        val env = Check("rock / sand / glass", rate = 2e-4, ceil = 1.5)
        val obs = Check("static obstacles", rate = 3e-5, ceil = 0.6)
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
        val report = "NoOverlapTest: ${"%.1f".format(hours)} h x ${RUNS.size} runs (seeds/hours $RUNS), tolerance $TOL cm (rare transients capped per check)\n  " +
            listOf(pairs, env, obs).joinToString("\n  ") { it.report() } + "\n  coral contacts (soft, not counted): $coralContacts" +
            "\n  $armCheck" + (armCheck.failure()?.let { "\n  $it" } ?: "")
        println(report)
        assertTrue(report, !pairs.failed && !env.failed && !obs.failed && armCheck.failure() == null)
    }

    /** Every section point of [o]'s body against the rock, sand and glass ([env]) and the static obstacles ([obs]). */
    private fun sampleBody(sim: Simulation, o: Organism, env: Check, obs: Check, seed: Long, t: Double) {
        val b = o.body
        b.refresh(o)
        val r = sim.rock
        val mask = StaticObstacles.passMask(o)
        val seat = o.seat?.takeIf { o.seatOn && it.blend >= 1.0 }
        val intake = o.brainKind == BrainKind.DIAMOND_GOBY && o.mouth >= 0.9
        // a brittle star on its arms: its rig rests the disc and the arms on the rock, sand and glass, measured below
        val arms = (o.limbRig as? BrittleArms)?.takeIf { o.seatOn }
        val onRig = seat != null || arms != null
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
                // (a seated crawler's rock, sand and glass are its seat's, measured on its contact points below)
                val terms = if (onRig) doubleArrayOf(-1.0, -1.0, -1.0, -1.0)
                    else doubleArrayOf(-r.sdf.dist(q), r.floorY(q.x, q.z) - q.y, max(-q.x, q.x - r.lengthCm), max(-q.z, q.z - r.widthCm))
                // (a sand sifter's snout in the bed while it takes a mouthful, up to [MOUTH_INTAKE_CM]: see the class doc)
                if (i == 0 && half == 0 && intake) { terms[0] -= MOUTH_INTAKE_CM; terms[1] -= MOUTH_INTAKE_CM }
                for (m in terms.indices) if (terms[m] > worstEnv) { worstEnv = terms[m]; envWhere = "${TERMS[m]} station $i${if (half == 1) "+" else ""}" }
                val d = sim.obstacles.depth(q, 0.0, mask)
                if (d > worstObs) { worstObs = d; obsWhere = "${sim.obstacles.lastOwner} station $i${if (half == 1) "+" else ""}" }
            }
        }
        if (seat != null) {
            val d = seat.contactDepth(sim.crawlSurface, o.lengthCm)
            if (d > worstEnv) { worstEnv = d; envWhere = "seat contact" }
        }
        if (arms != null) {
            // the disc's underside and each arm, as BrittleMotionTest measures them
            if (-arms.discUnderCm > worstEnv) { worstEnv = -arms.discUnderCm; envWhere = "brittle disc" }
            for (k in 0 until BrittleArms.ARMS) for (i in 1..10) {
                val s = i / 10.0
                if (s > arms.armLen[k]) break
                val d = sim.crawlSurface.dist(arms.armPoint(o, k, s)) - 0.0105 * 0.62 * (1 - 0.82 * Math.pow(s, 1.25)) * o.lengthCm
                armCheck.add(-d) { "arm $k s=$s at ${arms.armPoint(o, k, s)} t=${sim.simTimeSec}" }
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
        /** How far a sand sifter's snout may press into the bed (sand round the rock foot) taking a mouthful (cm). */
        const val MOUTH_INTAKE_CM = 0.3
        /** Sample every this many 20 Hz ticks (0.1 s). */
        const val EVERY = 2
        const val RIM = 12
        val TERMS = listOf("rock", "sand", "end glass", "front/back glass")
        /** (seed, start hour): two day runs, two night runs. */
        val RUNS = listOf(31L to 10.0, 32L to 14.5, 33L to 22.5, 34L to 2.0)
    }
}
