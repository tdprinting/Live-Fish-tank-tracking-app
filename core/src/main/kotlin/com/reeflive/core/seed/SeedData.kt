package com.reeflive.core.seed

import com.reeflive.core.model.CareTask
import com.reeflive.core.model.Equipment
import com.reeflive.core.model.Ids
import com.reeflive.core.model.LightCurve
import com.reeflive.core.model.LightSchedule
import com.reeflive.core.model.Livestock
import com.reeflive.core.model.Tank
import kotlinx.serialization.json.Json

/** A fully materialised seed, ready to insert. */
data class SeedBundle(
    val tank: Tank,
    val livestock: List<Livestock>,
    val equipment: List<Equipment>,
    val tasks: List<CareTask>,
    val lightSchedule: LightSchedule,
)

object SeedData {
    const val RESOURCE = "seed/tee_tank.json"
    const val DEFAULT_TANK_ID = "tank-main"

    private val JSON = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(text: String): SeedFile = JSON.decodeFromString(SeedFile.serializer(), text)

    fun loadFile(loader: ClassLoader? = SeedData::class.java.classLoader): SeedFile {
        val cl = loader ?: Thread.currentThread().contextClassLoader
        val text = (cl.getResourceAsStream(RESOURCE) ?: error("Missing $RESOURCE")).use { it.readBytes().toString(Charsets.UTF_8) }
        return parse(text)
    }

    /** Builds domain objects from the seed. Ids are fresh UUIDs except the tank id. */
    fun materialise(seed: SeedFile, nowMs: Long, tankId: String = DEFAULT_TANK_ID): SeedBundle {
        val t = seed.tank
        val tank = Tank(tankId, t.name, t.lengthCm, t.widthCm, t.heightCm, t.displayGallons, t.sumpGallons, t.reefType, t.sandDepthCm, nowMs)
        val livestock = seed.livestock.map { s ->
            Livestock(
                id = Ids.new(), tankId = tankId, speciesId = s.speciesId, nickname = s.nickname, variant = s.variant,
                role = s.role, lifeStage = s.lifeStage, sizeCm = s.sizeCm, quantity = s.quantity, placement = s.placement,
                verified = s.verified, notes = s.notes, createdAt = nowMs,
            )
        }
        val equipment = seed.equipment.map { e ->
            Equipment(Ids.new(), tankId, e.type, e.name, e.brand, e.model, null, e.serviceDays, null, e.notes)
        }
        val byName = equipment.associateBy { it.name }
        val tasks = seed.tasks.map { s ->
            CareTask(
                id = Ids.new(), tankId = tankId, title = s.title, intervalDays = s.intervalDays,
                reminderHour = s.hour, reminderMinute = s.minute, equipmentId = s.equipment?.let { byName[it]?.id },
                notes = s.notes, createdAt = nowMs,
            )
        }
        val points = seed.lightPoints ?: LightCurve.AI_SOFT_CORAL
        val schedule = LightSchedule(Ids.new(), tankId, "AI Soft Coral", points, active = true)
        return SeedBundle(tank, livestock, equipment, tasks, schedule)
    }
}
