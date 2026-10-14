package com.reeflive.core.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The supply shelf's props each do their own thing in the sim (docs/research/shelf_audit.md): foods carry their own
 * waste chemistry and eaters, the two water changes use different salts, the doses are checked for swings and
 * precipitation, a new sock or fresh carbon fits one when none is running, the trim exports nutrients only when there is
 * algae to cut, and the test kit reads like a hobby kit.
 */
class ShelfEffectsTest {
    private fun f(v: Double) = "%.3f".format(v)

    // ------------------------------------------------------------------ foods
    @Test
    fun everyFoodHasItsOwnPropertiesLine() {
        val lines = FoodKind.entries.map { it.properties }
        lines.forEach { assertTrue(it, it.isNotBlank() && it.contains(" · for ")) }
        assertEquals("all distinct", lines.size, lines.toSet().size)
        assertTrue(FoodKind.FLOATING_PELLET.properties, FoodKind.FLOATING_PELLET.properties.startsWith("Floats 5 min"))
        assertTrue(FoodKind.FLAKE.properties, FoodKind.FLAKE.properties.startsWith("Floats 45 s"))
        assertTrue(FoodKind.PRAWN.properties, FoodKind.PRAWN.properties.startsWith("Sinks fast (4 cm/s)"))
        assertTrue(FoodKind.CORAL_FOOD.properties, FoodKind.CORAL_FOOD.properties.startsWith("Drifts"))
        assertTrue(FoodKind.NORI.properties, FoodKind.NORI.properties.contains("low phosphate"))
        assertTrue(FoodKind.KRILL.properties, FoodKind.KRILL.properties.contains("phosphate-rich"))
    }

    @Test
    fun referenceFoodsKeepTheTunedChemistry() {
        // the autofeed foods (flake, mysis, pods) and pellets are the reference the feed constants were tuned on
        for (k in listOf(FoodKind.FLAKE, FoodKind.MYSIS, FoodKind.PODS, FoodKind.PELLET, FoodKind.FLOATING_PELLET)) {
            assertEquals(k.name, 1.0, k.po4Factor, 0.0); assertEquals(k.name, 1.0, k.docFactor, 0.0)
        }
        assertTrue(FoodKind.KRILL.po4Factor > 1.0 && FoodKind.PRAWN.po4Factor > 1.0 && FoodKind.CORAL_FOOD.po4Factor > 1.0)
        assertTrue(FoodKind.NORI.po4Factor < 1.0 && FoodKind.ALGAE_WAFER.po4Factor < 1.0)
    }

    @Test
    fun meatyFoodLeavesMorePhosphateThanSeaweed() {
        fun po4After(k: FoodKind): Double {
            val w = WaterSim(TankConfig(), WaterState(po4 = 0.02)).also { it.ato = true }
            // the same dry-equivalent load of each, so only the food's own P:N differs
            w.addFeed(2.0, k.po4Factor, k.docFactor)
            repeat(6 * 60) { w.step(60.0) }
            return w.s.po4
        }
        val krill = po4After(FoodKind.KRILL)
        val flake = po4After(FoodKind.FLAKE)
        val nori = po4After(FoodKind.NORI)
        println("shelf: PO4 6 h after 2 g dry-equivalent: krill ${f(krill)} flake ${f(flake)} nori ${f(nori)}")
        assertTrue("krill $krill > flake $flake", krill > flake)
        assertTrue("flake $flake > nori $nori", flake > nori)
    }

    @Test
    fun prawnReachesTheSandScavengers() {
        val s = TestTank.sim(DefaultStock.all, seed = 11L)
        fun sp(k: BrainKind) = s.organisms.first { it.brainKind == k }.species
        assertTrue(sp(BrainKind.NASSARIUS).dietMatch(FoodKind.PRAWN) >= 1.0)
        assertEquals(0.8, sp(BrainKind.HERMIT).dietMatch(FoodKind.PRAWN), 1e-9)
        assertEquals(0.8, sp(BrainKind.BRITTLE_STAR).dietMatch(FoodKind.PRAWN), 1e-9)
        assertEquals(0.0, sp(BrainKind.CLOWN).dietMatch(FoodKind.PRAWN), 0.0)
        // the snails still come first: preference ranks them over the hermits
        assertTrue(sp(BrainKind.NASSARIUS).foodPreference(FoodKind.PRAWN) > sp(BrainKind.HERMIT).foodPreference(FoodKind.PRAWN))
    }

    // ------------------------------------------------------------------ water changes
    @Test
    fun theTwoWaterChangesUseDifferentSalts() {
        val start = WaterState(no3 = 20.0, po4 = 0.1, alk = 8.5, ca = 420.0, mg = 1300.0)
        val big = WaterSim(TankConfig(), start.copy()).also { it.waterChange(0.25, WaterState.CORAL_PRO_MIX) }
        val small = WaterSim(TankConfig(), start.copy()).also { it.waterChange(0.10, WaterState.INSTANT_OCEAN_MIX) }
        println("shelf: big CoralPro NO3 ${f(big.s.no3)} alk ${f(big.s.alk)}; small IO NO3 ${f(small.s.no3)} alk ${f(small.s.alk)}")
        assertEquals(15.0, big.s.no3, 1e-6)
        assertEquals(18.0, small.s.no3, 1e-6)
        // Coral Pro mixes rich: about +1 dKH from a quarter change; Instant Ocean barely moves it
        assertEquals(8.5 + 0.25 * (12.0 - 8.5), big.s.alk, 1e-6)
        assertTrue(big.s.alk - 8.5 > 0.8)
        assertTrue(small.s.alk - 8.5 < 0.25)
        // the preview the shelf shows matches what the sim does
        val preview = SupplyPreview.afterWaterChange(start, 0.25, WaterState.CORAL_PRO_MIX)
        assertEquals(big.s.alk, preview.alk, 1e-9); assertEquals(big.s.no3, preview.no3, 1e-9); assertEquals(big.s.tempC, preview.tempC, 1e-9)
    }

    @Test
    fun theBigRichChangeShocksCoralsMoreThanTheSmallOne() {
        // the alk jump of a quarter Coral Pro change is a "noticeable" step for the corals' shock rule; a tenth of
        // Instant Ocean is not
        val w = WaterState()
        val bigStep = (SupplyPreview.afterWaterChange(w, 0.25, WaterState.CORAL_PRO_MIX).alk - w.alk) / CoralSim.ALK_STEP
        val smallStep = (SupplyPreview.afterWaterChange(w, 0.10, WaterState.INSTANT_OCEAN_MIX).alk - w.alk) / CoralSim.ALK_STEP
        val noticeable = HealthModel.SHOCK_RANGE.optHi!!
        assertTrue("big step $bigStep", bigStep > noticeable)
        assertTrue("small step $smallStep", smallStep < noticeable)
        fun meanStress(fraction: Double, mix: WaterState): Double {
            val s = TestTank.sim(DefaultStock.all, seed = 3L)
            s.advanceAbstract(3600.0)
            s.enqueue(Command.WaterChange(fraction, mix))
            s.advanceAbstract(2 * 3600.0)
            val c = s.corals.filter { it.kind != CoralKind.AIPTASIA }
            return c.sumOf { it.stress } / c.size
        }
        val big = meanStress(0.25, WaterState.CORAL_PRO_MIX)
        val small = meanStress(0.10, WaterState.INSTANT_OCEAN_MIX)
        println("shelf: mean coral stress 2 h after: big ${"%.6f".format(big)} small ${"%.6f".format(small)}")
        assertTrue("big $big vs small $small", big > small)
    }

    // ------------------------------------------------------------------ doses
    @Test
    fun dosesAreCheckedForSwingsLevelsAndPrecipitation() {
        val w = WaterState()
        assertEquals(DoseCheck.Verdict.OK, DoseCheck.check("alk", 1.0, w))
        assertEquals(DoseCheck.Verdict.OK, DoseCheck.check("ca", 20.0, w))
        assertEquals(DoseCheck.Verdict.OK, DoseCheck.check("mg", 50.0, w))
        // a second dKH the same day is too fast a swing
        assertEquals(DoseCheck.Verdict.FAST_SWING, DoseCheck.check("alk", 1.0, w, dosedLast24h = 1.0))
        assertEquals(DoseCheck.Verdict.FAST_SWING, DoseCheck.check("ca", 20.0, w, dosedLast24h = 20.0))
        // calcium already high (alk low enough not to precipitate)
        assertEquals(DoseCheck.Verdict.TOO_HIGH, DoseCheck.check("ca", 20.0, w.copy(ca = 460.0, alk = 7.0)))
        // high Ca × alk falls out as crust; low magnesium lets ordinary water do it; magnesium never makes it worse
        assertEquals(DoseCheck.Verdict.PRECIPITATION, DoseCheck.check("alk", 1.0, w.copy(alk = 10.5, ca = 450.0)))
        assertEquals(DoseCheck.Verdict.PRECIPITATION, DoseCheck.check("alk", 1.0, w.copy(mg = 1100.0)))
        assertEquals(DoseCheck.Verdict.OK, DoseCheck.check("mg", 50.0, w.copy(mg = 1100.0)))
        assertEquals(DoseCheck.Verdict.OK, DoseCheck.check("no3", 5.0, w))
    }

    @Test
    fun theDoseLogForgetsAfterADay() {
        val log = DoseCheck.Log()
        log.add(1000.0, "alk", 1.0)
        log.add(2000.0, "ALK", 1.0)
        log.add(2000.0, "ca", 20.0)
        assertEquals(2.0, log.lastDay("alk", 3000.0), 1e-9)
        assertEquals(20.0, log.lastDay("ca", 3000.0), 1e-9)
        assertEquals(0.0, log.lastDay("alk", 2000.0 + 86400.0 + 1), 1e-9)
        // a sandbox reset jumps back in time: the old doses are forgotten
        log.add(10.0, "alk", 1.0)
        assertEquals(1.0, log.lastDay("alk", 10.0), 1e-9)
    }

    @Test
    fun aFastAlkSwingStressesCoralsMoreThanOneDose() {
        fun meanStress(dkh: Double): Double {
            val s = TestTank.sim(DefaultStock.all, seed = 5L)
            s.advanceAbstract(3600.0)
            s.enqueue(Command.Dose("alk", dkh))
            s.advanceAbstract(2 * 3600.0)
            val c = s.corals.filter { it.kind != CoralKind.AIPTASIA }
            return c.sumOf { it.stress } / c.size
        }
        val one = meanStress(1.0)
        val three = meanStress(3.0)
        println("shelf: mean coral stress 2 h after alk +1 ${f(one)} vs +3 ${f(three)}")
        assertTrue("three $three > one $one", three > one)
    }

    // ------------------------------------------------------------------ sock, carbon, trim
    @Test
    fun aNewSockFitsOneAndCatchesDetritus() {
        fun run(swap: Boolean): WaterSim {
            val w = WaterSim(TankConfig(), WaterState(detritus = 1.0)).also { it.ato = true }
            assertFalse(w.filterSock)
            if (swap) w.maintain(MaintenanceTask.CHANGE_SOCK)
            repeat(12 * 60) { w.step(60.0) }
            return w
        }
        val none = run(false)
        val sock = run(true)
        println("shelf: detritus 12 h: no sock ${f(none.s.detritus)} new sock ${f(sock.s.detritus)} (load ${f(sock.sockLoad)})")
        assertTrue(sock.filterSock)
        assertTrue("the sock caught some: ${sock.s.detritus} vs ${none.s.detritus}", sock.s.detritus < none.s.detritus - 0.05)
        assertTrue(sock.sockLoad > 0)
    }

    @Test
    fun freshCarbonClearsYellowWaterAndStripsSomeIodine() {
        // yellow water clears within a day
        val day = WaterSim(TankConfig(), WaterState(doc = 6.0)).also { it.ato = true; it.skimmer = false }
        val yellow = day.clarity
        day.maintain(MaintenanceTask.REPLACE_CARBON)
        repeat(1440) { day.step(60.0) }
        assertTrue("clearer: ${day.clarity} vs $yellow", day.clarity > yellow + 0.05)
        fun run(fresh: Boolean): WaterSim {
            val w = WaterSim(TankConfig(), WaterState(doc = 6.0, i = 0.06)).also { it.ato = true; it.skimmer = false }
            if (fresh) w.maintain(MaintenanceTask.REPLACE_CARBON)
            repeat(5 * 1440) { w.step(60.0) }
            return w
        }
        val none = run(false)
        val carbon = run(true)
        println("shelf: 5 d DOC none ${f(none.s.doc)} carbon ${f(carbon.s.doc)}; iodine none ${f(none.s.i)} carbon ${f(carbon.s.i)}")
        assertTrue(carbon.carbon)
        assertTrue(carbon.s.doc < none.s.doc * 0.5)
        assertTrue("iodine stripped: ${carbon.s.i}", carbon.s.i < none.s.i * 0.8)
        assertTrue("but not all at once: ${carbon.s.i}", carbon.s.i > 0.02)
    }

    @Test
    fun trimmingNeedsAlgaeAndExportsWhatItCuts() {
        assertFalse(SupplyPreview.canTrim(WaterState(macro = 0.0, chaeto = 0.0)))
        assertTrue(SupplyPreview.canTrim(WaterState(macro = 0.3)))
        assertEquals(0.0, SupplyPreview.trimExportNo3(WaterState(macro = 0.0, chaeto = 0.0)), 0.0)
        val w = WaterSim(TankConfig(), WaterState(macro = 0.4))
        val export = SupplyPreview.trimExportNo3(w.s)
        w.maintain(MaintenanceTask.HARVEST_MACRO)
        assertEquals(0.4 * 0.67, w.s.macro, 1e-9)
        assertTrue("a third of 0.4 holds ~1.8 ppm NO3: $export", export in 1.5..2.2)
    }

    // ------------------------------------------------------------------ test kit
    @Test
    fun theTestKitReadsLikeAHobbyKit() {
        val w = WaterState(alk = 8.4, ca = 420.0, no3 = 12.0, po4 = 0.04)
        val reads = (0 until 400).map { TestKit.read(w, Random(it)) }
        val byId = { id: String -> reads.map { r -> r.first { it.id == id }.value } }
        // colour cards only show their swatches; low phosphate mostly reads zero
        assertTrue(byId("no3").all { it in TestKit.NO3_CARD.toList() })
        assertTrue(byId("no3").all { it == 10.0 || it == 20.0 })
        val po4 = byId("po4")
        assertTrue(po4.all { it == 0.0 || it == 0.25 })
        val share = po4.count { it > 0 }.toDouble() / po4.size
        assertTrue("PO4 0.04 reads 0.25 on ${share * 100} % of tests", share < 0.3)
        // calcium in whole drops of 20 ppm, within one drop of the truth; KH in 0.2 steps within ±0.3
        assertTrue(byId("ca").all { it % 20.0 == 0.0 && kotlin.math.abs(it - 420.0) <= 20.0 })
        assertTrue(byId("alk").all { kotlin.math.abs(it - 8.4) <= 0.31 })
        assertTrue("reads vary like real tests", byId("ca").toSet().size > 1)
        // the same test (seed) gives the same answer
        assertEquals(TestKit.read(w, Random(7)).map { it.text }, TestKit.read(w, Random(7)).map { it.text })
        assertEquals("8.4", kitFmt(8.4, 1)); assertEquals("0.25", kitFmt(0.25, 2)); assertEquals("420", kitFmt(420.0, 0)); assertEquals("0.05", kitFmt(0.05, 2))
    }
}
