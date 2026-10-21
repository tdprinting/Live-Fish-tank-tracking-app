package com.reeflive.core.sim

import com.reeflive.core.geom.AnalyticSdf
import com.reeflive.core.geom.Ellipsoid
import com.reeflive.core.geom.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Stony corals pack 1: hammer, torch, frogspawn, duncan (docs/research/stony_corals.md). */
class StonyCoralTest {
    private val rock = RockData(AnalyticSdf(listOf(Ellipsoid(Vec3(60.0, 3.0, 16.0), Vec3(40.0, 18.0, 14.0)))), AnchorSet(emptyList()))

    private fun coral(id: String, speciesId: String, near: Vec3): Coral {
        val p = rock.sdf.project(near, 0.2)
        return Coral(id, TestTank.catalog[speciesId], id, "", p, rock.sdf.gradient(p), 0.0)
    }

    private val euphyllia = listOf("fimbriaphyllia_ancora", "euphyllia_glabrescens", "fimbriaphyllia_divisa")

    @Test
    fun speciesResolveAndCanBeAdded() {
        for (id in euphyllia) assertEquals(id, CoralKind.EUPHYLLIA, CoralKind.resolve(id))
        assertEquals(CoralKind.LPS, CoralKind.resolve("duncanopsammia_axifuga"))
        for (id in euphyllia + "duncanopsammia_axifuga") {
            assertTrue(id, CoralKind.resolve(id).stony)
            assertNotNull("in Add life", Stocking.entry(id))
            assertNotNull("sheet", TestTank.catalog[id].common.takeIf { it.isNotBlank() })
            assertNotNull("sting", StonyCorals.sting(TestTank.catalog[id]))
        }
        // soft corals keep their old kinds and no sting table
        assertNull(StonyCorals.sting(TestTank.catalog["sarcophyton_sp"]))
        assertTrue(!CoralKind.LEATHER.stony)
    }

    /** Euphyllia keep their day tentacles half out at night and send out sweepers; by day the sweepers are in. */
    @Test
    fun nightTentaclesComeOutAfterDark() {
        val ws = WaterState()
        val c = coral("hammer", "fimbriaphyllia_ancora", Vec3(60.0, 25.0, 16.0))
        repeat(6 * 60) { CoralSim.step(c, ws, 0.6, 300.0, 300.0, 1.0, it * 60.0, ArrayList()) }
        val day = c.tentacles
        val dayExt = c.extension
        repeat(4 * 60) { CoralSim.step(c, ws, 0.0, null, null, 1.0, 86400.0 + it * 60.0, ArrayList()) }
        println("hammer: day tentacles ${"%.2f".format(day)} ext ${"%.2f".format(dayExt)}, night sweepers ${"%.2f".format(c.tentacles)} ext ${"%.2f".format(c.extension)}")
        assertTrue("sweepers in by day: $day", day < 0.05)
        assertTrue("some sweepers at night: ${c.tentacles}", c.tentacles > 0.25)
        assertTrue("day tentacles partly in at night", c.extension < dayExt)
        // duncan: full feeding tentacles at night
        val d = coral("duncan", "duncanopsammia_axifuga", Vec3(60.0, 25.0, 16.0))
        repeat(4 * 60) { CoralSim.step(d, ws, 0.0, null, null, 1.0, it * 60.0, ArrayList()) }
        assertTrue("duncan feeding tentacles: ${d.tentacles}", d.tentacles > 0.8)
        // a leather has none
        val l = coral("toad", "sarcophyton_sp", Vec3(60.0, 25.0, 16.0))
        repeat(60) { CoralSim.step(l, ws, 0.0, null, null, 1.0, it * 60.0, ArrayList()) }
        assertEquals(0.0, l.tentacles, 0.0)
    }

    /**
     * A torch grows sweepers against a leather in reach and stings it at night; one out of reach (or a frogspawn
     * beside it: Euphyllia tolerate each other) is left alone.
     */
    @Test
    fun sweepersGrowAgainstANeighbourAndStingAtNight() {
        val torch = coral("torch", "euphyllia_glabrescens", Vec3(50.0, 25.0, 16.0))
        val near = coral("near", "sinularia_sp", Vec3(66.0, 25.0, 16.0))
        val far = coral("far", "sinularia_sp", Vec3(95.0, 20.0, 16.0))
        val frog = coral("frog", "fimbriaphyllia_divisa", Vec3(38.0, 24.0, 16.0))
        val all = listOf(torch, near, far, frog)
        val gapNear = StonyCorals.gap(torch, near)
        println("torch-leather gap ${"%.1f".format(gapNear)} cm, far ${"%.1f".format(StonyCorals.gap(torch, far))} cm")
        assertTrue(gapNear < StonyCorals.sting(torch.species)!!.sweeperCm && gapNear > StonyCorals.sting(torch.species)!!.dayCm)
        repeat(10 * 24) { StonyCorals.developSweepers(torch, all, 60.0) }
        assertTrue("sweepers grown: ${torch.sweepers}", torch.sweepers > 0.9)
        torch.tentacles = 0.0
        val day = CoralGrowth.pressure(torch, near, 0.0).first
        torch.tentacles = 1.0
        val night = CoralGrowth.pressure(torch, near, 0.0).first
        println("torch on leather: day ${"%.3f".format(day)}, night ${"%.3f".format(night)}")
        assertEquals("day tentacles do not reach", 0.0, day, 1e-9)
        assertTrue("sweepers sting at night: $night", night > 0.1)
        assertEquals(0.0, CoralGrowth.pressure(torch, far, 0.0).first, 1e-9)
        assertEquals("Euphyllia tolerate each other", 0.0, CoralGrowth.pressure(torch, frog, 0.0).first, 1e-9)
        assertEquals(0.0, CoralGrowth.pressure(frog, torch, 0.0).first, 1e-9)
        // nothing left to fight: the sweepers are reabsorbed over weeks
        repeat(30 * 24) { StonyCorals.developSweepers(torch, listOf(torch, frog), 60.0) }
        assertTrue("reabsorbed: ${torch.sweepers}", torch.sweepers < 0.1)
    }

    /** Pack 2: acan, candy cane, open brain and bubble are meaty LPS; the open brain sits loose on the sand. */
    @Test
    fun pack2CoralsResolveAndTheOpenBrainSitsOnTheSand() {
        val pack2 = listOf("micromussa_lordhowensis", "caulastrea_furcata", "trachyphyllia_geoffroyi", "plerogyra_sinuosa")
        for (id in pack2) {
            assertEquals(id, CoralKind.LPS, CoralKind.resolve(id))
            assertNotNull("in Add life", Stocking.entry(id))
            assertNotNull("sting", StonyCorals.sting(TestTank.catalog[id]))
        }
        // the bubble coral's night sweepers outreach the other meaty LPS
        assertTrue(StonyCorals.sting(TestTank.catalog["plerogyra_sinuosa"])!!.sweeperCm > StonyCorals.sting(TestTank.catalog["micromussa_lordhowensis"])!!.sweeperCm)
        val sim = TestTank.sim(emptyList())
        assertEquals(2, Stocking.addMany(sim, "trachyphyllia_geoffroyi", 2))
        for (c in sim.corals.filter { it.species.id == "trachyphyllia_geoffroyi" })
            assertTrue("on the sand: ${c.pos}", c.pos.y <= sim.rock.sandTopCm + 0.5)
    }

    /**
     * Pack 3: SPS go high on the rock, suffer more from phosphate than LPS; xenia pulses in good water and stops with
     * low alkalinity, used-up iodine or a hard jet.
     */
    @Test
    fun spsSitHighAndXeniaPulsesOnlyInGoodWater() {
        for (id in listOf("montipora_capricornis", "seriatopora_hystrix", "acropora_millepora")) {
            assertEquals(id, CoralKind.SPS, CoralKind.resolve(id))
            assertTrue(CoralKind.SPS.stony)
            assertNull("no sweepers", StonyCorals.sting(TestTank.catalog[id]))
        }
        assertEquals(CoralKind.XENIA, CoralKind.resolve("xenia_umbellata"))
        val sim = TestTank.sim(emptyList())
        assertEquals(2, Stocking.addMany(sim, "acropora_millepora", 2))
        for (c in sim.corals.filter { it.species.id == "acropora_millepora" }) {
            assertTrue("up in the light: ${c.pos}", c.pos.y >= sim.rock.sandTopCm + 8.0)
            assertTrue(c.toPose().isCopy)
        }
        fun stressAt(speciesId: String, w: WaterState): Double {
            val c = coral(speciesId, speciesId, Vec3(60.0, 25.0, 16.0))
            repeat(120) { CoralSim.step(c, w, 0.7, 300.0, 300.0, 1.0, it * 60.0, ArrayList()) }
            return c.stress
        }
        val highP = WaterState(po4 = 0.25)
        assertTrue(stressAt("acropora_millepora", highP) > stressAt("micromussa_lordhowensis", highP))
        fun pulse(w: WaterState, flow: Double = 1.0): Double {
            val x = coral("xenia", "xenia_umbellata", Vec3(60.0, 25.0, 16.0))
            repeat(6 * 60) { CoralSim.step(x, w, 0.7, 300.0, 300.0, 1.0, it * 60.0, ArrayList(), flowStrength = flow) }
            return x.tentacles
        }
        val good = pulse(WaterState(i = 0.05))
        val lowAlk = pulse(WaterState(alk = 6.3, i = 0.05))
        val noIodine = pulse(WaterState(i = 0.0))
        val jet = pulse(WaterState(i = 0.05), flow = 2.0)
        println("xenia pulse: good ${"%.2f".format(good)}, alk 6.3 ${"%.2f".format(lowAlk)}, no iodine ${"%.2f".format(noIodine)}, jet ${"%.2f".format(jet)}")
        assertTrue("pulses in good water: $good", good > 0.5)
        assertTrue(lowAlk < 0.1)
        assertTrue(noIodine < good * 0.5)
        assertTrue(jet < good * 0.5)
    }

    /** Stony corals build a skeleton: low calcium and an alkalinity swing stress them more than a soft coral. */
    @Test
    fun calciumAndAlkalinitySwingsHurtStonyCoralsMore() {
        fun stressAt(speciesId: String, w: WaterState): Double {
            val c = coral(speciesId, speciesId, Vec3(60.0, 25.0, 16.0))
            repeat(120) { CoralSim.step(c, w, 0.6, 300.0, 300.0, 1.0, it * 60.0, ArrayList()) }
            return c.stress
        }
        val lowCa = WaterState(ca = 330.0)
        val stony = stressAt("fimbriaphyllia_ancora", lowCa)
        val soft = stressAt("sinularia_sp", lowCa)
        println("Ca 330: hammer stress ${"%.2f".format(stony)}, finger leather ${"%.2f".format(soft)}")
        assertTrue(stony > soft + 0.05)
        // a 2.5 dKH jump right after acclimating
        fun swing(speciesId: String): Double {
            val c = coral(speciesId, speciesId, Vec3(60.0, 25.0, 16.0))
            repeat(60) { CoralSim.step(c, WaterState(alk = 8.0), 0.6, 300.0, 300.0, 1.0, it * 60.0, ArrayList()) }
            CoralSim.step(c, WaterState(alk = 10.5), 0.6, 300.0, 300.0, 1.0, 3600.0, ArrayList())
            return c.stress
        }
        assertTrue(swing("euphyllia_glabrescens") > swing("sinularia_sp"))
    }

    /** New stony colonies from Add life sit in clear spots on the rock, drawn as their studio-built model moved there. */
    @Test
    fun addedColoniesAreCopiesOfTheStudioModel() {
        val sim = TestTank.sim(emptyList())
        val n = Stocking.addMany(sim, "fimbriaphyllia_ancora", 3)
        assertEquals(3, n)
        val added = sim.corals.filter { it.species.id == "fimbriaphyllia_ancora" }
        assertEquals(3, added.size)
        for (c in added) {
            val pose = c.toPose()
            assertTrue("drawn as a copy", pose.isCopy)
            assertEquals(StonyCorals.STUDIO.x.toFloat(), pose.homeX, 1e-3f)
            assertEquals(StonyCorals.STUDIO.y.toFloat(), pose.homeY, 1e-3f)
        }
        // one added on its own (Command.AddOrganism) also gets a free spot, never the bare studio point
        sim.addFromSeed(LivestockSeed("duncanopsammia_axifuga", "Duncan", 0.0))
        val d = sim.corals.last()
        assertTrue(d.toPose().isCopy)
        assertTrue(added.all { it.pos.distanceTo(d.pos) > 3.0 })
    }
}
