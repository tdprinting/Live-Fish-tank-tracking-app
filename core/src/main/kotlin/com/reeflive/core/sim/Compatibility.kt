package com.reeflive.core.sim

/** One add-time warning for the sandbox "add animal" UI (coverage_gaps B-10). */
data class CompatibilityWarning(
    /** Stable machine id: "blenny_cap", "dottyback_cap", "tang_tank_size", ... */
    val code: String,
    val severity: Severity,
    val message: String,
) {
    enum class Severity { INFO, WARN, SEVERE }
}

/** Stocking rules from the research (02 §10, 04 §15) that the sim models as chronic stress. */
object Compatibility {
    /** Small-tank reef (the owner's 60 gal ≈ 227 L): above this volume the one-blenny rule relaxes (research 04: 400 L+). */
    const val BLENNY_FREE_VOLUME_L = 400.0

    /** Sand-bed volume under which a sand-sifting goby runs out of sand fauna (research 16: 55 gal ≈ 208 L minimum). */
    const val SAND_SIFTER_MIN_L = 200.0

    fun isBlenny(k: BrainKind?) = k == BrainKind.LAWNMOWER_BLENNY || k == BrainKind.PEEKER_BLENNY

    /**
     * Warnings for adding [speciesId] to a tank holding [stock] (species ids of living animals; duplicates count).
     * [volumeL] <= 0 means unknown (volume rules are skipped). Pure and allocation-light; safe to call on every UI recomposition.
     */
    fun compatibilityWarnings(speciesId: String, stock: Collection<String>, volumeL: Double = 0.0): List<CompatibilityWarning> {
        val kind = BrainKind.byId(speciesId) ?: return emptyList()
        val others = stock.mapNotNull { BrainKind.byId(it) }
        val out = ArrayList<CompatibilityWarning>()
        if (isBlenny(kind) && others.any { isBlenny(it) } && (volumeL <= 0 || volumeL < BLENNY_FREE_VOLUME_L)) {
            out += CompatibilityWarning(
                "blenny_cap", CompatibilityWarning.Severity.WARN,
                "Blennies are territorial toward each other (lawnmower, tailspot, bicolor and midas fight). Only one per tank under about 400 L; the newcomer will be chronically stressed.",
            )
        }
        if (kind == BrainKind.DOTTYBACK && others.any { it == BrainKind.DOTTYBACK }) {
            out += CompatibilityWarning("dottyback_cap", CompatibilityWarning.Severity.WARN,
                "Dottybacks are aggressive toward their own kind. Keep one per tank.")
        }
        if ((kind == BrainKind.TANG || kind == BrainKind.EEL) && others.any { it == kind }) {
            out += CompatibilityWarning("conspecific_cap", CompatibilityWarning.Severity.WARN,
                "A second ${if (kind == BrainKind.TANG) "tang" else "moray"} will be harassed and chronically stressed in this tank.")
        }
        if (kind == BrainKind.DIAMOND_GOBY) {
            // research 16: sand sifters strip the sand fauna; unpaired adults fight; low corals get sanded
            if (volumeL > 0 && volumeL < SAND_SIFTER_MIN_L) {
                out += CompatibilityWarning("sand_sifter_tank_size", CompatibilityWarning.Severity.WARN,
                    "A diamond goby sifts a small sand bed clean of pods within weeks and then starves unless it takes sinking food. Plan on about 200 L of mature live sand.")
            } else {
                out += CompatibilityWarning("sand_sifter_food", CompatibilityWarning.Severity.INFO,
                    "Lives on the pods and worms in the sand. It needs a mature sand bed and target feeding, and it will drop sand on corals placed low.")
            }
            val n = others.count { it == BrainKind.DIAMOND_GOBY }
            if (n >= 2 && n % 2 == 0) {   // every one already has a mate
                out += CompatibilityWarning("diamond_goby_pair", CompatibilityWarning.Severity.WARN,
                    "Diamond gobies live in mated pairs; an extra one will be chased off the sand.")
            }
        }
        if (kind == BrainKind.LEOPARD_WRASSE) {
            // research video fish_leopard_wrasse: sleeps buried, lives on pods, newcomers hide for days, bullied by hawkfish/dottybacks
            out += CompatibilityWarning("leopard_wrasse_care", CompatibilityWarning.Severity.INFO,
                "Sleeps buried in the sand and lives on the tank's pods: it needs 5–8 cm of fine sand and a mature tank. A new one may stay in the sand for days.")
            if (others.any { it == BrainKind.HAWK || it == BrainKind.DOTTYBACK }) {
                out += CompatibilityWarning("leopard_wrasse_bullies", CompatibilityWarning.Severity.INFO,
                    "Hawkfish and dottybacks may chase a newly added leopard wrasse; it hides in the sand until they settle.")
            }
            if (others.count { it == BrainKind.LEOPARD_WRASSE } >= 1) {
                out += CompatibilityWarning("leopard_wrasse_pair", CompatibilityWarning.Severity.WARN,
                    "Leopard wrasses change sex: keep one, or one male with females in a large tank. Two males fight.")
            }
        }
        if (kind == BrainKind.ANEMONE) {
            // research 40: wanders until settled, stings what it touches, pumps are a hazard; clowns may move in
            out += CompatibilityWarning("anemone_wanders", CompatibilityWarning.Severity.INFO,
                "A new bubble tip anemone walks around for days until it finds a crevice with good light and flow. It stings corals and fish it touches, and powerhead intakes need a guard.")
            if (others.any { it == BrainKind.CLOWN }) {
                out += CompatibilityWarning("anemone_clowns", CompatibilityWarning.Severity.INFO,
                    "Your clownfish may move into it. Ocellaris are not natural bubble tip hosts, so it can take days to weeks of testing touches, and some never do.")
            }
            if (others.count { it == BrainKind.ANEMONE } >= 1) {
                out += CompatibilityWarning("anemone_war", CompatibilityWarning.Severity.WARN,
                    "Anemones that are not clones of each other sting one another when they touch. Splits from the same anemone get along.")
            }
        }
        squidWarnings(kind, others, out)
        val minV = LifeCycle.minVolumeL(kind)
        if (minV > 0 && volumeL > 0 && volumeL < minV) {
            out += CompatibilityWarning("${kind.name.lowercase()}_tank_size", CompatibilityWarning.Severity.WARN,
                "Adults need about ${minV.toInt()} L. In ${volumeL.toInt()} L it will be chronically stressed, grow stunted and live shorter.")
        }
        return out
    }

    /** Predatory fish that hunt or harass a bobtail squid (research cephalopod_squid §4). */
    fun huntsSquid(k: BrainKind?) = k == BrainKind.HAWK || k == BrainKind.DOTTYBACK || k == BrainKind.EEL

    /**
     * Bobtail squid (research cephalopod_squid): the honest verdict for a mixed reef. It eats small shrimp and crabs, predatory fish
     * hunt or harass it, it lives about six months and it wants cooler water than most reefs run.
     */
    private fun squidWarnings(kind: BrainKind, others: List<BrainKind>, out: MutableList<CompatibilityWarning>) {
        val squidHere = others.any { it == BrainKind.BOBTAIL_SQUID }
        if (kind == BrainKind.BOBTAIL_SQUID) {
            if (others.any { huntsSquid(it) }) {
                out += CompatibilityWarning("squid_predator_fish", CompatibilityWarning.Severity.SEVERE,
                    "A hawkfish, dottyback or moray will hunt or harass a bobtail squid. It stays buried, stops hunting and is injured or eaten. Keep it in its own tank.")
            }
            if (others.any { it == BrainKind.CLEANER_SHRIMP || it == BrainKind.BANDED_SHRIMP || it == BrainKind.HERMIT || it == BrainKind.SALLY_CRAB || it == BrainKind.ARROW_CRAB }) {
                out += CompatibilityWarning("squid_eats_crustaceans", CompatibilityWarning.Severity.WARN,
                    "Squid hunt shrimp and small crabs at night. Adult cleaner and banded shrimp are mostly too big, but small, young or freshly molted ones get eaten, and a banded shrimp can grab a sleeping squid.")
            }
            if (others.any { it.isFish && !huntsSquid(it) }) {
                out += CompatibilityWarning("squid_busy_fish", CompatibilityWarning.Severity.WARN,
                    "Active fish (tangs, clowns, wrasses) take its food and keep it stressed; squid do best in a quiet species tank.")
            }
            out += CompatibilityWarning("squid_short_life", CompatibilityWarning.Severity.INFO,
                "A bobtail squid lives about 4 to 8 months, breeds once and dies. It needs live mysids or shrimp every night, a deep fine sand bed to bury in, no copper and water no warmer than about 25 °C.")
        } else if (squidHere && huntsSquid(kind)) {
            out += CompatibilityWarning("squid_predator_fish", CompatibilityWarning.Severity.SEVERE,
                "This fish hunts or harasses the bobtail squid.")
        } else if (squidHere && (kind == BrainKind.CLEANER_SHRIMP || kind == BrainKind.BANDED_SHRIMP)) {
            out += CompatibilityWarning("squid_eats_crustaceans", CompatibilityWarning.Severity.WARN,
                "The bobtail squid hunts small and freshly molted shrimp at night.")
        }
    }
}

/** Convenience top-level entry point: `compatibilityWarnings("ecsenius_stigmatura", stockIds, volumeL)`. */
fun compatibilityWarnings(speciesId: String, stock: Collection<String>, volumeL: Double = 0.0): List<CompatibilityWarning> =
    Compatibility.compatibilityWarnings(speciesId, stock, volumeL)
