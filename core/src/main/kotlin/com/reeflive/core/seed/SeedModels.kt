package com.reeflive.core.seed

import com.reeflive.core.model.EquipmentType
import com.reeflive.core.model.LightPoint
import com.reeflive.core.model.ReefType
import kotlinx.serialization.Serializable

/** Shape of `seed/tee_tank.json` (DESIGN §9). Everything about Tee's tank lives in that file. */
@Serializable
data class SeedFile(
    val tank: SeedTank,
    val livestock: List<SeedLivestock>,
    val equipment: List<SeedEquipment>,
    val tasks: List<SeedTask> = emptyList(),
    /** Preset name ("ai_soft_coral_default") or omitted when [lightPoints] is given. */
    val lightSchedule: String? = null,
    val lightPoints: List<LightPoint>? = null,
)

@Serializable
data class SeedTank(
    val name: String,
    val lengthCm: Double,
    val widthCm: Double,
    val heightCm: Double,
    val displayGallons: Double,
    val sumpGallons: Double = 0.0,
    val reefType: ReefType = ReefType.SOFT,
    val sandDepthCm: Double = 3.0,
)

@Serializable
data class SeedLivestock(
    val speciesId: String,
    val nickname: String,
    val variant: String? = null,
    val sizeCm: Double? = null,
    val role: String? = null,
    val lifeStage: String? = null,
    val quantity: Int = 1,
    val placement: String? = null,
    val verified: Boolean = true,
    val notes: String? = null,
)

@Serializable
data class SeedEquipment(
    val type: EquipmentType,
    val name: String,
    val serviceDays: Int? = null,
    val brand: String? = null,
    val model: String? = null,
    val notes: String? = null,
)

@Serializable
data class SeedTask(
    val title: String,
    val intervalDays: Int,
    val hour: Int = 8,
    val minute: Int = 0,
    /** Name of the seeded equipment this task services, if any. */
    val equipment: String? = null,
    val notes: String? = null,
)
