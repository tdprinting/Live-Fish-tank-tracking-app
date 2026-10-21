package com.reeflive.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RadioStationTest {
    @Test fun nextAndBackWrapRound() {
        val n = RadioStation.songs.size
        assertEquals(1, RadioStation.step(0, 1))
        assertEquals(0, RadioStation.step(n - 1, 1))
        assertEquals(n - 1, RadioStation.step(0, -1))
        // a stale stored index (a song removed later) still lands on a real song
        assertEquals(RadioStation.songs[2], RadioStation.song(n + 2))
        assertEquals(RadioStation.songs[n - 1], RadioStation.song(-1))
    }

    @Test fun everySongShipsAsAFile() {
        val raw = File("src/main/res/raw")
        for (s in RadioStation.songs + RadioSong(RadioStation.TUNE_FILE, "", "")) {
            assertTrue("${s.file}.ogg is missing", File(raw, "${s.file}.ogg").isFile)
        }
        assertEquals(RadioStation.songs.size, RadioStation.songs.map { it.file }.toSet().size)
    }

    @Test fun theRadioStaysQuiet() {
        assertTrue(RadioStation.GAIN in 0.1f..0.5f)
    }
}
