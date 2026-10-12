package com.reeflive.core.sim.timelapse

import com.reeflive.core.sim.DefaultStock
import com.reeflive.core.sim.Simulation
import com.reeflive.core.sim.TestTank
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class TimelapseTest {
    private fun live(): Simulation = TestTank.sim(DefaultStock.all, seed = 3L).also { it.advanceAbstract(3600.0) }

    private fun run(spec: TimelapseSpec, cancel: CancelToken = CancelToken(), on: (TimelapseProgress) -> Unit = {}) =
        TimelapseRun(TimelapseFork.of(live()), spec).run(cancel, on)

    @Test fun salinityStepRaisesStressAndReportBlamesSalinity() {
        val res = run(TimelapseSpec.step("sg", 1.035, 72.0, "Salinity 1.035"))
        val rec = res.recording
        assertTrue(rec.completed)
        assertTrue(rec.samples.first().water.sg < 1.03)
        assertTrue(rec.samples.last().water.sg > 1.033)
        val fish = rec.samples.first().organisms.filter { !it.isCoral }.map { it.id }
        val peak = fish.maxOf { id -> rec.series(id) { it.stress }.maxOf { it.second } }
        val start = fish.maxOf { id -> rec.series(id) { it.stress }.first().second }
        assertTrue("stress should rise: start=$start peak=$peak", peak > start + 0.3f)
        val affected = res.report.organisms.filter { !it.coped && it.cause != null }
        assertTrue("someone should be affected: ${res.report.headline()}", affected.isNotEmpty())
        assertTrue("salinity should be the usual cause: ${affected.map { it.nickname to it.cause?.channel }}",
            affected.count { it.cause!!.channel == "sg" } >= affected.size / 2 + 1)
        println(res.report.headline())
        res.report.species.forEach { s -> println("${s.common}: ${s.verdict} at ${s.firstOnsetHour} cause=${s.cause?.label}") }
    }

    @Test fun noChangeRunReportsAllOk() {
        val res = run(TimelapseSpec.none(7 * 24.0))
        assertTrue("unexpected: " + res.report.organisms.filter { !it.coped }.map { "${it.nickname} ${it.verdict} peak=${it.peakStress} cause=${it.cause?.channel}" }, res.report.allOk)
        assertTrue(res.recording.samples.size > 100)
    }

    @Test fun cancelStopsEarlyWithPartialRecording() {
        val token = CancelToken()
        var last = 0f
        val res = run(TimelapseSpec.step("sg", 1.035, 28 * 24.0), token) { p ->
            last = p.fraction
            if (p.fraction > 0.1f) token.cancel()
        }
        assertTrue(res.cancelled)
        assertFalse(res.recording.completed)
        assertTrue("stopped promptly, last=$last", last < 0.2f)
        assertTrue(res.recording.samples.isNotEmpty())
    }

    @Test fun progressIsMonotonicAndReachesOne() {
        val seen = ArrayList<Float>()
        run(TimelapseSpec.none(24.0)) { seen += it.fraction }
        assertEquals(seen.sorted(), seen)
        assertEquals(1f, seen.last(), 0f)
    }

    @Test fun rampAndScheduleReachTheirTargets() {
        val ramp = run(TimelapseSpec.ramp("sg", 1.030, 24.0, 48.0)).recording
        val mid = ramp.sampleAt(12.0)!!.water.sg
        assertTrue("mid-ramp sg=$mid", mid in 1.026..1.029)
        assertTrue(ramp.samples.last().water.sg in 1.0295..1.0305)
        val sched = TimelapseSpec(listOf(TimelapseChange.Schedule("sg", listOf(0.0 to 1.022, 12.0 to 1.027), repeatEveryHours = 24.0)), 48.0)
        val rec = run(sched).recording
        assertTrue(rec.sampleAt(6.0)!!.water.sg < rec.sampleAt(18.0)!!.water.sg)
    }

    @Test fun forkDoesNotTouchTheSource() {
        val src = live()
        val before = src.water.s.sg
        TimelapseRun(TimelapseFork.of(src), TimelapseSpec.step("sg", 1.035, 12.0)).run()
        assertEquals(before, src.water.s.sg, 0.0)
    }
}
