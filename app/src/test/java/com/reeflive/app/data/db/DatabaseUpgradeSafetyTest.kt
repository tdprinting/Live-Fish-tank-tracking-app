package com.reeflive.app.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards that an app update never loses data (JVM scaffold; an on-device MigrationTestHelper test can be added under
 * androidTest, which already sees app/schemas as assets):
 *  - the database never falls back to a destructive migration,
 *  - schema export stays on and a schema JSON exists for every version up to the current one,
 *  - there is a Migration for every step between consecutive versions.
 */
class DatabaseUpgradeSafetyTest {
    // Unit tests run with the module directory (app/) as working directory.
    private val source = File("src/main/java/com/reeflive/app/data/db/ReefDatabase.kt")
    private val schemaDir = File("schemas/com.reeflive.app.data.db.ReefDatabase")

    private fun currentVersion(): Int =
        Regex("""version\s*=\s*(\d+)""").find(source.readText())!!.groupValues[1].toInt()

    @Test
    fun noDestructiveFallbackAndSchemaExportOn() {
        val text = source.readText()
        assertFalse("ReefDatabase must not wipe data on upgrade", text.contains("fallbackToDestructiveMigration"))
        assertTrue(text.contains("exportSchema = true"))
        assertTrue(text.contains(".addMigrations(*Migrations.ALL)"))
    }

    @Test
    fun schemaJsonForEveryVersion() {
        val v = currentVersion()
        for (n in 1..v) assertTrue("missing exported schema ${schemaDir.path}/$n.json", File(schemaDir, "$n.json").isFile)
    }

    @Test
    fun migrationForEveryStep() {
        val v = currentVersion()
        val steps = Migrations.ALL.map { it.startVersion to it.endVersion }.toSet()
        for (n in 1 until v) assertTrue("missing Migration($n, ${n + 1})", (n to n + 1) in steps)
        assertEquals("migrations past the current version", 0, Migrations.ALL.count { it.endVersion > v })
    }
}
