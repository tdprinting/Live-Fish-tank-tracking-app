package com.reeflive.core.sim

import com.reeflive.core.ai.rockSurfacePoint
import com.reeflive.core.geom.Rng
import com.reeflive.core.geom.Vec3
import kotlin.math.roundToInt

/** One egg clutch on the rock (clown nest). Age and hatch are on the sim clock; [untendedSec] counts neglect. */
class EggClutch(
    val id: String,
    val kind: String,
    val pos: Vec3,
    val normal: Vec3,
    val laidAt: Double,
    /** Age in days at which the larvae hatch (research 28 §1: ~7 d at 28.5 °C to ~10 d at 26 °C). */
    val hatchDays: Double,
    var tenderId: String?,
    /** Eggs laid (research 28 §1: 100-1000, more as the female grows). */
    val eggs: Int = 300,
) {
    var untendedSec = 0.0
    fun ageDays(now: Double) = (now - laidAt) / 86400.0
}

/**
 * Breeding and sex change (coverage_gaps B-1, B-2, B-3), stepped once per chemistry tick (60 s) so it works in
 * abstract mode too. The brains only add the visible behaviours (cleaning the site, laying, fanning); the timers and
 * outcomes live here. All randomness comes from a [Rng] seeded from the tank seed, so runs stay deterministic.
 */
class Reproduction(private val sim: Simulation) {
    enum class Phase { IDLE, CLEANING, LAYING }

    private val rng = Rng(sim.config.seed * 31 + 0x5EED)

    // ---- clown pair (B-1) ----
    var clownPhase = Phase.IDLE; private set
    private var clownReadiness = 0.0
    private var clownPhaseUntil = 0.0
    private var clownCooldownUntil = 0.0
    /** Nest site chosen for the current spawning (rock surface near the host) and its outward normal. */
    var site: Vec3? = null; private set
    var siteNormal: Vec3 = Vec3.Y; private set

    val clutches = ArrayList<EggClutch>()
    /** Total larvae batches released at hatching (they vanish: no larval rearing). */
    var hatchedClutches = 0; private set
    var lostClutches = 0; private set
    private var clutchCounter = 1
    /** The keeper asked for a spawning now (sandbox "Spawn now"): readiness, cooldown and fitness are skipped once. */
    private var forced = false

    /** A clutch from a My tank save (its male is found again on the next step). */
    fun restoreClutch(c: SavedClutch) {
        clutches += EggClutch("eggs#${clutchCounter++}", c.kind, Vec3(c.x, c.y, c.z), Vec3(c.nx, c.ny, c.nz), c.laidAt, c.hatchDays, null, c.eggs)
    }

    fun clownClutch(): EggClutch? = clutches.firstOrNull { it.kind == CLOWN }

    fun poses(): List<EggClutchPose> = clutches.map {
        EggClutchPose(it.id, it.kind, it.pos, it.normal, it.ageDays(sim.simTimeSec).toFloat(), it.hatchDays.toFloat(), it.tenderId, it.eggs)
    }

    fun step(dtSec: Double) {
        val now = sim.simTimeSec
        clownSexChange(dtSec)
        clownBreeding(now, dtSec)
        cardinalBreeding(now, dtSec)
        tangBreeding(now, dtSec)
        wrasseHarem(dtSec)
    }

    // ============================================================== wrasse harem (B-3)

    /**
     * Cleaner wrasse are protogynous: all start female, and with more than one present the largest becomes
     * the male over [WRASSE_CHANGE_DAYS] (research 02 §4C). A single wrasse stays as it is; if the male
     * goes, the next largest takes over.
     */
    private fun wrasseHarem(dtSec: Double) {
        val w = sim.organisms.filter { it.alive && it.brainKind == BrainKind.WRASSE }
        if (w.size < 2 || w.any { it.sexFemale == false }) { for (o in w) if (o.sexFemale != false) o.sexChange = maxOf(0.0, o.sexChange - dtSec / (WRASSE_CHANGE_DAYS * 86400.0)); return }
        val big = w.maxBy { it.lengthCm }
        for (o in w) if (o !== big) o.sexChange = 0.0
        big.sexChange += dtSec / (WRASSE_CHANGE_DAYS * 86400.0)
        if (big.sexChange >= 1.0) { big.sexChange = 0.0; big.sexFemale = false }
    }

    // ============================================================== clown sex and sex change

    private fun clowns() = sim.organisms.filter { it.alive && it.brainKind == BrainKind.CLOWN }

    /** Resolve an unspecified sex from the sheet role, then the partner's role, then size (the larger is the female). */
    fun clownIsFemale(o: Organism): Boolean {
        o.sexFemale?.let { return it }
        if (o.role != null) return o.role.equals("FEMALE", true)
        val others = clowns().filter { it !== o }
        if (others.isEmpty()) return true
        if (others.any { it.role.equals("FEMALE", true) }) return false
        return o.lengthCm >= others.maxOf { it.lengthCm }
    }

    /**
     * Protandry: a male stays male while a female lives. With the female gone the largest male turns
     * female over [SEX_CHANGE_DAYS] (research 02 §1F); a female that returns resets the change.
     */
    private fun clownSexChange(dtSec: Double) {
        val cl = clowns()
        // a baby stays undifferentiated until it is big enough to join the hierarchy (Buston 2003: the pair suppresses it)
        if (cl.size >= 2) for (o in cl) if (o.sexFemale == null && o.lengthCm >= MALE_MATURE_CM) o.sexFemale = clownIsFemale(o)
        val males = cl.filter { it.sexFemale == false }
        val hasFemale = cl.any { it.sexFemale == true || it.sexFemale == null && clownIsFemale(it) }
        if (males.isEmpty()) return
        if (hasFemale) { for (m in males) m.sexChange = maxOf(0.0, m.sexChange - dtSec / (SEX_CHANGE_DAYS * 86400.0)); return }
        val m = males.maxBy { it.lengthCm }
        m.sexChange += dtSec / (SEX_CHANGE_DAYS * 86400.0)
        if (m.sexChange >= 1.0) { m.sexChange = 0.0; m.sexFemale = true }
    }

    // ============================================================== clown spawning

    private fun inRange(w: WaterState) =
        w.tempC in 24.0..28.5 && w.sg in 1.020..1.028 && w.ph in 7.9..8.5 && w.nh3Free < 0.05 && w.no3 < 40.0

    /** Stable, fed, healthy: a pair that can spawn (research 02 §1C: stable, well-fed, mature). */
    private fun fit(o: Organism, minCm: Double, now: Double) =
        o.alive && o.lengthCm >= minCm && o.hunger < 0.55 && o.stress < 0.35 && o.health > 0.85 &&
            o.disease == null && now - o.arrivalTime > PAIR_BOND_DAYS * 86400.0

    private fun pairReady(f: Organism, m: Organism, now: Double) =
        fit(f, FEMALE_MATURE_CM, now) && fit(m, MALE_MATURE_CM, now) && inRange(sim.water.s)

    private fun clownBreeding(now: Double, dt: Double) {
        val cl = clowns()
        val (f, m) = clownPair(cl)
        tendClutch(now, dt, m, f)
        if (f == null || m == null) { if (clownPhase != Phase.IDLE) abortSpawning(); clownReadiness = maxOf(0.0, clownReadiness - dt / 86400.0); sim.brood.forceSpawn = false; return }
        if (sim.brood.forceSpawn) {
            sim.brood.forceSpawn = false
            if (clownClutch() == null && clownPhase == Phase.IDLE) {
                forced = true; clownReadiness = 1.0; clownCooldownUntil = 0.0
                startCleaning(f, now, 0.3)
                return
            }
        }
        val ok = forced || pairReady(f, m, now)
        // the female builds the next clutch while the male tends this one (research 28 §1: a 12-15 day cycle)
        if (clownClutch() != null) {
            clownReadiness = (clownReadiness + if (ok) dt / (READY_DAYS * 86400.0) else -dt / (READY_DAYS * 86400.0 / 4)).coerceIn(0.0, 1.0)
            return
        }
        when (clownPhase) {
            Phase.IDLE -> {
                clownReadiness = (clownReadiness + if (ok) dt / (READY_DAYS * 86400.0) else -dt / (READY_DAYS * 86400.0 / 4)).coerceIn(0.0, 1.0)
                if (clownReadiness >= 1.0 && now >= clownCooldownUntil && !sim.perception.night) startCleaning(f, now, 1.0)
            }
            Phase.CLEANING -> if (!ok) abortSpawning() else if (now >= clownPhaseUntil) {
                clownPhase = Phase.LAYING
                clownPhaseUntil = now + rng.range(0.5, 1.0) * 3600
            }
            Phase.LAYING -> if (!ok) abortSpawning() else if (now >= clownPhaseUntil) layClutch(now, m, f)
        }
    }

    /** The breeding female (largest female) and male (largest mature non-female). */
    private fun clownPair(cl: List<Organism>): Pair<Organism?, Organism?> {
        val f = cl.filter { it.sexFemale ?: clownIsFemale(it) }.maxByOrNull { it.lengthCm }
        val m = cl.filter { it !== f && it.lengthCm >= MALE_MATURE_CM && !(it.sexFemale ?: clownIsFemale(it)) }.maxByOrNull { it.lengthCm }
        return f to m
    }

    fun hasClownPair(): Boolean = clownPair(clowns()).let { it.first != null && it.second != null }

    private fun startCleaning(f: Organism, now: Double, speed: Double) {
        val host = sim.agentOf(f)?.brain?.anchorPos(sim.agentOf(f)!!.ctx, "host") ?: f.homePos
        pickSite(f, host)
        clownPhase = Phase.CLEANING
        clownPhaseUntil = now + rng.range(2.0, 4.0) * 3600 * speed
    }

    /** What the pair is waiting for, in the card's words. */
    fun clownWaitLine(): String {
        val now = sim.simTimeSec
        val (f, m) = clownPair(clowns())
        if (f == null || m == null) return if (clowns().size < 2) "Clownfish need a partner to breed." else "The clowns are too young to pair up yet."
        val name = { o: Organism -> o.nickname.ifBlank { "the ${if (o === f) "female" else "male"}" } }
        val why = buildList {
            for (o in listOf(f, m)) {
                if (o.lengthCm < (if (o === f) FEMALE_MATURE_CM else MALE_MATURE_CM)) add("${name(o)} is still growing")
                else if (now - o.arrivalTime < PAIR_BOND_DAYS * 86400.0) add("${name(o)} is still settling in")
                else if (o.hunger >= 0.55) add("${name(o)} is hungry")
                else if (o.stress >= 0.35 || o.health <= 0.85 || o.disease != null) add("${name(o)} is not feeling great")
            }
            if (!inRange(sim.water.s)) add("the water is not right for spawning")
        }
        if (why.isNotEmpty()) return "Not ready to spawn: ${why.distinct().joinToString(", ")}."
        val days = maxOf((1.0 - clownReadiness) * READY_DAYS, (clownCooldownUntil - now) / 86400.0)
        return if (days < 1) "${name(f)} and ${name(m)} look ready to spawn soon." else "${name(f)} and ${name(m)} are a pair. Next eggs in about ${days.toInt() + 1} days."
    }

    private fun abortSpawning() { clownPhase = Phase.IDLE; clownReadiness = 0.5; site = null; forced = false }

    private fun pickSite(f: Organism, host: Vec3) {
        val ctx = sim.agentOf(f)?.ctx
        val hit = ctx?.rockSurfacePoint(host, 9.0)
        site = hit?.first ?: Vec3(host.x + 4.0, sim.rock.sandTopCm + 0.5, host.z)
        siteNormal = hit?.second ?: Vec3.Y
    }

    private fun layClutch(now: Double, male: Organism, female: Organism) {
        val s = site ?: male.homePos
        val spec = BroodSpecs.CLOWN
        val n = clutchCounter++
        val eggs = sim.brood.clutchSize(spec, female.lengthCm, n)
        clutches += EggClutch("eggs#$n", CLOWN, s, siteNormal, now, sim.brood.hatchDays(spec, sim.water.s.tempC), male.id, eggs)
        clownPhase = Phase.IDLE; clownReadiness = 0.0; site = null; forced = false
        sim.emit(SimEvent.EggsLaid(spec.key, eggs, now))
    }

    /** The male's fanning keeps the eggs alive; neglect for [UNTENDED_LOSS_HOURS] kills them. Hatching waits for night. */
    private fun tendClutch(now: Double, dt: Double, male: Organism?, female: Organism?) {
        val it = clutches.iterator()
        while (it.hasNext()) {
            val c = it.next()
            if (c.kind != CLOWN) continue
            c.tenderId = male?.id
            val away = !sim.behaviorEnabled || sim.abstractMode
            val tended = male != null && (away || male.pos.distanceTo(c.pos) < 15.0)
            if (!tended) c.untendedSec += dt
            if (c.untendedSec > UNTENDED_LOSS_HOURS * 3600) {
                it.remove(); lostClutches++; clownCooldownUntil = now + 3 * 86400.0
                sim.water.addDetritusTanGrams(c.eggs * 0.00001)   // unguarded eggs fungus over and are picked off
                continue
            }
            if (c.ageDays(now) >= c.hatchDays && sim.perception.night) {
                it.remove(); hatchedClutches++
                clownCooldownUntil = now + rng.range(COOLDOWN_DAYS_MIN, COOLDOWN_DAYS_MAX) * 86400.0
                sim.brood.hatch(BroodSpecs.CLOWN, c.eggs, c.pos + c.normal * 1.0, listOfNotNull(female, male))
            }
        }
    }

    // ============================================================== cardinal mouthbrooding (B-2)

    private var cardReadiness = 0.0
    private var cardCooldownUntil = 0.0
    private var brooder: Organism? = null
    private var broodEndAt = 0.0
    /** Broods released at hatching / lost (male stressed or died). Larvae drift off; there is no rearing. */
    var hatchedBroods = 0; private set
    var lostBroods = 0; private set

    private fun cardinals() = sim.organisms.filter { it.alive && it.brainKind == BrainKind.CARDINAL }

    /** Sex from the sheet role, else the larger of a pair is the male (males have the bigger mouth, research 02 §3C). */
    private fun resolveCardinalSex(cl: List<Organism>) {
        if (cl.size < 2) return
        val big = cl.maxBy { it.lengthCm }
        for (o in cl) if (o.sexFemale == null) o.sexFemale = if (o.role != null) o.role.equals("FEMALE", true) else o !== big
    }

    private fun cardinalBreeding(now: Double, dt: Double) {
        val cl = cardinals()
        resolveCardinalSex(cl)
        val b = brooder
        if (b != null) {
            if (!b.alive || b !in sim.organisms) { b.mouthbrooding = false; brooder = null; lostBroods++; return }
            b.hunger = minOf(b.hunger, BROOD_MAX_HUNGER)       // he does not eat while brooding
            if (b.stress > BROOD_ABANDON_STRESS) {              // a stressed male swallows the eggs
                b.mouthbrooding = false; brooder = null; lostBroods++; cardCooldownUntil = now + 5 * 86400.0
            } else if (now >= broodEndAt && sim.perception.night) {
                b.mouthbrooding = false; brooder = null; hatchedBroods++
                cardCooldownUntil = now + rng.range(CARD_COOLDOWN_DAYS_MIN, CARD_COOLDOWN_DAYS_MAX) * 86400.0
            }
            return
        }
        val f = cl.filter { it.sexFemale == true }.maxByOrNull { it.lengthCm }
        val m = cl.filter { it.sexFemale == false }.maxByOrNull { it.lengthCm }
        if (f == null || m == null) { cardReadiness = maxOf(0.0, cardReadiness - dt / 86400.0); return }
        val ok = fit(f, CARDINAL_MATURE_CM, now) && fit(m, CARDINAL_MATURE_CM, now) && inRange(sim.water.s)
        cardReadiness = (cardReadiness + if (ok) dt / (READY_DAYS * 86400.0) else -dt / (READY_DAYS * 86400.0 / 4)).coerceIn(0.0, 1.0)
        if (cardReadiness >= 1.0 && now >= cardCooldownUntil && sim.perception.night) {
            m.mouthbrooding = true; brooder = m; cardReadiness = 0.0
            broodEndAt = now + rng.range(BROOD_DAYS_MIN, BROOD_DAYS_MAX) * 86400.0
        }
    }

    // ============================================================== blue tang group spawning (research 41)

    enum class TangPhase { IDLE, COURTING, RUSH }
    var tangPhase = TangPhase.IDLE; private set
    private var tangReadiness = 0.0
    private var tangPhaseUntil = 0.0
    private var tangCooldownUntil = 0.0
    private var tangForced = false
    /** The spawning pair while courting or rushing: the female leads, the male chases (TangSpawn behaviour). */
    var tangFemale: Organism? = null; private set
    var tangMale: Organism? = null; private set
    /** Spawnings so far (tests). */
    var tangSpawns = 0; private set

    /** Only blue tangs pair: other tang species share TangBrain but never cross with them. */
    private fun tangs() = sim.organisms.filter { it.alive && it.brainKind == BrainKind.TANG && it.species.id == BLUE_TANG_ID }

    /**
     * P. hepatus are gonochores with no outward sex difference ([S]): a fish's sex is fixed but unseen. Once two or more
     * are grown up, any not yet known are given one so the group holds both (a game choice: a real pair can turn out
     * to be two of a kind). A sheet role wins.
     */
    private fun resolveTangSex(cl: List<Organism>) {
        val grown = cl.filter { LifeStages.mature(it) }
        if (grown.size < 2) return
        for (o in grown.sortedWith(compareByDescending<Organism> { it.lengthCm }.thenBy { it.id })) {
            if (o.sexFemale != null) continue
            o.sexFemale = when {
                o.role.equals("FEMALE", true) -> true
                o.role.equals("MALE", true) -> false
                else -> grown.count { it.sexFemale == true } <= grown.count { it.sexFemale == false }
            }
        }
    }

    private fun tangPair(cl: List<Organism>): Pair<Organism?, Organism?> {
        val f = cl.filter { it.sexFemale == true && LifeStages.mature(it) }.maxByOrNull { it.lengthCm }
        val m = cl.filter { it.sexFemale == false && LifeStages.mature(it) }.maxByOrNull { it.lengthCm }
        return f to m
    }

    fun hasTangPair(): Boolean = tangPair(tangs()).let { it.first != null && it.second != null }

    /** Days into the lunar month (0 new moon, ~14.8 full) on the sim clock; yellow tangs spawn around the full moon [A]. */
    fun moonDay(now: Double = sim.simTimeSec): Double = ((now / 86400.0 + MOON_OFFSET_DAYS) % LUNAR_DAYS + LUNAR_DAYS) % LUNAR_DAYS
    private fun nearFullMoon(now: Double) = kotlin.math.abs(moonDay(now) - LUNAR_DAYS / 2) <= TANG_MOON_WINDOW_DAYS
    /** The last hour before lights out: P. hepatus spawn late afternoon to evening [S]. */
    private fun dusk(now: Double) = sim.lights.intensity(now + TANG_COURT_MAX_S + 2 * TANG_RUSH_S) >= LightSchedule.LIGHTS_ON &&
        sim.lights.intensity(now + 3600) < LightSchedule.LIGHTS_ON

    private fun tangBreeding(now: Double, dt: Double) {
        val cl = tangs()
        resolveTangSex(cl)
        val (f, m) = tangPair(cl)
        if (f == null || m == null) {
            if (tangPhase != TangPhase.IDLE) endTangSpawn()
            tangReadiness = maxOf(0.0, tangReadiness - dt / 86400.0); sim.brood.forceTangSpawn = false
            return
        }
        if (sim.brood.forceTangSpawn) {
            sim.brood.forceTangSpawn = false
            if (tangPhase == TangPhase.IDLE) { tangForced = true; startCourting(f, m, now) }
            return
        }
        val ok = tangForced || fit(f, 0.0, now) && fit(m, 0.0, now) && inRange(sim.water.s)
        when (tangPhase) {
            TangPhase.IDLE -> {
                tangReadiness = (tangReadiness + if (ok) dt / (TANG_READY_DAYS * 86400.0) else -dt / (TANG_READY_DAYS * 86400.0 / 4)).coerceIn(0.0, 1.0)
                if (tangReadiness >= 1.0 && now >= tangCooldownUntil && nearFullMoon(now) && dusk(now)) startCourting(f, m, now)
            }
            // a male chases the female in loops, both paling from deep to pale blue [S], then they rush up together
            TangPhase.COURTING -> if (!ok || sim.perception.night && !tangForced) endTangSpawn() else if (now >= tangPhaseUntil) {
                tangPhase = TangPhase.RUSH; tangPhaseUntil = now + TANG_RUSH_S
            }
            TangPhase.RUSH -> if (now >= tangPhaseUntil) {
                // at the top of the rush the eggs and milt go out in a cloud, and the pair darts back down
                val at = Vec3(f.pos.x, sim.rock.waterLevelCm - 3.0, f.pos.z)
                val eggs = sim.brood.clutchSize(BroodSpecs.TANG, f.lengthCm, tangSpawns + 1)
                tangSpawns++
                sim.brood.spawnPelagic(BroodSpecs.TANG, eggs, at, listOf(f, m))
                tangCooldownUntil = now + rng.range(TANG_COOLDOWN_DAYS_MIN, TANG_COOLDOWN_DAYS_MAX) * 86400.0
                tangReadiness = 0.0
                endTangSpawn()
            }
        }
    }

    private fun startCourting(f: Organism, m: Organism, now: Double) {
        tangFemale = f; tangMale = m
        tangPhase = TangPhase.COURTING
        tangPhaseUntil = now + rng.range(TANG_COURT_MIN_S, TANG_COURT_MAX_S)
    }

    private fun endTangSpawn() { tangPhase = TangPhase.IDLE; tangFemale = null; tangMale = null; tangForced = false }

    /** The tang parents' lines on their card. */
    fun tangLines(lines: MutableList<String>) {
        val now = sim.simTimeSec
        val cl = tangs()
        val (f, m) = tangPair(cl)
        val name = { o: Organism -> o.nickname.ifBlank { "the ${if (o === f) "female" else "male"}" } }
        when (tangPhase) {
            TangPhase.COURTING -> { lines += "${name(tangMale!!)} is chasing ${name(tangFemale!!)} and they have both gone pale blue. They're courting!"; return }
            TangPhase.RUSH -> { lines += "Spawning! They race up to the surface together and let their eggs go."; return }
            else -> {}
        }
        if (f == null || m == null) {
            lines += when {
                cl.size < 2 -> "Blue tangs need a partner to breed."
                cl.count { LifeStages.mature(it) } < 2 -> "The tangs are too young to breed yet. They grow up at about ${LifeStageSpec.of(cl[0].species).maturityCm.roundToInt()} cm."
                else -> "These tangs are all the same, so they can't breed."
            }
            return
        }
        val why = buildList {
            for (o in listOf(f, m)) {
                if (now - o.arrivalTime < PAIR_BOND_DAYS * 86400.0) add("${name(o)} is still settling in")
                else if (o.hunger >= 0.55) add("${name(o)} is hungry")
                else if (o.stress >= 0.35 || o.health <= 0.85 || o.disease != null) add("${name(o)} is not feeling great")
            }
            if (!inRange(sim.water.s)) add("the water is not right for spawning")
        }
        if (why.isNotEmpty()) { lines += "Not ready to spawn: ${why.distinct().joinToString(", ")}."; return }
        val toMoon = ((LUNAR_DAYS / 2 - TANG_MOON_WINDOW_DAYS - moonDay(now)) % LUNAR_DAYS + LUNAR_DAYS) % LUNAR_DAYS
        val days = maxOf((1.0 - tangReadiness) * TANG_READY_DAYS, (tangCooldownUntil - now) / 86400.0, if (nearFullMoon(now)) 0.0 else toMoon)
        lines += if (days < 1) "${name(f)} and ${name(m)} may spawn this evening, just before the lights go out."
        else "${name(f)} and ${name(m)} are a pair. Blue tangs spawn at dusk around the full moon, in about ${days.toInt() + 1} days."
    }

    companion object {
        const val BLUE_TANG_ID = "paracanthurus_hepatus"
        const val TANG_READY_DAYS = 10.0
        const val TANG_COURT_MIN_S = 900.0
        const val TANG_COURT_MAX_S = 1800.0
        const val TANG_RUSH_S = 60.0
        /** About one spawning a lunar month (yellow tang [A]): the cooldown keeps it to one night per full moon. */
        const val TANG_COOLDOWN_DAYS_MIN = 20.0
        const val TANG_COOLDOWN_DAYS_MAX = 24.0
        const val TANG_MOON_WINDOW_DAYS = 3.0
        const val LUNAR_DAYS = 29.53
        /** Sim day 0 falls this many days into the lunar month (DESIGN ESTIMATE: the sim has no calendar moon). */
        const val MOON_OFFSET_DAYS = 3.0
        const val WRASSE_CHANGE_DAYS = 14.0
        const val CARDINAL_MATURE_CM = 5.0
        const val BROOD_DAYS_MIN = 6.0
        const val BROOD_DAYS_MAX = 8.0
        const val BROOD_MAX_HUNGER = 0.6
        const val BROOD_ABANDON_STRESS = 0.8
        const val CLOWN = "CLOWN"
        const val SEX_CHANGE_DAYS = 28.0
        const val READY_DAYS = 7.0
        const val PAIR_BOND_DAYS = 14.0
        const val FEMALE_MATURE_CM = 6.0
        const val MALE_MATURE_CM = 4.5
        /** Cooldown after a clutch hatches: with incubation this gives the 12-15 day cycle (research 28 §1). */
        const val COOLDOWN_DAYS_MIN = 2.0
        const val COOLDOWN_DAYS_MAX = 5.0
        const val CARD_COOLDOWN_DAYS_MIN = 7.0
        const val CARD_COOLDOWN_DAYS_MAX = 14.0
        const val UNTENDED_LOSS_HOURS = 6.0
    }
}
