package com.reeflive.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateLogicTest {
    private val sample = """
        {
          "tag_name": "v0.1.20261012.57",
          "name": "ReefLive v0.1.20261012.57",
          "html_url": "https://github.com/x/y/releases/tag/v0.1.20261012.57",
          "body": "versionCode: 157\n\n## What's Changed\n* In-app updates by **Tee** in [#6](https://github.com/x/y/pull/6)\n",
          "assets": [
            {"name": "reeflive-debug.apk", "url": "https://api.github.com/repos/x/y/releases/assets/1", "browser_download_url": "https://github.com/d", "size": 20},
            {"name": "reeflive.apk", "url": "https://api.github.com/repos/x/y/releases/assets/2", "browser_download_url": "https://github.com/r", "size": 10}
          ]
        }
    """.trimIndent()

    @Test
    fun parsesReleaseAndPicksReleaseApk() {
        val parsed = UpdateLogic.parse(sample)
        assertNotNull(parsed)
        val r = parsed!!
        assertEquals(157L, r.versionCode)
        assertEquals("0.1.20261012.57", r.versionName)
        assertEquals("reeflive.apk", r.asset?.name)
        assertEquals("https://api.github.com/repos/x/y/releases/assets/2", r.asset?.apiUrl)
        assertEquals(10L, r.asset?.size)
        assertFalse(r.notes.contains("versionCode"))
        assertTrue(r.notes, r.notes.contains("• In-app updates by Tee in #6"))
    }

    @Test
    fun versionCodeFallsBackToTagRunNumber() {
        assertEquals(157L, UpdateLogic.versionCodeOf("v0.1.20261012.57", "notes"))
        assertEquals(200L, UpdateLogic.versionCodeOf("v0.1.20261012.57", "versionCode=200"))
        assertNull(UpdateLogic.versionCodeOf("nightly", ""))
        assertNull(UpdateLogic.parse("""{"tag_name": "nightly", "body": ""}"""))
        assertNull(UpdateLogic.parse("not json"))
    }

    @Test
    fun assetPreference() {
        fun a(n: String) = ReleaseAsset(n, "u/$n", "", 1)
        assertEquals("app-release.apk", UpdateLogic.pickAsset(listOf(a("app-debug.apk"), a("app-release.apk")))?.name)
        assertEquals("app-debug.apk", UpdateLogic.pickAsset(listOf(a("notes.txt"), a("app-debug.apk")))?.name)
        assertNull(UpdateLogic.pickAsset(listOf(a("notes.txt"))))
    }

    @Test
    fun newerAndAutoCheckSpacing() {
        val r = UpdateLogic.parse(sample)!!
        assertTrue(UpdateLogic.isNewer(r, 156))
        assertFalse(UpdateLogic.isNewer(r, 157))
        val h = 3_600_000L
        assertTrue(UpdateLogic.dueForAutoCheck(0, 10 * h))
        assertFalse(UpdateLogic.dueForAutoCheck(10 * h, 15 * h))
        assertTrue(UpdateLogic.dueForAutoCheck(10 * h, 16 * h))
        assertTrue(UpdateLogic.dueForAutoCheck(10 * h, 9 * h))
    }

    @Test
    fun sizeCheck() {
        assertTrue(UpdateLogic.sizeMatches(10, 10))
        assertFalse(UpdateLogic.sizeMatches(10, 9))
        assertFalse(UpdateLogic.sizeMatches(10, 0))
        assertTrue(UpdateLogic.sizeMatches(-1, 5))
    }
}
