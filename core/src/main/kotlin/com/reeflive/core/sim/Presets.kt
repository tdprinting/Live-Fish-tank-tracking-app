package com.reeflive.core.sim

/**
 * Tee's current livestock (DESIGN §9 plus the coordinator's livestock update) as seeds, used when
 * the app has no livestock repository yet and by tests/SimRunner. Species ids follow the
 * `genus_species` convention so `BrainKind`/`CoralKind` resolve from the genus.
 */
object DefaultStock {
    val fish = listOf(
        LivestockSeed("amphiprion_ocellaris", "Nemo", 8.0, role = "FEMALE", placement = "HOST_SHROOM", id = "clown_f"),
        LivestockSeed("amphiprion_ocellaris", "Marlin", 6.5, role = "MALE", placement = "HOST_SHROOM", id = "clown_m"),
        LivestockSeed("paracanthurus_hepatus", "Blue", 7.5, id = "tang"),
        LivestockSeed("sphaeramia_nematoptera", "Pajama", 6.0, id = "cardinal"),
        LivestockSeed("labroides_dimidiatus", "Doc", 8.0, id = "wrasse"),
        // Owner correction: the "eel" is a juvenile engineer goby (black/yellow stripes), not a moray.
        LivestockSeed("pholidichthys_leucotaenia", "Stripes", 10.0, id = "goby"),
        LivestockSeed("cirrhitichthys_falco", "Falco", 6.5, id = "hawk"),
        LivestockSeed("salarias_fasciatus", "Mower", 10.0, id = "lawnmower"),
        LivestockSeed("ecsenius_stigmatura", "Peeker", 5.5, id = "tailspot"),
        LivestockSeed("pseudochromis_indigo", "Indigo", 6.0, id = "dottyback"),
        // owner, 2026-10: the two fish that were not in the sim yet (research video fish_diamond_goby, fish_leopard_wrasse)
        LivestockSeed("valenciennea_puellaris", "Diamond", 10.0, id = "diamond_goby"),
        LivestockSeed("macropharyngodon_bipartitus", "Leopard", 8.5, id = "leopard_wrasse"),
    )

    val inverts = listOf(
        LivestockSeed("stenopus_hispidus", "Bandit", 6.0, placement = "SHRIMP_LEDGE", id = "banded"),
        LivestockSeed("lysmata_amboinensis", "Skunk", 5.5, id = "skunk"),
        LivestockSeed("clibanarius_tricolor", "Hermit 1", 2.5, id = "hermit1"),
        LivestockSeed("clibanarius_tricolor", "Hermit 2", 2.2, id = "hermit2"),
        LivestockSeed("clibanarius_tricolor", "Hermit 3", 2.8, id = "hermit3"),
        LivestockSeed("ophiocoma_erinaceus", "Brittle", 15.0, id = "brittle"),
        LivestockSeed("sabellastarte_sp", "Duster", 8.0, id = "duster"),
        LivestockSeed("nassarius_vibex", "Nassarius 1", 1.8, id = "nass1"),
        LivestockSeed("nassarius_vibex", "Nassarius 2", 1.6, id = "nass2"),
        LivestockSeed("nassarius_vibex", "Nassarius 3", 1.9, id = "nass3"),
        LivestockSeed("astraea_tecta", "Astrea 1", 2.5, id = "astrea1"),
        LivestockSeed("astraea_tecta", "Astrea 2", 2.2, id = "astrea2"),
        LivestockSeed("astraea_tecta", "Astrea 3", 2.6, id = "astrea3"),
        LivestockSeed("asterina_sp", "Asterina #1", 1.0, id = "asterina1"),
        LivestockSeed("asterina_sp", "Asterina #2", 0.8, id = "asterina2"),
        LivestockSeed("percnon_gibbesi", "Sally", 5.0, id = "crab"),
    )

    val corals = listOf(
        LivestockSeed("capnella_sp", "Kenya tree", placement = "coral_kenya_main", id = "kenya"),
        LivestockSeed("briareum_sp", "GSP (upper left)", placement = "coral_gsp_upper_left", id = "gsp1"),
        LivestockSeed("briareum_sp", "GSP (lower right)", placement = "coral_gsp_lower_right", id = "gsp2"),
        LivestockSeed("zoanthus_sp", "Zoas", placement = "coral_zoa_front_center", id = "zoa"),
        LivestockSeed("clavularia_sp", "Clove polyps", placement = "coral_clove_kenya_base", id = "clove"),
        LivestockSeed("discosoma_sp", "Mushrooms", placement = "coral_shroom_front_left", id = "shroom"),
        // the clowns' host (owner, 2026-10): hairy green mushrooms on the arch ledge
        LivestockSeed("rhodactis_sp", "Hairy mushrooms", placement = "coral_shroom_hairy", id = "shroom_hairy"),
        LivestockSeed("gracilaria_sp", "Red macro", placement = "coral_macro_back_left", id = "macro"),
        LivestockSeed("tubipora_musica", "Organ pipe", placement = "coral_organpipe_kenya_base", id = "organpipe"),
    )

    val all: List<LivestockSeed> get() = fish + inverts + corals
}

/** A scenario (SIMULATION §7 presets). [setup] runs on the fresh Simulation. */
data class Preset(
    val id: String,
    val label: String,
    val config: TankConfig = TankConfig(),
    val water: WaterState = WaterState(),
    val livestock: List<LivestockSeed> = DefaultStock.all,
    val setup: (Simulation) -> Unit = {},
)

object Presets {
    val realityDefault = Preset("reality_default", "Reality (default)")

    val newTankCycle = Preset(
        "new_tank_cycle", "New tank cycle",
        // fishless cycle: ghost-feed 0.3 g of flake a day into a tank with 2 % of a mature bacterial bed
        config = TankConfig(autoFeed = listOf(FeedTime(9 * 60 + 30, FoodKind.FLAKE, 0.3))),
        // dry rock and sand: silicate leaches out (research 09 A2) and drives the first diatom bloom; no coralline yet
        water = WaterState(bacteriaAob = 0.02, bacteriaNob = 0.02, no3 = 0.0, po4 = 0.0, detritus = 0.0, algaeHair = 0.01,
            coralline = 0.0, silicate = 2.0, siLeach = 0.1, microbes = 0.0),
        livestock = emptyList(),
        // research 01 §3.9 checkpoints: ammonia, nitrite peak, nitrite falling, cycled after 3 days of 0/0
        // dry rock: no pods or worms until live rock or a bottle seeds them (research 10 §3.3)
        setup = { sim -> sim.cycle = CycleTracker(sim.simTimeSec); sim.water.micro.dryRock(); sim.water.bacteria.newTank() },
    )

    val heaterFailureSummer = Preset(
        "heater_failure_summer", "Heater stuck on in summer",
        config = TankConfig(roomC = 30.0),
        setup = { sim -> sim.enqueue(Command.Heater(working = true, stuckOn = true)) },
    )

    val overfeeding = Preset(
        "overfeeding", "Overfeeding (3×)",
        config = TankConfig(autoFeed = TankConfig.DEFAULT_FEEDING.map { it.copy(grams = it.grams * 3) }),
    )

    val powerheadFailure = Preset(
        "powerhead_failure", "Powerheads off",
        setup = { sim -> sim.enqueue(Command.Powerheads(false)) },
    )

    val ichOutbreak = Preset(
        "ich_outbreak", "Ich outbreak",
        setup = { sim ->
            sim.organisms.firstOrNull { it.brainKind == BrainKind.TANG }?.let {
                it.disease = Disease.ICH; it.diseaseStage = 0.2; it.immunity = 0.6
            }
        },
    )

    val all = listOf(realityDefault, newTankCycle, heaterFailureSummer, overfeeding, powerheadFailure, ichOutbreak)

    fun byId(id: String) = all.firstOrNull { it.id == id } ?: realityDefault

    /** Build a ready Simulation for a preset. */
    fun create(
        p: Preset,
        species: SpeciesCatalog = SpeciesCatalog(),
        rock: RockData = RockData.defaultTank(),
        seed: Long = 1L,
        startTimeSec: Double = 12 * 3600.0,
    ): Simulation {
        val sim = Simulation(p.config.copy(seed = seed), species, rock, seed, startTimeSec, p.water.copy(), p.livestock)
        p.setup(sim)
        return sim
    }
}
