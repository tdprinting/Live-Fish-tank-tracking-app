package com.reeflive.core.sim

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/** The seven algae groups of research 09 Part A (A1). */
enum class AlgaeGroup { DIATOMS, FILM, HAIR, CYANO, DINOS, BUBBLE, CORALLINE }

/** Surfaces algae grow on (A0). Dead spots are folded into [ROCK_SHADE] (low light, low flow). */
enum class AlgaeSurface { GLASS, SAND, ROCK_TOP, ROCK_SHADE }

/** Which dissolved nutrient limits a group right now (Liebig minimum, A0). */
enum class Nutrient { NONE, N, P, SI }

/**
 * Grazers of research 09 A4. Rates are g DW of algae per animal per day at the reference body length;
 * preferences are per [AlgaeGroup] (diatom film, GSA film, hair, cyano, dinos, bubble, coralline) and
 * access is per [AlgaeSurface] (glass, sand, rock top, rock shade).
 */
enum class GrazerType(val gPerDay: Double, val kGraze: Double, val refCm: Double, val pref: DoubleArray, val access: DoubleArray) {
    TANG(1.0, 3.0, 8.0, doubleArrayOf(0.3, 0.5, 1.0, 0.0, 0.0, 0.05, 0.0), doubleArrayOf(0.3, 0.1, 1.0, 0.5)),
    SALLY_CRAB(0.35, 1.0, 5.0, doubleArrayOf(0.5, 0.5, 1.0, 0.3, 0.1, 0.3, 0.0), doubleArrayOf(0.0, 0.3, 1.0, 1.0)),
    TROCHUS(0.15, 1.0, 3.0, doubleArrayOf(1.0, 1.0, 0.4, 0.2, 0.1, 0.0, 0.0), doubleArrayOf(1.0, 0.5, 1.0, 1.0)),
    TURBO(0.12, 1.0, 2.5, doubleArrayOf(1.0, 1.0, 0.6, 0.1, 0.1, 0.0, 0.0), doubleArrayOf(1.0, 0.3, 1.0, 1.0)),
    NASSARIUS(0.02, 1.0, 1.8, doubleArrayOf(0.5, 0.3, 0.0, 0.0, 0.0, 0.0, 0.0), doubleArrayOf(0.2, 1.0, 0.0, 0.0)),
    HERMIT(0.04, 1.0, 2.5, doubleArrayOf(0.6, 0.5, 0.8, 0.3, 0.0, 0.1, 0.0), doubleArrayOf(0.3, 1.0, 1.0, 1.0)),
    LAWNMOWER(0.3, 1.0, 10.0, doubleArrayOf(1.0, 1.0, 0.5, 0.0, 0.0, 0.0, 0.0), doubleArrayOf(0.5, 0.2, 1.0, 0.7)),
    TAILSPOT(0.075, 1.0, 5.5, doubleArrayOf(0.8, 0.8, 0.2, 0.0, 0.0, 0.0, 0.0), doubleArrayOf(0.2, 0.0, 1.0, 0.5)),
    /** Not in research 09; asterina stars graze film lightly (est.). */
    ASTERINA(0.01, 1.0, 1.0, doubleArrayOf(0.5, 0.5, 0.0, 0.0, 0.0, 0.0, 0.0), doubleArrayOf(1.0, 0.3, 1.0, 1.0)),
    /** Not in research 09: the fighting conch grazes diatoms, film and cyano off the sand surface only (research 43 §3; rate est.). */
    CONCH(0.1, 1.0, 7.0, doubleArrayOf(1.0, 0.8, 0.2, 0.5, 0.1, 0.0, 0.0), doubleArrayOf(0.0, 1.0, 0.0, 0.0)),
    /** Not in research 09: the emerald crab scoops bubble algae first, then hair and film, off the rock at night (research 43 §1; rate est.). */
    EMERALD_CRAB(0.08, 1.0, 3.0, doubleArrayOf(0.2, 0.3, 0.6, 0.1, 0.0, 1.0, 0.0), doubleArrayOf(0.0, 0.0, 0.7, 1.0)),
    /** Not in research 09: the tuxedo urchin rasps film, hair and coralline off rock and glass (research 43 §2; rate est.). */
    URCHIN(0.12, 1.0, 5.0, doubleArrayOf(0.6, 0.8, 0.7, 0.1, 0.1, 0.2, 0.7), doubleArrayOf(0.8, 0.1, 1.0, 1.0)),
    ;

    companion object {
        /** Grazer row for an organism, or null when it does not graze algae. Trochus has no brain kind; matched by genus. */
        fun of(o: Organism): GrazerType? = when (o.brainKind) {
            BrainKind.TANG -> TANG
            BrainKind.SALLY_CRAB -> SALLY_CRAB
            BrainKind.ASTREA -> TURBO
            BrainKind.NASSARIUS -> NASSARIUS
            BrainKind.HERMIT -> HERMIT
            BrainKind.LAWNMOWER_BLENNY -> LAWNMOWER
            BrainKind.PEEKER_BLENNY -> TAILSPOT
            BrainKind.ASTERINA -> ASTERINA
            BrainKind.TROCHUS -> TROCHUS
            BrainKind.CONCH -> CONCH
            BrainKind.URCHIN -> URCHIN
            BrainKind.EMERALD_CRAB -> EMERALD_CRAB
            else -> when {
                o.species.id.startsWith("trochus", true) || o.species.id.startsWith("tectus", true) -> TROCHUS
                o.species.id.startsWith("turbo", true) -> TURBO
                else -> null
            }
        }
    }
}

/** One grazer present: its row and how many reference-size animals it counts for. */
class AlgaeGrazer(val type: GrazerType, val units: Double)

/**
 * Per-surface algae cover for the renderer (research 09 A6): `cover[g * SURFACES + s]` in 0..1 for every
 * [AlgaeGroup] g and [AlgaeSurface] s, and `vigor[g]` (0..1, the group's nutrient state; low = pale, starving).
 *
 * The front glass is spatial ([GlassMask]): [glassMask] is its [GlassMask.W] × [GlassMask.H] density grid (null while
 * the pane is untouched) and [glassBase] the cover per group on glass the scraper has not touched; the glass entries
 * of [cover] are the mean over all glass (scraped stripes included).
 */
class AlgaeCover(
    val cover: FloatArray,
    val vigor: FloatArray,
    val glassBase: FloatArray? = null,
    val glassMask: FloatArray? = null,
    /** [GlassMask.version] of [glassMask], so the renderer re-uploads only when it changed. */
    val glassMaskVersion: Int = 0,
    /**
     * Share of the dinoflagellate film lifted off the rock and sand into the water (0 by day .. 1 at night; research
     * 09 A6: "lifts off at night and reappears at lights-on"). The renderer thins the dino mats by it.
     */
    val dinoLift: Float = 0f,
) {
    fun cover(g: AlgaeGroup, s: AlgaeSurface): Float = cover[g.ordinal * SURFACES + s.ordinal]

    companion object {
        val SURFACES = AlgaeSurface.entries.size
        val GROUPS = AlgaeGroup.entries.size
        val EMPTY = AlgaeCover(FloatArray(GROUPS * SURFACES), FloatArray(GROUPS) { 1f })
    }
}

/**
 * Algae growth, succession, nutrient uptake, grazing and die-back (research 09 Part A).
 *
 * State is biomass B (g DW) per group and surface. Growth follows A0:
 * `mu = mu_max · f_nut · f_light · f_flow · f_temp · f_space` with the Liebig minimum of Monod terms (N, P and
 * silicate for diatoms), an internal store that lets a group ride out a nutrient crash (A3 simple clamp),
 * the N:P, ultra-low-nutrient, low-flow/DOC and silicate triggers (A2), stoichiometric uptake that takes
 * ammonia first (A3), multi-prey Holling II grazing by the grazers present (A4), and die-back into a dead pool
 * that remineralises to ammonia and phosphate and consumes oxygen (A5). Cover per surface is
 * `Cmax · (1 − exp(−B / (area · b_half)))` (A0, A6).
 *
 * The headline cover of each group is mirrored into [WaterState] (`algaeHair`, `diatoms`, `cyano`, `dinos`,
 * `film`, `bubble`, `coralline`) after every step; a value written there from outside (sandbox slider,
 * test) re-seeds that group's biomass on the next step. Deterministic: no randomness.
 */
class AlgaeModel(config: TankConfig) {
    private val ng = AlgaeCover.GROUPS
    private val ns = AlgaeCover.SURFACES

    /** Surface areas (m²): sand = footprint, glass = wetted walls, rock = 1.2 × footprint split top/shade (est.). */
    val area = DoubleArray(ns).also { a ->
        val foot = config.lengthCm * config.widthCm / 1e4
        val wet = (config.waterLevelCm - config.sandTopCm).coerceAtLeast(10.0)
        a[AlgaeSurface.GLASS.ordinal] = 2 * (config.lengthCm + config.widthCm) * wet / 1e4
        a[AlgaeSurface.SAND.ordinal] = foot
        a[AlgaeSurface.ROCK_TOP.ordinal] = 0.5 * foot
        a[AlgaeSurface.ROCK_SHADE.ordinal] = 0.7 * foot
    }

    /** Biomass, g DW, [g * ns + s]. */
    val biomass = DoubleArray(ng * ns)
    /** Where the glass film is on the front pane (Flipper stripes); glass biomass is spread over the glass by it. */
    val glassMask = GlassMask(config.lengthCm, config.sandTopCm, config.waterLevelCm)
    /** Share of the wetted glass area that is the front pane. */
    val frontShare = config.lengthCm / (2 * (config.lengthCm + config.widthCm))
    /** Net specific growth (per day) of each group on the glass in the last step: the stripe regrowth rate. */
    private val glassNet = DoubleArray(ng)
    /** Internal nutrient store per group (A3 clamp: f_nut ≥ store, decays over days of starvation). */
    private val store = DoubleArray(ng) { 1.0 }
    /** Dead algal tissue awaiting remineralisation (A5): labile pool (mg N, mg P, g DW)... */
    var deadN = 0.0; private set
    var deadP = 0.0; private set
    var deadDw = 0.0; private set
    /** ...and the refractory remainder that decays slowly (ULVDEC: 0.2 d-1 in week one, 0.025 d-1 by day 52). */
    private var oldN = 0.0
    private var oldP = 0.0
    private var oldDw = 0.0
    /** Hours of continuous darkness (blackout die-back starts after a normal night). */
    private var darkH = 0.0
    /** Last headline values written to the water state (to detect outside edits). */
    private val written = DoubleArray(ng) { Double.NaN }

    // ---- drivers set by the owner (Simulation / tests) ----
    /** Grazers present right now. */
    var grazers: List<AlgaeGrazer> = emptyList()
    /** Per-surface local flow override in cm/s (NaN = derive from pump strength). */
    val flowOverride = DoubleArray(ns) { Double.NaN }
    /** DOC index override 0..1 (null = derived from detritus and the dead pool). */
    var docOverride: Double? = null
    /** Groups switched off (sandbox / tests): held at zero biomass, no uptake. */
    val active = BooleanArray(ng) { true }
    /** Film grazed by copepods and micro-snails, g DW per day ([MicrofaunaModel], research 10 §3.1 food feedback). */
    var podGrazeGPerDay = 0.0
    /** Extra cyanobacteria die-back per day from [BacteriaModel] (an antibiotic in the water; research 18). */
    var cyanoExtraDeathPerDay = 0.0
    /** UV steriliser running ([WaterSim.uv]): kills the share of the dinos that is in the water column (A5). */
    var uv = false
    /** Probiotic / bottled bacteria in the water 0..1 ([BacteriaModel.probiotic]): competitors that hold dinos back (A2). */
    var probiotic = 0.0

    // ---- outputs of the last step ----
    /** mg N taken from / returned to the water in the last step (WaterSim nitrogen accounting). */
    var nUptakeMg = 0.0; private set
    var nReleaseMg = 0.0; private set
    /** O2 drawn by decaying algae in the last step, mg/L per hour. */
    var o2DemandPerHour = 0.0; private set
    /** Cumulative mg P returned to the water by decay (diagnostics). */
    var pReleasedMg = 0.0; private set
    /** g DW grown / grazed / died per group in the last step, scaled to per day. */
    val growthPerDay = DoubleArray(ng)
    val grazedPerDay = DoubleArray(ng)
    val deathPerDay = DoubleArray(ng)
    /** Effective nutrient factor and limiter per group in the last step. */
    val fNut = DoubleArray(ng) { 1.0 }
    val limiter = Array(ng) { Nutrient.NONE }
    var docIndex = 0.0; private set
    var uln = false; private set
    /** Share of the dino film lifted into the water (0 by day .. 1 at night), see [AlgaeCover.dinoLift]. */
    var dinoLift = 0.0; private set

    private var initialized = false

    // ============================================================== queries
    fun index(g: AlgaeGroup, s: AlgaeSurface) = g.ordinal * ns + s.ordinal
    fun biomass(g: AlgaeGroup, s: AlgaeSurface) = biomass[index(g, s)]
    fun biomass(g: AlgaeGroup): Double { var t = 0.0; for (s in 0 until ns) t += biomass[g.ordinal * ns + s]; return t }

    /** Cover fraction (0..Cmax) of group [g] on surface [s]. */
    fun cover(g: AlgaeGroup, s: AlgaeSurface): Double = coverOf(g.ordinal, s.ordinal)

    private fun coverOf(g: Int, s: Int): Double {
        val cmax = P[g].cmax[s]
        if (cmax <= 0) return 0.0
        return cmax * (1 - exp(-biomass[g * ns + s] / (area[s] * P[g].bHalf)))
    }

    /** Headline cover of a group: area-weighted over its headline surfaces (see [HEADLINE]). */
    fun headline(g: AlgaeGroup): Double {
        var a = 0.0; var c = 0.0
        for (s in HEADLINE[g.ordinal]) { a += area[s.ordinal]; c += area[s.ordinal] * coverOf(g.ordinal, s.ordinal) }
        return if (a > 0) c / a else 0.0
    }

    fun snapshot(): AlgaeCover {
        val mask = glassMask.export()
        return AlgaeCover(
            FloatArray(ng * ns) { coverOf(it / ns, it % ns).toFloat() },
            FloatArray(ng) { fNut[it].toFloat() },
            glassBase = if (mask == null) null else FloatArray(ng) { glassBaseCover(it).toFloat() },
            glassMask = mask,
            glassMaskVersion = glassMask.version,
            dinoLift = dinoLift.toFloat(),
        )
    }

    /** Glass area weight of the film: the share of the glass still carrying it at the pane-uniform density. */
    private fun glassWeight(): Double = (frontShare * glassMask.mean + 1 - frontShare).coerceAtLeast(1e-3)

    /** Cover of group [g] on glass the scraper has not touched (the biomass sits on [glassWeight] of the area). */
    fun glassBaseCover(g: Int): Double {
        val gl = AlgaeSurface.GLASS.ordinal
        val cmax = P[g].cmax[gl]
        if (cmax <= 0) return 0.0
        return cmax * (1 - exp(-biomass[g * ns + gl] / (area[gl] * glassWeight() * P[g].bHalf)))
    }

    // ============================================================== edits
    /** Set a group's cover on its headline surfaces (sandbox slider, presets); other surfaces keep a seed. */
    fun setCover(g: AlgaeGroup, value: Double) {
        val gi = g.ordinal
        for (s in HEADLINE[gi]) {
            val si = s.ordinal
            val cmax = P[gi].cmax[si]
            if (cmax <= 0) continue
            val frac = (value.coerceAtLeast(0.0) / cmax).coerceIn(0.0, 0.98)
            biomass[gi * ns + si] = max(seedOf(gi, si), -area[si] * P[gi].bHalf * ln(1 - frac))
        }
        if (AlgaeSurface.GLASS in HEADLINE[gi]) glassMask.fill()
        written[gi] = headline(g)
    }

    /** Set a group's total biomass (g DW), spread over its headline surfaces by area. */
    fun setBiomass(g: AlgaeGroup, grams: Double) {
        val surf = HEADLINE[g.ordinal].filter { P[g.ordinal].cmax[it.ordinal] > 0 }
        val a = surf.sumOf { area[it.ordinal] }
        for (s in surf) {
            biomass[index(g, s)] = max(seedOf(g.ordinal, s.ordinal), grams * area[s.ordinal] / a)
        }
        written[g.ordinal] = headline(g)
    }

    /** Owner wipes the glass: every film on the glass loses [fraction] of its biomass (exported, not left to rot). */
    fun wipeGlass(fraction: Double = 0.9) {
        glassMask.fill()
        val gl = AlgaeSurface.GLASS.ordinal
        for (g in listOf(AlgaeGroup.FILM, AlgaeGroup.DIATOMS, AlgaeGroup.CYANO, AlgaeGroup.DINOS)) {
            val i = g.ordinal * ns + gl
            biomass[i] = max(seedOf(g.ordinal, gl), biomass[i] * (1 - fraction))
        }
    }

    /**
     * The owner drags the Flipper over the front glass from (x0, y0) to (x1, y1) (cm from the left end and from the
     * tank floor): the [widthCm] × [heightCm] pad leaves [SCRAPE_LEFT] of the film it passes over ([GlassMask.scrape]).
     * Every glass group loses the biomass that sat under the swept cells; [SCRAPE_TO_DEAD] of it sinks as detritus
     * into the dead pool (remineralises to ammonia and phosphate), the rest is picked off by fish and the filter.
     * Headline covers in [s] are updated at once. Returns the g DW scraped off.
     */
    fun scrapeGlass(s: WaterState, x0: Double, y0: Double, x1: Double, y1: Double, widthCm: Double, heightCm: Double): Double {
        syncFromState(s)
        val before = glassWeight()
        val drop = glassMask.scrape(x0, y0, x1, y1, widthCm / 2, heightCm / 2, SCRAPE_LEFT)
        if (drop <= 0) return 0.0
        // biomass under the swept cells: density B / (A · weight) over frontShare · A · drop of the glass
        val k = (frontShare * drop / before).coerceIn(0.0, 1.0)
        val gl = AlgaeSurface.GLASS.ordinal
        var total = 0.0
        for (gi in 0 until ng) {
            val i = gi * ns + gl
            val m = min(biomass[i] * k, max(0.0, biomass[i] - seedOf(gi, gl)))
            if (m <= 0) continue
            biomass[i] -= m
            toDead(gi, m * SCRAPE_TO_DEAD)
            total += m
        }
        writeBack(s)
        return total
    }

    /** Kill [grams] DW of a group outright (die-off scenario): the tissue goes to the dead pool. */
    fun kill(g: AlgaeGroup, grams: Double) {
        val total = biomass(g)
        if (total <= 0) return
        val k = (grams / total).coerceIn(0.0, 1.0)
        for (s in 0 until ns) {
            val i = g.ordinal * ns + s
            val m = biomass[i] * k
            biomass[i] -= m
            toDead(g.ordinal, m)
        }
    }

    /**
     * The owner pulls the bubble algae off the rock (research 09 A2 "manual removal", A5): [BUBBLE_PICK] of the
     * bubbles above the hidden seed come out and leave the tank. Lifted out whole ([careful]) that is all; squeezed or
     * scraped, [BUBBLE_POP_SHARE] of the cells burst, their sap rots in the tank and each burst cell seeds
     * [BUBBLE_POP_SEEDS] new cells nearby ("a popped cell seeds 3-8 new cells", hobby consensus). Returns g DW removed.
     */
    fun removeBubble(s: WaterState, careful: Boolean): Double {
        syncFromState(s)
        val gi = AlgaeGroup.BUBBLE.ordinal
        var removed = 0.0
        for (si in 0 until ns) {
            val i = gi * ns + si
            val m = max(0.0, biomass[i] - seedOf(gi, si)) * BUBBLE_PICK
            biomass[i] -= m
            removed += m
        }
        if (!careful && removed > 0) {
            val popped = removed * BUBBLE_POP_SHARE
            toDead(gi, popped * 0.5)
            // swarmers settle on the rock around the burst cells, spread over the rock it grows on by area
            val spawn = popped * BUBBLE_POP_SEEDS * BUBBLE_DAUGHTER_MASS
            var a = 0.0
            for (si in 0 until ns) if (P[gi].cmax[si] > 0) a += area[si]
            for (si in 0 until ns) if (P[gi].cmax[si] > 0) biomass[gi * ns + si] += spawn * area[si] / a
        }
        writeBack(s)
        return removed
    }

    fun copyFrom(o: AlgaeModel) {
        o.biomass.copyInto(biomass); o.store.copyInto(store); o.written.copyInto(written)
        o.flowOverride.copyInto(flowOverride); o.active.copyInto(active)
        glassMask.copyFrom(o.glassMask); o.glassNet.copyInto(glassNet)
        deadN = o.deadN; deadP = o.deadP; deadDw = o.deadDw; darkH = o.darkH; docOverride = o.docOverride
        oldN = o.oldN; oldP = o.oldP; oldDw = o.oldDw
        cyanoExtraDeathPerDay = o.cyanoExtraDeathPerDay
        uv = o.uv; probiotic = o.probiotic; podGrazeGPerDay = o.podGrazeGPerDay; grazers = o.grazers
        // last step's outputs, so a snapshot taken before the next step (vigor, lift) reads as before the rebuild
        o.fNut.copyInto(fNut); o.limiter.copyInto(limiter)
        docIndex = o.docIndex; uln = o.uln; dinoLift = o.dinoLift; pReleasedMg = o.pReleasedMg
        initialized = o.initialized
    }

    // ============================================================== step
    /**
     * Advance [dtH] hours. [light] is the tank light intensity (0..1, [LightSchedule.intensity]); [flowEff] the pump
     * strength (0 = pumps off, 1 = normal, 2 = max). Changes nutrients in [s] and mirrors headline covers into it.
     */
    fun step(s: WaterState, dtH: Double, light: Double, flowEff: Double) {
        syncFromState(s)
        val days = dtH / 24.0
        val v = s.volumeL.coerceAtLeast(1.0)
        darkH = if (light < LightSchedule.LIGHTS_ON) darkH + dtH else 0.0
        val blackout = darkH > DARK_GRACE_H

        // ---- triggers (A2) ----
        val nEq = s.no3 + s.nh3Total * (62.0 / 17.0) + s.no2 * (62.0 / 46.0)   // ppm NO3-equivalent
        val p = s.po4
        val np = (nEq / 0.062) / (max(p, 1e-6) / 0.095)                         // atomic N:P
        uln = nEq < 1.0 && p < 0.02
        // microbial diversity: rises over the first ~6 months, knocked back by copper (A2)
        s.microbes = (s.microbes + days / 180.0 - (if (s.copper > 0.1) 0.3 * days else 0.0)).coerceIn(0.0, 1.0)
        // DOC proxy (A2): detritus and dying algae, or dissolved organics above a clean tank's (overfeeding). Dosed
        // carbon is left to the heterotrophs ([BacteriaModel]), which take it up with the phosphate.
        docIndex = docOverride ?: max((s.detritus - 0.3) / 1.5 + deadDw / 40.0, (s.doc - DOC_CLEAN) / DOC_SPAN).coerceIn(0.0, 1.0)
        var compB = 0.0
        for (g in COMPETITORS) compB += biomass(g)
        // bottled bacteria / probiotics stand in for the missing competitors while the community is young (A2 trigger off)
        val ci = (0.5 * s.microbes + 0.5 * compB / (compB + COMP_HALF_G) + PROBIOTIC_CI * probiotic).coerceIn(0.0, 1.0)
        // dinos lift off into the water after lights-out and settle again after lights-on (A1, A6)
        val lifting = light < LightSchedule.LIGHTS_ON
        dinoLift += ((if (lifting) 1.0 else 0.0) - dinoLift) * min(1.0, dtH / (if (lifting) DINO_LIFT_TAU_H else DINO_SETTLE_TAU_H))
        val nassarius = grazers.filter { it.type == GrazerType.NASSARIUS }.sumOf { it.units }

        // ---- per-group tank-level factors ----
        val mu = DoubleArray(ng)
        val dBase = DoubleArray(ng)
        for (gi in 0 until ng) {
            val pr = P[gi]
            val g = AlgaeGroup.entries[gi]
            // Liebig minimum of Monod terms
            var f = 1.0; var lim = Nutrient.NONE
            if (pr.ksN > 0) { val fn = nEq / (pr.ksN + nEq); if (fn < f) { f = fn; lim = Nutrient.N } }
            if (pr.ksP > 0) { val fp = p / (pr.ksP + p); if (fp < f) { f = fp; lim = Nutrient.P } }
            if (g == AlgaeGroup.DIATOMS) { val fs = s.silicate / (KS_SI + s.silicate); if (fs < f) { f = fs; lim = Nutrient.SI } }
            if (g == AlgaeGroup.DINOS && uln) f = max(f, DINO_ULN_FLOOR)                // mixotrophs: eat bacteria
            // internal store: tracks recent nutrient state up fast, decays linearly while starving
            store[gi] = if (f > store[gi]) store[gi] + (f - store[gi]) * min(1.0, days / 0.5)
                        else max(f, store[gi] - days / pr.storeDays)
            val fEff = max(f, store[gi])
            fNut[gi] = fEff; limiter[gi] = if (f >= 0.999) Nutrient.NONE else lim
            // temperature hump, chemistry tolerance
            val fT = exp(-((s.tempC - pr.tOpt) / pr.tWidth).let { it * it })
            val fPh = if (g == AlgaeGroup.CORALLINE) corallineCarbonate(s) else ((s.ph - pr.phMin) / 0.3).coerceIn(0.0, 1.0)
            val ppt = s.ppt
            val fSal = ((ppt - pr.pptMin) / 6.0).coerceIn(0.0, 1.0) * ((55.0 - ppt) / 8.0).coerceIn(0.0, 1.0)
            val fCu = 1.0 / (1.0 + s.copper / 0.5)
            var m = pr.muMax * fEff * fT * fPh * fSal * fCu
            when (g) {
                // N2 fixer: wins when NO3 is low relative to PO4; DOC (overfeeding, die-off) drives the mats
                AlgaeGroup.CYANO -> m *= (1 + 0.5 * ((8 - np) / 8).coerceIn(0.0, 1.0)) * (CYANO_DOC_BASE + CYANO_DOC_GAIN * docIndex)
                AlgaeGroup.DINOS -> m *= (1.3 - 0.8 * ci)
                AlgaeGroup.CORALLINE -> m *= 1 - 0.8 * ((s.po4 - 0.1) / 0.9).coerceIn(0.0, 1.0)
                // mature rock (A2 stage 5: month 3+) and a pH at or under 8.0 (hobby) favour bubble algae
                AlgaeGroup.BUBBLE -> m *= (BUBBLE_YOUNG_ROCK + (1 - BUBBLE_YOUNG_ROCK) * s.microbes) *
                    (1 + BUBBLE_LOW_PH_GAIN * ((8.1 - s.ph) / 0.2).coerceIn(0.0, 1.0))
                else -> {}
            }
            mu[gi] = m
            // die-back (A5): basal + starvation + blackout + heat + chemistry
            var d = pr.dBasal
            if (fEff < 0.1) d += pr.dStarve * ((0.1 - fEff) / 0.05).coerceIn(0.0, 1.0)
            if (blackout) d += pr.dDark
            d += 0.3 * ((s.tempC - pr.tCrash) / 2.0).coerceIn(0.0, 1.0)
            d += 0.3 * ((s.copper - 0.5) / 0.5).coerceIn(0.0, 1.0)
            if (g == AlgaeGroup.CORALLINE) {
                d += 0.05 * ((8.0 - s.ph) / 0.3).coerceIn(0.0, 1.0) + 0.2 * (s.copper / 0.3).coerceIn(0.0, 1.0) +
                    0.01 * (1 - caAlk(s)) + 0.02 * ((s.po4 - 0.3) / 0.7).coerceIn(0.0, 1.0)
            } else d += 0.2 * ((pr.phMin - s.ph) / 0.3).coerceIn(0.0, 1.0)
            if (ppt < pr.pptMin - 3 || ppt > 58) d += 0.3
            if (g == AlgaeGroup.CYANO && p < 0.01) d += 0.2
            if (g == AlgaeGroup.CYANO) d += cyanoExtraDeathPerDay
            // nutrients back and competitors present: dinos lose (A5 0.3/d, raised so a mature tank holds them at the seed)
            if (g == AlgaeGroup.DINOS && !uln) d += DINO_RETURN_DEATH * ci
            // UV only reaches the dinos in the water column: a few by day, most of the film once it lifts at night (A5)
            if (g == AlgaeGroup.DINOS && uv) d += DINO_UV_DEATH * (DINO_WATER_DAY + (1 - DINO_WATER_DAY) * dinoLift)
            dBase[gi] = d
        }

        // ---- grazing (A4): multi-prey Holling II per grazer over (group, surface) cells ----
        val graze = DoubleArray(ng * ns)
        val palatable = DoubleArray(ng * ns)   // grazeable biomass outside the crevice refuge
        val taste = DoubleArray(ng * ns) { 1.0 }
        for (gi in 0 until ng) for (si in 0 until ns) {
            val i = gi * ns + si
            palatable[i] = max(0.0, biomass[i] - REFUGE_COVER[gi] * area[si] * P[gi].bHalf)
            // short new fuzz is eaten; an established lawn (old Derbesia, Bryopsis) is mostly left alone (A4: 0.1-0.3)
            if (gi == AlgaeGroup.HAIR.ordinal) {
                val x = biomass[i] / (area[si] * P[gi].bHalf)
                taste[i] = OLD_HAIR_PALATABILITY + (1 - OLD_HAIR_PALATABILITY) * exp(-x / SHORT_TURF_X)
            }
        }
        for (z in grazers) {
            if (z.units <= 0) continue
            var denom = z.type.kGraze
            for (gi in 0 until ng) for (si in 0 until ns) denom += z.type.pref[gi] * z.type.access[si] * palatable[gi * ns + si]
            val cap = z.type.gPerDay * z.units * days
            for (gi in 0 until ng) for (si in 0 until ns) {
                val a = z.type.pref[gi] * z.type.access[si]
                if (a <= 0) continue
                graze[gi * ns + si] += cap * a * palatable[gi * ns + si] / denom * taste[gi * ns + si]
            }
        }
        // pods and micro-snails crop the diatom and green film (research 10 §3.1), spread by palatable biomass, at most
        // 5 %/day of it (harpacticoids remove 3-11 % of microalgal biomass per day, HARP-G)
        if (podGrazeGPerDay > 0) {
            var film = 0.0
            for (g in POD_FOOD) for (si in 0 until ns) film += palatable[g.ordinal * ns + si]
            if (film > 0) {
                val k = min(podGrazeGPerDay * days, POD_GRAZE_MAX * film * days) / film
                for (g in POD_FOOD) for (si in 0 until ns) graze[g.ordinal * ns + si] += k * palatable[g.ordinal * ns + si]
            }
        }
        for (si in 0 until ns) {
            // grazing never exceeds ~3 g DW m-2 d-1 of surface (HUM20 cap), nor the palatable stock
            var tot = 0.0
            for (gi in 0 until ng) tot += graze[gi * ns + si]
            val cap = GRAZE_CAP_G_M2_D * area[si] * days
            val k = if (tot > cap) cap / tot else 1.0
            for (gi in 0 until ng) { val i = gi * ns + si; graze[i] = min(graze[i] * k, palatable[i] * 0.5) }
        }

        // ---- growth and death per cell ----
        var nDemand = 0.0; var pDemand = 0.0; var siDemand = 0.0
        growthPerDay.fill(0.0); grazedPerDay.fill(0.0); deathPerDay.fill(0.0)
        val cover = DoubleArray(ng * ns) { coverOf(it / ns, it % ns) }
        glassNet.fill(0.0)
        for (si in 0 until ns) {
            val par = light * PAR_AT_FULL[si]
            val vel = flowOverride[si].takeUnless { it.isNaN() } ?: max(0.5, BASE_FLOW[si] * flowEff)
            for (gi in 0 until ng) {
                val pr = P[gi]
                if (pr.cmax[si] <= 0 || !active[gi]) continue
                val i = gi * ns + si
                val b = biomass[i]
                // light: saturating, photoinhibited above PAR_inhib (falls to 0.5)
                var fL = 1 - exp(-par / pr.ik)
                if (pr.parInhib > 0 && par > pr.parInhib) fL *= max(0.5, 1 - 0.5 * (par - pr.parInhib) / pr.parInhib)
                val fF = interp(pr.flowX, pr.flowY, vel)
                // other groups crowd the surface (coralline is overgrown, not a crowder)
                var others = 0.0
                for (h in 0 until ng) if (h != gi && (h != AlgaeGroup.CORALLINE.ordinal)) others += cover[h * ns + si]
                val fSpace = (1 - 0.5 * others).coerceIn(0.0, 1.0)
                var rate = mu[gi] * fL * fF * fSpace * pr.affinity[si] * DAY_GAIN
                if (gi == AlgaeGroup.CYANO.ordinal && si == AlgaeSurface.SAND.ordinal) rate *= 1 - 0.2 * (nassarius / 3).coerceAtMost(1.0)
                // production scales with the area occupied, so cover follows a logistic curve (A6 "logistic r"):
                // dB/dt = r · area · b_half · C/Cmax  ⇔  dC/dt = r · C · (1 − C/Cmax)
                val grow = rate * area[si] * pr.bHalf * (cover[i] / pr.cmax[si]) * days
                var d = dBase[gi]
                if (gi == AlgaeGroup.CYANO.ordinal) d += 0.2 * ((vel - 10) / 5).coerceIn(0.0, 1.0)     // flow raised: mats lift off
                if (gi == AlgaeGroup.HAIR.ordinal) d += 0.3 * ((vel - 40) / 20).coerceIn(0.0, 1.0)    // tears off
                if (si == AlgaeSurface.GLASS.ordinal) glassNet[gi] = max(0.0, rate - d)
                val die = min(b, d * b * days)
                val eaten = min(graze[i], max(0.0, b - die))
                biomass[i] = b + grow - die - eaten
                toDead(gi, die + GRAZE_EGESTED * eaten)
                growthPerDay[gi] += grow / days; deathPerDay[gi] += die / days; grazedPerDay[gi] += eaten / days
                nDemand += grow * pr.nFrac * 1000 * pr.nFromWater
                pDemand += grow * pr.pFrac * 1000
                if (gi == AlgaeGroup.DIATOMS.ordinal) siDemand += grow * SIO2_PER_DW * 1000
            }
        }

        // ---- uptake (A3): ammonia first, then nitrate, then nitrite; growth past the supply runs on the store ----
        var needN = nDemand
        nUptakeMg = 0.0
        fun takeN(ppm: Double, mgNPerPpm: Double): Double {
            if (needN <= 0 || ppm <= 0) return ppm
            val avail = ppm * mgNPerPpm * v
            val t = min(needN, avail * 0.999)
            needN -= t; nUptakeMg += t
            return ppm - t / (mgNPerPpm * v)
        }
        s.nh3Total = takeN(s.nh3Total, 14.0 / 17.0)
        s.no3 = takeN(s.no3, 14.0 / 62.0)
        s.no2 = takeN(s.no2, 14.0 / 46.0)
        s.po4 = max(0.0, s.po4 - min(pDemand * (95.0 / 31.0) / v, s.po4 * 0.999))
        s.silicate = max(0.0, s.silicate - min(siDemand / v, s.silicate * 0.999))

        // ---- remineralisation of the dead pool (A5): TAN and PO4 back, O2 consumed ----
        val k = 1 - exp(-K_DECAY * days)
        val kOld = 1 - exp(-K_DECAY_OLD * days)
        val lN = deadN * k; val lP = deadP * k; val lDw = deadDw * k
        deadN -= lN; deadP -= lP; deadDw -= lDw
        oldN += lN * REFRACTORY; oldP += lP * REFRACTORY; oldDw += lDw * REFRACTORY
        val oN = oldN * kOld; val oP = oldP * kOld; val oDw = oldDw * kOld
        oldN -= oN; oldP -= oP; oldDw -= oDw
        val rN = lN * (1 - REFRACTORY) + oN; val rP = lP * (1 - REFRACTORY) + oP; val rDw = lDw * (1 - REFRACTORY) + oDw
        s.nh3Total += rN * (17.0 / 14.0) / v
        s.po4 += rP * (95.0 / 31.0) / v
        nReleaseMg = rN
        pReleasedMg += rP
        o2DemandPerHour = if (dtH > 0) rDw * O2_PER_DW * 1000 / v / dtH else 0.0
        // anoxic sediment releases phosphate (AHE), the cyano pulse after a flow loss
        if (s.doMgL < 3) s.po4 += 0.01 * days * ((3 - s.doMgL) / 3).coerceIn(0.0, 1.0)

        // ---- silicate leaching from new sand and rock (A2: half-life 14 d) ----
        if (s.siLeach > 1e-6) {
            s.silicate += s.siLeach * days
            s.siLeach *= exp(-days * ln(2.0) / 14.0)
        }

        // ---- scraped stripes on the front glass grow back at the glass film's net rate (cover-weighted) ----
        if (!glassMask.isFull) {
            var wSum = 0.0; var rSum = 0.0
            val gl = AlgaeSurface.GLASS.ordinal
            for (gi in 0 until ng) { val c = cover[gi * ns + gl]; wSum += c; rSum += c * glassNet[gi] }
            glassMask.regrow(if (wSum > 1e-6) rSum / wSum else glassNet[AlgaeGroup.FILM.ordinal], days)
        }

        // ---- hidden seed pools: every group can come back ----
        for (gi in 0 until ng) for (si in 0 until ns) {
            val i = gi * ns + si
            if (!active[gi]) biomass[i] = 0.0
            else if (biomass[i] < seedOf(gi, si)) biomass[i] = seedOf(gi, si)
        }
        writeBack(s)
    }

    private fun toDead(gi: Int, dw: Double) {
        if (dw <= 0) return
        deadN += dw * P[gi].nFrac * 1000
        deadP += dw * P[gi].pFrac * 1000
        deadDw += dw
    }

    private fun seedOf(gi: Int, si: Int): Double {
        if (P[gi].cmax[si] <= 0) return 0.0
        return area[si] * P[gi].bHalf * SEED_COVER[gi]
    }

    /** Pick up outside edits of the headline covers (first step: initialise every group from the water state). */
    private fun syncFromState(s: WaterState) {
        for (g in AlgaeGroup.entries) {
            val v = headlineField(s, g)
            if (!initialized || kotlin.math.abs(v - written[g.ordinal]) > 1e-9) {
                if (!initialized) for (si in 0 until ns) biomass[g.ordinal * ns + si] = seedOf(g.ordinal, si)
                setCover(g, v)
            }
        }
        initialized = true
    }

    private fun writeBack(s: WaterState) {
        for (g in AlgaeGroup.entries) {
            val h = headline(g)
            written[g.ordinal] = h
            when (g) {
                AlgaeGroup.HAIR -> s.algaeHair = h
                AlgaeGroup.DIATOMS -> s.diatoms = h
                AlgaeGroup.CYANO -> s.cyano = h
                AlgaeGroup.DINOS -> s.dinos = h
                AlgaeGroup.FILM -> s.film = h
                AlgaeGroup.BUBBLE -> s.bubble = h
                AlgaeGroup.CORALLINE -> s.coralline = h
            }
        }
    }

    private fun headlineField(s: WaterState, g: AlgaeGroup) = when (g) {
        AlgaeGroup.HAIR -> s.algaeHair
        AlgaeGroup.DIATOMS -> s.diatoms
        AlgaeGroup.CYANO -> s.cyano
        AlgaeGroup.DINOS -> s.dinos
        AlgaeGroup.FILM -> s.film
        AlgaeGroup.BUBBLE -> s.bubble
        AlgaeGroup.CORALLINE -> s.coralline
    }

    /** Group parameters (A1, A5, A6). Rates per day, Ks in ppm NO3 / ppm PO4, Ik and PAR in µmol m-2 s-1, flow in cm/s. */
    class Params(
        val muMax: Double, val ksN: Double, val ksP: Double, val ik: Double, val parInhib: Double,
        val tOpt: Double, val tWidth: Double, val tCrash: Double, val phMin: Double, val pptMin: Double,
        val flowX: DoubleArray, val flowY: DoubleArray,
        val nFrac: Double, val pFrac: Double, val nFromWater: Double,
        val bHalf: Double, val cmax: DoubleArray, val storeDays: Double,
        val dBasal: Double, val dStarve: Double, val dDark: Double,
        /** Growth weight per surface (A6 "first appears on"). */
        val affinity: DoubleArray = doubleArrayOf(1.0, 1.0, 1.0, 1.0),
    )

    companion object {
        /** µmol m-2 s-1 per unit light intensity on each surface (05 §14 zones: rock top 200-400, sand 50-150, overhang < 50). */
        val PAR_AT_FULL = doubleArrayOf(180.0, 130.0, 380.0, 100.0)
        /** Local velocity (cm/s) at normal pump strength: glass 8, sand bed 10, rock top 15, shade / dead spots 4 (A1 zones). */
        val BASE_FLOW = doubleArrayOf(8.0, 10.0, 15.0, 4.0)
        /** Light-phase growth multiplier so mu_max is the daily rate under a ~10 h full-light day (est.). */
        const val DAY_GAIN = 2.4
        /** Darkness beyond a normal night (h) before blackout die-back starts (A2 light, A7 #8). */
        const val DARK_GRACE_H = 14.0
        const val KS_SI = 0.14                 // ppm SiO2 (5 µM, est.)
        const val SIO2_PER_DW = 0.25           // g SiO2 per g DW diatoms (frustules 20-40 % SiO2)
        const val K_DECAY = 0.2                // /d labile remineralisation (ULVDEC first week)
        const val K_DECAY_OLD = 0.025          // /d refractory remainder (ULVDEC by day 52)
        const val REFRACTORY = 0.15            // share of decaying tissue that turns refractory (est.)
        const val O2_PER_DW = 1.4              // g O2 per g DW decayed (A5 "about 1.3"; C 30-35 % of DW)
        const val GRAZE_CAP_G_M2_D = 3.0
        const val GRAZE_EGESTED = 0.5          // share of grazed tissue returned as faeces to the dead pool (est.)
        const val OLD_HAIR_PALATABILITY = 0.2  // Derbesia > 14 d and Bryopsis (A4: 0.1-0.3)
        /** Turf density (biomass / (area · b_half)) below which hair counts as short, palatable fuzz. */
        const val SHORT_TURF_X = 0.15
        const val DINO_ULN_FLOOR = 0.6
        const val DINO_RETURN_DEATH = 0.45
        /** Competitor biomass (g DW) at which the other-algae half of the dino competitor index is half on. */
        const val COMP_HALF_G = 1.0
        const val CYANO_DOC_BASE = 0.4
        const val CYANO_DOC_GAIN = 1.2
        /** Dissolved organic carbon (mg C/L) a clean tank stays under ([BacteriaModel.DOC_DIRTY]), and the rise to a full DOC index. */
        const val DOC_CLEAN = 1.5
        const val DOC_SPAN = 3.0
        /** Competitor index a full probiotic dose adds (est.). */
        const val PROBIOTIC_CI = 0.3
        /** Hours for the dino film to lift into the water after lights-out, and to settle back after lights-on (est.). */
        const val DINO_LIFT_TAU_H = 1.5
        const val DINO_SETTLE_TAU_H = 1.0
        /** UV die-back of the dinos in the water column (A5: 0.3 /d), and the share of the film in the water by day (est.). */
        const val DINO_UV_DEATH = 0.3
        const val DINO_WATER_DAY = 0.1
        /** Bubble algae growth on young rock (microbes 0) relative to mature rock, and the extra at pH 7.9 (est.). */
        const val BUBBLE_YOUNG_ROCK = 0.3
        const val BUBBLE_LOW_PH_GAIN = 0.5
        /** Manual removal: share of the bubbles taken, share that burst when squeezed, cells seeded per burst cell (3-8), mass of a new cell vs a grown one (est.). */
        const val BUBBLE_PICK = 0.85
        const val BUBBLE_POP_SHARE = 0.3
        const val BUBBLE_POP_SEEDS = 5.0
        const val BUBBLE_DAUGHTER_MASS = 0.15
        /** Film a Flipper pass leaves on the glass it slides over (felt pad: one pass takes ~90 %, est.). */
        const val SCRAPE_LEFT = 0.1
        /** Share of scraped film that settles as detritus; the rest is eaten by fish or caught by the filter (est.). */
        const val SCRAPE_TO_DEAD = 0.5

        private fun d(vararg v: Double) = v

        /** Order: DIATOMS, FILM, HAIR, CYANO, DINOS, BUBBLE, CORALLINE; surfaces GLASS, SAND, ROCK_TOP, ROCK_SHADE. */
        val P = arrayOf(
            // diatoms: fast, shade tolerant, silicate-limited; flat flow response
            Params(1.4, 0.08, 0.04, 100.0, 400.0, 24.0, 8.0, 31.0, 7.2, 15.0, d(0.0, 60.0), d(1.0, 1.0),
                0.05, 0.007, 1.0, 2.0, d(0.6, 1.0, 0.4, 0.4), 2.0, 0.1, 0.3, 0.3, d(0.4, 1.0, 0.5, 0.8)),
            // GSA / green film: the always-on glass film
            Params(1.0, 0.3, 0.03, 150.0, 0.0, 27.0, 6.0, 33.0, 7.2, 15.0, d(0.0, 60.0), d(1.0, 1.0),
                0.05, 0.007, 1.0, 3.0, d(1.0, 0.05, 0.1, 0.05), 2.0, 0.05, 0.15, 0.2),
            // green hair algae (Derbesia, Bryopsis): hump flow 5-20 cm/s, wants light
            Params(0.29, 1.9, 0.1, 150.0, 0.0, 27.0, 5.0, 33.0, 7.3, 15.0, d(0.0, 5.0, 20.0, 40.0, 60.0), d(0.5, 1.0, 1.0, 0.5, 0.3),
                0.025, 0.0015, 1.0, 40.0, d(0.2, 0.05, 0.9, 0.9), 7.0, 0.01, 0.05, 0.06),
            // cyanobacteria: N2 fixer (no N limit, 20 % of N from water), P-hungry, low flow only
            Params(0.8, 0.0, 0.005, 100.0, 600.0, 29.0, 7.0, 32.5, 7.0, 10.0, d(0.0, 3.0, 10.0, 20.0, 25.0), d(1.0, 1.0, 0.3, 0.05, 0.0),
                0.06, 0.005, 0.2, 10.0, d(0.2, 1.0, 0.6, 0.6), 4.0, 0.1, 0.2, 0.4),
            // dinoflagellates: tiny Ks, mixotrophic, prefer v < 8 cm/s
            Params(1.0, 0.02, 0.005, 100.0, 600.0, 26.0, 5.0, 32.5, 7.0, 15.0, d(0.0, 8.0, 20.0), d(1.0, 1.0, 0.3),
                0.05, 0.007, 1.0, 5.0, d(0.5, 1.0, 0.8, 0.8), 2.0, 0.05, 0.3, 0.4),
            // bubble algae: slow, stagnant mature rock. A1's 0.03-0.06 /d is the net rate (doubling 12-25 d; clusters double
            // every 3-4 weeks, A6); the gross rate is set so shaded rock at moderate nutrients (PO4 ~0.05) nets that
            // (Ik 100 and Ks_P 0.03 ppm, the shade-dweller ends of A1's estimates)
            Params(0.1, 0.6, 0.03, 100.0, 0.0, 26.0, 5.0, 31.0, 7.3, 15.0, d(0.0, 5.0, 15.0, 30.0), d(1.0, 1.0, 0.5, 0.2),
                0.025, 0.0015, 1.0, 20.0, d(0.0, 0.0, 0.3, 0.3), 10.0, 0.005, 0.02, 0.05),
            // coralline: very slow, carbonate-dependent, hump flow 10-25 cm/s
            Params(0.015, 0.06, 0.01, 100.0, 0.0, 26.5, 5.0, 31.0, 0.0, 20.0, d(0.0, 10.0, 25.0, 60.0), d(0.4, 1.0, 1.0, 0.6),
                0.005, 0.0003, 1.0, 100.0, d(0.5, 0.0, 0.9, 0.9), 10.0, 0.001, 0.01, 0.015),
        )

        /** Surfaces each group's headline (WaterState) cover is averaged over. */
        val HEADLINE: Array<List<AlgaeSurface>> = arrayOf(
            listOf(AlgaeSurface.SAND),                                                  // diatoms: the brown sand film
            listOf(AlgaeSurface.GLASS),                                                 // film: the glass-wiping cue
            listOf(AlgaeSurface.ROCK_TOP, AlgaeSurface.ROCK_SHADE),                     // hair: rock
            listOf(AlgaeSurface.SAND, AlgaeSurface.ROCK_TOP, AlgaeSurface.ROCK_SHADE),  // cyano: sand and rock
            listOf(AlgaeSurface.SAND, AlgaeSurface.ROCK_TOP, AlgaeSurface.ROCK_SHADE),  // dinos: sand and rock
            listOf(AlgaeSurface.ROCK_TOP, AlgaeSurface.ROCK_SHADE),                     // bubble: rock
            listOf(AlgaeSurface.ROCK_TOP, AlgaeSurface.ROCK_SHADE),                     // coralline: rock
        )

        /** Hidden seed cover per group (dinos keep 1 %, A5; cyano spots appear at ~1 % on day 1, A6). */
        val SEED_COVER = doubleArrayOf(0.002, 0.002, 0.002, 0.01, 0.01, 0.001, 0.002)
        /**
         * Biomass in crevices that grazers cannot reach, as a share of `area · b_half` (0.03 ≈ 3 % cover at Cmax 0.9).
         * Bubble algae start on the shaded undersides and in the crevices of mature rock (A6): about 3 % cover at their
         * Cmax of 0.3, so a crab that picks at them (A4) thins a patch but not the crevices.
         */
        val REFUGE_COVER = doubleArrayOf(0.01, 0.01, 0.03, 0.01, 0.01, 0.1, 0.0)
        val COMPETITORS = listOf(AlgaeGroup.DIATOMS, AlgaeGroup.FILM, AlgaeGroup.HAIR, AlgaeGroup.CYANO)
        /** Film groups the pods graze, and the most of that film they take per day. */
        val POD_FOOD = listOf(AlgaeGroup.DIATOMS, AlgaeGroup.FILM)
        const val POD_GRAZE_MAX = 0.05

        /** Carbonate factor for coralline (KUF08): 1.0 at pH 8.2 / 8 dKH, 0.5 at 7.9, 0.1 at 7.7; Ca, alk and Mg in range. */
        fun corallineCarbonate(s: WaterState): Double =
            interp(doubleArrayOf(7.5, 7.7, 7.9, 8.2), doubleArrayOf(0.0, 0.1, 0.5, 1.0), s.ph) * caAlk(s)

        private fun caAlk(s: WaterState): Double =
            ((s.alk - 4) / 4).coerceIn(0.0, 1.0) * ((s.ca - 250) / 150).coerceIn(0.0, 1.0) * ((s.mg - 1000) / 200).coerceIn(0.0, 1.0)

        fun interp(xs: DoubleArray, ys: DoubleArray, x: Double): Double {
            if (x <= xs[0]) return ys[0]
            for (i in 1 until xs.size) if (x <= xs[i]) {
                val t = (x - xs[i - 1]) / (xs[i] - xs[i - 1])
                return ys[i - 1] + (ys[i] - ys[i - 1]) * t
            }
            return ys.last()
        }
    }
}
