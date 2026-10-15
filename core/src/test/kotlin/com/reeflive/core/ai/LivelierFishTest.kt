package com.reeflive.core.ai

import com.reeflive.core.sim.BrainKind
import com.reeflive.core.sim.DefaultStock
import com.reeflive.core.sim.Simulation
import com.reeflive.core.sim.TestTank
import org.junit.Assert.assertTrue
import org.junit.Test

/** Research 30 (SIMULATION §22.2): clown plankton trips, the tang's circuit along the rock, the cardinal's day rest. */
class LivelierFishTest {
    private fun Simulation.label(kind: BrainKind) = organisms.first { it.brainKind == kind }.let { agentOf(it)!!.brain.utility.current?.name }

    /** Clowns leave the host for short plankton trips, more after lights-on than at midday, and stay within the leash. */
    @Test
    fun clownsPickPlanktonAboveTheHost() {
        fun trips(hour: Double): Pair<Int, Double> {
            val sim = TestTank.sim(DefaultStock.all, seed = 3, startHour = hour)
            val nemo = sim.organisms.first { it.brainKind == BrainKind.CLOWN }
            val host = sim.agentOf(nemo)!!.brain.anchorPos(sim.agentOf(nemo)!!.ctx, "host")!!
            var n = 0; var was = false; var far = 0.0
            sim.advance(1800.0, behaviorDt = 0.1) {
                val on = sim.label(BrainKind.CLOWN) == "sortie"
                if (on && !was) n++
                was = on
                if (on) far = maxOf(far, nemo.pos.distanceTo(host) / nemo.lengthCm)
            }
            return n to far
        }
        val (dawn, farDawn) = trips(9.0)
        val (noon, farNoon) = trips(14.0)
        assertTrue("dawn trips $dawn, midday $noon", dawn >= 6 && dawn > noon)
        assertTrue("farthest ${"%.1f".format(maxOf(farDawn, farNoon))} BL", maxOf(farDawn, farNoon) <= ClownBrain.SORTIE_LEASH_L + 0.5)
    }

    /** The tang patrols along the rock at more than one height, not one wall-to-wall lane. */
    @Test
    fun tangPatrolVariesHeight() {
        val sim = TestTank.sim(DefaultStock.all, seed = 3, startHour = 13.0)
        val tang = sim.organisms.first { it.brainKind == BrainKind.TANG }
        val ys = ArrayList<Double>()
        sim.advance(1800.0, behaviorDt = 0.1) { if (sim.label(BrainKind.TANG) == "patrol") ys += tang.pos.y }
        val mean = ys.average()
        val sd = kotlin.math.sqrt(ys.sumOf { (it - mean) * (it - mean) } / ys.size)
        assertTrue("patrol samples ${ys.size}", ys.size > 500)
        assertTrue("patrol height spread ${"%.1f".format(sd)} cm", sd >= 3.0)
    }

    /** Sandy sifts at about the measured rate counting rests (Myers et al. 2019: ~9 mouthfuls a minute) and rests between bouts. */
    @Test
    fun diamondGobyMouthfulRate() {
        val sim = TestTank.sim(DefaultStock.all, seed = 3, startHour = 11.0)
        val g = sim.organisms.first { it.brainKind == BrainKind.DIAMOND_GOBY }
        val b = sim.agentOf(g)!!.brain as DiamondGobyBrain
        sim.advance(300.0, behaviorDt = 0.1)
        val m0 = b.mouthfuls
        var rest = 0; var n = 0
        sim.advance(1800.0, behaviorDt = 0.1) { n++; if (sim.label(BrainKind.DIAMOND_GOBY) == "sandrest") rest++ }
        val perMin = (b.mouthfuls - m0) / 30.0
        assertTrue("mouthfuls/min ${"%.1f".format(perMin)}", perMin in 6.0..13.0)
        assertTrue("rest share ${rest * 100 / n}%", rest > 0.05 * n)
    }

    /** Ricky Bobby (juvenile engineer goby) swims up off the burrow for plankton trips and comes back. */
    @Test
    fun engineerGobyTakesPlanktonTrips() {
        val sim = TestTank.sim(DefaultStock.all, seed = 3, startHour = 11.0)
        val g = sim.organisms.first { it.brainKind == BrainKind.ENGINEER_GOBY }
        val b = sim.agentOf(g)!!.brain as GobyBrain
        var away = 0; var n = 0
        sim.advance(1800.0, behaviorDt = 0.1) { n++; if (b.utility.currentLabel == "Picking plankton above its burrow") away++ }
        assertTrue("on trips ${away * 100 / n}%", away > 0.1 * n && away < 0.7 * n)
    }
}
