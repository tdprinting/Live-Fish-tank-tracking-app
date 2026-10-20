package com.reeflive.core.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.reeflive.core.geom.Vec3

/** Sandbox "Add life" in bags: drop, float 30 sim minutes, tip out ([AcclimationBags], [Command.FloatBag]). */
class AcclimationBagTest {
    private fun sim(stock: List<LivestockSeed> = emptyList()): Simulation =
        Simulation(TankConfig(seed = 5L), SpeciesCatalog(), TestTank.rock, 5L, 12 * 3600.0, livestock = stock)

    private fun clowns(s: Simulation) = s.organisms.filter { it.species.id == "amphiprion_ocellaris" }

    @Test fun bagFloatsThirtyMinutesThenTheFishSwimOut() {
        val s = sim()
        s.enqueue(Command.FloatBag("amphiprion_ocellaris", 2, x = 60.0))
        repeat(20) { s.stepWall(0.05) }
        val snap = s.snapshot()
        assertEquals(1, snap.bags.size)
        val bag = snap.bags[0]
        assertEquals(1, bag.phase)                      // landed and floating
        assertTrue("still in the bag", clowns(s).isEmpty())
        assertEquals(2, snap.organisms.count { it.bagged })
        assertTrue("bag water starts cooler than the tank", bag.bagTempC < bag.tankTempC - 1)
        // inside the glass with the bulb clear of the rock
        val r = s.rock
        assertTrue(bag.x - bag.radius > 0 && bag.x + bag.radius < r.lengthCm && bag.z - bag.radius > 0 && bag.z + bag.radius < r.widthCm)
        assertTrue(r.sdf.dist(com.reeflive.core.geom.Vec3(bag.x.toDouble(), bag.y - 0.7 * bag.radius.toDouble(), bag.z.toDouble())) > bag.radius - 0.5)

        s.advance(25 * 60.0, behaviorDt = 0.5, fullBehavior = false)
        assertTrue("not yet at 25 min", clowns(s).isEmpty())
        val mid = s.snapshot().bags.single()
        assertTrue("water evened out", kotlin.math.abs(mid.bagTempC - mid.tankTempC) < 0.1)

        s.advance(6 * 60.0, behaviorDt = 0.5, fullBehavior = false)
        val out = clowns(s)
        assertEquals(2, out.size)
        assertTrue(out.all { it.alive && s.rock.inWater(it.pos) && it.id.contains("#bag") })
        s.advance(AcclimationBags.T_END + 1.0, behaviorDt = 0.05)
        val after = s.snapshot()
        assertTrue("bag lifted out", after.bags.isEmpty())
        assertEquals(0, after.organisms.count { it.bagged })
        assertEquals(2, after.stats.count { it.speciesId == "amphiprion_ocellaris" })
    }

    @Test fun fastForwardRunsTheClockUntilTheBagsOpen() {
        val s = sim()
        s.enqueue(Command.FloatBag("paracanthurus_hepatus", 1, x = 40.0))
        s.stepWall(0.05)
        s.enqueue(Command.HurryBags(true))
        s.stepWall(0.05)
        assertEquals(AcclimationBags.HURRY_SPEED, s.snapshot().speed, 1e-9)
        val t0 = s.simTimeSec
        // 30 sim minutes at 60x is 30 s of wall time
        repeat(20 * 32) { s.stepWall(0.05) }
        assertTrue(s.simTimeSec - t0 >= AcclimationBags.FLOAT_SEC)
        repeat(20 * 10) { s.stepWall(0.05) }
        assertEquals(1, s.organisms.count { it.species.id == "paracanthurus_hepatus" })
        assertEquals("back to the chosen speed", 1.0, s.snapshot().speed, 1e-9)
    }

    @Test fun manyAnimalsComeInSeveralBagsThatDoNotOverlap() {
        val s = sim()
        s.enqueue(Command.FloatBag("amphiprion_ocellaris", 20, x = 60.0))
        repeat(40) { s.stepWall(0.05) }
        val bags = s.snapshot().bags
        assertTrue(bags.size >= 4)
        assertEquals(20, bags.sumOf { it.count })
        for (i in bags.indices) for (j in i + 1 until bags.size) {
            val a = bags[i]; val b = bags[j]
            val d = kotlin.math.hypot((a.x - b.x).toDouble(), (a.z - b.z).toDouble())
            assertTrue("bags ${a.id} and ${b.id} overlap ($d)", d >= a.radius + b.radius - 0.05)
        }
    }

    @Test fun swimmersAreKeptOutOfAFloatingBag() {
        val s = sim(DefaultStock.all.filter { it.speciesId == "paracanthurus_hepatus" || it.speciesId == "amphiprion_ocellaris" })
        s.enqueue(Command.FloatBag("ecsenius_stigmatura", 1, x = 60.0))
        repeat(40) { s.stepWall(0.05) }
        val bag = s.snapshot().bags.single()
        // park a resident right in the bulb: the next behaviour step puts it outside
        val tang = s.organisms.first { it.species.id == "paracanthurus_hepatus" }
        tang.placeAt(com.reeflive.core.geom.Vec3(bag.x.toDouble(), bag.y - 0.7 * bag.radius.toDouble(), bag.z.toDouble()), com.reeflive.core.geom.Vec3.X)
        repeat(60) {
            s.stepWall(0.05)
            val b = s.snapshot().bags.single()
            val c = com.reeflive.core.geom.Vec3(b.x.toDouble(), b.y - 0.7 * b.radius.toDouble(), b.z.toDouble())
            for (o in s.organisms) if (o.alive && !o.attached) assertTrue("${o.id} inside the bag", o.pos.distanceTo(c) >= b.radius - 0.2)
        }
    }

    @Test fun crawlersSinkToTheBottomAndCoralsSettle() {
        val s = sim()
        s.enqueue(Command.FloatBag("clibanarius_tricolor", 3, x = 80.0))
        s.enqueue(Command.FloatBag("zoanthus_sp", 2, x = 30.0))
        repeat(20) { s.stepWall(0.05) }
        val corals0 = s.corals.size
        s.enqueue(Command.HurryBags(false, openNow = true))
        // emptied one bag at a time, then the hermits sink from the net
        repeat(20 * 24) { s.stepWall(0.05) }
        val hermits = s.organisms.filter { it.species.id == "clibanarius_tricolor" }
        assertEquals(3, hermits.size)
        assertTrue(hermits.all { s.rock.envDist(it.pos) < 2.0 })
        assertEquals(corals0 + 2, s.corals.size)
        assertTrue(s.snapshot().bags.isEmpty())
    }

    /** Spine points of a pose in a bag's own frame (cm). */
    private fun local(b: BagPose, x: Float, y: Float, z: Float): Vec3 {
        val m = DoubleArray(9)
        BagFrame.rotation(b.tiltX.toDouble(), b.tiltZ.toDouble(), b.yaw.toDouble(), m)
        val dx = x - b.x.toDouble(); val dy = y - b.y.toDouble(); val dz = z - b.z.toDouble()
        return Vec3(m[0] * dx + m[3] * dy + m[6] * dz, m[1] * dx + m[4] * dy + m[7] * dz, m[2] * dx + m[5] * dy + m[8] * dz)
    }

    private fun spine(p: OrganismPose) = (0 until p.spine.size / 3).map { Vec3(p.spine[it * 3].toDouble(), p.spine[it * 3 + 1].toDouble(), p.spine[it * 3 + 2].toDouble()) }

    private fun checkApart(snap: SimSnapshot, where: String) {
        val bagged = snap.organisms.filter { it.bagged }
        for (i in bagged.indices) for (j in i + 1 until bagged.size) {
            val a = spine(bagged[i]); val q = spine(bagged[j])
            val d = a.minOf { p -> q.minOf { it.distanceTo(p) } }
            assertTrue("$where: ${bagged[i].id} and ${bagged[j].id} overlap (${"%.2f".format(d)} cm)", d > 0.25)
        }
    }

    @Test fun baggedBodiesStayInsideTheBagAndTheNetAndApart() {
        for ((sp, n) in listOf("amphiprion_ocellaris" to 6, "paracanthurus_hepatus" to 1, "clibanarius_tricolor" to 5, "lysmata_amboinensis" to 3)) {
            val s = sim()
            s.enqueue(Command.FloatBag(sp, n, x = 60.0))
            var sawNet = false
            repeat(20 * 20) { tick ->
                if (tick == 20 * 8) s.enqueue(Command.HurryBags(false, openNow = true))
                s.stepWall(0.05)
                val snap = s.snapshot()
                val b = snap.bags.firstOrNull() ?: return@repeat
                if (b.phase == 1 || (b.phase == 2 && b.step <= AcclimationBags.STEP_CUT)) {
                    val r = b.radius.toDouble()
                    for (p in snap.organisms.filter { it.bagged }) for (q in spine(p)) {
                        val l = local(b, q.x.toFloat(), q.y.toFloat(), q.z.toFloat())
                        val out = l.distanceTo(Vec3(0.0, -AcclimationBags.BULB_C * r, 0.0)) - 0.97 * r
                        assertTrue("$sp ${p.id} pokes ${"%.2f".format(out)} cm out of the bag", out < 0.0)
                    }
                    checkApart(snap, "$sp in the bag")
                }
                if (b.step == AcclimationBags.STEP_NET_IN) {
                    sawNet = true
                    // in the net's mesh bag (a ball under the hoop): nobody pokes through
                    val f = Vec3(b.netFx.toDouble(), b.netFy.toDouble(), b.netFz.toDouble())
                    val c = Vec3(b.netX.toDouble(), b.netY.toDouble(), b.netZ.toDouble()) - f * (AcclimationBags.NET_C * b.netR)
                    for (p in snap.organisms.filter { it.bagged }) for (q in spine(p)) {
                        val out = q.distanceTo(c) - b.netR
                        assertTrue("$sp ${p.id} pokes ${"%.2f".format(out)} cm out of the net", out < 0.0)
                    }
                    checkApart(snap, "$sp in the net")
                }
            }
            assertTrue("$sp went through the net", sawNet)
        }
    }

    @Test fun baggedBrittleStarLiesFlatWithItsWholeSpanInsideTheBag() {
        val s = sim()
        s.enqueue(Command.FloatBag("ophiocoma_erinaceus", 2, x = 60.0))
        repeat(20 * 5) { s.stepWall(0.05) }
        val snap = s.snapshot()
        val b = snap.bags.single()
        val stars = snap.organisms.filter { it.bagged }
        assertEquals(2, stars.size)
        for (p in stars) {
            // no arm hangs down: every step height (the x slots) is at or above the disc plane (0.5)
            for (k in 0 until BrittleArms.ARMS) for (j in 0 until 8) {
                val h = p.limbs[k * 32 + 4 * j]
                assertTrue("arm $k step $j hangs ($h)", h >= 0.5f - 1e-4f)
            }
            val h = p.headPos()
            val l = local(b, h.x.toFloat(), h.y.toFloat(), h.z.toFloat())
            val out = l.distanceTo(Vec3(0.0, -AcclimationBags.BULB_C * b.radius, 0.0)) - 0.97 * b.radius
            assertTrue("star centre outside the bag ($out)", out < 0.0)
        }
    }

    /** The cut bag's outline (BagMesh.plasticCut, × radius): radius at height y. */
    private val cutProfile = floatArrayOf(
        0.02f, -1.7f, 0.5f, -1.62f, 0.82f, -1.42f, 0.97f, -1.1f, 1.0f, -0.72f, 0.97f, -0.38f,
        0.88f, -0.05f, 0.72f, 0.28f, 0.56f, 0.52f, 0.52f, 0.6f,
    )

    private fun cutRadius(y: Double): Double {
        for (i in 0 until cutProfile.size / 2 - 1) {
            val y0 = cutProfile[i * 2 + 1]; val y1 = cutProfile[i * 2 + 3]
            if (y in y0..y1) { val k = (y - y0) / (y1 - y0); return cutProfile[i * 2] + k * (cutProfile[i * 2 + 2] - cutProfile[i * 2]) }
        }
        return 0.0
    }

    @Test fun animalsLeaveTheCutBagThroughItsNeckNotItsShoulder() {
        for ((sp, n) in listOf("amphiprion_ocellaris" to 6, "paracanthurus_hepatus" to 1, "ophiocoma_erinaceus" to 3)) {
            val s = sim()
            s.enqueue(Command.FloatBag(sp, n, x = 60.0))
            repeat(20 * 2) { s.stepWall(0.05) }
            s.enqueue(Command.HurryBags(false, openNow = true))
            // a body point that was inside the plastic last frame must not be outside it now unless it left by the mouth
            val inside = HashMap<String, Boolean>()
            repeat(20 * 8) {
                s.stepWall(0.05)
                val snap = s.snapshot()
                val b = snap.bags.firstOrNull() ?: return@repeat
                if (b.phase != 2 || !b.cut || b.step > AcclimationBags.STEP_POUR) return@repeat
                val r = b.radius.toDouble()
                for (p in snap.organisms.filter { it.bagged }) for ((i, q) in spine(p).withIndex()) {
                    val l = local(b, q.x.toFloat(), q.y.toFloat(), q.z.toFloat())
                    val y = l.y / r
                    val radial = kotlin.math.sqrt(l.x * l.x + l.z * l.z) / r
                    val now = y < 0.6 && radial < cutRadius(y)
                    val key = "${p.id}/$i"
                    val was = inside[key]
                    if (was == true && !now) assertTrue("$sp ${p.id} point $i went through the plastic at y=${"%.2f".format(y)} radial=${"%.2f".format(radial)} (wall ${"%.2f".format(cutRadius(y))})", y >= 0.5 || radial - cutRadius(y) < 0.03)
                    inside[key] = now
                }
            }
        }
    }

    @Test fun bagWaterNeverReachesTheTank() {
        val s = sim()
        s.enqueue(Command.FloatBag("amphiprion_ocellaris", 2, x = 60.0))
        repeat(20) { s.stepWall(0.05) }
        s.enqueue(Command.HurryBags(false, openNow = true))
        var poured = false
        repeat(20 * 10) {
            s.stepWall(0.05)
            val b = s.snapshot().bags.firstOrNull() ?: return@repeat
            if (b.streamR > 0) {
                poured = true
                val w = s.rock.waterLevelCm
                assertTrue("the bag is out of the water when it pours", b.y - (AcclimationBags.BULB_C + 1) * b.radius > w)
                assertTrue("the bucket hangs clear of the water", b.bucketY - b.bucketH > w)
                assertTrue("it pours into the net", b.streamY1 > w && kotlin.math.abs(b.streamY1 - b.netY) < 0.01f)
                assertTrue(b.cut)
            }
        }
        assertTrue(poured)
        assertEquals(2, clowns(s).size)
    }
}
