package com.reeflive.core.sim.timelapse

import com.reeflive.core.sim.Command
import com.reeflive.core.sim.Simulation
import java.util.concurrent.atomic.AtomicBoolean

/** Thread-safe cancel flag for a [TimelapseRun]. */
class CancelToken {
    private val flag = AtomicBoolean(false)
    fun cancel() { flag.set(true) }
    val isCancelled: Boolean get() = flag.get()
}

/** Progress of a running time-lapse. [fraction] is 0..1. */
data class TimelapseProgress(val fraction: Float, val simHour: Double, val totalHours: Double, val samples: Int)

/** Outcome of [TimelapseRun.run]; a cancelled run still carries the partial recording and report. */
data class TimelapseResult(val recording: TimelapseRecording, val report: TimelapseReport, val cancelled: Boolean)

/**
 * Runs [spec] headless on [sim] (normally a [TimelapseFork] of the live sim; this mutates it) using the
 * abstract stepper ([Simulation.advanceAbstract]: 60 s chemistry/health steps, behaviour layer off), in
 * chunks of [TimelapseSpec.chunkSec]. Cheap enough for four weeks of tank time in seconds.
 */
class TimelapseRun(private val sim: Simulation, private val spec: TimelapseSpec) {

    private class Timed(val atSec: Double, val command: Command)

    private class RampState(val change: TimelapseChange.Ramp) {
        var from: Double? = null
        var done = false
    }

    fun run(cancel: CancelToken = CancelToken(), onProgress: (TimelapseProgress) -> Unit = {}): TimelapseResult {
        val recorder = TimelapseRecorder(sim, spec)
        val timed = buildTimed().sortedBy { it.atSec }
        val ramps = spec.changes.filterIsInstance<TimelapseChange.Ramp>().map(::RampState)
        var next = 0
        var t = 0.0
        var nextSample = 0.0
        val total = spec.durationSec
        val eps = 1e-6
        var cancelled = false

        recorder.sample()
        nextSample += spec.sampleSec
        onProgress(TimelapseProgress(0f, 0.0, spec.durationHours, recorder.sampleCount))

        while (t < total - eps) {
            if (cancel.isCancelled) { cancelled = true; break }
            while (next < timed.size && timed[next].atSec <= t + eps) sim.enqueue(timed[next++].command)
            for (r in ramps) applyRamp(r, t)
            val dt = minOf(spec.chunkSec, total - t)
            sim.advanceAbstract(dt)
            t += dt
            recorder.collectEvents()
            if (t >= nextSample - eps) {
                recorder.sample()
                nextSample += spec.sampleSec
            }
            onProgress(TimelapseProgress((t / total).toFloat().coerceIn(0f, 1f), t / 3600.0, spec.durationHours, recorder.sampleCount))
        }
        if (!cancelled && recorder.sampleCount > 0 && recorder.hourNow() > recorder.lastSampleHour + 1e-6) recorder.sample()

        val recording = recorder.finish(completed = !cancelled)
        return TimelapseResult(recording, TimelapseReport.build(recording), cancelled)
    }

    private fun applyRamp(r: RampState, tSec: Double) {
        val c = r.change
        val start = c.startHour * 3600.0
        if (r.done || tSec < start - 1e-6) return
        val from = r.from ?: (c.from ?: sim.water.s.get(c.paramId) ?: c.to).also { r.from = it }
        val frac = if (c.overHours <= 0) 1.0 else ((tSec - start) / (c.overHours * 3600.0)).coerceIn(0.0, 1.0)
        sim.enqueue(Command.SetParam(c.paramId, from + (c.to - from) * frac))
        if (frac >= 1.0) r.done = true
    }

    private fun buildTimed(): List<Timed> = buildList {
        for (ch in spec.changes) when (ch) {
            is TimelapseChange.Step -> add(Timed(ch.atHour * 3600.0, Command.SetParam(ch.paramId, ch.value)))
            is TimelapseChange.Do -> add(Timed(ch.atHour * 3600.0, ch.command))
            is TimelapseChange.Schedule -> {
                val period = ch.repeatEveryHours?.takeIf { it > 0 }
                var cycle = 0
                do {
                    val base = (period ?: 0.0) * cycle
                    for ((h, v) in ch.points) {
                        val at = base + h
                        if (at <= spec.durationHours) add(Timed(at * 3600.0, Command.SetParam(ch.paramId, v)))
                    }
                    cycle++
                } while (period != null && period * cycle <= spec.durationHours)
            }
            is TimelapseChange.Ramp -> Unit
        }
    }
}
