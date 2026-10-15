package com.reeflive.core.sim

import java.io.File

/**
 * Shared fixtures for the simulation tests: species sheets from test resources + the rockscape.
 * Uses Track B's baked `rock_sdf.bin` / `anchors.json` from the app assets when present (the real
 * tank), else the analytic stand-in. `-Dreeflive.analyticRock=true` forces the analytic scape.
 */
object TestTank {
    val catalog: SpeciesCatalog by lazy {
        SpeciesCatalog.fromClasspath(DefaultStock.all.map { it.speciesId }.distinct(), "sim-fixtures/species/")
    }

    private fun assetDir(): File? = listOf("../app/src/main/assets/sim", "app/src/main/assets/sim").map(::File)
        .firstOrNull { File(it, "rock_sdf.bin").isFile && File(it, "anchors.json").isFile }

    val bakedRock: RockData? by lazy {
        assetDir()?.let { d -> RockData.fromFiles(File(d, "rock_sdf.bin").readBytes(), File(d, "anchors.json").readText()) }
    }

    val rock: RockData by lazy {
        if (System.getProperty("reeflive.analyticRock") == "true") RockData.defaultTank() else bakedRock ?: RockData.defaultTank()
    }

    /** [bakedRock] with the bake's errors mended, as the app and the web load it for the solid bodies. */
    val solidRock: RockData by lazy {
        if (System.getProperty("reeflive.analyticRock") == "true") RockData.defaultTank()
        else assetDir()?.let { d -> RockData.fromFiles(File(d, "rock_sdf.bin").readBytes(), File(d, "anchors.json").readText(), repairBake = true) }
            ?: RockData.defaultTank()
    }

    /** [sim] on [solidRock] with the solid bodies on ([Simulation.solidBodies]). */
    fun solidSim(
        livestock: List<LivestockSeed>,
        seed: Long = 1L,
        startHour: Double = 12.0,
    ) = Simulation(TankConfig(seed = seed), catalog, solidRock, seed, startHour * 3600, WaterState(), livestock).also { it.solidBodies = true }

    fun sim(
        livestock: List<LivestockSeed>,
        seed: Long = 1L,
        startHour: Double = 12.0,
        water: WaterState = WaterState(),
        config: TankConfig = TankConfig(seed = seed),
    ) = Simulation(config.copy(seed = seed), catalog, rock, seed, startHour * 3600, water, livestock)

    fun seed(id: String) = DefaultStock.all.first { it.id == id }
}
