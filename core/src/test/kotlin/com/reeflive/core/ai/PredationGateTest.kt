package com.reeflive.core.ai

import com.reeflive.core.geom.Vec3
import com.reeflive.core.sim.BrainKind
import com.reeflive.core.sim.Command
import com.reeflive.core.sim.FoodKind
import com.reeflive.core.sim.FoodParticle
import com.reeflive.core.sim.LivestockSeed
import com.reeflive.core.sim.SimEvent
import com.reeflive.core.sim.Simulation
import com.reeflive.core.sim.TankConfig
import com.reeflive.core.sim.TestTank
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.asin

/** Evidence gate for unsourced predation rolls (SIMULATION §21.6). */
class PredationGateTest {
    private fun Simulation.org(kind: BrainKind) = organisms.first { it.brainKind == kind }
    private fun Simulation.agent(kind: BrainKind) = agentOf(org(kind))!!
    private fun stock(vararg ids: String) = ids.map { TestTank.seed(it) }

    // ------------------------------------------------------------------ X7 predation gate
    @Test
    fun unsourcedPredationIsOffEvenInTheSandbox() {
        val s = TestTank.sim(listOf(LivestockSeed("echidna_nebulosa", "Snowflake", 45.0, id = "eel"), TestTank.seed("banded")), seed = 1L, startHour = 23.0)
        s.predationEnabled = true
        assertFalse(s.unsourcedPredation)
        assertEquals(0.0, s.paramValue("unsourced_predation"))
        val eel = s.org(BrainKind.EEL)
        val shrimp = s.org(BrainKind.BANDED_SHRIMP)
        eel.hunger = 0.9
        val mouth = s.rock.anchors.pos("CAVE_EEL", Vec3(69.0, 5.0, 23.0))
        shrimp.placeAt(Vec3(mouth.x - 4.0, s.rock.sandTopCm + 0.8, mouth.z + 3.0), Vec3.X)
        s.advance(1800.0, behaviorDt = 0.1)
        assertTrue(shrimp.alive)
        assertTrue(s.eventLog.none { it is SimEvent.Eaten })
        s.enqueue(Command.SetParam("unsourced_predation", 1.0))
        s.advance(1.0)
        assertTrue(s.unsourcedPredation)
    }
}
