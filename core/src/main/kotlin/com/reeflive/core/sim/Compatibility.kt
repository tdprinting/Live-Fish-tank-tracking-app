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
                "Blennies are territorial toward each other (lawnmower, tailspot and bicolor fight). Only one per tank under about 400 L; the newcomer will be chronically stressed.",
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
        val minV = LifeCycle.minVolumeL(kind)
        if (minV > 0 && volumeL > 0 && volumeL < minV) {
            out += CompatibilityWarning("${kind.name.lowercase()}_tank_size", CompatibilityWarning.Severity.WARN,
                "Adults need about ${minV.toInt()} L. In ${volumeL.toInt()} L it will be chronically stressed, grow stunted and live shorter.")
        }
        return out
    }
}

/** Convenience top-level entry point: `compatibilityWarnings("ecsenius_stigmatura", stockIds, volumeL)`. */
fun compatibilityWarnings(speciesId: String, stock: Collection<String>, volumeL: Double = 0.0): List<CompatibilityWarning> =
    Compatibility.compatibilityWarnings(speciesId, stock, volumeL)
