package com.reeflive.core.species

import com.reeflive.core.seed.SeedData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeciesDbTest {
    private val db = SpeciesDb.load()

    @Test
    fun loadsEverySheet() {
        // 10 fish species (9 kinds incl. a clown pair), 9 inverts, 6 corals, 1 macroalga per Tee's confirmed list.
        assertEquals(25, db.size)
        assertEquals(9, db.all.count { it.kind == SpeciesKind.FISH })
        assertEquals(9, db.all.count { it.kind == SpeciesKind.INVERT })
        assertEquals(6, db.all.count { it.kind == SpeciesKind.CORAL })
        assertEquals(1, db.all.count { it.kind == SpeciesKind.ALGAE })
    }

    @Test
    fun toleranceTablesAreConsistent() {
        assertEquals(emptyMap<String, List<String>>(), db.validate())
        for (s in db.all) {
            assertTrue("${s.id} has temp tolerance", s.tolerance.containsKey("temp"))
            for ((k, b) in s.tolerance) {
                // optLo ≤ optHi ≤ tolHi ≤ lethalHi, on whichever bounds exist
                val chain = listOfNotNull(b.optLo, b.optHi, b.tolHi, b.lethalHi)
                assertEquals("${s.id}.$k high chain", chain.sorted(), chain)
                val low = listOfNotNull(b.lethalLo, b.tolLo, b.optLo)
                assertEquals("${s.id}.$k low chain", low.sorted(), low)
            }
        }
    }

    @Test
    fun seedSpeciesAllExist() {
        val seed = SeedData.loadFile()
        for (l in seed.livestock) assertNotNull("species ${l.speciesId}", db[l.speciesId])
    }

    @Test
    fun tankSizeWarningsForTangAndEelOnly() {
        val outgrow = db.all.filter { db.outgrowsTank(it.id, 60.0) }.map { it.id }.toSet()
        assertEquals(setOf("paracanthurus_hepatus", "echidna_nebulosa"), outgrow)
    }

    @Test
    fun fishHaveMotionAndBrains() {
        for (s in db.all.filter { it.isFish }) {
            assertNotNull(s.id, s.locomotion)
            assertNotNull(s.id, s.zone)
            assertNotNull(s.id, s.brain)
        }
        assertTrue(db.all.filter { it.kind == SpeciesKind.CORAL }.all { it.coral != null && it.isSessile })
    }

    @Test
    fun interactionMatrixIsSymmetric() {
        val m = db.interactions
        assertTrue(m.size > 40)
        assertEquals(Risk.HIGH, m.risk("echidna_nebulosa", "sphaeramia_nematoptera"))
        assertEquals(Risk.HIGH, m.risk("sphaeramia_nematoptera", "echidna_nebulosa"))
        assertEquals(Risk.HIGH, m.risk("salarias_fasciatus", "ecsenius_stigmatura"))
        assertEquals(Risk.MODERATE_HIGH, m.risk("lysmata_amboinensis", "cirrhitichthys_falco"))
        assertEquals(Risk.NONE, m.risk("amphiprion_ocellaris", "paracanthurus_hepatus"))
        assertEquals(Risk.HIGH, m.risk("salarias_fasciatus", "salarias_fasciatus"))
        assertEquals(Risk.NONE, m.risk("amphiprion_ocellaris", "amphiprion_ocellaris"))
    }

    @Test
    fun toleranceStressCurve() {
        val tang = db.require("paracanthurus_hepatus").tolerance["temp"]!!
        assertEquals(0.0, tang.stress(25.5), 1e-12)
        assertEquals(0.4, tang.stress(28.0), 1e-12)
        assertEquals(0.1, tang.stress(27.5), 1e-12)
        assertEquals(1.0, tang.stress(31.0), 1e-12)
        assertTrue(tang.stress(29.5) in 0.4..1.0)
        assertTrue(tang.isLethal(31.0))
        assertFalse(tang.isLethal(30.9))
        assertEquals(0.4, tang.stress(23.0), 1e-12)
        assertEquals(1.0 - 0.6 * 0.9, Tolerance.combine(listOf(0.4, 0.1)), 1e-12)
    }
}
