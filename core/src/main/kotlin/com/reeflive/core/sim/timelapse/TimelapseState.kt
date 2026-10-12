package com.reeflive.core.sim.timelapse

/** What the UI shows for the time-lapse feature. */
sealed class TimelapseState {
    data object Idle : TimelapseState()
    data class Running(val spec: TimelapseSpec, val progress: TimelapseProgress) : TimelapseState()
    data class Done(val spec: TimelapseSpec, val recording: TimelapseRecording, val report: TimelapseReport, val cancelled: Boolean) : TimelapseState()
    data class Failed(val message: String) : TimelapseState()
}
