package com.reeflive.core.sim.timelapse

import com.reeflive.core.sim.LightSchedule
import com.reeflive.core.sim.LivestockSeed
import com.reeflive.core.sim.Simulation

/**
 * Copies a running [Simulation] into an independent one (water, equipment, livestock and their health
 * state, corals, lights). Behaviour positions and floating food are not carried over: a time-lapse
 * runs the abstract layer, where animals are parked at their behaviour's home spots anyway.
 * Call on the thread that owns [src].
 */
object TimelapseFork {
    fun of(src: Simulation): Simulation {
        val seeds = src.organisms.filter { it.alive }.map {
            LivestockSeed(it.species.id, it.nickname, it.lengthCm, it.role, it.placement, it.id, it.variant)
        } + src.corals.map {
            LivestockSeed(it.species.id, it.nickname, 0.0, null, it.anchor.ifBlank { null }, it.id, offset = it.offset.takeIf { o -> o != com.reeflive.core.geom.Vec3.ZERO })
        }
        val lights = LightSchedule(src.lights.points).also {
            it.forcedOn = src.lights.forcedOn; it.intensityOverride = src.lights.intensityOverride
            it.photoperiodHours = src.lights.photoperiodHours; src.lights.gains.copyInto(it.gains)
        }
        val dst = Simulation(
            config = src.config.copy(seed = src.config.seed + 31),
            species = src.species,
            rock = src.rock,
            seed = src.config.seed + 31,
            startTimeSec = src.simTimeSec,
            initialWater = src.water.s.copy(),
            livestock = seeds,
            lights = lights,
        )
        dst.predationEnabled = src.predationEnabled
        val w = src.water
        dst.water.flowStrength = w.flowStrength; dst.water.heaterWorking = w.heaterWorking
        dst.water.heaterStuckOn = w.heaterStuckOn; dst.water.heaterSetC = w.heaterSetC; dst.water.ato = w.ato
        dst.water.algae.copyFrom(w.algae)
        dst.water.copyEquipmentFrom(w)
        val srcOrgs = src.organisms.associateBy { it.id }
        for (o in dst.organisms) {
            val s = srcOrgs[o.id] ?: continue
            o.health = s.health; o.stress = s.stress; o.chronic = s.chronic; o.immunity = s.immunity
            o.hunger = s.hunger; o.condition = s.condition; o.disease = s.disease; o.diseaseStage = s.diseaseStage
            o.hlle = s.hlle; o.acclTemp = s.acclTemp; o.acclSg = s.acclSg; o.acclPh = s.acclPh
            o.nutritionGreens = s.nutritionGreens; o.mood = s.mood
            o.symptoms.clear(); o.symptoms += s.symptoms
            // research 06 stress state: acclimated setpoints and the acute/chronic channels carry over
            val a = s.stressState; val b = o.stressState
            a.fast.copyInto(b.fast); a.slow.copyInto(b.slow); a.setpoint.copyInto(b.setpoint)
            b.socialInput = a.socialInput; b.heatSlow = a.heatSlow; b.lowPh = a.lowPh; b.sEnv = a.sEnv
            o.arrivalTime = s.arrivalTime
        }
        val srcCorals = src.corals.associateBy { it.id }
        for (c in dst.corals) {
            val s = srcCorals[c.id] ?: continue
            c.tissue = s.tissue; c.bleach = s.bleach; c.brown = s.brown; c.stress = s.stress; c.dhw = s.dhw
            c.acclSg = s.acclSg; c.acclAlk = s.acclAlk; c.acclLight = s.acclLight; c.algaeCover = s.algaeCover
        }
        return dst
    }
}
