package com.reeflive.app.render

import com.reeflive.core.custom.economy.Act
import com.reeflive.core.custom.economy.Activity
import com.reeflive.core.custom.economy.PetKind
import com.reeflive.core.custom.economy.Place
import com.reeflive.core.custom.economy.Prop
import com.reeflive.core.custom.economy.Role
import com.reeflive.core.custom.economy.StoreCast
import com.reeflive.core.custom.economy.StorePerson
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The people and the pet of a fish store going about their day (core StoreLife: who they are and their routines;
 * docs/research/fish_stores.md). GL thread only: [step] walks each person through their routine's steps and sets their
 * pose; StoreRoomScene draws them from it. Positions are store cm: x from the first tank's bay centre along the aisle
 * (bay b is centred on b · [bay]), z toward the front door, y from the floor; they never move when the keeper walks to
 * another tank (the scene shifts the whole store instead).
 *
 * Paths keep off the tanks and the counter: out to the person's own aisle lane, along it, and in to the spot; the staff
 * leave the till round the counter's open end. A person at [Place.BACK] or [Place.OUTSIDE] is out of sight for the
 * step. Lines they say become speech bubbles (at most [MAX_BUBBLES] at once, the keeper's own moments first).
 */
internal class StorePeople {
    /** One person: where they are, what they are doing, their pose this frame. */
    class Agent(val person: StorePerson) {
        val kid = person.role == Role.KID
        val staff = person.role == Role.CLERK || person.role == Role.AQUARIST || person.role == Role.STOCKER || person.role == Role.GREETER
        var x = 0f; var z = 0f; var yaw = 0f
        var hidden = false
        // path: waypoints, then the spot's facing
        val wx = FloatArray(6); val wz = FloatArray(6)
        var wn = 0; var wi = 0
        var faceYaw = 0f
        var hideAtEnd = false
        var walking = false
        var walked = 0f
        // routine
        var activity: Activity? = null
        var stepIdx = 0
        var act = Act.IDLE
        var prop = Prop.NONE
        var actLeft = 0f
        var actT = 0f
        var mode = MODE_ROUTINE
        // speech
        var say: String? = null
        var sayLeft = 0f
        var sayAge = 0f
        var pendingSay: String? = null
        var pendingIn = 0f
        /** The lines of the step being walked to (said on arrival). */
        var stepSay: List<String> = emptyList()
        // pose (radians, cm)
        var lean = 0f; var headYaw = 0f; var headPitch = 0f
        var armLp = 0f; var armLr = 0f; var armRp = 0f; var armRr = 0f
        var legL = 0f; var legR = 0f; var bob = 0f
        /** Head top height (cm over the floor) for the bubble and picking, from the model. */
        var headTop = 170f
    }

    /** The shop pet: wanders the aisle, naps at its spot. */
    class Pet(val kind: PetKind, val name: String, val nap: Place) {
        var x = 0f; var y = 0f; var z = 0f; var yaw = 0f
        var asleep = false
        var left = 0f
        val wx = FloatArray(4); val wz = FloatArray(4)
        var wn = 0; var wi = 0
        var walking = false
        var walked = 0f
        var tail = 0f
        var hop = 0f
        /** Walking to its nap spot (it curls up on arrival). */
        var toNap = false
    }

    val agents = ArrayList<Agent>()
    var pet: Pet? = null
        private set
    var cast: StoreCast? = null
        private set
    private var rnd = Random(1)
    private var time = 0f

    // ---- the store's layout (StoreRoomScene keeps these current)
    var bay = 180f
    var tanks = 0
    var at = 0
    var front = 540f
    var entryZ = 130f
    /** Half the glass length and the stand's front (cm) of each tank bay. */
    var halfLen = FloatArray(0)
    var depth = FloatArray(0)

    // ---- the keeper's moments (the scene passes them on from StoreRoomControl)
    private var tradeLine: String? = null
    private var tradeAsked = false
    private var tradeDue = TRADE_AFTER_S
    /** Set once when the trader has walked up and asked: the host shows the trade card. */
    var tradeAsk = false
    private var greeted = false

    fun reset(c: StoreCast?, seed: Long, trade: String?) {
        cast = c
        rnd = Random(seed)
        time = 0f
        agents.clear()
        tradeLine = trade; tradeAsked = false; tradeAsk = false; tradeDue = TRADE_AFTER_S + rnd.nextFloat() * 10f
        greeted = false
        pet = c?.pet?.let { Pet(it.kind, it.name, it.nap) }
        if (c == null) return
        for (p in c.people) {
            val a = Agent(p)
            agents += a
            spot(a, p.start)
            a.x = sx; a.z = sz; a.yaw = sYaw; a.faceYaw = sYaw
            a.hidden = sHide
            a.actLeft = 1f + rnd.nextFloat() * 5f
            a.act = if (p.role == Role.CLERK) Act.TILL else Act.IDLE
        }
        pet?.let { pt ->
            napSpot(pt)
            pt.x = sx; pt.z = sz; pt.y = sy; pt.asleep = true; pt.left = 8f + rnd.nextFloat() * 10f
        }
    }

    // ============================================================== stepping

    fun step(dt: Float, trade: String?, answer: Int) {
        if (cast == null) return
        time += dt
        if (trade != tradeLine) { tradeLine = trade; tradeAsked = false; tradeAsk = false; tradeDue = time + 6f }
        // the clerk (or the greeter at the door) says hello as the keeper comes in
        if (!greeted && time > GREET_AT_S) {
            greeted = true
            val g = agents.firstOrNull { it.person.role == Role.GREETER } ?: agents.firstOrNull { it.person.role == Role.CLERK }
            if (g != null) { speak(g, pick(g.person.hello), force = true); if (!g.walking) g.act = Act.WAVE; g.actLeft = max(g.actLeft, 2.5f) }
        }
        val t = cast!!.trader
        if (t != null && tradeLine != null && !tradeAsked && time > tradeDue) {
            agents.firstOrNull { it.person === t }?.let { a -> if (!a.hidden) { tradeAsked = true; a.mode = MODE_TRADE_GO; go(a, Place.KEEPER) } }
        }
        for (a in agents) {
            if (a.pendingSay != null) {
                a.pendingIn -= dt
                if (a.pendingIn <= 0f) { speak(a, a.pendingSay!!, force = true); a.pendingSay = null }
            }
            if (a.say != null) { a.sayLeft -= dt; a.sayAge += dt; if (a.sayLeft <= 0f) a.say = null }
            if (a.mode == MODE_TRADE_WAIT && answer != 0) {
                val c = cast!!
                speak(a, pick(if (answer > 0) c.tradeYes else c.tradeNo), force = true)
                a.act = if (answer > 0) Act.WAVE else Act.TALK; a.actLeft = 3.5f; a.actT = 0f
                a.mode = MODE_ROUTINE
            }
            if (a.walking) walk(a, dt) else act(a, dt)
            pose(a, dt)
        }
        pet?.let { stepPet(it, dt) }
    }

    private fun act(a: Agent, dt: Float) {
        a.actT += dt
        a.yaw = turn(a.yaw, a.faceYaw, dt * TURN)
        if (a.mode == MODE_TRADE_WAIT) return
        a.actLeft -= dt
        if (a.actLeft > 0f) return
        nextStep(a)
    }

    /** The next step of the person's routine (a new routine, by weight, after the last one). */
    private fun nextStep(a: Agent) {
        val cur = a.activity
        if (cur != null && a.stepIdx + 1 < cur.steps.size) a.stepIdx++
        else {
            val rs = a.person.routines
            var total = 0
            for (r in rs) total += r.weight
            var k = rnd.nextInt(max(total, 1))
            var pick = rs[0]
            for (r in rs) { if (k < r.weight) { pick = r; break }; k -= r.weight }
            a.activity = pick
            a.stepIdx = 0
        }
        val s = a.activity!!.steps[a.stepIdx]
        a.prop = s.prop
        a.act = s.act
        a.actLeft = (s.seconds * (0.8 + rnd.nextDouble() * 0.5)).toFloat()
        a.actT = 0f
        if (s.place == Place.HERE) arrive(a, s.say)
        else { a.stepSay = s.say; go(a, s.place, s.near) }
    }

    /** Arrived at the step's spot: the act starts (and its line, sometimes). */
    private fun arrive(a: Agent, say: List<String>) {
        a.actT = 0f
        if (a.hideAtEnd) { a.hidden = true; a.hideAtEnd = false }
        if (a.mode == MODE_TRADE_GO) {
            a.mode = MODE_TRADE_WAIT
            a.act = Act.TRADE
            tradeLine?.let { speak(a, it, force = true, hold = 600f) }
            tradeAsk = true
            return
        }
        if (say.isNotEmpty() && !a.hidden && rnd.nextFloat() < 0.75f) speak(a, pick(say))
        if (a.act == Act.TAP_GLASS) {
            // a staff member nearby asks them not to
            val c = cast!!
            val staff = agents.filter { it.staff && !it.hidden }.minByOrNull { abs(it.x - a.x) }
            if (staff != null && c.noTapping.isNotEmpty()) { staff.pendingSay = pick(c.noTapping); staff.pendingIn = 1.4f }
        }
    }

    /** Plans a walk to [place] (the step's spot). */
    private fun go(a: Agent, place: Place, near: Boolean = false) {
        spot(a, place, near)
        val lane = LANE0 + (a.person.slot % 4) * LANE_STEP
        var n = 0
        if (a.hidden) {
            // out of the back door, or in from the street
            a.hidden = false
        }
        val counterX = tanks * bay
        val behindNow = a.z < 45f && abs(a.x - counterX) < 95f
        val behindThen = sz < 45f && abs(sx - counterX) < 95f
        if (behindNow) { a.wx[n] = counterX - SIDE; a.wz[n] = a.z; n++ }
        if (!(behindNow && behindThen)) {
            a.wx[n] = if (behindNow) counterX - SIDE else a.x; a.wz[n] = lane; n++
            a.wx[n] = if (behindThen) counterX - SIDE else sx; a.wz[n] = lane; n++
            if (behindThen) { a.wx[n] = counterX - SIDE; a.wz[n] = sz; n++ }
        }
        a.wx[n] = sx; a.wz[n] = sz; n++
        a.wn = n; a.wi = 0
        a.faceYaw = sYaw
        a.hideAtEnd = sHide
        a.walking = true
    }

    private fun walk(a: Agent, dt: Float) {
        val speed = if (a.kid) KID_SPEED else WALK_SPEED
        var left = speed * dt
        while (left > 0f && a.wi < a.wn) {
            val dx = a.wx[a.wi] - a.x; val dz = a.wz[a.wi] - a.z
            val d = sqrt(dx * dx + dz * dz)
            if (d < 0.5f) { a.wi++; continue }
            val m = min(left, d)
            a.x += dx / d * m; a.z += dz / d * m
            a.walked += m
            left -= m
            a.yaw = turn(a.yaw, atan2(dx, dz), dt * TURN * 1.6f)
        }
        if (a.wi >= a.wn) {
            a.walking = false
            val say = a.stepSay
            a.stepSay = emptyList()
            arrive(a, say)
        }
    }

    // ============================================================== places

    private var sx = 0f; private var sz = 0f; private var sy = 0f; private var sYaw = 0f; private var sHide = false

    /** Resolves [place] for [a] into (sx, sz, sYaw, sHide). */
    private fun spot(a: Agent, place: Place, near: Boolean = false) {
        sHide = false
        when (place) {
            Place.TANK -> {
                if (tanks <= 1 && !near) { spot(a, Place.ENTRY); return }
                var best = 0f; var bx = 0f; var bz = 0f
                for (tryN in 0 until 4) {
                    val b = when {
                        near -> if (at + 1 < tanks) at + 1 else max(at - 1, 0)
                        else -> { var k = rnd.nextInt(max(tanks - 1, 1)); if (k >= at) k++; min(k, tanks - 1) }
                    }
                    val h = halfLen.getOrElse(b) { 60f }
                    val x = b * bay + (rnd.nextFloat() * 2f - 1f) * max(h - 18f, 4f)
                    val z = depth.getOrElse(b) { 52f } + if (a.kid) 24f else 34f
                    var clear = Float.MAX_VALUE
                    for (o in agents) if (o !== a && !o.hidden) {
                        val ox = if (o.walking) o.wx[o.wn - 1] else o.x
                        val oz = if (o.walking) o.wz[o.wn - 1] else o.z
                        clear = min(clear, (ox - x) * (ox - x) + (oz - z) * (oz - z))
                    }
                    if (clear > best) { best = clear; bx = x; bz = z }
                    if (clear > 60f * 60f) break
                }
                sx = bx; sz = bz; sYaw = PI.toFloat()
            }
            Place.KEEPER -> {
                sx = at * bay + halfLen.getOrElse(at) { 60f } + 30f
                sz = depth.getOrElse(at) { 52f } + 75f
                sYaw = atan2(-(halfLen.getOrElse(at) { 60f } + 30f), 330f)
            }
            Place.COUNTER -> { sx = tanks * bay + (rnd.nextFloat() - 0.5f) * 70f; sz = 128f; sYaw = PI.toFloat() }
            Place.TILL -> { sx = tanks * bay + 26f; sz = 12f; sYaw = 0f }
            Place.DOOR -> { sx = (rnd.nextFloat() - 0.5f) * 50f + 40f; sz = front - 45f; sYaw = 0f }
            Place.OUTSIDE -> { sx = 0f; sz = front + 140f; sYaw = 0f; sHide = true }
            Place.ENTRY -> { sx = -bay + (rnd.nextFloat() - 0.5f) * 70f; sz = entryZ; sYaw = PI.toFloat() }
            Place.BACK -> { sx = (tanks + 1) * bay - 30f; sz = -36f; sYaw = PI.toFloat(); sHide = true }
            Place.HERE -> { sx = a.x; sz = a.z; sYaw = a.yaw }
        }
    }

    // ============================================================== speech

    private fun pick(lines: List<String>): String = lines[rnd.nextInt(lines.size)]

    /** [a] says [line] in a bubble, unless [MAX_BUBBLES] are up already ([force]: the keeper's moments always show). */
    private fun speak(a: Agent, line: String, force: Boolean = false, hold: Float = 0f) {
        if (a.hidden) return
        if (!force) {
            var up = 0
            for (o in agents) if (o.say != null) up++
            if (up >= MAX_BUBBLES) return
        }
        a.say = a.person.name + ": " + line
        a.sayLeft = if (hold > 0f) hold else 3.2f + line.length * 0.045f
        a.sayAge = 0f
    }

    /** The keeper tapped person [slot]: they turn round and say hello. */
    fun hello(slot: Int) {
        val a = agents.getOrNull(slot) ?: return
        if (a.hidden) return
        speak(a, pick(a.person.hello), force = true)
        if (!a.walking && a.mode == MODE_ROUTINE) { a.act = Act.WAVE; a.actLeft = 2.5f; a.actT = 0f; a.faceYaw = atan2(at * bay - a.x, 420f - a.z) }
    }

    /** Called when the trade card closes without the trader's walk (left the store): nothing more to say. */
    fun tradeDone() { tradeAsk = false }

    /** Bubbles to show: copies up to [texts].size of them (text, alpha) and their anchors (store cm). */
    fun bubbles(texts: Array<String?>, pos: FloatArray, alpha: FloatArray): Int {
        var n = 0
        for (a in agents) {
            val s = a.say ?: continue
            if (a.hidden || n >= texts.size) continue
            texts[n] = s
            pos[n * 3] = a.x; pos[n * 3 + 1] = a.headTop + 12f + a.bob; pos[n * 3 + 2] = a.z
            alpha[n] = min(1f, min(a.sayAge * 5f, a.sayLeft * 2.5f))
            n++
        }
        return n
    }

    // ============================================================== poses

    private fun pose(a: Agent, dt: Float) {
        val t = a.actT
        var lean = 0f; var hy = 0f; var hp = 0f
        var lp = 0f; var lr = 0.06f; var rp = 0f; var rr = 0.06f
        var bob = 0f; var legL = 0f; var legR = 0f
        val idle = time * 0.7f + a.person.slot * 1.9f
        if (a.walking) {
            val ph = a.walked / STRIDE * 2f * PI.toFloat()
            val sw = sin(ph)
            legL = sw * 0.42f; legR = -sw * 0.42f
            lp = -sw * 0.32f; rp = sw * 0.32f
            bob = abs(sin(ph)) * 1.6f
            lean = 0.05f
            hy = sin(idle * 0.6f) * 0.12f
            if (a.prop == Prop.BUCKET || a.prop == Prop.BOX) { rp = -0.5f; lp = -0.5f; rr = -0.15f; lr = -0.15f }
        } else when (a.act) {
            Act.IDLE -> { hy = sin(idle * 0.5f) * 0.45f; hp = sin(idle * 0.3f) * 0.06f; bob = sin(idle * 1.6f) * 0.3f }
            Act.BROWSE -> { lean = 0.07f; hy = sin(t * 0.45f) * 0.35f; hp = 0.08f; lp = 0.25f; rp = 0.25f; lr = -0.1f; rr = -0.1f }
            Act.POINT -> { rp = -1.35f + sin(t * 2f) * 0.06f; rr = 0.12f; hy = -0.12f; hp = -0.05f }
            Act.PHOTO -> { rp = -1.5f; lp = -1.45f; rr = -0.35f; lr = -0.35f; hp = 0.05f; lean = -0.02f }
            Act.PRESS_GLASS -> { lean = 0.16f; rp = -1.45f; lp = -1.45f; rr = 0.1f; lr = 0.1f; hy = sin(t * 0.8f) * 0.3f; bob = sin(t * 3f) * 0.6f }
            Act.TAP_GLASS -> { lean = 0.1f; rp = -1.35f + sin(t * 14f) * 0.12f; hp = -0.05f }
            Act.JUMP -> {
                val j = abs(sin(t * 5.2f)); bob = j * 13f
                rp = -2.5f - j * 0.4f; lp = -2.5f - j * 0.4f; rr = 0.35f; lr = 0.35f; legL = -j * 0.25f; legR = -j * 0.25f; hp = -0.15f
            }
            Act.TALK, Act.TRADE -> {
                rp = -0.55f + sin(t * 2.3f) * 0.3f; rr = 0.15f; lp = -0.25f + sin(t * 1.7f + 1f) * 0.15f
                hp = sin(t * 3.1f) * 0.06f; hy = sin(t * 0.7f) * 0.15f
                if (a.act == Act.TRADE) { rp = -1.1f; rr = 0.05f }
            }
            Act.WAVE -> { rp = -2.75f; rr = 0.35f + sin(t * 9f) * 0.35f; hy = 0f; hp = -0.08f }
            Act.FEED -> { rp = -1.85f + sin(t * 9f) * 0.08f; rr = -0.05f; lean = 0.05f; hp = -0.15f }
            Act.NET -> { rp = -1.5f + sin(t * 1.6f) * 0.25f; rr = sin(t * 1.3f) * 0.35f; lean = 0.12f; hp = 0.12f; hy = sin(t * 1.3f) * 0.2f }
            Act.CLEAN -> { rp = -1.45f + sin(t * 4f) * 0.25f; rr = 0.1f + sin(t * 4f + 1.6f) * 0.25f; lean = 0.08f; hy = sin(t * 0.5f) * 0.2f }
            Act.TEST -> { rp = -0.95f; lp = -0.9f; rr = -0.3f; lr = -0.3f; hp = 0.4f; rp += sin(t * 2.5f) * 0.12f }
            Act.CARRY -> { rp = -0.5f; lp = -0.5f; rr = -0.15f; lr = -0.15f; lean = -0.04f }
            Act.SWEEP -> { rp = -0.7f + sin(t * 2.4f) * 0.15f; lp = -0.55f; rr = -0.3f + sin(t * 2.4f) * 0.35f; lr = -0.25f; lean = 0.12f; hp = 0.2f }
            Act.TILL -> { rp = -0.85f + sin(t * 7f) * 0.06f; lp = -0.8f + sin(t * 6f + 1f) * 0.06f; rr = -0.12f; lr = -0.12f; hp = 0.32f; lean = 0.06f }
            Act.SIP -> { val s = max(0f, sin(t * 0.9f)); rp = -0.5f - s * 1.6f; rr = -0.45f * s; hp = -0.12f * s; hy = (1f - s) * sin(idle * 0.5f) * 0.3f }
            Act.READ -> { rp = -0.95f; lp = -0.95f; rr = -0.35f; lr = -0.35f; hp = 0.42f; hy = sin(t * 0.4f) * 0.08f }
        }
        val k = min(1f, dt * POSE_EASE)
        a.lean += (lean - a.lean) * k; a.headYaw += (hy - a.headYaw) * k; a.headPitch += (hp - a.headPitch) * k
        a.armLp += (lp - a.armLp) * k; a.armLr += (lr - a.armLr) * k; a.armRp += (rp - a.armRp) * k; a.armRr += (rr - a.armRr) * k
        if (a.walking || a.act == Act.JUMP) { a.legL = legL; a.legR = legR; a.bob = bob }
        else { a.legL += (legL - a.legL) * k; a.legR += (legR - a.legR) * k; a.bob += (bob - a.bob) * k }
    }

    // ============================================================== the pet

    private fun napSpot(p: Pet) {
        sy = 0f
        when (p.nap) {
            Place.COUNTER -> { sx = tanks * bay - 40f; sz = 66f; sy = COUNTER_TOP }
            else -> { sx = -bay + 40f; sz = entryZ + 20f }
        }
    }

    private fun stepPet(p: Pet, dt: Float) {
        p.tail = sin(time * (if (p.kind == PetKind.DOG) 9f else 2.2f)) * (if (p.asleep) 0f else 0.5f)
        if (p.walking) {
            var left = (if (p.kind == PetKind.DOG) 85f else 60f) * dt
            while (left > 0f && p.wi < p.wn) {
                val dx = p.wx[p.wi] - p.x; val dz = p.wz[p.wi] - p.z
                val d = sqrt(dx * dx + dz * dz)
                if (d < 0.5f) { p.wi++; continue }
                val m = min(left, d)
                p.x += dx / d * m; p.z += dz / d * m; p.walked += m; left -= m
                p.yaw = turn(p.yaw, atan2(dx, dz), dt * TURN * 2f)
            }
            if (p.wi >= p.wn) {
                p.walking = false
                if (p.toNap) {
                    p.toNap = false; p.asleep = true; p.left = 25f + rnd.nextFloat() * 25f
                    if (p.nap == Place.COUNTER) p.hop = 1f
                } else p.left = 4f + rnd.nextFloat() * 6f
            }
            return
        }
        if (p.hop > 0f) {
            // the cat hops up onto the counter for its nap (or down off it)
            p.hop = max(0f, p.hop - dt / HOP_S)
            napSpot(p)
            val u = 1f - p.hop
            p.y = if (p.asleep) COUNTER_TOP * u + sin(u * PI.toFloat()) * 18f else COUNTER_TOP * (1f - u) + sin(u * PI.toFloat()) * 18f
            if (p.asleep) { p.z = 128f + (sz - 128f) * u } else { p.z = sz + (128f - sz) * u }
            return
        }
        p.left -= dt
        if (p.left > 0f) return
        if (p.asleep) {
            // wake up, hop down if on the counter, and wander
            p.asleep = false
            p.left = 0f
            if (p.y > 1f) { p.hop = 1f; return }
        }
        p.y = 0f
        if (rnd.nextFloat() < 0.3f) {
            // back to the nap spot
            napSpot(p)
            p.wx[0] = p.x; p.wz[0] = PET_LANE
            p.wx[1] = sx; p.wz[1] = PET_LANE
            val up = p.nap == Place.COUNTER
            p.wx[2] = sx; p.wz[2] = if (up) 128f else sz
            p.wn = 3; p.wi = 0; p.walking = true
            p.toNap = true
            return
        }
        val tx = -bay + rnd.nextFloat() * (tanks + 2) * bay
        p.wx[0] = p.x; p.wz[0] = PET_LANE
        p.wx[1] = tx; p.wz[1] = PET_LANE + (rnd.nextFloat() - 0.5f) * 40f
        p.wn = 2; p.wi = 0; p.walking = true
    }

    companion object {
        const val MAX_BUBBLES = 3
        const val MODE_ROUTINE = 0
        const val MODE_TRADE_GO = 1
        const val MODE_TRADE_WAIT = 2
        /** Seconds into the visit before the trader comes over (plus a little at random). */
        const val TRADE_AFTER_S = 16f
        const val GREET_AT_S = 3.2f
        private const val WALK_SPEED = 72f
        private const val KID_SPEED = 92f
        private const val STRIDE = 110f
        private const val TURN = 5f
        private const val POSE_EASE = 6f
        /** The aisle lanes (cm in front of the back wall), one per person mod 4, so walkers pass each other. */
        private const val LANE0 = 172f
        private const val LANE_STEP = 15f
        private const val PET_LANE = 150f
        /** How far left of the counter's centre the staff walk round its open end. */
        private const val SIDE = 84f
        /** The counter top over the floor (store_rooms.py counter: y 20 in the model, the floor at -82). */
        const val COUNTER_TOP = 102f
        private const val HOP_S = 0.6f

        /** [from] turned toward [to] by at most [step] radians (the short way round). */
        fun turn(from: Float, to: Float, step: Float): Float {
            var d = to - from
            val tau = 2f * PI.toFloat()
            while (d > PI) d -= tau
            while (d < -PI) d += tau
            return if (abs(d) <= step) to else from + if (d > 0) step else -step
        }
    }
}
