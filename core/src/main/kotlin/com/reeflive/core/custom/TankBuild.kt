package com.reeflive.core.custom

import com.reeflive.core.geom.FlowField
import com.reeflive.core.sim.TankConfig
import com.reeflive.core.sim.TankDims
import com.reeflive.core.sim.TankFilter
import com.reeflive.core.sim.TankShape

/**
 * A tank the keeper can start "My tank" in: size and the equipment that comes with it. The size sets the water
 * volume the chemistry, stocking and crowding run on (a nano's nitrate climbs ~10× faster than a 120's for the same
 * feeding; a tang is cramped below 450 L, LifeCycle.minVolumeL). The inch dimensions are the tank's box ([shape]):
 * the swimming space, the 3D glass, rim and stand, where the equipment hangs and how much of the stock rockscape fits.
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
    /** Polished glass edges and no black frame (a modern rimless tank). */
    val rimless: Boolean = false,
    val filter: TankFilter = TankFilter.HOB,
) {
    val liters: Double get() = gallons * LITERS_PER_GALLON
    val sizeLabel: String get() = "${fmt(lengthIn)} × ${fmt(depthIn)} × ${fmt(heightIn)} in"

    /** The tank's box in the sim's world frame (cm, to the millimetre) and its look. */
    val shape: TankShape by lazy {
        TankShape(cm(lengthIn), cm(heightIn), cm(depthIn), rimless = rimless, filter = filter, skimmer = skimmer && filter == TankFilter.HOB)
    }

    /**
     * The sim config for this build ([seed] picks the individual temperaments of the animals added later). No
     * automatic feeding: in My tank the keeper feeds (a daily job, docs/ECONOMY.md §9).
     */
    fun config(seed: Long): TankConfig {
        val s = shape
        return TankConfig(
            lengthCm = s.lengthCm, widthCm = s.depthCm, heightCm = s.heightCm, waterLevelCm = s.waterLevelCm, sandTopCm = s.sandTopCm,
            systemLiters = liters, pumpScale = liters / TankDims.DISPLAY_LITERS, heaterWatts = heaterWatts, atoEnabled = ato, seed = seed, autoFeed = emptyList(),
            // the powerheads hang where they do on Tee's tank, moved with their glass (TankShape)
            powerheads = FlowField.defaultJets().map { it.copy(pos = s.map(it.pos)) },
        )
    }

    companion object {
        const val LITERS_PER_GALLON = 3.785
        private fun cm(inches: Double) = kotlin.math.round(inches * 25.4) / 10.0
        private fun fmt(v: Double) = if (v == kotlin.math.floor(v)) v.toInt().toString() else v.toString()
    }
}

object TankBuilds {
    val NANO = TankBuild(
        "nano_cube_20", "Nano cube", "Small all-in-one: parameters swing fast, room for a few small animals.",
        gallons = 20.0, lengthIn = 16.0, depthIn = 16.0, heightIn = 18.0, heaterWatts = 100.0, skimmer = false, filterSock = false, ato = true,
        filter = TankFilter.AIO,
    )
    val BREEDER = TankBuild(
        "rimless_40", "Rimless 40 breeder", "Wide and shallow, a classic first reef with a small skimmer.",
        gallons = 40.0, lengthIn = 36.0, depthIn = 18.0, heightIn = 16.0, heaterWatts = 150.0, skimmer = true, filterSock = false,
        rimless = true,
    )
    val LONG_REEF = TankBuild(
        "long_reef_60", "Long reef 60", "The same size as your tank: 4 feet of swimming room.",
        gallons = TankDims.DISPLAY_GALLONS, lengthIn = 48.0, depthIn = 18.0, heightIn = 16.0, heaterWatts = 200.0, skimmer = true, filterSock = false,
    )
    val DISPLAY_120 = TankBuild(
        "display_120", "Big display 120", "Deep and wide with a sump and refugium: steady water, room for a tang.",
        gallons = 120.0, lengthIn = 48.0, depthIn = 24.0, heightIn = 24.0, heaterWatts = 400.0, skimmer = true, filterSock = true, refugium = true, ato = true,
        filter = TankFilter.SUMP,
    )

    val all = listOf(NANO, BREEDER, LONG_REEF, DISPLAY_120)
    val DEFAULT = LONG_REEF

    /** An unknown id (a build removed in a later version) falls back to the default so the save still opens. */
    fun byId(id: String?): TankBuild = all.firstOrNull { it.id == id } ?: DEFAULT
}
