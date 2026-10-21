package com.reeflive.core.radio

/**
 * One song on the tank radio: [file] is the res/raw name (Android) and the sounds/ file (web); [price] in sand dollars
 * from My tank's record shop (0 = free, always on the radio).
 */
data class RadioSong(val file: String, val title: String, val blurb: String, val price: Int = 0) {
    val free: Boolean get() = price == 0
    /** The record sleeve picture: drawable-nodpi/vinyl_<name>.webp, the same file on the web (vinyl/). */
    val art: String get() = "vinyl_" + file.removePrefix("radio_")
}

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
        // the record shop (My tank): about half a day of care each
        RadioSong("radio_window_seat", "Window Seat to the Sea", "Flying off to the ocean", 30),
        RadioSong("radio_five_oclock_dock", "Five O'Clock on the Dock", "Steel drums at happy hour", 35),
        RadioSong("radio_sandcastle_sunshine", "Sandcastle Sunshine", "Happy times at the beach", 30),
        RadioSong("radio_turtle_glide", "Swimming with Turtles", "Slow and floaty, with the turtles", 35),
        RadioSong("radio_sea_breeze_bossa", "Sea Breeze Bossa", "A breezy bossa nova", 40),
        RadioSong("radio_starfish_lullaby", "Starfish Lullaby", "A music box for sleepy fish", 25),
        RadioSong("radio_tiki_twilight", "Tiki Twilight", "Torches and a lazy steel guitar", 35),
        RadioSong("radio_kelp_forest_sway", "Kelp Forest Sway", "Rocking along with the kelp", 30),
        RadioSong("radio_postcard_reef", "Postcard from the Reef", "A tiptoeing underwater tune", 30),
        RadioSong("radio_dolphin_dance", "Dolphin Dance", "Bouncy, playful and quick", 45),
    )

    /** The tune-between-songs static. */
    const val TUNE_FILE = "radio_tune"

    /** Radio level against the user volume: quiet, like a radio across the room, under the tank sounds. */
    const val GAIN = 0.32f

    /** Static sweep level when the dial turns. */
    const val TUNE_GAIN = 0.25f

    /** [index] wrapped onto the list (any stored index is safe). */
    fun wrap(index: Int): Int = (index % songs.size + songs.size) % songs.size

    fun song(index: Int): RadioSong = songs[wrap(index)]

    /** Whether the radio may play [song]: free, or bought ([owned] holds bought files). */
    fun playable(song: RadioSong, owned: Set<String>): Boolean = song.free || song.file in owned

    /**
     * The next playable song [step] (+1 / -1) away from [index], skipping records not bought yet; [index] itself
     * when nothing else is playable.
     */
    fun step(index: Int, step: Int, owned: Set<String> = emptySet()): Int {
        val dir = if (step < 0) -1 else 1
        var i = wrap(index)
        repeat(songs.size) {
            i = wrap(i + dir)
            if (playable(songs[i], owned)) return i
        }
        return wrap(index)
    }

    /** [index] if it is playable, else the first free song (a stored pick from a tank that was started over). */
    fun playableOr(index: Int, owned: Set<String>): Int = if (playable(song(index), owned)) wrap(index) else 0
}
