package com.reeflive.app.ui.live

import com.reeflive.app.render.BurstKind
import com.reeflive.core.sim.DoseCheck
import com.reeflive.core.sim.FoodKind
import com.reeflive.core.sim.MaintenanceTask
import com.reeflive.core.sim.WaterState
import com.reeflive.core.status.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Every prop on the supply shelf has its own effect, picture, Hermie line and properties line
 * (docs/research/shelf_audit.md): nothing shares a line, nothing claims an effect it did not have.
 */
class SupplyEffectsTest {
    @Test fun everyPropHasItsOwnPropertiesLine() {
        val lines = SupplyItem.entries.map { it.properties }
        SupplyItem.entries.forEach { assertTrue("${it.id} has properties", it.properties.isNotBlank()) }
        assertEquals("no two props share a properties line", lines.size, lines.toSet().size)
        // foods read theirs off the sim's food data
        assertEquals(FoodKind.KRILL.properties, SupplyItem.KRILL.properties)
    }

    @Test fun theTwoWaterChangesUseTheirOwnSalt() {
        assertEquals(WaterState.CORAL_PRO_MIX, SupplyItem.WATER_CHANGE.saltMix)
        assertEquals(WaterState.INSTANT_OCEAN_MIX, SupplyItem.WATER_SMALL.saltMix)
        assertEquals(0.25, SupplyItem.WATER_CHANGE.changeFraction, 0.0)
        assertEquals(0.10, SupplyItem.WATER_SMALL.changeFraction, 0.0)
        var got: Triple<Double, WaterState, String>? = null
        val actions = SandboxActions(onSaltChange = { f, mix, salt -> got = Triple(f, mix, salt) })
        val tools = LiveTools(
            sandbox = true, soundOn = false, onSound = {}, onMicroscope = {}, onHide = {}, onHelp = {},
            cleaning = false, onCleanGlass = {}, hermieOn = true, onHermie = {}, meterOn = true, onMeter = {},
            onTapGlass = {}, onFeed = {}, readouts = emptyList(),
        )
        SupplyItem.WATER_CHANGE.use(actions, tools)
        assertEquals(Triple(0.25, WaterState.CORAL_PRO_MIX, "Coral Pro"), got)
        val w = WaterState(no3 = 12.0)
        val big = LiveReactions.waterChange(0.25, "Coral Pro", WaterState.CORAL_PRO_MIX, w)
        val small = LiveReactions.waterChange(0.10, "Instant Ocean", WaterState.INSTANT_OCEAN_MIX, w)
        assertNotEquals(big.say, small.say)
        assertTrue(big.feedback.effect, big.feedback.effect.contains("Nitrate 12.0 → 9.0"))
        assertTrue(big.feedback.effect, big.feedback.effect.contains("alk +0.9 dKH"))
        // the bucket is mixed at 25 °C, half a degree under the display: a cool shimmer at the return
        assertTrue(big.feedback.bursts.any { it.kind == BurstKind.COOL })
    }

    @Test fun everyFoodHasItsOwnLineAndSplash() {
        val lines = FoodKind.entries.map { LiveReactions.feedLine(it) }
        assertEquals(lines.size, lines.toSet().size)
        // frozen cubes shed a cloud of thaw juice in their own colour; the wafer plops; phyto drifts as a haze
        for (k in listOf(FoodKind.MYSIS, FoodKind.BRINE, FoodKind.KRILL, FoodKind.CYCLOPS, FoodKind.PRAWN)) {
            val b = Feedback.feeding(k).bursts
            assertTrue(k.name, b.any { it.kind == BurstKind.PLOP } && b.any { it.kind == BurstKind.HAZE })
        }
        assertTrue(Feedback.feeding(FoodKind.ALGAE_WAFER).bursts.any { it.kind == BurstKind.PLOP })
        assertTrue(Feedback.feeding(FoodKind.PLANKTON).bursts.any { it.kind == BurstKind.HAZE })
        assertTrue(Feedback.feeding(FoodKind.PODS).bursts.any { it.kind == BurstKind.PODS })
        val krill = Feedback.feeding(FoodKind.KRILL).bursts.first { it.kind == BurstKind.HAZE }
        val brine = Feedback.feeding(FoodKind.BRINE).bursts.first { it.kind == BurstKind.HAZE }
        assertTrue("thaw clouds differ in colour", krill.r != brine.r || krill.g != brine.g || krill.b != brine.b)
    }

    @Test fun dosesWarnAboutSwingsAndSnow() {
        val ok = LiveReactions.dose("alk", "Alkalinity", DoseCheck.Verdict.OK)
        val swing = LiveReactions.dose("alk", "Alkalinity", DoseCheck.Verdict.FAST_SWING)
        val snow = LiveReactions.dose("ca", "Calcium", DoseCheck.Verdict.PRECIPITATION)
        assertEquals(HermieMood.HAPPY, ok.mood)
        assertEquals(HermieMood.WORRIED, swing.mood)
        assertTrue(swing.feedback.effect, swing.feedback.effect.contains("1 dKH"))
        assertTrue(snow.feedback.bursts.any { it.kind == BurstKind.SALT })
        // each dose has its own dye colour
        val tints = listOf("alk", "ca", "mg").map { LiveReactions.dose(it, it).fx.tint }
        assertEquals(3, tints.toSet().size)
    }

    @Test fun careWithNothingToDoSaysSo() {
        val bare = WaterState(macro = 0.0, chaeto = 0.0)
        val nothing = LiveReactions.maintain(MaintenanceTask.HARVEST_MACRO, bare)
        assertEquals(FxKind.SPARKLE, nothing.fx.kind)
        val trim = LiveReactions.maintain(MaintenanceTask.HARVEST_MACRO, WaterState(macro = 0.4))
        assertEquals(FxKind.SNIP, trim.fx.kind)
        assertTrue(trim.feedback.effect, trim.feedback.effect.contains("ppm of nitrate"))
        assertEquals(FxKind.SPARKLE, LiveReactions.removeAiptasia(0).fx.kind)
        assertEquals(FxKind.PEST_REMOVE, LiveReactions.removeAiptasia(2).fx.kind)
        // the sock lifts a brown puff out, the carbon a black one: their pictures differ
        val sock = LiveReactions.maintain(MaintenanceTask.CHANGE_SOCK, null).feedback.bursts
        val carbon = LiveReactions.maintain(MaintenanceTask.REPLACE_CARBON, null).feedback.bursts
        assertTrue(sock.any { it.kind == BurstKind.SUCTION } && carbon.any { it.kind == BurstKind.CLARITY })
        val sockPuff = sock.first { it.kind == BurstKind.HAZE }
        val dust = carbon.first { it.kind == BurstKind.HAZE }
        assertTrue(dust.r < sockPuff.r)
    }

    @Test fun theTestKitShowsKitReadings() {
        val w = WaterState(alk = 8.4, ca = 420.0, no3 = 12.0, po4 = 0.04)
        val cells = testKitReadouts(w, Random(3))
        assertEquals(listOf("KH", "Ca", "NO3", "PO4"), cells.map { it.label })
        val no3 = cells.first { it.id == "no3" }.valueText
        assertTrue(no3, no3 == "10" || no3 == "20")
        assertNotNull(LiveReactions.testKit(cells))
        val bad = LiveReactions.testKit(listOf(WaterReadout("no3", "NO3", "80", Status.DANGER)))
        assertTrue(bad, bad.contains("NO3 reads 80"))
    }
}
