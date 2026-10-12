package com.reeflive.app.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/** Parses the committed GLBs (unit tests run with the module directory as the working dir). */
class GlbLoaderTest {
    private val models = File("src/main/assets/models")

    private fun load(name: String) = GlbLoader.parse(File(models, name).readBytes(), name)

    @Test fun everyModelParses() {
        val files = models.listFiles { f -> f.name.endsWith(".glb") }.orEmpty()
        assertTrue("assets present", files.size >= 20)
        for (f in files) {
            val m = GlbLoader.parse(f.readBytes(), f.name)
            assertTrue("${f.name} has geometry", m.triangles > 0)
            for (n in m.nodes) for (p in n.primitives) {
                assertEquals(0, p.vertices.size % GlbPrimitive.STRIDE)
                assertTrue(p.indices.all { it in 0 until p.vertexCount })
                assertTrue("${f.name}/${n.name} material", p.material < m.materials.size)
            }
            for (mat in m.materials) if (mat.baseColorImage >= 0) assertTrue(m.images[mat.baseColorImage].size > 8)
        }
    }

    @Test fun fishSpineCoordinateRunsSnoutToTail() {
        val m = load("tang.glb")
        assertEquals(6.5f, m.rootFloat("lengthCm")!!, 1e-3f)
        val body = m.nodes.first { it.part == "body" }
        val v = body.primitives.first().vertices
        var snoutA = 1f; var tailA = 0f; var minX = 1f; var maxX = -1f
        for (i in 0 until v.size / GlbPrimitive.STRIDE) {
            val x = v[i * GlbPrimitive.STRIDE]; val a = v[i * GlbPrimitive.STRIDE + 11]
            if (x > maxX) { maxX = x; snoutA = a }
            if (x < minX) { minX = x; tailA = a }
            // COLOR_0 normalized ushort → 0..1
            assertTrue(a in 0f..1f)
        }
        assertTrue("snout at +x", maxX > 0.3f)
        assertTrue("A ≈ 0 at the snout: $snoutA", snoutA < 0.05f)
        assertTrue("A grows toward the tail: $tailA", tailA > 0.7f)
        assertTrue(m.nodes.any { it.part == "fin" } && m.nodes.any { it.part == "eye" })
        assertTrue(m.nodes.first { it.part == "eye" }.vec3("pivot") != null)
    }

    @Test fun polypInstanceTables() {
        val m = load("zoa.glb")
        val polyp = m.nodes.first { it.part == "polyp" }
        val table = polyp.instances()!!
        assertEquals(10, polyp.instanceStride)
        assertEquals(0, table.size % 10)
        val buf = Mesh.polypInstances(table, 10, 1f)
        assertEquals(table.size, buf.size)
        // quaternion moved after pos+scale and stays unit length
        val q = buf.copyOfRange(4, 8)
        assertEquals(1f, kotlin.math.sqrt(q.sumOf { (it * it).toDouble() }).toFloat(), 1e-3f)
        val half = Mesh.polypInstances(load("gsp_mat.glb").nodes.first { it.part == "polyp" }.instances()!!, 10, 0.5f)
        assertEquals(800 * Mesh.INSTANCE_FLOATS, half.size)
        assertEquals("coral_zoa_front_center", m.rootString("anchor"))
    }

    @Test fun sceneParts() {
        val tank = load("tank.glb")
        assertTrue(tank.nodes.any { it.part == "water" })
        assertTrue(tank.nodes.first { it.part == "stand" }.hidden)
        val eq = load("equipment.glb")
        val lens = eq.nodes.first { it.part == "emitter" }
        assertEquals(110f, lens.float("coneDeg")!!, 1e-3f)
        // KHR_materials_emissive_strength is folded into the emissive factor
        assertTrue(eq.materials.any { it.emissive.any { e -> e > 1.5f } })
    }

    @Test fun rejectsGarbage() {
        try { GlbLoader.parse(ByteArray(64), "junk"); fail() } catch (_: GlbFormatException) {}
    }

    @Test fun ignoresUnknownFieldsAndChunks() {
        val json = """{"asset":{"version":"2.0","generator":"x","futureField":{"a":[1,2]}},"scene":0,"scenes":[{"nodes":[0]}],
            "nodes":[{"name":"n","mesh":0,"extras":{"part":"rock","whatever":true},"someExtension":1}],
            "meshes":[{"primitives":[{"attributes":{"POSITION":0},"mode":4}]}],
            "accessors":[{"bufferView":0,"componentType":5126,"count":3,"type":"VEC3","max":[1,1,0],"min":[0,0,0]}],
            "bufferViews":[{"buffer":0,"byteLength":36}],"buffers":[{"byteLength":36}],"animations":[{"x":1}]}"""
        val glb = buildGlb(json, FloatArray(9) { if (it == 3 || it == 7) 1f else 0f })
        val m = GlbLoader.parse(glb, "synthetic")
        assertEquals(1, m.nodes.size)
        assertEquals("rock", m.nodes[0].part)
        assertEquals(1, m.triangles)
    }

    private fun buildGlb(json: String, floats: FloatArray): ByteArray {
        var j = json.toByteArray()
        while (j.size % 4 != 0) j += ' '.code.toByte()
        val bin = java.nio.ByteBuffer.allocate(floats.size * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).apply { floats.forEach { putFloat(it) } }.array()
        val total = 12 + 8 + j.size + 16 + 8 + bin.size
        val b = java.nio.ByteBuffer.allocate(total).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        b.putInt(0x46546C67); b.putInt(2); b.putInt(total)
        b.putInt(j.size); b.putInt(0x4E4F534A); b.put(j)
        b.putInt(8); b.putInt(0x12345678); b.putLong(0) // unknown chunk
        b.putInt(bin.size); b.putInt(0x004E4942); b.put(bin)
        return b.array()
    }
}

class ShaderSourceTest {
    private val dir = File("src/main/assets/shaders")

    @Test fun includesResolveAndDefinesFollowVersion() {
        val src = ShaderProgram.preprocess(File(dir, "fish.frag").readText(), mapOf("FIN" to "1")) { File(dir, it).readText() }
        val lines = src.lines()
        assertTrue(lines[0].startsWith("#version 300 es"))
        assertEquals("#define FIN 1", lines[1])
        assertTrue("UBO included", src.contains("uniform Frame"))
        assertTrue("no unresolved include", !src.contains("#include"))
    }

    @Test fun everyShaderHasVersionAndResolves() {
        for (f in dir.listFiles { f -> f.name.endsWith(".vert") || f.name.endsWith(".frag") }.orEmpty()) {
            val src = ShaderProgram.preprocess(f.readText(), emptyMap()) { File(dir, it).readText() }
            assertTrue(f.name, src.startsWith("#version 300 es"))
        }
    }
}
