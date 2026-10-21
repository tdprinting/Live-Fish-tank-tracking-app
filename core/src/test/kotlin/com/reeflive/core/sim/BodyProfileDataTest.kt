package com.reeflive.core.sim

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every animal's model has its baked collision body (re-run tools/collision/bake_body_profiles.py after adding one). */
class BodyProfileDataTest {
    @Test
    fun everyAnimalModelIsBaked() {
        val sheets = listOf("src/main/resources/species", "core/src/main/resources/species").map(::File).first { it.isDirectory }
        val assets = listOf("../app/src/main/assets", "app/src/main/assets").map(::File).first { it.isDirectory }
        val missing = ArrayList<String>()
        for (f in sheets.listFiles()!!.filter { it.name.endsWith(".json") }) {
            val t = f.readText()
            if (!Regex("\"kind\"\\s*:\\s*\"(FISH|INVERT)\"").containsMatchIn(t)) continue
            for (m in Regex("\"(models/[^\"]+\\.glb)\"").findAll(t).map { it.groupValues[1] })
                if (File(assets, m).isFile && m !in BODY_PROFILE_DATA) missing += "${f.name}: $m"
        }
        assertTrue("models without a baked body (run tools/collision/bake_body_profiles.py):\n" + missing.joinToString("\n"), missing.isEmpty())
    }
}
