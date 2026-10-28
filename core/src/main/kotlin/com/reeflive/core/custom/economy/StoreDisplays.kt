package com.reeflive.core.custom.economy

import com.reeflive.core.custom.TankBuild
import com.reeflive.core.custom.TankBuilds
import com.reeflive.core.geom.Vec3
import com.reeflive.core.sim.DryRockShape
import com.reeflive.core.sim.Coral
import com.reeflive.core.sim.Simulation
import com.reeflive.core.sim.Stocking
import com.reeflive.core.sim.TankFilter
import kotlin.math.min

/**
 * The kinds of display tank a fish store runs (docs/ECONOMY.md §5 "Visiting a store"). Real shops mix them: a row of
 * long tanks for fish, nano cubes for small stuff, shallow frag tanks with the corals on plugs on egg-crate racks, and
 * one big showpiece. The hosts pass each tank's setup to the store room so the renderer draws the right stand, lamp and
 * prop tank in every bay; keep the order (tools/blender/store_rooms.py KINDS and its node names follow it).
 */
enum class DisplayKind(val build: TankBuild) {
    /** A 20 gallon all-in-one cube on a counter-height stand. */
    NANO_CUBE(TankBuilds.NANO),
    /** A rimless 40 breeder. */
    RIMLESS(TankBuilds.BREEDER),
    /** A 4 ft long tank, like Tee's. */
    LONG(TankBuilds.LONG_REEF),
    /** A shallow 36 × 24 × 12 in frag tank on a sump: corals on plugs, light close to the water. */
    FRAG(
        TankBuild(
            "store_frag_45", "Frag tank", "Shallow coral tank with egg-crate racks.",
            gallons = 45.0, lengthIn = 36.0, depthIn = 24.0, heightIn = 12.0, heaterWatts = 200.0, skimmer = true, filterSock = true,
            filter = TankFilter.SUMP,
        ),
    ),
    /** The big one: a rimless 120 on a sump, the store's pride. */
    SHOWPIECE(
        TankBuild(
            "store_show_120", "Showpiece 120", "A rimless 120 on a sump.",
            gallons = 120.0, lengthIn = 48.0, depthIn = 24.0, heightIn = 24.0, heaterWatts = 400.0, skimmer = true, filterSock = true,
            refugium = true, ato = true, rimless = true, filter = TankFilter.SUMP,
        ),
    ),
}

/**
 * How a display tank is lit: per-channel gains on the normal LED schedule (white, blue, royal, red, green, violet, uv,
 * moon; LightSchedule.CHANNEL_NAMES) so the live tank's renderer and the corals see the same colour, and [glow], the
 * linear colour the tank's water casts into the room (store.frag), also used for the other bays' prop tanks.
 */
enum class DisplayLight(val gains: DoubleArray, val glow: FloatArray) {
    /** Bright white and blue: a fish tank lit to show colour. */
    DAYLIGHT(doubleArrayOf(1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0), floatArrayOf(0.20f, 0.45f, 0.62f)),
    /** A little warmer (a pet shop's fluorescent tubes). */
    WARM(doubleArrayOf(1.0, 0.75, 0.7, 1.0, 1.0, 0.6, 0.4, 1.0), floatArrayOf(0.30f, 0.48f, 0.55f)),
    /** Mostly blue with some white: how coral shops light so the colours pop. */
    COOL(doubleArrayOf(0.55, 1.0, 1.0, 0.45, 0.55, 1.0, 1.0, 1.0), floatArrayOf(0.14f, 0.32f, 0.85f)),
    /** Actinic: blue, royal, violet and UV only, the frag tank purple glow. */
    ACTINIC(doubleArrayOf(0.18, 1.0, 1.0, 0.08, 0.2, 1.0, 1.0, 1.0), floatArrayOf(0.32f, 0.22f, 1.0f)),
}

/** The rockwork in a display tank, built from the aquascape builder's dry rock (cured) or Tee's own rock cut to fit. */
enum class DisplayScape {
    /** Tee's rockscape, fitted to the glass. */
    STOCK,
    /** Two islands with a sand channel between them. */
    ISLANDS,
    /** An arch in the middle, a boulder each side. */
    ARCH,
    /** Tall separate pillars with open water for big fish. */
    PILLARS,
    /** One rounded pile in the middle (a cube's classic bommie). */
    BOMMIE,
    /** Low rubble and slabs: caves and ledges for crabs, shrimp and snails. */
    RUBBLE,
    /** One tall dramatic tower with a branch reaching over the sand. */
    MONOLITH,
    /** No rockwork: egg-crate frag racks with plugs ([StoreDisplays.plugs]). */
    RACKS,
}

/** One display tank's setup: its tank, rock, light and the corals that make it look like a reef (not for sale). */
data class DisplaySetup(val kind: DisplayKind, val scape: DisplayScape, val light: DisplayLight, val decor: List<String> = emptyList())

/**
 * Which setup each store section gets, how its rock is built and where frag plugs are. One section, one look: the
 * same tank every day, so the store feels like a real place.
 */
object StoreDisplays {
    private const val ZOA = "zoanthus_sp"
    private const val SHROOM = "discosoma_sp"
    private const val HAIRY = "rhodactis_sp"
    private const val CLOVE = "clavularia_sp"
    private const val GSP = "briareum_sp"
    private const val KENYA = "capnella_sp"
    private const val TOADSTOOL = "sarcophyton_sp"
    private const val FINGER = "sinularia_sp"
    private const val PINK_LEATHER = "lobophytum_sp"

    private val FALLBACK = DisplaySetup(DisplayKind.RIMLESS, DisplayScape.STOCK, DisplayLight.DAYLIGHT, listOf(ZOA, KENYA, GSP))

    /** The setup of [store]'s section [title] ([StoreVisit.tanks] titles). */
    fun setup(store: FishStore, title: String): DisplaySetup = when (store) {
        FishStore.SHELLYS -> when (title) {
            "Starter fish" -> DisplaySetup(DisplayKind.LONG, DisplayScape.ISLANDS, DisplayLight.WARM, listOf(SHROOM, KENYA, ZOA))
            "Clean-up crew" -> DisplaySetup(DisplayKind.NANO_CUBE, DisplayScape.RUBBLE, DisplayLight.WARM, listOf(SHROOM))
            "Easy corals" -> DisplaySetup(DisplayKind.RIMLESS, DisplayScape.ARCH, DisplayLight.COOL)
            else -> FALLBACK
        }
        FishStore.FRAG_SHACK -> when (title) {
            "Mushrooms & polyps", "Leathers" -> DisplaySetup(DisplayKind.FRAG, DisplayScape.RACKS, DisplayLight.ACTINIC, listOf(ZOA, GSP, CLOVE))
            // the shop's own reef the frags are cut from
            else -> DisplaySetup(DisplayKind.RIMLESS, DisplayScape.BOMMIE, DisplayLight.ACTINIC, listOf(ZOA, GSP, HAIRY, KENYA, TOADSTOOL, CLOVE))
        }
        FishStore.BIG_BLUE -> when (title) {
            "Community fish" -> DisplaySetup(DisplayKind.LONG, DisplayScape.STOCK, DisplayLight.DAYLIGHT, listOf(KENYA, ZOA, SHROOM, FINGER))
            "Bigger fish" -> DisplaySetup(DisplayKind.SHOWPIECE, DisplayScape.PILLARS, DisplayLight.DAYLIGHT, listOf(TOADSTOOL, FINGER, KENYA))
            "Clean-up crew" -> DisplaySetup(DisplayKind.RIMLESS, DisplayScape.RUBBLE, DisplayLight.DAYLIGHT, listOf(SHROOM, ZOA))
            "Shrimp & crabs" -> DisplaySetup(DisplayKind.NANO_CUBE, DisplayScape.RUBBLE, DisplayLight.DAYLIGHT, listOf(ZOA))
            "Easy corals" -> DisplaySetup(DisplayKind.FRAG, DisplayScape.RACKS, DisplayLight.COOL, listOf(ZOA, CLOVE))
            "Showy corals" -> DisplaySetup(DisplayKind.LONG, DisplayScape.BOMMIE, DisplayLight.ACTINIC, listOf(ZOA, GSP, HAIRY))
            else -> FALLBACK
        }
        FishStore.DEEP_REEF -> when (title) {
            "The showpiece" -> DisplaySetup(DisplayKind.SHOWPIECE, DisplayScape.MONOLITH, DisplayLight.COOL, listOf(GSP, ZOA, PINK_LEATHER, HAIRY))
            "Rare fish" -> DisplaySetup(DisplayKind.LONG, DisplayScape.ARCH, DisplayLight.COOL, listOf(TOADSTOOL, ZOA, HAIRY))
            "Rare inverts" -> DisplaySetup(DisplayKind.RIMLESS, DisplayScape.ISLANDS, DisplayLight.COOL, listOf(ZOA, HAIRY))
            else -> DisplaySetup(DisplayKind.NANO_CUBE, DisplayScape.BOMMIE, DisplayLight.ACTINIC, listOf(ZOA, HAIRY))
        }
    }

    /** Display tanks keep their rock looking like long-cured live rock (DryRock.dryness ~0). */
    private const val CURED_DAYS = 400.0

    /** A dry rock to drop: shape, where along the glass and front to back (0..1), turn, and the build pass. */
    private class Drop(val shape: DryRockShape, val fx: Double, val fz: Double, val yaw: Double, val pass: Int = 0)

    private fun recipe(scape: DisplayScape): List<Drop> = when (scape) {
        DisplayScape.STOCK, DisplayScape.RACKS -> emptyList()
        DisplayScape.ISLANDS -> listOf(
            Drop(DryRockShape.BOULDER, 0.24, 0.45, 20.0), Drop(DryRockShape.SLAB, 0.20, 0.55, -10.0), Drop(DryRockShape.PILLAR, 0.30, 0.35, 0.0),
            Drop(DryRockShape.BOULDER, 0.74, 0.42, -30.0), Drop(DryRockShape.BRANCH, 0.70, 0.50, 160.0), Drop(DryRockShape.RUBBLE, 0.82, 0.62, 0.0),
            Drop(DryRockShape.SLAB, 0.25, 0.45, 35.0, 1), Drop(DryRockShape.RUBBLE, 0.76, 0.40, 0.0, 1),
        )
        DisplayScape.ARCH -> listOf(
            Drop(DryRockShape.ARCH, 0.50, 0.45, 0.0), Drop(DryRockShape.BOULDER, 0.16, 0.40, 40.0), Drop(DryRockShape.BOULDER, 0.84, 0.45, -20.0),
            Drop(DryRockShape.RUBBLE, 0.30, 0.65, 0.0), Drop(DryRockShape.SLAB, 0.84, 0.45, 15.0, 1), Drop(DryRockShape.BRANCH, 0.16, 0.40, 200.0, 1),
        )
        DisplayScape.PILLARS -> listOf(
            Drop(DryRockShape.PILLAR, 0.18, 0.35, 0.0), Drop(DryRockShape.PILLAR, 0.52, 0.30, 50.0), Drop(DryRockShape.PILLAR, 0.82, 0.38, 110.0),
            Drop(DryRockShape.BOULDER, 0.30, 0.55, 0.0), Drop(DryRockShape.BOULDER, 0.68, 0.55, 70.0), Drop(DryRockShape.RUBBLE, 0.45, 0.70, 0.0),
            Drop(DryRockShape.SLAB, 0.52, 0.30, 0.0, 1),
        )
        DisplayScape.BOMMIE -> listOf(
            Drop(DryRockShape.BOULDER, 0.50, 0.45, 0.0), Drop(DryRockShape.RUBBLE, 0.28, 0.55, 0.0), Drop(DryRockShape.RUBBLE, 0.72, 0.55, 90.0),
            Drop(DryRockShape.SLAB, 0.50, 0.45, 30.0, 1), Drop(DryRockShape.BRANCH, 0.45, 0.40, 120.0, 2),
        )
        DisplayScape.RUBBLE -> listOf(
            Drop(DryRockShape.SLAB, 0.35, 0.40, 10.0), Drop(DryRockShape.RUBBLE, 0.65, 0.35, 0.0), Drop(DryRockShape.RUBBLE, 0.25, 0.65, 60.0),
            Drop(DryRockShape.RUBBLE, 0.72, 0.62, 120.0), Drop(DryRockShape.SLAB, 0.55, 0.40, -25.0, 1),
        )
        DisplayScape.MONOLITH -> listOf(
            Drop(DryRockShape.BOULDER, 0.42, 0.40, 0.0), Drop(DryRockShape.PILLAR, 0.40, 0.38, 25.0, 1), Drop(DryRockShape.BRANCH, 0.48, 0.42, -20.0, 2),
            Drop(DryRockShape.RUBBLE, 0.70, 0.58, 0.0), Drop(DryRockShape.RUBBLE, 0.20, 0.62, 45.0),
        )
    }

    /**
     * Builds [setup]'s rockwork in a fresh display tank, a step at a time ([DisplayBuild] runs a few per frame): Tee's rock
     * comes out, the recipe's cured rock goes in pass by pass (each pass falls and settles on the one before), then every
     * rock settles to rest and the scape freezes. Nothing of the tank is on screen meanwhile, so the rocks fall without the
     * field refresh a live tank needs ([Simulation.deferRockRefresh]), and a tank handed over is at rest: no rock is still
     * jostling (and costing a frame's worth of physics) when the keeper walks up to it.
     */
    fun scapeSteps(sim: Simulation, setup: DisplaySetup, seed: Int): Sequence<Unit> = sequence {
        if (setup.scape == DisplayScape.STOCK) { sim.setDynamicRocks(false); return@sequence }
        sim.clearRocks()
        yield(Unit)
        val drops = recipe(setup.scape)
        val len = sim.rock.lengthCm
        val dep = sim.rock.widthCm
        val passes = drops.maxOfOrNull { it.pass } ?: -1
        sim.deferRockRefresh(true)
        for (pass in 0..passes) {
            for (i in drops.indices) {
                val d = drops[i]
                if (d.pass != pass) continue
                sim.placeRock(d.shape, d.fx * len, d.fz * dep, d.yaw, (seed * 131 + i * 17) and 0x7fffffff, CURED_DAYS)
                yield(Unit)
            }
            // each rock drops from just above what is under it: a few seconds settles it
            var left = SETTLE_STEPS
            while (left > 0) { val n = min(left, STEP_CHUNK); sim.advanceSteps(n, STEP_DT, fullBehavior = false); left -= n; yield(Unit) }
        }
        // rocks that were still easing into their final seat sleep before the keeper sees them
        var left = REST_STEPS
        while (left > 0 && sim.rocksAwake) { sim.advanceSteps(STEP_CHUNK, STEP_DT, fullBehavior = false); left -= STEP_CHUNK; yield(Unit) }
        sim.deferRockRefresh(false)
        sim.setDynamicRocks(false)
    }

    /** The settle after a rock drops: 4 s of sim time, in 0.05 s steps, [STEP_CHUNK] at a time. */
    private const val STEP_DT = 0.05
    private const val SETTLE_STEPS = 80
    private const val STEP_CHUNK = 4
    /** At most this many more steps (15 s, a rock's longest stay awake) for rocks still moving after the last pass. */
    private const val REST_STEPS = 300


    // ============================================================== frag racks

    /**
     * Egg-crate racks in a [DisplayKind.FRAG] tank (cm in the tank frame: x0, z0, x1, z1, top y), drawn by the store
     * room's Racks node (tools/blender/store_rooms.py RACKS uses the same numbers). Back rack raised on legs, front
     * rack low, like a shop's frag system.
     */
    val RACKS: List<DoubleArray> = listOf(
        doubleArrayOf(8.0, 6.0, 83.4, 24.0, 14.0),
        doubleArrayOf(8.0, 32.0, 83.4, 54.0, 7.0),
    )
    /** Plug pitch on a rack (cm) and how far a plug's top stands above the rack. */
    const val PLUG_PITCH = 6.25
    const val PLUG_TOP = 1.6

    /** Every plug's top centre, back rack first, left to right. */
    val plugs: List<Vec3> by lazy {
        val out = ArrayList<Vec3>()
        for (r in RACKS) {
            var z = r[1] + PLUG_PITCH / 2
            while (z < r[3]) {
                var x = r[0] + PLUG_PITCH / 2
                while (x < r[2]) { out += Vec3(x, r[4] + PLUG_TOP, z); x += PLUG_PITCH }
                z += PLUG_PITCH
            }
        }
        out
    }

    /**
     * Puts [count] frags of [speciesId] on free plugs; [used] tracks taken plugs. A frag gets a plug with free plugs
     * either side when there is one, so neighbours do not touch. Returns how many went in.
     */
    fun onPlugs(sim: Simulation, speciesId: String, count: Int, used: BooleanArray, rng: com.reeflive.core.geom.Rng): Int {
        val spots = ArrayList<Vec3>()
        fun roomy(k: Int) = !used[k] && (k == 0 || !used[k - 1]) && (k + 1 >= used.size || !used[k + 1])
        while (spots.size < count) {
            var k = -1
            for (t in 0 until 60) { val c = rng.nextInt(plugs.size); if (roomy(c)) { k = c; break } }
            if (k < 0) k = used.indexOfFirst { !it }
            if (k < 0) break
            used[k] = true
            spots += plugs[k]
        }
        return colonies(sim, speciesId, spots)
    }

    /** Puts [count] colonies of [speciesId] on free rock tops or sand (Stocking.coralSpots). */
    fun onRock(sim: Simulation, speciesId: String, count: Int): Int {
        sim.species.ensure(speciesId)
        val spacing = Stocking.spacingCm(sim.species[speciesId].coralKind)
        return colonies(sim, speciesId, Stocking.coralSpots(sim.rock, sim.corals.map { it.pos }, count, sim.rng, spacing).map { it.first })
    }

    /**
     * Colonies of [speciesId] at [spots]: the species' anchored model moved to each spot (a copy, as the stocking tool
     * makes). The move is measured from the anchor as modelled ([com.reeflive.core.sim.RockData.restAnchors]): a display tank's own
     * rockwork replaced the rock the anchors were on, and the renderer cuts the copy out of the model around that
     * spot.
     */
    private fun colonies(sim: Simulation, speciesId: String, spots: List<Vec3>): Int {
        sim.species.ensure(speciesId)
        val sp = sim.species[speciesId]
        val home = Stocking.homeAnchor(sim.rock, sp)
        val rest = home?.let { sim.rock.restAnchors[it.name] } ?: home
        val homePos = rest?.pos ?: Vec3(60.0, 10.0, 20.0)
        val already = sim.corals.count { it.species.id == speciesId }
        spots.forEachIndexed { i, p ->
            val n = already + i + 1
            val name = "${sp.common.ifBlank { speciesId }} #$n"
            sim.corals += Coral("$speciesId#shop$n", sp, name, rest?.name ?: "", p, Vec3.Y, sim.rng.nextDouble() * 6.28, p - homePos)
        }
        return spots.size
    }
}
