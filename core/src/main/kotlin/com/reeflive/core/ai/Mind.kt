package com.reeflive.core.ai

import com.reeflive.core.geom.Vec3
import com.reeflive.core.sim.BrainKind
import com.reeflive.core.sim.Ev
import com.reeflive.core.sim.Evidence
import com.reeflive.core.sim.Organism
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/*
 * An animal's memory and mood (research realism_gaps X2, X3, X4; SIMULATION §21.2–21.4): places it has learned, the
 * keeper's feeding clock, a continuous fear/arousal state, and a personality that drifts slowly with experience.
 * One [Mind] per brain ([SpeciesBrain.mind]). Nothing here draws random numbers.
 */

/** What a remembered place is good (or bad) for. */
enum class PlaceKind { REFUGE, THREAT, GRAZE, NIGHT, PATROL }

/** One remembered place: where, what, how good (0..1) when last reinforced, and when. */
class Place(var pos: Vec3, val kind: PlaceKind, var value: Double, var t: Double)

/**
 * Place memory (X2): a short list per kind, each entry fading with its kind's half-life. Writing near an existing
 * entry of the same kind reinforces it instead of adding one.
 */
class PlaceMemory {
    val items = ArrayList<Place>()

    fun strength(p: Place, t: Double): Double = p.value * 0.5.pow(max(0.0, t - p.t) / halfLife(p.kind))

    /** Remember (or reinforce) a [kind] place at [pos] with [value]; returns the entry. */
    fun note(kind: PlaceKind, pos: Vec3, value: Double, t: Double, mergeCm: Double = MERGE_CM): Place {
        val near = items.firstOrNull { it.kind == kind && it.pos.distanceTo(pos) < mergeCm }
        if (near != null) {
            near.value = min(1.0, max(strength(near, t), value))
            near.pos = near.pos.lerp(pos, 0.3)
            near.t = t
            return near
        }
        val same = items.filter { it.kind == kind }
        if (same.size >= cap(kind)) items.remove(same.minBy { strength(it, t) })
        return Place(pos, kind, value.coerceIn(0.0, 1.0), t).also { items += it }
    }

    /** Overwrite a place's value (a grazed-down patch, a refuge that failed). */
    fun set(p: Place, value: Double, t: Double) { p.value = value.coerceIn(0.0, 1.0); p.t = t }

    fun of(kind: PlaceKind): List<Place> = items.filter { it.kind == kind }

    /** The strongest [kind] place passing [filter], weighted by [score]; null if none is above [MIN_STRENGTH]. */
    fun best(kind: PlaceKind, t: Double, score: (Place, Double) -> Double = { _, s -> s }): Place? {
        var best: Place? = null
        var bs = 0.0
        for (p in items) {
            if (p.kind != kind) continue
            val s = strength(p, t)
            if (s < MIN_STRENGTH) continue
            val w = score(p, s)
            if (w > bs) { bs = w; best = p }
        }
        return best
    }

    fun nearThreat(at: Vec3, t: Double, radius: Double): Boolean =
        items.any { it.kind == PlaceKind.THREAT && it.pos.distanceTo(at) < radius && strength(it, t) > MIN_STRENGTH }

    fun prune(t: Double) { items.removeAll { strength(it, t) < MIN_STRENGTH * 0.5 } }

    companion object {
        const val MERGE_CM = 6.0
        const val MIN_STRENGTH = 0.15
        /** Half-lives (s). THREAT matches the 6 h threat memory of Social.kt; the rest are DESIGN ESTIMATES [L]. */
        @Ev(Evidence.DESIGN_ESTIMATE, "realism_gaps X2 [L]; threat 6 h as Social.kt")
        fun halfLife(k: PlaceKind): Double = when (k) {
            PlaceKind.REFUGE -> 7 * 86400.0
            PlaceKind.THREAT -> 6 * 3600.0
            PlaceKind.GRAZE -> 86400.0
            PlaceKind.NIGHT -> 30 * 86400.0
            PlaceKind.PATROL -> 14 * 86400.0
        }
        fun cap(k: PlaceKind): Int = when (k) {
            PlaceKind.REFUGE -> 3
            PlaceKind.THREAT -> 4
            PlaceKind.GRAZE -> 6
            PlaceKind.NIGHT -> 1
            PlaceKind.PATROL -> 6
        }
    }
}

/** One learned feeding time: minute of day, where the food went in, and how strongly it is expected. */
class FeedSlot(var minute: Double, var place: Vec3, var strength: Double, val createdAt: Double) {
    var lastFedAt = createdAt
    /** Start of the last occurrence already judged fed or missed (s). */
    var judgedOcc = Double.NEGATIVE_INFINITY
}

/**
 * The keeper's feeding clock (X3; REE02: goldfish and golden shiners learn up to three daily feeding times and places,
 * and food-anticipatory activity outlasts a withheld feed by 3–10 days). Each feed reinforces the nearest slot in time
 * (within [MATCH_MIN]) or starts a new one; a slot whose time passes without a feed weakens by [EXTINCT] per missed day.
 */
class FeedClock {
    val slots = ArrayList<FeedSlot>(MAX_SLOTS)
    var lastFeedAt = -1e12; private set

    fun learn(t: Double, place: Vec3) {
        val sameMeal = t - lastFeedAt < MEAL_GAP_S
        lastFeedAt = t
        val m = minuteOf(t)
        val slot = slots.minByOrNull { abs(circ(m - it.minute)) }?.takeIf { abs(circ(m - it.minute)) < MATCH_MIN }
        if (slot != null) {
            slot.lastFedAt = t
            if (sameMeal) return
            slot.minute = wrap(slot.minute + 0.3 * circ(m - slot.minute))
            slot.place = slot.place.lerp(place, 0.3)
            slot.strength += GAIN * (1 - slot.strength)
            return
        }
        if (sameMeal) return
        if (slots.size >= MAX_SLOTS) slots.remove(slots.minBy { it.strength })
        slots += FeedSlot(m, place, GAIN, t)
    }

    /** Weaken slots whose feeding time came and went without food (any number of missed days). */
    fun extinguish(t: Double) {
        for (s in slots) {
            // the most recent occurrence whose grace period is over
            val dayStart = floor(t / DAY) * DAY
            var occ = dayStart + s.minute * 60
            if (occ + GRACE_MIN * 60 > t) occ -= DAY
            if (occ <= s.judgedOcc || occ + GRACE_MIN * 60 < s.createdAt) continue
            val prevJudged = if (s.judgedOcc.isInfinite()) occ - DAY else s.judgedOcc
            val missedDays = ((occ - prevJudged) / DAY).toInt().coerceIn(1, 60)
            val fed = s.lastFedAt >= occ - LEAD_MIN * 60 && s.lastFedAt <= occ + GRACE_MIN * 60
            val misses = if (fed) missedDays - 1 else missedDays
            if (misses > 0) s.strength *= EXTINCT.pow(misses)
            s.judgedOcc = occ
        }
        slots.removeAll { it.strength < 0.05 }
    }

    /**
     * The slot being anticipated now and how close to its time it is (0..1, peaking at the feeding time), or null.
     * No anticipation once that meal has been given.
     */
    fun anticipation(t: Double): Pair<FeedSlot, Double>? {
        val m = minuteOf(t)
        var best: Pair<FeedSlot, Double>? = null
        for (s in slots) {
            if (s.strength < THRESHOLD) continue
            val d = circ(m - s.minute)
            if (d < -LEAD_MIN || d > LAG_MIN) continue
            if (t - s.lastFedAt < (LEAD_MIN + LAG_MIN) * 60) continue
            val ramp = if (d < 0) 1 + d / LEAD_MIN else 1 - 0.5 * d / LAG_MIN
            val v = ramp * s.strength
            if (best == null || v > best.second * best.first.strength) best = s to ramp
        }
        return best
    }

    companion object {
        const val DAY = 86400.0
        const val MAX_SLOTS = 3
        /** Feeds this close together are one meal (s) [L]. */
        const val MEAL_GAP_S = 30 * 60.0
        /** A feed within this many minutes of a slot reinforces it [L]. */
        const val MATCH_MIN = 90.0
        /** Strength gained per reinforced feed: anticipation from the 3rd consistent feed (REE02, TOM25 days-scale) [L]. */
        @Ev(Evidence.DESIGN_ESTIMATE, "REE02 direction (entrainment over days); TOM25 12 days; value [L]") const val GAIN = 0.15
        /** Strength above which the animal anticipates. */
        const val THRESHOLD = 0.35
        /** Strength kept per missed feeding day: anticipation lasts ~2–5 missed days (REE02: 3–10 days). */
        @Ev(Evidence.ANALOGY, "REE02 goldfish: FAA persisted 3-10 days after food withheld") const val EXTINCT = 0.8
        /** Anticipation starts this long before the learned time and lingers after it (min) [L]. */
        @Ev(Evidence.DESIGN_ESTIMATE, "realism_gaps X3; lead/lag [L]") const val LEAD_MIN = 60.0
        const val LAG_MIN = 30.0
        /** A missed feed is judged this long after its time (min). */
        const val GRACE_MIN = 45.0

        fun minuteOf(t: Double): Double = ((t / 60.0) % 1440.0 + 1440.0) % 1440.0
        /** Signed circular difference in minutes, −720..720. */
        fun circ(d: Double): Double { var x = d % 1440.0; if (x > 720) x -= 1440.0; if (x < -720) x += 1440.0; return x }
        fun wrap(m: Double): Double = (m % 1440.0 + 1440.0) % 1440.0
    }
}

/** Continuous mood with memory (X4): fear and arousal rise with events and decay exponentially. */
class Mood {
    var fear = 0.0
    var arousal = 0.0
    fun decay(dt: Double) {
        if (fear > 0) fear *= exp(-dt / FEAR_TAU_S)
        if (arousal > 0) arousal *= exp(-dt / AROUSAL_TAU_S)
    }
    /** Food is approached less eagerly by a frightened animal (score ×). */
    val feedFactor: Double get() = 1 - FEAR_FEED * fear

    companion object {
        /** Fear and arousal time constants (s): fear ≈ 20 min half-life (cf. 10 min timidity after a chase, 06 §5.4) [L]. */
        @Ev(Evidence.DESIGN_ESTIMATE, "realism_gaps X4 [L]; same order as 06 §5.4 timidUntil 10 min") const val FEAR_TAU_S = 1730.0
        const val AROUSAL_TAU_S = 260.0
        const val FEAR_FEED = 0.5
    }
}

/**
 * Per-animal memory, clock and mood ([SpeciesBrain.mind]). Personality drift (X4, WIL94: boldness varies between
 * individuals and is consistent across contexts): a safe meal makes it a little bolder, an attack shyer, and taps
 * that come to nothing make it less skittish. Drift stays within ±[DRIFT_CAP] of the birth value (the ±15 % birth
 * jitter is kept as it was).
 */
class Mind(val o: Organism) {
    val places = PlaceMemory()
    val clock = FeedClock()
    val mood = Mood()
    /** How strongly the keeper's presence predicts food (TOM25: sea bream learned the diver who fed them) 0..1. */
    var keeperAssoc = 0.0
    var keeperCueAt = -1e9; private set
    var keeperCuePos: Vec3 = Vec3.ZERO; private set
    private val birthBoldness = o.boldness
    private val birthSkittish = o.skittishness
    private var lastAttackAt = -1e9
    private var pendingTapAt = -1e9
    private var lastDamp = o.aggressionDampUntil
    private var nextSlow = 0.0
    private var lastFeedDay = Int.MIN_VALUE
    private var assocJudgedDay = Int.MIN_VALUE

    /** Called from [SpeciesBrain.preTick]: mood decay every tick, clock and memory upkeep once a minute. */
    fun tick(c: Ctx) {
        mood.decay(c.dt)
        // a tactile session with a cleaner (the dance sets aggressionDampUntil) calms the client (SOA11, Ctenochaetus analogy)
        if (o.aggressionDampUntil > lastDamp + 1.0) {
            mood.fear *= TACTILE_CALM; mood.arousal *= TACTILE_CALM
            o.socialStress *= TACTILE_STRESS
        }
        lastDamp = o.aggressionDampUntil
        // a tap that was not followed by an attack within a minute is learned as harmless
        if (pendingTapAt > 0 && c.t - pendingTapAt > 60) {
            if (lastAttackAt < pendingTapAt) drift(skittish = -SKITTISH_HABITUATE)
            pendingTapAt = -1e9
        }
        if (c.t < nextSlow) return
        nextSlow = c.t + 60.0
        clock.extinguish(c.t)
        places.prune(c.t)
        val day = floor(c.t / FeedClock.DAY).toInt()
        if (assocJudgedDay == Int.MIN_VALUE) assocJudgedDay = day
        while (assocJudgedDay < day) {
            if (lastFeedDay < assocJudgedDay) keeperAssoc *= KEEPER_FORGET
            assocJudgedDay++
        }
    }

    /** The keeper fed the tank (Simulation.feed, every mode): learn time, place and the keeper as a cue. */
    fun onKeeperFeed(t: Double, at: Vec3, eats: Boolean) {
        if (!o.alive || !eats) return
        val sameMeal = t - clock.lastFeedAt < FeedClock.MEAL_GAP_S
        clock.learn(t, at)
        lastFeedDay = floor(t / FeedClock.DAY).toInt()
        if (!sameMeal) keeperAssoc += KEEPER_GAIN * (1 - keeperAssoc)
        mood.arousal = min(1.0, mood.arousal + 0.3)
    }

    /** It ate a particle: a safe meal makes a shy animal a little bolder. */
    fun onBite(t: Double) {
        if (mood.fear < 0.2) drift(bold = BOLD_PER_SAFE_MEAL)
    }

    fun onAttacked(c: Ctx, from: Vec3, mag: Double) {
        lastAttackAt = c.t
        mood.fear = min(1.0, mood.fear + FEAR_PER_ATTACK * mag)
        mood.arousal = min(1.0, mood.arousal + 0.5 * mag)
        places.note(PlaceKind.THREAT, o.pos, mag.coerceIn(0.2, 1.0), c.t)
        if (mag >= 0.4) drift(bold = -BOLD_PER_ATTACK, skittish = SKITTISH_PER_ATTACK)
    }

    fun onStartle(c: Ctx, mag: Double) {
        mood.fear = min(1.0, mood.fear + FEAR_PER_STARTLE * mag)
        mood.arousal = min(1.0, mood.arousal + mag)
        pendingTapAt = c.t
    }

    /** Someone at the tank (a gentle tap, a hand's shadow, the scraper): a cue that may mean food. */
    fun onKeeperCue(c: Ctx, at: Vec3) { keeperCueAt = c.t; keeperCuePos = at; pendingTapAt = c.t }

    /** Reached cover after a flee: remember it as a refuge. */
    fun onRefuge(c: Ctx, at: Vec3) { places.note(PlaceKind.REFUGE, at, 1.0, c.t) }

    /**
     * A remembered refuge to run to from a threat at [from], or null: within [REFUGE_REACH_CM], not toward the threat,
     * and not where it was attacked.
     */
    fun refugeFor(c: Ctx, from: Vec3): Vec3? {
        val me = o.pos
        val away = (me - from).normalized(Vec3.Y)
        return places.best(PlaceKind.REFUGE, c.t) { p, s ->
            val d = p.pos.distanceTo(me)
            if (d > REFUGE_REACH_CM || places.nearThreat(p.pos, c.t, 8.0)) return@best 0.0
            val dir = (p.pos - me).normalized(away)
            if (d > 4 && (dir dot away) < -0.3) return@best 0.0
            if (p.pos.distanceTo(from) < 6) return@best 0.0
            s / (1 + d / 30)
        }?.pos
    }

    private fun drift(bold: Double = 0.0, skittish: Double = 0.0) {
        if (bold != 0.0) o.boldness = (o.boldness + bold).coerceIn(max(0.0, birthBoldness - DRIFT_CAP), min(1.0, birthBoldness + DRIFT_CAP))
        if (skittish != 0.0) o.skittishness = (o.skittishness + skittish).coerceIn(max(0.0, birthSkittish - DRIFT_CAP), min(1.0, birthSkittish + DRIFT_CAP))
    }

    /** Learned anticipation of the next feed, if any: slot and ramp 0..1. */
    fun anticipation(t: Double): Pair<FeedSlot, Double>? = clock.anticipation(t)

    companion object {
        @Ev(Evidence.DESIGN_ESTIMATE, "realism_gaps X4 [L]") const val FEAR_PER_ATTACK = 0.6
        const val FEAR_PER_STARTLE = 0.3
        /** Drift per event and the cap around the birth value (WIL94 direction; values [L]). */
        @Ev(Evidence.DESIGN_ESTIMATE, "WIL94 direction; drift sizes [L]") const val BOLD_PER_SAFE_MEAL = 0.002
        const val BOLD_PER_ATTACK = 0.02
        const val SKITTISH_PER_ATTACK = 0.01
        const val SKITTISH_HABITUATE = 0.003
        const val DRIFT_CAP = 0.15
        /** Tactile cleaner contact halves fear/arousal and trims social stress (SOA11 analogy; sizes [L]). */
        @Ev(Evidence.ANALOGY, "SOA11 Ctenochaetus cortisol after mechanical contact; sizes [L]") const val TACTILE_CALM = 0.5
        const val TACTILE_STRESS = 0.8
        /** Keeper association gained per meal and kept per day without one (TOM25 analogy; values [L]). */
        @Ev(Evidence.ANALOGY, "TOM25 sea bream learned their feeder over 12 days; values [L]") const val KEEPER_GAIN = 0.06
        const val KEEPER_FORGET = 0.9
        /** Above this association a keeper cue brings the fish to the glass. */
        const val KEEPER_BEG = 0.35
        /** Farthest remembered refuge used in a flee (cm) [L]. */
        const val REFUGE_REACH_CM = 45.0

        /** Fish whose brains take part in feeding anticipation and keeper begging. */
        fun anticipates(k: BrainKind): Boolean = k.isFish && k !in NO_ANTICIPATION
        private val NO_ANTICIPATION = setOf(
            BrainKind.EEL, BrainKind.HAWK, BrainKind.CARDINAL, BrainKind.ENGINEER_GOBY, BrainKind.DIAMOND_GOBY, BrainKind.LEOPARD_WRASSE,
            BrainKind.WATCHMAN_GOBY,
        )
    }
}
