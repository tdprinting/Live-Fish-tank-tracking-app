package com.reeflive.core.sim

import com.reeflive.core.model.Livestock
import com.reeflive.core.model.LivestockStatus

/**
 * Reality mode (SIMULATION §9.9): builds the simulation's starting state from the tracker's data.
 * The inputs are the measurement repository's latest values in Track A base units (temperature °F,
 * salinity SG), the living livestock rows and the active light schedule.
 */
object Reality {
    /** Water from the latest logged values; anything not logged keeps [base]. */
    fun waterFrom(latest: Map<String, Double>, base: WaterState = WaterState()): WaterState {
        val w = base.copy()
        for ((key, raw) in latest) {
            if (raw.isNaN()) continue
            when (key.lowercase()) {
                // Track A stores °F; a value that is plausibly °C is taken as is.
                "temp" -> w.tempC = if (raw > 45) (raw - 32) * 5.0 / 9.0 else raw
                // SG, or ppt when someone logged 35
                "sal", "sg" -> w.sg = if (raw > 5) 1 + raw * 0.000751 else raw
                "alk" -> w.alk = raw
                "ca" -> w.ca = raw
                "mg" -> w.mg = raw
                "ph" -> w.ph = raw
                "no3" -> w.no3 = raw
                "po4" -> w.po4 = raw
                "nh3" -> { w.nh3Total = raw; w.nh3Free = ChemFns.freeNh3(raw, w.ph, w.tempC) }
                "no2" -> w.no2 = raw
                "k" -> w.k = raw
                "sr" -> w.sr = raw
                "i" -> w.i = raw
                "do" -> w.doMgL = raw
            }
        }
        w.tempC = w.tempC.coerceIn(10.0, 40.0)
        w.sg = w.sg.coerceIn(1.000, 1.040)
        w.ph = w.ph.coerceIn(6.5, 9.5)
        // a unit slip (ppb as ppm, NO3-N as NO3) must not feed the sim a nonsense tank
        w.no3 = w.no3.coerceIn(0.0, com.reeflive.core.live.ReadingSanity.NO3_MAX)
        w.po4 = w.po4.coerceIn(0.0, com.reeflive.core.live.ReadingSanity.PO4_MAX)
        w.nh3Free = ChemFns.freeNh3(w.nh3Total, w.ph, w.tempC)
        return w
    }

    /**
     * One seed per animal. Group rows (snails, hermits) expand to `quantity` organisms; the first
     * keeps the livestock id so "View in Live Tank" (LiveFocus.followId) finds it, the rest get
     * `<id>#2`, `<id>#3` and so on.
     */
    fun seedsFrom(livestock: List<Livestock>, sizeOf: (String) -> Double? = { null }): List<LivestockSeed> = buildList {
        for (l in livestock) {
            if (l.status != LivestockStatus.ALIVE) continue
            val n = l.quantity.coerceIn(1, 40)
            for (k in 0 until n) {
                val id = if (k == 0) l.id else "${l.id}#${k + 1}"
                val nick = if (n > 1) "${l.nickname} ${k + 1}" else l.nickname
                val size = l.sizeCm ?: sizeOf(l.speciesId) ?: 0.0
                // spread the group a little in size so they read as individuals
                val jitter = if (n > 1 && size > 0) size * (0.9 + 0.2 * ((k * 37) % 11) / 10.0) else size
                add(LivestockSeed(l.speciesId, nick, jitter, role = l.role, placement = if (k == 0) l.placement else null, id = id, variant = l.variant))
            }
        }
    }

    /** The tracker's light schedule as the simulation's 8-channel schedule. */
    fun lightsFrom(schedule: com.reeflive.core.model.LightSchedule?): LightSchedule {
        val pts = schedule?.points.orEmpty().mapNotNull { p ->
            if (p.channels.size < LightSchedule.CHANNELS) null
            else LightSchedule.LightPoint(p.minuteOfDay, DoubleArray(LightSchedule.CHANNELS) { p.channels[it].coerceIn(0.0, 1.0) })
        }
        return LightSchedule(pts)
    }

    /** Livestock id for an organism id from [seedsFrom] (strips the group suffix). */
    fun livestockIdOf(organismId: String): String = organismId.substringBefore('#')
}
