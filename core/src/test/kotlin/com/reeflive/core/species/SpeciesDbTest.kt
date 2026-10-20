package com.reeflive.core.species

import com.reeflive.core.seed.SeedData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeciesDbTest {
    private val db = SpeciesDb.load()

    @Test
    fun loadsEverySheet() {
        // 10 fish species (9 kinds incl. a clown pair), 9 inverts, 6 corals, 1 macroalga per Tee's confirmed list,
        // plus the diamond goby (research 16) and blue star leopard wrasse (both added to the tank 2026-10), the
        // hairy mushroom the clowns host, 4 corals from Tee's photos 2026-10-13 (toadstool, pink leather, finger
        // leather, elephant ear), the bobtail squid (sandbox only, research cephalopod_squid) and the rainbow bubble tip
        // anemone (research 40).
        assertEquals(38, db.size)
        assertEquals(14, db.all.count { it.kind == SpeciesKind.FISH })
        assertEquals(12, db.all.count { it.kind == SpeciesKind.INVERT })
        assertEquals(11, db.all.count { it.kind == SpeciesKind.CORAL })
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
    fun modelsMatchShippedAssets() {
        // File names from the asset track (app/src/main/assets/models, ASSETS §5).
        val shipped = setOf(
            "algae_tufts", "asterina", "astrea", "bobtail_squid", "brittle_star", "cardinal", "clove", "clown_darwin", "clown_orange",
            "diamond_goby", "dottyback", "engineer_goby", "feather_duster", "gsp_mat", "gsp_neon", "hawkfish", "hermit_blue", "hermit_scarlet",
            "kenya", "lawnmower_blenny", "leopard_wrasse", "macro_red", "mushroom", "mushroom_hairy", "mushroom_hairy_mound", "nassarius", "organ_pipe", "sally_lightfoot", "shrimp", "skunk_shrimp",
            "tailspot_blenny", "tang", "wrasse", "zoa", "toadstool_leather", "leather_pink", "finger_leather", "elephant_ear", "bubble_tip_anemone", "yellow_tang", "purple_tang", "fairy_wrasse",
        ).map { "models/$it.glb" }.toSet()
        for (s in db.all) {
            val all = listOfNotNull(s.model) + s.variantModels.values
            assertTrue("${s.id} has a model", all.isNotEmpty())
            for (m in all) assertTrue("${s.id} -> $m is shipped", m in shipped)
        }
        assertEquals("models/clown_darwin.glb", db.require("amphiprion_ocellaris").modelFor("darwin_black"))
        assertEquals("models/clown_orange.glb", db.require("amphiprion_ocellaris").modelFor("classic"))
        assertEquals("models/gsp_neon.glb", db.require("briareum_sp").modelFor("neon"))
        assertEquals("models/mushroom_hairy_mound.glb", db.require("rhodactis_sp").modelFor("mound"))
        assertEquals("species_amphiprion_ocellaris", db.require("amphiprion_ocellaris").cardDrawable)
    }

    @Test
    fun seedSpeciesAllExist() {
        val seed = SeedData.loadFile()
        for (l in seed.livestock) assertNotNull("species ${l.speciesId}", db[l.speciesId])
    }

    @Test
    fun tankSizeWarningsForTangsAndEngineerGobyOnly() {
        val outgrow = db.all.filter { db.outgrowsTank(it.id, 60.0) }.map { it.id }.toSet()
        assertEquals(setOf("paracanthurus_hepatus", "pholidichthys_leucotaenia", "zebrasoma_flavescens", "zebrasoma_xanthurum"), outgrow)
        assertNull(db["echidna_nebulosa"])
    }

    @Test
    fun engineerGobyIsPeacefulBurrower() {
        val goby = db.require("pholidichthys_leucotaenia")
        assertEquals("models/engineer_goby.glb", goby.model)
        assertEquals("species_pholidichthys_leucotaenia", goby.cardDrawable)
        assertTrue(goby.careNotes.none { it.contains("predator", ignoreCase = true) })
        for (other in db.all) {
            val e = db.interactions.entry("pholidichthys_leucotaenia", other.id) ?: continue
            assertNull("${other.id}: goby is not a threat", e.threat)
            assertTrue("${other.id}: ${e.risk}", db.interactions.risk("pholidichthys_leucotaenia", other.id).level <= Risk.LOW.level)
        }
        val seed = SeedData.loadFile()
        assertTrue(seed.livestock.any { it.speciesId == "pholidichthys_leucotaenia" })
        assertTrue(seed.livestock.none { it.speciesId == "echidna_nebulosa" })
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
        assertEquals(Risk.LOW, m.risk("pholidichthys_leucotaenia", "sphaeramia_nematoptera"))
        assertEquals(Risk.LOW, m.risk("sphaeramia_nematoptera", "pholidichthys_leucotaenia"))
        assertEquals(Risk.HIGH, m.risk("ecsenius_stigmatura", "salarias_fasciatus"))
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
