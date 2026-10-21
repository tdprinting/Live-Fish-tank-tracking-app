package com.reeflive.core.radio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RadioStationTest {
    private val free = RadioStation.songs.indices.filter { RadioStation.songs[it].free }

    @Test fun nextAndBackWrapRoundTheFreeSongs() {
        val last = free.last()
        assertEquals(1, RadioStation.step(0, 1))
        assertEquals(0, RadioStation.step(last, 1))
        assertEquals(last, RadioStation.step(0, -1))
        // a stale stored index still lands on a real song
        assertEquals(RadioStation.songs[2], RadioStation.song(RadioStation.songs.size + 2))
    }

    @Test fun boughtRecordsJoinThePlayList() {
        val turtles = RadioStation.songs.indexOfFirst { it.file == "radio_turtle_glide" }
        val owned = setOf("radio_turtle_glide")
        assertEquals(turtles, RadioStation.step(free.last(), 1, owned))
        assertEquals(0, RadioStation.step(turtles, 1, owned))
        assertEquals(0, RadioStation.playableOr(turtles, emptySet()))
        assertEquals(turtles, RadioStation.playableOr(turtles, owned))
    }

    @Test fun everySongShipsWithItsSleeve() {
        val raw = File("../app/src/main/res/raw")
        val art = File("../app/src/main/res/drawable-nodpi")
        for (s in RadioStation.songs) {
            assertTrue("${s.file}.ogg is missing", File(raw, "${s.file}.ogg").isFile)
            assertTrue("${s.art}.webp is missing", File(art, "${s.art}.webp").isFile)
        }
        assertTrue(File(raw, "${RadioStation.TUNE_FILE}.ogg").isFile)
        assertEquals(RadioStation.songs.size, RadioStation.songs.map { it.file }.toSet().size)
    }

    @Test fun theFirstSixAreFreeAndTheShopIsModest() {
        assertEquals(6, free.size)
        assertTrue(RadioStation.songs.filterNot { it.free }.all { it.price in 20..60 })
        assertTrue(RadioStation.GAIN in 0.1f..0.5f)
    }
}
