package com.reeflive.core.ai

import com.reeflive.core.geom.Vec3
import com.reeflive.core.sim.BrainKind
import com.reeflive.core.sim.CoralKind
import com.reeflive.core.sim.Counters
import com.reeflive.core.sim.Ev
import com.reeflive.core.sim.Evidence
import com.reeflive.core.sim.HealthModel
import com.reeflive.core.sim.SandBed
import com.reeflive.core.sim.Simulation
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Blue star leopard wrasse, *Macropharyngodon bipartitus* (docs/research/video/fish_leopard_wrasse.md).
 *
 * By day it rows slowly on its pectorals through the coral and over the rock, hovering head-down to pick pods, amphipods,
 * forams and tiny snails every few seconds (the owner's fish works the Kenya tree branches, [H1]). The picks are fed by
 * the tank's microfauna ([com.reeflive.core.sim.MicrofaunaModel.rockPreyIndex]); on pod-poor rock it slowly starves
 * unless it takes prepared food.
 *
 * It sleeps buried in the sand: at dusk it dives head-first into open sand that is deep enough for its body
 * ([SandBed.canBury]) and wriggles down, digging a slot and throwing the sand to a rim, then pulling it back over itself
 * ([SandBed.buryStep]: real sand moved, nothing made). It lies under real cover (centre ≈ [BURY_DEPTH_CM] down) until
 * some time after the lights come on (lag [EMERGE_LAG_MIN_S]..[EMERGE_LAG_MAX_S]), when it bursts out
 * ([SandBed.emergeFrom]), leaving a dimple that slowly relaxes (SIMULATION §16.10). A big scare or a chase sends it into the sand for minutes, and a newly added
 * fish stays buried for [NEWCOMER_MIN_D]..[NEWCOMER_MAX_D] days while it settles (Fenner, TFH [M]).
 */
class LeopardWrasseBrain(c: Ctx) : SpeciesBrain(c) {
    companion object {
        /** No closer to its picking stand by 1 cm in this long: it cannot get there, pick another site (design estimate, as Feed). */
        const val STUCK_S = 8.0
        /** Seconds between picks while foraging: 10–20 picks a minute (owner clip H1; rate [L]). */
        @Ev(Evidence.DESIGN_ESTIMATE, "fish_leopard_wrasse: constant picking [H]; rate not measured") const val PICK_MIN_S = 3.0
        const val PICK_MAX_S = 6.0
        /** Picks at one site before moving on, and the forage range around its home (cm). */
        const val PICKS_PER_SITE_MIN = 3
        const val PICKS_PER_SITE_MAX = 8
        const val RANGE_CM = 40.0
        /**
         * Minutes of hunger rise one pick offsets at prey index 1. At ~9 picks per daylight minute (foraging about two thirds
         * of the day) this breaks even at a rock prey index ≈ 0.65, so a mature tank feeds it and a young one does not (est.).
         */
        @Ev(Evidence.DESIGN_ESTIMATE, "fish_leopard_wrasse: starves on pod-poor rock [M]; yield [L]") const val PICK_YIELD_MIN = 0.17
        /** Abstract mode: hunger relief per daylight minute, in units of the hunger rise, at prey index 1. */
        @Ev(Evidence.DESIGN_ESTIMATE) const val ABSTRACT_RELIEF = 1.5
        /** Dives into the sand when the evening light falls below this (dusk) or the lights go out. */
        @Ev(Evidence.DESIGN_ESTIMATE, "dives as the main lights go out (reefs.com) [M]") const val DIVE_LIGHT = 0.25
        /** Emerges this long after lights-on (s). */
        @Ev(Evidence.DESIGN_ESTIMATE, "emerges after the lights come on (H. trimaculatus proxy) [M]; lag [L]") const val EMERGE_LAG_MIN_S = 900.0
        const val EMERGE_LAG_MAX_S = 3600.0
        /** Depth of the buried fish under the sand surface (cm). */
        @Ev(Evidence.DESIGN_ESTIMATE, "2–5 cm") const val BURY_DEPTH_CM = 3.0
        /** Dive duration: head-first plunge then body waves until covered (Halichoeres bivittatus 1.05–3.06 s, SICB P2-191). */
        @Ev(Evidence.ANALOGY, "Halichoeres bivittatus, Tatom-Naecker & Westneat, SICB P2-191: 1.05-3.06 s") const val DIVE_MIN_S = 1.05
        const val DIVE_MAX_S = 3.06
        const val EMERGE_S = 1.2
        /** Share of the dive spent digging the slot; the cover is pulled back over the body from [COVER_FROM] on. */
        @Ev(Evidence.DESIGN_ESTIMATE, "SICB P2-191: plunge, then body waves until covered") const val DIG_SHARE = 0.85
        const val COVER_FROM = 0.55
        /**
         * Share of the sand it moves that flies as grains rather than being shoved: sand-diving wrasses fluidise the bed
         * with body waves and the sand closes over them (SICB P2-191: body waves until covered); the share is not measured.
         */
        @Ev(Evidence.DESIGN_ESTIMATE, "SICB P2-191: plunge, body waves, sand closes over") const val AIRBORNE = 0.35
        /** The fish lies on its side in the sand: slot width = body depth, thickness = body width (≈ 0.45 × depth). */
        @Ev(Evidence.DESIGN_ESTIMATE) const val THICK_OF_DEPTH = 0.45
        /** Least cover over the body that hides it (cm). */
        @Ev(Evidence.DESIGN_ESTIMATE, "buried 2–5 cm (fish_leopard_wrasse)") const val MIN_COVER_CM = 1.0
        /** Days a newcomer stays in the sand (Fenner, TFH: "may stay for days") and minutes after a big scare. */
        @Ev(Evidence.DESIGN_ESTIMATE, "Fenner TFH: newcomers dive and stay buried for days [M]") const val NEWCOMER_MIN_D = 1.0
        const val NEWCOMER_MAX_D = 4.0
        const val SCARE_MIN_S = 300.0
        const val SCARE_MAX_S = 1200.0

        /** Rock, glass and sand fauna a picker finds now (0 = dry rock, ≈ 1 = mature tank). */
        fun preyIndex(sim: Simulation): Double = sim.water.micro.rockPreyIndex().coerceIn(0.0, 1.5)
    }

    /** Where it forages: the Kenya tree's base when there is one (the owner's fish), else its home. */
    val forageCentre: Vec3
    /** Sand spot it sleeps under (x, z on open sand). */
    var sleepSpot: Vec3; private set

    // ---- state ----
    var buried = false; private set
    var buriedSince = -1e9; private set
    /** Sim time it may come out (lights-on + lag, end of a scare, end of the newcomer period); NaN = not set yet. */
    var emergeAt = Double.NaN; private set
    var lastEmergedAt = -1e9; private set
    /** The lights-on it is waiting out, when the emergence was set from one. */
    var emergeLightsOn = -1e9; private set
    private var hideUntil = -1e9
    private var newcomerFor = Double.NaN
    private var newcomerUntil = -1e9

    // ---- counters (tests, captions) ----
    var picks = 0; private set
    var dives = 0; private set
    var emergences = 0; private set
    var lastYield = 0.0; private set

    init {
        val kenya = c.sim.corals.filter { it.kind == CoralKind.KENYA && it.tissue > 0.05 }.minByOrNull { it.pos.x }
        forageCentre = kenya?.pos ?: Vec3(c.rock.lengthCm * 0.5, c.zoneY(0.35), c.rock.widthCm * 0.5)
        sleepSpot = findSleepSpot(c, forageCentre)
        install(SandSleep(), Pick(), LeopardFeed(), HoverHome(this, { forageCentre }, { 18.0 }, "Hovering in the coral"),
            Flee(this, { cc, from -> cc.coverPoint(from) }, "Darting away"), SurfaceGasp(this), Flash(this))
    }

    private val sand: SandBed get() = c.sim.sand

    /** Its body in the sand: slot width (body depth), thickness (body width) and the cover over it (cm). */
    private val slotW: Double get() = o.species.depthRatio * o.lengthCm
    private val thick: Double get() = THICK_OF_DEPTH * slotW
    private val cover: Double get() = max(MIN_COVER_CM, BURY_DEPTH_CM - 0.5 * thick)
    /** Head of the slot for a dive at [spot] heading (ux, uz): the body ends up centred under the spot. */
    private fun slotHead(spot: Vec3, ux: Double, uz: Double) = Vec3(spot.x + ux * 0.5 * o.lengthCm, 0.0, spot.z + uz * 0.5 * o.lengthCm)
    private fun fitsAt(x: Double, z: Double, ux: Double, uz: Double): Boolean {
        val h = slotHead(Vec3(x, 0.0, z), ux, uz)
        return sand.canBury(h.x, h.z, ux, uz, o.lengthCm, slotW, thick, cover)
    }

    /** Open sand within reach of [near]: away from rock, not hard against the glass. */
    /**
     * Open water straight up from the sand at (x, z): a spot under an overhang can only be reached through the rock
     * (a wrasse pressed against a ledge all night, heading for sand it could not see from above).
     */
    private fun openAbove(r: com.reeflive.core.sim.RockData, x: Double, y: Double, z: Double): Boolean =
        r.sdf.segmentClear(Vec3(x, y + 1.5, z), Vec3(x, min(y + 20.0, r.waterLevelCm - 2.0), z), 0.5)

    private fun findSleepSpot(c: Ctx, near: Vec3): Vec3 {
        val r = c.rock
        var best: Vec3? = null
        var bs = -1e9
        for (i in 0 until 60) {
            val x = (near.x + c.rng.signed() * 30.0).coerceIn(8.0, r.lengthCm - 8.0)
            val z = c.rng.range(6.0, r.widthCm - 5.0)
            val y = c.sim.sand.surfaceY(x, z)
            val clear = r.sdf.dist(Vec3(x, y + 1.5, z))
            if (clear < 4.0 || !openAbove(r, x, y, z)) continue
            // only where the sand is deep enough for the whole body under real cover (it lies along x, either way)
            if (!fitsAt(x, z, 1.0, 0.0) && !fitsAt(x, z, -1.0, 0.0)) continue
            val s = -0.05 * kotlin.math.abs(x - near.x) - 0.02 * kotlin.math.abs(z - near.z) + 0.02 * min(clear, 10.0)
            if (s > bs) { bs = s; best = Vec3(x, y, z) }
        }
        if (best != null) return best
        // nothing near: the deepest open sand in the tank (the back of the bed is deepest)
        var z = 4.0
        while (z < r.widthCm - 4.0) {
            var x = 8.0
            while (x < r.lengthCm - 8.0) {
                if (r.sdf.dist(Vec3(x, sand.surfaceY(x, z) + 1.5, z)) >= 4.0 && openAbove(r, x, sand.surfaceY(x, z), z) && (fitsAt(x, z, 1.0, 0.0) || fitsAt(x, z, -1.0, 0.0))) {
                    val s = -0.05 * kotlin.math.abs(x - near.x) - 0.02 * kotlin.math.abs(z - near.z)
                    if (s > bs) { bs = s; best = Vec3(x, sand.surfaceY(x, z), z) }
                }
                x += 2.0
            }
            z += 2.0
        }
        return best ?: Vec3(near.x.coerceIn(8.0, r.lengthCm - 8.0), sand.surfaceY(near.x, r.widthCm * 0.3), r.widthCm * 0.3)
    }

    /**
     * Under the sand: the head of the body lying in the slot it dug ([SandBed.BuriedBody.bodyY]); without one (abstract
     * mode) [BURY_DEPTH_CM] under the live surface at the sleep spot, never into the glass.
     */
    private fun buriedPos(): Vec3 {
        sand.buriedOf(o.id)?.let { return Vec3(it.headX, it.bodyY, it.headZ) }
        val g = sand.surfaceY(sleepSpot.x, sleepSpot.z)
        return Vec3(sleepSpot.x, max(g - BURY_DEPTH_CM, SandBed.GLASS_Y + SandBed.FLOOR_MARGIN + 0.5 * thick), sleepSpot.z)
    }

    override fun restSpot(c: Ctx, night: Boolean): Rest =
        if (night) Rest(buriedPos(), Vec3.X, posture = 5)
        else Rest(c.safe(forageCentre + Vec3(0.0, -4.0, 5.0)), Vec3.X)

    /** Abstract mode: buried (out of sight) at night, at the coral by day. */
    override fun abstractPose(c: Ctx, out: SteerIntent) {
        super.abstractPose(c, out)
        val night = c.p.night
        o.visible = !night
        buried = night
    }

    /** A newly added fish settles in the sand for days (sim time of its arrival is set after the brain is built). */
    private fun newcomerHide(c: Ctx): Boolean {
        val since = c.t - o.arrivalTime
        if (since < 0 || since > NEWCOMER_MAX_D * 86400) return false
        if (newcomerFor.isNaN() || newcomerFor != o.arrivalTime) {
            newcomerFor = o.arrivalTime
            newcomerUntil = o.arrivalTime + c.rng.range(NEWCOMER_MIN_D, NEWCOMER_MAX_D) * 86400
        }
        return c.t < newcomerUntil
    }

    /** Lights out, or the evening ramp below [DIVE_LIGHT] (the morning ramp does not count: it waits for its lag instead). */
    private fun dusk(c: Ctx): Boolean {
        if (c.p.night) return true
        val hour = (c.t / 3600.0) % 24.0
        return c.p.light < DIVE_LIGHT && hour >= 12.0
    }

    /** Big scares and chases: into the sand for a few minutes (it dives "when chased or startled", reefs.com [M]). */
    override fun onAttacked(c: Ctx, from: Vec3, mag: Double, by: com.reeflive.core.sim.Organism?) {
        super.onAttacked(c, from, mag, by)
        hideUntil = max(hideUntil, c.t + c.rng.range(SCARE_MIN_S, SCARE_MAX_S))
    }

    override fun onTap(c: Ctx, at: Vec3, s: Double) {
        super.onTap(c, at, s)
        if (s > 0.6) hideUntil = max(hideUntil, c.t + c.rng.range(SCARE_MIN_S, SCARE_MAX_S) * 0.5)
    }

    /** Daylight in abstract mode (> 240×): picking as a rate on the tank's microfauna. */
    fun abstractPick(dtMin: Double) {
        if (buried || !o.visible) return
        val prey = preyIndex(c.sim)
        o.hunger = max(0.0, o.hunger - HealthModel.hungerRatePerMin(o, c.sim.water.s.tempC) * ABSTRACT_RELIEF * prey * dtMin)
        picks += (9 * dtMin).toInt()
    }

    private fun eatPick(c: Ctx) {
        val prey = preyIndex(c.sim)
        val y = HealthModel.hungerRatePerMin(o, c.sim.water.s.tempC) * PICK_YIELD_MIN * prey
        lastYield = y
        if (y > 0) {
            o.hunger = max(0.0, o.hunger - y)
            if (prey > 0.2) o.counters.add(Counters.K.FEED, c.t)
        }
    }

    // ================================================================ behaviours

    /**
     * Sleeping (and hiding) in the sand: swim to the sleep spot, dive head-first, stay buried out of sight, and push out
     * again after lights-on + lag (or when a scare or the newcomer period is over).
     */
    inner class SandSleep : Behavior("sand", "Diving into the sand") {
        private var phase = 0           // 0 travel, 1 dive, 2 buried, 3 emerge
        private var timer = 0.0
        private var diveS = 2.0
        private var diveDir = Vec3.X
        private var emergeFrom = Vec3.ZERO
        override val committed: Boolean get() = phase >= 1
        override val label: String get() = when (phase) {
            0 -> "Heading for the sand"
            1 -> "Diving into the sand"
            2 -> if (c.p.night) "Asleep under the sand" else "Hiding under the sand"
            else -> "Pushing out of the sand"
        }

        private fun wantsIn(c: Ctx): Boolean = dusk(c) || newcomerHide(c) || c.t < hideUntil

        /** May come out: daylight, past the lights-on lag, past any scare or newcomer period. */
        fun mayEmerge(c: Ctx): Boolean {
            if (dusk(c) || newcomerHide(c) || c.t < hideUntil) return false
            val e = emergeAt
            return !e.isNaN() && c.t >= e
        }

        override fun score(c: Ctx): Double = when {
            buried -> 0.97
            wantsIn(c) -> if (newcomerHide(c) || c.t < hideUntil) 0.97 else 0.9
            else -> 0.0
        }

        override fun enter(c: Ctx) {
            if (buried) { phase = 2; timer = 0.0; return }
            phase = 0; timer = 0.0
            // a scared fish dives into the nearest open sand; at dusk it goes to its usual spot
            if (c.t < hideUntil || newcomerHide(c)) {
                val near = Vec3(o.pos.x, 0.0, o.pos.z)
                val s = findSleepSpot(c, near)
                if (s.distanceTo(o.pos.withY(s.y)) < 40) sleepSpot = s
            }
        }

        override fun update(c: Ctx, out: SteerIntent) {
            timer += c.dt
            when (phase) {
                0 -> {
                    val above = Vec3(sleepSpot.x, sand.surfaceY(sleepSpot.x, sleepSpot.z) + 0.3 * o.lengthCm + 1.0, sleepSpot.z)
                    val urgent = c.t < hideUntil || newcomerHide(c)
                    // over the patch at its own height first, then straight down the open column above it ([openAbove])
                    val over = Vec3(above.x, above.y + (o.pos.y - above.y).coerceIn(0.0, 16.0), above.z)
                    val far = o.pos.withY(0.0).distanceTo(above.withY(0.0)) > 3.0
                    val goal = if (far) over else above
                    out.seek(if (o.pos.distanceTo(goal) > 10) c.viaOpenWater(goal) else goal,
                        if (urgent) o.burstSpeed * 0.7 else o.cruiseSpeed, arrive = 1.5, urgency = if (urgent) 0.8 else 0.4)
                    out.hover = true
                    if (o.pos.distanceTo(above) >= 3.0 && timer > 40) {
                        // could not get over that patch (a rock bump in the way): take the open sand nearest to it
                        // instead of plunging from where it is (the kinematic dive would cut through the rock)
                        sleepSpot = findSleepSpot(c, Vec3(o.pos.x, 0.0, o.pos.z)); timer = 0.0
                    } else if (o.pos.distanceTo(above) < 3.0) {
                        phase = 1; timer = 0.0
                        diveS = c.rng.range(DIVE_MIN_S, DIVE_MAX_S)
                        diveDir = horizontalHeading(o.heading, Vec3.X)
                        val g = sand.surfaceY(sleepSpot.x, sleepSpot.z)
                        // lies along x (either way): the way it faces, unless only the other way fits
                        val ux = if (diveDir.x >= 0) 1.0 else -1.0
                        val fwd = fitsAt(sleepSpot.x, sleepSpot.z, ux, 0.0)
                        diveDir = Vec3(if (fwd) ux else -ux, 0.0, 0.0)
                        val h = slotHead(sleepSpot, diveDir.x, diveDir.z)
                        val body = sand.startBurial(o.id, h.x, h.z, diveDir.x, diveDir.z, o.lengthCm, slotW, thick, cover)
                        if (body == null) {
                            // the sand here is too shallow (dug out since it chose the spot): find deeper sand
                            sleepSpot = findSleepSpot(c, Vec3(o.pos.x, 0.0, o.pos.z)); phase = 0
                            return
                        }
                        sand.puff(sleepSpot.x, g + 0.5, sleepSpot.z, 4, 4.0, diveDir.x, diveDir.z, finesShare = 0.4)
                        dives++
                    }
                }
                1 -> { // head-first plunge, body waving: it digs the slot (sand thrown to the rim), then pulls it back over itself
                    val k = (timer / diveS).coerceIn(0.0, 1.0)
                    val body = sand.buriedOf(o.id)
                    if (body != null) {
                        // the body wave flings the dug sand up and out as grains (to the side the tail sweeps), then
                        // throws it back over itself; the grains build the rim and the cover where they land
                        val moved = sand.buryStep(body, k / DIG_SHARE, (k - COVER_FROM) / (1 - COVER_FROM), AIRBORNE, sin(c.t * 30.0))
                        // the fines it kicks loose: a small haze that settles back or drifts off with the flow
                        if (moved > 0.15 && c.rng.chance(0.5)) sand.puff(body.cx, sand.surfaceY(body.cx, body.cz) + 0.4, body.cz, 2, 3.0, diveDir.x, diveDir.z, finesShare = 0.4)
                    }
                    val g = sand.surfaceY(sleepSpot.x, sleepSpot.z)
                    val end = buriedPos()
                    // phase 1 near-vertical, phase 2 flattening out parallel to the bed (SICB P2-191)
                    val pitch = if (k < 0.55) -1.6 else -1.6 + 1.3 * ((k - 0.55) / 0.45)
                    val head = Vec3(diveDir.x, pitch, diveDir.z).normalized(Vec3.Y * -1.0)
                    val y = g + 0.3 * o.lengthCm + (end.y - g - 0.3 * o.lengthCm) * k
                    out.place(Vec3(sleepSpot.x + (end.x - sleepSpot.x) * k, y, sleepSpot.z + (end.z - sleepSpot.z) * k), head, null, 8.0)
                    out.posture = 5
                    out.ampMul = 1.8
                    o.bend = 0.25 * sin(c.t * 30.0)
                    if (timer >= diveS) {
                        sand.buriedOf(o.id)?.let { sand.buryStep(it, 1.0, 1.0) }
                        phase = 2; timer = 0.0
                        buried = true; buriedSince = c.t; emergeAt = Double.NaN; emergeLightsOn = -1e9
                        o.bend = 0.0
                    }
                }
                2 -> { // under the sand, out of sight
                    out.place(buriedPos(), diveDir, Vec3.Y, 3.0)
                    out.posture = 5
                    o.visible = false
                    o.sleeping = c.p.night
                    o.gill = 0.3 + 0.1 * sin(c.t * 1.5)
                    // when the lights come on the wait starts; after a scare or the newcomer period, the end of it
                    if (!c.p.night && c.p.lightsOnAt > buriedSince && c.p.lightsOnAt != emergeLightsOn) {
                        emergeLightsOn = c.p.lightsOnAt
                        emergeAt = c.p.lightsOnAt + c.rng.range(EMERGE_LAG_MIN_S, EMERGE_LAG_MAX_S)
                    }
                    if (emergeAt.isNaN() && !dusk(c)) emergeAt = max(hideUntil, c.t)
                    if (mayEmerge(c)) {
                        phase = 3; timer = 0.0
                        emergeFrom = buriedPos()
                        // it bursts out head first: the cover over its head is thrown aside (a dimple with a low rim)
                        if (sand.buriedOf(o.id) != null) sand.emergeFrom(o.id, airborne = AIRBORNE) else sand.sandEmerge(sleepSpot.x, sleepSpot.z, o.lengthCm)
                        val g = sand.surfaceY(emergeFrom.x, emergeFrom.z)
                        sand.puff(emergeFrom.x, g + 0.3, emergeFrom.z, 4, 3.0, finesShare = 0.4)
                        o.visible = true
                        buried = false
                        lastEmergedAt = c.t
                        emergences++
                    }
                }
                else -> { // head and body rise out of the sand
                    val k = (timer / EMERGE_S).coerceIn(0.0, 1.0)
                    val g = sand.surfaceY(emergeFrom.x, emergeFrom.z)
                    val y = emergeFrom.y + (g + 0.3 * o.lengthCm + 1.0 - emergeFrom.y) * k
                    out.place(Vec3(emergeFrom.x, y, emergeFrom.z), Vec3(diveDir.x, 1.0 - 0.8 * k, diveDir.z).normalized(Vec3.Y), null, 8.0)
                    out.posture = if (k < 0.6) 5 else 0
                    o.visible = true
                    if (timer >= EMERGE_S) finished = true
                }
            }
        }

        override fun exit(c: Ctx) {
            // never leave the fish invisible: an emergence that was cut short still surfaces
            if (sand.buriedOf(o.id) != null) {
                // a dive or a sleep cut short: it pushes out of whatever it had dug (the slot gives its body back)
                sand.emergeFrom(o.id)
                if (buried) { buried = false; lastEmergedAt = c.t; emergences++ }
            } else if (buried) {
                buried = false
                sand.sandEmerge(sleepSpot.x, sleepSpot.z, o.lengthCm)
                lastEmergedAt = c.t
                emergences++
            }
            c.o.visible = true
            c.o.bend = 0.0
        }
    }

    /**
     * Hover-and-pick: row to a spot on the coral or rock (sometimes the sand), hang head-down in front of it and pick every
     * [PICK_MIN_S]..[PICK_MAX_S] s, then move a little way on.
     */
    inner class Pick : Behavior("pick", "Picking pods off the coral") {
        private var spot = Vec3.ZERO
        private var normal = Vec3.Y
        private var stand = Vec3.ZERO
        private var nextPick = 0.0
        private var left = 0
        private var onSand = false
        override val label: String get() = if (onSand) "Picking over the sand" else "Picking pods off the coral"
        override fun score(c: Ctx): Double {
            if (dusk(c)) return 0.0
            return ((0.55 + 0.3 * o.hunger) * max(c.activity, 0.3)).coerceAtMost(0.8)
        }
        private var bestD = Double.MAX_VALUE
        private var progressAt = 0.0
        override fun enter(c: Ctx) { newSite(c); nextPick = c.t + c.rng.range(1.0, 2.5) }

        private fun newSite(c: Ctx) {
            bestD = Double.MAX_VALUE; progressAt = c.t
            left = PICKS_PER_SITE_MIN + c.rng.nextInt(PICKS_PER_SITE_MAX - PICKS_PER_SITE_MIN + 1)
            onSand = c.rng.chance(0.25)
            if (onSand) {
                val x = (forageCentre.x + c.rng.signed() * RANGE_CM * 0.7).coerceIn(6.0, c.rock.lengthCm - 6.0)
                val z = c.rng.range(5.0, c.rock.widthCm - 3.0)
                spot = Vec3(x, sand.surfaceY(x, z), z); normal = Vec3.Y
                stand = c.safe(spot + Vec3(0.0, 0.35 * o.lengthCm + 1.0, 0.0), 0.5)
                return
            }
            val centre = forageCentre + c.rng.unitVector() * (RANGE_CM * 0.5 * c.rng.nextDouble())
            val sp = c.rockSurfacePoint(centre, 14.0) ?: c.rockSurfacePoint(o.pos, 20.0)
            if (sp == null) { spot = c.safe(forageCentre); normal = Vec3.Y; stand = spot; return }
            spot = sp.first; normal = sp.second
            stand = c.safe(spot + normal * (0.35 * o.lengthCm + 1.0), 0.6 * o.collisionLength)
        }

        override fun update(c: Ctx, out: SteerIntent) {
            val d = o.pos.distanceTo(stand)
            // progress guard: a stand it cannot get to (behind a rock bump, the clearance holds it off) is given up
            if (d < bestD - 1.0) { bestD = d; progressAt = c.t }
            if (d > 2.5 && c.t - progressAt > STUCK_S) { newSite(c); return }
            if (d > 2.5) {
                out.seek(if (d > 12) c.viaOpenWater(stand) else stand, o.cruiseSpeed, arrive = 1.5)
                out.hover = true
                return
            }
            // hang head-down toward the surface it is working, rowing on the pectorals
            out.seek(stand, o.cruiseSpeed * 0.4, arrive = 1.5)
            out.hover = true
            out.pectoral = 0.8
            out.faceDir = (spot - o.pos).normalized(o.heading)
            out.pitchBias = -0.6 + 0.1 * sin(c.t * 1.6)
            if (c.t >= nextPick) {
                nextPick = c.t + c.rng.range(PICK_MIN_S, PICK_MAX_S)
                o.mouth = 1.0
                picks++
                eatPick(c)
                if (onSand) sand.freshen(spot.x, spot.z, 0.8, 0.4)
                left--
                if (left <= 0) {
                    if (elapsed > 60 + 60 * c.rng.nextDouble()) finished = true
                    else newSite(c)
                }
            }
        }
    }

    /** Prepared food (mysis, cyclops) close to the coral; trained fish only ([com.reeflive.core.sim.Organism.acceptsPrepared]). */
    inner class LeopardFeed : Feed(this@LeopardWrasseBrain, 45.0, "Picking food from the water") {
        override fun score(c: Ctx): Double = if (dusk(c) || buried) 0.0 else super.score(c)
    }
}
