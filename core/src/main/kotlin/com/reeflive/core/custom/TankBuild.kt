package com.reeflive.core.custom

import com.reeflive.core.sim.TankConfig
import com.reeflive.core.sim.TankDims

/**
 * A tank the keeper can start "My tank" in: size and the equipment that comes with it. The size sets the water
 * volume the chemistry, stocking and crowding run on (a nano's nitrate climbs ~10× faster than a 120's for the same
 * feeding; a tang is cramped below 450 L, LifeCycle.minVolumeL). The glass is drawn at Tee's 48 × 18 × 16 in for now;
 * the outside dimensions are saved with the tank so the 3D view can be sized to them later without a save migration.
 *
 * Volumes are the nominal gallons (US) the tank is sold as, as Tee's 60 gal is ([TankDims.DISPLAY_LITERS]).
 * Equipment follows common hobby sizing: about 3-5 W of heater per gallon (LiveAquaria heater guide), a skimmer from
 * ~40 gal up, a filter sock on sumped tanks.
 */
data class TankBuild(
    val id: String,
    val label: String,
    val blurb: String,
    val gallons: Double,
    val lengthIn: Double,
    val depthIn: Double,
    val heightIn: Double,
    val heaterWatts: Double,
    val skimmer: Boolean,
    val filterSock: Boolean,
    val refugium: Boolean = false,
    val ato: Boolean = false,
) {
    val liters: Double get() = gallons * LITERS_PER_GALLON
    val sizeLabel: String get() = "${fmt(lengthIn)} × ${fmt(depthIn)} × ${fmt(heightIn)} in"

    /** The sim config for this build ([seed] picks the individual temperaments of the animals added later). */
    fun config(seed: Long): TankConfig = TankConfig(
        systemLiters = liters, heaterWatts = heaterWatts, atoEnabled = ato, seed = seed,
    )

    companion object {
        const val LITERS_PER_GALLON = 3.785
        private fun fmt(v: Double) = if (v == kotlin.math.floor(v)) v.toInt().toString() else v.toString()
    }
}

object TankBuilds {
    val NANO = TankBuild(
        "nano_cube_20", "Nano cube", "Small all-in-one: parameters swing fast, room for a few small animals.",
        gallons = 20.0, lengthIn = 16.0, depthIn = 16.0, heightIn = 18.0, heaterWatts = 100.0, skimmer = false, filterSock = false, ato = true,
    )
    val BREEDER = TankBuild(
        "rimless_40", "Rimless 40 breeder", "Wide and shallow, a classic first reef with a small skimmer.",
        gallons = 40.0, lengthIn = 36.0, depthIn = 18.0, heightIn = 16.0, heaterWatts = 150.0, skimmer = true, filterSock = false,
    )
    val LONG_REEF = TankBuild(
        "long_reef_60", "Long reef 60", "The same size as your tank: 4 feet of swimming room.",
        gallons = TankDims.DISPLAY_GALLONS, lengthIn = 48.0, depthIn = 18.0, heightIn = 16.0, heaterWatts = 200.0, skimmer = true, filterSock = false,
    )
    val DISPLAY_120 = TankBuild(
        "display_120", "Big display 120", "Deep and wide with a sump and refugium: steady water, room for a tang.",
        gallons = 120.0, lengthIn = 48.0, depthIn = 24.0, heightIn = 24.0, heaterWatts = 400.0, skimmer = true, filterSock = true, refugium = true, ato = true,
    )

    val all = listOf(NANO, BREEDER, LONG_REEF, DISPLAY_120)
    val DEFAULT = LONG_REEF

    /** An unknown id (a build removed in a later version) falls back to the default so the save still opens. */
    fun byId(id: String?): TankBuild = all.firstOrNull { it.id == id } ?: DEFAULT
}
