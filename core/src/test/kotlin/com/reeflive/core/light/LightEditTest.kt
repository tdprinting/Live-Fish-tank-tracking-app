package com.reeflive.core.light

import com.reeflive.core.model.LightCurve
import com.reeflive.core.model.LightEdit
import com.reeflive.core.model.LightPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LightEditTest {
    private val base = LightCurve.AI_SOFT_CORAL

    @Test
    fun setLevelClampsAndKeepsOthers() {
        val out = LightEdit.setLevel(base, 4, 0, 1.4)
        assertEquals(1.0, out[4].channels[0], 1e-12)
        assertEquals(base[4].channels[1], out[4].channels[1], 1e-12)
        assertEquals(base.size, out.size)
    }

    @Test
    fun moveStaysBetweenNeighbours() {
        // point 3 is 09:30, between 08:45 and 10:30
        assertEquals(8 * 60 + 45 + LightEdit.MIN_GAP, LightEdit.move(base, 3, 0)[3].minuteOfDay)
        assertEquals(10 * 60 + 30 - LightEdit.MIN_GAP, LightEdit.move(base, 3, 1439)[3].minuteOfDay)
        assertEquals(600, LightEdit.move(base, 3, 600)[3].minuteOfDay)
    }

    @Test
    fun insertKeepsCurveAndRejectsNeighbours() {
        val (out, i) = LightEdit.insert(base, 14 * 60)!!
        assertEquals(base.size + 1, out.size)
        assertEquals(14 * 60, out[i].minuteOfDay)
        for (m in listOf(13 * 60.0, 14 * 60.0, 15 * 60.0)) {
            assertEquals(LightCurve.channelsAt(base, m)[2], LightCurve.channelsAt(out, m)[2], 1e-3)
        }
        assertNull(LightEdit.insert(base, 18 * 60 + 2))
    }

    @Test
    fun removeKeepsMinimum() {
        val two = listOf(LightPoint(0, List(8) { 0.0 }), LightPoint(600, List(8) { 1.0 }))
        assertEquals(2, LightEdit.remove(two, 0).size)
        assertEquals(base.size - 1, LightEdit.remove(base, 2).size)
    }

    @Test
    fun softCoralPhotoperiodIsAboutTenHours() {
        val h = LightEdit.photoperiodMinutes(base) / 60.0
        assertTrue("photoperiod $h h", h in 9.0..11.5)
    }
}
