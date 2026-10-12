package com.reeflive.core.sim

import com.reeflive.core.ai.UtilityBrain
import com.reeflive.core.geom.BodySpline
import com.reeflive.core.geom.Quat
import com.reeflive.core.geom.Rng
import com.reeflive.core.geom.Vec3
import kotlin.math.pow

/**
 * One animal: health state (SIMULATION §3.1) plus kinematics and render outputs.
 * Temperament values carry ±15 % per-individual noise (PLAN risks: "fish AI looks robotic").
 */
class Organism(
    val id: String,
    val species: SimSpecies,
    lengthCm: Double,
    val nickname: String,
    val role: String?,
    val rng: Rng,
    val placement: String? = null,
    val variant: String? = null,
) {
    val brainKind: BrainKind = species.brainKind

    var lengthCm: Double = lengthCm
        set(v) { field = v.coerceAtLeast(0.3); spline.length = field }

    // ---- temperament (±15 %) ----
    private fun jitter(v: Double) = (v * (1 + 0.15 * rng.signed())).coerceIn(0.0, 1.0)
    var aggression = jitter(species.temperament.aggression)
    var boldness = jitter(species.temperament.boldness)
    var curiosity = jitter(species.temperament.curiosity)
    var skittishness = jitter(species.temperament.skittishness)

    // ---- health (SIMULATION §3.1) ----
    var hunger = 0.3
    var stress = 0.0
    var chronic = 0.0
    var immunity = 1.0
    var health = 1.0
    var disease: Disease? = null
    var diseaseStage = 0.0
    var hlle = 0.0
    var condition = 1.0
    var alive = true
    var deadSince: Double? = null
    var deathCause: String? = null
    var mood = Mood.CALM
    val symptoms: MutableSet<Symptom> = LinkedHashSet()
    /** Per-parameter environmental stress contributions from the last chem tick (UI "why"). */
    val envStress: MutableMap<String, Double> = LinkedHashMap()
    var lethalExposure = false
    /** How far past the worst lethal bound (in tolerance spans) the last chem tick was. */
    var lethalOvershoot = 0.0

    // ---- social memory (SIMULATION §9.6) ----
    val relations: MutableMap<String, com.reeflive.core.ai.Relation> = HashMap()
    /** Individual this animal is currently wary of (refreshed by Social.perceive) and how much. */
    var wary: Organism? = null
    var waryLevel = 0.0
    /** Id of the organism the current behavior is about (chase target, cleaning client, prey…); null = none. */
    var focusId: String? = null
    /** Per-individual utility noise amplitude (temperament seed): some individuals are more capricious. */
    val whim: Double = 0.025 + 0.03 * rng.nextDouble()
    /** Acclimated temperature / SG / pH (rate-of-change shock, SIMULATION §9.6); NaN until the first chem tick. */
    var acclTemp = Double.NaN
    var acclSg = Double.NaN
    var acclPh = Double.NaN

    // ---- research 06 (SIMULATION §9.10) ----
    /** Per-channel acute/chronic stress and acclimated setpoints (StressModel). */
    val stressState = StressModel.State()
    /** Acute heat damage and stage (research 08, HeatModel). */
    val heat = HeatModel.State()
    /** Sim time this animal entered the tank (newcomer priority effects, 06 §3.3). Initial stock: 30 days before start. */
    var arrivalTime = -30 * 86400.0
    /** Tactile cleaning calms an aggressor: initiated aggression ×[AGG_DAMP] until this time (06 §5.5, S:Cheney08). */
    var aggressionDampUntil = -1e9
    /** Cardinal/tailspot keep closer to cover for 10 min after being chased (06 §5.4). */
    var timidUntil = -1e9
    /** A jolted client chases the cleaner until then (06 §5.5). */
    var retaliateUntil = -1e9
    /** Client pose reaction requested by a cleaner (06 §5.5): posing until this time for [poseFor]. */
    var poseUntil = -1e9
    var poseFor: Organism? = null
    /** Display-sequence render hooks (06 §5.4): quiver (clown ♂), spine flare (tang), dorsal raise, leg-rock (shrimp). */
    var quiver = 0.0
    var spineFlare = 0.0
    var dorsalRaise = 0.0
    var legRock = 0.0
    /** Rolling 1 h observation counters (06 §4.2): see [Counters]. */
    val counters = Counters()
    /** Cardinal out of cover under bright light (set by its brain; fin-clamp sign). */
    var brightExposed = false
    /** Water temperature at the last chemistry tick (appetite hook). */
    var lastTempC = 26.0
    /** Sim time of this animal's last behaviour tick (NaN until the first); used for memory timestamps. */
    var clock = Double.NaN
    /** Daytime hover / forage radius multiplier set by its brain (cardinal newcomer and timid states; 06 §5.6 #7). */
    var hoverRadius = 0.0

    // transient stress sources (decay per minute in HealthModel)
    var socialStress = 0.0
    var startle = 0.0
    var nutritionGreens = 1.0     // 0..1, decays without algae/nori (HLLE input)
    var acceptsPrepared = true    // cleaner wrasse roll (SIMULATION §3.4); lawnmower blenny too
    var lastCleanedAt = -1e9
    /** Last tactile pass or inspection by a cleaner (06 §5.6 #4: nobody goes > 90 min without one by day). */
    var lastCleanerContactAt = -1e9
    var parasiteLoad = 0.0        // cosmetic client parasite level (feeds cleaners)
    var satietyUntil = -1e9       // eel 3-day satiety after a kill
    var molting = false
    /** Sim time the last molt began (molt-window predation, 06 §2.14). */
    var lastMoltAt = -1e9
    var moltUntil = 0.0
    var nextMoltAt = 0.0

    // ---- kinematics ----
    var pos = Vec3.ZERO
    var vel = Vec3.ZERO
    var heading = Vec3.X
    var up = Vec3.Y
    var attached = false          // kinematically placed by the behavior (perch, hole, den, tube…)
    var sleeping = false
    var hostAnchor: String? = placement
    var homePos: Vec3 = Vec3.ZERO

    // ---- render outputs ----
    var swimPhase = 0.0
    var tailHz = 0.0
    var amp = 0.0
    var pectoral = 0.0
    var finClamp = 0.0
    var gill = 0.3
    var gillRate = 1.0
    var desat = 0.0
    var ich = 0.0
    var thin = 0.0
    var eyeCloud = 0.0
    var eyeYaw = 0.0
    var mouth = 0.0
    var posture = 0
    var appendagePhase = rng.nextDouble() * 6.28
    var extension = 1.0
    var visible = true
    var extraRoll = 0.0
    var pitchBias = 0.0

    val spline = BodySpline(lengthCm, rigid = brainKind.isBenthic && brainKind != BrainKind.BRITTLE_STAR)
    lateinit var brain: UtilityBrain
    /** Heat stages from LETHARGIC on override the brain's label (abstract mode never ticks the brain). */
    val behaviorName: String get() = when {
        !alive -> "Dead"
        heat.stage >= HeatModel.Stage.LETHARGIC -> HeatModel.label(this)
        ::brain.isInitialized -> brain.currentLabel
        else -> ""
    }

    val massG: Double get() = ChemFns.massGrams(lengthCm, species.massCoeff)
    val isFish: Boolean get() = brainKind.isFish
    /** Collision length: real BL, capped at 6 cm for eels so the clearance follows body girth, not length (SIMULATION §8.7). */
    val collisionLength: Double get() = when (brainKind) { BrainKind.EEL -> lengthCm.coerceAtMost(6.0); BrainKind.ENGINEER_GOBY -> lengthCm.coerceAtMost(3.0); else -> lengthCm }
    val cruiseSpeed: Double get() = species.locomotion.cruiseBL * lengthCm
    val burstSpeed: Double get() = species.locomotion.burstBL * lengthCm
    /** Meal size in grams (one meal takes hunger down by 0.8): 0.012 × mass^0.8 (tune). */
    val mealGrams: Double get() = (0.012 * massG.pow(0.8)).coerceAtLeast(0.004)

    fun eat(grams: Double) {
        hunger = (hunger - 0.8 * grams / mealGrams).coerceAtLeast(0.0)
    }

    /** Spine and frame buffers reused by [toPose]. */
    private val spinePos = Array(BodySpline.POINTS) { Vec3.ZERO }
    private val spineRot = Array(BodySpline.POINTS) { Quat.IDENTITY }

    fun placeAt(p: Vec3, dir: Vec3) {
        pos = p; heading = dir.normalized(); vel = Vec3.ZERO
        spline.reset(p, heading)
    }

    fun toPose(): OrganismPose {
        spline.sample(up, spinePos, spineRot)
        val sp = FloatArray(BodySpline.POINTS * 3)
        val fr = FloatArray(BodySpline.POINTS * 4)
        for (i in 0 until BodySpline.POINTS) {
            val p = spinePos[i]; val q = spineRot[i]
            sp[i * 3] = p.x.toFloat(); sp[i * 3 + 1] = p.y.toFloat(); sp[i * 3 + 2] = p.z.toFloat()
            fr[i * 4] = q.x.toFloat(); fr[i * 4 + 1] = q.y.toFloat(); fr[i * 4 + 2] = q.z.toFloat(); fr[i * 4 + 3] = q.w.toFloat()
        }
        return OrganismPose(
            id, species.id, sp, fr, swimPhase.toFloat(), amp.toFloat(), species.locomotion.waveLen.toFloat(),
            pectoral.toFloat(), finClamp.toFloat(), gill.toFloat(), gillRate.toFloat(), desat.toFloat(), ich.toFloat(),
            thin.toFloat(), eyeCloud.toFloat(), alive, lengthCm.toFloat(),
            brain = brainKind.name, posture = posture, eyeYaw = eyeYaw.toFloat(), mouth = mouth.toFloat(),
            appendagePhase = appendagePhase.toFloat(), extension = extension.toFloat(), visible = visible, nickname = nickname,
            variant = variant,
        )
    }

    fun stats(
        caption: String = behaviorName,
        intent: String = behaviorName.replaceFirstChar { it.lowercase() },
        intentTargetId: String? = focusId,
        relations: List<RelationInfo> = emptyList(),
    ): OrganismStats = OrganismStats(
        id, nickname, species.id, species.common, health.toFloat(), hunger.toFloat(), stress.toFloat(), mood,
        behaviorName, symptoms.toList(), disease, diseaseStage.toFloat(), alive, lengthCm.toFloat(), temperamentTags(),
        caption = caption, focusId = focusId, envStress = envStress.toMap(), cause = deathCause,
        intent = intent, intentTargetId = intentTargetId, relations = relations, waryOfId = wary?.id,
    )

    fun temperamentTags(): List<String> = buildList {
        if (boldness > 0.6) add("Bold") else if (boldness < 0.35) add("Shy")
        if (curiosity > 0.6) add("Curious")
        if (aggression > 0.45) add("Territorial")
        if (skittishness > 0.6) add("Skittish")
        if (species.schooling) add("Schooling")
        if (species.activity.equals("NOCTURNAL", true)) add("Nocturnal")
        if (species.activity.equals("CREPUSCULAR", true)) add("Dawn and dusk")
    }

    override fun toString() = "$nickname(${species.id})"
}

/**
 * Rolling observation counters (research 06 §4.2): event counts in the last hour of sim time,
 * kept as two half-hour buckets so the window is cheap to maintain. Totals are lifetime counts.
 */
class Counters {
    enum class K { CHASE_GIVEN, CHASE_RECEIVED, POSE, JOLT, FLASH, GASP, FEED, QUIVER, CLEAN_GIVEN, NEAR_MISS }
    private val cur = IntArray(K.entries.size)
    private val prev = IntArray(K.entries.size)
    val total = LongArray(K.entries.size)
    private var bucketStart = 0.0
    /** Seconds spent in cover (posture 3/4/6 or hiding) in the current and previous half hour. */
    private var coverCur = 0.0
    private var coverPrev = 0.0

    private fun roll(t: Double) {
        if (t - bucketStart < 1800) return
        val steps = ((t - bucketStart) / 1800).toInt()
        if (steps >= 2) { cur.copyInto(prev); prev.fill(0); cur.fill(0); coverPrev = 0.0; coverCur = 0.0 }
        else { cur.copyInto(prev); cur.fill(0); coverPrev = coverCur; coverCur = 0.0 }
        bucketStart += steps * 1800.0
    }

    fun add(k: K, t: Double, n: Int = 1) { roll(t); cur[k.ordinal] += n; total[k.ordinal] += n.toLong() }
    fun cover(dt: Double, t: Double) { roll(t); coverCur += dt }
    fun lastHour(k: K, t: Double): Int { roll(t); return cur[k.ordinal] + prev[k.ordinal] }
    fun coverFraction(t: Double): Double { roll(t); val span = 1800 + (t - bucketStart); return ((coverCur + coverPrev) / span).coerceIn(0.0, 1.0) }
    fun totalOf(k: K): Long = total[k.ordinal]
}
