package com.reeflive.app.ui.live

import com.reeflive.core.sim.OrganismStats
import com.reeflive.core.sim.RelationInfo
import com.reeflive.core.sim.Thought
import com.reeflive.core.sim.WaterState
import com.reeflive.core.status.Status
import java.util.Locale

/**
 * The two tanks at the top of the live screen: "My tank" (built from scratch and saved, where every lever works; the
 * screen opens here) and Watch (the real tank as logged, read-only). The old pretend Sandbox copy is gone; Watch's
 * fast-forward still runs on a hidden copy so the real tank keeps real time.
 */
enum class HudMode(val label: String) { MY_TANK("My tank"), WATCH("Watch") }

enum class DayPhase(val label: String) { NIGHT("Night"), DAWN("Dawn"), DAY("Day"), DUSK("Dusk") }

/** One cell of the compact water strip. [status] drives colour + icon (never colour only). */
data class WaterReadout(val id: String, val label: String, val valueText: String, val status: Status)

data class HudToast(val id: Long, val text: String, val severity: Status = Status.UNKNOWN)

data class HudState(
    val mode: HudMode = HudMode.WATCH,
    val speed: Double = 1.0,
    val speeds: List<Double> = listOf(1.0, 60.0, 240.0, 1000.0),
    val simTimeSec: Double = 0.0,
    val dayPhase: DayPhase = DayPhase.DAY,
    val readouts: List<WaterReadout> = emptyList(),
    val toast: HudToast? = null,
)

fun formatSpeed(s: Double): String = "${s.toLong()}x"

/** "Day 3 · 14:25" from simulated seconds (day 1 starts at 00:00). */
fun formatSimClock(simTimeSec: Double): String {
    val total = simTimeSec.toLong().coerceAtLeast(0)
    val day = total / 86_400 + 1
    val h = (total % 86_400) / 3600
    val m = (total % 3600) / 60
    return String.format(Locale.US, "Day %d · %02d:%02d", day, h, m)
}

// ---------------------------------------------------------------- fish

enum class RelationKind(val label: String) {
    FRIEND("Friend"), NEUTRAL("Neutral"), WARY("Wary"), RIVAL("Rival"), MATE("Mate"), PREY("Prey"), PREDATOR("Predator"),
}

/** How this fish feels about one tank mate (social-memory entry). [affinity] -1 (hostile)..+1 (bonded). */
data class RelationshipUi(
    val otherName: String,
    val otherSpecies: String,
    val kind: RelationKind,
    val affinity: Float,
    val note: String = "",
    /** Core phrase ("Afraid of", "Cleaning partner"...) shown instead of [kind] when set. */
    val labelOverride: String? = null,
)

data class FishCardState(
    val id: String,
    val speciesName: String,
    val name: String,
    val health: Float,
    val stress: Float,
    val hunger: Float,
    val mood: String,
    val behavior: String,
    val alive: Boolean = true,
    val symptoms: List<String> = emptyList(),
    val relationships: List<RelationshipUi> = emptyList(),
    /** Life stage and sex, e.g. "Juvenile" or "Adult · Female" (core LifeStages; docs/research/life_stages.md). */
    val lifeStage: String = "",
    /** "What is it thinking?" (core ai/Thoughts.kt): plan, next plan and feeling, shown under "Thinking…". */
    val thought: Thought = Thought.NONE,
)

/** Maps core social memory one-to-one: label drives [RelationKind], fear/harassment/familiarity drive [RelationshipUi.affinity]. */
fun RelationInfo.toUi(): RelationshipUi {
    val kind = when (label) {
        "Afraid of" -> RelationKind.PREDATOR
        "Chased by" -> RelationKind.WARY
        "Chases" -> RelationKind.RIVAL
        "Dominates" -> RelationKind.RIVAL
        "Cleaning partner" -> RelationKind.FRIEND
        else -> RelationKind.NEUTRAL
    }
    val negative = maxOf(fear, harassedBy, harassed)
    val positive = maxOf(familiarity * 0.5f, if (cleanings > 0) 0.6f else 0f)
    val affinity = (positive - negative).coerceIn(-1f, 1f)
    val note = when {
        cleanings > 0 -> "$cleanings cleanings"
        secondsSinceSeen > 3600f -> "not seen recently"
        else -> ""
    }
    return RelationshipUi(otherName, otherCommon, kind, affinity, note).copy(labelOverride = label)
}

fun OrganismStats.toFishCardState(relationships: List<RelationshipUi> = relations.map { it.toUi() }) = FishCardState(
    id = id, speciesName = common, name = nickname.ifBlank { common },
    health = health, stress = stress, hunger = hunger, mood = mood.label,
    behavior = intent.ifBlank { behavior },
    alive = alive, symptoms = symptoms.map { it.label }, relationships = relationships,
    lifeStage = LifeStageLabels.of(speciesId, lengthCm.toDouble(), sex),
    thought = thought,
)

/** Stage labels from the species sheets on the classpath (cached per species; "" for non-fish or a missing sheet). */
object LifeStageLabels {
    private val sheets = java.util.concurrent.ConcurrentHashMap<String, java.util.Optional<com.reeflive.core.sim.SimSpecies>>()

    fun of(speciesId: String, lengthCm: Double, sex: String): String {
        val sp = sheets.getOrPut(speciesId) {
            java.util.Optional.ofNullable(com.reeflive.core.sim.SpeciesCatalog.fromClasspath(listOf(speciesId)).all().firstOrNull())
        }.orElse(null) ?: return ""
        if (!sp.kind.equals("FISH", true)) return ""
        return com.reeflive.core.sim.LifeStages.describe(sp, lengthCm, sex)
    }
}

fun OrganismStats.toFollowState(index: Int, count: Int) = FollowState(
    fishName = nickname.ifBlank { common }, speciesName = common,
    behaviorLine = caption.ifBlank { intent.ifBlank { behavior } }, index = index, count = count,
    id = id, thought = thought,
)

data class FollowState(
    val fishName: String,
    val speciesName: String,
    val behaviorLine: String,
    val index: Int = 0,
    val count: Int = 1,
    val id: String = "",
    /** Shown as the "Thinking…" lines in place of [behaviorLine] when present. */
    val thought: Thought = Thought.NONE,
)

/** Water strip helper: temp, salinity, NO3, PO4 from [w] against the [SandboxParams] zones, plus live light % and flow. */
fun waterReadouts(w: WaterState, lightPct: Double, flowStrength: Double): List<WaterReadout> {
    fun f(v: Double, d: Int) = String.format(Locale.US, "%.${d}f", v)
    fun r(id: String, short: String, v: Double, text: String) =
        WaterReadout(id, short, text, SandboxParams.byId(id)?.zones?.status(v) ?: Status.UNKNOWN)
    val flowPct = flowStrength * 100.0
    return listOf(
        r("temp", "Temp", w.tempC, f(w.tempC, 1) + "°"),
        r("sal", "Sal", w.sg, f(w.sg, 3)),
        r("no3", "NO3", w.no3, f(w.no3, 1)),
        r("po4", "PO4", w.po4, f(w.po4, 2)),
        WaterReadout("light", "Light", f(lightPct, 0) + "%", if (lightPct > 0.5) Status.GOOD else Status.UNKNOWN),
        r("flow", "Flow", flowPct, f(flowPct, 0) + "%"),
    )
}

/**
 * The shelf test kit's cells for the water strip (core TestKit: API Reef Master colour cards and calcium drops, the
 * Salifert KH syringe). What a hobby kit shows, not the sim's exact numbers: one test per pick ([rnd]), so a second
 * test can read a swatch or a drop differently, and the status is judged from what the kit shows.
 */
fun testKitReadouts(w: WaterState, rnd: kotlin.random.Random): List<WaterReadout> =
    com.reeflive.core.sim.TestKit.read(w, rnd).map { k ->
        WaterReadout(k.id, k.label, k.text, SandboxParams.byId(k.id)?.zones?.status(k.value) ?: Status.UNKNOWN)
    }
