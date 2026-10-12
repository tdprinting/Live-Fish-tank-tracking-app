package com.reeflive.core.sim

import org.junit.Assert.assertTrue
import org.junit.Test

/** Track A's production species sheets (core resources `species/`) load into the simulation. */
class ProductionSheetsTest {
    private val ids = listOf(
        "amphiprion_ocellaris", "paracanthurus_hepatus", "sphaeramia_nematoptera", "labroides_dimidiatus", "pholidichthys_leucotaenia",
        "cirrhitichthys_falco", "salarias_fasciatus", "ecsenius_stigmatura", "pseudochromis_indigo", "stenopus_hispidus",
        "lysmata_amboinensis", "clibanarius_tricolor", "paguristes_cadenati", "ophiocoma_sp", "sabellastarte_sp", "nassarius_sp",
        "lithopoma_tectum", "asterina_sp", "capnella_sp", "briareum_sp", "zoanthus_sp", "clavularia_sp", "discosoma_sp",
        "gracilaria_sp", "tubipora_musica",
    )

    @Test
    fun sheetsParseAndResolveBrains() {
        val cat = SpeciesCatalog.fromClasspath(ids)
        val missing = ids.filter { !cat.has(it) }
        assertTrue("sheets that did not parse: $missing", missing.isEmpty())
        val generic = cat.all().filter { !it.isCoral && (it.brainKind == BrainKind.GENERIC_FISH || it.brainKind == BrainKind.GENERIC_INVERT) }
        assertTrue("animals without a brain: ${generic.map { it.id + "/" + it.brain }}", generic.isEmpty())
        assertTrue("goby brain", cat["pholidichthys_leucotaenia"].brainKind == BrainKind.ENGINEER_GOBY)
    }

    @Test
    fun productionStockRunsADay() {
        val cat = SpeciesCatalog.fromClasspath(ids)
        val stock = DefaultStock.all.map { s ->
            when (s.speciesId) {
                "astraea_tecta" -> s.copy(speciesId = "lithopoma_tectum")
                "nassarius_vibex" -> s.copy(speciesId = "nassarius_sp")
                "ophiocoma_erinaceus" -> s.copy(speciesId = "ophiocoma_sp")
                else -> s
            }
        }
        val sim = Simulation(TankConfig(), cat, TestTank.rock, 1L, 8 * 3600.0, livestock = stock)
        sim.advanceAbstract(86400.0)
        val dead = sim.eventLog.filterIsInstance<SimEvent.Died>()
        assertTrue("deaths with production sheets: $dead", dead.isEmpty())
    }
}
