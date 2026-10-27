package com.reeflive.core.tracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AcclimationPlanTest {
    @Test
    fun dripFitsResearchWindows() {
        val normal = AcclimationPlan.steps(AcclimationMethod.DRIP, sensitive = false)
        val slow = AcclimationPlan.steps(AcclimationMethod.DRIP, sensitive = true)
        // float 15 to 20 min, drip 60 to 90 min (research 01 §4.2)
        assertEquals(15 * 60, normal.first { it.title == "Float the bag" }.seconds)
        assertEquals(20 * 60, slow.first { it.title == "Float the bag" }.seconds)
        assertEquals(75, AcclimationPlan.timedMinutes(normal))
        assertEquals(110, AcclimationPlan.timedMinutes(slow))
    }

    @Test
    fun everyPlanEndsWithTheMoveAndHasTimers() {
        for (m in AcclimationMethod.entries) for (s in listOf(false, true)) {
            val steps = AcclimationPlan.steps(m, s)
            assertEquals("Into its new home", steps.last().title)
            assertTrue(steps.count { it.seconds > 0 } >= 3)
            assertTrue(steps.all { it.seconds >= 0 && it.title.isNotBlank() && it.detail.isNotBlank() })
        }
    }

    @Test
    fun floatAddsWaterFourTimes() {
        val steps = AcclimationPlan.steps(AcclimationMethod.FLOAT, sensitive = false)
        assertEquals(4, steps.count { it.title.startsWith("Add tank water") })
        assertEquals(35, AcclimationPlan.timedMinutes(steps))
    }
}
