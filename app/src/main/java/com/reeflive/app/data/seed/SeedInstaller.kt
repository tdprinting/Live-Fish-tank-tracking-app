package com.reeflive.app.data.seed

import androidx.room.withTransaction
import com.reeflive.app.data.db.ReefDatabase
import com.reeflive.app.data.db.toEntity
import com.reeflive.app.data.repo.Clock
import com.reeflive.app.data.repo.TANK_ID
import com.reeflive.core.seed.SeedBundle
import com.reeflive.core.seed.SeedData

/**
 * Installs Tee's tank (core `seed/tee_tank.json`). Onboarding edits a [SeedBundle] preview (tank fields,
 * unchecked livestock) and installs that. Reset replaces the tank, livestock, equipment, tasks and light
 * schedule but keeps measurements, events and the journal.
 */
class SeedInstaller(private val db: ReefDatabase, private val clock: Clock) {

    /** A fresh bundle from the bundled seed (new ids each call). */
    fun preview(): SeedBundle = SeedData.materialise(SeedData.loadFile(), clock.now(), TANK_ID)

    suspend fun install(bundle: SeedBundle = preview()) {
        val wipe = db.wipeDao()
        db.withTransaction {
            wipe.healthLogs()
            wipe.livestock()
            wipe.completions()
            wipe.tasks()
            wipe.equipment()
            wipe.lightSchedules()
            wipe.tanks()
            db.tankDao().upsert(bundle.tank.toEntity())
            db.livestockDao().upsertAll(bundle.livestock.map { it.toEntity() })
            db.equipmentDao().upsertAll(bundle.equipment.map { it.toEntity() })
            db.taskDao().upsertAll(bundle.tasks.map { it.toEntity() })
            db.lightDao().upsertAll(listOf(bundle.lightSchedule.toEntity()))
        }
    }

    /**
     * Tee named the animals (2026-10-14). Existing installs still hold the old seeded nicknames; rename a row only
     * while it still has its old default name, so names typed in the app are kept.
     */
    suspend fun renameSeededAnimals() {
        val dao = db.livestockDao()
        db.withTransaction { for ((species, old, new) in SEED_RENAMES) dao.renameIfStill(species, old, new) }
    }

    /** Installs the seed only when the database has no tank (first run, or after "Clear all data"). */
    suspend fun ensureInstalled(): Boolean {
        if (db.tankDao().get() != null) return false
        install()
        return true
    }

    companion object {
        /** (speciesId, old seeded nickname, new nickname). Both clowns share a species, so the old name picks the row. */
        val SEED_RENAMES = listOf(
            Triple("amphiprion_ocellaris", "Orange", "Nemo"),
            Triple("amphiprion_ocellaris", "Darwin", "Marlin"),
            Triple("paracanthurus_hepatus", "Blue", "Dori"),
            Triple("sphaeramia_nematoptera", "PJ", "Derprick"),
            Triple("labroides_dimidiatus", "Cleaner", "Shakira"),
            Triple("pholidichthys_leucotaenia", "Engineer", "Ricky Bobby Sanchez"),
            Triple("cirrhitichthys_falco", "Hawk", "Hawk tuah"),
            Triple("salarias_fasciatus", "Mower", "Dallas"),
            Triple("ecsenius_stigmatura", "Tailspot", "Dennis"),
            Triple("pseudochromis_indigo", "Indigo", "Indy"),
            Triple("valenciennea_puellaris", "Diamond", "Sandy"),
            Triple("macropharyngodon_bipartitus", "Leopard", "Marina"),
            Triple("stenopus_hispidus", "Bandit", "Spike"),
            Triple("paguristes_cadenati", "Scarlet", "Mr. Crabs"),
        )
    }
}
