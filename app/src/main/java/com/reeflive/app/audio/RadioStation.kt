package com.reeflive.app.audio

/** One song on the tank radio: [file] is the res/raw name (Android) and the sounds/ file (web). */
data class RadioSong(val file: String, val title: String, val blurb: String)

/**
 * The little radio on the table beside the tank: quiet, original background tunes (composed by
 * tools/audio/generate_radio.py, with the small-speaker sound and light static baked in). Shared with the web build.
 */
object RadioStation {
    val songs = listOf(
        RadioSong("radio_coral_morning", "Coral Reef Morning", "A twinkly underwater tune"),
        RadioSong("radio_aloha_lagoon", "Aloha Lagoon", "Hawaiian beach guitar"),
        RadioSong("radio_tide_pool", "Tide Pool Dreams", "Ocean waves and soft bells"),
        RadioSong("radio_clownfish_waltz", "Little Clownfish Waltz", "A happy, bouncy waltz"),
        RadioSong("radio_hammock_ukulele", "Hammock Ukulele", "Island strums by the sea"),
        RadioSong("radio_moonlit_drift", "Moonlit Drift", "A sleepy night-time tune"),
    )

    /** The tune-between-songs static. */
    const val TUNE_FILE = "radio_tune"

    /** Radio level against the user volume: quiet, like a radio across the room, under the tank sounds. */
    const val GAIN = 0.32f

    /** Static sweep level when the dial turns. */
    const val TUNE_GAIN = 0.25f

    /** [index] moved by [step] songs, wrapping round (any stored index is safe). */
    fun step(index: Int, step: Int): Int = ((index + step) % songs.size + songs.size) % songs.size

    fun song(index: Int): RadioSong = songs[step(index, 0)]
}
