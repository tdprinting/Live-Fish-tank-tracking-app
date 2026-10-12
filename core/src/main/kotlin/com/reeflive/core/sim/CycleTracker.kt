package com.reeflive.core.sim

/**
 * New-tank cycle checkpoints (research 01 §3.9; coverage gap W-5). Stage 1: ammonia rises while ammonia oxidisers
 * establish (days 0-7); 2: nitrite peaks (7-28); 3: nitrite oxidisers take over, NO2 falls and NO3 accumulates
 * (21-42); 4: cycled after 3 consecutive daily tests of 0 ammonia and 0 nitrite with a daily feed dose (28-56; 2-4
 * weeks with live rock or a bacteria starter, which the preset's 2 % seeded bed stands for). The daily test is read at
 * [TEST_MINUTE] (09:00, 24 h after the previous ghost feed).
 * "0" is read as below [ZERO] ppm (the first colour step of a hobby kit); the nitrite stage starts once NO2 passes 0.25 ppm and NH3.
 */
class CycleTracker(val startSec: Double) {
    enum class Stage(val label: String) {
        AMMONIA("Ammonia rising"),
        NITRITE("Nitrite peak"),
        NITRATE("Nitrite falling, nitrate rising"),
        CYCLED("Cycled"),
    }

    var stage = Stage.AMMONIA; private set
    var nh3Peak = 0.0; private set
    var nh3PeakDay = 0.0; private set
    var no2Peak = 0.0; private set
    var no2PeakDay = 0.0; private set
    /** Day the 3-day zero check passed (null until cycled). */
    var cycledDay: Double? = null; private set
    /** Day each stage was reached (index = Stage.ordinal). */
    val reachedDay = DoubleArray(Stage.entries.size) { Double.NaN }.also { it[0] = 0.0 }
    private var zeroDays = 0
    private var lastTestDay = Int.MIN_VALUE

    fun update(w: WaterState, simTimeSec: Double) {
        val t = (simTimeSec - startSec) / 86400
        val d = t.toInt()
        if (w.nh3Total > nh3Peak) { nh3Peak = w.nh3Total; nh3PeakDay = t }
        if (w.no2 > no2Peak) { no2Peak = w.no2; no2PeakDay = t }
        if (stage == Stage.AMMONIA && w.no2 > 0.25 && w.no2 > w.nh3Total) reach(Stage.NITRITE, t)
        if (stage == Stage.NITRITE && no2Peak > 0.1 && w.no2 < 0.5 * no2Peak) reach(Stage.NITRATE, t)
        val minute = (simTimeSec / 60) % 1440
        val absDay = Math.floorDiv((simTimeSec / 86400).toLong(), 1L).toInt()
        if (minute >= TEST_MINUTE && absDay != lastTestDay && d >= 1) {
            lastTestDay = absDay
            val zero = w.nh3Total < ZERO && w.no2 < ZERO
            zeroDays = if (zero && stage >= Stage.NITRATE) zeroDays + 1 else 0
            if (zeroDays >= 3 && stage != Stage.CYCLED) { reach(Stage.CYCLED, t); cycledDay = t }
        }
    }

    private fun reach(s: Stage, t: Double) { stage = s; reachedDay[s.ordinal] = t }

    companion object {
        const val ZERO = 0.05
        const val TEST_MINUTE = 9 * 60.0
    }
}
