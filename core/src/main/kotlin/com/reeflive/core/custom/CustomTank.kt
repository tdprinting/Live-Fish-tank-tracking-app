package com.reeflive.core.custom

import com.reeflive.core.geom.Vec3
import com.reeflive.core.live.FastForward
import com.reeflive.core.sim.Coral
import com.reeflive.core.sim.Disease
import com.reeflive.core.sim.LifeCycle
import com.reeflive.core.sim.LivestockSeed
import com.reeflive.core.sim.MicroGroup
import com.reeflive.core.sim.Organism
import com.reeflive.core.sim.RockData
import com.reeflive.core.sim.Simulation
import com.reeflive.core.sim.SpeciesCatalog
import com.reeflive.core.sim.WaterState

/**
 * "My tank": the keeper's own tank that starts clean and empty, takes any animals from Add life and keeps them, with
 * their names, personalities, growth and health, from one app session (and app update) to the next. Both hosts
 * (Android SimulationHolder, the web page) build, save and reopen it through here, so they share one format.
 */
object CustomTank {
    /** Autosave cadence while the tank is on screen (wall ms); hosts also save when it leaves the screen. */
    const val AUTOSAVE_MS = 30_000L

    /**
     * A new, empty tank: cured rock and sand, cycled filter bacteria (fish can go in on day one, as with a tank set up
     * on mature live rock), clean new salt water, no algae, macro or coralline yet. Pods come with the rock.
     */
    fun fresh(build: TankBuild, species: SpeciesCatalog, rock: RockData, startTimeSec: Double, seed: Long): Simulation {
        val water = WaterState(
            volumeL = build.liters, no3 = 0.5, po4 = 0.01, detritus = 0.02, algaeHair = 0.0, macro = 0.0, coralline = 0.02,
            diatoms = 0.0, cyano = 0.0, silicate = 0.3, microbes = 0.5, doc = 0.5,
        )
        val sim = Simulation(build.config(seed), species, rock, seed, startTimeSec, water, emptyList())
        applyBuild(sim, build)
        return sim
    }

    private fun applyBuild(sim: Simulation, build: TankBuild) {
        sim.bareScape = true
        // the keeper's own tank has a living rockscape: rocks settle when dug under, and it can be rebuilt
        sim.setDynamicRocks(true)
        sim.water.skimmer = build.skimmer
        sim.water.filterSock = build.filterSock
        sim.water.refugium = build.refugium
        sim.water.ato = build.ato
    }

    /** Seconds to skip on reopening after [savedAtMs]: the time away at 1×, capped (FastForward, 24 h). */
    fun catchUpSeconds(savedAtMs: Long, nowMs: Long): Double = FastForward.simSeconds(nowMs - savedAtMs, 1.0)

    // ============================================================== save

    fun capture(sim: Simulation, base: CustomTankSave, nowMs: Long): CustomTankSave {
        val animals = sim.organisms.filter { it.alive }.map { save(it).copy(state = sim.anemones.saveState(it)) } + sim.bags.unreleased().map { save(it).copy(arrivalTime = sim.simTimeSec) }
        val corals = sim.corals.filter { it.tissue > 0.01 }.map { save(it) } + sim.bags.unopenedCorals().flatMap { (sp, n) ->
            (0 until n).map { i -> SavedCoral("$sp#bagged${sim.tickCount}_$i", sp, sim.species[sp].common) }
        }
        val w = sim.water
        val eq = mapOf(
            "heater_set_c" to w.heaterSetC, "heater_working" to b(w.heaterWorking), "flow_strength" to w.flowStrength,
            "flow_on" to b(w.s.flowOn), "skimmer" to b(w.skimmer), "filter_sock" to b(w.filterSock), "carbon" to b(w.carbon),
            "uv" to b(w.uv), "refugium" to b(w.refugium), "ato" to b(w.ato), "gfo" to b(w.s.gfoActive),
            "predation" to b(sim.predationEnabled),
        )
        return base.copy(
            savedAtMs = nowMs,
            simTimeSec = sim.simTimeSec,
            speed = sim.speed,
            water = WaterFields.read(w.s),
            equipment = eq,
            micro = MicroGroup.entries.associate { it.name to w.micro.total(it) }.filterValues { it.isFinite() },
            animals = animals,
            corals = corals,
            brood = sim.brood.save(),
            rocks = sim.rockState(),
            pests = sim.pests.save(),
            nextIds = SavedIds(
                organism = maxOf(base.nextIds.organism, nextNumber(animals.map { it.id } + corals.map { it.id }, "#")),
                frag = base.nextIds.frag,
                bag = maxOf(base.nextIds.bag, nextNumber(animals.map { it.id }, "#bag")),
            ),
        )
    }

    private fun b(v: Boolean) = if (v) 1.0 else 0.0

    /** One past the largest number after [marker] in [ids] ("x#12" with "#" -> 13; "x#bag3_0" with "#bag" -> 4). */
    private fun nextNumber(ids: List<String>, marker: String): Int {
        var best = 0
        for (id in ids) {
            val at = id.lastIndexOf(marker)
            if (at < 0) continue
            val n = id.substring(at + marker.length).takeWhile { it.isDigit() }.toIntOrNull() ?: continue
            if (n > best) best = n
        }
        return best + 1
    }

    private fun save(o: Organism) = SavedAnimal(
        id = o.id, speciesId = o.species.id, nickname = o.nickname, role = o.role, placement = o.placement, variant = o.variant,
        lengthCm = o.lengthCm, ageSec = o.ageSec, agingDebtSec = o.agingDebtSec, arrivalTime = o.arrivalTime,
        aggression = o.aggression, boldness = o.boldness, curiosity = o.curiosity, skittishness = o.skittishness,
        acceptsPrepared = o.acceptsPrepared, sexFemale = o.sexFemale, sexChange = o.sexChange, mateId = o.mateId,
        health = o.health, condition = o.condition, hunger = o.hunger, stress = o.stress, chronic = o.chronic,
        immunity = o.immunity, disease = o.disease?.name, diseaseStage = o.diseaseStage, hlle = o.hlle,
        nutritionGreens = o.nutritionGreens,
    )

    private fun save(c: Coral) = SavedCoral(
        id = c.id, speciesId = c.species.id, nickname = c.nickname, anchor = c.anchor,
        offsetX = c.offset.x, offsetY = c.offset.y, offsetZ = c.offset.z,
        x = c.pos.x, y = c.pos.y, z = c.pos.z, nx = c.normal.x, ny = c.normal.y, nz = c.normal.z,
        tissue = c.tissue, bleach = c.bleach, brown = c.brown, stress = c.stress, dhw = c.dhw, algaeCover = c.algaeCover,
        size = c.size, cover = c.cover, radiusCm = c.radiusCm, fed = c.fed, acclLight = if (c.acclLight.isNaN()) -1.0 else c.acclLight,
    )

    // ============================================================== reopen

    /** Species ids a host must load before [restore] (sheets are loaded per id). */
    fun speciesIds(save: CustomTankSave): List<String> =
        (save.animals + save.dormant).map { it.speciesId }.plus((save.corals + save.dormantCorals).map { it.speciesId }).distinct()

    /**
     * Rebuilds the saved tank at its saved sim time (the host then skips it forward by [catchUpSeconds]). Animals of
     * species [species] cannot load are left out here and carried by [baseAfterRestore] for the next save.
     */
    fun restore(save: CustomTankSave, species: SpeciesCatalog, rock: RockData): Simulation {
        speciesIds(save).forEach { species.ensure(it) }
        val build = TankBuilds.byId(save.buildId)
        val water = WaterState(volumeL = build.liters)
        WaterFields.write(water, save.water)
        water.volumeL = build.liters
        val known = (save.animals + save.dormant).filter { species.has(it.speciesId) }
        val anchored = (save.corals + save.dormantCorals).filter { species.has(it.speciesId) }
        val seeds = known.map {
            LivestockSeed(it.speciesId, it.nickname, it.lengthCm, it.role, it.placement, it.id, it.variant, ageYears = it.ageSec / LifeCycle.SEC_PER_YEAR)
        } + anchored.filter { it.anchor.isNotBlank() || it.x == 0.0 }.map {
            val off = Vec3(it.offsetX, it.offsetY, it.offsetZ)
            LivestockSeed(it.speciesId, it.nickname, 0.0, null, it.anchor.ifBlank { null }, it.id, offset = off.takeIf { o -> o != Vec3.ZERO })
        }
        val sim = Simulation(build.config(save.seed), species, rock, save.seed, save.simTimeSec, water, seeds)
        applyBuild(sim, build)
        sim.restoreRocks(save.rocks)
        // loose colonies (dropped branches, frags) sit where they were
        for (c in anchored) if (c.anchor.isBlank() && c.x != 0.0) {
            sim.corals += Coral(c.id, species[c.speciesId], c.nickname, "", Vec3(c.x, c.y, c.z), Vec3(c.nx, c.ny, c.nz), 0.0)
        }
        val byId = known.associateBy { it.id }
        for (o in sim.organisms) byId[o.id]?.let { load(o, it); sim.anemones.loadState(o, it.state) }
        sim.anemones.relinkHosts()
        val coralsById = anchored.associateBy { it.id }
        for (c in sim.corals) coralsById[c.id]?.let { s ->
            c.tissue = s.tissue; c.bleach = s.bleach; c.brown = s.brown; c.stress = s.stress; c.dhw = s.dhw
            c.algaeCover = s.algaeCover; c.size = s.size; c.cover = s.cover; c.radiusCm = s.radiusCm; c.established = true
            c.fed = s.fed.coerceIn(0.0, 1.0); if (s.acclLight >= 0) c.acclLight = s.acclLight
        }
        val eq = save.equipment
        val w = sim.water
        eq["heater_set_c"]?.let { w.heaterSetC = it }
        eq["heater_working"]?.let { w.heaterWorking = it > 0.5 }
        eq["flow_strength"]?.let { w.flowStrength = it }
        eq["flow_on"]?.let { w.s.flowOn = it > 0.5 }
        eq["skimmer"]?.let { w.skimmer = it > 0.5 }
        eq["filter_sock"]?.let { w.filterSock = it > 0.5 }
        eq["carbon"]?.let { w.carbon = it > 0.5 }
        eq["uv"]?.let { w.uv = it > 0.5 }
        eq["refugium"]?.let { w.refugium = it > 0.5 }
        eq["ato"]?.let { w.ato = it > 0.5 }
        eq["gfo"]?.let { w.s.gfoActive = it > 0.5 }
        eq["predation"]?.let { sim.predationEnabled = it > 0.5 }
        for ((name, n) in save.micro) {
            val g = MicroGroup.entries.firstOrNull { it.name == name } ?: continue
            if (n.isFinite() && n >= 0) w.micro.setTotal(g, n)
        }
        sim.brood.load(save.brood)
        sim.pests.restore(save.pests)
        sim.speed = save.speed.coerceIn(1.0, 1000.0)
        sim.reserveIds(save.nextIds.organism, save.nextIds.frag, save.nextIds.bag)
        return sim
    }

    /**
     * The save to [capture] onto after [restore]: same identity, with the animals [species] could not load moved to
     * [CustomTankSave.dormant] so the next save writes them back unchanged.
     */
    fun baseAfterRestore(save: CustomTankSave, species: SpeciesCatalog): CustomTankSave = save.copy(
        dormant = (save.animals + save.dormant).filterNot { species.has(it.speciesId) },
        dormantCorals = (save.corals + save.dormantCorals).filterNot { species.has(it.speciesId) },
    )

    private fun load(o: Organism, s: SavedAnimal) {
        if (s.lengthCm > 0) o.lengthCm = s.lengthCm
        o.ageSec = s.ageSec; o.agingDebtSec = s.agingDebtSec; o.arrivalTime = s.arrivalTime
        s.aggression?.let { o.aggression = it.coerceIn(0.0, 1.0) }
        s.boldness?.let { o.boldness = it.coerceIn(0.0, 1.0) }
        s.curiosity?.let { o.curiosity = it.coerceIn(0.0, 1.0) }
        s.skittishness?.let { o.skittishness = it.coerceIn(0.0, 1.0) }
        o.acceptsPrepared = s.acceptsPrepared
        o.sexFemale = s.sexFemale; o.sexChange = s.sexChange; o.mateId = s.mateId
        o.health = s.health.coerceIn(0.0, 1.0); o.condition = s.condition; o.hunger = s.hunger.coerceIn(0.0, 1.0)
        o.stress = s.stress; o.chronic = s.chronic; o.immunity = s.immunity
        o.disease = s.disease?.let { d -> Disease.entries.firstOrNull { it.name == d } }
        o.diseaseStage = if (o.disease != null) s.diseaseStage else 0.0
        o.hlle = s.hlle; o.nutritionGreens = s.nutritionGreens
    }
}

/**
 * [WaterState] as a name → value map for saves. Names are stable save keys: never reuse one for a different meaning;
 * a renamed sim field keeps its old key here. Derived values (free ammonia, PAR) are left out: the sim recomputes them.
 */
object WaterFields {
    private class F(val key: String, val get: (WaterState) -> Double, val set: (WaterState, Double) -> Unit)

    private val fields = listOf(
        F("temp_c", { it.tempC }, { w, v -> w.tempC = v }), F("sg", { it.sg }, { w, v -> w.sg = v }),
        F("ph", { it.ph }, { w, v -> w.ph = v }), F("alk", { it.alk }, { w, v -> w.alk = v }),
        F("ca", { it.ca }, { w, v -> w.ca = v }), F("mg", { it.mg }, { w, v -> w.mg = v }),
        F("nh3_total", { it.nh3Total }, { w, v -> w.nh3Total = v }), F("no2", { it.no2 }, { w, v -> w.no2 = v }),
        F("no3", { it.no3 }, { w, v -> w.no3 = v }), F("po4", { it.po4 }, { w, v -> w.po4 = v }),
        F("do_mg_l", { it.doMgL }, { w, v -> w.doMgL = v }), F("k", { it.k }, { w, v -> w.k = v }),
        F("sr", { it.sr }, { w, v -> w.sr = v }), F("i", { it.i }, { w, v -> w.i = v }),
        F("copper", { it.copper }, { w, v -> w.copper = v }),
        F("algae_hair", { it.algaeHair }, { w, v -> w.algaeHair = v }), F("macro", { it.macro }, { w, v -> w.macro = v }),
        F("cyano", { it.cyano }, { w, v -> w.cyano = v }), F("diatoms", { it.diatoms }, { w, v -> w.diatoms = v }),
        F("coralline", { it.coralline }, { w, v -> w.coralline = v }), F("dinos", { it.dinos }, { w, v -> w.dinos = v }),
        F("film", { it.film }, { w, v -> w.film = v }), F("bubble", { it.bubble }, { w, v -> w.bubble = v }),
        F("silicate", { it.silicate }, { w, v -> w.silicate = v }), F("si_leach", { it.siLeach }, { w, v -> w.siLeach = v }),
        F("microbes", { it.microbes }, { w, v -> w.microbes = v }),
        F("bacteria_aob", { it.bacteriaAob }, { w, v -> w.bacteriaAob = v }), F("bacteria_nob", { it.bacteriaNob }, { w, v -> w.bacteriaNob = v }),
        F("detritus", { it.detritus }, { w, v -> w.detritus = v }), F("co2_index", { it.co2Index }, { w, v -> w.co2Index = v }),
        F("doc", { it.doc }, { w, v -> w.doc = v }), F("allelo", { it.allelo }, { w, v -> w.allelo = v }),
        F("chaeto", { it.chaeto }, { w, v -> w.chaeto = v }), F("h2s", { it.h2s }, { w, v -> w.h2s = v }),
    )

    fun read(w: WaterState): Map<String, Double> {
        val out = LinkedHashMap<String, Double>()
        for (f in fields) { val v = f.get(w); if (v.isFinite()) out[f.key] = v }
        return out
    }

    /** Applies the known, finite values of [m] to [w]; missing keys keep [w]'s values. */
    fun write(w: WaterState, m: Map<String, Double>) {
        for (f in fields) m[f.key]?.takeIf { it.isFinite() }?.let { f.set(w, it) }
    }
}
