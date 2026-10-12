package com.reeflive.core.live

import com.reeflive.core.model.Livestock
import com.reeflive.core.sim.Command
import com.reeflive.core.sim.SimEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveGlueTest {
    @Test fun everyScenarioHasSteps() {
        Scenario.values().forEach { assertTrue(it.name, it.steps().isNotEmpty()) }
        assertEquals(6, Scenario.values().size)
    }

    @Test fun heatWaveMapsToHeaterAndRoomTemp() {
        val cmds = Scenario.HEAT_WAVE.steps().map { it.command }
        assertTrue(cmds.any { it is Command.Heater && it.stuckOn && it.setC == 31.0 })
        assertTrue(cmds.any { it is Command.SetParam && it.paramId == "roomtemp" })
    }

    @Test fun nitrateSpikeDoses() {
        val d = Scenario.NITRATE_SPIKE.steps().map { it.command }.filterIsInstance<Command.Dose>()
        assertEquals(60.0, d.first { it.paramId == "no3" }.delta, 0.0)
    }

    @Test fun powerOutageCutsThenRestores() {
        val steps = Scenario.POWER_OUTAGE.steps()
        val first = steps.filter { it.atSimSec == 0.0 }.map { it.command }
        assertTrue(Command.Powerheads(false) in first && Command.Lights(on = false) in first)
        val later = steps.filter { it.atSimSec > 0 }.map { it.command }
        assertTrue(Command.Powerheads(true) in later && Command.Lights(on = null) in later)
    }

    @Test fun salinitySwingGoesHighThenLow() {
        val sg = Scenario.SALINITY_SWING.steps().map { it.command as Command.SetParam }
        assertTrue(sg[0].value > 1.030 && sg[1].value < 1.020)
    }

    @Test fun overfeedingFeedsSixTimes() {
        assertEquals(6, Scenario.OVERFEEDING.steps().count { it.command is Command.Feed })
    }

    @Test fun queueReleasesDelayedStepsOnce() {
        val q = ScenarioQueue()
        q.schedule(1000.0, Scenario.POWER_OUTAGE.steps())
        assertEquals(3, q.due(1000.0).size)
        assertTrue(q.due(1000.0 + 3600).isEmpty())
        assertEquals(3, q.due(1000.0 + 4 * 3600).size)
        assertTrue(q.isEmpty)
    }

    @Test fun fastForwardIsCappedAt24Hours() {
        assertEquals(60.0, FastForward.simSeconds(60_000), 0.0)
        assertEquals(86_400.0, FastForward.simSeconds(3L * 24 * 3600 * 1000), 0.0)
        assertEquals(86_400.0, FastForward.simSeconds(24L * 3600 * 1000), 0.0)
        assertEquals(0.0, FastForward.simSeconds(-5), 0.0)
        assertEquals(86_400.0, FastForward.simSeconds(600_000, speed = 1000.0), 0.0)
    }

    @Test fun loggedTemperatureConvertsFahrenheitToCelsius() {
        val w = RealityInit.waterFrom(mapOf("temp" to 78.8, "sal" to 1.026, "no3" to 12.0, "orp" to 350.0))
        assertEquals(26.0, w.tempC, 1e-9)
        assertEquals(1.026, w.sg, 0.0)
        assertEquals(12.0, w.no3, 0.0)
    }

    @Test fun changedCommandsOnlyForNewValues() {
        val cmds = RealityInit.changedCommands(mapOf("temp" to 78.0, "no3" to 5.0), mapOf("temp" to 86.0, "no3" to 5.0, "orp" to 1.0))
        assertEquals(1, cmds.size)
        val c = cmds[0] as Command.SetParam
        assertEquals("temp", c.paramId)
        assertEquals(30.0, c.value, 1e-9)
    }

    @Test fun seedsExpandGroupsAndKeepIds() {
        val l = Livestock("a", "t", "amphiprion_ocellaris", "Nemo", quantity = 2)
        val seeds = RealityInit.seedsFrom(listOf(l, l.copy(id = "b", speciesId = "x", nickname = "Solo", quantity = 1)))
        assertEquals(listOf("a", "a#2", "b"), seeds.map { it.id })
        assertEquals("Solo", seeds[2].nickname)
    }

    @Test fun peekDescribesEvents() {
        assertNull(LivePeek.lastDescribed(emptyList()))
        assertEquals("f1 died (heat)", LivePeek.lastDescribed(listOf(SimEvent.Died("f1", "heat", 1.0))))
    }
}
