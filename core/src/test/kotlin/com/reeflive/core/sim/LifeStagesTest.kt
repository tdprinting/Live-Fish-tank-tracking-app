package com.reeflive.core.sim

import com.reeflive.core.ai.GobyBrain
import com.reeflive.core.geom.Rng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Life stages and sex roles (docs/research/life_stages.md, LifeStages.kt). */
class LifeStagesTest {
    private val prod = SpeciesCatalog.fromClasspath(
        listOf("amphiprion_ocellaris", "paracanthurus_hepatus", "sphaeramia_nematoptera", "labroides_dimidiatus", "pholidichthys_leucotaenia",
            "cirrhitichthys_falco", "salarias_fasciatus", "ecsenius_stigmatura", "pseudochromis_indigo", "valenciennea_puellaris",
            "macropharyngodon_bipartitus"),
    )

    private fun org(sid: String, cm: Double, id: String = "x", role: String? = null, cat: SpeciesCatalog = prod) =
        Organism(id, cat[sid], cm, id, role, Rng(id.hashCode().toLong()))

    private fun days(sim: Simulation, n: Int) {
        repeat(n * 24) {
            for (o in sim.organisms) if (o.alive) { o.hunger = 0.2; o.stress = 0.0 }
            sim.advanceAbstract(3600.0)
        }
    }

    @Test
    fun everyTankFishSheetCarriesItsLifeStages() {
        for (sp in prod.all()) {
            assertNotNull("${sp.id} lifeStages", sp.lifeStages)
            val ls = sp.lifeStages!!
            assertTrue("${sp.id} has ≥ 2 stages", ls.stages.size >= 2)
            assertEquals("${sp.id} first stage starts at 0", 0.0, ls.stages.minOf { it.fromCm }, 0.0)
        }
        assertEquals(SexSystem.PROTANDROUS, prod["amphiprion_ocellaris"].lifeStages!!.system)
        assertEquals(SexSystem.PROTOGYNOUS, prod["labroides_dimidiatus"].lifeStages!!.system)
        assertEquals(SexSystem.UNKNOWN, prod["pholidichthys_leucotaenia"].lifeStages!!.system)
        assertEquals(SexSystem.GONOCHORISTIC, prod["paracanthurus_hepatus"].lifeStages!!.system)
        // blue star leopard wrasse: juvenile, initial-phase female from 4 cm, terminal male by a 14-day change
        val leo = prod["macropharyngodon_bipartitus"].lifeStages!!
        assertEquals("male", leo.maleForm)
        assertEquals(SexSystem.PROTOGYNOUS, leo.system)
        assertEquals("female", leo.stageAt(8.5).formName)
        assertEquals("juvenile", leo.stageAt(3.0).id)
        assertEquals(14.0, leo.sexChangeDays, 0.0)
    }

    @Test
    fun engineerGobyStagesFollowLength() {
        assertEquals("juvenile", LifeStages.stage(org("pholidichthys_leucotaenia", 4.0)).id)
        assertEquals("subadult", LifeStages.stage(org("pholidichthys_leucotaenia", 13.0)).id)
        assertEquals("adult", LifeStages.stage(org("pholidichthys_leucotaenia", 25.0)).id)
        // the goby brain's adult threshold agrees with the sheet
        assertEquals(GobyBrain.ADULT_CM, prod["pholidichthys_leucotaenia"].lifeStages!!.stages.first { it.id == "adult" }.fromCm, 0.0)
        // a striped juvenile is stubbier than the eel-like adult
        val j = LifeStages.form(org("pholidichthys_leucotaenia", 3.0))
        val a = LifeStages.form(org("pholidichthys_leucotaenia", 30.0))
        assertTrue(j.depthScale > 1.1f && a.depthScale < 0.9f)
        assertEquals(SexRole.UNSEXED, a.sex)
    }

    /** Growth carries a fish across a threshold as a smooth blend, never a pop. */
    @Test
    fun stageBlendIsGradual() {
        for (sid in listOf("pholidichthys_leucotaenia", "labroides_dimidiatus", "amphiprion_ocellaris", "paracanthurus_hepatus")) {
            val sp = prod[sid].lifeStages!!
            var prevKey = ""
            var prevMix = 0f
            var prevDepth = -1f
            var cm = 0.5
            while (cm < prod[sid].maxLengthCm) {
                val f = LifeStages.form(org(sid, cm))
                val key = f.fromForm + ">" + f.toForm
                // two stages that share a texture form (tang subadult -> adult): the mix draws nothing, only the shape blends
                val mixNow = if (f.fromForm == f.toForm) 0f else f.stageMix
                if (key == prevKey) assertTrue("$sid at $cm: mix jumped ${prevMix} -> ${mixNow}", abs(mixNow - prevMix) < 0.08f)
                else if (prevKey.isNotEmpty()) {
                    // switching pairs only happens at the ends of a blend: the drawn texture is the same on both sides
                    val before = prevKey.substringAfter('>').takeIf { prevMix > 0.95f } ?: prevKey.substringBefore('>')
                    assertEquals("$sid at $cm", before, f.fromForm)
                    assertTrue("$sid at $cm starts unmixed", mixNow < 0.05f)
                }
                assertTrue("$sid at $cm: shape is continuous", abs(f.depthScale - prevDepth) < 0.01f || prevDepth < 0f)
                prevKey = key; prevMix = mixNow; prevDepth = f.depthScale
                cm += 0.05
            }
            assertTrue(sp.blendCm > 0)
        }
    }

    /** Sandbox fish added at the juvenile stock size (0.3 × typical adult, floor 3 cm) read as juveniles. */
    @Test
    fun juvenileStockSizeIsAJuvenileStage() {
        for (sp in prod.all()) {
            val cm = maxOf(3.0, 0.3 * sp.typicalAdultCm)
            val st = LifeStages.stage(org(sp.id, cm)).id
            // the engineer goby's stripes are already breaking up at ~10 cm (49-108 mm, life_stages.md §5)
            if (sp.id == "pholidichthys_leucotaenia") assertTrue(st == "juvenile" || st == "subadult")
            else assertEquals("${sp.id} at $cm cm", "juvenile", st)
        }
    }

    @Test
    fun teesFishMatchThePhotos() {
        val sim = TestTank.sim(DefaultStock.fish)
        val f = LifeFormChannel.of(sim.snapshot())
        assertEquals("subadult", f["goby"]!!.stage)        // [P p02]
        assertEquals("juvenile", f["tang"]!!.stage)        // 7.5 cm
        assertEquals("adult", f["wrasse"]!!.stage)
        assertEquals(SexRole.FEMALE, f["clown_f"]!!.sex)
        assertEquals(SexRole.MALE, f["clown_m"]!!.sex)
        assertTrue(f.values.all { it.stageMix in 0f..1f })
    }

    @Test
    fun hawkfishHaremDominantFemaleBecomesMaleAndALoneMaleStays() {
        val seeds = listOf(5.5, 6.5, 5.0).mapIndexed { i, cm -> LivestockSeed("cirrhitichthys_falco", "H$i", cm, id = "h$i") }
        val sim = TestTank.sim(seeds, seed = 3)
        days(sim, 10)
        val hs = sim.organisms.associateBy { it.id }
        assertEquals("all start female", 3, hs.values.count { it.sexFemale == true })
        assertEquals(SexRole.CHANGING_TO_MALE, LifeStages.sexRole(hs["h1"]!!))
        days(sim, 25)
        assertEquals(false, hs["h1"]!!.sexFemale)
        assertTrue(hs["h0"]!!.sexFemale == true && hs["h2"]!!.sexFemale == true)
        // a lone fish has no cue to change
        val lone = TestTank.sim(listOf(seeds[1]), seed = 3)
        days(lone, 40)
        assertEquals(true, lone.organisms.single().sexFemale)
        assertEquals(0.0, lone.organisms.single().sexChange, 0.0)
    }

    /** Bidirectional (Wittenrich & Munday 2005): two dottyback males, no female: the smaller turns female. */
    @Test
    fun twoDottybackMalesTheSmallerReverts() {
        val seeds = listOf(LivestockSeed("pseudochromis_indigo", "A", 6.5, role = "MALE", id = "d0"),
            LivestockSeed("pseudochromis_indigo", "B", 5.5, role = "MALE", id = "d1"))
        val sim = TestTank.sim(seeds, seed = 5)
        days(sim, 40)
        val d = sim.organisms.associateBy { it.id }
        assertEquals(false, d["d0"]!!.sexFemale)
        assertTrue("reverting ${d["d1"]!!.sexChange}", d["d1"]!!.sexFemale == false && d["d1"]!!.sexChange > 0.4)
        days(sim, 40)
        assertEquals(true, d["d1"]!!.sexFemale)
        assertEquals(false, d["d0"]!!.sexFemale)
    }

    @Test
    fun gonochoristicAndImmatureFishShowNoSexChange() {
        val sim = TestTank.sim(listOf(LivestockSeed("paracanthurus_hepatus", "T1", 20.0, id = "t1"), LivestockSeed("paracanthurus_hepatus", "T2", 18.0, id = "t2")))
        days(sim, 20)
        for (o in sim.organisms) { assertNull(o.sexFemale); assertEquals(0.0, o.sexChange, 0.0) }
        assertEquals(SexRole.IMMATURE, LifeStages.form(org("cirrhitichthys_falco", 2.5)).sex)
        assertFalse(LifeStages.mature(org("labroides_dimidiatus", 3.0)))
    }

    @Test
    fun cardLabelsReadStageAndSex() {
        assertEquals("Juvenile", LifeStages.describe(prod["paracanthurus_hepatus"], 6.0))
        assertEquals("Adult · Female", LifeStages.describe(prod["amphiprion_ocellaris"], 8.0, "Female"))
        assertEquals("Subadult (breaking stripes)", LifeStages.describe(prod["pholidichthys_leucotaenia"], 13.0))
    }
}
