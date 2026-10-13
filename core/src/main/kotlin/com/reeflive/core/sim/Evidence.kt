package com.reeflive.core.sim

/**
 * How well a simulation constant is backed (research 15 "evidence ledger"):
 * - [SOURCED]: a direct study or curated species account of the named taxon gives the number or direction.
 * - [ANALOGY]: the evidence is from another species, genus or setting; the transfer is deliberate and labelled.
 * - [DESIGN_ESTIMATE]: a provisional model choice with no source; [EvidenceEntry.observe] says what would constrain it.
 */
enum class Evidence { SOURCED, ANALOGY, DESIGN_ESTIMATE }

/**
 * Source-only tag for a constant or rule (no runtime cost). [ref] names the research file and section or the
 * study key, e.g. `"12 Stenopus: NTOU thesis 5.6 %"`. The same constants are listed with values in [EvidenceLedger].
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD, AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class Ev(val level: Evidence, val ref: String = "")

/** One ledger row: catalog id, the constant or rule, its value, evidence level, reference and what tank observation would test it. */
data class EvidenceEntry(
    val speciesId: String,
    val name: String,
    val value: String,
    val level: Evidence,
    val ref: String,
    val observe: String = "",
)

/**
 * Evidence labels for the behaviour constants touched by research 10–15 (docs/research/15_master_livestock_research.md,
 * "Research and simulation evidence ledger"). Readable by the app (e.g. to mark a value as a design estimate) and by
 * tests; SIMULATION §14 mirrors it as a table. Values are strings so the ledger never drifts in type from the code.
 */
object EvidenceLedger {
    private val S = Evidence.SOURCED
    private val A = Evidence.ANALOGY
    private val D = Evidence.DESIGN_ESTIMATE

    val entries: List<EvidenceEntry> = listOf(
        // ------------------------------------------------------------ fish
        EvidenceEntry("amphiprion_ocellaris", "colour form", "no behavioural effect", S, "10 §1 Florida Museum", "dominance and host use per individual"),
        EvidenceEntry("paracanthurus_hepatus", "diet emphasis", "column picking > surface grazing", S, "10 §2 FishBase", "share of bites in open water vs on rock"),
        EvidenceEntry("paracanthurus_hepatus", "TangBrain.GRAZE_WEIGHT", "1.0 (was 1.5)", D, "10 §2", "bites on rock per hour"),
        EvidenceEntry("sphaeramia_nematoptera", "shelter/forage driver", "light level, not clock", S, "10 §3 FishBase", "distance from cover at each ramp stage"),
        EvidenceEntry("sphaeramia_nematoptera", "CardinalBrain.FORAGE_RADIUS ramp", "50 % at the forage threshold → 100 % in darkness", D, "10 §3", "distance from cover vs light"),
        EvidenceEntry("labroides_dimidiatus", "maxLengthCm", "14 TL (was 11)", S, "10 §4 FishBase"),
        EvidenceEntry("labroides_dimidiatus", "WrasseBrain.OVERDUE_S", "45 min weighting (not a guarantee)", D, "10 §4 'no visit guarantee'", "accepted visits per approach per client"),
        EvidenceEntry("labroides_dimidiatus", "cheat rate 0.04/0.17 per s", "per inspected second", A, "S:Pinto11 (field), 10 §4", "jolts per inspection"),
        EvidenceEntry("cirrhitichthys_falco", "maxLengthCm", "8 TL (was 7)", S, "10 §5 FishBase"),
        EvidenceEntry("cirrhitichthys_falco", "perch dwell 120–600 s (5–20 hops/h)", "dwell", D, "10 §5 'not established'", "perch changes per hour"),
        EvidenceEntry("cirrhitichthys_falco", "HawkBrain.STRIKE_CONTACT_P / KILL_SOFT_P", "0.5 / 0.5", D, "10 §5, 11 §10", "approach → strike → contact → injury/kill counts with shrimp molt state"),
        EvidenceEntry("salarias_fasciatus", "food = surface film matrix", "film + hair + detritus", S, "10 §6 Christiansen 2010", "bite bouts per surface type"),
        EvidenceEntry("salarias_fasciatus", "lmb_tailspot / lmb_tang pEvent", "0.9 / 0.15 per intrusion", S, "10 §6 Heron Island 90 % vs 20 %", "attacks per intrusion"),
        EvidenceEntry("ecsenius_stigmatura", "head-out / excursion / return sequence", "states", S, "10 §7 proposal", "excursion length and return"),
        EvidenceEntry("ecsenius_stigmatura", "MOVE_AWAY_CM 50, in-hole 40 % of daylight", "quota", D, "10 §7 'not established'"),
        EvidenceEntry("pseudochromis_indigo", "≥ 3 chases of a newcomer in hour 1; ≤ 1/h after 7 days", "acceptance quota", D, "10 §8 'remain model choices'", "same dyad at intro, day 3, day 7"),
        EvidenceEntry("pseudochromis_indigo", "dotty_* habituation/newcomerMult", "interactions.json", D, "10 §8"),
        EvidenceEntry("pholidichthys_leucotaenia", "GobyBrain.ADULT_CM", "15", D, "10 §9 'design estimate'", "length at which open-water activity stops"),
        EvidenceEntry("pholidichthys_leucotaenia", "feeding radius 18 cm", "Feed radius", D, "10 §9"),
        EvidenceEntry("pholidichthys_leucotaenia", "ADULT_NIGHT_PATROL_P", "0.05", D, "10 §9"),
        // ------------------------------------------------------------ shrimp
        EvidenceEntry("stenopus_hispidus", "clean per fish visit", "0.056", S, "12 NTOU thesis (4 stations, 414 h)", "cleans per visit at this tank's ledge"),
        EvidenceEntry("stenopus_hispidus", "day/night: shelter by day, forays at night, weak light lowers contrast", "light-driven", S, "12 Esaka 2016"),
        EvidenceEntry("stenopus_hispidus", "day parasite removal factor", "0.3 (night 1.0)", A, "12 Vaughan 2018 (grouper, night trials)"),
        EvidenceEntry("stenopus_hispidus", "intermolt", "25–26 d at 26 °C", S, "12 reproductive-cycle study 2010"),
        EvidenceEntry("stenopus_hispidus", "intermolt temperature scaling", "Q10 = 2", D, "12 'not guaranteed at another temperature'"),
        EvidenceEntry("stenopus_hispidus", "egg incubation", "mean 22.8 d (20–26)", S, "12 reproductive-cycle study 2010"),
        EvidenceEntry("stenopus_hispidus", "mate recognition", "familiar partner ≠ stranger", S, "12 Johnson 1977"),
        EvidenceEntry("stenopus_hispidus", "ShrimpBreeding.PAIR_FAMILIARITY", "0.3", D, "12", "how long two shrimp share a ledge before pairing"),
        EvidenceEntry("lysmata_amboinensis", "leg-rock predator vs other", "0.51 / 0.12", S, "12 Caves 2019"),
        EvidenceEntry("lysmata_amboinensis", "dim-light rocking gain", "+60 % in darkness", D, "12 Caves 2019 (direction only)"),
        EvidenceEntry("lysmata_amboinensis", "antenna tap before cleaning", "1–2 s", S, "12 Caves 2019 (sequence); duration D"),
        EvidenceEntry("lysmata_amboinensis", "cleans day and night", "station open both", S, "12 Vaughan 2018"),
        EvidenceEntry("lysmata_amboinensis", "fertile eggs need a partner; isolated → infertile", "rule", S, "12 Fiedler 1998"),
        EvidenceEntry("lysmata_amboinensis", "eggs carried until the next molt", "rule", A, "Lysmata general; not measured here"),
        // ------------------------------------------------------------ crabs, snails, stars, worms
        EvidenceEntry("clibanarius_tricolor", "smaller attacker rarely wins", "p ≤ 0.08", S, "11 §1 Hazlett 1966 (direction); value D"),
        EvidenceEntry("clibanarius_tricolor", "recently molted defender more vulnerable", "+0.25 for 48 h", S, "11 §1 Hazlett 1966 (direction); value D"),
        EvidenceEntry("clibanarius_tricolor", "hermit intermolt", "30–60 d", D, "11 §1 'not established'"),
        EvidenceEntry("clibanarius_tricolor", "SNAIL_KILL_PER_MIN (sandbox only)", "0.00015", D, "11 §1 'no snail-killing probability'", "occupied-shell inspections vs transfers"),
        EvidenceEntry("paguristes_cadenati", "pushiness", "0.25 × blue-leg", D, "11 §2 'avoid copying blue-leg rates'"),
        EvidenceEntry("percnon_gibbesi", "diet: grazing primary", "herbivore", S, "11 §3 Puccio 2006, Pizarro-Borrull 2025"),
        EvidenceEntry("percnon_gibbesi", "live-fish predation", "sandbox scenario, off by default", D, "11 §3 'clearly labeled scenario'"),
        EvidenceEntry("percnon_gibbesi", "scavenging the dead", "separate behaviour", S, "11 §3 proposal"),
        EvidenceEntry("ophiocoma_sp", "coordinated arm-role travel", "lead-arm switching", A, "11 §4 Astley 2012 (O. echinata)"),
        EvidenceEntry("ophiocoma_sp", "arm-extension feeding with disc sheltered", "rule", A, "11 §4 Micronesica 1983"),
        EvidenceEntry("nassarius_sp", "feed bout", "lognormal, mean 6 min, ~3/4 ≤ 8 min", A, "11 §5 Lucena 2012 (N. vibex)"),
        EvidenceEntry("nassarius_sp", "longer fasted → faster cue response", "latency 20–240 s × (1.2 − hunger)", A, "11 §5 Lucena 2012 (direction); values D"),
        EvidenceEntry("lithopoma_tectum", "no death timer after inversion", "rule", S, "11 §6"),
        EvidenceEntry("lithopoma_tectum", "righting attempt every 10–40 min, success 0.12 (0.35 with rock/glass in reach)", "attempts", D, "11 §6", "righting time per substrate"),
        EvidenceEntry("asterina_sp", "correlated random walk, shorter travel with food", "CRW", A, "11 §7 Lohmann 2016 (A. anomala)"),
        EvidenceEntry("asterina_sp", "organ-pipe corallivory", "sandbox scenario, off by default", D, "11 §7 'no species-confirmed evidence'"),
        EvidenceEntry("asterina_sp", "fission every ~30 d up to 4", "fissiparity", A, "11 §7 Ebert 2021 (only fissiparous taxa)"),
        EvidenceEntry("sabellastarte_sp", "retraction / crown loss / tube abandonment", "separate states", S, "11 §8"),
        EvidenceEntry("sabellastarte_sp", "crown regrowth", "30–60 d", D, "11 §8 'no regeneration clock'"),
        EvidenceEntry("sabellastarte_sp", "tube abandonment after 3 days crownless and unwell", "rule", D, "11 §8"),
        // ------------------------------------------------------------ sand bed and the diamond goby (research 16)
        EvidenceEntry("sand", "grain settling velocity", "Ferguson & Church 2004 (C1 18, C2 1.0)", S, "16 §3 Ferguson & Church 2004", "time for a puff to clear"),
        EvidenceEntry("sand", "aquarium aragonite grain sizes", "0.25-2 mm (oolite to special grade)", S, "16 §3 CaribSea grade sheet"),
        EvidenceEntry("sand", "detritus floc settling", "≈ 0.3 cm/s", A, "16 §3 Alldredge & Gotschalk 1988 (marine snow)"),
        EvidenceEntry("sand", "smoothing diffusivity and freshness fade", "D 1e-5 + 4e-5·flow² cm²/s; 6 h", D, "16 §3", "how long a pit or trail stays visible"),
        EvidenceEntry("valenciennea_puellaris", "sifts mouthfuls of sand through the gills", "behaviour", S, "16 §1 Wikipedia Valenciennea, fishprofiles"),
        EvidenceEntry("valenciennea_puellaris", "monogamous pairs in shallow burrows under rubble", "behaviour", S, "16 §1 FishBase, Fishes of Australia"),
        EvidenceEntry("valenciennea_puellaris", "starves on a young or small sand bed", "prey index", S, "16 §2 aquariadise (direction); rates D"),
        EvidenceEntry("valenciennea_puellaris", "sand on low corals", "puff within 7 cm, coral < 8 cm over the sand", S, "16 §2 aquariadise, aquariumsource (direction); distances D"),
        EvidenceEntry("valenciennea_puellaris", "DiamondGobyBrain.SIFT_YIELD_MIN", "0.3 min per mouthful at prey index 1", D, "16 §4", "condition over 3 weeks on a young vs mature bed"),
        // ------------------------------------------------------------ leopard wrasse (research video fish_leopard_wrasse)
        EvidenceEntry("macropharyngodon_bipartitus", "sleeps buried in sand; dives when scared", "behaviour", S, "fish_leopard_wrasse S1 Seatizens, S2 fishprofiles, S4 Fenner TFH"),
        EvidenceEntry("macropharyngodon_bipartitus", "dive kinematics", "head-first, 1.05-3.06 s", A, "fish_leopard_wrasse S6 Halichoeres bivittatus (SICB P2-191)"),
        EvidenceEntry("macropharyngodon_bipartitus", "LeopardWrasseBrain.EMERGE_LAG", "15-60 min after lights-on", D, "fish_leopard_wrasse S7 (H. trimaculatus, direction only)", "minutes from lights-on to the wrasse showing"),
        EvidenceEntry("macropharyngodon_bipartitus", "newcomer stays buried", "1-4 days", D, "fish_leopard_wrasse S4 Fenner TFH ('for days')", "days to first sighting after adding one"),
        EvidenceEntry("macropharyngodon_bipartitus", "LeopardWrasseBrain.PICK rate and yield", "10-20 picks/min; 0.17 min per pick at prey index 1", D, "fish_leopard_wrasse H1 (owner clip), rate not measured", "picks per minute counted on video"),
        // ------------------------------------------------------------ bacteria layer (research 18, BacteriaModel)
        EvidenceEntry("bacteria", "AOB dominate reef live rock; AOA a minor high-affinity pool", "AOA_K 2 % of the AOB bed", S, "17 §1.1 live-rock study (AOA < 0.01 %); capacity D", "NH3 floor in a lightly stocked tank"),
        EvidenceEntry("bacteria", "no comammox in marine biofilters", "comammox = 0", S, "17 §1.6 McKnight & Neufeld 2024"),
        EvidenceEntry("bacteria", "antibiotic inhibition Kinh AOB / NOB", "0.5 / 1.0 mg/L, half-life 2 d", D, "17 §1.4 [K]", "NH3 and NO2 after an in-tank erythromycin dose"),
        EvidenceEntry("bacteria", "bottled nitrifiers", "5 % of a mature bed × 0.3-0.6 establishment", D, "17 §6.1 no independent validation", "cycle length with and without a bottle"),
        EvidenceEntry("bacteria", "heterotroph C:N:P", "50:10:1 replete, flexible to C:P 500", D, "17 §2.2 forum [S], unverified", "NO3:PO4 drawdown ratio while carbon dosing"),
        EvidenceEntry("bacteria", "carbon-fed cells skimmed / dying", "0.01 /h / 0.2 /d", D, "17 §2.3, §2.5 [K]", "NO3 fall per week at 2 mg C/L/day, skimmer on vs off"),
        EvidenceEntry("bacteria", "dosed carbon reaching anoxic zones", "25 % at C:N 5", D, "17 §2.1 C:N 4-7 [K]"),
        EvidenceEntry("bacteria", "oxic sand layer", "2 cm (less with detritus, more with sifters)", D, "17 §3.1 [K]", "depth of the grey/black layer in the bed"),
        EvidenceEntry("bacteria", "denitrification needs < ~0.3 mg O2/L", "anoxic zones only", S, "17 §3.1 Heron Island preprint"),
        EvidenceEntry("bacteria", "deep-sand H2S on stirring", "0.15 mg/L per cm of sulfate-reducing sand", D, "17 §3.3 [K]", "H2S test after stirring a 5 cm bed"),
        EvidenceEntry("bacteria", "Vibrio rise with temperature", "≥ 10× in 3 d at 29 °C; coral stress above 28 °C and 1e4/mL", A, "17 §5.1 V. coralliilyticus (Oculina, Pocillopora) [S]; rates D"),
        EvidenceEntry("bacteria", "probiotic", "decays 0.25 /d, lowers the pathogen level", A, "17 §6.2 Pseudoalteromonas McH1-7, Red Sea trial [S]; rates D"),
        EvidenceEntry("bacteria", "UV-C on free-living cells", "1 /h at normal flow; biofilm untouched", D, "17 §6.3 [K]", "plate counts before/after 24 h of UV"),
    )

    fun of(speciesId: String): List<EvidenceEntry> = entries.filter { it.speciesId == speciesId }
    fun byLevel(level: Evidence): List<EvidenceEntry> = entries.filter { it.level == level }
}
