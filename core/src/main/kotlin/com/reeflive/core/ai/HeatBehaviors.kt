package com.reeflive.core.ai

import com.reeflive.core.geom.Vec3
import com.reeflive.core.sim.BrainKind
import com.reeflive.core.sim.HeatModel
import com.reeflive.core.sim.HeatModel.Stage
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The visible heat sequence (research 08 §2, §7.2), installed on every brain. Fish: AGITATED darts along
 * the glass and ignores food; GASPING drifts low toward the cooler bottom water; LETHARGIC sinks to the
 * bottom with clamped fins and a drooping tail; LOE lies on its side, carried by the flow, with a short
 * burst every 30–90 s. Blennies and the engineer goby hide low instead of darting. Inverts in simplified
 * form: shrimp sit motionless, then lie on their side; snails climb, then lose their grip and fall;
 * hermits and the Sally Lightfoot climb to the waterline, then fall off and lie inverted.
 * Death goes through HealthModel.kill (cause "heat"); feeding is cut by HeatModel.appetiteMult.
 */
class HeatResponse(val b: SpeciesBrain) : Behavior("heat", "Heat stress") {
    override val label: String get() = HeatModel.label(b.o).ifBlank { "Heat stress" }
    override val committed: Boolean get() = b.o.heat.stage >= Stage.LETHARGIC

    private var wp: Vec3? = null
    private var nextWp = 0.0
    private var burstUntil = -1.0
    private var nextBurst = 0.0
    private var burstDir: Vec3 = Vec3.X
    private var lastStage = Stage.NORMAL

    override fun score(c: Ctx): Double {
        val o = c.o
        val k = o.brainKind
        return when (o.heat.stage) {
            Stage.NORMAL -> 0.0
            // death throes: a fish spasms on its side where it lies (HeatModel.SPASM_SEC); inverts die at once
            Stage.DEATH -> if (o.isFish) 1.0 else 0.0
            Stage.AGITATED -> when {
                o.isFish -> if (HeatModel.hidesLow(k)) 0.0 else 0.85
                HeatModel.climbsOut(k) || HeatModel.isSnail(k) -> 0.85
                else -> 0.0
            }
            Stage.GASPING -> when {
                o.isFish -> if (k == BrainKind.ENGINEER_GOBY) 0.0 else 0.9
                HeatModel.climbsOut(k) || HeatModel.isSnail(k) -> 0.9
                else -> 0.0
            }
            // the goby stays in its burrow, head out, until it loses equilibrium (08 §2)
            Stage.LETHARGIC -> if (k == BrainKind.ENGINEER_GOBY) 0.0 else 0.97
            Stage.LOE -> 0.99
        }
    }

    override fun enter(c: Ctx) { wp = null; nextWp = 0.0; nextBurst = c.t + c.rng.range(10.0, 40.0); burstUntil = -1.0 }

    override fun update(c: Ctx, out: SteerIntent) {
        val o = c.o
        val st = o.heat.stage
        if (st != lastStage) { lastStage = st; wp = null; nextWp = 0.0 }
        if (o.isFish) fish(c, out, st) else invert(c, out, st)
        if (st == Stage.NORMAL) finished = true
    }

    // ---------------------------------------------------------------- fish
    private fun fish(c: Ctx, out: SteerIntent, st: Stage) {
        val o = c.o
        val r = c.rock
        when (st) {
            Stage.AGITATED -> {
                // leaves cover, fast erratic runs along the glass and rock edges (08 §2 stage 1)
                if (wp == null || c.t > nextWp || o.pos.distanceTo(wp!!) < c.L) {
                    nextWp = c.t + c.rng.range(2.0, 5.0)
                    val edgeZ = if (c.rng.chance(0.5)) 3.0 else r.widthCm - 3.0
                    val p = Vec3(c.rng.range(6.0, r.lengthCm - 6), c.zoneY(c.rng.range(0.15, 0.7)), if (c.rng.chance(0.6)) edgeZ else c.rng.range(4.0, r.widthCm - 4))
                    wp = c.safe(p)
                }
                out.seek(wp!!, o.cruiseSpeed * 1.8, arrive = c.L, urgency = 0.6)
                out.allowGlass = true
                out.wander = 0.3
            }
            Stage.GASPING -> {
                // mid-water to low, toward the cooler bottom water; fins spread, mouth working (stage 2)
                if (wp == null || c.t > nextWp) {
                    nextWp = c.t + c.rng.range(8.0, 15.0)
                    val cover = c.coverPoint(o.pos + Vec3.Y * 10.0, 25.0)
                    wp = c.safe(Vec3(cover.x, c.zoneY(c.rng.range(0.05, 0.25)), cover.z))
                }
                out.seek(wp!!, o.cruiseSpeed * (1.0 - 0.6 * o.heat.d).coerceAtLeast(0.4), arrive = 3.0)
                out.hover = true
                out.pectoral = 1.0
                o.mouth = max(o.mouth, 0.5 + 0.5 * sin(c.t * 9))
            }
            Stage.LETHARGIC -> {
                // settles on the bottom or wedges into rock: clamped fins, tail drooping, occasional righting (stage 3)
                if (wp == null || c.rng.chance(c.dt / 120)) wp = bottomSpot(c)
                out.seek(wp!!, o.cruiseSpeed * 0.2, arrive = 2.0)
                out.hover = true
                out.ampMul = 0.3; out.pectoral = 0.2; out.tailMul = 0.5
                out.pitchBias = -0.15
                out.rollExtra = 0.25 + 0.2 * sin(c.t * 0.4)
                out.separation = 0.2
                o.finClamp = max(o.finClamp, 0.8)
                o.mouth = max(o.mouth, 0.4 + 0.4 * sin(c.t * 9))
            }
            Stage.LOE -> loe(c, out)
            else -> loe(c, out, throes = true)
        }
    }

    /** Low point near cover: sand in front of rock, the floor of a cave. */
    private fun bottomSpot(c: Ctx): Vec3 {
        val cover = c.coverPoint(c.o.pos + Vec3.Y * 10.0, 20.0)
        return c.safe(Vec3(cover.x, c.rock.sandTopCm + 0.3 * c.L + 0.6, cover.z), 0.6 * c.o.collisionLength)
    }

    /**
     * Rolled 60–90° onto its side, carried by the flow, sinking; a 1–3 s burst every 30–90 s, kept low (08 §2
     * stage 4). In the death throes ([throes], stage 5) there are no bursts: the body jerks in spasms where it lies
     * (drawn by the renderer from [com.reeflive.core.sim.Organism.spasm]).
     */
    private fun loe(c: Ctx, out: SteerIntent, throes: Boolean = false) {
        val o = c.o
        val r = c.rock
        if (!throes && c.t > nextBurst && burstUntil < c.t) {
            burstUntil = c.t + c.rng.range(1.0, 3.0)
            nextBurst = burstUntil + c.rng.range(30.0, 90.0)
            burstDir = Vec3.rotateAbout(horizontalHeading(o.heading), Vec3.Y, c.rng.signed() * 2.5)
        }
        if (c.t < burstUntil) {
            // circling, body bending: a burst never climbs more than a few cm and never reaches the top
            val p = o.pos + burstDir * 15.0
            val y = min(o.pos.y + 2.0, c.zoneY(0.5))
            out.seek(c.safe(Vec3(p.x, y, p.z)), o.burstSpeed * 0.6, arrive = 1.0, urgency = 0.8)
            out.rollExtra = 1.2
            out.separation = 0.0
            return
        }
        val flow = c.sim.flow.velocityFast(o.pos)
        var next = o.pos + (flow * 0.6 + Vec3(0.0, -1.5, 0.0)) * c.dt
        val floor = r.sandTopCm + 0.15 * c.L + 0.4
        next = r.clampToWater(next, 1.0).let { if (it.y < floor) it.withY(floor) else it }
        val clear = 0.25 * o.collisionLength
        if (r.sdf.dist(next) < clear) next = r.sdf.project(next, clear)
        val h = horizontalHeading(o.heading)
        // not lying flat: the roll rocks slowly between ~65° and ~85° as the flow carries it
        val roll = LOE_ROLL + 0.17 * sin(c.t * 0.35 + o.appendagePhase)
        out.place(next, h, Vec3.rotateAbout(Vec3.Y, h, roll), 40.0)
        out.ampMul = 0.1; out.tailMul = 0.2; out.pectoral = 0.0
        // gaping in the death throes (08 §2 stage 5)
        o.mouth = max(o.mouth, if (throes) 0.9 else 0.6)
        o.finClamp = max(o.finClamp, 0.5)
    }

    // ---------------------------------------------------------------- invertebrates
    private fun invert(c: Ctx, out: SteerIntent, st: Stage) {
        val o = c.o
        val k = o.brainKind
        val r = c.rock
        if (st <= Stage.GASPING) {
            // climb toward the waterline: hermits and the crab try to leave the water, snails move up (stage 1)
            if (wp == null || c.t > nextWp) {
                nextWp = c.t + c.rng.range(60.0, 180.0)
                val up = c.rockSurfacePoint(Vec3(o.pos.x, r.waterLevelCm - 6, o.pos.z), 15.0, minNormalY = -0.2)?.first
                wp = up ?: run {
                    val z = if (o.pos.z > r.widthCm / 2) r.widthCm - surfaceOffset(c) else surfaceOffset(c)
                    Vec3(o.pos.x.coerceIn(3.0, r.lengthCm - 3), r.waterLevelCm - 2.5, z)
                }
            }
            out.crawl = true; out.target = wp; out.speed = o.cruiseSpeed * (if (HeatModel.isSnail(k)) 1.0 else 1.5)
            out.surfaceOffset = surfaceOffset(c)
            out.sideways = k == BrainKind.SALLY_CRAB
            o.appendagePhase += c.dt * 4
            return
        }
        when {
            HeatModel.isShrimp(k) && st == Stage.LETHARGIC -> {
                // stops cleaning, sits motionless where it is
                out.place(o.pos, o.heading, o.up, 2.0)
                out.posture = o.posture
            }
            HeatModel.isShrimp(k) || HeatModel.isSnail(k) || HeatModel.climbsOut(k) -> {
                // shrimp on its side with twitching pleopods; snails and crabs lose their grip and lie inverted
                val h = horizontalHeading(o.heading)
                val ground = groundBelow(c)
                val up = if (HeatModel.isShrimp(k)) (h cross Vec3.Y).normalized(Vec3.Z) else Vec3.Y * -1.0
                out.place(ground, h, up, 1.5)
                out.posture = 7
                if (HeatModel.isShrimp(k) && c.rng.chance(c.dt * 0.5)) o.appendagePhase += 0.8
                o.extension = if (HeatModel.climbsOut(k)) 0.3 else o.extension
            }
            else -> {
                // brittle stars, asterina, tube worms: still, crown or arms drawn in
                out.place(o.pos, o.heading, o.up, 2.0)
                out.posture = o.posture
                if (k == BrainKind.FEATHER_DUSTER) o.extension = max(0.0, o.extension - c.dt * 0.2)
            }
        }
    }

    /** Where a falling animal lands: straight down onto the sand or the rock below. */
    private fun groundBelow(c: Ctx): Vec3 {
        val o = c.o
        val r = c.rock
        val off = surfaceOffset(c)
        var p = r.clampToWater(Vec3(o.pos.x, o.pos.y, o.pos.z), 1.0)
        var y = p.y
        while (y > r.sandTopCm + off && r.sdf.dist(Vec3(p.x, y - 0.5, p.z)) > off) y -= 0.5
        p = p.withY(max(y, r.sandTopCm + off))
        return if (r.sdf.dist(p) < off) r.sdf.project(p, off) else p
    }

    companion object {
        /** Mean LOE roll onto the side (rad, ~75°; research 08 §2 stage 4: "rolls on side, cannot right"). */
        const val LOE_ROLL = 1.31

        /**
         * Abstract mode: a fish at LETHARGIC or worse lies on the bottom below its rest spot, shrimp and
         * snails on the sand. Returns false when the normal rest pose applies.
         */
        fun abstractPose(c: Ctx, out: SteerIntent): Boolean {
            val o = c.o
            val st = o.heat.stage
            if (st < Stage.LETHARGIC || o.brainKind == BrainKind.ENGINEER_GOBY && st < Stage.LOE) return false
            if (!o.isFish && !(HeatModel.isShrimp(o.brainKind) || HeatModel.isSnail(o.brainKind) || HeatModel.climbsOut(o.brainKind))) return false
            val r = c.rock
            val h = horizontalHeading(o.heading)
            val base = Vec3(o.pos.x, r.sandTopCm + 0.3 * c.L + 0.6, o.pos.z)
            val p = if (r.sdf.dist(base) < 0.5) r.sdf.project(base, 0.5) else base
            val up = when {
                st >= Stage.LOE && (o.isFish || HeatModel.isShrimp(o.brainKind)) -> (h cross Vec3.Y).normalized(Vec3.Z)
                !o.isFish && !HeatModel.isShrimp(o.brainKind) -> Vec3.Y * -1.0
                else -> Vec3.Y
            }
            out.place(r.clampToWater(p, 1.0), h, up)
            out.posture = if (o.isFish) 0 else 7
            return true
        }
    }
}
