package com.reeflive.core.ai

import com.reeflive.core.geom.Vec3
import com.reeflive.core.sim.Anchor
import com.reeflive.core.sim.Organism
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

/**
 * Engineer goby, Pholidichthys leucotaenia (juvenile, black/yellow stripes). A shy, peaceful
 * burrower that lives under the rock: it excavates sand from beneath the rock and spits the
 * mouthfuls out as small piles near the burrow mouth. By day juveniles hover in a loose group
 * near the mouth picking plankton; at night they withdraw deep into the tunnels. Anguilliform
 * swimmer (same spline deformation as an eel). Never a predator (SIMULATION §4.5, owner correction).
 */
class GobyBrain(c: Ctx) : SpeciesBrain(c) {
    val burrow: Anchor? = c.rock.anchors["BURROW_GOBY"] ?: c.rock.anchors["CAVE_EEL"]
    val mouth: Vec3 = burrow?.pos ?: Vec3(69.0, 5.0, 23.0)
    val inDir: Vec3 = (burrow?.dir ?: Vec3(0.5, 0.1, -0.85)).normalized()
    val outDir: Vec3 = (inDir * -1.0).let { Vec3(it.x, 0.05, it.z).normalized(Vec3.Z) }
    val tunnel: List<Vec3> = (c.rock.anchors["burrow_path"] ?: c.rock.anchors["den_path"])?.points
        ?: listOf(mouth, mouth + inDir * 8.0, mouth + inDir * 16.0)
    /** Individual offset around the mouth so a group spreads out (loose aggregation). */
    private val slot = Vec3(c.rng.signed() * 5, c.rng.range(1.5, 6.0), c.rng.signed() * 3)

    init {
        install(InBurrow(), MouthHover(), Excavate(), Feed(this, 18.0, "Picking plankton"),
            Flee(this, { _, _ -> outside(1.5) }, "Darting into its burrow"), SurfaceGasp(this))
    }

    fun outside(k: Double) = Vec3(mouth.x, max(mouth.y, c.rock.sandTopCm + 1.0), mouth.z) + outDir * k
    fun inside(k: Double) = mouth + inDir * k

    /** Body trailing back down the tunnel from the head. */
    fun tunnelSpline(head: Vec3): List<Vec3> = listOf(head, mouth) + tunnel.drop(1)

    override fun restSpot(c: Ctx, night: Boolean): Rest {
        val head = if (night) inside(4.0) else outside(0.5)
        return Rest(head, if (night) inDir else outDir, posture = 3, spline = tunnelSpline(head))
    }

    override fun findThreat(c: Ctx): Organism? = super.findThreat(c)

    /** In the burrow: deep and out of sight at night; at the entrance (head out) when resting by day. */
    inner class InBurrow : Behavior("burrow", "In its burrow") {
        override fun score(c: Ctx): Double {
            val o = c.o
            return when {
                c.p.night -> 0.85
                o.waryLevel > 0.3 || o.startle > 0.2 -> 0.7
                else -> 0.25 + 0.3 * o.skittishness
            }
        }
        override fun update(c: Ctx, out: SteerIntent) {
            val o = c.o
            val night = c.p.night
            if (o.posture != 3 && o.pos.distanceTo(mouth) > 4) {
                out.seek(outside(1.0), o.cruiseSpeed, arrive = 2.0, urgency = 0.5)
                return
            }
            val head = if (night) inside(4.0) else outside(0.6 + 0.4 * sin(c.t * 0.3))
            out.place(head, if (night) inDir else outDir, Vec3.Y, 3.0)
            out.posture = 3
            out.splineOverride = tunnelSpline(o.pos)
            o.visible = !night
            o.sleeping = night
            o.gill = 0.4 + 0.3 * sin(2 * PI * 0.5 * c.t)
        }
        override fun exit(c: Ctx) { c.o.visible = true }
    }

    /** Daytime: hover low in a loose group near the mouth, nose into the current, snapping at plankton. */
    inner class MouthHover : Behavior("mouthhover", "Hovering by its burrow") {
        private var nextSnap = 0.0
        override fun score(c: Ctx): Double = if (c.p.night) 0.0 else 0.55 * (1 - 0.5 * c.o.waryLevel)
        override fun update(c: Ctx, out: SteerIntent) {
            val o = c.o
            val spot = c.safe(outside(6.0) + slot + Vec3(sin(c.t * 0.11 + slot.x) * 2.0, sin(c.t * 0.07) * 1.0, 0.0), 1.0)
            out.seek(spot, o.cruiseSpeed * 0.5, arrive = 2.5)
            out.hover = true
            out.ampMul = 0.6
            out.separation = 1.5
            // pick drifting particles within a few cm
            val f = if (o.hunger > 0.05) c.food.nearest(o.pos, 6.0) { o.species.dietMatch(it.kind) > 0 } else null
            if (f != null) {
                out.seek(f.pos, o.cruiseSpeed * 1.5, arrive = 0.5, urgency = 0.6)
                if (o.pos.distanceTo(f.pos) < 1.2 && c.t > nextSnap) { c.bite(f); nextSnap = c.t + 1.0 }
            }
        }
    }

    /**
     * Excavation trip: duck into the burrow, emerge carrying a mouthful of sand, swim a few cm out
     * and spit it onto a growing pile (Simulation.depositSand → render sand piles).
     */
    inner class Excavate : Behavior("dig", "Moving sand out of its burrow") {
        private var phase = 0
        private var timer = 0.0
        private var dump = Vec3.ZERO
        private var nextAt = 0.0
        override val committed: Boolean get() = phase in 1..2
        override fun score(c: Ctx): Double = if (c.p.night || c.t < nextAt || c.o.waryLevel > 0.3) 0.0 else 0.72
        override fun enter(c: Ctx) {
            phase = 0; timer = 0.0
            val side = (outDir cross Vec3.Y).normalized(Vec3.X) * (c.rng.signed() * 5.0)
            dump = c.safe(outside(c.rng.range(4.0, 9.0)) + side, 0.5).withY(c.rock.sandTopCm + 1.2)
        }
        override fun update(c: Ctx, out: SteerIntent) {
            val o = c.o
            timer += c.dt
            when (phase) {
                0 -> { // into the mouth
                    out.seek(outside(0.5), o.cruiseSpeed, arrive = 1.0)
                    if (o.pos.distanceTo(outside(0.5)) < 2.0) { phase = 1; timer = 0.0 }
                }
                1 -> { // digging inside, out of sight for a few seconds
                    out.place(inside(2.5), inDir, Vec3.Y, 4.0)
                    out.posture = 3
                    out.splineOverride = tunnelSpline(o.pos)
                    if (timer > 4.0 + 4.0 * c.rng.nextDouble()) { phase = 2; timer = 0.0 }
                }
                2 -> { // carry the mouthful out
                    o.mouth = 1.0
                    out.seek(dump, o.cruiseSpeed * 0.8, arrive = 1.0, urgency = 0.4)
                    out.ampMul = 1.3
                    if (o.pos.distanceTo(dump) < 2.0 || timer > 10) { phase = 3; timer = 0.0 }
                }
                else -> { // spit
                    o.mouth = 1.0
                    out.seek(dump + Vec3.Y * 1.5, 0.5, arrive = 1.0)
                    out.pitchBias = -0.4
                    if (timer > 0.6) {
                        c.sim.depositSand(dump, GOBY_MOUTHFUL_CM3)
                        nextAt = c.t + c.rng.range(45.0, 300.0)
                        finished = true
                    }
                }
            }
        }
    }

    companion object { const val GOBY_MOUTHFUL_CM3 = 0.6 }
}
