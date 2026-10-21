package com.reeflive.core.sim

import com.reeflive.core.geom.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.atan2

/**
 * Submarine mode ([Submarine], [SubmarineRules], `ai.SubmarineSense`): who reacts how, an invisible sub changing
 * nothing at all, a fast approach startling, loitering being habituated to, and the hull never clipping into the tank.
 */
class SubmarineTest {
    private fun tank(seed: Long = 3L) = TestTank.sim(DefaultStock.all, seed = seed, startHour = 12.0).also { it.advance(5.0) }
    private fun Simulation.o(id: String) = organisms.first { it.id == id }
    private val FAR = Double.MAX_VALUE

    @Test
    fun speciesReactByTemperament() {
        val s = tank()
        fun r(id: String, d: Double, closing: Double = 0.0, guard: Double = FAR, night: Boolean = false, habit: Double = 0.0) =
            SubmarineRules.reaction(s.o(id), d, closing, guard, night, habit)
        assertEquals(SubReaction.INVESTIGATE, r("tang", 20.0))
        assertEquals(SubReaction.POSTURE, r("clown_f", 10.0, guard = 6.0))
        assertEquals(SubReaction.POSTURE, r("dottyback", 10.0, guard = 5.0))
        assertEquals(SubReaction.POSTURE, r("hawk", 15.0))
        assertEquals(SubReaction.AVOID, r("cardinal", 10.0))
        assertEquals(SubReaction.HIDE, r("goby", 6.0))
        assertEquals(SubReaction.HIDE, r("lawnmower", 6.0))
        for (id in listOf("banded", "skunk", "crab")) assertEquals(id, SubReaction.FREEZE, r(id, 10.0))
        assertEquals(SubReaction.HIDE, r("hermit1", 5.0))
        for (id in listOf("astrea1", "brittle", "asterina1")) assertEquals(id, SubReaction.NONE, r(id, 3.0, closing = 12.0))
        // a fast approach startles fish, sends crawlers into hiding
        assertEquals(SubReaction.STARTLE, r("tang", 6.0, closing = 11.0))
        assertEquals(SubReaction.STARTLE, r("cardinal", 10.0, closing = 11.0))
        assertEquals(SubReaction.HIDE, r("skunk", 6.0, closing = 11.0))
        // nobody comes to look in the dark; a fully habituated fish ignores a slow sub but a charge still counts
        assertEquals(SubReaction.NONE, r("tang", 20.0, night = true))
        assertEquals(SubReaction.NONE, r("tang", 10.0, habit = 0.8))
        assertEquals(SubReaction.STARTLE, r("tang", 4.0, closing = 11.0, habit = 0.8))
        assertEquals(SubReaction.NONE, r("tang", 80.0))
    }

    /** An invisible sub cruising through the fish leaves every animal exactly as it would have been without it. */
    @Test
    fun invisibleSubChangesNothing() {
        val a = tank(5L); val b = tank(5L)
        b.enqueue(Command.SubmarineMode(on = true, visible = false))
        b.advance(0.1); a.advance(0.1)
        val tang = b.o("tang")
        b.submarine.teleport(tang.pos + Vec3(-12.0, 0.0, 0.0), b.rock, b.sand, 0.0)
        b.enqueue(Command.SteerSubmarine(SubControls(thrust = 1.0, turn = 0.3)))
        var t = 0.0
        while (t < 30.0) {
            a.advance(0.25); b.advance(0.25); t += 0.25
            assertTrue(b.subSense.reactions.isEmpty())
        }
        assertEquals(0, b.subSense.startles)
        for (o in a.organisms) assertEquals(o.id, o.pos, b.o(o.id).pos)
        assertTrue("the sub moved", b.submarine.pos.distanceTo(tang.pos) > 0.0 && b.snapshot().submarine?.visible == false)
    }

    @Test
    fun fastApproachStartles() {
        var startled = 0
        for (seed in 1L..3L) {
            val s = tank(seed)
            s.enqueue(Command.SubmarineMode(on = true))
            s.advance(0.1)
            val fish = s.o("cardinal")
            // a clear horizontal run-up of 16 cm at the fish (open water, no rock in the way)
            val start = (0 until 16).map { k -> val a = k * Math.PI / 8; fish.pos + Vec3(kotlin.math.cos(a), 0.0, kotlin.math.sin(a)) * 16.0 }
                .filter { s.rock.inWater(it, Submarine.RADIUS_CM + 0.5) && s.rock.sdf.segmentClear(it, fish.pos, Submarine.RADIUS_CM + 0.3) && s.submarine.collide(it, s.rock, s.sand).distanceTo(it) < 0.01 }
                .firstOrNull() ?: continue
            val to = fish.pos - start
            s.submarine.teleport(start, s.rock, s.sand, atan2(-to.z, to.x))
            s.enqueue(Command.SteerSubmarine(SubControls(thrust = 1.0)))
            var t = 0.0
            var saw = false
            var minD = 99.0; var maxV = 0.0
            println("seed $seed: start d=%.1f fish=${fish.pos} sub=${s.submarine.pos} skit=%.2f".format(fish.pos.distanceTo(s.submarine.pos), fish.skittishness))
            while (t < 4.0) {
                s.advance(0.1); t += 0.1
                minD = minOf(minD, fish.pos.distanceTo(s.submarine.pos)); maxV = maxOf(maxV, s.submarine.speed)
                if (s.subSense.reactions["cardinal"] == SubReaction.STARTLE) saw = true
            }
            println("seed $seed: startles=${s.subSense.startles} cardinal=$saw minD=%.1f maxV=%.1f".format(minD, maxV))
            if (saw && s.subSense.startles > 0) startled++
        }
        assertTrue("a charging sub startles the cardinal: $startled/3", startled >= 2)
    }

    @Test
    fun loiteringIsHabituatedOverMinutes() {
        val s = tank(2L)
        s.enqueue(Command.SubmarineMode(on = true))
        s.advance(0.1)
        val tang = s.o("tang")
        fun park() = s.submarine.teleport(tang.pos + Vec3(0.0, 0.0, if (tang.pos.z > 16) -8.0 else 8.0), s.rock, s.sand)
        park()
        var early = 0; var late = 0
        var t = 0.0
        while (t < 600.0) {
            s.advance(0.5); t += 0.5
            // the sub loiters by her: when she swims off across the tank it is parked beside her again (still, so it
            // never closes on her), else her habituation decays while she is away and the run depends on her route
            if (s.submarine.pos.distanceTo(tang.pos) > LOITER_CM) park()
            val r = s.subSense.reactions["tang"]
            if (r != null && r != SubReaction.NONE) { if (t < 60.0) early++ else if (t > 540.0) late++ }
        }
        val h = s.subSense.memory.habituation("tang", s.simTimeSec)
        println("tang early=$early late=$late habit=%.2f".format(h))
        assertTrue("the tang reacts at first: $early", early > 40)
        assertTrue("after minutes the tang ignores a loitering sub: late=$late habit=$h", late < early / 4 && h >= SubmarineRules.HABIT_IGNORE * 0.9)
        assertEquals(0, s.subSense.startles)
    }

    /** Driven hard in every direction, the hull stays inside the glass, under the surface, above the sand and out of rock. */
    @Test
    fun subNeverClips() {
        val s = tank(4L)
        s.enqueue(Command.SubmarineMode(on = true, visible = false))
        s.advance(0.1)
        val rng = com.reeflive.core.geom.Rng(9L)
        var worst = 99.0
        var travelled = 0.0
        var last = s.submarine.pos
        for (i in 0 until 600) {
            if (i % 20 == 0) s.enqueue(Command.SteerSubmarine(SubControls(thrust = rng.range(-1.0, 1.0), turn = rng.range(-1.0, 1.0), rise = rng.range(-1.0, 1.0), strafe = rng.range(-1.0, 1.0))))
            s.advance(0.1)
            val p = s.submarine.pos
            travelled += p.distanceTo(last); last = p
            worst = minOf(worst, s.rock.sdf.dist(p))
            assertTrue("in the glass box at $p", p.x >= Submarine.RADIUS_CM - 1e-6 && p.x <= s.rock.lengthCm - Submarine.RADIUS_CM + 1e-6 &&
                p.z >= Submarine.RADIUS_CM - 1e-6 && p.z <= s.rock.widthCm - Submarine.RADIUS_CM + 1e-6 && p.y <= s.rock.waterLevelCm)
            assertTrue("above the sand at $p", p.y >= s.sand.surfaceY(p.x, p.z) + Submarine.RADIUS_CM - 1e-6)
        }
        println("rock clearance min=%.2f travelled=%.0f cm".format(worst, travelled))
        assertTrue("out of the rock: $worst", worst > Submarine.RADIUS_CM * 0.6)
        assertTrue("it actually moved: $travelled", travelled > 100.0)
    }

    /** Tap a fish: the sub glides over, settles at a polite distance nose to it, and never startles anyone doing it. */
    @Test
    fun watchingGlidesOverPolitelyAndFollows() {
        val s = tank(6L)
        s.enqueue(Command.SubmarineMode(on = true))
        s.advance(0.1)
        val tang = s.o("tang")
        s.enqueue(Command.SubmarineWatch("tang"))
        var t = 0.0
        var closest = 99.0
        val polite = s.submarine.politeDistance(tang.lengthCm)
        var near = 0; var samples = 0
        while (t < 60.0) {
            s.advance(0.1); t += 0.1
            val d = s.submarine.pos.distanceTo(tang.pos)
            closest = minOf(closest, d)
            if (t > 20.0) { samples++; if (d < polite + 10.0) near++ }
        }
        val to = tang.pos - s.submarine.pos
        val face = kotlin.math.abs(Math.IEEEremainder(atan2(-to.z, to.x) - s.submarine.yaw, 2 * Math.PI))
        println("watch: polite=%.1f closest=%.1f near=%d/%d face=%.2f startles=%d".format(polite, closest, near, samples, face, s.subSense.startles))
        assertEquals("still watching", "tang", s.snapshot().submarine?.watchId)
        assertEquals("no startles while gliding over", 0, s.subSense.startles)
        assertTrue("stays near the fish: $near/$samples", near > samples * 0.7)
        assertTrue("keeps its distance: $closest", closest > Submarine.RADIUS_CM + 3.0)
        assertTrue("nose to the fish: $face", face < 0.6)
        // the stick pulls it round; null lets go
        s.enqueue(Command.SubmarineWatch(null))
        s.advance(0.2)
        assertEquals(null, s.snapshot().submarine?.watchId)
    }

    /** ‹ › picks the nearest fish first, then steps through the fish only (no snails or corals), keeping the mode. */
    @Test
    fun nextFishStepsThroughFishOnly() {
        val s = tank()
        s.enqueue(Command.SubmarineMode(on = true)); s.advance(0.1)
        val fish = s.organisms.filter { it.alive && it.species.kind == "FISH" }
        val nearest = fish.minByOrNull { it.pos.distanceTo(s.submarine.pos) }!!.id
        s.enqueue(Command.SubmarineWatchNext(1)); s.advance(0.1)
        assertEquals(nearest, s.submarine.watchId)
        s.enqueue(Command.SubmarineWatch(nearest, SubWatch.CIRCLE)); s.advance(0.1)
        val seen = HashSet<String>()
        repeat(fish.size) {
            s.enqueue(Command.SubmarineWatchNext(1)); s.advance(0.1)
            val id = s.submarine.watchId!!
            assertTrue("$id is a fish", fish.any { it.id == id })
            assertEquals(SubWatch.CIRCLE, s.submarine.watchMode)
            seen += id
        }
        assertEquals("one lap visits every fish", fish.size, seen.size)
        s.enqueue(Command.SubmarineWatchNext(-1)); s.advance(0.1)
        val i = fish.indexOfFirst { it.id == nearest }
        assertEquals(fish[(i - 1 + fish.size) % fish.size].id, s.submarine.watchId)
    }

    /** Circling and swimming alongside both stay with the fish; a removed fish ends the watch. */
    @Test
    fun circleAndAlongsideKeepCompany() {
        for (mode in listOf(SubWatch.CIRCLE, SubWatch.ALONGSIDE)) {
            val s = tank(7L)
            s.enqueue(Command.SubmarineMode(on = true, visible = false))
            s.advance(0.1)
            val fish = s.o("clown_f")
            s.enqueue(Command.SubmarineWatch("clown_f", mode))
            var t = 0.0; var far = 0
            while (t < 40.0) { s.advance(0.1); t += 0.1; if (t > 15.0 && s.submarine.pos.distanceTo(fish.pos) > 40.0) far++ }
            assertTrue("$mode stays with the fish: far=$far", far < 30)
            s.enqueue(Command.RemoveOrganism("clown_f"))
            s.advance(0.3)
            assertEquals("$mode watch ends with the fish gone", null, s.submarine.watchId)
        }
    }

    private companion object {
        /** Farthest the parked sub is left from the fish before it is moved beside her again (cm). */
        const val LOITER_CM = 20.0
    }
}
