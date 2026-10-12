package com.reeflive.app.data.repo

import com.reeflive.app.data.db.OverrideDao
import com.reeflive.app.data.db.TankDao
import com.reeflive.app.data.db.toEntity
import com.reeflive.app.data.db.toModel
import com.reeflive.core.model.ParameterOverride
import com.reeflive.core.model.ReefType
import com.reeflive.core.model.Tank
import com.reeflive.core.params.ParameterDef
import com.reeflive.core.params.Targets
import com.reeflive.core.seed.SeedData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** v1 is single-tank: every row belongs to this id (the seed's tank id). */
const val TANK_ID: String = SeedData.DEFAULT_TANK_ID

/** Injectable wall clock so ViewModels and tests can pin "now". */
fun interface Clock {
    fun now(): Long

    companion object {
        val SYSTEM = Clock { System.currentTimeMillis() }
    }
}

class TankRepository(private val dao: TankDao) {
    val tank: Flow<Tank?> = dao.observe().map { it?.toModel() }

    suspend fun get(): Tank? = dao.get()?.toModel()

    suspend fun save(tank: Tank) = dao.upsert(tank.toEntity())
}

/** Effective parameter targets: reef-type defaults from `ParameterTable` merged with user overrides. */
class TargetsRepository(private val dao: OverrideDao, tanks: TankRepository) {
    val reefType: Flow<ReefType> = tanks.tank.map { it?.reefType ?: ReefType.SOFT }.distinctUntilChanged()

    val overrides: Flow<Map<String, ParameterOverride>> = dao.observe().map { list -> list.associate { it.paramId to it.toModel() } }

    /** Visible definitions in display order. */
    val effective: Flow<List<ParameterDef>> = combine(reefType, overrides) { t, o -> Targets.effective(t, o, includeHidden = false) }

    /** All definitions including hidden ones (settings editor). */
    val all: Flow<List<ParameterDef>> = combine(reefType, overrides) { t, o -> Targets.effective(t, o, includeHidden = true) }

    suspend fun effectiveOnce(includeHidden: Boolean = false): List<ParameterDef> =
        Targets.effective(reefType.first(), overrides.first(), includeHidden)

    suspend fun def(paramId: String): ParameterDef? = effectiveOnce(includeHidden = true).firstOrNull { it.id == paramId }

    fun observeDef(paramId: String): Flow<ParameterDef?> = all.map { list -> list.firstOrNull { it.id == paramId } }

    suspend fun override(paramId: String): ParameterOverride = overrides.first()[paramId] ?: ParameterOverride(paramId)

    suspend fun save(o: ParameterOverride) = dao.upsert(o.toEntity())

    /** Writes the ranges of [def] as an override (targets editor). */
    suspend fun saveRanges(def: ParameterDef) {
        val o = override(def.id)
        save(
            o.copy(
                idealLo = def.ideal.lo, idealHi = def.ideal.hi, accLo = def.acceptable.lo, accHi = def.acceptable.hi,
                dangerLo = def.dangerLow, dangerHi = def.dangerHigh, maxChange = def.maxChangePerDay, cadenceDays = def.cadenceDays,
            ),
        )
    }

    /** Drops range edits but keeps visibility/favorite/order preferences. */
    suspend fun resetRanges(paramId: String) {
        val o = override(paramId)
        save(ParameterOverride(paramId, hidden = o.hidden, favorite = o.favorite, order = o.order, displayUnit = o.displayUnit))
    }

    suspend fun setHidden(paramId: String, hidden: Boolean) = save(override(paramId).copy(hidden = hidden))
    suspend fun setFavorite(paramId: String, favorite: Boolean) = save(override(paramId).copy(favorite = favorite))

    /** Persists a user ordering: index in [ids] becomes the sort order. */
    suspend fun setOrder(ids: List<String>) {
        val current = overrides.first()
        dao.upsertAll(ids.mapIndexed { i, id -> (current[id] ?: ParameterOverride(id)).copy(order = i).toEntity() })
    }

    suspend fun resetAll(paramIds: List<String>) = paramIds.forEach { dao.delete(it) }
}
