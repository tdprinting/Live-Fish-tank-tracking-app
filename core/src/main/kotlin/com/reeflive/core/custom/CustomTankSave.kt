package com.reeflive.core.custom

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The saved "My tank": everything the keeper built up over time, in a format that survives app updates.
 *
 * Rules that keep old saves opening in every later version (see [CustomTankCodec]):
 * - every field has a default, so a field added later reads as its default from an older save;
 * - unknown fields are ignored, so a newer save still opens in this version;
 * - water, equipment and microfauna are name → number maps, so a renamed or dropped sim field costs one value, not
 *   the save; a renamed field gets a step in [CustomTankCodec.migrate] instead;
 * - an animal whose species this version does not know is kept as it is ([dormant]) and written back, never lost.
 */
@Serializable
data class CustomTankSave(
    val version: Int = CustomTankCodec.VERSION,
    val name: String = "My tank",
    val buildId: String = TankBuilds.DEFAULT.id,
    /** Wall clock (epoch ms) when the tank was set up and when it was last saved (the catch-up on reopen). */
    val createdAtMs: Long = 0L,
    val savedAtMs: Long = 0L,
    val simTimeSec: Double = 12 * 3600.0,
    val seed: Long = 1L,
    /** The time speed the keeper left it at (1 = real time). */
    val speed: Double = 1.0,
    /** How fast slow biology runs ([com.reeflive.core.sim.Simulation.lifePace]; older saves take the default). */
    val lifePace: Double = com.reeflive.core.sim.LifePace.DEFAULT.pace,
    /** The game-biology clock ([com.reeflive.core.sim.Simulation.lifeSec]); null in older saves = [simTimeSec]. */
    val lifeSec: Double? = null,
    val water: Map<String, Double> = emptyMap(),
    val equipment: Map<String, Double> = emptyMap(),
    /** Microfauna totals by group name (pods, worms…: what a squid, a goby or a mandarin lives on). */
    val micro: Map<String, Double> = emptyMap(),
    val animals: List<SavedAnimal> = emptyList(),
    val corals: List<SavedCoral> = emptyList(),
    /** Next free ids so newcomers never reuse a saved animal's id. */
    val nextIds: SavedIds = SavedIds(),
    /** Animals of species this version cannot load, carried through untouched. */
    val dormant: List<SavedAnimal> = emptyList(),
    val dormantCorals: List<SavedCoral> = emptyList(),
    /** Coral species ids out of their bag and waiting for the keeper to pick a spot, in order (added after v1, defaulted). */
    val toPlace: List<String> = emptyList(),
    /** Eggs on the rock and the nursery tank's larvae / fry (added after v1 as a defaulted field: no migration needed). */
    val brood: com.reeflive.core.sim.SavedBroodState = com.reeflive.core.sim.SavedBroodState(),
    /** The rockwork when it differs from the stock scape: rocks that shifted, were added or were taken out ([Simulation.rockState]). */
    val rocks: String = "",
    /**
     * How far Tee's rocks were moved to fit this tank's glass (x, y, z cm; Simulation.scapeShift). Missing in saves
     * from before tanks were drawn to size: a tank of another size then gets its rocks fitted afresh on opening.
     */
    val scapeFit: List<Double>? = null,
    /** Pest controllers living in the rock (PestState.save: "berghia" population, "peppermint" count). */
    val pests: Map<String, Double> = emptyMap(),
    /** Sand dollars, stars, today's care list and goals (docs/ECONOMY.md; added after v1 as a defaulted field). */
    val economy: com.reeflive.core.custom.economy.SavedEconomy = com.reeflive.core.custom.economy.SavedEconomy(),
    /** The stand and room the keeper bought and picked (room/Decor.kt; added after v1 as a defaulted field). */
    val decor: com.reeflive.core.custom.room.SavedDecor = com.reeflive.core.custom.room.SavedDecor(),
    /** Which wall of the room this tank stands against (0 back, 1 right, 2 front, 3 left; room/RoomTanks.kt). */
    val side: Int = 0,
    /**
     * The keeper's other tanks, on the other walls (room/RoomTanks.kt): each a whole save of its own, without its own
     * [others], [economy] or [decor] (the room's money and look are the active tank's). Defaulted: no codec bump.
     */
    val others: List<CustomTankSave> = emptyList(),
)

@Serializable
data class SavedIds(val organism: Int = 1, val frag: Int = 1, val bag: Int = 1)

/** One animal: who it is (name, personality), how far it has grown and aged, and how it is doing. */
@Serializable
data class SavedAnimal(
    val id: String,
    val speciesId: String,
    val nickname: String = "",
    val role: String? = null,
    val placement: String? = null,
    val variant: String? = null,
    val lengthCm: Double = 0.0,
    val ageSec: Double = 0.0,
    val agingDebtSec: Double = 0.0,
    /** Sim time it arrived (days in the tank, newcomer status). */
    val arrivalTime: Double = 0.0,
    /** Personality: the individual temperament rolled when it arrived (±15 % around its species). */
    val aggression: Double? = null,
    val boldness: Double? = null,
    val curiosity: Double? = null,
    val skittishness: Double? = null,
    val acceptsPrepared: Boolean = true,
    val sexFemale: Boolean? = null,
    val sexChange: Double = 0.0,
    val mateId: String? = null,
    val health: Double = 1.0,
    val condition: Double = 1.0,
    val hunger: Double = 0.3,
    val stress: Double = 0.0,
    val chronic: Double = 0.0,
    val immunity: Double = 1.0,
    val disease: String? = null,
    val diseaseStage: Double = 0.0,
    val hlle: Double = 0.0,
    val nutritionGreens: Double = 1.0,
    /** Species state that is not on [Organism] (an anemone's foot and shape, a clown's anemone acclimation); keys are stable. */
    val state: Map<String, Double> = emptyMap(),
)

@Serializable
data class SavedCoral(
    val id: String,
    val speciesId: String,
    val nickname: String = "",
    /** Anchor it grows on ("" = loose: a dropped branch or a frag, placed at [x], [y], [z]). */
    val anchor: String = "",
    val offsetX: Double = 0.0, val offsetY: Double = 0.0, val offsetZ: Double = 0.0,
    val x: Double = 0.0, val y: Double = 0.0, val z: Double = 0.0,
    val nx: Double = 0.0, val ny: Double = 1.0, val nz: Double = 0.0,
    val tissue: Double = 1.0,
    val bleach: Double = 0.0,
    val brown: Double = 0.0,
    val stress: Double = 0.0,
    val dhw: Double = 0.0,
    val algaeCover: Double = 0.0,
    val size: Double = 1.0,
    val cover: Double = 0.0,
    val radiusCm: Double = 0.0,
    /** Recent caught food 0..1 and the light it is used to (−1 unknown: an older save), for hunger and dark fading. */
    val fed: Double = 0.0,
    val acclLight: Double = -1.0,
)

/**
 * JSON for [CustomTankSave], versioned. [VERSION] goes up whenever a change needs more than a defaulted field; each
 * bump adds a step to [migrate] that rewrites the older JSON into the next version's shape, so a save from any past
 * version opens in this one (CustomTankCodecTest keeps a frozen v1 save).
 */
object CustomTankCodec {
    const val VERSION = 1

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
        allowSpecialFloatingPointValues = true
        explicitNulls = false
    }

    fun encode(save: CustomTankSave): String = json.encodeToString(CustomTankSave.serializer(), save.copy(version = VERSION))

    /** Reads a save of any version up to [VERSION]; null when the text is not a save at all (corrupt file). */
    fun decode(text: String): CustomTankSave? = runCatching {
        val root = json.parseToJsonElement(text).jsonObject
        json.decodeFromJsonElement(CustomTankSave.serializer(), migrate(root))
    }.getOrNull()

    /** Upgrades an older save's JSON step by step to [VERSION]. A save from a newer version is read as is. */
    fun migrate(root: JsonObject): JsonElement {
        var o = root
        var v = o["version"]?.jsonPrimitive?.let { runCatching { it.int }.getOrNull() } ?: 1
        while (v < VERSION) {
            o = when (v) {
                // future: 1 -> { rename / reshape fields here }
                else -> o
            }
            v++
            o = JsonObject(o + ("version" to JsonPrimitive(v)))
        }
        return o
    }
}
