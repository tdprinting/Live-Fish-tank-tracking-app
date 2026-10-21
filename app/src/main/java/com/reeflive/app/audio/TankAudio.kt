package com.reeflive.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.reeflive.app.R
import com.reeflive.core.sim.SimEvent
import com.reeflive.core.sim.SimSnapshot
import kotlin.random.Random

/**
 * Live Tank sound: four looping ambience beds whose volumes follow the sim ([TankSoundMix.ambience]) and rate-limited
 * one-shots for sim events and sandbox taps. Everything goes through one [SoundPool] on the media stream
 * (USAGE_GAME), so the phone's media volume applies. Call from the main thread only.
 *
 * Lifecycle: [onResume] / [onPause] follow the Live tab (loops stop while it is not in the foreground), [release]
 * frees the pool when the screen leaves composition.
 */
class TankAudio(context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val loaded = HashSet<Int>()
    private val limiter = CueLimiter()

    // the listener is set before any load; SoundPool reports completions on this (main) thread's looper
    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(MAX_STREAMS)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
        .also { p ->
            p.setOnLoadCompleteListener { _, sampleId, status ->
                if (status == 0 && !released) {
                    loaded += sampleId
                    syncLoops()
                }
            }
        }

    private val flowId = pool.load(context, R.raw.tank_flow_loop, 1)
    private val humId = pool.load(context, R.raw.tank_hum_loop, 1)
    private val hobId = pool.load(context, R.raw.tank_hob_loop, 1)
    private val bubblesId = pool.load(context, R.raw.tank_bubbles_loop, 1)
    private val cueIds: Map<Cue, Int> = mapOf(
        Cue.FEED to pool.load(context, R.raw.tank_feed_plop, 1),
        Cue.PELLET to pool.load(context, R.raw.tank_pellet_drop, 1),
        Cue.CLICK to pool.load(context, R.raw.tank_shrimp_click, 1),
        Cue.TICK to pool.load(context, R.raw.tank_ui_tick, 1),
        Cue.ALERT to pool.load(context, R.raw.tank_alert_chime, 1),
        Cue.SPARKLE to pool.load(context, R.raw.tank_reward_sparkle, 1),
    )
    private val scrapeId = pool.load(context, R.raw.tank_glass_scrape, 1)
    private var lastScrapeMs = 0L

    /** Loop sound id → playing stream id (absent = not playing). */
    private val loopStreams = HashMap<Int, Int>()
    /** Smoothed loop volumes, by loop sound id. */
    private val current = HashMap<Int, Float>()
    private var target = AmbienceLevels.SILENT

    /** Off until the first [setUserSettings], so a user who muted never hears a blip while settings load. */
    private var enabled = false
    private var volume = 0.6f
    private var resumed = false
    private var released = false
    private var lastSnapshot: SimSnapshot? = null
    private var loeIds: Set<String>? = null

    /** User setting (Settings > Live Tank > Tank sounds, or the HUD mute button). */
    fun setUserSettings(enabled: Boolean, volume: Float) {
        if (released) return
        this.enabled = enabled
        this.volume = volume.coerceIn(0f, 1f)
        if (!enabled) stopLoops() else syncLoops()
    }

    fun onResume() {
        if (released) return
        resumed = true
        syncLoops()
    }

    fun onPause() {
        if (released) return
        resumed = false
        main.removeCallbacksAndMessages(null)
        stopLoops()
    }

    /**
     * Feed every new snapshot (full rate, not the sampled HUD flow, or events are missed).
     * [powerheadShare] = share of the sandbox powerhead toggles that are on (1 in Watch mode).
     */
    fun onSnapshot(snap: SimSnapshot, powerheadShare: Float) {
        if (released || snap === lastSnapshot || snap === SimSnapshot.EMPTY) return
        // the first snapshot is the one a StateFlow replays to a new collector: its events were already heard
        val first = lastSnapshot == null
        lastSnapshot = snap
        target = TankSoundMix.ambience(snap.flowStrength, powerheadShare, snap.water.flowOn, snap.lightIntensity, volume)
        syncLoops()

        val loe = TankSoundMix.loeIds(snap.stats)
        val newLoe = loeIds?.let { prev -> loe.any { it !in prev } } ?: false
        loeIds = loe
        if (first || !active()) return
        if (newLoe) play(Cue.ALERT)
        for (e in snap.events) {
            val cue = TankSoundMix.cueFor(e) ?: continue
            if (play(cue) && e is SimEvent.Fed) scatterPellets(TankSoundMix.pelletCount(e.grams))
        }
    }

    /** The reef moment / birthday card's bells. */
    fun sparkle() {
        if (active()) play(Cue.SPARKLE, rate = 1f, gain = 0.8f)
    }

    /** Soft tick for sandbox actions (rate-limited, so a dragged slider does not machine-gun). */
    fun uiTick() {
        if (active()) play(Cue.TICK, rate = 1f, gain = 0.6f)
    }

    /**
     * A fingertip tap on the tank: a soft, dull knock (the pellet tick pitched well down), [strength] 0..1. Rate-limited
     * with the pellet ticks, so a burst of taps never machine-guns.
     */
    fun knock(strength: Float = 1f) {
        if (active()) play(Cue.PELLET, rate = 0.5f + Random.nextFloat() * 0.08f, gain = 0.9f * strength.coerceIn(0f, 1f))
    }

    /** The net goodbye: the body plops into the toilet bowl. */
    fun plop() {
        if (active()) play(Cue.FEED, rate = 0.75f, gain = 1f)
    }

    /** The net goodbye: the toilet flushes (the bubbles bed, pitched down, for a short gurgle). */
    fun flush() {
        if (!active() || bubblesId !in loaded) return
        val v = (volume * 0.9f).coerceIn(0f, 1f)
        val stream = pool.play(bubblesId, v, v, 2, 0, 0.7f)
        if (stream != 0) main.postDelayed({ if (!released) pool.stop(stream) }, FLUSH_MS)
    }

    /** Clean-glass mode: the Flipper slid [cm] over the glass. A soft felt-on-glass swish, rate-limited. */
    fun scrape(cm: Float) {
        if (!active() || scrapeId !in loaded) return
        val gain = TankSoundMix.scrapeGain(cm)
        val now = SystemClock.uptimeMillis()
        if (gain <= 0f || now - lastScrapeMs < TankSoundMix.SCRAPE_GAP_MS) return
        lastScrapeMs = now
        val v = (volume * gain).coerceIn(0f, 1f)
        pool.play(scrapeId, v, v, 1, 0, 0.9f + Random.nextFloat() * 0.2f)
    }

    fun release() {
        if (released) return
        released = true
        main.removeCallbacksAndMessages(null)
        pool.setOnLoadCompleteListener(null)
        pool.release()
        loopStreams.clear()
    }

    // ------------------------------------------------------------------ internals
    private fun active() = enabled && resumed && volume > 0f

    private fun play(cue: Cue, rate: Float = 0.9f + Random.nextFloat() * 0.2f, gain: Float = 1f): Boolean {
        val id = cueIds[cue] ?: return false
        if (id !in loaded || !limiter.tryPlay(cue, SystemClock.uptimeMillis())) return false
        val v = (volume * gain * CUE_GAIN.getValue(cue)).coerceIn(0f, 1f)
        pool.play(id, v, v, if (cue == Cue.ALERT) 2 else 1, 0, rate)
        return true
    }

    /** A few pellets ticking onto the surface after the plop. */
    private fun scatterPellets(n: Int) {
        var at = 140L
        repeat(n) {
            main.postDelayed({ if (!released && active()) play(Cue.PELLET, rate = 0.85f + Random.nextFloat() * 0.35f, gain = 0.7f) }, at)
            at += 90L + Random.nextLong(160L)
        }
    }

    private fun levelFor(id: Int): Float = when (id) {
        flowId -> target.flow
        humId -> target.hum
        hobId -> target.hob
        bubblesId -> target.bubbles
        else -> 0f
    }

    /** Starts, re-levels or stops each loop to match [target]; volumes glide (~0.3 s at 20 Hz). */
    private fun syncLoops() {
        if (released) return
        for (id in intArrayOf(flowId, humId, hobId, bubblesId)) {
            val want = if (active()) levelFor(id) else 0f
            val now = current[id] ?: 0f
            val v = if (kotlin.math.abs(want - now) < 0.005f) want else now + (want - now) * GLIDE
            current[id] = v
            val stream = loopStreams[id]
            when {
                v > MIN_LEVEL && stream == null && id in loaded -> {
                    val s = pool.play(id, v, v, 0, -1, 1f)
                    if (s != 0) loopStreams[id] = s
                }
                v > MIN_LEVEL && stream != null -> pool.setVolume(stream, v, v)
                v <= MIN_LEVEL && stream != null -> { pool.stop(stream); loopStreams.remove(id) }
            }
        }
    }

    private fun stopLoops() {
        for (s in loopStreams.values) pool.stop(s)
        loopStreams.clear()
        current.clear()
    }

    private companion object {
        private const val FLUSH_MS = 1700L
        const val MAX_STREAMS = 10
        const val GLIDE = 0.15f
        const val MIN_LEVEL = 0.003f
        val CUE_GAIN = mapOf(Cue.FEED to 0.9f, Cue.PELLET to 0.6f, Cue.CLICK to 0.45f, Cue.TICK to 0.5f, Cue.ALERT to 0.8f, Cue.SPARKLE to 0.7f)
    }
}
