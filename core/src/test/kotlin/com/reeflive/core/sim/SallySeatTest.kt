package com.reeflive.core.sim

import com.reeflive.core.geom.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * The Sally Lightfoot stands on the rock, sand and glass it walks on with every leg planted (SIMULATION §19.6, research
 * 27; Tee 2026-10-15: "it clips around the map and doesn't fit onto rocks"). Every tick of a multi-hour run of the
 * full stock no foot tip, knee or propodus of the seated crab is more than 0.1 cm inside the surface ([CrawlSurface]:
 * rock SDF, live sand bed, glass), and the carapace is inside it, or standing more than 0.3 cm off it, on at most one
 * tick in a thousand: the moments it squeezes out of a slot behind the rock narrower than its 5 cm carapace, where
 * no seat fits until it has walked out (SallyCrabBrain.Squeeze; before the seat it was drawn through the rock on most
 * ticks on the rock).
 */
class SallySeatTest {

    private class Stats {
        var samples = 0L
        var maxGap = 0.0
        var worstBody = 0.0
        var worstFoot = 0.0
        var worstKnee = 0.0
        var gapFails = 0
        var bodyFails = 0
        var footFails = 0
        var kneeFails = 0
        var folded = 0L
        var onRock = 0L
        var steep = 0L
        var walking = 0L
        val notes = ArrayList<String>()
        val counts = HashMap<String, Int>()
        val worst = HashMap<String, Pair<Double, String>>()
        fun note(s: String) {
            val key = s.substringBefore(' ') + " " + s.substringAfter("t=").substringAfter(' ').substringBefore(" p")
            val n = counts.getOrElse(key) { 0 }; counts[key] = n + 1; if (n < 2) notes += s
            val v = s.split(" ").firstNotNullOfOrNull { it.toDoubleOrNull() } ?: 0.0
            val k0 = s.substringBefore(' ')
            if (v > (worst[k0]?.first ?: -1.0)) worst[k0] = v to s
        }
    }

    private fun check(sim: Simulation, st: Stats) {
        val surf = sim.crawlSurface
        for (o in sim.organisms) {
            if (o.brainKind != BrainKind.SALLY_CRAB || !o.alive || !o.visible) continue
            val seat = o.seat ?: continue
            if (!o.seatOn || seat.blend < 1.0) continue
            val m = seat.crab!!
            val rig = o.limbRig as CrabLegs
            val L = o.lengthCm
            st.samples++
            if (sim.rock.sdf.dist(seat.pos) < 0.5) st.onRock++
            if (seat.up.y < 0.5) st.steep++
            if (rig.stepping > 0.5) st.walking++
            // the carapace and coxae (the stance points are virtual: the feet stand there)
            var minD = Double.MAX_VALUE
            val c = seat.shape.contacts
            var i = 0
            while (i < CrabModel.STANCE_FROM * 3) { minD = min(minD, surf.dist(seat.world(c[i], c[i + 1], c[i + 2], L))); i += 3 }
            // what the body stands on: the lowest of the carapace and the planted feet
            var support = minD
            for (k in 0 until 8) if (!seat.footSwing[k] && !seat.legTucked[k]) support = min(support, surf.dist(seat.footTip[k]))
            st.maxGap = max(st.maxGap, support)
            st.worstBody = min(st.worstBody, minD)
            val where = "t=%.0f %s p%d at %s up %s".format(sim.simTimeSec, o.behaviorName, o.posture, seat.pos, seat.up)
            if (support > GAP_MAX) { st.gapFails++; st.note("gap %.2f cm $where body %.2f feet ${(0 until 8).joinToString(",") { k -> (if (seat.footSwing[k]) "s" else if (seat.legTucked[k]) "f" else "") + "%.1f".format(surf.dist(seat.footTip[k])) }} sim %.2f".format(support, minD, surf.dist(o.pos))) }
            if (minD < -PEN_MAX) { st.bodyFails++; st.note("body %.2f cm inside $where".format(-minD)) }
            for (k in 0 until 8) {
                if (seat.legTucked[k]) { st.folded++; continue }
                val d = surf.dist(seat.footTip[k])
                st.worstFoot = min(st.worstFoot, d)
                if (d < -PEN_MAX) { st.footFails++; st.note("foot $k %.2f cm inside swing=${seat.footSwing[k]} $where".format(-d)) }
                val dk = min(surf.dist(seat.posedLeg(m, k, m.knee, k * 3, m.kneeT, L, rig.out)),
                    surf.dist(seat.posedLeg(m, k, m.mid, k * 3, CrawlerSeat.MID_T, L, rig.out)))
                st.worstKnee = min(st.worstKnee, dk)
                if (dk < -PEN_MAX) { st.kneeFails++; st.note("knee $k %.2f cm inside $where".format(-dk)) }
            }
        }
    }

    private fun run(seed: Long, hour: Double, hours: Double, dt: Double): Stats {
        val sim = TestTank.sim(DefaultStock.all, seed = seed, startHour = hour)
        val st = Stats()
        var left = hours * 3600
        while (left > 1e-6) {
            val chunk = minOf(600.0, left)
            sim.advance(chunk, behaviorDt = dt) { check(sim, st) }
            left -= chunk
        }
        return st
    }

    @Test
    fun sallyStandsOnTheSurfaceWithEveryFootPlanted() {
        // dusk into the night (out grazing on rock and glass) at the live tick, and a day pressed into its crevice at 10x
        val runs = listOf("seed 7 dusk 20 Hz" to run(7, 18.5, 2.0, 0.05), "seed 8 day 10x" to run(8, 9.0, 3.0, 0.5))
        for ((name, st) in runs) {
            println("[$name] %d samples (%d on rock, %d on faces steeper than 60°, %d walking, %d leg-ticks folded): max gap %.3f cm, deepest body %.3f, foot %.3f, knee %.3f cm"
                .format(st.samples, st.onRock, st.steep, st.walking, st.folded, st.maxGap, -st.worstBody, -st.worstFoot, -st.worstKnee))
            st.notes.forEach { println("   $it") }
            st.worst.values.forEach { println("   WORST ${it.second}") }
            println("   counts ${st.counts}")
        }
        for ((i, r) in runs.withIndex()) {
            val (name, st) = r
            val live = i == 0
            assertTrue("$name: crab seated", st.samples > 1000)
            assertTrue("$name: gap under the crab over $GAP_MAX cm on ${st.gapFails} ticks", st.gapFails <= st.samples / 1000)
            // (none at the live 20 Hz tick; at 10x fast-forward its half-second steps can still carry it a tick into a
            // slot before the seat stops it)
            assertTrue("$name: carapace inside the surface on ${st.bodyFails} ticks", st.bodyFails <= (if (live) 0L else st.samples / 2000))
            assertEquals("$name: foot tips inside the surface", 0, st.footFails)
            assertEquals("$name: knees inside the surface", 0, st.kneeFails)
            assertTrue("$name: legs mostly find footholds", st.folded < st.samples * 8 / 20)
        }
        assertTrue("walks on the rock", runs.sumOf { it.second.onRock } > 0 && runs[0].second.walking > 0)
    }

    @Test
    fun plantedFeetHoldStillThroughStanceAndTheGaitIsATetrapod() {
        // walking steadily sideways (toward its right) over the open sand at the front of the tank
        val sim = TestTank.sim(listOf(TestTank.seed("crab")), seed = 7L, startHour = 23.0)
        val o = sim.organisms.first { it.brainKind == BrainKind.SALLY_CRAB }
        val seat = o.seat!!
        val rig = o.limbRig as CrabLegs
        val surf = sim.crawlSurface
        val z0 = sim.rock.widthCm - 7.0
        fun ground(x: Double, z: Double): Vec3 { val top = Vec3(x, 20.0, z); return top + Vec3(0.0, -1.0, 0.0) * surf.cast(top, Vec3(0.0, -1.0, 0.0), 30.0) }
        val off = 0.5 * o.species.depthRatio * o.lengthCm + 0.2
        val speed = 4.0
        val dt = 0.05
        o.heading = Vec3(0.0, 0.0, -1.0)   // facing the back glass: walking along +X is toward its right
        o.up = Vec3.Y
        var x = 15.0
        var downMin = 8; var downMax = 0; var offSand = 0; var stanceTicks = 0; var slip = 0.0; var touchdowns = 0
        val wasStance = BooleanArray(8); val last = Array(8) { Vec3.ZERO }
        val flexStart = DoubleArray(8); val flexGrow = IntArray(8); val flexShrink = IntArray(8)
        for (tick in 0 until 260) {
            x += speed * dt
            o.pos = ground(x, z0) + Vec3.Y * off
            o.vel = Vec3(speed, 0.0, 0.0)
            rig.travel = o.vel
            rig.step(o, dt, speed)
            seat.update(o, true, surf, dt, off)
            if (tick < 60) continue   // seat blended in, gait settled
            var down = 0
            for (k in 0 until 8) {
                val stance = !seat.footSwing[k] && !seat.legTucked[k]
                val flex = (rig.out[(10 + k) * 4] - CrawlerSeat.FLEX0) / CrawlerSeat.FLEX_SCALE.toDouble()
                if (stance) {
                    down++
                    if (abs(surf.dist(seat.footTip[k])) > 0.15) { offSand++; if (offSand < 6) println("off sand k$k %.2f tick $tick ph %.2f target %.2f tuck ${seat.legTucked[k]}".format(surf.dist(seat.footTip[k]), rig.out[k * 4], surf.dist(seat.footTarget[k]))) }
                    if (wasStance[k]) { stanceTicks++; slip += seat.footTip[k].distanceTo(last[k]) } else { touchdowns++; flexStart[k] = flex }
                } else if (wasStance[k]) {
                    // the stance stroke just ended: legs on the leading side (its right, odd k) flexed, trailing ones extended
                    if (flex > flexStart[k] + 0.02) flexGrow[k]++ else if (flex < flexStart[k] - 0.02) flexShrink[k]++
                }
                wasStance[k] = stance; last[k] = seat.footTip[k]
            }
            downMin = min(downMin, down); downMax = max(downMax, down)
        }
        println("replants ${seat.replants}, off sand $offSand, feet down $downMin..$downMax, $touchdowns touch-downs, stance slip %.4f cm/tick, flexed ${flexGrow.toList()} extended ${flexShrink.toList()}".format(slip / max(1, stanceTicks)))
        // alternating tetrapod at duty factor 0.6 (with the back-to-front lag): four to six feet down
        assertTrue("at least 4 feet down ($downMin)", downMin >= 4)
        assertTrue("up to 6 feet down ($downMax)", downMax >= 6)
        assertTrue("planted feet do not skate", slip / max(1, stanceTicks) < 0.02)
        assertTrue("stance feet re-placed ${seat.replants} times", seat.replants < touchdowns / 10)
        assertTrue("it steps", touchdowns > 30)
        // leading legs (its right: the odd legs) pull by flexing, trailing legs (even) push by extending
        // (the rear pair points almost straight back, so a sideways stroke hardly changes its reach: the rear leading
        // leg only must not push)
        for (k in 1 until 6 step 2) assertTrue("leading leg $k flexes in stance", flexGrow[k] > 3 * flexShrink[k])
        assertTrue("rear leading leg does not extend", flexShrink[7] <= flexGrow[7])
        assertTrue("trailing legs extend in stance", (0 until 8 step 2).sumOf { flexShrink[it] } > 2 * (0 until 8 step 2).sumOf { flexGrow[it] })
        val pose = o.toPose()
        assertEquals(CrabLegs.COUNT * 4, pose.limbs.size)
        assertEquals("legs planted flag", 1f, pose.limbs[18 * 4 + 3])
    }

    companion object {
        const val GAP_MAX = 0.3
        const val PEN_MAX = 0.1
    }
}
