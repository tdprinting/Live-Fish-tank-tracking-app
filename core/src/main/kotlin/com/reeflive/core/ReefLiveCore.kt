package com.reeflive.core

/** Entry point marker for the pure-Kotlin core module. Real domain code arrives in M1. */
object ReefLiveCore {
    const val NAME = "ReefLive"

    /** Greeting shown by the M0 placeholder screen. */
    fun greeting(): String = "Hello reef"

    /** US gallons to litres (exact definition: 1 US gal = 3.785411784 L). */
    fun gallonsToLitres(gallons: Double): Double = gallons * 3.785411784
}
