package com.reeflive.core.ai

import com.reeflive.core.geom.Vec3
import com.reeflive.core.sim.BrainKind
import com.reeflive.core.sim.BrittleArms
import com.reeflive.core.sim.CrabLegs
import com.reeflive.core.sim.FoodKind
import com.reeflive.core.sim.LimbRig
import com.reeflive.core.sim.Simulation
import com.reeflive.core.sim.TestTank
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Brittle star and Sally Lightfoot locomotion and behaviour (research 17; SIMULATION §19). */
class CrawlerTest {
    private fun Simulation.org(kind: BrainKind) = organisms.first { it.brainKind == kind }
    private fun Simulation.star() = agentOf(org(BrainKind.BRITTLE_STAR))!!.brain as BrittleStarBrain
    private fun Simulation.crab() = agentOf(org(BrainKind.SALLY_CRAB))!!.brain as SallyCrabBrain
    private fun stock(vararg ids: String) = ids.map { TestTank.seed(it) }

    @Test
    fun brittleStarHidesByDayWithOnlyAFewArmTipsOut() {
        val s = TestTank.sim(stock("brittle"), seed = 3L, startHour = 12.0)
        s.advance(240.0, behaviorDt = 0.1)
        val o = s.org(BrainKind.BRITTLE_STAR)
        val b = s.star()
        assertEquals("hiding by day: ${o.behaviorName}", 3, o.posture)
        assertTrue("disc at its hide (${o.pos.distanceTo(b.home)} cm)", o.pos.horizontal().distanceTo(b.home.horizontal()) < 3.0)
        val out = (0 until BrittleArms.ARMS).count { b.arms.reachOf(it) > 0.6 }
        assertTrue("1-3 arm tips out, saw $out", out in 1..3)
        assertEquals("out arms = this individual's count", b.tipsOut, out)
        // the pose carries the 40-entry arm layout (eight entries per arm: its 15 drawn step directions and heights)
        val pose = o.toPose()
        assertEquals(BrittleArms.COUNT * 4, pose.limbs.size)
        assertEquals(1, pose.motionMode)
    }

    @Test
    fun brittleStarComesOutAtNight() {
        val s = TestTank.sim(stock("brittle"), seed = 3L, startHour = 23.0)
        var maxD = 0.0
        val b = s.star(); val o = s.org(BrainKind.BRITTLE_STAR)
        s.advance(1800.0, behaviorDt = 0.1) { maxD = maxOf(maxD, o.pos.distanceTo(b.home)) }
        assertTrue("roamed ${"%.1f".format(maxD)} cm from the hide at night", maxD > 8.0)
        assertTrue("all arms out at night", b.arms.meanReach > 0.8)
    }

    @Test
    fun brittleStarEmergesByDayForFoodArmsFirst() {
        val s = TestTank.sim(stock("brittle"), seed = 4L, startHour = 12.0)
        s.advance(120.0, behaviorDt = 0.1)
        val o = s.org(BrainKind.BRITTLE_STAR); val b = s.star()
        o.hunger = 0.8
        val home = o.pos
        // a pellet on open sand 20 cm away: beyond arm reach, so it must row there
        var food = Vec3(home.x - 20.0, s.rock.sandTopCm + 0.3, home.z + 4.0)
        for (i in 0 until 12) { if (s.rock.sdf.dist(food) > 2.0) break; food = food + Vec3(2.0, 0.0, 1.5) }
        s.food.spawn(FoodKind.PELLET, 0.02, food, s.simTimeSec)
        var left = -1.0
        var waitedS = 0.0
        s.advance(300.0, behaviorDt = 0.1) {
            if (left < 0 && o.pos.distanceTo(home) > 2.0) left = s.simTimeSec
            if (left < 0) waitedS += 0.1
            o.hunger = maxOf(o.hunger, 0.6)
        }
        assertTrue("left the hide for food by day", left > 0)
        // scent lag: not instant
        assertTrue("waited for the scent (${"%.0f".format(waitedS)} s)", waitedS > BrittleStarBrain.SCENT_DELAY_S)
        assertTrue("arms out before the disc moved (${b.armsFirstEmergences}/${b.emergences})", b.emergences >= 1 && b.armsFirstEmergences == b.emergences)
        assertTrue("fed (arm feeds ${b.armFeeds})", b.armFeeds >= 1)
    }

    @Test
    fun brittleStarSwitchesLeadArmInsteadOfTurning() {
        val s = TestTank.sim(stock("brittle"), seed = 5L, startHour = 23.0)
        s.advance(5.0, behaviorDt = 0.1)
        val o = s.org(BrainKind.BRITTLE_STAR); val b = s.star()
        // put it on open sand, clear of rock overhangs, with room for both food runs
        val y = s.rock.sandTopCm + 0.4
        val start = (15..105 step 2).flatMap { x -> (6..30 step 1).map { z -> Vec3(x.toDouble(), y, z.toDouble()) } }.maxBy { p ->
            listOf(p, p + Vec3(6.0, 0.0, 0.0), p + Vec3(12.0, 0.0, 0.0)).minOf { s.rock.sdf.dist(it) }
        }
        o.placeAt(start, Vec3.X)
        o.up = Vec3.Y   // (lying flat on the sand: not still tilted to the rock face it was lifted off)
        val h0 = o.heading.horizontal().normalized()
        var maxTurn = 0.0
        val roleErr = ArrayList<Double>()
        var lastTA = 0.0
        fun feedAt(p: Vec3): Boolean {
            s.food.spawn(FoodKind.PELLET, 0.02, p, s.simTimeSec - 600)   // already smelt
            val before = b.armFeeds
            var t = 0.0
            while (b.armFeeds == before && t < 240.0) {
                s.advance(1.0, behaviorDt = 0.1) {
                    o.hunger = maxOf(o.hunger, 0.7)
                    maxTurn = maxOf(maxTurn, Vec3.angle(h0, o.heading.horizontal().normalized(h0)))
                    if (o.vel.length() > 0.5 * o.cruiseSpeed && o.behaviorName == "Racing to food") {
                        val (f, _, l) = LimbRig.frame(o)
                        roleErr += abs(LimbRig.wrap(b.arms.travelAngle - kotlin.math.atan2(o.vel dot l, o.vel dot f)))
                        lastTA = b.arms.travelAngle
                    }
                }
                t += 1.0
            }
            return b.armFeeds > before
        }
        assertTrue("reached food along +X", feedAt(Vec3(start.x + 12.0, s.rock.sandTopCm + 0.3, start.z)))
        val leadA = lastTA
        val n1 = roleErr.size
        // second run: back the way it came (open sand it just crossed)
        val back = (start - o.pos).horizontal().normalized(Vec3.X * -1.0)
        assertTrue("reached food back the other way", feedAt((o.pos + back * 11.0).withY(s.rock.sandTopCm + 0.3)))
        // the arm roles follow the travel (a new centre arm after each turn) while the disc keeps its heading
        assertTrue("rowed on both legs (${roleErr.size} samples, $n1 on the first)", n1 > 5 && roleErr.size - n1 > 5)
        val late = roleErr.subList(roleErr.size - 3, roleErr.size)
        assertTrue("centre arm serves the travel at the end (${late.map { "%.2f".format(it) }})", late.all { it < 0.9 })
        assertTrue("arm roles turned round (travel angle $leadA -> $lastTA)", abs(LimbRig.wrap(lastTA - leadA)) > 1.5)
        assertTrue("the disc did not turn (${"%.2f".format(maxTurn)} rad)", maxTurn < 0.15)
    }

    @Test
    fun brittleStarArmLoopsFoodToTheMouth() {
        val s = TestTank.sim(stock("brittle"), seed = 2L, startHour = 12.0)
        s.advance(60.0, behaviorDt = 0.1)
        val o = s.org(BrainKind.BRITTLE_STAR); o.hunger = 0.8
        val disc = o.pos
        s.food.spawn(FoodKind.PELLET, 0.02, Vec3(disc.x + 4.0, s.rock.sandTopCm + 0.3, disc.z), s.simTimeSec)
        val b = s.star()
        var closest = Double.MAX_VALUE; var looped = false
        s.advance(60.0, behaviorDt = 0.1) {
            o.hunger = maxOf(o.hunger, 0.5)
            val p = s.food.particles.firstOrNull { !it.eaten && it.claimedBy == o.id }
            if (p != null) { looped = true; closest = minOf(closest, p.pos.distanceTo(o.pos)) }
        }
        assertTrue("arm feeds ${b.armFeeds}", b.armFeeds >= 1)
        assertTrue("food was carried in along the arm (closest ${"%.1f".format(closest)} cm)", looped && closest < 2.0)
        assertTrue("disc stayed put", o.pos.distanceTo(disc) < 2.5)
    }

    @Test
    fun brittleStarRetreatsFastWhenDisturbedAndShedsAnArmWhenBitten() {
        val s = TestTank.sim(stock("brittle"), seed = 6L, startHour = 23.0)
        s.advance(5.0, behaviorDt = 0.1)
        val o = s.org(BrainKind.BRITTLE_STAR); val b = s.star()
        o.placeAt(Vec3(b.home.x - 20.0, s.rock.sandTopCm + 0.4, 27.0).let { if (s.rock.sdf.dist(it) > 2) it else Vec3(60.0, it.y, 27.0) }, o.heading)
        s.advance(1.0, behaviorDt = 0.1)
        val ag = s.agentOf(o)!!
        ag.brain.onTap(ag.ctx, o.pos + Vec3(2.0, 1.0, 0.0), 0.8)
        var minReach = 1.0; var vmax = 0.0
        s.advance(20.0, behaviorDt = 0.1) { minReach = minOf(minReach, b.arms.meanReach); vmax = maxOf(vmax, o.vel.length()) }
        assertTrue("arms whipped in (mean reach $minReach)", minReach < 0.5)
        assertTrue("rowed off fast ($vmax cm/s)", vmax > o.cruiseSpeed * 1.5)
        // autotomy: a bite costs an arm tip, which then regrows
        repeat(10) { ag.brain.onAttacked(ag.ctx, o.pos + Vec3(3.0, 0.0, 0.0), 1.0) }
        assertTrue("arm shed (${b.autotomies})", b.autotomies >= 1)
        val k = (0 until BrittleArms.ARMS).minBy { b.arms.armLen[it] }
        val l0 = b.arms.armLen[k]
        assertTrue(l0 < 1.0)
        b.arms.regrow(30 * 86400.0, o.lengthCm)
        assertTrue("regrowing", b.arms.armLen[k] > l0)
    }

    @Test
    fun crabMostlyGrazesWithAlternatingClawsAndDartsHomeOnStartle() {
        val s = TestTank.sim(stock("crab"), seed = 7L, startHour = 23.0)
        val o = s.org(BrainKind.SALLY_CRAB); val b = s.crab()
        val time = HashMap<String, Double>()
        s.advance(1800.0, behaviorDt = 0.1) { time.merge(o.behaviorName, 0.1, Double::plus) }
        val active = time.filterKeys { !it.startsWith("Hiding") }.values.sum()
        val grazing = time.filterKeys { it.startsWith("Grazing") }.values.sum()
        assertTrue("grazing ${"%.0f".format(grazing)} of ${"%.0f".format(active)} active s: $time", grazing > 0.6 * active)
        // quick alternating claw strokes: 1.5-5 strokes/s while grazing (both chelae)
        val rate = b.legs.pickStrokes / b.grazeSeconds.coerceAtLeast(1.0)
        assertTrue("claw strokes $rate /s", rate in 1.5..5.0)
        val pose = o.toPose()
        assertEquals(3, pose.motionMode)
        val ph8 = pose.limbs[8 * 4]; val ph9 = pose.limbs[9 * 4]
        val d = abs(ph8 - ph9)
        assertTrue("claws alternate (phases $ph8 / $ph9)", abs(d - 0.5f) < 0.02f)
        // startled while out: it dashes into the crevice and presses flat
        val ag = s.agentOf(o)!!
        assertTrue("out of the crevice at night (${o.pos.distanceTo(b.home)} cm)", o.pos.distanceTo(b.home) > 4.0)
        ag.brain.onTap(ag.ctx, o.pos + Vec3(1.0, 1.0, 0.0), 0.8)
        var tuck = 0f
        s.advance(12.0, behaviorDt = 0.05) { tuck = maxOf(tuck, (o.limbRig as CrabLegs).tuckAmt.toFloat()) }
        assertTrue("darted (${b.darts})", b.darts >= 1)
        assertTrue("back in the crevice (${o.pos.distanceTo(b.home)} cm, ${o.behaviorName})", o.pos.distanceTo(b.home) < 3.0)
        assertTrue("pressed flat, legs drawn in (w $tuck)", tuck > 0.5f)
    }

    @Test
    fun crabLegsStepInAnAlternatingTetrapod() {
        val rig = LimbRig.forKind(BrainKind.SALLY_CRAB, 1L) as CrabLegs
        val s = TestTank.sim(stock("crab"), seed = 7L, startHour = 23.0)
        val o = s.org(BrainKind.SALLY_CRAB)
        o.heading = Vec3.X; o.up = Vec3.Y
        o.vel = Vec3(0.0, 0.0, 8.0)        // walking toward its right (model +Z)
        rig.travel = o.vel
        repeat(20) { rig.step(o, 0.05, 8.0) }
        fun ph(k: Int) = rig.out[k * 4]
        fun d(a: Int, b: Int) = abs(((ph(a) - ph(b) + 1.5f) % 1f) - 0.5f)
        // L1 (0) and R1 (1) half a cycle apart; L1 and R2 (3) in the same group; L1 and L2 (2) apart
        assertTrue("L1/R1 antiphase", d(0, 1) > 0.35f)
        assertTrue("L1/R2 together", d(0, 3) < 0.15f)
        assertTrue("L1/L2 antiphase", d(0, 2) > 0.35f)
        // leading (right) legs pull: negative stroke; trailing (left) push
        assertTrue(rig.out[1 * 4 + 1] < 0f && rig.out[0 * 4 + 1] > 0f)
        assertTrue("stride frequency ${rig.strideHz}", rig.strideHz in 1.5..7.0)
    }
}
