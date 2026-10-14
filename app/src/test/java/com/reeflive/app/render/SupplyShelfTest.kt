package com.reeflive.app.render

import com.reeflive.app.ui.live.SupplyItem
import com.reeflive.app.ui.live.glassPointAt
import com.reeflive.app.ui.live.insideTank
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SupplyShelfTest {
    private fun settle(cam: Camera, seconds: Float, fps: Int = 60) {
        repeat((seconds * fps).toInt()) { cam.update(1f / fps, 1920, 1080, null, { null }) { } }
    }

    @Test fun cameraGlidesToTheShelfAndBack() {
        val cam = Camera()
        settle(cam, 0.5f)
        val tankEyeX = cam.eye[0]
        cam.lookAtShelf(true, instant = false)
        settle(cam, 0.3f)
        assertTrue("still travelling", cam.shelfMix in 0.05f..0.95f)
        settle(cam, 1.5f)
        assertEquals(1f, cam.shelfMix, 1e-4f)
        assertEquals(SupplyShelfLayout.START_X, cam.eye[0], 0.5f)
        assertEquals(SupplyShelfLayout.Z_FRONT + cam.shelfDistance, cam.eye[2], 0.5f)
        cam.lookAtShelf(false, instant = false)
        settle(cam, 3f)
        assertEquals(0f, cam.shelfMix, 1e-4f)
        assertEquals(tankEyeX, cam.eye[0], 1f)
    }

    @Test fun reduceMotionCutsStraightThere() {
        val cam = Camera()
        settle(cam, 0.2f)
        cam.lookAtShelf(true, instant = true)
        settle(cam, 1f / 60f)
        assertEquals(1f, cam.shelfMix, 0f)
        assertEquals(SupplyShelfLayout.START_X, cam.eye[0], 0.5f)
    }

    @Test fun dragLooksAlongTheShelfAndStaysOnIt() {
        val cam = Camera()
        settle(cam, 0.2f)
        cam.lookAtShelf(true, instant = true)
        settle(cam, 0.1f)
        cam.touching = true
        cam.orbit(-400f, 0f, 1080f) // finger moves left: look further right along the shelf
        settle(cam, 0.5f)
        assertTrue("moved right: ${cam.eye[0]}", cam.eye[0] > SupplyShelfLayout.START_X + 5f)
        repeat(40) { cam.orbit(-400f, 0f, 1080f); settle(cam, 1f / 60f) }
        cam.touching = false
        settle(cam, 2f)
        assertEquals(SupplyShelfLayout.LOOK_X_HI, cam.eye[0], 0.3f)
        // gestures that would move the tank camera do nothing on the shelf
        cam.pan(300f, 300f, 1080f); cam.zoom(2f); cam.rotate(1f)
        settle(cam, 0.5f)
        assertEquals(SupplyShelfLayout.LOOK_X_HI, cam.eye[0], 0.3f)
    }

    @Test fun rowFocusGlidesToTheRow() {
        val cam = Camera()
        cam.lookAtShelf(true, instant = true)
        cam.shelfFocusRow(2)
        settle(cam, 2f)
        val y = SupplyShelfLayout.rowLookY(2)
        assertEquals(y + cam.shelfDistance * Camera.SHELF_EYE_RISE, cam.eye[1], 0.3f)
    }

    @Test fun rayBoxHitsAndMisses() {
        val box = floatArrayOf(0f, 0f, 0f, 1f, 1f, 1f)
        val t = SupplyShelfScene.rayBox(floatArrayOf(0.5f, 0.5f, 5f), floatArrayOf(0f, 0f, -1f), box, 0f)
        assertNotNull(t); assertEquals(4f, t!!, 1e-4f)
        assertNull(SupplyShelfScene.rayBox(floatArrayOf(3f, 0.5f, 5f), floatArrayOf(0f, 0f, -1f), box, 0f))
        assertNotNull(SupplyShelfScene.rayBox(floatArrayOf(1.5f, 0.5f, 5f), floatArrayOf(0f, 0f, -1f), box, 0.6f))
    }

    @Test fun dropPointOnTheGlassInvertsTheProjection() {
        val cam = Camera()
        settle(cam, 0.5f)
        val proj = TankProjection()
        proj.publish(cam.viewProj, cam.proj, 1920, 1080)
        val px = FloatArray(3)
        assertTrue(proj.project(30f, 20f, 33.8f, px))
        val out = FloatArray(2)
        assertTrue(glassPointAt(proj, px[0], px[1], out))
        assertEquals(30f, out[0], 0.2f); assertEquals(20f, out[1], 0.2f)
        assertTrue(insideTank(out[0], out[1]))
        assertFalse(insideTank(-5f, 20f))
    }

    @Test fun everyShelfItemHasAPropInTheModel() {
        val glb = GlbLoader.parse(File("src/main/assets/models/supply_shelf.glb").readBytes(), "supply_shelf")
        val nodes = glb.nodes.map { it.name }.toSet()
        for (item in SupplyItem.entries) assertTrue("prop for ${item.id}", SupplyShelfLayout.NODE_PREFIX + item.id in nodes)
        // locked props are exactly the sandbox-only ones, and only while watching the real tank
        assertTrue(SupplyItem.lockedIds(true).isEmpty())
        assertEquals(SupplyItem.entries.filter { it.sandboxOnly }.map { it.id }.toSet(), SupplyItem.lockedIds(false))
        assertFalse(SupplyItem.GLASS_CLEANER.sandboxOnly)
        assertFalse(SupplyItem.WATER_CHANGE.sandboxOnly)
        assertTrue(SupplyItem.MOVE_ROCKS.sandboxOnly)
    }

    @Test fun nameTagSlotsMatchThePropsInTheModel() {
        // the on-screen name tags are laid out from SupplyShelfLayout (propX / slotWidth); every prop must sit in its
        // slot (centred, and no wider than it, so neighbouring tags and props never overlap) and the shelf has a sign
        val glb = GlbLoader.parse(File("src/main/assets/models/supply_shelf.glb").readBytes(), "supply_shelf")
        assertNotNull(glb.nodes.firstOrNull { it.name == SupplyShelfLayout.SIGN_NODE })
        for (r in SupplyShelfLayout.ROW_COUNT.indices) {
            assertEquals("items in row $r", SupplyShelfLayout.ROW_COUNT[r], SupplyItem.entries.count { it.row.ordinal == r })
        }
        val tmp = FloatArray(3)
        for (item in SupplyItem.entries) {
            val node = glb.nodes.first { it.name == SupplyShelfLayout.NODE_PREFIX + item.id }
            var lo = Float.MAX_VALUE
            var hi = -Float.MAX_VALUE
            for (pr in node.primitives) for (cx in 0..1) for (cz in 0..1) {
                M.transformPoint(node.world, if (cx == 0) pr.bmin[0] else pr.bmax[0], pr.bmin[1], if (cz == 0) pr.bmin[2] else pr.bmax[2], tmp)
                lo = minOf(lo, tmp[0]); hi = maxOf(hi, tmp[0])
            }
            val n = SupplyShelfLayout.ROW_COUNT[item.row.ordinal]
            assertEquals("${item.id} centre", SupplyShelfLayout.propX(item.slot, n), (lo + hi) / 2f, 1.5f)
            assertTrue("${item.id} fits its slot: ${hi - lo}", hi - lo <= SupplyShelfLayout.slotWidth(n))
            assertTrue("${item.id} has a tag", item.tag.isNotBlank())
        }
    }
}
