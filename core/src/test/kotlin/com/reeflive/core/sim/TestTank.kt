package com.reeflive.core.sim

/** Shared fixtures for the simulation tests: species sheets from test resources + the analytic rockscape. */
object TestTank {
    val catalog: SpeciesCatalog by lazy {
        SpeciesCatalog.fromClasspath(DefaultStock.all.map { it.speciesId }.distinct(), "sim-fixtures/species/")
    }
    val rock: RockData by lazy { RockData.defaultTank() }

    fun sim(
        livestock: List<LivestockSeed>,
        seed: Long = 1L,
        startHour: Double = 12.0,
        water: WaterState = WaterState(),
        config: TankConfig = TankConfig(seed = seed),
    ) = Simulation(config.copy(seed = seed), catalog, rock, seed, startHour * 3600, water, livestock)

    fun seed(id: String) = DefaultStock.all.first { it.id == id }
}
