package com.reeflive.core.geom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class EyeColliderTest {
    /** A rock wall 4 cm thick across the tank at x 40..44, nothing else. */
    private val wall = object : Sdf {
        override fun dist(p: Vec3): Double = distAt(p.x, p.y, p.z)
        override fun distAt(x: Double, y: Double, z: Double): Double = maxOf(40.0 - x, x - 44.0)
        override fun cover(p: Vec3) = 0.0
    }

    private fun collider() = EyeCollider().apply {
        sdf = wall
        rockClearance = 3f; sandClearance = 1.5f; boxClearance = 1f; bodyClearance = 1f
    }

    @Test fun moveStopsBeforeAThinWallInsteadOfPoppingThrough() {
        val c = collider()
        val p = floatArrayOf(30f, 30f, 16f)
        // one big frame's move straight through the wall to the far side
        c.move(p, 60f, 30f, 16f)
        assertTrue("stopped on the near side, ${c.rockClearance} cm off the rock: ${p[0]}", p[0] <= 40f - c.rockClearance + 0.05f)
        assertTrue("but went as far as it could: ${p[0]}", p[0] > 36.5f)
    }

    @Test fun blockedMovesSlideAlongTheSurface() {
        val c = collider()
        val p = floatArrayOf(36f, 30f, 10f)
        c.move(p, 50f, 30f, 25f)
        assertTrue("kept off the rock: ${p[0]}", p[0] <= 37.05f)
        assertEquals("slid the whole way along it", 25f, p[2], 0.2f)
    }

    @Test fun pushOutClearsSandBoxesAndBodies() {
        val c = collider().apply { sdf = null }
        val p = floatArrayOf(60f, 4f, 16f) // in the sand shell at the middle of the bed
        c.pushOut(p)
        assertTrue("above the sand by its clearance: ${p[1]}", p[1] >= c.sandY(60f, 16f) + c.sandClearance - 1e-3f)
        c.addBox(10f, 10f, 10f, 20f, 20f, 20f)
        val q = floatArrayOf(12f, 15f, 15f) // inside the box, nearest the x = 10 face
        c.pushOut(q)
        assertEquals(10f - c.boxClearance, q[0], 1e-3f)
        c.addCapsule(70f, 30f, 16f, 80f, 30f, 16f, 1.5f)
        val f = floatArrayOf(75f, 31f, 16f)
        c.pushOut(f)
        assertTrue("off the fish body: ${f[1]}", f[1] - 30f >= 1.5f + c.bodyClearance - 1e-3f)
        assertTrue(c.free(f[0], f[1], f[2]) >= -1e-3f)
    }

    @Test fun castSkipsTheAnimalItStartsInAndStopsBeforeTheNext() {
        val c = collider().apply { sdf = null }
        c.addCapsule(9f, 30f, 16f, 11f, 30f, 16f, 1f, owner = 1)   // the followed fish, the ray starts in it
        c.addCapsule(20f, 28f, 16f, 20f, 32f, 16f, 1f, owner = 0)  // another fish across the line of sight
        val t = c.cast(10f, 30f, 16f, 1f, 0f, 0f, 30f, ignoreOwner = 1)
        assertEquals(20f - 1f - c.bodyClearance, 10f + t, 0.05f)
        // nothing in the way: the whole length
        assertEquals(30f, c.cast(10f, 40f, 16f, 1f, 0f, 0f, 30f, ignoreOwner = 1), 1e-3f)
    }

    @Test fun clearanceCoversTheNearPlaneCorners() {
        // the widest viewport the app uses (landscape ~2.4:1) at the largest near plane (1 cm)
        val corner = EyeCollider.nearCornerDistance(1f, 50f, 2.4f)
        val t = kotlin.math.tan(25.0 * kotlin.math.PI / 180.0).toFloat()
        assertEquals(sqrt(1f + t * t * (1f + 2.4f * 2.4f)), corner, 1e-4f)
        assertTrue("3 cm rock clearance > near-plane corner $corner", 3f > corner)
    }

    /** The eye's sand floor is the sim's live bed: base slope plus the dug / mounded grid from the snapshot. */
    @Test fun sandFollowsTheLiveSandBedSurface() {
        val bed = com.reeflive.core.sim.SandBed(121.9, 33.0, 4.0, 7L)
        bed.dig(40.0, 20.0, 30.0, 3.0)
        bed.deposit(80.0, 10.0, 40.0, 3.0)
        val g = bed.gridSnapshot()
        val c = EyeCollider().apply {
            lengthX = 121.9f; depthZ = 33f
            sandFront = com.reeflive.core.sim.SandBed.BASE_FRONT.toFloat(); sandBack = com.reeflive.core.sim.SandBed.BASE_BACK.toFloat()
            setSandGrid(g.height, g.nx, g.nz, g.cellXCm, g.cellZCm)
        }
        for ((x, z) in listOf(40.0 to 20.0, 80.0 to 10.0, 10.0 to 30.0, 100.0 to 2.0)) {
            assertEquals("x=$x z=$z", bed.surfaceY(x, z).toFloat(), c.sandY(x.toFloat(), z.toFloat()), 0.02f)
        }
    }
}
