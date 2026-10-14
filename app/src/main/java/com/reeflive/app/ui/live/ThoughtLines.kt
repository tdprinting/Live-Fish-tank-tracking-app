package com.reeflive.app.ui.live

import com.reeflive.core.sim.OrganismStats
import com.reeflive.core.sim.SimSnapshot
import com.reeflive.core.sim.Thought

/**
 * "What is it thinking?" on screen (UI_STYLE §10.9): the held lines of the fish card and follow bar, and the picker
 * for the thought bubbles over the 3D tank. Plain Kotlin, no Compose, so the web page shares it (web/build.gradle.kts)
 * and a JVM test can drive it with a fake clock.
 */

/** The three lines the card and follow bar show under "Thinking…". */
data class ThinkingLines(val now: String = "", val next: String = "", val feeling: String = "") {
    val isEmpty: Boolean get() = now.isBlank()

    companion object {
        val EMPTY = ThinkingLines()
        fun of(t: Thought) = ThinkingLines(t.now, t.next, t.feeling)
    }
}

/**
 * Keeps the card calm at the 4 Hz HUD: each line stays at least [minMs] before it changes, unless the plan itself
 * changes ([Thought.planKey]) or another animal is picked, which show at once.
 */
class ThoughtHold(private val minMs: Long = HOLD_MS) {
    private var id: String? = null
    private var key = ""
    private var shown = ThinkingLines.EMPTY
    private val since = LongArray(3)

    fun update(id: String, t: Thought, nowMs: Long): ThinkingLines {
        if (id != this.id || t.planKey != key || shown.isEmpty) {
            this.id = id; key = t.planKey
            shown = ThinkingLines.of(t)
            since.fill(nowMs)
            return shown
        }
        fun pick(i: Int, old: String, new: String): String {
            if (old == new || nowMs - since[i] < minMs) return old
            since[i] = nowMs
            return new
        }
        shown = ThinkingLines(pick(0, shown.now, t.now), pick(1, shown.next, t.next), pick(2, shown.feeling, t.feeling))
        return shown
    }

    companion object { const val HOLD_MS = 1500L }
}

/** One bubble to draw: screen position of the animal (px), text, and 0..1 opacity. */
data class BubbleView(val id: String, val text: String, val x: Float, val y: Float, val alpha: Float, val pxPerCm: Float)

/**
 * Picks at most [MAX] animals for thought bubbles and keeps them readable: a bubble stays [SHOW_MS] (longer when its
 * plan changes again), fades in and out over [FADE_MS], and an animal waits [COOLDOWN_MS] before its next bubble.
 * Interesting animals come first: a fresh change of plan, then [Thought.interest], then how close to the camera.
 * Selection runs every [PICK_MS]; positions follow the animals every call.
 */
class ThoughtBubblePicker {
    private class Live(val id: String, var text: String, val bornMs: Long, var dieMs: Long, var key: String) {
        var x = 0f; var y = 0f; var ppcm = 1f; var lost = false
    }

    private val live = ArrayList<Live>()
    private val lastKey = HashMap<String, String>()
    private val changedAt = HashMap<String, Long>()
    private val cooldownUntil = HashMap<String, Long>()
    private var lastPick = Long.MIN_VALUE
    private val out = ArrayList<BubbleView>()

    /** Forget everything (toggle off, a new tank). */
    fun clear() { live.clear(); lastKey.clear(); changedAt.clear(); cooldownUntil.clear(); lastPick = Long.MIN_VALUE }

    /**
     * [stats]: the animals with their thoughts; [place] gives an animal's screen spot as (x px, y px, px per cm) or
     * null when it is off screen or hidden; return a new array each call (the picker keeps it). Returns the bubbles
     * to draw this frame (a list reused by the next call).
     */
    fun update(stats: List<OrganismStats>, nowMs: Long, place: (String) -> FloatArray?): List<BubbleView> {
        // positions every frame
        for (b in live) {
            val p = place(b.id)
            if (p == null) { if (!b.lost) { b.lost = true; b.dieMs = minOf(b.dieMs, nowMs) } }
            else { b.x = p[0]; b.y = p[1]; b.ppcm = p[2] }
        }
        live.removeAll { nowMs > it.dieMs + FADE_MS }
        if (nowMs - lastPick >= PICK_MS || lastPick == Long.MIN_VALUE) {
            lastPick = nowMs
            pick(stats, nowMs, place)
        }
        out.clear()
        for (b in live) {
            val a = minOf(((nowMs - b.bornMs).toFloat() / FADE_MS).coerceIn(0f, 1f), (1f - (nowMs - b.dieMs).toFloat() / FADE_MS).coerceIn(0f, 1f))
            if (a > 0.01f) out += BubbleView(b.id, b.text, b.x, b.y, a, b.ppcm)
        }
        return out
    }

    private fun pick(stats: List<OrganismStats>, nowMs: Long, place: (String) -> FloatArray?) {
        // note fresh changes of plan, and refresh a showing bubble whose animal changed its mind
        for (s in stats) {
            if (!s.alive) continue
            val k = s.thought.planKey
            val prev = lastKey[s.id]
            if (prev != null && prev != k) changedAt[s.id] = nowMs
            lastKey[s.id] = k
            live.firstOrNull { it.id == s.id && !it.lost && nowMs < it.dieMs }?.let { b ->
                if (b.key != k && s.thought.bubble.isNotBlank()) { b.text = s.thought.bubble; b.key = k; b.dieMs = maxOf(b.dieMs, nowMs + SHOW_MS / 2) }
            }
        }
        // fading ones count too: never more than MAX on screen at once
        if (live.size >= MAX) return
        var best: OrganismStats? = null
        var bestScore = MIN_SCORE
        var bestPlace: FloatArray? = null
        for (s in stats) {
            val t = s.thought
            if (!s.alive || t.bubble.isBlank() || live.any { it.id == s.id }) continue
            if ((cooldownUntil[s.id] ?: Long.MIN_VALUE) > nowMs) continue
            val fresh = changedAt[s.id]?.let { nowMs - it < FRESH_MS } == true
            if (t.interest <= 0 && !fresh) continue
            val p = place(s.id) ?: continue
            // keep bubbles apart: not on top of one already showing
            if (live.any { kotlin.math.abs(it.x - p[0]) < SPREAD_PX && kotlin.math.abs(it.y - p[1]) < SPREAD_PX * 0.6f }) continue
            val score = t.interest + (if (fresh) 2.5f else 0f) + (p[2] / 30f).coerceAtMost(1.5f)
            if (score > bestScore) { bestScore = score; best = s; bestPlace = p }
        }
        val s = best ?: return
        val p = bestPlace ?: return
        // one new bubble per pick: they arrive one after another, never all at once
        live += Live(s.id, s.thought.bubble, nowMs, nowMs + SHOW_MS, s.thought.planKey).also { it.x = p[0]; it.y = p[1]; it.ppcm = p[2] }
        cooldownUntil[s.id] = nowMs + SHOW_MS + COOLDOWN_MS
    }

    companion object {
        const val MAX = 3
        const val SHOW_MS = 4_500L
        const val FADE_MS = 400L
        const val COOLDOWN_MS = 6_000L
        const val PICK_MS = 250L
        const val FRESH_MS = 3_000L
        const val SPREAD_PX = 140f
        private const val MIN_SCORE = 1.2f

        /** Where to anchor a bubble on an animal: a little above its head, in tank cm (x, y, z). */
        fun anchorOf(snap: SimSnapshot, id: String, out: FloatArray): Boolean {
            val pose = snap.organisms.firstOrNull { it.id == id } ?: return false
            if (!pose.alive || !pose.visible || pose.spine.size < 3) return false
            out[0] = pose.spine[0]; out[1] = pose.spine[1] + 1.5f + 0.25f * pose.scale; out[2] = pose.spine[2]
            return true
        }
    }
}
