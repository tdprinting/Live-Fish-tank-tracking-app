package com.reeflive.core.sim

import com.reeflive.core.geom.AnalyticSdf
import com.reeflive.core.geom.Ellipsoid
import com.reeflive.core.geom.Rng
import com.reeflive.core.geom.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Corals added from Tee's photos 2026-10-13 (docs/research/video/tee_tank_photos.md). */
class LeatherCoralTest {
    private val rock = RockData(AnalyticSdf(listOf(Ellipsoid(Vec3(60.0, 3.0, 16.0), Vec3(40.0, 18.0, 14.0)))), AnchorSet(emptyList()))

    private fun coral(id: String, speciesId: String, near: Vec3): Coral {
        val p = rock.sdf.project(near, 0.2)
        return Coral(id, TestTank.catalog[speciesId], id, "", p, rock.sdf.gradient(p), 0.0)
    }

    @Test
    fun newSpeciesResolveToTheirKinds() {
        for (id in listOf("sarcophyton_sp", "sinularia_sp", "lobophytum_sp")) assertEquals(id, CoralKind.LEATHER, CoralKind.resolve(id))
        assertEquals(CoralKind.MUSHROOM, CoralKind.resolve("amplexidiscus_fenestrafer"))
        assertEquals(CoralKind.GSP, CoralKind.resolve("briareum_sp"))
    }

    /** [M] leathers close for a few days every couple of weeks to a month to shed a waxy film; flow clears it faster. */
    @Test
    fun leathersShedAFewDaysEveryFewWeeks() {
        val c = coral("toadstool", "sarcophyton_sp", Vec3(60.0, 25.0, 16.0))
        val period = CoralSim.SHED_PERIOD_DAYS
        val hours = (period * 24).toInt()
        val shedHours = (0 until hours).count { CoralSim.shedding(c, it * 3600.0, 1.0) }
        val weakFlow = (0 until hours).count { CoralSim.shedding(c, it * 3600.0, 0.1) }
        println("leather shed: ${shedHours / 24.0} d per ${period} d cycle (weak flow ${weakFlow / 24.0} d)")
        assertEquals(CoralSim.SHED_DAYS * 24, shedHours.toDouble(), 1.0)
        assertTrue("weak flow sheds longer: $weakFlow vs $shedHours", weakFlow > 1.8 * shedHours)
        // non-leathers never shed
        assertTrue((0 until hours).none { CoralSim.shedding(coral("gsp", "briareum_sp", Vec3(60.0, 25.0, 16.0)), it * 3600.0) })

        // while shedding the colony stays closed at midday; outside a shed it opens
        val t0 = (0 until hours).first { CoralSim.shedding(c, it * 3600.0) } * 3600.0
        val ws = WaterState()
        fun extAt(start: Double): Double {
            val x = coral("toadstool", "sarcophyton_sp", Vec3(60.0, 25.0, 16.0))
            repeat(12 * 60) { CoralSim.step(x, ws, 0.6, 300.0, 300.0, 1.0, start + it * 60.0, ArrayList()) }
            return x.extension
        }
        val during = extAt(t0)
        val after = extAt(t0 + (CoralSim.SHED_DAYS + 2) * 86400)
        println("leather extension while shedding ${"%.2f".format(during)}, after ${"%.2f".format(after)}")
        assertTrue("closed while shedding: $during", during < 0.2)
        assertTrue("open otherwise: $after", after > 0.6)
    }

    /** Sammarco et al. 1983 / [M]: leather terpenes harm neighbours within ~15 cm; carbon helps. */
    @Test
    fun leatherTerpenesStressCloseNeighbours() {
        val leather = coral("finger", "sinularia_sp", Vec3(60.0, 25.0, 16.0)).also { it.size = 1.5 }
        val near = coral("zoaNear", "zoanthus_sp", Vec3(60.0 + CoralGrowth.extent(leather) + 5.0 + 4.0, 25.0, 16.0))
        val far = coral("zoaFar", "zoanthus_sp", Vec3(60.0 + CoralGrowth.extent(leather) + 30.0, 25.0, 16.0))
        val ws = WaterState()
        CoralGrowth.compete(listOf(leather, near, far), ws, 0.0)
        val s0 = near.neighborStress
        near.baseline = null
        CoralGrowth.compete(listOf(leather, near, far), ws, 1.0)
        println("leather -> zoa 4 cm ${"%.2f".format(s0)} (carbon ${"%.2f".format(near.neighborStress)}), far ${far.neighborStress}")
        assertTrue("near stressed: $s0", s0 > 0.1)
        assertEquals(0.0, far.neighborStress, 1e-9)
        assertTrue("carbon helps", near.neighborStress < 0.6 * s0)
        assertTrue("leathers add to the tank's allelochemicals", CoralGrowth.alleloSource(listOf(leather)) > 0.0)
    }

    /** [M] "a 5 cm frag can reach about 20 cm across within a year" (good conditions). */
    @Test
    fun toadstoolGrowsSeveralFoldInAYear() {
        val c = coral("toadstool", "sarcophyton_sp", Vec3(60.0, 25.0, 16.0))
        c.acclLight = 0.6
        val rng = Rng(5)
        val w = WaterState(no3 = 8.0, po4 = 0.05)
        repeat(180 * 24) { CoralGrowth.step(c, w, rock, rng, 60.0, 1) { "x" } }
        val half = c.size
        repeat(185 * 24) { CoralGrowth.step(c, w, rock, rng, 60.0, 1) { "x" } }
        println("toadstool size: 180 d ${"%.2f".format(half)}, 365 d ${"%.2f".format(c.size)}")
        assertTrue("grew: $half", half in 1.3..2.5)
        assertTrue("capped: ${c.size}", c.size <= CoralGrowth.growth(CoralKind.LEATHER).maxSize + 1e-9 && c.size > 2.0)
    }

    @Test
    fun teesNewCoralsSitOnTheirAnchors() {
        val sim = TestTank.sim(DefaultStock.corals)
        for ((sp, anchor) in listOf(
            "sarcophyton_sp" to "coral_toadstool_front", "lobophytum_sp" to "coral_leather_pink_front",
            "sinularia_sp" to "coral_finger_leather", "amplexidiscus_fenestrafer" to "coral_elephant_ear",
        )) {
            val c = sim.corals.firstOrNull { it.species.id == sp }
            assertNotNull(sp, c)
            assertEquals(sp, anchor, c!!.anchor)
            val a = TestTank.rock.anchors[anchor]!!
            assertTrue("$sp at its anchor", c.pos.distanceTo(a.pos) < 1e-6)
            assertEquals(anchor, Stocking.homeAnchor(TestTank.rock, c.species)?.name)
        }
        // a day of the default tank with the new corals keeps them healthy
        sim.advanceAbstract(86400.0)
        for (c in sim.corals.filter { it.kind == CoralKind.LEATHER }) assertTrue("${c.id} tissue ${c.tissue}", c.tissue > 0.95)
    }
}
