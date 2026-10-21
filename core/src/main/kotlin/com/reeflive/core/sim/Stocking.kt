package com.reeflive.core.sim

import com.reeflive.core.geom.Vec3
import kotlin.math.max
import kotlin.math.min

/** Gallery groups of the sandbox "Add life" tab. */
enum class StockGroup(val label: String) { FISH("Fish"), INVERTS("Inverts"), CORALS("Corals") }

/**
 * Size of the animals one "Add life" tap releases. Shops sell most reef fish as juveniles, so that is the default:
 * a juvenile blue tang is ~5-8 cm, not the 25 cm adult. Fraction of the species' typical adult length, floored so
 * tiny species still read on screen (never above the adult size).
 */
enum class StockSize(val label: String, val fraction: Double, val floorCm: Double) {
    JUVENILE("Juvenile", 0.3, 3.0), YOUNG("Young", 0.6, 4.0), ADULT("Adult", 1.0, 0.0);

    /** Length in cm for a species whose typical adult is [adultCm]. */
    fun lengthCm(adultCm: Double): Double = max(adultCm * fraction, min(floorCm, adultCm))
}

/**
 * One species the sandbox can add: it has a species sheet and a 3D model the renderer draws. [role] sells one sex
 * form as its own item (a male fairy wrasse is a different fish in the shop); its [key] is "speciesId@male", which
 * every add path takes in place of the species id ([Stocking.speciesOf], [Stocking.roleOf]).
 */
data class StockEntry(val speciesId: String, val group: StockGroup, val common: String, val typicalCm: Double, val role: String? = null) {
    val key: String = if (role == null) speciesId else "$speciesId@${role.lowercase()}"
}

/**
 * Sandbox stocking tests ([Command.AddMany]): add many of one species to see what overstocking does. The new
 * animals are ordinary organisms (brains, stress, aggression, bioload); corals are ordinary colonies (alkalinity
 * demand, biomass, bleaching). Sandbox only: the Live Tank enqueues it on the sandbox sim, never on Reality.
 */
object Stocking {
    /** Most animals one tap can add, and the whole-tank ceiling (phone budget: brains and pair checks scale with n²). */
    const val MAX_ANIMALS_PER_ADD = 50
    const val MAX_ANIMALS = 150
    const val MAX_CORALS_PER_ADD = 30
    const val MAX_CORALS = 80

    /**
     * Every species with a sheet and a bundled model (`species/<id>.json` → `model` in `assets/models`). Macroalgae
     * and pest anemones are left out: their model is several clumps around the tank, so a copy would not read as one.
     */
    val CATALOG: List<StockEntry> = listOf(
        StockEntry("amphiprion_ocellaris", StockGroup.FISH, "Ocellaris clownfish", 8.0),
        StockEntry("paracanthurus_hepatus", StockGroup.FISH, "Blue tang", 25.0),
        StockEntry("sphaeramia_nematoptera", StockGroup.FISH, "Pajama cardinalfish", 7.0),
        StockEntry("labroides_dimidiatus", StockGroup.FISH, "Bluestreak cleaner wrasse", 10.0),
        StockEntry("pholidichthys_leucotaenia", StockGroup.FISH, "Engineer goby", 35.0),
        StockEntry("cirrhitichthys_falco", StockGroup.FISH, "Falco hawkfish", 6.5),
        StockEntry("salarias_fasciatus", StockGroup.FISH, "Lawnmower blenny", 11.0),
        StockEntry("ecsenius_stigmatura", StockGroup.FISH, "Tailspot blenny", 5.5),
        // research 50: two more Ecsenius; the bicolor grazes round its hole, the midas picks plankton up in the water
        StockEntry("ecsenius_bicolor", StockGroup.FISH, "Bicolor blenny", 9.0),
        StockEntry("ecsenius_midas", StockGroup.FISH, "Midas blenny", 11.0),
        StockEntry("pseudochromis_indigo", StockGroup.FISH, "Indigo dottyback", 6.5),
        // research 16: sand sifter (in the owner's tank since 2026-10)
        StockEntry("valenciennea_puellaris", StockGroup.FISH, "Diamond goby", 12.0),
        StockEntry("macropharyngodon_bipartitus", StockGroup.FISH, "Blue star leopard wrasse", 10.0),
        // research 42: Zebrasoma grazers (they fight each other) and a harem plankton picker sold by sex
        StockEntry("zebrasoma_flavescens", StockGroup.FISH, "Yellow tang", 18.0),
        StockEntry("zebrasoma_xanthurum", StockGroup.FISH, "Purple tang", 20.0),
        StockEntry("cirrhilabrus_rubripinnis", StockGroup.FISH, "Red fairy wrasse (female)", 8.0, role = "FEMALE"),
        StockEntry("cirrhilabrus_rubripinnis", StockGroup.FISH, "Red fairy wrasse (male)", 8.0, role = "MALE"),
        // research 41: firefish (needs a lid) and a blue-green chromis shoal
        StockEntry("nemateleotris_magnifica", StockGroup.FISH, "Firefish goby", 7.0),
        StockEntry("chromis_viridis", StockGroup.FISH, "Blue-green chromis", 7.5),
        // research 50: popular reef fish pack, batch 1
        StockEntry("gramma_loreto", StockGroup.FISH, "Royal gramma", 6.5),
        StockEntry("pterapogon_kauderni", StockGroup.FISH, "Banggai cardinalfish", 6.5),
        StockEntry("cryptocentrus_cinctus", StockGroup.FISH, "Yellow watchman goby", 7.5),
        StockEntry("stenopus_hispidus", StockGroup.INVERTS, "Coral banded shrimp", 5.5),
        StockEntry("lysmata_amboinensis", StockGroup.INVERTS, "Skunk cleaner shrimp", 6.5),
        // research 42: hides by day, eats aiptasia at night
        StockEntry("lysmata_boggessi", StockGroup.INVERTS, "Peppermint shrimp", 4.0),
        StockEntry("clibanarius_tricolor", StockGroup.INVERTS, "Blue-leg hermit crab", 1.8),
        StockEntry("paguristes_cadenati", StockGroup.INVERTS, "Scarlet hermit crab", 3.0),
        StockEntry("percnon_gibbesi", StockGroup.INVERTS, "Sally Lightfoot crab", 3.0),
        // research 42: the bristleworm hunter
        StockEntry("stenorhynchus_seticornis", StockGroup.INVERTS, "Arrow crab", 4.5),
        // research 43 §1: bubble algae grazer, hides by day
        StockEntry("mithraculus_sculptus", StockGroup.INVERTS, "Emerald crab", 3.0),
        StockEntry("lysmata_debelius", StockGroup.INVERTS, "Fire shrimp", 3.5),
        StockEntry("thor_amboinensis", StockGroup.INVERTS, "Sexy shrimp", 1.5),
        StockEntry("hymenocera_picta", StockGroup.INVERTS, "Harlequin shrimp", 4.0),
        StockEntry("alpheus_bellulus", StockGroup.INVERTS, "Tiger pistol shrimp", 4.0),
        StockEntry("ophiocoma_sp", StockGroup.INVERTS, "Black brittle star", 20.0),
        StockEntry("sabellastarte_sp", StockGroup.INVERTS, "Brown feather duster", 15.0),
        StockEntry("nassarius_sp", StockGroup.INVERTS, "Nassarius snail", 1.5),
        StockEntry("lithopoma_tectum", StockGroup.INVERTS, "Astrea snail", 3.5),
        StockEntry("asterina_sp", StockGroup.INVERTS, "Asterina starfish", 1.2),
        // cleanup crew pack (research 43): a glass grazer that rights itself, a leaping sand conch, a debris-wearing urchin
        StockEntry("tectus_fenestratus", StockGroup.INVERTS, "Trochus snail", 3.0),
        StockEntry("strombus_alatus", StockGroup.INVERTS, "Fighting conch", 7.0),
        StockEntry("mespilia_globulus", StockGroup.INVERTS, "Tuxedo urchin", 5.0),
        // not in the owner's tank: the closest thing to a reef squid, to see what it does with this stock (research cephalopod_squid)
        StockEntry("euprymna_berryi", StockGroup.INVERTS, "Hummingbird bobtail squid", 6.0),
        // research 40: walks until it finds a crevice, stings corals in reach, splits, and clowns may move in
        StockEntry("entacmaea_quadricolor", StockGroup.INVERTS, "Rainbow bubble tip anemone", 20.0),
        // research 42: a file clam for a crevice; starves without daily phytoplankton
        StockEntry("ctenoides_scaber", StockGroup.INVERTS, "Flame scallop", 6.0),
        StockEntry("tridacna_maxima", StockGroup.INVERTS, "Maxima clam", 15.0),
        StockEntry("ophiarachna_incrassata", StockGroup.INVERTS, "Green serpent star", 35.0),
        StockEntry("neopetrolisthes_maculatus", StockGroup.INVERTS, "Porcelain crab", 1.8),
        StockEntry("holothuria_hilla", StockGroup.INVERTS, "Tiger tail sea cucumber", 25.0),
        StockEntry("capnella_sp", StockGroup.CORALS, "Kenya tree coral", 15.0),
        StockEntry("briareum_sp", StockGroup.CORALS, "Green star polyps", 15.0),
        StockEntry("zoanthus_sp", StockGroup.CORALS, "Zoanthids", 10.0),
        StockEntry("clavularia_sp", StockGroup.CORALS, "Clove polyps", 8.0),
        StockEntry("discosoma_sp", StockGroup.CORALS, "Green mushroom", 5.0),
        StockEntry("rhodactis_sp", StockGroup.CORALS, "Hairy green mushroom", 6.0),
        StockEntry("tubipora_musica", StockGroup.CORALS, "Organ pipe coral", 15.0),
        // Tee's photos 2026-10-13 (docs/research/video/tee_tank_photos.md)
        StockEntry("sarcophyton_sp", StockGroup.CORALS, "Toadstool leather", 12.0),
        StockEntry("lobophytum_sp", StockGroup.CORALS, "Pink lobed leather", 10.0),
        StockEntry("sinularia_sp", StockGroup.CORALS, "Finger leather", 12.0),
        StockEntry("amplexidiscus_fenestrafer", StockGroup.CORALS, "Elephant ear mushroom", 13.0),
        // stony corals (docs/research/stony_corals.md): not in Tee's tank, models built about StonyCorals.STUDIO
        StockEntry("fimbriaphyllia_ancora", StockGroup.CORALS, "Hammer coral", 12.0),
        StockEntry("euphyllia_glabrescens", StockGroup.CORALS, "Torch coral", 15.0),
        StockEntry("fimbriaphyllia_divisa", StockGroup.CORALS, "Frogspawn coral", 12.0),
        StockEntry("duncanopsammia_axifuga", StockGroup.CORALS, "Duncan coral", 8.0),
    )

    /** The entry for a stock key ("speciesId" or "speciesId@male"); a bare species id finds its first entry. */
    fun entry(key: String): StockEntry? = CATALOG.firstOrNull { it.key == key } ?: CATALOG.firstOrNull { it.speciesId == key }

    /** Species id of a stock key. */
    fun speciesOf(key: String): String = key.substringBefore('@')

    /** Sex role a stock key sells ("MALE"), or null for any. */
    fun roleOf(key: String): String? = key.substringAfter('@', "").uppercase().ifEmpty { null }

    /** Shop size for [role]: a sold male is a grown fish (only mature fish show male colors). */
    fun sizeFor(role: String?, size: StockSize): StockSize = if (role == "MALE" && size == StockSize.JUVENILE) StockSize.ADULT else size
    fun inGroup(g: StockGroup): List<StockEntry> = CATALOG.filter { it.group == g }

    /**
     * Anchor each coral model is built around (the GLB's root `anchor`), so a copy is the model moved from there.
     * Later names are fallbacks for older anchor sets.
     */
    private val HOME_ANCHORS: Map<CoralKind, List<String>> = mapOf(
        CoralKind.KENYA to listOf("coral_kenya_main"),
        CoralKind.GSP to listOf("coral_gsp_lower_right", "coral_gsp_upper_left"),
        CoralKind.ZOA to listOf("coral_zoa_front_center"),
        CoralKind.CLOVE to listOf("coral_clove_kenya_front", "coral_clove_kenya_base"),
        CoralKind.MUSHROOM to listOf("coral_shroom_center", "coral_shroom_front_left"),
        CoralKind.ORGAN_PIPE to listOf("coral_organpipe_kenya_base"),
        CoralKind.LEATHER to listOf("coral_toadstool_front", "coral_finger_leather", "coral_leather_pink_front"),
    )

    /** Same fallback spot as [Simulation.addFromSeed] when a coral has no anchor. */
    private val NO_ANCHOR = StonyCorals.STUDIO

    /** Clear space around a new colony (cm), by how wide the model is. */
    fun spacingCm(k: CoralKind): Double = when (k) {
        CoralKind.KENYA -> 16.0
        // Euphyllia: retail spacing 6-10 in so the sweepers do not reach (stony_corals.md §1-3)
        CoralKind.EUPHYLLIA -> 15.0
        CoralKind.GSP, CoralKind.LEATHER -> 12.0
        CoralKind.LPS -> 10.0
        CoralKind.ZOA, CoralKind.ORGAN_PIPE, CoralKind.CLOVE -> 8.0
        else -> 6.0
    }

    /** The anchor a coral species' model sits on, or null (no anchored model in this rockscape). */
    /** Species with a model of their own built around a different anchor than their kind's. */
    private val SPECIES_HOME_ANCHORS: Map<String, List<String>> = mapOf(
        "rhodactis_sp" to listOf("coral_shroom_hairy", "coral_gsp_lower_right"),
        "sarcophyton_sp" to listOf("coral_toadstool_front"),
        "lobophytum_sp" to listOf("coral_leather_pink_front"),
        "sinularia_sp" to listOf("coral_finger_leather"),
        "amplexidiscus_fenestrafer" to listOf("coral_elephant_ear"),
    )

    fun homeAnchor(rock: RockData, sp: SimSpecies): Anchor? = if (sp.coralKind.stony) null else
        SPECIES_HOME_ANCHORS[sp.id].orEmpty().firstNotNullOfOrNull { rock.anchors[it] }
            ?: HOME_ANCHORS[sp.coralKind].orEmpty().firstNotNullOfOrNull { rock.anchors[it] }
            ?: rock.anchors.ofType("CORAL").firstOrNull { it.name.contains(sp.coralKind.name.lowercase().take(4)) }

    /** Adds up to [count] of stock [key] (a species id, or "id@male"); returns how many were added (caps: per add and whole tank). */
    fun addMany(sim: Simulation, key: String, count: Int, sizeCm: Double = 0.0, size: StockSize = StockSize.JUVENILE): Int {
        val speciesId = speciesOf(key)
        val role = roleOf(key)
        sim.species.ensure(speciesId)
        val sp = sim.species[speciesId]
        if (sp.isCoral) return addCorals(sim, sp, count)
        val cm = if (sizeCm > 0) sizeCm else sizeFor(role, size).lengthCm(sp.typicalAdultCm.coerceAtMost(sp.maxLengthCm))
        return addAnimals(sim, sp, count, cm, role)
    }

    private fun nextNumber(existing: Int, i: Int) = existing + i + 1

    private fun addAnimals(sim: Simulation, sp: SimSpecies, count: Int, sizeCm: Double, role: String?): Int {
        val room = MAX_ANIMALS - sim.organisms.count { it.alive }
        val n = min(count.coerceIn(0, MAX_ANIMALS_PER_ADD), max(0, room))
        if (n == 0) return 0
        val rock = sim.rock
        val rng = sim.rng
        val already = sim.organisms.count { it.species.id == sp.id }
        val margin = 12.0
        for (i in 0 until n) {
            val o = sim.addFromSeed(LivestockSeed(sp.id, "${sp.common.ifBlank { sp.id }} #${nextNumber(already, i)}", sizeCm, role))
            // newcomers: nobody knows them yet (06 §3.3 priority effects)
            o.arrivalTime = sim.simTimeSec; o.clock = sim.simTimeSec
            if (o.isFish) {
                // released from acclimation cups spread along the surface, not one pile in the middle
                val x = margin + (i + 0.5 + rng.signed() * 0.4) / n * (rock.lengthCm - 2 * margin)
                val p = Vec3(x, rock.waterLevelCm - 4 - rng.nextDouble() * 4, rock.widthCm * (0.35 + 0.4 * rng.nextDouble()))
                o.placeAt(rock.clampToWater(p, 3.0), Vec3(rng.signed(), 0.0, rng.signed()))
            } else if (!o.brainKind.isSessile && n > 1) {
                // crawlers and walkers start scattered over the rock and sand (their brain takes it from there)
                surfaceSpot(rock, rng.range(8.0, rock.lengthCm - 8.0), rng.range(6.0, rock.widthCm - 4.0))?.let { (p, _) ->
                    val at = p + Vec3(0.0, 0.3, 0.0)
                    o.homePos = at
                    o.placeAt(at, Vec3(rng.signed(), 0.0, rng.signed()).normalized(Vec3.X))
                }
            }
        }
        return n
    }

    private fun addCorals(sim: Simulation, sp: SimSpecies, count: Int): Int {
        val room = MAX_CORALS - sim.corals.size
        val want = min(count.coerceIn(0, MAX_CORALS_PER_ADD), max(0, room))
        if (want == 0) return 0
        val spots = coralSpots(sim.rock, sim.corals.map { it.pos }, want, sim.rng, spacingCm(sp.coralKind))
        for ((p, _) in spots) placeCoral(sim, sp, p)
        return spots.size
    }

    /**
     * One colony of [sp] at [p] on the rock or sand: its species' model moved there from the anchor it was built
     * around (the keeper's pick, [CoralPicks], or a free spot from [coralSpots]). Returns its id.
     */
    fun placeCoral(sim: Simulation, sp: SimSpecies, p: Vec3): String {
        val home = homeAnchor(sim.rock, sp)
        val homePos = home?.pos ?: NO_ANCHOR
        val already = sim.corals.count { it.species.id == sp.id }
        sim.addFromSeed(
            LivestockSeed(sp.id, "${sp.common.ifBlank { sp.id }} #${nextNumber(already, 0)}", 0.0, placement = home?.name, offset = p - homePos),
        )
        return sim.corals.last().id
    }

    /**
     * Top surface of rock or sand under (x, z): sphere-traced down from just under the water line. Returns the point
     * and its outward normal.
     */
    fun surfaceSpot(rock: RockData, x: Double, z: Double): Pair<Vec3, Vec3>? {
        if (x !in 0.0..rock.lengthCm || z !in 0.0..rock.widthCm) return null
        var y = rock.waterLevelCm - 2
        while (y > rock.sandTopCm) {
            val d = rock.sdf.dist(Vec3(x, y, z))
            if (d < 0.25) {
                val p = rock.sdf.project(Vec3(x, y, z))
                return p to rock.sdf.gradient(p)
            }
            y -= max(0.3, d * 0.9)
        }
        return Vec3(x, rock.sandTopCm, z) to Vec3.Y
    }

    /**
     * Up to [count] free spots for new colonies: upward-facing rock tops or open sand, away from the glass, with
     * room above to grow, and at least [spacing] from [taken] colonies and each other.
     */
    fun coralSpots(rock: RockData, taken: List<Vec3>, count: Int, rng: com.reeflive.core.geom.Rng, spacing: Double): List<Pair<Vec3, Vec3>> {
        val out = ArrayList<Pair<Vec3, Vec3>>()
        val used = ArrayList(taken)
        var gap = spacing
        // relax the spacing a little if the tank is full before giving up
        for (round in 0 until 3) {
            var tries = 0
            while (out.size < count && tries < 400) {
                tries++
                val x = rng.range(7.0, rock.lengthCm - 7.0)
                val z = rng.range(5.0, rock.widthCm - 3.0)
                val (p, n) = surfaceSpot(rock, x, z) ?: continue
                if (n.y < 0.6) continue                                   // too steep: it would hang off the rock
                if (p.y > rock.waterLevelCm - 15) continue                // no room to grow
                if (used.any { it.distanceTo(p) < gap }) continue
                out += p to n; used += p
            }
            if (out.size >= count) break
            gap *= 0.7
        }
        return out
    }
}
