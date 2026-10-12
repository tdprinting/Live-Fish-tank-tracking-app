package com.reeflive.core.light

import com.reeflive.core.model.LightCurve
import com.reeflive.core.model.LightPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LightCurveTest {
    @Test
    fun interpolatesAndWrapsMidnight() {
        val pts = listOf(LightPoint(600, List(8) { if (it == 0) 1.0 else 0.0 }), LightPoint(1200, List(8) { 0.0 }))
        assertEquals(0.5, LightCurve.channelsAt(pts, 900.0)[0], 1e-12)
        // 20:00 → 10:00 next day wraps: at 03:00 we are 7/14 of the way
        assertEquals(0.5, LightCurve.channelsAt(pts, 180.0)[0], 1e-12)
        assertEquals(1.0, LightCurve.channelsAt(pts, 600.0)[0], 1e-12)
        assertEquals(0.5, LightCurve.channelsAt(pts, 900.0 + 1440.0)[0], 1e-12)
    }

    @Test
    fun defaultScheduleShape() {
        val p = LightCurve.AI_SOFT_CORAL
        val noon = LightCurve.intensity(LightCurve.channelsAt(p, 12 * 60.0))
        val night = LightCurve.intensity(LightCurve.channelsAt(p, 23 * 60.0))
        assertTrue(noon in 0.4..0.7)
        assertTrue(night < 0.01)
        val swNoon = LightCurve.swatchArgb(LightCurve.channelsAt(p, 12 * 60.0))
        val swNight = LightCurve.swatchArgb(LightCurve.channelsAt(p, 23 * 60.0))
        fun lum(c: Long) = ((c shr 16) and 0xFF) + ((c shr 8) and 0xFF) + (c and 0xFF)
        assertTrue(lum(swNoon) > lum(swNight) * 3)
        assertEquals(3, LightCurve.PRESETS.size)
    }
}
