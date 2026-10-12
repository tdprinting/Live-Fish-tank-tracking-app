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
