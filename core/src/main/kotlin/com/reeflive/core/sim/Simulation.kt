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
class Simulation(
    config: TankConfig,
    val species: SpeciesCatalog,
    val rock: RockData,
    seed: Long = config.seed,
    startTimeSec: Double = 12 * 3600.0,
    initialWater: WaterState = WaterState(volumeL = config.systemLiters),
    livestock: List<LivestockSeed> = emptyList(),
    val lights: LightSchedule = LightSchedule(),
) {
    var config: TankConfig = config; private set
    val rng = Rng(seed)
    var water = WaterSim(config, initialWater); private set
    val organisms = ArrayList<Organism>()
    val corals = ArrayList<Coral>()
    val flow = FlowField(config.powerheads, rock.sdf, config.lengthCm, config.waterLevelCm)
    val food = FoodSystem(rock, flow, rng.fork(7))
    val perception = Perception(this)
    /** Breeding and sex change (coverage B-1..B-3), stepped in the chemistry tick. */
    val repro = Reproduction(this)
    /** Hermit shells, asterina-vs-organ-pipe and feather-duster crown cycle (research 04). */
    val invertEcology = InvertEcology(this)
    private val agents = HashMap<Organism, Agent>()

    var simTimeSec: Double = startTimeSec; private set
    var speed: Double = 1.0
        set(v) { field = v.coerceIn(0.0, 1000.0) }
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

    private val commands = ConcurrentLinkedQueue<Command>()
    /** Coral growth, branch drops and pest rolls draw from their own stream so they leave the behaviour RNG untouched. */
    val growthRng = Rng(seed xor 0x6C0A1L)
    private var fragCounter = 1
    fun nextFragId(): Int = fragCounter++
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
    /** Nassarius trails, hermit and crab scratching, brittle-star furrows (observed after each behaviour tick). */
    val sandWork = SandWork(sand, rock)
    /** Bodies rest on the live sand and push it aside instead of passing through it (SIMULATION §16.9). */
    val sandContact = SandContact(sand, rock.sdf)
    private val sandFlowAt: (Double, Double, Double) -> Vec3 = { x, y, z -> flow.velocityFast(Vec3(x, y, z)) }
    private var lastSandGridAt = -1e9
    private var sandGridCache: SandGridSnapshot? = null

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
    val surface = com.reeflive.core.geom.WaterSurface(rock.lengthCm, rock.widthCm)
    private var fluidAcc = 0.0
    private var lastFlowGridAt = -1e9
    private var flowGridCache: com.reeflive.core.geom.FlowGrid? = null
    /** Parameter id the fluid's dose tracer currently represents. */
    var doseTracerParam: String? = null; private set
    val heaterPos = Vec3(rock.lengthCm - 2.4, (rock.sandTopCm + rock.waterLevelCm) / 2, 2.5)   // equipment.glb Heater, back-right corner

    /** Turn on the grid flow solver (Live Tank); spins it up for 20 s of sim time before handing it to [flow]. */
    fun enableFluid(quality: com.reeflive.core.geom.FluidQuality) {
        val f = com.reeflive.core.geom.FluidSim(rock.lengthCm, rock.widthCm, rock.sandTopCm, rock.waterLevelCm, quality,
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

    // ============================================================== public API
    fun enqueue(cmd: Command) { commands.add(cmd) }

    /** Called by the sim thread every wall tick (normally 1/20 s). */
    fun stepWall(dtWall: Double) {
        drainCommands()
        submarine.step(dtWall, rock, sand)
        bags.stepWall(this, dtWall)
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
            submarine.step(dt, rock, sand)
            bags.stepWall(this, dt)
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
        if (useBehavior && !abstractMode) nightPlankton(simDt)
        if (sand.particleCount > 0) sand.stepClouds(min(simDt, 2.0), sandFlowAt)
        food.step(min(simDt, 1.0), simTimeSec)
        if (food.expiredGrams > 0) water.addDetritusTanGrams(food.expiredGrams * 0.10)
        if (useBehavior && !abstractMode) coralCatch(simDt)
        // checked every step (not on the 60 s chemistry tick) so the 02:00 roll lands at 02:00
        nightRolls()
        chemAccum += simDt
        while (chemAccum >= CHEM_DT) {
            chemAccum -= CHEM_DT
            chemTick()
        }
        tickCount++
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
        if (corals.isEmpty() || food.particles.none { !it.eaten && it.kind.coralCatchable }) return
        for (c in corals) {
            if (c.tissue < 0.1 || c.extension < 0.3 || c.kind == CoralKind.MACRO) continue
            val reach = (2.0 + CoralGrowth.extent(c)).coerceIn(3.0, 12.0)   // footprint radius + polyp reach, cm
            val f = food.nearest(c.pos, reach) { it.kind.coralCatchable } ?: continue
            food.consume(f)
            c.fed = min(1.0, c.fed + CORAL_FED_PER_CATCH)
        }
    }
    private var coralCatchAcc = 0.0

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
            if (!o.alive) { o.limbRig?.unseat(o); deadPhysics(o, dt); continue }
            a.intent.reset()
            a.brain.preTick(a.ctx)
            a.brain.utility.tick(a.ctx, a.intent)
            a.steering.apply(a.intent)
            o.attached = a.intent.kinematic || a.intent.crawl
            o.seat?.update(o, a.intent.crawl, crawlSurface, dt, if (a.intent.crawl) a.intent.surfaceOffset else -1.0)
            o.limbRig?.contact(o, crawlSurface, organisms, dt)
        }
        sandWork.step(organisms)
        sandContact.step(organisms, dt, displace = true)
    }

    private fun abstractPlacement(dt: Double) {
        perception.rebuild()
        for (o in organisms) {
            val a = agents[o] ?: continue
            a.ctx.p = perception; a.ctx.dt = dt
            if (!o.alive) { o.limbRig?.unseat(o); deadPhysics(o, dt); continue }
            a.intent.reset()
            // research 08: a fish lethargic or on its side from heat lies on the bottom, not at its rest spot
            if (!com.reeflive.core.ai.HeatResponse.abstractPose(a.ctx, a.intent)) a.brain.abstractPose(a.ctx, a.intent)
            if (!a.intent.kinematic) a.intent.place(a.intent.target ?: o.homePos, o.heading)
            a.intent.kinRate = 3.0
            a.steering.apply(a.intent)
            o.attached = true
            o.seat?.update(o, true, crawlSurface, dt, if (a.intent.crawl) a.intent.surfaceOffset else -1.0)
            o.limbRig?.contact(o, crawlSurface, organisms, dt)
        }
        sandContact.step(organisms, dt, displace = false)
    }

    private fun deadPhysics(o: Organism, dt: Double) {
        val buoyant = o.species.buoyantWhenDead ?: (o.brainKind in setOf(BrainKind.CLOWN, BrainKind.TANG, BrainKind.CARDINAL, BrainKind.WRASSE, BrainKind.DOTTYBACK, BrainKind.GENERIC_FISH))
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
        val light = LightSchedule.intensity(lightCh)
        cachedSinceOn = lights.minutesSinceLightsOn(simTimeSec)
        cachedUntilOff = if (cachedSinceOn != null) lights.minutesUntilLightsOff(simTimeSec) else null
        // drivers
        var bio = 0.0
        for (o in organisms) if (o.alive) bio += Math.pow(o.massG, 0.8) * HealthModel.metabolicMult(w.s.tempC) * (if (o.sleeping) 0.7 else 1.0)
        w.bioMassIndex = bio
        repro.step(CHEM_DT)
        SexRoles.step(organisms, CHEM_DT)   // protogynous / bidirectional sex roles (life_stages.md)
        shrimpBreeding.step(CHEM_DT / 60.0)
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
                else -> false
            }
            val blennyExcess = Compatibility.isBlenny(o.brainKind) && w.s.volumeL < Compatibility.BLENNY_FREE_VOLUME_L &&
                organisms.any { it !== o && it.alive && Compatibility.isBlenny(it.brainKind) && (it.arrivalTime < o.arrivalTime || (it.arrivalTime == o.arrivalTime && organisms.indexOf(it) < organisms.indexOf(o))) }
            val ctx = HealthContext(simTimeSec, w.s.volumeL, ichOthers, fishCount > 1, rival, night, flowStrength, light, blennyExcess,
                infected, treatments.modsFor(o, w.s, simTimeSec), treatments.hypo && o.isFish, w.bacteria.pathogenPressure)
            HealthModel.step(o, if (fluid != null && o.alive) localWater(o.pos) else w.s, ctx, dtMin, pendingEventsSink)
            if (o.alive) LifeCycle.step(o, CHEM_DT, w.s.volumeL, simTimeSec, pendingEventsSink)
            if (o.alive) growth(o, dtMin)
        }
        coralGrowth(dtMin)
        if (pests.peppermint > 0 || pests.berghia > 0 || corals.any { it.kind == CoralKind.AIPTASIA }) AiptasiaModel.step(this, dtMin)
        for (c in corals) {
            c.flowSpeed = flow.velocityFast(c.pos).length()
            c.fed *= exp(-dtMin / CORAL_FED_DECAY_MIN)
            val tissueBefore = c.tissue
            CoralSim.step(c, w.s, light * w.clarity, cachedSinceOn, cachedUntilOff, dtMin, simTimeSec, pendingEventsSink, flowStrength, w.bacteria.coralPathogenStress)
            // Dying coral tissue decomposes (die-off cascade, research 05 §11)
            if (c.tissue < tissueBefore) w.addDetritusTanGrams((tissueBefore - c.tissue) * c.kind.biomass * CORAL_TAN_PER_COLONY)
            c.swayPhase = (c.swayPhase + dtMin * 60 * 0.3) % (2 * Math.PI)
        }
        crustaceanMolts(dtMin)
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

    /** Colony growth, Kenya branch drops and neighbour competition (CoralGrowth; coverage gaps C-1, C-2). */
    private fun coralGrowth(dtMin: Double) {
        if (corals.isEmpty()) return
        CoralGrowth.compete(corals, water.s, water.carbonEfficiency)
        val born = ArrayList<Coral>()
        val kenyas = corals.count { it.kind == CoralKind.KENYA && it.tissue > 0.05 }
        for (c in corals) {
            CoralGrowth.step(c, water.s, rock, growthRng, dtMin, kenyas + born.size) { "${c.id}~${nextFragId()}" }?.let { born += it }
        }
        for (f in born) {
            corals += f
            pendingEventsSink += SimEvent.CoralSpawned(f.id.substringBefore('~'), f.id, simTimeSec)
        }
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
        val stunt = if (o.brainKind == BrainKind.TANG && water.s.volumeL < 450 && o.lengthCm > 15) 0.3 else 1.0
        o.lengthCm += g / (365 * 1440) * dtMin * o.health * stunt
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

    private fun crustaceanMolts(dtMin: Double) {
        for (o in organisms) {
            if (!o.alive) continue
            val (lo, hi, dur) = when (o.brainKind) {
                // research 12: adult intermolt ≈ 25–26 d at 26 °C (S); Q10 = 2 temperature scaling (D)
                BrainKind.BANDED_SHRIMP -> ShrimpBreeding.stenopusIntermoltDays(water.s.tempC).let { Triple(0.92 * it, 1.08 * it, 36.0) }
                BrainKind.CLEANER_SHRIMP -> Triple(21.0, 42.0, 36.0)
                BrainKind.SALLY_CRAB -> Triple(28.0, 56.0, 24.0)
                else -> continue
            }
            if (o.nextMoltAt == 0.0) o.nextMoltAt = simTimeSec + rng.range(lo, hi) * 86400
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
                o.nextMoltAt = simTimeSec + rng.range(lo, hi) * 86400
            }
        }
    }

    private fun asterinaPopulation() {
        val stars = organisms.filter { it.alive && it.brainKind == BrainKind.ASTERINA }
        if (stars.isEmpty() || stars.size >= ASTERINA_CAP) return
        // fissiparity: each healthy star splits on average every ~30 days
        for (s in stars) if (s.health > 0.8 && s.hunger < 0.6 && rng.chance(1.0 / (30 * 1440))) {
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

    fun feedPoint(anchor: String?): Vec3 = when (anchor) {
        // Tee feeds by hand at the back middle, right beside the return outlet (video T97 0:01-0:03): the return jet then
        // sweeps the pinch forward and sideways before it sinks
        null -> rock.anchors.pos("HAND_FEED", HAND_FEED).withY(rock.waterLevelCm - 0.3)
        "CAVE_EEL" -> rock.anchors.pos("CAVE_EEL", Vec3(62.0, 3.8, 32.5)) + Vec3(-2.0, 7.0, 5.0)
        "DUSTER_TUBE" -> rock.anchors.pos("DUSTER_TUBE", Vec3(86.0, 4.5, 27.3)) + Vec3(2.0, 12.0, 1.0)
        else -> rock.anchors.pos(anchor, Vec3(41.0, rock.waterLevelCm - 6, 26.0))
    }

    /**
     * Sand bed upkeep once per chemistry tick (SIMULATION §16): slumping and flow smoothing, freshness fading, detritus
     * settling from the column, and fines that stayed suspended reaching the filter sock or skimmer.
     */
    private fun sandTick(dtMin: Double) {
        sand.relax(dtMin, flowStrength, water.s.detritus)
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
            is Command.Maintain -> water.maintain(c.task)
            is Command.AddAiptasia -> AiptasiaModel.seed(this, c.count.coerceIn(0, AiptasiaModel.CAP))
            is Command.RemoveAiptasia -> AiptasiaModel.remove(this, c.count, c.chemical)
            is Command.StartTreatment -> startTreatment(c.kind, c.days ?: c.kind.defaultDays, c.fishIds?.toSet())
            is Command.StopTreatment -> treatments.active[c.kind]?.let { endTreatment(it) }
            is Command.Infect -> (organisms.firstOrNull { it.id == c.id } ?: organisms.filter { it.alive && it.isFish }.let { l -> if (l.isEmpty()) null else l[growthRng.nextInt(l.size)] })
                ?.takeIf { it.alive && it.isFish }?.let { o ->
                    o.disease = c.disease; o.diseaseStage = c.stage.coerceIn(0.01, 1.0)
                    emit(SimEvent.DiseaseOnset(o.id, c.disease, simTimeSec))
                }
            is Command.AddPestControl -> when (c.controller) {
                PestController.PEPPERMINT_SHRIMP -> pests.peppermint = max(0, pests.peppermint + c.count)
                PestController.BERGHIA -> pests.berghia = max(0.0, pests.berghia + c.count)
            }
            is Command.Feed -> { feed(c.kind, c.grams, c.at ?: feedPoint(null)); touchMemory.fed() }
            is Command.WaterChange -> water.waterChange(c.fraction, c.mix)
            is Command.Dose -> {
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
            is Command.RemoveOrganism -> organisms.firstOrNull { it.id == c.id }?.let { removeOrganism(it) }
            is Command.TapGlass -> tapGlass(c.worldPoint)
            is Command.TouchTank -> touchTank(c)
            is Command.Finger -> finger(c)
            is Command.WaterPuff -> waterPuff(c)
            is Command.TouchAnimal -> touchAnimal(c.id)
            is Command.SubmarineMode -> {
                submarine.setActive(c.on, rock, sand)
                submarine.visible = c.visible; submarine.headlight = c.headlight
                if (!c.on || !c.visible) subSense.tick(this, 0.0, agentOf)
            }
            is Command.SteerSubmarine -> submarine.controls = c.controls
            is Command.FloatBag -> bags.float(this, c.speciesId, c.count, c.size, c.sizeCm, c.x, c.z)
            is Command.HurryBags -> { bags.hurry = c.on && bags.floating > 0; if (c.openNow) bags.openAll() }
            is Command.SetSpeed -> speed = c.speed
            is Command.ResetToReality -> resetTo(c.water, c.livestock)
            is Command.SetSize -> organisms.firstOrNull { it.id == c.id }?.let { it.lengthCm = c.sizeCm.coerceIn(0.5, it.species.maxLengthCm * 1.2) }
            is Command.SetPredation -> predationEnabled = c.enabled
            is Command.Shadow -> shadow(c.worldPoint)
            is Command.AddMany -> Stocking.addMany(this, c.speciesId, c.count, c.sizeCm, c.size)
            is Command.ScrapeGlass -> scrapeGlass(c)
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
            if (o.sleeping && o.brainKind == BrainKind.WRASSE) s *= 0.5
            o.startle = max(o.startle, s)
            StressModel.addSocial(o, TAP_SOCIAL * s)
            agents[o]?.brain?.onTap(agents[o]!!.ctx, point, s)
        }
    }

    /** The toy submarine (Submarine mode, [Command.SubmarineMode]) and the animals' perception of it. */
    val submarine = Submarine()
    /** Acclimation bags floating in the tank (sandbox Add life, [Command.FloatBag]). */
    val bags = AcclimationBags()
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
            if (d >= TankTouch.CORAL_CM || !TankTouch.retracts(k.kind)) continue
            k.disturb = max(k.disturb, if (d < TankTouch.ON_CORAL_CM) TankTouch.CORAL_DISTURB_HIT else TankTouch.CORAL_DISTURB)
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
        fun distToPath(q: Vec3): Double {
            val k = ((q - c.point) dot dir).coerceIn(0.0, travel)
            return q.distanceTo(c.point + dir * k)
        }
        // sand under the puff's path lifts (a little cloud, pushed along)
        for (k in 0..3) {
            val q = c.point.lerp(end, k / 3.0)
            val sy = sand.surfaceY(q.x.coerceIn(0.0, rock.lengthCm), q.z.coerceIn(0.0, rock.widthCm))
            if (q.y - sy < 6.0) { sand.puff(q.x, sy + 0.5, q.z, 6, 3.0 + 0.1 * sp, dir.x * sp * 0.3, dir.z * sp * 0.3); break }
        }
        for (k in corals) {
            if (!TankTouch.retracts(k.kind)) continue
            val d = distToPath(k.pos)
            if (d < TankTouch.PUFF_CM) k.disturb = max(k.disturb, TankTouch.PUFF_DISTURB * (1 - d / TankTouch.PUFF_CM))
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

    private fun shadow(point: Vec3) {
        for (o in organisms) if (o.alive && o.pos.distanceTo(point) < 40) agents[o]?.brain?.onShadow(agents[o]!!.ctx, point)
        for (c in corals) if (c.pos.distanceTo(point) < 15) c.disturb = 0.6
    }

    private fun resetTo(w: WaterState, livestock: List<LivestockSeed>) {
        water = WaterSim(config.copy(), w.copy())
        setFlow(true)
        organisms.clear(); agents.clear(); corals.clear(); food.clear(); moltShells.clear(); sandPiles.clear(); sand.reset(); invertEcology.reset(); shrimpBreeding.reset(); touchMemory.clear()
        finger = null; flow.gusts.clear()
        submarine.reset(); subSense.clear(); bags.clear()
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
                ?: rock.anchors.ofType("CORAL").firstOrNull { it.name.contains(sp.coralKind.name.lowercase().take(4)) }
            val pos = (a?.pos ?: Vec3(60.0, 10.0, 20.0)) + (seed.offset ?: Vec3.ZERO)
            val normal = if (seed.offset != null) Vec3.Y else a?.normal ?: Vec3.Y
            corals += Coral(id, sp, seed.nickname, a?.name ?: "", pos, normal, rng.nextDouble() * 6.28, seed.offset ?: Vec3.ZERO)
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
            submarine = if (submarine.active) submarine.pose(subSense.reactions) else null,
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
            corals = corals.map { it.toPose() },
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
                if (simTimeSec - lastSandGridAt > 0.25 * max(speed, 1.0) || sandGridCache == null) { lastSandGridAt = simTimeSec; sandGridCache = sand.gridSnapshot() }
                sandGridCache
            },
            sandClouds = sand.cloudArray(),
            foodMeta = food.exportMeta(simTimeSec),
            pumps = pumpArray(),
            nori = food.nori?.let { n ->
                val cl = n.cloth
                NoriPose(cl.cols, cl.rows, cl.exportNodes(), cl.exportCells(), n.pos.x.toFloat(), n.pos.y.toFloat(), n.pos.z.toFloat(),
                    cl.inward.x.toFloat(), cl.inward.y.toFloat(), cl.inward.z.toFloat())
            },
            gusts = gustArray(),
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
        /** Default drop point of a feed (back-centre surface beside the HOB return, video T97); anchor `HAND_FEED`. */
        val HAND_FEED = Vec3(74.0, TankDims.WATER_LEVEL_CM - 1.0, 4.5)
        /** A finger with no news for this long (sim s at 1×) counts as lifted (a lost touch-up). */
        const val FINGER_TIMEOUT_S = 6.0
        private val EMPTY_FLOATS = FloatArray(0)
        const val CHEM_DT = 60.0
        /** Acute stress input of a full-strength glass tap (cortisol envelope, S:Mathew23). */
        const val TAP_SOCIAL = 0.35
        const val ABSTRACT_SPEED = 240.0
        /** Largest behavior/steering step in sim seconds (stepWall caps dtB here at high speed). */
        const val MAX_BEHAVIOR_DT = 0.5
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
        val SCAVENGERS = setOf(BrainKind.HERMIT, BrainKind.NASSARIUS, BrainKind.BRITTLE_STAR, BrainKind.BANDED_SHRIMP, BrainKind.CLEANER_SHRIMP, BrainKind.SALLY_CRAB)
        private fun clampD(v: Double, lo: Double, hi: Double) = v.coerceIn(lo, hi)
    }
}
