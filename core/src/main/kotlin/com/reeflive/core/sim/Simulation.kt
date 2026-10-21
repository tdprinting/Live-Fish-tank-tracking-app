package com.reeflive.core.sim

import com.reeflive.core.ai.Agent
import com.reeflive.core.ai.Ctx
import com.reeflive.core.ai.Perception
import com.reeflive.core.ai.SpeciesBrains
import com.reeflive.core.ai.Tap
import com.reeflive.core.geom.FlowField
import com.reeflive.core.geom.Rng
import com.reeflive.core.geom.Vec3
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * The tank simulation (SIMULATION §1). Pure Kotlin, no Android. Two clocks:
 * behavior/physics ticks (`stepWall` at 20 Hz wall, `dtB = min(speed/20, 0.5)` sim s) and the
 * 60-s chemistry/health tick fed by an accumulator. Above [ABSTRACT_SPEED] the behavior layer
 * switches to abstract mode (animals parked at their behavior's home spots, idle animation).
 *
 * Threading: [enqueue] is safe from any thread; everything else runs on the sim thread.
 * [latest] is a volatile snapshot for the renderer/UI.
 */
class Simulation private constructor(
    config: TankConfig,
    val species: SpeciesCatalog,
    /** This simulation's own copy of the rockscape ([RockData.forSim]): its rocks can move without touching another sim's. */
    val rock: RockData,
    seed: Long,
    startTimeSec: Double,
    initialWater: WaterState,
    livestock: List<LivestockSeed>,
    val lights: LightSchedule,
    @Suppress("UNUSED_PARAMETER") ownRock: Unit,
) {
    constructor(
        config: TankConfig,
        species: SpeciesCatalog,
        rock: RockData,
        seed: Long = config.seed,
        startTimeSec: Double = 12 * 3600.0,
        initialWater: WaterState = WaterState(volumeL = config.systemLiters),
        livestock: List<LivestockSeed> = emptyList(),
        lights: LightSchedule = LightSchedule(),
    ) : this(config, species, rock.forSim(), seed, startTimeSec, initialWater, livestock, lights, Unit)

    var config: TankConfig = config; private set
    /**
     * The tank's box and look (My tank builds set theirs, [CustomTank]): the renderer sizes the glass, equipment and
     * camera to it, and equipment-side spots built for Tee's tank (jets, the HOB, feeding) are placed by it.
     */
    var shape: TankShape = TankShape(config.lengthCm, config.heightCm, config.widthCm)
    val rng = Rng(seed)
    var water = WaterSim(config, initialWater); private set
    val organisms = ArrayList<Organism>()
    val corals = ArrayList<Coral>()
    val flow = FlowField(config.powerheads, rock.sdf, config.lengthCm, config.waterLevelCm, tankDepth = config.widthCm)
    val food = FoodSystem(rock, flow, rng.fork(7))
    val perception = Perception(this)
    /** Breeding and sex change (coverage B-1..B-3), stepped in the chemistry tick. */
    val repro = Reproduction(this)
    /** Larvae, the nursery tank and fry (research 28), stepped right after [repro]. */
    val brood = Brood(this)
    /** Hermit shells, asterina-vs-organ-pipe and feather-duster crown cycle (research 04). */
    val invertEcology = InvertEcology(this)
    /** Bubble-tip anemones and the clowns adopting them (research 40). */
    val anemones = AnemoneModel(this)
    /** My tank's away grace (custom AwayCatchUp): hunger and slowly worsening water cannot kill while it is on. */
    var healthGrace = false
    /** What the keeper did (feedings, chores) for My tank's daily care list ([CareLog]). */
    val careLog = CareLog()
    private val agents = HashMap<Organism, Agent>()

    var simTimeSec: Double = startTimeSec; private set
    var speed: Double = 1.0
        set(v) { field = v.coerceIn(0.0, 1000.0) }
    /**
     * Game pace of slow biology ([LifePace]): growth, coral growth and spread, breeding, nursery fry, anemone splits,
     * pest and asterina populations and molts run this many times faster than the sim clock, while motion, hunger,
     * water chemistry, fouling, lights and damage from bad water stay on it. 1 = real (Watch, stores); My tank
     * defaults to [LifePace.DEFAULT]. Unlike [speed] it does not fast-forward the tank, so animals still move at real speed.
     */
    var lifePace: Double = 1.0
        set(v) { field = if (v.isFinite()) v.coerceIn(LifePace.MIN, LifePace.MAX) else 1.0 }
    /** The game-biology clock: advances [lifePace] game seconds per sim second (equals [simTimeSec] at pace 1). */
    var lifeSec: Double = startTimeSec
    /** Sim seconds that [days] game days of slow biology take (for stamps: `simTimeSec + lifeDays(x)`). */
    fun lifeDays(days: Double): Double = days * 86400.0 / lifePace
    /** Game days since the sim-clock stamp [sinceSimSec]. */
    fun lifeAge(sinceSimSec: Double): Double = (simTimeSec - sinceSimSec) * lifePace / 86400.0
    var predationEnabled: Boolean = config.predationEnabled
    /** Σ BL² / footprint (06 §3.4) and its aggression multiplier, refreshed every chemistry tick. */
    var crowding: Double = com.reeflive.core.ai.PairMatrix.CROWDING_BASE; private set
    var crowdingMult: Double = 1.0; private set
    /**
     * Sandbox toggle: banded shrimp may kill a freshly molted skunk cleaner (06 §2.14). Off by default:
     * cross-species kills are unsourced (07 gap 4); the conspecific molt-window rule stays on.
     */
    var crossSpeciesShrimpKills = false
    /**
     * Sandbox scenario (research 11 §3): a big, hungry Sally Lightfoot may attack a sleeping small fish. Off by default:
     * *Percnon* is primarily a grazer and no live-fish capture rate is sourced. SetParam id `crab_predation` (1 = on).
     */
    @Ev(Evidence.DESIGN_ESTIMATE, "11 §3") var crabLiveFishScenario = false
    /**
     * Sandbox scenario (research 11 §7): a capped asterina population grazes organ pipe tissue. Off by default: no
     * species-confirmed corallivory for the owner's unidentified stars. SetParam id `asterina_coral` (1 = on).
     */
    @Ev(Evidence.DESIGN_ESTIMATE, "11 §7") var asterinaCorallivoryScenario = false
    /**
     * Evidence gate (research realism_gaps X7): predation rolls with no supporting source stay off even in the sandbox
     * until this is on: the Sally Lightfoot's snail rolls and grabs (PUC06, PIZ25 support grazing and scavenging, not a
     * kill rate) and the moray's kill roll (SIMULATION §9.2 numbers are unsourced). Off, the eel's strike still lands as
     * an attack that startles. SetParam id `unsourced_predation` (1 = on). Crab-on-fish keeps its own `crab_predation`.
     */
    @Ev(Evidence.DESIGN_ESTIMATE, "realism_gaps X7; 11 §3") var unsourcedPredation = false
    /** Shrimp pairing, molt-linked mating and egg carrying (research 12). */
    val shrimpBreeding = ShrimpBreeding(this)
    /** Sim time of the last [Command.RearrangeRock]; territorial bouts run hot for a day after it (06 §3.3). */
    var territoryResetAt = -1e12
    /** Night-time zooplankton emerging from the rock (06 §1.3; the cardinal's natural food). Particles per hour. */
    var nightPlanktonPerHour = 60.0
    private var planktonAcc = 0.0
    private var lastNightRollDay = Int.MIN_VALUE
    /** Run the behavior layer at full fidelity regardless of speed (tests / SimRunner). */
    var forceFullBehavior = false
    /** Disable the behavior layer entirely (health-only long runs). */
    var behaviorEnabled = true
    /** Started from scratch (core custom/CustomTank): the snapshot asks the renderer to draw no static colonies. */
    var bareScape = false

    private val commands = ConcurrentLinkedQueue<Command>()
    /** Coral growth, branch drops and pest rolls draw from their own stream so they leave the behaviour RNG untouched. */
    val growthRng = Rng(seed xor 0x6C0A1L)
    /** Coral budding and splitting (CoralHealth.maybeSpread) roll here, so they leave the growth and pest stream untouched. */
    private val spreadRng = Rng(seed xor 0x5B4EADL)
    private var fragCounter = 1
    fun nextFragId(): Int = fragCounter++

    /**
     * A restored tank (core `custom/CustomTank`) keeps its animals' ids: new ids handed out after it ("<species>#n",
     * frag and bag numbers) must start past the saved ones so a newcomer never takes a saved animal's id.
     */
    fun reserveIds(nextOrganism: Int, nextFrag: Int, nextBag: Int) {
        idCounter = max(idCounter, nextOrganism); fragCounter = max(fragCounter, nextFrag); bags.reserveIds(nextBag)
    }
    /** Aiptasia controllers (AiptasiaModel). */
    val pests = PestState()
    /** Running disease treatments (W-4). */
    val treatments = Treatments()
    /** New-tank cycle checkpoints, when a cycle is being tracked (Presets.newTankCycle; W-5). */
    var cycle: CycleTracker? = null
    val aiptasiaCount: Int get() = corals.count { it.kind == CoralKind.AIPTASIA && it.tissue > 0.01 }

    /** Copies pest controllers from another simulation (time-lapse fork). */
    fun copyPestsFrom(src: Simulation) { pests.copyFrom(src.pests); treatments.copyFrom(src.treatments); fragCounter = max(fragCounter, src.fragCounter) }

    /** A parameter ramp/hold (06 §4.2): linear from [from] to [to] over [overSec], then held if [hold]. */
    class ParamRamp(val id: String, val from: Double, val to: Double, val startSec: Double, val overSec: Double, val hold: Boolean) {
        fun value(t: Double): Double = if (overSec <= 0) to else from + (to - from) * ((t - startSec) / overSec).coerceIn(0.0, 1.0)
        fun done(t: Double) = t >= startSec + overSec
    }
    /** Active ramps by parameter id (lower case). */
    val ramps = LinkedHashMap<String, ParamRamp>()
    /** Commands waiting for their sim time ([Command.Schedule]), sorted by time. */
    private val scheduled = ArrayList<Pair<Double, Command>>()
    val scheduledCount: Int get() = scheduled.size
    private val pendingEvents = ArrayList<SimEvent>()
    val eventLog = ArrayDeque<SimEvent>()
    private var chemAccum = 0.0
    var tickCount = 0L; private set
    private var lastLight = 0.0
    private var grazeAccum = 0.0
    private var idCounter = 1
    val moltShells = ArrayList<Pair<Vec3, Double>>()   // position, born at
    /** Sand piles spat out by burrowing gobies: position on the sand and volume (cm³). */
    val sandPiles = ArrayList<SandPile>()

    /** Dynamic sand bed: height, freshly turned sand, detritus and suspended clouds (SIMULATION §16). */
    val sand = SandBed(rock.lengthCm, rock.widthCm, rock.sandTopCm, seed).also { s ->
        s.attachRock(rock.sdf)
        // food comes to rest on the live sand surface, not inside it
        food.groundY = { x, z -> s.surfaceY(x, z) }
    }
    /** Rock, live sand and glass for the crawlers' surface seat ([CrawlerSeat]). */
    val crawlSurface = CrawlSurface(rock, sand)
    /** The same with the rock's pores smoothed shut, for the limb rigs' arms and legs ([CrawlSurface.rockDist]). */
    val limbSurface = CrawlSurface(rock, sand, smoothPores = true)
    /** Nassarius trails, hermit and crab scratching, brittle-star furrows (observed after each behaviour tick). */
    val sandWork = SandWork(sand, rock)
    /** Bodies rest on the live sand and push it aside instead of passing through it (SIMULATION §16.9). */
    val sandContact = SandContact(sand, rock.sdf)
    /** Fish feces: guts, drifting and settled pellets, their fates (research 18_fish_waste; [WasteSystem]). */
    val waste = WasteSystem(rock, flow, sand, Rng(seed xor 0x1F3CE5L))   // own stream: forking [rng] would reshuffle every animal
    /** Single-occupant holes and who holds them (sim/RockHoles.kt): no two animals share a blenny hole or a small cave. */
    val holeClaims = HoleClaims { it in organisms }
    /** Keeps bodies out of solid rock except in holes that fit them (SIMULATION §4.11). */
    val rockGuard = RockGuard(rock, { x, z -> sand.surfaceY(x, z) }, holeClaims)

    // ============================================================== dynamic rockscape
    /** Loose rocks as rigid bodies while [dynamicRocks] is on (null for a scape without separate rocks). */
    val rockPhysics: RockPhysics? = rock.dynamic?.let { RockPhysics(it, sand, rock.lengthCm, rock.widthCm) }
    /** Rocks can settle or topple when the sand under them is dug out ([Command.DynamicRocks]); off = no rock physics. */
    var dynamicRocks = false; private set
    private var rockPosesCache: FloatArray? = null
    private var rockPosesAt = -1L
    private var anchorPiece: Map<String, Int>? = null
    private var holePiece: Map<String, Int>? = null

    /** Turns the dynamic rockscape on (builds the rock bodies the first time) or off (rocks freeze where they are). */
    fun setDynamicRocks(on: Boolean) {
        val rp = rockPhysics ?: return
        if (on) rp.build()
        dynamicRocks = on
    }

    private fun stepRocks(dt: Double) {
        val rp = rockPhysics ?: return
        // static scape: only a rock just dropped in with the aquascape builder falls (no support checks)
        if (!dynamicRocks && !rp.anyAwake) return
        resolveCoralRocks()
        rp.step(dt, checks = dynamicRocks)
        for (s in rp.drainSettled()) onRockSettled(s)
    }

    private fun onRockSettled(s: RockPhysics.Settle) {
        if (s.body.dropping) {
            // a builder rock landed: the scape changed (save it), it is no rockslide
            s.body.dropping = false
            refreshRockDependents()
            val sy = sand.surfaceY(s.x.coerceIn(0.0, rock.lengthCm), s.z.coerceIn(0.0, rock.widthCm))
            sand.puff(s.x, sy, s.z, 10, 4.0)
            emit(SimEvent.RockscapeChanged(s.body.name, true, simTimeSec))
            return
        }
        if (s.moveCm < 0.3 && s.turnDeg < 1.0) return
        refreshRockDependents()
        // the thud: animals nearby startle, and a puff of sand and mulm comes up where it landed
        val sy = sand.surfaceY(s.x.coerceIn(0.0, rock.lengthCm), s.z.coerceIn(0.0, rock.widthCm))
        tapGlass(Vec3(s.x, max(sy + 1.0, s.y - s.body.extent * 0.5), s.z))
        sand.puff(s.x, sy, s.z, (6 + 2 * s.moveCm).toInt().coerceAtMost(40), 4.0 + min(8.0, s.moveCm))
        emit(SimEvent.RockShifted(s.body.name, s.moveCm, s.turnDeg, simTimeSec))
    }

    /**
     * After a rock came to rest: the sand's rock roof (burrows), the anchors on the rock (perches, holes, caves, coral
     * spots), the corals growing on it and the flow solver's solid cells follow it.
     */
    private fun refreshRockDependents() {
        val f = rock.dynamic ?: return
        sand.attachRock(rock.sdf)
        val ap = anchorPiece ?: rock.restAnchors.all.associate { it.name to RockPieceDef.labelOf(rock.pieces, it.pos.x, it.pos.y, it.pos.z, ANCHOR_ON_ROCK_CM) }.also { anchorPiece = it }
        rock.anchors = AnchorSet(rock.restAnchors.all.map { a ->
            val p = ap[a.name] ?: -1
            if (p >= 0 && f.gone[p]) {
                // its rock was taken out: the spot drops to the sand below
                val at = fitFree(a.pos)
                val sy = sand.surfaceY(at.x, at.z)
                a.copy(pos = Vec3(at.x, sy + 1.0, at.z), points = a.points?.map { val q = fitFree(it); Vec3(q.x, sy + 1.0, q.z) })
            } else if (p < 0) {
                if (fitted) a.copy(pos = fitFree(a.pos), points = a.points?.map { fitFree(it) }) else a
            } else if (!f.moved[p]) a else {
                val m = f.xf[p]
                a.copy(pos = xfPoint(m, a.pos), dir = a.dir?.let { xfDir(m, it) }, normal = a.normal?.let { xfDir(m, it) }, points = a.points?.map { xfPoint(m, it) })
            }
        })
        // the holes and caves ride with their rock too (the brains look their homes up by name, RockHoles.kt)
        val hp = holePiece ?: rock.restHoles.all.associate { h ->
            // a burrow is dug in the sand: it stays where it is (the sand keeps its own roof, SandBed.attachRock)
            if (h.kind == HoleKind.BURROW) return@associate h.name to -1
            val q = h.along(min(h.depth, HOLE_LABEL_IN_CM))
            h.name to RockPieceDef.labelOf(rock.pieces, q.x, q.y, q.z, ANCHOR_ON_ROCK_CM)
        }.also { holePiece = it }
        rock.holes = HoleSet(rock.restHoles.all.mapNotNull { h ->
            val p = hp[h.name] ?: -1
            // its rock was taken out: the hole is gone with it (its dweller shelters on the rock face instead)
            if (p >= 0 && f.gone[p]) null
            else if (p < 0) { if (fitted) h.copy(mouth = fitFree(h.mouth), path = h.path.map { fitFree(it) }) else h }
            else if (!f.moved[p]) h else {
                val m = f.xf[p]
                h.copy(mouth = xfPoint(m, h.mouth), axis = xfDir(m, h.axis), path = h.path.map { xfPoint(m, it) }, up = xfDir(m, h.up))
            }
        })
        resolveCoralRocks()
        for (c in corals) {
            val p = c.rockPiece
            if (p >= 0 && f.gone[p]) {
                // its rock was taken out: the colony tumbles onto the sand below
                val sy = sand.surfaceY(c.pos.x.coerceIn(0.0, rock.lengthCm), c.pos.z.coerceIn(0.0, rock.widthCm))
                c.pos = Vec3(c.pos.x, sy, c.pos.z); c.normal = Vec3(0.0, 1.0, 0.0)
                c.restPos = c.pos; c.restNormal = c.normal; c.rockPiece = -1; c.patch = null
                continue
            }
            if (p < 0 || !f.moved[p]) continue
            c.pos = xfPoint(f.xf[p], c.restPos); c.normal = xfDir(f.xf[p], c.restNormal); c.patch = null
        }
        fluid?.let { fl -> for (k in 0 until fl.nz) for (j in 0 until fl.ny) for (i in 0 until fl.nx) fl.solid[fl.idx(i, j, k)] = rock.sdf.dist(fl.center(i, j, k)) < 0.0 }
    }

    /**
     * How far the stock rocks were moved to fit a tank of another size ([fitStockScape]); spots that sit on no rock
     * (sand-level caves, burrows) move with them.
     */
    var scapeShift: Vec3 = Vec3.ZERO; private set
    private val fitted: Boolean get() = scapeShift != Vec3.ZERO || !shape.isStock

    /**
     * A spot on no rock, in this tank: equipment-side spots (at the water line or a glass of Tee's tank: the HOB
     * outflow, the nori clip, feeding spots) follow the glass they were at ([TankShape.map]); the others move with
     * the rocks ([scapeShift]). Kept inside the water.
     */
    private fun fitFree(p: Vec3): Vec3 {
        if (!fitted) return p
        val edge = p.y > TankDims.WATER_LEVEL_CM - 6 || p.x < TankShape.EDGE_CM || p.x > TankDims.LENGTH_CM - TankShape.EDGE_CM ||
            p.z < TankShape.EDGE_CM || p.z > TankDims.DEPTH_CM - TankShape.EDGE_CM
        val q = if (edge) shape.map(p) else p + scapeShift
        return Vec3(q.x.coerceIn(1.0, rock.lengthCm - 1.0), q.y.coerceIn(0.5, rock.waterLevelCm), q.z.coerceIn(1.0, rock.widthCm - 1.0))
    }

    /**
     * Fits Tee's rockscape into this tank when it is another size (a My tank build): of the rocks that fit, the run
     * with the most rock stays, centred along the glass; the rest are taken out (RockPhysics.fitInto). [savedShift]
     * (a reopened tank whose rocks were already fitted and saved) only restores where the free spots went.
     */
    fun fitStockScape(savedShift: Vec3? = null) {
        val rp = rockPhysics ?: return
        if (savedShift != null) { scapeShift = savedShift; refreshRockDependents(); return }
        if (shape.isStock) return
        rp.build()
        val dx = rp.fitInto(rock.lengthCm, rock.widthCm) ?: 0.0
        scapeShift = Vec3(dx, 0.0, 0.0)
        refreshRockDependents()
    }

    /**
     * Adds saved animals and colonies after the rockwork is in place (a reopened My tank), so each starts at its spot
     * on the rock as it is now.
     */
    fun addRestored(livestock: List<LivestockSeed>) {
        livestock.forEach { addFromSeed(it) }
        corals.forEach { it.established = true }
        perception.rebuild()
        latest = snapshot()
    }

    /** Which rock each new colony grows on (a colony placed after its rock moved is re-expressed in the rock's frame). */
    private fun resolveCoralRocks() {
        val f = rock.dynamic ?: return
        for (c in corals) {
            if (c.rockPiece != -2) continue
            var best = -1
            if (f.active) {
                var bd = CORAL_ON_ROCK_CM
                for (p in rock.pieces.indices) if (f.moved[p]) { val d = f.pieceDist(p, c.pos.x, c.pos.y, c.pos.z); if (d < bd) { bd = d; best = p } }
            }
            if (best >= 0) {
                c.rockPiece = best
                val m = f.xf[best]
                c.restPos = xfInverse(m, c.pos); c.restNormal = xfDirInverse(m, c.normal)
            } else {
                val l = RockPieceDef.labelOf(rock.pieces, c.restPos.x, c.restPos.y, c.restPos.z, CORAL_ON_ROCK_CM)
                c.rockPiece = if (l >= 0 && !f.moved[l]) l else -1
            }
        }
    }

    /** Moved rocks as text for saving the tank ("" when none moved or the scape has no loose rocks). */
    fun rockState(): String = rockPhysics?.takeIf { it.built && it.anyMoved }?.encode() ?: ""

    /**
     * Digs the sand out from under one side of a rock that stands mostly on sand, past its centre of mass, down to the
     * glass ([Command.UndermineRock]). Returns the rock's name, or null when no rock stands on sand.
     */
    fun undermineRock(name: String? = null): String? {
        val rp = rockPhysics ?: return null
        rp.build()
        // the rock leaning least on glass and other rocks goes first: digging under it actually tips it
        val b = rp.bodies.filter { b -> !b.pinned && !b.awake && (name == null || b.name == name) }
            .onEach { rp.stable(it) }.filter { it.supSand > it.supGlass + it.supRock }
            .minByOrNull { (it.supGlass + it.supRock).toDouble() / (it.supSand + it.supGlass + it.supRock) } ?: return null
        var ang = (simTimeSec * 1.37) % (2 * kotlin.math.PI)
        var dx = 0.0; var dz = 0.0
        // try sides until it no longer stands
        for (attempt in 0 until 4) {
            dx = kotlin.math.cos(ang); dz = kotlin.math.sin(ang)
            if (!scourUnder(b, dx, dz)) return null
            if (!rp.stable(b)) break
            ang += 0.5 * kotlin.math.PI
        }
        val sy = sand.surfaceY(b.pos[0].coerceIn(0.0, rock.lengthCm), b.pos[2].coerceIn(0.0, rock.widthCm))
        sand.puff(b.pos[0] + dx * b.extent * 0.5, sy, b.pos[2] + dz * b.extent * 0.5, 12, 3.0)
        return b.name
    }

    /** Digs the sand under [b]'s footprint on the (dx, dz) side, out to the floor. False when it has no underside. */
    private fun scourUnder(b: RockBody, dx: Double, dz: Double): Boolean {
        // the footprint: where the rock's underside meets the sand
        val r = b.rot
        var x0 = Double.MAX_VALUE; var x1 = -Double.MAX_VALUE; var z0 = Double.MAX_VALUE; var z1 = -Double.MAX_VALUE
        for (i in 0 until b.np) {
            val ny = r[3] * b.nxL[i] + r[4] * b.nyL[i] + r[5] * b.nzL[i]
            if (ny > -RockPhysics.BOTTOM_NY) continue
            val x = b.pos[0] + r[0] * b.rx[i] + r[1] * b.ry[i] + r[2] * b.rz[i]
            val z = b.pos[2] + r[6] * b.rx[i] + r[7] * b.ry[i] + r[8] * b.rz[i]
            x0 = minOf(x0, x); x1 = maxOf(x1, x); z0 = minOf(z0, z); z1 = maxOf(z1, z)
        }
        if (x0 > x1) return false
        // scour that side out to the floor, a little past the centre of mass so it tips that way
        var x = maxOf(0.5, x0 - 1.0)
        while (x <= minOf(rock.lengthCm - 0.5, x1 + 1.0)) {
            var z = maxOf(0.5, z0 - 1.0)
            while (z <= minOf(rock.widthCm - 0.5, z1 + 1.0)) {
                if ((x - b.pos[0]) * dx + (z - b.pos[2]) * dz >= -UNDERMINE_PAST_CM) repeat(4) { sand.dig(x, z, 40.0, 1.2) }
                z += 1.0
            }
            x += 1.0
        }
        return true
    }

    // ============================================================== aquascape builder
    private var placeSeq = 0

    /**
     * Drops a white dry rock of [shape] over ([x], [z]) ([Command.PlaceRock]): it starts just above whatever is under its
     * footprint, falls and settles. Works with the rockscape static too (only the new rock moves). Returns its name.
     */
    fun placeRock(shape: DryRockShape, x: Double, z: Double, yawDeg: Double = 0.0, seed: Int = -1): String? {
        val rp = rockPhysics ?: return null
        rp.build()
        resolveCoralRocks()
        val sd = if (seed >= 0) seed else (config.seed.toInt() * 31 + (++placeSeq) * 7477 + (simTimeSec * 13).toInt()) and 0x7fffffff
        val def = DryRock.def(shape, sd)
        val yaw = yawDeg * kotlin.math.PI / 180.0
        val c = kotlin.math.cos(yaw); val sn = kotlin.math.sin(yaw)
        // footprint after the turn, kept inside the glass
        val b = def.box
        var hx = 0.0; var hz = 0.0
        for (cx in doubleArrayOf(b[0], b[3])) for (cz in doubleArrayOf(b[2], b[5])) {
            hx = max(hx, kotlin.math.abs(c * cx + sn * cz)); hz = max(hz, kotlin.math.abs(-sn * cx + c * cz))
        }
        val px = if (2 * hx + 2 > rock.lengthCm) rock.lengthCm / 2 else x.coerceIn(hx + 1.0, rock.lengthCm - hx - 1.0)
        val pz = if (2 * hz + 2 > rock.widthCm) rock.widthCm / 2 else z.coerceIn(hz + 1.0, rock.widthCm - hz - 1.0)
        // the highest sand or rock under the footprint
        var top = SandBed.GLASS_Y
        var gx = px - hx
        while (gx <= px + hx) {
            var gz = pz - hz
            while (gz <= pz + hz) {
                val sx = gx.coerceIn(0.0, rock.lengthCm); val sz = gz.coerceIn(0.0, rock.widthCm)
                top = max(top, sand.surfaceY(sx, sz))
                var y = rock.waterLevelCm
                while (y > top) { if (rock.sdf.dist(Vec3(sx, y, sz)) < 0.0) { top = y; break }; y -= 1.0 }
                gz += 1.5
            }
            gx += 1.5
        }
        val height = b[4] - b[1]
        val bottom = min(top + DROP_GAP_CM, max(top + 0.5, rock.waterLevelCm - height))
        val body = rp.addRock(shape, sd, px, bottom, pz, yaw, simTimeSec)
        emit(SimEvent.RockscapeChanged(body.name, true, simTimeSec))
        return body.name
    }

    /** Takes out the rock nearest [at], or the dry rock added last ([Command.RemoveRock]). Returns its name. */
    fun removeRock(at: Vec3? = null): String? {
        val rp = rockPhysics ?: return null
        rp.build()
        val b = if (at == null) rp.added.maxByOrNull { it.bornSec } else rp.bodyNear(at.x, at.y, at.z, REMOVE_NEAR_CM)
        b ?: return null
        takeOut(rp, listOf(b))
        return b.name
    }

    /** Empties the tank of rock ([Command.ClearRocks]): build a scape from scratch. */
    fun clearRocks() {
        val rp = rockPhysics ?: return
        rp.build()
        takeOut(rp, rp.bodies.toList())
    }

    private fun takeOut(rp: RockPhysics, bodies: List<RockBody>) {
        if (bodies.isEmpty()) return
        resolveCoralRocks()
        for (b in bodies) rp.removeBody(b)
        refreshRockDependents()
        for (b in bodies) {
            val sy = sand.surfaceY(b.pos[0].coerceIn(0.0, rock.lengthCm), b.pos[2].coerceIn(0.0, rock.widthCm))
            sand.puff(b.pos[0], sy, b.pos[2], 8, 4.0)
            emit(SimEvent.RockscapeChanged(b.name, false, simTimeSec))
        }
    }

    private var builtCache: List<BuiltRock>? = null
    private var builtAt = -1L
    private var builtDay = -1L

    /** The builder's dry rocks for the snapshot (re-made when a rock changes or once a sim hour as they cure). */
    private fun builtRocks(): List<BuiltRock>? {
        val rp = rockPhysics ?: return null
        if (!rp.built) return null
        val hour = (simTimeSec / 3600).toLong()
        if (builtAt == rp.version && builtDay == hour) return builtCache
        builtAt = rp.version; builtDay = hour
        val list = rp.added.map { b -> BuiltRock(b.pieces[0], b.shape!!, b.seed, DryRock.dryness((simTimeSec - b.bornSec) / 86400.0).toFloat()) }
        builtCache = list.ifEmpty { null }
        return builtCache
    }

    /** A fork of [src] (sandbox, time-lapse): its rocks where they lie and its dynamic / static choice. */
    fun copyRocksFrom(src: Simulation) {
        restoreRocks(src.rockState())
        setDynamicRocks(src.dynamicRocks)
    }

    /** Puts rocks back where a saved tank had them ([rockState]). */
    fun restoreRocks(state: String) {
        val rp = rockPhysics ?: return
        if (state.isBlank()) return
        rp.build()
        resolveCoralRocks()
        rp.decode(state)
        refreshRockDependents()
    }

    private fun rockPoses(): FloatArray? {
        val rp = rockPhysics ?: return null
        if (!rp.built || !rp.anyMoved) return null
        if (rockPosesAt != rp.version || rockPosesCache == null) { rockPosesCache = rp.poseMatrices(); rockPosesAt = rp.version }
        return rockPosesCache
    }

    private fun xfPoint(m: DoubleArray, v: Vec3) = Vec3(
        m[0] * v.x + m[1] * v.y + m[2] * v.z + m[9], m[3] * v.x + m[4] * v.y + m[5] * v.z + m[10], m[6] * v.x + m[7] * v.y + m[8] * v.z + m[11])
    private fun xfDir(m: DoubleArray, v: Vec3) = Vec3(m[0] * v.x + m[1] * v.y + m[2] * v.z, m[3] * v.x + m[4] * v.y + m[5] * v.z, m[6] * v.x + m[7] * v.y + m[8] * v.z)
    private fun xfDirInverse(m: DoubleArray, v: Vec3) = Vec3(m[0] * v.x + m[3] * v.y + m[6] * v.z, m[1] * v.x + m[4] * v.y + m[7] * v.z, m[2] * v.x + m[5] * v.y + m[8] * v.z)
    private fun xfInverse(m: DoubleArray, v: Vec3) = xfDirInverse(m, Vec3(v.x - m[9], v.y - m[10], v.z - m[11]))
    private val sandFlowAt: (Double, Double, Double) -> Vec3 = { x, y, z -> flow.velocityFast(Vec3(x, y, z)) }
    private var lastSandGridAt = -1e9
    private var sandGridCache: SandGridSnapshot? = null
    private var sandRelaxPendingMin = 0.0

    /** A goby drops a mouthful of sand at [at]; nearby piles merge (max 8 piles). The sand bed gets the mound and a puff. */
    fun depositSand(at: Vec3, cm3: Double) {
        sand.deposit(at.x, at.z, cm3, 2.0)
        sand.puff(at.x, sand.surfaceY(at.x, at.z) + 0.6, at.z, 6, 4.0)
        val p = sandPiles.firstOrNull { it.pos.distanceTo(at) < 3.0 }
        if (p != null) p.volume += cm3
        else {
            if (sandPiles.size >= 8) sandPiles.minByOrNull { it.volume }?.let { sandPiles.remove(it) }
            sandPiles += SandPile(Vec3(at.x, rock.sandTopCm, at.z), cm3)
        }
    }

    /**
     * A digger spits the mouthful it carries ([SandBed.carriedCm3]) at [at], blowing along ([hx], [hz]): the spoil
     * mound and its plume go into the sand bed (mass-conserving) and the legacy pile list merges it like [depositSand].
     */
    fun spitSand(at: Vec3, cm3: Double, hx: Double = 0.0, hz: Double = 0.0) {
        sand.spit(at.x, sand.surfaceY(at.x, at.z) + 0.8, at.z, cm3, 1.4, plume = 6, hx = hx, hz = hz)
        val p = sandPiles.firstOrNull { it.pos.distanceTo(at.withY(rock.sandTopCm)) < 3.0 }
        if (p != null) p.volume += cm3
        else {
            if (sandPiles.size >= 8) sandPiles.minByOrNull { it.volume }?.let { sandPiles.remove(it) }
            sandPiles += SandPile(Vec3(at.x, rock.sandTopCm, at.z), cm3)
        }
    }
    private val lightCh = DoubleArray(LightSchedule.CHANNELS)
    private var cachedSinceOn: Double? = null
    private var cachedUntilOff: Double? = null
    private var lastAlgaeBloomReport = 0.0

    @Volatile var latest: SimSnapshot = SimSnapshot.EMPTY; private set

    // ---- water physics (SIMULATION §9.7): optional Navier–Stokes flow + surface waves ----
    var fluid: com.reeflive.core.geom.FluidSim? = null; private set
    val surface = com.reeflive.core.geom.WaterSurface(rock.lengthCm, rock.widthCm).also { w -> w.hobPos = w.hobPos?.let { shape.map(it).withY(rock.waterLevelCm) } }
    private var fluidAcc = 0.0
    private var lastFlowGridAt = -1e9
    private var flowGridCache: com.reeflive.core.geom.FlowGrid? = null
    /** Parameter id the fluid's dose tracer currently represents. */
    var doseTracerParam: String? = null; private set
    val heaterPos = Vec3(rock.lengthCm - 2.4, (rock.sandTopCm + rock.waterLevelCm) / 2, 2.5)   // equipment.glb Heater, back-right corner

    /** Turn on the grid flow solver (Live Tank); spins it up for 20 s of sim time before handing it to [flow]. */
    fun enableFluid(quality: com.reeflive.core.geom.FluidQuality) {
        val f = com.reeflive.core.geom.FluidSim(rock.lengthCm, rock.widthCm, rock.sandTopCm, rock.waterLevelCm, quality,
            pumps = com.reeflive.core.geom.Pump.defaults().map { it.copy(pos = shape.map(it.pos), intake = it.intake?.let { i -> shape.map(i) }) },
            solidAt = { p -> rock.sdf.dist(p) < 0.0 })
        f.strength = water.flowStrength; f.powerheadsOn = water.s.flowOn
        repeat(200) { f.step(0.1, water.heaterOutputW, heaterPos) }
        fluid = f
        flow.fluid = f
    }

    fun disableFluid() { fluid = null; flow.fluid = null }

    /** Fluid + surface step at ~15 Hz of wall time (called from [stepWall]). */
    private fun stepWater(dtWall: Double) {
        fluidAcc += dtWall
        if (fluidAcc < 1.0 / 15) return
        val dt = min(fluidAcc * max(speed, 0.0), 0.1)
        fluidAcc = 0.0
        if (dt <= 0) return
        fluid?.let { f ->
            for (p in food.particles) if (!p.eaten) f.addScent(p.pos, 0.02)
            f.step(dt, water.heaterOutputW, heaterPos)
        }
        surface.agitation = if (water.s.flowOn) water.flowStrength else 0.0
        surface.step(dt)
    }

    /** Water at a point: the tank mean plus the local temperature / dose anomaly from the flow solver. */
    fun localWater(p: Vec3): WaterState {
        val f = fluid ?: return water.s
        val dT = f.tempAnomaly(p)
        val dd = f.doseExcess(p)
        if (abs(dT) < 0.05 && abs(dd) < 1e-4) return water.s
        val w = water.s.copy(tempC = water.s.tempC + dT)
        doseTracerParam?.let { id -> w.get(id)?.let { base -> applyParam(w, id, base + dd) } }
        return w
    }

    private fun applyParam(w: WaterState, id: String, v: Double) {
        when (id.lowercase()) {
            "alk" -> w.alk = v; "ca" -> w.ca = v; "mg" -> w.mg = v; "po4" -> w.po4 = v; "no3" -> w.no3 = v
            "k" -> w.k = v; "sr" -> w.sr = v; "i" -> w.i = v; "copper", "cu" -> w.copper = v
            "nh3", "nh3total", "tan" -> { w.nh3Total = v; w.nh3Free = ChemFns.freeNh3(v, w.ph, w.tempC) }
            "no2" -> w.no2 = v
        }
    }

    val abstractMode: Boolean get() = !forceFullBehavior && speed > ABSTRACT_SPEED
    val dayIndex: Int get() = floor(simTimeSec / 86400).toInt()
    val minuteOfDay: Double get() = (simTimeSec / 60) % 1440

    /** Hands the sub's autopilot where the watched animal is; a watched animal that died or left ends the watch. */
    /** The fish the sub's ‹ › moves to ([Command.SubmarineWatchNext]), or null when there is none. */
    private fun nextSubFish(step: Int): String? {
        val fish = organisms.filter { it.alive && it.species.kind == "FISH" }
        if (fish.isEmpty()) return null
        val i = fish.indexOfFirst { it.id == submarine.watchId }
        if (i < 0) return fish.minByOrNull { it.pos.distanceTo(submarine.pos) }?.id
        val n = fish.size
        return fish[((i + step) % n + n) % n].id
    }

    private fun feedSubWatch() {
        val id = submarine.watchId ?: return
        if (!submarine.active) return
        val o = organisms.firstOrNull { it.id == id }
        if (o == null || !o.alive) submarine.watch(null) else submarine.setTarget(o.pos, o.vel, o.lengthCm)
    }

    // ============================================================== public API
    fun enqueue(cmd: Command) { commands.add(cmd) }

    /** Called by the sim thread every wall tick (normally 1/20 s). */
    fun stepWall(dtWall: Double) {
        drainCommands()
        feedSubWatch()
        submarine.step(dtWall, rock, sand)
        bags.stepWall(this, dtWall)
        net.stepWall(this, dtWall)
        val simDt = bags.effectiveSpeed(speed) * dtWall
        stepWater(dtWall)
        if (simDt <= 0) { latest = snapshot(); return }
        val dtB = min(simDt, MAX_BEHAVIOR_DT)
        advanceInternal(simDt, dtB)
        latest = snapshot()
    }

    /**
     * Headless advance of [simSeconds] with behavior ticks of [behaviorDt] (tests, SimRunner).
     * Every sim second is covered by behavior when [fullBehavior] (no subsampling).
     */
    fun advance(simSeconds: Double, behaviorDt: Double = 0.05, fullBehavior: Boolean = true, onTick: (() -> Unit)? = null) {
        val prevForce = forceFullBehavior
        val prevBehavior = behaviorEnabled
        forceFullBehavior = fullBehavior
        var left = simSeconds
        while (left > 1e-9) {
            drainCommands()
            val dt = min(behaviorDt, left)
            feedSubWatch()
            submarine.step(dt, rock, sand)
            bags.stepWall(this, dt)
            net.stepWall(this, dt)
            advanceInternal(dt, dt)
            left -= dt
            onTick?.invoke()
        }
        forceFullBehavior = prevForce
        behaviorEnabled = prevBehavior
        latest = snapshot()
    }

    /** Health/chemistry only (behavior off) — 90-day tests. Abstract feeding still applies. */
    fun advanceAbstract(simSeconds: Double) {
        val prev = behaviorEnabled
        behaviorEnabled = false
        var left = simSeconds
        while (left > 1e-9) {
            drainCommands()
            val dt = min(60.0, left)
            advanceInternal(dt, dt)
            left -= dt
        }
        behaviorEnabled = prev
        latest = snapshot()
    }

    fun drainEvents(): List<SimEvent> = synchronized(pendingEvents) { pendingEvents.toList().also { pendingEvents.clear() } }

    // ============================================================== core loop
    private fun advanceInternal(simDt: Double, dtB: Double) {
        simTimeSec += simDt
        lifeSec += simDt * lifePace
        while (scheduled.isNotEmpty() && scheduled[0].first <= simTimeSec) apply(scheduled.removeAt(0).second)
        updateLights()
        flow.now = simTimeSec
        flow.pruneGusts(simTimeSec)
        finger?.let { f -> if (simTimeSec - f.lastAt > FINGER_TIMEOUT_S * max(speed, 1.0)) liftFinger() }
        // every step (cheap), so the multiplier is right from the first bout and follows adds and deaths at once
        crowding = com.reeflive.core.ai.PairMatrix.crowding(this); crowdingMult = com.reeflive.core.ai.PairMatrix.crowdingMult(crowding)
        // Gate on the behavior step, not the sim step: stepWall at 60x/240x advances 3-12 sim s per wall tick with a
        // 0.5 s behavior step (SIMULATION §1.2). Gating on simDt parked every animal at its home spot above 20x.
        val useBehavior = behaviorEnabled && dtB <= MAX_BEHAVIOR_DT + 1e-9
        if (useBehavior) behaviorTick(dtB) else abstractPlacement(min(dtB, MAX_BEHAVIOR_DT))
        bags.keepClear(this)
        net.keepClear(this)
        if (useBehavior && !abstractMode) nightPlankton(simDt)
        sand.step(min(simDt, 2.0), sandFlowAt)
        stepRocks(simDt)
        food.step(min(simDt, 1.0), simTimeSec)
        if (food.expiredGrams > 0) water.addDetritusTanGrams(food.expiredGrams * 0.10)
        waste.step(simDt, simTimeSec, water.s.tempC, organisms, water, useBehavior && !abstractMode, fluid?.hobOn ?: true)
        if (useBehavior && !abstractMode) coralCatch(simDt)
        if (useBehavior && !abstractMode) gspContacts(simDt)
        // snails and stars on the front pane wear trails into the glass film (SIMULATION §11.1)
        if (useBehavior && !abstractMode) glassTrails.step(organisms, water.algae.glassMask, rock.widthCm)
        // checked every step (not on the 60 s chemistry tick) so the 02:00 roll lands at 02:00
        nightRolls()
        chemAccum += simDt
        while (chemAccum >= CHEM_DT) {
            chemAccum -= CHEM_DT
            chemTick()
        }
        tickCount++
    }

    private var gspContactAcc = 0.0

    /** Share of a GSP colony a touch next to it closes (the whole of a small frag, little of a big mat). */
    private fun gspShare(k: Coral): Double {
        val r = GspPolyps.matRadius(k)
        return (GspPolyps.WAVE_MAX_CM * GspPolyps.WAVE_MAX_CM / max(r * r, GspPolyps.WAVE_MAX_CM * GspPolyps.WAVE_MAX_CM)).coerceIn(0.2, 1.0)
    }

    /** Walkers and resting animals on a GSP mat close the polyps under them ([GspPolyps.contacts]). */
    private fun gspContacts(dt: Double) {
        gspContactAcc += dt
        if (gspContactAcc < GspPolyps.CONTACT_DT) return
        gspContactAcc = 0.0
        if (corals.any { it.kind == CoralKind.GSP }) GspPolyps.contacts(organisms, corals, rock, simTimeSec)
    }

    /**
     * Coral polyps catch fine suspended food (coral powder, cyclops, plankton, pods) drifting within reach of the
     * colony: one particle per colony per second at most, so a dosed cloud is shared with the filter feeders.
     * Caught food tops up [Coral.fed], which [CoralGrowth.vigor] turns into a small growth boost.
     */
    private fun coralCatch(dt: Double) {
        coralCatchAcc += dt
        if (coralCatchAcc < 1.0) return
        coralCatchAcc = 0.0
        if (corals.isEmpty() || food.particles.none { !it.eaten && (it.kind.coralCatchable || it.kind in StonyCorals.MEATY) }) return
        for (c in corals) {
            if (c.tissue < 0.1 || c.extension < 0.3 || c.kind == CoralKind.MACRO) continue
            // stony LPS also take mysis and brine shrimp, and reach further with their night tentacles out
            val stony = c.kind.stony
            val reach = (2.0 + CoralGrowth.extent(c) + (if (stony) 3.0 * c.tentacles else 0.0)).coerceIn(3.0, 12.0)   // footprint radius + polyp reach, cm
            val f = food.nearest(c.pos, reach) { it.kind.coralCatchable || (stony && it.kind in StonyCorals.MEATY) } ?: continue
            food.consume(f)
            c.fed = min(1.0, c.fed + CORAL_FED_PER_CATCH)
        }
    }
    private var coralCatchAcc = 0.0
    private val glassTrails = GlassTrails()

    /** Demersal zooplankton rises from the rock after dark (S:Marnane02 context); pods drift and expire. */
    private fun nightPlankton(dt: Double) {
        if (perception.light >= 0.3 || nightPlanktonPerHour <= 0) { planktonAcc = 0.0; return }
        planktonAcc += dt * nightPlanktonPerHour / 3600
        while (planktonAcc >= 1) {
            planktonAcc -= 1
            if (food.particles.count { !it.eaten && it.kind == FoodKind.PODS } > 40) continue
            val p = Vec3(rng.range(8.0, rock.lengthCm - 8), rng.range(rock.sandTopCm + 8, rock.waterLevelCm - 10), rng.range(6.0, rock.widthCm - 6))
            if (!rock.inWater(p, 2.0) || rock.sdf.dist(p) < 1.5) continue
            food.spawn(FoodKind.PODS, 0.003, p, simTimeSec)
        }
    }

    /**
     * Once per night (02:00) the rare opportunistic predation rolls of the matrix's perNight rows
     * (06 §2.19, §5.3): a big Sally Lightfoot and sleeping small fish or snails, a banded shrimp and a
     * freshly molted skunk cleaner. Lethal outcomes are sandbox-only; Reality turns them into a
     * [SimEvent.NearMiss] and a stress spike (06 §5.3 reality rule).
     */
    private fun nightRolls() {
        val day = dayIndex
        if (minuteOfDay < 120 || minuteOfDay >= 121 || lastNightRollDay == day) return
        lastNightRollDay = day
        for (r in com.reeflive.core.ai.PairMatrix.rows) {
            if (!r.perNight) continue
            for (a in organisms.toList()) {
                if (!a.alive || a.brainKind.name != r.actor || a.lengthCm < r.minActorCm) continue
                // the crab is an opportunist: only a hungry one tries (vendor statement, 07 gap 5)
                if (a.brainKind == BrainKind.SALLY_CRAB && a.hunger < 0.5) continue
                // live-fish capture by the crab is an opt-in sandbox scenario (research 11 §3)
                val crabOnFish = a.brainKind == BrainKind.SALLY_CRAB && r.targets.any { it != "ASTREA" && it != "NASSARIUS" }
                if (crabOnFish && !crabLiveFishScenario) continue
                // the crab's snail rolls have no source (realism_gaps X7): behind the evidence gate
                if (a.brainKind == BrainKind.SALLY_CRAB && !crabOnFish && !unsourcedPredation) continue
                val eligible = organisms.filter { t -> t !== a && t.alive && r.targets.contains(t.brainKind.name) && nightEligible(r, a, t) }
                if (eligible.isEmpty()) continue
                // one roll per actor and row per night, on the nearest eligible target: the row's pEvent is a
                // per-night probability for the actor, not per prey animal in the tank
                val t = eligible.minBy { it.pos.distanceTo(a.pos) }
                var p = r.pEvent
                if (simTimeSec - t.arrivalTime < 7 * 86400) p *= r.newcomerMult
                if (rng.chance(p)) nightPredation(a, t, r.bout)
            }
        }
    }

    private fun nightEligible(r: com.reeflive.core.ai.PairRow, a: Organism, t: Organism): Boolean {
        if (a.pos.distanceTo(t.pos) > r.triggerRadius + 2) return false
        // a sleeping fish (diurnal species at 02:00), and only one well under the crab's leg span (07 gap 5)
        if (r.filter == "sleeping" && !(t.isFish && t.species.activity.uppercase() != "NOCTURNAL" && t.lengthCm < r.maxPreyRatio * a.lengthCm)) return false
        if (r.moltWindowH > 0) {
            val cross = a.brainKind != t.brainKind
            // banded-on-skunk kills are unsourced: a sandbox toggle, off by default (07 gap 4)
            if (cross && !crossSpeciesShrimpKills) return false
            if (simTimeSec - t.lastMoltAt > r.moltWindowH * 3600) return false
            // conspecifics: a pair is stable, a third animal is the one at risk (S:Wong11)
            if (!cross && organisms.count { it.alive && it.brainKind == t.brainKind } < r.minGroup) return false
        }
        return true
    }

    /** A lethal roll: eaten in Sandbox (predation on), a near miss and a stress spike in Reality. */
    fun nightPredation(predator: Organism, prey: Organism, bout: String) {
        if (predationEnabled) eaten(predator, prey)
        else {
            prey.counters.add(Counters.K.NEAR_MISS, simTimeSec)
            StressModel.addSocial(prey, 0.8)
            prey.startle = max(prey.startle, 0.8)
            emit(SimEvent.NearMiss(predator.id, prey.id, bout, simTimeSec))
        }
    }

    private fun updateLights() {
        lights.channels(simTimeSec, lightCh)
        val i = LightSchedule.intensity(lightCh)
        val p = perception
        p.t = simTimeSec
        if (lastLight < LightSchedule.LIGHTS_ON && i >= LightSchedule.LIGHTS_ON) p.lightsOnAt = simTimeSec
        if (lastLight >= LightSchedule.LIGHTS_ON && i < LightSchedule.LIGHTS_ON) p.lightsOffAt = simTimeSec
        lastLight = i
        p.light = i
        p.night = i < LightSchedule.LIGHTS_ON
    }

    private fun behaviorTick(dt: Double) {
        perception.rebuild()
        if (abstractMode) { abstractPlacement(dt); return }
        if (submarine.active) subSense.tick(this, dt, agentOf)
        // lights-on startle (SIMULATION §4.7)
        for (o in organisms.toList()) {
            if (o !in organisms) continue   // eaten during this tick
            val a = agents[o] ?: continue
            a.ctx.p = perception
            a.ctx.dt = dt
            if (!o.alive) { o.limbRig?.unseat(o); if (!o.netted) deadPhysics(o, dt); continue }
            // a leap at the surface flies the body; the brain waits until it is back in the water (JumpModel)
            if (o.leap != null) { if (JumpModel.step(this, o, dt)) jumpedOut(o); continue }
            a.intent.reset()
            a.brain.preTick(a.ctx)
            if (o.leap != null) continue
            a.brain.utility.tick(a.ctx, a.intent)
            a.steering.apply(a.intent)
            a.brain.postSteer(a.ctx)
            o.attached = a.intent.kinematic || a.intent.crawl
        }
        takeOutJumpers()
        sandWork.step(organisms)
        sandContact.step(organisms, dt, displace = true)
        // the drawn bodies are seated on the sand as this tick left it (a goby's spoil landing on a resting hermit or
        // crab after its seat was solved buried the shell for a tick)
        for (o in organisms) {
            if (!o.alive || o.leap != null) continue
            val a = agents[o] ?: continue
            (o.limbRig as? ShrimpLimbs)?.plant(o, crawlSurface, dt)
            sandContact.preClamp(o)
            rockGuard.apply(o)
            o.seat?.update(o, a.intent.crawl, crawlSurface, dt, if (a.intent.crawl) a.intent.surfaceOffset else -1.0)
            o.limbRig?.contact(o, limbSurface, organisms, dt)
            if (o.seat != null) rockGuard.keepClaims(o)
        }
    }

    private fun abstractPlacement(dt: Double) {
        perception.rebuild()
        for (o in organisms) {
            val a = agents[o] ?: continue
            a.ctx.p = perception; a.ctx.dt = dt
            if (!o.alive) { o.limbRig?.unseat(o); if (!o.netted) deadPhysics(o, dt); continue }
            if (o.leap != null) { if (JumpModel.step(this, o, dt)) jumpedOut(o); continue }
            a.intent.reset()
            // research 08: a fish lethargic or on its side from heat lies on the bottom, not at its rest spot
            if (!com.reeflive.core.ai.HeatResponse.abstractPose(a.ctx, a.intent)) a.brain.abstractPose(a.ctx, a.intent)
            if (!a.intent.kinematic) a.intent.place(a.intent.target ?: o.homePos, o.heading)
            a.intent.kinRate = 3.0
            a.steering.apply(a.intent)
            o.attached = true
            (o.limbRig as? ShrimpLimbs)?.plant(o, crawlSurface, dt)
            sandContact.preClamp(o)
            rockGuard.apply(o)
            o.seat?.update(o, true, crawlSurface, dt, if (a.intent.crawl) a.intent.surfaceOffset else -1.0)
            o.limbRig?.contact(o, limbSurface, organisms, dt)
            if (o.seat != null) rockGuard.keepClaims(o)
        }
        takeOutJumpers()
        sandContact.step(organisms, dt, displace = false)
    }

    private fun deadPhysics(o: Organism, dt: Double) {
        val buoyant = o.species.buoyantWhenDead ?: (o.brainKind in setOf(BrainKind.CLOWN, BrainKind.TANG, BrainKind.CARDINAL, BrainKind.WRASSE, BrainKind.FAIRY_WRASSE, BrainKind.DOTTYBACK, BrainKind.GENERIC_FISH))
        val targetY = if (buoyant) rock.waterLevelCm - 0.8 else rock.sandTopCm + 0.6
        val f = flow.velocityFast(o.pos) * (if (buoyant) 0.6 else 0.05)
        val vy = if (buoyant) 1.2 else -2.0
        var p = o.pos + (f + Vec3(0.0, vy, 0.0)) * dt
        p = if (buoyant) p.withY(min(p.y, targetY)) else p.withY(max(p.y, targetY))
        if (rock.sdf.dist(p) < 0.5) p = rock.sdf.project(p, 0.5)
        o.pos = rock.clampToWater(p, 1.0)
        val belly = if (buoyant) Vec3.Y * -1.0 else Vec3.rotateAbout(Vec3.Y, o.heading, 1.4)
        o.up = o.up.lerp(belly.rejectFrom(o.heading).normalized(belly), 0.02).normalized(Vec3.Y)
        o.vel = Vec3.ZERO; o.amp = 0.0; o.tailHz = 0.0
        o.spline.override = null
        o.spline.update(o.pos, o.heading, dt, 2.0)
    }

    // ============================================================== chemistry / health tick
    private fun chemTick() {
        val w = water
        val dtMin = CHEM_DT / 60
        // slow biology on the game clock (lifePace); everything else in this tick stays on the sim clock
        val lifeDt = CHEM_DT * lifePace
        val lifeMin = dtMin * lifePace
        val light = LightSchedule.intensity(lightCh)
        cachedSinceOn = lights.minutesSinceLightsOn(simTimeSec)
        cachedUntilOff = if (cachedSinceOn != null) lights.minutesUntilLightsOff(simTimeSec) else null
        // drivers
        var bio = 0.0
        for (o in organisms) if (o.alive) bio += Math.pow(o.massG, 0.8) * HealthModel.metabolicMult(w.s.tempC) * (if (o.sleeping) 0.7 else 1.0)
        w.bioMassIndex = bio
        var fecalBio = 0.0
        for (o in organisms) if (o.alive && o.isFish && !o.sleeping) fecalBio += Math.pow(o.massG, 0.8) * HealthModel.metabolicMult(w.s.tempC)
        w.fecalBioIndex = fecalBio
        repro.step(lifeDt)
        brood.step(lifeDt)
        SexRoles.step(organisms, lifeDt)   // protogynous / bidirectional sex roles (life_stages.md)
        shrimpBreeding.step(lifeMin)
        w.lightIntensity = light
        w.coralDemandDkhPerDay = 0.01 + corals.sumOf { it.species.coralKind.demandDkhPerDay * it.tissue * CoralGrowth.mass(it) }
        w.coralBiomass = 0.3 + corals.sumOf { it.species.coralKind.biomass * it.tissue * (1 - it.bleach * 0.7) * CoralGrowth.mass(it) }
        w.alleloSourcePerHour = CoralGrowth.alleloSource(corals)
        if (!behaviorEnabled || abstractMode) abstractGrazing(dtMin)
        w.grazingPerHour = grazeAccum * 3600 / CHEM_DT
        grazeAccum = 0.0
        w.algae.grazers = algaeGrazers()
        microDrivers()
        w.bacteria.bioturbation = sandStirrers()
        w.step(CHEM_DT)
        waste.collect(organisms, w)
        applyRamps()
        cycle?.update(w.s, simTimeSec)
        // health
        val night = light < LightSchedule.LIGHTS_ON
        val fishCount = organisms.count { it.alive && it.isFish }
        expireTreatments()
        val infectedAll = HashMap<Disease, Double>()
        for (x in organisms) if (x.alive) x.disease?.let { infectedAll[it] = (infectedAll[it] ?: 0.0) + x.diseaseStage }
        for (o in organisms) {
            val ichOthers = organisms.filter { it !== o && it.alive && it.disease == Disease.ICH }.sumOf { it.diseaseStage }
            val infected = if (infectedAll.isEmpty()) emptyMap() else
                infectedAll.mapValues { (d, v) -> if (o.disease == d) v - o.diseaseStage else v }
            val rival = when (o.brainKind) {
                BrainKind.LAWNMOWER_BLENNY -> organisms.any { it !== o && it.alive && (it.brainKind == BrainKind.PEEKER_BLENNY || it.brainKind == BrainKind.LAWNMOWER_BLENNY) }
                BrainKind.PEEKER_BLENNY -> organisms.any { it !== o && it.alive && (it.brainKind == BrainKind.LAWNMOWER_BLENNY || it.brainKind == BrainKind.PEEKER_BLENNY) }
                BrainKind.DOTTYBACK, BrainKind.TANG, BrainKind.EEL -> organisms.any { it !== o && it.alive && it.brainKind == o.brainKind }
                BrainKind.CHROMIS -> chromisUnderdog(o)
                else -> false
            }
            val blennyExcess = Compatibility.isBlenny(o.brainKind) && w.s.volumeL < Compatibility.BLENNY_FREE_VOLUME_L &&
                organisms.any { it !== o && it.alive && Compatibility.isBlenny(it.brainKind) && (it.arrivalTime < o.arrivalTime || (it.arrivalTime == o.arrivalTime && organisms.indexOf(it) < organisms.indexOf(o))) }
            val ctx = HealthContext(simTimeSec, w.s.volumeL, ichOthers, fishCount > 1, rival, night, flowStrength, light, blennyExcess,
                infected, treatments.modsFor(o, w.s, simTimeSec), treatments.hypo && o.isFish, w.bacteria.pathogenPressure, healthGrace)
            HealthModel.step(o, if (fluid != null && o.alive) localWater(o.pos) else w.s, ctx, dtMin, pendingEventsSink)
            if (o.alive) LifeCycle.step(o, CHEM_DT, w.s.volumeL, simTimeSec, pendingEventsSink)
            if (o.alive) growth(o, lifeMin)
        }
        coralGrowth(lifeMin)
        if (pests.peppermint > 0 || pests.berghia > 0 || corals.any { it.kind == CoralKind.AIPTASIA }) AiptasiaModel.step(this, dtMin, light < LightSchedule.LIGHTS_ON, lifeMin)
        for (c in corals) {
            c.flowSpeed = flow.velocityFast(c.pos).length()
            c.fed *= exp(-dtMin / CORAL_FED_DECAY_MIN)
            val tissueBefore = c.tissue
            // My tank: each colony sees the light where it sits (deeper and under ledges is dimmer; CoralSpots)
            val here = if (bareScape) localLight(c) else 1.0
            CoralSim.step(c, w.s, light * w.clarity * here, cachedSinceOn, cachedUntilOff, dtMin, simTimeSec, pendingEventsSink, flowStrength, w.bacteria.coralPathogenStress)
            // Dying coral tissue decomposes (die-off cascade, research 05 §11)
            if (c.tissue < tissueBefore) w.addDetritusTanGrams((tissueBefore - c.tissue) * c.kind.biomass * CORAL_TAN_PER_COLONY)
            c.swayPhase = (c.swayPhase + dtMin * 60 * 0.3) % (2 * Math.PI)
        }
        crustaceanMolts()
        dinoToxin(dtMin)
        flatwormToxin()
        // co-residents grow familiar at 0.15/day even while the behaviour layer is off (06 §3.3), and fear, grudges and
        // intrusion memory fade on their own half-lives (Social.perceive only runs on behaviour ticks)
        if (!behaviorEnabled || abstractMode) {
            val gain = com.reeflive.core.ai.Social.FAMILIARITY_PER_DAY * CHEM_DT / 86400
            for (o in organisms) if (o.alive) {
                for (r in o.relations.values) r.familiarity = min(1.0, r.familiarity + gain)
                com.reeflive.core.ai.Social.fadeMemories(o, CHEM_DT)
            }
        }
        asterinaPopulation()
        invertEcology.step(dtMin)
        anemones.step(dtMin, lifeMin)
        corpses(dtMin)
        autoFeed()
        maintenance()
        if (w.s.algaeHair > 0.35 && simTimeSec - lastAlgaeBloomReport > 86400) {
            lastAlgaeBloomReport = simTimeSec
            pendingEventsSink += SimEvent.AlgaeBloom(w.s.algaeHair, simTimeSec)
        }
        moltShells.removeAll { simTimeSec - it.second > 24 * 3600 }
        // piles slump and spread over ~4 days (faster in strong flow); abstract mode digs at the day rate
        val slump = Math.exp(-dtMin / (4 * 1440.0) * (0.5 + 0.5 * flowStrength))
        sandPiles.forEach { it.volume *= slump }
        sandPiles.removeAll { it.volume < 0.2 }
        if ((!behaviorEnabled || abstractMode) && !night) for (o in organisms) if (o.alive && o.brainKind == BrainKind.ENGINEER_GOBY) {
            val b = agents[o]?.brain as? com.reeflive.core.ai.GobyBrain ?: continue
            if (rng.chance(dtMin / 3.0)) {
                // same draws as before (the sim stream is unchanged): spoil distance and offsets
                val k = rng.range(4.0, 9.0); val ox = rng.signed() * 4; val oz = rng.signed() * 2
                b.abstractDig(k, ox, oz)
            }
        }
        if ((!behaviorEnabled || abstractMode) && !night) for (o in organisms) if (o.alive && o.brainKind == BrainKind.DIAMOND_GOBY) {
            (agents[o]?.brain as? com.reeflive.core.ai.DiamondGobyBrain)?.abstractSift(dtMin)
        }
        if ((!behaviorEnabled || abstractMode) && !night) for (o in organisms) if (o.alive && o.brainKind == BrainKind.LEOPARD_WRASSE) {
            (agents[o]?.brain as? com.reeflive.core.ai.LeopardWrasseBrain)?.abstractPick(dtMin)
        }
        sandTick(dtMin)
        flushEvents()
    }

    /** Sand sifters and burrowers alive (bioturbation keeps the sand bed's top layer oxic; research 18 §3.1). */
    private fun sandStirrers(): Double {
        var n = 0.0
        for (o in organisms) if (o.alive) when (o.brainKind) {
            BrainKind.DIAMOND_GOBY, BrainKind.ENGINEER_GOBY -> n += 1.0
            BrainKind.NASSARIUS -> n += 0.25
            // a sand-sleeping wrasse turns over a crater of sand twice a day (research video fish_leopard_wrasse)
            BrainKind.LEOPARD_WRASSE -> n += 0.1
            else -> {}
        }
        return n
    }

    /** Doses in the last day (sim s, param, amount): the care button keeps inside a day's safe swing. */
    private val doseLog = ArrayList<Triple<Double, String, Double>>()
    /** Last care-button feeding (sim s). */
    var reefCareAt = -1e12

    fun dosedLast24h(id: String): Double {
        doseLog.removeAll { simTimeSec - it.first > 86400 }
        var n = 0.0
        for (d in doseLog) if (d.second == id) n += d.third
        return n
    }

    /** Tank Health's care button ([CoralCare]): doses and feeds what the corals and inverts need now. */
    private fun reefCare() {
        var fed = 0.0; var n = 0
        for (c in corals) if (c.tissue > 0.1 && c.kind != CoralKind.MACRO && c.kind != CoralKind.AIPTASIA) { fed += c.fed; n++ }
        val live = organisms.filter { it.alive }
        val anemones = live.filter { it.brainKind == BrainKind.ANEMONE }
        val dusters = live.filter { it.brainKind == BrainKind.FEATHER_DUSTER }
        val plan = CoralCare.plan(
            water.s, if (n == 0) Double.NaN else fed / n, anemones.size, dusters.size, live.count { CoralCare.isCrustacean(it.brainKind) },
            (simTimeSec - reefCareAt) / 3600, ::dosedLast24h,
        )
        for (item in plan.items) when (item.kind) {
            CoralCare.Kind.MAGNESIUM -> apply(Command.Dose("mg", item.amount))
            CoralCare.Kind.CALCIUM -> apply(Command.Dose("ca", item.amount))
            CoralCare.Kind.ALKALINITY -> apply(Command.Dose("alk", item.amount))
            CoralCare.Kind.IODINE -> apply(Command.Dose("i", item.amount))
            CoralCare.Kind.CORAL_FOOD -> apply(Command.Feed(FoodKind.CORAL_FOOD, FoodKind.CORAL_FOOD.typicalDoseGrams))
            CoralCare.Kind.PHYTO -> { reefCareAt = simTimeSec; for (d in dusters) apply(Command.Feed(FoodKind.PLANKTON, FoodKind.PLANKTON.typicalDoseGrams, d.pos, target = true)) }
            CoralCare.Kind.ANEMONE_FOOD -> { reefCareAt = simTimeSec; for (a in anemones) apply(Command.Feed(FoodKind.MYSIS, FoodKind.MYSIS.typicalDoseGrams * 0.3, a.pos, target = true)) }
        }
    }

    /** Colony growth, Kenya branch drops and neighbour competition (CoralGrowth; coverage gaps C-1, C-2). */
    private fun coralGrowth(dtMin: Double) {
        if (corals.isEmpty()) return
        CoralGrowth.compete(corals, water.s, water.carbonEfficiency)
        val born = ArrayList<Coral>()
        val kenyas = corals.count { it.kind == CoralKind.KENYA && it.tissue > 0.05 }
        for (i in corals.indices) {
            val c = corals[i]
            if (c.tissue <= CoralHealth.DEAD_TISSUE) continue
            CoralGrowth.step(c, water.s, rock, growthRng, dtMin, kenyas + born.size) { "${c.id}~${nextFragId()}" }?.let { born += it }
            StonyCorals.developSweepers(c, corals, dtMin)
            // zoas and clove bud along runners, mushrooms split, leathers bud (coral_health §3)
            if (CoralHealth.profile(c.species).spreadEveryDays > 0 && c.size >= CoralHealth.profile(c.species).spreadMinSize) {
                val own = corals.count { it.species.id == c.species.id && it.tissue > CoralHealth.DEAD_TISSUE } + born.count { it.species.id == c.species.id }
                if (corals.size + born.size >= Stocking.MAX_CORALS) continue
                CoralHealth.maybeSpread(c, CoralGrowth.vigor(c, water.s), rock, spreadRng, dtMin, own, corals) { "${c.id}~${nextFragId()}" }?.let { born += it }
            }
        }
        for (f in born) {
            corals += f
            pendingEventsSink += SimEvent.CoralSpawned(f.id.substringBefore('~'), f.id, simTimeSec)
        }
        coralDeaths()
    }

    /** A colony whose tissue is gone is reported once and cleared away [CoralHealth.CLEAR_DAYS] later (aiptasia stay for their own model). */
    private fun coralDeaths() {
        var clear = false
        for (c in corals) {
            if (c.kind == CoralKind.AIPTASIA || c.tissue > CoralHealth.DEAD_TISSUE) continue
            if (c.diedAt.isNaN()) {
                c.diedAt = simTimeSec
                val cause = if (c.need >= 0) CoralNeed.entries[c.need].words else "it was too stressed for too long"
                pendingEventsSink += SimEvent.CoralDied(c.id, c.nickname.ifBlank { c.species.common }, cause, simTimeSec)
            } else if (simTimeSec - c.diedAt > CoralHealth.CLEAR_DAYS * 86400) clear = true
        }
        if (clear) corals.removeAll { it.kind != CoralKind.AIPTASIA && !it.diedAt.isNaN() && simTimeSec - it.diedAt > CoralHealth.CLEAR_DAYS * 86400 }
    }

    /** Collects events from the health/coral models; flushed to the log once per tick. */
    private val pendingEventsSink = ArrayList<SimEvent>()

    private fun flushEvents() {
        if (pendingEventsSink.isEmpty()) return
        synchronized(pendingEvents) { pendingEvents.addAll(pendingEventsSink) }
        pendingEventsSink.forEach { eventLog.addLast(it); if (eventLog.size > 200) eventLog.removeFirst() }
        pendingEventsSink.clear()
    }

    fun emit(e: SimEvent) { pendingEventsSink += e; flushEvents() }

    private fun growth(o: Organism, dtMin: Double) {
        val g = o.species.growthCmPerYear
        if (g <= 0 || o.lengthCm >= o.species.maxLengthCm) return
        val stunt = if (o.brainKind == BrainKind.TANG && water.s.volumeL < LifeCycle.minVolumeL(o) && o.lengthCm > 0.6 * o.species.typicalAdultCm) 0.3 else 1.0
        // My tank's game pace: youngsters shoot up to near adult size (where they can breed) in a week or two of play
        val young = if (lifePace > 1.0 && o.lengthCm < LifePace.YOUNG_UNTIL * o.species.typicalAdultCm) LifePace.YOUNG_BOOST else 1.0
        o.lengthCm += g / (365 * 1440) * dtMin * o.health * stunt * young
    }

    /**
     * Grazing and passive feeding while the behavior layer is off (abstract mode / health-only runs).
     * Grazers eat at up to 1.6× their hunger rise rate scaled by food availability: hair algae
     * (`hair/(hair+0.02)`) or the always-present biofilm for snails, asterina and hermits.
     */
    private fun abstractGrazing(dtMin: Double) {
        val hair = water.s.algaeHair
        val aHair = hair / (hair + 0.02)
        val day = LightSchedule.intensity(lightCh) > 0.1
        for (o in organisms) {
            if (!o.alive || !o.species.grazer) continue
            val diurnal = !o.species.activity.equals("NOCTURNAL", true)
            if (diurnal && !day && o.brainKind != BrainKind.ASTREA) continue
            if (o.brainKind == BrainKind.SALLY_CRAB && day) continue   // wedged in its crevice by day
            val film = when (o.brainKind) {
                BrainKind.ASTREA, BrainKind.ASTERINA, BrainKind.HERMIT -> 0.7
                BrainKind.SALLY_CRAB -> 0.5
                BrainKind.LAWNMOWER_BLENNY -> 0.4
                else -> 0.15
            }
            val avail = max(aHair, film)
            val rise = HealthModel.hungerRatePerMin(o, water.s.tempC)
            o.hunger = max(0.0, o.hunger - 1.6 * rise * avail * dtMin * (if (diurnal) 1.5 else 1.0))
            o.nutritionGreens = 1.0
            grazeAccum += grazeRemovalPerMin(o) * aHair * dtMin
        }
        // Cleaners earn a little from clients even when nobody is watching.
        val clients = organisms.count { it.alive && it.isFish && it.brainKind != BrainKind.WRASSE }
        for (o in organisms) {
            if (!o.alive) continue
            val rise = HealthModel.hungerRatePerMin(o, water.s.tempC)
            when (o.brainKind) {
                BrainKind.WRASSE, BrainKind.CLEANER_SHRIMP ->
                    if (day && clients > 0) o.hunger = max(0.0, o.hunger - rise * (0.15 + 0.05 * clients).coerceAtMost(0.6) * 1.5 * dtMin)
                // filter feeders and detritivores live partly on detritus / bacterioplankton
                BrainKind.FEATHER_DUSTER, BrainKind.BRITTLE_STAR, BrainKind.NASSARIUS ->
                    o.hunger = max(0.0, o.hunger - rise * 0.8 * (water.s.detritus / 0.2).coerceIn(0.3, 1.2) * dtMin)
                // the flame scallop lives on phytoplankton only (research 42 §2.4)
                BrainKind.FILE_CLAM -> com.reeflive.core.ai.FileClamBrain.abstractFeed(o, this, dtMin)
                else -> {}
            }
        }
    }

    /** Hair-algae units removed per minute of continuous grazing (tuned for a healthy-tank equilibrium hair ≈ 0.03–0.05). */
    fun grazeRemovalPerMin(o: Organism): Double = when (o.brainKind) {
        BrainKind.TANG -> 2e-6; BrainKind.LAWNMOWER_BLENNY -> 3e-6; BrainKind.PEEKER_BLENNY -> 0.5e-6
        BrainKind.ASTREA -> 1e-6; BrainKind.HERMIT -> 0.5e-6; BrainKind.SALLY_CRAB -> 1.2e-6; BrainKind.ASTERINA -> 0.2e-6
        else -> 0.5e-6
    } * (o.lengthCm / o.species.typicalAdultCm.coerceAtLeast(0.5)).coerceIn(0.3, 2.0)

    fun addGrazing(amount: Double) { grazeAccum += amount }

    /** Grazers present for the algae model (research 09 A4); size scales intake with body area. Dying animals stop grazing. */
    private fun algaeGrazers(): List<AlgaeGrazer> {
        val out = ArrayList<AlgaeGrazer>()
        for (o in organisms) {
            if (!o.alive) continue
            val t = GrazerType.of(o) ?: continue
            val size = (o.lengthCm / t.refCm).let { it * it }.coerceIn(0.3, 2.5)
            out += AlgaeGrazer(t, size * o.health.coerceIn(0.0, 1.0))
        }
        return out
    }

    /**
     * Pod predators, plankton-catching polyps and display macroalgae for the microfauna model (research 10 §2.6, §3.4).
     * Polyps per colony are estimates (a GSP mat ~500, research 10 §6.3 #9); retracted polyps catch nothing.
     */
    private fun microDrivers() {
        val m = water.micro
        val preds = ArrayList<MicroPredator>()
        for (o in organisms) {
            if (!o.alive) continue
            val t = PodPredator.of(o) ?: continue
            preds += MicroPredator(t, o.health.coerceIn(0.0, 1.0))
        }
        m.predators = preds
        var small = 0.0; var zoa = 0.0; var shroom = 0.0; var macro = 0.0
        for (c in corals) {
            val open = c.tissue * c.extension.coerceIn(0.1, 1.0)
            when (c.kind) {
                CoralKind.GSP -> small += 500 * open
                CoralKind.CLOVE -> small += 150 * open
                CoralKind.KENYA -> small += 100 * open
                CoralKind.ORGAN_PIPE -> small += 200 * open
                CoralKind.ZOA -> zoa += 50 * open
                CoralKind.MUSHROOM -> shroom += 5 * open
                // leather polyps are small particle feeders (est.; tee_tank_photos.md)
                CoralKind.LEATHER -> small += 80 * open
                // Euphyllia and the meaty LPS catch pods and plankton with their tentacles, most at night (est.)
                CoralKind.EUPHYLLIA, CoralKind.LPS -> small += 60 * (open + c.tentacles)
                CoralKind.MACRO -> macro += 0.5 * c.tissue
                else -> {}
            }
        }
        m.smallPolyps = small; m.zoaPolyps = zoa; m.mushrooms = shroom; m.displayMacroL = macro
        // pod habitat in the sump: the refugium chaeto biomass index read as litres of loose chaeto
        m.refugiumL = water.s.chaeto
    }

    /**
     * A mass flatworm kill releases toxin (research 10 §3.3, §6.3 #10): fish carry a steady social-stress input
     * (twice the calm baseline) for 48 h.
     */
    private fun flatwormToxin() {
        if (water.micro.toxinHours <= 0) return
        for (o in organisms) if (o.alive && o.isFish && o.stressState.socialInput < TOXIN_SOCIAL) StressModel.addSocial(o, TOXIN_SOCIAL - o.stressState.socialInput)
    }

    /**
     * Dinoflagellate toxins (research 09 A3): while dino cover on rock exceeds 30 %, snails and hermits take a
     * 2 %/day mortality hazard.
     */
    private fun dinoToxin(dtMin: Double) {
        val a = water.algae
        val rock = (a.cover(AlgaeGroup.DINOS, AlgaeSurface.ROCK_TOP) * a.area[AlgaeSurface.ROCK_TOP.ordinal] +
            a.cover(AlgaeGroup.DINOS, AlgaeSurface.ROCK_SHADE) * a.area[AlgaeSurface.ROCK_SHADE.ordinal]) /
            (a.area[AlgaeSurface.ROCK_TOP.ordinal] + a.area[AlgaeSurface.ROCK_SHADE.ordinal])
        if (rock <= 0.3) return
        for (o in organisms.toList()) {
            if (!o.alive) continue
            if (o.brainKind != BrainKind.ASTREA && o.brainKind != BrainKind.HERMIT && o.brainKind != BrainKind.NASSARIUS &&
                GrazerType.of(o) != GrazerType.TROCHUS) continue
            if (rng.chance(0.02 * dtMin / 1440.0)) HealthModel.kill(o, "dinoflagellate toxin", simTimeSec, pendingEventsSink)
        }
    }

    /** Molts come every few game weeks ([lifeDays]); the soft-shell days after one stay on the sim clock. */
    private fun crustaceanMolts() {
        for (o in organisms) {
            if (!o.alive) continue
            val (lo, hi, dur) = when (o.brainKind) {
                // research 12: adult intermolt ≈ 25–26 d at 26 °C (S); Q10 = 2 temperature scaling (D)
                BrainKind.BANDED_SHRIMP -> ShrimpBreeding.stenopusIntermoltDays(water.s.tempC).let { Triple(0.92 * it, 1.08 * it, 36.0) }
                BrainKind.CLEANER_SHRIMP -> Triple(21.0, 42.0, 36.0)
                // research 42 §1.4: small Lysmata molt every 1-3 weeks and hide about a day after [E]
                BrainKind.PEPPERMINT_SHRIMP -> Triple(10.0, 21.0, 24.0)
                BrainKind.SALLY_CRAB -> Triple(28.0, 56.0, 24.0)
                else -> continue
            }
            if (o.nextMoltAt == 0.0) o.nextMoltAt = simTimeSec + lifeDays(rng.range(lo, hi))
            if (!o.molting && simTimeSec >= o.nextMoltAt) {
                o.molting = true; o.moltUntil = simTimeSec + dur * 3600; o.lastMoltAt = simTimeSec
                val shellPos = rock.clampToWater(o.pos.withY(rock.sandTopCm + 0.3) + Vec3(rng.signed() * 4, 0.0, 4.0), 2.0).withY(rock.sandTopCm + 0.3)
                moltShells += shellPos to simTimeSec
                emit(SimEvent.Molted(o.id, simTimeSec))
            }
            if (o.molting && simTimeSec >= o.moltUntil) {
                o.molting = false
                // failed molts are more likely with low Ca/Mg/iodine (research 04 §4C)
                val bad = (water.s.ca < 360 || water.s.mg < 1150 || water.s.i < 0.02)
                if (bad && rng.chance(0.25)) HealthModel.kill(o, "failed molt", simTimeSec, pendingEventsSink)
                o.nextMoltAt = simTimeSec + lifeDays(rng.range(lo, hi))
            }
        }
    }

    private fun asterinaPopulation() {
        val stars = organisms.filter { it.alive && it.brainKind == BrainKind.ASTERINA }
        if (stars.isEmpty() || stars.size >= ASTERINA_CAP) return
        // fissiparity: each healthy star splits on average every ~30 days
        for (s in stars) if (s.health > 0.8 && s.hunger < 0.6 && rng.chance(lifePace / (30 * 1440))) {
            val child = addFromSeed(LivestockSeed(s.species.id, s.nickname.substringBefore(" #") + " #" + (stars.size + 1), s.lengthCm * 0.6))
            child.placeAt(s.pos + Vec3(rng.signed() * 2, 0.0, rng.signed() * 2), s.heading)
            break
        }
    }

    private fun corpses(dtMin: Double) {
        val it = organisms.iterator()
        val removed = ArrayList<Organism>()
        while (it.hasNext()) {
            val o = it.next()
            val since = o.deadSince ?: continue
            val age = simTimeSec - since
            val dt = carcassRelease(o, age, dtMin)
            if (dt > 0) { o.carcassTanReleasedG += dt; water.addDetritusTanGrams(dt) }
            if (age > 48 * 3600) { removed += o; it.remove() }
        }
        removed.forEach { agents.remove(it) }
    }

    /**
     * g TAN-equivalent a carcass [age] s dead leaches in the next [dtMin]. The pool is the body's own nitrogen
     * (wet mass × [CARCASS_N_FRACTION], as TAN), so a kill can never release more N than the fish held; after a
     * [CARCASS_LAG_H] autolysis lag it leaches first-order at [CARCASS_LEACH_PER_H] (faster when warm) until the keeper
     * or the clean-up crew removes the body at 48 h, taking the rest with it. The grams go in as detritus, spread over
     * the whole system volume by [WaterSim.addDetritusTanGrams].
     */
    private fun carcassRelease(o: Organism, ageSec: Double, dtMin: Double): Double {
        val hours = ageSec / 3600
        if (hours < CARCASS_LAG_H) return 0.0
        val pool = carcassTanPoolG(o) - o.carcassTanReleasedG
        if (pool <= 0) return 0.0
        val k = CARCASS_LEACH_PER_H * HealthModel.metabolicMult(water.s.tempC)
        return pool * (1 - exp(-k * dtMin / 60))
    }

    private val lastAutoFeedDay = HashMap<Int, Int>()

    private fun autoFeed() {
        val m = minuteOfDay
        for ((i, f) in config.autoFeed.withIndex()) {
            if (m < f.minuteOfDay || m >= f.minuteOfDay + 2) continue
            if (lastAutoFeedDay[i] == dayIndex) continue
            if (dayIndex % max(1, f.everyDays) != 0) continue
            lastAutoFeedDay[i] = dayIndex
            if (f.anchor == "DUSTER_TUBE" && organisms.none { it.alive && it.brainKind == BrainKind.FEATHER_DUSTER }) continue
            if (f.anchor == "CAVE_EEL" && organisms.none { it.alive && it.brainKind == BrainKind.EEL }) continue
            feed(f.kind, f.grams, feedPoint(f.anchor))
        }
    }

    private var lastWaterChangeDay = -1

    /** Routine water change at 10:00 every [TankConfig.waterChangeEveryDays] days. */
    private fun maintenance() {
        val every = config.waterChangeEveryDays
        if (every <= 0 || config.waterChangeFraction <= 0) return
        if (dayIndex % every != every - 1 || lastWaterChangeDay == dayIndex) return
        if (minuteOfDay < 600 || minuteOfDay >= 602) return
        lastWaterChangeDay = dayIndex
        water.waterChange(config.waterChangeFraction)
        water.algae.wipeGlass()   // the glass gets wiped on water-change day (research 09 A2: GSA wiped every 3-7 d)
    }

    /**
     * Where food aimed at [aim] goes in: floating foods (flake, floating pellets) land on the surface right above it;
     * everything else is let go a little above it, the way a keeper target-feeds with a turkey baster or feeding tube,
     * so it drifts and sinks onto the fish, coral or spot that was picked. Nori still goes on its clip.
     */
    fun targetRelease(kind: FoodKind, aim: Vec3): Vec3 {
        val p = rock.clampToWater(aim, 1.0)
        return if (kind.floats) p.withY(rock.waterLevelCm - 0.3)
        else p.withY((p.y + TARGET_LIFT_CM).coerceAtMost(rock.waterLevelCm - 2.5))
    }

    fun feedPoint(anchor: String?): Vec3 = when (anchor) {
        // Tee feeds by hand at the back middle, right beside the return outlet (video T97 0:01-0:03): the return jet then
        // sweeps the pinch forward and sideways before it sinks
        null -> rock.anchors.pos("HAND_FEED", shape.map(HAND_FEED)).withY(rock.waterLevelCm - 0.3)
        "CAVE_EEL" -> rock.anchors.pos("CAVE_EEL", Vec3(62.0, 3.8, 32.5)) + Vec3(-2.0, 7.0, 5.0)
        "DUSTER_TUBE" -> rock.anchors.pos("DUSTER_TUBE", Vec3(86.0, 4.5, 27.3)) + Vec3(2.0, 12.0, 1.0)
        else -> rock.anchors.pos(anchor, Vec3(41.0, rock.waterLevelCm - 6, 26.0))
    }

    /**
     * Sand bed upkeep once per chemistry tick (SIMULATION §16): slumping and flow smoothing, freshness fading, detritus
     * settling from the column, and fines that stayed suspended reaching the filter sock or skimmer.
     */
    private fun sandTick(dtMin: Double) {
        // the bed's slow upkeep runs in batches: disturbed sand already pours in real time (SandBed.step)
        sandRelaxPendingMin += dtMin
        if (sandRelaxPendingMin >= SAND_RELAX_EVERY_MIN) { sand.relax(sandRelaxPendingMin, flowStrength, water.s.detritus); sandRelaxPendingMin = 0.0 }
        val up = sand.resuspendedPending
        if (up > 0) {
            water.captureResuspended((up * RESUSPEND_CAPTURE_PER_LOAD).coerceAtMost(RESUSPEND_CAPTURE_MAX_PER_MIN * dtMin))
            sand.resuspendedPending = 0.0
        }
    }

    // ============================================================== commands
    private fun drainCommands() {
        while (true) {
            val c = commands.poll() ?: break
            apply(c)
        }
    }

    private fun apply(c: Command) {
        when (c) {
            is Command.SetParam -> { ramps.remove(c.paramId.lowercase()); setParam(c.paramId, c.value) }
            is Command.RampParam -> {
                val from = paramValue(c.paramId) ?: c.target
                ramps[c.paramId.lowercase()] = ParamRamp(c.paramId, from, c.target, simTimeSec, max(0.0, c.overMin) * 60, c.hold)
                if (c.overMin <= 0) setParam(c.paramId, c.target)
            }
            is Command.ReleaseParam -> ramps.remove(c.paramId.lowercase())
            is Command.Schedule -> {
                if (c.atSimSec <= simTimeSec) apply(c.command)
                else { val i = scheduled.indexOfFirst { it.first > c.atSimSec }.let { if (it < 0) scheduled.size else it }; scheduled.add(i, c.atSimSec to c.command) }
            }
            is Command.PowerheadFail -> {
                apply(Command.Schedule(c.atSimSec, Command.Powerheads(false)))
                apply(Command.Schedule(c.atSimSec + c.durationSec, Command.Powerheads(true)))
            }
            is Command.RearrangeRock -> { rearrangeRock(); AiptasiaModel.rockMoved(this) }
            is Command.SetAcclimation -> for (o in organisms) if (o.alive) {
                StressModel.acclimate(o, water.s, lightIntensity, flowStrength, c.paramId)
                if (c.paramId == null || c.paramId.equals("temp", true)) HeatModel.acclimate(o, water.s.tempC)
            }
            is Command.Photoperiod -> lights.photoperiodHours = c.hours.coerceIn(4.0, 16.0)
            is Command.AddSpareShells -> invertEcology.addSpareShells(c.count, c.sizeCm)
            is Command.Maintain -> {
                careLog.maintained[c.task.ordinal]++
                water.maintain(c.task)
                if (c.task == MaintenanceTask.SIPHON_SAND) { waste.siphon(); sand.siphon(WaterSim.SIPHON_SHARE) }
            }
            is Command.AddAiptasia -> {
                val near = c.nearCoralId?.let { id -> corals.firstOrNull { it.id == id } }
                if (near != null) repeat(c.count.coerceIn(0, 3)) { AiptasiaModel.hitchhike(this, near.pos) }
                else AiptasiaModel.seed(this, c.count.coerceIn(0, AiptasiaModel.CAP))
            }
            is Command.TreatAiptasia -> AiptasiaModel.treat(this, c.treatment, c.polypId)
            is Command.RemoveAiptasia -> AiptasiaModel.remove(this, c.count, c.chemical)
            is Command.StartTreatment -> startTreatment(c.kind, c.days ?: c.kind.defaultDays, c.fishIds?.toSet())
            is Command.StopTreatment -> treatments.active[c.kind]?.let { endTreatment(it) }
            is Command.Infect -> (organisms.firstOrNull { it.id == c.id } ?: organisms.filter { it.alive && it.isFish }.let { l -> if (l.isEmpty()) null else l[growthRng.nextInt(l.size)] })
                ?.takeIf { it.alive && it.isFish }?.let { o ->
                    o.disease = c.disease; o.diseaseStage = c.stage.coerceIn(0.01, 1.0)
                    emit(SimEvent.DiseaseOnset(o.id, c.disease, simTimeSec))
                }
            is Command.AddPestControl -> when (c.controller) {
                // real peppermint shrimp now (research 42): they go straight onto the rock and hunt the polyps themselves
                PestController.PEPPERMINT_SHRIMP -> if (c.count > 0) {
                    species.ensure(AiptasiaModel.PEPPERMINT_ID)
                    repeat(c.count) { addFromSeed(LivestockSeed(AiptasiaModel.PEPPERMINT_ID, "Peppermint", 0.0)) }
                } else pests.peppermint = max(0, pests.peppermint + c.count)
                PestController.BERGHIA -> pests.berghia = max(0.0, pests.berghia + c.count)
            }
            is Command.Feed -> { careLog.fed(c.kind); feed(c.kind, c.grams, c.at?.let { if (c.target) targetRelease(c.kind, it) else it } ?: feedPoint(null)); touchMemory.fed() }
            is Command.FeedHungry -> {
                val drops = HungryFeed.plan(organisms, rock.waterLevelCm, rock.lengthCm, rock.widthCm)
                for (d in drops) { careLog.fed(d.kind); feed(d.kind, d.grams, d.at) }
                if (drops.isNotEmpty()) touchMemory.fed()
            }
            is Command.WaterChange -> { if (c.fraction >= CareLog.MIN_CHANGE) careLog.waterChanges++; water.waterChange(c.fraction, c.mix) }
            is Command.ReefCare -> reefCare()
            is Command.Dose -> {
                doseLog += Triple(simTimeSec, c.paramId.lowercase(), c.delta)
                water.dose(c.paramId, c.delta)
                // the dose enters at the HOB return and spreads through the flow before it is well mixed
                fluid?.let { f -> doseTracerParam = c.paramId; f.injectDose(rock.anchors.pos("HOB_OUTFLOW", Vec3(73.0, rock.waterLevelCm - 1, 3.0)) + Vec3(0.0, -2.0, 4.0), c.delta, 1.0) }
            }
            is Command.Lights -> { lights.forcedOn = c.on; c.overrideIntensity?.let { lights.intensityOverride = it } }
            is Command.Heater -> { water.heaterWorking = c.working; water.heaterStuckOn = c.stuckOn; c.setC?.let { water.heaterSetC = it } }
            is Command.Powerheads -> setFlow(c.on)
            is Command.AddOrganism -> addFromSeed(LivestockSeed(c.speciesId, c.nickname, c.sizeCm, c.role, ageYears = c.ageYears)).let { o ->
                // a newcomer: nobody knows it yet (06 §3.3 priority effects)
                o.arrivalTime = simTimeSec; o.clock = simTimeSec
                // new arrivals enter at the surface like a release from the acclimation cup
                if (o.isFish) o.placeAt(Vec3(rock.lengthCm / 2 + rng.signed() * 10, rock.waterLevelCm - 4, rock.widthCm * 0.6), Vec3(rng.signed(), 0.0, rng.signed()))
            }
            is Command.Breed -> {
                // "ACT" for the clowns, "ACT:<spec key>" for the other breeding species (Brood.id)
                val name = c.act.substringBefore(':')
                val spec = BroodSpecs.byKey(c.act.substringAfter(':', BroodSpecs.CLOWN.key)) ?: BroodSpecs.CLOWN
                Brood.Act.entries.firstOrNull { it.name == name }?.let { brood.act(it, spec) }
            }
            is Command.RemoveOrganism -> organisms.firstOrNull { it.id == c.id }?.let { removeOrganism(it) }
            is Command.Rename -> c.nickname.trim().take(NAME_MAX).takeIf { it.isNotEmpty() }?.let { n -> organisms.firstOrNull { it.id == c.id }?.nickname = n }
            is Command.TapGlass -> tapGlass(c.worldPoint)
            is Command.TouchTank -> touchTank(c)
            is Command.DynamicRocks -> setDynamicRocks(c.on)
            is Command.UndermineRock -> undermineRock(c.rock)
            is Command.PlaceRock -> placeRock(c.shape, c.x, c.z, c.yawDeg, c.seed)
            is Command.RemoveRock -> removeRock(c.at)
            is Command.ClearRocks -> clearRocks()
            is Command.Finger -> finger(c)
            is Command.WaterPuff -> waterPuff(c)
            is Command.TouchAnimal -> touchAnimal(c.id)
            is Command.SubmarineMode -> {
                submarine.setActive(c.on, rock, sand)
                submarine.visible = c.visible; submarine.headlight = c.headlight
                if (!c.on || !c.visible) subSense.tick(this, 0.0, agentOf)
            }
            is Command.SteerSubmarine -> submarine.controls = c.controls
            is Command.SubmarineWatch -> if (submarine.active) submarine.watch(c.id, c.mode)
            is Command.SubmarineWatchNext -> if (submarine.active) nextSubFish(c.step)?.let { submarine.watch(it, submarine.watchMode) }
            is Command.FloatBag -> bags.float(this, c.speciesId, c.count, c.size, c.sizeCm, c.x, c.z)
            is Command.NetOut -> net.request(this, c.id)
            is Command.HurryBags -> { bags.hurry = c.on && bags.floating > 0; if (c.openNow) bags.openAll() }
            is Command.PlaceCoral -> coralPicks.place(this, c.spot)
            is Command.SetSpeed -> speed = c.speed
            is Command.ResetToReality -> resetTo(c.water, c.livestock)
            is Command.SetSize -> organisms.firstOrNull { it.id == c.id }?.let { it.lengthCm = c.sizeCm.coerceIn(0.5, it.species.maxLengthCm * 1.2) }
            is Command.SetPredation -> predationEnabled = c.enabled
            is Command.Shadow -> shadow(c.worldPoint)
            is Command.AddMany -> Stocking.addMany(this, c.speciesId, c.count, c.sizeCm, c.size)
            is Command.ScrapeGlass -> { careLog.scrapes++; scrapeGlass(c) }
            is Command.Micro -> water.micro.apply(c.action, c.amount, water.s)
            is Command.Bacteria -> water.bacteria.apply(c.action, c.amount, water)
        }
    }

    /**
     * Treatments (research 01 §3.8, §4.3). Hyposalinity ramps SG down at 0.002/day to 1.010 and holds it for [days];
     * copper is ramped to 0.20 ppm over 2 days and held for [days]; UV switches the steriliser on; tank transfer,
     * formalin and praziquantel act on the fish named (all fish by default) for [days].
     */
    private fun startTreatment(kind: TreatmentKind, days: Double, fishIds: Set<String>?) {
        var total = days
        when (kind) {
            TreatmentKind.HYPOSALINITY -> {
                val downDays = ((water.s.sg - Treatments.HYPO_SG) / Treatments.HYPO_RATE_PER_DAY).coerceAtLeast(0.0)
                apply(Command.RampParam("sg", Treatments.HYPO_SG, downDays * 1440, hold = true))
                total = downDays + days
            }
            TreatmentKind.COPPER -> {
                apply(Command.RampParam("copper", Treatments.COPPER_PPM, Treatments.COPPER_RAMP_DAYS * 1440, hold = true))
                total = Treatments.COPPER_RAMP_DAYS + days
            }
            TreatmentKind.UV -> water.uv = true
            else -> {}
        }
        val end = if (total <= 0) Double.POSITIVE_INFINITY else simTimeSec + total * 86400
        treatments.active[kind] = ActiveTreatment(kind, simTimeSec, end, fishIds)
    }

    private fun endTreatment(t: ActiveTreatment) {
        treatments.active.remove(t.kind)
        when (t.kind) {
            // back up at 0.002 SG/day to normal, then the chemistry takes over
            TreatmentKind.HYPOSALINITY -> {
                val upDays = ((1.025 - water.s.sg) / Treatments.HYPO_RATE_PER_DAY).coerceAtLeast(0.0)
                apply(Command.RampParam("sg", 1.025, upDays * 1440, hold = false))
            }
            // stop dosing and run fresh carbon to strip what is left
            TreatmentKind.COPPER -> { ramps.remove("copper"); water.setParam("carbon", 1.0) }
            TreatmentKind.UV -> water.uv = false
            // the treated fish come back clean
            TreatmentKind.TANK_TRANSFER -> for (o in organisms) if (o.alive && t.applies(o) && o.disease == Disease.ICH) {
                pendingEventsSink += SimEvent.Recovered(o.id, Disease.ICH, simTimeSec)
                o.disease = null; o.diseaseStage = 0.0
            }
            else -> {}
        }
    }

    private fun expireTreatments() {
        if (treatments.active.isEmpty()) return
        for (t in treatments.active.values.toList()) if (simTimeSec >= t.endSec) endTreatment(t)
    }

    /** Current value of a rampable parameter. */
    fun paramValue(id: String): Double? = when (id.lowercase()) {
        "light" -> lights.intensityOverride ?: 1.0
        "flow" -> water.flowStrength
        "photoperiod" -> lights.photoperiodHours ?: LightSchedule.DEFAULT_PHOTOPERIOD_H
        "roomtemp" -> config.roomC
        "crab_predation" -> if (crabLiveFishScenario) 1.0 else 0.0
        "asterina_coral" -> if (asterinaCorallivoryScenario) 1.0 else 0.0
        "unsourced_predation" -> if (unsourcedPredation) 1.0 else 0.0
        "lid" -> if (water.lid) 1.0 else 0.0
        else -> water.s.get(id)
    }

    private fun applyRamps() {
        if (ramps.isEmpty()) return
        val it = ramps.values.iterator()
        while (it.hasNext()) {
            val r = it.next()
            val active = !r.done(simTimeSec - CHEM_DT)
            if (active || r.hold) setParam(r.id, r.value(simTimeSec))
            if (!active && !r.hold) it.remove()
        }
    }

    private fun setParam(id: String, value: Double) {
        val k = id.lowercase()
        when {
            k == "light" -> lights.intensityOverride = value.coerceIn(0.0, 1.5)
            k == "roomtemp" -> { config = config.copy(roomC = value); water = rebuildWater() }
            k == "flow" -> setFlowStrength(value)
            k == "speed" -> speed = value
            k == "photoperiod" -> lights.photoperiodHours = value.coerceIn(4.0, 16.0)
            k == "crab_predation" -> crabLiveFishScenario = value > 0.5
            k == "asterina_coral" -> asterinaCorallivoryScenario = value > 0.5
            k == "unsourced_predation" -> unsourcedPredation = value > 0.5
            k.startsWith("spectrum.") -> lights.setGain(k.removePrefix("spectrum."), value)
            k.startsWith("pump.") -> setPump(id.substring(5), value > 0.5)
            else -> water.setParam(id, value)
        }
    }

    /** Switch one pump (by name, case-insensitive) on or off in both flow models. */
    fun setPump(name: String, on: Boolean) {
        flow.jets = flow.jets.map { if (it.name.equals(name, true)) it.copy(on = on) else it }
        fluid?.let { f -> f.pumps = f.pumps.map { if (it.name.equals(name, true)) it.copy(on = on) else it } }
    }

    val pumpNames: List<String> get() = flow.jets.map { it.name }

    /** Rock rearrangement (06 §3.3): territories re-anchor, familiarity falls to 0.5, intrusion memory clears. */
    private fun rearrangeRock() {
        territoryResetAt = simTimeSec
        for (o in organisms) {
            // individuals still recognise each other (familiarity 0.5, not 0; 06 §3.3); intrusion memory is kept
            for (r in o.relations.values) r.familiarity = min(r.familiarity, 0.5)
            agents[o]?.brain?.utility?.behaviors?.forEach { (it as? com.reeflive.core.ai.PairBouts)?.reset() }
        }
        emit(SimEvent.TerritoryReset(simTimeSec))
    }

    private fun rebuildWater(): WaterSim {
        val old = water
        return WaterSim(config, old.s.copy()).also { nw ->
            nw.algae.copyFrom(old.algae)
            nw.copyEquipmentFrom(old)
            nw.micro.copyFrom(old.micro)
            nw.bacteria.copyFrom(old.bacteria)
            nw.flowStrength = old.flowStrength; nw.heaterWorking = old.heaterWorking; nw.heaterStuckOn = old.heaterStuckOn; nw.heaterSetC = old.heaterSetC; nw.ato = old.ato
        }
    }

    private fun setFlow(on: Boolean) {
        flow.powerheadsOn = on
        water.s.flowOn = on
    }

    /** Sandbox pump strength 0..2 (0 = pumps off, 1 = Tee's setup). */
    fun setFlowStrength(v: Double) {
        val k = v.coerceIn(0.0, 2.0)
        flow.strength = k.coerceAtLeast(0.05)
        water.flowStrength = k
        setFlow(k > 0.02)
        fluid?.let { it.strength = k; it.powerheadsOn = k > 0.02 }
    }
    val flowStrength: Double get() = if (water.s.flowOn) water.flowStrength else 0.0

    fun feed(kind: FoodKind, grams: Double, at: Vec3) {
        surface.impulse(at.x, at.z, 0.08, 2.5)
        water.addFeed(grams * kind.nutrientFactor, kind.po4Factor, kind.docFactor)
        emit(SimEvent.Fed(kind, grams, simTimeSec))
        // every animal that eats this food learns the time, the place and the keeper (ai/Mind.kt, realism_gaps X3)
        for (a in agents.values) a.brain.mind.onKeeperFeed(simTimeSec, at, a.ctx.o.species.dietMatch(kind) > 0 || kind == FoodKind.NORI && a.ctx.o.species.grazer)
        if (!behaviorEnabled || abstractMode) { abstractFeed(kind, grams, at); return }
        food.spawn(kind, grams, at, simTimeSec)
        for (a in agents.values) a.brain.onFood(a.ctx, kind, at)
    }

    /** Feeding without particles (abstract mode): diet-matched animals eat in priority order. */
    private fun abstractFeed(kind: FoodKind, grams: Double, at: Vec3) {
        var left = if (kind == FoodKind.NORI) 1.0 else grams
        val eaters = organisms.filter { o ->
            o.alive && o.species.dietMatch(kind) > 0 && o.hunger > 0.1 && (o.acceptsPrepared || kind.suspended) &&
                !(o.brainKind == BrainKind.EEL && at.distanceTo(rock.anchors.pos("CAVE_EEL", at)) > 20) &&
                !(o.brainKind == BrainKind.FEATHER_DUSTER && (!kind.suspended || invertEcology.crownOf(o) < 0.7))
        }.map { it to (it.aggression + it.boldness + (if (it.role == "FEMALE") 0.3 else 0.0) + it.hunger + rng.nextDouble() * 0.3 + (it.species.foodPreference(kind) - it.species.dietMatch(kind))) }
            .sortedByDescending { it.second }.map { it.first }
        // corals catch a share of a dosed cloud straight away (no particles in abstract mode)
        if (kind.coralCatchable) for (c in corals) if (c.tissue >= 0.1 && c.kind != CoralKind.MACRO) c.fed = min(1.0, c.fed + CORAL_FED_PER_CATCH * 3)
        for (o in eaters) {
            if (left <= 0) break
            if (kind == FoodKind.NORI) { o.hunger = max(0.0, o.hunger - 0.4); o.nutritionGreens = 1.0; continue }
            val want = o.mealGrams * (o.hunger / 0.8) * o.species.foodPreference(kind)
            val g = min(want, left)
            o.eat(g); left -= g
        }
        if (left > 0 && kind != FoodKind.NORI) {
            // scavengers clean up most leftovers; the rest becomes detritus
            val scav = organisms.filter { it.alive && it.brainKind in SCAVENGERS }
            for (s in scav) { val g = min(left * 0.5, s.mealGrams); s.eat(g); left -= g }
            water.addDetritusTanGrams(left * 0.10)
        }
    }

    fun tapGlass(point: Vec3) {
        surface.impulse(point.x, point.z.coerceIn(0.0, rock.widthCm), 0.03, 3.0)
        val night = perception.night
        perception.taps += Tap(point, simTimeSec, night)
        for (o in organisms) {
            if (!o.alive) continue
            val d = o.pos.distanceTo(point)
            if (d > 30) continue
            var s = o.skittishness * (1 - d / 30) * (if (night) 1.5 else 1.0) * StressModel.skittishMult(o)
            // a wrasse in its mucus cocoon needs twice the disturbance to wake (06 §5.5)
            if (o.sleeping && (o.brainKind == BrainKind.WRASSE || o.brainKind == BrainKind.FAIRY_WRASSE && o.cocoon > 0.5)) s *= 0.5
            o.startle = max(o.startle, s)
            StressModel.addSocial(o, TAP_SOCIAL * s)
            agents[o]?.brain?.onTap(agents[o]!!.ctx, point, s)
        }
    }

    /** The toy submarine (Submarine mode, [Command.SubmarineMode]) and the animals' perception of it. */
    val submarine = Submarine()
    /** Acclimation bags floating in the tank (sandbox Add life, [Command.FloatBag]). */
    val bags = AcclimationBags()
    /** Corals out of their bag waiting for the keeper to pick a spot (My tank, [Command.PlaceCoral]). */
    val coralPicks = CoralPicks()
    /** [CoralSpots.lightAt] for [c], cached until it or the rockwork moves. */
    private fun localLight(c: Coral): Double {
        val rev = rockRevision()
        if (c.lightHere.isNaN() || c.lightAtRev != rev) { c.lightHere = CoralSpots.lightAt(rock, c.pos, c.normal); c.lightAtRev = rev }
        return c.lightHere
    }
    /** Changes whenever rocks move, are added or taken out (spot suggestions are recomputed then). */
    fun rockRevision(): Long = rockPhysics?.version ?: 0L
    /** The fish net that takes dead animals out ([Command.NetOut]). */
    val net = FishNet()
    val subSense = com.reeflive.core.ai.SubmarineSense()
    private val agentOf: (Organism) -> com.reeflive.core.ai.Agent? = { agents[it] }

    /** Habituation and quick-tap runs for [Command.TouchTank]. */
    val touchMemory = TouchMemory()
    /** The last gentle tap's outcome (UI wording, tests). */
    var lastTouch: TouchOutcome? = null; private set

    /**
     * A gentle fingertip tap ([TankTouch]): polyp corals by a rock / coral tap retract, nearby animals answer by
     * species and personality unless they have got used to it, and a quick run of taps in one spot is a knock
     * ([tapGlass], the full startle).
     */
    fun touchTank(c: Command.TouchTank) {
        val p = c.point
        val night = perception.night
        val surface = if (c.surface == TouchSurface.ROCK && corals.any { it.pos.distanceTo(p) < TankTouch.ON_CORAL_CM }) TouchSurface.CORAL else c.surface
        if (surface == TouchSurface.SURFACE) this.surface.impulse(p.x, p.z.coerceIn(0.0, rock.widthCm), 0.06, 2.5)
        if (touchMemory.tap(p, simTimeSec, TankTouch.RUN_WINDOW_S * max(1.0, speed))) {
            tapGlass(p)
            lastTouch = TouchOutcome(surface, p, true, emptyMap(), 0, simTimeSec)
            return
        }
        var retracted = 0
        if (surface == TouchSurface.ROCK || surface == TouchSurface.CORAL) for (k in corals) {
            val d = k.pos.distanceTo(p)
            // a GSP mat closes around the touch, not as a whole (GspPolyps)
            if (k.kind == CoralKind.GSP) { if (GspPolyps.poke(k, p, simTimeSec, 1.0, 3.0)) { retracted++; continue } }
            if (d >= TankTouch.CORAL_CM || !TankTouch.retracts(k.kind)) continue
            val hit = if (d < TankTouch.ON_CORAL_CM) TankTouch.CORAL_DISTURB_HIT else TankTouch.CORAL_DISTURB
            // a tap on nearby rock shakes a mat's closest polyps; how much of the colony that is depends on its size
            k.disturb = max(k.disturb, if (k.kind == CoralKind.GSP) hit * gspShare(k) else hit)
            retracted++
        }
        val out = LinkedHashMap<String, TouchReaction>()
        for (o in organisms) {
            if (!o.alive) continue
            val d = o.pos.distanceTo(p)
            val r = TankTouch.reaction(o, surface, d, night)
            if (r == TouchReaction.NONE) continue
            val h = touchMemory.habituation(o.id, simTimeSec)
            if (h >= TankTouch.HABIT_IGNORE) continue
            val reach = when (r) {
                TouchReaction.INVESTIGATE, TouchReaction.RISE -> TankTouch.SEE_CM
                TouchReaction.CHECK -> 30.0
                else -> if (surface == TouchSurface.GLASS) TankTouch.FEEL_CM else TankTouch.ROCK_CM
            }
            val s = TankTouch.falloff(d, reach, c.strength) * (1 - h)
            if (s < 0.05) continue
            touchMemory.reacted(o.id, simTimeSec)
            if (r == TouchReaction.DUCK || r == TouchReaction.AVOID) {
                o.startle = max(o.startle, 0.1 * s)
                StressModel.addSocial(o, TAP_SOCIAL * 0.3 * s)
            }
            agents[o]?.let { a -> a.ctx.p = perception; a.brain.onTouch(a.ctx, p, r, s) }
            out[o.id] = r
        }
        lastTouch = TouchOutcome(surface, p, false, out, retracted, simTimeSec)
    }

    /** The finger held on the glass after a long press ([Command.Finger]), or null. */
    var finger: FingerState? = null; private set
    /** The last tap right on an animal ([Command.TouchAnimal]): who and how it answered (tests, captions). */
    var lastAnimalTouch: Pair<String, TouchReaction>? = null; private set

    /** One animal answers a touch of kind [r] at [p] [d] cm away (reach [reach]), unless it has got used to them. */
    private fun answerTouch(o: Organism, p: Vec3, r: TouchReaction, d: Double, reach: Double, strength: Double = 1.0): Boolean {
        val h = touchMemory.habituation(o.id, simTimeSec)
        if (h >= TankTouch.HABIT_IGNORE) return false
        val s = TankTouch.falloff(d, reach, strength) * (1 - h)
        if (s < 0.05) return false
        touchMemory.reacted(o.id, simTimeSec)
        if (r == TouchReaction.DUCK || r == TouchReaction.AVOID) {
            o.startle = max(o.startle, 0.1 * s)
            StressModel.addSocial(o, TAP_SOCIAL * 0.3 * s)
        }
        agents[o]?.let { a -> a.ctx.p = perception; a.brain.onTouch(a.ctx, p, r, s) }
        return true
    }

    /**
     * A finger on the glass ([Command.Finger], TankTouch finger rules): when it goes down the animals that would
     * answer a tap there answer it, except that the fish that would come to look follow the fingertip instead for as
     * long as it stays; animals it is dragged close to later get their turn. A finger whisked past fast startles the
     * shy ones near it. Lifting it lets the followers linger a moment.
     */
    fun finger(c: Command.Finger) {
        val p = c.point ?: run { liftFinger(); return }
        val t = simTimeSec
        val f = finger ?: FingerState(p, c.surface, t).also { finger = it }
        f.move(p, c.speedCmS, t)
        val night = perception.night
        val fast = c.speedCmS > TankTouch.FINGER_FAST_CM_S
        for (id in f.followers) organisms.firstOrNull { it.id == id }?.let { o -> agents[o]?.brain?.touchFrom = p }
        for (o in organisms) {
            if (!o.alive || o.id in f.considered) continue
            val d = o.pos.distanceTo(p)
            val r = if (fast) TankTouch.fastFingerReaction(o, d, night) else TankTouch.fingerReaction(o, f.surface, d, night)
            if (r == TouchReaction.NONE) continue
            f.considered += o.id
            val reach = when (r) {
                TouchReaction.FOLLOW -> TankTouch.SEE_CM
                else -> TankTouch.FEEL_CM
            }
            if (!answerTouch(o, p, r, d, reach)) continue
            f.reactions[o.id] = r
            if (r == TouchReaction.FOLLOW) f.followers += o.id
        }
    }

    /** The finger lifted: its followers watch the spot a moment longer, then go back to what they were doing. */
    private fun liftFinger() {
        val f = finger ?: return
        finger = null
        for (id in f.followers) organisms.firstOrNull { it.id == id }?.let { o ->
            agents[o]?.brain?.let { b -> b.touchUntil = min(b.touchUntil, simTimeSec + TankTouch.FINGER_LINGER_S) }
        }
    }

    /**
     * A flick in the water ([Command.WaterPuff]): a puff added to the flow ([FlowField.addGust]) that every flow user
     * feels (the nori cloth, food, sand clouds, the renderer's coral sway); polyps it reaches pull in a little, sand under
     * it lifts, and shy animals, crustaceans and dusters in its path react ([TankTouch.puffReaction]).
     */
    fun waterPuff(c: Command.WaterPuff) {
        val sp = c.velocity.length()
        if (sp < 1e-6) return
        val t = simTimeSec
        flow.now = t
        flow.addGust(c.point, c.velocity, t)
        val dir = c.velocity / sp
        val travel = sp.coerceAtMost(com.reeflive.core.geom.FlowGust.MAX_SPEED) * com.reeflive.core.geom.FlowGust.TAU_S
        val end = c.point + dir * travel
        fun pathPoint(q: Vec3): Vec3 = c.point + dir * ((q - c.point) dot dir).coerceIn(0.0, travel)
        fun distToPath(q: Vec3): Double = q.distanceTo(pathPoint(q))
        // sand under the puff's path lifts (a little cloud, pushed along)
        for (k in 0..3) {
            val q = c.point.lerp(end, k / 3.0)
            val sy = sand.surfaceY(q.x.coerceIn(0.0, rock.lengthCm), q.z.coerceIn(0.0, rock.widthCm))
            if (q.y - sy < 6.0) { sand.puff(q.x, sy + 0.5, q.z, 6, 3.0 + 0.1 * sp, dir.x * sp * 0.3, dir.z * sp * 0.3); break }
        }
        for (k in corals) {
            if (!TankTouch.retracts(k.kind)) continue
            val d = distToPath(k.pos)
            if (d >= TankTouch.PUFF_CM) continue
            val f = TankTouch.PUFF_DISTURB * (1 - d / TankTouch.PUFF_CM)
            // a puff closes the stretch of a GSP mat it washes over
            if (k.kind == CoralKind.GSP) { GspPolyps.poke(k, pathPoint(k.pos), simTimeSec, f / TankTouch.PUFF_DISTURB, TankTouch.PUFF_CM); continue }
            k.disturb = max(k.disturb, f)
        }
        val night = perception.night
        for (o in organisms) {
            val d = distToPath(o.pos)
            val r = TankTouch.puffReaction(o, d, night)
            if (r != TouchReaction.NONE) answerTouch(o, o.pos - dir * 5.0, r, d, TankTouch.PUFF_CM)
        }
    }

    /** A tap right on an animal ([Command.TouchAnimal], [TankTouch.animalReaction]): snails and crabs tuck in, and so on. */
    fun touchAnimal(id: String) {
        val o = organisms.firstOrNull { it.id == id } ?: return
        val r = TankTouch.animalReaction(o)
        if (r == TouchReaction.NONE) { lastAnimalTouch = id to r; return }
        val answered = answerTouch(o, o.pos + o.up * 2.0, r, 0.0, 1.0)
        lastAnimalTouch = id to (if (answered) r else TouchReaction.NONE)
    }

    private var lastScrapeStir = -1e9

    /** A Flipper stroke on the glass: clears the film it sweeps; nearby fish and corals react as to a passing hand. */
    private fun scrapeGlass(c: Command.ScrapeGlass) {
        when (c.pane) {
            GlassPane.FRONT -> water.algae.scrapeGlass(water.s, c.x0, c.y0, c.x1, c.y1, c.widthCm, c.heightCm)
        }
        if (simTimeSec - lastScrapeStir >= SCRAPE_STIR_SEC) {
            lastScrapeStir = simTimeSec
            shadow(Vec3(c.x1, c.y1, rock.widthCm - 2.0))
        }
    }

    /** Animals near [point] dart off as from a shadow overhead (the fish net dipping in). */
    internal fun scareFrom(point: Vec3) = shadow(point)

    private fun shadow(point: Vec3) {
        for (o in organisms) if (o.alive && o.pos.distanceTo(point) < 40) agents[o]?.brain?.onShadow(agents[o]!!.ctx, point)
        for (c in corals) if (c.pos.distanceTo(point) < 15) c.disturb = 0.6
    }

    private fun resetTo(w: WaterState, livestock: List<LivestockSeed>) {
        water = WaterSim(config.copy(), w.copy())
        setFlow(true)
        organisms.clear(); agents.clear(); corals.clear(); food.clear(); moltShells.clear(); sandPiles.clear(); sand.reset(); invertEcology.reset(); shrimpBreeding.reset(); brood.reset(); touchMemory.clear()
        finger = null; flow.gusts.clear()
        submarine.reset(); subSense.clear(); bags.clear(); coralPicks.clear(); net.clear()
        pests.copyFrom(PestState()); treatments.active.clear(); ramps.clear(); scheduled.clear()
        lights.forcedOn = null; lights.intensityOverride = null
        predationEnabled = config.predationEnabled
        livestock.forEach { addFromSeed(it) }
        corals.forEach { it.established = true }
    }

    // ============================================================== organisms
    fun addFromSeed(seed: LivestockSeed): Organism {
        val sp = species[seed.speciesId]
        val id = seed.id.ifBlank { "${sp.id}#${idCounter++}" }
        if (sp.isCoral) {
            val a = seed.placement?.let { rock.anchors[it] ?: rock.anchors["coral_$it"] }
                ?: if (sp.coralKind.stony) null else rock.anchors.ofType("CORAL").firstOrNull { it.name.contains(sp.coralKind.name.lowercase().take(4)) }
            // a stony coral has no anchor on this rock: one added on its own goes to a free spot (as Add life places them)
            val offset = seed.offset ?: if (a == null && sp.coralKind.stony)
                Stocking.coralSpots(rock, corals.map { it.pos }, 1, rng, Stocking.spacingCm(sp.coralKind)).firstOrNull()?.let { it.first - StonyCorals.STUDIO }
                else null
            val pos = (a?.pos ?: StonyCorals.STUDIO) + (offset ?: Vec3.ZERO)
            val normal = if (offset != null) Vec3.Y else a?.normal ?: Vec3.Y
            corals += Coral(id, sp, seed.nickname, a?.name ?: "", pos, normal, rng.nextDouble() * 6.28, offset ?: Vec3.ZERO)
            return Organism(id, sp, 1.0, seed.nickname, null, rng.fork(id.hashCode().toLong()))   // placeholder (not added)
        }
        val size = if (seed.sizeCm > 0) seed.sizeCm else sp.typicalAdultCm.coerceAtMost(sp.maxLengthCm)
        val o = Organism(id, sp, size, seed.nickname, seed.role, rng.fork(id.hashCode().toLong()), seed.placement, seed.variant)
        o.hunger = 0.25 + rng.nextDouble() * 0.2
        o.ageSec = LifeCycle.initialAgeSec(o, seed.ageYears)
        if (o.brainKind == BrainKind.WRASSE || o.brainKind == BrainKind.LAWNMOWER_BLENNY) o.acceptsPrepared = rng.chance(0.7)
        // research 16: many diamond gobies never take prepared food and live off the sand fauna alone
        if (o.brainKind == BrainKind.DIAMOND_GOBY) o.acceptsPrepared = rng.chance(0.6)
        // leopard wrasses: many learn frozen mysis/cyclops, thin ones never do (Fenner, TFH; LiveAquaria)
        if (o.brainKind == BrainKind.LEOPARD_WRASSE) o.acceptsPrepared = rng.chance(0.7)
        organisms += o
        val ctx = Ctx(this, o).also { it.p = perception }
        val brain = SpeciesBrains.create(ctx)
        agents[o] = Agent(ctx, brain)
        return o
    }

    /** Fish that leapt over the rim this tick ([JumpModel]); taken out after the loop that flew them. */
    private val leftTank = ArrayList<Organism>()

    private fun jumpedOut(o: Organism) { leftTank += o }

    /** A fish that jumped out landed on the floor: it is gone from the tank (no carcass in the water). */
    private fun takeOutJumpers() {
        if (leftTank.isEmpty()) return
        for (o in leftTank) {
            o.leap = null
            HealthModel.kill(o, JUMPED_OUT, simTimeSec, pendingEventsSink)
            removeOrganism(o)
        }
        leftTank.clear()
        flushEvents()
    }

    /**
     * The bottom fish of a small chromis group (2–5) that its mates have been chasing (research 41 §2.5): the steady
     * pecking wears it down. In a group of 6 or more the chases spread out and nobody is singled out.
     */
    private fun chromisUnderdog(o: Organism): Boolean {
        var n = 1
        for (m in organisms) {
            if (m === o || !m.alive || m.brainKind != BrainKind.CHROMIS) continue
            n++
            if (com.reeflive.core.ai.Interactions.dominates(o, m)) return false
        }
        return n in 2..5 && o.relations.values.any { it.harassedBy > 0.2 }
    }

    fun removeOrganism(o: Organism) {
        organisms.remove(o)
        agents.remove(o)
        perception.stations.removeAll { it.cleaner === o }
        perception.stations.forEach { if (it.client === o) it.release() }
    }

    /** Predation outcome (eel strike, hawkfish pounce). Removes the prey (SIMULATION §4.5). */
    fun eaten(predator: Organism, prey: Organism) {
        predator.hunger = max(0.0, predator.hunger - 0.6)
        predator.satietyUntil = simTimeSec + 3 * 86400
        emit(SimEvent.Eaten(predator.id, prey.id, simTimeSec))
        emit(SimEvent.Died(prey.id, "eaten by ${predator.nickname}", simTimeSec))
        prey.alive = false
        removeOrganism(prey)
    }

    fun agentOf(o: Organism): Agent? = agents[o]

    fun lightChannels(): DoubleArray = lightCh.copyOf()
    val lightIntensity: Double get() = LightSchedule.intensity(lightCh)
    val minutesSinceLightsOn: Double? get() = cachedSinceOn

    // ============================================================== snapshot
    private fun statsAll(): List<OrganismStats> {
        val byId = organisms.associateBy { it.id }
        val out = organisms.map { o ->
            val d = com.reeflive.core.ai.Caption.describe(o, this)
            val target = (d.targetId ?: o.focusId)?.let { byId[it] }
            val thought = if (thoughtsEnabled) thoughtBook.thought(o, this, target) else Thought.NONE
            o.stats(d.caption, d.intent, d.targetId ?: o.focusId, com.reeflive.core.ai.Social.summarize(o, byId, simTimeSec), thought)
        }
        if (thoughtsEnabled) thoughtBook.endSnapshot(this, byId.keys)
        return out
    }

    /**
     * The "what is it thinking" readout in [OrganismStats.thought] (ai/Thoughts.kt). Read-only: turning it off changes
     * nothing in the sim (ThoughtsTest), only the snapshot's words.
     */
    var thoughtsEnabled = true
    private val thoughtBook = com.reeflive.core.ai.ThoughtBook()

    /** Minutes until the lights go out (null while dark), from the last light update. */
    val minutesUntilLightsOff: Double? get() = cachedUntilOff

    // life stage and sex per organism for the renderer / fish card (LifeStages.kt)
    fun snapshot(): SimSnapshot =
        buildSnapshot().copy(
            lifeForms = organisms.filter { it.isFish }.associate { it.id to LifeStages.form(it) },
            bareScape = bareScape,
            shape = shape,
            submarine = if (submarine.active) submarine.pose(subSense.reactions) else null,
            rockPoses = rockPoses(),
            rockVersion = rockPhysics?.version ?: 0L,
            net = net.pose(),
            netFlush = net.lastFlush,
            brood = brood.view,
            builtRocks = builtRocks(),
            rockSdf = rock.dynamic?.takeIf { it.active },
            coralPick = coralPicks.pose(this),
        ).let { s ->
            if (!bags.any) s
            else s.copy(organisms = s.organisms + bags.riderPoses(), bags = bags.poses(water.s.tempC, simTimeSec), bagHurry = bags.hurry, speed = bags.effectiveSpeed(speed))
        }

    private fun buildSnapshot(): SimSnapshot {
        val (xyz, kinds) = food.export()
        val evs = drainEvents()
        return SimSnapshot(
            simTimeSec = simTimeSec,
            lights = FloatArray(LightSchedule.CHANNELS) { lightCh[it].toFloat() },
            water = water.s.copy(),
            organisms = organisms.map { it.toPose() },
            corals = corals.map { it.toPose(simTimeSec) },
            food = xyz, foodKinds = kinds,
            algaeBloom = water.s.algaeHair.toFloat(),
            algae = water.algae.snapshot(),
            eggClutches = repro.poses(),
            microfauna = water.micro.snapshot(),
            bacteria = water.bacteria.snapshot(),
            events = evs,
            speed = speed,
            lightIntensity = lightIntensity.toFloat(),
            dayPhase = lights.phaseLabel(simTimeSec),
            stats = statsAll(),
            tick = tickCount,
            abstractMode = abstractMode,
            moltShells = moltShells.map { it.first },
            sandPiles = FloatArray(sandPiles.size * 4).also { a -> sandPiles.forEachIndexed { i, p -> a[i * 4] = p.pos.x.toFloat(); a[i * 4 + 1] = p.pos.z.toFloat(); a[i * 4 + 2] = p.radius.toFloat(); a[i * 4 + 3] = p.height.toFloat() } },
            flowStrength = flowStrength.toFloat(),
            surfaceHeights = surface.heights.copyOf(),
            surfaceDims = intArrayOf(surface.sx, surface.sz),
            flowGrid = fluid?.let { f ->
                if (simTimeSec - lastFlowGridAt > 0.25 * max(speed, 1.0) || flowGridCache == null) { lastFlowGridAt = simTimeSec; flowGridCache = f.snapshotGrid() }
                flowGridCache
            },
            noriRemaining = food.nori?.remaining?.toFloat() ?: 0f,
            sandGrid = run {
                if (simTimeSec - lastSandGridAt > (if (sand.busy) SAND_GRID_BUSY_S else 0.25) * max(speed, 1.0) || sandGridCache == null) { lastSandGridAt = simTimeSec; sandGridCache = sand.gridSnapshot() }
                sandGridCache
            },
            sandClouds = sand.cloudArray(),
            foodMeta = food.exportMeta(simTimeSec),
            waste = waste.export(simTimeSec),
            pumps = pumpArray(),
            berghia = pests.berghia.toFloat(),
            berghiaPrey = if (pests.berghia > 0) pests.berghiaPrey else null,
            nori = food.nori?.let { n ->
                val cl = n.cloth
                NoriPose(cl.cols, cl.rows, cl.exportNodes(), cl.exportCells(), n.pos.x.toFloat(), n.pos.y.toFloat(), n.pos.z.toFloat(),
                    cl.inward.x.toFloat(), cl.inward.y.toFloat(), cl.inward.z.toFloat())
            },
            gusts = gustArray(),
            care = water.careState(),
            reefCareAt = reefCareAt,
        )
    }

    /** Live touch puffs for the renderer ([SimSnapshot.gusts]). */
    private fun gustArray(): FloatArray {
        val g = flow.gusts
        if (g.isEmpty()) return EMPTY_FLOATS
        val stride = com.reeflive.core.geom.FlowGust.STRIDE
        val out = FloatArray(g.size * stride)
        g.forEachIndexed { i, q ->
            val o = i * stride
            out[o] = q.pos.x.toFloat(); out[o + 1] = q.pos.y.toFloat(); out[o + 2] = q.pos.z.toFloat()
            out[o + 3] = q.dir.x.toFloat(); out[o + 4] = q.dir.y.toFloat(); out[o + 5] = q.dir.z.toFloat()
            out[o + 6] = q.speed.toFloat(); out[o + 7] = (simTimeSec - q.bornAt).toFloat()
        }
        return out
    }

    /** Jets for the renderer ([SimSnapshot.pumps]): powerheads follow the master switch and the flow slider, the HOB runs on its own. */
    private fun pumpArray(): FloatArray {
        val jets = flow.jets
        val out = FloatArray(jets.size * SimSnapshot.PUMP_STRIDE)
        jets.forEachIndexed { i, j ->
            val o = i * SimSnapshot.PUMP_STRIDE
            val hob = j.name.equals("HOB", true)
            val output = when {
                !j.on -> 0.0
                hob -> 1.0
                !flow.powerheadsOn -> 0.0
                else -> flow.strength
            }
            out[o] = j.pos.x.toFloat(); out[o + 1] = j.pos.y.toFloat(); out[o + 2] = j.pos.z.toFloat()
            out[o + 3] = j.axis.x.toFloat(); out[o + 4] = j.axis.y.toFloat(); out[o + 5] = j.axis.z.toFloat()
            out[o + 6] = output.toFloat(); out[o + 7] = j.v0.toFloat()
        }
        return out
    }

    // Initialised last so every property above exists before livestock is added.
    init {
        livestock.forEach { addFromSeed(it) }
        corals.forEach { it.established = true }
        updateLights()
        lastLight = perception.light
        perception.rebuild()
        latest = snapshot()
    }

    companion object {
        /** Death cause of a fish that leapt over the rim of the open top ([JumpModel]). */
        const val JUMPED_OUT = "jumped out of the tank"
        /** How far above the aimed spot a target-fed food is let go (cm): close enough to land on it, clear of the rock. */
        const val TARGET_LIFT_CM = 2.5
        /** Default drop point of a feed (back-centre surface beside the HOB return, video T97); anchor `HAND_FEED`. */
        val HAND_FEED = Vec3(74.0, TankDims.WATER_LEVEL_CM - 1.0, 4.5)
        /** A finger with no news for this long (sim s at 1×) counts as lifted (a lost touch-up). */
        const val FINGER_TIMEOUT_S = 6.0
        /** An anchor / colony within this of a rock's analytic surface rides with that rock. */
        const val ANCHOR_ON_ROCK_CM = 4.0
        /** A hole belongs to the rock nearest this far in from its mouth (cm). */
        const val HOLE_LABEL_IN_CM = 1.0
        const val CORAL_ON_ROCK_CM = 4.0
        /** A dry rock from the builder starts this far above what is under it (cm), then falls. */
        const val DROP_GAP_CM = 1.5
        /** A tap this close to a rock (cm) takes that rock out. */
        const val REMOVE_NEAR_CM = 6.0
        /** [undermineRock] digs this far past the rock's centre of mass (cm), so it no longer stands. */
        const val UNDERMINE_PAST_CM = 2.5
        /** Longest animal name a [Command.Rename] keeps. */
        const val NAME_MAX = 24
        private val EMPTY_FLOATS = FloatArray(0)
        const val CHEM_DT = 60.0
        /** Acute stress input of a full-strength glass tap (cortisol envelope, S:Mathew23). */
        const val TAP_SOCIAL = 0.35
        const val ABSTRACT_SPEED = 240.0
        /** Largest behavior/steering step in sim seconds (stepWall caps dtB here at high speed). */
        const val MAX_BEHAVIOR_DT = 0.5
        /** Sand grid refresh for the renderer while grains fly or slopes pour (s); 0.25 s when the bed is still. */
        const val SAND_GRID_BUSY_S = 0.1
        /** Sim minutes between the sand bed's slow upkeep passes (smoothing, freshness, detritus): a full-grid sweep. */
        const val SAND_RELAX_EVERY_MIN = 3.0
        /** Nitrogen as a fraction of fish wet mass (~18 % protein × 16 % N ≈ 2-3 %). */
        const val CARCASS_N_FRACTION = 0.025
        /** Hours before a carcass starts leaching (autolysis), and the leach rate of what N is left (est.). */
        const val CARCASS_LAG_H = 4.0
        const val CARCASS_LEACH_PER_H = 0.02
        /** Total TAN-equivalent grams a carcass of [o] can ever release: its N content. */
        fun carcassTanPoolG(o: Organism) = o.massG * CARCASS_N_FRACTION * 17.0 / 14.0
        /** g TAN-equivalent released by a full colony's tissue per unit CoralKind.biomass. */
        const val CORAL_TAN_PER_COLONY = 2.0
        const val ASTERINA_CAP = 4
        /** Shortest sim time between the startles a dragged Flipper causes (one per stroke, not per frame). */
        const val SCRAPE_STIR_SEC = 1.0
        /** Social-stress input held on every fish while flatworm toxin is in the water. */
        const val TOXIN_SOCIAL = 0.3
        /**
         * Detritus kept in suspension by stirring reaches the sock/skimmer: share of the column's detritus pool captured
         * per unit of suspended load that timed out ([SandBed.resuspendedPending]), capped per minute (DESIGN ESTIMATE).
         */
        @Ev(Evidence.DESIGN_ESTIMATE) const val RESUSPEND_CAPTURE_PER_LOAD = 0.005
        const val RESUSPEND_CAPTURE_MAX_PER_MIN = 0.0002
        /** [Coral.fed] gained per caught particle and its e-folding time (≈ 2 days). */
        @Ev(Evidence.DESIGN_ESTIMATE) const val CORAL_FED_PER_CATCH = 0.08
        @Ev(Evidence.DESIGN_ESTIMATE) const val CORAL_FED_DECAY_MIN = 2 * 1440.0
        val SCAVENGERS = setOf(BrainKind.HERMIT, BrainKind.NASSARIUS, BrainKind.BRITTLE_STAR, BrainKind.BANDED_SHRIMP, BrainKind.CLEANER_SHRIMP, BrainKind.PEPPERMINT_SHRIMP, BrainKind.SALLY_CRAB)
        private fun clampD(v: Double, lo: Double, hi: Double) = v.coerceIn(lo, hi)
    }
}
