package com.reeflive.core.sim

import kotlinx.serialization.Serializable

/**
 * A species' sensing channels (research realism_gaps X1/X6, SIMULATION §21.1). Optional `senses` block in the species
 * sheet; a sheet without one gets [defaultFor] by brain and activity. Every number here is a DESIGN ESTIMATE inside
 * the direction the sources give: sight fades with light (rod-poor diurnal retinas, MRL23 title only), a lateral line
 * carries the near field in the dark (POH04, catfish analogy), and smell reaches downstream along the current
 * (DIX12, goby analogy; HAZ96 for hermits).
 *
 * - [visionCm]: how far food is seen in full light (cm). 120 covers the whole tank, so daylight search is unchanged.
 * - [darkVision]: fraction of [visionCm] left in darkness (0 = blind at night, 1 = no loss).
 * - [lateralBL]: lateral-line reach in body lengths, independent of light (fish only; 0 for invertebrates).
 * - [chem]: 0..1 weight of smell. 0 = the animal ignores scent; above 0 a scent plume can lead it to food it cannot see.
 * - [gated]: false = no perception gate at all (the old radius search). Invertebrates default to false so their
 *   tuned scavenging rates stay as they were; a sheet can opt one in.
 */
@Serializable
data class SensesSpec(
    @Ev(Evidence.DESIGN_ESTIMATE, "realism_gaps X1: whole-tank daylight range keeps day search unchanged")
    val visionCm: Double = 120.0,
    @Ev(Evidence.DESIGN_ESTIMATE, "realism_gaps X6, MRL23 (title only): diurnal 0.12, nocturnal 0.5")
    val darkVision: Double = 0.12,
    @Ev(Evidence.ANALOGY, "POH04 catfish lateral line; near-field reach 1.5 BL is a design value")
    val lateralBL: Double = 1.5,
    @Ev(Evidence.ANALOGY, "DIX12 goby chemical cue; HAZ96 hermit odour")
    val chem: Double = 0.6,
    val gated: Boolean = true,
) {
    companion object {
        /** Defaults by body plan and daily rhythm when the sheet has no `senses` block. */
        fun defaultFor(sp: SimSpecies): SensesSpec {
            val kind = sp.brainKind
            if (!kind.isFish) return SensesSpec(darkVision = 1.0, lateralBL = 0.0, chem = 1.0, gated = false)
            val act = sp.activity.uppercase()
            return when {
                act == "NOCTURNAL" || act == "CREPUSCULAR" -> SensesSpec(darkVision = NOCTURNAL_DARK_VISION)
                else -> SensesSpec()
            }
        }

        /** Rod-rich dim-light retinas of nocturnal reef fish keep more of their range (MRL23, title only) [L]. */
        @Ev(Evidence.DESIGN_ESTIMATE, "MRL23 title; value [L]") const val NOCTURNAL_DARK_VISION = 0.5
    }
}
