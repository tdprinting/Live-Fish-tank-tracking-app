package com.reeflive.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.reeflive.app.R

/**
 * The radio on the table beside the tank: plays [RadioStation] songs one after another, quietly, while the Live tab is
 * in front. Songs stream from res/raw through [MediaPlayer] (one decoder, nothing held in memory), so it is cheap.
 * Call from the main thread only.
 *
 * [onSongEnded] reports the next song's index when one finishes, so the caller can remember it.
 */
class TankRadio(context: Context, private val onSongEnded: (Int) -> Unit) {
    private val app = context.applicationContext
    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()
    private val session = (app.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager).generateAudioSessionId()

    private var player: MediaPlayer? = null
    private var playerTrack = -1
    private var on = false
    private var track = 0
    private var volume = 0.6f
    private var resumed = false
    private var released = false

    /** User settings: radio on/off, the song it is on and the shared volume slider. */
    fun set(on: Boolean, track: Int, volume: Float) {
        if (released) return
        val turned = on && this.on && track != this.track && player != null
        this.on = on
        this.track = RadioStation.step(track, 0)
        this.volume = volume.coerceIn(0f, 1f)
        if (turned) tuneStatic()
        sync()
    }

    fun onResume() { if (!released) { resumed = true; sync() } }

    fun onPause() { if (!released) { resumed = false; player?.takeIf { it.isPlaying }?.pause() } }

    fun release() {
        if (released) return
        released = true
        player?.release()
        player = null
    }

    // ------------------------------------------------------------------ internals
    private fun level() = volume * RadioStation.GAIN

    private fun sync() {
        if (!on || !resumed || volume <= 0f) {
            player?.takeIf { it.isPlaying }?.pause()
            return
        }
        if (player == null || playerTrack != track) {
            player?.release()
            player = create(RadioStation.song(track).file)?.also { p ->
                p.setOnCompletionListener {
                    // on to the next song by itself (no dial static); the caller remembers it
                    if (released) return@setOnCompletionListener
                    track = RadioStation.step(playerTrack, 1)
                    sync()
                    onSongEnded(track)
                }
            }
            playerTrack = track
        }
        player?.let { p ->
            p.setVolume(level(), level())
            if (!p.isPlaying) p.start()
        }
    }

    /** The dial turning: a short sweep of static under the new song. */
    private fun tuneStatic() {
        if (!resumed) return
        val p = create(RadioStation.TUNE_FILE) ?: return
        val v = volume * RadioStation.TUNE_GAIN
        p.setVolume(v, v)
        p.setOnCompletionListener { it.release() }
        p.start()
    }

    private fun create(file: String): MediaPlayer? {
        val id = RAW[file] ?: return null
        return runCatching { MediaPlayer.create(app, id, attrs, session) }.getOrNull()
    }

    private companion object {
        /** RadioStation file names to resources (named here so resource shrinking keeps them). */
        val RAW = mapOf(
            "radio_coral_morning" to R.raw.radio_coral_morning,
            "radio_aloha_lagoon" to R.raw.radio_aloha_lagoon,
            "radio_tide_pool" to R.raw.radio_tide_pool,
            "radio_clownfish_waltz" to R.raw.radio_clownfish_waltz,
            "radio_hammock_ukulele" to R.raw.radio_hammock_ukulele,
            "radio_moonlit_drift" to R.raw.radio_moonlit_drift,
            RadioStation.TUNE_FILE to R.raw.radio_tune,
        )
    }
}
