package com.reeflive.app.render

import com.reeflive.app.ui.live.SupplyItem
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The shelf program's inputs from supply_shelf.glb: prop footprint slots, rack material tags, baked vertex AO. */
class SupplyShelfShadingTest {
    private val glb = GlbLoader.parse(File("src/main/assets/models/supply_shelf.glb").readBytes(), "supply_shelf")

    @Test fun everyPropHasItsOwnFootprintSlot() {
        // shelf.frag looks up a prop's footprint by row and slot (uFoot[28]); each prop must land in its own slot
        assertEquals(SupplyShelfScene.PROPS, SupplyItem.entries.size)
        val tmp = FloatArray(3)
        for (item in SupplyItem.entries) {
            val node = glb.nodes.first { it.name == SupplyShelfLayout.NODE_PREFIX + item.id }
            val box = floatArrayOf(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
            for (pr in node.primitives) for (cx in 0..1) for (cy in 0..1) for (cz in 0..1) {
                M.transformPoint(node.world, if (cx == 0) pr.bmin[0] else pr.bmax[0], if (cy == 0) pr.bmin[1] else pr.bmax[1], if (cz == 0) pr.bmin[2] else pr.bmax[2], tmp)
                for (k in 0 until 3) { box[k] = minOf(box[k], tmp[k]); box[k + 3] = maxOf(box[k + 3], tmp[k]) }
            }
            var base = 0
            for (r in 0 until item.row.ordinal) base += SupplyShelfLayout.ROW_COUNT[r]
            assertEquals("${item.id} footprint slot", base + item.slot, SupplyShelfScene.slotOf(box))
        }
    }

    @Test fun rackMaterialsAreTaggedAndPropsCarryAo() {
        val tags = glb.materials.mapNotNull { (it.extras["shelf"] as? JsonPrimitive)?.content }.toSet()
        for (t in listOf("board", "wood", "glass", "lamp")) assertTrue("a $t material", t in tags)
        // COLOR_0.r is baked ambient occlusion on the props (1 = open), never black
        var minAo = 1f
        var dark = 0
        var total = 0
        for (n in glb.nodes) if (n.name.startsWith(SupplyShelfLayout.NODE_PREFIX)) for (pr in n.primitives) {
            for (v in 0 until pr.vertexCount) {
                val ao = pr.vertices[v * GlbPrimitive.STRIDE + 8]
                minAo = minOf(minAo, ao)
                if (ao < 0.99f) dark++
                total++
            }
        }
        assertTrue("AO floor: $minAo", minAo >= 0.35f)
        assertTrue("props are occluded somewhere: $dark of $total", dark > total / 10)
    }
}
