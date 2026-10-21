package com.reeflive.core.ai

import com.reeflive.core.geom.Vec3
import com.reeflive.core.geom.smoothstep
import com.reeflive.core.sim.BrainKind
import com.reeflive.core.sim.Ev
import com.reeflive.core.sim.Evidence
import com.reeflive.core.sim.FoodParticle
import com.reeflive.core.sim.HealthModel
import com.reeflive.core.sim.LifeStages
import com.reeflive.core.sim.Organism
import com.reeflive.core.sim.SexRole
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Red fairy wrasse, *Cirrhilabrus rubripinnis* (docs/research/42_fairy_wrasse_yellow_purple_tang.md §1).
 *
 * By day the group hangs in the current a hand's width above the rock, heads into the flow, and picks zooplankton out
 * of the water every few seconds (FishBase: rubble and patch reefs in current, zooplanktivore). The picks are fed by the
 * tank's own plankton ([com.reeflive.core.sim.MicrofaunaModel.columnPreyIndex]) on top of what the keeper feeds.
 *
 * It lives in a harem: one male with several females (FishBase [H]). The females keep near the male; the male patrols
 * round them and flashes: fins raised wide, colours at their brightest, a fast loop round a female and a lateral
 * display beside her ([Organism.display], drawn by the renderer). He flashes now and then by day and in rapid bouts in
 * the evening before the lights go out, when a ripe female may rise with him in a short spawning rush. Two males fight
 * (the pair-matrix row `fairy_male_male`). With no male, the biggest female turns male over weeks ([LifeStages] /
 * SexRoles, sheet `sexChangeDays`).
 *
 * At dusk each one slips into a crevice ([TangShelters] fitted to its slender body) and spins a mucus cocoon round
 * itself over about half an hour (posture 4, [Organism.cocoon]); it leaves it a while after the lights come back on.
 * A hard scare at night bursts it out of the cocoon, and it does not make another that night. Jumping out of an
 * open tank is the shared jump model's job (species sheet `jumpRisk`).
 */
class FairyWrasseBrain(c: Ctx) : SpeciesBrain(c) {
    companion object {
        /** Seconds between snaps at drifting plankton while it holds station (DESIGN ESTIMATE: no field rate for Cirrhilabrus). */
        @Ev(Evidence.DESIGN_ESTIMATE, "research 42 §1: picks zooplankton in the current [H]; rate [L]") const val SNAP_MIN_S = 2.0
        const val SNAP_MAX_S = 6.0
        /** Minutes of hunger rise one snap offsets at a column prey index of 1 (est.: a mature tank half-feeds a picker). */
        @Ev(Evidence.DESIGN_ESTIMATE) const val SNAP_YIELD_MIN = 0.06
        /** Height of its station above the rock below it, cm. */
        @Ev(Evidence.DESIGN_ESTIMATE, "research 42 §1: mid-water over rubble") const val STATION_MIN_CM = 6.0
        const val STATION_MAX_CM = 22.0
        /** Females keep within this of the male (cm). */
        @Ev(Evidence.DESIGN_ESTIMATE, "FishBase: small groups each with a single male [H]; spacing [L]") const val HAREM_CM = 28.0
        /** Male flash bouts: seconds between bouts by day and in the evening rush, and how long one lasts. */
        @Ev(Evidence.DESIGN_ESTIMATE, "research 42 §1: males flash, mostly towards dusk [M]; timing [L]") const val FLASH_DAY_MIN_S = 360.0
        const val FLASH_DAY_MAX_S = 900.0
        const val FLASH_DUSK_MIN_S = 40.0
        const val FLASH_DUSK_MAX_S = 120.0
        const val FLASH_S = 4.0
        /** Evening display window: afternoon light between these (the ramp toward lights-out). */
        const val DUSK_LO = 0.1
        const val DUSK_HI = 0.7
        /** Chance a dusk flash bout ends in a spawning rush with a mature female, and how high they rise (cm). */
        @Ev(Evidence.ANALOGY, "labrid paired spawning rush at dusk (Cirrhilabrus, Labroides) [M]") const val RUSH_CHANCE = 0.25
        const val RUSH_RISE_CM = 12.0
        /** Goes to its crevice when the evening light falls below this. */
        @Ev(Evidence.DESIGN_ESTIMATE, "research 42 §1: sleeps in rock crevices [M]") const val SLEEP_LIGHT = 0.12
        /** Seconds to spin the full cocoon. */
        @Ev(Evidence.ANALOGY, "parrotfish cocoons take 30-60 min (Grutter et al. 2011 Biol Lett) [M]") const val COCOON_BUILD_S = 2400.0
        /** It leaves its bed this long after lights-on (s). */
        @Ev(Evidence.DESIGN_ESTIMATE) const val WAKE_LAG_MIN_S = 300.0
        const val WAKE_LAG_MAX_S = 1500.0
        /** A night scare at least this hard bursts it out of the cocoon (a cocooned wrasse also feels taps at half strength). */
        @Ev(Evidence.DESIGN_ESTIMATE, "research 42 §1: a startled fish leaves its cocoon and does not rebuild [L]") const val BURST_MAG = 0.45
        /** Days a newcomer stays close to the rock before it rises into the open water. */
        @Ev(Evidence.DESIGN_ESTIMATE, "hobby accounts: new fairy wrasses hide for a day or two [L]") const val NEWCOMER_MIN_D = 0.5
        const val NEWCOMER_MAX_D = 2.0
        /** Seconds it tries to get into a bed before picking another. */
        const val BED_GIVE_UP_S = 120.0

        /** Male, or a female far enough through the change to behave as one (the change starts with behaviour). */
        fun isMale(o: Organism): Boolean {
            val r = LifeStages.sexRole(o)
            return r == SexRole.MALE || r == SexRole.CHANGING_TO_MALE && o.sexChange > 0.3
        }

        /** Plankton drifting in the water now (0 = none, ≈ 1 = mature tank), as a picker finds it. */
        fun preyIndex(c: Ctx): Double = c.sim.water.micro.columnPreyIndex().coerceIn(0.0, 1.5)
    }

    /** Crevices fitted to its slender body (the shared rock scan). */
    val shelters by lazy { TangShelters.forSpecies(c) }
    private var bed: TangShelters.Pose? = null
    private var bedFor = -1.0
    private var bedGen = -1
    private val badBeds = ArrayList<Vec3>()

    /** Where it holds station by day (re-picked now and then near the harem). */
    private var station: Vec3 = Vec3(c.rock.lengthCm * 0.5, c.zoneY(0.55), c.rock.widthCm * 0.5)

    /** Burst out of its cocoon tonight (no second one until the next night). */
    var cocoonBroken = false; private set
    private var brokenNight = -1.0
    private var newcomerFor = Double.NaN
    private var newcomerUntil = -1e9
    /** Female joining a male's rush: rise until then. */
    private var rushUntil = -1e9
    private var rushFrom = Vec3.ZERO

    // ---- counters (tests, captions) ----
    var snaps = 0; private set
    var flashes = 0; private set
    var rushes = 0; private set
    var cocoonsBuilt = 0; private set

    init {
        install(CocoonSleep(), Rush(), Flashing(), PickPlankton(), FairyFeed(), Harem(),
            Flee(this, { cc, from -> shelterFrom(cc, from) }, "Darting into the rock"),
            VisitCleaner(this, 0.05), SurfaceGasp(this), Flash(this))
    }

    /** Male, or far enough through the change from female that he already behaves as one (the change starts with behaviour). */
    val male: Boolean get() = isMale(o)

    private var haremAt = -1e9
    private var haremMale: Organism? = null

    /** The nearest male of its own species (looked up once a second: a scan over the tank). */
    private fun harem(c: Ctx): Organism? {
        if (c.t - haremAt < 1.0 && haremMale?.alive == true) return haremMale
        haremAt = c.t
        haremMale = findMale(c)
        return haremMale
    }

    private fun findMale(c: Ctx): Organism? {
        var best: Organism? = null
        var bd = Double.MAX_VALUE
        for (n in c.sim.organisms) {
            if (n === o || !n.alive || !n.visible || n.species.id != o.species.id) continue
            val nb = c.sim.agentOf(n)?.brain as? FairyWrasseBrain ?: continue
            if (!nb.male) continue
            val d = n.pos.distanceSqTo(o.pos)
            if (d < bd) { bd = d; best = n }
        }
        return best
    }

    private fun newcomer(c: Ctx): Boolean {
        val since = c.t - o.arrivalTime
        if (since < 0 || since > NEWCOMER_MAX_D * 86400) return false
        if (newcomerFor.isNaN() || newcomerFor != o.arrivalTime) {
            newcomerFor = o.arrivalTime
            newcomerUntil = o.arrivalTime + c.rng.range(NEWCOMER_MIN_D, NEWCOMER_MAX_D) * 86400
        }
        return c.t < newcomerUntil
    }

    private fun evening(c: Ctx): Boolean {
        val hour = (c.t / 3600.0) % 24.0
        return !c.p.night && hour >= 12.0 && c.p.light in DUSK_LO..DUSK_HI
    }

    private fun bedtime(c: Ctx): Boolean {
        if (c.p.night) return true
        val hour = (c.t / 3600.0) % 24.0
        return hour >= 12.0 && c.p.light < SLEEP_LIGHT
    }

    /** A crevice away from [from], or open cover when the rock has none that fits. */
    private fun shelterFrom(c: Ctx, from: Vec3): Vec3 =
        shelters.pick(c.o.pos, from, c.o.lengthCm, 0.0)?.entry ?: c.coverPoint(from)

    /** Its bed for its present size (refitted if it has grown or the rocks moved). */
    fun bedPose(c: Ctx, locked: Boolean): TangShelters.Pose? {
        shelters.spots
        val gen = shelters.generation
        val cur = bed
        if (locked && cur != null) return cur
        if (bedFor < 0 || kotlin.math.abs(bedFor - o.lengthCm) > 0.5 || gen != bedGen) {
            bedFor = o.lengthCm; bedGen = gen
            bed = shelters.pick(station, null, o.lengthCm, 0.0, prefer = station, tries = 10, snuggest = true, avoid = badBeds)
        }
        return bed
    }

    private fun bedBlocked() {
        bed?.let { badBeds += it.pos; if (badBeds.size > 4) badBeds.removeAt(0) }
        bedFor = -1.0
    }

    override fun restSpot(c: Ctx, night: Boolean): Rest {
        if (night) bedPose(c, false)?.let { return Rest(it.pos, it.heading, it.up, posture = 4) }
        return Rest(c.safe(station), Vec3.X)
    }

    override fun initialPlace(c: Ctx) {
        station = pickStation(c, Vec3(c.rng.range(30.0, c.rock.lengthCm - 30.0), c.zoneY(0.5), c.rock.widthCm * 0.5))
        super.initialPlace(c)
    }

    override fun onTap(c: Ctx, at: Vec3, s: Double) {
        super.onTap(c, at, s)
        if (o.cocoon > 0.05 && s >= BURST_MAG) burst(c)
    }

    override fun onAttacked(c: Ctx, from: Vec3, mag: Double, by: Organism?) {
        super.onAttacked(c, from, mag, by)
        if (o.cocoon > 0.05) burst(c)
    }

    private fun burst(c: Ctx) {
        o.cocoon = 0.0
        cocoonBroken = true
        brokenNight = c.p.lightsOffAt
    }

    /** A spot [STATION_MIN_CM]..[STATION_MAX_CM] above the rock near [near], in open water. */
    private fun pickStation(c: Ctx, near: Vec3): Vec3 {
        val x = near.x.coerceIn(12.0, c.rock.lengthCm - 12.0)
        val z = near.z.coerceIn(6.0, c.rock.widthCm - 6.0)
        var top = c.rock.sandTopCm
        val hit = c.rock.sdf.raycast(Vec3(x, c.rock.waterLevelCm - 2.0, z), Vec3.Y * -1.0, c.rock.waterLevelCm)
        if (hit != null) top = c.rock.waterLevelCm - 2.0 - hit
        val y = min(c.zoneY(0.85), top + c.rng.range(STATION_MIN_CM, STATION_MAX_CM))
        return c.safe(Vec3(x, y, z), c.clearance + 2.0)
    }

    /** A female's answer to her male's rush call. */
    fun joinRush(c: Ctx) { rushUntil = c.t + 1.4; rushFrom = o.pos; rushes++ }

    /**
     * Day: holds station in the flow, head up-current, a few cm of drift around it, and snaps at plankton that passes.
     * Each snap relieves hunger by the tank's drifting prey. A newcomer keeps near the rock.
     */
    inner class PickPlankton : Behavior("plankton", "Picking plankton in the current") {
        private var nextSnap = 0.0
        private var nextMove = 0.0
        private var jitter = Vec3.ZERO
        override fun score(c: Ctx) = 0.42 * c.activity
        override fun enter(c: Ctx) { nextSnap = c.t + c.rng.range(SNAP_MIN_S, SNAP_MAX_S) }
        override fun update(c: Ctx, out: SteerIntent) {
            val o = c.o
            if (c.t > nextMove) {
                nextMove = c.t + c.rng.range(6.0, 16.0)
                val u = c.rng.unitVector()
                jitter = Vec3(u.x * 6.0, u.y * 2.5, u.z * 4.0)
                if (newcomer(c)) {
                    // newly added: close to the rock and its bed, not up in the open
                    val b = bedPose(c, false)
                    station = c.safe((b?.entry ?: station).let { it + Vec3(0.0, 2.0, 2.0) })
                } else if (c.rng.chance(0.15)) station = pickStation(c, station + Vec3(c.rng.signed() * 15.0, 0.0, c.rng.signed() * 5.0))
            }
            val goal = c.safe(station + jitter)
            out.seek(c.viaOpenWater(goal), o.cruiseSpeed * 0.5, arrive = 3.0)
            out.hover = true; out.pectoral = 1.0; out.ampMul = 0.4
            val fl = c.sim.flow.velocityFast(o.pos)
            if (fl.length() > 1.0) out.faceDir = (fl * -1.0).normalized(o.heading)
            if (c.t >= nextSnap) {
                nextSnap = c.t + c.rng.range(SNAP_MIN_S, SNAP_MAX_S)
                o.mouth = 1.0
                val y = HealthModel.hungerRatePerMin(o, c.sim.water.s.tempC) * SNAP_YIELD_MIN * preyIndex(c)
                o.hunger = max(0.0, o.hunger - y)
                snaps++
            }
            if (elapsed > 120) finished = true
        }
    }

    /** Females stay with the male: back toward him when they drift further than [HAREM_CM]. */
    inner class Harem : Behavior("harem", "Staying with the group") {
        private var m: Organism? = null
        override fun score(c: Ctx): Double {
            if (male || c.p.night) return 0.0
            val h = harem(c) ?: return 0.0
            m = h
            val d = h.pos.distanceTo(c.o.pos)
            return if (d > HAREM_CM) (0.45 + 0.01 * (d - HAREM_CM)).coerceAtMost(0.6) * c.activity else 0.0
        }
        override fun update(c: Ctx, out: SteerIntent) {
            val h = m ?: run { finished = true; return }
            out.seek(c.viaOpenWater(c.safe(h.pos + Vec3(c.rng.signed() * 4.0, 2.0, 3.0))), c.o.cruiseSpeed, arrive = 6.0)
            if (h.pos.distanceTo(c.o.pos) < 0.6 * HAREM_CM) { station = c.safe(c.o.pos); finished = true }
        }
    }

    /**
     * The male's flash: picks the nearest female, swims to her side, raises his fins and brightens, loops once round her
     * fast and holds a lateral display a moment. Evening bouts come every minute or two and may end in a spawning rush.
     */
    inner class Flashing : Behavior("flashing", "Flashing his fins") {
        private var next = Double.NaN
        private var her: Organism? = null
        private var phase = 0
        private var loopT = 0.0
        private var rushAt = -1.0
        override fun score(c: Ctx): Double {
            if (!male || c.p.night || newcomer(c)) return 0.0
            if (next.isNaN()) next = c.t + c.rng.range(60.0, FLASH_DAY_MAX_S)
            if (c.t < next) return 0.0
            her = nearestFemale(c)
            if (her == null) { next = c.t + c.rng.range(FLASH_DAY_MIN_S, FLASH_DAY_MAX_S); return 0.0 }
            return 0.6
        }
        private fun nearestFemale(c: Ctx): Organism? {
            var best: Organism? = null
            var bd = 45.0 * 45.0
            for (n in c.sim.organisms) {
                if (n === c.o || !n.alive || !n.visible || n.species.id != c.o.species.id) continue
                val nb = c.sim.agentOf(n)?.brain as? FairyWrasseBrain ?: continue
                if (nb.male) continue
                val d = n.pos.distanceSqTo(c.o.pos)
                if (d < bd) { bd = d; best = n }
            }
            return best
        }
        override fun enter(c: Ctx) {
            phase = 0; loopT = 0.0; rushAt = -1.0
            flashes++
            val ev = evening(c)
            next = c.t + if (ev) c.rng.range(FLASH_DUSK_MIN_S, FLASH_DUSK_MAX_S) else c.rng.range(FLASH_DAY_MIN_S, FLASH_DAY_MAX_S)
            val h = her
            if (ev && h != null && LifeStages.mature(h) && LifeStages.mature(c.o) && c.rng.chance(RUSH_CHANCE)) rushAt = FLASH_S * 0.7
        }
        override fun update(c: Ctx, out: SteerIntent) {
            val o = c.o
            val h = her ?: run { finished = true; return }
            if (!h.alive) { finished = true; return }
            o.focusId = h.id
            o.display = max(o.display, smoothstep(0.0, 0.6, elapsed))
            val side = (h.heading cross Vec3.Y).normalized(Vec3.Z)
            when (phase) {
                // close in beside her
                0 -> {
                    out.seek(c.safe(h.pos + side * (1.2 * c.L)), o.cruiseSpeed * 1.6, arrive = 1.5, urgency = 0.5)
                    if (o.pos.distanceTo(h.pos) < 2.0 * c.L || elapsed > 3.0) phase = 1
                }
                // one fast vertical loop round her (~1.2 s)
                1 -> {
                    loopT += c.dt
                    val a = 2 * Math.PI * (loopT / 1.2)
                    val p = h.pos + side * (cos(a) * 1.2 * c.L) + Vec3.Y * (sin(a) * 1.0 * c.L)
                    out.seek(c.safe(p), o.burstSpeed * 0.6, arrive = 0.5, urgency = 0.8)
                    if (loopT > 1.2) phase = 2
                }
                // lateral display alongside her, flank on, fins up
                else -> {
                    out.seek(c.safe(h.pos + side * (1.1 * c.L)), o.cruiseSpeed * 0.4, arrive = 1.0)
                    out.faceDir = h.heading
                    out.hover = true; out.pectoral = 1.0
                    out.rollExtra = 0.25
                }
            }
            if (rushAt > 0 && elapsed >= rushAt) {
                rushAt = -1.0
                (c.sim.agentOf(h)?.brain as? FairyWrasseBrain)?.joinRush(c.sim.agentOf(h)!!.ctx)
                joinRush(c)
                c.sim.emit(com.reeflive.core.sim.SimEvent.Chase(o.id, h.id, "SPAWNING_RUSH", false, true, c.sim.simTimeSec))
            }
            if (elapsed > FLASH_S) finished = true
        }
    }

    /** The pair's spawning rush: both dart up together and turn back down (gametes go with the current; no brood is kept). */
    inner class Rush : Behavior("rush", "Spawning rush") {
        override val committed: Boolean get() = elapsed < 1.4
        override fun score(c: Ctx) = if (c.t < rushUntil) 0.94 else 0.0
        override fun update(c: Ctx, out: SteerIntent) {
            val o = c.o
            out.seek(c.safe(rushFrom + Vec3.Y * RUSH_RISE_CM), o.burstSpeed * 0.7, arrive = 1.0, urgency = 0.9)
            if (male) o.display = 1.0
            if (c.t >= rushUntil) finished = true
        }
    }

    /** Food the keeper adds: it takes what drifts by in open water, not what lies on the sand or rock. */
    inner class FairyFeed : Feed(this@FairyWrasseBrain, 60.0) {
        override fun accept(c: Ctx, f: FoodParticle) = !f.settled && !f.onSurface && f.pos.y > c.rock.sandTopCm + 2.0 &&
            c.rock.sdf.dist(f.pos) > 0.45 * c.o.collisionLength - (1.2 + 0.1 * c.L)
        override fun bonus(c: Ctx, f: FoodParticle) = if (f.kind == com.reeflive.core.sim.FoodKind.MYSIS ||
            f.kind == com.reeflive.core.sim.FoodKind.CYCLOPS || f.kind == com.reeflive.core.sim.FoodKind.PODS) 0.1 else 0.0
    }

    /**
     * Evening and night: into its crevice ([Dock]: doorway, turn, glide in), then the cocoon builds round it over
     * [COCOON_BUILD_S] (posture 4). After lights-on plus a lag it glides back out; the cocoon is left to the current.
     */
    inner class CocoonSleep : Behavior("cocoon", "Sleeping in its mucus cocoon") {
        private val dock = Dock()
        private var waking = false
        private var triedAt = 0.0
        private var wakeAt = Double.NaN
        private var wakeFor = -1e9
        private var lastBed: TangShelters.Pose? = null
        override val committed: Boolean get() = dock.inside
        override fun score(c: Ctx): Double {
            if (bedtime(c)) return 0.85 * Feed.nightRestDamp(c, this@FairyWrasseBrain)
            // morning: stay in until the lag after lights-on has passed
            if (c.p.lightsOnAt > wakeFor) { wakeFor = c.p.lightsOnAt; wakeAt = c.p.lightsOnAt + c.rng.range(WAKE_LAG_MIN_S, WAKE_LAG_MAX_S) }
            return if (dock.inside && c.t < wakeAt) 0.85 else 0.0
        }
        override fun enter(c: Ctx) { dock.reset(); waking = false; triedAt = 0.0; lastBed = null }
        override fun exit(c: Ctx) { c.o.cocoon = 0.0 }
        override fun update(c: Ctx, out: SteerIntent) {
            val o = c.o
            if (cocoonBroken && c.p.lightsOffAt != brokenNight) cocoonBroken = false
            val bp = bedPose(c, dock.inside)
            if (bp !== lastBed) { if (lastBed != null && bp != null && !dock.inside) dock.reset(); lastBed = bp }
            if (bp == null) {
                // no crevice fits: rest low by the rock in the open
                out.seek(c.safe(station.withY(c.zoneY(0.2))), o.cruiseSpeed * 0.5, arrive = 3.0)
                out.hover = true; out.ampMul = 0.3
                o.sleeping = c.p.night
                return
            }
            if (waking || !bedtime(c) && c.t >= wakeAt) {
                waking = true
                o.cocoon = max(0.0, o.cocoon - c.dt / 20.0)
                if (dock.leave(c, out, bp)) finished = true
                return
            }
            if (!dock.drive(c, out, bp, o.cruiseSpeed * 0.8, rate = 1.5)) {
                if (!dock.inside && elapsed - triedAt > BED_GIVE_UP_S) { bedBlocked(); dock.reset(); triedAt = elapsed }
                return
            }
            out.place(bp.pos, bp.heading, bp.up, 1.5)
            out.posture = 4
            out.pectoral = 0.15; out.ampMul = 0.0
            o.sleeping = true
            if (!cocoonBroken && c.p.night) {
                val was = o.cocoon
                o.cocoon = min(1.0, o.cocoon + c.dt / COCOON_BUILD_S)
                if (was < 1.0 && o.cocoon >= 1.0) cocoonsBuilt++
            }
        }
    }
}
