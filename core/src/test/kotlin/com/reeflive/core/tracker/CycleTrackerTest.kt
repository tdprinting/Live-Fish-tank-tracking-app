package com.reeflive.core.tracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CycleTrackerTest {
    private val day = CycleTracker.DAY_MS
    private val start = 1_700_000_000_000.0
    private fun at(d: Double) = start + d * day
    private fun r(d: Double, nh3: Double? = null, no2: Double? = null, no3: Double? = null) = CycleReading(at(d), nh3, no2, no3)

    @Test
    fun noReadingsWaits() {
        val s = CycleTracker.assess(emptyList(), start, at(2.0))
        assertEquals(CycleStage.WAITING, s.stage)
        assertEquals(3, s.day)
        assertEquals(28, s.daysLeftLo)
        assertEquals(56, s.daysLeftHi)
    }

    @Test
    fun allZeroWithoutAmmoniaIsNotCycled() {
        val rs = (0..4).map { r(it.toDouble(), 0.0, 0.0, 0.0) }
        assertEquals(CycleStage.WAITING, CycleTracker.assess(rs, start, at(4.0)).stage)
    }

    @Test
    fun ammoniaSpike() {
        val rs = listOf(r(1.0, 2.0, 0.0, 0.0), r(4.0, 2.5, 0.1, 0.0))
        val s = CycleTracker.assess(rs, start, at(4.0))
        assertEquals(CycleStage.AMMONIA, s.stage)
        assertEquals(2.5, s.peakNh3, 1e-9)
        assertTrue(s.daysLeft in s.daysLeftLo..s.daysLeftHi)
        assertTrue(s.daysLeftLo >= 14)
    }

    @Test
    fun nitriteSpikeWhenAmmoniaFalls() {
        val rs = listOf(r(2.0, 2.0, 0.0), r(9.0, 0.8, 1.0), r(14.0, 0.25, 3.0, 5.0))
        val s = CycleTracker.assess(rs, start, at(14.0))
        assertEquals(CycleStage.NITRITE, s.stage)
        assertEquals(3.0, s.peakNo2, 1e-9)
    }

    @Test
    fun fallingNitriteProjectsToZero() {
        // peak 4 ppm on day 20, 2 ppm on day 24: 0.5 ppm a day → 4 more days + the 3-day check = 7
        val rs = listOf(r(5.0, 2.0, 0.0), r(12.0, 0.2, 2.0), r(20.0, 0.0, 4.0, 10.0), r(24.0, 0.0, 2.0, 20.0))
        val s = CycleTracker.assess(rs, start, at(24.0))
        assertEquals(CycleStage.NITRITE, s.stage)
        assertEquals(7, s.daysLeft)
        assertTrue(s.daysLeftLo <= 7)
    }

    @Test
    fun valuesCarryForwardAcrossSeparateEntries() {
        val rs = listOf(r(10.0, nh3 = 0.0), r(10.01, no2 = 2.0))
        assertEquals(CycleStage.NITRITE, CycleTracker.assess(rs, start, at(10.0)).stage)
    }

    @Test
    fun clearingThenCycledAfterThreeDays() {
        val base = listOf(r(3.0, 2.0, 0.0), r(15.0, 0.0, 3.0, 10.0))
        val one = base + r(28.0, 0.0, 0.0, 30.0)
        val s1 = CycleTracker.assess(one, start, at(28.0))
        assertEquals(CycleStage.CLEARING, s1.stage)
        assertEquals(1, s1.zeroDays)
        assertEquals(2, s1.daysLeft)
        // tests taken at different times of day still count as three days in a row
        val three = one + r(29.2, 0.0, 0.0) + r(29.9, 0.03, 0.0, 30.0)
        val s3 = CycleTracker.assess(three, start, at(30.0))
        assertEquals(CycleStage.CYCLED, s3.stage)
        assertEquals(0, s3.daysLeft)
    }

    @Test
    fun ammoniaBackResetsTheCheck() {
        val rs = listOf(r(3.0, 2.0, 0.0), r(20.0, 0.0, 0.0, 10.0), r(21.0, 0.0, 0.0), r(22.0, 0.0, 0.5))
        assertEquals(CycleStage.NITRITE, CycleTracker.assess(rs, start, at(22.0)).stage)
    }

    @Test
    fun nitrateAloneCountsAsEvidence() {
        // cured live rock: no visible spike, nitrate shows the bacteria are working
        val rs = (0..2).map { r(it.toDouble(), 0.0, 0.0, 5.0) }
        assertEquals(CycleStage.CYCLED, CycleTracker.assess(rs, start, at(2.0)).stage)
    }

    @Test
    fun boostedCycleIsShorter() {
        val rs = listOf(r(1.0, 2.0, 0.0))
        val plain = CycleTracker.assess(rs, start, at(1.0))
        val boosted = CycleTracker.assess(rs, start, at(1.0), boosted = true)
        assertTrue(boosted.daysLeftHi < plain.daysLeftHi)
        assertTrue(boosted.daysLeft < plain.daysLeft)
    }
}
