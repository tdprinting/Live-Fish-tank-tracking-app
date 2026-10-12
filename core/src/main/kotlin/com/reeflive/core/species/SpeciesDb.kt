package com.reeflive.core.species

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Loads all species sheets from classpath resources `species/<id>.json`, listed in
 * `species/index.json` (classpath directories cannot be listed on Android).
 */
class SpeciesDb(sheets: List<SpeciesSheet>, val interactions: InteractionMatrix = InteractionMatrix.EMPTY) {
    val all: List<SpeciesSheet> = sheets.sortedWith(compareBy({ it.kind.ordinal }, { it.common }))
    private val byId: Map<String, SpeciesSheet> = sheets.associateBy { it.id }

    operator fun get(id: String): SpeciesSheet? = byId[id]
    fun require(id: String): SpeciesSheet = byId[id] ?: error("Unknown species $id")
    val size: Int get() = byId.size

    fun search(query: String): List<SpeciesSheet> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return all
        return all.filter { it.common.lowercase().contains(q) || it.scientific.lowercase().contains(q) || it.id.contains(q) }
    }

    /** All tolerance-band problems, keyed "speciesId.param". Empty when valid. */
    fun validate(): Map<String, List<String>> = all.flatMap { s ->
        s.tolerance.mapNotNull { (k, band) -> band.problems().takeIf { it.isNotEmpty() }?.let { "${s.id}.$k" to it } }
    }.toMap()

    /** True when an adult of [id] needs more water than [displayGallons]. */
    fun outgrowsTank(id: String, displayGallons: Double): Boolean =
        byId[id]?.minTankGallons?.let { it > displayGallons } ?: false

    @Serializable
    private data class Index(val species: List<String>)

    companion object {
        val JSON = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

        fun parseSheet(text: String): SpeciesSheet = JSON.decodeFromString(SpeciesSheet.serializer(), text)

        fun load(loader: ClassLoader? = SpeciesDb::class.java.classLoader): SpeciesDb {
            val cl = loader ?: Thread.currentThread().contextClassLoader
            val index = JSON.decodeFromString(Index.serializer(), readResource(cl, "species/index.json"))
            val sheets = index.species.map { parseSheet(readResource(cl, "species/$it.json")) }
            val matrix = runCatching { InteractionMatrix.parse(readResource(cl, "species/interactions.json")) }
                .getOrDefault(InteractionMatrix.EMPTY)
            return SpeciesDb(sheets, matrix)
        }

        internal fun readResource(cl: ClassLoader, path: String): String {
            val stream = cl.getResourceAsStream(path) ?: error("Missing resource $path")
            return stream.use { it.readBytes().toString(Charsets.UTF_8) }
        }
    }
}
