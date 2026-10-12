package com.reeflive.core.sim

import com.reeflive.core.geom.smoothstep

/** Fish disease treatments (coverage gap W-4; research 01 §3.8, §4.3). */
enum class TreatmentKind(val label: String, val defaultDays: Double) {
    /** 1.009-1.010 SG reached at 0.002 SG/day, held 4-6 weeks, raised back at 0.002/day (Manta). Ich only; not velvet. */
    HYPOSALINITY("Hyposalinity", 35.0),
    /** Copper held at a therapeutic ~0.20 ppm for 14-28 days (CRYPT case 0.24 → 0.16). Kills inverts and corals in a display. */
    COPPER("Copper", 21.0),
    /** UV on the return: kills free-swimming stages (ich theronts), so less spreads between fish; cures nothing on the fish. */
    UV("UV steriliser", 0.0),
    /** Fish moved to a clean tank every 72 h, 4 transfers, 12 days: trophonts drop off and are left behind (ich). */
    TANK_TRANSFER("Tank transfer", 12.0),
    /** Formalin baths: velvet, Brooklynella, Uronema (and some ich). */
    FORMALIN("Formalin baths", 7.0),
    /** Praziquantel: flukes. Repeat dosing per label. */
    PRAZIQUANTEL("Praziquantel", 7.0),
}

/** How a treatment changes one disease for one fish: onset and growth multipliers and a clearing rate (stage/min). */
data class DiseaseMods(val onset: Double = 1.0, val growth: Double = 1.0, val clearPerMin: Double = 0.0, val transmission: Double = 1.0) {
    fun and(o: DiseaseMods) = DiseaseMods(onset * o.onset, growth * o.growth, clearPerMin + o.clearPerMin, transmission * o.transmission)
}

class ActiveTreatment(val kind: TreatmentKind, val startSec: Double, val endSec: Double, val fishIds: Set<String>?) {
    fun applies(o: Organism) = o.isFish && (fishIds == null || o.id in fishIds)
}

/**
 * Active treatments and what they do to each disease. Hyposalinity and copper act through the water itself (the SG
 * and copper the fish actually sit in), so stopping the ramp early or diluting the copper weakens them.
 */
class Treatments {
    val active = LinkedHashMap<TreatmentKind, ActiveTreatment>()

    fun isActive(k: TreatmentKind) = active.containsKey(k)

    fun copyFrom(o: Treatments) { active.clear(); active.putAll(o.active) }

    fun modsFor(o: Organism, w: WaterState, simTimeSec: Double): Map<Disease, DiseaseMods> {
        if (active.isEmpty() || !o.isFish) return emptyMap()
        val m = HashMap<Disease, DiseaseMods>()
        fun add(d: Disease, x: DiseaseMods) { m[d] = (m[d] ?: DiseaseMods()).and(x) }
        for (t in active.values) {
            if (!t.applies(o)) continue
            when (t.kind) {
                TreatmentKind.HYPOSALINITY -> {
                    // Cryptocaryon cannot complete its cycle at ≤ 1.010; velvet is unaffected (01 §4.3, Manta)
                    val e = 1 - smoothstep(1.010, 1.013, w.sg)
                    add(Disease.ICH, DiseaseMods(onset = 1 - e, clearPerMin = 7e-5 * e, transmission = 1 - e))
                }
                TreatmentKind.COPPER -> {
                    // therapeutic from ~0.15 ppm; copper works on ich, velvet and Uronema, not Brooklynella (01 §4.3)
                    val e = smoothstep(0.08, 0.15, w.copper)
                    add(Disease.ICH, DiseaseMods(onset = 1 - e, clearPerMin = 8e-5 * e, transmission = 1 - e))
                    add(Disease.VELVET, DiseaseMods(onset = 1 - e, clearPerMin = 1.2e-4 * e, transmission = 1 - e))
                    add(Disease.URONEMA, DiseaseMods(clearPerMin = 5e-5 * e))
                }
                TreatmentKind.UV -> for (d in listOf(Disease.ICH, Disease.VELVET, Disease.URONEMA)) add(d, DiseaseMods(onset = 0.5, transmission = 0.2))
                TreatmentKind.TANK_TRANSFER -> {
                    // trophonts leave the fish within 3-7 days and stay behind in the old tank (01 §3.8)
                    val days = (simTimeSec - t.startSec) / 86400
                    add(Disease.ICH, DiseaseMods(onset = 0.0, transmission = 0.0, clearPerMin = if (days >= 3) 1.5e-4 else 3e-5))
                }
                TreatmentKind.FORMALIN -> {
                    add(Disease.VELVET, DiseaseMods(clearPerMin = 1e-4))
                    add(Disease.BROOKLYNELLA, DiseaseMods(clearPerMin = 1.5e-4))
                    add(Disease.URONEMA, DiseaseMods(clearPerMin = 1e-4))
                    add(Disease.ICH, DiseaseMods(clearPerMin = 3e-5))
                }
                TreatmentKind.PRAZIQUANTEL -> add(Disease.FLUKES, DiseaseMods(onset = 0.0, clearPerMin = 1.5e-4))
            }
        }
        return m
    }

    /** True while hyposalinity is running: fish are acclimated slowly, so their SG tolerance follows the treatment band. */
    val hypo: Boolean get() = isActive(TreatmentKind.HYPOSALINITY)

    companion object {
        const val HYPO_SG = 1.010
        const val HYPO_RATE_PER_DAY = 0.002
        const val COPPER_PPM = 0.20
        const val COPPER_RAMP_DAYS = 2.0
        /** Fish SG band during a slow hyposalinity course (01 §3.8: 1.009-1.010 held 4-6 weeks). */
        val HYPO_FISH_SG = ToleranceRange(1.009, 1.0265, 1.008, 1.028, 1.005, 1.032)
    }
}
