package com.reeflive.core.sim

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** The shrimp's planted feet and cleaning claws (core ShrimpLimbs, research 19). */
class ShrimpLegsTest {
    @Test
    fun feetStayPlantedOnTheSurfaceAndStepInAlternatingTripods() {
        val s = TestTank.sim(listOf(TestTank.seed("skunk")), seed = 4L, startHour = 23.0)
        val o = s.organisms.first { it.brainKind == BrainKind.CLEANER_SHRIMP }
        val rig = o.limbRig as ShrimpLimbs
        val last = arrayOfNulls<com.reeflive.core.geom.Vec3>(6)
        var planted = 0; var off = 0; var steps = 0; var slid = 0; var bothGroups = 0
        val groupA = booleanArrayOf(true, false, false, true, true, false)
        s.advance(900.0, behaviorDt = 0.1) {
            if (!o.attached || o.ridingOn != null) { last.fill(null); return@advance }
            var a = false; var b = false
            for (i in 0 until 6) {
                if (rig.swinging(i)) { if (groupA[i]) a = true else b = true }
                val f = rig.stanceFoot(i)
                if (f == null) { if (rig.swinging(i) && last[i] != null) steps++; last[i] = null; continue }
                planted++
                if (abs(s.crawlSurface.dist(f)) > 0.3) off++
                val p = last[i]
                if (p != null && p !== f && p.distanceTo(f) > 1e-9) slid++
                last[i] = f
            }
            if (a && b) bothGroups++
        }
        assertTrue("planted feet $planted", planted > 1000)
        assertTrue("feet off the surface $off / $planted", off < planted * 0.02)
        assertTrue("stepped $steps", steps > 20)
        // a planted foot never slides: it only moves by lifting and swinging to a new foothold
        assertTrue("slid $slid", slid == 0)
        // the tripods take turns; only a leg left far behind (a hop, a quick turn) steps out of turn
        assertTrue("both tripods in the air together $bothGroups of $steps steps", bothGroups < steps / 10)
    }

    @Test
    fun cleaningShrimpPicksAtItsClientWithItsFrontClaws() {
        val s = TestTank.sim(listOf(TestTank.seed("skunk"), TestTank.seed("tang")), seed = 1L, startHour = 11.0)
        val o = s.organisms.first { it.brainKind == BrainKind.CLEANER_SHRIMP }
        var riding = 0; var reach = 0.0; var onBody = 0; var feet = 0
        val rig = o.limbRig as ShrimpLimbs
        s.advance(3600.0, behaviorDt = 0.1) {
            val c = o.ridingOn ?: return@advance
            riding++
            for (j in 7..10) {
                val r = rig.out
                reach = maxOf(reach, abs(r[j * 4 + 1].toDouble()) + abs(r[j * 4 + 2].toDouble()))
            }
            for (i in 0 until 6) {
                val f = rig.stanceFoot(i) ?: continue
                feet++
                // on the skin: a short ray from just outside the foot toward the client's midline meets the body
                val h = c.heading.normalized()
                val mid = c.pos - h * (0.5 * c.lengthCm)
                val inward = (mid + h * ((f - mid) dot h) - f).normalized(-c.up)
                if (ShrimpLimbs.onBody(c, f - inward * 0.3, inward, 0.6) != null) onBody++
            }
        }
        assertTrue("cleaned a client ($riding ticks)", riding > 50)
        assertTrue("claws reached out ($reach)", reach > 0.04)
        assertTrue("feet on the client $onBody / $feet", feet > 0 && onBody > feet * 0.7)
    }
}
