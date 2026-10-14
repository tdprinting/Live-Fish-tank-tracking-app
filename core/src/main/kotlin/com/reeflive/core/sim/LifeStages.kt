package com.reeflive.core.sim

import kotlinx.serialization.Serializable

/**
 * Life stages and sex roles (docs/research/life_stages.md). Each fish species sheet carries a `lifeStages` block
 * ([LifeStageSpec]): length thresholds for its colour/shape stages, how wide the visual blend is, and its sex system
 * (protandrous clowns, protogynous wrasses / hawkfish / dottybacks, gonochoristic tangs, cardinals, blennies and
 * gobies, unknown for the engineer goby). Stage is a pure function of [Organism.lengthCm], so fish added small
 * from the sandbox (StockSize JUVENILE) look and act juvenile, and sim growth carries them through a gradual blend.
 */
@Serializable
data class StageSpec(
    /** "juvenile", "subadult", "adult" (or "initial" for a labrid's female phase). */
    val id: String,
    /** Total length (cm) where this stage starts; the first stage starts at 0. */
    val fromCm: Double = 0.0,
    /** Texture form drawn for this stage (renderer `textures/stages` manifest key); defaults to [id]. */
    val form: String? = null,
    val label: String? = null,
    /** Body depth (model Y) and width (model Z) relative to the GLB as built (shader `uStageShape`). */
    val depthScale: Double = 1.0,
    val widthScale: Double = 1.0,
) {
    val formName: String get() = form ?: id
    val labelText: String get() = label ?: id.replaceFirstChar { it.uppercase() }
}

/** How a species allocates sex (research per species in life_stages.md). */
enum class SexSystem {
    /** Separate sexes for life (tang, cardinal, blennies, diamond goby). */
    GONOCHORISTIC,
    /** Male first; the dominant (largest) becomes female (clownfish). */
    PROTANDROUS,
    /** Female first; the dominant becomes male (wrasses, hawkfish, dottybacks). */
    PROTOGYNOUS,
    /** Not described (engineer goby: reproduction is undocumented). */
    UNKNOWN;

    companion object {
        fun parse(s: String?): SexSystem = entries.firstOrNull { it.name.equals(s ?: "", true) } ?: UNKNOWN
    }
}

@Serializable
data class LifeStageSpec(
    val stages: List<StageSpec> = listOf(StageSpec("juvenile"), StageSpec("adult", 4.0)),
    /** Width (cm of length) of the visual blend centred on each threshold. */
    val blendCm: Double = 1.0,
    val sexSystem: String = "UNKNOWN",
    /** Protogynous/protandrous species that can also change back (Kadota 2009 hawkfish; Wittenrich & Munday 2005). */
    val bidirectional: Boolean = false,
    /** Length (cm) from which a fish takes part in the sex hierarchy. */
    val maturityCm: Double = 0.0,
    /** Days for the dominant individual's change; days for a reverse change (bidirectional species). */
    val sexChangeDays: Double = 28.0,
    val reverseChangeDays: Double = 60.0,
    /** Texture forms that differ by sex (e.g. the leopard wrasse terminal male); null = no visible colour difference. */
    val maleForm: String? = null,
    val femaleForm: String? = null,
) {
    val system: SexSystem get() = SexSystem.parse(sexSystem)
    private val sorted: List<StageSpec> get() = stages.sortedBy { it.fromCm }

    /** Stage the fish is in (hard threshold; behaviour uses this). */
    fun stageAt(lengthCm: Double): StageSpec = sorted.lastOrNull { lengthCm >= it.fromCm } ?: sorted.first()

    /**
     * Visual blend at [lengthCm]: the stage before and after the nearest threshold and the mix 0..1 between them
     * (smoothstep across [blendCm] centred on the threshold), so a growing fish fades from one pattern to the next.
     */
    fun blendAt(lengthCm: Double): Triple<StageSpec, StageSpec, Double> {
        val s = sorted
        for (i in 1 until s.size) {
            val t = s[i].fromCm
            val half = blendCm.coerceAtLeast(1e-3) / 2
            if (lengthCm < t - half) return Triple(s[i - 1], s[i - 1], 0.0)
            if (lengthCm <= t + half) {
                val x = ((lengthCm - (t - half)) / (2 * half)).coerceIn(0.0, 1.0)
                return Triple(s[i - 1], s[i], x * x * (3 - 2 * x))
            }
        }
        val last = s.last()
        return Triple(last, last, 0.0)
    }

    companion object {
        /** Generic fallback (sheets without a block): juvenile below 45 % of max length, as FishBrains used to assume. */
        fun generic(sp: SimSpecies) = LifeStageSpec(
            stages = listOf(StageSpec("juvenile"), StageSpec("adult", 0.45 * sp.maxLengthCm)),
            blendCm = 0.1 * sp.maxLengthCm,
        )

        /**
         * Specs for species whose sheet does not carry a `lifeStages` block yet (none at present: the blue star leopard
         * wrasse sheet now carries its own, docs/research/life_stages.md §10).
         */
        val PENDING: Map<String, LifeStageSpec> = emptyMap()

        fun of(sp: SimSpecies): LifeStageSpec = sp.lifeStages ?: PENDING[sp.id] ?: generic(sp)
    }
}

/** Visible sex / role of one animal. */
enum class SexRole { FEMALE, MALE, CHANGING_TO_FEMALE, CHANGING_TO_MALE, IMMATURE, UNSEXED }

/**
 * One animal's life stage and sex as the renderer and UI see them. Texture: draw [fromForm] mixed toward [toForm] by
 * [stageMix]; then, where the species has a sex form, mix toward [sexForm] by [sexMix]. Shape: scale the body depth
 * and width by [depthScale] / [widthScale] (already blended).
 */
data class LifeForm(
    val stage: String,
    val stageLabel: String,
    val fromForm: String,
    val toForm: String,
    val stageMix: Float,
    val depthScale: Float,
    val widthScale: Float,
    val sex: SexRole,
    val sexForm: String? = null,
    val sexMix: Float = 0f,
    val mature: Boolean = true,
) {
    /** "Juvenile", "Adult · Female", "Initial phase · Changing to male", ... */
    val label: String get() = when (sex) {
        SexRole.FEMALE -> "$stageLabel · Female"
        SexRole.MALE -> "$stageLabel · Male"
        SexRole.CHANGING_TO_FEMALE -> "$stageLabel · Changing to female"
        SexRole.CHANGING_TO_MALE -> "$stageLabel · Changing to male"
        else -> stageLabel
    }
}

object LifeStages {
    fun spec(o: Organism): LifeStageSpec = LifeStageSpec.of(o.species)

    fun stage(o: Organism): StageSpec = spec(o).stageAt(o.lengthCm)

    /** Replaces FishBrains' old `lengthCm < 0.45 × max` test with the species' own first-stage threshold. */
    fun isJuvenile(o: Organism): Boolean = spec(o).let { s -> s.stageAt(o.lengthCm) === s.stages.minByOrNull { it.fromCm } && s.stages.size > 1 }

    fun isAdult(o: Organism): Boolean = stage(o).id == "adult"

    fun mature(o: Organism): Boolean = o.lengthCm >= spec(o).maturityCm

    /** Sex role from the organism's resolved sex, its in-progress change and the species system. */
    fun sexRole(o: Organism): SexRole {
        val sp = spec(o)
        val female = o.sexFemale ?: when {
            o.role.equals("FEMALE", true) -> true
            o.role.equals("MALE", true) -> false
            else -> null
        }
        if (o.sexChange > 0.0 && female != null) return if (female) SexRole.CHANGING_TO_MALE else SexRole.CHANGING_TO_FEMALE
        return when {
            female == true -> SexRole.FEMALE
            female == false -> SexRole.MALE
            !mature(o) && sp.system != SexSystem.UNKNOWN -> SexRole.IMMATURE
            else -> SexRole.UNSEXED
        }
    }

    /**
     * Card line from a sheet, a length and the stats' sex label ("Female", "Changing sex", ""), e.g. "Juvenile" or
     * "Adult · Female"; used where only [OrganismStats] is at hand.
     */
    fun describe(sp: SimSpecies, lengthCm: Double, sexLabel: String = ""): String {
        val st = LifeStageSpec.of(sp).stageAt(lengthCm).labelText
        return if (sexLabel.isBlank()) st else "$st · $sexLabel"
    }

    /** Render/UI view of [o] (pure; no sim state is changed). */
    fun form(o: Organism): LifeForm {
        val sp = spec(o)
        val (a, b, mix) = sp.blendAt(o.lengthCm)
        val st = sp.stageAt(o.lengthCm)
        val m = mix.toFloat()
        val role = sexRole(o)
        // sex forms show once the fish is past its first stage: male = full, changing = progress
        val (sexForm, sexMix) = when (role) {
            SexRole.MALE -> sp.maleForm to 1f
            SexRole.CHANGING_TO_MALE -> sp.maleForm to o.sexChange.toFloat()
            SexRole.CHANGING_TO_FEMALE -> (sp.maleForm) to (1f - o.sexChange.toFloat())
            SexRole.FEMALE -> sp.femaleForm to (if (sp.femaleForm != null) 1f else 0f)
            else -> null to 0f
        }
        return LifeForm(
            stage = st.id, stageLabel = st.labelText,
            fromForm = a.formName, toForm = b.formName, stageMix = m,
            depthScale = (a.depthScale + (b.depthScale - a.depthScale) * mix).toFloat(),
            widthScale = (a.widthScale + (b.widthScale - a.widthScale) * mix).toFloat(),
            sex = role, sexForm = sexForm?.takeIf { sexMix > 0f }, sexMix = if (sexForm != null) sexMix.coerceIn(0f, 1f) else 0f,
            mature = mature(o),
        )
    }
}

/**
 * Sex roles and their changes for species not handled by [Reproduction] (which keeps the clownfish pair, the cleaner
 * wrasse harem and the cardinal pair). Stepped once per chemistry tick.
 *
 *  - Protogynous (hawkfish, dottyback, leopard wrasse): mature fish start female. With two or more mature fish and no
 *    male, the largest female becomes male over `sexChangeDays` (it reverts if a male appears meanwhile).
 *  - Bidirectional (hawkfish, dottyback, cleaner wrasse): with two or more males and no female, the smaller male
 *    becomes female over `reverseChangeDays` (Kadota 2009; Wittenrich & Munday 2005; Kuwamura et al. 2002).
 *  - A lone fish keeps whatever it is: there is no social cue to change.
 */
object SexRoles {
    /** Brains whose sex Reproduction already resolves and changes forward (it keeps that; reverse change is added here). */
    private fun forwardHandled(k: BrainKind) = k == BrainKind.CLOWN || k == BrainKind.WRASSE || k == BrainKind.CARDINAL

    fun step(organisms: List<Organism>, dtSec: Double) {
        val bySpecies = organisms.filter { it.alive && it.isFish }.groupBy { it.species.id }
        for ((_, group) in bySpecies) {
            val sp = LifeStageSpec.of(group[0].species)
            val sys = sp.system
            if (sys != SexSystem.PROTOGYNOUS && !(sys == SexSystem.PROTANDROUS && sp.bidirectional)) continue
            val k = group[0].brainKind
            val mature = group.filter { it.lengthCm >= sp.maturityCm }
            if (!forwardHandled(k) && sys == SexSystem.PROTOGYNOUS) protogyny(mature, sp, dtSec)
            if (sp.bidirectional) reverse(mature, sp, sys, dtSec)
        }
    }

    private fun protogyny(mature: List<Organism>, sp: LifeStageSpec, dtSec: Double) {
        for (o in mature) if (o.sexFemale == null) o.sexFemale = !o.role.equals("MALE", true)
        val males = mature.filter { it.sexFemale == false }
        val females = mature.filter { it.sexFemale == true }
        val rate = dtSec / (sp.sexChangeDays.coerceAtLeast(0.1) * 86400.0)
        if (males.isNotEmpty() || mature.size < 2) {
            // a male is present (or the fish is alone): any female change in progress fades out
            for (f in females) f.sexChange = maxOf(0.0, f.sexChange - rate)
            return
        }
        val big = females.maxByOrNull { it.lengthCm } ?: return
        for (f in females) if (f !== big) f.sexChange = maxOf(0.0, f.sexChange - rate)
        big.sexChange += rate
        if (big.sexChange >= 1.0) { big.sexChange = 0.0; big.sexFemale = false }
    }

    private fun reverse(mature: List<Organism>, sp: LifeStageSpec, sys: SexSystem, dtSec: Double) {
        // the reverse of the species' usual direction: male → female for protogynous species
        if (sys != SexSystem.PROTOGYNOUS) return
        val males = mature.filter { it.sexFemale == false }
        val anyFemale = mature.any { it.sexFemale != false }
        val rate = dtSec / (sp.reverseChangeDays.coerceAtLeast(0.1) * 86400.0)
        if (males.size < 2 || anyFemale) {
            for (m in males) m.sexChange = maxOf(0.0, m.sexChange - rate)
            return
        }
        val big = males.maxBy { it.lengthCm }
        val small = males.filter { it !== big }.maxBy { it.lengthCm }
        for (m in males) if (m !== small) m.sexChange = maxOf(0.0, m.sexChange - rate)
        small.sexChange += rate
        if (small.sexChange >= 1.0) { small.sexChange = 0.0; small.sexFemale = true }
    }
}

/** Each snapshot's [LifeForm]s by organism id ([SimSnapshot.lifeForms]); empty for snapshots the sim did not build. */
object LifeFormChannel {
    fun of(s: SimSnapshot?): Map<String, LifeForm> = s?.lifeForms ?: emptyMap()
}
