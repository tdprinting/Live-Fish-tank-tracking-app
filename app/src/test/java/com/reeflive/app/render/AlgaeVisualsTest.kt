package com.reeflive.app.render

import com.reeflive.core.sim.AlgaeCover
import com.reeflive.core.sim.AlgaeGroup
import com.reeflive.core.sim.AlgaeSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AlgaeVisualsTest {
    private fun cover(vararg set: Triple<AlgaeGroup, AlgaeSurface, Float>): AlgaeCover {
        val c = FloatArray(AlgaeCover.GROUPS * AlgaeCover.SURFACES)
        for ((g, s, v) in set) c[g.ordinal * AlgaeCover.SURFACES + s.ordinal] = v
        return AlgaeCover(c, FloatArray(AlgaeCover.GROUPS) { 1f })
    }

    @Test fun packsEachSurfaceIntoItsSlot() {
        val c = cover(
            Triple(AlgaeGroup.FILM, AlgaeSurface.GLASS, 0.11f),
            Triple(AlgaeGroup.DIATOMS, AlgaeSurface.GLASS, 0.12f),
            Triple(AlgaeGroup.DIATOMS, AlgaeSurface.SAND, 0.21f),
            Triple(AlgaeGroup.CYANO, AlgaeSurface.SAND, 0.22f),
            Triple(AlgaeGroup.HAIR, AlgaeSurface.ROCK_TOP, 0.03f),
            Triple(AlgaeGroup.DINOS, AlgaeSurface.ROCK_TOP, 0.33f),
            Triple(AlgaeGroup.CORALLINE, AlgaeSurface.ROCK_SHADE, 0.41f),
            Triple(AlgaeGroup.BUBBLE, AlgaeSurface.ROCK_SHADE, 0.44f),
        )
        val out = FloatArray(4 + AlgaeVisuals.FLOATS) { -1f }
        AlgaeVisuals.pack(c, out, 4)
        for (i in 0 until 4) assertEquals(-1f, out[i], 0f) // nothing written before the offset
        val o = 4
        assertEquals(0.11f, out[o + 0], 0f); assertEquals(0.12f, out[o + 1], 0f)   // glass film, diatoms
        assertEquals(0.21f, out[o + 4], 0f); assertEquals(0.22f, out[o + 5], 0f)   // sand diatoms, cyano
        assertEquals(0.03f, out[o + 8], 0f); assertEquals(0.33f, out[o + 10], 0f)  // top hair, dinos
        assertEquals(0.41f, out[o + 12], 0f); assertEquals(0.44f, out[o + 15], 0f) // shade coralline, bubble
        assertEquals(0f, out[o + 2], 0f) // glass cyano unset
    }

    @Test fun clampsAndSanitises() {
        val c = cover(
            Triple(AlgaeGroup.FILM, AlgaeSurface.GLASS, 1.7f),
            Triple(AlgaeGroup.DIATOMS, AlgaeSurface.GLASS, -0.2f),
            Triple(AlgaeGroup.CYANO, AlgaeSurface.GLASS, Float.NaN),
        )
        val out = FloatArray(AlgaeVisuals.FLOATS)
        AlgaeVisuals.pack(c, out, 0)
        assertEquals(1f, out[0], 0f); assertEquals(0f, out[1], 0f); assertEquals(0f, out[2], 0f)
        AlgaeVisuals.pack(AlgaeCover.EMPTY, out, 0)
        assertTrue(out.all { it == 0f })
    }

    /** After a Flipper scrape the glass slots carry the untouched-glass cover; other surfaces keep the mean cover. */
    @Test fun scrapedGlassPacksTheUntouchedCover() {
        val base = cover(
            Triple(AlgaeGroup.FILM, AlgaeSurface.GLASS, 0.30f),
            Triple(AlgaeGroup.DIATOMS, AlgaeSurface.SAND, 0.21f),
        )
        val glassBase = FloatArray(AlgaeCover.GROUPS).also { it[AlgaeGroup.FILM.ordinal] = 0.4f; it[AlgaeGroup.DIATOMS.ordinal] = 0.05f }
        val c = AlgaeCover(base.cover, base.vigor, glassBase = glassBase, glassMask = FloatArray(64 * 32) { 0.5f }, glassMaskVersion = 3)
        val out = FloatArray(AlgaeVisuals.FLOATS)
        AlgaeVisuals.pack(c, out, 0)
        assertEquals(0.4f, out[0], 0f); assertEquals(0.05f, out[1], 0f)   // glass film, diatoms from glassBase
        assertEquals(0.21f, out[4], 0f)                                     // sand diatoms unchanged
    }

    /** The Kotlin packing must match the std140 `Frame` block in common.glsl (all members are vec4/mat4). */
    @Test fun frameBlockMatchesShaderLayout() {
        val src = File("src/main/assets/shaders/common.glsl").readText()
        val body = src.substringAfter("uniform Frame {").substringBefore("};")
        var floats = 0
        var algaeAt = -1
        var fxAt = -1
        var castersAt = -1
        var shadowAt = -1
        val member = Regex("""^\s*(mat4|vec4)\s+(\w+)(\[(\d+)])?\s*;""")
        for (line in body.lines()) {
            val m = member.find(line) ?: continue
            val size = if (m.groupValues[1] == "mat4") 16 else 4
            if (m.groupValues[2] == "uAlgaeGlass") algaeAt = floats
            if (m.groupValues[2] == "uFx") fxAt = floats
            if (m.groupValues[2] == "uCasters") castersAt = floats
            if (m.groupValues[2] == "uShadowMat") shadowAt = floats
            floats += size * (m.groupValues[4].toIntOrNull() ?: 1)
        }
        assertEquals(FrameUniforms.FLOATS, floats)
        assertEquals(FrameUniforms.ALGAE_OFFSET, algaeAt)
        assertEquals(AlgaeVisuals.FLOATS, FrameUniforms.FX_OFFSET - FrameUniforms.ALGAE_OFFSET)
        assertEquals(FrameUniforms.FX_OFFSET, fxAt)
        assertEquals(FrameUniforms.CASTER_OFFSET, castersAt)
        assertEquals(FrameUniforms.SHADOW_OFFSET, FrameUniforms.CASTER_OFFSET + FrameUniforms.MAX_CASTERS * 4)
        assertEquals(FrameUniforms.SHADOW_OFFSET, shadowAt)
        assertEquals(FrameUniforms.FLOATS, FrameUniforms.SHADOW_OFFSET + 20)
        val names = Regex("""vec4\s+(uAlgae\w+)\s*;""").findAll(body).map { it.groupValues[1] }.toList()
        assertEquals(listOf("uAlgaeGlass", "uAlgaeSand", "uAlgaeTop", "uAlgaeShade"), names)
    }
}
