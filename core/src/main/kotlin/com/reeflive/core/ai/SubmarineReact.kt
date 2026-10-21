package com.reeflive.core.ai

import com.reeflive.core.geom.Vec3
import com.reeflive.core.geom.smoothstep
import com.reeflive.core.sim.BrainKind
import com.reeflive.core.sim.Organism
import com.reeflive.core.sim.Simulation
import com.reeflive.core.sim.StressModel
import com.reeflive.core.sim.SubMemory
import com.reeflive.core.sim.SubReaction
import com.reeflive.core.sim.Submarine
import com.reeflive.core.sim.SubmarineRules
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The fish's eyes on the toy submarine ([Submarine], [SubmarineRules]). Every [SENSE_DT] sim seconds while the sub is
 * out and visible, each animal near it gets a reaction: STARTLE and HIDE go through the species' own escape
 * ([SpeciesBrain.onTap], the glass-knock flee: a goby into its burrow, a clown into its host), the others are held on the
 * animal's [SubmarineReact] for a moment and refreshed while they last. Loitering builds habituation ([SubMemory]).
 * An invisible sub is not perceived at all: every held reaction is dropped and nothing is evaluated.
 */
class SubmarineSense {
    val memory = SubMemory()
    private var acc = 0.0
    private val current = LinkedHashMap<String, SubReaction>()
    /** Who is reacting to the sub right now, and how (UI readout, tests). */
    val reactions: Map<String, SubReaction> get() = current
    /** Startles caused by the sub since the last [clear] (tests, UI). */
    var startles = 0; private set

    fun tick(sim: Simulation, dt: Double, agentOf: (Organism) -> Agent?) {
        val sub = sim.submarine
        if (!sub.active || !sub.visible) {
            if (current.isNotEmpty()) { for (o in sim.organisms) agentOf(o)?.let { react(it)?.drop() }; current.clear() }
            acc = 0.0
            return
        }
        acc += dt
        if (acc < SENSE_DT) return
        val step = acc; acc = 0.0
        val t = sim.simTimeSec
        val night = sim.perception.night
        val sp = sub.pos
        for (o in sim.organisms) {
            val a = agentOf(o) ?: continue
            val r = react(a) ?: continue
            val d = o.pos.distanceTo(sp)
            if (!o.alive || d > MAX_CM) { r.drop(); current.remove(o.id); continue }
            val closing = sub.vel dot (o.pos - sp).normalized(Vec3.X)
            val guard = guardPoint(a)
            val guardD = guard?.distanceTo(sp) ?: Double.MAX_VALUE
            val h = memory.habituation(o.id, t)
            val k = SubmarineRules.reaction(o, d, closing, guardD, night, h)
            a.ctx.p = sim.perception
            when (k) {
                SubReaction.NONE -> {
                    // a fish that already ignores a harmless sub nearby keeps getting used to it: without this,
                    // habituation stalled right at the ignore level and every short trip away tipped it back under
                    if (h >= SubmarineRules.HABIT_IGNORE && d < SubmarineRules.SEE_CM && closing <= SubmarineRules.STARTLE_SPEED) memory.exposed(o.id, t, step)
                    r.drop(); current.remove(o.id); continue
                }
                SubReaction.STARTLE, SubReaction.HIDE -> {
                    if (t >= r.cooldownUntil) {
                        val mag = if (k == SubReaction.STARTLE) SubmarineRules.startleMag(closing, d, SubmarineRules.startleReach(o, h)) else 0.5
                        r.drop()
                        a.brain.onTap(a.ctx, sp, mag)
                        o.startle = max(o.startle, 0.12 * mag)
                        StressModel.addSocial(o, Simulation.TAP_SOCIAL * 0.25 * mag)
                        r.cooldownUntil = t + if (k == SubReaction.STARTLE) 5.0 else 8.0
                        if (k == SubReaction.STARTLE) { memory.startled(o.id, t); startles++ } else memory.exposed(o.id, t, step)
                    } else memory.exposed(o.id, t, step)
                }
                else -> { r.hold(k, t + HOLD_S, guard); memory.exposed(o.id, t, step) }
            }
            current[o.id] = k
        }
    }

    /** What [a] guards from the sub: a clown its host, a dottyback its cave; null for everyone else. */
    private fun guardPoint(a: Agent): Vec3? = when (a.brain.o.brainKind) {
        BrainKind.CLOWN -> a.brain.anchorPos(a.ctx, "host")
        BrainKind.DOTTYBACK -> a.brain.o.homePos
        else -> null
    }

    private fun react(a: Agent): SubmarineReact? {
        for (b in a.brain.utility.behaviors) if (b is SubmarineReact) return b
        return null
    }

    fun clear() { memory.clear(); current.clear(); acc = 0.0; startles = 0 }

    companion object {
        const val SENSE_DT = 0.25
        /** A held reaction lasts this long past the last sighting that called for it. */
        const val HOLD_S = 1.5
        /** Nothing reacts beyond this (cm). */
        const val MAX_CM = 40.0
    }
}

/**
 * Plays the slow-sub reactions ([SubmarineSense]) for any brain: INVESTIGATE hovers a few cm off the sub facing it
 * and follows it about; POSTURE guards the clown's host or the dottyback's cave with fins up and short darts at the
 * sub (a perched hawkfish or a denned eel just turns to watch); AVOID keeps clear and drifts to cover; FREEZE holds a
 * shrimp or crab still. Idle (score 0, no rng, no utility noise) whenever the sub is away, hidden or off.
 */
class SubmarineReact(val b: SpeciesBrain) : Behavior("submarine", "Watching the submarine") {
    override val noisy: Boolean get() = false
    private var kind = SubReaction.NONE
    private var until = -1e9
    private var guard: Vec3? = null
    internal var cooldownUntil = -1e9
    private var playing = SubReaction.NONE
    /** The reaction being played (or about to be), for the thoughts readout. */
    val reaction: SubReaction get() = if (playing != SubReaction.NONE) playing else kind
    val guarding: Boolean get() = guard != null
    private var cover: Vec3 = Vec3.ZERO
    private var holdPos: Vec3 = Vec3.ZERO
    private var holdHeading: Vec3 = Vec3.X
    private var holdUp: Vec3 = Vec3.Y
    private var holdPosture = 0
    private var wasAttached = false

    override val label: String
        get() = when (playing) {
            SubReaction.INVESTIGATE -> "Checking out the submarine"
            SubReaction.POSTURE -> if (guard != null) "Guarding its home from the submarine" else "Watching the submarine"
            SubReaction.AVOID -> "Keeping away from the submarine"
            SubReaction.FREEZE -> "Freezing still"
            else -> "Watching the submarine"
        }

    internal fun hold(k: SubReaction, until: Double, guard: Vec3?) { kind = k; this.until = until; this.guard = guard }
    internal fun drop() { kind = SubReaction.NONE; until = -1e9 }

    private fun live(c: Ctx): Submarine? = c.sim.submarine.takeIf { it.active && it.visible }

    override fun score(c: Ctx): Double {
        if (kind == SubReaction.NONE || c.t > until || live(c) == null) return 0.0
        return when (kind) {
            SubReaction.FREEZE -> 0.9
            SubReaction.AVOID -> 0.86
            SubReaction.POSTURE -> 0.84
            SubReaction.INVESTIGATE -> 0.8
            else -> 0.0
        }
    }

    override fun enter(c: Ctx) {
        val o = c.o
        playing = kind
        holdPos = o.pos; holdHeading = o.heading; holdUp = o.up; holdPosture = o.posture; wasAttached = o.attached
        val sub = c.sim.submarine.pos
        cover = if (kind == SubReaction.AVOID) c.coverPoint(sub, 25.0) else o.pos
    }

    override fun update(c: Ctx, out: SteerIntent) {
        val o = c.o
        val sub = live(c)
        if (sub == null || kind != playing || c.t > until) {
            finished = true
            if (playing == SubReaction.FREEZE || wasAttached && playing == SubReaction.POSTURE) hold(out)
            return
        }
        val sp = sub.pos
        val away = o.pos - sp
        val d = away.length()
        val toSub = (sp - o.pos).normalized(o.heading)
        when (playing) {
            SubReaction.INVESTIGATE -> {
                val standoff = Submarine.RADIUS_CM + 2.5 + 0.6 * c.L
                val goal = c.safe(sp + away.normalized(Vec3.Z) * standoff, 1.0)
                out.seek(goal, max(o.cruiseSpeed * 1.3, min(o.burstSpeed * 0.6, sub.speed + 2.0)), arrive = 1.5, urgency = 0.4)
                out.hover = true
                out.faceDir = toSub
                o.dorsalRaise = max(o.dorsalRaise, 0.3)
            }
            SubReaction.POSTURE -> {
                val g = guard
                if (wasAttached || g == null) {
                    // perched or denned: stay put and turn to watch it
                    if (wasAttached) { out.place(holdPos, horizontalHeading(toSub, holdHeading), holdUp, 3.0); out.posture = holdPosture }
                    else { out.seek(holdPos, o.cruiseSpeed * 0.5, arrive = 1.5); out.hover = true; out.faceDir = toSub }
                    if (o.brainKind == BrainKind.EEL) o.mouth = max(o.mouth, 0.5 + 0.3 * sin(c.t * 2.0))
                } else {
                    // between the intruder and home, with a short dart at it every couple of seconds
                    val fromHome = sp - g
                    val post = c.safe(g + fromHome.normalized(Vec3.Y) * min(6.0, fromHome.length() * 0.5), 1.0)
                    val phase = (elapsed % DART_PERIOD_S)
                    if (phase < DART_S && d > Submarine.RADIUS_CM + 1.5) {
                        out.seek(sp + away.normalized(Vec3.Z) * (Submarine.RADIUS_CM + 1.2), o.burstSpeed * 0.6, arrive = 0.8, urgency = 0.6)
                    } else {
                        out.seek(post, o.cruiseSpeed, arrive = 1.5, urgency = 0.4); out.hover = true
                    }
                    out.faceDir = toSub
                }
                o.dorsalRaise = 1.0
            }
            SubReaction.AVOID -> {
                if (d < SubmarineRules.SHY_CM * 0.7) { out.fleeFrom = sp; out.urgency = 0.5 }
                else { out.seek(cover, o.cruiseSpeed * 0.8, arrive = 2.0, urgency = 0.3); out.hover = true }
                o.finClamp = max(o.finClamp, 0.4)
            }
            SubReaction.FREEZE -> {
                hold(out)
                val k = smoothstep(0.0, 0.4, elapsed)
                if (o.brainKind.isShrimp) o.groom = max(o.groom, 0.8 * k)
                o.walkAmt = 0.0
            }
            else -> finished = true
        }
    }

    private fun hold(out: SteerIntent) {
        out.place(holdPos, holdHeading, holdUp, 6.0)
        out.posture = holdPosture
    }

    companion object {
        const val DART_PERIOD_S = 2.6
        const val DART_S = 0.5
    }
}
