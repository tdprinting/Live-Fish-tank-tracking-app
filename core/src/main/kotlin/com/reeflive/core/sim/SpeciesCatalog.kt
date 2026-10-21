package com.reeflive.core.sim

/**
 * Species lookup used by the simulation. Production sheets come from Track A
 * (`species/<id>.json` on the core classpath); [fallback] synthesizes a plausible sheet from the
 * brain archetype when a sheet is missing (e.g. before Track A's sheets are merged), so the Live
 * Tank never shows an empty tank. Tests use JSON fixtures in `src/test/resources/sim-fixtures`.
 */
class SpeciesCatalog(sheets: Collection<SimSpecies> = emptyList()) {
    private val map = LinkedHashMap<String, SimSpecies>().apply { sheets.forEach { put(it.id, it) } }

    fun put(s: SimSpecies) { map[s.id] = s }
    fun has(id: String) = map.containsKey(id)
    fun all(): Collection<SimSpecies> = map.values

    private val synthesized = HashSet<String>()

    /** The sheet for [id], or a synthesized fallback (never null). */
    operator fun get(id: String): SimSpecies = map[id] ?: fallback(id).also { map[id] = it; synthesized += id }

    /**
     * Makes sure the real sheet for [id] is loaded (from `species/<id>.json` on the classpath) when it is missing or
     * was synthesized; the sandbox can add any species, not only the ones the tank was built with.
     */
    fun ensure(id: String, prefix: String = "species/", loader: ClassLoader? = SpeciesCatalog::class.java.classLoader) {
        if (map.containsKey(id) && id !in synthesized) return
        val text = runCatching { loader?.getResourceAsStream("$prefix$id.json")?.bufferedReader()?.use { it.readText() } }.getOrNull() ?: return
        runCatching { SimSpecies.parse(text) }.getOrNull()?.let { put(it); synthesized -= id }
    }

    companion object {
        /** Loads `species/<id>.json` resources for [ids] from the classpath (missing ones skipped). */
        fun fromClasspath(ids: Collection<String>, prefix: String = "species/", loader: ClassLoader? = SpeciesCatalog::class.java.classLoader): SpeciesCatalog {
            val c = SpeciesCatalog()
            for (id in ids) {
                val text = loader?.getResourceAsStream("$prefix$id.json")?.bufferedReader()?.use { it.readText() } ?: continue
                runCatching { SimSpecies.parse(text) }.getOrNull()?.let { c.put(it) }
            }
            return c
        }

        fun fromJsonTexts(texts: Collection<String>) = SpeciesCatalog(texts.map { SimSpecies.parse(it) })

        private val reefFishTol = mapOf(
            "temp" to ToleranceRange(24.0, 27.0, 22.0, 28.5, 19.0, 31.0),
            "sg" to ToleranceRange(1.022, 1.026, 1.020, 1.027, 1.016, 1.030),
            "ph" to ToleranceRange(8.0, 8.4, 7.9, 8.5, 7.5, 8.9),
            "nh3Free" to ToleranceRange(optHi = 0.0, tolHi = 0.01, lethalHi = 0.05),
            "no2" to ToleranceRange(optHi = 0.0, tolHi = 0.15, lethalHi = 1.0),
            "no3" to ToleranceRange(optHi = 20.0, tolHi = 35.0, lethalHi = 80.0),
            "do" to ToleranceRange(optLo = 6.0, tolLo = 5.0, lethalLo = 2.5),
        )

        /** Archetype defaults (values from research 02/04; see fixtures for the full sheets). */
        fun fallback(id: String): SimSpecies {
            val probe = SimSpecies(id = id, kind = if (CoralKind.resolve(id) != CoralKind.GENERIC) "CORAL" else "FISH")
            if (probe.isCoral) return probe.copy(kind = "CORAL", common = id, locomotion = Locomotion(mode = "SESSILE", cruiseBL = 0.0, burstBL = 0.0), tolerance = reefFishTol)
            val base = SimSpecies(id = id, tolerance = reefFishTol)
            return when (BrainKind.byId(id) ?: BrainKind.GENERIC_FISH) {
                BrainKind.CLOWN -> base.copy(common = "Ocellaris clownfish", maxLengthCm = 11.0, typicalAdultCm = 8.0, depthRatio = 0.42,
                    locomotion = Locomotion("LABRIFORM", 1.4, 5.0, 3.0, 260.0, 0.9, 0.035, 0.08), zone = Zone(0.2, 0.75, 0.3, 0.6),
                    temperament = Temperament(0.5, 0.6, 0.6, 0.4), diet = listOf("FLAKE", "MYSIS", "PELLET", "NORI"), feedsPerDay = 2.0,
                    homeRadiusCm = 20.0, hardiness = 0.8, diseaseSusceptibility = mapOf("ICH" to 0.4), iridescence = 0.12, model = "models/clown_orange.glb")
                BrainKind.TANG -> base.copy(common = "Blue tang", maxLengthCm = 31.0, typicalAdultCm = 25.0, depthRatio = 0.52, massCoeff = 0.025,
                    locomotion = Locomotion("LABRIFORM", 1.5, 7.0, 4.0, 240.0, 0.9, 0.04, 0.09), zone = Zone(0.25, 0.95, 0.8, 0.3),
                    temperament = Temperament(0.3, 0.6, 0.7, 0.5), diet = listOf("ALGAE", "NORI", "MYSIS", "FLAKE", "PELLET"), feedsPerDay = 4.0,
                    grazer = true, homeRadiusCm = 60.0, hardiness = 0.45, diseaseSusceptibility = mapOf("ICH" to 1.0, "HLLE" to 0.6, "BACTERIAL" to 0.4), iridescence = 0.35, model = "models/tang.glb")
                BrainKind.CARDINAL -> base.copy(common = "Pajama cardinalfish", maxLengthCm = 8.0, typicalAdultCm = 6.0, depthRatio = 0.46, activity = "CREPUSCULAR",
                    locomotion = Locomotion("LABRIFORM", 0.8, 4.0, 2.0, 180.0, 0.9, 0.03, 0.08), zone = Zone(0.3, 0.6, 0.4, 0.6),
                    temperament = Temperament(0.0, 0.3, 0.2, 0.8), diet = listOf("MYSIS", "FLAKE", "PELLET", "PODS"), hardiness = 0.8, homeRadiusCm = 25.0, model = "models/cardinal.glb")
                BrainKind.WRASSE -> base.copy(common = "Bluestreak cleaner wrasse", maxLengthCm = 14.0, depthRatio = 0.21, massCoeff = 0.012,
                    locomotion = Locomotion("LABRIFORM", 1.6, 6.0, 5.0, 300.0, 0.8, 0.05, 0.1), zone = Zone(0.1, 0.55, 0.3, 0.6),
                    temperament = Temperament(0.1, 0.8, 0.8, 0.3), diet = listOf("PARASITES", "PODS", "FLAKE", "MYSIS"), feedsPerDay = 3.0,
                    hardiness = 0.5, starvationDays = 28.0, iridescence = 0.5, homeRadiusCm = 30.0, model = "models/wrasse.glb")
                BrainKind.EEL -> base.copy(common = "Snowflake moray", maxLengthCm = 80.0, typicalAdultCm = 60.0, depthRatio = 0.05, massCoeff = 0.0022, activity = "NOCTURNAL",
                    locomotion = Locomotion("ANGUILLIFORM", 0.5, 2.5, 1.0, 120.0, 0.6, 0.06, 0.1), zone = Zone(0.0, 0.35, 0.1, 0.9),
                    temperament = Temperament(0.4, 0.4, 0.5, 0.3), diet = listOf("MYSIS", "PELLET", "MEATY"), feedsPerDay = 0.33,
                    hardiness = 0.85, starvationDays = 60.0, iridescence = 0.05, homeRadiusCm = 50.0, model = "models/eel.glb")
                BrainKind.ENGINEER_GOBY -> base.copy(common = "Engineer goby", maxLengthCm = 30.0, typicalAdultCm = 20.0, depthRatio = 0.08, massCoeff = 0.004,
                    locomotion = Locomotion("ANGUILLIFORM", 0.8, 4.0, 1.6, 160.0, 0.7, 0.06, 0.1), zone = Zone(0.0, 0.25, 0.2, 0.9),
                    temperament = Temperament(0.05, 0.25, 0.4, 0.75), diet = listOf("PODS", "MYSIS", "FLAKE", "PELLET"), feedsPerDay = 3.0,
                    hardiness = 0.85, homeRadiusCm = 15.0, iridescence = 0.05, model = "models/engineer_goby.glb")
                BrainKind.HAWK -> base.copy(common = "Falco hawkfish", maxLengthCm = 8.0, depthRatio = 0.4,
                    locomotion = Locomotion("LABRIFORM", 0.4, 3.0, 2.0, 200.0, 0.9, 0.03, 0.08), zone = Zone(0.05, 0.6, 0.1, 0.8),
                    temperament = Temperament(0.5, 0.7, 0.5, 0.3), diet = listOf("MYSIS", "PELLET", "FLAKE"), hardiness = 0.6, homeRadiusCm = 40.0, model = "models/hawkfish.glb")
                BrainKind.LAWNMOWER_BLENNY -> base.copy(common = "Lawnmower blenny", maxLengthCm = 13.0, typicalAdultCm = 11.0, depthRatio = 0.18,
                    locomotion = Locomotion("LABRIFORM", 0.7, 4.0, 2.0, 220.0, 0.9, 0.04, 0.09), zone = Zone(0.0, 0.5, 0.05, 0.9),
                    temperament = Temperament(0.5, 0.6, 0.4, 0.5), diet = listOf("ALGAE", "NORI"), feedsPerDay = 6.0, grazer = true,
                    hardiness = 0.8, starvationDays = 35.0, homeRadiusCm = 30.0, model = "models/lawnmower_blenny.glb")
                BrainKind.PEEKER_BLENNY -> base.copy(common = "Tailspot blenny", maxLengthCm = 6.4, depthRatio = 0.18,
                    locomotion = Locomotion("LABRIFORM", 0.8, 4.0, 2.5, 240.0, 0.9, 0.04, 0.09), zone = Zone(0.1, 0.6, 0.15, 0.85),
                    temperament = Temperament(0.4, 0.4, 0.5, 0.7), diet = listOf("ALGAE", "FLAKE", "MYSIS", "NORI", "PODS"), feedsPerDay = 4.0, grazer = true,
                    hardiness = 0.7, homeRadiusCm = 25.0, model = "models/tailspot_blenny.glb")
                BrainKind.DOTTYBACK -> base.copy(common = "Indigo dottyback", maxLengthCm = 7.6, depthRatio = 0.32,
                    locomotion = Locomotion("LABRIFORM", 1.0, 5.0, 3.0, 300.0, 0.9, 0.04, 0.09), zone = Zone(0.05, 0.5, 0.15, 0.85),
                    temperament = Temperament(0.55, 0.5, 0.4, 0.6), diet = listOf("MYSIS", "PELLET", "FLAKE"), hardiness = 0.8, homeRadiusCm = 20.0, model = "models/dottyback.glb")
                BrainKind.BANDED_SHRIMP -> base.copy(kind = "INVERT", common = "Banded coral shrimp", maxLengthCm = 6.3, depthRatio = 0.22, activity = "NOCTURNAL",
                    locomotion = Locomotion("WALKING", 0.15, 8.0, 0.0, 120.0), diet = listOf("FLAKE", "MYSIS", "PELLET"), feedsPerDay = 1.0,
                    temperament = Temperament(0.3, 0.4, 0.3, 0.6), hardiness = 0.7, homeRadiusCm = 25.0, model = "models/shrimp.glb")
                BrainKind.CLEANER_SHRIMP -> base.copy(kind = "INVERT", common = "Skunk cleaner shrimp", maxLengthCm = 7.5, depthRatio = 0.2,
                    locomotion = Locomotion("WALKING", 0.18, 8.0, 0.0, 140.0), diet = listOf("PARASITES", "FLAKE", "MYSIS", "PELLET"), feedsPerDay = 1.0,
                    temperament = Temperament(0.0, 0.8, 0.7, 0.4), hardiness = 0.7, homeRadiusCm = 20.0, model = "models/skunk_shrimp.glb")
                BrainKind.PEPPERMINT_SHRIMP -> base.copy(kind = "INVERT", common = "Peppermint shrimp", maxLengthCm = 5.0, typicalAdultCm = 4.0, depthRatio = 0.2,
                    activity = "NOCTURNAL", locomotion = Locomotion("WALKING", 0.2, 8.0, 0.0, 160.0), diet = listOf("MYSIS", "BRINE", "PELLET", "FLAKE", "AIPTASIA"),
                    feedsPerDay = 1.0, temperament = Temperament(0.05, 0.3, 0.5, 0.8), hardiness = 0.75, homeRadiusCm = 30.0, brain = "peppermint_shrimp",
                    model = "models/peppermint_shrimp.glb")
                BrainKind.FIRE_SHRIMP -> base.copy(kind = "INVERT", common = "Fire shrimp", maxLengthCm = 5.0, typicalAdultCm = 3.5, depthRatio = 0.2,
                    activity = "NOCTURNAL", locomotion = Locomotion("WALKING", 0.2, 8.0, 0.0, 160.0), diet = listOf("PARASITES", "MYSIS", "BRINE", "PELLET", "FLAKE"),
                    feedsPerDay = 1.0, temperament = Temperament(0.3, 0.25, 0.4, 0.75), hardiness = 0.7, homeRadiusCm = 30.0, brain = "fire_shrimp",
                    model = "models/fire_shrimp.glb")
                BrainKind.SEXY_SHRIMP -> base.copy(kind = "INVERT", common = "Sexy shrimp", maxLengthCm = 2.0, typicalAdultCm = 1.5, depthRatio = 0.2,
                    locomotion = Locomotion("WALKING", 0.3, 10.0, 0.0, 160.0), diet = listOf("MYSIS", "BRINE", "PELLET", "FLAKE"),
                    feedsPerDay = 1.0, temperament = Temperament(0.0, 0.5, 0.5, 0.6), hardiness = 0.7, homeRadiusCm = 12.0, brain = "sexy_shrimp",
                    model = "models/sexy_shrimp.glb")
                BrainKind.HARLEQUIN_SHRIMP -> base.copy(kind = "INVERT", common = "Harlequin shrimp", maxLengthCm = 5.0, typicalAdultCm = 4.0, depthRatio = 0.2,
                    activity = "NOCTURNAL", locomotion = Locomotion("WALKING", 0.12, 4.0, 0.0, 110.0), diet = listOf("STARFISH"),
                    feedsPerDay = 0.2, temperament = Temperament(0.1, 0.35, 0.4, 0.6), hardiness = 0.5, homeRadiusCm = 30.0, brain = "harlequin_shrimp",
                    model = "models/harlequin_shrimp.glb")
                BrainKind.PISTOL_SHRIMP -> base.copy(kind = "INVERT", common = "Tiger pistol shrimp", maxLengthCm = 5.0, typicalAdultCm = 4.0, depthRatio = 0.2,
                    activity = "NOCTURNAL", locomotion = Locomotion("WALKING", 0.15, 6.0, 0.0, 160.0), diet = listOf("DETRITUS", "MYSIS", "BRINE", "PELLET"),
                    feedsPerDay = 1.0, temperament = Temperament(0.4, 0.3, 0.3, 0.85), hardiness = 0.75, homeRadiusCm = 15.0, brain = "pistol_shrimp",
                    model = "models/pistol_shrimp.glb")
                BrainKind.FILE_CLAM -> base.copy(kind = "INVERT", common = "Flame scallop", maxLengthCm = 7.5, typicalAdultCm = 6.0, depthRatio = 0.38, massCoeff = 0.06,
                    locomotion = Locomotion("SESSILE", 0.0, 2.0, 0.0, 0.0), diet = listOf("PHYTOPLANKTON", "PLANKTON"), feedsPerDay = 3.0, hardiness = 0.3,
                    starvationDays = 30.0, homeRadiusCm = 10.0, model = "models/flame_scallop.glb", buoyantWhenDead = false)
                BrainKind.GIANT_CLAM -> base.copy(kind = "INVERT", common = "Maxima clam", maxLengthCm = 25.0, typicalAdultCm = 15.0, depthRatio = 0.42, massCoeff = 0.12,
                    locomotion = Locomotion("SESSILE", 0.0, 0.0, 0.0, 0.0), diet = listOf("PHYTOPLANKTON", "PLANKTON"), feedsPerDay = 0.5, hardiness = 0.55,
                    starvationDays = 40.0, homeRadiusCm = 5.0, model = "models/maxima_clam.glb", buoyantWhenDead = false)
                BrainKind.HERMIT -> base.copy(kind = "INVERT", common = "Hermit crab", maxLengthCm = 3.0, depthRatio = 0.8, massCoeff = 0.08, activity = "NOCTURNAL",
                    locomotion = Locomotion("WALKING", 0.15, 1.0, 0.0, 90.0), diet = listOf("ALGAE", "DETRITUS", "FLAKE", "MYSIS", "PELLET"), feedsPerDay = 2.0,
                    grazer = true, hardiness = 0.85, homeRadiusCm = 40.0, model = "models/hermit.glb")
                BrainKind.SALLY_CRAB -> base.copy(kind = "INVERT", common = "Sally Lightfoot crab", maxLengthCm = 4.0, typicalAdultCm = 3.0, depthRatio = 0.25, massCoeff = 0.03,
                    activity = "NOCTURNAL", locomotion = Locomotion("WALKING", 0.6, 8.0, 0.0, 360.0), diet = listOf("ALGAE", "DETRITUS", "FLAKE", "MYSIS", "PELLET"),
                    feedsPerDay = 2.0, grazer = true, temperament = Temperament(0.1, 0.2, 0.4, 0.95), hardiness = 0.6, homeRadiusCm = 40.0, model = "models/sally_lightfoot.glb")
                BrainKind.EMERALD_CRAB -> base.copy(kind = "INVERT", common = "Emerald crab", maxLengthCm = 5.0, typicalAdultCm = 3.0, depthRatio = 0.45, massCoeff = 0.03,
                    activity = "NOCTURNAL", locomotion = Locomotion("WALKING", 0.25, 2.0, 0.0, 120.0), diet = listOf("ALGAE", "MEATY", "MYSIS", "PELLET", "DETRITUS"),
                    feedsPerDay = 1.0, grazer = true, temperament = Temperament(0.25, 0.25, 0.4, 0.7), hardiness = 0.8, homeRadiusCm = 30.0, model = "models/emerald_crab.glb")
                BrainKind.ARROW_CRAB -> base.copy(kind = "INVERT", common = "Arrow crab", maxLengthCm = 6.0, typicalAdultCm = 4.5, depthRatio = 0.6, massCoeff = 0.006,
                    activity = "NOCTURNAL", locomotion = Locomotion("WALKING", 0.25, 1.5, 0.0, 120.0), diet = listOf("MEATY", "MYSIS", "PELLET", "FLAKE", "DETRITUS"),
                    feedsPerDay = 1.0, temperament = Temperament(0.45, 0.55, 0.6, 0.4), hardiness = 0.75, homeRadiusCm = 45.0, model = "models/arrow_crab.glb")
                BrainKind.BRITTLE_STAR -> base.copy(kind = "INVERT", common = "Black brittle star", maxLengthCm = 20.0, depthRatio = 0.1, massCoeff = 0.002, activity = "NOCTURNAL",
                    locomotion = Locomotion("CRAWLING", 0.05, 0.4, 0.0, 60.0), diet = listOf("DETRITUS", "MYSIS", "PELLET", "FLAKE"), feedsPerDay = 1.0,
                    hardiness = 0.8, homeRadiusCm = 30.0, model = "models/brittle_star.glb")
                BrainKind.FEATHER_DUSTER -> base.copy(kind = "INVERT", common = "Brown feather duster", maxLengthCm = 8.0, depthRatio = 1.0, massCoeff = 0.01,
                    locomotion = Locomotion("SESSILE", 0.0, 0.0, 0.0, 0.0), diet = listOf("PODS", "PLANKTON"), feedsPerDay = 2.0, hardiness = 0.5,
                    starvationDays = 40.0, model = "models/feather_duster.glb")
                BrainKind.NASSARIUS -> base.copy(kind = "INVERT", common = "Nassarius snail", maxLengthCm = 2.0, depthRatio = 0.6, massCoeff = 0.15, activity = "NOCTURNAL",
                    locomotion = Locomotion("CRAWLING", 0.15, 0.6, 0.0, 90.0), diet = listOf("MYSIS", "PRAWN", "PELLET", "FLAKE", "DETRITUS"), feedsPerDay = 0.5,
                    hardiness = 0.85, starvationDays = 21.0, homeRadiusCm = 60.0, model = "models/nassarius.glb")
                BrainKind.ASTREA -> base.copy(kind = "INVERT", common = "Astrea snail", maxLengthCm = 4.0, depthRatio = 0.7, massCoeff = 0.15,
                    locomotion = Locomotion("CRAWLING", 0.02, 0.05, 0.0, 30.0), diet = listOf("ALGAE"), grazer = true, feedsPerDay = 4.0,
                    hardiness = 0.8, starvationDays = 21.0, homeRadiusCm = 60.0, model = "models/astrea.glb")
                BrainKind.TROCHUS -> base.copy(kind = "INVERT", common = "Trochus snail", maxLengthCm = 5.0, typicalAdultCm = 3.0, depthRatio = 1.05, massCoeff = 0.05,
                    locomotion = Locomotion("CRAWLING", 0.04, 0.12, 0.0, 60.0), diet = listOf("ALGAE", "DIATOMS"), grazer = true, feedsPerDay = 6.0,
                    hardiness = 0.8, starvationDays = 21.0, homeRadiusCm = 60.0, model = "models/trochus.glb")
                BrainKind.CONCH -> base.copy(kind = "INVERT", common = "Fighting conch", maxLengthCm = 11.0, typicalAdultCm = 7.0, depthRatio = 0.6, massCoeff = 0.08,
                    activity = "NOCTURNAL", locomotion = Locomotion("CRAWLING", 0.07, 1.1, 0.0, 45.0), diet = listOf("ALGAE", "DIATOMS", "DETRITUS", "NORI", "PELLET"),
                    grazer = true, feedsPerDay = 3.0, hardiness = 0.8, starvationDays = 21.0, homeRadiusCm = 40.0, model = "models/fighting_conch.glb")
                BrainKind.URCHIN -> base.copy(kind = "INVERT", common = "Tuxedo urchin", maxLengthCm = 8.0, typicalAdultCm = 5.0, depthRatio = 0.75, massCoeff = 0.12,
                    activity = "NOCTURNAL", locomotion = Locomotion("CRAWLING", 0.01, 0.025, 0.0, 30.0), diet = listOf("ALGAE", "DIATOMS", "NORI"),
                    grazer = true, feedsPerDay = 4.0, hardiness = 0.6, starvationDays = 30.0, homeRadiusCm = 60.0, model = "models/tuxedo_urchin.glb")
                BrainKind.ASTERINA -> base.copy(kind = "INVERT", common = "Asterina starfish", maxLengthCm = 1.5, depthRatio = 0.2, massCoeff = 0.02, activity = "NOCTURNAL",
                    locomotion = Locomotion("CRAWLING", 0.04, 0.08, 0.0, 30.0), diet = listOf("ALGAE", "DETRITUS"), grazer = true, feedsPerDay = 2.0,
                    hardiness = 0.95, homeRadiusCm = 60.0, model = "models/asterina.glb")
                BrainKind.DIAMOND_GOBY -> base.copy(common = "Diamond goby", maxLengthCm = 16.0, typicalAdultCm = 12.0, depthRatio = 0.2, massCoeff = 0.012,
                    locomotion = Locomotion("LABRIFORM", 0.8, 5.0, 2.5, 220.0, 0.85, 0.04, 0.09), zone = Zone(0.0, 0.2, 0.4, 0.6),
                    temperament = Temperament(0.15, 0.4, 0.4, 0.6), diet = listOf("PODS", "MYSIS", "PELLET", "FLAKE"), feedsPerDay = 3.0,
                    hardiness = 0.6, starvationDays = 21.0, homeRadiusCm = 35.0, iridescence = 0.15, model = "models/diamond_goby.glb")
                BrainKind.LEOPARD_WRASSE -> base.copy(common = "Blue star leopard wrasse", maxLengthCm = 13.0, typicalAdultCm = 10.0, depthRatio = 0.36, massCoeff = 0.016,
                    locomotion = Locomotion("LABRIFORM", 0.8, 5.0, 3.0, 260.0, 0.85, 0.03, 0.08), zone = Zone(0.05, 0.55, 0.3, 0.7),
                    temperament = Temperament(0.05, 0.35, 0.5, 0.7), diet = listOf("PODS", "MYSIS", "FLAKE", "PELLET"), feedsPerDay = 3.0,
                    hardiness = 0.4, starvationDays = 18.0, homeRadiusCm = 40.0, iridescence = 0.25, model = "models/leopard_wrasse.glb")
                BrainKind.FAIRY_WRASSE -> base.copy(common = "Red fairy wrasse", maxLengthCm = 9.1, typicalAdultCm = 8.0, depthRatio = 0.28, massCoeff = 0.013,
                    locomotion = Locomotion("LABRIFORM", 1.4, 7.0, 3.5, 300.0, 0.85, 0.03, 0.09), zone = Zone(0.3, 0.85, 0.75, 0.4),
                    temperament = Temperament(0.15, 0.45, 0.5, 0.7), diet = listOf("MYSIS", "CYCLOPS", "ZOOPLANKTON", "FLAKE", "PELLET"), feedsPerDay = 3.0,
                    hardiness = 0.6, starvationDays = 14.0, homeRadiusCm = 45.0, iridescence = 0.3, model = "models/fairy_wrasse.glb")
                BrainKind.FIREFISH -> base.copy(common = "Firefish goby", maxLengthCm = 9.0, typicalAdultCm = 7.0, depthRatio = 0.17, massCoeff = 0.008,
                    lifespanYears = 3.0, locomotion = Locomotion("LABRIFORM", 0.5, 9.0, 2.5, 320.0, 0.9, 0.03, 0.1), zone = Zone(0.05, 0.45, 0.3, 0.85),
                    temperament = Temperament(0.2, 0.3, 0.45, 0.85), diet = listOf("MYSIS", "BRINE", "CYCLOPS", "PODS", "ZOOPLANKTON", "FLAKE"), feedsPerDay = 3.0,
                    hardiness = 0.65, starvationDays = 18.0, homeRadiusCm = 15.0, iridescence = 0.15, jumpRisk = 0.9, model = "models/firefish.glb")
                BrainKind.CHROMIS -> base.copy(common = "Blue-green chromis", maxLengthCm = 10.0, typicalAdultCm = 7.5, depthRatio = 0.46, massCoeff = 0.022,
                    lifespanYears = 10.0, locomotion = Locomotion("LABRIFORM", 1.4, 8.0, 3.5, 300.0, 0.9, 0.035, 0.09), zone = Zone(0.4, 0.85, 0.85, 0.35),
                    schooling = true, temperament = Temperament(0.25, 0.55, 0.6, 0.5), diet = listOf("FLAKE", "MYSIS", "BRINE", "CYCLOPS", "PELLET", "PODS", "ZOOPLANKTON"),
                    feedsPerDay = 3.0, hardiness = 0.6, starvationDays = 16.0, homeRadiusCm = 35.0, iridescence = 0.6, jumpRisk = 0.15, model = "models/chromis.glb")
                BrainKind.BOBTAIL_SQUID -> base.copy(kind = "INVERT", common = "Hummingbird bobtail squid", family = "Sepiolidae", maxLengthCm = 9.0, typicalAdultCm = 6.0,
                    lifespanYears = 0.5, depthRatio = 0.45, massCoeff = 0.09, activity = "NOCTURNAL",
                    locomotion = Locomotion("FIN_JET", 0.6, 8.0, 1.5, 300.0, 1.0, 0.0, 0.0), zone = Zone(0.0, 0.6, 0.6, 0.5),
                    temperament = Temperament(0.1, 0.25, 0.5, 0.8), diet = listOf("PODS", "MYSIS", "KRILL", "PRAWN"), feedsPerDay = 2.0,
                    hardiness = 0.35, starvationDays = 6.0, homeRadiusCm = 40.0, iridescence = 0.45, model = "models/bobtail_squid.glb", buoyantWhenDead = false)
                BrainKind.ANEMONE -> base.copy(kind = "INVERT", common = "Bubble tip anemone", maxLengthCm = 40.0, typicalAdultCm = 20.0, depthRatio = 0.5, massCoeff = 0.02,
                    locomotion = Locomotion("CRAWLING", 0.00005, 0.0002, 0.0, 5.0), diet = listOf("LIGHT", "MYSIS", "KRILL", "PRAWN", "PELLET"), feedsPerDay = 0.3,
                    hardiness = 0.6, starvationDays = 120.0, homeRadiusCm = 15.0, model = "models/bubble_tip_anemone.glb")
                BrainKind.GRAMMA -> base.copy(common = "Royal gramma", maxLengthCm = 8.0, typicalAdultCm = 6.5, depthRatio = 0.3, massCoeff = 0.016,
                    lifespanYears = 6.0, locomotion = Locomotion("LABRIFORM", 0.7, 6.0, 2.5, 320.0, 0.9, 0.035, 0.09), zone = Zone(0.1, 0.6, 0.15, 0.85),
                    temperament = Temperament(0.35, 0.45, 0.45, 0.6), diet = listOf("MYSIS", "BRINE", "CYCLOPS", "PODS", "ZOOPLANKTON", "FLAKE", "PELLET"),
                    hardiness = 0.75, homeRadiusCm = 20.0, iridescence = 0.2, model = "models/royal_gramma.glb")
                BrainKind.WATCHMAN_GOBY -> base.copy(common = "Yellow watchman goby", maxLengthCm = 10.0, typicalAdultCm = 7.5, depthRatio = 0.2, massCoeff = 0.012,
                    locomotion = Locomotion("LABRIFORM", 0.6, 6.0, 2.2, 260.0, 0.9, 0.04, 0.1), zone = Zone(0.0, 0.15, 0.1, 0.8),
                    temperament = Temperament(0.3, 0.45, 0.5, 0.7), diet = listOf("MYSIS", "BRINE", "PODS", "PELLET", "FLAKE", "MEATY"),
                    hardiness = 0.85, starvationDays = 24.0, homeRadiusCm = 15.0, iridescence = 0.15, model = "models/watchman_goby.glb")
                BrainKind.GENERIC_FISH -> base
                BrainKind.GENERIC_INVERT -> base.copy(kind = "INVERT", locomotion = Locomotion("CRAWLING", 0.05, 0.2))
            }
        }
    }
}
