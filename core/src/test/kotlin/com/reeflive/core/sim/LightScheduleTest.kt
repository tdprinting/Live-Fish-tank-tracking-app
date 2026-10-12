package com.reeflive.core.sim

import org.junit.Assert.assertEquals
import org.junit.Test

class LightScheduleTest {
    private fun at(h: Int, m: Int = 0) = h * 3600.0 + m * 60.0

    @Test
    fun phaseLabelFollowsTheDefaultRamps() {
        val s = LightSchedule()
        assertEquals("Night", s.phaseLabel(at(3)))
        assertEquals("Dawn", s.phaseLabel(at(9, 30)))
        assertEquals("Day", s.phaseLabel(at(14)))
        assertEquals("Dusk", s.phaseLabel(at(18)))
        assertEquals("Dusk", s.phaseLabel(at(18, 30)))
        assertEquals("Night", s.phaseLabel(at(22)))
    }
}
