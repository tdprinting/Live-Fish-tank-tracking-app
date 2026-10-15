package com.reeflive.app.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The sand bed's load-time refinement (Mesh.subdivide): top split, skirt cut along the shared edge, no cracks. */
class SandMeshTest {
    private fun vert(x: Float, y: Float, z: Float) = floatArrayOf(x, y, z, 0f, 1f, 0f, x / 10f, z / 10f, 1f, 1f, 1f, 1f)

    @Test fun topIsSplitAndTheSkirtFollowsWithoutTJunctions() {
        // a top triangle (y 4) and a skirt triangle hanging from its front edge (down to y −0.5)
        val v = vert(0f, 4f, 10f) + vert(10f, 4f, 10f) + vert(0f, 4f, 0f) + vert(0f, -0.5f, 10f)
        val idx = intArrayOf(0, 1, 2, 0, 3, 1)
        val (nv, ni) = Mesh.subdivide(v, idx) { _, y, _ -> y > 0.5f }
        val st = GlbPrimitive.STRIDE
        assertEquals("4 corners + 3 midpoints", 7, nv.size / st)
        assertEquals("4 top triangles + the skirt cut in two", 6, ni.size / 3)
        // the midpoint of the shared edge (0,4,10)-(10,4,10) is used by the skirt too
        val mid = (0 until nv.size / st).first { nv[it * st] == 5f && nv[it * st + 1] == 4f && nv[it * st + 2] == 10f }
        val skirtTris = (0 until ni.size / 3).filter { t -> (0..2).any { ni[t * 3 + it] == 3 } }
        assertEquals(2, skirtTris.size)
        assertTrue("the skirt meets the split edge at its midpoint", skirtTris.all { t -> (0..2).any { ni[t * 3 + it] == mid } })
        // normals stay unit, uv averaged
        assertEquals(1f, nv[mid * st + 4], 1e-6f)
        assertEquals(0.5f, nv[mid * st + 6], 1e-6f)
    }
}
