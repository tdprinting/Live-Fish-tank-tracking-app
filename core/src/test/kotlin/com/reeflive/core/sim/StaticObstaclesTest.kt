package com.reeflive.core.sim

import com.reeflive.core.geom.Rng
import com.reeflive.core.geom.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The coral, decor and equipment obstacles ([StaticObstacles]): data, push semantics, soft coral, contacts and rebuilds. */
class StaticObstaclesTest {
    private fun sim(stock: List<LivestockSeed> = DefaultStock.all) =
        Simulation(TankConfig(seed = 5), TestTank.catalog, TestTank.rock.forSimulation(), 5, 11 * 3600.0, WaterState(), stock)

    private fun ready(s: Simulation): StaticObstacles = s.obstacles.also { it.sync(s) }

    private fun capsulesOf(o: StaticObstacles, owner: String): List<DoubleArray> =
        (0 until o.size).filter { o.ownerOf(it).contains(owner) }.map { o.capsule(it) }

    @Test
    fun everyColonyDecorAndEquipmentIsModelled() {
        val s = sim()
        val o = ready(s)
        for (c in s.corals) assertTrue("no obstacle for ${c.id}", capsulesOf(o, "coral ${c.id} ").isNotEmpty())
        // coral is soft, the equipment, hard decor and the duster are not
        for (i in 0 until o.size) {
            val name = o.ownerOf(i)
            val coral = name.startsWith("coral ") || name.startsWith("reef_details MushroomsRed")
            assertEquals(name, coral, o.isSoft(i))
        }
        for (part in listOf("equipment Heater", "equipment IntakeTube", "equipment PowerheadRight", "reef_details MushroomsRed", "duster duster tube", "duster duster crown"))
            assertTrue("no obstacle for $part", capsulesOf(o, part).isNotEmpty())
        // nothing outside the water box
        for (i in 0 until o.size) {
            val c = o.capsule(i)
            assertTrue(o.ownerOf(i), c[0] in -1.0..123.0 && c[2] in -1.0..34.0 && c[1] < 60.0)
        }
        println("StaticObstacles: ${o.size} capsules")
    }

    @Test
    fun pushReturnsTheVectorThatClearsTheSphere() {
        val s = sim()
        val o = ready(s)
        // the heater: hard, no give; a 1 cm sphere at a capsule's axis end is pushed out to touch it
        val h = capsulesOf(o, "equipment Heater").maxByOrNull { it[6] }!!
        val p = Vec3(h[0], h[1], h[2]) + Vec3(0.2, 0.0, 0.3)
        assertTrue(o.depth(p, 1.0) > 0)
        var q = p
        repeat(12) { o.push(q, 1.0)?.let { d -> q += d } }
        assertTrue("still inside by ${o.depth(q, 1.0)} (${o.lastOwner})", o.depth(q, 1.0) <= 1e-3)
        // a single contact: p + push lies exactly on the surface (depth 0)
        val lone = Vec3(60.0, 50.0, 25.0)
        assertTrue(o.depth(lone, 0.5) < 0)
        assertNull(o.push(lone, 0.5))
        val far = Vec3(h[0], h[1], h[2]) + Vec3(h[6] + 0.5 + 2.0, 0.0, 0.0)
        val near = Vec3(h[0], h[1], h[2]) + Vec3(h[6] + 0.5 - 0.1, 0.0, 0.0)
        if (o.depth(far, 0.5) < 0) {
            val d = o.push(near, 0.5)
            assertNotNull(d)
            assertTrue("push ${d!!} should point out +x", d.x > 0)
        }
    }

    @Test
    fun coralIsSoftForEveryoneTheEquipmentStaysSolid() {
        val s = sim()
        val o = ready(s)
        fun org(id: String) = s.organisms.first { it.id == id }
        val clown = org("clown_f"); val shrimp = org("skunk"); val tang = org("tang")
        assertEquals(0, StaticObstacles.passMask(clown))
        assertEquals(StaticObstacles.PASS_SMALL, StaticObstacles.passMask(shrimp))
        assertEquals(0, StaticObstacles.passMask(tang))
        // the clowns' host and the Kenya: no push and no depth for anyone (the colony bends)
        for (owner in listOf("(mushroom_hairy)", "(kenya)")) {
            val c = capsulesOf(o, owner).maxByOrNull { it[6] }!!
            val p = Vec3(c[0], c[1], c[2])
            for (who in listOf(clown, shrimp, tang)) {
                assertTrue("$owner pushes ${who.id}", o.push(p, 0.5, StaticObstacles.passMask(who)) == null && o.softTouched)
                assertTrue("$owner solid to ${who.id}", o.depth(p, 0.5, StaticObstacles.passMask(who)) <= 0)
            }
        }
        // the heater stays solid for the clown too
        val h = capsulesOf(o, "equipment Heater").maxByOrNull { it[6] }!!
        val hp = Vec3(h[0], h[1], h[2])
        assertTrue(o.depth(hp, 0.5, StaticObstacles.passMask(clown)) > 0)
        assertNotNull(o.push(hp, 0.5, StaticObstacles.passMask(clown)))
    }

    @Test
    fun coralContactsAreListedPerAnimalAndColonyAndClearedEachTick() {
        val s = sim()
        val o = ready(s)
        val k = capsulesOf(o, "(kenya)").maxByOrNull { it[6] }!!
        val kp = Vec3(k[0], k[1], k[2])
        val kenyaId = o.ownerOf((0 until o.size).first { o.ownerOf(it).contains("(kenya)") }).removePrefix("coral ").substringBefore(" (")
        assertNull(o.push(kp, 0.5, 0, "tang"))
        o.push(kp + Vec3(0.1, 0.0, 0.0), 0.5, 0, "tang")   // the same animal and colony again: one entry, the deeper one
        o.push(kp, 0.5, 0, "clown_f")
        assertEquals(2, o.contacts.size)
        val c = o.contacts.first { it.organismId == "tang" }
        assertEquals(kenyaId, c.coralId)
        assertTrue("depth ${c.depthCm}", c.depthCm >= k[6] + 0.5 - 1e-6)
        assertTrue("point on the colony's surface", c.point.distanceTo(kp) <= k[6] + 1e-6)
        // the next tick starts empty, and the entries are reused (no allocation per tick)
        val first = o.contacts[0]
        o.sync(s)
        assertEquals(0, o.contacts.size)
        o.push(kp, 0.5, 0, "tang")
        assertTrue(first === o.contacts[0])
        // in the running sim: a swimmer's stations in a colony are reported, and nothing is pushed off coral
        var seen = 0
        repeat(2400) { s.advance(0.05, 0.05); seen += s.obstacles.contacts.size }
        println("StaticObstacles: $seen coral contacts in 2 min")
    }

    @Test
    fun rebuildsWhenColoniesChangeAndScalesTheDusterCrown() {
        val s = sim()
        val o = ready(s)
        val n0 = o.size
        val r0 = o.rebuilds
        o.sync(s); o.sync(s)
        assertEquals("no change, no rebuild", r0, o.rebuilds)
        // a sandbox copy of the organ pipe 30 cm to the right: new capsules there
        val pipe = s.corals.first { it.id == "organpipe" }
        s.addFromSeed(LivestockSeed(pipe.species.id, "copy", placement = pipe.anchor, id = "pipe2", offset = Vec3(30.0, 0.0, 0.0)))
        o.sync(s)
        assertEquals(r0 + 1, o.rebuilds)
        val copy = capsulesOf(o, "coral pipe2 ")
        val orig = capsulesOf(o, "coral organpipe ")
        assertEquals(orig.size, copy.size)
        assertEquals(orig[0][0] + 30.0, copy[0][0], 1e-9)
        // removed again
        s.corals.removeAll { it.id == "pipe2" }
        o.sync(s)
        assertEquals(n0, o.size)
        // the duster's crown follows its extension (retracted: the crown is gone, the tube stays)
        val duster = s.organisms.first { it.brainKind == BrainKind.FEATHER_DUSTER }
        val crown = (0 until o.size).filter { o.ownerOf(it).endsWith("crown") }
        duster.extension = 1.0; o.sync(s)
        val full = o.capsule(crown[0])[6]
        duster.extension = 0.0; o.sync(s)
        assertEquals(0.0, o.capsule(crown[0])[6], 1e-12)
        assertTrue(full > 1.0)
        assertTrue(capsulesOf(o, "tube").all { it[6] > 0.5 })
    }

    @Test
    fun queriesAreCheap() {
        val s = sim()
        val o = ready(s)
        val rng = Rng(3)
        val pts = Array(4096) { Vec3(rng.nextDouble() * 121.9, 3 + rng.nextDouble() * 53, rng.nextDouble() * 33) }
        var hits = 0
        repeat(20) { for (p in pts) if (o.push(p, 1.0) != null) hits++ }
        val t0 = System.nanoTime()
        repeat(100) { for (p in pts) if (o.push(p, 1.0) != null) hits++ }
        val ns = (System.nanoTime() - t0).toDouble() / (100 * pts.size)
        println("StaticObstacles: %.0f ns per push (r = 1 cm), %.1f %% of random water points in contact".format(ns, 100.0 * hits / (120 * pts.size)))
        assertTrue("push costs $ns ns", ns < 5000)
    }
}
