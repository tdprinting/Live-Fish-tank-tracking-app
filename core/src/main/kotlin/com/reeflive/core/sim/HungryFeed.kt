package com.reeflive.core.sim

import com.reeflive.core.geom.Vec3

/**
 * Tank Health → "Feed the hungry ones": every hungry animal gets a meal of the food it likes best
 * ([SimSpecies.foodPreference]), dropped at the surface right above it, the way a keeper target-feeds a shy fish or a
 * snail. Hungry animals close together that like the same food share one drop. Each drop is sized to the meals it
 * covers ([Organism.mealGrams] scaled by how hungry they are), never under a few particles so it can be seen, and never
 * a full pinch for one small fish, so the auto feed does not foul the water. Pure planning: [Simulation] sends each
 * [Command.Feed] through the normal feeding path, so every other animal can still race for it.
 */
object HungryFeed {
    /** Hunger (0..1) from which an animal shows as hungry and gets fed (the fish card's bar turns orange here). */
    const val HUNGRY_AT = 0.5

    /** Foods the auto feed picks from, everyday ones first (ties go to the earlier one). Nori needs the clip, pods the micro sim. */
    val FOODS = listOf(
        FoodKind.PELLET, FoodKind.MYSIS, FoodKind.FLAKE, FoodKind.BRINE, FoodKind.ALGAE_WAFER,
        FoodKind.KRILL, FoodKind.CYCLOPS, FoodKind.PRAWN, FoodKind.PLANKTON,
    )

    /** Hungry animals within this (cm, across the tank floor) of a drop share it. */
    const val SHARE_CM = 12.0
    /** A drop holds at least this many particles so it shows in the water. */
    const val MIN_PARTICLES = 4
    /** One drop is generous (a hungry fish gets beaten to some of it) but never more than a normal tap of that food. */
    const val PORTION = 1.5

    /** The food [species] does best on, or null when it eats none of the shelf foods (it grazes or catches its own). */
    fun bestFood(species: SimSpecies): FoodKind? =
        FOODS.maxByOrNull { species.foodPreference(it) }?.takeIf { species.foodPreference(it) > 0.0 }

    /** Is this animal hungry enough to show in the list and get fed? */
    fun isHungry(o: Organism): Boolean = o.alive && o.hunger >= HUNGRY_AT && bestFood(o.species) != null

    /** One planned drop: [grams] of [kind] at [at] for [ids]. */
    data class Drop(val kind: FoodKind, val grams: Double, val at: Vec3, val ids: List<String>)

    /** The drops that feed every hungry animal in [organisms]; empty when nobody is hungry. */
    fun plan(organisms: List<Organism>, waterLevelCm: Double, lengthCm: Double, widthCm: Double): List<Drop> {
        class Group(val kind: FoodKind, var x: Double, var z: Double) { var grams = 0.0; val ids = ArrayList<String>() }
        val groups = ArrayList<Group>()
        for (o in organisms) {
            if (!isHungry(o)) continue
            val kind = bestFood(o.species) ?: continue
            val g = groups.firstOrNull { it.kind == kind && kotlin.math.hypot(it.x - o.pos.x, it.z - o.pos.z) <= SHARE_CM }
                ?: Group(kind, o.pos.x, o.pos.z).also { groups += it }
            // the share grows toward the newcomer so the drop lands between them
            val n = g.ids.size
            g.x = (g.x * n + o.pos.x) / (n + 1); g.z = (g.z * n + o.pos.z) / (n + 1)
            g.grams += o.mealGrams * (o.hunger / 0.8) * PORTION
            g.ids += o.id
        }
        return groups.map { g ->
            val k = g.kind
            val grams = g.grams.coerceAtLeast(MIN_PARTICLES * k.gramsPerParticle).coerceAtMost(maxOf(k.typicalDoseGrams, MIN_PARTICLES * k.gramsPerParticle))
            val at = Vec3(g.x.coerceIn(4.0, lengthCm - 4.0), waterLevelCm - 0.3, g.z.coerceIn(4.0, widthCm - 4.0))
            Drop(k, grams, at, g.ids)
        }
    }
}

/**
 * One line of the Tank Health hunger list: [name] and its [hunger] bar (0 full .. 1 starving), whether it counts as
 * [hungry], and the food the auto feed would give it ([food], null when it finds its own).
 */
data class HungerRow(
    val id: String,
    val name: String,
    val species: String,
    val hunger: Float,
    val isFish: Boolean,
    val hungry: Boolean,
    val food: FoodKind?,
)

/** The hunger list for [stats] (living animals only): fish first, then the rest, hungriest first within each. */
fun HungryFeed.rows(stats: List<OrganismStats>, species: (String) -> SimSpecies?): List<HungerRow> =
    stats.filter { it.alive }.mapNotNull { st ->
        val sp = species(st.speciesId) ?: return@mapNotNull null
        val food = bestFood(sp)
        HungerRow(st.id, st.nickname.ifBlank { st.common }, st.common, st.hunger, sp.brainKind.isFish, food != null && st.hunger >= HUNGRY_AT, food)
    }.sortedWith(compareBy<HungerRow>({ !it.isFish }, { -it.hunger }))
