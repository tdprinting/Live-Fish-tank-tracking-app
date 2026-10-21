package com.reeflive.core.ai

import com.reeflive.core.geom.Vec3
import com.reeflive.core.geom.smoothstep
import com.reeflive.core.sim.BrainKind
import com.reeflive.core.sim.ClamRig
import com.reeflive.core.sim.CoralSpots
import com.reeflive.core.sim.HealthModel
import com.reeflive.core.sim.Organism
import com.reeflive.core.sim.Simulation
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

// =====================================================================================
// Maxima clam (Tridacna maxima, research 45 §1). Fixed by its byssus, hinge down, high on the rock or on the sand where
// the light is strongest, the colourful mantle spread over the shell rim to light its zooxanthellae, which feed it most
// of what it needs; it filters a little phytoplankton too (more while small). Its mantle edge carries hundreds of
// simple eyes: a shadow, a tap or a hand and the mantle snaps in and the valves close with a squirt, then it opens again
// over a minute or two. Too little light or too much heat and it loses its algae (bleaches), turns pale and starves.
//
// Pose (ClamRig, fish.vert mode 7): +X is the gape (up, toward the light), the hinge at -X on the rock; +Z the shell
// length, laid along the front glass so the keeper sees the whole mantle.
// =====================================================================================
class GiantClamBrain(c: Ctx) : SpeciesBrain(c) {
    companion object {
        /** PAR (µmol/m²/s) just under the surface in the middle at full fixture output [E: AI Prime HD class, 40 cm tank]. */
        const val PAR_FULL = 550.0
        /** PAR that runs its photosynthesis flat out (research 45 §0: 300-400 optimal). */
        const val PAR_SAT = 320.0
        /** Day-average PAR below which its algae starve and it slowly bleaches (research 45 §0: floor ~200-250). */
        const val PAR_LOW = 200.0
        /** Day-average PAR above which it pales from too much light, without slow acclimation [E]. */
        const val PAR_HIGH = 750.0
        /** Water temperature above which it bleaches (°C; Tridacna bleach in sustained heat like corals) [E]. */
        const val BLEACH_C = 30.0
        /** Hunger worked off per hunger gained at full light and full symbionts. Lights are on ~40 % of the day. */
        const val PHOTO_GAIN = 2.7
        /** Share of its needs it can filter at full phytoplankton: small clams lean on it more (research 45 §1). */
        const val FILTER_ADULT = 0.25
        const val FILTER_JUVENILE = 0.6
        const val JUVENILE_CM = 8.0
        /** Alkalinity a 12 cm clam takes for its shell, dKH a day in the display [E: like a small stony colony]. */
        const val DKH_PER_DAY_12CM = 0.03
        /** Shadow reaction range, cm (hyaline-organ eyes along the mantle edge [M]). */
        const val SHADOW_CM = 30.0
        /** Mantle reopening per second after a close: about 1-2 minutes to spread fully [E; research 45 GAP]. */
        const val REOPEN_RATE = 0.025

        /** Light at its mantle as PAR. */
        fun parAt(sim: Simulation, o: Organism): Double =
            PAR_FULL * sim.lights.intensity(sim.simTimeSec) * CoralSpots.lightAt(sim.rock, o.pos + o.heading * (0.06 * o.lengthCm), o.heading)

        /** Alkalinity demand of every live giant clam in the tank (WaterSim adds it to the corals'). */
        fun demandDkhPerDay(organisms: List<Organism>): Double {
            var d = 0.0
            for (o in organisms) if (o.alive && o.brainKind == BrainKind.GIANT_CLAM) d += DKH_PER_DAY_12CM * (o.lengthCm / 12.0).pow(2.5) * (1 - 0.7 * o.bleach)
            return d
        }

        /** Photosynthesis and filtering without the behaviour layer (fast time, away catch-up): the same balance as [Live]. */
        fun abstractFeed(o: Organism, sim: Simulation, dtMin: Double) {
            val rise = HealthModel.hungerRatePerMin(o, sim.water.s.tempC)
            val par = parAt(sim, o)
            val photo = min(1.0, par / PAR_SAT) * (1 - o.bleach)
            o.hunger = max(0.0, o.hunger - (PHOTO_GAIN * photo + filterShare(o) * FileClamBrain.foodAvailable(sim)) * rise * dtMin)
            val lit = sim.lights.intensity(sim.simTimeSec) > 0.05
            symbionts(o, sim.water.s.tempC, if (lit) par else -1.0, lit, dtMin * 60)
        }

        /**
         * Its zooxanthellae over [dt] s: heat and too much light drive them out, too little light starves them (only
         * counted while the lamps are on; [parDay] < 0 = not known yet), otherwise they grow back slowly.
         */
        fun symbionts(o: Organism, tempC: Double, parDay: Double, lit: Boolean, dt: Double) {
            val day = dt / 86400.0
            var loss = 0.0
            if (tempC > BLEACH_C) loss += 0.08 * (tempC - BLEACH_C) * day
            if (parDay > PAR_HIGH) loss += 0.05 * ((parDay - PAR_HIGH) / 250.0).coerceAtMost(2.0) * day
            // lamps are on ~40 % of the day: scale the lit-hours rate so a dim spot loses ~4 % a day at worst
            if (lit && parDay in 0.0..PAR_LOW) loss += 0.04 * (1 - parDay / PAR_LOW) * day / 0.4
            o.bleach = if (loss > 0) min(1.0, o.bleach + loss) else max(0.0, o.bleach - 0.03 * day)
        }

        private fun filterShare(o: Organism) = if (o.lengthCm < JUVENILE_CM) FILTER_JUVENILE else FILTER_ADULT
    }

    private var home: Rest? = null
    private var placedAt = Vec3.ZERO
    private var gape = 0.0
    private var ext = 0.0
    private var flinch = 0.0
    private var nextSense = 0.0
    /** Day-average PAR at its mantle (an exponential mean over the lit hours, ~2 days). */
    var parDay = -1.0; private set
    /** Closes (shadow, tap, touch, pickers): tests and captions. */
    var closes = 0; private set
    /** Symbionts left, 1 healthy .. 0 bleached white (shown as o.bleach). */
    val zoox: Double get() = 1 - o.bleach

    init { install(Live()) }

    override fun restSpot(c: Ctx, night: Boolean): Rest = home ?: findHome(c)

    override fun initialPlace(c: Ctx) {
        val r = findHome(c)
        o.homePos = r.pos
        o.placeAt(r.pos, r.heading)
        o.up = r.up ?: Vec3.Z
        placedAt = o.pos
    }

    /**
     * Its spot: the brightest open, upward-facing rock face near where it was put (or the middle of the tank) that is
     * flat enough for the shell to sit on without its ends in the rock and clear of the corals; failing that, the sand
     * under the brightest water. Hinge on the surface, gape up, the shell's length along the front glass.
     */
    private fun findHome(c: Ctx, near: Vec3? = null): Rest {
        val L = o.lengthCm
        val sand = c.rock.sandTopCm
        val centre = near ?: o.pos.takeIf { it != Vec3.ZERO } ?: Vec3(c.rock.lengthCm * 0.5, sand + 12, c.rock.widthCm * 0.5)
        val taken = ArrayList<Vec3>()
        for (k in c.sim.corals) taken.add(k.pos)
        for (x in c.sim.organisms) if (x !== o && x.alive && (x.brainKind == BrainKind.GIANT_CLAM || x.brainKind == BrainKind.ANEMONE)) taken.add(x.homePos)
        val along = { n: Vec3 -> Vec3.X.rejectFrom(n).normalized(Vec3.X) }
        val hit = c.rock.perchNear(centre, 40.0, step = 3.0, spacing = 0.6 * L + 3.0, taken = taken,
            accept = { q, n ->
                if (n.y < 0.8 || q.y < sand + 1.0 || q.y + 0.65 * L > c.rock.waterLevelCm - 3.0) false else {
                    // both ends of the shell on the rock, not in it or hanging off it
                    val a = along(n) * (0.45 * L)
                    val e1 = c.rock.sdf.dist(q + a + n * (0.08 * L)); val e2 = c.rock.sdf.dist(q - a + n * (0.08 * L))
                    e1 > 0.0 && e2 > 0.0 && e1 < 0.3 * L && e2 < 0.3 * L && c.rock.sdf.dist(q + n * (0.6 * L)) > 0.45 * L
                }
            },
            score = { q, n -> -10.0 * CoralSpots.lightAt(c.rock, q, n) + 0.04 * q.distanceTo(centre) })
        val (q, n) = hit ?: run {
            // the sand under open water, toward the middle where the light is best
            val x = centre.x.coerceIn(0.3 * c.rock.lengthCm, 0.7 * c.rock.lengthCm)
            val z = centre.z.coerceIn(0.25 * c.rock.widthCm, 0.6 * c.rock.widthCm)
            var p = Vec3(x, c.sim.sand.surfaceY(x, z), z)
            if (c.rock.sdf.dist(p + Vec3.Y * (0.3 * L)) < 0.4 * L) p = c.rock.sdf.project(p + Vec3.Y * (0.3 * L), 0.4 * L).let { Vec3(it.x, c.sim.sand.surfaceY(it.x, it.z), it.z) }
            p to Vec3.Y
        }
        // valve plane normal across the front glass so the length (+Z of the model) runs along it
        val up = Vec3.Z.rejectFrom(n).normalized((n cross Vec3.X).normalized(Vec3.Z))
        val r = Rest(q + n * (0.47 * L), n, up, posture = 3)
        home = r
        return r
    }

    override fun preTick(c: Ctx) {
        super.preTick(c)
        // moved by a hand, the rock builder or the bag: re-attach where it now is
        if (o.pos.distanceTo(placedAt) > 3.0) { home = findHome(c, o.pos); flinch = 1.0 }
    }

    private fun close(s: Double) { if (flinch < 0.3 && s >= 0.3) closes++; flinch = max(flinch, s) }
    override fun onTap(c: Ctx, at: Vec3, s: Double) { super.onTap(c, at, s); close(0.4 + 0.6 * s) }
    override fun onShadow(c: Ctx, at: Vec3) {
        super.onShadow(c, at)
        if (at.distanceTo(o.pos) < SHADOW_CM) close(0.5)
    }
    override fun onTouch(c: Ctx, at: Vec3, kind: com.reeflive.core.sim.TouchReaction, s: Double) {
        super.onTouch(c, at, kind, s)
        if (at.distanceTo(o.pos) < o.lengthCm + 4) close(1.0)
    }
    override fun onAttacked(c: Ctx, from: Vec3, mag: Double, by: Organism?) {
        super.onAttacked(c, from, mag, by)
        close(1.0)
    }

    /** Tankmates that pick at an open clam's mantle (research 45 §1: pickers; the hobby names crabs and big shrimp too) [M]. */
    private fun pests(k: BrainKind) = k == BrainKind.BANDED_SHRIMP || k == BrainKind.SALLY_CRAB || k == BrainKind.ARROW_CRAB

    inner class Live : Behavior("giantclam", "Maxima clam") {
        override val noisy: Boolean get() = false
        override val label: String get() = when {
            flinch > 0.4 -> "Snapped shut"
            ext < 0.6 && flinch > 0.05 -> "Opening its mantle again"
            o.bleach > 0.5 -> "Bleached: pale, it has lost its algae"
            o.bleach > 0.15 -> "Paling: its algae are leaving"
            !c.p.night && parDay in 0.0..PAR_LOW -> "Spreading its mantle wide: too little light here"
            c.p.night -> "Mantle half drawn in for the night"
            o.hunger > 0.6 -> "Hungry: soaking up the light"
            else -> "Mantle spread wide in the light"
        }
        override fun score(c: Ctx) = 0.5
        override fun update(c: Ctx, out: SteerIntent) = live(c, out)
    }

    private fun live(c: Ctx, out: SteerIntent) {
        val dt = c.dt
        val r = home ?: findHome(c)
        out.place(r.pos, r.heading, r.up, 4.0)
        out.posture = 3
        placedAt = r.pos
        o.homePos = r.pos
        flinch *= exp(-dt / 4.0)
        if (c.t >= nextSense) { sense(c, r, c.t - nextSense + 1.0); nextSense = c.t + 1.0 }
        val weak = smoothstep(0.6, 1.0, o.hunger) * 0.5 + (1 - o.health).coerceIn(0.0, 1.0) * 0.4
        val par = parAt(c.sim, o)
        // the mantle: spread in light (wider when it is short of light), half in at night, in at once to a flinch
        val eTarget = when {
            flinch > 0.25 -> 0.0
            c.p.night -> 0.5
            par > PAR_HIGH -> 0.7
            else -> 1.0
        } * (1 - 0.4 * weak)
        ext += (eTarget - ext) * min(1.0, dt * if (eTarget < ext) 5.0 else REOPEN_RATE * 4.0)
        // the valves part a little under the mantle; shutting is fast (adductor), opening slow (ligament)
        val gTarget = if (flinch > 0.25) 0.0 else 0.18 + 0.12 * ext
        gape += (gTarget - gape) * min(1.0, dt * if (gTarget < gape) 10.0 else 0.6)
        o.extension = ext
        o.mouth = gape
        (o.limbRig as? ClamRig)?.set(0.0, gape, ext, 0.0, 0.0, 0.0, o.bleach)
    }

    /** Once a second: light, food, heat, pickers. [dt] is the real time since the last look. */
    private fun sense(c: Ctx, r: Rest, dt: Double) {
        val w = c.sim.water.s
        val par = parAt(c.sim, o)
        val lit = c.sim.lights.intensity(c.t) > 0.05
        if (lit) parDay = if (parDay < 0) par else parDay + (par - parDay) * min(1.0, dt / (2 * 10 * 3600.0))
        val rise = HealthModel.hungerRatePerMin(o, w.tempC) / 60.0 * dt
        val photo = min(1.0, par / PAR_SAT) * zoox * ext
        val filter = filterShare(o) * FileClamBrain.foodAvailable(c.sim) * smoothstep(0.05, 0.2, gape)
        o.hunger = max(0.0, o.hunger - (PHOTO_GAIN * photo + filter) * rise)
        // a long-bleached clam starves even in good light (photo above scales with zoox); the hunger clock does the rest
        symbionts(o, w.tempC, parDay, lit, dt)
        val gapeAt = r.pos + r.heading * (0.08 * o.lengthCm)
        val picker = c.p.neighbors(gapeAt, 8.0).firstOrNull { it !== o && it.alive && pests(it.brainKind) && it.pos.distanceTo(gapeAt) < 2.0 + 0.6 * o.lengthCm }
        if (picker != null && ext > 0.3) {
            close(0.8)
            if (c.rng.chance(0.03)) o.health = max(0.05, o.health - 0.01)
        }
        // suspended food landing on the mantle
        if (gape > 0.1) c.food.nearest(gapeAt, 0.5 * o.lengthCm) { !it.settled && it.kind.suspended && o.species.dietMatch(it.kind) > 0 }?.let { c.bite(it) }
    }

    override fun abstractPose(c: Ctx, out: SteerIntent) {
        val r = home ?: findHome(c)
        out.place(r.pos, r.heading, r.up)
        out.posture = 3
        placedAt = r.pos
        ext = if (c.sim.lights.intensity(c.sim.simTimeSec) > 0.05) 1.0 else 0.5
        gape = 0.25
        (o.limbRig as? ClamRig)?.set(0.0, gape, ext, 0.0, 0.0, 0.0, o.bleach)
    }
}
