package com.reeflive.app.data.repo

import com.reeflive.core.extras.Feeding
import com.reeflive.core.extras.FeedingPlan
import com.reeflive.core.extras.ReefExtras
import com.reeflive.core.extras.Supply
import com.reeflive.core.extras.TreatmentCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.UUID

/**
 * Supplies, feeding log and treatments live in one versioned JSON file (`files/extras.json`) instead of Room.
 * That keeps the Room schema at version 1 (its exported v1 schema is not committed yet, so adding tables would
 * need a migration we can't verify against the shipped schema). The file is written atomically (tmp + rename),
 * unknown fields are ignored, and it is included in JSON backups.
 */
class ExtrasRepository(private val file: File, private val scope: CoroutineScope, private val clock: Clock) {
    private val lock = Mutex()
    private val _data = MutableStateFlow(ReefExtras())
    val data: StateFlow<ReefExtras> = _data.asStateFlow()

    private var loaded = false

    init {
        scope.launch { lock.withLock { ensureLoaded() } }
    }

    /** Call with [lock] held: the first access reads the file, so no write can clobber unread data. */
    private fun ensureLoaded() {
        if (!loaded) {
            _data.value = read()
            loaded = true
        }
    }

    private fun read(): ReefExtras = runCatching { if (file.isFile) ReefExtras.decode(file.readText()) else ReefExtras() }
        .getOrElse {
            // Keep the unreadable file for recovery instead of overwriting it with an empty one.
            runCatching { file.copyTo(File(file.parentFile, file.name + ".bad"), overwrite = true) }
            ReefExtras()
        }

    private fun write(e: ReefExtras) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(ReefExtras.encode(e))
        if (!tmp.renameTo(file)) {
            file.writeText(ReefExtras.encode(e))
            tmp.delete()
        }
    }

    suspend fun update(block: (ReefExtras) -> ReefExtras) {
        lock.withLock {
            ensureLoaded()
            val next = block(_data.value)
            _data.value = next
            write(next)
        }
    }

    suspend fun replaceAll(e: ReefExtras) = update { e }

    fun newId(): String = UUID.randomUUID().toString()

    // ---------------------------------------------------------- supplies
    suspend fun saveSupply(s: Supply) = update { e -> e.copy(supplies = e.supplies.filterNot { it.id == s.id } + s) }
    suspend fun deleteSupply(id: String) = update { e -> e.copy(supplies = e.supplies.filterNot { it.id == id }) }
    suspend fun adjustSupply(id: String, delta: Double) = update { e ->
        e.copy(supplies = e.supplies.map { if (it.id == id) it.copy(quantity = (it.quantity + delta).coerceAtLeast(0.0)) else it })
    }

    // ---------------------------------------------------------- feeding
    suspend fun logFeeding(food: String, amount: String = "", at: Long = clock.now()) =
        update { e -> e.copy(feedings = (e.feedings + Feeding(newId(), at, food, amount)).sortedBy { it.at }.takeLast(MAX_FEEDINGS)) }
    suspend fun deleteFeeding(id: String) = update { e -> e.copy(feedings = e.feedings.filterNot { it.id == id }) }
    suspend fun setPlan(plan: FeedingPlan) = update { e -> e.copy(feedingPlan = plan) }

    // ---------------------------------------------------------- treatments
    suspend fun saveCase(c: TreatmentCase) = update { e -> e.copy(cases = e.cases.filterNot { it.id == c.id } + c) }
    suspend fun deleteCase(id: String) = update { e -> e.copy(cases = e.cases.filterNot { it.id == id }) }
    suspend fun logDose(id: String, at: Long = clock.now()) =
        update { e -> e.copy(cases = e.cases.map { if (it.id == id) it.copy(doses = it.doses + at) else it }) }
    suspend fun undoDose(id: String) =
        update { e -> e.copy(cases = e.cases.map { if (it.id == id) it.copy(doses = it.doses.dropLast(1)) else it }) }
    suspend fun endCase(id: String, outcome: String, at: Long = clock.now()) =
        update { e -> e.copy(cases = e.cases.map { if (it.id == id) it.copy(endedAt = at, outcome = outcome) else it }) }

    companion object {
        const val FILE_NAME = "extras.json"
        /** Two years of twice-daily feedings. */
        const val MAX_FEEDINGS = 1500
    }
}
