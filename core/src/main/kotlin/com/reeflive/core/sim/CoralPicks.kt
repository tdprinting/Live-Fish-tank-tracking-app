package com.reeflive.core.sim

/** A suggested spot as the renderer and the HUD see it ([CoralSpot]). [rank] 0 is the best. */
data class CoralSpotPose(
    val rank: Int,
    val x: Float, val y: Float, val z: Float,
    val nx: Float, val ny: Float, val nz: Float,
    val stars: Int,
    val reason: String,
    val onSand: Boolean,
)

/** The next colony waiting for its spot: species, name, how many are waiting (this one included) and its spots. */
data class CoralPickPose(
    val speciesId: String,
    val name: String,
    val waiting: Int,
    val spots: List<CoralSpotPose>,
)

/**
 * Corals waiting for the keeper to pick their spot (My tank). When a coral bag is emptied each colony joins the queue
 * instead of settling somewhere at random; the HUD shows the head's best spots ([CoralSpots.suggest], highlighted in
 * the tank) and [Command.PlaceCoral] puts it on one, where it grows from then on. Off (the stocking code places them)
 * in the other tanks and in tests that do not turn it on.
 */
class CoralPicks {
    /** True in My tank: colonies wait for a spot. */
    var byHand = false
    private val waiting = ArrayList<String>()
    private var cached: CoralPickPose? = null
    private var cachedSpots: List<CoralSpot> = emptyList()
    /** What the cache was built for: head species, queue length, colonies, animals and the rockwork's revision. */
    private var dirty = true
    private var keyHead = ""
    private var keyWaiting = 0
    private var keyCorals = 0
    private var keyAnimals = 0
    private var keyRock = 0L

    val count: Int get() = waiting.size
    /** Species ids waiting, in arrival order (saved with the tank). */
    fun ids(): List<String> = waiting.toList()

    fun add(speciesId: String, count: Int) { repeat(count.coerceAtLeast(0)) { waiting += speciesId }; dirty = true }
    fun restore(ids: List<String>) { waiting.clear(); waiting += ids; dirty = true }
    fun clear() { waiting.clear(); cached = null; cachedSpots = emptyList(); dirty = true }

    /**
     * The head colony and its spots (cached: recomputed when the queue, the colonies or the rockwork change, so the
     * 20 Hz snapshot only reads a field).
     */
    fun pose(sim: Simulation): CoralPickPose? {
        if (waiting.isEmpty()) { cached = null; return null }
        val rock = sim.rockRevision()
        if (dirty || keyHead != waiting[0] || keyWaiting != waiting.size || keyCorals != sim.corals.size || keyAnimals != sim.organisms.size || keyRock != rock) {
            dirty = false; keyHead = waiting[0]; keyWaiting = waiting.size; keyCorals = sim.corals.size; keyAnimals = sim.organisms.size; keyRock = rock
            val sp = sim.species[waiting[0]]
            cachedSpots = CoralSpots.suggest(sim, sp)
            cached = CoralPickPose(
                sp.id, sp.common.ifBlank { sp.id }, waiting.size,
                cachedSpots.mapIndexed { i, s ->
                    CoralSpotPose(
                        i, s.pos.x.toFloat(), s.pos.y.toFloat(), s.pos.z.toFloat(),
                        s.normal.x.toFloat(), s.normal.y.toFloat(), s.normal.z.toFloat(), s.stars, s.reason, s.onSand,
                    )
                },
            )
        }
        return cached
    }

    /**
     * Puts the head colony on spot [index] of its suggestions (−1 or out of range: the best one). With no free spot
     * at all it settles wherever the stocking code finds room. Returns the new colony's id, or null when none waited.
     */
    fun place(sim: Simulation, index: Int): String? {
        if (waiting.isEmpty()) return null
        pose(sim)
        val sp = sim.species[waiting.removeAt(0)]
        dirty = true
        val spot = cachedSpots.getOrNull(index) ?: cachedSpots.firstOrNull()
        return if (spot != null) Stocking.placeCoral(sim, sp, spot.pos)
        else { Stocking.addMany(sim, sp.id, 1); sim.corals.lastOrNull()?.id }
    }

    /** Puts every waiting colony on its best spot (tests, and hosts with no picker). */
    fun placeAll(sim: Simulation) { while (waiting.isNotEmpty()) place(sim, -1) }
}
