package com.reeflive.core.io

import com.reeflive.core.model.Alert
import com.reeflive.core.model.CareTask
import com.reeflive.core.model.Equipment
import com.reeflive.core.model.HealthLog
import com.reeflive.core.model.JournalEntry
import com.reeflive.core.model.LightSchedule
import com.reeflive.core.model.Livestock
import com.reeflive.core.model.Measurement
import com.reeflive.core.model.ParameterOverride
import com.reeflive.core.model.Photo
import com.reeflive.core.model.ReefEvent
import com.reeflive.core.model.Tank
import com.reeflive.core.model.TaskCompletion
import com.reeflive.core.model.TestSession
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Full JSON backup of every table (DESIGN §6.7). Photos are referenced by path, not embedded. */
@Serializable
data class Backup(
    val version: Int = VERSION,
    val exportedAt: Long,
    val tanks: List<Tank> = emptyList(),
    val sessions: List<TestSession> = emptyList(),
    val measurements: List<Measurement> = emptyList(),
    val overrides: List<ParameterOverride> = emptyList(),
    val alerts: List<Alert> = emptyList(),
    val tasks: List<CareTask> = emptyList(),
    val completions: List<TaskCompletion> = emptyList(),
    val events: List<ReefEvent> = emptyList(),
    val livestock: List<Livestock> = emptyList(),
    val healthLogs: List<HealthLog> = emptyList(),
    val equipment: List<Equipment> = emptyList(),
    val journal: List<JournalEntry> = emptyList(),
    val photos: List<Photo> = emptyList(),
    val lightSchedules: List<LightSchedule> = emptyList(),
) {
    companion object {
        const val VERSION = 1
    }
}

object BackupCodec {
    val JSON = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = false; explicitNulls = false }

    fun encode(b: Backup): String = JSON.encodeToString(Backup.serializer(), b)

    fun decode(text: String): Backup {
        val b = JSON.decodeFromString(Backup.serializer(), text)
        require(b.version <= Backup.VERSION) { "Backup version ${b.version} is newer than this app" }
        return b
    }
}
