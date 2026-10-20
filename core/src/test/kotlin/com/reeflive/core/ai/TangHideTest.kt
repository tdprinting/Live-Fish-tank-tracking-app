package com.reeflive.core.ai

import com.reeflive.core.geom.Vec3
import com.reeflive.core.sim.BrainKind
import com.reeflive.core.sim.Command
import com.reeflive.core.sim.DefaultStock
import com.reeflive.core.sim.Organism
import com.reeflive.core.sim.Simulation
import com.reeflive.core.sim.TestTank
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Dori hides under the rock when scared and to sleep, rubs on the rock and races across the tank (Tee 2026-10-15). */
class TangHideTest {
    private fun Simulation.tang() = organisms.first { it.brainKind == BrainKind.TANG }
    private fun Simulation.brain() = agentOf(tang())!!.brain as TangBrain

    /** Deepest point of the tang's body inside the rock (cm, negative = inside), from its nose, tail, fin tips and flanks. */
    private fun Simulation.bodyDepth(o: Organism): Double {
        val h = o.heading.normalized(Vec3.X); val up = o.up.rejectFrom(h).normalized(Vec3.Y)
        val side = (h cross up).normalized(Vec3.Z); val L = o.lengthCm
        val a = TangShelters.HALF_LEN * L; val b = TangShelters.HALF_DEPTH * L; val t = TangShelters.HALF_THICK * L
        return listOf(o.pos, o.pos + h * a, o.pos - h * a, o.pos + up * b, o.pos - up * b, o.pos + side * t, o.pos - side * t)
            .minOf { rock.sdf.dist(it) }
    }

    @Test
    fun sheltersAreUnderRockAndFitTheBody() {
        val sim = TestTank.sim(DefaultStock.all, seed = 1L, startHour = 12.0)
        val sh = sim.brain().shelters
        assertTrue("shelters ${sh.spots.size}", sh.spots.size >= 4)
        val L = sim.tang().lengthCm
        val poses = sh.spots.mapNotNull { sh.fit(it.pos, L, TangHide.HIDE_ROLL, roof = true) }
        assertTrue("fitted ${poses.size}", poses.size >= 3)
        for (p in poses) {
            assertNotNull("rock overhead at ${p.pos}", sim.rock.sdf.raycast(p.pos, Vec3.Y, 25.0))
            assertTrue("body clear at ${p.pos}", sh.clear(p.pos, p.heading, p.up, L, 0.0))
            assertTrue("snug at ${p.pos}", sim.rock.sdf.dist(p.pos) < 0.5 * L)
        }
    }

    @Test
    fun aHardTapSendsItUnderTheRockThenItComesOut() {
        var hid = 0; var minDepth = 9.0; var covered = 0; var settledTicks = 0; var cameOut = 0
        for (seed in 1L..3L) {
            val sim = TestTank.sim(DefaultStock.all, seed = seed, startHour = 12.0)
            val tang = sim.tang(); val b = sim.brain()
            val hide = b.utility.behaviors.filterIsInstance<TangHide>().single()
            sim.advance(30.0, behaviorDt = 0.1)
            sim.enqueue(Command.TapGlass(tang.pos))
            var seen = false
            sim.advance(400.0, behaviorDt = 0.1) {
                val p = hide.hidePose
                if (b.utility.current === hide) minDepth = minOf(minDepth, sim.bodyDepth(tang))
                if (b.utility.current === hide && p != null && tang.pos.distanceTo(p.pos) < 1.0) {
                    seen = true; settledTicks++
                    if (sim.rock.sdf.raycast(tang.pos, Vec3.Y, 25.0) != null) covered++
                }
            }
            if (seen) hid++
            if (hide.hides > 0) cameOut++
        }
        assertTrue("hid in $hid of 3", hid >= 2)
        assertTrue("under rock ${covered} of $settledTicks", covered >= 0.9 * settledTicks)
        assertTrue("clipped ${"%.2f".format(minDepth)} cm into rock", minDepth > -0.1)
        assertTrue("came back out in $cameOut of 3", cameOut >= 1)
    }

    @Test
    fun itSleepsWedgedUnderAnOverhang() {
        val sim = TestTank.sim(DefaultStock.all, seed = 1L, startHour = 22.5)
        val tang = sim.tang(); val b = sim.brain()
        val bed = b.bedPose(sim.agentOf(tang)!!.ctx)
        assertNotNull(bed); bed!!
        assertNotNull("roof over the bed", sim.rock.sdf.raycast(bed.pos, Vec3.Y, b.shelters.roofMax(tang.lengthCm)))
        // wedged in, walled round by rock: not just lying in a rock's shadow (Tee 2026-10-20)
        assertTrue("snug ${b.shelters.snug(bed.pos, tang.lengthCm)}", b.shelters.snug(bed.pos, tang.lengthCm) >= 0.6)
        var minDepth = 9.0
        sim.advance(600.0, behaviorDt = 0.1) { minDepth = minOf(minDepth, sim.bodyDepth(tang)) }
        var there = 0; var n = 0
        sim.advance(120.0, behaviorDt = 0.1) {
            n++
            minDepth = minOf(minDepth, sim.bodyDepth(tang))
            if (tang.sleeping && tang.pos.distanceTo(bed.pos) < 1.5) there++
        }
        assertTrue("in its crevice $there of $n", there >= 0.8 * n)
        assertTrue("clipped ${"%.2f".format(minDepth)}", minDepth > -0.1)
    }

    @Test
    fun itRubsOnTheRockWithoutGoingIntoIt() {
        var rubs = 0; var touch = 9.0; var minDepth = 9.0
        for (seed in 1L..3L) {
            val sim = TestTank.sim(DefaultStock.all, seed = seed, startHour = 11.0)
            val tang = sim.tang(); val b = sim.brain()
            tang.hunger = 0.2
            val rub = b.utility.behaviors.filterIsInstance<TangRub>().single()
            sim.advance(20.0, behaviorDt = 0.1)
            rub.nextAt = 0.0
            sim.advance(60.0, behaviorDt = 0.1) {
                if (b.utility.current === rub) {
                    val d = sim.bodyDepth(tang)
                    minDepth = minOf(minDepth, d)
                    touch = minOf(touch, d)
                }
            }
            rubs += rub.rubs
        }
        assertTrue("rubs $rubs", rubs >= 2)
        assertTrue("flank came within ${"%.2f".format(touch)} cm of the rock", touch < 1.0)
        assertTrue("clipped ${"%.2f".format(minDepth)} cm into rock", minDepth > -0.1)
    }

    @Test
    fun itRacesAcrossTheTank() {
        var zooms = 0; var top = 0.0; var span = 0.0
        for (seed in 1L..3L) {
            val sim = TestTank.sim(DefaultStock.all, seed = seed, startHour = 11.0)
            val tang = sim.tang(); val b = sim.brain()
            tang.hunger = 0.2
            val z = b.utility.behaviors.filterIsInstance<TangZoom>().single()
            sim.advance(20.0, behaviorDt = 0.1)
            z.nextAt = 0.0
            var x0 = Double.NaN
            sim.advance(30.0, behaviorDt = 0.1) {
                if (b.utility.current === z) {
                    if (x0.isNaN()) x0 = tang.pos.x
                    top = maxOf(top, tang.vel.length() / tang.burstSpeed)
                    span = maxOf(span, kotlin.math.abs(tang.pos.x - x0))
                }
            }
            zooms += z.zooms
        }
        assertTrue("zooms $zooms", zooms >= 2)
        assertTrue("top speed ${"%.2f".format(top)} of burst", top > 0.6)
        assertTrue("crossed ${"%.0f".format(span)} cm", span > 40)
    }

    /** My tank: the rockwork changes (a rock taken out over its bed), and Dori finds a new crevice to sleep in. */
    @Test
    fun itFindsANewCreviceWhenTheRockworkChanges() {
        val cat = com.reeflive.core.sim.SpeciesCatalog.fromClasspath(listOf("paracanthurus_hepatus"))
        val sim = com.reeflive.core.custom.CustomTank.fresh(com.reeflive.core.custom.TankBuilds.DEFAULT, cat, TestTank.rock, 21.0 * 3600, seed = 2L)
        sim.enqueue(Command.AddOrganism("paracanthurus_hepatus", 8.0, "Dori"))
        sim.stepWall(0.05)
        val tang = sim.tang(); val b = sim.brain(); val ctx = sim.agentOf(tang)!!.ctx
        val first = b.bedPose(ctx)!!
        sim.advance(30.0, behaviorDt = 0.1)
        sim.enqueue(Command.RemoveRock(first.pos))
        sim.advance(120.0, behaviorDt = 0.1)
        val bed = b.bedPose(ctx)
        assertTrue("rescanned", b.shelters.generation >= 1)
        assertNotNull(bed); bed!!
        assertTrue("bed clear of the new rockwork", b.shelters.clear(bed.pos, bed.heading, bed.up, tang.lengthCm, 0.0))
        assertNotNull("roof over the new bed", sim.rock.sdf.raycast(bed.pos, Vec3.Y, b.shelters.roofMax(tang.lengthCm)))
        var minDepth = 9.0; var there = 0; var n = 0
        sim.advance(900.0, behaviorDt = 0.1) { minDepth = minOf(minDepth, sim.bodyDepth(tang)) }
        val now = b.bedPose(ctx)!!
        sim.advance(120.0, behaviorDt = 0.1) { n++; if (tang.sleeping && tang.pos.distanceTo(now.pos) < 1.5) there++ }
        assertTrue("asleep in its crevice $there of $n", there >= 0.8 * n)
        assertTrue("clipped ${"%.2f".format(minDepth)}", minDepth > -0.1)
    }
}
