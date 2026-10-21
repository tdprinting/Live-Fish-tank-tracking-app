package com.reeflive.core.custom.economy

import com.reeflive.core.custom.CustomTank
import com.reeflive.core.custom.TankBuilds
import com.reeflive.core.sim.Command
import com.reeflive.core.sim.SpeciesCatalog
import com.reeflive.core.sim.TestTank
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A store regular's swap offer (StoreTrades) and the people who shop at each store (StoreLife). */
class StoreTradesTest {
    private val day = "2026-10-21"
    private val zoa = "zoanthus_sp"

    private fun tank(coralSize: Double): com.reeflive.core.sim.Simulation {
        val sim = CustomTank.fresh(TankBuilds.LONG_REEF, SpeciesCatalog.fromClasspath(listOf(zoa)), TestTank.rock, 12 * 3600.0, 4L)
        sim.coralPicks.byHand = false
        sim.enqueue(Command.AddMany(zoa, 1))
        sim.stepWall(0.0)
        val c = sim.corals.first { it.species.id == zoa }
        c.size = coralSize
        return sim
    }

    private fun keeper() = Economy(SavedEconomy(stars = Levels.starsFor(6)))

    @Test fun aGrownCoralGetsAFragTradeForSomethingNew() {
        val sim = tank(1.3)
        val e = keeper()
        val o = StoreTrades.offer(FishStore.FRAG_SHACK, sim, e, day)
        assertNotNull(o); o!!
        assertEquals("Mia", o.trader)
        assertTrue(o.giveFrag)
        assertTrue(o.line.contains(o.getName))
        // something the keeper can keep at their level and doesn't have yet
        val l = PriceBook.listing(o.getSpecies)!!
        assertTrue(e.unlocked(l) && l.species != zoa)
        val before = sim.corals.first().size
        StoreTrades.apply(sim, o)
        assertEquals(before * (1 - StoreTrades.FRAG_CUT), sim.corals.first().size, 1e-9)
        // one swap a store a day
        e.traded(o, day)
        assertTrue(e.tradedToday(FishStore.FRAG_SHACK, day))
        assertNull(StoreTrades.offer(FishStore.FRAG_SHACK, sim, e, day))
        assertNotNull(StoreTrades.offer(FishStore.FRAG_SHACK, sim, e, "2026-10-22"))
    }

    @Test fun smallColoniesAreNeverAskedFor() {
        assertNull(StoreTrades.offer(FishStore.FRAG_SHACK, tank(0.6), keeper(), day))
        // a store the keeper hasn't opened yet doesn't trade
        assertNull(StoreTrades.offer(FishStore.DEEP_REEF, tank(1.3), Economy(), day))
    }

    @Test fun everyStoreHasItsOwnStaffRegularsAndRoutines() {
        val names = HashSet<String>()
        for (store in FishStore.entries) {
            val cast = StoreLife.cast(store)
            assertEquals(store, cast.store)
            assertNotNull("${store.id} trader", cast.trader)
            assertTrue(cast.people.any { it.role == Role.CLERK })
            // slots are the model's P<slot> nodes, in order
            assertEquals(cast.people.indices.toList(), cast.people.map { it.slot })
            for (p in cast.people) {
                assertTrue("${p.name} is new", names.add(p.name))
                assertTrue(p.routines.isNotEmpty() && p.hello.isNotEmpty())
                assertFalse(p.routines.any { it.steps.isEmpty() || it.weight <= 0 })
            }
            assertTrue(cast.noTapping.isNotEmpty() && cast.tradeYes.isNotEmpty() && cast.tradeNo.isNotEmpty())
            // the clerk's name greets the keeper at the door
            assertTrue(store.greeting.startsWith(cast.clerk.name))
        }
    }
}
