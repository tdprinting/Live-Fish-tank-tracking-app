package com.reeflive.app.ui.live

import com.reeflive.app.render.Camera
import com.reeflive.app.ui.components.ReefSliderSpec
import com.reeflive.app.ui.components.SliderZones
import com.reeflive.core.sim.WaterState
import com.reeflive.core.status.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SandboxParamsTest {
    @Test fun idsAreUniqueAndZonesSitInsideTheRange() {
        val ids = SandboxParams.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        SandboxParams.all.forEach { d ->
            assertTrue(d.id, d.min < d.max && d.step > 0)
            d.zones?.let { z ->
                assertTrue(d.id, z.safeFrom <= z.safeTo)
                assertTrue(d.id, z.safeFrom >= d.min && z.safeTo <= d.max)
                z.dangerBelow?.let { assertTrue(d.id, it in d.min..z.safeFrom) }
                z.dangerAbove?.let { assertTrue(d.id, it in z.safeTo..d.max) }
            }
        }
    }

    @Test fun rangesAllowCatastrophicValues() {
        fun range(id: String) = SandboxParams.byId(id)!!.let { it.min..it.max }
        assertTrue(15.0 in range("temp") && 40.0 in range("temp"))
        assertTrue(1.000 in range("sal") && 1.040 in range("sal"))
        assertTrue(6.5 in range("ph") && 9.5 in range("ph"))
        assertTrue(5.0 in range("nh3"))
        assertTrue(200.0 in range("no3"))
        assertTrue(3.0 in range("po4"))
        assertTrue(1.0 in range("copper"))
        assertTrue(0.0 in range("do") && 10.0 in range("do"))
    }

    @Test fun temperatureZonesFollowTheHeatResearch() {
        val z = SandboxParams.byId("temp")!!.zones!!
        assertEquals(Status.GOOD, z.status(26.5))
        assertEquals(Status.CAUTION, z.status(30.5))
        assertEquals(Status.DANGER, z.status(33.5))
    }

    @Test fun everySliderTabHasRows() {
        SandboxCategory.values().filter { it != SandboxCategory.EVENTS && it != SandboxCategory.STOCK }.forEach {
            assertTrue(it.name, SandboxParams.inCategory(it).any { d -> d.supported })
        }
    }

    @Test fun everyRowHasPlainWordsAndEveryTabIsListed() {
        SandboxParams.all.forEach { d ->
            assertTrue(d.id, SandboxFriendly.has(d.id))
            assertTrue(d.id, SandboxFriendly.of(d).explain.isNotBlank())
        }
        assertEquals(SandboxCategory.values().toSet(), SandboxFriendly.tabs.toSet())
        assertEquals(SandboxFriendly.tabs.size, SandboxFriendly.tabs.toSet().size)
    }

    @Test fun temperatureShowsFahrenheitAsTheExpertLine() {
        val r = TankReading(WaterState(tempC = 25.0), lightPct = 100.0, flowPct = 100.0)
        val line = SandboxFriendly.expert(SandboxParams.byId("temp")!!, r)!!
        assertTrue(line, line.contains("Temperature") && line.contains("77.0 °F"))
    }

    @Test fun readingsUseDisplayUnits() {
        val r = TankReading(WaterState(algaeHair = 0.25), lightPct = 80.0, flowPct = 100.0)
        assertEquals(25.0, SandboxParams.byId("algae")!!.valueIn(r), 1e-9)
        // light is absolute peak PAR: without a sim value it follows the old % of the default schedule's ~300
        assertEquals(0.8 * SandboxParams.DEFAULT_PEAK_PAR, SandboxParams.byId("light")!!.valueIn(r), 1e-9)
        assertEquals(500.0, SandboxParams.byId("light")!!.valueIn(r.copy(peakPar = 500.0)), 1e-9)
        assertEquals(60.0, SandboxParams.byId("flow")!!.valueIn(r.copy(turnover = 60.0)), 1e-9)
        // photoperiod reads the sim's light schedule (default 11 h)
        assertEquals(11.0, SandboxParams.byId("photoperiod")!!.valueIn(r), 1e-9)
    }
}

class ReefSliderSpecTest {
    private val spec = ReefSliderSpec("Nitrate", "ppm", 0.0, 500.0, 0.5, 1, SliderZones(null, 1.0, 20.0, 80.0), curve = 2.0)

    @Test fun snapClampsAndRounds() {
        assertEquals(12.5, spec.snap(12.6), 1e-9)
        assertEquals(0.0, spec.snap(-3.0), 1e-9)
        assertEquals(500.0, spec.snap(900.0), 1e-9)
        val sg = ReefSliderSpec("Salinity", "SG", 1.0, 1.045, 0.001, 3)
        assertEquals(1.025, sg.snap(1.02512), 1e-12)
    }

    @Test fun curvedTrackRoundTrips() {
        for (v in listOf(0.0, 1.0, 10.0, 80.0, 250.0, 500.0)) assertEquals(v, spec.valueAt(spec.fraction(v)), 1e-3)
        // the curve gives the normal band a usable share of the track
        assertTrue(spec.fraction(20.0) > 0.15f)
    }

    @Test fun zones() {
        assertEquals(Status.CAUTION, spec.zones!!.status(0.5))
        assertEquals(Status.GOOD, spec.zones!!.status(10.0))
        assertEquals(Status.CAUTION, spec.zones!!.status(50.0))
        assertEquals(Status.DANGER, spec.zones!!.status(120.0))
    }
}

class CameraFitTest {
    @Test fun wideViewportsMoveCloserTallOnesKeepTheDefault() {
        assertEquals(Camera.DEFAULT_DISTANCE, Camera.fitDistance(1080f / 2200f), 1e-3f)
        assertTrue(Camera.fitDistance(2f) < Camera.DEFAULT_DISTANCE * 0.7f)
        assertTrue(Camera.fitDistance(4f) >= Camera.MIN_FIT_DISTANCE)
    }
}
