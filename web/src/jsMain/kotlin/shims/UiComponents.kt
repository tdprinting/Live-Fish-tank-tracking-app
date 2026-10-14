@file:Suppress("PackageDirectoryMismatch")

package com.reeflive.app.ui.components

import com.reeflive.core.status.Status

/*
 * The two plain data classes the shared sandbox table (ui/live/SandboxParams.kt) uses from ui/components/ReefSlider.kt.
 * ReefSlider.kt itself is Compose UI, so the browser build carries these copies; keep them in step with it.
 */

data class SliderZones(val dangerBelow: Double?, val safeFrom: Double, val safeTo: Double, val dangerAbove: Double?) {
    fun status(v: Double): Status = when {
        dangerBelow != null && v < dangerBelow -> Status.DANGER
        dangerAbove != null && v > dangerAbove -> Status.DANGER
        v >= safeFrom && v <= safeTo -> Status.GOOD
        else -> Status.CAUTION
    }
}

data class ReefSliderSpec(
    val label: String,
    val unit: String,
    val min: Double,
    val max: Double,
    val step: Double,
    val decimals: Int,
    val zones: SliderZones? = null,
    val curve: Double = 1.0,
)
