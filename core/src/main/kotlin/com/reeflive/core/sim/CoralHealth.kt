package com.reeflive.core.sim

import com.reeflive.core.geom.Rng
import com.reeflive.core.geom.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** How a colony makes new colonies (docs/research/coral_health.md §3). */
enum class CoralSpread {
    /** No new colonies: it only gets bigger (GSP mat edge, organ pipe stolons, macro biomass). */
    NONE,
    /** Zoas, palys and clove polyps: runners / stolons bud new polyps at the colony edge, and a clump spreads. */
    RUNNER,
    /** Mushrooms: longitudinal fission and pedal laceration leave a new disc next to the old one. */
    FISSION,
    /** Leathers: a bud or a fission piece of the stalk drops off and attaches beside the parent. */
    BUD,
    /** Kenya tree: branchlets pinch off, drift and root where they land (CoralGrowth.dropBranch). */
    BRANCHLET,
}

/** What a colony is short of or bothered by right now (the main stress), for the Tank Health card and the welcome back. */
enum class CoralNeed(val words: String) {
    HEAT("the water is too warm"),
    COLD("the water is too cold"),
    LIGHT_HIGH("the light is too strong"),
    LIGHT_LOW("it is too dark"),
    FLOW("the water flow is wrong"),
    SALT("the salt level is off"),
    ALK("alkalinity is off"),
    CA_MG("calcium or magnesium is low"),
    NUTRIENTS("there is too much nitrate or phosphate"),
    HUNGRY("it is hungry (no food and very clean water)"),
    IODINE("iodine is used up"),
    NEIGHBOR("a neighbour is stinging or crowding it"),
    POISON("something toxic is in the water"),
    ALGAE("algae is growing over it"),
    SICK("an infection is eating its tissue"),
}

/**
 * Per-species coral needs and responses (docs/research/coral_health.md). [CoralSim] reads the multipliers into its
 * bleaching, tissue and recovery rates; [CoralGrowth] reads [spread] for new colonies. Most aquarium timelines have
 * no controlled data, so the multipliers are labelled estimates ordered by the hobby consensus on hardiness.
 *
 * @property heatBleach heat (and DHW) bleaching speed × typical; @property lightBleach too-much-light paling ×;
 * @property darkFade bleach per day when its acclimated light sits fully below its low limit (zooxanthellae starve);
 * @property feedNeed 0..1 how much it relies on caught food (heterotrophy); @property iodineNeed 0..1 stress weight of
 * used-up iodine; @property recede tissue loss × once failing; @property recovery colour and tissue regain ×;
 * @property spreadEveryDays mean days between new colonies at full vigour once at [spreadMinSize];
 * @property tint what the bleached tissue fades to (linear RGB): white for most, cream for leathers, pale pink for zoas.
 */
data class CoralProfile(
    val heatBleach: Double = 1.0,
    val lightBleach: Double = 1.0,
    val darkFade: Double = 0.02,
    val feedNeed: Double = 0.1,
    val iodineNeed: Double = 0.1,
    val recede: Double = 1.0,
    val recovery: Double = 1.0,
    val spread: CoralSpread = CoralSpread.NONE,
    val spreadEveryDays: Double = 0.0,
    val spreadMinSize: Double = 1.3,
    val tint: FloatArray = WHITE,
) {
    companion object {
        val WHITE = floatArrayOf(0.89f, 0.89f, 0.87f)
        val CREAM = floatArrayOf(0.86f, 0.80f, 0.66f)
        val PALE_PINK = floatArrayOf(0.90f, 0.80f, 0.80f)
        val GREY = floatArrayOf(0.75f, 0.72f, 0.70f)
    }
}

object CoralHealth {
    /** Tissue at or below this is a dead colony ([Simulation] reports it once and clears it after [CLEAR_DAYS]). */
    const val DEAD_TISSUE = 0.01
    /** Soft tissue melts away in a day or two once dead ([M] "melts overnight"); then the colony is gone. */
    @Ev(Evidence.DESIGN_ESTIMATE, "coral_health §2")
    const val CLEAR_DAYS = 2.0
    /** Colonies of one species a tank grows on its own (keeps the coral list, the competition pairs and draws small). */
    const val MAX_OWN_COLONIES = 10
    /** Health a coral needs to count as doing well (economy happy-tank bonus: "health above 70 %"). */
    const val HEALTHY = 0.7

    /** One number for "how is this colony doing", 0..1: living tissue, colour and stress. */
    fun health(c: Coral): Double = (c.tissue * (1 - 0.5 * c.bleach) * (1 - 0.3 * c.stress)).coerceIn(0.0, 1.0)

    fun health(p: CoralPose): Double = (p.tissue * (1 - 0.5 * p.bleach) * (1 - 0.3 * p.stress)).toDouble().coerceIn(0.0, 1.0)

    /**
     * How big to draw a colony: its grown (or shrunken) size, held between a small bud and a third over the model so a
     * grown colony spreads by budding rather than swelling into the rock or the glass. GSP mats draw their own reach.
     */
    fun drawScale(c: Coral): Double = when (c.kind) {
        CoralKind.GSP, CoralKind.MACRO, CoralKind.AIPTASIA, CoralKind.GENERIC -> 1.0
        else -> c.size.coerceIn(0.35, 1.3)
    }

    private val cache = HashMap<String, CoralProfile>()

    fun profile(sp: SimSpecies): CoralProfile = cache.getOrPut(sp.id) { profileOf(sp.id, sp.coralKind) }

    /**
     * Ordering from coral_health.md: GSP, mushrooms, Kenya and palys are the hardy ones; zoas and clove in the middle;
     * Sarcophyton and Ricordea bleach readily in heat; organ pipe needs Ca/alk and food and melts fastest.
     */
    @Ev(Evidence.DESIGN_ESTIMATE, "coral_health §1-3 (hobby consensus; Fujimura & Riegl 2017 for Palythoa)")
    fun profileOf(id: String, k: CoralKind): CoralProfile {
        val genus = id.lowercase().substringBefore('_')
        return when {
            genus.startsWith("palythoa") -> CoralProfile(0.6, 0.8, 0.015, 0.35, 0.05, 0.8, 1.2, CoralSpread.RUNNER, 45.0, 1.4, CoralProfile.PALE_PINK)
            genus.startsWith("ricordea") -> CoralProfile(1.3, 1.4, 0.02, 0.3, 0.1, 1.2, 0.7, CoralSpread.FISSION, 90.0, 1.1)
            // bounce mushroom (research 61 §2): slow to split, and its vesicles deflate under too much light
            id.startsWith("rhodactis_inchoata") -> CoralProfile(0.9, 1.4, 0.012, 0.25, 0.1, 0.9, 0.9, CoralSpread.FISSION, 180.0, 1.2)
            genus.startsWith("rhodactis") -> CoralProfile(0.8, 1.2, 0.012, 0.25, 0.1, 0.8, 1.0, CoralSpread.FISSION, 60.0, 1.1)
            genus.startsWith("amplexidiscus") -> CoralProfile(0.8, 1.5, 0.01, 0.4, 0.1, 0.9, 1.0, CoralSpread.FISSION, 120.0, 1.15)
            genus.startsWith("sarcophyton") -> CoralProfile(1.3, 1.0, 0.02, 0.1, 0.35, 1.0, 0.9, CoralSpread.BUD, 150.0, 1.7, CoralProfile.CREAM)
            genus.startsWith("sinularia") -> CoralProfile(1.1, 1.0, 0.02, 0.1, 0.35, 0.9, 1.0, CoralSpread.BUD, 120.0, 1.6, CoralProfile.CREAM)
            genus.startsWith("lobophytum") -> CoralProfile(1.0, 1.0, 0.02, 0.1, 0.3, 0.9, 1.0, CoralSpread.BUD, 160.0, 1.7, CoralProfile.CREAM)
            // duncans are the forgiving LPS (stony_corals.md §4): fed a lot, slow to bleach, recover well
            genus.startsWith("duncanopsammia") -> CoralProfile(0.9, 1.0, 0.02, 0.5, 0.05, 0.9, 1.0, CoralSpread.NONE)
            // scolymia (research 61 §3): low light, harmed more by too much light than too little, relies on meaty
            // target feeding; a solitary polyp, never buds
            genus.startsWith("homophyllia") || genus.startsWith("scolymia") -> CoralProfile(1.1, 1.5, 0.015, 0.6, 0.05, 1.2, 0.8, CoralSpread.NONE)
            else -> when (k) {
                CoralKind.GSP -> CoralProfile(0.6, 0.7, 0.02, 0.1, 0.05, 0.6, 1.3, CoralSpread.NONE, tint = CoralProfile.GREY)
                CoralKind.ZOA -> CoralProfile(0.9, 1.0, 0.02, 0.25, 0.05, 1.0, 1.0, CoralSpread.RUNNER, 50.0, 1.4, CoralProfile.PALE_PINK)
                CoralKind.CLOVE -> CoralProfile(1.0, 1.0, 0.025, 0.3, 0.2, 1.0, 0.9, CoralSpread.RUNNER, 70.0, 1.3)
                CoralKind.MUSHROOM -> CoralProfile(0.7, 1.3, 0.01, 0.2, 0.1, 0.8, 1.0, CoralSpread.FISSION, 60.0, 1.1)
                CoralKind.KENYA -> CoralProfile(0.8, 0.9, 0.02, 0.05, 0.4, 0.8, 1.1, CoralSpread.BRANCHLET, tint = CoralProfile.CREAM)
                CoralKind.ORGAN_PIPE -> CoralProfile(1.3, 1.1, 0.03, 0.5, 0.15, 1.5, 0.6, CoralSpread.NONE)
                CoralKind.LEATHER -> CoralProfile(1.1, 1.0, 0.02, 0.1, 0.35, 0.9, 1.0, CoralSpread.BUD, 140.0, 1.7, CoralProfile.CREAM)
                // Euphyllia (stony_corals.md §1-3): brown jelly and recession strip a stressed head in days, and a
                // receded skeleton is slow to cover again. New heads come from the colony growing (size), not budding.
                CoralKind.EUPHYLLIA -> CoralProfile(1.2, 1.1, 0.02, 0.3, 0.05, 1.4, 0.7, CoralSpread.NONE)
                CoralKind.LPS -> CoralProfile(1.0, 1.1, 0.02, 0.5, 0.05, 1.1, 0.9, CoralSpread.NONE)
                CoralKind.MACRO -> CoralProfile(0.5, 0.5, 0.0, 0.0, 0.0, 0.5, 1.5, CoralSpread.NONE)
                CoralKind.AIPTASIA -> CoralProfile(0.4, 0.5, 0.0, 0.3, 0.0, 0.5, 1.5, CoralSpread.NONE)
                CoralKind.GENERIC -> CoralProfile()
            }
        }
    }

    // ------------------------------------------------------------------ spreading

    /**
     * A new colony from [c] when its spreading roll comes up this step, or null. [own] is how many colonies of the
     * species the tank already has. The parent gives up a little of its size (the piece that left).
     */
    fun maybeSpread(c: Coral, v: Double, rock: RockData, rng: Rng, dtMin: Double, own: Int, others: List<Coral>, id: () -> String): Coral? {
        val p = profile(c.species)
        if (p.spreadEveryDays <= 0.0 || own >= MAX_OWN_COLONIES || v < 0.4 || c.tissue < 0.8 || c.bleach > 0.3) return null
        if (c.size < p.spreadMinSize) return null
        if (!rng.chance(v * dtMin / 1440.0 / p.spreadEveryDays)) return null
        val (near, far) = when (p.spread) {
            CoralSpread.RUNNER -> 0.5 to 3.0
            CoralSpread.FISSION -> 1.0 to 4.0
            CoralSpread.BUD -> 1.0 to 6.0
            else -> return null
        }
        val baby = place(c, rock, rng, id(), near, far, others) ?: return null
        val g = CoralGrowth.growth(c.kind)
        when (g.form) {
            CoralGrowth.Form.ENCRUSTING -> {
                // the edge that budded off: the mat steps back, the bud starts as a small clump
                c.radiusCm = (c.radiusCm - 0.3 * g.r0).coerceAtLeast(g.r0)
                baby.radiusCm = 0.5 * g.r0
                baby.size = 0.5
            }
            else -> { c.size = (c.size - 0.15).coerceAtLeast(1.0); baby.size = 0.4 }
        }
        baby.tissue = 0.9
        baby.fed = c.fed
        return baby
    }

    /**
     * A spot on top of the rock beside [c] (or null): within [near]..[far] cm of its edge, facing up, not on the sand,
     * with room above, not on another colony and out of reach of other species' stings and terpenes (a bud that lands
     * there is killed, so the colonies that last are the ones in the clear). The baby keeps the parent's model and home anchor, moved by its
     * offset (as a copy from Add life), so it draws and saves like any other colony.
     */
    fun place(c: Coral, rock: RockData, rng: Rng, id: String, near: Double, far: Double, others: List<Coral>): Coral? {
        val r0 = CoralGrowth.extent(c)
        repeat(6) {
            val ang = rng.nextDouble() * 2 * PI
            val r = r0 + rng.range(near, far)
            val spot = Stocking.surfaceSpot(rock, c.pos.x + cos(ang) * r, c.pos.z + sin(ang) * r) ?: return@repeat
            val (p, n) = spot
            if (n.y < 0.5 || p.y < rock.sandTopCm + 0.5 || p.y > rock.waterLevelCm - 8) return@repeat
            if (others.any { o -> o.tissue > DEAD_TISSUE && o.pos.distanceTo(p) < CoralGrowth.extent(o) + clearance(c, o) }) return@repeat
            val home = c.restPos - c.offset
            return Coral(id, c.species, budName(c.nickname), c.anchor, p, Vec3.Y, rng.nextDouble() * 6.28, offset = p - home)
        }
        return null
    }

    /** Gap a bud of [c] needs from [o]: room to grow from its own kind, the reach of either one's chemical weapons from others. */
    private fun clearance(c: Coral, o: Coral): Double {
        if (o.species.id == c.species.id) return 1.0
        fun reach(k: CoralKind) = when (k) {
            CoralKind.LEATHER -> CoralGrowth.LEATHER_RANGE_CM
            CoralKind.KENYA -> CoralGrowth.KENYA_RANGE_CM
            CoralKind.ZOA, CoralKind.AIPTASIA -> CoralGrowth.STING_RANGE_CM
            else -> 1.0
        }
        return maxOf(reach(c.kind), reach(o.kind), StonyCorals.maxReach(c.species), StonyCorals.maxReach(o.species), 2.0) + 1.0
    }

    private fun budName(parent: String): String {
        val base = parent.substringBefore(" bud").substringBefore(" branch")
        return "$base bud"
    }

    // ------------------------------------------------------------------ summaries (Tank Health card, welcome back)

    /** One colony's state worth remembering across a catch-up. */
    data class Mark(val id: String, val name: String, val alive: Boolean, val bleach: Double, val health: Double)

    fun marks(corals: List<Coral>): List<Mark> = corals.filter { it.kind != CoralKind.AIPTASIA }.map {
        Mark(it.id, it.nickname.ifBlank { it.species.common.ifBlank { it.species.id } }, it.tissue > DEAD_TISSUE, it.bleach, health(it))
    }

    /**
     * Plain words for what happened to the corals between [before] and [after] (a catch-up, the welcome-back card):
     * died, started bleaching, getting worse, got better, new colonies grown. Empty when nothing worth saying.
     */
    fun changes(before: List<Mark>, after: List<Coral>): List<String> {
        val now = marks(after).associateBy { it.id }
        val out = ArrayList<String>()
        val died = before.filter { it.alive && now[it.id]?.alive != true }.map { it.name }
        if (died.isNotEmpty()) out += "${list(died)} died while you were away."
        val bleached = before.filter { b -> now[b.id]?.let { it.alive && b.bleach < 0.5 && it.bleach >= 0.5 } == true }.map { it.name }
        if (bleached.isNotEmpty()) out += "${list(bleached)} turned pale (bleaching)."
        val worse = before.filter { b -> now[b.id]?.let { it.alive && it.bleach < 0.5 && it.health < b.health - 0.15 } == true }.map { it.name }
        if (worse.isNotEmpty()) out += "${list(worse)} ${if (worse.size == 1) "is" else "are"} not doing well."
        val better = before.filter { b -> now[b.id]?.let { it.alive && b.bleach >= 0.3 && it.bleach < b.bleach - 0.2 } == true }.map { it.name }
        if (better.isNotEmpty()) out += "${list(better)} got ${if (better.size == 1) "its" else "their"} colour back."
        val ids = before.map { it.id }.toHashSet()
        val born = after.count { it.kind != CoralKind.AIPTASIA && it.id !in ids && it.tissue > DEAD_TISSUE }
        if (born == 1) out += "A coral grew a new baby colony!" else if (born > 1) out += "Corals grew $born new baby colonies!"
        return out
    }

    private fun list(names: List<String>): String {
        val d = names.distinct()
        return when (d.size) {
            1 -> d[0]
            2 -> "${d[0]} and ${d[1]}"
            else -> d.dropLast(1).joinToString(", ") + " and " + d.last()
        }
    }

    /** One coral on the Tank Health card: its name, health 0..1, whether it is turning pale, and its main trouble. */
    data class Row(val name: String, val health: Double, val pale: Boolean, val need: CoralNeed?)

    /** Tank Health rows for the living corals in a snapshot (aiptasia are pests, not listed). */
    fun rows(corals: List<CoralPose>): List<Row> = corals.filter { it.tissue > DEAD_TISSUE && CoralKind.resolve(it.speciesId) != CoralKind.AIPTASIA }.map {
        Row(it.name.ifBlank { it.speciesId }, health(it), it.bleach > 0.3f, if (it.need >= 0) CoralNeed.entries[it.need] else null)
    }

    /** The commonest need among struggling colonies, for a one-line hint ("Corals: 2 pale, it is too dark"). */
    fun topNeed(corals: List<CoralPose>): CoralNeed? {
        val counts = IntArray(CoralNeed.entries.size)
        for (c in corals) if (c.need >= 0 && c.tissue > DEAD_TISSUE) counts[c.need]++
        var best = -1; var n = 0
        for (i in counts.indices) if (counts[i] > n) { n = counts[i]; best = i }
        return if (best < 0) null else CoralNeed.entries[best]
    }
}
