package com.reeflive.core.sim

import com.reeflive.core.geom.AnalyticSdf
import com.reeflive.core.geom.Ellipsoid
import com.reeflive.core.geom.Rng
import com.reeflive.core.geom.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Coral growth, spread and competition (coverage gaps C-1, C-2, C-8; research 03 C1, C2, D). */
class CoralGrowthTest {
    private fun f(v: Double) = "%.2f".format(v)

    /** Two tall rocks standing in the sand, [gap] cm apart at the sand line. */
    private fun twoRocks(gap: Double): RockData {
        val a = Ellipsoid(Vec3(30.0, 3.0, 16.0), Vec3(8.0, 12.0, 8.0))
        val b = Ellipsoid(Vec3(30.0 + 16.0 + gap, 3.0, 16.0), Vec3(8.0, 12.0, 8.0))
        return RockData(AnalyticSdf(listOf(a, b), smoothK = 0.3), AnchorSet(emptyList()))
    }

    /** A big low mound: a wide rock face for the GSP growth-rate check. */
    private fun mound() = RockData(AnalyticSdf(listOf(Ellipsoid(Vec3(60.0, 3.0, 16.0), Vec3(40.0, 18.0, 14.0)))), AnchorSet(emptyList()))

    private fun coral(id: String, speciesId: String, rock: RockData, near: Vec3): Coral {
        val p = rock.sdf.project(near, 0.2)
        return Coral(id, TestTank.catalog[speciesId], id, "", p, rock.sdf.gradient(p), 0.0)
    }

    private fun grow(c: Coral, rock: RockData, days: Int, w: WaterState = WaterState(no3 = 12.0, po4 = 0.06)) {
        val rng = Rng(5)
        repeat(days * 24) { CoralGrowth.step(c, w, rock, rng, 60.0, 1) { "x" } }
    }

    /** 03 C2: GSP covers a 30 cm rock face in 3-4 months (several cm/month). */
    @Test
    fun gspCoversA30cmFaceIn3To4Months() {
        val rock = mound()
        val gsp = coral("gsp", "briareum_sp", rock, Vec3(60.0, 25.0, 16.0))
        grow(gsp, rock, 105)
        println("C-1 GSP after 105 d: mat radius ${f(gsp.radiusCm)} cm, size ${f(gsp.size)}, cover ${f(gsp.cover)}")
        assertTrue("mat radius ${gsp.radiusCm}", gsp.radiusCm in 14.0..19.0)
        assertTrue("size ${gsp.size}", gsp.size > 2.0)
        // a stressed, algae-covered colony barely spreads
        val sick = coral("gsp2", "briareum_sp", rock, Vec3(60.0, 25.0, 16.0)).also { it.stress = 0.6; it.algaeCover = 0.5 }
        grow(sick, rock, 105)
        assertTrue("stressed GSP grew ${sick.radiusCm}", sick.radiusCm < 8.0)
    }

    /** 03 C2: "GSP mat rarely crosses a sand gap > 5 cm": it bridges 3 cm but stops at 8 cm. */
    @Test
    fun gspStopsAtASandGap() {
        for ((gap, crosses) in listOf(3.0 to true, 8.0 to false)) {
            val rock = twoRocks(gap)
            val gsp = coral("gsp", "briareum_sp", rock, Vec3(30.0, 13.0, 16.0))
            val farRock = rock.sdf.project(Vec3(30.0 + 8.0 + gap + 1.5, 8.0, 16.0), 0.2)
            grow(gsp, rock, 500)
            val reached = gsp.patch!!.distTo(farRock) <= gsp.radiusCm
            println("C-1 sand gap $gap cm: mat radius ${f(gsp.radiusCm)} reach ${f(gsp.patch!!.maxReach)} cover ${f(gsp.cover)} other rock reached=$reached")
            assertEquals("gap $gap", crosses, reached)
            assertTrue("the mat fills what it can reach: ${gsp.cover}", gsp.cover > 0.95)
            if (!crosses) assertTrue("stopped at the gap: ${gsp.radiusCm}", gsp.radiusCm <= gsp.patch!!.maxReach + SurfacePatch.CELL + 1e-9)
        }
    }

    /** 03 A1/C2: Kenya grows fast and drops branches that root on the rock below; clove polyps are slow. */
    @Test
    fun kenyaGrowsAndShedsBranchesCloveIsSlow() {
        val sim = TestTank.sim(DefaultStock.all)
        // the keeper doses: alkalinity held at 8.5 dKH (growing corals use more than weekly water changes return)
        sim.enqueue(Command.RampParam("alk", 8.5, 0.0, hold = true))
        val kenya = sim.corals.first { it.kind == CoralKind.KENYA }
        val clove = sim.corals.first { it.kind == CoralKind.CLOVE }
        val gsp = sim.corals.first { it.kind == CoralKind.GSP }
        sim.advanceAbstract(60 * 86400.0)
        val kenya60 = kenya.size; val clove60 = clove.radiusCm; val gsp60 = gsp.radiusCm
        sim.advanceAbstract(140 * 86400.0)
        val frags = sim.corals.filter { it.kind == CoralKind.KENYA && it !== kenya }
        println("C-1 day 60: Kenya size ${f(kenya60)} clove r ${f(clove60)} GSP r ${f(gsp60)}; day 200: Kenya ${f(kenya.size)}, ${frags.size} branches rooted , clove r ${f(clove.radiusCm)}, GSP r ${f(gsp.radiusCm)}")
        println("C-1 colonies ${sim.corals.groupingBy { it.kind }.eachCount()} Kenya stress ${f(kenya.stress)} need ${kenya.need} vigour ${f(CoralGrowth.vigor(kenya, sim.water.s))} iodine ${sim.water.s.i} no3 ${f(sim.water.s.no3)}")
        assertTrue("Kenya grew: $kenya60", kenya60 > 1.2)
        assertTrue("Kenya dropped a branch that rooted", frags.isNotEmpty())
        assertTrue("frags start small and grow: ${frags.map { it.size }}", frags.all { it.size in 0.25..2.5 })
        assertTrue("clove slower than GSP: ${clove.radiusCm - 4} vs ${gsp.radiusCm - 6}", clove.radiusCm - 4 < 0.4 * (gsp.radiusCm - 6))
        // poses carry size and cover with defaults for older renderers
        val pose = kenya.toPose()
        assertEquals(kenya.size.toFloat(), pose.size, 1e-6f)
        assertEquals(1f, CoralPose("x", 1f, 0f, 0f, 0f, 0f).size, 0f)
    }

    /** 03 D: Kenya allelopathy within ~10 cm (carbon helps), zoa stings on contact, GSP smothers what it reaches. */
    @Test
    fun neighboursCompeteByDistance() {
        val rock = mound()
        val kenya = coral("kenya", "capnella_sp", rock, Vec3(60.0, 25.0, 16.0)).also { it.size = 2.0 }
        fun zoaAt(dx: Double) = coral("zoa$dx", "zoanthus_sp", rock, Vec3(60.0 + dx, 25.0, 16.0))
        val near = zoaAt(CoralGrowth.extent(kenya) + 5.0 + 4.0)
        val far = zoaAt(CoralGrowth.extent(kenya) + 25.0)
        val ws = WaterState()
        CoralGrowth.compete(listOf(kenya, near, far), ws, 0.0)
        val nearS = near.neighborStress
        println("C-2 Kenya → zoa 4 cm away ${f(nearS)}, 20 cm away ${f(far.neighborStress)}")
        assertTrue("allelopathy near: $nearS", nearS > 0.15)
        assertEquals(0.0, far.neighborStress, 1e-9)
        near.baseline = null
        CoralGrowth.compete(listOf(kenya, near, far), ws, 1.0)
        assertTrue("carbon cuts it: ${near.neighborStress} vs $nearS", near.neighborStress < 0.6 * nearS)
        // a new neighbour loses tissue over weeks; an established one keeps its baseline until the Kenya grows
        near.baseline = null
        repeat(30 * 1440) { CoralSim.step(near, ws, 0.6, 300.0, 300.0, 1.0, 0.0, ArrayList()) }
        assertTrue("zoa tissue ${near.tissue}", near.tissue < 0.85)
        val old = zoaAt(CoralGrowth.extent(kenya) + 5.0 + 4.0).also { it.established = true }
        CoralGrowth.compete(listOf(kenya, old), ws, 0.0)
        assertEquals(0.0, old.neighborStress, 1e-9)
        kenya.size = 2.5
        CoralGrowth.compete(listOf(kenya, old), ws, 0.0)
        assertTrue("growth adds pressure: ${old.neighborStress}", old.neighborStress > 0.0)

        // GSP mat reaches a mushroom 12 cm away along the rock and smothers it
        val gsp = coral("gsp", "briareum_sp", rock, Vec3(40.0, 22.0, 16.0))
        val shroom = coral("shroom", "discosoma_sp", rock, Vec3(54.0, 23.0, 16.0))
        val rng = Rng(3)
        var day = 0
        while (day < 200 && shroom.overgrown <= 0.0) {
            repeat(24) { CoralGrowth.step(gsp, WaterState(no3 = 12.0, po4 = 0.06), rock, rng, 60.0, 0) { "x" } }
            CoralGrowth.compete(listOf(gsp, shroom), ws, 0.0)
            day++
        }
        println("C-2 GSP mat reached the mushroom on day $day (mat radius ${f(gsp.radiusCm)})")
        assertTrue("GSP never reached the mushroom", shroom.overgrown > 0.0 && day in 40..160)
    }

    /** 03 C1: low Ca/Mg stresses soft corals mildly; the calcifying organ pipe more. */
    @Test
    fun lowCalciumMagnesiumMildlyStressesSoftCorals() {
        val sim = TestTank.sim(DefaultStock.corals)
        fun stress(kind: CoralKind, w: WaterState): Double {
            val c = sim.corals.first { it.kind == kind }
            c.acclSg = Double.NaN
            CoralSim.step(c, w, 0.6, 300.0, 300.0, 1.0, 0.0, ArrayList())
            return c.stress
        }
        val low = WaterState(ca = 300.0, mg = 1000.0)
        val zoa0 = stress(CoralKind.ZOA, WaterState()); val zoa = stress(CoralKind.ZOA, low)
        val op0 = stress(CoralKind.ORGAN_PIPE, WaterState()); val op = stress(CoralKind.ORGAN_PIPE, low)
        println("C-8 low Ca/Mg stress: zoa ${f(zoa0)} → ${f(zoa)}, organ pipe ${f(op0)} → ${f(op)}")
        assertTrue("soft coral mildly stressed: $zoa", zoa - zoa0 in 0.1..0.45)
        assertTrue("organ pipe more: $op vs $zoa", op - op0 > zoa - zoa0 + 0.1)
    }
}
