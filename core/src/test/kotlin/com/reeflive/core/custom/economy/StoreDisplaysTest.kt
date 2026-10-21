package com.reeflive.core.custom.economy

import com.reeflive.core.sim.SpeciesCatalog
import com.reeflive.core.sim.Stocking
import com.reeflive.core.sim.TestTank
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The stores' display tanks (StoreDisplays): each section's own tank, rockwork, light and frag racks. */
class StoreDisplaysTest {
    private val day = "2026-10-20"

    @Test fun storesRunTheirOwnMixOfTanks() {
        for (d in listOf(day, "2026-10-21", "2026-10-22")) {
            val kinds = FishStore.entries.associateWith { s -> StoreVisit.tanks(s, d).map { it.setup.kind } }
            assertTrue(kinds[FishStore.FRAG_SHACK]!!.contains(DisplayKind.FRAG))
            assertEquals(DisplayKind.SHOWPIECE, kinds[FishStore.DEEP_REEF]!!.first())
            assertTrue(kinds[FishStore.BIG_BLUE]!!.toSet().size >= 2)
        }
    }

    @Test fun aFragTankHoldsItsCoralsOnPlugs() {
        val tank = StoreVisit.tanks(FishStore.FRAG_SHACK, day).first { it.setup.kind == DisplayKind.FRAG }
        val sim = StoreVisit.build(tank, SpeciesCatalog(), TestTank.rock, 5L)
        // the frag tank's own glass: 36 in long, 12 in tall
        assertEquals(91.4, sim.rock.lengthCm, 0.2)
        assertTrue(sim.rock.waterLevelCm < 30.0)
        assertTrue(sim.corals.isNotEmpty())
        for (c in sim.corals) assertTrue("${c.id} on a plug", StoreDisplays.plugs.any { it.distanceTo(c.pos) < 0.01 })
        for (it in tank.items) assertEquals(StoreVisit.shown(it), sim.corals.count { c -> c.species.id == it.listing.speciesId })
    }

    @Test fun aBuiltScapeStandsInTheWater() {
        val tank = StoreVisit.tanks(FishStore.SHELLYS, day).first { it.title == "Starter fish" }
        assertEquals(DisplayScape.ISLANDS, tank.setup.scape)
        val sim = StoreVisit.build(tank, SpeciesCatalog(), TestTank.rock, 3L)
        val built = sim.snapshot().builtRocks.orEmpty()
        assertEquals(8, built.size)
        // cured live rock, not white dry rock
        assertTrue(built.all { it.dryness < 0.01f })
        // the two islands stand up off the sand; the channel between them is open sand
        val r = sim.rock
        for (fx in doubleArrayOf(0.24, 0.74)) {
            val (p, _) = Stocking.surfaceSpot(r, fx * r.lengthCm, 0.45 * r.widthCm)!!
            assertTrue("island at $fx", p.y > r.sandTopCm + 8 && p.y < r.waterLevelCm)
        }
        assertTrue(sim.corals.all { it.pos.y < r.waterLevelCm })
    }
}
