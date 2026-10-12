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
    /** Night-time zooplankton emerging from the rock (06 §1.3; the cardinal's natural food). Particles per hour. */
    var nightPlanktonPerHour = 60.0
    private var planktonAcc = 0.0
    private var lastNightRollDay = Int.MIN_VALUE
    /** Run the behavior layer at full fidelity regardless of speed (tests / SimRunner). */
    var forceFullBehavior = false
    /** Disable the behavior layer entirely (health-only long runs). */
    var behaviorEnabled = true

    private val commands = ConcurrentLinkedQueue<Command>()

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
    private var tickCount = 0L
    private var lastLight = 0.0
    private var grazeAccum = 0.0
    private var idCounter = 1
    val moltShells = ArrayList<Pair<Vec3, Double>>()   // position, born at
    /** Sand piles spat out by burrowing gobies: position on the sand and volume (cm³). */
    val sandPiles = ArrayList<SandPile>()

    /** A goby drops a mouthful of sand at [at]; nearby piles merge (max 8 piles). */
    fun depositSand(at: Vec3, cm3: Double) {
        val p = sandPiles.firstOrNull { it.pos.distanceTo(at) < 3.0 }
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
    val heaterPos = Vec3(rock.lengthCm - 1.5, 30.0, 8.0)

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
        val simDt = speed * dtWall
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
        // Gate on the behavior step, not the sim step: stepWall at 60x/240x advances 3-12 sim s per wall tick with a
        // 0.5 s behavior step (SIMULATION §1.2). Gating on simDt parked every animal at its home spot above 20x.
        val useBehavior = behaviorEnabled && dtB <= MAX_BEHAVIOR_DT + 1e-9
        if (useBehavior) behaviorTick(dtB) else abstractPlacement(min(dtB, MAX_BEHAVIOR_DT))
        if (useBehavior && !abstractMode) nightPlankton(simDt)
        food.step(min(simDt, 1.0), simTimeSec)
        if (food.expiredGrams > 0) water.addDetritusTanGrams(food.expiredGrams * 0.10)
        chemAccum += simDt
        while (chemAccum >= CHEM_DT) {
            chemAccum -= CHEM_DT
            chemTick()
        }
        tickCount++
    }

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
                for (t in organisms.toList()) {
                    if (t === a || !t.alive || !r.targets.contains(t.brainKind.name)) continue
                    if (r.filter == "sleeping" && !(t.isFish && t.species.activity.uppercase() != "NOCTURNAL")) continue
                    var p = r.pEvent
                    if (r.moltWindowH > 0) {
                        val cross = a.brainKind != t.brainKind
                        if (cross && !crossSpeciesShrimpKills) continue
                        if (simTimeSec - t.lastMoltAt > r.moltWindowH * 3600) continue
                        if (a.pos.distanceTo(t.pos) > r.triggerRadius + 2) continue
                    }
                    if (simTimeSec - t.arrivalTime < 7 * 86400) p *= r.newcomerMult
                    if (!rng.chance(p)) continue
                    nightPredation(a, t, r.bout)
                }
            }
        }
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
        // lights-on startle (SIMULATION §4.7)
        for (o in organisms.toList()) {
            if (o !in organisms) continue   // eaten during this tick
            val a = agents[o] ?: continue
            a.ctx.p = perception
            a.ctx.dt = dt
            if (!o.alive) { deadPhysics(o, dt); continue }
            a.intent.reset()
            a.brain.preTick(a.ctx)
            a.brain.utility.tick(a.ctx, a.intent)
            a.steering.apply(a.intent)
            o.attached = a.intent.kinematic || a.intent.crawl
        }
    }

    private fun abstractPlacement(dt: Double) {
        perception.rebuild()
        for (o in organisms) {
            val a = agents[o] ?: continue
            a.ctx.p = perception; a.ctx.dt = dt
            if (!o.alive) { deadPhysics(o, dt); continue }
            a.intent.reset()
            // research 08: a fish lethargic or on its side from heat lies on the bottom, not at its rest spot
            if (!com.reeflive.core.ai.HeatResponse.abstractPose(a.ctx, a.intent)) a.brain.abstractPose(a.ctx, a.intent)
            if (!a.intent.kinematic) a.intent.place(a.intent.target ?: o.homePos, o.heading)
            a.intent.kinRate = 3.0
            a.steering.apply(a.intent)
            o.attached = true
        }
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
        w.lightIntensity = light
        w.coralDemandDkhPerDay = 0.01 + corals.sumOf { it.species.coralKind.demandDkhPerDay * it.tissue }
        w.coralBiomass = 0.3 + corals.sumOf { it.species.coralKind.biomass * it.tissue * (1 - it.bleach * 0.7) }
        if (!behaviorEnabled || abstractMode) abstractGrazing(dtMin)
        w.grazingPerHour = grazeAccum * 3600 / CHEM_DT
        grazeAccum = 0.0
        w.algae.grazers = algaeGrazers()
        w.step(CHEM_DT)
        applyRamps()
        // health
        val night = light < LightSchedule.LIGHTS_ON
        val fishCount = organisms.count { it.alive && it.isFish }
        crowding = com.reeflive.core.ai.PairMatrix.crowding(this); crowdingMult = com.reeflive.core.ai.PairMatrix.crowdingMult(crowding)
        for (o in organisms) {
            val ichOthers = organisms.filter { it !== o && it.alive && it.disease == Disease.ICH }.sumOf { it.diseaseStage }
            val rival = when (o.brainKind) {
                BrainKind.LAWNMOWER_BLENNY -> organisms.any { it !== o && it.alive && (it.brainKind == BrainKind.PEEKER_BLENNY || it.brainKind == BrainKind.LAWNMOWER_BLENNY) }
                BrainKind.PEEKER_BLENNY -> organisms.any { it !== o && it.alive && (it.brainKind == BrainKind.LAWNMOWER_BLENNY || it.brainKind == BrainKind.PEEKER_BLENNY) }
                BrainKind.DOTTYBACK, BrainKind.TANG, BrainKind.EEL -> organisms.any { it !== o && it.alive && it.brainKind == o.brainKind }
                else -> false
            }
            val blennyExcess = Compatibility.isBlenny(o.brainKind) && w.s.volumeL < Compatibility.BLENNY_FREE_VOLUME_L &&
                organisms.any { it !== o && it.alive && Compatibility.isBlenny(it.brainKind) && (it.arrivalTime < o.arrivalTime || (it.arrivalTime == o.arrivalTime && organisms.indexOf(it) < organisms.indexOf(o))) }
            val ctx = HealthContext(simTimeSec, w.s.volumeL, ichOthers, fishCount > 1, rival, night, flowStrength, light, blennyExcess)
            HealthModel.step(o, if (fluid != null && o.alive) localWater(o.pos) else w.s, ctx, dtMin, pendingEventsSink)
            if (o.alive) LifeCycle.step(o, CHEM_DT, w.s.volumeL, simTimeSec, pendingEventsSink)
            if (o.alive) growth(o, dtMin)
        }
        for (c in corals) {
            c.flowSpeed = flow.velocityFast(c.pos).length()
            val tissueBefore = c.tissue
            CoralSim.step(c, w.s, light, cachedSinceOn, cachedUntilOff, dtMin, simTimeSec, pendingEventsSink, flowStrength)
            // Dying coral tissue decomposes (die-off cascade, research 05 §11)
            if (c.tissue < tissueBefore) w.addDetritusTanGrams((tissueBefore - c.tissue) * c.kind.biomass * CORAL_TAN_PER_COLONY)
            c.swayPhase = (c.swayPhase + dtMin * 60 * 0.3) % 6283.0
        }
        crustaceanMolts(dtMin)
        dinoToxin(dtMin)
        nightRolls()
        // co-residents grow familiar at 0.15/day even while the behaviour layer is off (06 §3.3)
        if (!behaviorEnabled || abstractMode) {
            val gain = com.reeflive.core.ai.Social.FAMILIARITY_PER_DAY * CHEM_DT / 86400
            for (o in organisms) if (o.alive) for (r in o.relations.values) r.familiarity = min(1.0, r.familiarity + gain)
        }
        asterinaPopulation()
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
            if (rng.chance(dtMin / 3.0)) depositSand(b.outside(rng.range(4.0, 9.0)) + Vec3(rng.signed() * 4, 0.0, rng.signed() * 2), com.reeflive.core.ai.GobyBrain.GOBY_MOUTHFUL_CM3)
        }
        flushEvents()
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
                BrainKind.BANDED_SHRIMP -> Triple(21.0, 56.0, 36.0)
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
            if (age < 24 * 3600) water.addDetritusTanGrams(o.massG * CORPSE_TAN_PER_G * dtMin / (24 * 60))
            if (age > 48 * 3600) { removed += o; it.remove() }
        }
        removed.forEach { agents.remove(it) }
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
        null -> rock.anchors.pos("SURFACE_FEED", Vec3(41.0, 55.0, 20.0)).withY(rock.waterLevelCm - 0.3)
        "CAVE_EEL" -> rock.anchors.pos("CAVE_EEL", Vec3(69.0, 5.0, 23.0)) + Vec3(-2.0, 7.0, 5.0)
        "DUSTER_TUBE" -> rock.anchors.pos("DUSTER_TUBE", Vec3(15.0, 3.0, 27.0)) + Vec3(2.0, 12.0, 1.0)
        else -> rock.anchors.pos(anchor, Vec3(41.0, 50.0, 20.0))
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
            is Command.RearrangeRock -> rearrangeRock()
            is Command.SetAcclimation -> for (o in organisms) if (o.alive) {
                StressModel.acclimate(o, water.s, lightIntensity, flowStrength, c.paramId)
                if (c.paramId == null || c.paramId.equals("temp", true)) HeatModel.acclimate(o, water.s.tempC)
            }
            is Command.Photoperiod -> lights.photoperiodHours = c.hours.coerceIn(4.0, 16.0)
            is Command.Feed -> feed(c.kind, c.grams, c.at ?: feedPoint(null))
            is Command.WaterChange -> water.waterChange(c.fraction, c.mix)
            is Command.Dose -> {
                water.dose(c.paramId, c.delta)
                // the dose enters at the HOB return and spreads through the flow before it is well mixed
                fluid?.let { f -> doseTracerParam = c.paramId; f.injectDose(rock.anchors.pos("HOB_OUTFLOW", Vec3(71.0, 54.0, 3.0)) + Vec3(0.0, -2.0, 4.0), c.delta, 1.0) }
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
            is Command.SetSpeed -> speed = c.speed
            is Command.ResetToReality -> resetTo(c.water, c.livestock)
            is Command.SetSize -> organisms.firstOrNull { it.id == c.id }?.let { it.lengthCm = c.sizeCm.coerceIn(0.5, it.species.maxLengthCm * 1.2) }
            is Command.SetPredation -> predationEnabled = c.enabled
            is Command.Shadow -> shadow(c.worldPoint)
        }
    }

    /** Current value of a rampable parameter. */
    fun paramValue(id: String): Double? = when (id.lowercase()) {
        "light" -> lights.intensityOverride ?: 1.0
        "flow" -> water.flowStrength
        "photoperiod" -> lights.photoperiodHours ?: LightSchedule.DEFAULT_PHOTOPERIOD_H
        "roomtemp" -> config.roomC
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
        for (o in organisms) {
            for (r in o.relations.values) { r.familiarity = min(r.familiarity, 0.5); r.intrusions = 0.0 }
            agents[o]?.brain?.utility?.behaviors?.forEach { (it as? com.reeflive.core.ai.PairBouts)?.reset() }
        }
        emit(SimEvent.TerritoryReset(simTimeSec))
    }

    private fun rebuildWater(): WaterSim {
        val old = water
        return WaterSim(config, old.s.copy()).also { nw ->
            nw.algae.copyFrom(old.algae)
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
        water.addFeed(grams * kind.nutrientFactor)
        emit(SimEvent.Fed(kind, grams, simTimeSec))
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
                !(o.brainKind == BrainKind.FEATHER_DUSTER && !kind.suspended)
        }.sortedByDescending { it.aggression + it.boldness + (if (it.role == "FEMALE") 0.3 else 0.0) + it.hunger + rng.nextDouble() * 0.3 + (it.species.foodPreference(kind) - it.species.dietMatch(kind)) }
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

    private fun shadow(point: Vec3) {
        for (o in organisms) if (o.alive && o.pos.distanceTo(point) < 40) agents[o]?.brain?.onShadow(agents[o]!!.ctx, point)
        for (c in corals) if (c.pos.distanceTo(point) < 15) c.disturb = 0.6
    }

    private fun resetTo(w: WaterState, livestock: List<LivestockSeed>) {
        water = WaterSim(config.copy(), w.copy())
        setFlow(true)
        organisms.clear(); agents.clear(); corals.clear(); food.clear(); moltShells.clear(); sandPiles.clear()
        lights.forcedOn = null; lights.intensityOverride = null
        predationEnabled = config.predationEnabled
        livestock.forEach { addFromSeed(it) }
    }

    // ============================================================== organisms
    fun addFromSeed(seed: LivestockSeed): Organism {
        val sp = species[seed.speciesId]
        val id = seed.id.ifBlank { "${sp.id}#${idCounter++}" }
        if (sp.isCoral) {
            val a = seed.placement?.let { rock.anchors[it] ?: rock.anchors["coral_$it"] }
                ?: rock.anchors.ofType("CORAL").firstOrNull { it.name.contains(sp.coralKind.name.lowercase().take(4)) }
            val pos = a?.pos ?: Vec3(60.0, 10.0, 20.0)
            corals += Coral(id, sp, seed.nickname, a?.name ?: "", pos, a?.normal ?: Vec3.Y, rng.nextDouble() * 6.28)
            return Organism(id, sp, 1.0, seed.nickname, null, rng.fork(id.hashCode().toLong()))   // placeholder (not added)
        }
        val size = if (seed.sizeCm > 0) seed.sizeCm else sp.typicalAdultCm.coerceAtMost(sp.maxLengthCm)
        val o = Organism(id, sp, size, seed.nickname, seed.role, rng.fork(id.hashCode().toLong()), seed.placement, seed.variant)
        o.hunger = 0.25 + rng.nextDouble() * 0.2
        o.ageSec = LifeCycle.initialAgeSec(o, seed.ageYears)
        if (o.brainKind == BrainKind.WRASSE || o.brainKind == BrainKind.LAWNMOWER_BLENNY) o.acceptsPrepared = rng.chance(0.7)
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
        return organisms.map { o ->
            val d = com.reeflive.core.ai.Caption.describe(o, this)
            o.stats(d.caption, d.intent, d.targetId ?: o.focusId, com.reeflive.core.ai.Social.summarize(o, byId, simTimeSec))
        }
    }

    fun snapshot(): SimSnapshot {
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
        )
    }

    // Initialised last so every property above exists before livestock is added.
    init {
        livestock.forEach { addFromSeed(it) }
        updateLights()
        lastLight = perception.light
        perception.rebuild()
        latest = snapshot()
    }

    companion object {
        const val CHEM_DT = 60.0
        /** Acute stress input of a full-strength glass tap (cortisol envelope, S:Mathew23). */
        const val TAP_SOCIAL = 0.35
        const val ABSTRACT_SPEED = 240.0
        /** Largest behavior/steering step in sim seconds (stepWall caps dtB here at high speed). */
        const val MAX_BEHAVIOR_DT = 0.5
        const val CORPSE_TAN_PER_G = 0.02
        /** g TAN-equivalent released by a full colony's tissue per unit CoralKind.biomass. */
        const val CORAL_TAN_PER_COLONY = 2.0
        const val ASTERINA_CAP = 4
        val SCAVENGERS = setOf(BrainKind.HERMIT, BrainKind.NASSARIUS, BrainKind.BRITTLE_STAR, BrainKind.BANDED_SHRIMP, BrainKind.CLEANER_SHRIMP, BrainKind.SALLY_CRAB)
        private fun clampD(v: Double, lo: Double, hi: Double) = v.coerceIn(lo, hi)
    }
}
